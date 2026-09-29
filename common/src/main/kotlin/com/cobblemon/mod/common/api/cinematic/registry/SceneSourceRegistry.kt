/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic.registry

import com.cobblemon.mod.common.api.cinematic.model.sources.FovSource
import com.cobblemon.mod.common.api.cinematic.model.sources.PositionSource
import com.cobblemon.mod.common.api.cinematic.model.sources.RollSource
import com.cobblemon.mod.common.api.cinematic.model.sources.RotationSource
import com.cobblemon.mod.common.api.cinematic.model.sources.ShotModifier
import com.cobblemon.mod.common.api.cinematic.model.tracks.ActorTrack
import com.google.gson.JsonObject

/** Registration is single-threaded at initialization; subsequent lookups are read-only. */
object SceneSourceRegistry {

    private val positionSources = mutableMapOf<String, (JsonObject) -> PositionSource>()
    private val rotationSources = mutableMapOf<String, (JsonObject) -> RotationSource>()
    private val fovSources = mutableMapOf<String, (JsonObject) -> FovSource>()
    private val rollSources = mutableMapOf<String, (JsonObject) -> RollSource>()
    private val modifiers = mutableMapOf<String, (JsonObject) -> ShotModifier>()
    private val actorTracks = mutableMapOf<String, (JsonObject) -> ActorTrack>()

    fun registerPositionSource(type: String, factory: (JsonObject) -> PositionSource) {
        positionSources[type] = factory
    }

    fun registerRotationSource(type: String, factory: (JsonObject) -> RotationSource) {
        rotationSources[type] = factory
    }

    fun registerFovSource(type: String, factory: (JsonObject) -> FovSource) {
        fovSources[type] = factory
    }

    fun registerRollSource(type: String, factory: (JsonObject) -> RollSource) {
        rollSources[type] = factory
    }

    fun registerModifier(type: String, factory: (JsonObject) -> ShotModifier) {
        modifiers[type] = factory
    }

    fun registerActorTrack(type: String, factory: (JsonObject) -> ActorTrack) {
        actorTracks[type] = factory
    }

    fun deserializePositionSource(type: String, obj: JsonObject): PositionSource? =
        positionSources[type]?.invoke(obj)

    fun deserializeRotationSource(type: String, obj: JsonObject): RotationSource? =
        rotationSources[type]?.invoke(obj)

    fun deserializeFovSource(type: String, obj: JsonObject): FovSource? =
        fovSources[type]?.invoke(obj)

    fun deserializeRollSource(type: String, obj: JsonObject): RollSource? =
        rollSources[type]?.invoke(obj)

    fun deserializeModifier(type: String, obj: JsonObject): ShotModifier? =
        modifiers[type]?.invoke(obj)

    fun deserializeActorTrack(type: String, obj: JsonObject): ActorTrack? =
        actorTracks[type]?.invoke(obj)

    fun clear() {
        positionSources.clear()
        rotationSources.clear()
        fovSources.clear()
        rollSources.clear()
        modifiers.clear()
        actorTracks.clear()
    }
}
