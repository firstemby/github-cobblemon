/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render

import com.cobblemon.mod.common.Cobblemon.LOGGER
import java.lang.reflect.Method

/**
 * Detects whether a shaderpack is active and, if so, *which* one, then exposes a per-pack
 * [ShaderParticleProfile] describing how Cobblemon's snowstorm particles should be blended so they
 * look right under that specific pack.
 *
 * ## Why per-pack?
 * Iris/OptiFine route our custom translucent particles through the shaderpack's own
 * `gbuffers_particles_translucent` program, and every pack composites that program differently.
 * A single pack-agnostic treatment is impossible: premultiplied-alpha fixes Complementary's
 * fade-to-black but makes Photon's particles vanish and leaves Bliss darkening. So instead we
 * identify the pack and apply a tailored blend/depth profile. **Tune the table in [profileFor].**
 *
 * Everything here is reflective and fully guarded: Iris/OptiFine are optional and may be absent at
 * runtime, so we never hard-reference their classes. Any reflective failure degrades to "no shaders"
 * / the vanilla profile and never throws. Works identically on Fabric and NeoForge.
 */
object ShaderCompat {

    /** Identifies the active shaderpack family (by name substring). [OTHER] = active but unrecognised. */
    enum class ShaderPack { NONE, COMPLEMENTARY, BLISS, PHOTON, BSL, OTHER }

    /**
     * How to blend the snowstorm particle materials under a given pack. These are the only knobs we
     * actually control in-pipeline (vertex color + GL blend/depth state); the pack's fragment program
     * is out of reach.
     *
     * @param premultiplyAlpha ALPHA & BLEND: premultiply the vertex color by its alpha and blend with
     *   `(ONE, ONE_MINUS_SRC_ALPHA)`. Cures "fades to black" on packs that composite premultiplied,
     *   but can read too opaque on packs that already premultiply internally. (No-shader path: false.)
     * @param addWritesDepth   ADD material `depthMask`. `false` stops the pack treating additive glow
     *   as solid geometry (dimming via fog/deferred lighting); some packs need `true` or particles
     *   disappear. (No-shader path: true.)
     * @param blendWritesDepth BLEND material `depthMask`, same trade-off as [addWritesDepth].
     */
    data class ShaderParticleProfile(
        val premultiplyAlpha: Boolean,
        val addWritesDepth: Boolean,
        val blendWritesDepth: Boolean,
        /**
         * Clamp emitted vertex alpha to at least this (0 = no clamp). Defeats packs that `discard`
         * low-alpha particle fragments — Photon's shared translucent program does
         * `if (fragment_color.a < 0.1) discard;`, so faded/soft particles vanish. ~0.12–0.2 keeps the
         * particle core above the floor. (Vanilla/most packs: 0.) Trade-off: particles can't fade
         * fully to transparent under that pack.
         */
        val minAlpha: Float = 0f
    )

    /** The vanilla, no-shaderpack profile — must reproduce stock Minecraft particle blending exactly. */
    val VANILLA_PROFILE = ShaderParticleProfile(premultiplyAlpha = false, addWritesDepth = true, blendWritesDepth = true)

    // --- reflective handles, resolved once ------------------------------------------------------

    // Iris: net.irisshaders.iris.api.v0.IrisApi.getInstance().isShaderPackInUse()
    private val irisInstance: Any?
    private val irisIsShaderPackInUse: Method?
    // Iris: net.irisshaders.iris.Iris.getCurrentPackName() (static) -> String, "(off)" when none.
    private val irisGetCurrentPackName: Method?

    // OptiFine: net.optifine.Config.isShaders() (static) + net.optifine.shaders.Shaders.getShaderPackName()
    private val optifineIsShaders: Method?
    private val optifineGetShaderPackName: Method?

    init {
        var irisInst: Any? = null
        var irisInUse: Method? = null
        var irisPackName: Method? = null
        try {
            val irisApi = Class.forName("net.irisshaders.iris.api.v0.IrisApi")
            irisInst = irisApi.getMethod("getInstance").invoke(null)
            irisInUse = irisApi.getMethod("isShaderPackInUse")
        } catch (ignored: Throwable) { irisInst = null; irisInUse = null }
        try {
            irisPackName = Class.forName("net.irisshaders.iris.Iris").getMethod("getCurrentPackName")
        } catch (ignored: Throwable) { irisPackName = null }
        irisInstance = irisInst
        irisIsShaderPackInUse = irisInUse
        irisGetCurrentPackName = irisPackName

        var ofIsShaders: Method? = null
        var ofPackName: Method? = null
        try {
            ofIsShaders = Class.forName("net.optifine.Config").getMethod("isShaders")
        } catch (ignored: Throwable) { ofIsShaders = null }
        try {
            ofPackName = Class.forName("net.optifine.shaders.Shaders").getMethod("getShaderPackName")
        } catch (ignored: Throwable) { ofPackName = null }
        optifineIsShaders = ofIsShaders
        optifineGetShaderPackName = ofPackName
    }

    // --- detection ------------------------------------------------------------------------------

    /** `true` when Iris or OptiFine reports an active (compiled and enabled) shaderpack. Never throws. */
    fun shadersActive(): Boolean {
        val inst = irisInstance
        val inUse = irisIsShaderPackInUse
        if (inst != null && inUse != null) {
            try { if (inUse.invoke(inst) == true) return true } catch (ignored: Throwable) {}
        }
        val of = optifineIsShaders
        if (of != null) {
            try { if (of.invoke(null) == true) return true } catch (ignored: Throwable) {}
        }
        return false
    }

    /** Active shaderpack display name (e.g. "ComplementaryUnbound_r5.3.zip"), or null if none/unknown. */
    fun activePackName(): String? {
        if (!shadersActive()) return null
        irisGetCurrentPackName?.let { m ->
            try {
                val name = m.invoke(null) as? String
                if (!name.isNullOrBlank() && !name.equals("(off)", ignoreCase = true) && !name.equals("(internal)", ignoreCase = true)) {
                    return name
                }
            } catch (ignored: Throwable) {}
        }
        optifineGetShaderPackName?.let { m ->
            try {
                val name = m.invoke(null) as? String
                if (!name.isNullOrBlank() && !name.equals("OFF", ignoreCase = true) && !name.equals("(internal)", ignoreCase = true)) {
                    return name
                }
            } catch (ignored: Throwable) {}
        }
        return null
    }

    /** Classifies the active pack into a [ShaderPack] family by name substring. */
    fun activePack(): ShaderPack {
        val name = activePackName()?.lowercase() ?: return if (shadersActive()) ShaderPack.OTHER else ShaderPack.NONE
        return when {
            "complementary" in name -> ShaderPack.COMPLEMENTARY
            "bliss" in name -> ShaderPack.BLISS
            "photon" in name -> ShaderPack.PHOTON
            "bsl" in name -> ShaderPack.BSL
            else -> ShaderPack.OTHER
        }
    }

    // --- per-pack profile -----------------------------------------------------------------------

    /**
     * THE TUNING TABLE. Maps each recognised pack to its particle blend profile. These are starting
     * points based on observed symptoms — adjust freely while exploring; only the shaders-active path
     * is affected (NONE always returns [VANILLA_PROFILE], so non-shader users are never touched).
     */
    private fun profileFor(pack: ShaderPack): ShaderParticleProfile = when (pack) {
        ShaderPack.NONE -> VANILLA_PROFILE
        // Premultiplied fixes the fade-to-black; trade-off is it can look a touch too opaque.
        ShaderPack.COMPLEMENTARY -> ShaderParticleProfile(premultiplyAlpha = true, addWritesDepth = false, blendWritesDepth = false)
        // Bliss force-overrides blend to SRC_ALPHA (blend.gbuffers_textured), so premultiplied color
        // gets multiplied by alpha twice -> the darkening. Straight alpha fixes it. Brightness/glow is
        // driven by the lightmap (Bliss relights particles as (Indirect+Direct)*Albedo): glow particles
        // are emitted full-bright via environmentLighting=false and stay bright; non-glow ones use world
        // light and read as environment-lit, exactly as intended.
        ShaderPack.BLISS -> ShaderParticleProfile(premultiplyAlpha = false, addWritesDepth = false, blendWritesDepth = false)
        // Photon (MC <=1.21.1) disables its dedicated particle programs and routes particles through a
        // shared translucent program that (a) discards fragments with alpha < 0.1, (b) force-overrides
        // blend to straight alpha-over, and (c) relights RGB with diffuse-lighting * alpha (NoL needs a
        // normal we can't supply). Straight alpha + lift alpha above the 0.1 discard floor; glow comes
        // from the full-bright lightmap of environmentLighting=false particles. NOTE: true additive
        // glow can't be reproduced here (Photon's relight*alpha + forced alpha-over) — bypass needed.
        ShaderPack.PHOTON -> ShaderParticleProfile(
            premultiplyAlpha = false,
            addWritesDepth = true,
            blendWritesDepth = true,
            minAlpha = 0.15f
        )
        ShaderPack.BSL -> ShaderParticleProfile(premultiplyAlpha = true, addWritesDepth = false, blendWritesDepth = false)
        ShaderPack.OTHER -> ShaderParticleProfile(premultiplyAlpha = true, addWritesDepth = false, blendWritesDepth = false)
    }

    // Cached so per-particle reads (premultipliesUnderShaders) are free; refreshed once per particle
    // batch from ParticleRenderType.begin() (a few times per frame), where re-querying is negligible.
    @Volatile
    private var cachedProfile: ShaderParticleProfile = VANILLA_PROFILE
    private var lastLoggedPack: ShaderPack? = null

    /** Recomputes and caches the active profile. Call once per particle batch (from begin()). */
    fun refreshParticleProfile(): ShaderParticleProfile {
        val pack = activePack()
        if (pack != lastLoggedPack) {
            lastLoggedPack = pack
            LOGGER.info("Cobblemon snowstorm particle shader profile -> $pack (${activePackName() ?: "no pack"})")
        }
        val profile = profileFor(pack)
        cachedProfile = profile
        return profile
    }

    /** The profile from the most recent [refreshParticleProfile]; cheap, no reflection. */
    fun particleProfile(): ShaderParticleProfile = cachedProfile
}
