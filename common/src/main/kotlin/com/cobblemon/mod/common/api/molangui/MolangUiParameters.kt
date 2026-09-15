package com.cobblemon.mod.common.api.molangui

import com.bedrockk.molang.runtime.value.DoubleValue
import com.bedrockk.molang.runtime.value.MoValue
import com.bedrockk.molang.runtime.value.StringValue

internal object MolangUiParameters {
    val GENERAL_FUNCTIONS = setOf("format", "to_string", "to_number", "to_int", "is_number", "is_int", "is_blank")
    private val reserved = GENERAL_FUNCTIONS + setOf(
        "gui_id", "gui_time", "delta", "screen_width", "screen_height", "mouse_x", "mouse_y", "item", "translate", "event"
    )
    private val namePattern = Regex("[a-z_][a-z0-9_]*")

    fun requireName(name: String) {
        MolangUiPacketLimits.requireIdentifier(name, "parameter name")
        require(namePattern.matches(name) && name !in reserved) { "invalid or reserved parameter name '$name'" }
    }

    fun decode(types: Map<String, ParameterType>, values: Map<String, String>): Map<String, ParameterValue> {
        require(values.size <= MolangUiPacketLimits.MAX_ACTION_FIELDS) { "too many parameters" }
        return values.mapValues { (name, raw) ->
            requireName(name)
            val type = types[name] ?: throw IllegalArgumentException("undeclared parameter '$name'")
            MolangUiPacketLimits.requireValue(raw, name)
            ParameterValue.parse(type, raw) ?: throw IllegalArgumentException("invalid ${type.id} parameter '$name'")
        }
    }

    fun validate(values: Map<String, ParameterValue>) {
        require(values.size <= MolangUiPacketLimits.MAX_ACTION_FIELDS) { "too many parameters" }
        values.forEach { (name, value) ->
            requireName(name)
            MolangUiPacketLimits.requireValue(value.asWireValue(), name)
        }
    }

    fun requireVariableName(name: String): String {
        MolangUiPacketLimits.requireIdentifier(name, "variable name")
        return name.removePrefix("v.").also {
            require(namePattern.matches(it)) { "invalid variable '$name'" }
        }
    }

    fun validateVariables(values: Map<String, Double>) {
        val names = mutableSetOf<String>()
        values.forEach { (name, value) ->
            val canonical = requireVariableName(name)
            require(names.add(canonical) && value.isFinite()) {
                "invalid or duplicate variable '$name'"
            }
        }
    }
}

internal fun ParameterValue.asMoLangValue(): MoValue = when (this) {
    is ParameterValue.StringValue -> StringValue(value)
    is ParameterValue.NumberValue -> DoubleValue(value)
    is ParameterValue.BooleanValue -> DoubleValue(value)
    is ParameterValue.Vec3Value -> StringValue(asWireValue())
}
