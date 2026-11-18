package com.projecthero.mod.squad;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * v0.17.5 squad knockdown: client &rarr; server, sent once the knocked player has held the jump key for
 * {@link SquadKnockdown#GIVE_UP_TICKS}. The server re-checks that they are still knocked and not being revived.
 */
public record SquadKnockdownGiveUpPayload() implements CustomPacketPayload {
	public static final SquadKnockdownGiveUpPayload INSTANCE = new SquadKnockdownGiveUpPayload();

	public static final CustomPacketPayload.Type<SquadKnockdownGiveUpPayload> TYPE =
			new CustomPacketPayload.Type<>(ProjectHeroMod.id("squad_knockdown_give_up"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SquadKnockdownGiveUpPayload> CODEC = StreamCodec.unit(INSTANCE);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
