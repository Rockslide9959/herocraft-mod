package com.projecthero.mod.punisher.ability;

import com.projecthero.mod.firearm.Firearms;
import com.projecthero.mod.punisher.Punisher;
import com.projecthero.mod.punisher.PunisherConfig;
import com.projecthero.mod.punisher.PunisherFeedback;
import com.projecthero.mod.punisher.PunisherPassives;
import com.projecthero.mod.punisher.data.PunisherState;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Ability 4 (Z) -- Suppressive Fire. Needs the Assault Rifle unlocked. For 8 seconds: much less
 * recoil and spread, a faster rifle fire rate, and enemies you hit are briefly Slowed -- but you
 * move slower yourself while it is up. It is a sustained-fire control tool, not a damage steroid
 * (spec section 25). 20-second cooldown.
 */
public final class PunisherSuppressive {
	public static final String ABILITY = "suppressive_fire";

	private PunisherSuppressive() {
	}

	public static void activate(ServerPlayer player) {
		if (!Punisher.hasPower(player)) {
			return;
		}
		// The Assault Rifle is the intended weapon for this stance, but holding any Punisher firearm is
		// enough to enter it -- a stricter "must have crafted the rifle" gate was the most common reason
		// pressing Z appeared to do nothing (v0.9.23).
		if (!Punisher.weaponUnlocked(player, Firearms.RIFLE) && !holdingFirearm(player)) {
			PunisherFeedback.message(player, "need_rifle");
			return;
		}
		if (!Punisher.abilityReady(player, ABILITY)) {
			int secs = (Punisher.cooldownRemaining(player, ABILITY) + 19) / 20;
			player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
					"message.projecthero.punisher.suppressive_cooldown", secs)
					.withStyle(net.minecraft.ChatFormatting.GRAY), true);
			return;
		}
		PunisherState c = Punisher.state(player).copy();
		c.suppressiveUntil = player.level().getGameTime() + PunisherConfig.SUPPRESSIVE_DURATION_TICKS;
		Punisher.save(player, c);
		PunisherPassives.reconcile(player);
		Punisher.triggerCooldown(player, ABILITY, PunisherConfig.SUPPRESSIVE_COOLDOWN_TICKS);

		ServerLevel level = player.serverLevel();
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.CROSSBOW_LOADING_END.value(), SoundSource.PLAYERS, 0.6f, 0.8f);
		level.sendParticles(ParticleTypes.CRIT, player.getX(), player.getY() + 1.0, player.getZ(),
				12, 0.4, 0.5, 0.4, 0.1);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.CROSSBOW_LOADING_END.value(), SoundSource.PLAYERS, 0.9f, 1.5f);
		PunisherFeedback.message(player, "suppressive_on");
	}

	private static boolean holdingFirearm(ServerPlayer player) {
		return player.getMainHandItem().getItem() instanceof com.projecthero.mod.firearm.item.FirearmItem
				|| player.getOffhandItem().getItem() instanceof com.projecthero.mod.firearm.item.FirearmItem;
	}
}
