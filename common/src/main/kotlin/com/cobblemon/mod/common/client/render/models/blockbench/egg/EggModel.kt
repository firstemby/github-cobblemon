package com.cobblemon.mod.common.client.render.models.blockbench.egg

import com.cobblemon.mod.common.client.render.models.blockbench.PosableModel
import com.cobblemon.mod.common.client.render.models.blockbench.animation.PoseAnimation
import com.cobblemon.mod.common.client.render.models.blockbench.pose.Bone
import com.cobblemon.mod.common.client.render.models.blockbench.quirk.ModelQuirk
import com.cobblemon.mod.common.entity.PoseType

/**
 * A model for rendering eggs in nests.
 * This is based on the PosableModel framework.
 */
class EggModel(root: Bone) : PosableModel(root) {
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
