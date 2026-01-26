package com.mrcrayfish.vehicle.tileentity;

import com.mrcrayfish.vehicle.util.FluidUtils;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fluids.FluidTank;

/**
 * Author: MrCrayfish
 */
public class TileEntityFuelDrum extends TileFluidHandlerSynced
{
    protected int color = -1; // -1 means no color (default)

    public TileEntityFuelDrum()
    {
        tank = new FluidTank(0)
        {
            @Override
            protected void onContentsChanged()
            {
                syncToClient();
            }
        };
        tank.setCanFill(true);
        tank.setCanDrain(true);
    }

    public TileEntityFuelDrum(int capacity)
    {
        this();
        this.setCapacity(capacity);
    }

    public FluidTank getFluidTank()
    {
        return tank;
    }

    public boolean hasFluid()
    {
        return tank.getFluid() != null;
    }

    public int getAmount()
    {
        return tank.getFluidAmount();
    }

    public int getCapacity()
    {
        return tank.getCapacity();
    }

    private void setCapacity(int capacity)
    {
        tank.setCapacity(capacity);
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
    public void readFromNBT(NBTTagCompound tag)
    {
        FluidUtils.fixEmptyTag(tag);
        super.readFromNBT(tag);
        if(tag.hasKey("capacity", Constants.NBT.TAG_INT))
        {
            this.setCapacity(tag.getInteger("capacity"));
        }
        if(tag.hasKey("color", Constants.NBT.TAG_INT))
        {
            color = tag.getInteger("color");
        }
        else
        {
            color = -1;
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag)
    {
        super.writeToNBT(tag);
        tag.setInteger("capacity", tank.getCapacity());
        if(color != -1)
        {
            tag.setInteger("color", color);
        }
        return tag;
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

}
