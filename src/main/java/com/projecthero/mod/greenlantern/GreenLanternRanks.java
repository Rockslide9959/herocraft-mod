package com.projecthero.mod.greenlantern;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.greenlantern.data.GreenLanternState;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.1 Lantern Ranks: Green Lantern grows with use. Willpower XP comes from damage the ring deals (1 per point),
 * kills (+{@link GreenLanternConfig#XP_PER_KILL}) and boss kills (+{@link GreenLanternConfig#XP_PER_BOSS_KILL}); five
 * ranks ({@link GreenLanternConfig#RANK_XP}) scale ring damage, the Ring Charge pool and every Ring Charge cost. XP lives in
 * {@link GreenLanternState#willpowerXp}, so it is kept on death and when the ring comes off.
 *
 * <p>Willpower Surge rides on the same damage multiplier: above {@link GreenLanternConfig#SURGE_HIGH} of the pool ring
 * damage is +15%, below {@link GreenLanternConfig#SURGE_LOW} it is -15%.
 *
 * <p>Rank reads are CLIENT-SAFE (the HUD, the I menu and the Construct Menu show them); the rest is server-side.
 */
public final class GreenLanternRanks {
	public static final int MAX_RANK = 5;

	private GreenLanternRanks() {
	}

	/** Rank 1..5 for an XP total. */
	public static int rankFor(long xp) {
		int r = 1;
		for (int i = 1; i < GreenLanternConfig.RANK_XP.length; i++) {
			if (xp >= GreenLanternConfig.RANK_XP[i]) {
				r = i + 1;
			}
		}
		return r;
	}

	public static int rank(Player player) {
		GreenLanternState s = player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_STATE, null);
		return s == null ? 1 : rankFor(s.willpowerXp);
	}

	public static long xp(Player player) {
		GreenLanternState s = player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_STATE, null);
		return s == null ? 0L : s.willpowerXp;
	}

	/** XP at which the next rank starts (or the last rank's threshold at max rank). */
	public static long nextThreshold(int rank) {
		return GreenLanternConfig.RANK_XP[Math.min(rank, MAX_RANK - 1)];
	}

	public static String nameKey(int rank) {
		return "projecthero.green_lantern.rank." + rank;
	}

	public static float damageMult(int rank) {
		return GreenLanternConfig.RANK_DAMAGE[rank - 1];
	}

	public static float maxChargeMult(int rank) {
		return GreenLanternConfig.RANK_MAX_CHARGE[rank - 1];
	}

	public static float costMult(int rank) {
		return GreenLanternConfig.RANK_COST[rank - 1];
	}

	/** This player's Ring Charge pool (the base pool scaled by rank). */
	public static float maxCharge(Player player) {
		return GreenLanternConfig.MAX_RING_CHARGE * maxChargeMult(rank(player));
	}

	/** +15% above 80% charge, -15% below 20%, else 1. */
	public static float surge(Player player) {
		GreenLanternState s = player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_STATE, null);
		if (s == null) {
			return 1f;
		}
		float frac = s.ringCharge / maxCharge(player);
		if (frac > GreenLanternConfig.SURGE_HIGH) {
			return 1f + GreenLanternConfig.SURGE_BONUS;
		}
		if (frac < GreenLanternConfig.SURGE_LOW) {
			return 1f - GreenLanternConfig.SURGE_BONUS;
		}
		return 1f;
	}

	/**
	 * Every ring damage number goes through this: the Oath, the rank and the Willpower Surge. v0.17.1 balance: their bonuses
	 * ADD (1 + 0.5 Oath + up to 0.25 rank +- 0.15 Surge -- at most x1.9) instead of multiplying (which reached x3.45).
	 */
	public static float power(ServerPlayer player) {
		float oath = GreenLanternOath.isActive(player) ? GreenLanternConfig.OATH_DAMAGE_BONUS : 0f;
		return 1f + oath + (damageMult(rank(player)) - 1f) + (surge(player) - 1f);
	}

	/** Rank 5: every construct is drawn golden-green. */
	public static boolean golden(Player player) {
		return rank(player) >= MAX_RANK;
	}

	// ---------------- earning XP ----------------

	/** Damage a Lantern's ring dealt ({@code GreenLanternRanksEvents} feeds this). */
	public static void onDamageDealt(ServerPlayer player, float amount) {
		if (amount > 0f) {
			award(player, Math.round(amount));
		}
	}

	public static void onKill(ServerPlayer player, LivingEntity victim) {
		award(player, com.projecthero.mod.titanshifter.TitanCombat.isBoss(victim) ? GreenLanternConfig.XP_PER_BOSS_KILL
				: GreenLanternConfig.XP_PER_KILL);
	}

	public static void award(ServerPlayer player, long amount) {
		if (amount <= 0L || !GreenLantern.hasPower(player)) {
			return;
		}
		GreenLanternState c = GreenLantern.state(player).copy();
		int before = rankFor(c.willpowerXp);
		c.willpowerXp += amount;
		GreenLantern.save(player, c);
		int after = rankFor(c.willpowerXp);
		if (after > before) {
			rankedUp(player, after);
		}
	}

	/** Testing / admin: set the rank directly (XP jumps to that rank's threshold). */
	public static void setRank(ServerPlayer player, int rank) {
		int r = Math.max(1, Math.min(MAX_RANK, rank));
		GreenLanternState c = GreenLantern.state(player).copy();
		c.willpowerXp = GreenLanternConfig.RANK_XP[r - 1];
		c.ringCharge = Math.min(c.ringCharge, GreenLanternConfig.MAX_RING_CHARGE * maxChargeMult(r));
		GreenLantern.save(player, c);
	}

	private static void rankedUp(ServerPlayer player, int rank) {
		player.displayClientMessage(Component.translatable("message.projecthero.green_lantern.rank_up",
				Component.translatable(nameKey(rank))).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), false);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP,
				SoundSource.PLAYERS, 1.0f, 0.8f);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT,
				SoundSource.PLAYERS, 1.0f, 1.4f);
	}

	/**
	 * Who should get credit for {@code source}: the Lantern behind it (directly, or through one of their hard-light
	 * constructs), or null.
	 */
	public static ServerPlayer creditFor(DamageSource source) {
		if (source.getEntity() instanceof ServerPlayer p && GreenLantern.hasPower(p)) {
			return p;
		}
		return null;
	}
}
