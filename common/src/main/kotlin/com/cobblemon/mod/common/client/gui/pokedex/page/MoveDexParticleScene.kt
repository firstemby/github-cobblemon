/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.pokedex.page

import com.bedrockk.molang.runtime.MoLangRuntime
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.molang.MoLangFunctions.setup
import com.cobblemon.mod.common.api.moves.MoveTemplate
import com.cobblemon.mod.common.api.snowstorm.ParticleMaterial
import com.cobblemon.mod.common.api.snowstorm.ParticleMaterials
import com.cobblemon.mod.common.client.ClientMoLangFunctions.setupClient
import com.cobblemon.mod.common.client.particle.BedrockParticleOptionsRepository
import com.cobblemon.mod.common.client.particle.GuiParticleScene
import com.cobblemon.mod.common.client.particle.ParticleStorm
import com.cobblemon.mod.common.client.render.MatrixWrapper
import com.cobblemon.mod.common.client.render.models.blockbench.PosableState
import com.cobblemon.mod.common.client.render.structure.StructurePreviewResources
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferUploader
import com.mojang.blaze3d.vertex.PoseStack
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
            val e = emitAnchor(storm, atTarget, state, scale, sideZ) // real emit anchor [x, up, z]
            val aimY = if (atTarget) ctxActorMid else ctxTargetMid    // opposing centre height
            val aimZ = if (atTarget) ctxUserZ else ctxTargetFaceZ     // attacker centre / target block near face
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

    fun clear() {
        storms.clear()
        pending.clear()
        sideOf.clear()
        startTickOf.clear()
        locatorOffsetOf.clear()
        locatorNamesOf.clear()
        targetHolderOf.clear()
        frozenEmitAnchorOf.clear()
        tickCount = 0
        renderPartial = 0f
    }

    /**
     * Body-relative emit offset (height up, distance toward the opponent) reproducing battle's bounding-box
     * locator approximations (LocatorAccess) from the emitting doll's hitbox: root=base, middle/special_attack
     * =mid-height, target/special=mid-height + forward, top=head. Falls back to mid-height.
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

    /** The dolls stand at [origin]; their feet (and the visual stadium floor) sit at this Y. */
    override fun guiFloorY(): Double = origin.y

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
        // Safety cap: event-spawned children can multiply; never let the scene grow unbounded.
        if (storms.size + pending.size >= 256) return
        pending.add(storm)
        sideOf[storm] = currentSide
        startTickOf[storm] = nextStartTick
        locatorOffsetOf[storm] = nextLocatorOffset
        locatorNamesOf[storm] = nextLocatorNames
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
            val json = stream.use { JsonParser.parseReader(it.reader()) }.takeIf { it.isJsonObject }?.asJsonObject ?: return
            val timeline = json.getAsJsonArray("timeline") ?: return

            // Walk the WHOLE timeline keeping a running clock so each effect fires at its real start time.
            var clock = 0f
            for (element in timeline) {
                val obj = element.takeIf { it.isJsonObject }?.asJsonObject ?: continue
                clock = processKeyframe(obj, clock, world, targetDistance)
            }
        }.onFailure { Cobblemon.LOGGER.warn("MoveDex particle scene build failed for {}", move.name, it) }
    }

    private fun delayOf(obj: JsonObject) = obj.get("delay")?.takeIf { it.isJsonPrimitive }?.asFloat ?: 0f
    private fun pauseOf(obj: JsonObject) = obj.get("pause")?.takeIf { it.isJsonPrimitive }?.asFloat ?: 0f

    /**
     * Processes one keyframe and returns the clock after it. Keyframes run sequentially, so most just
     * advance the clock by their `delay`/`pause`; `sequence` runs its children in order, while
     * `parallel`/`fork` run theirs from the same start. Recursing here is what makes moves whose particles
     * are nested inside those containers (e.g. Bubble Beam's `sequence`) actually play.
     */
    private fun processKeyframe(obj: JsonObject, clock: Float, world: ClientLevel, targetDistance: Float): Float {
        val type = obj.get("type")?.takeIf { it.isJsonPrimitive }?.asString ?: return clock
        return when (type) {
            "entity_particles" -> {
                spawnRoot(obj, clock, world, targetDistance)
                clock + delayOf(obj)
            }
            "pause" -> clock + pauseOf(obj)
            "sequence" -> {
                var c = clock
                obj.getAsJsonArray("keyframes")?.forEach { c = processKeyframe(it.asJsonObject, c, world, targetDistance) }
                c + delayOf(obj)
            }
            "parallel", "fork" -> {
                var maxEnd = clock
                obj.getAsJsonArray("keyframes")?.forEach {
                    val end = processKeyframe(it.asJsonObject, clock, world, targetDistance)
                    if (end > maxEnd) maxEnd = end
                }
                maxEnd + delayOf(obj)
            }
            else -> clock + delayOf(obj) // animation / molang / holds / sounds: just advance by their delay
        }
    }

    /** Creates and registers one `entity_particles` storm, starting at [startClock] seconds into the cycle. */
    private fun spawnRoot(obj: JsonObject, startClock: Float, world: ClientLevel, targetDistance: Float) {
        val effectId = obj.get("effect")?.takeIf { it.isJsonPrimitive }?.asString?.let(ResourceLocation::tryParse) ?: return
        val effect = BedrockParticleOptionsRepository.getEffect(effectId) ?: return
        // Which doll emits is decided by the keyframe's entityCondition, exactly as in battle:
        // absent -> the USER (point A); "is_user == false" -> the defending Pokémon (point B).
        val condition = obj.get("entityCondition")?.takeIf { it.isJsonPrimitive }?.asString?.replace(" ", "") ?: ""
        val emitsAtTarget = condition.contains("is_user==false")
        val targetArr = obj.getAsJsonArray("targetLocators")
        val hasTarget = targetArr != null && targetArr.size() > 0
        val locNames = obj.getAsJsonArray("locators")?.mapNotNull { it.takeIf { e -> e.isJsonPrimitive }?.asString }
            ?: obj.get("locator")?.takeIf { it.isJsonPrimitive }?.asString?.let { listOf(it) }
            ?: emptyList()

        val wrapper = MatrixWrapper()
        val matrix = PoseStack()
        matrix.translate(origin.x, origin.y, origin.z)
        wrapper.updateMatrix(matrix.last().pose())
        // The opponent sits along the emitter's FORWARD (-Z) axis (every directional effect is authored against
        // this, e.g. Flamethrower drives speed/rate/direction off v.target_deltaz). We ALSO offset the target in
        // Y by the height difference between the emitter's locator and the opponent's target-locator, so the
        // beam angles up/down toward where the opposing doll actually is rather than always firing dead level.
        val emitHeight = locatorOffset(locNames.firstOrNull(), emitsAtTarget).first
        val targetHeight = locatorOffset(targetArr?.firstOrNull()?.asString, !emitsAtTarget).first
        val towardZ = if (emitsAtTarget) targetDistance else -targetDistance
        // Mutable aim point (initial hitbox-based guess); refreshTargets() re-aims it from the real emit locator
        // toward the opposing doll's centre each tick.
        val holder = arrayOf(origin.add(0.0, (targetHeight - emitHeight).toDouble(), towardZ.toDouble()))
        val runtime = MoLangRuntime().setup().setupClient()
        val storm = ParticleStorm(
            effect, wrapper, wrapper, world,
            targetPos = if (hasTarget) ({ holder[0] }) else null,
            runtime = runtime,
            guiScene = this
        )
        if (hasTarget) targetHolderOf[storm] = holder // addAttackingFunctions is set in spawn() for guiScene storms
        // Register this root: side, windup start tick (seconds -> 20 Hz ticks), and its locator names/offset.
        currentSide = emitsAtTarget
        nextStartTick = (startClock * 20f).toInt()
        nextLocatorOffset = locatorOffset(locNames.firstOrNull(), emitsAtTarget)
        nextLocatorNames = locNames
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
                    xs.add((p.getX() - origin.x).toFloat())
                    ys.add((-(p.getY() - origin.y) - height).toFloat())
                    zs.add((p.getZ() - origin.z + anchorZ).toFloat())
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
    private fun emitAnchor(storm: ParticleStorm, atTarget: Boolean, state: PosableState?, scale: Float, sideZ: Float): FloatArray {
        // Once frozen (after the storm starts firing) the emit point holds steady — see [frozenEmitAnchorOf].
        frozenEmitAnchorOf[storm]?.let { return it }
        val names = locatorNamesOf[storm] ?: emptyList()
        val root = state?.locatorStates?.get("root")
        val matched = if (state != null) names.firstNotNullOfOrNull { state.getMatchingLocators(it).firstOrNull() } else null
        val locWrapper = matched?.let { state?.locatorStates?.get(it) }
        if (state != null && root != null && locWrapper != null) {
            val loc = locWrapper.getOrigin(); val r = root.getOrigin()
            // Un-flip LocatorAccess's per-locator ZP(180) (negates X,Y) to upright model space; keep Z.
            var mx = -(loc.x - r.x).toFloat(); val my = -(loc.y - r.y).toFloat(); var mz = (loc.z - r.z).toFloat()
            if (atTarget) { mx = -mx; mz = -mz } // the target doll faces +Z (rendered at facingYaw 180)
            val anchor = floatArrayOf(scale * mx, scale * my, sideZ + scale * mz)
            // Lock it the moment this storm starts emitting, so the beam doesn't slide with the head afterwards.
            if (tickCount >= (startTickOf[storm] ?: 0)) frozenEmitAnchorOf[storm] = anchor
            return anchor
        }
        // Fallback: hitbox-based approximation (matches battle's bbox locators when the model defines none).
        val (height, front) = locatorOffsetOf[storm] ?: (1.0f to 0f)
        return floatArrayOf(0f, height, sideZ + (if (atTarget) front else -front))
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
                for (storm in group) {
                    val atTarget = sideOf[storm] == true
                    val state = if (atTarget) targetState else actorState
                    val scale = if (atTarget) targetScale else actorScale
                    val sideZ = if (atTarget) targetLocalZ else userLocalZ
                    val a = emitAnchor(storm, atTarget, state, scale, sideZ)
                    // Fixed scene-space target a beam must END on: the opposing centre (Y up = -mid). For a
                    // user effect that's the target block's near-face centre; for a target effect, the attacker.
                    // Only storms that actually aim at something get a beam end (others billboard normally).
                    val beamEnd = if (storm.targetPos != null) {
                        if (atTarget) floatArrayOf(0f, -ctxActorMid, ctxUserZ) else floatArrayOf(0f, -ctxTargetMid, ctxTargetFaceZ)
                    } else null
                    storm.particles.forEach { it.renderInScene(pose, builder, cameraAngle, cameraYaw, cameraPitch, origin, a[0], a[1], a[2], renderPartial, beamEnd) }
                }
                builder.build()?.let { BufferUploader.drawWithShader(it) }
            }
            // Restore GUI-friendly GL state after the particle materials' setup.
            lightTexture.turnOffLightLayer()
            RenderSystem.defaultBlendFunc()
            RenderSystem.enableCull()
        }.onFailure { Cobblemon.LOGGER.warn("MoveDex particle scene render failed", it) }
    }
}
