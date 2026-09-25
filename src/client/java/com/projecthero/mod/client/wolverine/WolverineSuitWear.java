package com.projecthero.mod.client.wolverine;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import com.projecthero.mod.wolverine.Wolverine;
import com.projecthero.mod.wolverine.WolverineConfig;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * How torn the Wolverine Suit looks (0 = clean .. 4 = almost torn off). The stage the wearer's health calls
 * for is applied at once, but the suit repairs itself slowly: it drops one stage every
 * {@code SUIT_REPAIR_TICKS_PER_STAGE}, so a fully shredded suit is whole again 20 seconds after the last damage.
 * Weakly keyed by entity, so nothing outlives the wearer.
 */
public final class WolverineSuitWear {
	/** entity -> {stage, game time the stage last changed}. */
	private static final Map<LivingEntity, long[]> WEAR = Collections.synchronizedMap(new WeakHashMap<>());

	private WolverineSuitWear() {
	}

	private static int targetStage(LivingEntity wearer) {
		if (wearer instanceof Player p && Wolverine.resurrecting(p)) {
			return 4; // Death Surge: almost completely torn off
		}
		float health = wearer.getHealth();
		if (wearer instanceof Player p && Wolverine.clawsOut(p)) {
			// v0.12.20: the wound from deploying the claws never tears the suit -- counted as unhurt for a few seconds
			com.projecthero.mod.wolverine.data.WolverineState s = p.getAttachedOrElse(
					com.projecthero.mod.attachment.ModAttachments.WOLVERINE_STATE, null);
			if (s != null && p.level().getGameTime() - s.clawsChangedAt < 3 * 20) {
				health = Math.min(wearer.getMaxHealth(), health + WolverineConfig.CLAW_DEPLOY_DAMAGE);
			}
		}
		float frac = wearer.getMaxHealth() <= 0 ? 1.0f : health / wearer.getMaxHealth();
		return frac > 0.9f ? 0 : frac > 0.65f ? 1 : frac > 0.4f ? 2 : 3;
	}

	public static int stage(LivingEntity wearer) {
		long now = wearer.level().getGameTime();
		int target = targetStage(wearer);
		long[] w = WEAR.computeIfAbsent(wearer, e -> new long[] {target, now});
		if (target >= w[0]) {
			w[0] = target;
			w[1] = now; // health still calls for this much damage: the repair timer keeps restarting
		} else if (now - w[1] >= WolverineConfig.SUIT_REPAIR_TICKS_PER_STAGE) {
			w[0]--;
			w[1] = now;
		}
		return (int) w[0];
	}
}
