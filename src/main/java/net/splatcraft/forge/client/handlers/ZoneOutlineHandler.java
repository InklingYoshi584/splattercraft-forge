package net.splatcraft.forge.client.handlers;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.splatcraft.forge.Splatcraft;
import net.splatcraft.forge.client.data.ClientMatchData;
import net.splatcraft.forge.data.match.MatchType;
import net.splatcraft.forge.items.remotes.ZoneMarkerItem;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = Splatcraft.MODID)
public class ZoneOutlineHandler
{
    private static final RenderType ZONE_FILL = RenderType.create("splatcraft:zone_fill",
        DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 256, false, true,
        RenderType.CompositeState.builder()
            .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionColorShader))
            .setTransparencyState(new RenderStateShard.TransparencyStateShard("zone_fill_transparency", () -> {
                RenderSystem.enableBlend();
                RenderSystem.blendFuncSeparate(
                    GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            }, () -> {
                RenderSystem.disableBlend();
                RenderSystem.defaultBlendFunc();
            }))
            .setDepthTestState(new RenderStateShard.DepthTestStateShard("always", 519))
            .setCullState(new RenderStateShard.CullStateShard(false))
            .createCompositeState(false));

    @SubscribeEvent
    public static void renderLevel(RenderLevelStageEvent event)
    {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)
            return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;

        BlockPos[] zoneMins;
        BlockPos[] zoneMaxs;
        int[] zoneColors;
        int ctrlIdx;
        boolean inMatch;

        if (ClientMatchData.currentMatchId != null && ClientMatchData.matchType == MatchType.ZONES)
        {
            zoneMins = ClientMatchData.zoneMins;
            zoneMaxs = ClientMatchData.zoneMaxs;
            zoneColors = ClientMatchData.teamColors;
            ctrlIdx = ClientMatchData.controllingTeamIdx;
            inMatch = true;
        }
        else
        {
            ItemStack held = player.getMainHandItem();
            if (!(held.getItem() instanceof ZoneMarkerItem)) return;
            inMatch = false;
            zoneMins = new BlockPos[0];
            zoneMaxs = new BlockPos[0];
            zoneColors = new int[0];
            ctrlIdx = -1;
        }

        if (zoneMins.length == 0) return;

        PoseStack poseStack = event.getPoseStack();
        Vec3 cameraPos = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();
        VertexConsumer consumer = buffer.getBuffer(ZONE_FILL);

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        Matrix4f pose = poseStack.last().pose();
        Matrix3f normalMat = poseStack.last().normal();

        for (int zi = 0; zi < zoneMins.length; zi++)
        {
            float r, g, b, fillAlpha;
            if (inMatch && ctrlIdx >= 0 && ctrlIdx < zoneColors.length)
            {
                int color = zoneColors[ctrlIdx];
                r = ((color >> 16) & 0xFF) / 255.0F;
                g = ((color >> 8) & 0xFF) / 255.0F;
                b = (color & 0xFF) / 255.0F;
                fillAlpha = 0.12F;
            }
            else
            {
                r = 1.0F; g = 1.0F; b = 1.0F; fillAlpha = 0.08F;
            }

            float x1 = zoneMins[zi].getX();
            float y1 = zoneMins[zi].getY();
            float z1 = zoneMins[zi].getZ();
            float x2 = zoneMaxs[zi].getX() + 1;
            float y2 = zoneMaxs[zi].getY() + 1;
            float z2 = zoneMaxs[zi].getZ() + 1;

            renderBoxFaces(consumer, pose, normalMat, x1, y1, z1, x2, y2, z2, r, g, b, fillAlpha);
        }

        poseStack.popPose();
        buffer.endBatch(ZONE_FILL);
    }

    private static void renderBoxFaces(VertexConsumer c, Matrix4f pose, Matrix3f normal,
                                        float x1, float y1, float z1, float x2, float y2, float z2,
                                        float r, float g, float b, float a)
    {
        addQuad(c, pose, normal, x1, x2, y1, z1, z2, r, g, b, a);
        addQuad(c, pose, normal, x1, x2, y2, z1, z2, r, g, b, a);

        addQuad(c, pose, normal, x1, x2, z1, y1, y2, r, g, b, a);
        addQuad(c, pose, normal, x1, x2, z2, y1, y2, r, g, b, a);

        addQuad(c, pose, normal, z1, z2, x1, y1, y2, r, g, b, a);
        addQuad(c, pose, normal, z1, z2, x2, y1, y2, r, g, b, a);
    }

    private static void addQuad(VertexConsumer c, Matrix4f pose, Matrix3f normal,
                                 float u1, float u2, float fixed, float v1, float v2,
                                 float r, float g, float b, float a)
    {
        c.vertex(pose, u1, fixed, v1).color(r, g, b, a).normal(normal, 0, 1, 0).endVertex();
        c.vertex(pose, u2, fixed, v1).color(r, g, b, a).normal(normal, 0, 1, 0).endVertex();
        c.vertex(pose, u2, fixed, v2).color(r, g, b, a).normal(normal, 0, 1, 0).endVertex();
        c.vertex(pose, u1, fixed, v2).color(r, g, b, a).normal(normal, 0, 1, 0).endVertex();
    }
}
