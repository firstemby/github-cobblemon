package com.cobblemon.mod.common.pokemon.ai

import com.bedrockk.molang.runtime.value.DoubleValue
import com.bedrockk.molang.runtime.value.StringValue
import com.cobblemon.mod.common.api.molang.ObjectValue
import com.cobblemon.mod.common.util.asExpression

/*
    TODO: available moves should not be explicitly defined in the species file. Most likely defined in their own registry
    TODO: Have a dynamic way of giving pokemon real time attacks
 */

class RealTimeMove {
    val name : String = ""
    val power : Float = 1f
    val cooldownTicks = 20
    val range = 0.0f
    val effect = "".asExpression()
    val priority = 1
    val isProjectile = false

    @Transient
    val struct = ObjectValue(this).also {
        it.addFunction("name") { StringValue(name)}
        it.addFunction("power") { DoubleValue(power) }
        it.addFunction("range") { DoubleValue(power) }
        it.addFunction("cooldown_ticks") { DoubleValue(cooldownTicks) }
        it.addFunction("priority") { DoubleValue(priority) }
        it.addFunction("isProjectile") { DoubleValue(isProjectile) }

    }
}