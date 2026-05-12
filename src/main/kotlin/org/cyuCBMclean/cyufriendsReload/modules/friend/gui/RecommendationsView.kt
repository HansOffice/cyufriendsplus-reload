package org.cyuCBMclean.cyufriendsReload.modules.friend.gui

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.cyuCBMclean.cyufriendsReload.core.scheduler.CyuConcurrency
import org.cyuCBMclean.cyufriendsReload.extension.sendLang
import org.cyuCBMclean.cyufriendsReload.extension.uid
import org.cyuCBMclean.cyufriendsReload.integration.hook.CyuIdHook
import org.cyuCBMclean.cyufriendsReload.modules.friend.FriendModule
import org.cyuCBMclean.cyufriendsReload.modules.friend.FriendRecommendation
import org.cyuCBMclean.cyufriendsReload.ui.action.ActionRegistry
import org.cyuCBMclean.cyufriendsReload.ui.action.CyuClickType
import org.cyuCBMclean.cyufriendsReload.ui.compat.GuiHeads
import org.cyuCBMclean.cyufriendsReload.ui.layout.GuiPattern
import org.cyuCBMclean.cyufriendsReload.ui.layout.ItemTemplate
import org.cyuCBMclean.cyufriendsReload.ui.view.PaginatedView
import java.text.SimpleDateFormat
import java.util.Date

class RecommendationsView(
    player: Player,
    pattern: GuiPattern,
    private val itemsMap: Map<Char, ItemTemplate>,
    private val module: FriendModule,
    title: String = "Friend Recommendations"
) : PaginatedView<FriendRecommendation>(player, title, pattern, itemsMap, 'R', 'P', 'N') {

    private val timeFormat = SimpleDateFormat("MM-dd HH:mm")
    private var cachedRecommendations: List<FriendRecommendation> = emptyList()

    override fun getSource(): List<FriendRecommendation> {
        return module.friendManager.recommendationsStoredSync(player.uid, 56).also { cachedRecommendations = it }
    }

    override fun viewReplacements(): Map<String, String> {
        return mapOf(
            "%page%" to page.toString(),
            "%total_pages%" to totalPagesFor(cachedRecommendations.size).toString(),
            "%entry_count%" to cachedRecommendations.size.toString(),
            "%recommend_count%" to cachedRecommendations.size.toString(),
            "%recommend_hint%" to if (cachedRecommendations.isEmpty()) {
                "暂无推荐，更多好友互动后会自动出现"
            } else {
                "Shift 右键暂不推荐 / 双击永久忽略"
            }
        )
    }

    override fun mapElement(element: FriendRecommendation): ItemStack {
        val template = itemsMap['R'] ?: return ItemStack(Material.PLAYER_HEAD)
        val replacements = replacements(element)
        val baseItem = template.render(player, replacements).clone()
        return if (template.hasHeadSource()) baseItem else GuiHeads.applyForUid(baseItem, element.candidateUid, player)
    }

    override fun onElementClick(element: FriendRecommendation, clickType: CyuClickType) {
        val replacements = replacements(element)
        when (clickType) {
            CyuClickType.SHIFT_RIGHT -> {
                val days = module.plugin.config.getLong("recommendation.snooze-days", 14L).coerceAtLeast(1L)
                val expiresAt = System.currentTimeMillis() + days * 24L * 60L * 60L * 1000L
                val ownerUid = player.uid
                val targetName = replacements["%candidate_name%"] ?: "该玩家"
                CyuConcurrency.scheduler.runAsync(module.plugin) {
                    module.friendManager.ignoreRecommendationSync(ownerUid, element.candidateUid, expiresAt)
                    CyuConcurrency.scheduler.runEntity(module.plugin, player) {
                        player.sendLang("recommend-snoozed", mapOf("target" to targetName, "days" to days.toString()))
                        onRender()
                    }
                }
            }
            CyuClickType.DOUBLE_CLICK -> {
                val ownerUid = player.uid
                val targetName = replacements["%candidate_name%"] ?: "该玩家"
                CyuConcurrency.scheduler.runAsync(module.plugin) {
                    module.friendManager.ignoreRecommendationSync(ownerUid, element.candidateUid, 0L)
                    CyuConcurrency.scheduler.runEntity(module.plugin, player) {
                        player.sendLang("recommend-hidden", mapOf("target" to targetName))
                        onRender()
                    }
                }
            }
            else -> {
                val template = itemsMap['R'] ?: return
                val nodes = template.actions[clickType] ?: template.actions[CyuClickType.ALL] ?: return
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
        }
    }

    private fun replacements(entry: FriendRecommendation): Map<String, String> {
        val candidateName = CyuIdHook.getName(entry.candidateUid) ?: "未知玩家"
        val mutualUids = module.friendManager.mutualFriendUidsStoredSync(player.uid, entry.candidateUid)
        val mutualPreview = mutualUids.take(3)
            .map { CyuIdHook.getName(it) ?: it }
            .joinToString("、")
            .ifBlank { "暂无共同好友" }
        val requestState = when {
            module.requestManager.hasRequestStable(player.uid, entry.candidateUid) -> "已发送申请"
            module.requestManager.hasRequestStable(entry.candidateUid, player.uid) -> "对方已向你申请"
            else -> "可直接发起申请"
        }
        val sharedAt = entry.latestSharedInteractionAt
            .takeIf { it > 0L }
            ?.let { timeFormat.format(Date(it)) }
            ?: "暂无记录"

        return mapOf(
            "%candidate_name%" to candidateName,
            "%candidate_uid%" to entry.candidateUid,
            "%mutual_count%" to entry.mutualCount.toString(),
            "%mutual_preview%" to mutualPreview,
            "%request_state%" to requestState,
            "%shared_interaction%" to sharedAt,
            "%recommend_reason%" to recommendationReason(entry),
            "%recommend_hint%" to "Shift 右键暂不推荐 / 双击永久忽略"
        )
    }

    private fun recommendationReason(entry: FriendRecommendation): String {
        return when {
            entry.mutualCount >= 4 -> "你们共同认识很多人"
            entry.mutualCount == 3 -> "你们有 3 位共同好友"
            entry.mutualCount == 2 -> "你们有 2 位共同好友"
            else -> "你们至少有 1 位共同好友"
        }
    }
}
