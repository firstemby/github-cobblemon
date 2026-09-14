package com.cobblemon.mod.common.client.editor.cinematic.panel.inspector

import com.cobblemon.mod.common.client.editor.model.cinematic.EditorScene
import com.cobblemon.mod.common.api.cinematic.runtime.ActorFraming
import imgui.ImGui
import imgui.flag.ImGuiSelectableFlags
import net.minecraft.client.resources.language.I18n

object ActorGroupPicker {
    fun render(id: String, scene: EditorScene, selected: List<String>, changed: (List<String>) -> Unit) {
        ImGui.setNextItemWidth(-1f)
        val summary = if (selected.isEmpty()) I18n.get("cobblemon.cinematic_editor.actor_group.none")
            else I18n.get("cobblemon.cinematic_editor.actor_group.count", selected.size)
        if (!ImGui.beginCombo("##actorGroup$id", summary)) return
        try {
            (ActorFraming.groups.keys + scene.actors.keys + selected).sorted().forEach { actor ->
                val checked = actor in selected
                val label = ActorFraming.groups[actor]?.let { I18n.get(it) } ?: actor
                if (ImGui.selectable("$label###$actor", checked, ImGuiSelectableFlags.DontClosePopups)) {
                    if (checked) changed(selected - actor) else if (selected.size < 16) changed(selected + actor)
                }
            }
        } finally {
            ImGui.endCombo()
        }
    }
}
