package com.projecthero.mod.stand.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * v0.17.2: the pose a Stand user is striking (N) -- the USER's body, not the Stand, so it lives apart from
 * {@link StandState} and is synced to everyone ({@code projecthero:stand_pose}): anybody can see a player strike a pose.
 * {@code pose} 0 = none; {@code start} = game time it began.
 */
public record StandPoseState(int pose, long start) {
	public static final StandPoseState NONE = new StandPoseState(0, 0L);

	public static final Codec<StandPoseState> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.optionalFieldOf("pose", 0).forGetter(StandPoseState::pose),
			Codec.LONG.optionalFieldOf("start", 0L).forGetter(StandPoseState::start)
	).apply(i, StandPoseState::new));
}
