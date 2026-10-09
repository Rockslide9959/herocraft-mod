package com.projecthero.mod.punisher.ability;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.punisher.Punisher;
import com.projecthero.mod.punisher.PunisherConfig;
import com.projecthero.mod.punisher.PunisherControl;
import com.projecthero.mod.punisher.entity.FlashbangEntity;
import com.projecthero.mod.squad.Squads;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.15.18 -- C and Shift+C.
 *
 * <ul>
 *   <li><b>C -- Smoke Screen</b>: a thick grey smoke cloud (5-block radius, 6 s) where you stand, seen by everyone.
 *       A mob inside loses its target and cannot pick a new one while it stays in the smoke
 *       ({@link PunisherControl#suppressTarget}); another player inside is blinded (Blindness I) -- never you or your
 *       squad, and only where abilities may affect players at all. 12 s cooldown.</li>
 *   <li><b>Shift+C -- Flashbang</b>: throw a flashbang ({@link FlashbangEntity}) that goes off after 1.5 s. Everything
 *       within 6 blocks of it but you and your squad gets Blindness, Slowness II and Nausea for 8 s, and a mob drops its
 *       target (and cannot take one for 3 s). Blinding flash, loud bang. 12 s cooldown.</li>
 * </ul>
 *
 * Smoke clouds are a static list ticked from {@link Punisher#initialize}'s server tick, cleared on server stop.
 */
public final class PunisherSmoke {
	public static final String SMOKE = "smoke_screen";
	public static final String FLASH = "flashbang";

	private record Cloud(ServerLevel level, UUID owner, Vec3 centre, long until) {
	}

	private static final List<Cloud> CLOUDS = new CopyOnWriteArrayList<>();

	private PunisherSmoke() {
	}

	public static void clearSessionState() {
		CLOUDS.clear();
	}

	// ---------------- C: Smoke Screen ----------------

	public static void smokeScreen(ServerPlayer player) {
		if (!Punisher.hasPower(player) || !Punisher.abilityReady(player, SMOKE)) {
			return;
		}
		deploy(player, player.position());
		Punisher.triggerCooldown(player, SMOKE, PunisherConfig.SMOKE_COOLDOWN_TICKS);
	}

	/** Pop a smoke cloud at {@code at} (the gametests call this directly). */
	public static void deploy(ServerPlayer player, Vec3 at) {
		ServerLevel level = player.serverLevel();
		CLOUDS.add(new Cloud(level, player.getUUID(), at, level.getGameTime() + PunisherConfig.SMOKE_DURATION_TICKS));
		player.swing(InteractionHand.OFF_HAND, true);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.0f, 0.6f);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.LAVA_EXTINGUISH, SoundSource.PLAYERS, 0.8f, 0.5f);
		level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, at.x, at.y + 0.6, at.z, 30, 1.6, 0.6, 1.6, 0.02);
	}

	/** Is {@code e} inside one of the live smoke clouds? */
	public static boolean inSmoke(LivingEntity e) {
		for (Cloud c : CLOUDS) {
			if (c.level == e.level() && c.level.getGameTime() < c.until && inside(c, e)) {
				return true;
			}
		}
		return false;
	}

	private static boolean inside(Cloud c, Entity e) {
		double r = PunisherConfig.SMOKE_RADIUS;
		return AbilityHelpers.distanceSqToBox(e, c.centre.add(0, 1.0, 0)) <= r * r;
	}

	/** Once per server tick: draw every cloud and work it on whoever is inside. */
	public static void tick(MinecraftServer server) {
		for (Cloud c : CLOUDS) {
			long now = c.level.getGameTime();
			if (now >= c.until) {
				CLOUDS.remove(c);
				continue;
			}
			if (now % 2 == 0) {
				double r = PunisherConfig.SMOKE_RADIUS;
				c.level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, c.centre.x, c.centre.y + 0.8, c.centre.z,
						6, r * 0.45, 0.7, r * 0.45, 0.004);
				c.level.sendParticles(ParticleTypes.LARGE_SMOKE, c.centre.x, c.centre.y + 1.0, c.centre.z,
						10, r * 0.45, 0.8, r * 0.45, 0.005);
			}
			ServerPlayer owner = server.getPlayerList().getPlayer(c.owner);
			double r = PunisherConfig.SMOKE_RADIUS + 2.0;
			for (LivingEntity e : c.level.getEntitiesOfClass(LivingEntity.class, HeroTargets.around(c.centre, r),
					e -> e.isAlive() && inside(c, e))) {
				if (e instanceof Mob mob) {
					PunisherControl.suppressTarget(mob, 2);
				} else if (e instanceof Player p && owner != null && p != owner && !Squads.areAllies(owner, p)
						&& HeroTargets.canHarm(owner, p) && com.projecthero.mod.hero.HeroConfig.get().abilityPvpDamage) {
					p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30, 0, true, false, true));
				}
			}
		}
	}

	// ---------------- Shift+C: Flashbang ----------------

	public static void throwFlashbang(ServerPlayer player) {
		if (!Punisher.hasPower(player) || !Punisher.abilityReady(player, FLASH)) {
			return;
		}
		ServerLevel level = player.serverLevel();
		FlashbangEntity f = new FlashbangEntity(level, player);
		Vec3 look = player.getLookAngle();
		f.setPos(player.getX() + look.x * 0.4, player.getEyeY() - 0.15, player.getZ() + look.z * 0.4);
		f.shoot(look.x, look.y + 0.1, look.z, PunisherConfig.FLASHBANG_THROW_SPEED, 0.5f);
		f.setDeltaMovement(f.getDeltaMovement().add(player.getDeltaMovement().scale(0.4)));
		level.addFreshEntity(f);
		player.swing(InteractionHand.OFF_HAND, true);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.8f, 1.2f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LEVER_CLICK, SoundSource.PLAYERS, 0.6f, 1.8f);
		Punisher.triggerCooldown(player, FLASH, PunisherConfig.FLASHBANG_COOLDOWN_TICKS);
	}

	/** The flashbang goes off at {@code at}. {@code thrower} may be null (offline); then only he is unknown, not spared. */
	public static void flash(ServerLevel level, Vec3 at, Entity thrower) {
		level.sendParticles(ParticleTypes.FLASH, at.x, at.y + 0.3, at.z, 2, 0.1, 0.1, 0.1, 0.0);
		level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 0.3, at.z, 40, 0.3, 0.3, 0.3, 0.35);
		level.sendParticles(ParticleTypes.FIREWORK, at.x, at.y + 0.3, at.z, 30, 0.4, 0.4, 0.4, 0.25);
		level.sendParticles(ParticleTypes.POOF, at.x, at.y + 0.2, at.z, 12, 0.5, 0.3, 0.5, 0.02);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.PLAYERS, 4.0f, 1.0f);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 2.0f, 1.9f);

		for (LivingEntity e : flashTargets(level, at, thrower)) {
			int t = PunisherConfig.FLASHBANG_EFFECT_TICKS;
			AbilityHelpers.applyControl(e, MobEffects.BLINDNESS, t, 0);
			AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, t, PunisherConfig.FLASHBANG_SLOW_AMP);
			AbilityHelpers.applyControl(e, MobEffects.CONFUSION, t, 0);
			if (e instanceof Mob mob) {
				PunisherControl.suppressTarget(mob, PunisherConfig.FLASHBANG_NO_TARGET_TICKS);
			}
		}
	}

	/** Everything a flashbang at {@code at} catches: never the thrower, his squad or his pets. */
	public static List<LivingEntity> flashTargets(ServerLevel level, Vec3 at, Entity thrower) {
		return AbilityHelpers.living(level, at, PunisherConfig.FLASHBANG_RADIUS,
				e -> e != thrower && HeroTargets.canHarm(thrower, e) && !Squads.areAllies(thrower, e));
	}
}
