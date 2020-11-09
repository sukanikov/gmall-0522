package com.atguigu.gmall.search.controller;

import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.search.pojo.SearchParamVo;
import com.atguigu.gmall.search.pojo.SearchResponseVo;
import com.atguigu.gmall.search.service.SearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

//@RestController
@Controller
@RequestMapping("/search")
public class SearchController {

    @Autowired
    private SearchService searchService;    //spring默认代理为cglib，所以不写接口也可以

//    @GetMapping
//    public ResponseVo<SearchResponseVo> search(SearchParamVo paramVo){
//         SearchResponseVo searchResponseVo = this.searchService.search(paramVo);
//
//         return ResponseVo.ok(searchResponseVo);
//    }

    @GetMapping
    public String search(SearchParamVo paramVo, Model model) {
        SearchResponseVo searchResponseVo = this.searchService.search(paramVo);
        model.addAttribute("response", searchResponseVo);   //和th:object中的一样，为了解包
        model.addAttribute("searchParam", paramVo);
        return "search";
    }
}
