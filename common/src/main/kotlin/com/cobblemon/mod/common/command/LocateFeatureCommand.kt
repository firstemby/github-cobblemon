/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.command

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.permission.CobblemonPermissions
import com.cobblemon.mod.common.util.commandLang
import com.cobblemon.mod.common.util.permission
import com.cobblemon.mod.common.world.feature.PlacedFeatureLocator
import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType
import com.mojang.brigadier.suggestion.SuggestionProvider
import kotlin.math.sqrt
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.ResourceLocationArgument
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentUtils
import net.minecraft.network.chat.HoverEvent
import net.minecraft.resources.ResourceKey
import net.minecraft.util.Mth

/**
 * `/locatefeature <placed_feature> [radiusChunks]`
 *
 * The `/locate` equivalent for placed features. Works on unloaded and ungenerated chunks by replaying the
 * world seed's decoration randomness (see [PlacedFeatureLocator]); the reported coordinates are where the
 * feature attempts to place, which real terrain can still veto.
 */
object LocateFeatureCommand {

    private const val DEFAULT_RADIUS_CHUNKS = 400
    private const val MAX_RADIUS_CHUNKS = 512

    private val UNKNOWN_FEATURE = DynamicCommandExceptionType { commandLang("locatefeature.unknown", it.toString()) }
    private val UNSUPPORTED_MODIFIER = Dynamic2CommandExceptionType { id, modifier -> commandLang("locatefeature.unsupported", id.toString(), modifier.toString()) }
    private val NOT_FOUND = Dynamic2CommandExceptionType { id, radius -> commandLang("locatefeature.notfound", id.toString(), radius.toString()) }
    private val NOT_IN_DIMENSION = DynamicCommandExceptionType { commandLang("locatefeature.notindimension", it.toString()) }

    private val SUGGESTIONS = SuggestionProvider<CommandSourceStack> { context, builder ->
        val registry = context.source.registryAccess().registryOrThrow(Registries.PLACED_FEATURE)
        SharedSuggestionProvider.suggestResource(
            registry.keySet().filter(PlacedFeatureLocator::isLocatable),
            builder
        )
    }

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("locatefeature")
                .permission(CobblemonPermissions.LOCATE_FEATURE)
                .then(
                    Commands.argument("feature", ResourceLocationArgument.id())
                        .suggests(SUGGESTIONS)
                        .executes { execute(it, DEFAULT_RADIUS_CHUNKS) }
                        .then(
                            Commands.argument("radiusChunks", IntegerArgumentType.integer(1, MAX_RADIUS_CHUNKS))
                                .executes { execute(it, IntegerArgumentType.getInteger(it, "radiusChunks")) }
                        )
                )
        )
    }

    private fun execute(context: CommandContext<CommandSourceStack>, radiusChunks: Int): Int {
        val source = context.source
        val id = ResourceLocationArgument.getId(context, "feature")
        val registry = source.registryAccess().registryOrThrow(Registries.PLACED_FEATURE)
        val holder = registry.getHolder(ResourceKey.create(Registries.PLACED_FEATURE, id)).orElseThrow { UNKNOWN_FEATURE.create(id) }

        PlacedFeatureLocator.firstUnreplayableModifier(holder.value())?.let { modifier ->
            val modifierId = BuiltInRegistries.PLACEMENT_MODIFIER_TYPE.getKey(modifier.type())
            throw UNSUPPORTED_MODIFIER.create(id, modifierId ?: modifier.javaClass.simpleName)
        }

        val origin = BlockPos.containing(source.position)
        val result = PlacedFeatureLocator.locate(source.level, holder, origin, radiusChunks)
        if (result is PlacedFeatureLocator.LocateResult.NotInDimension) {
            throw NOT_IN_DIMENSION.create(id)
        }
        val sites = (result as? PlacedFeatureLocator.LocateResult.Found)?.sites ?: throw NOT_FOUND.create(id, radiusChunks)

        sites.forEachIndexed { i, site ->
            val dx = (site.pos.x - origin.x).toDouble()
            val dz = (site.pos.z - origin.z).toDouble()
            val distance = Mth.floor(sqrt(dx * dx + dz * dz))
            val coordinates = clickableCoordinates(site.pos)
            val line = if (i == 0) {
                commandLang("locatefeature.success", id.toString(), coordinates, distance)
            } else {
                commandLang("locatefeature.also", coordinates, distance)
            }
            if (!site.confirmed) {
                line.append(commandLang("locatefeature.predicted").withStyle(ChatFormatting.GRAY))
            }
            source.sendSuccess({ line }, false)
        }
        return Command.SINGLE_SUCCESS
    }

    /** Same presentation as vanilla /locate: green clickable coords that put a /tp in the chat bar. */
    private fun clickableCoordinates(pos: BlockPos) =
        ComponentUtils.wrapInSquareBrackets(Component.translatable("chat.coordinates", pos.x, "~", pos.z))
            .withStyle { style ->
                style.withColor(ChatFormatting.GREEN)
                    .withClickEvent(ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/tp @s ${pos.x} ~ ${pos.z}"))
                    .withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("chat.coordinates.tooltip")))
            }
}