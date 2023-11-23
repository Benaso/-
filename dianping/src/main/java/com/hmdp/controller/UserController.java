package com.hmdp.controller;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.entity.UserInfo;
import com.hmdp.service.IUserInfoService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private IUserService userService;

    @Resource
    private IUserInfoService userInfoService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 发送手机验证码
     */
    @PostMapping("code")
    public Result sendCode(@RequestParam("phone") String phone, HttpSession session) {
        // TODO 发送短信验证码并保存验证码
        return userService.sendCode(phone,session);
    }

    /**
     * 登录功能
     * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
     */
    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginForm, HttpSession session){
        // TODO 实现登录功能
        return userService.login(loginForm,session);
    }

    /**
     * 登出功能
     * @return 无
     */
    @PostMapping("/logout")
    public Result logout(){
        // TODO 实现登出功能
        return userService.logout();
    }

    @GetMapping("/me")
    public Result me(){

        // TODO 获取当前登录的用户并返回

        return userService.getMe();

    }

    @GetMapping("/info/{id}")
    public Result info(@PathVariable("id") Long userId){
        //从缓存中存数据
        String key = "user:info"+userId;
        String cache = stringRedisTemplate.opsForValue().get(key);
        UserInfo userInfo = null;
        if (cache!=null){
             userInfo = JSONUtil.toBean(cache, UserInfo.class);

        }else {
            // 查询详情
            userInfo = userInfoService.getById(userId);
            if (userInfo == null) {
                // 没有详情，应该是第一次查看详情
                return Result.ok();
            }
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(userInfo));


        }
        // 返回
        UserDTO user = UserHolder.getUser();
        Long id = user.getId();
        String key1 = "followMine:" + id;
        String key2 = "followYours:" + id;
        stringRedisTemplate.delete(key1);
        stringRedisTemplate.delete(key2);
        return Result.ok(userInfo);
    }

    /**
     * 点击他人主页时查看它的详情
     * @param userId
     * @return
     */
    @GetMapping("/{id}")
    public Result queryUserById(@PathVariable("id") Long userId){
        // 查询详情
        User user = userService.getById(userId);
        if (user == null) {
            return Result.ok();
        }
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        // 返回
        return Result.ok(userDTO);
    }

    /**
     * 签到功能
     * @return
     */
    @PostMapping("/sign")
    public Result sign(){
        return userService.sign();
    }

    /**
     * 获取详细信息
     */
    @PutMapping("/info")
    public Result updateInfo(@RequestBody UserInfo user){
        return userInfoService.updateInfo(user);
    }

    /**
     * 获取详细信息
     */
    @GetMapping("/getInfo")
    public Result getUserInfo(){
        return userInfoService.getUserInfo();
    }

    /**
     * 改变昵称
     */
    @PutMapping("/changeName")
    public Result updateInfo(@RequestParam("nickName") String nickName ){
        return userService.changeName(nickName);
    }

    /**
     * 获取用户
     */
    @GetMapping("/getUser")
    public Result getUser(@RequestParam("id")String id){
        User user = userService.getById(id);
        return Result.ok(user);
    }
}
