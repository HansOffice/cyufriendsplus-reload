# Changelog

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
