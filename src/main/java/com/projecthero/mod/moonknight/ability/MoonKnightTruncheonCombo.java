package com.projecthero.mod.moonknight.ability;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.moonknight.MoonKnightAnim;
import com.projecthero.mod.moonknight.MoonKnightConfig;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.4: the Truncheon's 3-hit melee combo. Every melee hit the Moon Knight lands with the summoned truncheon
 * ({@link MoonKnightTruncheon#onMeleeHit}) is one step:
 * <ol>
 *   <li><b>forehand</b> -- a right-to-left swing, the plain hit ({@code TRUNCHEON_HIT_1});</li>
 *   <li><b>backhand</b> -- back across left-to-right, +{@link MoonKnightConfig#TRUNCHEON_BACKHAND_BONUS} x power and a
 *       shove ({@code TRUNCHEON_HIT_2});</li>
 *   <li><b>overhead smash</b> -- both hands down on the target, +{@link MoonKnightConfig#TRUNCHEON_SLAM_BONUS} x power,
 *       heavy knockback and a small lift ({@code TRUNCHEON_SLAM}); the combo then starts over.</li>
 * </ol>
 * A hit less than {@link MoonKnightConfig#TRUNCHEON_COMBO_MIN_GAP} ticks after the last counted one doesn't advance it
 * (so click-spamming can't reach the smash); one more than {@link MoonKnightConfig#TRUNCHEON_COMBO_WINDOW} after starts
 * over at the forehand. Missed swings don't touch it. Each step plays its pose through the synced
 * {@code MoonKnightAction}, so every viewer sees the same swing. At night each hit on a mob also heals
 * {@link MoonKnightConfig#TRUNCHEON_NIGHT_HEAL} x power (as before).
 */
public final class MoonKnightTruncheonCombo {
	/** Per attacker: {step of the last counted hit (1..3), game time of it}. */
	private static final Map<UUID, long[]> COMBO = new ConcurrentHashMap<>();

	private MoonKnightTruncheonCombo() {
	}

	/** The step the last counted hit was (1..3), or 0 if there is none or the window has run out. For HUD / tests. */
	public static int step(ServerPlayer player) {
		long[] c = COMBO.get(player.getUUID());
		if (c == null || player.level().getGameTime() - c[1] > MoonKnightConfig.TRUNCHEON_COMBO_WINDOW) {
			return 0;
		}
		return (int) c[0];
	}

	/** The step the next counted hit will be (1..3). */
	public static int nextStep(ServerPlayer player) {
		int s = step(player);
		return s <= 0 || s >= MoonKnightConfig.TRUNCHEON_COMBO_HITS ? 1 : s + 1;
	}

	/** A truncheon hit landed: advance the combo and deal its step. Returns the step dealt, 0 if it didn't count. */
	static int onHit(ServerPlayer attacker, LivingEntity target) {
		float power = MoonKnightAbilities.power(attacker);
		ServerLevel level = attacker.serverLevel();
		if (target instanceof Mob && MoonKnightAbilities.night(attacker)) {
			attacker.heal(MoonKnightConfig.TRUNCHEON_NIGHT_HEAL * power);
			level.sendParticles(MoonKnightCombat.PALE_BLUE, attacker.getX(), attacker.getY() + 1.2, attacker.getZ(),
					3, 0.3, 0.3, 0.3, 0.0);
		}
		long now = attacker.level().getGameTime();
		long[] c = COMBO.computeIfAbsent(attacker.getUUID(), k -> new long[]{0L, Long.MIN_VALUE / 2});
		if (c[0] > 0 && now - c[1] < MoonKnightConfig.TRUNCHEON_COMBO_MIN_GAP) {
			return 0; // too quick after the last one: a plain hit that doesn't count
		}
		int step = nextStep(attacker);
		c[0] = step;
		c[1] = now;
		Vec3 at = target.position().add(0, target.getBbHeight() * 0.5, 0);
		switch (step) {
			case 1 -> {
				MoonKnightAnim.play(attacker, MoonKnightAnim.TRUNCHEON_HIT_1);
				level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.8f, 1.2f);
			}
			case 2 -> {
				MoonKnightCombat.hit(attacker, target, MoonKnightConfig.TRUNCHEON_BACKHAND_BONUS * power);
				MoonKnightCombat.knock(target, attacker.position(), MoonKnightConfig.TRUNCHEON_BACKHAND_KNOCKBACK * power, 0.0);
				MoonKnightAnim.play(attacker, MoonKnightAnim.TRUNCHEON_HIT_2);
				level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
				level.sendParticles(MoonKnightCombat.MOON, at.x, at.y, at.z, 5, 0.25, 0.25, 0.25, 0.02);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.9f, 0.95f);
			}
			default -> {
				MoonKnightCombat.hit(attacker, target, MoonKnightConfig.TRUNCHEON_SLAM_BONUS * power);
				MoonKnightCombat.knock(target, attacker.position(), MoonKnightConfig.TRUNCHEON_SLAM_KNOCKBACK * power,
						MoonKnightConfig.TRUNCHEON_SLAM_LIFT);
				MoonKnightAnim.play(attacker, MoonKnightAnim.TRUNCHEON_SLAM);
				level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 14, 0.3, 0.3, 0.3, 0.3);
				level.sendParticles(MoonKnightCombat.MOON, at.x, at.y, at.z, 12, 0.3, 0.3, 0.3, 0.02);
				level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 0.4, at.z, 6, 0.15, 0.3, 0.15, 0.05);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0f, 0.7f);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.MACE_SMASH_AIR, SoundSource.PLAYERS, 0.8f, 1.3f);
			}
		}
		return step;
	}

	/** Start over (the truncheon was put away). */
	static void reset(ServerPlayer player) {
		COMBO.remove(player.getUUID());
	}

	static void clearSessionState() {
		COMBO.clear();
	}
}
