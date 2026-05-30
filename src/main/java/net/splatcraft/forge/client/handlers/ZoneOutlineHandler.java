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
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.splatcraft.forge.Splatcraft;
import net.splatcraft.forge.client.data.ClientMatchData;
import net.splatcraft.forge.data.Stage;
import net.splatcraft.forge.data.match.MatchType;
import net.splatcraft.forge.data.match.ZonesData;
import net.splatcraft.forge.items.remotes.ZoneMarkerItem;
import net.splatcraft.forge.util.ClientUtils;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = Splatcraft.MODID)
public class ZoneOutlineHandler
{
    private static final RenderType ZONE_RENDER = RenderType.create("splatcraft:zone",
        DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 4096, false, true,
        RenderType.CompositeState.builder()
            .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionColorShader))
            .setTransparencyState(new RenderStateShard.TransparencyStateShard("zone_transparency", () -> {
                RenderSystem.enableBlend();
                RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
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
        int[] zoneCtrls;

        if (ClientMatchData.currentMatchId != null && ClientMatchData.matchType == MatchType.ZONES)
        {
            zoneMins = ClientMatchData.zoneMins;
            zoneMaxs = ClientMatchData.zoneMaxs;
            zoneColors = ClientMatchData.teamColors;
            zoneCtrls = ClientMatchData.zoneControllers;
        }
        else
        {
            boolean holdsMarker = player.getMainHandItem().getItem() instanceof ZoneMarkerItem
                || player.getOffhandItem().getItem() instanceof ZoneMarkerItem;
            if (!holdsMarker) return;

            List<ZonesData> foundZones = new ArrayList<>();
            Vec3 ppos = player.position();
            for (Stage stage : ClientUtils.clientStages.values())
            {
                if (!stage.dimID.equals(mc.level.dimension().location())) continue;
                int minX = Math.min(stage.cornerA.getX(), stage.cornerB.getX());
                int minY = Math.min(stage.cornerA.getY(), stage.cornerB.getY());
                int minZ = Math.min(stage.cornerA.getZ(), stage.cornerB.getZ());
                int maxX = Math.max(stage.cornerA.getX(), stage.cornerB.getX());
                int maxY = Math.max(stage.cornerA.getY(), stage.cornerB.getY());
                int maxZ = Math.max(stage.cornerA.getZ(), stage.cornerB.getZ());
                if (ppos.x >= minX && ppos.x <= maxX && ppos.y >= minY && ppos.y <= maxY && ppos.z >= minZ && ppos.z <= maxZ)
                    foundZones.addAll(stage.getZones());
            }
            if (foundZones.isEmpty()) return;
            zoneMins = new BlockPos[foundZones.size()];
            zoneMaxs = new BlockPos[foundZones.size()];
            for (int i = 0; i < foundZones.size(); i++)
            {
                zoneMins[i] = foundZones.get(i).min;
                zoneMaxs[i] = foundZones.get(i).max;
            }
            zoneColors = new int[0];
            zoneCtrls = new int[0];
        }

        if (zoneMins.length == 0) return;

        PoseStack poseStack = event.getPoseStack();
        Vec3 cameraPos = event.getCamera().getPosition();

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        Matrix4f pose = poseStack.last().pose();
        Matrix3f normalMat = poseStack.last().normal();
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();

        for (int zi = 0; zi < zoneMins.length; zi++)
        {
            float r, g, b;
            int ziCtrl = zi < zoneCtrls.length ? zoneCtrls[zi] : -1;
            if (ziCtrl >= 0 && ziCtrl < zoneColors.length)
            {
                int color = zoneColors[ziCtrl];
                r = ((color >> 16) & 0xFF) / 255.0F; g = ((color >> 8) & 0xFF) / 255.0F; b = (color & 0xFF) / 255.0F;
            }
            else { r = 1.0F; g = 1.0F; b = 1.0F; }

            int bx1 = zoneMins[zi].getX(), bz1 = zoneMins[zi].getZ();
            int bx2 = zoneMaxs[zi].getX(), bz2 = zoneMaxs[zi].getZ();
            int minY = zoneMins[zi].getY(), maxY = zoneMaxs[zi].getY();
            int w = bx2 - bx1 + 1, h = bz2 - bz1 + 1;

            float[][] surface = new float[w][h];
            for (int sx = 0; sx < w; sx++)
                for (int sz = 0; sz < h; sz++)
                    surface[sx][sz] = getSurfaceY(mc, bx1 + sx, bz1 + sz, minY, maxY);

            VertexConsumer c = buffer.getBuffer(ZONE_RENDER);

            for (int sx = 0; sx < w; sx++)
                for (int sz = 0; sz < h; sz++)
                {
                    float sy = surface[sx][sz];
                    float x = bx1 + sx, z = bz1 + sz;

                    c.vertex(pose, x, sy, z).color(r, g, b, 0.15F).normal(normalMat, 0, 1, 0).endVertex();
                    c.vertex(pose, x+1, sy, z).color(r, g, b, 0.15F).normal(normalMat, 0, 1, 0).endVertex();
                    c.vertex(pose, x+1, sy, z+1).color(r, g, b, 0.15F).normal(normalMat, 0, 1, 0).endVertex();
                    c.vertex(pose, x, sy, z+1).color(r, g, b, 0.15F).normal(normalMat, 0, 1, 0).endVertex();
                }

            float wireA = 0.85F;
            float t = 0.04F;

            // bottom edge (z = bz1)
                for (int sx = 0; sx < w; sx++)
                {
                    float y0 = surface[sx][0];
                    float y1 = (sx + 1 < w) ? surface[sx + 1][0] : y0;
                    drawWireEdge(c, pose, normalMat, bx1 + sx, y0, bz1, bx1 + sx + 1, y0, bz1, t, r, g, b, wireA);
                    if (Math.abs(y0 - y1) > 0.01F)
                        drawWireEdge(c, pose, normalMat, bx1 + sx + 1, Math.min(y0, y1), bz1, bx1 + sx + 1, Math.max(y0, y1), bz1, t, r, g, b, wireA * 0.7F);
                }

                // top edge (z = bz2 + 1)
                for (int sx = 0; sx < w; sx++)
                {
                    float y0 = surface[sx][h - 1];
                    float y1 = (sx + 1 < w) ? surface[sx + 1][h - 1] : y0;
                    drawWireEdge(c, pose, normalMat, bx1 + sx + 1, y0, bz2 + 1, bx1 + sx, y0, bz2 + 1, t, r, g, b, wireA);
                    if (Math.abs(y0 - y1) > 0.01F)
                        drawWireEdge(c, pose, normalMat, bx1 + sx + 1, Math.min(y0, y1), bz2 + 1, bx1 + sx + 1, Math.max(y0, y1), bz2 + 1, t, r, g, b, wireA * 0.7F);
                }

                // left edge (x = bx1)
                for (int sz = 0; sz < h; sz++)
                {
                    float y0 = surface[0][sz];
                    float y1 = (sz + 1 < h) ? surface[0][sz + 1] : y0;
                    drawWireEdge(c, pose, normalMat, bx1, y0, bz1 + sz + 1, bx1, y0, bz1 + sz, t, r, g, b, wireA);
                    if (Math.abs(y0 - y1) > 0.01F)
                        drawWireEdge(c, pose, normalMat, bx1, Math.min(y0, y1), bz1 + sz + 1, bx1, Math.max(y0, y1), bz1 + sz + 1, t, r, g, b, wireA * 0.7F);
                }

                // right edge (x = bx2 + 1)
                for (int sz = 0; sz < h; sz++)
                {
                    float y0 = surface[w - 1][sz];
                    float y1 = (sz + 1 < h) ? surface[w - 1][sz + 1] : y0;
                    drawWireEdge(c, pose, normalMat, bx2 + 1, y0, bz1 + sz, bx2 + 1, y0, bz1 + sz + 1, t, r, g, b, wireA);
                    if (Math.abs(y0 - y1) > 0.01F)
                        drawWireEdge(c, pose, normalMat, bx2 + 1, Math.min(y0, y1), bz1 + sz + 1, bx2 + 1, Math.max(y0, y1), bz1 + sz + 1, t, r, g, b, wireA * 0.7F);
                }
        }

        poseStack.popPose();
        buffer.endBatch(ZONE_RENDER);
    }

    private static void drawWireEdge(VertexConsumer c, Matrix4f pose, Matrix3f n,
                                       float ax, float ay, float az, float bx, float by, float bz,
                                       float t, float r, float g, float b, float a)
    {
        float dx = Math.abs(bx - ax) < 0.01f ? t : 0;
        float dy = Math.abs(by - ay) < 0.01f ? t : 0;
        float dz = Math.abs(bz - az) < 0.01f ? t : 0;

        c.vertex(pose, ax - dx, ay - dy, az - dz).color(r, g, b, a).normal(n, 0, 1, 0).endVertex();
        c.vertex(pose, bx - dx, by - dy, bz - dz).color(r, g, b, a).normal(n, 0, 1, 0).endVertex();
        c.vertex(pose, bx + dx, by + dy, bz + dz).color(r, g, b, a).normal(n, 0, 1, 0).endVertex();
        c.vertex(pose, ax + dx, ay + dy, az + dz).color(r, g, b, a).normal(n, 0, 1, 0).endVertex();
    }

    private static float getSurfaceY(Minecraft mc, int x, int z, int minY, int maxY)
    {
        if (mc.level == null) return maxY;
        for (int y = maxY; y >= minY; y--)
        {
            BlockPos pos = new BlockPos(x, y, z);
            var state = mc.level.getBlockState(pos);
            if (!state.isAir() && state.blocksMotion() && state.getFluidState().isEmpty())
                return y + 1.005F;
        }
        return minY + 1.005F;
    }
}
