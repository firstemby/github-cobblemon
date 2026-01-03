package com.cobblemon.mod.common.pokemon

import com.mojang.serialization.Codec
import net.minecraft.util.StringRepresentable

enum class OriginalTrainerType : StringRepresentable {
    NONE, PLAYER, NPC;

    override fun getSerializedName() = this.name
    companion object {
        @JvmStatic
        val CODEC: Codec<OriginalTrainerType> = StringRepresentable.fromEnum(OriginalTrainerType::values)
    }
}