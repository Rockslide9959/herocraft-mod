package com.projecthero.mod.shazam.entity;

import com.projecthero.mod.shazam.ShazamConfig;
import com.projecthero.mod.shazam.ShazamWizards;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * v0.17.3 Shazam: <b>Wizard Shazam</b>, the last of the Council of Wizards. A very rare visitor near villages in a thunderstorm
 * ({@link ShazamWizards#trySpawn}): an old man in white and red robes with a long white beard (the player model in his own skin),
 * standing still and watching. Right-click him and he judges whether you are pure of heart ({@link ShazamWizards#judge}). He can't
 * be hurt, isn't saved, and fades in a flash of lightning when his work is done, the storm passes or five minutes go by.
 */
public class WizardShazamEntity extends PathfinderMob {
	private int life;

	public WizardShazamEntity(EntityType<? extends WizardShazamEntity> type, Level level) {
		super(type, level);
		setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0).add(Attributes.MOVEMENT_SPEED, 0.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 10f, 1f));
		goalSelector.addGoal(2, new RandomLookAroundGoal(this));
	}

	@Override
	public boolean isInvulnerableTo(DamageSource source) {
		return !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		if (!level().isClientSide() && player instanceof ServerPlayer sp) {
			ShazamWizards.judge(this, sp);
		}
		return InteractionResult.sidedSuccess(level().isClientSide());
	}

	@Override
	public void tick() {
		super.tick();
		if (level() instanceof ServerLevel server) {
			life++;
			if (life % 10 == 0) {
				server.sendParticles(ParticleTypes.ENCHANT, getX(), getY() + 1.8, getZ(), 3, 0.3, 0.3, 0.3, 0.3);
			}
			if (life % 40 == 0) {
				server.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 1.0, getZ(), 2, 0.3, 0.6, 0.3, 0.02);
			}
			if (life >= ShazamConfig.WIZARD_LIFETIME || (life > 200 && !server.isThundering() && !summoned)) {
				ShazamWizards.fade(this);
			}
		}
	}

	/** Called by the command: he stays until judged or five minutes are up, storm or not. */
	private boolean summoned;

	public void setSummoned(boolean summoned) {
		this.summoned = summoned;
	}

	public int life() {
		return life;
	}
}
