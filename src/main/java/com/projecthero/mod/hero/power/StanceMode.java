package com.projecthero.mod.hero.power;

import java.util.Locale;

import com.projecthero.mod.hero.AbilityContext;

import net.minecraft.network.chat.Component;

/**
 * Shared rules for the six "C" stance toggles — Crystal Armor, Earth Armor, Frozen Armor, Flame Body,
 * Tailwind and Aquatic Form (v0.10.9). Each of them:
 *
 * <ul>
 *   <li>adds +{@value #ABILITY_BONUS} to that power's ability damage while worn,</li>
 *   <li>adds +{@value #MELEE_BONUS} melee damage (an {@code ATTACK_DAMAGE} modifier the power wires
 *       up itself), and</li>
 *   <li>puts the slot on a {@value #DEACTIVATE_CD_SECONDS}-second cooldown the moment it is dropped,
 *       whether or not its own stamina/strain bar had run out.</li>
 * </ul>
 */
public final class StanceMode {
	public static final float ABILITY_BONUS = 10.0f;
	public static final double MELEE_BONUS = 8.0;
	public static final int DEACTIVATE_CD_SECONDS = 20;
	public static final int DEACTIVATE_CD_TICKS = DEACTIVATE_CD_SECONDS * 20;

	private StanceMode() {
	}

	/**
	 * Call at the very top of a stance toggle's {@code onToggleOn}. If the slot is still on its
	 * post-deactivation cooldown this flips the toggle back off, shows the standard cooldown action-bar
	 * message and returns {@code true} — the caller should then {@code return} immediately.
	 */
	public static boolean blockedByCooldown(AbilityContext ctx) {
		if (ctx.cooldownReady()) {
			return false;
		}
		ctx.setToggled(false);
		ctx.actionBar("message.projecthero.ability.on_cooldown",
				Component.translatable(ctx.ability().nameKey()),
				String.format(Locale.ROOT, "%.0f", Math.ceil(ctx.cooldownRemaining() / 20.0f)));
		return true;
	}

	/** Call from every path that ends the stance (manual toggle-off and stamina-exhaustion alike). */
	public static void startDeactivateCooldown(AbilityContext ctx) {
		ctx.triggerCooldown(DEACTIVATE_CD_TICKS);
	}
}
