package com.projecthero.mod.gyro.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * v0.17.3 Gyro Zeppeli: what the Spin is doing to one creature ({@code projecthero:gyro_mark}), on any living entity. Not saved,
 * synced to every client (the twisting skin rings, the greyed and cracked skin, a spun player's view drift). Game times, 0 = none.
 *
 * @param spunUntil   a ball hit: the body spins (stumbles, can't aim)
 * @param lockedUntil Spin Touch: the joints are locked (can't move)
 * @param agedUntil   Ball Breaker: aged (grey, cracked)
 * @param healUntil   Healing Spin: healing until this time...
 * @param healPerTick ...this much a tick
 */
public record GyroMark(long spunUntil, long lockedUntil, long agedUntil, long healUntil, float healPerTick) {
	public static final GyroMark NONE = new GyroMark(0L, 0L, 0L, 0L, 0f);

	public boolean spun(long now) {
		return spunUntil > now;
	}

	public boolean locked(long now) {
		return lockedUntil > now;
	}

	public boolean aged(long now) {
		return agedUntil > now;
	}

	public boolean healing(long now) {
		return healUntil > now;
	}

	/** Nothing running any more at {@code now}. */
	public boolean idle(long now) {
		return !spun(now) && !locked(now) && !aged(now) && !healing(now);
	}

	public GyroMark withSpun(long until) {
		return new GyroMark(Math.max(spunUntil, until), lockedUntil, agedUntil, healUntil, healPerTick);
	}

	public GyroMark withLocked(long until) {
		return new GyroMark(spunUntil, Math.max(lockedUntil, until), agedUntil, healUntil, healPerTick);
	}

	public GyroMark withAged(long until) {
		return new GyroMark(spunUntil, lockedUntil, Math.max(agedUntil, until), healUntil, healPerTick);
	}

	public GyroMark withHeal(long until, float perTick) {
		return new GyroMark(spunUntil, lockedUntil, agedUntil, until, perTick);
	}

	public static final Codec<GyroMark> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.optionalFieldOf("spun", 0L).forGetter(GyroMark::spunUntil),
			Codec.LONG.optionalFieldOf("locked", 0L).forGetter(GyroMark::lockedUntil),
			Codec.LONG.optionalFieldOf("aged", 0L).forGetter(GyroMark::agedUntil),
			Codec.LONG.optionalFieldOf("heal", 0L).forGetter(GyroMark::healUntil),
			Codec.FLOAT.optionalFieldOf("heal_per_tick", 0f).forGetter(GyroMark::healPerTick)
	).apply(i, GyroMark::new));
}
