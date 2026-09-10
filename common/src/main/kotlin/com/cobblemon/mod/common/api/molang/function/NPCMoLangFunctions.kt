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
        val functions = hashMapOf<String, (MoParams) -> Any>()

        functions["class"] = { StringValue(npc.npc.id.toString()) }
        functions["name"] = { StringValue(npc.name.string) }
        functions["level"] = { DoubleValue(npc.level) }
        functions["has_aspect"] = { params -> DoubleValue(npc.aspects.contains(params.getString(0))) }
        functions["in_battle"] = { DoubleValue(npc.isInBattle()) }
        functions["battles"] = {
            ArrayStruct(
                npc.battleIds
                    .mapNotNull { BattleRegistry.getBattle(it)?.struct }
                    .mapIndexed { index, value -> "$index" to value }
                    .toMap(),
            )
        }
        functions["stop_battles"] = { npc.battleIds.forEach { BattleRegistry.getBattle(it)?.stop() } }
        functions["run_script_on_client"] = { params ->
            val world = npc.level()
            if (world is ServerLevel) {
                val script = params.getString(0)
                val packet = RunPosableMoLangPacket(npc.id, setOf("q.run_script('$script')"))
                packet.sendToPlayers(world.players().toList())
            }
            Unit
        }
        functions["run_script"] = { params ->
            val script = params.getString(0).asIdentifierDefaultingNamespace()
            val runtime = MoLangRuntime()
            runtime.environment.cloneFrom(params.environment)
            CobblemonScripts.run(script, runtime) ?: DoubleValue(0)
        }
        functions["set_movable"] = { params ->
            val movable = params.getBooleanOrNull(0) != false
            npc.isMovable = movable
            DoubleValue.ONE
        }
        functions["set_invulnerable"] = { params ->
            val invulnerable = params.getBooleanOrNull(0) != false
            npc.isInvulnerable = invulnerable
            DoubleValue.ONE
        }
        functions["set_leashable"] = { params ->
            val leashable = params.getBooleanOrNull(0) != false
            npc.isLeashable = leashable
            DoubleValue.ONE
        }
        functions["set_allow_projectile_hits"] = { params ->
            val allowProjectileHits = params.getBooleanOrNull(0) != false
            npc.allowProjectileHits = allowProjectileHits
            DoubleValue.ONE
        }
        functions["set_name_tag_visible"] = { params ->
            val nameTagVisible = params.getBooleanOrNull(0) != false
            npc.hideNameTag = !nameTagVisible
            DoubleValue.ONE
        }
        functions["unset_interaction"] = {
            npc.interaction = null
            DoubleValue.ONE
        }
        functions["set_dialogue_interaction"] = { params ->
            val dialogue = params.getString(0).asIdentifierDefaultingNamespace()
            npc.interaction =
                DialogueNPCInteractionConfiguration().also {
                    it.dialogue = dialogue
                }
            DoubleValue.ONE
        }
        functions["set_script_interaction"] = { params ->
            val script = params.getString(0).asIdentifierDefaultingNamespace()
            npc.interaction =
                ScriptNPCInteractionConfiguration().also {
                    it.script = script
                }
            DoubleValue.ONE
        }
        functions["set_player_texture"] = setPlayerTexture@{ params ->
            val username = params.getString(0)
            // Re-applying it would be unnecessarily laggy.
            if (username == npc.data.map["player_texture_username"]?.asString()) {
                return@setPlayerTexture DoubleValue.ZERO
            }
            npc.loadTextureFromGameProfileName(username)
            DoubleValue.ONE
        }
        functions["unset_player_texture"] = {
            npc.unloadTexture()
            DoubleValue.ONE
        }
        functions["set_resource_identifier"] = { params ->
            val identifier = params.getStringOrNull(0)?.asIdentifierDefaultingNamespace()
            npc.forcedResourceIdentifier = identifier
            DoubleValue.ONE
        }
        functions["unset_resource_identifier"] = {
            npc.forcedResourceIdentifier = null
            DoubleValue.ONE
        }
        functions["set_class"] = setClass@{ params ->
            val identifier = params.getString(0).asIdentifierDefaultingNamespace()
            val npcClass = NPCClasses.getByIdentifier(identifier)
            if (npcClass != null) {
                npc.npc = npcClass
                return@setClass DoubleValue.ONE
            }
            Cobblemon.LOGGER.error("Unknown NPC class: $identifier")
            DoubleValue.ZERO
        }
        functions["set_render_scale"] = { params ->
            val scale = params.getDouble(0)
            npc.renderScale = scale.toFloat()
            DoubleValue.ONE
        }
        functions["render_scale"] = { DoubleValue(npc.renderScale) }
        functions["set_hitbox_scale"] = { params ->
            val scale = params.getDouble(0).toFloat()
            npc.hitboxScale = scale
            npc.refreshDimensions()
            DoubleValue.ONE
        }
        functions["hitbox_scale"] = { DoubleValue(npc.hitboxScale) }
        functions["set_hitbox"] = setHitbox@{ params ->
            if (params.params.isEmpty()) {
                npc.hitbox = null
                return@setHitbox DoubleValue.ONE
            }
            val width = params.getDouble(0).toFloat()
            val height = params.getDouble(1).toFloat()
            val eyeHeight = params.getDoubleOrNull(2)?.toFloat() ?: (height * 0.85F)
            npc.hitbox = EntityDimensions.scalable(width, height).withEyeHeight(eyeHeight)
            DoubleValue.ONE
        }
        functions["unset_hitbox"] = {
            npc.hitbox = null
            DoubleValue.ONE
        }
        functions["aspects"] = {
            val aspects = npc.aspects
            aspects.asArrayValue { StringValue(it) }
        }
        functions["add_aspect"] = { params ->
            val aspects = params.params.map { it.asString() }
            npc.appliedAspects.addAll(aspects)
            npc.updateAspects()
            DoubleValue.ONE
        }
        functions["remove_aspect"] = { params ->
            val aspects = params.params.map { it.asString() }
            npc.appliedAspects.removeAll(aspects.toSet())
            npc.updateAspects()
            DoubleValue.ONE
        }
        functions["party"] = party@{
            val party = npc.party ?: return@party DoubleValue.ZERO
            party.asMoLangValue()
        }
        functions["create_npc_party"] = {
            val party = NPCPartyStore(npc)
            party.asMoLangValue()
        }
        functions["set_npc_party"] = { params ->
            val party = params.get<MoValue>(0)
            if (party is ObjectValue<*>) {
                npc.party = party.obj as NPCPartyStore
            } else {
                npc.party = null
            }
            DoubleValue.ONE
        }
        functions["run_action_effect"] = runActionEffect@{ params ->
            val runtime = MoLangRuntime().setup()
            runtime.environment.cloneFrom(params.environment)
            runtime.withNPCValue(value = npc)
            val actionEffect = ActionEffects.actionEffects[params.getString(0).asIdentifierDefaultingNamespace()]
            if (actionEffect != null) {
                val context =
                    ActionEffectContext(
                        actionEffect = actionEffect,
                        providers = mutableListOf(NPCProvider(npc)),
                        runtime = runtime,
                        level = npc.level(),
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

                return@runActionEffect DoubleValue.ONE
            }
            DoubleValue.ZERO
        }
        functions["put_pokemon_in_healer"] = putPokeInHealer@{ params ->
            val healer =
                npc
                    .level()
                    .getBlockEntity(params.get<ArrayStruct>(0).asBlockPos(), CobblemonBlockEntities.HEALING_MACHINE)
                    .orElse(null) ?: return@putPokeInHealer DoubleValue.ZERO
            val party = npc.party ?: return@putPokeInHealer DoubleValue.ZERO
            if (healer.canHeal(party)) {
                healer.activate(npc.uuid, party)
                return@putPokeInHealer DoubleValue.ONE
            }
            DoubleValue.ZERO
        }
        functions["look_at_position"] = { params ->
            val pos = Vec3(params.getDouble(0), params.getDouble(1), params.getDouble(2))
            npc.lookAt(EntityAnchorArgument.Anchor.EYES, pos)
        }
        functions["environment"] = { npc.runtime.environment }
        functions["party"] = { npc.party?.struct ?: DoubleValue.ZERO }
        functions["has_party"] = { DoubleValue(npc.party != null) }
        functions["is_npc"] = { DoubleValue.ONE }
        functions["can_battle"] = {
            DoubleValue(npc.party?.any { it.currentHealth > 0 } == true || npc.npc.party?.isStatic == false)
        }
        functions["set_battle_theme"] = { params ->
            val soundId = params.getString(0).asResource()
            npc.npc.battleTheme = soundId
            DoubleValue.ONE
        }

        return functions
    }
}
