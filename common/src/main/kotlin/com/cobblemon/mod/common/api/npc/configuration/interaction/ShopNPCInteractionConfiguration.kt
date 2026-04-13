/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.npc.configuration.interaction

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.api.dialogue.ActiveDialogue
import com.cobblemon.mod.common.api.dialogue.Dialogue
import com.cobblemon.mod.common.api.dialogue.DialogueManager
import com.cobblemon.mod.common.api.dialogue.DialoguePage
import com.cobblemon.mod.common.api.dialogue.FunctionDialogueAction
import com.cobblemon.mod.common.api.dialogue.WrappedDialogueText
import com.cobblemon.mod.common.api.dialogue.input.DialogueOption
import com.cobblemon.mod.common.api.dialogue.input.DialogueOptionSetInput
import com.cobblemon.mod.common.api.gui.scripted.ScriptedGuiSessionManager
import com.cobblemon.mod.common.api.npc.configuration.NPCInteractConfiguration
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.text.text
import com.cobblemon.mod.common.entity.npc.NPCEntity
import com.cobblemon.mod.common.util.DataKeys
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.party
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/**
 * An [NPCInteractConfiguration] which opens a shop dialogue when the player interacts with the NPC.
 * Presents "See Articles" / "Leave" options, then opens a scripted GUI shop with purchase handling.
 *
 * @author Cobblemon Contributors
 * @since April 2026
 */
class ShopNPCInteractionConfiguration : NPCInteractConfiguration {
    override val type: String = "shop"
    var shopConfig: ResourceLocation = cobblemonResource("example_shop")

    /**
     * A shop entry that can be either a Minecraft item or a Pokémon.
     * @param type "item" or "pokemon"
     * @param id Item registry ID (for items) or species name (for Pokémon)
     * @param price Cost in emeralds
     * @param level Pokémon level (only for pokemon type, default 5)
     */
    data class ShopEntry(
        val type: String,
        val id: String,
        val price: Int,
        val level: Int = 5
    ) {
        val isItem get() = type == "item"
        val isPokemon get() = type == "pokemon"

        fun getDisplayName(): String {
            return if (isItem) {
                val itemId = ResourceLocation.tryParse(id) ?: return id
                BuiltInRegistries.ITEM.get(itemId).defaultInstance.hoverName.string
            } else {
                PokemonSpecies.getByName(id)?.translatedName?.string ?: id.replaceFirstChar { it.uppercase() }
            }
        }
    }

    companion object {
        fun loadShopItems(): List<ShopEntry> {
            return listOf(
                ShopEntry("item", "cobblemon:poke_ball", 3),
                ShopEntry("item", "cobblemon:great_ball", 6),
                ShopEntry("item", "cobblemon:ultra_ball", 12),
                ShopEntry("item", "minecraft:potion", 5),
                ShopEntry("pokemon", "bulbasaur", 20, level = 5),
                ShopEntry("pokemon", "charmander", 20, level = 5),
                ShopEntry("pokemon", "squirtle", 20, level = 5),
                ShopEntry("pokemon", "pikachu", 25, level = 10),
                ShopEntry("pokemon", "eevee", 30, level = 5),
            )
        }
    }

    override fun interact(npc: NPCEntity, player: ServerPlayer): Boolean {
        val shopItems = loadShopItems()

        val seeArticlesOption = DialogueOption(
            text = WrappedDialogueText("See Articles".text()),
            value = "see_articles",
            action = FunctionDialogueAction { activeDialogue, _ ->
                activeDialogue.close()
                openShopGui(player, npc, shopItems)
            }
        )

        val leaveOption = DialogueOption(
            text = WrappedDialogueText("Leave".text()),
            value = "leave",
            action = FunctionDialogueAction { activeDialogue, _ ->
                activeDialogue.close()
            }
        )

        val page = DialoguePage.of(
            lines = listOf("Welcome to the shop! What would you like to do?".text()),
            input = DialogueOptionSetInput(
                options = mutableListOf(seeArticlesOption, leaveOption),
                vertical = true
            )
        )

        val dialogue = Dialogue(
            pages = listOf(page),
            escapeAction = FunctionDialogueAction { activeDialogue, _ -> activeDialogue.close() }
        )

        val currentDialogues = npc.brain.getMemory(CobblemonMemories.DIALOGUES).orElse(mutableListOf())
        val activeDialogue = DialogueManager.startDialogue(player, npc, dialogue)
        val newDialogues = currentDialogues + activeDialogue
        npc.brain.setMemory(CobblemonMemories.DIALOGUES, newDialogues)
        activeDialogue.completion.thenRun { onDialogueStopped(npc, activeDialogue) }
        return true
    }

    private fun openShopGui(player: ServerPlayer, npc: NPCEntity, shopItems: List<ShopEntry>) {
        val guiId = cobblemonResource("npc_shop")

        // Serialize shop items as a JSON array for the data-driven LIST widget
        val itemsJson = JsonArray()
        for (entry in shopItems) {
            val obj = JsonObject()
            obj.addProperty("name", entry.getDisplayName())
            obj.addProperty("price", entry.price.toString())
            obj.addProperty("entry_type", entry.type)
            if (entry.isItem) {
                obj.addProperty("item_id", entry.id)
            } else {
                obj.addProperty("species", entry.id)
                obj.addProperty("level", entry.level.toString())
            }
            itemsJson.add(obj)
        }
        val args = mapOf(
            "shop_items" to Gson().toJson(itemsJson),
            "emerald_count" to countEmeralds(player).toString()
        )

        val session = ScriptedGuiSessionManager.openGui(
            player = player,
            guiId = guiId,
            args = args
        )

        if (session == null) {
            Cobblemon.LOGGER.error("[ShopNPCInteraction] Failed to open shop GUI '$guiId' for ${player.name.string}")
            return
        }

        // Set the emerald count variable immediately so the list page shows it
        session.pushUpdate(variables = mapOf("v.emerald_count" to countEmeralds(player).toDouble()))

        session.addActionListener { action, data ->
            Cobblemon.LOGGER.info("[ShopNPC] Action received: action='$action' data=$data")
            when (action) {
                "select_item" -> {
                    val index = data["0"]?.toDoubleOrNull()?.toInt() ?: return@addActionListener
                    if (index !in shopItems.indices) return@addActionListener

                    val entry = shopItems[index]

                    val params = mutableMapOf(
                        "selected_item_name" to entry.getDisplayName(),
                        "selected_entry_type" to entry.type,
                        "feedback_message" to ""
                    )
                    if (entry.isItem) {
                        params["selected_item_id"] = entry.id
                    } else {
                        params["selected_species"] = entry.id
                    }

                    session.pushUpdate(
                        variables = mapOf(
                            "v.page" to 1.0,
                            "v.selected_index" to index.toDouble(),
                            "v.unit_price" to entry.price.toDouble(),
                            "v.quantity" to 1.0,
                            "v.feedback_type" to 0.0,
                            "v.emerald_count" to countEmeralds(player).toDouble()
                        ),
                        parameters = params
                    )
                }
                "back" -> {
                    session.pushUpdate(
                        variables = mapOf(
                            "v.page" to 0.0,
                            "v.feedback_type" to 0.0,
                            "v.emerald_count" to countEmeralds(player).toDouble()
                        ),
                        parameters = mapOf("feedback_message" to "")
                    )
                }
                "purchase" -> {
                    val index = data["0"]?.toDoubleOrNull()?.toInt() ?: return@addActionListener
                    val quantity = data["1"]?.toDoubleOrNull()?.toInt() ?: return@addActionListener
                    if (index !in shopItems.indices || quantity <= 0) return@addActionListener

                    val entry = shopItems[index]
                    val totalCost = entry.price * quantity
                    val emeralds = countEmeralds(player)

                    if (emeralds < totalCost) {
                        session.pushUpdate(
                            variables = mapOf("v.feedback_type" to 2.0),
                            parameters = mapOf("feedback_message" to "Not enough emeralds")
                        )
                        return@addActionListener
                    }

                    if (entry.isItem) {
                        if (!hasInventorySpace(player)) {
                            session.pushUpdate(
                                variables = mapOf("v.feedback_type" to 2.0),
                                parameters = mapOf("feedback_message" to "Inventory full")
                            )
                            return@addActionListener
                        }
                        removeEmeralds(player, totalCost)
                        val itemId = ResourceLocation.tryParse(entry.id) ?: return@addActionListener
                        giveItems(player, itemId, quantity)
                    } else {
                        val party = player.party()
                        if (party.occupied() + quantity > 6) {
                            session.pushUpdate(
                                variables = mapOf("v.feedback_type" to 2.0),
                                parameters = mapOf("feedback_message" to "Party is full")
                            )
                            return@addActionListener
                        }
                        removeEmeralds(player, totalCost)
                        repeat(quantity) {
                            val pokemon = PokemonProperties.parse("${entry.id} level=${entry.level}").create()
                            party.add(pokemon)
                        }
                    }

                    session.pushUpdate(
                        variables = mapOf(
                            "v.emerald_count" to countEmeralds(player).toDouble(),
                            "v.feedback_type" to 1.0,
                            "v.quantity" to 1.0
                        ),
                        parameters = mapOf("feedback_message" to "Purchased ${quantity}x ${entry.getDisplayName()}")
                    )
                }
            }
        }
    }

    private fun countEmeralds(player: ServerPlayer): Int {
        var count = 0
        for (i in 0 until player.inventory.containerSize) {
            val stack = player.inventory.getItem(i)
            if (stack.`is`(Items.EMERALD)) {
                count += stack.count
            }
        }
        return count
    }

    private fun removeEmeralds(player: ServerPlayer, amount: Int) {
        var remaining = amount
        for (i in 0 until player.inventory.containerSize) {
            if (remaining <= 0) break
            val stack = player.inventory.getItem(i)
            if (stack.`is`(Items.EMERALD)) {
                val toRemove = minOf(remaining, stack.count)
                stack.shrink(toRemove)
                remaining -= toRemove
            }
        }
    }

    private fun hasInventorySpace(player: ServerPlayer): Boolean {
        return player.inventory.freeSlot != -1
    }

    private fun giveItems(player: ServerPlayer, itemId: ResourceLocation, quantity: Int) {
        val item = BuiltInRegistries.ITEM.get(itemId)
        var remaining = quantity
        while (remaining > 0) {
            val stackSize = minOf(remaining, item.defaultMaxStackSize)
            val stack = ItemStack(item, stackSize)
            player.inventory.add(stack)
            remaining -= stackSize
        }
    }

    private fun onDialogueStopped(npc: NPCEntity, activeDialogue: ActiveDialogue) {
        val currentDialogues = npc.brain.getMemory(CobblemonMemories.DIALOGUES).orElse(mutableListOf())
        val newDialogues = currentDialogues - activeDialogue
        if (newDialogues.isEmpty()) {
            npc.brain.eraseMemory(CobblemonMemories.DIALOGUES)
        } else {
            npc.brain.setMemory(CobblemonMemories.DIALOGUES, newDialogues)
        }
    }

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeResourceLocation(shopConfig)
    }

    override fun decode(buffer: RegistryFriendlyByteBuf) {
        shopConfig = buffer.readResourceLocation()
    }

    override fun writeToNBT(compoundTag: CompoundTag) {
        compoundTag.putString(DataKeys.NPC_INTERACT_SHOP, shopConfig.toString())
    }

    override fun readFromNBT(compoundTag: CompoundTag) {
        shopConfig = ResourceLocation.parse(compoundTag.getString(DataKeys.NPC_INTERACT_SHOP))
    }

    override fun isDifferentTo(other: NPCInteractConfiguration): Boolean {
        return other !is ShopNPCInteractionConfiguration || other.shopConfig != shopConfig
    }
}
