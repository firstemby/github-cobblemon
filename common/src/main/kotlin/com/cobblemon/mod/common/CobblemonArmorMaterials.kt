package com.cobblemon.mod.common

import com.cobblemon.mod.common.platform.PlatformRegistry
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.item.ArmorItem
import net.minecraft.world.item.ArmorMaterial
import net.minecraft.world.item.Items
import net.minecraft.world.item.crafting.Ingredient

@Suppress("unused")
object CobblemonArmorMaterials : PlatformRegistry<Registry<ArmorMaterial>, ResourceKey<Registry<ArmorMaterial>>, ArmorMaterial>() {

    override val registry: Registry<ArmorMaterial> = BuiltInRegistries.ARMOR_MATERIAL
    override val resourceKey: ResourceKey<Registry<ArmorMaterial>> = Registries.ARMOR_MATERIAL

    @JvmField
    val MALICIOUS_ARMOR = create(
        "malicious_armor", ArmorMaterial(
            mapOf(
                ArmorItem.Type.HELMET to 3,
                ArmorItem.Type.CHESTPLATE to 8,
                ArmorItem.Type.LEGGINGS to 6,
                ArmorItem.Type.BOOTS to 3
            ),
            25,
            SoundEvents.ARMOR_EQUIP_IRON,
            { Ingredient.of(Items.NETHERITE_INGOT) },
            listOf(ArmorMaterial.Layer(cobblemonResource("malicious_armor"), "", true)),
            3.0f,
            0.1f,
        )
    )
}