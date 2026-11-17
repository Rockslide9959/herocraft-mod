package com.projecthero.mod.client.symbiote;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.symbiote.SymbioteAnim;
import com.projecthero.mod.symbiote.SymbioteSuitStyle;
import com.projecthero.mod.symbiote.SymbioteVitals;
import com.projecthero.mod.symbiote.item.SymbioteHostArmorItem;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import software.bernie.geckolib.cache.object.GeoBone;

/**
 * v0.17.5 (user: "make the mouth animated, the tongue moves and stuff, try to make it look good"): the Lethal Protector suit's 3D maw.
 * The geo ({@code geo/symbiote_host_lethal.geo.json}, made by {@code scratchpad/symbiote/make_lethal_protector.js}) carries, under the
 * head:
 * <ul>
 *   <li>{@code lp_upper} -- the upper teeth (eleven, two long fangs, three more round each corner of the grin) on a dark gum;</li>
 *   <li>{@code lp_jaw} -- the lower jaw: a shell over the bottom of the face wearing the skin's own chin and lower lip, its teeth, hinged
 *   back inside the head so it drops and swings down when it opens. The face behind it is painted as a throat, so an open mouth shows
 *   a dark red maw;</li>
 *   <li>{@code lp_tongue_0..5} -- a chain of six tapering segments lying in the jaw, each hinged on the one before. The chain slides
 *   out of the mouth ({@link Pose#ext}, px) and every joint bends (pitch / yaw), so it lolls, sways, curls and whips.</li>
 * </ul>
 *
 * <p>Everything is procedural, from synced state only, so every viewer sees the same thing and nothing is stored per frame:
 * <ul>
 *   <li><b>idle</b>: the jaw breathes open and shut a few degrees; now and then (a per-player schedule) the tongue lolls out over the
 *   lower teeth for about four seconds, swaying, with a drip of drool;</li>
 *   <li><b>snarl</b>: a melee swing, being hurt, or any Symbiote move (the synced {@link SymbioteVitals#animId} / {@code animStart})
 *   throws the jaw wide with a tremble and lifts the upper lip; Onslaught's charge holds the roar for as long as it charges;</li>
 *   <li><b>lash</b>: landing a melee hit, or a tendril strike / grapple / grab / lunge, whips the tongue out about six pixels with a
 *   travelling S-wave and a flick of the tip, then reels it back in.</li>
 * </ul>
 * Swings, hurts and hits are noticed in a client tick ({@link #tick}) and kept per entity id in {@link #TRACKS} (ids, not entities:
 * nothing can hold a world; cleared on disconnect and pruned as players leave). Drool is a client particle spawned from the same tick.
 */
public final class SymbioteMouth {
	/** Jaw / tongue joints are written as GeckoLib rotations; in the armour's geo space a negative X rotation drops the jaw. */
	static final float JAW_SIGN = -1f;
	/** A positive pitch here bends a tongue joint down; GeckoLib's X axis runs the other way in geo space. */
	static final float PITCH_SIGN = -1f;
	/** The tongue chain's slide when fully in: the tip sits just behind the face. */
	static final float TONGUE_IN = 4.0f;
	static final int SEGMENTS = 6;
	static final int SNARL_TICKS = 26;
	static final int LASH_TICKS = 20;
	static final int LOLL_PERIOD = 260;
	static final int LOLL_TICKS = 90;

	/** One frame's mouth pose. Angles in degrees. */
	public static final class Pose {
		public float jaw;
		public float upperLift;
		public float ext;
		public final float[] pitch = new float[SEGMENTS];
		public final float[] yaw = new float[SEGMENTS];
		public float loll;
		public float snarl;
		public float lash;

		void reset() {
			jaw = upperLift = ext = loll = snarl = lash = 0f;
			for (int k = 0; k < SEGMENTS; k++) {
				pitch[k] = 0f;
				yaw[k] = 0f;
			}
		}
	}

	/** Per entity id: when it last swung, was hurt or landed a hit (game time). */
	static final class Track {
		long swingAt = Long.MIN_VALUE / 2;
		long hurtAt = Long.MIN_VALUE / 2;
		long hitAt = Long.MIN_VALUE / 2;
		long hitCheckUntil;
		long seen;
	}

	private static final Int2ObjectMap<Track> TRACKS = new Int2ObjectOpenHashMap<>();
	private static final Pose SCRATCH = new Pose();
	/** The last pose computed (render thread only): reused by the eight mouth bones of the same wearer in the same frame. */
	private static int lastId = Integer.MIN_VALUE;
	private static double lastT = Double.NaN;

	private SymbioteMouth() {
	}

	public static void initialize() {
		ClientTickEvents.END_CLIENT_TICK.register(SymbioteMouth::tick);
	}

	/** Drops every track (leaving a world). */
	public static void clear() {
		TRACKS.clear();
		lastId = Integer.MIN_VALUE;
		lastT = Double.NaN;
	}

	/** Is {@code p} wearing the Lethal Protector suit (helmet on, style chosen, textures in this build)? */
	public static boolean wearsLethal(Player p) {
		return p.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof SymbioteHostArmorItem
				&& SymbioteSuitTint.styleOf(p) == SymbioteSuitStyle.LETHAL_PROTECTOR;
	}

	// ------------------------------------------------------------------ the bones (called from SuperheroArmorRenderer#renderRecursively)

	/**
	 * Poses one {@code lp_*} bone for {@code wearer} this frame. Returns false when the bone (and its children) should not be drawn: the
	 * tongue while it is all the way in, and the whole mouth until the suit has covered the head on the way on.
	 */
	public static boolean applyBone(GeoBone bone, Player wearer, float partialTick) {
		float grow = 1f;
		Pose pose = SCRATCH;
		if (wearer != null) {
			if (SymbioteReveal.isRevealing(wearer)) {
				float pr = SymbioteReveal.progress(wearer, partialTick);
				grow = smooth((pr - 0.93f) / 0.06f);
				if (grow <= 0.01f) {
					return false;
				}
			}
			double t = wearer.level().getGameTime() + partialTick;
			if (wearer.getId() != lastId || t != lastT) {
				compute(wearer, partialTick, SCRATCH);
				lastId = wearer.getId();
				lastT = t;
			}
		} else {
			pose.reset();
			lastId = Integer.MIN_VALUE;
		}
		String name = bone.getName();
		switch (name) {
			case "lp_jaw" -> {
				bone.setRotX(JAW_SIGN * rad(pose.jaw));
				bone.setScaleX(grow);
				bone.setScaleY(grow);
				bone.setScaleZ(grow);
			}
			case "lp_upper" -> {
				bone.setPosY(pose.upperLift);
				bone.setScaleX(grow);
				bone.setScaleY(grow);
				bone.setScaleZ(grow);
			}
			default -> {
				if (!name.startsWith("lp_tongue_")) {
					return true;
				}
				int k = name.charAt(name.length() - 1) - '0';
				if (k < 0 || k >= SEGMENTS) {
					return true;
				}
				if (k == 0) {
					if (pose.ext < 0.15f || grow < 0.99f) {
						return false;
					}
					bone.setPosZ(TONGUE_IN - pose.ext);
				}
				bone.setRotX(PITCH_SIGN * rad(pose.pitch[k]));
				bone.setRotY(rad(pose.yaw[k]));
			}
		}
		return true;
	}

	// ------------------------------------------------------------------ the animation

	/** The pose of {@code p}'s mouth at this instant. Pure function of synced state + the tracks. */
	public static Pose compute(Player p, float partialTick, Pose out) {
		out.reset();
		long now = p.level().getGameTime();
		double t = now + partialTick;
		int id = p.getId();
		double phase = ((id * 0.6180339887) % 1.0) * Math.PI * 2;
		Track tr = TRACKS.get(id);

		// idle breathing: a few degrees open and shut
		double breath = 0.5 + 0.5 * Math.sin(t * Math.PI * 2 / 68 + phase);
		out.jaw = (float) (0.6 + 2.8 * Math.pow(breath, 1.5));

		// the loll: now and then the tongue hangs out over the lower teeth and sways
		double local = ((t + Math.floorMod(id * 97, LOLL_PERIOD)) % LOLL_PERIOD + LOLL_PERIOD) % LOLL_PERIOD;
		float loll = local < LOLL_TICKS ? smooth((float) local / 18f) * (1f - smooth(((float) local - 66f) / 24f)) : 0f;
		out.loll = loll;

		// snarls: the strongest of a melee swing, a hurt and a Symbiote move
		SymbioteVitals v = p.getAttachedOrElse(ModAttachments.SYMBIOTE_VITALS, null);
		int animId = v == null ? SymbioteAnim.NONE : v.animId;
		long animStart = v == null ? 0L : v.animStart;
		float snarl = 0f;
		float lash = 0f;
		float lashAge = 0f;
		if (tr != null) {
			snarl = Math.max(snarl, 0.75f * snarlEnv((float) (t - tr.swingAt)));
			snarl = Math.max(snarl, 0.6f * snarlEnv((float) (t - tr.hurtAt)));
			float la = (float) (t - tr.hitAt);
			if (la >= 0 && la < LASH_TICKS) {
				lash = 1f;
				lashAge = la;
			}
		}
		if (animId != SymbioteAnim.NONE) {
			float a = (float) (t - animStart);
			if (isLashMove(animId)) {
				if (a >= 0 && a < LASH_TICKS && (lash == 0f || a < lashAge)) {
					lash = 1f;
					lashAge = a;
				}
				snarl = Math.max(snarl, 0.55f * snarlEnv(a));
			} else if (animId == SymbioteAnim.ONSLAUGHT_CHARGE) {
				snarl = Math.max(snarl, smooth(a / 6f)); // hold the roar while it charges
			} else {
				snarl = Math.max(snarl, (animId == SymbioteAnim.RESURRECT ? 1.15f : 1f) * snarlEnv(a));
			}
		}
		out.snarl = snarl;

		// the lash: out fast with a little overshoot, hold with a flick, reel back in
		float ext = 0f;
		float lashEnv = 0f;
		if (lash > 0f) {
			float a = lashAge;
			if (a < 4f) {
				ext = 7f * easeOutBack(a / 4f);
			} else if (a < 8f) {
				ext = 7f + 0.3f * (float) Math.sin((a - 4f) * 1.6f);
			} else {
				ext = 7f * (1f - easeInCubic((a - 8f) / (LASH_TICKS - 8f)));
			}
			lashEnv = Math.max(0f, Math.min(1f, ext / 7f));
			float open = a < 3f ? a / 3f : a < 14f ? 1f : 1f - (a - 14f) / 6f;
			out.jaw = Math.max(out.jaw, 24f * Math.max(0f, open));
		}
		out.lash = lashEnv;

		// the jaw and upper lip
		float tremble = (float) Math.sin(t * 2.6 + phase) * 1.6f * snarl;
		out.jaw = Math.max(out.jaw, 13f * loll);
		out.jaw = Math.max(out.jaw, 31f * Math.min(1.15f, snarl) + tremble);
		out.upperLift = 0.28f * Math.min(1f, snarl);

		// the tongue: the loll's lazy droop and sway, a snarl's raised curl behind the teeth, the lash's whip
		float lollExt = 3.4f * loll;
		float snarlExt = 1.1f * Math.min(1f, snarl);
		out.ext = Math.max(Math.max(lollExt, snarlExt), ext);
		float wLash = lashEnv;
		float wRest = 1f - wLash;
		for (int k = 0; k < SEGMENTS; k++) {
			float kk = k / (float) (SEGMENTS - 1);
			// loll: each joint droops a little more toward the tip, sways in a slow S, and the tip curls up now and then
			float lp = (4f + 3f * k) * loll;
			float curl = k >= SEGMENTS - 2 ? -12f * loll * (float) Math.max(0.0, Math.sin(t * 0.09 + phase)) : 0f;
			float ly = 7f * loll * (float) Math.sin(t * 0.13 + k * 0.7 + phase);
			// snarl: the tongue lifts and curls back behind the teeth
			float sp = -6f * Math.min(1f, snarl) * (0.5f + kk);
			// lash: a travelling wave down the chain, and a flick of the tip as it peaks
			float a = lashAge;
			float lyLash = 16f * (float) Math.sin(a * 1.7f - k * 0.95f) * lashEnv * (0.4f + 0.6f * kk);
			float flick = k >= SEGMENTS - 2 ? -22f * bump(a, 5.5f, 2.2f) : 0f;
			float lpLash = -3f * lashEnv + flick + (a > 8f ? 3f * lashEnv : 0f);
			float restPitch = loll >= Math.min(1f, snarl) ? lp + curl : sp + lp * 0.3f;
			out.pitch[k] = wRest * restPitch + wLash * lpLash;
			out.yaw[k] = wRest * ly + wLash * lyLash;
		}
		return out;
	}

	static boolean isLashMove(int animId) {
		return animId == SymbioteAnim.TENDRIL_STRIKE || animId == SymbioteAnim.GRAPPLE || animId == SymbioteAnim.GRAB
				|| animId == SymbioteAnim.LUNGE;
	}

	/** Snarl envelope over {@link #SNARL_TICKS}: up in 4 ticks, held to 11, eased shut by 26. */
	static float snarlEnv(float a) {
		if (a < 0f || a >= SNARL_TICKS) {
			return 0f;
		}
		if (a < 4f) {
			float u = a / 4f;
			return 1f - (1f - u) * (1f - u) * (1f - u);
		}
		if (a < 11f) {
			return 1f;
		}
		return 1f - smooth((a - 11f) / 15f);
	}

	// ------------------------------------------------------------------ the tick: swings, hurts, hits and drool

	private static void tick(Minecraft mc) {
		if (mc.level == null) {
			if (!TRACKS.isEmpty()) {
				clear();
			}
			return;
		}
		if (mc.isPaused()) {
			return;
		}
		long now = mc.level.getGameTime();
		for (Player p : mc.level.players()) {
			if (!wearsLethal(p)) {
				continue;
			}
			Track tr = TRACKS.computeIfAbsent(p.getId(), k -> new Track());
			tr.seen = now;
			// a swing just started (remote players get swingTime -1 -> 0 from the animate packet)
			if (p.swinging && p.swingTime <= 1 && now - tr.swingAt > 4) {
				tr.swingAt = now - Math.max(0, p.swingTime);
				tr.hitCheckUntil = now + 6;
			}
			if (p.hurtTime >= 9 && now - tr.hurtAt > 10) {
				tr.hurtAt = now;
			}
			// a hit: something in front of them took damage right after the swing
			if (now <= tr.hitCheckUntil && now - tr.hitAt > 22) {
				Vec3 look = p.getViewVector(1f);
				Vec3 eye = p.getEyePosition();
				for (LivingEntity e : mc.level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(5.0),
						e -> e != p && e.hurtTime > 0 && e.hurtTime >= e.hurtDuration - 4)) {
					Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
					if (to.lengthSqr() < 1e-4 || to.normalize().dot(look) > 0.25) {
						tr.hitAt = now;
						tr.hitCheckUntil = 0;
						break;
					}
				}
			}
			drool(mc, p, now);
		}
		if (now % 100 == 0) {
			TRACKS.int2ObjectEntrySet().removeIf(en -> now - en.getValue().seen > 200);
		}
	}

	/** Now and then a drop of drool falls from the mouth -- far more often while the tongue lolls. */
	private static void drool(Minecraft mc, Player p, long now) {
		if (p == mc.player && mc.options.getCameraType().isFirstPerson()) {
			return;
		}
		if (p.isInvisible() || SymbioteReveal.isRevealing(p)) {
			return;
		}
		Pose pose = compute(p, 0f, new Pose());
		float chance = pose.loll > 0.3f ? 1f / 16f : pose.lash > 0.2f ? 1f / 6f : 1f / 320f;
		if (p.getRandom().nextFloat() >= chance) {
			return;
		}
		Vec3 fwd = Vec3.directionFromRotation(p.getXRot(), p.yHeadRot);
		Vec3 up = Vec3.directionFromRotation(p.getXRot() - 90f, p.yHeadRot);
		Vec3 side = fwd.cross(up);
		double s = (p.getRandom().nextDouble() - 0.5) * 0.12;
		Vec3 at = p.getEyePosition().add(fwd.scale(0.33)).add(up.scale(-0.1)).add(side.scale(s));
		mc.level.addParticle(ParticleTypes.ITEM_SLIME, at.x, at.y, at.z, 0.0, -0.02, 0.0);
	}

	// ------------------------------------------------------------------ easing

	static float smooth(float x) {
		float u = Math.max(0f, Math.min(1f, x));
		return u * u * (3f - 2f * u);
	}

	static float easeOutBack(float x) {
		float u = Math.max(0f, Math.min(1f, x)) - 1f;
		float c = 1.6f;
		return 1f + (c + 1f) * u * u * u + c * u * u;
	}

	static float easeInCubic(float x) {
		float u = Math.max(0f, Math.min(1f, x));
		return u * u * u;
	}

	/** A soft bump centred on {@code at}, {@code width} ticks to either side. */
	static float bump(float a, float at, float width) {
		float d = (a - at) / width;
		return Math.abs(d) >= 1f ? 0f : (float) (0.5 + 0.5 * Math.cos(Math.PI * d));
	}

	private static float rad(float deg) {
		return deg * (float) (Math.PI / 180.0);
	}
}
