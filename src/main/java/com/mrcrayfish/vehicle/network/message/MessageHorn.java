package com.mrcrayfish.vehicle.network.message;

import com.mrcrayfish.vehicle.entity.EntityPoweredVehicle;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class MessageHorn implements IMessage, IMessageHandler<MessageHorn, IMessage>
{
	public MessageHorn() {}

	@Override
	public void toBytes(ByteBuf buf)
	{
		// No data needed for one-time sound
	}

	@Override
	public void fromBytes(ByteBuf buf)
	{
		// No data needed for one-time sound
	}

	@Override
	public IMessage onMessage(MessageHorn message, MessageContext ctx)
	{
	    FMLCommonHandler.instance().getWorldThread(ctx.netHandler).addScheduledTask(() ->
        {
            EntityPlayer player = ctx.getServerHandler().player;
            Entity riding = player.getRidingEntity();
            if(riding instanceof EntityPoweredVehicle)
            {
                EntityPoweredVehicle vehicle = (EntityPoweredVehicle) riding;
                // Only allow the driver (controlling passenger) to use the horn
                if(vehicle.getControllingPassenger() == player)
                {
                    SoundEvent hornSound = vehicle.getHornRidingSound();
                    if(hornSound != null)
                    {
                        // Play sound on server to sync with all clients
                        vehicle.world.playSound(null, vehicle.posX, vehicle.posY, vehicle.posZ, hornSound, SoundCategory.NEUTRAL, 1.0F, 0.85F);
                    }
                }
            }
        });
		return null;
	}
}
