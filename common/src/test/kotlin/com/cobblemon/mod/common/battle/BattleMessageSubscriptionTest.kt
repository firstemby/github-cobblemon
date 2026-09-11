package com.cobblemon.mod.common.battle

import com.cobblemon.mod.common.client.battle.ClientBattleMessageQueue
import net.minecraft.network.chat.Component
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BattleMessageSubscriptionTest {
    @Test
    fun `reopened log receives history without retaining disposed screens`() {
        val queue = ClientBattleMessageQueue()
        val first = Component.translatable("cobblemon.battle.turn", 1)
        val second = Component.translatable("cobblemon.battle.turn", 2)
        queue.addComponents(listOf(first))
        val closedScreen = mutableListOf<Component>()
        val unsubscribe = queue.subscribeComponents { closedScreen.add(it) }
        unsubscribe()
        queue.addComponents(listOf(second))
        val reopenedScreen = mutableListOf<Component>()
        queue.subscribeComponents { reopenedScreen.add(it) }
        assertEquals(listOf(first), closedScreen)
        assertEquals(listOf(first, second), reopenedScreen)
    }
}
