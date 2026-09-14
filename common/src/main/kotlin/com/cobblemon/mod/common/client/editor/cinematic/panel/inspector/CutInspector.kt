/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.cinematic.panel.inspector

import com.cobblemon.mod.common.api.cinematic.model.CutTransition
import com.cobblemon.mod.common.client.editor.cinematic.panel.timeline.CutLaneMath
import com.cobblemon.mod.common.client.editor.cinematic.panel.timeline.ShotPickerPopup
import com.cobblemon.mod.common.client.editor.cinematic.panel.timeline.TransitionPopover
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorScene
import com.cobblemon.mod.common.client.editor.model.cinematic.actions.ChangeCutEntry
import imgui.ImGui
import net.minecraft.client.resources.language.I18n
import imgui.type.ImFloat

class CutInspector {
    private val timeBuf = ImFloat(0f)
    private var syncedSig = 0
    private var syncedTime = Float.NaN

    fun render(scene: EditorScene, beatId: String, feedId: String, entryIndex: Int) {
        val beat = scene.beats[beatId] ?: run { ImGui.text(I18n.get("cobblemon.cinematic_editor.cut.beat_missing", beatId)); return }
        val track = beat.cuts[feedId] ?: run { ImGui.text(I18n.get("cobblemon.cinematic_editor.cut.track_missing", feedId)); return }
        val entry = track.entries.getOrNull(entryIndex) ?: run { ImGui.text(I18n.get("cobblemon.cinematic_editor.cut.missing", entryIndex)); return }

        val sig = System.identityHashCode(entry)
        if (sig != syncedSig || entry.time != syncedTime) {
            timeBuf.set(entry.time)
            syncedSig = sig
            syncedTime = entry.time
        }

        ImGui.text(I18n.get("cobblemon.cinematic_editor.cut.title", feedId, entryIndex))
        ImGui.separator()

        ImGui.text(I18n.get("cobblemon.cinematic_editor.cut.time"))
        ImGui.setNextItemWidth(100f)
        if (ImGui.inputFloat("##cutTime$feedId$entryIndex", timeBuf) && timeBuf.get().isFinite()) {
            val v = CutLaneMath.clampToMonotonic(track.entries.map { it.time }, entryIndex,
                timeBuf.get(), beat.duration ?: Float.MAX_VALUE)
            timeBuf.set(v)
            scene.execute(ChangeCutEntry.time(beatId, feedId, entryIndex, entry.time, v))
            syncedSig = 0
        }

        ImGui.text(I18n.get("cobblemon.cinematic_editor.cut.shot_id"))
        ImGui.sameLine()
        val pickerId = "##cutShotPicker$feedId$entryIndex"
        if (ImGui.button("${entry.shotId}##pickBtn$feedId$entryIndex")) ShotPickerPopup.open(pickerId)
        ShotPickerPopup.render(pickerId, scene) { picked ->
            if (picked != entry.shotId) {
                scene.execute(ChangeCutEntry.shotId(beatId, feedId, entryIndex, entry.shotId, picked))
            }
        }

        ImGui.text(I18n.get("cobblemon.cinematic_editor.cut.transition"))
        ImGui.sameLine()
        val transLabel = when (val t = entry.transition) {
            is CutTransition.Instant -> I18n.get("cobblemon.cinematic_editor.cut.instant")
            is CutTransition.Blend -> I18n.get("cobblemon.cinematic_editor.cut.blend", String.format("%.2fs", t.duration))
        }
        val popId = "##cutTransPop$feedId$entryIndex"
        if (ImGui.button("$transLabel##transBtn$feedId$entryIndex")) TransitionPopover.open(popId)
        TransitionPopover.render(popId, entry.transition) { next ->
            scene.execute(ChangeCutEntry.transition(beatId, feedId, entryIndex, entry.transition, next))
        }
    }
}
