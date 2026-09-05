package com.herocraft.mod.event.entity;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Ranged area denial (spec section 17). Keeps its distance and lobs {@link AcidGlobEntity}s on a
 * generous cooldown; the glob is slow and arcs visibly, so it is dodgeable, and the pool it leaves
 * pushes players off the ground they were holding.
 *
 * <p>Its melee is deliberately feeble and its health low -- the answer to an Acid Zombie is to reach
 * it, which is exactly the pressure it is there to create when it is paired with Armoured Zombies and
 * Sword Skeletons in the later waves.
 */
public class AcidZombie extends RaidUndead implements RangedAttackMob {
	/** Ticks between shots. Two and a half seconds is enough to read the wind-up and move. */
	private static final int ATTACK_INTERVAL = 50;
	private static final float ATTACK_RANGE = 16.0f;

	public AcidZombie(EntityType<? extends AcidZombie> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Zombie.createAttributes()
				.add(Attributes.MAX_HEALTH, 20.0)
				.add(Attributes.ATTACK_DAMAGE, 2.0)
				.add(Attributes.MOVEMENT_SPEED, 0.23)
				.add(Attributes.FOLLOW_RANGE, 40.0)
				.add(Attributes.ARMOR, 1.0);
	}

	/**
	 * Replaces the vanilla zombie melee package rather than adding to it: a ranged attacker that also
	 * runs {@code ZombieAttackGoal} would charge into melee and stop shooting, which is the opposite
	 * of the role.
	 */
	@Override
	protected void addBehaviourGoals() {
		this.goalSelector.addGoal(2, new RangedAttackGoal(this, 1.0, ATTACK_INTERVAL, ATTACK_RANGE));
		this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 1.0));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	@Override
	public void performRangedAttack(LivingEntity target, float velocity) {
		AcidGlobEntity glob = new AcidGlobEntity(level(), this);
		double dx = target.getX() - getX();
		double dy = target.getY(0.35) - glob.getY();
		double dz = target.getZ() - getZ();
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		// A deliberately lazy arc: slow enough to see coming and sidestep.
		glob.shoot(dx, dy + horizontal * 0.2, dz, 0.85f, 6.0f);
		level().playSound(null, getX(), getY(), getZ(), SoundEvents.LLAMA_SPIT, SoundSource.HOSTILE, 1.0f, 0.7f);
		level().addFreshEntity(glob);
	}
}
