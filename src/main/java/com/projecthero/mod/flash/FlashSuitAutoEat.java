package com.projecthero.mod.flash;

import com.projecthero.mod.hero.power.p04.SuperSpeedHandlers;
import com.projecthero.mod.ironman.IronManAutoFeed;
import com.projecthero.mod.speedforce.SpeedForceSpeedster;

import net.minecraft.server.level.ServerPlayer;

/**
 * v0.17.1 (user: "when player is wearing the flash armour make them automatically eat food please since their powers cause
 * them to use up a lot of food"): a speedster (the plain Speedster or the Speed Force Speedster) in the full Flash Suit
 * is fed from their inventory.
 *
 * <p>v0.17.1 (user: "make the flash suit feed you the same as the iron man armours feed you ... as soon as you lose half a
 * hunger bar"): this is now the Iron Man suits' own feeding ({@link IronManAutoFeed}) -- every 2 s, whenever the hunger bar
 * is below full, the best safe food goes in (most nutrition, then saturation; nothing raw, nothing with an effect, no
 * chorus fruit, golden apples or suspicious stew), bowls and bottles come back.
 */
public final class FlashSuitAutoEat {
	private FlashSuitAutoEat() {
	}

	public static void tick(ServerPlayer p) {
		if (p.tickCount % 40 != 0 || p.isUsingItem() || !FlashSuit.wearsFull(p)
				|| !(SuperSpeedHandlers.owns(p) || SpeedForceSpeedster.hasPower(p))) {
			return;
		}
		IronManAutoFeed.tick(p, "message.projecthero.flash.auto_eat");
	}
}
