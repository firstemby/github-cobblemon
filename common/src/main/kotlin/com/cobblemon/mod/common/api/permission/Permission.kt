/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.permission

import net.minecraft.resources.Identifier
import net.minecraft.server.permissions.PermissionLevel

interface Permission {

    val identifier: Identifier

    val literal: String

    val level: PermissionLevel

    fun levelCheck(): net.minecraft.server.permissions.Permission.HasCommandLevel =
        net.minecraft.server.permissions.Permission.HasCommandLevel(this.level)
}
