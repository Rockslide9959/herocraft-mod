package com.projecthero.mod.client.saiyan.kit;

import static com.projecthero.mod.client.saiyan.kit.SaiyanKitDraw.G;
import static com.projecthero.mod.client.saiyan.kit.SaiyanKitDraw.S;
import static com.projecthero.mod.client.saiyan.kit.SaiyanKitDraw.b;
import static com.projecthero.mod.client.saiyan.kit.SaiyanKitDraw.g;
import static com.projecthero.mod.client.saiyan.kit.SaiyanKitDraw.r;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.client.beamclash.BeamClashClient;
import com.projecthero.mod.saiyan.kit.SaiyanKitSounds;
import com.projecthero.mod.saiyan.kit.SaiyanKitFxPayload;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * v0.17.3 Saiyan revamp: the move kit's look for every viewer, from {@link SaiyanKitFxPayload}s -- glowing Ki spheres with
 * short tails and little blasts, the Kamehameha (a ball gathering between the cupped hands at his side, then a big blue beam
 * with a white-hot core, spiral streaks and a boiling end; it hides in a clash, {@link BeamClashClient#hides}), punch
 * impacts, afterimages and Ki trails, the Explosive Wave's dome, slam craters (dust and rock chips only), the Solar Flare's
 * flash and Ki Rain's target ring. Keeps the poses' clock ({@link SaiyanKitPose}).
 */
public final class SaiyanKitClient {
	private SaiyanKitClient() {
	}

	/** entity id -> {anim, start game time}. */
	private static final Map<Integer, long[]> ANIMS = new HashMap<>();

	private record BeamState(long start, float maxCharge, int rgb, long fired, float charge, int fireTicks) {
	}

	private record Shot(int owner, Vec3 from, Vec3 vel, float size, int rgb, long start) {
	}

	private record Timed(Vec3 pos, Vec3 dir, float size, int rgb, long start, int extra) {
	}

	private record Ghost(Vec3 from, Vec3 to, float yaw, int rgb, long start) {
	}

	private static final class Trail {
		final int rgb;
		final long until;
		final ArrayDeque<Vec3> points = new ArrayDeque<>();

		Trail(int rgb, long until) {
			this.rgb = rgb;
			this.until = until;
		}
	}

	private static final Map<Integer, BeamState> BEAMS = new HashMap<>();
	private static final Map<Integer, Shot> SHOTS = new HashMap<>();
	private static final List<Timed> BURSTS = new ArrayList<>();
	private static final List<Timed> IMPACTS = new ArrayList<>();
	private static final List<Timed> DOMES = new ArrayList<>();
	private static final List<Timed> CRATERS = new ArrayList<>();
	private static final List<Timed> FLASHES = new ArrayList<>();
	private static final List<Timed> MARKS = new ArrayList<>();
	private static final List<Ghost> GHOSTS = new ArrayList<>();
	private static final Map<Integer, Trail> TRAILS = new HashMap<>();

	public static void initialize() {
		ClientPlayNetworking.registerGlobalReceiver(SaiyanKitFxPayload.TYPE, (payload, context) -> context.client().execute(() -> receive(payload)));
		WorldRenderEvents.AFTER_ENTITIES.register(SaiyanKitClient::render);
		ClientTickEvents.END_CLIENT_TICK.register(SaiyanKitClient::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> reset());
	}

	static void reset() {
		ANIMS.clear();
		BEAMS.clear();
		SHOTS.clear();
		BURSTS.clear();
		IMPACTS.clear();
		DOMES.clear();
		CRATERS.clear();
		FLASHES.clear();
		MARKS.clear();
		GHOSTS.clear();
		TRAILS.clear();
	}

	private static long now() {
		ClientLevel l = Minecraft.getInstance().level;
		return l == null ? 0L : l.getGameTime();
	}

	// ---------------------------------------------------------------- the payloads

	static void receive(SaiyanKitFxPayload p) {
		long now = now();
		switch (p.kind()) {
			case SaiyanKitFxPayload.ANIM -> {
				if (p.extra() == 0) {
					ANIMS.remove(p.entity());
				} else {
					ANIMS.put(p.entity(), new long[] { p.extra(), now });
				}
			}
			case SaiyanKitFxPayload.BEAM_CHARGE -> BEAMS.put(p.entity(), new BeamState(now, p.f(), p.rgb(), -1L, 0f, 0));
			case SaiyanKitFxPayload.BEAM_FIRE -> {
				BeamState b = BEAMS.get(p.entity());
				BEAMS.put(p.entity(), new BeamState(b == null ? now : b.start(), b == null ? 40f : b.maxCharge(), p.rgb(), now, p.f(), p.extra()));
				Entity shooter = Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.getEntity(p.entity());
				if (shooter != null) {
					Minecraft.getInstance().getSoundManager().play(new BeamLoop(shooter, 0.85f - 0.15f * p.f()));
				}
			}
			case SaiyanKitFxPayload.BEAM_END -> BEAMS.remove(p.entity());
			case SaiyanKitFxPayload.SHOT -> SHOTS.put(p.extra(), new Shot(p.entity(), p.a(), p.b(), p.f(), p.rgb(), now));
			case SaiyanKitFxPayload.SHOT_END -> {
				SHOTS.remove(p.extra());
				BURSTS.add(new Timed(p.a(), Vec3.ZERO, p.f(), p.rgb(), now, 0));
				sparks(p.a(), p.rgb(), (int) (5 + p.f() * 6), 0.12 + p.f() * 0.12);
				if (p.f() > 1.2f) {
					particles(ParticleTypes.POOF, p.a(), 3, 0.05);
				}
			}
			case SaiyanKitFxPayload.IMPACT -> {
				IMPACTS.add(new Timed(p.a(), p.b(), p.f(), p.rgb(), now, 0));
				sparks(p.a(), p.rgb(), (int) (6 + p.f() * 8), 0.25 + p.f() * 0.15);
			}
			case SaiyanKitFxPayload.GHOST -> {
				GHOSTS.add(new Ghost(p.a(), p.b(), p.f(), p.rgb(), now));
				sparks(p.a().add(0, 1, 0), p.rgb(), 12, 0.3);
			}
			case SaiyanKitFxPayload.TRAIL -> TRAILS.put(p.entity(), new Trail(p.rgb(), now + Math.max(4, p.extra())));
			case SaiyanKitFxPayload.DOME -> {
				DOMES.add(new Timed(p.a(), Vec3.ZERO, p.f(), p.rgb(), now, 0));
				groundBlast(p.a().add(0, -0.9, 0), p.rgb(), p.f(), 40);
			}
			case SaiyanKitFxPayload.CRATER -> {
				CRATERS.add(new Timed(p.a(), Vec3.ZERO, p.f(), p.rgb(), now, p.extra()));
				crater(p.a(), p.f());
			}
			case SaiyanKitFxPayload.FLASH -> {
				FLASHES.add(new Timed(p.a(), Vec3.ZERO, p.f(), p.rgb(), now, 0));
				ClientLevel level = Minecraft.getInstance().level;
				if (level != null) {
					level.addParticle(ParticleTypes.FLASH, p.a().x, p.a().y, p.a().z, 0, 0, 0);
					particles(ParticleTypes.END_ROD, p.a(), 24, 0.35);
				}
			}
			case SaiyanKitFxPayload.RAIN_MARK -> MARKS.add(new Timed(p.a(), Vec3.ZERO, p.f(), p.rgb(), now, p.extra()));
			default -> {
			}
		}
	}

	private static DustParticleOptions dust(int rgb, float size) {
		return new DustParticleOptions(new Vector3f(r(rgb), g(rgb), b(rgb)), size);
	}

	/** Ki sparks thrown out from a point. */
	private static void sparks(Vec3 at, int rgb, int count, double speed) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}
		DustParticleOptions dust = dust(rgb, 1.1f);
		for (int i = 0; i < count; i++) {
			double vx = (Math.random() - 0.5) * 2 * speed, vy = (Math.random() - 0.3) * 2 * speed, vz = (Math.random() - 0.5) * 2 * speed;
			level.addParticle(i % 3 == 0 ? ParticleTypes.ELECTRIC_SPARK : dust, at.x, at.y, at.z, vx, vy, vz);
		}
	}

	private static void particles(net.minecraft.core.particles.ParticleOptions type, Vec3 at, int count, double speed) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}
		for (int i = 0; i < count; i++) {
			level.addParticle(type, at.x, at.y, at.z, (Math.random() - 0.5) * 2 * speed, (Math.random() - 0.2) * 2 * speed, (Math.random() - 0.5) * 2 * speed);
		}
	}

	/** A ring of dust and Ki rushing out along the ground. */
	private static void groundBlast(Vec3 at, int rgb, float radius, int count) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}
		DustParticleOptions dust = dust(rgb, 1.6f);
		for (int i = 0; i < count; i++) {
			double a = i * Math.PI * 2 / count + Math.random() * 0.2;
			double sp = radius * 0.11 * (0.8 + Math.random() * 0.4);
			double cx = Math.cos(a), cz = Math.sin(a);
			level.addParticle(i % 2 == 0 ? ParticleTypes.CLOUD : dust, at.x + cx * 0.6, at.y + 0.15, at.z + cz * 0.6, cx * sp, 0.02 + Math.random() * 0.05,
					cz * sp);
		}
	}

	/** A slam's crater: chips of the floor block thrown up, dust rolling out. Nothing is broken. */
	private static void crater(Vec3 at, float radius) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}
		BlockPos below = BlockPos.containing(at.x, at.y - 0.2, at.z);
		BlockState st = level.getBlockState(below);
		if (!st.isAir()) {
			BlockParticleOption chips = new BlockParticleOption(ParticleTypes.BLOCK, st);
			for (int i = 0; i < 46; i++) {
				double a = Math.random() * Math.PI * 2, d = Math.random() * radius * 0.7;
				level.addParticle(chips, at.x + Math.cos(a) * d, at.y + 0.1, at.z + Math.sin(a) * d, Math.cos(a) * 0.25, 0.3 + Math.random() * 0.45,
						Math.sin(a) * 0.25);
			}
		}
		for (int i = 0; i < 18; i++) {
			double a = i * Math.PI * 2 / 18;
			level.addParticle(ParticleTypes.CLOUD, at.x + Math.cos(a) * 0.5, at.y + 0.2, at.z + Math.sin(a) * 0.5, Math.cos(a) * 0.22, 0.03,
					Math.sin(a) * 0.22);
		}
		level.addParticle(ParticleTypes.EXPLOSION, at.x, at.y + 0.4, at.z, 0, 0, 0);
	}

	// ---------------------------------------------------------------- the tick

	private static void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		if (level == null) {
			return;
		}
		long now = level.getGameTime();
		ANIMS.entrySet().removeIf(e -> now - e.getValue()[1] > 200 || now < e.getValue()[1]);
		BURSTS.removeIf(t -> now - t.start() > 8);
		IMPACTS.removeIf(t -> now - t.start() > 8);
		DOMES.removeIf(t -> now - t.start() > 18);
		CRATERS.removeIf(t -> now - t.start() > 20);
		FLASHES.removeIf(t -> now - t.start() > 14);
		MARKS.removeIf(t -> now - t.start() > t.extra());
		GHOSTS.removeIf(g -> now - g.start() > 14);
		SHOTS.entrySet().removeIf(e -> now - e.getValue().start() > 60);
		BEAMS.entrySet().removeIf(e -> now - e.getValue().start() > 300);
		for (Iterator<Map.Entry<Integer, Trail>> it = TRAILS.entrySet().iterator(); it.hasNext();) {
			Map.Entry<Integer, Trail> e = it.next();
			Trail t = e.getValue();
			Entity ent = level.getEntity(e.getKey());
			if (now <= t.until && ent != null) {
				t.points.addLast(ent.position().add(0, ent.getBbHeight() * 0.55, 0));
			}
			if (t.points.size() > 10 || (now > t.until && !t.points.isEmpty())) {
				t.points.pollFirst();
			}
			if (now > t.until && t.points.isEmpty()) {
				it.remove();
			}
		}
		// sparks drawn into a charging Kamehameha (not in his own first-person view: blobs that close to the eye)
		for (Map.Entry<Integer, BeamState> e : BEAMS.entrySet()) {
			if (!(level.getEntity(e.getKey()) instanceof Player p) || e.getValue().fired() >= 0) {
				continue;
			}
			if (p == mc.player && mc.options.getCameraType().isFirstPerson()) {
				continue;
			}
			Vec3 at = chargeOrb(p, 1f);
			for (int i = 0; i < 2; i++) {
				Vec3 from = at.add((Math.random() - 0.5) * 1.8, (Math.random() - 0.5) * 1.8, (Math.random() - 0.5) * 1.8);
				Vec3 v = at.subtract(from).scale(0.18);
				level.addParticle(ParticleTypes.ELECTRIC_SPARK, from.x, from.y, from.z, v.x, v.y, v.z);
			}
		}
	}

	// ---------------------------------------------------------------- the pose's view

	/** The kit pose running on this player and its age in ticks, or null. */
	static float[] anim(Player p, float partial) {
		long[] a = ANIMS.get(p.getId());
		if (a == null) {
			return null;
		}
		return new float[] { a[0], p.level().getGameTime() - a[1] + partial };
	}

	static boolean beamFiring(Player p) {
		BeamState s = BEAMS.get(p.getId());
		return s != null && s.fired() >= 0;
	}

	static boolean beamCharging(Player p) {
		BeamState s = BEAMS.get(p.getId());
		return s != null && s.fired() < 0;
	}

	static float beamAge(Player p, float partial) {
		BeamState s = BEAMS.get(p.getId());
		return s == null ? 0f : p.level().getGameTime() - s.start() + partial;
	}

	static float beamCharge(Player p, float partial) {
		BeamState s = BEAMS.get(p.getId());
		return s == null ? 0f : Mth.clamp(beamAge(p, partial) / s.maxCharge(), 0f, 1f);
	}

	private static float side(Player p) {
		return p.getMainArm() == HumanoidArm.RIGHT ? 1f : -1f;
	}

	/** The Kamehameha's gathering ball: between the cupped hands at his main-hand hip (his own view: low at the screen's edge). */
	static Vec3 chargeOrb(Player p, float partial) {
		Minecraft mc = Minecraft.getInstance();
		if (p == mc.player && mc.options.getCameraType().isFirstPerson()) {
			float yaw = (float) Math.toRadians(p.getViewYRot(partial));
			Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
			Vec3 flat = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
			return p.getEyePosition(partial).add(flat.scale(0.95)).add(right.scale(0.36 * side(p))).add(0, -0.5, 0);
		}
		float by = (float) Math.toRadians(Mth.rotLerp(partial, p.yBodyRotO, p.yBodyRot));
		Vec3 right = new Vec3(-Math.cos(by), 0, -Math.sin(by));
		Vec3 fwd = new Vec3(-Math.sin(by), 0, Math.cos(by));
		return p.getPosition(partial).add(0, 0.9, 0).add(right.scale(0.48 * side(p))).add(fwd.scale(-0.1));
	}

	// ---------------------------------------------------------------- drawing

	private static void render(WorldRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || ctx.consumers() == null || ctx.matrixStack() == null) {
			return;
		}
		float partial = ctx.tickCounter().getGameTimeDeltaPartialTick(false);
		float t = mc.level.getGameTime() + partial;
		Vec3 cam = ctx.camera().getPosition();
		PoseStack pose = ctx.matrixStack();
		SaiyanKitDraw.buffers = ctx.consumers();

		for (Map.Entry<Integer, BeamState> e : BEAMS.entrySet()) {
			if (mc.level.getEntity(e.getKey()) instanceof Player p && !BeamClashClient.hides(p)) {
				drawBeam(pose, cam, p, e.getValue(), partial, t);
			}
		}
		for (Shot s : SHOTS.values()) {
			float age = t - s.start();
			Vec3 at = s.from().add(s.vel().scale(age));
			float flick = 0.9f + 0.1f * Mth.sin(t * 4.1f + s.start());
			SaiyanKitDraw.orb(pose, cam, at, s.size() * flick, s.rgb(), 1f);
			Vec3 tail = at.subtract(s.vel().scale(Math.min(age, 1.2f)));
			SaiyanKitDraw.line(pose, cam, G(), tail, at, s.size() * 0.55f, 8, r(s.rgb()), g(s.rgb()), b(s.rgb()), 0.35f);
		}
		for (Timed x : BURSTS) {
			float age = t - x.start();
			float k = Mth.clamp(age / 8f, 0f, 1f);
			float rad = x.size() * (0.3f + 0.9f * k);
			SaiyanKitDraw.sphere(pose, cam, G(), x.pos(), rad * 0.5f, 1f, 1f, 1f, (1f - k) * 0.7f, 10);
			SaiyanKitDraw.sphere(pose, cam, G(), x.pos(), rad, r(x.rgb()), g(x.rgb()), b(x.rgb()), (1f - k) * 0.4f, 12);
		}
		for (Timed i : IMPACTS) {
			drawImpact(pose, cam, i, t);
		}
		for (Timed d : DOMES) {
			float age = t - d.start();
			float grow = Mth.clamp(age / 6f, 0f, 1f);
			float ease = 1f - (1f - grow) * (1f - grow);
			float fade = Mth.clamp(1f - (age - 4f) / 14f, 0f, 1f);
			float rad = d.size() * ease;
			int c = d.rgb();
			SaiyanKitDraw.sphere(pose, cam, G(), d.pos(), rad, r(c), g(c), b(c), 0.22f * fade, 24);
			SaiyanKitDraw.sphere(pose, cam, G(), d.pos(), rad * 0.92f, 1f, 1f, 1f, 0.1f * fade, 24);
			if (age < 4f) {
				SaiyanKitDraw.sphere(pose, cam, S(), d.pos(), 0.6f * (1f - age / 4f), 1f, 1f, 1f, 1f, 10);
			}
			Vec3 ground = d.pos().add(0, -0.85, 0);
			SaiyanKitDraw.ring(pose, cam, G(), ground, rad * 1.1f, 0.7f, r(c), g(c), b(c), 0.6f * fade);
			SaiyanKitDraw.ring(pose, cam, G(), ground, rad * 0.75f, 0.3f, 1f, 1f, 1f, 0.4f * fade);
		}
		for (Timed c : CRATERS) {
			float age = t - c.start();
			float k = Mth.clamp(age / 10f, 0f, 1f);
			float a = 1f - Mth.clamp(age / 20f, 0f, 1f);
			Vec3 at = c.pos().add(0, 0.06, 0);
			int col = c.rgb();
			SaiyanKitDraw.ring(pose, cam, G(), at, c.size() * (0.3f + 0.9f * k), 0.45f, r(col), g(col), b(col), 0.65f * a);
			SaiyanKitDraw.ring(pose, cam, G(), at, c.size() * (0.2f + 0.6f * k), 0.2f, 1f, 1f, 1f, 0.5f * a);
			if (age < 5f) {
				SaiyanKitDraw.sphere(pose, cam, G(), at.add(0, 0.3, 0), c.size() * 0.35f * (1f - age / 5f), 1f, 1f, 1f, 0.6f, 12);
			}
		}
		for (Timed f : FLASHES) {
			float age = t - f.start();
			float rad = age < 3f ? f.size() * 0.45f * (age / 3f) : f.size() * 0.45f;
			float a = age < 3f ? 1f : Mth.clamp(1f - (age - 3f) / 11f, 0f, 1f);
			SaiyanKitDraw.sphere(pose, cam, G(), f.pos(), 0.5f + age * 0.1f, 1f, 1f, 1f, 0.9f * a, 12);
			SaiyanKitDraw.sphere(pose, cam, G(), f.pos(), rad * 0.4f, 1f, 1f, 0.92f, 0.45f * a, 16);
			SaiyanKitDraw.sphere(pose, cam, G(), f.pos(), rad, 1f, 1f, 0.85f, 0.14f * a, 20);
		}
		for (Timed m : MARKS) {
			float age = t - m.start();
			float in = Mth.clamp(age / 5f, 0f, 1f);
			float out = Mth.clamp((m.extra() - age) / 6f, 0f, 1f);
			float pulse = 0.85f + 0.15f * Mth.sin(t * 0.8f);
			int c = m.rgb();
			Vec3 at = m.pos().add(0, 0.06, 0);
			SaiyanKitDraw.ring(pose, cam, G(), at, m.size() * in, 0.25f, r(c), g(c), b(c), 0.6f * out * pulse);
			SaiyanKitDraw.ring(pose, cam, G(), at, m.size() * 0.55f * in, 0.12f, 1f, 1f, 1f, 0.35f * out);
		}
		for (Ghost gh : GHOSTS) {
			drawGhost(pose, cam, gh, t);
		}
		for (Trail tr : TRAILS.values()) {
			drawTrail(pose, cam, tr);
		}
	}

	/** Where a client-side beam stops: the first block on the look line (it passes through creatures, as the server's). */
	private static Vec3[] beamSeg(Player p, float partial, double range) {
		Vec3 look = p.getViewVector(partial);
		Vec3 from = p.getPosition(partial).add(0, p.getEyeHeight() - 0.45, 0).add(look.scale(0.8));
		Vec3 far = from.add(look.scale(range));
		BlockHitResult bhr = p.level().clip(new ClipContext(from, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		Vec3 end = bhr.getType() == HitResult.Type.MISS ? far : bhr.getLocation();
		return new Vec3[] { from, end };
	}

	private static void drawBeam(PoseStack pose, Vec3 cam, Player p, BeamState s, float partial, float t) {
		int rgb = s.rgb();
		Minecraft mc = Minecraft.getInstance();
		boolean ownFp = p == mc.player && mc.options.getCameraType().isFirstPerson();
		if (s.fired() < 0) {
			float charge = Mth.clamp((t - s.start()) / s.maxCharge(), 0f, 1f);
			float flick = 0.85f + 0.15f * Mth.sin(t * 2.3f);
			float rad = (0.12f + 0.3f * charge) * flick * (ownFp ? 0.5f : 1f);
			Vec3 at = chargeOrb(p, partial);
			SaiyanKitDraw.orb(pose, cam, at, rad, rgb, 1f);
			if (!ownFp) {
				// little arcs crawling round the ball
				pose.pushPose();
				pose.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
				Matrix4f m = pose.last().pose();
				for (int k = 0; k < 3; k++) {
					float ph = t * 0.7f + k * 2.1f;
					Vector3f dir = new Vector3f(Mth.cos(ph), Mth.sin(ph * 1.3f), Mth.sin(ph)).normalize();
					Vector3f aa = SaiyanKitDraw.perp(dir), bb = new Vector3f(dir).cross(aa).normalize();
					SaiyanKitDraw.helix(G(), m, dir, aa, bb, rad * 1.6f, rad * 0.5f, ph, 0.02f, 0.85f, 0.95f, 1f, 0.6f * charge);
				}
				pose.popPose();
			}
			return;
		}
		Vec3[] seg = beamSeg(p, partial, 40.0);
		if (ownFp) {
			// his own beam: a little further out and lower so it doesn't fill the view
			seg[0] = seg[0].add(p.getViewVector(partial).scale(1.0)).add(0, -0.25, 0);
		}
		Vec3 d = seg[1].subtract(seg[0]);
		float len = (float) d.length();
		if (len < 0.05f) {
			return;
		}
		float age = t - s.fired();
		float grow = Mth.clamp(age / 3f, 0f, 1f);
		float fade = Mth.clamp((s.fireTicks() - age) / 5f, 0f, 1f);
		float width = (0.8f + 0.6f * s.charge()) * fade * (ownFp ? 0.5f : 1f);
		float pulse = 1f + 0.08f * Mth.sin(t * 3.1f);
		float reach = len * grow;
		pose.pushPose();
		pose.translate(seg[0].x - cam.x, seg[0].y - cam.y, seg[0].z - cam.z);
		Matrix4f m = pose.last().pose();
		Vector3f dir = new Vector3f((float) d.x, (float) d.y, (float) d.z).normalize();
		Vector3f a = SaiyanKitDraw.perp(dir);
		Vector3f bb = new Vector3f(dir).cross(a).normalize();
		SaiyanKitDraw.tube(S(), m, dir, a, bb, reach, 0.17f * width * pulse, 12, 0.95f, 0.98f, 1f, 1f);
		SaiyanKitDraw.tube(G(), m, dir, a, bb, reach, 0.36f * width * pulse, 14, r(rgb), g(rgb), b(rgb), 0.75f);
		SaiyanKitDraw.tube(G(), m, dir, a, bb, reach, 0.66f * width, 16, r(rgb), g(rgb), b(rgb), 0.24f);
		for (int k = 0; k < 3; k++) {
			SaiyanKitDraw.helix(G(), m, dir, a, bb, reach, 0.46f * width, t * 0.9f + k * Mth.TWO_PI / 3f, 0.06f, 0.9f, 0.96f, 1f, 0.5f);
		}
		pose.popPose();
		float boil = 0.8f + 0.2f * Mth.sin(t * 4.7f);
		Vec3 end = seg[0].add(d.scale(grow));
		SaiyanKitDraw.sphere(pose, cam, S(), end, 0.5f * width * boil, 1f, 1f, 1f, 1f, 10);
		SaiyanKitDraw.sphere(pose, cam, G(), end, 1.25f * width * boil, r(rgb), g(rgb), b(rgb), 0.45f, 12);
		if (!ownFp) {
			SaiyanKitDraw.sphere(pose, cam, G(), seg[0], 0.55f * width, r(rgb), g(rgb), b(rgb), 0.6f, 10);
			SaiyanKitDraw.sphere(pose, cam, S(), seg[0], 0.22f * width, 1f, 1f, 1f, 1f, 8);
		}
		if (Math.random() < 0.6) {
			mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK, end.x, end.y, end.z, (Math.random() - 0.5) * 0.6, Math.random() * 0.4,
					(Math.random() - 0.5) * 0.6);
		}
	}

	private static void drawImpact(PoseStack pose, Vec3 cam, Timed i, float t) {
		float age = t - i.start();
		float k = Mth.clamp(age / 8f, 0f, 1f);
		float s = i.size() * (0.4f + 0.9f * k);
		float a = (1f - k) * 0.8f;
		int rgb = i.rgb();
		SaiyanKitDraw.sphere(pose, cam, G(), i.pos(), s * 0.35f, 1f, 1f, 1f, a, 8);
		SaiyanKitDraw.sphere(pose, cam, G(), i.pos(), s * 0.6f, r(rgb), g(rgb), b(rgb), a * 0.6f, 10);
		java.util.Random rnd = new java.util.Random((long) (i.pos().x * 31 + i.pos().y * 17 + i.pos().z * 7));
		for (int n = 0; n < 8; n++) {
			Vec3 dir = new Vec3(rnd.nextFloat() - 0.5f, rnd.nextFloat() - 0.4f, rnd.nextFloat() - 0.5f).normalize();
			Vec3 a0 = i.pos().add(dir.scale(s * 0.3f));
			SaiyanKitDraw.line(pose, cam, G(), a0, a0.add(dir.scale(s * (0.6f + rnd.nextFloat() * 0.6f))), 0.03f * i.size(), 4, 1f, 1f, 1f, a);
		}
	}

	private static void drawGhost(PoseStack pose, Vec3 cam, Ghost gh, float t) {
		float age = t - gh.start();
		float a = Mth.clamp(1f - age / 14f, 0f, 1f) * 0.5f;
		int rgb = gh.rgb();
		pose.pushPose();
		pose.translate(gh.from().x - cam.x, gh.from().y - cam.y, gh.from().z - cam.z);
		pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-gh.yaw()));
		float k = 1f + age * 0.03f;
		Matrix4f m = pose.last().pose();
		SaiyanKitDraw.box(G(), m, -0.25f * k, 1.5f, -0.25f * k, 0.25f * k, 2.0f, 0.25f * k, r(rgb), g(rgb), b(rgb), a);
		SaiyanKitDraw.box(G(), m, -0.25f * k, 0.75f, -0.13f * k, 0.25f * k, 1.5f, 0.13f * k, r(rgb), g(rgb), b(rgb), a);
		SaiyanKitDraw.box(G(), m, -0.5f * k, 0.8f, -0.12f * k, -0.26f * k, 1.5f, 0.12f * k, r(rgb), g(rgb), b(rgb), a);
		SaiyanKitDraw.box(G(), m, 0.26f * k, 0.8f, -0.12f * k, 0.5f * k, 1.5f, 0.12f * k, r(rgb), g(rgb), b(rgb), a);
		SaiyanKitDraw.box(G(), m, -0.25f * k, 0f, -0.12f * k, -0.01f, 0.75f, 0.12f * k, r(rgb), g(rgb), b(rgb), a);
		SaiyanKitDraw.box(G(), m, 0.01f, 0f, -0.12f * k, 0.25f * k, 0.75f, 0.12f * k, r(rgb), g(rgb), b(rgb), a);
		pose.popPose();
		if (age < 6f) {
			SaiyanKitDraw.line(pose, cam, G(), gh.from().add(0, 1, 0), gh.to().add(0, 1, 0), 0.06f, 6, 1f, 1f, 1f, (1f - age / 6f) * 0.7f);
		}
	}

	private static void drawTrail(PoseStack pose, Vec3 cam, Trail tr) {
		Vec3 prev = null;
		int i = 0, n = tr.points.size();
		for (Vec3 pt : tr.points) {
			if (prev != null) {
				float a = (i / (float) n) * 0.6f;
				SaiyanKitDraw.line(pose, cam, G(), prev, pt, 0.06f + 0.16f * i / n, 8, r(tr.rgb), g(tr.rgb), b(tr.rgb), a * 0.6f);
			}
			prev = pt;
			i++;
		}
	}

	/** The Kamehameha's roar: loops on the shooter while the beam fires, fading in and out (quiet). */
	private static final class BeamLoop extends AbstractTickableSoundInstance {
		private final Entity shooter;
		private int fade;

		BeamLoop(Entity shooter, float pitch) {
			super(SaiyanKitSounds.BEAM_LOOP, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
			this.shooter = shooter;
			this.looping = true;
			this.delay = 0;
			this.volume = 0.01f;
			this.pitch = pitch;
			this.x = shooter.getX();
			this.y = shooter.getY() + 1.2;
			this.z = shooter.getZ();
		}

		@Override
		public void tick() {
			BeamState s = BEAMS.get(shooter.getId());
			boolean live = shooter.isAlive() && s != null && s.fired() >= 0;
			fade = live ? Math.min(4, fade + 1) : fade - 1;
			if (fade < 0) {
				stop();
				return;
			}
			this.volume = 0.6f * fade / 4f;
			this.x = shooter.getX();
			this.y = shooter.getY() + 1.2;
			this.z = shooter.getZ();
		}
	}

}
