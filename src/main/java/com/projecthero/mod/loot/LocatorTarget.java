package com.projecthero.mod.loot;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.GlobalPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.17.5: the {@code projecthero:locator_target} component on a Locator -- which place tier it seeks (2 Site, 3 Hideout,
 * 4 Dungeon) and, once used, where it found one ({@code pos}, with dimension) and which structure it is.
 * In a loot table: {@code "projecthero:locator_target": {"tier": 3}}.
 */
public record LocatorTarget(int tier, Optional<GlobalPos> pos, Optional<ResourceLocation> structure) {
	public static final int MIN_TIER = 2;
	public static final int MAX_TIER = 4;

	public static final Codec<LocatorTarget> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.intRange(MIN_TIER, MAX_TIER).fieldOf("tier").forGetter(LocatorTarget::tier),
			GlobalPos.CODEC.optionalFieldOf("pos").forGetter(LocatorTarget::pos),
			ResourceLocation.CODEC.optionalFieldOf("structure").forGetter(LocatorTarget::structure))
			.apply(i, LocatorTarget::new));

	public static final StreamCodec<ByteBuf, LocatorTarget> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, LocatorTarget::tier,
			ByteBufCodecs.optional(GlobalPos.STREAM_CODEC), LocatorTarget::pos,
			ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC), LocatorTarget::structure,
			LocatorTarget::new);

	public static LocatorTarget unbound(int tier) {
		return new LocatorTarget(clampTier(tier), Optional.empty(), Optional.empty());
	}

	public LocatorTarget bound(GlobalPos at, ResourceLocation structureId) {
		return new LocatorTarget(tier, Optional.of(at), Optional.of(structureId));
	}

	public boolean isBound() {
		return pos.isPresent();
	}

	public static int clampTier(int tier) {
		return Math.max(MIN_TIER, Math.min(MAX_TIER, tier));
	}
}
