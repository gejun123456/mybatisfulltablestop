# MyBatis Full Table Interceptor (JSqlParser AST Edition)

[![Maven Central](https://img.shields.io/maven-central/v/io.github.gejun123456/mybatis-full-table-interceptor.svg)](https://central.sonatype.com/artifact/io.github.gejun123456/mybatis-full-table-interceptor)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)

基于 **JSqlParser 语法抽象语法树（AST）分析** 的 MyBatis 全表操作安全拦截器。

用于防止 MyBatis 动态 SQL 因条件缺失意外执行全表 `SELECT`, `UPDATE` 和 `DELETE` 操作。

```text
SELECT * FROM user
        ↓
      BLOCK

UPDATE user SET status = 0
        ↓
      BLOCK

DELETE FROM user
## 核心特性：通过 Package Shading（包重定位）杜绝版本冲突

很多项目不敢轻易引入 JSqlParser 是因为担心与现有的 PageHelper、MyBatis-Plus 等类库发生 JSqlParser 版本冲突（如 `NoClassDefFoundError: SelectBody`）。

本工程采用了业界成熟的 **Bytecode Shading & Relocation** 方案（与 Spring 重定位 ASM 为 `org.springframework.asm` 相同）：

* 构建时通过 `maven-shade-plugin` 自动将 JSqlParser 重定位为内部私有包：
  ```text
  net.sf.jsqlparser  ==>  com.github.mybatisfulltable.shaded.jsqlparser
  ```
* 所有 AST 字节码引用均被重写为内部私有命名空间，并随本 Jar 一同打包。
* **对业务项目完全透明**：不会污染宿主工程的任何依赖树。宿主工程中无论是 PageHelper 使用老版 JSqlParser，还是其他组件使用新版，**均可 100% 和谐共存，绝无类库冲突**！

---

## 1. Maven Central 依赖坐标

### Maven
```xml
<dependency>
    <groupId>io.github.gejun123456</groupId>
    <artifactId>mybatis-full-table-interceptor</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Gradle
```groovy
implementation 'io.github.gejun123456:mybatis-full-table-interceptor:1.0.0'
```

---

## 2. 拦截器配置与使用

### 方式 A：Spring Boot 环境（开箱即用）
引入依赖后即自动生效，默认开启严格保护模式，无需编写额外 Java 配置。

可在 `application.yml` 中自定义行为：
```yaml
mybatis:
  full-table:
    enabled: true        # 是否启用拦截器，默认 true
    block-select: true   # 是否拦截全表普通 SELECT，默认 true
    block-count: false   # 是否拦截全表 SELECT COUNT(*)，默认 false（放行常用 count 统计；若需严格拦截设为 true）
    block-update: true   # 是否拦截全表 UPDATE，默认 true
    block-delete: true   # 是否拦截全表 DELETE，默认 true
    mode: STRICT         # 拦截模式：STRICT (抛出异常阻止执行) 或 WARN (仅记录告警日志放行)
```

### 方式 B：Spring Bean 手动注册
```java
@Configuration
public class MyBatisConfig {

    @Bean
    public FullTableInterceptor fullTableInterceptor() {
        return new FullTableInterceptor();
    }
}
```

### 方式 C：原生 MyBatis XML 配置 (`mybatis-config.xml`)
```xml
<plugins>
    <plugin interceptor="com.github.mybatisfulltable.interceptor.FullTableInterceptor">
        <property name="enabled" value="true"/>
        <property name="blockSelect" value="true"/>
        <property name="blockCount" value="false"/>
        <property name="blockUpdate" value="true"/>
        <property name="blockDelete" value="true"/>
        <property name="mode" value="STRICT"/>
    </plugin>
</plugins>
```

---

## 3. 核心拦截规则

| 操作类型 | SQL 示例 | 默认行为 | 说明 |
| :--- | :--- | :--- | :--- |
| **SELECT** | `SELECT * FROM user` | **BLOCK** | 缺少 WHERE 条件 |
| **SELECT** | `SELECT * FROM user WHERE 1 = 1` | **BLOCK** | 恒真条件，无实际过滤效果 |
| **SELECT** | `SELECT * FROM user WHERE id = ?` | **ALLOW** | 存在有效过滤条件 |
| **SELECT** | `SELECT * FROM user WHERE 1 = 1 AND id = ?` | **ALLOW** | 动态拼接后存在有效条件 |
| **SELECT** | `SELECT 1` / `SELECT 1 FROM DUAL` | **ALLOW** | 无数据表查询或哑表查询放行 |
| **SELECT** | `SELECT count(*) FROM user` | **ALLOW** | 默认 `block-count=false` 放行（设为 true 则拦截） |
| **UPDATE** | `UPDATE user SET status = 0` | **BLOCK** | 缺少 WHERE 条件 |
| **UPDATE** | `UPDATE user SET status = 0 WHERE 1 = 1` | **BLOCK** | 恒真条件 |
| **UPDATE** | `UPDATE user SET status = 0 WHERE id = ?` | **ALLOW** | 存在有效过滤条件 |
| **DELETE** | `DELETE FROM user` | **BLOCK** | 缺少 WHERE 条件 |
| **DELETE** | `DELETE FROM user WHERE 1 = 1` | **BLOCK** | 恒真条件 |
| **DELETE** | `DELETE FROM user WHERE id = ?` | **ALLOW** | 存在有效过滤条件 |

---

## 4. 豁免全表操作：`@IgnoreFullTable`

对于定时任务清理历史数据、全量同步、报表导出等明确合法的全表操作，使用 `@IgnoreFullTable` 进行豁免：

### 方法级豁免
```java
@Mapper
public interface UserMapper {

    // 意外未传参数导致动态 SQL 生成 SELECT * FROM user -> 被拦截！
    List<User> findUsers();

    // 正常条件查询 -> 放行
    User findById(@Param("id") Long id);

    // 显式豁免全表查询
    @IgnoreFullTable(reason = "报表全量导出")
    List<User> findAll();

    // 显式豁免全表删除
    @IgnoreFullTable(reason = "定时任务清空缓存数据")
    int deleteAll();
}
```

### 接口级豁免
```java
@IgnoreFullTable(reason = "该 Mapper 内均为系统批量维护操作")
@Mapper
public interface MaintenanceMapper {
    int clearAllLogs();
    int resetAllStatus();
}
```

---

## 5. 本地构建与运行测试

### 运行测试
```bash
./mvnw clean test
```

### 构建打包（包含 Jar、源码包、Javadoc 包）
```bash
./mvnw clean package
```

---

## 6. 发布到 Maven Central (Central Portal)

本项目已配置支持发布到最新版 **Maven Central Publishing Portal** (`central.sonatype.com`)。

### 方式一：GitHub Actions 自动发布（推荐）

在 GitHub 仓库中设置以下 Secrets：
1. `MAVEN_USERNAME`：Sonatype Central Portal User Token Username。
2. `MAVEN_PASSWORD`：Sonatype Central Portal User Token Password。
3. `GPG_PRIVATE_KEY`：GPG 私钥 ASCII 导出内容。
4. `GPG_PASSPHRASE`：GPG 私钥密码。

推送 Tag 触发发布：
```bash
git tag v1.0.0
git push origin v1.0.0
```

### 方式二：本地命令行发布
```bash
./mvnw clean deploy -P release
```

---

## 7. License

[Apache License 2.0](LICENSE)
