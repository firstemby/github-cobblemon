/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.molangui

import com.cobblemon.mod.common.api.molangui.template.TemplateLibraryRegistry
import io.mockk.every
import io.mockk.mockk
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.Resource
import net.minecraft.server.packs.resources.ResourceManager
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TemplateLibraryRegistryTest {
    @AfterEach
    fun cleanup() = reload(emptyList())

    @Test
    fun `qualified references distinguish namespaces and permit dots in library paths`() {
        reload(listOf("first:common.v1" to "first", "second:common.v1" to "second"))
        assertEquals("first", text("first:common.v1.label"))
        assertEquals("second", text("second:common.v1.label"))
        assertNull(TemplateLibraryRegistry.getTemplate("common.v1.label"))
        assertNull(TemplateLibraryRegistry.getTemplate("missing:common.v1.label"))
    }

    @Test
    fun `ambiguous shorthand never depends on resource iteration order`() {
        val entries = listOf("first:common" to "first", "second:common" to "second")
        for (order in listOf(entries, entries.reversed())) {
            reload(order)
            assertNull(TemplateLibraryRegistry.getTemplate("common.label"))
        }
        reload(entries.take(1))
        assertEquals("first", text("common.label"))
        reload(emptyList())
        assertNull(TemplateLibraryRegistry.getTemplate("common.label"))
    }

    private fun text(reference: String) = TemplateLibraryRegistry.getTemplate(reference)?.body?.get("text")?.asString

    private fun reload(entries: List<Pair<String, String>>) {
        val resources = entries.associate { (name, text) ->
            val library = ResourceLocation.parse(name)
            val path = library.withPath("molang_ui/templates/${library.path}.json")
            val resource = mockk<Resource>()
            every { resource.openAsReader() } answers {
                """{"templates":{"label":{"type":"label","text":"$text"}}}""".reader().buffered()
            }
            path to resource
        }
        val manager = mockk<ResourceManager>()
        every { manager.listResources(any(), any()) } returns resources
        TemplateLibraryRegistry.reload(manager)
    }
}
