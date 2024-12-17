/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.npc.configuration

import com.bedrockk.molang.runtime.value.DoubleValue
import com.bedrockk.molang.runtime.value.MoValue
import com.bedrockk.molang.runtime.value.StringValue
import com.cobblemon.mod.common.entity.npc.NPCEntity
import com.cobblemon.mod.common.util.asTranslated
import com.cobblemon.mod.common.util.readString
import com.cobblemon.mod.common.util.readText
import com.cobblemon.mod.common.util.writeString
import com.cobblemon.mod.common.util.writeText
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component

/**
 * A predefined variable that will be declared and filled in on any NPC that extends from the class or preset
 * that specifies this variable. This gives the client a cleaner way to represent a variable that should exist
 * on the NPC.
 *
 * @author Hiroku
 * @since August 14th, 2023
 */
class NPCConfigVariable(
    val variableName: String = "variable",
    val displayName: Component = "Variable".asTranslated(),
    val description: Component = "A variable that can be used in the NPC's configuration.".asTranslated(),
    val type: NPCVariableType = NPCVariableType.NUMBER,
    val defaultValue: String = "0",
) {
    companion object {
        fun decode(buffer: RegistryFriendlyByteBuf): NPCConfigVariable {
            return NPCConfigVariable(
                buffer.readString(),
                buffer.readText(),
                buffer.readText(),
                buffer.readEnum(NPCVariableType::class.java),
                buffer.readString()
            )
        }
    }

    enum class NPCVariableType {
        NUMBER,
        TEXT,
        BOOLEAN;

        fun toMoValue(value: String): MoValue {
            return when (this) {
                TEXT -> StringValue(value)
                BOOLEAN -> DoubleValue(value.let { it == "1" || it.toBooleanStrictOrNull() == true  })
                else -> DoubleValue(value.toDouble())
            }
        }
    }

    fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeString(variableName)
        buffer.writeText(displayName)
        buffer.writeText(description)
        buffer.writeEnum(type)
        buffer.writeString(defaultValue)
    }

    fun apply(npc: NPCEntity, value: MoValue) {
        npc.config.setDirectly(variableName, value)
    }

    fun applyDefault(npc: NPCEntity) {
        npc.config.setDirectly(variableName, type.toMoValue(defaultValue))
    }
}