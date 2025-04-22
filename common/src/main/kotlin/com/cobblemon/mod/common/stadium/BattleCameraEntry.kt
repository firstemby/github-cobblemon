package com.cobblemon.mod.common.stadium

import com.bedrockk.molang.Expression
import com.bedrockk.molang.runtime.value.DoubleValue
import com.cobblemon.mod.common.util.*
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import java.lang.reflect.Type
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2

class BattleCameraEntry(
    var id: ResourceLocation,
    val length: Double = 0.0,
    val positionTimeline: MutableMap<String, CameraKeyFrame> = mutableMapOf(),
    val targetPositionTimeline: MutableMap<String, CameraKeyFrame> = mutableMapOf(),
    val move: String = ""
) {
    fun resolvePosition(time: Double, yRot: Float): Vec3 {
        return resolvePosition(time, positionTimeline).yRot(yRot * (Mth.PI/180))
    }

    private fun resolvePosition(time: Double, map: MutableMap<String, CameraKeyFrame>): Vec3 {
        val runtime = BattleCameraManagerClient.runtime
        runtime.environment.setSimpleVariable("anim_time", DoubleValue(time))
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

    fun resolveRotation(time: Double, yRot: Float): Vector3f {
        val position = resolvePosition(time, positionTimeline).yRot(yRot * (Mth.PI/180))
        val target = resolvePosition(time, targetPositionTimeline).yRot(yRot * (Mth.PI/180))
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
        buf.writeString(move)
    }

    companion object {
        fun decode(buf: RegistryFriendlyByteBuf) : BattleCameraEntry {
            val id = buf.readIdentifier()
            val length = buf.readDouble()
            val positionTimeline = buf.readMap({_ -> buf.readString()}, {_ ->
                CameraKeyFrame(buf.readString().asExpression(), buf.readString().asExpression(), buf.readString().asExpression(), buf.readEnum(CameraInterpolation::class.java))
            }).toMutableMap()
            val targetPositionTimeline = buf.readMap({_ -> buf.readString()}, {_ ->
                CameraKeyFrame(buf.readString().asExpression(), buf.readString().asExpression(), buf.readString().asExpression(), buf.readEnum(CameraInterpolation::class.java))
            }).toMutableMap()
            val move = buf.readString()

            return BattleCameraEntry(id, length, positionTimeline, targetPositionTimeline, move)
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

    object CameraEntryAdapter: JsonDeserializer<BattleCameraEntry> {
        override fun deserialize(
            json: JsonElement?,
            typeOfT: Type?,
            context: JsonDeserializationContext?
        ): BattleCameraEntry {
            if (json is JsonObject) {
                val length = json["length"]?.asDouble ?: 0.0
                val positionTimeline = mutableMapOf<String, CameraKeyFrame>()
                json["positions"]?.asJsonObject?.entrySet()?.forEach { (name, position) ->
                    val xyz = position.asJsonObject["xyz"]
                    var x = "0.0".asExpression()
                    var y = "0.0".asExpression()
                    var z = "0.0".asExpression()
                    if (xyz != null && xyz.isJsonArray && xyz.asJsonArray.size() == 3) {
                        x = xyz.asJsonArray[0].asString.asExpression()
                        y = xyz.asJsonArray[1].asString.asExpression()
                        z = xyz.asJsonArray[2].asString.asExpression()
                    }
                    val interpolation = when (position.asJsonObject["interpolation"]?.asString) {
                        "linear" -> CameraInterpolation.LINEAR
                        "step" -> CameraInterpolation.STEP
                        else -> CameraInterpolation.LINEAR
                    }
                    positionTimeline[name] = CameraKeyFrame(x, y, z, interpolation)
                }
                val targetPositionTimeline = mutableMapOf<String, CameraKeyFrame>()
                json["targetPositions"]?.asJsonObject?.entrySet()?.forEach { (name, position) ->
                    val xyz = position.asJsonObject["xyz"]
                    var x = "0.0".asExpression()
                    var y = "0.0".asExpression()
                    var z = "0.0".asExpression()
                    if (xyz != null && xyz.isJsonArray && xyz.asJsonArray.size() == 3) {
                        x = xyz.asJsonArray[0].asString.asExpression()
                        y = xyz.asJsonArray[1].asString.asExpression()
                        z = xyz.asJsonArray[2].asString.asExpression()
                    }
                    val interpolation = when (position.asJsonObject["interpolation"]?.asString) {
                        "linear" -> CameraInterpolation.LINEAR
                        "step" -> CameraInterpolation.STEP
                        else -> CameraInterpolation.LINEAR
                    }
                    targetPositionTimeline[name] = CameraKeyFrame(x, y, z, interpolation)
                }
                val move = json["move"]?.asString ?: ""
                return BattleCameraEntry(cobblemonResource(""), length, positionTimeline, targetPositionTimeline, move)
            }
            throw IllegalStateException("Stadium Camera could not be parsed")
        }
    }
}