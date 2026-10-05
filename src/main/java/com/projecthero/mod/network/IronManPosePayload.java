package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.14.26: server -&gt; clients "Iron Man {@code playerId} is doing ability animation {@code anim} for the next
 * {@code ticks} ticks" (ids in {@code IronManAbilityFx}). Sent to the user and everyone tracking them; held abilities
 * (Unibeam, barrier, flamethrower, charging) re-send it every few ticks while they last. Drives the attack poses and the
 * ability models (shield, flame cone, punch shockwave).
 */
public record IronManPosePayload(int playerId, int anim, int ticks) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<IronManPosePayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "iron_man_pose"));

	public static final StreamCodec<RegistryFriendlyByteBuf, IronManPosePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, IronManPosePayload::playerId,
			ByteBufCodecs.VAR_INT, IronManPosePayload::anim,
			ByteBufCodecs.VAR_INT, IronManPosePayload::ticks,
			IronManPosePayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
