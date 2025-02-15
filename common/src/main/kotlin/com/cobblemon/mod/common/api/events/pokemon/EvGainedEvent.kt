/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.events.pokemon

import com.cobblemon.mod.common.api.events.Cancelable
import com.cobblemon.mod.common.api.pokemon.stats.EvSource
import com.cobblemon.mod.common.api.pokemon.stats.Stat
import com.cobblemon.mod.common.pokemon.Pokemon

/**
 * Fired during EV mutation.
 *
 * @see [Pre]
 * @see [Post]
 */
interface EvGainedEvent {

    /**
     * The [Stat] having EVs added to itself.
     */
    val stat: Stat

    /**
     * @see EvSource.pokemon
     */
    val pokemon: Pokemon get() = this.source.pokemon

    /**
     * The [EvSource] that fired this event.
     */
    val source: EvSource

    /**
     * Fired before EV mutation occurs, cancelling will cause the mutation to be flagged as not successful (returns 0).
     *
     * @property stat See [EvGainedEvent.stat].
     * @property amount The amount of EVs that will be gained.
     * @property source See [EvGainedEvent.source].
     */
    class Pre(override val stat: Stat, var amount: Int, override val source: EvSource) : EvGainedEvent, Cancelable()

    /**
     * Fired after EV mutation occurs, this is purely for notification purposes.
     *
     * @property stat See [EvGainedEvent.stat].
     * @property amount The final amount of EVs gained.
     * @property source See [EvGainedEvent.source].
     */
    class Post(override val stat: Stat, val amount: Int, override val source: EvSource) : EvGainedEvent

}