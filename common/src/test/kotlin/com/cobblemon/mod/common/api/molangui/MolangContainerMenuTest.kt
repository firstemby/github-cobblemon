/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.molangui

import com.cobblemon.mod.common.CobblemonMenuType
import com.cobblemon.mod.common.api.molangui.container.CloseItemsPolicy
import com.cobblemon.mod.common.api.molangui.container.CollectionConfig
import com.cobblemon.mod.common.api.molangui.container.CollectionSource
import com.cobblemon.mod.common.api.molangui.container.ItemFilterEntry
import com.cobblemon.mod.common.api.molangui.container.MolangContainerMenu
import com.cobblemon.mod.common.api.molangui.container.ServerCollectionSetup
import com.cobblemon.mod.common.api.molangui.container.ShiftClickRule
import com.cobblemon.mod.common.api.molangui.container.SlotLayout
import com.cobblemon.mod.common.junit.BootstrapMinecraft
import com.cobblemon.mod.common.util.asExpression
import com.cobblemon.mod.common.util.resolve
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.Container
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.flag.FeatureFlags
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

@BootstrapMinecraft
class MolangContainerMenuTest {
    companion object {
        @JvmStatic
        @BeforeAll
        fun initializeMenuTypes() {
            // The test bootstrap freezes vanilla registries before Cobblemon's menu initializer runs.
            mockkStatic(MenuType::class)
            try {
                every { MenuType.register(any(), any<MenuType.MenuSupplier<AbstractContainerMenu>>()) } answers {
                    MenuType(secondArg<MenuType.MenuSupplier<AbstractContainerMenu>>(), FeatureFlags.DEFAULT_FLAGS)
                }
                CobblemonMenuType.MOLANG_UI
            } finally {
                unmockkStatic(MenuType::class)
            }
        }
    }

    private val player = mockk<ServerPlayer>(relaxed = true)
    private val inventory = Inventory(player)
    private val session = mockk<MolangUiSession>(relaxed = true)

    @Test
    fun `menu removal closes its session even when item cleanup fails`() {
        val container = object : SimpleContainer(1) {
            override fun clearContent() { throw IllegalStateException("cleanup failed") }
        }
        val menu = MolangContainerMenu(1, inventory)
        menu.initializeServer(listOf(setup("input", container)), sessionOwned = setOf("input"),
            closeItemsPolicy = CloseItemsPolicy.DESTROY, owner = player, session = session)

        assertThrows(IllegalStateException::class.java) { menu.removed(player) }

        verify(exactly = 1) { session.closeFromMenu() }
    }

    @Test
    fun `scripts address collection slots through their block mapping`() {
        val block = SimpleContainer(5)
        block.setItem(0, ItemStack(Items.EMERALD, 7))
        block.setItem(3, ItemStack(Items.DIAMOND, 4))
        val menu = menu(listOf(setup("input", block, mapping = listOf(3)), setup("output", block, mapping = listOf(4))))
        assertEquals(4, menu.getStack("input", 0)?.count)
        assertNull(menu.getStack("input", 3))
        assertNull(menu.getStack("input", Int.MAX_VALUE))
        assertTrue(menu.replaceSlotAtomically("input", 0, 2, "output", 0, "minecraft:gold_ingot", 3))
        assertEquals(2, block.getItem(3).count)
        assertTrue(block.getItem(4).`is`(Items.GOLD_INGOT))
        assertEquals(3, block.getItem(4).count)
        assertEquals(7, block.getItem(0).count)
    }

    @Test
    fun `rejected oversized outputs leave both slots unchanged`() {
        val input = SimpleContainer(1).apply { setItem(0, ItemStack(Items.DIAMOND, 4)) }
        val output = SimpleContainer(1)
        val menu = menu(listOf(setup("input", input), setup("output", output)))
        for (count in listOf(65, Int.MAX_VALUE)) {
            assertFalse(menu.replaceSlotAtomically("input", 0, 1, "output", 0, "minecraft:gold_ingot", count))
            assertEquals(4, input.getItem(0).count)
            assertTrue(output.getItem(0).isEmpty)
        }
        output.setItem(0, ItemStack(Items.GOLD_INGOT, 63))
        assertFalse(menu.replaceSlotAtomically("input", 0, 1, "output", 0, "minecraft:gold_ingot", Int.MAX_VALUE))
        assertEquals(63, output.getItem(0).count)
        assertEquals(4, input.getItem(0).count)
    }

    @Test
    fun `scripted output honors container limits and filters on take-only collections`() {
        val input = SimpleContainer(1).apply { setItem(0, ItemStack(Items.DIAMOND, 4)) }
        val output = object : SimpleContainer(1) {
            override fun getMaxStackSize(stack: ItemStack) = 3
        }
        val allowed = listOf(ItemFilterEntry.Item(ResourceLocation.withDefaultNamespace("gold_ingot")))
        val menu = menu(listOf(setup("input", input), setup("output", output, takeOnly = true, allowed = allowed)))
        assertFalse(menu.replaceSlotAtomically("input", 0, 1, "output", 0, "minecraft:gold_ingot", 4))
        assertFalse(menu.replaceSlotAtomically("input", 0, 1, "output", 0, "minecraft:emerald", 1))
        assertEquals(4, input.getItem(0).count)
        assertTrue(menu.replaceSlotAtomically("input", 0, 1, "output", 0, "minecraft:gold_ingot", 3))
        assertEquals(3, output.getItem(0).count)
    }

    @Test
    fun `aliased input and output slots transform only after validating the remainder`() {
        val block = SimpleContainer(1).apply { setItem(0, ItemStack(Items.DIAMOND, 4)) }
        val menu = menu(listOf(setup("input", block), setup("output", block)))
        assertFalse(menu.replaceSlotAtomically("input", 0, 1, "output", 0, "minecraft:gold_ingot", 1))
        assertEquals(4, block.getItem(0).count)
        assertTrue(menu.replaceSlotAtomically("input", 0, 4, "output", 0, "minecraft:gold_ingot", 2))
        assertTrue(block.getItem(0).`is`(Items.GOLD_INGOT))
        assertEquals(2, block.getItem(0).count)
    }

    @Test
    fun `inventory and hotbar use their own shift click rules`() {
        val main = SimpleContainer(1)
        val hotbar = SimpleContainer(1)
        val menu = menu(listOf(setup("main", main), setup("hotbar", hotbar)), listOf(
            ShiftClickRule(listOf(ShiftClickRule.PLAYER_INVENTORY), listOf("main")),
            ShiftClickRule(listOf(ShiftClickRule.PLAYER_HOTBAR), listOf("hotbar"))
        ))
        inventory.setItem(9, ItemStack(Items.DIAMOND, 3))
        inventory.setItem(0, ItemStack(Items.EMERALD, 5))
        menu.quickMoveStack(player, 2)
        menu.quickMoveStack(player, 29)
        assertEquals(3, main.getItem(0).count)
        assertTrue(main.getItem(0).`is`(Items.DIAMOND))
        assertEquals(5, hotbar.getItem(0).count)
        assertTrue(hotbar.getItem(0).`is`(Items.EMERALD))
        assertTrue(inventory.getItem(9).isEmpty)
        assertTrue(inventory.getItem(0).isEmpty)
    }

    @Test
    fun `shift click continues through partially filled destinations`() {
        val first = SimpleContainer(1).apply { setItem(0, ItemStack(Items.DIAMOND, 63)) }
        val second = SimpleContainer(2)
        val menu = menu(listOf(setup("first", first), setup("second", second)), listOf(
            ShiftClickRule(listOf(ShiftClickRule.PLAYER_INVENTORY), listOf("first", "second"))
        ))
        inventory.setItem(9, ItemStack(Items.DIAMOND, 10))
        menu.quickMoveStack(player, 3)
        assertEquals(64, first.getItem(0).count)
        assertEquals(9, second.getItem(0).count)
        assertTrue(inventory.getItem(9).isEmpty)
    }

    @Test
    fun `shift click cannot merge into filtered or take-only occupied slots`() {
        val denied = listOf(ItemFilterEntry.Item(ResourceLocation.withDefaultNamespace("emerald")))
        val filtered = SimpleContainer(1).apply { setItem(0, ItemStack(Items.DIAMOND, 1)) }
        val takeOnly = SimpleContainer(1).apply { setItem(0, ItemStack(Items.DIAMOND, 1)) }
        val destination = SimpleContainer(1)
        val menu = menu(listOf(setup("filtered", filtered, allowed = denied), setup("output", takeOnly, takeOnly = true), setup("valid", destination)))
        inventory.setItem(9, ItemStack(Items.DIAMOND, 5))
        menu.quickMoveStack(player, 3)
        assertEquals(1, filtered.getItem(0).count)
        assertEquals(1, takeOnly.getItem(0).count)
        assertEquals(5, destination.getItem(0).count)
    }

    @Test
    fun `shift click skips aliases of the source slot`() {
        val block = SimpleContainer(1).apply { setItem(0, ItemStack(Items.DIAMOND, 4)) }
        val destination = SimpleContainer(1)
        val menu = menu(listOf(setup("source", block), setup("alias", block), setup("destination", destination)), listOf(
            ShiftClickRule(listOf("source"), listOf("alias", "destination"))
        ))
        menu.quickMoveStack(player, 0)
        assertTrue(block.getItem(0).isEmpty)
        assertEquals(4, destination.getItem(0).count)
    }

    @Test
    fun `shift click respects block placement restrictions and fills multiple limited slots`() {
        val restricted = object : SimpleContainer(1) {
            override fun canPlaceItem(index: Int, stack: ItemStack) = false
        }.apply { setItem(0, ItemStack(Items.DIAMOND, 1)) }
        val limited = object : SimpleContainer(2) {
            override fun getMaxStackSize(stack: ItemStack) = 2
        }
        val menu = menu(listOf(setup("restricted", restricted), setup("limited", limited)))
        inventory.setItem(9, ItemStack(Items.DIAMOND, 5))
        menu.quickMoveStack(player, 3)
        assertEquals(1, restricted.getItem(0).count)
        assertEquals(2, limited.getItem(0).count)
        assertEquals(2, limited.getItem(1).count)
        assertEquals(1, inventory.getItem(9).count)
    }

    @Test
    fun `server expressions reject fractional or overflowing slot counts and indices`() {
        val input = SimpleContainer(1).apply { setItem(0, ItemStack(Items.DIAMOND, 4)) }
        val output = SimpleContainer(1)
        val menu = menu(listOf(setup("input", input), setup("output", output)))
        every { session.containerMenu } returns menu
        val runtime = MolangUiServerRuntimeFactory.create(session)
        for (expression in listOf(
            "q.replace_slot('input', 0.5, 1, 'output', 0, 'minecraft:gold_ingot', 1)",
            "q.replace_slot('input', 0, 1.5, 'output', 0, 'minecraft:gold_ingot', 1)",
            "q.replace_slot('input', 0, 1, 'output', 0.5, 'minecraft:gold_ingot', 1)",
            "q.replace_slot('input', 0, 1, 'output', 0, 'minecraft:gold_ingot', 1.5)",
            "q.replace_slot('input', 0, 1, 'output', 0, 'minecraft:gold_ingot', 4294967297)"
        )) {
            assertEquals(0.0, runtime.resolve(expression.asExpression()).asDouble(), expression)
        }
        assertEquals("", runtime.resolve("q.slot('input', 0.5)".asExpression()).asString())
        assertEquals("minecraft:diamond", runtime.resolve("q.slot('input', 0)".asExpression()).asString())
        assertEquals(4, input.getItem(0).count)
        assertTrue(output.getItem(0).isEmpty)
    }

    private fun setup(name: String, container: Container, mapping: List<Int>? = null, takeOnly: Boolean = false,
                      allowed: List<ItemFilterEntry> = emptyList()): ServerCollectionSetup {
        val size = mapping?.size ?: container.containerSize
        return ServerCollectionSetup(name, CollectionConfig(size, allowed, takeOnly,
            if (mapping == null) CollectionSource.SESSION else CollectionSource.BLOCK_ENTITY,
            mapping, SlotLayout(0, 0, size, 1)), container)
    }

    private fun menu(setups: List<ServerCollectionSetup>, rules: List<ShiftClickRule> = emptyList()) =
        MolangContainerMenu(1, inventory).also {
            it.initializeServer(setups, rules, owner = player, session = session)
        }
}
