package com.mrcrayfish.vehicle.client.render.vehicle;

import com.mrcrayfish.vehicle.client.SpecialModels;
import com.mrcrayfish.vehicle.client.render.AbstractRenderVehicle;
import com.mrcrayfish.vehicle.common.Seat;
import com.mrcrayfish.vehicle.entity.VehicleProperties;
import com.mrcrayfish.vehicle.entity.vehicle.EntityBicycle;
import com.mrcrayfish.vehicle.util.RenderUtil;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.Vec3d;

import com.mrcrayfish.vehicle.entity.WheelType;
import com.mrcrayfish.vehicle.init.ModItems;
import net.minecraft.item.ItemStack;

public class RenderBicycle extends AbstractRenderVehicle<EntityBicycle>
{
    @Override
    public void render(EntityBicycle entity, float partialTicks)
    {
        this.renderDamagedPart(entity, SpecialModels.BICYCLE_BODY.getModel());

        float wheelScale = 2.0F;

        //Render the handles bars
        GlStateManager.pushMatrix();
        {
            GlStateManager.translate(0, 0, 10.5 * 0.0625);
            GlStateManager.rotate(-22.5F, 1, 0, 0);

            float wheelAngle = entity.prevRenderWheelAngle + (entity.renderWheelAngle - entity.prevRenderWheelAngle) * partialTicks;
            float wheelAngleNormal = wheelAngle / 45F;
            float turnRotation = wheelAngleNormal * 25F;

            GlStateManager.rotate(turnRotation, 0, 1, 0);
            GlStateManager.rotate(22.5F, 1, 0, 0);
            GlStateManager.translate(0, 0, -10.5 * 0.0625);

            this.renderDamagedPart(entity, SpecialModels.BICYCLE_HANDLE_BAR.getModel());

            if(entity.hasWheels())
            {
                // Render front wheel
                GlStateManager.pushMatrix();
                {
                    GlStateManager.translate(0, -0.38 + 1.7 * 0.0625, 9 * 0.0625);
                    float frontWheelSpin = entity.prevFrontWheelRotation + (entity.frontWheelRotation - entity.prevFrontWheelRotation) * partialTicks;
                    if(entity.isMoving())
                    {
                        GlStateManager.rotate(-frontWheelSpin, 1, 0, 0);
                    }
                    GlStateManager.scale(wheelScale/2, wheelScale, wheelScale);
                    GlStateManager.rotate(180F, 0, 1, 0);
                    ItemStack wheelStack = new ItemStack(ModItems.WHEEL, 1, WheelType.BICYCLE.ordinal());
                    IBakedModel model = RenderUtil.getModel(wheelStack);
                    if(model != null)
                    {
                        RenderUtil.renderColoredModel(model, ItemCameraTransforms.TransformType.NONE, entity.getWheelColor());
                    }
                }
                GlStateManager.popMatrix();
            }
        }
        GlStateManager.popMatrix();

        // Render rear wheel (outside of handlebar rotation)
        if(entity.hasWheels())
        {
            GlStateManager.pushMatrix();
            {
                GlStateManager.translate(0, -0.38 + 1.7 * 0.0625, -9.2 * 0.0625);
                float rearWheelSpin = entity.prevRearWheelRotation + (entity.rearWheelRotation - entity.prevRearWheelRotation) * partialTicks;
                if(entity.isMoving())
                {
                    GlStateManager.rotate(-rearWheelSpin, 1, 0, 0);
                }
                GlStateManager.scale(wheelScale/2, wheelScale, wheelScale);
                GlStateManager.rotate(180F, 0, 1, 0);
                ItemStack wheelStack = new ItemStack(ModItems.WHEEL, 1, WheelType.BICYCLE.ordinal());
                IBakedModel model = RenderUtil.getModel(wheelStack);
                if(model != null)
                {
                    RenderUtil.renderColoredModel(model, ItemCameraTransforms.TransformType.NONE, entity.getWheelColor());
                }
            }
            GlStateManager.popMatrix();
        }
    }

    @Override
    public void applyPlayerModel(EntityBicycle entity, EntityPlayer player, ModelPlayer model, float partialTicks)
    {
        float wheelAngle = entity.prevRenderWheelAngle + (entity.renderWheelAngle - entity.prevRenderWheelAngle) * partialTicks;
        float wheelAngleNormal = wheelAngle / 45F;
        float turnRotation = wheelAngleNormal * 8F;
        model.bipedRightArm.rotateAngleX = (float) Math.toRadians(-55F - turnRotation);
        model.bipedLeftArm.rotateAngleX = (float) Math.toRadians(-55F + turnRotation);
        model.bipedRightArm.offsetZ = -0.1F * wheelAngleNormal;
        model.bipedLeftArm.offsetZ = 0.1F * wheelAngleNormal;
        model.bipedRightLeg.rotateAngleX = (float) Math.toRadians(-65F);
        model.bipedRightLeg.rotateAngleY = (float) Math.toRadians(30F);
        model.bipedLeftLeg.rotateAngleX = (float) Math.toRadians(-65F);
        model.bipedLeftLeg.rotateAngleY = (float) Math.toRadians(-30F);
    }

    @Override
    public void applyPlayerRender(EntityBicycle entity, EntityPlayer player, float partialTicks)
    {
        int index = entity.getSeatTracker().getSeatIndex(player.getUniqueID());
        if(index != -1)
        {
            VehicleProperties properties = entity.getProperties();
            Seat seat = properties.getSeats().get(index);
            Vec3d seatVec = seat.getPosition().addVector(0, properties.getAxleOffset() + properties.getWheelOffset(), 0).scale(properties.getBodyPosition().getScale());
            seatVec = new Vec3d(-seatVec.x, seatVec.y, seatVec.z);
            seatVec = seatVec.scale(0.0625);
            double scale = 32.0 / 30.0;
            double offsetX = seatVec.x * scale;
            double offsetY = (seatVec.y + player.getYOffset()) * scale + 24 * 0.0625; //Player is 2 blocks high tall but renders at 1.8 blocks tall
            double offsetZ = -seatVec.z * scale;
            GlStateManager.translate(offsetX, offsetY, offsetZ);
            float currentSpeedNormal = (entity.prevCurrentSpeed + (entity.currentSpeed - entity.prevCurrentSpeed) * partialTicks) / entity.getMaxSpeed();
            float turnAngleNormal = (entity.prevTurnAngle + (entity.turnAngle - entity.prevTurnAngle) * partialTicks) / 45F;
            GlStateManager.rotate(turnAngleNormal * currentSpeedNormal * 20F, 0, 0, 1);
            GlStateManager.translate(-offsetX, -offsetY, -offsetZ);
        }
    }
}
