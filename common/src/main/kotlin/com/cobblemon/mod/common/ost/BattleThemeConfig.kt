package com.cobblemon.mod.common.ost

import net.minecraft.resources.ResourceLocation

/**
 * A configuration that dispatches a [BattleTrack] to use when playing battle music.
 *
 * @author Segfault Guy
 * @since January 6th, 2026
 */
class BattleThemeConfig(
    id: ResourceLocation,
    /** */
    override val tracks: Collection<BattleTrack>,
    /** 2nd-priority [BattleMusicCue]s that are subscribed by every track in the configuration. */
    val cues: Collection<BattleMusicCue>,
) : SoundTrackConfig(id, tracks)