/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.block.entity

import com.cobblemon.mod.common.CobblemonBlockEntities
import com.cobblemon.mod.common.CobblemonRecipeTypes
import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.api.cooking.getColourMixFromSeasonings
import com.cobblemon.mod.common.block.PotComponent
import com.cobblemon.mod.common.block.campfirepot.CookingPotMenu
import com.cobblemon.mod.common.client.particle.BedrockParticleOptionsRepository
import com.cobblemon.mod.common.client.particle.ParticleStorm
import com.cobblemon.mod.common.client.render.MatrixWrapper
import com.cobblemon.mod.common.client.sound.BlockEntitySoundTracker
import com.cobblemon.mod.common.client.sound.instances.CancellableSoundInstance
import com.cobblemon.mod.common.item.crafting.CookingPotRecipeBase
import com.cobblemon.mod.common.util.playSoundServer
import com.mojang.blaze3d.vertex.PoseStack
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import java.util.Optional
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.HolderLookup
import net.minecraft.core.NonNullList
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtOps
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.FastColor
import net.minecraft.world.ContainerHelper
import net.minecraft.world.WorldlyContainer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.StackedContents
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerData
import net.minecraft.world.inventory.CraftingContainer
import net.minecraft.world.inventory.RecipeCraftingHolder
import net.minecraft.world.inventory.StackedContentsCompatible
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.crafting.CraftingInput
import net.minecraft.world.item.crafting.RecipeHolder
import net.minecraft.world.item.crafting.RecipeManager
import net.minecraft.world.item.crafting.RecipeType
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import org.joml.Vector4f
import kotlin.compareTo

class CampfireBlockEntity(pos: BlockPos, state: BlockState) : BaseContainerBlockEntity(
    CobblemonBlockEntities.CAMPFIRE,
    pos,
    state
), WorldlyContainer, RecipeCraftingHolder, StackedContentsCompatible, CraftingContainer {

    companion object {
        const val RESULT_SLOT = 0
        val CRAFTING_GRID_SLOTS = 1..9
        val SEASONING_SLOTS = 10..12
        const val PREVIEW_ITEM_SLOT = 13
        const val ITEMS_SIZE = 14

        val PLAYER_INVENTORY_SLOTS = 14..40
        val PLAYER_HOTBAR_SLOTS = 41..49

        const val CRAFTING_GRID_WIDTH = 3
        const val PLAYER_INVENTORY_WIDTH = 9

        const val COOKING_TOTAL_TIME = 200
        const val COOKING_PROGRESS_PER_TICK = 2

        const val COOKING_PROGRESS_INDEX = 0
        const val COOKING_PROGRESS_TOTAL_TIME_INDEX = 1
        const val IS_LID_OPEN_INDEX = 2

        const val BASE_BROTH_COLOR = 0xFFFFFFFF.toInt()

        fun clientTick(level: Level, pos: BlockPos, state: BlockState, campfireBlockEntity: CampfireBlockEntity) {
            if (!level.isClientSide) return

            val isLit = campfireBlockEntity.dataAccess.get(COOKING_PROGRESS_INDEX) > 0
            val isSoundActive = BlockEntitySoundTracker.isActive(pos, campfireBlockEntity.runningSound.location)

            if (isLit && !isSoundActive) {
                BlockEntitySoundTracker.play(pos, CancellableSoundInstance(campfireBlockEntity.runningSound, pos, true, 0.8f, 1.0f))
            } else if (!isLit && isSoundActive) {
                BlockEntitySoundTracker.stop(pos, campfireBlockEntity.runningSound.location)
            }

            campfireBlockEntity.brothColor =
                getColourMixFromSeasonings(campfireBlockEntity.getSeasonings())
                ?: BASE_BROTH_COLOR

            campfireBlockEntity.bubbleColor =
                getColourMixFromSeasonings(campfireBlockEntity.getSeasonings(), true)
                    ?: BASE_BROTH_COLOR

            if (campfireBlockEntity.particleCooldown > 0) {
                campfireBlockEntity.particleCooldown--
            } else {
                 if (isLit) {
                    val position = Vec3(pos.x + 0.5, pos.y + 0.5375, pos.z + 0.5)

                    campfireBlockEntity.particleEntityHandler(
                        position = position,
                        level = level,
                        particle = ResourceLocation("cobblemon", if (campfireBlockEntity.getSeasonings().isEmpty()) "broth_bubbles_basic" else "broth_bubbles")
                    )
                 }

                campfireBlockEntity.particleCooldown = 20
            }
        }

        fun serverTick(level: Level, pos: BlockPos, state: BlockState, campfireBlockEntity: CampfireBlockEntity) {
            if (level.isClientSide) return

            val craftingInput = CraftingInput.of(3, 3, campfireBlockEntity.items.subList(1, 10))

            fun <T : CookingPotRecipeBase> fetchRecipe(
                recipeType: RecipeType<T>
            ): Optional<RecipeHolder<CookingPotRecipeBase>> {
                val optional = level.recipeManager.getRecipeFor(recipeType, craftingInput, level)
                @Suppress("UNCHECKED_CAST")
                return optional.map { it as RecipeHolder<CookingPotRecipeBase> }
            }

            // Check for both COOKING_POT_COOKING and COOKING_POT_SHAPELESS recipes
            val optionalRecipe = fetchRecipe(CobblemonRecipeTypes.COOKING_POT_COOKING)
                .orElseGet { fetchRecipe(CobblemonRecipeTypes.COOKING_POT_SHAPELESS).orElse(null) }

            if (optionalRecipe == null) {
                campfireBlockEntity.cookingProgress = 0
                campfireBlockEntity.setItem(PREVIEW_ITEM_SLOT, ItemStack.EMPTY)
                return
            }

            val cookingPotRecipe = optionalRecipe
            val recipe = cookingPotRecipe.value()
            val cookedItem = recipe.assemble(craftingInput, level.registryAccess())
            val resultSlotItem = campfireBlockEntity.getItem(0)

            recipe.applySeasoning(cookedItem, campfireBlockEntity.getSeasonings())

            campfireBlockEntity.setItem(PREVIEW_ITEM_SLOT, cookedItem)

            if (campfireBlockEntity.isLidOpen) {
                campfireBlockEntity.cookingProgress = 0
                return
            }

            if (!resultSlotItem.isEmpty) {
                if (!ItemStack.isSameItemSameComponents(resultSlotItem, cookedItem) || resultSlotItem.count >= resultSlotItem.maxStackSize) {
                    campfireBlockEntity.cookingProgress = 0
                    return
                }
            }

            campfireBlockEntity.cookingProgress += COOKING_PROGRESS_PER_TICK
            if (campfireBlockEntity.cookingProgress == campfireBlockEntity.cookingTotalTime) {
                campfireBlockEntity.cookingProgress = 0

                if (!cookedItem.isEmpty) {
                    (campfireBlockEntity as RecipeCraftingHolder).recipeUsed = cookingPotRecipe

                    if (resultSlotItem.isEmpty) {
                        campfireBlockEntity.setItem(0, cookedItem)
                    } else {
                        resultSlotItem.grow(cookedItem.count)
                    }

                    campfireBlockEntity.consumeCraftingIngredients()

                    level.playSoundServer(
                        position = pos.bottomCenter,
                        sound = CobblemonSounds.CAMPFIRE_POT_CRAFT,
                    )

                    setChanged(level, pos, state);
                }
            }
        }
    }

    private val runningSound = CobblemonSounds.CAMPFIRE_POT_COOK
    private var cookingProgress : Int = 0
    private var cookingTotalTime : Int = COOKING_TOTAL_TIME
    private var isLidOpen : Boolean = true
    private var items : NonNullList<ItemStack> = NonNullList.withSize(ITEMS_SIZE, ItemStack.EMPTY)
    private val recipesUsed: Object2IntOpenHashMap<ResourceLocation> = Object2IntOpenHashMap()
    private val quickCheck: RecipeManager.CachedCheck<CraftingInput, *> = RecipeManager.createCheck(CobblemonRecipeTypes.COOKING_POT_COOKING)
    private var potComponent: PotComponent? = null
    private var particleCooldown: Int = 0
    var brothColor: Int = BASE_BROTH_COLOR
    var bubbleColor: Int = 0xFFFFFF

    var dataAccess : ContainerData = object : ContainerData {
        override fun get(index: Int): Int {
            return when (index) {
                COOKING_PROGRESS_INDEX -> this@CampfireBlockEntity.cookingProgress
                COOKING_PROGRESS_TOTAL_TIME_INDEX -> this@CampfireBlockEntity.cookingTotalTime
                IS_LID_OPEN_INDEX -> if (this@CampfireBlockEntity.isLidOpen) 1 else 0
                else -> 0
            }
        }

        override fun set(index: Int, value: Int) {
            when (index) {
                COOKING_PROGRESS_INDEX -> this@CampfireBlockEntity.cookingProgress = value
                COOKING_PROGRESS_TOTAL_TIME_INDEX -> this@CampfireBlockEntity.cookingTotalTime = value
                IS_LID_OPEN_INDEX -> this@CampfireBlockEntity.isLidOpen = value == 1
            }
        }

        override fun getCount(): Int {
            return 3
        }
    }

    fun particleEntityHandler(position: Vec3, level: Level, particle: ResourceLocation): ParticleStorm {
        val wrapper = MatrixWrapper()
        val matrix = PoseStack()
        wrapper.updateMatrix(matrix.last().pose())
        wrapper.updatePosition(position)
        val effect = BedrockParticleOptionsRepository.getEffect(particle) ?: throw IllegalStateException("Particle with resource location $particle not found")

        val particleStorm = ParticleStorm(
            effect,
            wrapper,
            wrapper,
            level as ClientLevel,
             sourceAlive = { cookingProgress > 0 },
             sourceVisible = { cookingProgress > 0 },
            getParticleColor = {
                val red = FastColor.ARGB32.red(bubbleColor) / 255F
                val green = FastColor.ARGB32.green(bubbleColor) / 255F
                val blue = FastColor.ARGB32.blue(bubbleColor) / 255F
                val alpha = FastColor.ARGB32.alpha(bubbleColor) / 255F

                Vector4f(red, green, blue, alpha)
            }
        ).also {
            it.spawn()
        }

        return particleStorm
    }

    fun consumeCraftingIngredients() {
        for (i in CRAFTING_GRID_SLOTS.first..SEASONING_SLOTS.last) {
            val itemInSlot = getItem(i)
            if (!itemInSlot.isEmpty) {
                when (itemInSlot.item) {
                    Items.LAVA_BUCKET, Items.WATER_BUCKET, Items.MILK_BUCKET -> {
                        // Replace with empty bucket
                        setItem(i, ItemStack(Items.BUCKET))
                    }
                    Items.HONEY_BOTTLE -> {
                        // TODO: Currently eats the empty bottles until the honey bottle stack is empty, replace with better system later.
                        itemInSlot.shrink(1)
                        if (itemInSlot.count <= 0) {
                            setItem(i, ItemStack.EMPTY)
                        }
                    }
                    else -> {
                        // Decrease the stack size by 1
                        itemInSlot.shrink(1)
                        if (itemInSlot.count <= 0) {
                            setItem(i, ItemStack.EMPTY) // Clear the slot if empty
                        }
                    }
                }
            }
        }
    }

    fun getSeasonings(): List<ItemStack> =
        items.subList(SEASONING_SLOTS.first, SEASONING_SLOTS.last + 1)
            .filterNotNull()
            .filter { !it.isEmpty }

    fun getIngredients(): List<ItemStack> =
        items.subList(CRAFTING_GRID_SLOTS.first, CRAFTING_GRID_SLOTS.last + 1)
            .filterNotNull()
            .filter { !it.isEmpty }

    override fun getDefaultName(): Component {
        return Component.translatable("cobblemon.container.campfire_pot")
    }

    override fun getWidth() = CRAFTING_GRID_WIDTH
    override fun getHeight() = CRAFTING_GRID_WIDTH

    override fun getItems(): NonNullList<ItemStack> {
        level?.let { onItemUpdate(it) }
        return this.items
    }

    override fun setItems(items: NonNullList<ItemStack>) {
        this.items.clear()
        this.items.addAll(items)
        level?.let { onItemUpdate(it) }
    }

    override fun createMenu(
        containerId: Int,
        inventory: Inventory
    ): AbstractContainerMenu {
        return CookingPotMenu(containerId, inventory, this, this.dataAccess)
    }

    override fun getContainerSize() = this.items.size
    override fun getSlotsForFace(side: Direction) = intArrayOf(0, 1, 2)
    override fun canPlaceItemThroughFace(index: Int, itemStack: ItemStack, direction: Direction?) = false
    override fun canTakeItemThroughFace(index: Int, stack: ItemStack, direction: Direction) = false

    override fun setRecipeUsed(recipe: RecipeHolder<*>?) {
        if (recipe != null) {
            val resourceLocation = recipe.id()
            this.recipesUsed.addTo(resourceLocation, 1)
        }
    }

    override fun getRecipeUsed() = null
    override fun fillStackedContents(contents: StackedContents) {
        for (itemStack in this.items) {
            contents.accountSimpleStack(itemStack);
        }
    }

    override fun getItem(slot: Int): ItemStack {
        return if (slot in 0 until items.size) items[slot] else ItemStack.EMPTY
    }

    fun getPotItem(): ItemStack? {
        return potComponent?.potItem
    }

    fun setPotItem(stack: ItemStack?) {
        this.potComponent = PotComponent(stack ?: ItemStack.EMPTY) // Ensure a non-null value is passed
        setChanged()
        level?.sendBlockUpdated(blockPos, blockState, blockState, Block.UPDATE_CLIENTS)
    }

    override fun saveAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.saveAdditional(tag, registries)

        tag.putInt("CookingProgress", this.cookingProgress)
        tag.putBoolean("IsLidOpen", this.isLidOpen)

        ContainerHelper.saveAllItems(tag, this.items, registries)
        potComponent?.let { component ->
            PotComponent.CODEC.encodeStart(NbtOps.INSTANCE, component)
                .result()
                ?.ifPresent { encoded -> tag.put("PotComponent", encoded) }
        }
    }

    override fun loadAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.loadAdditional(tag, registries)

        this.cookingProgress = tag.getInt("CookingProgress")
        this.isLidOpen = tag.getBoolean("IsLidOpen")

        // Minecraft doesn't save empty item stacks to the items tag, so we have to manually clear them
        // otherwise they would never clear and seasonings would always render wrongly

        clearContent()
        ContainerHelper.loadAllItems(tag, this.items, registries)

        if (tag.contains("PotComponent")) {
            val component = PotComponent.CODEC.parse(NbtOps.INSTANCE, tag.getCompound("PotComponent"))
                .result()
                ?.orElse(null)
            potComponent = component
        }
    }

    override fun getUpdatePacket(): Packet<ClientGamePacketListener>? {
        return ClientboundBlockEntityDataPacket.create(this)
    }

    override fun getUpdateTag(registryLookup: HolderLookup.Provider): CompoundTag {
        return saveWithoutMetadata(registryLookup)
    }

    private fun onItemUpdate(level: Level) {
        val oldState = level.getBlockState(blockPos)
        level.sendBlockUpdated(blockPos, oldState, level.getBlockState(blockPos), Block.UPDATE_ALL)
        level.updateNeighbourForOutputSignal(blockPos, level.getBlockState(blockPos).block)
        setChanged()
        level.sendBlockUpdated(blockPos, oldState, level.getBlockState(blockPos), Block.UPDATE_ALL)
    }

    override fun setRemoved() {
        cookingProgress = 0
        super.setRemoved()

        if (level?.isClientSide == true) {
            BlockEntitySoundTracker.stop(blockPos, runningSound.location)
        }
    }
}