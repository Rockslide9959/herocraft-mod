package com.projecthero.mod.quirk.explosion;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.17.1, client &rarr; server: an Explosion holder's movement keys (W/S as {@code forward}, A/D as {@code strafe}, each
 * -1..1), sent only when they change. The Explosion Dash blasts toward them and the Spin Dodge picks its side from them --
 * the server never sees WASD otherwise. Purely a hint: a bad value only changes which way he dashes.
 */
public record ExplosionInputPayload(int forward, int strafe) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<ExplosionInputPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "explosion_input"));

	public static final StreamCodec<RegistryFriendlyByteBuf, ExplosionInputPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, ExplosionInputPayload::forward,
			ByteBufCodecs.VAR_INT, ExplosionInputPayload::strafe,
			ExplosionInputPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
