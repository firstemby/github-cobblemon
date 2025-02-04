/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.pokemon.transformation.proxy

import com.cobblemon.mod.common.api.pokemon.transformation.Transformation
import com.cobblemon.mod.common.api.pokemon.transformation.TransformationDisplay
import com.cobblemon.mod.common.api.pokemon.transformation.TransformationLike
import com.cobblemon.mod.common.api.pokemon.transformation.controller.TransformationController

/**
 * Responsible for holding the different [TransformationController] implementations based on the logical side.
 * If you haven't already checked your side previously you can use [isClient].
 *
 * @param C The type of [TransformationDisplay] on the client side.
 * @param S The type of [Transformation] on the server side.
 * @param CI The type of [PreProcessor] on the client side.
 * @param SI The type of [PreProcessor] on the server side.
 *
 * @author Licious
 * @since June 18th, 2022
 */
interface TransformationProxy<C : TransformationDisplay, S : Transformation, CI : PreProcessor, SI : PreProcessor> {

    /**
     * Checks if the current logical side is the client.
     *
     * @return True if the current logical side is the client.
     */
    fun isClient(): Boolean

    /**
     * Returns the current [TransformationController].
     * Keep in mind this will have type erasure and as such most operations will not be possible.
     * It's recommended to work with [client] or [server] when possible.
     *
     * @return The current [TransformationController].
     */
    fun current(): TransformationController<out TransformationLike, *>

    /**
     * The client side implementation of the [TransformationController].
     *
     * @throws [ClassCastException] if called from the server side.
     *
     * @return The client side implementation of the [TransformationController] if possible.
     */
    fun client(): TransformationController<C, CI>

    /**
     * The server side implementation of the [TransformationController].
     *
     * @throws [ClassCastException] if called from the client side.
     *
     * @return The server side implementation of the [TransformationController] if possible.
     */
    fun server(): TransformationController<S, SI>

}