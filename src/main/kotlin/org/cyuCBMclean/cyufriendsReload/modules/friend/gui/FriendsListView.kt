package org.cyuCBMclean.cyufriendsReload.modules.friend.gui

import kotlinx.coroutines.runBlocking
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.cyuCBMclean.cyufriendsReload.CyufriendsReload
import org.cyuCBMclean.cyufriendsReload.core.scheduler.CyuConcurrency
import org.cyuCBMclean.cyufriendsReload.extension.isPlayerOnlineGlobally
import org.cyuCBMclean.cyufriendsReload.extension.isRemoteOnline
import org.cyuCBMclean.cyufriendsReload.extension.onlineScope
import org.cyuCBMclean.cyufriendsReload.extension.onlineServerName
import org.cyuCBMclean.cyufriendsReload.extension.playAudio
import org.cyuCBMclean.cyufriendsReload.extension.resolvePlayerName
import org.cyuCBMclean.cyufriendsReload.extension.sendLang
import org.cyuCBMclean.cyufriendsReload.extension.uid
import org.cyuCBMclean.cyufriendsReload.integration.hook.CyuIdHook
import org.cyuCBMclean.cyufriendsReload.modules.friend.FriendDefaults
import org.cyuCBMclean.cyufriendsReload.modules.friend.FriendListSortMode
import org.cyuCBMclean.cyufriendsReload.modules.friend.FriendListState
import org.cyuCBMclean.cyufriendsReload.modules.friend.FriendModule
import org.cyuCBMclean.cyufriendsReload.modules.proxy.ProxyModule
import org.cyuCBMclean.cyufriendsReload.ui.action.ActionRegistry
import org.cyuCBMclean.cyufriendsReload.ui.action.CyuClickType
import org.cyuCBMclean.cyufriendsReload.ui.compat.GuiHeads
import org.cyuCBMclean.cyufriendsReload.ui.layout.GuiPattern
import org.cyuCBMclean.cyufriendsReload.ui.layout.ItemTemplate
import org.cyuCBMclean.cyufriendsReload.ui.view.PaginatedView
import java.text.SimpleDateFormat
import java.util.Date

class FriendsListView(
    player: Player,
    pattern: GuiPattern,
    private val itemsMap: Map<Char, ItemTemplate>,
    private val module: FriendModule,
    private val state: FriendListState = FriendListState(),
    title: String = "Friends List"
) : PaginatedView<String>(player, title, pattern, itemsMap, 'F', 'P', 'N') {

    private val timeFormat = SimpleDateFormat("yyyy-MM-dd")
    private var cachedSource: List<String> = emptyList()

    override fun getSource(): List<String> {
        return filteredFriendUids().also { cachedSource = it }
    }

    override fun viewReplacements(): Map<String, String> {
        val friendUids = cachedSource.ifEmpty { filteredFriendUids().also { cachedSource = it } }
        val onlineCount = friendUids.count { CyufriendsReload.instance.isPlayerOnlineGlobally(it) }
        val currentFilter = state.filterTag ?: "全部好友"
        val currentSearch = state.keyword ?: "未搜索"
        val focus = when {
            !state.keyword.isNullOrBlank() -> "搜索: ${state.keyword}"
            !state.filterTag.isNullOrBlank() -> "标签: ${state.filterTag}"
            else -> state.sortMode.displayName
        }
        return mapOf(
            "%filter_tag%" to currentFilter,
            "%filter_state%" to if (state.filterTag.isNullOrBlank()) "全部好友" else "标签筛选中",
            "%filter_count%" to friendUids.size.toString(),
            "%filter_tag_token%" to (state.filterTag ?: "__none__"),
            "%search_keyword%" to currentSearch,
            "%search_state%" to if (state.keyword.isNullOrBlank()) "未搜索" else "关键词搜索中",
            "%sort_mode%" to state.sortMode.displayName,
            "%sort_mode_id%" to state.sortMode.id,
            "%pending_request_count%" to module.requestManager.countReceivedSync(player.uid).toString(),
            "%friend_count%" to friendUids.size.toString(),
            "%friend_online_count%" to onlineCount.toString(),
            "%page%" to page.toString(),
            "%total_pages%" to totalPagesFor(friendUids.size).toString(),
            "%current_tab%" to focus,
            "%friend_list_focus%" to focus
        )
    }

    override fun mapElement(element: String): ItemStack {
        val template = itemsMap['F'] ?: return ItemStack(Material.PLAYER_HEAD)
        val replacements = replacements(element)
        val baseItem = template.render(player, replacements).clone()
        return if (template.hasHeadSource()) baseItem else GuiHeads.applyForUid(baseItem, element, player)
    }

    override fun onElementClick(element: String, clickType: CyuClickType) {
        val template = itemsMap['F'] ?: return
        if (clickType == CyuClickType.MIDDLE) {
            cyclePrimaryTag(element)
            return
        }
        val nodes = template.actions[clickType] ?: template.actions[CyuClickType.ALL] ?: return
        val replacements = replacements(element)
        ActionRegistry.execute(
            player,
            nodes.map { node ->
                node.copy(
                    payload = replacements.entries.fold(node.payload) { current, entry ->
                        current.replace(entry.key, entry.value)
                    }
                )
            }
        )
    }

    private fun filteredFriendUids(): List<String> {
        return module.friendManager
            .getFriendEntriesCached(player.uid)
            .asSequence()
            .filter { matchesTag(it) }
            .filter { matchesKeyword(it) }
            .sortedWith(sortComparator())
            .map { it.friendUid }
            .toList()
    }

    private fun matchesTag(data: org.cyuCBMclean.cyufriendsReload.modules.friend.FriendData): Boolean {
        val normalized = state.filterTag?.trim()?.takeIf { it.isNotEmpty() } ?: return true
        return data.tagNames.any { it.equals(normalized, ignoreCase = true) }
    }

    private fun matchesKeyword(data: org.cyuCBMclean.cyufriendsReload.modules.friend.FriendData): Boolean {
        val keyword = state.keyword?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return true
        return sequenceOf(
            CyuIdHook.getName(data.friendUid),
            data.noteName,
            data.noteDetail,
            data.groupName,
            data.primaryTag(),
            data.joinedTags(),
            data.friendUid
        )
            .filterNotNull()
            .map { it.lowercase() }
            .any { it.contains(keyword) }
    }

    private fun sortComparator(): Comparator<org.cyuCBMclean.cyufriendsReload.modules.friend.FriendData> {
        val plugin = CyufriendsReload.instance
        return when (state.sortMode) {
            FriendListSortMode.RECENT -> compareByDescending<org.cyuCBMclean.cyufriendsReload.modules.friend.FriendData> { it.pinned }
                .thenByDescending { isGroupPinned(it) }
                .thenByDescending { it.lastInteractionAt }
                .thenBy { displayNameKey(it) }
                .thenBy { it.friendUid.lowercase() }

            FriendListSortMode.ONLINE -> compareByDescending<org.cyuCBMclean.cyufriendsReload.modules.friend.FriendData> { it.pinned }
                .thenByDescending { isGroupPinned(it) }
                .thenByDescending { plugin.isPlayerOnlineGlobally(it.friendUid) }
                .thenBy { onlineBucket(it) }
                .thenByDescending { it.lastInteractionAt }
                .thenBy { displayNameKey(it) }
                .thenBy { it.friendUid.lowercase() }

            FriendListSortMode.SERVER -> compareByDescending<org.cyuCBMclean.cyufriendsReload.modules.friend.FriendData> { it.pinned }
                .thenByDescending { isGroupPinned(it) }
                .thenBy { onlineBucket(it) }
                .thenBy { plugin.onlineServerName(it.friendUid).lowercase() }
                .thenBy { displayNameKey(it) }
                .thenByDescending { it.lastInteractionAt }
                .thenBy { it.friendUid.lowercase() }

            FriendListSortMode.NAME -> compareByDescending<org.cyuCBMclean.cyufriendsReload.modules.friend.FriendData> { it.pinned }
                .thenByDescending { isGroupPinned(it) }
                .thenBy { displayNameKey(it) }
                .thenByDescending { it.lastInteractionAt }
                .thenBy { it.friendUid.lowercase() }
        }
    }

    private fun onlineBucket(data: org.cyuCBMclean.cyufriendsReload.modules.friend.FriendData): Int {
        val plugin = CyufriendsReload.instance
        return when {
            CyuIdHook.isOnlineLocally(data.friendUid) -> 0
            plugin.isRemoteOnline(data.friendUid) -> 1
            else -> 2
        }
    }

    private fun displayNameKey(data: org.cyuCBMclean.cyufriendsReload.modules.friend.FriendData): String {
        return (data.noteName ?: CyuIdHook.getName(data.friendUid) ?: data.friendUid).lowercase()
    }

    private fun replacements(friendUid: String): Map<String, String> {
        val rawName = CyufriendsReload.instance.resolvePlayerName(friendUid) ?: friendUid
        val friendData = module.friendManager.getFriendData(player.uid, friendUid)
        val displayName = friendData?.noteName ?: rawName
        val noteName = friendData?.noteName ?: "未设置"
        val noteDetail = friendData?.noteDetail ?: "未设置"
        val groupName = friendData?.groupName ?: FriendDefaults.DEFAULT_GROUP_NAME
        val primaryTag = friendData?.primaryTag() ?: "未设置"
        val tags = friendData?.joinedTags().takeUnless { it.isNullOrBlank() } ?: "未设置"
        val tagsMm = friendData?.joinedColoredTags().takeUnless { it.isNullOrBlank() } ?: "<gray>未设置</gray>"
        val primaryTagMm = friendData?.primaryColoredTag() ?: "<gray>未设置</gray>"
        val primaryTagColor = friendData?.primaryTagColor() ?: FriendDefaults.TAG_COLOR_PALETTE.first()
        val tagCount = friendData?.tagNames?.size?.toString() ?: "0"
        val pinState = if (friendData?.pinned == true) "已置顶" else "未置顶"
        val groupPinState = if (friendData != null && module.preferencesManager.isGroupPinnedCached(player.uid, friendData.groupName)) "分组置顶" else "普通显示"
        val friendSince = friendData?.createdAt?.takeIf { it > 0L }?.let { timeFormat.format(Date(it)) } ?: "未知时间"
        val lastInteraction = friendData?.lastInteractionAt?.takeIf { it > 0L }?.let { timeFormat.format(Date(it)) } ?: "暂无记录"

        return mapOf(
            "%friend_name%" to displayName,
            "%raw_name%" to rawName,
            "%note_name%" to noteName,
            "%note_detail%" to noteDetail,
            "%group_name%" to groupName,
            "%tag_name%" to tags,
            "%primary_tag%" to primaryTag,
            "%primary_tag_color%" to primaryTagColor,
            "%tag_color%" to primaryTagColor,
            "%primary_tag_mm%" to primaryTagMm,
            "%tags%" to tags,
            "%tags_mm%" to tagsMm,
            "%tag_count%" to tagCount,
            "%pin_state%" to pinState,
            "%group_pin_state%" to groupPinState,
            "%friend_since%" to friendSince,
            "%last_interaction%" to lastInteraction,
            "%server_name%" to CyufriendsReload.instance.onlineServerName(friendUid),
            "%online_scope%" to CyufriendsReload.instance.onlineScope(friendUid),
            "%uid%" to friendUid
        )
    }

    private fun isGroupPinned(data: org.cyuCBMclean.cyufriendsReload.modules.friend.FriendData): Boolean {
        return module.preferencesManager.isGroupPinnedCached(player.uid, data.groupName)
    }

    private fun cyclePrimaryTag(friendUid: String) {
        val friendData = module.friendManager.getFriendData(player.uid, friendUid) ?: return
        val orderedTags = friendData.orderedTags()
        val targetName = CyufriendsReload.instance.resolvePlayerName(friendUid) ?: friendUid
        if (orderedTags.size <= 1) {
            player.sendLang("tag-cycle-unavailable", mapOf("target" to targetName))
            return
        }
        val currentIndex = orderedTags.indexOfFirst { it.equals(friendData.primaryTag(), ignoreCase = true) }
            .takeIf { it >= 0 }
            ?: 0
        val nextTag = orderedTags[(currentIndex + 1) % orderedTags.size]
        val proxyGateway = CyufriendsReload.instance.moduleManager.getModule<ProxyModule>("proxy")?.gateway
        val ownerUid = player.uid

        CyuConcurrency.scheduler.runAsync(CyufriendsReload.instance) {
            val updated = runBlocking { module.friendManager.setPrimaryTag(ownerUid, friendUid, nextTag) }
            if (!updated) return@runAsync
            proxyGateway?.invalidateRelation(ownerUid)
            CyuConcurrency.scheduler.runEntity(CyufriendsReload.instance, player) {
                player.sendLang("tag-primary-set", mapOf("target" to targetName, "tag" to nextTag))
                player.playAudio("success")
                onRender()
            }
        }
    }
}
