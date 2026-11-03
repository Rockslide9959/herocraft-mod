package com.projecthero.mod.omnitrix.alien.alienx;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * v0.17.2: the council's state for the screen (server to client) -- the phase, what is around his body, cooldowns, the debate
 * (meters, turn, offered arguments, revealed concerns) and the transcript, as one tag ({@link AlienXCouncil#stateTag}).
 * {@code open}: open the screen if it is not already.
 */
public record AlienXCouncilStatePayload(boolean open, CompoundTag state) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<AlienXCouncilStatePayload> TYPE = new CustomPacketPayload.Type<>(
			ProjectHeroMod.id("alien_x_council_state"));

	public static final StreamCodec<RegistryFriendlyByteBuf, AlienXCouncilStatePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, AlienXCouncilStatePayload::open,
			ByteBufCodecs.COMPOUND_TAG, AlienXCouncilStatePayload::state,
			AlienXCouncilStatePayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
