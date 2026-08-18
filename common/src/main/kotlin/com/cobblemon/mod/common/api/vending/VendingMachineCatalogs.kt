/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.vending

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.data.JsonDataRegistry
import com.cobblemon.mod.common.api.molangui.ActionKey
import com.cobblemon.mod.common.api.molangui.MolangUiActionContext
import com.cobblemon.mod.common.api.molangui.MolangUiActionHandler
import com.cobblemon.mod.common.api.molangui.ParameterValue
import com.cobblemon.mod.common.api.reactive.SimpleObservable
import com.cobblemon.mod.common.util.adapters.IdentifierAdapter
import com.cobblemon.mod.common.util.cobblemonResource
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.packs.PackType

data class CatalogEntry(val item: ResourceLocation, val price: Int)

data class VendingMachineCatalog(val entries: List<CatalogEntry> = emptyList()) {

    val pageCount: Int get() = if (entries.isEmpty()) 1 else (entries.size + PAGE_SIZE - 1) / PAGE_SIZE

    fun clampPage(page: Int): Int = page.coerceIn(0, pageCount - 1)

    fun pageParameters(page: Int): Map<String, ParameterValue> {
        val safePage = clampPage(page)
        val parameters = linkedMapOf<String, ParameterValue>(
            "page" to ParameterValue.NumberValue(safePage.toDouble()),
            "page_count" to ParameterValue.NumberValue(pageCount.toDouble()),
            "page_text" to ParameterValue.StringValue("${safePage + 1}/$pageCount")
        )
        for (row in 0 until PAGE_SIZE) {
            val entry = entries.getOrNull(safePage * PAGE_SIZE + row)
            parameters["row${row}_item"] = ParameterValue.StringValue(entry?.item?.toString() ?: "")
            parameters["row${row}_price"] = ParameterValue.StringValue(entry?.price?.toString() ?: "")
        }
        return parameters
    }

    companion object { const val PAGE_SIZE = 3 }
}

object VendingMachinePageHandler : MolangUiActionHandler {
    val ACTION = ActionKey("vending_page")

    override fun handle(context: MolangUiActionContext) {
        val requested = (context.parameters["page"] as? ParameterValue.NumberValue)?.value ?: return
        val catalog = VendingMachineCatalogs.catalog(VendingMachineCatalogs.DEFAULT)
        context.session.updateParameters(catalog.pageParameters(requested.toInt()))
    }
}

object VendingMachineCatalogs : JsonDataRegistry<VendingMachineCatalog> {
    override val id = cobblemonResource("vending_machine")
    override val type = PackType.SERVER_DATA
    override val observable = SimpleObservable<VendingMachineCatalogs>()
    override val typeToken: TypeToken<VendingMachineCatalog> = TypeToken.get(VendingMachineCatalog::class.java)
    override val resourcePath = "vending_machine"
    override val gson = GsonBuilder()
        .registerTypeAdapter(ResourceLocation::class.java, IdentifierAdapter)
        .create()

    val DEFAULT = cobblemonResource("default")

    private val catalogs = mutableMapOf<ResourceLocation, VendingMachineCatalog>()

    fun catalog(id: ResourceLocation): VendingMachineCatalog = catalogs[id] ?: VendingMachineCatalog()

    override fun reload(data: Map<ResourceLocation, VendingMachineCatalog>) {
        catalogs.clear()
        data.forEach { (id, catalog) -> catalogs[id] = validate(id, catalog) }
        Cobblemon.LOGGER.info("Loaded ${catalogs.size} vending machine catalogs")
        observable.emit(this)
    }

    override fun sync(player: ServerPlayer) = Unit

    private fun validate(id: ResourceLocation, catalog: VendingMachineCatalog): VendingMachineCatalog {
        val kept = catalog.entries.filter { entry ->
            val known = BuiltInRegistries.ITEM.containsKey(entry.item)
            if (!known) Cobblemon.LOGGER.error("Vending catalog '$id' drops unknown item '${entry.item}'")
            else if (entry.price <= 0) Cobblemon.LOGGER.error("Vending catalog '$id' drops '${entry.item}' with price ${entry.price}")
            known && entry.price > 0
        }
        return VendingMachineCatalog(kept)
    }
}
