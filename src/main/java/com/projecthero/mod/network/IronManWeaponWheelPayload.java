package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * The Mark 7 weapon wheel ("changes 16").
 *
 * <ul>
 *   <li><b>server &rarr; client</b>, {@code ability == ""}: open the wheel screen (the player pressed
 *       the V slot on a weapon-wheel suit).</li>
 *   <li><b>client &rarr; server</b>, {@code ability != ""}: the player picked an option; bind it to
 *       slot 3 (X). Re-validated server-side against
 *       {@link com.projecthero.mod.ironman.ability.IronManAbilities#WEAPON_WHEEL_OPTIONS}.</li>
 * </ul>
 */
public record IronManWeaponWheelPayload(String ability) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<IronManWeaponWheelPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "iron_man_weapon_wheel"));

	public static final StreamCodec<RegistryFriendlyByteBuf, IronManWeaponWheelPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, IronManWeaponWheelPayload::ability,
			IronManWeaponWheelPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
