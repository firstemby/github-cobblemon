/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.cinematic.panel.inspector

import kotlin.math.abs
import com.cobblemon.mod.common.api.cinematic.model.FeedKind
import com.cobblemon.mod.common.client.editor.model.cinematic.ChangeSceneProperty
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorFeed
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorScene
import imgui.ImGui
import net.minecraft.client.resources.language.I18n
import imgui.type.ImFloat

enum class FeedKindChoice(private val labelKey: String) {
    FULLSCREEN("cobblemon.cinematic_editor.feed.kind.fullscreen"),
    PIP("cobblemon.cinematic_editor.feed.kind.pip"),
    SPLIT("cobblemon.cinematic_editor.feed.kind.split"),
    DIAGONAL("cobblemon.cinematic_editor.feed.kind.diagonal");

    val label get() = I18n.get(labelKey)
}

class FeedInspector {

    private val pipSizeBuf = ImFloat(0.25f)
    private val splitSizeBuf = ImFloat(0.5f)
    private var syncedScene: EditorScene? = null
    private var syncedFeed: EditorFeed? = null
    private var syncedKind: FeedKind? = null
    private data class PendingSwap(val scene: EditorScene, val feed: EditorFeed, val kind: FeedKind, val next: FeedKindChoice)
    private var pendingSwap: PendingSwap? = null
    private var pendingPopup: String? = null

    fun render(scene: EditorScene, feedIndex: Int) {
        val feed = scene.feeds.getOrNull(feedIndex) ?: run {
            ImGui.text(I18n.get("cobblemon.cinematic_editor.feed.missing", feedIndex)); return
        }
        if (scene !== syncedScene || feed !== syncedFeed || feed.kind != syncedKind) {
            pendingPopup?.let { popup ->
                if (ImGui.beginPopupModal(popup)) {
                    try { ImGui.closeCurrentPopup() } finally { ImGui.endPopup() }
                }
            }
            pendingSwap = null
            pendingPopup = null
            syncBuffers(feed.kind)
            syncedScene = scene
            syncedFeed = feed
            syncedKind = feed.kind
        }

        ImGui.text(I18n.get("cobblemon.cinematic_editor.feed.title", feed.id))
        ImGui.separator()

        val currentChoice = choiceOf(feed.kind)
        if (ImGui.beginCombo(I18n.get("cobblemon.cinematic_editor.feed.kind") + "##feedKind${feed.id}", currentChoice.label)) {
            try {
                for (choice in FeedKindChoice.entries) {
                    val selected = choice == currentChoice
                    if (ImGui.selectable("${choice.label}###${choice.name}", selected) && !selected) {
                        if (hasUserData(feed.kind)) {
                            requestSwap(scene, feed, choice)
                            pendingPopup = I18n.get("cobblemon.cinematic_editor.feed.discard_title") + "###feedDiscard"
                        } else applyKind(scene, feed, defaultKind(choice))
                    }
                    if (selected) ImGui.setItemDefaultFocus()
                }
            } finally {
                ImGui.endCombo()
            }
        }

        pendingSwap?.let { request ->
            val popup = checkNotNull(pendingPopup)
            ImGui.openPopup(popup)
            if (ImGui.beginPopupModal(popup)) {
                try {
                    ImGui.text(I18n.get("cobblemon.cinematic_editor.feed.discard_detail"))
                    ImGui.text(I18n.get("cobblemon.cinematic_editor.feed.discard_question", request.next.label))
                    if (ImGui.button(I18n.get("cobblemon.cinematic_editor.swap"))) {
                        confirmSwap(scene, feed)
                        ImGui.closeCurrentPopup()
                    }
                    ImGui.sameLine()
                    if (ImGui.button(I18n.get("cobblemon.cinematic_editor.keep"))) {
                        pendingSwap = null
                        pendingPopup = null
                        ImGui.closeCurrentPopup()
                    }
                } finally {
                    ImGui.endPopup()
                }
            }
        }

        ImGui.separator()
        when (val k = feed.kind) {
            is FeedKind.Fullscreen -> ImGui.textDisabled(I18n.get("cobblemon.cinematic_editor.feed.fullscreen_detail"))
            is FeedKind.Pip -> renderPip(scene, feed, k)
            is FeedKind.Split -> renderSplit(scene, feed, k)
            is FeedKind.Diagonal -> renderDiagonal(scene, feed, k)
        }

        ImGui.separator()
        ImGui.text(I18n.get("cobblemon.cinematic_editor.feed.preview"))
        FeedLayoutSketch.render(feed.kind)
    }

    private fun renderPip(scene: EditorScene, feed: EditorFeed, current: FeedKind.Pip) {
        ImGui.text(I18n.get("cobblemon.cinematic_editor.feed.corner"))
        for (corner in FeedKind.Corner.entries) {
            if (ImGui.radioButton(I18n.get("cobblemon.cinematic_editor.feed.corner.${corner.name.lowercase()}") + "###pipCorner${corner.name}", current.corner == corner)) {
                if (corner != current.corner) {
                    applyKind(scene, feed, FeedKind.Pip(corner, current.size))
                }
            }
            if (corner != FeedKind.Corner.entries.last()) ImGui.sameLine()
        }
        ImGui.text(I18n.get("cobblemon.cinematic_editor.feed.size"))
        ImGui.setNextItemWidth(100f)
        if (inputFiniteFloat("##pipSize${feed.id}", pipSizeBuf)) {
            val v = pipSizeBuf.get().coerceIn(0.05f, 1f); pipSizeBuf.set(v)
            val latest = feed.kind as? FeedKind.Pip ?: return
            if (abs(v - latest.size) > 1e-4f) {
                applyKind(scene, feed, FeedKind.Pip(latest.corner, v))
            }
        }
    }

    private fun renderSplit(scene: EditorScene, feed: EditorFeed, current: FeedKind.Split) {
        ImGui.text(I18n.get("cobblemon.cinematic_editor.feed.side"))
        for (side in FeedKind.Side.entries) {
            if (ImGui.radioButton(I18n.get("cobblemon.cinematic_editor.feed.side.${side.name.lowercase()}") + "###splitSide${side.name}", current.side == side)) {
                if (side != current.side) {
                    applyKind(scene, feed, FeedKind.Split(side, current.size))
                }
            }
            if (side != FeedKind.Side.entries.last()) ImGui.sameLine()
        }
        ImGui.text(I18n.get("cobblemon.cinematic_editor.feed.size"))
        ImGui.setNextItemWidth(100f)
        if (inputFiniteFloat("##splitSize${feed.id}", splitSizeBuf)) {
            val v = splitSizeBuf.get().coerceIn(0.05f, 1f); splitSizeBuf.set(v)
            val latest = feed.kind as? FeedKind.Split ?: return
            if (abs(v - latest.size) > 1e-4f) {
                applyKind(scene, feed, FeedKind.Split(latest.side, v))
            }
        }
    }

    private fun renderDiagonal(scene: EditorScene, feed: EditorFeed, current: FeedKind.Diagonal) {
        ImGui.text(I18n.get("cobblemon.cinematic_editor.feed.half"))
        for (half in FeedKind.DiagonalHalf.entries) {
            if (ImGui.radioButton(I18n.get("cobblemon.cinematic_editor.feed.half.${half.name.lowercase()}") + "###diagHalf${half.name}", current.half == half)) {
                if (half != current.half) {
                    applyKind(scene, feed, FeedKind.Diagonal(half))
                }
            }
            if (half != FeedKind.DiagonalHalf.entries.last()) ImGui.sameLine()
        }
    }

    private fun syncBuffers(kind: FeedKind) {
        when (kind) {
            is FeedKind.Pip -> pipSizeBuf.set(kind.size)
            is FeedKind.Split -> splitSizeBuf.set(kind.size)
            else -> {   }
        }
    }

    internal fun requestSwap(scene: EditorScene, feed: EditorFeed, next: FeedKindChoice) {
        pendingSwap = PendingSwap(scene, feed, feed.kind, next)
    }

    internal fun confirmSwap(scene: EditorScene, feed: EditorFeed): Boolean {
        val request = pendingSwap ?: return false
        pendingSwap = null
        pendingPopup = null
        if (scene !== request.scene || feed !== request.feed || feed.kind != request.kind) return false
        return applyKind(scene, feed, defaultKind(request.next))
    }

    private fun applyKind(scene: EditorScene, feed: EditorFeed, next: FeedKind): Boolean {
        if (scene.feeds.none { it === feed } || feed.kind == next) return false
        val old = feed.kind
        scene.execute(
            ChangeSceneProperty(
                target = feed,
                setter = { f, v -> f.kind = v },
                oldValue = old,
                newValue = next
            )
        )
        syncBuffers(next)
        syncedKind = next
        return true
    }

    companion object {
        fun defaultKind(choice: FeedKindChoice): FeedKind = when (choice) {
            FeedKindChoice.FULLSCREEN -> FeedKind.Fullscreen
            FeedKindChoice.PIP -> FeedKind.Pip(corner = FeedKind.Corner.TOP_RIGHT, size = 0.25f)
            FeedKindChoice.SPLIT -> FeedKind.Split(side = FeedKind.Side.RIGHT, size = 0.5f)
            FeedKindChoice.DIAGONAL -> FeedKind.Diagonal(half = FeedKind.DiagonalHalf.LOWER)
        }

        fun choiceOf(kind: FeedKind): FeedKindChoice = when (kind) {
            is FeedKind.Fullscreen -> FeedKindChoice.FULLSCREEN
            is FeedKind.Pip -> FeedKindChoice.PIP
            is FeedKind.Split -> FeedKindChoice.SPLIT
            is FeedKind.Diagonal -> FeedKindChoice.DIAGONAL
        }

        fun hasUserData(kind: FeedKind): Boolean = when (kind) {
            is FeedKind.Fullscreen -> false
            is FeedKind.Pip -> kind != (defaultKind(FeedKindChoice.PIP) as FeedKind.Pip)
            is FeedKind.Split -> kind != (defaultKind(FeedKindChoice.SPLIT) as FeedKind.Split)
            is FeedKind.Diagonal -> kind != (defaultKind(FeedKindChoice.DIAGONAL) as FeedKind.Diagonal)
        }
    }
}
