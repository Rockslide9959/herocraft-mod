package com.projecthero.mod.titanshifter.entity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.projecthero.mod.titanshifter.TitanBoulders;
import com.projecthero.mod.titanshifter.TitanCombat;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7 (user): a real flying rock of the Beast Titan -- a chunk of debris (Rock Scatter, Pitcher's Fastball, the
 * shards of a shattered boulder) or a whole boulder (Boulder Throw, Boulder Volley). Drawn client-side as a cluster of
 * the block it was torn out of ({@code TitanRockRenderer}), tumbling in flight. Both sides fly it (gravity + drag) so it
 * moves smoothly; only the server hits things. A chunk hits the first mob it touches (a shared {@link Volley} limits how
 * many of one throw's chunks may hurt one target) or the first block, then bursts into block particles and dust. A
 * boulder bursts through {@link TitanBoulders#impact}. Never saved; a few seconds at most.
 */
public class TitanRockEntity extends Entity {
	private static final EntityDataAccessor<Integer> BLOCK = SynchedEntityData.defineId(TitanRockEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Float> SCALE = SynchedEntityData.defineId(TitanRockEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(TitanRockEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Float> GRAVITY = SynchedEntityData.defineId(TitanRockEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> DRAG = SynchedEntityData.defineId(TitanRockEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Integer> SEED = SynchedEntityData.defineId(TitanRockEntity.class, EntityDataSerializers.INT);

	/** A chunk of debris (a jagged rock of a few cubes). */
	public static final int KIND_CHUNK = 0;
	/** A whole boulder (a big cluster of stone blocks). */
	public static final int KIND_BOULDER = 1;

	/** One throw's chunks: how many of them may hurt each target, and who has been hit how often. */
	public static final class Volley {
		final int perTarget;
		final Map<Integer, Integer> hits = new HashMap<>();

		public Volley(int perTarget) {
			this.perTarget = perTarget;
		}

		public int hitsOn(LivingEntity e) {
			return hits.getOrDefault(e.getId(), 0);
		}
	}

	// ---- server only ----
	private UUID ownerId;
	private int formId = -1;
	private float damage;
	private double knock = 0.4;
	private Volley volley;
	private int maxAge = 60;
	/** Boulder: the burst radius (full damage), shrapnel to twice that. */
	private double radius;
	private int age;

	public TitanRockEntity(EntityType<? extends TitanRockEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	/** A rock of {@code block}, {@code scale} blocks across, flying at {@code velocity} from {@code at}. */
	public static TitanRockEntity create(ServerLevel level, int kind, BlockState block, float scale, Vec3 at, Vec3 velocity, float gravity) {
		TitanRockEntity r = new TitanRockEntity(TitanShifterEntities.TITAN_ROCK, level);
		r.entityData.set(KIND, kind);
		r.entityData.set(BLOCK, Block.getId(block));
		r.entityData.set(SCALE, scale);
		r.entityData.set(GRAVITY, gravity);
		r.entityData.set(DRAG, kind == KIND_BOULDER ? 0.995f : 0.985f);
		r.setPos(at.x, at.y, at.z);
		r.setDeltaMovement(velocity);
		r.xo = at.x;
		r.yo = at.y;
		r.zo = at.z;
		return r;
	}

	/** Who threw it (hits are theirs), its Titan, damage per hit and knockback. Zero damage = cosmetic. */
	public TitanRockEntity thrownBy(ServerPlayer owner, TitanFormEntity form, float damage, double knock, Volley volley) {
		this.ownerId = owner == null ? null : owner.getUUID();
		this.formId = form == null ? -1 : form.getId();
		this.damage = damage;
		this.knock = knock;
		this.volley = volley;
		return this;
	}

	public TitanRockEntity boulderRadius(double radius) {
		this.radius = radius;
		return this;
	}

	/** The model's shape seed (a boulder keeps the shape it had in the hand: the Titan's id). */
	public TitanRockEntity seed(int seed) {
		entityData.set(SEED, seed);
		return this;
	}

	public int shapeSeed() {
		int s = entityData.get(SEED);
		return s != 0 ? s : getId();
	}

	/** A pitched stone: hardly slowed by the air. */
	public TitanRockEntity fast() {
		entityData.set(DRAG, 0.997f);
		return this;
	}

	/** Server: the id of the Titan that threw it (-1 if none). */
	public int formId() {
		return formId;
	}

	/** A boulder's burst radius (full damage), or {@code fallback} when none was set. */
	public double boulderRadiusOr(double fallback) {
		return radius > 0.0 ? radius : fallback;
	}

	public TitanRockEntity maxAge(int ticks) {
		this.maxAge = ticks;
		return this;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(BLOCK, Block.getId(Blocks.COBBLESTONE.defaultBlockState()));
		builder.define(SCALE, 0.6f);
		builder.define(KIND, KIND_CHUNK);
		builder.define(GRAVITY, 0.06f);
		builder.define(DRAG, 0.985f);
		builder.define(SEED, 0);
	}

	public BlockState block() {
		return Block.stateById(entityData.get(BLOCK));
	}

	public float scale() {
		return entityData.get(SCALE);
	}

	public int kind() {
		return entityData.get(KIND);
	}

	public boolean isBoulder() {
		return kind() == KIND_BOULDER;
	}

	public float damage() {
		return damage;
	}

	@Override
	public void tick() {
		super.tick();
		Vec3 v = getDeltaMovement();
		Vec3 from = position();
		Vec3 to = from.add(v);
		if (level() instanceof ServerLevel sl) {
			age++;
			if (serverStep(sl, from, to)) {
				return;
			}
			if (age >= maxAge || to.y < sl.getMinBuildHeight()) {
				if (isBoulder()) {
					TitanBoulders.impact(this, owner(sl), form(sl), to);
				} else {
					burst(sl, to, false);
				}
				discard();
				return;
			}
			if (isBoulder() && age % 2 == 0) {
				TitanBoulders.fx(sl, new BlockParticleOption(ParticleTypes.FALLING_DUST, block()), to.x, to.y, to.z, 2, scale() * 0.3, scale() * 0.3,
						scale() * 0.3, 0.0);
			} else if (!isBoulder() && scale() > 0.7f && age % 4 == 0) {
				TitanBoulders.fx(sl, new BlockParticleOption(ParticleTypes.FALLING_DUST, block()), to.x, to.y, to.z, 1, 0.1, 0.1, 0.1, 0.0);
			}
		}
		setPos(to.x, to.y, to.z);
		float g = entityData.get(GRAVITY);
		double drag = entityData.get(DRAG);
		setDeltaMovement(v.x * drag, v.y * drag - g, v.z * drag);
	}

	private ServerPlayer owner(ServerLevel sl) {
		return ownerId == null ? null : sl.getServer().getPlayerList().getPlayer(ownerId);
	}

	private TitanFormEntity form(ServerLevel sl) {
		Entity fe = formId < 0 ? null : sl.getEntity(formId);
		return fe instanceof TitanFormEntity f && f.isAlive() ? f : null;
	}

	/** Hits along this tick's path. True if the rock is gone. */
	private boolean serverStep(ServerLevel sl, Vec3 from, Vec3 to) {
		ServerPlayer owner = owner(sl);
		TitanFormEntity form = form(sl);
		double r = scale() * 0.5 + 0.25;
		if (owner != null && (damage > 0.0f || isBoulder())) {
			LivingEntity first = null;
			double best = Double.MAX_VALUE;
			for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new AABB(from, to).inflate(r + 0.5),
					e -> e.isAlive() && e != form && e != owner && com.projecthero.mod.combat.HeroTargets.canHarm(owner, e))) {
				if (volley != null && volley.hitsOn(e) >= volley.perTarget) {
					continue; // this throw has hurt it enough; the chunk flies past
				}
				AABB grown = e.getBoundingBox().inflate(r);
				double d;
				if (grown.contains(from)) {
					d = 0.0;
				} else {
					var clip = grown.clip(from, to);
					if (clip.isEmpty()) {
						continue;
					}
					d = clip.get().distanceToSqr(from);
				}
				if (d < best) {
					best = d;
					first = e;
				}
			}
			if (first != null) {
				Vec3 at = first.position().add(0, first.getBbHeight() * 0.5, 0);
				if (isBoulder()) {
					TitanBoulders.impact(this, owner, form, at);
				} else {
					if (volley != null) {
						volley.hits.merge(first.getId(), 1, Integer::sum);
					}
					if (form != null) {
						TitanCombat.hit(form, owner, first, damage, knock, knock * 0.25, from);
					} else {
						com.projecthero.mod.hero.power.AbilityHelpers.hurtBurst(owner, first, damage);
					}
					burst(sl, from.add(to.subtract(from).scale(0.5)), true);
				}
				discard();
				return true;
			}
		}
		var hit = sl.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this));
		if (hit.getType() != HitResult.Type.MISS) {
			if (isBoulder()) {
				TitanBoulders.impact(this, owner, form, hit.getLocation());
			} else {
				burst(sl, hit.getLocation(), false);
			}
			discard();
			return true;
		}
		return false;
	}

	/** A chunk breaks up: a puff of its block, dust and a crumble. */
	public void burst(ServerLevel sl, Vec3 at, boolean onMob) {
		float s = scale();
		BlockState st = block();
		int n = Math.max(4, Math.round(10 * s * s + 4));
		TitanBoulders.fx(sl, new BlockParticleOption(ParticleTypes.BLOCK, st), at.x, at.y + 0.1, at.z, n, s * 0.35, s * 0.25, s * 0.35, 0.15);
		TitanBoulders.fx(sl, new BlockParticleOption(ParticleTypes.FALLING_DUST, st), at.x, at.y + 0.2, at.z, Math.max(1, n / 4), s * 0.4,
				s * 0.2, s * 0.4, 0.0);
		if (s > 0.6f) {
			TitanBoulders.fx(sl, ParticleTypes.POOF, at.x, at.y + 0.2, at.z, 1 + (int) s, s * 0.3, 0.1, s * 0.3, 0.02);
		}
		// one crumble per few chunks so a spray of 20 does not stack 20 sounds
		if (onMob || s > 0.75f || random.nextInt(3) == 0) {
			sl.playSound(null, at.x, at.y, at.z, st.getSoundType().getBreakSound(), SoundSource.HOSTILE, 0.6f + s * 0.6f,
					1.2f - Math.min(0.6f, s * 0.4f) + random.nextFloat() * 0.15f);
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
	public boolean shouldRenderAtSqrDistance(double d2) {
		double r = 96.0 * Math.max(1.0, scale());
		return d2 < r * r;
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
		// never saved
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
		// never saved
	}
}
