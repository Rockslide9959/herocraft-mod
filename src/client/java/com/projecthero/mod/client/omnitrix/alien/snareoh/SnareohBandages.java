package com.projecthero.mod.client.omnitrix.alien.snareoh;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.omnitrix.AlienParts;
import com.projecthero.mod.client.omnitrix.OmnitrixRenderers;
import com.projecthero.mod.client.omnitrix.alien.brainstorm.BrainstormFx;
import com.projecthero.mod.client.omnitrix.alien.SnareohClient;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.snareoh.SnareohKit;
import com.projecthero.mod.omnitrix.data.OmnitrixState;
import com.projecthero.mod.omnitrix.entity.AlienProjectileEntity;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
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

/**
 * v0.17.3 Snare-oh's bandages, drawn: every strand is a rope of {@link #N} points simulated in the WORLD each client tick (verlet, the
 * approach of {@code GhostfreakTail} / {@code FeedbackCables}): both ends pinned -- his hand and whatever it holds -- the middle carrying
 * its own momentum under gravity, so a bandage sags, swings and trails behind a moving target instead of drawing a stiff line. A whip
 * ({@code snare_oh_whip}) has its far end run out to the target and back again, so the slack in it cracks like a lash.
 * <p>
 * Also drawn here: the wraps round a bound creature ({@code snare_oh_wrap}: a band wound round it in a slow-tightening spiral), the
 * flying hand ({@code snare_oh_hand}: a bandaged fist on its own strand), Unravel Spin's loose ends fanning out round him, and the
 * swirl of bandages he becomes while scattered. Everything is textured with one bandage strip ({@code snare_oh_bandage.png},
 * gen_textures_snare_oh.js) and lit by the world.
 */
public final class SnareohBandages {
	public static final ResourceLocation STRIP = ProjectHeroMod.id("textures/entity/omnitrix/snare_oh_bandage.png");
	static final int N = 14;
	/** A bandage's width (blocks). */
	static final float WIDTH = 0.13f;

	private static final class Rope {
		final Vec3[] pos = new Vec3[N], prev = new Vec3[N], last = new Vec3[N];
		boolean started;
		int age;
	}

	private static final Map<Entity, Rope> ROPES = new WeakHashMap<>();
	private static boolean registered;

	private SnareohBandages() {
	}

	public static void register() {
		if (registered) {
			return;
		}
		registered = true;
		OmnitrixRenderers.projectileLook("snare_oh_whip", (e, partial, pose, buffers) -> rope(e, partial, pose, buffers, true));
		OmnitrixRenderers.projectileLook("snare_oh_rope", (e, partial, pose, buffers) -> rope(e, partial, pose, buffers, false));
		OmnitrixRenderers.projectileLook("snare_oh_wrap", SnareohBandages::wrap);
		OmnitrixRenderers.projectileLook("snare_oh_hand", SnareohBandages::hand);
		ClientTickEvents.END_CLIENT_TICK.register(mc -> tick());
		WorldRenderEvents.AFTER_ENTITIES.register(SnareohBandages::world);
	}

	// ---------------------------------------------------------------- the ropes' ends

	/** Where his bandages leave him: his right hand (just past the screen's edge in his own first person). */
	static Vec3 handOf(Player p, float partial) {
		return BrainstormFx.hand(p, partial);
	}

	/** The far end of a strand carrier: the creature it names (its middle), else the carrier itself. */
	static Vec3 farEnd(AlienProjectileEntity e, float partial) {
		int c = e.colour();
		if (c > 0) {
			Entity t = e.level().getEntity(c - 1);
			if (t != null) {
				return t.getPosition(partial).add(0, t.getBbHeight() * 0.55, 0);
			}
		}
		return e.getPosition(partial);
	}

	/** A whip's far end: runs out over 3 ticks, holds 2, and comes back over 4. */
	static Vec3 whipEnd(Vec3 start, Vec3 target, float age) {
		float k = age < 3f ? age / 3f : age < 5f ? 1f : Math.max(0f, 1f - (age - 5f) / 4f);
		return start.lerp(target, Mth.clamp(k, 0f, 1f));
	}

	// ---------------------------------------------------------------- the simulation (client tick)

	private static void tick() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.isPaused()) {
			return;
		}
		ROPES.entrySet().removeIf(en -> !en.getKey().isAlive());
		for (Map.Entry<Entity, Rope> en : ROPES.entrySet()) {
			if (en.getKey() instanceof AlienProjectileEntity e && e.getOwner() instanceof Player owner) {
				Rope r = en.getValue();
				r.age++;
				Vec3 a = handOf(owner, 1f);
				Vec3 b = farEnd(e, 1f);
				if ("snare_oh_whip".equals(e.kind())) {
					b = whipEnd(a, b, r.age);
				}
				step(r, a, b, "snare_oh_whip".equals(e.kind()));
			}
		}
	}

	private static void step(Rope r, Vec3 a, Vec3 b, boolean whip) {
		if (!r.started) {
			// laid out with a ripple in it (the throw), so the first ticks already move
			Vec3 d = b.subtract(a);
			Vec3 side = d.cross(new Vec3(0, 1, 0));
			side = side.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : side.normalize();
			for (int i = 0; i < N; i++) {
				float t = i / (float) (N - 1);
				Vec3 q = a.lerp(b, t).add(side.scale(Mth.sin(t * Mth.PI * 2f) * 0.25 * Mth.sin(t * Mth.PI))).add(0, Mth.sin(t * Mth.PI) * 0.15, 0);
				r.pos[i] = q;
				r.prev[i] = q;
				r.last[i] = q;
			}
			r.started = true;
		}
		for (int i = 0; i < N; i++) {
			r.last[i] = r.pos[i];
		}
		for (int i = 1; i < N - 1; i++) {
			Vec3 vel = r.pos[i].subtract(r.prev[i]).scale(whip ? 0.94 : 0.86);
			r.prev[i] = r.pos[i];
			r.pos[i] = r.pos[i].add(vel).add(0, -0.03, 0);
		}
		r.prev[0] = r.pos[0];
		r.pos[0] = a;
		r.prev[N - 1] = r.pos[N - 1];
		r.pos[N - 1] = b;
		// the links: a little slack (it sags), never stretched; both ends pinned
		double len = a.distanceTo(b) * (whip ? 1.12 : 1.04) / (N - 1);
		for (int pass = 0; pass < 6; pass++) {
			for (int i = 0; i < N - 1; i++) {
				Vec3 d = r.pos[i + 1].subtract(r.pos[i]);
				double l = d.length();
				if (l < 1e-6) {
					continue;
				}
				Vec3 fix = d.scale((l - len) / l * 0.5);
				if (i > 0) {
					r.pos[i] = r.pos[i].add(fix);
				}
				if (i + 1 < N - 1) {
					r.pos[i + 1] = r.pos[i + 1].subtract(fix);
				}
			}
		}
	}

	// ---------------------------------------------------------------- drawing

	/**
	 * A textured, camera-facing band along world points {@code pts} ({@code pose} at the camera), {@code w} wide, lit by the world at each
	 * point, its texture running {@code vPerBlock} rows a block.
	 */
	static void band(PoseStack pose, MultiBufferSource buffers, List<Vec3> pts, float w, int argb) {
		if (pts.size() < 2) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		Vec3 cam = BrainstormFx.cam();
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(STRIP));
		PoseStack.Pose last = pose.last();
		float v = 0f;
		Vec3 pl = null, pr = null;
		int plight = 0;
		float pv = 0f;
		for (int i = 0; i < pts.size(); i++) {
			Vec3 p = pts.get(i);
			Vec3 tan = (i < pts.size() - 1 ? pts.get(i + 1) : p).subtract(i > 0 ? pts.get(i - 1) : p);
			Vec3 toCam = cam.subtract(p);
			Vec3 side = tan.cross(toCam);
			side = side.lengthSqr() < 1.0e-8 ? new Vec3(0, 1, 0) : side.normalize().scale(w * 0.5);
			Vec3 l = p.add(side).subtract(cam), r = p.subtract(side).subtract(cam);
			int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(p));
			if (i > 0) {
				v += (float) pts.get(i).distanceTo(pts.get(i - 1)) * 2f;
				quad(vc, last, pl, pr, r, l, pv, v, plight, light, argb);
			}
			pl = l;
			pr = r;
			plight = light;
			pv = v;
		}
	}

	private static void quad(VertexConsumer vc, PoseStack.Pose last, Vec3 a, Vec3 b, Vec3 c, Vec3 d, float v0, float v1, int l0, int l1, int argb) {
		int al = (argb >>> 24) & 0xFF, r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, bl = argb & 0xFF;
		vc.addVertex(last, (float) a.x, (float) a.y, (float) a.z).setColor(r, g, bl, al).setUv(0f, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(l0).setNormal(last, 0, 1, 0);
		vc.addVertex(last, (float) b.x, (float) b.y, (float) b.z).setColor(r, g, bl, al).setUv(1f, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(l0).setNormal(last, 0, 1, 0);
		vc.addVertex(last, (float) c.x, (float) c.y, (float) c.z).setColor(r, g, bl, al).setUv(1f, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(l1).setNormal(last, 0, 1, 0);
		vc.addVertex(last, (float) d.x, (float) d.y, (float) d.z).setColor(r, g, bl, al).setUv(0f, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(l1).setNormal(last, 0, 1, 0);
	}

	/** A strand carrier: its simulated rope from his hand to the far end. */
	private static void rope(AlienProjectileEntity e, float partial, PoseStack pose, MultiBufferSource buffers, boolean whip) {
		if (!(e.getOwner() instanceof Player owner)) {
			return;
		}
		Rope r = ROPES.computeIfAbsent(e, k -> new Rope());
		Vec3 a = handOf(owner, partial);
		Vec3 b = farEnd(e, partial);
		if (whip) {
			b = whipEnd(a, b, r.age + partial);
		}
		if (!r.started) {
			step(r, a, b, whip);
		}
		List<Vec3> pts = new ArrayList<>(N);
		for (int i = 0; i < N; i++) {
			pts.add(i == 0 ? a : i == N - 1 ? b : r.last[i].lerp(r.pos[i], partial));
		}
		pose.pushPose();
		BrainstormFx.toCamera(pose, e, partial);
		band(pose, buffers, pts, WIDTH, 0xFFFFFFFF);
		pose.popPose();
	}

	/** A bound creature: a band wound round it, tightening in over the first few ticks. */
	private static void wrap(AlienProjectileEntity e, float partial, PoseStack pose, MultiBufferSource buffers) {
		int c = e.colour();
		Entity t = c > 0 ? e.level().getEntity(c - 1) : null;
		if (t == null) {
			return;
		}
		float age = e.tickCount + partial;
		float tight = Mth.clamp(age / 5f, 0f, 1f);
		Vec3 base = t.getPosition(partial);
		double rad = t.getBbWidth() * 0.5 + 0.06 + (1f - tight) * 0.5;
		double h = t.getBbHeight();
		List<Vec3> pts = new ArrayList<>();
		float turns = 4.5f;
		int n = 64;
		for (int i = 0; i <= n; i++) {
			float k = i / (float) n;
			double a = k * turns * Mth.TWO_PI + age * 0.02;
			pts.add(base.add(Math.cos(a) * rad, h * (0.1 + 0.8 * k), Math.sin(a) * rad));
		}
		pose.pushPose();
		BrainstormFx.toCamera(pose, e, partial);
		spiral(pose, buffers, pts, base, WIDTH * 1.4f);
		pose.popPose();
	}

	/** A band laid flat round a vertical axis through {@code axis} (its face outward), for wraps. */
	private static void spiral(PoseStack pose, MultiBufferSource buffers, List<Vec3> pts, Vec3 axis, float w) {
		Minecraft mc = Minecraft.getInstance();
		Vec3 cam = BrainstormFx.cam();
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(STRIP));
		PoseStack.Pose last = pose.last();
		Vec3 pl = null, pr = null;
		int plight = 0;
		float v = 0f, pv = 0f;
		for (int i = 0; i < pts.size(); i++) {
			Vec3 p = pts.get(i);
			Vec3 tan = (i < pts.size() - 1 ? pts.get(i + 1) : p).subtract(i > 0 ? pts.get(i - 1) : p);
			Vec3 out = new Vec3(p.x - axis.x, 0, p.z - axis.z);
			Vec3 side = tan.cross(out);
			side = side.lengthSqr() < 1.0e-8 ? new Vec3(0, 1, 0) : side.normalize().scale(w * 0.5);
			Vec3 l = p.add(side).subtract(cam), r = p.subtract(side).subtract(cam);
			int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(p));
			if (i > 0) {
				v += (float) pts.get(i).distanceTo(pts.get(i - 1)) * 2f;
				quad(vc, last, pl, pr, r, l, pv, v, plight, light, 0xFFFFFFFF);
			}
			pl = l;
			pr = r;
			plight = light;
			pv = v;
		}
	}

	/** The flying hand: a bandaged fist at the carrier, on its strand back to his wrist. */
	private static void hand(AlienProjectileEntity e, float partial, PoseStack pose, MultiBufferSource buffers) {
		if (!(e.getOwner() instanceof Player owner)) {
			return;
		}
		Rope r = ROPES.computeIfAbsent(e, k -> new Rope());
		Vec3 a = handOf(owner, partial);
		Vec3 b = e.getPosition(partial);
		if (!r.started) {
			step(r, a, b, false);
		}
		List<Vec3> pts = new ArrayList<>(N);
		for (int i = 0; i < N; i++) {
			pts.add(i == 0 ? a : i == N - 1 ? b : r.last[i].lerp(r.pos[i], partial));
		}
		pose.pushPose();
		BrainstormFx.toCamera(pose, e, partial);
		band(pose, buffers, pts, WIDTH * 0.8f, 0xFFFFFFFF);
		pose.popPose();
		// the fist itself, its knuckles to where it flies, textured with his skin's own hand
		Minecraft mc = Minecraft.getInstance();
		int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(b));
		Vec3 dir = b.subtract(a);
		float yaw = (float) Math.atan2(dir.x, dir.z);
		float pitch = (float) Math.atan2(-dir.y, Math.sqrt(dir.x * dir.x + dir.z * dir.z));
		pose.pushPose();
		pose.mulPose(com.mojang.math.Axis.YP.rotation(yaw));
		pose.mulPose(com.mojang.math.Axis.XP.rotation(pitch));
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(SnareohClient.SKIN));
		// the right arm's lower faces (u 44..48, v 26..32: the forearm and fist)
		AlienParts.box(pose, vc, -2f, -2f, -3f, 2f, 2f, 2f, 44f / 64f, 26f / 64f, 48f / 64f, 32f / 64f, 0xFFFFFFFF, light);
		pose.popPose();
	}

	// ---------------------------------------------------------------- the world pass: Unravel Spin, the scatter

	private static void world(WorldRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || context.consumers() == null || context.matrixStack() == null) {
			return;
		}
		float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
		for (var pl : mc.level.players()) {
			if (!(pl instanceof AbstractClientPlayer p) || !SnareohKit.isSnareoh(p)) {
				continue;
			}
			int flags = Omnitrix.state(p).kitFlags;
			boolean own = p == mc.player && mc.options.getCameraType().isFirstPerson();
			if ((flags & (SnareohKit.F_SPIN | SnareohKit.F_WHIRL)) != 0 && !own) {
				spinStrands(context.matrixStack(), context.consumers(), p, partial);
			}
			if ((flags & SnareohKit.F_SCATTER) != 0 && !own) {
				swirl(context.matrixStack(), context.consumers(), p, partial);
			}
			if ((flags & SnareohKit.F_COCOON) != 0 && !own) {
				cocoon(context.matrixStack(), context.consumers(), p, partial);
			}
		}
	}

	/** Unravel Spin: four loose ends flung out from his middle, sweeping round with him. */
	private static void spinStrands(PoseStack pose, MultiBufferSource buffers, AbstractClientPlayer p, float partial) {
		float age = p.tickCount + partial;
		Vec3 c = p.getPosition(partial).add(0, p.getBbHeight() * 0.5, 0);
		float spin = SnareohClient.spinDegrees(p, partial) * Mth.DEG_TO_RAD;
		for (int s = 0; s < 4; s++) {
			List<Vec3> pts = new ArrayList<>();
			for (int i = 0; i <= 10; i++) {
				float k = i / 10f;
				// trailing back against the turn the further out it goes
				double a = spin + s * Mth.HALF_PI - k * 0.9;
				double r = 0.3 + k * (SnareohKit.SPIN_RADIUS - 0.6);
				pts.add(c.add(Math.cos(a) * r, Mth.sin(age * 0.8f + s + k * 3f) * 0.18 * k - k * 0.2, Math.sin(a) * r));
			}
			band(pose, buffers, pts, WIDTH, 0xFFFFFFFF);
		}
	}

	/** v0.17.4 Cocoon: two bandages wound tight round him from his feet to the top of his head, slowly drawing tighter. */
	private static void cocoon(PoseStack pose, MultiBufferSource buffers, AbstractClientPlayer p, float partial) {
		float age = p.tickCount + partial;
		OmnitrixState s = Omnitrix.state(p);
		float t = p.level().getGameTime() + partial - s.animStart;
		float wind = s.animId == SnareohKit.ANIM_COCOON ? Mth.clamp(t / 8f, 0f, 1f) : 1f;
		Vec3 c = p.getPosition(partial);
		float h = p.getBbHeight() * wind;
		double r = p.getBbWidth() * (0.62 - 0.04 * Mth.sin(age * 0.15f));
		for (int strand = 0; strand < 2; strand++) {
			List<Vec3> pts = new ArrayList<>();
			int n = 48;
			for (int i = 0; i <= n; i++) {
				float k = i / (float) n;
				double a = (strand * Math.PI) + k * Math.PI * 2 * 7 + age * 0.05;
				double rr = r * (k > 0.85f ? 1.0 - (k - 0.85) * 2.5 : 1.0);
				pts.add(c.add(Math.cos(a) * rr, 0.05 + k * h, Math.sin(a) * rr));
			}
			band(pose, buffers, pts, WIDTH * 1.6f, 0xFFFFFFFF);
		}
	}

	/** Scattered: a loose whirl of bandages where his body was. */
	private static void swirl(PoseStack pose, MultiBufferSource buffers, AbstractClientPlayer p, float partial) {
		float age = p.tickCount + partial;
		Vec3 c = p.getPosition(partial);
		for (int s = 0; s < 7; s++) {
			List<Vec3> pts = new ArrayList<>();
			double base = s * 0.9 + age * (0.25 + s * 0.03);
			double y0 = 0.2 + (s % 4) * 0.42;
			for (int i = 0; i <= 8; i++) {
				float k = i / 8f;
				double a = base + k * 1.6;
				double r = 0.45 + 0.25 * Mth.sin(age * 0.2f + s);
				pts.add(c.add(Math.cos(a) * r, y0 + k * 0.35 + Mth.sin(age * 0.3f + s * 2f) * 0.1, Math.sin(a) * r));
			}
			band(pose, buffers, pts, WIDTH * 1.2f, 0xFFFFFFFF);
		}
	}
}
