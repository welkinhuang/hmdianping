package com.hmdp.service.impl;

import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import javax.servlet.http.HttpSession;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * UserServiceImpl 单元测试
 * 
 * 注意：由于UserServiceImpl继承了ServiceImpl，login方法中使用了query().eq().one()链式调用，
 * 这种深度依赖MyBatis-Plus上下文的代码在纯Mock环境下难以测试。
 * 
 * 本测试类专注于测试可以被Mock的部分：
 * 1. sendCode - 验证码发送（仅依赖Redis）
 * 2. login中的验证逻辑（手机号格式、验证码校验）
 *
 * @author SDET
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("用户服务测试")
class UserServiceImplTest {

    @InjectMocks
    private UserServiceImpl userService;

    @Mock
    private UserMapper userMapper;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @Mock
    private HttpSession session;

    @BeforeEach
    void setUp() {
        // Mock Redis链式调用
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
    }

    @Nested
    @DisplayName("发送验证码 - sendCode")
    class SendCodeTest {

        @Test
        @DisplayName("手机号格式正确 - 发送验证码成功")
        void sendCode_WhenPhoneValid_ShouldReturnSuccess() {
            // Given - 准备有效手机号
            String validPhone = "13812345678";

            // When - 执行调用
            Result result = userService.sendCode(validPhone, session);

            // Then - 断言成功
            assertTrue(result.getSuccess(), "请求应该成功");
            assertNull(result.getErrorMsg(), "不应有错误信息");

            // Verify - 验证验证码存入Redis（2分钟过期）
            verify(valueOperations, times(1)).set(
                    eq(LOGIN_CODE_KEY + validPhone),
                    anyString(),
                    eq(LOGIN_CODE_TTL),
                    eq(TimeUnit.MINUTES)
            );
        }

        @Test
        @DisplayName("手机号格式错误 - 返回失败")
        void sendCode_WhenPhoneInvalid_ShouldReturnFail() {
            // Given - 无效手机号
            String invalidPhone = "12345";

            // When - 执行调用
            Result result = userService.sendCode(invalidPhone, session);

            // Then - 断言失败
            assertFalse(result.getSuccess(), "请求应该失败");
            assertEquals("手机号格式错误！", result.getErrorMsg(), "错误信息应匹配");

            // Verify - 不应存储验证码
            verify(valueOperations, never()).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
        }

        @Test
        @DisplayName("手机号为空 - 返回失败")
        void sendCode_WhenPhoneEmpty_ShouldReturnFail() {
            // Given - 空手机号
            String emptyPhone = "";

            // When - 执行调用
            Result result = userService.sendCode(emptyPhone, session);

            // Then - 断言失败
            assertFalse(result.getSuccess(), "请求应该失败");
            assertEquals("手机号格式错误！", result.getErrorMsg());
        }

        @Test
        @DisplayName("手机号为null - 返回失败")
        void sendCode_WhenPhoneNull_ShouldReturnFail() {
            // Given - null手机号
            String nullPhone = null;

            // When - 执行调用
            Result result = userService.sendCode(nullPhone, session);

            // Then - 断言失败
            assertFalse(result.getSuccess(), "请求应该失败");
        }
    }

    @Nested
    @DisplayName("用户登录 - login (验证码校验部分)")
    class LoginTest {

        @Test
        @DisplayName("验证码错误 - 登录失败")
        void login_WhenCodeIncorrect_ShouldReturnFail() {
            // Given - 准备数据
            String phone = "13812345678";
            String inputCode = "123456";
            String correctCode = "654321";  // 实际验证码不同

            LoginFormDTO loginForm = new LoginFormDTO();
            loginForm.setPhone(phone);
            loginForm.setCode(inputCode);

            // Mock Redis返回不同的验证码
            when(valueOperations.get(LOGIN_CODE_KEY + phone)).thenReturn(correctCode);

            // When - 执行登录
            Result result = userService.login(loginForm, session);

            // Then - 断言失败
            assertFalse(result.getSuccess(), "登录应该失败");
            assertEquals("验证码错误", result.getErrorMsg(), "错误信息应匹配");

            // Verify - 不应查询数据库
            verify(userMapper, never()).selectList(any());
        }

        @Test
        @DisplayName("验证码不存在（过期） - 登录失败")
        void login_WhenCodeExpired_ShouldReturnFail() {
            // Given
            String phone = "13812345678";
            LoginFormDTO loginForm = new LoginFormDTO();
            loginForm.setPhone(phone);
            loginForm.setCode("123456");

            // Mock Redis返回null（验证码过期）
            when(valueOperations.get(LOGIN_CODE_KEY + phone)).thenReturn(null);

            // When
            Result result = userService.login(loginForm, session);

            // Then
            assertFalse(result.getSuccess(), "登录应该失败");
            assertEquals("验证码错误", result.getErrorMsg());
        }

        @Test
        @DisplayName("手机号格式错误 - 登录失败")
        void login_WhenPhoneInvalid_ShouldReturnFail() {
            // Given - 无效手机号
            LoginFormDTO loginForm = new LoginFormDTO();
            loginForm.setPhone("invalid");
            loginForm.setCode("123456");

            // When
            Result result = userService.login(loginForm, session);

            // Then
            assertFalse(result.getSuccess(), "登录应该失败");
            assertEquals("手机号格式错误！", result.getErrorMsg());

            // Verify - 不应访问Redis
            verify(valueOperations, never()).get(anyString());
        }

        @Test
        @DisplayName("手机号为空 - 登录失败")
        void login_WhenPhoneEmpty_ShouldReturnFail() {
            // Given
            LoginFormDTO loginForm = new LoginFormDTO();
            loginForm.setPhone("");
            loginForm.setCode("123456");

            // When
            Result result = userService.login(loginForm, session);

            // Then
            assertFalse(result.getSuccess(), "登录应该失败");
            assertEquals("手机号格式错误！", result.getErrorMsg());
        }
    }
}
