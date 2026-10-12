package com.projecthero.mod.client.ultron.contagion;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.ultron.contagion.block.OmegaCoreBlock;
import com.projecthero.mod.ultron.contagion.block.OmegaCoreBlockEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

import org.joml.Matrix4f;

/**
 * v0.16.1: the Omega Core trophy's eyes -- two red slits on the head's face that slowly dim almost to nothing and flare
 * back, with a faint glow round each. Full-bright, glow quads first, then the additive core.
 */
public class OmegaCoreRenderer implements BlockEntityRenderer<OmegaCoreBlockEntity> {
	public OmegaCoreRenderer(BlockEntityRendererProvider.Context ctx) {
	}

	@Override
	public void render(OmegaCoreBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
		long time = be.getLevel() != null ? be.getLevel().getGameTime() : Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0;
		float t = time + partialTick;
		float k = 0.15f + 0.85f * (0.5f + 0.5f * Mth.sin(t * 0.045f));
		Direction facing = be.getBlockState().hasProperty(OmegaCoreBlock.FACING) ? be.getBlockState().getValue(OmegaCoreBlock.FACING) : Direction.NORTH;
		pose.pushPose();
		pose.translate(0.5, 0, 0.5);
		pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot() + 180f));
		pose.translate(-0.5, 0, -0.5);
		Matrix4f m = pose.last().pose();
		float z = 4.5f / 16f - 0.002f;
		VertexConsumer glow = buffers.getBuffer(RenderType.debugQuads());
		quad(glow, m, 5.0f, 11.6f, 7.5f, 13.4f, z - 0.001f, 255, 30, 20, (int) (90 * k));
		quad(glow, m, 8.5f, 11.6f, 11.0f, 13.4f, z - 0.001f, 255, 30, 20, (int) (90 * k));
		VertexConsumer core = buffers.getBuffer(RenderType.lightning());
		quad(core, m, 5.5f, 12.2f, 7.0f, 12.8f, z - 0.002f, 255, 60, 40, (int) (230 * k));
		quad(core, m, 9.0f, 12.2f, 10.5f, 12.8f, z - 0.002f, 255, 60, 40, (int) (230 * k));
		pose.popPose();
	}

	/** A flat quad facing -Z at depth {@code z} (block units), x / y in model pixels; both windings. */
	private static void quad(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, float z, int r, int g, int b, int a) {
		float ax = x0 / 16f, ay = y0 / 16f, bx = x1 / 16f, by = y1 / 16f;
		vc.addVertex(m, ax, ay, z).setColor(r, g, b, a);
		vc.addVertex(m, bx, ay, z).setColor(r, g, b, a);
		vc.addVertex(m, bx, by, z).setColor(r, g, b, a);
		vc.addVertex(m, ax, by, z).setColor(r, g, b, a);
		vc.addVertex(m, ax, by, z).setColor(r, g, b, a);
		vc.addVertex(m, bx, by, z).setColor(r, g, b, a);
		vc.addVertex(m, bx, ay, z).setColor(r, g, b, a);
		vc.addVertex(m, ax, ay, z).setColor(r, g, b, a);
	}
}
