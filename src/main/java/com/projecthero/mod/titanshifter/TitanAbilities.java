package com.projecthero.mod.titanshifter;

import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.titanshifter.data.TitanShifterState;
import com.projecthero.mod.titanshifter.entity.TitanFormEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The Titan's abilities (v0.12.31). Every one is server-validated: the shifter must be in the TITAN
 * phase, the form must not be locked, and the cooldown (kept in {@link TitanShifterState}) must be ready.
 * Timings are driven by {@link TitanFormEntity#schedule} so a wind-up hits exactly when the matching
 * animation lands and is dropped if the Titan is gone.
 */
public final class TitanAbilities {
	public static final String PUNCH = "punch";
	public static final String SMASH = "heavy_smash";
	public static final String STOMP = "stomp";
	public static final String LEAP = "leap";
	public static final String ROAR = "roar";
	public static final String REGEN = "regeneration";
	public static final String HARDEN = "hardening";
	public static final String BITE = "bite";

	/** Punch combo: punch, punch, heavy punch -- the counter resets after this many ticks of no swings. */
	private static final int COMBO_WINDOW = 30;

	private TitanAbilities() {
	}

	// ---------------- shared plumbing ----------------

	/** The form to act with, or null (with feedback) if the ability cannot fire right now. */
	private static TitanFormEntity begin(ServerPlayer player, String id) {
		TitanShifterState s = TitanShifter.state(player);
		TitanFormEntity form = TitanShifter.formOf(player);
		if (!s.unlocked || s.phase() != TitanPhase.TITAN || form == null || !form.isAlive()) {
			TitanShifter.say(player, "message.projecthero.titan_shifter.invalid_state", ChatFormatting.GRAY);
			return null;
		}
		if (form.isLocked()) {
			return null;
		}
		int cd = TitanShifter.cooldownRemaining(player, id);
		if (cd > 0) {
			TitanShifter.say(player, "message.projecthero.titan_shifter.ability_cooldown", ChatFormatting.RED,
					Component_translatable(id), String.format(java.util.Locale.ROOT, "%.1f", cd / 20.0));
			return null;
		}
		return form;
	}

	private static net.minecraft.network.chat.Component Component_translatable(String id) {
		return net.minecraft.network.chat.Component.translatable("projecthero.titan_shifter.ability." + id);
	}

	private static boolean stillGood(TitanFormEntity form) {
		return form.isAlive() && !form.isDefeated() && form.formState() == TitanFormEntity.FORM_NORMAL;
	}

	private static ServerLevel level(TitanFormEntity form) {
		return (ServerLevel) form.level();
	}

	// ---------------- Ability 1: Titan Punch (Shift = Titan Kick) ----------------

	public static void punch(ServerPlayer player) {
		TitanFormEntity form = begin(player, PUNCH);
		if (form == null) {
			return;
		}
		var a = TitanShifterConfig.abilities();
		var d = TitanShifterConfig.damage();
		TitanShifter.startCooldown(player, PUNCH, a.punchCooldown, 1);
		ServerLevel level = level(form);
		long now = level.getGameTime();

		if (player.isShiftKeyDown()) {
			// Titan Kick: a wide, low sweep
			form.play("kick");
			level.playSound(null, form.getX(), form.getY(), form.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP,
					SoundSource.HOSTILE, 2.5f, 0.35f);
			form.schedule(7, () -> {
				if (!stillGood(form)) {
					return;
				}
				var box = TitanCombat.fistBox(form, 4.5, form.getBbWidth() * 0.9);
				int n = TitanCombat.hitBox(form, player, new net.minecraft.world.phys.AABB(box.minX, form.getY(), box.minZ,
						box.maxX, form.getY() + form.getBbHeight() * 0.3, box.maxZ), (float) d.kick, d.punchKnockback * 1.4, 0.55,
						form.position());
				meleeFx(level, form, 1.1f, n);
			});
			return;
		}

		int combo = comboStep(player, now);
		boolean heavy = combo >= 2;
		form.play(heavy ? "heavy_punch" : "punch");
		level.playSound(null, form.getX(), form.getY(), form.getZ(), SoundEvents.PLAYER_ATTACK_STRONG,
				SoundSource.HOSTILE, 2.5f, heavy ? 0.3f : 0.45f);
		form.schedule(heavy ? 9 : 6, () -> {
			if (!stillGood(form)) {
				return;
			}
			float dmg = (float) (heavy ? d.heavyPunch : d.punch);
			double kb = heavy ? d.punchKnockback * 1.8 : d.punchKnockback;
			int n = TitanCombat.hitBox(form, player, TitanCombat.fistBox(form, heavy ? 5.5 : 4.5, form.getBbWidth() * 0.55),
					dmg, kb, heavy ? 0.5 : 0.25, form.position());
			meleeFx(level, form, heavy ? 1.3f : 1.0f, n);
			if (heavy) {
				TitanCombat.shake(level, form.position(), 0.5f, 8);
			}
		});
	}

	private static final java.util.Map<java.util.UUID, long[]> COMBO = new java.util.HashMap<>();

	public static void clearSessionState() {
		COMBO.clear();
	}

	private static int comboStep(ServerPlayer player, long now) {
		long[] c = COMBO.computeIfAbsent(player.getUUID(), k -> new long[] { 0, -1000 });
		if (now - c[1] > COMBO_WINDOW) {
			c[0] = 0;
		}
		int step = (int) c[0];
		c[0] = step >= 2 ? 0 : step + 1;
		c[1] = now;
		return step;
	}

	private static void meleeFx(ServerLevel level, TitanFormEntity form, float power, int hits) {
		Vec3 fwd = Vec3.directionFromRotation(0, form.getYRot());
		Vec3 at = form.position().add(fwd.scale(form.getBbWidth() * 0.5 + 3.0)).add(0, form.getBbHeight() * 0.35, 0);
		level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, hits > 0 ? 2 : 1, 0.6, 0.6, 0.6, 0.0);
		level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 6, 0.6, 0.5, 0.6, 0.05);
		if (hits > 0) {
			level.playSound(null, at.x, at.y, at.z, SoundEvents.IRON_GOLEM_ATTACK, SoundSource.HOSTILE, 2.5f * power, 0.4f);
			TitanCombat.shake(level, at, 0.25f * power, 5);
		}
	}

	// ---------------- Ability 2: Heavy Smash ----------------

	public static void heavySmash(ServerPlayer player) {
		TitanFormEntity form = begin(player, SMASH);
		if (form == null) {
			return;
		}
		var a = TitanShifterConfig.abilities();
		var d = TitanShifterConfig.damage();
		TitanShifter.startCooldown(player, SMASH, a.smashCooldown, 2);
		ServerLevel level = level(form);
		form.play("smash");
		form.slowFor(a.smashChargeTicks + 10, (float) a.smashSlowFactor);
		level.playSound(null, form.getX(), form.getY(), form.getZ(), SoundEvents.WARDEN_SONIC_CHARGE,
				SoundSource.HOSTILE, 2.5f, 0.6f);
		// build-up: growing crackle around the fists
		for (int t = 4; t < a.smashChargeTicks; t += 4) {
			int tt = t;
			form.schedule(tt, () -> {
				if (stillGood(form)) {
					Vec3 fwd = Vec3.directionFromRotation(0, form.getYRot());
					Vec3 at = form.position().add(fwd.scale(2.0)).add(0, form.getBbHeight() * 0.7, 0);
					level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 6, 1.2, 0.8, 1.2, 0.1);
					form.steamBurst(level, 2);
				}
			});
		}
		form.schedule(a.smashChargeTicks, () -> {
			form.clearSlow();
			if (!stillGood(form)) {
				return;
			}
			form.lockFor(8);
			Vec3 fwd = Vec3.directionFromRotation(0, form.getYRot());
			Vec3 at = form.position().add(fwd.scale(form.getBbWidth() * 0.5 + 3.5));
			int n = TitanCombat.hitRadius(form, player, at, a.smashRadius, (float) d.heavySmash, d.smashKnockback, 0.7);
			TitanCombat.impactFx(level, at, a.smashRadius * 1.3, 1.6f);
			level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 0.5, at.z, 1, 0, 0, 0, 0);
			TitanCombat.impactBlocks(level, at, a.smashRadius);
			TitanCombat.shake(level, at, 1.0f, 16);
		});
	}

	// ---------------- Ability 3: Titan Stomp ----------------

	public static void stomp(ServerPlayer player) {
		TitanFormEntity form = begin(player, STOMP);
		if (form == null) {
			return;
		}
		var a = TitanShifterConfig.abilities();
		TitanShifter.startCooldown(player, STOMP, a.stompCooldown, 4);
		ServerLevel level = level(form);
		form.play("stomp");
		form.schedule(10, () -> {
			if (!stillGood(form)) {
				return;
			}
			Vec3 c = form.position();
			TitanCombat.hitRadius(form, player, c, a.stompRadius, (float) TitanShifterConfig.damage().stomp,
					TitanShifterConfig.damage().punchKnockback, 0.55);
			TitanCombat.impactFx(level, c, a.stompRadius, 1.2f);
			TitanCombat.ring(level, c, a.stompRadius * 0.6, ParticleTypes.POOF, 20);
			TitanCombat.impactBlocks(level, c, a.stompRadius);
			TitanCombat.shake(level, c, 0.9f, 14);
		});
	}

	// ---------------- Ability 4: Titan Leap ----------------

	public static void leap(ServerPlayer player) {
		TitanFormEntity form = begin(player, LEAP);
		if (form == null) {
			return;
		}
		if (!form.onGround()) {
			TitanShifter.say(player, "message.projecthero.titan_shifter.need_ground", ChatFormatting.GRAY);
			return;
		}
		var a = TitanShifterConfig.abilities();
		TitanShifter.startCooldown(player, LEAP, a.leapCooldown, 3);
		ServerLevel level = level(form);
		form.play("leap");
		level.playSound(null, form.getX(), form.getY(), form.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2.0f, 0.5f);
		// wind-up crouch (the animation), then launch
		form.schedule(6, () -> {
			if (!stillGood(form)) {
				return;
			}
			Vec3 fwd = Vec3.directionFromRotation(0, form.getYRot());
			form.setDeltaMovement(fwd.x * a.leapHorizontal, a.leapVertical, fwd.z * a.leapHorizontal);
			form.hasImpulse = true;
			form.hurtMarked = true;
			form.beginLeap();
			TitanCombat.ring(level, form.position(), 2.5, ParticleTypes.POOF, 16);
			TitanCombat.shake(level, form.position(), 0.6f, 8);
			form.footstep(level, 1.6f, false);
		});
	}

	// ---------------- Ability 5: Titan Roar ----------------

	public static void roar(ServerPlayer player) {
		TitanFormEntity form = begin(player, ROAR);
		if (form == null) {
			return;
		}
		var a = TitanShifterConfig.abilities();
		TitanShifter.startCooldown(player, ROAR, a.roarCooldown, 5);
		ServerLevel level = level(form);
		form.play("roar");
		form.lockFor(30);
		form.schedule(10, () -> {
			if (!stillGood(form)) {
				return;
			}
			Vec3 c = form.position().add(0, form.getBbHeight() * 0.9, 0);
			level.playSound(null, c.x, c.y, c.z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 5.0f, 0.4f);
			level.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 5.0f, 0.5f);
			for (int i = 1; i <= 3; i++) {
				TitanCombat.ring(level, form.position().add(0, i * 1.2, 0), a.roarRadius * i / 3.0,
						ParticleTypes.SONIC_BOOM, 6 * i);
			}
			level.sendParticles(ParticleTypes.CLOUD, c.x, c.y, c.z, 40, 1.5, 1.0, 1.5, 0.4);
			form.steamBurst(level, 6);
			TitanCombat.shake(level, form.position(), 1.0f, 24);
			for (LivingEntity t : TitanCombat.targetsAround(level, form.position(), a.roarRadius, form, player)) {
				boolean boss = TitanCombat.isBoss(t);
				int ticks = (int) (a.roarEffectTicks * (boss ? a.roarBossResistance : 1.0));
				AbilityHelpers.applyControl(t, MobEffects.MOVEMENT_SLOWDOWN, ticks, 2);
				AbilityHelpers.applyControl(t, MobEffects.WEAKNESS, ticks, 1);
				if (boss) {
					continue;
				}
				AbilityHelpers.knockbackFrom(t, form.position(), 1.6);
				AbilityHelpers.push(t, new Vec3(0, 0.3, 0));
				if (t instanceof PathfinderMob mob && !(t instanceof Player)) {
					// intimidation: they lose the shifter and bolt away
					mob.setTarget(null);
					Vec3 away = DefaultRandomPos.getPosAway(mob, 18, 8, form.position());
					if (away != null) {
						mob.getNavigation().moveTo(away.x, away.y, away.z, 1.4);
					}
				}
			}
		});
	}

	// ---------------- Ability 6: Titan Regeneration ----------------

	public static void regeneration(ServerPlayer player) {
		TitanFormEntity form = begin(player, REGEN);
		if (form == null) {
			return;
		}
		var a = TitanShifterConfig.abilities();
		if (form.getHealth() >= form.getMaxHealth()) {
			TitanShifter.say(player, "message.projecthero.titan_shifter.full_health", ChatFormatting.GRAY);
			return;
		}
		long now = player.level().getGameTime();
		TitanShifter.startCooldown(player, REGEN, a.regenCooldown, 6);
		TitanShifterState s = TitanShifter.state(player).copy();
		s.regenUntil = now + a.regenTicks;
		TitanShifter.save(player, s);
		ServerLevel level = level(form);
		form.play("regeneration");
		level.playSound(null, form.getX(), form.getY(), form.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 2.5f, 0.6f);
		level.playSound(null, form.getX(), form.getY(), form.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.HOSTILE, 2.5f, 0.5f);
		form.steamBurst(level, 8);
	}

	// ---------------- N: grab / bite / set down ----------------

	/** N: with an empty hand, pick up the mob you are looking at; with a mob in the hand, bite it. */
	public static void grabOrBite(ServerPlayer player) {
		TitanShifterState s = TitanShifter.state(player);
		TitanFormEntity form = TitanShifter.formOf(player);
		if (!s.unlocked || s.phase() != TitanPhase.TITAN || form == null || !form.isAlive() || form.isLocked()) {
			return;
		}
		LivingEntity held = form.held();
		if (held == null) {
			grab(player, form);
		} else {
			bite(player, form, held);
		}
	}

	private static void grab(ServerPlayer player, TitanFormEntity form) {
		var a = TitanShifterConfig.abilities();
		ServerLevel level = level(form);
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		Vec3 end = eye.add(look.scale(a.grabReach + 12.0));
		LivingEntity best = null;
		double bestDist = Double.MAX_VALUE;
		for (LivingEntity e : TitanCombat.targetsAround(level, form.position(), a.grabReach, form, player)) {
			if (e instanceof Player || e.isPassenger() || e.isVehicle() || TitanCombat.isBoss(e) || e.getBbHeight() > 6.0f
					|| e instanceof TitanFormEntity) {
				continue;
			}
			var hitPos = e.getBoundingBox().inflate(0.8).clip(eye, end);
			if (hitPos.isEmpty()) {
				continue;
			}
			double d = hitPos.get().distanceToSqr(eye);
			if (d < bestDist) {
				bestDist = d;
				best = e;
			}
		}
		if (best == null) {
			TitanShifter.say(player, "message.projecthero.titan_shifter.nothing_to_grab", ChatFormatting.GRAY);
			return;
		}
		form.hold(best);
		form.play("grab");
		form.lockFor(8);
		Vec3 c = best.position().add(0, best.getBbHeight() * 0.5, 0);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.IRON_GOLEM_ATTACK, SoundSource.HOSTILE, 2.0f, 0.5f);
		level.sendParticles(ParticleTypes.POOF, c.x, c.y, c.z, 12, 0.5, 0.5, 0.5, 0.05);
		TitanCombat.shake(level, form.position(), 0.2f, 5);
	}

	private static void bite(ServerPlayer player, TitanFormEntity form, LivingEntity held) {
		int cd = TitanShifter.cooldownRemaining(player, BITE);
		if (cd > 0) {
			TitanShifter.say(player, "message.projecthero.titan_shifter.ability_cooldown", ChatFormatting.RED,
					Component_translatable(BITE), String.format(java.util.Locale.ROOT, "%.1f", cd / 20.0));
			return;
		}
		var a = TitanShifterConfig.abilities();
		TitanShifter.startCooldown(player, BITE, a.biteCooldown, 7);
		ServerLevel level = level(form);
		form.play("bite");
		form.lockFor(14);
		form.schedule(8, () -> {
			if (!stillGood(form) || form.held() != held) {
				return;
			}
			AbilityHelpers.hurtBurst(player, held, (float) a.biteDamage);
			Vec3 c = held.position().add(0, held.getBbHeight() * 0.5, 0);
			level.playSound(null, c.x, c.y, c.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.HOSTILE, 2.5f, 0.4f);
			level.playSound(null, c.x, c.y, c.z, SoundEvents.RAVAGER_ATTACK, SoundSource.HOSTILE, 2.5f, 0.5f);
			level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, c.x, c.y, c.z, 10, 0.4, 0.4, 0.4, 0.2);
			level.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 20, 0.5, 0.5, 0.5, 0.3);
			TitanCombat.shake(level, form.position(), 0.3f, 6);
			if (!held.isAlive()) {
				form.releaseHeld();
			}
		});
	}

	/** Shift+N: set the held mob gently down. */
	public static void letDown(ServerPlayer player) {
		TitanShifterState s = TitanShifter.state(player);
		TitanFormEntity form = TitanShifter.formOf(player);
		if (!s.unlocked || s.phase() != TitanPhase.TITAN || form == null || !form.isAlive() || form.held() == null) {
			return;
		}
		form.startLowering();
	}

	// ---------------- Titan Hardening ----------------

	public static void hardening(ServerPlayer player) {
		TitanFormEntity form = begin(player, HARDEN);
		if (form == null) {
			return;
		}
		var a = TitanShifterConfig.abilities();
		long now = player.level().getGameTime();
		TitanShifter.startCooldown(player, HARDEN, a.hardenCooldown, 6);
		TitanShifterState s = TitanShifter.state(player).copy();
		s.hardenUntil = now + a.hardenTicks;
		TitanShifter.save(player, s);
		ServerLevel level = level(form);
		form.play("hardening");
		form.setHardened(true);
		Vec3 c = form.position().add(0, form.getBbHeight() * 0.5, 0);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 3.0f, 0.5f);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.5f, 0.6f);
		level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 50, form.getBbWidth() * 0.6, form.getBbHeight() * 0.4,
				form.getBbWidth() * 0.6, 0.08);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, net.minecraft.world.level.block.Blocks.AMETHYST_BLOCK.defaultBlockState()),
				c.x, c.y, c.z, 60, form.getBbWidth() * 0.6, form.getBbHeight() * 0.4, form.getBbWidth() * 0.6, 0.1);
		TitanCombat.shake(level, c, 0.4f, 8);
	}
}
