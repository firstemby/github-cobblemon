/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.nest

import com.cobblemon.mod.common.CobblemonBlocks
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.Species
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.FluidTags
import net.minecraft.tags.TagKey
import net.minecraft.util.StringRepresentable
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.FluidState

enum class WildNestType(val id: String) {
    FIERY("fiery"),
    GRASSY("grassy"),
    ICY("icy"),
    PLAYER("player"),
    ROCKY("rocky"),
    SANDY("sandy"),
    SHROOMY("shroomy"),
    TWIGGY("twiggy"),
    WATERY("watery");

    companion object {
        fun fromId(id: String) = values().firstOrNull { it.id.equals(id, ignoreCase = true) }
    }
}

enum class WildNestVariant(private val id: String) : StringRepresentable {
    FIERY_DEFAULT("fiery_default"),
    FIERY_NETHERRACK("fiery_netherrack"),

    GRASSY_DEFAULT("grassy_default"),
    GRASSY_CRIMSON_ROOTS("grassy_crimson_roots"),
    GRASSY_DRY("grassy_dry"),
    GRASSY_FERN("grassy_fern"),
    GRASSY_LEAVES("grassy_leaves"),
    GRASSY_WARPED_ROOTS("grassy_warped_roots"),

    ICY_DEFAULT("icy_default"),
    ICY_ICE("icy_ice"),

    PLAYER_SUBMERGED("player_submerged"),

    ROCKY_DEFAULT("rocky_default"),
    ROCKY_AMETHYST("rocky_amethyst"),
    ROCKY_ANDESITE("rocky_andesite"),
    ROCKY_BASALT("rocky_basalt"),
    ROCKY_BLACKSTONE("rocky_blackstone"),
    ROCKY_CALCITE("rocky_calcite"),
    ROCKY_DEEPSLATE("rocky_deepslate"),
    ROCKY_DIORITE("rocky_diorite"),
    ROCKY_DRIPSTONE("rocky_dripstone"),
    ROCKY_END_STONE("rocky_end_stone"),
    ROCKY_GRANITE("rocky_granite"),
    ROCKY_GRAVEL("rocky_gravel"),
    ROCKY_RED_SANDSTONE("rocky_red_sandstone"),
    ROCKY_SANDSTONE("rocky_sandstone"),
    ROCKY_SCULK("rocky_sculk"),
    ROCKY_SNOWY("rocky_snowy"),
    ROCKY_TERRACOTTA("rocky_terracotta"),
    ROCKY_TUFF("rocky_tuff"),

    SANDY_DEFAULT("sandy_default"),
    SANDY_RED_SAND("sandy_red_sand"),
    SANDY_SOUL_SAND("sandy_soul_sand"),

    SHROOMY_DEFAULT("shroomy_default"),
    SHROOMY_CRIMSON_FUNGUS("shroomy_crimson_fungus"),
    SHROOMY_WARPED_FUNGUS("shroomy_warped_fungus"),

    TWIGGY_DEFAULT("twiggy_default"),
    TWIGGY_CHORUS("twiggy_chorus"),
    TWIGGY_DEAD_BUSH("twiggy_dead_bush"),
    TWIGGY_LEAFY("twiggy_leafy"),
    TWIGGY_MANGROVE("twiggy_mangrove"),

    WATERY_DEFAULT("watery_default"),
    WATERY_DRIPLEAF("watery_dripleaf"),
    WATERY_FIREFLY_BUSH("watery_firefly_bush"),
    WATERY_SEAGRASS_DIRT("watery_seagrass_dirt"),
    WATERY_KELP("watery_kelp");

    override fun getSerializedName() = id
}

data class WildNestPlacement(
    val type: WildNestType,
    val variant: WildNestVariant,
    val waterlogged: Boolean
)

object WildNestTypes {
    private fun nestSurfaceTag(path: String) = TagKey.create(Registries.BLOCK, ResourceLocation("cobblemon", "nest_surfaces/$path"))
    private fun nestAllowedTag(path: String) = TagKey.create(Registries.BLOCK, ResourceLocation("cobblemon", "nest_allowed/$path"))

    private val NEST_SURFACE_FIERY_NETHERRACK = nestSurfaceTag("fiery_netherrack")

    private val NEST_SURFACE_GRASSY_CRIMSON_ROOTS = nestSurfaceTag("grassy_crimson_roots")
    private val NEST_SURFACE_GRASSY_DRY = nestSurfaceTag("grassy_dry")
    private val NEST_SURFACE_GRASSY_FERN = nestSurfaceTag("grassy_fern")
    private val NEST_SURFACE_GRASSY_LEAVES = nestSurfaceTag("grassy_leaves")
    private val NEST_SURFACE_GRASSY_WARPED_ROOTS = nestSurfaceTag("grassy_warped_roots")

    private val NEST_SURFACE_ICY_ICE = nestSurfaceTag("icy_ice")

    private val NEST_SURFACE_ROCKY_AMETHYST = nestSurfaceTag("rocky_amethyst")
    private val NEST_SURFACE_ROCKY_ANDESITE = nestSurfaceTag("rocky_andesite")
    private val NEST_SURFACE_ROCKY_BASALT = nestSurfaceTag("rocky_basalt")
    private val NEST_SURFACE_ROCKY_BLACKSTONE = nestSurfaceTag("rocky_blackstone")
    private val NEST_SURFACE_ROCKY_CALCITE = nestSurfaceTag("rocky_calcite")
    private val NEST_SURFACE_ROCKY_DEEPSLATE = nestSurfaceTag("rocky_deepslate")
    private val NEST_SURFACE_ROCKY_DIORITE = nestSurfaceTag("rocky_diorite")
    private val NEST_SURFACE_ROCKY_DRIPSTONE = nestSurfaceTag("rocky_dripstone")
    private val NEST_SURFACE_ROCKY_END_STONE = nestSurfaceTag("rocky_end_stone")
    private val NEST_SURFACE_ROCKY_GRANITE = nestSurfaceTag("rocky_granite")
    private val NEST_SURFACE_ROCKY_GRAVEL = nestSurfaceTag("rocky_gravel")
    private val NEST_SURFACE_ROCKY_RED_SANDSTONE = nestSurfaceTag("rocky_red_sandstone")
    private val NEST_SURFACE_ROCKY_SANDSTONE = nestSurfaceTag("rocky_sandstone")
    private val NEST_SURFACE_ROCKY_SCULK = nestSurfaceTag("rocky_sculk")
    private val NEST_SURFACE_ROCKY_SNOWY = nestSurfaceTag("rocky_snowy")
    private val NEST_SURFACE_ROCKY_TERRACOTTA = nestSurfaceTag("rocky_terracotta")
    private val NEST_SURFACE_ROCKY_TUFF = nestSurfaceTag("rocky_tuff")

    private val NEST_SURFACE_SANDY_RED_SAND = nestSurfaceTag("sandy_red_sand")
    private val NEST_SURFACE_SANDY_SOUL_SAND = nestSurfaceTag("sandy_soul_sand")

    private val NEST_SURFACE_SHROOMY_CRIMSON_FUNGUS = nestSurfaceTag("shroomy_crimson_fungus")
    private val NEST_SURFACE_SHROOMY_WARPED_FUNGUS = nestSurfaceTag("shroomy_warped_fungus")

    private val NEST_SURFACE_TWIGGY_CHORUS = nestSurfaceTag("twiggy_chorus")
    private val NEST_SURFACE_TWIGGY_DEAD_BUSH = nestSurfaceTag("twiggy_dead_bush")
    private val NEST_SURFACE_TWIGGY_LEAFY = nestSurfaceTag("twiggy_leafy")
    private val NEST_SURFACE_TWIGGY_MANGROVE = nestSurfaceTag("twiggy_mangrove")

    private val NEST_SURFACE_WATERY_DRIPLEAF = nestSurfaceTag("watery_dripleaf")
    private val NEST_SURFACE_WATERY_FIREFLY_BUSH = nestSurfaceTag("watery_firefly_bush")
    private val NEST_SURFACE_WATERY_SEAGRASS_DIRT = nestSurfaceTag("watery_seagrass_dirt")
    private val NEST_SURFACE_WATERY_KELP = nestSurfaceTag("watery_kelp")

    private val NEST_ALLOWED_FIERY = nestAllowedTag("fiery")
    private val NEST_ALLOWED_GRASSY = nestAllowedTag("grassy")
    private val NEST_ALLOWED_ICY = nestAllowedTag("icy")
    private val NEST_ALLOWED_ROCKY = nestAllowedTag("rocky")
    private val NEST_ALLOWED_SANDY = nestAllowedTag("sandy")
    private val NEST_ALLOWED_SHROOMY = nestAllowedTag("shroomy")
    private val NEST_ALLOWED_TWIGGY = nestAllowedTag("twiggy")
    private val NEST_ALLOWED_WATERY = nestAllowedTag("watery")

    fun typesFor(species: Species): List<WildNestType> {
        return species.nestTypes.mapNotNull(WildNestType::fromId)
    }

    fun combinedTypes(entity: PokemonEntity, mate: PokemonEntity): List<WildNestType> {
        val entityTypes = typesFor(entity.pokemon.species)
        val mateTypes = typesFor(mate.pokemon.species)
        if (entityTypes.isEmpty() && mateTypes.isEmpty()) return emptyList()
        val combined = LinkedHashSet<WildNestType>()
        combined.addAll(entityTypes)
        combined.addAll(mateTypes)
        return combined.toList()
    }

    fun resolvePlacement(
        types: List<WildNestType>,
        baseState: BlockState,
        fluidState: FluidState
    ): WildNestPlacement? {
        val submerged = fluidState.`is`(FluidTags.WATER)
        for (type in types) {
            if (!isAllowedSurface(type, baseState, submerged)) continue
            val variant = variantFor(type, baseState, submerged) ?: defaultVariantFor(type)
            return WildNestPlacement(type, variant, submerged && (type == WildNestType.WATERY || type == WildNestType.PLAYER))
        }
        return null
    }

    fun blockFor(type: WildNestType): Block = when (type) {
        WildNestType.FIERY -> CobblemonBlocks.NEST_FIERY
        WildNestType.GRASSY -> CobblemonBlocks.NEST_GRASSY
        WildNestType.ICY -> CobblemonBlocks.NEST_ICY
        WildNestType.PLAYER -> CobblemonBlocks.NEST_PLAYER
        WildNestType.ROCKY -> CobblemonBlocks.NEST_ROCKY
        WildNestType.SANDY -> CobblemonBlocks.NEST_SANDY
        WildNestType.SHROOMY -> CobblemonBlocks.NEST_SHROOMY
        WildNestType.TWIGGY -> CobblemonBlocks.NEST_TWIGGY
        WildNestType.WATERY -> CobblemonBlocks.NEST_WATERY
    }

    fun defaultVariantFor(type: WildNestType): WildNestVariant = when (type) {
        WildNestType.FIERY -> WildNestVariant.FIERY_DEFAULT
        WildNestType.GRASSY -> WildNestVariant.GRASSY_DEFAULT
        WildNestType.ICY -> WildNestVariant.ICY_DEFAULT
        WildNestType.PLAYER -> WildNestVariant.PLAYER_SUBMERGED
        WildNestType.ROCKY -> WildNestVariant.ROCKY_DEFAULT
        WildNestType.SANDY -> WildNestVariant.SANDY_DEFAULT
        WildNestType.SHROOMY -> WildNestVariant.SHROOMY_DEFAULT
        WildNestType.TWIGGY -> WildNestVariant.TWIGGY_DEFAULT
        WildNestType.WATERY -> WildNestVariant.WATERY_DEFAULT
    }

    private fun variantFor(type: WildNestType, baseState: BlockState, submerged: Boolean): WildNestVariant? {
        if (submerged && type != WildNestType.WATERY && type != WildNestType.PLAYER) return null

        return when (type) {
            WildNestType.FIERY -> when {
                baseState.`is`(NEST_SURFACE_FIERY_NETHERRACK) -> WildNestVariant.FIERY_NETHERRACK
                else -> null
            }
            WildNestType.GRASSY -> when {
                baseState.`is`(NEST_SURFACE_GRASSY_CRIMSON_ROOTS) -> WildNestVariant.GRASSY_CRIMSON_ROOTS
                baseState.`is`(NEST_SURFACE_GRASSY_WARPED_ROOTS) -> WildNestVariant.GRASSY_WARPED_ROOTS
                baseState.`is`(NEST_SURFACE_GRASSY_FERN) -> WildNestVariant.GRASSY_FERN
                baseState.`is`(NEST_SURFACE_GRASSY_LEAVES) -> WildNestVariant.GRASSY_LEAVES
                baseState.`is`(NEST_SURFACE_GRASSY_DRY) -> WildNestVariant.GRASSY_DRY
                else -> null
            }
            WildNestType.ICY -> when {
                baseState.`is`(NEST_SURFACE_ICY_ICE) -> WildNestVariant.ICY_ICE
                else -> null
            }
            WildNestType.PLAYER -> if (submerged) WildNestVariant.PLAYER_SUBMERGED else null
            WildNestType.ROCKY -> when {
                baseState.`is`(NEST_SURFACE_ROCKY_AMETHYST) -> WildNestVariant.ROCKY_AMETHYST
                baseState.`is`(NEST_SURFACE_ROCKY_ANDESITE) -> WildNestVariant.ROCKY_ANDESITE
                baseState.`is`(NEST_SURFACE_ROCKY_BASALT) -> WildNestVariant.ROCKY_BASALT
                baseState.`is`(NEST_SURFACE_ROCKY_BLACKSTONE) -> WildNestVariant.ROCKY_BLACKSTONE
                baseState.`is`(NEST_SURFACE_ROCKY_CALCITE) -> WildNestVariant.ROCKY_CALCITE
                baseState.`is`(NEST_SURFACE_ROCKY_DEEPSLATE) -> WildNestVariant.ROCKY_DEEPSLATE
                baseState.`is`(NEST_SURFACE_ROCKY_DIORITE) -> WildNestVariant.ROCKY_DIORITE
                baseState.`is`(NEST_SURFACE_ROCKY_DRIPSTONE) -> WildNestVariant.ROCKY_DRIPSTONE
                baseState.`is`(NEST_SURFACE_ROCKY_END_STONE) -> WildNestVariant.ROCKY_END_STONE
                baseState.`is`(NEST_SURFACE_ROCKY_GRANITE) -> WildNestVariant.ROCKY_GRANITE
                baseState.`is`(NEST_SURFACE_ROCKY_GRAVEL) -> WildNestVariant.ROCKY_GRAVEL
                baseState.`is`(NEST_SURFACE_ROCKY_RED_SANDSTONE) -> WildNestVariant.ROCKY_RED_SANDSTONE
                baseState.`is`(NEST_SURFACE_ROCKY_SANDSTONE) -> WildNestVariant.ROCKY_SANDSTONE
                baseState.`is`(NEST_SURFACE_ROCKY_SCULK) -> WildNestVariant.ROCKY_SCULK
                baseState.`is`(NEST_SURFACE_ROCKY_SNOWY) -> WildNestVariant.ROCKY_SNOWY
                baseState.`is`(NEST_SURFACE_ROCKY_TERRACOTTA) -> WildNestVariant.ROCKY_TERRACOTTA
                baseState.`is`(NEST_SURFACE_ROCKY_TUFF) -> WildNestVariant.ROCKY_TUFF
                else -> null
            }
            WildNestType.SANDY -> when {
                baseState.`is`(NEST_SURFACE_SANDY_RED_SAND) -> WildNestVariant.SANDY_RED_SAND
                baseState.`is`(NEST_SURFACE_SANDY_SOUL_SAND) -> WildNestVariant.SANDY_SOUL_SAND
                else -> null
            }
            WildNestType.SHROOMY -> when {
                baseState.`is`(NEST_SURFACE_SHROOMY_CRIMSON_FUNGUS) -> WildNestVariant.SHROOMY_CRIMSON_FUNGUS
                baseState.`is`(NEST_SURFACE_SHROOMY_WARPED_FUNGUS) -> WildNestVariant.SHROOMY_WARPED_FUNGUS
                else -> null
            }
            WildNestType.TWIGGY -> when {
                baseState.`is`(NEST_SURFACE_TWIGGY_MANGROVE) -> WildNestVariant.TWIGGY_MANGROVE
                baseState.`is`(NEST_SURFACE_TWIGGY_CHORUS) -> WildNestVariant.TWIGGY_CHORUS
                baseState.`is`(NEST_SURFACE_TWIGGY_LEAFY) -> WildNestVariant.TWIGGY_LEAFY
                baseState.`is`(NEST_SURFACE_TWIGGY_DEAD_BUSH) -> WildNestVariant.TWIGGY_DEAD_BUSH
                else -> null
            }
            WildNestType.WATERY -> when {
                baseState.`is`(NEST_SURFACE_WATERY_DRIPLEAF) -> WildNestVariant.WATERY_DRIPLEAF
                !submerged && baseState.`is`(NEST_SURFACE_WATERY_FIREFLY_BUSH) -> WildNestVariant.WATERY_FIREFLY_BUSH
                submerged && baseState.`is`(NEST_SURFACE_WATERY_SEAGRASS_DIRT) -> WildNestVariant.WATERY_SEAGRASS_DIRT
                submerged && baseState.`is`(NEST_SURFACE_WATERY_KELP) -> WildNestVariant.WATERY_KELP
                else -> null
            }
        }
    }

    private fun isAllowedSurface(type: WildNestType, baseState: BlockState, submerged: Boolean): Boolean {
        return when (type) {
            WildNestType.FIERY -> baseState.`is`(NEST_ALLOWED_FIERY)
            WildNestType.GRASSY -> baseState.`is`(NEST_ALLOWED_GRASSY)
            WildNestType.ICY -> baseState.`is`(NEST_ALLOWED_ICY)
            WildNestType.PLAYER -> submerged
            WildNestType.ROCKY -> baseState.`is`(NEST_ALLOWED_ROCKY)
            WildNestType.SANDY -> baseState.`is`(NEST_ALLOWED_SANDY)
            WildNestType.SHROOMY -> baseState.`is`(NEST_ALLOWED_SHROOMY)
            WildNestType.TWIGGY -> baseState.`is`(NEST_ALLOWED_TWIGGY)
            WildNestType.WATERY -> baseState.`is`(NEST_ALLOWED_WATERY)
        }
    }
}
