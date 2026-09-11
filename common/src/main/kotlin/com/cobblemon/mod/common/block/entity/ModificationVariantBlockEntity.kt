/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.block.entity

import com.cobblemon.mod.common.CobblemonItemComponents
import com.cobblemon.mod.common.api.modification.ModificationVariant
import com.cobblemon.mod.common.api.modification.ModificationVariants
import com.cobblemon.mod.common.item.components.ModificationVariantComponent
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.core.component.DataComponentMap
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState

abstract class ModificationVariantBlockEntity(type: BlockEntityType<*>, pos: BlockPos, state: BlockState) : BlockEntity(type, pos, state) {
    var variantIdentifier: ResourceLocation? = null

    fun variant(): ModificationVariant? = variantIdentifier?.let(ModificationVariants::getByIdentifier)

    override fun saveAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.saveAdditional(tag, registries)
        variantIdentifier?.let { tag.putString(VARIANT_TAG, it.toString()) }
    }

    override fun loadAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.loadAdditional(tag, registries)
        variantIdentifier = if (tag.contains(VARIANT_TAG)) ResourceLocation.tryParse(tag.getString(VARIANT_TAG)) else null
    }

    override fun getUpdatePacket(): Packet<ClientGamePacketListener> = ClientboundBlockEntityDataPacket.create(this)

    override fun getUpdateTag(registries: HolderLookup.Provider): CompoundTag = saveWithoutMetadata(registries)

    override fun applyImplicitComponents(componentInput: DataComponentInput) {
        super.applyImplicitComponents(componentInput)
        variantIdentifier = componentInput.get(CobblemonItemComponents.MODIFICATION_VARIANT)?.variant
    }

    override fun collectImplicitComponents(builder: DataComponentMap.Builder) {
        super.collectImplicitComponents(builder)
        val identifier = variantIdentifier ?: return
        builder.set(CobblemonItemComponents.MODIFICATION_VARIANT, ModificationVariantComponent(identifier))
        ModificationVariants.getByIdentifier(identifier)?.let { builder.set(DataComponents.ITEM_NAME, it.displayName()) }
    }

    override fun removeComponentsFromTag(tag: CompoundTag) {
        super.removeComponentsFromTag(tag)
        tag.remove(VARIANT_TAG)
    }

    companion object {
        const val VARIANT_TAG = "ModificationVariant"
    }
}
