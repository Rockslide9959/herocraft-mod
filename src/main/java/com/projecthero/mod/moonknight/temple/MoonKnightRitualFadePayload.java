package com.projecthero.mod.moonknight.temple;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server &rarr; client: the Khonshu ritual's "death" -- fade the screen to white and back over {@code ticks}.
 * Purely cosmetic; the rebirth itself is timed and applied on the server ({@link KhonshuRitual}).
 */
public record MoonKnightRitualFadePayload(int ticks) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<MoonKnightRitualFadePayload> TYPE =
			new CustomPacketPayload.Type<>(ProjectHeroMod.id("moon_knight_ritual_fade"));

	public static final StreamCodec<RegistryFriendlyByteBuf, MoonKnightRitualFadePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, MoonKnightRitualFadePayload::ticks,
			MoonKnightRitualFadePayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
