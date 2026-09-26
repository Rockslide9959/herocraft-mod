package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server &rarr; client (v0.12.43): what a Titan's roar picked up -- the entities within the highlight radius, outlined blue for
 * {@code ticks}. Sent only to the roaring shifter; nothing is set on any entity, so nobody else sees the outline.
 */
public record TitanRoarSensePayload(int[] ids, int ticks) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<TitanRoarSensePayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "titan_roar_sense"));

	public static final StreamCodec<RegistryFriendlyByteBuf, TitanRoarSensePayload> CODEC =
			StreamCodec.ofMember(TitanRoarSensePayload::write, TitanRoarSensePayload::read);

	private static void write(TitanRoarSensePayload p, RegistryFriendlyByteBuf buf) {
		buf.writeVarIntArray(p.ids);
		buf.writeVarInt(p.ticks);
	}

	private static TitanRoarSensePayload read(RegistryFriendlyByteBuf buf) {
		return new TitanRoarSensePayload(buf.readVarIntArray(), buf.readVarInt());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
