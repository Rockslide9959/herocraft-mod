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

	/**
	 * Server side of a pick (v0.15.3: sent when V is let go over a wedge -- see {@code IronManUiLayout#wheelReleaseChoice}).
	 * The entity-glow wedge toggles the highlight, a Mark III / 4 weapon becomes what G fires, a Mark 7 option is bound
	 * to X; anything else is ignored. Public so the gametests can drive it.
	 */
	public static void handleServer(net.minecraft.server.level.ServerPlayer player, String ability) {
		if (com.projecthero.mod.ironman.ability.IronManAbilities.ENTITY_GLOW_TOGGLE.equals(ability)) {
			com.projecthero.mod.ironman.ability.IronManAbilities.toggleEntityGlowFromWheel(player);
			return;
		}
		// v0.15.4: the Mark 6 / Mark 7 wheel picks what G fires (checked first -- its options share ids with the legacy wheel)
		if (com.projecthero.mod.ironman.ability.IronManMark6.isWeapon(ability)
				&& com.projecthero.mod.ironman.ability.IronManMark6.selectWeapon(player, ability)) {
			return;
		}
		// v0.14.27 (agent D): the Mark III wheel picks what G fires
		if (com.projecthero.mod.ironman.ability.IronManMark3.isWeapon(ability)) {
			com.projecthero.mod.ironman.ability.IronManMark3.selectWeapon(player, ability);
			return;
		}
		for (String option : com.projecthero.mod.ironman.ability.IronManAbilities.WEAPON_WHEEL_OPTIONS) {
			if (option.equals(ability)) {
				com.projecthero.mod.ironman.TonyStark.setWeaponWheelChoice(player, option);
				return;
			}
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
