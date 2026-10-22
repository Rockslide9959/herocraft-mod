package com.projecthero.mod.titanshifter;

import java.util.HashSet;
import java.util.Set;

import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.titanshifter.entity.TitanFormEntity;
import com.projecthero.mod.titanshifter.entity.TitanRockEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7 (user, Titan revamp): routes every key (tap and Shift) to the selected Titan's kit ({@link TitanMove#of}) and
 * holds the type-specific moves. The shared moves (punch combo, kick, smash, leap, stomp, roar, regeneration, hardening,
 * grab / bite) stay in {@link TitanAbilities}. Every move is server-validated by {@link TitanAbilities#begin}, and each
 * hit lands on its {@link TitanMove#impactTick} so it meets the clip's contact frame.
 */
public final class TitanKit {
	private TitanKit() {
	}

	/** A key press inside the Titan. */
	public static void handle(ServerPlayer player, int slot, boolean shift) {
		TitanFormEntity form = TitanShifter.formOf(player);
		if (form == null) {
			return;
		}
		TitanMove move = TitanMove.of(form.titanType(), slot, shift);
		if (move == null) {
			return;
		}
		switch (move) {
			case PUNCH -> TitanAbilities.punch(player);
			case KICK -> TitanAbilities.kick(player);
			case HEAVY_SMASH -> TitanAbilities.heavySmash(player);
			case LEAP -> TitanAbilities.leap(player);
			case STOMP -> TitanAbilities.stomp(player);
			case ROAR -> TitanAbilities.roar(player);
			case REGEN -> TitanAbilities.regeneration(player);
			case HARDEN -> TitanAbilities.hardening(player);
			case NAPE_GUARD -> napeGuard(player);
			case UPPERCUT -> uppercut(player);
			case RAGE -> rage(player);
			case DROPKICK -> dropkick(player);
			case ELBOW -> elbow(player);
			case GROUND_POUND -> groundPound(player);
			case SHOULDER_CHARGE -> shoulderCharge(player);
			case BRACE -> brace(player);
			case ROUNDHOUSE -> roundhouse(player);
			case AXE_KICK -> axeKick(player);
			case SWEEP_KICK -> sweepKick(player);
			case CRYSTAL_FISTS -> crystalFists(player);
			case SCREAM -> scream(player);
			case FASTBALL -> fastball(player);
			case ROCK_SCATTER -> rockScatter(player);
			case BOULDER_THROW -> boulderThrow(player);
			case BOULDER_VOLLEY -> boulderVolley(player);
			default -> {
			}
		}
	}

	// ---------------- shared plumbing ----------------

	/** begin + the limb this move needs ({@code arms} / {@code legs} at least), then the cooldown and the clip. Null if refused. */
	private static TitanFormEntity start(ServerPlayer player, TitanMove move, int arms, int legs) {
		TitanFormEntity form = TitanAbilities.begin(player, move.id);
		if (form == null) {
			return null;
		}
		if (form.armsLeft() < arms) {
			TitanShifter.say(player, "message.projecthero.titan_shifter.no_arms", ChatFormatting.GRAY);
			return null;
		}
		if (form.legsLeft() < legs) {
			TitanShifter.say(player, "message.projecthero.titan_shifter.no_leg", ChatFormatting.GRAY);
			return null;
		}
		TitanShifter.startCooldown(player, move.id, move.cooldown(), move.ordinal());
		if (move.anim != null) {
			form.play(move.anim);
		}
		return form;
	}

	private static void sound(TitanFormEntity form, net.minecraft.sounds.SoundEvent s, float vol, float pitch) {
		form.level().playSound(null, form.getX(), form.getY(), form.getZ(), s, SoundSource.HOSTILE, vol, pitch);
	}

	private static void sound(TitanFormEntity form, net.minecraft.core.Holder<net.minecraft.sounds.SoundEvent> s, float vol, float pitch) {
		form.level().playSound(null, form.getX(), form.getY(), form.getZ(), s, SoundSource.HOSTILE, vol, pitch);
	}

	private static Vec3 fwd(TitanFormEntity form) {
		return Vec3.directionFromRotation(0, form.getYRot());
	}

	private static final DustParticleOptions RAGE_RED = new DustParticleOptions(new org.joml.Vector3f(0.9f, 0.1f, 0.05f), 2.0f);
	private static final DustParticleOptions CRYSTAL = new DustParticleOptions(new org.joml.Vector3f(0.7f, 0.9f, 1.0f), 1.6f);

	// ---------------- every Titan: Shift+V Nape Guard ----------------

	/** Shift+V: a hand clapped over the nape for 4 s -- nape hits deal normal damage and can never cut the shifter out. */
	public static void napeGuard(ServerPlayer player) {
		TitanFormEntity form = start(player, TitanMove.NAPE_GUARD, 1, 0);
		if (form == null) {
			return;
		}
		form.startNapeGuard(TitanShifterConfig.sharedMoves().napeGuardTicks);
		sound(form, SoundEvents.ARMOR_EQUIP_IRON, 2.5f, 0.5f);
		Vec3 back = fwd(form).scale(-form.getBbWidth() * 0.25);
		Vec3 nape = form.position().add(back).add(0, form.getBbHeight() * 0.82, 0);
		((ServerLevel) form.level()).sendParticles(ParticleTypes.CLOUD, nape.x, nape.y, nape.z, 10, 0.5, 0.4, 0.5, 0.03);
	}

	// ---------------- Attack Titan ----------------

	/** Shift+G: an uppercut that launches what it hits (aimed like the punch). */
	public static void uppercut(ServerPlayer player) {
		TitanFormEntity form = start(player, TitanMove.UPPERCUT, 1, 0);
		if (form == null) {
			return;
		}
		ServerLevel level = TitanAbilities.level(form);
		sound(form, SoundEvents.PLAYER_ATTACK_STRONG, 2.5f, 0.35f);
		form.schedule(TitanMove.UPPERCUT.impactTick, () -> {
			if (!TitanAbilities.stillGood(form)) {
				return;
			}
			var c = TitanShifterConfig.attackTitan();
			Vec3 tip = TitanCombat.aimedFistTip(form, player, form.getBbWidth() * 0.5 + c.uppercutReach);
			int n = TitanCombat.hitAimedFist(form, player, tip, c.uppercutRadius, form.getBbWidth() * 0.55,
					(float) TitanMove.UPPERCUT.damage(), c.uppercutKnockback, c.uppercutLift);
			TitanAbilities.meleeFx(level, tip, 1.3f, n);
		});
	}

	/** Shift+X (signature): Rage -- 10 s at full Rage (+50% damage, a 40% faster combo) whatever the HP; a red-steam bellow. */
	public static void rage(ServerPlayer player) {
		TitanFormEntity form = start(player, TitanMove.RAGE, 0, 0);
		if (form == null) {
			return;
		}
		ServerLevel level = TitanAbilities.level(form);
		form.lockFor(TitanMove.RAGE.impactTick + 4);
		form.schedule(TitanMove.RAGE.impactTick, () -> {
			if (!TitanAbilities.stillGood(form)) {
				return;
			}
			form.startRage(TitanShifterConfig.attackTitan().rageTicks);
			Vec3 c = form.position().add(0, form.getBbHeight() * 0.85, 0);
			level.playSound(null, c.x, c.y, c.z, SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 4.0f, 0.5f);
			level.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_ANGRY, SoundSource.HOSTILE, 3.0f, 0.6f);
			level.sendParticles(RAGE_RED, c.x, c.y, c.z, 60, form.getBbWidth() * 0.5, form.getBbHeight() * 0.3, form.getBbWidth() * 0.5, 0.1);
			form.steamBurst(level, 10);
			TitanCombat.shake(level, form.position(), 0.6f, 12);
		});
	}

	/** Shift+Z: a running dropkick -- the Titan launches forward feet first, hitting everything in its path. */
	public static void dropkick(ServerPlayer player) {
		TitanFormEntity form = start(player, TitanMove.DROPKICK, 0, 2);
		if (form == null) {
			return;
		}
		if (!form.onGround()) {
			TitanShifter.say(player, "message.projecthero.titan_shifter.need_ground", ChatFormatting.GRAY);
			return;
		}
		ServerLevel level = TitanAbilities.level(form);
		var dk = TitanShifterConfig.attackTitan();
		int flight = Math.max(1, dk.dropkickHitTicks);
		form.lockFor(TitanMove.DROPKICK.impactTick + flight + 5);
		sound(form, SoundEvents.RAVAGER_ROAR, 2.0f, 0.7f);
		Set<Integer> hit = new HashSet<>();
		form.schedule(TitanMove.DROPKICK.impactTick, () -> {
			if (!TitanAbilities.stillGood(form)) {
				return;
			}
			Vec3 f = fwd(form);
			form.setDeltaMovement(f.x * dk.dropkickSpeed, dk.dropkickLift, f.z * dk.dropkickSpeed);
			form.hasImpulse = true;
			form.hurtMarked = true;
			TitanCombat.ring(level, form.position(), 2.5, ParticleTypes.POOF, 16);
		});
		for (int t = TitanMove.DROPKICK.impactTick + 1; t <= TitanMove.DROPKICK.impactTick + flight; t++) {
			form.schedule(t, () -> {
				if (!TitanAbilities.stillGood(form)) {
					return;
				}
				AABB box = TitanCombat.fistBox(form, 4.0, form.getBbWidth() * 0.7);
				for (LivingEntity e : TitanCombat.targetsIn(level, box, form, player)) {
					if (hit.add(e.getId()) && TitanCombat.hit(form, player, e, (float) TitanMove.DROPKICK.damage(), dk.dropkickKnockback, 0.5, form.position())) {
						TitanAbilities.meleeFx(level, e.position().add(0, e.getBbHeight() * 0.5, 0), 1.2f, 1);
					}
				}
			});
		}
	}

	// ---------------- Armoured Titan ----------------

	/** Shift+R: a short plated elbow -- heavy, close, a big shove. */
	public static void elbow(ServerPlayer player) {
		TitanFormEntity form = start(player, TitanMove.ELBOW, 1, 0);
		if (form == null) {
			return;
		}
		ServerLevel level = TitanAbilities.level(form);
		sound(form, SoundEvents.PLAYER_ATTACK_STRONG, 2.5f, 0.3f);
		form.schedule(TitanMove.ELBOW.impactTick, () -> {
			if (!TitanAbilities.stillGood(form)) {
				return;
			}
			var c = TitanShifterConfig.armoredTitan();
			int n = TitanCombat.hitBox(form, player, TitanCombat.fistBox(form, c.elbowReach, form.getBbWidth() * 0.6),
					(float) TitanMove.ELBOW.damage(), c.elbowKnockback, 0.4, form.position());
			TitanAbilities.meleeFx(level, TitanAbilities.defaultFxSpot(form), 1.3f, n);
			sound(form, SoundEvents.ANVIL_LAND, 1.2f, 0.5f);
		});
	}

	/** Shift+G: both plated fists into the ground -- a slow, huge slam (7 blocks). */
	public static void groundPound(ServerPlayer player) {
		TitanFormEntity form = start(player, TitanMove.GROUND_POUND, 1, 0);
		if (form == null) {
			return;
		}
		ServerLevel level = TitanAbilities.level(form);
		int at = TitanMove.GROUND_POUND.impactTick;
		form.slowFor(at + 6, 0.2f);
		sound(form, SoundEvents.WARDEN_SONIC_CHARGE, 2.5f, 0.5f);
		form.schedule(at, () -> {
			form.clearSlow();
			if (!TitanAbilities.stillGood(form)) {
				return;
			}
			form.lockFor(10);
			Vec3 c = form.position().add(fwd(form).scale(form.getBbWidth() * 0.5 + 2.0));
			var cfg = TitanShifterConfig.armoredTitan();
			double r = cfg.groundPoundRadius;
			TitanCombat.hitRadius(form, player, c, r, (float) TitanMove.GROUND_POUND.damage(), cfg.groundPoundKnockback, 0.8);
			TitanCombat.impactFx(level, c, r * 1.2, 1.8f);
			level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 0.5, c.z, 1, 0, 0, 0, 0);
			TitanCombat.impactBlocks(level, c, r);
			TitanCombat.shake(level, c, 1.2f, 18);
		});
	}

	/** True if a Titan charge may break blocks here: the hero terrain-damage config AND the mobGriefing gamerule. */
	public static boolean chargeMayBreak(ServerLevel level) {
		return AbilityHelpers.canGrief() && level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
	}

	/** What a Shoulder Charge ploughs through: anything up to stone-and-ore hard that is not a block entity or fluid. */
	static boolean chargeBreakable(ServerLevel level, BlockPos pos, BlockState s) {
		if (s.isAir() || s.hasBlockEntity() || !s.getFluidState().isEmpty()) {
			return false;
		}
		float h = s.getDestroySpeed(level, pos);
		return h >= 0.0f && h <= 3.0f;
	}


	/**
	 * Shift+X (signature): the Shoulder Charge -- 1.5 s of shoulder-first sprint (0.9 blocks a tick) that hits everything in
	 * front once (40, a huge shove) and ploughs through blocks up to stone hardness (no drops) when the terrain-damage config
	 * and the mobGriefing gamerule allow it; otherwise solid ground stops it.
	 */
	public static void shoulderCharge(ServerPlayer player) {
		TitanFormEntity form = start(player, TitanMove.SHOULDER_CHARGE, 0, 2);
		if (form == null) {
			return;
		}
		ServerLevel level = TitanAbilities.level(form);
		int t0 = TitanMove.SHOULDER_CHARGE.impactTick;
		var sc = TitanShifterConfig.armoredTitan();
		final int CHARGE_TICKS = Math.max(1, sc.shoulderChargeTicks);
		final int CHARGE_BLOCK_CAP = Math.max(0, sc.shoulderChargeBlockCap);
		form.lockFor(t0 + CHARGE_TICKS + 4);
		sound(form, SoundEvents.RAVAGER_ROAR, 2.5f, 0.45f);
		Set<Integer> hit = new HashSet<>();
		int[] broken = { 0 };
		boolean[] stopped = { false };
		form.schedule(t0, () -> {
			if (TitanAbilities.stillGood(form)) {
				form.startCharge(CHARGE_TICKS);
			}
		});
		for (int t = t0; t < t0 + CHARGE_TICKS; t++) {
			form.schedule(t, () -> {
				if (stopped[0] || !TitanAbilities.stillGood(form) || !form.isCharging()) {
					return;
				}
				Vec3 f = fwd(form);
				Vec3 v = form.getDeltaMovement();
				form.setDeltaMovement(f.x * sc.shoulderChargeSpeed, v.y, f.z * sc.shoulderChargeSpeed);
				form.hasImpulse = true;
				form.hurtMarked = true;
				AABB front = TitanCombat.fistBox(form, 2.5, form.getBbWidth() * 0.55);
				for (LivingEntity e : TitanCombat.targetsIn(level, front, form, player)) {
					if (hit.add(e.getId())) {
						TitanCombat.hit(form, player, e, (float) TitanMove.SHOULDER_CHARGE.damage(), sc.shoulderChargeKnockback, 0.6, form.position());
						sound(form, SoundEvents.ANVIL_LAND, 1.5f, 0.4f);
					}
				}
				int n = 0;
				if (chargeMayBreak(level) && broken[0] < CHARGE_BLOCK_CAP) {
					AABB wall = new AABB(front.minX, form.getY() + 0.05, front.minZ, front.maxX, form.getY() + form.getBbHeight() * 0.85, front.maxZ);
					n = ploughBlocks(level, player, wall, CHARGE_BLOCK_CAP - broken[0]);
					broken[0] += n;
				}
				if (form.horizontalCollision && n == 0 && form.tickCount % 2 == 0) {
					// a wall it may not break: the charge stops dead against it
					stopped[0] = true;
					form.endCharge();
					TitanCombat.impactFx(level, form.position().add(f.scale(form.getBbWidth() * 0.6)), 3.0, 1.0f);
					TitanCombat.shake(level, form.position(), 0.6f, 8);
				}
				if (form.tickCount % 3 == 0) {
					form.footstep(level, 1.2f, true);
				}
			});
		}
	}

	private static int ploughBlocks(ServerLevel level, ServerPlayer player, AABB box, int cap) {
		int n = 0;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = (int) Math.floor(box.minX); x <= (int) Math.floor(box.maxX); x++) {
			for (int y = (int) Math.floor(box.minY); y <= (int) Math.floor(box.maxY); y++) {
				for (int z = (int) Math.floor(box.minZ); z <= (int) Math.floor(box.maxZ); z++) {
					if (n >= cap) {
						return n;
					}
					pos.set(x, y, z);
					if (!level.isLoaded(pos) || !level.mayInteract(player, pos)) {
						continue;
					}
					BlockState s = level.getBlockState(pos);
					if (chargeBreakable(level, pos, s)) {
						if (n % 4 == 0) {
							level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, s), x + 0.5, y + 0.5, z + 0.5, 4, 0.3, 0.3, 0.3, 0.1);
						}
						level.destroyBlock(pos, false, player);
						n++;
					}
				}
			}
		}
		return n;
	}

	/** Shift+Z: Brace -- 3 s rooted behind the plates (a quarter of all damage) while every hostile near turns on the Titan. */
	public static void brace(ServerPlayer player) {
		TitanFormEntity form = start(player, TitanMove.BRACE, 0, 0);
		if (form == null) {
			return;
		}
		ServerLevel level = TitanAbilities.level(form);
		var bc = TitanShifterConfig.armoredTitan();
		form.startBrace(bc.braceTicks);
		sound(form, SoundEvents.ANVIL_PLACE, 2.0f, 0.4f);
		sound(form, SoundEvents.AMETHYST_BLOCK_RESONATE, 3.0f, 0.5f);
		Vec3 c = form.position().add(0, form.getBbHeight() * 0.5, 0);
		level.sendParticles(CRYSTAL, c.x, c.y, c.z, 50, form.getBbWidth() * 0.6, form.getBbHeight() * 0.4, form.getBbWidth() * 0.6, 0.05);
		for (LivingEntity e : TitanCombat.hostilesInCylinder(level, form, bc.braceTauntRadius, player)) {
			if (e instanceof Mob mob) {
				mob.setTarget(form);
			}
		}
	}

	// ---------------- Female Titan ----------------

	/** Shift+R: a crystal-shinned roundhouse -- a wide arc across the front, a big shove. */
	public static void roundhouse(ServerPlayer player) {
		TitanFormEntity form = start(player, TitanMove.ROUNDHOUSE, 0, 2);
		if (form == null) {
			return;
		}
		ServerLevel level = TitanAbilities.level(form);
		sound(form, SoundEvents.PLAYER_ATTACK_SWEEP, 2.5f, 0.4f);
		form.schedule(TitanMove.ROUNDHOUSE.impactTick, () -> {
			if (!TitanAbilities.stillGood(form)) {
				return;
			}
			Vec3 c = form.position().add(fwd(form).scale(form.getBbWidth() * 0.5 + 1.5)).add(0, form.getBbHeight() * 0.45, 0);
			var cfg = TitanShifterConfig.femaleTitan();
			int n = TitanCombat.hitRadius(form, player, c, form.getBbWidth() * 0.5 + cfg.roundhouseRadius, (float) TitanMove.ROUNDHOUSE.damage(),
					cfg.roundhouseKnockback, 0.4);
			level.sendParticles(ParticleTypes.SWEEP_ATTACK, c.x, c.y, c.z, 5, 2.5, 0.5, 2.5, 0.0);
			TitanAbilities.meleeFx(level, c, 1.2f, n);
		});
	}

	/** G: an axe kick -- the heel comes down where you aim (like Heavy Smash, faster and narrower). */
	public static void axeKick(ServerPlayer player) {
		TitanFormEntity form = start(player, TitanMove.AXE_KICK, 0, 2);
		if (form == null) {
			return;
		}
		ServerLevel level = TitanAbilities.level(form);
		sound(form, SoundEvents.PLAYER_ATTACK_STRONG, 2.5f, 0.4f);
		form.schedule(TitanMove.AXE_KICK.impactTick, () -> {
			if (!TitanAbilities.stillGood(form)) {
				return;
			}
			form.lockFor(6);
			Vec3 at = TitanAbilities.smashPoint(form, player);
			double r = TitanShifterConfig.femaleTitan().axeKickRadius;
			TitanCombat.hitRadius(form, player, at, r, (float) TitanMove.AXE_KICK.damage(), 2.0, 0.3);
			TitanCombat.impactFx(level, at, r + 0.5, 1.2f);
			TitanCombat.impactBlocks(level, at, r);
			TitanCombat.shake(level, at, 0.7f, 10);
		});
	}

	/** Shift+G: a low spinning sweep round the feet -- everything on the ground within reach is hit and slowed. */
	public static void sweepKick(ServerPlayer player) {
		TitanFormEntity form = start(player, TitanMove.SWEEP_KICK, 0, 2);
		if (form == null) {
			return;
		}
		ServerLevel level = TitanAbilities.level(form);
		sound(form, SoundEvents.PLAYER_ATTACK_SWEEP, 3.0f, 0.3f);
		form.schedule(TitanMove.SWEEP_KICK.impactTick, () -> {
			if (!TitanAbilities.stillGood(form)) {
				return;
			}
			var cfg = TitanShifterConfig.femaleTitan();
			double r = form.getBbWidth() * 0.5 + cfg.sweepKickRadius;
			Vec3 c = form.position();
			for (LivingEntity e : TitanCombat.targetsAround(level, c, r, form, player)) {
				if (e.getY() > form.getY() + form.getBbHeight() * 0.35) {
					continue; // low: it passes under anything up high
				}
				if (TitanCombat.hit(form, player, e, (float) TitanMove.SWEEP_KICK.damage(), 1.6, 0.35, c)) {
					AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, cfg.sweepKickSlowTicks, 2);
				}
			}
			TitanCombat.ring(level, c, r * 0.8, ParticleTypes.SWEEP_ATTACK, 14);
			TitanCombat.ring(level, c, r, ParticleTypes.POOF, 24);
			TitanCombat.shake(level, c, 0.5f, 8);
		});
	}

	/**
	 * Shift+X (signature): crystal fists and shins for 12 s -- every hit +50%, and the nape crystallises too (nape hits can
	 * not cut the shifter out while it lasts).
	 */
	public static void crystalFists(ServerPlayer player) {
		TitanFormEntity form = start(player, TitanMove.CRYSTAL_FISTS, 0, 0);
		if (form == null) {
			return;
		}
		ServerLevel level = TitanAbilities.level(form);
		form.schedule(TitanMove.CRYSTAL_FISTS.impactTick, () -> {
			if (!TitanAbilities.stillGood(form)) {
				return;
			}
			form.startCrystal(TitanShifterConfig.femaleTitan().crystalFistsTicks);
			Vec3 c = form.position().add(0, form.getBbHeight() * 0.4, 0);
			level.playSound(null, c.x, c.y, c.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 3.0f, 0.7f);
			level.playSound(null, c.x, c.y, c.z, SoundEvents.AMETHYST_CLUSTER_PLACE, SoundSource.HOSTILE, 3.0f, 0.5f);
			level.sendParticles(CRYSTAL, c.x, c.y, c.z, 70, form.getBbWidth() * 0.6, form.getBbHeight() * 0.4, form.getBbWidth() * 0.6, 0.05);
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.AMETHYST_BLOCK.defaultBlockState()),
					c.x, c.y, c.z, 40, form.getBbWidth() * 0.5, form.getBbHeight() * 0.4, form.getBbWidth() * 0.5, 0.1);
		});
	}

	/** Shift+Z: the Scream -- every hostile within 48 blocks turns on the Titan and the near ones are dragged in toward it. */
	public static void scream(ServerPlayer player) {
		TitanFormEntity form = start(player, TitanMove.SCREAM, 0, 0);
		if (form == null) {
			return;
		}
		ServerLevel level = TitanAbilities.level(form);
		form.lockFor(TitanMove.SCREAM.impactTick + 16);
		form.schedule(TitanMove.SCREAM.impactTick, () -> {
			if (!TitanAbilities.stillGood(form)) {
				return;
			}
			Vec3 head = form.position().add(0, form.getBbHeight() * 0.9, 0);
			level.playSound(null, head.x, head.y, head.z, SoundEvents.GHAST_SCREAM, SoundSource.HOSTILE, 5.0f, 0.4f);
			level.playSound(null, head.x, head.y, head.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 3.0f, 0.7f);
			for (int i = 1; i <= 3; i++) {
				TitanCombat.ring(level, form.position().add(0, i * 1.5, 0), 6.0 * i, ParticleTypes.SONIC_BOOM, 6 * i);
			}
			Vec3 c = form.position();
			var cfg = TitanShifterConfig.femaleTitan();
			for (LivingEntity e : TitanCombat.hostilesInCylinder(level, form, cfg.screamRadius, player)) {
				if (e instanceof Mob mob) {
					mob.setTarget(form);
					mob.getNavigation().moveTo(c.x, c.y, c.z, 1.3);
				}
				if (TitanCombat.isBoss(e)) {
					continue;
				}
				Vec3 to = c.subtract(e.position());
				double d = to.horizontalDistance();
				if (d > 4.0 && d < cfg.screamPullRange) {
					Vec3 pull = new Vec3(to.x, 0, to.z).normalize().scale(Math.min(1.6, 0.4 + d * 0.06));
					AbilityHelpers.push(e, pull.add(0, 0.35, 0));
				}
			}
			TitanCombat.shake(level, c, 0.8f, 16);
		});
	}

	// ---------------- Beast Titan ----------------

	// v0.16.7 (user, Titan fixes): the Beast's numbers live in TitanShifterConfig.beastTitan() (these read it live)

	/** Pitcher's Fastball: stones in the spray. */
	public static int fastballStones() {
		return Math.max(1, TitanShifterConfig.beastTitan().fastballStones);
	}

	/** Pitcher's Fastball: at most this many stones of one pitch hurt one target. */
	public static int fastballPerTarget() {
		return Math.max(1, TitanShifterConfig.beastTitan().fastballPerTarget);
	}

	/** Pitcher's Fastball: how long one stone flies (ticks). */
	public static int fastballLife() {
		return Math.max(1, TitanShifterConfig.beastTitan().fastballLife);
	}

	/** Rock Scatter: chunks flung. */
	public static int scatterChunks() {
		return Math.max(3, TitanShifterConfig.beastTitan().scatterChunks);
	}

	private static Vec3 flatDir(ServerPlayer player) {
		Vec3 look = player.getLookAngle();
		Vec3 d = new Vec3(look.x, 0, look.z);
		return d.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : d.normalize();
	}

	/** Turns {@code d} about the vertical by {@code deg}. */
	private static Vec3 yaw(Vec3 d, double deg) {
		double r = Math.toRadians(deg), c = Math.cos(r), s = Math.sin(r);
		return new Vec3(d.x * c - d.z * s, d.y, d.x * s + d.z * c);
	}

	/** The velocity that carries a rock from {@code from} onto {@code to} in {@code ticks} under {@code g} (scaled up for the chunks' 0.985 drag). */
	private static Vec3 lob(Vec3 from, Vec3 to, double ticks, double g) {
		double k = 0.985, f = ticks * (1.0 - k) / (1.0 - Math.pow(k, ticks));
		return to.subtract(from).scale(1.0 / ticks).add(0, g * ticks * 0.5, 0).scale(f);
	}

	/**
	 * Shift+R: Pitcher's Fastball (replaced the Backhand) -- the Beast's signature from the anime. It scoops a handful of
	 * ground (t8), crushes it to gravel in its fist (t12-15), rears into a pitcher's leg kick and fires the lot overarm at
	 * the crosshair on t22: {@link #fastballStones()} fast stones in a tight shotgun spread, 9 each (at most
	 * {@link #fastballPerTarget()} on one target), about 80 blocks of range. 8 s.
	 */
	public static void fastball(ServerPlayer player) {
		TitanFormEntity form = start(player, TitanMove.FASTBALL, 1, 0);
		if (form == null) {
			return;
		}
		ServerLevel level = TitanAbilities.level(form);
		int t = TitanMove.FASTBALL.impactTick;
		form.lockFor(t + 6);
		form.schedule(8, () -> {
			if (TitanAbilities.stillGood(form)) {
				TitanBoulders.pickUp(form, true, 3);
			}
		});
		for (int c : new int[] { 12, 14 }) {
			form.schedule(c, () -> {
				if (!TitanAbilities.stillGood(form)) {
					return;
				}
				// the fist grinds the stones to gravel: a crunch and grit spilling out
				Vec3 fist = form.position().add(fwd(form).scale(form.getBbWidth() * 0.45)).add(0, form.getBbHeight() * 0.52, 0);
				BlockState ground = TitanBoulders.groundState(form);
				TitanBoulders.fx(level, new BlockParticleOption(ParticleTypes.FALLING_DUST, ground), fist.x, fist.y, fist.z, 10, 0.4, 0.2, 0.4, 0.0);
				TitanBoulders.fx(level, new BlockParticleOption(ParticleTypes.BLOCK, ground), fist.x, fist.y, fist.z, 14, 0.3, 0.2, 0.3, 0.05);
				level.playSound(null, fist.x, fist.y, fist.z, SoundEvents.GRAVEL_BREAK, SoundSource.HOSTILE, 2.5f, 0.45f);
				level.playSound(null, fist.x, fist.y, fist.z, SoundEvents.STONE_BREAK, SoundSource.HOSTILE, 2.0f, 0.35f + c * 0.01f);
			});
		}
		form.schedule(18, () -> {
			if (TitanAbilities.stillGood(form)) {
				sound(form, SoundEvents.RAVAGER_STEP, 2.5f, 0.6f); // the leg kick
			}
		});
		form.schedule(t, () -> {
			form.setHoldingRocks(false);
			if (!TitanAbilities.stillGood(form)) {
				return;
			}
			pitch(form, player);
		});
	}

	/** The pitch itself (also the gametests): the stones leave the hand at the crosshair. Returns how many flew. */
	public static int pitch(TitanFormEntity form, ServerPlayer player) {
		ServerLevel level = TitanAbilities.level(form);
		Vec3 from = TitanBoulders.releasePos(form);
		Vec3 aim = throwAim(form, player);
		Vec3 dir = aim.subtract(from);
		dir = dir.lengthSqr() < 1.0e-4 ? player.getLookAngle() : dir.normalize();
		BlockState ground = TitanBoulders.groundState(form);
		var rnd = level.random;
		TitanRockEntity.Volley volley = new TitanRockEntity.Volley(fastballPerTarget());
		Vec3 up = new Vec3(0, 1, 0);
		Vec3 side = dir.cross(up).lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : dir.cross(up).normalize();
		Vec3 vert = side.cross(dir).normalize();
		int n = 0;
		int stones = fastballStones();
		double speed0 = TitanShifterConfig.beastTitan().fastballSpeed;
		for (int i = 0; i < stones; i++) {
			// a tight cone: about 4-5 degrees either way, a touch more sideways than up and down
			double a = rnd.nextGaussian() * 0.07, b = rnd.nextGaussian() * 0.045;
			Vec3 d = dir.add(side.scale(a)).add(vert.scale(b)).normalize();
			double speed = speed0 * (0.92 + rnd.nextDouble() * 0.16);
			float s = 0.5f + rnd.nextFloat() * 0.5f;
			Vec3 at = from.add(side.scale((rnd.nextDouble() - 0.5) * 0.8)).add(vert.scale((rnd.nextDouble() - 0.5) * 0.8));
			TitanRockEntity r = TitanRockEntity.create(level, TitanRockEntity.KIND_CHUNK, TitanBoulders.debrisState(ground, rnd), s, at,
					d.scale(speed), 0.012f).fast().thrownBy(player, form, (float) TitanMove.FASTBALL.damage(), 0.35, volley)
					.maxAge(fastballLife());
			if (TitanBoulders.spawn(level, r)) {
				n++;
			}
		}
		TitanBoulders.fx(level, ParticleTypes.CLOUD, from.x, from.y, from.z, 6, 0.4, 0.4, 0.4, 0.05);
		TitanBoulders.fx(level, new BlockParticleOption(ParticleTypes.BLOCK, ground), from.x, from.y, from.z, 16, 0.4, 0.4, 0.4, 0.2);
		level.playSound(null, from.x, from.y, from.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 3.0f, 0.35f);
		level.playSound(null, from.x, from.y, from.z, SoundEvents.ARROW_SHOOT, SoundSource.HOSTILE, 3.0f, 0.4f);
		level.playSound(null, from.x, from.y, from.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.2f, 1.6f);
		TitanCombat.shake(level, from, 0.25f, 6);
		return n;
	}

	/**
	 * Shift+G: Rock Scatter -- the Beast rakes both hands through the ground (t7, a few real blocks torn up when the
	 * terrain rules allow it) and flings the rubble forward as its arms come up (t10-12): {@link #scatterChunks()} real
	 * chunks of the ground, cobble and dirt, big and small, tumbling over a 12-block cone. Each hits what it touches --
	 * 18, once per target (a chunk is aimed at each mob in the cone) -- and crumbles. 8 s.
	 */
	public static void rockScatter(ServerPlayer player) {
		TitanFormEntity form = start(player, TitanMove.ROCK_SCATTER, 1, 0);
		if (form == null) {
			return;
		}
		ServerLevel level = TitanAbilities.level(form);
		sound(form, SoundEvents.RAVAGER_ROAR, 1.2f, 0.7f);
		form.schedule(7, () -> {
			if (!TitanAbilities.stillGood(form)) {
				return;
			}
			Vec3 spot = form.position().add(flatDir(player).scale(form.getBbWidth() * 0.55));
			TitanBoulders.ripFx(level, spot, TitanBoulders.groundState(form), 2.0f);
			TitanBoulders.ripGround(level, form, spot, 2.5, 8);
			TitanCombat.shake(level, spot, 0.3f, 6);
		});
		TitanRockEntity.Volley volley = new TitanRockEntity.Volley(1);
		int t = TitanMove.ROCK_SCATTER.impactTick;
		for (int k = 0; k < 3; k++) {
			int wave = k;
			form.schedule(t - 2 + k, () -> {
				if (TitanAbilities.stillGood(form)) {
					scatterWave(form, player, volley, wave);
				}
			});
		}
	}

	/** One of the three waves of Rock Scatter's rubble (wave 0 is low, from the hands rising past the knees). */
	public static int scatterWave(TitanFormEntity form, ServerPlayer player, TitanRockEntity.Volley volley, int wave) {
		ServerLevel level = TitanAbilities.level(form);
		var rnd = level.random;
		Vec3 dir = flatDir(player);
		Vec3 feet = form.position();
		Vec3 from = feet.add(dir.scale(form.getBbWidth() * (0.55 - wave * 0.08))).add(0, form.getBbHeight() * (0.22 + wave * 0.2), 0);
		BlockState ground = TitanBoulders.groundState(form);
		var bc = TitanShifterConfig.beastTitan();
		final double SCATTER_RANGE = bc.scatterRange, SCATTER_CONE = bc.scatterCone;
		int per = scatterChunks() / 3;
		int n = 0;
		if (wave == 1) {
			// one chunk aimed at each mob in the cone, so everything in it gets hit
			for (LivingEntity e : TitanCombat.targetsAround(level, feet, SCATTER_RANGE + form.getBbWidth() * 0.5, form, player)) {
				Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0);
				Vec3 flat = new Vec3(to.x - feet.x, 0, to.z - feet.z);
				if (flat.lengthSqr() > 1.0e-4 && flat.normalize().dot(dir) < SCATTER_CONE) {
					continue;
				}
				double ticks = 5 + to.distanceTo(from) * 0.35;
				TitanRockEntity r = TitanRockEntity.create(level, TitanRockEntity.KIND_CHUNK, TitanBoulders.debrisState(ground, rnd),
						0.9f + rnd.nextFloat() * 0.6f, from, lob(from, to, ticks, 0.06), 0.06f)
						.thrownBy(player, form, (float) TitanMove.ROCK_SCATTER.damage(), 1.2, volley).maxAge(50);
				if (TitanBoulders.spawn(level, r)) {
					n++;
				}
			}
		}
		for (int i = 0; i < per; i++) {
			// a random spot on the ground in the cone, 3-13 blocks out; low waves land near, high waves far
			double ang = (rnd.nextDouble() * 2 - 1) * 42.0;
			double d = 3.0 + rnd.nextDouble() * (SCATTER_RANGE - 3.0) * (0.55 + wave * 0.25) + form.getBbWidth() * 0.4;
			Vec3 to = feet.add(yaw(dir, ang).scale(d)).add(0, 0.3, 0);
			double ticks = 9 + d * 0.5 + rnd.nextDouble() * 3;
			float s = 0.7f + rnd.nextFloat() * rnd.nextFloat() * 1.4f + (i == 0 ? 0.6f : 0.0f);
			Vec3 at = from.add(yaw(dir, 90).scale((rnd.nextDouble() - 0.5) * form.getBbWidth() * 0.7));
			TitanRockEntity r = TitanRockEntity.create(level, TitanRockEntity.KIND_CHUNK, TitanBoulders.debrisState(ground, rnd), s, at,
					lob(at, to, ticks, 0.06), 0.06f).thrownBy(player, form, (float) TitanMove.ROCK_SCATTER.damage(), 1.2, volley).maxAge(50);
			if (TitanBoulders.spawn(level, r)) {
				n++;
			}
		}
		TitanBoulders.fx(level, new BlockParticleOption(ParticleTypes.BLOCK, ground), from.x, from.y, from.z, 24, form.getBbWidth() * 0.3, 0.5,
				form.getBbWidth() * 0.3, 0.25);
		TitanBoulders.fx(level, ParticleTypes.CAMPFIRE_COSY_SMOKE, from.x, from.y, from.z, 3, form.getBbWidth() * 0.3, 0.4, form.getBbWidth() * 0.3,
				0.02);
		level.playSound(null, from.x, from.y, from.z, SoundEvents.GRAVEL_BREAK, SoundSource.HOSTILE, 3.0f, 0.5f + wave * 0.1f);
		if (wave == 0) {
			level.playSound(null, from.x, from.y, from.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 3.0f, 0.4f);
		}
		return n;
	}

	/** Where a throw lands: the crosshair's mob / block, or 40 blocks down the look. */
	private static Vec3 throwAim(TitanFormEntity form, ServerPlayer player) {
		TitanCombat.Aim aim = TitanCombat.aim(form, player, 60.0);
		return aim.hitSomething() ? aim.point() : player.getEyePosition().add(player.getLookAngle().scale(40.0));
	}

	/**
	 * Shift+X (signature): the Boulder Throw -- the Beast rips a boulder out of the ground (it is in its right hand from
	 * tick 6) and pitches it at the crosshair on tick 16: a cluster of stone blocks tumbling in an arc that bursts where it
	 * lands -- 45 in 4 blocks, shrapnel for half out to 8, a ring of dust and real debris.
	 */
	public static void boulderThrow(ServerPlayer player) {
		TitanFormEntity form = start(player, TitanMove.BOULDER_THROW, 1, 0);
		if (form == null) {
			return;
		}
		form.lockFor(TitanMove.BOULDER_THROW.impactTick + 4);
		form.schedule(6, () -> {
			if (TitanAbilities.stillGood(form)) {
				TitanBoulders.pickUp(form, false, 6);
			}
		});
		form.schedule(TitanMove.BOULDER_THROW.impactTick, () -> {
			if (!TitanAbilities.stillGood(form)) {
				form.setHoldingBoulder(false);
				return;
			}
			TitanBoulders.throwBoulder(form, player, 4.0f, (float) TitanMove.BOULDER_THROW.damage(), TitanShifterConfig.beastTitan().boulderThrowRadius,
					throwAim(form, player), 1.8,
					TitanBoulders.releasePos(form), true);
		});
	}

	/** Shift+Z: a volley of three smaller boulders fanned round the crosshair (20 each in 2.5 blocks, plus shrapnel). */
	public static void boulderVolley(ServerPlayer player) {
		TitanFormEntity form = start(player, TitanMove.BOULDER_VOLLEY, 1, 0);
		if (form == null) {
			return;
		}
		form.lockFor(TitanMove.BOULDER_VOLLEY.impactTick + 8);
		form.schedule(4, () -> {
			if (TitanAbilities.stillGood(form)) {
				TitanBoulders.pickUp(form, true, 4);
			}
		});
		for (int i = -1; i <= 1; i++) {
			int k = i;
			form.schedule(TitanMove.BOULDER_VOLLEY.impactTick + (i + 1) * 2, () -> {
				if (!TitanAbilities.stillGood(form)) {
					form.setHoldingRocks(false);
					return;
				}
				Vec3 aim = throwAim(form, player);
				Vec3 side = Vec3.directionFromRotation(0, form.getYRot() + 90.0f);
				// underarm from both hands: the outer rocks leave from either side
				Vec3 from = TitanBoulders.releasePos(form).add(side.scale(-form.getBbWidth() * 0.22 + k * form.getBbWidth() * 0.22))
						.add(0, -form.getBbHeight() * 0.06, 0);
				TitanBoulders.throwBoulder(form, player, 2.2f, (float) TitanMove.BOULDER_VOLLEY.damage(), TitanShifterConfig.beastTitan().boulderVolleyRadius,
						aim.add(side.scale(k * 4.0)), 2.0,
						from, k == 1);
			});
		}
	}
}
