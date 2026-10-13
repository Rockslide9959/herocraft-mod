package com.projecthero.mod.galactus;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * v0.16.1, server -> client: Galactus' sky for everyone near the arena -- where it is, how big, the stage
 * ({@code stage}: 0 = clear, 1 = the deep-violet sky with daytime stars, 2 = the planet being consumed (white-hot),
 * 3 = clearing after the retreat) and the World Consumption ({@code consumption}, 0..1, for the HUD line). The violet tint
 * itself rides the shared raid-sky packet; this adds the stars and the HUD. Purely cosmetic. {@code active = false}
 * clears it.
 */
public record GalactusSkyPayload(boolean active, int x, int y, int z, float radius, byte stage, float consumption) implements CustomPacketPayload {
	public static final Type<GalactusSkyPayload> TYPE = new Type<>(ProjectHeroMod.id("galactus_sky"));

	public static final StreamCodec<RegistryFriendlyByteBuf, GalactusSkyPayload> CODEC = StreamCodec.of(
			(buf, p) -> {
				buf.writeBoolean(p.active);
				buf.writeVarInt(p.x);
				buf.writeVarInt(p.y);
				buf.writeVarInt(p.z);
				buf.writeFloat(p.radius);
				buf.writeByte(p.stage);
				buf.writeFloat(p.consumption);
			},
			buf -> new GalactusSkyPayload(buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readFloat(), buf.readByte(),
					buf.readFloat()));

	public static GalactusSkyPayload clear(int x, int y, int z) {
		return new GalactusSkyPayload(false, x, y, z, 0f, (byte) 0, 0f);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
