/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.modification

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.CobblemonItemComponents
import com.cobblemon.mod.common.api.data.JsonDataRegistry
import com.cobblemon.mod.common.api.reactive.SimpleObservable
import com.cobblemon.mod.common.block.modification.ModificationVariantBlock
import com.cobblemon.mod.common.net.messages.client.data.ModificationVariantRegistrySyncPacket
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.endsWith
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import java.io.File
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.world.item.ItemStack

object ModificationVariants : JsonDataRegistry<ModificationVariant> {
    override val id: ResourceLocation = cobblemonResource("modification_variants")
    override val type: PackType = PackType.SERVER_DATA
    override val observable = SimpleObservable<ModificationVariants>()
    override val typeToken: TypeToken<ModificationVariant> = TypeToken.get(ModificationVariant::class.java)
    override val resourcePath = "modification_variants"
    override val gson: Gson = GsonBuilder().disableHtmlEscaping().create()

    private val variantsByIdentifier = linkedMapOf<ResourceLocation, ModificationVariant>()
    private val variantsByParent = hashMapOf<ResourceLocation, List<ModificationVariant>>()
    private val variantOrder = compareBy<ModificationVariant> { it.identifier.namespace != Cobblemon.MODID }
        .thenBy { it.identifier.namespace }
        .thenBy { it.order }
        .thenBy { it.identifier.toString() }

    override fun reload(manager: ResourceManager) {
        val parsed = hashMapOf<ResourceLocation, ModificationVariant>()
        manager.listResources(resourcePath) { path -> path.endsWith(JsonDataRegistry.JSON_EXTENSION) }.forEach { (location, resource) ->
            val identifier = ResourceLocation.fromNamespaceAndPath(location.namespace, File(location.path).nameWithoutExtension)
            try {
                resource.openAsReader().use { reader ->
                    parsed[identifier] = ModificationVariantParser.parse(identifier, JsonParser.parseReader(reader).asJsonObject)
                }
            } catch (exception: Exception) {
                Cobblemon.LOGGER.error("Skipping modification variant {} from {}: {}", identifier, location, exception.message)
            }
        }
        reload(parsed)
    }

    override fun reload(data: Map<ResourceLocation, ModificationVariant>) {
        replaceEntries(data.values.filter(::isRegistered))
    }

    fun replaceEntries(entries: Collection<ModificationVariant>) {
        variantsByIdentifier.clear()
        variantsByParent.clear()
        entries.sortedWith(variantOrder).forEach { variant -> variantsByIdentifier[variant.identifier] = variant }
        variantsByParent.putAll(variantsByIdentifier.values.groupBy { it.parent })
        Cobblemon.LOGGER.info("Loaded {} modification variants", variantsByIdentifier.size)
        observable.emit(this)
    }

    override fun sync(player: ServerPlayer) {
        ModificationVariantRegistrySyncPacket(all()).sendToPlayer(player)
    }

    @JvmStatic
    fun all(): List<ModificationVariant> = variantsByIdentifier.values.toList()

    @JvmStatic
    fun forParent(parent: ResourceLocation): List<ModificationVariant> = variantsByParent[parent] ?: emptyList()

    @JvmStatic
    fun getByIdentifier(identifier: ResourceLocation): ModificationVariant? = variantsByIdentifier[identifier]

    @JvmStatic
    fun fromStack(stack: ItemStack): ModificationVariant? = stack.get(CobblemonItemComponents.MODIFICATION_VARIANT)?.variant?.let(::getByIdentifier)

    @JvmStatic
    fun parentOf(stack: ItemStack): ResourceLocation = fromStack(stack)?.parent ?: BuiltInRegistries.ITEM.getKey(stack.item)

    @JvmStatic
    fun hasVariantsFor(stack: ItemStack): Boolean = !stack.isEmpty && forParent(parentOf(stack)).isNotEmpty()

    private fun isRegistered(variant: ModificationVariant): Boolean {
        val hasParent = BuiltInRegistries.BLOCK.containsKey(variant.parent) || BuiltInRegistries.ITEM.containsKey(variant.parent)
        if (!hasParent) {
            Cobblemon.LOGGER.warn("Skipping modification variant {} because parent {} is not a registered block or item", variant.identifier, variant.parent)
            return false
        }
        if (variant.targetBlock() !is ModificationVariantBlock) {
            Cobblemon.LOGGER.warn("Skipping modification variant {} because block {} cannot hold modification variants", variant.identifier, variant.block)
            return false
        }
        return true
    }
}