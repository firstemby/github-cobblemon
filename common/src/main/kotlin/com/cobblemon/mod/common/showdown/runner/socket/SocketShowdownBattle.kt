package com.cobblemon.mod.common.showdown.runner.socket

import com.cobblemon.mod.common.showdown.runner.ShowdownBattle

class SocketShowdownBattle(
    override val id: String,
    val server: SocketCobblemonShowdownBattleServer
) : ShowdownBattle() {
    override fun sendMessages(messages: List<String>) {
        messages.forEach {
            server.sendServerMessage("$id~$it")
        }
    }
}