package com.projecthero.mod.hulk.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * v0.16.4 (user): where a player was standing when the Gamma Reactor blew and he was pulled into his own mind
 * ({@code HulkMind}) -- the dimension id, position and rotation he is sent back to. Saved with the player, so a crash or a
 * relog in the middle of it still brings him home (and finishes the change).
 */
public record HulkMindReturn(String dimension, double x, double y, double z, float yRot, float xRot) {
	public static final Codec<HulkMindReturn> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("dimension").forGetter(HulkMindReturn::dimension),
			Codec.DOUBLE.fieldOf("x").forGetter(HulkMindReturn::x),
			Codec.DOUBLE.fieldOf("y").forGetter(HulkMindReturn::y),
			Codec.DOUBLE.fieldOf("z").forGetter(HulkMindReturn::z),
			Codec.FLOAT.fieldOf("yRot").forGetter(HulkMindReturn::yRot),
			Codec.FLOAT.fieldOf("xRot").forGetter(HulkMindReturn::xRot)).apply(i, HulkMindReturn::new));
}
