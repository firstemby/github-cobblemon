/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.util.adapters

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.Environment
import com.cobblemon.mod.common.util.server
import net.minecraft.client.Minecraft
import net.minecraft.core.RegistryAccess
import net.minecraft.core.registries.BuiltInRegistries

/**
 * Utility for getting RegistryAccess with the correct client/server registry access.
 * Used when as a fallback when manually encoding/decoding registry-backed values (e.g., items, predicates).
 */
object RegistryAccessResolver {
    private val builtInAccess: RegistryAccess by lazy { RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY) }

    fun getAccess(): RegistryAccess {
        try {
            Cobblemon.implementation
        } catch (e: UninitializedPropertyAccessException) {
            return builtInAccess
        }
        if (Cobblemon.implementation.environment() == Environment.CLIENT) {
            val minecraft = Minecraft.getInstance()
            if (minecraft != null && minecraft.level != null)
                return minecraft.level!!.registryAccess()
        }

        if (Cobblemon.implementation.environment() == Environment.SERVER) {
            val server = server()
            if (server != null)
                return server.registryAccess()
        }

        return builtInAccess
    }
}