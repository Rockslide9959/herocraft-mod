package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.14.29 Remote Pilot, client &rarr; server: the player pressed PILOT on a suit card in the Call Armour picker.
 * {@code source} is the card's {@link IronManSuitListPayload} source. Fully re-validated server-side by
 * {@link com.projecthero.mod.ironman.drone.IronManDrones#deploy}.
 */
public record IronManDroneDeployPayload(String suitId, int source) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<IronManDroneDeployPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "iron_man_drone_deploy"));

	public static final StreamCodec<RegistryFriendlyByteBuf, IronManDroneDeployPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, IronManDroneDeployPayload::suitId,
			ByteBufCodecs.VAR_INT, IronManDroneDeployPayload::source,
			IronManDroneDeployPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
