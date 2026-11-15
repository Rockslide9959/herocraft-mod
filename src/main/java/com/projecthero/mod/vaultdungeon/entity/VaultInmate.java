package com.projecthero.mod.vaultdungeon.entity;

import com.projecthero.mod.syndicate.entity.SyndicateSkin;
import com.projecthero.mod.syndicate.entity.SyndicateThug;

import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
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
 * v0.17.5: an <b>escaped inmate</b> of The Vault -- loose in the Cell Block in an orange jumpsuit, fast and reckless,
 * swinging a shiv, a pipe or bare fists. A Syndicate-style human in a player skin (the player model renderer), so it
 * never burns in daylight and backs up the other Vault guards.
 */
public class VaultInmate extends SyndicateThug {
	public VaultInmate(EntityType<? extends VaultInmate> type, Level level) {
		super(type, level);
		this.xpReward = 8;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 30.0)
				.add(Attributes.MOVEMENT_SPEED, 0.33)
				.add(Attributes.ATTACK_DAMAGE, 3.0)
				.add(Attributes.ARMOR, 2.0)
				.add(Attributes.FOLLOW_RANGE, 32.0);
	}

	@Override
	protected SyndicateSkin defaultSkin() {
		return SyndicateSkin.VAULT_INMATE_A;
	}

	@Override
	public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason, SpawnGroupData data) {
		SpawnGroupData out = super.finalizeSpawn(level, difficulty, reason, data);
		setSkin(random.nextBoolean() ? SyndicateSkin.VAULT_INMATE_A : SyndicateSkin.VAULT_INMATE_B);
		switch (random.nextInt(3)) {
			case 0 -> arm(new ItemStack(Items.STONE_SWORD)); // the shiv
			case 1 -> arm(new ItemStack(Items.IRON_SHOVEL)); // a pipe torn off the wall
			default -> setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY); // fists
		}
		return out;
	}
}
