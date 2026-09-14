/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.moves.animations.keyframes

import com.bedrockk.molang.runtime.MoLangRuntime
import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.api.cinematic.model.ActorBinding
import com.cobblemon.mod.common.api.cinematic.model.ActorDeclaration
import com.cobblemon.mod.common.api.cinematic.model.ActorKind
import com.cobblemon.mod.common.api.cinematic.model.Beat
import com.cobblemon.mod.common.api.cinematic.model.Scene
import com.cobblemon.mod.common.api.cinematic.runtime.CinematicManager
import com.cobblemon.mod.common.api.cinematic.runtime.SceneInstance
import com.cobblemon.mod.common.api.molang.ExpressionLike
import com.cobblemon.mod.common.api.moves.animations.ActionEffectContext
import com.cobblemon.mod.common.api.moves.animations.ActionEffectTimeline
import com.cobblemon.mod.common.api.moves.animations.TargetsProvider
import com.cobblemon.mod.common.api.moves.animations.UsersProvider
import com.cobblemon.mod.common.api.scheduling.ServerTaskTracker
import com.cobblemon.mod.common.junit.BootstrapMinecraft
import com.cobblemon.mod.common.util.server
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.util.UUID
import net.minecraft.server.MinecraftServer
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@BootstrapMinecraft
class CameraFocusActionEffectTest {
    private val battleId = UUID.randomUUID()
    private val user = mockk<Entity>()
    private val target = mockk<Entity>()
    private lateinit var instance: SceneInstance
    private lateinit var providers: MutableList<Any>

    @BeforeEach
    fun setup() {
        mockkStatic(::server)
        every { server() } returns mockk<MinecraftServer>(relaxed = true)
        ServerTaskTracker.clear()
        val scene = Scene("focus", actors = listOf("user", "target").associateWith {
            ActorDeclaration(it, ActorKind.RealEntity)
        }, startBeatId = "main", beats = mapOf("main" to Beat("main")))
        val id = CinematicManager.startScene(scene, Vec3.ZERO)!!
        instance = CinematicManager.getScene(id)!!
        CinematicManager.registerSceneForBattle(battleId, id)
        val battle = mockk<PokemonBattle>()
        every { battle.battleId } returns battleId
        providers = mutableListOf(battle, UsersProvider(user), TargetsProvider(target))
    }

    @AfterEach
    fun cleanup() {
        try {
            CinematicManager.clear()
            ServerTaskTracker.clear()
        } finally {
            unmockkStatic(::server)
        }
    }

    @Test
    fun `explicit release and its later timeout cannot consume another timeline request`() {
        val first = context()
        val second = context()
        focus(1f).play(first)
        focus(4f, true).play(second)
        release(first)
        assertFocus(1, true, target)
        release(first)
        ServerTaskTracker.update(1f)
        assertFocus(1, true, target)
        ServerTaskTracker.update(3f)
        assertEquals(0.0, instance.getVariable("camera_focus_count"))
    }

    @Test
    fun `ending newest request restores the previous role and entity`() {
        val first = context()
        focus(4f).play(first)
        focus(1f, true).play(context())
        assertFocus(2, true, target)
        ServerTaskTracker.update(1f)
        assertFocus(1, false, user)
        ServerTaskTracker.update(3f)
        assertEquals(0.0, instance.getVariable("camera_focus_count"))
    }

    @Test
    fun `same role overlapping requests restore the earlier entity`() {
        focus(4f).play(context())
        val other = mockk<Entity>()
        val second = ActionEffectContext(ActionEffectTimeline.NONE, runtime = MoLangRuntime(),
            providers = mutableListOf(providers.first(), UsersProvider(other)))
        focus(1f).play(second)
        assertFocus(2, false, other)
        ServerTaskTracker.update(1f)
        assertFocus(1, false, user)
    }

    @Test
    fun `zero negative and nonfinite durations expire after the default timeout`() {
        for (seconds in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            focus(seconds).play(context())
            assertFocus(1, false, user)
            ServerTaskTracker.update(2f)
            assertFocus(1, false, user)
            ServerTaskTracker.update(1f)
            assertEquals(0.0, instance.getVariable("camera_focus_count"))
        }
    }

    @Test
    fun `stopped scene timeouts do not affect a replacement scene`() {
        focus(1f).play(context())
        CinematicManager.stopScene(instance.id)
        val replacementId = CinematicManager.startScene(instance.scene, Vec3.ZERO)!!
        instance = CinematicManager.getScene(replacementId)!!
        CinematicManager.registerSceneForBattle(battleId, replacementId)
        focus(4f, true).play(context())
        ServerTaskTracker.update(1f)
        assertFocus(1, true, target)
    }

    @Test
    fun `interrupt releases only its own context even with shared providers`() {
        val first = context()
        val second = context()
        val keyframe = focus(1f)
        keyframe.play(first)
        focus(4f, true).play(second)
        keyframe.interrupt(first)
        assertFocus(1, true, target)
        ServerTaskTracker.update(1f)
        assertFocus(1, true, target)
    }

    private fun context() = ActionEffectContext(ActionEffectTimeline.NONE, runtime = MoLangRuntime(), providers = providers)

    private fun focus(seconds: Float, target: Boolean = false) = CameraFocusActionEffectKeyframe().apply {
        source = if (target) CameraFocusActionEffectKeyframe.FocusSource.TARGETS else CameraFocusActionEffectKeyframe.FocusSource.USERS
        duration = mockk<ExpressionLike>().also { every { it.resolveFloat(any()) } returns seconds }
    }

    private fun release(context: ActionEffectContext) {
        CameraFocusActionEffectKeyframe().apply { release = true }.play(context)
    }

    private fun assertFocus(count: Int, targetRole: Boolean, entity: Entity) {
        assertEquals(count.toDouble(), instance.getVariable("camera_focus_count"))
        assertEquals(if (targetRole) 1.0 else 0.0, instance.getVariable("camera_focus_role"))
        val binding = instance.actorDirector.bindings.getValue(if (targetRole) "target" else "user") as ActorBinding.Real
        assertSame(entity, binding.entity)
    }
}
