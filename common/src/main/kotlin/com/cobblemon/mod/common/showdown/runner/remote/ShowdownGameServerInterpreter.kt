package com.cobblemon.mod.common.showdown.runner.remote

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.battles.ShowdownInterpreter
import com.cobblemon.mod.common.showdown.ShowdownSearch
import com.google.gson.GsonBuilder

/**
 * A variation of the [ShowdownInterpreter] that interprets messages from the showdown game server
 */
class ShowdownGameServerInterpreter(
    val serverConnectionState: ShowdownGameServerConnectionState
) {
    val messageDispatchMap = mutableMapOf<String, (String) -> Unit>()
    //Maps a command name to a function that takes a room name and command
    val scopedMessageDispatchMap = mutableMapOf<String, (String, String) -> Unit>()
    //For slash commands sent in PMs
    val commandDispatchMap = mutableMapOf<String, (String, String) -> Unit>()

    private val gson = GsonBuilder().create()

    init {
        messageDispatchMap.put("formats", {})
        messageDispatchMap.put("challstr", ::handleChallStr)
        messageDispatchMap.put("pm", ::handlePrivateMessage)
        messageDispatchMap.put("updatesearch", ::handleUpdateSearch)

        scopedMessageDispatchMap.put("request", ::handleRequest)
        scopedMessageDispatchMap.put("player", ::handlePlayer)
        scopedMessageDispatchMap.put("start", ::handleStart)
        scopedMessageDispatchMap.put("gametype", ::handleGameType)

        commandDispatchMap.put("challenge", ::handleChallengeCommand)
    }

    fun interpret(message: String) {
        Cobblemon.LOGGER.info(message)
        val lines = message.split('\n')
        val isLocal = lines[0].startsWith(">")
        val roomId = if(isLocal) lines[0].substring(1) else null
        val startIndex = if(isLocal) 1 else 0
        for (line in lines.slice(startIndex until lines.size)) {
            val args = line.split("|")
            if (args.size == 1) {
                return
            }
            val messageType = args[1]
            if (isLocal) {
                val handler = scopedMessageDispatchMap.get(messageType)
                if (handler == null) {
                    Cobblemon.LOGGER.warn("Showdown server interpreter received an unknown message type for room: $messageType")
                }
                handler?.invoke(line, roomId!!)
            }
            else {
                val messageHandler = messageDispatchMap[messageType]
                if (messageHandler == null) {
                    Cobblemon.LOGGER.warn("Showdown server interpreter received an unknown message type: $messageType")
                }
                messageHandler?.invoke(message)
            }

        }
    }

    fun handleChallStr(message: String) {
        val args = message.split("|")
        val key = args[2]
        val str = args[3]
        val newLoginState = ShowdownGameServerLoginState(str, Integer.valueOf(key))
        serverConnectionState.loginState = newLoginState
    }

    fun handlePrivateMessage(message: String) {
        val args = message.split("|")
        val sender = args[2].trim()
        val recipient = args[3].trim()
        val privateMessage = args[4]
        Cobblemon.LOGGER.info("Recieved PM from $sender: $privateMessage")
        if (privateMessage.startsWith("/")) {
            val commandParams = privateMessage.substring(1).split(" ")
            val commandHandler = commandDispatchMap[commandParams[0]]
            if (commandHandler == null) {
                Cobblemon.LOGGER.warn("Showdown server interpreter received an unknown command type: ${privateMessage.substring(1)}")
            }
            commandHandler?.invoke(message, sender)
        }
    }

    fun handleUpdateSearch(message: String) {
        val args = message.split("|")
        val searchObjJson = args[2]
        val searchObj = gson.fromJson(searchObjJson, ShowdownSearch::class.java)
        searchObj.searching.forEach {
            Cobblemon.LOGGER.info("Searching for $it")
        }
        searchObj.games?.forEach { key, value ->
            Cobblemon.LOGGER.info("Entered room $key")
        }
    }

    fun handleRequest(message: String, roomId: String) {
        val params = message.split("|")
        if (params[2].isEmpty()) {
            serverConnectionState.battlesBeingCreated[roomId] = RemoteShowdownBattle.Builder(roomId)
        }
        else {
            serverConnectionState.battlesBeingCreated[roomId]?.teamStr = params[2]
        }
    }

    fun handleStart(message: String, roomId: String) {
        println("Hit")
    }

    fun handlePlayer(message: String, roomId: String) {
        val params = message.split("|")
        if (params[3].trim() == serverConnectionState.loginState.username) {
            val battleBuilder = serverConnectionState.battlesBeingCreated[roomId]!!
            battleBuilder.localSide = params[3]
        }
    }

    fun handleGameType(message: String, roomId: String) {
        val params = message.split("|")
        val battleBuilder = serverConnectionState.battlesBeingCreated[roomId]!!
        battleBuilder.gameType = params[2]
    }

    //Just auto accepts challenges
    fun handleChallengeCommand(command: String, sender: String) {
        val showdownServer = Cobblemon.showdownServer
        showdownServer.sendServerMessage(("|/utm null}"))
        showdownServer.sendServerMessage("|/accept ${sender.lowercase()}")
    }
}