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
 *   <li>Unsuited ({@code wornSuit} empty): one {@link Entry} per suit racked on a Suit Platform within 20 blocks (v0.15.9:
 *       and per suit carried in the pack).</li>
 *   <li>Suited: {@code wornSuit} is the suit worn; {@code canRemove} says whether a platform in range has room for it
 *       ({@code removeDistance} blocks from the floor). v0.15.9: {@code entries} are the suits it can be swapped for
 *       (every suit above except the worn one).</li>
 * </ul>
 */
public record StarkGantryMenuPayload(BlockPos centre, String wornSuit, boolean canRemove, int removeDistance, List<Entry> entries)
		implements CustomPacketPayload {
	/**
	 * A suit: where (a platform, or {@code pack} = carried -- {@code platform} is then unused), which mark, which pieces
	 * (rack-slot mask), charge / integrity fractions, distance.
	 */
	public record Entry(BlockPos platform, String suitId, int mask, float energyFrac, float integrityFrac, int distance, boolean pack) {
		public Entry(BlockPos platform, String suitId, int mask, float energyFrac, float integrityFrac, int distance) {
			this(platform, suitId, mask, energyFrac, integrityFrac, distance, false);
		}
	}

	public static final CustomPacketPayload.Type<StarkGantryMenuPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "stark_gantry_menu"));

	public static final StreamCodec<RegistryFriendlyByteBuf, StarkGantryMenuPayload> CODEC =
			StreamCodec.ofMember(StarkGantryMenuPayload::write, StarkGantryMenuPayload::read);

	/** At most this many entries are read (a sane cap -- nobody has 256 suits in range). */
	private static final int MAX_ENTRIES = 256;

	private static void write(StarkGantryMenuPayload p, RegistryFriendlyByteBuf buf) {
		buf.writeBlockPos(p.centre);
		buf.writeUtf(p.wornSuit);
		buf.writeBoolean(p.canRemove);
		buf.writeVarInt(p.removeDistance);
		int n = Math.min(MAX_ENTRIES, p.entries.size());
		buf.writeVarInt(n);
		for (int i = 0; i < n; i++) {
			Entry e = p.entries.get(i);
			buf.writeBlockPos(e.platform());
			buf.writeUtf(e.suitId());
			buf.writeVarInt(e.mask());
			buf.writeFloat(e.energyFrac());
			buf.writeFloat(e.integrityFrac());
			buf.writeVarInt(e.distance());
			buf.writeBoolean(e.pack());
		}
	}

	private static StarkGantryMenuPayload read(RegistryFriendlyByteBuf buf) {
		BlockPos centre = buf.readBlockPos();
		String worn = buf.readUtf();
		boolean canRemove = buf.readBoolean();
		int dist = buf.readVarInt();
		int n = Math.min(MAX_ENTRIES, Math.max(0, buf.readVarInt()));
		List<Entry> out = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			out.add(new Entry(buf.readBlockPos(), buf.readUtf(), buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readVarInt(),
					buf.readBoolean()));
		}
		return new StarkGantryMenuPayload(centre, worn, canRemove, dist, out);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
