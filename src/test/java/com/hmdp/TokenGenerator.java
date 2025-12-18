package com.hmdp;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.service.IUserService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;

/**
 * Token生成器 - 用于生成JMeter测试所需的token.txt文件
 */
@SpringBootTest
public class TokenGenerator {

    @Resource
    private IUserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 生成指定数量的token到token.txt文件
     * 默认生成1000个token
     */
    @Test
    public void generateTokens() throws IOException {
        int count = 1000; // 生成的token数量
        String fileName = "token.txt"; // 输出文件名
        
        generateTokensToFile(count, fileName);
    }

    /**
     * 自定义生成token数量和文件名
     * @param count 生成的token数量
     * @param fileName 输出文件名
     */
    public void generateTokensToFile(int count, String fileName) throws IOException {
        // 查询所有用户
        List<User> users = userService.list();
        
        if (users.isEmpty()) {
            System.out.println("数据库中没有用户数据,请先创建用户!");
            return;
        }
        
        System.out.println("开始生成token,共" + count + "个");
        System.out.println("数据库用户总数: " + users.size());
        
        // 创建文件写入器
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName))) {
            for (int i = 0; i < count; i++) {
                // 循环使用用户列表
                User user = users.get(i % users.size());
                
                // 生成token
                String token = UUID.randomUUID().toString(true);
                
                // 将User对象转为UserDTO
                UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
                
                // 将UserDTO转为HashMap
                Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                        CopyOptions.create()
                                .setIgnoreNullValue(true)
                                .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
                
                // 存储到Redis
                String tokenKey = LOGIN_USER_KEY + token;
                stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
                
                // 设置token有效期(30分钟)
                stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);
                
                // 写入文件
                writer.write(token);
                writer.newLine();
                
                if ((i + 1) % 100 == 0) {
                    System.out.println("已生成: " + (i + 1) + " 个token");
                }
            }
        }
        
        System.out.println("Token生成完成! 文件保存路径: " + fileName);
        System.out.println("请在JMeter中配置CSV Data Set Config读取此文件");
    }
}
