package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server &rarr; client: the teleporter finished the 5-second Portal charge -- open the destination
 * picker (coordinates + dimension).
 */
public record PortalPickerPayload(double x, double y, double z) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<PortalPickerPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "portal_picker"));

	public static final StreamCodec<RegistryFriendlyByteBuf, PortalPickerPayload> CODEC = StreamCodec.of(
			(buf, p) -> {
				buf.writeDouble(p.x);
				buf.writeDouble(p.y);
				buf.writeDouble(p.z);
			},
			buf -> new PortalPickerPayload(buf.readDouble(), buf.readDouble(), buf.readDouble()));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
