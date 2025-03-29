package com.cobblemon.mod.common.stadium

import com.bedrockk.molang.Expression
import com.cobblemon.mod.common.util.*
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f

class StadiumCameraEntry(
    val id: ResourceLocation,
    val length: Double = 0.0,
    val cameraPositionTimeline: MutableMap<String, VectorExpression> = mutableMapOf(),
    val cameraTargetPositionTimeline: MutableMap<String, VectorExpression> = mutableMapOf()
) {

    fun resolvePosition(): Vec3 {
        return Vec3(0.0,0.0,0.0)
    }

    fun resolveRotation(): Vector3f {
        return Vector3f(0.0F, 0.0F, 0.0F)
    }

    fun encode(buf: RegistryFriendlyByteBuf) {
        buf.writeIdentifier(id)
        buf.writeDouble(length)
        buf.writeMap(cameraPositionTimeline, {_, key -> buf.writeString(key)}, {_, value ->
            buf.writeString(value.x.getString())
            buf.writeString(value.y.getString())
            buf.writeString(value.z.getString())
        })
        buf.writeMap(cameraTargetPositionTimeline, {_, key -> buf.writeString(key)}, {_, value ->
            buf.writeString(value.x.getString())
            buf.writeString(value.y.getString())
            buf.writeString(value.z.getString())
        })
    }

    companion object {
        fun decode(buf: RegistryFriendlyByteBuf) : StadiumCameraEntry {
            val id = buf.readIdentifier()
            val length = buf.readDouble()
            val cameraPositions = buf.readMap({_ -> buf.readString()}, {_ ->
                VectorExpression(buf.readString().asExpression(), buf.readString().asExpression(), buf.readString().asExpression())
            }).toMutableMap()
            val cameraRotations = buf.readMap({_ -> buf.readString()}, {_ ->
                VectorExpression(buf.readString().asExpression(), buf.readString().asExpression(), buf.readString().asExpression())
            }).toMutableMap()

            return StadiumCameraEntry(id, length, cameraPositions, cameraRotations)
        }
    }

    data class VectorExpression (
        val x: Expression,
        val y: Expression,
        val z: Expression
    )
}