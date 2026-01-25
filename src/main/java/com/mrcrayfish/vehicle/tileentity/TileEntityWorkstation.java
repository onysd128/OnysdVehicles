package com.mrcrayfish.vehicle.tileentity;

import com.mrcrayfish.vehicle.inventory.IStorageBlock;
import net.minecraft.block.state.IBlockState;
import net.minecraft.inventory.ItemStackHelper;
import net.minecraft.item.ItemDye;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.util.NonNullList;
import net.minecraftforge.common.util.Constants;

/**
 * Author: MrCrayfish
 */
public class TileEntityWorkstation extends TileEntitySynced implements IStorageBlock
{
    private NonNullList<ItemStack> inventory = NonNullList.withSize(3, ItemStack.EMPTY);
    private int color = -1; // -1 means no color (default)

    @Override
    public NonNullList<ItemStack> getInventory()
    {
        return inventory;
    }

    @Override
    public String getName()
    {
        return "vehicle.container.workstation";
    }

    public boolean hasColor()
    {
        return color != -1;
    }

    public int getColor()
    {
        return color;
    }

    public void setColor(int color)
    {
        this.color = color;
        this.markDirty();
        if(this.world != null && !this.world.isRemote)
        {
            // Sync to clients
            this.syncToClient();
            // Force block update for rendering
            IBlockState state = this.world.getBlockState(this.pos);
            this.world.notifyBlockUpdate(this.pos, state, state, 3);
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound)
    {
        ItemStackHelper.saveAllItems(compound, this.inventory);
        if(color != -1)
        {
            compound.setInteger("color", color);
        }
        return super.writeToNBT(compound);
    }

    @Override
    public void readFromNBT(NBTTagCompound compound)
    {
        super.readFromNBT(compound);
        ItemStackHelper.loadAllItems(compound, this.inventory);
        if(compound.hasKey("color", Constants.NBT.TAG_INT))
        {
            color = compound.getInteger("color");
        }
        else
        {
            color = -1;
        }
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt)
    {
        super.onDataPacket(net, pkt);
        // Force client-side render update when color changes
        if(this.world != null && this.world.isRemote)
        {
            this.world.markBlockRangeForRenderUpdate(this.pos, this.pos);
        }
    }

    @Override
    public boolean isItemValidForSlot(int index, ItemStack stack)
    {
        return index != 0 || (stack.getItem() instanceof ItemDye && inventory.get(index).getCount() < 1);
    }
}
