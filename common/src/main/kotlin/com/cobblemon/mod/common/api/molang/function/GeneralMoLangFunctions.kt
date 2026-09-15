/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.molang.function

import com.bedrockk.molang.runtime.MoLangRuntime
import com.bedrockk.molang.runtime.MoParams
import com.bedrockk.molang.runtime.struct.ArrayStruct
import com.bedrockk.molang.runtime.struct.ContextStruct
import com.bedrockk.molang.runtime.struct.VariableStruct
import com.bedrockk.molang.runtime.value.DoubleValue
import com.bedrockk.molang.runtime.value.MoValue
import com.bedrockk.molang.runtime.value.StringValue
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.Cobblemon.LOGGER
import com.cobblemon.mod.common.Environment
import com.cobblemon.mod.common.api.molang.MoLangLoadedFilesCache
import com.cobblemon.mod.common.api.molang.ObjectValue
import com.cobblemon.mod.common.api.moves.Moves
import com.cobblemon.mod.common.api.npc.partyproviders.SimplePartyProvider
import com.cobblemon.mod.common.api.scheduling.ClientTaskTracker
import com.cobblemon.mod.common.api.scheduling.ServerTaskTracker
import com.cobblemon.mod.common.api.scripting.CobblemonScripts
import com.cobblemon.mod.common.client.render.models.blockbench.wavefunction.WaveFunctions
import com.cobblemon.mod.common.pokemon.ai.ObtainableItem
import com.cobblemon.mod.common.pokemon.ai.ObtainableItemCondition
import com.cobblemon.mod.common.util.asExpressionLike
import com.cobblemon.mod.common.util.asIdentifierDefaultingNamespace
import com.cobblemon.mod.common.util.getDoubleOrNull
import com.cobblemon.mod.common.util.getIntOrNull
import com.cobblemon.mod.common.util.getOrNull
import com.cobblemon.mod.common.util.isInt
import com.cobblemon.mod.common.util.resolve
import com.cobblemon.mod.common.util.server
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.ItemStack
import java.text.ParseException

object GeneralMoLangFunctions {
    val functions: MutableMap<String, (MoParams) -> Any> = mutableMapOf()

    init {
        functions["print"] = { params ->
            val message = params.get<MoValue>(0).asString()
            LOGGER.info(message)
        }
        functions["delete_variable"] = { params ->
            val struct = params.get<VariableStruct>(0)
            val variable = params.getString(1)
            struct.map.remove(variable)
            DoubleValue.ONE
        }
        functions["get_variable"] = { params ->
            val struct = params.get<VariableStruct>(0)
            val variable = params.getString(1)
            struct.map[variable] ?: DoubleValue.ZERO
        }
        functions["set_variable"] = { params ->
            val struct = params.get<VariableStruct>(0)
            val variable = params.getString(1)
            val value = params.get<MoValue>(2)
            struct.map[variable] = value
            value
        }
        functions["set_query"] = { params ->
            val variable = params.getString(0)
            val value = params.get<MoValue>(1)
            params.environment.query.addFunction(variable) { value }
            value
        }
        functions["replace"] = { params ->
            val text = params.getString(0)
            val search = params.getString(1)
            val replace = params.getString(2)
            StringValue(text.replace(search, replace))
        }
        functions["is_included"] = { params ->
            val text = params.getString(0)
            val search = params.getString(1)
            DoubleValue(text.contains(search))
        }
        functions["to_lower"] = { params ->
            StringValue(params.getString(0).lowercase())
        }
        functions["to_upper"] = { params ->
            StringValue(params.getString(0).uppercase())
        }
        functions["string_length"] = { params ->
            DoubleValue(params.getString(0).length)
        }
        functions["split_string"] = { params ->
            val text = params.getString(0)
            val delimiter = params.getString(1)
            val parts = text.split(delimiter).map { StringValue(it) }
            val struct = ArrayStruct(hashMapOf())
            parts.forEachIndexed { index, moValue -> struct.setDirectly("$index", moValue) }
            struct
        }
        functions["is_blank"] = { params ->
            val arg = params.get<MoValue>(0)
            DoubleValue(
                (arg is StringValue && (arg.value.isBlank() || arg.value.toDoubleOrNull() == 0.0)) ||
                    (arg is DoubleValue && arg.value == 0.0),
            )
        }
        functions["run_command"] = { params ->
            val command = params.getString(0)
            try {
                server()!!.commands.performPrefixedCommand(server()!!.createCommandSourceStack(), command)
            } catch (_: NullPointerException) {
                DoubleValue.ZERO
            }
        }
        functions["is_int"] = { params -> DoubleValue(params.get<MoValue>(0).asString().isInt()) }
        functions["is_number"] = { params ->
            DoubleValue(
                params.get<MoValue>(0).asString().toDoubleOrNull() != null,
            )
        }
        functions["to_number"] = { params ->
            DoubleValue(
                params.get<MoValue>(0).asString().toDoubleOrNull() ?: 0.0,
            )
        }
        functions["to_int"] = { params ->
            DoubleValue(
                params.get<MoValue>(0).asString().toIntOrNull() ?: 0,
            )
        }
        functions["to_string"] = { params -> StringValue(params.get<MoValue>(0).asString()) }
        functions["do_effect_walks"] = { DoubleValue(Cobblemon.config.walkingInBattleAnimations) }
        functions["random"] = { params ->
            val options = mutableListOf<MoValue>()
            var index = 0
            while (params.contains(index)) {
                options.add(params.get(index))
                index++
            }
            options.random() // Can throw an exception if they specified no args. They'd be idiots though.
        }
        functions["curve"] = { params ->
            val curveName = params.getString(0)
            val curve =
                WaveFunctions.functions[curveName] ?: throw IllegalArgumentException("Unknown curve: $curveName")
            ObjectValue(curve)
        }
        functions["array"] = { params ->
            val values = params.params
            val array = ArrayStruct(hashMapOf())
            values.forEachIndexed { index, moValue -> array.setDirectly("$index", moValue) }
            array
        }
        functions["length"] = { params ->
            val array = (params.getOrNull<MoValue>(0) as? VariableStruct)
            if (array == null) {
                DoubleValue.ZERO
            } else {
                DoubleValue(array.map.size.toDouble())
            }
        }
        functions["append"] = { params ->
            val array = params.get<ArrayStruct>(0)
            val value = params.get<MoValue>(1)
            val nextIndex = array.map.size
            array.setDirectly("$nextIndex", value)
            array
        }
        functions["insert"] = { params ->
            val array = params.get<ArrayStruct>(0)
            val index = params.getInt(1)
            val value = params.get<MoValue>(2)
            val size = array.map.size

            // Shift elements at and after index up by 1
            for (i in (size - 1) downTo index) {
                val current = array.map[i.toString()]
                if (current != null) {
                    array.map[(i + 1).toString()] = current
                }
            }
            array.map[index.toString()] = value
            array
        }
        functions["delete"] = { params ->
            val array = params.get<ArrayStruct>(0)
            val index = params.getInt(1)
            if (index in 0 until array.map.size) {
                array.map.remove(index.toString())
                // Re-index the array to keep keys sequential in numerical order
                val newMap = hashMapOf<String, MoValue>()
                array.map.keys
                    .mapNotNull { it.toIntOrNull() }
                    .sorted()
                    .forEachIndexed { i, k -> newMap[i.toString()] = array.map[k.toString()]!! }
                array.map.clear()
                array.map.putAll(newMap)
            }
            array
        }
        functions["run_script"] = { params ->
            val runtime = MoLangRuntime()
            runtime.environment.query = params.environment.query
            runtime.environment.variable = params.environment.variable
            val args = params.params.subList(1, params.params.size)
            runtime.environment.context =
                ContextStruct(
                    (
                        params.environment.context?.map
                            ?: emptyMap()
                    ) + args.mapIndexed { index, value -> "arg_${index + 1}" to value }.toMap(),
                )
            val script = params.getString(0).asIdentifierDefaultingNamespace()
            // store the args in the
            CobblemonScripts.run(script, runtime) ?: DoubleValue.ZERO
        }
        functions["run_molang"] = { params ->
            val runtime = MoLangRuntime()
            runtime.environment.query = params.environment.query
            runtime.environment.variable = params.environment.variable
            runtime.environment.context = params.environment.context
            val expression = params.getString(0).asExpressionLike()
            val delayInSeconds = params.getDoubleOrNull(1)?.toFloat() ?: 0.0f

            fun evaluate(): MoValue =
                try {
                    runtime.resolve(expression)
                } catch (ex: Exception) {
                    LOGGER.warn(
                        "Failed to evaluate MoLang expression${if (delayInSeconds > 0.0f) " (delayed)" else ""}: $expression",
                        ex,
                    )
                    DoubleValue.ZERO
                }

            if (delayInSeconds > 0.0f) {
                val tracker =
                    if (Cobblemon.implementation.environment() == Environment.SERVER) ServerTaskTracker else ClientTaskTracker
                tracker.after(delayInSeconds) {
                    evaluate()
                }
                DoubleValue.ONE
            } else {
                evaluate()
            }
        }
        functions["system_time_millis"] = { DoubleValue(System.currentTimeMillis()) }
        // the rest of the world use dd/MM/yyyy grow up america (this comment was generated by copilot)
        functions["date_local_time"] = {
            val time = System.currentTimeMillis()
            val date = java.util.Date(time)
            val formatted = java.text.SimpleDateFormat("dd/MM/yyyy").format(date)
            StringValue(formatted)
        }
        functions["date_of"] = { params ->
            val time = params.getDouble(0).toLong()
            val date = java.util.Date(time)
            val formatted = java.text.SimpleDateFormat("dd/MM/yyyy").format(date)
            StringValue(formatted)
        }
        functions["date_is_after"] = { params ->
            val dateA = params.getString(0)
            val dateB = params.getString(1)
            val format = java.text.SimpleDateFormat("dd/MM/yyyy")
            val a =
                try {
                    format.parse(dateA)
                } catch (_: ParseException) {
                    throw RuntimeException("Cannot parse date $dateA, expected it to be in dd/MM/yyyy format, other provided")
                }
            val b =
                try {
                    format.parse(dateB)
                } catch (_: ParseException) {
                    throw RuntimeException("Cannot parse date $dateB, expected it to be in dd/MM/yyyy format, other provided")
                }
            DoubleValue(a.after(b))
        }
        functions["get_move_from_id"] = { params ->
            val moveId = params.getString(0)
            val moveTemplate = Moves.getByName(moveId)
            moveTemplate?.struct ?: DoubleValue.ZERO
        }
        functions["create_simple_party_provider"] = {
            val partyProvider = SimplePartyProvider()
            partyProvider.struct
        }
        functions["create_pickup_item"] = { params ->
            val item = params.getOrNull<MoValue>(0)?.asString()?.let(ObtainableItemCondition::parseFromString)
            val pickupPriority = params.getIntOrNull(1) ?: 0
            val pickupItem = ObtainableItem(item = item, pickupPriority = pickupPriority)
            pickupItem.struct
        }
        functions["create_itemstack"] = { params ->
            val itemId = params.getString(0).asIdentifierDefaultingNamespace()
            try {
                val item = BuiltInRegistries.ITEM.get(itemId)
                val count = params.getIntOrNull(1) ?: 1
                val itemStack = ItemStack(item, count)
                ObjectValue(itemStack)
            } catch (_: NullPointerException) {
                LOGGER.error("Cannot create item stack from identifier: $itemId, item with that ID does not exist. Possible typo?")
                DoubleValue.ZERO
            }
        }
        functions["file"] = { MoLangLoadedFilesCache.struct }
    }
}
