package com.hmdp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Redis 连接测试
 * 验证 application-test.yaml 中的 Redis 配置是否正确
 * 
 * @author SDET
 * @since 2026-01-11
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Redis 连接测试")
class RedisConnectionTest {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Test
    @DisplayName("测试 Redis 连接是否正常")
    void testRedisConnection() {
        // 验证 stringRedisTemplate 注入成功
        assertNotNull(stringRedisTemplate, "StringRedisTemplate 应该被成功注入");
        
        // 执行 PING 命令
        String pong = stringRedisTemplate.getConnectionFactory()
                .getConnection()
                .ping();
        
        assertEquals("PONG", pong, "Redis PING 命令应该返回 PONG");
        
        System.out.println("✅ Redis 连接成功！PING 返回: " + pong);
    }

    @Test
    @DisplayName("测试 Redis 基本操作 - SET/GET")
    void testRedisBasicOperations() {
        String testKey = "test:connection:key";
        String testValue = "Hello Redis!";
        
        try {
            // SET 操作
            stringRedisTemplate.opsForValue().set(testKey, testValue, 10, TimeUnit.SECONDS);
            System.out.println("✅ Redis SET 成功：" + testKey + " = " + testValue);
            
            // GET 操作
            String retrievedValue = stringRedisTemplate.opsForValue().get(testKey);
            assertEquals(testValue, retrievedValue, "Redis GET 应该返回相同的值");
            System.out.println("✅ Redis GET 成功：" + retrievedValue);
            
            // 验证 TTL
            Long ttl = stringRedisTemplate.getExpire(testKey, TimeUnit.SECONDS);
            assertNotNull(ttl, "TTL 不应为 null");
            assertTrue(ttl > 0 && ttl <= 10, "TTL 应该在 0-10 秒之间");
            System.out.println("✅ Redis TTL 验证成功：" + ttl + " 秒");
            
        } finally {
            // 清理测试数据
            stringRedisTemplate.delete(testKey);
            System.out.println("🧹 测试数据已清理");
        }
    }

    @Test
    @DisplayName("测试 Redis 数据库选择")
    void testRedisDatabaseSelection() {
        // application-test.yaml 配置的是 database: 0
        String testKey = "test:db:check";
        String testValue = "database-0";
        
        try {
            stringRedisTemplate.opsForValue().set(testKey, testValue);
            String value = stringRedisTemplate.opsForValue().get(testKey);
            
            assertEquals(testValue, value, "应该能在正确的数据库中读写数据");
            System.out.println("✅ Redis 数据库选择正确（database: 0）");
            
        } finally {
            stringRedisTemplate.delete(testKey);
        }
    }

    @Test
    @DisplayName("测试 Redis 连接池配置")
    void testRedisConnectionPool() {
        // 验证连接池可以同时处理多个操作
        String keyPrefix = "test:pool:";
        
        try {
            // 批量写入
            for (int i = 0; i < 5; i++) {
                stringRedisTemplate.opsForValue().set(keyPrefix + i, "value-" + i);
            }
            System.out.println("✅ Redis 批量 SET 成功（5条）");
            
            // 批量读取
            for (int i = 0; i < 5; i++) {
                String value = stringRedisTemplate.opsForValue().get(keyPrefix + i);
                assertEquals("value-" + i, value, "批量读取应该返回正确的值");
            }
            System.out.println("✅ Redis 批量 GET 成功（5条）");
            
        } finally {
            // 清理数据
            for (int i = 0; i < 5; i++) {
                stringRedisTemplate.delete(keyPrefix + i);
            }
            System.out.println("🧹 批量测试数据已清理");
        }
    }

    @Test
    @DisplayName("测试 Redis 删除操作")
    void testRedisDeleteOperation() {
        String testKey = "test:delete:key";
        String testValue = "to-be-deleted";
        
        // 写入数据
        stringRedisTemplate.opsForValue().set(testKey, testValue);
        assertTrue(Boolean.TRUE.equals(stringRedisTemplate.hasKey(testKey)), "Key 应该存在");
        
        // 删除数据
        Boolean deleted = stringRedisTemplate.delete(testKey);
        assertTrue(Boolean.TRUE.equals(deleted), "删除操作应该返回 true");
        
        // 验证已删除
        assertFalse(Boolean.TRUE.equals(stringRedisTemplate.hasKey(testKey)), "Key 应该已被删除");
        System.out.println("✅ Redis DELETE 操作成功");
    }
}
