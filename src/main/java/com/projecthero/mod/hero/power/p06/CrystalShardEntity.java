package com.projecthero.mod.hero.power.p06;

import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.revamp.BatchBEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.13.22 Crystalkinesis R -- a flying amethyst shard (drawn as a small faceted crystal by
 * {@code CrystalShardRenderer}). Wherever it lands it grows a {@link CrystalNodeEntity}: on the face of the block it
 * struck, or at the feet of the creature it hit. Spire shards and reflected shards never plant nodes.
 */
public class CrystalShardEntity extends ThrowableItemProjectile {
	private static final BlockParticleOption DUST =
			new BlockParticleOption(ParticleTypes.BLOCK, Blocks.AMETHYST_BLOCK.defaultBlockState());
	private float damage = 13.0f;
	private boolean plantsNode = true;
	private int age;

	public CrystalShardEntity(EntityType<? extends CrystalShardEntity> type, Level level) {
		super(type, level);
	}

	public CrystalShardEntity(Level level, LivingEntity shooter, float damage, boolean plantsNode) {
		super(BatchBEntities.CRYSTAL_SHARD, shooter, level);
		this.damage = damage;
		this.plantsNode = plantsNode;
	}

	@Override
	protected Item getDefaultItem() {
		return Items.AMETHYST_SHARD;
	}

	@Override
	protected double getDefaultGravity() {
		return 0.01;
	}

	@Override
	public void tick() {
		super.tick();
		if (level() instanceof ServerLevel server) {
			if (age % 2 == 0) {
				server.sendParticles(ParticleTypes.END_ROD, getX(), getY(), getZ(), 1, 0.02, 0.02, 0.02, 0.0);
			}
			if (++age > 60) {
				discard();
			}
		}
	}

	@Override
	protected void onHitEntity(EntityHitResult result) {
		if (!(level() instanceof ServerLevel server) || result.getEntity() == getOwner()) {
			return;
		}
		if (result.getEntity() instanceof CrystalNodeEntity) {
			return;
		}
		if (getOwner() instanceof ServerPlayer p && result.getEntity() instanceof LivingEntity e) {
			if (AbilityHelpers.hurtLands(p, e, damage)) {
				AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, 40, 1);
			}
			if (plantsNode) {
				CrystalNodeEntity.spawn(server, p, Vec3.atBottomCenterOf(e.blockPosition()), Direction.UP, false);
			}
		}
	}

	@Override
	protected void onHitBlock(BlockHitResult result) {
		super.onHitBlock(result);
		if (plantsNode && level() instanceof ServerLevel server && getOwner() instanceof ServerPlayer p) {
			BlockPos cell = result.getBlockPos().relative(result.getDirection());
			if (server.getBlockState(cell).isAir() || server.getBlockState(cell).canBeReplaced()) {
				CrystalNodeEntity.spawn(server, p, Vec3.atBottomCenterOf(cell), result.getDirection(), false);
			}
		}
	}

	@Override
	protected void onHit(HitResult result) {
		if (result.getType() == HitResult.Type.ENTITY) {
			var hit = ((EntityHitResult) result).getEntity();
			if (hit == getOwner() || hit instanceof CrystalNodeEntity) {
				return;
			}
		}
		super.onHit(result);
		if (level() instanceof ServerLevel server) {
			Vec3 c = result.getLocation();
			server.sendParticles(DUST, c.x, c.y, c.z, 14, 0.2, 0.2, 0.2, 0.1);
			server.playSound(null, c.x, c.y, c.z, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 1.0f, 1.3f);
			discard();
		}
	}

	@Override
	protected boolean canHitEntity(net.minecraft.world.entity.Entity e) {
		return !(e instanceof CrystalNodeEntity) && super.canHitEntity(e);
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putFloat("Damage", damage);
		tag.putBoolean("PlantsNode", plantsNode);
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		damage = tag.getFloat("Damage");
		plantsNode = tag.getBoolean("PlantsNode");
	}
}
