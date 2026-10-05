package com.projecthero.mod.ironman.ability;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.ironman.IronManAbilityFx;
import com.projecthero.mod.ironman.IronManArmor;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.IronManFlight;
import com.projecthero.mod.ironman.IronManTargeting;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.data.TonyStarkState;
import com.projecthero.mod.ironman.entity.IronManMissileEntity;
import com.projecthero.mod.ironman.suit.IronManSuit;
import com.projecthero.mod.network.IronManBeamPayload;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.27: the Mark III's own kit (agent D). Slot layout -- R(1) G(2) X(3) Z(4) V(5) C(6):
 * <pre>
 *   R        Repulsor (shared, agent C)
 *   G        fire the weapon selected on the Mark III weapon wheel:
 *              Rockets         one AoE rocket, 35 dmg, 100 energy, 10 s cooldown
 *              Miniguns        HOLD: up to 5 s of hit-scan fire, 10 dmg every 0.5 s, 5 energy/s, 15 s cooldown after
 *              Micro-Missiles  4 homing shoulder missiles, 25 dmg each, 150 energy, 12 s cooldown
 *   Sneak+G  Sonic Clap (shared {@link IronManSonicClap}): 20 dmg, 50 energy, 8 s cooldown
 *   X        Flares (shared {@link IronManFlares}, advanced); while flying also a 25 s supersonic boost
 *   Sneak+X  JARVIS scan ({@link IronManJarvisScan}): 20 energy, 3 s cooldown
 *   Z        Unibeam -- fires for as long as Z is held: 20 dmg per damage tick, 80 energy/s, 20 s cooldown after
 *   V        opens the Mark III weapon wheel (Rockets / Miniguns / Micro-Missiles)
 *   Sneak+V  Energy Shield toggle: blocks ALL damage, 10 energy/s, 10% of every blocked hit is paid in energy
 *   C        store suit
 * </pre>
 * The weapon choice and the shield flag live in {@link TonyStarkState#abilityReadyAt} under {@code mark_iii/...} keys
 * (the same trick the Mark 1 highlight timer uses) so they persist and sync without growing the state codec. The
 * short-lived hold state (minigun, Unibeam, micro-missile volley) is a per-player server map, cleared on server stop.
 */
public final class IronManMark3 {
	public static final String SUIT_ID = "mark_iii";

	// slot ability ids
	public static final String ARSENAL = "mk3_arsenal";
	public static final String FLARES = "mk3_flares";
	public static final String UNIBEAM = "mk3_unibeam";
	public static final String WHEEL = "mk3_weapon_wheel";

	// weapon-wheel options (also their cooldown keys)
	public static final String ROCKETS = "mk3_rockets";
	public static final String MINIGUN = "mk3_minigun";
	public static final String MICRO_MISSILES = "mk3_micro_missiles";
	public static final String[] WEAPONS = { ROCKETS, MINIGUN, MICRO_MISSILES };

	// sneak variants
	public static final String SONIC_CLAP = "mk3_sonic_clap";
	public static final String JARVIS_SCAN = "mk3_jarvis_scan";
	public static final String SHIELD = "mk3_shield";

	/** The S2C {@code IronManWeaponWheelPayload} ability string that opens the Mark III wheel instead of the Mark VII one. */
	public static final String OPEN_WHEEL = "mk3";

	// ---- tuning ----
	public static final float ROCKET_DAMAGE = 35f;
	public static final float ROCKET_ENERGY = 100f;
	public static final int ROCKET_COOLDOWN = 10 * 20;
	public static final float ROCKET_BLAST_RADIUS = 3.0f;

	public static final float MINIGUN_DAMAGE = 10f;
	public static final int MINIGUN_SHOT_INTERVAL = 10; // 0.5 s
	public static final int MINIGUN_MAX_TICKS = 5 * 20;
	public static final int MINIGUN_COOLDOWN = 15 * 20;
	public static final float MINIGUN_ENERGY_PER_TICK = 5f / 20f; // 5 energy/s
	public static final double MINIGUN_RANGE = 48.0;

	public static final int MICRO_COUNT = 4;
	public static final float MICRO_DAMAGE = 25f;
	public static final float MICRO_ENERGY = 150f;
	public static final int MICRO_COOLDOWN = 12 * 20;
	private static final int MICRO_STAGGER = 3;

	public static final float SONIC_CLAP_DAMAGE = 20f;
	public static final float SONIC_CLAP_ENERGY = 50f;
	public static final int SONIC_CLAP_COOLDOWN = 8 * 20;

	public static final int FLARE_COOLDOWN = 10 * 20;
	public static final int SUPERSONIC_BOOST_TICKS = 25 * 20;

	public static final float UNIBEAM_DAMAGE = 20f;
	public static final float UNIBEAM_ENERGY_PER_TICK = 80f / 20f; // 80 energy/s
	public static final int UNIBEAM_COOLDOWN = 20 * 20;
	private static final double UNIBEAM_RANGE = 28.0;

	public static final float SHIELD_ENERGY_PER_TICK = 10f / 20f; // 10 energy/s
	public static final float SHIELD_HIT_ENERGY_SHARE = 0.10f;

	private static final String WEAPON_KEY = SUIT_ID + "/mk3_weapon_choice";
	private static final String SHIELD_KEY = SUIT_ID + "/mk3_shield_on";

	/** Beam kind for a minigun tracer ({@link IronManBeamPayload}; drawn by the client's IronManAbilityVisuals). */
	public static final int TRACER_BEAM = 4;

	private static final Map<UUID, Runtime> RUNTIME = new HashMap<>();

	private static final class Runtime {
		long minigunStart = 0L; // 0 = not firing
		long minigunNextShot = 0L;
		boolean unibeam = false;
		int pendingMicro = 0;
		long microNext = 0L;
		int microTotal = 0;
	}

	private IronManMark3() {
	}

	public static void clearSessionState() {
		RUNTIME.clear();
	}

	private static Runtime rt(ServerPlayer player) {
		return RUNTIME.computeIfAbsent(player.getUUID(), u -> new Runtime());
	}

	// ------------------------------------------------------------------ weapon selection

	/** The weapon G fires, read from a (server or client-synced) state. */
	public static String selectedWeapon(TonyStarkState state) {
		if (state == null) {
			return ROCKETS;
		}
		long idx = state.abilityReadyAt.getOrDefault(WEAPON_KEY, 0L);
		return idx >= 0 && idx < WEAPONS.length ? WEAPONS[(int) idx] : ROCKETS;
	}

	public static String selectedWeapon(ServerPlayer player) {
		return selectedWeapon(TonyStark.state(player));
	}

	public static boolean isWeapon(String id) {
		for (String w : WEAPONS) {
			if (w.equals(id)) {
				return true;
			}
		}
		return false;
	}

	public static void selectWeapon(ServerPlayer player, String weapon) {
		for (int i = 0; i < WEAPONS.length; i++) {
			if (WEAPONS[i].equals(weapon)) {
				if (WEAPONS[i].equals(selectedWeapon(player))) {
					return;
				}
				stopMinigun(player, true);
				TonyStarkState s = TonyStark.state(player).copy();
				s.abilityReadyAt.put(WEAPON_KEY, (long) i);
				player.setAttached(ModAttachments.TONY_STARK_STATE, s);
				AbilityHelpers.sound(player, SoundEvents.UI_BUTTON_CLICK.value(), 0.4f, 1.6f);
				player.displayClientMessage(Component.translatable("message.projecthero.ironman.mk3.weapon_selected",
						Component.translatable("hud.projecthero.ironman.ability." + weapon)).withStyle(ChatFormatting.GOLD), true);
				return;
			}
		}
	}

	// ------------------------------------------------------------------ shield state

	public static boolean shieldOn(TonyStarkState state) {
		return state != null && state.abilityReadyAt.containsKey(SHIELD_KEY);
	}

	public static boolean shieldOn(ServerPlayer player) {
		return shieldOn(TonyStark.state(player));
	}

	private static void setShield(ServerPlayer player, boolean on) {
		if (shieldOn(player) == on) {
			return;
		}
		TonyStarkState s = TonyStark.state(player).copy();
		if (on) {
			s.abilityReadyAt.put(SHIELD_KEY, 1L);
		} else {
			s.abilityReadyAt.remove(SHIELD_KEY);
		}
		player.setAttached(ModAttachments.TONY_STARK_STATE, s);
	}

	public static boolean minigunFiring(ServerPlayer player) {
		Runtime r = RUNTIME.get(player.getUUID());
		return r != null && r.minigunStart != 0L;
	}

	public static boolean unibeamFiring(ServerPlayer player) {
		Runtime r = RUNTIME.get(player.getUUID());
		return r != null && r.unibeam;
	}

	// ------------------------------------------------------------------ dispatch

	/** Called from {@link IronManAbilities#trigger} for the four Mark III slot ids. */
	public static void trigger(ServerPlayer player, IronManSuit suit, String ability, boolean pressed) {
		boolean sneak = player.isShiftKeyDown();
		switch (ability) {
			case ARSENAL -> {
				if (pressed) {
					if (sneak) {
						IronManSonicClap.fire(player, SONIC_CLAP_DAMAGE, SONIC_CLAP_ENERGY, SONIC_CLAP_COOLDOWN);
					} else {
						fireSelected(player, suit);
					}
				} else {
					stopMinigun(player, true);
				}
			}
			case FLARES -> {
				if (!pressed) {
					return;
				}
				if (sneak) {
					IronManJarvisScan.run(player, suit);
				} else {
					boolean flying = IronManFlight.isFlying(player);
					IronManFlares.fire(player, true, FLARE_COOLDOWN);
					if (flying) {
						IronManFlares.supersonicBoost(player, SUPERSONIC_BOOST_TICKS, 0f);
					}
				}
			}
			case UNIBEAM -> {
				if (pressed) {
					startUnibeam(player, suit);
				} else {
					stopUnibeam(player, true);
				}
			}
			case WHEEL -> {
				if (!pressed) {
					return;
				}
				if (sneak) {
					toggleShield(player, suit);
				} else if (requireHelmet(player)) {
					ServerPlayNetworking.send(player, new com.projecthero.mod.network.IronManWeaponWheelPayload(OPEN_WHEEL));
				}
			}
			default -> {
			}
		}
	}

	private static void fireSelected(ServerPlayer player, IronManSuit suit) {
		switch (selectedWeapon(player)) {
			case MINIGUN -> startMinigun(player, suit);
			case MICRO_MISSILES -> microMissiles(player, suit);
			default -> rocket(player, suit);
		}
	}

	// ------------------------------------------------------------------ Rockets

	private static void rocket(ServerPlayer player, IronManSuit suit) {
		if (!requireHelmet(player) || !cooldownReady(player, ROCKETS) || !pay(player, suit, ROCKET_ENERGY)) {
			return;
		}
		ServerLevel level = (ServerLevel) player.level();
		Vec3 shoulder = player.getEyePosition().add(0, 0.15, 0);
		Vec3 dir = IronManTargeting.aim(player, shoulder, player.getLookAngle(), 100);
		// same AoE rocket the other marks fire (and, like theirs, it breaks blocks only where griefing is on)
		IronManMissileEntity missile = new IronManMissileEntity(level, player, dir.scale(1.4))
				.withDamage(ROCKET_DAMAGE, ROCKET_DAMAGE * 0.7f)
				.withBlastRadius(ROCKET_BLAST_RADIUS)
				.withBreaksBlocks();
		missile.setPos(shoulder.x + dir.x, shoulder.y + dir.y, shoulder.z + dir.z);
		level.addFreshEntity(missile);
		IronManAbilityFx.play(player, IronManAbilityFx.ROCKET, 14);
		AbilityHelpers.sound(player, SoundEvents.FIREWORK_ROCKET_LAUNCH, 1.2f, 0.9f);
		TonyStark.triggerCooldown(player, SUIT_ID, ROCKETS, ROCKET_COOLDOWN);
	}

	// ------------------------------------------------------------------ Micro-Missiles

	private static void microMissiles(ServerPlayer player, IronManSuit suit) {
		Runtime r = rt(player);
		if (r.pendingMicro > 0) {
			return; // still launching
		}
		if (!requireHelmet(player) || !cooldownReady(player, MICRO_MISSILES) || !pay(player, suit, MICRO_ENERGY)) {
			return;
		}
		r.pendingMicro = MICRO_COUNT;
		r.microTotal = MICRO_COUNT;
		r.microNext = player.level().getGameTime();
		IronManAbilityFx.play(player, IronManAbilityFx.MISSILES, 24);
		AbilityHelpers.sound(player, SoundEvents.FIREWORK_ROCKET_LAUNCH, 1.0f, 1.2f);
		TonyStark.triggerCooldown(player, SUIT_ID, MICRO_MISSILES, MICRO_COOLDOWN);
		tickMicroMissiles(player, r); // the first one leaves right away
	}

	private static void tickMicroMissiles(ServerPlayer player, Runtime r) {
		long now = player.level().getGameTime();
		if (r.pendingMicro <= 0 || now < r.microNext) {
			return;
		}
		ServerLevel level = (ServerLevel) player.level();
		int index = r.microTotal - r.pendingMicro;
		Vec3 look = player.getLookAngle();
		Vec3 side = look.cross(new Vec3(0, 1, 0));
		side = side.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : side.normalize();
		double s = index % 2 == 0 ? 1 : -1;
		// alternate shoulders, kicked up and out, then the missile steers itself onto its target
		Vec3 shoulder = player.position().add(0, player.getBbHeight() * 0.85, 0).add(side.scale(0.45 * s));
		Vec3 dir = look.add(side.scale(0.45 * s)).add(0, 0.35, 0).normalize();
		IronManMissileEntity missile = new IronManMissileEntity(level, player, dir.scale(0.9))
				.withDamage(MICRO_DAMAGE, MICRO_DAMAGE * 0.4f)
				.withBlastRadius(1.5f)
				.withHoming();
		LivingEntity target = pickMicroTarget(player, index);
		if (target != null) {
			missile.withTarget(target);
		}
		missile.setPos(shoulder.x + dir.x * 0.3, shoulder.y + dir.y * 0.3, shoulder.z + dir.z * 0.3);
		level.addFreshEntity(missile);
		AbilityHelpers.sound(player, SoundEvents.FIREWORK_ROCKET_LAUNCH, 0.6f, 1.6f);
		r.pendingMicro--;
		r.microNext = now + MICRO_STAGGER;
	}

	/** The lock, else the crosshair target, else the index-th nearest hostile around the player (spreads the volley). */
	private static LivingEntity pickMicroTarget(ServerPlayer player, int index) {
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

	// ------------------------------------------------------------------ Miniguns

	private static void startMinigun(ServerPlayer player, IronManSuit suit) {
		Runtime r = rt(player);
		if (r.minigunStart != 0L) {
			return; // already spinning (key repeat)
		}
		if (!requireChest(player) || !cooldownReady(player, MINIGUN)) {
			return;
		}
		if (!IronManEnergy.has(player, SUIT_ID, MINIGUN_ENERGY_PER_TICK * 20f * suit.energyCostMultiplier())) {
			noEnergy(player, MINIGUN_ENERGY_PER_TICK * 20f);
			return;
		}
		long now = player.level().getGameTime();
		r.minigunStart = now;
		r.minigunNextShot = now;
		AbilityHelpers.sound(player, SoundEvents.PISTON_EXTEND, 0.8f, 1.6f);
		tickMinigun(player, suit, r); // the first burst leaves on the press, so even a quick tap fires
	}

	/** Stop the miniguns (key released, time up, power out, suit off); starts the 15 s cooldown if they were firing. */
	public static void stopMinigun(ServerPlayer player, boolean cooldown) {
		Runtime r = RUNTIME.get(player.getUUID());
		if (r == null || r.minigunStart == 0L) {
			return;
		}
		r.minigunStart = 0L;
		if (cooldown) {
			TonyStark.triggerCooldown(player, SUIT_ID, MINIGUN, MINIGUN_COOLDOWN);
		}
		AbilityHelpers.sound(player, SoundEvents.PISTON_CONTRACT, 0.8f, 1.4f);
	}

	private static void tickMinigun(ServerPlayer player, IronManSuit suit, Runtime r) {
		long now = player.level().getGameTime();
		if (now - r.minigunStart >= MINIGUN_MAX_TICKS || !IronManArmor.hasChestplate(player, SUIT_ID)
				|| !IronManEnergy.spend(player, SUIT_ID, MINIGUN_ENERGY_PER_TICK * suit.energyCostMultiplier())) {
			stopMinigun(player, true);
			return;
		}
		ServerLevel level = (ServerLevel) player.level();
		Vec3 look = player.getLookAngle();
		Vec3 right = rightOf(player, look);
		// v0.14.27: the guns sit on the shoulders -- alternate the barrels each burst
		double side = (now / 2) % 2 == 0 ? 1 : -1;
		Vec3 muzzle = player.position().add(0, player.getBbHeight() * 0.86, 0).add(right.scale(0.42 * side)).add(look.scale(0.4));
		LivingEntity locked = IronManTargeting.lockedWithin(player, MINIGUN_RANGE);
		LivingEntity target = locked != null ? locked : AbilityHelpers.raycastEntity(player, MINIGUN_RANGE);
		Vec3 end;
		if (target != null) {
			end = target.position().add(0, target.getBbHeight() * 0.5, 0);
		} else {
			var bh = AbilityHelpers.raycastBlock(player, MINIGUN_RANGE);
			end = bh.getType() != HitResult.Type.MISS ? bh.getLocation() : player.getEyePosition().add(look.scale(MINIGUN_RANGE));
		}
		IronManAbilityFx.hold(player, IronManAbilityFx.MINIGUN);
		if (now % 2 == 0) {
			Vec3 jitter = new Vec3((level.random.nextDouble() - 0.5) * 0.4, (level.random.nextDouble() - 0.5) * 0.4,
					(level.random.nextDouble() - 0.5) * 0.4);
			broadcastBeam(player, muzzle, end.add(jitter), TRACER_BEAM);
			level.sendParticles(ParticleTypes.SMALL_FLAME, muzzle.x, muzzle.y, muzzle.z, 1, 0.02, 0.02, 0.02, 0.0);
			level.sendParticles(ParticleTypes.CRIT, end.x, end.y, end.z, 2, 0.15, 0.15, 0.15, 0.1);
			AbilityHelpers.sound(player, SoundEvents.CROSSBOW_SHOOT, 0.55f, 1.8f + level.random.nextFloat() * 0.2f);
		}
		if (now >= r.minigunNextShot) {
			r.minigunNextShot = now + MINIGUN_SHOT_INTERVAL;
			if (target != null) {
				// its own 0.5 s counter paces the damage, so the hit must land through any leftover i-frames
				AbilityHelpers.hurtBurst(player, target, MINIGUN_DAMAGE);
			}
		}
	}

	// ------------------------------------------------------------------ Unibeam (held)

	private static void startUnibeam(ServerPlayer player, IronManSuit suit) {
		Runtime r = rt(player);
		if (r.unibeam) {
			return;
		}
		if (!requireChest(player) || !cooldownReady(player, UNIBEAM)) {
			return;
		}
		if (!IronManEnergy.has(player, SUIT_ID, UNIBEAM_ENERGY_PER_TICK * suit.energyCostMultiplier())) {
			noEnergy(player, UNIBEAM_ENERGY_PER_TICK * 20f);
			return;
		}
		r.unibeam = true;
		AbilityHelpers.sound(player, SoundEvents.BEACON_ACTIVATE, 1.4f, 0.4f);
		player.displayClientMessage(Component.translatable("message.projecthero.ironman.unibeam_firing"), true);
	}

	public static void stopUnibeam(ServerPlayer player, boolean cooldown) {
		Runtime r = RUNTIME.get(player.getUUID());
		if (r == null || !r.unibeam) {
			return;
		}
		r.unibeam = false;
		if (cooldown) {
			TonyStark.triggerCooldown(player, SUIT_ID, UNIBEAM, UNIBEAM_COOLDOWN);
		}
		AbilityHelpers.sound(player, SoundEvents.BEACON_DEACTIVATE, 0.8f, 0.6f);
	}

	private static void tickUnibeam(ServerPlayer player, IronManSuit suit) {
		if (!IronManArmor.hasChestplate(player, SUIT_ID)
				|| !IronManEnergy.spend(player, SUIT_ID, UNIBEAM_ENERGY_PER_TICK * suit.energyCostMultiplier())) {
			stopUnibeam(player, true);
			return;
		}
		long now = player.level().getGameTime();
		ServerLevel level = (ServerLevel) player.level();
		Vec3 chest0 = player.position().add(0, player.getBbHeight() * 0.62, 0);
		Vec3 dir = IronManTargeting.aim(player, chest0, player.getLookAngle(), UNIBEAM_RANGE);
		Vec3 chest = chest0.add(dir.scale(0.4));
		Vec3 end = chest.add(dir.scale(UNIBEAM_RANGE));
		IronManAbilityFx.hold(player, IronManAbilityFx.UNIBEAM);
		if (now % 2 == 0) {
			broadcastBeam(player, chest, end, 2);
		}
		if (now % 10 == 0) {
			var bh = AbilityHelpers.raycastBlock(player, UNIBEAM_RANGE);
			if (bh.getType() == HitResult.Type.BLOCK && AbilityHelpers.canGrief()) {
				BlockPos bp = bh.getBlockPos();
				float speed = level.getBlockState(bp).getDestroySpeed(level, bp);
				if (speed >= 0f && speed < 3.0f) {
					level.destroyBlock(bp, true, player);
				}
			}
			AbilityHelpers.sound(player, SoundEvents.GENERIC_EXPLODE, 0.4f, 0.7f);
		}
		// the same per-tick, i-frame-paced damage model as the shared Unibeam (a hit lands every ~0.5 s)
		for (LivingEntity e : AbilityHelpers.enemiesAround(player, chest.add(dir.scale(12)), 12.0)) {
			if (e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(chest).normalize().dot(dir) > 0.9) {
				AbilityHelpers.hurt(player, e, UNIBEAM_DAMAGE);
				e.igniteForSeconds(2);
			}
		}
	}

	// ------------------------------------------------------------------ Energy Shield

	private static void toggleShield(ServerPlayer player, IronManSuit suit) {
		if (shieldOn(player)) {
			setShield(player, false);
			AbilityHelpers.sound(player, SoundEvents.BEACON_DEACTIVATE, 0.6f, 1.3f);
			player.displayClientMessage(Component.translatable("message.projecthero.ironman.mk3.shield_down"), true);
			return;
		}
		if (!requireChest(player)) {
			return;
		}
		if (!IronManEnergy.has(player, SUIT_ID, SHIELD_ENERGY_PER_TICK * 20f * suit.energyCostMultiplier())) {
			noEnergy(player, SHIELD_ENERGY_PER_TICK * 20f);
			return;
		}
		setShield(player, true);
		AbilityHelpers.sound(player, SoundEvents.CONDUIT_ACTIVATE, 1.0f, 1.4f);
		player.displayClientMessage(Component.translatable("message.projecthero.ironman.mk3.shield_up"), true);
	}

	private static void tickShield(ServerPlayer player, IronManSuit suit) {
		if (!IronManArmor.hasChestplate(player, SUIT_ID)
				|| !IronManEnergy.spend(player, SUIT_ID, SHIELD_ENERGY_PER_TICK * suit.energyCostMultiplier())) {
			dropShield(player);
			return;
		}
		IronManAbilityFx.hold(player, IronManAbilityFx.BARRIER);
	}

	private static void dropShield(ServerPlayer player) {
		if (!shieldOn(player)) {
			return;
		}
		setShield(player, false);
		AbilityHelpers.sound(player, SoundEvents.BEACON_DEACTIVATE, 0.6f, 1.2f);
		player.displayClientMessage(Component.translatable("message.projecthero.ironman.mk3.shield_failed")
				.withStyle(ChatFormatting.RED), true);
	}

	/**
	 * Called from {@link com.projecthero.mod.ironman.IronManDamage} before any other suit mitigation: with the shield up
	 * the hit is cancelled outright and 10% of it is paid out of suit energy instead (dropping the shield if that empties
	 * the suit). Returns true when the hit was absorbed.
	 */
	public static boolean absorb(ServerPlayer player, String suitId, DamageSource source, float amount) {
		if (!SUIT_ID.equals(suitId) || !shieldOn(player) || !IronManArmor.hasChestplate(player, SUIT_ID)
				|| source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return false;
		}
		float tax = amount * SHIELD_HIT_ENERGY_SHARE;
		if (!IronManEnergy.spend(player, SUIT_ID, tax)) {
			IronManEnergy.setEnergy(player, SUIT_ID, 0f);
			dropShield(player);
		}
		ServerLevel level = (ServerLevel) player.level();
		level.playSound(null, player.blockPosition(), SoundEvents.SHIELD_BLOCK, net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 1.5f);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + player.getBbHeight() * 0.6, player.getZ(),
				8, 0.5, 0.5, 0.5, 0.05);
		return true;
	}

	// ------------------------------------------------------------------ ticking / shutdown

	/** Per-tick from {@link com.projecthero.mod.ironman.IronManSuitTicker} while a powered suit is worn. */
	public static void tick(ServerPlayer player, IronManSuit suit) {
		if (!SUIT_ID.equals(suit.id())) {
			shutDown(player);
			return;
		}
		Runtime r = RUNTIME.get(player.getUUID());
		if (r != null) {
			if (r.minigunStart != 0L) {
				tickMinigun(player, suit, r);
			}
			if (r.unibeam) {
				tickUnibeam(player, suit);
			}
			if (r.pendingMicro > 0) {
				if (IronManArmor.hasHelmet(player, SUIT_ID)) {
					tickMicroMissiles(player, r);
				} else {
					r.pendingMicro = 0;
				}
			}
		}
		if (shieldOn(player)) {
			tickShield(player, suit);
		}
	}

	/** Everything off (suit removed, depleted, overloaded, power lost). Starts the hold cooldowns if they were running. */
	public static void shutDown(ServerPlayer player) {
		Runtime r = RUNTIME.get(player.getUUID());
		if (r != null) {
			stopMinigun(player, true);
			stopUnibeam(player, true);
			r.pendingMicro = 0;
		}
		if (shieldOn(player)) {
			setShield(player, false);
		}
	}

	/** Test hook: drive one tick of the held abilities without the rest of the suit ticker (no recharge). */
	public static void tickForTest(ServerPlayer player, IronManSuit suit) {
		tick(player, suit);
	}

	// ------------------------------------------------------------------ helpers

	private static Vec3 rightOf(ServerPlayer player, Vec3 look) {
		Vec3 right = look.cross(new Vec3(0, 1, 0));
		if (right.lengthSqr() < 1.0E-6) {
			double yaw = Math.toRadians(player.getYRot());
			right = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
		}
		return right.normalize();
	}

	private static void broadcastBeam(ServerPlayer player, Vec3 start, Vec3 end, int kind) {
		IronManBeamPayload payload = new IronManBeamPayload(start, end, kind);
		for (ServerPlayer viewer : IronManBeamRecipients.recipients(player, start, end)) {
			ServerPlayNetworking.send(viewer, payload);
		}
	}

	private static boolean requireChest(ServerPlayer player) {
		if (IronManArmor.hasChestplate(player, SUIT_ID)) {
			return true;
		}
		player.displayClientMessage(Component.translatable("message.projecthero.ironman.need_chest"), true);
		return false;
	}

	static boolean requireHelmet(ServerPlayer player) {
		if (IronManArmor.hasHelmet(player, SUIT_ID)) {
			return true;
		}
		player.displayClientMessage(Component.translatable("message.projecthero.ironman.need_helmet"), true);
		return false;
	}

	static boolean cooldownReady(ServerPlayer player, String abilityId) {
		if (TonyStark.abilityReady(player, SUIT_ID, abilityId)) {
			return true;
		}
		player.displayClientMessage(Component.translatable("message.projecthero.ironman.on_cooldown",
				String.format(java.util.Locale.ROOT, "%.1f",
						TonyStark.abilityCooldownRemaining(player, SUIT_ID, abilityId) / 20.0f)), true);
		return false;
	}

	static boolean pay(ServerPlayer player, IronManSuit suit, float base) {
		float cost = base * suit.energyCostMultiplier();
		if (IronManEnergy.spend(player, SUIT_ID, cost)) {
			return true;
		}
		noEnergy(player, cost);
		return false;
	}

	private static void noEnergy(ServerPlayer player, float required) {
		player.displayClientMessage(Component.translatable("message.projecthero.ironman.not_enough_energy",
				Math.round(required)).withStyle(ChatFormatting.RED), true);
	}
}
