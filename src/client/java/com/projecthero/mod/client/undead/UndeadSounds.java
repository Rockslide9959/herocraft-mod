package com.projecthero.mod.client.undead;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import com.projecthero.mod.undead.UndeadSoundEvents;
import com.projecthero.mod.undead.data.UndeadState;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3: Undead's client sounds -- the regrowth fizz, the blood jets, the boost burn, the punch wind-up, and one-shot helpers
 * for client-side effects.
 *
 * <h2>Regrowth</h2>
 * v0.17.3 (user: "make the regeneration sound effects the steam fizz sound please instead of the gross sounds it has now"): a
 * regrowing segment hisses softly -- vanilla's {@code random/fizz} ({@link SoundEvents#FIRE_EXTINGUISH}) every few ticks, quiet,
 * at a pitch of its own (a heavier part lower, the hands and feet higher and quieter), several segments at once sharing the level
 * instead of stacking up; when it is whole it gives one slightly stronger, lower fizz. {@link #watch} drives it from the synced
 * regrowth times.
 *
 * <h2>Loops</h2>
 * {@link #jet}, {@link #burn} and {@link #punchCharge} are sticky: {@code on} starts the loop (once), {@code off} fades it
 * out. Every loop follows its player and stops itself when he dies, is removed, or the world changes; everything is dropped
 * on disconnect ({@link #reset}).
 */
public final class UndeadSounds {
	/** A heavier part sounds lower (head, body, arms, legs, hands, feet). */
	private static final float[] PART_PITCH = { 1.04f, 0.86f, 1.06f, 1.06f, 0.96f, 0.96f, 1.22f, 1.22f, 1.12f, 1.12f };
	/** v0.17.3: per segment loudness -- the hands and feet (segments 6-9) are small: quieter. */
	private static final float[] SEG_GAIN = { 1f, 1f, 1f, 1f, 1f, 1f, 0.6f, 0.6f, 0.6f, 0.6f };
	/** Height of each part's centre above the feet. */
	private static final double[] PART_Y = { 1.65, 1.1, 1.25, 1.25, 0.45, 0.45, 0.75, 0.75, 0.1, 0.1 };
	/**
	 * The hiss while a segment grows: one fizz every {@link #HISS_EVERY} + 0..{@link #HISS_JITTER} ticks at {@link #HISS_VOLUME}
	 * (the user dislikes loud sounds) and {@link #HISS_PITCH} times the part's pitch; the fizz as it completes is a little
	 * stronger and lower ({@link #DONE_VOLUME}, {@link #DONE_PITCH}).
	 */
	static final int HISS_EVERY = 5, HISS_JITTER = 4;
	static final float HISS_VOLUME = 0.12f, HISS_PITCH = 1.35f;
	static final float DONE_VOLUME = 0.26f, DONE_PITCH = 1.05f;
	/** Regrowth sounds only for Undeads this close to the camera. */
	private static final double RANGE = 20.0;

	private static final Map<UUID, Voices> VOICES = new HashMap<>();
	private static final RandomSource RANDOM = RandomSource.create();
	private static long ticks;
	private static ClientLevel lastLevel;

	private UndeadSounds() {
	}

	/** Registers the tick (loop keep-alive, world-change and stale-player cleanup) and the disconnect reset. */
	public static void initialize() {
		ClientTickEvents.END_CLIENT_TICK.register(UndeadSounds::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
	}

	// ================================================================ regrowth

	/**
	 * For one Undead (once per client tick while in view): every segment regrowing hisses softly, and fizzes once when it is
	 * whole again; drives the jet loop from the synced propelling flag.
	 */
	public static void watch(Player player, UndeadState s, long now) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || player == null || s == null) {
			return;
		}
		Voices v = VOICES.computeIfAbsent(player.getUUID(), k -> new Voices());
		v.lastWatched = ticks;
		v.autoJet = s.hasPower && s.flag(UndeadState.FLAG_PROPELLING) && player.isAlive();
		updateJet(player, v);
		boolean near = mc.player != null && player.distanceToSqr(mc.player) <= RANGE * RANGE;
		int growing = 0;
		for (int i = 0; i < UndeadState.SEGS; i++) {
			if (s.broken(i) && s.regrowStart(i) > 0L && now >= s.regrowStart(i)) {
				growing++;
			}
		}
		// several parts at once (the Undead re-form): they share the level instead of stacking up
		float crowd = 1f / (float) Math.sqrt(Math.max(1, growing));
		for (int i = 0; i < UndeadState.SEGS; i++) {
			PartVoice pv = v.parts[i];
			if (!near) {
				v.parts[i] = null;
				continue;
			}
			if (s.broken(i)) {
				long start = s.regrowStart(i);
				if (start > 0L && now >= start) {
					if (pv == null) {
						pv = v.parts[i] = new PartVoice();
						pv.nextAt = ticks + RANDOM.nextInt(3); // segments that start together don't hiss in lockstep
					}
					if (s.regrowth(i, now) >= 0.999f) {
						finished(player, i, pv); // (the server clears it on its next tick)
					} else if (ticks >= pv.nextAt) {
						pv.nextAt = ticks + HISS_EVERY + RANDOM.nextInt(HISS_JITTER + 1);
						local(player, PART_Y[i], SoundEvents.FIRE_EXTINGUISH, HISS_VOLUME * SEG_GAIN[i] * crowd * (0.85f + 0.3f * RANDOM.nextFloat()),
								HISS_PITCH * PART_PITCH[i] * (0.94f + 0.12f * RANDOM.nextFloat()));
					}
				} else {
					v.parts[i] = null; // gone, still waiting for its regrowth to start (or gone again before it finished)
				}
			} else if (pv != null) {
				finished(player, i, pv); // the server finished it between our samples
				v.parts[i] = null;
			}
		}
	}

	/** A segment is whole again: one stronger, lower fizz (once). */
	private static void finished(Player player, int part, PartVoice pv) {
		if (pv.finished) {
			return;
		}
		pv.finished = true;
		local(player, PART_Y[part], SoundEvents.FIRE_EXTINGUISH, DONE_VOLUME * SEG_GAIN[part], DONE_PITCH * PART_PITCH[part] * (0.95f + 0.1f * RANDOM.nextFloat()));
	}

	/** The part just broke (again): its regrowth sound starts fresh next time. */
	public static void partBroken(Player player, int part) {
		Voices v = player == null ? null : VOICES.get(player.getUUID());
		if (v != null && part >= 0 && part < UndeadState.SEGS) {
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
		// swelling with each beat of the jets' spurts (UndeadJets' heart)
		float vol = (v.boosting ? 0.85f : 0.6f) * (0.78f + 0.3f * UndeadJets.soundPulse(player));
		float pitch = v.boosting ? 1.18f : 1.0f;
		if (on) {
			if (v.jet == null || v.jet.isStopped()) {
				v.jet = new Loop(UndeadSoundEvents.BLOOD_JET_LOOP, player, 0.9, 0.2f);
				v.jet.sticky = true;
				v.jet.feed(vol, pitch);
				Minecraft.getInstance().getSoundManager().play(v.jet);
				local(player, 0.9, UndeadSoundEvents.BLOOD_JET_START, 0.8f, 0.95f + 0.1f * RANDOM.nextFloat());
			} else {
				v.jet.feed(vol, pitch);
			}
		} else if (v.jet != null) {
			if (!v.jet.isStopped() && !v.jet.released) {
				local(player, 0.9, UndeadSoundEvents.BLOOD_JET_STOP, 0.7f, 0.95f + 0.1f * RANDOM.nextFloat());
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
				v.burn = new Loop(UndeadSoundEvents.BOOST_BURN_LOOP, player, 1.0, 0.15f);
				v.burn.sticky = true;
				v.burn.feed(0.35f, 1.0f); // (v0.17.3: a quiet sizzle under the jets, not a roar)
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
				v.punch = new Loop(UndeadSoundEvents.PUNCH_STRETCH, player, 1.25, 0.3f);
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
		at(at, UndeadSoundEvents.PART_BREAK, 1.0f, 1f);
	}

	/** A splash of blood; {@code size} 0..1 (bigger is louder and lower). */
	public static void bloodBurst(Vec3 at, float size) {
		float s = Mth.clamp(size, 0f, 1f);
		at(at, UndeadSoundEvents.BLOOD_BURST, 0.55f + 0.45f * s, 1.15f - 0.3f * s);
	}

	public static void spray(Vec3 at) {
		at(at, UndeadSoundEvents.BLOOD_SPRAY, 0.6f, 1f);
	}

	public static void drip(Vec3 at) {
		at(at, UndeadSoundEvents.BLOOD_DRIP, 0.35f, 1f);
	}

	public static void splat(Vec3 at) {
		at(at, UndeadSoundEvents.BLOOD_SPLAT, 0.5f, 1f);
	}

	/** The head thudding and rolling; {@code speed} 0..1. */
	public static void headRoll(Player player, float speed) {
		if (player != null) {
			float s = Mth.clamp(speed, 0.1f, 1f);
			local(player, 0.3, UndeadSoundEvents.HEAD_ROLL, 0.35f + 0.45f * s, 0.9f + 0.2f * s);
		}
	}

	public static void launch(Vec3 at) {
		at(at, UndeadSoundEvents.LAUNCH_WHOOSH, 0.85f, 1f);
	}

	public static void partsBullet(Vec3 at) {
		at(at, UndeadSoundEvents.BULLET_PARTS, 0.75f, 1f);
	}

	public static void crimsonBullet(Vec3 at) {
		at(at, UndeadSoundEvents.BULLET_CRIMSON, 1.0f, 1f);
	}

	public static void punchFire(Vec3 at) {
		at(at, UndeadSoundEvents.PUNCH_FIRE, 0.9f, 1f);
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
		// an Undead no longer watched or fed (out of view, logged out, lost the power) for a second: drop him
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

	/** Tests / debug: how many Undeads have sound state. */
	public static int tracked() {
		return VOICES.size();
	}

	// ================================================================ state

	private static final class Voices {
		final PartVoice[] parts = new PartVoice[UndeadState.SEGS];
		Loop jet, burn, punch;
		boolean apiJet, autoJet, boosting;
		long lastWatched;

		boolean anySticky() {
			return (jet != null && !jet.isStopped()) || (burn != null && !burn.isStopped()) || (punch != null && !punch.isStopped());
		}

		void releaseAll() {
			java.util.Arrays.fill(parts, null);
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

	/** One regrowing segment's fizz: when it hisses next, and whether its completion fizz has played. */
	private static final class PartVoice {
		long nextAt;
		boolean finished;
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
