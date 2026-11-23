package com.projecthero.mod.alarm;

import java.util.List;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.7: what the {@link GuardRole}s do. Medic heals and Sniper shots run from the zone step ({@link #step}); the
 * Shield-bearer's frontal block and the Captain's +20% aura are applied to the incoming damage at the top of
 * {@code LivingEntity#hurt} ({@link #modifyIncoming}, from {@code LivingEntityAlarmDamageMixin}). The Runner is driven by
 * {@link AlarmDirector#runners}; the Captain's keycard drops from {@link AlarmMod}'s death hook.
 */
public final class AlarmRoles {
	public static final double CAPTAIN_AURA = 8.0;
	public static final float CAPTAIN_BONUS = 1.2f;
	public static final float SHIELD_FACTOR = 0.3f;
	public static final double SHIELD_ARC_COS = Math.cos(Math.toRadians(60));
	public static final float SHIELD_BREAK_HIT = 10f;
	public static final double MEDIC_RANGE = 8.0;
	public static final float MEDIC_HEAL = 4f;
	public static final int MEDIC_PERIOD = 40;
	/** Ticks a Sniper aims (laser on its target) before each shot. */
	public static final int SNIPER_AIM = 50;
	public static final double SNIPER_RANGE = 40.0;
	public static final int SNIPER_BRIGHT = 30;

	private AlarmRoles() {
	}

	static void step(ServerLevel level, AlarmZone z, List<ServerPlayer> players) {
		boolean medicTick = level.getGameTime() % MEDIC_PERIOD == 0;
		for (Mob g : z.guards) {
			if (!g.isAlive()) {
				continue;
			}
			GuardRole r = AlarmGuards.roleOf(g);
			if (r == GuardRole.MEDIC && medicTick) {
				heal(level, z, g);
			} else if (r == GuardRole.SNIPER) {
				snipe(level, z, g);
			}
		}
	}

	/** The Medic heals the most-hurt guard (itself included) within {@link #MEDIC_RANGE}. Returns who, or null. */
	public static Mob heal(ServerLevel level, AlarmZone z, Mob medic) {
		Mob worst = null;
		float worstFrac = 0.999f;
		for (Mob g : z.guards) {
			if (!g.isAlive() || g.distanceToSqr(medic) > MEDIC_RANGE * MEDIC_RANGE) {
				continue;
			}
			float f = g.getHealth() / g.getMaxHealth();
			if (f < worstFrac) {
				worstFrac = f;
				worst = g;
			}
		}
		if (worst == null) {
			return null;
		}
		worst.heal(MEDIC_HEAL);
		medic.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
		level.sendParticles(ParticleTypes.HEART, worst.getX(), worst.getY() + worst.getBbHeight() + 0.3, worst.getZ(), 3, 0.3, 0.2, 0.3, 0.0);
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, worst.getX(), worst.getY() + 1, worst.getZ(), 8, 0.4, 0.5, 0.4, 0.0);
		level.playSound(null, worst.blockPosition(), SoundEvents.BREWING_STAND_BREW, SoundSource.HOSTILE, 0.6f, 1.6f);
		return worst;
	}

	/** Aim time of a sniper (ticks its laser has been on the current target). */
	public static int aimOf(AlarmZone z, Mob sniper) {
		return z.aim.getOrDefault(sniper.getUUID(), 0);
	}

	/** The target a sniper's laser points at (a targetable player in range it can see), or null. */
	public static Player laserTarget(Mob sniper) {
		if (sniper.getTarget() instanceof Player p && AlarmDirector.targetable(p) && sniper.distanceToSqr(p) <= SNIPER_RANGE * SNIPER_RANGE
				&& sniper.getSensing().hasLineOfSight(p)) {
			return p;
		}
		return null;
	}

	static void snipe(ServerLevel level, AlarmZone z, Mob sniper) {
		Player t = laserTarget(sniper);
		if (t == null) {
			z.aim.remove(sniper.getUUID());
			return;
		}
		sniper.getLookControl().setLookAt(t, 30f, 30f);
		int aim = z.aim.getOrDefault(sniper.getUUID(), 0) + AlarmDirector.STEP;
		if (aim >= SNIPER_AIM) {
			shoot(level, sniper, t);
			aim = 0;
		}
		z.aim.put(sniper.getUUID(), aim);
	}

	/** The sniper's heavy shot (an arrow at 3.2 blocks/tick, ~9 damage). */
	public static void shoot(ServerLevel level, Mob sniper, LivingEntity t) {
		Arrow arrow = new Arrow(level, sniper, new ItemStack(Items.ARROW), null);
		arrow.setBaseDamage(3.0);
		arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
		Vec3 from = sniper.getEyePosition();
		arrow.setPos(from.x, from.y - 0.1, from.z);
		Vec3 d = t.getBoundingBox().getCenter().subtract(arrow.position());
		arrow.shoot(d.x, d.y + d.horizontalDistance() * 0.02, d.z, 3.2f, 0.6f);
		level.addFreshEntity(arrow);
		level.playSound(null, sniper.getX(), sniper.getEyeY(), sniper.getZ(), SoundEvents.CROSSBOW_SHOOT, SoundSource.HOSTILE, 1.6f, 0.6f);
	}

	/** Incoming-damage scaling for the Shield-bearer (victim) and the Captain's aura (attacker). Server side only. */
	public static float modifyIncoming(LivingEntity self, DamageSource source, float amount) {
		if (amount <= 0f) {
			return amount;
		}
		if (self instanceof Mob m && AlarmGuards.shieldUp(m)) {
			amount = shield(m, source, amount);
		}
		Entity att = source.getEntity();
		if (att instanceof Mob am && am != self && AlarmGuards.isGuard(am) && !AlarmGuards.hasRole(am, GuardRole.CAPTAIN) && captainNear(am)) {
			amount *= CAPTAIN_BONUS;
		}
		return amount;
	}

	/** True if a living Captain stands within {@link #CAPTAIN_AURA} of {@code guard}. */
	public static boolean captainNear(Mob guard) {
		AABB box = guard.getBoundingBox().inflate(CAPTAIN_AURA);
		return !guard.level().getEntitiesOfClass(Mob.class, box,
				c -> c != guard && c.isAlive() && AlarmGuards.hasRole(c, GuardRole.CAPTAIN) && c.distanceToSqr(guard) <= CAPTAIN_AURA * CAPTAIN_AURA).isEmpty();
	}

	/** True if a hit from {@code from} lands on the front of {@code m} (within 60 degrees of its body facing). */
	public static boolean fromFront(Mob m, Vec3 from) {
		Vec3 facing = Vec3.directionFromRotation(0f, m.yBodyRot);
		Vec3 to = new Vec3(from.x - m.getX(), 0, from.z - m.getZ());
		if (to.lengthSqr() < 1.0e-4) {
			return true;
		}
		return facing.dot(to.normalize()) >= SHIELD_ARC_COS;
	}

	private static float shield(Mob m, DamageSource source, float amount) {
		Vec3 from = source.getSourcePosition();
		if (from == null || source.is(DamageTypeTags.BYPASSES_SHIELD) || !fromFront(m, from)) {
			return amount;
		}
		boolean axe = source.getDirectEntity() instanceof LivingEntity att && att.getMainHandItem().getItem() instanceof AxeItem;
		if (m.level() instanceof ServerLevel level) {
			level.playSound(null, m.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 1.0f, 0.9f);
			if (axe || amount >= SHIELD_BREAK_HIT) {
				m.addTag(AlarmGuards.SHIELD_DOWN);
				m.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
				level.playSound(null, m.blockPosition(), SoundEvents.SHIELD_BREAK, SoundSource.HOSTILE, 1.2f, 0.8f);
				level.sendParticles(new net.minecraft.core.particles.ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.SHIELD)),
						m.getX(), m.getY() + 1.2, m.getZ(), 16, 0.3, 0.3, 0.3, 0.1);
			}
		}
		return amount * SHIELD_FACTOR;
	}
}
