package com.projecthero.mod.flight.landing;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.17.1, server &rarr; every client tracking the lander (and the lander): a superhero landing started
 * ({@code style} &gt; 0, with its {@code strength} 0..1) or ended ({@code style} = 0: blend back to standing now). Purely
 * cosmetic -- the pose, the first-person dip and the Silver Surfer's board under his feet.
 */
public record HeroLandingPayload(int entityId, int style, float strength) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<HeroLandingPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "hero_landing"));

	public static final StreamCodec<RegistryFriendlyByteBuf, HeroLandingPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, HeroLandingPayload::entityId,
			ByteBufCodecs.VAR_INT, HeroLandingPayload::style,
			ByteBufCodecs.FLOAT, HeroLandingPayload::strength,
			HeroLandingPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
