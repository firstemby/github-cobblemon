/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.tms

import com.cobblemon.mod.common.CobblemonNetwork.sendPacket
import com.cobblemon.mod.common.api.moves.MoveTemplate
import com.cobblemon.mod.common.api.scheduling.ScheduledTask
import com.cobblemon.mod.common.api.scheduling.ServerTaskTracker
import com.cobblemon.mod.common.api.storage.player.InstancedPlayerData
import com.cobblemon.mod.common.api.storage.player.PlayerInstancedDataStoreTypes
import com.cobblemon.mod.common.api.storage.player.client.ClientTMMoveManager
import com.cobblemon.mod.common.net.messages.client.SetClientPlayerDataPacket
import com.cobblemon.mod.common.net.messages.client.toast.ToastPacket
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.getPlayer
import com.cobblemon.mod.common.util.lang
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import java.util.UUID
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation

class TMMoveManager(
    override val uuid: UUID,
    val learnedTMs: MutableSet<ResourceLocation> = mutableSetOf()
) : InstancedPlayerData {

    val storeType = PlayerInstancedDataStoreTypes.TM_MOVES

    override fun toClientData() = ClientTMMoveManager(learnedTMs.toMutableSet())
    fun toClientDataFrom(set: Set<ResourceLocation>) = ClientTMMoveManager(set.toMutableSet())

    fun syncTMsFromPokemon(pokemon: Pokemon) {
        learn(getLearnableTMsFromPokemon(pokemon))
    }

    fun syncTMFromMove(moveTemplate: MoveTemplate) {
        val tmId = TechnicalMachines.moveToTM[moveTemplate]?.id ?: return
        learn(listOf(tmId))
    }

    fun getLearnableTMsFromPokemon(pokemon: Pokemon): Collection<ResourceLocation> {
        val learnableMoves = pokemon.allAccessibleMoves.toMutableSet()
        learnableMoves.addAll(pokemon.moveSet.map { it.template })

        var preEvolution = pokemon.preEvolution
        while (preEvolution != null) {
            learnableMoves.addAll(preEvolution.form.moves.getLevelUpMovesUpTo(pokemon.level))
            learnableMoves.addAll(preEvolution.form.moves.evolutionMoves)
            preEvolution = preEvolution.form.preEvolution
        }

        return TechnicalMachines.tmMap.values
            .filter { tm -> tm.moveName in learnableMoves }
            .map(TechnicalMachine::id)
    }

    fun scheduleFullSyncFromStores(
        party: Iterable<Pokemon?>,
        pc: Iterable<Pokemon?>,
        batchSize: Int = 25
    ) {
        val iterator = sequence {
            for (pokemon in party) {
                if (pokemon != null) yield(pokemon)
            }
            for (pokemon in pc) {
                if (pokemon != null) yield(pokemon)
            }
        }.iterator()

        if (!iterator.hasNext()) return

        val tmIds = mutableSetOf<ResourceLocation>()

        ScheduledTask.Builder()
            .tracker(ServerTaskTracker)
            .interval(0F)
            .infiniteIterations()
            .execute { task ->
                var processed = 0
                while (processed < batchSize && iterator.hasNext()) {
                    val pokemon = iterator.next()
                    tmIds.addAll(getLearnableTMsFromPokemon(pokemon))
                    processed++
                }

                if (!iterator.hasNext()) {
                    learn(tmIds)
                    task.expire()
                }
            }
            .build()
    }

    fun learn(tmIds: Collection<ResourceLocation>): Boolean {
        val newLearnedTms = mutableListOf<ResourceLocation>()
        for (tmId in tmIds) {
            if (learnedTMs.add(tmId)) {
                newLearnedTms.add(tmId)
            }
        }

        if (newLearnedTms.isEmpty()) return false

        syncClient(newLearnedTms.toSet())
        sendTMToast(newLearnedTms)

        return true
    }

    fun unlearn(tmIds: Collection<ResourceLocation>): Boolean {
        val removedTms = mutableListOf<ResourceLocation>()

        for (tmId in tmIds) {
            if (learnedTMs.remove(tmId)) {
                removedTms.add(tmId)
            }
        }

        if (removedTms.isEmpty()) return false

        syncClient(learnedTMs, isIncremental = false)

        return true
    }

    fun sendTMToast(tmIds: List<ResourceLocation>) {
        val player = uuid.getPlayer() ?: return
        var moveName: Component = Component.empty()
        val icons = tmIds.mapNotNull {
            val tm = TechnicalMachines.tmMap[it] ?: return@mapNotNull null
            moveName = tm.translatedMoveName()

            return@mapNotNull tm.createItemStack()
        }

        val description = if (icons.size == 1) moveName else lang("tms.check_tmm")

        val packet = ToastPacket(
            title = lang("tms.new_tms_learned"),
            description = description,
            icons = icons,
            frameTexture = ResourceLocation.parse("minecraft:toast/advancement"),
            progress = -1F,
            progressColor = 0x00FF00,
            // Keeping it a fixed UUID makes it so the ToastTracker merges different toasts on the client
            uuid = UUID.nameUUIDFromBytes("tm_toast".toByteArray()),
            behaviour = ToastPacket.Behaviour.SHOW_OR_UPDATE,
            durationMs = 4000L
        )

        player.sendPacket(packet)
    }

    fun syncClient(updateSet: Set<ResourceLocation> = learnedTMs, isIncremental: Boolean = true) {
        uuid.getPlayer()?.sendPacket(
            SetClientPlayerDataPacket(
                type = storeType,
                playerData = toClientDataFrom(updateSet),
                isIncremental = isIncremental
            )
        )
    }

    companion object {
        val CODEC: Codec<TMMoveManager> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.STRING.xmap(UUID::fromString, UUID::toString)
                    .fieldOf("uuid")
                    .forGetter { it.uuid },
                Codec.list(ResourceLocation.CODEC)
                    .xmap({ it.toMutableSet() }, { it.toList() })
                    .fieldOf("learnedTMs")
                    .forGetter { it.learnedTMs }
            ).apply(instance, ::TMMoveManager)
        }
    }
}
