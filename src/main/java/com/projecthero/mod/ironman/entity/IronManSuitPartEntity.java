package com.projecthero.mod.ironman.entity;

import java.util.UUID;

import com.projecthero.mod.ironman.suit.IronManSuitUpManager;

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
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * One flying Iron Man armour piece, in transit from storage to its owner (spec sections 22, 25).
 * Reusable for every mark and every part -- {@code suitId}, {@code partOrdinal} (an
 * {@link ArmorItem.Type} ordinal) and {@code ownerId} describe which piece this is and who it is
 * going to. This is what makes partial / modular summons (Mark 42) possible: spawn only the parts you
 * want.
 *
 * <p>Purely a courier: on reaching the owner it hands the piece to
 * {@link IronManSuitUpManager#receivePart} (which equips it, server-authoritative) and discards
 * itself. If the owner logs out or dies mid-flight the piece is dropped safely at the entity's
 * position -- never destroyed.
 */
public class IronManSuitPartEntity extends Entity {
	private static final EntityDataAccessor<String> SUIT_ID =
			SynchedEntityData.defineId(IronManSuitPartEntity.class, EntityDataSerializers.STRING);
	private static final EntityDataAccessor<Integer> PART_ORDINAL =
			SynchedEntityData.defineId(IronManSuitPartEntity.class, EntityDataSerializers.INT);

	private UUID ownerId;
	private int life;
	/**
	 * Ticks this courier hovers at its spawn point before it starts flying to the owner. Lets a suit
	 * summon its pieces in a visible one-at-a-time sequence (chestplate, then boots, then leggings,
	 * then helmet -- "changes 10") instead of the whole set converging at once.
	 */
	private int launchDelay;

	public IronManSuitPartEntity(EntityType<? extends IronManSuitPartEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	public static IronManSuitPartEntity spawn(ServerLevel level, Vec3 from, ServerPlayer owner,
			String suitId, ArmorItem.Type part) {
		return spawn(level, from, owner, suitId, part, 0);
	}

	public static IronManSuitPartEntity spawn(ServerLevel level, Vec3 from, ServerPlayer owner,
			String suitId, ArmorItem.Type part, int launchDelay) {
		IronManSuitPartEntity e = new IronManSuitPartEntity(IronManEntityTypes.SUIT_PART, level);
		e.setPos(from.x, from.y, from.z);
		e.ownerId = owner.getUUID();
		e.launchDelay = Math.max(0, launchDelay);
		e.getEntityData().set(SUIT_ID, suitId);
		e.getEntityData().set(PART_ORDINAL, part.ordinal());
		level.addFreshEntity(e);
		return e;
	}

	public String suitId() {
		return getEntityData().get(SUIT_ID);
	}

	public ArmorItem.Type part() {
		return ArmorItem.Type.values()[getEntityData().get(PART_ORDINAL)];
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(SUIT_ID, "mark_iii");
		builder.define(PART_ORDINAL, ArmorItem.Type.CHESTPLATE.ordinal());
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide()) {
			level().addParticle(ParticleTypes.END_ROD, getX(), getY(), getZ(), 0, 0, 0);
			return;
		}
		life++;
		ServerLevel level = (ServerLevel) level();
		ServerPlayer owner = ownerId == null ? null : (ServerPlayer) level.getPlayerByUUID(ownerId);
		if (owner == null || owner.level() != level || life > 800) {
			dropAndDiscard(owner);
			return;
		}

		// Hold at the staging point until this piece's turn in the sequence.
		if (life < launchDelay) {
			setDeltaMovement(0, Math.sin(life * 0.3) * 0.01, 0);
			setPos(getX(), getY() + getDeltaMovement().y, getZ());
			setYRot(getYRot() + 8f);
			level.sendParticles(ParticleTypes.END_ROD, getX(), getY(), getZ(), 1, 0.05, 0.05, 0.05, 0.005);
			return;
		}

		int flightTicks = life - launchDelay;
		Vec3 target = owner.position().add(0, owner.getBbHeight() * 0.5, 0);
		Vec3 toTarget = target.subtract(position());
		double dist = toTarget.length();
		if (dist < 1.4) {
			IronManSuitUpManager.receivePart(owner, suitId(), part());
			level.playSound(null, owner.getX(), owner.getY(), owner.getZ(),
					SoundEvents.NETHERITE_BLOCK_PLACE, SoundSource.PLAYERS, 0.7f, 1.2f);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY(), getZ(), 12, 0.2, 0.2, 0.2, 0.1);
			discard();
			return;
		}
		// Cruise toward the owner. A gentle ramp + a mild distance term, capped low, so a courier
		// launched from far away visibly takes longer to arrive than one from a platform at your feet
		// (spec "changes 9" follow-up) rather than snapping in at the same speed regardless.
		double speed = Math.min(1.4, 0.35 + flightTicks * 0.02 + dist * 0.008);
		Vec3 step = toTarget.normalize().scale(speed);
		setDeltaMovement(step);
		setPos(getX() + step.x, getY() + step.y, getZ() + step.z);
		setYRot(getYRot() + 22f);
		level.sendParticles(ParticleTypes.END_ROD, getX(), getY(), getZ(), 2, 0.05, 0.05, 0.05, 0.01);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY(), getZ(), 1, 0.03, 0.03, 0.03, 0.02);
	}

	private void dropAndDiscard(ServerPlayer owner) {
		if (!level().isClientSide()) {
			var item = com.projecthero.mod.ironman.item.IronManItems.armor(suitId(), part());
			if (item != null) {
				spawnAtLocation(new net.minecraft.world.item.ItemStack(item));
			}
		}
		discard();
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
		if (tag.hasUUID("Owner")) {
			ownerId = tag.getUUID("Owner");
		}
		life = tag.getInt("Life");
		launchDelay = tag.getInt("LaunchDelay");
		if (tag.contains("SuitId")) {
			getEntityData().set(SUIT_ID, tag.getString("SuitId"));
		}
		getEntityData().set(PART_ORDINAL, tag.getInt("Part"));
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
		if (ownerId != null) {
			tag.putUUID("Owner", ownerId);
		}
		tag.putInt("Life", life);
		tag.putInt("LaunchDelay", launchDelay);
		tag.putString("SuitId", suitId());
		tag.putInt("Part", getEntityData().get(PART_ORDINAL));
	}

	@Override
	public boolean isPickable() {
		return false;
	}
}
