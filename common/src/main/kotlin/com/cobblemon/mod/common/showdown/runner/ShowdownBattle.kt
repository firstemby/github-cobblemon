package com.cobblemon.mod.common.showdown.runner

abstract class ShowdownBattle {
    abstract val id: String

    fun sendMessage(message: String) {
        sendMessages(message)
    }
    fun sendMessages(vararg messages: String) {
        return sendMessages(messages.toList())
    }

    abstract fun sendMessages(messages: List<String>)

}