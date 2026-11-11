package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.17.3: client -> server, Undead's right click held / let go while he flies (the boost: 32 b/s, the body burns away over 10 s).
 * Sent on the edges only; the server re-checks that he is flying ({@code UndeadFlight.setBoost}).
 */
public record UndeadBoostPayload(boolean held) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<UndeadBoostPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "undead_boost"));

	public static final StreamCodec<RegistryFriendlyByteBuf, UndeadBoostPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, UndeadBoostPayload::held, UndeadBoostPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
