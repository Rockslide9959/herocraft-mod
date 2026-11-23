package com.projecthero.mod.skyfortress.entity;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.syndicate.entity.SyndicateGunman;
import com.projecthero.mod.syndicate.entity.SyndicateSkin;

import net.minecraft.resources.ResourceLocation;
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
 * v0.17.7: a <b>Sky Gunner</b> -- the fortress's marksmen: the trooper's kit with a red-striped flak vest and a sidearm
 * (the Syndicate gunman's pistol kit and real bullets); posted on perches they carry a rifle ({@code Kind.SNIPER},
 * laser sight). Player model, persistent.
 */
public class SkyGunner extends SyndicateGunman implements SkyHuman {
	private static final ResourceLocation TEX = ProjectHeroMod.id("textures/entity/sky_fortress/sky_gunner.png");

	public SkyGunner(EntityType<? extends SkyGunner> type, Level level) {
		super(type, level);
		this.xpReward = 10;
		super.setKind(Kind.PISTOL);
		setSkin(SyndicateSkin.THUG_C);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 30.0)
				.add(Attributes.MOVEMENT_SPEED, 0.28)
				.add(Attributes.ATTACK_DAMAGE, 3.0)
				.add(Attributes.ARMOR, 6.0)
				.add(Attributes.FOLLOW_RANGE, 44.0);
	}

	@Override
	public ResourceLocation skyTexture() {
		return TEX;
	}

	@Override
	protected SyndicateSkin defaultSkin() {
		return SyndicateSkin.THUG_C;
	}

	@Override
	public void setKind(Kind kind) {
		super.setKind(kind);
		setSkin(SyndicateSkin.THUG_C); // keep the wide-armed model whatever gun he carries
	}

	@Override
	public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason, SpawnGroupData data) {
		SpawnGroupData out = super.finalizeSpawn(level, difficulty, reason, data);
		setKind(kind());
		return out;
	}
}
