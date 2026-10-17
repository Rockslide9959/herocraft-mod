package com.projecthero.mod.miraculous.renarouge.entity;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.miraculous.renarouge.RenaRougeCombat;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.6, Shift+V (and the Mirage): an illusion wall -- a {@link #width} x {@link #height} sheet of fake blocks one block
 * thick, made of whatever the ground under it is, shimmering a little. It is not solid (anyone can walk through it) but
 * mobs cannot see through it: {@link #blocksSight} is asked by {@code RenaRougeFakeWallSightMixin} from
 * {@code LivingEntity#hasLineOfSight} for every mob, and a mob that loses sight of its target this way drops it.
 *
 * <p>The live walls are kept in {@link #ACTIVE} (so the line-of-sight check costs nothing while there are none);
 * {@code RenaRougeKit#clearSessionState} empties it.
 */
public class FakeWallEntity extends Entity {
	public static final Set<FakeWallEntity> ACTIVE = ConcurrentHashMap.newKeySet();

	private static final EntityDataAccessor<Integer> BLOCK = SynchedEntityData.defineId(FakeWallEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> WIDTH = SynchedEntityData.defineId(FakeWallEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> HEIGHT = SynchedEntityData.defineId(FakeWallEntity.class, EntityDataSerializers.INT);
	/** True: the wall runs along X (it faces north / south); false: along Z. */
	private static final EntityDataAccessor<Boolean> ALONG_X = SynchedEntityData.defineId(FakeWallEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(FakeWallEntity.class, EntityDataSerializers.INT);

	private @Nullable UUID owner;
	private int age;
	private @Nullable AABB box;

	public FakeWallEntity(EntityType<? extends FakeWallEntity> type, Level level) {
		super(type, level);
		noPhysics = true;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(BLOCK, Block.getId(Blocks.STONE.defaultBlockState()));
		builder.define(WIDTH, 5);
		builder.define(HEIGHT, 3);
		builder.define(ALONG_X, true);
		builder.define(LIFE, 200);
	}

	/**
	 * A wall centred on block {@code base} (its bottom row stands in that block), running along X ({@code alongX}) or Z,
	 * made to look like {@code look}.
	 */
	public void setup(UUID ownerId, BlockPos base, boolean alongX, int w, int h, BlockState look, int lifeTicks) {
		owner = ownerId;
		entityData.set(BLOCK, Block.getId(look));
		entityData.set(WIDTH, w);
		entityData.set(HEIGHT, h);
		entityData.set(ALONG_X, alongX);
		entityData.set(LIFE, lifeTicks);
		setPos(base.getX() + 0.5, base.getY(), base.getZ() + 0.5);
		box = null;
	}

	public BlockState look() {
		return Block.stateById(entityData.get(BLOCK));
	}

	public int width() {
		return entityData.get(WIDTH);
	}

	public int height() {
		return entityData.get(HEIGHT);
	}

	public boolean alongX() {
		return entityData.get(ALONG_X);
	}

	public int lifeTicks() {
		return entityData.get(LIFE);
	}

	public @Nullable UUID ownerId() {
		return owner;
	}

	/** The block-aligned box the wall fills. */
	public AABB wallBox() {
		if (box == null) {
			int half = width() / 2;
			BlockPos c = blockPosition();
			BlockPos a = alongX() ? c.offset(-half, 0, 0) : c.offset(0, 0, -half);
			BlockPos b = alongX() ? c.offset(width() - 1 - half, height() - 1, 0) : c.offset(0, height() - 1, width() - 1 - half);
			box = new AABB(Vec3.atLowerCornerOf(a), Vec3.atLowerCornerOf(b).add(1, 1, 1));
		}
		return box;
	}

	@Override
	public void setPos(double x, double y, double z) {
		super.setPos(x, y, z);
		box = null;
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
		super.onSyncedDataUpdated(key);
		box = null;
	}

	public int age() {
		return age;
	}

	@Override
	public void tick() {
		super.tick();
		age++;
		if (level().isClientSide) {
			return;
		}
		if (age >= lifeTicks()) {
			discard();
			return;
		}
		ACTIVE.add(this);
		if (age % 10 == 0 && level() instanceof ServerLevel sl) {
			// mobs that can no longer see who they were after give up on them
			AABB near = wallBox().inflate(24.0);
			for (Mob m : sl.getEntitiesOfClass(Mob.class, near, m -> m.getTarget() != null)) {
				LivingEntity t = m.getTarget();
				if (!(t instanceof RenaIllusionEntity) && blocks(m.getEyePosition(), t.getEyePosition())) {
					m.setTarget(null);
					m.getNavigation().stop();
				}
			}
			AABB b = wallBox();
			sl.sendParticles(RenaRougeCombat.ORANGE, b.minX + random.nextDouble() * b.getXsize(), b.minY + random.nextDouble() * b.getYsize(),
					b.minZ + random.nextDouble() * b.getZsize(), 2, 0.1, 0.1, 0.1, 0.0);
		}
	}

	@Override
	public void remove(RemovalReason reason) {
		ACTIVE.remove(this);
		super.remove(reason);
	}

	/** Does this wall stand between {@code from} and {@code to}? */
	public boolean blocks(Vec3 from, Vec3 to) {
		AABB b = wallBox();
		return b.contains(from) || b.contains(to) ? false : b.clip(from, to).isPresent();
	}

	/** Asked by the line-of-sight mixin: can {@code viewer} (a mob) not see {@code target} because of a fake wall? */
	public static boolean blocksSight(LivingEntity viewer, Entity target) {
		if (ACTIVE.isEmpty() || !(viewer instanceof Mob) || viewer.level().isClientSide || target instanceof RenaIllusionEntity) {
			return false;
		}
		Vec3 from = viewer.getEyePosition();
		Vec3 to = target.getEyePosition();
		for (FakeWallEntity w : ACTIVE) {
			if (w.isRemoved() || w.level() != viewer.level()) {
				continue;
			}
			if (w.blocks(from, to)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double d) {
		return d < 128 * 128;
	}

	@Override
	public AABB getBoundingBoxForCulling() {
		return wallBox().inflate(0.5);
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
	}
}
