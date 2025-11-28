package com.cobblemon.mod.common.net.serverhandling.riding

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.cobblemon.mod.common.client.MountedCameraTypeHandler
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.net.messages.server.riding.GrabEntityPacket
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.party
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.LivingEntity

object GrabEntityPacketHandler : ServerNetworkPacketHandler<GrabEntityPacket> {

    override fun handle(
        packet: GrabEntityPacket,
        server: MinecraftServer,
        player: ServerPlayer
    ) {
        if (!(player.isPassenger && player.vehicle is PokemonEntity)) return

        val pokemon = player.vehicle as PokemonEntity
        if (pokemon.controllingPassenger == player) {
            pokemon.ifRidingAvailable { behaviour, _, _ ->
                if (behaviour.key == cobblemonResource("air/bird") && pokemon.entityHook == null) {
                    pokemon.level().getEntities(pokemon, pokemon.boundingBox.inflate(1.0, 1.0, 1.0)) { it is LivingEntity && it !is PokemonEntity && !pokemon.passengers.contains(it) }
                        .minByOrNull { it.distanceToSqr(pokemon) }
                        ?.let { passenger ->
                            if (passenger is ServerPlayer) {
                                passenger.party()
                                    .mapNotNull { it.entity }
                                    .filter { it != this }
                                    .forEach { it.recallWithAnimation() }
                            }
                            else if (pokemon.level().isClientSide) {
                                MountedCameraTypeHandler.handleMount(passenger, pokemon)
                            }
                            pokemon.entityHook = passenger
                            passenger.startRiding(pokemon)
                        }
                }
                else if (pokemon.entityHook != null) {
                    pokemon.entityHook!!.stopRiding()
                }
            }
        }
    }

    private fun canPlayerStopRidingPokemon(pokemon: PokemonEntity, player: ServerPlayer): Boolean {
        return pokemon.ifRidingAvailableSupply(false) { behaviour, settings, state ->
            behaviour.canStopRiding(settings, state, pokemon, player)
        }
    }

}