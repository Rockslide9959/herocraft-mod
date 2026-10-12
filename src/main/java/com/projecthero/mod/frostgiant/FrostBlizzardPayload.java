package com.projecthero.mod.frostgiant;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * v0.16.1, server -> client: the state of a Frost Giant Assault's sky, for everyone near it -- where the rift is, how
 * big the arena is, how hard it is snowing ({@code blizzard}: 0 = clear, 1 = the blizzard, 2 = doubled by the open
 * Casket) and the rift's stage ({@code rift}: 0 = none, 1 = open, 2 = torn wide for Ymir, 3 = collapsing). Purely
 * cosmetic: the client draws the snow, the fog and the swirling rift from it. {@code active = false} clears it.
 */
public record FrostBlizzardPayload(boolean active, int x, int y, int z, float radius, float riftHeight, byte blizzard, byte rift)
		implements CustomPacketPayload {
	public static final Type<FrostBlizzardPayload> TYPE = new Type<>(ProjectHeroMod.id("frost_blizzard"));

	public static final StreamCodec<RegistryFriendlyByteBuf, FrostBlizzardPayload> CODEC = StreamCodec.of(
			(buf, p) -> {
				buf.writeBoolean(p.active);
				buf.writeVarInt(p.x);
				buf.writeVarInt(p.y);
				buf.writeVarInt(p.z);
				buf.writeFloat(p.radius);
				buf.writeFloat(p.riftHeight);
				buf.writeByte(p.blizzard);
				buf.writeByte(p.rift);
			},
			buf -> new FrostBlizzardPayload(buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readFloat(),
					buf.readFloat(), buf.readByte(), buf.readByte()));

	/** The "no blizzard here" packet for a rift at {@code x,y,z}. */
	public static FrostBlizzardPayload clear(int x, int y, int z) {
		return new FrostBlizzardPayload(false, x, y, z, 0f, 0f, (byte) 0, (byte) 0);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

}
