package com.cobblemon.mod.common.api.pokemon.marks

import com.bedrockk.molang.runtime.MoLangRuntime
import com.cobblemon.mod.common.api.multiblock.condition.OrCondition
import net.minecraft.resources.ResourceLocation

class PokemonMark(
    id: ResourceLocation,
    val title: String,
    val description: String,
    val icon: ResourceLocation,
    val chance: Float = 1.0f
) {
    @Transient
    var identifier = id
        internal set
}
