package com.projecthero.mod.wolverine;

import com.projecthero.mod.ProjectHeroMod;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * v0.16.1: the Wolverine Suit's self-repair. A damaged piece mends {@link #REPAIR_PER_SECOND} durability every second
 * -- worn or carried -- but only once its holder has been out of combat for {@link #OUT_OF_COMBAT_TICKS} (no damage
 * dealt or taken). Replaces v0.15.21's flat 1 point every 5 s.
 *
 * <p>"Combat" is tracked for every living thing (anyone may wear the suit, power or not) in a server-only, unsaved
 * attachment: the game time of the last damage it took or dealt. A fresh login counts as out of combat. Deploying
 * Wolverine's claws (which tears his own hands) is not combat, the same rule as his Rage bar.
 */
public final class WolverineSuitMend {
	/** Out of combat = no damage dealt or taken for this long (30 s). */
	public static final int OUT_OF_COMBAT_TICKS = 30 * 20;
	/** Durability restored per mend... */
	public static final int REPAIR_PER_SECOND = 1;
	/** ...once every this many ticks (1 s). */
	public static final int REPAIR_INTERVAL_TICKS = 20;

	/** Game time of the last damage this entity took or dealt (0 = never). Not persisted, not synced. */
	public static final AttachmentType<Long> LAST_COMBAT_TICK = AttachmentRegistry.create(
			ProjectHeroMod.id("wolverine_suit_last_combat"),
			builder -> builder.initializer(() -> 0L));

	private WolverineSuitMend() {
	}

	public static void initialize() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseAmount, dealtAmount, blocked) -> {
			if (Wolverine.deployingClaws()) {
				return;
			}
			markCombat(entity);
			if (source.getEntity() instanceof LivingEntity attacker && attacker != entity) {
				markCombat(attacker);
			}
		});
	}

	/** Note that {@code entity} just took or dealt damage. Public for the gametests. */
	public static void markCombat(LivingEntity entity) {
		entity.setAttached(LAST_COMBAT_TICK, entity.level().getGameTime());
	}

	/** True once {@code entity} has gone {@link #OUT_OF_COMBAT_TICKS} without dealing or taking damage. */
	public static boolean outOfCombat(LivingEntity entity) {
		long last = entity.getAttachedOrElse(LAST_COMBAT_TICK, 0L);
		long now = entity.level().getGameTime();
		return last == 0L || now < last || now - last >= OUT_OF_COMBAT_TICKS;
	}

	/** One mend: {@link #REPAIR_PER_SECOND} points off the damage (never below 0). Public for the gametests. */
	public static void repairTick(ItemStack stack) {
		if (stack.isDamaged()) {
			stack.setDamageValue(Math.max(0, stack.getDamageValue() - REPAIR_PER_SECOND));
		}
	}
}
