package com.atguigu.gmall.cart.controller;

import com.atguigu.gmall.cart.interceptor.LoginInterceptor;
import com.atguigu.gmall.cart.pojo.Cart;
import com.atguigu.gmall.cart.service.CartService;
import com.atguigu.gmall.common.bean.ResponseVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

@Controller
public class CartController {

    @Autowired
    private CartService cartService;

    @GetMapping("test")
    public String test(HttpServletRequest request){
        System.out.println("这是一个Handler方法。。。。。。。。。。。。。" + LoginInterceptor.getUserInfo());
        return "hello interceptors";
    }

    /**
     * 添加商品到购物车，并重定向到新增购物车成功页面
     * @param cart
     * @return
     */
    @GetMapping
    public String addCart(Cart cart){
        this.cartService.addCart(cart);

        return "redirect:http://cart.gmall.com/addCart.html?skuId=" + cart.getSkuId();
    }

    /**
     * 新增购物车成功页面，本质就是根据用户登录信息和skuId查询
     * @param skuId
     * @param model
     * @return
     */
    @GetMapping("addCart.html")
    public String queryCart(@RequestParam("skuId") Long skuId, Model model){
        Cart cart = this.cartService.queryCart(skuId);

        model.addAttribute("cart", cart);

        return "addCart";
    }

    /**
     * 跳转到购物车结算页面，查询购物车所有商品
     * @param model
     * @return
     */
    @GetMapping("cart.html")
    public String queryCarts(Model model){
        List<Cart> carts = this.cartService.queryCarts();
        model.addAttribute("carts", carts);
        return "cart";
    }

    /**
     * 修改购物车的商品数量
     * @param cart
     * @return
     */
    @PostMapping("updateNum")
    @ResponseBody
    public ResponseVo updateNum(@RequestBody Cart cart){
        this.cartService.updateNum(cart);
        return ResponseVo.ok();
    }

    /**
     * 修改购物车的选中状态
     * @param cart
     * @return
     */
    @PostMapping("updateStatus")
    @ResponseBody
    public ResponseVo updateStatus(@RequestBody Cart cart){
        this.cartService.updateStatus(cart);
        return ResponseVo.ok();
    }

    @PostMapping("deleteCart")
    @ResponseBody
    public ResponseVo deleteCart(@RequestParam("skuId")Long skuId){
        this.cartService.deleteCart(skuId);
        return ResponseVo.ok();
    }


}
