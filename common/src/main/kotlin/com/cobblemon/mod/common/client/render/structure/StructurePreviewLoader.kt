/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.structure

import com.cobblemon.mod.common.Cobblemon
import net.minecraft.commands.arguments.blocks.BlockStateParser
import net.minecraft.core.BlockPos
import net.minecraft.core.Vec3i
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.block.Blocks
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.NbtAccounter
import net.minecraft.nbt.NbtIo
import net.minecraft.nbt.NbtUtils
import net.minecraft.nbt.Tag
import net.minecraft.resources.ResourceLocation

/**
 * Loads bundled structure `.nbt` templates from the mod jar (client-side) and parses them into
 * [StructurePreview]s for the CobblePedia preview pane. Reads the raw structure NBT directly
 * (palette + blocks + size) so it doesn't depend on the server-only StructureTemplate pipeline, then
 * applies the structure's data-defined processors via [StructurePreviewProcessors].
 *
 * Resolution is fully generic: every bundled `.nbt` under each namespace's `structure` data folder is
 * auto-indexed at first use, so adding new structures requires no code changes here. An entry id is matched against the
 * index by full path, bare filename, and digit-stripped variants (so a numbered family like
 * `abandoned_fortress1.nbt` resolves from the bare `abandoned_fortress`).
 */
object StructurePreviewLoader {
    private data class Indexed(val resourceName: String, val elementLocation: ResourceLocation)
    private data class Raw(val size: Vec3i, val blocks: List<StructurePreview.BlockEntry>, val elementLocation: ResourceLocation)

    private val index: Map<String, Indexed> by lazy { buildIndex() }
    private val rawCache = mutableMapOf<ResourceLocation, Raw?>()
    private val processedCache = mutableMapOf<ResourceLocation, StructurePreview?>()
    private val rerollSeed = mutableMapOf<ResourceLocation, Int>()

    fun forEntry(entryId: ResourceLocation): StructurePreview? = processedCache.getOrPut(entryId) {
        val raw = rawFor(entryId) ?: return@getOrPut null
        val seed = rerollSeed[entryId] ?: 0
        StructurePreview(raw.size, StructurePreviewProcessors.apply(raw.elementLocation, raw.blocks, seed))
    }

    /**
     * Re-applies the structure's processors with a fresh random roll (re-picking which blocks get
     * substituted, e.g. berry types), replacing the cached preview. Returns the new preview.
     */
    fun reroll(entryId: ResourceLocation): StructurePreview? {
        rerollSeed[entryId] = (rerollSeed[entryId] ?: 0) + 1
        processedCache.remove(entryId)
        return forEntry(entryId)
    }

    fun hasPreview(entryId: ResourceLocation): Boolean = forEntry(entryId) != null

    /** Cheap existence check: does some bundled .nbt resolve for this id (index lookup only, no parsing). */
    fun hasTemplate(entryId: ResourceLocation): Boolean = resolve(entryId) != null

    /** Family key (digit-stripped path / filename) -> all concrete .nbt variants, in numeric order. */
    private val families: Map<String, List<ResourceLocation>> by lazy {
        val byFamily = HashMap<String, LinkedHashSet<ResourceLocation>>()
        for (indexed in index.values.distinct()) {
            val loc = indexed.elementLocation
            byFamily.getOrPut("${loc.namespace}:${stripTrailingDigits(loc.path)}") { LinkedHashSet() }.add(loc)
            byFamily.getOrPut("${loc.namespace}:${stripTrailingDigits(loc.path.substringAfterLast('/'))}") { LinkedHashSet() }.add(loc)
        }
        byFamily.mapValues { (_, set) ->
            set.sortedWith(compareBy({ stripTrailingDigits(it.path) }, { trailingNumber(it.path) }))
        }
    }

    /** Folder key (full parent path and last folder segment) -> the .nbts inside, in numeric order. */
    private val folderFamilies: Map<String, List<ResourceLocation>> by lazy {
        val byFolder = HashMap<String, LinkedHashSet<ResourceLocation>>()
        for (indexed in index.values.distinct()) {
            val loc = indexed.elementLocation
            val parent = loc.path.substringBeforeLast('/', "")
            if (parent.isEmpty()) continue
            byFolder.getOrPut("${loc.namespace}:$parent") { LinkedHashSet() }.add(loc)
            byFolder.getOrPut("${loc.namespace}:${parent.substringAfterLast('/')}") { LinkedHashSet() }.add(loc)
        }
        byFolder.mapValues { (_, set) ->
            set.sortedWith(compareBy({ stripTrailingDigits(it.path) }, { trailingNumber(it.path) }))
        }
    }

    /**
     * Every concrete variant of an entry's structure family — e.g. `habitats/berry_patch` returns
     * `habitats/berry_patch1`..`berry_patchN` — so callers can cycle between variations. Matching is
     * deliberately forgiving about naming drift between worldgen ids and .nbt files: digit-stripped,
     * filename-only, and singular/plural forms are all tried (so `earthen_hives` finds `earthen_hive1`),
     * then folders named like the entry (so vanilla `shipwreck` finds `structure/shipwreck/`'s pieces).
     * Falls back to the entry itself, or an empty list when no .nbt exists for it.
     */
    fun variantsFor(entryId: ResourceLocation): List<ResourceLocation> {
        val ns = entryId.namespace
        val bases = LinkedHashSet<String>()
        for (base in listOf(
            entryId.path,
            stripTrailingDigits(entryId.path),
            entryId.path.substringAfterLast('/'),
            stripTrailingDigits(entryId.path.substringAfterLast('/'))
        )) {
            bases.add(base)
            bases.add(base + "s")
            if (base.endsWith("s")) bases.add(base.dropLast(1))
        }
        for (base in bases) {
            families["$ns:$base"]?.let { return it }
        }
        for (base in bases) {
            folderFamilies["$ns:$base"]?.let { return it }
        }
        return if (hasTemplate(entryId)) listOf(entryId) else emptyList()
    }

    private fun trailingNumber(path: String): Int = Regex("(\\d+)$").find(path)?.value?.toIntOrNull() ?: 0

    private fun rawFor(entryId: ResourceLocation): Raw? = rawCache.getOrPut(entryId) {
        val indexed = resolve(entryId) ?: return@getOrPut null
        val parsed = loadFromClasspath(indexed.resourceName) ?: return@getOrPut null
        Raw(parsed.size, parsed.blocks, indexed.elementLocation)
    }

    private fun resolve(entryId: ResourceLocation): Indexed? {
        val ns = entryId.namespace
        val path = entryId.path
        val candidates = listOf(
            "$ns:$path",
            "$ns:${stripTrailingDigits(path)}",
            "$ns:${path.substringAfterLast('/')}",
            "$ns:${stripTrailingDigits(path.substringAfterLast('/'))}"
        )
        for (candidate in candidates) {
            index[candidate]?.let { return it }
        }
        return null
    }

    private fun buildIndex(): Map<String, Indexed> {
        val result = HashMap<String, Indexed>()
        // Sort so lower-numbered variants (e.g. ...1.nbt) register first and win putIfAbsent.
        val resources = StructurePreviewResources.listResources("data", ".nbt")
            .filter { "/structure/" in "/$it" }
            .sorted()
        for (resource in resources) {
            val afterData = resource.substringAfter("data/", "")
            val ns = afterData.substringBefore('/', "")
            val rel = resource.substringAfter("/structure/", "").removeSuffix(".nbt")
            if (ns.isEmpty() || rel.isEmpty()) continue
            val indexed = Indexed(resource, ResourceLocation.fromNamespaceAndPath(ns, rel))
            val fileName = rel.substringAfterLast('/')
            listOf(
                "$ns:$rel",
                "$ns:${stripTrailingDigits(rel)}",
                "$ns:$fileName",
                "$ns:${stripTrailingDigits(fileName)}"
            ).forEach { key -> result.putIfAbsent(key, indexed) }
        }
        return result
    }

    private fun stripTrailingDigits(value: String): String = value.replace(Regex("\\d+$"), "")

    private fun loadFromClasspath(resourceName: String): StructurePreview? {
        val stream = StructurePreviewResources.openStream(resourceName) ?: return null
        return try {
            stream.use { parse(NbtIo.readCompressed(it, NbtAccounter.unlimitedHeap())) }
        } catch (e: Exception) {
            Cobblemon.LOGGER.warn("Failed to load structure preview from {}", resourceName, e)
            null
        }
    }

    private fun parse(root: CompoundTag): StructurePreview? {
        val blockGetter = BuiltInRegistries.BLOCK.asLookup()

        val palette: ListTag = when {
            root.contains("palette") -> root.getList("palette", Tag.TAG_COMPOUND.toInt())
            root.contains("palettes") -> (root.getList("palettes", Tag.TAG_LIST.toInt()).firstOrNull() as? ListTag) ?: return null
            else -> return null
        }
        val states = palette.map { NbtUtils.readBlockState(blockGetter, it as CompoundTag) }

        val blocks = ArrayList<StructurePreview.BlockEntry>()
        for (entry in root.getList("blocks", Tag.TAG_COMPOUND.toInt())) {
            val tag = entry as CompoundTag
            val posList = tag.getList("pos", Tag.TAG_INT.toInt())
            if (posList.size < 3) continue
            var state = states.getOrNull(tag.getInt("state")) ?: continue

            // Jigsaw blocks are placeholders: render their configured final_state instead.
            if (state.block === Blocks.JIGSAW && tag.contains("nbt")) {
                val finalState = tag.getCompound("nbt").getString("final_state")
                if (finalState.isNotEmpty()) {
                    state = runCatching { BlockStateParser.parseForBlock(blockGetter, finalState, false).blockState() }
                        .getOrDefault(state)
                }
            }

            // Skip air and the invisible structure markers so the preview looks like a built structure.
            if (state.isAir ||
                state.block === Blocks.STRUCTURE_VOID ||
                state.block === Blocks.STRUCTURE_BLOCK ||
                state.block === Blocks.JIGSAW ||
                state.block === Blocks.BARRIER ||
                state.block === Blocks.LIGHT
            ) continue

            blocks.add(StructurePreview.BlockEntry(BlockPos(posList.getInt(0), posList.getInt(1), posList.getInt(2)), state))
        }

        val sizeList = root.getList("size", Tag.TAG_INT.toInt())
        val size = if (sizeList.size >= 3) Vec3i(sizeList.getInt(0), sizeList.getInt(1), sizeList.getInt(2)) else Vec3i(1, 1, 1)
        return StructurePreview(size, blocks)
    }
}
