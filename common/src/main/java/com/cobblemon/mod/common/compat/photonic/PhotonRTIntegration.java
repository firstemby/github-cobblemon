/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.compat.photonic;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.client.particle.PhotonicDynamicLight;
import com.cobblemon.mod.common.client.particle.PhotonicDynamicLights;
import dev.plasteredcrab.photonrt.api.LightEmitter;
import dev.plasteredcrab.photonrt.api.PhotonRT;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Integrates Cobblemon with the PhotonRT raytracing mod (our fork of Photonic; mod id {@code photonrt}).
 * Two things:
 * <ul>
 *   <li><b>Block lights</b> — registers every Cobblemon light-emitting block as a colored, path-traced
 *       light via {@link PhotonRT#registerBlockLight}.</li>
 *   <li><b>Moving move-particle lights</b> — registers a single emitter hook that, each gather, pushes a
 *       traced point light for every glowing snowstorm particle currently active (see
 *       {@link PhotonicDynamicLights}). PhotonRT handles the per-frame light-grid rebuild so the lights
 *       follow the particles — the thing stock Photonic could not do.</li>
 * </ul>
 *
 * <p>This class references PhotonRT API types directly (Cobblemon compiles against PhotonRT's jar as a
 * {@code compileOnly} dependency, like it does for the LambDynamicLights API). It must therefore only be
 * loaded when {@code photonrt} is actually present — the caller
 * ({@link com.cobblemon.mod.fabric.client.CobblemonFabricClient}) gates the call on
 * {@code isModLoaded("photonrt")} so this class (and its PhotonRT references) never load otherwise.</p>
 */
public final class PhotonRTIntegration {
    private PhotonRTIntegration() {}

    /** Photonic's default block-light attenuation. */
    private static final float ATTEN_CONSTANT = 0.9f;
    private static final float ATTEN_QUADRATIC = 0.9f;

    private static boolean hookRegistered = false;

    /** One Cobblemon block id -> the light color it should cast under raytracing (0..1 linear RGB). */
    private record Entry(String id, float r, float g, float b) {}

    private static final Entry[] ENTRIES = {
        // Evolution stone blocks / ores — fire = orange
        new Entry("fire_stone_block",            1.00f, 0.42f, 0.12f),
        new Entry("fire_stone_ore",              1.00f, 0.42f, 0.12f),
        new Entry("deepslate_fire_stone_ore",    1.00f, 0.42f, 0.12f),
        new Entry("nether_fire_stone_ore",       1.00f, 0.36f, 0.10f),
        // thunder = electric yellow
        new Entry("thunder_stone_block",         1.00f, 0.88f, 0.25f),
        new Entry("thunder_stone_ore",           1.00f, 0.88f, 0.25f),
        new Entry("deepslate_thunder_stone_ore", 1.00f, 0.88f, 0.25f),
        // sun = warm gold
        new Entry("sun_stone_block",             1.00f, 0.78f, 0.30f),
        new Entry("sun_stone_ore",               1.00f, 0.78f, 0.30f),
        new Entry("deepslate_sun_stone_ore",     1.00f, 0.78f, 0.30f),
        new Entry("terracotta_sun_stone_ore",    1.00f, 0.74f, 0.34f),
        // shiny = bright near-white
        new Entry("shiny_stone_block",           1.00f, 1.00f, 0.96f),
        new Entry("shiny_stone_ore",             1.00f, 1.00f, 0.96f),
        new Entry("deepslate_shiny_stone_ore",   1.00f, 1.00f, 0.96f),
        // dawn = soft magenta/pink
        new Entry("dawn_stone_block",            0.92f, 0.55f, 0.88f),
        new Entry("dawn_stone_ore",              0.92f, 0.55f, 0.88f),
        new Entry("deepslate_dawn_stone_ore",    0.92f, 0.55f, 0.88f),
        // Machines / utility
        new Entry("healing_machine",             1.00f, 0.32f, 0.48f), // pink-red
        new Entry("pc",                          0.30f, 0.80f, 1.00f), // cyan screen
        new Entry("monitor",                     0.35f, 0.72f, 1.00f),
        new Entry("damaged_monitor",             0.45f, 0.85f, 0.60f), // sickly green glitch
        new Entry("pasture",                     1.00f, 0.90f, 0.70f), // warm
        new Entry("lectern",                     1.00f, 0.90f, 0.60f),
        // Cooking / misc
        new Entry("campfire",                    1.00f, 0.50f, 0.16f),
        new Entry("soul_campfire",               0.30f, 0.86f, 0.92f), // soul cyan
        new Entry("poke_cake",                   1.00f, 0.80f, 0.40f),
        new Entry("poke_snack",                  1.00f, 0.80f, 0.40f),
    };

    /**
     * Registers Cobblemon's block lights and the move-particle emitter hook with PhotonRT. Call only when
     * {@code photonrt} is loaded (the caller checks). Safe to call more than once: block lights are
     * replaced rather than duplicated, and the emitter hook is registered at most once.
     */
    public static void register() {
        try {
            int added = 0;
            for (Entry entry : ENTRIES) {
                final ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Cobblemon.MODID, entry.id());
                final Block block = BuiltInRegistries.BLOCK.get(id);
                if (block == null || block == Blocks.AIR) {
                    continue;
                }
                PhotonRT.registerBlockLight(block, entry.r(), entry.g(), entry.b(), ATTEN_CONSTANT, ATTEN_QUADRATIC, true);
                added++;
            }

            if (!hookRegistered) {
                PhotonRT.addEmitterHook(PhotonRTIntegration::emitDynamicLights);
                hookRegistered = true;
            }

            // Let glowing move particles start registering themselves (consumed by the hook above).
            PhotonicDynamicLights.setEnabled(true);

            Cobblemon.LOGGER.info("PhotonRT raytracing compatibility enabled: {} Cobblemon light blocks + moving move-particle lights", added);
        } catch (Throwable t) {
            Cobblemon.LOGGER.warn("PhotonRT is present but its API could not be reached (version mismatch?); skipping Cobblemon raytracing integration.", t);
        }
    }

    /**
     * Emitter hook: push one traced point light per active glowing particle. Runs on the client thread
     * during PhotonRT's per-gather pass; {@link PhotonicDynamicLights#activeSnapshot()} is thread-safe and
     * prunes dead particles. Color is the particle's live tint (see {@code SnowstormPhotonicLight}).
     */
    private static long lastDiagLogMs = 0L;

    private static void emitDynamicLights(LightEmitter emitter) {
        final java.util.List<PhotonicDynamicLight> active = PhotonicDynamicLights.activeSnapshot();
        int pushed = 0;
        for (PhotonicDynamicLight light : active) {
            if (light.getLuminance() <= 0) {
                continue;
            }
            emitter.light(light.getX(), light.getY(), light.getZ(), light.getRed(), light.getGreen(), light.getBlue());
            pushed++;
        }
        // Diagnostic (throttled): proves the hook is firing and how many particle lights we feed PhotonRT.
        // If this never logs, PhotonRT isn't calling the hook (raytracer inactive); if active>0 but you see
        // no light in-game, the gap is on PhotonRT's render side.
        final long now = System.currentTimeMillis();
        if (now - lastDiagLogMs > 2000L) {
            lastDiagLogMs = now;
            Cobblemon.LOGGER.info("[PhotonRT integration] emitter hook fired: {} active particle light(s), {} pushed", active.size(), pushed);
        }
    }
}
