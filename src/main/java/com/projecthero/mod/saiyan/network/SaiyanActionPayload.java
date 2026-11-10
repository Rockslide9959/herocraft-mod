package com.projecthero.mod.saiyan.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server (v0.17.3): Q held / let go (charge Ki), and a pick on the H form wheel ({@code arg} = the
 * {@code SaiyanForm} ordinal). The server re-checks everything.
 */
public record SaiyanActionPayload(Action action, int arg) implements CustomPacketPayload {
	public enum Action {
		CHARGE_START,
		CHARGE_STOP,
		PICK_FORM
	}

	public static final CustomPacketPayload.Type<SaiyanActionPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "saiyan_action"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SaiyanActionPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT.map(i -> Action.values()[Math.floorMod(i, Action.values().length)], Action::ordinal),
			SaiyanActionPayload::action,
			ByteBufCodecs.VAR_INT,
			SaiyanActionPayload::arg,
			SaiyanActionPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
