package com.projecthero.mod.speedforce.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * v0.17.1: a trip through the Speed Force, saved on the runner while they are inside ({@code projecthero:speed_force_trip},
 * persistent, server-only): where they went in (the dimension, the spot and the way they were facing -- the road inside runs
 * that way), where they came out on the road, their lane, and how long they have been inside. Removed on the way out; a
 * runner who logs out inside comes out on the next login where the run had taken them.
 */
public record SpeedForceTrip(String dimension, double x, double y, double z, float yaw, double entryX, double entryY, double entryZ,
		int lane, int ticks) {
	public static final Codec<SpeedForceTrip> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("dimension").forGetter(SpeedForceTrip::dimension),
			Codec.DOUBLE.fieldOf("x").forGetter(SpeedForceTrip::x),
			Codec.DOUBLE.fieldOf("y").forGetter(SpeedForceTrip::y),
			Codec.DOUBLE.fieldOf("z").forGetter(SpeedForceTrip::z),
			Codec.FLOAT.fieldOf("yaw").forGetter(SpeedForceTrip::yaw),
			Codec.DOUBLE.fieldOf("entry_x").forGetter(SpeedForceTrip::entryX),
			Codec.DOUBLE.fieldOf("entry_y").forGetter(SpeedForceTrip::entryY),
			Codec.DOUBLE.fieldOf("entry_z").forGetter(SpeedForceTrip::entryZ),
			Codec.INT.optionalFieldOf("lane", 0).forGetter(SpeedForceTrip::lane),
			Codec.INT.optionalFieldOf("ticks", 0).forGetter(SpeedForceTrip::ticks)
	).apply(i, SpeedForceTrip::new));

	public SpeedForceTrip withTicks(int t) {
		return new SpeedForceTrip(dimension, x, y, z, yaw, entryX, entryY, entryZ, lane, t);
	}
}
