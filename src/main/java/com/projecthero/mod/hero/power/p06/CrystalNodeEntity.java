package com.projecthero.mod.hero.power.p06;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.revamp.BatchBEntities;

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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.13.22 Crystalkinesis signature -- <b>plant crystal nodes, then shatter them.</b>
 *
 * <p>A node is a small amethyst cluster growing out of whatever surface a Crystal Shard struck (drawn by
 * {@code CrystalNodeRenderer} as the real amethyst-cluster block model, turned to face out of that surface). It lasts
 * 30 s, is never saved with the world, and belongs to its caster: Shatter (G) detonates every one of them, Refract (N)
 * splits them, Crystal Eruption (Z) sets them all off too. At most {@value #MAX_NODES} ordinary nodes per caster --
 * planting another cracks the oldest.
 *
 * <p>A <b>Resonance Spire</b> (H) is the same entity flagged {@link #SPIRE}: drawn three times the size on a budding
 * base, it lives 12 s and fires shards at hostile creatures it can see.
 */
public class CrystalNodeEntity extends Entity {
	public static final int MAX_NODES = 8;
	public static final int NODE_LIFE = 30 * 20;
	public static final int SPIRE_LIFE = 12 * 20;
	public static final double OWNED_RANGE = 64.0;
	private static final double SPIRE_RANGE = 14.0;
	private static final int SPIRE_FIRE_TICKS = 12;
	private static final float SPIRE_DAMAGE = 8.0f;
	private static final BlockParticleOption DUST =
			new BlockParticleOption(ParticleTypes.BLOCK, Blocks.AMETHYST_BLOCK.defaultBlockState());

	private static final EntityDataAccessor<Integer> FACING =
			SynchedEntityData.defineId(CrystalNodeEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> SPIRE =
			SynchedEntityData.defineId(CrystalNodeEntity.class, EntityDataSerializers.BOOLEAN);

	private UUID owner;
	private int life;
	private int maxLife = NODE_LIFE;
	private int fireCd = SPIRE_FIRE_TICKS;

	public CrystalNodeEntity(EntityType<? extends CrystalNodeEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		setNoGravity(true);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(FACING, Direction.UP.get3DDataValue());
		builder.define(SPIRE, false);
	}

	public Direction facing() {
		return Direction.from3DDataValue(entityData.get(FACING));
	}

	public boolean isSpire() {
		return entityData.get(SPIRE);
	}

	public UUID owner() {
		return owner;
	}

	public boolean ownedBy(ServerPlayer p) {
		return owner != null && owner.equals(p.getUUID());
	}

	/** Ticks left before it cracks on its own (for the renderer's fade; approximate on the client). */
	public int lifeLeft() {
		return maxLife - life;
	}

	// ---------------- spawning / ownership ----------------

	/**
	 * Grows a node (or a spire) with its base at {@code bottomCenter}, growing toward {@code facing}. Enforces the
	 * per-caster cap by cracking the oldest ordinary node.
	 */
	public static CrystalNodeEntity spawn(ServerLevel level, ServerPlayer owner, Vec3 bottomCenter, Direction facing,
			boolean spire) {
		if (!spire) {
			List<CrystalNodeEntity> mine = owned(owner, false);
			while (mine.size() >= MAX_NODES) {
				CrystalNodeEntity oldest = mine.remove(0);
				oldest.crack();
			}
		}
		CrystalNodeEntity node = new CrystalNodeEntity(BatchBEntities.CRYSTAL_NODE, level);
		node.owner = owner.getUUID();
		node.maxLife = spire ? SPIRE_LIFE : NODE_LIFE;
		node.entityData.set(FACING, facing.get3DDataValue());
		node.entityData.set(SPIRE, spire);
		node.moveTo(bottomCenter.x, bottomCenter.y, bottomCenter.z, 0.0f, 0.0f);
		level.addFreshEntity(node);
		level.sendParticles(DUST, bottomCenter.x, bottomCenter.y + 0.4, bottomCenter.z, spire ? 30 : 10, 0.25, 0.3, 0.25, 0.05);
		level.playSound(null, bottomCenter.x, bottomCenter.y, bottomCenter.z,
				spire ? SoundEvents.AMETHYST_CLUSTER_PLACE : SoundEvents.SMALL_AMETHYST_BUD_PLACE, SoundSource.PLAYERS,
				1.0f, spire ? 0.6f : 1.2f);
		return node;
	}

	/** The caster's live nodes near them, oldest first. {@code includeSpires} also returns Resonance Spires. */
	public static List<CrystalNodeEntity> owned(ServerPlayer p, boolean includeSpires) {
		List<CrystalNodeEntity> out = new ArrayList<>(p.level().getEntitiesOfClass(CrystalNodeEntity.class,
				p.getBoundingBox().inflate(OWNED_RANGE),
				n -> n.isAlive() && n.ownedBy(p) && (includeSpires || !n.isSpire())));
		out.sort(Comparator.comparingInt((CrystalNodeEntity n) -> n.life).reversed());
		return out;
	}

	// ---------------- tick ----------------

	@Override
	public void tick() {
		super.tick();
		if (!(level() instanceof ServerLevel server)) {
			return;
		}
		life++;
		if (life >= maxLife) {
			crack();
			return;
		}
		ServerPlayer p = owner != null && server.getPlayerByUUID(owner) instanceof ServerPlayer sp ? sp : null;
		if (p == null || !p.isAlive()) {
			if (life % 20 == 0) {
				crack(); // the caster left, died or changed dimension -- their crystals go with them
			}
			return;
		}
		if (life % 20 == 0) {
			server.sendParticles(ParticleTypes.END_ROD, getX(), getY() + (isSpire() ? 1.4 : 0.4), getZ(), 1,
					0.15, 0.15, 0.15, 0.005);
		}
		if (isSpire() && --fireCd <= 0) {
			fireCd = SPIRE_FIRE_TICKS;
			LivingEntity target = spireTarget(server, p);
			if (target != null) {
				fireAt(server, p, target);
			}
		}
	}

	private LivingEntity spireTarget(ServerLevel server, ServerPlayer p) {
		Vec3 eye = position().add(0, 1.6, 0);
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, new AABB(eye, eye).inflate(SPIRE_RANGE),
				e -> com.projecthero.mod.combat.HeroTargets.isHostile(p, e))) { // v0.14.20: turret, rule 2
			double d = e.distanceToSqr(eye);
			if (d > SPIRE_RANGE * SPIRE_RANGE || d >= bestD) {
				continue;
			}
			Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0);
			if (server.clip(new ClipContext(eye, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this))
					.getType() != HitResult.Type.MISS) {
				continue;
			}
			best = e;
			bestD = d;
		}
		return best;
	}

	private void fireAt(ServerLevel server, ServerPlayer p, LivingEntity target) {
		Vec3 from = position().add(0, 1.7, 0);
		Vec3 to = target.position().add(0, target.getBbHeight() * 0.55, 0);
		Vec3 dir = to.subtract(from).normalize();
		CrystalShardEntity shard = new CrystalShardEntity(server, p, SPIRE_DAMAGE, false);
		shard.setPos(from.x + dir.x * 0.6, from.y + dir.y * 0.6, from.z + dir.z * 0.6);
		shard.shoot(dir.x, dir.y, dir.z, 2.0f, 0.5f);
		server.addFreshEntity(shard);
		server.sendParticles(DUST, from.x, from.y, from.z, 6, 0.15, 0.15, 0.15, 0.05);
		server.playSound(null, from.x, from.y, from.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.9f, 1.6f);
	}

	// ---------------- endings ----------------

	/** Quietly cracks apart (expired / capped / orphaned). */
	public void crack() {
		if (level() instanceof ServerLevel server && isAlive()) {
			server.sendParticles(DUST, getX(), getY() + 0.3, getZ(), 12, 0.2, 0.2, 0.2, 0.05);
			server.playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 0.6f, 1.4f);
		}
		discard();
	}

	/**
	 * Detonates: {@code damage} to every enemy of {@code p} within {@code radius}, a burst of amethyst and a glassy
	 * crack. A spire detonates half again as hard. Returns how many creatures it hit.
	 */
	public int shatter(ServerPlayer p, float damage, double radius) {
		if (!(level() instanceof ServerLevel server) || !isAlive()) {
			return 0;
		}
		float dmg = isSpire() ? damage * 1.5f : damage;
		double r = isSpire() ? radius * 1.4 : radius;
		Vec3 c = position().add(0, 0.4, 0);
		int hits = 0;
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, c, r)) {
			if (AbilityHelpers.hurtBurst(p, e, dmg)) {
				hits++;
			}
			AbilityHelpers.knockbackFrom(e, c, 0.8);
			AbilityHelpers.push(e, new Vec3(0, 0.3, 0));
		}
		server.sendParticles(DUST, c.x, c.y, c.z, 40, r * 0.35, 0.4, r * 0.35, 0.18);
		server.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 10, 0.4, 0.4, 0.4, 0.12);
		server.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		server.playSound(null, c.x, c.y, c.z, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 1.4f, 0.7f);
		server.playSound(null, c.x, c.y, c.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.9f, 1.3f);
		discard();
		return hits;
	}

	// ---------------- entity plumbing ----------------

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double d) {
		return d < 96 * 96;
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
		// never saved (the type is noSave); nothing to read
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
	}
}
