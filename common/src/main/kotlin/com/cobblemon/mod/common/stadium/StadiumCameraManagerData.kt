package com.cobblemon.mod.common.stadium

import com.cobblemon.mod.common.util.*
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation

class StadiumCameraManagerEntry(
    val id: ResourceLocation,
    val mapOfStates: MutableMap<String, StadiumCameraState>,
    val startingState: String
) {
    fun encode(buf: RegistryFriendlyByteBuf) {
        buf.writeIdentifier(id)
        buf.writeMap(mapOfStates, {_, key -> buf.writeString(key)}, {_, value -> value.encode(buf)})
        buf.writeString(startingState)
    }

    companion object {
        fun decode(buf: RegistryFriendlyByteBuf): StadiumCameraManagerEntry {
            val id = buf.readIdentifier()
            val mapOfStates = buf.readMap({buf.readString()}, {StadiumCameraState.decode(buf)}).toMutableMap()
            val startState = buf.readString()
            return StadiumCameraManagerEntry(id, mapOfStates, startState)
        }
    }
}

class StadiumCameraState(
    val camera: ResourceLocation?,
    val target: StadiumCameraTarget,
    val listOfTransitions: MutableList<StadiumCameraTransition>,
    val loop: Boolean
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
    val target: StadiumCameraTarget,
    val targetState: String,
    val checkOnEnd: Boolean
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