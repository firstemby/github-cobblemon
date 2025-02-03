package com.cobblemon.mod.common.client.render.models.blockbench.repository.appendage

import com.cobblemon.mod.common.client.render.layer.appendage.ModelAppendage
import com.cobblemon.mod.common.client.render.models.blockbench.PosableModel
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.endsWith
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import java.io.File
import java.nio.charset.StandardCharsets

object ModelAppendageModelRepository : VaryingModelRepository<PosableModel>() {
    override val poserClass: Class<PosableModel>
        get() = PosableModel::class.java
    override val title: String
        get() = "Model Appendage"
    override val type: String
        get() = "appendage"
    override val variationDirectories: List<String>
        get() = listOf("bedrock/$type/variations")
    override val poserDirectories: List<String>
        get() = listOf("bedrock/$type/posers")
    override val modelDirectories: List<String>
        get() = listOf("bedrock/$type/models")
    override val animationDirectories: List<String>
        get() = listOf("bedrock/$type/animations")
    val appendageDirectories: List<String>
        get() = listOf("bedrock/$type/appendages")
    val appendages = mutableMapOf<ResourceLocation, ModelAppendage>()
    override val fallback: ResourceLocation = cobblemonResource("substitute")
    override val isForLivingEntityRenderer: Boolean
        get() = true

    override fun registerInBuiltPosers() {}

    override fun reload(resourceManager: ResourceManager) {
        super.reload(resourceManager)
        this.appendages.clear()
        registerJsonAppendages(resourceManager)
    }

    private fun registerJsonAppendages(resourceManager: ResourceManager) {
        for (directory in appendageDirectories) {
            resourceManager
                .listResources(directory) { path -> path.endsWith(".json") }
                .forEach { (identifier, resource) ->
                    resource.open().use { stream ->
                        val json = String(stream.readAllBytes(), StandardCharsets.UTF_8)
                        val resolvedIdentifier = ResourceLocation.fromNamespaceAndPath(identifier.namespace, File(identifier.path).nameWithoutExtension)
                        appendages[resolvedIdentifier] = gson.fromJson(json, ModelAppendage::class.java)
                    }
                }
        }
    }

    fun getAppendagesForModelAndTarget(model: ResourceLocation, target: String): List<ModelAppendage> {
        return appendages.entries.filter { it.value.targetModel == model && it.value.targetLocator == target }.map { it.value }
    }
}