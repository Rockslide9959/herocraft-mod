package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client &rarr; server (v0.15.3): a toggle on Thor's N weapon screen -- set weapon {@code weapon} (a
 * {@link com.projecthero.mod.hammer.ThorWeapon} ordinal) ACTIVE or INACTIVE for R. Re-validated in
 * {@link com.projecthero.mod.hammer.ThorWeaponSelection#handleToggle}.
 */
public record ThorWeaponTogglePayload(int weapon, boolean active) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<ThorWeaponTogglePayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "thor_weapon_toggle"));

	public static final StreamCodec<RegistryFriendlyByteBuf, ThorWeaponTogglePayload> CODEC = StreamCodec.of(
			(buf, p) -> {
				buf.writeByte(p.weapon);
				buf.writeBoolean(p.active);
			},
			buf -> new ThorWeaponTogglePayload(buf.readByte(), buf.readBoolean()));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
