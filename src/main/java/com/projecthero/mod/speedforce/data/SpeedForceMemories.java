package com.projecthero.mod.speedforce.data;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * v0.17.1 (user, "Speed Force Memories"): the places a Speed Force Speedster's memory road leads back to, saved on the
 * runner ({@code projecthero:speed_force_memories}: persistent, kept through death, synced to the owner only -- the Shift+N
 * menu lists the marked places). Kept apart from {@link SpeedForceState}, whose codec is at its 16 fields.
 *
 * <ul>
 *   <li>{@link #marked}: up to {@code MEMORY_MARKED_MAX} places marked from the menu ("Remember this place"), oldest first;
 *       a sixth replaces the oldest;</li>
 *   <li>{@link #exits}: the last {@code MEMORY_EXITS_MAX} places they came out of the Speed Force at, newest first.</li>
 * </ul>
 * Immutable: every change returns a new value (re-saved through {@code setAttached}).
 */
public record SpeedForceMemories(List<Place> marked, List<Place> exits) {
	/** One remembered spot: the dimension's id, the feet position and the way they faced. */
	public record Place(String dimension, double x, double y, double z, float yaw) {
		public static final Codec<Place> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("dimension").forGetter(Place::dimension),
				Codec.DOUBLE.fieldOf("x").forGetter(Place::x),
				Codec.DOUBLE.fieldOf("y").forGetter(Place::y),
				Codec.DOUBLE.fieldOf("z").forGetter(Place::z),
				Codec.FLOAT.optionalFieldOf("yaw", 0f).forGetter(Place::yaw)
		).apply(i, Place::new));

		public boolean near(Place o, double range) {
			if (!dimension.equals(o.dimension)) {
				return false;
			}
			double dx = x - o.x;
			double dy = y - o.y;
			double dz = z - o.z;
			return dx * dx + dy * dy + dz * dz <= range * range;
		}
	}

	public static final int MARKED_MAX = 5;
	public static final int EXITS_MAX = 5;
	public static final SpeedForceMemories EMPTY = new SpeedForceMemories(List.of(), List.of());

	public static final Codec<SpeedForceMemories> CODEC = RecordCodecBuilder.create(i -> i.group(
			Place.CODEC.listOf().optionalFieldOf("marked", List.of()).forGetter(SpeedForceMemories::marked),
			Place.CODEC.listOf().optionalFieldOf("exits", List.of()).forGetter(SpeedForceMemories::exits)
	).apply(i, SpeedForceMemories::new));

	public SpeedForceMemories {
		marked = List.copyOf(marked);
		exits = List.copyOf(exits);
	}

	/** Marks {@code p}: appended; past {@link #MARKED_MAX} the oldest is dropped. */
	public SpeedForceMemories remember(Place p) {
		List<Place> m = new ArrayList<>(marked);
		m.add(p);
		while (m.size() > MARKED_MAX) {
			m.remove(0);
		}
		return new SpeedForceMemories(m, exits);
	}

	/** Forgets marked place {@code index} (unchanged if there is none). */
	public SpeedForceMemories forget(int index) {
		if (index < 0 || index >= marked.size()) {
			return this;
		}
		List<Place> m = new ArrayList<>(marked);
		m.remove(index);
		return new SpeedForceMemories(m, exits);
	}

	/** A new exit point, newest first; one within {@code merge} blocks of an older one (same dimension) replaces it. */
	public SpeedForceMemories withExit(Place p, double merge) {
		List<Place> e = new ArrayList<>();
		e.add(p);
		for (Place old : exits) {
			if (!old.near(p, merge)) {
				e.add(old);
			}
		}
		while (e.size() > EXITS_MAX) {
			e.remove(e.size() - 1);
		}
		return new SpeedForceMemories(marked, e);
	}
}
