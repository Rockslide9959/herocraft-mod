package com.projecthero.mod.doctorfate.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server (v0.17.3 Doctor Fate): the double-tap-Space flight toggle, a Space press while possessed (struggle), the answer
 * to one of Nabu's bargains ({@code arg} = the bargain kind), the answer to "Will you serve Order?" ({@code pos} = the Tomb
 * pedestal) and H (the Helmet of Fate on / off, like Nova's suit). The server re-checks everything.
 */
public record FateActionPayload(Action action, int arg, BlockPos pos) implements CustomPacketPayload {
	public enum Action {
		TOGGLE_FLIGHT,
		STRUGGLE,
		BARGAIN_ACCEPT,
		BARGAIN_REFUSE,
		SERVE_ACCEPT,
		SERVE_REFUSE,
		/** v0.17.3 (user: "make it similar to Nova"): H puts the Helmet of Fate on / takes it off. */
		TOGGLE_HELMET,
		/** v0.17.3 the Tower of Fate mindscape: a dialogue choice ({@code arg} = {@code DoctorFateMindscape.Choice} ordinal). */
		MIND_CHOICE,
		/** v0.17.3 the mindscape screen closed. */
		MIND_CLOSE
	}

	public static final CustomPacketPayload.Type<FateActionPayload> TYPE = new CustomPacketPayload.Type<>(ProjectHeroMod.id("doctor_fate_action"));

	public static final StreamCodec<RegistryFriendlyByteBuf, FateActionPayload> CODEC = StreamCodec.of((buf, p) -> {
		buf.writeVarInt(p.action.ordinal());
		buf.writeVarInt(p.arg);
		buf.writeBlockPos(p.pos);
	}, buf -> new FateActionPayload(Action.values()[Math.floorMod(buf.readVarInt(), Action.values().length)], buf.readVarInt(), buf.readBlockPos()));

	public static FateActionPayload of(Action action) {
		return new FateActionPayload(action, 0, BlockPos.ZERO);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
