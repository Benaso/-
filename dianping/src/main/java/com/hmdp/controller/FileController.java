package com.hmdp.controller;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.ServletContext;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;

@RestController
@RequestMapping("/file")
public class FileController {

    @Value("${files.upload.path}")
    private String filePath;

    @Resource
    StringRedisTemplate stringRedisTemplate;
    @Resource
    private UserMapper userMapper;

    @PostMapping("/upload/{userId}")
    public String upload( MultipartFile file,@PathVariable("userId")Long userId) {
        String originalFilename = file.getOriginalFilename();
        String type = FileUtil.extName(originalFilename);
        // 定义一个文件唯一的标识码
        String fileUUID = IdUtil.fastSimpleUUID() + StrUtil.DOT + type;
//        String fileUploadPath = this.getClass().getResource("/").getPath();
//        String fileUploadPath = System.getProperty("user.dir")+ "\\nginx-1.18.0\\html\\hmdp"+ filePath;
        String fileUploadPath = SystemConstants.IMAGE_UPLOAD_DIR + filePath;
        File uploadFile = new File(fileUploadPath + fileUUID);
        // 检查目录是否存在，如不存在则创建
        File parentFile = uploadFile.getParentFile();
        if (!parentFile.exists()) {
            parentFile.mkdirs();
        }
        String url;
        try {

            // 上传文件到磁盘
            file.transferTo(uploadFile);
            // 数据库若不存在重复文件，则不删除刚才上传的文件
            url = filePath + fileUUID;
            User user = userMapper.selectById(userId);
            if (user != null) {
                // 存储数据库
                user.setIcon(url);
                userMapper.updateById(user);
                String key = "user:"+userId;
                stringRedisTemplate.delete(key);
                stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(user));
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return url;
    }
}
