package com.hmdp.controller;

import com.hmdp.dto.PageResult;
import com.hmdp.dto.RequestParams;
import com.hmdp.dto.Result;
import com.hmdp.service.IShopService;
import com.hmdp.service.impl.ShopServiceImpl;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/map")
public class MapController {
    @Resource
    private IShopService shopService;

    @GetMapping("/shop")
    public PageResult search(@RequestBody RequestParams params) {
        return shopService.search(params);
    }

    @GetMapping("suggestion")
    public Result getSuggestion(@RequestParam("key") String prefix){
        return shopService.getSuggestion(prefix);
    }
}
