package com.cobblemon.mod.common.entity.bait

import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.projectile.ItemSupplier
import net.minecraft.world.entity.projectile.ThrowableItemProjectile
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.phys.BlockHitResult

class PokeBaitEntity(
    type: EntityType<out PokeBaitEntity>,
    level: Level,
    private var baitStack: ItemStack
) : ThrowableItemProjectile(type, level), ItemSupplier {
    override fun getDefaultItem(): Item? {
        TODO("Not yet implemented")
    }

    companion object {
        private val DATA_ITEM_STACK: EntityDataAccessor<ItemStack> = SynchedEntityData.defineId(PokeBaitEntity::class.java, EntityDataSerializers.ITEM_STACK)
    }

    /*private var lifespan = 200 // 200 ticks lifespan
    private var tickCounter = 0

    constructor(level: Level, owner: LivingEntity, stack: ItemStack) : this(
        EntityType.SNOWBALL, level, stack
    ) {
        this.owner = owner
        this.baitStack = stack
        setItem(stack)
    }

    override fun getDefaultItem(): Item {
        // Return a default item, if needed (can be a placeholder)
        return baitStack.item
    }

    override fun getItem(): ItemStack = baitStack

    override fun defineSynchedData(builder: SynchedEntityData.Builder) {
        super.defineSynchedData(builder)
        entityData.set(DATA_ITEM_STACK, ItemStack.EMPTY) // todo is this correct
    }

    override fun setItem(stack: ItemStack) {
        entityData.set(DATA_ITEM_STACK, stack.copy())
    }

    override fun tick() {
        super.tick()

        // Check if the entity has landed
        if (this.onGround()) {
            tickCounter++
            if (tickCounter >= lifespan) {
                discard() // Remove the entity after lifespan ends
            } else {
                // RandomTick logic placeholder
                if (!level().isClientSide && level().random.nextInt(100) < 5) { // 5% chance to trigger per tick
                    performRandomTickBehavior()
                }
            }
        }
    }

    private fun performRandomTickBehavior() {
        // todo Placeholder: Add your custom random tick logic here
        val pos = BlockPos(blockPosition().x, blockPosition().y, blockPosition().z)
        if (level() is ServerLevel) {
            // Example behavior: Placeholder for Pokémon spawning or other effects
        }
    }

    override fun addAdditionalSaveData(compound: CompoundTag) {
        super.addAdditionalSaveData(compound)

        // Create a separate CompoundTag to save the item
        val itemTag = CompoundTag()
        level()?.registryAccess()?.let { registryAccess ->
            item.save(itemTag, registryAccess) // Pass the HolderLookup.Provider for serialization
        }
        compound.put("Item", itemTag)
        compound.putInt("Lifespan", lifespan)
        compound.putInt("TickCounter", tickCounter)
    }



    override fun readAdditionalSaveData(compound: CompoundTag) {
        super.readAdditionalSaveData(compound)

        if (compound.contains("Item", 10)) { // Ensure "Item" is a compound tag
            val itemTag = compound.getCompound("Item")
            level()?.registryAccess()?.let { registryAccess ->
                setItem(ItemStack.parseOptional(registryAccess, itemTag)) // Deserialize using the HolderLookup.Provider
            }
        }
        lifespan = compound.getInt("Lifespan")
        tickCounter = compound.getInt("TickCounter")
    }


    override fun onHitBlock(result: BlockHitResult) {
        super.onHitBlock(result)
        this.setNoGravity(true) // stick to surface
    }

    override fun shoot(x: Double, y: Double, z: Double, velocity: Float, inaccuracy: Float) {
        super.shoot(x, y * 0.5, z, velocity * 0.5f, inaccuracy) // Adjust for low arc
    }*/
}
