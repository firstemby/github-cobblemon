/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.modification

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import net.minecraft.core.Vec3i
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.GsonHelper
import net.minecraft.world.phys.AABB

object ModificationVariantParser {
    private const val MIN_HITBOX_HORIZONTAL_PIXEL = -32.0
    private const val MAX_HITBOX_HORIZONTAL_PIXEL = 48.0
    private const val MIN_HITBOX_VERTICAL_PIXEL = 0.0
    private const val MAX_HITBOX_VERTICAL_PIXEL = 80.0

    fun parse(identifier: ResourceLocation, json: JsonObject): ModificationVariant {
        val parent = parseResourceLocation(json, "parent")
        val block = if (json.has("block")) parseResourceLocation(json, "block") else parent
        return ModificationVariant(
            identifier = identifier,
            parent = parent,
            block = block,
            model = parseResourceLocation(json, "model"),
            blockstateModels = parseBlockstateModels(json),
            cells = parseSize(json),
            hitbox = parseHitbox(json),
            order = GsonHelper.getAsInt(json, "order", 0),
            isTranslucent = GsonHelper.getAsBoolean(json, "translucent", false)
        )
    }

    private fun parseResourceLocation(json: JsonObject, key: String): ResourceLocation {
        val raw = GsonHelper.getAsString(json, key)
        return ResourceLocation.tryParse(raw) ?: throw JsonParseException("Invalid resource location '$raw' for '$key'")
    }

    private fun parseBlockstateModels(json: JsonObject): List<ModificationVariantBlockstateModel> {
        if (!json.has("blockstates")) {
            return emptyList()
        }
        return GsonHelper.getAsJsonObject(json, "blockstates").entrySet().map { (rawConditions, element) ->
            val conditions = ModificationVariantBlockstateModel.parseConditions(rawConditions)
                ?: throw JsonParseException("Blockstate condition '$rawConditions' must look like 'property=value' with commas between properties")
            val raw = GsonHelper.convertToString(element, "blockstate model")
            val model = ResourceLocation.tryParse(raw) ?: throw JsonParseException("Invalid resource location '$raw' for blockstate '$rawConditions'")
            ModificationVariantBlockstateModel(conditions, model)
        }
    }

    private fun parseSize(json: JsonObject): List<Vec3i> {
        if (!json.has("size")) {
            return listOf(Vec3i.ZERO)
        }
        val size = GsonHelper.getAsJsonObject(json, "size")
        val height = GsonHelper.getAsInt(size, "height", 1)
        val depth = GsonHelper.getAsInt(size, "depth", 1)
        if (height !in 1..ModificationVariant.MAX_HEIGHT) {
            throw JsonParseException("Size height must be between 1 and ${ModificationVariant.MAX_HEIGHT}, found $height")
        }
        if (depth !in 1..ModificationVariant.MAX_DEPTH) {
            throw JsonParseException("Size depth must be between 1 and ${ModificationVariant.MAX_DEPTH}, found $depth")
        }
        return (0 until depth).flatMap { z -> (0 until height).map { y -> Vec3i(0, y, z) } }
    }

    private fun parseHitbox(json: JsonObject): List<AABB> {
        if (!json.has("hitbox")) {
            return emptyList()
        }
        val boxes = GsonHelper.getAsJsonArray(json, "hitbox").map { element ->
            parseBox(GsonHelper.convertToJsonArray(element, "hitbox box"))
        }
        if (boxes.size > ModificationVariant.MAX_HITBOX_BOXES) {
            throw JsonParseException("Hitbox has ${boxes.size} boxes, the limit is ${ModificationVariant.MAX_HITBOX_BOXES}")
        }
        return boxes
    }

    private fun parseBox(coordinates: JsonArray): AABB {
        if (coordinates.size() != 6) {
            throw JsonParseException("Hitbox boxes need exactly 6 coordinates, found ${coordinates.size()}")
        }
        val values = coordinates.map { it.asDouble }
        if (values.any { !it.isFinite() }) {
            throw JsonParseException("Hitbox box $values contains a non finite coordinate")
        }
        val box = AABB(values[0], values[1], values[2], values[3], values[4], values[5])
        if (box.minX >= box.maxX || box.minY >= box.maxY || box.minZ >= box.maxZ) {
            throw JsonParseException("Hitbox box $values has a minimum that is not below its maximum")
        }
        val isHorizontalInRange = listOf(box.minX, box.maxX, box.minZ, box.maxZ).all { it in MIN_HITBOX_HORIZONTAL_PIXEL..MAX_HITBOX_HORIZONTAL_PIXEL }
        val isVerticalInRange = listOf(box.minY, box.maxY).all { it in MIN_HITBOX_VERTICAL_PIXEL..MAX_HITBOX_VERTICAL_PIXEL }
        if (!isHorizontalInRange || !isVerticalInRange) {
            throw JsonParseException("Hitbox box $values is outside the allowed pixel range")
        }
        return box
    }
}
