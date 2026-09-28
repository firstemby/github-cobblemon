package com.cobblemon.mod.common.client.pokeradar

import com.cobblemon.mod.common.util.cobblemonResource

/**
 * Defines the available Poké Radar variants and their resource locations.
 *
 * @author MeAlam
 * @since 1.0.0
 */
enum class PokeRadarType {
    BLACK,
    BLUE,
    GREEN,
    PINK,
    RED,
    WHITE,
    YELLOW;

    fun getTexturePath() = cobblemonResource("textures/gui/pokedex/pokedex_base_${this.name.lowercase()}.png")
    fun getItemModelPath(additionalState: String? = null) = cobblemonResource("item/pokedex_${this.name.lowercase()}_model${if (additionalState != null) "_$additionalState" else ""}")
    fun getItemSpritePath() = cobblemonResource("pokedex_${this.name.lowercase()}")
}