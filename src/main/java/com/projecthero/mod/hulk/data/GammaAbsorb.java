package com.projecthero.mod.hulk.data;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * v0.16.4 (user): a player soaking up an overloading Gamma Reactor's flash -- which reactor, and the game time its 10-second
 * flash began. Synced to everyone (not saved) so every viewer draws the green flash, the energy streaming into him and
 * his kneeling pose on the same clock ({@code GammaOverload}).
 */
public record GammaAbsorb(BlockPos reactor, long startedAt) {
	public static final StreamCodec<ByteBuf, GammaAbsorb> STREAM_CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, GammaAbsorb::reactor,
			ByteBufCodecs.VAR_LONG, GammaAbsorb::startedAt,
			GammaAbsorb::new);
}
