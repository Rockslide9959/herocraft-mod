package com.projecthero.mod.alarm;

import com.projecthero.mod.hideout.HideoutBoss;
import com.projecthero.mod.hideout.HideoutGuards;
import com.projecthero.mod.mixin.MobGoalsAccessor;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * v0.17.7: which mobs are alarm-zone guards, and their {@link GuardRole}s.
 *
 * <p>A guard is any mob tagged {@link #TAG} or the v0.17.5 hideout tag ({@link HideoutGuards#TAG}); mini-bosses
 * ({@link HideoutBoss}) are never guards (they keep their own threat-table AI). Inside a zone a guard that has not been
 * alerted has its "hunt the nearest player" target goals removed ({@link #pacify}) -- it only fights once it has
 * <i>seen</i> you (the suspicion meter), been hurt, or the zone goes Alert.
 *
 * <pre>
 * Mob m = MyGuards.spawn(...);
 * AlarmGuards.makeGuard(m);                 // only needed for mobs that are not hideout guards
 * AlarmGuards.setRole(m, GuardRole.CAPTAIN); // name tag, gear and attributes for the role
 * </pre>
 */
public final class AlarmGuards {
	public static final String TAG = "projecthero.alarm_guard";
	/** Reinforcements spawned by an alarm wave. */
	public static final String WAVE_TAG = "projecthero.alarm_wave";
	/** A Captain whose keycard has already dropped. */
	public static final String KEYCARD_DROPPED = "projecthero.keycard_dropped";
	/** A Shield-bearer whose shield was knocked away. */
	public static final String SHIELD_DOWN = "projecthero.shield_down";

	private static final ResourceLocation RUNNER_SPEED = com.projecthero.mod.ProjectHeroMod.id("alarm_runner_speed");
	private static final ResourceLocation SNIPER_RANGE = com.projecthero.mod.ProjectHeroMod.id("alarm_sniper_range");
	private static final ResourceLocation CAPTAIN_HEALTH = com.projecthero.mod.ProjectHeroMod.id("alarm_captain_health");

	private AlarmGuards() {
	}

	public static boolean isGuard(Mob mob) {
		if (mob instanceof HideoutBoss) {
			return false;
		}
		var tags = mob.getTags();
		return tags.contains(TAG) || tags.contains(HideoutGuards.TAG);
	}

	/** Tags {@code mob} as a guard (persistent, never despawns). */
	public static void makeGuard(Mob mob) {
		mob.addTag(TAG);
		mob.setPersistenceRequired();
	}

	public static GuardRole roleOf(Mob mob) {
		var tags = mob.getTags();
		if (tags.isEmpty()) {
			return null;
		}
		for (GuardRole r : GuardRole.values()) {
			if (tags.contains(r.tag())) {
				return r;
			}
		}
		return null;
	}

	public static boolean hasRole(Mob mob, GuardRole role) {
		return mob.getTags().contains(role.tag());
	}

	/**
	 * Gives {@code mob} a role: the tag, a name tag ("Captain", shown when you look at it), and the role's kit -- a gold
	 * helmet and +50% health for the Captain, +30% speed and no armour for the Runner, a shield in the off hand for the
	 * Shield-bearer, a 40-block follow range for the Sniper. Also makes it a guard. Returns {@code mob}.
	 */
	public static Mob setRole(Mob mob, GuardRole role) {
		if (mob == null) {
			return null;
		}
		makeGuard(mob);
		for (GuardRole r : GuardRole.values()) {
			mob.removeTag(r.tag());
		}
		mob.addTag(role.tag());
		mob.setCustomName(Component.translatable(role.key()));
		switch (role) {
			case CAPTAIN -> {
				equip(mob, EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));
				AttributeInstance hp = mob.getAttribute(Attributes.MAX_HEALTH);
				if (hp != null && !hp.hasModifier(CAPTAIN_HEALTH)) {
					hp.addPermanentModifier(new AttributeModifier(CAPTAIN_HEALTH, 0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
					mob.setHealth(mob.getMaxHealth());
				}
			}
			case RUNNER -> {
				AttributeInstance sp = mob.getAttribute(Attributes.MOVEMENT_SPEED);
				if (sp != null && !sp.hasModifier(RUNNER_SPEED)) {
					sp.addPermanentModifier(new AttributeModifier(RUNNER_SPEED, 0.3, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
				}
				for (EquipmentSlot s : new EquipmentSlot[] { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET }) {
					mob.setItemSlot(s, ItemStack.EMPTY);
				}
			}
			case SHIELD -> equip(mob, EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
			case SNIPER -> {
				AttributeInstance fr = mob.getAttribute(Attributes.FOLLOW_RANGE);
				if (fr != null && !fr.hasModifier(SNIPER_RANGE)) {
					fr.addPermanentModifier(new AttributeModifier(SNIPER_RANGE, 24.0, AttributeModifier.Operation.ADD_VALUE));
				}
			}
			case MEDIC -> {
			}
		}
		return mob;
	}

	private static void equip(Mob mob, EquipmentSlot slot, ItemStack stack) {
		mob.setItemSlot(slot, stack);
		mob.setDropChance(slot, 0f);
	}

	/** Removes {@code mob}'s "attack the nearest player" target goals. True if any were removed. */
	public static boolean pacify(Mob mob) {
		var selector = ((MobGoalsAccessor) mob).projecthero$targetSelector();
		boolean any = selector.getAvailableGoals().stream().anyMatch(g -> g.getGoal() instanceof NearestAttackableTargetGoal<?>);
		if (any) {
			selector.removeAllGoals(g -> g instanceof NearestAttackableTargetGoal<?>);
		}
		return any;
	}

	/**
	 * True while {@code mob} is a guard standing in an alarm zone that is not Alert and has not alerted it. Mobs that pick
	 * targets in their own code (not with target goals) ask this and leave players alone until it turns false.
	 */
	public static boolean unalerted(Mob mob) {
		if (!(mob.level() instanceof net.minecraft.server.level.ServerLevel s) || !isGuard(mob)) {
			return false;
		}
		AlarmZone z = AlarmZones.get(s).zoneAt(mob.blockPosition());
		return z != null && z.state != AlarmState.ALERT && !z.isAlerted(mob);
	}

	/** True while {@code mob} still has a shield up (Shield-bearer role, shield not knocked away). */
	public static boolean shieldUp(Mob mob) {
		return hasRole(mob, GuardRole.SHIELD) && !mob.getTags().contains(SHIELD_DOWN) && mob.getOffhandItem().is(Items.SHIELD);
	}
}
