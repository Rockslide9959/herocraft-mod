package com.projecthero.mod.ultron.entity;

import java.util.UUID;

import com.projecthero.mod.ultron.UltronCombat;
import com.projecthero.mod.ultron.UltronConfig;
import com.projecthero.mod.ultron.UltronFx;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.15.12: an <b>Ultron Sniper Frame</b> -- 40 health, perched on top of a relay pylon (or standing off at the arena's
 * edge). It paints its target with a thin red laser sight for 1.5 seconds, then fires a 14-damage shot down the line.
 * Like the Syndicate's sniper: break its line of sight while the laser is on you. Knock out its pylon and it drops.
 */
public class UltronSniperEntity extends UltronRobot {
	private UUID perch;
	private int aiming;
	private int cooldown = 30;
	private int blind;

	public UltronSniperEntity(EntityType<? extends UltronSniperEntity> type, Level level) {
		super(type, level);
		this.xpReward = 10;
	}

	public static AttributeSupplier.Builder createAttributes() {
		UltronConfig.Sniper cfg = UltronConfig.sniper();
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, cfg.health)
				.add(Attributes.ARMOR, cfg.armor)
				.add(Attributes.ATTACK_DAMAGE, 3.0)
				.add(Attributes.MOVEMENT_SPEED, 0.24)
				.add(Attributes.FOLLOW_RANGE, 64.0);
	}

	@Override
	public UltronSkin skin() {
		return UltronSkin.SNIPER;
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 24.0f));
		targetSelector.addGoal(1, new HurtByTargetGoal(this, UltronRobot.class));
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
	}

	/** Perches on top of {@code pylon} (no gravity while it stands). */
	public void perchOn(UltronPylonEntity pylon) {
		this.perch = pylon.getUUID();
		setNoGravity(true);
		moveTo(pylon.getX(), pylon.getY() + pylon.getBbHeight(), pylon.getZ(), getYRot(), 0f);
	}

	public boolean isPerched() {
		return perch != null;
	}

	@Override
	protected void customServerAiStep() {
		super.customServerAiStep();
		if (!(level() instanceof ServerLevel server)) {
			return;
		}
		if (perch != null) {
			Entity p = server.getEntity(perch);
			if (p instanceof UltronPylonEntity pylon && pylon.isAlive()) {
				setPos(pylon.getX(), pylon.getY() + pylon.getBbHeight(), pylon.getZ());
				setDeltaMovement(Vec3.ZERO);
			} else {
				perch = null;
				setNoGravity(false);
			}
		}
		if (isStunned()) {
			aiming = 0;
			setAction(ACTION_NONE);
			return;
		}
		LivingEntity target = getTarget();
		if (target == null || !target.isAlive() || !UltronCombat.canTarget(target)) {
			if (target != null) {
				setTarget(null);
			}
			if (tickCount % 20 == 0) {
				Player p = UltronCombat.nearestPlayer(server, position(), UltronConfig.sniper().range);
				if (p != null) {
					setTarget(p);
				}
			}
			aiming = 0;
			setAction(ACTION_NONE);
			return;
		}
		UltronConfig.Sniper cfg = UltronConfig.sniper();
		getLookControl().setLookAt(target, 30f, 30f);
		boolean sees = hasLineOfSight(target);
		blind = sees ? 0 : blind + 1;
		if (perch == null) {
			if (blind > 100) {
				getNavigation().moveTo(target, 1.0);
			} else {
				getNavigation().stop();
			}
		}
		if (cooldown > 0) {
			cooldown--;
			return;
		}
		if (!sees || distanceTo(target) > cfg.range) {
			if (aiming > 0) {
				aiming = 0;
				setAction(ACTION_NONE);
			}
			return;
		}
		setAction(ACTION_AIM);
		aiming++;
		Vec3 from = muzzle();
		Vec3 aimAt = target.position().add(0, target.getBbHeight() * 0.6, 0);
		if (aiming % 2 == 1) {
			UltronFx.beam(server, from, aimAt, UltronFx.LASER_SIGHT, 3);
		}
		if (aiming == 1) {
			server.playSound(null, getX(), getY(), getZ(), SoundEvents.SPYGLASS_USE, SoundSource.HOSTILE, 1.4f, 0.6f);
		}
		if (aiming < cfg.aimTicks) {
			return;
		}
		UltronCombat.hitscan(server, this, from, aimAt.subtract(from), cfg.range + 4, cfg.shotDamage, UltronFx.SNIPER_SHOT, 8, 0.3f);
		server.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 1.6f, 0.6f);
		server.playSound(null, getX(), getY(), getZ(), SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.HOSTILE, 1.0f, 0.5f);
		aiming = 0;
		setAction(ACTION_NONE);
		cooldown = cfg.cooldownTicks + random.nextInt(20);
	}

	/** Where the shot leaves: just in front of the right shoulder at eye height. */
	public Vec3 muzzle() {
		Vec3 look = getViewVector(1.0f);
		Vec3 right = new Vec3(-look.z, 0, look.x).normalize();
		return getEyePosition().add(look.scale(0.6)).add(right.scale(0.3)).add(0, -0.25, 0);
	}

	@Override
	public boolean isPushable() {
		return perch == null && super.isPushable();
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		if (perch != null) {
			tag.putUUID("Perch", perch);
		}
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		perch = tag.hasUUID("Perch") ? tag.getUUID("Perch") : null;
		setNoGravity(perch != null);
	}
}
