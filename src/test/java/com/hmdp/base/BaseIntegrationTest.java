package com.hmdp.base;

import com.hmdp.utils.TestDataHelper;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * 集成测试基类
 * 
 * 提供通用的测试环境配置和工具方法
 * 所有 Controller 集成测试都应继承此类
 * 
 * 特性：
 * - 启动真实的 Spring Context（不使用 Mock）
 * - 连接测试环境的 MySQL 和 Redis
 * - 自动配置 RestAssured
 * - 提供测试数据辅助工具
 * 
 * @author SDET
 * @since 2026-01-11
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    @LocalServerPort
    protected int port;

    @Autowired
    protected StringRedisTemplate stringRedisTemplate;

    @Autowired(required = false)
    protected TestDataHelper testDataHelper;

    /**
     * 每个测试前的初始化
     * 子类可以重写此方法添加自定义逻辑，但需调用 super.setUp()
     */
    @BeforeEach
    public void setUp() {
        // 配置 RestAssured 端口
        RestAssured.port = port;
        
        // 配置默认 Content-Type
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    /**
     * 清理 Redis 缓存（按 pattern）
     * 
     * @param pattern Redis key 的匹配模式，如 "cache:shop:*"
     */
    protected void clearRedisCache(String pattern) {
        stringRedisTemplate.delete(stringRedisTemplate.keys(pattern));
    }

    /**
     * 清理所有测试相关的 Redis 缓存
     */
    protected void clearAllCache() {
        if (testDataHelper != null) {
            testDataHelper.clearAllTestCache();
        }
    }

    /**
     * 检查 Redis 中是否存在指定 key
     * 
     * @param key Redis key
     * @return 是否存在
     */
    protected boolean hasRedisKey(String key) {
        Boolean hasKey = stringRedisTemplate.hasKey(key);
        return hasKey != null && hasKey;
    }

    /**
     * 获取 Redis 中的值
     * 
     * @param key Redis key
     * @return value，不存在返回 null
     */
    protected String getRedisValue(String key) {
        return stringRedisTemplate.opsForValue().get(key);
    }

    /**
     * 设置 Redis 值
     * 
     * @param key Redis key
     * @param value Redis value
     */
    protected void setRedisValue(String key, String value) {
        stringRedisTemplate.opsForValue().set(key, value);
    }

    /**
     * 删除 Redis key
     * 
     * @param key Redis key
     */
    protected void deleteRedisKey(String key) {
        stringRedisTemplate.delete(key);
    }
}
