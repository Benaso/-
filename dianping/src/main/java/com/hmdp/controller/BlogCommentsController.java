package com.hmdp.controller;


import cn.hutool.core.date.DateUtil;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.hmdp.entity.BlogComments;
import com.hmdp.entity.User;
import com.hmdp.service.IBlogCommentsService;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/comment")
public class BlogCommentsController {
        @Resource
        private IBlogCommentsService commentService;

        @Resource
        private IBlogService blogService;
        @Resource
        private IUserService userService;


        // 新增或者更新
        @PostMapping
        public Result save(@RequestBody BlogComments comment) {
            if (comment.getId()==null){
                Long id = UserHolder.getUser().getId();
                comment.setUserId(id);
                User user = userService.getById(id);
                comment.setNickName(user.getNickName());
                comment.setIcon(user.getIcon());

                if (comment.getAnswerId()!=null) {    //判断回复则进行处理
                    Long aid = comment.getAnswerId();
                    BlogComments pComment = commentService.getById(aid);
                    if (pComment.getParentId() != null && pComment.getParentId()!=0L) {//祖宗id,如果当前回复的父级有祖宗，那么就设置相同的祖宗
                        comment.setParentId(pComment.getParentId());
                    }
                }else{
                    comment.setParentId(0L);
                }
            }
            Long blogId = comment.getBlogId();
            Blog blog = blogService.query().eq("id", blogId).one();
            blog.setIsRead(false);
            boolean saveOrUpdate = blogService.saveOrUpdate(blog);
            boolean update = commentService.saveOrUpdate(comment);
            if (update && saveOrUpdate) {
                boolean id = blogService.update().setSql("comments=comments+1")
                        .eq("id", comment.getBlogId()).update();
                if (!id) {
                    return Result.fail("出现错误");
                }
            }

            return Result.ok();
        }

        @DeleteMapping("/{id}")
        public Result delete(@PathVariable Long id) {
            commentService.removeById(id);
            return Result.ok();
        }

        @PostMapping("/del/batch")
        public Result deleteBatch(@RequestBody List<Long> ids) {
            commentService.removeByIds(ids);
            return Result.ok();
        }

        @GetMapping
        public Result findAll() {
            return Result.ok(commentService.list());
        }

        @GetMapping("/tree/{blogId}")
        public Result findTree(@PathVariable Long blogId) {  //查询所有的评论数据
//        QueryWrapper<Comment> queryWrapper = new QueryWrapper<>();
//        queryWrapper.eq("article_id",articleId);
            List<BlogComments> articleComment = commentService.findCommentDetail(blogId);
            //查询评论数据不包括回复
            List<BlogComments> originList = articleComment.stream().filter(comment -> comment.getParentId() == 0).collect(Collectors.toList());
//            //设置评论数据的子节点，也就是回复内容
            for (BlogComments origin:originList){
                //父子关系,表示回复的对象集合
                List<BlogComments> comments = articleComment.stream().filter(comment -> origin.getId().equals(comment.getParentId())).collect(Collectors.toList());
                comments.forEach(comment -> {
                    Optional<BlogComments> pComment = articleComment.stream().filter(c1 -> c1.getId().equals(comment.getAnswerId())).findFirst();  // 找到当前评论的父级
                    pComment.ifPresent((v -> {  // 找到父级评论的用户id和用户昵称，并设置给当前的回复对象
                        comment.setAUserID(v.getUserId());
                        comment.setANickname(v.getNickName());
                    }));
                });
                origin.setChildren(comments);
            }
//            return Result.ok(originList);
            return Result.ok(originList);
        }

        @GetMapping("/{id}")
        public Result findOne(@PathVariable Long id) {
            return Result.ok(commentService.getById(id));
        }

    }
