package com.projecthero.mod.ironman.ability;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.ironman.IronManAbilityFx;
import com.projecthero.mod.ironman.IronManArmor;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.IronManTargeting;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.data.TonyStarkState;
import com.projecthero.mod.ironman.entity.IronManMissileEntity;
import com.projecthero.mod.ironman.suit.IronManSuit;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.29 (agent C): the Mark 6's own kit, built round the film's new-element arc reactor. Slot layout -- R(1) G(2)
 * X(3) Z(4) V(5) C(6):
 * <pre>
 *   R        Repulsor (builder data): tap 17 / hold 1 s for 26; Sneak+R repulsor dash (22)
 *   G        HOLD: 360-degree Repulsor Shield (the shared full-body barrier)
 *   Sneak+G  Sonic Clap: 22 dmg, 50 energy, 8 s cooldown
 *   X        Arc Reactor Surge: 10 s of +50% damage dealt and +50% flight / walk speed; 250 energy to start, then
 *            150 energy/s; 40 s cooldown after it ends (X again ends it early)
 *   Sneak+X  Flares (advanced): 10 s cooldown
 *   Z        Unibeam -- fires for as long as Z is held: 24 per damage tick, 90 energy/s, 18 s cooldown after
 *   V        Shoulder Barrage: 6 homing micro-missiles, 20 dmg each, 180 energy, 14 s cooldown
 *   Sneak+V  coloured entity highlight toggle
 *   C        store suit
 * </pre>
 * Every energy figure is a base cost; the Mark 6's 0.6 cost multiplier applies on top. The surge timer lives in
 * {@link TonyStarkState#abilityReadyAt} under {@link #SURGE_KEY} (synced, so the client's flight and HUD read it); the
 * barrage volley is a per-player server map, cleared on server stop.
 */
public final class IronManMark6 {
	public static final String SUIT_ID = "mark_6";

	// slot ability ids (each also its own cooldown key, so the HUD strip shows it)
	public static final String SHIELD = "mk6_shield";
	public static final String SURGE = "mk6_surge";
	public static final String UNIBEAM = "mk6_unibeam";
	public static final String BARRAGE = "mk6_barrage";

	// ---- tuning ----
	public static final int SURGE_TICKS = 10 * 20;
	public static final float SURGE_DAMAGE_MULTIPLIER = 1.5f;
	public static final double SURGE_SPEED_MULTIPLIER = 1.5;
	public static final float SURGE_START_ENERGY = 250f;
	public static final float SURGE_ENERGY_PER_TICK = 150f / 20f; // 150 energy/s
	public static final int SURGE_COOLDOWN = 40 * 20;

	public static final float SONIC_CLAP_DAMAGE = 22f;
	public static final float SONIC_CLAP_ENERGY = 50f;
	public static final int SONIC_CLAP_COOLDOWN = 8 * 20;

	public static final int FLARE_COOLDOWN = 10 * 20;

	public static final float UNIBEAM_DAMAGE = 24f;
	public static final float UNIBEAM_ENERGY_PER_TICK = 90f / 20f; // 90 energy/s
	public static final int UNIBEAM_COOLDOWN = 18 * 20;
	public static final double UNIBEAM_RANGE = 30.0;
	public static final IronManHeldBeam.Spec BEAM = new IronManHeldBeam.Spec(SUIT_ID, UNIBEAM, UNIBEAM_DAMAGE,
			UNIBEAM_ENERGY_PER_TICK, UNIBEAM_COOLDOWN, UNIBEAM_RANGE);

	public static final int BARRAGE_COUNT = 6;
	public static final float BARRAGE_DAMAGE = 20f;
	public static final float BARRAGE_ENERGY = 180f;
	public static final int BARRAGE_COOLDOWN = 14 * 20;
	private static final int BARRAGE_STAGGER = 3;

	/** Synced surge end time (game time) for this suit. */
	public static final String SURGE_KEY = SUIT_ID + "/mk6_surge_until";
	private static final ResourceLocation SPEED_ID = ProjectHeroMod.id("mk6_surge_speed");
	private static final DustParticleOptions REACTOR_DUST = new DustParticleOptions(new org.joml.Vector3f(0.55f, 0.95f, 1.0f), 1.0f);

	private static final ThreadLocal<Boolean> REISSUING = ThreadLocal.withInitial(() -> false);
	private static final Map<UUID, int[]> BARRAGE_RT = new HashMap<>(); // {pending, total}
	private static final Map<UUID, Long> BARRAGE_NEXT = new HashMap<>();

	private IronManMark6() {
	}

	/** Registers the surge's outgoing-damage boost (called from {@code IronManDamage.initialize}). */
	public static void initialize() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(IronManMark6::allowDamage);
	}

	public static void clearSessionState() {
		BARRAGE_RT.clear();
		BARRAGE_NEXT.clear();
	}

	// ------------------------------------------------------------------ surge state (either side)

	public static long surgeUntil(TonyStarkState state) {
		return state == null ? 0L : state.abilityReadyAt.getOrDefault(SURGE_KEY, 0L);
	}

	public static boolean surging(TonyStarkState state, long gameTime) {
		return surgeUntil(state) > gameTime;
	}

	public static boolean surging(ServerPlayer player) {
		return surging(TonyStark.state(player), player.level().getGameTime());
	}

	/** Flight speed multiplier for the client's directional flight: x1.5 while this suit is surging. */
	public static double flightSpeedMultiplier(TonyStarkState state, String suitId, long gameTime) {
		return SUIT_ID.equals(suitId) && surging(state, gameTime) ? SURGE_SPEED_MULTIPLIER : 1.0;
	}

	/** The cooldown key the HUD strip should read for a slot (the shields run on the shared barrier cooldown). */
	public static String hudCooldownId(String abilityId) {
		return SHIELD.equals(abilityId) || IronManMark7.SHIELD.equals(abilityId) ? IronManAbilities.REPULSOR_BARRIER : abilityId;
	}

	/** The outgoing damage multiplier for a hit dealt by {@code attacker} (1 unless a worn Mark 6 is surging). */
	public static float damageMultiplier(ServerPlayer attacker) {
		return surging(attacker) && IronManArmor.hasChestplate(attacker, SUIT_ID) ? SURGE_DAMAGE_MULTIPLIER : 1f;
	}

	private static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (REISSUING.get() || amount <= 0f || !(source.getEntity() instanceof ServerPlayer attacker) || attacker == entity) {
			return true;
		}
		float mult = damageMultiplier(attacker);
		if (mult == 1f) {
			return true;
		}
		// Fabric's ALLOW_DAMAGE can only veto: cancel the hit and re-issue it at the boosted amount
		REISSUING.set(true);
		try {
			entity.hurt(source, amount * mult);
		} finally {
			REISSUING.set(false);
		}
		return false;
	}

	// ------------------------------------------------------------------ dispatch

	/** Called from {@link IronManAbilities#trigger} for the Mark 6 slot ids. */
	public static void trigger(ServerPlayer player, IronManSuit suit, String ability, boolean pressed) {
		boolean sneak = player.isShiftKeyDown();
		switch (ability) {
			case SHIELD -> shieldSlot(player, suit, pressed, () -> IronManSonicClap.fire(player, SONIC_CLAP_DAMAGE,
					SONIC_CLAP_ENERGY, SONIC_CLAP_COOLDOWN));
			case SURGE -> {
				if (!pressed) {
					return;
				}
				if (sneak) {
					IronManFlares.fire(player, true, FLARE_COOLDOWN);
				} else if (surging(player)) {
					endSurge(player, true);
				} else {
					startSurge(player, suit);
				}
			}
			case UNIBEAM -> {
				if (pressed) {
					IronManHeldBeam.start(player, suit, BEAM);
				} else {
					IronManHeldBeam.stop(player, true);
				}
			}
			case BARRAGE -> {
				if (!pressed) {
					return;
				}
				if (sneak) {
					IronManAbilities.toggleEntityGlowFromWheel(player);
				} else {
					barrage(player, suit);
				}
			}
			default -> {
			}
		}
	}

	/**
	 * The G shield slot both the Mark 6 and Mark 7 use: hold G for the shared 360-degree Repulsor Shield, Sneak+G runs
	 * {@code sneakAction} instead (on the press only -- the release then finds no shield up and does nothing).
	 */
	static void shieldSlot(ServerPlayer player, IronManSuit suit, boolean pressed, Runnable sneakAction) {
		if (pressed) {
			if (player.isShiftKeyDown()) {
				if (!TonyStark.state(player).barrierHeld) {
					sneakAction.run();
				}
			} else {
				IronManAbilities.startBarrier(player, suit);
			}
		} else {
			IronManAbilities.stopBarrier(player, suit, true);
		}
	}

	// ------------------------------------------------------------------ Arc Reactor Surge

	private static void startSurge(ServerPlayer player, IronManSuit suit) {
		if (!IronManArmor.hasChestplate(player, SUIT_ID)) {
			player.displayClientMessage(Component.translatable("message.projecthero.ironman.need_chest"), true);
			return;
		}
		if (!IronManAbilities.cooldownReady(player, SUIT_ID, SURGE)) {
			return;
		}
		float cost = SURGE_START_ENERGY * suit.energyCostMultiplier();
		if (!IronManEnergy.spend(player, SUIT_ID, cost)) {
			IronManAbilities.noEnergy(player, cost);
			return;
		}
		TonyStarkState s = TonyStark.state(player).copy();
		s.abilityReadyAt.put(SURGE_KEY, player.level().getGameTime() + SURGE_TICKS);
		player.setAttached(ModAttachments.TONY_STARK_STATE, s);
		applySpeed(player, true);
		ServerLevel level = (ServerLevel) player.level();
		Vec3 chest = reactorPos(player);
		level.sendParticles(ParticleTypes.FLASH, chest.x, chest.y, chest.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, chest.x, chest.y, chest.z, 30, 0.4, 0.5, 0.4, 0.25);
		level.sendParticles(ParticleTypes.END_ROD, chest.x, chest.y, chest.z, 16, 0.2, 0.2, 0.2, 0.15);
		AbilityHelpers.sound(player, SoundEvents.BEACON_POWER_SELECT, 1.2f, 1.6f);
		AbilityHelpers.sound(player, SoundEvents.CONDUIT_ACTIVATE, 0.8f, 1.8f);
		player.displayClientMessage(Component.translatable("message.projecthero.ironman.mk6.surge_on")
				.withStyle(ChatFormatting.AQUA), true);
	}

	/** End the surge (timer, X again, power out, suit off); starts the cooldown when {@code cooldown}. */
	public static void endSurge(ServerPlayer player, boolean cooldown) {
		applySpeed(player, false);
		if (!TonyStark.state(player).abilityReadyAt.containsKey(SURGE_KEY)) {
			return;
		}
		TonyStarkState s = TonyStark.state(player).copy();
		s.abilityReadyAt.remove(SURGE_KEY);
		player.setAttached(ModAttachments.TONY_STARK_STATE, s);
		if (cooldown) {
			TonyStark.triggerCooldown(player, SUIT_ID, SURGE, SURGE_COOLDOWN);
		}
		AbilityHelpers.sound(player, SoundEvents.BEACON_DEACTIVATE, 0.9f, 1.2f);
		player.displayClientMessage(Component.translatable("message.projecthero.ironman.mk6.surge_off")
				.withStyle(ChatFormatting.GRAY), true);
	}

	private static void applySpeed(ServerPlayer player, boolean on) {
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed == null) {
			return;
		}
		boolean has = speed.getModifier(SPEED_ID) != null;
		if (on && !has) {
			speed.addTransientModifier(new AttributeModifier(SPEED_ID, SURGE_SPEED_MULTIPLIER - 1.0,
					AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		} else if (!on && has) {
			speed.removeModifier(SPEED_ID);
		}
	}

	private static Vec3 reactorPos(ServerPlayer player) {
		Vec3 look = Vec3.directionFromRotation(0, player.getYRot());
		return player.position().add(0, player.getBbHeight() * 0.7, 0).add(look.scale(0.32));
	}

	private static void tickSurge(ServerPlayer player, IronManSuit suit) {
		long now = player.level().getGameTime();
		if (surgeUntil(TonyStark.state(player)) <= now) {
			endSurge(player, true);
			return;
		}
		if (!IronManArmor.hasChestplate(player, SUIT_ID)
				|| !IronManEnergy.spend(player, SUIT_ID, SURGE_ENERGY_PER_TICK * suit.energyCostMultiplier())) {
			endSurge(player, true);
			return;
		}
		applySpeed(player, true);
		if (now % 2 != 0) {
			return;
		}
		ServerLevel level = (ServerLevel) player.level();
		Vec3 chest = reactorPos(player);
		// the over-driven reactor: a hot cyan glow pulsing off the chest, sparks crawling over the plating
		level.sendParticles(REACTOR_DUST, chest.x, chest.y, chest.z, 3, 0.08, 0.08, 0.08, 0.0);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + player.getBbHeight() * 0.5,
				player.getZ(), 2, 0.35, 0.6, 0.35, 0.05);
		if (now % 6 == 0) {
			level.sendParticles(ParticleTypes.END_ROD, chest.x, chest.y, chest.z, 1, 0.05, 0.05, 0.05, 0.02);
		}
		// eye glints for everyone else (never in front of the wearer's own camera)
		Vec3 look = player.getLookAngle();
		Vec3 right = IronManAbilities.rightOf(player, look);
		Vec3 eyes = player.getEyePosition().add(look.scale(0.28));
		for (ServerPlayer viewer : level.players()) {
			if (viewer == player || viewer.distanceToSqr(player) > 48 * 48) {
				continue;
			}
			for (int side = -1; side <= 1; side += 2) {
				Vec3 e = eyes.add(right.scale(0.11 * side));
				level.sendParticles(viewer, REACTOR_DUST, false, e.x, e.y, e.z, 1, 0, 0, 0, 0);
			}
		}
		if (now % 20 == 0) {
			AbilityHelpers.sound(player, SoundEvents.BEACON_AMBIENT, 0.6f, 1.8f);
		}
	}

	// ------------------------------------------------------------------ Shoulder Barrage

	private static void barrage(ServerPlayer player, IronManSuit suit) {
		int[] r = BARRAGE_RT.get(player.getUUID());
		if (r != null && r[0] > 0) {
			return; // still launching
		}
		if (!requireHelmet(player, SUIT_ID) || !IronManAbilities.cooldownReady(player, SUIT_ID, BARRAGE)) {
			return;
		}
		float cost = BARRAGE_ENERGY * suit.energyCostMultiplier();
		if (!IronManEnergy.spend(player, SUIT_ID, cost)) {
			IronManAbilities.noEnergy(player, cost);
			return;
		}
		BARRAGE_RT.put(player.getUUID(), new int[] { BARRAGE_COUNT, BARRAGE_COUNT });
		BARRAGE_NEXT.put(player.getUUID(), player.level().getGameTime());
		IronManAbilityFx.play(player, IronManAbilityFx.MISSILES, 24);
		AbilityHelpers.sound(player, SoundEvents.FIREWORK_ROCKET_LAUNCH, 1.0f, 1.1f);
		TonyStark.triggerCooldown(player, SUIT_ID, BARRAGE, BARRAGE_COOLDOWN);
		tickBarrage(player); // the first one leaves right away
	}

	/** Missiles still to launch in the current barrage (0 = none). */
	public static int pendingBarrage(ServerPlayer player) {
		int[] r = BARRAGE_RT.get(player.getUUID());
		return r == null ? 0 : r[0];
	}

	private static void tickBarrage(ServerPlayer player) {
		int[] r = BARRAGE_RT.get(player.getUUID());
		if (r == null || r[0] <= 0) {
			return;
		}
		long now = player.level().getGameTime();
		if (now < BARRAGE_NEXT.getOrDefault(player.getUUID(), 0L)) {
			return;
		}
		ServerLevel level = (ServerLevel) player.level();
		int index = r[1] - r[0];
		Vec3 look = player.getLookAngle();
		Vec3 side = IronManAbilities.rightOf(player, look);
		double s = index % 2 == 0 ? 1 : -1;
		Vec3 shoulder = player.position().add(0, player.getBbHeight() * 0.85, 0).add(side.scale(0.45 * s));
		Vec3 dir = look.add(side.scale(0.5 * s)).add(0, 0.4, 0).normalize();
		IronManMissileEntity missile = new IronManMissileEntity(level, player, dir.scale(0.9))
				.withDamage(BARRAGE_DAMAGE, BARRAGE_DAMAGE * 0.4f)
				.withBlastRadius(1.5f)
				.withHoming();
		LivingEntity target = pickSpreadTarget(player, index);
		if (target != null) {
			missile.withTarget(target);
		}
		missile.setPos(shoulder.x + dir.x * 0.3, shoulder.y + dir.y * 0.3, shoulder.z + dir.z * 0.3);
		level.addFreshEntity(missile);
		AbilityHelpers.sound(player, SoundEvents.FIREWORK_ROCKET_LAUNCH, 0.6f, 1.6f);
		r[0]--;
		BARRAGE_NEXT.put(player.getUUID(), now + BARRAGE_STAGGER);
	}

	// ------------------------------------------------------------------ ticking / shutdown

	/** Per-tick from {@link com.projecthero.mod.ironman.IronManSuitTicker} while a powered suit is worn. */
	public static void tick(ServerPlayer player, IronManSuit suit) {
		if (!SUIT_ID.equals(suit.id())) {
			shutDown(player);
			return;
		}
		if (TonyStark.state(player).abilityReadyAt.containsKey(SURGE_KEY)) {
			tickSurge(player, suit);
		}
		IronManHeldBeam.tick(player, suit);
		if (pendingBarrage(player) > 0) {
			if (IronManArmor.hasHelmet(player, SUIT_ID)) {
				tickBarrage(player);
			} else {
				BARRAGE_RT.remove(player.getUUID());
			}
		}
	}

	/** Everything off (suit removed, depleted, power lost). Cheap when nothing is running. */
	public static void shutDown(ServerPlayer player) {
		if (TonyStark.state(player).abilityReadyAt.containsKey(SURGE_KEY)) {
			endSurge(player, true);
		} else {
			applySpeed(player, false);
		}
		IronManHeldBeam.stopFor(player, SUIT_ID, true);
		BARRAGE_RT.remove(player.getUUID());
	}

	// ------------------------------------------------------------------ helpers (shared with the Mark 7 kit)

	static boolean requireHelmet(ServerPlayer player, String suitId) {
		if (IronManArmor.hasHelmet(player, suitId)) {
			return true;
		}
		player.displayClientMessage(Component.translatable("message.projecthero.ironman.need_helmet"), true);
		return false;
	}

	/** The lock, else the crosshair target, else the index-th nearest hostile around the player (spreads a volley). */
	static LivingEntity pickSpreadTarget(ServerPlayer player, int index) {
		LivingEntity lock = IronManTargeting.locked(player);
		if (lock != null) {
			return lock;
		}
		LivingEntity aimed = IronManAbilities.homingTarget(player);
		if (aimed != null) {
			return aimed;
		}
		java.util.List<LivingEntity> near = com.projecthero.mod.combat.HeroTargets.hostiles(player.level(), player,
				com.projecthero.mod.combat.HeroTargets.around(player.position(), 32.0));
		if (near.isEmpty()) {
			return null;
		}
		near.sort(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(player)));
		return near.get(index % near.size());
	}

	/** Test hook: one tick of the Mark 6 kit without the rest of the suit ticker (no recharge). */
	public static void tickForTest(ServerPlayer player, IronManSuit suit) {
		tick(player, suit);
	}
}
