package com.projecthero.mod.temple.entity;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.syndicate.entity.SyndicateSkin;
import com.projecthero.mod.syndicate.entity.SyndicateThug;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

/**
 * v0.17.7: base of the Ancient Temple's people -- temple guardians, wrapped revenants, cult acolytes and the Tomb
 * Sentinel. Player-model humans in detailed skins (drawn by the Syndicate player-model renderer, which this reuses with
 * its own {@link Skin} textures, so the Syndicate's skin list stays untouched), melee AI from the Syndicate thug, never
 * burning in daylight.
 */
public abstract class TempleMob extends SyndicateThug {
	private static final EntityDataAccessor<Byte> DATA_TEMPLE_SKIN = SynchedEntityData.defineId(TempleMob.class, EntityDataSerializers.BYTE);

	/** The temple skins, {@code textures/entity/temple/<name>.png} (painted by {@code scratchpad/v0177/gen_temple_v0177.js}). */
	public enum Skin {
		GUARDIAN_A("guardian_a"), GUARDIAN_B("guardian_b"), REVENANT_A("revenant_a"), REVENANT_B("revenant_b"),
		ACOLYTE_A("acolyte_a"), ACOLYTE_B("acolyte_b"), SENTINEL("tomb_sentinel");

		private final ResourceLocation texture;

		Skin(String name) {
			this.texture = ProjectHeroMod.id("textures/entity/temple/" + name + ".png");
		}

		public ResourceLocation texture() {
			return texture;
		}
	}

	protected TempleMob(EntityType<? extends TempleMob> type, Level level) {
		super(type, level);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_TEMPLE_SKIN, (byte) defaultTempleSkin().ordinal());
	}

	protected abstract Skin defaultTempleSkin();

	/** Picks a skin variant and arms the mob (after the thug's own spawn set-up). */
	protected abstract void outfit();

	public Skin templeSkin() {
		int i = entityData.get(DATA_TEMPLE_SKIN);
		Skin[] all = Skin.values();
		return i >= 0 && i < all.length ? all[i] : defaultTempleSkin();
	}

	public void setTempleSkin(Skin skin) {
		entityData.set(DATA_TEMPLE_SKIN, (byte) skin.ordinal());
	}

	@Override
	protected SyndicateSkin defaultSkin() {
		return SyndicateSkin.THUG_A; // only decides the arm width (wide); the texture is the temple skin
	}

	@Override
	public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason, SpawnGroupData data) {
		SpawnGroupData out = super.finalizeSpawn(level, difficulty, reason, data);
		setSkin(SyndicateSkin.THUG_A);
		outfit();
		return out;
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putByte("TempleSkin", entityData.get(DATA_TEMPLE_SKIN));
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		if (tag.contains("TempleSkin")) {
			entityData.set(DATA_TEMPLE_SKIN, tag.getByte("TempleSkin"));
		}
	}
}
