package com.projecthero.mod.ultron.entity;

import com.projecthero.mod.ironman.entity.IronManMissileEntity;
import com.projecthero.mod.ultron.UltronCombat;
import com.projecthero.mod.ultron.UltronConfig;
import com.projecthero.mod.ultron.UltronFx;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * v0.15.12: an <b>Ultron Heavy</b> -- the walking artillery. Big (x1.3), slow, armoured (120 health, 10 armour). At
 * range it raises both arms (a one-second telegraph: shoulder sparks and a rising whine) and fires a barrage of three
 * red missiles that home on its target; up close it stamps the ground (a 4-block shockwave that throws you up).
 */
public class UltronHeavyEntity extends UltronRobot {
	public static final float SCALE = 1.3f;
	public static final byte ACTION_BARRAGE = 2;
	public static final byte ACTION_STOMP = 3;
	static final int BARRAGE_WINDUP = 20;
	static final int STOMP_WINDUP = 12;

	private int barrageCooldown = 80;
	private int stompCooldown = 40;
	private int windup;
	private int volley = -1;
	private byte move = ACTION_NONE;

	public UltronHeavyEntity(EntityType<? extends UltronHeavyEntity> type, Level level) {
		super(type, level);
		this.xpReward = 20;
		refreshDimensions(); // the SCALE attribute sizes its hit-box
	}

	public static AttributeSupplier.Builder createAttributes() {
		UltronConfig.Heavy cfg = UltronConfig.heavy();
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, cfg.health)
				.add(Attributes.ARMOR, cfg.armor)
				.add(Attributes.ARMOR_TOUGHNESS, 4.0)
				.add(Attributes.ATTACK_DAMAGE, cfg.meleeDamage)
				.add(Attributes.ATTACK_KNOCKBACK, 1.0)
				.add(Attributes.MOVEMENT_SPEED, cfg.speed)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.8)
				.add(Attributes.FOLLOW_RANGE, 40.0)
				.add(Attributes.STEP_HEIGHT, 1.0)
				.add(Attributes.SCALE, SCALE);
	}

	@Override
	public UltronSkin skin() {
		return UltronSkin.HEAVY;
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.0, true));
		goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.8));
		goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 16.0f));
		goalSelector.addGoal(9, new RandomLookAroundGoal(this));
		targetSelector.addGoal(1, new HurtByTargetGoal(this, UltronRobot.class));
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
		targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, IronGolem.class, true));
	}

	@Override
	public boolean doHurtTarget(Entity target) {
		boolean hit = super.doHurtTarget(target);
		if (hit && target instanceof ServerPlayer sp) {
			UltronCombat.drainSuit(sp, (float) getAttributeValue(Attributes.ATTACK_DAMAGE));
		}
		return hit;
	}

	@Override
	protected void customServerAiStep() {
		super.customServerAiStep();
		if (!(level() instanceof ServerLevel server) || isStunned()) {
			return;
		}
		LivingEntity target = getTarget();
		if (barrageCooldown > 0) {
			barrageCooldown--;
		}
		if (stompCooldown > 0) {
			stompCooldown--;
		}
		if (move == ACTION_BARRAGE) {
			tickBarrage(server, target);
			return;
		}
		if (move == ACTION_STOMP) {
			tickStomp(server);
			return;
		}
		if (target == null || !target.isAlive()) {
			return;
		}
		double d = distanceTo(target);
		if (d < 3.5 && stompCooldown <= 0) {
			startStomp();
		} else if (d > 6 && d < 32 && barrageCooldown <= 0 && hasLineOfSight(target)) {
			startBarrage(server);
		}
	}

	/** Starts the barrage's telegraph now (also a test hook). */
	public void startBarrage(ServerLevel server) {
		move = ACTION_BARRAGE;
		windup = 0;
		volley = -1;
		setAction(ACTION_BARRAGE);
		getNavigation().stop();
		server.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 1.4f, 1.6f);
	}

	private void tickBarrage(ServerLevel server, LivingEntity target) {
		getNavigation().stop();
		windup++;
		if (target != null) {
			getLookControl().setLookAt(target, 30f, 30f);
		}
		if (windup <= BARRAGE_WINDUP) {
			for (Vec3 s : shoulders()) {
				server.sendParticles(UltronFx.RED_SMALL, s.x, s.y, s.z, 2, 0.08, 0.08, 0.08, 0.01);
			}
			if (windup % 5 == 0) {
				server.playSound(null, getX(), getY(), getZ(), SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.HOSTILE, 1.0f, 0.8f + windup * 0.05f);
			}
			return;
		}
		int shot = (windup - BARRAGE_WINDUP - 1) / 5;
		if ((windup - BARRAGE_WINDUP - 1) % 5 == 0 && shot < 3) {
			fireMissile(server, target, shot);
		}
		if (shot >= 3) {
			move = ACTION_NONE;
			setAction(ACTION_NONE);
			barrageCooldown = UltronConfig.heavy().barrageCooldownTicks + random.nextInt(40);
		}
	}

	private Vec3[] shoulders() {
		float yawRad = yBodyRot * ((float) Math.PI / 180f);
		Vec3 right = new Vec3(-Math.cos(yawRad), 0, -Math.sin(yawRad));
		Vec3 top = position().add(0, getBbHeight() * 0.86, 0);
		return new Vec3[] { top.add(right.scale(0.45 * SCALE)), top.add(right.scale(-0.45 * SCALE)) };
	}

	private void fireMissile(ServerLevel server, LivingEntity target, int index) {
		UltronConfig.Heavy cfg = UltronConfig.heavy();
		Vec3 from = shoulders()[index % 2];
		Vec3 dir;
		if (target != null) {
			dir = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(from).normalize();
		} else {
			dir = getViewVector(1.0f);
		}
		dir = dir.add(0, 0.35, 0).add(random.nextGaussian() * 0.12, 0, random.nextGaussian() * 0.12).normalize();
		IronManMissileEntity missile = new IronManMissileEntity(server, this, dir.scale(0.9)).withDamage(cfg.rocketDamage, cfg.rocketSplash)
				.withBlastRadius(1.4f).withRedTint();
		if (target != null) {
			missile.withTarget(target);
		}
		missile.setPos(from.x + dir.x * 0.6, from.y + dir.y * 0.6, from.z + dir.z * 0.6);
		server.addFreshEntity(missile);
		server.playSound(null, from.x, from.y, from.z, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.HOSTILE, 1.2f, 0.7f);
	}

	private void startStomp() {
		move = ACTION_STOMP;
		windup = 0;
		setAction(ACTION_STOMP);
		getNavigation().stop();
	}

	private void tickStomp(ServerLevel server) {
		getNavigation().stop();
		if (++windup < STOMP_WINDUP) {
			return;
		}
		UltronConfig.Heavy cfg = UltronConfig.heavy();
		Vec3 c = position();
		BlockState ground = server.getBlockState(blockPosition().below());
		if (!ground.isAir()) {
			server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), c.x, c.y + 0.1, c.z, 40, cfg.stompRadius * 0.5, 0.1,
					cfg.stompRadius * 0.5, 0.2);
		}
		UltronFx.ring(server, UltronFx.RED, c.add(0, 0.2, 0), cfg.stompRadius, 24);
		server.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.0f, 0.6f);
		for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(cfg.stompRadius, 1.5, cfg.stompRadius),
				UltronCombat::canTarget)) {
			if (e.distanceToSqr(c) <= cfg.stompRadius * cfg.stompRadius && e.onGround()) {
				UltronCombat.hit(server, this, e, cfg.stompDamage);
				Vec3 push = e.position().subtract(c).normalize().scale(0.7);
				e.push(push.x, 0.65, push.z);
				e.hurtMarked = true;
			}
		}
		move = ACTION_NONE;
		setAction(ACTION_NONE);
		stompCooldown = 80 + random.nextInt(40);
	}
}
