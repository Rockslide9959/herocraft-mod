package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client &rarr; server: the ice weapon the player picked from the cryokinesis weapon wheel.
 * {@link #weapon} indexes {@link com.projecthero.mod.hero.power.p09.CryokinesisHandlers.IceWeapon}.
 * Re-validated server-side (owns Cryokinesis, off internal cooldown).
 */
public record CryoWeaponPayload(int weapon) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<CryoWeaponPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "cryo_weapon"));

	public static final StreamCodec<RegistryFriendlyByteBuf, CryoWeaponPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, CryoWeaponPayload::weapon,
			CryoWeaponPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
