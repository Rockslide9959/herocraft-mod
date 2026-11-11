package com.projecthero.mod.saiyan.kit;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.3 Saiyan revamp: the move kit. CONTRACT shared by the parallel builds -- the core (keys, Ki, rage, Super Saiyan,
 * Kaioken, flight) routes each key to {@link #press}; the kit owns every move. Move ids:
 * <pre>
 *   R combo        Shift+R martial      G ki_blast       Shift+G beam        Z strong        Shift+Z spirit_bomb
 *   X dash         Shift+X rush         C aoe            Shift+C aoe2        V utility (tap) Shift+V utility2
 * </pre>
 * {@code spirit_bomb} is delegated to {@link SaiyanSpiritBomb}. Ki is spent through the core's {@code Saiyan.spendKi}.
 */
public final class SaiyanKit {
	public static final String COMBO = "combo", MARTIAL = "martial", KI_BLAST = "ki_blast", BEAM = "beam", STRONG = "strong",
			SPIRIT_BOMB = "spirit_bomb", DASH = "dash", RUSH = "rush", AOE = "aoe", AOE2 = "aoe2", UTILITY = "utility", UTILITY2 = "utility2";

	private SaiyanKit() {
	}

	/** A key went down ({@code down}) or up for move {@code id}. */
	public static void press(ServerPlayer p, String id, boolean down) {
		if (SPIRIT_BOMB.equals(id)) {
			SaiyanSpiritBomb.press(p, down);
		}
	}

	/** Every server tick for every Saiyan. */
	public static void tick(ServerPlayer p) {
		SaiyanSpiritBomb.tick(p);
	}

	/** Left click on a creature (the combo's melee hit hook). */
	public static void attack(ServerPlayer p) {
	}

	public static int cooldownRemaining(Player p, String id) {
		return SPIRIT_BOMB.equals(id) ? SaiyanSpiritBomb.cooldownRemaining(p) : 0;
	}

	public static int maxCooldown(String id) {
		return SPIRIT_BOMB.equals(id) ? SaiyanSpiritBomb.maxCooldown() : 1;
	}

	/** True while a move holds the player (rooted / channelling), so the core can block other inputs. */
	public static boolean busy(Player p) {
		return SaiyanSpiritBomb.busy(p);
	}

	/** Session reset / player leave. */
	public static void clearSessionState() {
		SaiyanSpiritBomb.clearSessionState();
	}
}
