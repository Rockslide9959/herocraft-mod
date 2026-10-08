package com.projecthero.mod.symbiote;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * v0.15.15 (user request): <b>Resistance I</b> for as long as a Symbiote suit is on -- the Normal host and Agent Venom.
 * Black Suit Spider-Man already gets exactly Resistance I from {@code SpiderPassives} while his black suit is on, so he
 * is left to it. Replaces the Normal host's old flat 10% "Living Armour" damage cut (which v0.12.15 had introduced as a
 * stand-in for Resistance I).
 *
 * <p>Ambient, no particles, no HUD icon, refreshed well before it can lapse, and taken off again the moment the suit
 * comes off. It never stomps a stronger (or a longer, visible) Resistance from anywhere else: a stronger one is simply
 * left alone and ours resumes when it runs out, and only an effect that is unmistakably ours is ever removed.
 */
public final class SymbioteSuitResistance {
	/** Ticks each refresh lasts; refreshed whenever it drops under {@link #REFRESH_BELOW}. */
	public static final int DURATION = 100;
	private static final int REFRESH_BELOW = 60;

	/** Players we have handed the suit's Resistance to (so taking it away never touches someone else's). */
	private static final Set<UUID> GRANTED = ConcurrentHashMap.newKeySet();

	private SymbioteSuitResistance() {
	}

	/** Does this player's worn Symbiote suit grant Resistance I right now? */
	public static boolean applies(ServerPlayer player) {
		return Symbiote.hasSymbiote(player) && Symbiote.isActive(player)
				&& SymbioteHostType.of(player) != SymbioteHostType.SPIDER_MAN;
	}

	/** Per-tick upkeep, called for every player from {@link Symbiote#tick}. Cheap when nothing applies. */
	public static void tick(ServerPlayer player) {
		boolean on = applies(player);
		if (!on && !GRANTED.contains(player.getUUID())) {
			return;
		}
		MobEffectInstance current = player.getEffect(MobEffects.DAMAGE_RESISTANCE);
		if (on) {
			boolean ours = current != null && current.getAmplifier() == 0 && current.isAmbient() && !current.isVisible();
			if (current == null || (ours && current.getDuration() < REFRESH_BELOW)) {
				player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, DURATION, 0, true, false, false));
				GRANTED.add(player.getUUID());
			}
			return;
		}
		GRANTED.remove(player.getUUID());
		if (current != null && current.getAmplifier() == 0 && current.isAmbient() && !current.isVisible()
				&& current.getDuration() <= DURATION) {
			player.removeEffect(MobEffects.DAMAGE_RESISTANCE);
		}
	}

	public static void clearSessionState() {
		GRANTED.clear();
	}
}
