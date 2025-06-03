/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.debug.riding

import com.cobblemon.mod.common.CobblemonNetwork
import com.cobblemon.mod.common.api.riding.RidingStyle
import com.cobblemon.mod.common.api.riding.stats.RidingStat
import com.cobblemon.mod.common.api.text.text
import com.cobblemon.mod.common.client.gui.CobblemonRenderable
import com.cobblemon.mod.common.client.render.drawScaledText
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.net.messages.server.debug.ServerboundUpdateRidingStatRangePacket
import com.cobblemon.mod.common.net.messages.server.debug.ServerboundUpdateRidingStatsPacket
import com.cobblemon.mod.common.util.lang
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractSliderButton
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import net.minecraft.util.Mth
import java.awt.Color

class RidingStatsDebugGUI(val vehicle: PokemonEntity) : Screen(lang("ui.debug.riding_stats")), CobblemonRenderable {

    var ridingStyle: RidingStyle = RidingStyle.LAND

    val changeRidingStyle: Button

    val speedSlider: SettingsSlider
    val accelerationSlider: SettingsSlider
    val skillSlider: SettingsSlider
    val jumpSlider: SettingsSlider
    val staminaSlider: SettingsSlider

    val minSpeedInput: RidingStatInputWidget
    val maxSpeedInput: RidingStatInputWidget
    val minAccelerationInput: RidingStatInputWidget
    val maxAccelerationInput: RidingStatInputWidget
    val minSkillInput: RidingStatInputWidget
    val maxSkillInput: RidingStatInputWidget
    val minJumpInput: RidingStatInputWidget
    val maxJumpInput: RidingStatInputWidget
    val minStaminaInput: RidingStatInputWidget
    val maxStaminaInput: RidingStatInputWidget

    init {
        ridingStyle = vehicle.ifRidingAvailableSupply(RidingStyle.LAND) { behaviour, settings, state ->
            behaviour.getRidingStyle(settings, state)
        }
        changeRidingStyle = addRenderableWidget(
            Button.builder("Riding Style: $ridingStyle".text()) { button ->
                ridingStyle = when (ridingStyle) {
                    RidingStyle.LAND -> RidingStyle.LIQUID
                    RidingStyle.LIQUID -> RidingStyle.AIR
                    RidingStyle.AIR -> RidingStyle.LAND
                }
                refresh()
            }.bounds(10, 10, getScaledWidth() - 20, 20).build()
        )
        speedSlider = addRenderableWidget(SettingsSlider(10, 60, 200, "Speed".text(), 0, 0f, 1f))
        accelerationSlider = addRenderableWidget(SettingsSlider(10, 90, 200, "Acceleration".text(), 0, 0f, 1f))
        skillSlider = addRenderableWidget(SettingsSlider(10, 120, 200, "Skill".text(), 0, 0f, 1f))
        jumpSlider = addRenderableWidget(SettingsSlider(10, 150, 200, "Jump".text(), 0, 0f, 1f))
        staminaSlider = addRenderableWidget(SettingsSlider(10, 180, 200, "Stamina".text(), 0, 0f, 1f))
        addRenderableWidget(
            Button.builder("Save".text()) { button ->
                saveStats()
            }.bounds(10, 210, 200, 20).build()
        )

        minSpeedInput = addRenderableWidget(RidingStatInputWidget(getScaledWidth() - 210, 60, "".text()))
        maxSpeedInput = addRenderableWidget(RidingStatInputWidget(getScaledWidth() - 100, 60, "".text()))
        minAccelerationInput = addRenderableWidget(RidingStatInputWidget(getScaledWidth() - 210, 90, "".text()))
        maxAccelerationInput = addRenderableWidget(RidingStatInputWidget(getScaledWidth() - 100, 90, "".text()))
        minSkillInput = addRenderableWidget(RidingStatInputWidget(getScaledWidth() - 210, 120, "".text()))
        maxSkillInput = addRenderableWidget(RidingStatInputWidget(getScaledWidth() - 100, 120, "".text()))
        minJumpInput = addRenderableWidget(RidingStatInputWidget(getScaledWidth() - 210, 150, "".text()))
        maxJumpInput = addRenderableWidget(RidingStatInputWidget(getScaledWidth() - 100, 150, "".text()))
        minStaminaInput = addRenderableWidget(RidingStatInputWidget(getScaledWidth() - 210, 180, "".text()))
        maxStaminaInput = addRenderableWidget(RidingStatInputWidget(getScaledWidth() - 100, 180, "".text()))

        addRenderableWidget(
            Button.builder("Save".text()) { button ->
                saveStatRanges()
            }.bounds(getScaledWidth() - 210, 220, 200, 20).build()
        )

        refresh()
    }

    fun refresh() {
        changeRidingStyle.message = "Riding Style: $ridingStyle".text()
        updateElementsForStat(RidingStat.SPEED, speedSlider, minSpeedInput, maxSpeedInput)
        updateElementsForStat(RidingStat.ACCELERATION, accelerationSlider, minAccelerationInput, maxAccelerationInput)
        updateElementsForStat(RidingStat.SKILL, skillSlider, minSkillInput, maxSkillInput)
        updateElementsForStat(RidingStat.JUMP, jumpSlider, minJumpInput, maxJumpInput)
        updateElementsForStat(RidingStat.STAMINA, staminaSlider, minStaminaInput, maxStaminaInput)
    }

    fun saveStats() {
        val speed = speedSlider.currentValue
        val acceleration = accelerationSlider.currentValue
        val skill = skillSlider.currentValue
        val jump = jumpSlider.currentValue
        val stamina = staminaSlider.currentValue

        vehicle.overrideRideStat(ridingStyle, RidingStat.SPEED, speed.toDouble())
        vehicle.overrideRideStat(ridingStyle, RidingStat.ACCELERATION, acceleration.toDouble())
        vehicle.overrideRideStat(ridingStyle, RidingStat.SKILL, skill.toDouble())
        vehicle.overrideRideStat(ridingStyle, RidingStat.JUMP, jump.toDouble())
        vehicle.overrideRideStat(ridingStyle, RidingStat.STAMINA, stamina.toDouble())

        CobblemonNetwork.sendToServer(
            ServerboundUpdateRidingStatsPacket(
                vehicle.id,
                ridingStyle,
                speed.toDouble(),
                acceleration.toDouble(),
                skill.toDouble(),
                jump.toDouble(),
                stamina.toDouble()
            )
        )
        refresh()
    }

    fun saveStatRanges() {
        val minSpeed = minSpeedInput.value.toIntOrNull() ?: getRidingStatRange(RidingStat.SPEED).first
        val maxSpeed = maxSpeedInput.value.toIntOrNull() ?: getRidingStatRange(RidingStat.SPEED).first
        val minAcceleration = minAccelerationInput.value.toIntOrNull() ?: getRidingStatRange(RidingStat.ACCELERATION).first
        val maxAcceleration = maxAccelerationInput.value.toIntOrNull() ?: getRidingStatRange(RidingStat.ACCELERATION).last
        val minSkill = minSkillInput.value.toIntOrNull() ?: getRidingStatRange(RidingStat.SKILL).first
        val maxSkill = maxSkillInput.value.toIntOrNull() ?: getRidingStatRange(RidingStat.SKILL).last
        val minJump = minJumpInput.value.toIntOrNull() ?: getRidingStatRange(RidingStat.JUMP).first
        val maxJump = maxJumpInput.value.toIntOrNull() ?: getRidingStatRange(RidingStat.JUMP).last
        val minStamina = minStaminaInput.value.toIntOrNull() ?: getRidingStatRange(RidingStat.STAMINA).first
        val maxStamina = maxStaminaInput.value.toIntOrNull() ?: getRidingStatRange(RidingStat.STAMINA).last

        if (minSpeed < maxSpeed) {
            vehicle.rideProp.stats[RidingStat.SPEED]?.ranges?.put(ridingStyle, minSpeed..maxSpeed)
        }
        if (minAcceleration < maxAcceleration) {
            vehicle.rideProp.stats[RidingStat.ACCELERATION]?.ranges?.put(ridingStyle, minAcceleration..maxAcceleration)
        }
        if (minSkill < maxSkill) {
            vehicle.rideProp.stats[RidingStat.SKILL]?.ranges?.put(ridingStyle, minSkill..maxSkill)
        }
        if (minJump < maxJump) {
            vehicle.rideProp.stats[RidingStat.JUMP]?.ranges?.put(ridingStyle, minJump..maxJump)
        }
        if (minStamina < maxStamina) {
            vehicle.rideProp.stats[RidingStat.STAMINA]?.ranges?.put(ridingStyle, minStamina..maxStamina)
        }

        CobblemonNetwork.sendToServer(
            ServerboundUpdateRidingStatRangePacket(
                vehicle.id,
                ridingStyle,
                minSpeed,
                maxSpeed,
                minAcceleration,
                maxAcceleration,
                minSkill,
                maxSkill,
                minJump,
                maxJump,
                minStamina,
                maxStamina
            )
        )
        refresh()
    }

    fun updateElementsForStat(
        ridingStat: RidingStat,
        slider: SettingsSlider,
        minInput: RidingStatInputWidget,
        maxInput: RidingStatInputWidget
    ) {
        val range = getRidingStatRange(ridingStat)

        slider.update(getRidingStat(ridingStat), range.first.toFloat(), range.last.toFloat())
        minInput.setHint("Min: ${range.first}".text())
        maxInput.setHint("Max: ${range.last}".text())
    }

    fun getRidingStat(ridingStat: RidingStat): Int {
        return vehicle.getRawRideStat(ridingStat, ridingStyle).toInt()
    }

    fun getRidingStatRange(ridingStat: RidingStat): IntRange {
        return vehicle.rideProp.stats[ridingStat]?.ranges?.get(ridingStyle) ?: 0..0
    }

    fun getScaledWidth() = Minecraft.getInstance().window.guiScaledWidth

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        guiGraphics.fill(0, 0, width, height, Color(0, 0, 0, 100).rgb)
        super.render(guiGraphics, mouseX, mouseY, partialTick)
        drawScaledText(
            context = guiGraphics,
            text = "Pokemon Stats Editor".text(),
            x = 10,
            y = 40,
        )

        drawScaledText(
            context = guiGraphics,
            text = "Species Stat Range Editor".text(),
            x = getScaledWidth() - 210,
            y = 40,
        )

        drawScaledText(
            context = guiGraphics,
            text = "Speed".text(),
            x = getScaledWidth() - 285,
            y = 70,
        )

        drawScaledText(
            context = guiGraphics,
            text = "Acceleration".text(),
            x = getScaledWidth() - 285,
            y = 100,
        )

        drawScaledText(
            context = guiGraphics,
            text = "Skill".text(),
            x = getScaledWidth() - 285,
            y = 130,
        )

        drawScaledText(
            context = guiGraphics,
            text = "Jump".text(),
            x = getScaledWidth() - 285,
            y = 160,
        )

        drawScaledText(
            context = guiGraphics,
            text = "Stamina".text(),
            x = getScaledWidth() - 285,
            y = 190,
        )
    }

}

class SettingsSlider(x: Int, y: Int, width: Int, val label: Component, value: Int, private var minValue: Float, private var maxValue: Float) :
    AbstractSliderButton(x, y, width, 20, CommonComponents.EMPTY, 0.0) {

    var currentValue: Int
        get() = ((this.value * (maxValue - minValue)) + minValue).toInt()
        set(value) {
            this.value = ((Mth.clamp(value.toFloat(), minValue, maxValue) - minValue) / (maxValue - minValue)).toDouble()
        }

    init {
        currentValue = value
    }

    fun update(value: Int, min: Float, max: Float) {
        minValue = min
        maxValue = max
        currentValue = value
        refresh()
    }

    override fun applyValue() {}

    fun refresh() {
        this.updateMessage()
    }

    override fun updateMessage() {
        this.message = CommonComponents.optionNameValue(label, "$currentValue".text())
    }
}

class RidingStatInputWidget(x: Int, y: Int, val hint: Component) : EditBox(
    Minecraft.getInstance().font,
    x,
    y,
    90,
    30,
    Component.literal("Enter command")
) {
    init {
        setHint(hint)
    }
}
