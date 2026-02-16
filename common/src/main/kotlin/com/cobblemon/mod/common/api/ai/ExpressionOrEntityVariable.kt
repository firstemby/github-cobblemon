/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai

import com.bedrockk.molang.Expression
import com.bedrockk.molang.ast.NumberExpression
import com.bedrockk.molang.ast.StringExpression
import com.bedrockk.molang.runtime.MoLangRuntime
import com.bedrockk.molang.runtime.value.DoubleValue
import com.bedrockk.molang.runtime.value.StringValue
import com.cobblemon.mod.common.api.npc.configuration.MoLangConfigVariable
import com.cobblemon.mod.common.entity.MoLangScriptingEntity
import com.cobblemon.mod.common.util.asExpression
import com.cobblemon.mod.common.util.lang
import com.cobblemon.mod.common.util.resolveBoolean
import com.cobblemon.mod.common.util.resolveDouble
import com.cobblemon.mod.common.util.resolveFloat
import com.cobblemon.mod.common.util.resolveInt
import com.cobblemon.mod.common.util.resolveString
import com.mojang.datafixers.util.Either
import kotlin.jvm.optionals.getOrNull
import net.minecraft.world.entity.LivingEntity

/**
 * The issue in a lot of the behaviour datapacking is that there are times when a property should be exposed to
 * the user and other times that you want to fix the value in place. The remedy to this is using an [Either] where
 * one side is an Expression, ergo a value that is not exposed to the user, and the other side is a fully defined
 * configuration variable.
 *
 * When users are editing JSONs they can have a simple expression, or they can declare full variable to show to the
 * user. Similarly, the classes for tasks and brain configurations can have defaults that hide from the user or
 * defaults that are properly defined variables. One example where this is useful is Wander tasks which might be reused
 * in panic activities where the walk speed is tuned up. When specifying the task in the panic activity, the value of
 * the walk speed would be set (overriding the default which is the walk_speed MoLang variable) to a separate config
 * variable that is exposed to the user. Pretty neat!
 *
 * @author Hiroku
 * @since January 4th, 2025
 */
typealias ExpressionOrEntityVariable = Either<Expression, MoLangConfigVariable>

fun ExpressionOrEntityVariable.asExpression() = map({ it }, { "q.entity.config.${it.variableName}".asExpression() })
fun ExpressionOrEntityVariable.resolveString(runtime: MoLangRuntime) = runtime.resolveString(asExpression())
fun ExpressionOrEntityVariable.resolveBoolean(runtime: MoLangRuntime) = runtime.resolveBoolean(asExpression())
fun ExpressionOrEntityVariable.resolveInt(runtime: MoLangRuntime) = runtime.resolveInt(asExpression())
fun ExpressionOrEntityVariable.resolveDouble(runtime: MoLangRuntime) = runtime.resolveDouble(asExpression())
fun ExpressionOrEntityVariable.resolveFloat(runtime: MoLangRuntime) = runtime.resolveFloat(asExpression())

private fun variable(category: String, name: String, type: MoLangConfigVariable.MoLangVariableType, default: String) = MoLangConfigVariable(
    variableName = name,
    category = lang("entity.variable.category.$category"),
    displayName = lang("entity.variable.$name.name"),
    description = lang("entity.variable.$name.desc"),
    type = type,
    defaultValue = default
)

fun stringVariable(category: String, name: String, default: String) = variable(category = category, name = name, type = MoLangConfigVariable.MoLangVariableType.TEXT, default = default)
fun numberVariable(category: String, name: String, default: Number) = variable(category = category, name = name, type = MoLangConfigVariable.MoLangVariableType.NUMBER, default = default.toString())
fun booleanVariable(category: String, name: String, default: Boolean) = variable(category = category, name = name, type = MoLangConfigVariable.MoLangVariableType.BOOLEAN, default = default.toString())

fun getVariableExpression(name: String) = "q.entity.config.$name".asExpression()
fun resolveBooleanVariable(name: String, runtime: MoLangRuntime) = runtime.resolveBoolean(getVariableExpression(name))

fun checkCondition(runtime: MoLangRuntime, expressionOrEntityVariable: ExpressionOrEntityVariable): Boolean {
    return expressionOrEntityVariable.resolveBoolean(runtime)
}

fun ExpressionOrEntityVariable.asSimplifiedExpression(entity: LivingEntity): Expression {
    return map(
        { it },
        {
            if (entity is MoLangScriptingEntity) {
                val variable = entity.config.map[it.variableName]
                if (variable is DoubleValue) {
                    return@map NumberExpression(variable.value)
                } else if (variable is StringValue) {
                    return@map StringExpression(variable)
                }
            }
            return@map "q.entity.config.${it.variableName}".asExpression()
        }
    )
}

/**
 * In the case of [ExpressionOrEntityVariable] the right side is the only one that requires visibility for a user,
 * this function is a shorthand for finding all of the values in the list that have something to show the user to
 * potentially edit.
 */
fun List<ExpressionOrEntityVariable>.asVariables() = mapNotNull { it.right().getOrNull() }


