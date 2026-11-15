package com.projecthero.mod.vaultdungeon.entity;

import com.projecthero.mod.gear.GearItems;
import com.projecthero.mod.syndicate.entity.SyndicateSkin;
import com.projecthero.mod.syndicate.entity.SyndicateThug;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.5: a <b>riot warden</b> -- The Vault's guards, in black tactical armour and a visored riot helmet, with a stun
 * baton and a riot shield. Hits land a short Slowness (the baton's shock); the shield takes {@link #SHIELD_CUT} off any
 * hit from the front (melee or projectile) -- flank them. Player model, detailed skin, persistent, no daylight burning.
 */
public class RiotWarden extends SyndicateThug {
	/** The share of a frontal hit the shield stops. */
	public static final float SHIELD_CUT = 0.4f;

	public RiotWarden(EntityType<? extends RiotWarden> type, Level level) {
		super(type, level);
		this.xpReward = 12;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 44.0)
				.add(Attributes.MOVEMENT_SPEED, 0.29)
				.add(Attributes.ATTACK_DAMAGE, 5.0)
				.add(Attributes.ARMOR, 10.0)
				.add(Attributes.ARMOR_TOUGHNESS, 2.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.4)
				.add(Attributes.FOLLOW_RANGE, 32.0);
	}

	@Override
	protected SyndicateSkin defaultSkin() {
		return SyndicateSkin.VAULT_RIOT_WARDEN;
	}

	@Override
	public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason, SpawnGroupData data) {
		SpawnGroupData out = super.finalizeSpawn(level, difficulty, reason, data);
		setSkin(SyndicateSkin.VAULT_RIOT_WARDEN);
		arm(new ItemStack(GearItems.ALLOY_BATON != null ? GearItems.ALLOY_BATON : Items.IRON_SWORD));
		setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
		setDropChance(EquipmentSlot.OFFHAND, 0.0f);
		return out;
	}

	/** True when {@code source} comes at this warden from the front (within ~110 degrees of where it faces). */
	public static boolean fromFront(LivingEntity self, DamageSource source) {
		Vec3 from = source.getSourcePosition();
		if (from == null || source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC)
				|| source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_SHIELD)) {
			return false;
		}
		Vec3 look = self.getViewVector(1f).multiply(1, 0, 1).normalize();
		Vec3 to = from.subtract(self.position()).multiply(1, 0, 1);
		return to.lengthSqr() > 1.0e-4 && look.dot(to.normalize()) > 0.35;
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (!level().isClientSide() && !getOffhandItem().isEmpty() && fromFront(this, source)) {
			amount *= 1f - SHIELD_CUT;
			level().playSound(null, getX(), getY(), getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 0.8f, 0.9f + random.nextFloat() * 0.2f);
		}
		return super.hurt(source, amount);
	}

	@Override
	public boolean doHurtTarget(Entity target) {
		boolean hit = super.doHurtTarget(target);
		if (hit && target instanceof LivingEntity l) {
			l.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 25, 1), this);
		}
		return hit;
	}
}
