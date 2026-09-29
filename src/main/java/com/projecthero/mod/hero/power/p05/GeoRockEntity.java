package com.projecthero.mod.hero.power.p05;

import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.revamp.BatchBEntities;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.13.22 Geokinesis R -- Rock Shot: a chunk of the ground you are standing on, torn up and hurled. It renders as
 * that block (a thrown-item billboard of the block item, so a deepslate player throws deepslate and a desert player
 * throws sand), arcs very slightly, and carries the ground's rider (see {@link GroundType}).
 */
public class GeoRockEntity extends ThrowableItemProjectile {
	private float damage = 11.0f;
	private GroundType ground = GroundType.STONE;
	private BlockState dust = Blocks.STONE.defaultBlockState();
	private int age;

	public GeoRockEntity(EntityType<? extends GeoRockEntity> type, Level level) {
		super(type, level);
	}

	public GeoRockEntity(Level level, LivingEntity shooter, float damage, GroundType.Ground g) {
		super(BatchBEntities.GEO_ROCK, shooter, level);
		this.damage = damage;
		this.ground = g.type();
		this.dust = g.state();
		Item item = g.state().getBlock().asItem();
		setItem(new ItemStack(item instanceof BlockItem ? item : Items.COBBLESTONE));
	}

	@Override
	protected Item getDefaultItem() {
		return Items.COBBLESTONE;
	}

	@Override
	protected double getDefaultGravity() {
		return 0.012;
	}

	public float damage() {
		return damage;
	}

	@Override
	public void tick() {
		super.tick();
		if (level() instanceof ServerLevel server) {
			if (age % 2 == 0) {
				server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, dust), getX(), getY(), getZ(),
						2, 0.1, 0.1, 0.1, 0.0);
			}
			if (++age > 80) {
				discard();
			}
		}
	}

	@Override
	protected void onHitEntity(EntityHitResult result) {
		if (!(level() instanceof ServerLevel) || result.getEntity() == getOwner()) {
			return;
		}
		if (getOwner() instanceof ServerPlayer p && result.getEntity() instanceof LivingEntity e) {
			if (AbilityHelpers.hurtLands(p, e, damage)) {
				AbilityHelpers.knockbackFrom(e, position().subtract(getDeltaMovement()), 1.2);
				ground.onHit(p, e, p.position());
			}
		}
	}

	@Override
	protected void onHit(HitResult result) {
		if (result.getType() == HitResult.Type.ENTITY && ((EntityHitResult) result).getEntity() == getOwner()) {
			return;
		}
		super.onHit(result);
		if (level() instanceof ServerLevel server) {
			Vec3 c = result.getLocation();
			server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, dust), c.x, c.y, c.z, 24, 0.3, 0.3, 0.3, 0.12);
			server.playSound(null, c.x, c.y, c.z, dust.getSoundType().getBreakSound(), SoundSource.PLAYERS, 1.0f, 0.8f);
			if (getOwner() instanceof ServerPlayer p) {
				ground.onImpact(p, c);
			}
			server.playSound(null, c.x, c.y, c.z, SoundEvents.STONE_HIT, SoundSource.PLAYERS, 0.8f, 0.6f);
			discard();
		}
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putFloat("Damage", damage);
		tag.putInt("Ground", ground.ordinal());
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		damage = tag.getFloat("Damage");
		int g = tag.getInt("Ground");
		ground = g >= 0 && g < GroundType.values().length ? GroundType.values()[g] : GroundType.STONE;
	}
}
