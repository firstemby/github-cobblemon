/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.molang.function

import com.bedrockk.molang.runtime.MoParams

interface AbstractMoLangFunctionHolder<T> {
    val holder: MutableList<(T) -> MutableMap<String, (MoParams) -> Any>>

    fun attachTo(value: T): MutableMap<String, (MoParams) -> Any> = holder.flatMap { valueObject ->
        valueObject(value).map { it.key to it.value }
    }.toMap().toMutableMap()
}

interface AbstractMoLangFunctionHolderP2<T, T2> {
    val holder: MutableList<(T, T2) -> MutableMap<String, (MoParams) -> Any>>

    fun attachTo(tFirst: T, tSecond: T2): MutableMap<String, (MoParams) -> Any> = holder.flatMap { valueObject ->
        valueObject(tFirst, tSecond).map { it.key to it.value }
    }.toMap().toMutableMap()
}
