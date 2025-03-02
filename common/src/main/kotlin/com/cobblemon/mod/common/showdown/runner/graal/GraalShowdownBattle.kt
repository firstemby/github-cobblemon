package com.cobblemon.mod.common.showdown.runner.graal

import com.cobblemon.mod.common.showdown.runner.ShowdownBattle
import org.graalvm.polyglot.Value

class GraalShowdownBattle(
    override val id: String,
    val sendBattleFunction: Value
) : ShowdownBattle() {
    override fun sendMessages(messages: List<String>) {
        sendBattleFunction.execute(id, messages)
    }
}