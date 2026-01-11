package com.hmdp.utils;

import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 测试数据准备工具类
 * 用于在集成测试中准备和清理测试数据
 * 
 * @author SDET
 * @since 2026-01-11
 */
@Component
public class TestDataHelper {

    @Autowired
    private ShopMapper shopMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 创建测试商铺并插入数据库
     * 
     * @param name 商铺名称
     * @param typeId 商铺类型ID
     * @return 新增的商铺（包含自动生成的ID）
     */
    public Shop createAndSaveShop(String name, Long typeId) {
        Shop shop = new Shop();
        shop.setName(name);
        shop.setTypeId(typeId);
        shop.setImages("https://example.com/test-image.jpg");
        shop.setArea("测试区域");
        shop.setAddress("测试地址" + System.currentTimeMillis());
        shop.setX(121.472644 + Math.random() * 0.01);
        shop.setY(31.231706 + Math.random() * 0.01);
        shop.setAvgPrice(50L);
        shop.setSold(100);
        shop.setComments(50);
        shop.setScore(45);
        shop.setOpenHours("09:00-21:00");
        shop.setCreateTime(LocalDateTime.now());
        
        shopMapper.insert(shop);
        return shop;
    }

    /**
     * 批量创建测试商铺
     * 
     * @param count 创建数量
     * @param typeId 商铺类型ID
     * @return 商铺ID列表
     */
    public List<Long> createBatchShops(int count, Long typeId) {
        List<Long> shopIds = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Shop shop = createAndSaveShop("批量测试商铺_" + i, typeId);
            shopIds.add(shop.getId());
        }
        return shopIds;
    }

    /**
     * 删除指定ID的商铺
     * 
     * @param shopId 商铺ID
     */
    public void deleteShop(Long shopId) {
        if (shopId != null) {
            shopMapper.deleteById(shopId);
        }
    }

    /**
     * 批量删除商铺
     * 
     * @param shopIds 商铺ID列表
     */
    public void deleteBatchShops(List<Long> shopIds) {
        if (shopIds != null && !shopIds.isEmpty()) {
            shopMapper.deleteBatchIds(shopIds);
        }
    }

    /**
     * 清理所有商铺缓存
     */
    public void clearShopCache() {
        stringRedisTemplate.delete(stringRedisTemplate.keys("cache:shop:*"));
    }

    /**
     * 清理所有地理位置缓存
     */
    public void clearGeoCache() {
        stringRedisTemplate.delete(stringRedisTemplate.keys("shop:geo:*"));
    }

    /**
     * 清理所有测试相关的Redis缓存
     */
    public void clearAllTestCache() {
        clearShopCache();
        clearGeoCache();
        // 可以根据需要添加更多缓存清理
    }

    /**
     * 检查商铺是否存在于数据库
     * 
     * @param shopId 商铺ID
     * @return 是否存在
     */
    public boolean shopExists(Long shopId) {
        return shopMapper.selectById(shopId) != null;
    }

    /**
     * 检查商铺是否存在于缓存
     * 
     * @param shopId 商铺ID
     * @return 是否存在
     */
    public boolean shopExistsInCache(Long shopId) {
        Boolean hasKey = stringRedisTemplate.hasKey("cache:shop:" + shopId);
        return hasKey != null && hasKey;
    }

    /**
     * 更新商铺信息
     * 
     * @param shop 商铺对象
     * @return 更新成功返回true
     */
    public boolean updateShop(Shop shop) {
        return shopMapper.updateById(shop) > 0;
    }

    /**
     * 根据ID查询商铺
     * 
     * @param shopId 商铺ID
     * @return 商铺对象，不存在则返回null
     */
    public Shop getShopById(Long shopId) {
        return shopMapper.selectById(shopId);
    }

    /**
     * 准备标准测试数据集
     * 创建多个不同类型的商铺用于测试
     * 
     * @return 商铺ID列表
     */
    public List<Long> prepareStandardTestData() {
        List<Long> shopIds = new ArrayList<>();
        
        // 创建不同类型的商铺
        shopIds.add(createAndSaveShop("测试咖啡店", 1L).getId());
        shopIds.add(createAndSaveShop("测试餐厅", 2L).getId());
        shopIds.add(createAndSaveShop("测试超市", 3L).getId());
        shopIds.add(createAndSaveShop("测试书店", 1L).getId());
        shopIds.add(createAndSaveShop("测试电影院", 4L).getId());
        
        return shopIds;
    }
}
