package com.projecthero.mod.cyborg.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server (v0.16.5): a button on Cyborg's Boom Tube menu. The actions match the Bifrost's
 * ({@code BifrostActionPayload}): travel to typed coordinates, travel to a waypoint, save here, clear. Everything is
 * re-checked in {@code CyborgBoomTube.handleAction}; a waypoint trip uses the coordinates the server saved.
 */
public record CyborgBoomActionPayload(int action, int slot, int x, int y, int z, String name) implements CustomPacketPayload {
	public static final int TRAVEL_COORDS = 0;
	public static final int TRAVEL_WAYPOINT = 1;
	public static final int SAVE = 2;
	public static final int CLEAR = 3;

	public static final CustomPacketPayload.Type<CyborgBoomActionPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "cyborg_boom_action"));

	public static final StreamCodec<RegistryFriendlyByteBuf, CyborgBoomActionPayload> CODEC = StreamCodec.of(
			(buf, p) -> {
				buf.writeByte(p.action);
				buf.writeByte(p.slot);
				buf.writeInt(p.x);
				buf.writeInt(p.y);
				buf.writeInt(p.z);
				ByteBufCodecs.stringUtf8(64).encode(buf, p.name);
			},
			buf -> new CyborgBoomActionPayload(buf.readByte(), buf.readByte(), buf.readInt(), buf.readInt(), buf.readInt(),
					ByteBufCodecs.stringUtf8(64).decode(buf)));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
