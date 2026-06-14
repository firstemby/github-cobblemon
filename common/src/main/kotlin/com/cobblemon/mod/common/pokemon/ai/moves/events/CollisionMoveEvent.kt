package com.cobblemon.mod.common.pokemon.ai.moves.events

import net.minecraft.world.phys.HitResult

class CollisionMoveEvent(
    val collisionType: HitResult.Type,
    val actions: List<MoveEventAction>, // create | move
)