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
    val scopedMessageDispatchMap = mutableMapOf<String, (String, String) -> Unit>()
    //For slash commands sent in PMs
    val commandDispatchMap = mutableMapOf<String, (String, String) -> Unit>()
    //Messages that should be processed by the normal ShowdownInterpreter
    val delegatedMessages = mutableSetOf<String>()

    private val gson = GsonBuilder().create()
    private val showdownServer = Cobblemon.showdownServer

    init {
        messageDispatchMap.put("formats", {})
        messageDispatchMap.put("challstr", ::handleChallStr)
        messageDispatchMap.put("pm", ::handlePrivateMessage)
        messageDispatchMap.put("updatesearch", ::handleUpdateSearch)
        scopedMessageDispatchMap.put("request", ::handleRequestCommand)

        commandDispatchMap.put("challenge", ::handleChallengeCommand)
    }

    fun interpret(message: String) {
        Cobblemon.LOGGER.info(message)
        val lines = message.split('\n')
        val isLocal = lines[0].startsWith(">")
        val roomId = if(isLocal) lines[0].substring(1) else null
        val startIndex = if(isLocal) 1 else 0
        for (line in lines.slice(startIndex until lines.size)) {
            val args = message.split("|")
            if (args.size == 1) {
                return
            }
            val messageType = args[1]
            if (messageType in delegatedMessages) {
                if (roomId == null) {
                    Cobblemon.LOGGER.error("Received delegated message with no room id")
                    return
                }
                /*
                val battle = BattleRegistry.getBattle(roomId)
                if (battle == null) {
                    Cobblemon.LOGGER.error("Received message for nonexistent battle")
                    return
                }
                ShowdownInterpreter.interpret(battle, message)
                 */
            }
            val messageHandler = messageDispatchMap[messageType]
            if (messageHandler == null) {
                Cobblemon.LOGGER.warn("Showdown server interpreter received an unknown message type: $messageType")
            }
            messageHandler?.invoke(message)
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

    fun handleRequestCommand(message: String, roomId: String) {
        /*
        if (BattleRegistry.getBattle(roomId) == null) {
            val player = server()!!.playerList.players.first()
            val npcEntity = server()!!.overworld().getNearestEntity(
                NPCEntity::class.java,
                TargetingConditions.DEFAULT,
                player,
                64.0,
                64.0,
                64.0,
                AABB.ofSize(player.position(), 64.0, 64.0, 64.0)
            )

            if (npcEntity == null) {
                Cobblemon.LOGGER.error("Couldn't find NPC entity")
                return
            }
            BattleRegistry.initShowdownControlledBattle(
                roomId,
                player.uuid,
                npcEntity,
                "",
            )
        }

         */
    }

    //Just auto accepts challenges
    fun handleChallengeCommand(command: String, sender: String) {
        showdownServer.sendServerMessage(("|/utm null}"))
        showdownServer.sendServerMessage("|/accept ${sender.lowercase()}")
    }
}