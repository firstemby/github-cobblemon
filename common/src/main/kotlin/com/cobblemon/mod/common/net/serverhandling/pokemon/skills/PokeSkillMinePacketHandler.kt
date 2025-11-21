package com.cobblemon.mod.common.net.serverhandling.pokemon.skills

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.api.ai.config.CobblemonMineTargetData
import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.cobblemon.mod.common.net.messages.server.pokemon.skills.PokeSkillMinePacket
import com.cobblemon.mod.common.pokemon.activestate.ActivePokemonState
import com.cobblemon.mod.common.pokemon.activestate.ShoulderedState
import com.cobblemon.mod.common.util.raycast
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.HitResult

object PokeSkillMinePacketHandler : ServerNetworkPacketHandler<PokeSkillMinePacket> {
    override fun handle(
        packet: PokeSkillMinePacket,
        server: MinecraftServer,
        player: ServerPlayer
    ) {
        val rayCastHitResult = player.raycast(5f, ClipContext.Fluid.NONE)
        if (rayCastHitResult.type != HitResult.Type.BLOCK) {
            return
        }

        val party = Cobblemon.storage.getParty(player)
        val pokemon = party.get(0) ?: return

        val state = pokemon.state
        if (state is ShoulderedState || state !is ActivePokemonState) {
            return
        }

        val pokemonEntity = state.entity
        if (pokemonEntity == null) {
            return
        }

        val mineTargetData = CobblemonMineTargetData(rayCastHitResult.blockPos, rayCastHitResult.direction)
        pokemonEntity.brain.setMemory(CobblemonMemories.MINE_TARGET, mineTargetData)
    }
}