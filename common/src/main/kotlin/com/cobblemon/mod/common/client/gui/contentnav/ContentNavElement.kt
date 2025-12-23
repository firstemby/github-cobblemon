package com.cobblemon.mod.common.client.gui.contentnav

import net.minecraft.core.Direction

abstract class ContentNavElement<E: ContentNavElement<E>> {
    var northNav: ContentNavElement<*>? = null
    var southNav: ContentNavElement<*>? = null
    var eastNav: ContentNavElement<*>? = null
    var westNav: ContentNavElement<*>? = null

    var onUseOverride: ((E) -> Unit)? = null
    var onSpecial1Lamba: ((E) -> Unit)? = null

    abstract fun onUse()

    open fun onSelect() {}
    open fun onNavigation(direction: Direction): Boolean = false
}