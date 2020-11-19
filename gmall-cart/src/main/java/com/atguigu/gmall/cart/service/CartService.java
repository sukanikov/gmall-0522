package com.atguigu.gmall.cart.service;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.cart.feign.GmallPmsClient;
import com.atguigu.gmall.cart.feign.GmallSmsClient;
import com.atguigu.gmall.cart.feign.GmallWmsClient;
import com.atguigu.gmall.cart.interceptor.LoginInterceptor;
import com.atguigu.gmall.cart.pojo.Cart;
import com.atguigu.gmall.cart.pojo.UserInfo;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.common.exception.CartException;
import com.atguigu.gmall.pms.entity.SkuAttrValueEntity;
import com.atguigu.gmall.pms.entity.SkuEntity;
import com.atguigu.gmall.sms.vo.ItemSaleVo;
import com.atguigu.gmall.wms.entity.WareSkuEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CartService {
    private static final String KEY_PREFIX = "cart:info:";
    private static final String PRICE_PREFIX = "cart:price:";

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private GmallPmsClient pmsClient;

    @Autowired
    private GmallWmsClient wmsClient;

    @Autowired
    private GmallSmsClient smsClient;

    @Autowired
    private AsyncService asyncService;

    public void addCart(Cart cart) {
        // 获取登录信息，如果userId不为空，就以userId作为key，否则以userKey作为key
        String userId = this.getKey();

        // 通过外层的key获取内层的map结构（借助BoundHashOperations）
        BoundHashOperations<String, Object, Object> hashOps = this.redisTemplate.boundHashOps(KEY_PREFIX + userId);
        BigDecimal count = cart.getCount();
        String skuId = cart.getSkuId().toString();

        // 判断该用户的购物车是否包含当前这件商品，包含则更新数量，不包含则新增记录
        if (hashOps.hasKey(skuId)){
            // 更新数量
            String cartJson = hashOps.get(skuId).toString();
            cart = JSON.parseObject(cartJson, Cart.class);
            cart.setCount(cart.getCount().add(count));  // 原有数量（从redis中取）+ 新增数量（27行预先取出来），之后再覆盖redis中的数量

            // 在mysql更新（利用异步更新）
            this.asyncService.updateCart(userId, cart);

        }else {
            // 新增记录：skuId count
            cart.setUserId(userId);

            ResponseVo<SkuEntity> skuEntityResponseVo = this.pmsClient.querySkuById(cart.getSkuId());
            SkuEntity skuEntity = skuEntityResponseVo.getData();
            if(null == skuEntity){ // 没有这件商品，直接退出方法
                return;
            }
            cart.setTitle(skuEntity.getTitle());
            cart.setPrice(skuEntity.getPrice());
            cart.setDefaultImage(skuEntity.getDefaultImage());

            // 查询库存信息
            ResponseVo<List<WareSkuEntity>> listResponseVo = this.wmsClient.queryWareSkusBySkuId(cart.getSkuId());
            List<WareSkuEntity> wareSkuEntities = listResponseVo.getData();
            if (!CollectionUtils.isEmpty(wareSkuEntities)){
                cart.setStore(wareSkuEntities.stream().anyMatch(wareSkuEntity ->
                            wareSkuEntity.getStock() - wareSkuEntity.getStockLocked() > 0));
            }

            // 销售属性
            ResponseVo<List<SkuAttrValueEntity>> saleAttrsResponseVo = this.pmsClient.querySaleAttrsBySkuId(cart.getSkuId());
            List<SkuAttrValueEntity> skuAttrValueEntities = saleAttrsResponseVo.getData();
            cart.setSaleAttrs(JSON.toJSONString(skuAttrValueEntities));

            // 营销信息
            ResponseVo<List<ItemSaleVo>> salesResponseVo = this.smsClient.querySalesBySkuId(cart.getSkuId());
            List<ItemSaleVo> sales = salesResponseVo.getData();
            cart.setSales(JSON.toJSONString(sales));

            // 设置选中
            cart.setCheck(true);

            // 在mysql写入（利用异步更新）
            this.asyncService.insertCart(cart);

            // 添加价格缓存，这样如果别人没有加入过该商品，你加入时就添加了价格缓存；如果别人加入过该商品，你加入时就覆盖了价格缓存，这样价格总是实时的
            this.redisTemplate.opsForValue().set(PRICE_PREFIX + skuId, skuEntity.getPrice().toString());

        }

        // 最后统一在redis更新或写入
        hashOps.put(skuId, JSON.toJSONString(cart));
    }

    /**
     * 从拦截器中获取UserKey，如果登录了，以userId为userKey
     * @return
     */
    private String getKey() {
        UserInfo userInfo = LoginInterceptor.getUserInfo();
        if (null == userInfo.getUserId()){
            return userInfo.getUserKey();
        }

        return userInfo.getUserId().toString(); //Long型转为String，便于在redis中设置key
    }

    public Cart queryCart(Long skuId) {
        String userId = this.getKey();

        //获取内层map
        BoundHashOperations<String, Object, Object> hashOps = this.redisTemplate.boundHashOps(KEY_PREFIX + userId);

        if (hashOps.hasKey(skuId.toString())){
            String cartJson = hashOps.get(skuId.toString()).toString();
            return JSON.parseObject(cartJson, Cart.class);
        }

        throw new CartException("此用户不存在这条购物车记录！");
    }

    public List<Cart> queryCarts() {
        // 获取userKey（这里要对userKey和userId分别处理）
        UserInfo userInfo = LoginInterceptor.getUserInfo();
        String userKey = userInfo.getUserKey();

        // 查询未登录的购物车内层map
        BoundHashOperations<String, Object, Object> unLoginHashOps = this.redisTemplate.boundHashOps(KEY_PREFIX + userKey);
        List<Object> unLoginCartJsons = unLoginHashOps.values();

        // 将查询出来的json字符串集合转为cart集合
        List<Cart> unLoginCarts = null;
        if (!CollectionUtils.isEmpty(unLoginCartJsons)){
            unLoginCarts = unLoginCartJsons.stream().map(cartJson -> {
                Cart cart = JSON.parseObject(cartJson.toString(), Cart.class);
                cart.setCurrentPrice(new BigDecimal(this.redisTemplate.opsForValue().get(PRICE_PREFIX + cart.getSkuId())));
                return cart;
            }).collect(Collectors.toList());
        }
        // 获取userId判断是否为空，为空则直接返回未登录的购物车
        Long userId = userInfo.getUserId();
        if (null == userId){
            return unLoginCarts;
        }

        // 查询登录购物车的内存map
        BoundHashOperations<String, Object, Object> loginHashOps = this.redisTemplate.boundHashOps(KEY_PREFIX + userId);

        // 把未登录购物车合并到登录状态购物车的内存map中
        if (!CollectionUtils.isEmpty(unLoginCarts)){
            unLoginCarts.forEach(cart -> {
                String skuId = cart.getSkuId().toString();
                BigDecimal count = cart.getCount();
                if (loginHashOps.hasKey(skuId)){
                    // 对登录状态购物车的某个商品更新数量
                    String cartJson = loginHashOps.get(skuId).toString();
                    cart = JSON.parseObject(cartJson, Cart.class);
                    cart.setCount(cart.getCount().add(count));
                    this.asyncService.updateCart(userId.toString(), cart);
                }else {
                    // 登录状态的购物车不包含该商品，新增一条记录
                    cart.setUserId(userId.toString());
                    this.asyncService.insertCart(cart);
                }

                //更新到redis
                loginHashOps.put(skuId, JSON.toJSONString(cart));
            });

            // 删除未登录的购物车
            this.redisTemplate.delete(KEY_PREFIX + userKey);
            this.asyncService.deleteCart(userKey);
        }

        // 查询登录状态的购物并返回
        List<Object> loginCartJsons = loginHashOps.values();
        if (!CollectionUtils.isEmpty(loginCartJsons)){
            return loginCartJsons.stream().map(cartJson -> {
                Cart cart = JSON.parseObject(cartJson.toString(), Cart.class);
                cart.setCurrentPrice(new BigDecimal(this.redisTemplate.opsForValue().get(PRICE_PREFIX + cart.getSkuId())));
                return cart;
            }).collect(Collectors.toList());
        }

        return null;
    }

    public void updateNum(Cart cart) {
        String userId = this.getKey();

        BoundHashOperations<String, Object, Object> hashOps = this.redisTemplate.boundHashOps(KEY_PREFIX + userId);
        if (hashOps.hasKey(cart.getSkuId().toString())){
            String cartJson = hashOps.get(cart.getSkuId().toString()).toString();
            BigDecimal count = cart.getCount();
            cart = JSON.parseObject(cartJson, Cart.class);
            cart.setCount(count);
            hashOps.put(cart.getSkuId().toString(), JSON.toJSONString(cart));
            this.asyncService.updateCart(userId, cart);
            return;
        }
        throw new CartException("该用户的购物车不包含该条记录");
    }

    public void deleteCart(Long skuId) {
        String userId = this.getKey();

        BoundHashOperations<String, Object, Object> hashOps = this.redisTemplate.boundHashOps(KEY_PREFIX + userId);
        if (hashOps.hasKey(skuId.toString())){

            hashOps.delete(skuId.toString());
            this.asyncService.deleteCartByUserIdAndSkuId(userId, skuId);
            return ;
        }
        throw new CartException("该用户的购物车不包含该条记录。");
    }

    public void updateStatus(Cart cart) {
        String userId = this.getKey();

        BoundHashOperations<String, Object, Object> hashOps = this.redisTemplate.boundHashOps(KEY_PREFIX + userId);
        if (hashOps.hasKey(cart.getSkuId().toString())){
            String cartJson = hashOps.get(cart.getSkuId().toString()).toString();
            Boolean check = cart.getCheck();
            cart = JSON.parseObject(cartJson, Cart.class);
            cart.setCheck(check);
            hashOps.put(cart.getSkuId().toString(), JSON.toJSONString(cart));
            this.asyncService.updateCart(userId, cart);
            return;
        }
        throw new CartException("该用户的购物车不包含该条记录");
    }
}
