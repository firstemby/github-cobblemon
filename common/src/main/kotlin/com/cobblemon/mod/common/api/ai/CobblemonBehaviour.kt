/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai

import com.bedrockk.molang.runtime.MoLangRuntime
import com.cobblemon.mod.common.api.ai.config.BehaviourConfig
import com.cobblemon.mod.common.api.molang.ExpressionLike
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asMostSpecificMoLangValue
import com.cobblemon.mod.common.api.molang.MoLangFunctions.setup
import com.cobblemon.mod.common.api.scripting.CobblemonScripts
import com.cobblemon.mod.common.util.asTranslated
import com.cobblemon.mod.common.util.resolve
import com.cobblemon.mod.common.util.withQueryValue
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.LivingEntity

class CobblemonBehaviour(
    val name: Component = "".asTranslated(),
    val description: Component = "".asTranslated(),
    /** Some behaviours aren't really worth showing on the client, namely ones that are simply conditional bundles of other behaviours. */
    val visible: Boolean = true,
    val entityType: ResourceLocation? = null,
    val configurations: List<BehaviourConfig> = mutableListOf(),
    val undo: ExpressionLike? = null,
    val undoScript: ResourceLocation? = null,
) {
    fun canBeApplied(entity: LivingEntity) = entityType?.let { entityType == entity.type.builtInRegistryHolder().unwrapKey().get().location() } != false
    fun configure(entity: LivingEntity, behaviourConfigurationContext: BehaviourConfigurationContext) {
        configurations.forEach { it.configure(entity, behaviourConfigurationContext) }
    }

    /** Undoes anything that needs undoing once this configuration is being removed from an entity that had it before. */
    fun undo(entity: LivingEntity) {
        val runtime = MoLangRuntime().setup()
        runtime.withQueryValue("entity", entity.asMostSpecificMoLangValue())
        if (undoScript != null) {
            CobblemonScripts.run(undoScript, runtime)
        }
        if (undo != null) {
            runtime.resolve(undo)
        }
    }
}