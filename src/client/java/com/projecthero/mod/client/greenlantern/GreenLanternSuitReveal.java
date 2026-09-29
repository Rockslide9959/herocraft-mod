package com.projecthero.mod.client.greenlantern;

import com.projecthero.mod.armor.ArmorVisualDefinition;
import com.projecthero.mod.armor.SuperheroArmorVisuals;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.client.render.ArmorSweepReveal;
import com.projecthero.mod.greenlantern.GreenLanternConfig;
import com.projecthero.mod.greenlantern.data.GreenLanternState;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * v0.13.21: how much of the Green Lantern suit is on right now, for the top-to-bottom pixel-row sweep
 * ({@link ArmorSweepReveal}). Read off the synced suit clock ({@link GreenLanternState#suitAnimStartTick} /
 * {@code suitAnimDir}), so every viewer sees the same rows: 0 -> 1 over a suit-up (the pieces are worn from its first
 * tick), 1 -> 0 over a suit-down, 1 while suited. Worn but neither suited nor animating (the instant between the
 * server swapping the equipment and the state reaching this client) counts as 0, so the suit never flashes fully on.
 */
public final class GreenLanternSuitReveal {
	private GreenLanternSuitReveal() {
	}

	public static float progress(Player player, float partialTick) {
		GreenLanternState s = player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_STATE, null);
		if (s == null || !s.hasPower) {
			return 1.0f;
		}
		float elapsed = player.level().getGameTime() - s.suitAnimStartTick + partialTick;
		if (s.suitAnimDir == GreenLanternState.SUIT_SUITING_UP) {
			// fully on a couple of ticks before the clock ends, so the last row lands as the server finishes
			return clamp(elapsed / (GreenLanternConfig.SUIT_UP_TICKS - 2.0f));
		}
		if (s.suitAnimDir == GreenLanternState.SUIT_SUITING_DOWN) {
			return clamp(1.0f - elapsed / (float) GreenLanternConfig.SUIT_UP_TICKS);
		}
		return s.suited ? 1.0f : 0.0f;
	}

	/** The suit texture to draw on {@code player} right now (the base texture outside a transition). */
	public static ResourceLocation texture(Player player, ResourceLocation base, float partialTick) {
		float p = progress(player, partialTick);
		if (p >= 1.0f) {
			return base;
		}
		ArmorVisualDefinition def = SuperheroArmorVisuals.get("green_lantern");
		return ArmorSweepReveal.texture(def.geometry(), base, p);
	}

	private static float clamp(float v) {
		return Math.max(0.0f, Math.min(1.0f, v));
	}
}
