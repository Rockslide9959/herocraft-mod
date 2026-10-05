package com.projecthero.mod.client.flash;

import org.joml.Vector3f;

import com.projecthero.mod.armor.ArmorVisualDefinition;
import com.projecthero.mod.armor.SuperheroArmorVisuals;
import com.projecthero.mod.client.render.ArmorSweepReveal;
import com.projecthero.mod.flash.FlashFx;
import com.projecthero.mod.flash.FlashRing;
import com.projecthero.mod.flash.FlashSuit;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * v0.14.11: the Flash Suit's timeline, read off the synced {@link FlashFx} clock so every viewer sees the same thing.
 *
 * <p><b>Suit-up</b> ({@link FlashRing#SUIT_UP_TICKS} = 30): ticks 0-{@value #EMERGE} the ring snaps open and the
 * compressed suit shoots out of it and swells; {@value #EMERGE}-{@value #REVEAL_END} the suit spreads over the body
 * outward from the ring on the right hand, texel by texel, behind a white-hot edge and an orange lightning trail
 * ({@link ArmorSweepReveal.Sweep#radial}), while the speedster whirls through two full turns; at {@value #REVEAL_END}
 * a crack of lightning bursts out at the feet; the rest is the settle.
 *
 * <p><b>Suit-down</b> ({@link FlashRing#SUIT_DOWN_TICKS} = 16): the reverse -- the suit is pulled back into the ring,
 * farthest texels first, through one turn.
 */
public final class FlashSuitReveal {
	public static final int EMERGE = 5;
	public static final int REVEAL_END = FlashRing.SUIT_UP_SNAP_TICK;
	public static final int DOWN_END = FlashRing.SUIT_DOWN_TICKS - 2;

	/** The ring: bottom-front of the right fist, in the armour geometry's own model units. */
	// v0.14.26: up the ring arm first (armorRightArm), then out over the body from the right shoulder
	private static final ArmorSweepReveal.Sweep FROM_RING = ArmorSweepReveal.Sweep.radialVia("flash_ring_arm",
			new Vector3f(-6.0f, 12.5f, -1.0f), "armorRightArm", new Vector3f(-6.0f, 23.0f, 0.0f), 0xFFFFFBE0, 0xFFFFA21F);

	private FlashSuitReveal() {
	}

	/** Ticks into the running transition (with partial), or -1 if none is running. */
	public static float age(Player player, float partial) {
		FlashFx fx = FlashRing.fx(player);
		if (fx.dir() == FlashFx.NONE || player.level() == null) {
			return -1f;
		}
		float age = fx.age(player.level().getGameTime(), partial);
		return age < 0f || age >= fx.length() ? -1f : age;
	}

	public static int dir(Player player) {
		return FlashRing.fx(player).dir();
	}

	/** How much of the suit is showing (0..1); 1 outside a transition. */
	public static float progress(Player player, float partial) {
		float t = age(player, partial);
		if (t < 0f) {
			return 1f;
		}
		if (dir(player) == FlashFx.UP) {
			return Mth.clamp((t - EMERGE) / (float) (REVEAL_END - EMERGE), 0f, 1f);
		}
		return 1f - Mth.clamp(t / DOWN_END, 0f, 1f);
	}

	/** Extra body yaw for the speed-force whirl, in degrees. */
	public static float spin(Player player, float partial) {
		float t = age(player, partial);
		if (t < 0f) {
			return 0f;
		}
		if (dir(player) == FlashFx.UP) {
			return 720f * smooth((t - EMERGE) / (float) (REVEAL_END - EMERGE));
		}
		return -360f * smooth(t / DOWN_END);
	}

	/** The texture to draw a Flash piece with right now (the plain one outside a transition). */
	public static ResourceLocation texture(Player player, ResourceLocation base, float partial) {
		float p = progress(player, partial);
		if (p >= 1f) {
			return base;
		}
		ArmorVisualDefinition def = SuperheroArmorVisuals.get(FlashSuit.SET_ID);
		return def == null ? base : ArmorSweepReveal.texture(def.geometry(), base, p, FROM_RING);
	}

	static float smooth(float x) {
		x = Mth.clamp(x, 0f, 1f);
		return x * x * (3f - 2f * x);
	}
}
