# 集成测试说明

## 📁 目录结构

```
src/test/java/com/hmdp/
├── base/
│   └── BaseIntegrationTest.java          # 集成测试基类（所有集成测试继承此类）
├── controller/
│   └── ShopControllerIntegrationTest.java # ShopController 集成测试
├── service/
│   └── impl/
│       └── ShopServiceImplTest.java      # ShopService 单元测试（使用 Mock）
├── utils/
│   └── TestDataHelper.java               # 测试数据辅助工具类
└── HmDianPingApplicationTests.java       # Spring Boot 启动测试
```

## 🎯 测试分类

### 1️⃣ 单元测试 (Unit Tests)
- **位置**：`src/test/java/com/hmdp/service/impl/`
- **特点**：使用 Mockito 模拟依赖，测试单个类的逻辑
- **运行速度**：快 ⚡
- **示例**：`ShopServiceImplTest.java`

### 2️⃣ 集成测试 (Integration Tests)
- **位置**：`src/test/java/com/hmdp/controller/`
- **特点**：启动真实 Spring Context，连接真实数据库和 Redis
- **运行速度**：较慢 🐢
- **示例**：`ShopControllerIntegrationTest.java`

## 🚀 快速开始

### 运行所有测试
```bash
mvn test
```

### 只运行单元测试
```bash
mvn test -Dtest=*Test
```

### 只运行集成测试
```bash
mvn test -Dtest=*IntegrationTest
```

### 运行特定测试类
```bash
mvn test -Dtest=ShopControllerIntegrationTest
```

## 📚 文档

- 📖 [ShopController 集成测试完整指南](../../docs/ShopController集成测试指南.md)
- 🚀 [快速入门教程](../../docs/快速入门-ShopController集成测试.md)

## 🛠️ 测试工具类使用

### BaseIntegrationTest（集成测试基类）

所有 Controller 集成测试都应继承此类：

```java
@DisplayName("UserController 集成测试")
class UserControllerIntegrationTest extends BaseIntegrationTest {
    
    @BeforeEach
    @Override
    public void setUp() {
        super.setUp();
        RestAssured.basePath = "/user";
    }
    
    @Test
    void testLogin() {
        given()
            .contentType(ContentType.JSON)
        .when()
            .get("/login")
        .then()
            .statusCode(200);
    }
}
```

### TestDataHelper（测试数据辅助类）

用于创建和清理测试数据：

```java
@Autowired
private TestDataHelper testDataHelper;

// 创建测试商铺
Shop shop = testDataHelper.createAndSaveShop("测试商铺", 1L);

// 批量创建
List<Long> shopIds = testDataHelper.createBatchShops(10, 1L);

// 清理数据
testDataHelper.deleteShop(shopId);
testDataHelper.deleteBatchShops(shopIds);

// 清理缓存
testDataHelper.clearAllTestCache();
```

## ⚙️ 配置说明

### 测试环境配置
测试环境配置文件：`src/main/resources/application-test.yaml`

```yaml
spring:
  datasource:
    url: jdbc:mysql://192.168.155.1:3306/hmdp
    username: root
    password: root
  redis:
    host: 172.17.0.1
    port: 6379
    password: 123456
```

### 激活测试配置
集成测试类使用 `@ActiveProfiles("test")` 激活测试配置：

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")  // 👈 激活 application-test.yaml
class MyIntegrationTest extends BaseIntegrationTest {
    // ...
}
```

## 📊 代码覆盖率

运行测试后查看覆盖率报告：

```bash
# 生成报告
mvn test

# 查看报告（Windows）
start target/site/jacoco/index.html

# 查看报告（Linux/Mac）
open target/site/jacoco/index.html
```

## ✅ 测试最佳实践

1. **命名规范**
   - 单元测试：`XxxTest.java`
   - 集成测试：`XxxIntegrationTest.java`
   - 测试方法：`test<方法名>_When<条件>_Should<结果>()`

2. **测试隔离**
   - 使用 `@BeforeEach` 准备测试数据
   - 使用 `@AfterEach` 清理测试数据
   - 每个测试方法应独立运行

3. **断言清晰**
   ```java
   // ❌ 不好：只检查状态码
   .then().statusCode(200);
   
   // ✅ 好：具体验证返回内容
   .then()
       .statusCode(200)
       .body("success", equalTo(true))
       .body("data.id", equalTo(1))
       .body("data.name", notNullValue());
   ```

4. **使用 @DisplayName**
   ```java
   @Test
   @DisplayName("成功场景：查询存在的商铺")
   void testQueryShopById_Success() {
       // ...
   }
   ```

## 🐛 调试技巧

### 1. 启用 RestAssured 日志
```java
RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
```

### 2. 打印请求和响应
```java
given()
    .log().all()  // 打印请求
.when()
    .get("/shop/1")
.then()
    .log().all()  // 打印响应
    .statusCode(200);
```

### 3. 使用断点调试
在 IDE 中对测试方法设置断点，Debug 模式运行测试。

## ⚠️ 注意事项

1. **不要在生产环境运行测试** ⚠️
   - 测试会修改数据库和 Redis
   - 确保使用独立的测试环境

2. **及时清理测试数据** 🧹
   - 使用 `@AfterEach` 自动清理
   - 避免测试数据污染

3. **控制测试数据量** 📦
   - 批量创建时不要超过必要数量
   - 避免测试运行缓慢

4. **Redis 缓存管理** 🔄
   - 测试前清理相关缓存
   - 避免缓存干扰测试结果

## 📞 联系与支持

如有问题，请查看：
- 📖 [完整测试指南](../../docs/ShopController集成测试指南.md)
- 🚀 [快速入门教程](../../docs/快速入门-ShopController集成测试.md)

---

**Happy Testing!** 🎉
