package com.mrcrayfish.vehicle.item;

import com.mrcrayfish.vehicle.VehicleMod;
import com.mrcrayfish.vehicle.util.ItemNames;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Author: MrCrayfish
 */
public class ItemSprayCan extends Item implements SubItems, IDyeable
{
    public static final int MAX_SPRAYS = 5;

    public ItemSprayCan()
    {
        this.setUnlocalizedName(ItemNames.SPRAY_CAN.replace(":", "."));
        this.setRegistryName(ItemNames.SPRAY_CAN);
        this.setMaxDamage(0);
        this.setHasSubtypes(true);
        this.setCreativeTab(VehicleMod.CREATIVE_TAB);
    }

    @Override
    public NonNullList<ResourceLocation> getModels()
    {
        NonNullList<ResourceLocation> modelLocations = NonNullList.create();
        modelLocations.add(new ResourceLocation(this.getRegistryName() + "_lid"));
        modelLocations.add(new ResourceLocation(this.getRegistryName().toString()));
        return modelLocations;
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World worldIn, List<String> tooltip, ITooltipFlag flagIn)
    {
        if(GuiScreen.isShiftKeyDown())
        {
            String info = I18n.format("item.vehicle.spray_can.info");
            tooltip.addAll(Minecraft.getMinecraft().fontRenderer.listFormattedStringToWidth(info, 150));
        }
        else
        {
            if(hasColor(stack))
            {
                tooltip.add(I18n.format("item.color", TextFormatting.DARK_GRAY.toString() + String.format("#%06X", createTagCompound(stack).getInteger("color"))));
            }
            else
            {
                tooltip.add(I18n.format("item.vehicle.spray_can.empty"));
            }
            tooltip.add(TextFormatting.YELLOW + I18n.format("vehicle.info_help"));
        }
    }

    public static NBTTagCompound createTagCompound(ItemStack stack)
    {
        if(!stack.hasTagCompound())
        {
            stack.setTagCompound(new NBTTagCompound());
        }
        NBTTagCompound tagCompound = stack.getTagCompound();
        if(tagCompound != null)
        {
            if(!tagCompound.hasKey("remainingSprays", Constants.NBT.TAG_INT))
            {
                tagCompound.setInteger("remainingSprays", MAX_SPRAYS);
            }
        }
        return tagCompound;
    }

    @Override
    public boolean hasColor(ItemStack stack)
    {
        NBTTagCompound tagCompound = createTagCompound(stack);
        return tagCompound.hasKey("color", Constants.NBT.TAG_INT);
    }

    @Override
    public int getColor(ItemStack stack)
    {
        NBTTagCompound tagCompound = createTagCompound(stack);
        return tagCompound.getInteger("color");
    }

    @Override
    public void setColor(ItemStack stack, int color)
    {
        NBTTagCompound tagCompound = createTagCompound(stack);
        tagCompound.setInteger("color", color);
    }

    @Override
    public boolean showDurabilityBar(ItemStack stack)
    {
        NBTTagCompound tagCompound = createTagCompound(stack);
        int remainingSprays = tagCompound.getInteger("remainingSprays");
        return tagCompound.hasKey("color", Constants.NBT.TAG_INT) && remainingSprays >= 0 && remainingSprays < MAX_SPRAYS;
    }

    @Override
    public double getDurabilityForDisplay(ItemStack stack)
    {
        NBTTagCompound tagCompound = createTagCompound(stack);
        return 1.0D - (tagCompound.getInteger("remainingSprays") / (double) MAX_SPRAYS);
    }

    public static float getRemainingSprays(ItemStack stack)
    {
        NBTTagCompound tagCompound = createTagCompound(stack);
        return (tagCompound.getInteger("remainingSprays") / (float) MAX_SPRAYS);
    }

    /**
     * Safely uses a spray from the stack, handling stack splitting if needed.
     * If the stack has more than one item, it creates a copy of one item with its own NBT,
     * decrements remainingSprays on the copy, and updates the player's held item.
     * 
     * @param stack The spray can stack
     * @param player The player using the spray can
     * @param hand The hand holding the spray can
     * @return The ItemStack to use for NBT access (either the original stack if count == 1, or the new single-item stack)
     */
    public static ItemStack useSpray(ItemStack stack, EntityPlayer player, EnumHand hand)
    {
        if(stack.getCount() > 1)
        {
            // Split one item from the stack (this automatically reduces the original stack size)
            // splitStack returns a new stack with count=1 and reduces the original stack by 1
            ItemStack singleItem = stack.splitStack(1);
            
            // Modify the single item's NBT (it now has its own NBT copy)
            NBTTagCompound tagCompound = createTagCompound(singleItem);
            int remainingSprays = tagCompound.getInteger("remainingSprays");
            if(remainingSprays > 0)
            {
                tagCompound.setInteger("remainingSprays", remainingSprays - 1);
            }
            
            // Set the single item (with modified NBT) in player's hand
            // The remaining stack (stack) will automatically stay in inventory slot
            if(hand == EnumHand.MAIN_HAND)
            {
                player.inventory.setInventorySlotContents(player.inventory.currentItem, singleItem);
                // If there's remaining stack, try to add it back to inventory
                if(!stack.isEmpty())
                {
                    if(!player.inventory.addItemStackToInventory(stack))
                    {
                        // If inventory is full, drop the remaining stack
                        player.dropItem(stack, false);
                    }
                }
            }
            else
            {
                player.setHeldItem(hand, singleItem);
                // If there's remaining stack, try to add it back to inventory
                if(!stack.isEmpty())
                {
                    if(!player.inventory.addItemStackToInventory(stack))
                    {
                        // If inventory is full, drop the remaining stack
                        player.dropItem(stack, false);
                    }
                }
            }
            
            return singleItem;
        }
        else
        {
            // Single item, modify directly
            NBTTagCompound tagCompound = createTagCompound(stack);
            int remainingSprays = tagCompound.getInteger("remainingSprays");
            if(remainingSprays > 0)
            {
                tagCompound.setInteger("remainingSprays", remainingSprays - 1);
            }
            return stack;
        }
    }

    @Override
    public void getSubItems(CreativeTabs tab, NonNullList<ItemStack> items)
    {
         if(this.isInCreativeTab(tab))
         {
             ItemStack stack = new ItemStack(this, 1, 0);
             createTagCompound(stack);
             items.add(stack);
         }
    }
}
