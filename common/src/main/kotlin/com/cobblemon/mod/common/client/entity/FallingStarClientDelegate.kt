/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.entity

import com.cobblemon.mod.common.api.entity.EntitySideDelegate
import com.cobblemon.mod.common.client.render.models.blockbench.PosableState
import com.cobblemon.mod.common.entity.projectile.FallingStarEntity

class FallingStarClientDelegate : EntitySideDelegate<FallingStarEntity>, PosableState() {
    lateinit var currentEntity: FallingStarEntity
    override val schedulingTracker
        get() = getEntity().schedulingTracker
    override fun getEntity() = currentEntity

    override fun initialize(entity: FallingStarEntity) {
        super.initialize(entity)
        this.currentEntity = entity
        this.age = entity.tickCount
    }

    override fun tick(entity: FallingStarEntity) {
        super.tick(entity)
        updateLocatorPosition(entity.position())
        incrementAge(entity)
    }

    override fun updatePartialTicks(partialTicks: Float) {
        this.currentPartialTicks = partialTicks
    }
}
