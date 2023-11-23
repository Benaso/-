package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.TypeReference;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private IUserService userService;

    /**
     * 关注或者取关操作
     * @param followUserId
     * @param isFollow
     * @return
     */
    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        //0.获取登录的用户
        Long userId = UserHolder.getUser().getId();
        String key = "follows:"+userId;
        //1.判断是否是关注还是取关
        if (isFollow) {
            //2.关注，新增数据
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);//被关注的人
            boolean isSuccess = save(follow);
            if (isSuccess) {
                //把关注用户的id，放入redis的set集合，sadd userId followUserId
                stringRedisTemplate.opsForSet().add(key,followUserId.toString());
            }
        }else {
            //3.取关，删除delete from tb_follow where user_id =？ and follow_user_id = ?
            boolean isSuccess = remove(new QueryWrapper<Follow>()
                    .eq("user_id", userId)
                    .eq("follow_user_id", followUserId));
            if (isSuccess){
                //把关注用户的id从redis集合中移除
                stringRedisTemplate.opsForSet().remove(key,followUserId.toString());
            }
        }
        String key3 = "followMine:" + userId;
        String key4 = "followYours:" + userId;
        stringRedisTemplate.delete(key3);
        stringRedisTemplate.delete(key4);
        return Result.ok();
    }

    /**
     * 判断是否关注
     * @param followUserId
     * @return
     */
    @Override
    public Result isFollow(Long followUserId) {
        //1.获取登录用户
        Long userID = UserHolder.getUser().getId();
        //2.查询是否关注 select count(*) from tb_follow where user_id =? and follow_user_id = ?
        Integer count = query().eq("user_id", userID).eq("follow_user_id", followUserId).count();
        //3.判断
        return Result.ok(count>0);
    }



    @Override
    public Result followCommons(Long followUserId) {
        //1.获取当前用户
        Long userId = UserHolder.getUser().getId();
        String key = "follows:"+userId;
        String key2 = "follows:"+followUserId;
        //2.求交集
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key, key2);
        if (intersect==null || intersect.isEmpty()){
            //无交集
            return Result.ok(Collections.emptyList());
        }
        //3.解析id集合
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        //4.查询用户
        List<UserDTO> users = userService.listByIds(ids).stream().map(
                user -> BeanUtil.copyProperties(user, UserDTO.class)
        ).collect(Collectors.toList());
        return Result.ok(users);
    }

    @Override
    public Result followMine(Long id) {
        Long userId = UserHolder.getUser().getId();
        String key = "followMine:" + userId;
        String s = stringRedisTemplate.opsForValue().get(key);
        List<User> userList = new ArrayList<>();

        if (s != null) {
          userList = JSONUtil.toList(s, User.class);
            return Result.ok(userList);
        } else {
            List<Follow> follow_user_id = query().eq("follow_user_id", userId).list();
            if (follow_user_id.size() == 0) {
                return Result.ok();
            }
            for (Follow f:follow_user_id){
                Long owner = f.getUserId();
                User user = userService.getById(owner);
                userList.add(user);
            }
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(userList));
            return Result.ok(userList);
        }
    }
    @Override
    public Result followYours(Long id) {
        Long userId = UserHolder.getUser().getId();
        String key = "followYours:" + userId;
        String s = stringRedisTemplate.opsForValue().get(key);
        List<User> userList = new ArrayList<>();

        if (s != null) {
            userList = JSONUtil.toList(s, User.class);
            return Result.ok(userList);
        } else {
            List<Follow> follow_user_id = query().eq("user_id", userId).list();
            if (follow_user_id.size() == 0) {
                return Result.ok();
            }
            for (Follow f:follow_user_id){
                Long owner = f.getFollowUserId();
                User user = userService.getById(owner);
                userList.add(user);
            }
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(userList));
            return Result.ok(userList);
        }
    }
}
