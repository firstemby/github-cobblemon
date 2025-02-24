package com.cobblemon.mod.common.util.adapters

import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.pokemon.transformation.form.PermanentForm
import com.cobblemon.mod.common.pokemon.transformation.form.TemporaryForm
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import java.lang.reflect.Type
import kotlin.reflect.KClass

// TODO interface
object FormAdapter : JsonDeserializer<FormData> {

    private const val TYPE = "type"

    private val types = mutableMapOf<String, KClass<out FormData>>()

    init {
        registerType("temporary", TemporaryForm::class)
        registerType("permanent", PermanentForm::class)
    }

    fun <T : FormData> registerType(id: String, type: KClass<T>) {
        types[id.lowercase()] = type
    }

    override fun deserialize(jsonIn: JsonElement, typeOfT: Type, ctx: JsonDeserializationContext): FormData {
        val json = jsonIn.asJsonObject
        val variant = json.get(TYPE)?.asString?.lowercase()
        val type = variant?.let { types[variant] } ?: PermanentForm::class
        return ctx.deserialize(json, type.java)
    }
}