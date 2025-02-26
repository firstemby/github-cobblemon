package com.cobblemon.mod.common.client.render.models.blockbench.egg

import com.cobblemon.mod.common.client.render.models.blockbench.PosableModel
import com.cobblemon.mod.common.client.render.models.blockbench.animation.PoseAnimation
import com.cobblemon.mod.common.client.render.models.blockbench.pose.Bone
import com.cobblemon.mod.common.client.render.models.blockbench.quirk.ModelQuirk
import com.cobblemon.mod.common.entity.PoseType
import com.google.gson.annotations.SerializedName
import net.minecraft.client.model.geom.ModelPart

/**
 * A model for rendering eggs in nests.
 * This is based on the PosableModel framework.
 */
class EggModel(root: Bone) : PosableModel(root) {
    //@Transient
    //@SerializedName("dummy")
    //verride var isForLivingEntityRenderer = false
    @Transient
    @SerializedName("Something that isn't root part. Gson thinks they're the same as the root field and so field duplication. Stupid.")
    override val rootPart = (root as ModelPart).registerChildWithAllChildren(root.children.entries.first().key)

    var eggAnimations: Array<PoseAnimation> = emptyArray()
    var eggQuirks: Array<ModelQuirk<*>> = emptyArray()

    override fun registerPoses() {
        // Register specific egg poses and animations
        registerPose(
                poseType = PoseType.NONE,
                animations = eggAnimations,
                quirks = eggQuirks
        )
    }
}
