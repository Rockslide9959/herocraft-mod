package com.projecthero.mod.client.thoracquire;

import java.util.Map;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.client.thor.ThorDraw;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.7: the small permanent storm cloud over every Mjolnir's Crater that still holds its hammer -- a slowly turning knot of
 * dark storm puffs ({@link #ALT} blocks above the hammer, billboarded vanilla smoke sprites tinted slate grey) that lights from
 * inside every few seconds with a silent flicker and a short bolt licking down out of it. Visible as far as the world is
 * drawn. During a Trial of Asgard it grows, darkens and churns faster, flickers constantly, and bolts reach the ground, where a
 * ring of light marks the plinth.
 */
public final class CraterCloudRenderer {
	public static final int ALT = 24;
	/** A soft lumpy puff of our own (scratchpad/v0177/gen_thor_acquire_assets.js); vanilla particle sprites live in the atlas. */
	private static final ResourceLocation[] PUFFS = { com.projecthero.mod.ProjectHeroMod.id("textures/misc/storm_puff.png") };

	private CraterCloudRenderer() {
	}

	static void render(WorldRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || ctx.consumers() == null || ctx.matrixStack() == null) {
			return;
		}
		if (ThorAcquireClient.CRATERS.isEmpty() && ThorAcquireClient.TRIALS.isEmpty()) {
			return;
		}
		float partial = ctx.tickCounter().getGameTimeDeltaPartialTick(false);
		float time = mc.level.getGameTime() + partial;
		Vec3 cam = ctx.camera().getPosition();
		Quaternionf rot = ctx.camera().rotation();
		PoseStack pose = ctx.matrixStack();
		MultiBufferSource buffers = ctx.consumers();
		for (Map.Entry<BlockPos, Long> e : ThorAcquireClient.CRATERS.entrySet()) {
			boolean trial = nearTrial(e.getKey());
			cloud(pose, buffers, rot, cam, e.getKey(), time, trial);
		}
		for (BlockPos t : ThorAcquireClient.TRIALS.keySet()) {
			if (!nearCrater(t)) {
				cloud(pose, buffers, rot, cam, t, time, true);
			}
		}
	}

	private static boolean nearTrial(BlockPos c) {
		for (BlockPos t : ThorAcquireClient.TRIALS.keySet()) {
			if (t.closerThan(c, 16)) {
				return true;
			}
		}
		return false;
	}

	private static boolean nearCrater(BlockPos t) {
		for (BlockPos c : ThorAcquireClient.CRATERS.keySet()) {
			if (t.closerThan(c, 16)) {
				return true;
			}
		}
		return false;
	}

	private static void cloud(PoseStack pose, MultiBufferSource buffers, Quaternionf rot, Vec3 cam, BlockPos c, float time, boolean trial) {
		double cx = c.getX() + 0.5 - cam.x, cy = c.getY() + ALT - cam.y, cz = c.getZ() + 0.5 - cam.z;
		long seed = c.asLong();
		int puffs = trial ? 56 : 36;
		double spread = trial ? 12.5 : 8.5;
		double swirl = time * (trial ? 0.012 : 0.0025);
		// the flicker clock: a flash lasts 4 ticks; normally every 3-7 s, during a trial every second or so
		int period = trial ? 22 : 60 + (int) (h(seed, 3) * 80);
		float phase = (time + (float) (h(seed, 4) * period)) % period;
		boolean flash = phase < 4;
		int flashCycle = (int) Math.floor((time + h(seed, 4) * period) / period);
		double flashAngle = h(seed, flashCycle * 7 + 1) * Math.PI * 2;
		double flashR = h(seed, flashCycle * 7 + 2) * spread * 0.6;
		Vec3 flashAt = new Vec3(cx + Math.cos(flashAngle) * flashR, cy - 0.5, cz + Math.sin(flashAngle) * flashR);
		float flashK = flash ? (phase < 1 ? 1f : 1f - (phase - 1) / 3f) : 0f;

		for (int i = 0; i < puffs; i++) {
			double a = h(seed, i * 5) * Math.PI * 2 + swirl * (0.6 + h(seed, i * 5 + 1));
			double r = Math.sqrt(h(seed, i * 5 + 2)) * spread;
			double y = (h(seed, i * 5 + 3) - 0.5) * (trial ? 6.0 : 4.0) - r * 0.12 + Math.sin(time * 0.02 + i) * 0.3;
			double px = cx + Math.cos(a) * r, pz = cz + Math.sin(a) * r, py = cy + y;
			float size = (float) (3.5 + h(seed, i * 5 + 4) * (trial ? 5.0 : 4.0));
			// slate storm grey, darker underneath and during a trial
			float shade = (float) ((trial ? 0.17 : 0.27) + h(seed, i * 7 + 9) * 0.1 + (y > 0 ? 0.05 : -0.03));
			float lr = shade, lg = shade, lb = shade * 1.12f;
			if (flash) {
				double d = Math.sqrt((px - flashAt.x) * (px - flashAt.x) + (pz - flashAt.z) * (pz - flashAt.z));
				float lit = (float) Math.max(0, 1 - d / (spread * 0.7)) * flashK;
				lr += (0.75f - lr) * lit;
				lg += (0.82f - lg) * lit;
				lb += (1.0f - lb) * lit;
			}
			ResourceLocation tex = PUFFS[i % PUFFS.length];
			VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(tex));
			billboard(vc, pose, rot, px, py, pz, size, lr, lg, lb, 0.93f, (float) (h(seed, i * 3 + 11) * 360));
		}

		if (flash) {
			VertexConsumer vc = ThorDraw.buffer(buffers);
			Vec3 end = trial ? new Vec3(cx + (h(seed, flashCycle * 3) - 0.5) * 6, cy - ALT + 0.3, cz + (h(seed, flashCycle * 5) - 0.5) * 6)
					: flashAt.add(0, -4 - h(seed, flashCycle * 3 + 1) * 5, 0);
			ThorDraw.bolt(vc, pose.last(), ThorDraw.jagged(flashAt, end, trial ? 16 : 8, trial ? 1.4 : 0.7, flashCycle * 1.7 + seed),
					trial ? 1.4f : 0.8f, flashK);
		}
		if (trial) {
			VertexConsumer vc = ThorDraw.buffer(buffers);
			Vec3 ground = new Vec3(cx, cy - ALT + 0.06, cz);
			float pulse = 0.6f + 0.4f * (float) Math.sin(time * 0.3);
			ThorDraw.ring(vc, pose.last(), ground, 1.3f, 1.9f, ThorDraw.GLOW, 0.5f * pulse, 40);
			ThorDraw.ring(vc, pose.last(), ground, 2.6f, 2.8f, ThorDraw.HALO, 0.35f * pulse, 48);
		}
	}

	private static void billboard(VertexConsumer vc, PoseStack pose, Quaternionf rot, double x, double y, double z, float size,
			float r, float g, float b, float a, float spinDeg) {
		Quaternionf q = new Quaternionf(rot).rotateZ((float) Math.toRadians(spinDeg));
		Vector3f[] corners = { new Vector3f(-1, -1, 0), new Vector3f(-1, 1, 0), new Vector3f(1, 1, 0), new Vector3f(1, -1, 0) };
		float[][] uv = { { 1, 1 }, { 1, 0 }, { 0, 0 }, { 0, 1 } };
		PoseStack.Pose p = pose.last();
		for (int i = 0; i < 4; i++) {
			Vector3f v = corners[i].rotate(q).mul(size * 0.5f).add((float) x, (float) y, (float) z);
			vc.addVertex(p, v.x, v.y, v.z).setColor(r, g, b, a).setUv(uv[i][0], uv[i][1]).setOverlay(OverlayTexture.NO_OVERLAY)
					.setLight(LightTexture.FULL_BRIGHT).setNormal(p, 0, 1, 0);
		}
	}

	private static double h(long seed, int i) {
		return ThorDraw.hash(seed * 0.0001 + i * 17.13 + (seed & 0xFFFF) * 0.37);
	}
}
