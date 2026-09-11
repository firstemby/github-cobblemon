package com.cobblemon.mod.common.battle

import com.cobblemon.mod.common.api.abilities.Abilities
import com.cobblemon.mod.common.api.abilities.AbilityTemplate
import com.cobblemon.mod.common.api.battles.interpreter.BattleMessage
import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.battles.dispatch.InstructionSet
import com.cobblemon.mod.common.battles.interpreter.instructions.AbilityInstruction
import com.cobblemon.mod.common.battles.interpreter.instructions.WeatherInstruction
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon
import com.cobblemon.mod.common.junit.BootstrapMinecraft
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

@BootstrapMinecraft
class BattleAbilityRevealTest {
    private class BattleFixture {
        val uuid = UUID.randomUUID()
        val battle = mockk<PokemonBattle>(relaxed = true)
        val pokemon = mockk<BattlePokemon>(relaxed = true)
        val reveals = mutableListOf<() -> Unit>()

        init {
            every { battle.getBattlePokemon("p2a", uuid.toString()) } returns pokemon
            every { battle.dispatchGo(capture(reveals)) } just Runs
        }

        fun assertRevealed(id: String) {
            verify(exactly = 0) { pokemon.sendUpdate() }
            reveals.forEach { it() }
            verifyOrder {
                pokemon.revealedAbility = id
                pokemon.sendUpdate()
            }
            assertNotNull(Abilities.get(id), "The revealed ID must resolve in the client ability registry")
        }
    }

    @Test
    fun `weather abilities reveal registry IDs instead of display names`() {
        for ((weather, name, id) in listOf(
            Triple("Sandstorm", "Sand Stream", "sandstream"),
            Triple("RainDance", "Drizzle", "drizzle"),
            Triple("SunnyDay", "Drought", "drought"),
            Triple("Snow", "Snow Warning", "snowwarning")
        )) {
            Abilities.register(AbilityTemplate(id))
            val fixture = BattleFixture()
            val message = BattleMessage("|-weather|$weather|[from] ability: $name|[of] p2a: ${fixture.uuid}")
            WeatherInstruction(message).invoke(fixture.battle)
            fixture.assertRevealed(id)
        }
    }

    @Test
    fun `direct ability announcements without a type prefix reveal the ability`() {
        Abilities.register(AbilityTemplate("unnerve"))
        val fixture = BattleFixture()
        val message = BattleMessage("|-ability|p2a: ${fixture.uuid}|Unnerve")
        AbilityInstruction(InstructionSet(), message).invoke(fixture.battle)
        fixture.assertRevealed("unnerve")
    }

    @Test
    fun `trace reveals the acquired ability rather than the announcement cause`() {
        Abilities.register(AbilityTemplate("trace"))
        Abilities.register(AbilityTemplate("sandstream"))
        val fixture = BattleFixture()
        val message = BattleMessage("|-ability|p2a: ${fixture.uuid}|Sand Stream|[from] ability: Trace")
        AbilityInstruction(InstructionSet(), message).invoke(fixture.battle)
        fixture.assertRevealed("sandstream")
        verify(exactly = 0) { fixture.pokemon.revealedAbility = "trace" }
    }

    @Test
    fun `weather without an ability source does not reveal hidden abilities`() {
        val fixture = BattleFixture()
        for (rawMessage in listOf(
            "|-weather|Sandstorm|[of] p2a: ${fixture.uuid}",
            "|-weather|Sandstorm|[upkeep]",
            "|-weather|none"
        )) {
            WeatherInstruction(BattleMessage(rawMessage)).invoke(fixture.battle)
        }
        assertTrue(fixture.reveals.isEmpty())
        verify(exactly = 0) { fixture.pokemon.sendUpdate() }
    }
}
