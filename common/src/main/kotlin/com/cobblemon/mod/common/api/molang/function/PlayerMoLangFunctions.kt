/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.molang.function

import com.bedrockk.molang.runtime.MoLangEnvironment
import com.bedrockk.molang.runtime.MoParams
import com.bedrockk.molang.runtime.struct.ArrayStruct
import com.bedrockk.molang.runtime.struct.VariableStruct
import com.bedrockk.molang.runtime.value.DoubleValue
import com.bedrockk.molang.runtime.value.MoValue
import com.bedrockk.molang.runtime.value.StringValue
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.CobblemonBlockEntities
import com.cobblemon.mod.common.api.dialogue.PlayerDialogueFaceProvider
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asMoLangValue
import com.cobblemon.mod.common.api.molang.ObjectValue
import com.cobblemon.mod.common.api.text.text
import com.cobblemon.mod.common.battles.BattleBuilder
import com.cobblemon.mod.common.battles.BattleFormat
import com.cobblemon.mod.common.entity.npc.NPCEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.net.messages.client.battle.BattleMusicPacket
import com.cobblemon.mod.common.net.messages.client.sound.UnvalidatedPlaySoundS2CPacket
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.activeDialogue
import com.cobblemon.mod.common.util.asBlockPos
import com.cobblemon.mod.common.util.asResource
import com.cobblemon.mod.common.util.asUUID
import com.cobblemon.mod.common.util.getBattleState
import com.cobblemon.mod.common.util.getBooleanOrNull
import com.cobblemon.mod.common.util.getDoubleOrNull
import com.cobblemon.mod.common.util.getIntOrNull
import com.cobblemon.mod.common.util.getOrNull
import com.cobblemon.mod.common.util.getStringOrNull
import com.cobblemon.mod.common.util.isInBattle
import com.cobblemon.mod.common.util.isInDialogue
import com.cobblemon.mod.common.util.party
import com.cobblemon.mod.common.util.pc
import com.cobblemon.mod.common.util.pokedex
import com.cobblemon.mod.common.util.server
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.GameType
import kotlin.collections.iterator

object PlayerMoLangFunctions : AbstractMoLangFunctionHolder<Player>() {
    override fun Player.moLangFunctions(): MutableMap<String, (MoParams) -> Any> {
        val player = this
        val map = mutableMapOf<String, (MoParams) -> Any>()
        map["username"] = { _ -> StringValue(player.gameProfile.name) }
        map["uuid"] = { _ -> StringValue(player.gameProfile.id.toString()) }
        map["main_held_item"] = { _ -> player.mainHandItem.asMoLangValue(player.registryAccess()) }
        map["off_held_item"] = { _ -> player.offhandItem.asMoLangValue(player.registryAccess()) }
        map["inventory"] = put@{ _ ->
            val inventory = player.inventory
            val items = ArrayStruct(hashMapOf())
            for (i in 0 until inventory.containerSize) {
                items.setDirectly("$i", inventory.getItem(i).asMoLangValue(player.registryAccess()))
            }
            return@put items
        }
        map["has_inventory_space"] = put@{ _ ->
            val inventory = player.inventory
            return@put if (inventory.getFreeSlot() != -1) DoubleValue.ONE else DoubleValue.ZERO
        }
        map["set_inventory_slot"] = put@{ params ->
            val slot = params.getInt(0)
            val inventory = player.inventory
            if (slot !in 0 until inventory.containerSize) return@put DoubleValue.ZERO

            val value = params.getOrNull<MoValue>(1) ?: return@put DoubleValue.ZERO

            val stack: ItemStack? = when (value) {
                is ObjectValue<*> -> value.obj as? ItemStack
                is StringValue -> {
                    val id = ResourceLocation.parse(value.value)
                    val item = player.registryAccess().registryOrThrow(Registries.ITEM).get(id)
                        ?: return@put DoubleValue.ZERO
                    ItemStack(item)
                }

                else -> null
            }

            if (stack == null) return@put DoubleValue.ZERO

            inventory.setItem(slot, stack)
            return@put DoubleValue.ONE
        }
        map["give_item"] = put@{ params ->
            val inventory = player.inventory
            val freeSlot = inventory.getFreeSlot()
            val value = params.getOrNull<MoValue>(0) ?: return@put DoubleValue.ZERO
            val dropIfFull = params.getBooleanOrNull(1) ?: false

            val stack: ItemStack? = when (value) {
                is ObjectValue<*> -> value.obj as? ItemStack
                is StringValue -> {
                    val id = ResourceLocation.parse(value.value)
                    val item = player.registryAccess().registryOrThrow(Registries.ITEM).get(id)
                        ?: return@put DoubleValue.ZERO
                    ItemStack(item)
                }

                else -> null
            }

            if (stack == null) return@put DoubleValue.ZERO

            if (freeSlot == -1) {
                if (dropIfFull) {
                    player.drop(stack, false)
                    return@put DoubleValue.ONE
                } else {
                    return@put DoubleValue.ZERO
                }
            }

            inventory.setItem(freeSlot, stack)
            return@put DoubleValue.ONE
        }
        map["face"] = { params ->
            ObjectValue(
                PlayerDialogueFaceProvider(
                    player.uuid,
                    params.getBooleanOrNull(0) != false
                )
            )
        }
        map["swing_hand"] = { _ -> player.swing(player.usedItemHand) }
        map["food_level"] = { _ -> DoubleValue(player.foodData.foodLevel) }
        map["saturation_level"] = { _ -> DoubleValue(player.foodData.saturationLevel) }
        map["tell"] = { params ->
            val message = params.getString(0).text()
            val overlay = params.getBooleanOrNull(1) == true
            player.displayClientMessage(message, overlay)
        }
        map["teleport"] = { params ->
            val x = params.getDouble(0)
            val y = params.getDouble(1)
            val z = params.getDouble(2)
            val playParticleOptionss = params.getBooleanOrNull(3) ?: false
            player.randomTeleport(x, y, z, playParticleOptionss)
        }
        map["heal"] = { params ->
            val amount = params.getDoubleOrNull(0) ?: player.maxHealth
            player.heal(amount.toFloat())
        }
        map["environment"] = {
            val environment = MoLangEnvironment()
            environment.query = player.asMoLangValue()
            environment
        }
        map["is_player"] = { DoubleValue.ONE }
        map["riding_pokemon"] = put@{
            val vehicle = player.vehicle
            if (vehicle is PokemonEntity) {
                return@put vehicle.struct
            } else {
                return@put DoubleValue.ZERO
            }
        }
        if (player is ServerPlayer) {
            map["seen_credits"] = { _ ->
                DoubleValue(player.seenCredits)
            }
            map["is_in_dialogue"] = { _ ->
                DoubleValue(player.isInDialogue)
            }
            map["active_dialogue"] = put@{ _ ->
                if (player.isInDialogue) {
                    return@put player.activeDialogue?.toMoLangStruct() ?: DoubleValue.ZERO
                } else {
                    DoubleValue.ZERO
                }
            }
            map["is_spectator"] = { DoubleValue(player.isSpectator) }
            map["is_creative"] = { DoubleValue(player.isCreative) }
            map["is_survival"] = { DoubleValue(player.gameMode.isSurvival) }
            map["is_adventure"] = { DoubleValue(player.gameMode.gameModeForPlayer == GameType.ADVENTURE) }
            map["run_command"] = { params ->
                val command = params.getString(0)
                player.server.commands.performPrefixedCommand(player.createCommandSourceStack(), command)
            }
            map["set_battle_theme"] = put@{ params ->
                val soundId = params.getString(0).asResource()
                Cobblemon.playerDataManager.getGenericData(player).battleTheme = soundId
                return@put DoubleValue.ONE
            }
            map["battle_music"] = put@{ params ->
                val soundId = params.getString(0).asResource()
                val volume = params.getDoubleOrNull(1)?.toFloat() ?: 1.0f
                val pitch = params.getDoubleOrNull(2)?.toFloat() ?: 1.0f
                val restart = params.getBooleanOrNull(3) ?: true

                if (soundId != null) {
                    val packet = BattleMusicPacket(soundId, volume, pitch, restart)
                    packet.sendToPlayer(player)
                    return@put DoubleValue.ONE
                } else {
                    return@put DoubleValue.ZERO
                }
            }
            map["stop_battle_music"] = put@{ _ ->
                val packet = BattleMusicPacket(null)
                packet.sendToPlayer(player)
                return@put DoubleValue.ONE
            }
            map["play_sound_on_server"] = { params ->
                val sound = params.getString(0).asResource()
                val soundSource = SoundSource.valueOf(params.getString(1).uppercase())
                val volume = params.getDoubleOrNull(2)?.toFloat() ?: 1.0f
                val pitch = params.getDoubleOrNull(3)?.toFloat() ?: 1.0f

                val packet =
                    UnvalidatedPlaySoundS2CPacket(sound, soundSource, player.x, player.y, player.z, volume, pitch)
                packet.sendToPlayer(player)
            }
            map["is_party_at_full_health"] = { _ ->
                DoubleValue(player.party().none(Pokemon::canBeHealed))
            }
            map["can_heal_at_healer"] = put@{ params ->
                val pos = params.get<ArrayStruct>(0).asBlockPos()
                val healer = player.level().getBlockEntity(pos, CobblemonBlockEntities.HEALING_MACHINE).orElse(null)
                    ?: return@put DoubleValue.ZERO
                val party = player.party()
                return@put DoubleValue(healer.canHeal(party))
            }
            map["put_pokemon_in_healer"] = put@{ params ->
                val healer = player.level()
                    .getBlockEntity(params.get<ArrayStruct>(0).asBlockPos(), CobblemonBlockEntities.HEALING_MACHINE)
                    .orElse(null) ?: return@put DoubleValue.ZERO
                val party = player.party()
                if (healer.canHeal(party)) {
                    healer.activate(player.uuid, party)
                    return@put DoubleValue.ONE
                } else {
                    return@put DoubleValue.ZERO
                }
            }
            map["party"] = { player.party().struct }
            map["pc"] = { player.pc().struct }
            map["has_permission"] = { params ->
                DoubleValue(
                    Cobblemon.permissionValidator.hasPermission(
                        player,
                        params.getString(0),
                        params.getIntOrNull(1) ?: 4
                    )
                )
            }
            map["data"] = { params -> Cobblemon.molangData.load(player.uuid, params.getStringOrNull(0)) }
            map["save_data"] = { params -> Cobblemon.molangData.save(player.uuid, params.getStringOrNull(0)) }
            map["in_battle"] = { DoubleValue(player.isInBattle()) }
            map["battle"] = { player.getBattleState()?.first?.struct ?: DoubleValue.ZERO }
            map["get_npc_data"] = put@{ params ->
                val npcId = ((params.get<MoValue>(0) as? ObjectValue<*>)?.obj as? NPCEntity)?.stringUUID
                    ?: params.getString(0)
                val data = Cobblemon.molangData.load(player.uuid, params.getStringOrNull(1))
                if (data.map.containsKey(npcId)) {
                    return@put data.map[npcId]!!
                } else {
                    val vars = VariableStruct()
                    data.map[npcId] = vars
                    return@put vars
                }
            }
            map["get_npc_variable"] = put@{ params ->
                val npcId = ((params.get<MoValue>(0) as? ObjectValue<*>)?.obj as? NPCEntity)?.stringUUID
                    ?: params.getString(0)
                val variable = params.getString(1)
                val data = Cobblemon.molangData.load(player.uuid, params.getStringOrNull(2))
                if (data.map.containsKey(npcId)) {
                    return@put (data.map[npcId] as VariableStruct).map[variable] ?: DoubleValue.ZERO
                } else {
                    return@put DoubleValue.ZERO
                }
            }
            map["set_npc_variable"] = put@{ params ->
                val npcId = ((params.get<MoValue>(0) as? ObjectValue<*>)?.obj as? NPCEntity)?.stringUUID
                    ?: params.getString(0)
                val variable = params.getString(1)
                val value = params.get<MoValue>(2)
                val saveAfterwards = params.getBooleanOrNull(3) != false
                val path = params.getStringOrNull(4)
                val data = Cobblemon.molangData.load(player.uuid, path)
                val npcData = data.map.getOrPut(npcId) { VariableStruct() } as VariableStruct
                npcData.map[variable] = value
                if (saveAfterwards) {
                    Cobblemon.molangData.save(player.uuid, path)
                }
                return@put DoubleValue.ONE
            }
            map["pokedex"] = { player.pokedex().struct }
            map["has_advancement"] = put@{ params ->
                val requiredAdvancement = ResourceLocation.parse(params.getString(0))
                for (entry in player.advancements.progress) {
                    if (entry.key.id == requiredAdvancement && entry.value.isDone) {
                        return@put DoubleValue.ONE
                    }
                }
                return@put DoubleValue.ZERO
            }
            map["start_battle"] = put@{ params ->
                val opponentValue = params.get<MoValue>(0)
                val opponent = if (opponentValue is ObjectValue<*>) {
                    opponentValue.obj as ServerPlayer
                } else {
                    val paramString = opponentValue.asString()
                    val playerUUID = paramString.asUUID
                    if (playerUUID != null) {
                        server()?.playerList?.getPlayer(playerUUID) ?: return@put DoubleValue.ZERO
                    } else {
                        server()?.playerList?.getPlayerByName(paramString) ?: return@put DoubleValue.ZERO
                    }
                }
                val format = params.getStringOrNull(1)
                    ?.let(BattleFormat::fromFormatIdentifier)
                    ?: BattleFormat.GEN_9_SINGLES

                val setLevel = params.getIntOrNull(2) ?: -1
                format.adjustLevel = setLevel

                val rules = params.getStringOrNull(5)
                    ?.split(",")
                    ?.toSet()
                    ?: emptySet()

                val modifiedBattleFormat = BattleFormat.setBattleRules(
                    battleFormat = format,
                    rules = rules
                )

                val cloneParties = (setLevel != -1) || (params.getBooleanOrNull(3) ?: false)
                val healFirst = params.getBooleanOrNull(4) ?: false

                val battleStartResult = BattleBuilder.pvp1v1(
                    player1 = player,
                    player2 = opponent,
                    battleFormat = modifiedBattleFormat,
                    cloneParties = cloneParties,
                    healFirst = healFirst
                )
                var returnValue: MoValue = DoubleValue.ZERO
                battleStartResult.ifSuccessful { returnValue = it.struct }
                return@put returnValue
            }
            map["get_custom_stat"] = put@{ params ->
                val statName = params.getString(0)

                val resourceLocation = ResourceLocation.tryParse(statName) ?: return@put DoubleValue.ZERO
                val statResourceLocation =
                    BuiltInRegistries.CUSTOM_STAT.get(resourceLocation) ?: return@put DoubleValue.ZERO

                val exists = net.minecraft.stats.Stats.CUSTOM.contains(statResourceLocation)
                if (!exists) return@put DoubleValue.ZERO

                val stat = net.minecraft.stats.Stats.CUSTOM.get(statResourceLocation)
                val value = player.stats.getValue(stat)

                DoubleValue(value)
            }
            map["get_starter_uuid"] = { _ ->
                Cobblemon.playerDataManager.getGenericData(player).starterUUID?.let {
                    StringValue(
                        it.toString()
                    )
                } ?: DoubleValue.ZERO
            }
            map["has_chosen_starter"] = { _ -> DoubleValue(Cobblemon.playerDataManager.getGenericData(player).starterSelected) }
        }

        return map
    }
}
