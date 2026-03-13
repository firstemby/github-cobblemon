/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.molang.function

import com.bedrockk.molang.runtime.MoParams
import net.minecraft.core.Holder
import net.minecraft.world.level.block.Block

object BlockMoLangFunctions: AbstractMoLangFunctionHolder<Holder<Block>>() {
    override fun Holder<Block>.moLangFunctions(): MutableMap<String, (MoParams) -> Any> {
        return mutableMapOf()
    }
}
