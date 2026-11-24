package com.projecthero.mod.client.eyebeam;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.5 (user: "change how heat vision in the mod looks, like laser vision in general, i want it to look better in third
 * person and in first person"): the one look every eye beam shares -- the Kryptonian's Heat Vision, Laser Vision (and the
 * Laser Vision boss), the Sentry's Solar Gaze, Starfire's Eye Beams, Vision's Mind Stone beams and Doomsday's heat vision.
 * Each renderer still decides <i>when</i> its beam is live and where it lands; it hands the beam here every frame
 * ({@link #twin}, {@link #brow}, {@link #twinAt}, {@link #twinFrom}) with its own {@link Style} (colours, widths, sparks), and
 * this class draws everything in one pass at the end of {@code AFTER_ENTITIES} ({@link #init} registers last).
 *
 * <p><b>Third person.</b> One thin beam from each eye, placed with the head's real transform: the player model's head matrix is
 * captured as it is drawn ({@code EyeBeamHeadCaptureMixin}), so the beams leave the eyes whatever the pose -- crouching,
 * flying, swimming, scaled (a head-pivot estimate stands in when the model was not drawn this frame). Each beam is four soft-edged
 * layers (wide haze, coloured glow, additive core, white-hot centre) with a slight flicker and bright pulses running out along it,
 * a faint heat shimmer at the eyes, soft glowing eyes on the face, and a flare at the hit point with rotating rays, sparks and
 * embers (capped) and a molten scorch on the block that cools and fades over a few seconds. Starting, the beam shoots out over
 * two ticks and swells to full; stopping, it thins and pulls away from the eyes toward the target.
 *
 * <p><b>First person.</b> The beams leave your eyes: each comes in from the side of the view at eye level (the brow beam from
 * above) as one even thin line, fading in at the screen edge, and converges on the crosshair, thinning toward the target so it
 * stays visible; a soft warm glow blooms at the side edges at eye level and faintly along the top while firing ({@link #hud}). Your own beams seen from behind in third person draw their first stretch over your head (no depth
 * test), and from the front they stop short of the camera.
 *
 * <p>Beam clashes are unchanged: callers still skip beams {@code BeamClashClient.hides(...)}.
 */
public final class EyeBeamFx {
	/** A beam's look: colours from the outer haze in to the white-hot centre, base half-widths, and its impact particles. */
	public record Style(int haze, int glow, int core, int hot, float glowW, float coreW, ParticleOptions spark, ParticleOptions ember, float rampTicks,
			float fadeTicks) {
	}

	/** Heat Vision (Kryptonian): red-orange heat with a yellow-white centre, flames and lava embers. */
	public static final Style HEAT = new Style(0xFF2A06, 0xFF4410, 0xFF8A30, 0xFFF2D0, 0.12f, 0.036f, ParticleTypes.SMALL_FLAME, ParticleTypes.LAVA,
			3f, 4f);
	/** Laser Vision (and the Laser Vision boss): a cleaner, redder laser. */
	public static final Style LASER = new Style(0xFF1206, 0xFF200E, 0xFF4A2A, 0xFFE4DA, 0.11f, 0.034f,
			new DustParticleOptions(new Vector3f(1f, 0.25f, 0.15f), 0.7f), ParticleTypes.SMALL_FLAME, 2f, 4f);
	/** The Sentry's Solar Gaze, warming up. */
	public static final Style SOLAR = new Style(0xFFA020, 0xFFB52A, 0xFFD870, 0xFFFBEA, 0.12f, 0.038f, ParticleTypes.WAX_ON, ParticleTypes.SMALL_FLAME,
			3f, 4f);
	/** The Solar Gaze once it runs hot. */
	public static final Style SOLAR_HOT = new Style(0xFF7A10, 0xFF8A20, 0xFFC060, 0xFFF6E0, 0.12f, 0.038f, ParticleTypes.WAX_ON, ParticleTypes.LAVA,
			3f, 4f);
	/** Starfire's green starbolt eyes. */
	public static final Style STARFIRE = new Style(0x1EE028, 0x3CFF3A, 0x9CFF8C, 0xF0FFE8, 0.12f, 0.036f,
			new DustParticleOptions(new Vector3f(0.35f, 1f, 0.3f), 0.8f), ParticleTypes.GLOW, 3f, 4f);
	/** Vision's Mind Stone: gold-yellow. */
	public static final Style MIND = new Style(0xFFB820, 0xFFC830, 0xFFE890, 0xFFFFF0, 0.12f, 0.04f, ParticleTypes.WAX_ON, ParticleTypes.END_ROD,
			3f, 4f);
	/** Doomsday's heat vision: deeper, angrier red. */
	public static final Style DOOMSDAY = new Style(0xE01004, 0xFF1A0A, 0xFF7038, 0xFFF0D0, 0.12f, 0.036f, ParticleTypes.FLAME, ParticleTypes.LAVA,
			2f, 4f);

	/** Where a beam lands: the point, the block face it burns ({@code null} for a creature or open air). */
	public record Hit(Vec3 pos, Direction face, boolean entity) {
		public static Hit at(Vec3 pos) {
			return new Hit(pos, null, false);
		}
	}

	private static final int TWIN = 0, BROW = 1, RAW = 2;

	private record Req(int mode, long key, Player shooter, Style style, Vec3 dir, Hit hit, float width, float alpha, boolean halo, Vec3 rawA,
			Vec3 rawB) {
	}

	private static final class State {
		float start;
		float lastSeen;
		long frame;
		Req last;
		long sparkTick = -1;
		long decalTick = -1;
	}

	private record Head(Matrix4f m, Vec3 cam, long frame) {
	}

	private static final class Decal {
		final Vec3 pos;
		final Direction face;
		final Style style;
		float born;
		final float size;

		Decal(Vec3 pos, Direction face, Style style, float born, float size) {
			this.pos = pos;
			this.face = face;
			this.style = style;
			this.born = born;
			this.size = size;
		}
	}

	private static final List<Req> REQS = new ArrayList<>();
	private static final Map<Long, State> STATES = new HashMap<>();
	private static final Map<Integer, Head> HEADS = new HashMap<>();
	private static final List<Decal> DECALS = new ArrayList<>();
	private static final Set<Long> SPARKED = new HashSet<>();
	private static final int MAX_DECALS = 96;
	private static final float DECAL_LIFE = 80f;
	private static final int MAX_PARTICLES_PER_TICK = 18;
	private static final float GROW_TICKS = 2f;
	/**
	 * First person: where the beams leave your eyes. Seen from your own eyes, a beam from each eye is a line coming in from the side of
	 * the view at eye level and meeting the crosshair, so each beam starts {@code FP_EYE_FORWARD} blocks in front of the camera, out
	 * to {@code FP_EYE_SIDE} of the way to the screen's side edge and {@code FP_EYE_DOWN} of the way down to its bottom edge (shares of
	 * the half-screen, so it looks the same at every FOV and window shape); the brow beam comes down from {@code FP_BROW_UP} of the
	 * way to the top edge. {@code FP_FULL_DIST}: how far out (blocks) the beam reaches its full width -- until then it widens with
	 * distance, so on screen it stays one even thin line instead of a slab right in front of you.
	 */
	static final double FP_EYE_FORWARD = 0.12, FP_EYE_SIDE = 0.86, FP_EYE_DOWN = 0.16, FP_BROW_UP = 0.55, FP_FULL_DIST = 3.0;
	private static long frameNo;
	private static long particleTick = -1;
	private static int particlesThisTick;
	private static final RandomSource RNG = RandomSource.create();

	// what the first-person screen glow should show (set while drawing your own first-person beam)
	private static long fpFrame = -10;
	private static float fpStrength;
	private static int fpColour;
	private static float hudGlow;

	// the layers collected this frame (camera-relative points), drawn glow -> add -> over
	private static final List<Quad> GLOW_Q = new ArrayList<>();
	private static final List<Quad> ADD_Q = new ArrayList<>();
	private static final List<Quad> OVER_Q = new ArrayList<>();

	/** One quad, camera-relative: four points and their ARGB colours. */
	private static final class Quad {
		final float[] xyz = new float[12];
		final int[] argb = new int[4];
	}

	private EyeBeamFx() {
	}

	/** Call once, after every renderer that hands beams in has registered its own {@code AFTER_ENTITIES} listener. */
	public static void init() {
		WorldRenderEvents.START.register(ctx -> frameNo++);
		WorldRenderEvents.AFTER_ENTITIES.register(EyeBeamFx::render);
		HudRenderCallback.EVENT.register(EyeBeamFx::hud);
		ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> c.execute(EyeBeamFx::clear));
	}

	public static void clear() {
		REQS.clear();
		STATES.clear();
		HEADS.clear();
		DECALS.clear();
		SPARKED.clear();
		GLOW_Q.clear();
		ADD_Q.clear();
		OVER_Q.clear();
		hudGlow = 0f;
	}

	// ================================================================ the API the beam renderers call every frame

	/** Twin beams from {@code shooter}'s eyes, along {@code dir}, landing at {@code hit}. {@code channel} tells a player's beams apart. */
	public static void twin(Player shooter, int channel, Style style, Vec3 dir, Hit hit, float width, float alpha) {
		twin(shooter, channel, style, dir, hit, width, alpha, false);
	}

	/** As {@link #twin(Player, int, Style, Vec3, Hit, float, float)}; {@code halo} adds a wide white-hot centre between the eyes. */
	public static void twin(Player shooter, int channel, Style style, Vec3 dir, Hit hit, float width, float alpha, boolean halo) {
		REQS.add(new Req(TWIN, key(shooter.getId(), channel), shooter, style, dir, hit, width, alpha, halo, null, null));
	}

	/** One beam from the middle of {@code shooter}'s brow (Vision's Mind Stone). */
	public static void brow(Player shooter, int channel, Style style, Vec3 dir, Hit hit, float width, float alpha) {
		REQS.add(new Req(BROW, key(shooter.getId(), channel), shooter, style, dir, hit, width, alpha, false, null, null));
	}

	/** Twin beams with nobody to follow (server-sent beams, bosses): from either side of {@code start} to {@code end}. */
	public static void twinAt(Vec3 start, Vec3 end, Style style, float width, float alpha) {
		Vec3 d = end.subtract(start);
		if (d.lengthSqr() < 1.0e-6) {
			return;
		}
		Vec3 dir = d.normalize();
		Vec3 right = rightOf(dir);
		Vec3 a = start.add(dir.scale(0.25)).add(right.scale(0.088));
		Vec3 b = start.add(dir.scale(0.25)).add(right.scale(-0.088));
		twinFrom(start, a, b, faceAt(end, dir), style, width, alpha);
	}

	/** Twin beams from two given eye points (a boss's own eye positions) to {@code hit}. {@code id} keys its sparks. */
	public static void twinFrom(Vec3 id, Vec3 eyeA, Vec3 eyeB, Hit hit, Style style, float width, float alpha) {
		Vec3 d = hit.pos().subtract(eyeA.add(eyeB).scale(0.5));
		if (d.lengthSqr() < 1.0e-6) {
			return;
		}
		long k = 0x7F00000000000000L ^ (Mth.floor(id.x * 2) * 73856093L) ^ (Mth.floor(id.y * 2) * 19349663L) ^ (Mth.floor(id.z * 2) * 83492791L);
		REQS.add(new Req(RAW, k, null, style, d.normalize(), hit, width, alpha, false, eyeA, eyeB));
	}

	/** The ray every eye beam uses: first block (collider) within {@code range}, then optionally the first creature before it. */
	public static Hit ray(Level level, Entity shooter, Vec3 from, Vec3 dir, double range, boolean entities) {
		Vec3 to = from.add(dir.scale(range));
		BlockHitResult bhr = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter));
		boolean block = bhr.getType() != HitResult.Type.MISS;
		Vec3 end = block ? bhr.getLocation() : to;
		if (entities) {
			EntityHitResult ehr = ProjectileUtil.getEntityHitResult(shooter, from, end, new AABB(from, end).inflate(1.0),
					e -> e != shooter && e.isPickable() && e instanceof LivingEntity && !(e instanceof ArmorStand) && !e.isSpectator(),
					from.distanceToSqr(end));
			if (ehr != null) {
				// v0.17.7: the hit-box is where the creature is this tick, but it is drawn between ticks -- move the end with the
				// drawn body so the beam doesn't jitter on a moving target
				Entity e = ehr.getEntity();
				float pt = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
				return new Hit(ehr.getLocation().add(e.getPosition(pt).subtract(e.position())), null, true);
			}
		}
		return new Hit(end, block ? bhr.getDirection() : null, false);
	}

	/** A block hit at {@code end} along {@code dir}, the face found by a tiny probe (server-sent beams carry no face). */
	private static Hit faceAt(Vec3 end, Vec3 dir) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return Hit.at(end);
		}
		BlockHitResult b = level.clip(new ClipContext(end.subtract(dir.scale(0.3)), end.add(dir.scale(0.3)), ClipContext.Block.COLLIDER,
				ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty()));
		return b.getType() == HitResult.Type.MISS ? Hit.at(end) : new Hit(end, b.getDirection(), false);
	}

	/** Captures a player's head transform as the model is drawn ({@code EyeBeamHeadCaptureMixin}). */
	public static void captureHead(LivingEntity e, EntityModel<?> model, PoseStack pose) {
		if (!(e instanceof Player) || !(model instanceof HumanoidModel<?> hm) || !hm.head.visible) {
			return;
		}
		PoseStack ps = new PoseStack();
		ps.last().pose().set(pose.last().pose());
		hm.head.translateAndRotate(ps);
		Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
		HEADS.put(e.getId(), new Head(new Matrix4f(ps.last().pose()), cam, frameNo));
	}

	private static long key(int id, int channel) {
		return ((long) id << 8) | (channel & 0xFF);
	}

	// ================================================================ the frame

	private static void render(WorldRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		PoseStack poseStack = context.matrixStack();
		MultiBufferSource consumers = context.consumers();
		if (level == null || poseStack == null || consumers == null) {
			REQS.clear();
			return;
		}
		float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
		float now = level.getGameTime() + partial;
		Camera camera = context.camera();
		Vec3 cam = camera.getPosition();
		if (level.getGameTime() != particleTick) {
			particleTick = level.getGameTime();
			particlesThisTick = 0;
			SPARKED.clear();
		}
		GLOW_Q.clear();
		ADD_Q.clear();
		OVER_Q.clear();
		Frame f = new Frame(mc, level, camera, cam, partial, now);
		// the view rotation lives in positionMatrix (the pose stack here is only the identity the quads are drawn with)
		f.screen(context.projectionMatrix(), new Matrix4f(context.positionMatrix()).mul(poseStack.last().pose()));

		for (Req r : REQS) {
			if (r.mode() == RAW) {
				f.draw(r, 1f, 1f, 0f);
				spawnAt(r.hit(), r.dir(), r.style(), r.width()); // at most once a tick per spot (SPARKED)
				if (r.hit().face() != null) {
					addDecal(r.hit(), r.style(), r.width());
				}
				continue;
			}
			State st = STATES.get(r.key());
			if (st == null) {
				st = new State();
				st.start = now;
				STATES.put(r.key(), st);
			} else if (st.frame != frameNo) {
				float since = now - st.lastSeen;
				if (since > r.style().fadeTicks() + 1f) {
					st.start = now; // a fresh beam: ramp and shoot out again
				} else if (since > 1.5f) {
					// re-fired while still fading: pick up from the fade's brightness instead of popping
					float s = 1f - Mth.clamp(since / r.style().fadeTicks(), 0f, 1f);
					st.start = now - s * r.style().rampTicks();
				}
			}
			st.lastSeen = now;
			st.frame = frameNo;
			st.last = r;
			float age = now - st.start;
			f.draw(r, Mth.clamp(age / Math.max(0.5f, r.style().rampTicks()), 0f, 1f), Mth.clamp(age / GROW_TICKS, 0f, 1f), 0f);
			f.sparks(r, st);
		}
		REQS.clear();

		// beams that stopped: fade out over a few ticks, pulling away from the eyes toward the target
		for (Iterator<Map.Entry<Long, State>> it = STATES.entrySet().iterator(); it.hasNext();) {
			State st = it.next().getValue();
			if (st.frame == frameNo) {
				continue;
			}
			float t = (now - st.lastSeen) / st.last.style().fadeTicks();
			Player p = st.last.shooter();
			if (t >= 1f || t < 0f || p == null || p.isRemoved() || p.level() != level
					|| com.projecthero.mod.client.beamclash.BeamClashClient.hides(p)) {
				it.remove();
				continue;
			}
			f.draw(st.last, 1f, 1f, t);
		}

		// scorches cooling on the blocks
		DECALS.removeIf(d -> now - d.born > DECAL_LIFE || now < d.born - 40f);
		for (Decal d : DECALS) {
			f.decal(d);
		}
		if (fpFrame != frameNo) {
			fpStrength = 0f;
		}
		if (GLOW_Q.isEmpty() && ADD_Q.isEmpty() && OVER_Q.isEmpty()) {
			return;
		}
		poseStack.pushPose();
		Matrix4f m = poseStack.last().pose();
		flush(consumers.getBuffer(EyeBeamRenderType.GLOW), m, GLOW_Q);
		flush(consumers.getBuffer(EyeBeamRenderType.ADD), m, ADD_Q);
		if (!OVER_Q.isEmpty()) {
			flush(consumers.getBuffer(EyeBeamRenderType.OVER), m, OVER_Q);
		}
		poseStack.popPose();
		if (HEADS.size() > 64) {
			HEADS.values().removeIf(h -> frameNo - h.frame > 200);
		}
	}

	private static void flush(VertexConsumer vc, Matrix4f m, List<Quad> quads) {
		for (Quad q : quads) {
			for (int v = 0; v < 4; v++) {
				vc.addVertex(m, q.xyz[v * 3], q.xyz[v * 3 + 1], q.xyz[v * 3 + 2]).setColor(q.argb[v]);
			}
		}
	}

	/** One frame's drawing context: camera, partial tick, and the geometry helpers (camera-relative points). */
	private static final class Frame {
		final Minecraft mc;
		final ClientLevel level;
		final Vec3 cam;
		final float partial;
		final float now;
		final Vec3 camLeft;
		final Vec3 camUp;
		final Vec3 camLook;
		/** v0.17.7: screen (clip space) -> camera-relative world, this frame's projection x view, view bobbing and hurt tilt included. */
		Matrix4f unproject;

		Frame(Minecraft mc, ClientLevel level, Camera camera, Vec3 cam, float partial, float now) {
			this.mc = mc;
			this.level = level;
			this.cam = cam;
			this.partial = partial;
			this.now = now;
			Vector3f l = camera.getLeftVector();
			Vector3f u = camera.getUpVector();
			this.camLeft = new Vec3(l.x(), l.y(), l.z());
			this.camUp = new Vec3(u.x(), u.y(), u.z());
			Vector3f k = camera.getLookVector();
			this.camLook = new Vec3(k.x(), k.y(), k.z());
		}

		void screen(Matrix4f projection, Matrix4f view) {
			Matrix4f m = new Matrix4f(projection).mul(view);
			unproject = Math.abs(m.determinant()) > 1.0e-12f ? m.invert() : null;
		}

		/**
		 * v0.17.7 (user: "in first person when player is using laser vision the laser vision wobbles around"): the world point that
		 * draws at screen position ({@code ndcX}, {@code ndcY}) (-1..1, up positive), {@code fwd} blocks in front of the camera. Built
		 * from this frame's own projection, so view bobbing, the hurt tilt and the FOV changing (sprinting, slowness, flying) leave it
		 * fixed on screen. The old point, placed in the world 0.12 blocks ahead, was swung across the screen by all three.
		 */
		Vec3 onScreen(double ndcX, double ndcY, double fwd) {
			if (unproject == null) {
				return null;
			}
			Vector4f a = unproject.transform(new Vector4f((float) ndcX, (float) ndcY, -1f, 1f));
			Vector4f b = unproject.transform(new Vector4f((float) ndcX, (float) ndcY, 0.5f, 1f));
			if (Math.abs(a.w) < 1.0e-9f || Math.abs(b.w) < 1.0e-9f) {
				return null;
			}
			Vec3 na = new Vec3(a.x / a.w, a.y / a.w, a.z / a.w);
			Vec3 nb = new Vec3(b.x / b.w, b.y / b.w, b.z / b.w);
			Vec3 d = nb.subtract(na);
			double along = d.dot(camLook);
			if (Math.abs(along) < 1.0e-9) {
				return null;
			}
			double t = (fwd - na.dot(camLook)) / along;
			return cam.add(na.add(d.scale(t)));
		}

		/**
		 * One beam request at {@code strength} (ramp 0..1), {@code grow} (how far it has shot out, 0..1) and {@code fade} (0 while
		 * live, rising to 1 as it goes out).
		 */
		void draw(Req r, float strength, float grow, float fade) {
			Style s = r.style();
			Player p = r.shooter();
			boolean fp = false, ownBack = false, ownFront = false;
			Vec3[] eyes;
			Head head = null;
			if (r.mode() == RAW) {
				eyes = new Vec3[] { r.rawA(), r.rawB() };
			} else {
				boolean own = p == mc.getCameraEntity();
				fp = own && mc.options.getCameraType().isFirstPerson();
				ownBack = own && !fp && !mc.options.getCameraType().isMirrored();
				ownFront = own && !fp && mc.options.getCameraType().isMirrored();
				Head h = HEADS.get(p.getId());
				head = h != null && h.frame == frameNo && !fp ? h : null;
				eyes = fp ? fpOrigins(p, r.mode() == BROW) : eyeOrigins(p, head, r.mode() == BROW);
			}
			Vec3 end = r.hit().pos();
			float live = 1f - fade;
			float w = r.width() * (0.45f + 0.55f * strength) * (1f - 0.65f * fade);
			float a = r.alpha() * (0.35f + 0.65f * strength) * live;
			if (a <= 0.01f) {
				return;
			}
			boolean capped = false;
			if (ownFront) {
				// the front camera sits on your line of sight: stop the beam short of it instead of flooding the screen
				Vec3 eye = p.getEyePosition(partial);
				Vec3 dir = r.dir();
				double camAlong = cam.subtract(eye).dot(dir);
				if (camAlong > 0.8 && eye.distanceTo(end) > camAlong - 0.8) {
					end = eye.add(dir.scale(camAlong - 0.8));
					capped = true;
				}
			}
			float seed = (r.key() & 0xFF) * 0.37f + (r.key() >> 8 & 0xFFFF) * 0.11f;
			for (int i = 0; i < eyes.length; i++) {
				Vec3 from = eyes[i];
				Vec3 tip = grow >= 1f ? end : from.add(end.subtract(from).scale(grow));
				if (fade > 0f) {
					from = from.add(end.subtract(from).scale(Math.min(0.85, fade * 0.9))); // pulls away toward the target
				}
				body(from, tip, s, w, a, fp, seed + i * 2.3f, fade > 0f ? 0f : 1f);
				if (ownBack) {
					Vec3 side = eyes.length == 2 ? eyes[i].subtract(eyes[1 - i]) : Vec3.ZERO;
					overlay(from, tip, s, w, a, side.lengthSqr() > 1.0e-8 ? side.normalize() : Vec3.ZERO);
				}
			}
			if (r.halo() && eyes.length == 2) {
				Vec3 mid = eyes[0].add(eyes[1]).scale(0.5);
				Vec3 tip = grow >= 1f ? end : mid.add(end.subtract(mid).scale(grow));
				float fl = fp ? 1f : flicker(seed + 9f);
				soft(GLOW_Q, mid, tip, s.glowW() * w * 2.4f * fl, s.glowW() * w * 3.2f * fl, s.haze(), (fp ? 0.12f : 0.2f) * a, (fp ? 0.14f : 0.22f) * a);
				soft(ADD_Q, mid, tip, s.coreW() * w * 1.4f, s.coreW() * w * 1.6f, s.hot(), (fp ? 0.5f : 0.9f) * a, (fp ? 0.6f : 0.9f) * a);
			}
			if (grow >= 1f && !capped) {
				impact(end, r.hit().face(), s, ownBack ? w * 1.8f : w, a * (fade > 0f ? live : 1f), fp);
			}
			if (!fp && r.mode() != RAW) {
				eyeGlow(p, head, eyes, s, Math.max(a, 0.5f * live * r.alpha()), w);
			}
			if (fp && fade <= 0f) {
				fpFrame = frameNo;
				if (a > fpStrength) {
					fpStrength = a;
					fpColour = s.glow();
				}
			}
		}

		/** One beam: haze, glow, core, white-hot centre, pulses running out along it; first person fades in at the screen edge. */
		void body(Vec3 a, Vec3 b, Style s, float w, float alpha, boolean fp, float seed, float pulses) {
			Vec3 d = b.subtract(a);
			double len = d.length();
			if (len < 0.02) {
				return;
			}
			Vec3 dir = d.scale(1.0 / len);
			float fl = fp ? 1f : flicker(seed); // v0.17.7: steady in first person (a flicker that close to your eyes reads as shaking)
			float haze = s.glowW() * w * 2.1f * fl;
			float glow = s.glowW() * w * fl;
			float core = s.coreW() * w * 1.5f * (0.94f + 0.06f * fl);
			float hot = s.coreW() * w * 0.55f;
			if (fp) {
				// from your eyes (fpOrigins), drawn as a run of short pieces. Near the camera the width grows with distance from it (so on screen
				// the beam is one even thin line coming in from the side, never a slab), it fades in from nothing at the screen edge,
				// and over the last stretch it thins and turns see-through so the crosshair and what you burn stay visible
				double tail = len * (1.0 - Math.min(0.35, 2.5 / len));
				double[] at = { 0.0, 0.025, 0.06, 0.12, 0.25, 0.5, 1.0, 2.0, FP_FULL_DIST, tail, len };
				double prevD = 0.0;
				Vec3 prevP = a;
				float prevW = fpWidth(a, 0.0, tail, len), prevA = fpAlpha(0.0, tail, len) * alpha;
				for (int k = 1; k < at.length; k++) {
					double dk = Math.min(at[k], len);
					if (dk <= prevD + 1.0e-4) {
						continue;
					}
					Vec3 pk = a.add(dir.scale(dk));
					float wk = fpWidth(pk, dk, tail, len), ak = fpAlpha(dk, tail, len) * alpha;
					layer(prevP, pk, haze * prevW * 0.75f, haze * wk * 0.75f, glow * prevW, glow * wk, core * prevW, core * wk, hot * prevW, hot * wk, s,
							prevA, ak);
					prevD = dk;
					prevP = pk;
					prevW = wk;
					prevA = ak;
				}
			} else {
				// third person: a hair narrower right at the eye so it reads as leaving the eye, not a slab in front of the face
				double t1 = Math.min(0.4, 0.6 / len);
				Vec3 p1 = a.add(dir.scale(len * t1));
				layer(a, p1, haze * 0.25f, haze, glow * 0.45f, glow, core * 0.6f, core, hot * 0.8f, hot, s, alpha * 0.9f, alpha);
				layer(p1, b, haze, haze * 1.15f, glow, glow * 1.1f, core, core, hot, hot, s, alpha, alpha);
				shimmer(a, dir, s, w, alpha, seed);
			}
			if (pulses > 0f && len > 1.5) {
				// bright pulses running out along the beam
				double speed = 3.2; // blocks a tick
				double plen = Math.min(1.4, len * 0.12);
				for (int k = 0; k < 2; k++) {
					double along = ((now * speed + k * len * 0.5 + seed * 7.0) % len + len) % len;
					if (along + plen > len) {
						continue;
					}
					float fpA = fp ? (float) Mth.clamp(along / (len * 0.3), 0.0, 1.0) * 0.6f : 1f;
					Vec3 p0 = a.add(dir.scale(along));
					Vec3 p1 = a.add(dir.scale(along + plen));
					soft(ADD_Q, p0, p1, core * 1.2f, core * 1.6f, s.core(), 0f, 0.5f * alpha * fpA);
				}
			}
		}

		/** First person: the beam's width (share of full) at point {@code p}, {@code d} blocks from your eye, {@code tail} where it starts thinning. */
		private float fpWidth(Vec3 p, double d, double tail, double len) {
			float near = (float) Math.pow(Mth.clamp(p.distanceTo(cam) / FP_FULL_DIST, 0.0, 1.0), 0.75); // a touch wider on screen near you, like perspective
			float thin = d <= tail || len <= tail ? 1f : 1f - 0.5f * (float) ((d - tail) / (len - tail));
			return 0.7f * near * thin;
		}

		/** First person: the beam's brightness (share of full) {@code d} blocks from your eye: in from nothing at the screen edge, see-through at the target. */
		private float fpAlpha(double d, double tail, double len) {
			float in = (float) Mth.clamp(d / 0.07, 0.0, 1.0);
			float out = d <= tail || len <= tail ? 1f : 1f - 0.45f * (float) ((d - tail) / (len - tail));
			return 0.8f * in * in * (3f - 2f * in) * out;
		}

		private void layer(Vec3 a, Vec3 b, float hazeA, float hazeB, float glowA, float glowB, float coreA, float coreB, float hotA, float hotB,
				Style s, float alA, float alB) {
			soft(GLOW_Q, a, b, hazeA, hazeB, s.haze(), 0.14f * alA, 0.14f * alB);
			soft(GLOW_Q, a, b, glowA, glowB, s.glow(), 0.42f * alA, 0.42f * alB);
			soft(ADD_Q, a, b, coreA, coreB, s.core(), 0.85f * alA, 0.85f * alB);
			soft(ADD_Q, a, b, hotA, hotB, s.hot(), alA, alB);
		}

		/**
		 * Your own beam seen from behind in third person (the camera sits on your line of sight, so the real beam is end-on behind
		 * your head): its first stretch drawn over your head from just beside it, converging on the target, fading out (no depth test).
		 */
		void overlay(Vec3 a, Vec3 b, Style s, float w, float alpha, Vec3 side) {
			a = a.add(side.scale(0.2));
			Vec3 d = b.subtract(a);
			double len = Math.min(7.0, d.length());
			if (len < 0.05) {
				return;
			}
			Vec3 far = a.add(d.normalize().scale(len));
			soft(OVER_Q, a, far, s.glowW() * w * 1.1f, s.glowW() * w * 0.5f, s.glow(), 0.45f * alpha, 0f);
			soft(OVER_Q, a, far, s.coreW() * w * 1.1f, s.coreW() * w * 0.5f, s.hot(), 0.9f * alpha, 0f);
		}

		/** A faint wavering of the air just in front of the eyes. */
		void shimmer(Vec3 a, Vec3 dir, Style s, float w, float alpha, float seed) {
			Vec3 side = dir.cross(cam.subtract(a));
			if (side.lengthSqr() < 1.0e-8) {
				return;
			}
			side = side.normalize();
			Vec3 prev = a;
			for (int k = 1; k <= 4; k++) {
				double t = k * 0.22;
				double off = Math.sin(now * 1.9 + k * 1.7 + seed) * 0.018 * k;
				Vec3 p = a.add(dir.scale(t)).add(side.scale(off));
				soft(GLOW_Q, prev, p, 0.05f * w, 0.07f * w, s.hot(), 0.10f * alpha * (1f - k * 0.2f), 0.08f * alpha * (1f - k * 0.2f));
				prev = p;
			}
		}

		/** The hit point: soft layered flare, rotating rays, a molten spot on the block (and a scorch that stays a while). */
		void impact(Vec3 end, Direction face, Style s, float w, float alpha, boolean fp) {
			Vec3 toCam = cam.subtract(end);
			double dist = toCam.length();
			Vec3 c = dist > 0.3 ? end.add(toCam.scale(0.12 / dist)) : end;
			float pulse = 0.88f + 0.12f * Mth.sin(now * 2.1f);
			float r = 0.6f * w * pulse * (fp ? 0.75f : 1f);
			float al = alpha * (fp ? 0.8f : 1f);
			disc(GLOW_Q, c, camLeft, camUp, r * 2.0f, s.haze(), 0.28f * al);
			disc(GLOW_Q, c, camLeft, camUp, r * 1.1f, s.glow(), 0.5f * al);
			disc(ADD_Q, c, camLeft, camUp, r * 0.6f, s.core(), 0.95f * al);
			disc(ADD_Q, c, camLeft, camUp, r * 0.3f, s.hot(), al);
			for (int k = 0; k < 6; k++) {
				double ang = now * 0.11 + k * (Math.PI / 3.0) + (k % 2) * 0.3;
				float len = r * (k % 2 == 0 ? 2.3f : 1.5f) * (0.75f + 0.25f * Mth.sin(now * 3.1f + k * 1.9f));
				Vec3 rd = camLeft.scale(Math.cos(ang)).add(camUp.scale(Math.sin(ang)));
				soft(ADD_Q, c, c.add(rd.scale(len)), r * 0.1f, 0f, s.core(), 0.7f * al, 0f);
			}
			if (face != null) {
				Vec3 n = Vec3.atLowerCornerOf(face.getNormal());
				Vec3[] uv = faceAxes(face);
				Vec3 at = end.add(n.scale(0.012));
				disc(ADD_Q, at, uv[0], uv[1], r * 0.75f, s.core(), 0.75f * al);
				disc(ADD_Q, at, uv[0], uv[1], r * 0.35f, s.hot(), 0.8f * al);
			}
		}

		/** Sparks, embers and a little smoke at the hit (once a tick per beam, capped across all beams), and the scorch decal. */
		void sparks(Req r, State st) {
			long t = level.getGameTime();
			if (st.sparkTick == t || particlesThisTick >= MAX_PARTICLES_PER_TICK) {
				return;
			}
			if (now - st.start < GROW_TICKS) {
				return;
			}
			st.sparkTick = t;
			spawnAt(r.hit(), r.dir(), r.style(), r.width());
			if (r.hit().face() != null && st.decalTick != t && t % 2 == 0) {
				st.decalTick = t;
				addDecal(r.hit(), r.style(), r.width());
			}
		}

		void decal(Decal d) {
			float age = now - d.born;
			float life = 1f - age / DECAL_LIFE;
			Vec3 n = Vec3.atLowerCornerOf(d.face.getNormal());
			Vec3[] uv = faceAxes(d.face);
			Vec3 at = d.pos.add(n.scale(0.006));
			disc(GLOW_Q, at, uv[0], uv[1], d.size, 0x140804, 0.6f * life);
			float molten = 1f - age / 30f;
			if (molten > 0f) {
				disc(ADD_Q, at.add(n.scale(0.002)), uv[0], uv[1], d.size * 0.7f, d.style.glow(), 0.55f * molten * molten);
			}
		}

		// ------------------------------------------------------------ where the beams leave

		/** Third person: each eye from the drawn head (exact for every pose), or a head-pivot estimate if it wasn't drawn. */
		Vec3[] eyeOrigins(Player p, Head h, boolean brow) {
			if (h != null) {
				if (brow) {
					return new Vec3[] { headPoint(h, 0f, -6.2f, -4.4f) };
				}
				return new Vec3[] { headPoint(h, -1.5f, -3.5f, -4.4f), headPoint(h, 1.5f, -3.5f, -4.4f) };
			}
			Vec3 eye = p.getEyePosition(partial);
			Vec3 look = p.getViewVector(partial);
			Vec3 right = rightOf(look);
			Vec3 up = right.cross(look).normalize();
			double s = p.getScale() * 0.9375 / 16.0;
			Vec3 pivot = eye.subtract(0, 3.5 * s, 0);
			if (brow) {
				return new Vec3[] { pivot.add(up.scale(6.2 * s)).add(look.scale(4.4 * s)) };
			}
			Vec3 c = pivot.add(up.scale(3.5 * s)).add(look.scale(4.4 * s));
			return new Vec3[] { c.add(right.scale(1.5 * s)), c.add(right.scale(-1.5 * s)) };
		}

		/**
		 * First person: from your eyes. v0.17.5 (user: "it looks like the beam is coming from the players chest, i want it to look
		 * like the beam is coming from the players eyes"): the beams used to start just off the lower screen edge, which reads as the
		 * chest. Like other first-person eye beams, they now start at the real eye positions -- a player's eyes sit 1.5 px either side
		 * of the face centre (about 0.09 blocks) at eye height -- pushed a little in front of the camera so they're on screen: two
		 * short beams entering from either side of the view at eye level and meeting at the crosshair. The brow beam (Vision) starts
		 * dead centre a touch above eye level.
		 */
		Vec3[] fpOrigins(Player p, boolean brow) {
			// v0.17.7: pinned to the screen through this frame's projection (no wobble from bobbing, hurt tilt or FOV changes)
			if (brow) {
				Vec3 c = onScreen(0.0, FP_BROW_UP, FP_EYE_FORWARD);
				if (c != null) {
					return new Vec3[] { c };
				}
			} else {
				Vec3 r = onScreen(FP_EYE_SIDE, -FP_EYE_DOWN, FP_EYE_FORWARD);
				Vec3 l = onScreen(-FP_EYE_SIDE, -FP_EYE_DOWN, FP_EYE_FORWARD);
				if (r != null && l != null) {
					return new Vec3[] { r, l };
				}
			}
			Vec3 eye = p.getEyePosition(partial);
			Vec3 look = p.getViewVector(partial);
			Vec3 right = rightOf(look);
			Vec3 up = right.cross(look).normalize();
			double fwd = FP_EYE_FORWARD;
			double tanV = Math.tan(Math.toRadians(mc.options.fov().get() * 0.5));
			var win = mc.getWindow();
			double tanH = tanV * Math.max(0.5, win.getWidth() / (double) Math.max(1, win.getHeight()));
			Vec3 base = eye.add(look.scale(fwd));
			if (brow) {
				return new Vec3[] { base.add(up.scale(fwd * tanV * FP_BROW_UP)) };
			}
			Vec3 lowered = base.subtract(up.scale(fwd * tanV * FP_EYE_DOWN));
			double side = fwd * tanH * FP_EYE_SIDE;
			return new Vec3[] { lowered.add(right.scale(side)), lowered.add(right.scale(-side)) };
		}

		Vec3 headPoint(Head h, float x, float y, float z) {
			Vector3f v = h.m.transformPosition(new Vector3f(x / 16f, y / 16f, z / 16f), new Vector3f());
			return h.cam.add(v.x(), v.y(), v.z());
		}

		/** Soft glowing eyes on the face while firing (and a small flare in front of each). */
		void eyeGlow(Player p, Head h, Vec3[] eyes, Style s, float alpha, float w) {
			if (p.isInvisible()) {
				return;
			}
			float fl = 0.85f + 0.15f * Mth.sin(now * 2.7f);
			if (h != null) {
				Vec3 o = headPoint(h, 0f, 0f, 0f);
				Vec3 ux = headPoint(h, 1f, 0f, 0f).subtract(o);
				Vec3 uy = headPoint(h, 0f, 1f, 0f).subtract(o);
				float[] xs = eyes.length == 1 ? new float[] { 0f } : new float[] { -1.5f, 1.5f };
				float y = eyes.length == 1 ? -6.2f : -3.5f;
				for (float x : xs) {
					Vec3 c = headPoint(h, x, y, -4.06f);
					disc(ADD_Q, c, ux, uy, 1.6f, s.core(), 0.8f * alpha * fl);
					disc(ADD_Q, c, ux, uy, 0.8f, s.hot(), alpha);
				}
			}
			for (Vec3 e : eyes) {
				disc(GLOW_Q, e, camLeft, camUp, 0.13f * Math.max(0.8f, w), s.glow(), 0.35f * alpha * fl);
				disc(ADD_Q, e, camLeft, camUp, 0.05f * Math.max(0.8f, w), s.hot(), 0.9f * alpha);
			}
		}

		// ------------------------------------------------------------ geometry (camera-relative)

		/** A ribbon from {@code a} to {@code b} facing the camera, bright down the middle and fading to nothing at its edges. */
		void soft(List<Quad> out, Vec3 a, Vec3 b, float wa, float wb, int rgb, float aa, float ab) {
			if (aa <= 0.003f && ab <= 0.003f) {
				return;
			}
			Vec3 d = b.subtract(a);
			if (d.lengthSqr() < 1.0e-8) {
				return;
			}
			Vec3 mid = a.add(b).scale(0.5);
			Vec3 side = d.cross(cam.subtract(mid));
			if (side.lengthSqr() < 1.0e-10) {
				side = d.cross(new Vec3(0, 1, 0));
				if (side.lengthSqr() < 1.0e-10) {
					side = new Vec3(1, 0, 0);
				}
			}
			side = side.normalize();
			Vec3 a1 = a.add(side.scale(wa)), a2 = a.subtract(side.scale(wa));
			Vec3 b1 = b.add(side.scale(wb)), b2 = b.subtract(side.scale(wb));
			quad(out, a1, 0f, a, aa, b, ab, b1, 0f, rgb);
			quad(out, a, aa, a2, 0f, b2, 0f, b, ab, rgb);
		}

		/** A soft disc (bright centre, fading rim) in the plane of {@code u}/{@code v}. */
		void disc(List<Quad> out, Vec3 c, Vec3 u, Vec3 v, float r, int rgb, float alpha) {
			if (alpha <= 0.003f || r <= 0f) {
				return;
			}
			int n = 10;
			Vec3 prev = c.add(u.scale(r));
			for (int i = 1; i <= n; i++) {
				double ang = i * (Math.PI * 2.0 / n);
				Vec3 cur = c.add(u.scale(Math.cos(ang) * r)).add(v.scale(Math.sin(ang) * r));
				quad(out, c, alpha, prev, 0f, cur, 0f, cur, 0f, rgb);
				prev = cur;
			}
		}

		void quad(List<Quad> out, Vec3 p0, float a0, Vec3 p1, float a1, Vec3 p2, float a2, Vec3 p3, float a3, int rgb) {
			Quad q = new Quad();
			put(q, 0, p0, a0, rgb);
			put(q, 1, p1, a1, rgb);
			put(q, 2, p2, a2, rgb);
			put(q, 3, p3, a3, rgb);
			out.add(q);
		}

		void put(Quad q, int v, Vec3 p, float a, int rgb) {
			q.xyz[v * 3] = (float) (p.x - cam.x);
			q.xyz[v * 3 + 1] = (float) (p.y - cam.y);
			q.xyz[v * 3 + 2] = (float) (p.z - cam.z);
			q.argb[v] = (Mth.clamp((int) (a * 255f), 0, 255) << 24) | (rgb & 0xFFFFFF);
		}

		float flicker(float seed) {
			return 0.9f + 0.065f * Mth.sin(now * 2.9f + seed * 1.7f) + 0.045f * Mth.sin(now * 7.7f + seed * 3.1f);
		}
	}

	// ================================================================ particles and scorches

	private static void spawnAt(Hit hit, Vec3 dir, Style s, float width) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null || particlesThisTick >= MAX_PARTICLES_PER_TICK) {
			return;
		}
		long k = Mth.floor(hit.pos().x * 3) * 73856093L ^ Mth.floor(hit.pos().y * 3) * 19349663L ^ Mth.floor(hit.pos().z * 3) * 83492791L;
		if (!SPARKED.add(k)) {
			return; // two beams on one spot (both eyes, or two shooters): one spray
		}
		Vec3 n = hit.face() != null ? Vec3.atLowerCornerOf(hit.face().getNormal()) : dir.scale(-1);
		Vec3 at = hit.pos().add(n.scale(0.05));
		int sparks = width > 1.6f ? 3 : 2;
		for (int i = 0; i < sparks && particlesThisTick < MAX_PARTICLES_PER_TICK; i++, particlesThisTick++) {
			Vec3 v = n.scale(0.06 + RNG.nextDouble() * 0.08).add((RNG.nextDouble() - 0.5) * 0.16, RNG.nextDouble() * 0.1, (RNG.nextDouble() - 0.5) * 0.16);
			level.addParticle(s.spark(), at.x, at.y, at.z, v.x, v.y, v.z);
		}
		if (RNG.nextFloat() < 0.12f && particlesThisTick < MAX_PARTICLES_PER_TICK) {
			particlesThisTick++;
			level.addParticle(s.ember(), at.x, at.y, at.z, n.x * 0.05, 0.04, n.z * 0.05);
		}
		if (RNG.nextFloat() < 0.1f && particlesThisTick < MAX_PARTICLES_PER_TICK) {
			particlesThisTick++;
			level.addParticle(ParticleTypes.SMOKE, at.x, at.y, at.z, n.x * 0.02, 0.03, n.z * 0.02);
		}
	}

	private static void addDecal(Hit hit, Style s, float width) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return;
		}
		float now = mc.level.getGameTime();
		for (Decal d : DECALS) {
			if (d.face == hit.face() && d.pos.distanceToSqr(hit.pos()) < 0.12 * 0.12) {
				d.born = now; // still burning the same spot: keep it hot
				return;
			}
		}
		if (DECALS.size() >= MAX_DECALS) {
			DECALS.remove(0);
		}
		DECALS.add(new Decal(hit.pos(), hit.face(), s, now, 0.16f + 0.06f * Math.min(2.5f, width)));
	}

	private static Vec3[] faceAxes(Direction face) {
		return switch (face.getAxis()) {
			case X -> new Vec3[] { new Vec3(0, 0, 1), new Vec3(0, 1, 0) };
			case Y -> new Vec3[] { new Vec3(1, 0, 0), new Vec3(0, 0, 1) };
			case Z -> new Vec3[] { new Vec3(1, 0, 0), new Vec3(0, 1, 0) };
		};
	}

	private static Vec3 rightOf(Vec3 dir) {
		Vec3 right = dir.cross(new Vec3(0, 1, 0));
		return right.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : right.normalize();
	}

	// ================================================================ first person: the warm screen-edge glow

	private static void hud(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		boolean on = fpFrame >= frameNo - 1 && fpStrength > 0.02f && mc.options.getCameraType().isFirstPerson();
		float target = on ? fpStrength : 0f;
		// eases in and out over a few frames instead of snapping
		hudGlow += (target - hudGlow) * (target > hudGlow ? 0.25f : 0.12f);
		if (hudGlow < 0.01f) {
			return;
		}
		int w = g.guiWidth();
		int h = g.guiHeight();
		float pulse = 0.9f + 0.1f * Mth.sin((mc.level == null ? 0 : mc.level.getGameTime()) * 0.6f + delta.getGameTimeDeltaPartialTick(false) * 0.6f);
		int edge = Mth.clamp((int) (255 * 0.2f * hudGlow * pulse), 0, 255);
		int rgb = fpColour & 0xFFFFFF;
		int clear = rgb;
		// v0.17.5 round 2: the glow sits where the beams come in -- a soft warm bloom at each side edge at eye level (the old glow
		// along the bottom edge made the beams read as coming from the chest) -- plus a faint warm tint along the top edge
		int c = (Mth.clamp((int) (edge * 1.5f), 0, 255) << 24) | rgb;
		int mid = (int) (h * (0.5f + 0.5f * (float) FP_EYE_DOWN));
		int bandUp = (int) (h * 0.22f), bandDown = (int) (h * 0.22f);
		int side = (int) (w * 0.12f);
		VertexConsumer vc = g.bufferSource().getBuffer(RenderType.gui());
		Matrix4f m = g.pose().last().pose();
		for (int sgn = -1; sgn <= 1; sgn += 2) {
			float xe = sgn < 0 ? 0 : w, xi = sgn < 0 ? side : w - side;
			// two quads meeting at the edge midpoint: bright there, fading inward and up/down (a soft half-diamond)
			quadGui(vc, m, xe, mid - bandUp, clear, xe, mid, c, xi, mid, clear, xi, mid - bandUp, clear, sgn > 0);
			quadGui(vc, m, xe, mid, c, xe, mid + bandDown, clear, xi, mid + bandDown, clear, xi, mid, clear, sgn > 0);
		}
		g.flush();
		g.fillGradient(0, 0, w, (int) (h * 0.1f), (((int) (edge * 0.5f)) << 24) | rgb, clear);
	}

	/** One GUI quad with a colour per corner, wound counter-clockwise on screen ({@code flip} reverses a mirrored one). */
	private static void quadGui(VertexConsumer vc, Matrix4f m, float x0, float y0, int c0, float x1, float y1, int c1, float x2, float y2, int c2, float x3,
			float y3, int c3, boolean flip) {
		if (flip) {
			vc.addVertex(m, x3, y3, 0).setColor(c3);
			vc.addVertex(m, x2, y2, 0).setColor(c2);
			vc.addVertex(m, x1, y1, 0).setColor(c1);
			vc.addVertex(m, x0, y0, 0).setColor(c0);
		} else {
			vc.addVertex(m, x0, y0, 0).setColor(c0);
			vc.addVertex(m, x1, y1, 0).setColor(c1);
			vc.addVertex(m, x2, y2, 0).setColor(c2);
			vc.addVertex(m, x3, y3, 0).setColor(c3);
		}
	}
}
