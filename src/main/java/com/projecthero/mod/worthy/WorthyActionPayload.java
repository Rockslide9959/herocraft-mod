package com.projecthero.mod.worthy;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server (v0.17.7 Thor x Super Soldier): the Thor weapon screen's "Call back" button ({@link #CALL_BACK}, {@code arg} = the
 * ThorWeapon ordinal) and a lent-weapon Super Soldier's double-tap-Space flight toggle ({@link #TOGGLE_FLIGHT}). The server re-checks
 * everything.
 */
public record WorthyActionPayload(int action, int arg) implements CustomPacketPayload {
	public static final int CALL_BACK = 0;
	public static final int TOGGLE_FLIGHT = 1;

	public static final CustomPacketPayload.Type<WorthyActionPayload> TYPE = new CustomPacketPayload.Type<>(ProjectHeroMod.id("worthy_action"));
	public static final StreamCodec<RegistryFriendlyByteBuf, WorthyActionPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, WorthyActionPayload::action,
			ByteBufCodecs.VAR_INT, WorthyActionPayload::arg,
			WorthyActionPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
