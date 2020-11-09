package com.atguigu.gmall.pms.service.impl;

import com.atguigu.gmall.pms.entity.SkuAttrValueEntity;
import com.atguigu.gmall.pms.entity.SkuImagesEntity;
import com.atguigu.gmall.pms.entity.SpuAttrValueEntity;
import com.atguigu.gmall.pms.feign.GmallSmsClient;
import com.atguigu.gmall.pms.mapper.SkuMapper;
import com.atguigu.gmall.pms.service.*;
import com.atguigu.gmall.pms.vo.SkuVo;
import com.atguigu.gmall.pms.vo.SpuAttrValueVo;
import com.atguigu.gmall.pms.vo.SpuVo;
import com.atguigu.gmall.sms.vo.SkuSaleVo;
import io.seata.spring.annotation.GlobalTransactional;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.PageParamVo;

import com.atguigu.gmall.pms.mapper.SpuMapper;
import com.atguigu.gmall.pms.entity.SpuEntity;
import org.springframework.util.CollectionUtils;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;


@Service("spuService")
public class SpuServiceImpl extends ServiceImpl<SpuMapper, SpuEntity> implements SpuService {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private SpuDescService descService;

    @Autowired
    private SpuAttrValueService attrValueService;

    @Autowired
    private SkuImagesService imagesService;

    @Autowired
    private SkuAttrValueService skuAttrValueService;

    @Autowired
    private SkuMapper skuMapper;

    @Autowired
    private GmallSmsClient smsClient;

    @Override
    public PageResultVo queryPage(PageParamVo paramVo) {
        IPage<SpuEntity> page = this.page(
                paramVo.getPage(),
                new QueryWrapper<SpuEntity>()
        );

        return new PageResultVo(page);
    }

    @Override
    public PageResultVo querySpuByCidAndPage(Long cid, PageParamVo pageParamVo) {
        QueryWrapper<SpuEntity> queryWrapper = new QueryWrapper<>();
        if(cid != 0){
            queryWrapper.eq("category_id", cid);
        }

        String key = pageParamVo.getKey();
        if(StringUtils.isNotBlank(key)){    //blank可以判断一连串的空格，比empty更好
//            SELECT * FROM pms_spu WHERE category_id=225 AND (id = 7 OR NAME LIKE '%7%')怎么实现
            queryWrapper.and(t -> t.eq("id", key).or().like("name", key));
        }

        IPage<SpuEntity> page = this.page(pageParamVo.getPage(), queryWrapper); //page来自于IService,所以这里可以写this.page

        return new PageResultVo(page);
    }

    @GlobalTransactional
    @Override
    public void bigSave(SpuVo spu) {
        // 1.保存Spu相关信息
        // 1.1. 保存spu表
        Long spuId = saveSpu(spu);

        // 1.2. 保存spu_desc表（id取自spu）
        this.descService.saveSpuDesc(spu, spuId);

//        int i = 1/0;

        // 1.3. 保存Spu_attr_value表
        saveBaseAttr(spu, spuId);

        // 2.保存sku相关信息
        saveSkus(spu, spuId);

        this.rabbitTemplate.convertAndSend("PMS_SPU_EXCHANGE", "item.insert", spuId);

//        int i = 1/0;

    }

    private Long saveSpu(SpuVo spu) {
        spu.setCreateTime(new Date());
        spu.setUpdateTime(spu.getCreateTime());
        this.save(spu);
        return spu.getId();
    }

    private void saveBaseAttr(SpuVo spu, Long spuId) {
        List<SpuAttrValueVo> baseAttrs = spu.getBaseAttrs();
        if (!CollectionUtils.isEmpty(baseAttrs)){
            List<SpuAttrValueEntity> spuAttrValueEntities = baseAttrs.stream().map(spuAttrValueVo -> {  //使用stream流的map和collect方法。把一个集合转为另一个集合
                SpuAttrValueEntity spuAttrValueEntity = new SpuAttrValueEntity();
                BeanUtils.copyProperties(spuAttrValueVo, spuAttrValueEntity);   //copy
                spuAttrValueEntity.setSpuId(spuId);     //这里手动设置了id，需要修改主键策略
                return spuAttrValueEntity;
            }).collect(Collectors.toList());
            this.attrValueService.saveBatch(spuAttrValueEntities);  //批量保存只有service有，mapper没有
        }
    }

    private void saveSkus(SpuVo spu, Long spuId) {
        List<SkuVo> skus = spu.getSkus();
        if (CollectionUtils.isEmpty(skus)){
            return;
        }

        skus.forEach(skuVo -> {
            // 2.1. 保存sku表
            skuVo.setSpuId(spuId);
            skuVo.setBrandId(spu.getBrandId());
            skuVo.setCatagoryId(spu.getCategoryId());
            List<String> images = skuVo.getImages();
            if (!CollectionUtils.isEmpty(images)){
                skuVo.setDefaultImage(StringUtils.isNotBlank(skuVo.getDefaultImage()) ? skuVo.getDefaultImage() : images.get(0));
            }
            this.skuMapper.insert(skuVo);
            Long skuId = skuVo.getId();

            // 2.2. 保存sku图片表
            if (!CollectionUtils.isEmpty(images)){
                this.imagesService.saveBatch(images.stream().map(image -> {
                    SkuImagesEntity skuImagesEntity = new SkuImagesEntity();
                    skuImagesEntity.setSkuId(skuId);
                    skuImagesEntity.setUrl(image);
                    skuImagesEntity.setDefaultStatus(StringUtils.equals(image, skuVo.getDefaultImage()) ? 1 : 0);
                    return skuImagesEntity;
                }).collect(Collectors.toList()));
            }

            // 2.3. 保存sku_attr_value表
            List<SkuAttrValueEntity> saleAttrs = skuVo.getSaleAttrs();
            if (!CollectionUtils.isEmpty(saleAttrs)){
                saleAttrs.forEach(skuAttrValueEntity -> skuAttrValueEntity.setSkuId(skuId));
                this.skuAttrValueService.saveBatch(saleAttrs);
            }

            // 3. 保存sku的营销信息
            SkuSaleVo skuSaleVo = new SkuSaleVo();
            BeanUtils.copyProperties(skuVo, skuSaleVo);
            skuSaleVo.setSkuId(skuVo.getId());
            this.smsClient.saveSales(skuSaleVo);
        });
    }


}