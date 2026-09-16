package com.projecthero.mod.greenlantern;

import com.projecthero.mod.greenlantern.data.GreenLanternState;
import com.projecthero.mod.greenlantern.item.GreenLanternSuitArmor;

import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Suit Up / Suit Down (V, tap). 0.8s animation, 100 charge on activation, 0 idle upkeep (the ring's
 * armour costs nothing to maintain -- only actions do). A 0.25s debounce stops a double-tap from
 * immediately reversing the animation, and suit-down is refused while battery-recharging (Phase 4).
 */
public final class GreenLanternSuit {
	private GreenLanternSuit() {
	}

	public static void toggle(ServerPlayer player) {
		GreenLanternState s = GreenLantern.state(player);
		long now = player.level().getGameTime();

		if (s.suitAnimDir != GreenLanternState.SUIT_IDLE) {
			return; // mid-animation -- debounce
		}
		if (now - s.suitAnimStartTick < GreenLanternConfig.SUIT_DOWN_DEBOUNCE_TICKS && s.suitAnimStartTick != 0L) {
			return;
		}

		if (s.suited) {
			if (GreenLanternBattery.isChannelling(player)) {
				GreenLanternEnergy.feedback(player, "message.projecthero.green_lantern.cannot_suit_down_recharging");
				return;
			}
			beginTransition(player, GreenLanternState.SUIT_SUITING_DOWN);
			return;
		}

		if (!GreenLanternEnergy.spend(player, GreenLanternConfig.SUIT_UP_COST)) {
			GreenLanternEnergy.feedback(player, "message.projecthero.ability.low_charge");
			return;
		}
		GreenLanternEnergy.markAbilityUsed(player);
		beginTransition(player, GreenLanternState.SUIT_SUITING_UP);
	}

	private static void beginTransition(ServerPlayer player, int dir) {
		GreenLanternState c = GreenLantern.state(player).copy();
		c.suitAnimDir = dir;
		c.suitAnimStartTick = player.level().getGameTime();
		GreenLantern.save(player, c);
		ServerLevel level = player.serverLevel();
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				dir == GreenLanternState.SUIT_SUITING_UP ? SoundEvents.BEACON_ACTIVATE : SoundEvents.BEACON_DEACTIVATE,
				SoundSource.PLAYERS, 0.5f, 1.6f);
	}

	/** Per-player server tick -- progresses the suit-up/suit-down animation. */
	public static void tick(ServerPlayer player) {
		GreenLanternState s = GreenLantern.state(player);
		if (s.suitAnimDir == GreenLanternState.SUIT_IDLE) {
			return;
		}
		long elapsed = player.level().getGameTime() - s.suitAnimStartTick;
		if (elapsed < GreenLanternConfig.SUIT_UP_TICKS) {
			return;
		}
		boolean suitingUp = s.suitAnimDir == GreenLanternState.SUIT_SUITING_UP;
		GreenLanternState c = s.copy();
		c.suited = suitingUp;
		c.suitAnimDir = GreenLanternState.SUIT_IDLE;
		GreenLantern.save(player, c);
		if (suitingUp) {
			GreenLanternSuitArmor.equip(player);
		} else {
			GreenLanternSuitArmor.strip(player);
			// Flight/shield no longer belong to the suit (the ring's powers work unsuited too), so
			// suiting down must not touch either -- forcing flight to stop here used to be safe only
			// because flight required the suit in the first place; now it would end a legitimate
			// unsuited flight with no controlled-descent grace (GreenLanternDamage's Emergency Catch is
			// itself suit-gated), an instant plummet from whatever height the player suited down at.
		}
		player.displayClientMessage(Component.translatable(suitingUp
				? "message.projecthero.green_lantern.suited_up" : "message.projecthero.green_lantern.suited_down"), true);
	}
}
