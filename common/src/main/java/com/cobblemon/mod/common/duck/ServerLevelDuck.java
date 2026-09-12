/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.duck;

import net.minecraft.resources.ResourceLocation;

/**
 * Duck that facilitates getting a level key that we store on the level.
 *
 * @author dualspiral
 * @since August 30th, 2026
 */
public interface ServerLevelDuck {
    ResourceLocation cobblemon$getLevelResourceLocation();
}
