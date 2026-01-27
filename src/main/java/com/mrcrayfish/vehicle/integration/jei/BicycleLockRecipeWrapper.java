package com.mrcrayfish.vehicle.integration.jei;

import com.mrcrayfish.vehicle.init.ModItems;
import com.mrcrayfish.vehicle.recipe.RecipeBicycleLock;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.recipe.IRecipeWrapper;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * JEI Recipe Wrapper for Bicycle Lock Recipe
 */
public class BicycleLockRecipeWrapper implements IRecipeWrapper
{
    private final RecipeBicycleLock recipe;

    public BicycleLockRecipeWrapper(RecipeBicycleLock recipe)
    {
        this.recipe = recipe;
    }

    @Override
    public void getIngredients(@Nonnull IIngredients ingredients)
    {
        List<List<ItemStack>> inputs = new ArrayList<>();
        
        // Key (without vehicleId NBT - unbound key)
        ItemStack keyStack = new ItemStack(ModItems.KEY);
        inputs.add(Arrays.asList(keyStack));
        
        // Gold ingot
        inputs.add(Arrays.asList(new ItemStack(Items.GOLD_INGOT)));
        
        // Iron ingot
        inputs.add(Arrays.asList(new ItemStack(Items.IRON_INGOT)));
        
        ingredients.setInputLists(ItemStack.class, inputs);
        
        // Output: Bicycle lock
        ingredients.setOutput(ItemStack.class, new ItemStack(ModItems.BICYCLE_LOCK));
    }
}
