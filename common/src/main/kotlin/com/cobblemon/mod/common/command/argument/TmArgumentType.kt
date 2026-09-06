/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.command.argument

import com.cobblemon.mod.common.api.tms.TechnicalMachine
import com.cobblemon.mod.common.api.tms.TechnicalMachines
import com.cobblemon.mod.common.util.commandLang
import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.network.chat.MutableComponent
import net.minecraft.resources.ResourceLocation
import java.util.concurrent.CompletableFuture

class TmArgumentType : ArgumentType<TechnicalMachine> {

    override fun parse(reader: StringReader): TechnicalMachine {
        val resourceLocation = ResourceLocation.read(reader)
        if (resourceLocation == null) {
            throw SimpleCommandExceptionType(INVALID_TM).createWithContext(reader)
        }

        val tm = TechnicalMachines.getByResourceLocation(resourceLocation)
        if (tm == null) {
            throw SimpleCommandExceptionType(INVALID_TM).createWithContext(reader)
        }

        return tm
    }

    override fun <S : Any> listSuggestions(
        context: CommandContext<S>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        val resourceLocations = TechnicalMachines.getAllResourceLocations()
        val suggestions = resourceLocations.map { it.toString() }
        return SharedSuggestionProvider.suggest(suggestions, builder)
    }

    companion object {
        val INVALID_TM: MutableComponent = commandLang("technicalmachine.invalid-tm")

        fun tm() = TmArgumentType()

        fun <S> getTm(context: CommandContext<S>, name: String): TechnicalMachine {
            return context.getArgument(name, TechnicalMachine::class.java)
        }
    }
}