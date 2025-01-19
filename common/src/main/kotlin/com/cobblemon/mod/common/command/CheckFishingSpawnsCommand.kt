package com.cobblemon.mod.common.command

import com.cobblemon.mod.common.Cobblemon.config
import com.cobblemon.mod.common.api.permission.CobblemonPermissions
import com.cobblemon.mod.common.api.spawning.fishing.FishingSpawnCause
import com.cobblemon.mod.common.api.spawning.fishing.FishingSpawner
import com.cobblemon.mod.common.api.text.add
import com.cobblemon.mod.common.api.text.green
import com.cobblemon.mod.common.api.text.lightPurple
import com.cobblemon.mod.common.api.text.plus
import com.cobblemon.mod.common.api.text.red
import com.cobblemon.mod.common.api.text.text
import com.cobblemon.mod.common.api.text.underline
import com.cobblemon.mod.common.api.text.yellow
import com.cobblemon.mod.common.command.argument.SpawnBucketArgumentType
import com.cobblemon.mod.common.util.lang
import com.cobblemon.mod.common.util.permission
import com.cobblemon.mod.common.util.toBlockPos
import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.MutableComponent
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import java.text.DecimalFormat
import kotlin.collections.component1
import kotlin.collections.component2

object CheckFishingSpawnsCommand {
    const val PURPLE_THRESHOLD = 0.01F
    const val RED_THRESHOLD = 0.1F
    const val YELLOW_THRESHOLD = 5F
    val df = DecimalFormat("#.##")

    fun register(dispatcher : CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(Commands.literal("checkfishingspawn")
            .permission(CobblemonPermissions.CHECKSPAWNS)
            .then(
                Commands.argument("bucket", SpawnBucketArgumentType.spawnBucket())
                    .requires { it.player != null }
                    .executes { execute(it, it.source.playerOrException) }
            ))
    }

    private fun execute(context: CommandContext<CommandSourceStack>, player: ServerPlayer) : Int {
        if (!config.enableSpawning) {
            return 0
        }

        val spawner = FishingSpawner()
        val bucket = SpawnBucketArgumentType.getSpawnBucket(context, "bucket")
        val cause = FishingSpawnCause(spawner, bucket, player, player.mainHandItem)

        val level = player.commandSenderWorld as? ServerLevel ?: return 0
        val context = spawner.parseContext(
            cause,
            level,
            player.position().toBlockPos()
        ) ?: return 0

        val spawnProbabilities = spawner.getSpawningSelector()
            .getProbabilities(
                spawner,
                listOf(context)
            )

        val spawnNames = mutableMapOf<String, MutableComponent>()
        val namedProbabilities = mutableMapOf<MutableComponent, Float>()

        spawnProbabilities.entries.forEach {
            val nameText = it.key.getName()
            val nameString = nameText.string
            if (!spawnNames.containsKey(nameString)) {
                spawnNames[nameString] = it.key.getName()
            }

            val standardizedNameText = spawnNames[nameString]!!
            namedProbabilities[standardizedNameText] = (namedProbabilities[standardizedNameText] ?: 0F) + it.value
        }

        val sortedEntries = namedProbabilities.entries.sortedByDescending { it.value }
        val messages = mutableListOf<MutableComponent>()
        sortedEntries.forEach { (name, percentage) ->
            val message = name + ": " + applyColour("${df.format(percentage)}%".text(), percentage)
//            player.sendMessage()
            messages.add(message)
        }

        if (messages.isEmpty()) {
            player.sendSystemMessage(lang("command.checkspawns.nothing").red())
        } else {
            player.sendSystemMessage(lang("command.checkspawns.spawns").underline())
            val msg = messages[0]
            for (nextMessage in messages.subList(1, messages.size)) {
                msg.add(", ".text() + nextMessage)
            }
            player.sendSystemMessage(msg)
        }

        return Command.SINGLE_SUCCESS
    }

    fun applyColour(name: MutableComponent, percentage: Float): MutableComponent {
        return if (percentage < PURPLE_THRESHOLD) {
            name.lightPurple()
        } else if (percentage < RED_THRESHOLD) {
            name.red()
        } else if (percentage < YELLOW_THRESHOLD) {
            name.yellow()
        } else {
            name.green()
        }
    }
}
