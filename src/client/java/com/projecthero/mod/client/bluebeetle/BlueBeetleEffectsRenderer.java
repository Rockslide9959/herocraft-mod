package com.projecthero.mod.client.bluebeetle;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.bluebeetle.BlueBeetleAbilities;
import com.projecthero.mod.bluebeetle.BlueBeetleConfig;
import com.projecthero.mod.bluebeetle.data.BlueBeetleState;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;

/**
 * v0.16.5: Blue Beetle's world effects for every viewer, from the synced state:
 * <ul>
 *   <li>the Mega Cannon's beam (a thick blue beam with a white core, fading over 10 ticks);</li>
 *   <li>the Bubble Prison (a shimmering blue sphere round the trapped creature);</li>
 *   <li>Scarab Grab (user: "make 2 tentacles come out of the players back and hit the mob back") -- two segmented voxel
 *       tentacles curving out of his upper back over his shoulders to the target, striking one after the other;</li>
 *   <li>Plasma Spikes -- a line of glowing blue energy spikes bursting out of the ground one after another.</li>
 * </ul>
 * Additive {@code lightning} quads (both windings: the type is culled) and palette voxels; nothing spawned.
 */
public final class BlueBeetleEffectsRenderer {
	private BlueBeetleEffectsRenderer() {
	}

	static void render(WorldRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || context.consumers() == null || context.matrixStack() == null) {
			return;
		}
		Camera camera = context.camera();
		float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
		long now = mc.level.getGameTime();
		PoseStack pose = context.matrixStack();
		Vec3 cam = camera.getPosition();
		for (Player player : mc.level.players()) {
			BlueBeetleState s = player.getAttachedOrElse(ModAttachments.BLUE_BEETLE_STATE, null);
			if (s == null || !s.hasPower || !s.suited) {
				continue;
			}
			boolean beam = s.fx.beamUntil > now - 1 && s.fx.beamEnd.size() == 3;
			Entity bubbled = s.fx.bubbleTarget != -1 && s.fx.bubbleUntil > now ? mc.level.getEntity(s.fx.bubbleTarget) : null;
			Entity grabbed = s.fx.grabTarget != -1 ? mc.level.getEntity(s.fx.grabTarget) : null;
			boolean spikes = s.fx.spikeStart != 0L && s.fx.spikeLine.size() == 4 + BlueBeetleConfig.SPIKE_COUNT;
			if (!beam && bubbled == null && grabbed == null && !spikes) {
				continue;
			}
			pose.pushPose();
			pose.translate(-cam.x, -cam.y, -cam.z);
			Matrix4f m = pose.last().pose();
			if (beam) {
				VertexConsumer vc = context.consumers().getBuffer(RenderType.lightning());
				float life = Mth.clamp((s.fx.beamUntil - now - partial) / 10f, 0f, 1f);
				Vec3 from = player.getEyePosition(partial).add(0, -0.35, 0)
						.add(Vec3.directionFromRotation(player.getViewXRot(partial), player.getViewYRot(partial)).scale(1.2));
				Vec3 to = new Vec3(s.fx.beamEnd.get(0), s.fx.beamEnd.get(1), s.fx.beamEnd.get(2));
				ribbon(vc, m, cam, from, to, 0.9f * life, 0.15f, 0.45f, 1.0f, 0.5f * life);
				ribbon(vc, m, cam, from, to, 0.35f * life, 0.85f, 0.95f, 1.0f, 0.9f * life);
			}
			if (bubbled != null) {
				VertexConsumer vc = context.consumers().getBuffer(RenderType.lightning());
				Vec3 c = bubbled.getPosition(partial).add(0, bubbled.getBbHeight() * 0.5, 0);
				float r = Math.max(bubbled.getBbWidth(), bubbled.getBbHeight()) * 0.75f + 0.3f;
				float left = Mth.clamp((s.fx.bubbleUntil - now - partial) / 10f, 0f, 1f);
				float shimmer = 0.75f + 0.25f * Mth.sin((now + partial) * 0.4f);
				sphere(vc, m, c, r, 0.25f, 0.6f, 1.0f, 0.22f * shimmer * Math.max(0.3f, left));
			}
			if (grabbed != null) {
				tentacles(context, pose, cam, player, grabbed, now - s.fx.grabStart + partial, partial);
			}
			if (spikes) {
				spikes(context, m, cam, s.fx.spikeStart, s.fx.spikeLine, now + partial);
			}
			pose.popPose();
		}
	}

	// ---------------------------------------------------------------- Scarab Grab: the tentacles

	/** How far out a tentacle is, 0..1: out over [{@code out0}, {@code out1}], back in over the last 6 ticks. */
	private static float reach(float age, float out0, float out1) {
		float total = BlueBeetleConfig.GRAB_TICKS;
		if (age < out0) {
			return 0f;
		}
		if (age < out1) {
			float t = (age - out0) / (out1 - out0);
			return 1f - (1f - t) * (1f - t);
		}
		if (age < total - 6f) {
			return 1f;
		}
		return Mth.clamp((total - age) / 6f, 0f, 1f);
	}

	private static void tentacles(WorldRenderContext context, PoseStack pose, Vec3 cam, Player player, Entity target, float age, float partial) {
		Minecraft mc = Minecraft.getInstance();
		float yaw = (float) Math.toRadians(Mth.lerp(partial, player.yBodyRotO, player.yBodyRot));
		Vec3 fwd = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
		Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
		Vec3 base = player.getPosition(partial);
		Vec3 root = base.add(0, player.getBbHeight() * 0.74, 0).subtract(fwd.scale(0.2));
		Vec3 hit = target.getPosition(partial).add(0, target.getBbHeight() * 0.55, 0);
		// two passes, never interleaving the two buffers (a shared builder would end the first batch under us)
		java.util.List<Vec3> chain = new java.util.ArrayList<>();
		VertexConsumer voxels = context.consumers().getBuffer(RenderType.entityCutoutNoCull(BlueBeetleTech.TEX));
		for (int side = -1; side <= 1; side += 2) {
			// the first strikes at GRAB_FIRST_HIT, the second at GRAB_SECOND_HIT
			float strike = side < 0 ? BlueBeetleConfig.GRAB_FIRST_HIT : BlueBeetleConfig.GRAB_SECOND_HIT;
			float ext = reach(age, side < 0 ? 0f : 2f, strike);
			if (ext <= 0f) {
				continue;
			}
			Vec3 p0 = root.add(right.scale(0.13 * side));
			Vec3 p1 = p0.add(0, 1.0, 0).add(right.scale(0.85 * side)).subtract(fwd.scale(0.5));
			Vec3 p3 = hit.add(right.scale(0.18 * side));
			Vec3 p2 = p3.add(0, 0.9, 0).add(right.scale(0.6 * side)).subtract(p3.subtract(p0).normalize().scale(1.2));
			int n = 22;
			Vec3 prev = null;
			for (int i = 0; i <= n; i++) {
				float t = ext * i / n;
				Vec3 p = bezier(p0, p1, p2, p3, t);
				// a slithering wobble that dies away at both ends
				double wob = 0.06 * Math.sin(age * 0.9 + i * 0.7 + side) * Math.sin(Math.PI * i / n);
				p = p.add(right.scale(wob)).add(0, wob, 0);
				float size = Mth.lerp(i / (float) n, 0.17f, 0.09f);
				int colour = i == n ? BlueBeetleTech.GOLD : (i % 4 == 0 ? BlueBeetleTech.GOLD : (i % 2 == 0 ? BlueBeetleTech.BLUE : BlueBeetleTech.BLACK));
				int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(p));
				BlueBeetleTech.worldCube(voxels, pose.last(), light, (float) p.x, (float) p.y, (float) p.z, size, colour);
				if (i == n) {
					// the claw: a gold tip with a glowing barb
					Vec3 dir = prev == null ? p3.subtract(p0).normalize() : p.subtract(prev).normalize();
					Vec3 tip = p.add(dir.scale(0.12));
					BlueBeetleTech.worldCube(voxels, pose.last(), LightTexture.FULL_BRIGHT, (float) tip.x, (float) tip.y, (float) tip.z, 0.08f,
							BlueBeetleTech.GLOW);
				}
				if (prev != null) {
					chain.add(prev);
					chain.add(p);
				}
				prev = p;
			}
		}
		VertexConsumer glow = context.consumers().getBuffer(RenderType.lightning());
		Matrix4f m = pose.last().pose();
		for (int i = 0; i + 1 < chain.size(); i += 2) {
			ribbon(glow, m, cam, chain.get(i), chain.get(i + 1), 0.05f, 0.35f, 0.75f, 1.0f, 0.6f);
		}
	}

	private static Vec3 bezier(Vec3 a, Vec3 b, Vec3 c, Vec3 d, float t) {
		float u = 1f - t;
		return a.scale(u * u * u).add(b.scale(3 * u * u * t)).add(c.scale(3 * u * t * t)).add(d.scale(t * t * t));
	}

	// ---------------------------------------------------------------- Plasma Spikes

	private static void spikes(WorldRenderContext context, Matrix4f m, Vec3 cam, long slam, List<Double> line, float time) {
		VertexConsumer vc = context.consumers().getBuffer(RenderType.lightning());
		double x0 = line.get(0), z0 = line.get(1), dx = line.get(2), dz = line.get(3);
		Vec3 along = new Vec3(dx, 0, dz);
		Vec3 across = new Vec3(-dz, 0, dx);
		for (int i = 0; i < BlueBeetleConfig.SPIKE_COUNT; i++) {
			double gy = line.get(4 + i);
			if (gy < -9000) {
				continue;
			}
			float t = time - BlueBeetleAbilities.spikeTime(slam, i);
			if (t < 0f) {
				continue;
			}
			float life = BlueBeetleConfig.SPIKE_LIFE;
			float rise = t < 2.5f ? 1f - (1f - t / 2.5f) * (1f - t / 2.5f) : t < life - 6f ? 1f : Mth.clamp((life - t) / 6f, 0f, 1f);
			if (rise <= 0f) {
				continue;
			}
			Vec3 base = new Vec3(x0 + dx * i, gy, z0 + dz * i);
			float hsh = hash(i);
			// the main spike, two smaller shards leaning out of its foot
			spike(vc, m, base, along, across, (1.3f + 0.7f * hsh) * rise, 0.24f + 0.06f * hsh, 0.12f * (hsh - 0.5f), 0.1f * (hash(i + 31) - 0.5f));
			spike(vc, m, base.add(across.scale(0.32)).add(along.scale(0.1)), along, across, (0.6f + 0.3f * hash(i + 7)) * rise, 0.13f, 0.35f, 0.05f);
			spike(vc, m, base.subtract(across.scale(0.3)).subtract(along.scale(0.12)), along, across, (0.55f + 0.35f * hash(i + 13)) * rise, 0.12f,
					-0.4f, -0.05f);
		}
	}

	/**
	 * One spike: a four-sided pyramid of blue light from {@code base}, {@code height} tall, leaning {@code leanAcross} /
	 * {@code leanAlong} (blocks of tip offset per block of height), with a pale core inside.
	 */
	private static void spike(VertexConsumer vc, Matrix4f m, Vec3 base, Vec3 along, Vec3 across, float height, float half, float leanAcross,
			float leanAlong) {
		if (height <= 0.02f) {
			return;
		}
		for (int layer = 0; layer < 2; layer++) {
			float w = layer == 0 ? half : half * 0.45f;
			float h = layer == 0 ? height : height * 0.85f;
			float r = layer == 0 ? 0.2f : 0.8f, g = layer == 0 ? 0.55f : 0.93f, b = 1f, a = layer == 0 ? 0.6f : 0.85f;
			Vec3 tip = base.add(0, h, 0).add(across.scale(leanAcross * h)).add(along.scale(leanAlong * h));
			Vec3[] c = { base.add(along.scale(w)).add(across.scale(w)), base.add(along.scale(w)).subtract(across.scale(w)),
					base.subtract(along.scale(w)).subtract(across.scale(w)), base.subtract(along.scale(w)).add(across.scale(w)) };
			for (int k = 0; k < 4; k++) {
				Vec3 p = c[k], q = c[(k + 1) % 4];
				vertex(vc, m, p, r, g, b, a);
				vertex(vc, m, q, r, g, b, a);
				vertex(vc, m, tip, r, g, b, a * 0.35f);
				vertex(vc, m, tip, r, g, b, a * 0.35f);
				vertex(vc, m, q, r, g, b, a);
				vertex(vc, m, p, r, g, b, a);
				vertex(vc, m, tip, r, g, b, a * 0.35f);
				vertex(vc, m, tip, r, g, b, a * 0.35f);
			}
		}
	}

	private static float hash(int i) {
		int h = i * 374761393 + 0x27d4eb2d;
		h = (h ^ (h >>> 13)) * 1274126177;
		return ((h >>> 8) & 0xFFFF) / 65536f;
	}

	// ---------------------------------------------------------------- shared

	private static void ribbon(VertexConsumer vc, Matrix4f m, Vec3 cam, Vec3 a, Vec3 b, float width, float r, float g, float bl, float alpha) {
		Vec3 dir = b.subtract(a);
		if (dir.lengthSqr() < 1.0e-6 || alpha <= 0.01f) {
			return;
		}
		Vec3 side = dir.cross(cam.subtract(a.add(b).scale(0.5)));
		if (side.lengthSqr() < 1.0e-8) {
			return;
		}
		side = side.normalize().scale(width * 0.5);
		vertex(vc, m, a.subtract(side), r, g, bl, alpha);
		vertex(vc, m, a.add(side), r, g, bl, alpha);
		vertex(vc, m, b.add(side), r, g, bl, alpha);
		vertex(vc, m, b.subtract(side), r, g, bl, alpha);
		vertex(vc, m, b.subtract(side), r, g, bl, alpha);
		vertex(vc, m, b.add(side), r, g, bl, alpha);
		vertex(vc, m, a.add(side), r, g, bl, alpha);
		vertex(vc, m, a.subtract(side), r, g, bl, alpha);
	}

	/** A UV sphere of quads (12 x 8). */
	private static void sphere(VertexConsumer vc, Matrix4f m, Vec3 c, float r, float cr, float cg, float cb, float alpha) {
		int lon = 12, lat = 8;
		for (int i = 0; i < lat; i++) {
			double t0 = Math.PI * i / lat - Math.PI / 2, t1 = Math.PI * (i + 1) / lat - Math.PI / 2;
			for (int j = 0; j < lon; j++) {
				double p0 = Math.PI * 2 * j / lon, p1 = Math.PI * 2 * (j + 1) / lon;
				float edge = (i == 0 || i == lat - 1) ? 0.7f : 1f;
				vertex(vc, m, at(c, r, t0, p0), cr, cg, cb, alpha * edge);
				vertex(vc, m, at(c, r, t0, p1), cr, cg, cb, alpha * edge);
				vertex(vc, m, at(c, r, t1, p1), cr, cg, cb, alpha * edge);
				vertex(vc, m, at(c, r, t1, p0), cr, cg, cb, alpha * edge);
			}
		}
	}

	private static Vec3 at(Vec3 c, float r, double theta, double phi) {
		return c.add(Math.cos(theta) * Math.cos(phi) * r, Math.sin(theta) * r, Math.cos(theta) * Math.sin(phi) * r);
	}

	private static void vertex(VertexConsumer vc, Matrix4f m, Vec3 p, float r, float g, float b, float a) {
		vc.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setColor(r, g, b, a);
	}
}
