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
import com.cobblemon.mod.common.api.moves.Moves
import com.cobblemon.mod.common.api.text.text
import com.cobblemon.mod.common.api.tms.TechnicalMachines
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
import com.cobblemon.mod.common.util.tmList
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundSource
import net.minecraft.stats.Stats
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.GameType

object PlayerMoLangFunctions : AbstractMoLangFunctionHolder<Player>() {
    override fun Player.moLangFunctions(): MutableMap<String, (MoParams) -> Any> {
        val player = this
        val functions = hashMapOf<String, (MoParams) -> Any>()

        functions["username"] = { StringValue(player.gameProfile.name) }
        functions["uuid"] = { StringValue(player.gameProfile.id.toString()) }
        functions["main_held_item"] = { player.mainHandItem.asMoLangValue(player.registryAccess()) }
        functions["off_held_item"] = { player.offhandItem.asMoLangValue(player.registryAccess()) }
        functions["inventory"] = {
            val inventory = player.inventory
            val items = ArrayStruct(hashMapOf())
            for (i in 0 until inventory.containerSize) {
                items.setDirectly("$i", inventory.getItem(i).asMoLangValue(player.registryAccess()))
            }
            items
        }
        functions["has_inventory_space"] = {
            val inventory = player.inventory
            if (inventory.freeSlot != -1) DoubleValue.ONE else DoubleValue.ZERO
        }
        functions["set_inventory_slot"] = setInvSlot@{ params ->
            val slot = params.getInt(0)
            val inventory = player.inventory
            if (slot !in 0 until inventory.containerSize) return@setInvSlot DoubleValue.ZERO

            val value = params.getOrNull<MoValue>(1) ?: return@setInvSlot DoubleValue.ZERO

            val stack: ItemStack? =
                when (value) {
                    is ObjectValue<*> -> {
                        value.obj as? ItemStack
                    }

                    is StringValue -> {
                        val id = ResourceLocation.parse(value.value)
                        val item =
                            player.registryAccess().registryOrThrow(Registries.ITEM).get(id)
                                ?: return@setInvSlot DoubleValue.ZERO
                        ItemStack(item)
                    }

                    else -> {
                        null
                    }
                }

            if (stack == null) return@setInvSlot DoubleValue.ZERO

            inventory.setItem(slot, stack)
            DoubleValue.ONE
        }
        functions["give_item"] = giveItem@{ params ->
            val inventory = player.inventory
            val freeSlot = inventory.freeSlot
            val value = params.getOrNull<MoValue>(0) ?: return@giveItem DoubleValue.ZERO
            val dropIfFull = params.getBooleanOrNull(1) ?: false

            val stack: ItemStack? =
                when (value) {
                    is ObjectValue<*> -> {
                        value.obj as? ItemStack
                    }

                    is StringValue -> {
                        val id = ResourceLocation.parse(value.value)
                        val item =
                            player.registryAccess().registryOrThrow(Registries.ITEM).get(id)
                                ?: return@giveItem DoubleValue.ZERO
                        ItemStack(item)
                    }

                    else -> {
                        null
                    }
                }

            if (stack == null) return@giveItem DoubleValue.ZERO

            if (freeSlot == -1) {
                if (dropIfFull) {
                    player.drop(stack, false)
                    return@giveItem DoubleValue.ONE
                } else {
                    return@giveItem DoubleValue.ZERO
                }
            }

            inventory.setItem(freeSlot, stack)
            DoubleValue.ONE
        }
        functions["face"] = { params ->
            ObjectValue(
                PlayerDialogueFaceProvider(
                    player.uuid,
                    params.getBooleanOrNull(0) != false,
                ),
            )
        }
        functions["swing_hand"] = { player.swing(player.usedItemHand) }
        functions["food_level"] = { DoubleValue(player.foodData.foodLevel) }
        functions["saturation_level"] = { DoubleValue(player.foodData.saturationLevel) }
        functions["tell"] = { params ->
            val message = params.getString(0).text()
            val overlay = params.getBooleanOrNull(1) == true
            player.displayClientMessage(message, overlay)
        }
        functions["teleport"] = { params ->
            val x = params.getDouble(0)
            val y = params.getDouble(1)
            val z = params.getDouble(2)
            val playParticleOptions = params.getBooleanOrNull(3) ?: false
            player.randomTeleport(x, y, z, playParticleOptions)
        }
        functions["heal"] = { params ->
            val amount = params.getDoubleOrNull(0) ?: player.maxHealth
            player.heal(amount.toFloat())
        }
        functions["environment"] = {
            val environment = MoLangEnvironment()
            environment.query = player.asMoLangValue()
            environment
        }
        functions["is_player"] = { DoubleValue.ONE }
        functions["riding_pokemon"] = ridingPokemon@{
            val vehicle = player.vehicle
            if (vehicle is PokemonEntity) {
                return@ridingPokemon vehicle.struct
            }
            DoubleValue.ZERO
        }

        when (player) {
            is ServerPlayer -> {
                functions["seen_credits"] = { DoubleValue(player.seenCredits) }
                functions["is_in_dialogue"] = { DoubleValue(player.isInDialogue) }
                functions["active_dialogue"] = activeDialogue@{
                    if (player.isInDialogue) {
                        return@activeDialogue player.activeDialogue?.toMoLangStruct() ?: DoubleValue.ZERO
                    }
                    DoubleValue.ZERO
                }
                functions["is_spectator"] = { DoubleValue(player.isSpectator) }
                functions["is_creative"] = { DoubleValue(player.isCreative) }
                functions["is_survival"] = { DoubleValue(player.gameMode.isSurvival) }
                functions["is_adventure"] = { DoubleValue(player.gameMode.gameModeForPlayer == GameType.ADVENTURE) }
                functions["run_command"] = { params ->
                    val command = params.getString(0)
                    player.server.commands.performPrefixedCommand(player.createCommandSourceStack(), command)
                }
                functions["set_battle_theme"] = { params ->
                    val soundId = params.getString(0).asResource()
                    Cobblemon.playerDataManager.getGenericData(player).battleTheme = soundId
                    DoubleValue.ONE
                }
                functions["battle_music"] = battleMusic@{ params ->
                    val soundId = params.getString(0).asResource()
                    val volume = params.getDoubleOrNull(1)?.toFloat() ?: 1.0f
                    val pitch = params.getDoubleOrNull(2)?.toFloat() ?: 1.0f
                    val restart = params.getBooleanOrNull(3) ?: true

                    if (soundId != null) {
                        val packet = BattleMusicPacket(soundId, volume, pitch, restart)
                        packet.sendToPlayer(player)
                        return@battleMusic DoubleValue.ONE
                    }
                    DoubleValue.ZERO
                }
                functions["stop_battle_music"] = {
                    val packet = BattleMusicPacket(null)
                    packet.sendToPlayer(player)
                    DoubleValue.ONE
                }
                functions["play_sound_on_server"] = { params ->
                    val sound = params.getString(0).asResource()
                    val soundSource = SoundSource.valueOf(params.getString(1).uppercase())
                    val volume = params.getDoubleOrNull(2)?.toFloat() ?: 1.0f
                    val pitch = params.getDoubleOrNull(3)?.toFloat() ?: 1.0f

                    val packet =
                        UnvalidatedPlaySoundS2CPacket(
                            sound,
                            soundSource,
                            player.x,
                            player.y,
                            player.z,
                            volume,
                            pitch,
                        )

                    packet.sendToPlayer(player)
                }
                functions["is_party_at_full_health"] = { DoubleValue(player.party().none(Pokemon::canBeHealed)) }
                functions["can_heal_at_healer"] = canHealAtHealer@{ params ->
                    val pos = params.get<ArrayStruct>(0).asBlockPos()
                    val healer =
                        player.level().getBlockEntity(pos, CobblemonBlockEntities.HEALING_MACHINE).orElse(null)
                            ?: return@canHealAtHealer DoubleValue.ZERO
                    val party = player.party()
                    DoubleValue(healer.canHeal(party))
                }
                functions["put_pokemon_in_healer"] = putPokemonAtHealer@{ params ->
                    val healer =
                        player
                            .level()
                            .getBlockEntity(
                                params.get<ArrayStruct>(0).asBlockPos(),
                                CobblemonBlockEntities.HEALING_MACHINE,
                            ).orElse(null) ?: return@putPokemonAtHealer DoubleValue.ZERO
                    val party = player.party()
                    if (healer.canHeal(party)) {
                        healer.activate(player.uuid, party)
                        return@putPokemonAtHealer DoubleValue.ONE
                    }
                    DoubleValue.ZERO
                }
                functions["party"] = { player.party().struct }
                functions["pc"] = { player.pc().struct }
                functions["has_permission"] = { params ->
                    DoubleValue(
                        Cobblemon.permissionValidator.hasPermission(
                            player,
                            params.getString(0),
                            params.getIntOrNull(1) ?: 4,
                        ),
                    )
                }
                functions["data"] = { params -> Cobblemon.molangData.load(player.uuid, params.getStringOrNull(0)) }
                functions["save_data"] = { params -> Cobblemon.molangData.save(player.uuid, params.getStringOrNull(0)) }
                functions["in_battle"] = { DoubleValue(player.isInBattle()) }
                functions["battle"] = { player.getBattleState()?.first?.struct ?: DoubleValue.ZERO }
                functions["get_npc_data"] = getNpcData@{ params ->
                    val npcId =
                        ((params.get<MoValue>(0) as? ObjectValue<*>)?.obj as? NPCEntity)?.stringUUID
                            ?: params.getString(0)
                    val data = Cobblemon.molangData.load(player.uuid, params.getStringOrNull(1))
                    if (data.map.containsKey(npcId)) {
                        return@getNpcData data.map[npcId]!!
                    }

                    val vars = VariableStruct()
                    data.map[npcId] = vars
                    vars
                }
                functions["get_npc_variable"] = getNpcVariable@{ params ->
                    val npcId =
                        ((params.get<MoValue>(0) as? ObjectValue<*>)?.obj as? NPCEntity)?.stringUUID
                            ?: params.getString(0)
                    val variable = params.getString(1)
                    val data = Cobblemon.molangData.load(player.uuid, params.getStringOrNull(2))
                    if (data.map.containsKey(npcId)) {
                        return@getNpcVariable (data.map[npcId] as VariableStruct).map[variable] ?: DoubleValue.ZERO
                    }
                    DoubleValue.ZERO
                }
                functions["set_npc_variable"] = { params ->
                    val npcId =
                        ((params.get<MoValue>(0) as? ObjectValue<*>)?.obj as? NPCEntity)?.stringUUID
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
                    DoubleValue.ONE
                }
                functions["pokedex"] = { player.pokedex().struct }
                functions["has_advancement"] = hasAdvancement@{ params ->
                    val requiredAdvancement = ResourceLocation.parse(params.getString(0))
                    player.advancements.progress.forEach { (holder, progress) ->
                        if (holder.id == requiredAdvancement && progress.isDone) {
                            return@hasAdvancement DoubleValue.ONE
                        }
                    }
                    DoubleValue.ZERO
                }
                functions["has_tm_move_unlocked"] = hasTmMoveUnlocked@{ params ->
                    val move = Moves.getByName(params.getString(0)) ?: return@hasTmMoveUnlocked DoubleValue.ZERO
                    val tm = TechnicalMachines.moveToTM[move] ?: return@hasTmMoveUnlocked DoubleValue.ZERO
                    DoubleValue(if (player.tmList().learnedTMs.contains(tm.id)) 1.0 else 0.0)
                }
                functions["unlock_tm_move"] = unlockTmMove@{ params ->
                    val move = Moves.getByName(params.getString(0)) ?: return@unlockTmMove DoubleValue.ZERO
                    val tm = TechnicalMachines.moveToTM[move] ?: return@unlockTmMove DoubleValue.ZERO
                    player.tmList().syncTMFromMove(move)
                    DoubleValue(if (player.tmList().learnedTMs.contains(tm.id)) 1.0 else 0.0)
                }
                functions["lock_tm_move"] = lockTmMove@{ params ->
                    val move = Moves.getByName(params.getString(0)) ?: return@lockTmMove DoubleValue.ZERO
                    val tm = TechnicalMachines.moveToTM[move] ?: return@lockTmMove DoubleValue.ZERO
                    player.tmList().syncTMLockFromMove(move)
                    DoubleValue(if (player.tmList().learnedTMs.contains(tm.id)) 0.0 else 1.0)
                }
                functions["start_battle"] = startBattle@{ params ->
                    val opponentValue = params.get<MoValue>(0)
                    val opponent =
                        if (opponentValue is ObjectValue<*>) {
                            opponentValue.obj as ServerPlayer
                        } else {
                            val paramString = opponentValue.asString()
                            val playerUUID = paramString.asUUID
                            if (playerUUID != null) {
                                server()?.playerList?.getPlayer(playerUUID) ?: return@startBattle DoubleValue.ZERO
                            } else {
                                server()?.playerList?.getPlayerByName(paramString)
                                    ?: return@startBattle DoubleValue.ZERO
                            }
                        }
                    val format =
                        params
                            .getStringOrNull(1)
                            ?.let(BattleFormat::fromFormatIdentifier)
                            ?: BattleFormat.GEN_9_SINGLES

                    val setLevel = params.getIntOrNull(2) ?: -1
                    format.adjustLevel = setLevel

                    val rules =
                        params
                            .getStringOrNull(5)
                            ?.split(",")
                            ?.toSet()
                            ?: emptySet()

                    val modifiedBattleFormat =
                        BattleFormat.setBattleRules(
                            battleFormat = format,
                            rules = rules,
                        )

                    val cloneParties = (setLevel != -1) || (params.getBooleanOrNull(3) ?: false)
                    val healFirst = params.getBooleanOrNull(4) ?: false

                    val battleStartResult =
                        BattleBuilder.pvp1v1(
                            player1 = player,
                            player2 = opponent,
                            battleFormat = modifiedBattleFormat,
                            cloneParties = cloneParties,
                            healFirst = healFirst,
                        )
                    var returnValue: MoValue = DoubleValue.ZERO
                    battleStartResult.ifSuccessful { returnValue = it.struct }
                    returnValue
                }
                functions["get_custom_stat"] = getCustomStat@{ params ->
                    val statName = params.getString(0)

                    val resourceLocation = ResourceLocation.tryParse(statName) ?: return@getCustomStat DoubleValue.ZERO
                    val statResourceLocation =
                        BuiltInRegistries.CUSTOM_STAT.get(resourceLocation) ?: return@getCustomStat DoubleValue.ZERO

                    val exists = Stats.CUSTOM.contains(statResourceLocation)
                    if (!exists) return@getCustomStat DoubleValue.ZERO

                    val stat = Stats.CUSTOM.get(statResourceLocation)
                    val value = player.stats.getValue(stat)

                    DoubleValue(value)
                }
                functions["get_starter_uuid"] = {
                    Cobblemon.playerDataManager.getGenericData(player).starterUUID?.let {
                        StringValue(
                            it.toString(),
                        )
                    } ?: DoubleValue.ZERO
                }
                functions["has_chosen_starter"] = {
                    DoubleValue(Cobblemon.playerDataManager.getGenericData(player).starterSelected)
                }
            }
        }

        return functions
    }
}
