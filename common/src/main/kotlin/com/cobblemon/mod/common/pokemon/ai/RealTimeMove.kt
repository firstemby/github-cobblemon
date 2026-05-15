package com.cobblemon.mod.common.pokemon.ai

import com.bedrockk.molang.Expression
import com.bedrockk.molang.runtime.value.DoubleValue
import com.bedrockk.molang.runtime.value.StringValue
import com.cobblemon.mod.common.api.molang.ObjectValue
import com.cobblemon.mod.common.util.asExpression

/*
    TODO: available moves should not be explicitly defined in the species file. Most likely defined in their own registry
    TODO: Have a dynamic way of giving pokemon real time attacks
 */

class RealTimeMove(
    val name : String = "",
    val power : Float = 1f,
    val cooldownTicks : Int = 20,
    val range : Float = 0f,
    val effect : Expression = "".asExpression(),
    val priority : Int = 1,
    val isProjectile : Boolean = false
) {

    @Transient
    val struct = ObjectValue(this).also {
        it.addFunction("name") { StringValue(name)}
        it.addFunction("power") { DoubleValue(power) }
        it.addFunction("range") { DoubleValue(power) }
        it.addFunction("cooldown_ticks") { DoubleValue(cooldownTicks) }
        it.addFunction("priority") { DoubleValue(priority) }
        it.addFunction("isProjectile") { DoubleValue(isProjectile) }

    }

    companion object {
        val defaultMelee : RealTimeMove = RealTimeMove(
            name = "Tackle",
            power = 1f,
            cooldownTicks = 15,
            range = 0f,
            priority = 9999,
            isProjectile = false
        )

        val defaultRange : RealTimeMove = RealTimeMove(
            name = "Swift",
            power = 3f,
            cooldownTicks = 40,
            range = 10f,
            priority = 9999,
            isProjectile = true
        )


    }
}

