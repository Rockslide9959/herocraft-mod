package com.projecthero.mod.backpack;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server (v0.17.5): the Open Backpack key was pressed. The server picks the backpack ({@link BackpackMod#openKey}). */
public record OpenBackpackPayload() implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<OpenBackpackPayload> TYPE = new CustomPacketPayload.Type<>(ProjectHeroMod.id("open_backpack"));

	public static final StreamCodec<RegistryFriendlyByteBuf, OpenBackpackPayload> CODEC = StreamCodec.unit(new OpenBackpackPayload());

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
