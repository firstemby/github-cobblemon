package com.cobblemon.mod.common.stadium

import com.bedrockk.molang.Expression
import com.cobblemon.mod.common.util.*
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation

class StadiumCameraManagerEntry(
    var id: ResourceLocation,
    val states: MutableMap<String, StadiumCameraState> = mutableMapOf(),
    val startingState: String = ""
) {
    fun encode(buf: RegistryFriendlyByteBuf) {
        buf.writeIdentifier(id)
        buf.writeMap(states, {_, key -> buf.writeString(key)}, {_, value -> value.encode(buf)})
        buf.writeString(startingState)
    }

    companion object {
        fun decode(buf: RegistryFriendlyByteBuf): StadiumCameraManagerEntry {
            val id = buf.readIdentifier()
            val states = buf.readMap({buf.readString()}, {StadiumCameraState.decode(buf)}).toMutableMap()
            val startState = buf.readString()
            return StadiumCameraManagerEntry(id, states, startState)
        }
    }
}

class StadiumCameraState(
    val camera: ResourceLocation?,
    val target: StadiumCameraTarget = StadiumCameraTarget.CENTRE,
    val listOfTransitions: MutableList<StadiumCameraTransition> = mutableListOf(),
    val loop: Boolean = false
) {
    fun encode(buf: RegistryFriendlyByteBuf) {
        buf.writeNullable(camera) {_, v -> buf.writeIdentifier(v)}
        buf.writeEnum(target)
        buf.writeCollection(listOfTransitions) {_, transition -> transition.encode(buf)}
        buf.writeBoolean(loop)
    }

    companion object {
        fun decode(buf: RegistryFriendlyByteBuf): StadiumCameraState {
            val camera = buf.readNullable { it.readIdentifier() }
            val target = buf.readEnum(StadiumCameraTarget::class.java)
            val listOfTransitions = buf.readList { StadiumCameraTransition.decode(buf) }.toMutableList()
            val loop = buf.readBoolean()
            return StadiumCameraState(camera, target, listOfTransitions, loop)
        }
    }
}

class StadiumCameraTransition(
    val camera: ResourceLocation?,
    val target: StadiumCameraTarget = StadiumCameraTarget.CENTRE,
    val targetState: String = "",
    val checkOnEnd: Boolean = false,
    val condition: Expression = "1.0".asExpression()
) {
    fun encode(buf: RegistryFriendlyByteBuf) {
        buf.writeNullable(camera) {_, v -> buf.writeIdentifier(v)}
        buf.writeEnum(target)
        buf.writeString(targetState)
        buf.writeBoolean(checkOnEnd)
    }

    companion object {
        fun decode(buf: RegistryFriendlyByteBuf): StadiumCameraTransition {
            val camera = buf.readNullable { it.readIdentifier() }
            val target = buf.readEnum(StadiumCameraTarget::class.java)
            val targetState = buf.readString()
            val checkOnEnd = buf.readBoolean()
            return StadiumCameraTransition(camera, target, targetState, checkOnEnd)
        }
    }
}

enum class StadiumCameraTarget {
    CENTRE,
    POKEMON,
    TRAINER
}