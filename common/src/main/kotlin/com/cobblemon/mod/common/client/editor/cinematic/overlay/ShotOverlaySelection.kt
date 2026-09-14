package com.cobblemon.mod.common.client.editor.cinematic.overlay

import com.cobblemon.mod.common.client.editor.cinematic.SceneSelectionManager
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorScene
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorShot

object ShotOverlaySelection {
    fun shots(scene: EditorScene, selection: SceneSelectionManager, showAll: Boolean): List<EditorShot> {
        if (showAll) return scene.shots.values.toList()
        selection.getSelectedShotId()?.let { return listOfNotNull(scene.shots[it]) }
        selection.getSelectedCut()?.let { cut ->
            val shotId = scene.beats[cut.beatId]?.cuts?.get(cut.feedId)?.entries?.getOrNull(cut.entryIndex)?.shotId
            return listOfNotNull(scene.shots[shotId])
        }
        val beat = scene.beats[selection.getSelectedBeatId() ?: scene.startBeatId] ?: return emptyList()
        return beat.cuts.values.flatMap { it.entries }.map { it.shotId }.distinct().mapNotNull { scene.shots[it] }
    }
}
