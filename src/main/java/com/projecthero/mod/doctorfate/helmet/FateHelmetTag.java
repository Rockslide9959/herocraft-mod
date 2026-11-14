package com.projecthero.mod.doctorfate.helmet;

import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * v0.17.4 the identity every physical Helmet of Fate carries (the item's {@code projecthero:fate_helmet} component, and the placed
 * {@link FateHelmetEntity}): whose helmet it is and which <b>generation</b>. {@link FateHelmetRegistry} keeps each owner's current
 * generation; a copy with an older one is a ghost of a helmet that has since been summoned away, and deletes itself as soon as it
 * is seen (the Mjolnir recall scheme).
 */
public record FateHelmetTag(UUID owner, String ownerName, int generation) {
	public static final Codec<FateHelmetTag> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.CODEC.fieldOf("owner").forGetter(FateHelmetTag::owner),
			Codec.STRING.optionalFieldOf("owner_name", "").forGetter(FateHelmetTag::ownerName),
			Codec.INT.optionalFieldOf("generation", 0).forGetter(FateHelmetTag::generation)
	).apply(i, FateHelmetTag::new));

	public static final StreamCodec<ByteBuf, FateHelmetTag> STREAM_CODEC = StreamCodec.composite(
			UUIDUtil.STREAM_CODEC, FateHelmetTag::owner,
			ByteBufCodecs.STRING_UTF8, FateHelmetTag::ownerName,
			ByteBufCodecs.VAR_INT, FateHelmetTag::generation,
			FateHelmetTag::new);
}
