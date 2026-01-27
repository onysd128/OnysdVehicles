package com.mrcrayfish.vehicle.recipe;

import com.mrcrayfish.vehicle.Reference;
import com.mrcrayfish.vehicle.item.ItemSprayCan;
import net.minecraft.init.Items;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;

/**
 * Author: MrCrayfish
 */
public class RecipeRefillSprayCan extends net.minecraftforge.registries.IForgeRegistryEntry.Impl<IRecipe> implements IRecipe
{
    public RecipeRefillSprayCan()
    {
        this.setRegistryName(new ResourceLocation(Reference.MOD_ID, "refill_spray_can"));
    }

    @Override
    public boolean matches(InventoryCrafting inv, World worldIn)
    {
        ItemStack foundSprayCan = ItemStack.EMPTY;
        ItemStack foundWaterBucket = ItemStack.EMPTY;
        ItemStack foundBoneMeal = ItemStack.EMPTY;

        for (int i = 0; i < inv.getSizeInventory(); ++i)
        {
            ItemStack stack = inv.getStackInSlot(i);
            if (!stack.isEmpty())
            {
                if (stack.getItem() instanceof ItemSprayCan)
                {
                    // Accept any spray can (with or without color)
                    if (!foundSprayCan.isEmpty())
                    {
                        return false;
                    }
                    foundSprayCan = stack;
                }
                else if (stack.getItem() == Items.WATER_BUCKET)
                {
                    if (!foundWaterBucket.isEmpty())
                    {
                        return false;
                    }
                    foundWaterBucket = stack;
                }
                else if (stack.getItem() == Items.DYE && stack.getMetadata() == 15)
                {
                    // Bone meal is white dye (metadata 15)
                    if (!foundBoneMeal.isEmpty())
                    {
                        return false;
                    }
                    foundBoneMeal = stack;
                }
                else
                {
                    return false; // Unknown item
                }
            }
        }
        return !foundSprayCan.isEmpty() && !foundWaterBucket.isEmpty() && !foundBoneMeal.isEmpty();
    }

    @Override
    public ItemStack getCraftingResult(InventoryCrafting inv)
    {
        ItemStack foundSprayCan = ItemStack.EMPTY;
        for (int i = 0; i < inv.getSizeInventory(); ++i)
        {
            ItemStack stack = inv.getStackInSlot(i);
            if (!stack.isEmpty())
            {
                if (stack.getItem() instanceof ItemSprayCan)
                {
                    foundSprayCan = stack;
                    break;
                }
            }
        }

        if(!foundSprayCan.isEmpty())
        {
            ItemStack copy = foundSprayCan.copy();
            copy.setCount(1);
            NBTTagCompound tagCompound = ItemSprayCan.createTagCompound(copy);
            // Preserve color if it exists
            int color = -1;
            if(tagCompound.hasKey("color", Constants.NBT.TAG_INT))
            {
                color = tagCompound.getInteger("color");
            }
            // Set remaining sprays to max
            tagCompound.setInteger("remainingSprays", ItemSprayCan.MAX_SPRAYS);
            // Restore color if it existed
            if(color != -1)
            {
                tagCompound.setInteger("color", color);
            }
            return copy;
        }

        return ItemStack.EMPTY;
    }

    @Override
    public boolean canFit(int width, int height)
    {
        return width * height >= 3;
    }

    @Override
    public ItemStack getRecipeOutput()
    {
        return ItemStack.EMPTY;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(InventoryCrafting inv)
    {
        NonNullList<ItemStack> remainingItems = NonNullList.<ItemStack>withSize(inv.getSizeInventory(), ItemStack.EMPTY);
        for (int i = 0; i < remainingItems.size(); ++i)
        {
            ItemStack stack = inv.getStackInSlot(i);
            if (!stack.isEmpty())
            {
                if (stack.getItem() == Items.WATER_BUCKET)
                {
                    // Return empty bucket instead of water bucket
                    remainingItems.set(i, new ItemStack(Items.BUCKET));
                }
                else
                {
                    // Use default container item (bone meal and spray can are consumed)
                    remainingItems.set(i, net.minecraftforge.common.ForgeHooks.getContainerItem(stack));
                }
            }
        }
        return remainingItems;
    }

    @Override
    public boolean isDynamic()
    {
        return true;
    }
}
