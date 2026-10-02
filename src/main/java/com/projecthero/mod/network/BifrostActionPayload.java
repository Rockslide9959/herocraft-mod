package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client &rarr; server (v0.14.20): a button on Stormbreaker's Bifrost screen. Everything is re-validated on the server
 * ({@link com.projecthero.mod.stormbreaker.Bifrost#handleAction}): holding a worthy Stormbreaker, the cooldown, the
 * world border and build height, the dimension and a safe landing. Travelling to a waypoint uses the coordinates the
 * server saved, never anything the client sends.
 *
 * <ul>
 *   <li>{@link #TRAVEL_COORDS}: travel to {@code x y z} (slot and name unused).</li>
 *   <li>{@link #TRAVEL_WAYPOINT}: travel to waypoint {@code slot}.</li>
 *   <li>{@link #SAVE}: save the player's current position into {@code slot} as {@code name}.</li>
 *   <li>{@link #CLEAR}: empty waypoint {@code slot}.</li>
 * </ul>
 */
public record BifrostActionPayload(int action, int slot, int x, int y, int z, String name) implements CustomPacketPayload {
	public static final int TRAVEL_COORDS = 0;
	public static final int TRAVEL_WAYPOINT = 1;
	public static final int SAVE = 2;
	public static final int CLEAR = 3;

	public static final CustomPacketPayload.Type<BifrostActionPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "bifrost_action"));

	public static final StreamCodec<RegistryFriendlyByteBuf, BifrostActionPayload> CODEC = StreamCodec.of(
			(buf, p) -> {
				buf.writeByte(p.action);
				buf.writeByte(p.slot);
				buf.writeInt(p.x);
				buf.writeInt(p.y);
				buf.writeInt(p.z);
				ByteBufCodecs.stringUtf8(64).encode(buf, p.name);
			},
			buf -> new BifrostActionPayload(buf.readByte(), buf.readByte(), buf.readInt(), buf.readInt(), buf.readInt(),
					ByteBufCodecs.stringUtf8(64).decode(buf)));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
