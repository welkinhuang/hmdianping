package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1. 校验手机号
        if(RegexUtils.isPhoneInvalid(phone))
        {
            return Result.fail("手机号格式错误！");
        }

        //2. 生成验证码
        String code = RandomUtil.randomNumbers(6);
        
        //3. 保存验证码到Redis，设置2分钟有效期
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
        
        //4. 发送验证码（模拟）
        log.debug("发送短信验证码成功，验证码: {}", code);
        return Result.ok();

    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        log.debug("开始登录流程，手机号: {}", loginForm.getPhone());
        
        //1. 校验手机号
        String phone = loginForm.getPhone();
        if(RegexUtils.isPhoneInvalid(phone))
        {
            log.debug("手机号格式错误: {}", phone);
            return Result.fail("手机号格式错误！");
        }
        
        //2. 从Redis获取验证码并校验
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();
        log.debug("验证码校验 - 缓存: {}, 输入: {}", cacheCode, code);
        if(cacheCode == null || !cacheCode.equals(code))
        {
            return Result.fail("验证码错误");
        }

        //3. 根据手机号查询用户
        User user = query().eq("phone", phone).one();
        
        //4. 判断用户是否存在，不存在则创建
        if(user == null)
        {
            user = createUserWithPhone(phone);
        }
        
        //5. 保存用户信息到Redis
        // 5.1 随机生成token作为登录令牌
        String token = UUID.randomUUID().toString(true);
        
        // 5.2 将User对象转为HashMap存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                cn.hutool.core.bean.copier.CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        
        // 5.3 存储到Redis
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        
        // 5.4 设置token有效期30分钟
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);
        
        //6. 返回token
        return Result.ok(token);
    }
    private  User createUserWithPhone(String phone)
    {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX+RandomUtil.randomString(10));
        save(user);
        return user;
    }
}
