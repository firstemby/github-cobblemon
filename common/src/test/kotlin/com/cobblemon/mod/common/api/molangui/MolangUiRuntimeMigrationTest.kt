/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.molangui

import com.cobblemon.mod.common.client.gui.molangui.MolangUiClientRuntimeFactory
import com.cobblemon.mod.common.junit.BootstrapMinecraft
import com.cobblemon.mod.common.util.asExpression
import com.cobblemon.mod.common.util.resolve
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import net.minecraft.client.resources.language.I18n
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@BootstrapMinecraft
class MolangUiRuntimeMigrationTest {
    private fun runtime() = MolangUiClientRuntimeFactory.create(
        ResourceLocation.fromNamespaceAndPath("cobblemon", "migration"), emptyMap(), emptyMap()
    )

    @Test fun `format survives migration to the upstream general function holder`() {
        val runtime = runtime()
        val result = runtime.resolve("q.format(12.345, 2)".asExpression()).asString()
        assertEquals(String.format("%.2f", 12.345), result)
    }

    @Test fun `new general functions do not widen the UI runtime permissions`() {
        val functions = runtime().environment.query.functions
        assertTrue("format" in functions)
        assertTrue("to_number" in functions)
        assertFalse("run_command" in functions)
        assertFalse("player" in functions)
        assertFalse("set_query" in functions)
    }

    @Test fun `translated expressions pass arguments and follow language changes`() {
        mockkStatic(I18n::class)
        try {
            every { I18n.get("test.total", "7", "emeralds") } returns "Total: 7 emeralds"
            val runtime = runtime()
            val expression = "q.translate('test.total', '7', 'emeralds')".asExpression()
            assertEquals("Total: 7 emeralds", runtime.resolve(expression).asString())
            every { I18n.get("test.total", "7", "emeralds") } returns "Total : 7 émeraudes"
            assertEquals("Total : 7 émeraudes", runtime.resolve(expression).asString())
        } finally {
            unmockkStatic(I18n::class)
        }
    }
}
