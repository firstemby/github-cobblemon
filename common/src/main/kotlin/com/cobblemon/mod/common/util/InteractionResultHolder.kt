/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.util

import net.minecraft.world.InteractionResult
import net.minecraft.world.item.ItemStack

object InteractionResultHolder {
    fun success(stack: ItemStack): InteractionResult.Success {
        return InteractionResult.SUCCESS.heldItemTransformedTo(stack)
    }

    fun consume(stack: ItemStack): InteractionResult.Success {
        return InteractionResult.CONSUME.heldItemTransformedTo(stack)
    }

    fun pass(stack: ItemStack): InteractionResult {
        return InteractionResult.PASS
    }

    fun fail(stack: ItemStack): InteractionResult {
        return InteractionResult.FAIL
    }

    fun sidedSuccess(stack: ItemStack, isClientSide: Boolean): InteractionResult.Success {
        return (if (isClientSide) InteractionResult.SUCCESS else InteractionResult.SUCCESS_SERVER)
            .heldItemTransformedTo(stack)
    }

    operator fun invoke(result: InteractionResult, stack: ItemStack): InteractionResult {
        return if (result is InteractionResult.Success && result.wasItemInteraction()) {
            result.heldItemTransformedTo(stack)
        } else {
            result
        }
    }
}

