package com.projecthero.mod.ironman.crate;

import com.projecthero.mod.ironman.IronManSounds;
import com.projecthero.mod.ironman.entity.IronManEntityTypes;
import com.projecthero.mod.ironman.item.SuitCrateItem;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7 (user) ERA I: the <b>empty suit folding itself into its crate</b> after a Shift+C emergency eject. The wearer
 * shoots out of the back ({@code EraOneEject}); this entity is the armour left standing there on its own, facing the way
 * they faced, lights on, for {@link #STAND_TICKS} (1 s); then over {@link #FOLD_TICKS} (1 s) it collapses -- arms in,
 * head down, the plates shrinking -- while the crate's walls swing up around it from the ground and the lid slams shut
 * (sparks, metal clanks), and it becomes the {@link SuitCrateBlock Suit Crate block} holding the four real stacks.
 *
 * <p>It carries the real armour stacks (synced so the renderer can draw the suit, and saved, so an unload mid-fold loses
 * nothing). If it is removed any other way before it lands the crate, the suit drops as a Suit Crate item.
 */
public class SuitCrateFoldEntity extends Entity {
	public static final int STAND_TICKS = 20;
	public static final int FOLD_TICKS = 20;
	/** A suit ejected in the air falls first; the stand starts on landing (or after this long airborne). */
	private static final int MAX_AIRBORNE = 100;

	@SuppressWarnings("unchecked")
	private static final EntityDataAccessor<ItemStack>[] SLOTS = new EntityDataAccessor[] {
			SynchedEntityData.defineId(SuitCrateFoldEntity.class, EntityDataSerializers.ITEM_STACK),
			SynchedEntityData.defineId(SuitCrateFoldEntity.class, EntityDataSerializers.ITEM_STACK),
			SynchedEntityData.defineId(SuitCrateFoldEntity.class, EntityDataSerializers.ITEM_STACK),
			SynchedEntityData.defineId(SuitCrateFoldEntity.class, EntityDataSerializers.ITEM_STACK) };
	/** Ticks since it stood on the ground (0 while still falling). */
	private static final EntityDataAccessor<Integer> AGE = SynchedEntityData.defineId(SuitCrateFoldEntity.class, EntityDataSerializers.INT);
	/** Where the crate will stand (picked as the fold starts), so the walls rise on the right block. */
	private static final EntityDataAccessor<BlockPos> TARGET = SynchedEntityData.defineId(SuitCrateFoldEntity.class, EntityDataSerializers.BLOCK_POS);
	private static final EntityDataAccessor<Boolean> HAS_TARGET = SynchedEntityData.defineId(SuitCrateFoldEntity.class, EntityDataSerializers.BOOLEAN);

	private int airborne;
	private boolean landed;

	public SuitCrateFoldEntity(EntityType<? extends SuitCrateFoldEntity> type, Level level) {
		super(type, level);
	}

	/** Stand the ejected suit up at {@code at}, facing {@code yaw}. */
	public static SuitCrateFoldEntity spawn(ServerLevel level, Vec3 at, float yaw, NonNullList<ItemStack> slots) {
		SuitCrateFoldEntity e = new SuitCrateFoldEntity(IronManEntityTypes.SUIT_CRATE_FOLD, level);
		e.moveTo(at.x, at.y, at.z, yaw, 0f);
		e.setYHeadRot(yaw);
		for (int i = 0; i < 4; i++) {
			e.entityData.set(SLOTS[i], i < slots.size() ? slots.get(i).copy() : ItemStack.EMPTY);
		}
		level.addFreshEntity(e);
		return e;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		for (EntityDataAccessor<ItemStack> s : SLOTS) {
			builder.define(s, ItemStack.EMPTY);
		}
		builder.define(AGE, 0);
		builder.define(TARGET, BlockPos.ZERO);
		builder.define(HAS_TARGET, false);
	}

	public ItemStack stack(int i) {
		return entityData.get(SLOTS[i]);
	}

	public NonNullList<ItemStack> stacks() {
		NonNullList<ItemStack> out = NonNullList.withSize(4, ItemStack.EMPTY);
		for (int i = 0; i < 4; i++) {
			out.set(i, stack(i).copy());
		}
		return out;
	}

	public int age() {
		return entityData.get(AGE);
	}

	/** 0..1 through the fold (0 while standing). */
	public float foldProgress(float partialTick) {
		int a = age();
		float t = a <= 0 ? 0f : a + partialTick;
		return Mth.clamp((t - STAND_TICKS) / FOLD_TICKS, 0f, 1f);
	}

	/** The crate's block, once picked (null before the fold starts or when there is no room). */
	public BlockPos target() {
		return entityData.get(HAS_TARGET) ? entityData.get(TARGET) : null;
	}

	/** The crate's facing: the suit's yaw snapped to a side. */
	public Direction facing() {
		return Direction.fromYRot(getYRot());
	}

	@Override
	public void tick() {
		super.tick();
		// it stands where it was ejected; one ejected mid-air falls first
		if (!onGround()) {
			setDeltaMovement(getDeltaMovement().add(0.0, -0.08, 0.0).multiply(0.9, 0.98, 0.9));
		} else {
			setDeltaMovement(Vec3.ZERO);
		}
		if (getDeltaMovement().lengthSqr() > 1.0E-6) {
			move(MoverType.SELF, getDeltaMovement());
		}
		if (level().isClientSide() || !(level() instanceof ServerLevel sl)) {
			return;
		}
		if (getY() < sl.getMinBuildHeight() - 8) {
			dropAsItem(sl); // fell out of the world: hand the suit over as an item where it can be reached
			return;
		}
		if (!onGround() && airborne++ < MAX_AIRBORNE && age() == 0) {
			return;
		}
		int age = age() + 1;
		entityData.set(AGE, age);
		double x = getX();
		double y = getY();
		double z = getZ();
		if (age == 1) {
			IronManSounds.playAt(sl, x, y + 1.0, z, IronManSounds.SERVO, 0.6f, 0.8f); // standing on its own, humming
		}
		if (age == STAND_TICKS) {
			BlockPos spot = SuitCrateBlock.findSpot(sl, blockPosition());
			if (spot != null) {
				entityData.set(TARGET, spot);
				entityData.set(HAS_TARGET, true);
			}
			IronManSounds.playAt(sl, x, y + 1.0, z, IronManSounds.RELEASE, 0.9f, 0.7f);
			sl.playSound(null, x, y, z, SoundEvents.PISTON_CONTRACT, SoundSource.PLAYERS, 0.6f, 0.8f);
		}
		if (age > STAND_TICKS && age < STAND_TICKS + FOLD_TICKS && (age - STAND_TICKS) % 4 == 0) {
			// plates folding in: a clank, a ratchet and a spray of sparks every few ticks
			float f = (age - STAND_TICKS) / (float) FOLD_TICKS;
			double py = y + 1.4 - f * 1.0;
			sl.playSound(null, x, py, z, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.PLAYERS, 0.7f, 0.7f + f * 0.5f);
			sl.playSound(null, x, py, z, SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 0.5f, 0.9f + random.nextFloat() * 0.3f);
			IronManSounds.playAt(sl, x, py, z, IronManSounds.CLAMP, 0.6f, 0.8f + f * 0.4f);
			sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, py, z, 6, 0.3, 0.25, 0.3, 0.12);
			sl.sendParticles(ParticleTypes.CRIT, x, py, z, 4, 0.25, 0.2, 0.25, 0.15);
		}
		if (age >= STAND_TICKS + FOLD_TICKS) {
			landCrate(sl);
		}
	}

	/** The fold is done: the crate block stands where the suit stood (its contents the suit), and this is gone. */
	private void landCrate(ServerLevel sl) {
		BlockPos want = target();
		BlockPos at = SuitCrateBlock.place(sl, want != null ? want : blockPosition(), facing(), stacks());
		landed = true;
		double x = at != null ? at.getX() + 0.5 : getX();
		double y = at != null ? at.getY() + 0.5 : getY();
		double z = at != null ? at.getZ() + 0.5 : getZ();
		sl.playSound(null, x, y, z, SoundEvents.NETHERITE_BLOCK_PLACE, SoundSource.BLOCKS, 1.0f, 0.7f);
		sl.playSound(null, x, y, z, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.35f, 1.4f);
		IronManSounds.playAt(sl, x, y, z, IronManSounds.CLAMP, 0.9f, 0.6f);
		sl.sendParticles(ParticleTypes.CLOUD, x, y, z, 10, 0.45, 0.2, 0.45, 0.03);
		sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y + 0.4, z, 10, 0.4, 0.2, 0.4, 0.12);
		discard();
	}

	private void dropAsItem(ServerLevel sl) {
		landed = true;
		BlockPos safe = new BlockPos(getBlockX(), sl.getMinBuildHeight() + 1, getBlockZ());
		BlockPos at = SuitCrateBlock.place(sl, sl.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, safe),
				facing(), stacks());
		if (at == null) {
			sl.addFreshEntity(new ItemEntity(sl, getX(), sl.getMinBuildHeight() + 2, getZ(), SuitCrateItem.of(stacks())));
		}
		discard();
	}

	@Override
	public void remove(RemovalReason reason) {
		// never lose the suit: killed / discarded before it landed the crate -> the crate item drops here
		if (!landed && reason.shouldDestroy() && level() instanceof ServerLevel sl) {
			landed = true;
			NonNullList<ItemStack> s = stacks();
			boolean any = false;
			for (ItemStack st : s) {
				any |= !st.isEmpty();
			}
			if (any) {
				sl.addFreshEntity(new ItemEntity(sl, getX(), getY() + 0.5, getZ(), SuitCrateItem.of(s)));
			}
		}
		super.remove(reason);
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
	public boolean isPushable() {
		return false;
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
		ListTag list = tag.getList("Pieces", Tag.TAG_COMPOUND);
		for (int i = 0; i < 4 && i < list.size(); i++) {
			entityData.set(SLOTS[i], ItemStack.parseOptional(registryAccess(), list.getCompound(i)));
		}
		entityData.set(AGE, tag.getInt("Age"));
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
		ListTag list = new ListTag();
		for (int i = 0; i < 4; i++) {
			ItemStack s = stack(i);
			list.add(s.isEmpty() ? new CompoundTag() : s.save(registryAccess()));
		}
		tag.put("Pieces", list);
		tag.putInt("Age", age());
	}
}
