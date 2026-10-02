package com.projecthero.mod.stormbreaker;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * v0.14.20: a player's three saved Bifrost waypoints. Immutable -- every change returns a new value, which is then
 * stored back on the {@link com.projecthero.mod.attachment.ModAttachments#BIFROST_WAYPOINTS} attachment (persistent and
 * kept through death, so the list survives relogs, dying and losing the axe).
 *
 * <p>Always exactly {@link #SLOTS} entries; an unused slot is {@link Waypoint#EMPTY} (no dimension).
 */
public record BifrostWaypoints(List<Waypoint> slots) {
	public static final int SLOTS = 3;
	public static final int MAX_NAME_LENGTH = 20;

	/** One saved spot: a name, the block the player stood in, and the dimension id it belongs to. */
	public record Waypoint(String name, int x, int y, int z, String dimension) {
		public static final Waypoint EMPTY = new Waypoint("", 0, 0, 0, "");

		public static final Codec<Waypoint> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.optionalFieldOf("name", "").forGetter(Waypoint::name),
				Codec.INT.fieldOf("x").forGetter(Waypoint::x),
				Codec.INT.fieldOf("y").forGetter(Waypoint::y),
				Codec.INT.fieldOf("z").forGetter(Waypoint::z),
				Codec.STRING.optionalFieldOf("dimension", "").forGetter(Waypoint::dimension)
		).apply(i, Waypoint::new));

		public static final StreamCodec<ByteBuf, Waypoint> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8, Waypoint::name,
				ByteBufCodecs.VAR_INT, Waypoint::x,
				ByteBufCodecs.VAR_INT, Waypoint::y,
				ByteBufCodecs.VAR_INT, Waypoint::z,
				ByteBufCodecs.STRING_UTF8, Waypoint::dimension,
				Waypoint::new);

		public boolean isEmpty() {
			return dimension.isEmpty();
		}
	}

	public static final BifrostWaypoints EMPTY = new BifrostWaypoints(List.of(Waypoint.EMPTY, Waypoint.EMPTY, Waypoint.EMPTY));

	public static final Codec<BifrostWaypoints> CODEC = Waypoint.CODEC.listOf()
			.xmap(BifrostWaypoints::new, BifrostWaypoints::slots);

	public static final StreamCodec<ByteBuf, BifrostWaypoints> STREAM_CODEC =
			Waypoint.STREAM_CODEC.apply(ByteBufCodecs.list(SLOTS)).map(BifrostWaypoints::new, BifrostWaypoints::slots);

	/** Pads or trims to exactly {@link #SLOTS} entries, whatever an old save or a packet held. */
	public BifrostWaypoints {
		List<Waypoint> fixed = new ArrayList<>(SLOTS);
		for (int i = 0; i < SLOTS; i++) {
			Waypoint w = slots != null && i < slots.size() ? slots.get(i) : null;
			fixed.add(w == null ? Waypoint.EMPTY : w);
		}
		slots = List.copyOf(fixed);
	}

	public Waypoint get(int slot) {
		return slot >= 0 && slot < SLOTS ? slots.get(slot) : Waypoint.EMPTY;
	}

	public BifrostWaypoints with(int slot, Waypoint waypoint) {
		if (slot < 0 || slot >= SLOTS) {
			return this;
		}
		List<Waypoint> copy = new ArrayList<>(slots);
		copy.set(slot, waypoint == null ? Waypoint.EMPTY : waypoint);
		return new BifrostWaypoints(copy);
	}

	/** Trims and shortens a typed name; blank becomes "Waypoint N". */
	public static String cleanName(String name, int slot) {
		String s = name == null ? "" : name.replaceAll("[\\p{Cntrl}§]", "").trim();
		if (s.length() > MAX_NAME_LENGTH) {
			s = s.substring(0, MAX_NAME_LENGTH).trim();
		}
		return s.isEmpty() ? "Waypoint " + (slot + 1) : s;
	}
}
