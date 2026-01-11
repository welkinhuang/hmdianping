# ShopController 集成测试快速入门

## 🚀 5分钟快速开始

### 步骤1：启动测试环境

确保 MySQL 和 Redis 服务已启动：

```bash
# 检查 MySQL（使用 application-test.yaml 中的配置）
mysql -h 192.168.155.1 -P 3306 -uroot -proot -e "USE hmdp; SELECT COUNT(*) FROM tb_shop;"

# 检查 Redis
redis-cli -h 172.17.0.1 -p 6379 -a 123456 PING
```

### 步骤2：运行测试

```bash
# 方式1：使用 Maven 运行所有测试
mvn test -Dtest=ShopControllerIntegrationTest

# 方式2：使用 IDE 运行（推荐）
# 打开 ShopControllerIntegrationTest.java
# 点击类名旁边的绿色运行按钮
```

### 步骤3：查看结果

✅ **成功示例：**
```
[INFO] Tests run: 20, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

❌ **失败示例：**
```
[ERROR] Tests run: 20, Failures: 2, Errors: 1
[ERROR] testQueryShopById_Success  Time elapsed: 0.5 s  <<< FAILURE!
```

---

## 📝 编写你的第一个测试

### 示例：测试查询商铺接口

```java
@Test
@DisplayName("成功场景：查询存在的商铺")
void testQueryShopById_Success() {
    given()
        .contentType(ContentType.JSON)
    .when()
        .get("/1")  // 请求 GET /shop/1
    .then()
        .statusCode(200)  // 验证状态码
        .body("success", equalTo(true))  // 验证返回成功
        .body("data.id", equalTo(1))  // 验证商铺ID
        .body("data.name", notNullValue());  // 验证商铺名称不为空
}
```

### RestAssured 常用断言

```java
// 状态码断言
.statusCode(200)
.statusCode(is(200))

// JSON 字段断言
.body("success", equalTo(true))
.body("data.id", equalTo(1))
.body("data.name", notNullValue())
.body("data.name", is("星巴克"))
.body("data", isA(java.util.List.class))
.body("data", empty())

// 提取响应数据
Integer shopId = given()
    .contentType(ContentType.JSON)
    .body(newShop)
.when()
    .post("/shop")
.then()
    .statusCode(200)
.extract()
    .path("data");
```

---

## 🧪 测试数据管理

### 📌 数据预热策略

**重要**：集成测试使用 `@BeforeAll` 预热基础测试数据，确保 Redis 和数据库中有必要的数据。

```java
@BeforeAll
static void setUpTestData(@Autowired TestDataHelper helper) {
    // 预热3个测试商铺，供多个测试用例复用
    Shop shop1 = helper.createAndSaveShop("预热测试商铺1", 1L);
    preloadedShopId1 = shop1.getId();
    
    System.out.println("✅ 测试数据预热完成");
}

@AfterAll
static void tearDownTestData(@Autowired TestDataHelper helper) {
    // 清理预热的数据
    helper.deleteShop(preloadedShopId1);
}
```

### 使用 TestDataHelper 创建测试数据

```java
@Autowired
private TestDataHelper testDataHelper;

@Test
void testWithTestData() {
    // 创建测试商铺
    Shop shop = testDataHelper.createAndSaveShop("测试商铺", 1L);
    Long shopId = shop.getId();
    
    // 执行测试
    given()
        .get("/" + shopId)
    .then()
        .statusCode(200)
        .body("data.name", equalTo("测试商铺"));
    
    // 清理数据
    testDataHelper.deleteShop(shopId);
}
```

### 批量创建测试数据

```java
@BeforeEach
void setUp() {
    // 准备10个测试商铺
    List<Long> shopIds = testDataHelper.createBatchShops(10, 1L);
}

@AfterEach
void tearDown() {
    // 清理所有测试数据
    testDataHelper.deleteBatchShops(shopIds);
}
```

---

## 🔍 调试技巧

### 1. 开启 RestAssured 日志

```java
@BeforeEach
void setUp() {
    // 请求失败时自动打印请求和响应
    RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
}
```

### 2. 手动打印请求和响应

```java
given()
    .log().all()  // 打印请求
.when()
    .get("/1")
.then()
    .log().all()  // 打印响应
    .statusCode(200);
```

### 3. 检查 Redis 缓存

```java
@Test
void testCacheDebug() {
    // 查询商铺
    given().get("/1").then().statusCode(200);
    
    // 检查缓存是否存在
    boolean hasCache = hasRedisKey("cache:shop:1");
    System.out.println("缓存是否存在: " + hasCache);
    
    // 获取缓存内容
    String cacheValue = getRedisValue("cache:shop:1");
    System.out.println("缓存内容: " + cacheValue);
}
```

---

## ⚠️ 常见错误及解决方案

### 错误1：数据库连接失败

```
Caused by: com.mysql.cj.jdbc.exceptions.CommunicationsException: Communications link failure
```

**解决方案：**
```bash
# 1. 检查 MySQL 是否启动
systemctl status mysql  # Linux
net start MySQL80       # Windows

# 2. 检查网络连接
ping 192.168.155.1

# 3. 验证用户名密码
mysql -h 192.168.155.1 -uroot -proot
```

### 错误2：Redis 连接失败

```
io.lettuce.core.RedisConnectionException: Unable to connect to 172.17.0.1:6379
```

**解决方案：**
```bash
# 1. 检查 Redis 是否启动
redis-cli -h 172.17.0.1 -p 6379 -a 123456 PING

# 2. 检查 Redis 配置
# 编辑 redis.conf
bind 0.0.0.0  # 允许外部连接
protected-mode no

# 3. 重启 Redis
systemctl restart redis
```

### 错误3：测试数据冲突

```
org.springframework.dao.DuplicateKeyException: Duplicate entry '1' for key 'PRIMARY'
```

**解决方案：**
```java
@AfterEach
void tearDown() {
    // 确保每个测试后清理数据
    if (testShopId != null) {
        shopMapper.deleteById(testShopId);
        testShopId = null;
    }
    
    // 清理 Redis 缓存
    clearAllCache();
}
```

---

## 📚 下一步学习

1. ✅ **阅读完整文档**：[ShopController集成测试指南.md](ShopController集成测试指南.md)
2. 🔧 **查看工具类**：
   - [BaseIntegrationTest.java](../src/test/java/com/hmdp/base/BaseIntegrationTest.java) - 集成测试基类
   - [TestDataHelper.java](../src/test/java/com/hmdp/utils/TestDataHelper.java) - 测试数据辅助类
3. 📖 **参考完整测试用例**：[ShopControllerIntegrationTest.java](../src/test/java/com/hmdp/controller/ShopControllerIntegrationTest.java)
4. 🌐 **RestAssured 官方文档**：https://rest-assured.io/

---

## 💡 最佳实践检查清单

- [ ] 每个测试方法只测试一个功能点
- [ ] 使用 `@DisplayName` 清晰描述测试场景
- [ ] 测试前清理 Redis 缓存（`@BeforeEach`）
- [ ] 测试后清理测试数据（`@AfterEach`）
- [ ] 使用 `TestDataHelper` 创建和管理测试数据
- [ ] 断言要具体明确，避免只检查状态码
- [ ] 边界条件和异常场景也要覆盖
- [ ] 不要在测试中硬编码业务数据，使用测试数据

---

**祝测试愉快！** 🎉

如果遇到问题，请查看 [ShopController集成测试指南.md](ShopController集成测试指南.md) 中的「常见问题」章节。
