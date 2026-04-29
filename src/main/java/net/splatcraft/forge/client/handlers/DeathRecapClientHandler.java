package net.splatcraft.forge.client.handlers;

import java.util.UUID;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.splatcraft.forge.Splatcraft;
import net.splatcraft.forge.client.gui.DeathRecapScreen;

@Mod.EventBusSubscriber(modid = Splatcraft.MODID, value = Dist.CLIENT)
public class DeathRecapClientHandler
{
    private static final float RECAP_CAMERA_YAW = 45.0F;
    private static final float RECAP_CAMERA_PITCH = 40.0F;
    private static final double RECAP_CAMERA_DISTANCE = 7.0D;

    private static DeathRecapData pendingRecap;
    private static DeathRecapData activeRecap;
    private static boolean respawnRequested;

    public static void startRecap(DeathRecapData recap)
    {
        pendingRecap = recap;
        respawnRequested = false;
        maybeOpenRecapScreen(Minecraft.getInstance());
    }

    public static void requestRespawn()
    {
        respawnRequested = true;
    }

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event)
    {
        if (!(event.getNewScreen() instanceof DeathScreen))
            return;

        DeathRecapData recap = getQueuedRecap();
        if (recap == null)
            return;

        event.setNewScreen(new DeathRecapScreen(recap));
        activateRecap(recap, Minecraft.getInstance());
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
            return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null)
        {
            clearRecap(minecraft, false);
            return;
        }

        if (activeRecap == null)
        {
            maybeOpenRecapScreen(minecraft);
            return;
        }

        if (minecraft.getCameraEntity() != minecraft.player)
            minecraft.setCameraEntity(minecraft.player);

        if (!minecraft.player.isDeadOrDying())
        {
            clearRecap(minecraft, minecraft.screen instanceof DeathRecapScreen);
            return;
        }

        if (!(minecraft.screen instanceof DeathRecapScreen) && !respawnRequested)
            maybeOpenRecapScreen(minecraft);
    }

    @SubscribeEvent
    public static void onRenderGuiOverlay(RenderGuiOverlayEvent.Pre event)
    {
        if (isRecapScreenOpen())
            event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event)
    {
        if (isRecapScreenOpen())
            event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onClientLogOut(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event)
    {
        clearRecap(Minecraft.getInstance(), false);
    }

    private static void maybeOpenRecapScreen(Minecraft minecraft)
    {
        DeathRecapData recap = getQueuedRecap();
        if (recap == null || minecraft.player == null)
            return;

        if (!(minecraft.screen instanceof DeathScreen || minecraft.screen instanceof DeathRecapScreen))
            return;

        if (minecraft.screen instanceof DeathRecapScreen)
            return;

        activateRecap(recap, minecraft);
        minecraft.setScreen(new DeathRecapScreen(recap));
    }

    private static void activateRecap(DeathRecapData recap, Minecraft minecraft)
    {
        activeRecap = recap;
        pendingRecap = null;
    }

    private static DeathRecapData getQueuedRecap()
    {
        return pendingRecap != null ? pendingRecap : activeRecap;
    }

    public static boolean applyCameraOverride(Camera camera, Entity entity, float partialTick)
    {
        if (activeRecap == null || !isRecapScreenOpen())
            return false;

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer localPlayer = minecraft.player;
        if (localPlayer == null || minecraft.level == null || !localPlayer.isDeadOrDying())
            return false;

        Vec3 focus = activeRecap.deathPos().add(0.0D, 1.0D, 0.0D);

        if (activeRecap.killerId() != null) {
            Entity killer = minecraft.level.players().stream().filter(candidate -> activeRecap.killerId().equals(candidate.getUUID())).findFirst().orElse(null);
            if (killer != null)
                focus = new Vec3(Mth.lerp(partialTick, killer.xo, killer.getX()), Mth.lerp(partialTick, killer.yo, killer.getY()) + killer.getBbHeight() * 0.75D, Mth.lerp(partialTick, killer.zo, killer.getZ()));
        }

        Vec3 lookVector = Vec3.directionFromRotation(RECAP_CAMERA_PITCH, RECAP_CAMERA_YAW).normalize();
        Vec3 cameraPos = focus.subtract(lookVector.scale(RECAP_CAMERA_DISTANCE));
        CameraAccessor accessor = (CameraAccessor)camera;

        accessor.splatcraft$setRotation(RECAP_CAMERA_YAW, RECAP_CAMERA_PITCH);
        accessor.splatcraft$setPosition(cameraPos);
        return true;
    }

    private static void clearRecap(Minecraft minecraft, boolean closeScreen)
    {
        pendingRecap = null;
        activeRecap = null;
        respawnRequested = false;

        if (closeScreen)
            minecraft.setScreen(null);
    }

    public static boolean isRecapScreenOpen()
    {
        return Minecraft.getInstance().screen instanceof DeathRecapScreen;
    }

    public static boolean shouldHoldLocalPlayerDeath()
    {
        return activeRecap != null && !respawnRequested;
    }

    public record DeathRecapData(Component deathMessage, Vec3 deathPos, ResourceLocation deathDimension, UUID killerId, int inkColor, int respawnDelayTicks)
    {
    }

    public interface CameraAccessor
    {
        void splatcraft$setPosition(Vec3 position);
        void splatcraft$setRotation(float yaw, float pitch);
    }
}
