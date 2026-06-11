/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.command.argument

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.util.lang
import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import java.util.concurrent.CompletableFuture
import net.minecraft.commands.SharedSuggestionProvider

class SpawnBucketArgumentType: ArgumentType<String> {

    companion object {
        val EXAMPLES: List<String> = listOf(Cobblemon.bestSpawner.config.worldBuckets.keys.first())
        val INVALID_BUCKET = lang("command.checkspawns.invalid-bucket")

        fun spawnBucket() = SpawnBucketArgumentType()

        fun <S> getSpawnBucket(context: CommandContext<S>, name: String): String {
            return context.getArgument(name, String::class.java)
        }
    }

    override fun parse(reader: StringReader): String {
        val name = reader.readString()
        return Cobblemon.bestSpawner.config.worldBuckets.entries.find { it.key.equals(name, ignoreCase = true) }?.key
            ?: throw SimpleCommandExceptionType(INVALID_BUCKET).createWithContext(reader)
    }

    override fun <S : Any> listSuggestions(
        context: CommandContext<S>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        return SharedSuggestionProvider.suggest(Cobblemon.bestSpawner.config.worldBuckets.keys, builder)
    }

    override fun getExamples() = EXAMPLES
}