package com.projecthero.mod.hideout.boss;

import java.util.EnumSet;

import com.projecthero.mod.hideout.HideoutBoss;
import com.projecthero.mod.hideout.HideoutBossLink;
import com.projecthero.mod.hideout.HideoutFaction;
import com.projecthero.mod.hideout.HideoutGuards;
import com.projecthero.mod.syndicate.entity.SyndicateCriminal;
import com.projecthero.mod.syndicate.entity.SyndicateEnforcer;
import com.projecthero.mod.syndicate.entity.SyndicateSkin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.5: <b>Kingpin's Enforcer</b> -- the Kingpin's Front mini-boss. The Syndicate's biggest heavy (x1.6, 340 health,
 * 12 armour) in a black suit, guarding the back-office vault. Keeps the Enforcer's <b>Shoulder Charge</b> (wall = dazed)
 * and adds:
 * <ul>
 * <li><b>Haymaker Combo</b> (within 3.5 blocks): two jabs and a launching uppercut, half a second apart.</li>
 * <li><b>Seismic Stomp</b> (within 6 blocks): a one-second wind-up (a dust ring marks the reach), then a 6-block
 * shockwave that hurts and throws everyone on the ground -- jump it.</li>
 * <li><b>"Boys!"</b>: at half health, once, two thugs and a gunman come running.</li>
 * </ul>
 * Shared threat table, own boss bar (see {@link HideoutBossLink}).
 */
public class KingpinEnforcerBoss extends SyndicateEnforcer implements HideoutBoss {
	public static final float SCALE = 1.6f;
	static final int STOMP_WINDUP = 20;
	static final double STOMP_RADIUS = 6.0;

	private final HideoutBossLink link = new HideoutBossLink(this, HideoutFaction.KINGPIN, BossEvent.BossBarColor.YELLOW, this::busy);
	private boolean calledBoys;
	int moveTicks;
	byte move; // 0 none, 1 combo, 2 stomp

	public KingpinEnforcerBoss(EntityType<? extends KingpinEnforcerBoss> type, Level level) {
		super(type, level);
		this.xpReward = 120;
		setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 340.0)
				.add(Attributes.MOVEMENT_SPEED, 0.28)
				.add(Attributes.ATTACK_DAMAGE, 11.0)
				.add(Attributes.ATTACK_KNOCKBACK, 1.6)
				.add(Attributes.ARMOR, 12.0)
				.add(Attributes.ARMOR_TOUGHNESS, 4.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.9)
				.add(Attributes.FOLLOW_RANGE, 40.0)
				.add(Attributes.STEP_HEIGHT, 1.0)
				.add(Attributes.SCALE, SCALE);
	}

	@Override
	public HideoutBossLink hideoutLink() {
		return link;
	}

	boolean busy() {
		return move != 0;
	}

	@Override
	protected SyndicateSkin defaultSkin() {
		return SyndicateSkin.HIDEOUT_ENFORCER;
	}

	@Override
	public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason, SpawnGroupData data) {
		SpawnGroupData out = super.finalizeSpawn(level, difficulty, reason, data);
		setSkin(SyndicateSkin.HIDEOUT_ENFORCER);
		return out;
	}

	@Override
	protected void registerGoals() {
		super.registerGoals();
		// the shared threat table decides who he hits (BossThreat); vanilla's hurt-by retarget would fight it
		targetSelector.removeAllGoals(g -> g instanceof HurtByTargetGoal);
	}

	@Override
	protected void registerCombatGoals() {
		super.registerCombatGoals();
		goalSelector.addGoal(1, new MovesGoal(this));
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		boolean hurt = super.hurt(source, amount);
		if (hurt && level() instanceof ServerLevel server) {
			link.onHurt(source, amount);
			if (!calledBoys && getHealth() < getMaxHealth() * 0.5f) {
				calledBoys = true;
				callBoys(server);
			}
		}
		return hurt;
	}

	@Override
	protected void customServerAiStep() {
		super.customServerAiStep();
		if (level() instanceof ServerLevel server) {
			link.tick(server);
		}
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (level() instanceof ServerLevel server) {
			link.onDeath(server);
		}
	}

	@Override
	public void stopSeenByPlayer(net.minecraft.server.level.ServerPlayer player) {
		super.stopSeenByPlayer(player);
		link.stopSeen(player);
	}

	@Override
	public void remove(Entity.RemovalReason reason) {
		link.detach();
		super.remove(reason);
	}

	/** "Boys!" -- two thugs and a pistol gunman arrive around him. */
	void callBoys(ServerLevel level) {
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.PILLAGER_CELEBRATE, SoundSource.HOSTILE, 2.0f, 0.6f);
		HideoutGuards.Role[] roles = { HideoutGuards.Role.THUG, HideoutGuards.Role.THUG, HideoutGuards.Role.PISTOL };
		for (HideoutGuards.Role r : roles) {
			BlockPos spot = HideoutGuards.findSpot(level, blockPosition(), 3, 7, random);
			if (spot != null) {
				var m = HideoutGuards.spawn(level, r, spot, random.nextFloat() * 360f, 12);
				if (m != null && getTarget() != null) {
					m.setTarget(getTarget());
				}
				level.sendParticles(ParticleTypes.POOF, spot.getX() + 0.5, spot.getY() + 0.5, spot.getZ() + 0.5, 6, 0.3, 0.4, 0.3, 0.02);
			}
		}
	}

	private static boolean victim(LivingEntity e, LivingEntity self) {
		return e != self && e.isAlive() && !(e instanceof SyndicateCriminal) && !(e instanceof Player p && (p.isCreative() || p.isSpectator()));
	}

	/** Starts the Seismic Stomp now (test hook too). */
	public void startStomp() {
		move = 2;
		moveTicks = 0;
		setAction(ACTION_SLAM);
		getNavigation().stop();
	}

	/** Starts the Haymaker Combo now (test hook too). */
	public void startCombo() {
		move = 1;
		moveTicks = 0;
		getNavigation().stop();
	}

	void tickMove(ServerLevel level) {
		moveTicks++;
		LivingEntity t = getTarget();
		getNavigation().stop();
		if (t != null) {
			getLookControl().setLookAt(t, 40f, 40f);
		}
		if (move == 1) {
			if (t != null && distanceTo(t) > 2.0) {
				Vec3 to = t.position().subtract(position()).normalize().scale(0.18);
				setDeltaMovement(to.x, getDeltaMovement().y, to.z);
			}
			if (moveTicks == 6 || moveTicks == 16 || moveTicks == 28) {
				swing(moveTicks == 16 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
				boolean upper = moveTicks == 28;
				level.playSound(null, getX(), getY(), getZ(), upper ? SoundEvents.PLAYER_ATTACK_KNOCKBACK : SoundEvents.PLAYER_ATTACK_STRONG,
						SoundSource.HOSTILE, 1.2f, upper ? 0.6f : 0.8f);
				Vec3 fwd = getViewVector(1f);
				Vec3 hit = position().add(fwd.x * 1.8, 1.0, fwd.z * 1.8);
				for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(hit, hit).inflate(1.6, 1.4, 1.6),
						e -> victim(e, this))) {
					e.hurt(damageSources().mobAttack(this), upper ? 14.0f : 7.0f);
					if (upper) {
						e.push(fwd.x * 0.9, 0.95, fwd.z * 0.9);
						level.sendParticles(ParticleTypes.CRIT, e.getX(), e.getY() + 1, e.getZ(), 14, 0.3, 0.4, 0.3, 0.3);
					} else {
						e.push(fwd.x * 0.3, 0.1, fwd.z * 0.3);
					}
					e.hurtMarked = true;
				}
			}
			if (moveTicks >= 34) {
				endMove(100 + random.nextInt(40), 1);
			}
		} else if (move == 2) {
			BlockPos below = blockPosition().below();
			if (moveTicks < STOMP_WINDUP && moveTicks % 4 == 0) {
				for (int i = 0; i < 28; i++) {
					double a = i * Math.PI * 2 / 28;
					level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, getX() + Math.cos(a) * STOMP_RADIUS, getY() + 0.1,
							getZ() + Math.sin(a) * STOMP_RADIUS, 1, 0, 0.02, 0, 0.0);
				}
				level.playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_STEP, SoundSource.HOSTILE, 1.2f, 0.6f);
			}
			if (moveTicks == STOMP_WINDUP) {
				level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.4f, 0.7f);
				for (int r = 1; r <= (int) STOMP_RADIUS; r++) {
					for (int i = 0; i < 6 * r; i++) {
						double a = i * Math.PI * 2 / (6 * r);
						level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(below)), getX() + Math.cos(a) * r,
								getY() + 0.1, getZ() + Math.sin(a) * r, 2, 0.1, 0.1, 0.1, 0.15);
					}
				}
				for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(STOMP_RADIUS, 1.5, STOMP_RADIUS),
						e -> victim(e, this) && e.onGround())) {
					if (e.distanceToSqr(this) > STOMP_RADIUS * STOMP_RADIUS) {
						continue;
					}
					e.hurt(damageSources().mobAttack(this), 12.0f);
					Vec3 out = e.position().subtract(position());
					Vec3 h = new Vec3(out.x, 0, out.z).normalize();
					e.push(h.x * 0.8, 0.85, h.z * 0.8);
					e.hurtMarked = true;
				}
			}
			if (moveTicks >= STOMP_WINDUP + 12) {
				endMove(160 + random.nextInt(60), 2);
			}
		}
	}

	private int comboCooldown = 40;
	private int stompCooldown = 80;

	private void endMove(int cooldown, int which) {
		if (which == 1) {
			comboCooldown = cooldown;
		} else {
			stompCooldown = cooldown;
		}
		move = 0;
		setAction(ACTION_NONE);
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		link.save(tag);
		tag.putBoolean("CalledBoys", calledBoys);
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		link.load(tag);
		calledBoys = tag.getBoolean("CalledBoys");
	}

	/** The combo and the stomp, as one goal above the charge so they never overlap. */
	static final class MovesGoal extends Goal {
		private final KingpinEnforcerBoss mob;

		MovesGoal(KingpinEnforcerBoss mob) {
			this.mob = mob;
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
		}

		@Override
		public boolean canUse() {
			if (mob.comboCooldown > 0) {
				mob.comboCooldown--;
			}
			if (mob.stompCooldown > 0) {
				mob.stompCooldown--;
			}
			if (mob.move != 0) {
				return true;
			}
			LivingEntity t = mob.getTarget();
			if (t == null || !t.isAlive() || !mob.onGround()) {
				return false;
			}
			double d = mob.distanceTo(t);
			if (mob.stompCooldown <= 0 && d < STOMP_RADIUS && t.onGround() && mob.random.nextInt(3) == 0) {
				mob.startStomp();
				return true;
			}
			if (mob.comboCooldown <= 0 && d < 3.5) {
				mob.startCombo();
				return true;
			}
			return false;
		}

		@Override
		public boolean canContinueToUse() {
			return mob.move != 0 && mob.isAlive();
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			if (mob.level() instanceof ServerLevel level) {
				mob.tickMove(level);
			}
		}

		@Override
		public void stop() {
			if (mob.move != 0) {
				mob.endMove(60, mob.move);
			}
		}
	}
}
