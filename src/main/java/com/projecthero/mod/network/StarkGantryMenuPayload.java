package com.projecthero.mod.network;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.15.4, server &rarr; client: the Stark Gantry menu ({@code com.projecthero.mod.ironman.gantry.StarkGantry}). Sent when
 * a Tony Stark presses H standing on a complete gantry floor.
 * <ul>
 *   <li>Unsuited ({@code wornSuit} empty): one {@link Entry} per suit racked on a Suit Platform within 20 blocks.</li>
 *   <li>Suited: {@code wornSuit} is the suit worn; {@code canRemove} says whether a platform in range has room for it
 *       ({@code removeDistance} blocks from the floor).</li>
 * </ul>
 */
public record StarkGantryMenuPayload(BlockPos centre, String wornSuit, boolean canRemove, int removeDistance, List<Entry> entries)
		implements CustomPacketPayload {
	/** A racked suit: where, which mark, which pieces (rack-slot mask), charge / integrity fractions, distance. */
	public record Entry(BlockPos platform, String suitId, int mask, float energyFrac, float integrityFrac, int distance) {
	}

	public static final CustomPacketPayload.Type<StarkGantryMenuPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "stark_gantry_menu"));

	public static final StreamCodec<RegistryFriendlyByteBuf, StarkGantryMenuPayload> CODEC =
			StreamCodec.ofMember(StarkGantryMenuPayload::write, StarkGantryMenuPayload::read);

	private static void write(StarkGantryMenuPayload p, RegistryFriendlyByteBuf buf) {
		buf.writeBlockPos(p.centre);
		buf.writeUtf(p.wornSuit);
		buf.writeBoolean(p.canRemove);
		buf.writeVarInt(p.removeDistance);
		buf.writeVarInt(p.entries.size());
		for (Entry e : p.entries) {
			buf.writeBlockPos(e.platform());
			buf.writeUtf(e.suitId());
			buf.writeVarInt(e.mask());
			buf.writeFloat(e.energyFrac());
			buf.writeFloat(e.integrityFrac());
			buf.writeVarInt(e.distance());
		}
	}

	private static StarkGantryMenuPayload read(RegistryFriendlyByteBuf buf) {
		BlockPos centre = buf.readBlockPos();
		String worn = buf.readUtf();
		boolean canRemove = buf.readBoolean();
		int dist = buf.readVarInt();
		int n = buf.readVarInt();
		List<Entry> out = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			out.add(new Entry(buf.readBlockPos(), buf.readUtf(), buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readVarInt()));
		}
		return new StarkGantryMenuPayload(centre, worn, canRemove, dist, out);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
