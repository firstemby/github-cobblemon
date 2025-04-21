package com.cobblemon.mod.common.util.adapters

import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.pokemon.transformation.form.GigantamaxForm
import com.cobblemon.mod.common.pokemon.transformation.form.PermanentForm
import com.cobblemon.mod.common.pokemon.transformation.form.TemporaryForm
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import java.lang.reflect.Type
import kotlin.reflect.KClass

/**
 * Type adapter for [FormData] derivations.
 *
 * @author Segfault Guy
 * @since February 23rd, 2025
 */
object FormAdapter : JsonDeserializer<FormData> {

    private const val TYPE = "variant"

    private val types = mutableMapOf<String, KClass<out FormData>>()

    init {
        registerType("gigantamax", GigantamaxForm::class)
        registerType("temporary", TemporaryForm::class)
        registerType("permanent", PermanentForm::class)
    }
    // these are only forms that are deserializable. StandardForms are constructed from Species data and do not apply.

    fun <T : FormData> registerType(id: String, type: KClass<T>) {
        types[id.lowercase()] = type
    }

    override fun deserialize(jsonIn: JsonElement, typeOfT: Type, ctx: JsonDeserializationContext): FormData {
        val json = jsonIn.asJsonObject
        val variant = json.get(TYPE)?.asString?.lowercase()
        val type = variant?.let { types[variant] } ?:  throw IllegalArgumentException("Cannot resolve type for form variant $variant")
        return ctx.deserialize(json, type.java)
    }
}