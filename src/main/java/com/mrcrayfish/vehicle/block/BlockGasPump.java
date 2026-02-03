package com.mrcrayfish.vehicle.block;

import com.mrcrayfish.vehicle.init.ModFluids;
import com.mrcrayfish.vehicle.init.ModItems;
import com.mrcrayfish.vehicle.init.ModSounds;
import com.mrcrayfish.vehicle.item.ItemJerryCan;
import com.mrcrayfish.vehicle.item.ItemSprayCan;
import com.mrcrayfish.vehicle.common.entity.SyncedPlayerData;
import com.mrcrayfish.vehicle.tileentity.TileEntityGasPump;
import com.mrcrayfish.vehicle.tileentity.TileEntityGasPumpTank;
import com.mrcrayfish.vehicle.util.BlockNames;
import com.google.common.base.Optional;
import com.mrcrayfish.vehicle.util.Bounds;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Author: MrCrayfish
 */
public class BlockGasPump extends BlockRotatedObject
{
    public static final PropertyBool TOP = PropertyBool.create("top");

    private static final AxisAlignedBB[] COLLISION_BOXES = new Bounds(3, 0, 0, 13, 15, 16).getRotatedBounds();
    private static final AxisAlignedBB[] TOP_SELECTION_BOXES = new Bounds(3, -16, 0, 13, 15, 16).getRotatedBounds();
    private static final AxisAlignedBB[] BOTTOM_SELECTION_BOXES = new Bounds(3, 0, 0, 13, 31, 16).getRotatedBounds();

    public BlockGasPump()
    {
        super(Material.ANVIL, BlockNames.GAS_PUMP);
        this.setDefaultState(this.blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH).withProperty(TOP, false));
        this.setHardness(1.0F);
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos)
    {
        EnumFacing facing = state.getValue(FACING);
        return state.getValue(TOP) ? TOP_SELECTION_BOXES[facing.getHorizontalIndex()] : BOTTOM_SELECTION_BOXES[facing.getHorizontalIndex()];
    }

    @Override
    public void addCollisionBoxToList(IBlockState state, World worldIn, BlockPos pos, AxisAlignedBB entityBox, List<AxisAlignedBB> collidingBoxes, @Nullable Entity entityIn, boolean isActualState)
    {
        EnumFacing facing = state.getValue(FACING);
        Block.addCollisionBoxToList(pos, entityBox, collidingBoxes, COLLISION_BOXES[facing.getHorizontalIndex()]);
    }

    @Override
    public boolean onBlockActivated(World worldIn, BlockPos pos, IBlockState state, EntityPlayer playerIn, EnumHand hand, EnumFacing face, float hitX, float hitY, float hitZ)
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
                    
                    // Get the bottom block position (where the tank is)
                    BlockPos bottomPos = state.getValue(TOP) ? pos.down() : pos;
                    BlockPos topPos = state.getValue(TOP) ? pos : pos.up();
                    
                    // Set color on both tile entities
                    TileEntity bottomTile = worldIn.getTileEntity(bottomPos);
                    TileEntity topTile = worldIn.getTileEntity(topPos);
                    
                    boolean colorChanged = false;
                    
                    // Set color on bottom first, which will sync to top
                    if(bottomTile instanceof TileEntityGasPumpTank)
                    {
                        TileEntityGasPumpTank tank = (TileEntityGasPumpTank) bottomTile;
                        if(tank.getColor() != color)
                        {
                            tank.setColor(color, true);
                            colorChanged = true;
                        }
                    }
                    
                    // Set color on top (it won't sync back since bottom already has the color)
                    if(topTile instanceof TileEntityGasPump)
                    {
                        TileEntityGasPump pump = (TileEntityGasPump) topTile;
                        if(pump.getColor() != color)
                        {
                            pump.setColor(color, false); // Don't sync back to avoid double sync
                            colorChanged = true;
                        }
                    }
                    
                    if(colorChanged)
                    {
                        worldIn.playSound(null, pos, ModSounds.SPRAY_CAN_SPRAY, SoundCategory.PLAYERS, 1.0F, 1.0F);
                        ItemSprayCan.useSpray(heldItem, playerIn, hand);
                    }
                }
            }
            return true;
        }
        
        if(!worldIn.isRemote)
        {
            if(state.getValue(TOP))
            {
                TileEntity tileEntity = worldIn.getTileEntity(pos);
                if(tileEntity instanceof TileEntityGasPump)
                {
                    TileEntityGasPump gasPump = (TileEntityGasPump) tileEntity;
                    if(gasPump.getFuelingEntity() != null && gasPump.getFuelingEntity().getEntityId() == playerIn.getEntityId())
                    {
                        gasPump.setFuelingEntity(null);
                        worldIn.playSound(null, pos, ModSounds.NOZZLE_PUT_DOWN, SoundCategory.BLOCKS, 1.0F, 1.0F);
                    }
                    else if(state.getValue(FACING).rotateY().equals(face))
                    {
                        // Check if player already has a nozzle from another gas pump
                        Optional<BlockPos> existingGasPumpPos = SyncedPlayerData.getGasPumpPos(playerIn);
                        if(existingGasPumpPos.isPresent() && !existingGasPumpPos.get().equals(pos))
                        {
                            // Player already has a nozzle from another gas pump, don't allow taking a new one
                            return false;
                        }
                        gasPump.setFuelingEntity(playerIn);
                        worldIn.playSound(null, pos, ModSounds.NOZZLE_PICK_UP, SoundCategory.BLOCKS, 1.0F, 1.0F);
                    }
                }
            }
            else
            {
                ItemStack stack = playerIn.getHeldItem(hand);

                if(FluidUtil.interactWithFluidHandler(playerIn, hand, worldIn, pos, face))
                {
                    return true;
                }

                if(stack.getItem() instanceof ItemJerryCan)
                {
                    ItemJerryCan jerryCan = (ItemJerryCan) stack.getItem();

                    if(jerryCan.isFull(stack))
                    {
                        return false;
                    }

                    TileEntity tileEntity = worldIn.getTileEntity(pos);
                    if(tileEntity != null && tileEntity.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, null))
                    {
                        IFluidHandler handler = tileEntity.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, null);
                        if(handler != null)
                        {
                            if(handler instanceof FluidTank)
                            {
                                FluidTank tank = (FluidTank) handler;
                                if(tank.getFluid() != null && tank.getFluid().getFluid() != ModFluids.FUELIUM)
                                {
                                    return false;
                                }

                                FluidStack fluidStack = handler.drain(50, true);
                                if(fluidStack != null)
                                {
                                    int remaining = jerryCan.fill(stack, fluidStack.amount);
                                    if(remaining > 0)
                                    {
                                        fluidStack.amount = remaining;
                                        handler.fill(fluidStack, true);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return true;
    }

    @Override
    public boolean canPlaceBlockAt(World worldIn, BlockPos pos)
    {
        return worldIn.isAirBlock(pos) && worldIn.isAirBlock(pos.up());
    }

    @Override
    public void onBlockPlacedBy(World worldIn, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack)
    {
        super.onBlockPlacedBy(worldIn, pos, state, placer, stack);
        
        // Create top block
        worldIn.setBlockState(pos.up(), state.withProperty(TOP, true));
        
        // Transfer color from ItemStack NBT to tile entities
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
                // Set color on bottom tile entity (tank)
                TileEntity bottomTile = worldIn.getTileEntity(pos);
                if(bottomTile instanceof TileEntityGasPumpTank)
                {
                    ((TileEntityGasPumpTank) bottomTile).setColor(color);
                }
                
                // Set color on top tile entity (pump)
                TileEntity topTile = worldIn.getTileEntity(pos.up());
                if(topTile instanceof TileEntityGasPump)
                {
                    ((TileEntityGasPump) topTile).setColor(color);
                }
            }
        }
    }

    @Override
    public Item getItemDropped(IBlockState state, java.util.Random rand, int fortune)
    {
        return net.minecraft.init.Items.AIR;
    }

    @Override
    public boolean removedByPlayer(IBlockState state, World world, BlockPos pos, EntityPlayer player, boolean willHarvest)
    {
        if(!world.isRemote && !player.capabilities.isCreativeMode)
        {
            // Get the bottom block position (where the tank is)
            BlockPos bottomPos = state.getValue(TOP) ? pos.down() : pos;
            TileEntity bottomTile = world.getTileEntity(bottomPos);
            
            if(bottomTile instanceof TileEntityGasPumpTank)
            {
                TileEntityGasPumpTank tank = (TileEntityGasPumpTank) bottomTile;
                ItemStack drop = new ItemStack(Item.getItemFromBlock(this));
                
                // Save color to ItemStack NBT if it has a color
                if(tank.hasColor())
                {
                    if(!drop.hasTagCompound())
                    {
                        drop.setTagCompound(new NBTTagCompound());
                    }
                    // Save color in root NBT (for recipe compatibility)
                    drop.getTagCompound().setInteger("color", tank.getColor());
                    
                    // Also save in BlockEntityTag (for proper item rendering)
                    NBTTagCompound tileEntityTag = new NBTTagCompound();
                    tank.writeToNBT(tileEntityTag);
                    tileEntityTag.removeTag("x");
                    tileEntityTag.removeTag("y");
                    tileEntityTag.removeTag("z");
                    tileEntityTag.removeTag("id");
                    drop.getTagCompound().setTag("BlockEntityTag", tileEntityTag);
                }
                
                world.spawnEntity(new EntityItem(world, bottomPos.getX() + 0.5, bottomPos.getY() + 0.5, bottomPos.getZ() + 0.5, drop));
            }
        }
        
        if(state.getValue(TOP))
        {
            if(world.getBlockState(pos.down()).getBlock() instanceof BlockGasPump)
            {
                world.setBlockToAir(pos.down());
            }
        }
        else
        {
            if(world.getBlockState(pos.up()).getBlock() instanceof BlockGasPump)
            {
                world.setBlockToAir(pos.up());
            }
        }
        
        return super.removedByPlayer(state, world, pos, player, willHarvest);
    }

    @Override
    public void onBlockHarvested(World worldIn, BlockPos pos, IBlockState state, EntityPlayer player)
    {
        if(state.getValue(TOP))
        {
            if(worldIn.getBlockState(pos.down()).getBlock() instanceof BlockGasPump)
            {
                worldIn.setBlockToAir(pos.down());
            }
        }
        else
        {
            if(worldIn.getBlockState(pos.up()).getBlock() instanceof BlockGasPump)
            {
                worldIn.setBlockToAir(pos.up());
            }
        }
    }

    @Override
    public int getMetaFromState(IBlockState state)
    {
        return state.getValue(FACING).getHorizontalIndex() + (state.getValue(TOP) ? 4 : 0);
    }

    @Override
    public IBlockState getStateFromMeta(int meta)
    {
        return this.getDefaultState().withProperty(FACING, EnumFacing.getHorizontal(meta % 4)).withProperty(TOP, meta / 4 > 0);
    }

    @Override
    protected BlockStateContainer createBlockState()
    {
        return new BlockStateContainer(this, FACING, TOP);
    }

    @Override
    public BlockRenderLayer getBlockLayer()
    {
        return BlockRenderLayer.CUTOUT;
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
        if(state.getValue(TOP))
        {
            return new TileEntityGasPump();
        }
        return new TileEntityGasPumpTank();
    }
}