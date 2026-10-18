package com.projecthero.mod.miraculous.pegasus.network;

import java.util.List;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client (v0.16.6): Shift+Z, the Voyage -- open the destination menu. Each entry is one place the portal can
 * open to (your bed / spawn point, the world spawn, each squadmate, each of your waypoints); the client answers with the
 * picked index ({@link PegasusVoyageChoicePayload}) and the server re-checks it.
 */
public record PegasusVoyageMenuPayload(List<Dest> destinations, String hereDim) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<PegasusVoyageMenuPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "pegasus_voyage_menu"));

	public static final int BED = 0;
	public static final int WORLD_SPAWN = 1;
	public static final int SQUADMATE = 2;
	public static final int WAYPOINT = 3;

	/** {@code kind} (BED..WAYPOINT), a name (a squadmate's, a waypoint's number), the dimension id, where. */
	public record Dest(int kind, String name, String dim, int x, int y, int z) {
		public static final StreamCodec<RegistryFriendlyByteBuf, Dest> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, Dest::kind,
				ByteBufCodecs.STRING_UTF8, Dest::name,
				ByteBufCodecs.STRING_UTF8, Dest::dim,
				ByteBufCodecs.VAR_INT, Dest::x,
				ByteBufCodecs.VAR_INT, Dest::y,
				ByteBufCodecs.VAR_INT, Dest::z,
				Dest::new);
	}

	public static final StreamCodec<RegistryFriendlyByteBuf, PegasusVoyageMenuPayload> CODEC = StreamCodec.composite(
			Dest.CODEC.apply(ByteBufCodecs.list(32)), PegasusVoyageMenuPayload::destinations,
			ByteBufCodecs.STRING_UTF8, PegasusVoyageMenuPayload::hereDim,
			PegasusVoyageMenuPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
