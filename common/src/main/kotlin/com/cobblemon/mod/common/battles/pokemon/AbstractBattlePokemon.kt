package com.cobblemon.mod.common.battles.pokemon

import com.bedrockk.molang.runtime.struct.QueryStruct
import com.bedrockk.molang.runtime.value.DoubleValue
import com.bedrockk.molang.runtime.value.StringValue
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor
import com.cobblemon.mod.common.api.moves.MoveSet
import com.cobblemon.mod.common.api.pokemon.helditem.HeldItemManager
import com.cobblemon.mod.common.api.pokemon.stats.Stat
import com.cobblemon.mod.common.battles.interpreter.ContextManager
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.Nature
import net.minecraft.network.chat.MutableComponent
import java.util.UUID
import java.util.function.Function

abstract class AbstractBattlePokemon {
    lateinit var actor: BattleActor
    val struct = QueryStruct(
        hashMapOf(
            "actor" to Function { actor.struct },
            "battle" to Function { actor.battle.struct },
            "uuid" to Function { StringValue(uuid.toString()) },
            "health" to Function { DoubleValue(health.toDouble()) },
            "max_health" to Function { DoubleValue(maxHealth.toDouble()) },
            "nature" to Function { StringValue(nature.name.toString()) },
            "moveset" to Function { moveSet.toStruct() }
    ))

    val statChanges = mutableMapOf<Stat, Int>()
    //For when a wild pokemon is being captured in a ball
    var gone = false

    abstract val uuid: UUID
    abstract var health: Int
    abstract val maxHealth: Int
    abstract val nature: Nature
    abstract val moveSet: MoveSet
    abstract val entity: PokemonEntity?
    abstract val heldItemManager: HeldItemManager

    abstract fun canBeSentOut(): Boolean
    abstract fun getName(): MutableComponent
    abstract fun sendUpdate()

    /** The set of all the BattlePokemon that they faced during the battle (for exp purposes) */
    val facedOpponents = mutableSetOf<BattlePokemon>()
    val contextManager = ContextManager()

    fun isSentOut() = actor.battle.activePokemon.any { it.battlePokemon == this }
    fun getIllusion(): AbstractBattlePokemon? = this.actor.activePokemon.find { it.battlePokemon == this }?.illusion
}