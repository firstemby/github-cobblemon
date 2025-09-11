/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.riding.util

/**
 * Enum to hold the ranges for each riding stat tier
 *
 * @author Jackson
 * @since September 10th, 2025
 */
enum class RidingTier(val tierRange: IntRange) {
    S(80..100),
    A(55..85),
    B(30..65),
    C(10..40),
    D(0..10);
}