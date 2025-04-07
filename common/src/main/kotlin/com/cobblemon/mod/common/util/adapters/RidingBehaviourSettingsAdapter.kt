/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.util.adapters

import com.cobblemon.mod.common.api.riding.behaviour.RidingBehaviourSettings
import com.cobblemon.mod.common.api.riding.behaviour.types.*
import com.cobblemon.mod.common.util.asIdentifierDefaultingNamespace
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import net.minecraft.resources.ResourceLocation
import java.lang.reflect.Type

/**
 * Adapter for deserializing [RidingBehaviourSettings] types.
 *
 * @author landonjw
 */
object RidingBehaviourSettingsAdapter : JsonDeserializer<RidingBehaviourSettings> {
    val types: MutableMap<ResourceLocation, Class<out RidingBehaviourSettings>> = mutableMapOf(
        BirdAirBehaviour.KEY to BirdAirSettings::class.java,
        DolphinBehaviour.KEY to DolphinSettings::class.java,
        FallToGlideCompositeBehaviour.KEY to FallToGlideCompositeSettings::class.java,
        GenericLandBehaviour.KEY to GenericLandSettings::class.java,
        GenericSwimBehaviour.KEY to GenericSwimSettings::class.java,
        GliderAirBehaviour.KEY to GliderAirSettings::class.java,
        HelicopterBehaviour.KEY to HelicopterSettings::class.java,
        JetAirBehaviour.KEY to JetAirSettings::class.java,
        JumpToFlightCompositeBehaviour.KEY to JumpToFlightCompositeSettings::class.java,
        RunToJetCompositeBehaviour.KEY to RunToJetCompositeSettings::class.java,
        SwimDashBehaviour.KEY to SwimDashSettings::class.java,
        VehicleLandBehaviour.KEY to VehicleLandSettings::class.java
    )

    override fun deserialize(element: JsonElement, type: Type, context: JsonDeserializationContext): RidingBehaviourSettings {
        val root = element.asJsonObject
        val key = root.get("key").asString
        val behaviourType = types[key.asIdentifierDefaultingNamespace()] ?: throw IllegalArgumentException("Unknown controller: $key")
        val settings: RidingBehaviourSettings = context.deserialize(element, behaviourType)
        return settings
    }
}
