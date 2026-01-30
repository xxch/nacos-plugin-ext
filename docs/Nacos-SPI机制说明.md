# Nacos 扩展组件与 SPI 机制说明

## 一、什么是 SPI

**SPI（Service Provider Interface）** 是 Java 内置的一种**服务发现与加载机制**，用来实现**接口与实现解耦**、**可插拔扩展**。

- **接口**由框架/核心库定义（如 Nacos 定义 `Mapper`、`DatabaseDialect`）。
- **实现**由扩展方提供（如你的达梦插件提供 `ConfigInfoGrayMapperByDaMeng`、`DaMengDatabaseDialect`）。
- 框架在**运行时**通过约定好的目录和文件名，**自动发现并加载**所有实现类，无需在核心代码里写死具体实现。

也就是说：**你只要按约定提供接口实现 + 配置文件，放到 classpath，Nacos 就能自动找到并用到你的扩展。**

---

## 二、Java 原生 SPI 的约定

JDK 自带的 `java.util.ServiceLoader` 规定：

1. **接口**：例如 `com.alibaba.nacos.plugin.datasource.mapper.Mapper`。
2. **配置文件位置与命名**（固定）：
   - 路径：`META-INF/services/`
   - 文件名：**接口的全限定类名**（例如 `com.alibaba.nacos.plugin.datasource.mapper.Mapper`）。
3. **文件内容**：每行一个**实现类的全限定类名**，注释用 `#`。

```
# 注释行
com.alibaba.nacos.plugin.datasource.impl.dm.ConfigInfoGrayMapperByDaMeng
com.alibaba.nacos.plugin.datasource.impl.dm.ConfigInfoMapperByDaMeng
...
```

4. **加载方式**：  
   `ServiceLoader.load(Mapper.class)` 会扫描 classpath 下所有 jar 里 `META-INF/services/com.alibaba.nacos.plugin.datasource.mapper.Mapper`，逐行读取类名，反射实例化，并返回一个 `Iterator<Mapper>`。

这样，**只要把包含上述配置和实现类的 jar 放到 Nacos 的 classpath**，无需改 Nacos 源码，就能“注册”新的 Mapper 实现。

---

## 三、Nacos 的封装：NacosServiceLoader

Nacos 没有改 SPI 的“约定”，而是在 **nacos-common** 里对 JDK 的 `ServiceLoader` 做了一层封装：`NacosServiceLoader`。

作用大致是：

- 仍然用 `ServiceLoader.load(接口.class)` 扫描 `META-INF/services/接口全限定名`。
- 把加载到的实现类**缓存**起来，避免重复扫描。
- 提供 `load(Class)` 返回 `Collection`，以及 `newServiceInstances(Class)` 按缓存再创建一批新实例。

也就是说：**SPI 的“接口 + 配置文件 + 实现类”这一套完全没变，只是多了一层缓存和集合式 API。**

你只要按 Java SPI 的约定写配置和实现类，Nacos 用 `NacosServiceLoader.load(XXX.class)` 就能把你的扩展都加载进来。

---

## 四、Nacos 里 SPI 用在哪：两类例子

### 1. 数据源方言：DatabaseDialect

- **接口**：`com.alibaba.nacos.plugin.datasource.dialect.DatabaseDialect`
- **加载代码**：在 **nacos-datasource-plugin-ext-base** 的 `DatabaseDialectManager` 里：

```java
// DatabaseDialectManager.java
Collection<DatabaseDialect> dialectList = NacosServiceLoader.load(DatabaseDialect.class);
for (DatabaseDialect dialect : dialectList) {
    SUPPORT_DIALECT_MAP.put(dialect.getType(), dialect);
}
```

- **扩展方式**：  
  达梦插件在  
  `src/main/resources/META-INF/services/com.alibaba.nacos.plugin.datasource.dialect.DatabaseDialect`  
  中写一行：  
  `com.alibaba.nacos.plugin.datasource.dialect.DaMengDatabaseDialect`  
  并实现 `DaMengDatabaseDialect`。  
  打成的 jar 被 Nacos 加载后，`NacosServiceLoader.load(DatabaseDialect.class)` 就会多出一个实现，`getType()` 为 DM，从而支持达梦方言。

### 2. 数据表 Mapper：Mapper

- **接口**：`com.alibaba.nacos.plugin.datasource.mapper.Mapper`
- **加载代码**：在 Nacos 核心 **plugin/datasource** 的 `MapperManager` 里：

```java
// MapperManager.java
Collection<Mapper> mappers = NacosServiceLoader.load(Mapper.class);
for (Mapper mapper : mappers) {
    putMapper(mapper);  // 按 dataSource + tableName 放进 MAPPER_SPI_MAP
}
```

- **扩展方式**：  
  达梦插件在  
  `src/main/resources/META-INF/services/com.alibaba.nacos.plugin.datasource.mapper.Mapper`  
  中列出所有达梦的 Mapper 实现类，例如：

```
com.alibaba.nacos.plugin.datasource.impl.dm.ConfigInfoGrayMapperByDaMeng
com.alibaba.nacos.plugin.datasource.impl.dm.ConfigInfoMapperByDaMeng
...
```

  每个实现类实现 `getDataSource()`（返回如 `"dm"`）和 `getTableName()`，以及对应 SQL 方法。  
  Nacos 启动时通过 `NacosServiceLoader.load(Mapper.class)` 扫描到这些类，实例化后按 `dataSource + tableName` 注册，运行时根据当前数据源类型和表名取用。

---

## 五、整体流程小结（便于理解）

1. **你（扩展方）**  
   - 实现 Nacos 定义的接口（如 `Mapper`、`DatabaseDialect`）。  
   - 在 `META-INF/services/` 下以**接口全限定名**为文件名，写上**实现类全限定名**（每行一个）。  
   - 打成 jar，放到 Nacos 的 classpath（如 `plugin` 目录或依赖中）。

2. **Nacos（框架方）**  
   - 调用 `NacosServiceLoader.load(接口.class)`。  
   - 底层用 JDK `ServiceLoader` 扫描所有 jar 中的 `META-INF/services/接口全限定名` 文件。  
   - 按文件中的类名反射实例化，得到所有实现；  
   - 再按业务需要放进 Map（如按 `getType()`、`getDataSource()+getTableName()`），供运行时使用。

3. **SPI 机制带来的效果**  
   - 核心只依赖“接口”，不依赖具体实现。  
   - 新增/替换数据库支持只需加一个实现了接口的 jar 和一份 `META-INF/services` 配置，无需改 Nacos 源码。  
   - 这就是你说的“Nacos 可以扩展组件是基于 SPI 机制实现的”的具体做法。

---

## 六、和你项目对应的文件一览

| 作用           | 接口全限定名 | 你项目中的 SPI 配置文件 |
|----------------|--------------|--------------------------|
| 数据库方言     | `com.alibaba.nacos.plugin.datasource.dialect.DatabaseDialect` | `nacos-dm-datasource-plugin-ext/src/main/resources/META-INF/services/com.alibaba.nacos.plugin.datasource.dialect.DatabaseDialect` |
| 表 Mapper 实现 | `com.alibaba.nacos.plugin.datasource.mapper.Mapper` | `nacos-dm-datasource-plugin-ext/src/main/resources/META-INF/services/com.alibaba.nacos.plugin.datasource.mapper.Mapper` |

理解“**接口由 Nacos 定，实现和 META-INF/services 由你写，加载用 NacosServiceLoader（底层 JDK ServiceLoader）**”，就掌握了 Nacos 用 SPI 做扩展的方式。
