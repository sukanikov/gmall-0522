package com.atguigu.gmall.search.service;

import com.atguigu.gmall.pms.entity.BrandEntity;
import com.atguigu.gmall.pms.entity.CategoryEntity;
import com.atguigu.gmall.search.pojo.Goods;
import com.atguigu.gmall.search.pojo.SearchParamVo;
import com.atguigu.gmall.search.pojo.SearchResponseAttrVo;
import com.atguigu.gmall.search.pojo.SearchResponseVo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.text.Text;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.nested.ParsedNested;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedLongTerms;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedStringTerms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SearchService {
    @Autowired
    private RestHighLevelClient restHighLevelClient;

    private static final ObjectMapper MAPPER = new ObjectMapper();


    public SearchResponseVo search(SearchParamVo paramVo) {
        try {
            SearchRequest searchRequest = new SearchRequest(new String[]{"goods"}, buildDSL(paramVo));
            SearchResponse searchResponse = this.restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);
            System.out.println(searchResponse);

            //解析搜索的响应结果集
            SearchResponseVo searchResponseVo = this.parseSearchResult(searchResponse);

            //分页参数在搜索的请求参数中
            searchResponseVo.setPageNum(paramVo.getPageNum());
            searchResponseVo.setPageSize(paramVo.getPageSize());

            return searchResponseVo;
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    private SearchResponseVo parseSearchResult(SearchResponse searchResponse) {
        SearchResponseVo responseVo = new SearchResponseVo();

        //1. 解析hits命中对象
        SearchHits hits = searchResponse.getHits();

        //1.1. 总命中记录数
        responseVo.setTotal(hits.getTotalHits());

        //1.2.当前页记录
        SearchHit[] hitsHits = hits.getHits();
        if (null == hitsHits || hitsHits.length == 0) {
            return null;
        }

        //把SearchHit数组转化为List<Goods>集合
        List<Goods> goodsList = Arrays.stream(hitsHits).map(hitsHit -> {
            try {
                String json = hitsHit.getSourceAsString();
                Goods goods = MAPPER.readValue(json, Goods.class);  //使用jackson将每个hitsHit转为json字符串，然后再反序列化为Goods对象

                //用高亮title替换goods中的普通title
                Map<String, HighlightField> highlightFields = hitsHit.getHighlightFields();
                HighlightField highlightField = highlightFields.get("title");
                Text[] fragments = highlightField.getFragments();   //高亮字段可能为集合字段，为了兼容，所以返回集合
                goods.setTitle(fragments[0].string());
                return goods;

            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }

            return null;

        }).collect(Collectors.toList());

        responseVo.setGoodsList(goodsList);

        //2. 解析聚合结果集
        Map<String, Aggregation> aggregationMap = searchResponse.getAggregations().asMap();

        //2.1. 解析品牌聚合结果类
        ParsedLongTerms brandIdAgg = (ParsedLongTerms) aggregationMap.get("brandIdAgg");

        List<? extends Terms.Bucket> brandBuckets = brandIdAgg.getBuckets();
        if (!CollectionUtils.isEmpty(brandBuckets)) {
            responseVo.setBrands(brandBuckets.stream().map(bucket -> {  //把每个桶变成brandEntity对象
                BrandEntity brandEntity = new BrandEntity();

                //获取attrIdAgg中的key，也就是品牌id
                brandEntity.setId(bucket.getKeyAsNumber().longValue());

                //获取子聚合map
                Map<String, Aggregation> subAggregationMap = bucket.getAggregations().asMap();

                //获取品牌名称子聚合
                ParsedStringTerms brandNameAgg = (ParsedStringTerms) subAggregationMap.get("brandNameAgg");
                List<? extends Terms.Bucket> nameAggBuckets = brandNameAgg.getBuckets();
                //获取桶中的第一个元素（按道理此桶中有且仅有一个元素，就是key品牌）
                if (!CollectionUtils.isEmpty(nameAggBuckets)){  //避免有的id没有名称
                    brandEntity.setName(nameAggBuckets.get(0).getKeyAsString());
                }

                //获取logo子聚合，如法炮制
                ParsedStringTerms logoAgg = (ParsedStringTerms) subAggregationMap.get("logoAgg");
                List<? extends Terms.Bucket> logoAggBuckets = logoAgg.getBuckets();
                if (!CollectionUtils.isEmpty(logoAggBuckets)){
                    brandEntity.setLogo(logoAggBuckets.get(0).getKeyAsString());
                }

                return brandEntity;
            }).collect(Collectors.toList()));
        }

        //2.2. 解析分类聚合结果类
        ParsedLongTerms categoryIdAgg = (ParsedLongTerms) aggregationMap.get("categoryIdAgg");

        List<? extends Terms.Bucket> categoryBuckets = categoryIdAgg.getBuckets();
        if (!CollectionUtils.isEmpty(categoryBuckets)){
            responseVo.setCategories(categoryBuckets.stream().map(bucket -> {
                CategoryEntity categoryEntity = new CategoryEntity();

                categoryEntity.setId(bucket.getKeyAsNumber().longValue());

                ParsedStringTerms categoryNameAgg = bucket.getAggregations().get("categoryNameAgg");
                List<? extends Terms.Bucket> nameAggBuckets = categoryNameAgg.getBuckets();
                if (!CollectionUtils.isEmpty(nameAggBuckets)){
                    categoryEntity.setName(nameAggBuckets.get(0).getKeyAsString());
                }

                return categoryEntity;
            }).collect(Collectors.toList()));
        }


//        //2.3. 解析规格参数聚合结果类（有嵌套）
        ParsedNested attrAgg = (ParsedNested) aggregationMap.get("attrAgg");

        ParsedLongTerms attrIdAgg = attrAgg.getAggregations().get("attrIdAgg");

        List<? extends Terms.Bucket> attrIdAggBuckets = attrIdAgg.getBuckets();
        if (!CollectionUtils.isEmpty(attrIdAggBuckets)){
            responseVo.setFilters(attrIdAggBuckets.stream().map(bucket -> {
                SearchResponseAttrVo searchResponseAttrVo = new SearchResponseAttrVo();

                searchResponseAttrVo.setAttrId(bucket.getKeyAsNumber().longValue());

                Map<String, Aggregation> subAggregationMap = bucket.getAggregations().asMap();

                ParsedStringTerms attrNameAgg = (ParsedStringTerms) subAggregationMap.get("attrNameAgg");
                List<? extends Terms.Bucket> nameAggBuckets = attrNameAgg.getBuckets();
                if (!CollectionUtils.isEmpty(nameAggBuckets)){
                    searchResponseAttrVo.setAttrName(nameAggBuckets.get(0).getKeyAsString());
                }

                ParsedStringTerms attrValueAgg = (ParsedStringTerms) subAggregationMap.get("attrValueAgg");
                List<? extends Terms.Bucket> valueAggBuckets = attrValueAgg.getBuckets();
                if (!CollectionUtils.isEmpty(valueAggBuckets)){
                    searchResponseAttrVo.setAttrValues(valueAggBuckets.stream().map(Terms.Bucket::getKeyAsString).collect(Collectors.toList()));
                }

                return searchResponseAttrVo;
            }).collect(Collectors.toList()));
        }


        return responseVo;
    }

    //构建查询条件
    private SearchSourceBuilder buildDSL(SearchParamVo paramVo) {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        String keyword = paramVo.getKeyword();

        if (StringUtils.isBlank(keyword)) {
            return sourceBuilder;
        }

        //1.构建查询及过滤条件
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        sourceBuilder.query(boolQueryBuilder);

        //1.1. 构建匹配查询
        boolQueryBuilder.must(QueryBuilders.matchQuery("title", keyword).operator(Operator.AND));

        //1.2. 构建过滤条件
        //1.2.1. 构建品牌过滤
        List<Long> brandId = paramVo.getBrandId();
        if (!CollectionUtils.isEmpty(brandId)) {
            boolQueryBuilder.filter(QueryBuilders.termsQuery("brandId", brandId));
        }

        //1.2.2. 构建分类过滤
        List<Long> categoryId = paramVo.getCategoryId();
        if (!CollectionUtils.isEmpty(categoryId)) {
            boolQueryBuilder.filter(QueryBuilders.termsQuery("categoryId", categoryId));
        }

        //1.2.3. 构建价格区间过滤
        Double priceFrom = paramVo.getPriceFrom();
        Double priceTo = paramVo.getPriceTo();
        if (priceFrom != null || priceTo != null) {
            RangeQueryBuilder rangeQuery = QueryBuilders.rangeQuery("price");
            boolQueryBuilder.filter(rangeQuery);

            if (priceFrom != null) {
                rangeQuery.gte(priceFrom);
            }
            if (priceTo != null) {
                rangeQuery.lte(priceTo);
            }
        }

        //1.2.4. 构建是否有货过滤
        Boolean store = paramVo.getStore();
        if (null != store) {
            boolQueryBuilder.filter(QueryBuilders.termsQuery("store", store));
        }

        //1.2.5. 构建规格参数嵌套过滤（4：8G-12G，5：128G-256G）
        List<String> props = paramVo.getProps();
        if (!CollectionUtils.isEmpty(props)) {
            //4: 8G-12G
            props.forEach(prop -> {
                String[] attrs = StringUtils.split(prop, ":");
                if (null != attrs && attrs.length == 2) {       //分割aa:bb格式，长度必为2
                    BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
                    boolQuery.must(QueryBuilders.termQuery("searchAttrs.attrId", attrs[0]));

                    String attrValue = attrs[1];
                    String[] attrValues = StringUtils.split(attrValue, "-");    //8G-12G
                    boolQuery.must(QueryBuilders.termsQuery("searchAttrs.attrValue", attrValues));
                    boolQueryBuilder.filter(QueryBuilders.nestedQuery("searchAttrs", boolQuery, ScoreMode.None));   //得分模式，不需要得分
                }
            });
        }

        //2.构建排序条件：1-价格升序 2-价格降序 3-销量降序 4-新品降序  默认0，使用得分排序
        Integer sort = paramVo.getSort();
        if (null != sort) {
            switch (sort) {
                case 1:
                    sourceBuilder.sort("price", SortOrder.ASC);
                    break;
                case 2:
                    sourceBuilder.sort("price", SortOrder.DESC);
                    break;
                case 3:
                    sourceBuilder.sort("sales", SortOrder.DESC);
                    break;
                case 4:
                    sourceBuilder.sort("createTime", SortOrder.DESC);
                    break;
                default:
                    sourceBuilder.sort("_score", SortOrder.DESC);
                    break;
            }
        }

        //3.构建分页条件
        Integer pageNum = paramVo.getPageNum();
        Integer pageSize = paramVo.getPageSize();
        sourceBuilder.from((pageNum - 1) * pageSize);
        sourceBuilder.size(pageSize);

        //4.构建高亮
        sourceBuilder.highlighter(new HighlightBuilder()
                .field("title")
                .preTags("<font style='color:red'>")
                .postTags("</font>"));

        //5.构建聚合查询
        //5.1 品牌聚合
        sourceBuilder.aggregation(AggregationBuilders.terms("brandIdAgg").field("brandId")
                .subAggregation(AggregationBuilders.terms("brandNameAgg").field("brandName"))   //给前一个聚合添加子聚合
                .subAggregation(AggregationBuilders.terms("logoAgg").field("logo")));           //和前一个子聚合同级，注意点的位置

        //5.2 分类聚合
        sourceBuilder.aggregation(AggregationBuilders.terms("categoryIdAgg").field("categoryId")
                .subAggregation(AggregationBuilders.terms("categoryNameAgg").field("categoryName")));

        //5.3 规格参数聚合
        sourceBuilder.aggregation(AggregationBuilders.nested("attrAgg", "searchAttrs")
                .subAggregation(AggregationBuilders.terms("attrIdAgg").field("searchAttrs.attrId")
                        .subAggregation(AggregationBuilders.terms("attrNameAgg").field("searchAttrs.attrName"))
                        .subAggregation(AggregationBuilders.terms("attrValueAgg").field("searchAttrs.attrValue"))));

        //6.结果集过滤（过滤不需要的字段，降低带宽）
        sourceBuilder.fetchSource(new String[]{"skuId", "title", "subTitle", "defaultImage", "price"}, null);

        System.out.println(sourceBuilder);
        return sourceBuilder;
    }
}
