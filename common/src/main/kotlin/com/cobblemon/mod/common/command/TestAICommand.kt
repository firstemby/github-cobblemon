/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.command

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.battles.ai.config.BattleAIConfigs
import com.cobblemon.mod.common.api.moves.Moves
import com.cobblemon.mod.common.api.npc.NPCClasses
import com.cobblemon.mod.common.api.npc.configuration.NPCBattleConfiguration
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.storage.party.NPCPartyStore
import com.cobblemon.mod.common.api.text.aqua
import com.cobblemon.mod.common.api.text.gold
import com.cobblemon.mod.common.api.text.green
import com.cobblemon.mod.common.api.text.red
import com.cobblemon.mod.common.api.text.yellow
import com.cobblemon.mod.common.api.text.text
import com.cobblemon.mod.common.battles.BattleBuilder
import com.cobblemon.mod.common.battles.SuccessfulBattleStart
import com.cobblemon.mod.common.entity.npc.NPCEntity
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.cobblemonResource
import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands.argument
import net.minecraft.commands.Commands.literal
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.AABB

/**
 * Test command for the configurable NPC battle AI system.
 *
 * Usage:
 * - /testai scenario <name> <preset>    — Run a predefined test scenario against an AI preset
 * - /testai scenarios                    — List available test scenarios with descriptions
 * - /testai spawn <preset> [pokemon...]  — Spawn a test NPC with AI preset and optional team
 * - /testai preset <preset>              — Change nearest NPC's AI preset
 * - /testai team <pokemon...>            — Give yourself a test team (replaces current party)
 * - /testai battle <preset> [pokemon...] — Spawn NPC and immediately start battle
 * - /testai list                         — List available AI presets
 */
object TestAICommand {

    // ============================================================================
    // Test Scenario Definitions
    // ============================================================================

    private data class TestScenario(
        val name: String,
        val description: String,
        val playerTeam: List<String>,
        val npcTeam: List<String>,
        val guide: List<Pair<String, String>> // color -> text pairs: "aqua"/"gold"/"yellow"/"green"/"red"/""
    )

    /**
     * Scenario 1: TYPE MATCHUP
     * Gyarados (Water/Flying) vs Luxray (Electric).
     * Luxray has Thunderbolt (4x SE), Volt Switch (4x SE), Crunch (1x), Ice Fang (1x).
     * Clear best move is Thunderbolt. Tests whether AI picks super-effective moves.
     */
    private val SCENARIO_MATCHUP = TestScenario(
        name = "matchup",
        description = "Type Effectiveness - Does the AI pick super-effective moves?",
        playerTeam = listOf(
            "gyarados level=50 moves=waterfall,icefang,dragondance,earthquake",
            "garchomp level=50 moves=earthquake,dragonclaw,stoneedge,swordsdance",
            "gengar level=50 moves=shadowball,sludgebomb,thunderbolt,nastyplot"
        ),
        npcTeam = listOf(
            "luxray level=50 moves=thunderbolt,crunch,icefang,voltswitch",
            "infernape level=50 moves=flamethrower,closecombat,thunderpunch,grassknot",
            "starmie level=50 moves=surf,psychic,thunderbolt,icebeam"
        ),
        guide = listOf(
            "aqua" to "=== TEST: Type Effectiveness ===",
            "" to "Lead: YOUR Gyarados vs NPC Luxray",
            "yellow" to "YOUR ACTION: Use Dragon Dance every turn",
            "" to "",
            "gold" to "NPC Luxray's moves vs Water/Flying:",
            "" to "  Thunderbolt = 4x SE (best)",
            "" to "  Volt Switch  = 4x SE",
            "" to "  Ice Fang     = 1x neutral",
            "" to "  Crunch       = 1x neutral",
            "" to "",
            "green" to "EXPECTED per AI preset:",
            "" to "  random: Any move equally",
            "" to "  easy:   Slight preference for Thunderbolt",
            "" to "  medium: Should pick Thunderbolt or Volt Switch",
            "" to "  expert: Always Thunderbolt (4x SE, highest BP)",
            "red" to "FAIL: Expert uses Crunch or Ice Fang on turn 1"
        )
    )

    /**
     * Scenario 2: SWITCHING
     * Garchomp (Dragon/Ground) vs Pikachu (Electric).
     * Pikachu's Electric moves are IMMUNE vs Ground. Iron Tail is 0.5x NVE.
     * NPC has Togekiss in back: immune to Earthquake AND Dragon Claw, hits back with 4x SE Dazzling Gleam.
     * Tests whether AI switches out of a hopeless matchup.
     */
    private val SCENARIO_SWITCH = TestScenario(
        name = "switch",
        description = "Switching - Does the AI switch out of a hopeless matchup?",
        playerTeam = listOf(
            "garchomp level=50 moves=earthquake,dragonclaw,stoneedge,swordsdance",
            "blissey level=50 moves=softboiled,toxic,thunderwave,seismictoss",
            "scizor level=50 moves=bulletpunch,uturn,swordsdance,knockoff"
        ),
        npcTeam = listOf(
            "pikachu level=50 moves=thunderbolt,voltswitch,irontail,nastyplot",
            "togekiss level=50 moves=airslash,dazzlinggleam,roost,thunderwave",
            "swampert level=50 moves=earthquake,waterfall,icepunch,stealthrock"
        ),
        guide = listOf(
            "aqua" to "=== TEST: Switch Decision ===",
            "" to "Lead: YOUR Garchomp vs NPC Pikachu",
            "yellow" to "YOUR ACTION: Use Earthquake every turn",
            "" to "",
            "gold" to "NPC Pikachu's moves vs Dragon/Ground:",
            "" to "  Thunderbolt = 0x IMMUNE",
            "" to "  Volt Switch  = 0x IMMUNE",
            "" to "  Iron Tail    = 0.5x NVE",
            "" to "  Nasty Plot   = useless (no good moves)",
            "" to "",
            "" to "NPC backup: Togekiss (immune to EQ + Dragon Claw, has 4x SE Dazzling Gleam)",
            "" to "",
            "green" to "EXPECTED per AI preset:",
            "" to "  random/easy: Pikachu stays, uses Thunderbolt (immune = wasted)",
            "" to "  medium:      May switch to Togekiss or Swampert",
            "" to "  expert:      Should switch to Togekiss (double immunity)",
            "red" to "FAIL: Expert keeps Pikachu in using Electric moves"
        )
    )

    /**
     * Scenario 3: SETUP MOVES
     * Magikarp (non-threatening) vs Gyarados (has Dragon Dance).
     * Magikarp only has Splash/Tackle — zero threat.
     * Smart AI should use the free turn to Dragon Dance before attacking.
     */
    private val SCENARIO_SETUP = TestScenario(
        name = "setup",
        description = "Setup Moves - Does the AI set up when facing zero threat?",
        playerTeam = listOf(
            "magikarp level=50 moves=splash,tackle,flail,bounce",
            "magikarp level=50 moves=splash,tackle,flail,bounce",
            "magikarp level=50 moves=splash,tackle,flail,bounce"
        ),
        npcTeam = listOf(
            "gyarados level=50 moves=dragondance,waterfall,icefang,earthquake",
            "scizor level=50 moves=swordsdance,bulletpunch,uturn,knockoff",
            "dragonite level=50 moves=dragondance,outrage,extremespeed,earthquake"
        ),
        guide = listOf(
            "aqua" to "=== TEST: Setup Recognition ===",
            "" to "Lead: YOUR Magikarp vs NPC Gyarados",
            "yellow" to "YOUR ACTION: Use Splash every turn",
            "" to "",
            "gold" to "NPC Gyarados's moves:",
            "" to "  Dragon Dance = +1 Atk, +1 Spe (setup)",
            "" to "  Waterfall    = OHKO on Magikarp",
            "" to "  Ice Fang     = OHKO on Magikarp",
            "" to "  Earthquake   = OHKO on Magikarp",
            "" to "",
            "" to "Magikarp is zero threat. Free turn to boost.",
            "" to "",
            "green" to "EXPECTED per AI preset:",
            "" to "  random: Any move (25% Dragon Dance by luck)",
            "" to "  easy:   Attacks immediately (overkill)",
            "" to "  medium: May Dragon Dance first",
            "" to "  expert: Should Dragon Dance (free setup = sweep later)",
            "red" to "FAIL: Expert never uses Dragon Dance vs Magikarp"
        )
    )

    /**
     * Scenario 4: STATUS MOVES
     * Blissey (Normal, 255 HP / 135 SpDef) vs Gengar (Ghost/Poison).
     * Shadow Ball is IMMUNE (Ghost vs Normal).
     * Sludge Bomb/Thunderbolt do almost nothing to Blissey's massive SpDef.
     * Toxic is the only real way to threaten Blissey.
     */
    private val SCENARIO_STATUS = TestScenario(
        name = "status",
        description = "Status Moves - Does the AI use Toxic when attacks are useless?",
        playerTeam = listOf(
            "blissey level=50 moves=softboiled,toxic,thunderwave,seismictoss",
            "chansey level=50 moves=softboiled,toxic,thunderwave,seismictoss",
            "snorlax level=50 moves=rest,sleeptalk,bodyslam,earthquake"
        ),
        npcTeam = listOf(
            "gengar level=50 moves=shadowball,sludgebomb,toxic,thunderbolt",
            "alakazam level=50 moves=psychic,shadowball,toxic,calmmind",
            "espeon level=50 moves=psychic,shadowball,toxic,calmmind"
        ),
        guide = listOf(
            "aqua" to "=== TEST: Status vs Damage ===",
            "" to "Lead: YOUR Blissey vs NPC Gengar",
            "yellow" to "YOUR ACTION: Use Soft-Boiled every turn (heal to full)",
            "" to "",
            "gold" to "NPC Gengar's moves vs Normal-type Blissey (255 HP, 135 SpDef):",
            "" to "  Shadow Ball  = 0x IMMUNE (Ghost vs Normal)",
            "" to "  Sludge Bomb  = ~10% damage (bounces off SpDef wall)",
            "" to "  Thunderbolt  = ~10% damage (bounces off SpDef wall)",
            "" to "  Toxic        = progressive %HP damage (ignores SpDef)",
            "" to "",
            "" to "Blissey heals 50% each turn with Soft-Boiled. Only Toxic can win.",
            "" to "",
            "green" to "EXPECTED per AI preset:",
            "" to "  random: Any move (25% Shadow Ball = immune!)",
            "" to "  easy:   May use Shadow Ball into Normal immunity",
            "" to "  medium: Avoids Shadow Ball, spams Sludge Bomb/Tbolt",
            "" to "  expert: Should use Toxic (only winning strategy)",
            "red" to "FAIL: Expert uses Shadow Ball or never uses Toxic"
        )
    )

    /**
     * Scenario 5: COVERAGE CALCULATION
     * Skarmory (Steel/Flying) vs Infernape (Fire/Fighting).
     * Flamethrower is 2x SE (Fire vs Steel).
     * Close Combat is 1x (Fighting: 2x vs Steel, 0.5x vs Flying = neutral).
     * Thunder Punch is 1x (Electric: 0.5x vs Steel, 2x vs Flying = neutral).
     * Grass Knot is 0.25x NVE (Grass: 0.5x vs Steel, 0.5x vs Flying).
     * Tests whether AI calculates dual-type effectiveness correctly.
     */
    private val SCENARIO_COVERAGE = TestScenario(
        name = "coverage",
        description = "Coverage - Does the AI calculate dual-type effectiveness correctly?",
        playerTeam = listOf(
            "skarmory level=50 moves=roost,whirlwind,stealthrock,bravebird",
            "ferrothorn level=50 moves=leechseed,protect,gyroball,stealthrock",
            "corviknight level=50 moves=roost,bravebird,uturn,defog"
        ),
        npcTeam = listOf(
            "infernape level=50 moves=flamethrower,closecombat,thunderpunch,grassknot",
            "charizard level=50 moves=flamethrower,airslash,dragonclaw,roost",
            "arcanine level=50 moves=flareblitz,closecombat,wildcharge,extremespeed"
        ),
        guide = listOf(
            "aqua" to "=== TEST: Dual-Type Coverage ===",
            "" to "Lead: YOUR Skarmory (Steel/Flying) vs NPC Infernape",
            "yellow" to "YOUR ACTION: Use Roost every turn",
            "" to "",
            "gold" to "NPC Infernape's moves vs Steel/Flying:",
            "" to "  Flamethrower  = 2x SE   (Fire: 2x vs Steel, 1x vs Flying)",
            "" to "  Close Combat  = 1x neut (Fight: 2x Steel, 0.5x Flying)",
            "" to "  Thunder Punch = 1x neut (Elec: 0.5x Steel, 2x Flying)",
            "" to "  Grass Knot    = 0.25x!! (Grass: 0.5x Steel, 0.5x Flying)",
            "" to "",
            "green" to "EXPECTED per AI preset:",
            "" to "  random: Any move (25% Grass Knot = terrible)",
            "" to "  easy:   Any attacking move",
            "" to "  medium: Should prefer Flamethrower (SE)",
            "" to "  expert: Always Flamethrower (2x SE STAB, clearly best)",
            "red" to "FAIL: Expert uses Grass Knot (0.25x) or doesn't pick Flamethrower"
        )
    )

    private val ALL_SCENARIOS = mapOf(
        "matchup" to SCENARIO_MATCHUP,
        "switch" to SCENARIO_SWITCH,
        "setup" to SCENARIO_SETUP,
        "status" to SCENARIO_STATUS,
        "coverage" to SCENARIO_COVERAGE
    )

    // ============================================================================
    // Command Registration
    // ============================================================================

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            literal("testai")
                .requires { it.hasPermission(2) }
                .then(literal("scenario")
                    .then(argument("name", StringArgumentType.string())
                        .suggests { _, builder -> suggestScenarios(builder) }
                        .then(argument("preset", StringArgumentType.string())
                            .suggests { _, builder -> suggestPresets(builder) }
                            .executes(::runScenario)
                        )
                    )
                )
                .then(literal("scenarios").executes(::listScenarios))
                .then(literal("spawn")
                    .then(argument("preset", StringArgumentType.string())
                        .suggests { _, builder -> suggestPresets(builder) }
                        .then(argument("pokemon", StringArgumentType.greedyString())
                            .executes { spawnNPC(it, true) }
                        )
                        .executes { spawnNPC(it, false) }
                    )
                )
                .then(literal("preset")
                    .then(argument("preset", StringArgumentType.string())
                        .suggests { _, builder -> suggestPresets(builder) }
                        .executes(::changePreset)
                    )
                )
                .then(literal("team")
                    .then(argument("pokemon", StringArgumentType.greedyString())
                        .executes(::setTeam)
                    )
                )
                .then(literal("battle")
                    .then(argument("preset", StringArgumentType.string())
                        .suggests { _, builder -> suggestPresets(builder) }
                        .then(argument("pokemon", StringArgumentType.greedyString())
                            .executes { battle(it, true) }
                        )
                        .executes { battle(it, false) }
                    )
                )
                .then(literal("list").executes(::listPresets))
                .executes(::showHelp)
        )
    }

    // ============================================================================
    // Suggestions
    // ============================================================================

    private fun suggestPresets(builder: SuggestionsBuilder) =
        SharedSuggestionProvider.suggest(BattleAIConfigs.getAllIds().map { it.toString() }, builder)

    private fun suggestScenarios(builder: SuggestionsBuilder) =
        SharedSuggestionProvider.suggest(ALL_SCENARIOS.keys, builder)

    // ============================================================================
    // Scenario Commands
    // ============================================================================

    private fun runScenario(context: CommandContext<CommandSourceStack>): Int {
        val player = context.source.playerOrException
        val scenarioName = StringArgumentType.getString(context, "name")
        val presetStr = StringArgumentType.getString(context, "preset")
        val presetId = resolvePresetId(presetStr)

        val scenario = ALL_SCENARIOS[scenarioName]
        if (scenario == null) {
            context.source.sendFailure("Unknown scenario: $scenarioName. Use /testai scenarios to list.".text().red())
            return 0
        }

        if (!BattleAIConfigs.exists(presetId)) {
            context.source.sendFailure("Unknown AI preset: $presetId. Use /testai list to see presets.".text().red())
            return 0
        }

        // Set player team
        val playerParty = Cobblemon.storage.getParty(player)
        for (i in 0 until 6) {
            playerParty.get(i)?.let { playerParty.remove(it) }
        }
        for (spec in scenario.playerTeam) {
            try {
                val pokemon = createPokemon(spec)
                pokemon.heal()
                playerParty.add(pokemon)
            } catch (e: Exception) {
                context.source.sendFailure("Failed to create player Pokemon: '$spec' - ${e.message}".text().red())
            }
        }

        // Spawn NPC with scenario team
        val npc = createTestNPC(player, presetId, scenario.npcTeam)
        if (npc == null) {
            context.source.sendFailure("Failed to spawn test NPC.".text().red())
            return 0
        }

        // Heal and start battle
        playerParty.forEach { it?.heal() }
        val result = BattleBuilder.pvn(
            player = player,
            npcEntity = npc,
            healFirst = true,
            cloneParties = true
        )

        if (result !is SuccessfulBattleStart) {
            context.source.sendFailure("Failed to start battle. Check logs.".text().red())
            return 0
        }

        // Print the test guide
        context.source.sendSuccess({
            Component.literal("")
                .append("Battle started: ".text().green())
                .append(scenario.name.text().aqua())
                .append(" vs ".text())
                .append(presetId.toString().text().gold())
                .append("\n".text())
        }, false)

        for ((color, line) in scenario.guide) {
            val component = when (color) {
                "aqua" -> line.text().aqua()
                "gold" -> line.text().gold()
                "yellow" -> line.text().yellow()
                "green" -> line.text().green()
                "red" -> line.text().red()
                else -> line.text()
            }
            player.sendSystemMessage(component)
        }

        return Command.SINGLE_SUCCESS
    }

    private fun listScenarios(context: CommandContext<CommandSourceStack>): Int {
        val msg = Component.literal("")
            .append("=== Test Scenarios ===\n".text().aqua())

        for ((name, scenario) in ALL_SCENARIOS) {
            msg.append(name.text().gold())
                .append(" - ${scenario.description}\n".text())
        }

        msg.append("\n".text())
            .append("Usage: ".text().gold())
            .append("/testai scenario <name> <preset>\n".text().yellow())
            .append("Example: ".text().gold())
            .append("/testai scenario matchup expert\n".text().yellow())
            .append("Run the same scenario with different presets to compare AI behavior.".text())

        context.source.sendSuccess({ msg }, false)
        return Command.SINGLE_SUCCESS
    }

    // ============================================================================
    // Original Commands (spawn, preset, team, battle, list)
    // ============================================================================

    private fun spawnNPC(context: CommandContext<CommandSourceStack>, hasTeam: Boolean): Int {
        val player = context.source.playerOrException
        val presetStr = StringArgumentType.getString(context, "preset")
        val presetId = resolvePresetId(presetStr)

        if (!BattleAIConfigs.exists(presetId)) {
            context.source.sendFailure("Unknown AI preset: $presetId".text().red())
            return 0
        }

        val pokemonSpecs = if (hasTeam) {
            parsePokemonList(StringArgumentType.getString(context, "pokemon"))
        } else {
            DEFAULT_NPC_TEAM
        }

        val npc = createTestNPC(player, presetId, pokemonSpecs)
        if (npc == null) {
            context.source.sendFailure("Failed to spawn test NPC.".text().red())
            return 0
        }

        context.source.sendSuccess({
            Component.literal("")
                .append("Spawned test NPC with AI: ".text().green())
                .append(presetId.toString().text().aqua())
                .append(" (${pokemonSpecs.size} Pokemon)".text())
        }, false)

        return Command.SINGLE_SUCCESS
    }

    private fun changePreset(context: CommandContext<CommandSourceStack>): Int {
        val player = context.source.playerOrException
        val presetStr = StringArgumentType.getString(context, "preset")
        val presetId = resolvePresetId(presetStr)

        if (!BattleAIConfigs.exists(presetId)) {
            context.source.sendFailure("Unknown AI preset: $presetId".text().red())
            return 0
        }

        val npc = findNearestNPC(player)
        if (npc == null) {
            context.source.sendFailure("No NPC found within 10 blocks.".text().red())
            return 0
        }

        npc.battleAIConfigId = presetId

        context.source.sendSuccess({
            Component.literal("")
                .append("Set NPC AI to: ".text().green())
                .append(presetId.toString().text().aqua())
        }, false)

        return Command.SINGLE_SUCCESS
    }

    private fun setTeam(context: CommandContext<CommandSourceStack>): Int {
        val player = context.source.playerOrException
        val input = StringArgumentType.getString(context, "pokemon")
        val pokemonSpecs = parsePokemonList(input)

        if (pokemonSpecs.isEmpty()) {
            context.source.sendFailure("No Pokemon specified. Separate multiple with |".text().red())
            return 0
        }

        val party = Cobblemon.storage.getParty(player)
        for (i in 0 until 6) {
            party.get(i)?.let { party.remove(it) }
        }

        var count = 0
        for (spec in pokemonSpecs.take(6)) {
            try {
                val pokemon = createPokemon(spec)
                pokemon.heal()
                party.add(pokemon)
                count++
            } catch (e: Exception) {
                context.source.sendFailure("Failed to create Pokemon from: '$spec' - ${e.message}".text().red())
            }
        }

        context.source.sendSuccess({
            "Set your team to $count Pokemon.".text().green()
        }, false)

        return Command.SINGLE_SUCCESS
    }

    private fun battle(context: CommandContext<CommandSourceStack>, hasTeam: Boolean): Int {
        val player = context.source.playerOrException
        val presetStr = StringArgumentType.getString(context, "preset")
        val presetId = resolvePresetId(presetStr)

        if (!BattleAIConfigs.exists(presetId)) {
            context.source.sendFailure("Unknown AI preset: $presetId".text().red())
            return 0
        }

        val playerParty = Cobblemon.storage.getParty(player)
        if (playerParty.none { it != null }) {
            for (spec in DEFAULT_PLAYER_TEAM) {
                try {
                    val pokemon = createPokemon(spec)
                    pokemon.heal()
                    playerParty.add(pokemon)
                } catch (_: Exception) {}
            }
            context.source.sendSuccess({
                "Gave you a default test team.".text().yellow()
            }, false)
        }

        val npcSpecs = if (hasTeam) {
            parsePokemonList(StringArgumentType.getString(context, "pokemon"))
        } else {
            DEFAULT_NPC_TEAM
        }

        val npc = createTestNPC(player, presetId, npcSpecs)
        if (npc == null) {
            context.source.sendFailure("Failed to spawn test NPC.".text().red())
            return 0
        }

        playerParty.forEach { it?.heal() }

        val result = BattleBuilder.pvn(
            player = player,
            npcEntity = npc,
            healFirst = true,
            cloneParties = true
        )

        if (result is SuccessfulBattleStart) {
            context.source.sendSuccess({
                Component.literal("")
                    .append("Battle started vs ".text().green())
                    .append(presetId.toString().text().aqua())
                    .append(" AI!".text().green())
            }, false)
        } else {
            context.source.sendFailure("Failed to start battle. Check logs.".text().red())
        }

        return Command.SINGLE_SUCCESS
    }

    private fun listPresets(context: CommandContext<CommandSourceStack>): Int {
        val ids = BattleAIConfigs.getAllIds().sorted()

        if (ids.isEmpty()) {
            context.source.sendFailure("No AI presets loaded.".text().red())
            return 0
        }

        val msg = Component.literal("")
            .append("=== Available AI Presets ===\n".text().aqua())

        for (id in ids) {
            val config = BattleAIConfigs.getConfig(id)
            val parent = config?.parent?.toString() ?: "none"
            val skill = config?.getEffectiveSkillLevel() ?: 0
            msg.append(id.toString().text().gold())
                .append(" (skill=$skill, parent=$parent)\n".text())
        }

        msg.append("===========================".text().aqua())
        context.source.sendSuccess({ msg }, false)
        return Command.SINGLE_SUCCESS
    }

    private fun showHelp(context: CommandContext<CommandSourceStack>): Int {
        val help = Component.literal("")
            .append("=== Test AI Commands ===\n".text().aqua())
            .append("/testai scenario <name> <preset>".text().yellow())
            .append(" - Run test scenario\n".text())
            .append("/testai scenarios".text().yellow())
            .append(" - List test scenarios\n".text())
            .append("/testai battle <preset> [pokemon]".text().yellow())
            .append(" - Custom battle\n".text())
            .append("/testai spawn <preset> [pokemon]".text().yellow())
            .append(" - Spawn NPC\n".text())
            .append("/testai preset <preset>".text().yellow())
            .append(" - Change nearest NPC's AI\n".text())
            .append("/testai team <pokemon>".text().yellow())
            .append(" - Set your party\n".text())
            .append("/testai list".text().yellow())
            .append(" - List AI presets\n".text())
            .append("\n".text())
            .append("Pokemon separated by | in custom commands\n".text().gold())
            .append("Example: ".text())
            .append("/testai scenario matchup expert".text().yellow())

        context.source.sendSuccess({ help }, false)
        return Command.SINGLE_SUCCESS
    }

    // ============================================================================
    // Helpers
    // ============================================================================

    private val DEFAULT_NPC_TEAM = listOf(
        "garchomp level=50 moves=earthquake,dragonclaw,stoneedge,swordsdance",
        "tyranitar level=50 moves=stoneedge,crunch,earthquake,dragondance",
        "gengar level=50 moves=shadowball,sludgebomb,thunderbolt,nastyplot",
        "scizor level=50 moves=bulletpunch,uturn,swordsdance,knockoff",
        "starmie level=50 moves=surf,psychic,thunderbolt,icebeam",
        "togekiss level=50 moves=airslash,dazzlinggleam,roost,thunderwave"
    )

    private val DEFAULT_PLAYER_TEAM = listOf(
        "charizard level=50 moves=flamethrower,airslash,dragonclaw,roost",
        "lucario level=50 moves=closecombat,meteormash,swordsdance,extremespeed",
        "gardevoir level=50 moves=psychic,moonblast,calmmind,thunderbolt",
        "gyarados level=50 moves=waterfall,icefang,dragondance,earthquake",
        "excadrill level=50 moves=earthquake,ironhead,swordsdance,rockslide",
        "dragapult level=50 moves=shadowball,dracometeor,flamethrower,thunderbolt"
    )

    private fun resolvePresetId(preset: String): ResourceLocation {
        return if (preset.contains(":")) {
            ResourceLocation.parse(preset)
        } else {
            cobblemonResource(preset)
        }
    }

    private fun parsePokemonList(input: String): List<String> {
        return input.split("|").map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun createPokemon(spec: String): Pokemon {
        val properties = PokemonProperties.parse(spec)
        val pokemon = properties.create()
        // Force-set exact moveset, bypassing PokemonProperties' shuffle-insert logic
        properties.moves?.let { moveNames ->
            val templates = moveNames.mapNotNull { Moves.getByName(it) }
            if (templates.isNotEmpty()) {
                pokemon.moveSet.doWithoutEmitting {
                    for (i in 0..3) pokemon.moveSet.setMove(i, null)
                    templates.take(4).forEachIndexed { i, t ->
                        pokemon.moveSet.setMove(i, t.create())
                    }
                }
                pokemon.moveSet.update()
            }
        }
        return pokemon
    }

    private fun createTestNPC(player: ServerPlayer, presetId: ResourceLocation, pokemonSpecs: List<String>): NPCEntity? {
        val world = player.serverLevel()

        val npcClass = NPCClasses.getByIdentifier(cobblemonResource("standard"))
            ?: NPCClasses.classes.firstOrNull()
            ?: return null

        val npc = NPCEntity(world)
        val pos = player.position().add(player.lookAngle.normalize().scale(3.0))
        npc.moveTo(pos.x, pos.y, pos.z, player.yRot + 180f, 0f)
        npc.npc = npcClass
        npc.battleAIConfigId = presetId

        val party = NPCPartyStore(npc)
        for (spec in pokemonSpecs.take(6)) {
            try {
                val pokemon = createPokemon(spec)
                pokemon.heal()
                party.add(pokemon)
            } catch (e: Exception) {
                Cobblemon.LOGGER.warn("Failed to create test Pokemon from '$spec': ${e.message}")
            }
        }

        if (party.none { it != null }) return null

        npc.party = party
        npc.initialize(50)
        npc.party = party

        val battleConfig = npc.battle ?: NPCBattleConfiguration().also { npc.battle = it }
        battleConfig.canChallenge = true
        battleConfig.battleAIConfigId = presetId

        if (!world.addFreshEntity(npc)) return null
        return npc
    }

    private fun findNearestNPC(player: ServerPlayer): NPCEntity? {
        val box = AABB.ofSize(player.position(), 20.0, 10.0, 20.0)
        return player.serverLevel().getEntitiesOfClass(NPCEntity::class.java, box)
            .minByOrNull { it.distanceTo(player) }
    }
}
