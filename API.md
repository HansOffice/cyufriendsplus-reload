# cyufriends-reload API

这份 API 面向附属插件。原则是：能从这里拿到的，就不要去碰 `modules.*` 里的内部 manager。

## API Jar

`cyufriends-reload` 打包时会额外生成一份轻量 API：

- `target/cyufriends-reload-1.0-api.jar`

这份 jar 只包含：

- `org.cyuCBMclean.cyufriendsReload.api.*`
- `CyuFriendsService` 服务接口
- 快照 DTO
- `ApiResult`
- 公开事件

不会包含本体实现、数据库、GUI、模块 manager。其他插件开发时应该依赖这份 API jar，而不是把完整插件 jar 当编译依赖。

本地 Maven 安装示例：

```bash
mvn install:install-file \
  -Dfile=target/cyufriends-reload-1.0-api.jar \
  -DgroupId=org.cyuCBMclean \
  -DartifactId=cyufriends-reload-api \
  -Dversion=1.0 \
  -Dpackaging=jar
```

依赖示例：

```xml
<dependency>
    <groupId>org.cyuCBMclean</groupId>
    <artifactId>cyufriends-reload-api</artifactId>
    <version>1.0</version>
    <scope>provided</scope>
</dependency>
```

如果后续把 API 单独发布到 Maven 仓库，附属插件只需要依赖 API 坐标，运行时由服务器上的 `cyufriends-reload` 本体提供服务。

## 获取服务

```kotlin
import org.bukkit.Bukkit
import org.cyuCBMclean.cyufriendsReload.api.CyuFriendsApi
import org.cyuCBMclean.cyufriendsReload.api.service.CyuFriendsService

val service = CyuFriendsApi.service()
    ?: Bukkit.getServicesManager().load(CyuFriendsService::class.java)
    ?: return
```

`plugin.yml` 建议写：

```yaml
softdepend: [cyufriends-reload]
```

如果你的插件没有好友系统就不能工作，用 `depend`。

## 基础查询

```kotlin
val uid = service.uidByName("Steve")
val name = service.nameByUid("10001")
val friendModuleOn = service.isModuleEnabled("friend")

val areFriends = service.areFriends("10001", "10002")
val friends = service.friendsOf("10001")
val groups = service.groupedFriends("10001")
val mutual = service.mutualFriends("10001", "10002")
val recommends = service.recommendations("10001", 10)
```

## 快照

```kotlin
val friend = service.friendSnapshot("10001", "10002")
val profile = service.profile("10001")
val latestStatus = service.latestStatus("10001", "10002")
val wall = service.wall("10001", "10002", includePending = false, limit = 20)
val conversations = service.conversationSummaries("10001", 20)
val unread = service.unreadMessages("10001")
```

快照都是只读数据，不会暴露内部可变对象。

## 好友写入

```kotlin
val request = service.sendFriendRequest("10001", "10002", "一起玩吗")
val accepted = service.acceptFriendRequest(receiverUid = "10002", senderUid = "10001")
val removed = service.removeFriendship("10001", "10002")

if (!request.success) {
    logger.info("发送失败: ${request.code}")
}
```

可用入口：

- `createFriendship(firstUid, secondUid)`
- `removeFriendship(firstUid, secondUid)`
- `sendFriendRequest(senderUid, receiverUid, note)`
- `acceptFriendRequest(receiverUid, senderUid)`
- `denyFriendRequest(receiverUid, senderUid)`
- `revokeFriendRequest(senderUid, receiverUid)`
- `blockUser(ownerUid, targetUid)`
- `unblockUser(ownerUid, targetUid)`

这些入口会刷新缓存，并触发现有好友事件。

## 好友资料写入

```kotlin
service.setFriendNote("10001", "10002", "建筑师")
service.setFriendGroup("10001", "10002", "生存伙伴")
service.addFriendTag("10001", "10002", "仓库")
service.setFriendTagColor("10001", "10002", "仓库", "#5EC8FF")
service.setFriendPinned("10001", "10002", true)
```

可用入口：

- `setFriendNote`
- `setFriendNoteDetail`
- `setFriendGroup`
- `setFriendPinned`
- `addFriendTag`
- `removeFriendTag`
- `clearFriendTags`
- `setPrimaryFriendTag`
- `setFriendTagColor`
- `clearFriendTagColor`

## 私聊与社交写入

```kotlin
service.sendPrivateMessage("10001", "10002", "仓库已到货", markRead = false)
service.clearUnreadMessages("10002", senderUid = "10001")
```

发布动态和留言墙需要传入 `Player`，因为冷却和数量档位可能依赖玩家权限。

```kotlin
service.publishStatus(player, playerUid, "今天开新仓库", "PUBLIC")
service.postWall(player, ownerUid = "10002", authorUid = playerUid, content = "我放了一组石头", visibility = "FRIENDS")
```

可见性支持：

- `PUBLIC`
- `FRIENDS`
- `PRIVATE`

## 异步入口

低频联动直接用同步方法就行。批量同步、网页面板、跨插件后台任务建议用异步入口：

```kotlin
service.sendFriendRequestAsync("10001", "10002", "一起玩吗")
    .thenAccept { result ->
        logger.info("result=${result.code}")
    }

service.friendsOfAsync("10001").thenAccept { friends ->
    logger.info("friends=${friends.size}")
}
```

目前提供异步包装：

- `friendsOfAsync`
- `createFriendshipAsync`
- `removeFriendshipAsync`
- `sendFriendRequestAsync`
- `acceptFriendRequestAsync`
- `sendPrivateMessageAsync`
- `publishStatusAsync`
- `postWallAsync`
- `rebuildCachesAsync`

## 结果码

写入方法返回 `ApiResult`：

```kotlin
val result = service.createFriendship("10001", "10002")

when (result.code) {
    ApiResultCode.SUCCESS -> {}
    ApiResultCode.ALREADY_FRIENDS -> {}
    ApiResultCode.MODULE_DISABLED -> {}
    else -> logger.warning("CyuFriends API failed: ${result.code} ${result.message}")
}
```

常见结果：

- `SUCCESS`
- `PENDING`：例如留言墙开启审核后，内容已进入待审
- `MODULE_DISABLED`
- `INVALID_ARGUMENT`
- `SAME_PLAYER`
- `ALREADY_FRIENDS`
- `NOT_FRIENDS`
- `BLOCKED`
- `REQUEST_EXISTS`
- `REQUEST_NOT_FOUND`
- `EMPTY_CONTENT`
- `COOLDOWN`
- `LIMIT_REACHED`
- `FORBIDDEN`
- `NOT_FOUND`

## 事件

```kotlin
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.cyuCBMclean.cyufriendsReload.api.event.CyuFriendRequestSendEvent
import org.cyuCBMclean.cyufriendsReload.api.event.CyuFriendRequestAcceptEvent
import org.cyuCBMclean.cyufriendsReload.api.event.CyuFriendRequestDenyEvent
import org.cyuCBMclean.cyufriendsReload.api.event.CyuFriendRequestRevokeEvent
import org.cyuCBMclean.cyufriendsReload.api.event.CyuFriendshipCreateEvent
import org.cyuCBMclean.cyufriendsReload.api.event.CyuFriendshipRemoveEvent
import org.cyuCBMclean.cyufriendsReload.api.event.CyuFriendMetaUpdateEvent
import org.cyuCBMclean.cyufriendsReload.api.event.CyuProfileUpdateEvent
import org.cyuCBMclean.cyufriendsReload.api.event.CyuPrivateMessageSendEvent
import org.cyuCBMclean.cyufriendsReload.api.event.CyuStatusCommentEvent
import org.cyuCBMclean.cyufriendsReload.api.event.CyuStatusPublishEvent
import org.cyuCBMclean.cyufriendsReload.api.event.CyuStatusReactionEvent
import org.cyuCBMclean.cyufriendsReload.api.event.CyuWallPostEvent

class FriendsApiListener : Listener {
    @EventHandler
    fun onCreate(event: CyuFriendshipCreateEvent) {
        logger.info("${event.firstUid} + ${event.secondUid}")
    }
}
```

可监听的常用事件：

- `CyuFriendRequestSendEvent`
- `CyuFriendRequestAcceptEvent`
- `CyuFriendRequestDenyEvent`
- `CyuFriendRequestRevokeEvent`
- `CyuFriendshipCreateEvent`
- `CyuFriendshipRemoveEvent`
- `CyuFriendMetaUpdateEvent`
- `CyuPrivateMessageSendEvent`
- `CyuProfileUpdateEvent`
- `CyuStatusPublishEvent`
- `CyuStatusReactionEvent`
- `CyuStatusCommentEvent`
- `CyuWallPostEvent`

事件可能在异步线程触发。监听里如果要操作 Bukkit 实体、背包、世界，请切回主线程或对应 Folia 调度。

## 线程说明

当前 API 是同步 API。它适合命令、菜单点击、低频联动。

如果你要在大批量任务里调用，例如一次同步几千个玩家的数据，请自己放到异步任务里跑，不要卡主线程。

## UID 约定

所有 API 都用 `cyuid-reload` 的 UID 字符串作为稳定身份。名字只适合显示或临时解析，不建议作为长期存储键。
