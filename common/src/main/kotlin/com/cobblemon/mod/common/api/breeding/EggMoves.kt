package com.cobblemon.mod.common.api.breeding

import com.cobblemon.mod.common.api.data.JsonDataRegistry
import com.cobblemon.mod.common.api.moves.MoveTemplate
import com.cobblemon.mod.common.api.moves.Moves
import com.cobblemon.mod.common.api.reactive.SimpleObservable
import com.cobblemon.mod.common.net.messages.client.breeding.EggMoveRegistrySyncPacket
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.adapters.IdentifierAdapter
import com.cobblemon.mod.common.util.cobblemonResource
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.packs.PackType

object EggMoves : JsonDataRegistry<EggMove> {
    override val id = cobblemonResource("egg_moves")
    override val type = PackType.SERVER_DATA
    override val observable = SimpleObservable<EggMoves>()
    override val typeToken: TypeToken<EggMove> = TypeToken.get(EggMove::class.java)
    override val resourcePath = "egg_moves"
    override val gson: Gson = GsonBuilder()
            .registerTypeAdapter(ResourceLocation::class.java, IdentifierAdapter)
            .setPrettyPrinting()
            .create()

    private val registry = mutableMapOf<ResourceLocation, EggMove>()

    override fun sync(player: ServerPlayer) {
        EggMoveRegistrySyncPacket(this.registry.values.toList()).sendToPlayer(player)
    }

    override fun reload(data: Map<ResourceLocation, EggMove>) {
        registry.clear()
        data.forEach { id, eggMove ->
            registry[id] = eggMove
        }
        println("Loaded Egg Move Rules: ${registry.size}")
    }

    // disabled due to possibly being not very performant
    /*fun getEggMovesForParents(
            parent1: EggMove.ParentRules,
            parent2: EggMove.ParentRules,
            baby: ResourceLocation
    ): List<EggMove> {
        return registry.values.filter { eggMove ->
            eggMove.baby == baby &&
                    checkParentRules(eggMove.parent1rules, parent1) &&
                    checkParentRules(eggMove.parent2rules, parent2)
        }
    }*/

    fun getEggMoveList(
            mother: Pokemon,
            father: Pokemon,
            baby: ResourceLocation
    ): List<MoveTemplate> {
        // Filter egg moves to only those relevant to the baby
        val potentialEggMoves = registry.values.filter { it.baby == baby }

        if (potentialEggMoves.isEmpty()) {
            return emptyList()
        }

        // Prepare parent rules for validation
        val parent1Rules = createParentRules(mother)
        val parent2Rules = createParentRules(father)

        // Validate the narrowed list of egg moves against parent rules
        return potentialEggMoves.filter { eggMove ->
            checkParentRules(eggMove.parent1rules, parent1Rules) &&
                    checkParentRules(eggMove.parent2rules, parent2Rules)
        }.mapNotNull { validEggMove ->
            Moves.getByName(validEggMove.eggMove.path)
        }
    }

    /**
     * Helper function to create ParentRules for a Pokémon.
     */
    private fun createParentRules(parent: Pokemon): EggMove.ParentRules {
        return EggMove.ParentRules(
                species = parent.species.resourceIdentifier,
                gender = parent.gender.name.lowercase(),
                hasMove = parent.moveSet.getMoves().firstOrNull()?.template?.name?.let { ResourceLocation.parse(it) },
                hasItem = parent.heldItemNoCopy().item.let { BuiltInRegistries.ITEM.getKey(it) } // if holding nothing it will be minecraft:air
        )
    }

    /**
     * Validate if parent rules match the expected rules.
     */
    private fun checkParentRules(expectedRules: EggMove.ParentRules, actualRules: EggMove.ParentRules): Boolean {
        return (expectedRules.species == null || expectedRules.species == actualRules.species) &&
                (expectedRules.gender == null || expectedRules.gender == actualRules.gender) &&
                (expectedRules.hasMove == null || expectedRules.hasMove == actualRules.hasMove) &&
                (expectedRules.hasItem == null || expectedRules.hasItem == actualRules.hasItem)
    }
}
