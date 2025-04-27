/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.riding.behaviour

import com.cobblemon.mod.common.api.riding.behaviour.types.*
import com.cobblemon.mod.common.api.riding.behaviour.types.composite.CompositeBehaviour
import net.minecraft.resources.ResourceLocation

object RidingBehaviours {
    val behaviours = mutableMapOf<ResourceLocation, RidingBehaviour<RidingBehaviourSettings, RidingBehaviourState>>()

    init {
        register(BirdAirBehaviour.KEY, BirdAirBehaviour())
        register(DolphinBehaviour.KEY, DolphinBehaviour())
        register(GenericLandBehaviour.KEY, GenericLandBehaviour())
        register(GenericSwimBehaviour.KEY, GenericSwimBehaviour())
        register(GliderAirBehaviour.KEY, GliderAirBehaviour())
        register(HelicopterBehaviour.KEY, HelicopterBehaviour())
        register(JetAirBehaviour.KEY, JetAirBehaviour())
        register(SwimDashBehaviour.KEY, SwimDashBehaviour())
        register(VehicleLandBehaviour.KEY, VehicleLandBehaviour())
        register(HoverBehaviour.KEY, HoverBehaviour())
        register(CompositeBehaviour.KEY, CompositeBehaviour())
    }

    fun register(key: ResourceLocation, behaviour: RidingBehaviour<out RidingBehaviourSettings, out RidingBehaviourState>) {
        if (behaviours.contains(key)) error("Behaviour already registered to key $key")
        behaviours[key] = RidingController(behaviour) as RidingBehaviour<RidingBehaviourSettings, RidingBehaviourState>
    }

    fun get(key: ResourceLocation): RidingBehaviour<RidingBehaviourSettings, RidingBehaviourState> {
        if (!behaviours.contains(key)) error("Behaviour not registered to key $key")
        return behaviours[key]!!
    }
}
