/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.command

import com.cobblemon.mod.common.api.permission.CobblemonPermissions
import com.cobblemon.mod.common.api.text.red
import com.cobblemon.mod.common.api.text.text
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.cobblemon.mod.common.client.render.models.blockbench.createTransformation
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.permission
import com.cobblemon.mod.common.util.traceFirstEntityCollision
import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.FloatArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands

object ChangeJointScaleCommand {
    fun register(dispatcher : CommandDispatcher<CommandSourceStack>) {
        val command = Commands.literal("changejointscale")
            .permission(CobblemonPermissions.CHANGE_JOINT_SCALE)
            .then(
                Commands.argument("joint", StringArgumentType.string())
                    .then(
                        Commands.argument("x", FloatArgumentType.floatArg())
                            .then(Commands.argument("y", FloatArgumentType.floatArg())
                                .then(
                                    Commands.argument("z", FloatArgumentType.floatArg())
                                        .executes(::execute)
                                )
                            )
                    )
                    .executes(::execute))
        dispatcher.register(command)
    }

    private fun execute(context: CommandContext<CommandSourceStack>) : Int {
        val jointName = StringArgumentType.getString(context, "joint")
        val x = FloatArgumentType.getFloat(context, "x")
        val y = FloatArgumentType.getFloat(context, "y")
        val z = FloatArgumentType.getFloat(context, "z")

        val player = context.source.playerOrException
        val targetEntity = player.traceFirstEntityCollision(entityClass = PokemonEntity::class.java)
        if (targetEntity == null) {
            player.sendSystemMessage("No pokemon".red())
            return 0
        }

        val state = FloatingState()
        state.currentAspects = targetEntity.aspects
        val model = VaryingModelRepository.getPoser(name = targetEntity.exposedSpecies.resourceIdentifier, state = state)

        val part = model.relevantPartsByName[jointName]
            ?: run {
                player.sendSystemMessage("No joint named $jointName".red())
                return 0
            }
        val existing = model.transformedParts.find { it.modelPart == part }
        if (existing == null) {
            model.transformedParts = model.transformedParts + part.createTransformation().withScale(x, y, z)
        } else {
            existing.withScale(x, y, z)
        }

        player.sendSystemMessage("Changed scale of $jointName to $x, $y, $z".text())
        return Command.SINGLE_SUCCESS
    }
}