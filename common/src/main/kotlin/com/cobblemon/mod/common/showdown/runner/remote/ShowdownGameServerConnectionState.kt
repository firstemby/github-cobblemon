package com.cobblemon.mod.common.showdown.runner.remote

/**
 * State that is tracked throughout the lifecycle of a connection to a showdown server
 */
data class ShowdownGameServerConnectionState(
    var loginState: ShowdownGameServerLoginState = ShowdownGameServerLoginState(),
    val battlesBeingCreated: MutableMap<String, RemoteShowdownBattle.Builder> = mutableMapOf()
)