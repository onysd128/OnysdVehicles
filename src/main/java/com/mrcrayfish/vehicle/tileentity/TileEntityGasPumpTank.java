package com.mrcrayfish.vehicle.tileentity;

import com.mrcrayfish.vehicle.init.ModFluids;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;

/**
 * Author: MrCrayfish
 */
public class TileEntityGasPumpTank extends TileFluidHandlerSynced
{
    protected int color = -1; // -1 means no color (default)

    public TileEntityGasPumpTank()
    {
        tank = new FluidTank(Fluid.BUCKET_VOLUME * 50)
        {
            @Override
            protected void onContentsChanged()
            {
                syncToClient();
            }

            @Override
            public boolean canFillFluidType(FluidStack fluid)
            {
                return fluid.getFluid() == ModFluids.FUELIUM;
            }
        };
        tank.setCanFill(true);
        tank.setCanDrain(true);
    }

    public FluidTank getFluidTank()
    {
        return tank;
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
        setColor(color, true);
    }

    public void setColor(int color, boolean syncToOther)
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
            
            // Also update the top block (pump) to keep colors synchronized
            if(syncToOther)
            {
                TileEntity topTile = this.world.getTileEntity(this.pos.up());
                if(topTile instanceof com.mrcrayfish.vehicle.tileentity.TileEntityGasPump)
                {
                    com.mrcrayfish.vehicle.tileentity.TileEntityGasPump pump = (com.mrcrayfish.vehicle.tileentity.TileEntityGasPump) topTile;
                    if(pump.getColor() != color)
                    {
                        // Set color without syncing back to avoid recursion
                        pump.setColor(color, false);
                    }
                }
            }
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound compound)
    {
        super.readFromNBT(compound);
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
    public NBTTagCompound writeToNBT(NBTTagCompound compound)
    {
        super.writeToNBT(compound);
        if(color != -1)
        {
            compound.setInteger("color", color);
        }
        return compound;
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