package com.mrcrayfish.vehicle.entity.vehicle;

import com.mrcrayfish.vehicle.client.EntityRaytracer.IEntityRaytraceable;
import com.mrcrayfish.vehicle.entity.EngineType;
import com.mrcrayfish.vehicle.entity.EntityMotorcycle;
import com.mrcrayfish.vehicle.init.ModSounds;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import com.mrcrayfish.vehicle.VehicleConfig;

public class EntityBicycle extends EntityMotorcycle implements IEntityRaytraceable
{
    public EntityBicycle(World worldIn)
    {
        super(worldIn);
        this.setMaxSpeed(10F);
        this.setTurnSensitivity(12);
        this.setFuelCapacity(0F);
        this.setFuelConsumption(0F);
    }

    @Override
    public SoundEvent getMovingSound()
    {
        return ModSounds.GO_KART_ENGINE_MONO;
    }

    @Override
    public SoundEvent getRidingSound()
    {
        return ModSounds.GO_KART_ENGINE_STEREO;
    }

    @Override
    public EngineType getEngineType()
    {
        return EngineType.NONE;
    }

    @Override
    public float getMinEnginePitch()
    {
        return 0.5F;
    }

    @Override
    public float getMaxEnginePitch()
    {
        return 1.8F;
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
}

//TODO: Add locking to bycicle, change ride and honk sounds.