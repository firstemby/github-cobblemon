/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.molang

import com.bedrockk.molang.runtime.MoLangRuntime
import com.bedrockk.molang.runtime.MoParams
import com.bedrockk.molang.runtime.struct.ArrayStruct
import com.bedrockk.molang.runtime.struct.QueryStruct
import com.bedrockk.molang.runtime.struct.VariableStruct
import com.bedrockk.molang.runtime.value.DoubleValue
import com.bedrockk.molang.runtime.value.MoValue
import com.bedrockk.molang.runtime.value.StringValue
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.battles.interpreter.BattleMessage
import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor
import com.cobblemon.mod.common.api.drop.DropEntry
import com.cobblemon.mod.common.api.molang.function.BattleActorMoLangFunctions
import com.cobblemon.mod.common.api.molang.function.BattleMessageMoLangFunctions
import com.cobblemon.mod.common.api.molang.function.BattleMoLangFunctions
import com.cobblemon.mod.common.api.molang.function.BiomeMoLangFunctions
import com.cobblemon.mod.common.api.molang.function.BlockMoLangFunctions
import com.cobblemon.mod.common.api.molang.function.DimensionTypeMoLangFunctions
import com.cobblemon.mod.common.api.molang.function.DropEntryMoLangFunctions
import com.cobblemon.mod.common.api.molang.function.EntityMoLangFunctions
import com.cobblemon.mod.common.api.molang.function.EvolutionMoLangFunctions
import com.cobblemon.mod.common.api.molang.function.GeneralMoLangFunctions
import com.cobblemon.mod.common.api.molang.function.ItemStackMoLangFunctions
import com.cobblemon.mod.common.api.molang.function.LivingEntityMoLangFunctions
import com.cobblemon.mod.common.api.molang.function.NPCMoLangFunctions
import com.cobblemon.mod.common.api.molang.function.PCMoLangFunctions
import com.cobblemon.mod.common.api.molang.function.PartyMoLangFunctions
import com.cobblemon.mod.common.api.molang.function.PlayerMoLangFunctions
import com.cobblemon.mod.common.api.molang.function.PokedexMoLangFunctions
import com.cobblemon.mod.common.api.molang.function.PokemonEntityMoLangFunctions
import com.cobblemon.mod.common.api.molang.function.PokemonMoLangFunctions
import com.cobblemon.mod.common.api.molang.function.PokemonPropertiesMoLangFunctions
import com.cobblemon.mod.common.api.molang.function.PokemonStoreMoLangFunctions
import com.cobblemon.mod.common.api.molang.function.ServerMoLangFunctions
import com.cobblemon.mod.common.api.molang.function.SpawnablePositionMoLangFunctions
import com.cobblemon.mod.common.api.molang.function.SpeciesMoLangFunctions
import com.cobblemon.mod.common.api.molang.function.WorldMoLangFunctions
import com.cobblemon.mod.common.api.pokedex.AbstractPokedexManager
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.pokemon.evolution.Evolution
import com.cobblemon.mod.common.api.scripting.CobblemonScripts
import com.cobblemon.mod.common.api.spawning.position.SpawnablePosition
import com.cobblemon.mod.common.api.storage.PokemonStore
import com.cobblemon.mod.common.api.storage.party.PartyStore
import com.cobblemon.mod.common.api.storage.pc.PCStore
import com.cobblemon.mod.common.entity.npc.NPCEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.Species
import com.cobblemon.mod.common.util.effectiveName
import com.mojang.datafixers.util.Either
import net.minecraft.core.Holder
import net.minecraft.core.Registry
import net.minecraft.core.RegistryAccess
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.DoubleTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.StringTag
import net.minecraft.nbt.Tag
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.TagKey
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.dimension.DimensionType
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.level.pathfinder.PathType
import net.minecraft.world.level.storage.loot.LootParams
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets
import net.minecraft.world.level.storage.loot.parameters.LootContextParams
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.function.Function

/**
 * Holds a bunch of useful MoLang trickery that can be used or extended in API
 *
 * @author Hiroku
 * @since October 2nd, 2023
 */
object MoLangFunctions {
    fun ItemStack.asMoLangValue(registryAccess: RegistryAccess) = ObjectValue(this)
        .addStandardFunctions()
        .addFunctions(ItemStackMoLangFunctions.attach(Pair(this, registryAccess)))

    fun Holder<Biome>.asBiomeMoLangValue() = asMoLangValue(Registries.BIOME)
        .addFunctions(BiomeMoLangFunctions.attach(this))

    fun Holder<Level>.asWorldMoLangValue() = asMoLangValue(Registries.DIMENSION)
        .addFunctions(WorldMoLangFunctions.attach(this))

    fun Holder<Block>.asBlockMoLangValue() = asMoLangValue(Registries.BLOCK)
        .addFunctions(BlockMoLangFunctions.attach(this))

    fun MinecraftServer.asMoLangValue() = ObjectValue(this)
        .addStandardFunctions()
        .addFunctions(ServerMoLangFunctions.attach(this))

    fun Holder<DimensionType>.asDimensionTypeMoLangValue() = asMoLangValue(Registries.DIMENSION_TYPE)
        .addFunctions(DimensionTypeMoLangFunctions.attach(this))

    fun Player.asMoLangValue(): ObjectValue<Player> {
        if (this is ServerPlayer && uuid in Cobblemon.serverPlayerStructs) {
            val existing = Cobblemon.serverPlayerStructs[uuid]!!
            if (existing.obj == this) {
                return existing
            } else {
                Cobblemon.serverPlayerStructs.remove(uuid)
            }
        }

        val value = ObjectValue(
            obj = this,
            stringify = { it.effectiveName().string }
        )

        value.addFunctions(EntityMoLangFunctions.attach(this))
        value.addFunctions(LivingEntityMoLangFunctions.attach(this))
        value.addFunctions(PlayerMoLangFunctions.attach(this))

        if (this is ServerPlayer) {
            Cobblemon.serverPlayerStructs[uuid] = value
        }

        return value
    }

    // We need to migrate the writeVariables thing to be all about query structs, variable doesn't make sense and I don't want to break fringe 1.6 compatibility issues close to release
    fun Pokemon.asStruct(): ObjectValue<Pokemon> {
        val queryStruct = ObjectValue(this)
        queryStruct.addFunctions(PokemonMoLangFunctions.attach(this))
        return queryStruct
    }

    fun PartyStore.asMoLangValue(): ObjectValue<PartyStore> {
        val value = ObjectValue(
            obj = this,
            stringify = { it.toString() }
        )
        value.addFunctions(PokemonStoreMoLangFunctions.attach(this))
        value.addFunctions(PartyMoLangFunctions.attach(this))
        return value
    }

    fun DropEntry.asMoLangValue(): ObjectValue<DropEntry> {
        val value = ObjectValue(
            obj = this,
            stringify = { it.toString() }
        )
        value.addFunctions(DropEntryMoLangFunctions.attach(this))
        return value
    }

    fun PokemonProperties.asMoLangValue(): ObjectValue<PokemonProperties> {
        val value = ObjectValue(
            obj = this,
            stringify = { it.asString() }
        )
        value.addFunctions(PokemonPropertiesMoLangFunctions.attach(this))
        return value
    }

    fun Evolution.asMoLangValue(): ObjectValue<Evolution> {
        val value = ObjectValue(
            obj = this,
            stringify = { it.toString() }
        )
        value.addFunctions(EvolutionMoLangFunctions.attach(this))
        return value
    }

    fun PCStore.asMoLangValue(): ObjectValue<PCStore> {
        val value = ObjectValue(
            obj = this,
            stringify = { it.toString() }
        )
        value.addFunctions(PokemonStoreMoLangFunctions.attach(this))
        value.addFunctions(PCMoLangFunctions.attach(this))
        return value
    }

    fun NPCEntity.asMoLangValue(): ObjectValue<NPCEntity> {
        val value = ObjectValue(
            obj = this,
            stringify = { it.name.string }
        )
        value.addFunctions(EntityMoLangFunctions.attach(this))
        value.addFunctions(LivingEntityMoLangFunctions.attach(this))
        value.addFunctions(NPCMoLangFunctions.attach(this))
        return value
    }

    fun PokemonEntity.asMoLangValue(): ObjectValue<PokemonEntity> {
        val value = ObjectValue(
            obj = this,
            stringify = { it.pokemon.uuid.toString() }
        )

        value.addFunctions(EntityMoLangFunctions.attach(this))
        value.addFunctions(LivingEntityMoLangFunctions.attach(this))
        value.addFunctions(PokemonEntityMoLangFunctions.attach(this))
        value.addFunctions(PokemonMoLangFunctions.attach(this.pokemon))

        return value
    }

    fun PokemonBattle.asMoLangValue(): ObjectValue<PokemonBattle> {
        val value = ObjectValue(
            obj = this,
            stringify = { it.battleId.toString() }
        )
        value.addFunctions(BattleMoLangFunctions.attach(this))
        return value
    }

    fun BattleActor.asMoLangValue(): ObjectValue<BattleActor> {
        val value = ObjectValue(
            obj = this,
            stringify = { it.toString() }
        )
        value.addFunctions(BattleActorMoLangFunctions.attach(this))
        return value
    }

    fun SpawnablePosition.asMoLangValue(): ObjectValue<SpawnablePosition> {
        val value = ObjectValue(
            obj = this,
            stringify = { it.toString() }
        )
        value.addFunctions(SpawnablePositionMoLangFunctions.attach(this))
        return value
    }

    fun Vec3.asMoLangValue(): ObjectValue<Vec3> {
        val value = ObjectValue(
            obj = this
        )
        value.addFunction("x") { this.x }
        value.addFunction("y") { this.y }
        value.addFunction("z") { this.z }

        return value
    }

    /**
     * Different functions exist depending on the entity type, this tries to make the struct that's most specific to the type.
     */
    fun Entity.asMostSpecificMoLangValue(): ObjectValue<out Entity> {
        return when (this) {
            is Player -> asMoLangValue()
            is PokemonEntity -> struct
            is NPCEntity -> struct
            is ItemEntity -> ObjectValue(this).also { item ->
                item.addStandardFunctions()
                item.addFunctions(EntityMoLangFunctions.attach(this))
                item.addFunctions(ItemStackMoLangFunctions.attach(Pair(this.item, this.registryAccess())))
            }

            else -> ObjectValue(this).also { objectValue ->
                objectValue.addStandardFunctions()
                objectValue.addFunctions(EntityMoLangFunctions.attach(this))
                if (this is LivingEntity) {
                    objectValue.addFunctions(LivingEntityMoLangFunctions.attach(this))
                }
            }
        }
    }

    fun <T> Holder<T>.asMoLangValue(key: ResourceKey<Registry<T>>): ObjectValue<Holder<T>> {
        val value = ObjectValue(
            obj = this,
            stringify = { it.unwrapKey().get().location().toString() }
        )
        value.functions["is_in"] = Function put@{ params: MoParams ->
            val tag = TagKey.create(key, ResourceLocation.parse(params.getString(0).replace("#", "")))
            return@put DoubleValue(if (value.obj.`is`(tag)) 1.0 else 0.0)
        }
        value.functions["is_of"] = Function put@{ params: MoParams ->
            val identifier = ResourceLocation.parse(params.getString(0))
            return@put DoubleValue(if (value.obj.`is`(identifier)) 1.0 else 0.0)
        }
        return value
    }

    fun QueryStruct.addStandardFunctions(): QueryStruct {
        addFunctions(GeneralMoLangFunctions.holder)
        return this
    }

    fun QueryStruct.addEntityFunctions(entity: Entity): QueryStruct {
        val addedFunctions = EntityMoLangFunctions.attach(entity)
        addFunctions(addedFunctions)
        return this
    }

    fun QueryStruct.addLivingEntityFunctions(entity: LivingEntity): QueryStruct {
        val addedFunctions = LivingEntityMoLangFunctions.attach(entity)
        addFunctions(addedFunctions)
        return this
    }

    fun QueryStruct.addPokedexFunctions(pokedexManager: AbstractPokedexManager): QueryStruct {
        val addedFunctions = PokedexMoLangFunctions.attach(pokedexManager)
        addFunctions(addedFunctions)
        return this
    }

    fun QueryStruct.addSpeciesFunctions(species: Species): QueryStruct {
        val addedFunctions = SpeciesMoLangFunctions.attach(species)
        addFunctions(addedFunctions)
        return this
    }

    fun QueryStruct.addPokemonFunctions(pokemon: Pokemon): QueryStruct {
        val addedFunctions = PokemonMoLangFunctions.attach(pokemon)
        addFunctions(addedFunctions)
        return this
    }

    fun QueryStruct.addBattleMessageFunctions(battleMessage: BattleMessage): QueryStruct {
        addFunctions(BattleMessageMoLangFunctions.attach(battleMessage))

        return this
    }

    fun QueryStruct.addPokemonEntityFunctions(pokemonEntity: PokemonEntity): QueryStruct {
        val addedFunctions = PokemonEntityMoLangFunctions.attach(pokemonEntity)
        addFunctions(addedFunctions)
        pokemonEntity.registerFunctionsForScripting(this)
        return this
    }

    fun QueryStruct.addPokemonStoreFunctions(store: PokemonStore<*>): QueryStruct {
        val addedFunctions = PokemonStoreMoLangFunctions.attach(store)
        addFunctions(addedFunctions)
        return this
    }

    fun <T : QueryStruct> T.addFunctions(functions: Map<String, Function<MoParams, Any>>): T {
        this.functions.putAll(functions)
        return this
    }

    @JvmName("addFunctionsKt")
    fun <T : QueryStruct> T.addFunctions(functions: Map<String, (MoParams) -> Any>): T {
        functions.forEach { (string, function) ->
            this.functions[string] = Function(function)
        }

        return this
    }

    fun moLangFunctionMap(
        vararg functions: Pair<String, (MoParams) -> MoValue>
    ): Map<String, (MoParams) -> MoValue> {
        return functions.toMap()
    }

    fun queryStructOf(
        vararg functions: Pair<String, (MoParams) -> MoValue>
    ): QueryStruct {
        return QueryStruct(
            hashMapOf<String, Function<MoParams, Any>>(
                *functions.map { (name, func) ->
                    name to Function<MoParams, Any> { params -> func(params) }
                }.toTypedArray()
            )
        )
    }

    fun MoLangRuntime.setup(): MoLangRuntime {
        environment.query.addStandardFunctions()
        return this
    }

    fun writeMoValueToNBT(value: MoValue): Tag? {
        return when (value) {
            is DoubleValue -> DoubleTag.valueOf(value.value)
            is StringValue -> StringTag.valueOf(value.value)
            is ArrayStruct -> {
                val list = value.map.values
                val nbtList = ListTag()
                list.mapNotNull(::writeMoValueToNBT).forEach(nbtList::add)
                nbtList
            }

            is VariableStruct -> {
                val nbt = CompoundTag()
                value.map.forEach { (key, value) ->
                    val element = writeMoValueToNBT(value) ?: return@forEach
                    nbt.put(key, element)
                }
                nbt
            }

            else -> null
        }
    }

    fun readMoValueFromNBT(nbt: Tag): MoValue {
        return when (nbt) {
            is DoubleTag -> DoubleValue(nbt.asDouble)
            is StringTag -> StringValue(nbt.asString)
            is ListTag -> {
                val array = ArrayStruct(hashMapOf())
                var index = 0
                nbt.forEach { element ->
                    val value = readMoValueFromNBT(element)
                    array.setDirectly("$index", value)
                    index++
                }
                array
            }

            is CompoundTag -> {
                val variable = VariableStruct(hashMapOf())
                nbt.allKeys.toList().forEach { key ->
                    val value = readMoValueFromNBT(nbt[key]!!)
                    variable.map[key] = value
                }
                variable
            }

            else -> VariableStruct()
        }
    }
}

// TODO: this one is unused. Should we move it to [MoLangFunctions] object?
fun Either<ResourceLocation, ExpressionLike>.runScript(runtime: MoLangRuntime) =
    map({ CobblemonScripts.run(it, runtime) }, { it.resolve(runtime) })