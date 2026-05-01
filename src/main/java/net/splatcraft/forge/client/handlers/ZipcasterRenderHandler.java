package net.splatcraft.forge.client.handlers;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.FastColor;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.splatcraft.forge.Splatcraft;
import net.splatcraft.forge.data.capabilities.playerinfo.PlayerInfoCapability;
import net.splatcraft.forge.items.weapons.ZipcasterSpecialItem;
import net.splatcraft.forge.util.ColorUtils;
import org.joml.Matrix4f;

@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = Splatcraft.MODID)
public class ZipcasterRenderHandler
{
    private static final double BEAM_HALF_WIDTH = 0.22D;
    private static final float MARKER_HEIGHT = 5.0F;
    private static final RenderStateShard.TransparencyStateShard TRANSLUCENT_TRANSPARENCY = new RenderStateShard.TransparencyStateShard("zipcaster_translucent", () -> {
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
    }, () -> {
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    });
    private static final RenderType MARKER_FILL = RenderType.create("splatcraft:zipcaster_marker_fill", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLE_FAN, 256, false, true,
            RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionColorShader))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(new RenderStateShard.CullStateShard(false))
                    .createCompositeState(false));
    private static final RenderType BEAM_FILL = RenderType.create("splatcraft:zipcaster_beam_fill", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 256, false, true,
            RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionColorShader))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(new RenderStateShard.CullStateShard(false))
                    .createCompositeState(false));

    @SubscribeEvent
    public static void renderLevel(RenderLevelStageEvent event)
    {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)
            return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null)
            return;

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();
        Vec3 cameraPos = event.getCamera().getPosition();

        for (Player player : minecraft.level.players())
        {
            if (!hasVisibleZipcasterMarker(player))
                continue;

            Vec3 recall = getRecallMarkerPos(player);
            if (recall != null)
                renderMarker(poseStack, buffer, cameraPos, recall, getMarkerColor(player), 0.9F);

            Vec3 anchor = ZipcasterSpecialItem.getAnchorPos(player);
            if (anchor != null && ZipcasterSpecialItem.getPhase(player) == ZipcasterSpecialItem.PHASE_REEL)
            {
                renderAnchorMarker(poseStack, buffer, cameraPos, anchor, getMarkerColor(player), 0.45F);
                renderBeam(poseStack, buffer, cameraPos, player, anchor, getMarkerColor(player));
            }
        }

        buffer.endBatch();
    }

    private static int getMarkerColor(Player player)
    {
        int color = ColorUtils.getPlayerColor(player);
        return color == -1 ? 0xFFFFFF : color;
    }

    private static void renderMarker(PoseStack poseStack, MultiBufferSource buffer, Vec3 cameraPos, Vec3 position, int color, float radius)
    {
        poseStack.pushPose();
        poseStack.translate(position.x - cameraPos.x, position.y + 0.05D - cameraPos.y, position.z - cameraPos.z);
        addDisk(buffer.getBuffer(MARKER_FILL), poseStack.last().pose(), radius, color, 0.25F);
        addBeacon(buffer.getBuffer(RenderType.lines()), poseStack.last().pose(), color);
        poseStack.popPose();
    }

    private static void renderAnchorMarker(PoseStack poseStack, MultiBufferSource buffer, Vec3 cameraPos, Vec3 position, int color, float radius)
    {
        poseStack.pushPose();
        poseStack.translate(position.x - cameraPos.x, position.y + 0.05D - cameraPos.y, position.z - cameraPos.z);
        addDisk(buffer.getBuffer(MARKER_FILL), poseStack.last().pose(), radius, color, 0.18F);
        poseStack.popPose();
    }

    private static void addBeacon(VertexConsumer consumer, Matrix4f pose, int color)
    {
        float red = ((color >> 16) & 0xFF) / 255.0F;
        float green = ((color >> 8) & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;

        addBeamLine(consumer, pose, new Vec3(0.18D, 0.0D, 0.0D), new Vec3(0.18D, MARKER_HEIGHT, 0.0D), Vec3.ZERO, red, green, blue);
        addBeamLine(consumer, pose, new Vec3(-0.18D, 0.0D, 0.0D), new Vec3(-0.18D, MARKER_HEIGHT, 0.0D), Vec3.ZERO, red, green, blue);
        addBeamLine(consumer, pose, new Vec3(0.0D, 0.0D, 0.18D), new Vec3(0.0D, MARKER_HEIGHT, 0.18D), Vec3.ZERO, red, green, blue);
        addBeamLine(consumer, pose, new Vec3(0.0D, 0.0D, -0.18D), new Vec3(0.0D, MARKER_HEIGHT, -0.18D), Vec3.ZERO, red, green, blue);
    }

    private static Vec3 getRecallMarkerPos(Player player)
    {
        return ZipcasterSpecialItem.getRecallPos(player);
    }

    private static boolean hasVisibleZipcasterMarker(Player player)
    {
        return PlayerInfoCapability.hasCapability(player)
                && PlayerInfoCapability.get(player).hasActiveSpecial()
                && ZipcasterSpecialItem.isActive(player);
    }

    private static void renderBeam(PoseStack poseStack, MultiBufferSource buffer, Vec3 cameraPos, Player player, Vec3 anchor, int color)
    {
        Vec3 start = player.position().add(0.0D, player.getBbHeight() * 0.55D, 0.0D);
        poseStack.pushPose();
        poseStack.translate(start.x - cameraPos.x, start.y - cameraPos.y, start.z - cameraPos.z);
        Matrix4f matrix = poseStack.last().pose();
        float red = FastColor.ARGB32.red(color | 0xFF000000) / 255.0F;
        float green = FastColor.ARGB32.green(color | 0xFF000000) / 255.0F;
        float blue = FastColor.ARGB32.blue(color | 0xFF000000) / 255.0F;
        Vec3 delta = anchor.subtract(start);
        Vec3 beamDir = delta.normalize();
        Vec3 right = beamDir.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (right.lengthSqr() < 1.0E-4D)
            right = beamDir.cross(new Vec3(1.0D, 0.0D, 0.0D));
        right = right.normalize().scale(BEAM_HALF_WIDTH);
        Vec3 up = beamDir.cross(right).normalize().scale(BEAM_HALF_WIDTH);

        VertexConsumer quadConsumer = buffer.getBuffer(BEAM_FILL);
        addBeamQuad(quadConsumer, matrix, delta, right, red, green, blue, 0.75F);
        addBeamQuad(quadConsumer, matrix, delta, up, red, green, blue, 0.75F);
        addBeamQuad(quadConsumer, matrix, delta, right.add(up).normalize().scale(BEAM_HALF_WIDTH), red, green, blue, 0.55F);
        addBeamQuad(quadConsumer, matrix, delta, right.subtract(up).normalize().scale(BEAM_HALF_WIDTH), red, green, blue, 0.55F);

        VertexConsumer lineConsumer = buffer.getBuffer(RenderType.lines());
        addBeamLine(lineConsumer, matrix, Vec3.ZERO, delta, Vec3.ZERO, red, green, blue);
        addBeamLine(lineConsumer, matrix, Vec3.ZERO, delta, right.scale(0.55D), red, green, blue);
        addBeamLine(lineConsumer, matrix, Vec3.ZERO, delta, right.scale(-0.55D), red, green, blue);
        poseStack.popPose();
    }

    private static void addBeamQuad(VertexConsumer consumer, Matrix4f matrix, Vec3 end, Vec3 offset, float red, float green, float blue, float alpha)
    {
        Vec3 startA = offset;
        Vec3 startB = offset.scale(-1.0D);
        Vec3 endA = end.add(offset);
        Vec3 endB = end.subtract(offset);
        consumer.vertex(matrix, (float) startA.x, (float) startA.y, (float) startA.z).color(red, green, blue, alpha).endVertex();
        consumer.vertex(matrix, (float) endA.x, (float) endA.y, (float) endA.z).color(red, green, blue, alpha).endVertex();
        consumer.vertex(matrix, (float) endB.x, (float) endB.y, (float) endB.z).color(red, green, blue, alpha).endVertex();
        consumer.vertex(matrix, (float) startB.x, (float) startB.y, (float) startB.z).color(red, green, blue, alpha).endVertex();
    }

    private static void addBeamLine(VertexConsumer consumer, Matrix4f matrix, Vec3 start, Vec3 end, Vec3 offset, float red, float green, float blue)
    {
        Vec3 from = start.add(offset);
        Vec3 to = end.add(offset);
        consumer.vertex(matrix, (float) from.x, (float) from.y, (float) from.z).color(red, green, blue, 1.0F).normal(0.0F, 1.0F, 0.0F).endVertex();
        consumer.vertex(matrix, (float) to.x, (float) to.y, (float) to.z).color(red, green, blue, 1.0F).normal(0.0F, 1.0F, 0.0F).endVertex();
    }

    private static void addDisk(VertexConsumer consumer, Matrix4f pose, float radius, int color, float alpha)
    {
        float red = ((color >> 16) & 0xFF) / 255.0F;
        float green = ((color >> 8) & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        consumer.vertex(pose, 0.0F, 0.0F, 0.0F).color(red, green, blue, alpha).endVertex();
        for (int i = 0; i <= 28; i++)
        {
            double angle = Math.PI * 2.0D * i / 28.0D;
            consumer.vertex(pose, (float) (Math.cos(angle) * radius), 0.0F, (float) (Math.sin(angle) * radius)).color(red, green, blue, alpha).endVertex();
        }
    }
}
