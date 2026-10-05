package com.projecthero.mod.syndicate.entity;

import java.util.EnumSet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.25: the Syndicate's heavy -- a quarter bigger than a man, armoured, bare-knuckled. Between punches he does two
 * things worth learning:
 * <ul>
 *   <li><b>Shoulder Charge</b> (5-14 blocks off): he stamps and lowers his shoulder for 0.75 s, then barrels in a straight
 *       line. Whoever he hits is hurt and thrown; if he hits a wall instead he is dazed for 1.5 s and takes extra
 *       damage -- side-step it into the crates.</li>
 *   <li><b>Ground Slam</b> (two or more people within 3.5 blocks): both fists into the floor -- a ring that hurts and
 *       pops everyone up.</li>
 * </ul>
 */
public class SyndicateEnforcer extends SyndicateCriminal {
	public static final byte ACTION_WINDUP = 2;
	public static final byte ACTION_CHARGE = 3;
	public static final byte ACTION_DAZED = 4;
	public static final byte ACTION_SLAM = 5;

	private int dazed;

	public SyndicateEnforcer(EntityType<? extends SyndicateEnforcer> type, Level level) {
		super(type, level);
		this.xpReward = 20;
		refreshDimensions(); // the SCALE attribute sizes his hit-box
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 70.0)
				.add(Attributes.MOVEMENT_SPEED, 0.27)
				.add(Attributes.ATTACK_DAMAGE, 9.0)
				.add(Attributes.ATTACK_KNOCKBACK, 1.2)
				.add(Attributes.ARMOR, 8.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.7)
				.add(Attributes.FOLLOW_RANGE, 40.0)
				.add(Attributes.SCALE, 1.25);
	}

	@Override
	protected SyndicateSkin defaultSkin() {
		return SyndicateSkin.THUG_A;
	}

	@Override
	protected void registerCombatGoals() {
		goalSelector.addGoal(2, new ChargeGoal(this));
		goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.1, false));
	}

	@Override
	public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason, SpawnGroupData data) {
		SpawnGroupData out = super.finalizeSpawn(level, difficulty, reason, data);
		setSkin(random.nextBoolean() ? SyndicateSkin.THUG_A : SyndicateSkin.THUG_C);
		return out;
	}

	@Override
	public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
		return super.hurt(source, dazed > 0 ? amount * 1.5f : amount);
	}

	@Override
	public boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
		return dazed <= 0 && super.doHurtTarget(target);
	}

	@Override
	protected void customServerAiStep() {
		super.customServerAiStep();
		if (dazed > 0) {
			getNavigation().stop();
			setDeltaMovement(0, getDeltaMovement().y, 0);
			if (tickCount % 6 == 0 && level() instanceof ServerLevel server) {
				server.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT, getX(), getEyeY() + 0.4, getZ(), 2, 0.3, 0.1, 0.3, 0.0);
			}
			if (--dazed == 0) {
				setAction(ACTION_NONE);
			}
		}
	}

	boolean isDazed() {
		return dazed > 0;
	}

	void daze(int ticks) {
		dazed = ticks;
		setAction(ACTION_DAZED);
	}

	/** Shoulder Charge and Ground Slam: one goal, so they never overlap. */
	static final class ChargeGoal extends Goal {
		private final SyndicateEnforcer mob;
		private int cooldown = 60;
		private int slamCooldown = 80;
		private int timer;
		private int stage; // 0 idle, 1 windup, 2 charging, 3 slam
		private Vec3 dir = Vec3.ZERO;
		private final java.util.Set<LivingEntity> struck = new java.util.HashSet<>();

		ChargeGoal(SyndicateEnforcer mob) {
			this.mob = mob;
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
		}

		@Override
		public boolean canUse() {
			if (cooldown > 0) {
				cooldown--;
			}
			if (slamCooldown > 0) {
				slamCooldown--;
			}
			LivingEntity t = mob.getTarget();
			if (t == null || !t.isAlive() || mob.isDazed() || !mob.onGround()) {
				return false;
			}
			if (slamCooldown <= 0 && crowd() >= 2) {
				stage = 3;
				return true;
			}
			double d = mob.distanceTo(t);
			if (cooldown <= 0 && d >= 5 && d <= 14 && mob.getSensing().hasLineOfSight(t)) {
				stage = 1;
				return true;
			}
			return false;
		}

		private int crowd() {
			return mob.level().getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(3.5),
					e -> e.isAlive() && !(e instanceof SyndicateCriminal) && e instanceof net.minecraft.world.entity.player.Player p && !p.isCreative() && !p.isSpectator())
					.size();
		}

		@Override
		public void start() {
			timer = 0;
			struck.clear();
			mob.getNavigation().stop();
			mob.setAction(stage == 3 ? ACTION_SLAM : ACTION_WINDUP);
		}

		@Override
		public boolean canContinueToUse() {
			return stage != 0 && mob.isAlive() && !mob.isDazed();
		}

		@Override
		public void stop() {
			if (!mob.isDazed()) {
				mob.setAction(ACTION_NONE);
			}
			stage = 0;
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			if (!(mob.level() instanceof ServerLevel level)) {
				return;
			}
			timer++;
			LivingEntity t = mob.getTarget();
			switch (stage) {
				case 1 -> {
					if (t != null) {
						mob.getLookControl().setLookAt(t, 40f, 40f);
						Vec3 to = t.position().subtract(mob.position());
						dir = new Vec3(to.x, 0, to.z).normalize();
					}
					if (timer % 5 == 0) {
						BlockPos below = mob.blockPosition().below();
						level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(below)), mob.getX(), mob.getY() + 0.1,
								mob.getZ(), 8, 0.4, 0.05, 0.4, 0.1);
						level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.RAVAGER_STEP, SoundSource.HOSTILE, 1.0f, 0.8f);
					}
					if (timer >= 15) {
						stage = 2;
						timer = 0;
						mob.setAction(ACTION_CHARGE);
						level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.0f, 1.4f);
					}
				}
				case 2 -> {
					mob.setYRot((float) Math.toDegrees(Math.atan2(-dir.x, dir.z)));
					mob.setYBodyRot(mob.getYRot());
					Vec3 v = dir.scale(0.85);
					mob.setDeltaMovement(v.x, mob.getDeltaMovement().y, v.z);
					for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(0.6),
							e -> e != mob && e.isAlive() && !(e instanceof SyndicateCriminal))) {
						if (struck.add(e)) {
							e.hurt(mob.damageSources().mobAttack(mob), 10.0f);
							e.push(dir.x * 1.6, 0.55, dir.z * 1.6);
							e.hurtMarked = true;
							level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.HOSTILE, 1.2f, 0.7f);
						}
					}
					if (mob.horizontalCollision && timer > 2) {
						level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.ZOMBIE_ATTACK_IRON_DOOR, SoundSource.HOSTILE, 1.2f, 0.7f);
						level.sendParticles(ParticleTypes.CRIT, mob.getX(), mob.getEyeY(), mob.getZ(), 12, 0.3, 0.3, 0.3, 0.2);
						mob.daze(30);
						end(60 + mob.random.nextInt(40));
						return;
					}
					if (timer >= 22) {
						end(80 + mob.random.nextInt(40));
					}
				}
				case 3 -> {
					if (timer == 10) {
						level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 0.8f, 1.4f);
						BlockPos below = mob.blockPosition().below();
						for (int i = 0; i < 24; i++) {
							double a = i * Math.PI * 2 / 24;
							level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(below)), mob.getX() + Math.cos(a) * 2.5,
									mob.getY() + 0.1, mob.getZ() + Math.sin(a) * 2.5, 3, 0.2, 0.05, 0.2, 0.1);
						}
						for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(3.5, 1.0, 3.5),
								e -> e != mob && e.isAlive() && !(e instanceof SyndicateCriminal) && e.onGround())) {
							e.hurt(mob.damageSources().mobAttack(mob), 7.0f);
							Vec3 out = e.position().subtract(mob.position());
							Vec3 h = new Vec3(out.x, 0, out.z).normalize();
							e.push(h.x * 0.6, 0.75, h.z * 0.6);
							e.hurtMarked = true;
						}
					}
					if (timer >= 20) {
						slamCooldown = 140;
						stage = 0;
					}
				}
				default -> stage = 0;
			}
		}

		private void end(int nextCooldown) {
			cooldown = nextCooldown;
			stage = 0;
			mob.setDeltaMovement(0, mob.getDeltaMovement().y, 0);
		}
	}
}
