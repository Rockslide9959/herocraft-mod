package com.projecthero.mod.hero.power.p05;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.projecthero.mod.hero.power.AbilityHelpers;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.5 Geokinesis: the shapes of earth the power raises -- one short-lived, never-saved, collision-free entity drawn
 * by {@code GeoFormRenderer} in the texture of the ground it came out of. It never changes a block, so nothing is left to
 * clean up when it ends.
 *
 * <ul>
 * <li>{@link #GRIP} -- Earth Grip: two stone hands burst up round the target's legs and hold it (the root is applied
 * here, every tick, for {@code life} ticks).</li>
 * <li>{@link #PILLAR} -- Tectonic Pillar: a column shoots up out of the ground, stands, and sinks back.</li>
 * <li>{@link #SPIKE} -- Earth Spikes: one jagged spike stabbing up and retracting.</li>
 * <li>{@link #QUAKE} -- Earthquake: a ripple of heaving ground running out to {@code size} blocks; it hits whatever the
 * wave front passes (see {@link GeokinesisHandlers#quakeHit}).</li>
 * <li>{@link #SINK} -- Sinkhole: the ground caving in, a ring of rubble tipping down into the hole.</li>
 * <li>v0.16.6 hold moves: {@link #BURROW} ({@link GeoBurrowEntity}, the mound you travel under), {@link #FISSURE} (the
 * jagged lips of the crack, opening and grinding shut -- the crack itself is real, in {@link GeoTerrain}'s ledger),
 * {@link #QUICKSAND} (the churning sand surface over the pool; holds what is in it) and {@link #AVALANCHE} (the rubble
 * piling up where the rocks land).</li>
 * </ul>
 */
public class GeoFormEntity extends Entity {
	public static final int GRIP = 0;
	public static final int PILLAR = 1;
	public static final int SPIKE = 2;
	public static final int QUAKE = 3;
	public static final int SINK = 4;
	/** v0.16.6 Burrow: the travelling mound ({@link GeoBurrowEntity}). */
	public static final int BURROW = 5;
	/** v0.16.6 Fissure: the lips of the crack; {@code size} = its length. */
	public static final int FISSURE = 6;
	/** v0.16.6 Quicksand: the pool's surface; {@code size} = its diameter. */
	public static final int QUICKSAND = 7;
	/** v0.16.6 Avalanche: the rubble heap in the target area; {@code size} = its diameter. */
	public static final int AVALANCHE = 8;

	private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(GeoFormEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> BLOCK = SynchedEntityData.defineId(GeoFormEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> TARGET = SynchedEntityData.defineId(GeoFormEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Float> SIZE = SynchedEntityData.defineId(GeoFormEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(GeoFormEntity.class, EntityDataSerializers.INT);

	private UUID owner;
	private Vec3 anchor;
	private boolean pin = true;
	private final Set<Integer> struck = new HashSet<>();
	/** A ripple that only shows (the Burrow burst does its own damage). */
	private boolean harmless;
	/** Fissure: the surface cell (first air) over each carved column, for lifting out whatever is still down there. */
	private final java.util.List<net.minecraft.core.BlockPos> columns = new java.util.ArrayList<>();
	/** Fissure / Quicksand: the tick of the form's life the ground goes back on (whoever is inside is lifted out first). */
	private int closeAt = -1;

	public GeoFormEntity(EntityType<? extends GeoFormEntity> type, Level level) {
		super(type, level);
		noPhysics = true;
	}

	public static GeoFormEntity create(ServerLevel level, int kind, ServerPlayer owner, Vec3 at, BlockState block, float size, int life) {
		return init(new GeoFormEntity(GeoEntityTypes.GEO_FORM, level), kind, owner, at, block, size, life);
	}

	static <T extends GeoFormEntity> T init(T f, int kind, ServerPlayer owner, Vec3 at, BlockState block, float size, int life) {
		f.entityData.set(KIND, kind);
		f.entityData.set(BLOCK, Block.getId(block));
		f.entityData.set(SIZE, size);
		f.entityData.set(LIFE, life);
		((GeoFormEntity) f).owner = owner == null ? null : owner.getUUID();
		f.setPos(at.x, at.y, at.z);
		f.setYRot(owner == null ? 0 : owner.getYRot());
		return f;
	}

	public GeoFormEntity target(Entity e) {
		entityData.set(TARGET, e == null ? -1 : e.getId());
		anchor = e == null ? null : e.position();
		return this;
	}

	/** Earth Grip: whether the target is pinned to the spot (false for bosses: they are only slowed). */
	public GeoFormEntity pin(boolean pin) {
		this.pin = pin;
		return this;
	}

	/** A ripple that only shows: nothing it passes is hit (the Burrow burst does its own damage). */
	public GeoFormEntity harmless() {
		this.harmless = true;
		return this;
	}

	/** Fissure / Quicksand: the ground goes back on tick {@code tick} of the form's life. */
	public GeoFormEntity closeAt(int tick) {
		this.closeAt = tick;
		return this;
	}

	/** Fissure: remembers a carved column (its surface cell). */
	public void addColumn(net.minecraft.core.BlockPos surface) {
		columns.add(surface.immutable());
	}

	public java.util.List<net.minecraft.core.BlockPos> columns() {
		return columns;
	}

	protected UUID ownerId() {
		return owner;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(KIND, PILLAR);
		builder.define(BLOCK, Block.getId(Blocks.STONE.defaultBlockState()));
		builder.define(TARGET, -1);
		builder.define(SIZE, 1.0f);
		builder.define(LIFE, 40);
	}

	public int kind() {
		return entityData.get(KIND);
	}

	public BlockState block() {
		BlockState s = Block.stateById(entityData.get(BLOCK));
		return s.isAir() ? Blocks.STONE.defaultBlockState() : s;
	}

	public float size() {
		return entityData.get(SIZE);
	}

	public int life() {
		return entityData.get(LIFE);
	}

	public int targetId() {
		return entityData.get(TARGET);
	}

	/** 0..1 of the form's life gone (for the renderer). */
	public float progress(float partial) {
		return Math.min(1.0f, (tickCount + partial) / Math.max(1.0f, life()));
	}

	@Override
	public void tick() {
		super.tick();
		if (!(level() instanceof ServerLevel server)) {
			return;
		}
		if (tickCount >= life()) {
			discard();
			return;
		}
		switch (kind()) {
			case GRIP -> gripTick(server);
			case QUAKE -> {
				if (!harmless) {
					quakeTick(server);
				}
			}
			case QUICKSAND -> GeokinesisHandlers.quicksandTick(server, this, ownerPlayer(server), tickCount >= closeAt && closeAt >= 0);
			case FISSURE -> GeokinesisHandlers.fissureTick(server, this, ownerPlayer(server), tickCount == closeAt);
			default -> {
			}
		}
	}

	protected ServerPlayer ownerPlayer(ServerLevel server) {
		return owner == null ? null : server.getServer().getPlayerList().getPlayer(owner);
	}

	/** Earth Grip: the target is held at the spot it was grabbed, no jumping, for the hands' whole life. */
	private void gripTick(ServerLevel server) {
		Entity e = server.getEntity(targetId());
		if (!(e instanceof LivingEntity t) || !t.isAlive()) {
			discard();
			return;
		}
		if (anchor == null) {
			anchor = t.position();
		}
		boolean hold = pin && (!(t instanceof Player) || com.projecthero.mod.hero.HeroConfig.get().abilityHardCrowdControlOnPlayers);
		if (hold) {
			if (t.position().distanceToSqr(anchor) > 0.01) {
				t.teleportTo(anchor.x, Math.min(t.getY(), anchor.y + 0.2), anchor.z);
			}
			t.setDeltaMovement(0, Math.min(0, t.getDeltaMovement().y), 0);
			t.hurtMarked = true;
			if (t instanceof Mob m) {
				m.getNavigation().stop();
			}
			if (tickCount % 10 == 0) {
				AbilityHelpers.applyControl(t, MobEffects.MOVEMENT_SLOWDOWN, 12, 9);
				AbilityHelpers.applyControl(t, MobEffects.JUMP, 12, -10); // a negative jump boost: no jumping
			}
		}
		setPos(t.getX(), anchor.y, t.getZ());
		if (tickCount % 8 == 0) {
			server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, block()), t.getX(), anchor.y + 0.2, t.getZ(), 3, 0.3,
					0.1, 0.3, 0.02);
		}
	}

	/** Earthquake: the wave runs out to {@code size} over the first 70% of the life, hitting whatever it passes. */
	private void quakeTick(ServerLevel server) {
		ServerPlayer p = ownerPlayer(server);
		if (p == null) {
			return;
		}
		float run = life() * 0.7f;
		double front = Math.min(1.0, (tickCount + 1) / run) * size();
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, position(), front + 0.5)) {
			double d = Math.sqrt(e.distanceToSqr(getX(), e.getY(), getZ()));
			if (d <= front + 0.5 && Math.abs(e.getY() - getY()) < 4.0 && struck.add(e.getId())) {
				GeokinesisHandlers.quakeHit(p, e, position());
			}
		}
	}

	/** For the gametests: run the wave to its edge now. */
	public void finishQuake() {
		if (level() instanceof ServerLevel server) {
			ServerPlayer p = ownerPlayer(server);
			while (tickCount < life() * 0.7f + 1 && p != null) {
				tickCount++;
				quakeTick(server);
			}
		}
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
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
	}
}
