# ShopController 集成测试文档

## 📋 概述

本文档说明如何运行 `ShopController` 的集成测试。测试使用 **JUnit 5 + RestAssured** 框架，连接真实的测试环境（MySQL 和 Redis），**不使用 Mock**。

## 🎯 测试目标

- ✅ 验证 Controller 层的 HTTP 接口逻辑
- ✅ 验证参数校验和边界条件处理
- ✅ 验证与数据库的交互是否正常
- ✅ 验证 Redis 缓存机制
- ✅ 验证地理位置查询功能

## 🛠️ 前置条件

### 1. 测试环境准备

确保测试环境的 MySQL 和 Redis 服务已启动并可访问：

```bash
# 检查 MySQL 连接
mysql -h 192.168.155.1 -P 3306 -uroot -proot -e "SELECT 1"

# 检查 Redis 连接
redis-cli -h 172.17.0.1 -p 6379 -a 123456 PING
```

### 2. 数据库初始化

确保测试数据库 `hmdp` 已创建并包含必要的表结构：

```sql
-- 如果数据库不存在，创建它
CREATE DATABASE IF NOT EXISTS hmdp CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 使用数据库
USE hmdp;

-- 确保 tb_shop 表存在（根据项目实际 SQL 脚本执行）
```

**注意**：测试会自动预热必要的测试数据，无需手动插入数据。

### 3. 数据预热机制

测试类使用 `@BeforeAll` 自动预热基础测试数据：

```java
@BeforeAll
static void setUpTestData(@Autowired TestDataHelper helper) {
    // 创建3个预热商铺，用于查询和更新测试
    Shop shop1 = helper.createAndSaveShop("预热测试商铺1", 1L);
    preloadedShopId1 = shop1.getId();
    
    System.out.println("✅ 测试数据预热完成: ID=" + preloadedShopId1);
}
```

**优势**：
- ✅ **自动化**：无需手动准备数据
- ✅ **隔离性**：测试数据独立，不影响其他环境
- ✅ **可靠性**：每次运行都有一致的测试数据
- ✅ **易清理**：测试结束自动删除

### 4. 测试配置文件

测试配置文件位于 `src/main/resources/application-test.yaml`，主要配置如下：

```yaml
spring:
  datasource:
    url: jdbc:mysql://192.168.155.1:3306/hmdp?useUnicode=true&characterEncoding=UTF-8
    username: root
    password: root
  redis:
    host: 172.17.0.1
    port: 6379
    password: 123456
```

## 🚀 运行测试

### 方式一：使用 Maven 命令

```bash
# 运行所有测试
mvn test

# 只运行 ShopController 集成测试
mvn test -Dtest=ShopControllerIntegrationTest

# 运行特定测试方法
mvn test -Dtest=ShopControllerIntegrationTest#testQueryShopById_Success
```

### 方式二：使用 IDE (IntelliJ IDEA / VS Code)

1. 打开测试文件：`src/test/java/com/hmdp/controller/ShopControllerIntegrationTest.java`
2. 点击类名旁边的绿色运行按钮（运行所有测试）
3. 或点击具体测试方法旁边的运行按钮（运行单个测试）

### 方式三：使用 Gradle (如果项目使用 Gradle)

```bash
./gradlew test --tests ShopControllerIntegrationTest
```

## 📊 测试覆盖范围

### 1. 查询商铺 (`GET /{id}`)

| 测试场景 | 测试方法 | 验证点 |
|---------|---------|-------|
| ✅ 成功查询存在的商铺 | `testQueryShopById_Success` | 返回正确的商铺信息 |
| ❌ 查询不存在的商铺 | `testQueryShopById_NotFound` | 返回 "店铺不存在" 错误 |
| 🔢 ID 边界值测试 | `testQueryShopById_ZeroId` | 处理非法 ID |
| 🔄 缓存验证 | `testQueryShopById_CacheHit` | 验证 Redis 缓存机制 |

### 2. 新增商铺 (`POST /`)

| 测试场景 | 测试方法 | 验证点 |
|---------|---------|-------|
| ✅ 成功新增商铺 | `testSaveShop_Success` | 返回商铺ID，数据库包含新记录 |
| ❌ 缺少必填字段 | `testSaveShop_MissingRequiredField` | 参数校验 |
| 🔢 边界值测试 | `testSaveShop_LongName` | 处理超长字段 |

### 3. 更新商铺 (`PUT /`)

| 测试场景 | 测试方法 | 验证点 |
|---------|---------|-------|
| ✅ 成功更新商铺 | `testUpdateShop_Success` | 数据库记录被更新 |
| ❌ 更新不存在的商铺 | `testUpdateShop_NotFound` | 错误处理 |
| 🔄 缓存一致性 | `testUpdateShop_CacheInvalidation` | 更新后删除缓存 |

### 4. 按类型查询商铺 (`GET /of/type`)

| 测试场景 | 测试方法 | 验证点 |
|---------|---------|-------|
| ✅ 查询指定类型商铺 | `testQueryShopByType_Success` | 返回列表 |
| 📄 分页测试 | `testQueryShopByType_Pagination` | 分页参数生效 |
| 🌍 地理位置查询 | `testQueryShopByType_WithGeo` | 经纬度参数生效 |
| 🔢 无效类型ID | `testQueryShopByType_InvalidTypeId` | 返回空列表 |
| ⚙️ 默认值测试 | `testQueryShopByType_DefaultCurrent` | 默认 current=1 |

### 5. 按名称查询商铺 (`GET /of/name`)

| 测试场景 | 测试方法 | 验证点 |
|---------|---------|-------|
| ✅ 模糊查询商铺名称 | `testQueryShopByName_Success` | 返回匹配结果 |
| 📄 空查询测试 | `testQueryShopByName_EmptyName` | 返回所有商铺 |
| ❌ 无结果场景 | `testQueryShopByName_NoResult` | 返回空列表 |
| 📄 分页测试 | `testQueryShopByName_Pagination` | 分页参数生效 |
| 🛡️ SQL注入防护 | `testQueryShopByName_SpecialCharacters` | 安全性验证 |

## 🧰 测试工具类

### TestDataHelper

位于 `src/test/java/com/hmdp/utils/TestDataHelper.java`，提供以下功能：

```java
@Autowired
private TestDataHelper testDataHelper;

// 创建并保存测试商铺
Shop shop = testDataHelper.createAndSaveShop("测试商铺", 1L);

// 批量创建商铺
List<Long> shopIds = testDataHelper.createBatchShops(10, 1L);

// 删除测试数据
testDataHelper.deleteShop(shopId);
testDataHelper.deleteBatchShops(shopIds);

// 清理缓存
testDataHelper.clearAllTestCache();

// 检查商铺是否存在
boolean exists = testDataHelper.shopExists(shopId);
boolean inCache = testDataHelper.shopExistsInCache(shopId);
```

## 📈 查看测试报告

### JaCoCo 代码覆盖率报告

运行测试后，可以查看代码覆盖率报告：

```bash
# 生成覆盖率报告
mvn test

# 打开报告（Windows）
start target/site/jacoco/index.html

# 打开报告（macOS/Linux）
open target/site/jacoco/index.html
```

### Surefire 测试报告

测试结果报告位于：

```
target/surefire-reports/
├── TEST-com.hmdp.controller.ShopControllerIntegrationTest.xml
└── com.hmdp.controller.ShopControllerIntegrationTest.txt
```

## 🐛 常见问题

### 1. 连接数据库失败

**错误信息：** `Communications link failure`

**解决方案：**
- 检查 MySQL 服务是否启动
- 验证 `application-test.yaml` 中的 IP 地址和端口
- 检查防火墙设置

```bash
# 测试 MySQL 连接
mysql -h 192.168.155.1 -P 3306 -uroot -proot
```

### 2. 连接 Redis 失败

**错误信息：** `Unable to connect to Redis`

**解决方案：**
- 检查 Redis 服务是否启动
- 验证 Redis 密码是否正确
- 检查 Redis 配置中的 `bind` 地址

```bash
# 测试 Redis 连接
redis-cli -h 172.17.0.1 -p 6379 -a 123456 PING
```

### 3. 测试数据污染

**问题：** 测试之间相互影响

**解决方案：**
- 每个测试方法的 `@AfterEach` 会自动清理数据
- 手动清理：使用 `testDataHelper.clearAllTestCache()`
- 重置数据库：重新导入 SQL 脚本

### 4. 端口冲突

**错误信息：** `Port 8081 already in use`

**解决方案：**
- 测试使用随机端口 (`RANDOM_PORT`)，不应该出现冲突
- 如果仍有问题，检查是否有其他 Spring Boot 应用正在运行

## 🎓 最佳实践

1. **测试隔离**：每个测试方法应独立，不依赖其他测试的执行顺序
2. **数据清理**：使用 `@AfterEach` 自动清理测试数据
3. **缓存管理**：测试前清理 Redis 缓存，避免缓存干扰
4. **断言清晰**：使用 RestAssured 的链式断言，确保验证点明确
5. **命名规范**：测试方法名应清晰表达测试场景，如 `testXxx_WhenCondition_ShouldResult`

## 📚 参考资料

- [RestAssured 官方文档](https://rest-assured.io/)
- [JUnit 5 用户指南](https://junit.org/junit5/docs/current/user-guide/)
- [Spring Boot Testing 指南](https://spring.io/guides/gs/testing-web/)
- [MyBatis-Plus 文档](https://baomidou.com/)

## 📝 维护日志

| 日期 | 作者 | 变更内容 |
|------|------|---------|
| 2026-01-11 | SDET | 初始版本：创建 ShopController 集成测试 |

---

**注意：** 本测试套件连接真实的测试环境，请勿在生产环境运行！
