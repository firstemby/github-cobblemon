/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.pokedex.page

import com.bedrockk.molang.runtime.MoLangRuntime
import com.bedrockk.molang.runtime.value.DoubleValue
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.molang.ExpressionLike
import com.cobblemon.mod.common.api.molang.MoLangFunctions.setup
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
import com.cobblemon.mod.common.api.snowstorm.ParticleCameraModeType
import com.cobblemon.mod.common.api.snowstorm.ParticleMaterial
import com.cobblemon.mod.common.api.snowstorm.ParticleMaterials
import com.cobblemon.mod.common.client.ClientMoLangFunctions.setupClient
import com.cobblemon.mod.common.client.particle.BedrockParticleOptionsRepository
import com.cobblemon.mod.common.client.particle.GuiParticleScene
import com.cobblemon.mod.common.client.particle.ParticleStorm
import com.cobblemon.mod.common.client.render.MatrixWrapper
import com.cobblemon.mod.common.client.render.models.blockbench.PosableState
import com.cobblemon.mod.common.client.render.structure.StructurePreviewResources
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferUploader
import com.cobblemon.mod.common.util.resolveBoolean
import com.cobblemon.mod.common.util.resolveDouble
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import com.mojang.blaze3d.vertex.Tesselator
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.particle.ParticleRenderType
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf

/**
 * Plays a move's REAL snowstorm particle effects inside the MoveDex viewport as a simulated 3D scene.
 *
 * Rather than reimplement the particle engine, this runs the genuine [ParticleStorm] lifecycle: each
 * `entity_particles` keyframe's storm is constructed with `guiScene = this` so it (and every effect it
 * event-spawns — trails, secondary bursts, heal sparkles) reports here instead of the world particle
 * engine. The scene then ticks every storm and particle at 20 Hz with their real `tick()` methods — so
 * emitter shapes/rates, MoLang motion, `target_delta*` drains, flipbook UVs, tints, materials, collisions
 * and child-effect event timelines all behave exactly as in battle — and renders each particle through
 * its real [com.cobblemon.mod.common.api.snowstorm.ParticleCameraMode] via [com.cobblemon.mod.common.client.render.SnowstormParticle.renderInScene].
 *
 * Storms live high in the sky (empty collision/lighting) and never touch the world engine, so they appear
 * only in the dex. Everything is guarded so a misbehaving effect degrades to "no particles" not a crash.
 */
class MoveDexParticleScene : GuiParticleScene {

    private val storms = ArrayList<ParticleStorm>()
    private val pending = ArrayList<ParticleStorm>()
    // Per-storm: which doll it renders against (true = target/B, false = user/A) and the tick it starts on.
    private val sideOf = HashMap<ParticleStorm, Boolean>()
    private val startTickOf = HashMap<ParticleStorm, Int>()
    // Per-storm body-relative emit offset (height, front-toward-opponent) — the hitbox approximation, used
    // for headless measurement and as a fallback when the model has no matching geo locator.
    private val locatorOffsetOf = HashMap<ParticleStorm, Pair<Float, Float>>()
    // Per-storm keyframe locator names (e.g. ["special","target"]); resolved against the model's real geo
    // locators at render time so Pokémon emit from their actual locators, exactly like battle.
    private val locatorNamesOf = HashMap<ParticleStorm, List<String>>()
    // Per-storm MUTABLE aim point (a 1-element holder the targetPos lambda reads); refreshed each tick from the
    // real emit locator -> the opposing doll's centre, so beams angle toward the target instead of dead-level.
    private val targetHolderOf = HashMap<ParticleStorm, Array<Vec3>>()
    // Per-storm emit anchor frozen at the moment the storm starts firing. The real geo locator animates with the
    // actor's head; without freezing, a stretched beam (a single quad anchored at the mouth) would slide its whole
    // length — including its far end — around with the head. Locked once firing, so the beam holds steady.
    private val frozenEmitAnchorOf = HashMap<ParticleStorm, FloatArray>()

    // Aim context (set each frame by the view): the doll states/scales/Z anchors + their centre heights, used
    // to resolve the real emit locator and the opposing doll's centre when refreshing aim points.
    private var ctxActorState: PosableState? = null
    private var ctxTargetState: PosableState? = null
    private var ctxActorScale = 1f
    private var ctxTargetScale = 1f
    private var ctxUserZ = 0f
    private var ctxTargetZ = 0f
    private var ctxActorMid = 1f
    private var ctxTargetMid = 1f
    // Z of the centre of the target block's face that faces the attacker (its near face). Moves that fire at the
    // target aim here — the front face — instead of the block's centre, since that's the surface a projectile hits.
    private var ctxTargetFaceZ = 0f

    /** Provides the per-frame doll context used to keep dynamic aim points aligned with the real emit locators. */
    fun setAimContext(actorState: PosableState?, targetState: PosableState?, actorScale: Float, targetScale: Float,
                      userZ: Float, targetZ: Float, actorMid: Float, targetMid: Float, targetFaceZ: Float) {
        ctxActorState = actorState; ctxTargetState = targetState
        ctxActorScale = actorScale; ctxTargetScale = targetScale
        ctxUserZ = userZ; ctxTargetZ = targetZ
        ctxActorMid = actorMid; ctxTargetMid = targetMid
        ctxTargetFaceZ = targetFaceZ
    }

    /**
     * Re-aims each targeted storm from its real emit locator toward the target spot. A storm fired AT the target
     * aims at the centre of the target block's near face (the surface facing the attacker); a storm fired back at
     * the attacker aims at the attacker doll's centre.
     */
    private fun refreshTargets() {
        for ((storm, holder) in targetHolderOf) {
            val atTarget = sideOf[storm] == true
            val state = if (atTarget) ctxTargetState else ctxActorState
            val scale = if (atTarget) ctxTargetScale else ctxActorScale
            val sideZ = if (atTarget) ctxTargetZ else ctxUserZ
            val e = emitAnchor(storm, atTarget, state, scale, sideZ) ?: continue // unresolved locator → skip
            // A keyframe's targetLocators ALWAYS refer to the DEFENDER (battle's TargetsProvider), so the aim is
            // the target doll regardless of which side emits: for a user-side effect (a beam) that's the opposing
            // doll; for a DEFENDER-side effect it's the SAME doll — e.g. Draco Meteor's meteors target their own
            // `root` so they rain down ON the target. Aiming target-side storms at the attacker (the old behavior)
            // made those meteors land way off near the attacker.
            val aimY = ctxTargetMid                                  // the target doll's centre height
            val aimZ = if (atTarget) ctxTargetZ else ctxTargetFaceZ  // self-aim: target centre; incoming: its near face
            holder[0] = origin.add((0f - e[0]).toDouble(), (aimY - e[1]).toDouble(), (aimZ - e[2]).toDouble())
        }
        // Re-evaluate every storm's storm-level target_delta* from the live aim point so TRAVELING emitters
        // (which read v.target_deltaz off the storm runtime, not per-particle) follow the target block's near
        // face. Children inherit the parent's targetPos lambda, so they re-aim from the same holder too.
        for (storm in storms) storm.refreshAttackingVariables()
        for (storm in pending) storm.refreshAttackingVariables()
    }

    private var origin = Vec3.ZERO
    private var tickCount = 0
    private var renderPartial = 0f

    // DIAGNOSTIC (temporary): per-effect counts of storms registered vs dropped by the cap, plus the peak live
    // storm count. Logged once per build cycle in [clear] so a single Eruption run reveals whether the trail
    // child effects (eruption_targetflame) are being starved by the storm cap.
    private val spawnTally = HashMap<String, Int>()
    private val dropTally = HashMap<String, Int>()
    private var peakStorms = 0

    // Context for [addStorm]: the side/start a newly-registered storm inherits. Set before spawning a root
    // (its keyframe's side + windup tick) and before ticking each storm (its own side + "now" for children).
    private var currentSide = false
    private var nextStartTick = 0
    private var nextLocatorOffset = 1.0f to 0f
    private var nextLocatorNames = emptyList<String>()

    // Hitbox dims (height, width in world blocks) of the emitting dolls, used to place named locators exactly
    // like battle's LocatorAccess bounding-box approximations.
    private var actorHeight = 2.0f
    private var actorWidth = 1.0f
    private var targetHeight = 2.0f
    private var targetWidth = 1.0f

    // The locators battle's LocatorAccess auto-generates from the entity bounding box for EVERY entity. A keyframe
    // requesting one of these always resolves (we approximate it from the hitbox); a keyframe requesting only
    // model-specific geo locators the doll doesn't define (hand_primary/hand/physical/mouth/...) resolves to
    // nothing and the storm is skipped — matching battle, where such an effect simply isn't spawned.
    private val AUTOMATIC_LOCATORS = setOf("root", "target", "special_attack", "middle", "top")

    fun clear() {
        storms.clear()
        pending.clear()
        sideOf.clear()
        startTickOf.clear()
        locatorOffsetOf.clear()
        locatorNamesOf.clear()
        targetHolderOf.clear()
        frozenEmitAnchorOf.clear()
        loggedBeam.clear()
        // Log the previous cycle's storm pressure before wiping it, so one run shows whether the cap drops storms.
        if (spawnTally.isNotEmpty() || dropTally.isNotEmpty()) {
            Cobblemon.LOGGER.info("MoveDex particle cycle: peakStorms={} spawned={} dropped={}", peakStorms, spawnTally, dropTally)
            // If ANY effect was dropped, this move is still hitting the cap — surface it loudly so it's obvious
            // which move (the effect ids name it) needs the ceiling raised further, not just Eruption.
            if (dropTally.isNotEmpty()) Cobblemon.LOGGER.warn("MoveDex particle scene hit the storm cap (peak {}); dropped child storms: {}", peakStorms, dropTally)
        }
        spawnTally.clear(); dropTally.clear(); peakStorms = 0
        tickCount = 0
        renderPartial = 0f
        // Reset the spawn context too, so a rebuilt scene never inherits the previous cycle's leftover start tick.
        currentSide = false
        nextStartTick = 0
        nextLocatorOffset = 1.0f to 0f
        nextLocatorNames = emptyList()
    }

    /**
     * Body-relative emit offset (height up, distance toward the opponent) for one of battle's AUTOMATIC bounding-
     * box locators (the ones [LocatorAccess] generates from the entity hitbox: root=base, middle=mid-height,
     * target/special_attack=mid-height + forward, top=head). Only ever called by [emitAnchor] for a name that IS an
     * automatic locator, so the catch-alls just mirror `middle`.
     */
    private fun locatorOffset(name: String?, atTarget: Boolean): Pair<Float, Float> {
        val h = if (atTarget) targetHeight else actorHeight
        val w = if (atTarget) targetWidth else actorWidth
        return when {
            name == null -> (h * 0.5f) to 0f
            name.startsWith("root") -> 0f to 0f
            name.startsWith("top") -> h to 0f
            name.startsWith("middle") -> (h * 0.5f) to 0f
            name.startsWith("target") || name.startsWith("special") -> (h * 0.5f) to (w * 0.6f)
            else -> (h * 0.5f) to 0f
        }
    }

    /**
     * Collision floor for [storm]: the dolls' feet, which is the storm's emit HEIGHT below the scene origin. Every
     * particle simulates at [origin] (the per-locator height is applied only at render via the emit anchor), so a
     * storm emitting at mid-height (e.g. an impact burst at the `target` locator) must have its floor that height
     * below the origin — otherwise its particles spawn already touching the slab and any `expire_on_contact` effect
     * dies instantly. A ground emitter (root, height 0) keeps the floor at the origin, so Bulldoze still settles on it.
     */
    override fun guiFloorY(storm: ParticleStorm): Double = origin.y - (locatorOffsetOf[storm]?.first?.toDouble() ?: 0.0)

    /**
     * Solid box for [storm]'s target, centred on its live aim point (the holder updated each tick to the target
     * block's near-face centre). The aim point is built as origin + (aimPoint - emitAnchor), which renders back
     * exactly onto the target's face — so a box here, in the same sim space the particles move in, stops
     * traveling/colliding effects at the visible target instead of letting them shoot past. Sized to the thing
     * being hit (the block for user effects, the attacker for target effects). Null if the storm has no target.
     */
    override fun guiImpactBox(storm: ParticleStorm): AABB? {
        val holder = targetHolderOf[storm] ?: return null
        val c = holder[0]
        val atTarget = sideOf[storm] == true
        val w = (if (atTarget) actorWidth else targetWidth).toDouble()
        val h = (if (atTarget) actorHeight else targetHeight).toDouble()
        val hx = (w * 0.5).coerceAtLeast(0.1)
        val hz = (w * 0.5).coerceAtLeast(0.1)
        val hy = (h * 0.5).coerceAtLeast(0.1)
        return AABB(c.x - hx, c.y - hy, c.z - hz, c.x + hx, c.y + hy, c.z + hz)
    }

    /** World Y of the scene's ground plane, exposed so the viewport can draw the floor at the dolls' feet. */
    fun floorOriginY(): Double = origin.y

    override fun addStorm(storm: ParticleStorm) {
        // Safety cap: event-spawned children can multiply; never let the scene grow unbounded. 256 was far too low
        // for emitter-heavy moves — Eruption's burning rocks each spawn a steady stream of `eruption_targetflame`
        // trail storms on top of the actor-side fountain, so the later trails were hitting the cap and getting
        // dropped (rocks survived because their storm was already registered). These are lightweight bookkeeping
        // objects; the real cost is particles, which each storm bounds via its own max_particles.
        if (storms.size + pending.size >= 4096) {
            dropTally.merge(storm.effect.id.toString(), 1, Int::plus)
            return
        }
        spawnTally.merge(storm.effect.id.toString(), 1, Int::plus)
        pending.add(storm)
        sideOf[storm] = currentSide
        startTickOf[storm] = nextStartTick
        locatorOffsetOf[storm] = nextLocatorOffset
        locatorNamesOf[storm] = nextLocatorNames
        if (storm.effect.id.path.contains("rockslide")) {
            val sp = storm.effect.space; val col = storm.effect.particle.collision
            runCatching {
                Cobblemon.LOGGER.info(
                    "ROCKDBG STORM-ADD id={} side={} startTick={} locOffset={} locNames={} | space[localPos={} localRot={} isLocalSpace={}] | coll[enabled={} radius={} expireOnContact={}] | maxAgeSec={} motion={} stormXYZ=({},{},{}) floorY={}",
                    storm.effect.id.path, currentSide, nextStartTick, nextLocatorOffset, nextLocatorNames,
                    sp.localPosition, sp.localRotation, sp.isLocalSpace,
                    storm.runtime.resolveBoolean(col.enabled), "%.3f".format(storm.runtime.resolveDouble(col.radius)), col.expiresOnContact,
                    "%.3f".format(storm.runtime.resolveDouble(storm.effect.particle.maxAge)), storm.effect.particle.motion::class.simpleName,
                    "%.2f".format(storm.getX()), "%.2f".format(storm.getY()), "%.2f".format(storm.getZ()), "%.2f".format(guiFloorY(storm))
                )
            }
        }
        // Battle sets entity_width/height/size/radius/scale from the emitting entity's bounding box; our storms
        // have no entity, so populate them here from the emitting doll's hitbox — otherwise effects sized off the
        // Pokémon (e.g. Dragon Claw's aura disc `radius: v.entity_radius`) collapse to a point. Done for EVERY
        // storm (roots AND event-spawned children, which also get fresh entity-less runtimes).
        val ew = (if (currentSide) targetWidth else actorWidth).toDouble()
        val eh = (if (currentSide) targetHeight else actorHeight).toDouble()
        val longer = maxOf(ew, eh)
        val env = storm.runtime.environment
        // Effects reference these as BOTH variables (v.entity_*) and queries (q.entity_size/height/radius/width are
        // each used by 100+ effects), so provide both namespaces — otherwise size-relative effects collapse to 0.
        env.setSimpleVariable("entity_width", DoubleValue(ew));   env.query.addFunction("entity_width") { DoubleValue(ew) }
        env.setSimpleVariable("entity_height", DoubleValue(eh));  env.query.addFunction("entity_height") { DoubleValue(eh) }
        env.setSimpleVariable("entity_size", DoubleValue(longer)); env.query.addFunction("entity_size") { DoubleValue(longer) }
        env.setSimpleVariable("entity_radius", DoubleValue(longer / 2.0)); env.query.addFunction("entity_radius") { DoubleValue(longer / 2.0) }
        env.setSimpleVariable("entity_scale", DoubleValue(1.0));  env.query.addFunction("entity_scale") { DoubleValue(1.0) }
    }

    /**
     * Builds the root storms for [move]'s action-effect particles (cleared first). [targetDistance] is the
     * scene gap (in blocks) between user (A) and target (B) along the forward (-Z) axis; the hitbox dims size
     * the per-locator emit offsets. Safe on any failure.
     */
    fun build(move: MoveTemplate, targetDistance: Float, actorH: Float, actorW: Float, targetH: Float, targetW: Float) {
        clear()
        actorHeight = actorH; actorWidth = actorW; targetHeight = targetH; targetWidth = targetW
        runCatching {
            val world = Minecraft.getInstance().level ?: return
            val player = Minecraft.getInstance().player ?: return
            // High above the player: the storm's real collision/lighting checks hit empty sky.
            origin = Vec3(player.x, player.y + 600.0, player.z)

            val resource = "data/cobblemon/action_effects/moves/${move.name}.json"
            val stream = StructurePreviewResources.openStream(resource) ?: return
            // Parse with the SAME deserializer the battle system uses, then walk the typed keyframes — so locators,
            // entity conditions, molang delays, and sequence/parallel/fork nesting are interpreted exactly as in
            // battle, instead of a hand-rolled JSON walk that drifted from the real format.
            val actionEffect = stream.use { ActionEffects.gson.fromJson(it.reader(), ActionEffectTimeline::class.java) } ?: return
            walk(actionEffect.timeline, 0f, world, targetDistance)
        }.onFailure { Cobblemon.LOGGER.warn("MoveDex particle scene build failed for {}", move.name, it) }
    }

    private val parseRuntime by lazy { MoLangRuntime().setup().setupClient() }
    private fun secs(e: ExpressionLike) = runCatching { e.resolveFloat(parseRuntime) }.getOrDefault(0f).let { if (it.isFinite()) it else 0f }

    /**
     * Walks the typed timeline keeping a running clock so each storm spawns at its real start time. Keyframes run
     * sequentially (each advancing the clock by its `delay`/`pause`); `sequence` chains its children, `parallel`
     * runs them from the same start (clock = latest end), and `fork` spawns its branch without blocking. This is
     * the same control flow [ActionEffectTimeline] uses to play the effect in battle.
     */
    private fun walk(keyframes: List<ActionEffectKeyframe>, start: Float, world: ClientLevel, targetDistance: Float): Float {
        var clock = start
        for (kf in keyframes) {
            clock = when (kf) {
                is PauseActionEffectKeyframe -> clock + secs(kf.pause)
                is EntityParticlesActionEffectKeyframe -> { spawnRoot(kf, clock, world, targetDistance); clock + secs(kf.delay) }
                is AnimationActionEffectKeyframe -> clock + secs(kf.delay)
                is EntityMoLangActionEffectKeyframe -> clock + secs(kf.delay)
                is EntitySoundActionEffectKeyframe -> clock + secs(kf.delay)
                is SequenceActionEffectKeyframe -> walk(kf.keyframes, clock, world, targetDistance)
                is ParallelActionEffectKeyframe -> {
                    var maxEnd = clock
                    kf.keyframes.forEach { maxEnd = maxOf(maxEnd, walk(listOf(it), clock, world, targetDistance)) }
                    maxEnd
                }
                is ForkActionEffectKeyframe -> { walk(kf.ifTrue, clock, world, targetDistance); clock } // non-blocking branch
                else -> clock // add_holds / remove_holds / interrupt markers: no particles, no delay
            }
        }
        return clock
    }

    /** Creates and registers one [EntityParticlesActionEffectKeyframe]'s storm, starting at [startClock] seconds. */
    private fun spawnRoot(kf: EntityParticlesActionEffectKeyframe, startClock: Float, world: ClientLevel, targetDistance: Float) {
        val effectId = kf.effect?.let(ResourceLocation::tryParse) ?: return // skip molang-computed ids (e.g. generic_move)
        val effect = BedrockParticleOptionsRepository.getEffect(effectId) ?: return
        // Which doll emits is decided by the keyframe's entityCondition, exactly as in battle:
        // absent -> the USER (point A); "is_user == false" -> the defending Pokémon (point B).
        val emitsAtTarget = kf.entityCondition.getString().replace(" ", "").contains("is_user==false")
        // The typed `locators`/`targetLocators` fields ARE what battle reads (so a stray singular "locator" key is
        // ignored here too — matching battle rather than our old hand-parser).
        val locNames = kf.locators
        val targetLocs = kf.targetLocators
        val hasTarget = !targetLocs.isNullOrEmpty()

        val wrapper = MatrixWrapper()
        val matrix = PoseStack()
        // Face the emitter at its opponent, exactly like the battle entity's locator matrix. The effect's local
        // FORWARD is -Z; the user already looks down -Z (target sits there), but the TARGET's opponent (the
        // attacker) is at +Z, so its emitter must be turned 180° — otherwise every "fire forward" effect on the
        // defender (Close Combat's hit burst, Draco Meteor's meteors, etc.) sprays out the BACK of the target.
        if (emitsAtTarget) matrix.mulPose(Axis.YP.rotationDegrees(180f))
        // The scene origin goes in the wrapper's POSITION, NOT baked into the matrix translation. The particle
        // engine's position-attachment step (ParticleStorm.tick) sets a storm's emitter position to
        // attachedMatrix.getOrigin() = origin; if the matrix ALSO carried translate(origin), transformPosition
        // would add the origin TWICE — sending parametric particles (Swords Dance's swords, Rock Slide's rocks)
        // to ~2×origin (~666 blocks). Keeping the matrix orientation-only makes that re-add idempotent, matching
        // how battle's locator matrices behave. getOrigin()/transformPosition still yield the same world values.
        wrapper.updateMatrix(matrix.last().pose())
        wrapper.updatePosition(origin)
        // The opponent sits along the emitter's FORWARD (-Z) axis (every directional effect is authored against
        // this, e.g. Flamethrower drives speed/rate/direction off v.target_deltaz). We ALSO offset the target in
        // Y by the height difference between the emitter's locator and the opponent's target-locator, so the
        // beam angles up/down toward where the opposing doll actually is rather than always firing dead level.
        val emitHeight = locatorOffset(locNames.firstOrNull(), emitsAtTarget).first
        val targetHeight = locatorOffset(targetLocs?.firstOrNull(), !emitsAtTarget).first
        val towardZ = if (emitsAtTarget) targetDistance else -targetDistance
        // Mutable aim point (initial hitbox-based guess); refreshTargets() re-aims it from the real emit locator
        // toward the opposing doll's centre each tick.
        val holder = arrayOf(origin.add(0.0, (targetHeight - emitHeight).toDouble(), towardZ.toDouble()))
        val runtime = MoLangRuntime().setup().setupClient()
        // Set the spawn context (side, windup start tick, locators) BEFORE constructing the storm. The storm's
        // `init` synchronously fires its creation events — which spawn child effects like Bubble Beam's bubble
        // column — and those children inherit this context through addStorm. If it were set AFTER construction the
        // child would grab the STALE nextStartTick left over from the previous cycle's ticking (a value near the
        // loop point), so it would start too late or past the loop and only appear on some cycles. That was the
        // "column shows every few loops" bug.
        currentSide = emitsAtTarget
        nextStartTick = (startClock * 20f).toInt()
        nextLocatorOffset = locatorOffset(locNames.firstOrNull(), emitsAtTarget)
        nextLocatorNames = locNames
        val storm = ParticleStorm(
            effect, wrapper, wrapper, world,
            targetPos = if (hasTarget) ({ holder[0] }) else null,
            runtime = runtime,
            guiScene = this
        )
        if (hasTarget) targetHolderOf[storm] = holder // addAttackingFunctions is set in spawn() for guiScene storms
        storm.spawn()
    }

    private fun materialFor(material: ParticleMaterial): ParticleRenderType = when (material) {
        ParticleMaterial.ALPHA -> ParticleMaterials.ALPHA
        ParticleMaterial.OPAQUE -> ParticleMaterials.OPAQUE
        ParticleMaterial.BLEND -> ParticleMaterials.BLEND
        ParticleMaterial.ADD -> ParticleMaterials.ADD
    }

    val isEmpty: Boolean get() = storms.isEmpty() && pending.isEmpty()

    /**
     * Runs the (already-built) effect headlessly for [ticks] 20 Hz steps, sampling every particle's scene-local
     * position each step, then UNIONS [box] (minX,minY,minZ,maxX,maxY,maxZ) with the non-outlier extent of
     * those samples. "Outlier" is the standard Tukey rule (beyond 1.5×IQR past the 25th/75th percentile) applied
     * per axis — a general rule, not move-specific: any particle whose position is wild compared to the bulk
     * (e.g. an effect that fires sparks at speed 1299 which, lacking ground in the sky scene, fly hundreds of
     * blocks) is ignored, so it can't blow up the framing. Consumes the scene; rebuild it for playback.
     */
    fun measureBounds(box: FloatArray, ticks: Int, userLocalZ: Float, targetLocalZ: Float) {
        val xs = ArrayList<Float>(); val ys = ArrayList<Float>(); val zs = ArrayList<Float>()
        val cap = 16000
        repeat(ticks.coerceIn(0, 400)) {
            stepTick()
            if (xs.size >= cap) return@repeat
            for (storm in storms) {
                if (storm.particles.isEmpty()) continue
                val atTarget = sideOf[storm] == true
                val (height, front) = locatorOffsetOf[storm] ?: (1.0f to 0f)
                val anchorZ = (if (atTarget) targetLocalZ else userLocalZ) + (if (atTarget) front else -front)
                for (p in storm.particles) {
                    if (xs.size >= cap) break
                    val sx = (p.getX() - origin.x).toFloat()
                    val sy = (-(p.getY() - origin.y) - height).toFloat()
                    val sz = (p.getZ() - origin.z + anchorZ).toFloat()
                    // Drop non-finite AND far-flung samples: some moves have a runaway in a particle's parametric/
                    // dynamic motion that sends MANY particles hundreds of blocks away (Swords Dance's swords hit
                    // ~666). The Tukey filter only rejects statistical outliers, so when the runaway is the majority
                    // it survives and blows the fit box up → zoom clamps to 4 → the whole viewport reads as blank.
                    // Nothing legitimate in this scene is past ±64 blocks, so an absolute clamp keeps framing sane.
                    if (sx.isFinite() && sy.isFinite() && sz.isFinite() &&
                        sx > -64f && sx < 64f && sy > -64f && sy < 64f && sz > -64f && sz < 64f) {
                        xs.add(sx); ys.add(sy); zs.add(sz)
                    }
                }
            }
        }
        if (xs.isEmpty()) return
        xs.sort(); ys.sort(); zs.sort()
        unionNonOutlier(box, xs, 0, 3)
        unionNonOutlier(box, ys, 1, 4)
        unionNonOutlier(box, zs, 2, 5)
    }

    /** Unions box[loIdx]/box[hiIdx] with the Tukey-fence (1.5×IQR) bounds of the sorted samples. */
    private fun unionNonOutlier(box: FloatArray, sorted: List<Float>, loIdx: Int, hiIdx: Int) {
        fun at(p: Float) = sorted[(sorted.size * p).toInt().coerceIn(0, sorted.size - 1)]
        val q1 = at(0.25f); val q3 = at(0.75f)
        val fence = 1.5f * (q3 - q1)
        val lo = maxOf(sorted.first(), q1 - fence)   // smallest non-outlier (>= lower fence)
        val hi = minOf(sorted.last(), q3 + fence)    // largest non-outlier  (<= upper fence)
        if (lo < box[loIdx]) box[loIdx] = lo
        if (hi > box[hiIdx]) box[hiIdx] = hi
    }

    /**
     * Advances the scene by [ticksToStep] whole 20 Hz ticks (driven by the caller off the authoritative
     * game-tick clock, so speed matches the real engine) and stores [partialTick] (the in-tick fraction)
     * as the render interpolation amount. Stepping by whole ticks — never by accumulated frame fractions —
     * is what makes the particle motion and flipbook frames run at the correct battle speed.
     */
    fun update(ticksToStep: Int, partialTick: Float) {
        repeat(ticksToStep.coerceIn(0, 10)) { stepTick() }
        renderPartial = partialTick.coerceIn(0f, 1f)
    }

    private fun stepTick() {
        tickCount++
        if (pending.isNotEmpty()) {
            storms.addAll(pending)
            pending.clear()
        }
        if (storms.size > peakStorms) peakStorms = storms.size
        refreshTargets() // re-aim from the real emit locators before this tick spawns particles
        for (storm in storms.toList()) {
            if ((startTickOf[storm] ?: 0) > tickCount) continue
            // Children spawned by this storm's events inherit its side, offset, and start ticking immediately.
            currentSide = sideOf[storm] ?: false
            nextStartTick = tickCount
            nextLocatorOffset = locatorOffsetOf[storm] ?: (1.0f to 0f)
            nextLocatorNames = locatorNamesOf[storm] ?: emptyList()
            runCatching {
                // A stopped emitter has finished EMITTING, but its already-spawned particles must keep
                // ticking until they age out (in the real engine the storm and particles tick separately).
                // Skipping them here was freezing every `once`-emitter move's particles after active_time.
                if (!storm.stopped) {
                    storm.tick()                               // emit + emitter event timeline while alive
                } else if (!storm.despawned) {
                    storm.remove()                             // fire the emitter's expiration events once (final bursts)
                }
                storm.particles.toList().forEach { it.tick() } // always advance existing particles to expiry
            }
        }
        // Drop finished storms (stopped emitter with no live particles left).
        storms.removeAll { it.stopped && it.particles.isEmpty() }
    }

    /**
     * Renders every storm's particles into the caller's already-applied scene pose (the iso viewport space).
     * Each storm is translated to its scene-local anchor along the forward axis — [targetLocalZ] for
     * target-side effects, [userLocalZ] otherwise — and its particles are placed by world-position-minus-
     * [origin] so a storm and its event-spawned children share one consistent 3D space.
     * [cameraAngle]/[cameraYaw]/[cameraPitch] describe the virtual battle camera (the inverse of the pose's
     * view rotation) so each particle billboards through its real cameraMode like the world render. Guarded.
     */
    /**
     * Scene-local emit anchor [x,y,z] for a storm. Uses the doll's REAL geo locator (target/special/mouth/...)
     * resolved from [state].locatorStates when it exists — placing particles at the exact spot battle uses,
     * scaled to the model — falling back to the hitbox approximation otherwise. The locator offset is taken
     * relative to "root", un-flipped from LocatorAccess's ZP(180), facing-rotated, and scaled by [scale].
     */
    private fun emitAnchor(storm: ParticleStorm, atTarget: Boolean, state: PosableState?, scale: Float, sideZ: Float): FloatArray? {
        // Once frozen (after the storm starts firing) the emit point holds steady — see [frozenEmitAnchorOf].
        frozenEmitAnchorOf[storm]?.let { return it }
        val names = locatorNamesOf[storm] ?: emptyList()
        val root = state?.locatorStates?.get("root")
        // Resolve the emit locator IN ORDER, exactly like battle (createAtEntity): the first requested name that is
        // either a real geo locator on this doll OR one of the AUTOMATIC bbox locators (which battle auto-generates
        // for every entity) wins. A name that is neither — e.g. Fire Punch / Dragon Claw's hand effects asking for
        // hand_primary/hand/physical on a doll that defines none — does NOT resolve. If NO name resolves we return
        // null and the renderer SKIPS the storm, because battle wouldn't spawn it there either.
        for (name in names) {
            val geoName = if (state != null) state.getMatchingLocators(name).firstOrNull() else null
            val locWrapper = geoName?.let { state?.locatorStates?.get(it) }
            if (root != null && locWrapper != null) {
                val loc = locWrapper.getOrigin(); val r = root.getOrigin()
                // getOrigin() returns the locator's true model-space offset from root (the per-locator ZP180 is a
                // post-multiplied ROTATION, so it doesn't change the translation we read). X must NOT be negated —
                // doing so mirrored left/right, e.g. Charizard's "physical" locator (its left hand at x=-21.5)
                // emitted from the right. Y is negated to match the render's upright flip; Z is forward.
                var mx = (loc.x - r.x).toFloat(); val my = -(loc.y - r.y).toFloat(); var mz = (loc.z - r.z).toFloat()
                if (atTarget) { mx = -mx; mz = -mz } // the target doll faces +Z (rendered at facingYaw 180)
                val anchor = floatArrayOf(scale * mx, scale * my, sideZ + scale * mz)
                // Lock it the moment this storm starts emitting, so the beam doesn't slide with the head afterwards.
                if (tickCount >= (startTickOf[storm] ?: 0)) frozenEmitAnchorOf[storm] = anchor
                logBeamAnchor(storm, geoName, names, anchor)
                return anchor
            }
            if (AUTOMATIC_LOCATORS.contains(name)) {
                // Auto bbox locator: approximate it from the doll's hitbox (battle's LocatorAccess does the same).
                val (height, front) = locatorOffset(name, atTarget)
                val fb = floatArrayOf(0f, height, sideZ + (if (atTarget) front else -front))
                logBeamAnchor(storm, "auto:$name", names, fb)
                return fb
            }
        }
        logBeamAnchor(storm, "SKIP(no resolvable locator)", names, floatArrayOf(0f, 0f, 0f))
        return null
    }

    // DIAGNOSTIC (temporary): for each storm, log ONCE where its emit anchor resolved — which locator matched (or
    // FALLBACK), the requested locator names, and the scene-space anchor [x, up, z]. Reveals e.g. Dragon Claw's
    // effects defaulting to `target` vs landing somewhere unexpected. Soft-capped so emitter-heavy moves don't spam.
    private val loggedBeam = HashSet<ParticleStorm>()
    private fun logBeamAnchor(storm: ParticleStorm, matched: String?, names: List<String>, anchor: FloatArray) {
        if (loggedBeam.size >= 24 || !loggedBeam.add(storm)) return
        // particleSimY = the storm's first particle's WORLD-Y minus origin (sim space): >0 means it sits ABOVE the
        // scene origin (e.g. a Rock Slide rock lofted high by guide2), ~0 means it's at the dolls' feet level.
        val pY = storm.particles.firstOrNull()?.let { (it.getY() - origin.y) }
        Cobblemon.LOGGER.info("MoveDex emit {} locator='{}' (requested {}) anchor=[{}, {}, {}] particleSimY={}",
            storm.effect.id, matched, names, anchor[0], anchor[1], anchor[2], pY)
    }

    fun render(
        context: GuiGraphics,
        userLocalZ: Float,
        targetLocalZ: Float,
        cameraAngle: Quaternionf,
        cameraYaw: Float,
        cameraPitch: Float,
        actorState: PosableState?,
        targetState: PosableState?,
        actorScale: Float,
        targetScale: Float
    ) {
        if (storms.isEmpty()) return
        runCatching {
            // The particle shader multiplies by the lightmap; bind it (full-bright via our setLight) or
            // particles render black. White shader colour so GuiGraphics' modulator doesn't tint them.
            val lightTexture = Minecraft.getInstance().gameRenderer.lightTexture()
            lightTexture.turnOnLightLayer()
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f)
            // Occlude particles by the 3D scene: the dolls, floor and target block were drawn into the depth
            // buffer, so turn depth TESTING on (the particle materials only ever set depthMask, never the test) —
            // without it every particle drew on top of the models even when behind them. depthMask is forced OFF
            // below so the transparent particles are hidden by nearer opaque geometry but don't write depth
            // themselves, which would let them wrongly occlude one another and the 2D GUI drawn afterwards.
            RenderSystem.enableDepthTest()
            val pose = context.pose()
            // Batch every storm of a given material into ONE begin/build/draw — effects spawn many child
            // storms (trails, sub-bursts), and a draw call per storm per frame is what stutters. Each
            // particle bakes its own doll-anchor, so no per-storm pose push is needed.
            val active = storms.filter { it.particles.isNotEmpty() }
            for ((material, group) in active.groupBy { it.effect.particle.material }) {
                val builder = materialFor(material).begin(Tesselator.getInstance(), Minecraft.getInstance().textureManager) ?: continue
                // The GUI scene pose winds the billboard quads back-facing, so the cull-enabled materials
                // (particles_alpha/opaque — most effects) would cull them away. Force cull off: flat billboards.
                RenderSystem.disableCull()
                RenderSystem.depthMask(false) // test against the models' depth, but don't write our own

                for (storm in group) {
                    val atTarget = sideOf[storm] == true
                    val state = if (atTarget) targetState else actorState
                    val scale = if (atTarget) targetScale else actorScale
                    val sideZ = if (atTarget) targetLocalZ else userLocalZ
                    val a = emitAnchor(storm, atTarget, state, scale, sideZ) ?: continue // unresolved locator → don't render
                    storm.particles.forEach { it.renderInScene(pose, builder, cameraAngle, cameraYaw, cameraPitch, origin, a[0], a[1], a[2], renderPartial) }
                }
                builder.build()?.let { BufferUploader.drawWithShader(it) }
            }
            // Restore GUI-friendly GL state after the particle materials' setup.
            lightTexture.turnOffLightLayer()
            RenderSystem.depthMask(true)
            RenderSystem.defaultBlendFunc()
            RenderSystem.enableCull()
        }.onFailure { Cobblemon.LOGGER.warn("MoveDex particle scene render failed", it) }
    }
}
