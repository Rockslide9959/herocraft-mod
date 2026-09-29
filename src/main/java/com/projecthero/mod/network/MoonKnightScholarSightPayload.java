package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Moon Knight Phase 5, Steven Grant's "Scholar's Sight": the blocks worth knowing about near the player (chests,
 * barrels, ores, spawners) as packed {@code BlockPos.asLong} values with an RGB outline colour each, and how long to
 * show them. Sent only to the Steven who used it; the client draws the outlines through walls for that player alone.
 */
public record MoonKnightScholarSightPayload(long[] positions, int[] colours, int ticks) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<MoonKnightScholarSightPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "moon_knight_scholars_sight"));

	public static final StreamCodec<FriendlyByteBuf, MoonKnightScholarSightPayload> CODEC =
			StreamCodec.ofMember(MoonKnightScholarSightPayload::write, MoonKnightScholarSightPayload::read);

	private void write(FriendlyByteBuf buf) {
		buf.writeLongArray(positions);
		buf.writeVarIntArray(colours);
		buf.writeVarInt(ticks);
	}

	private static MoonKnightScholarSightPayload read(FriendlyByteBuf buf) {
		long[] positions = buf.readLongArray();
		int[] colours = buf.readVarIntArray();
		return new MoonKnightScholarSightPayload(positions, colours, buf.readVarInt());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
