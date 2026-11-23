package com.projecthero.mod.skyfortress.entity;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.skyfortress.item.SkyFortressItems;
import com.projecthero.mod.syndicate.entity.SyndicateSkin;
import com.projecthero.mod.syndicate.entity.SyndicateThug;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

/**
 * v0.17.7: a <b>Sky Trooper</b> -- Admiral Kestrel's deck crew: gunmetal flight-deck armour, navy webbing, an orange
 * visor. Stun baton or a cutlass. The {@link #SERGEANT} variant is the Launch Pad's <b>Pad Sergeant</b>: a white
 * deck-crew vest, and he drops the Fortress Pass when he dies (once). Player model, persistent, never burns.
 */
public class SkyTrooper extends SyndicateThug implements SkyHuman {
	public static final byte TROOPER = 0;
	public static final byte SERGEANT = 1;
	public static final String PASS_DROPPED = "projecthero.sky_pass_dropped";

	private static final ResourceLocation TEX_TROOPER = ProjectHeroMod.id("textures/entity/sky_fortress/sky_trooper.png");
	private static final ResourceLocation TEX_SERGEANT = ProjectHeroMod.id("textures/entity/sky_fortress/pad_sergeant.png");
	private static final EntityDataAccessor<Byte> DATA_VARIANT = SynchedEntityData.defineId(SkyTrooper.class, EntityDataSerializers.BYTE);

	public SkyTrooper(EntityType<? extends SkyTrooper> type, Level level) {
		super(type, level);
		this.xpReward = 10;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 36.0)
				.add(Attributes.MOVEMENT_SPEED, 0.29)
				.add(Attributes.ATTACK_DAMAGE, 5.0)
				.add(Attributes.ARMOR, 8.0)
				.add(Attributes.ARMOR_TOUGHNESS, 1.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.2)
				.add(Attributes.FOLLOW_RANGE, 32.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_VARIANT, TROOPER);
	}

	public byte variant() {
		return entityData.get(DATA_VARIANT);
	}

	public void setVariant(byte v) {
		entityData.set(DATA_VARIANT, v);
	}

	public boolean isSergeant() {
		return variant() == SERGEANT;
	}

	@Override
	public ResourceLocation skyTexture() {
		return isSergeant() ? TEX_SERGEANT : TEX_TROOPER;
	}

	@Override
	protected SyndicateSkin defaultSkin() {
		return SyndicateSkin.THUG_C; // wide arms (the texture itself comes from skyTexture)
	}

	@Override
	public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason, SpawnGroupData data) {
		SpawnGroupData out = super.finalizeSpawn(level, difficulty, reason, data);
		setSkin(SyndicateSkin.THUG_C);
		arm(new ItemStack(random.nextInt(3) == 0 ? Items.IRON_SWORD
				: com.projecthero.mod.gear.GearItems.ALLOY_BATON != null ? com.projecthero.mod.gear.GearItems.ALLOY_BATON : Items.IRON_SWORD));
		return out;
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
		super.dropCustomDeathLoot(level, source, recentlyHit);
		if (isSergeant() && !getTags().contains(PASS_DROPPED)) {
			addTag(PASS_DROPPED);
			spawnAtLocation(new ItemStack(SkyFortressItems.FORTRESS_PASS), 0.5f);
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.HOSTILE, 1f, 1.4f);
			level.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 1, getZ(), 12, 0.3, 0.4, 0.3, 0.05);
		}
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putByte("SkyVariant", variant());
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		setVariant(tag.getByte("SkyVariant"));
	}
}
