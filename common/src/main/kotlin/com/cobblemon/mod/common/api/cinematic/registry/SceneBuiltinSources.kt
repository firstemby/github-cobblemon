/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic.registry

import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.ConstraintModifier
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.CranePosition
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.DollyPosition
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.ExpressionFov
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.ExpressionVec3Position
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.FixedFov
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.FixedPosition
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.FixedRoll
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.FixedRotation
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.FollowActorPosition
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.KeyframeFov
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.KeyframePosition
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.KeyframeRoll
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.KeyframeRotation
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.LookAlongPath
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.LookAt
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.OffsetModifier
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.ShakeDerivedRoll
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.OrbitPosition
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.ShakeModifier
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.SmoothingModifier
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.SplinePosition
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.TruckPosition
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.VariableVec3Position
import com.cobblemon.mod.common.api.cinematic.model.tracks.builtin.AnimationTrack
import com.cobblemon.mod.common.api.cinematic.model.tracks.builtin.AttachTrack
import com.cobblemon.mod.common.api.cinematic.model.tracks.builtin.FollowActor
import com.cobblemon.mod.common.api.cinematic.model.tracks.builtin.KeyframeMovement
import com.cobblemon.mod.common.api.cinematic.model.tracks.builtin.LookAtTrack
import com.cobblemon.mod.common.api.cinematic.model.tracks.builtin.PoseLockTrack
import com.cobblemon.mod.common.api.cinematic.model.tracks.builtin.SplineMovement
import com.cobblemon.mod.common.api.cinematic.model.tracks.builtin.WaypointPathfinding

object SceneBuiltinSources {
    fun register() {
        SceneSourceRegistry.registerPositionSource(FixedPosition.TYPE, FixedPosition::fromJson)
        SceneSourceRegistry.registerPositionSource(KeyframePosition.TYPE, KeyframePosition::fromJson)
        SceneSourceRegistry.registerPositionSource(SplinePosition.TYPE, SplinePosition::fromJson)
        SceneSourceRegistry.registerPositionSource(OrbitPosition.TYPE, OrbitPosition::fromJson)
        SceneSourceRegistry.registerPositionSource(FollowActorPosition.TYPE, FollowActorPosition::fromJson)
        SceneSourceRegistry.registerPositionSource(DollyPosition.TYPE, DollyPosition::fromJson)
        SceneSourceRegistry.registerPositionSource(TruckPosition.TYPE, TruckPosition::fromJson)
        SceneSourceRegistry.registerPositionSource(CranePosition.TYPE, CranePosition::fromJson)
        SceneSourceRegistry.registerPositionSource(VariableVec3Position.TYPE, VariableVec3Position::fromJson)
        SceneSourceRegistry.registerPositionSource(ExpressionVec3Position.TYPE, ExpressionVec3Position::fromJson)
        SceneSourceRegistry.registerRotationSource(FixedRotation.TYPE, FixedRotation::fromJson)
        SceneSourceRegistry.registerRotationSource(LookAt.TYPE, LookAt::fromJson)
        SceneSourceRegistry.registerRotationSource(KeyframeRotation.TYPE, KeyframeRotation::fromJson)
        SceneSourceRegistry.registerRotationSource(LookAlongPath.TYPE, LookAlongPath::fromJson)
        SceneSourceRegistry.registerFovSource(FixedFov.TYPE, FixedFov::fromJson)
        SceneSourceRegistry.registerFovSource(KeyframeFov.TYPE, KeyframeFov::fromJson)
        SceneSourceRegistry.registerFovSource(ExpressionFov.TYPE, ExpressionFov::fromJson)
        SceneSourceRegistry.registerRollSource(FixedRoll.TYPE, FixedRoll::fromJson)
        SceneSourceRegistry.registerRollSource(KeyframeRoll.TYPE, KeyframeRoll::fromJson)
        SceneSourceRegistry.registerRollSource(ShakeDerivedRoll.TYPE, ShakeDerivedRoll::fromJson)
        SceneSourceRegistry.registerModifier(SmoothingModifier.TYPE, SmoothingModifier::fromJson)
        SceneSourceRegistry.registerModifier(ShakeModifier.TYPE, ShakeModifier::fromJson)
        SceneSourceRegistry.registerModifier(OffsetModifier.TYPE, OffsetModifier::fromJson)
        SceneSourceRegistry.registerModifier(ConstraintModifier.TYPE, ConstraintModifier::fromJson)
        SceneSourceRegistry.registerActorTrack(KeyframeMovement.TYPE, KeyframeMovement::fromJson)
        SceneSourceRegistry.registerActorTrack(AnimationTrack.TYPE, AnimationTrack::fromJson)
        SceneSourceRegistry.registerActorTrack(LookAtTrack.TYPE, LookAtTrack::fromJson)
        SceneSourceRegistry.registerActorTrack(SplineMovement.TYPE, SplineMovement::fromJson)
        SceneSourceRegistry.registerActorTrack(WaypointPathfinding.TYPE, WaypointPathfinding::fromJson)
        SceneSourceRegistry.registerActorTrack(FollowActor.TYPE, FollowActor::fromJson)
        SceneSourceRegistry.registerActorTrack(PoseLockTrack.TYPE, PoseLockTrack::fromJson)
        SceneSourceRegistry.registerActorTrack(AttachTrack.TYPE, AttachTrack::fromJson)
    }
}
