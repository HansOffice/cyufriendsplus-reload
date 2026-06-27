package org.cyuCBMclean.cyufriendsReload.ui.layout

import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.cyuCBMclean.cyufriendsReload.ui.action.ActionNode
import org.cyuCBMclean.cyufriendsReload.ui.action.CyuClickType
import org.cyuCBMclean.cyufriendsReload.core.config.ColorCompat
import org.cyuCBMclean.cyufriendsReload.ui.compat.CraftEngineItems
import org.cyuCBMclean.cyufriendsReload.ui.compat.ExternalMenuItems
import org.cyuCBMclean.cyufriendsReload.ui.compat.GuiHeads

class ItemTemplate(private val section: ConfigurationSection) {

    companion object {
        private val miniMessage = MiniMessage.miniMessage()
    }

    private val materialStr = section.getString("material") ?: "STONE"
    private val headSource = section.getString("head") ?: section.getString("skullOwner")
    private val craftEngineKey = section.getString("craftengine") ?: section.getString("craft_engine") ?: section.getString("ce")
    private val defaultSound = section.getString("sound") ?: section.getString("click_sound")
    private val amount = section.getInt("amount", 1)
    private val customModelData = section.getInt(
        "custom_model_data",
        section.getInt("custom-model-data", section.getInt("model_data", 0))
    )
    private var staticCache: ItemStack? = null

    val actions = mutableMapOf<CyuClickType, List<ActionNode>>()

    init {
        section.getConfigurationSection("actions")?.let { actionSec ->
            actionSec.getKeys(false).forEach { clickKey ->
                val clickType = runCatching { CyuClickType.valueOf(clickKey.uppercase()) }.getOrDefault(CyuClickType.ALL)
                val rawActions = actionSec.getStringList(clickKey)
                actions[clickType] = rawActions.map { ActionNode.parse(it) }
            }
        }
    }

    fun render(player: Player): ItemStack {
        return render(player, emptyMap())
    }

    fun hasHeadSource(): Boolean {
        return !headSource.isNullOrBlank() || GuiHeads.isStaticSource(materialStr)
    }

    fun sound(clickType: CyuClickType): String? {
        return section.getString("sounds.${clickType.name.lowercase()}")
            ?: section.getString("sounds.${clickType.name}")
            ?: defaultSound
    }

    fun render(player: Player, replacements: Map<String, String>): ItemStack {
        val cacheable = replacements.isEmpty() && !isDynamic()
        if (cacheable) staticCache?.let { return it.clone() }

        val materialValue = replace(materialStr, player, replacements).trim()
        val textureFromMaterial = materialValue.takeIf { GuiHeads.isStaticSource(it) }
        val item = buildBaseItem(player, materialValue, textureFromMaterial, replacements)
        val meta = item.itemMeta ?: return item

        section.getString("name")?.let {
            val parsed = replace(it, player, replacements)
            val component = ColorCompat.parseMiniMessage(miniMessage, parsed)
            if (component == null || !ColorCompat.applyGuiDisplayName(meta, component)) {
                meta.setDisplayName(ColorCompat.renderGuiMiniMessage(miniMessage, parsed))
            }
        }

        section.getStringList("lore").takeIf { it.isNotEmpty() }?.let { list ->
            val parsedLore = list.map {
                val parsed = replace(it, player, replacements)
                parsed to ColorCompat.parseMiniMessage(miniMessage, parsed)
            }
            val components = parsedLore.mapNotNull { it.second }
            if (components.size != parsedLore.size || !ColorCompat.applyGuiLore(meta, components)) {
                meta.lore = parsedLore.map { ColorCompat.renderGuiMiniMessage(miniMessage, it.first) }
            }
        }

        applyCustomModelData(meta, customModelDataFromMaterial(materialValue))

        item.itemMeta = meta
        val headValue = headSource?.let { replace(it, player, replacements) } ?: textureFromMaterial
        val rendered = GuiHeads.apply(item, headValue, player)
        if (cacheable) staticCache = rendered.clone()
        return rendered
    }

    private fun buildBaseItem(player: Player, materialValue: String, textureFromMaterial: String?, replacements: Map<String, String>): ItemStack {
        val externalItem = ExternalMenuItems.build(materialValue)
        if (externalItem != null) {
            externalItem.amount = amount
            return externalItem
        }

        val materialCraftEngineKey = craftEngineKeyFromMaterial(materialValue)
        val ceItem = (craftEngineKey ?: materialCraftEngineKey)
            ?.let { replace(it, player, replacements) }
            ?.let { CraftEngineItems.build(it, player) }

        if (ceItem != null) {
            ceItem.amount = amount
            return ceItem
        }

        val materialName = when {
            textureFromMaterial != null -> "PLAYER_HEAD"
            materialCraftEngineKey != null || ExternalMenuItems.isExternal(materialValue) -> "STONE"
            else -> stripModelData(materialValue)
        }
        val mat = Material.matchMaterial(materialName.uppercase()) ?: Material.STONE
        return ItemStack(mat, amount)
    }

    private fun craftEngineKeyFromMaterial(materialValue: String): String? {
        val lower = materialValue.lowercase()
        return when {
            lower.startsWith("craftengine:") -> materialValue.substringAfter(':').trim().takeIf { it.isNotEmpty() }
            lower.startsWith("ce:") -> materialValue.substringAfter(':').trim().takeIf { it.isNotEmpty() }
            else -> null
        }
    }

    private fun applyCustomModelData(meta: ItemMeta, inlineCustomModelData: Int?) {
        val modelData = inlineCustomModelData ?: customModelData
        if (modelData <= 0) return
        runCatching {
            meta.javaClass.getMethod("setCustomModelData", Int::class.javaPrimitiveType).invoke(meta, modelData)
        }
    }

    private fun customModelDataFromMaterial(materialValue: String): Int? {
        val trimmed = materialValue.trim()
        val hashValue = trimmed.substringAfterLast('#', missingDelimiterValue = "").toIntOrNull()
        if (hashValue != null) return hashValue
        if (trimmed.count { it == ':' } == 1) {
            return trimmed.substringAfter(':').toIntOrNull()
        }
        return null
    }

    private fun stripModelData(materialValue: String): String {
        val trimmed = materialValue.trim()
        if (trimmed.substringAfterLast('#', missingDelimiterValue = "").toIntOrNull() != null) {
            return trimmed.substringBeforeLast('#')
        }
        if (trimmed.count { it == ':' } == 1 && trimmed.substringAfter(':').toIntOrNull() != null) {
            return trimmed.substringBefore(':')
        }
        return trimmed
    }

    private fun replace(value: String, player: Player, replacements: Map<String, String>): String {
        return GuiTextFormatter.replaceTokens(value, player, replacements)
    }

    private fun isDynamic(): Boolean {
        if (containsDynamic(materialStr) || containsDynamic(headSource) || containsDynamic(craftEngineKey)) return true
        if (containsDynamic(section.getString("name"))) return true
        return section.getStringList("lore").any { containsDynamic(it) }
    }

    private fun containsDynamic(value: String?): Boolean {
        if (value == null) return false
        return value.contains("%")
    }
}
