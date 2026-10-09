/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.vending

import com.cobblemon.mod.common.api.molangui.ParameterValue
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class VendingMachineCatalogTest {

    private fun catalog(size: Int) = VendingMachineCatalog(
        (0 until size).map { CatalogEntry(ResourceLocation.fromNamespaceAndPath("cobblemon", "item_$it"), it + 1) }
    )

    private fun items(page: Map<String, ParameterValue>) = rows(page, "item")

    private fun prices(page: Map<String, ParameterValue>) = rows(page, "price")

    private fun rows(page: Map<String, ParameterValue>, suffix: String) =
        (0 until VendingMachineCatalog.PAGE_SIZE).map { text(page, "row${it}_$suffix") }

    private fun text(page: Map<String, ParameterValue>, key: String) =
        (page.getValue(key) as ParameterValue.StringValue).value

    private fun number(page: Map<String, ParameterValue>, key: String) =
        (page.getValue(key) as ParameterValue.NumberValue).value.toInt()

    @Test
    fun `page count rounds up and never reports zero pages`() {
        assertEquals(1, catalog(0).pageCount)
        assertEquals(1, catalog(3).pageCount)
        assertEquals(2, catalog(4).pageCount)
        assertEquals(3, catalog(8).pageCount)
    }

    @Test
    fun `a short final page blanks its unused rows`() {
        val page = catalog(8).pageParameters(2)
        assertEquals(listOf("cobblemon:item_6", "cobblemon:item_7", ""), items(page))
        assertEquals(listOf("7", "8", ""), prices(page))
    }

    @Test
    fun `out of range pages clamp instead of emptying the machine`() {
        val catalog = catalog(8)
        assertEquals(items(catalog.pageParameters(0)), items(catalog.pageParameters(-5)))
        assertEquals(items(catalog.pageParameters(2)), items(catalog.pageParameters(99)))
        assertEquals(0, number(catalog.pageParameters(-5), "page"))
        assertEquals(2, number(catalog.pageParameters(99), "page"))
    }

    @Test
    fun `every page reports the same page count and the full parameter set`() {
        val page = catalog(8).pageParameters(1)
        assertEquals(3, number(page, "page_count"))
        assertEquals(
            setOf(
                "page", "page_count", "page_text",
                "row0_item", "row1_item", "row2_item", "row0_price", "row1_price", "row2_price"
            ),
            page.keys
        )
    }

    @Test
    fun `displayed text is rendered server-side without float artifacts`() {
        assertEquals(listOf("1", "2", "3"), prices(catalog(8).pageParameters(0)))
        assertEquals("1/3", text(catalog(8).pageParameters(0), "page_text"))
        assertEquals("3/3", text(catalog(8).pageParameters(2), "page_text"))
        assertEquals("1/1", text(catalog(0).pageParameters(0), "page_text"))
    }
}
