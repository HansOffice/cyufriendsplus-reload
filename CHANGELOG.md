# Changelog

## 1.1.4

- 修订版本号，功能与 1.1.3 保持一致

## 1.1.3

- CyuID UID 写入完成后统一发布变更事件，好友、亲密度与代理同步不再依赖单一管理命令
- 跨服代理启用时校验好友本体与已安装 CyuID 的共享 MySQL 存储，阻止本地 SQLite 分裂身份数据
- 更新 CyuID companion 依赖到 1.0.4
- 修复好友资料菜单首次头像异步刷新后传送按钮不执行的问题，重绘后继续保留菜单动作

## 1.1.2

- 修复 `/friend reload` 未完整刷新进服摘要、语言、声音和 GUI 的问题；配置校验失败时继续保留旧运行时
- 拒绝将 Citizens NPC 添加为好友，并补齐黑名单公共聊天过滤的管理员绕过权限
- 修复好友申请的并发、冷却和每日限额持久化，以及传送请求覆盖和关系写入的事务一致性问题
- 私聊统一收归 `/friend msg`、`/friend reply` 和 `/friend messages`，不再占用服务器已有的 `/msg`、`/reply`、`/messages` 命令
- 调整好友菜单为蓝色玻璃边框与头像展示，分页保留箭头；菜单配置不再混用 `rows` 与 `layout`
- 将 GUI 与 PlaceholderAPI 的同步数据库读取迁出 Folia 玩家线程，并补齐 Paper / Folia 分离构建校验
- 更新跨服后端的消息防重放与签名比较；配套代理端继续作为私有插件单独发布

## 1.0.8

- 修复 GUI 在线玩家头像在 Folia 区域线程里同步调用 SkinsRestorer 的风险
- SkinsRestorer 头像改为异步预热，菜单首次打开先显示可用缓存或默认头像，解析完成后自动刷新当前菜单
- 保留 Paper / Folia 双构建口径，避免头像缓存未命中时触发区域线程 Watchdog

## 1.0.7

- 分页禁用态按钮改为完整 GUI 按钮模板，支持 CraftEngine、ItemsAdder、Oraxen、Nexo 和资源包 CustomModelData
- 优化 CraftEngine 菜单图标桥接，首次绑定后复用方法缓存，减少菜单渲染时的反射开销
- 更新 GUI 速查说明，补充分页禁用态按钮的自定义入口

## 1.0.5

- 拆分 Paper 与 Folia 构建目标，发布包名称明确区分运行平台
- Paper 包不再声明 Folia 支持，Folia 包单独写入 `folia-supported: true`
- Folia 调度实现改为平台源码目录内的原生实现，不再和 Paper 包混在一起
- 保留 `paper-legacy-all` 作为完整依赖附加包，默认发布包继续走 `plugin.yml libraries`

## 1.0

CyuFriends Reload 首个公开版本

- 重构好友、分组、私聊、动态、留言墙、个人资料等核心模块
- 新增数据驱动 GUI，可通过 `gui/*.yml` 定制菜单布局与点击动作
- 新增动态墙、留言墙、生日提醒、好友标签颜色、关系时间线等社交功能
- 新增 PlaceholderAPI 变量与附属插件 API jar
- 支持 SQLite / MySQL
- 补齐 Folia 兼容调度封装
- 默认按单服发布，跨服代理能力暂不作为 1.0 发布内容
