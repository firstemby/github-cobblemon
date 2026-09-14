package com.cobblemon.mod.common.client.imgui

import imgui.ImGui
import imgui.flag.ImGuiCol

object ImGuiTheme {
    fun apply() {
        ImGui.styleColorsDark()
        val style = ImGui.getStyle()

        style.setColor(ImGuiCol.WindowBg, 0.055f, 0.063f, 0.090f, 1.0f)
        style.setColor(ImGuiCol.ChildBg, 0.078f, 0.086f, 0.118f, 1.0f)
        style.setColor(ImGuiCol.PopupBg, 0.078f, 0.086f, 0.118f, 1.0f)
        style.setColor(ImGuiCol.MenuBarBg, 0.102f, 0.114f, 0.157f, 1.0f)
        style.setColor(ImGuiCol.DockingEmptyBg, 0.0f, 0.0f, 0.0f, 0.0f)   // central node stays transparent for the game
        style.setColor(ImGuiCol.TitleBg, 0.08f, 0.08f, 0.12f, 1.0f)
        style.setColor(ImGuiCol.TitleBgActive, 0.12f, 0.12f, 0.18f, 1.0f)
        style.setColor(ImGuiCol.FrameBg, 0.04f, 0.04f, 0.08f, 1.0f)
        style.setColor(ImGuiCol.FrameBgHovered, 0.10f, 0.10f, 0.18f, 1.0f)
        style.setColor(ImGuiCol.FrameBgActive, 0.15f, 0.15f, 0.25f, 1.0f)
        style.setColor(ImGuiCol.Button, 0.15f, 0.22f, 0.40f, 1.0f)
        style.setColor(ImGuiCol.ButtonHovered, 0.20f, 0.30f, 0.55f, 1.0f)
        style.setColor(ImGuiCol.ButtonActive, 0.25f, 0.38f, 0.70f, 1.0f)
        style.setColor(ImGuiCol.Header, 0.15f, 0.22f, 0.40f, 1.0f)
        style.setColor(ImGuiCol.HeaderHovered, 0.20f, 0.30f, 0.55f, 1.0f)
        style.setColor(ImGuiCol.HeaderActive, 0.25f, 0.38f, 0.70f, 1.0f)
        style.setColor(ImGuiCol.Tab, 0.10f, 0.10f, 0.18f, 1.0f)
        style.setColor(ImGuiCol.TabHovered, 0.20f, 0.30f, 0.55f, 1.0f)
        style.setColor(ImGuiCol.TabSelected, 0.18f, 0.27f, 0.50f, 1.0f)
        style.setColor(ImGuiCol.CheckMark, 0.29f, 0.62f, 1.0f, 1.0f)
        style.setColor(ImGuiCol.SliderGrab, 0.29f, 0.62f, 1.0f, 1.0f)
        style.setColor(ImGuiCol.SeparatorHovered, 0.29f, 0.62f, 1.0f, 0.78f)

        style.windowRounding = 4.0f
        style.frameRounding = 2.0f
        style.grabRounding = 2.0f
        style.windowBorderSize = 1.0f
        style.frameBorderSize = 0.0f
    }
}
