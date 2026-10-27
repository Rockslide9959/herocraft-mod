package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client &rarr; server (v0.17.1): the Waypoints screen and the gateway picker. {@link #ADD} saves a waypoint where the
 * Lantern stands ({@code name}); {@link #RENAME} / {@link #DELETE} change waypoint {@code index}; {@link #OPEN} starts a
 * Hard-Light Gateway to waypoint {@code index}. The server re-checks everything ({@code GreenLanternGateway}).
 */
public record GreenLanternGatewayPayload(int action, int index, String name) implements CustomPacketPayload {
	public static final int ADD = 0;
	public static final int RENAME = 1;
	public static final int DELETE = 2;
	public static final int OPEN = 3;

	public static final CustomPacketPayload.Type<GreenLanternGatewayPayload> TYPE = new CustomPacketPayload.Type<>(
			ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "green_lantern_gateway"));

	public static final StreamCodec<RegistryFriendlyByteBuf, GreenLanternGatewayPayload> CODEC =
			StreamCodec.ofMember(GreenLanternGatewayPayload::write, GreenLanternGatewayPayload::read);

	private static void write(GreenLanternGatewayPayload p, RegistryFriendlyByteBuf buf) {
		buf.writeVarInt(p.action);
		buf.writeVarInt(p.index);
		buf.writeUtf(p.name, 64);
	}

	private static GreenLanternGatewayPayload read(RegistryFriendlyByteBuf buf) {
		return new GreenLanternGatewayPayload(buf.readVarInt(), buf.readVarInt(), buf.readUtf(64));
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
