package com.projecthero.mod.client.andy;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import com.projecthero.mod.andy.AndySoundEvents;
import com.projecthero.mod.andy.data.AndyState;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3: Andy's client sounds -- the regrowth (looping beds per layer plus random accents, a wet snap when the part is
 * whole), the blood jets, the boost burn, the punch wind-up, and one-shot helpers for client-side effects.
 *
 * <h2>Regrowth</h2>
 * {@link #regrowth} is fed every tick (or frame) for each regrowing part with the three layer fractions: the bone loop
 * plays while the bone grows, the muscle bed while the muscle grows, the skin while the skin grows, each fading in and out
 * at its layer's ends, pitched up by its progress and by how fast it is going; cracks, squelches and stretches land at
 * random at a rate that follows the same; {@link AndySoundEvents#REGROW_FINISH} plays once when the skin reaches 1. If the
 * feed stops the loops fade out by themselves within half a second. A part that breaks again (the bone fraction drops back)
 * starts over. Without a renderer that knows the real fractions, {@link #watch} estimates them from the synced regrowth
 * times ({@link #layers}).
 *
 * <h2>Loops</h2>
 * {@link #jet}, {@link #burn} and {@link #punchCharge} are sticky: {@code on} starts the loop (once), {@code off} fades it
 * out. Every loop follows its player and stops itself when he dies, is removed, or the world changes; everything is dropped
 * on disconnect ({@link #reset}).
 */
public final class AndySounds {
	/**
	 * The layer windows as fractions of the whole regrowth (Normal is ~8.5 s: bone 0-3 s, muscle 2-6 s, skin 4-8.5 s). The
	 * regeneration speed only stretches the whole, so the fractions hold at every speed.
	 */
	public static final float BONE_START = 0f, BONE_END = 3f / 8.5f;
	public static final float MUSCLE_START = 2f / 8.5f, MUSCLE_END = 6f / 8.5f;
	public static final float SKIN_START = 4f / 8.5f, SKIN_END = 1f;
	/** Layer rates at Normal (fraction per second): a faster layer sounds a little higher and busier. */
	private static final float[] NOMINAL_RATE = { 1f / 3f, 1f / 4f, 1f / 4.5f };
	/** A heavier part sounds lower. */
	private static final float[] PART_PITCH = { 1.04f, 0.86f, 1.06f, 1.06f, 0.96f, 0.96f };
	/** Height of each part's centre above the feet. */
	private static final double[] PART_Y = { 1.65, 1.1, 1.25, 1.25, 0.45, 0.45 };
	/**
	 * Loop levels. Since the v2 regrowth sounds (no more boiling-water bubbles / fizz) the loops are sparse events over
	 * near-silence, so they sit under the accents: the accents carry the sound, the loops fill the gaps between them.
	 */
	private static final float[] BASE_VOLUME = { 0.45f, 0.4f, 0.32f };
	/** Accent rates per second at full layer volume (bone cracks, flesh squelches, skin stretches); kept sparse. */
	private static final float[] ACCENT_RATE = { 2.4f, 1.2f, 0.7f };
	/** Accent volume range (min, spread). */
	private static final float ACCENT_VOLUME = 0.45f, ACCENT_VOLUME_SPREAD = 0.3f;
	/** Regrowth sounds only for Andys this close to the camera (the beds are audible ~12 blocks). */
	private static final double RANGE = 20.0;

	private static final Map<UUID, Voices> VOICES = new HashMap<>();
	private static final RandomSource RANDOM = RandomSource.create();
	private static long ticks;
	private static ClientLevel lastLevel;

	private AndySounds() {
	}

	/** Registers the tick (loop keep-alive, world-change and stale-player cleanup) and the disconnect reset. */
	public static void initialize() {
		ClientTickEvents.END_CLIENT_TICK.register(AndySounds::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
	}

	// ================================================================ regrowth

	/**
	 * The three layer fractions (bone, muscle, skin, each 0..1) for a part whose whole regrowth is {@code p} (0..1) through,
	 * from {@link #BONE_START}..{@link #SKIN_END}.
	 */
	public static float[] layers(float p) {
		return new float[] { window(p, BONE_START, BONE_END), window(p, MUSCLE_START, MUSCLE_END), window(p, SKIN_START, SKIN_END) };
	}

	private static float window(float p, float a, float b) {
		return Mth.clamp((p - a) / (b - a), 0f, 1f);
	}

	/**
	 * The estimator path: for one Andy, feeds {@link #regrowth} from the synced regrowth times for every regrowing part, ends a
	 * regrowth the server just finished (the snap), restarts a part that broke again; and drives the jet loop from the
	 * synced propelling flag. Call once per client tick per Andy in view.
	 */
	public static void watch(Player player, AndyState s, long now) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || player == null || s == null) {
			return;
		}
		Voices v = VOICES.computeIfAbsent(player.getUUID(), k -> new Voices());
		v.lastWatched = ticks;
		v.autoJet = s.hasPower && s.flag(AndyState.FLAG_PROPELLING) && player.isAlive();
		updateJet(player, v);
		boolean near = mc.player != null && player.distanceToSqr(mc.player) <= RANGE * RANGE;
		for (int i = 0; i < AndyState.PARTS; i++) {
			PartVoice pv = v.parts[i];
			if (!near) {
				if (pv != null) {
					pv.release();
					v.parts[i] = null;
				}
				continue;
			}
			if (s.broken(i)) {
				long start = s.regrowStart(i);
				if (start > 0L && now >= start) {
					float[] l = layers(s.regrowth(i, now));
					regrowth(player, i, l[0], l[1], l[2]);
				} else if (pv != null && pv.lastBone > 0f) {
					partBroken(player, i); // gone again before it finished (or waiting to start over)
				}
			} else if (pv != null && !pv.finished && pv.lastSkin > 0.3f) {
				regrowth(player, i, 1f, 1f, 1f); // the server finished it between our samples: the snap
			}
		}
	}

	/**
	 * One regrowing part's progress: {@code boneFrac}, {@code muscleFrac}, {@code skinFrac} 0..1 (call every tick or frame
	 * while it regrows; the loops fade out by themselves if the calls stop).
	 */
	public static void regrowth(Player player, int part, float boneFrac, float muscleFrac, float skinFrac) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || player == null || part < 0 || part >= AndyState.PARTS) {
			return;
		}
		Voices v = VOICES.computeIfAbsent(player.getUUID(), k -> new Voices());
		v.lastWatched = ticks;
		PartVoice pv = v.parts[part];
		if (pv == null) {
			pv = v.parts[part] = new PartVoice();
		}
		float[] f = { Mth.clamp(boneFrac, 0f, 1f), Mth.clamp(muscleFrac, 0f, 1f), Mth.clamp(skinFrac, 0f, 1f) };
		// broke again: the bone went back, or a finished part is growing anew
		if (pv.lastBone >= 0f && (f[0] < pv.lastBone - 0.2f || (pv.finished && f[2] < 0.9f))) {
			pv.release();
			pv = v.parts[part] = new PartVoice();
		}
		// rates and accents step at most every 40 ms, however often this is called (per tick or per frame)
		long nanos = System.nanoTime();
		float dt = pv.lastNanos == 0L ? 0f : Math.min(0.25f, (nanos - pv.lastNanos) / 1.0e9f);
		float[] last = { pv.lastBone, pv.lastMuscle, pv.lastSkin };
		float step = 0f;
		if (pv.lastNanos == 0L) {
			pv.lastNanos = nanos;
			System.arraycopy(f, 0, pv.ref, 0, 3);
		} else if (dt >= 0.04f) {
			step = dt;
			pv.lastNanos = nanos;
			for (int k = 0; k < 3; k++) {
				if (f[k] > 0f && f[k] < 1f && f[k] > pv.ref[k]) {
					float rate = Mth.clamp((f[k] - pv.ref[k]) / dt / NOMINAL_RATE[k], 0.4f, 3f);
					pv.rate[k] += (rate - pv.rate[k]) * 0.3f;
				}
			}
			System.arraycopy(f, 0, pv.ref, 0, 3);
		}
		double y = PART_Y[part];
		float partPitch = PART_PITCH[part];
		// several parts at once (the Undead re-form): the beds share the level instead of stacking up
		pv.lastFedTick = ticks;
		int growing = 0;
		for (PartVoice o : v.parts) {
			if (o != null && !o.finished && ticks - o.lastFedTick <= 2) {
				growing++;
			}
		}
		float crowd = 1f / (float) Math.sqrt(Math.max(1, growing));
		SoundEvent[] beds = { AndySoundEvents.REGROW_BONE_PUSH, AndySoundEvents.REGROW_MUSCLE, AndySoundEvents.REGROW_SKIN };
		SoundEvent[] accents = { AndySoundEvents.REGROW_BONE_CRACK, AndySoundEvents.REGROW_SQUELCH, AndySoundEvents.REGROW_SKIN_STRETCH };
		for (int k = 0; k < 3; k++) {
			float e = f[k] > 0f && f[k] < 1f ? Math.min(1f, f[k] / 0.08f) * Math.min(1f, (1f - f[k]) / 0.12f) : 0f;
			float speed = pv.rate[k];
			float vol = BASE_VOLUME[k] * e * crowd * (0.85f + 0.15f * Math.min(2f, speed));
			float pitch = partPitch * (0.9f + 0.2f * f[k]) * (0.94f + 0.06f * speed);
			Loop loop = pv.beds[k];
			if (vol > 0.001f) {
				if (loop == null || loop.isStopped()) {
					loop = pv.beds[k] = new Loop(beds[k], player, y, 0.25f);
					loop.feed(vol, pitch);
					mc.getSoundManager().play(loop);
				} else {
					loop.feed(vol, pitch);
				}
				// accents, Poisson at a rate that follows the layer
				if (step > 0f && RANDOM.nextFloat() < ACCENT_RATE[k] * e * crowd * Math.min(2.2f, speed) * step) {
					local(player, y, accents[k], ACCENT_VOLUME + ACCENT_VOLUME_SPREAD * RANDOM.nextFloat(), partPitch * (0.9f + 0.2f * RANDOM.nextFloat()));
				}
			} else if (loop != null) {
				loop.feed(0f, pitch);
			}
			// a layer starting: a crack for the bone, a thick squelch as the flesh begins
			if (last[k] <= 0f && f[k] > 0f && f[k] < 0.5f) {
				if (k == 0) {
					local(player, y, AndySoundEvents.REGROW_BONE_CRACK, 0.75f, partPitch * 0.9f);
				} else if (k == 1) {
					local(player, y, AndySoundEvents.REGROW_SQUELCH, 0.8f, partPitch * 0.85f);
				}
			}
		}
		if (!pv.finished && f[2] >= 0.999f && pv.lastSkin >= 0f) {
			pv.finished = true;
			local(player, y, AndySoundEvents.REGROW_FINISH, 0.8f, partPitch * (0.95f + 0.1f * RANDOM.nextFloat()));
			pv.fadeBeds();
		}
		pv.lastBone = f[0];
		pv.lastMuscle = f[1];
		pv.lastSkin = f[2];
	}

	/** The part just broke (again): any regrowth sound for it stops at once and the next regrowth starts fresh. */
	public static void partBroken(Player player, int part) {
		Voices v = player == null ? null : VOICES.get(player.getUUID());
		if (v != null && part >= 0 && part < AndyState.PARTS && v.parts[part] != null) {
			v.parts[part].release();
			v.parts[part] = null;
		}
	}

	// ================================================================ loops

	/** The blood jets: {@code on} starts the loop (with the burst-out), off sputters it out; {@code boosting} pushes it harder. */
	public static void jet(Player player, boolean on, boolean boosting) {
		if (player == null) {
			return;
		}
		Voices v = VOICES.computeIfAbsent(player.getUUID(), k -> new Voices());
		v.lastWatched = ticks;
		v.apiJet = on;
		v.boosting = on && boosting;
		updateJet(player, v);
	}

	private static void updateJet(Player player, Voices v) {
		boolean on = (v.apiJet || v.autoJet) && player.isAlive();
		// swelling with each beat of the jets' spurts (AndyJets' heart)
		float vol = (v.boosting ? 0.85f : 0.6f) * (0.78f + 0.3f * AndyJets.soundPulse(player));
		float pitch = v.boosting ? 1.18f : 1.0f;
		if (on) {
			if (v.jet == null || v.jet.isStopped()) {
				v.jet = new Loop(AndySoundEvents.BLOOD_JET_LOOP, player, 0.9, 0.2f);
				v.jet.sticky = true;
				v.jet.feed(vol, pitch);
				Minecraft.getInstance().getSoundManager().play(v.jet);
				local(player, 0.9, AndySoundEvents.BLOOD_JET_START, 0.8f, 0.95f + 0.1f * RANDOM.nextFloat());
			} else {
				v.jet.feed(vol, pitch);
			}
		} else if (v.jet != null) {
			if (!v.jet.isStopped() && !v.jet.released) {
				local(player, 0.9, AndySoundEvents.BLOOD_JET_STOP, 0.7f, 0.95f + 0.1f * RANDOM.nextFloat());
			}
			v.jet.release();
			v.jet = null;
		}
	}

	/** The boost burning his body away: sizzle and wind while {@code on}. */
	public static void burn(Player player, boolean on) {
		if (player == null) {
			return;
		}
		Voices v = VOICES.computeIfAbsent(player.getUUID(), k -> new Voices());
		v.lastWatched = ticks;
		if (on && player.isAlive()) {
			if (v.burn == null || v.burn.isStopped()) {
				v.burn = new Loop(AndySoundEvents.BOOST_BURN_LOOP, player, 1.0, 0.15f);
				v.burn.sticky = true;
				v.burn.feed(0.7f, 1.0f);
				Minecraft.getInstance().getSoundManager().play(v.burn);
			}
		} else if (v.burn != null) {
			v.burn.release();
			v.burn = null;
		}
	}

	/** The punch wind-up: sinew stretching while {@code on}, rising in pitch over ~2 s of charge. */
	public static void punchCharge(Player player, boolean on) {
		if (player == null) {
			return;
		}
		Voices v = VOICES.computeIfAbsent(player.getUUID(), k -> new Voices());
		v.lastWatched = ticks;
		if (on && player.isAlive()) {
			if (v.punch == null || v.punch.isStopped()) {
				v.punch = new Loop(AndySoundEvents.PUNCH_STRETCH, player, 1.25, 0.3f);
				v.punch.sticky = true;
				v.punch.rising = true;
				v.punch.feed(0.6f, 0.8f);
				Minecraft.getInstance().getSoundManager().play(v.punch);
			}
		} else if (v.punch != null) {
			v.punch.release();
			v.punch = null;
		}
	}

	/** Every sound of this player stops (death, power lost). */
	public static void stop(Player player) {
		if (player == null) {
			return;
		}
		Voices v = VOICES.remove(player.getUUID());
		if (v != null) {
			v.releaseAll();
		}
	}

	// ================================================================ one-shots (client-side effects)

	public static void partBreak(Vec3 at) {
		at(at, AndySoundEvents.PART_BREAK, 1.0f, 1f);
	}

	/** A splash of blood; {@code size} 0..1 (bigger is louder and lower). */
	public static void bloodBurst(Vec3 at, float size) {
		float s = Mth.clamp(size, 0f, 1f);
		at(at, AndySoundEvents.BLOOD_BURST, 0.55f + 0.45f * s, 1.15f - 0.3f * s);
	}

	public static void spray(Vec3 at) {
		at(at, AndySoundEvents.BLOOD_SPRAY, 0.6f, 1f);
	}

	public static void drip(Vec3 at) {
		at(at, AndySoundEvents.BLOOD_DRIP, 0.35f, 1f);
	}

	public static void splat(Vec3 at) {
		at(at, AndySoundEvents.BLOOD_SPLAT, 0.5f, 1f);
	}

	/** The head thudding and rolling; {@code speed} 0..1. */
	public static void headRoll(Player player, float speed) {
		if (player != null) {
			float s = Mth.clamp(speed, 0.1f, 1f);
			local(player, 0.3, AndySoundEvents.HEAD_ROLL, 0.35f + 0.45f * s, 0.9f + 0.2f * s);
		}
	}

	public static void launch(Vec3 at) {
		at(at, AndySoundEvents.LAUNCH_WHOOSH, 0.85f, 1f);
	}

	public static void partsBullet(Vec3 at) {
		at(at, AndySoundEvents.BULLET_PARTS, 0.75f, 1f);
	}

	public static void crimsonBullet(Vec3 at) {
		at(at, AndySoundEvents.BULLET_CRIMSON, 1.0f, 1f);
	}

	public static void punchFire(Vec3 at) {
		at(at, AndySoundEvents.PUNCH_FIRE, 0.9f, 1f);
	}

	private static void at(Vec3 at, SoundEvent ev, float volume, float pitch) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level != null && at != null) {
			level.playLocalSound(at.x, at.y, at.z, ev, SoundSource.PLAYERS, volume, Mth.clamp(pitch * (0.92f + 0.16f * RANDOM.nextFloat()), 0.5f, 2f), false);
		}
	}

	private static void local(Player player, double y, SoundEvent ev, float volume, float pitch) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level != null) {
			level.playLocalSound(player.getX(), player.getY() + y, player.getZ(), ev, SoundSource.PLAYERS, volume, Mth.clamp(pitch, 0.5f, 2f), false);
		}
	}

	// ================================================================ lifecycle

	private static void tick(Minecraft mc) {
		if (mc.level != lastLevel) {
			lastLevel = mc.level;
			reset(); // world change or left the world
			return;
		}
		if (mc.level == null || mc.isPaused()) {
			return;
		}
		ticks++;
		// an Andy no longer watched or fed (out of view, logged out, lost the power) for a second: drop him
		if (ticks % 10 == 0 && !VOICES.isEmpty()) {
			Iterator<Map.Entry<UUID, Voices>> it = VOICES.entrySet().iterator();
			while (it.hasNext()) {
				Voices v = it.next().getValue();
				if (ticks - v.lastWatched > 20 && !v.anySticky()) {
					v.releaseAll();
					it.remove();
				} else if (ticks - v.lastWatched > 20) {
					v.autoJet = false;
				}
			}
		}
	}

	/** Every loop stopped and every map cleared (disconnect, world change). */
	public static void reset() {
		for (Voices v : VOICES.values()) {
			v.releaseAll();
		}
		VOICES.clear();
		ticks = 0L;
	}

	/** Tests / debug: how many Andys have sound state. */
	public static int tracked() {
		return VOICES.size();
	}

	// ================================================================ state

	private static final class Voices {
		final PartVoice[] parts = new PartVoice[AndyState.PARTS];
		Loop jet, burn, punch;
		boolean apiJet, autoJet, boosting;
		long lastWatched;

		boolean anySticky() {
			return (jet != null && !jet.isStopped()) || (burn != null && !burn.isStopped()) || (punch != null && !punch.isStopped());
		}

		void releaseAll() {
			for (int i = 0; i < parts.length; i++) {
				if (parts[i] != null) {
					parts[i].release();
					parts[i] = null;
				}
			}
			if (jet != null) {
				jet.release();
				jet = null;
			}
			if (burn != null) {
				burn.release();
				burn = null;
			}
			if (punch != null) {
				punch.release();
				punch = null;
			}
		}
	}

	private static final class PartVoice {
		final Loop[] beds = new Loop[3];
		final float[] rate = { 1f, 1f, 1f };
		final float[] ref = new float[3];
		float lastBone = -1f, lastMuscle = -1f, lastSkin = -1f;
		long lastNanos, lastFedTick;
		boolean finished;

		void fadeBeds() {
			for (Loop l : beds) {
				if (l != null) {
					l.feed(0f, l.getPitch());
				}
			}
		}

		void release() {
			for (int k = 0; k < beds.length; k++) {
				if (beds[k] != null) {
					beds[k].release();
					beds[k] = null;
				}
			}
		}
	}

	/**
	 * A looping sound that follows a player. Fed a target volume / pitch it glides there; unfed for 10 ticks (unless
	 * {@link #sticky}) or released it fades out and stops; it stops at once if the player dies, is removed or the world changes.
	 */
	static final class Loop extends AbstractTickableSoundInstance {
		private final Player player;
		private final double yOff;
		private final float attack;
		private final net.minecraft.world.level.Level startLevel;
		private float targetVolume, targetPitch = 1f;
		private long fedAt;
		private int age;
		boolean sticky, rising, released;

		Loop(SoundEvent ev, Player player, double yOff, float attack) {
			super(ev, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
			this.player = player;
			this.yOff = yOff;
			this.attack = attack;
			this.startLevel = player.level();
			this.looping = true;
			this.delay = 0;
			this.volume = 0.01f;
			this.pitch = 1f;
			this.relative = false;
			this.attenuation = SoundInstance.Attenuation.LINEAR;
			this.x = player.getX();
			this.y = player.getY() + yOff;
			this.z = player.getZ();
			this.fedAt = ticks;
		}

		void feed(float vol, float pitch) {
			this.targetVolume = Mth.clamp(vol, 0f, 1f);
			this.targetPitch = Mth.clamp(pitch, 0.5f, 2f);
			this.fedAt = ticks;
			if (this.age == 0) {
				this.pitch = this.targetPitch;
			}
		}

		void release() {
			this.released = true;
			this.targetVolume = 0f;
		}

		@Override
		public void tick() {
			age++;
			Minecraft mc = Minecraft.getInstance();
			if (player.isRemoved() || !player.isAlive() || mc.level == null || player.level() != startLevel || mc.level != startLevel) {
				stop();
				return;
			}
			this.x = player.getX();
			this.y = player.getY() + yOff;
			this.z = player.getZ();
			boolean starved = !sticky && ticks - fedAt > 10;
			float target = released || starved ? 0f : targetVolume;
			float p = targetPitch;
			if (rising) {
				p = targetPitch + Math.min(0.5f, age / 40f * 0.5f); // ~2 s of charge: up half an octave-ish
			}
			if (this.volume < target) {
				this.volume = Math.min(target, this.volume + attack * Math.max(0.2f, target));
			} else {
				this.volume = Math.max(target, this.volume - 0.12f * Math.max(0.3f, this.volume));
			}
			this.pitch += (Mth.clamp(p, 0.5f, 2f) - this.pitch) * 0.25f;
			if ((released || starved || target <= 0f) && this.volume <= 0.005f && age > 2) {
				stop();
			}
		}

		@Override
		public boolean canStartSilent() {
			return true;
		}
	}

}
