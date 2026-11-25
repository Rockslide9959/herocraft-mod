package com.projecthero.mod.stormbreaker;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * v0.17.1 (user: "make the bifrost animations and vfx look better and make it leave runic markings on the ground"): the
 * Bifrost's two visible pieces.
 * <ul>
 *   <li>{@link Beam} -- the rainbow bridge itself: a swirling column of colour crashing down out of the sky onto the
 *   arrival point (and lingering, then pulling up, where the travellers left). Never saved.</li>
 *   <li>{@link Mark} -- the rune circle the bridge burns into the ground at both ends: it glows white-gold at first, then
 *   cools to a scorched pattern that stays for {@link #MARK_TICKS} (saved with the world, so it outlasts a relog).</li>
 * </ul>
 */
public final class BifrostEntities {
	public static final int BEAM_TICKS = 34;
	public static final int MARK_TICKS = 20 * 60 * 20; // 20 minutes
	public static final int MARK_GLOW_TICKS = 160;

	public static final EntityType<Beam> BEAM = Registry.register(BuiltInRegistries.ENTITY_TYPE, ProjectHeroMod.id("bifrost_beam"),
			EntityType.Builder.<Beam>of(Beam::new, MobCategory.MISC).sized(0.5f, 0.5f).fireImmune().noSave()
					.clientTrackingRange(16).updateInterval(40).build("bifrost_beam"));
	public static final EntityType<Mark> MARK = Registry.register(BuiltInRegistries.ENTITY_TYPE, ProjectHeroMod.id("bifrost_mark"),
			EntityType.Builder.<Mark>of(Mark::new, MobCategory.MISC).sized(0.5f, 0.1f).fireImmune()
					.clientTrackingRange(10).updateInterval(200).build("bifrost_mark"));

	private BifrostEntities() {
	}

	/** Loads the class (registers the types) from the mod initialiser. */
	public static void initialize() {
	}

	/** The rainbow bridge at one end of a trip. {@code arrival}: it crashes down; else it lingers and pulls back up. */
	public static class Beam extends Entity {
		/** v0.17.1 modes: the crash-down where travellers land, the linger-and-pull-up where they left. */
		public static final int ARRIVAL = 0;
		public static final int DEPARTURE = 1;
		/**
		 * v0.17.7 Bifrost Travel cinematic ({@link BifrostTravel}): LIFT -- crashes down onto the group, holds while they rise
		 * ({@link BifrostTravel#RISE_TICKS}), flares and pulls back up into the sky; DESCEND -- crashes down at the
		 * destination with the riders at its top, holds while they are lowered, flares and rolls a shockwave out on landing.
		 */
		public static final int LIFT = 2;
		public static final int DESCEND = 3;
		/** Beam ticks: LIFT lives from the strike to past the transport, DESCEND from the transport to past the landing. */
		public static final int LIFT_TICKS = BifrostTravel.TRANSPORT - BifrostTravel.STRIKE + 16;
		public static final int DESCEND_TICKS = BifrostTravel.DESCEND_TICKS + 30;
		private static final EntityDataAccessor<Integer> MODE = SynchedEntityData.defineId(Beam.class, EntityDataSerializers.INT);
		private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(Beam.class, EntityDataSerializers.FLOAT);

		public Beam(EntityType<? extends Beam> type, Level level) {
			super(type, level);
			noPhysics = true;
			noCulling = true;
		}

		public void setArrival(boolean arrival) {
			entityData.set(MODE, arrival ? ARRIVAL : DEPARTURE);
		}

		public boolean arrival() {
			return entityData.get(MODE) == ARRIVAL;
		}

		/** v0.17.7: a cinematic column ({@link #LIFT} / {@link #DESCEND}) wide enough for everyone in it. */
		public void setMode(int mode, float radius) {
			entityData.set(MODE, mode);
			entityData.set(RADIUS, radius);
		}

		public int mode() {
			return entityData.get(MODE);
		}

		/** The column's radius in blocks (the v0.17.1 trip column is 1.15). */
		public float radius() {
			return entityData.get(RADIUS);
		}

		public int life() {
			return switch (mode()) {
				case LIFT -> LIFT_TICKS;
				case DESCEND -> DESCEND_TICKS;
				default -> BEAM_TICKS;
			};
		}

		@Override
		protected void defineSynchedData(SynchedEntityData.Builder builder) {
			builder.define(MODE, ARRIVAL);
			builder.define(RADIUS, 1.15f);
		}

		@Override
		public void tick() {
			super.tick();
			if (!level().isClientSide && tickCount >= life()) {
				discard();
			}
		}

		@Override
		public boolean shouldRenderAtSqrDistance(double d) {
			return d < 256 * 256;
		}

		@Override
		public AABB getBoundingBoxForCulling() {
			float r = Math.max(3f, radius() * 2.2f + 2f);
			return new AABB(getX() - r, getY(), getZ() - r, getX() + r, getY() + 64, getZ() + r);
		}

		@Override
		public boolean isPickable() {
			return false;
		}

		@Override
		protected void readAdditionalSaveData(CompoundTag tag) {
		}

		@Override
		protected void addAdditionalSaveData(CompoundTag tag) {
		}
	}

	/** The rune circle burned into the ground. Its age is saved, so it fades on schedule across relogs. */
	public static class Mark extends Entity {
		private static final EntityDataAccessor<Integer> AGE = SynchedEntityData.defineId(Mark.class, EntityDataSerializers.INT);
		/** v0.17.7: 1 = the v0.17.1 circle (6.4 blocks across); the cinematic scales it to its beam. Saved. */
		private static final EntityDataAccessor<Float> SIZE = SynchedEntityData.defineId(Mark.class, EntityDataSerializers.FLOAT);
		/** Server-side age (the synced copy only follows it now and then once the glow is over). */
		private int age;

		public Mark(EntityType<? extends Mark> type, Level level) {
			super(type, level);
			noPhysics = true;
		}

		public int markAge() {
			return entityData.get(AGE);
		}

		public float size() {
			return entityData.get(SIZE);
		}

		public void setSize(float size) {
			entityData.set(SIZE, size);
		}

		@Override
		protected void defineSynchedData(SynchedEntityData.Builder builder) {
			builder.define(AGE, 0);
			builder.define(SIZE, 1f);
		}

		@Override
		public void tick() {
			super.tick();
			if (level().isClientSide) {
				return;
			}
			age++;
			// the glowing phase syncs the age every tick (the fade), after that only now and then
			if (age <= MARK_GLOW_TICKS || age % 100 == 0) {
				entityData.set(AGE, age);
			}
			if (age >= MARK_TICKS) {
				discard();
			}
		}

		@Override
		public boolean isPickable() {
			return false;
		}

		@Override
		public AABB getBoundingBoxForCulling() {
			float r = 4f * size();
			return new AABB(getX() - r, getY() - 0.2, getZ() - r, getX() + r, getY() + 0.4, getZ() + r);
		}

		@Override
		protected void readAdditionalSaveData(CompoundTag tag) {
			age = tag.getInt("MarkAge");
			entityData.set(AGE, age);
			if (tag.contains("MarkSize")) {
				entityData.set(SIZE, tag.getFloat("MarkSize"));
			}
		}

		@Override
		protected void addAdditionalSaveData(CompoundTag tag) {
			tag.putInt("MarkAge", age);
			tag.putFloat("MarkSize", size());
		}
	}
}
