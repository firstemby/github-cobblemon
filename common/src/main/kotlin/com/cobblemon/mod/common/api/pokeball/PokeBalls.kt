/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.pokeball

import com.bedrockk.molang.Expression
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.data.JsonDataRegistry
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.molang.ExpressionLike
import com.cobblemon.mod.common.api.pokeball.catching.CaptureEffect
import com.cobblemon.mod.common.api.pokeball.catching.CatchRateModifier
import com.cobblemon.mod.common.api.pokeball.catching.effects.CaptureEffects
import com.cobblemon.mod.common.api.pokeball.catching.effects.FriendshipEarningBoostEffect
import com.cobblemon.mod.common.api.pokeball.catching.modifiers.BaseStatModifier
import com.cobblemon.mod.common.api.pokeball.catching.modifiers.CatchRateModifiers
import com.cobblemon.mod.common.api.pokeball.catching.modifiers.DynamicMultiplierModifier
import com.cobblemon.mod.common.api.pokeball.catching.modifiers.GuaranteedModifier
import com.cobblemon.mod.common.api.pokeball.catching.modifiers.MultiplierModifier
import com.cobblemon.mod.common.api.pokemon.labels.CobblemonPokemonLabels
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.api.pokemon.status.Statuses
import com.cobblemon.mod.common.api.reactive.SimpleObservable
import com.cobblemon.mod.common.api.types.ElementalTypes
import com.cobblemon.mod.common.item.components.PokeBallComponent
import com.cobblemon.mod.common.pokeball.PokeBall
import com.cobblemon.mod.common.util.adapters.CobblemonCaptureEffectAdapter
import com.cobblemon.mod.common.util.adapters.CobblemonCatchRateModifierAdapter
import com.cobblemon.mod.common.util.adapters.ExpressionAdapter
import com.cobblemon.mod.common.util.adapters.ExpressionLikeAdapter
import com.cobblemon.mod.common.util.adapters.FloatNumberRangeAdapter
import com.cobblemon.mod.common.util.adapters.IdentifierAdapter
import com.cobblemon.mod.common.util.adapters.IntRangeAdapter
import com.cobblemon.mod.common.util.adapters.LiteralHexColorAdapter
import com.cobblemon.mod.common.util.asExpressionLike
import com.cobblemon.mod.common.util.cobblemonResource
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import net.minecraft.advancements.critereon.MinMaxBounds
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.packs.PackType
import net.minecraft.util.LowerCaseEnumTypeAdapterFactory
import java.awt.Color
import kotlin.math.roundToInt

/**
 * The data registry for [PokeBall]s.
 * All the pokeball fields are guaranteed to exist
 */
object PokeBalls : JsonDataRegistry<PokeBall> {

    override val id = cobblemonResource("pokeballs")
    override val type = PackType.SERVER_DATA
    override val observable = SimpleObservable<PokeBalls>()

    // ToDo once datapack pokeball is implemented add required adapters here
    override val gson: Gson = GsonBuilder()
        .registerTypeAdapter(ResourceLocation::class.java, IdentifierAdapter)
        .registerTypeAdapter(Expression::class.java, ExpressionAdapter)
        .registerTypeAdapter(ExpressionLike::class.java, ExpressionLikeAdapter)
        .registerTypeAdapter(IntRange::class.java, IntRangeAdapter)
        .registerTypeAdapter(MinMaxBounds.Doubles::class.java, FloatNumberRangeAdapter)
        .registerTypeAdapter(CatchRateModifier::class.java, CobblemonCatchRateModifierAdapter)
        .registerTypeAdapter(CaptureEffect::class.java, CobblemonCaptureEffectAdapter)
        .registerTypeAdapter(Color::class.java, LiteralHexColorAdapter)
        .registerTypeAdapterFactory(LowerCaseEnumTypeAdapterFactory())
        .disableHtmlEscaping()
        .setPrettyPrinting()
        .create()
    override val typeToken: TypeToken<PokeBall> = TypeToken.get(PokeBall::class.java)
    override val resourcePath = "pokeballs"

    private val defaults = hashMapOf<ResourceLocation, PokeBall>()

    val custom = hashMapOf<ResourceLocation, PokeBall>()

    @JvmStatic
    @get:JvmName("getPokeBall")
    val POKE_BALL
        get() = this.byName("poke_ball")
    @JvmStatic
    @get:JvmName("getSlateBall")
    val SLATE_BALL
        get() = this.byName("slate_ball")
    @JvmStatic
    @get:JvmName("getAzureBall")
    val AZURE_BALL
        get() = this.byName("azure_ball")
    @JvmStatic
    @get:JvmName("getVerdantBall")
    val VERDANT_BALL
        get() = this.byName("verdant_ball")
    @JvmStatic
    @get:JvmName("getRoseateBall")
    val ROSEATE_BALL
        get() = this.byName("roseate_ball")
    @JvmStatic
    @get:JvmName("getCitrineBall")
    val CITRINE_BALL
        get() = this.byName("citrine_ball")
    @JvmStatic
    @get:JvmName("getGreatBall")
    val GREAT_BALL
        get() = this.byName("great_ball")
    @JvmStatic
    @get:JvmName("getUltraBall")
    val ULTRA_BALL
        get() = this.byName("ultra_ball")
    @JvmStatic
    @get:JvmName("getMasterBall")
    val MASTER_BALL
        get() = this.byName("master_ball")
    @JvmStatic
    @get:JvmName("getSafariBall")
    val SAFARI_BALL
        get() = this.byName("safari_ball")
    @JvmStatic
    @get:JvmName("getFastBall")
    val FAST_BALL
        get() = this.byName("fast_ball")
    @JvmStatic
    @get:JvmName("getLevelBall")
    val LEVEL_BALL
        get() = this.byName("level_ball")
    @JvmStatic
    @get:JvmName("getLureBall")
    val LURE_BALL
        get() = this.byName("lure_ball")
    @JvmStatic
    @get:JvmName("getHeavyBall")
    val HEAVY_BALL
        get() = this.byName("heavy_ball")
    @JvmStatic
    @get:JvmName("getLoveBall")
    val LOVE_BALL
        get() = this.byName("love_ball")
    @JvmStatic
    @get:JvmName("getFriendBall")
    val FRIEND_BALL
        get() = this.byName("friend_ball")
    @JvmStatic
    @get:JvmName("getMoonBall")
    val MOON_BALL
        get() = this.byName("moon_ball")
    @JvmStatic
    @get:JvmName("getSportBall")
    val SPORT_BALL
        get() = this.byName("sport_ball")
    @JvmStatic
    @get:JvmName("getNetBall")
    val NET_BALL
        get() = this.byName("net_ball")
    @JvmStatic
    @get:JvmName("getDiveBall")
    val DIVE_BALL
        get() = this.byName("dive_ball")
    @JvmStatic
    @get:JvmName("getNestBall")
    val NEST_BALL
        get() = this.byName("nest_ball")
    @JvmStatic
    @get:JvmName("getRepeatBall")
    val REPEAT_BALL
        get() = this.byName("repeat_ball")
    @JvmStatic
    @get:JvmName("getTimerBall")
    val TIMER_BALL
        get() = this.byName("timer_ball")
    @JvmStatic
    @get:JvmName("getLuxuryBall")
    val LUXURY_BALL
        get() = this.byName("luxury_ball")
    @JvmStatic
    @get:JvmName("getPremierBall")
    val PREMIER_BALL
        get() = this.byName("premier_ball")
    @JvmStatic
    @get:JvmName("getDuskBall")
    val DUSK_BALL
        get() = this.byName("dusk_ball")
    @JvmStatic
    @get:JvmName("getHealBall")
    val HEAL_BALL
        get() = this.byName("heal_ball")
    @JvmStatic
    @get:JvmName("getQuickBall")
    val QUICK_BALL
        get() = this.byName("quick_ball")
    @JvmStatic
    @get:JvmName("getCherishBall")
    val CHERISH_BALL
        get() = this.byName("cherish_ball")
    @JvmStatic
    @get:JvmName("getParkBall")
    val PARK_BALL
        get() = this.byName("park_ball")
    @JvmStatic
    @get:JvmName("getDreamBall")
    val DREAM_BALL
        get() = this.byName("dream_ball")
    @JvmStatic
    @get:JvmName("getBeastBall")
    val BEAST_BALL
        get() = this.byName("beast_ball")
    @JvmStatic
    @get:JvmName("getAncientPokeBall")
    val ANCIENT_POKE_BALL
        get() = this.byName("ancient_poke_ball")
    @JvmStatic
    @get:JvmName("getAncientCitrineBall")
    val ANCIENT_CITRINE_BALL
        get() = this.byName("ancient_citrine_ball")
    @JvmStatic
    @get:JvmName("getAncientVerdantBall")
    val ANCIENT_VERDANT_BALL
        get() = this.byName("ancient_verdant_ball")
    @JvmStatic
    @get:JvmName("getAncientAzureBall")
    val ANCIENT_AZURE_BALL
        get() = this.byName("ancient_azure_ball")
    @JvmStatic
    @get:JvmName("getAncientRoseateBall")
    val ANCIENT_ROSEATE_BALL
        get() = this.byName("ancient_roseate_ball")
    @JvmStatic
    @get:JvmName("getAncientSlateBall")
    val ANCIENT_SLATE_BALL
        get() = this.byName("ancient_slate_ball")
    @JvmStatic
    @get:JvmName("getAncientIvoryBall")
    val ANCIENT_IVORY_BALL
        get() = this.byName("ancient_ivory_ball")
    @JvmStatic
    @get:JvmName("getAncientGreatBall")
    val ANCIENT_GREAT_BALL
        get() = this.byName("ancient_great_ball")
    @JvmStatic
    @get:JvmName("getAncientUltraBall")
    val ANCIENT_ULTRA_BALL
        get() = this.byName("ancient_ultra_ball")
    @JvmStatic
    @get:JvmName("getAncientHeavyBall")
    val ANCIENT_HEAVY_BALL
        get() = this.byName("ancient_heavy_ball")
    @JvmStatic
    @get:JvmName("getAncientLeadenBall")
    val ANCIENT_LEADEN_BALL
        get() = this.byName("ancient_leaden_ball")
    @JvmStatic
    @get:JvmName("getAncientGigatonBall")
    val ANCIENT_GIGATON_BALL
        get() = this.byName("ancient_gigaton_ball")
    @JvmStatic
    @get:JvmName("getAncientFeatherBall")
    val ANCIENT_FEATHER_BALL
        get() = this.byName("ancient_feather_ball")
    @JvmStatic
    @get:JvmName("getAncientWingBall")
    val ANCIENT_WING_BALL
        get() = this.byName("ancient_wing_ball")
    @JvmStatic
    @get:JvmName("getAncientJetBall")
    val ANCIENT_JET_BALL
        get() = this.byName("ancient_jet_ball")
    @JvmStatic
    @get:JvmName("getAncientOriginBall")
    val ANCIENT_ORIGIN_BALL
        get() = this.byName("ancient_origin_ball")
    @JvmStatic
    @get:JvmName("getStrangeBall")
    val STRANGE_BALL
        get() = this.byName("strange_ball")

    init {
        createDefault("poke_ball")
        createDefault("slate_ball")
        createDefault("azure_ball")
        createDefault("verdant_ball")
        createDefault("roseate_ball")
        createDefault("citrine_ball")
        createDefault("great_ball", MultiplierModifier(1.5F))
        createDefault("ultra_ball", MultiplierModifier(2F))
        createDefault("master_ball", GuaranteedModifier())
        createDefault("safari_ball", CatchRateModifiers.SAFARI)
        createDefault("fast_ball", BaseStatModifier(Stats.SPEED, 100, BaseStatModifier.Comparator.GTE, 4F))
        createDefault("level_ball", CatchRateModifiers.LEVEL)
        createDefault("lure_ball", CatchRateModifiers.LURE)
        createDefault("heavy_ball", CatchRateModifiers.WEIGHT_BASED)
        createDefault("love_ball", CatchRateModifiers.LOVE)
        createDefault("friend_ball", effects = listOf("q.pokemon.set_friendship(150.0)".asExpressionLike()))
        createDefault("moon_ball", CatchRateModifiers.MOON_PHASES)
        createDefault("sport_ball", MultiplierModifier(1.5F))
        createDefault("net_ball", CatchRateModifiers.typeBoosting(3F, ElementalTypes.BUG, ElementalTypes.WATER))
        createDefault("dive_ball", CatchRateModifiers.SUBMERGED_IN_WATER, waterDragValue = 0.99F)
        createDefault("nest_ball", CatchRateModifiers.NEST)
        createDefault("repeat_ball", CatchRateModifiers.REPEAT)
        createDefault("timer_ball", CatchRateModifiers.turnBased { turn -> (1F + turn * (1229F / 4096F)).coerceAtMost(4F) })
        createDefault("luxury_ball", /*effects = listOf(FriendshipEarningBoostEffect(2F))*/)
        createDefault("premier_ball")
        createDefault("dusk_ball", CatchRateModifiers.LIGHT_LEVEL)
        createDefault("heal_ball", effects = listOf("q.pokemon.heal()".asExpressionLike()))
        createDefault("quick_ball", CatchRateModifiers.turnBased { turn -> if (turn == 1) 5F else 1F })
        createDefault("cherish_ball")
        createDefault("park_ball", CatchRateModifiers.PARK)
        createDefault("dream_ball", CatchRateModifiers.statusBoosting(4F, Statuses.SLEEP))
        createDefault("beast_ball", DynamicMultiplierModifier({ _, pokemon -> if (pokemon.hasLabels(CobblemonPokemonLabels.ULTRA_BEAST)) 5F else 0.1F }) { _, _ -> true })
        createDefault("ancient_poke_ball", ancient = true)
        createDefault("ancient_citrine_ball", ancient = true)
        createDefault("ancient_verdant_ball", ancient = true)
        createDefault("ancient_azure_ball", ancient = true)
        createDefault("ancient_roseate_ball", ancient = true)
        createDefault("ancient_slate_ball", ancient = true)
        createDefault("ancient_ivory_ball", ancient = true)
        createDefault("ancient_great_ball", MultiplierModifier(1.5F), ancient = true)
        createDefault("ancient_ultra_ball", MultiplierModifier(2F), ancient = true)
        createDefault("ancient_heavy_ball", throwPower = 0.75f, ancient = true)
        createDefault("ancient_leaden_ball", MultiplierModifier(1.5F), throwPower = 0.75f, ancient = true)
        createDefault("ancient_gigaton_ball", MultiplierModifier(2F), throwPower = 0.75f, ancient = true)
        createDefault("ancient_feather_ball", throwPower = 2.5f, ancient = true)
        createDefault("ancient_wing_ball", MultiplierModifier(1.5F), throwPower = 2.5f, ancient = true)
        createDefault("ancient_jet_ball", MultiplierModifier(2F), throwPower = 2.5f, ancient = true)
        createDefault("ancient_origin_ball", GuaranteedModifier(), ancient = true)
        // for customs
        createDefault("strange_ball")

        // Luxury ball effect, low priority as it must be triggered before soothe bell as of gen 4
        CobblemonEvents.FRIENDSHIP_UPDATED.subscribe(priority = Priority.LOW) { event ->
            if (!event.earned) {
                return@subscribe
            }
            var increment = (event.newFriendship - event.pokemon.friendship).toFloat()
            if (increment <= 0) {
                return@subscribe
            }
            event.pokemon.caughtBall.effects.filterIsInstance<FriendshipEarningBoostEffect>()
                .forEach { increment *= it.multiplier }
            event.newFriendship = event.pokemon.friendship + increment.roundToInt()
        }
    }

    override fun reload(data: Map<ResourceLocation, PokeBall>) {
        data.entries.forEach { it.value.name = it.key }
        this.custom.clear()

        data.forEach { (location, ball) ->
            if (defaults.containsKey(location)) {
                ball.stack = defaults[location]!!.stack
            } else {
                ball.stack = PokeBallComponent.createStack(ball)
            }

            this.custom[location] = ball
        }

        Cobblemon.LOGGER.info("Loaded ${data.size} custom poke balls.")
    }

    override fun sync(player: ServerPlayer) {
        // ToDo once datapack pokeball is implemented sync them here
    }

    /**
     * Gets a Pokeball from registry name.
     * @return the pokeball object if found otherwise strange ball.
     */
    @JvmStatic
    fun getPokeBall(name : ResourceLocation): PokeBall =
        this.custom[name] ?: this.defaults[name] ?: STRANGE_BALL

    @JvmStatic
    fun all() = this.defaults.filterKeys { !this.custom.containsKey(it) }.values + this.custom.values

    private fun createDefault(
        name: String,
        modifier: CatchRateModifier = MultiplierModifier(1F) { _, _ -> true },
        effects: List<ExpressionLike> = emptyList(),
        waterDragValue: Float = 0.8F,
        model2d: ResourceLocation = cobblemonResource(name),
        model3d: ResourceLocation = cobblemonResource("item/${name}_model"),
        throwPower: Float = 1.25f,
        ancient: Boolean = false,
        color: Color = Color.decode("#ff1111")
    ): PokeBall {
        val identifier = cobblemonResource(name)
        val pokeball = PokeBall(identifier, modifier, effects, PokeBall.PokeBallProperties(), waterDragValue, model2d, model3d, throwPower, ancient, color)
        this.defaults[identifier] = pokeball
        return pokeball
    }

    fun byName(name: String): PokeBall {
        val identifier = cobblemonResource(name)
        return this.custom[identifier] ?: this.defaults[identifier]!!
    }
}
