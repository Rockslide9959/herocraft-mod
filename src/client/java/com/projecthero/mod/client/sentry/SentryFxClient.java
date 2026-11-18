package com.projecthero.mod.client.sentry;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.client.beamclash.BeamClashClient;
import com.projecthero.mod.client.darkseid.BeamDraw;
import com.projecthero.mod.sentry.Sentry;
import com.projecthero.mod.sentry.SentryConfig;
import com.projecthero.mod.sentry.SentryMoves;
import com.projecthero.mod.sentry.data.SentryState;
import com.projecthero.mod.sentry.network.SentryFxPayload;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.5 Sentry's effects in the world, drawn as camera-facing ribbons ({@link BeamDraw}): the <b>Sunbeam</b> (from the synced
 * {@code beamStart}, every viewer, hidden while the beam is locked in a clash: {@link BeamClashClient#hides}), and every
 * {@link SentryFxPayload} -- golden shockwave rings, A Million Suns gathering and going off (plus a white-gold screen flash nearby), the
 * Void Rift's black hole, the Nightfall dome, tendrils, Erase's streak, the sonic boom, the Sentinel Shield's dome, Sun Dive's crater,
 * the golden flash when the Void is thrown out, and a helper's golden thread. Light goes into the additive buffer, darkness into the
 * translucent one; every glow first, every core second (a batched buffer can't interleave two render types).
 */
public final class SentryFxClient {
	private record Fx(SentryFxPayload p, long start) {
	}

	private record Seg(Vec3 a, Vec3 b, float wa, float wb, int rgb, float aa, float ab) {
	}

	private static final List<Fx> ACTIVE = new ArrayList<>();
	private static final int MAX = 96;
	/** The screen flash of A Million Suns (or the Void thrown out), real ms. */
	private static long flashAt;
	private static float flashStrength;
	private static int flashColour = 0xFFF4C0;

	private SentryFxClient() {
	}

	public static void reset() {
		ACTIVE.clear();
		flashAt = 0L;
	}

	public static void receive(SentryFxPayload p) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return;
		}
		if (ACTIVE.size() >= MAX) {
			ACTIVE.remove(0);
		}
		ACTIVE.add(new Fx(p, mc.level.getGameTime()));
		if ((p.kind() == SentryFxPayload.SUNS || p.kind() == SentryFxPayload.THROWN_OUT) && mc.player != null) {
			double d = mc.player.position().distanceTo(new Vec3(p.x(), p.y(), p.z()));
			float s = (float) Mth.clamp(1.0 - d / 48.0, 0.0, 1.0);
			if (s > 0f) {
				flashAt = net.minecraft.Util.getMillis();
				flashStrength = s * (p.kind() == SentryFxPayload.SUNS ? 0.9f : 0.6f);
				flashColour = 0xFFF4C0;
			}
		}
	}

	/** The screen flash (from the HUD callback). */
	public static void renderFlash(GuiGraphics g, DeltaTracker delta) {
		if (flashAt == 0L) {
			return;
		}
		float t = (net.minecraft.Util.getMillis() - flashAt) / 900f;
		if (t >= 1f) {
			flashAt = 0L;
			return;
		}
		int a = (int) (255 * flashStrength * (1f - t) * (1f - t));
		g.fill(0, 0, g.guiWidth(), g.guiHeight(), (a << 24) | flashColour);
	}

	public static void render(WorldRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		PoseStack pose = context.matrixStack();
		MultiBufferSource buffers = context.consumers();
		if (level == null || pose == null || buffers == null) {
			return;
		}
		float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
		float now = level.getGameTime() + partial;
		List<Seg> light = new ArrayList<>();
		List<Seg> dark = new ArrayList<>();
		for (Player p : level.players()) {
			SentryState s = Sentry.peek(p);
			if (s != null && s.hasPower && s.beamStart != 0L && !BeamClashClient.hides(p)) {
				gaze(mc, level, p, s, partial, now, light, dark);
			}
		}
		ACTIVE.removeIf(f -> level.getGameTime() - f.start >= f.p.ticks() + 2 || level.getGameTime() < f.start - 20);
		for (Fx f : ACTIVE) {
			float age = Math.max(0f, now - f.start);
			draw(level, f.p, age, partial, context.camera().getPosition(), light, dark);
		}
		if (light.isEmpty() && dark.isEmpty()) {
			return;
		}
		Vec3 cam = context.camera().getPosition();
		pose.pushPose();
		pose.translate(-cam.x, -cam.y, -cam.z);
		PoseStack.Pose last = pose.last();
		VertexConsumer glow = buffers.getBuffer(RenderType.debugQuads());
		for (Seg s : dark) {
			BeamDraw.segment(glow, last, s.a, s.b, cam, s.wa, s.wb, s.rgb, s.aa, s.ab);
		}
		VertexConsumer core = buffers.getBuffer(RenderType.lightning());
		for (Seg s : light) {
			BeamDraw.segment(core, last, s.a, s.b, cam, s.wa, s.wb, s.rgb, s.aa, s.ab);
		}
		pose.popPose();
	}

	// ---------------------------------------------------------------- the Solar Gaze (moveset 2: twin beams from his eyes)

	private static void gaze(Minecraft mc, ClientLevel level, Player p, SentryState st, float partial, float now, List<Seg> light, List<Seg> dark) {
		Vec3 eye = p.getEyePosition(partial);
		Vec3 dir = p.getViewVector(partial);
		boolean fp = p == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();
		Vec3 right = dir.cross(new Vec3(0, 1, 0));
		right = right.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : right.normalize();
		Vec3 base = fp ? eye.add(dir.scale(0.6)).add(0, -0.12, 0) : eye.add(dir.scale(0.22)).add(0, 0.02, 0);
		double sep = fp ? 0.09 : 0.065;
		Vec3 from = base;
		Vec3 end = from.add(dir.scale(SentryConfig.GAZE_RANGE));
		BlockHitResult bh = level.clip(new ClipContext(from, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		if (bh.getType() != HitResult.Type.MISS) {
			end = bh.getLocation();
		}
		var eh = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(level, p, from, end,
				new net.minecraft.world.phys.AABB(from, end).inflate(1.0), e -> e instanceof net.minecraft.world.entity.LivingEntity le
						&& le.isAlive() && !e.isSpectator() && e != p, 0.5f);
		if (eh != null) {
			end = eh.getLocation();
		}
		boolean hot = level.getGameTime() - st.beamStart >= SentryConfig.GAZE_RAMP_TICKS;
		float pulse = 0.85f + 0.15f * Mth.sin(now * 1.3f);
		float near = fp ? 0.5f : 1f;
		float w = hot ? 1.45f : 1f;
		for (int side = -1; side <= 1; side += 2) {
			Vec3 a = base.add(right.scale(side * sep));
			light.add(new Seg(a, end, 0.11f * pulse * w, 0.2f * pulse * w, hot ? 0xFF8A20 : 0xFFB52A, 0.5f * near, 0.55f));
			light.add(new Seg(a, end, 0.04f * w, 0.07f * w, 0xFFF8E0, 0.95f * near, 0.95f));
			// the glint in each eye
			light.add(new Seg(a.add(0, -0.06, 0), a.add(0, 0.06, 0), 0.12f, 0.12f, 0xFFF4C0, 0.8f * near, 0.8f * near));
		}
		// the burst where the two meet
		float b = (hot ? 0.75f : 0.5f) * pulse;
		light.add(new Seg(end.add(0, -b, 0), end.add(0, b, 0), b, b, hot ? 0xFFB060 : 0xFFE8A0, 0.55f, 0.55f));
		ring(end, dir, 0.35f + 0.15f * Mth.sin(now * 0.9f), 0.05f, 0xFFD860, 0.6f, light);
	}

	// ---------------------------------------------------------------- the payload effects

	private static void draw(ClientLevel level, SentryFxPayload p, float age, float partial, Vec3 cam, List<Seg> light, List<Seg> dark) {
		Vec3 at = new Vec3(p.x(), p.y(), p.z());
		Vec3 to = new Vec3(p.tx(), p.ty(), p.tz());
		float life = Math.max(1, p.ticks());
		float t = Mth.clamp(age / life, 0f, 1f);
		float fade = 1f - t;
		switch (p.kind()) {
			case SentryFxPayload.RING -> {
				float r = 0.6f + p.size() * smooth(t);
				ring(at, new Vec3(0, 1, 0), r, 0.18f + 0.1f * fade, 0xFFC83A, 0.8f * fade, light);
				ring(at.add(0, 0.05, 0), new Vec3(0, 1, 0), r * 0.92f, 0.08f, 0xFFF4C0, 0.9f * fade, light);
			}
			case SentryFxPayload.CRATER -> {
				ring(at, new Vec3(0, 1, 0), p.size() * 0.9f, 0.35f, 0xFFB52A, 0.5f * fade, light);
				ring(at, new Vec3(0, 1, 0), p.size() * 0.5f, 0.25f, 0xFFE08A, 0.6f * fade, light);
			}
			case SentryFxPayload.SUNS_CHARGE -> {
				Entity owner = p.owner() >= 0 ? level.getEntity(p.owner()) : null;
				Vec3 c = owner != null ? owner.getPosition(partial).add(0, 1.0, 0) : at;
				float r = p.size() * (1f - smooth(t)) + 0.4f;
				for (int i = 0; i < 3; i++) {
					Vec3 axis = new Vec3(Math.sin(age * 0.1 + i * 2.1), 0.6, Math.cos(age * 0.1 + i * 2.1)).normalize();
					ring(c, axis, r, 0.06f + 0.1f * t, 0xFFE08A, 0.4f + 0.5f * t, light);
				}
				// the light gathering into his chest
				light.add(new Seg(c.add(0, -0.4 * t, 0), c.add(0, 0.4 * t, 0), 0.2f + 0.8f * t, 0.2f + 0.8f * t, 0xFFF4C0, 0.3f + 0.6f * t, 0.3f + 0.6f * t));
			}
			case SentryFxPayload.SUNS -> {
				float e = smooth(Math.min(1f, t * 2.2f));
				float r = p.size() * e;
				// the sphere of light: rings on three axes and a blinding core
				for (int i = 0; i < 6; i++) {
					Vec3 axis = new Vec3(Math.sin(i * 1.05), Math.cos(i * 0.7), Math.cos(i * 1.05)).normalize();
					ring(at, axis, r, 0.5f * fade + 0.1f, 0xFFC83A, 0.75f * fade, light);
				}
				ring(at.add(0, -0.95, 0), new Vec3(0, 1, 0), r * 1.3f, 0.6f * fade, 0xFFF4C0, 0.8f * fade, light);
				float core = p.size() * 0.45f * (1f - t * 0.7f);
				light.add(new Seg(at.add(0, -core, 0), at.add(0, core, 0), core, core, 0xFFF8E0, 0.9f * fade, 0.9f * fade));
				for (int i = 0; i < 12; i++) {
					double a = i * Math.PI / 6.0 + age * 0.02;
					Vec3 ray = new Vec3(Math.cos(a), Math.sin(i * 0.9) * 0.6, Math.sin(a)).normalize();
					light.add(new Seg(at, at.add(ray.scale(r * 1.4)), 0.4f * fade, 0.02f, 0xFFE8A0, 0.8f * fade, 0f));
				}
			}
			case SentryFxPayload.RIFT -> {
				float grow = smooth(Math.min(1f, age / 8f)) * (t > 0.85f ? (1f - t) / 0.15f : 1f);
				float r = 1.4f * grow;
				// the black hole: a dark core and an accretion swirl of purple and gold
				disc(at, r, cam, 0x050008, 0.95f, dark);
				for (int i = 0; i < 4; i++) {
					Vec3 axis = new Vec3(Math.sin(i * 0.8 + age * 0.05), 1.0, Math.cos(i * 0.8 + age * 0.05)).normalize();
					ring(at, axis, r * 1.5f + i * 0.35f, 0.07f, i % 2 == 0 ? 0x7A30FF : 0xFFB52A, 0.55f * grow, light);
				}
				ring(at, new Vec3(0, 1, 0), 7f * grow, 0.05f, 0x5020A0, 0.3f * grow, light); // (the old Void Rift: kind kept)
			}
			case SentryFxPayload.NIGHTFALL -> {
				float grow = smooth(Math.min(1f, age / 12f)) * (t > 0.9f ? (1f - t) / 0.1f : 1f);
				float r = p.size() * grow;
				dome(at, r, 0x080410, 0.35f * grow, 0.14f, dark);
				ring(at.add(0, 0.1, 0), new Vec3(0, 1, 0), r, 0.12f, 0x3A2060, 0.5f * grow, light);
			}
			case SentryFxPayload.SHIELD -> {
				Entity owner = p.owner() >= 0 ? level.getEntity(p.owner()) : null;
				Vec3 c = owner != null ? owner.getPosition(partial) : at;
				float grow = smooth(Math.min(1f, age / 6f)) * (t > 0.92f ? (1f - t) / 0.08f : 1f);
				dome(c, p.size() * grow, 0xFFC83A, 0.35f * grow, 0.05f, light);
			}
			case SentryFxPayload.TENDRIL -> {
				tendril(at, to, p.size(), age, fade, dark, light);
			}
			case SentryFxPayload.ERASE -> {
				// a black streak from his eyes, and the creature burning into a shadow
				dark.add(new Seg(to, at, 0.05f, 0.35f * fade, 0x000000, 0.8f * fade, 0.9f * fade));
				float h = Math.max(0.6f, p.size()) * 0.5f;
				dark.add(new Seg(at.add(0, -h, 0), at.add(0, h * fade, 0), 0.5f, 0.3f * fade, 0x050008, 0.9f * fade, 0.3f * fade));
				light.add(new Seg(at.add(0, -h, 0), at.add(0, -h + 0.1, 0), 0.7f * fade, 0.7f * fade, 0xFFB52A, 0.5f * fade, 0.5f * fade));
			}
			case SentryFxPayload.BOOM -> {
				Vec3 dir = to.subtract(at);
				dir = dir.lengthSqr() < 1.0e-6 ? new Vec3(0, 0, 1) : dir.normalize();
				for (int i = 0; i < 3; i++) {
					float r = 0.6f + (p.size() + i) * smooth(t);
					ring(at.add(dir.scale(-i * 0.8 * t)), dir, r, 0.12f, 0xFFFFFF, 0.6f * fade, light);
				}
			}
			case SentryFxPayload.THROWN_OUT -> {
				float r = 0.5f + p.size() * smooth(t);
				for (int i = 0; i < 3; i++) {
					ring(at, new Vec3(Math.sin(i * 2.1), 1.2, Math.cos(i * 2.1)).normalize(), r, 0.25f * fade, 0xFFC83A, 0.9f * fade, light);
				}
				light.add(new Seg(at.add(0, -1.2, 0), at.add(0, 6.0 * fade, 0), 0.8f * fade, 0.1f, 0xFFF4C0, 0.9f * fade, 0f));
			}
			case SentryFxPayload.REACH -> {
				light.add(new Seg(at, to, 0.06f, 0.06f, 0xFFD860, 0.8f * fade, 0.8f * fade));
			}
			case SentryFxPayload.DISINTEGRATE -> disintegrate(at, p.size(), (float) p.tx(), age, t, light);
			case SentryFxPayload.POOL -> pool(at, p.size(), age, t, life, dark, light);
			case SentryFxPayload.SPHERE -> {
				Entity owner = p.owner() >= 0 ? level.getEntity(p.owner()) : null;
				Vec3 c = owner != null ? owner.getPosition(partial).add(0, owner.getBbHeight() * 0.5, 0) : at;
				float grow = smooth(Math.min(1f, age / 6f)) * (age > life - 6 ? Math.max(0f, (life - age) / 6f) : 1f);
				float r = p.size() * grow;
				disc(c, r, cam, 0x020004, 0.94f, dark);
				for (int i = 0; i < 3; i++) {
					Vec3 axis = new Vec3(Math.sin(i * 2.1 + age * 0.08), 0.8, Math.cos(i * 2.1 + age * 0.08)).normalize();
					ring(c, axis, r * 1.04f, 0.05f, i == 0 ? 0xFFB52A : 0x7A30FF, 0.6f * grow, light);
				}
			}
			case SentryFxPayload.NIGHTMARE -> {
				float r = p.size() * smooth(t);
				ring(at, new Vec3(0, 1, 0), r, 0.6f * fade + 0.1f, 0x080410, 0.75f * fade, dark);
				ring(at.add(0, 0.05, 0), new Vec3(0, 1, 0), r * 0.97f, 0.06f, 0x7A30FF, 0.6f * fade, light);
				for (int i = 0; i < 10; i++) {
					double a = i * Math.PI / 5.0 + 0.3;
					Vec3 d = new Vec3(Math.cos(a), 0, Math.sin(a));
					dark.add(new Seg(at.add(d.scale(r * 0.4)), at.add(d.scale(r)).add(0, 0.1, 0), 0.05f, 0.4f * fade, 0x050008, 0.2f, 0.8f * fade));
				}
			}
			case SentryFxPayload.ECLIPSE -> {
				Entity owner = p.owner() >= 0 ? level.getEntity(p.owner()) : null;
				Vec3 c = owner != null ? owner.getPosition(partial) : at;
				eclipse(c, p.size(), age, life, cam, dark, light);
			}
			case SentryFxPayload.SHADE -> {
				boolean brief = life <= 8; // a clone, re-sent every 2 ticks: only the newest copy draws
				float in = brief ? 1f : Math.min(1f, age / 4f);
				float out = brief ? (age <= 2.05f ? 1f : 0f) : Math.min(1f, (life - age) / 6f);
				shade(at, to, p.size(), age, Math.max(0f, in * out), dark, light);
			}
			case SentryFxPayload.PILLAR -> {
				float grow = smooth(Math.min(1f, age / 5f));
				Vec3 top = at.add(0, p.size() * grow, 0);
				light.add(new Seg(at, top, 0.7f * fade, 0.25f * fade, 0xFFC83A, 0.6f * fade, 0.1f));
				light.add(new Seg(at, top, 0.25f * fade, 0.08f * fade, 0xFFF8E0, 0.9f * fade, 0.2f));
				ring(at.add(0, 0.05, 0), new Vec3(0, 1, 0), 0.6f + 0.6f * smooth(t), 0.06f, 0xFFE08A, 0.8f * fade, light);
			}
			case SentryFxPayload.COMET -> {
				Entity owner = p.owner() >= 0 ? level.getEntity(p.owner()) : null;
				if (owner != null && owner.isAlive()) {
					Vec3 c = owner.getPosition(partial).add(0, owner.getBbHeight() * 0.5, 0);
					Vec3 v = new Vec3(owner.getX() - owner.xo, owner.getY() - owner.yo, owner.getZ() - owner.zo);
					if (v.lengthSqr() > 0.01) {
						Vec3 tail = c.subtract(v.scale(5.0));
						light.add(new Seg(c, tail, p.size() * fade + 0.15f, 0.02f, 0xFFB52A, 0.75f * fade, 0f));
						light.add(new Seg(c, c.subtract(v.scale(2.5)), 0.2f * fade + 0.05f, 0.01f, 0xFFF8E0, 0.9f * fade, 0f));
					}
				}
			}
			default -> {
			}
		}
	}

	private static float smooth(float t) {
		t = Mth.clamp(t, 0f, 1f);
		return t * t * (3f - 2f * t);
	}

	/** A ring of radius {@code r} round {@code axis} (16 segments). */
	private static void ring(Vec3 c, Vec3 axis, float r, float w, int rgb, float alpha, List<Seg> out) {
		if (alpha <= 0.01f || r <= 0.01f) {
			return;
		}
		Vec3 n = axis.normalize();
		Vec3 u = Math.abs(n.y) < 0.95 ? n.cross(new Vec3(0, 1, 0)).normalize() : n.cross(new Vec3(1, 0, 0)).normalize();
		Vec3 v = n.cross(u).normalize();
		int seg = 16;
		Vec3 prev = c.add(u.scale(r));
		for (int i = 1; i <= seg; i++) {
			double a = i * Math.PI * 2.0 / seg;
			Vec3 pt = c.add(u.scale(Math.cos(a) * r)).add(v.scale(Math.sin(a) * r));
			out.add(new Seg(prev, pt, w, w, rgb, alpha, alpha));
			prev = pt;
		}
	}

	/** A camera-facing filled disc (16 wedges from the centre). */
	private static void disc(Vec3 c, float r, Vec3 cam, int rgb, float alpha, List<Seg> out) {
		Vec3 n = cam.subtract(c);
		n = n.lengthSqr() < 1.0e-6 ? new Vec3(0, 0, 1) : n.normalize();
		Vec3 u = Math.abs(n.y) < 0.95 ? n.cross(new Vec3(0, 1, 0)).normalize() : n.cross(new Vec3(1, 0, 0)).normalize();
		Vec3 v = n.cross(u).normalize();
		int k = 16;
		float half = (float) (r * Math.tan(Math.PI / k)) * 1.05f;
		for (int i = 0; i < k; i++) {
			double a = i * Math.PI * 2.0 / k;
			Vec3 rim = c.add(u.scale(Math.cos(a) * r)).add(v.scale(Math.sin(a) * r));
			out.add(new Seg(c, rim, 0.01f, half, rgb, alpha, alpha));
		}
	}

	/** A dome of radius {@code r} over {@code c}: latitude rings and meridians. */
	private static void dome(Vec3 c, float r, int rgb, float alpha, float w, List<Seg> out) {
		if (r <= 0.05f) {
			return;
		}
		for (int k = 0; k < 4; k++) {
			double lat = k * Math.PI / 8.0;
			ring(c.add(0, Math.sin(lat) * r, 0), new Vec3(0, 1, 0), (float) (Math.cos(lat) * r), w, rgb, alpha, out);
		}
		for (int m = 0; m < 8; m++) {
			double lon = m * Math.PI / 4.0;
			Vec3 prev = c.add(Math.cos(lon) * r, 0, Math.sin(lon) * r);
			for (int k = 1; k <= 6; k++) {
				double lat = k * Math.PI / 12.0;
				Vec3 pt = c.add(Math.cos(lon) * Math.cos(lat) * r, Math.sin(lat) * r, Math.sin(lon) * Math.cos(lat) * r);
				out.add(new Seg(prev, pt, w, w, rgb, alpha, alpha));
				prev = pt;
			}
		}
	}

	/** A black, writhing tendril from {@code a} to {@code b} with a gold vein down it. */
	private static void tendril(Vec3 a, Vec3 b, float width, float age, float fade, List<Seg> dark, List<Seg> light) {
		Vec3 d = b.subtract(a);
		double len = d.length();
		if (len < 0.1) {
			return;
		}
		Vec3 n = d.scale(1.0 / len);
		Vec3 u = Math.abs(n.y) < 0.95 ? n.cross(new Vec3(0, 1, 0)).normalize() : n.cross(new Vec3(1, 0, 0)).normalize();
		Vec3 v = n.cross(u).normalize();
		int seg = Math.max(4, (int) (len * 2));
		float reach = Math.min(1f, age / 3f);
		Vec3 prev = a;
		for (int i = 1; i <= seg; i++) {
			double t = i / (double) seg;
			if (t > reach) {
				break;
			}
			double wob = Math.sin(t * 9.0 + age * 0.9) * 0.25 * Math.sin(t * Math.PI);
			Vec3 pt = a.add(d.scale(t)).add(u.scale(wob)).add(v.scale(Math.cos(t * 7.0 + age) * 0.15 * Math.sin(t * Math.PI)));
			float w0 = (float) (width * (1.0 - (t - 1.0 / seg) * 0.7));
			float w1 = (float) (width * (1.0 - t * 0.7));
			dark.add(new Seg(prev, pt, w0, w1, 0x050008, 0.92f * fade, 0.92f * fade));
			light.add(new Seg(prev, pt, w0 * 0.18f, w1 * 0.18f, 0xFFB52A, 0.6f * fade, 0.6f * fade));
			prev = pt;
		}
	}

	/** For the gaze's start point (his eyes). */
	static Vec3 gazeOrigin(Player p) {
		return SentryMoves.gazeOrigin(p);
	}

	// ---------------------------------------------------------------- moveset 2 shapes

	/** A tiny repeatable hash in 0..1. */
	private static double hash(int i, int salt) {
		long h = i * 0x9E3779B97F4A7C15L + salt * 0xC2B2AE3D27D4EB4FL;
		h ^= h >>> 31;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 29;
		return ((h >>> 11) & 0xFFFFF) / (double) 0xFFFFF;
	}

	/** The creature comes apart into golden motes rising and scattering, its outline of light fading. */
	private static void disintegrate(Vec3 c, float h, float w, float age, float t, List<Seg> light) {
		float fade = 1f - t;
		w = Math.max(0.4f, w);
		h = Math.max(0.5f, h);
		// the outline of light where it stood, thinning away
		light.add(new Seg(c.add(0, -h * 0.5, 0), c.add(0, h * 0.5 * (1f - t * 0.6f), 0), w * 0.5f * fade, w * 0.35f * fade, 0xFFE08A,
				0.45f * fade * fade, 0.2f * fade));
		for (int i = 0; i < 48; i++) {
			Vec3 start = c.add((hash(i, 1) - 0.5) * w, (hash(i, 2) - 0.5) * h, (hash(i, 3) - 0.5) * w);
			Vec3 out = new Vec3(hash(i, 4) - 0.5, 0.0, hash(i, 5) - 0.5);
			double k = age / 20.0;
			Vec3 pt = start.add(out.scale(k * 2.2)).add(0, k * (0.8 + hash(i, 6) * 1.6), 0);
			float sz = (float) (0.05 + 0.06 * hash(i, 7)) * fade;
			light.add(new Seg(pt, pt.add(0, 0.12 + 0.1 * hash(i, 8), 0), sz, sz * 0.4f, i % 3 == 0 ? 0xFFFFFF : 0xFFC83A, 0.9f * fade, 0.2f * fade));
		}
	}

	/** A pool of shadow spreading on the ground (concentric dark rings make the fill), tendrils rising out of it. */
	private static void pool(Vec3 c, float full, float age, float t, float life, List<Seg> dark, List<Seg> light) {
		float r = (float) com.projecthero.mod.sentry.VoidMoves.poolRadius((long) age);
		r = Math.min(r, full);
		float alpha = age > life - 10 ? Math.max(0f, (life - age) / 10f) : 1f;
		for (float rr = 0.3f; rr <= r; rr += 0.35f) {
			ring(c, new Vec3(0, 1, 0), rr, 0.24f, 0x050008, 0.7f * alpha, dark);
		}
		ring(c.add(0, 0.02, 0), new Vec3(0, 1, 0), r, 0.08f, 0x5020A0, 0.5f * alpha, light);
		for (int i = 0; i < 7; i++) {
			double a = hash(i, 11) * Math.PI * 2.0 + age * 0.01;
			double d = r * (0.25 + 0.65 * hash(i, 12));
			Vec3 foot = c.add(Math.cos(a) * d, 0, Math.sin(a) * d);
			float rise = (float) (0.8 + 1.4 * hash(i, 13)) * (0.6f + 0.4f * Mth.sin(age * 0.2f + i));
			tendril(foot, foot.add(Math.sin(age * 0.1 + i) * 0.3, rise, Math.cos(age * 0.1 + i) * 0.3), 0.12f, 12f, alpha, dark, light);
		}
	}

	/** A black sun with a gold corona above him; while it opens the dark gathers into him. */
	private static void eclipse(Vec3 c, float r, float age, float life, Vec3 cam, List<Seg> dark, List<Seg> light) {
		int windup = com.projecthero.mod.sentry.SentryConfig.ECLIPSE_WINDUP_TICKS;
		if (age < windup) {
			float k = age / windup;
			for (int i = 0; i < 3; i++) {
				Vec3 axis = new Vec3(Math.sin(age * 0.15 + i * 2.1), 0.5, Math.cos(age * 0.15 + i * 2.1)).normalize();
				ring(c.add(0, 1.0, 0), axis, 4f * (1f - k) + 0.6f, 0.15f, 0x050008, 0.6f + 0.3f * k, dark);
			}
			return;
		}
		float open = smooth(Math.min(1f, (age - windup) / 10f)) * (age > life - 10 ? Math.max(0f, (life - age) / 10f) : 1f);
		Vec3 sun = c.add(0, 7.0, 0);
		float sr = 2.4f * open;
		// the corona first (behind), then the black disc over it
		for (int i = 0; i < 3; i++) {
			ring(sun, cam.subtract(sun), sr * (1.05f + i * 0.12f), 0.18f - i * 0.04f, i == 0 ? 0xFFF4C0 : 0xFFB52A, (0.8f - i * 0.2f) * open, light);
		}
		for (int i = 0; i < 16; i++) {
			double a = i * Math.PI / 8.0 + age * 0.01;
			Vec3 n = cam.subtract(sun).normalize();
			Vec3 u = Math.abs(n.y) < 0.95 ? n.cross(new Vec3(0, 1, 0)).normalize() : n.cross(new Vec3(1, 0, 0)).normalize();
			Vec3 v = n.cross(u).normalize();
			Vec3 d = u.scale(Math.cos(a)).add(v.scale(Math.sin(a)));
			float len = (float) (0.8 + 0.9 * hash(i, 21)) * (0.8f + 0.2f * Mth.sin(age * 0.3f + i));
			light.add(new Seg(sun.add(d.scale(sr * 1.05)), sun.add(d.scale(sr * (1.05 + len))), 0.12f * open, 0.01f, 0xFFC83A, 0.7f * open, 0f));
		}
		disc(sun, sr, cam, 0x000000, 0.97f * open, dark);
		// the dark under it
		dome(c, r * open, 0x080410, 0.16f * open, 0.18f, dark);
		ring(c.add(0, 0.1, 0), new Vec3(0, 1, 0), r * open, 0.14f, 0x3A2060, 0.5f * open, light);
	}

	/** A figure of shadow (a clone, a nightmare manifestation): a black body facing {@code to}, two white pixel eyes. */
	private static void shade(Vec3 feet, Vec3 to, float h, float age, float alpha, List<Seg> dark, List<Seg> light) {
		if (alpha <= 0.01f) {
			return;
		}
		Vec3 fwd = new Vec3(to.x - feet.x, 0, to.z - feet.z);
		fwd = fwd.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : fwd.normalize();
		Vec3 side = fwd.cross(new Vec3(0, 1, 0)).normalize();
		Vec3 up = new Vec3(0, 1, 0);
		float sway = Mth.sin(age * 0.25f);
		int col = 0x030006;
		float a = 0.92f * alpha;
		Vec3 hip = feet.add(up.scale(h * 0.47));
		Vec3 neck = feet.add(up.scale(h * 0.78));
		// legs (a stalking step), the body, the arms reaching, the head
		dark.add(new Seg(hip.add(side.scale(h * 0.07)), feet.add(side.scale(h * 0.09)).add(fwd.scale(h * 0.1 * sway)), h * 0.1f, h * 0.06f, col, a, a * 0.6f));
		dark.add(new Seg(hip.add(side.scale(-h * 0.07)), feet.add(side.scale(-h * 0.09)).add(fwd.scale(-h * 0.1 * sway)), h * 0.1f, h * 0.06f, col, a, a * 0.6f));
		dark.add(new Seg(hip, neck, h * 0.17f, h * 0.2f, col, a, a));
		for (int s = -1; s <= 1; s += 2) {
			Vec3 sh = neck.add(side.scale(s * h * 0.13)).add(up.scale(-h * 0.03));
			Vec3 elbow = sh.add(fwd.scale(h * 0.14)).add(side.scale(s * h * 0.06)).add(up.scale(-h * 0.12));
			Vec3 hand = elbow.add(fwd.scale(h * (0.16 + 0.04 * Mth.sin(age * 0.4f + s)))).add(up.scale(-h * 0.02 * s * sway));
			dark.add(new Seg(sh, elbow, h * 0.07f, h * 0.06f, col, a, a));
			dark.add(new Seg(elbow, hand, h * 0.06f, h * 0.02f, col, a, a * 0.7f));
		}
		Vec3 head = neck.add(up.scale(h * 0.12));
		dark.add(new Seg(neck.add(up.scale(h * 0.02)), head.add(up.scale(h * 0.06)), h * 0.13f, h * 0.12f, col, a, a));
		// a wisp trailing off the head and the feet
		dark.add(new Seg(head.add(up.scale(h * 0.06)), head.add(up.scale(h * 0.2)).add(fwd.scale(-h * 0.15)), h * 0.06f, 0.0f, col, a * 0.6f, 0f));
		Vec3 eyes = head.add(up.scale(h * 0.02)).add(fwd.scale(h * 0.07));
		light.add(new Seg(eyes.add(side.scale(h * 0.035)), eyes.add(side.scale(h * 0.035)).add(up.scale(0.04)), 0.035f, 0.035f, 0xFFFFFF, alpha, alpha));
		light.add(new Seg(eyes.add(side.scale(-h * 0.035)), eyes.add(side.scale(-h * 0.035)).add(up.scale(0.04)), 0.035f, 0.035f, 0xFFFFFF, alpha, alpha));
	}
}
