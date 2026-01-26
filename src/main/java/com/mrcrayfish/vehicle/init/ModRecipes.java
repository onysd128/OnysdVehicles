package com.mrcrayfish.vehicle.init;

import com.mrcrayfish.vehicle.recipe.RecipeBicycleLock;
import com.mrcrayfish.vehicle.recipe.RecipeColorSprayCan;
import com.mrcrayfish.vehicle.recipe.RecipeDyeFuelDrum;
import com.mrcrayfish.vehicle.recipe.RecipeDyeGasPump;
import com.mrcrayfish.vehicle.recipe.RecipeDyeJack;
import com.mrcrayfish.vehicle.recipe.RecipeDyeWorkstation;
import com.mrcrayfish.vehicle.recipe.RecipeRefillSprayCan;

/**
 * Author: MrCrayfish
 */
public class ModRecipes
{
    public static void register()
    {
        RegistrationHandler.Recipes.add(new RecipeColorSprayCan());
        RegistrationHandler.Recipes.add(new RecipeRefillSprayCan());
        RegistrationHandler.Recipes.add(new RecipeBicycleLock());
        RegistrationHandler.Recipes.add(new RecipeDyeWorkstation());
        RegistrationHandler.Recipes.add(new RecipeDyeGasPump());
        RegistrationHandler.Recipes.add(new RecipeDyeJack());
        RegistrationHandler.Recipes.add(new RecipeDyeFuelDrum());
    }
}
