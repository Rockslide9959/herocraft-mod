package com.projecthero.mod.miraculous.pegasus;

import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.data.MiraculousState;

import net.minecraft.world.entity.player.Player;

/**
 * v0.16.6: Pegasus's synced values (the keys of {@code MiraculousState#vals} his kit owns) and the small client-safe
 * reads the HUD, the poses and the renderers share. Timers are absolute game times.
 */
public final class Pegasus {
	/** 1 while the Horseshoe is thrown (not on the belt, not in the hand). */
	public static final String V_SHOE_OUT = "pg_shoe_out";
	/** 1 while Gallop (Shift+X) is on. */
	public static final String V_GALLOP = "pg_gallop";
	/** The Gallop Trample dash runs until. */
	public static final String V_TRAMPLE = "pg_trample_until";
	/** The Portal Shield stands until. */
	public static final String V_SHIELD = "pg_shield_until";
	/** Sky Gallop runs until. */
	public static final String V_SKY = "pg_sky_until";
	/** The Voyage portal stays open until. */
	public static final String V_VOYAGE = "pg_voyage_until";
	/** The Linked Portals stay open until. */
	public static final String V_LINK = "pg_link_until";

	// the pose ids (120..139, see MiraculousState)
	public static final int ANIM_THROW = 120;
	public static final int ANIM_CATCH = 121;
	public static final int ANIM_VOLLEY = 122;
	public static final int ANIM_KICK = 123;
	public static final int ANIM_TRAMPLE = 124;
	public static final int ANIM_HOP = 125;
	public static final int ANIM_GALLOP = 126;
	public static final int ANIM_TRAP = 127;
	public static final int ANIM_VOYAGE = 128;
	public static final int ANIM_SHIELD = 129;
	public static final int ANIM_SKY = 130;
	public static final int ANIM_WAYPOINT = 131;
	public static final int ANIM_LINK = 132;

	private Pegasus() {
	}

	/** Client-safe: transformed as Pegasus. */
	public static boolean is(Player player) {
		return Miraculous.transformedAs(player, Kwami.KAALKI);
	}

	public static long val(Player player, String key) {
		MiraculousState s = Miraculous.peek(player);
		return s == null ? 0L : s.val(key);
	}

	/** Client-safe: a timer value still in the future. */
	public static boolean running(Player player, String key) {
		return val(player, key) > player.level().getGameTime();
	}

	/** Client-safe: the Horseshoe is thrown. */
	public static boolean shoeOut(Player player) {
		return val(player, V_SHOE_OUT) != 0L;
	}

	/** Client-safe: Gallop is on. */
	public static boolean galloping(Player player) {
		return is(player) && val(player, V_GALLOP) != 0L;
	}

	/** Client-safe: Sky Gallop is running. */
	public static boolean skyGalloping(Player player) {
		return is(player) && running(player, V_SKY);
	}
}
