package com.mrcrayfish.vehicle.item;

import com.mrcrayfish.vehicle.VehicleMod;
import com.mrcrayfish.vehicle.util.ItemNames;
import net.minecraft.item.Item;

/**
 * Author: MrCrayfish
 */
public class ItemBicycleLock extends Item
{
    public ItemBicycleLock()
    {
        this.setUnlocalizedName(ItemNames.BICYCLE_LOCK.replace(":", "."));
        this.setRegistryName(ItemNames.BICYCLE_LOCK);
        this.setCreativeTab(VehicleMod.CREATIVE_TAB);
        this.setMaxStackSize(1);
    }
}
