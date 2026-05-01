package com.cobblemon.mod.common.integration.emi

import com.cobblemon.mod.common.integration.recipeviewer.RecipeViewerCategoryProvider
import dev.emi.emi.api.EmiEntrypoint
import dev.emi.emi.api.EmiPlugin
import dev.emi.emi.api.EmiRegistry

@EmiEntrypoint
class CobblemonEmiPlugin: EmiPlugin {
    val emiProviders = RecipeViewerCategoryProvider.categoriesProviders.map {
        CobblemonEmiProvider(it)
    }

    override fun register(registry: EmiRegistry) {
        emiProviders.forEach {
            it.register(registry)
        }
    }
}