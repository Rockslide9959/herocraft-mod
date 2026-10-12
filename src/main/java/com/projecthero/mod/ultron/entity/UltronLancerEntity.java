package com.projecthero.mod.ultron.entity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.projecthero.mod.ultron.UltronCombat;
import com.projecthero.mod.ultron.UltronConfig;
import com.projecthero.mod.ultron.UltronFx;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.1: an <b>Ultron Lancer</b> -- the shield wall. A heavy silver-and-red frame (90 health, 8 armour) carrying a
 * <b>front energy shield</b> (a translucent red hex plane): projectiles and beams that come at it from the front are
 * stopped dead. Flank it, or hit it from above; melee still lands. Lancers advance shoulder to shoulder in a line and
 * lunge with an energy lance (10 damage, reach 4) -- and the shield drops for 2 s after every lunge: that's the opening.
 */
public class UltronLancerEntity extends UltronRobot {
	public static final byte ACTION_WINDUP = 2;
	public static final byte ACTION_LUNGE = 3;
	private static final EntityDataAccessor<Boolean> DATA_SHIELD = SynchedEntityData.defineId(UltronLancerEntity.class, EntityDataSerializers.BOOLEAN);

	private int lungeCooldown = 30;
	private int windup;
	private int lungeShow;
	private int shieldDown;
	private int lineSlot;
	private int lineSize = 1;

	public UltronLancerEntity(EntityType<? extends UltronLancerEntity> type, Level level) {
		super(type, level);
		this.xpReward = 14;
	}

	public static AttributeSupplier.Builder createAttributes() {
		UltronConfig.Lancer cfg = UltronConfig.lancer();
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, cfg.health)
				.add(Attributes.ARMOR, cfg.armor)
				.add(Attributes.ARMOR_TOUGHNESS, 2.0)
				.add(Attributes.ATTACK_DAMAGE, cfg.lanceDamage)
				.add(Attributes.MOVEMENT_SPEED, cfg.speed)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.6)
				.add(Attributes.FOLLOW_RANGE, 40.0)
				.add(Attributes.STEP_HEIGHT, 1.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_SHIELD, true);
	}

	@Override
	public UltronSkin skin() {
		return UltronSkin.LANCER;
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 16.0f));
		goalSelector.addGoal(9, new RandomLookAroundGoal(this));
		targetSelector.addGoal(1, new HurtByTargetGoal(this, UltronRobot.class));
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
		targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, IronGolem.class, true));
	}

	/** Is the front shield up (synced: the renderer draws it)? */
	public boolean shieldUp() {
		return entityData.get(DATA_SHIELD);
	}

	/** Test hook: drop the shield for {@code ticks} as a lunge would. */
	public void dropShield(int ticks) {
		shieldDown = Math.max(shieldDown, ticks);
		entityData.set(DATA_SHIELD, false);
	}

	@Override
	protected void customServerAiStep() {
		super.customServerAiStep();
		if (!(level() instanceof ServerLevel server)) {
			return;
		}
		UltronConfig.Lancer cfg = UltronConfig.lancer();
		if (shieldDown > 0) {
			shieldDown--;
		}
		if (lungeCooldown > 0) {
			lungeCooldown--;
		}
		if (lungeShow > 0 && --lungeShow == 0) {
			setAction(ACTION_NONE);
		}
		boolean up = shieldDown == 0 && !isStunned();
		if (entityData.get(DATA_SHIELD) != up) {
			entityData.set(DATA_SHIELD, up);
			if (up) {
				server.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 0.6f, 1.8f);
			}
		}
		if (isStunned()) {
			windup = 0;
			return;
		}
		LivingEntity target = getTarget();
		if (target == null || !UltronCombat.canTarget(target)) {
			windup = 0;
			if (target != null) {
				setTarget(null);
			}
			if (tickCount % 20 == 0) {
				Player p = UltronCombat.nearestPlayer(server, position(), 40);
				if (p != null) {
					setTarget(p);
				}
			}
			return;
		}
		if (windup > 0) {
			tickWindup(server, target, cfg);
			return;
		}
		double d = distanceTo(target);
		getLookControl().setLookAt(target, 30f, 30f);
		if (d <= cfg.lanceReach && lungeCooldown <= 0 && hasLineOfSight(target)) {
			windup = 1;
			setAction(ACTION_WINDUP);
			getNavigation().stop();
			server.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 1.0f, 1.9f);
			return;
		}
		advance(server, target, d);
	}

	/** Shoulder to shoulder: every Lancer near takes a slot in a line across the line of advance. */
	private void advance(ServerLevel server, LivingEntity target, double d) {
		if (tickCount % 10 == 0) {
			List<UltronLancerEntity> line = new ArrayList<>(server.getEntitiesOfClass(UltronLancerEntity.class, getBoundingBox().inflate(16),
					l -> l.isAlive() && l.getTarget() == target));
			line.sort(Comparator.comparing(Entity::getUUID));
			lineSize = Math.max(1, line.size());
			lineSlot = Math.max(0, line.indexOf(this));
			Vec3 to = target.position().subtract(position());
			Vec3 flat = new Vec3(to.x, 0, to.z);
			Vec3 side = flat.lengthSqr() < 1.0e-4 ? Vec3.ZERO : new Vec3(-flat.z, 0, flat.x).normalize();
			double offset = (lineSlot - (lineSize - 1) / 2.0) * 2.2;
			Vec3 goal = d > 6.0 ? target.position().add(side.scale(offset)) : target.position();
			getNavigation().moveTo(goal.x, goal.y, goal.z, 1.0);
		}
	}

	private void tickWindup(ServerLevel server, LivingEntity target, UltronConfig.Lancer cfg) {
		windup++;
		getNavigation().stop();
		getLookControl().setLookAt(target, 40f, 40f);
		faceTowards(target);
		Vec3 tip = lanceTip();
		server.sendParticles(UltronFx.RED_SMALL, tip.x, tip.y, tip.z, 2, 0.05, 0.05, 0.05, 0.0);
		if (windup < cfg.lungeWindupTicks) {
			return;
		}
		windup = 0;
		lunge(server, cfg);
	}

	private void faceTowards(Entity target) {
		float yaw = (float) (Math.atan2(target.getZ() - getZ(), target.getX() - getX()) * (180.0 / Math.PI)) - 90.0f;
		setYRot(yaw);
		yBodyRot = yaw;
		yHeadRot = yaw;
	}

	/** The lunge: a short dash and the lance's thrust -- everything in front within reach takes the hit. */
	public void lunge(ServerLevel server, UltronConfig.Lancer cfg) {
		Vec3 fwd = Vec3.directionFromRotation(0, yBodyRot);
		setDeltaMovement(fwd.scale(0.7).add(0, 0.1, 0));
		hurtMarked = true;
		setAction(ACTION_LUNGE);
		lungeShow = 8;
		Vec3 base = position().add(0, getBbHeight() * 0.55, 0);
		for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(cfg.lanceReach + 1.0), UltronCombat::canTarget)) {
			Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(base);
			double dist = to.length();
			if (dist > cfg.lanceReach + e.getBbWidth() * 0.5 || dist < 1.0e-3) {
				continue;
			}
			if (to.normalize().dot(fwd) < 0.55) {
				continue;
			}
			UltronCombat.hit(server, this, e, cfg.lanceDamage);
			e.push(fwd.x * 0.6, 0.25, fwd.z * 0.6);
			e.hurtMarked = true;
		}
		Vec3 tip = base.add(fwd.scale(cfg.lanceReach));
		UltronFx.beam(server, base.add(fwd.scale(0.6)), tip, UltronFx.STREAK, 4);
		server.sendParticles(ParticleTypes.ELECTRIC_SPARK, tip.x, tip.y, tip.z, 8, 0.2, 0.2, 0.2, 0.2);
		server.playSound(null, getX(), getY(), getZ(), SoundEvents.TRIDENT_THROW.value(), SoundSource.HOSTILE, 1.2f, 0.6f);
		server.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 0.8f, 1.6f);
		shieldDown = cfg.shieldDownTicks;
		lungeCooldown = cfg.lungeCooldownTicks + random.nextInt(20);
		entityData.set(DATA_SHIELD, false);
	}

	/** Where the lance's point is (just past the right hand). */
	public Vec3 lanceTip() {
		Vec3 fwd = Vec3.directionFromRotation(0, yBodyRot);
		Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
		return position().add(0, getBbHeight() * 0.55, 0).add(right.scale(0.35)).add(fwd.scale(1.6));
	}

	// ---------------------------------------------------------------- the shield

	/**
	 * Would the front shield stop {@code source}? Only while it is up; only a ranged hit (a projectile, an explosion, or
	 * an attacker standing beyond arm's reach -- every beam); only from the front ({@link UltronConfig.Lancer#shieldHalfAngle}
	 * either side of where its body faces); and never from steeply above.
	 */
	public boolean blocks(DamageSource source) {
		if (!shieldUp() || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || source.is(DamageTypeTags.BYPASSES_SHIELD)) {
			return false;
		}
		Vec3 from = source.getSourcePosition();
		if (from == null) {
			return false;
		}
		Entity attacker = source.getEntity();
		Entity direct = source.getDirectEntity();
		boolean projectile = source.is(DamageTypeTags.IS_PROJECTILE) || direct instanceof Projectile || source.is(DamageTypeTags.IS_EXPLOSION);
		boolean ranged = projectile || attacker != null && attacker.distanceTo(this) > 4.5;
		if (!ranged) {
			return false; // a melee hit lands
		}
		Vec3 centre = position().add(0, getBbHeight() * 0.5, 0);
		Vec3 to = from.subtract(centre);
		double horiz = Math.sqrt(to.x * to.x + to.z * to.z);
		if (to.y > horiz) {
			return false; // from above: over the top of the shield
		}
		if (horiz < 1.0e-3) {
			return false;
		}
		Vec3 fwd = Vec3.directionFromRotation(0, yBodyRot);
		double cos = (fwd.x * to.x + fwd.z * to.z) / horiz;
		return cos >= Math.cos(Math.toRadians(UltronConfig.lancer().shieldHalfAngle));
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (!level().isClientSide() && blocks(source)) {
			if (level() instanceof ServerLevel server) {
				Vec3 fwd = Vec3.directionFromRotation(0, yBodyRot);
				Vec3 at = position().add(0, getBbHeight() * 0.55, 0).add(fwd.scale(0.7));
				server.sendParticles(UltronFx.RED, at.x, at.y, at.z, 10, 0.35, 0.45, 0.35, 0.02);
				server.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 6, 0.3, 0.4, 0.3, 0.15);
				server.playSound(null, getX(), getY(), getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 1.0f, 0.7f);
			}
			return false;
		}
		return super.hurt(source, amount);
	}
}
