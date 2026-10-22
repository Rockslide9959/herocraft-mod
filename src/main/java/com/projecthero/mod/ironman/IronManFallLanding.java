package com.projecthero.mod.ironman;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

/**
 * v0.16.7 (user): "make it so that if the player falls a large distance they still do the iron man landing pose if wearing
 * an armour, right now they only do the pose when they fly into the ground". Every Iron Man suit -- Era I, Era II, the
 * Hulkbuster -- lands a long fall (at least {@value #FALL_MIN} blocks, not flying) with its landing pose, scaled by the
 * height the way a flight landing is scaled by its speed:
 * <ul>
 *   <li><b>light</b> (a drop of {@value #FALL_MIN} .. ~7 blocks): a touchdown -- the knees soak it up, a shallow version
 *       of the three-point landing (deeper the further it fell), a small dust puff;</li>
 *   <li><b>heavy</b> ({@linkplain #fallIntensity intensity} {@value #HEAVY_AT}+, ~7+ blocks): the full superhero landing
 *       with the ground impact, and the same area slam as flying into the ground ({@link IronManLandingSlam#trySlam}).</li>
 * </ul>
 * The Hulkbuster plays its own body clips ({@link HulkbusterLanding#land}); every other suit gets the player-model pose
 * through the {@code IronManPosePayload} ({@link IronManAbilityFx#IM_LAND_BASE} + intensity step), so everyone sees it.
 * No double landing: a flight that touched down ({@link #noteFlightLanding}) clears the fall, and one touchdown only ever
 * lands once.
 */
public final class IronManFallLanding {
	/** A fall at least this high (blocks) lands with the pose. */
	public static final double FALL_MIN = 5.0;
	/** Intensity at or above which a fall lands heavy (the full slam). */
	public static final float HEAVY_AT = HulkbusterLanding.HEAVY_AT;
	/** One landing per touchdown: another within this many ticks is the same one. */
	public static final int SAME_LANDING_TICKS = 10;

	/** The last landing per player (gametests / debugging); {@code flight} for one a flight made. */
	public record Landing(long time, float intensity, boolean heavy, String suitId, boolean flight) {
	}

	private static final Map<UUID, Double> FALL_PEAK = new HashMap<>();
	private static final Map<UUID, Landing> LAST = new HashMap<>();

	private IronManFallLanding() {
	}

	/** 0..1 for a fall of {@code height} blocks (5 = light, ~7 = the heavy threshold, 14+ = full). */
	public static float fallIntensity(double height) {
		return (float) Mth.clamp((height - 2.5) / 11.5, 0.0, 1.0);
	}

	/** The worn suit with a landing: a whole Iron Man suit, or null. */
	static String suitOf(ServerPlayer p) {
		String id = IronManArmor.wornSuitId(p);
		return id != null && IronManArmor.wearingFullSuit(p, id) ? id : null;
	}

	/** Every server tick: follow a fall (not flying) and land it on touchdown when it was long enough. */
	public static void tick(ServerPlayer p) {
		UUID uuid = p.getUUID();
		if (suitOf(p) == null || IronManFlight.isFlying(p) || p.getAbilities().flying || p.isInWater() || p.isPassenger()
				|| p.onClimbable() || p.isFallFlying() || p.isSpectator()
				|| com.projecthero.mod.ironman.suit.ThrusterPackFlight.packFlying(p)) {
			FALL_PEAK.remove(uuid);
			return;
		}
		if (!p.onGround()) {
			FALL_PEAK.merge(uuid, p.getY(), Math::max);
			return;
		}
		Double peak = FALL_PEAK.remove(uuid);
		if (peak != null && peak - p.getY() >= FALL_MIN) {
			land(p, fallIntensity(peak - p.getY()));
		}
	}

	/** A flight just touched down (IronManLandingSlam.onLanded): it is THE landing -- no fall landing on top of it. */
	public static void noteFlightLanding(ServerPlayer p, float intensity, boolean heavy) {
		FALL_PEAK.remove(p.getUUID());
		LAST.put(p.getUUID(), new Landing(p.level().getGameTime(), intensity, heavy, IronManArmor.wornSuitId(p), true));
	}

	/** Land a fall of {@code intensity} (0..1). Public for the gametests. False if it was the same touchdown again. */
	public static boolean land(ServerPlayer p, float intensity) {
		String suit = suitOf(p);
		if (suit == null) {
			return false;
		}
		long now = p.level().getGameTime();
		Landing prev = LAST.get(p.getUUID());
		if (prev != null && now - prev.time() < SAME_LANDING_TICKS) {
			return false; // one landing per touchdown (a flight landing already played)
		}
		boolean heavy = intensity >= HEAVY_AT;
		LAST.put(p.getUUID(), new Landing(now, intensity, heavy, suit, false));
		if (HulkbusterLanding.SUIT.equals(suit)) {
			HulkbusterLanding.land(p, intensity, heavy);
		} else {
			IronManAbilityFx.play(p, IronManAbilityFx.IM_LAND_BASE + step(intensity), IronManAbilityFx.IM_LAND_TICKS);
		}
		if (heavy) {
			IronManLandingSlam.trySlam(p);
		}
		return true;
	}

	/** The intensity sent to the clients, in tenths (0..10). */
	public static int step(float intensity) {
		return Mth.clamp(Math.round(intensity * 10f), 0, 10);
	}

	/** The last landing {@code p} made (flight or fall), or null (gametests). */
	public static Landing last(ServerPlayer p) {
		return LAST.get(p.getUUID());
	}

	/** Server stopped: drop the per-player maps (ServerStateReset). */
	public static void clearSessionState() {
		FALL_PEAK.clear();
		LAST.clear();
	}
}
