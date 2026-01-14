package com.cobblemon.mod.common.ost

import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvent

/**
 * Event to signal a transition in battle music. When received by the client, will either replace the currently playing
 * battle music with a new instance, or pause and temporarily switches from the current instance to play a [soundEvent].
 *
 * @author Segfault Guy
 * @since December 16, 2925
 */
data class BattleMusicCue(
    /** The identifier of this cue. */
    val id: ResourceLocation,
    /** The track or sound effect to play. */
    val soundEvent: SoundEvent,
    /** Whether the [soundEvent] should replace and become the main track. */
    val replace: Boolean,
    /** Determines which cue's [soundEvent] plays if multiple cues are scheduled at once. Priority is lost when cue becomes
     * the main track. */
    val priority: Int,
    /** Time in seconds before the [soundEvent] starts playing. */
    val delay: Int,
    /** If the currently playing track should fade out, and the [soundEvent] should fade in. */
    val fade: Boolean = false
)