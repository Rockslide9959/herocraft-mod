package com.projecthero.mod.greenlantern.recon;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * v0.17.1: the Power Ring's remembered places -- up to {@link GreenLanternReconConfig#MAX_WAYPOINTS} named waypoints per
 * Lantern, each tied to the dimension it was saved in (a Hard-Light Gateway only reaches waypoints in the world you are
 * standing in). Its own persistent attachment ({@code projecthero:green_lantern_waypoints}), kept through death and ring
 * removal, synced to its owner only (the Waypoints screen and the gateway picker read it). Immutable: every change makes a
 * new list and re-saves it (see {@link GreenLanternGateway}).
 */
public record GreenLanternWaypoints(List<Waypoint> list) {
	public static final GreenLanternWaypoints EMPTY = new GreenLanternWaypoints(List.of());

	/** One waypoint: a name, the dimension id, the feet position and the way the Lantern faced when saving it. */
	public record Waypoint(String name, String dimension, double x, double y, double z, float yaw) {
		public static final Codec<Waypoint> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("name").forGetter(Waypoint::name),
				Codec.STRING.fieldOf("dim").forGetter(Waypoint::dimension),
				Codec.DOUBLE.fieldOf("x").forGetter(Waypoint::x),
				Codec.DOUBLE.fieldOf("y").forGetter(Waypoint::y),
				Codec.DOUBLE.fieldOf("z").forGetter(Waypoint::z),
				Codec.FLOAT.optionalFieldOf("yaw", 0f).forGetter(Waypoint::yaw)
		).apply(i, Waypoint::new));

		public Waypoint renamed(String to) {
			return new Waypoint(to, dimension, x, y, z, yaw);
		}

		public double distanceTo(double px, double py, double pz) {
			double dx = x - px;
			double dy = y - py;
			double dz = z - pz;
			return Math.sqrt(dx * dx + dy * dy + dz * dz);
		}
	}

	public static final Codec<GreenLanternWaypoints> CODEC = Waypoint.CODEC.listOf()
			.xmap(GreenLanternWaypoints::new, GreenLanternWaypoints::list);

	public GreenLanternWaypoints {
		list = List.copyOf(list);
	}

	public int size() {
		return list.size();
	}

	public Waypoint get(int i) {
		return i >= 0 && i < list.size() ? list.get(i) : null;
	}

	public boolean full() {
		return list.size() >= GreenLanternReconConfig.MAX_WAYPOINTS;
	}

	public GreenLanternWaypoints with(Waypoint w) {
		List<Waypoint> out = new ArrayList<>(list);
		out.add(w);
		return new GreenLanternWaypoints(out);
	}

	public GreenLanternWaypoints set(int i, Waypoint w) {
		List<Waypoint> out = new ArrayList<>(list);
		out.set(i, w);
		return new GreenLanternWaypoints(out);
	}

	public GreenLanternWaypoints without(int i) {
		List<Waypoint> out = new ArrayList<>(list);
		out.remove(i);
		return new GreenLanternWaypoints(out);
	}

	/** A name as the ring keeps it: trimmed, no control characters, at most {@link GreenLanternReconConfig#WAYPOINT_NAME_MAX}. */
	public static String clean(String name) {
		if (name == null) {
			return "";
		}
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < name.length() && b.length() < GreenLanternReconConfig.WAYPOINT_NAME_MAX; i++) {
			char c = name.charAt(i);
			if (c >= 32 && c != 127 && c != '§') {
				b.append(c);
			}
		}
		return b.toString().trim();
	}
}
