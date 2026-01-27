package com.mrcrayfish.vehicle.integration.jei;

import com.mrcrayfish.vehicle.recipe.RecipeBicycleLock;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.IModRegistry;
import mezz.jei.api.JEIPlugin;
import mezz.jei.api.recipe.VanillaRecipeCategoryUid;

import javax.annotation.Nonnull;

/**
 * JEI Integration Plugin
 */
@JEIPlugin
public class VehicleJEIPlugin implements IModPlugin
{
    @Override
    public void register(@Nonnull IModRegistry registry)
    {
        // Register bicycle lock recipe wrapper
        // With isDynamic() = false, JEI should not auto-detect it, so wrapper is the only display
        registry.handleRecipes(RecipeBicycleLock.class, BicycleLockRecipeWrapper::new, VanillaRecipeCategoryUid.CRAFTING);
    }
}
