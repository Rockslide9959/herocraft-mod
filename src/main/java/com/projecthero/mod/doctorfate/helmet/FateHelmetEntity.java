package com.projecthero.mod.doctorfate.helmet;

import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.4 the Helmet of Fate set down in the world: on a block (right-click with the item), or fallen from Doctor Fate's head when
 * a Possession ends. It hovers a little above where it rests, turning slowly, glowing gold (the renderer,
 * {@code client.doctorfate.FateHelmetRenderer}). Right-click picks it up as the item. It never despawns, fire, lava and
 * explosions can't touch it, and only {@code /kill} (or its owner's summon) moves it.
 *
 * <p>It carries its {@link FateHelmetTag}: a copy whose generation its owner has moved past (the helmet was summoned away while
 * this chunk was unloaded) deletes itself the first time it ticks, in a puff of gold ({@link FateHelmetGuard} also catches it as it
 * loads).
 */
public class FateHelmetEntity extends Entity {
	private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(FateHelmetEntity.class,
			EntityDataSerializers.OPTIONAL_UUID);
	private static final EntityDataAccessor<Boolean> FALLING = SynchedEntityData.defineId(FateHelmetEntity.class, EntityDataSerializers.BOOLEAN);

	private String ownerName = "";
	private int generation;
	private boolean checked;

	public FateHelmetEntity(EntityType<? extends FateHelmetEntity> type, Level level) {
		super(type, level);
		this.noCulling = true;
	}

	/** A helmet resting at {@code at} (feet position), facing {@code yaw}; {@code falling} = it drops to the ground first. */
	public static FateHelmetEntity place(Level level, Vec3 at, FateHelmetTag tag, float yaw, boolean falling) {
		FateHelmetEntity e = new FateHelmetEntity(FateHelmet.ENTITY, level);
		e.moveTo(at.x, at.y, at.z, yaw, 0f);
		e.setTag(tag);
		e.entityData.set(FALLING, falling);
		return e;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(OWNER, Optional.empty());
		builder.define(FALLING, false);
	}

	public void setTag(FateHelmetTag tag) {
		if (tag == null) {
			entityData.set(OWNER, Optional.empty());
			ownerName = "";
			generation = 0;
			return;
		}
		entityData.set(OWNER, Optional.of(tag.owner()));
		ownerName = tag.ownerName();
		generation = tag.generation();
	}

	/** Null for a helmet nobody owns (a {@code /give}n one). */
	public FateHelmetTag tag() {
		return entityData.get(OWNER).map(o -> new FateHelmetTag(o, ownerName, generation)).orElse(null);
	}

	public Optional<UUID> owner() {
		return entityData.get(OWNER);
	}

	public boolean falling() {
		return entityData.get(FALLING);
	}

	public ItemStack toStack() {
		ItemStack s = new ItemStack(FateHelmet.ITEM);
		FateHelmetTag t = tag();
		if (t != null) {
			s.set(FateHelmet.TAG, t);
		}
		return s;
	}

	@Override
	public void tick() {
		super.tick();
		if (falling()) {
			Vec3 v = getDeltaMovement();
			setDeltaMovement(v.x * 0.9, Math.max(-0.6, v.y - 0.03), v.z * 0.9);
			move(MoverType.SELF, getDeltaMovement());
			if (!level().isClientSide() && getY() < level().getMinBuildHeight() - 4) {
				// v0.17.4 round 2: dropped into the void -- Nabu won't lose it: it rises onto the highest ground above (or the spawn)
				net.minecraft.core.BlockPos top = level().getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
						blockPosition());
				if (top.getY() <= level().getMinBuildHeight()) {
					top = ((ServerLevel) level()).getSharedSpawnPos();
				}
				setPos(top.getX() + 0.5, top.getY() + 0.5, top.getZ() + 0.5);
				setDeltaMovement(Vec3.ZERO);
			}
			if (onGround()) {
				setDeltaMovement(Vec3.ZERO);
				if (!level().isClientSide()) {
					entityData.set(FALLING, false);
					((ServerLevel) level()).sendParticles(com.projecthero.mod.doctorfate.DoctorFate.dust(0xFFC93A, 1.0f), getX(), getY() + 0.1, getZ(),
							10, 0.3, 0.05, 0.3, 0.0);
					level().playSound(null, getX(), getY(), getZ(), SoundEvents.ARMOR_EQUIP_GOLD.value(), SoundSource.NEUTRAL, 0.7f, 0.7f);
				}
			}
		}
		if (level() instanceof ServerLevel sl) {
			if (!checked || tickCount % 20 == 0) {
				checked = true;
				FateHelmetRegistry reg = FateHelmetRegistry.get(sl);
				FateHelmetTag t = tag();
				if (reg.isStale(t)) {
					vanish(sl);
					return;
				}
				if (t != null) {
					reg.note(t.owner(), FateHelmetRegistry.Where.PLACED, sl, blockPosition(), getUUID());
				}
			}
			if (tickCount % 6 == 0) {
				sl.sendParticles(com.projecthero.mod.doctorfate.DoctorFate.dust(0xFFC93A, 0.7f), getX(), getY() + 0.55, getZ(), 1, 0.25, 0.25, 0.25, 0.0);
			}
			if (tickCount % 15 == 0) {
				sl.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 0.5, getZ(), 1, 0.2, 0.2, 0.2, 0.005);
			}
		}
	}

	/** A ghost of a summoned-away helmet: gone in a puff of gold. */
	void vanish(ServerLevel level) {
		level.sendParticles(com.projecthero.mod.doctorfate.DoctorFate.dust(0xFFC93A, 1.2f), getX(), getY() + 0.5, getZ(), 16, 0.3, 0.3, 0.3, 0.0);
		discard();
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand hand) {
		if (level().isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (!(player instanceof ServerPlayer sp) || isRemoved()) {
			return InteractionResult.PASS;
		}
		ServerLevel sl = (ServerLevel) level();
		FateHelmetRegistry reg = FateHelmetRegistry.get(sl);
		if (reg.isStale(tag())) {
			vanish(sl);
			return InteractionResult.CONSUME;
		}
		ItemStack stack = toStack();
		discard();
		if (!sp.getInventory().add(stack)) {
			sp.drop(stack, false);
		} else {
			FateHelmetTag t = tag();
			if (t != null) {
				reg.note(t.owner(), FateHelmetRegistry.Where.INVENTORY, sl, sp.blockPosition(), sp.getUUID());
			}
		}
		sl.playSound(null, getX(), getY(), getZ(), SoundEvents.ARMOR_EQUIP_GOLD.value(), SoundSource.PLAYERS, 0.8f, 1.1f);
		sl.playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 1.3f);
		return InteractionResult.CONSUME;
	}

	@Override
	public boolean isPickable() {
		return !isRemoved();
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
	public boolean skipAttackInteraction(Entity attacker) {
		return true;
	}

	@Override
	public boolean ignoreExplosion(Explosion explosion) {
		return true;
	}

	@Override
	public boolean isIgnoringBlockTriggers() {
		return true;
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double d) {
		return d < 96.0 * 96.0;
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
		if (tag.hasUUID("Owner")) {
			setTag(new FateHelmetTag(tag.getUUID("Owner"), tag.getString("OwnerName"), tag.getInt("Generation")));
		}
		entityData.set(FALLING, tag.getBoolean("Falling"));
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
		FateHelmetTag t = tag();
		if (t != null) {
			tag.putUUID("Owner", t.owner());
			tag.putString("OwnerName", t.ownerName());
			tag.putInt("Generation", t.generation());
		}
		tag.putBoolean("Falling", falling());
	}
}
