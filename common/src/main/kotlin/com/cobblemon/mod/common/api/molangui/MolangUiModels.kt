/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.molangui

import com.cobblemon.mod.common.api.molangui.container.SlotLayout
import com.cobblemon.mod.common.api.molangui.container.CollectionConfig
import com.cobblemon.mod.common.api.molangui.container.CloseItemsPolicy
import com.cobblemon.mod.common.api.molangui.container.ShiftClickRule
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

const val MOLANG_UI_FORMAT_VERSION = 1

enum class ParameterType(val id: String) {
    STRING("string"),
    NUMBER("number"),
    BOOLEAN("boolean"),
    VEC3("vec3");

    companion object {
        fun fromId(id: String): ParameterType? = entries.firstOrNull { it.id == id }
    }
}

sealed interface ParameterValue {
    val type: ParameterType
    fun asWireValue(): String

    data class StringValue(val value: String) : ParameterValue {
        override val type = ParameterType.STRING
        override fun asWireValue() = value
    }

    data class NumberValue(val value: Double) : ParameterValue {
        init { require(value.isFinite()) { "number parameters must be finite" } }
        override val type = ParameterType.NUMBER
        override fun asWireValue() = value.toString()
    }

    data class BooleanValue(val value: Boolean) : ParameterValue {
        override val type = ParameterType.BOOLEAN
        override fun asWireValue() = value.toString()
    }

    data class Vec3Value(val x: Double, val y: Double, val z: Double) : ParameterValue {
        init { require(x.isFinite() && y.isFinite() && z.isFinite()) { "vec3 parameters must be finite" } }
        override val type = ParameterType.VEC3
        override fun asWireValue() = "$x,$y,$z"
    }

    companion object {
        fun parse(type: ParameterType, value: String): ParameterValue? = when (type) {
            ParameterType.STRING -> StringValue(value)
            ParameterType.NUMBER -> value.toDoubleOrNull()?.takeIf(Double::isFinite)?.let(::NumberValue)
            ParameterType.BOOLEAN -> value.toBooleanStrictOrNull()?.let(::BooleanValue)
            ParameterType.VEC3 -> value.split(',').takeIf { it.size == 3 }
                ?.map { it.trim().toDoubleOrNull() }
                ?.takeIf { it.all { part -> part != null && part.isFinite() } }
                ?.let { Vec3Value(it[0]!!, it[1]!!, it[2]!!) }
        }
    }
}

data class ParameterDefinition(
    val type: ParameterType,
    val required: Boolean = true,
    val default: ParameterDefault? = null
)

sealed interface ParameterDefault {
    data class Literal(val value: ParameterValue) : ParameterDefault
    data class Molang(val expression: String) : ParameterDefault
}

@JvmInline
value class ActionKey(val value: String) {
    init { require(value.isNotBlank() && value.length <= MolangUiPacketLimits.MAX_IDENTIFIER_LENGTH) }
}

data class ActionDefinition(
    val handler: ResourceLocation? = null,
    val parameters: Map<String, ParameterType> = emptyMap()
)

data class MolangUiContract(
    val id: ResourceLocation,
    val formatVersion: Int,
    val contractVersion: String,
    val mode: GuiMode,
    val parameters: Map<String, ParameterDefinition>,
    val actions: Map<ActionKey, ActionDefinition>,
    val onOpen: ResourceLocation?,
    val collections: Map<String, CollectionConfig>,
    val shiftClickRules: List<ShiftClickRule>,
    val closeItemsPolicy: CloseItemsPolicy,
    val exclusive: Boolean
) {
    init {
        require(mode == GuiMode.SCREEN || collections.isEmpty()) { "overlay views cannot have container collections" }
        SlotLayout.validateCollections(collections.mapValues { it.value.layout })
    }
}

data class MolangUiOpenRequest(
    val uiId: ResourceLocation,
    val parameters: Map<String, ParameterValue> = emptyMap(),
    val blockPos: BlockPos? = null
)

sealed interface MolangUiOpenResult {
    data class Opened(val session: MolangUiSessionHandle) : MolangUiOpenResult
    data class Rejected(val reason: RejectionReason, val detail: String) : MolangUiOpenResult
}

enum class RejectionReason {
    UNKNOWN_UI,
    INCOMPATIBLE_CLIENT,
    INVALID_PARAMETERS,
    SESSION_LIMIT,
    INVALID_CONTAINER,
    INTERNAL_ERROR
}

enum class SessionState { OPENING, ACTIVE, CLOSING, CLOSED }

interface MolangUiSessionHandle : AutoCloseable {
    val sessionId: UUID
    val uiId: ResourceLocation
    val state: SessionState
    fun updateParameters(parameters: Map<String, ParameterValue>): Boolean
    fun updateVariables(variables: Map<String, Double>): Boolean
    override fun close()
}

data class MolangUiActionContext(
    val session: MolangUiSessionHandle,
    val player: ServerPlayer,
    val parameters: Map<String, ParameterValue>
)

fun interface MolangUiActionHandler {
    fun handle(context: MolangUiActionContext)
}
