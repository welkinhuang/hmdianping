package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.utils.CacheClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ShopServiceImpl 单元测试
 * 
 * 注意：由于queryShopByGeo依赖MyBatis-Plus的链式查询和Redis Geo操作，
 * 在纯Mock环境下难以完整测试。本测试专注于可Mock的核心业务逻辑。
 *
 * @author SDET
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("店铺服务测试")
class ShopServiceImplTest {

    @InjectMocks
    private ShopServiceImpl shopService;

    @Mock
    private ShopMapper shopMapper;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private CacheClient cacheClient;

    @BeforeEach
    void setUp() {
        // Mock Redis链式调用
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Nested
    @DisplayName("根据ID查询店铺 - queryById")
    class QueryByIdTest {

        @Test
        @DisplayName("店铺存在 - 返回店铺信息")
        void queryById_WhenShopExists_ShouldReturnShop() {
            // Given - 准备数据
            Long shopId = 1L;
            Shop mockShop = createMockShop(shopId, "星巴克", 1L);
            
            // Mock CacheClient返回店铺
            when(cacheClient.queryWithLogicalExpire(
                    eq(CACHE_SHOP_KEY),
                    eq(shopId),
                    eq(Shop.class),
                    any(),
                    eq(20L),
                    eq(TimeUnit.SECONDS)
            )).thenReturn(mockShop);

            // When - 执行查询
            Result result = shopService.queryById(shopId);

            // Then - 断言成功
            assertTrue(result.getSuccess(), "请求应该成功");
            assertNotNull(result.getData(), "返回数据不应为空");
            
            Shop actualShop = (Shop) result.getData();
            assertEquals(shopId, actualShop.getId(), "店铺ID应匹配");
            assertEquals("星巴克", actualShop.getName(), "店铺名称应匹配");

            // Verify
            verify(cacheClient, times(1)).queryWithLogicalExpire(
                    anyString(), anyLong(), eq(Shop.class), any(), anyLong(), any(TimeUnit.class)
            );
        }

        @Test
        @DisplayName("店铺不存在 - 返回失败")
        void queryById_WhenShopNotExists_ShouldReturnFail() {
            // Given - CacheClient返回null（店铺不存在或缓存穿透）
            Long shopId = 999L;
            when(cacheClient.queryWithLogicalExpire(
                    eq(CACHE_SHOP_KEY),
                    eq(shopId),
                    eq(Shop.class),
                    any(),
                    eq(20L),
                    eq(TimeUnit.SECONDS)
            )).thenReturn(null);

            // When
            Result result = shopService.queryById(shopId);

            // Then
            assertFalse(result.getSuccess(), "请求应该失败");
            assertEquals("店铺不存在", result.getErrorMsg(), "错误信息应匹配");
            assertNull(result.getData(), "失败时data应为null");
        }
        
        @Test
        @DisplayName("查询多个店铺 - 验证缓存调用")
        void queryById_MultipleCalls_ShouldCallCacheClient() {
            // Given
            Shop shop1 = createMockShop(1L, "店铺1", 1L);
            Shop shop2 = createMockShop(2L, "店铺2", 2L);
            
            when(cacheClient.queryWithLogicalExpire(
                    eq(CACHE_SHOP_KEY), eq(1L), eq(Shop.class), any(), eq(20L), eq(TimeUnit.SECONDS)
            )).thenReturn(shop1);
            
            when(cacheClient.queryWithLogicalExpire(
                    eq(CACHE_SHOP_KEY), eq(2L), eq(Shop.class), any(), eq(20L), eq(TimeUnit.SECONDS)
            )).thenReturn(shop2);

            // When
            Result result1 = shopService.queryById(1L);
            Result result2 = shopService.queryById(2L);

            // Then
            assertTrue(result1.getSuccess());
            assertTrue(result2.getSuccess());
            assertEquals("店铺1", ((Shop) result1.getData()).getName());
            assertEquals("店铺2", ((Shop) result2.getData()).getName());
            
            // Verify - 验证调用了2次缓存
            verify(cacheClient, times(2)).queryWithLogicalExpire(
                    anyString(), anyLong(), eq(Shop.class), any(), anyLong(), any(TimeUnit.class)
            );
        }
    }

    @Nested
    @DisplayName("更新店铺 - update")
    class UpdateTest {

        @Test
        @DisplayName("店铺ID有效 - 更新成功并删除缓存")
        void update_WhenIdValid_ShouldUpdateAndDeleteCache() {
            // Given
            Shop shop = createMockShop(1L, "更新后的星巴克", 1L);
            
            // Mock 数据库更新成功
            when(shopMapper.updateById(any(Shop.class))).thenReturn(1);

            // When
            Result result = shopService.update(shop);

            // Then
            assertTrue(result.getSuccess(), "更新应该成功");
            assertNotNull(result.getData(), "应返回更新后的店铺");

            // Verify - 验证删除缓存
            verify(shopMapper, times(1)).updateById(shop);
            verify(stringRedisTemplate, times(1)).delete(CACHE_SHOP_KEY + shop.getId());
        }

        @Test
        @DisplayName("店铺ID为空 - 返回失败")
        void update_WhenIdNull_ShouldReturnFail() {
            // Given - 店铺ID为null
            Shop shop = new Shop();
            shop.setId(null);
            shop.setName("测试店铺");

            // When
            Result result = shopService.update(shop);

            // Then
            assertFalse(result.getSuccess(), "更新应该失败");
            assertEquals("店铺id不能为空", result.getErrorMsg());

            // Verify - 不应更新数据库或删除缓存
            verify(shopMapper, never()).updateById(any());
            verify(stringRedisTemplate, never()).delete(anyString());
        }
        
        @Test
        @DisplayName("更新店铺名称 - 验证缓存Key正确")
        void update_WhenUpdateName_ShouldDeleteCorrectCacheKey() {
            // Given
            Long shopId = 123L;
            Shop shop = createMockShop(shopId, "新名称", 1L);
            when(shopMapper.updateById(any(Shop.class))).thenReturn(1);

            // When
            shopService.update(shop);

            // Then - 验证删除的是正确的缓存Key
            verify(stringRedisTemplate).delete(CACHE_SHOP_KEY + shopId);
        }
    }

    /**
     * 创建测试用的Shop对象
     */
    private Shop createMockShop(Long id, String name, Long typeId) {
        Shop shop = new Shop();
        shop.setId(id);
        shop.setName(name);
        shop.setTypeId(typeId);
        shop.setAddress("北京市朝阳区");
        shop.setAvgPrice(50L);
        shop.setSold(100);
        shop.setX(116.4);
        shop.setY(39.9);
        return shop;
    }
}
