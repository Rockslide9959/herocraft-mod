package com.projecthero.mod.syndicate.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * v0.14.25: every member of the Syndicate -- thugs, gunmen, enforcers and the Kingpin himself. Humans in player skins
 * (see {@link SyndicateSkin}), so the renderer draws them with the player model; the skin is synced so the client knows
 * which. They don't burn in daylight, they back each other up ({@link HurtByTargetGoal#setAlertOthers}), they can open
 * doors, and their guns never hit each other ({@link com.projecthero.mod.syndicate.SyndicateGunfire}).
 *
 * <p>{@link #DATA_ACTION} is a small synced "what am I doing" byte the client reads for poses (aiming a gun, a charge,
 * a grab...); each subclass defines its own values.
 */
public abstract class SyndicateCriminal extends Monster {
	private static final EntityDataAccessor<Byte> DATA_SKIN = SynchedEntityData.defineId(SyndicateCriminal.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Byte> DATA_ACTION = SynchedEntityData.defineId(SyndicateCriminal.class, EntityDataSerializers.BYTE);

	public static final byte ACTION_NONE = 0;
	public static final byte ACTION_AIM = 1;

	protected SyndicateCriminal(EntityType<? extends SyndicateCriminal> type, Level level) {
		super(type, level);
		if (getNavigation() instanceof GroundPathNavigation nav) {
			nav.setCanOpenDoors(true);
		}
		setDropChance(EquipmentSlot.MAINHAND, 0.0f);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_SKIN, (byte) defaultSkin().ordinal());
		builder.define(DATA_ACTION, ACTION_NONE);
	}

	/** The skin a fresh one of this kind wears before {@link #setSkin} picks a variant. */
	protected abstract SyndicateSkin defaultSkin();

	public SyndicateSkin skin() {
		int i = entityData.get(DATA_SKIN);
		SyndicateSkin[] all = SyndicateSkin.values();
		return i >= 0 && i < all.length ? all[i] : defaultSkin();
	}

	public void setSkin(SyndicateSkin skin) {
		entityData.set(DATA_SKIN, (byte) skin.ordinal());
	}

	public byte action() {
		return entityData.get(DATA_ACTION);
	}

	public void setAction(byte action) {
		if (entityData.get(DATA_ACTION) != action) {
			entityData.set(DATA_ACTION, action);
		}
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(1, new OpenDoorGoal(this, true));
		registerCombatGoals();
		goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.8));
		goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 12.0f));
		goalSelector.addGoal(9, new RandomLookAroundGoal(this));
		targetSelector.addGoal(1, new HurtByTargetGoal(this, SyndicateCriminal.class).setAlertOthers(SyndicateCriminal.class));
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
		targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, IronGolem.class, true));
	}

	/** Priorities 2-6 are the subclass's: how it fights. */
	protected abstract void registerCombatGoals();

	/** Gives this crook its weapon (no drop chance -- guns are not that easy to come by). */
	protected void arm(ItemStack stack) {
		setItemSlot(EquipmentSlot.MAINHAND, stack);
		setDropChance(EquipmentSlot.MAINHAND, 0.0f);
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
		super.dropCustomDeathLoot(level, source, recentlyHit);
		if (recentlyHit && random.nextFloat() < 0.25f) {
			spawnAtLocation(new ItemStack(Items.EMERALD, 1 + random.nextInt(2)));
		}
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putByte("SyndicateSkin", entityData.get(DATA_SKIN));
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		if (tag.contains("SyndicateSkin")) {
			entityData.set(DATA_SKIN, tag.getByte("SyndicateSkin"));
		}
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.PILLAGER_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.PLAYER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.PLAYER_DEATH;
	}

	@Override
	public int getAmbientSoundInterval() {
		return 240;
	}

	@Override
	public float getVoicePitch() {
		return 0.8f + random.nextFloat() * 0.2f;
	}
}
