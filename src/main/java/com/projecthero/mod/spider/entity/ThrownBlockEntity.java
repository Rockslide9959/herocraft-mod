package com.projecthero.mod.spider.entity;

import java.util.UUID;

import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.hero.power.AbilityHelpers;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.1: a block Spider-Man ripped out with Grab &amp; Hurl (Shift+Z) and threw. A plain ballistic body (gravity +
 * drag, ordinary block collision) that renders as its block, tumbling. The moment it hits a block or a body it shatters:
 * the block's particles, its normal drops (never the block itself placed back), and damage to whatever it hit. It never
 * places a block, so -- unlike a falling block -- it cannot land and build.
 */
public class ThrownBlockEntity extends Entity {
	private static final EntityDataAccessor<BlockState> STATE =
			SynchedEntityData.defineId(ThrownBlockEntity.class, EntityDataSerializers.BLOCK_STATE);

	private UUID ownerId;
	private float damage = 10.0f;
	private int life;
	/** True while Grab &amp; Hurl is still hauling it to the hand (no gravity, no collision, cannot shatter). */
	public boolean held;

	public ThrownBlockEntity(EntityType<? extends ThrownBlockEntity> type, Level level) {
		super(type, level);
	}

	public static ThrownBlockEntity create(ServerLevel level, ServerPlayer owner, BlockState state, Vec3 pos, float damage) {
		ThrownBlockEntity e = new ThrownBlockEntity(SpiderEntityTypes.THROWN_BLOCK, level);
		e.setPos(pos.x, pos.y, pos.z);
		e.entityData.set(STATE, state);
		e.ownerId = owner.getUUID();
		e.damage = damage;
		return e;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(STATE, Blocks.STONE.defaultBlockState());
	}

	public BlockState blockState() {
		return this.entityData.get(STATE);
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide) {
			return; // the server moves it (position synced every tick); the client only draws it
		}
		if (held) {
			// on the end of the web, being hauled to the hand: steered by Grab & Hurl, passes through everything
			setPos(position().add(getDeltaMovement()));
			if (++life > 60) {
				held = false;
			}
			return;
		}
		Vec3 v = getDeltaMovement().add(0, -0.06, 0).scale(0.985);
		setDeltaMovement(v);
		if (++life > 200) {
			shatter(null);
			return;
		}
		// bodies first: anything this tick's path sweeps through
		AABB sweep = getBoundingBox().expandTowards(v).inflate(0.3);
		ServerPlayer owner = ownerId == null ? null : ((ServerLevel) level()).getServer().getPlayerList().getPlayer(ownerId);
		for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, sweep, LivingEntity::isAlive)) {
			if (owner != null && (e == owner || !HeroTargets.canHarm(owner, e))) {
				continue;
			}
			shatter(e);
			return;
		}
		move(MoverType.SELF, v);
		if (horizontalCollision || verticalCollision || onGround()) {
			shatter(null);
		}
	}

	private void shatter(LivingEntity struck) {
		if (!(level() instanceof ServerLevel level)) {
			return;
		}
		BlockState state = blockState();
		Vec3 c = position().add(0, 0.5, 0);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), c.x, c.y, c.z, 40, 0.4, 0.4, 0.4, 0.15);
		level.playSound(null, c.x, c.y, c.z, state.getSoundType().getBreakSound(), SoundSource.BLOCKS, 1.2f, 0.8f);
		ServerPlayer owner = ownerId == null ? null : level.getServer().getPlayerList().getPlayer(ownerId);
		if (owner != null) {
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(1.4),
					x -> x.isAlive() && x != owner && HeroTargets.canHarm(owner, x))) {
				float dmg = e == struck ? damage : damage * 0.5f;
				AbilityHelpers.hurt(owner, e, dmg);
				AbilityHelpers.knockbackFrom(e, position(), 0.9);
			}
		}
		// the block's ordinary drops, as items (the block is never placed back)
		for (ItemStack drop : Block.getDrops(state, level, BlockPos.containing(c), null)) {
			ItemEntity item = new ItemEntity(level, c.x, c.y, c.z, drop);
			item.setDefaultPickUpDelay();
			level.addFreshEntity(item);
		}
		discard();
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
		if (tag.contains("block")) {
			this.entityData.set(STATE, NbtUtils.readBlockState(level().holderLookup(net.minecraft.core.registries.Registries.BLOCK),
					tag.getCompound("block")));
		}
		if (tag.hasUUID("owner")) {
			ownerId = tag.getUUID("owner");
		}
		damage = tag.getFloat("damage");
		life = tag.getInt("life");
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
		tag.put("block", NbtUtils.writeBlockState(blockState()));
		if (ownerId != null) {
			tag.putUUID("owner", ownerId);
		}
		tag.putFloat("damage", damage);
		tag.putInt("life", life);
	}
}
