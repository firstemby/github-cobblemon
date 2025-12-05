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
object CobblemonArmorMaterials :
    PlatformRegistry<Registry<ArmorMaterial>, ResourceKey<Registry<ArmorMaterial>>, ArmorMaterial>() {

    override val registry: Registry<ArmorMaterial> = BuiltInRegistries.ARMOR_MATERIAL
    override val resourceKey: ResourceKey<Registry<ArmorMaterial>> = Registries.ARMOR_MATERIAL

    @JvmField
    val MALICIOUS_ARMOR = cosmeticMaterial("malicious_armor")
    @JvmField
    val HEAVY_DUTY_BOOTS = cosmeticMaterial("heavy_duty_boots")
    @JvmField
    val PROTECTIVE_PADS = cosmeticMaterial("protective_pads")
    @JvmField
    val ASSAULT_VEST = cosmeticMaterial("assault_vest")
    @JvmField
    val MYSTIC_WATER = cosmeticMaterial("mystic_water")
    @JvmField
    val BLACK_BELT = cosmeticMaterial("black_belt")

    private fun cosmeticMaterial(name: String): ArmorMaterial {
        val armorMaterial = ArmorMaterial(
            mapOf(
                ArmorItem.Type.HELMET to 0,
                ArmorItem.Type.CHESTPLATE to 0,
                ArmorItem.Type.LEGGINGS to 0,
                ArmorItem.Type.BOOTS to 0,
            ),
            0,
            SoundEvents.ARMOR_EQUIP_GENERIC,
            { Ingredient.EMPTY },
            listOf(ArmorMaterial.Layer(cobblemonResource(name), "", true)),
            0.0f,
            0.0f,
        )

        return create(name, armorMaterial)
    }
}