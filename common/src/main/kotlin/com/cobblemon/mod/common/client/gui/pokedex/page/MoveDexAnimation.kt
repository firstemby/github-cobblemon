/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.pokedex.page

import com.bedrockk.molang.runtime.MoLangRuntime
import com.cobblemon.mod.common.api.molang.ExpressionLike
import com.cobblemon.mod.common.api.moves.MoveTemplate
import com.cobblemon.mod.common.api.moves.animations.ActionEffectTimeline
import com.cobblemon.mod.common.api.moves.animations.ActionEffects
import com.cobblemon.mod.common.api.moves.animations.keyframes.ActionEffectKeyframe
import com.cobblemon.mod.common.api.moves.animations.keyframes.AnimationActionEffectKeyframe
import com.cobblemon.mod.common.api.moves.animations.keyframes.EntityMoLangActionEffectKeyframe
import com.cobblemon.mod.common.api.moves.animations.keyframes.EntityParticlesActionEffectKeyframe
import com.cobblemon.mod.common.api.moves.animations.keyframes.EntitySoundActionEffectKeyframe
import com.cobblemon.mod.common.api.moves.animations.keyframes.ForkActionEffectKeyframe
import com.cobblemon.mod.common.api.moves.animations.keyframes.ParallelActionEffectKeyframe
import com.cobblemon.mod.common.api.moves.animations.keyframes.PauseActionEffectKeyframe
import com.cobblemon.mod.common.api.moves.animations.keyframes.SequenceActionEffectKeyframe
import com.cobblemon.mod.common.client.render.structure.StructurePreviewResources

/**
 * Reads a move's action-effect timeline and flattens it into timed cues for the MoveDex viewport.
 *
 * Deserializes the file with the same [ActionEffects] gson the battle system uses, then walks the resulting
 * typed [ActionEffectKeyframe]s, so sequence/parallel/fork nesting, molang `delay`/`pause`, conditions, and
 * `q.play_animation` overlays are interpreted exactly as in battle. Produces the cycle duration (for
 * auto-pause/loop), the timed bedrock animation cues (actor windup plus target hit/throw, where
 * `is_user == false` means the target), and the timed sound cues.
 */
object MoveDexAnimation {

    private const val DEFAULT_SECONDS = 2.0f
    private const val TAIL_SECONDS = 1.5f

    // Animation name out of a molang play_animation call: q.bedrock_stateful('seismictoss','actor'). Move-specific
    // animations live in shared files keyed "animation.<group>.<variant>"; rebuilding "<group>:<key>" lets
    // PosableModel.extractAnimation resolve them against that shared repo (not the species poser).
    private val ANIM_RE = Regex("""bedrock(?:_stateful)?\(\s*['"]([^'"]+)['"]\s*,\s*['"]([^'"]+)['"]""", RegexOption.IGNORE_CASE)
    private val SOUND_RE = Regex("""q\.sound\(\s*['"]([^'"]+)['"]""", RegexOption.IGNORE_CASE)

    /** Flattened cues for one move, computed once from the real parsed timeline. */
    private class Parsed(
        val duration: Float,
        val animations: List<Triple<Set<String>, Float, Boolean>>, // (names, timeSeconds, isTarget)
        val sounds: List<Pair<String, Float>>,                     // (soundId, timeSeconds)
        val hasEffect: Boolean                                     // false when the move has no action-effect file
    )

    private val cache = HashMap<String, Parsed>()
    private val runtime = MoLangRuntime()

    private fun parsed(move: MoveTemplate): Parsed = cache.getOrPut(move.name) {
        runCatching { compute(move) }.getOrElse { Parsed(DEFAULT_SECONDS, emptyList(), emptyList(), false) }
    }

    /** Whether the move actually ships an action effect (timeline). False means the MoveDex marks it "no animation yet". */
    fun hasActionEffect(move: MoveTemplate): Boolean = parsed(move).hasEffect

    /** Estimated cycle length in seconds (timeline end + a tail for trailing/looping particles). */
    fun durationFor(move: MoveTemplate): Float = parsed(move).duration

    /** Timed bedrock animation cues as (names, timeSeconds, isTarget). */
    fun animationEventsFor(move: MoveTemplate): List<Triple<Set<String>, Float, Boolean>> = parsed(move).animations

    /** Timed sound cues as (soundId, timeSeconds), sorted by time. */
    fun soundsFor(move: MoveTemplate): List<Pair<String, Float>> = parsed(move).sounds

    private fun compute(move: MoveTemplate): Parsed {
        val resource = "data/cobblemon/action_effects/moves/${move.name}.json"
        val stream = StructurePreviewResources.openStream(resource) ?: return Parsed(DEFAULT_SECONDS, emptyList(), emptyList(), false)
        val timeline = stream.use { ActionEffects.gson.fromJson(it.reader(), ActionEffectTimeline::class.java) }
            ?: return Parsed(DEFAULT_SECONDS, emptyList(), emptyList(), false)

        val anims = ArrayList<Triple<Set<String>, Float, Boolean>>()
        val sounds = ArrayList<Pair<String, Float>>()
        val end = walk(timeline.timeline, 0f, anims, sounds)
        return Parsed(
            duration = (end + TAIL_SECONDS).coerceIn(1.0f, 10.0f),
            animations = anims.sortedBy { it.second },
            sounds = sounds.sortedBy { it.second },
            hasEffect = timeline.timeline.isNotEmpty()
        )
    }

    private fun resolve(e: ExpressionLike): Float = runCatching { e.resolveFloat(runtime) }.getOrDefault(0f).let { if (it.isFinite()) it else 0f }

    private fun isTarget(cond: ExpressionLike): Boolean = cond.getString().replace(" ", "").contains("is_user==false")

    /** Walks the typed timeline advancing an absolute clock exactly as the real chain/sequence/parallel/fork does. */
    private fun walk(
        keyframes: List<ActionEffectKeyframe>,
        start: Float,
        anims: MutableList<Triple<Set<String>, Float, Boolean>>,
        sounds: MutableList<Pair<String, Float>>
    ): Float {
        var clock = start
        for (kf in keyframes) {
            when (kf) {
                is PauseActionEffectKeyframe -> clock += resolve(kf.pause)
                is AnimationActionEffectKeyframe -> {
                    if (kf.animation.isNotEmpty()) anims.add(Triple(kf.animation, clock, isTarget(kf.entityCondition)))
                    clock += resolve(kf.delay)
                }
                is EntityMoLangActionEffectKeyframe -> {
                    val target = isTarget(kf.entityCondition)
                    kf.expressions.forEach { expr ->
                        ANIM_RE.findAll(expr).forEach { m ->
                            anims.add(Triple(setOf("${m.groupValues[1]}:animation.${m.groupValues[1]}.${m.groupValues[2]}"), clock, target))
                        }
                        SOUND_RE.findAll(expr).forEach { sounds.add(it.groupValues[1] to clock) }
                    }
                    clock += resolve(kf.delay)
                }
                is EntitySoundActionEffectKeyframe -> {
                    kf.sound?.let { sounds.add(it to clock) }
                    clock += resolve(kf.delay)
                }
                is EntityParticlesActionEffectKeyframe -> clock += resolve(kf.delay) // particles handled by the scene
                is SequenceActionEffectKeyframe -> clock = walk(kf.keyframes, clock, anims, sounds)
                is ParallelActionEffectKeyframe -> {
                    var maxEnd = clock
                    kf.keyframes.forEach { maxEnd = maxOf(maxEnd, walk(listOf(it), clock, anims, sounds)) }
                    clock = maxEnd
                }
                is ForkActionEffectKeyframe -> walk(kf.ifTrue, clock, anims, sounds) // the hit path
                else -> {} // add_holds / remove_holds / interrupt markers: no time, no cue
            }
        }
        return clock
    }
}
