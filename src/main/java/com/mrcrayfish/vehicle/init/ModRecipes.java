package com.mrcrayfish.vehicle.init;

import com.mrcrayfish.vehicle.recipe.RecipeBicycleLock;
import com.mrcrayfish.vehicle.recipe.RecipeColorSprayCan;
import com.mrcrayfish.vehicle.recipe.RecipeDyeFluidExtractor;
import com.mrcrayfish.vehicle.recipe.RecipeDyeFluidMixer;
import com.mrcrayfish.vehicle.recipe.RecipeDyeFluidPipe;
import com.mrcrayfish.vehicle.recipe.RecipeDyeFluidPump;
import com.mrcrayfish.vehicle.recipe.RecipeDyeFuelDrum;
import com.mrcrayfish.vehicle.recipe.RecipeDyeJerryCan;
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
        RegistrationHandler.Recipes.add(new RecipeDyeFluidMixer());
        RegistrationHandler.Recipes.add(new RecipeDyeFluidExtractor());
        RegistrationHandler.Recipes.add(new RecipeDyeFluidPipe());
        RegistrationHandler.Recipes.add(new RecipeDyeFluidPump());
        RegistrationHandler.Recipes.add(new RecipeDyeJerryCan());
    }
}
