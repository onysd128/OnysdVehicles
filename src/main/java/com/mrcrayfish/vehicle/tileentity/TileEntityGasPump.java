package com.mrcrayfish.vehicle.tileentity;

import com.google.common.base.Optional;
import com.mrcrayfish.vehicle.VehicleConfig;
import com.mrcrayfish.vehicle.client.util.HermiteInterpolator;
import com.mrcrayfish.vehicle.common.CommonEvents;
import com.mrcrayfish.vehicle.common.entity.SyncedPlayerData;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;

/**
 * Author: MrCrayfish
 */
public class TileEntityGasPump extends TileEntitySynced implements ITickable
{
    private int fuelingEntityId;
    private EntityPlayer fuelingEntity;

    private HermiteInterpolator cachedSpline;
    private boolean recentlyUsed;
    protected int color = -1; // -1 means no color (default)

    public HermiteInterpolator getCachedSpline()
    {
        return cachedSpline;
    }

    public void setCachedSpline(HermiteInterpolator cachedSpline)
    {
        this.cachedSpline = cachedSpline;
    }

    public boolean isRecentlyUsed()
    {
        return recentlyUsed;
    }

    public void setRecentlyUsed(boolean recentlyUsed)
    {
        this.recentlyUsed = recentlyUsed;
    }

    @Nullable
    public FluidTank getTank()
    {
        TileEntity tileEntity = world.getTileEntity(pos.down());
        if(tileEntity instanceof TileEntityGasPumpTank)
        {
            return ((TileEntityGasPumpTank) tileEntity).getFluidTank();
        }
        return null;
    }

    public EntityPlayer getFuelingEntity()
    {
        return fuelingEntity;
    }

    public void setFuelingEntity(@Nullable EntityPlayer entity)
    {
        if(!world.isRemote)
        {
            if(fuelingEntity != null)
            {
                SyncedPlayerData.setGasPumpPos(this.fuelingEntity, Optional.absent());
            }
            this.fuelingEntity = null;
            this.fuelingEntityId = -1;
            if(entity != null)
            {
                this.fuelingEntityId = entity.getEntityId();
                SyncedPlayerData.setGasPumpPos(entity, Optional.fromNullable(this.getPos()));
            }
            this.syncToClient();
        }
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
            
            // Also update the bottom block (tank) to keep colors synchronized
            if(syncToOther)
            {
                TileEntity bottomTile = this.world.getTileEntity(this.pos.down());
                if(bottomTile instanceof TileEntityGasPumpTank)
                {
                    TileEntityGasPumpTank tank = (TileEntityGasPumpTank) bottomTile;
                    if(tank.getColor() != color)
                    {
                        // Set color without syncing back to avoid recursion
                        tank.setColor(color, false);
                    }
                }
            }
        }
    }

    @Override
    public void update()
    {
        if(fuelingEntityId != -1)
        {
            if(fuelingEntity == null)
            {
                Entity entity = world.getEntityByID(fuelingEntityId);
                if(entity instanceof EntityPlayer)
                {
                    fuelingEntity = (EntityPlayer) entity;
                }
                else if(!world.isRemote)
                {
                    fuelingEntityId = -1;
                    this.syncToClient();
                }
            }
        }
        else if(world.isRemote && fuelingEntity != null)
        {
            fuelingEntity = null;
        }

        if(!world.isRemote && fuelingEntity != null)
        {
            if(Math.sqrt(fuelingEntity.getDistanceSq(this.getPos())) > VehicleConfig.SERVER.maxHoseDistance || fuelingEntity.isDead)
            {
                if(!fuelingEntity.isDead)
                {
                    world.playSound(null, fuelingEntity.getPosition(), SoundEvents.ENTITY_ITEM_BREAK, SoundCategory.PLAYERS, 1.0F, 1.0F);
                }
                SyncedPlayerData.setGasPumpPos(this.fuelingEntity, Optional.absent());
                fuelingEntityId = -1;
                fuelingEntity = null;
                this.syncToClient();
                //TODO add breaking sound like when a trailer disconnects
            }
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound compound)
    {
        super.readFromNBT(compound);
        if(compound.hasKey("fuelingEntityId", Constants.NBT.TAG_INT))
        {
            this.fuelingEntityId = compound.getInteger("fuelingEntityId");
        }
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
    public NBTTagCompound getUpdateTag()
    {
        NBTTagCompound tagCompound = super.writeToNBT(new NBTTagCompound());
        tagCompound.setInteger("fuelingEntityId", this.fuelingEntityId);
        return tagCompound;
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
    public AxisAlignedBB getRenderBoundingBox()
    {
        return INFINITE_EXTENT_AABB;
    }

    @SideOnly(Side.CLIENT)
    public double getMaxRenderDistanceSquared()
    {
        return 65536.0D;
    }
}
