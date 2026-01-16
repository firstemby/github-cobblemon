package com.cobblemon.mod.common.ost

import net.minecraft.resources.ResourceLocation

/**
 * A configuration that dispatches a [SoundTrack] to use when playing music.
 *
 * @author Segfault Guy
 * @since January 6th, 2026
 */
open class SoundTrackConfig(
    /** The identifier of this configuration. */
    val id: ResourceLocation,
    /** The  */
    open val tracks: Collection<SoundTrack>,
) {

    fun dispatchTrack(): BattleTrack {
        // TODO based on requirements
    }
}

// should this be templated instead?