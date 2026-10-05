package com.projecthero.mod.syndicate.entity;

import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

/**
 * v0.14.25: the Syndicate's rank and file -- fast, cheap and many, swinging whatever they found (a bat-like wooden
 * club, a crowbar, a knife). They rush in and back each other up; the danger is the crowd, not any one of them.
 */
public class SyndicateThug extends SyndicateCriminal {
	public SyndicateThug(EntityType<? extends SyndicateThug> type, Level level) {
		super(type, level);
		this.xpReward = 6;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 26.0)
				.add(Attributes.MOVEMENT_SPEED, 0.32)
				.add(Attributes.ATTACK_DAMAGE, 2.0)
				.add(Attributes.ARMOR, 2.0)
				.add(Attributes.FOLLOW_RANGE, 40.0);
	}

	@Override
	protected SyndicateSkin defaultSkin() {
		return SyndicateSkin.THUG_A;
	}

	@Override
	protected void registerCombatGoals() {
		goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2, false));
	}

	@Override
	public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason, SpawnGroupData data) {
		SpawnGroupData out = super.finalizeSpawn(level, difficulty, reason, data);
		setSkin(random.nextBoolean() ? SyndicateSkin.THUG_A : SyndicateSkin.THUG_C);
		arm(new ItemStack(switch (random.nextInt(3)) {
			case 0 -> Items.WOODEN_SWORD; // the bat
			case 1 -> Items.IRON_SHOVEL; // the crowbar
			default -> Items.STONE_SWORD; // the knife
		}));
		return out;
	}
}
