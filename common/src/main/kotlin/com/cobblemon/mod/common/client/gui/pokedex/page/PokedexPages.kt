/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.pokedex.page

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.data.JsonDataRegistry
import com.cobblemon.mod.common.api.reactive.SimpleObservable
import com.cobblemon.mod.common.util.cobblemonResource
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.packs.PackType

object PokedexPages : JsonDataRegistry<PokedexPageDefinition> {
    override val id = cobblemonResource("pokedex_pages")
    override val type = PackType.CLIENT_RESOURCES
    override val observable = SimpleObservable<PokedexPages>()
    override val gson = GsonBuilder()
        .disableHtmlEscaping()
        .setPrettyPrinting()
        .create()
    override val typeToken: TypeToken<PokedexPageDefinition> = TypeToken.get(PokedexPageDefinition::class.java)
    override val resourcePath = "pokedex/pages"

    private val definitions = linkedMapOf<ResourceLocation, PokedexPageDefinition>()
    private val pageFactories = mutableMapOf<ResourceLocation, (ResourceLocation, PokedexPageDefinition) -> PokedexGuiPage>()

    init {
        registerPageType(cobblemonResource("main_dex")) { pageId, def -> MainDexPokedexPage(pageId, def.titleKey) }
        registerPageType(cobblemonResource("photo_gallery")) { pageId, def -> PhotoGalleryPokedexPage(pageId, def.titleKey) }
        registerPageType(cobblemonResource("cobblepedia")) { pageId, def -> CobblePediaPokedexPage(pageId, def.titleKey) }
    }

    override fun sync(player: ServerPlayer) {}

    override fun reload(data: Map<ResourceLocation, PokedexPageDefinition>) {
        definitions.clear()
        data.forEach { (identifier, page) ->
            if (page.enabled) {
                definitions[identifier] = page
            }
        }
        observable.emit(this)
    }

    fun registerPageType(
        typeId: ResourceLocation,
        factory: (ResourceLocation, PokedexPageDefinition) -> PokedexGuiPage
    ) {
        pageFactories[typeId] = factory
    }

    fun resolvePages(): List<PokedexGuiPage> {
        val pages = definitions
            .entries
            .sortedWith(compareBy<Map.Entry<ResourceLocation, PokedexPageDefinition>> { it.value.order }.thenBy { it.key.toString() })
            .mapNotNull { (id, definition) ->
                val typeId = parseType(definition) ?: return@mapNotNull null
                val factory = pageFactories[typeId]
                if (factory == null) {
                    Cobblemon.LOGGER.warn("Skipped Pokedex page {} - unknown page type {}", id, definition.pageType)
                    null
                } else {
                    factory(id, definition)
                }
            }
            .toMutableList()

        if (pages.isEmpty()) {
            pages.add(MainDexPokedexPage(cobblemonResource("fallback_main_dex"), "ui.pokedex.page.pokedex"))
            pages.add(PhotoGalleryPokedexPage(cobblemonResource("fallback_photo_gallery"), "ui.pokedex.page.photo_gallery"))
            return pages
        }

        if (pages.none { it is MainDexPokedexPage }) {
            pages.add(0, MainDexPokedexPage(cobblemonResource("fallback_main_dex"), "ui.pokedex.page.pokedex"))
        }

        return pages
    }

    private fun parseType(definition: PokedexPageDefinition): ResourceLocation? {
        if (definition.pageType.isBlank()) return null
        return runCatching { ResourceLocation.parse(definition.pageType) }
            .onFailure {
                Cobblemon.LOGGER.warn("Skipped Pokedex page definition - invalid pageType {}", definition.pageType)
            }
            .getOrNull()
    }
}