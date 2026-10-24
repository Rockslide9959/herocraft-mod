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
		private static final EntityDataAccessor<Boolean> ARRIVAL = SynchedEntityData.defineId(Beam.class, EntityDataSerializers.BOOLEAN);

		public Beam(EntityType<? extends Beam> type, Level level) {
			super(type, level);
			noPhysics = true;
			noCulling = true;
		}

		public void setArrival(boolean arrival) {
			entityData.set(ARRIVAL, arrival);
		}

		public boolean arrival() {
			return entityData.get(ARRIVAL);
		}

		@Override
		protected void defineSynchedData(SynchedEntityData.Builder builder) {
			builder.define(ARRIVAL, true);
		}

		@Override
		public void tick() {
			super.tick();
			if (!level().isClientSide && tickCount >= BEAM_TICKS) {
				discard();
			}
		}

		@Override
		public boolean shouldRenderAtSqrDistance(double d) {
			return d < 256 * 256;
		}

		@Override
		public AABB getBoundingBoxForCulling() {
			return new AABB(getX() - 3, getY(), getZ() - 3, getX() + 3, getY() + 64, getZ() + 3);
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
		/** Server-side age (the synced copy only follows it now and then once the glow is over). */
		private int age;

		public Mark(EntityType<? extends Mark> type, Level level) {
			super(type, level);
			noPhysics = true;
		}

		public int markAge() {
			return entityData.get(AGE);
		}

		@Override
		protected void defineSynchedData(SynchedEntityData.Builder builder) {
			builder.define(AGE, 0);
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
			return new AABB(getX() - 4, getY() - 0.2, getZ() - 4, getX() + 4, getY() + 0.4, getZ() + 4);
		}

		@Override
		protected void readAdditionalSaveData(CompoundTag tag) {
			age = tag.getInt("MarkAge");
			entityData.set(AGE, age);
		}

		@Override
		protected void addAdditionalSaveData(CompoundTag tag) {
			tag.putInt("MarkAge", age);
		}
	}
}
