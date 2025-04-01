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

class StadiumCameraEntry(
    var id: ResourceLocation,
    val length: Double = 0.0,
    val positionTimeline: MutableMap<String, VectorExpression> = mutableMapOf(),
    val targetPositionTimeline: MutableMap<String, VectorExpression> = mutableMapOf()
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
        buf.writeMap(positionTimeline, {_, key -> buf.writeString(key)}, {_, value ->
            buf.writeString(value.x.getString())
            buf.writeString(value.y.getString())
            buf.writeString(value.z.getString())
        })
        buf.writeMap(targetPositionTimeline, {_, key -> buf.writeString(key)}, {_, value ->
            buf.writeString(value.x.getString())
            buf.writeString(value.y.getString())
            buf.writeString(value.z.getString())
        })
    }

    companion object {
        fun decode(buf: RegistryFriendlyByteBuf) : StadiumCameraEntry {
            val id = buf.readIdentifier()
            val length = buf.readDouble()
            val positionTimeline = buf.readMap({_ -> buf.readString()}, {_ ->
                VectorExpression(buf.readString().asExpression(), buf.readString().asExpression(), buf.readString().asExpression())
            }).toMutableMap()
            val targetPositionTimeline = buf.readMap({_ -> buf.readString()}, {_ ->
                VectorExpression(buf.readString().asExpression(), buf.readString().asExpression(), buf.readString().asExpression())
            }).toMutableMap()

            return StadiumCameraEntry(id, length, positionTimeline, targetPositionTimeline)
        }
    }

    data class VectorExpression (
        val x: Expression,
        val y: Expression,
        val z: Expression
    )

    object VectorExpressionAdapter: JsonDeserializer<VectorExpression> {
        override fun deserialize(
            json: JsonElement?,
            typeOfT: Type?,
            context: JsonDeserializationContext?
        ): VectorExpression {
            if (json is JsonArray) {
                if (json.count() == 3) {
                    return VectorExpression(json[0].asString.asExpression(), json[1].asString.asExpression(), json[2].asString.asExpression())
                }
            }
            throw IllegalStateException("Camera Timeline Node could not be parsed")
        }
    }
}