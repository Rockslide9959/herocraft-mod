package com.projecthero.mod.spider;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.spider.data.SpiderManState;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Spider-Man's organic webbing budget. He needs no mechanical shooters and no ammunition item; every
 * web ability spends from this internal reserve instead, and it refills on its own a moment after the
 * last <em>major</em> web ability.
 *
 * <p>Swinging is deliberately the cheapest thing here by a wide margin -- it is the traversal the
 * whole power is built around, so a swing drains slowly while the line is attached and does not push
 * back the regeneration delay the combat abilities do. Combat webbing is what actually costs.
 *
 * <p>Server-authoritative: only {@link #spend} on a {@link ServerPlayer} can move the number down and
 * only {@link #tick} can move it up, so a modified client can never mint reserve. The value is synced
 * to clients purely so the HUD can draw it.
 */
public final class SpiderWebReserve {
	/**
	 * The <b>normal</b> Spider-Man maximum (v0.9.6: doubled from 100). A player who has ever bonded with
	 * a Symbiote gets the doubled ceiling from {@link #maxFor} permanently -- see
	 * {@link com.projecthero.mod.symbiote.Symbiote}.
	 */
	public static final float MAX = 200.0f;

	/**
	 * v0.9.10: the Symbiote roughly doubles Spider-Man's webbing capacity. v0.9.13: this is a bond
	 * perk, not a suit-on perk -- the organism has permanently strengthened the wearer's own webbing
	 * glands, so the doubled ceiling applies whether or not the black suit is currently worn.
	 */
	public static final float SYMBIOTE_MULTIPLIER = 2.0f;

	/**
	 * The current webbing ceiling for this player: {@link #MAX}, or {@code MAX * }{@link
	 * #SYMBIOTE_MULTIPLIER} once they have bonded with a Symbiote (regardless of whether it is currently
	 * worn). Deliberately a pure function of the synced Symbiote state, so it works on both sides and
	 * toggling the Symbiote never mints or destroys reserve -- only unbonding entirely moves the cap
	 * back down.
	 */
	public static float maxFor(Player player) {
		return com.projecthero.mod.symbiote.Symbiote.hasSymbiote(player) ? MAX * SYMBIOTE_MULTIPLIER : MAX;
	}

	// ---- costs (spec section 20 starting values) ----
	public static final float COST_WEB_ZIP = 5.0f;
	/** v0.6.20: lowered from 4 -- Web Shot is spammable now (no cooldown), so each shot costs less. */
	public static final float COST_WEB_SHOT = 2.0f;
	public static final float COST_WEB_YANK = 6.0f;
	public static final float COST_WEB_NET = 20.0f;
	/** Fired once when a swing line attaches. Deliberately tiny -- swinging is the traversal the whole
	 *  power is built around and should almost never be reserve-gated. */
	public static final float COST_SWING_FIRE = 0.4f;
	/**
	 * Drain per tick while a swing line is attached. Very low on purpose (v0.6.17): ~0.8/s, i.e. over
	 * two straight minutes of continuous rope time from a full reserve, so a Spider-Man crossing the
	 * map by swing effectively never runs dry.
	 */
	public static final float DRAIN_SWING_PER_TICK = 0.04f;

	/** Regeneration: 4 reserve per second once the delay has elapsed. */
	private static final float REGEN_PER_TICK = 0.2f;
	/** Ticks after a major web ability before regeneration resumes. */
	public static final int REGEN_DELAY_TICKS = 30;
	/** Black Suit Spider-Man: web regeneration +50% (spec). */
	private static final float SYMBIOTE_REGEN_MULTIPLIER = 1.5f;

	private SpiderWebReserve() {
	}

	public static float get(Player player) {
		SpiderManState s = player.getAttachedOrElse(ModAttachments.SPIDER_MAN_STATE, null);
		return s == null ? 0.0f : s.webReserve;
	}

	public static boolean has(ServerPlayer player, float amount) {
		return SpiderMan.state(player).webReserve >= amount;
	}

	/**
	 * Spend from the reserve. Returns false (and changes nothing) when there is not enough, which is
	 * how every ability reports "out of webbing" to the player.
	 *
	 * @param major true for the combat/utility abilities that should also pause regeneration; false for
	 *              the swing's continuous trickle
	 */
	public static boolean spend(ServerPlayer player, float amount, boolean major) {
		SpiderManState s = SpiderMan.state(player);
		if (s.webReserve < amount) {
			return false;
		}
		SpiderManState c = s.copy();
		c.webReserve = Math.max(0.0f, c.webReserve - amount);
		if (major) {
			c.lastWebUseTick = player.level().getGameTime();
		}
		SpiderMan.saveReserve(player, c, s);
		return true;
	}

	/**
	 * Per-tick regeneration. Held back for {@link #REGEN_DELAY_TICKS} after a major ability, and
	 * suppressed entirely while a swing line is attached (the swing is spending, not saving).
	 */
	public static void tick(ServerPlayer player) {
		SpiderManState s = SpiderMan.state(player);
		float cap = maxFor(player);
		if (s.webReserve >= cap || s.swinging) {
			return;
		}
		if (player.level().getGameTime() - s.lastWebUseTick < REGEN_DELAY_TICKS) {
			return;
		}
		float regen = REGEN_PER_TICK;
		if (com.projecthero.mod.symbiote.Symbiote.isActive(player)
				&& com.projecthero.mod.symbiote.SymbioteHostType.of(player) == com.projecthero.mod.symbiote.SymbioteHostType.SPIDER_MAN) {
			regen *= SYMBIOTE_REGEN_MULTIPLIER;
		}
		SpiderManState c = s.copy();
		c.webReserve = Math.min(cap, c.webReserve + regen);
		SpiderMan.saveReserve(player, c, s);
	}
}
