package com.projecthero.mod.punisher.ability;

import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.punisher.Punisher;
import com.projecthero.mod.punisher.PunisherConfig;
import com.projecthero.mod.punisher.PunisherControl;
import com.projecthero.mod.squad.Squads;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.15.18 -- G and Shift+G, the Punisher's close-quarters moves. Both hit the living thing you are looking at within
 * 5 blocks (never yourself, a squadmate or your own pet) and stun it ({@link PunisherControl#stun}): it cannot move or
 * attack until the stun ends.
 *
 * <ul>
 *   <li><b>G -- Brutal Strike</b>: 18 damage, 2 s stun. 2 s cooldown.</li>
 *   <li><b>Shift+G -- Breach Kick</b>: 25 damage, thrown about 10 blocks back, 5 s stun. 5 s cooldown.</li>
 * </ul>
 *
 * A swing at nothing just whiffs: no cooldown is spent.
 */
public final class PunisherMelee {
	public static final String STRIKE = "brutal_strike";
	public static final String KICK = "breach_kick";

	private PunisherMelee() {
	}

	/** The living thing {@code player} is looking at within strike range that he may hit, or null. */
	public static LivingEntity target(ServerPlayer player) {
		LivingEntity t = AbilityHelpers.raycastEntity(player, PunisherConfig.STRIKE_RANGE);
		if (t == null || !HeroTargets.canHarm(player, t) || Squads.areAllies(player, t)) {
			return null;
		}
		return t;
	}

	public static void brutalStrike(ServerPlayer player) {
		if (!Punisher.hasPower(player) || !Punisher.abilityReady(player, STRIKE)) {
			return;
		}
		LivingEntity t = target(player);
		player.swing(InteractionHand.MAIN_HAND, true);
		if (t == null) {
			whiff(player);
			return;
		}
		ServerLevel level = player.serverLevel();
		boolean dealt = AbilityHelpers.hurtBurst(player, t, PunisherConfig.BRUTAL_STRIKE_DAMAGE);
		if (dealt && t.isAlive()) {
			PunisherControl.stun(t, PunisherConfig.BRUTAL_STRIKE_STUN_TICKS);
		}
		Vec3 at = t.position().add(0, t.getBbHeight() * 0.6, 0);
		level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 14, 0.25, 0.25, 0.25, 0.25);
		level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, at.x, at.y, at.z, 4, 0.2, 0.2, 0.2, 0.1);
		level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.0f, 0.7f);
		level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.PLAYERS, 0.5f, 1.4f);
		Punisher.triggerCooldown(player, STRIKE, PunisherConfig.BRUTAL_STRIKE_COOLDOWN_TICKS);
	}

	public static void breachKick(ServerPlayer player) {
		if (!Punisher.hasPower(player) || !Punisher.abilityReady(player, KICK)) {
			return;
		}
		LivingEntity t = target(player);
		player.swing(InteractionHand.OFF_HAND, true);
		if (t == null) {
			whiff(player);
			return;
		}
		ServerLevel level = player.serverLevel();
		boolean dealt = AbilityHelpers.hurtBurst(player, t, PunisherConfig.BREACH_KICK_DAMAGE);
		if (dealt && t.isAlive()) {
			launch(player, t);
			PunisherControl.stun(t, PunisherConfig.BREACH_KICK_STUN_TICKS);
		}
		Vec3 at = t.position().add(0, t.getBbHeight() * 0.5, 0);
		level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 20, 0.3, 0.3, 0.3, 0.35);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState()),
				t.getX(), t.getY() + 0.1, t.getZ(), 12, 0.3, 0.05, 0.3, 0.1);
		level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.2f, 0.6f);
		level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.PLAYERS, 0.6f, 1.3f);
		Punisher.triggerCooldown(player, KICK, PunisherConfig.BREACH_KICK_COOLDOWN_TICKS);
	}

	/** Throw {@code t} straight away from the kicker, about 10 blocks; knockback resistance still counts. */
	private static void launch(ServerPlayer player, LivingEntity t) {
		Vec3 dir = new Vec3(t.getX() - player.getX(), 0, t.getZ() - player.getZ());
		if (dir.lengthSqr() < 1.0E-4) {
			dir = new Vec3(player.getLookAngle().x, 0, player.getLookAngle().z);
		}
		dir = dir.normalize();
		double resist = t.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
		double s = PunisherConfig.BREACH_KICK_SPEED * Math.max(0.0, 1.0 - resist);
		if (s <= 0.0) {
			return;
		}
		t.setDeltaMovement(dir.x * s, PunisherConfig.BREACH_KICK_LIFT * Math.max(0.0, 1.0 - resist), dir.z * s);
		t.hasImpulse = true;
		t.hurtMarked = true;
	}

	private static void whiff(ServerPlayer player) {
		player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.PLAYER_ATTACK_NODAMAGE, SoundSource.PLAYERS, 0.7f, 0.9f);
	}
}
