/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render

import com.cobblemon.mod.common.api.molang.ExpressionLike
import com.cobblemon.mod.common.client.render.models.blockbench.PosableModel
import com.cobblemon.mod.common.client.render.models.blockbench.PosableState
import com.cobblemon.mod.common.client.render.models.blockbench.bedrock.animation.BedrockActiveAnimation
import com.cobblemon.mod.common.client.render.models.blockbench.bedrock.animation.BedrockPoseAnimation
import com.cobblemon.mod.common.client.render.models.blockbench.bedrock.animation.TextureInterpolationType
import com.cobblemon.mod.common.client.render.models.blockbench.pose.Bone
import com.cobblemon.mod.common.client.render.models.blockbench.repository.RenderContext
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository
import com.cobblemon.mod.common.util.adapters.*
import com.cobblemon.mod.common.util.asIdentifierDefaultingNamespace
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.resolveBoolean
import com.cobblemon.mod.common.util.toRGBA
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.RenderStateShard
import net.minecraft.client.renderer.texture.DynamicTexture
import kotlin.math.floor
import net.minecraft.resources.ResourceLocation
import org.joml.Vector2d
import org.joml.Vector3f
import org.joml.Vector4f

/**
 * All the information required for rendering a Pokémon/Poké Ball/NPC with aspects.
 *
 * @author Hiroku
 * @since May 14th, 2022
 */
class VaryingRenderableResolver(
    val name: ResourceLocation,
    val variations: MutableList<ModelAssetVariation>
) {
    lateinit var repository: VaryingModelRepository
    val posers = mutableMapOf<Pair<ResourceLocation, ResourceLocation>, PosableModel>()
    val models = mutableMapOf<ResourceLocation, Bone>()

    fun getResolvedPoser(state: PosableState): ResourceLocation {
        return getVariationValue(state) { poser }
            ?: throw IllegalStateException("Unable to find a poser for $name with aspects ${state.currentAspects.joinToString()}. This shouldn't be possible if you've defined the fallback variation.")
    }

    fun getResolvedModel(state: PosableState): ResourceLocation {
        return getVariationValue(state) { model }
            ?: throw IllegalStateException("Unable to find a model for $name with aspects ${state.currentAspects.joinToString()}. This shouldn't be possible if you've defined the fallback variation.")
    }

    fun getResolvedTexture(state: PosableState): ResourceLocation {
        return getVariationValue(state) { texture }?.getTexture(state)
            ?: throw IllegalStateException("Unable to find a texture for $name with aspects ${state.currentAspects.joinToString()}. This shouldn't be possible if you've defined the fallback variation.")
    }

    fun getSprite(state: PosableState, type: SpriteType): ResourceLocation? {
        return getVariationValue(state) { sprites }?.get(type)
    }

    private fun <T> getVariationValue(state: PosableState, selector: ModelAssetVariation.() -> T?): T? {
        return variations.lastOrNull { it.fits(state) && selector(it) != null }?.let(selector)
    }

    fun getResolvedLayers(state: PosableState): Iterable<ModelLayer> {
        val layerMaps = mutableMapOf<String, ModelLayer>()
        for (variation in variations) {
            val layers = variation.layers
            if (layers != null && variation.fits(state)) {
                for (layer in layers) {
                    layerMaps[layer.name] = layer
                }
            }
        }
        return layerMaps.values.filter(ModelLayer::enabled)
    }

    fun getAllModels(): Set<ResourceLocation> {
        val models = mutableSetOf<ResourceLocation>()
        for (variation in variations) {
            if (variation.model != null) {
                models.add(variation.model)
            }
        }
        return models
    }

    companion object {
        val GSON = GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(ResourceLocation::class.java, IdentifierAdapter)
            .registerTypeAdapter(Vector3f::class.java, Vector3fAdapter)
            .registerTypeAdapter(Vector4f::class.java, Vector4fAdapter)
            .registerTypeAdapter(ModelTextureSupplier::class.java, ModelTextureSupplierAdapter)
            .registerTypeAdapter(ExpressionLike::class.java, ExpressionLikeAdapter)
            .registerTypeAdapter(SpriteType::class.java, SpriteTypeAdapter)
            .disableHtmlEscaping()
            .setLenient()
            .create()
    }

    fun initialize(repository: VaryingModelRepository) {
        this.repository = repository
        posers.clear()
        getAllModels().forEach { identifier ->
            try {
                models[identifier] = repository.texturedModels[identifier]!!
            } catch (e: Exception) {
                throw IllegalStateException("Unable to load model $identifier for $name", e)
            }
        }
    }

    fun getPoser(state: PosableState): PosableModel {
        val poserName = getResolvedPoser(state)
        val poserSupplier = repository.posers[poserName] ?: throw IllegalStateException("No poser found for name: $poserName for $name")
        val modelName = getResolvedModel(state)
        val existingEntityModel = posers[poserName to modelName]
        return if (existingEntityModel != null) {
            existingEntityModel
        } else {
            val model = models[modelName]!!
            val entityModel = poserSupplier(model)
            entityModel.initializeLocatorAccess()
            entityModel.registerPoses()
            posers[poserName to modelName] = entityModel
            entityModel
        }
    }

    fun getTexture(state: PosableState): ResourceLocation {
        repository.posers[getResolvedPoser(state)] ?: throw IllegalStateException("No poser for $name")
        return getResolvedTexture(state)
    }

    fun getLayers(state: PosableState): Iterable<ModelLayer> {
        repository.posers[getResolvedPoser(state)] ?: throw IllegalStateException("No poser for $name")
        return getResolvedLayers(state)
    }
}

/**
 * A set of variations. This is essentially a prioritized list of [ModelAssetVariation]s, with
 * an [order] property to control the priority of this set compared to other sets.
 *
 * @author Hiroku
 * @since December 4th, 2022
 */
class ModelVariationSet(
    @SerializedName("name", alternate = ["species", "pokeball"])
    val name: ResourceLocation = cobblemonResource("thing"),
    val order: Int = 0,
    val variations: MutableList<ModelAssetVariation> = mutableListOf()
)


/**
 * A variation to the base set, which can overwrite the poser, model, texture, or any combination of the above.
 * It contains a set of aspects that must ALL be present on a renderable for this variation to be considered.
 * If a later variation also matches, but provides different properties, both this and the other variation will
 * be used for their respective non-null properties.
 *
 * @author Hiroku
 * @since May 14th, 2022
 */
class ModelAssetVariation(
    val aspects: MutableSet<String> = mutableSetOf(),
    val condition: ExpressionLike? = null,
    val poser: ResourceLocation? = null,
    val model: ResourceLocation? = null,
    val texture: ModelTextureSupplier? = null,
    val layers: List<ModelLayer>? = null,
    val sprites: Map<SpriteType, ResourceLocation>? = null
) {
    fun fits(state: PosableState): Boolean {
        return aspects.all { it in state.currentAspects } && (condition == null || state.runtime.resolveBoolean(condition))
    }
}

/** Needed a custom one, so I can make it take in a dynamic texture instead of resource location */
class DynamicStateShard(texture: DynamicTexture) : RenderStateShard.EmptyTextureStateShard(Runnable {
    RenderSystem.setShaderTexture(0, texture.id)
}, Runnable {
    texture.close() // Cleanup
})

/**
 * Given the animation seconds, returns a texture to use. Only implemented
 * by [StaticModelTextureSupplier] and [AnimatedModelTextureSupplier].
 *
 * @author Hiroku
 * @since February 6th, 2023
 */
interface ModelTextureSupplier {
    operator fun invoke(state: PosableState): RenderStateShard.EmptyTextureStateShard
    fun getTexture(state: PosableState): ResourceLocation
}

class StaticModelTextureSupplier(val texture: ResourceLocation): ModelTextureSupplier {
    override fun invoke(state: PosableState): RenderStateShard.EmptyTextureStateShard {
        return RenderStateShard.TextureStateShard(texture, false, false)
    }

    override fun getTexture(state: PosableState): ResourceLocation {
        return texture
    }
}

class AnimatedModelTextureSupplier(
    val loop: Boolean,
    val fps: Float,
    val frames: List<ResourceLocation>,
    val interpolation: Boolean
): ModelTextureSupplier {
    override fun invoke(state: PosableState): RenderStateShard.EmptyTextureStateShard {
        var frameIndex = state.animationSeconds * fps
        if (frameIndex > frames.size - 1.0f && !loop) frameIndex = frames.size.toFloat() - 1.0f
        val i = frameIndex % 1.0

        if (interpolation && i != 0.0) {
            val o = floor(frameIndex).toInt() % frames.size
            val s = if (o == frames.size - 1) 0 else o + 1

            return DynamicStateShard(getInterpolatedTexture(frames[o], frames[s], i))
        }
        else {
            val frame = floor(frameIndex).toInt() % frames.size
            return RenderStateShard.TextureStateShard(frames[frame], false, false)
        }
    }

    override fun getTexture(state: PosableState): ResourceLocation {
        val frameIndex = floor(state.animationSeconds * fps).toInt()
        return frames[frameIndex]
    }
}

class KeyframableTextureSupplier(val textures: MutableList<ResourceLocation>) : ModelTextureSupplier {
    var layerName = ""

    override fun invoke(state: PosableState): RenderStateShard.EmptyTextureStateShard {
        var index = 0
        var animation = if ((state.primaryAnimation?.animation as? BedrockActiveAnimation)?.animation?.cobblemonTextures?.get(layerName) != null) {
            (state.primaryAnimation?.animation as? BedrockActiveAnimation)?.animation
        } else null
        var startTime = 0.0F

        val pose = state.currentPose?.let(state.currentModel!!.poses::get)

        pose?.animations?.filterIsInstance<BedrockPoseAnimation>()?.filter{ state.shouldIdleRun(it, 0F) }?.forEach {
            if (it.animation.cobblemonTextures[layerName] != null) {
                animation = it.animation
            }
        }

        if (state.primaryAnimation == null && state.currentModel!!.context.request(RenderContext.DO_QUIRKS) != false) {
            pose?.quirks?.forEach { quirk ->
                val quirkData = quirk.getOrCreateData(state)
                quirkData.animations.filterIsInstance<BedrockActiveAnimation>().forEach {
                    if (it.animation.cobblemonTextures[layerName] != null) {
                        startTime = it.startedSeconds
                        animation = it.animation
                    }
                }
            }
        }

        //Here we should either have null or the most prioritized keyframe list
        if (animation != null) {
            val keyframeMap = animation!!.cobblemonTextures[layerName]!!
            var adjustedTime = (state.animationSeconds - startTime)
            if (animation!!.shouldLoop) adjustedTime %= animation!!.animationLength.toFloat()
            val keyIndex = keyframeMap.keys.indexOfLast { it <= adjustedTime }
            val keyframe1 = when(keyIndex) {
                -1 -> keyframeMap.firstEntry()
                else -> keyframeMap.entries.toMutableList()[keyIndex]
            }
            val keyframe2 = when {
                keyIndex + 1 >= keyframeMap.size -> keyframeMap.lastEntry()
                else -> keyframeMap.entries.toMutableList()[keyIndex+1]
            }

            val i = (adjustedTime - keyframe1.key.toFloat()) / (keyframe2.key - keyframe1.key)
            if (keyframe1.value.textureSlot != keyframe2.value.textureSlot && keyframe1.value.interpolationType == TextureInterpolationType.LINEAR) {
                return DynamicStateShard(getInterpolatedTexture(textures[keyframe1.value.textureSlot], textures[keyframe2.value.textureSlot], i))
            } else if (keyframe1.value.textureSlot != keyframe2.value.textureSlot && keyframe1.value.interpolationType == TextureInterpolationType.BEZIER) {
                val clampedCubicValues = keyframe1.value.cubicLerpFunctionValues.toMutableList()
                for (value in 0..<clampedCubicValues.size) {
                    clampedCubicValues[value] = Math.clamp(clampedCubicValues[value],0.0,1.0)
                }

                val points = mutableListOf<Vector2d>()
                for (o in 0..200) {
                    val t = o/200.0
                    val omt = (1.0-t)

                    val point = Vector2d()
                    point.x = 3.0 * omt * omt * t * clampedCubicValues[0] + 3.0 * omt * t * t * clampedCubicValues[2] + t * t * t
                    point.y = 3.0 * omt * omt * t * clampedCubicValues[1] + 3.0 * omt * t * t * clampedCubicValues[3] + t * t * t

                    points.add(point)
                }

                var beforePoint = Vector2d(0.0,0.0)
                var afterPoint = Vector2d(1.0, 1.0)

                for (point in points) {
                    if (point.x > beforePoint.x && point.x < i) {
                        beforePoint = point
                    }
                    if (point.x < afterPoint.x && point.x > i) {
                        afterPoint = point
                    }
                }

                val cubicInterp = beforePoint.y + (afterPoint.y - beforePoint.y) * ((i - beforePoint.x) / (afterPoint.x - beforePoint.x))
                return DynamicStateShard(getInterpolatedTexture(textures[keyframe1.value.textureSlot], textures[keyframe2.value.textureSlot], cubicInterp))
            } else {
                index = keyframe1.value.textureSlot
            }
        }

        return RenderStateShard.TextureStateShard(textures[index], false, false)
    }

    override fun getTexture(state: PosableState): ResourceLocation {
        return textures[0]
    }
}

class VariableModelTextureSupplier : ModelTextureSupplier {
    override fun invoke(state: PosableState): RenderStateShard.EmptyTextureStateShard {
        return RenderStateShard.TextureStateShard(getTexture(state), false, false)
    }

    override fun getTexture(state: PosableState): ResourceLocation {
        return state.runtime.environment.variable.map["texture"]?.asString()?.asIdentifierDefaultingNamespace()
            ?: cobblemonResource("textures/npcs/default.png")
    }
}

class ModelLayer {
    val name: String = ""
    val enabled: Boolean = true
    val tint: Vector4f = Vector4f(1F, 1F, 1F, 1F)
    val texture: ModelTextureSupplier? = null
    val emissive: Boolean = false
    val translucent: Boolean = false
    val translucent_cull: Boolean = false
}

enum class SpriteType {
    PORTRAIT,
    PROFILE
}

private fun getInterpolatedTexture(resource1: ResourceLocation, resource2: ResourceLocation, i: Double): DynamicTexture {
    val resourceManager = Minecraft.getInstance().resourceManager

    val texture1 = NativeImage.read(resourceManager.getResourceOrThrow(resource1).open())
    val texture2 = NativeImage.read(resourceManager.getResourceOrThrow(resource2).open())

    for (x in 0..<texture1.width) {
        for (y in 0..<texture1.height) {
            val color1 = texture1.getPixelRGBA(x,y).toRGBA()
            val color2 = texture2.getPixelRGBA(x,y).toRGBA()

            var newRed : Int
            var newGreen : Int
            var newBlue : Int

            //Usually full transparent pixel is black, so this makes it copy the other texture colours to look better, however considering if we should allow to disable this feature
            if (color1.w == 0F && color2.w == 1F) {
                newRed = floor(color2.x * 255).toInt()
                newGreen = floor(color2.y * 255).toInt()
                newBlue = floor(color2.z * 255).toInt()
            } else if (color1.w == 1F && color2.w == 0F) {
                newRed = floor(color1.x * 255).toInt()
                newGreen = floor(color1.y * 255).toInt()
                newBlue = floor(color1.z * 255).toInt()
            } else {
                newRed = floor((color1.x + (color2.x - color1.x) * i) * 255).toInt()
                newGreen = floor((color1.y + (color2.y - color1.y) * i) * 255).toInt()
                newBlue = floor((color1.z + (color2.z - color1.z) * i) * 255).toInt()
            }

            val newAlpha = floor((color1.w + (color2.w - color1.w) * i) * 255).toInt()

            val finalColor = (newAlpha shl 24) or (newRed shl 16) or (newGreen shl 8) or newBlue

            texture1.setPixelRGBA(x,y,finalColor) //We aren't using texture 1 anymore so it's easier to override it
        }
    }
    val newTexture = DynamicTexture(texture1)
    newTexture.setFilter(false, false)
    return newTexture
}