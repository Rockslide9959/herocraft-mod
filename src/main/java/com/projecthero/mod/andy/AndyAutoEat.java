package com.projecthero.mod.andy;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.server.level.ServerPlayer;

/**
 * v0.17.3 (user: "the undead power should have some form of auto feeding" -- passive): food is what his regrowth runs on, so he
 * eats from his inventory while he wears his suit's chestplate (Iron Man's rule since v0.17.3: see {@link #tick}). Formerly: below AUTO_EAT_BELOW, every
 * {@link AndyConfig#AUTO_EAT_EVERY} ticks he eats one piece of plain food from his inventory -- the least filling first, and only
 * if it won't be wasted. Never anything with an effect (golden apples, rotten flesh, spider eyes, pufferfish, suspicious stew,
 * chorus fruit, honey), never in creative.
 */
public final class AndyAutoEat {
	private static final Map<UUID, Long> NEXT = new ConcurrentHashMap<>();

	private AndyAutoEat() {
	}

	public static void clearSessionState() {
		NEXT.clear();
	}

	public static void forget(UUID id) {
		NEXT.remove(id);
	}

	/**
	 * Every tick for an Andy wearing his suit's chestplate (from {@link Andy#tick}). v0.17.3 (user: "feed the player when they get
	 * hungry enough to eat same as the iron man armour"): Iron Man's rule -- every {@link AndyConfig#AUTO_EAT_EVERY} ticks, whenever
	 * the hunger bar is below full, the best safe food in the inventory goes in ({@link IronManAutoFeed#feedNow}: most nutrition first,
	 * never anything with an effect, raw meat, chorus fruit or golden apples; bowls and bottles come back).
	 */
	public static void tick(ServerPlayer p) {
		if (p.getAbilities().instabuild || p.isSpectator() || !p.isAlive()) {
			return;
		}
		long now = p.level().getGameTime();
		Long next = NEXT.get(p.getUUID());
		if (next != null && now < next) {
			return;
		}
		if (p.getFoodData().getFoodLevel() >= 20) {
			return;
		}
		NEXT.put(p.getUUID(), now + AndyConfig.AUTO_EAT_EVERY);
		com.projecthero.mod.ironman.IronManAutoFeed.feedNow(p, "message.projecthero.andy.auto_feed");
	}
}
