/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.util

import com.cobblemon.mod.common.Cobblemon
import com.mojang.brigadier.StringReader
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import net.minecraft.IdentifierException
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier

fun Identifier.endsWith(suffix: String): Boolean {
    return this.toString().endsWith(suffix)
}

/**
 * Attempts to simplify an identifier as a string for user-friendly results.
 *
 * If the [Identifier.namespace] is [namespace] the result is [Identifier.path] else [Identifier.toString].
 *
 * @param namespace The namespace that if matched with [Identifier.namespace] allows the shortening.
 * @return The resulting string.
 */
fun Identifier.simplify(namespace: String = Cobblemon.MODID): String = if (this.namespace == namespace) this.path else this.toString()


fun Identifier.extractTo(directory : File) {
    val stream = Cobblemon::class.java.getResourceAsStream(String.format("/assets/%s/%s", namespace, path))
        ?: throw Exception("Could not read $this")
    Files.copy(stream, directory.toPath(), StandardCopyOption.REPLACE_EXISTING)
    stream.close()
}

/**
 * Creates an identifier from this string, if a namespace is not present the default [namespace] will be used.
 *
 * @param namespace The namespace that will default if none is present, defaults to [Cobblemon.MODID].
 */
fun String.asIdentifierDefaultingNamespace(namespace: String = Cobblemon.MODID): Identifier {
    val id = this.lowercase()
    return if (id.contains(":")) Identifier.fromNamespaceAndPath(id.substringBefore(":"), id.substringAfter(":")) else Identifier.fromNamespaceAndPath(namespace, id)
}

fun Identifier.toAssetPath(prefix: String = "", suffix: String = ""): Identifier {
    return Identifier.fromNamespaceAndPath(this.namespace, "$prefix$path$suffix")
}


/**
 * Attempts to parse an [Identifier] from the [StringReader].
 * Unlike [Identifier.fromCommandInput] this will default the namespace to the given [namespace] if non-present.
 * This is useful for when we want to automatically assign an identifier to our mod.
 *
 * @throws CommandSyntaxException If the raw data is not a valid identifier.
 *
 * @param namespace The [Identifier.namespace] being assigned if none is present.
 * @return The parsed [Identifier].
 */
fun StringReader.asIdentifierDefaultingNamespace(namespace: String = Cobblemon.MODID): Identifier {
    val start = this.cursor
    while (this.canRead() && Identifier.isAllowedInIdentifier(this.peek())) {
        this.skip()
    }
    val raw = this.string.substring(start, this.cursor)
    try {
        return raw.asIdentifierDefaultingNamespace(namespace)
    } catch (e: IdentifierException) {
        this.cursor = start
        throw SimpleCommandExceptionType(Component.translatable("argument.id.invalid")).createWithContext(this)
    }
}
