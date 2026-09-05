package com.herocraft.mod.punisher.ability;

import com.herocraft.mod.firearm.FirearmReload;
import com.herocraft.mod.firearm.Firearms;
import com.herocraft.mod.firearm.item.FirearmItem;
import com.herocraft.mod.hero.power.AbilityHelpers;
import com.herocraft.mod.punisher.Punisher;
import com.herocraft.mod.punisher.PunisherConfig;
import com.herocraft.mod.punisher.data.PunisherState;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Ability 3 (X) -- Tactical Roll. A quick dive in the direction the player is moving (or facing, if
 * still). Not a teleport: the player is given horizontal velocity for a few ticks and vanilla
 * collision stops them at walls / closed doors, so there is no clipping and no way to enter a block.
 * No upward component -- it can't be used to climb. Cancels an active reload and gives a brief window
 * of knockback resistance + light damage reduction (spec section 24). 4-second cooldown.
 */
public final class PunisherRoll {
	public static final String ABILITY = "tactical_roll";

	private PunisherRoll() {
	}

	public static void roll(ServerPlayer player) {
		if (!Punisher.hasPower(player) || !Punisher.abilityReady(player, ABILITY)) {
			return;
		}
		// require footing -- a roll is a ground move; also blocks an air-stall exploit
		if (!player.onGround() && !nearGround(player)) {
			return;
		}

		Vec3 dir = moveDirection(player);
		if (dir.lengthSqr() < 1.0E-4) {
			dir = new Vec3(player.getLookAngle().x, 0, player.getLookAngle().z).normalize();
		}
		Vec3 v = dir.scale(PunisherConfig.ROLL_SPEED);
		AbilityHelpers.launchSelf(player, new Vec3(v.x, Math.min(0.0, player.getDeltaMovement().y), v.z));

		PunisherState c = Punisher.state(player).copy();
		c.rollUntil = player.level().getGameTime() + PunisherConfig.ROLL_DURATION_TICKS;
		Punisher.save(player, c);

		// cancel a reload in progress
		ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
		if (held.getItem() instanceof FirearmItem && Firearms.of(held) != null) {
			FirearmReload.cancel(held);
		}

		ServerLevel level = player.serverLevel();
		level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.1, player.getZ(),
				8, 0.3, 0.05, 0.3, 0.02);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.5f, 1.3f);

		Punisher.triggerCooldown(player, ABILITY, PunisherConfig.ROLL_COOLDOWN_TICKS);
		com.herocraft.mod.punisher.PunisherPassives.reconcile(player);
	}

	/** Applied each tick while rolling: keep the dive going and hold the roll pose momentum. */
	public static void tick(ServerPlayer player) {
		if (!Punisher.rolling(player)) {
			return;
		}
		Vec3 m = player.getDeltaMovement();
		// re-assert horizontal speed against friction for the duration; never add lift
		double target = PunisherConfig.ROLL_SPEED * 0.9;
		double h = Math.sqrt(m.x * m.x + m.z * m.z);
		if (h > 0.05 && h < target) {
			double s = target / h;
			player.setDeltaMovement(m.x * s, Math.min(m.y, 0.0), m.z * s);
			player.hurtMarked = true;
		}
	}

	private static Vec3 moveDirection(ServerPlayer player) {
		// Use the player's actual horizontal motion if they are moving; a ServerPlayer's xxa/zza
		// input fields are not reliably populated, but its velocity is.
		Vec3 m = player.getDeltaMovement();
		Vec3 flat = new Vec3(m.x, 0, m.z);
		return flat.lengthSqr() > 0.002 ? flat.normalize() : Vec3.ZERO;
	}

	private static boolean nearGround(ServerPlayer player) {
		return !player.level().noCollision(player,
				player.getBoundingBox().expandTowards(0, -0.6, 0));
	}
}
