package com.projecthero.mod.client.ultron;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ultron.block.UltronCoreBlockEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;

import org.joml.Matrix4f;

/**
 * v0.15.12: the Ultron Core's eye -- a red iris (an octagon with a hot centre) that slowly turns about the vertical,
 * hovering in the pedestal's socket, plus a faint halo ring. Full-bright: glow quads first, then the additive core.
 */
public class UltronCoreRenderer implements BlockEntityRenderer<UltronCoreBlockEntity> {
	public UltronCoreRenderer(BlockEntityRendererProvider.Context ctx) {
	}

	@Override
	public void render(UltronCoreBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
		long time = be.getLevel() != null ? be.getLevel().getGameTime() : Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0;
		float t = time + partialTick;
		float pulse = 0.75f + 0.25f * Mth.sin(t * 0.15f);
		pose.pushPose();
		pose.translate(0.5, 0.86 + 0.03 * Mth.sin(t * 0.08f), 0.5);
		pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(t * 1.5f));
		Matrix4f m = pose.last().pose();
		VertexConsumer glow = buffers.getBuffer(RenderType.debugQuads());
		disc(glow, m, 0.15f, 255, 20, 12, (int) (150 * pulse));
		ring(glow, m, 0.2f, 0.025f, 255, 40, 30, (int) (120 * pulse));
		VertexConsumer core = buffers.getBuffer(RenderType.lightning());
		disc(core, m, 0.06f, 255, 170, 150, (int) (230 * pulse));
		pose.popPose();
	}

	/** A vertical octagon in the local XY plane (both windings). */
	private static void disc(VertexConsumer vc, Matrix4f m, float r, int cr, int cg, int cb, int a) {
		int n = 8;
		for (int i = 0; i < n; i++) {
			double a0 = Math.PI * 2 * i / n;
			double a1 = Math.PI * 2 * (i + 1) / n;
			float x0 = (float) Math.cos(a0) * r, y0 = (float) Math.sin(a0) * r;
			float x1 = (float) Math.cos(a1) * r, y1 = (float) Math.sin(a1) * r;
			vc.addVertex(m, 0, 0, 0).setColor(cr, cg, cb, a);
			vc.addVertex(m, x0, y0, 0).setColor(cr, cg, cb, a);
			vc.addVertex(m, x1, y1, 0).setColor(cr, cg, cb, a);
			vc.addVertex(m, 0, 0, 0).setColor(cr, cg, cb, a);
			vc.addVertex(m, 0, 0, 0).setColor(cr, cg, cb, a);
			vc.addVertex(m, x1, y1, 0).setColor(cr, cg, cb, a);
			vc.addVertex(m, x0, y0, 0).setColor(cr, cg, cb, a);
			vc.addVertex(m, 0, 0, 0).setColor(cr, cg, cb, a);
		}
	}

	/** A thin vertical ring round the iris. */
	private static void ring(VertexConsumer vc, Matrix4f m, float r, float w, int cr, int cg, int cb, int a) {
		int n = 16;
		for (int i = 0; i < n; i++) {
			double a0 = Math.PI * 2 * i / n;
			double a1 = Math.PI * 2 * (i + 1) / n;
			float ox0 = (float) Math.cos(a0) * (r + w), oy0 = (float) Math.sin(a0) * (r + w);
			float ox1 = (float) Math.cos(a1) * (r + w), oy1 = (float) Math.sin(a1) * (r + w);
			float ix0 = (float) Math.cos(a0) * r, iy0 = (float) Math.sin(a0) * r;
			float ix1 = (float) Math.cos(a1) * r, iy1 = (float) Math.sin(a1) * r;
			vc.addVertex(m, ix0, iy0, 0).setColor(cr, cg, cb, a);
			vc.addVertex(m, ox0, oy0, 0).setColor(cr, cg, cb, a);
			vc.addVertex(m, ox1, oy1, 0).setColor(cr, cg, cb, a);
			vc.addVertex(m, ix1, iy1, 0).setColor(cr, cg, cb, a);
			vc.addVertex(m, ix1, iy1, 0).setColor(cr, cg, cb, a);
			vc.addVertex(m, ox1, oy1, 0).setColor(cr, cg, cb, a);
			vc.addVertex(m, ox0, oy0, 0).setColor(cr, cg, cb, a);
			vc.addVertex(m, ix0, iy0, 0).setColor(cr, cg, cb, a);
		}
	}
}
