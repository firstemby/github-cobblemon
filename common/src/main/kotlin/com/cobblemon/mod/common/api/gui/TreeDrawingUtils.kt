package com.cobblemon.mod.common.api.gui

import kotlin.math.max

interface TreeNode<T> {
    val children: List<TreeNode<T>>
}

class TreeDrawingNode<T>(
    val value: T,
    val children: List<TreeDrawingNode<T>>,
) {
    var x = 0
    var y = 0
    var offset = 0
}

fun <T> createDrawingTree(treeNode: TreeNode<T>): TreeDrawingNode<T> {
    val root = setup(treeNode, 0, mutableListOf<Int>(), mutableListOf<Int>())
    addMods(root, 0)

    return root
}

@Suppress("UNCHECKED_CAST")
private fun <T> setup(
    treeNode: TreeNode<T>,
    depth: Int,
    nexts: MutableList<Int>,
    offset: MutableList<Int>,
): TreeDrawingNode<T> {
    val children = treeNode.children.map { child ->
        setup(child, depth + 1, nexts, offset)
    }

    val treeDrawingNode = TreeDrawingNode<T>(treeNode as T, children)
    treeDrawingNode.y = depth

    while (nexts.size <= depth) {
        nexts.add(0)
    }

    while (offset.size <= depth) {
        offset.add(0)
    }

    var place =
        if (children.isEmpty()) {
            nexts[depth]
        } else if (children.size == 1) {
//            children.first().x - 1
            children.first().x
        } else {
//            (children[0].x + children[1].x) / 2
            (children.first().x + children.last().x) / 2
        }

    offset[depth] = max(offset[depth], nexts[depth] - place)

    if (children.isEmpty()) {
        treeDrawingNode.x = place
    } else {
        treeDrawingNode.x = place + offset[depth]
    }

    nexts[depth] += 2
    treeDrawingNode.offset = offset[depth]

    return treeDrawingNode
}

private fun <T> addMods(
    treeNode: TreeDrawingNode<T>,
    modSum: Int,
) {
    treeNode.x += modSum
    val newModSum = modSum + treeNode.offset

    treeNode.children.forEach { child ->
        addMods(child, newModSum)
    }
}