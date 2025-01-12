/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.behaviour

import com.cobblemon.mod.common.client.gui.npc.NPCEditorButton
import com.cobblemon.mod.common.net.messages.server.behaviour.SetEntityBehaviourPacket
import com.cobblemon.mod.common.util.lang
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.LivingEntity

class BehaviourEditorScreen(
    val entity: LivingEntity,
    val appliedPresets: MutableSet<ResourceLocation>,
) : Screen(Component.literal("Behaviour Editor")) {
    val unadded: BehaviourOptionsList = addRenderableWidget(
        BehaviourOptionsList(
            parent = this,
            left = 50,
            entity = entity,
            appliedPresets = appliedPresets,
            addingMenu = true
        )
    )
    val added: BehaviourOptionsList = addRenderableWidget(
        BehaviourOptionsList(
            parent = this,
            left = 220,
            entity = entity,
            appliedPresets = appliedPresets,
            addingMenu = false
        )
    )

    init {
        addRenderableWidget(
            NPCEditorButton(
                buttonX = 400F,
                buttonY = 201F,
                label = lang("ui.generic.save"),
                alignRight = true
            ) {
                // Send packet for updating behaviours of the entity
                // expect that it will reopen whichever GUI makes sense afterwards
                SetEntityBehaviourPacket(entity.id, appliedPresets.toSet()).sendToServer()
            }
        )
    }

    fun add(resourceLocation: ResourceLocation) {
        appliedPresets.add(resourceLocation)
        unadded.removeEntry(resourceLocation)
        added.addEntry(resourceLocation)
    }

    fun remove(resourceLocation: ResourceLocation) {
        appliedPresets.remove(resourceLocation)
        added.removeEntry(resourceLocation)
        unadded.addEntry(resourceLocation)
    }
}