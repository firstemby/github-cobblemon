/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.permission

import net.minecraft.server.permissions.PermissionLevel

object CobblemonPermissions {

    private const val COMMAND_PREFIX = "command."
    private val permissions = arrayListOf<Permission>()

    @JvmStatic
    val CHANGE_EYE_HEIGHT = this.create("${COMMAND_PREFIX}changeeyeheight", PermissionLevel.OWNERS)
    @JvmStatic
    val CHANGE_SCALE_AND_SIZE = this.create("${COMMAND_PREFIX}changescaleandsize", PermissionLevel.OWNERS)
    @JvmStatic
    val CHANGE_WALK_SPEED = this.create("${COMMAND_PREFIX}changewalkspeed", PermissionLevel.OWNERS)
    @JvmStatic
    val TRANSFORM_MODEL_PART = this.create("${COMMAND_PREFIX}transformmodelpart", PermissionLevel.OWNERS)
    @JvmStatic
    val CHECKSPAWNS = this.create("${COMMAND_PREFIX}checkspawns", PermissionLevel.GAMEMASTERS)
    @JvmStatic
    val GET_NBT = this.create("${COMMAND_PREFIX}getnbt", PermissionLevel.OWNERS)

    private const val GIVE_POKEMON_BASE = "${COMMAND_PREFIX}givepokemon"
    @JvmStatic
    val GIVE_POKEMON_SELF = this.create("${GIVE_POKEMON_BASE}.self", PermissionLevel.GAMEMASTERS)
    @JvmStatic
    val GIVE_POKEMON_OTHER = this.create("${GIVE_POKEMON_BASE}.other", PermissionLevel.GAMEMASTERS)
    private const val HEAL_POKEMON_BASE = "${COMMAND_PREFIX}healpokemon"
    @JvmStatic
    val HEAL_POKEMON_SELF = this.create("$HEAL_POKEMON_BASE.self", PermissionLevel.GAMEMASTERS)
    @JvmStatic
    val HEAL_POKEMON_OTHER = this.create("$HEAL_POKEMON_BASE.other", PermissionLevel.GAMEMASTERS)


    private const val LEVEL_UP_BASE = "${COMMAND_PREFIX}levelup"
    @JvmStatic
    val LEVEL_UP_SELF = this.create("$LEVEL_UP_BASE.self", PermissionLevel.GAMEMASTERS)
    @JvmStatic
    val LEVEL_UP_OTHER = this.create("$LEVEL_UP_BASE.other", PermissionLevel.GAMEMASTERS)

    @JvmStatic
    val OPEN_STARTER_SCREEN = this.create("${COMMAND_PREFIX}openstarterscreen", PermissionLevel.GAMEMASTERS)
    @JvmStatic
    val BEDROCK_PARTICLE = this.create("${COMMAND_PREFIX}bedrockparticle", PermissionLevel.GAMEMASTERS)
    @JvmStatic
    val OPEN_DIALOGUE = this.create("${COMMAND_PREFIX}opendialogue", PermissionLevel.GAMEMASTERS)
    @JvmStatic
    val UNLOCK_PC_BOX_WALLPAPER = this.create("${COMMAND_PREFIX}unlockpcboxwallpaper", PermissionLevel.GAMEMASTERS)

    private const val POKEMON_EDIT_BASE = "${COMMAND_PREFIX}pokemonedit"
    @JvmStatic
    val POKEMON_EDIT_SELF = this.create("$POKEMON_EDIT_BASE.self", PermissionLevel.GAMEMASTERS)
    @JvmStatic
    val POKEMON_EDIT_OTHER = this.create("$POKEMON_EDIT_BASE.other", PermissionLevel.GAMEMASTERS)
    @JvmStatic
    val SPAWN_ALL_POKEMON = this.create("${COMMAND_PREFIX}spawnallpokemon", PermissionLevel.OWNERS)
    @JvmStatic
    val GIVE_ALL_POKEMON = this.create("${COMMAND_PREFIX}giveallpokemon", PermissionLevel.OWNERS)

    @JvmStatic
    val SPAWN_POKEMON = this.create("${COMMAND_PREFIX}spawnpokemon", PermissionLevel.GAMEMASTERS)
    @JvmStatic
    val SPAWN_NPC = this.create("${COMMAND_PREFIX}spawnnpc", PermissionLevel.GAMEMASTERS)


    @JvmStatic
    val STOP_BATTLE = this.create("${COMMAND_PREFIX}stopbattle", PermissionLevel.GAMEMASTERS)


    @JvmStatic
    val TAKE_POKEMON = this.create("${COMMAND_PREFIX}takepokemon", PermissionLevel.GAMEMASTERS)


    @JvmStatic
    val TEACH = this.create("${COMMAND_PREFIX}teach.base", PermissionLevel.GAMEMASTERS)
    @JvmStatic
    val TEACH_BYPASS_LEARNSET = this.create("${COMMAND_PREFIX}teach.bypass", PermissionLevel.GAMEMASTERS)


    @JvmStatic
    val FRIENDSHIP = this.create("${COMMAND_PREFIX}friendship", PermissionLevel.GAMEMASTERS)


    @JvmStatic
    val HELD_ITEM = this.create("${COMMAND_PREFIX}helditem", PermissionLevel.GAMEMASTERS)

    @JvmStatic
    val CHANGE_MARK = this.create("${COMMAND_PREFIX}changemark", PermissionLevel.GAMEMASTERS)

    @JvmStatic
    val PC = this.create("${COMMAND_PREFIX}pc", PermissionLevel.GAMEMASTERS)
    @JvmStatic
    val PC_SEARCH = this.create("${COMMAND_PREFIX}pcsearch", PermissionLevel.GAMEMASTERS)
    @JvmStatic
    val POKEBOX = this.create("${COMMAND_PREFIX}pokebox", PermissionLevel.GAMEMASTERS)

    @JvmStatic
    val RENAMEBOX = this.create("${COMMAND_PREFIX}renamebox", PermissionLevel.GAMEMASTERS)
    @JvmStatic
    val CHANGE_WALLPAPER = this.create("${COMMAND_PREFIX}changewallpaper", PermissionLevel.GAMEMASTERS)
    @JvmStatic
    val TEST_STORE = this.create("${COMMAND_PREFIX}teststore", PermissionLevel.GAMEMASTERS)
    @JvmStatic
    val QUERY_LEARNSET = this.create("${COMMAND_PREFIX}querylearnset", PermissionLevel.GAMEMASTERS)
    @JvmStatic
    val TEST_PC_SLOT = this.create("${COMMAND_PREFIX}testpcslot", PermissionLevel.GAMEMASTERS)
    @JvmStatic
    val TEST_PARTY_SLOT = this.create("${COMMAND_PREFIX}testpartyslot", PermissionLevel.GAMEMASTERS)
    @JvmStatic
    val CLEAR_PARTY = this.create("${COMMAND_PREFIX}clearparty", PermissionLevel.GAMEMASTERS)
    @JvmStatic
    val CLEAR_PC = this.create("${COMMAND_PREFIX}clearpc", PermissionLevel.GAMEMASTERS)
    @JvmStatic
    val POKEDEX = this.create("${COMMAND_PREFIX}pokedex", PermissionLevel.GAMEMASTERS)

    @JvmStatic
    val NPC_EDIT = this.create("${COMMAND_PREFIX}npcedit", PermissionLevel.GAMEMASTERS)
    @JvmStatic
    val NPC_DELETE = this.create("${COMMAND_PREFIX}npcdelete", PermissionLevel.GAMEMASTERS)
    @JvmStatic
    val FREEZE_POKEMON = this.create("${COMMAND_PREFIX}freezepokemon", PermissionLevel.GAMEMASTERS)
    @JvmStatic
    val APPLY_PLAYER_TEXTURE = this.create("${COMMAND_PREFIX}applyplayertexture", PermissionLevel.GAMEMASTERS)
    @JvmStatic
    val BEHAVIOUR_EDIT = this.create("${COMMAND_PREFIX}behaviouredit", PermissionLevel.GAMEMASTERS)
    @JvmStatic
    val ABANDON_MULTITEAM = this.create("${COMMAND_PREFIX}abandonmultiteam", PermissionLevel.ALL)
    @JvmStatic
    val RUN_MOLANG = this.create("${COMMAND_PREFIX}runmolang", PermissionLevel.GAMEMASTERS)
    @JvmStatic
    val RUN_MOLANG_SCRIPT = this.create("${COMMAND_PREFIX}runmolangscript", PermissionLevel.GAMEMASTERS)
    @JvmStatic
    val COBBLEMON_CONFIG_RELOAD = this.create("${COMMAND_PREFIX}cobblemonconfig.reload", PermissionLevel.GAMEMASTERS)
    @JvmStatic
    val CALCULATE_SEAT_POSITIONS = this.create("${COMMAND_PREFIX}calculateseatpositions", PermissionLevel.GAMEMASTERS)
    @JvmStatic
    val CHANGE_BOX_COUNT = this.create("${COMMAND_PREFIX}boxcount", PermissionLevel.GAMEMASTERS)
    @JvmStatic
    val SPECTATE_BATTLE = this.create("${COMMAND_PREFIX}spectatebattle", PermissionLevel.OWNERS)

    @JvmStatic
    val SEE_HIDDEN_NPCS = this.create("seehiddennpcs", PermissionLevel.GAMEMASTERS)

    @JvmStatic
    val USE_RIDING_STATS_DEBUG = this.create("useridingstatsdebug", PermissionLevel.GAMEMASTERS)

    @JvmStatic
    fun all(): Iterable<Permission> = this.permissions

    private fun create(node: String, level: PermissionLevel): Permission {
        val permission = CobblemonPermission(node, level)
        this.permissions += permission
        return permission
    }

}
