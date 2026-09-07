package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server &rarr; nearby clients: a firearm bullet just struck a solid block at {@code (x, y, z)} on the
 * given face ({@code face} = {@link net.minecraft.core.Direction#get3DDataValue()}). The client drops
 * a fading "bullet hole" decal there -- see {@code BulletHoleRenderer}. Purely cosmetic; no block is
 * ever changed.
 */
public record BulletHolePayload(double x, double y, double z, int face) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<BulletHolePayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "bullet_hole"));

	public static final StreamCodec<RegistryFriendlyByteBuf, BulletHolePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.DOUBLE, BulletHolePayload::x,
			ByteBufCodecs.DOUBLE, BulletHolePayload::y,
			ByteBufCodecs.DOUBLE, BulletHolePayload::z,
			ByteBufCodecs.VAR_INT, BulletHolePayload::face,
			BulletHolePayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
