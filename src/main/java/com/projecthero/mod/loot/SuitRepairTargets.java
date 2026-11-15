package com.projecthero.mod.loot;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * v0.17.5: what a Suit Repair Kit mends. Worn armour with durability is always repaired (by {@code fraction} of each
 * piece's max durability, see {@link #repairArmour}); any other suit-health system registers a {@link Repair} here
 * (e.g. Iron Man suit integrity). If nothing is repaired anywhere the kit is refused (not used up).
 *
 * <pre>{@code
 * SuitRepairTargets.register("iron_man", (player, fraction) -> { ...; return changed; });
 * }</pre>
 *
 * Built-in repairs live in {@link SupplyTargetDefaults}.
 */
public final class SuitRepairTargets {
	/** Repairs by {@code fraction} (0..1) of the max; returns true if anything was repaired. */
	@FunctionalInterface
	public interface Repair {
		boolean repair(ServerPlayer player, float fraction);
	}

	public record Target(String id, Repair repair) {
	}

	private static final List<Target> TARGETS = new CopyOnWriteArrayList<>();

	private SuitRepairTargets() {
	}

	public static void register(String id, Repair repair) {
		Target t = new Target(id, repair);
		for (int i = 0; i < TARGETS.size(); i++) {
			if (TARGETS.get(i).id().equals(id)) {
				TARGETS.set(i, t);
				return;
			}
		}
		TARGETS.add(t);
	}

	public static List<Target> targets() {
		return List.copyOf(TARGETS);
	}

	/** Worn armour first, then every registered target. Returns true if anything was repaired. */
	public static boolean apply(ServerPlayer player, float fraction) {
		boolean any = repairArmour(player, fraction);
		for (Target t : TARGETS) {
			try {
				any |= t.repair().repair(player, fraction);
			} catch (RuntimeException e) {
				com.projecthero.mod.ProjectHeroMod.LOGGER.warn("Suit Repair Kit target {} failed", t.id(), e);
			}
		}
		return any;
	}

	/** Mends every damaged armour piece the player wears by {@code fraction} of its max durability. */
	public static boolean repairArmour(ServerPlayer player, float fraction) {
		boolean any = false;
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			if (!slot.isArmor()) {
				continue;
			}
			ItemStack stack = player.getItemBySlot(slot);
			if (stack.isEmpty() || !stack.isDamageableItem() || stack.getDamageValue() <= 0) {
				continue;
			}
			int mend = Math.max(1, Math.round(stack.getMaxDamage() * fraction));
			stack.setDamageValue(Math.max(0, stack.getDamageValue() - mend));
			any = true;
		}
		return any;
	}
}
