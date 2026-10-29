package com.projecthero.mod.omnitrix;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1 (user: "make the Omnitrix and alien sounds sound more like the show, not tring sounds, not loud"): a sound
 * <em>recipe</em> engine. A {@link Cue} is a few layered sound events -- nearly all vanilla samples, pitched and quiet --
 * each with its own volume, pitch and tick offset. Every Omnitrix / alien sound is a Cue (see OmnitrixSounds and the kits;
 * the design notes and the before / after table live in {@code docs/OMNITRIX_SOUNDS_0171.md}).
 *
 * <p>Playback rules, the same for every Cue:
 * <ul>
 * <li>The caller's volume is a <em>relative</em> scale (the kits pass 0.3-1.6): at most 1 it multiplies the layers, above 1 it
 * is compressed ({@code 1 + (v - 1) * 0.35}) so a big move is a little louder / carries a little further, never harsh. The
 * layer volumes themselves are the real levels (UI 0.1-0.45, moves 0.2-0.6).</li>
 * <li>The caller's pitch is compressed toward 1 by half and multiplied in, then one random jitter (default +-6 %) for the
 * whole Cue so repeats don't grate; every layer is clamped to the engine's 0.5-2.0.</li>
 * <li>A Cue never plays twice within its {@link Cue#gap} ticks at the same spot (a 3-block cell, or the same player for
 * {@link #notify}), so nothing stacks every tick.</li>
 * <li>Delayed layers wait in a small server-tick queue, cleared with the session ({@link #clearSessionState}).</li>
 * </ul>
 */
public final class OmnitrixSfx {
	private static final List<Cue> ALL = Collections.synchronizedList(new ArrayList<>());
	private static final List<Pending> PENDING = new ArrayList<>();
	private static final Map<String, Long> LAST = new HashMap<>();
	private static final int CELL = 3;

	private OmnitrixSfx() {
	}

	/** One layer of a Cue: a sound event, its volume and pitch at a caller volume / pitch of 1, and a delay in ticks. */
	public record Layer(Holder<SoundEvent> sound, float volume, float pitch, int delay) {
	}

	private record Pending(ServerLevel level, ServerPlayer only, Vec3 at, Holder<SoundEvent> sound, SoundSource source, float volume,
			float pitch, int due) {
	}

	/** A named, layered sound recipe. Build once into a {@code static final} field: {@code cue("x").layer(...).layer(...)}. */
	public static final class Cue {
		private final String name;
		private final List<Layer> layers = new ArrayList<>();
		private float jitter = 0.06f;
		private int gap = 2;

		private Cue(String name) {
			this.name = name;
		}

		public Cue layer(SoundEvent sound, float volume, float pitch) {
			return layer(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), volume, pitch, 0);
		}

		public Cue layer(SoundEvent sound, float volume, float pitch, int delay) {
			return layer(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), volume, pitch, delay);
		}

		public Cue layer(Holder<SoundEvent> sound, float volume, float pitch) {
			return layer(sound, volume, pitch, 0);
		}

		public Cue layer(Holder<SoundEvent> sound, float volume, float pitch, int delay) {
			layers.add(new Layer(sound, volume, pitch, Math.max(0, delay)));
			return this;
		}

		/** The random pitch spread (a fraction: 0.06 = +-6 %). */
		public Cue jitter(float jitter) {
			this.jitter = Math.max(0f, jitter);
			return this;
		}

		/** The shortest time (ticks) between two plays of this Cue at the same spot / for the same player. */
		public Cue gap(int ticks) {
			this.gap = Math.max(0, ticks);
			return this;
		}

		public String name() {
			return name;
		}

		public List<Layer> layers() {
			return Collections.unmodifiableList(layers);
		}

		public float jitter() {
			return jitter;
		}

		public int gap() {
			return gap;
		}

		@Override
		public String toString() {
			return "Cue[" + name + "]";
		}
	}

	/** Starts a Cue (and lists it for the sound gametest). */
	public static Cue cue(String name) {
		Cue c = new Cue(name);
		ALL.add(c);
		return c;
	}

	/** Every Cue built so far (the kits' are built when their class loads, at mod init). */
	public static List<Cue> all() {
		synchronized (ALL) {
			return List.copyOf(ALL);
		}
	}

	/** Shapes a caller volume: as given up to 1, compressed above it. */
	public static float shapeVolume(float volume) {
		float v = Math.max(0f, volume);
		return v <= 1f ? v : 1f + (v - 1f) * 0.35f;
	}

	/** Shapes a caller pitch: halfway back toward 1. */
	public static float shapePitch(float pitch) {
		return 1f + (pitch - 1f) * 0.5f;
	}

	/**
	 * Plays {@code cue} attached to {@code entity} for everyone nearby: the undelayed layers follow it (a long sound -- the user's
	 * power-down -- stays on a running alien); delayed layers play where it was.
	 */
	public static void playOn(Entity entity, Cue cue, float volume, float pitch, SoundSource source) {
		if (entity == null || cue == null || cue.layers.isEmpty() || !(entity.level() instanceof ServerLevel level)) {
			return;
		}
		if (throttled(cue.name + '@' + entity.getUUID(), cue, level.getGameTime())) {
			return;
		}
		float v = shapeVolume(volume);
		float q = shapePitch(pitch) * (1f + cue.jitter * (level.random.nextFloat() * 2f - 1f));
		for (Layer l : cue.layers) {
			float lv = l.volume() * v;
			float lp = Mth.clamp(l.pitch() * q, 0.5f, 2.0f);
			if (lv <= 0.001f) {
				continue;
			}
			if (l.delay() <= 0 || level.getServer() == null) {
				level.playSeededSound(null, entity, l.sound(), source, lv, lp, level.random.nextLong());
			} else {
				PENDING.add(new Pending(level, null, entity.position(), l.sound(), source, lv, lp, level.getServer().getTickCount() + l.delay()));
			}
		}
	}

	/** Plays {@code cue} at {@code at} for everyone nearby (player sounds). */
	public static void play(ServerLevel level, Vec3 at, Cue cue, float volume, float pitch) {
		play(level, at, cue, volume, pitch, SoundSource.PLAYERS);
	}

	public static void play(ServerLevel level, Vec3 at, Cue cue, float volume, float pitch, SoundSource source) {
		if (level == null || at == null || cue == null || cue.layers.isEmpty()) {
			return;
		}
		String key = cue.name + '@' + level.dimension().location() + '@' + Mth.floor(at.x / CELL) + ',' + Mth.floor(at.y / CELL) + ','
				+ Mth.floor(at.z / CELL);
		if (throttled(key, cue, level.getGameTime())) {
			return;
		}
		emitAll(level, null, at, cue, volume, pitch, source);
	}

	/** Plays {@code cue} to {@code player} alone, at their position (the watch's own UI sounds). */
	public static void notify(ServerPlayer player, Cue cue, float volume, float pitch) {
		if (player == null || cue == null || cue.layers.isEmpty()) {
			return;
		}
		if (throttled(cue.name + '#' + player.getUUID(), cue, player.serverLevel().getGameTime())) {
			return;
		}
		emitAll(player.serverLevel(), player, player.position(), cue, volume, pitch, SoundSource.PLAYERS);
	}

	private static boolean throttled(String key, Cue cue, long now) {
		if (cue.gap <= 0) {
			return false;
		}
		Long last = LAST.get(key);
		if (last != null && now >= last && now - last < cue.gap) {
			return true;
		}
		LAST.put(key, now);
		if (LAST.size() > 1024) {
			LAST.values().removeIf(t -> now - t > 200 || now < t);
		}
		return false;
	}

	private static void emitAll(ServerLevel level, ServerPlayer only, Vec3 at, Cue cue, float volume, float pitch, SoundSource source) {
		float v = shapeVolume(volume);
		float q = shapePitch(pitch) * (1f + cue.jitter * (level.random.nextFloat() * 2f - 1f));
		MinecraftServer server = level.getServer();
		for (Layer l : cue.layers) {
			float lv = l.volume() * v;
			float lp = Mth.clamp(l.pitch() * q, 0.5f, 2.0f);
			if (lv <= 0.001f) {
				continue;
			}
			if (l.delay() <= 0 || server == null) {
				emit(level, only, at, l.sound(), source, lv, lp);
			} else {
				PENDING.add(new Pending(level, only, at, l.sound(), source, lv, lp, server.getTickCount() + l.delay()));
			}
		}
	}

	private static void emit(ServerLevel level, ServerPlayer only, Vec3 at, Holder<SoundEvent> sound, SoundSource source, float volume,
			float pitch) {
		if (only != null) {
			if (only.hasDisconnected() || only.isRemoved()) {
				return;
			}
			only.connection.send(new ClientboundSoundPacket(sound, source, only.getX(), only.getY(), only.getZ(), volume, pitch,
					only.getRandom().nextLong()));
		} else {
			level.playSeededSound(null, at.x, at.y, at.z, sound, source, volume, pitch, level.random.nextLong());
		}
	}

	/** END_SERVER_TICK: plays the delayed layers that are due. */
	public static void tick(MinecraftServer server) {
		if (PENDING.isEmpty()) {
			return;
		}
		int now = server.getTickCount();
		List<Pending> due = new ArrayList<>();
		for (Iterator<Pending> it = PENDING.iterator(); it.hasNext();) {
			Pending p = it.next();
			if (now >= p.due() || p.due() - now > 200) {
				it.remove();
				due.add(p);
			}
		}
		for (Pending p : due) {
			if (p.level().getServer() == server) {
				emit(p.level(), p.only(), p.at(), p.sound(), p.source(), p.volume(), p.pitch());
			}
		}
	}

	/** For the sound gametest: how many delayed layers are waiting. */
	public static int pendingCount() {
		return PENDING.size();
	}

	public static void clearSessionState() {
		PENDING.clear();
		LAST.clear();
	}
}
