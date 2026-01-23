package com.mrcrayfish.vehicle.entity.vehicle;

import com.mrcrayfish.vehicle.client.EntityRaytracer.IEntityRaytraceable;
import com.mrcrayfish.vehicle.entity.EngineType;
import com.mrcrayfish.vehicle.entity.EntityMotorcycle;
import com.mrcrayfish.vehicle.init.ModItems;
import com.mrcrayfish.vehicle.init.ModSounds;
import com.mrcrayfish.vehicle.item.IDyeable;
import com.mrcrayfish.vehicle.util.CommonUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import com.mrcrayfish.vehicle.VehicleConfig;
import com.mrcrayfish.vehicle.entity.WheelType;

public class EntityBicycle extends EntityMotorcycle implements IEntityRaytraceable
{
    private static final DataParameter<Boolean> BICYCLE_LOCKED = EntityDataManager.createKey(EntityBicycle.class, DataSerializers.BOOLEAN);

    public EntityBicycle(World worldIn)
    {
        super(worldIn);
        this.setWheelType(WheelType.BICYCLE);
        this.setMaxSpeed(10F);
        this.setTurnSensitivity(12);
        this.setFuelCapacity(0F);
        this.setFuelConsumption(0F);
    }

    @Override
    public void entityInit()
    {
        super.entityInit();
        this.dataManager.register(BICYCLE_LOCKED, false);
    }

    @Override
    public SoundEvent getMovingSound()
    {
        return ModSounds.BICYCLE_RIDING;
    }

    @Override
    public SoundEvent getRidingSound()
    {
        return ModSounds.BICYCLE_RIDING;
    }

    @Override
    public EngineType getEngineType()
    {
        return EngineType.NONE;
    }

    @Override
    public float getMinEnginePitch()
    {
        return 0.9F;
    }

    @Override
    public float getMaxEnginePitch()
    {
        return 1.2F;
    }

    @Override
    public boolean shouldShowEngineSmoke()
    {
        return false;
    }

    @Override
    public Vec3d getEngineSmokePosition()
    {
        return new Vec3d(0, 0.55, 0);
    }

    @Override
    public boolean canBeColored()
    {
        return true;
    }

    @Override
    public boolean shouldRenderEngine()
    {
        return false;
    }

    @Override
    public boolean shouldRenderFuelPort()
    {
        return false;
    }

    @Override
    public boolean isLockable()
    {
        return false;
    }

    @Override
    public boolean requiresFuel()
    {
        return false;
    }

    @Override
    public boolean canWheelie()
    {
        return false;
    }

    @Override
    public boolean canCharge()
    {
        return false;
    }
    @Override
    public SoundEvent getHornSound()
    {
        return ModSounds.BICYCLE_BELL;
    }
    @Override
    public SoundEvent getHornRidingSound()
    {
        return ModSounds.BICYCLE_BELL;
    }

    @Override
    public void onUpdateVehicle()
    {
        super.onUpdateVehicle();
        
        // Consume player hunger when riding bicycle, similar to sprinting
        if(VehicleConfig.SERVER.bikeHungerConsumption)
        {
            Entity controllingPassenger = this.getControllingPassenger();
            if(controllingPassenger instanceof EntityPlayer && !world.isRemote)
            {
                EntityPlayer player = (EntityPlayer) controllingPassenger;
                // Only consume hunger if player is not in creative mode and bicycle is moving
                if(!player.isCreative())
                {
                    float currentSpeed = Math.abs(this.getSpeed());
                    if(currentSpeed > 0.1F)
                    {
                        // Calculate exhaustion based on speed (similar to sprinting)
                        // Sprinting uses 0.1F per tick, we'll use slightly less for cycling
                        float speedFactor = Math.min(currentSpeed / this.getMaxSpeed(), 1.0F);
                        float exhaustion = 0.08F * speedFactor; // Max 0.08F when at full speed
                        player.getFoodStats().addExhaustion(exhaustion);
                    }
                }
            }
        }
    }

    public boolean isBicycleLocked()
    {
        return this.dataManager.get(BICYCLE_LOCKED);
    }

    public void setBicycleLocked(boolean locked)
    {
        this.dataManager.set(BICYCLE_LOCKED, locked);
    }

    @Override
    public boolean canDrive()
    {
        if(this.isBicycleLocked())
        {
            return false;
        }
        return super.canDrive();
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound compound)
    {
        super.readEntityFromNBT(compound);
        if(compound.hasKey("bicycleLocked", Constants.NBT.TAG_BYTE))
        {
            this.setBicycleLocked(compound.getBoolean("bicycleLocked"));
        }
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound compound)
    {
        super.writeEntityToNBT(compound);
        compound.setBoolean("bicycleLocked", this.isBicycleLocked());
    }

    @Override
    public boolean processInitialInteract(EntityPlayer player, EnumHand hand)
    {
        ItemStack stack = player.getHeldItem(hand);
        if(!world.isRemote)
        {
            // Handle locking with bicycle lock item
            if(!stack.isEmpty() && stack.getItem() == ModItems.BICYCLE_LOCK)
            {
                // Check ownership: only owner can lock, or anyone if no owner
                if(!this.isOwner(player))
                {
                    CommonUtils.sendInfoMessage(player, "vehicle.status.bicycle_lock_invalid_owner");
                    return false;
                }

                // Set owner if not already set
                this.setOwner(player.getUniqueID());

                // Only allow locking if bicycle is not already locked
                if(!this.isBicycleLocked())
                {
                    // Create key with vehicleId in NBT
                    ItemStack keyStack = new ItemStack(ModItems.KEY);
                    NBTTagCompound tag = CommonUtils.getItemTagCompound(keyStack);
                    tag.setUniqueId("vehicleId", this.getUniqueID());
                    
                    // Copy color from lock to key if lock has color
                    if(stack.getItem() instanceof IDyeable)
                    {
                        IDyeable dyeableLock = (IDyeable) stack.getItem();
                        if(dyeableLock.hasColor(stack))
                        {
                            IDyeable dyeableKey = (IDyeable) ModItems.KEY;
                            dyeableKey.setColor(keyStack, dyeableLock.getColor(stack));
                        }
                    }
                    
                    // Replace lock item with key in player's hand
                    if(hand == EnumHand.MAIN_HAND)
                    {
                        player.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, keyStack);
                    }
                    else
                    {
                        player.setItemStackToSlot(EntityEquipmentSlot.OFFHAND, keyStack);
                    }
                    
                    this.setBicycleLocked(true);
                    CommonUtils.sendInfoMessage(player, "vehicle.status.bicycle_locked");
                    return true;
                }
            }
            // Handle unlocking with key
            else if(!stack.isEmpty() && stack.getItem() == ModItems.KEY)
            {
                // Check if bicycle is locked
                if(!this.isBicycleLocked())
                {
                    return super.processInitialInteract(player, hand);
                }

                // Check ownership: only owner can unlock
                if(!this.isOwner(player))
                {
                    CommonUtils.sendInfoMessage(player, "vehicle.status.bicycle_lock_invalid_owner");
                    return false;
                }

                // Check if key's vehicleId matches bicycle UUID
                NBTTagCompound tag = CommonUtils.getItemTagCompound(stack);
                if(tag.hasUniqueId("vehicleId") && this.getUniqueID().equals(tag.getUniqueId("vehicleId")))
                {
                    // Valid key: spawn lock item, remove key, unlock bicycle
                    ItemStack lockStack = new ItemStack(ModItems.BICYCLE_LOCK);
                    
                    // Copy color from key to lock if key has color
                    if(stack.getItem() instanceof IDyeable)
                    {
                        IDyeable dyeableKey = (IDyeable) stack.getItem();
                        if(dyeableKey.hasColor(stack))
                        {
                            IDyeable dyeableLock = (IDyeable) ModItems.BICYCLE_LOCK;
                            dyeableLock.setColor(lockStack, dyeableKey.getColor(stack));
                        }
                    }
                    
                    EntityItem lockItem = new EntityItem(world, this.posX, this.posY + 0.5, this.posZ, lockStack);
                    lockItem.setPickupDelay(0);
                    world.spawnEntity(lockItem);
                    
                    // Remove key from player's hand
                    if(hand == EnumHand.MAIN_HAND)
                    {
                        player.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, ItemStack.EMPTY);
                    }
                    else
                    {
                        player.setItemStackToSlot(EntityEquipmentSlot.OFFHAND, ItemStack.EMPTY);
                    }
                    
                    this.setBicycleLocked(false);
                    CommonUtils.sendInfoMessage(player, "vehicle.status.bicycle_unlocked");
                    return true;
                }
                else
                {
                    // Invalid key
                    CommonUtils.sendInfoMessage(player, "vehicle.status.bicycle_key_invalid");
                    return false;
                }
            }
        }
        return super.processInitialInteract(player, hand);
    }
}

//TODO: Add locking to bycicle. add more wheel types.