package com.projecthero.mod.ironman.ability;

import com.projecthero.mod.ironman.IronManSounds;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.ironman.IronManAbilityFx;
import com.projecthero.mod.ironman.IronManArmor;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.IronManTargeting;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.entity.IronManMissileEntity;
import com.projecthero.mod.ironman.suit.IronManSuit;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.29 (agent C): the Mark 7's refreshed kit. Slot layout -- R(1) G(2) X(3) Z(4) V(5) C(6):
 * <pre>
 *   R        Repulsor (builder data): tap 20 / hold 1 s for 30; Sneak+R repulsor dash (25)
 *   G        HOLD: 360-degree Repulsor Shield (shared full-body barrier)
 *   Sneak+G  Flares (advanced): 10 s cooldown
 *   X        fires the weapon picked on the 7-wedge V wheel:
 *              Micro-Missiles   6 x 26 dmg dumb-fire volley (builder data), 160 energy, 8 s cooldown
 *              Homing Missiles  4 x 26 dmg locked volley (builder data), 160 energy, 8 s cooldown
 *              Flamethrower     12 dmg/s + burning, 750 heat bar (builder data)
 *              Wrist Laser      3 s cutting beam, 15 per damage tick, 150 energy, 20 s cooldown -- no overload
 *              Rocket           40 dmg (28 splash, 3.5 radius), 120 energy, 10 s cooldown
 *              Supersonic       the shared 20 s burst
 *              Entity glow      coloured highlight toggle
 *   Z        Unibeam -- fires for as long as Z is held: 28 per damage tick, 100 energy/s, 15 s cooldown after
 *   V        weapon wheel
 *   C        store suit
 * </pre>
 * The Rocket and the Wrist Laser are this class's own Mark 7 versions (the shared ones are the Mark 1 / Mark 4
 * numbers -- the Mark 4 laser's one-shot-and-overload rule stays the Mark 4's); the wheel's other entries run the
 * shared code off the Mark 7's builder numbers. Every energy figure is a base cost (x0.6 on this suit).
 */
public final class IronManMark7 {
	public static final String SUIT_ID = "mark_vii";

	public static final String SHIELD = "mk7_shield";
	public static final String UNIBEAM = "mk7_unibeam";

	// ---- tuning ----
	public static final int FLARE_COOLDOWN = 10 * 20;

	public static final float UNIBEAM_DAMAGE = 28f;
	public static final float UNIBEAM_ENERGY_PER_TICK = 100f / 20f; // 100 energy/s
	public static final int UNIBEAM_COOLDOWN = 15 * 20;
	public static final double UNIBEAM_RANGE = 32.0;
	public static final IronManHeldBeam.Spec BEAM = new IronManHeldBeam.Spec(SUIT_ID, UNIBEAM, UNIBEAM_DAMAGE,
			UNIBEAM_ENERGY_PER_TICK, UNIBEAM_COOLDOWN, UNIBEAM_RANGE);

	public static final float ROCKET_DAMAGE = 40f;
	public static final float ROCKET_SPLASH = 28f;
	public static final float ROCKET_RADIUS = 3.5f;
	public static final float ROCKET_ENERGY = 120f;
	public static final int ROCKET_COOLDOWN = 10 * 20;

	public static final int LASER_TICKS = 3 * 20;
	public static final float LASER_DAMAGE = 15f;
	public static final float LASER_ENERGY = 150f;
	public static final int LASER_COOLDOWN = 20 * 20;
	public static final double LASER_RANGE = 40.0;

	private static final Map<UUID, Long> LASER_UNTIL = new HashMap<>();

	private IronManMark7() {
	}

	public static void clearSessionState() {
		LASER_UNTIL.clear();
	}

	public static boolean laserFiring(ServerPlayer player) {
		return LASER_UNTIL.containsKey(player.getUUID());
	}

	// ------------------------------------------------------------------ dispatch

	/** Called from {@link IronManAbilities#trigger} for the Mark 7's own slot ids (G, Z). */
	public static void trigger(ServerPlayer player, IronManSuit suit, String ability, boolean pressed) {
		switch (ability) {
			case SHIELD -> IronManMark6.shieldSlot(player, suit, pressed, () -> IronManFlares.fire(player, true, FLARE_COOLDOWN));
			case UNIBEAM -> {
				if (pressed) {
					IronManHeldBeam.start(player, suit, BEAM);
				} else {
					IronManHeldBeam.stop(player, true);
				}
			}
			default -> {
			}
		}
	}

	/** True when the Mark 7 runs its own version of this weapon-wheel choice (the X slot). */
	public static boolean handlesWheelChoice(IronManSuit suit, String chosen) {
		return SUIT_ID.equals(suit.id())
				&& (IronManAbilities.ROCKET.equals(chosen) || IronManAbilities.WRIST_LASER.equals(chosen));
	}

	/** The X slot, for a choice {@link #handlesWheelChoice} claimed. */
	public static void fireWheel(ServerPlayer player, IronManSuit suit, String chosen, boolean pressed) {
		if (!pressed) {
			return;
		}
		if (IronManAbilities.ROCKET.equals(chosen)) {
			rocket(player, suit);
		} else if (IronManAbilities.WRIST_LASER.equals(chosen)) {
			startLaser(player, suit);
		}
	}

	// ------------------------------------------------------------------ Rocket

	private static void rocket(ServerPlayer player, IronManSuit suit) {
		if (!IronManMark6.requireHelmet(player, SUIT_ID) || !IronManAbilities.cooldownReady(player, SUIT_ID, IronManAbilities.ROCKET)
				|| !pay(player, suit, ROCKET_ENERGY)) {
			return;
		}
		ServerLevel level = (ServerLevel) player.level();
		Vec3 shoulder = player.getEyePosition().add(0, 0.15, 0);
		Vec3 dir = IronManTargeting.aim(player, shoulder, player.getLookAngle(), 100);
		IronManMissileEntity missile = new IronManMissileEntity(level, player, dir.scale(1.5))
				.withDamage(ROCKET_DAMAGE, ROCKET_SPLASH)
				.withBlastRadius(ROCKET_RADIUS)
				.withBreaksBlocks();
		missile.setPos(shoulder.x + dir.x, shoulder.y + dir.y, shoulder.z + dir.z);
		level.addFreshEntity(missile);
		IronManAbilityFx.play(player, IronManAbilityFx.ROCKET, 14);
		IronManSounds.move(player, IronManSounds.ROCKET_LAUNCH, 1.1f, 1.0f);
		IronManSounds.loop(player, IronManSounds.THRUSTER, 0.6f, 1.2f);
		TonyStark.triggerCooldown(player, SUIT_ID, IronManAbilities.ROCKET, ROCKET_COOLDOWN);
	}

	// ------------------------------------------------------------------ Wrist Laser

	private static void startLaser(ServerPlayer player, IronManSuit suit) {
		if (LASER_UNTIL.containsKey(player.getUUID())) {
			return;
		}
		if (!IronManArmor.hasChestplate(player, SUIT_ID)) {
			player.displayClientMessage(Component.translatable("message.projecthero.ironman.need_chest"), true);
			return;
		}
		if (!IronManAbilities.cooldownReady(player, SUIT_ID, IronManAbilities.WRIST_LASER) || !pay(player, suit, LASER_ENERGY)) {
			return;
		}
		LASER_UNTIL.put(player.getUUID(), player.level().getGameTime() + LASER_TICKS);
		IronManSounds.move(player, IronManSounds.LASER_START, 1.0f, 1.0f);
		IronManSounds.play(player, IronManSounds.REPULSOR_ZAP, 0.8f, 1.2f);
		player.displayClientMessage(Component.translatable("message.projecthero.ironman.mk7.laser_firing")
				.withStyle(ChatFormatting.RED), true);
	}

	private static void stopLaser(ServerPlayer player, boolean cooldown) {
		if (LASER_UNTIL.remove(player.getUUID()) == null) {
			return;
		}
		if (cooldown) {
			TonyStark.triggerCooldown(player, SUIT_ID, IronManAbilities.WRIST_LASER, LASER_COOLDOWN);
		}
		IronManSounds.play(player, IronManSounds.LASER_END, 0.9f, 1.0f);
	}

	private static void tickLaser(ServerPlayer player) {
		long until = LASER_UNTIL.getOrDefault(player.getUUID(), 0L);
		long now = player.level().getGameTime();
		if (now >= until || !IronManArmor.hasChestplate(player, SUIT_ID)) {
			stopLaser(player, true);
			return;
		}
		ServerLevel level = (ServerLevel) player.level();
		Vec3 look = player.getLookAngle();
		Vec3 right = IronManAbilities.rightOf(player, look);
		Vec3 origin = player.getEyePosition().add(look.scale(0.5)).add(right.scale(0.35)).add(0, -0.35, 0);
		LivingEntity locked = IronManTargeting.lockedWithin(player, LASER_RANGE);
		LivingEntity target = locked != null ? locked : AbilityHelpers.raycastEntity(player, LASER_RANGE);
		var blockHit = AbilityHelpers.raycastBlock(player, LASER_RANGE);
		Vec3 end;
		if (target != null) {
			end = target.position().add(0, target.getBbHeight() * 0.5, 0);
		} else if (blockHit.getType() != HitResult.Type.MISS) {
			end = blockHit.getLocation();
		} else {
			end = origin.add(look.scale(LASER_RANGE));
		}
		IronManAbilityFx.hold(player, IronManAbilityFx.LASER);
		IronManAbilities.broadcastBeam(player, origin, end, 3); // kind 3 = thin red laser
		if (target != null) {
			AbilityHelpers.hurt(player, target, LASER_DAMAGE);
			target.igniteForSeconds(1);
		} else if (now % 4 == 0 && blockHit.getType() == HitResult.Type.BLOCK && AbilityHelpers.canGrief()) {
			BlockPos bp = blockHit.getBlockPos();
			float speed = level.getBlockState(bp).getDestroySpeed(level, bp);
			if (speed >= 0f && speed < 3.0f) {
				level.destroyBlock(bp, false, player);
			}
		}
		if (now % 2 == 0) {
			level.sendParticles(ParticleTypes.SMALL_FLAME, end.x, end.y, end.z, 2, 0.08, 0.08, 0.08, 0.01);
		}
		if (now % 4 == 0) {
			IronManSounds.play(player, IronManSounds.LASER, 0.7f, 0.95f + (player.tickCount % 3) * 0.05f);
		}
	}

	// ------------------------------------------------------------------ ticking / shutdown

	/** Per-tick from {@link com.projecthero.mod.ironman.IronManSuitTicker} while a powered suit is worn. */
	public static void tick(ServerPlayer player, IronManSuit suit) {
		if (!SUIT_ID.equals(suit.id())) {
			shutDown(player);
			return;
		}
		IronManHeldBeam.tick(player, suit);
		if (LASER_UNTIL.containsKey(player.getUUID())) {
			tickLaser(player);
		}
	}

	public static void shutDown(ServerPlayer player) {
		stopLaser(player, true);
		IronManHeldBeam.stopFor(player, SUIT_ID, true);
	}

	/** Test hook: one tick of the Mark 7 kit without the rest of the suit ticker. */
	public static void tickForTest(ServerPlayer player, IronManSuit suit) {
		tick(player, suit);
	}

	private static boolean pay(ServerPlayer player, IronManSuit suit, float base) {
		float cost = base * suit.energyCostMultiplier();
		if (IronManEnergy.spend(player, SUIT_ID, cost)) {
			return true;
		}
		IronManAbilities.noEnergy(player, cost);
		return false;
	}
}
