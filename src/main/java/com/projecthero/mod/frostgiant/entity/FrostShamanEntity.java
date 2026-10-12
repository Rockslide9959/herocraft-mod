package com.projecthero.mod.frostgiant.entity;

import com.projecthero.mod.frostgiant.FrostBestiary;
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
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.1: a <b>Frost Shaman</b> -- 5 blocks tall, robed, a staff with a glowing crystal. A priority target: she keeps 14+
 * blocks away and makes the fight harder for everyone.
 * <ul>
 *   <li><b>Ice Wall</b>: a 5-wide, 3-tall line of temporary packed ice raised in front of a fighter (8 s, breakable,
 *       tracked and removed).</li>
 *   <li><b>Rime Ward</b>: encases a wounded giant in ice for 4 s -- invulnerable, then healed 25% -- unless the fighters
 *       break the ice first.</li>
 *   <li><b>Frost Nova</b> when a fighter gets within 4 blocks: 6 damage, knockback and heavy freeze.</li>
 * </ul>
 */
public class FrostShamanEntity extends FrostGiantEntity {
	private int wallCooldown = 60;
	private int wardCooldown = 80;
	private int novaCooldown;
	private LivingEntity wardTarget;

	public FrostShamanEntity(EntityType<? extends FrostShamanEntity> type, Level level) {
		super(type, level);
		this.xpReward = 20;
	}

	public static AttributeSupplier.Builder createAttributes() {
		FrostGiantConfig.Shaman cfg = FrostGiantConfig.shaman();
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, cfg.health)
				.add(Attributes.ARMOR, cfg.armor)
				.add(Attributes.ATTACK_DAMAGE, 4.0)
				.add(Attributes.MOVEMENT_SPEED, cfg.speed)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.5)
				.add(Attributes.FOLLOW_RANGE, 64.0)
				.add(Attributes.STEP_HEIGHT, 1.5);
	}

	@Override
	public String model() {
		return FrostGiantAnims.SHAMAN;
	}

	@Override
	public int bestiaryId() {
		return FrostBestiary.SHAMAN;
	}

	@Override
	protected Clip idleClip() {
		return FrostGiantAnims.SHAMAN_IDLE;
	}

	@Override
	protected Clip walkClip() {
		return FrostGiantAnims.SHAMAN_WALK;
	}

	@Override
	protected Clip runClip() {
		return FrostGiantAnims.SHAMAN_RUN;
	}

	@Override
	protected Clip deathClip() {
		return FrostGiantAnims.SHAMAN_DEATH;
	}

	@Override
	protected Clip hurtClip() {
		return FrostGiantAnims.SHAMAN_HURT;
	}

	@Override
	protected void tickCombat(ServerLevel level, LivingEntity target) {
		FrostGiantConfig.Shaman cfg = FrostGiantConfig.shaman();
		if (wallCooldown > 0) {
			wallCooldown--;
		}
		if (wardCooldown > 0) {
			wardCooldown--;
		}
		if (novaCooldown > 0) {
			novaCooldown--;
		}
		if (move != null) {
			if (move == FrostGiantAnims.SHAMAN_RIME_WARD && wardTarget != null) {
				face(wardTarget.position());
			} else if (target != null) {
				face(target.position());
			}
			Vec3 staff = position().add(0, getBbHeight() * 0.95, 0);
			if (moveTick < (move.hit() < 0 ? 0 : move.hit()) && moveTick % 3 == 0) {
				level.sendParticles(FrostFx.FROST, staff.x, staff.y, staff.z, 3, 0.2, 0.2, 0.2, 0.01);
			}
			if (moveTick == move.hit()) {
				if (move == FrostGiantAnims.SHAMAN_FROST_NOVA) {
					nova(level, cfg);
				} else if (move == FrostGiantAnims.SHAMAN_RIME_WARD && wardTarget != null && wardTarget.isAlive()
						&& wardTarget instanceof FrostGiantEntity giant && !giant.isWarded()) {
					IcePrisonEntity prison = IcePrisonEntity.encase(level, giant, IcePrisonEntity.WARD, cfg.wardTicks,
							Math.max(4, (int) (giant.getBbHeight() * 1.2)), assaultId(), false);
					if (prison != null) {
						giant.ward(cfg.wardTicks, prison.getUUID());
					}
					FrostFx.line(level, FrostFx.FROST, staff, giant.position().add(0, giant.getBbHeight() * 0.5, 0), 0.6, false);
				} else if (move == FrostGiantAnims.SHAMAN_ICE_WALL && target != null) {
					raiseWall(level, target, cfg);
				}
			}
			if (moveTick >= move.length()) {
				wardTarget = null;
				endMove(20);
			}
			return;
		}
		if (target == null) {
			getNavigation().stop();
			return;
		}
		// Frost Nova: someone got too close
		if (novaCooldown <= 0) {
			for (Player p : FrostCombat.playersNear(level, position(), cfg.novaRadius + getBbWidth() * 0.5)) {
				startMove(FrostGiantAnims.SHAMAN_FROST_NOVA, p);
				novaCooldown = cfg.novaCooldownTicks;
				return;
			}
		}
		// Rime Ward on a wounded giant
		if (wardCooldown <= 0 && cooldown <= 0) {
			LivingEntity hurt = woundedGiant(level);
			if (hurt != null) {
				wardTarget = hurt;
				startMove(FrostGiantAnims.SHAMAN_RIME_WARD, target);
				wardCooldown = cfg.wardCooldownTicks;
				return;
			}
		}
		double dist = distanceTo(target);
		if (wallCooldown <= 0 && cooldown <= 0 && target instanceof Player && dist > 6 && dist < 34 && hasLineOfSight(target)) {
			startMove(FrostGiantAnims.SHAMAN_ICE_WALL, target);
			wallCooldown = cfg.iceWallCooldownTicks;
			return;
		}
		if (dist < cfg.keepAway - 2) {
			retreatFrom(target, 1.25);
		} else if (dist > cfg.keepAway + 10) {
			approach(target, 1.0);
		} else {
			getNavigation().stop();
			getLookControl().setLookAt(target, 30f, 30f);
		}
	}

	private LivingEntity woundedGiant(ServerLevel level) {
		LivingEntity best = null;
		float bestFrac = 0.5f;
		for (FrostGiantEntity g : level.getEntitiesOfClass(FrostGiantEntity.class, getBoundingBox().inflate(20))) {
			if (g == this || g instanceof KingLaufeyEntity || g instanceof RimeHoundEntity || g.isWarded() || g.isDeadOrDying()
					|| g.isArriving()) {
				continue;
			}
			float f = g.getHealth() / g.getMaxHealth();
			if (f < bestFrac) {
				bestFrac = f;
				best = g;
			}
		}
		return best;
	}

	private void nova(ServerLevel level, FrostGiantConfig.Shaman cfg) {
		Vec3 c = position().add(0, 1.0, 0);
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_HURT_FREEZE, SoundSource.HOSTILE, 2.0f, 0.5f);
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 1.5f, 0.6f);
		FrostFx.ring(level, FrostFx.FROST_BIG, c, cfg.novaRadius, 28, true);
		level.sendParticles(ParticleTypes.SNOWFLAKE, c.x, c.y, c.z, 40, cfg.novaRadius * 0.4, 0.5, cfg.novaRadius * 0.4, 0.2);
		double r = cfg.novaRadius + getBbWidth() * 0.5;
		for (LivingEntity e : FrostCombat.targetsNear(level, position(), r)) {
			if (e instanceof RuneBrazierEntity) {
				continue;
			}
			if (FrostCombat.hit(level, this, e, cfg.novaDamage, cfg.novaFreezeTicks)) {
				Vec3 push = e.position().subtract(position()).multiply(1, 0, 1);
				if (push.lengthSqr() > 1.0e-4) {
					push = push.normalize().scale(1.4);
					e.push(push.x, 0.45, push.z);
					e.hurtMarked = true;
				}
			}
		}
	}

	/** A 5 x 3 packed-ice wall three blocks in front of {@code target}, across the line to the Shaman. */
	private void raiseWall(ServerLevel level, LivingEntity target, FrostGiantConfig.Shaman cfg) {
		Vec3 toMe = position().subtract(target.position()).multiply(1, 0, 1);
		if (toMe.lengthSqr() < 1.0e-3) {
			return;
		}
		Vec3 dir = toMe.normalize();
		Vec3 mid = target.position().add(dir.scale(3.0));
		Vec3 across = new Vec3(-dir.z, 0, dir.x);
		FrostGiantAssault a = assault();
		int placed = 0;
		for (int i = -2; i <= 2; i++) {
			Vec3 col = mid.add(across.scale(i));
			int x = Mth.floor(col.x);
			int z = Mth.floor(col.z);
			int y = Mth.floor(target.getY());
			for (int dy = 0; dy < 3; dy++) {
				if (FrostGiantAssault.placeTempIce(level, a, new BlockPos(x, y + dy, z), Blocks.PACKED_ICE.defaultBlockState(), cfg.iceWallTicks)) {
					placed++;
				}
			}
			level.sendParticles(FrostFx.ICE, col.x, y + 1.5, col.z, 6, 0.3, 0.8, 0.3, 0.1);
		}
		if (placed > 0) {
			level.playSound(null, mid.x, mid.y, mid.z, SoundEvents.GLASS_PLACE, SoundSource.HOSTILE, 2.0f, 0.5f);
		}
	}
}
