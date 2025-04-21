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
import com.cobblemon.mod.common.api.riding.behaviour.types.composite.CompositeBehaviour
import com.cobblemon.mod.common.api.riding.behaviour.types.composite.CompositeSettings
import com.cobblemon.mod.common.api.riding.behaviour.types.composite.strategies.FallCompositeSettings
import com.cobblemon.mod.common.api.riding.behaviour.types.composite.strategies.FallStrategy
import com.cobblemon.mod.common.api.riding.behaviour.types.composite.strategies.JumpStrategy
import com.cobblemon.mod.common.api.riding.behaviour.types.composite.strategies.RunStrategy
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
    val types: MutableMap<ResourceLocation, Class<out RidingBehaviourSettings>> = mutableMapOf(BirdAirBehaviour.KEY to BirdAirSettings::class.java,
        DolphinBehaviour.KEY to DolphinSettings::class.java,
        GenericLandBehaviour.KEY to GenericLandSettings::class.java,
        GenericSwimBehaviour.KEY to GenericSwimSettings::class.java,
        GliderAirBehaviour.KEY to GliderAirSettings::class.java,
        HelicopterBehaviour.KEY to HelicopterSettings::class.java,
        JetAirBehaviour.KEY to JetAirSettings::class.java,
        SwimDashBehaviour.KEY to SwimDashSettings::class.java,
        VehicleLandBehaviour.KEY to VehicleLandSettings::class.java,
        HoverBehaviour.KEY to HoverSettings::class.java,
        CompositeBehaviour.KEY to CompositeSettings::class.java,

        /*
         Strategy registration. if you do not register a strategy here, it will not be deserialized.
         Register to CompositeSettings if you do not need to define a subclass.
         */
        FallStrategy.key to FallCompositeSettings::class.java,
        JumpStrategy.key to CompositeSettings::class.java,
        RunStrategy.key to CompositeSettings::class.java,
    )

    override fun deserialize(element: JsonElement, type: Type, context: JsonDeserializationContext): RidingBehaviourSettings {
        val root = element.asJsonObject
        val key = root.get("key").asString
        val keyIdentifier = key.asIdentifierDefaultingNamespace()
        if (keyIdentifier == CompositeBehaviour.KEY) {
            val strategy = root.get("transitionStrategy").asString
            val strategyIdentifier = strategy.asIdentifierDefaultingNamespace()
            val behaviourType = types[strategyIdentifier] ?: throw IllegalArgumentException("Unknown strategy for composite behaviour: $strategy")
            val settings: RidingBehaviourSettings = context.deserialize(root, behaviourType)
            return settings
        }
        else {
            val behaviourType = types[keyIdentifier] ?: throw IllegalArgumentException("Unknown behaviour: $key")
            val settings: RidingBehaviourSettings = context.deserialize(element, behaviourType)
            return settings
        }
    }
}
