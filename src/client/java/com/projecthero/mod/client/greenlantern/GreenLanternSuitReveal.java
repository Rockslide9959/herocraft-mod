package com.projecthero.mod.client.greenlantern;

import org.joml.Vector3f;

import com.projecthero.mod.armor.ArmorVisualDefinition;
import com.projecthero.mod.armor.SuperheroArmorVisuals;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.client.render.ArmorSweepReveal;
import com.projecthero.mod.greenlantern.GreenLanternConfig;
import com.projecthero.mod.greenlantern.GreenLanternSuitStyle;
import com.projecthero.mod.greenlantern.data.GreenLanternState;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * v0.13.21: how much of the Green Lantern suit is on right now, read off the synced suit clock
 * ({@link GreenLanternState#suitAnimStartTick} / {@code suitAnimDir}), so every viewer sees the same texels: 0 -> 1 over a
 * suit-up (the pieces are worn from its first tick), 1 -> 0 over a suit-down, 1 while suited. Worn but neither suited nor
 * animating (the instant between the server swapping the equipment and the state reaching this client) counts as 0, so
 * the suit never flashes fully on.
 *
 * <p>v0.15.15, explicit user request: the suit no longer sweeps from the chest down -- it spreads outward from the Power
 * Ring on the right hand ({@link ArmorSweepReveal.Sweep#radialVia}: up the ring arm to the shoulder first, then out over the
 * body and head), behind a white-green edge; suit-down plays it backwards, the suit receding into the ring. The suit drawn
 * is the wearer's chosen {@link GreenLanternSuitStyle} (N); every style shares the geometry, so the same sweep serves all.
 * {@link #ringGlow} is how hard the ring blazes while the suit forms.
 */
public final class GreenLanternSuitReveal {
	/** The ring on the right fist, then the right shoulder joint -- in the armour geometry's own model units. */
	private static final ArmorSweepReveal.Sweep FROM_RING = ArmorSweepReveal.Sweep.radialVia("gl_ring_arm",
			new Vector3f(-6.0f, 12.5f, -1.0f), "armorRightArm", new Vector3f(-6.0f, 23.0f, 0.0f), 0xFFE6FFEC, 0xFF5CFF8E);

	private GreenLanternSuitReveal() {
	}

	private static GreenLanternState state(Player player) {
		return player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_STATE, null);
	}

	public static float progress(Player player, float partialTick) {
		GreenLanternState s = state(player);
		if (s == null || !s.hasPower) {
			return 1.0f;
		}
		float elapsed = player.level().getGameTime() - s.suitAnimStartTick + partialTick;
		if (s.suitAnimDir == GreenLanternState.SUIT_SUITING_UP) {
			// fully on a couple of ticks before the clock ends, so the last texels land as the server finishes
			return clamp(elapsed / (GreenLanternConfig.SUIT_UP_TICKS - 2.0f));
		}
		if (s.suitAnimDir == GreenLanternState.SUIT_SUITING_DOWN) {
			return clamp(1.0f - elapsed / (GreenLanternConfig.SUIT_UP_TICKS - 2.0f));
		}
		return s.suited ? 1.0f : 0.0f;
	}

	/**
	 * 0..1: how brightly the ring blazes for the suit -- ramps up over the first few ticks of a suit-up / suit-down, stays
	 * lit while the suit pours out of (or back into) it, and fades over the last few. 0 outside a transition.
	 */
	public static float ringGlow(Player player, float partialTick) {
		GreenLanternState s = state(player);
		if (s == null || !s.hasPower || s.suitAnimDir == GreenLanternState.SUIT_IDLE || player.level() == null) {
			return 0f;
		}
		float t = player.level().getGameTime() - s.suitAnimStartTick + partialTick;
		float len = GreenLanternConfig.SUIT_UP_TICKS;
		if (t < 0f || t > len) {
			return 0f;
		}
		return clamp(Math.min(t / 3f, (len - t) / 5f));
	}

	/** The wearer's chosen suit texture (the default suit when none is chosen). */
	public static ResourceLocation styleTexture(Player player, ResourceLocation fallback) {
		GreenLanternState s = state(player);
		if (s == null) {
			return fallback;
		}
		GreenLanternSuitStyle style = GreenLanternSuitStyle.byOrdinal(s.suitStyle);
		return style == GreenLanternSuitStyle.DEFAULT ? fallback : style.texture();
	}

	/** The suit texture to draw on {@code player} right now: their chosen style, swept on / off outside a settled suit. */
	public static ResourceLocation texture(Player player, ResourceLocation base, float partialTick) {
		ResourceLocation tex = styleTexture(player, base);
		float p = progress(player, partialTick);
		if (p >= 1.0f) {
			return tex;
		}
		ArmorVisualDefinition def = SuperheroArmorVisuals.get("green_lantern");
		return def == null ? tex : ArmorSweepReveal.texture(def.geometry(), tex, p, FROM_RING);
	}

	private static float clamp(float v) {
		return Math.max(0.0f, Math.min(1.0f, v));
	}
}
