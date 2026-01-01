package com.cobblemon.mod.common.battles.ai.competitive

import com.cobblemon.mod.common.api.moves.MoveTemplate
import com.cobblemon.mod.common.api.moves.Moves
import com.cobblemon.mod.common.api.pokemon.stats.Stat
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.api.pokemon.status.Statuses

 
object MoveData {
    val multiHitMoves: Map<String, Map<Int, Int>> = mapOf(
        "armthrust" to mapOf(2 to 5),
        "barrage" to mapOf(2 to 5),
        "bonerush" to mapOf(2 to 5),
        "bulletseed" to mapOf(2 to 5),
        "cometpunch" to mapOf(2 to 5),
        "doubleslap" to mapOf(2 to 5),
        "furyattack" to mapOf(2 to 5),
        "furyswipes" to mapOf(2 to 5),
        "iciclespear" to mapOf(2 to 5),
        "pinmissile" to mapOf(2 to 5),
        "rockblast" to mapOf(2 to 5),
        "scaleshot" to mapOf(2 to 5),
        "spikecannon" to mapOf(2 to 5),
        "tailslap" to mapOf(2 to 5),
        "watershuriken" to mapOf(2 to 5),
        "bonemerang" to mapOf(2 to 2),
        "doublehit" to mapOf(2 to 2),
        "doubleironbash" to mapOf(2 to 2),
        "doublekick" to mapOf(2 to 2),
        "dragondarts" to mapOf(2 to 2),
        "dualchop" to mapOf(2 to 2),
        "dualwingbeat" to mapOf(2 to 2),
        "geargrind" to mapOf(2 to 2),
        "twinbeam" to mapOf(2 to 2),
        "twineedle" to mapOf(2 to 2),
        "surgingstrikes" to mapOf(3 to 3),
        "tripledive" to mapOf(3 to 3),
        "tripleaxel" to mapOf(1 to 3), 
        "triplekick" to mapOf(1 to 3),
        "populationbomb" to mapOf(1 to 10)
    )
     
    val statusMoves: Map<MoveTemplate?, String> = mapOf(
        Moves.getByName("willowisp") to Statuses.BURN.showdownName,
        Moves.getByName("scald") to Statuses.BURN.showdownName,
        Moves.getByName("scorchingsands") to Statuses.BURN.showdownName,
        Moves.getByName("flamethrower") to Statuses.BURN.showdownName,
        Moves.getByName("sacredfire") to Statuses.BURN.showdownName,
        Moves.getByName("glare") to Statuses.PARALYSIS.showdownName,
        Moves.getByName("nuzzle") to Statuses.PARALYSIS.showdownName,
        Moves.getByName("stunspore") to Statuses.PARALYSIS.showdownName,
        Moves.getByName("thunderwave") to Statuses.PARALYSIS.showdownName,
        Moves.getByName("bodyslam") to Statuses.PARALYSIS.showdownName,
        Moves.getByName("discharge") to Statuses.PARALYSIS.showdownName,
        Moves.getByName("spark") to Statuses.PARALYSIS.showdownName,
        Moves.getByName("darkvoid") to Statuses.SLEEP.showdownName,
        Moves.getByName("hypnosis") to Statuses.SLEEP.showdownName,
        Moves.getByName("lovelykiss") to Statuses.SLEEP.showdownName,
        Moves.getByName("relicsong") to Statuses.SLEEP.showdownName,
        Moves.getByName("sing") to Statuses.SLEEP.showdownName,
        Moves.getByName("sleeppowder") to Statuses.SLEEP.showdownName,
        Moves.getByName("spore") to Statuses.SLEEP.showdownName,
        Moves.getByName("yawn") to Statuses.SLEEP.showdownName,
        Moves.getByName("chatter") to "confusion",
        Moves.getByName("confuseray") to "confusion",
        Moves.getByName("dynamicpunch") to "confusion",
        Moves.getByName("flatter") to "confusion",
        Moves.getByName("supersonic") to "confusion",
        Moves.getByName("swagger") to "confusion",
        Moves.getByName("sweetkiss") to "confusion",
        Moves.getByName("teeterdance") to "confusion",
        Moves.getByName("poisongas") to Statuses.POISON.showdownName,
        Moves.getByName("poisonpowder") to Statuses.POISON.showdownName,
        Moves.getByName("poisonjab") to Statuses.POISON.showdownName,
        Moves.getByName("sludgebomb") to Statuses.POISON.showdownName,
        Moves.getByName("crosspoison") to Statuses.POISON.showdownName,
        Moves.getByName("toxic") to Statuses.POISON_BADLY.showdownName,
        Moves.getByName("toxicthread") to Statuses.POISON.showdownName,
        Moves.getByName("curse") to "cursed",
        Moves.getByName("leechseed") to "leech"
    )

    val boostFromMoves: Map<String, Map<Stat, Int>> = mapOf(
        "bellydrum" to mapOf(Stats.ATTACK to 6), 
        "swordsdance" to mapOf(Stats.ATTACK to 2),
        "honeclaws" to mapOf(Stats.ATTACK to 1, Stats.ACCURACY to 1),
        "bulkup" to mapOf(Stats.ATTACK to 1, Stats.DEFENCE to 1),
        "curse" to mapOf(Stats.ATTACK to 1, Stats.DEFENCE to 1, Stats.SPEED to -1), 
        "howl" to mapOf(Stats.ATTACK to 1),
        "meditate" to mapOf(Stats.ATTACK to 1),
        "sharpen" to mapOf(Stats.ATTACK to 1),
        "workup" to mapOf(Stats.ATTACK to 1, Stats.SPECIAL_ATTACK to 1),
        "acidarmor" to mapOf(Stats.DEFENCE to 2),
        "barrier" to mapOf(Stats.DEFENCE to 2),
        "cottonguard" to mapOf(Stats.DEFENCE to 3),
        "defensecurl" to mapOf(Stats.DEFENCE to 1),
        "irondefense" to mapOf(Stats.DEFENCE to 2),
        "stockpile" to mapOf(Stats.DEFENCE to 1, Stats.SPECIAL_DEFENCE to 1),
        "stuffcheeks" to mapOf(Stats.DEFENCE to 2),
        "shelter" to mapOf(Stats.DEFENCE to 2, Stats.EVASION to 1),
        "withdraw" to mapOf(Stats.DEFENCE to 1),
        "harden" to mapOf(Stats.DEFENCE to 1),
        "nastyplot" to mapOf(Stats.SPECIAL_ATTACK to 2),
        "calmmind" to mapOf(Stats.SPECIAL_ATTACK to 1, Stats.SPECIAL_DEFENCE to 1),
        "quiverdance" to mapOf(Stats.SPECIAL_ATTACK to 1, Stats.SPECIAL_DEFENCE to 1, Stats.SPEED to 1),
        "tailglow" to mapOf(Stats.SPECIAL_ATTACK to 3),
        "takeheart" to mapOf(Stats.SPECIAL_ATTACK to 1, Stats.SPECIAL_DEFENCE to 1),
        "geomancy" to mapOf(Stats.SPECIAL_ATTACK to 2, Stats.SPECIAL_DEFENCE to 2, Stats.SPEED to 2), 
        "growth" to mapOf(Stats.SPECIAL_ATTACK to 1),
        "amnesia" to mapOf(Stats.SPECIAL_DEFENCE to 2),
        "agility" to mapOf(Stats.SPEED to 2),
        "autotomize" to mapOf(Stats.SPEED to 2),
        "rockpolish" to mapOf(Stats.SPEED to 2),
        "dragondance" to mapOf(Stats.ATTACK to 1, Stats.SPEED to 1),
        "shiftgear" to mapOf(Stats.ATTACK to 1, Stats.SPEED to 2),
        "clangoroussoul" to mapOf(Stats.ATTACK to 1, Stats.DEFENCE to 1, Stats.SPECIAL_ATTACK to 1, Stats.SPECIAL_DEFENCE to 1, Stats.SPEED to 1),
        "extremeevoboost" to mapOf(Stats.ATTACK to 2, Stats.DEFENCE to 2, Stats.SPECIAL_ATTACK to 2, Stats.SPECIAL_DEFENCE to 2, Stats.SPEED to 2),
        "clangoroussoulblaze" to mapOf(Stats.ATTACK to 1, Stats.DEFENCE to 1, Stats.SPECIAL_ATTACK to 1, Stats.SPECIAL_DEFENCE to 1, Stats.SPEED to 1),
        "filletaway" to mapOf(Stats.ATTACK to 2, Stats.SPECIAL_ATTACK to 2, Stats.SPEED to 2), 
        "noretreat" to mapOf(Stats.ATTACK to 1, Stats.DEFENCE to 1, Stats.SPECIAL_ATTACK to 1, Stats.SPECIAL_DEFENCE to 1, Stats.SPEED to 1),
        "shellsmash" to mapOf(Stats.ATTACK to 2, Stats.DEFENCE to -1, Stats.SPECIAL_ATTACK to 2, Stats.SPECIAL_DEFENCE to -1, Stats.SPEED to 2),
        "tidyup" to mapOf(Stats.ATTACK to 1, Stats.SPEED to 1),
        "victorydance" to mapOf(Stats.ATTACK to 1, Stats.DEFENCE to 1, Stats.SPEED to 1),
        "coil" to mapOf(Stats.ATTACK to 1, Stats.DEFENCE to 1, Stats.ACCURACY to 1),
        "minimize" to mapOf(Stats.EVASION to 2),
        "doubleteam" to mapOf(Stats.EVASION to 1)
    )

    val weatherMoves: Map<String, String> = mapOf(
        "raindance" to "rain",
        "sunnyday" to "sun",
        "sandstorm" to "sand",
        "hail" to "hail",
        "snowscape" to "snow"
    )

    val weatherDependentMoves: Map<String, String> = mapOf(
        "thunder" to "rain", 
        "hurricane" to "rain", 
        "blizzard" to "hail", 
        "solarbeam" to "sun", 
        "hydrosteam" to "sun", 
        "weatherball" to "any" 
    )

    val weatherAbilities: Map<String, String> = mapOf(
        "swiftswim" to "rain", 
        "raindish" to "rain", 
        "dryskin" to "rain",
        "chlorophyll" to "sun", 
        "leafguard" to "sun", 
        "flowergift" to "sun", 
        "solarpower" to "sun",
        "sandrush" to "sand", 
        "sandveil" to "sand", 
        "sandforce" to "sand",
        "icebody" to "hail", 
        "snowcloak" to "hail" 
    )

    val entryHazards = listOf("spikes", "stealthrock", "stickyweb", "toxicspikes")
    val antiHazardsMoves = listOf("rapidspin", "defog", "tidyup")
    val antiBoostMoves = listOf("clearsmog", "haze", "roar", "whirlwind")
    val pivotMoves = listOf("uturn", "flipturn", "partingshot", "batonpass", "voltswitch", "teleport")
    val setupMoves = setOf("tailwind", "trickroom", "auroraveil", "lightscreen", "reflect")
    val selfRecoveryMoves = listOf("healorder", "milkdrink", "recover", "rest", "roost", "slackoff", "softboiled")
    val priorityMoves = listOf(
        "quickattack", "extremespeed", "bulletpunch", "machpunch",
        "aquajet", "shadowsneak", "accelerock", "iceshard",
        "suckerpunch", "firstimpression", "fakeout"
    )

    val recoilMoves = listOf(
        "doubleedge", "bravebird", "closecombat", "superpower",
        "overheat", "dracometeor", "leafstorm", "flareblitz"
    )
}
