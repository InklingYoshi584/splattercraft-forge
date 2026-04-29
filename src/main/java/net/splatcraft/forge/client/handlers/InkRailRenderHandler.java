package net.splatcraft.forge.client.handlers;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.util.FastColor;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.splatcraft.forge.Splatcraft;
import net.splatcraft.forge.tileentities.AbstractInkRailTileEntity;
import net.splatcraft.forge.tileentities.InkRailTileEntity;
import net.splatcraft.forge.util.InkRailUtils;

@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = Splatcraft.MODID)
public class InkRailRenderHandler
{
    private static final double BEAM_HALF_WIDTH = 0.36D;
    private static final RenderStateShard.TransparencyStateShard TRANSLUCENT_TRANSPARENCY = new RenderStateShard.TransparencyStateShard("ink_rail_translucent", () -> {
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
    }, () -> {
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    });
    private static final RenderType BEAM_FILL = RenderType.create("splatcraft:ink_rail_beam_fill", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 256, false, true,
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

        ClientLevel level = minecraft.level;
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();
        Vec3 cameraPos = event.getCamera().getPosition();
        int chunkRadius = minecraft.options.getEffectiveRenderDistance();
        int cameraChunkX = net.minecraft.core.SectionPos.blockToSectionCoord((int) Math.floor(cameraPos.x));
        int cameraChunkZ = net.minecraft.core.SectionPos.blockToSectionCoord((int) Math.floor(cameraPos.z));

        for (int chunkX = cameraChunkX - chunkRadius; chunkX <= cameraChunkX + chunkRadius; chunkX++)
        {
            for (int chunkZ = cameraChunkZ - chunkRadius; chunkZ <= cameraChunkZ + chunkRadius; chunkZ++)
            {
                LevelChunk chunk = level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
                if (chunk == null)
                    continue;

                for (net.minecraft.world.level.block.entity.BlockEntity blockEntity : chunk.getBlockEntities().values())
                {
                    if (!(blockEntity instanceof AbstractInkRailTileEntity tileEntity))
                        continue;

                    if (tileEntity.getBlockPos().distToCenterSqr(cameraPos.x, cameraPos.y, cameraPos.z) > 4096.0D)
                        continue;

                    renderRail(tileEntity, poseStack, buffer, cameraPos, event.getPartialTick());
                }
            }
        }

        buffer.endBatch();
    }

    private static void renderRail(AbstractInkRailTileEntity tileEntity, PoseStack poseStack, MultiBufferSource buffer, Vec3 cameraPos, float partialTicks)
    {
        if (tileEntity.getLevel() == null)
            return;

        InkRailTileEntity host = InkRailUtils.getHost(tileEntity.getLevel(), tileEntity.getBlockPos());
        if (host == null || !host.isActive())
            return;

        Vec3 startWorld = InkRailUtils.getConnectionPoint(tileEntity.getLevel(), tileEntity.getBlockPos());
        poseStack.pushPose();
        poseStack.translate(startWorld.x - cameraPos.x, startWorld.y - cameraPos.y, startWorld.z - cameraPos.z);
        org.joml.Matrix4f matrix = poseStack.last().pose();
        int color = host.getColor();
        float alpha = host.isFlashing() ? 0.35F + 0.35F * (float) ((Math.sin((tileEntity.getLevel().getGameTime() + partialTicks) * 0.7D) + 1.0D) * 0.5D) : 0.82F;
        float red = FastColor.ARGB32.red(color | 0xFF000000) / 255.0F;
        float green = FastColor.ARGB32.green(color | 0xFF000000) / 255.0F;
        float blue = FastColor.ARGB32.blue(color | 0xFF000000) / 255.0F;

        VertexConsumer quadConsumer = buffer.getBuffer(BEAM_FILL);

        for (net.minecraft.core.BlockPos link : InkRailUtils.getActiveLinks(tileEntity.getLevel(), tileEntity.getBlockPos()))
        {
            if (tileEntity.getBlockPos().asLong() >= link.asLong())
                continue;

            Vec3 delta = InkRailUtils.getConnectionPoint(tileEntity.getLevel(), link).subtract(startWorld);
            Vec3 beamDir = delta.normalize();
            Vec3 right = beamDir.cross(new Vec3(0.0D, 1.0D, 0.0D));
            if (right.lengthSqr() < 1.0E-4D)
                right = beamDir.cross(new Vec3(1.0D, 0.0D, 0.0D));
            right = right.normalize().scale(BEAM_HALF_WIDTH);
            Vec3 up = beamDir.cross(right).normalize().scale(BEAM_HALF_WIDTH);

            addBeamQuad(quadConsumer, matrix, delta, right, red, green, blue, alpha);
            addBeamQuad(quadConsumer, matrix, delta, up, red, green, blue, alpha);
            addBeamQuad(quadConsumer, matrix, delta, right.add(up).normalize().scale(BEAM_HALF_WIDTH), red, green, blue, alpha * 0.72F);
            addBeamQuad(quadConsumer, matrix, delta, right.subtract(up).normalize().scale(BEAM_HALF_WIDTH), red, green, blue, alpha * 0.72F);
        }

        VertexConsumer lineConsumer = buffer.getBuffer(RenderType.lines());
        for (net.minecraft.core.BlockPos link : InkRailUtils.getActiveLinks(tileEntity.getLevel(), tileEntity.getBlockPos()))
        {
            if (tileEntity.getBlockPos().asLong() >= link.asLong())
                continue;

            Vec3 delta = InkRailUtils.getConnectionPoint(tileEntity.getLevel(), link).subtract(startWorld);
            Vec3 beamDir = delta.normalize();
            Vec3 right = beamDir.cross(new Vec3(0.0D, 1.0D, 0.0D));
            if (right.lengthSqr() < 1.0E-4D)
                right = beamDir.cross(new Vec3(1.0D, 0.0D, 0.0D));
            right = right.normalize().scale(BEAM_HALF_WIDTH * 0.55D);

            addBeamLine(lineConsumer, matrix, Vec3.ZERO, delta, Vec3.ZERO, red, green, blue);
            addBeamLine(lineConsumer, matrix, Vec3.ZERO, delta, right, red, green, blue);
            addBeamLine(lineConsumer, matrix, Vec3.ZERO, delta, right.scale(-1.0D), red, green, blue);
        }

        poseStack.popPose();
    }

    private static void addBeamQuad(VertexConsumer consumer, org.joml.Matrix4f matrix, Vec3 end, Vec3 offset, float red, float green, float blue, float alpha)
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

    private static void addBeamLine(VertexConsumer consumer, org.joml.Matrix4f matrix, Vec3 start, Vec3 end, Vec3 offset, float red, float green, float blue)
    {
        Vec3 from = start.add(offset);
        Vec3 to = end.add(offset);
        consumer.vertex(matrix, (float) from.x, (float) from.y, (float) from.z).color(red, green, blue, 1.0F).normal(0.0F, 1.0F, 0.0F).endVertex();
        consumer.vertex(matrix, (float) to.x, (float) to.y, (float) to.z).color(red, green, blue, 1.0F).normal(0.0F, 1.0F, 0.0F).endVertex();
    }
}
