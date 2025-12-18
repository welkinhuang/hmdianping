package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final String CACHE_SHOP_TYPE_KEY = "cache:shoptype:list";

    @Override
    public Result queryTypeList() {
        // 1. 从Redis查询店铺类型缓存
        String shopTypeJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_TYPE_KEY);
        
        // 2. 判断是否存在
        if (StrUtil.isNotBlank(shopTypeJson)) {
            // 3. 存在,直接返回
            List<ShopType> typeList = JSONUtil.toList(shopTypeJson, ShopType.class);
            return Result.ok(typeList);
        }
        
        // 4. 不存在,从数据库查询
        List<ShopType> typeList = query().orderByAsc("sort").list();
        
        // 5. 判断数据库中是否存在
        if (typeList == null || typeList.isEmpty()) {
            return Result.fail("店铺类型不存在");
        }
        
        // 6. 存在,写入Redis缓存
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_TYPE_KEY, JSONUtil.toJsonStr(typeList));
        
        // 7. 返回
        return Result.ok(typeList);
    }
}
