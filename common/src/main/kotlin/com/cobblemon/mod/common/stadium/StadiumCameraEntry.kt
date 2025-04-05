package com.cobblemon.mod.common.stadium

import com.bedrockk.molang.Expression
import com.cobblemon.mod.common.util.*
import com.google.gson.JsonArray
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import net.fabricmc.loader.impl.lib.sat4j.core.Vec
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import java.lang.reflect.Type
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2

class StadiumCameraEntry(
    var id: ResourceLocation,
    val length: Double = 0.0,
    val positionTimeline: MutableMap<String, CameraKeyFrame> = mutableMapOf(),
    val targetPositionTimeline: MutableMap<String, CameraKeyFrame> = mutableMapOf()
) {
    fun resolvePosition(time: Double): Vec3 {
        return resolvePosition(time, positionTimeline)
    }

    private fun resolvePosition(time: Double, map: MutableMap<String, CameraKeyFrame>): Vec3 {
        val runtime = StadiumClientManager.runtime
        var frame1: CameraKeyFrame? = null
        var frame2: CameraKeyFrame? = null
        var time1 = -1.0
        var time2 = -1.0
        for (keyframe in map) {
            if (time >= keyframe.key.toDouble()) {
                if (time1 == -1.0 || abs(time - keyframe.key.toDouble()) < abs(time - time1)) {
                    frame1 = keyframe.value
                    time1 = keyframe.key.toDouble()
                }
            }
            if (time < keyframe.key.toDouble()) {
                if (time2 == -1.0 || abs(time - keyframe.key.toDouble()) < abs(time - time2)) {
                    frame2 = keyframe.value
                    time2 = keyframe.key.toDouble()
                }
            }
        }
        val difference = (time - time1) / (time2 - time1)

        if (frame1 != null && frame1.interpolation == CameraInterpolation.STEP) {
            frame2 = null
        }

        if (frame1 == null && frame2 == null) {
            return Vec3.ZERO
        }
        else if (frame1 == null) {
            frame2!!
            val x = runtime.resolveDouble(frame2.x)
            val y = runtime.resolveDouble(frame2.y)
            val z = runtime.resolveDouble(frame2.z)
            return Vec3(x,y,z)
        }
        else if (frame2 == null) {
            val x = runtime.resolveDouble(frame1.x)
            val y = runtime.resolveDouble(frame1.y)
            val z = runtime.resolveDouble(frame1.z)
            return Vec3(x,y,z)
        }
        else {
            val x1 = runtime.resolveDouble(frame1.x)
            val y1 = runtime.resolveDouble(frame1.y)
            val z1 = runtime.resolveDouble(frame1.z)
            val vec1 = Vec3(x1,y1,z1)
            val x2 = runtime.resolveDouble(frame2.x)
            val y2 = runtime.resolveDouble(frame2.y)
            val z2 = runtime.resolveDouble(frame2.z)
            val vec2 = Vec3(x2,y2,z2)
            return vec1.lerp(vec2, difference)
        }
    }

    fun resolveRotation(time: Double): Vector3f {
        val position = resolvePosition(time, positionTimeline)
        val target = resolvePosition(time, targetPositionTimeline)
        val offset = target.subtract(position)
        val xRot = -atan(offset.y.toFloat()/offset.horizontalDistance().toFloat()) * (180F / Math.PI.toFloat())
        val yRot = -atan2(offset.x.toFloat(),offset.z.toFloat()) * (180F / Math.PI.toFloat())
        return Vector3f(xRot, yRot, 0.0F)
    }

    fun encode(buf: RegistryFriendlyByteBuf) {
        buf.writeIdentifier(id)
        buf.writeDouble(length)
        buf.writeMap(positionTimeline, {_, key -> buf.writeString(key)}, {_, value ->
            buf.writeString(value.x.getString())
            buf.writeString(value.y.getString())
            buf.writeString(value.z.getString())
            buf.writeEnum(value.interpolation)
        })
        buf.writeMap(targetPositionTimeline, {_, key -> buf.writeString(key)}, {_, value ->
            buf.writeString(value.x.getString())
            buf.writeString(value.y.getString())
            buf.writeString(value.z.getString())
            buf.writeEnum(value.interpolation)
        })
    }

    companion object {
        fun decode(buf: RegistryFriendlyByteBuf) : StadiumCameraEntry {
            val id = buf.readIdentifier()
            val length = buf.readDouble()
            val positionTimeline = buf.readMap({_ -> buf.readString()}, {_ ->
                CameraKeyFrame(buf.readString().asExpression(), buf.readString().asExpression(), buf.readString().asExpression(), buf.readEnum(CameraInterpolation::class.java))
            }).toMutableMap()
            val targetPositionTimeline = buf.readMap({_ -> buf.readString()}, {_ ->
                CameraKeyFrame(buf.readString().asExpression(), buf.readString().asExpression(), buf.readString().asExpression(), buf.readEnum(CameraInterpolation::class.java))
            }).toMutableMap()

            return StadiumCameraEntry(id, length, positionTimeline, targetPositionTimeline)
        }
    }

    data class CameraKeyFrame (
        val x: Expression,
        val y: Expression,
        val z: Expression,
        val interpolation: CameraInterpolation
    )

    enum class CameraInterpolation {
        LINEAR,
        STEP
    }

    object CameraKeyFrameAdapter: JsonDeserializer<CameraKeyFrame> {
        override fun deserialize(
            json: JsonElement?,
            typeOfT: Type?,
            context: JsonDeserializationContext?
        ): CameraKeyFrame {
            if (json is JsonArray) {
                if (json.count() >= 3) {
                    var interpolation = CameraInterpolation.LINEAR
                    if (json.count() >= 4) {
                        interpolation = when(json[4].asString) {
                            "linear" -> CameraInterpolation.LINEAR
                            "step" -> CameraInterpolation.STEP
                            else -> CameraInterpolation.LINEAR
                        }
                    }
                    return CameraKeyFrame(json[0].asString.asExpression(), json[1].asString.asExpression(), json[2].asString.asExpression(), interpolation)
                }
            }
            throw IllegalStateException("Camera Timeline Node could not be parsed")
        }
    }
}