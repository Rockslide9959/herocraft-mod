package com.projecthero.mod.omnitrix.alien.alienx;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * v0.17.2: the council screen's requests (client to server). {@code op} is one of the {@code OP_} constants; {@code action} an
 * action id ({@link AlienXActions}); {@code value} a target id or an argument (tone) index. The server validates all of it.
 */
public record AlienXCouncilPayload(int op, String action, int value) implements CustomPacketPayload {
	public static final int OP_OPEN = 0, OP_PROPOSE = 1, OP_ARGUE = 2, OP_LISTEN = 3, OP_WITHDRAW = 4, OP_LEAVE = 5, OP_BACK = 6, OP_CLOSE = 7;

	public static final CustomPacketPayload.Type<AlienXCouncilPayload> TYPE = new CustomPacketPayload.Type<>(ProjectHeroMod.id("alien_x_council"));

	public static final StreamCodec<RegistryFriendlyByteBuf, AlienXCouncilPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, AlienXCouncilPayload::op,
			ByteBufCodecs.stringUtf8(64), AlienXCouncilPayload::action,
			ByteBufCodecs.VAR_INT, AlienXCouncilPayload::value,
			AlienXCouncilPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
