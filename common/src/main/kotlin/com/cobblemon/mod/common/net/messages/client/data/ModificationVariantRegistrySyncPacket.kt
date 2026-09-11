/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.client.data

import com.cobblemon.mod.common.api.modification.ModificationVariant
import com.cobblemon.mod.common.api.modification.ModificationVariants
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf

class ModificationVariantRegistrySyncPacket(variants: Collection<ModificationVariant>) :
    DataRegistrySyncPacket<ModificationVariant, ModificationVariantRegistrySyncPacket>(variants) {

    companion object {
        val ID = cobblemonResource("modification_variant_registry_sync")

        fun decode(buffer: RegistryFriendlyByteBuf): ModificationVariantRegistrySyncPacket {
            return ModificationVariantRegistrySyncPacket(emptyList()).apply { decodeBuffer(buffer) }
        }
    }

    override val id = ID

    override fun encodeEntry(buffer: RegistryFriendlyByteBuf, entry: ModificationVariant) = entry.encode(buffer)

    override fun decodeEntry(buffer: RegistryFriendlyByteBuf): ModificationVariant = ModificationVariant.decode(buffer)

    override fun synchronizeDecoded(entries: Collection<ModificationVariant>) {
        ModificationVariants.replaceEntries(entries)
    }
}
