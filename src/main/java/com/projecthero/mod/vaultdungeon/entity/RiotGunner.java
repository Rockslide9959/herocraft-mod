package com.projecthero.mod.vaultdungeon.entity;

import com.projecthero.mod.syndicate.entity.SyndicateGunman;
import com.projecthero.mod.syndicate.entity.SyndicateSkin;

import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

/**
 * v0.17.5: a <b>riot gunner</b> -- a Vault warden with a riot shotgun (the Syndicate gunman's shotgun kit and its
 * bullets) and a grey ballistic vest. Keeps its distance and fires from cover.
 */
public class RiotGunner extends SyndicateGunman {
	public RiotGunner(EntityType<? extends RiotGunner> type, Level level) {
		super(type, level);
		this.xpReward = 12;
		super.setKind(Kind.SHOTGUN);
		setSkin(SyndicateSkin.VAULT_RIOT_GUNNER);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 34.0)
				.add(Attributes.MOVEMENT_SPEED, 0.28)
				.add(Attributes.ATTACK_DAMAGE, 3.0)
				.add(Attributes.ARMOR, 7.0)
				.add(Attributes.FOLLOW_RANGE, 40.0);
	}

	@Override
	protected SyndicateSkin defaultSkin() {
		return SyndicateSkin.VAULT_RIOT_GUNNER;
	}

	@Override
	public void setKind(Kind kind) {
		super.setKind(kind);
		setSkin(SyndicateSkin.VAULT_RIOT_GUNNER);
	}

	@Override
	public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason, SpawnGroupData data) {
		SpawnGroupData out = super.finalizeSpawn(level, difficulty, reason, data);
		setKind(Kind.SHOTGUN);
		return out;
	}
}
