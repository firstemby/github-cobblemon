package com.cobblemon.mod.common.showdown

data class ShowdownSearch(
    val searching: List<String>,
    val games: Map<String, String>?
) {
}