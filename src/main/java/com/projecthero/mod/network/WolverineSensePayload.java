package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server &rarr; client: what the Wolverine's senses currently pick up. {@code hunters} are mobs that are
 * targeting him right now (glow orange, refreshed every scan and self-expiring on the client);
 * {@code sniffed} are the living things a Sniff marked, kept for {@code sniffTicks}. Sent only to the
 * Wolverine himself -- nothing is set on any entity, so no other player sees a glow.
 */
public record WolverineSensePayload(int[] hunters, int[] sniffed, int sniffTicks) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<WolverineSensePayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "wolverine_sense"));

	public static final StreamCodec<RegistryFriendlyByteBuf, WolverineSensePayload> CODEC =
			StreamCodec.ofMember(WolverineSensePayload::write, WolverineSensePayload::read);

	private static void write(WolverineSensePayload p, RegistryFriendlyByteBuf buf) {
		buf.writeVarIntArray(p.hunters);
		buf.writeVarIntArray(p.sniffed);
		buf.writeVarInt(p.sniffTicks);
	}

	private static WolverineSensePayload read(RegistryFriendlyByteBuf buf) {
		return new WolverineSensePayload(buf.readVarIntArray(), buf.readVarIntArray(), buf.readVarInt());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
