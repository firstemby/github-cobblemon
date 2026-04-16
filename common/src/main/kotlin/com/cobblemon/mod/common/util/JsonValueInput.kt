/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.util

import com.google.common.collect.Streams
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.mojang.serialization.Codec
import com.mojang.serialization.DataResult
import com.mojang.serialization.DynamicOps
import com.mojang.serialization.JsonOps
import com.mojang.serialization.MapCodec
import net.minecraft.core.HolderLookup
import net.minecraft.util.ProblemReporter
import net.minecraft.world.level.storage.ValueInput
import java.util.*
import java.util.stream.Stream

class JsonValueInput private constructor(
    private val problemReporter: ProblemReporter,
    private val context: ValueInputContextHelper,
    private val input: JsonObject
) : ValueInput {

    companion object {
        fun create(
            problemReporter: ProblemReporter,
            holders: HolderLookup.Provider,
            json: JsonObject
        ): ValueInput {
            return JsonValueInput(problemReporter, ValueInputContextHelper(holders, JsonOps.INSTANCE), json)
        }

        fun create(
            problemReporter: ProblemReporter,
            holders: HolderLookup.Provider,
            elements: List<JsonObject>
        ): ValueInput.ValueInputList {
            return JsonCompoundListWrapper(problemReporter, ValueInputContextHelper(holders, JsonOps.INSTANCE), elements)
        }

        private fun wrapChild(
            problemReporter: ProblemReporter,
            context: ValueInputContextHelper,
            jsonObject: JsonObject
        ): ValueInput {
            return if (jsonObject.size() == 0) context.empty() else JsonValueInput(problemReporter, context, jsonObject)
        }
    }

    override fun <T : Any> read(name: String, codec: Codec<T>): Optional<T> {
        val element = input.get(name) ?: return Optional.empty()
        val result = codec.parse(context.ops(), element)
        return when (result) {
            is DataResult.Success -> Optional.of(result.value())
            is DataResult.Error -> {
                problemReporter.report(DecodeFromFieldFailedProblem(name, element, result))
                result.partialValue()
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun <T : Any> read(codec: MapCodec<T>): Optional<T> {
        val ops = context.ops()
        val result = ops.getMap(input).flatMap { map -> codec.decode(ops, map) }
        return when (result) {
            is DataResult.Success -> Optional.of(result.value())
            is DataResult.Error -> {
                problemReporter.report(DecodeFromMapFailedProblem(result))
                result.partialValue()
            }
        }
    }

    private fun getOptionalTypedElement(name: String): JsonElement? {
        return input.get(name)
    }

    override fun child(name: String): Optional<ValueInput> {
        val element = getOptionalTypedElement(name)
        return if (element is JsonObject) {
            Optional.of(wrapChild(name, element))
        } else {
            if (element != null) {
                problemReporter.report(UnexpectedTypeProblem(name, "Object", element.javaClass.simpleName))
            }
            Optional.empty()
        }
    }

    override fun childOrEmpty(name: String): ValueInput {
        val element = getOptionalTypedElement(name)
        return if (element is JsonObject) {
            wrapChild(name, element)
        } else {
            context.empty()
        }
    }

    override fun childrenList(name: String): Optional<ValueInput.ValueInputList> {
        val element = getOptionalTypedElement(name)
        return if (element is JsonArray) {
            Optional.of(wrapList(name, context, element))
        } else {
            if (element != null) {
                problemReporter.report(UnexpectedTypeProblem(name, "Array", element.javaClass.simpleName))
            }
            Optional.empty()
        }
    }

    override fun childrenListOrEmpty(name: String): ValueInput.ValueInputList {
        val element = getOptionalTypedElement(name)
        return if (element is JsonArray) {
            wrapList(name, context, element)
        } else {
            context.emptyList()
        }
    }

    override fun <T : Any> list(name: String, codec: Codec<T>): Optional<ValueInput.TypedInputList<T>> {
        val element = getOptionalTypedElement(name)
        return if (element is JsonArray) {
            Optional.of(wrapTypedList(name, element, codec))
        } else {
            if (element != null) {
                problemReporter.report(UnexpectedTypeProblem(name, "Array", element.javaClass.simpleName))
            }
            Optional.empty()
        }
    }

    override fun <T : Any> listOrEmpty(name: String, codec: Codec<T>): ValueInput.TypedInputList<T> {
        val element = getOptionalTypedElement(name)
        return if (element is JsonArray) {
            wrapTypedList(name, element, codec)
        } else {
            context.emptyTypedList()
        }
    }

    private fun getNumericElement(name: String): JsonPrimitive? {
        val element = input.get(name)
        if (element is JsonPrimitive && element.isNumber) {
            return element
        } else if (element != null) {
            problemReporter.report(UnexpectedNonNumberProblem(name, element.javaClass.simpleName))
        }
        return null
    }

    override fun getBooleanOr(name: String, defaultValue: Boolean): Boolean {
        val element = input.get(name)
        return if (element is JsonPrimitive && element.isBoolean) {
            element.asBoolean
        } else if (element is JsonPrimitive && element.isNumber) {
            element.asInt != 0
        } else {
            defaultValue
        }
    }

    override fun getByteOr(name: String, defaultValue: Byte): Byte {
        return getNumericElement(name)?.asByte ?: defaultValue
    }

    override fun getShortOr(name: String, defaultValue: Short): Int {
        return getNumericElement(name)?.asShort?.toInt() ?: defaultValue.toInt()
    }

    override fun getInt(name: String): Optional<Int> {
        return Optional.ofNullable(getNumericElement(name)?.asInt)
    }

    override fun getIntOr(name: String, defaultValue: Int): Int {
        return getNumericElement(name)?.asInt ?: defaultValue
    }

    override fun getLongOr(name: String, defaultValue: Long): Long {
        return getNumericElement(name)?.asLong ?: defaultValue
    }

    override fun getLong(name: String): Optional<Long> {
        return Optional.ofNullable(getNumericElement(name)?.asLong)
    }

    override fun getFloatOr(name: String, defaultValue: Float): Float {
        return getNumericElement(name)?.asFloat ?: defaultValue
    }

    override fun getDoubleOr(name: String, defaultValue: Double): Double {
        return getNumericElement(name)?.asDouble ?: defaultValue
    }

    override fun getString(name: String): Optional<String> {
        val element = input.get(name)
        return if (element is JsonPrimitive && element.isString) {
            Optional.of(element.asString)
        } else {
            Optional.empty()
        }
    }

    override fun getStringOr(name: String, defaultValue: String): String {
        val element = input.get(name)
        return if (element is JsonPrimitive && element.isString) {
            element.asString
        } else {
            defaultValue
        }
    }

    override fun getIntArray(name: String): Optional<IntArray> {
        val element = input.get(name)
        return if (element is JsonArray) {
            val array = IntArray(element.size())
            for (i in 0 until element.size()) {
                val e = element.get(i)
                if (e is JsonPrimitive && e.isNumber) {
                    array[i] = e.asInt
                }
            }
            Optional.of(array)
        } else {
            Optional.empty()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun lookup(): HolderLookup.Provider {
        return context.lookup()
    }

    private fun wrapChild(name: String, jsonObject: JsonObject): ValueInput {
        return if (jsonObject.size() == 0) {
            context.empty()
        } else {
            JsonValueInput(problemReporter.forChild(ProblemReporter.FieldPathElement(name)), context, jsonObject)
        }
    }

    private fun wrapList(name: String, context: ValueInputContextHelper, array: JsonArray): ValueInput.ValueInputList {
        return if (array.size() == 0) context.emptyList() else JsonListWrapper(problemReporter, name, context, array)
    }

    private fun <T : Any> wrapTypedList(name: String, array: JsonArray, codec: Codec<T>): ValueInput.TypedInputList<T> {
        return if (array.size() == 0) context.emptyTypedList() else JsonTypedListWrapper(problemReporter, name, context, codec, array)
    }

    private class JsonListWrapper(
        private val problemReporter: ProblemReporter,
        private val name: String,
        private val context: ValueInputContextHelper,
        private val array: JsonArray
    ) : ValueInput.ValueInputList {
        override fun isEmpty(): Boolean = array.size() == 0

        private fun reporterForChild(index: Int): ProblemReporter {
            return problemReporter.forChild(ProblemReporter.IndexedFieldPathElement(name, index))
        }

        private fun reportIndexUnwrapProblem(index: Int, element: JsonElement) {
            problemReporter.report(UnexpectedListElementTypeProblem(name, index, "Object", element.javaClass.simpleName))
        }

        override fun stream(): Stream<ValueInput> {
            return Streams.mapWithIndex(Streams.stream(array)) { element, index ->
                if (element is JsonObject) {
                    wrapChild(reporterForChild(index.toInt()), context, element)
                } else {
                    reportIndexUnwrapProblem(index.toInt(), element)
                    null
                }
            }.filter(Objects::nonNull).map { it!! }
        }

        override fun iterator(): MutableIterator<ValueInput> {
            val iterator = array.iterator()
            return object : MutableIterator<ValueInput> {
                private var index = 0
                private var nextValue: ValueInput? = null

                override fun hasNext(): Boolean {
                    while (nextValue == null && iterator.hasNext()) {
                        val element = iterator.next()
                        val currentIndex = index++
                        if (element is JsonObject) {
                            nextValue = wrapChild(reporterForChild(currentIndex), context, element)
                        } else {
                            reportIndexUnwrapProblem(currentIndex, element)
                        }
                    }
                    return nextValue != null
                }

                override fun next(): ValueInput {
                    if (!hasNext()) throw NoSuchElementException()
                    val value = nextValue!!
                    nextValue = null
                    return value
                }

                override fun remove() {
                    iterator.remove()
                }
            }
        }
    }

    private class JsonTypedListWrapper<T : Any>(
        private val problemReporter: ProblemReporter,
        private val name: String,
        private val context: ValueInputContextHelper,
        private val codec: Codec<T>,
        private val array: JsonArray
    ) : ValueInput.TypedInputList<T> {
        override fun isEmpty(): Boolean = array.size() == 0

        private fun reportIndexUnwrapProblem(index: Int, element: JsonElement, error: DataResult.Error<*>) {
            problemReporter.report(DecodeFromListFailedProblem(name, index, element, error))
        }

        override fun stream(): Stream<T> {
            return Streams.mapWithIndex(Streams.stream(array)) { element, index ->
                val result = codec.parse(context.ops(), element)
                when (result) {
                    is DataResult.Success -> result.value()
                    is DataResult.Error -> {
                        reportIndexUnwrapProblem(index.toInt(), element, result)
                        result.partialValue().orElse(null)
                    }
                }
            }.filter(Objects::nonNull)
        }

        override fun iterator(): MutableIterator<T> {
            val iterator = array.iterator()
            return object : MutableIterator<T> {
                private var index = 0
                private var nextValue: T? = null
                private var hasValue = false

                override fun hasNext(): Boolean {
                    while (!hasValue && iterator.hasNext()) {
                        val currentIndex = index++
                        val element = iterator.next()
                        val result = codec.parse(context.ops(), element)
                        when (result) {
                            is DataResult.Success -> {
                                nextValue = result.value()
                                hasValue = true
                            }
                            is DataResult.Error -> {
                                reportIndexUnwrapProblem(currentIndex, element, result)
                                val partial = result.partialValue()
                                if (partial.isPresent) {
                                    nextValue = partial.get()
                                    hasValue = true
                                }
                            }
                        }
                    }
                    return hasValue
                }

                override fun next(): T {
                    if (!hasNext()) throw NoSuchElementException()
                    val value = nextValue as T
                    nextValue = null
                    hasValue = false
                    return value
                }

                override fun remove() {
                    iterator.remove()
                }
            }
        }
    }

    private class JsonCompoundListWrapper(
        private val problemReporter: ProblemReporter,
        private val context: ValueInputContextHelper,
        private val list: List<JsonObject>
    ) : ValueInput.ValueInputList {
        override fun isEmpty(): Boolean = list.isEmpty()

        private fun wrapChild(index: Int, jsonObject: JsonObject): ValueInput {
            return wrapChild(problemReporter.forChild(ProblemReporter.IndexedFieldPathElement("list", index)), context, jsonObject)
        }

        override fun stream(): Stream<ValueInput> {
            return Streams.mapWithIndex(list.stream()) { element, index -> wrapChild(index.toInt(), element) }
        }

        override fun iterator(): MutableIterator<ValueInput> {
            val iterator = list.toMutableList().iterator()
            return object : MutableIterator<ValueInput> {
                private var index = 0
                override fun hasNext(): Boolean = iterator.hasNext()
                override fun next(): ValueInput = wrapChild(index++, iterator.next())
                override fun remove() {
                    throw UnsupportedOperationException()
                }
            }
        }
    }

    data class DecodeFromFieldFailedProblem(
        val name: String,
        val element: JsonElement,
        val error: DataResult.Error<*>
    ) : ProblemReporter.Problem {
        override fun description(): String {
            return "Failed to decode value '$element' from field '$name': ${error.message()}"
        }
    }

    data class DecodeFromListFailedProblem(
        val name: String,
        val index: Int,
        val element: JsonElement,
        val error: DataResult.Error<*>
    ) : ProblemReporter.Problem {
        override fun description(): String {
            return "Failed to decode value '$element' from field '$name' at index $index: ${error.message()}"
        }
    }

    data class DecodeFromMapFailedProblem(
        val error: DataResult.Error<*>
    ) : ProblemReporter.Problem {
        override fun description(): String {
            return "Failed to decode from map: ${error.message()}"
        }
    }

    data class UnexpectedTypeProblem(
        val name: String,
        val expected: String,
        val actual: String
    ) : ProblemReporter.Problem {
        override fun description(): String {
            return "Expected field '$name' to contain value of type $expected, but got $actual"
        }
    }

    data class UnexpectedNonNumberProblem(
        val name: String,
        val actual: String
    ) : ProblemReporter.Problem {
        override fun description(): String {
            return "Expected field '$name' to contain number, but got $actual"
        }
    }

    data class UnexpectedListElementTypeProblem(
        val name: String,
        val index: Int,
        val expected: String,
        val actual: String
    ) : ProblemReporter.Problem {
        override fun description(): String {
            return "Expected list '$name' to contain at index $index value of type $expected, but got $actual"
        }
    }
}

class ValueInputContextHelper(
    private val holders: HolderLookup.Provider,
    private val ops: DynamicOps<JsonElement>
) {
    private val emptyInput: ValueInput by lazy { EmptyValueInput(this) }
    private val emptyList: ValueInput.ValueInputList by lazy { EmptyValueInputList(this) }
    private val emptyTypedList: ValueInput.TypedInputList<Any> by lazy { EmptyTypedInputList(this) }

    fun ops(): DynamicOps<JsonElement> = ops
    fun lookup(): HolderLookup.Provider = holders

    fun empty(): ValueInput = emptyInput
    fun emptyList(): ValueInput.ValueInputList = emptyList
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> emptyTypedList(): ValueInput.TypedInputList<T> = emptyTypedList as ValueInput.TypedInputList<T>

    private class EmptyValueInput(private val context: ValueInputContextHelper) : ValueInput {
        override fun <T : Any> read(name: String, codec: Codec<T>): Optional<T> = Optional.empty()
        override fun <T : Any> read(codec: MapCodec<T>): Optional<T> = Optional.empty()
        override fun child(name: String): Optional<ValueInput> = Optional.empty()
        override fun childOrEmpty(name: String): ValueInput = this
        override fun childrenList(name: String): Optional<ValueInput.ValueInputList> = Optional.empty()
        override fun childrenListOrEmpty(name: String): ValueInput.ValueInputList = context.emptyList()
        override fun <T : Any> list(name: String, codec: Codec<T>): Optional<ValueInput.TypedInputList<T>> = Optional.empty()
        override fun <T : Any> listOrEmpty(name: String, codec: Codec<T>): ValueInput.TypedInputList<T> = context.emptyTypedList()
        override fun getBooleanOr(name: String, defaultValue: Boolean): Boolean = defaultValue
        override fun getByteOr(name: String, defaultValue: Byte): Byte = defaultValue
        override fun getShortOr(name: String, defaultValue: Short): Int = defaultValue.toInt()
        override fun getInt(name: String): Optional<Int> = Optional.empty()
        override fun getIntOr(name: String, defaultValue: Int): Int = defaultValue
        override fun getLongOr(name: String, defaultValue: Long): Long = defaultValue
        override fun getLong(name: String): Optional<Long> = Optional.empty()
        override fun getFloatOr(name: String, defaultValue: Float): Float = defaultValue
        override fun getDoubleOr(name: String, defaultValue: Double): Double = defaultValue
        override fun getString(name: String): Optional<String> = Optional.empty()
        override fun getStringOr(name: String, defaultValue: String): String = defaultValue
        override fun getIntArray(name: String): Optional<IntArray> = Optional.empty()
        @Deprecated("Deprecated in Java")
        override fun lookup(): HolderLookup.Provider = context.lookup()
    }

    private class EmptyValueInputList(private val context: ValueInputContextHelper) : ValueInput.ValueInputList {
        override fun isEmpty(): Boolean = true
        override fun stream(): Stream<ValueInput> = Stream.empty()
        override fun iterator(): MutableIterator<ValueInput> = Collections.emptyIterator()
    }

    private class EmptyTypedInputList<T : Any>(private val context: ValueInputContextHelper) : ValueInput.TypedInputList<T> {
        override fun isEmpty(): Boolean = true
        override fun stream(): Stream<T> = Stream.empty()
        override fun iterator(): MutableIterator<T> = Collections.emptyIterator()
    }
}
