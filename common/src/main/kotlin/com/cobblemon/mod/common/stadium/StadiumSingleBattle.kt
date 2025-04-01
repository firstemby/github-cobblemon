package com.cobblemon.mod.common.stadium

import com.cobblemon.mod.common.CobblemonNetwork.sendPacket
import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.battles.BattleBuilder
import com.cobblemon.mod.common.battles.SuccessfulBattleStart
import com.cobblemon.mod.common.util.getPlayer
import com.cobblemon.mod.common.util.party
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3

class StadiumSingleBattle: StadiumBattleDefenition() {
    @JvmField
    val MAX_PLAYERS = 2

    var player1Position = Vec3(0.0,0.0,0.0)
    var player2Position = Vec3(0.0,0.0,0.0)

    var player1PokemonPosition = Vec3(0.0,0.0,0.0)
    var player2PokemonPosition = Vec3(0.0,0.0,0.0)

    var activeBattle: PokemonBattle? = null

    override fun readyForBattle(): Boolean {
        return this.getPlayers().count() == MAX_PLAYERS
    }

    override fun canAddPlayer(player: ServerPlayer): Boolean {
        val party = player.party()
        return party.occupied() > 0
    }

    override fun addPlayer(player: ServerPlayer) {
        if (canAddPlayer(player)) {
            participants.add(player.uuid)
            player.sendPacket(StadiumJoinPacket(player.uuid))
        }
    }

    override fun getPlayers(): MutableList<ServerPlayer> {
        return participants.mapNotNull { it.getPlayer() }.toMutableList()
    }

    override fun initializeBattle() {
        if (!readyForBattle()) return

        val players = getPlayers()

        val player1 = players[0]
        val player2 = players[1]

        val battleResult = BattleBuilder.pvp1v1(player1, player2)
        var errored = false
        battleResult.ifErrored { errored = true }
        if (errored) return

        val battle = (battleResult as SuccessfulBattleStart).battle
    }

    override fun getCurrentCamera(): StadiumCameraEntry {
        TODO("Not yet implemented")
    }
}