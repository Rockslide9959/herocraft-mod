package com.projecthero.mod.client.alarm;

import java.util.Map;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.7: guard marks and sniper lasers in the world, for the viewer only (sent by the server to this player). A small
 * amber "?" floats over a guard whose suspicion meter is filling, a red "!" over an alerted one; each Sniper draws a thin
 * red line from its eyes to its target, brighter in the last second before it fires.
 */
public final class AlarmWorldRender {
	private AlarmWorldRender() {
	}

	static void render(WorldRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || ctx.consumers() == null || ctx.matrixStack() == null || AlarmClient.state() == null) {
			return;
		}
		float partial = ctx.tickCounter().getGameTimeDeltaPartialTick(false);
		PoseStack pose = ctx.matrixStack();
		Vec3 cam = ctx.camera().getPosition();
		MultiBufferSource buffers = ctx.consumers();
		Font font = mc.font;
		for (Map.Entry<Integer, Integer> m : AlarmClient.marks().entrySet()) {
			Entity e = mc.level.getEntity(m.getKey());
			if (e == null || !e.isAlive()) {
				continue;
			}
			boolean alert = m.getValue() == 2;
			String text = alert ? "!" : "?";
			int col = alert ? 0xFFFF3020 : 0xFFFFC030;
			Vec3 at = e.getPosition(partial).add(0, e.getBbHeight() + 0.55, 0);
			double d = at.distanceTo(cam);
			pose.pushPose();
			pose.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
			pose.mulPose(ctx.camera().rotation());
			float sc = 0.045f * (float) Math.max(1.0, d / 16.0);
			float bob = (float) Math.sin((mc.level.getGameTime() + partial) * 0.3) * 1.5f;
			pose.scale(sc, -sc, sc);
			Matrix4f mat = pose.last().pose();
			font.drawInBatch(text, -font.width(text) / 2f, bob, col, false, mat, buffers, Font.DisplayMode.SEE_THROUGH, 0, LightTexture.FULL_BRIGHT);
			pose.popPose();
		}
		if (AlarmClient.lasers().isEmpty()) {
			return;
		}
		VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
		for (int[] l : AlarmClient.lasers()) {
			Entity s = mc.level.getEntity(l[0]);
			Entity t = mc.level.getEntity(l[1]);
			if (s == null || t == null) {
				continue;
			}
			Vec3 a = s.getEyePosition(partial).subtract(cam);
			Vec3 b = t.getPosition(partial).add(0, t.getBbHeight() * 0.6, 0).subtract(cam);
			float alpha = l[2] == 1 ? 0.9f : 0.45f;
			float w = l[2] == 1 ? 0.035f : 0.02f;
			beam(pose.last().pose(), vc, a, b, w, 1f, 0.1f, 0.05f, alpha);
		}
	}

	/** A thin glowing beam (two crossed quads) from {@code a} to {@code b} (camera-relative). */
	static void beam(Matrix4f m, VertexConsumer vc, Vec3 a, Vec3 b, float w, float r, float g, float bl, float alpha) {
		Vec3 dir = b.subtract(a);
		if (dir.lengthSqr() < 1.0e-6) {
			return;
		}
		Vec3 up = Math.abs(dir.normalize().y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
		Vec3 s1 = dir.cross(up).normalize().scale(w);
		Vec3 s2 = dir.cross(s1).normalize().scale(w);
		for (Vec3 s : new Vec3[] { s1, s2 }) {
			quad(m, vc, a.add(s), a.subtract(s), b.subtract(s), b.add(s), r, g, bl, alpha);
			quad(m, vc, b.add(s), b.subtract(s), a.subtract(s), a.add(s), r, g, bl, alpha);
		}
	}

	private static void quad(Matrix4f m, VertexConsumer vc, Vec3 p1, Vec3 p2, Vec3 p3, Vec3 p4, float r, float g, float b, float a) {
		vc.addVertex(m, (float) p1.x, (float) p1.y, (float) p1.z).setColor(r, g, b, a);
		vc.addVertex(m, (float) p2.x, (float) p2.y, (float) p2.z).setColor(r, g, b, a);
		vc.addVertex(m, (float) p3.x, (float) p3.y, (float) p3.z).setColor(r, g, b, a);
		vc.addVertex(m, (float) p4.x, (float) p4.y, (float) p4.z).setColor(r, g, b, a);
	}
}
