/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.cinematic

/** Constants are Minecraft ARGB; convert with imguiPacked before passing colors to ImGui. */
object EditorTheme {
    const val BEAT = 0xFF_5B7FE0.toInt()
    const val SHOT = 0xFF_8B5CF6.toInt()
    const val ACTOR = 0xFF_10B981.toInt()
    const val KEYFRAME = 0xFF_F59E0B.toInt()
    const val MODIFIER = 0xFF_EF4444.toInt()
    const val GIZMO_Z = 0xFF_3B82F6.toInt()
    const val SMOOTHING = 0xFF_22D3EE.toInt()
    const val TRANSITION = 0xFF_EC4899.toInt()
    const val PLAYHEAD = 0xFF_FBBF24.toInt()

    const val AXIS_X = MODIFIER
    const val AXIS_Y = ACTOR
    const val AXIS_Z = GIZMO_Z

    const val MODE_TELEPORT = SMOOTHING
    const val MODE_BLEND = KEYFRAME
    const val MODE_EXPECT = ACTOR
    const val MODE_IGNORE = 0xFF_6B7280.toInt()

    /** Keyframe overlay colors are packed ABGR for ImGui. */
    const val PATH_ORANGE_PACKED: Int = 0xFF_3C_9A_FF.toInt()

    /** Cut-lane palette — ABGR packed ints for ImGui drawlists. */
    const val CUT_BLOCK_EVEN: Int = 0xFF_3A_47_5F.toInt()
    const val CUT_BLOCK_ODD: Int = 0xFF_2F_3B_52.toInt()
    const val CUT_BLOCK_SELECTED: Int = 0xFF_58_A6_FF.toInt()
    const val CUT_BLOCK_TEXT: Int = 0xFF_EE_EE_EE.toInt()
    const val TRANSITION_PILL_INSTANT: Int = 0xFF_FF_9A_3C.toInt()
    const val TRANSITION_PILL_BLEND: Int = 0xFF_6E_C7_FF.toInt()

    /** Feed-layout preview colours — ABGR packed ints for ImGui drawlists. */
    const val FEED_PREVIEW_BG: Int = 0xFF_1A_20_2A.toInt()
    const val FEED_PREVIEW_REGION: Int = 0xAA_58_A6_FF.toInt()
    const val FEED_PREVIEW_BORDER: Int = 0xFF_80_90_A0.toInt()

    /** World-space overlay colors use Minecraft ARGB packing. */
    const val PATH_ORANGE: Int = 0xFF_FF_9A_3C.toInt()
    const val PATH_SELECTED_BLUE: Int = 0xFF_58_A6_FF.toInt()
    const val LOOK_AT_GREY: Int = 0x88_AA_AA_AA.toInt()

    /** Start-pose ghost colours (ARGB for world-space lines). */
    const val GHOST_TELEPORT: Int = 0xFF_22_D3_EE.toInt()
    const val GHOST_BLEND: Int = 0xFF_F5_9E_0B.toInt()
    const val GHOST_EXPECT: Int = 0xFF_10_B9_81.toInt()
    const val GHOST_IGNORE: Int = 0xFF_6B_72_80.toInt()

    /** ImGui wants colors in ABGR packed order. */
    fun imguiPacked(argb: Int): Int {
        val a = (argb ushr 24) and 0xFF
        val r = (argb ushr 16) and 0xFF
        val g = (argb ushr 8) and 0xFF
        val b = argb and 0xFF
        return (a shl 24) or (b shl 16) or (g shl 8) or r
    }

    fun floats(argb: Int): FloatArray {
        val a = ((argb ushr 24) and 0xFF) / 255f
        val r = ((argb ushr 16) and 0xFF) / 255f
        val g = ((argb ushr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        return floatArrayOf(r, g, b, a)
    }
}
