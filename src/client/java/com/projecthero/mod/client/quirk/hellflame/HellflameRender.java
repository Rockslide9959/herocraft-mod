package com.projecthero.mod.client.quirk.hellflame;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.client.darkseid.BeamDraw;
import com.projecthero.mod.quirk.hellflame.Hellflame;
import com.projecthero.mod.quirk.hellflame.HellflameConfig;
import com.projecthero.mod.quirk.hellflame.HellflameMoves;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7 revamp: Hellflame's flame geometry, drawn in the world after the translucent pass for every Hellflame player near
 * the viewer, from the synced state (so every viewer sees the same), as glowing camera-facing ribbons in vanilla's additive
 * lightning buffer ({@link BeamDraw}):
 * <ul>
 *   <li><b>Hell Spider</b>: five THIN cutting strings, a white-hot core in an orange glow, that shoot out from the
 *       fingertips and sweep right to left across his aim (the same sweep the server cuts with,
 *       {@link HellflameMoves#spiderDir}), then fade;</li>
 *   <li>the <b>Mesh</b>: a dome of the same strings -- meridians and rings -- over the trapped spot, flickering, with the
 *       strings that wove it running from his hand for the first moments;</li>
 *   <li><b>Prominence Burn</b>: the huge beam -- a wide orange sheath, a yellow body and a white-hot core that ripple, with
 *       a flare at the fist and a burst where it lands (Plus Ultra makes it 40% bigger).</li>
 * </ul>
 */
public final class HellflameRender {
	private static final int WHITE = 0xFFF6D8;
	private static final int YELLOW = 0xFFC040;
	private static final int ORANGE = 0xFF6A10;
	private static final int RED = 0xD02808;

	private HellflameRender() {
	}

	static void register() {
		WorldRenderEvents.AFTER_TRANSLUCENT.register(HellflameRender::render);
	}

	private static void render(WorldRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if (level == null || mc.player == null || ctx.consumers() == null) {
			return;
		}
		float partial = ctx.tickCounter().getGameTimeDeltaPartialTick(false);
		Vec3 cam = ctx.camera().getPosition();
		PoseStack pose = ctx.matrixStack();
		MultiBufferSource buffers = ctx.consumers();
		long now = level.getGameTime();
		List<Player> who = new ArrayList<>();
		for (Player p : level.players()) {
			if (!Hellflame.is(p) || p.distanceToSqr(mc.player) > 160 * 160) {
				continue;
			}
			long spiderAt = Hellflame.val(p, Hellflame.V_SPIDER);
			if ((spiderAt != 0L && now - spiderAt < HellflameConfig.SPIDER_DRAW_TICKS) || Hellflame.meshUp(p) || Hellflame.beaming(p)) {
				who.add(p);
			}
		}
		if (who.isEmpty()) {
			return;
		}
		pose.pushPose();
		pose.translate(-cam.x, -cam.y, -cam.z);
		PoseStack.Pose last = pose.last();
		// two passes (the BeamDraw rule): the orange / red glow with ordinary alpha blending first, so it stays orange against
		// the sky; then the yellow / white-hot cores in the additive lightning buffer. Never interleave the two buffers.
		for (boolean glowPass : new boolean[] { true, false }) {
			glow = glowPass;
			VertexConsumer vc = buffers.getBuffer(glowPass ? RenderType.debugQuads() : RenderType.lightning());
			for (Player p : who) {
				boolean firstPerson = p == mc.player && mc.options.getCameraType().isFirstPerson();
				long spiderAt = Hellflame.val(p, Hellflame.V_SPIDER);
				if (spiderAt != 0L && now - spiderAt < HellflameConfig.SPIDER_DRAW_TICKS) {
					strings(vc, last, level, p, cam, now - spiderAt + partial, partial, firstPerson);
				}
				if (Hellflame.meshUp(p)) {
					mesh(vc, last, level, p, cam, now, partial);
				}
				if (Hellflame.beaming(p)) {
					beam(vc, last, level, p, cam, now, partial, firstPerson);
				}
			}
		}
		pose.popPose();
	}

	/** Which pass is drawing: the glow (true) or the cores (false). */
	private static boolean glow;

	/** His right hand now (interpolated). */
	private static Vec3 hand(Player p, float partial, boolean firstPerson) {
		Vec3 eye = p.getEyePosition(partial);
		Vec3 look = p.getViewVector(partial);
		Vec3 right = look.cross(new Vec3(0, 1, 0));
		right = right.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : right.normalize();
		if (firstPerson) {
			return eye.add(look.scale(0.6)).add(right.scale(0.3)).add(0, -0.32, 0);
		}
		return eye.add(look.scale(0.55)).add(right.scale(0.36)).add(0, -0.42, 0);
	}

	private static Vec3 clip(ClientLevel level, Player p, Vec3 from, Vec3 to) {
		BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		return hit.getType() == HitResult.Type.MISS ? to : hit.getLocation();
	}

	/** One thin glowing string a-b: an orange glow round a white-hot core. */
	private static void string(VertexConsumer vc, PoseStack.Pose last, Vec3 a, Vec3 b, Vec3 cam, float width, float alpha) {
		if (glow) BeamDraw.segment(vc, last, a, b, cam, width * 2.6f, width * 2.0f, ORANGE, alpha * 0.55f, alpha * 0.35f);
		if (!glow) BeamDraw.segment(vc, last, a, b, cam, width, width * 0.8f, WHITE, alpha, alpha * 0.8f);
	}

	private static void strings(VertexConsumer vc, PoseStack.Pose last, ClientLevel level, Player p, Vec3 cam, float age, float partial,
			boolean firstPerson) {
		Vec3 aim = Hellflame.vec(p, Hellflame.VEC_SPIDER);
		if (aim == null) {
			return;
		}
		float yaw = (float) aim.x, pitch = (float) aim.y;
		double size = aim.z <= 0 ? 1.0 : aim.z;
		float sweepT = Mth.clamp(age / HellflameConfig.SPIDER_SWEEP_TICKS, 0f, 1f);
		sweepT = sweepT * sweepT * (3f - 2f * sweepT);
		double sweep = Mth.lerp(sweepT, HellflameConfig.SPIDER_SWEEP_DEGREES, -HellflameConfig.SPIDER_SWEEP_DEGREES);
		float grow = Mth.clamp(age / 1.5f, 0f, 1f);
		float fade = age < HellflameConfig.SPIDER_SWEEP_TICKS + 2 ? 1f
				: Mth.clamp(1f - (age - HellflameConfig.SPIDER_SWEEP_TICKS - 2) / (HellflameConfig.SPIDER_DRAW_TICKS - HellflameConfig.SPIDER_SWEEP_TICKS - 2), 0f, 1f);
		Vec3 base = hand(p, partial, firstPerson);
		Vec3 look = Vec3.directionFromRotation(pitch, yaw);
		Vec3 right = look.cross(new Vec3(0, 1, 0));
		right = right.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : right.normalize();
		Vec3 up = right.cross(look).normalize();
		int threads = HellflameConfig.SPIDER_THREADS;
		for (int i = 0; i < threads; i++) {
			// each string leaves its own fingertip (a small fan at the hand)
			double f = i - (threads - 1) / 2.0;
			Vec3 tip = base.add(right.scale(f * 0.045)).add(up.scale(Math.abs(f) * -0.02)).add(look.scale(0.1));
			Vec3 dir = HellflameMoves.spiderDir(yaw, pitch, i, sweep);
			Vec3 end = clip(level, p, tip, tip.add(dir.scale(HellflameConfig.SPIDER_RANGE * size * grow)));
			// a faint shimmer along the string
			float flick = 0.85f + 0.15f * Mth.sin(age * 3.1f + i * 1.7f);
			string(vc, last, tip, end, cam, 0.012f * (float) size, fade * flick);
			// the cutting point glows
			if (fade > 0.2f) {
				if (!glow) BeamDraw.segment(vc, last, end.subtract(dir.scale(0.25)), end.add(dir.scale(0.05)), cam, 0.06f, 0.02f, YELLOW, fade * 0.8f, 0f);
			}
		}
	}

	private static void mesh(VertexConsumer vc, PoseStack.Pose last, ClientLevel level, Player p, Vec3 cam, long now, float partial) {
		Vec3 c = Hellflame.vec(p, Hellflame.VEC_MESH);
		if (c == null) {
			return;
		}
		long at = Hellflame.val(p, Hellflame.V_MESH_AT);
		long until = Hellflame.val(p, Hellflame.V_MESH);
		float age = now - at + partial;
		float left = (until - now - partial);
		float grow = Mth.clamp(age / 5f, 0f, 1f);
		grow = grow * grow * (3f - 2f * grow);
		float fade = Mth.clamp(left / 10f, 0f, 1f);
		double r = HellflameConfig.MESH_RADIUS * Hellflame.size(p) * (0.3 + 0.7 * grow);
		float alpha = fade * (0.75f + 0.25f * Mth.sin(age * 0.9f));
		float w = 0.012f;
		int meridians = 10;
		int segs = 8;
		double spin = age * 0.01;
		// the meridians: arcs from the ground ring over the top
		for (int m = 0; m < meridians; m++) {
			double a = m * Math.PI * 2.0 / meridians + spin;
			Vec3 prev = null;
			for (int k = 0; k <= segs; k++) {
				double el = k / (double) segs * Math.PI * 0.5;
				Vec3 q = c.add(Math.cos(a) * Math.cos(el) * r, Math.sin(el) * r * 0.85 + 0.05, Math.sin(a) * Math.cos(el) * r);
				if (prev != null) {
					string(vc, last, prev, q, cam, w, alpha);
				}
				prev = q;
			}
		}
		// the rings
		for (double el : new double[] { 0.0, 0.45, 0.9 }) {
			double rr = r * Math.cos(el);
			double y = Math.sin(el) * r * 0.85 + 0.05;
			int pts = 20;
			Vec3 prev = null;
			for (int k = 0; k <= pts; k++) {
				double a = k * Math.PI * 2.0 / pts - spin;
				Vec3 q = c.add(Math.cos(a) * rr, y, Math.sin(a) * rr);
				if (prev != null) {
					string(vc, last, prev, q, cam, w, alpha * (el == 0.0 ? 1f : 0.8f));
				}
				prev = q;
			}
		}
		// the strings that wove it, from his hand, for the first moments
		if (age < 8f) {
			float f = 1f - age / 8f;
			Vec3 h = hand(p, partial, p == Minecraft.getInstance().player && Minecraft.getInstance().options.getCameraType().isFirstPerson());
			for (int m = 0; m < 5; m++) {
				double a = m * Math.PI * 2.0 / 5 + spin;
				Vec3 rim = c.add(Math.cos(a) * r, r * 0.3, Math.sin(a) * r);
				string(vc, last, h, rim, cam, w, f);
			}
		}
	}

	private static void beam(VertexConsumer vc, PoseStack.Pose last, ClientLevel level, Player p, Vec3 cam, long now, float partial, boolean firstPerson) {
		Vec3 from = hand(p, partial, firstPerson);
		Vec3 look = p.getViewVector(partial);
		Vec3 end = clip(level, p, from, from.add(look.scale(HellflameConfig.PROMINENCE_RANGE)));
		double size = Hellflame.size(p);
		float age = now - Hellflame.val(p, Hellflame.V_BEAM) + partial;
		// it bursts out wide for a moment, then settles to its full width
		float burst = 1f + 0.6f * Mth.clamp(1f - age / 6f, 0f, 1f);
		float wob = 1f + 0.08f * Mth.sin(age * 2.3f);
		float wFull = (float) (HellflameConfig.PROMINENCE_RADIUS * size) * burst * wob;
		// in first person the beam starts a little ahead so it never fills the screen
		Vec3 a = firstPerson ? from.add(look.scale(0.4)) : from;
		double len = a.distanceTo(end);
		int pieces = Math.max(1, (int) (len / 3.0));
		Vec3 step = end.subtract(a).scale(1.0 / pieces);
		for (int i = 0; i < pieces; i++) {
			Vec3 s0 = a.add(step.scale(i));
			Vec3 s1 = a.add(step.scale(i + 1));
			// the beam flares from the fist to its full width within the first few blocks
			float t0 = (float) Mth.clamp(i * step.length() / 4.0, 0.25, 1.0);
			float t1 = (float) Mth.clamp((i + 1) * step.length() / 4.0, 0.25, 1.0);
			float rip = 0.9f + 0.1f * Mth.sin(age * 1.9f - i * 0.8f);
			if (glow) BeamDraw.segment(vc, last, s0, s1, cam, wFull * t0 * 1.15f * rip, wFull * t1 * 1.15f * rip, RED, 0.4f, 0.4f);
			if (glow) BeamDraw.segment(vc, last, s0, s1, cam, wFull * t0 * 0.85f, wFull * t1 * 0.85f, ORANGE, 0.62f, 0.62f);
			if (!glow) BeamDraw.segment(vc, last, s0, s1, cam, wFull * t0 * 0.5f * rip, wFull * t1 * 0.5f * rip, YELLOW, 0.75f, 0.75f);
			if (!glow) BeamDraw.segment(vc, last, s0, s1, cam, wFull * t0 * 0.22f, wFull * t1 * 0.22f, WHITE, 1f, 1f);
		}
		// the flare at the fist and the burst where it lands
		for (int k = 0; k < 4; k++) {
			double ang = k * Math.PI / 4 + age * 0.3;
			Vec3 axis = perpendicular(look, ang).scale(wFull * 0.9);
			if (!glow) BeamDraw.segment(vc, last, end.subtract(axis), end.add(axis), cam, wFull * 0.35f, wFull * 0.35f, YELLOW, 0.7f, 0.7f);
			if (!firstPerson) {
				Vec3 ax2 = perpendicular(look, ang).scale(0.45 * size);
				if (!glow) BeamDraw.segment(vc, last, from.subtract(ax2), from.add(ax2), cam, 0.2f, 0.2f, WHITE, 0.8f, 0.8f);
			}
		}
	}

	private static Vec3 perpendicular(Vec3 dir, double angle) {
		Vec3 up = Math.abs(dir.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
		Vec3 a = dir.cross(up).normalize();
		Vec3 b = dir.cross(a).normalize();
		return a.scale(Mth.cos((float) angle)).add(b.scale(Mth.sin((float) angle)));
	}
}
