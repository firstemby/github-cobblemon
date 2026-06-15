/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.pokedex.page

import com.cobblemon.mod.common.api.moves.MoveTemplate
import com.cobblemon.mod.common.client.render.structure.StructurePreviewResources
import com.google.gson.JsonParser

/**
 * Reads a move's action-effect timeline (`data/cobblemon/action_effects/moves/<name>.json`) to estimate
 * how long one playthrough of the move runs, so the MoveDex viewport can auto-pause at the end of a
 * cycle. Keyframes run sequentially, so the cycle length is the SUM of every keyframe's `delay`/`pause`
 * (the running clock), plus a tail to let trailing/looping particle effects (and event-spawned children
 * like heal sparkles) finish. Falls back to a default when no timeline exists.
 */
object MoveDexAnimation {

    private const val DEFAULT_SECONDS = 2.0f
    private const val TAIL_SECONDS = 1.5f

    private val cache = HashMap<String, Float>()

    /** Estimated cycle length in seconds for the move's action effect. */
    fun durationFor(move: MoveTemplate): Float = cache.getOrPut(move.name) {
        runCatching { compute(move) }.getOrDefault(DEFAULT_SECONDS)
    }

    private fun compute(move: MoveTemplate): Float {
        val resource = "data/cobblemon/action_effects/moves/${move.name}.json"
        val stream = StructurePreviewResources.openStream(resource) ?: return DEFAULT_SECONDS
        val json = stream.use { JsonParser.parseReader(it.reader()) }.takeIf { it.isJsonObject }?.asJsonObject
            ?: return DEFAULT_SECONDS
        val timeline = json.getAsJsonArray("timeline") ?: return DEFAULT_SECONDS

        // Sum the running clock the same way the scene does: each keyframe holds the next one back by its
        // `pause` (pause keyframes) or `delay` (everything else), so the total is their sum.
        var clock = 0f
        for (element in timeline) {
            val obj = element.takeIf { it.isJsonObject }?.asJsonObject ?: continue
            val type = obj.get("type")?.takeIf { it.isJsonPrimitive }?.asString
            clock += if (type == "pause") {
                obj.get("pause")?.takeIf { it.isJsonPrimitive }?.asFloat ?: 0f
            } else {
                obj.get("delay")?.takeIf { it.isJsonPrimitive }?.asFloat ?: 0f
            }
        }
        return (clock + TAIL_SECONDS).coerceIn(1.0f, 10.0f)
    }

    private val animCache = HashMap<String, Set<String>>()

    /**
     * The bedrock animation names the USER plays for this move (from the action effect's first `animation`
     * keyframe that isn't target-only), so the swapped-in Pokémon model can play the real move animation
     * just like battle. Empty if the move has none. Recurses into sequence/parallel/fork containers.
     */
    fun userAnimationsFor(move: MoveTemplate): Set<String> = animCache.getOrPut(move.name) {
        runCatching { computeAnimations(move) }.getOrDefault(emptySet())
    }

    private fun computeAnimations(move: MoveTemplate): Set<String> {
        val resource = "data/cobblemon/action_effects/moves/${move.name}.json"
        val stream = StructurePreviewResources.openStream(resource) ?: return emptySet()
        val json = stream.use { JsonParser.parseReader(it.reader()) }.takeIf { it.isJsonObject }?.asJsonObject ?: return emptySet()
        val timeline = json.getAsJsonArray("timeline") ?: return emptySet()

        fun search(arr: com.google.gson.JsonArray): Set<String>? {
            for (element in arr) {
                val obj = element.takeIf { it.isJsonObject }?.asJsonObject ?: continue
                when (obj.get("type")?.takeIf { it.isJsonPrimitive }?.asString) {
                    "animation" -> {
                        // Skip target-only animations (e.g. "recoil"); we want the user's move animation.
                        val cond = obj.get("entityCondition")?.takeIf { it.isJsonPrimitive }?.asString?.replace(" ", "") ?: ""
                        if (!cond.contains("is_user==false")) {
                            val names = obj.getAsJsonArray("animation")?.mapNotNull { it.takeIf { e -> e.isJsonPrimitive }?.asString }
                            if (!names.isNullOrEmpty()) return names.toSet()
                        }
                    }
                    "sequence", "parallel", "fork" -> obj.getAsJsonArray("keyframes")?.let { search(it)?.let { found -> return found } }
                }
            }
            return null
        }
        return search(timeline) ?: emptySet()
    }

    // Pulls BOTH args out of a molang play_animation call, e.g. q.bedrock_stateful('seismictoss', 'actor').
    // Move-specific animations live in SHARED files (generic/animations/moves/<group>.animation.json) keyed
    // as "animation.<group>.<variant>", NOT in the species' own poser — so the actor would only ever play its
    // generic physical/special, never the move's real overlay (the Seismic Toss throw, Body Slam squish, etc.).
    // We rebuild the "group:full-key" form that PosableModel.extractAnimation resolves against that shared repo.
    private val ANIM_RE = Regex("""bedrock(?:_stateful)?\(\s*['"]([^'"]+)['"]\s*,\s*['"]([^'"]+)['"]""", RegexOption.IGNORE_CASE)

    private val animEventCache = HashMap<String, List<Triple<Set<String>, Float, Boolean>>>()

    /**
     * The move's bedrock animation cues as (animationNames, timeSeconds, isTarget): the user's windup and the
     * TARGET's damage/recoil animations, each timed by the running clock. `isTarget` = the keyframe is for the
     * defending Pokémon (`is_user == false`). Lets the target play its hurt animation when the hit lands.
     */
    fun animationEventsFor(move: MoveTemplate): List<Triple<Set<String>, Float, Boolean>> = animEventCache.getOrPut(move.name) {
        runCatching { computeAnimEvents(move) }.getOrDefault(emptyList())
    }

    private fun computeAnimEvents(move: MoveTemplate): List<Triple<Set<String>, Float, Boolean>> {
        val resource = "data/cobblemon/action_effects/moves/${move.name}.json"
        val stream = StructurePreviewResources.openStream(resource) ?: return emptyList()
        val json = stream.use { JsonParser.parseReader(it.reader()) }.takeIf { it.isJsonObject }?.asJsonObject ?: return emptyList()
        val timeline = json.getAsJsonArray("timeline") ?: return emptyList()
        val out = ArrayList<Triple<Set<String>, Float, Boolean>>()

        fun delayOf(o: com.google.gson.JsonObject) = o.get("delay")?.takeIf { it.isJsonPrimitive }?.asFloat ?: 0f

        fun walk(arr: com.google.gson.JsonArray, start: Float): Float {
            var clock = start
            for (element in arr) {
                val obj = element.takeIf { it.isJsonObject }?.asJsonObject ?: continue
                when (obj.get("type")?.takeIf { it.isJsonPrimitive }?.asString) {
                    "pause" -> clock += obj.get("pause")?.takeIf { it.isJsonPrimitive }?.asFloat ?: 0f
                    "animation" -> {
                        val names = obj.getAsJsonArray("animation")?.mapNotNull { it.takeIf { e -> e.isJsonPrimitive }?.asString }?.toSet()
                        val cond = obj.get("entityCondition")?.takeIf { it.isJsonPrimitive }?.asString?.replace(" ", "") ?: ""
                        if (!names.isNullOrEmpty()) out.add(Triple(names, clock, cond.contains("is_user==false")))
                        clock += delayOf(obj)
                    }
                    "entity_molang" -> {
                        // Many moves play their squash/stretch/translate animation via molang rather than an
                        // `animation` keyframe, e.g. q.play_animation(q.bedrock_stateful('squish','actor')). Pull the
                        // animation name out so the actor plays it and the (block) target can react to a target cue.
                        val cond = obj.get("entityCondition")?.takeIf { it.isJsonPrimitive }?.asString?.replace(" ", "") ?: ""
                        val isTarget = cond.contains("is_user==false")
                        obj.getAsJsonArray("expressions")?.forEach { e ->
                            e.takeIf { it.isJsonPrimitive }?.asString?.let { expr ->
                                ANIM_RE.findAll(expr).forEach { m ->
                                    val group = m.groupValues[1]; val variant = m.groupValues[2]
                                    // "group:animation.group.variant" — resolved by extractAnimation against the shared repo.
                                    out.add(Triple(setOf("$group:animation.$group.$variant"), clock, isTarget))
                                }
                            }
                        }
                        clock += delayOf(obj)
                    }
                    "sequence" -> { obj.getAsJsonArray("keyframes")?.let { clock = walk(it, clock) }; clock += delayOf(obj) }
                    "parallel", "fork" -> {
                        var maxEnd = clock
                        obj.getAsJsonArray("keyframes")?.forEach { val a = com.google.gson.JsonArray(); a.add(it); val e = walk(a, clock); if (e > maxEnd) maxEnd = e }
                        clock = maxEnd + delayOf(obj)
                    }
                    else -> clock += delayOf(obj)
                }
            }
            return clock
        }
        walk(timeline, 0f)
        return out.sortedBy { it.second }
    }

    private val soundCache = HashMap<String, List<Pair<String, Float>>>()
    private val SOUND_RE = Regex("""q\.sound\(\s*['"]([^'"]+)['"]""", RegexOption.IGNORE_CASE)

    /**
     * The move's sound cues as (soundId, timeSeconds), from `entity_sound` keyframes and `q.sound('...')` calls
     * in `entity_molang` keyframes, timed by the same running clock as the particles. Sorted by time.
     */
    fun soundsFor(move: MoveTemplate): List<Pair<String, Float>> = soundCache.getOrPut(move.name) {
        runCatching { computeSounds(move) }.getOrDefault(emptyList())
    }

    private fun computeSounds(move: MoveTemplate): List<Pair<String, Float>> {
        val resource = "data/cobblemon/action_effects/moves/${move.name}.json"
        val stream = StructurePreviewResources.openStream(resource) ?: return emptyList()
        val json = stream.use { JsonParser.parseReader(it.reader()) }.takeIf { it.isJsonObject }?.asJsonObject ?: return emptyList()
        val timeline = json.getAsJsonArray("timeline") ?: return emptyList()
        val out = ArrayList<Pair<String, Float>>()

        fun delayOf(o: com.google.gson.JsonObject) = o.get("delay")?.takeIf { it.isJsonPrimitive }?.asFloat ?: 0f

        fun walk(arr: com.google.gson.JsonArray, start: Float): Float {
            var clock = start
            for (element in arr) {
                val obj = element.takeIf { it.isJsonObject }?.asJsonObject ?: continue
                when (obj.get("type")?.takeIf { it.isJsonPrimitive }?.asString) {
                    "pause" -> clock += obj.get("pause")?.takeIf { it.isJsonPrimitive }?.asFloat ?: 0f
                    "entity_sound" -> {
                        obj.get("sound")?.takeIf { it.isJsonPrimitive }?.asString?.let { out.add(it to clock) }
                        clock += delayOf(obj)
                    }
                    "entity_molang" -> {
                        obj.getAsJsonArray("expressions")?.forEach { e ->
                            e.takeIf { it.isJsonPrimitive }?.asString?.let { expr ->
                                SOUND_RE.findAll(expr).forEach { out.add(it.groupValues[1] to clock) }
                            }
                        }
                        clock += delayOf(obj)
                    }
                    "sequence" -> { obj.getAsJsonArray("keyframes")?.let { clock = walk(it, clock) }; clock += delayOf(obj) }
                    "parallel", "fork" -> {
                        var maxEnd = clock
                        obj.getAsJsonArray("keyframes")?.forEach { val arr2 = com.google.gson.JsonArray(); arr2.add(it); val e = walk(arr2, clock); if (e > maxEnd) maxEnd = e }
                        clock = maxEnd + delayOf(obj)
                    }
                    else -> clock += delayOf(obj)
                }
            }
            return clock
        }
        walk(timeline, 0f)
        return out.sortedBy { it.second }
    }
}
