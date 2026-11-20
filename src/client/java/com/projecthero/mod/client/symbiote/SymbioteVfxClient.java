package com.projecthero.mod.client.symbiote;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.network.SymbioteVfxPayload;
import com.projecthero.mod.symbiote.Symbiote;
import com.projecthero.mod.symbiote.SymbioteAnim;
import com.projecthero.mod.symbiote.SymbioteParticles;
import com.projecthero.mod.symbiote.SymbioteState;
import com.projecthero.mod.symbiote.SymbioteVfx;
import com.projecthero.mod.symbiote.SymbioteVitals;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.5 tendril-vfx round (user: "instead of spawning in particles for the symbiote powers can you make it the tentacle models and
 * vfx please ... when player uses symbiote lunge ... symbiote tendril come off the players legs and push off the ground launching
 * them"): the client side of {@link SymbioteVfx}. Every effect is living tendrils -- tapered, writhing {@link GooMesh} tubes in the
 * host's suit colours ({@link SymbioteSuitTint#goo}, the same texture and tint as {@code SymbioteTendrilRenderer}) -- drawn in world
 * space after the entities, re-anchored every frame on the host's legs / hands / body (and on a held target), so they stay attached
 * while everyone moves. A couple of goo droplets ({@link SymbioteParticles#GOO}, the body colour) are the only particles left, as
 * accents.
 *
 * <p>Two sources:
 * <ul>
 *   <li><b>one-shot effects</b> from {@link SymbioteVfxPayload} (impacts, ground bursts, the Lunge's leg push, wraps, body bursts, Maw
 *       Bite's jaws, the blade slash, wall grips, Spike Rain's circle), kept in {@link #LIVE} until they finish;</li>
 *   <li><b>held states</b> read from the synced attachments of every player in view: the Symbiote Shield (a woven tendril lattice
 *       in front of the host, grown from both forearms), the Impaling Spear's charge (tendrils spiral down the arm into a growing
 *       spear), the Dive Bomb (tendrils stream off the back, claws reach down from the feet) and Onslaught's wind-up (tendrils from
 *       the legs anchor in the ground while a closing ring of claws rises round the host).</li>
 * </ul>
 * Client-only statics (effects keep entity ids, never entity references); cleared on leaving a world.
 */
public final class SymbioteVfxClient {
	private static final ResourceLocation TEXTURE = ProjectHeroMod.id("textures/entity/symbiote_tendril.png");
	private static final int MAX_LIVE = 256;
	private static final double VIEW = 64.0;

	private static final class Fx {
		final int kind, owner, target, duration, seed;
		final Vec3 pos, dir;
		final float size, born;
		Vec3 side;

		Fx(SymbioteVfxPayload p, float born) {
			this.kind = p.kind();
			this.owner = p.owner();
			this.target = p.target();
			this.duration = p.duration();
			this.pos = new Vec3(p.x(), p.y(), p.z());
			this.dir = new Vec3(p.dx(), p.dy(), p.dz());
			this.size = p.size();
			this.born = born;
			this.seed = (int) (born * 977) ^ (p.owner() * 31) ^ Float.floatToIntBits(p.x() + p.z());
		}
	}

	private static final List<Fx> LIVE = new ArrayList<>();

	private SymbioteVfxClient() {
	}

	public static void initialize() {
		ClientPlayNetworking.registerGlobalReceiver(SymbioteVfxPayload.TYPE, (payload, context) -> context.client().execute(() -> add(payload)));
		WorldRenderEvents.AFTER_ENTITIES.register(SymbioteVfxClient::render);
		ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> c.execute(LIVE::clear));
	}

	private static float now(float partial) {
		Minecraft mc = Minecraft.getInstance();
		return mc.level == null ? 0f : mc.level.getGameTime() + partial;
	}

	/** Also the client screenshot harness's way in (no server needed). */
	public static void add(SymbioteVfxPayload p) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return;
		}
		if (LIVE.size() >= MAX_LIVE) {
			LIVE.remove(0);
		}
		Fx fx = new Fx(p, now(0f));
		LIVE.add(fx);
		accents(mc.level, fx);
	}

	// ------------------------------------------------------------------ render

	private static void render(WorldRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		Level level = mc.level;
		if (level == null || ctx.consumers() == null || ctx.matrixStack() == null) {
			return;
		}
		float partial = mc.getTimer().getGameTimeDeltaPartialTick(false);
		float t = now(partial);
		Vec3 cam = ctx.camera().getPosition();
		PoseStack pose = ctx.matrixStack();
		MultiBufferSource buffers = ctx.consumers();
		pose.pushPose();
		Draw d = new Draw(pose, buffers, level, cam, t, partial);
		Iterator<Fx> it = LIVE.iterator();
		while (it.hasNext()) {
			Fx fx = it.next();
			float age = t - fx.born;
			if (age > fx.duration + 1 || age < -2) {
				it.remove();
				continue;
			}
			if (fx.pos.distanceToSqr(cam) > VIEW * VIEW * 4) {
				continue;
			}
			Entity owner = level.getEntity(fx.owner);
			if (owner != null && owner.isInvisible() && mc.player != null && owner.isInvisibleTo(mc.player)) {
				continue;
			}
			d.use(owner);
			switch (fx.kind) {
				case SymbioteVfx.IMPACT -> impact(d, fx, age);
				case SymbioteVfx.GROUND_BURST -> groundBurst(d, fx, age);
				case SymbioteVfx.LEG_PUSH -> legPush(d, fx, age);
				case SymbioteVfx.WRAP -> wrap(d, fx, age);
				case SymbioteVfx.BODY_BURST -> bodyBurst(d, fx, age);
				case SymbioteVfx.JAWS -> jaws(d, fx, age);
				case SymbioteVfx.SLASH -> slash(d, fx, age);
				case SymbioteVfx.WALL_GRIP -> wallGrip(d, fx, age);
				case SymbioteVfx.GROUND_MARK -> groundMark(d, fx, age);
				default -> {
				}
			}
		}
		for (Player p : level.players()) {
			if (p.distanceToSqr(cam) > VIEW * VIEW || !Symbiote.hasSymbiote(p) || !Symbiote.isNormalHost(p)) {
				continue;
			}
			if (p.isInvisible() && mc.player != null && p.isInvisibleTo(mc.player)) {
				continue;
			}
			d.use(p);
			held(d, p, t);
		}
		pose.popPose();
	}

	/** One frame's drawing context: the buffer for the current owner's colours, the camera offset, the time. */
	static final class Draw {
		final PoseStack pose;
		final MultiBufferSource buffers;
		final Level level;
		final Vec3 cam;
		final float time, partial;
		VertexConsumer vc;

		Draw(PoseStack pose, MultiBufferSource buffers, Level level, Vec3 cam, float time, float partial) {
			this.pose = pose;
			this.buffers = buffers;
			this.level = level;
			this.cam = cam;
			this.time = time;
			this.partial = partial;
		}

		void use(Entity owner) {
			vc = buffers.getBuffer(RenderType.entityCutoutNoCull(SymbioteSuitTint.goo(owner, TEXTURE)));
		}

		int light(Vec3 at) {
			return LevelRenderer.getLightColor(level, BlockPos.containing(at));
		}

		/** A strand through world points, radius {@code width} at the root tapering to a point. */
		void strand(Vec3[] pts, float width, float tipFrac) {
			if (pts.length < 2) {
				return;
			}
			Vec3[] local = new Vec3[pts.length];
			float[] radii = new float[pts.length];
			for (int i = 0; i < pts.length; i++) {
				local[i] = pts[i].subtract(cam);
				double along = (double) i / (pts.length - 1);
				radii[i] = (float) (width * (1.0 - (1.0 - tipFrac) * along));
			}
			radii[pts.length - 1] *= 0.2f;
			GooMesh.tube(vc, pose.last(), light(pts[pts.length / 2]), local, radii, 6, 0.35f);
		}

		/** A strand of constant-ish width that tapers at both ends (blade crescents, rims). */
		void band(Vec3[] pts, float width) {
			if (pts.length < 2) {
				return;
			}
			Vec3[] local = new Vec3[pts.length];
			float[] radii = new float[pts.length];
			for (int i = 0; i < pts.length; i++) {
				local[i] = pts[i].subtract(cam);
				double s = Math.sin(Math.PI * i / (pts.length - 1));
				radii[i] = (float) (width * (0.25 + 0.75 * s));
			}
			GooMesh.tube(vc, pose.last(), light(pts[pts.length / 2]), local, radii, 6, 0.2f);
		}

		/**
		 * The living tendril: {@code a} -> {@code b}, drawn out to {@code reach} (0..1) of the way, bowed by {@code bow} blocks toward
		 * {@code bowDir} (or the natural up), writhing, a barbed tip.
		 */
		void tendril(Vec3 a, Vec3 b, float reach, float width, int seed, double bow, Vec3 bowDir, boolean barb) {
			if (reach <= 0.02f) {
				return;
			}
			Vec3 span = b.subtract(a);
			double len = span.length();
			if (len < 0.03) {
				return;
			}
			Vec3 dir = span.scale(1.0 / len);
			Vec3 side = dir.cross(new Vec3(0, 1, 0));
			side = side.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : side.normalize();
			Vec3 up = bowDir != null ? bowDir : side.cross(dir).normalize();
			int segs = Mth.clamp((int) (len * 6), 6, 28);
			int count = Math.max(2, Math.round(segs * reach) + 1);
			Vec3[] pts = new Vec3[count];
			float w = Math.min(1f, (float) len * 0.5f);
			for (int i = 0; i < count; i++) {
				double s = (double) i / segs;
				double arc = Math.sin(Math.PI * s);
				double wig = Math.sin(s * Math.PI * 3.0 - time * 0.6 + seed) * 0.08 * arc * w;
				double wig2 = Math.cos(s * Math.PI * 2.0 - time * 0.8 + seed * 0.7) * 0.06 * arc * w;
				pts[i] = a.add(span.scale(s)).add(up.scale(bow * arc + wig2)).add(side.scale(wig));
			}
			strand(pts, width, 0.3f);
			if (barb && count > 2) {
				Vec3 tip = pts[count - 1];
				Vec3 back = pts[count - 2].subtract(tip).normalize();
				for (int k = 0; k < 3; k++) {
					double ang = Math.PI * 2 * k / 3.0 + seed;
					Vec3 out = side.scale(Math.cos(ang)).add(up.scale(Math.sin(ang)));
					strand(new Vec3[] { tip.add(back.scale(width * 0.5)), tip.add(back.scale(width * 1.8)).add(out.scale(width * 1.4)) },
							width * 0.4f, 0.1f);
				}
			}
		}
	}

	// ------------------------------------------------------------------ anchors on a host (client twins of SymbioteHands)

	static Vec3 fwd(Player p, float partial) {
		float yaw = Mth.lerp(partial, p.yBodyRotO, p.yBodyRot) * Mth.DEG_TO_RAD;
		return new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
	}

	static Vec3 right(Player p, float partial) {
		Vec3 f = fwd(p, partial);
		return new Vec3(-f.z, 0, f.x);
	}

	/** A point on the host's body: {@code up} blocks above the feet, {@code side} to the right, {@code forward} ahead (all scaled). */
	static Vec3 body(Player p, float partial, double up, double side, double forward) {
		double s = p.getScale();
		return p.getPosition(partial).add(0, up * s, 0).add(right(p, partial).scale(side * s)).add(fwd(p, partial).scale(forward * s));
	}

	static Vec3 hand(Player p, boolean rightHand, float partial) {
		return SymbioteTendrilRenderer.handOf(p, rightHand, partial);
	}

	private static float ease(float x) {
		x = Mth.clamp(x, 0f, 1f);
		return 1f - (1f - x) * (1f - x);
	}

	/** 0 -> 1 over {@code in} ticks, held, 1 -> 0 over the last {@code out} ticks of {@code dur}. */
	private static float envelope(float age, float in, float dur, float out) {
		if (age < in) {
			return ease(age / in);
		}
		if (age > dur - out) {
			return Mth.clamp((dur - age) / out, 0f, 1f);
		}
		return 1f;
	}

	private static double rnd(int seed, int k) {
		int h = seed * 374761393 + k * 668265263;
		h = (h ^ (h >>> 13)) * 1274126177;
		return ((h ^ (h >>> 16)) >>> 1) / (double) Integer.MAX_VALUE;
	}

	private static Vec3 randomUnit(int seed, int k) {
		double z = rnd(seed, k) * 2 - 1;
		double a = rnd(seed, k + 101) * Math.PI * 2;
		double r = Math.sqrt(1 - z * z);
		return new Vec3(r * Math.cos(a), z, r * Math.sin(a));
	}

	// ------------------------------------------------------------------ one-shot effects

	/** Short tendrils splay out of the hit point (clinging to the struck creature while it moves) and snap back. */
	private static void impact(Draw d, Fx fx, float age) {
		Vec3 at = fx.pos;
		Entity target = fx.target >= 0 ? d.level.getEntity(fx.target) : null;
		if (target != null && target.isAlive()) {
			at = target.getPosition(d.partial).add(0, target.getBbHeight() * 0.5, 0);
		}
		Vec3 blow = fx.dir.lengthSqr() < 1.0e-6 ? Vec3.ZERO : fx.dir.normalize();
		float reach = envelope(age, 1.5f, fx.duration, 4f);
		int n = 4 + Math.round(fx.size * 3);
		for (int k = 0; k < n; k++) {
			Vec3 dir = randomUnit(fx.seed, k).add(blow.scale(0.9)).normalize();
			double len = (0.45 + rnd(fx.seed, k + 50) * 0.55) * fx.size;
			Vec3 from = at.subtract(dir.scale(0.12 * fx.size));
			d.tendril(from, at.add(dir.scale(len)), reach, 0.035f + 0.03f * fx.size, fx.seed + k, len * 0.25,
					randomUnit(fx.seed, k + 200), true);
		}
	}

	/** A ring of tendrils erupting from the ground and lashing outward, tips curling up. */
	private static void groundBurst(Draw d, Fx fx, float age) {
		float reach = envelope(age, 3.5f, fx.duration, 5f);
		int n = Mth.clamp(Math.round(fx.size * 3f), 8, 26);
		float w = Math.min(0.2f, 0.07f + 0.015f * fx.size);
		for (int k = 0; k < n; k++) {
			double a = Math.PI * 2 * k / n + rnd(fx.seed, k) * 0.4;
			Vec3 out = new Vec3(Math.cos(a), 0, Math.sin(a));
			Vec3 root = fx.pos.add(out.scale(Math.min(0.6, fx.size * 0.2)));
			double len = fx.size * (0.75 + rnd(fx.seed, k + 30) * 0.25);
			Vec3 tip = fx.pos.add(out.scale(len)).add(0, 0.3 + rnd(fx.seed, k + 60) * 0.7, 0);
			d.tendril(root, tip, reach, w, fx.seed + k, len * 0.22, new Vec3(0, 1, 0), true);
		}
	}

	/**
	 * Lunge: two tendrils out of each leg (calf and thigh) stab down and plant on the ground where the host stood, grip it with
	 * splayed toes and push -- they stay planted and stretch as the host flies off -- then let go and whip back into the legs.
	 */
	private static void legPush(Draw d, Fx fx, float age) {
		if (!(d.level.getEntity(fx.owner) instanceof Player p)) {
			return;
		}
		if (fx.side == null) {
			Vec3 flat = new Vec3(fx.dir.x, 0, fx.dir.z);
			flat = flat.lengthSqr() < 1.0e-4 ? fwd(p, d.partial) : flat.normalize();
			fx.side = new Vec3(-flat.z, 0, flat.x);
		}
		Vec3 back = new Vec3(fx.side.z, 0, -fx.side.x); // opposite the launch
		double s = p.getScale();
		float plant = ease(age / 1.5f);
		float release = age < 6f ? 0f : ease((age - 6f) / 4.5f);
		for (int side = -1; side <= 1; side += 2) {
			for (int k = 0; k < 2; k++) {
				Vec3 root = body(p, d.partial, k == 0 ? 0.32 : 0.62, side * 0.12, k == 0 ? -0.04 : 0.02);
				Vec3 ground = fx.pos.add(back.scale((0.22 + 0.28 * k) * s)).add(fx.side.scale(side * (0.16 + 0.14 * k) * s));
				Vec3 end = ground.add(root.subtract(ground).scale(release));
				float w = (float) ((k == 0 ? 0.085 : 0.07) * s);
				Vec3 bowDir = fx.side.scale(side).add(0, 0.3, 0).normalize();
				d.tendril(root, end, plant, w, fx.seed + side * 7 + k, 0.12 * s * (1 - release), bowDir, release > 0.05f);
				if (plant > 0.95f && release < 0.4f) {
					// the toes: three short claws splayed flat on the ground
					float grip = 1f - release / 0.4f;
					for (int toe = 0; toe < 3; toe++) {
						double a = Math.atan2(back.z, back.x) + (toe - 1) * 0.9 + side * 0.3;
						Vec3 toeEnd = ground.add(Math.cos(a) * 0.24 * s, 0.01, Math.sin(a) * 0.24 * s);
						d.tendril(ground.add(0, 0.03, 0), toeEnd, grip, w * 0.55f, fx.seed + toe * 13 + side, 0.04, new Vec3(0, 1, 0), false);
					}
				}
			}
		}
	}

	/** Tendrils coil round a creature's body, crawling slowly, wound on and off. */
	private static void wrap(Draw d, Fx fx, float age) {
		Entity target = fx.target >= 0 ? d.level.getEntity(fx.target) : null;
		if (target == null || !target.isAlive()) {
			return;
		}
		Vec3 base = target.getPosition(d.partial);
		double r = target.getBbWidth() * 0.62 + 0.05;
		double h = target.getBbHeight();
		float wound = envelope(age, 4f, fx.duration, 3f);
		int strands = 3;
		int pts = 30;
		for (int k = 0; k < strands; k++) {
			int shown = Math.max(2, Math.round(pts * wound));
			Vec3[] line = new Vec3[shown];
			double phase = Math.PI * 2 * k / strands + d.time * 0.12 + fx.seed;
			for (int i = 0; i < shown; i++) {
				double s = (double) i / (pts - 1);
				double a = phase + s * Math.PI * 2 * 2.2 * (k % 2 == 0 ? 1 : -1);
				double y = h * (0.08 + 0.82 * (k % 2 == 0 ? s : 1 - s));
				double rr = r * (1.0 + 0.08 * Math.sin(s * 12 + d.time * 0.5 + k));
				line[i] = base.add(Math.cos(a) * rr, y, Math.sin(a) * rr);
			}
			d.strand(line, 0.055f * fx.size, 0.5f);
		}
	}

	/** Tendrils burst out of the host's torso all round, flail, and are drawn back in. */
	private static void bodyBurst(Draw d, Fx fx, float age) {
		if (!(d.level.getEntity(fx.owner) instanceof Player p)) {
			return;
		}
		float reach = envelope(age, 3f, fx.duration, 6f);
		double s = p.getScale();
		int n = 6 + Math.round(fx.size * 4);
		for (int k = 0; k < n; k++) {
			double a = Math.PI * 2 * k / n + rnd(fx.seed, k) * 0.5;
			double up = 0.8 + rnd(fx.seed, k + 9) * 0.6;
			Vec3 root = body(p, d.partial, up, Math.cos(a) * 0.2, Math.sin(a) * 0.12);
			Vec3 out = new Vec3(Math.cos(a), 0.35 + rnd(fx.seed, k + 40) * 0.6, Math.sin(a)).normalize();
			// rotate the ring with the body
			Vec3 r = right(p, d.partial), f = fwd(p, d.partial);
			Vec3 o = r.scale(out.x).add(0, out.y, 0).add(f.scale(out.z));
			double len = (0.8 + rnd(fx.seed, k + 70) * 0.7) * fx.size * s;
			d.tendril(root, root.add(o.scale(len)), reach, (float) (0.06 * s), fx.seed + k, len * 0.3, null, true);
		}
	}

	/** Maw Bite: two great jaw-tendrils from the shoulders gape round the prey and snap shut on it, fangs inward. */
	private static void jaws(Draw d, Fx fx, float age) {
		if (!(d.level.getEntity(fx.owner) instanceof Player p)) {
			return;
		}
		Vec3 at = fx.pos;
		Entity target = fx.target >= 0 ? d.level.getEntity(fx.target) : null;
		if (target != null && target.isAlive()) {
			at = target.getPosition(d.partial).add(0, target.getBbHeight() * 0.55, 0);
		}
		double s = p.getScale() * fx.size;
		float reach = envelope(age, 2.5f, fx.duration, 3.5f);
		float shut = age < 2.5f ? 0f : ease((age - 2.5f) / 1.5f);
		Vec3 toward = at.subtract(p.getEyePosition(d.partial));
		Vec3 dir = toward.lengthSqr() < 1.0e-4 ? fwd(p, d.partial) : toward.normalize();
		Vec3 side = dir.cross(new Vec3(0, 1, 0));
		side = side.lengthSqr() < 1.0e-6 ? right(p, d.partial) : side.normalize();
		Vec3 up = side.cross(dir).normalize();
		for (int j = -1; j <= 1; j += 2) {
			Vec3 root = body(p, d.partial, 1.32, j * 0.3, 0.05);
			double gape = (1.0 - shut) * 0.75 * s;
			Vec3 tip = at.add(up.scale(j * (gape + 0.08 * s))).subtract(dir.scale(0.15 * s));
			Vec3 ctrl = root.add(tip).scale(0.5).add(up.scale(j * (0.5 * s + gape)));
			int n = 16;
			int shown = Math.max(2, Math.round(n * reach));
			Vec3[] pts = new Vec3[shown];
			for (int i = 0; i < shown; i++) {
				double t = (double) i / (n - 1);
				pts[i] = root.scale((1 - t) * (1 - t)).add(ctrl.scale(2 * t * (1 - t))).add(tip.scale(t * t));
			}
			d.strand(pts, (float) (0.12 * s), 0.35f);
			if (reach > 0.9f) {
				// fangs along the inside of the jaw, pointing at the other one
				for (int f = 0; f < 4; f++) {
					double t = 0.62 + f * 0.1;
					Vec3 on = root.scale((1 - t) * (1 - t)).add(ctrl.scale(2 * t * (1 - t))).add(tip.scale(t * t));
					d.strand(new Vec3[] { on, on.add(up.scale(-j * 0.2 * s)).add(dir.scale(0.04 * s)) }, (float) (0.035 * s), 0.05f);
				}
			}
		}
	}

	/** Blade Slash: a living blade-crescent sweeps right to left in front of the host. */
	private static void slash(Draw d, Fx fx, float age) {
		if (!(d.level.getEntity(fx.owner) instanceof Player p)) {
			return;
		}
		double s = p.getScale();
		Vec3 aim = new Vec3(fx.dir.x, 0, fx.dir.z);
		aim = aim.lengthSqr() < 1.0e-4 ? fwd(p, d.partial) : aim.normalize();
		Vec3 centre = body(p, d.partial, 1.05, 0, 0);
		double radius = Math.max(1.2, fx.size * 0.7) * Math.max(1.0, s);
		float sweep = ease(age / 4f);
		float fade = age < 4f ? 1f : Mth.clamp(1f - (age - 4f) / 3f, 0f, 1f);
		if (fade <= 0f) {
			return;
		}
		double a0 = Math.toRadians(70), a1 = Math.toRadians(-70);
		double head = a0 + (a1 - a0) * sweep;
		double tail = Math.min(a0, head + Math.toRadians(95) * fade);
		int n = 18;
		Vec3[] pts = new Vec3[n];
		double base = Math.atan2(aim.z, aim.x);
		for (int i = 0; i < n; i++) {
			double a = tail + (head - tail) * i / (n - 1);
			double ang = base + a;
			double tilt = 0.25 * (a / a0);
			pts[i] = centre.add(Math.cos(ang) * radius, tilt, Math.sin(ang) * radius);
		}
		d.band(pts, (float) (0.09 * s * fade));
	}

	/** Wall climb: tendrils from both hands grip the wall. */
	private static void wallGrip(Draw d, Fx fx, float age) {
		if (!(d.level.getEntity(fx.owner) instanceof Player p)) {
			return;
		}
		float reach = envelope(age, 2f, fx.duration, 3f);
		for (int j = 0; j < 2; j++) {
			Vec3 from = hand(p, j == 0, d.partial);
			Vec3 to = fx.pos.add(0, (j == 0 ? 0.3 : -0.2), 0).add(right(p, d.partial).scale(j == 0 ? 0.25 : -0.25));
			d.tendril(from, to, reach, 0.05f, fx.seed + j, 0.1, null, true);
		}
	}

	/** Spike Rain's circle: short tendrils writhe up all round its edge. */
	private static void groundMark(Draw d, Fx fx, float age) {
		float grow = envelope(age, 4f, fx.duration, 4f);
		int n = Mth.clamp(Math.round(fx.size * 3), 8, 16);
		for (int k = 0; k < n; k++) {
			double a = Math.PI * 2 * k / n + d.time * 0.02;
			Vec3 root = fx.pos.add(Math.cos(a) * fx.size, 0.02, Math.sin(a) * fx.size);
			double hgt = 0.5 + 0.35 * Math.sin(d.time * 0.3 + k * 1.7);
			Vec3 tip = root.add(-Math.cos(a) * 0.25, hgt, -Math.sin(a) * 0.25);
			d.tendril(root, tip, grow, 0.06f, fx.seed + k, 0.12, null, true);
		}
	}

	// ------------------------------------------------------------------ held states (from synced attachments)

	private static void held(Draw d, Player p, float t) {
		SymbioteState s = p.getAttachedOrElse(ModAttachments.SYMBIOTE_STATE, null);
		SymbioteVitals v = p.getAttachedOrElse(ModAttachments.SYMBIOTE_VITALS, null);
		if (s == null || v == null) {
			return;
		}
		long now = p.level().getGameTime();
		float age = now - v.animStart + d.partial;
		if (s.shieldHeld) {
			shield(d, p, t);
		}
		if (v.animId == SymbioteAnim.SPEAR_CHARGE && age < 70) {
			spear(d, p, Math.min(1f, age / 20f), t);
		}
		if (v.animId == SymbioteAnim.DIVE_BOMB && age < 80 && !p.onGround()) {
			dive(d, p, t, age);
		}
		if (s.onslaughtChargeStart >= 0) {
			onslaught(d, p, Math.min(1f, (now - s.onslaughtChargeStart + d.partial) / 60f), t);
		}
	}

	/** The Symbiote Shield: a woven disc of tendrils in front of the host, grown from both forearms. */
	private static void shield(Draw d, Player p, float t) {
		double sc = Math.max(1.0, p.getScale());
		Vec3 look = p.getViewVector(d.partial);
		Vec3 rgt = new Vec3(-look.z, 0, look.x);
		rgt = rgt.lengthSqr() < 1.0e-6 ? right(p, d.partial) : rgt.normalize();
		Vec3 upv = rgt.cross(look).normalize().scale(-1);
		if (upv.y < 0) {
			upv = upv.scale(-1);
		}
		Vec3 centre = p.getEyePosition(d.partial).add(look.scale(0.9 * sc)).add(0, -0.35 * sc, 0);
		int n = 26;
		for (int ring = 0; ring < 2; ring++) {
			double rr = ring == 0 ? 1.0 : 0.55;
			Vec3[] rim = new Vec3[n + 1];
			for (int i = 0; i <= n; i++) {
				double a = Math.PI * 2 * i / n;
				double wob = 1.0 + 0.06 * Math.sin(a * 5 + t * 0.4 + ring);
				rim[i] = centre.add(rgt.scale(Math.cos(a) * 0.55 * sc * rr * wob)).add(upv.scale(Math.sin(a) * 0.7 * sc * rr * wob))
						.add(look.scale(-0.12 * sc * (1 - rr)));
			}
			Vec3[] local = new Vec3[rim.length];
			float[] radii = new float[rim.length];
			for (int i = 0; i < rim.length; i++) {
				local[i] = rim[i].subtract(d.cam);
				radii[i] = (float) ((ring == 0 ? 0.06 : 0.04) * sc);
			}
			GooMesh.tube(d.vc, d.pose.last(), d.light(centre), local, radii, 6, 0.2f);
		}
		for (int k = 0; k < 8; k++) {
			double a = Math.PI * 2 * k / 8 + 0.2;
			Vec3 edge = centre.add(rgt.scale(Math.cos(a) * 0.55 * sc)).add(upv.scale(Math.sin(a) * 0.7 * sc));
			Vec3 mid = centre.add(look.scale(-0.12 * sc));
			d.tendril(mid, edge, 1f, (float) (0.035 * sc), k * 17 + p.getId(), 0.05 * sc, look.scale(-1), false);
		}
		for (int j = 0; j < 2; j++) {
			Vec3 hand = hand(p, j == 0, d.partial);
			double a = j == 0 ? -0.6 : Math.PI + 0.6;
			Vec3 edge = centre.add(rgt.scale(Math.cos(a) * 0.55 * sc)).add(upv.scale(Math.sin(a) * 0.7 * sc));
			d.tendril(hand, edge, 1f, (float) (0.05 * sc), 91 + j, 0.08 * sc, null, false);
		}
	}

	/** The Impaling Spear charging: tendrils spiral down the arm and knot into a spear that grows from the fist. */
	private static void spear(Draw d, Player p, float charge, float t) {
		double sc = p.getScale();
		Vec3 hand = hand(p, true, d.partial);
		Vec3 shoulder = body(p, d.partial, 1.375, 0.3125, 0);
		Vec3 look = p.getViewVector(d.partial);
		Vec3 axis = hand.subtract(shoulder);
		Vec3 a1 = axis.cross(new Vec3(0, 1, 0));
		a1 = a1.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : a1.normalize();
		Vec3 a2 = axis.normalize().cross(a1).normalize();
		for (int k = 0; k < 3; k++) {
			int n = 18;
			Vec3[] pts = new Vec3[n];
			for (int i = 0; i < n; i++) {
				double s = (double) i / (n - 1);
				double ang = s * Math.PI * 4 + k * Math.PI * 2 / 3 + t * 0.5;
				double r = 0.1 * sc * (1.0 - 0.5 * s * charge);
				pts[i] = shoulder.add(axis.scale(s)).add(a1.scale(Math.cos(ang) * r)).add(a2.scale(Math.sin(ang) * r));
			}
			d.strand(pts, (float) (0.035 * sc), 0.6f);
		}
		double len = (0.3 + 1.2 * charge) * sc;
		int n = 10;
		Vec3[] spear = new Vec3[n];
		float[] radii = new float[n];
		for (int i = 0; i < n; i++) {
			double s = (double) i / (n - 1);
			double wob = Math.sin(s * 9 - t * 0.9) * 0.02 * (1 - charge) * sc;
			spear[i] = hand.add(look.scale(len * s)).add(0, wob, 0).subtract(d.cam);
			radii[i] = (float) ((0.06 + 0.06 * charge) * sc * (1.0 - s) + 0.004);
		}
		GooMesh.tube(d.vc, d.pose.last(), d.light(hand), spear, radii, 6, 0.6f);
		if (charge >= 1f) {
			Vec3 tip = hand.add(look.scale(len));
			Vec3 side = look.cross(new Vec3(0, 1, 0));
			side = side.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : side.normalize();
			Vec3 up = side.cross(look).normalize();
			for (int k = 0; k < 4; k++) {
				double a = Math.PI * 2 * k / 4 + t * 0.2;
				Vec3 root = tip.subtract(look.scale(0.18 * sc));
				Vec3 barb = root.subtract(look.scale(0.22 * sc)).add(side.scale(Math.cos(a) * 0.12 * sc)).add(up.scale(Math.sin(a) * 0.12 * sc));
				d.strand(new Vec3[] { root, barb }, (float) (0.03 * sc), 0.1f);
			}
		}
	}

	/** Dive Bomb: tendrils stream off the shoulders and back like a wake, claws reach down from the feet. */
	private static void dive(Draw d, Player p, float t, float age) {
		double sc = p.getScale();
		Vec3 vel = p.getDeltaMovement();
		Vec3 wake = vel.lengthSqr() < 1.0e-4 ? new Vec3(0, 1, 0) : vel.normalize().scale(-1);
		float grow = ease(age / 3f);
		for (int k = 0; k < 5; k++) {
			double side = (k - 2) * 0.13;
			Vec3 root = body(p, d.partial, 1.25 - Math.abs(side) * 0.6, side, -0.12);
			Vec3 spread = right(p, d.partial).scale(side * 3.0);
			Vec3 tip = root.add(wake.scale((1.1 + 0.25 * Math.sin(t * 0.7 + k)) * sc)).add(spread.scale(sc));
			d.tendril(root, tip, grow, (float) (0.06 * sc), p.getId() * 7 + k, 0.2 * sc, null, true);
		}
		for (int j = -1; j <= 1; j += 2) {
			Vec3 root = body(p, d.partial, 0.15, j * 0.12, 0);
			Vec3 tip = root.add(right(p, d.partial).scale(j * 0.35 * sc)).add(0, -0.85 * sc, 0).add(fwd(p, d.partial).scale(0.2 * sc));
			d.tendril(root, tip, grow, (float) (0.07 * sc), p.getId() * 3 + j, 0.12 * sc, null, true);
		}
	}

	/** Onslaught's wind-up: tendrils from the legs dig into the ground, and a closing ring of claws rises round the host. */
	private static void onslaught(Draw d, Player p, float frac, float t) {
		double sc = p.getScale();
		Vec3 feet = p.getPosition(d.partial);
		for (int k = 0; k < 4; k++) {
			double a = Math.PI * 2 * k / 4 + Math.PI / 4;
			int side = (k == 0 || k == 3) ? 1 : -1;
			Vec3 root = body(p, d.partial, 0.45, side * 0.12, 0);
			Vec3 ground = feet.add(Math.cos(a) * (0.9 + 0.2 * frac) * sc, 0.02, Math.sin(a) * (0.9 + 0.2 * frac) * sc);
			d.tendril(root, ground, Math.min(1f, frac * 4f), (float) (0.08 * sc), p.getId() + k, 0.25 * sc, new Vec3(0, 1, 0), false);
		}
		double r = 3.4 * (1.0 - frac * 0.6); // v0.17.5 Monster form charge: the claws close in tight round the host (was 9 for Onslaught)
		int n = 14 + Math.round(frac * 10);
		for (int k = 0; k < n; k++) {
			double a = Math.PI * 2 * k / n + t * 0.03;
			Vec3 root = feet.add(Math.cos(a) * r, 0.02, Math.sin(a) * r);
			double h = (0.4 + 0.9 * frac) * (0.8 + 0.2 * Math.sin(t * 0.4 + k * 2.1));
			Vec3 tip = root.add(-Math.cos(a) * 0.5 * frac, h, -Math.sin(a) * 0.5 * frac);
			d.tendril(root, tip, 1f, 0.07f, k * 31 + p.getId(), 0.15, null, true);
		}
	}

	// ------------------------------------------------------------------ accents

	/** A few goo droplets in the host's body colour (the only particles left on the moves). */
	private static void accents(Level level, Fx fx) {
		int count = switch (fx.kind) {
			case SymbioteVfx.IMPACT -> 2;
			case SymbioteVfx.GROUND_BURST, SymbioteVfx.LEG_PUSH -> 3;
			case SymbioteVfx.BODY_BURST, SymbioteVfx.JAWS -> 2;
			default -> 0;
		};
		if (count == 0) {
			return;
		}
		Entity owner = level.getEntity(fx.owner);
		int rgb = owner instanceof Player p && Symbiote.isNormalHost(p) ? SymbioteSuitTint.colours(p).primary() : 0x14161F;
		Vec3 at = fx.kind == SymbioteVfx.BODY_BURST && owner != null ? owner.position().add(0, owner.getBbHeight() * 0.6, 0) : fx.pos;
		for (int i = 0; i < count; i++) {
			Vec3 v = randomUnit(fx.seed, i + 500).scale(0.08).add(0, 0.06, 0);
			level.addParticle(new SymbioteParticles.Goo(rgb, 0.04f), at.x, at.y + 0.05, at.z, v.x, v.y, v.z);
		}
	}
}
