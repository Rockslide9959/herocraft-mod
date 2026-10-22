package com.projecthero.mod.client.quirk.erasure;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.data.QuirkState;
import com.projecthero.mod.quirk.erasure.ErasureConfig;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * v0.16.7: the capture weapon in the world, for every Eraser any client can see, straight from his synced state: a
 * flat grey cloth ribbon from his hand -- whipped out and back (Lash / Disarm / a missed throw), taut to the hook (Swing),
 * reeling him to a creature or a block (Scarf Pull), wound round a bound creature (Capture Bind), or whirling one round
 * him (Bind and Spin).
 */
public final class ErasureWorld {
	public static final ResourceLocation SCARF = ProjectHeroMod.id("textures/entity/quirk/erasure_scarf.png");
	/** The ribbon's half width, blocks. */
	private static final double HALF_W = 0.055;

	private static final Map<Integer, double[]> HAND = new ConcurrentHashMap<>();
	private static long frame;

	private ErasureWorld() {
	}

	static void recordHand(Player p, Vec3 world) {
		HAND.put(p.getId(), new double[] { world.x, world.y, world.z, frame });
	}

	static void reset() {
		HAND.clear();
	}

	/** His right hand, world space, this frame. */
	static Vec3 hand(Minecraft mc, Player p, float partial, Camera cam) {
		if (p == mc.player && mc.options.getCameraType().isFirstPerson()) {
			Vector3f look = cam.getLookVector(), up = cam.getUpVector(), left = cam.getLeftVector();
			return cam.getPosition().add(look.x * 0.62, look.y * 0.62, look.z * 0.62).add(-left.x * 0.36, -left.y * 0.36, -left.z * 0.36)
					.add(-up.x * 0.30, -up.y * 0.30, -up.z * 0.30);
		}
		double[] h = HAND.get(p.getId());
		if (h != null && frame - (long) h[3] <= 1) {
			return new Vec3(h[0], h[1], h[2]);
		}
		Vec3 pos = p.getPosition(partial);
		float bodyYaw = Mth.rotLerp(partial, p.yBodyRotO, p.yBodyRot);
		Vec3 right = Vec3.directionFromRotation(0f, bodyYaw + 90f);
		return pos.add(0, p.getBbHeight() * 0.72, 0).add(right.scale(-0.36)).add(Vec3.directionFromRotation(0f, bodyYaw).scale(0.3));
	}

	static void render(WorldRenderContext context) {
		frame++;
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || context.consumers() == null || context.matrixStack() == null) {
			return;
		}
		Camera camera = context.camera();
		Vec3 cam = camera.getPosition();
		float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
		long now = mc.level.getGameTime();
		for (Player p : mc.level.players()) {
			if (!Quirks.holds(p, Quirk.ERASURE) || p.isInvisible() || p.distanceToSqr(cam) > 128 * 128) {
				continue;
			}
			QuirkState s = Quirks.peek(p);
			if (s == null) {
				continue;
			}
			draw(mc, p, s, context.matrixStack(), context.consumers(), cam, camera, partial, now);
		}
	}

	private static Entity entity(Minecraft mc, QuirkState s, String key) {
		long v = s.val(key);
		return v == 0L ? null : mc.level.getEntity((int) (v - 1L));
	}

	private static Vec3 mid(Entity e, float partial) {
		return e.getPosition(partial).add(0, e.getBbHeight() * 0.55, 0);
	}

	private static void draw(Minecraft mc, Player p, QuirkState s, PoseStack pose, MultiBufferSource buffers, Vec3 cam, Camera camera,
			float partial, long now) {
		Vec3 hand = hand(mc, p, partial, camera);
		float t = now + partial;
		// ---- the line (swing / zip)
		long line = s.val(ErasureConfig.V_LINE);
		if (line != 0L) {
			Vec3 to = Quirks.vec(s, ErasureConfig.P_HOOK);
			Entity z = line == 2L ? entity(mc, s, ErasureConfig.V_ZIP_TARGET) : null;
			if (z != null) {
				to = mid(z, partial);
				coils(mc, pose, buffers, cam, z, partial, 0.6f, t);
			}
			if (to != null) {
				ribbon(mc, pose, buffers, cam, curve(hand, to, 0f, t, 0.02f));
			}
		}
		// ---- bound / hoisted
		Entity bound = s.val(ErasureConfig.V_BIND_UNTIL) > now ? entity(mc, s, ErasureConfig.V_BIND) : null;
		if (bound != null) {
			Vec3 m = mid(bound, partial);
			double len = hand.distanceTo(m);
			ribbon(mc, pose, buffers, cam, curve(hand, coils(mc, pose, buffers, cam, bound, partial, 1f, t), (float) Math.min(0.5, len * 0.04), t, 0.04f));
		}
		// ---- Bind and Spin: taut from his hand to the wrapped creature whirling round him (a little lag in the cloth)
		Entity spun = entity(mc, s, ErasureConfig.V_SPIN);
		if (spun != null) {
			ribbon(mc, pose, buffers, cam, curve(hand, coils(mc, pose, buffers, cam, spun, partial, 0.8f, t), 0.05f, t, 0.05f));
		}
		// ---- the whip (out and back)
		float age = now - s.val(ErasureConfig.V_LASH_AT) + partial;
		Vec3 tip = Quirks.vec(s, ErasureConfig.P_LASH);
		if (tip != null && age >= 0f && age < ErasureConfig.LASH_SHOW_TICKS && line == 0L) {
			float out = age < 3f ? age / 3f : 1f - (age - 3f) / (ErasureConfig.LASH_SHOW_TICKS - 3f);
			Vec3 end = hand.lerp(tip, Mth.clamp(out, 0f, 1f));
			ribbon(mc, pose, buffers, cam, curve(hand, end, 0.15f * (1f - out), t, 0.12f * (1f - out)));
		}
	}

	/** Points along a line from {@code a} to {@code b} that sags {@code sag} and ripples {@code ripple}. */
	private static List<Vec3> curve(Vec3 a, Vec3 b, float sag, float t, float ripple) {
		double len = a.distanceTo(b);
		int n = Math.max(2, Math.min(32, (int) (len * 2.5)));
		Vec3 dir = len < 1.0e-4 ? new Vec3(0, 1, 0) : b.subtract(a).scale(1.0 / len);
		Vec3 side = dir.cross(new Vec3(0, 1, 0));
		side = side.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : side.normalize();
		List<Vec3> pts = new ArrayList<>(n + 1);
		for (int i = 0; i <= n; i++) {
			double u = i / (double) n;
			double bow = 4.0 * u * (1.0 - u);
			double wave = ripple * Math.sin(u * 12.0 - t * 0.9) * bow;
			pts.add(a.lerp(b, u).add(0, -sag * bow, 0).add(side.scale(wave)));
		}
		return pts;
	}

	/**
	 * The scarf wound round {@code e} ({@code wound} 0..1 of four turns, from the shoulders down). Returns the coil's
	 * first point (where the line arrives).
	 */
	private static Vec3 coils(Minecraft mc, PoseStack pose, MultiBufferSource buffers, Vec3 cam, Entity e, float partial, float wound, float t) {
		Vec3 base = e.getPosition(partial);
		double r = e.getBbWidth() * 0.55 + 0.08;
		double h = e.getBbHeight();
		int turns = 3, steps = 48;
		int shown = Math.max(2, Math.round(steps * Mth.clamp(wound, 0f, 1f)));
		List<Vec3> pts = new ArrayList<>();
		for (int i = 0; i <= shown; i++) {
			double u = i / (double) steps;
			double ang = u * turns * Math.PI * 2 + t * 0.02;
			double y = Mth.lerp(u, h * 0.8, h * 0.3);
			pts.add(base.add(Math.cos(ang) * r, y, Math.sin(ang) * r));
		}
		ribbon(mc, pose, buffers, cam, pts);
		return pts.get(0);
	}

	/** A flat, camera-facing cloth ribbon through world points, the scarf texture running along it. */
	static void ribbon(Minecraft mc, PoseStack pose, MultiBufferSource buffers, Vec3 cam, List<Vec3> pts) {
		if (pts.size() < 2) {
			return;
		}
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(SCARF));
		PoseStack.Pose last = pose.last();
		for (int i = 0; i + 1 < pts.size(); i++) {
			Vec3 wa = pts.get(i), wb = pts.get(i + 1);
			Vec3 a = wa.subtract(cam), b = wb.subtract(cam);
			Vec3 d = b.subtract(a);
			double seg = d.length();
			if (seg < 1.0e-5) {
				continue;
			}
			Vec3 m = a.add(b).scale(0.5);
			Vec3 w = d.cross(m);
			if (w.lengthSqr() < 1.0e-9) {
				continue;
			}
			// a little narrower right at the camera (the first-person hand)
			w = w.normalize().scale(HALF_W * Math.max(0.35, Math.min(1.0, m.length() / 2.5)));
			// one texture tile a segment (textures clamp; segments are ~0.4 blocks)
			float v0 = 0f, v1 = 1f;
			int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(wa.lerp(wb, 0.5)));
			vertex(vc, last, a.subtract(w), 0f, v0, light);
			vertex(vc, last, a.add(w), 1f, v0, light);
			vertex(vc, last, b.add(w), 1f, v1, light);
			vertex(vc, last, b.subtract(w), 0f, v1, light);
		}
	}

	private static void vertex(VertexConsumer vc, PoseStack.Pose p, Vec3 at, float u, float v, int light) {
		vc.addVertex(p.pose(), (float) at.x, (float) at.y, (float) at.z).setColor(0xFFFFFFFF).setUv(u, v)
				.setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, 0f, 1f, 0f);
	}
}
