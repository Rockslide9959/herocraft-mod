package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.14.26: server -&gt; client "your Mark III targeting system has locked onto entity {@code entityId}" (-1 = no lock).
 * Sent only to the wearer, only when the lock changes. The client draws the lock reticle and the HUD's target panel.
 */
public record IronManLockPayload(int entityId) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<IronManLockPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "iron_man_lock"));

	public static final StreamCodec<RegistryFriendlyByteBuf, IronManLockPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, IronManLockPayload::entityId,
			IronManLockPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
