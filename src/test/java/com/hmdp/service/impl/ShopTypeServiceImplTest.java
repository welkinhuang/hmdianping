package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * ShopTypeServiceImpl 单元测试
 * 
 * @author SDET
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("店铺类型服务测试")
class ShopTypeServiceImplTest {

    @InjectMocks
    private ShopTypeServiceImpl shopTypeService;

    @Mock
    private ShopTypeMapper shopTypeMapper;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private static final String CACHE_SHOP_TYPE_KEY = "cache:shoptype:list";

    @BeforeEach
    void setUp() {
        // Mock Redis链式调用: stringRedisTemplate.opsForValue().xxx()
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Nested
    @DisplayName("查询店铺类型列表 - queryTypeList")
    class QueryTypeListTest {

        @Test
        @DisplayName("缓存命中 - 直接返回Redis缓存数据")
        void queryTypeList_WhenCacheHit_ShouldReturnCachedData() {
            // Given - 准备数据
            List<ShopType> expectedList = createMockShopTypeList();
            String cachedJson = JSONUtil.toJsonStr(expectedList);
            when(valueOperations.get(CACHE_SHOP_TYPE_KEY)).thenReturn(cachedJson);

            // When - 执行调用
            Result result = shopTypeService.queryTypeList();

            // Then - 断言结果
            assertTrue(result.getSuccess(), "请求应该成功");
            assertNotNull(result.getData(), "返回数据不应为空");
            
            @SuppressWarnings("unchecked")
            List<ShopType> actualList = (List<ShopType>) result.getData();
            assertEquals(2, actualList.size(), "应返回2条店铺类型");
            assertEquals("美食", actualList.get(0).getName(), "第一个类型应该是美食");
            assertEquals("娱乐", actualList.get(1).getName(), "第二个类型应该是娱乐");

            // Verify - 验证从Redis读取，不应查询数据库
            verify(valueOperations, times(1)).get(CACHE_SHOP_TYPE_KEY);
            verify(shopTypeMapper, never()).selectList(any());
        }

        @Test
        @DisplayName("缓存未命中 - 从数据库查询并写入缓存")
        void queryTypeList_WhenCacheMiss_ShouldQueryDbAndSetCache() {
            // Given - 缓存未命中（返回null）
            when(valueOperations.get(CACHE_SHOP_TYPE_KEY)).thenReturn(null);
            
            // 模拟数据库返回数据
            List<ShopType> dbList = createMockShopTypeList();
            when(shopTypeMapper.selectList(any())).thenReturn(dbList);

            // When - 执行调用
            Result result = shopTypeService.queryTypeList();

            // Then - 断言结果
            assertTrue(result.getSuccess(), "请求应该成功");
            assertNotNull(result.getData(), "返回数据不应为空");
            
            @SuppressWarnings("unchecked")
            List<ShopType> actualList = (List<ShopType>) result.getData();
            assertEquals(2, actualList.size(), "应返回2条店铺类型");

            // Verify - 验证写入缓存
            verify(valueOperations, times(1)).get(CACHE_SHOP_TYPE_KEY);
            verify(valueOperations, times(1)).set(eq(CACHE_SHOP_TYPE_KEY), anyString());
        }

        @Test
        @DisplayName("缓存未命中且数据库为空 - 返回失败")
        void queryTypeList_WhenCacheMissAndDbEmpty_ShouldReturnFail() {
            // Given - 缓存未命中
            when(valueOperations.get(CACHE_SHOP_TYPE_KEY)).thenReturn(null);
            
            // 数据库返回空列表
            when(shopTypeMapper.selectList(any())).thenReturn(Collections.emptyList());

            // When - 执行调用
            Result result = shopTypeService.queryTypeList();

            // Then - 断言失败
            assertFalse(result.getSuccess(), "请求应该失败");
            assertEquals("店铺类型不存在", result.getErrorMsg(), "错误信息应匹配");
            assertNull(result.getData(), "失败时data应为null");

            // Verify - 不应写入缓存
            verify(valueOperations, never()).set(anyString(), anyString());
        }

        @Test
        @DisplayName("缓存为空字符串 - 应从数据库查询")
        void queryTypeList_WhenCacheEmpty_ShouldQueryDb() {
            // Given - 缓存返回空字符串（StrUtil.isNotBlank会返回false）
            when(valueOperations.get(CACHE_SHOP_TYPE_KEY)).thenReturn("");
            
            List<ShopType> dbList = createMockShopTypeList();
            when(shopTypeMapper.selectList(any())).thenReturn(dbList);

            // When - 执行调用
            Result result = shopTypeService.queryTypeList();

            // Then - 应该查询数据库
            assertTrue(result.getSuccess(), "请求应该成功");
            verify(shopTypeMapper, times(1)).selectList(any());
        }
    }

    /**
     * 创建测试用的店铺类型列表
     */
    private List<ShopType> createMockShopTypeList() {
        ShopType type1 = new ShopType();
        type1.setId(1L);
        type1.setName("美食");
        type1.setIcon("/images/food.png");
        type1.setSort(1);

        ShopType type2 = new ShopType();
        type2.setId(2L);
        type2.setName("娱乐");
        type2.setIcon("/images/fun.png");
        type2.setSort(2);

        return Arrays.asList(type1, type2);
    }
}
