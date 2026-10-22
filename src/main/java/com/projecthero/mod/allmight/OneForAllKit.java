package com.projecthero.mod.allmight;

import com.projecthero.mod.allmight.data.AllMightState;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.quirk.QuirkKit;

import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.7 (user: "add it to the quirk framework"): One For All's {@link QuirkKit}, registered for
 * {@link com.projecthero.mod.quirk.Quirk#ONE_FOR_ALL} by {@code QuirkMod}. A thin adapter: the moves, the forms and the One For All
 * timer stay in the All Might engine ({@link AllMightAbilityManager}, {@link AllMight}), which also runs its own tick. The Quirk core routes the keys here (debounced, refused while Erased); the Quirk HUD reads the ids,
 * cooldowns and locks from here.
 *
 * <pre>
 *   R  Detroit Smash                      Shift  New Hampshire Smash
 *   G  Texas Smash                        Shift  Delaware Smash
 *   X  Leap                               Shift  Manchester Smash
 *   Z  United States of Smash (hold)      Shift  Oklahoma Smash
 *   V  Carolina Smash                     Shift  Missouri Smash
 *   C  Plus Ultra (costs timer)           Shift  St. Louis Smash
 *   H  Base Form / Power Form             N      the costume locker
 * </pre>
 */
public final class OneForAllKit implements QuirkKit {
	@Override
	public void handle(ServerPlayer player, AbilitySlot slot, boolean pressed, boolean shift) {
		if (!AllMight.isFullPower(player)) {
			// the Base Form is a plain player: the keys only point at H (releases are ignored)
			if (pressed && slot.ordinal() <= AbilitySlot.SLOT_6.ordinal()) {
				AllMight.say(player, "message.projecthero.all_might.base_form_keys", ChatFormatting.GRAY);
			}
			return;
		}
		AllMightAbilityManager.handle(player, slot, pressed);
	}

	@Override
	public void stopAll(ServerPlayer player) {
		AllMightAbilities.cancelCharge(player, false);
	}

	@Override
	public String[] idsOf(AbilitySlot slot) {
		if (slot.ordinal() > AbilitySlot.SLOT_6.ordinal()) {
			return new String[0];
		}
		return new String[] { AllMightAbilityManager.abilityIdOf(slot), AllMightAbilityManager.shiftAbilityIdOf(slot) };
	}

	@Override
	public int maxCooldown(String abilityId) {
		return AllMightAbilityManager.maxCooldown(abilityId);
	}

	@Override
	public int cooldownLeft(Player player, String abilityId) {
		return AllMight.cooldownRemaining(player, abilityId);
	}

	/** Locked in the Base Form (no moves). */
	@Override
	public boolean keyLocked(Player player, AbilitySlot slot, boolean shift) {
		return !AllMight.isFullPower(player);
	}

	@Override
	public boolean active(Player player, AbilitySlot slot) {
		AllMightState s = player.getAttachedOrElse(ModAttachments.ALL_MIGHT_STATE, null);
		if (s == null || !s.hasPower || !s.fullPower) {
			return false;
		}
		return switch (slot) {
			case SLOT_6 -> s.plusUltra;
			case SLOT_4 -> s.chargeStart > 0L;
			default -> false;
		};
	}

	@Override
	public String abilityLangKey(String abilityId) {
		return "projecthero.all_might.ability." + abilityId;
	}
}
