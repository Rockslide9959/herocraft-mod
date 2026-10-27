package com.projecthero.mod.quirk.erasure;

import com.projecthero.mod.hero.PowerSuppression;
import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.Quirks;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LightLayer;

/**
 * v0.17.1 Quirks revamp (docs/QUIRKS_REVAMP_PROPOSAL.md 3.5): the two Erasure passives that reach other creatures, read by
 * {@code ErasureExposedMixin} (on {@code LivingEntity}).
 * <ul>
 *   <li><b>Exposed</b>: an erased creature ({@link PowerSuppression}) takes x{@link ErasureConfig#EXPOSED_DAMAGE_MULT} from
 *       every hit (scaled at the top of {@code hurt}, server side, so armour and resistances still apply to the raised number --
 *       the Punisher-mark pattern), cannot sprint and gets no jump boost. This is what makes Eraser Head strong against plain
 *       mobs, which have no power to lose. The local player's own client also stops sprinting while erased.</li>
 *   <li><b>Underground</b>: an Eraser sneaking in the dark is seen at x{@link ErasureConfig#UNDERGROUND_VISIBILITY} of a mob's
 *       range ({@code getVisibilityPercent}, the same hook vanilla's sneaking and invisibility use).</li>
 * </ul>
 */
public final class ErasureExposed {
	private ErasureExposed() {
	}

	/** Erased right now (server: anyone; client: the local player). */
	public static boolean exposed(Entity e) {
		return e instanceof LivingEntity && PowerSuppression.isSuppressed(e);
	}

	/** {@code LivingEntity#hurt} HEAD: the raised damage an exposed creature takes. */
	public static float scale(LivingEntity self, DamageSource source, float amount) {
		if (amount <= 0f || self.level().isClientSide() || source.is(DamageTypes.GENERIC_KILL) || source.is(DamageTypes.FELL_OUT_OF_WORLD)) {
			return amount;
		}
		return exposed(self) ? amount * ErasureConfig.EXPOSED_DAMAGE_MULT : amount;
	}

	/** {@code getVisibilityPercent} RETURN: an Eraser sneaking in the dark is half as easy to notice. */
	public static double visibility(LivingEntity self, double v) {
		if (!(self instanceof Player p) || !p.isCrouching() || self.level().isClientSide() || !Quirks.activeAs(p, Quirk.ERASURE)) {
			return v;
		}
		return dark(p) ? v * ErasureConfig.UNDERGROUND_VISIBILITY : v;
	}

	/** Light where he stands (block light, and sky light darkened by the time of day) is at or below the Underground level. */
	public static boolean dark(Player p) {
		var pos = p.blockPosition();
		int block = p.level().getBrightness(LightLayer.BLOCK, pos);
		int sky = p.level().getBrightness(LightLayer.SKY, pos) - p.level().getSkyDarken();
		return Math.max(block, sky) <= ErasureConfig.UNDERGROUND_MAX_LIGHT;
	}
}
