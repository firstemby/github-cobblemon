package com.cobblemon.mod.common.client.editor.widget

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExpressionLikeFieldTest {
    @Test fun `long expressions retain every UTF8 byte when the field is created`() {
        val expression = "v.arena_center_x + ".repeat(100) + "'Pokémon雪'"
        assertEquals(expression, ExpressionLikeField("expression", expression).getValue())
    }

    @Test fun `compact ternaries stay inline while valid namespaced script ids select script mode`() {
        assertTrue(ExpressionLikeField.looksLikeResourceLocation("cobblemon:camera/orbit"))
        assertFalse(ExpressionLikeField.looksLikeResourceLocation("v.visible?1:0"))
        assertFalse(ExpressionLikeField.looksLikeResourceLocation("query.is_alive?64.0:0.0"))
        assertFalse(ExpressionLikeField.looksLikeResourceLocation("70.0"))
    }
}
