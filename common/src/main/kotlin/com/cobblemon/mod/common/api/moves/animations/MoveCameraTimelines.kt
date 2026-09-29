/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.moves.animations

import com.cobblemon.mod.common.api.data.JsonDataRegistry
import com.cobblemon.mod.common.api.reactive.SimpleObservable
import com.cobblemon.mod.common.util.cobblemonResource
import com.google.gson.reflect.TypeToken
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.packs.PackType

object MoveCameraTimelines : JsonDataRegistry<ActionEffectTimeline> {
    override val id: ResourceLocation = cobblemonResource("move_camera_timelines")
    override val type: PackType = PackType.SERVER_DATA
    override val observable = SimpleObservable<MoveCameraTimelines>()
    override val gson = ActionEffects.gson
    override val typeToken: TypeToken<ActionEffectTimeline> = TypeToken.get(ActionEffectTimeline::class.java)
    override val resourcePath = "action_effects/moves/camera"

    private var cameraTimelines: Map<ResourceLocation, ActionEffectTimeline> = emptyMap()

    override fun reload(data: Map<ResourceLocation, ActionEffectTimeline>) {
        cameraTimelines = data.toMap()
        observable.emit(this)
    }

    override fun sync(player: ServerPlayer) {}

    fun getCameraTimeline(moveId: ResourceLocation): ActionEffectTimeline? {
        val path = "${moveId.path}_camera"
        return cameraTimelines[ResourceLocation.fromNamespaceAndPath(moveId.namespace, path)]
            ?: cameraTimelines[cobblemonResource(path)]
            ?: cameraTimelines[cobblemonResource("generic_move_camera")]
    }
}
