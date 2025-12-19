package com.cobblemon.mod.common.api.interaction

import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack

data class InteractionAttemptContext(
    val pokemon: Pokemon,
    val player: ServerPlayer?,
    val item: ItemStack
)