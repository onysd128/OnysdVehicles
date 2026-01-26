package com.mrcrayfish.vehicle.recipe;

import com.google.common.collect.Lists;
import com.mrcrayfish.vehicle.Reference;
import com.mrcrayfish.vehicle.init.ModItems;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;

import java.util.List;

/**
 * Author: MrCrayfish
 */
public class RecipeDyeJerryCan extends net.minecraftforge.registries.IForgeRegistryEntry.Impl<IRecipe> implements IRecipe
{
    public RecipeDyeJerryCan()
    {
        this.setRegistryName(new ResourceLocation(Reference.MOD_ID, "dye_jerry_can"));
    }

    @Override
    public boolean matches(InventoryCrafting inv, World worldIn)
    {
        ItemStack jerryCanItem = ItemStack.EMPTY;
        List<ItemStack> dyes = Lists.newArrayList();

        for (int i = 0; i < inv.getSizeInventory(); ++i)
        {
            ItemStack stack = inv.getStackInSlot(i);
            if (!stack.isEmpty())
            {
                if (stack.getItem() == ModItems.JERRY_CAN || stack.getItem() == ModItems.INDUSTRIAL_JERRY_CAN)
                {
                    if (!jerryCanItem.isEmpty())
                    {
                        return false;
                    }
                    jerryCanItem = stack;
                }
                else if (net.minecraftforge.oredict.DyeUtils.isDye(stack))
                {
                    dyes.add(stack);
                }
                else
                {
                    return false;
                }
            }
        }

        return !jerryCanItem.isEmpty() && !dyes.isEmpty();
    }

    @Override
    public ItemStack getCraftingResult(InventoryCrafting inv)
    {
        ItemStack jerryCanItem = ItemStack.EMPTY;
        int[] combinedValues = new int[3];
        int combinedColor = 0;
        int colorCount = 0;

        for (int k = 0; k < inv.getSizeInventory(); ++k)
        {
            ItemStack stack = inv.getStackInSlot(k);
            if (!stack.isEmpty())
            {
                if (stack.getItem() == ModItems.JERRY_CAN || stack.getItem() == ModItems.INDUSTRIAL_JERRY_CAN)
                {
                    if (!jerryCanItem.isEmpty())
                    {
                        return ItemStack.EMPTY;
                    }
                    jerryCanItem = stack.copy();
                    jerryCanItem.setCount(1);

                    // If item already has color, include it in the mix
                    if (jerryCanItem.hasTagCompound() && jerryCanItem.getTagCompound().hasKey("color", Constants.NBT.TAG_INT))
                    {
                        int existingColor = jerryCanItem.getTagCompound().getInteger("color");
                        float red = (float)(existingColor >> 16 & 255) / 255.0F;
                        float green = (float)(existingColor >> 8 & 255) / 255.0F;
                        float blue = (float)(existingColor & 255) / 255.0F;
                        combinedColor = (int)((float)combinedColor + Math.max(red, Math.max(green, blue)) * 255.0F);
                        combinedValues[0] = (int)((float)combinedValues[0] + red * 255.0F);
                        combinedValues[1] = (int)((float)combinedValues[1] + green * 255.0F);
                        combinedValues[2] = (int)((float)combinedValues[2] + blue * 255.0F);
                        colorCount++;
                    }
                }
                else if (net.minecraftforge.oredict.DyeUtils.isDye(stack))
                {
                    float[] color = net.minecraftforge.oredict.DyeUtils.colorFromStack(stack).get().getColorComponentValues();
                    int red = (int)(color[0] * 255.0F);
                    int green = (int)(color[1] * 255.0F);
                    int blue = (int)(color[2] * 255.0F);
                    combinedColor += Math.max(red, Math.max(green, blue));
                    combinedValues[0] += red;
                    combinedValues[1] += green;
                    combinedValues[2] += blue;
                    colorCount++;
                }
            }
        }

        if (jerryCanItem.isEmpty() || colorCount == 0)
        {
            return ItemStack.EMPTY;
        }
        else
        {
            int red = combinedValues[0] / colorCount;
            int green = combinedValues[1] / colorCount;
            int blue = combinedValues[2] / colorCount;
            float averageColor = (float)combinedColor / (float)colorCount;
            float maxValue = (float)Math.max(red, Math.max(green, blue));
            if (maxValue > 0)
            {
                red = (int)((float)red * averageColor / maxValue);
                green = (int)((float)green * averageColor / maxValue);
                blue = (int)((float)blue * averageColor / maxValue);
            }
            int finalColor = (red << 8) + green;
            finalColor = (finalColor << 8) + blue;

            // Store color in ItemStack NBT
            if (!jerryCanItem.hasTagCompound())
            {
                jerryCanItem.setTagCompound(new NBTTagCompound());
            }
            jerryCanItem.getTagCompound().setInteger("color", finalColor);

            return jerryCanItem;
        }
    }

    @Override
    public boolean canFit(int width, int height)
    {
        return width * height >= 2;
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
            remainingItems.set(i, net.minecraftforge.common.ForgeHooks.getContainerItem(stack));
        }
        return remainingItems;
    }

    @Override
    public boolean isDynamic()
    {
        return true;
    }
}
