package com.mrcrayfish.vehicle.client;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mrcrayfish.vehicle.VehicleConfig;
import com.mrcrayfish.vehicle.common.CommonEvents;
import com.mrcrayfish.vehicle.common.entity.PartPosition;
import com.mrcrayfish.vehicle.common.entity.SyncedPlayerData;
import com.mrcrayfish.vehicle.entity.EntityPoweredVehicle;
import com.mrcrayfish.vehicle.entity.EntityVehicle;
import com.mrcrayfish.vehicle.entity.VehicleProperties;
import com.mrcrayfish.vehicle.entity.trailer.*;
import com.mrcrayfish.vehicle.entity.vehicle.*;
import com.mrcrayfish.vehicle.init.ModItems;
import com.mrcrayfish.vehicle.item.ItemJerryCan;
import com.mrcrayfish.vehicle.network.PacketHandler;
import com.mrcrayfish.vehicle.network.message.MessageFuelVehicle;
import com.mrcrayfish.vehicle.network.message.MessageInteractKey;
import com.mrcrayfish.vehicle.network.message.MessagePickupVehicle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.RayTraceResult.Type;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;
import javax.vecmath.AxisAngle4d;
import javax.vecmath.Matrix4d;
import javax.vecmath.Vector3d;
import javax.vecmath.Vector4d;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Author: Phylogeny
 * <p>
 * This class allows precise ratraces to be performed on the rendered model item parts, as well as on additional interaction boxes, of entities.
 */
@EventBusSubscriber(Side.CLIENT)
public class EntityRaytracer
{
    /**
     * Whether or not this class has been initialized
     */
    private static boolean initialized;

    /**
     * Maps raytraceable entities to maps, which map rendered model item parts to the triangles that comprise static versions of the faces of their BakedQuads
     */
    private static final Map<Class<? extends IEntityRaytraceable>, Map<RayTracePart, TriangleRayTraceList>> entityRaytraceTrianglesStatic = Maps.newHashMap();

    /**
     * Maps raytraceable entities to maps, which map rendered model item parts to the triangles that comprise dynamic versions of the faces of their BakedQuads
     */
    private static final Map<Class<? extends IEntityRaytraceable>, Map<RayTracePart, TriangleRayTraceList>> entityRaytraceTrianglesDynamic = Maps.newHashMap();

    /**
     * Contains all data in entityRaytraceTrianglesStatic and entityRaytraceTrianglesDynamic
     */
    private static final Map<Class<? extends IEntityRaytraceable>, Map<RayTracePart, TriangleRayTraceList>> entityRaytraceTriangles = new HashMap<>();

    /**
     * Scales and offsets for rendering the entities in crates
     */
    private static final Map<Class<? extends Entity>, Pair<Float, Float>> entityCrateScalesAndOffsets = new HashMap<>();
    private static final Pair<Float, Float> SCALE_AND_OFFSET_DEFAULT = new ImmutablePair<>(0.25F, 0.0F);

    /**
     * Nearest common superclass shared by all raytraceable entity classes
     */
    private static Class<? extends Entity> entityRaytraceSuperclass;

    /**
     * NBT key for a name string tag that parts stacks with NBT tags will have.
     * <p>
     * <strong>Example:</strong> <code>partStack.getTagCompound().getString(EntityRaytracer.PART_NAME)</code>
     */
    public static final String PART_NAME = "nameRaytrace";

    /**
     * The result of clicking and holding on a continuously interactable raytrace part. Every tick that this is not null,
     * both the raytrace and the interaction of this part will be performed.
     */
    private static RayTraceResultRotated continuousInteraction;

    /**
     * The object returned by the interaction function of the result of clicking and holding on a continuously interactable raytrace part.
     */
    private static Object continuousInteractionObject;

    /**
     * Counts the number of ticks that a continuous interaction has been performed for
     */
    private static int continuousInteractionTickCounter;

    /**
     * Clears registration data and triggers re-registration in the next client tick
     */
    public static void clearDataForReregistration()
    {
        entityRaytraceTrianglesStatic.clear();
        entityRaytraceTrianglesDynamic.clear();
        entityRaytraceTriangles.clear();
        entityCrateScalesAndOffsets.clear();
        entityRaytraceSuperclass = null;
        initialized = false;
    }

    /**
     * Getter for the current continuously interacting raytrace result
     * 
     * @return result of the raytrace
     */
    @Nullable
    public static RayTraceResultRotated getContinuousInteraction()
    {
        return continuousInteraction;
    }

    /**
     * Getter for the object returned by the current continuously interacting raytrace result's interaction function
     * 
     * @return interaction function result
     */
    @Nullable
    public static Object getContinuousInteractionObject()
    {
        return continuousInteractionObject;
    }

    /**
     * Checks if fuel can be transferred from a jerry can to a powered vehicle, and sends a packet to do so every other tick, if it can
     * 
     * @return whether or not fueling can continue
     */
    public static final Function<RayTraceResultRotated, EnumHand> FUNCTION_FUELING = (rayTraceResult) ->
    {
        EntityPlayer player = Minecraft.getMinecraft().player;
        if(SyncedPlayerData.getGasPumpPos(player).isPresent() && ControllerEvents.isRightClicking())
        {
            Entity entity = rayTraceResult.entityHit;
            if(entity instanceof EntityPoweredVehicle)
            {
                EntityPoweredVehicle poweredVehicle = (EntityPoweredVehicle) entity;
                if(poweredVehicle.requiresFuel() && poweredVehicle.getCurrentFuel() < poweredVehicle.getFuelCapacity())
                {
                    if(continuousInteractionTickCounter % 2 == 0)
                    {
                        PacketHandler.INSTANCE.sendToServer(new MessageFuelVehicle(Minecraft.getMinecraft().player, EnumHand.MAIN_HAND, rayTraceResult.entityHit));
                    }
                    return EnumHand.MAIN_HAND;
                }
            }
        }

        for(EnumHand hand : EnumHand.values())
        {
            ItemStack stack = Minecraft.getMinecraft().player.getHeldItem(hand);
            if(!stack.isEmpty() && stack.getItem() instanceof ItemJerryCan && ControllerEvents.isRightClicking())
            {
                Entity entity = rayTraceResult.entityHit;
                if(entity instanceof EntityPoweredVehicle)
                {
                    EntityPoweredVehicle poweredVehicle = (EntityPoweredVehicle) entity;
                    if(poweredVehicle.requiresFuel() && poweredVehicle.getCurrentFuel() < poweredVehicle.getFuelCapacity())
                    {
                        int fuel = ((ItemJerryCan) stack.getItem()).getCurrentFuel(stack);
                        if(fuel > 0)
                        {
                            if(continuousInteractionTickCounter % 2 == 0)
                            {
                                PacketHandler.INSTANCE.sendToServer(new MessageFuelVehicle(Minecraft.getMinecraft().player, hand, entity));
                            }
                            return hand;
                        }
                    }
                }
            }
        }
        return null;
    };

    /**
     * Create static triangles for raytraceable entities
     * <p>
     * For a static raytrace, all static GL operation performed on each item part during rendering must be accounted
     * for by performing the same matrix transformations on the triangles that will comprise the faces their BakedQuads
     */
    private static void registerEntitiesStatic()
    {
        // Aluminum boat
        List<MatrixTransformation> aluminumBoatTransformGlobal = new ArrayList<>();
        createBodyTransforms(aluminumBoatTransformGlobal, EntityAluminumBoat.class);
        HashMap<RayTracePart, List<MatrixTransformation>> aluminumBoatParts = Maps.newHashMap();
        createTransformListForPart(SpecialModels.ALUMINUM_BOAT_BODY, aluminumBoatParts, aluminumBoatTransformGlobal);
        createFuelablePartTransforms(SpecialModels.FUEL_PORT_CLOSED, EntityAluminumBoat.class, aluminumBoatParts, aluminumBoatTransformGlobal);
        createKeyPortTransforms(SpecialModels.KEY_HOLE, EntityAluminumBoat.class, aluminumBoatParts, aluminumBoatTransformGlobal);
        registerEntityStatic(EntityAluminumBoat.class, aluminumBoatParts);

        // ATV
        List<MatrixTransformation> atvTransformGlobal = Lists.newArrayList();
        createBodyTransforms(atvTransformGlobal, EntityATV.class);
        HashMap<RayTracePart, List<MatrixTransformation>> atvParts = Maps.newHashMap();
        createTransformListForPart(SpecialModels.ATV_BODY, atvParts, atvTransformGlobal);
        createTransformListForPart(SpecialModels.ATV_HANDLE_BAR, atvParts, atvTransformGlobal,
                MatrixTransformation.createTranslation(0, 0.3375, 0.25),
                MatrixTransformation.createRotation(-45, 1, 0, 0),
                MatrixTransformation.createTranslation(0, -0.025, 0));
        createTransformListForPart(SpecialModels.TOW_BAR, atvParts,
                MatrixTransformation.createRotation(180, 0, 1, 0),
                MatrixTransformation.createTranslation(0.0, 0.5, 1.05));
        createFuelablePartTransforms(SpecialModels.FUEL_PORT_2_CLOSED, EntityATV.class, atvParts, atvTransformGlobal);
        createKeyPortTransforms(SpecialModels.KEY_HOLE, EntityATV.class, atvParts, atvTransformGlobal);
        registerEntityStatic(EntityATV.class, atvParts);

        // Bumper car
        List<MatrixTransformation> bumperCarTransformGlobal = Lists.newArrayList();
        createBodyTransforms(bumperCarTransformGlobal, EntityBumperCar.class);
        HashMap<RayTracePart, List<MatrixTransformation>> bumperCarParts = Maps.newHashMap();
        createTransformListForPart(SpecialModels.BUMPER_CAR_BODY, bumperCarParts, bumperCarTransformGlobal);
        createTransformListForPart(SpecialModels.GO_KART_STEERING_WHEEL, bumperCarParts, bumperCarTransformGlobal,
                MatrixTransformation.createTranslation(0, 0.2, 0),
                MatrixTransformation.createRotation(-45, 1, 0, 0),
                MatrixTransformation.createTranslation(0, -0.02, 0),
                MatrixTransformation.createScale(0.9));
        createFuelablePartTransforms(SpecialModels.FUEL_PORT_CLOSED, EntityBumperCar.class, bumperCarParts, bumperCarTransformGlobal);
        createKeyPortTransforms(SpecialModels.KEY_HOLE, EntityBumperCar.class, bumperCarParts, bumperCarTransformGlobal);
        registerEntityStatic(EntityBumperCar.class, bumperCarParts);

        //Dirt bike
        List<MatrixTransformation> dirtBikeTransformGlobal = Lists.newArrayList();
        createBodyTransforms(dirtBikeTransformGlobal, EntityDirtBike.class);
        HashMap<RayTracePart, List<MatrixTransformation>> dirtBikeParts = Maps.newHashMap();
        createTransformListForPart(SpecialModels.DIRT_BIKE_BODY, dirtBikeParts, dirtBikeTransformGlobal);
        createTransformListForPart(SpecialModels.DIRT_BIKE_HANDLES, dirtBikeParts, dirtBikeTransformGlobal);
        createFuelablePartTransforms(SpecialModels.FUEL_PORT_2_CLOSED, EntityDirtBike.class, dirtBikeParts, dirtBikeTransformGlobal);
        createKeyPortTransforms(SpecialModels.KEY_HOLE, EntityDirtBike.class, dirtBikeParts, dirtBikeTransformGlobal);
        registerEntityStatic(EntityDirtBike.class, dirtBikeParts);

        // Dune buggy
        // List<MatrixTransformation> duneBuggyTransformGlobal = Lists.newArrayList();
        // createBodyTransforms(duneBuggyTransformGlobal, EntityDuneBuggy.class);
        // HashMap<RayTracePart, List<MatrixTransformation>> duneBuggyParts = Maps.newHashMap();
        // createTransformListForPart(SpecialModels.DUNE_BUGGY_BODY, duneBuggyParts, duneBuggyTransformGlobal);
        // createTransformListForPart(SpecialModels.DUNE_BUGGY_HANDLE_BAR, duneBuggyParts, duneBuggyTransformGlobal,
        //         MatrixTransformation.createTranslation(0, 0, -0.0046875));
        // createFuelablePartTransforms(SpecialModels.FUEL_PORT_CLOSED, EntityDuneBuggy.class, duneBuggyParts, duneBuggyTransformGlobal);
        // registerEntityStatic(EntityDuneBuggy.class, duneBuggyParts);

        // Go kart
        List<MatrixTransformation> goKartTransformGlobal = Lists.newArrayList();
        createBodyTransforms(goKartTransformGlobal, EntityGoKart.class);
        HashMap<RayTracePart, List<MatrixTransformation>> goKartParts = Maps.newHashMap();
        createTransformListForPart(SpecialModels.GO_KART_BODY, goKartParts, goKartTransformGlobal);
        createTransformListForPart(SpecialModels.GO_KART_STEERING_WHEEL, goKartParts, goKartTransformGlobal,
                MatrixTransformation.createTranslation(0, 0.09, 0.49),
                MatrixTransformation.createRotation(-45, 1, 0, 0),
                MatrixTransformation.createTranslation(0, -0.02, 0),
                MatrixTransformation.createScale(0.9));
        createPartTransforms(ModItems.SMALL_ENGINE, VehicleProperties.getProperties(EntityGoKart.class).getEnginePosition(), goKartParts, goKartTransformGlobal, FUNCTION_FUELING);
        createKeyPortTransforms(SpecialModels.KEY_HOLE, EntityGoKart.class, goKartParts, goKartTransformGlobal);
        registerEntityStatic(EntityGoKart.class, goKartParts);

        // Jet ski
        List<MatrixTransformation> jetSkiTransformGlobal = Lists.newArrayList();
        createBodyTransforms(jetSkiTransformGlobal, EntityJetSki.class);
        HashMap<RayTracePart, List<MatrixTransformation>> jetSkiParts = Maps.newHashMap();
        createTransformListForPart(SpecialModels.JET_SKI_BODY, jetSkiParts, jetSkiTransformGlobal);
        createTransformListForPart(SpecialModels.ATV_HANDLE_BAR, jetSkiParts, jetSkiTransformGlobal,
                MatrixTransformation.createTranslation(0, 0.375, 0.25),
                MatrixTransformation.createRotation(-45, 1, 0, 0),
                MatrixTransformation.createTranslation(0, 0.02, 0));
        createFuelablePartTransforms(SpecialModels.FUEL_PORT_2_CLOSED, EntityJetSki.class, jetSkiParts, jetSkiTransformGlobal);
        createKeyPortTransforms(SpecialModels.KEY_HOLE, EntityJetSki.class, jetSkiParts, jetSkiTransformGlobal);
        registerEntityStatic(EntityJetSki.class, jetSkiParts);
        

        // Lawn mower
        List<MatrixTransformation> lawnMowerTransformGlobal = Lists.newArrayList();
        createBodyTransforms(lawnMowerTransformGlobal, EntityLawnMower.class);
        HashMap<RayTracePart, List<MatrixTransformation>> lawnMowerParts = Maps.newHashMap();
        createTransformListForPart(SpecialModels.LAWN_MOWER_BODY, lawnMowerParts, lawnMowerTransformGlobal);
        createTransformListForPart(SpecialModels.GO_KART_STEERING_WHEEL, lawnMowerParts, lawnMowerTransformGlobal,
                MatrixTransformation.createTranslation(0, 0.4, -0.15),
                MatrixTransformation.createRotation(-45, 1, 0, 0),
                MatrixTransformation.createScale(0.9));
        createTransformListForPart(SpecialModels.TOW_BAR, lawnMowerParts,
                MatrixTransformation.createRotation(180, 0, 1, 0),
                MatrixTransformation.createTranslation(0.0, 0.5, 0.6));
        createFuelablePartTransforms(SpecialModels.FUEL_PORT_CLOSED, EntityLawnMower.class, lawnMowerParts, lawnMowerTransformGlobal);
        createKeyPortTransforms(SpecialModels.KEY_HOLE, EntityLawnMower.class, lawnMowerParts, lawnMowerTransformGlobal);
        registerEntityStatic(EntityLawnMower.class, lawnMowerParts);

        // Mini bike
        List<MatrixTransformation> miniBikeTransformGlobal = Lists.newArrayList();
        createBodyTransforms(miniBikeTransformGlobal, EntityMiniBike.class);
        HashMap<RayTracePart, List<MatrixTransformation>> miniBikeParts = Maps.newHashMap();
        createTransformListForPart(SpecialModels.MINI_BIKE_BODY, miniBikeParts, miniBikeTransformGlobal);
        createTransformListForPart(SpecialModels.MINI_BIKE_HANDLE_BAR, miniBikeParts, miniBikeTransformGlobal);
        createPartTransforms(ModItems.SMALL_ENGINE, VehicleProperties.getProperties(EntityMiniBike.class).getEnginePosition(), miniBikeParts, miniBikeTransformGlobal, FUNCTION_FUELING);
        createKeyPortTransforms(SpecialModels.KEY_HOLE, EntityMiniBike.class, miniBikeParts, miniBikeTransformGlobal);
        registerEntityStatic(EntityMiniBike.class, miniBikeParts);

        // Moped
        List<MatrixTransformation> mopedTransformGlobal = Lists.newArrayList();
        createBodyTransforms(mopedTransformGlobal, EntityMoped.class);
        HashMap<RayTracePart, List<MatrixTransformation>> mopedParts = Maps.newHashMap();
        createTransformListForPart(SpecialModels.MOPED_BODY, mopedParts, mopedTransformGlobal);
        createTransformListForPart(SpecialModels.MOPED_HANDLE_BAR, mopedParts, mopedTransformGlobal,
                MatrixTransformation.createTranslation(0, -0.0625, 0),
                MatrixTransformation.createTranslation(0, 0.835, 0.525),
                MatrixTransformation.createScale(0.8));
        createTransformListForPart(SpecialModels.MOPED_MUD_GUARD, mopedParts, mopedTransformGlobal,
                MatrixTransformation.createTranslation(0, -0.0625, 0),
                MatrixTransformation.createTranslation(0, -0.12, 0.785),
                MatrixTransformation.createRotation(-22.5, 1, 0, 0),
                MatrixTransformation.createScale(0.9));
        createFuelablePartTransforms(SpecialModels.FUEL_PORT_CLOSED, EntityMoped.class, mopedParts, mopedTransformGlobal);
        createKeyPortTransforms(SpecialModels.KEY_HOLE, EntityMoped.class, mopedParts, mopedTransformGlobal);
        registerEntityStatic(EntityMoped.class, mopedParts);

        // // Shopping cart
        // List<MatrixTransformation> cartTransformGlobal = Lists.newArrayList();
        // createBodyTransforms(cartTransformGlobal, EntityShoppingCart.class);
        // HashMap<RayTracePart, List<MatrixTransformation>> cartParts = Maps.newHashMap();
        // createTransformListForPart(SpecialModels.SHOPPING_CART_BODY, cartParts, cartTransformGlobal);
        // registerEntityStatic(EntityShoppingCart.class, cartParts);

        // Smart car
        List<MatrixTransformation> smartCarTransformGlobal = Lists.newArrayList();
        createBodyTransforms(smartCarTransformGlobal, EntitySmartCar.class);
        HashMap<RayTracePart, List<MatrixTransformation>> smartCarParts = Maps.newHashMap();
        createTransformListForPart(SpecialModels.SMART_CAR_BODY, smartCarParts, smartCarTransformGlobal);
        createTransformListForPart(SpecialModels.GO_KART_STEERING_WHEEL, smartCarParts, smartCarTransformGlobal,
                MatrixTransformation.createTranslation(0, 0.2, 0.3),
                MatrixTransformation.createRotation(-67.5, 1, 0, 0),
                MatrixTransformation.createTranslation(0, -0.02, 0),
                MatrixTransformation.createScale(0.9));
        createTransformListForPart(SpecialModels.TOW_BAR, smartCarParts,
                MatrixTransformation.createRotation(180, 0, 1, 0),
                MatrixTransformation.createTranslation(0.0, 0.5, 1.35));
        createFuelablePartTransforms(SpecialModels.FUEL_PORT_CLOSED, EntitySmartCar.class, smartCarParts, smartCarTransformGlobal);
        createKeyPortTransforms(SpecialModels.KEY_HOLE, EntitySmartCar.class, smartCarParts, smartCarTransformGlobal);
        registerEntityStatic(EntitySmartCar.class, smartCarParts);

        // Speed boat
        List<MatrixTransformation> speedBoatTransformGlobal = Lists.newArrayList();
        createBodyTransforms(speedBoatTransformGlobal, EntitySpeedBoat.class);
        HashMap<RayTracePart, List<MatrixTransformation>> speedBoatParts = Maps.newHashMap();
        createTransformListForPart(SpecialModels.SPEED_BOAT_BODY, speedBoatParts, speedBoatTransformGlobal);
        createTransformListForPart(SpecialModels.GO_KART_STEERING_WHEEL, speedBoatParts, speedBoatTransformGlobal,
                MatrixTransformation.createTranslation(0, 0.215, -0.125),
                MatrixTransformation.createRotation(-45, 1, 0, 0),
                MatrixTransformation.createTranslation(0, 0.02, 0));
        createFuelablePartTransforms(SpecialModels.FUEL_PORT_CLOSED, EntitySpeedBoat.class, speedBoatParts, speedBoatTransformGlobal);
        createKeyPortTransforms(SpecialModels.KEY_HOLE, EntitySpeedBoat.class, speedBoatParts, speedBoatTransformGlobal);
        registerEntityStatic(EntitySpeedBoat.class, speedBoatParts);

        // Sports plane
        List<MatrixTransformation> sportsPlaneTransformGlobal = Lists.newArrayList();
        createBodyTransforms(sportsPlaneTransformGlobal, EntitySportsPlane.class);
        HashMap<RayTracePart, List<MatrixTransformation>> sportsPlaneParts = Maps.newHashMap();
        createTransformListForPart(SpecialModels.SPORTS_PLANE_BODY, sportsPlaneParts, sportsPlaneTransformGlobal);
        createFuelablePartTransforms(SpecialModels.FUEL_PORT_CLOSED, EntitySportsPlane.class, sportsPlaneParts, sportsPlaneTransformGlobal);
        createKeyPortTransforms(SpecialModels.KEY_HOLE, EntitySportsPlane.class, sportsPlaneParts, sportsPlaneTransformGlobal);
        createTransformListForPart(SpecialModels.SPORTS_PLANE_WING, sportsPlaneParts, sportsPlaneTransformGlobal,
                MatrixTransformation.createTranslation(0, -0.1875, 0.5),
                MatrixTransformation.createRotation(180, 0, 0, 1),
                MatrixTransformation.createTranslation(0.875, 0.0625, 0),
                MatrixTransformation.createRotation(5, 1, 0, 0));
        createTransformListForPart(SpecialModels.SPORTS_PLANE_WING, sportsPlaneParts, sportsPlaneTransformGlobal,
                MatrixTransformation.createTranslation(0.875, -0.1875, 0.5),
                MatrixTransformation.createRotation(-5, 1, 0, 0));
        sportsPlaneTransformGlobal.add(MatrixTransformation.createTranslation(0, -0.5, 0));
        sportsPlaneTransformGlobal.add(MatrixTransformation.createScale(0.85));
        createTransformListForPart(SpecialModels.SPORTS_PLANE_WHEEL_COVER, sportsPlaneParts, sportsPlaneTransformGlobal,
                MatrixTransformation.createTranslation(0, -0.1875, 1.5));
        createTransformListForPart(SpecialModels.SPORTS_PLANE_LEG, sportsPlaneParts, sportsPlaneTransformGlobal,
                MatrixTransformation.createTranslation(0, -0.1875, 1.5));
        createTransformListForPart(SpecialModels.SPORTS_PLANE_WHEEL_COVER, sportsPlaneParts, sportsPlaneTransformGlobal,
                MatrixTransformation.createTranslation(-0.46875, -0.1875, 0.125));
        createTransformListForPart(SpecialModels.SPORTS_PLANE_LEG, sportsPlaneParts, sportsPlaneTransformGlobal,
                MatrixTransformation.createTranslation(-0.46875, -0.1875, 0.125),
                MatrixTransformation.createRotation(-100, 0, 1, 0));
        createTransformListForPart(SpecialModels.SPORTS_PLANE_WHEEL_COVER, sportsPlaneParts, sportsPlaneTransformGlobal,
                MatrixTransformation.createTranslation(0.46875, -0.1875, 0.125));
        createTransformListForPart(SpecialModels.SPORTS_PLANE_LEG, sportsPlaneParts, sportsPlaneTransformGlobal,
                MatrixTransformation.createTranslation(0.46875, -0.1875, 0.125),
                MatrixTransformation.createRotation(100, 0, 1, 0));
        registerEntityStatic(EntitySportsPlane.class, sportsPlaneParts);

        // Golf Cart
        List<MatrixTransformation> golfCartTransformGlobal = Lists.newArrayList();
        createBodyTransforms(golfCartTransformGlobal, EntityGolfCart.class);
        HashMap<RayTracePart, List<MatrixTransformation>> golfCartParts = Maps.newHashMap();
        createTransformListForPart(SpecialModels.GOLF_CART_BODY, golfCartParts, golfCartTransformGlobal);
        createTransformListForPart(SpecialModels.GO_KART_STEERING_WHEEL, golfCartParts, golfCartTransformGlobal,
                MatrixTransformation.createTranslation(-0.345, 0.425, 0.1),
                MatrixTransformation.createRotation(-45, 1, 0, 0),
                MatrixTransformation.createTranslation(0, -0.02, 0),
                MatrixTransformation.createScale(0.95));
        createFuelablePartTransforms(SpecialModels.FUEL_PORT_CLOSED, EntityGolfCart.class, golfCartParts, golfCartTransformGlobal);
        createKeyPortTransforms(SpecialModels.KEY_HOLE, EntityGolfCart.class, golfCartParts, golfCartTransformGlobal);
        registerEntityStatic(EntityGolfCart.class, golfCartParts);

        // Off-Roader
        List<MatrixTransformation> offRoaderTransformGlobal = Lists.newArrayList();
        createBodyTransforms(offRoaderTransformGlobal, EntityOffRoader.class);
        HashMap<RayTracePart, List<MatrixTransformation>> offRoaderParts = Maps.newHashMap();
        createTransformListForPart(SpecialModels.OFF_ROADER_BODY, offRoaderParts, offRoaderTransformGlobal);
        createTransformListForPart(SpecialModels.GO_KART_STEERING_WHEEL, offRoaderParts, offRoaderTransformGlobal,
                MatrixTransformation.createTranslation(-0.3125, 0.35, 0.2),
                MatrixTransformation.createRotation(-45, 1, 0, 0),
                MatrixTransformation.createTranslation(0, -0.02, 0),
                MatrixTransformation.createScale(0.75));
        createFuelablePartTransforms(SpecialModels.FUEL_PORT_CLOSED, EntityOffRoader.class, offRoaderParts, offRoaderTransformGlobal);
        createKeyPortTransforms(SpecialModels.KEY_HOLE, EntityOffRoader.class, offRoaderParts, offRoaderTransformGlobal);
        createTransformListForPart(SpecialModels.TOW_BAR, offRoaderParts,
                MatrixTransformation.createRotation(180, 0, 1, 0),
                MatrixTransformation.createTranslation(0.0, 2, 1.05));
        registerEntityStatic(EntityOffRoader.class, offRoaderParts);

        List<MatrixTransformation> tractorTransformGlobal = Lists.newArrayList();
        createBodyTransforms(tractorTransformGlobal, EntityTractor.class);
        HashMap<RayTracePart, List<MatrixTransformation>> tractorParts = Maps.newHashMap();
        createTransformListForPart(SpecialModels.TRACTOR_BODY, tractorParts, tractorTransformGlobal);
        createTransformListForPart(SpecialModels.GO_KART_STEERING_WHEEL, tractorParts, tractorTransformGlobal,
                MatrixTransformation.createTranslation(0, 0.66, -0.475),
                MatrixTransformation.createRotation(-67.5F, 1, 0, 0),
                MatrixTransformation.createTranslation(0, -0.02, 0),
                MatrixTransformation.createScale(0.9));
        createFuelablePartTransforms(SpecialModels.FUEL_PORT_CLOSED, EntityTractor.class, tractorParts, tractorTransformGlobal);
        createKeyPortTransforms(SpecialModels.KEY_HOLE, EntityTractor.class, tractorParts, tractorTransformGlobal);
        registerEntityStatic(EntityTractor.class, tractorParts);

        List<MatrixTransformation> miniBusTransformGlobal = Lists.newArrayList();
        createBodyTransforms(miniBusTransformGlobal, EntityMiniBus.class);
        HashMap<RayTracePart, List<MatrixTransformation>> miniBusParts = Maps.newHashMap();
        createTransformListForPart(SpecialModels.MINI_BUS_BODY, miniBusParts, miniBusTransformGlobal);
        createTransformListForPart(SpecialModels.GO_KART_STEERING_WHEEL, miniBusParts, miniBusTransformGlobal,
                MatrixTransformation.createTranslation(-0.2825F, 0.225F, 1.0625F),
                MatrixTransformation.createRotation(-67.5F, 1, 0, 0),
                MatrixTransformation.createTranslation(0.0F, -0.02F, 0.0F),
                MatrixTransformation.createScale(0.75F));
        createFuelablePartTransforms(SpecialModels.FUEL_PORT_CLOSED, EntityMiniBus.class, miniBusParts, miniBusTransformGlobal);
        createKeyPortTransforms(SpecialModels.KEY_HOLE, EntityMiniBus.class, miniBusParts, miniBusTransformGlobal);
        registerEntityStatic(EntityMiniBus.class, miniBusParts);

        if(Loader.isModLoaded("cfm"))
        {
            // Bath
            List<MatrixTransformation> bathTransformGlobal = Lists.newArrayList();
            createBodyTransforms(bathTransformGlobal, EntityBath.class);
            HashMap<RayTracePart, List<MatrixTransformation>> bathParts = Maps.newHashMap();
            createTransformListForPart(Item.getByNameOrId("cfm:bath_bottom"), bathParts, bathTransformGlobal,
                    MatrixTransformation.createRotation(90, 0, 1, 0));
            registerEntityStatic(EntityBath.class, bathParts);

            // Couch
            List<MatrixTransformation> couchTransformGlobal = Lists.newArrayList();
            createBodyTransforms(couchTransformGlobal, EntityCouch.class);
            HashMap<RayTracePart, List<MatrixTransformation>> couchParts = Maps.newHashMap();
            createTransformListForPart(Item.getByNameOrId("cfm:couch_jeb"), couchParts, couchTransformGlobal,
                    MatrixTransformation.createRotation(90, 0, 1, 0),
                    MatrixTransformation.createTranslation(0, 0.0625, 0));
            registerEntityStatic(EntityCouch.class, couchParts);

            // Sofacopter
            List<MatrixTransformation> sofacopterTransformGlobal = Lists.newArrayList();
            createBodyTransforms(sofacopterTransformGlobal, EntitySofacopter.class);
            HashMap<RayTracePart, List<MatrixTransformation>> sofacopterParts = Maps.newHashMap();
            createTransformListForPart(Item.getByNameOrId("cfm:couch"), sofacopterParts, sofacopterTransformGlobal,
                    MatrixTransformation.createRotation(90, 0, 1, 0));
            createTransformListForPart(SpecialModels.COUCH_HELICOPTER_ARM, sofacopterParts, sofacopterTransformGlobal,
                    MatrixTransformation.createTranslation(0, 8 * 0.0625, 0.0));
            createPartTransforms(SpecialModels.FUEL_PORT_CLOSED, EntitySofacopter.FUEL_PORT_POSITION, sofacopterParts, sofacopterTransformGlobal, FUNCTION_FUELING);
            createPartTransforms(SpecialModels.KEY_HOLE, EntitySofacopter.KEY_PORT_POSITION, sofacopterParts, sofacopterTransformGlobal);
            registerEntityStatic(EntitySofacopter.class, sofacopterParts);
        }

        // Vehicle Trailer
        List<MatrixTransformation> trailerVehicleTransformGlobal = Lists.newArrayList();
        createBodyTransforms(trailerVehicleTransformGlobal, EntityVehicleTrailer.class);
        HashMap<RayTracePart, List<MatrixTransformation>> trailerVehicleParts = Maps.newHashMap();
        createTransformListForPart(SpecialModels.TRAILER_BODY, trailerVehicleParts, trailerVehicleTransformGlobal);
        registerEntityStatic(EntityVehicleTrailer.class, trailerVehicleParts);

        // Chest Trailer
        List<MatrixTransformation> trailerStorageTransformGlobal = Lists.newArrayList();
        createBodyTransforms(trailerStorageTransformGlobal, EntityStorageTrailer.class);
        HashMap<RayTracePart, List<MatrixTransformation>> trailerStorageParts = Maps.newHashMap();
        createTransformListForPart(SpecialModels.CHEST_TRAILER, trailerStorageParts, trailerStorageTransformGlobal);
        registerEntityStatic(EntityStorageTrailer.class, trailerStorageParts);

        // Seeder Trailer
        List<MatrixTransformation> seederTransformGlobal = Lists.newArrayList();
        createBodyTransforms(seederTransformGlobal, EntitySeederTrailer.class);
        HashMap<RayTracePart, List<MatrixTransformation>> seederParts = Maps.newHashMap();
        createTransformListForPart(SpecialModels.SEEDER_TRAILER, seederParts, seederTransformGlobal);
        registerEntityStatic(EntitySeederTrailer.class, seederParts);

        // Fertilizer
        List<MatrixTransformation> fertilizerTransformGlobal = Lists.newArrayList();
        createBodyTransforms(fertilizerTransformGlobal, EntityFertilizerTrailer.class);
        HashMap<RayTracePart, List<MatrixTransformation>> fertilizerParts = Maps.newHashMap();
        createTransformListForPart(SpecialModels.FERTILIZER_TRAILER, fertilizerParts, fertilizerTransformGlobal);
        registerEntityStatic(EntityFertilizerTrailer.class, fertilizerParts);

        // Fluid
        List<MatrixTransformation> trailerFluidTransformGlobal = Lists.newArrayList();
        createBodyTransforms(trailerFluidTransformGlobal, EntityFluidTrailer.class);
        HashMap<RayTracePart, List<MatrixTransformation>> trailerFluidParts = Maps.newHashMap();
        createTransformListForPart(SpecialModels.FLUID_TRAILER, trailerFluidParts, trailerFluidTransformGlobal);
        registerEntityStatic(EntityFluidTrailer.class, trailerFluidParts);
    }

    /**
     * Create dynamic triangles for raytraceable entities
     * <p>
     * For a dynamic raytrace, all GL operation performed be accounted for
     */
    private static void registerEntitiesDynamic()
    {
        /* Map<RayTracePart, BiFunction<RayTracePart, Entity, Matrix4d>> aluminumBoatPartsDynamic = Maps.<RayTracePart, BiFunction<RayTracePart, Entity, Matrix4d>>newHashMap();
        aluminumBoatPartsDynamic.put(new RayTracePart(new ItemStack(ModItems.ALUMINUM_BOAT_BODY)), (part, entity) ->
        {
            EntityVehicle aluminumBoat = (EntityVehicle) entity;
            Matrix4d matrix = new Matrix4d();
            matrix.setIdentity();
            MatrixTransformation.createScale(1.1).transform(matrix);
            MatrixTransformation.createTranslation(0, 0.5, 0.2).transform(matrix);
            double currentSpeedNormal = aluminumBoat.currentSpeed / aluminumBoat.getMaxSpeed();
            double turnAngleNormal = aluminumBoat.turnAngle / 45.0;
            MatrixTransformation.createRotation(turnAngleNormal * currentSpeedNormal * -15, 0, 0, 1).transform(matrix);
            MatrixTransformation.createRotation(-8 * Math.min(1.0F, currentSpeedNormal), 1, 0, 0).transform(matrix);
            finalizePartStackMatrix(matrix);
            return matrix;
        });
        registerDynamicClass(EntityAluminumBoat.class, aluminumBoatPartsDynamic); */
    }

    /**
     * Creates a part stack with an NBT tag containing a string name of the part
     * 
     * @param part the rendered item part
     * @param name name of the part
     * 
     * @return the part stack
     */
    public static ItemStack getNamedPartStack(Item part, String name)
    {
        ItemStack partStack = new ItemStack(part);
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setString(PART_NAME, name);
        partStack.setTagCompound(nbt);
        return partStack;
    }

    /**
     * Creates a body transformation based on a PartPosition for a raytraceable entity's body. These
     * arguments should be the same as the static properties defined for the vehicle.
     *
     * @param transforms the global transformation matrix
     * @param clazz the vehicle class
     */
    public static void createBodyTransforms(List<MatrixTransformation> transforms, Class<? extends EntityVehicle> clazz)
    {
        VehicleProperties properties = VehicleProperties.getProperties(clazz);
        PartPosition bodyPosition = properties.getBodyPosition();
        transforms.add(MatrixTransformation.createRotation(bodyPosition.getRotX(), 1, 0, 0));
        transforms.add(MatrixTransformation.createRotation(bodyPosition.getRotY(), 0, 1, 0));
        transforms.add(MatrixTransformation.createRotation(bodyPosition.getRotZ(), 0, 0, 1));
        transforms.add(MatrixTransformation.createTranslation(bodyPosition.getX(), bodyPosition.getY(), bodyPosition.getZ()));
        transforms.add(MatrixTransformation.createScale(bodyPosition.getScale()));
        transforms.add(MatrixTransformation.createTranslation(0, 0.5, 0));
        transforms.add(MatrixTransformation.createTranslation(0, properties.getAxleOffset() * 0.0625, 0));
        transforms.add(MatrixTransformation.createTranslation(0, properties.getWheelOffset() * 0.0625, 0));
    }

    public static void createPartTransforms(Item part, PartPosition partPosition, HashMap<RayTracePart, List<MatrixTransformation>> parts, List<MatrixTransformation> transformsGlobal)
    {
        List<MatrixTransformation> transforms = Lists.newArrayList();
        transforms.addAll(transformsGlobal);
        transforms.add(MatrixTransformation.createTranslation(partPosition.getX() * 0.0625, partPosition.getY() * 0.0625, partPosition.getZ() * 0.0625));
        transforms.add(MatrixTransformation.createTranslation(0, -0.5, 0));
        transforms.add(MatrixTransformation.createScale(partPosition.getScale()));
        transforms.add(MatrixTransformation.createTranslation(0, 0.5, 0));
        transforms.add(MatrixTransformation.createRotation(partPosition.getRotX(), 1, 0, 0));
        transforms.add(MatrixTransformation.createRotation(partPosition.getRotY(), 0, 1, 0));
        transforms.add(MatrixTransformation.createRotation(partPosition.getRotZ(), 0, 0, 1));
        createTransformListForPart(new ItemStack(part), parts, transforms);
    }

    public static void createPartTransforms(SpecialModels model, PartPosition partPosition, HashMap<RayTracePart, List<MatrixTransformation>> parts, List<MatrixTransformation> transformsGlobal)
    {
        List<MatrixTransformation> transforms = Lists.newArrayList();
        transforms.addAll(transformsGlobal);
        transforms.add(MatrixTransformation.createTranslation(partPosition.getX() * 0.0625, partPosition.getY() * 0.0625, partPosition.getZ() * 0.0625));
        transforms.add(MatrixTransformation.createTranslation(0, -0.5, 0));
        transforms.add(MatrixTransformation.createScale(partPosition.getScale()));
        transforms.add(MatrixTransformation.createTranslation(0, 0.5, 0));
        transforms.add(MatrixTransformation.createRotation(partPosition.getRotX(), 1, 0, 0));
        transforms.add(MatrixTransformation.createRotation(partPosition.getRotY(), 0, 1, 0));
        transforms.add(MatrixTransformation.createRotation(partPosition.getRotZ(), 0, 0, 1));
        createTransformListForPart(model, parts, transforms);
    }

    public static void createPartTransforms(Item part, PartPosition partPosition, HashMap<RayTracePart, List<MatrixTransformation>> parts, List<MatrixTransformation> transformsGlobal, Function<RayTraceResultRotated, EnumHand> function)
    {
        List<MatrixTransformation> transforms = Lists.newArrayList();
        transforms.addAll(transformsGlobal);
        transforms.add(MatrixTransformation.createTranslation(partPosition.getX() * 0.0625, partPosition.getY() * 0.0625, partPosition.getZ() * 0.0625));
        transforms.add(MatrixTransformation.createTranslation(0, -0.5, 0));
        transforms.add(MatrixTransformation.createScale(partPosition.getScale()));
        transforms.add(MatrixTransformation.createTranslation(0, 0.5, 0));
        transforms.add(MatrixTransformation.createRotation(partPosition.getRotX(), 1, 0, 0));
        transforms.add(MatrixTransformation.createRotation(partPosition.getRotY(), 0, 1, 0));
        transforms.add(MatrixTransformation.createRotation(partPosition.getRotZ(), 0, 0, 1));
        createTransformListForPart(new ItemStack(part), parts, transforms, function);
    }

    public static void createPartTransforms(SpecialModels model, PartPosition partPosition, HashMap<RayTracePart, List<MatrixTransformation>> parts, List<MatrixTransformation> transformsGlobal, Function<RayTraceResultRotated, EnumHand> function)
    {
        List<MatrixTransformation> transforms = Lists.newArrayList();
        transforms.addAll(transformsGlobal);
        transforms.add(MatrixTransformation.createTranslation(partPosition.getX() * 0.0625, partPosition.getY() * 0.0625, partPosition.getZ() * 0.0625));
        transforms.add(MatrixTransformation.createTranslation(0, -0.5, 0));
        transforms.add(MatrixTransformation.createScale(partPosition.getScale()));
        transforms.add(MatrixTransformation.createTranslation(0, 0.5, 0));
        transforms.add(MatrixTransformation.createRotation(partPosition.getRotX(), 1, 0, 0));
        transforms.add(MatrixTransformation.createRotation(partPosition.getRotY(), 0, 1, 0));
        transforms.add(MatrixTransformation.createRotation(partPosition.getRotZ(), 0, 0, 1));
        createTransformListForPart(model, parts, transforms, function);
    }

    /**
     * Creates part-specific transforms for a raytraceable entity's rendered part.
     * 
     * @param xPixel part's x position
     * @param yPixel part's y position
     * @param zPixel part's z position
     * @param rotation part's rotation vector
     * @param scale part's scale
     * @param transforms list that part transforms are added to
     */
    public static void createPartTransforms(double xPixel, double yPixel, double zPixel, Vec3d rotation, double scale, List<MatrixTransformation> transforms)
    {
        transforms.add(MatrixTransformation.createTranslation(xPixel * 0.0625, yPixel * 0.0625, zPixel * 0.0625));
        transforms.add(MatrixTransformation.createTranslation(0, -0.5, 0));
        transforms.add(MatrixTransformation.createScale(scale));
        transforms.add(MatrixTransformation.createTranslation(0, 0.5, 0));
        if (rotation.x != 0)
        {
            transforms.add(MatrixTransformation.createRotation(rotation.x, 1, 0, 0));
        }
        if (rotation.y != 0)
        {
            transforms.add(MatrixTransformation.createRotation(rotation.y, 0, 1, 0));
        }
        if (rotation.z != 0)
        {
            transforms.add(MatrixTransformation.createRotation(rotation.z, 0, 0, 1));
        }
    }

    /**
     * Creates part-specific transforms for a raytraceable entity's rendered part and adds them the list of transforms
     * for the given entity.
     * 
     * @param part the rendered item part
     * @param xMeters part's x offset meters
     * @param yMeters part's y offset meters
     * @param zMeters part's z offset meters
     * @param xPixel part's x position in pixels
     * @param yPixel part's y position in pixels
     * @param zPixel part's z position in pixels
     * @param rotation part's rotation vector
     * @param scale part's scale
     * @param parts map of all parts to their transforms
     * @param transformsGlobal transforms that apply to all parts for this entity
     */
    public static void createFuelablePartTransforms(Item part, double xMeters, double yMeters, double zMeters, double xPixel, double yPixel, double zPixel,
            Vec3d rotation, double scale, HashMap<RayTracePart, List<MatrixTransformation>> parts, List<MatrixTransformation> transformsGlobal)
    {
        List<MatrixTransformation> partTransforms = Lists.newArrayList();
        partTransforms.add(MatrixTransformation.createTranslation(xMeters, yMeters, zMeters));
        createPartTransforms(xPixel, yPixel, zPixel, rotation, scale, partTransforms);
        transformsGlobal.addAll(partTransforms);
        createTransformListForPart(new ItemStack(part), parts, transformsGlobal, FUNCTION_FUELING);
    }

    /**
     * Creates part-specific transforms for a raytraceable entity's rendered part and adds them the list of transforms
     * for the given entity.
     *
     * @param part the rendered item part
     * @param clazz the vehicle class
     * @param parts map of all parts to their transforms
     * @param transformsGlobal transforms that apply to all parts for this entity
     */
    public static void createFuelablePartTransforms(Item part, Class<? extends EntityVehicle> clazz, HashMap<RayTracePart, List<MatrixTransformation>> parts, List<MatrixTransformation> transformsGlobal)
    {
        PartPosition fuelPortPosition = VehicleProperties.getProperties(clazz).getFuelPortPosition();
        createPartTransforms(part, fuelPortPosition, parts, transformsGlobal, FUNCTION_FUELING);
    }

    /**
     * Creates part-specific transforms for a raytraceable entity's rendered part and adds them the list of transforms
     * for the given entity.
     *
     * @param model the rendered item part
     * @param clazz the vehicle class
     * @param parts map of all parts to their transforms
     * @param transformsGlobal transforms that apply to all parts for this entity
     */
    public static void createFuelablePartTransforms(SpecialModels model, Class<? extends EntityVehicle> clazz, HashMap<RayTracePart, List<MatrixTransformation>> parts, List<MatrixTransformation> transformsGlobal)
    {
        PartPosition fuelPortPosition = VehicleProperties.getProperties(clazz).getFuelPortPosition();
        createPartTransforms(model, fuelPortPosition, parts, transformsGlobal, FUNCTION_FUELING);
    }

    /**
     * Version of {@link EntityRaytracer#createFuelablePartTransforms createFuelablePartTransforms} that sets the axis of rotation to Y
     * 
     * @param part the rendered item part
     * @param xMeters part's x offset meters
     * @param yMeters part's y offset meters
     * @param zMeters part's z offset meters
     * @param xPixel part's x position in pixels
     * @param yPixel part's y position in pixels
     * @param zPixel part's z position in pixels
     * @param rotation part's rotation yaw (Y axis)
     * @param scale part's scale
     * @param parts map of all parts to their transforms
     * @param transformsGlobal transforms that apply to all parts for this entity
     */
    public static void createFuelablePartTransforms(Item part, double xMeters, double yMeters, double zMeters, double xPixel, double yPixel, double zPixel,
            double rotation, double scale, HashMap<RayTracePart, List<MatrixTransformation>> parts, List<MatrixTransformation> transformsGlobal)
    {
        List<MatrixTransformation> partTransforms = Lists.newArrayList();
        partTransforms.add(MatrixTransformation.createTranslation(xMeters, yMeters, zMeters));
        createPartTransforms(xPixel, yPixel, zPixel, new Vec3d(0, rotation, 0), scale, partTransforms);
        transformsGlobal.addAll(partTransforms);
        createTransformListForPart(new ItemStack(part), parts, transformsGlobal, FUNCTION_FUELING);
    }

    public static void createKeyPortTransforms(Item part, Class<? extends EntityVehicle> clazz, HashMap<RayTracePart, List<MatrixTransformation>> parts, List<MatrixTransformation> transformsGlobal)
    {
        PartPosition keyPortPosition = VehicleProperties.getProperties(clazz).getKeyPortPosition();
        createPartTransforms(part, keyPortPosition, parts, transformsGlobal);
    }

    public static void createKeyPortTransforms(SpecialModels model, Class<? extends EntityVehicle> clazz, HashMap<RayTracePart, List<MatrixTransformation>> parts, List<MatrixTransformation> transformsGlobal)
    {
        PartPosition keyPortPosition = VehicleProperties.getProperties(clazz).getKeyPortPosition();
        createPartTransforms(model, keyPortPosition, parts, transformsGlobal);
    }

    /**
     * Adds all global and part-specific transforms for an item part to the list of transforms for the given entity
     * 
     * @param part the rendered item part in a stack
     * @param parts map of all parts to their transforms
     * @param transformsGlobal transforms that apply to all parts for this entity
     * @param continuousInteraction interaction to be performed each tick
     * @param transforms part-specific transforms for the given part 
     */
    public static void createTransformListForPart(ItemStack part, HashMap<RayTracePart, List<MatrixTransformation>> parts, List<MatrixTransformation> transformsGlobal,
                                                  @Nullable Function<RayTraceResultRotated, EnumHand> continuousInteraction, MatrixTransformation... transforms)
    {
        List<MatrixTransformation> transformsAll = Lists.newArrayList();
        transformsAll.addAll(transformsGlobal);
        transformsAll.addAll(Arrays.asList(transforms));
        parts.put(new RayTracePart<>(part, continuousInteraction), transformsAll);
    }

    /**
     * Version of {@link EntityRaytracer#createTransformListForPart(ItemStack, HashMap, List, Function, MatrixTransformation[]) createTransformListForPart} that accepts the part as an item, rather than a stack
     * 
     * @param part the rendered item part in a stack
     * @param parts map of all parts to their transforms
     * @param transformsGlobal transforms that apply to all parts for this entity
     * @param transforms part-specific transforms for the given part 
     */
    public static void createTransformListForPart(ItemStack part, HashMap<RayTracePart, List<MatrixTransformation>> parts, List<MatrixTransformation> transformsGlobal, MatrixTransformation... transforms)
    {
        createTransformListForPart(part, parts, transformsGlobal, null, transforms);
    }

    /**
     * Version of {@link EntityRaytracer#createTransformListForPart(ItemStack, HashMap, List, MatrixTransformation[]) createTransformListForPart} that accepts the part as an item, rather than a stack
     * 
     * @param part the rendered item part
     * @param parts map of all parts to their transforms
     * @param transformsGlobal transforms that apply to all parts for this entity
     * @param transforms part-specific transforms for the given part 
     */
    public static void createTransformListForPart(Item part, HashMap<RayTracePart, List<MatrixTransformation>> parts, List<MatrixTransformation> transformsGlobal, MatrixTransformation... transforms)
    {
        createTransformListForPart(new ItemStack(part), parts, transformsGlobal, transforms);
    }

    /**
     * Version of {@link EntityRaytracer#createTransformListForPart(Item, HashMap, List, MatrixTransformation[]) createTransformListForPart} without global transform list
     * 
     * @param part the rendered item part
     * @param parts map of all parts to their transforms
     * @param transforms part-specific transforms for the given part 
     */
    public static void createTransformListForPart(Item part, HashMap<RayTracePart, List<MatrixTransformation>> parts, MatrixTransformation... transforms)
    {
        createTransformListForPart(part, parts, Lists.newArrayList(), transforms);
    }

    public static void createTransformListForPart(SpecialModels model, HashMap<RayTracePart, List<MatrixTransformation>> parts, MatrixTransformation... transforms)
    {
        createTransformListForPart(model, parts, Lists.newArrayList(), transforms);
    }

    public static void createTransformListForPart(SpecialModels model, HashMap<RayTracePart, List<MatrixTransformation>> parts, List<MatrixTransformation> transformsGlobal,
                                                  @Nullable Function<RayTraceResultRotated, EnumHand> continuousInteraction, MatrixTransformation... transforms)
    {
        List<MatrixTransformation> transformsAll = Lists.newArrayList();
        transformsAll.addAll(transformsGlobal);
        transformsAll.addAll(Arrays.asList(transforms));
        parts.put(new RayTracePart<>(model, continuousInteraction), transformsAll);
    }

    /**
     * Version of {@link EntityRaytracer#createTransformListForPart(ItemStack, HashMap, List, Function, MatrixTransformation[]) createTransformListForPart} that accepts the part as an item, rather than a stack
     *
     * @param model the ibakedmodel of the part
     * @param parts map of all parts to their transforms
     * @param transformsGlobal transforms that apply to all parts for this entity
     * @param transforms part-specific transforms for the given part
     */
    public static void createTransformListForPart(SpecialModels model, HashMap<RayTracePart, List<MatrixTransformation>> parts, List<MatrixTransformation> transformsGlobal, MatrixTransformation... transforms)
    {
        createTransformListForPart(model, parts, transformsGlobal, null, transforms);
    }

    /**
     * Generates lists of dynamic matrix-generating triangles and static lists of transformed triangles that represent each dynamic/static IBakedModel 
     * of each rendered item part for each raytraceable entity class, and finds the nearest superclass in common between those classes.
     * <p>
     * 
     * <strong>Note:</strong> this must be called on the client during the {@link net.minecraftforge.fml.common.event.FMLInitializationEvent init} phase.
     */
    public static void init()
    {
        clearDataForReregistration();

        // Create triangles for raytraceable entities
        registerEntitiesDynamic();
        registerEntitiesStatic();

        for (Class raytraceClass : entityRaytraceTriangles.keySet())
        {
            // Find nearest common superclass
            if (entityRaytraceSuperclass != null)
            {
                Class<?> nearestSuperclass = raytraceClass;
                while (!nearestSuperclass.isAssignableFrom(entityRaytraceSuperclass))
                {
                    nearestSuperclass = nearestSuperclass.getSuperclass();
                    if (nearestSuperclass == Entity.class)
                    {
                        break;
                    }
                }
                entityRaytraceSuperclass = (Class<? extends Entity>) nearestSuperclass;
            }
            else
            {
                entityRaytraceSuperclass = raytraceClass;
            }

            // Calculate scale and offset for rendering the entity in a crate
            float min = 0;
            float max = 0;
            float[] data;
            float x, y, z;
            Entity entity = EntityList.newEntity(raytraceClass, Minecraft.getMinecraft().world);
            for (Entry<RayTracePart, TriangleRayTraceList> entry : entityRaytraceTriangles.get(raytraceClass).entrySet())
            {
                for (TriangleRayTrace triangle : entity == null ? entry.getValue().getTriangles() : entry.getValue().getTriangles(entry.getKey(), entity))
                {
                    data = triangle.getData();
                    for (int i = 0; i < data.length; i += 3)
                    {
                        x = data[i];
                        y = data[i + 1];
                        z = data[i + 2];
                        if (x < min) min = x;
                        if (y < min) min = y;
                        if (z < min) min = z;
                        if (x > max) max = x;
                        if (y > max) max = y;
                        if (z > max) max = z;
                    }
                }
            }
            float range = max - min;
            entityCrateScalesAndOffsets.put(raytraceClass, new ImmutablePair<>(1 / (range * 1.25F), -(min + range * 0.5F)));
        }
        initialized = true;
    }

    /**
     * Create static triangles for raytraceable entity
     * 
     * @param raytraceClass class of entity
     * @param transforms matrix transforms for each part
     */
    private static void registerEntityStatic(Class<? extends IEntityRaytraceable> raytraceClass, Map<RayTracePart, List<MatrixTransformation>> transforms)
    {
        Map<RayTracePart, TriangleRayTraceList> partTriangles = Maps.newHashMap();
        for (Entry<RayTracePart, List<MatrixTransformation>> entryPart : transforms.entrySet())
        {
            RayTracePart part = entryPart.getKey();

            // Generate part-specific matrix
            Matrix4d matrix = new Matrix4d();
            matrix.setIdentity();
            for (MatrixTransformation tranform : entryPart.getValue())
                tranform.transform(matrix);

            finalizePartStackMatrix(matrix);

            partTriangles.put(part, new TriangleRayTraceList(generateTriangles(getModel(part), matrix)));
        }
        entityRaytraceTrianglesStatic.put(raytraceClass, partTriangles);
        HashMap partTrianglesCopy = new HashMap<>(partTriangles);
        Map<RayTracePart, TriangleRayTraceList> partTrianglesAll = entityRaytraceTriangles.get(raytraceClass);
        if (partTrianglesAll != null)
            partTrianglesCopy.putAll(partTrianglesAll);

        entityRaytraceTriangles.put(raytraceClass, partTrianglesCopy);
    }

    /**
     * Create dynamic triangles for raytraceable entity
     * 
     * @param raytraceClass class of entity
     * @param matrixFactories functions for dynamic triangles that take the part and the raytraced
     * entity as arguments and output that part's dynamically generated transformation matrix
     */
    @SuppressWarnings("unused")
    private static void registerEntityDynamic(Class<? extends IEntityRaytraceable> raytraceClass, Map<RayTracePart, BiFunction<RayTracePart, Entity, Matrix4d>> matrixFactories)
    {
        Map<RayTracePart, TriangleRayTraceList> partTriangles = Maps.newHashMap();
        for (Entry<RayTracePart, BiFunction<RayTracePart, Entity, Matrix4d>> entryPart : matrixFactories.entrySet())
        {
            RayTracePart part = entryPart.getKey();
            partTriangles.put(part, new TriangleRayTraceList(generateTriangles(getModel(part), null), entryPart.getValue()));
        }
        entityRaytraceTrianglesDynamic.put(raytraceClass, partTriangles);
        entityRaytraceTriangles.put(raytraceClass, partTriangles);
    }

    /**
     * Gets entity's scale and offset for rendering in a crate
     * 
     * @param raytraceClass class of entity
     * 
     * @return pair of scale and offset
     */
    public static Pair<Float, Float> getCrateScaleAndOffset(Class<? extends Entity> raytraceClass)
    {
        Pair<Float, Float> scaleAndOffset = entityCrateScalesAndOffsets.get(raytraceClass);
        return scaleAndOffset == null ? SCALE_AND_OFFSET_DEFAULT : scaleAndOffset;
    }

    /**
     * Gets an IBakedModel from a RayTracePart
     * 
     * @param part a ray trace part
     * 
     * @return stack's model
     */
    private static IBakedModel getModel(RayTracePart part)
    {
        if(part.model != null)
        {
            return part.model.getModel();
        }
        return Minecraft.getMinecraft().getRenderItem().getItemModelWithOverrides(part.partStack, null, Minecraft.getMinecraft().player);
    }

    /**
     * Converts a model into triangles that represent its quads
     * 
     * @param model rendered model of entity
     * @param matrix part-specific matrix mirroring the static GL operations performed on that part during rendering - should be null for dynamic triangles
     * 
     * @return list of all triangles
     */
    private static List<TriangleRayTrace> generateTriangles(IBakedModel model, @Nullable Matrix4d matrix)
    {
        List<TriangleRayTrace> triangles = Lists.newArrayList();
        try
        {
            // Generate triangles for all faceless and faced quads
            generateTriangles(model.getQuads(null, null, 0L), matrix, triangles);
            for (EnumFacing facing : EnumFacing.values())
            {
                generateTriangles(model.getQuads(null, facing, 0L), matrix, triangles);
            }
        }
        catch (Exception ignored) {}
        return triangles;
    }

    /**
     * Converts quads into pairs of transformed triangles that represent them
     * 
     * @param list list of BakedQuad
     * @param matrix part-specific matrix mirroring the static GL operations performed on that part during rendering - should be null for dynamic triangles
     * @param triangles list of all triangles for the given raytraceable entity class
     */
    private static void generateTriangles(List<BakedQuad> list, @Nullable Matrix4d matrix, List<TriangleRayTrace> triangles)
    {
        for(BakedQuad quad : list)
        {
            int size = quad.getFormat().getIntegerSize();
            int[] data = quad.getVertexData();
            // Two triangles that represent the BakedQuad
            float[] triangle1 = new float[9];
            float[] triangle2 = new float[9];

            // Corner 1
            triangle1[0] = Float.intBitsToFloat(data[0]);
            triangle1[1] = Float.intBitsToFloat(data[1]);
            triangle1[2] = Float.intBitsToFloat(data[2]);
            // Corner 2
            triangle1[3] = triangle2[6] = Float.intBitsToFloat(data[size]);
            triangle1[4] = triangle2[7] = Float.intBitsToFloat(data[size + 1]);
            triangle1[5] = triangle2[8] = Float.intBitsToFloat(data[size + 2]);
            // Corner 3
            size *= 2;
            triangle2[0] = Float.intBitsToFloat(data[size]);
            triangle2[1] = Float.intBitsToFloat(data[size + 1]);
            triangle2[2] = Float.intBitsToFloat(data[size + 2]);
            // Corner 4
            size *= 1.5;
            triangle1[6] = triangle2[3] = Float.intBitsToFloat(data[size]);
            triangle1[7] = triangle2[4] = Float.intBitsToFloat(data[size + 1]);
            triangle1[8] = triangle2[5] = Float.intBitsToFloat(data[size + 2]);

            transformTriangleAndAdd(triangle1, matrix, triangles);
            transformTriangleAndAdd(triangle2, matrix, triangles);
        }
    }

    /**
     * Transforms a static triangle by the part-specific matrix and adds it to the list of all triangles for the given raytraceable entity class, or simply
     * adds a dynamic triangle to that list
     * 
     * @param triangle array of the three vertices that comprise the triangle
     * @param matrix part-specific matrix mirroring the static GL operations performed on that part during rendering - should be null for dynamic triangles
     * @param triangles list of all triangles for the given raytraceable entity class
     */
    private static void transformTriangleAndAdd(float[] triangle, @Nullable Matrix4d matrix, List<TriangleRayTrace> triangles)
    {
        triangles.add(new TriangleRayTrace(matrix != null ? getTransformedTriangle(triangle, matrix) : triangle));
    }

    /**
     * gets a new triangle transformed by the passed part-specific matrix
     * 
     * @param triangle array of the three vertices that comprise the triangle
     * @param matrix part-specific matrix mirroring the GL operation performed on that part during rendering
     * 
     * @return new transformed triangle
     */
    private static float[] getTransformedTriangle(float[] triangle, Matrix4d matrix)
    {
        float[] triangleNew = new float[9];
        for (int i = 0; i < 9; i += 3)
        {
            Vector4d vec = new Vector4d(triangle[i], triangle[i + 1], triangle[i + 2], 1);
            matrix.transform(vec);
            triangleNew[i] = (float) vec.x;
            triangleNew[i + 1] = (float) vec.y;
            triangleNew[i + 2] = (float) vec.z;
        }
        return triangleNew;
    }

    /**
     * Adds the final required translation to the part stack's matrix
     * 
     * @param matrix part-specific matrix mirroring the GL operation performed on that part during rendering
     */
    public static void finalizePartStackMatrix(Matrix4d matrix)
    {
        MatrixTransformation.createTranslation(-0.5, -0.5, -0.5).transform(matrix);
    }

    /**
     * Matrix transformation that corresponds to one of the three supported GL operations that might be performed on a rendered item part
     */
    public static class MatrixTransformation
    {
        private final MatrixTransformationType type;
        private double x, y, z, angle;

        /**
         * Three matrix transformations that correspond to the three supported GL operations that might be performed on a rendered item part
         */
        private enum MatrixTransformationType
        {
            TRANSLATION, ROTATION, SCALE
        }

        public MatrixTransformation(MatrixTransformationType type, double x, double y, double z)
        {
            this.type = type;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public MatrixTransformation(MatrixTransformationType type, double x, double y, double z, double angle)
        {
            this(type, x, y, z);
            this.angle = angle;
        }

        public static MatrixTransformation createTranslation(double x, double y, double z)
        {
            return new MatrixTransformation(MatrixTransformationType.TRANSLATION, x, y, z);
        }

        public static MatrixTransformation createRotation(double angle, double x, double y, double z)
        {
            return new MatrixTransformation(MatrixTransformationType.ROTATION, x, y, z, angle);
        }

        public static MatrixTransformation createScale(double x, double y, double z)
        {
            return new MatrixTransformation(MatrixTransformationType.SCALE, x, y, z);
        }

        public static MatrixTransformation createScale(double xyz)
        {
            return new MatrixTransformation(MatrixTransformationType.SCALE, xyz, xyz, xyz);
        }

        /**
         * Applies the matrix transformation that this class represents to the passed matrix
         * 
         * @param matrix matrix to apply this transformation to
         */
        public void transform(Matrix4d matrix)
        {
            Matrix4d temp = new Matrix4d();
            switch (type)
            {
                case ROTATION:      temp.set(new AxisAngle4d(x, y, z, (float) Math.toRadians(angle)));
                                    break;
                case TRANSLATION:   temp.set(new Vector3d(x, y, z));
                                    break;
                case SCALE:         Vector3d scaleVec = new Vector3d(x, y, z);
                                    temp.setIdentity();
                                    temp.m00 = scaleVec.x;
                                    temp.m11 = scaleVec.y;
                                    temp.m22 = scaleVec.z;
            }
            matrix.mul(temp);
        }
    }

    /**
     * Performs a specific and general interaction with a raytraceable entity
     * 
     * @param entity raytraceable entity
     * @param result the result of the raytrace
     */
    public static void interactWithEntity(IEntityRaytraceable entity, RayTraceResult result)
    {
        Minecraft.getMinecraft().playerController.interactWithEntity(Minecraft.getMinecraft().player, (Entity) entity, EnumHand.MAIN_HAND);
        Minecraft.getMinecraft().playerController.interactWithEntity(Minecraft.getMinecraft().player, (Entity) entity, result, EnumHand.MAIN_HAND);
    }

    /**
     * Performs a raytrace and interaction each tick that a continuously interactable part is right-clicked and held while looking at
     * 
     * @param event tick event
     */
    @SubscribeEvent
    public static void raytraceEntitiesContinuously(ClientTickEvent event)
    {
        if (event.phase != Phase.START)
            return;

        if ((!initialized || VehicleConfig.CLIENT.debug.reloadRaytracerEachTick) && Minecraft.getMinecraft().world != null)
            init();

        if (continuousInteraction == null || Minecraft.getMinecraft().player == null)
            return;

        RayTraceResultRotated result = raytraceEntities(continuousInteraction.isRightClick());
        if (result == null || result.entityHit != continuousInteraction.entityHit || result.getPartHit() != continuousInteraction.getPartHit())
        {
            continuousInteraction = null;
            continuousInteractionTickCounter = 0;
            return;
        }
        continuousInteractionObject = result.performContinuousInteraction();
        if (continuousInteractionObject == null)
        {
            continuousInteraction = null;
            continuousInteractionTickCounter = 0;
        }
        else
        {
            continuousInteractionTickCounter++;
        }
    }

    /**
     * Performs raytrace on interaction boxes and item part triangles of all raytraceable entities within reach of the player upon click,
     * and cancels it if the clicked raytraceable entity returns true from {@link IEntityRaytraceable#processHit processHit}
     * 
     * @param event mouse event
     */
    @SubscribeEvent
    public static void onMouseEvent(MouseEvent event)
    {
        // Return if not right and/or left clicking, if the mouse is being released, or if there are no entity classes to raytrace
        boolean rightClick = Minecraft.getMinecraft().gameSettings.keyBindUseItem.getKeyCode() + 100 == event.getButton();
        if ((!rightClick && (!VehicleConfig.CLIENT.interaction.enabledLeftClick
                || Minecraft.getMinecraft().gameSettings.keyBindAttack.getKeyCode() + 100 != event.getButton()))
                || !event.isButtonstate())
        {
            return;
        }
        if (performRayTrace(rightClick))
        {
            // Cancel click
            event.setCanceled(true);
        }
    }

    public static boolean performRayTrace(boolean rightClick)
    {
        if(entityRaytraceSuperclass == null)
            return false;

        RayTraceResultRotated result = raytraceEntities(rightClick);
        if (result != null)
        {
            continuousInteractionObject = result.performContinuousInteraction();
            if (continuousInteractionObject != null)
            {
                continuousInteraction = result;
                continuousInteractionTickCounter = 1;
            }
            return true;
        }
        return false;
    }

    /**
     * Performs raytrace on interaction boxes and item part triangles of all raytraceable entities within reach of the player,
     * and returns the result if the clicked raytraceable entity returns true from {@link IEntityRaytraceable#processHit processHit}
     * 
     * @param rightClick whether the click was a right-click or a left-click
     * 
     * @return the result of the raytrace - returns null, if it fails
     */
    @Nullable
    private static RayTraceResultRotated raytraceEntities(boolean rightClick)
    {
        float reach = Minecraft.getMinecraft().playerController.getBlockReachDistance();
        Vec3d eyeVec = Minecraft.getMinecraft().player.getPositionEyes(1);
        Vec3d forwardVec = eyeVec.add(Minecraft.getMinecraft().player.getLook(1).scale(reach));
        AxisAlignedBB box = new AxisAlignedBB(eyeVec, eyeVec).grow(reach);
        RayTraceResultRotated lookObject = null;
        double distanceShortest = Double.MAX_VALUE;
        // Perform raytrace on all raytraceable entities within reach of the player
        RayTraceResultRotated lookObjectPutative;
        double distance;
        for (Entity entity : Minecraft.getMinecraft().world.getEntitiesWithinAABB(entityRaytraceSuperclass, box))
        {
            if (entityRaytraceTrianglesDynamic.keySet().contains(entity.getClass()) || entityRaytraceTrianglesStatic.keySet().contains(entity.getClass()))
            {
                lookObjectPutative = rayTraceEntityRotated((IEntityRaytraceable) entity, eyeVec, forwardVec, reach, rightClick);
                if (lookObjectPutative != null)
                {
                    distance = lookObjectPutative.getDistanceToEyes();
                    if (distance < distanceShortest)
                    {
                        lookObject = lookObjectPutative;
                        distanceShortest = distance;
                    }
                }
            }
        }
        if (lookObject != null)
        {
            double eyeDistance = lookObject.getDistanceToEyes();
            if (eyeDistance <= reach)
            {
                Vec3d hit = forwardVec;
                RayTraceResult lookObjectMC = Minecraft.getMinecraft().objectMouseOver;
                // If the hit entity is a raytraceable entity, and if the player's eyes are inside what MC
                // thinks the player is looking at, then process the hit regardless of what MC thinks
                boolean bypass = entityRaytraceTrianglesStatic.keySet().contains(lookObject.entityHit.getClass());
                if (bypass && lookObjectMC != null && lookObjectMC.typeOfHit != Type.MISS)
                {
                    AxisAlignedBB boxMC;
                    if (lookObjectMC.typeOfHit == Type.ENTITY)
                    {
                        boxMC = lookObjectMC.entityHit.getEntityBoundingBox();
                    }
                    else
                    {
                        boxMC = lookObject.entityHit.world.getBlockState(lookObjectMC.getBlockPos()).getBoundingBox(lookObject.entityHit.world, lookObjectMC.getBlockPos());
                    }
                    bypass = boxMC != null && boxMC.contains(eyeVec);
                }

                if (!bypass && lookObjectMC != null && lookObjectMC.typeOfHit != Type.MISS)
                {
                    // Set hit to what MC thinks the player is looking at if the player is not looking at the hit entity
                    if (lookObjectMC.typeOfHit == Type.ENTITY && lookObjectMC.entityHit == lookObject.entityHit)
                    {
                        bypass = true;
                    }
                    else
                    {
                        hit = lookObjectMC.hitVec;
                    }
                }
                // If not bypassed, process the hit only if it is closer to the player's eyes than what MC thinks the player is looking
                if (bypass || eyeDistance < hit.distanceTo(eyeVec))
                {
                    if (((IEntityRaytraceable) lookObject.entityHit).processHit(lookObject, rightClick))
                    {
                        return lookObject;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Performs raytrace on interaction boxes and item part triangles of raytraceable entity
     * 
     * @param boxProvider raytraceable entity
     * @param eyeVec position of the player's eyes
     * @param forwardVec eyeVec extended by reach distance in the direction the player is looking in
     * @param reach distance at which players can interact with objects in the world
     * @param rightClick whether the click was a right-click or a left-click
     * 
     * @return the result of the raytrace
     */
    @Nullable
    public static RayTraceResultRotated rayTraceEntityRotated(IEntityRaytraceable boxProvider, Vec3d eyeVec, Vec3d forwardVec, double reach, boolean rightClick)
    {
        Entity entity = (Entity) boxProvider;
        Vec3d pos = entity.getPositionVector();
        double angle = Math.toRadians(-entity.rotationYaw);

        // Rotate the raytrace vectors in the opposite direction as the entity's rotation yaw
        Vec3d eyeVecRotated = rotateVecXZ(eyeVec, angle, pos);
        Vec3d forwardVecRotated = rotateVecXZ(forwardVec, angle, pos);

        float[] eyes = new float[]{(float) eyeVecRotated.x, (float) eyeVecRotated.y, (float) eyeVecRotated.z};
        Vec3d look = forwardVecRotated.subtract(eyeVecRotated).normalize().scale(reach);
        float[] direction = new float[]{(float) look.x, (float) look.y, (float) look.z};
        // Perform raytrace on the entity's interaction boxes
        RayTraceResultTriangle lookBox = null;
        RayTraceResultTriangle lookPart = null;
        double distanceShortest = Double.MAX_VALUE;
        List<RayTracePart> boxesApplicable = boxProvider.getApplicableInteractionBoxes();
        List<RayTracePart> partsNonApplicable = boxProvider.getNonApplicableParts();

        // Perform raytrace on the dynamic boxes and triangles of the entity's parts
        lookBox = raytracePartTriangles(entity, pos, eyeVecRotated, lookBox, distanceShortest, eyes, direction, boxesApplicable, false, boxProvider.getDynamicInteractionBoxMap());
        distanceShortest = updateShortestDistance(lookBox, distanceShortest);
        lookPart = raytracePartTriangles(entity, pos, eyeVecRotated, lookPart, distanceShortest, eyes, direction, partsNonApplicable, true, entityRaytraceTrianglesDynamic.get(entity.getClass()));
        distanceShortest = updateShortestDistance(lookPart, distanceShortest);

        boolean isDynamic = lookBox != null || lookPart != null;

        // If no closer intersection than that of the dynamic boxes and triangles found, then perform raytrace on the static boxes and triangles of the entity's parts
        if (!isDynamic)
        {
            lookBox = raytracePartTriangles(entity, pos, eyeVecRotated, lookBox, distanceShortest, eyes, direction, boxesApplicable, false, boxProvider.getStaticInteractionBoxMap());
            distanceShortest = updateShortestDistance(lookBox, distanceShortest);
            lookPart = raytracePartTriangles(entity, pos, eyeVecRotated, lookPart, distanceShortest, eyes, direction, partsNonApplicable, true, entityRaytraceTrianglesStatic.get(entity.getClass()));
        }
        // Return the result object of hit with hit vector rotated back in the same direction as the entity's rotation yaw, or null it no hit occurred
        if (lookPart != null)
        {
            return new RayTraceResultRotated(entity, rotateVecXZ(lookPart.getHit(), -angle, pos), lookPart.getDistance(), lookPart.getPart(), rightClick);
        }
        return lookBox == null ? null : new RayTraceResultRotated(entity, rotateVecXZ(lookBox.getHit(), -angle, pos), lookBox.getDistance(), lookBox.getPart(), rightClick);
    }

    /**
     * Sets the current shortest distance to the current closest viewed object
     * 
     * @param lookObject current closest viewed object
     * @param distanceShortest distance from eyes to the current closest viewed object
     * 
     * @return new shortest distance
     */
    private static double updateShortestDistance(RayTraceResultTriangle lookObject, double distanceShortest)
    {
        if (lookObject != null)
        {
            distanceShortest = lookObject.getDistance();
        }
        return distanceShortest;
    }

    /**
     * Performs raytrace on part triangles of raytraceable entity
     * 
     * @param entity raytraced entity
     * @param pos position of the raytraced entity 
     * @param eyeVecRotated position of the player's eyes taking into account the rotation yaw of the raytraced entity
     * @param lookPart current closest viewed object
     * @param distanceShortest distance from eyes to the current closest viewed object
     * @param eyes position of the eyes of the player
     * @param direction normalized direction vector the player is looking in scaled by the player reach distance
     * @param partsApplicable list of parts that currently apply to the raytraced entity - if null, all are applicable
     * @param parts triangles for the part
     * 
     * @return the result of the part raytrace
     */
    private static RayTraceResultTriangle raytracePartTriangles(Entity entity, Vec3d pos, Vec3d eyeVecRotated, RayTraceResultTriangle lookPart, double distanceShortest,
            float[] eyes, float[] direction, @Nullable List<RayTracePart> partsApplicable, boolean invalidateParts, Map<RayTracePart, TriangleRayTraceList> parts)
    {
        if (parts != null)
        {
            for (Entry<RayTracePart, TriangleRayTraceList> entry : parts.entrySet())
            {
                if (partsApplicable == null || (invalidateParts != partsApplicable.contains(entry.getKey())))
                {
                    RayTraceResultTriangle lookObjectPutative;
                    double distance;
                    RayTracePart part = entry.getKey();
                    for (TriangleRayTrace triangle : entry.getValue().getTriangles(part, entity))
                    {
                        lookObjectPutative = RayTraceResultTriangle.calculateIntercept(eyes, direction, pos, triangle.getData(), part);
                        if (lookObjectPutative != null)
                        {
                            distance = lookObjectPutative.calculateAndSaveDistance(eyeVecRotated);
                            if (distance < distanceShortest)
                            {
                                lookPart = lookObjectPutative;
                                distanceShortest = distance;
                            }
                        }
                    }
                }
            }
        }
        return lookPart;
    }

    /**
     * Rotates the x and z components of a vector about the y axis
     * 
     * @param vec vector to rotate
     * @param angle angle in radians to rotate about the y axis
     * @param rotationPoint vector containing the x/z position to rotate around
     * 
     * @return the passed vector rotated by 'angle' around 'rotationPoint'
     */
    private static Vec3d rotateVecXZ(Vec3d vec, double angle, Vec3d rotationPoint)
    {
        double x = rotationPoint.x + Math.cos(angle) * (vec.x - rotationPoint.x) - Math.sin(angle) * (vec.z - rotationPoint.z);
        double z = rotationPoint.z + Math.sin(angle) * (vec.x - rotationPoint.x) + Math.cos(angle) * (vec.z - rotationPoint.z);
        return new Vec3d(x, vec.y, z);
    }

    /**
     * <strong>Debug Method:</strong> Renders the interaction boxes of, and the triangles of the parts of, a raytraceable entity
     * <p>
     * <strong>Note:</strong>
     * <ul>
     *     <li><strong>The inner if statement must be hard-coded to true, in order to take effect.</strong></li>
     *     <li>This should be called in the entity's renderer at the end of doRender.</li>
     * </ul>
     * 
     * @param entity raytraced entity
     * @param x entity's x position
     * @param y entity's y position
     * @param z entity's z position
     * @param yaw entity's rotation yaw
     */
    public static void renderRaytraceElements(IEntityRaytraceable entity, double x, double y, double z, float yaw)
    {
        if (VehicleConfig.CLIENT.debug.renderOutlines)
        {
            GlStateManager.pushMatrix();
            {
                GlStateManager.translate(x, y, z);
                GlStateManager.rotate(-yaw, 0, 1, 0);
                GlStateManager.enableBlend();
                GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
                GlStateManager.glLineWidth(2.0F);
                GlStateManager.disableTexture2D();
                GlStateManager.disableLighting();
                Tessellator tessellator = Tessellator.getInstance();
                BufferBuilder buffer = tessellator.getBuffer();
                renderRaytraceTriangles(entity, tessellator, buffer, entityRaytraceTrianglesStatic);
                renderRaytraceTriangles(entity, tessellator, buffer, entityRaytraceTrianglesDynamic);
                entity.drawInteractionBoxes(tessellator, buffer);
                GlStateManager.enableLighting();
                GlStateManager.enableTexture2D();
                GlStateManager.disableBlend();
            }
            GlStateManager.popMatrix();
        }
    }

    /**
     * Renders the triangles of the parts of a raytraceable entity
     * 
     * @param entity raytraced entity
     * @param tessellator rendered plane tiler
     * @param buffer tessellator's vertex buffer
     * @param entityTriangles map containing the triangles for the given ray traceable entity
     */
    private static void renderRaytraceTriangles(IEntityRaytraceable entity, Tessellator tessellator, BufferBuilder buffer,
            Map<Class<? extends IEntityRaytraceable>, Map<RayTracePart, TriangleRayTraceList>> entityTriangles)
    {
        Map<RayTracePart, TriangleRayTraceList> map = entityTriangles.get(entity.getClass());
        if (map != null)
        {
            List<RayTracePart> partsNonApplicable = entity.getNonApplicableParts();
            for (Entry<RayTracePart, TriangleRayTraceList> entry : map.entrySet())
            {
                if (partsNonApplicable == null || !partsNonApplicable.contains(entry.getKey()))
                {
                    for (TriangleRayTrace triangle : entry.getValue().getTriangles(entry.getKey(), (Entity) entity))
                    {
                        triangle.draw(tessellator, buffer, 1, 0, 0, 0.4F);
                    }
                }
            }
        }
    }

    /**
     * Converts interaction box to list of triangles that represents it
     * 
     * @param box raytraceable interaction box
     * @param matrixFactory function for dynamic triangles that takes the part and the raytraced
     * entity as arguments and outputs that part's dynamically generated transformation matrix
     * 
     * @return triangle list
     */
    public static TriangleRayTraceList boxToTriangles(AxisAlignedBB box, @Nullable BiFunction<RayTracePart, Entity, Matrix4d> matrixFactory)
    {
        List<TriangleRayTrace> triangles = Lists.newArrayList();
        getTranglesFromQuadAndAdd(triangles, box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.minZ, box.maxX, box.minY, box.minZ, box.minX, box.minY, box.minZ);
        getTranglesFromQuadAndAdd(triangles, box.maxX, box.maxY, box.minZ, box.maxX, box.maxY, box.maxZ, box.maxX, box.minY, box.maxZ, box.maxX, box.minY, box.minZ);
        getTranglesFromQuadAndAdd(triangles, box.maxX, box.maxY, box.maxZ, box.minX, box.maxY, box.maxZ, box.minX, box.minY, box.maxZ, box.maxX, box.minY, box.maxZ);
        getTranglesFromQuadAndAdd(triangles, box.minX, box.maxY, box.maxZ, box.minX, box.maxY, box.minZ, box.minX, box.minY, box.minZ, box.minX, box.minY, box.maxZ);
        getTranglesFromQuadAndAdd(triangles, box.minX, box.maxY, box.maxZ, box.maxX, box.maxY, box.maxZ, box.maxX, box.maxY, box.minZ, box.minX, box.maxY, box.minZ);
        getTranglesFromQuadAndAdd(triangles, box.maxX, box.minY, box.maxZ, box.minX, box.minY, box.maxZ, box.minX, box.minY, box.minZ, box.maxX, box.minY, box.minZ);
        return new TriangleRayTraceList(triangles, matrixFactory);
    }

    /**
     * Version of {@link EntityRaytracer#boxToTriangles(AxisAlignedBB, BiFunction) boxToTriangles}
     * without a matrix-generating function for static interaction boxes
     * 
     * @param box raytraceable interaction box
     * 
     * @return triangle list
     */
    public static TriangleRayTraceList boxToTriangles(AxisAlignedBB box)
    {
        return boxToTriangles(box, null);
    }

    /**
     * Converts quad into a pair of triangles that represents it
     * 
     * @param triangles list of all triangles for the given raytraceable entity class
     * @param data four vertices of a quad
     */
    private static void getTranglesFromQuadAndAdd(List<TriangleRayTrace> triangles, double... data)
    {
        int size = 3;
        // Two triangles that represent the BakedQuad
        float[] triangle1 = new float[9];
        float[] triangle2 = new float[9];

        // Corner 1
        triangle1[0] = (float) data[0];
        triangle1[1] = (float) data[1];
        triangle1[2] = (float) data[2];
        // Corner 2
        triangle1[3] = triangle2[6] = (float) data[size];
        triangle1[4] = triangle2[7] = (float) data[size + 1];
        triangle1[5] = triangle2[8] = (float) data[size + 2];
        // Corner 3
        size *= 2;
        triangle2[0] = (float) data[size];
        triangle2[1] = (float) data[size + 1];
        triangle2[2] = (float) data[size + 2];
        // Corner 4
        size *= 1.5;
        triangle1[6] = triangle2[3] = (float) data[size];
        triangle1[7] = triangle2[4] = (float) data[size + 1];
        triangle1[8] = triangle2[5] = (float) data[size + 2];

        transformTriangleAndAdd(triangle1, null, triangles);
        transformTriangleAndAdd(triangle2, null, triangles);
    }

    /**
     * Triangle used in raytracing
     */
    public static class TriangleRayTrace
    {
        private final float[] data;

        public TriangleRayTrace(float[] data)
        {
            this.data = data;
        }

        public float[] getData()
        {
            return data;
        }

        public void draw(Tessellator tessellator, BufferBuilder buffer, float red, float green, float blue, float alpha)
        {
            buffer.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION_COLOR);
            buffer.pos(data[6], data[7], data[8]).color(red, green, blue, alpha).endVertex();
            buffer.pos(data[0], data[1], data[2]).color(red, green, blue, alpha).endVertex();
            buffer.pos(data[3], data[4], data[5]).color(red, green, blue, alpha).endVertex();
            tessellator.draw();
        }
    }

    /**
     * Wrapper class for raytraceable triangles
     */
    public static class TriangleRayTraceList
    {
        private final List<TriangleRayTrace> triangles;
        private final BiFunction<RayTracePart, Entity, Matrix4d> matrixFactory;

        /**
         * Constructor for static triangles
         * 
         * @param triangles raytraceable triangle list
         */
        public TriangleRayTraceList(List<TriangleRayTrace> triangles)
        {
            this(triangles, null);
        }

        /**
         * Constructor for dynamic triangles
         * 
         * @param triangles raytraceable triangle list
         * @param matrixFactory function for dynamic triangles that takes the part and the raytraced
         * entity as arguments and outputs that part's dynamically generated transformation matrix
         */
        public TriangleRayTraceList(List<TriangleRayTrace> triangles, @Nullable BiFunction<RayTracePart, Entity, Matrix4d> matrixFactory)
        {
            this.triangles = triangles;
            this.matrixFactory = matrixFactory;
        }

        /**
         * Gets list of static pre-transformed triangles, or gets a new list of dynamically transformed triangles
         * 
         * @param part rendered item-part
         * @param entity raytraced entity
         */
        public List<TriangleRayTrace> getTriangles(RayTracePart part, Entity entity)
        {
            if (matrixFactory != null)
            {
                List<TriangleRayTrace> triangles = Lists.newArrayList();
                Matrix4d matrix = matrixFactory.apply(part, entity);
                for (TriangleRayTrace triangle : this.triangles)
                {
                    triangles.add(new TriangleRayTrace(getTransformedTriangle(triangle.getData(), matrix)));
                }
                return triangles;
            }
            return this.triangles;
        }

        /**
         * Gets list of triangles directly
         */
        public List<TriangleRayTrace> getTriangles()
        {
            return triangles;
        }
    }

    /**
     * The result of a raytrace on a triangle.
     * <p>
     * This class utilizes a Möller/Trumbore intersection algorithm.
     */
    private static class RayTraceResultTriangle
    {
        private static final float EPSILON = 0.000001F;
        private final float x, y, z;
        private final RayTracePart part;
        private double distance; 

        public RayTraceResultTriangle(RayTracePart part, float x, float y, float z)
        {
            this.part = part;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public Vec3d getHit()
        {
            return new Vec3d(x, y, z);
        }

        public RayTracePart getPart()
        {
            return part;
        }

        public double calculateAndSaveDistance(Vec3d eyeVec)
        {
            distance = eyeVec.distanceTo(getHit());
            return distance;
        }

        public double getDistance()
        {
            return distance;
        }

        /**
         * Raytrace a triangle using a Möller/Trumbore intersection algorithm
         * 
         * @param eyes position of the eyes of the player
         * @param direction normalized direction vector scaled by reach distance that represents the player's looking direction
         * @param posEntity position of the entity being raytraced
         * @param data triangle data of a part of the entity being raytraced
         * @param part raytrace part
         * 
         * @return new instance of this class, if the ray intersect the triangle - null if the ray does not
         */
        public static RayTraceResultTriangle calculateIntercept(float[] eyes, float[] direction, Vec3d posEntity, float[] data, RayTracePart part)
        {
            float[] vec0 = {data[0] + (float) posEntity.x, data[1] + (float) posEntity.y, data[2] + (float) posEntity.z};
            float[] vec1 = {data[3] + (float) posEntity.x, data[4] + (float) posEntity.y, data[5] + (float) posEntity.z};
            float[] vec2 = {data[6] + (float) posEntity.x, data[7] + (float) posEntity.y, data[8] + (float) posEntity.z};
            float[] edge1 = new float[3];
            float[] edge2 = new float[3];
            float[] tvec = new float[3];
            float[] pvec = new float[3];
            float[] qvec = new float[3];
            float det;
            float inv_det;
            subtract(edge1, vec1, vec0);
            subtract(edge2, vec2, vec0);
            crossProduct(pvec, direction, edge2);
            det = dotProduct(edge1, pvec);
            if (det <= -EPSILON || det >= EPSILON)
            {
                inv_det = 1f / det;
                subtract(tvec, eyes, vec0);
                float u = dotProduct(tvec, pvec) * inv_det;
                if (u >= 0 && u <= 1)
                {
                    crossProduct(qvec, tvec, edge1);
                    float v = dotProduct(direction, qvec) * inv_det;
                    if (v >= 0 && u + v <= 1 && inv_det * dotProduct(edge2, qvec) > EPSILON)
                    {
                        return new RayTraceResultTriangle(part, edge1[0] * u + edge2[0] * v + vec0[0], edge1[1] * u + edge2[1] * v + vec0[1], edge1[2] * u + edge2[2] * v + vec0[2]);
                    }
                }
            }
            return null;
        }

        private static void crossProduct(float[] result, float[] v1, float[] v2)
        {
            result[0] = v1[1] * v2[2] - v1[2] * v2[1];
            result[1] = v1[2] * v2[0] - v1[0] * v2[2];
            result[2] = v1[0] * v2[1] - v1[1] * v2[0];
        }

        private static float dotProduct(float[] v1, float[] v2)
        {
            return v1[0] * v2[0] + v1[1] * v2[1] + v1[2] * v2[2];
        }

        private static void subtract(float[] result, float[] v1, float[] v2)
        {
            result[0] = v1[0] - v2[0];
            result[1] = v1[1] - v2[1];
            result[2] = v1[2] - v2[2];
        }
    }

    /**
     * A raytrace part representing either an item or a box
     */
    public static class RayTracePart<R>
    {
        private final ItemStack partStack;
        private final AxisAlignedBB partBox;
        private final SpecialModels model;
        private final Function<RayTraceResultRotated, R> continuousInteraction;

        public RayTracePart(ItemStack partStack, @Nullable Function<RayTraceResultRotated, R> continuousInteraction)
        {
            this(partStack, null, null, continuousInteraction);
        }

        public RayTracePart(AxisAlignedBB partBox, @Nullable Function<RayTraceResultRotated, R> continuousInteraction)
        {
            this(ItemStack.EMPTY, partBox, null, continuousInteraction);
        }

        public RayTracePart(SpecialModels model, @Nullable Function<RayTraceResultRotated, R> continuousInteraction)
        {
            this(ItemStack.EMPTY, null, model, continuousInteraction);
        }

        public RayTracePart(AxisAlignedBB partBox)
        {
            this(ItemStack.EMPTY, partBox, null, null);
        }

        private RayTracePart(ItemStack partStack, @Nullable AxisAlignedBB partBox, @Nullable SpecialModels model, @Nullable Function<RayTraceResultRotated, R> continuousInteraction)
        {
            this.partStack = partStack;
            this.partBox = partBox;
            this.model = model;
            this.continuousInteraction = continuousInteraction;
        }

        public ItemStack getStack()
        {
            return partStack;
        }

        @Nullable
        public AxisAlignedBB getBox()
        {
            return partBox;
        }

        @Nullable
        public SpecialModels getModel()
        {
            return model;
        }

        public Function<RayTraceResultRotated, R> getContinuousInteraction()
        {
            return continuousInteraction;
        }
    }

    /**
     * The result of a rotated raytrace
     */
    public static class RayTraceResultRotated extends RayTraceResult
    {
        private final RayTracePart partHit;
        private final double distanceToEyes;
        private final boolean rightClick;

        private RayTraceResultRotated(Entity entityHit, Vec3d hitVec, double distanceToEyes, RayTracePart partHit, boolean rightClick)
        {
            super(entityHit, hitVec);
            this.distanceToEyes = distanceToEyes;
            this.partHit = partHit;
            this.rightClick = rightClick;
        }

        public RayTracePart getPartHit()
        {
            return partHit;
        }

        public double getDistanceToEyes()
        {
            return distanceToEyes;
        }

        public boolean isRightClick()
        {
            return rightClick;
        }

        public Object performContinuousInteraction()
        {
            return partHit.getContinuousInteraction() == null ? null : partHit.getContinuousInteraction().apply(this);
        }

        public <R> boolean equalsContinuousInteraction(Function<RayTraceResultRotated, R> function)
        {
            return function.equals(partHit.getContinuousInteraction());
        }
    }

    /**
     * Interface that allows entities to be raytraceable
     * <p>
     * <strong>Note:</strong>
     * <ul>
     *     <li>This must be implemented by all entities that raytraces are to be performed on.</li>
     *     <li>Only classes that extend {@link net.minecraft.entity.Entity Entity} should implement this interface.</li>
     * </ul>
     */
    public interface IEntityRaytraceable
    {
        /**
         * Called when either an item part is clicked or an entity-specific interaction box is clicked.
         * <p>
         * Default behavior is to perform a general interaction with the entity when a part is clicked.
         * 
         * @param result item part hit - null if none was hit
         * @param rightClick whether the click was a right-click or a left-click
         * 
         * @return whether or not the click that initiated the hit should be canceled
         */
        @SideOnly(Side.CLIENT)
        default boolean processHit(RayTraceResultRotated result, boolean rightClick)
        {
            SpecialModels model = result.getPartHit().model;
            if(model == SpecialModels.KEY_HOLE)
            {
                PacketHandler.INSTANCE.sendToServer(new MessageInteractKey((Entity) this));
                return true;
            }

            boolean isContinuous = result.partHit.getContinuousInteraction() != null;
            if(isContinuous || !(Minecraft.getMinecraft().objectMouseOver != null && Minecraft.getMinecraft().objectMouseOver.entityHit == this))
            {
                EntityPlayer player = Minecraft.getMinecraft().player;
                boolean notRiding = player.getRidingEntity() != this;
                if(!rightClick && notRiding)
                {
                    Minecraft.getMinecraft().playerController.attackEntity(player, (Entity) this);
                    return true;
                }
                ItemStack stack = result.getPartHit().getStack();
                if(!stack.isEmpty() || result.getPartHit().model != null)
                {
                    if(notRiding)
                    {
                        if(player.isSneaking() && !player.isSpectator())
                        {
                            PacketHandler.INSTANCE.sendToServer(new MessagePickupVehicle((Entity) this));
                            return true;
                        }
                        if(!isContinuous) interactWithEntity(this, result);
                    }
                    return notRiding;
                }
            }
            return false;
        }

        /**
         * Mapping of static interaction boxes for the entity to lists of triangles that represent them
         * 
         * @return box to triangle map
         */
        @SideOnly(Side.CLIENT)
        default Map<RayTracePart, TriangleRayTraceList> getStaticInteractionBoxMap()
        {
            return Maps.newHashMap();
        }

        /**
         * Mapping of dynamic interaction boxes for the entity to lists of triangles that represent them
         * 
         * @return box to triangle map
         */
        @SideOnly(Side.CLIENT)
        default Map<RayTracePart, TriangleRayTraceList> getDynamicInteractionBoxMap()
        {
            return Maps.newHashMap();
        }

        /**
         * List of all currently applicable interaction boxes for the entity
         * 
         * @return box list - if null, all box are assumed to be applicable
         */
        @Nullable
        @SideOnly(Side.CLIENT)
        default List<RayTracePart> getApplicableInteractionBoxes()
        {
            return null;
        }

        /**
         * List of all currently non-applicable item parts for the entity
         * 
         * @return part list - if null, all parts are assumed to be applicable
         */
        @Nullable
        @SideOnly(Side.CLIENT)
        default List<RayTracePart> getNonApplicableParts()
        {
            return null;
        }

        /**
         * Opportunity to draw representations of applicable interaction boxes for the entity
         * 
         * @param tessellator rendered plane tiler
         * @param buffer tessellator's vertex buffer
         */
        @SideOnly(Side.CLIENT)
        default void drawInteractionBoxes(Tessellator tessellator, BufferBuilder buffer) {}
    }
}
