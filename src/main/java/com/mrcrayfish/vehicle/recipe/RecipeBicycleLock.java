package com.mrcrayfish.vehicle.recipe;

import com.mrcrayfish.vehicle.Reference;
import com.mrcrayfish.vehicle.init.ModItems;
import com.mrcrayfish.vehicle.item.IDyeable;
import com.mrcrayfish.vehicle.util.CommonUtils;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;

/**
 * Author: Onysd
 */
public class RecipeBicycleLock extends net.minecraftforge.registries.IForgeRegistryEntry.Impl<IRecipe> implements IRecipe
{
    public RecipeBicycleLock()
    {
        this.setRegistryName(new ResourceLocation(Reference.MOD_ID, "bicycle_lock"));
    }

    @Override
    public boolean matches(InventoryCrafting inv, World worldIn)
    {
        boolean hasKey = false;
        boolean hasGold = false;
        boolean hasIron = false;
        ItemStack keyStack = ItemStack.EMPTY;

        for (int i = 0; i < inv.getSizeInventory(); ++i)
        {
            ItemStack stack = inv.getStackInSlot(i);
            if (!stack.isEmpty())
            {
                if (stack.getItem() == ModItems.KEY)
                {
                    if (hasKey)
                    {
                        return false; // Multiple keys
                    }
                    hasKey = true;
                    keyStack = stack;
                }
                else if (stack.getItem() == net.minecraft.init.Items.GOLD_INGOT)
                {
                    if (hasGold)
                    {
                        return false; // Multiple gold ingots
                    }
                    hasGold = true;
                }
                else if (stack.getItem() == net.minecraft.init.Items.IRON_INGOT)
                {
                    if (hasIron)
                    {
                        return false; // Multiple iron ingots
                    }
                    hasIron = true;
                }
                else
                {
                    return false; // Unknown item
                }
            }
        }

        // Check if key is bound to a vehicle
        if (hasKey && !keyStack.isEmpty())
        {
            if (keyStack.hasTagCompound())
            {
                net.minecraft.nbt.NBTTagCompound tag = keyStack.getTagCompound();
                if (tag.hasUniqueId("vehicleId"))
                {
                    return false; // Key is already bound to a vehicle
                }
            }
        }

        return hasKey && hasGold && hasIron;
    }

    @Override
    public ItemStack getCraftingResult(InventoryCrafting inv)
    {
        ItemStack keyStack = ItemStack.EMPTY;
        
        // Find the key in the crafting grid
        for (int i = 0; i < inv.getSizeInventory(); ++i)
        {
            ItemStack stack = inv.getStackInSlot(i);
            if (!stack.isEmpty() && stack.getItem() == ModItems.KEY)
            {
                keyStack = stack;
                break;
            }
        }

        // Create the bicycle lock
        ItemStack lockStack = new ItemStack(ModItems.BICYCLE_LOCK);
        
        // Copy color from key to lock if key has color
        if (!keyStack.isEmpty() && keyStack.getItem() instanceof IDyeable)
        {
            IDyeable dyeableKey = (IDyeable) keyStack.getItem();
            if (dyeableKey.hasColor(keyStack))
            {
                IDyeable dyeableLock = (IDyeable) ModItems.BICYCLE_LOCK;
                dyeableLock.setColor(lockStack, dyeableKey.getColor(keyStack));
            }
        }
        
        return lockStack;
    }

    @Override
    public boolean canFit(int width, int height)
    {
        return width * height >= 3;
    }

    @Override
    public ItemStack getRecipeOutput()
    {
        return new ItemStack(ModItems.BICYCLE_LOCK);
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(InventoryCrafting inv)
    {
        NonNullList<ItemStack> remainingItems = NonNullList.<ItemStack>withSize(inv.getSizeInventory(), ItemStack.EMPTY);
        for (int i = 0; i < remainingItems.size(); ++i)
        {
            ItemStack stack = inv.getStackInSlot(i);
            remainingItems.set(i, net.minecraftforge.common.ForgeHooks.getContainerItem(stack));
        }
        return remainingItems;
    }

    @Override
    public boolean isDynamic()
    {
        return false;
    }
}
