package com.projecthero.mod.client.undead;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.undead.Undead;
import com.projecthero.mod.undead.UndeadBoneSword;
import com.projecthero.mod.undead.UndeadKitConfig;
import com.projecthero.mod.undead.UndeadKitNet;
import com.projecthero.mod.undead.UndeadParts;
import com.projecthero.mod.undead.UndeadShoulderSword;
import com.projecthero.mod.undead.data.UndeadState;
import com.projecthero.mod.undead.entity.UndeadPartEntity;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * v0.17.3 Undead kit: the client side of Z, Shift+Z, Shift+V and the shoulder sword, for every viewer.
 * <ul>
 * <li>Ten Twists (Z): blood wrung out of the arm and a tightening red spiral round it as it is wrenched round; a wind spiral
 * and blood trailing the drill; a ring of blood blown out where it stops.</li>
 * <li>Unluck Meteor (Shift + hold Z): red unluck embers rising round him, the sky darkening red, a red vortex churning high over
 * the spot he is looking at and a glowing ring on the ground there, growing with the charge; then the meteor -- a glowing rock
 * in a tail of fire -- streaking down, and the impact: a white flash, a fireball, a shockwave rolling out over the ground and
 * a scorch mark that slowly fades.</li>
 * <li>Rib Cage Guard (Shift+V): his ribs sprung out in front of him as a cage of bone, blood at their roots; a flash where
 * something hits them.</li>
 * <li>The shoulder sword: blood as it goes in or comes out; Shift + right click is sent to the server from here.</li>
 * </ul>
 * Fed by {@link UndeadSwordFx} (watch, tick, render) and the {@link UndeadKitNet.Fx} packet.
 */
public final class UndeadKitFx {
	private static final RandomSource RND = RandomSource.create();

	/** player -> client game time the meteor charge was first seen. */
	private static final Map<UUID, Long> CHARGE = new HashMap<>();
	/** player -> {when the ribs went up, when they started folding back (0 while up)}. */
	private static final Map<UUID, long[]> RIBS = new HashMap<>();
	private static final Map<UUID, Long> SEEN_ANIM = new HashMap<>();

	private record Meteor(Vec3 from, Vec3 to, long start, int ticks) {
	}

	private record Burst(int kind, Vec3 at, Vec3 dir, long start) {
	}

	private static final List<Meteor> METEORS = new ArrayList<>();
	private static final List<Burst> BURSTS = new ArrayList<>();
	/** The sky stays dark a little after the meteor is called (until the impact and after). */
	private static long darkUntil;
	private static boolean useWasDown;

	private UndeadKitFx() {
	}

	public static void initialize() {
		ClientPlayNetworking.registerGlobalReceiver(UndeadKitNet.Fx.TYPE, (payload, context) -> context.client().execute(() -> onFx(payload)));
	}

	static void reset() {
		CHARGE.clear();
		RIBS.clear();
		SEEN_ANIM.clear();
		METEORS.clear();
		BURSTS.clear();
		darkUntil = 0L;
		useWasDown = false;
		UndeadRibShotFx.reset(); // v0.17.3 tap V
	}

	private static long now() {
		Minecraft mc = Minecraft.getInstance();
		return mc.level == null ? 0L : mc.level.getGameTime();
	}

	private static void onFx(UndeadKitNet.Fx fx) {
		long now = now();
		switch (fx.kind()) {
			case UndeadKitNet.METEOR_FALL -> {
				METEORS.add(new Meteor(fx.a(), fx.b(), now, Math.max(1, fx.extra())));
				darkUntil = Math.max(darkUntil, now + fx.extra() + 60);
			}
			case UndeadKitNet.METEOR_IMPACT -> {
				BURSTS.add(new Burst(fx.kind(), fx.a(), fx.b(), now));
				Vec3 t = fx.a();
				for (int k = 0; k < 40; k++) {
					Vec3 d = new Vec3(RND.nextGaussian(), Math.abs(RND.nextGaussian()) + 0.6, RND.nextGaussian()).normalize();
					BloodFx.drop(t.x, t.y + 0.5, t.z, d.x * 0.7, d.y * 0.8, d.z * 0.7, 0.06f + RND.nextFloat() * 0.06f,
							RND.nextBoolean() ? 0x2A1E1A : 0x4A3A30, 40 + RND.nextInt(20), 0); // rock thrown up
				}
				BloodFx.embers(t.add(0, 1, 0), new Vec3(0, 0.3, 0), 30);
			}
			case UndeadKitNet.VORTEX_BURST, UndeadKitNet.RIB_BLOCK -> BURSTS.add(new Burst(fx.kind(), fx.a(), fx.b(), now));
			case UndeadKitNet.VORTEX_FIRE -> {
				BURSTS.add(new Burst(fx.kind(), fx.a(), fx.b(), now));
				if (!nearView(fx.a())) {
					BloodFx.spurt(fx.a(), fx.b().scale(-1).add(0, 0.5, 0), 10, 0.25, 0.5, Vec3.ZERO);
				}
			}
			default -> {
			}
		}
	}

	// ---------------------------------------------------------------- watching

	static void watch(Player p, UndeadState s, long now) {
		UndeadRibShotFx.watch(p, s, now); // v0.17.3 tap V: Rib Shotgun
		UUID id = p.getUUID();
		if (s.flag(UndeadKitConfig.FLAG_METEOR_CHARGE)) {
			CHARGE.putIfAbsent(id, now);
		} else {
			CHARGE.remove(id);
		}
		long[] r = RIBS.get(id);
		if (s.flag(UndeadKitConfig.FLAG_RIB_GUARD)) {
			if (r == null || r[1] != 0L) {
				RIBS.put(id, new long[] { now, 0L });
			}
		} else if (r != null && r[1] == 0L) {
			r[1] = now;
		} else if (r != null && now - r[1] > 8) {
			RIBS.remove(id);
		}
		Long seen = SEEN_ANIM.put(id, s.animStart);
		long age = now - s.animStart;
		if (seen != null && seen != s.animStart && age < 4) {
			if (s.animId == UndeadKitConfig.ANIM_SHOULDER_IN || s.animId == UndeadKitConfig.ANIM_SHOULDER_OUT) {
				Vec3 at = UndeadShoulderSword.shoulder(p);
				boolean in = s.animId == UndeadKitConfig.ANIM_SHOULDER_IN;
				BloodFx.spurt(at, new Vec3(0, 1, 0), in ? 8 : 14, in ? 0.18 : 0.3, 0.5, p.getDeltaMovement());
				BloodFx.mist(at, in ? 1 : 2, 0.2f, p.getDeltaMovement());
			}
		}
	}

	/** Meteor charge: 0..1 through it (client-timed from when the charge was first seen), or -1 if he is not charging. */
	public static float charge(Player p, float partial) {
		Long start = CHARGE.get(p.getUUID());
		if (start == null) {
			return -1f;
		}
		return Mth.clamp((now() - start + partial) / UndeadKitConfig.METEOR_CHARGE, 0f, 1f);
	}

	/** Rib Cage Guard: how far out the ribs are (0..1), 0 when not up. */
	public static float ribs(Player p, float partial) {
		long[] r = RIBS.get(p.getUUID());
		if (r == null) {
			return 0f;
		}
		float t = now() + partial;
		float out = Mth.clamp((t - r[0]) / 4f, 0f, 1f);
		out = out * out * (3f - 2f * out);
		if (r[1] != 0L) {
			out = Math.min(out, Mth.clamp(1f - (t - r[1]) / 5f, 0f, 1f));
		}
		return out;
	}

	// ---------------------------------------------------------------- ticking

	static void tick(long now) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			reset();
			return;
		}
		shoulderKey(mc);
		UndeadRibShotFx.tick(now); // v0.17.3 tap V: Rib Shotgun
		BURSTS.removeIf(b -> now - b.start() > (b.kind() == UndeadKitNet.METEOR_IMPACT ? 220 : 20));
		for (Iterator<Meteor> it = METEORS.iterator(); it.hasNext();) {
			Meteor m = it.next();
			long age = now - m.start();
			if (age > m.ticks()) {
				it.remove();
				continue;
			}
			Vec3 at = meteorAt(m, age);
			Vec3 dir = m.to().subtract(m.from()).normalize();
			for (int k = 0; k < 6; k++) {
				Vec3 o = new Vec3(RND.nextGaussian(), RND.nextGaussian(), RND.nextGaussian()).scale(0.9);
				Vec3 back = at.subtract(dir.scale(RND.nextDouble() * 4)).add(o);
				mc.level.addParticle(k % 3 == 0 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.FLAME, back.x, back.y, back.z, -dir.x * 0.1, -dir.y * 0.1,
						-dir.z * 0.1);
			}
			if (age % 2 == 0) {
				mc.level.addParticle(ParticleTypes.LAVA, at.x, at.y, at.z, 0, 0, 0);
			}
		}
		for (Player p : mc.level.players()) {
			UndeadState s = Undead.peek(p);
			if (s == null || !s.hasPower) {
				continue;
			}
			float k = charge(p, 0f);
			if (k >= 0f && !ownView(p)) {
				// unluck embers rising round him
				for (int i = 0; i < 1 + (int) (k * 4); i++) {
					double a = RND.nextDouble() * Math.PI * 2, r = 0.6 + RND.nextDouble() * (0.6 + k);
					BloodFx.drop(p.getX() + Math.cos(a) * r, p.getY() + RND.nextDouble() * 0.4, p.getZ() + Math.sin(a) * r, 0, 0.06 + k * 0.05, 0,
							0.03f + RND.nextFloat() * 0.03f, RND.nextBoolean() ? 0xFF2030 : 0xC0101A, 20 + RND.nextInt(16), 2);
				}
			}
			if (s.animId == UndeadKitConfig.ANIM_VORTEX_TWIST && !ownView(p)) {
				long age = now - s.animStart;
				if (age > 2 && age % 2 == 0) {
					int arm = s.flag(UndeadState.FLAG_OFF_ARM) ? Undead.otherArm(Undead.mainArm(p)) : Undead.mainArm(p);
					Vec3 h = UndeadSwordFx.hand(p, arm, 0.55);
					Vec3 d = new Vec3(RND.nextGaussian(), 0.2, RND.nextGaussian());
					BloodFx.spurt(h, d, 2, 0.1, 0.4, p.getDeltaMovement());
				}
			}
		}
		// the drill: blood flung off it as it spins
		for (Entity e : mc.level.entitiesForRendering()) {
			if (e instanceof UndeadPartEntity d && d.style() == UndeadPartEntity.STYLE_DRILL && !d.stuck() && d.getDeltaMovement().lengthSqr() > 0.5) {
				Vec3 c = d.position().add(0, d.getBbHeight() * 0.5, 0);
				Vec3 v = d.getDeltaMovement().normalize();
				Vec3 a = v.cross(Math.abs(v.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize();
				Vec3 b = v.cross(a).normalize();
				for (int k = 0; k < 3; k++) {
					double ang = (now + k / 3.0) * 2.1 + k * 2.09;
					Vec3 out = a.scale(Math.cos(ang)).add(b.scale(Math.sin(ang)));
					BloodFx.drop(c.x, c.y, c.z, out.x * 0.25 - v.x * 0.2, out.y * 0.25 - v.y * 0.2, out.z * 0.25 - v.z * 0.2, 0.025f + RND.nextFloat() * 0.025f,
							BloodFx.shade(RND.nextFloat()), 18 + RND.nextInt(10), 0);
				}
			}
		}
	}

	/** The local player's Shift + right click: sent on the press when it would move a sword in or out of his shoulder. */
	private static void shoulderKey(Minecraft mc) {
		Player p = mc.player;
		boolean down = p != null && mc.screen == null && mc.options.keyUse.isDown();
		if (down && !useWasDown && p.isShiftKeyDown() && Undead.hasPower(p) && !UndeadBoneSword.held(p) && mc.getConnection() != null) {
			ItemStack main = p.getMainHandItem();
			if (UndeadShoulderSword.isSword(main) || (main.isEmpty() && UndeadShoulderSword.has(p))) {
				ClientPlayNetworking.send(new UndeadKitNet.ShoulderSword());
			}
		}
		useWasDown = down;
	}

	/** {@code p} is the local player seen from his own eyes (his body is not drawn: effects round it would fill the view). */
	private static boolean ownView(Player p) {
		Minecraft mc = Minecraft.getInstance();
		return p == mc.player && mc.options.getCameraType().isFirstPerson();
	}

	/** {@code at} is right in front of the camera (in first person). */
	private static boolean nearView(Vec3 at) {
		Minecraft mc = Minecraft.getInstance();
		return mc.options.getCameraType().isFirstPerson() && mc.gameRenderer.getMainCamera().getPosition().distanceToSqr(at) < 2.5 * 2.5;
	}

	private static Vec3 meteorAt(Meteor m, float age) {
		float t = Mth.clamp(age / m.ticks(), 0f, 1f);
		return m.from().lerp(m.to(), t);
	}

	/** Where a charging Undead's meteor will land (as the server finds it, blocks only). */
	private static Vec3 target(Player p, float partial) {
		Vec3 eye = p.getEyePosition(partial);
		Vec3 far = eye.add(p.getViewVector(partial).scale(UndeadKitConfig.METEOR_REACH));
		BlockHitResult bhr = p.level().clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, p));
		if (bhr.getType() != HitResult.Type.MISS) {
			return bhr.getLocation();
		}
		BlockHitResult down = p.level().clip(new ClipContext(far, far.subtract(0, 96, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, p));
		return down.getType() != HitResult.Type.MISS ? down.getLocation() : far;
	}

	// ---------------------------------------------------------------- drawing

	static void render(WorldRenderContext context, PoseStack pose, Vec3 cam, float now) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || context.consumers() == null) {
			return;
		}
		float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
		MultiBufferSource buffers = context.consumers();
		VertexConsumer body = buffers.getBuffer(RenderType.debugQuads());
		Matrix4f m = pose.last().pose();
		List<Runnable> glow = new ArrayList<>();
		List<Runnable> solid = new ArrayList<>(); // (other render types: drawn last, so the quad buffer is not cut off mid-way)
		float dark = darkUntil > now ? Mth.clamp((darkUntil - now) / 30f, 0f, 1f) : 0f;
		for (Player p : mc.level.players()) {
			UndeadState s = Undead.peek(p);
			if (s == null || !s.hasPower) {
				continue;
			}
			float k = charge(p, partial);
			if (k >= 0f) {
				if (p.distanceToSqr(cam) < 96 * 96) {
					dark = Math.max(dark, k);
				}
				Vec3 t = target(p, partial);
				drawSkyVortex(body, glow, buffers, m, t.add(0, 42, 0).subtract(cam), k, now);
				float rr = (float) (1.0 + (UndeadKitConfig.METEOR_RADIUS - 1.0) * k);
				float pulse = 0.75f + 0.25f * Mth.sin(now * 0.5f);
				UndeadSwordFx.ringAt(body, glow, buffers, m, t.add(0, 0.06, 0).subtract(cam), new Vec3(1, 0, 0), new Vec3(0, 0, 1), rr, 0.35f + 0.25f * k,
						(0.45f + 0.5f * k) * pulse);
				if (!ownView(p)) {
					drawCoil(glow, buffers, m, p.getPosition(partial).subtract(cam), k, now);
				}
			}
			if (s.animId == UndeadKitConfig.ANIM_VORTEX_TWIST) {
				float age = now - s.animStart;
				if (age >= 0f && age < UndeadKitConfig.VORTEX_WINDUP + 1) {
					int arm = s.flag(UndeadState.FLAG_OFF_ARM) ? Undead.otherArm(Undead.mainArm(p)) : Undead.mainArm(p);
					drawTwist(glow, buffers, m, p, arm, partial, cam, age);
				}
			}
			// (v0.17.3 tap V: the Rib Shotgun's wind-up springs the same ribs half out before they fly)
			float ribs = Math.max(ribs(p, partial), UndeadRibShotFx.opening(p, partial));
			if (ribs > 0.01f) {
				solid.add(() -> drawRibs(pose, buffers, p, partial, cam, ribs, now));
			}
		}
		if (dark > 0.01f) {
			drawDarkSky(body, m, dark);
		}
		for (Entity e : mc.level.entitiesForRendering()) {
			if (e instanceof UndeadPartEntity d && d.style() == UndeadPartEntity.STYLE_DRILL && !d.stuck()) {
				drawDrillWake(glow, buffers, m, d, partial, cam, now);
			}
		}
		for (Meteor me : METEORS) {
			float age = now - me.start();
			if (age >= 0f && age <= me.ticks()) {
				drawMeteor(body, glow, buffers, m, me, age, cam, now);
			}
		}
		for (Burst b : BURSTS) {
			float age = now - b.start();
			if (age < 0f) {
				continue;
			}
			switch (b.kind()) {
				case UndeadKitNet.METEOR_IMPACT -> drawImpact(body, glow, buffers, m, b, age, cam, context.camera().rotation());
				case UndeadKitNet.VORTEX_BURST -> {
					Vec3 d = b.dir().normalize();
					Vec3 a = d.cross(Math.abs(d.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize();
					Vec3 bb = d.cross(a).normalize();
					float t = Mth.clamp(age / 8f, 0f, 1f);
					if (t < 1f) {
						float e = 1f - (1f - t) * (1f - t);
						UndeadSwordFx.ringAt(body, glow, buffers, m, b.at().subtract(cam), a, bb, 0.3f + e * (float) UndeadKitConfig.VORTEX_BURST_RADIUS,
								0.6f * (1f - t) + 0.05f, 1f - t);
						UndeadSwordFx.ringAt(body, glow, buffers, m, b.at().subtract(cam), new Vec3(1, 0, 0), new Vec3(0, 0, 1),
								0.2f + e * (float) UndeadKitConfig.VORTEX_BURST_RADIUS * 0.8f, 0.4f * (1f - t) + 0.05f, 0.8f * (1f - t));
					}
					UndeadSwordFx.star(glow, buffers, m, context.camera().rotation(), b.at().subtract(cam), age, 1.8f);
				}
				case UndeadKitNet.VORTEX_FIRE -> {
					Vec3 d = b.dir().normalize();
					Vec3 a = d.cross(Math.abs(d.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize();
					Vec3 bb = d.cross(a).normalize();
					float t = Mth.clamp(age / 6f, 0f, 1f);
					if (t < 1f) {
						// the shock ring as it leaves the shoulder
						UndeadSwordFx.ringAt(body, glow, buffers, m, b.at().add(d.scale(0.6 + t)).subtract(cam), a, bb, 0.2f + 0.9f * t, 0.15f * (1f - t) + 0.03f,
								1f - t);
					}
				}
				case UndeadKitNet.RIB_BLOCK -> UndeadSwordFx.star(glow, buffers, m, context.camera().rotation(), b.at().subtract(cam), age * 1.4f, 0.7f);
				default -> {
				}
			}
		}
		for (Runnable r : glow) {
			r.run();
		}
		for (Runnable r : solid) {
			r.run();
		}
	}

	/** The sky darkening red round the viewer: an inside-out dome far out (terrain in front of it hides it: only the sky dims). */
	private static void drawDarkSky(VertexConsumer vc, Matrix4f m, float k) {
		float r = 90f;
		int lon = 24, lat = 8;
		float alpha = 0.66f * k;
		for (int j = 0; j < lat; j++) {
			float p0 = Mth.lerp(j / (float) lat, -0.02f, Mth.HALF_PI), p1 = Mth.lerp((j + 1) / (float) lat, -0.02f, Mth.HALF_PI);
			float f0 = j == 0 ? 0f : 1f; // the lowest ring fades in from nothing at the horizon
			for (int i = 0; i < lon; i++) {
				float a0 = i / (float) lon * Mth.TWO_PI, a1 = (i + 1) / (float) lon * Mth.TWO_PI;
				Vec3 v00 = sph(r, a0, p0), v10 = sph(r, a1, p0), v11 = sph(r, a1, p1), v01 = sph(r, a0, p1);
				// redder low down, near black overhead
				float lo = 1f - j / (float) lat, hi = 1f - (j + 1) / (float) lat;
				UndeadSwordFx.band(vc, m, v00, v10, v11, v01, 0.42f * lo + 0.06f, 0.0f, 0.03f, alpha * f0, 0.42f * lo + 0.06f, 0.0f, 0.03f, alpha * f0,
						0.42f * hi + 0.06f, 0.0f, 0.03f, alpha, 0.42f * hi + 0.06f, 0.0f, 0.03f, alpha);
			}
		}
	}

	private static Vec3 sph(float r, float a, float p) {
		return new Vec3(Mth.cos(a) * Mth.cos(p) * r, Mth.sin(p) * r, Mth.sin(a) * Mth.cos(p) * r);
	}

	/** The red vortex high over the target: arms of dark red cloud spiralling into a glowing eye, turning faster as it builds. */
	private static void drawSkyVortex(VertexConsumer body, List<Runnable> glow, MultiBufferSource buffers, Matrix4f m, Vec3 c, float k, float now) {
		float rad = 4f + 20f * k;
		float spin = now * (0.03f + 0.08f * k);
		int arms = 4, seg = 18;
		for (int a = 0; a < arms; a++) {
			float base = spin + a * Mth.TWO_PI / arms;
			for (int i = 0; i < seg; i++) {
				float t0 = i / (float) seg, t1 = (i + 1) / (float) seg;
				float r0 = rad * t0, r1 = rad * t1;
				float g0 = base + t0 * 3.4f, g1 = base + t1 * 3.4f;
				float w0 = (0.4f + 2.2f * t0) * (0.5f + 0.5f * k), w1 = (0.4f + 2.2f * t1) * (0.5f + 0.5f * k);
				Vec3 p0 = c.add(Mth.cos(g0) * r0, -t0 * 3, Mth.sin(g0) * r0), p1 = c.add(Mth.cos(g1) * r1, -t1 * 3, Mth.sin(g1) * r1);
				Vec3 o0 = new Vec3(Mth.cos(g0 + Mth.HALF_PI), 0, Mth.sin(g0 + Mth.HALF_PI)).scale(w0);
				Vec3 o1 = new Vec3(Mth.cos(g1 + Mth.HALF_PI), 0, Mth.sin(g1 + Mth.HALF_PI)).scale(w1);
				float al0 = (1f - t0) * 0.8f * k + 0.1f, al1 = (1f - t1) * 0.8f * k + 0.1f;
				UndeadSwordFx.band(body, m, p0.subtract(o0), p1.subtract(o1), p1.add(o1), p0.add(o0), 0.45f, 0.0f, 0.05f, al0, 0.45f, 0.0f, 0.05f, al1, 0.2f,
						0.0f, 0.02f, al0 * 0.3f, 0.2f, 0.0f, 0.02f, al1 * 0.3f);
			}
		}
		// the eye: a glowing red disc
		float eye = 1.2f + 3.5f * k;
		glow.add(() -> {
			VertexConsumer add = buffers.getBuffer(RenderType.lightning());
			int n = 20;
			for (int i = 0; i < n; i++) {
				float a0 = i / (float) n * Mth.TWO_PI, a1 = (i + 1) / (float) n * Mth.TWO_PI;
				Vec3 p0 = c.add(Mth.cos(a0) * eye, 0, Mth.sin(a0) * eye), p1 = c.add(Mth.cos(a1) * eye, 0, Mth.sin(a1) * eye);
				UndeadSwordFx.band(add, m, c, c, p1, p0, 1f, 0.3f, 0.3f, 0.9f * k, 1f, 0.3f, 0.3f, 0.9f * k, 0.9f, 0.05f, 0.1f, 0f, 0.9f, 0.05f, 0.1f, 0f);
			}
		});
	}

	/** Unluck winding up round him: red bands coiling up his body, more of them and faster as the charge builds. */
	private static void drawCoil(List<Runnable> glow, MultiBufferSource buffers, Matrix4f m, Vec3 feet, float k, float now) {
		glow.add(() -> {
			VertexConsumer add = buffers.getBuffer(RenderType.lightning());
			int strands = 2 + (int) (k * 2);
			for (int sI = 0; sI < strands; sI++) {
				float phase = now * (0.25f + 0.35f * k) + sI * Mth.TWO_PI / strands;
				int n = 24;
				for (int i = 0; i < n; i++) {
					float t0 = i / (float) n, t1 = (i + 1) / (float) n;
					float r0 = 0.75f + 0.25f * Mth.sin(t0 * 6f + now * 0.2f), r1 = 0.75f + 0.25f * Mth.sin(t1 * 6f + now * 0.2f);
					float g0 = phase + t0 * 7f, g1 = phase + t1 * 7f;
					Vec3 p0 = feet.add(Mth.cos(g0) * r0, t0 * 2.4f, Mth.sin(g0) * r0), p1 = feet.add(Mth.cos(g1) * r1, t1 * 2.4f, Mth.sin(g1) * r1);
					float a0 = Mth.sin(t0 * Mth.PI) * (0.25f + 0.6f * k), a1 = Mth.sin(t1 * Mth.PI) * (0.25f + 0.6f * k);
					Vec3 up = new Vec3(0, 0.05f + 0.05f * k, 0);
					UndeadSwordFx.band(add, m, p0.subtract(up), p1.subtract(up), p1.add(up), p0.add(up), 1f, 0.15f, 0.2f, a0, 1f, 0.15f, 0.2f, a1, 1f, 0.15f,
							0.2f, a0, 1f, 0.15f, 0.2f, a1);
				}
			}
		});
	}

	/** Ten Twists: a red spiral tightening round the arm, shoulder to hand, wound a turn further with each twist. */
	private static void drawTwist(List<Runnable> glow, MultiBufferSource buffers, Matrix4f m, Player p, int arm, float partial, Vec3 cam, float age) {
		Vec3 off = p.getPosition(partial).subtract(p.position());
		Vec3 sh = UndeadParts.centre(p, arm).add(0, 0.3, 0).add(off);
		Vec3 hand = UndeadSwordFx.hand(p, arm, 0.75).add(off);
		Vec3 d = hand.subtract(sh);
		double len = d.length();
		if (len < 0.1) {
			return;
		}
		Vec3 dn = d.normalize();
		Vec3 a = dn.cross(Math.abs(dn.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize();
		Vec3 b = dn.cross(a).normalize();
		float k = Mth.clamp(age / UndeadKitConfig.VORTEX_WINDUP, 0f, 1f);
		float turns = 1f + k * UndeadKitConfig.VORTEX_TWISTS * 0.5f;
		float spin = age * 1.6f;
		glow.add(() -> {
			VertexConsumer add = buffers.getBuffer(RenderType.lightning());
			for (int sI = 0; sI < 2; sI++) {
				int n = 30;
				for (int i = 0; i < n; i++) {
					float t0 = i / (float) n, t1 = (i + 1) / (float) n;
					float g0 = spin + sI * Mth.PI + t0 * turns * Mth.TWO_PI, g1 = spin + sI * Mth.PI + t1 * turns * Mth.TWO_PI;
					float r = 0.2f - 0.05f * k;
					Vec3 p0 = sh.add(dn.scale(len * t0)).add(a.scale(Mth.cos(g0) * r)).add(b.scale(Mth.sin(g0) * r)).subtract(cam);
					Vec3 p1 = sh.add(dn.scale(len * t1)).add(a.scale(Mth.cos(g1) * r)).add(b.scale(Mth.sin(g1) * r)).subtract(cam);
					Vec3 w = dn.scale(0.035);
					float al = (0.3f + 0.6f * k) * Mth.sin(t0 * Mth.PI);
					UndeadSwordFx.band(add, m, p0.subtract(w), p1.subtract(w), p1.add(w), p0.add(w), 1f, 0.2f, 0.2f, al, 1f, 0.2f, 0.2f, al, 1f, 0.2f, 0.2f, al,
							1f, 0.2f, 0.2f, al);
				}
			}
		});
	}

	/** The drill's wake: a twisting spiral of wind and blood trailing off it. */
	private static void drawDrillWake(List<Runnable> glow, MultiBufferSource buffers, Matrix4f m, UndeadPartEntity d, float partial, Vec3 cam, float now) {
		Vec3 v = d.getDeltaMovement();
		if (v.lengthSqr() < 0.25) {
			return;
		}
		Vec3 dn = v.normalize();
		Vec3 c = d.getPosition(partial).add(0, d.getBbHeight() * 0.5, 0).subtract(cam);
		Vec3 a = dn.cross(Math.abs(dn.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize();
		Vec3 b = dn.cross(a).normalize();
		glow.add(() -> {
			VertexConsumer add = buffers.getBuffer(RenderType.lightning());
			for (int sI = 0; sI < 3; sI++) {
				int n = 26;
				for (int i = 0; i < n; i++) {
					float t0 = i / (float) n, t1 = (i + 1) / (float) n;
					float L = 5.5f;
					float g0 = -now * 1.9f + sI * Mth.TWO_PI / 3 + t0 * 9f, g1 = -now * 1.9f + sI * Mth.TWO_PI / 3 + t1 * 9f;
					float r0 = 0.25f + 0.55f * t0, r1 = 0.25f + 0.55f * t1;
					Vec3 p0 = c.subtract(dn.scale(L * t0)).add(a.scale(Mth.cos(g0) * r0)).add(b.scale(Mth.sin(g0) * r0));
					Vec3 p1 = c.subtract(dn.scale(L * t1)).add(a.scale(Mth.cos(g1) * r1)).add(b.scale(Mth.sin(g1) * r1));
					Vec3 w = a.scale(Mth.cos(g0)).add(b.scale(Mth.sin(g0))).scale(0.06);
					float al = (1f - t0) * 0.75f;
					UndeadSwordFx.band(add, m, p0.subtract(w), p1.subtract(w), p1.add(w), p0.add(w), 1f, 0.3f + 0.5f * (1 - t0), 0.3f + 0.4f * (1 - t0), al, 1f,
							0.3f + 0.5f * (1 - t1), 0.3f + 0.4f * (1 - t1), (1f - t1) * 0.75f, 1f, 0.3f, 0.3f, al, 1f, 0.3f, 0.3f, (1f - t1) * 0.75f);
				}
			}
		});
	}

	/** The meteor: a lumpy glowing rock tumbling down in a long tail of fire. */
	private static void drawMeteor(VertexConsumer body, List<Runnable> glow, MultiBufferSource buffers, Matrix4f m, Meteor me, float age, Vec3 cam,
			float now) {
		Vec3 at = meteorAt(me, age);
		Vec3 dir = me.to().subtract(me.from()).normalize();
		Vec3 c = at.subtract(cam);
		Vec3 a = dir.cross(Math.abs(dir.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize();
		Vec3 b = dir.cross(a).normalize();
		// the rock: a few overlapping lumps turning together
		float spin = now * 0.35f;
		float[][] lumps = { { 0, 0, 0, 1.7f }, { 0.9f, 0.4f, 0.2f, 1.1f }, { -0.7f, 0.7f, -0.3f, 1.2f }, { 0.2f, -0.9f, 0.5f, 1.0f },
				{ -0.4f, -0.3f, 0.9f, 0.9f }, { 0.5f, 0.2f, -1.0f, 1.0f } };
		Quaternionf q = new Quaternionf().rotateXYZ(spin, spin * 0.7f, spin * 0.4f);
		for (float[] l : lumps) {
			Vector3f o = new Vector3f(l[0], l[1], l[2]).rotate(q);
			cube(body, m, c.add(o.x * 1.6f, o.y * 1.6f, o.z * 1.6f), l[3] * 1.6f, q, 0.16f, 0.12f, 0.11f, 1f);
		}
		glow.add(() -> {
			VertexConsumer add = buffers.getBuffer(RenderType.lightning());
			// the glowing crust round it
			for (float[] l : lumps) {
				Vector3f o = new Vector3f(l[0], l[1], l[2]).rotate(q);
				cube(add, m, c.add(o.x * 1.6f, o.y * 1.6f, o.z * 1.6f), l[3] * 1.8f, q, 1f, 0.35f, 0.08f, 0.6f);
			}
			// the tail: a cone of fire behind it, white-hot at the rock, orange, then dull red
			float len = Math.min(26f, (float) at.distanceTo(me.from()) + 2f);
			int rings = 10, sides = 12;
			for (int j = 0; j < rings; j++) {
				float t0 = j / (float) rings, t1 = (j + 1) / (float) rings;
				float r0 = 2.6f * (1f - t0 * 0.85f), r1 = 2.6f * (1f - t1 * 0.85f);
				float al0 = (1f - t0) * 0.85f, al1 = (1f - t1) * 0.85f;
				for (int i = 0; i < sides; i++) {
					float g0 = i / (float) sides * Mth.TWO_PI + now * 0.3f, g1 = (i + 1) / (float) sides * Mth.TWO_PI + now * 0.3f;
					Vec3 q00 = c.subtract(dir.scale(len * t0)).add(a.scale(Mth.cos(g0) * r0)).add(b.scale(Mth.sin(g0) * r0));
					Vec3 q01 = c.subtract(dir.scale(len * t0)).add(a.scale(Mth.cos(g1) * r0)).add(b.scale(Mth.sin(g1) * r0));
					Vec3 q11 = c.subtract(dir.scale(len * t1)).add(a.scale(Mth.cos(g1) * r1)).add(b.scale(Mth.sin(g1) * r1));
					Vec3 q10 = c.subtract(dir.scale(len * t1)).add(a.scale(Mth.cos(g0) * r1)).add(b.scale(Mth.sin(g0) * r1));
					float gg0 = 0.6f - 0.55f * t0, gg1 = 0.6f - 0.55f * t1;
					UndeadSwordFx.band(add, m, q00, q01, q11, q10, 1f, gg0, 0.35f - 0.3f * t0, al0, 1f, gg0, 0.35f - 0.3f * t0, al0, 1f, gg1, 0.35f - 0.3f * t1,
							al1, 1f, gg1, 0.35f - 0.3f * t1, al1);
				}
			}
		});
	}

	/** The impact: the flash, the fireball, the shockwave over the ground, the fading scorch and a pillar of red. */
	private static void drawImpact(VertexConsumer body, List<Runnable> glow, MultiBufferSource buffers, Matrix4f m, Burst b, float age, Vec3 cam,
			Quaternionf camRot) {
		Vec3 c = b.at().subtract(cam);
		Vec3 x = new Vec3(1, 0, 0), z = new Vec3(0, 0, 1);
		// the scorch: a dark disc that slowly fades
		float scorch = Mth.clamp(1f - (age - 60f) / 160f, 0f, 1f) * Mth.clamp(age / 2f, 0f, 1f);
		if (scorch > 0f) {
			int n = 28;
			float r = 7.5f;
			for (int i = 0; i < n; i++) {
				float a0 = i / (float) n * Mth.TWO_PI, a1 = (i + 1) / (float) n * Mth.TWO_PI;
				Vec3 p0 = c.add(Mth.cos(a0) * r, 0.04, Mth.sin(a0) * r), p1 = c.add(Mth.cos(a1) * r, 0.04, Mth.sin(a1) * r);
				Vec3 cc = c.add(0, 0.04, 0);
				UndeadSwordFx.band(body, m, cc, cc, p1, p0, 0.05f, 0.02f, 0.02f, 0.8f * scorch, 0.05f, 0.02f, 0.02f, 0.8f * scorch, 0.12f, 0.03f, 0.02f, 0f,
						0.12f, 0.03f, 0.02f, 0f);
			}
		}
		// the shockwave rolling out over the ground
		float t = Mth.clamp(age / 16f, 0f, 1f);
		if (t < 1f) {
			float e = 1f - (1f - t) * (1f - t);
			UndeadSwordFx.ringAt(body, glow, buffers, m, c.add(0, 0.15, 0), x, z, 1.5f + 17f * e, 2.2f * (1f - t) + 0.2f, 1f - t);
			// a low wall of dust riding the ring
			int n = 32;
			float r = 1.5f + 17f * e, h = 2.2f * (1f - t);
			for (int i = 0; i < n; i++) {
				float a0 = i / (float) n * Mth.TWO_PI, a1 = (i + 1) / (float) n * Mth.TWO_PI;
				Vec3 p0 = c.add(Mth.cos(a0) * r, 0.1, Mth.sin(a0) * r), p1 = c.add(Mth.cos(a1) * r, 0.1, Mth.sin(a1) * r);
				UndeadSwordFx.band(body, m, p0, p1, p1.add(0, h, 0), p0.add(0, h, 0), 0.35f, 0.22f, 0.15f, 0.55f * (1f - t), 0.35f, 0.22f, 0.15f, 0.55f * (1f - t),
						0.35f, 0.22f, 0.15f, 0f, 0.35f, 0.22f, 0.15f, 0f);
			}
		}
		float fb = Mth.clamp(age / 10f, 0f, 1f);
		glow.add(() -> {
			VertexConsumer add = buffers.getBuffer(RenderType.lightning());
			// the fireball
			if (fb < 1f) {
				float r = 1.5f + 7f * (1f - (1f - fb) * (1f - fb));
				sphere(add, m, c.add(0, r * 0.35, 0), r, 1f, 0.55f - 0.3f * fb, 0.15f, 0.8f * (1f - fb));
				sphere(add, m, c.add(0, r * 0.3, 0), r * 0.6f, 1f, 0.95f, 0.75f, 0.9f * (1f - fb));
			}
			// the pillar of unluck red
			float pt = Mth.clamp(age / 14f, 0f, 1f);
			if (pt < 1f) {
				float w = 2.5f * (1f - pt);
				for (Vec3 s : new Vec3[] { x, z }) {
					UndeadSwordFx.band(add, m, c.subtract(s.scale(w)), c.add(s.scale(w)), c.add(s.scale(w * 0.2)).add(0, 60, 0),
							c.subtract(s.scale(w * 0.2)).add(0, 60, 0), 1f, 0.2f, 0.25f, 0.9f * (1f - pt), 1f, 0.2f, 0.25f, 0.9f * (1f - pt), 1f, 0.1f, 0.15f, 0f,
							1f, 0.1f, 0.15f, 0f);
				}
			}
			// the flash: a sheet of light over the whole view, for anyone near enough
			double dist = c.length();
			float flash = Mth.clamp(1f - age / 7f, 0f, 1f) * (float) Mth.clamp(1.0 - dist / 70.0, 0.0, 1.0);
			if (flash > 0f) {
				Vector3f fwd = new Vector3f(0, 0, -1).rotate(camRot), up = new Vector3f(0, 1, 0).rotate(camRot), rt = new Vector3f(1, 0, 0).rotate(camRot);
				Vec3 F = new Vec3(fwd.x, fwd.y, fwd.z).scale(0.3), U = new Vec3(up.x, up.y, up.z), R = new Vec3(rt.x, rt.y, rt.z);
				UndeadSwordFx.quad(add, m, F.subtract(R).subtract(U), F.add(R).subtract(U), F.add(R).add(U), F.subtract(R).add(U), 1f, 0.85f, 0.7f,
						0.95f * flash);
			}
		});
	}

	/** A box of side {@code s} round {@code c}, turned by {@code q}, one colour, slightly shaded per face. */
	private static void cube(VertexConsumer vc, Matrix4f m, Vec3 c, float s, Quaternionf q, float r, float g, float b, float a) {
		float h = s * 0.5f;
		Vector3f[] p = new Vector3f[8];
		for (int i = 0; i < 8; i++) {
			p[i] = new Vector3f((i & 1) == 0 ? -h : h, (i & 2) == 0 ? -h : h, (i & 4) == 0 ? -h : h).rotate(q).add((float) c.x, (float) c.y, (float) c.z);
		}
		int[][] faces = { { 0, 1, 3, 2 }, { 4, 6, 7, 5 }, { 0, 4, 5, 1 }, { 2, 3, 7, 6 }, { 0, 2, 6, 4 }, { 1, 5, 7, 3 } };
		float[] shade = { 0.8f, 1f, 0.7f, 1.1f, 0.9f, 0.95f };
		for (int f = 0; f < 6; f++) {
			int[] fi = faces[f];
			float sh = shade[f];
			Vec3 v0 = vec(p[fi[0]]), v1 = vec(p[fi[1]]), v2 = vec(p[fi[2]]), v3 = vec(p[fi[3]]);
			UndeadSwordFx.quad(vc, m, v0, v1, v2, v3, Math.min(1f, r * sh), Math.min(1f, g * sh), Math.min(1f, b * sh), a);
		}
	}

	private static Vec3 vec(Vector3f v) {
		return new Vec3(v.x, v.y, v.z);
	}

	private static void sphere(VertexConsumer vc, Matrix4f m, Vec3 c, float r, float cr, float cg, float cb, float a) {
		int lon = 16, lat = 8;
		for (int j = 0; j < lat; j++) {
			float p0 = Mth.lerp(j / (float) lat, -Mth.HALF_PI, Mth.HALF_PI), p1 = Mth.lerp((j + 1) / (float) lat, -Mth.HALF_PI, Mth.HALF_PI);
			for (int i = 0; i < lon; i++) {
				float a0 = i / (float) lon * Mth.TWO_PI, a1 = (i + 1) / (float) lon * Mth.TWO_PI;
				// fainter toward the rim as seen from anywhere: a soft ball
				float al = a * (0.35f + 0.65f * Mth.cos((p0 + p1) * 0.5f));
				UndeadSwordFx.quad(vc, m, c.add(sph(r, a0, p0)), c.add(sph(r, a1, p0)), c.add(sph(r, a1, p1)), c.add(sph(r, a0, p1)), cr, cg, cb, al);
			}
		}
	}

	/**
	 * Rib Cage Guard: his ribs sprung out of his chest in front of him -- six a side, curving out and back in toward the front, a
	 * gap down the middle -- grown out by {@code k}; blood where they leave him. World space, so the viewer sees it in first
	 * person too.
	 */
	private static void drawRibs(PoseStack pose, MultiBufferSource buffers, Player p, float partial, Vec3 cam, float k, float now) {
		Minecraft mc = Minecraft.getInstance();
		Vec3 chest = p.getPosition(partial).add(0, p.getBbHeight() * 0.62, 0);
		int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(chest));
		pose.pushPose();
		pose.translate(chest.x - cam.x, chest.y - cam.y, chest.z - cam.z);
		pose.mulPose(Axis.YP.rotationDegrees(-p.getViewYRot(partial)));
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(UndeadRender.PALETTE));
		PoseStack.Pose pp = pose.last();
		int segs = 9;
		for (int side = -1; side <= 1; side += 2) {
			for (int r = 0; r < 6; r++) {
				float y = 6f - r * 2.6f; // px, top rib first
				float reach = (0.75f + 0.25f * (1f - Math.abs(r - 2.5f) / 2.5f)) * k;
				float px = 0, pz = 0;
				for (int i = 0; i <= segs; i++) {
					float t = i / (float) segs * reach;
					float x = side * (3.5f + 9.5f * Mth.sin(Mth.PI * t) - 1.5f * t);
					float zz = 1.5f + 13f * t;
					float yy = y - 2.2f * t * t; // they droop a little toward their tips
					float th = 0.75f - 0.3f * t / Math.max(0.01f, reach);
					if (i > 0) {
						int col = (i + r) % 3 == 0 ? UndeadBoneSwordRender.BONE_SHADE : UndeadBoneSwordRender.BONE;
						UndeadBoneSwordRender.box(vc, pp, Math.min(px, x) - th, yy - th, Math.min(pz, zz) - th, Math.max(px, x) + th, yy + th, Math.max(pz, zz) + th,
								col, light);
					}
					px = x;
					pz = zz;
				}
				// a knob of bone at the tip, and blood where the rib leaves his chest
				UndeadBoneSwordRender.box(vc, pp, px - 0.7f, y - 2.2f * reach * reach - 0.7f, pz - 0.7f, px + 0.7f, y - 2.2f * reach * reach + 0.7f, pz + 0.7f,
						UndeadBoneSwordRender.BONE_DARK, light);
				UndeadBoneSwordRender.box(vc, pp, side * 3.5f - 0.9f, y - 0.8f, 0.8f, side * 3.5f + 0.9f, y + 0.8f, 2.6f, UndeadBoneSwordRender.BLOOD, light);
			}
		}
		// the split sternum, pushed out in front
		float sz = 1.5f + 3f * k;
		for (int side = -1; side <= 1; side += 2) {
			UndeadBoneSwordRender.box(vc, pp, side * 1.4f - 0.6f, -8f, sz - 0.6f, side * 1.4f + 0.6f, 7f, sz + 0.6f, UndeadBoneSwordRender.BONE_SHADE, light);
			UndeadBoneSwordRender.box(vc, pp, side * 1.4f - 0.65f, -2f, sz - 0.65f, side * 1.4f + 0.65f, 1f, sz + 0.65f, UndeadBoneSwordRender.BLOOD_WET, light);
		}
		pose.popPose();
	}
}
