package com.hmdp.controller;


import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.BlogComments;
import com.hmdp.entity.User;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 */
@RestController
@RequestMapping("/blog")
public class BlogController {

    @Resource
    private IBlogService blogService;
    @Resource
    private IUserService userService;

    @Resource
    StringRedisTemplate stringRedisTemplate;

    @PostMapping
    public Result saveBlog(@RequestBody Blog blog) {
        UserDTO user = UserHolder.getUser();
        Page<Blog> page = blogService.query().eq("user_id", user.getId())
                .page(new Page<>(1, SystemConstants.MAX_PAGE_SIZE));
        long total = page.getTotal();
        int pageSize = SystemConstants.MAX_PAGE_SIZE;
        int totalPages = (int) ((total + pageSize - 1) / pageSize);
        String key ="";
        for (int i = 1; i <= totalPages; i++) {

            key = "blog:of:"+user.getId()+"_"+i;
            stringRedisTemplate.delete(key);
        }

        return blogService.saveBlog(blog);
    }

    @PutMapping("/like/{id}")
    public Result likeBlog(@PathVariable("id") Long id) {
//        // 修改点赞数量
//        blogService.update()
//                .setSql("liked = liked + 1").eq("id", id).update();
//        return Result.ok();
        return blogService.likeBlog(id);
    }

    @GetMapping("/cache")
    public Result queryCache(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        UserDTO user = UserHolder.getUser();
        String key = "blog:of:"+user.getId()+"_"+current;
        stringRedisTemplate.delete(key);
        return Result.ok();
    }

//    @GetMapping("/of/me")
//    public Result queryMyBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
//        // 获取登录用户
//        UserDTO user = UserHolder.getUser();
//        //从缓存中存数据
//        String key = "blog:of:"+user.getId()+"_"+current;
//
//        String cachedData = stringRedisTemplate.opsForValue().get(key);
//
//        if (cachedData != null) {
//            // Data found in cache, return it
//            List<Blog> records = JSONUtil.toList(JSONUtil.parseArray(cachedData), Blog.class);
//            return Result.ok(records);
//        } else {
//            // Data not found in cache, query database
//            Page<Blog> page = blogService.query().eq("user_id", user.getId())
//                    .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
//            List<Blog> records = page.getRecords();
//            long pageTotal = page.getTotal();
//            // Store the retrieved data in Redis cache
//            Map<String, Object> resultData = new HashMap<>();
//            resultData.put("records", records);
//            resultData.put("totalPage", pageTotal);
//            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(resultData));
//            return Result.ok(resultData);
//        }
//        // 根据用户查询
////        Page<Blog> page = blogService.query()
////                .eq("user_id", user.getId()).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
////        // 获取当前页数据
////        List<Blog> records = page.getRecords();
////        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(records));
////        return Result.ok(records);
//    }

    @GetMapping("/of/me")
    public Result queryMyBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();

        // 从缓存中存数据
        String key = "blog:of:" + user.getId() + "_" + current;
        String cachedData = stringRedisTemplate.opsForValue().get(key);

        if (cachedData != null) {
            // 缓存中存在数据，则将缓存的数据转换为Map类型并返回
            // 初始化一个ObjectMapper对象
            ObjectMapper objectMapper = new ObjectMapper();

            // 定义一个TypeReference对象，用于指定转换后的类型
            TypeReference<HashMap<String, Object>> typeRef = new TypeReference<HashMap<String, Object>>() {};
            // 将String类型的缓存数据转换为Map类型
            try {
                // 通过readValue方法将缓存数据转换为Map类型
                Map<String, Object> resultData = objectMapper.readValue(cachedData, typeRef);
                return Result.ok(resultData);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }

        } else {
            // 缓存中不存在数据，则从数据库中查询数据
            Page<Blog> page = blogService.query().eq("user_id", user.getId())
                    .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
            List<Blog> records = page.getRecords();
            long pageTotal = page.getPages();

            // 将查询到的数据存入Map中
            Map<String, Object> resultData = new HashMap<>();
            resultData.put("records", records);
            resultData.put("totalPage", pageTotal);

            // 将数据转换为JSON字符串，并存入缓存
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(resultData));

            return Result.ok(resultData);
        }
    }

    @GetMapping("/hot")
    public Result queryHotBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
      return blogService.queryHotBlog(current);
    }

    @GetMapping("/{id}")
    public Result queryBlogById(@PathVariable("id")Long id){
        return blogService.queryBlogById(id);
    }

    @GetMapping("/likes/{id}")
    public Result queryBlogLikes(@PathVariable("id") Long id) {
//        // 修改点赞数量
//        blogService.update()
//                .setSql("liked = liked + 1").eq("id", id).update();
//        return Result.ok();
        return blogService.queryBlogLikes(id);
    }

    /**
     * 点击他人主页时查询所有的博客
     * @param current
     * @param id
     * @return
     */
    // BlogController
    @GetMapping("/of/user")
    public Result queryBlogByUserId(
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam("id") Long id) {
        // 根据用户查询
        Page<Blog> page = blogService.query()
                .eq("user_id", id).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return Result.ok(records);
    }

    @PostMapping("/queryMe/read")
    public Result addToMessageList(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        UserDTO user = UserHolder.getUser();
        Long id = user.getId();
        Page<Blog> page = blogService.query().eq("user_id", id).
                eq("isRead", false).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        Map<String, Object> resultData = new HashMap<>();
        resultData.put("records", page.getRecords());
        resultData.put("totalPage", page.getPages());
        return Result.ok(resultData);
    }

    @GetMapping("/of/follow")
    public Result queryBlogOfFollow(
            @RequestParam("lastId") Long max, @RequestParam(value = "offset", defaultValue = "0") Integer offset){
        return blogService.queryBlogOfFollow(max, offset);
    }

    @PostMapping("/removeId")
    public Result queryBlogOfremoveId(
            @RequestParam("blogId") Long blogId){
        UserDTO user = UserHolder.getUser();
        Long userId = user.getId();
        Blog blog = blogService.query().eq("user_id", userId)
                .eq("id", blogId).one();
        blog.setIsRead(true);
        blogService.saveOrUpdate(blog);
        return Result.ok(blog);

    }

    @DeleteMapping("/del/{id}")
    public Result delete(@PathVariable Long id) {
        blogService.removeById(id);
        UserDTO user = UserHolder.getUser();
        Page<Blog> page = blogService.query().eq("user_id", user.getId())
                .page(new Page<>(1, SystemConstants.MAX_PAGE_SIZE));
        long total = page.getTotal();
        int pageSize = SystemConstants.MAX_PAGE_SIZE;
        int totalPages = (int) ((total + pageSize - 1) / pageSize);
        String key ="";
        for (int i = 1; i <= totalPages; i++) {

            key = "blog:of:"+user.getId()+"_"+i;
            stringRedisTemplate.delete(key);
        }
        return Result.ok();
    }
}
