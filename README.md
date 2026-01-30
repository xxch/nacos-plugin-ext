# nacos-plugin
A collection of Nacos plug-ins that provide pluggable plug-in capabilities for Nacos and support user customization and high scalability

![](https://tva1.sinaimg.cn/large/008i3skNly1gxmnilyukqj30qp0fgglx.jpg)

## 版本说明

- **当前基线**: Nacos **3.1.1**，JDK **17**
- 本工程已从 Nacos 2.4.3 升级至 3.1.1，主要变更：
  - **datasource 插件**：移除 `ConfigInfoAggrMapper` 及 `config_info_aggr` 相关实现；新增 `ConfigInfoGrayMapper`、`ConfigMigrateMapper` 及对应各数据库实现（PostgreSQL、Oracle、DM、MSSQL、Kingbase、Oceanbase、Yashan）；OpenGauss 沿用原有 Gray/Migrate 实现。
  - **GroupCapacityMapper / TenantCapacityMapper**：3.1.1 接口新增方法已在各扩展中通过继承 Base 或补充实现完成适配（如 Oracle、Oceanbase、Yashan 等）。
  - **config 插件**：WebHook 使用 `org.apache.hc.core5.http.HttpStatus`（HttpClient 5）；Whitelist 移除对 `org.apache.http.entity.ContentType` 的依赖。
  - **encryption 插件**：增加 `commons-codec` 依赖以满足 AES 插件编译。
