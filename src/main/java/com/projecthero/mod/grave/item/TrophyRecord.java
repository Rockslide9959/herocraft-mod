package com.projecthero.mod.grave.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * v0.14.4: who took a boss trophy head, and on which in-game day. Stamped on the head when the boss drops it, shown in
 * its tooltip, and carried onto -- and back off -- the placed head block, so a trophy wall remembers every kill.
 *
 * @param slayer the name of whoever got the kill credit (a player's name, or a mob's name if a mob finished it)
 * @param day    the world day it happened on, counted from 1 like the F3 day counter
 */
public record TrophyRecord(String slayer, long day) {
	public static final Codec<TrophyRecord> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("slayer").forGetter(TrophyRecord::slayer),
			Codec.LONG.fieldOf("day").forGetter(TrophyRecord::day)).apply(i, TrophyRecord::new));

	public static final StreamCodec<ByteBuf, TrophyRecord> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, TrophyRecord::slayer,
			ByteBufCodecs.VAR_LONG, TrophyRecord::day,
			TrophyRecord::new);

	/** The world day ({@code dayTime / 24000 + 1}) for a level's current day time. */
	public static long dayOf(long dayTime) {
		return Math.max(0L, dayTime) / 24000L + 1L;
	}
}
