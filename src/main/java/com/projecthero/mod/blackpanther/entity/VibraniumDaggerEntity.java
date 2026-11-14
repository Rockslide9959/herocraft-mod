package com.projecthero.mod.blackpanther.entity;

import java.util.HashSet;
import java.util.Set;

import com.projecthero.mod.blackpanther.BlackPanther;
import com.projecthero.mod.blackpanther.BlackPantherCombat;
import com.projecthero.mod.blackpanther.BlackPantherConfig;

import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3: the Vibranium Dagger (Z). Flies straight, hops to the next enemy within 10 blocks for up to 3 hits, and flies back
 * to his hand when it runs out of targets, hits a wall or goes too far. Only ever exists while thrown.
 */
public class VibraniumDaggerEntity extends Projectile {
	private final Set<Integer> struck = new HashSet<>();
	private boolean returning;
	private double travelled;
	private int life;

	public VibraniumDaggerEntity(EntityType<? extends VibraniumDaggerEntity> type, Level level) {
		super(type, level);
		this.setNoGravity(true);
	}

	/** v0.17.4: the unsuited Throwing Knife is the same blade, thrown plain -- one target, less damage, a shorter reach. */
	private float damage = BlackPantherConfig.DAGGER_DAMAGE;
	private int bounces = BlackPantherConfig.DAGGER_BOUNCES;
	private double range = BlackPantherConfig.DAGGER_RANGE;

	public static VibraniumDaggerEntity shootKnife(ServerPlayer owner) {
		VibraniumDaggerEntity d = shoot(owner);
		d.damage = BlackPantherConfig.KNIFE_DAMAGE;
		d.bounces = 1;
		d.range = BlackPantherConfig.KNIFE_RANGE;
		return d;
	}

	public static VibraniumDaggerEntity shoot(ServerPlayer owner) {
		VibraniumDaggerEntity d = new VibraniumDaggerEntity(BlackPantherEntityTypes.VIBRANIUM_DAGGER, owner.level());
		d.setOwner(owner);
		Vec3 from = owner.getEyePosition().add(0, -0.25, 0).add(owner.getLookAngle().scale(0.6));
		d.moveTo(from.x, from.y, from.z, 0f, 0f);
		d.setDeltaMovement(owner.getLookAngle().scale(BlackPantherConfig.DAGGER_SPEED));
		owner.level().addFreshEntity(d);
		return d;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
	}

	@Override
	public void tick() {
		super.tick();
		Vec3 v = this.getDeltaMovement();
		if (!this.level().isClientSide) {
			if (++this.life > 200 || !(this.getOwner() instanceof ServerPlayer owner) || !owner.isAlive() || owner.level() != this.level()) {
				this.discard();
				return;
			}
			if (this.returning) {
				Vec3 to = owner.getEyePosition().add(0, -0.3, 0).subtract(this.position());
				if (to.length() < 1.6) {
					this.discard();
					this.level().playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.PLAYERS, 0.6f, 1.6f);
					return;
				}
				v = to.normalize().scale(BlackPantherConfig.DAGGER_SPEED * 1.4);
				this.setDeltaMovement(v);
				this.hurtMarked = true;
			} else {
				HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
				if (hit.getType() != HitResult.Type.MISS) {
					this.onHit(hit);
					v = this.getDeltaMovement();
				}
				this.travelled += v.length();
				if (this.travelled > this.range) {
					this.returning = true;
				}
			}
		}
		this.setPos(this.getX() + v.x, this.getY() + v.y, this.getZ() + v.z);
	}

	@Override
	protected boolean canHitEntity(Entity e) {
		if (!super.canHitEntity(e) || !(e instanceof LivingEntity le) || this.struck.contains(e.getId())) {
			return false;
		}
		return !(this.getOwner() instanceof ServerPlayer p) || BlackPantherCombat.isTarget(p, le);
	}

	@Override
	protected void onHitEntity(EntityHitResult result) {
		if (!(this.getOwner() instanceof ServerPlayer p) || !(result.getEntity() instanceof LivingEntity le)) {
			return;
		}
		BlackPantherCombat.strike(p, le, this.position(), this.damage, 0.2, 0.0);
		this.struck.add(le.getId());
		this.level().playSound(null, le.getX(), le.getY(), le.getZ(), SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 0.8f, 1.4f);
		LivingEntity next = this.struck.size() >= this.bounces ? null : nextTarget(p, le);
		if (next == null) {
			this.returning = true;
			return;
		}
		Vec3 to = next.position().add(0, next.getBbHeight() * 0.5, 0).subtract(this.position());
		this.setDeltaMovement(to.normalize().scale(BlackPantherConfig.DAGGER_SPEED));
		this.hurtMarked = true;
		this.travelled = 0.0;
	}

	private LivingEntity nextTarget(ServerPlayer owner, LivingEntity from) {
		LivingEntity best = null;
		double bestSq = Double.MAX_VALUE;
		for (LivingEntity e : BlackPantherCombat.within(owner, from.position(), BlackPantherConfig.DAGGER_HOP,
				e -> e != from && !this.struck.contains(e.getId()) && BlackPantherCombat.isTarget(owner, e))) {
			double d = e.distanceToSqr(from);
			if (d < bestSq) {
				bestSq = d;
				best = e;
			}
		}
		return best;
	}

	@Override
	protected void onHitBlock(BlockHitResult result) {
		super.onHitBlock(result);
		this.returning = true;
		if (this.level() instanceof ServerLevel level) {
			Vec3 at = result.getLocation();
			level.sendParticles(BlackPanther.PURPLE, at.x, at.y, at.z, 5, 0.1, 0.1, 0.1, 0.0);
		}
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 128 * 128;
	}
}
