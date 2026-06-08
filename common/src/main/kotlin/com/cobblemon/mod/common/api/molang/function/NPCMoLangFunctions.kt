/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.molang.function

import com.bedrockk.molang.runtime.MoLangRuntime
import com.bedrockk.molang.runtime.MoParams
import com.bedrockk.molang.runtime.struct.ArrayStruct
import com.bedrockk.molang.runtime.value.DoubleValue
import com.bedrockk.molang.runtime.value.MoValue
import com.bedrockk.molang.runtime.value.StringValue
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.CobblemonActivities
import com.cobblemon.mod.common.CobblemonBlockEntities
import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asMoLangValue
import com.cobblemon.mod.common.api.molang.MoLangFunctions.setup
import com.cobblemon.mod.common.api.molang.ObjectValue
import com.cobblemon.mod.common.api.moves.animations.ActionEffectContext
import com.cobblemon.mod.common.api.moves.animations.ActionEffects
import com.cobblemon.mod.common.api.moves.animations.NPCProvider
import com.cobblemon.mod.common.api.npc.NPCClasses
import com.cobblemon.mod.common.api.npc.configuration.interaction.DialogueNPCInteractionConfiguration
import com.cobblemon.mod.common.api.npc.configuration.interaction.ScriptNPCInteractionConfiguration
import com.cobblemon.mod.common.api.scripting.CobblemonScripts
import com.cobblemon.mod.common.api.storage.party.NPCPartyStore
import com.cobblemon.mod.common.battles.BattleRegistry
import com.cobblemon.mod.common.entity.npc.NPCEntity
import com.cobblemon.mod.common.net.messages.client.effect.RunPosableMoLangPacket
import com.cobblemon.mod.common.util.asArrayValue
import com.cobblemon.mod.common.util.asBlockPos
import com.cobblemon.mod.common.util.asIdentifierDefaultingNamespace
import com.cobblemon.mod.common.util.asResource
import com.cobblemon.mod.common.util.cloneFrom
import com.cobblemon.mod.common.util.getBooleanOrNull
import com.cobblemon.mod.common.util.getDoubleOrNull
import com.cobblemon.mod.common.util.getStringOrNull
import com.cobblemon.mod.common.util.withNPCValue
import net.minecraft.commands.arguments.EntityAnchorArgument
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.EntityDimensions
import net.minecraft.world.phys.Vec3
import kotlin.collections.any

object NPCMoLangFunctions : AbstractMoLangFunctionHolder<NPCEntity>() {
    override fun NPCEntity.moLangFunctions(): MutableMap<String, (MoParams) -> Any> {
        val npc = this
        val map = hashMapOf<String, (MoParams) -> Any>()
        map["class"] = { StringValue(npc.npc.id.toString()) }
        map["name"] = { StringValue(npc.name.string) }
        map["level"] = { DoubleValue(npc.level) }
        map["has_aspect"] = { params -> DoubleValue(npc.aspects.contains(params.getString(0))) }
        map["in_battle"] = { DoubleValue(npc.isInBattle()) }
        map["battles"] = {
            ArrayStruct(npc.battleIds.mapNotNull { BattleRegistry.getBattle(it)?.struct }
                .mapIndexed { index, value -> "$index" to value }.toMap())
        }
        map["stop_battles"] = { _ -> npc.battleIds.forEach { BattleRegistry.getBattle(it)?.stop() } }
        map["run_script_on_client"] = { params ->
            val world = npc.level()
            if (world is ServerLevel) {
                val script = params.getString(0)
                val packet = RunPosableMoLangPacket(npc.id, setOf("q.run_script('$script')"))
                packet.sendToPlayers(world.players().toList())
            }
            Unit
        }
        map["run_script"] = { params ->
            val script = params.getString(0).asIdentifierDefaultingNamespace()
            val runtime = MoLangRuntime()
            runtime.environment.cloneFrom(params.environment)
            CobblemonScripts.run(script, runtime) ?: DoubleValue(0)
        }
        map["set_movable"] = put@{ params ->
            val movable = params.getBooleanOrNull(0) != false
            npc.isMovable = movable
            return@put DoubleValue.ONE
        }
        map["set_invulnerable"] = put@{ params ->
            val invulnerable = params.getBooleanOrNull(0) != false
            npc.isInvulnerable = invulnerable
            return@put DoubleValue.ONE
        }
        map["set_leashable"] = put@{ params ->
            val leashable = params.getBooleanOrNull(0) != false
            npc.isLeashable = leashable
            return@put DoubleValue.ONE
        }
        map["set_allow_projectile_hits"] = put@{ params ->
            val allowProjectileHits = params.getBooleanOrNull(0) != false
            npc.allowProjectileHits = allowProjectileHits
            return@put DoubleValue.ONE
        }
        map["set_name_tag_visible"] = put@{ params ->
            val nameTagVisible = params.getBooleanOrNull(0) != false
            npc.hideNameTag = !nameTagVisible
            return@put DoubleValue.ONE
        }
        map["unset_interaction"] = put@{
            npc.interaction = null
            return@put DoubleValue.ONE
        }
        map["set_dialogue_interaction"] = put@{ params ->
            val dialogue = params.getString(0).asIdentifierDefaultingNamespace()
            npc.interaction = DialogueNPCInteractionConfiguration().also {
                it.dialogue = dialogue
            }
            return@put DoubleValue.ONE
        }
        map["set_script_interaction"] = put@{ params ->
            val script = params.getString(0).asIdentifierDefaultingNamespace()
            npc.interaction = ScriptNPCInteractionConfiguration().also {
                it.script = script
            }
            return@put DoubleValue.ONE
        }
        map["set_player_texture"] = put@{ params ->
            val username = params.getString(0)
            // Re-applying it would be unnecessarily laggy.
            if (username == npc.data.map["player_texture_username"]?.asString()) {
                return@put DoubleValue.ZERO
            }
            npc.loadTextureFromGameProfileName(username)
            return@put DoubleValue.ONE
        }
        map["unset_player_texture"] = put@{
            npc.unloadTexture()
            return@put DoubleValue.ONE
        }
        map["set_resource_identifier"] = put@{ params ->
            val identifier = params.getStringOrNull(0)?.asIdentifierDefaultingNamespace()
            npc.forcedResourceIdentifier = identifier
            return@put DoubleValue.ONE
        }
        map["unset_resource_identifier"] = put@{
            npc.forcedResourceIdentifier = null
            return@put DoubleValue.ONE
        }
        map["set_class"] = put@{ params ->
            val identifier = params.getString(0).asIdentifierDefaultingNamespace()
            val npcClass = NPCClasses.getByIdentifier(identifier)
            if (npcClass != null) {
                npc.npc = npcClass
                return@put DoubleValue.ONE
            } else {
                Cobblemon.LOGGER.error("Unknown NPC class: $identifier")
                return@put DoubleValue.ZERO
            }
        }
        map["set_render_scale"] = put@{ params ->
            val scale = params.getDouble(0)
            npc.renderScale = scale.toFloat()
            return@put DoubleValue.ONE
        }
        map["render_scale"] = { _ -> DoubleValue(npc.renderScale) }
        map["set_hitbox_scale"] = put@{ params ->
            val scale = params.getDouble(0).toFloat()
            npc.hitboxScale = scale
            npc.refreshDimensions()
            return@put DoubleValue.ONE
        }
        map["hitbox_scale"] = { _ -> DoubleValue(npc.hitboxScale) }
        map["set_hitbox"] = put@{ params ->
            if (params.params.isEmpty()) {
                npc.hitbox = null
                return@put DoubleValue.ONE
            }
            val width = params.getDouble(0).toFloat()
            val height = params.getDouble(1).toFloat()
            val eyeHeight = params.getDoubleOrNull(2)?.toFloat() ?: (height * 0.85F)
            npc.hitbox = EntityDimensions.scalable(width, height).withEyeHeight(eyeHeight)
            return@put DoubleValue.ONE
        }
        map["unset_hitbox"] = put@{
            npc.hitbox = null
            return@put DoubleValue.ONE
        }
        map["aspects"] = put@{
            val aspects = npc.aspects
            return@put aspects.asArrayValue { StringValue(it) }
        }
        map["add_aspect"] = put@{ params ->
            val aspects = params.params.map { it.asString() }
            npc.appliedAspects.addAll(aspects)
            npc.updateAspects()
            return@put DoubleValue.ONE
        }
        map["remove_aspect"] = put@{ params ->
            val aspects = params.params.map { it.asString() }
            npc.appliedAspects.removeAll(aspects.toSet())
            npc.updateAspects()
            return@put DoubleValue.ONE
        }
        map["party"] = put@{
            val party = npc.party ?: return@put DoubleValue.ZERO
            return@put party.asMoLangValue()
        }
        map["create_npc_party"] = put@{
            val party = NPCPartyStore(npc)
            return@put party.asMoLangValue()
        }
        map["set_npc_party"] = { params ->
            val party = params.get<MoValue>(0)
            if (party is ObjectValue<*>) {
                npc.party = party.obj as NPCPartyStore
            } else {
                npc.party = null
            }
            DoubleValue.ONE
        }
        map["run_action_effect"] = put@{ params ->
            val runtime = MoLangRuntime().setup()
            runtime.environment.cloneFrom(params.environment)
            runtime.withNPCValue(value = npc)
            val actionEffect = ActionEffects.actionEffects[params.getString(0).asIdentifierDefaultingNamespace()]
            if (actionEffect != null) {
                val context = ActionEffectContext(
                    actionEffect = actionEffect,
                    providers = mutableListOf(NPCProvider(npc)),
                    runtime = runtime,
                    level = npc.level()
                )
                npc.actionEffect = context
                npc.brain.setMemory(CobblemonMemories.ACTIVE_ACTION_EFFECT, context)
                npc.brain.setActiveActivityIfPossible(CobblemonActivities.ACTION_EFFECT)
                actionEffect.run(context).thenRun {
                    val npcActionEffect = npc.brain.getMemory(CobblemonMemories.ACTIVE_ACTION_EFFECT).orElse(null)
                    if (npcActionEffect == context && npc.brain.isActive(CobblemonActivities.ACTION_EFFECT)) {
                        npc.brain.eraseMemory(CobblemonMemories.ACTIVE_ACTION_EFFECT)
                        npc.actionEffect = null
                    }
                }

                return@put DoubleValue.ONE
            }
            return@put DoubleValue.ZERO
        }
        map["put_pokemon_in_healer"] = put@{ params ->
            val healer = npc.level()
                .getBlockEntity(params.get<ArrayStruct>(0).asBlockPos(), CobblemonBlockEntities.HEALING_MACHINE)
                .orElse(null) ?: return@put DoubleValue.ZERO
            val party = npc.party ?: return@put DoubleValue.ZERO
            if (healer.canHeal(party)) {
                healer.activate(npc.uuid, party)
                return@put DoubleValue.ONE
            } else {
                return@put DoubleValue.ZERO
            }
        }
        map["look_at_position"] = { params ->
            val pos = Vec3(params.getDouble(0), params.getDouble(1), params.getDouble(2))
            npc.lookAt(EntityAnchorArgument.Anchor.EYES, pos)
        }
        map["environment"] = { _ -> npc.runtime.environment }
        map["party"] = { npc.party?.struct ?: DoubleValue.ZERO }
        map["has_party"] = { DoubleValue(npc.party != null) }
        map["is_npc"] = { DoubleValue.ONE }
        map["can_battle"] =
            { DoubleValue(npc.party?.any { it.currentHealth > 0 } == true || npc.npc.party?.isStatic == false) }
        map["set_battle_theme"] = put@{ params ->
            val soundId = params.getString(0).asResource()
            npc.npc.battleTheme = soundId
            return@put DoubleValue.ONE
        }
        return map
    }
}
