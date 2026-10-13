package com.projecthero.mod.spider.web;

import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.1: one of Spider-Man's web structures -- a tightrope, a web wall or a woven web -- as a temporary entity that
 * carries its own shape ({@link WebGeometry}) instead of placing a single block. The world is never modified.
 *
 * <ul>
 *   <li><b>Owner and squad</b> can stand, walk, climb and swing on it (its collision boxes are handed to their mover by
 *       {@code EntityWebCollisionMixin}, on both sides, so nothing rubber-bands).</li>
 *   <li><b>Everyone else</b> passes into it and is stuck fast while touching it -- far worse than a cobweb -- and the
 *       owner's Spider-Sense is pinged (the web alarm).</li>
 *   <li>It fades after {@code SpiderManConfig.webs.fadeSeconds} and its owner can cut it (attack it).</li>
 *   <li>Never saved: a chunk unload or a restart simply drops it ({@link #shouldBeSaved}), so a web can never be
 *       orphaned in a save; {@link WebStructures} caps how many each player has.</li>
 * </ul>
 */
public class WebStructureEntity extends Entity {
	private static final EntityDataAccessor<Optional<UUID>> OWNER =
			SynchedEntityData.defineId(WebStructureEntity.class, EntityDataSerializers.OPTIONAL_UUID);
	private static final EntityDataAccessor<CompoundTag> SHAPE =
			SynchedEntityData.defineId(WebStructureEntity.class, EntityDataSerializers.COMPOUND_TAG);
	private static final EntityDataAccessor<Long> EXPIRES =
			SynchedEntityData.defineId(WebStructureEntity.class, EntityDataSerializers.LONG);
	private static final EntityDataAccessor<Long> BORN =
			SynchedEntityData.defineId(WebStructureEntity.class, EntityDataSerializers.LONG);

	private WebGeometry geometry;
	/** Server: the last game time this web raised an alarm (per web, throttled). */
	public long lastAlarm = Long.MIN_VALUE / 2;
	/** Server: where the last alarm was raised (null before the first). */
	public Vec3 lastAlarmAt;

	public WebStructureEntity(EntityType<? extends WebStructureEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		this.noCulling = true;
		WebStructures.track(this);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(OWNER, Optional.empty());
		builder.define(SHAPE, new CompoundTag());
		builder.define(EXPIRES, 0L);
		builder.define(BORN, 0L);
	}

	// ---------------- accessors ----------------

	public UUID owner() {
		return this.entityData.get(OWNER).orElse(null);
	}

	public void setOwner(UUID id) {
		this.entityData.set(OWNER, Optional.ofNullable(id));
	}

	public WebGeometry geometry() {
		return geometry;
	}

	public void setGeometry(WebGeometry g) {
		this.geometry = g;
		this.entityData.set(SHAPE, g.encode());
		this.setBoundingBox(makeBoundingBox());
	}

	public long expiresAt() {
		return this.entityData.get(EXPIRES);
	}

	public long bornAt() {
		return this.entityData.get(BORN);
	}

	public void setLifetime(long born, long expires) {
		this.entityData.set(BORN, born);
		this.entityData.set(EXPIRES, expires);
	}

	public byte kind() {
		return geometry == null ? -1 : geometry.kind;
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
		super.onSyncedDataUpdated(key);
		if (SHAPE.equals(key)) {
			this.geometry = WebGeometry.decode(this.entityData.get(SHAPE));
			this.setBoundingBox(makeBoundingBox());
		}
	}

	@Override
	protected AABB makeBoundingBox() {
		return geometry != null ? geometry.bounds : super.makeBoundingBox();
	}

	@Override
	public AABB getBoundingBoxForCulling() {
		return geometry != null ? geometry.bounds : super.getBoundingBoxForCulling();
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 160.0 * 160.0;
	}

	// ---------------- life ----------------

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide) {
			return;
		}
		if (geometry == null || level().getGameTime() >= expiresAt()) {
			dissolve(false);
			return;
		}
		WebStructures.serverTick((ServerLevel) level(), this);
	}

	/** Remove it with a puff of web along its strands. */
	public void dissolve(boolean cut) {
		if (level() instanceof ServerLevel sl && geometry != null) {
			int n = Math.min(40, geometry.segmentCount());
			for (int i = 0; i < n; i++) {
				Vec3 m = geometry.a(i).lerp(geometry.b(i), 0.5);
				sl.sendParticles(ParticleTypes.ITEM_COBWEB, m.x, m.y, m.z, cut ? 3 : 1, 0.2, 0.2, 0.2, 0.0);
			}
			if (geometry.wallBox != null) {
				Vec3 c = geometry.wallBox.getCenter();
				sl.sendParticles(ParticleTypes.ITEM_COBWEB, c.x, c.y, c.z, 30, 0.8, 0.8, 0.8, 0.0);
			}
		}
		discard();
	}

	/** Shape and owner only matter while the world runs: a web is never written to disk (see the class doc). */
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

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean isAttackable() {
		return false;
	}

	@Override
	public boolean ignoreExplosion(net.minecraft.world.level.Explosion explosion) {
		return true;
	}
}
