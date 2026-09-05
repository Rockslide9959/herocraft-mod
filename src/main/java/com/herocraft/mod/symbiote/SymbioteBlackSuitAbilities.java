package com.herocraft.mod.symbiote;

import com.herocraft.mod.hero.power.AbilityHelpers;
import com.herocraft.mod.spider.SpiderMan;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Black Suit Spider-Man's extra Symbiote-flavoured abilities. Per the spec's explicit "use
 * contextual abilities... rather than requiring an unreasonable number of new keybinds" instruction,
 * these do NOT get new keys -- they are sneak-modified variants of Spider-Man's existing slots,
 * dispatched from {@code SpiderManAbilityManager.handle} only while
 * {@link SymbioteHostType#SPIDER_MAN} is active:
 *
 * <ul>
 *   <li><b>Sneak + X (Web Yank's key)</b> -- Symbiote Web Tendrils: a tendril pull
 *       ({@link SymbioteTendrils#pull}) that costs no web reserve at all, paced by its own cooldown
 *       instead. Web Zip's key (G) already overloads sneak for its own "pin to the wall" behaviour
 *       and Web Yank (X) has none, which is why this lands here rather than there.</li>
 *   <li><b>Sneak + Z (Web Shot's key)</b> -- Multi-Tendril Attack: launches tendrils at up to three
 *       nearby enemies at once.</li>
 *   <li><b>Sneak + C (the wall-crawl toggle's key), only while airborne</b> -- Symbiote Slam
 *       Enhancement: a stronger version of the Normal host's Symbiote Slam.</li>
 * </ul>
 *
 * <p>Symbiote Recovery (slightly faster regen than a Normal host, still nowhere near overpowered) is
 * a passive with no key at all -- see {@code SpiderPassives#tick}.
 */
public final class SymbioteBlackSuitAbilities {
	/** Keys into {@code SpiderManState.abilityReadyAt} -- synced, so {@code SymbioteHud} can show these
	 *  cooldowns client-side exactly like every other Spider-Man ability, instead of the
	 *  server-only-and-therefore-invisible-to-the-HUD map this used before. */
	public static final String TENDRIL_ZIP = "symbiote_tendril_zip";
	public static final String MULTI_TENDRIL = "symbiote_multi_tendril";
	public static final String SLAM_ENHANCED = "symbiote_slam_enhanced";

	private static final int CD_TENDRIL_ZIP = 120;    // 6s
	private static final int CD_MULTI_TENDRIL = 160;  // 8s
	private static final int CD_SLAM_ENHANCED = 200;  // 10s

	private SymbioteBlackSuitAbilities() {
	}

	/**
	 * Symbiote Web Tendrils: a Tendril Grab-style pull ({@link SymbioteTendrils#pull}) that costs no
	 * web reserve at all -- an organic alternative to Web Yank, paced by its own cooldown instead so
	 * the real web resource still matters (per the spec's explicit balance instruction).
	 */
	public static void tendrilZip(ServerPlayer player) {
		if (!SpiderMan.abilityReady(player, TENDRIL_ZIP)) {
			return;
		}
		if (SymbioteTendrils.pull(player, 15.0)) {
			SpiderMan.triggerCooldown(player, TENDRIL_ZIP, CD_TENDRIL_ZIP);
		}
	}

	/** Multi-Tendril Attack: strike up to 3 nearby enemies at once. */
	public static void multiTendrilAttack(ServerPlayer player) {
		if (!SpiderMan.abilityReady(player, MULTI_TENDRIL)) {
			return;
		}
		ServerLevel level = AbilityHelpers.level(player);
		Vec3 center = player.getEyePosition().add(player.getLookAngle().scale(2.0));
		int hit = 0;
		for (LivingEntity target : AbilityHelpers.enemiesAround(player, center, 8.0)) {
			if (hit >= 3) {
				break;
			}
			if (AbilityHelpers.hurt(player, target, 6.0f)) {
				AbilityHelpers.line(level, player.getEyePosition(),
						target.position().add(0, target.getBbHeight() * 0.5, 0), ParticleTypes.SQUID_INK, 3.0);
				AbilityHelpers.knockbackFrom(target, player.position(), 0.5);
				hit++;
			}
		}
		if (hit == 0) {
			return;
		}
		SpiderMan.triggerCooldown(player, MULTI_TENDRIL, CD_MULTI_TENDRIL);
		AbilityHelpers.sound(player, SoundEvents.HOSTILE_SWIM, 1.0f, 0.4f);
	}

	/** Symbiote Slam Enhancement: a stronger aerial ground slam, only meaningful while airborne. */
	public static boolean slamEnhanced(ServerPlayer player) {
		if (player.onGround()) {
			return false;
		}
		if (!SpiderMan.abilityReady(player, SLAM_ENHANCED)) {
			return false;
		}
		SpiderMan.triggerCooldown(player, SLAM_ENHANCED, CD_SLAM_ENHANCED);
		ServerLevel level = AbilityHelpers.level(player);
		Vec3 center = player.position();
		for (LivingEntity target : AbilityHelpers.enemiesAround(player, center, 5.0)) {
			float damage = 10.0f + player.getRandom().nextFloat() * 2.0f;
			if (AbilityHelpers.hurt(player, target, damage)) {
				AbilityHelpers.knockbackFrom(target, center, 1.2);
			}
		}
		AbilityHelpers.burst(level, center, ParticleTypes.SQUID_INK, 35, 0.7);
		AbilityHelpers.burst(level, center, ParticleTypes.CRIT, 20, 0.9);
		AbilityHelpers.sound(player, SoundEvents.GENERIC_BIG_FALL, 1.0f, 0.5f);
		return true;
	}
}
