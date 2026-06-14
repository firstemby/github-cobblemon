package com.cobblemon.mod.common.pokemon.ai.moves.events

class TickMoveEvent(
    val startTick: Int = 0,
    val intervalTicks: Int = 1, // Must be > 0
    val maxOccurrences: Int = 1,
    val actions: List<MoveEventAction>, // create | move

)