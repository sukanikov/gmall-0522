package com.atguigu.gmall.index.controller;

import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.index.service.IndexService;
import com.atguigu.gmall.pms.entity.CategoryEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

@Controller
public class IndexController {

    @Autowired
    private IndexService indexService;

    @GetMapping("xxx")
    @ResponseBody
    public String test(@RequestHeader("userId") String userId){
        return "userId:" + userId;
    }

    @GetMapping({"/", "/index"})
    public String toIndex(Model model){
        List<CategoryEntity> cates = this.indexService.queryLv1Categories();

        model.addAttribute("categories", cates);
        return "index";
    }

    @GetMapping("/index/cates/{pid}")
    @ResponseBody
    public ResponseVo< List<CategoryEntity>> queryLv2CategoriesWithSubsByPid(@PathVariable Long pid){
        List<CategoryEntity> categoryEntities = this.indexService.queryLv2CategoriesWithSubsByPid(pid);
        return ResponseVo.ok(categoryEntities);
    }
}
