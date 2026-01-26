package com.mrcrayfish.vehicle.block;

import com.mrcrayfish.vehicle.VehicleMod;
import com.mrcrayfish.vehicle.init.ModItems;
import com.mrcrayfish.vehicle.init.ModSounds;
import com.mrcrayfish.vehicle.item.ItemSprayCan;
import com.mrcrayfish.vehicle.tileentity.TileEntityFluidMixer;
import com.mrcrayfish.vehicle.tileentity.TileEntitySynced;
import com.mrcrayfish.vehicle.util.BlockNames;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fluids.FluidUtil;

import javax.annotation.Nullable;
import java.util.Random;

/**
 * Author: MrCrayfish
 */
public class BlockFluidMixer extends BlockRotatedObject
{
    public BlockFluidMixer()
    {
        super(Material.ANVIL, BlockNames.FLUID_MIXER);
        this.setHardness(1.0F);
    }

    @Override
    public boolean onBlockActivated(World worldIn, BlockPos pos, IBlockState state, EntityPlayer playerIn, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ)
    {
        ItemStack heldItem = playerIn.getHeldItem(hand);
        
        // Check if player is holding a spray can
        if(!heldItem.isEmpty() && heldItem.getItem() == ModItems.SPRAY_CAN)
        {
            if(!worldIn.isRemote)
            {
                NBTTagCompound tagCompound = ItemSprayCan.createTagCompound(heldItem);
                int remainingSprays = tagCompound.getInteger("remainingSprays");
                if(tagCompound.hasKey("color", Constants.NBT.TAG_INT) && remainingSprays > 0)
                {
                    int color = tagCompound.getInteger("color");
                    TileEntity tileEntity = worldIn.getTileEntity(pos);
                    if(tileEntity instanceof TileEntityFluidMixer)
                    {
                        TileEntityFluidMixer fluidMixer = (TileEntityFluidMixer) tileEntity;
                        if(fluidMixer.getColor() != color)
                        {
                            fluidMixer.setColor(color);
                            worldIn.playSound(null, pos, ModSounds.SPRAY_CAN_SPRAY, SoundCategory.PLAYERS, 1.0F, 1.0F);
                            tagCompound.setInteger("remainingSprays", remainingSprays - 1);
                        }
                    }
                }
            }
            return true;
        }
        
        if(!worldIn.isRemote)
        {
            if(!FluidUtil.interactWithFluidHandler(playerIn, hand, worldIn, pos, facing))
            {
                TileEntity tileEntity = worldIn.getTileEntity(pos);
                if(tileEntity instanceof TileEntityFluidMixer)
                {
                    ((TileEntitySynced)tileEntity).syncToClient();
                    playerIn.openGui(VehicleMod.instance, 0, worldIn, pos.getX(), pos.getY(), pos.getZ());
                }
            }
        }
        return true;
    }

    @Override
    public Item getItemDropped(IBlockState state, Random rand, int fortune)
    {
        return Items.AIR;
    }

    @Override
    public boolean removedByPlayer(IBlockState state, World world, BlockPos pos, EntityPlayer player, boolean willHarvest)
    {
        if(!world.isRemote && !player.capabilities.isCreativeMode)
        {
            TileEntity tileEntity = world.getTileEntity(pos);
            if(tileEntity instanceof TileEntityFluidMixer)
            {
                TileEntityFluidMixer tileEntityFluidMixer = (TileEntityFluidMixer) tileEntity;
                NBTTagCompound tileEntityTag = new NBTTagCompound();
                tileEntity.writeToNBT(tileEntityTag);
                tileEntityTag.removeTag("x");
                tileEntityTag.removeTag("y");
                tileEntityTag.removeTag("z");
                tileEntityTag.removeTag("id");
                tileEntityTag.removeTag("RemainingFuel");
                tileEntityTag.removeTag("FuelMaxProgress");
                tileEntityTag.removeTag("ExtractionProgress");

                NBTTagCompound compound = new NBTTagCompound();
                compound.setTag("BlockEntityTag", tileEntityTag);

                ItemStack drop = new ItemStack(Item.getItemFromBlock(this));
                drop.setTagCompound(compound);
                
                // Save color to ItemStack NBT if it has a color
                if(tileEntityFluidMixer.hasColor())
                {
                    // Save color in root NBT (for recipe compatibility)
                    drop.getTagCompound().setInteger("color", tileEntityFluidMixer.getColor());
                }
                
                if(tileEntityFluidMixer.hasCustomName())
                {
                    drop.setStackDisplayName(tileEntityFluidMixer.getName());
                }
                world.spawnEntity(new EntityItem(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, drop));
                return world.setBlockToAir(pos);
            }
        }
        return super.removedByPlayer(state, world, pos, player, willHarvest);
    }

    @Override
    public void onBlockPlacedBy(World worldIn, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack)
    {
        super.onBlockPlacedBy(worldIn, pos, state, placer, stack);
        
        // Transfer color from ItemStack NBT to TileEntity
        if(!worldIn.isRemote && stack.hasTagCompound())
        {
            NBTTagCompound tagCompound = stack.getTagCompound();
            int color = -1;
            
            // Check root NBT first (for crafted items)
            if(tagCompound.hasKey("color", Constants.NBT.TAG_INT))
            {
                color = tagCompound.getInteger("color");
            }
            // Check BlockEntityTag (for items broken from world)
            else if(tagCompound.hasKey("BlockEntityTag", Constants.NBT.TAG_COMPOUND))
            {
                NBTTagCompound blockEntityTag = tagCompound.getCompoundTag("BlockEntityTag");
                if(blockEntityTag.hasKey("color", Constants.NBT.TAG_INT))
                {
                    color = blockEntityTag.getInteger("color");
                }
            }
            
            if(color != -1)
            {
                TileEntity tileEntity = worldIn.getTileEntity(pos);
                if(tileEntity instanceof TileEntityFluidMixer)
                {
                    TileEntityFluidMixer fluidMixer = (TileEntityFluidMixer) tileEntity;
                    fluidMixer.setColor(color);
                }
            }
        }
    }

    @Override
    public boolean hasTileEntity(IBlockState state)
    {
        return true;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(World world, IBlockState state)
    {
        return new TileEntityFluidMixer();
    }
}
