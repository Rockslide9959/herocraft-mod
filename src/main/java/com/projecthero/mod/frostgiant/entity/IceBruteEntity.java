package com.projecthero.mod.frostgiant.entity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.projecthero.mod.frostgiant.FrostCombat;
import com.projecthero.mod.frostgiant.FrostFx;
import com.projecthero.mod.frostgiant.FrostGiantAnims;
import com.projecthero.mod.frostgiant.FrostGiantAnims.Clip;
import com.projecthero.mod.frostgiant.FrostGiantAssault;
import com.projecthero.mod.frostgiant.FrostGiantConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.1: an <b>Ice Brute</b> -- 7 blocks of hulking (but upright) Jotun with ice gauntlets. The <b>brazier smasher</b>:
 * it goes for a lit Rune Brazier unless a fighter is right on top of it.
 * <ul>
 *   <li><b>Charge</b>: 1.2 s pawing the ground, then a straight rush -- 16 damage and a launch to whatever it hits.</li>
 *   <li><b>Glacier Spikes</b>: both fists slam down and a line of ice spikes erupts toward the target (cracking frost
 *       shows the line for a second first), 12 damage each.</li>
 *   <li>Brazier smash / melee: 14 to a fighter, 40 to a brazier.</li>
 * </ul>
 * Fire hurts it half again as much (every giant's rule).
 */
public class IceBruteEntity extends FrostGiantEntity {
	private int chargeCooldown = 60;
	private int spikeCooldown = 100;
	private Vec3 lockedDir = Vec3.ZERO;
	private Vec3 lockedFrom = Vec3.ZERO;
	private int chargeTicks;
	private final Set<UUID> chargeHit = new HashSet<>();

	public IceBruteEntity(EntityType<? extends IceBruteEntity> type, Level level) {
		super(type, level);
		this.xpReward = 40;
	}

	public static AttributeSupplier.Builder createAttributes() {
		FrostGiantConfig.Brute cfg = FrostGiantConfig.brute();
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, cfg.health)
				.add(Attributes.ARMOR, cfg.armor)
				.add(Attributes.ATTACK_DAMAGE, cfg.meleeDamage)
				.add(Attributes.MOVEMENT_SPEED, cfg.speed)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.FOLLOW_RANGE, 64.0)
				.add(Attributes.STEP_HEIGHT, 2.0);
	}

	@Override
	public String model() {
		return FrostGiantAnims.BRUTE;
	}

	@Override
	protected boolean huntsBraziers() {
		return true;
	}

	@Override
	protected double brazierIgnoreRange() {
		return 4.0;
	}

	@Override
	protected Clip idleClip() {
		return FrostGiantAnims.BRUTE_IDLE;
	}

	@Override
	protected Clip walkClip() {
		return FrostGiantAnims.BRUTE_WALK;
	}

	@Override
	protected Clip runClip() {
		return FrostGiantAnims.BRUTE_RUN;
	}

	@Override
	protected Clip deathClip() {
		return FrostGiantAnims.BRUTE_DEATH;
	}

	@Override
	protected Clip hurtClip() {
		return FrostGiantAnims.BRUTE_HURT;
	}

	@Override
	protected void tickCombat(ServerLevel level, LivingEntity target) {
		FrostGiantConfig.Brute cfg = FrostGiantConfig.brute();
		if (chargeCooldown > 0) {
			chargeCooldown--;
		}
		if (spikeCooldown > 0) {
			spikeCooldown--;
		}
		if (move != null) {
			tickMove(level, target, cfg);
			return;
		}
		if (target == null) {
			getNavigation().stop();
			return;
		}
		double gap = gapTo(target);
		if (cooldown <= 0 && gap <= 2.5) {
			startMove(FrostGiantAnims.BRUTE_BRAZIER_SMASH, target);
			return;
		}
		double dist = distanceTo(target);
		if (target instanceof Player && cooldown <= 0) {
			if (chargeCooldown <= 0 && dist >= 8 && dist <= 22 && hasLineOfSight(target)) {
				lockDirection(target);
				startMove(FrostGiantAnims.BRUTE_CHARGE_WINDUP, target);
				chargeCooldown = cfg.chargeCooldownTicks;
				return;
			}
			if (spikeCooldown <= 0 && dist >= 5 && dist <= cfg.spikeRange) {
				lockDirection(target);
				startMove(FrostGiantAnims.BRUTE_GLACIER_SPIKES, target);
				spikeCooldown = cfg.spikeCooldownTicks;
				return;
			}
		}
		approach(target, 1.0);
	}

	private void lockDirection(LivingEntity target) {
		Vec3 d = target.position().subtract(position()).multiply(1, 0, 1);
		lockedDir = d.lengthSqr() < 1.0e-4 ? Vec3.directionFromRotation(0, getYRot()) : d.normalize();
		lockedFrom = position();
		face(position().add(lockedDir));
	}

	private void tickMove(ServerLevel level, LivingEntity target, FrostGiantConfig.Brute cfg) {
		if (move == FrostGiantAnims.BRUTE_CHARGE_WINDUP) {
			face(position().add(lockedDir));
			if (moveTick % 4 == 0) {
				level.sendParticles(FrostFx.SNOW, getX() + lockedDir.x, getY() + 0.1, getZ() + lockedDir.z, 8, 0.6, 0.1, 0.6, 0.1);
				level.playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_STEP, SoundSource.HOSTILE, 1.2f, 0.6f);
			}
			if (moveTick >= move.length()) {
				chargeTicks = 0;
				chargeHit.clear();
				startMove(FrostGiantAnims.BRUTE_CHARGE, target);
				level.playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2.0f, 0.6f);
			}
			return;
		}
		if (move == FrostGiantAnims.BRUTE_CHARGE) {
			chargeTicks++;
			face(position().add(lockedDir));
			Vec3 v = lockedDir.scale(cfg.chargeSpeed);
			setDeltaMovement(v.x, getDeltaMovement().y, v.z);
			hurtMarked = true;
			if (chargeTicks % 2 == 0) {
				level.sendParticles(FrostFx.SNOW, getX(), getY() + 0.2, getZ(), 6, 0.8, 0.1, 0.8, 0.1);
			}
			boolean struck = false;
			for (LivingEntity e : FrostCombat.targetsNear(level, position().add(0, 1, 0), getBbWidth() * 0.5 + 1.6)) {
				if (chargeHit.add(e.getUUID())) {
					float dmg = e instanceof RuneBrazierEntity ? cfg.brazierSmashDamage : cfg.chargeDamage;
					if (FrostCombat.hit(level, this, e, dmg, 0) && !(e instanceof RuneBrazierEntity)) {
						e.push(lockedDir.x * 1.6, 0.9, lockedDir.z * 1.6);
						e.hurtMarked = true;
					}
					struck = true;
				}
			}
			if (struck) {
				FrostFx.shake(level, position(), 0.5f, 10, 16);
				level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.2f, 1.2f);
			}
			if (struck || chargeTicks > 28 || chargeTicks > 4 && horizontalCollision) {
				setDeltaMovement(0, getDeltaMovement().y, 0);
				clearAnim();
				endMove(30);
			}
			return;
		}
		if (target != null) {
			face(target.position());
		}
		if (move == FrostGiantAnims.BRUTE_BRAZIER_SMASH && moveTick == move.hit() && target != null && gapTo(target) <= 3.2) {
			float dmg = target instanceof RuneBrazierEntity ? cfg.brazierSmashDamage : cfg.meleeDamage;
			FrostCombat.hit(level, this, target, dmg, 0);
			FrostFx.landing(level, target.position(), 0.8);
			level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.0f, 0.5f);
		}
		if (move == FrostGiantAnims.BRUTE_GLACIER_SPIKES) {
			face(position().add(lockedDir));
			int hit = move.hit();
			if (moveTick < hit && moveTick >= hit - 20 && moveTick % 2 == 0) {
				// cracking frost along the ground shows the line
				for (double d = 2; d <= cfg.spikeRange; d += 1.5) {
					Vec3 p = ground(level, lockedFrom.add(lockedDir.scale(d)));
					level.sendParticles(FrostFx.SHADOW, p.x, p.y + 0.1, p.z, 1, 0.15, 0.0, 0.15, 0);
					level.sendParticles(ParticleTypes.SNOWFLAKE, p.x, p.y + 0.1, p.z, 1, 0.2, 0.0, 0.2, 0);
				}
			}
			if (moveTick == hit) {
				FrostFx.shake(level, position(), 0.45f, 10, 18);
				level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.4f, 0.8f);
			}
			if (moveTick >= hit) {
				int step = moveTick - hit;
				double d0 = 2.0 + step * 2.0;
				if (d0 <= cfg.spikeRange) {
					for (double d = d0; d < d0 + 2.0 && d <= cfg.spikeRange; d += 1.0) {
						spike(level, ground(level, lockedFrom.add(lockedDir.scale(d))), cfg);
					}
				}
			}
		}
		if (move != null && moveTick >= move.length()) {
			endMove(move == FrostGiantAnims.BRUTE_BRAZIER_SMASH ? 20 : 30);
		}
	}

	private Vec3 ground(ServerLevel level, Vec3 at) {
		BlockPos col = BlockPos.containing(at.x, at.y, at.z);
		if (!level.isLoaded(col)) {
			return at;
		}
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, col.getX(), col.getZ());
		if (Math.abs(y - at.y) > 6) {
			y = Mth.floor(at.y);
		}
		return new Vec3(at.x, y, at.z);
	}

	private void spike(ServerLevel level, Vec3 at, FrostGiantConfig.Brute cfg) {
		BlockPos base = BlockPos.containing(at.x, at.y, at.z);
		FrostGiantAssault a = assault();
		FrostGiantAssault.placeTempIce(level, a, base, Blocks.PACKED_ICE.defaultBlockState(), 60);
		FrostGiantAssault.placeTempIce(level, a, base.above(), Blocks.BLUE_ICE.defaultBlockState(), 60);
		level.sendParticles(FrostFx.ICE, at.x, at.y + 1, at.z, 8, 0.3, 0.6, 0.3, 0.15);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 0.8f, 0.5f);
		for (LivingEntity e : FrostCombat.targetsNear(level, at.add(0, 1, 0), 1.6)) {
			if (FrostCombat.hit(level, this, e, cfg.spikeDamage, 30) && !(e instanceof RuneBrazierEntity)) {
				e.push(0, 0.6, 0);
				e.hurtMarked = true;
			}
		}
	}
}
