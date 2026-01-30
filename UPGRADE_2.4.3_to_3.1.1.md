# Nacos-Plugin 从 2.4.3 升级到 3.1.1 修改说明

本文档详细记录 nacos-plugin 工程从 **Nacos 2.4.3** 升级至 **Nacos 3.1.1**、**JDK 17** 的所有修改点，便于后续维护与二次升级参考。

---

## 一、升级概览

| 项目 | 2.4.3 | 3.1.1 |
|------|--------|--------|
| Nacos 版本 | 2.4.3 | **3.1.1** |
| JDK | 17 | 17（保持不变） |
| 涉及模块 | 根 POM、datasource、config、encryption | 同上 |

---

## 二、根 POM 修改（`pom.xml`）

### 2.1 版本与属性

| 修改项 | 原值 | 新值 | 说明 |
|--------|------|------|------|
| `alibaba-nacos.version` | `2.4.3` | **`3.1.1`** | 所有子模块对 Nacos 相关依赖统一使用 3.1.1 |
| `junit.version` | `4.12` | **`4.13.2`** | 测试依赖版本升级 |
| 新增 `commons-codec.version` | - | **`1.16.0`** | 供 encryption 插件使用 |

### 2.2 dependencyManagement 新增依赖

在 `<dependencyManagement><dependencies>` 中新增：

```xml
<dependency>
    <groupId>commons-codec</groupId>
    <artifactId>commons-codec</artifactId>
    <version>${commons-codec.version}</version>
</dependency>
```

**原因**：Nacos 3.1.1 的 nacos-encryption-plugin 未传递暴露 `commons-codec`，而 nacos-aes-encryption-plugin 使用了 `org.apache.commons.codec.binary.Hex`，需显式声明该依赖。

---

## 三、Datasource 插件修改

Nacos 3.1.1 的 datasource 插件做了结构性调整：**移除聚合表相关 Mapper**，**新增灰阶与迁移相关 Mapper**。本工程据此做了对应增删与适配。

### 3.1 模块：`nacos-datasource-plugin-ext-base`

#### 3.1.1 删除的文件

| 文件路径 | 说明 |
|----------|------|
| `src/main/java/.../impl/base/BaseConfigInfoAggrMapper.java` | 2.4.3 中基于 `ConfigInfoAggrMapperByMySql` 的扩展基类；3.1.1 已移除 `ConfigInfoAggrMapper` 接口，故删除 |

#### 3.1.2 新增的文件

**（1）BaseConfigInfoGrayMapper.java**

- **路径**：`src/main/java/com/alibaba/nacos/plugin/datasource/impl/base/BaseConfigInfoGrayMapper.java`
- **作用**：为各数据库扩展提供灰阶配置 Mapper 的公共实现基类（对应 Nacos 3.1.1 的 `ConfigInfoGrayMapper`）。
- **实现要点**：
  - 继承 `AbstractMapper`（nacos-datasource-plugin 提供），实现 `ConfigInfoGrayMapper`。
  - 通过 `DatabaseDialectManager.getInstance().getDialect(getDataSource())` 获取方言，使用 `getLimitPageSqlWithOffset` 生成分页 SQL。
  - 实现 `findAllConfigInfoGrayForDumpAllFetchRows(MapperContext)`：生成 `config_info_gray` 表的分页查询。
  - 实现 `getFunction(String)`：委托给当前数据源的 `DatabaseDialect.getFunction`。
  - `getDataSource()` 由各数据库子类实现（返回如 `postgresql`、`oracle` 等）。

**（2）BaseConfigMigrateMapper.java**

- **路径**：`src/main/java/com/alibaba/nacos/plugin/datasource/impl/base/BaseConfigMigrateMapper.java`
- **作用**：为各数据库扩展提供配置迁移 Mapper 的公共实现基类（对应 Nacos 3.1.1 的 `ConfigMigrateMapper`）。
- **实现要点**：
  - 继承 `AbstractMapper`，实现 `ConfigMigrateMapper`。
  - `ConfigMigrateMapper` 中所有 SQL 均由接口 default 方法提供，因此基类只需实现 `getFunction(String)`（同样委托给 `DatabaseDialect`），以及由子类实现 `getDataSource()`。

#### 3.1.3 常量类修改：DatabaseTypeConstant.java

- **路径**：`src/main/java/.../constants/DatabaseTypeConstant.java`
- **修改**：在类末尾新增常量：
  - `public static final String OCEANBASE = "oceanbase";`
- **原因**：Oceanbase 扩展中 `getDataSource()` 需返回 `"oceanbase"`，与 META-INF 中注册的 dialect 一致，此处统一为常量便于维护。

---

### 3.2 各数据库扩展子模块的修改

以下各子模块均需：**删除 ConfigInfoAggr 相关类及 SPI 注册**，**新增 ConfigInfoGray / ConfigMigrate 实现类并注册**。若 3.1.1 中 `GroupCapacityMapper` / `TenantCapacityMapper` 接口新增了方法，则通过改为继承 Base 或补充实现完成适配。

#### 3.2.1 PostgreSQL（nacos-postgresql-datasource-plugin-ext）

| 操作 | 文件/位置 | 说明 |
|------|------------|------|
| **删除** | `impl/postgresql/ConfigInfoAggrMapperByPostgresql.java` | 实现 2.4.3 的 ConfigInfoAggrMapper，3.1.1 已无此接口 |
| **新增** | `impl/postgresql/ConfigInfoGrayMapperByPostgresql.java` | 继承 `BaseConfigInfoGrayMapper`，`getDataSource()` 返回 `DatabaseTypeConstant.POSTGRESQL` |
| **新增** | `impl/postgresql/ConfigMigrateMapperByPostgresql.java` | 继承 `BaseConfigMigrateMapper`，`getDataSource()` 返回 `DatabaseTypeConstant.POSTGRESQL` |
| **修改** | `resources/META-INF/services/com.alibaba.nacos.plugin.datasource.mapper.Mapper` | 去掉一行 `ConfigInfoAggrMapperByPostgresql`，增加两行：`ConfigInfoGrayMapperByPostgresql`、`ConfigMigrateMapperByPostgresql` |
| **修改** | `impl/postgresql/TenantInfoMapperByPostgresql.java` | 类注释由「The postgresql implementation of ConfigInfoAggrMapper」改为「The postgresql implementation of TenantInfoMapper」（仅为注释修正） |

#### 3.2.2 Oracle（nacos-oracle-datasource-plugin-ext）

| 操作 | 文件/位置 | 说明 |
|------|------------|------|
| **删除** | `impl/oracle/ConfigInfoAggrMapperByOracle.java` | 原继承 AbstractOracleMapper 实现 ConfigInfoAggrMapper，3.1.1 移除 |
| **新增** | `impl/oracle/ConfigInfoGrayMapperByOracle.java` | 继承 `BaseConfigInfoGrayMapper`，`getDataSource()` 返回 `DatabaseTypeConstant.ORACLE` |
| **新增** | `impl/oracle/ConfigMigrateMapperByOracle.java` | 继承 `BaseConfigMigrateMapper`，`getDataSource()` 返回 `DatabaseTypeConstant.ORACLE` |
| **修改** | `impl/oracle/GroupCapacityMapperByOracle.java` | **整类重写**：由「继承 AbstractOracleMapper + 只实现 selectGroupInfoBySize」改为「继承 BaseGroupCapacityMapper，仅重写 getDataSource() 返回 ORACLE」。这样 3.1.1 中 GroupCapacityMapper 新增的 select、insertIntoSelect、updateUsageByWhere 等方法均由 Base（进而 MySQL 实现）提供，仅分页等通过 Base 内 DatabaseDialect 适配 |
| **修改** | `impl/oracle/TenantCapacityMapperByOracle.java` | **新增方法**：`select(MapperContext)`，SQL 为按 tenant_id 查询 tenant_capacity，tenant_id 使用 `NVL(?, NamespaceUtil.getNamespaceDefaultId())` 以兼容 Oracle 空字符串语义；并增加 `import java.util.Collections` |
| **修改** | `resources/META-INF/services/.../Mapper` | 删除 ConfigInfoAggrMapperByOracle 注册，增加 ConfigInfoGrayMapperByOracle、ConfigMigrateMapperByOracle |

#### 3.2.3 DM 达梦（nacos-dm-datasource-plugin-ext）

| 操作 | 文件/位置 | 说明 |
|------|------------|------|
| **删除** | `impl/dm/ConfigInfoAggrMapperByDaMeng.java` | 原继承 BaseConfigInfoAggrMapper，3.1.1 不再需要 |
| **新增** | `impl/dm/ConfigInfoGrayMapperByDaMeng.java` | 继承 BaseConfigInfoGrayMapper，getDataSource 返回 `DatabaseTypeConstant.DM` |
| **新增** | `impl/dm/ConfigMigrateMapperByDaMeng.java` | 继承 BaseConfigMigrateMapper，getDataSource 返回 `DatabaseTypeConstant.DM` |
| **修改** | `impl/dm/TenantInfoMapperByDaMeng.java` | 类注释由「The dameng implementation of ConfigInfoAggrMapper」改为「The dameng implementation of TenantInfoMapper」 |
| **修改** | META-INF 中 Mapper 列表 | 删除 ConfigInfoAggrMapperByDaMeng，增加 ConfigInfoGrayMapperByDaMeng、ConfigMigrateMapperByDaMeng |

#### 3.2.4 MSSQL（nacos-mssql-datasource-plugin-ext）

| 操作 | 文件/位置 | 说明 |
|------|------------|------|
| **删除** | `impl/mssql/ConfigInfoAggrMapperBySqlServer.java` | 原继承 BaseConfigInfoAggrMapper |
| **新增** | `impl/mssql/ConfigInfoGrayMapperBySqlServer.java` | 继承 BaseConfigInfoGrayMapper，getDataSource 返回 `DatabaseTypeConstant.SQLSERVER` |
| **新增** | `impl/mssql/ConfigMigrateMapperBySqlServer.java` | 继承 BaseConfigMigrateMapper，getDataSource 返回 `DatabaseTypeConstant.SQLSERVER` |
| **修改** | META-INF 中 Mapper 列表 | 删除 ConfigInfoAggrMapperBySqlServer，增加上述两个新类 |

#### 3.2.5 Kingbase 人大金仓（nacos-kingbase-datasource-plugin-ext）

| 操作 | 文件/位置 | 说明 |
|------|------------|------|
| **删除** | `impl/kingbase/ConfigInfoAggrMapperByKingbase.java` | 原继承 BaseConfigInfoAggrMapper |
| **新增** | `impl/kingbase/ConfigInfoGrayMapperByKingbase.java` | 继承 BaseConfigInfoGrayMapper，getDataSource 返回 `DatabaseTypeConstant.KINGBASE` |
| **新增** | `impl/kingbase/ConfigMigrateMapperByKingbase.java` | 继承 BaseConfigMigrateMapper，getDataSource 返回 `DatabaseTypeConstant.KINGBASE` |
| **修改** | `impl/kingbase/TenantInfoMapperByKingbase.java` | 类注释由「The kingbase implementation of ConfigInfoAggrMapper」改为「The kingbase implementation of TenantInfoMapper」 |
| **修改** | META-INF 中 Mapper 列表 | 删除 ConfigInfoAggrMapperByKingbase，增加 ConfigInfoGrayMapperByKingbase、ConfigMigrateMapperByKingbase |

#### 3.2.6 Oceanbase（nacos-oceanbase-datasource-plugin-ext）

| 操作 | 文件/位置 | 说明 |
|------|------------|------|
| **删除** | `impl/oceanbase/ConfigInfoAggrMapperByOceanbase.java` | 原继承 AbstractOceanbaseMapper 实现 ConfigInfoAggrMapper |
| **新增** | `impl/oceanbase/ConfigInfoGrayMapperByOceanbase.java` | 继承 BaseConfigInfoGrayMapper，getDataSource 返回 `DatabaseTypeConstant.OCEANBASE`（与 DatabaseTypeConstant 新增常量一致） |
| **新增** | `impl/oceanbase/ConfigMigrateMapperByOceanbase.java` | 继承 BaseConfigMigrateMapper，getDataSource 返回 `DatabaseTypeConstant.OCEANBASE` |
| **修改** | `impl/oceanbase/GroupCapacityMapperByOceanbase.java` | **整类重写**：由「继承 AbstractOceanbaseMapper + 仅实现 selectGroupInfoBySize」改为「继承 BaseGroupCapacityMapper，仅重写 getDataSource() 返回 OCEANBASE」，以满足 3.1.1 中 GroupCapacityMapper 的 updateUsageByWhere 等新方法 |
| **修改** | `impl/oceanbase/TenantCapacityMapperByOceanbase.java` | **新增方法**：`select(MapperContext)`，SQL 与 Oracle 风格一致（tenant_id 使用 NVL，默认命名空间使用 DEFAULT_NAMESPACE_ID）；并增加 `import java.util.Collections` |
| **修改** | META-INF 中 Mapper 列表 | 删除 ConfigInfoAggrMapperByOceanbase，增加 ConfigInfoGrayMapperByOceanbase、ConfigMigrateMapperByOceanbase |

#### 3.2.7 Yashan 崖山（nacos-yashan-datasource-plugin-ext）

| 操作 | 文件/位置 | 说明 |
|------|------------|------|
| **删除** | `impl/yashan/ConfigInfoAggrMapperByYaShan.java` | 原继承 AbstractYaShanMapper 实现 ConfigInfoAggrMapper |
| **新增** | `impl/yashan/ConfigInfoGrayMapperByYaShan.java` | 继承 BaseConfigInfoGrayMapper，getDataSource 返回 `DatabaseTypeConstant.YASDB` |
| **新增** | `impl/yashan/ConfigMigrateMapperByYaShan.java` | 继承 BaseConfigMigrateMapper，getDataSource 返回 `DatabaseTypeConstant.YASDB` |
| **修改** | `impl/yashan/GroupCapacityMapperByYaShan.java` | **整类重写**：由「继承 AbstractYaShanMapper + 仅实现 selectGroupInfoBySize」改为「继承 BaseGroupCapacityMapper，仅重写 getDataSource() 返回 YASDB」 |
| **修改** | `impl/yashan/TenantCapacityMapperByYaShan.java` | **新增方法**：`select(MapperContext)`，SQL 使用 `NVL(?, NamespaceUtil.getNamespaceDefaultId())`；并增加 `import java.util.Collections` |
| **修改** | META-INF 中 Mapper 列表 | 删除 ConfigInfoAggrMapperByYaShan，增加 ConfigInfoGrayMapperByYaShan、ConfigMigrateMapperByYaShan |

#### 3.2.8 OpenGauss（nacos-opengauss-datasource-plugin-ext）

该模块在 2.4.3 时期已包含 Gray/Migrate 实现，且无 ConfigInfoAggr，因此**仅做代码清理**：

| 操作 | 文件/位置 | 说明 |
|------|------------|------|
| **修改** | `impl/opengauss/OpenGaussConfigMigrateMapper.java` | 删除无用 import：`ConfigInfoGrayMapper`、`MapperContext`、`MapperResult`、`java.util.Collections`；将类注释由「The base implementation of TenantInfo」改为「The OpenGauss implementation of ConfigMigrateMapper for Nacos 3.1.1+」 |

META-INF 中已注册的 `OpenGaussConfigInfoGrayMapper`、`OpenGaussConfigMigrateMapper` 保持不变。

---

## 四、Config 插件修改

### 4.1 WebHook 插件（nacos-webhook-config-change-plugin）

| 文件 | 修改内容 |
|------|----------|
| `src/main/java/.../WebHookConfigChangePluginService.java` | **Import 变更**：`import org.apache.http.HttpStatus;` 改为 `import org.apache.hc.core5.http.HttpStatus;` |

**原因**：Nacos 3.1.1 将 HTTP 客户端从 Apache HttpClient 4 升级为 **HttpClient 5**，原 `org.apache.http.HttpStatus`（如 `SC_OK`、`SC_INTERNAL_SERVER_ERROR` 等）在 5.x 中位于 `org.apache.hc.core5.http.HttpStatus`。代码中仅使用这些常量，逻辑不变；依赖由 nacos-common 传递引入的 httpclient5/httpcore5 提供。

### 4.2 Whitelist 插件（nacos-whitelist-config-change-plugin）

| 文件 | 修改内容 |
|------|----------|
| `src/main/java/.../WhiteListConfigChangePluginService.java` | 1）**删除 import**：`import org.apache.http.entity.ContentType;`<br>2）**调用处修改**：原 `new MockMultipartFile(ContentType.APPLICATION_OCTET_STREAM.toString(), fileByFilteredBytes)` 改为 `new MockMultipartFile("application/octet-stream", fileByFilteredBytes)` |

**原因**：避免依赖 `org.apache.http.entity.ContentType`（HttpClient 4），且此处仅需 MIME 类型字符串 `"application/octet-stream"`，直接使用字面量即可，无需引入 HttpClient。

---

## 五、Encryption 插件修改

### 5.1 根 POM（见第二章）

- 在根 POM 的 `dependencyManagement` 中增加 `commons-codec` 的版本管理。

### 5.2 nacos-encryption-plugin-ext 父模块

| 文件 | 修改内容 |
|------|----------|
| `nacos-encryption-plugin-ext/pom.xml` | 在 `<dependencies>` 中新增：`<dependency><groupId>commons-codec</groupId><artifactId>commons-codec</artifactId></dependency>`（版本由根 POM 统一管理） |

**原因**：nacos-aes-encryption-plugin 中使用 `org.apache.commons.codec.binary.Hex` 进行十六进制编解码，Nacos 3.1.1 的 nacos-encryption-plugin 未传递该依赖，需在扩展工程中显式声明。

---

## 六、未修改的模块说明

以下模块在升级中**未做代码或依赖修改**，仅因根 POM 中 `alibaba-nacos.version` 改为 3.1.1 而间接使用新版本 Nacos 依赖：

- **nacos-trace-plugin-ext**（含 nacos-trace-logging-plugin）：接口与 2.4.3 一致，兼容 3.1.1。
- **nacos-custom-environment-plugin-ext**（含 nacos-db-password-encryption-plugin）：CustomEnvironmentPluginService 未变更，兼容 3.1.1。
- **nacos-config-change-plugin-ext** 下的 **nacos-fileformat-config-change-plugin**：ConfigChangePluginService 及入参出参未变，兼容 3.1.1。

本工程中**没有**基于 auth 插件的扩展，因此未涉及 Nacos 3.1.1 中 AuthPluginService 的 `AuthResult` 等变更；若后续新增 auth 相关扩展，需按《Nacos插件2.4.3到3.1.1升级分析报告》中 auth 章节适配。

---

## 七、META-INF 注册表汇总（Datasource）

各数据库扩展的 SPI 文件路径均为：  
`src/main/resources/META-INF/services/com.alibaba.nacos.plugin.datasource.mapper.Mapper`

**统一变更规则**：

- **删除一行**：`com.alibaba.nacos.plugin.datasource.impl.<数据库>/ConfigInfoAggrMapperBy<数据库>`
- **新增两行**（紧接在原有第一个 Mapper 之后，保持按模块/字母顺序即可）：
  - `com.alibaba.nacos.plugin.datasource.impl.<数据库>.ConfigInfoGrayMapperBy<数据库>`
  - `com.alibaba.nacos.plugin.datasource.impl.<数据库>.ConfigMigrateMapperBy<数据库>`

例如 PostgreSQL 的 Mapper 文件中，原：

```text
com.alibaba.nacos.plugin.datasource.impl.postgresql.ConfigInfoAggrMapperByPostgresql
com.alibaba.nacos.plugin.datasource.impl.postgresql.ConfigInfoBetaMapperByPostgresql
...
```

改为：

```text
com.alibaba.nacos.plugin.datasource.impl.postgresql.ConfigInfoBetaMapperByPostgresql
com.alibaba.nacos.plugin.datasource.impl.postgresql.ConfigInfoGrayMapperByPostgresql
com.alibaba.nacos.plugin.datasource.impl.postgresql.ConfigMigrateMapperByPostgresql
...
```

其他数据库同理，仅类名与包路径随数据库名变化。

---

## 八、编译与验证

- **编译命令**：在工程根目录执行 `mvn clean compile -DskipTests`。
- **建议**：升级后使用 Nacos 3.1.1 服务端与对应 JDK 17 进行集成测试，尤其验证：
  - 各数据库扩展的 datasource 插件加载与 config 读写；
  - 灰阶发布、配置迁移等依赖 ConfigInfoGrayMapper/ConfigMigrateMapper 的功能（若使用）；
  - WebHook / Whitelist 配置变更插件的实际触发与 HTTP 调用。

---

## 九、修改文件清单（按路径）

便于快速检索，以下按路径列出所有被修改、新增或删除的文件（相对工程根目录）。

**根目录**

- `pom.xml`（修改）

**nacos-datasource-plugin-ext**

- `nacos-datasource-plugin-ext-base`
  - `src/main/java/.../constants/DatabaseTypeConstant.java`（修改）
  - `src/main/java/.../impl/base/BaseConfigInfoAggrMapper.java`（**删除**）
  - `src/main/java/.../impl/base/BaseConfigInfoGrayMapper.java`（**新增**）
  - `src/main/java/.../impl/base/BaseConfigMigrateMapper.java`（**新增**）
- `nacos-postgresql-datasource-plugin-ext`
  - `impl/postgresql/ConfigInfoAggrMapperByPostgresql.java`（**删除**）
  - `impl/postgresql/ConfigInfoGrayMapperByPostgresql.java`（**新增**）
  - `impl/postgresql/ConfigMigrateMapperByPostgresql.java`（**新增**）
  - `impl/postgresql/TenantInfoMapperByPostgresql.java`（修改注释）
  - `resources/META-INF/services/.../Mapper`（修改）
- `nacos-oracle-datasource-plugin-ext`
  - `impl/oracle/ConfigInfoAggrMapperByOracle.java`（**删除**）
  - `impl/oracle/ConfigInfoGrayMapperByOracle.java`（**新增**）
  - `impl/oracle/ConfigMigrateMapperByOracle.java`（**新增**）
  - `impl/oracle/GroupCapacityMapperByOracle.java`（重写）
  - `impl/oracle/TenantCapacityMapperByOracle.java`（新增 select 方法）
  - `resources/META-INF/services/.../Mapper`（修改）
- `nacos-dm-datasource-plugin-ext`
  - `impl/dm/ConfigInfoAggrMapperByDaMeng.java`（**删除**）
  - `impl/dm/ConfigInfoGrayMapperByDaMeng.java`（**新增**）
  - `impl/dm/ConfigMigrateMapperByDaMeng.java`（**新增**）
  - `impl/dm/TenantInfoMapperByDaMeng.java`（修改注释）
  - `resources/META-INF/services/.../Mapper`（修改）
- `nacos-mssql-datasource-plugin-ext`
  - `impl/mssql/ConfigInfoAggrMapperBySqlServer.java`（**删除**）
  - `impl/mssql/ConfigInfoGrayMapperBySqlServer.java`（**新增**）
  - `impl/mssql/ConfigMigrateMapperBySqlServer.java`（**新增**）
  - `resources/META-INF/services/.../Mapper`（修改）
- `nacos-kingbase-datasource-plugin-ext`
  - `impl/kingbase/ConfigInfoAggrMapperByKingbase.java`（**删除**）
  - `impl/kingbase/ConfigInfoGrayMapperByKingbase.java`（**新增**）
  - `impl/kingbase/ConfigMigrateMapperByKingbase.java`（**新增**）
  - `impl/kingbase/TenantInfoMapperByKingbase.java`（修改注释）
  - `resources/META-INF/services/.../Mapper`（修改）
- `nacos-oceanbase-datasource-plugin-ext`
  - `impl/oceanbase/ConfigInfoAggrMapperByOceanbase.java`（**删除**）
  - `impl/oceanbase/ConfigInfoGrayMapperByOceanbase.java`（**新增**）
  - `impl/oceanbase/ConfigMigrateMapperByOceanbase.java`（**新增**）
  - `impl/oceanbase/GroupCapacityMapperByOceanbase.java`（重写）
  - `impl/oceanbase/TenantCapacityMapperByOceanbase.java`（新增 select 方法）
  - `resources/META-INF/services/.../Mapper`（修改）
- `nacos-yashan-datasource-plugin-ext`
  - `impl/yashan/ConfigInfoAggrMapperByYaShan.java`（**删除**）
  - `impl/yashan/ConfigInfoGrayMapperByYaShan.java`（**新增**）
  - `impl/yashan/ConfigMigrateMapperByYaShan.java`（**新增**）
  - `impl/yashan/GroupCapacityMapperByYaShan.java`（重写）
  - `impl/yashan/TenantCapacityMapperByYaShan.java`（新增 select 方法）
  - `resources/META-INF/services/.../Mapper`（修改）
- `nacos-opengauss-datasource-plugin-ext`
  - `impl/opengauss/OpenGaussConfigMigrateMapper.java`（修改 import 与注释）

**nacos-config-change-plugin-ext**

- `nacos-webhook-config-change-plugin/.../WebHookConfigChangePluginService.java`（修改 HttpStatus import）
- `nacos-whitelist-config-change-plugin/.../WhiteListConfigChangePluginService.java`（删除 ContentType 依赖，使用字面量）

**nacos-encryption-plugin-ext**

- `pom.xml`（新增 commons-codec 依赖）

**文档**

- `README.md`（增加版本与升级说明）
- `UPGRADE_2.4.3_to_3.1.1.md`（本说明文档）

---

## 七、SQL Schema 升级（与 Nacos 3.1.1 表结构对齐）

为避免因表/字段缺失导致运行时错误，各数据库扩展中的建表脚本已按 Nacos 3.1.1 官方 schema 做了升级，变更要点如下。

### 7.1 表结构变更摘要（相对 2.4.3）

| 变更类型 | 说明 |
|----------|------|
| **删除表** | `config_info_aggr`（聚合配置表在 3.1.1 中已移除） |
| **新增表** | `config_info_gray`（灰阶发布配置表，字段含：id, data_id, group_id, content, md5, src_user, src_ip, gmt_create, gmt_modified, app_name, tenant_id, gray_name, gray_rule, encrypted_data_key；唯一约束 `data_id, group_id, tenant_id, gray_name`；索引 `idx_dataid_gmt_modified`、`idx_gmt_modified`） |
| **his_config_info 新增列** | `publish_type`（varchar，默认 `'formal'`）、`gray_name`（varchar，可空）、`ext_info`（大文本，可空） |

**说明**：`ConfigMigrateMapper` 对应的逻辑表名虽为 `migrate_config`，但官方注释表明该表实际不存在，迁移逻辑操作的是 `config_info_gray`，因此无需单独建 `migrate_config` 表。

### 7.2 已升级的 Schema 文件

| 模块 | Schema 文件 | 修改内容 |
|------|-------------|----------|
| nacos-postgresql-datasource-plugin-ext | `schema/nacos-pg.sql` | 删除 config_info_aggr；新增 config_info_gray 表及索引；his_config_info 增加 publish_type、gray_name、ext_info |
| nacos-oracle-datasource-plugin-ext | `schema/nacos-oracle.sql` | 同上（含 sequence/trigger） |
| nacos-mssql-datasource-plugin-ext | `schema/nacos-mssql.sql` | 同上（MSSQL 语法） |
| nacos-dm-datasource-plugin-ext | `schema/nacos-dm.sql` | CONFIG_INFO_AGGR → CONFIG_INFO_GRAY；HIS_CONFIG_INFO 增加三列及注释/约束 |
| nacos-kingbase-datasource-plugin-ext | `schema/nacos-kingbase.sql` | 删除 config_info_aggr；新增 config_info_gray；his_config_info 增加三列 |
| nacos-yashan-datasource-plugin-ext | `schema/nacos-yashan.sql` | CONFIG_INFO_AGGR 序列/表/索引 → CONFIG_INFO_GRAY；HIS_CONFIG_INFO 增加三列 |
| nacos-opengauss-datasource-plugin-ext | `schema/nacos-openguass-compatible-oracle.sql` | 已为 3.1.1 结构（含 config_info_gray 及 his_config_info 新列），无需改动 |

### 7.3 已有库升级建议

若数据库此前已用 2.4.3 的 schema 初始化：

1. **备份数据**后，再执行结构变更。
2. **删除** `config_info_aggr` 表（或先停止使用后择机删除）。
3. **执行**对应数据库的 3.1.1 建表脚本中与 `config_info_gray` 相关的部分（建表 + 索引）。
4. 对 **his_config_info** 执行 `ALTER TABLE` 增加 `publish_type`、`gray_name`、`ext_info` 三列（类型与默认值与上表一致）。

新环境直接使用各模块下更新后的 `.sql` 初始化即可。

---

## 八、扩展组件内 SQL 与 3.1.1 对齐

除建表脚本外，各数据库扩展的 Mapper 实现中若**手写 SQL**（如 `HistoryConfigInfoMapper`），需与 Nacos 3.1.1 的 `his_config_info` 表字段一致，否则会出现列缺失或查询/删除语义错误。以下为本次对齐的修改。

### 8.1 涉及接口与表

- **HistoryConfigInfoMapper**：操作表 `his_config_info`。3.1.1 中该表新增列 `publish_type`、`gray_name`、`ext_info`，且 `findDeletedConfig` 的 WHERE 条件需带 `publish_type = ?`。

### 8.2 已修改的扩展实现（SQL 与 3.1.1 一致）

| 模块 | 文件 | 修改内容 |
|------|------|----------|
| nacos-oracle-datasource-plugin-ext | `HistoryConfigInfoMapperOracle.java` | `pageFindConfigHistoryFetchRows`、`findConfigHistoryFetchRows` 的 SELECT 增加 `ext_info,publish_type,gray_name` |
| nacos-yashan-datasource-plugin-ext | `HistoryConfigInfoMapperYaShan.java` | 同上 |
| nacos-oceanbase-datasource-plugin-ext | `HistoryConfigInfoMapperOceanbase.java` | 同上 |
| nacos-mssql-datasource-plugin-ext | `HistoryConfigInfoMapperBySqlServer.java` | `pageFindConfigHistoryFetchRows`：SELECT 增加 `ext_info,publish_type,gray_name`，分页改为 `OFFSET x ROWS FETCH NEXT y ROWS ONLY`；`findDeletedConfig`：按接口改为完整 SELECT（含 id,nid,content 等）且 WHERE 增加 `publish_type = ?`，参数顺序与接口一致；`removeConfigHistory`：改为按 `nid` 子查询删除，语义与 3.1.1 一致 |
| nacos-postgresql-datasource-plugin-ext | `HistoryConfigInfoMapperByPostgresql.java` | `removeConfigHistory`：CTE 由按 `id` 改为按 `nid` 选取待删行，再按 `nid` 删除，与 3.1.1 语义一致 |

### 8.3 未改动的扩展（已符合 3.1.1）

- **nacos-postgresql / nacos-kingbase / nacos-dm**：继承 Nacos 官方的 `HistoryConfigInfoMapperByMySql`，仅重写 `removeConfigHistory` 或 `getDataSource()`，`pageFindConfigHistoryFetchRows` 等已使用 3.1.1 的列（含 `ext_info,publish_type,gray_name`），无需改 SQL。
- **nacos-opengauss**：`OpenGaussHistoryConfigInfoMapper` 中 `pageFindConfigHistoryFetchRows` 已包含 `ext_info,publish_type,gray_name`，无需改动。

后续若 Nacos 再调整表结构或 Mapper 默认 SQL，扩展中所有手写 SQL 需同步核对并更新。

---

## 九、Nacos 3.1.1 兼容性检查清单

以下检查项用于确认当前扩展已满足 Nacos 3.1.1 使用要求。

### 9.1 Mapper 接口与注册

| 检查项 | 状态 | 说明 |
|--------|------|------|
| 无 ConfigInfoAggrMapper 残留 | ✅ | 3.1.1 已移除该接口；各扩展已删除 Aggr 实现类及 META-INF 注册 |
| 已注册 ConfigInfoGrayMapper | ✅ | PostgreSQL、Oracle、DM、MSSQL、Kingbase、Oceanbase、Yashan、OpenGauss 均已注册 |
| 已注册 ConfigMigrateMapper | ✅ | 同上 |
| Mapper 数量与官方一致 | ✅ | 各扩展 META-INF 均包含 10 个 Mapper（ConfigInfo、ConfigInfoBeta、ConfigInfoTag、ConfigTagsRelation、ConfigInfoGray、ConfigMigrate、HistoryConfigInfo、TenantInfo、TenantCapacity、GroupCapacity） |

### 9.2 表与 SQL 对齐

| 检查项 | 状态 | 说明 |
|--------|------|------|
| 无 config_info_aggr 表/引用 | ✅ | 建表脚本与 Java 代码中均已移除 |
| config_info_gray 表与列 | ✅ | 各库 schema 已包含 config_info_gray；BaseConfigInfoGrayMapper 及各库 Gray/Migrate 实现使用 config_info_gray |
| his_config_info 含 publish_type、gray_name、ext_info | ✅ | 各库 schema 已增加三列；HistoryConfigInfoMapper 相关 SQL（含 findDeletedConfig、pageFindConfigHistoryFetchRows、findConfigHistoryFetchRows、detailPreviousConfigHistory、getNextHistoryInfo）已按 3.1.1 列与条件更新 |

### 9.3 各扩展 Mapper 与 SQL 核对结果

| 扩展 | META-INF | HistoryConfigInfo SQL | 其他手写 SQL | 备注 |
|------|----------|------------------------|--------------|------|
| PostgreSQL | ✅ 10 个，含 Gray/Migrate | ✅ 继承 MySQL，removeConfigHistory 已改为按 nid | 继承/重写合理 | 已对齐 |
| Oracle | ✅ 10 个 | ✅ 已补全 publish_type、gray_name、ext_info | TenantCapacity/GroupCapacity 等已含 select | 已对齐 |
| DM | ✅ 10 个 | ✅ 继承 MySQL | 继承 MySQL | 已对齐 |
| MSSQL | ✅ 10 个 | ✅ pageFind/findDeletedConfig/removeConfigHistory 已按 3.1.1 修正 | 已对齐 | 已对齐 |
| Kingbase | ✅ 10 个 | ✅ 继承 MySQL | 继承 MySQL | 已对齐 |
| Oceanbase | ✅ 10 个 | ✅ 已补全三列 | TenantCapacity 等已含 select；README 已更新为 3.1.1 | 已对齐 |
| Yashan | ✅ 10 个 | ✅ 已补全三列 | 已对齐 | 已对齐 |
| OpenGauss | ✅ 10 个 | ✅ 已含 ext_info、publish_type、gray_name | 已对齐 | 已对齐 |

### 9.4 文档与杂项

| 检查项 | 状态 |
|--------|------|
| Oceanbase README 去除 ConfigInfoAggr、版本改为 3.1.1 | ✅ 已更新 |
| 根 pom 与各子模块 Nacos 版本 3.1.1、JDK 17 | ✅ 已升级 |
| 加密/Config/Environment/Trace 插件依赖与 import | ✅ 已适配 |

**结论**：当前组件扩展已满足 Nacos 3.1.1 使用要求，包括 Mapper 注册、建表脚本与各 Mapper 中 SQL 与 3.1.1 表结构及接口一致。建议在目标环境执行一次完整编译与冒烟测试（含实际数据库）以进一步验证。

---

以上为本次 2.4.3 → 3.1.1 升级的全部修改说明；若后续 Nacos 或 JDK 再升级，可在此文档基础上追加章节或另建新文档。
