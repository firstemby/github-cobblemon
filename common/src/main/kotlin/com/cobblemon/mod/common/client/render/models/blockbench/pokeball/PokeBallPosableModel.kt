/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.models.blockbench.pokeball

import com.cobblemon.mod.common.client.render.models.blockbench.PosableModel
import com.cobblemon.mod.common.client.render.models.blockbench.frame.ModelFrame
import com.cobblemon.mod.common.client.render.models.blockbench.pose.Bone
import net.minecraft.client.model.geom.ModelPart

open class PokeBallPosableModel(root: Bone) : PosableModel(root), ModelFrame {
    @Transient
    override val rootPart =
        (root as ModelPart)
            .children.entries
            .first()
            .let { root.registerChildWithAllChildren(it.key) }
}
