package com.cobblemon.mod.common.stadium

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3
import java.util.*

abstract class StadiumBattleDefenition {
    abstract fun playerPositions(): MutableList<Pair<Int, Vec3>>
    abstract fun pokemonPositions(): MutableList<Pair<Int, Vec3>>


}