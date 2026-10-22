package com.projecthero.mod.titanshifter.entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.projecthero.mod.titanshifter.TitanShifterConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7 (user, Titan fixes): one 1 x 4 column of the human-form Crystal Wall. The wall used to be conjured blocks, so it
 * did nothing on a server with "ability terrain damage" off. Now it is four of these entities, which never touch a
 * block: each is solid ({@link #canBeCollidedWith()} -- players, mobs and Titans walk into it like a boat or a shulker)
 * and pickable, so arrows, tridents and fireballs strike it and are stopped (it shrugs the hit off: {@link #hurt} returns
 * false, so an arrow bounces away). The client draws four amethyst blocks that grow out of the ground and crack apart
 * at the end ({@code TitanCrystalWallRenderer}).
 *
 * <p>Why entities and not "temporary blocks only in air": blocks need the terrain permission the server turned off,
 * would persist if the server stopped mid-wall, and could be duplicated / griefed by pistons, water and Endermen. These
 * are never saved ({@code noSave}), cannot be pushed, and each shifter has at most one wall ({@link #BY_OWNER}, cleared by
 * {@code ServerStateReset}): a new wall replaces the old one.
 */
public class TitanCrystalWallEntity extends Entity {
	private static final EntityDataAccessor<Integer> DATA_LIFE =
			SynchedEntityData.defineId(TitanCrystalWallEntity.class, EntityDataSerializers.INT);
	/** The column's place in the wall (0..3) -- staggers its rise so the wall grows from one side. */
	private static final EntityDataAccessor<Integer> DATA_INDEX =
			SynchedEntityData.defineId(TitanCrystalWallEntity.class, EntityDataSerializers.INT);
	/** Blocks tall. */
	public static final int HEIGHT = 4;
	/** The crack-apart at the end (ticks). */
	public static final int BREAK_TICKS = 12;

	/** Each shifter's live wall columns (at most one wall). */
	private static final Map<UUID, List<TitanCrystalWallEntity>> BY_OWNER = new HashMap<>();

	private int age;

	public TitanCrystalWallEntity(EntityType<? extends TitanCrystalWallEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		this.setNoGravity(true);
	}

	/** ServerStateReset: forget every wall (they are never saved, so they went with the world). */
	public static void clearSessionState() {
		BY_OWNER.clear();
	}

	/** Live wall columns of {@code owner} (gametests). */
	public static List<TitanCrystalWallEntity> wallOf(UUID owner) {
		List<TitanCrystalWallEntity> l = BY_OWNER.get(owner);
		if (l == null) {
			return List.of();
		}
		l.removeIf(Entity::isRemoved);
		return List.copyOf(l);
	}

	public int life() {
		return this.entityData.get(DATA_LIFE);
	}

	public int index() {
		return this.entityData.get(DATA_INDEX);
	}

	/**
	 * Raises a 4-wide, 4-tall wall two blocks in front of {@code player}, facing them, each column standing on the ground
	 * under it (it steps up / down a little to follow the terrain). Replaces the player's last wall. Returns how many
	 * columns stood; 0 if there was no room at all (nothing is spawned then).
	 */
	public static int raise(ServerPlayer player, int life) {
		ServerLevel level = (ServerLevel) player.level();
		List<Vec3> spots = spots(level, player);
		if (spots.isEmpty()) {
			return 0;
		}
		dismiss(player.getUUID());
		List<TitanCrystalWallEntity> wall = new ArrayList<>();
		Direction facing = player.getDirection();
		int i = 0;
		for (Vec3 at : spots) {
			TitanCrystalWallEntity c = TitanShifterEntities.TITAN_CRYSTAL_WALL.create(level);
			if (c == null) {
				continue;
			}
			c.moveTo(at.x, at.y, at.z, facing.toYRot(), 0.0f);
			c.entityData.set(DATA_LIFE, Math.max(BREAK_TICKS + 10, life));
			c.entityData.set(DATA_INDEX, i++);
			if (level.addFreshEntity(c)) {
				wall.add(c);
			}
		}
		if (!wall.isEmpty()) {
			BY_OWNER.put(player.getUUID(), wall);
		}
		return wall.size();
	}

	/** Where the columns stand: two blocks ahead, one to the left and two to the right of the player's line (the old block wall's cells). */
	public static List<Vec3> spots(ServerLevel level, ServerPlayer player) {
		Direction facing = player.getDirection();
		Direction side = facing.getClockWise();
		BlockPos front = player.blockPosition().relative(facing, 2);
		List<Vec3> out = new ArrayList<>();
		for (int w = -1; w <= 2; w++) {
			BlockPos col = front.relative(side, w);
			Integer y = groundAt(level, col);
			if (y != null) {
				out.add(new Vec3(col.getX() + 0.5, y, col.getZ() + 0.5));
			}
		}
		return out;
	}

	/** The floor a column stands on near {@code col}: up to 2 blocks up out of a slope, up to 3 down off a ledge; null if buried. */
	private static Integer groundAt(ServerLevel level, BlockPos col) {
		BlockPos.MutableBlockPos p = col.mutable();
		for (int up = 0; up <= 2; up++) {
			p.set(col.getX(), col.getY() + up, col.getZ());
			if (!solid(level, p)) {
				// found air: drop to the floor below it (at most 3 more)
				for (int down = 0; down < 3 && !solid(level, p.below()); down++) {
					p.move(0, -1, 0);
				}
				return p.getY();
			}
		}
		return null;
	}

	private static boolean solid(ServerLevel level, BlockPos pos) {
		return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
	}

	/** Breaks {@code owner}'s wall now (a new wall, or the gametests). */
	public static void dismiss(UUID owner) {
		List<TitanCrystalWallEntity> old = BY_OWNER.remove(owner);
		if (old != null) {
			for (TitanCrystalWallEntity c : old) {
				if (!c.isRemoved()) {
					c.shatter();
				}
			}
		}
	}

	private void shatter() {
		if (level() instanceof ServerLevel sl) {
			sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.AMETHYST_BLOCK.defaultBlockState()), getX(), getY() + HEIGHT * 0.5,
					getZ(), 40, 0.35, HEIGHT * 0.3, 0.35, 0.15);
			sl.playSound(null, getX(), getY() + 1.0, getZ(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 1.2f, 0.7f);
		}
		discard();
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_LIFE, 500);
		builder.define(DATA_INDEX, 0);
	}

	@Override
	public void tick() {
		super.tick();
		if (level() instanceof ServerLevel sl) {
			age++;
			if (age == 1 + index() * 2) {
				sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.AMETHYST_BLOCK.defaultBlockState()), getX(), getY() + 0.2, getZ(),
						18, 0.4, 0.1, 0.4, 0.1);
			}
			if (age == life() - BREAK_TICKS) {
				sl.playSound(null, getX(), getY() + 1.0, getZ(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 1.0f, 0.6f + index() * 0.05f);
			}
			if (age >= life()) {
				sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.AMETHYST_BLOCK.defaultBlockState()), getX(), getY() + HEIGHT * 0.5,
						getZ(), 30, 0.35, HEIGHT * 0.3, 0.35, 0.12);
				discard();
			}
		}
	}

	/** Hits glance off (arrows bounce away); a chime and a few chips show it. */
	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (level() instanceof ServerLevel sl && !isRemoved()) {
			Vec3 at = source.getSourcePosition() != null ? source.getSourcePosition() : position().add(0, HEIGHT * 0.5, 0);
			sl.sendParticles(ParticleTypes.END_ROD, at.x, Math.max(getY(), Math.min(getY() + HEIGHT, at.y)), at.z, 4, 0.2, 0.2, 0.2, 0.02);
			sl.playSound(null, getX(), getY() + 1.5, getZ(), SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 1.0f, 0.9f + random.nextFloat() * 0.3f);
		}
		return false;
	}

	@Override
	public boolean isPickable() {
		return !isRemoved();
	}

	@Override
	public boolean canBeCollidedWith() {
		return true;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public void push(Entity entity) {
	}

	@Override
	public void push(double x, double y, double z) {
	}

	@Override
	public boolean isPushedByFluid() {
		return false;
	}

	@Override
	public boolean ignoreExplosion(net.minecraft.world.level.Explosion explosion) {
		return true;
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
		// never saved
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
		// never saved
	}

	/** The default wall length from the config (25 s). */
	public static int defaultLife() {
		return Math.max(40, TitanShifterConfig.human().wallTicks);
	}
}
