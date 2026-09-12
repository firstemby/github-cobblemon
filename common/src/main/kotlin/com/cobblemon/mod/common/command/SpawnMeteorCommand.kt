/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.command

import com.cobblemon.mod.common.CobblemonEntities
import com.cobblemon.mod.common.api.permission.CobblemonPermissions
import com.cobblemon.mod.common.api.text.green
import com.cobblemon.mod.common.entity.projectile.FallingStarEntity
import com.cobblemon.mod.common.entity.projectile.FallingStarResultType
import com.cobblemon.mod.common.util.alias
import com.cobblemon.mod.common.util.commandLang
import com.cobblemon.mod.common.util.permission
import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.Commands.literal
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.coordinates.Vec3Argument
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.Vec3

object SpawnMeteorCommand {
    const val NAME = "spawnmeteor"
    const val ALIAS = "meteor"

    private val INVALID_RESULT_TYPE = SimpleCommandExceptionType(commandLang("spawnmeteor.invalid_result_type"))
    private val RESULT_TYPE_NAMES = FallingStarResultType.entries.map { it.name }

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        val command = dispatcher.register(literal(NAME)
            .permission(CobblemonPermissions.SPAWN_METEOR)
            .then(Commands.argument("pos", Vec3Argument.vec3())
                .then(Commands.argument("resultType", StringArgumentType.word())
                    .suggests { _, builder -> SharedSuggestionProvider.suggest(RESULT_TYPE_NAMES, builder) }
                    .executes { context ->
                        execute(context, Vec3Argument.getVec3(context, "pos"), resultTypeOf(context))
                    }
                )
                .executes { context -> execute(context, Vec3Argument.getVec3(context, "pos"), FallingStarResultType.entries.random()) }
            )
            .executes { context -> execute(context, context.source.position, FallingStarResultType.entries.random()) }
        )

        dispatcher.register(command.alias(ALIAS))
    }

    private fun execute(context: CommandContext<CommandSourceStack>, target: Vec3, resultType: FallingStarResultType): Int {
        val world = context.source.level as? ServerLevel ?: return 0
        val fallingStar = FallingStarEntity(CobblemonEntities.FALLING_STAR, world)
        fallingStar.resultType = resultType
        fallingStar.horizontalDirection = Random.nextDouble(2 * Math.PI).toFloat()

        val skyHeight = min(target.y + 64, world.maxBuildHeight - 10.0)
        val ticksToFall = sqrt((skyHeight - target.y) / 0.015)
        val horizontalDistance = ticksToFall * 0.8

        fallingStar.setPos(
            target.x - sin(fallingStar.horizontalDirection) * horizontalDistance,
            skyHeight,
            target.z - cos(fallingStar.horizontalDirection) * horizontalDistance
        )

        world.addFreshEntity(fallingStar)
        context.source.sendSystemMessage(
            commandLang("spawnmeteor", resultType.name, target.x.toInt(), target.y.toInt(), target.z.toInt()).green()
        )
        return Command.SINGLE_SUCCESS
    }

    private fun resultTypeOf(context: CommandContext<CommandSourceStack>): FallingStarResultType {
        val name = StringArgumentType.getString(context, "resultType")
        return FallingStarResultType.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?: throw INVALID_RESULT_TYPE.create()
    }
}
