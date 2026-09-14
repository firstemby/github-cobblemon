/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.cinematic

import com.cobblemon.mod.common.junit.BootstrapMinecraft
import io.mockk.every
import io.mockk.mockk
import net.minecraft.world.entity.player.Abilities
import net.minecraft.world.entity.player.Player
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@BootstrapMinecraft
class EditorPlayerMovementTest {
    @Test
    fun `ending an override restores the original collision and flight flags`() {
        for (noPhysics in listOf(false, true)) {
            for (flying in listOf(false, true)) {
                val player = player(noPhysics, flying)
                val movement = EditorPlayerMovement()
                repeat(3) { movement.update(player, true) }
                assertTrue(player.noPhysics)
                assertTrue(player.abilities.flying)
                movement.update(player, false)
                assertEquals(noPhysics, player.noPhysics)
                assertEquals(flying, player.abilities.flying)
            }
        }
    }

    @Test
    fun `an inactive editor does not overwrite movement state owned elsewhere`() {
        val player = player(true, true)
        val movement = EditorPlayerMovement()
        movement.update(player, false)
        movement.restore()
        assertTrue(player.noPhysics)
        assertTrue(player.abilities.flying)
    }

    @Test
    fun `changing players restores the old player and captures the new baseline`() {
        val first = player(false, false)
        val second = player(true, false)
        val movement = EditorPlayerMovement()
        movement.update(first, true)
        movement.update(second, true)
        assertFalse(first.noPhysics)
        assertFalse(first.abilities.flying)
        assertTrue(second.noPhysics)
        assertTrue(second.abilities.flying)
        movement.restore()
        assertTrue(second.noPhysics)
        assertFalse(second.abilities.flying)
    }

    @Test
    fun `losing the player restores once and a later override captures fresh flags`() {
        val player = player(false, true)
        val movement = EditorPlayerMovement()
        movement.update(player, true)
        movement.update(null, true)
        assertFalse(player.noPhysics)
        assertTrue(player.abilities.flying)
        player.noPhysics = true
        player.abilities.flying = false
        movement.restore()
        movement.update(player, true)
        movement.restore()
        assertTrue(player.noPhysics)
        assertFalse(player.abilities.flying)
    }

    private fun player(noPhysics: Boolean, flying: Boolean): Player {
        val abilities = Abilities().apply { this.flying = flying }
        return mockk<Player>().also {
            every { it.abilities } returns abilities
            it.noPhysics = noPhysics
        }
    }
}
