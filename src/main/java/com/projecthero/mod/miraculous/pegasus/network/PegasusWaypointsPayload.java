package com.projecthero.mod.miraculous.pegasus.network;

import java.util.List;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server to client (v0.16.6): your own Pegasus waypoints, for the small golden markers only you see. */
public record PegasusWaypointsPayload(List<Entry> waypoints) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<PegasusWaypointsPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "pegasus_waypoints"));

	/** Slot (1..3), the dimension's id, where. */
	public record Entry(int slot, String dim, double x, double y, double z) {
		public static final StreamCodec<RegistryFriendlyByteBuf, Entry> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, Entry::slot,
				ByteBufCodecs.STRING_UTF8, Entry::dim,
				ByteBufCodecs.DOUBLE, Entry::x,
				ByteBufCodecs.DOUBLE, Entry::y,
				ByteBufCodecs.DOUBLE, Entry::z,
				Entry::new);
	}

	public static final StreamCodec<RegistryFriendlyByteBuf, PegasusWaypointsPayload> CODEC = StreamCodec.composite(
			Entry.CODEC.apply(ByteBufCodecs.list(8)), PegasusWaypointsPayload::waypoints,
			PegasusWaypointsPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
