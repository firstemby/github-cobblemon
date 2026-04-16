/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.particle

import com.cobblemon.mod.common.client.render.SnowstormParticle
import net.minecraft.CrashReport
import net.minecraft.CrashReportCategory
import net.minecraft.ReportedException
import net.minecraft.client.Camera
import net.minecraft.client.particle.ParticleEngine
import net.minecraft.client.particle.ParticleGroup
import net.minecraft.client.particle.ParticleRenderType
import net.minecraft.client.renderer.culling.Frustum
import net.minecraft.client.renderer.state.level.ParticleGroupRenderState

class SnowstormParticleGroup(
    engine: ParticleEngine,
    private val particleType: ParticleRenderType
) : ParticleGroup<SnowstormParticle>(engine) {
    private val particleTypeRenderState = SnowstormParticleRenderState()

    override fun extractRenderState(frustum: Frustum, camera: Camera, tickDelta: Float): ParticleGroupRenderState {
        particleTypeRenderState.clear()

        for (particle in particles) {
            if (frustum.pointInFrustum(particle.getX(), particle.getY(), particle.getZ())) {
                try {
                    particle.extract(particleTypeRenderState, camera, tickDelta)
                } catch (throwable: Throwable) {
                    val crashReport = CrashReport.forThrowable(throwable, "Rendering Particle")
                    val crashReportCategory: CrashReportCategory = crashReport.addCategory("Particle being rendered")
                    crashReportCategory.setDetail("Particle") { particle.toString() }
                    crashReportCategory.setDetail("Particle Type") { particleType.toString() }
                    throw ReportedException(crashReport)
                }
            }
        }

        return particleTypeRenderState
    }
}
