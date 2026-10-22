package com.projecthero.mod.ironman;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.ironman.entity.IronManSentryEntity;
import com.projecthero.mod.ironman.suit.IronManSuit;

import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;

/**
 * v0.16.7 (user) Hulkbuster fixes: "change the hulkbuster armour integrity so that instead of the player taking damage the
 * hulkbuster takes the damage almost like a titan shifter". While the full Hulkbuster I (mark_8) is worn and has
 * integrity, it is the suit that gets hit, never its pilot (like a Titan Shifter's Titan, {@code TitanShifterDamage}):
 * <ul>
 *   <li>every hit the suit can soak ({@link IronManDamage#absorbable}) comes off its integrity in full, 1:1, after the
 *   Hydraulic Brace / shields ({@code IronManComicState#incomingFactor}); the pilot takes nothing. The body flinches
 *   (the hurt flash on every viewer) and rings like struck metal;</li>
 *   <li>what it can't soak -- drowning, suffocating, starving, cramming, the world border -- doesn't reach the sealed
 *   pilot at all; the void and /kill ({@code bypasses_invulnerability}) still do;</li>
 *   <li>hits within 10 ticks of the last one only take what they exceed it by (vanilla's i-frames: the hit is cancelled,
 *   so the player's own never start);</li>
 *   <li>when a hit takes the integrity to 0 the suit is wrecked: {@link IronManSentryEntity#wreck} throws the pilot out
 *   of the back and leaves it as a wrecked Sentry for {@link IronManSentryEntity#WRECK_COOLDOWN_TICKS}.</li>
 * </ul>
 * A Hulkbuster already at 0 (stepped back into after its reboot) protects nothing: hits land on the pilot until it is
 * repaired -- its Replacement Pod or a Suit Platform, the existing repair rules (it still never self-repairs).
 *
 * <p>The i-frame record is static per-player state, cleared in {@code ServerStateReset} and on disconnect.
 */
public final class HulkbusterShield {
	/** Vanilla: a second hit inside half the 20-tick invulnerability only lands what it exceeds the first by. */
	public static final int IFRAME_TICKS = 10;

	private record Hit(float amount, long until) {
	}

	private static final Map<UUID, Hit> LAST = new ConcurrentHashMap<>();

	private HulkbusterShield() {
	}

	public static void clearSessionState() {
		LAST.clear();
	}

	public static void forget(UUID player) {
		LAST.remove(player);
	}

	/** Is {@code player} in a whole, working Hulkbuster -- the suit takes their hits? */
	public static boolean shielding(ServerPlayer player, IronManSuit suit) {
		return suit != null && suit.hulkbuster() && IronManArmor.wearingFullSuit(player, suit.id())
				&& IronManEnergy.integrity(player, suit.id()) > 0f;
	}

	/**
	 * The Hulkbuster takes this hit ({@code amount} after the shields / brace) instead of {@code player}. Returns true when
	 * it was handled -- the hit must then be cancelled for the player -- and false when the normal rules apply (not in a
	 * working Hulkbuster, or a source nothing stops). Public for the gametests (mock players never fire the damage events).
	 */
	public static boolean take(ServerPlayer player, IronManSuit suit, DamageSource source, float amount) {
		if (!shielding(player, suit) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return false;
		}
		if (!IronManDamage.absorbable(source) || amount <= 0f) {
			return true; // sealed in: nothing of this reaches the pilot, and it doesn't wear the suit either
		}
		long now = player.level().getGameTime();
		float take = amount;
		Hit last = LAST.get(player.getUUID());
		if (last != null && now < last.until() && !source.is(DamageTypeTags.BYPASSES_COOLDOWN)) {
			if (amount <= last.amount()) {
				return true;
			}
			take = amount - last.amount();
		}
		LAST.put(player.getUUID(), new Hit(amount, now + IFRAME_TICKS));
		float integrity = IronManEnergy.integrity(player, suit.id());
		float left = Math.max(0f, integrity - take);
		IronManEnergy.setIntegrity(player, suit.id(), left);
		// the suit flinches and rings; the pilot inside feels nothing
		player.serverLevel().getChunkSource().broadcastAndSend(player, new ClientboundHurtAnimationPacket(player));
		player.level().playSound(null, player.getX(), player.getY() + 1.5, player.getZ(), SoundEvents.IRON_GOLEM_DAMAGE, SoundSource.PLAYERS,
				0.8f, 0.6f + player.getRandom().nextFloat() * 0.15f);
		if (left <= 0f) {
			IronManSentryEntity.wreck(player);
		}
		return true;
	}
}
