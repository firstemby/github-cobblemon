package com.cobblemon.mod.common.command

import com.cobblemon.mod.common.Cobblemon.config
import com.cobblemon.mod.common.api.permission.CobblemonPermissions
import com.cobblemon.mod.common.util.permission
import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.server.level.ServerPlayer
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.LinkedList

object AlterShinyChanceCommand {
    private var currentTask: Runnable? = null
    private var remainingTime: Long = 0
    private var shinyRate: Float = config.shinyRate
    private val scheduler: ScheduledExecutorService = Executors.newScheduledThreadPool(1)
    private val queue: LinkedList<Pair<Float, Long>> = LinkedList()

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
                Commands.literal("altershinychance")
                        .permission(CobblemonPermissions.ALTER_SHINY_CHANCE)
                        .then(
                                Commands.argument("rate", IntegerArgumentType.integer(1))
                                        .executes { execute(it, it.source.server, it.source.playerOrException, null) }
                                        .then(
                                                Commands.argument("duration", IntegerArgumentType.integer(1))
                                                        .requires { it.player != null }
                                                        .executes { execute(it, it.source.server, it.source.playerOrException, IntegerArgumentType.getInteger(it, "duration").toLong()) }
                                        )
                        )
                        .then(
                                Commands.literal("skip")
                                        .requires { it.player != null }
                                        .executes { skipNext(it, it.source.server, it.source.playerOrException) }
                        )
                        .then(
                                Commands.literal("clear")
                                        .requires { it.player != null }
                                        .executes { clearQueue(it, it.source.server, it.source.playerOrException) }
                        )
        )
    }

    private fun execute(context: CommandContext<CommandSourceStack>, server: MinecraftServer, player: ServerPlayer, duration: Long?): Int {
        val rate = IntegerArgumentType.getInteger(context, "rate").toFloat()

        if (duration == null) {
            // Permanent change
            config.shinyRate = rate
            queue.clear()
            player.sendSystemMessage(Component.literal("Shiny rate permanently set to 1 in ${rate.toInt()}"))
        } else {
            queue.add(Pair(rate, duration))
            if (currentTask == null) {
                startNextShinyRate(server, player)
            } else {
                player.sendSystemMessage(Component.literal("Shiny rate queued to 1 in ${rate.toInt()} for $duration seconds."))
            }
        }
        return Command.SINGLE_SUCCESS
    }

    private fun startNextShinyRate(server: MinecraftServer, player: ServerPlayer) {
        if (queue.isEmpty()) {
            config.shinyRate = shinyRate // Reset to original
            currentTask = null
            player.sendSystemMessage(Component.literal("Shiny rate has returned to 1 in ${shinyRate.toInt()}"))
            return
        }

        val (rate, duration) = queue.poll()
        config.shinyRate = rate
        remainingTime = duration
        player.sendSystemMessage(Component.literal("Shiny rate set to 1 in ${rate.toInt()} for $duration seconds."))

        currentTask = Runnable {
            if (remainingTime > 0) {
                remainingTime -= 10
                scheduler.schedule(currentTask, 10, TimeUnit.SECONDS)
            } else {
                startNextShinyRate(server, player)
            }
        }
        scheduler.schedule(currentTask, 10, TimeUnit.SECONDS)
    }

    private fun skipNext(context: CommandContext<CommandSourceStack>, server: MinecraftServer, player: ServerPlayer): Int {
        if (queue.isNotEmpty()) {
            player.sendSystemMessage(Component.literal("Skipping current shiny rate duration."))
            startNextShinyRate(server, player)
        } else {
            player.sendSystemMessage(Component.literal("No active shiny rate durations to skip."))
        }
        return Command.SINGLE_SUCCESS
    }

    private fun clearQueue(context: CommandContext<CommandSourceStack>, server: MinecraftServer, player: ServerPlayer): Int {
        queue.clear()
        remainingTime = 0
        config.shinyRate = shinyRate
        currentTask = null
        player.sendSystemMessage(Component.literal("All shiny rate durations cleared. Shiny rate reset to 1 in ${shinyRate.toInt()}"))
        return Command.SINGLE_SUCCESS
    }
}