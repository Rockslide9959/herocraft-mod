package com.projecthero.mod.miraculous.renarouge;

import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.data.MiraculousState;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.6: Rena Rouge's synced values (the keys of {@code MiraculousState#vals} her kit owns) and the small client-safe
 * reads the HUD, the poses and the renderers share. Timers are absolute game times.
 */
public final class RenaRouge {
	/** Fox Camouflage runs until. */
	public static final String V_CAMO = "rr_camo_until";
	/** Fox Feint runs until. */
	public static final String V_FEINT = "rr_feint_until";
	/** Melody Lure pulls until. */
	public static final String V_LURE = "rr_lure_until";
	/** The Flute is at her lips (Lure, Mirage) until. */
	public static final String V_PLAY = "rr_play_until";
	/** 1 while the Flute is thrown (not on her back, not in her hand). */
	public static final String V_FLUTE_OUT = "rr_flute_out";
	/** The Flute Strike combo's step (1, 2, 3) and when it was struck. */
	public static final String V_COMBO = "rr_combo";
	public static final String V_COMBO_AT = "rr_combo_at";
	/** 1 while a Fox Pounce is in the air. */
	public static final String V_POUNCE = "rr_pounce";
	/** The Illusion Double lasts until. */
	public static final String V_DOUBLE = "rr_double_until";
	/** The Mirage (its copies, its wall, the confusion) lasts until. */
	public static final String V_MIRAGE = "rr_mirage_until";
	/** The Fake Wall stands until. */
	public static final String V_WALL = "rr_wall_until";

	// the pose ids (60..79, see MiraculousState)
	public static final int ANIM_STRIKE_1 = 60;
	public static final int ANIM_STRIKE_2 = 61;
	public static final int ANIM_STRIKE_3 = 62;
	public static final int ANIM_SWEEP = 63;
	public static final int ANIM_THROW = 64;
	public static final int ANIM_CATCH = 65;
	public static final int ANIM_LURE = 66;
	public static final int ANIM_DASH = 67;
	public static final int ANIM_SWAP = 68;
	public static final int ANIM_DOUBLE = 69;
	public static final int ANIM_MIRAGE = 70;
	public static final int ANIM_CAMO = 71;
	public static final int ANIM_WALL = 72;
	public static final int ANIM_POUNCE = 73;
	public static final int ANIM_FEINT = 74;

	private RenaRouge() {
	}

	/** Client-safe: transformed as Rena Rouge. */
	public static boolean is(Player player) {
		return Miraculous.transformedAs(player, Kwami.TRIXX);
	}

	/** Client-safe: a timer value still in the future. */
	public static boolean running(Player player, String key) {
		MiraculousState s = Miraculous.peek(player);
		return s != null && s.val(key) > player.level().getGameTime();
	}

	public static long val(Player player, String key) {
		MiraculousState s = Miraculous.peek(player);
		return s == null ? 0L : s.val(key);
	}

	/** Client-safe: camouflaged (nothing of her is drawn). */
	public static boolean camouflaged(Player player) {
		return is(player) && running(player, V_CAMO);
	}

	/** Client-safe: the Flute is thrown. */
	public static boolean fluteOut(Player player) {
		return val(player, V_FLUTE_OUT) != 0L;
	}

	/** Ends Fox Camouflage (an attack, a strike, a pounce landing, or V again); its cooldown starts now. */
	public static void breakCamouflage(ServerPlayer player) {
		if (!Miraculous.holds(player, Kwami.TRIXX) || Miraculous.state(player).val(V_CAMO) == 0L) {
			return;
		}
		Miraculous.setVal(player, V_CAMO, 0L);
		MobEffectInstance inv = player.getEffect(MobEffects.INVISIBILITY);
		if (inv != null && !inv.isVisible()) {
			player.removeEffect(MobEffects.INVISIBILITY);
		}
		if (!player.hasEffect(MobEffects.INVISIBILITY)) {
			player.setInvisible(false);
		}
		Miraculous.setCooldown(player, RenaRougeConfig.CAMO, RenaRougeConfig.CAMO_COOLDOWN);
		RenaRougeCombat.glow(player.serverLevel(), player.position().add(0, 1.0, 0), 14, 0.35);
	}
}
