package com.mrcrayfish.vehicle.entity.vehicle;

import com.mrcrayfish.vehicle.client.EntityRaytracer.IEntityRaytraceable;
import com.mrcrayfish.vehicle.entity.EngineType;
import com.mrcrayfish.vehicle.entity.EntityMotorcycle;
import com.mrcrayfish.vehicle.init.ModSounds;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import com.mrcrayfish.vehicle.VehicleConfig;
import com.mrcrayfish.vehicle.entity.WheelType;

public class EntityBicycle extends EntityMotorcycle implements IEntityRaytraceable
{
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
}

//TODO: Add locking to bycicle. add more wheel types.