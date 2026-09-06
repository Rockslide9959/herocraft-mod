package com.projecthero.mod.event.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

/**
 * The raid's frontline: the Basic, Baby and Armoured zombies of the wave table, in one entity type
 * with a synced {@link Variant}.
 *
 * <p>One type rather than three is a deliberate simplification -- they share a model, a texture, an
 * AI and a loot table, and differ only in a handful of attribute values plus (for Armoured) the gear
 * they wear. Three registrations would have bought three near-identical classes and three loot table
 * files for no gameplay difference. The variant is synced so the client can size the baby correctly.
 *
 * <p><b>Armoured</b> exists specifically to stop the raid being trivialised from range (spec section
 * 17): high armour and armour toughness cut projectile damage hard, but its melee output is
 * unremarkable, so the answer to it is to close in and deal with it -- not to out-range it.
 */
public class RaidZombie extends RaidUndead {
	private static final EntityDataAccessor<Integer> DATA_VARIANT =
			SynchedEntityData.defineId(RaidZombie.class, EntityDataSerializers.INT);

	public enum Variant {
		BASIC,
		BABY,
		ARMOURED;

		public static Variant byId(int id) {
			Variant[] all = values();
			return id >= 0 && id < all.length ? all[id] : BASIC;
		}
	}

	public RaidZombie(EntityType<? extends RaidZombie> type, Level level) {
		super(type, level);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_VARIANT, Variant.BASIC.ordinal());
	}

	public Variant variant() {
		return Variant.byId(this.entityData.get(DATA_VARIANT));
	}

	/**
	 * Set the variant and apply its stat profile. Called once, before the mob enters the world, by the
	 * wave table's spawn customizer.
	 */
	public void setVariant(Variant variant) {
		this.entityData.set(DATA_VARIANT, variant.ordinal());
		applyVariant(variant);
	}

	private void applyVariant(Variant variant) {
		switch (variant) {
			case BABY -> {
				setBaby(true);
				set(Attributes.MAX_HEALTH, 12.0);
				set(Attributes.ATTACK_DAMAGE, 3.0);
				// Vanilla already gives babies a speed bonus modifier; this is the base on top of it.
				set(Attributes.MOVEMENT_SPEED, 0.25);
				set(Attributes.FOLLOW_RANGE, 40.0);
			}
			case ARMOURED -> {
				setBaby(false);
				// v0.7.3: the v0.6.22 nerf left it too soft -- restored to a real damage sponge. Base
				// armour/toughness/knockback bonuses are back and the worn set is iron again. Its melee
				// output is still unremarkable, so the answer is to close in and grind it down, not
				// out-range it.
				set(Attributes.MAX_HEALTH, 45.0);
				set(Attributes.ATTACK_DAMAGE, 4.0);
				set(Attributes.MOVEMENT_SPEED, 0.20);
				set(Attributes.FOLLOW_RANGE, 40.0);
				set(Attributes.ARMOR, 7.0);
				set(Attributes.ARMOR_TOUGHNESS, 3.0);
				set(Attributes.KNOCKBACK_RESISTANCE, 0.4);
				equipArmour();
			}
			default -> {
				setBaby(false);
				set(Attributes.MAX_HEALTH, 22.0);
				set(Attributes.ATTACK_DAMAGE, 3.5);
				// A shade quicker and longer-sighted than a wild zombie: "slightly more aggressive
				// than normal Minecraft zombies during the event" (spec section 17).
				set(Attributes.MOVEMENT_SPEED, 0.245);
				set(Attributes.FOLLOW_RANGE, 40.0);
			}
		}
		setHealth(getMaxHealth());
	}

	private void equipArmour() {
		setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
		setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
		setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
		setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
				// Raid gear is not loot -- otherwise the event becomes an iron farm.
				setDropChance(slot, 0.0f);
			}
		}
	}

	private void set(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, double value) {
		AttributeInstance instance = getAttribute(attribute);
		if (instance != null) {
			instance.setBaseValue(value);
		}
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Zombie.createAttributes()
				.add(Attributes.MAX_HEALTH, 22.0)
				.add(Attributes.ATTACK_DAMAGE, 3.5)
				.add(Attributes.MOVEMENT_SPEED, 0.245)
				.add(Attributes.FOLLOW_RANGE, 40.0)
				.add(Attributes.ARMOR, 2.0)
				.add(Attributes.ARMOR_TOUGHNESS, 0.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.0);
	}

	@Override
	public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
			MobSpawnType reason, SpawnGroupData data) {
		SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, data);
		// Vanilla's finalizeSpawn rolls baby-ness and random gear; re-assert the variant's own profile
		// afterwards so a Basic never comes out as a baby and an Armoured never loses its kit.
		applyVariant(variant());
		return result;
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putInt("RaidVariant", this.entityData.get(DATA_VARIANT));
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		if (tag.contains("RaidVariant")) {
			this.entityData.set(DATA_VARIANT, tag.getInt("RaidVariant"));
		}
	}

}
