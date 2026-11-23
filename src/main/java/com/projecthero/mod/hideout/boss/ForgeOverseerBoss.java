package com.projecthero.mod.hideout.boss;

import java.util.List;

import com.projecthero.mod.hideout.HideoutBoss;
import com.projecthero.mod.hideout.HideoutBossLink;
import com.projecthero.mod.hideout.HideoutFaction;
import com.projecthero.mod.hideout.HideoutGuards;
import com.projecthero.mod.ultron.UltronCombat;
import com.projecthero.mod.ultron.UltronFx;
import com.projecthero.mod.ultron.entity.UltronDroneEntity;
import com.projecthero.mod.ultron.entity.UltronHeavyEntity;
import com.projecthero.mod.ultron.entity.UltronSkin;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.5: the <b>Forge Overseer</b> -- the Ultron Forge mini-boss, a graphite-and-gold Ultron unit that runs the
 * assembly line (x1.7, 420 health, 12 armour; every Ultron hit is x1.3 as usual). It keeps the Heavy's <b>Missile
 * Barrage</b> (homing red missiles) and <b>Stamp</b> (close shockwave) and adds:
 * <ul>
 * <li><b>Overload Beam</b> (6-28 blocks, line of sight): a red telegraph line for a second, then a beam that follows its
 * target slowly for 1.5 s -- 4 damage every 5 ticks to anything on the line. Break line of sight.</li>
 * <li><b>Deploy Drones</b>: two Ultron Drones launch from the forge (never more than four of its drones at once).</li>
 * </ul>
 * Shared threat table, own boss bar (see {@link HideoutBossLink}).
 */
public class ForgeOverseerBoss extends UltronHeavyEntity implements HideoutBoss {
	public static final float SCALE = 1.7f;
	static final int BEAM_WINDUP = 20;
	static final int BEAM_LENGTH = 30;
	static final int DEPLOY_LENGTH = 24;
	static final byte MOVE_NONE = 0;
	static final byte MOVE_BEAM = 1;
	static final byte MOVE_DEPLOY = 2;

	private final HideoutBossLink link = new HideoutBossLink(this, HideoutFaction.ULTRON, BossEvent.BossBarColor.RED, this::busy);
	/** v0.17.7: the second phase (see HideoutPhase). */
	final HideoutPhase phase = new HideoutPhase(this, HideoutFaction.ULTRON, HideoutPhase.Kind.PLASMA_MINES, 0.5f, 160);
	private byte move = MOVE_NONE;
	private int moveTicks;
	private int beamCooldown = 60;
	private int deployCooldown = 120;
	private Vec3 beamDir = Vec3.ZERO;

	public ForgeOverseerBoss(EntityType<? extends ForgeOverseerBoss> type, Level level) {
		super(type, level);
		this.xpReward = 120;
		setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 420.0)
				.add(Attributes.ARMOR, 12.0)
				.add(Attributes.ARMOR_TOUGHNESS, 5.0)
				.add(Attributes.ATTACK_DAMAGE, 10.0)
				.add(Attributes.ATTACK_KNOCKBACK, 1.2)
				.add(Attributes.MOVEMENT_SPEED, 0.22)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.9)
				.add(Attributes.FOLLOW_RANGE, 40.0)
				.add(Attributes.STEP_HEIGHT, 1.0)
				.add(Attributes.SCALE, SCALE);
	}

	@Override
	public HideoutBossLink hideoutLink() {
		return link;
	}

	/** v0.17.7: the second phase (test hook). */
	public HideoutPhase phase() {
		return phase;
	}

	@Override
	public UltronSkin skin() {
		return UltronSkin.OVERSEER;
	}

	boolean busy() {
		return move != MOVE_NONE || action() != ACTION_NONE;
	}

	@Override
	protected void registerGoals() {
		super.registerGoals();
		targetSelector.removeAllGoals(g -> g instanceof HurtByTargetGoal);
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		boolean hurt = super.hurt(source, amount);
		if (hurt && !level().isClientSide()) {
			link.onHurt(source, amount);
			phase.hurtCheck((ServerLevel) level());
		}
		return hurt;
	}

	@Override
	protected void customServerAiStep() {
		if (!(level() instanceof ServerLevel server)) {
			super.customServerAiStep();
			return;
		}
		link.tick(server);
		phase.tick(server, busy() || isStunned());
		if (isStunned()) {
			return;
		}
		if (beamCooldown > 0) {
			beamCooldown--;
		}
		if (deployCooldown > 0) {
			deployCooldown--;
		}
		if (move != MOVE_NONE) {
			tickMove(server);
			return; // the Heavy's own moves wait while one of ours runs
		}
		super.customServerAiStep();
		if (action() != ACTION_NONE) {
			return; // a barrage or stamp is running
		}
		LivingEntity t = getTarget();
		if (t == null || !t.isAlive()) {
			return;
		}
		double d = distanceTo(t);
		if (deployCooldown <= 0 && myDrones(server).size() < 3) {
			startDeploy();
		} else if (beamCooldown <= 0 && d > 6 && d < 28 && hasLineOfSight(t)) {
			startBeam();
		}
	}

	private List<UltronDroneEntity> myDrones(ServerLevel level) {
		return level.getEntitiesOfClass(UltronDroneEntity.class, getBoundingBox().inflate(32), Entity::isAlive);
	}

	/** Starts the Overload Beam now (test hook too). */
	public void startBeam() {
		move = MOVE_BEAM;
		moveTicks = 0;
		setAction(ACTION_AIM);
		getNavigation().stop();
		LivingEntity t = getTarget();
		beamDir = t != null ? t.getEyePosition().subtract(eye()).normalize() : getViewVector(1f);
		level().playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 1.6f, 0.6f);
	}

	/** Starts Deploy Drones now (test hook too). */
	public void startDeploy() {
		move = MOVE_DEPLOY;
		moveTicks = 0;
		setAction(ACTION_BARRAGE);
		getNavigation().stop();
		level().playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 1.6f, 1.4f);
	}

	private Vec3 eye() {
		return position().add(0, getBbHeight() * 0.88, 0);
	}

	private void tickMove(ServerLevel level) {
		moveTicks++;
		getNavigation().stop();
		LivingEntity t = getTarget();
		if (t != null) {
			getLookControl().setLookAt(t, 20f, 20f);
		}
		if (move == MOVE_BEAM) {
			Vec3 from = eye();
			if (t != null) {
				// the beam follows, slowly: 4 degrees a tick at most
				Vec3 want = t.position().add(0, t.getBbHeight() * 0.5, 0).subtract(from).normalize();
				double cos = Math.max(-1, Math.min(1, beamDir.dot(want)));
				double ang = Math.acos(cos);
				double max = Math.toRadians(moveTicks <= BEAM_WINDUP ? 6 : 4);
				beamDir = ang <= max ? want : beamDir.scale(Math.sin(ang - max)).add(want.scale(Math.sin(max))).normalize();
			}
			Vec3 far = from.add(beamDir.scale(30));
			var clip = level.clip(new net.minecraft.world.level.ClipContext(from, far, net.minecraft.world.level.ClipContext.Block.COLLIDER,
					net.minecraft.world.level.ClipContext.Fluid.NONE, this));
			Vec3 to = clip.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? far : clip.getLocation();
			if (moveTicks > BEAM_WINDUP) {
				com.projecthero.mod.beamclash.BeamClash.report(this, from, beamDir, 30, 1.2f, 0xFF2018); // every live tick
			}
			if (moveTicks <= BEAM_WINDUP) {
				if (moveTicks % 4 == 0) {
					UltronFx.beam(level, from, to, UltronFx.TELEGRAPH, 5);
				}
			} else if ((moveTicks - BEAM_WINDUP) % 5 == 0) {
				UltronFx.beam(level, from, to, UltronFx.ENCEPHALO, 6);
				for (LivingEntity e : UltronCombat.alongLine(level, from, to, 0.8)) {
					if (e != this && UltronCombat.canTarget(e)) {
						UltronCombat.hit(level, this, e, 4.0f);
					}
				}
				level.playSound(null, getX(), getY(), getZ(), SoundEvents.GUARDIAN_ATTACK, SoundSource.HOSTILE, 1.0f, 1.6f);
			}
			if (moveTicks >= BEAM_WINDUP + BEAM_LENGTH) {
				end();
				beamCooldown = 140 + random.nextInt(60);
			}
		} else if (move == MOVE_DEPLOY) {
			if (moveTicks == 12 || moveTicks == 20) {
				BlockPos spot = HideoutGuards.findSpot(level, blockPosition(), 2, 5, random);
				if (spot != null) {
					var drone = HideoutGuards.spawn(level, HideoutGuards.Role.DRONE, spot.above(2), random.nextFloat() * 360f, 0);
					if (drone != null) {
						drone.setTarget(t);
						level.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, drone.getX(), drone.getY(), drone.getZ(), 20,
								0.4, 0.4, 0.4, 0.2);
						UltronFx.beam(level, eye(), drone.position(), UltronFx.TETHER, 8);
					}
				}
				level.playSound(null, getX(), getY(), getZ(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.HOSTILE, 1.2f, 0.6f);
			}
			if (moveTicks >= DEPLOY_LENGTH) {
				end();
				deployCooldown = 360 + random.nextInt(120);
			}
		}
	}

	private void end() {
		move = MOVE_NONE;
		setAction(ACTION_NONE);
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

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		link.save(tag);
		phase.save(tag);
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		link.load(tag);
		phase.load(tag);
	}
}
