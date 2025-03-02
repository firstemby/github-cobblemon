package com.cobblemon.mod.common.showdown.runner.remote

import com.cobblemon.mod.common.api.moves.MoveTemplate
import com.cobblemon.mod.common.api.scheduling.ScheduledTask
import com.cobblemon.mod.common.api.scheduling.ServerTaskTracker
import com.cobblemon.mod.common.showdown.runner.ShowdownBattle
import com.cobblemon.mod.common.showdown.runner.ShowdownBattleServer
import javax.naming.OperationNotSupportedException

class RemoteShowdownBattleServer(
    val socketUrl: String,
    val websiteUrl: String,
) : ShowdownBattleServer {
    private val client = ShowdownGameClient(socketUrl)
    private val scraper = ShowdownClientScraper(websiteUrl)
    private val state = ShowdownGameServerConnectionState()
    private val interpreter = ShowdownGameServerInterpreter(state)

    override fun connect() {
        client.connect()
    }

    override fun disconnect() {
        client.close()
    }

    override fun initializeNewBattle(battleId: String, messages: Array<String>): ShowdownBattle {
        throw OperationNotSupportedException()
    }

    override fun sendServerMessage(msg: String) {
        client.sendMessage(msg)
    }

    override fun getAbilityIds(): Collection<String> {
        return scraper.getAbilityIds().map { it.asString }.toSet()
    }

    override fun getMoves(): Collection<MoveTemplate> {
        val json = scraper.getMoves()
        return json.keySet().map {
            MoveTemplate.decodeJsonMove(json.get(it).asJsonObject, it)
        }
    }

    override fun getItemIds(): Collection<String> {
        return scraper.getItemIds().map { it.toString() }.toSet()
    }

    override fun onServerStarted() {
        val pollSocketTask = ScheduledTask.Builder().interval(1/4F)
            .infiniteIterations()
            .execute { client.pollMessage() }
            .build()
        ServerTaskTracker.addTask(pollSocketTask)
        val interpretMessageTask = ScheduledTask.Builder().interval(1/4F)
            .infiniteIterations()
            .execute {
                var message: String? = null
                do {
                    message = client.messageQueue.poll()
                    if (message != null)   interpreter.interpret(message)
                } while (message != null)
            }
            .build()
        ServerTaskTracker.addTask(interpretMessageTask)
    }

    fun getLoginState(): ShowdownGameServerLoginState {
        return state.loginState
    }
    fun setLoginState(newState: ShowdownGameServerLoginState) {
        state.loginState = newState
    }
}