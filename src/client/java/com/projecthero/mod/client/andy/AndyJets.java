package com.projecthero.mod.client.andy;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.andy.Andy;
import com.projecthero.mod.andy.AndyFlight;
import com.projecthero.mod.andy.data.AndyState;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;

/**
 * v0.17.3 (user: "make it look like actual blood bursting out of the players body"): Blood Propulsion's jets as arterial
 * spurts, not exhaust. One heart drives every stump: a sharp surge then a decay, ~3.5 beats a second (~4.5 boosting).
 * Each jet is a chain of liquid parcels leaving the cut face, each with the speed of the beat it left on; the fast surge
 * parcels catch up with the slow ones (the slugs and bulges of a pulsing jet), stay one opaque rope near the stump and
 * then break into globs that keep their momentum and fall. The rope is lit, opaque, dark at its core and underside with a
 * glossy streak along its top. Around it {@link BloodFx} carries the pieces that leave it (drops, motion-stretched
 * streaks, globs that splat, mist only at the peaks, drips between beats). Take-off erupts, the boost surges and throws
 * blood and flesh off the burning edges, landing sputters out in a few weak spurts and dribbles.
 *
 * <p>Everything is worked out from the synced state and the game time (the heart's phase included), so every viewer sees
 * the same jets. Rendering allocates nothing per parcel (scratch arrays); particles are BloodFx's pooled ones.
 */
public final class AndyJets {
	private static final RandomSource RND = RandomSource.create();

	/** Per player: flying last tick, take-off tick, landing tick, boosting last tick, boost start tick. */
	private static final Map<UUID, long[]> SEEN = new HashMap<>();

	/** How long after landing the stumps still sputter. */
	static final int SPUTTER_TICKS = 32;
	/** How long the take-off eruption keeps the jets violent. */
	static final int ERUPT_TICKS = 12;

	// liquid colours: the rope's glossy top, its sides, its underside, the specular streak
	private static final int TOP = 0xA8161A, SIDE = 0x6E0A0E, BOTTOM = 0x34030A, SPEC = 0xF26A55, CAP = 0x55060A;

	private AndyJets() {
	}

	static void reset() {
		SEEN.clear();
	}

	// ---------------------------------------------------------------- the heart

	/** Beats per tick: ~3.4 Hz cruising, ~4.4 Hz boosting. */
	static float period(boolean boost) {
		return boost ? 4.5f : 5.9f;
	}

	/**
	 * The heart's push at game time {@code t} (ticks): 0 between beats .. 1 at the surge. A steep rise, an exponential fall
	 * and a small second bump (the dicrotic notch) -- the rhythm of an artery, not a flicker.
	 */
	public static float pulse(float t, boolean boost) {
		float ph = t / period(boost);
		ph -= Mth.floor(ph);
		float p;
		if (ph < 0.12f) {
			float k = ph / 0.12f;
			p = k * k * (3f - 2f * k);
		} else {
			p = (float) Math.exp(-(ph - 0.12f) * 6.5f);
		}
		float n = (ph - 0.5f) / 0.07f;
		p += 0.28f * (float) Math.exp(-n * n);
		return Math.min(1f, p);
	}

	/** Which beat {@code t} falls in (for per-beat randomness: the landing's skipped beats). */
	static int beat(float t, boolean boost) {
		return Mth.floor(t / period(boost));
	}

	/** A stable 0..1 hash of a parcel / beat index and a seed (the same on every client). */
	static float hash(int i, int seed) {
		int h = i * 0x9E3779B1 + seed * 0x85EBCA77;
		h ^= h >>> 15;
		h *= 0x2C1B3C6D;
		h ^= h >>> 12;
		h *= 0x297A2D39;
		h ^= h >>> 15;
		return (h & 0xFFFFFF) / (float) 0x1000000;
	}

	// ---------------------------------------------------------------- the stumps

	/** The stumps of one player this frame: origin, outward direction, radius, power, a seed, the heart's lag. */
	private static final int MAX_STUMPS = 6;
	private static final double[] OX = new double[MAX_STUMPS], OY = new double[MAX_STUMPS], OZ = new double[MAX_STUMPS];
	private static final float[] DX = new float[MAX_STUMPS], DY = new float[MAX_STUMPS], DZ = new float[MAX_STUMPS];
	private static final float[] RAD = new float[MAX_STUMPS], POW = new float[MAX_STUMPS], LAG = new float[MAX_STUMPS];
	private static final int[] SEED = new int[MAX_STUMPS], PART = new int[MAX_STUMPS];
	private static int stumps;

	private static void add(Vec3 at, Vec3 dir, float rad, float power, int seed, int part, float lag) {
		if (stumps >= MAX_STUMPS || dir.lengthSqr() < 1.0e-8) {
			return;
		}
		Vec3 d = dir.normalize();
		OX[stumps] = at.x;
		OY[stumps] = at.y;
		OZ[stumps] = at.z;
		DX[stumps] = (float) d.x;
		DY[stumps] = (float) d.y;
		DZ[stumps] = (float) d.z;
		RAD[stumps] = rad;
		POW[stumps] = power;
		SEED[stumps] = seed;
		PART[stumps] = part;
		LAG[stumps] = lag;
		stumps++;
	}

	/** What a player's jets are doing right now. */
	enum Mode {
		NONE, FLYING, SPUTTER
	}

	static long[] seen(Player player) {
		return SEEN.computeIfAbsent(player.getUUID(), k -> new long[] { 0L, -1000L, -1000L, 0L, -1000L });
	}

	static Mode mode(AndyState s, long[] seen, float now) {
		if (s.flag(AndyState.FLAG_PROPELLING)) {
			return Mode.FLYING;
		}
		return now - seen[2] < SPUTTER_TICKS && now >= seen[2] ? Mode.SPUTTER : Mode.NONE;
	}

	/** Fills the stump arrays for {@code player}; returns the mode. */
	private static Mode collect(Player player, AndyState s, float now, Vec3 look) {
		stumps = 0;
		long[] seen = seen(player);
		Mode mode = mode(s, seen, now);
		if (mode == Mode.NONE) {
			return mode;
		}
		int id = player.getId() * 7;
		if (mode == Mode.SPUTTER) {
			// landed: the stumps hang where the pose puts them and the last spurts drop out of them, along the limb
			for (int part = AndyState.RIGHT_ARM; part <= AndyState.LEFT_LEG; part++) {
				AndyCuts.Cut cut = AndyCuts.of(s, part, now);
				if (cut == null || s.broken(part)) {
					continue;
				}
				float rows = cut.rows() + (12f - cut.rows()) * Mth.clamp(cut.q(), 0f, 1f);
				add(AndyBlood.endOf(player, part, rows), AndyBlood.axis(player, part), 0.07f, 1f, id + part, part, part >= AndyState.RIGHT_LEG ? 0.35f : 0f);
			}
			return mode;
		}
		float power = s.flag(AndyState.FLAG_BOOST) ? 1.75f : 1f;
		Vec3 back = look.scale(-1);
		if (s.headOnly(now)) {
			Vec3[] neck = AndyBlood.stump(player, AndyState.HEAD);
			add(neck[0], back, 0.075f, power * 1.1f, id, AndyState.HEAD, 0f);
			return mode;
		}
		for (int part = AndyState.RIGHT_ARM; part <= AndyState.LEFT_LEG; part++) {
			float lag = part >= AndyState.RIGHT_LEG ? 0.35f : 0f; // the legs' arteries are a little further from the heart
			if (!s.broken(part)) {
				AndyCuts.Cut cut = AndyCuts.of(s, part, now);
				float rows = cut != null ? cut.rows() : 12f;
				add(AndyBlood.endOf(player, part, rows), AndyBlood.jetDir(AndyBlood.axis(player, part), look), 0.058f, power, id + part, part, lag);
			} else if (!s.broken(AndyState.BODY)) {
				Vec3[] st = AndyBlood.stump(player, part);
				add(st[0], AndyBlood.jetDir(st[1], look), 0.062f, power * 0.9f, id + part, part, lag);
			}
		}
		if (s.broken(AndyState.BODY)) {
			Vec3[] neck = AndyBlood.stump(player, AndyState.HEAD);
			add(neck[0], back, 0.072f, power, id, AndyState.HEAD, 0f);
		}
		return mode;
	}

	/** Extra violence: the take-off eruption and the boost's first surge, fading. */
	private static float violence(long[] seen, float now) {
		float v = 1f;
		float up = now - seen[1];
		if (up >= 0f && up < ERUPT_TICKS) {
			v += 1.1f * (1f - up / ERUPT_TICKS);
		}
		float b = now - seen[4];
		if (b >= 0f && b < 10f) {
			v += 0.6f * (1f - b / 10f);
		}
		return v;
	}

	/** The landing's sputter: how strong the jets still are (0 = done), with whole beats skipped as it dies. */
	private static float sputter(long[] seen, float now, float t, int seed) {
		float age = now - seen[2];
		if (age < 0f || age >= SPUTTER_TICKS) {
			return 0f;
		}
		float fade = (float) Math.exp(-age / 9f);
		int b = beat(t, false);
		float keep = 1f - age / SPUTTER_TICKS;
		return hash(b, seed + 77) < keep * 0.9f + 0.1f ? fade : fade * 0.08f;
	}

	// ---------------------------------------------------------------- tick: edges, particles, sounds

	/** From the client tick, for every Andy in view (after the cut / burst watch). */
	static void tick(Player player, AndyState s, long now, boolean firstPersonSelf) {
		long[] seen = seen(player);
		boolean flying = s.flag(AndyState.FLAG_PROPELLING);
		boolean boost = flying && s.flag(AndyState.FLAG_BOOST);
		Vec3 carry = player.getDeltaMovement();
		if (flying && seen[0] == 0L) {
			seen[1] = now;
			if (!firstPersonSelf) {
				takeoff(player, s, now, carry);
			}
		} else if (!flying && seen[0] != 0L) {
			seen[2] = now;
		}
		if (boost && seen[3] == 0L) {
			seen[4] = now;
		}
		seen[0] = flying ? 1L : 0L;
		seen[3] = boost ? 1L : 0L;
		if (firstPersonSelf) {
			return; // AndyFirstPerson's spray does the local view
		}
		Vec3 look = player.getLookAngle();
		Mode mode = collect(player, s, now, look);
		if (mode == Mode.NONE) {
			return;
		}
		float vio = violence(seen, now);
		for (int k = 0; k < stumps; k++) {
			float t = now - LAG[k];
			float p = pulse(t, boost);
			float pw = POW[k];
			if (mode == Mode.SPUTTER) {
				float sp = sputter(seen, now, t, SEED[k]);
				Vec3 at = new Vec3(OX[k], OY[k], OZ[k]);
				if (sp * p > 0.25f) {
					// a weak spurt: a few drops out of the stump that arc straight down
					Vec3 d = new Vec3(DX[k], DY[k], DZ[k]);
					BloodFx.spurt(at, d.add(0, 0.25, 0), 1 + (int) (3 * sp * p), 0.06 + 0.12 * sp, 0.3, Vec3.ZERO);
				}
				if (RND.nextFloat() < 0.22f + 0.3f * (1f - sp)) {
					BloodFx.drip(at); // dribbles off the cut face
				}
				continue;
			}
			emit(new Vec3(OX[k], OY[k], OZ[k]), new Vec3(DX[k], DY[k], DZ[k]), carry, p, pw * vio, boost);
		}
		// the boost burning him away: blood and flesh off every burning edge
		if (boost) {
			for (int part = AndyState.BODY; part < AndyState.PARTS; part++) {
				if (AndyFlight.burnt(s, part) <= 0f) {
					continue;
				}
				AndyCuts.Cut cut = AndyCuts.of(s, part, now);
				if (cut == null) {
					continue;
				}
				Vec3 edge = part == AndyState.BODY ? AndyBlood.at(player, part, 0f, cut.rows(), 0f) : AndyBlood.endOf(player, part, cut.rows());
				Vec3 out = AndyBlood.axis(player, part);
				BloodFx.spurt(edge, out.add(look.scale(-0.8)), 2, 0.22, 0.6, carry.scale(0.9));
				if (RND.nextFloat() < 0.55f) {
					BloodFx.flesh(edge, out.scale(0.08).add(look.scale(-0.12)).add(carry.scale(0.9)), 1);
				}
			}
		}
	}

	/** Blocks a tick the jet's liquid leaves the stump at (relative to Andy), before the beat's push. */
	private static float speed(boolean boost) {
		return boost ? 0.62f : 0.42f;
	}

	/** One tick of the spray off one jet: what breaks off the rope's end leaves it as world particles. */
	private static void emit(Vec3 at, Vec3 dir, Vec3 carry, float p, float power, boolean boost) {
		float v = speed(boost) * (0.55f + 0.75f * p);
		float breakAt = v * (0.9f + 1.6f * p);
		Vec3 from = at.add(dir.scale(breakAt * 0.9));
		int drops = (int) (0.6f + 2.6f * p * power + RND.nextFloat());
		for (int i = 0; i < drops; i++) {
			BloodFx.arterialDrop(from, dir, carry, v * (0.75f + RND.nextFloat() * 0.5f), 0.1f + 0.08f * power, false);
		}
		if (RND.nextFloat() < 0.16f * p * power) {
			BloodFx.arterialDrop(from, dir, carry, v * 0.9f, 0.06f, true); // a heavy glob: it splats where it lands
		}
		if (p > 0.85f && RND.nextFloat() < 0.12f * power) {
			BloodFx.mist(from.add(dir.scale(0.35)), 1, 0.08f + 0.03f * power, carry.add(dir.scale(v * 0.5)));
		}
		if (p < 0.18f && RND.nextFloat() < 0.3f) {
			BloodFx.drop(at.x, at.y, at.z, carry.x * 0.95, carry.y * 0.95 - 0.03, carry.z * 0.95, 0.028f + RND.nextFloat() * 0.018f,
					BloodFx.SHADES[1 + RND.nextInt(3)], 26, 0); // a drip off the cut face between beats
		}
	}

	/** Take-off: as the hands and feet blow off, each stump erupts (a crown of blood, globs, mist, flesh). */
	private static void takeoff(Player player, AndyState s, long now, Vec3 carry) {
		float cut = 12f - com.projecthero.mod.andy.AndyConfig.HAND_ROWS;
		for (int part = AndyState.RIGHT_ARM; part <= AndyState.LEFT_LEG; part++) {
			if (s.broken(part)) {
				continue;
			}
			Vec3 at = AndyBlood.endOf(player, part, cut);
			BloodFx.eruption(at, AndyBlood.axis(player, part), carry.scale(0.5), 1f);
		}
	}

	/** For the jet sound: the heart's push for this player right now (1 = loud surge). */
	public static float soundPulse(Player player) {
		AndyState s = Andy.peek(player);
		if (s == null || player.level() == null) {
			return 0.5f;
		}
		return pulse(player.level().getGameTime(), s.flag(AndyState.FLAG_BOOST));
	}

	// ---------------------------------------------------------------- render (third person / other players)

	static void render(WorldRenderContext context, PoseStack pose, Vec3 cam, Minecraft mc, float partial) {
		float now = mc.level.getGameTime() + partial;
		VertexConsumer vc = null;
		for (Player player : mc.level.players()) {
			AndyState s = Andy.peek(player);
			if (s == null || !s.hasPower) {
				continue;
			}
			if (player == mc.player && mc.options.getCameraType().isFirstPerson()) {
				continue; // drawn on the first-person arms instead
			}
			long[] seen = SEEN.get(player.getUUID());
			if (seen == null) {
				continue;
			}
			Mode mode = collect(player, s, now, player.getViewVector(partial));
			if (mode == Mode.NONE || stumps == 0) {
				continue;
			}
			if (vc == null) {
				vc = context.consumers().getBuffer(RenderType.entityCutoutNoCull(AndyRender.PALETTE));
			}
			boolean boost = s.flag(AndyState.FLAG_BOOST);
			float vio = violence(seen, now);
			PoseStack.Pose last = pose.last();
			for (int k = 0; k < stumps; k++) {
				int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(OX[k], OY[k], OZ[k]));
				float ox = (float) (OX[k] - cam.x), oy = (float) (OY[k] - cam.y), oz = (float) (OZ[k] - cam.z);
				float t = now - LAG[k];
				if (mode == Mode.SPUTTER) {
					float sp = sputter(seen, now, t, SEED[k]);
					if (sp > 0.02f) {
						rope(vc, last, ox, oy, oz, DX[k], DY[k], DZ[k], 0f, -1f, 0f, RAD[k] * 0.75f, 0.16f * sp + 0.03f, sp, 1f, t, false, SEED[k], light, 2.2f);
					}
					continue;
				}
				rope(vc, last, ox, oy, oz, DX[k], DY[k], DZ[k], 0f, -1f, 0f, RAD[k], speed(boost), POW[k], vio, t, boost, SEED[k], light, 3.5f);
			}
		}
	}

	// ---------------------------------------------------------------- the rope

	private static final float DT = 0.25f;
	private static final int MAX_PARCELS = 18;
	private static final float[] PXS = new float[MAX_PARCELS + 1], PYS = new float[MAX_PARCELS + 1], PZS = new float[MAX_PARCELS + 1];
	private static final float[] PW = new float[MAX_PARCELS + 1], PV = new float[MAX_PARCELS + 1], PAGE = new float[MAX_PARCELS + 1];
	private static final boolean[] PATT = new boolean[MAX_PARCELS + 1];
	private static final int[] PIDX = new int[MAX_PARCELS + 1];

	/**
	 * One jet of blood from ({@code ox, oy, oz}) along unit {@code d}, gravity along unit {@code g}, in the pose's space:
	 * parcels emitted every {@link #DT} ticks, each with its beat's speed and size; joined into one opaque rope while young,
	 * loose globs after. {@code r0} the stump's radius, {@code v0} blocks a tick, {@code power} the scale of everything,
	 * {@code vio} extra violence, {@code maxAge} how long (ticks) a parcel is drawn before BloodFx takes over.
	 */
	static void rope(VertexConsumer vc, PoseStack.Pose pose, float ox, float oy, float oz, float dx, float dy, float dz, float gx, float gy, float gz,
			float r0, float v0, float power, float vio, float t, boolean boost, int seed, int light, float maxAge) {
		// a frame round the jet
		float ax, ay, az;
		if (Math.abs(dy) > 0.9f) {
			ax = 1f;
			ay = 0f;
			az = 0f;
		} else {
			// d x up
			ax = -dz;
			ay = 0f;
			az = dx;
		}
		float al = Mth.sqrt(ax * ax + ay * ay + az * az);
		ax /= al;
		ay /= al;
		az /= al;
		float bx = dy * az - dz * ay, by = dz * ax - dx * az, bz = dx * ay - dy * ax;
		int n = Math.min(MAX_PARCELS, (int) (maxAge / DT));
		int newest = Mth.floor(t / DT);
		float strength = Math.min(1.6f, power);
		// parcel 0: the liquid at the cut face right now
		float p0 = pulse(t, boost);
		PXS[0] = ox;
		PYS[0] = oy;
		PZS[0] = oz;
		PW[0] = r0 * (0.8f + 0.35f * p0) * (0.9f + 0.1f * vio);
		PV[0] = v0;
		PAGE[0] = 0f;
		PATT[0] = true;
		PIDX[0] = newest + 1;
		for (int i = 1; i <= n; i++) {
			int idx = newest - (i - 1);
			float te = idx * DT;
			float age = t - te;
			float p = pulse(te, boost);
			float v = v0 * (0.68f + 0.5f * p) * (0.8f + 0.2f * strength) * (0.75f + 0.25f * vio);
			float along = v * age * (1f - 0.05f * age); // air drag: the old liquid slows
			float drop = 0.5f * 0.045f * age * age;
			// the jet wanders a little from beat to beat and each parcel spreads off the line as it goes
			float spread = (0.05f + 0.06f * (vio - 1f)) * v * age;
			float hx = (hash(idx, seed) - 0.5f) * 2f, hy = (hash(idx, seed + 31) - 0.5f) * 2f;
			float wob = Mth.sin(te * 0.9f + seed) * 0.03f * age * v;
			float sx = hx * spread + wob, sy = hy * spread;
			PXS[i] = ox + dx * along + gx * drop + ax * sx + bx * sy;
			PYS[i] = oy + dy * along + gy * drop + ay * sx + by * sy;
			PZS[i] = oz + dz * along + gz * drop + az * sx + bz * sy;
			// a surge is a fat slug; between beats the jet necks down thin; it swells a touch as it leaves the cut
			PW[i] = r0 * (0.42f + 0.62f * p) * (1f + 0.2f * Math.min(1f, age)) * (0.9f + 0.1f * vio) * (0.9f + 0.1f * strength);
			PV[i] = v;
			PAGE[i] = age;
			PIDX[i] = idx;
			// a surge holds together further before it breaks up
			PATT[i] = age < 0.9f + 1.7f * p + 0.4f * (vio - 1f);
		}
		Matrix4f m = pose.pose();
		// the rope: young parcels joined end to end
		for (int i = 1; i <= n; i++) {
			if (!PATT[i] || !PATT[i - 1]) {
				continue;
			}
			float w0 = PW[i - 1], w1 = PW[i];
			int top = i <= 2 ? 0xC21C1E : TOP; // freshest out of the artery: brightest
			// faint bands that travel with the liquid (each parcel a touch lighter or darker): the surface reads as moving
			float band = 0.88f + 0.24f * hash(PIDX[i], seed + 3);
			segment(vc, pose, m, PXS[i - 1], PYS[i - 1], PZS[i - 1], w0, PXS[i], PYS[i], PZS[i], w1, darken(top, band), darken(SIDE, band), BOTTOM, light, true);
		}
		// what has broken off: globs, some split in two, shrinking as they go (the spray carries on as BloodFx drops)
		for (int i = 1; i <= n; i++) {
			if (PATT[i]) {
				continue;
			}
			float age = PAGE[i];
			float h = hash(PIDX[i], seed + 7);
			if (h < 0.22f) {
				continue; // this one has already gone to droplets
			}
			float life = Mth.clamp(1f - (age - 1.2f) / (maxAge - 0.8f), 0f, 1f);
			float w = PW[i] * (0.45f + 0.3f * h) * life;
			if (w < 0.008f) {
				continue;
			}
			// along its own motion (out along the jet and falling), stretched by its speed
			float vx = dx * PV[i] + gx * 0.045f * age, vy = dy * PV[i] + gy * 0.045f * age, vz = dz * PV[i] + gz * 0.045f * age;
			float vl = Mth.sqrt(vx * vx + vy * vy + vz * vz);
			if (vl < 1.0e-4f) {
				continue;
			}
			float len = w * (1.2f + Math.min(2.2f, vl * 3.5f));
			vx /= vl;
			vy /= vl;
			vz /= vl;
			int shade = BloodFx.SHADES[2 + (int) (h * 4.99f)];
			if (h > 0.62f) {
				// split: two smaller globs side by side
				float off = w * 1.3f;
				float s2 = (hash(PIDX[i], seed + 13) - 0.5f) * 2f;
				for (int k = -1; k <= 1; k += 2) {
					float cx = PXS[i] + (ax * k + bx * s2 * 0.5f) * off, cy = PYS[i] + (ay * k + by * s2 * 0.5f) * off, cz = PZS[i] + (az * k + bz * s2 * 0.5f) * off;
					glob(vc, pose, m, cx, cy, cz, vx, vy, vz, len * 0.7f, w * 0.65f, shade, light);
				}
			} else {
				glob(vc, pose, m, PXS[i], PYS[i], PZS[i], vx, vy, vz, len, w, shade, light);
			}
		}
	}

	/** A drop of liquid: a box along its motion, glossy top. */
	private static void glob(VertexConsumer vc, PoseStack.Pose pose, Matrix4f m, float x, float y, float z, float vx, float vy, float vz, float len, float w, int rgb,
			int light) {
		float hl = len * 0.5f;
		segment(vc, pose, m, x - vx * hl, y - vy * hl, z - vz * hl, w * 0.85f, x + vx * hl, y + vy * hl, z + vz * hl, w, lighten(rgb, 40), rgb, darken(rgb, 0.45f),
				light, w > 0.025f);
	}

	private static int lighten(int rgb, int by) {
		int r = Math.min(255, ((rgb >> 16) & 0xFF) + by), g = Math.min(255, ((rgb >> 8) & 0xFF) + by / 4), b = Math.min(255, (rgb & 0xFF) + by / 4);
		return r << 16 | g << 8 | b;
	}

	private static int darken(int rgb, float f) {
		int r = Math.min(255, (int) (((rgb >> 16) & 0xFF) * f)), g = Math.min(255, (int) (((rgb >> 8) & 0xFF) * f)), b = Math.min(255, (int) ((rgb & 0xFF) * f));
		return r << 16 | g << 8 | b;
	}

	// ---------------------------------------------------------------- boxes

	private static final float[] C = new float[24];

	/**
	 * A tapered box from p0 (half-width w0) to p1 (w1), lengthened a little past both ends so a chain of them reads as one
	 * rope: its upper face {@code top}, sides {@code side}, underside {@code bottom}; with {@code spec}, a raised glossy streak
	 * along the top.
	 */
	static void segment(VertexConsumer vc, PoseStack.Pose pose, Matrix4f m, float x0, float y0, float z0, float w0, float x1, float y1, float z1, float w1,
			int top, int side, int bottom, int light, boolean spec) {
		float dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
		float l = Mth.sqrt(dx * dx + dy * dy + dz * dz);
		if (l < 1.0e-4f) {
			dx = 0f;
			dy = -1f;
			dz = 0f;
			l = 1.0e-4f;
		} else {
			dx /= l;
			dy /= l;
			dz /= l;
		}
		// overlap the neighbours
		float e0 = w0 * 0.6f, e1 = w1 * 0.6f;
		x0 -= dx * e0;
		y0 -= dy * e0;
		z0 -= dz * e0;
		x1 += dx * e1;
		y1 += dy * e1;
		z1 += dz * e1;
		// side axis: horizontal, across the segment; up axis: the rest (always pointing up-ish, so "top" is the lit top)
		float ax, ay, az;
		if (Math.abs(dy) > 0.95f) {
			ax = 1f;
			ay = 0f;
			az = 0f;
		} else {
			ax = -dz;
			ay = 0f;
			az = dx;
			float al = Mth.sqrt(ax * ax + az * az);
			ax /= al;
			az /= al;
		}
		float bx = ay * dz - az * dy, by = az * dx - ax * dz, bz = ax * dy - ay * dx;
		if (by < 0f) {
			bx = -bx;
			by = -by;
			bz = -bz;
			ax = -ax;
			ay = -ay;
			az = -az;
		}
		// an octagonal rope: a square box with its faces up / down / across, and a second one turned 45 degrees round the
		// axis, its faces half-lit (upper) and half-shaded (lower) -- a rounded liquid profile, lit from above
		box(vc, pose, m, x0, y0, z0, x1, y1, z1, ax, ay, az, bx, by, bz, w0, w0, w1, w1, 0f, side, side, bottom, top, light);
		final float r2 = 0.70710677f;
		float cx = (ax + bx) * r2, cy = (ay + by) * r2, cz = (az + bz) * r2; // up and to one side
		float ex = (bx - ax) * r2, ey = (by - ay) * r2, ez = (bz - az) * r2; // up and to the other
		int upper = mix(top, side), lower = mix(side, bottom);
		float k = 0.93f;
		// faces: -c (down, other side), +c (up, this side), -e (down, this side), +e (up, other side)
		box(vc, pose, m, x0, y0, z0, x1, y1, z1, cx, cy, cz, ex, ey, ez, w0 * k, w0 * k, w1 * k, w1 * k, 0f, lower, upper, lower, upper, light);
		if (spec && w0 + w1 > 0.024f) {
			// the gloss: a narrow bright streak riding the top along the whole segment
			float wa0 = w0 * 0.3f, wa1 = w1 * 0.3f;
			box(vc, pose, m, x0, y0, z0, x1, y1, z1, ax, ay, az, bx, by, bz, wa0, w0 * 0.12f, wa1, w1 * 0.12f, 0.92f, top, top, top, SPEC, light);
		}
	}

	private static int mix(int p, int q) {
		return ((((p >> 16) & 0xFF) + ((q >> 16) & 0xFF)) / 2) << 16 | ((((p >> 8) & 0xFF) + ((q >> 8) & 0xFF)) / 2) << 8 | ((p & 0xFF) + (q & 0xFF)) / 2;
	}

	/**
	 * A box from p0 to p1: across {@code a} half-widths wa0/wa1, along {@code b} half-heights wb0/wb1, its centre {@code lift}
	 * of the rope's half-width (wa / 0.3) up the b axis; faces -a, +a, -b, +b coloured as given.
	 */
	private static void box(VertexConsumer vc, PoseStack.Pose pose, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1, float ax, float ay,
			float az, float bx, float by, float bz, float wa0, float wb0, float wa1, float wb1, float lift, int negA, int posA, int negB, int posB, int light) {
		float l0 = lift > 0f ? wa0 / 0.3f * lift : 0f, l1 = lift > 0f ? wa1 / 0.3f * lift : 0f;
		for (int c = 0; c < 8; c++) {
			boolean far = (c & 4) != 0;
			float sa = (c & 1) == 0 ? -1f : 1f, sb = (c & 2) == 0 ? -1f : 1f;
			float wa = far ? wa1 : wa0, wb = far ? wb1 : wb0, lb = far ? l1 : l0;
			float px = far ? x1 : x0, py = far ? y1 : y0, pz = far ? z1 : z0;
			C[c * 3] = px + ax * sa * wa + bx * (sb * wb + lb);
			C[c * 3 + 1] = py + ay * sa * wa + by * (sb * wb + lb);
			C[c * 3 + 2] = pz + az * sa * wa + bz * (sb * wb + lb);
		}
		face(vc, pose, m, 0, 4, 6, 2, negA, light);
		face(vc, pose, m, 1, 3, 7, 5, posA, light);
		face(vc, pose, m, 0, 1, 5, 4, negB, light);
		face(vc, pose, m, 2, 6, 7, 3, posB, light);
		face(vc, pose, m, 0, 2, 3, 1, CAP, light);
		face(vc, pose, m, 4, 5, 7, 6, negA, light);
	}

	private static final float WU0 = 5.3f / 16f, WV0 = 0.3f / 16f, WU1 = 5.7f / 16f, WV1 = 0.7f / 16f;

	private static void face(VertexConsumer vc, PoseStack.Pose pose, Matrix4f m, int i0, int i1, int i2, int i3, int rgb, int light) {
		int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
		vert(vc, pose, m, i0, r, g, b, WU0, WV0, light);
		vert(vc, pose, m, i1, r, g, b, WU1, WV0, light);
		vert(vc, pose, m, i2, r, g, b, WU1, WV1, light);
		vert(vc, pose, m, i3, r, g, b, WU0, WV1, light);
	}

	private static void vert(VertexConsumer vc, PoseStack.Pose pose, Matrix4f m, int i, int r, int g, int b, float u, float v, int light) {
		vc.addVertex(m, C[i * 3], C[i * 3 + 1], C[i * 3 + 2]).setColor(r, g, b, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
				.setNormal(pose, 0f, 1f, 0f);
	}

	// ---------------------------------------------------------------- first person

	/**
	 * Andy's own view: a short spurting jet out of a forearm stump, in the arm's own space at the cut ({@code dir} out of it,
	 * gravity roughly {@code g}); short, so it never reaches the camera.
	 */
	public static void firstPersonArm(PoseStack pose, MultiBufferSource buffers, int light, Player player, int part, float dx, float dy, float dz, float gx,
			float gy, float gz) {
		AndyState s = Andy.peek(player);
		if (s == null) {
			return;
		}
		float now = player.level().getGameTime() + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		boolean boost = s.flag(AndyState.FLAG_BOOST);
		long[] seen = seen(player);
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(AndyRender.PALETTE));
		// (right in front of the camera: a small fountain, a third the size of what others see)
		rope(vc, pose.last(), 0f, 0f, 0f, dx, dy, dz, gx, gy, gz, 0.05f, speed(boost) * 0.65f, boost ? 1.3f : 1f, Math.min(1.3f, violence(seen, now)), now, boost,
				player.getId() * 7 + part, light, boost ? 1.2f : 1.0f);
	}

	/** First person, flying: the spray off the arm jets streaming back past the bottom corners of the view. */
	static void firstPersonSpray(Player player, AndyState s, Vec3 at, Vec3 dir) {
		boolean boost = s.flag(AndyState.FLAG_BOOST);
		float p = pulse(player.level().getGameTime(), boost);
		float v = speed(boost) * (0.55f + 0.75f * p);
		int drops = (int) (2f * p * (boost ? 1.5f : 1f) + RND.nextFloat() * 0.8f);
		for (int i = 0; i < drops; i++) {
			BloodFx.arterialDrop(at, dir, player.getDeltaMovement(), v * (0.75f + RND.nextFloat() * 0.5f), 0.12f, false);
		}
	}
}
