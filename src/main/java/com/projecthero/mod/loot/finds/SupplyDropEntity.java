package com.projecthero.mod.loot.finds;

import com.projecthero.mod.loot.HeroVault;
import com.projecthero.mod.loot.HeroVaultBlockEntity;
import com.projecthero.mod.loot.LootItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * v0.17.5 Supply Drop (v0.17.7: dressed on landing, see {@link #decorate}): a Hero Vault crate under a red-and-white parachute, drifting down trailing signal smoke. On
 * touching the ground it becomes a real Hero Vault block (tier 1, sometimes 2) and this entity stays behind, invisible,
 * as the crate's timer: the vault is taken away again {@link #UNOPENED_LIFETIME} after landing if nobody opened it, or
 * {@link #OPENED_LIFETIME} after somebody first did (so a squad has time to take its rolls). The timer is saved with
 * the entity and only runs while the chunk is loaded.
 *
 * <p>{@link com.projecthero.mod.entity.GiantTracking.AlwaysTracked}: sent to every player within its tracking range (160
 * blocks) whatever their view distance, so the fall is visible 40-80 blocks out even at a low render distance; while it
 * falls it keeps its chunk entity-ticking with {@link com.projecthero.mod.entity.GiantTracking#keepLoaded}.
 *
 * <p>No static state: everything lives on the entity (NBT) and in {@link SupplyDropSchedule} (world saved data).
 */
public class SupplyDropEntity extends Entity implements com.projecthero.mod.entity.GiantTracking.AlwaysTracked {
	public static final double FALL_SPEED = 0.32;
	public static final int UNOPENED_LIFETIME = 20 * 60 * 20;
	public static final int OPENED_LIFETIME = 5 * 60 * 20;

	private static final EntityDataAccessor<Integer> TIER = SynchedEntityData.defineId(SupplyDropEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> LANDED = SynchedEntityData.defineId(SupplyDropEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Integer> FACING = SynchedEntityData.defineId(SupplyDropEntity.class, EntityDataSerializers.INT);

	private int landedTicks;
	private int openedTicks = -1;
	private BlockPos vaultPos;
	/** v0.17.7: the dressing round the landed crate (parachute, flare, broken pallet), removed again on pack-up. */
	private final java.util.List<BlockPos> deco = new java.util.ArrayList<>();
	private int decoVariant = -1;

	public SupplyDropEntity(EntityType<? extends SupplyDropEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	public static SupplyDropEntity create(ServerLevel level, double x, double y, double z, int tier, Direction facing) {
		SupplyDropEntity e = new SupplyDropEntity(FindsMod.SUPPLY_DROP, level);
		e.setPos(x, y, z);
		e.entityData.set(TIER, tier);
		e.entityData.set(FACING, facing.get2DDataValue());
		return e;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(TIER, 1);
		builder.define(LANDED, false);
		builder.define(FACING, Direction.SOUTH.get2DDataValue());
	}

	public int tier() {
		return entityData.get(TIER);
	}

	public boolean landed() {
		return entityData.get(LANDED);
	}

	public Direction facing() {
		return Direction.from2DDataValue(entityData.get(FACING));
	}

	public BlockPos vaultPos() {
		return vaultPos;
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide) {
			if (!landed()) {
				// the smoke trail, always visible (a player 80 blocks away should see where it comes down)
				for (int i = 0; i < 2; i++) {
					level().addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true,
							getX() + (random.nextDouble() - 0.5) * 0.4, getY() + 0.1, getZ() + (random.nextDouble() - 0.5) * 0.4,
							0, 0.01, 0);
				}
				if (tickCount % 3 == 0) {
					level().addParticle(ParticleTypes.FLAME, getX(), getY() + 0.05, getZ(), 0, -0.05, 0);
				}
			}
			return;
		}
		ServerLevel level = (ServerLevel) level();
		if (!landed()) {
			fall(level);
		} else {
			timer(level);
		}
	}

	private void fall(ServerLevel level) {
		if (tickCount % 40 == 0) {
			com.projecthero.mod.entity.GiantTracking.keepLoaded(level, position());
		}
		int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, getBlockX(), getBlockZ());
		double next = getY() - FALL_SPEED;
		// a gentle sway under the canopy
		setYRot(getYRot() + 1.5f);
		if (next > ground) {
			setPos(getX(), next, getZ());
			return;
		}
		land(level, new BlockPos(getBlockX(), ground, getBlockZ()));
	}

	private void land(ServerLevel level, BlockPos at) {
		BlockState there = level.getBlockState(at);
		if (!there.isAir() && !there.canBeReplaced()) {
			at = at.above();
		}
		HeroVault.place(level, at, tier(), facing());
		vaultPos = at.immutable();
		setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		entityData.set(LANDED, true);
		level.playSound(null, at, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 1.0f, 0.6f);
		level.playSound(null, at, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 0.5f, 1.6f);
		BlockState below = level.getBlockState(at.below());
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, below.isAir() ? Blocks.DIRT.defaultBlockState() : below),
				at.getX() + 0.5, at.getY() + 0.1, at.getZ() + 0.5, 40, 0.8, 0.1, 0.8, 0.15);
		level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, at.getX() + 0.5, at.getY() + 0.6, at.getZ() + 0.5, 12, 0.6, 0.3, 0.6, 0.01);
		decorate(level, at, random.nextInt(3));
	}

	/**
	 * v0.17.7 decoration pass: the landed crate gets dressing, one of three looks -- 0 the red-and-white parachute draped
	 * behind it, 1 the parachute plus a lit signal flare beside it, 2 the pallet smashed on landing (planks and slats
	 * strewn round) plus the parachute. Only placed into air over solid ground; every block is remembered and taken away
	 * again when the crate is collected.
	 */
	public void decorate(ServerLevel level, BlockPos at, int variant) {
		decoVariant = variant;
		if (vaultPos == null) {
			vaultPos = at.immutable();
		}
		Direction face = facing();
		Direction back = face.getOpposite();
		Direction side = face.getClockWise();
		for (int row = 1; row <= 2; row++) {
			for (int s = -1; s <= 1; s++) {
				if (row == 2 && s != 0 && random.nextFloat() < 0.4f) {
					continue;
				}
				BlockPos p = at.relative(back, row).relative(side, s);
				decoAt(level, p, ((row + s) & 1) == 0 ? Blocks.RED_CARPET.defaultBlockState() : Blocks.WHITE_CARPET.defaultBlockState());
			}
		}
		if (variant == 1) {
			decoAt(level, at.relative(side, 2), Blocks.CAMPFIRE.defaultBlockState().setValue(net.minecraft.world.level.block.CampfireBlock.SIGNAL_FIRE, true));
			decoAt(level, at.relative(side, -2).relative(face), Blocks.LANTERN.defaultBlockState());
		} else if (variant == 2) {
			int[][] bits = { { 1, 1 }, { -1, 1 }, { 2, 0 }, { -2, -1 }, { 1, 2 } };
			for (int[] b : bits) {
				BlockPos p = at.relative(side, b[0]).relative(face, b[1]);
				decoAt(level, p, random.nextBoolean() ? Blocks.SPRUCE_SLAB.defaultBlockState()
						: Blocks.SPRUCE_TRAPDOOR.defaultBlockState());
			}
		}
	}

	private void decoAt(ServerLevel level, BlockPos p, BlockState state) {
		BlockState there = level.getBlockState(p);
		// air or a plant (grass, flowers) is fine; never a block, never water
		if (!(there.isAir() || there.canBeReplaced() && level.getFluidState(p).isEmpty() && !there.hasBlockEntity())
				|| !level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP)) {
			return;
		}
		level.setBlock(p, state, 3);
		deco.add(p.immutable());
	}

	/** The dressing blocks placed round the landed crate (for tests). */
	public java.util.List<BlockPos> decoration() {
		return java.util.Collections.unmodifiableList(deco);
	}

	public int decoVariant() {
		return decoVariant;
	}

	private void timer(ServerLevel level) {
		if (vaultPos == null || !level.getBlockState(vaultPos).is(LootItems.HERO_VAULT)) {
			packUp(level); // broken / replaced by an operator: tidy the dressing away too
			return;
		}
		landedTicks++;
		if (openedTicks < 0 && level.getBlockEntity(vaultPos) instanceof HeroVaultBlockEntity be && !be.looted().isEmpty()) {
			openedTicks = 0;
		}
		if (openedTicks >= 0) {
			openedTicks++;
		}
		if ((openedTicks < 0 && landedTicks >= UNOPENED_LIFETIME) || openedTicks >= OPENED_LIFETIME) {
			packUp(level);
		}
	}

	/** The crate is collected: the vault block goes, with a puff. */
	public void packUp(ServerLevel level) {
		if (vaultPos != null && level.getBlockState(vaultPos).is(LootItems.HERO_VAULT)) {
			level.setBlock(vaultPos, Blocks.AIR.defaultBlockState(), 3);
			level.sendParticles(ParticleTypes.POOF, vaultPos.getX() + 0.5, vaultPos.getY() + 0.5, vaultPos.getZ() + 0.5, 20, 0.4, 0.4, 0.4, 0.02);
			level.playSound(null, vaultPos, SoundEvents.VAULT_DEACTIVATE, SoundSource.BLOCKS, 0.8f, 1.0f);
		}
		for (BlockPos p : deco) {
			BlockState s = level.getBlockState(p);
			if (s.is(Blocks.RED_CARPET) || s.is(Blocks.WHITE_CARPET) || s.is(Blocks.CAMPFIRE) || s.is(Blocks.LANTERN) || s.is(Blocks.SPRUCE_SLAB)
					|| s.is(Blocks.SPRUCE_TRAPDOOR)) {
				level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
			}
		}
		deco.clear();
		discard();
	}

	@Override
	public boolean isAlwaysTicking() {
		return com.projecthero.mod.entity.GiantTracking.alwaysTicking(this, super.isAlwaysTicking());
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean isInvisible() {
		return landed() || super.isInvisible();
	}

	@Override
	public AABB getBoundingBoxForCulling() {
		return getBoundingBox().inflate(2.0, 0, 2.0).expandTowards(0, 4.0, 0);
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 160 * 160;
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
		entityData.set(TIER, Math.max(1, Math.min(4, tag.getInt("Tier"))));
		entityData.set(FACING, tag.getInt("Facing"));
		entityData.set(LANDED, tag.getBoolean("Landed"));
		landedTicks = tag.getInt("LandedTicks");
		openedTicks = tag.contains("OpenedTicks") ? tag.getInt("OpenedTicks") : -1;
		if (tag.contains("VaultPos")) {
			vaultPos = BlockPos.of(tag.getLong("VaultPos"));
		}
		deco.clear();
		for (long l : tag.getLongArray("Deco")) {
			deco.add(BlockPos.of(l));
		}
		decoVariant = tag.contains("DecoVariant") ? tag.getInt("DecoVariant") : -1;
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
		tag.putInt("Tier", tier());
		tag.putInt("Facing", entityData.get(FACING));
		tag.putBoolean("Landed", landed());
		tag.putInt("LandedTicks", landedTicks);
		if (openedTicks >= 0) {
			tag.putInt("OpenedTicks", openedTicks);
		}
		if (vaultPos != null) {
			tag.putLong("VaultPos", vaultPos.asLong());
		}
		if (!deco.isEmpty()) {
			tag.putLongArray("Deco", deco.stream().mapToLong(BlockPos::asLong).toArray());
		}
		tag.putInt("DecoVariant", decoVariant);
	}
}
