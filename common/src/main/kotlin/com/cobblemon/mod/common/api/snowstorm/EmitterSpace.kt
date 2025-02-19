/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.snowstorm

import com.cobblemon.mod.common.api.data.ArbitrarilyMappedSerializableCompanion
import com.cobblemon.mod.common.client.render.MatrixWrapper
import com.cobblemon.mod.common.util.codec.CodecUtils
import com.cobblemon.mod.common.util.codec.optionalFieldOfWithDefault
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import org.joml.AxisAngle4f
import org.joml.Matrix4f
import org.joml.Vector3f

class EmitterSpace(
    var scaling: ScalingMode = ScalingMode.WORLD
) {
    companion object {
        val CODEC: Codec<EmitterSpace> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.STRING.optionalFieldOfWithDefault("scaling", "world").forGetter { it.scaling.toString() }
            ).apply(instance) { scaling ->
                EmitterSpace(
                    ScalingMode.valueOf(scaling.uppercase())
                )
            }
        }
    }

    fun initializeEmitterMatrix(rootMatrix: MatrixWrapper, locatorMatrix: MatrixWrapper): MatrixWrapper {
        val rootRotation = rootMatrix.matrix.getRotation(AxisAngle4f())
        val scale = Vector3f()

        if (scaling == ScalingMode.ENTITY) {
            locatorMatrix.matrix.getScale(scale)
        }
        else {
            rootMatrix.matrix.getScale(scale)
            //Incase they are multiplying some axis by -1 to rotate
            scale.x = Math.signum(scale.x)
            scale.y = Math.signum(scale.y)
            scale.z = Math.signum(scale.z)
        }

        val particleScale = Vector3f(scale.x, scale.y, scale.z)
        //Presumably we will want to make the initial rotation configurable instead of always using root
        val particleRawMatrix = Matrix4f().scale(particleScale).rotate(rootRotation)
        return MatrixWrapper().updateMatrix(particleRawMatrix).updatePosition(locatorMatrix.getOrigin())
    }

    enum class ScalingMode {
        WORLD,
        ENTITY
    }
}