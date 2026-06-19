# CyuFriends Reload

[![Build](https://github.com/HansOffice/cyufriendsplus-reload/actions/workflows/build.yml/badge.svg)](https://github.com/HansOffice/cyufriendsplus-reload/actions/workflows/build.yml)

你的世界，从朋友开始

作者：HansOffice

CyuFriends Reload 是一个面向 Minecraft 服务器的好友与轻社交插件。它保留了 CyuFriends 系列“让服务器里的关系更像一个社区”的方向，同时重新整理了模块、GUI、消息、缓存和 API，让插件更适合长期维护，也更适合开源协作

## 功能

- 好友申请、同意、拒绝、撤回、删除
- 黑名单、好友上线提醒、好友传送
- 好友分组、备注、详细备注、标签、标签颜色、置顶
- 私聊、快捷回复、离线留言、未读消息中心
- 个人资料、签名、生日、隐私设置
- 动态墙、评论、点赞、可见性控制
- 留言墙、回复、点赞、审核、置顶
- PlaceholderAPI 变量与附属插件 API
- SQLite / MySQL 数据存储
- Folia 兼容调度封装

## Reload 版有什么不同

Reload 不是旧版换名。它把原本堆在一起的好友系统拆成了更清楚的模块：

- `friend` 负责好友关系、申请、黑名单、传送与好友资料
- `group` 负责分组列表、分组成员和批量移动
- `chat` 负责私聊、回复和离线留言
- `social` 负责动态、留言墙、评论和审核
- `profile` 负责个人资料、生日和隐私设置
- `proxy` 后端代码保留但默认关闭；代理端插件已发布但不开源，开源仓库不把跨服能力作为默认卖点

配置、GUI、权限和 API 都尽量按服主能看懂、能改动的方式整理。你可以把它当成一个完整的好友系统，也可以把它当成服务器社交功能的底座

## 安装

1. 从 GitHub Releases 下载 `cyufriends-reload-1.0.4.jar`
2. 将 jar 放入服务端 `plugins/`
3. 推荐同时安装 `cyuid-reload`，让好友系统使用稳定 UID
4. 可选安装 PlaceholderAPI，用于计分板、菜单或聊天变量
5. 启动服务器，生成配置文件
6. 按需要修改 `plugins/cyufriends-reload/config.yml`、`messages.yml` 和 `gui/` 下的菜单文件

默认配置按单服发布准备。跨服相关配置保持关闭即可

## 下载

稳定版会放在 GitHub Releases 中。普通服主下载插件本体即可：

- `cyufriends-reload-1.0.4.jar`

附属插件开发者可以下载 API jar，或通过 GitHub Packages 引入：

- `cyufriends-reload-1.0.4-api.jar`

## GitHub Packages

Maven 仓库：

```xml
<repository>
    <id>github</id>
    <url>https://maven.pkg.github.com/HansOffice/cyufriendsplus-reload</url>
</repository>
```

API 依赖：

```xml
<dependency>
    <groupId>org.cyuCBMclean</groupId>
    <artifactId>cyufriends-reload</artifactId>
    <version>1.0.4</version>
    <classifier>api</classifier>
</dependency>
```

如果仓库还没有公开，拉取 GitHub Packages 时需要配置 GitHub token。仓库公开后，仍建议开发者按 GitHub Packages 的 Maven 认证方式配置，避免本地构建环境差异

## 常用命令

| 命令 | 用途 |
| --- | --- |
| `/friend` | 打开好友主页 |
| `/friend add <玩家>` | 发送好友申请 |
| `/friend requests` | 查看收到的申请 |
| `/friend group` | 管理好友分组 |
| `/friend tp <好友>` | 请求传送到好友 |
| `/msg <好友> <内容>` | 发送私聊 |
| `/reply <内容>` | 回复最近私聊 |
| `/messages` | 打开未读消息中心 |
| `/status` | 查看动态墙 |
| `/wall` | 查看留言墙 |
| `/settings` | 打开个人设置 |
| `/bio <内容>` | 设置个人签名 |
| `/birthday <MM-dd>` | 设置生日 |

## 权限

普通玩家默认拥有常用功能：

- `cyufriends.use`
- `cyufriends.use.friend`
- `cyufriends.use.group`
- `cyufriends.use.chat`
- `cyufriends.use.social`
- `cyufriends.use.profile`

管理员权限：

- `cyufriends.admin`

数量和冷却档位可以通过权限扩展，例如：

- `cyufriends.request.vip`
- `cyufriends.status.vip`
- `cyufriends.wall.vip`

完整节点见 `src/main/resources/Permissions.yml` 与 `plugin.yml`

## 配置与定制

你可以改这些文件来做自己的服务器风格：

- `config.yml`：数据库、模块开关、冷却、数量、审核、生日提醒
- `messages.yml`：聊天提示与富文本消息
- `Placeholder.yml`：PlaceholderAPI 变量显示文本
- `Permissions.yml`：权限说明
- `gui/*.yml`：菜单布局、按钮、材质、点击动作

GUI 菜单是数据驱动的，不需要重新编译插件就能调整大部分展示和入口

## 构建

```bash
mvn -DskipTests package
```

构建完成后：

- `target/cyufriends-reload-1.0.4.jar` 是插件本体
- `target/cyufriends-reload-1.0.4-api.jar` 是附属插件编译用 API

目前 `cyuid-reload` 仍作为本地 companion jar 参与编译，保留在 `libs/cyuid-reload-1.0.3.jar`。PlaceholderAPI 从 Maven 仓库解析

## API

附属插件请优先依赖 API jar，不要直接调用内部 `modules.*` manager

更多示例见 `API.md`

## 发布口径

CyuFriends Reload 1.0.4 默认仍按单服体验发布。跨服相关后端代码保留在项目中，但默认关闭；代理端插件已发布但不开源，因此不要把跨服能力作为当前开源版本的卖点或必备功能

## 开源协作

欢迎提交问题、建议和 PR。比较适合优先改的方向：

- 新 GUI 样式与菜单体验
- 更多 PlaceholderAPI 变量
- 更完整的数据迁移工具
- 面向服主的配置示例
- API 示例项目

贡献说明见 `CONTRIBUTING.md`

如果你只是想让玩家更愿意在服务器里认识彼此，这个项目就是为这件事做的
