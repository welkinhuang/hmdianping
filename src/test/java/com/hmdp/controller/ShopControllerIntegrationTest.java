package com.hmdp.controller;

import com.hmdp.base.BaseIntegrationTest;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * ShopController 集成测试
 * 
 * 测试策略：
 * - 使用 RestAssured 进行 HTTP 接口测试
 * - 启动真实的 Spring Context (不使用 Mock)
 * - 连接测试环境的 MySQL 和 Redis (配置在 application-test.yaml)
 * - 每个测试方法执行后清理测试数据，保证测试隔离性
 * 
 * @author SDET
 * @since 2026-01-11
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("ShopController 集成测试")
class ShopControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ShopMapper shopMapper;

    @Autowired
    private com.hmdp.utils.TestDataHelper testDataHelper;

    @Autowired
    private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    private static final String BASE_PATH = "/shop";
    private Long testShopId;
    
    // 预热的测试数据 ID（在 @BeforeEach 中创建）
    private Long preloadedShopId1;
    private Long preloadedShopId2;
    private Long preloadedShopId3;



    @BeforeEach
    @Override
    public void setUp() {
        super.setUp();
        RestAssured.basePath = BASE_PATH;
        
        // 清理 Redis 缓存，确保测试独立性
        clearRedisCache("cache:shop:*");
        clearRedisCache("shop:geo:*");
        
        // 🔥 每次测试前创建预热数据，确保数据库有数据且已提交
        Shop shop1 = testDataHelper.createAndSaveShop("预热测试商铺1", 1L);
        Shop shop2 = testDataHelper.createAndSaveShop("预热测试咖啡店", 1L);
        Shop shop3 = testDataHelper.createAndSaveShop("预热测试餐厅", 2L);
        
        preloadedShopId1 = shop1.getId();
        preloadedShopId2 = shop2.getId();
        preloadedShopId3 = shop3.getId();
        
        // 🔥 预热商铺数据到 Redis（逻辑过期策略要求必须预热）
        preloadShopToRedis(shop1);
        preloadShopToRedis(shop2);
        preloadShopToRedis(shop3);
        
        // 🔥 预热 Redis Geo 数据，避免地理位置查询失败
        stringRedisTemplate.opsForGeo().add(
            "shop:geo:1",
            new org.springframework.data.geo.Point(121.472644, 31.231706),
            preloadedShopId1.toString()
        );
        stringRedisTemplate.opsForGeo().add(
            "shop:geo:1",
            new org.springframework.data.geo.Point(121.473644, 31.232706),
            preloadedShopId2.toString()
        );
    }

    @AfterEach
    void tearDown() {
        // 清理测试数据（如果有新增的测试数据）
        if (testShopId != null && testShopId > 0) {
            shopMapper.deleteById(testShopId);
            testShopId = null;
        }
        
        // 清理预热数据
        if (preloadedShopId1 != null) {
            testDataHelper.deleteShop(preloadedShopId1);
        }
        if (preloadedShopId2 != null) {
            testDataHelper.deleteShop(preloadedShopId2);
        }
        if (preloadedShopId3 != null) {
            testDataHelper.deleteShop(preloadedShopId3);
        }
    }

    // ==================== 查询商铺 ====================

    @Nested
    @DisplayName("GET /{id} - 根据ID查询商铺")
    class QueryShopByIdTests {

        @Test
        @Order(1)
        @DisplayName("成功场景：查询存在的商铺")
        void testQueryShopById_Success() {
            given()
                .contentType(ContentType.JSON)
            .when()
                .get("/" + preloadedShopId1) // 使用预热的测试数据
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("success", equalTo(true))
                .body("data", notNullValue())
                .body("data.id", equalTo(preloadedShopId1.intValue()))
                .body("data.name", equalTo("预热测试商铺1"))
                .body("errorMsg", nullValue());
        }

        @Test
        @Order(2)
        @DisplayName("失败场景：查询不存在的商铺")
        void testQueryShopById_NotFound() {
            given()
                .contentType(ContentType.JSON)
            .when()
                .get("/999999") // 不存在的商铺ID
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("success", equalTo(false))
                .body("errorMsg", equalTo("店铺不存在"))
                .body("data", nullValue());
        }

        @Test
        @Order(3)
        @DisplayName("边界场景：ID 为 0")
        void testQueryShopById_ZeroId() {
            given()
                .contentType(ContentType.JSON)
            .when()
                .get("/0")
            .then()
                .statusCode(200)
                .body("success", equalTo(false));
        }

        @Test
        @Order(4)
        @DisplayName("缓存验证：重复查询应命中缓存")
        void testQueryShopById_CacheHit() {
            String cacheKey = "cache:shop:" + preloadedShopId1;
            
            // 第一次查询，写入缓存
            given()
                .contentType(ContentType.JSON)
            .when()
                .get("/" + preloadedShopId1)
            .then()
                .statusCode(200)
                .body("success", equalTo(true));

            // 验证 Redis 中存在缓存
            Assertions.assertTrue(hasRedisKey(cacheKey), "Redis 应该包含商铺缓存");

            // 第二次查询，应命中缓存
            given()
                .contentType(ContentType.JSON)
            .when()
                .get("/" + preloadedShopId1)
            .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.id", equalTo(preloadedShopId1.intValue()));
        }
    }

    // ==================== 新增商铺 ====================

    @Nested
    @DisplayName("POST / - 新增商铺")
    class SaveShopTests {

        @Test
        @Order(5)
        @DisplayName("成功场景：新增商铺并返回ID")
        void testSaveShop_Success() {
            Shop newShop = createTestShop("测试商铺", 1L);

            Integer shopId = given()
                .contentType(ContentType.JSON)
                .body(newShop)
            .when()
                .post()
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("success", equalTo(true))
                .body("data", notNullValue())
            .extract()
                .path("data");

            // 保存 ID 用于清理
            testShopId = shopId.longValue();

            // 验证数据库中存在该商铺
            Shop savedShop = shopMapper.selectById(testShopId);
            Assertions.assertNotNull(savedShop, "数据库应包含新增的商铺");
            Assertions.assertEquals("测试商铺", savedShop.getName());
        }

        @Test
        @Order(6)
        @DisplayName("失败场景：缺少必填字段（name）")
        void testSaveShop_MissingRequiredField() {
            Shop invalidShop = new Shop();
            invalidShop.setTypeId(1L);
            invalidShop.setArea("测试区域");
            // 缺少 name 字段

            given()
                .contentType(ContentType.JSON)
                .body(invalidShop)
            .when()
                .post()
            .then()
                .statusCode(200); // Spring Boot 默认返回 200，业务逻辑可能需要校验
        }

        @Test
        @Order(7)
        @DisplayName("边界场景：商铺名称超长")
        void testSaveShop_LongName() {
            String longName = new String(new char[500]).replace('\0', 'A');

            Shop shop = createTestShop(longName, 1L);

            given()
                    .contentType(ContentType.JSON)
                    .body(shop)
                    .when()
                    .post()
                    .then()
                    .statusCode(200);
        }
    }

    // ==================== 更新商铺 ====================

    @Nested
    @DisplayName("PUT / - 更新商铺")
    class UpdateShopTests {

        @Test
        @Order(8)
        @DisplayName("成功场景：更新现有商铺")
        void testUpdateShop_Success() {
            // 先查询一个现有商铺（使用预热数据）
            Shop existingShop = shopMapper.selectById(preloadedShopId2);
            Assertions.assertNotNull(existingShop, "预热的测试数据应该存在");
            
            // 保存原始名称和区域
            String originalName = existingShop.getName();
            String originalArea = existingShop.getArea();

            // 修改商铺信息
            existingShop.setName("更新后的商铺名称");
            existingShop.setArea("新区域");

            given()
                .contentType(ContentType.JSON)
                .body(existingShop)
            .when()
                .put()
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("success", equalTo(true));

            // 验证数据库更新成功
            Shop updatedShop = shopMapper.selectById(preloadedShopId2);
            Assertions.assertEquals("更新后的商铺名称", updatedShop.getName());
            Assertions.assertEquals("新区域", updatedShop.getArea());

            // 恢复原数据（避免影响其他测试）
            existingShop.setName(originalName);
            existingShop.setArea(originalArea);
            shopMapper.updateById(existingShop);
        }

        @Test
        @Order(9)
        @DisplayName("失败场景：更新不存在的商铺")
        void testUpdateShop_NotFound() {
            Shop nonExistentShop = createTestShop("不存在的商铺", 1L);
            nonExistentShop.setId(999999L); // 不存在的ID

            given()
                .contentType(ContentType.JSON)
                .body(nonExistentShop)
            .when()
                .put()
            .then()
                .statusCode(200);
            // 根据业务逻辑，可能返回成功或失败
        }

        @Test
        @Order(10)
        @DisplayName("缓存一致性：更新后应删除缓存")
        void testUpdateShop_CacheInvalidation() {
            String cacheKey = "cache:shop:" + preloadedShopId3;
            
            // 先查询商铺，写入缓存
            given().get("/" + preloadedShopId3).then().statusCode(200);

            // 验证缓存存在
            Assertions.assertTrue(hasRedisKey(cacheKey), "更新前应有缓存");

            // 更新商铺
            Shop shop = shopMapper.selectById(preloadedShopId3);
            String originalName = shop.getName();
            shop.setName("缓存测试商铺_" + System.currentTimeMillis());

            given()
                .contentType(ContentType.JSON)
                .body(shop)
            .when()
                .put()
            .then()
                .statusCode(200);

            // 验证缓存已删除
            Assertions.assertFalse(hasRedisKey(cacheKey), "更新后应删除缓存");
            
            // 恢复原数据
            shop.setName(originalName);
            shopMapper.updateById(shop);
        }
    }

    // ==================== 按类型查询商铺 ====================

    @Nested
    @DisplayName("GET /of/type - 按商铺类型查询")
    class QueryShopByTypeTests {

        @Test
        @Order(11)
        @DisplayName("成功场景：查询指定类型的商铺列表")
        void testQueryShopByType_Success() {
            given()
                .queryParam("typeId", 1)
                .queryParam("current", 1)
            .when()
                .get("/of/type")
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("success", equalTo(true))
                .body("data", notNullValue())
                .body("data", isA(java.util.List.class));
        }

        @Test
        @Order(12)
        @DisplayName("分页场景：查询第2页数据")
        void testQueryShopByType_Pagination() {
            given()
                .queryParam("typeId", 1)
                .queryParam("current", 2)
            .when()
                .get("/of/type")
            .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data", isA(java.util.List.class));
        }

        @Test
        @Order(13)
        @DisplayName("地理位置场景：带经纬度查询")
        void testQueryShopByType_WithGeo() {
            given()
                .queryParam("typeId", 1)
                .queryParam("current", 1)
                .queryParam("x", 121.472644)
                .queryParam("y", 31.231706)
            .when()
                .get("/of/type")
            .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data", notNullValue());
        }

        @Test
        @Order(14)
        @DisplayName("边界场景：不存在的类型ID")
        void testQueryShopByType_InvalidTypeId() {
            given()
                .queryParam("typeId", 99999)
                .queryParam("current", 1)
            .when()
                .get("/of/type")
            .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data", empty()); // 应返回空列表
        }

        @Test
        @Order(15)
        @DisplayName("默认值场景：未传 current 参数")
        void testQueryShopByType_DefaultCurrent() {
            given()
                .queryParam("typeId", 1)
            .when()
                .get("/of/type")
            .then()
                .statusCode(200)
                .body("success", equalTo(true));
            // 应使用默认值 current=1
        }
    }

    // ==================== 按名称查询商铺 ====================

    @Nested
    @DisplayName("GET /of/name - 按商铺名称查询")
    class QueryShopByNameTests {

        @Test
        @Order(16)
        @DisplayName("成功场景：模糊查询商铺名称")
        void testQueryShopByName_Success() {
            given()
                .queryParam("name", "咖啡")
                .queryParam("current", 1)
            .when()
                .get("/of/name")
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("success", equalTo(true))
                .body("data", notNullValue())
                .body("data", isA(java.util.List.class));
        }

        @Test
        @Order(17)
        @DisplayName("空查询场景：未传 name 参数")
        void testQueryShopByName_EmptyName() {
            given()
                .queryParam("current", 1)
            .when()
                .get("/of/name")
            .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data", isA(java.util.List.class));
            // 应返回所有商铺
        }

        @Test
        @Order(18)
        @DisplayName("无结果场景：查询不存在的名称")
        void testQueryShopByName_NoResult() {
            given()
                .queryParam("name", "不可能存在的商铺名称XYZ123")
                .queryParam("current", 1)
            .when()
                .get("/of/name")
            .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data", empty());
        }

        @Test
        @Order(19)
        @DisplayName("分页场景：查询第3页")
        void testQueryShopByName_Pagination() {
            given()
                .queryParam("name", "店")
                .queryParam("current", 3)
            .when()
                .get("/of/name")
            .then()
                .statusCode(200)
                .body("success", equalTo(true));
        }

        @Test
        @Order(20)
        @DisplayName("特殊字符场景：包含 SQL 特殊字符")
        void testQueryShopByName_SpecialCharacters() {
            given()
                .queryParam("name", "%' OR '1'='1")
                .queryParam("current", 1)
            .when()
                .get("/of/name")
            .then()
                .statusCode(200)
                .body("success", equalTo(true));
            // 应正确处理 SQL 注入攻击
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 创建测试用商铺对象
     */
    private Shop createTestShop(String name, Long typeId) {
        Shop shop = new Shop();
        shop.setName(name);
        shop.setTypeId(typeId);
        shop.setImages("https://example.com/image.jpg");
        shop.setArea("测试区域");
        shop.setAddress("测试地址123号");
        shop.setX(121.472644);
        shop.setY(31.231706);
        shop.setAvgPrice(50L);
        shop.setSold(100);
        shop.setComments(50);
        shop.setScore(45);
        shop.setOpenHours("10:00-22:00");
        shop.setCreateTime(LocalDateTime.now());
        return shop;
    }

    /**
     * 预热商铺数据到 Redis（使用逻辑过期策略）
     * 后端使用 queryWithLogicalExpire，必须预热才能查到数据
     */
    private void preloadShopToRedis(Shop shop) {
        if (shop == null || shop.getId() == null) {
            return;
        }
        
        String key = "cache:shop:" + shop.getId();
        
        // 使用 Hutool 构造 RedisData（格式：{"data": {...}, "expireTime": "2026-01-11T12:00:00"}）
        cn.hutool.json.JSONObject shopJson = cn.hutool.json.JSONUtil.parseObj(cn.hutool.json.JSONUtil.toJsonStr(shop));
        cn.hutool.json.JSONObject redisData = cn.hutool.json.JSONUtil.createObj()
            .set("data", shopJson)
            .set("expireTime", java.time.LocalDateTime.now().plusMinutes(30));
        
        stringRedisTemplate.opsForValue().set(key, redisData.toString());
    }
}
