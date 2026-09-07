package com.projecthero.mod.hero.power.p05;

import com.projecthero.mod.hero.HeroConfig;
import com.projecthero.mod.hero.power.AbilityHelpers;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Geokinesis' Colossal Rock ultimate: a huge boulder hurled at ghast-fireball speed. It flies dead
 * straight (no gravity, no drag), tears every earth block out of its own path so terrain never stops
 * it, has a very large hitbox, and detonates into a crater on the first thing it cannot chew through.
 * Renders as an over-scaled stone block via {@code ThrownItemRenderer}.
 */
public class ColossalRockEntity extends ThrowableItemProjectile {
	/** Constant flight speed, blocks/tick -- comparable to a ghast fireball at full tilt. */
	private static final double SPEED = 2.6;
	private static final int MAX_AGE = 120;
	private static final double HIT_RADIUS = 6.0;
	private static final int CRATER_RADIUS = 4;

	private float damage = 50.0f;
	private int age;
	private boolean detonated;

	public ColossalRockEntity(EntityType<? extends ColossalRockEntity> type, Level level) {
		super(type, level);
	}

	public ColossalRockEntity(Level level, LivingEntity shooter, float damage) {
		super(GeoEntityTypes.COLOSSAL_ROCK, shooter, level);
		this.damage = damage;
	}

	@Override
	protected Item getDefaultItem() {
		return Items.STONE;
	}

	@Override
	protected double getDefaultGravity() {
		return 0.0;
	}

	@Override
	public void tick() {
		if (!level().isClientSide) {
			clearEarthAhead();
			// hold a constant velocity -- never let drag or gravity bleed the speed off
			Vec3 v = getDeltaMovement();
			if (v.lengthSqr() > 1.0e-6) {
				setDeltaMovement(v.normalize().scale(SPEED));
			}
		}
		super.tick();
		if (level() instanceof ServerLevel server) {
			server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()),
					getX(), getY(), getZ(), 12, 0.8, 0.8, 0.8, 0.02);
			server.sendParticles(ParticleTypes.POOF, getX(), getY(), getZ(), 3, 0.5, 0.5, 0.5, 0.01);
			if (++age >= MAX_AGE) {
				detonate();
			}
		}
	}

	/** Chews a tunnel of earth blocks out of the boulder's path so nothing but bedrock/builds stops it. */
	private void clearEarthAhead() {
		if (!(level() instanceof ServerLevel server) || !AbilityHelpers.canGrief()) {
			return;
		}
		Vec3 from = position();
		Vec3 step = getDeltaMovement();
		int segments = Math.max(1, (int) Math.ceil(step.length()));
		for (int s = 0; s <= segments; s++) {
			BlockPos centre = BlockPos.containing(from.add(step.scale(s / (double) segments)));
			for (BlockPos bp : BlockPos.betweenClosed(centre.offset(-2, -2, -2), centre.offset(2, 2, 2))) {
				BlockState st = server.getBlockState(bp);
				if (!st.isAir() && GeoBareHands.isEarth(st) && st.getDestroySpeed(server, bp) >= 0
						&& server.getFluidState(bp).isEmpty()) {
					server.destroyBlock(bp, false);
				}
			}
		}
	}

	@Override
	protected void onHitEntity(EntityHitResult result) {
		if (result.getEntity() == getOwner()) {
			return;
		}
		detonate();
	}

	@Override
	protected void onHit(HitResult result) {
		super.onHit(result);
		detonate();
	}

	private void detonate() {
		if (detonated || !(level() instanceof ServerLevel server)) {
			return;
		}
		detonated = true;
		LivingEntity owner = getOwner() instanceof LivingEntity le ? le : null;
		Vec3 c = position();

		for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(HIT_RADIUS),
				e -> e.isAlive() && e != owner && !(e instanceof ArmorStand))) {
			double d = Math.sqrt(e.distanceToSqr(c.x, c.y, c.z));
			if (d > HIT_RADIUS) {
				continue;
			}
			if (e instanceof Player pl && !canHurt(pl, owner)) {
				continue;
			}
			float dmg = (float) (damage * (1.0 - 0.55 * (d / HIT_RADIUS)));
			e.hurt(damageSources().mobProjectile(this, owner), dmg);
			Vec3 push = e.position().subtract(c).normalize();
			e.setDeltaMovement(e.getDeltaMovement().add(push.x * 1.7, 0.55, push.z * 1.7));
			e.hurtMarked = true;
		}

		if (AbilityHelpers.canGrief()) {
			BlockPos base = BlockPos.containing(c);
			for (BlockPos bp : BlockPos.betweenClosed(base.offset(-CRATER_RADIUS, -CRATER_RADIUS, -CRATER_RADIUS),
					base.offset(CRATER_RADIUS, CRATER_RADIUS, CRATER_RADIUS))) {
				if (bp.distSqr(base) > (double) CRATER_RADIUS * CRATER_RADIUS) {
					continue;
				}
				BlockState st = server.getBlockState(bp);
				if (!st.isAir() && GeoBareHands.isEarth(st) && st.getDestroySpeed(server, bp) >= 0) {
					server.destroyBlock(bp, false);
				}
			}
		}

		server.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 2, 1.0, 1.0, 1.0, 0.0);
		server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()),
				c.x, c.y, c.z, 180, 3.0, 3.0, 3.0, 0.25);
		server.playSound(null, BlockPos.containing(c), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 2.0f, 0.3f);
		server.playSound(null, BlockPos.containing(c), SoundEvents.STONE_BREAK, SoundSource.PLAYERS, 2.0f, 0.4f);
		discard();
	}

	private boolean canHurt(Player target, LivingEntity owner) {
		if (target == owner) {
			return true;
		}
		return getServer() != null && getServer().isPvpAllowed() && HeroConfig.get().abilityPvpDamage;
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putFloat("Damage", damage);
		tag.putInt("Age", age);
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		if (tag.contains("Damage")) {
			damage = tag.getFloat("Damage");
		}
		age = tag.getInt("Age");
	}
}
