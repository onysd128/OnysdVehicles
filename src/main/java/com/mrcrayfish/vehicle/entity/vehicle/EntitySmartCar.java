package com.mrcrayfish.vehicle.entity.vehicle;

import com.mrcrayfish.vehicle.client.EntityRaytracer.IEntityRaytraceable;
import com.mrcrayfish.vehicle.entity.EngineType;
import com.mrcrayfish.vehicle.entity.EntityLandVehicle;
import com.mrcrayfish.vehicle.init.ModSounds;
import net.minecraft.util.SoundEvent;
import net.minecraft.world.World;
import com.mrcrayfish.vehicle.VehicleConfig;

/**
 * Author: MrCrayfish
 */
public class EntitySmartCar extends EntityLandVehicle implements IEntityRaytraceable
{
    public EntitySmartCar(World worldIn)
    {
        super(worldIn);
        this.setMaxSpeed(15F);
        this.setTurnSensitivity(12);
        this.setSize(1.85F, 1.15F);
        this.stepHeight = 1F;
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
        return EngineType.SMALL_MOTOR;
    }

    @Override
    public float getMinEnginePitch()
    {
        return 0.8F;
    }

    @Override
    public float getMaxEnginePitch()
    {
        return 1.6F;
    }

    @Override
    public boolean canBeColored()
    {
        return true;
    }

    @Override
    public boolean canTowTrailer()
    {
        return true;
    }

    @Override
    public boolean canMountTrailer()
    {
        return false;
    }

    @Override
    public boolean isLockable()
    {
        return VehicleConfig.SERVER.VEHICLES.smartCarKey;
    }
}
