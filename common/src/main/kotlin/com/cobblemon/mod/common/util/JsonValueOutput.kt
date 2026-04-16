/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.util

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.mojang.serialization.Codec
import com.mojang.serialization.DataResult
import com.mojang.serialization.DynamicOps
import com.mojang.serialization.JsonOps
import com.mojang.serialization.MapCodec
import net.minecraft.core.HolderLookup
import net.minecraft.util.ProblemReporter
import net.minecraft.world.level.storage.ValueOutput
import java.util.Objects

class JsonValueOutput(
    private val problemReporter: ProblemReporter,
    private val ops: DynamicOps<JsonElement>,
    private val json: JsonObject = JsonObject()
) : ValueOutput {

    companion object {
        fun createWithContext(problemReporter: ProblemReporter, provider: HolderLookup.Provider): JsonValueOutput {
            return JsonValueOutput(problemReporter, provider.createSerializationContext(JsonOps.INSTANCE), JsonObject())
        }

        fun createWithoutContext(problemReporter: ProblemReporter): JsonValueOutput {
            return JsonValueOutput(problemReporter, JsonOps.INSTANCE, JsonObject())
        }
    }

    fun getJson(): JsonObject = json

    override fun <T : Any> store(name: String, codec: Codec<T>, value: T) {
        val result = codec.encodeStart(ops, value)
        if (result is DataResult.Success<JsonElement>) {
            json.add(name, result.value())
        } else if (result is DataResult.Error<JsonElement>) {
            problemReporter.report(EncodeToFieldFailedProblem(name, value, result))
            result.partialValue().ifPresent { json.add(name, it) }
        }
    }

    override fun <T : Any> storeNullable(
        name: String,
        codec: Codec<T>,
        value: T?
    ) {
        if (value != null) {
            store(name, codec, value)
        }
    }

    override fun <T : Any> store(codec: MapCodec<T>, value: T) {
        val result = codec.encoder().encodeStart(ops, value)
        if (result is DataResult.Success<JsonElement>) {
            val element = result.value()
            if (element is JsonObject) {
                element.entrySet().forEach { (key, value) ->
                    json.add(key, value)
                }
            }
        } else if (result is DataResult.Error<JsonElement>) {
            problemReporter.report(EncodeToMapFailedProblem(value, result))
            result.partialValue().ifPresent { partial ->
                if (partial is JsonObject) {
                    partial.entrySet().forEach { (key, value) ->
                        json.add(key, value)
                    }
                }
            }
        }
    }

    override fun putBoolean(name: String, value: Boolean) {
        json.addProperty(name, value)
    }

    override fun putByte(name: String, value: Byte) {
        json.addProperty(name, value)
    }

    override fun putShort(name: String, value: Short) {
        json.addProperty(name, value)
    }

    override fun putInt(name: String, value: Int) {
        json.addProperty(name, value)
    }

    override fun putLong(name: String, value: Long) {
        json.addProperty(name, value)
    }

    override fun putFloat(name: String, value: Float) {
        json.addProperty(name, value)
    }

    override fun putDouble(name: String, value: Double) {
        json.addProperty(name, value)
    }

    override fun putString(name: String, value: String) {
        json.addProperty(name, value)
    }

    override fun putIntArray(name: String, value: IntArray) {
        val array = JsonArray()
        value.forEach { array.add(it) }
        json.add(name, array)
    }

    private fun reporterForChild(name: String): ProblemReporter {
        return problemReporter.forChild(ProblemReporter.FieldPathElement(name))
    }

    override fun child(name: String): ValueOutput {
        val child = JsonObject()
        json.add(name, child)
        return JsonValueOutput(reporterForChild(name), ops, child)
    }

    override fun childrenList(name: String): ValueOutput.ValueOutputList {
        val array = JsonArray()
        json.add(name, array)
        return JsonValueOutputList(name, problemReporter, ops, array)
    }

    override fun <T : Any> list(
        name: String,
        codec: Codec<T>
    ): ValueOutput.TypedOutputList<T> {
        val array = JsonArray()
        json.add(name, array)
        return JsonTypedOutputList(problemReporter, name, ops, codec, array)
    }

    override fun discard(name: String) {
        json.remove(name)
    }

    override fun isEmpty(): Boolean {
        return json.size() == 0
    }

    private class JsonValueOutputList(
        private val fieldName: String,
        private val problemReporter: ProblemReporter,
        private val ops: DynamicOps<JsonElement>,
        private val array: JsonArray
    ) : ValueOutput.ValueOutputList {
        override fun addChild(): ValueOutput {
            val newChildIndex = array.size()
            val child = JsonObject()
            array.add(child)
            return JsonValueOutput(
                problemReporter.forChild(ProblemReporter.IndexedFieldPathElement(fieldName, newChildIndex)),
                ops,
                child
            )
        }

        override fun discardLast() {
            if (array.size() > 0) {
                array.remove(array.size() - 1)
            }
        }

        override fun isEmpty(): Boolean = array.size() == 0
    }

    private class JsonTypedOutputList<T : Any>(
        private val problemReporter: ProblemReporter,
        private val name: String,
        private val ops: DynamicOps<JsonElement>,
        private val codec: Codec<T>,
        private val array: JsonArray
    ) : ValueOutput.TypedOutputList<T> {
        override fun add(value: T) {
            val result = codec.encodeStart(ops, value)
            if (result is DataResult.Success<JsonElement>) {
                array.add(result.value())
            } else if (result is DataResult.Error<JsonElement>) {
                problemReporter.report(EncodeToListFailedProblem(name, value, result))
                result.partialValue().ifPresent { array.add(it) }
            }
        }

        override fun isEmpty(): Boolean = array.size() == 0
    }

    data class EncodeToFieldFailedProblem(
        val name: String,
        val value: Any,
        val error: DataResult.Error<*>
    ) : ProblemReporter.Problem {
        override fun description(): String {
            return "Failed to encode value '$value' to field '$name': ${error.message()}"
        }
    }

    data class EncodeToListFailedProblem(
        val name: String,
        val value: Any,
        val error: DataResult.Error<*>
    ) : ProblemReporter.Problem {
        override fun description(): String {
            return "Failed to append value '$value' to list '$name': ${error.message()}"
        }
    }

    data class EncodeToMapFailedProblem(
        val value: Any,
        val error: DataResult.Error<*>
    ) : ProblemReporter.Problem {
        override fun description(): String {
            return "Failed to merge value '$value' to an object: ${error.message()}"
        }
    }
}
