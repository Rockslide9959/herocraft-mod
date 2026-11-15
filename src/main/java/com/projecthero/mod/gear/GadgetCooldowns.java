package com.projecthero.mod.gear;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;

/**
 * v0.17.5: gadget cooldowns and the Utility Belt's spare charge.
 *
 * <p>Without a belt, a gadget puts itself (and its upgraded twin) on an item cooldown. With a Utility Belt carried
 * anywhere in the inventory the cooldown is {@link #BELT_COOLDOWN_FACTOR} as long, and each gadget kind has one spare
 * charge: a use while the spare is ready spends the spare instead of starting the item cooldown (the spare then
 * recharges over one cooldown). So a belt lets you throw two in a row.
 *
 * <p>Static per-session state (UUID -> game time, no world references); cleared from {@code ServerStateReset}.
 */
public final class GadgetCooldowns {
	public static final float BELT_COOLDOWN_FACTOR = 0.7f;

	public enum Kind {
		GRAPPLE, SMOKE, FLASH
	}

	private static final Map<UUID, EnumMap<Kind, Long>> SPARE_READY_AT = new HashMap<>();

	private GadgetCooldowns() {
	}

	/** The cooldown a gadget with {@code baseTicks} gets for this player (shorter with a belt). */
	public static int cooldownFor(Player player, int baseTicks) {
		return UtilityBeltItem.carried(player) ? Math.max(1, Math.round(baseTicks * BELT_COOLDOWN_FACTOR)) : baseTicks;
	}

	/**
	 * After a successful gadget use: spends the belt's spare charge if it is ready, otherwise puts both items of the kind
	 * on cooldown. Returns true when the spare charge was spent.
	 */
	public static boolean afterUse(Player player, Kind kind, int baseTicks, Item... items) {
		int ticks = cooldownFor(player, baseTicks);
		if (UtilityBeltItem.carried(player)) {
			long now = player.level().getGameTime();
			EnumMap<Kind, Long> m = SPARE_READY_AT.computeIfAbsent(player.getUUID(), u -> new EnumMap<>(Kind.class));
			Long ready = m.get(kind);
			if (ready == null || now >= ready) {
				m.put(kind, now + ticks);
				return true;
			}
		}
		for (Item i : items) {
			if (i != null) {
				player.getCooldowns().addCooldown(i, ticks);
			}
		}
		return false;
	}

	/** True when the belt's spare charge for this kind is ready (or would be, if a belt were carried). */
	public static boolean spareReady(Player player, Kind kind) {
		EnumMap<Kind, Long> m = SPARE_READY_AT.get(player.getUUID());
		Long ready = m == null ? null : m.get(kind);
		return ready == null || player.level().getGameTime() >= ready;
	}

	public static void clearSessionState() {
		SPARE_READY_AT.clear();
	}
}
