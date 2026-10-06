package com.projecthero.mod.hammer;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.power.ThorPassives;
import com.projecthero.mod.power.ThorPowers;
import com.projecthero.mod.worthiness.Worthiness;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * v0.15.3: Thor's N weapon selector. Each of the two callable weapons is ACTIVE (R may call it) or INACTIVE (R never
 * calls it, even when it is the closer or the only bound one). Stored per player in
 * {@link ModAttachments#THOR_WEAPONS_INACTIVE}; both active by default, which is exactly the v0.15.1 call rule. Read by
 * {@link MjolnirRecall#call} / {@link MjolnirRecall#chooseWeapon}; written only through {@link #set}, which the N
 * screen reaches with a {@code ThorWeaponTogglePayload}. No static state.
 */
public final class ThorWeaponSelection {
	private ThorWeaponSelection() {
	}

	private static int bit(ThorWeapon weapon) {
		return 1 << weapon.ordinal();
	}

	/** Whether R may call {@code weapon} for this player (either side -- the attachment is synced to its owner). */
	public static boolean isActive(Player player, ThorWeapon weapon) {
		return (player.getAttachedOrElse(ModAttachments.THOR_WEAPONS_INACTIVE, 0) & bit(weapon)) == 0;
	}

	/** Whether R may call anything at all. */
	public static boolean anyActive(Player player) {
		for (ThorWeapon weapon : ThorWeapon.values()) {
			if (isActive(player, weapon)) {
				return true;
			}
		}
		return false;
	}

	/** Sets one weapon's state (server side; the attachment syncs to the owner). Clears the attachment when back to default. */
	public static void set(ServerPlayer player, ThorWeapon weapon, boolean active) {
		int mask = player.getAttachedOrElse(ModAttachments.THOR_WEAPONS_INACTIVE, 0);
		int next = active ? mask & ~bit(weapon) : mask | bit(weapon);
		if (next != mask) {
			player.setAttached(ModAttachments.THOR_WEAPONS_INACTIVE, next);
		}
	}

	/**
	 * Who the N screen is for: a worthy player with Thor's control context -- holding Mjolnir or Stormbreaker, bound to
	 * Mjolnir (the Power of Thor), or bound to a Stormbreaker. The same rule on both sides (worthiness and both bindings
	 * are synced to their owner), so the client opens the screen exactly when the server will accept its toggles.
	 */
	public static boolean ownsSelector(Player player) {
		if (!Worthiness.isWorthy(player)) {
			return false;
		}
		return ThorPowers.isHoldingThorWeapon(player) || ThorPassives.hasPowerOfThor(player)
				|| player.getAttachedOrElse(ModAttachments.BOUND_STORMBREAKER_ID, null) != null;
	}

	/** Server handler for the N screen's toggle button. Re-validates everything; a refused request changes nothing. */
	public static void handleToggle(ServerPlayer player, int weaponOrdinal, boolean active) {
		if (weaponOrdinal < 0 || weaponOrdinal >= ThorWeapon.values().length || !ownsSelector(player)) {
			return;
		}
		ThorWeapon weapon = ThorWeapon.values()[weaponOrdinal];
		set(player, weapon, active);
		Component name = Component.translatable(weapon.item().getDescriptionId());
		player.displayClientMessage(Component.translatable(active
						? "message.projecthero.thor.weapon_select.active" : "message.projecthero.thor.weapon_select.inactive", name)
				.withStyle(active ? ChatFormatting.AQUA : ChatFormatting.GRAY), true);
	}
}
