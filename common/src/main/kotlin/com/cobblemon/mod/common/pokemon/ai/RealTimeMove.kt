/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokemon.ai

import com.bedrockk.molang.Expression
import com.bedrockk.molang.runtime.value.DoubleValue
import com.bedrockk.molang.runtime.value.StringValue
import com.cobblemon.mod.common.api.molang.ObjectValue
import com.cobblemon.mod.common.util.asExpression
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.resources.ResourceLocation

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
    lateinit var id: ResourceLocation

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
        ).also {
            it.id = cobblemonResource("tackle")
        }

        val defaultRange : RealTimeMove = RealTimeMove(
            name = "Swift",
            power = 3f,
            cooldownTicks = 40,
            range = 10f,
            priority = 9999,
            isProjectile = true
        ).also {
            it.id = cobblemonResource("swift")
        }


    }
}

