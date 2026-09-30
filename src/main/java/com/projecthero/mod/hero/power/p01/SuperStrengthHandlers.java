package com.projecthero.mod.hero.power.p01;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilityContext;
import com.projecthero.mod.hero.AbilityHandler;
import com.projecthero.mod.hero.AbilityHandlers;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.data.ExperimentalState;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.hero.revamp.batcha.BatchA;
import com.projecthero.mod.hero.revamp.batcha.ThrownChunkEntity;
import com.projecthero.mod.hero.visual.MutationVisuals;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Power 01 — Super Strength (v0.14.5 rework: six keys, three passives).
 *
 * <h2>Passives</h2>
 * Exactly three: +10 unarmed damage (only while the main hand holds nothing that deals attack damage of its own),
 * 150% melee knockback, and the Charged Punch.
 *
 * <h2>Charged Punch</h2>
 * Hold the attack key for 1 s (client-tracked, {@code StrengthActionPayload}) and release: your current melee
 * damage +15, massive knockback, shields disabled. A 2 s cooldown starts once it lands. A Hairline bar above the
 * keys fills during the wind-up and drains over the cooldown.
 *
 * <h2>Slots</h2>
 * R Haymaker (3-hit combo), G Ground Slam (air = dive) / Shift+G Thunderclap, X Power Leap (charge, superhero
 * landing), Z Bull Rush, V Grab &amp; Throw / Shift+V Rip &amp; Hurl, C Maximum Effort (30 s, 70 s cooldown).
 * Thunderclap and Rip &amp; Hurl keep their own cooldowns ({@code clap_cd} / {@code rip_cd}, in ticks), which the
 * HUD folds into the G / V boxes.
 */
public final class SuperStrengthHandlers {
	public static final String KEY = "power_01_super_strength";

	/** +10 unarmed damage, only while the main hand carries no attack-damage item of its own. */
	public static final ResourceLocation PASSIVE_ATK = id("strength_passive_attack");
	/** 150% knockback on melee hits. */
	public static final ResourceLocation PASSIVE_ATK_KB = id("strength_passive_attack_kb");
	/** Pre-v0.14.5 passives: never applied any more, only cleared so a live session drops them. */
	private static final ResourceLocation PASSIVE_JUMP = id("strength_passive_jump");
	private static final ResourceLocation PASSIVE_KB_RES = id("strength_passive_kb_resist");
	private static final ResourceLocation PASSIVE_SWIM = id("strength_passive_swim");
	private static final ResourceLocation SPRINT_SPD = id("strength_sprint_speed");
	private static final ResourceLocation RUSH_KB = id("strength_rush_kb");
	private static final ResourceLocation RUSH_STEP = id("strength_rush_step");
	private static final ResourceLocation EFFORT_KB = id("strength_effort_kb");
	private static final ResourceLocation EFFORT_ATK = id("strength_effort_attack");

	public static final double UNARMED_BONUS = 10.0;
	/**
	 * A plain hit already knocks back 0.4 (vanilla {@code hurt}); {@code Player.attack} then halves that and adds a
	 * second push of {@code ATTACK_KNOCKBACK * 0.5}. +0.8 makes it 0.2 + 0.4 = 0.6, i.e. 150% of a normal hit.
	 */
	public static final double KNOCKBACK_BONUS = 0.8;

	/** Charged Punch: 1 s hold, your melee damage +15, 2 s cooldown after it lands. */
	public static final int CHARGED_HOLD_TICKS = 20;
	public static final float CHARGED_BONUS = 15f;
	public static final int CHARGED_CD_TICKS = 40;
	/** Haymaker: jab, cross, then the launching haymaker; each follow-up must land within 1.5 s. */
	private static final float[] HAYMAKER_DAMAGE = { 10f, 12f, 22f };
	private static final int COMBO_WINDOW = 30;
	private static final int COMBO_GAP = 8;
	private static final int HAYMAKER_CD = 102;
	/** A combo abandoned after one or two hits costs a short cooldown, so it never out-damages finishing it. */
	private static final int COMBO_DROP_CD = 50;
	/** G: Ground Slam on the ground. */
	public static final float SLAM_DAMAGE = 20f;
	private static final int SLAM_CD = 136;
	/** Z: Bull Rush is a 5 s hold-to-charge, then 8 s of ploughing forward; 34 s cooldown. */
	public static final int RUSH_CHARGE = 100;
	public static final int RUSH_RUN = 160;
	private static final int RUSH_CD = 680;
	/** C: Maximum Effort runs 30 s; its 70 s cooldown is the catalog's. */
	public static final int EFFORT_TICKS = 600;
	public static final int EFFORT_CD = 1400;
	/** Shift+G Thunderclap / Shift+V Rip & Hurl keep their own cooldowns (the clap_cd / rip_cd resources). */
	public static final int THUNDERCLAP_CD = 160;
	public static final int RIP_CD = 187;
	private static final int RIP_RISE = 12;
	/** Rip & Hurl launch speed (1.9 before v0.14.5 -- the boulder now flies about twice as far). */
	public static final double RIP_THROW_SPEED = 3.2;
	private static final int GRAB_HOLD = 300;
	private static final int THROW_CD = 68;

	private static ResourceLocation id(String p) {
		return com.projecthero.mod.ProjectHeroMod.id(p);
	}

	private SuperStrengthHandlers() {
	}

	private static Power power() {
		return Powers.byKey(KEY);
	}

	/** Client-safe: does this player own Super Strength (persistent-stacking model). */
	public static boolean owns(Player player) {
		ExperimentalState st = player.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		return st != null && st.ownedPowers.contains(KEY);
	}

	public static boolean maxEffortActive(ServerPlayer player) {
		return res(player, "effort_left") > 0.5f;
	}

	/** Maximum Effort doubles the damage of every Super Strength move (Bull Rush: x1.25). */
	private static float effort(ServerPlayer p) {
		return maxEffortActive(p) ? 2.0f : 1.0f;
	}

	private static void res(ServerPlayer player, String name, float value) {
		BatchA.set(player, KEY, name, value, 1e12f);
	}

	private static float res(ServerPlayer player, String name) {
		return BatchA.res(player, KEY, name);
	}

	/** Cooldown ticks, halved while Maximum Effort is running. */
	private static int effortCd(ServerPlayer p, int baseTicks) {
		return maxEffortActive(p) ? baseTicks / 2 : baseTicks;
	}

	private static void triggerCd(AbilityContext ctx, int baseTicks) {
		ctx.triggerCooldown(effortCd(ctx.player(), baseTicks));
	}

	private static void cooldownMessage(AbilityContext ctx) {
		cooldownMessage(ctx, ctx.ability().nameKey(), ctx.cooldownRemaining());
	}

	private static void cooldownMessage(AbilityContext ctx, String nameKey, int ticks) {
		ctx.actionBar("message.projecthero.ability.on_cooldown", net.minecraft.network.chat.Component.translatable(nameKey),
				String.format(java.util.Locale.ROOT, "%.0f", Math.ceil(ticks / 20.0f)));
	}

	private static String moveName(String id) {
		return "projecthero.power." + KEY + ".ability." + id;
	}

	// ============================================================================================

	public static void register() {
		AbilityHandlers.register(KEY, "haymaker", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				haymaker(ctx);
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				haymakerTick(ctx);
			}
		});
		// G is a HOLD slot so the router does not gate it on the slam's cooldown: Shift+G (Thunderclap) has its own.
		AbilityHandlers.register(KEY, "ground_slam", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (p.isShiftKeyDown()) {
					float cd = res(p, "clap_cd");
					if (cd > 0.5f) {
						cooldownMessage(ctx, moveName("thunderclap"), (int) cd);
						return;
					}
					thunderclap(ctx);
					return;
				}
				if (res(p, "diving") > 0.5f) {
					return; // already mid-dive
				}
				if (!ctx.cooldownReady()) {
					cooldownMessage(ctx);
					return;
				}
				groundSlamActivate(ctx);
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				groundSlamTick(ctx);
			}
		});
		// Power Leap's timing is owned entirely by the client (StrengthActionPayload.PERFORM_POWER_LEAP)
		// so the launch is deterministic. The slot handler draws the wind-up and runs the hero landing.
		AbilityHandlers.register(KEY, "power_leap", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				if (ctx.cooldownReady()) {
					res(ctx.player(), "leap_press", ctx.player().level().getGameTime());
				}
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				res(ctx.player(), "leap_press", 0);
				MutationVisuals.stopIf(ctx.player(), "crouch_charge");
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				leapTick(ctx);
			}
		});
		AbilityHandlers.register(KEY, "bull_rush", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				bullRushPress(ctx);
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				bullRushRelease(ctx);
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				bullRushTick(ctx);
			}
		});
		// V is a HOLD slot for the same reason as G: Shift+V (Rip & Hurl) keeps its own cooldown.
		AbilityHandlers.register(KEY, "grab_throw", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				boolean holding = res(p, "grabbed") > 0.5f || res(p, "chunk_id") > 0.5f;
				if (holding) {
					grabThrow(ctx); // throw it (sneaking: set a creature down) -- never blocked by the cooldown
					return;
				}
				if (p.isShiftKeyDown()) {
					if (res(p, "rip_id") > 0.5f) {
						return; // one already coming up
					}
					float cd = res(p, "rip_cd");
					if (cd > 0.5f) {
						cooldownMessage(ctx, moveName("rip_hurl"), (int) cd);
						return;
					}
					ripHurl(ctx);
					return;
				}
				if (!ctx.cooldownReady()) {
					cooldownMessage(ctx);
					return;
				}
				grabThrow(ctx);
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				grabTick(ctx);
				ripTick(ctx);
			}
		});
		AbilityHandlers.register(KEY, "maximum_effort", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				maximumEffort(ctx);
			}
		});

		registerPassives();
	}

	// ---- passives ---------------------------------------------------------------------------------

	/** Whether {@code stack} deals attack damage of its own (a sword, axe, trident, mace ...). */
	private static boolean isWeapon(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		ItemAttributeModifiers mods = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
		for (ItemAttributeModifiers.Entry e : mods.modifiers()) {
			if (e.attribute().value() == Attributes.ATTACK_DAMAGE.value()) {
				return true;
			}
		}
		return false;
	}

	/** +10 while the main hand is empty (or holds something that is not a weapon), nothing otherwise. */
	public static void updateUnarmed(ServerPlayer player) {
		if (isWeapon(player.getMainHandItem())) {
			PowerToggles.clearModifier(player, Attributes.ATTACK_DAMAGE, PASSIVE_ATK);
		} else {
			PowerToggles.modifier(player, Attributes.ATTACK_DAMAGE, PASSIVE_ATK, UNARMED_BONUS, AttributeModifier.Operation.ADD_VALUE);
		}
	}

	/** The passive modifiers this power no longer grants -- cleared whenever the passives are reconciled. */
	private static void clearLegacyPassives(ServerPlayer player) {
		PowerToggles.clearModifier(player, Attributes.JUMP_STRENGTH, PASSIVE_JUMP);
		PowerToggles.clearModifier(player, Attributes.KNOCKBACK_RESISTANCE, PASSIVE_KB_RES);
		PowerToggles.clearModifier(player, Attributes.WATER_MOVEMENT_EFFICIENCY, PASSIVE_SWIM);
		PowerToggles.clearModifier(player, Attributes.MOVEMENT_SPEED, SPRINT_SPD);
	}

	private static void registerPassives() {
		com.projecthero.mod.hero.PowerPassives.register(KEY, (player, active) -> {
			clearLegacyPassives(player);
			if (active) {
				updateUnarmed(player);
				PowerToggles.modifier(player, Attributes.ATTACK_KNOCKBACK, PASSIVE_ATK_KB, KNOCKBACK_BONUS,
						AttributeModifier.Operation.ADD_VALUE);
			} else {
				PowerToggles.clearModifier(player, Attributes.ATTACK_DAMAGE, PASSIVE_ATK);
				PowerToggles.clearModifier(player, Attributes.ATTACK_KNOCKBACK, PASSIVE_ATK_KB);
				PowerToggles.clearModifier(player, Attributes.KNOCKBACK_RESISTANCE, EFFORT_KB);
				PowerToggles.clearModifier(player, Attributes.ATTACK_DAMAGE, EFFORT_ATK);
				PowerToggles.clearModifier(player, Attributes.KNOCKBACK_RESISTANCE, RUSH_KB);
				PowerToggles.clearModifier(player, Attributes.STEP_HEIGHT, RUSH_STEP);
				releaseGrab(player, false);
			}
		});

		com.projecthero.mod.hero.PowerPassives.registerTick(KEY, player -> {
			updateUnarmed(player);
			// Countdown timers (also read by the HUD).
			for (String cdName : new String[] { "charged_cd", "clap_cd", "rip_cd" }) {
				float cd = res(player, cdName);
				if (cd > 0) {
					res(player, cdName, Math.max(0, cd - 1));
				}
			}
			float eff = res(player, "effort_left");
			if (eff > 0) {
				res(player, "effort_left", eff - 1);
				if (player.tickCount % 4 == 0 && player.level() instanceof ServerLevel sl) {
					sl.sendParticles(new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.9f, 0.1f, 0.05f), 1.2f),
							player.getX(), player.getY() + 1.0, player.getZ(), 3, 0.35, 0.6, 0.35, 0.0);
				}
				if (eff - 1 <= 0.5f) {
					PowerToggles.clearModifier(player, Attributes.KNOCKBACK_RESISTANCE, EFFORT_KB);
					PowerToggles.clearModifier(player, Attributes.ATTACK_DAMAGE, EFFORT_ATK);
				}
			}
		});
	}

	// ---- charged punch --------------------------------------------------------------------------

	/** Charged Punch damage right now: the player's melee damage (unarmed bonus, Maximum Effort included) + 15. */
	public static float chargedPunchDamage(ServerPlayer p) {
		return (float) p.getAttributeValue(Attributes.ATTACK_DAMAGE) + CHARGED_BONUS;
	}

	/**
	 * The client held the attack key for 1 s (while not aimed at a minable block) and then released it. Throw the
	 * charged punch now: melee damage +15 to whatever is in front, massive knockback, shields broken, then a 2 s
	 * cooldown.
	 */
	public static void performChargedPunch(ServerPlayer p) {
		if (!owns(p) || res(p, "charged_cd") > 0.5f || !(p.level() instanceof ServerLevel level)) {
			return;
		}
		res(p, "charged_cd", effortCd(p, CHARGED_CD_TICKS));
		float dmg = chargedPunchDamage(p);
		BatchA.play(p, KEY, "haymaker", 14);

		Vec3 look = p.getLookAngle();
		Vec3 fist = p.getEyePosition().add(look.scale(1.6));
		level.sendParticles(ParticleTypes.SWEEP_ATTACK, fist.x, fist.y, fist.z, 6, 0.3, 0.3, 0.3, 0.0);
		level.sendParticles(ParticleTypes.EXPLOSION, fist.x, fist.y, fist.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.CRIT, fist.x, fist.y, fist.z, 24, 0.5, 0.5, 0.5, 0.5);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState()),
				fist.x, fist.y - 0.4, fist.z, 24, 0.5, 0.3, 0.5, 0.15);
		level.playSound(null, p.blockPosition(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.4f, 0.45f);

		LivingEntity aimed = AbilityHelpers.raycastEntity(p, 4.5);
		java.util.List<LivingEntity> victims;
		if (aimed != null) {
			victims = java.util.List.of(aimed);
		} else {
			Vec3 centre = p.getEyePosition().add(look.scale(2.6));
			victims = AbilityHelpers.living(level, centre, 3.5, e -> e != p
					&& new Vec3(e.getX() - p.getX(), e.getEyeY() - p.getEyeY(), e.getZ() - p.getZ())
							.normalize().dot(look) > 0.3);
		}
		for (LivingEntity e : victims) {
			AbilityHelpers.hurt(p, e, AbilityHelpers.kinetic(p), dmg);
			AbilityHelpers.knockbackFrom(e, p.position(), 3.6);
			AbilityHelpers.push(e, new Vec3(0, 0.5, 0));
			if (e instanceof Player victim) {
				victim.stopUsingItem();
				victim.getCooldowns().addCooldown(Items.SHIELD, 100);
			}
		}
	}

	// ---- R: Haymaker ------------------------------------------------------------------------------

	/** Aimed target in reach, else the nearest enemy close enough to swing at (anything not behind you). */
	private static LivingEntity meleeTarget(ServerPlayer p, double reach, double closeRadius) {
		LivingEntity aimed = AbilityHelpers.raycastEntity(p, reach);
		if (aimed != null && (!(aimed instanceof Player) || AbilityHelpers.enemiesAround(p, aimed.position(), 0.1).contains(aimed))) {
			return aimed;
		}
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		Vec3 flat = BatchA.flatLook(p);
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position(), closeRadius)) {
			Vec3 to = new Vec3(e.getX() - p.getX(), 0, e.getZ() - p.getZ());
			if (to.lengthSqr() > 0.01 && to.normalize().dot(flat) < -0.3) {
				continue; // behind you
			}
			double d = e.distanceToSqr(p);
			if (d < bestD) {
				bestD = d;
				best = e;
			}
		}
		return best;
	}

	private static void haymaker(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		int step = res(p, "combo_window") > 0.5f ? (int) res(p, "combo") + 1 : 1;
		if (step > 3) {
			step = 1;
		}
		LivingEntity t = meleeTarget(p, 4.5, 3.0);
		float dmg = HAYMAKER_DAMAGE[step - 1] * effort(p);
		String anim = step == 1 ? "punch_right" : step == 2 ? "punch_left" : "haymaker";
		BatchA.play(p, KEY, anim, step == 3 ? 18 : 12);
		Vec3 fist = p.getEyePosition().add(p.getLookAngle().scale(1.4));
		if (t != null) {
			AbilityHelpers.hurtBurst(p, t, AbilityHelpers.kinetic(p), dmg);
			Vec3 hit = t.position().add(0, t.getBbHeight() * 0.6, 0);
			fist = hit;
			if (step == 3) {
				AbilityHelpers.knockbackFrom(t, p.position(), 3.2 * (maxEffortActive(p) ? 1.3 : 1.0));
				AbilityHelpers.push(t, new Vec3(0, 0.75, 0));
				if (t instanceof Player victim) {
					victim.stopUsingItem();
					victim.getCooldowns().addCooldown(Items.SHIELD, 60);
				}
			} else {
				AbilityHelpers.knockbackFrom(t, p.position(), 0.5);
			}
		}
		level.sendParticles(ParticleTypes.CRIT, fist.x, fist.y, fist.z, step == 3 ? 24 : 8, 0.3, 0.3, 0.3, 0.3);
		if (step == 3) {
			level.sendParticles(ParticleTypes.EXPLOSION, fist.x, fist.y, fist.z, 1, 0, 0, 0, 0);
			level.sendParticles(ParticleTypes.SWEEP_ATTACK, fist.x, fist.y, fist.z, 3, 0.3, 0.2, 0.3, 0);
			level.playSound(null, p.blockPosition(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.3f, 0.5f);
			level.playSound(null, p.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.5f, 1.4f);
			res(p, "combo", 0);
			res(p, "combo_window", 0);
			triggerCd(ctx, HAYMAKER_CD);
		} else {
			AbilityHelpers.sound(p, t != null ? SoundEvents.PLAYER_ATTACK_STRONG : SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f,
					step == 1 ? 1.0f : 0.85f);
			res(p, "combo", step);
			res(p, "combo_window", COMBO_WINDOW);
			ctx.triggerCooldown(COMBO_GAP);
		}
	}

	private static void haymakerTick(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		float w = res(p, "combo_window");
		if (w <= 0.5f) {
			return;
		}
		res(p, "combo_window", w - 1);
		if (w - 1 <= 0.5f && res(p, "combo") > 0.5f) {
			res(p, "combo", 0);
			ctx.triggerCooldown(COMBO_DROP_CD); // the combo was dropped half way
		}
	}

	// ---- G: Ground Slam / air dive -------------------------------------------------------------

	private static void groundSlamActivate(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		if (p.onGround()) {
			BatchA.play(p, KEY, "slam_two_hand", 18);
			groundSlam(ctx, SLAM_DAMAGE, 5.0, 0.85, 1.0);
			triggerCd(ctx, SLAM_CD);
		} else {
			res(p, "diving", 1);
			res(p, "dive_from_y", (float) Math.max(0, p.getY() + 1000));
			Vec3 look = p.getLookAngle();
			AbilityHelpers.launchSelf(p, new Vec3(look.x * 0.35, -2.4, look.z * 0.35));
			AbilityHelpers.sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 0.5f);
			MutationVisuals.play(p, "p01.dive");
		}
	}

	private static void groundSlamTick(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		if (res(p, "diving") <= 0.5f) {
			return;
		}
		if (p.onGround() || p.verticalCollisionBelow || p.isInWater()) {
			double dist = Math.max(0, (res(p, "dive_from_y") - 1000) - p.getY());
			float dmg;
			if (dist < 6) {
				dmg = SLAM_DAMAGE;
			} else if (dist < 10) {
				dmg = 22f;
			} else if (dist < 20) {
				dmg = 26f;
			} else if (dist < 30) {
				dmg = 30f;
			} else {
				dmg = 34f;
			}
			res(p, "diving", 0);
			res(p, "dive_from_y", 0);
			p.resetFallDistance(); // committed dive: the landing shrugs off fall damage
			groundSlam(ctx, dmg, 5.0, 0.6, 1.4);
			landingImpact(ctx.level(), p, 3);
			triggerCd(ctx, SLAM_CD);
		}
	}

	/** Radial shockwave: damage, 2 s slow, upward launch. Leaves terrain intact. Maximum Effort: 1.5x radius, 2x damage. */
	private static void groundSlam(AbilityContext ctx, float damage, double radius, double launch, double kb) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		if (maxEffortActive(p)) {
			radius *= 1.5;
			damage *= 2.0f;
			kb *= 1.3;
		}
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position(), radius)) {
			AbilityHelpers.hurt(p, e, damage);
			AbilityHelpers.knockbackFrom(e, p.position(), kb);
			AbilityHelpers.push(e, new Vec3(0, launch, 0));
			AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, 40, 2);
		}
		level.sendParticles(ParticleTypes.EXPLOSION, p.getX(), p.getY() + 0.1, p.getZ(), 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.1, p.getZ(), 50, radius / 2, 0.1, radius / 2, 0.06);
		BatchA.debrisRing(level, p.position(), radius * 0.45, 16);
		BatchA.debrisRing(level, p.position(), radius * 0.8, 24);
		AbilityHelpers.sound(p, SoundEvents.GENERIC_EXPLODE.value(), 0.9f, 1.1f);
	}

	// ---- Shift+G: Thunderclap --------------------------------------------------------------------

	/** A clap that sends a cone of air out in front: stuns, throws back, reverses projectiles and snuffs fire. */
	private static void thunderclap(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		double range = maxEffortActive(p) ? 11.0 : 9.0;
		double minDot = 0.5; // a 120-degree cone
		BatchA.play(p, KEY, "clap", 16);
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		float dmg = 9f * effort(p);
		for (LivingEntity e : AbilityHelpers.living(level, eye, range, e -> e != p)) {
			if (!BatchA.inCone(p, e, minDot)) {
				continue;
			}
			e.clearFire();
			if (!AbilityHelpers.enemiesAround(p, e.position(), 0.05).contains(e)) {
				continue; // allies / protected players: just put out, never hit
			}
			AbilityHelpers.hurt(p, e, dmg);
			AbilityHelpers.knockbackFrom(e, p.position(), 2.4);
			AbilityHelpers.push(e, new Vec3(0, 0.3, 0));
			AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, 50, 4);
			AbilityHelpers.applyControl(e, MobEffects.WEAKNESS, 50, 1);
			level.sendParticles(ParticleTypes.CRIT, e.getX(), e.getY() + e.getBbHeight(), e.getZ(), 6, 0.3, 0.2, 0.3, 0.1);
		}
		for (Projectile proj : level.getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(range))) {
			Vec3 to = proj.position().subtract(eye);
			if (to.lengthSqr() > 1.0e-3 && to.normalize().dot(look) >= minDot) {
				proj.setDeltaMovement(to.normalize().scale(Math.max(1.0, proj.getDeltaMovement().length())));
				proj.hurtMarked = true;
			}
		}
		// snuff out fire in the cone (and on yourself) -- extinguishing is never griefing
		p.clearFire();
		BlockPos c = p.blockPosition();
		int r = (int) Math.ceil(range);
		int put = 0;
		for (BlockPos bp : BlockPos.betweenClosed(c.offset(-r, -2, -r), c.offset(r, 3, r))) {
			BlockState st = level.getBlockState(bp);
			if (!st.is(net.minecraft.tags.BlockTags.FIRE)) {
				continue;
			}
			Vec3 to = Vec3.atCenterOf(bp).subtract(eye);
			if (to.length() <= range && to.normalize().dot(look) >= minDot - 0.15) {
				level.removeBlock(bp, false);
				put++;
			}
		}
		if (put > 0) {
			level.playSound(null, p.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.0f, 1.0f);
		}
		// the visible blast: a cone of air rolling out from the hands
		Vec3 hands = eye.add(look.scale(0.8)).add(0, -0.35, 0);
		level.sendParticles(ParticleTypes.GUST_EMITTER_SMALL, hands.x, hands.y, hands.z, 1, 0, 0, 0, 0);
		Vec3 right = look.cross(new Vec3(0, 1, 0));
		right = right.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : right.normalize();
		Vec3 up = right.cross(look).normalize();
		for (int i = 0; i < 40; i++) {
			double dist = 1.0 + (i % 8) * range / 8.0;
			double a = i * 2.39996;
			double spread = dist * 0.55 * ((i % 5) / 4.0);
			Vec3 at = hands.add(look.scale(dist)).add(right.scale(Math.cos(a) * spread)).add(up.scale(Math.sin(a) * spread * 0.6));
			level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 1, look.x * 0.4, look.y * 0.4, look.z * 0.4, 1.0);
		}
		level.sendParticles(ParticleTypes.EXPLOSION, hands.x, hands.y, hands.z, 1, 0, 0, 0, 0);
		level.playSound(null, p.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.7f, 1.7f);
		level.playSound(null, p.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.6f, 1.6f);
		res(p, "clap_cd", com.projecthero.mod.hero.HeroConfig.get().scaledCooldown(effortCd(p, THUNDERCLAP_CD)));
	}

	// ---- X: Power Leap ----------------------------------------------------------------------

	/** 0.5 s per tier of charge, 2.5 s (50 ticks) = maximum. Mirrors the client-side charge bar. */
	public static final int LEAP_MAX_CHARGE = 50;

	private static int leapTier(int heldTicks) {
		return heldTicks < 10 ? 0 : heldTicks < 20 ? 1 : heldTicks < 30 ? 2 : heldTicks < 40 ? 3 : heldTicks < 50 ? 4 : 5;
	}

	/**
	 * The client released X after holding it {@code heldTicks} ticks. Launch along the full line of
	 * sight, scaled by the charge tier. Cooldown-gated here (the only authority).
	 */
	public static void performPowerLeap(ServerPlayer p, int heldTicks) {
		Power power = power();
		if (power == null || !owns(p)) {
			return;
		}
		com.projecthero.mod.hero.Ability ab = power.ability(com.projecthero.mod.hero.AbilitySlot.SLOT_3);
		if (!com.projecthero.mod.hero.ExperimentalPowers.cooldownReady(p, power, ab)) {
			return;
		}
		res(p, "leap_press", 0);

		int tier = leapTier(heldTicks);
		double[] speed = {2.4, 3.3, 4.3, 5.4, 6.6, 7.8};
		double s = speed[tier] * (maxEffortActive(p) ? 1.2 : 1.0);
		Vec3 dir = p.getLookAngle().normalize();
		if (dir.y < 0.15) {
			dir = dir.add(0, 0.30, 0).normalize();
		}
		AbilityHelpers.launchSelf(p, dir.scale(s));
		res(p, "no_fall_until", p.level().getGameTime() + 600);
		// arm the hero landing: it fires the first time you touch down after the launch
		res(p, "leap_air", 1 + tier);
		res(p, "leap_air_ticks", 0);
		BatchA.play(p, KEY, "leap", 22);
		if (p.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.EXPLOSION, p.getX(), p.getY() + 0.1, p.getZ(), 1, 0, 0, 0, 0);
			level.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.1, p.getZ(), 40, 0.4, 0.2, 0.4, 0.1);
			BatchA.debrisRing(level, p.position(), 1.2, 12);
		}
		AbilityHelpers.sound(p, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.1f, 0.5f);
		com.projecthero.mod.hero.ExperimentalPowers.triggerCooldown(p, power, ab,
				com.projecthero.mod.hero.HeroConfig.get().scaledCooldown(maxEffortActive(p) ? 25 : 51));
	}

	private static void leapTick(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		// ---- wind-up (while X is held) ----
		float press = res(p, "leap_press");
		if (press > 0) {
			long held = p.level().getGameTime() - (long) press;
			if (held < 0 || held > LEAP_MAX_CHARGE + 30) {
				res(p, "leap_press", 0); // lost the release edge -- do not leak dust forever
				MutationVisuals.stopIf(p, "crouch_charge");
			} else {
				BatchA.stance(p, KEY, "crouch_charge");
				ctx.level().sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.1, p.getZ(),
						3, 0.3, 0.05, 0.3, 0.02);
				if (held > 0 && held <= LEAP_MAX_CHARGE && held % 10 == 0) {
					AbilityHelpers.sound(p, SoundEvents.STONE_HIT, 0.5f, 0.8f + held / 60.0f);
					BatchA.debrisRing(ctx.level(), p.position(), 0.8 + held / 25.0, 8);
				}
			}
		}
		// ---- in the air after a leap: land it like a hero ----
		float air = res(p, "leap_air");
		if (air <= 0.5f) {
			return;
		}
		float t = res(p, "leap_air_ticks") + 1;
		res(p, "leap_air_ticks", t);
		if (p.isInWater() || t > 240 || p.getAbilities().flying) {
			res(p, "leap_air", 0);
			res(p, "leap_air_ticks", 0);
			return;
		}
		if (t > 4 && p.onGround()) {
			heroLanding(ctx, (int) air - 1);
			res(p, "leap_air", 0);
			res(p, "leap_air_ticks", 0);
		}
	}

	/** The v0.14.5 superhero-landing pose (client: {@code StrengthLandingPose}): one knee down, fist planted, head bowed. */
	public static final String LANDING_POSE = "p01.landing";

	/** Crater impact at the end of a Power Leap: bigger and harder the more the leap was charged. */
	private static void heroLanding(AbilityContext ctx, int tier) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		double radius = 2.5 + tier * 0.6;
		float dmg = (7f + tier * 3.0f) * effort(p);
		p.resetFallDistance();
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position(), radius)) {
			AbilityHelpers.hurt(p, e, dmg);
			AbilityHelpers.knockbackFrom(e, p.position(), 0.8 + tier * 0.2);
			AbilityHelpers.push(e, new Vec3(0, 0.35 + tier * 0.06, 0));
		}
		landingImpact(level, p, tier);
	}

	/**
	 * The look of a Super Strength landing, synced to everyone: the superhero-landing pose, a ring of cracks radiating
	 * out through the block you landed on, a rolling dust ring, the ground's own break sound under the boom, and a
	 * short camera shake for anyone close by. {@code tier} 0..5 scales it.
	 */
	static void landingImpact(ServerLevel level, ServerPlayer p, int tier) {
		MutationVisuals.play(p, LANDING_POSE);
		Vec3 c = p.position();
		double radius = 2.5 + tier * 0.6;
		BlockPos below = BlockPos.containing(c.x, c.y - 0.2, c.z);
		BlockState ground = level.getBlockState(below);
		if (ground.isAir() || !ground.getFluidState().isEmpty()) {
			ground = Blocks.DIRT.defaultBlockState();
		}
		BlockParticleOption crack = new BlockParticleOption(ParticleTypes.BLOCK, ground);
		// radial cracks: seven jagged spokes of the landed-on block kicked up along the ground
		int spokes = 7;
		double phase = (p.getId() % 16) * 0.39;
		for (int i = 0; i < spokes; i++) {
			double a = phase + i * Math.PI * 2 / spokes;
			for (double d = 0.4; d <= radius; d += 0.35) {
				double wob = Math.sin(d * 3.1 + i) * 0.18;
				double x = c.x + Math.cos(a + wob) * d;
				double z = c.z + Math.sin(a + wob) * d;
				level.sendParticles(crack, x, c.y + 0.05, z, 2, 0.06, 0.02, 0.06, 0.05);
			}
		}
		// the fist-plant burst and the ground heaving at the rim
		level.sendParticles(crack, c.x, c.y + 0.15, c.z, 30 + tier * 6, 0.35, 0.1, 0.35, 0.25);
		BatchA.debrisRing(level, c, radius, 20 + tier * 3);
		// a low rolling dust ring
		BatchA.ring(level, c.add(0, 0.12, 0), 0.5, ParticleTypes.CLOUD, 24, 0.28 + tier * 0.05);
		level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, c.x, c.y + 0.1, c.z, 3 + tier, radius * 0.35, 0.05,
				radius * 0.35, 0.01);
		if (tier >= 3) {
			level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y + 0.1, c.z, 1, 0, 0, 0, 0);
		}
		level.playSound(null, p.blockPosition(), ground.getSoundType().getBreakSound(), SoundSource.PLAYERS, 1.4f, 0.55f);
		level.playSound(null, p.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS,
				0.45f + tier * 0.1f, 0.55f);
		level.playSound(null, p.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.35f, 0.5f);
		// a short camera shake for anyone near the impact (purely cosmetic)
		float intensity = 0.18f + tier * 0.06f;
		for (ServerPlayer viewer : level.players()) {
			double d2 = viewer.distanceToSqr(c);
			if (d2 <= 16 * 16) {
				try {
					net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(viewer,
							new com.projecthero.mod.network.TitanShakePayload(intensity * (float) (1.0 - Math.sqrt(d2) / 16.0 * 0.8), 8));
				} catch (RuntimeException ignored) {
					// a connection that cannot take the packet (e.g. a test mock) just misses the shake
				}
			}
		}
	}

	// ---- C: Maximum Effort -----------------------------------------------------------------

	private static void maximumEffort(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		res(p, "effort_left", EFFORT_TICKS);
		int d = EFFORT_TICKS;
		PowerToggles.modifier(p, Attributes.KNOCKBACK_RESISTANCE, EFFORT_KB, 1.0, AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(p, Attributes.ATTACK_DAMAGE, EFFORT_ATK, 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, d, 1, false, false, true));
		p.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, d, 1, false, false, true));
		p.addEffect(new MobEffectInstance(MobEffects.JUMP, d, 1, false, false, true));
		BatchA.play(p, KEY, "power_up", 24);
		ServerLevel level = ctx.level();
		AbilityHelpers.burst(level, p.position().add(0, 1, 0), ParticleTypes.CRIT, 40, 0.6);
		BatchA.ring(level, p.position().add(0, 0.1, 0), 0.5, ParticleTypes.CLOUD, 24, 0.4);
		BatchA.debrisRing(level, p.position(), 1.5, 16);
		AbilityHelpers.sound(p, SoundEvents.PLAYER_LEVELUP, 0.9f, 0.6f);
		p.level().playSound(null, p.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 1.0f, 1.4f);
		ctx.actionBar("message.projecthero.strength.maximum_effort");
		ctx.triggerCooldown();
	}

	// ---- V: Grab & Throw ------------------------------------------------------------------

	/** Where a held creature / chunk sits: straight above your head, a touch forward. */
	private static Vec3 overhead(ServerPlayer p, double extra) {
		Vec3 f = BatchA.flatLook(p);
		return new Vec3(p.getX() + f.x * 0.25, p.getY() + p.getBbHeight() + extra, p.getZ() + f.z * 0.25);
	}

	private static void grabThrow(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		int mobId = (int) res(p, "grabbed");
		int chunkId = (int) res(p, "chunk_id");
		// ---- something already in hand: throw it (or, sneaking, set a creature down) ----
		if (mobId != 0) {
			Entity e = level.getEntity(mobId);
			res(p, "grabbed", 0);
			res(p, "grab_ticks", 0);
			if (e instanceof LivingEntity le && le.isAlive()) {
				le.setNoGravity(false);
				if (p.isShiftKeyDown()) {
					le.setDeltaMovement(0, -0.05, 0);
					le.hurtMarked = true;
					BatchA.play(p, KEY, "grab_pull", 12);
					AbilityHelpers.sound(p, SoundEvents.PLAYER_ATTACK_WEAK, 0.7f, 1.0f);
				} else {
					le.setDeltaMovement(p.getLookAngle().scale(2.8).add(0, 0.25, 0));
					le.hurtMarked = true;
					le.hasImpulse = true;
					res(p, "thrown_id", le.getId());
					res(p, "thrown_ticks", 30);
					BatchA.play(p, KEY, "throw_right", 14);
					AbilityHelpers.sound(p, SoundEvents.PLAYER_ATTACK_STRONG, 1.0f, 0.7f);
					level.sendParticles(ParticleTypes.CLOUD, le.getX(), le.getY() + 0.5, le.getZ(), 10, 0.3, 0.3, 0.3, 0.05);
				}
			}
			MutationVisuals.stopIf(p, "carry_overhead");
			triggerCd(ctx, THROW_CD);
			return;
		}
		if (chunkId != 0) {
			res(p, "chunk_id", 0);
			res(p, "grab_ticks", 0);
			if (level.getEntity(chunkId) instanceof ThrownChunkEntity chunk && chunk.isAlive()) {
				float m = effort(p);
				chunk.launch(p.getLookAngle().scale(2.2).add(0, 0.12, 0), 19f * m, 8f * m, 2.5);
				BatchA.play(p, KEY, "throw_right", 14);
				AbilityHelpers.sound(p, SoundEvents.PLAYER_ATTACK_STRONG, 1.0f, 0.6f);
			}
			MutationVisuals.stopIf(p, "carry_overhead");
			triggerCd(ctx, THROW_CD);
			return;
		}
		// ---- empty-handed: grab a creature, else rip the block you are looking at out of the world ----
		LivingEntity target = AbilityHelpers.raycastEntity(p, 5.0);
		if (target != null && AbilityHelpers.isValidGrabTarget(target, p)) {
			res(p, "grabbed", target.getId());
			res(p, "grab_ticks", GRAB_HOLD);
			target.setNoGravity(true);
			BatchA.play(p, KEY, "grab_pull", 10);
			AbilityHelpers.sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.8f, 0.7f);
			ctx.actionBar("message.projecthero.ability.grabbed");
			return;
		}
		BlockHitResult bhr = AbilityHelpers.raycastBlock(p, 5.0);
		if (bhr.getType() == HitResult.Type.BLOCK) {
			BlockPos bp = bhr.getBlockPos();
			BlockState st = level.getBlockState(bp);
			if (rippable(level, bp, st)) {
				if (AbilityHelpers.canGrief()) {
					level.removeBlock(bp, false);
				}
				ThrownChunkEntity chunk = ThrownChunkEntity.create(level, p, st, 1.0f);
				chunk.hold(Vec3.atBottomCenterOf(bp));
				level.addFreshEntity(chunk);
				res(p, "chunk_id", chunk.getId());
				res(p, "grab_ticks", GRAB_HOLD);
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, st), bp.getX() + 0.5, bp.getY() + 0.5,
						bp.getZ() + 0.5, 30, 0.4, 0.4, 0.4, 0.15);
				level.playSound(null, bp, st.getSoundType().getBreakSound(), SoundSource.PLAYERS, 1.2f, 0.7f);
				BatchA.play(p, KEY, "grab_pull", 10);
				return;
			}
		}
		ctx.actionBar("message.projecthero.strength.nothing_to_grab");
	}

	/** A block a strongman can tear loose: breakable, not a container or other block entity, not obsidian-hard. */
	private static boolean rippable(ServerLevel level, BlockPos bp, BlockState st) {
		if (st.isAir() || !st.getFluidState().isEmpty() || st.hasBlockEntity()) {
			return false;
		}
		float hard = st.getDestroySpeed(level, bp);
		return hard >= 0f && hard < 10f && !st.is(Blocks.BEDROCK);
	}

	private static void grabTick(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		// a hurled creature bowls over what it hits
		int thrownId = (int) res(p, "thrown_id");
		if (thrownId != 0) {
			float tt = res(p, "thrown_ticks") - 1;
			Entity te = level.getEntity(thrownId);
			if (!(te instanceof LivingEntity thrown) || !thrown.isAlive() || tt <= 0) {
				res(p, "thrown_id", 0);
				res(p, "thrown_ticks", 0);
			} else {
				res(p, "thrown_ticks", tt);
				java.util.List<LivingEntity> hitMobs = AbilityHelpers.enemiesAround(p, thrown.position(), 1.4);
				hitMobs.remove(thrown);
				boolean slam = thrown.horizontalCollision || !hitMobs.isEmpty() || (tt < 26 && thrown.onGround());
				if (slam) {
					float dmg = 12f * effort(p);
					AbilityHelpers.hurtBurst(p, thrown, dmg);
					for (LivingEntity o : hitMobs) {
						AbilityHelpers.hurt(p, o, dmg * 0.75f);
						AbilityHelpers.knockbackFrom(o, thrown.position(), 1.2);
					}
					level.sendParticles(ParticleTypes.EXPLOSION, thrown.getX(), thrown.getY() + 0.5, thrown.getZ(), 1, 0, 0, 0, 0);
					BatchA.debrisRing(level, thrown.position(), 1.0, 10);
					level.playSound(null, thrown.blockPosition(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.2f, 0.6f);
					res(p, "thrown_id", 0);
					res(p, "thrown_ticks", 0);
				}
			}
		}
		int mobId = (int) res(p, "grabbed");
		int chunkId = (int) res(p, "chunk_id");
		if (mobId == 0 && chunkId == 0) {
			return;
		}
		float ticks = res(p, "grab_ticks") - 1;
		if (mobId != 0) {
			Entity e = level.getEntity(mobId);
			if (!(e instanceof LivingEntity le) || !le.isAlive() || ticks <= 0 || p.distanceToSqr(e) > 100 || !p.isAlive()) {
				releaseGrab(p, true);
				return;
			}
			res(p, "grab_ticks", ticks);
			Vec3 hold = overhead(p, 0.25);
			le.setPos(hold.x, hold.y, hold.z);
			le.setDeltaMovement(Vec3.ZERO);
			le.fallDistance = 0;
			le.hurtMarked = true;
		} else {
			Entity e = level.getEntity(chunkId);
			if (!(e instanceof ThrownChunkEntity chunk) || !chunk.isAlive() || ticks <= 0 || !p.isAlive()) {
				releaseGrab(p, true);
				return;
			}
			res(p, "grab_ticks", ticks);
			chunk.hold(overhead(p, 0.3));
		}
		BatchA.stance(p, KEY, "carry_overhead");
	}

	/** Lets go of whatever is held (a creature is set free, a chunk crumbles). */
	private static void releaseGrab(ServerPlayer p, boolean startCooldown) {
		if (!(p.level() instanceof ServerLevel level)) {
			return;
		}
		int mobId = (int) res(p, "grabbed");
		int chunkId = (int) res(p, "chunk_id");
		if (mobId != 0 && level.getEntity(mobId) instanceof LivingEntity le) {
			le.setNoGravity(false);
		}
		if (chunkId != 0 && level.getEntity(chunkId) instanceof ThrownChunkEntity chunk && !chunk.flying()) {
			chunk.crumble(level);
		}
		if (mobId != 0 || chunkId != 0) {
			res(p, "grabbed", 0);
			res(p, "chunk_id", 0);
			res(p, "grab_ticks", 0);
			MutationVisuals.stopIf(p, "carry_overhead");
			Power power = power();
			if (startCooldown && power != null) {
				com.projecthero.mod.hero.ExperimentalPowers.triggerCooldown(p, power,
						power.ability(com.projecthero.mod.hero.AbilitySlot.SLOT_5),
						com.projecthero.mod.hero.HeroConfig.get().scaledCooldown(THROW_CD));
			}
		}
	}

	// ---- Shift+V: Rip & Hurl -----------------------------------------------------------------

	/** Tear a boulder out of the ground in front of you; it rises over your head and is hurled where you aim. */
	private static void ripHurl(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		Vec3 f = BatchA.flatLook(p);
		BlockPos probe = BlockPos.containing(p.getX() + f.x * 1.8, p.getY() - 0.5, p.getZ() + f.z * 1.8);
		BlockPos ground = null;
		for (int i = 0; i < 4; i++) {
			BlockPos bp = probe.below(i);
			if (!level.getBlockState(bp).isAir() && level.getBlockState(bp).getFluidState().isEmpty()) {
				ground = bp;
				break;
			}
		}
		if (ground == null) {
			ctx.actionBar("message.projecthero.strength.no_ground");
			return;
		}
		BlockState st = level.getBlockState(ground);
		BlockState boulder = rippable(level, ground, st) && st.isCollisionShapeFullBlock(level, ground) ? st
				: Blocks.COBBLESTONE.defaultBlockState();
		if (AbilityHelpers.canGrief() && rippable(level, ground, st)) {
			level.removeBlock(ground, false);
		}
		ThrownChunkEntity chunk = ThrownChunkEntity.create(level, p, boulder, 1.8f);
		chunk.hold(Vec3.atBottomCenterOf(ground));
		level.addFreshEntity(chunk);
		res(p, "rip_id", chunk.getId());
		res(p, "rip_ticks", RIP_RISE);
		res(p, "rip_from_y", (float) (ground.getY() + 1000));
		BatchA.play(p, KEY, "summon_ground", RIP_RISE + 2);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, boulder), ground.getX() + 0.5, ground.getY() + 1.0,
				ground.getZ() + 0.5, 40, 0.8, 0.3, 0.8, 0.2);
		BatchA.debrisRing(level, Vec3.atBottomCenterOf(ground.above()), 1.4, 14);
		level.playSound(null, ground, SoundEvents.ROOTED_DIRT_BREAK, SoundSource.PLAYERS, 1.5f, 0.6f);
		level.playSound(null, ground, boulder.getSoundType().getBreakSound(), SoundSource.PLAYERS, 1.5f, 0.5f);
		res(p, "rip_cd", com.projecthero.mod.hero.HeroConfig.get().scaledCooldown(effortCd(p, RIP_CD)));
	}

	private static void ripTick(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		int id = (int) res(p, "rip_id");
		if (id == 0) {
			return;
		}
		ServerLevel level = ctx.level();
		float t = res(p, "rip_ticks") - 1;
		Entity e = level.getEntity(id);
		if (!(e instanceof ThrownChunkEntity chunk) || !chunk.isAlive() || !p.isAlive()) {
			res(p, "rip_id", 0);
			res(p, "rip_ticks", 0);
			return;
		}
		if (t > 0) {
			res(p, "rip_ticks", t);
			double frac = 1.0 - t / RIP_RISE;
			Vec3 top = overhead(p, 0.4);
			Vec3 from = new Vec3(chunk.getX(), res(p, "rip_from_y") - 1000, chunk.getZ());
			chunk.hold(from.lerp(top, frac * frac * (3 - 2 * frac)));
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, chunk.blockState()), chunk.getX(), chunk.getY(),
					chunk.getZ(), 3, 0.5, 0.2, 0.5, 0.05);
			return;
		}
		res(p, "rip_id", 0);
		res(p, "rip_ticks", 0);
		float m = effort(p);
		chunk.hold(overhead(p, 0.4));
		// v0.14.5: a much harder throw -- about twice the old range
		chunk.launch(p.getLookAngle().scale(RIP_THROW_SPEED).add(0, 0.25, 0), 26f * m, 14f * m, 3.2);
		BatchA.play(p, KEY, "throw_right", 14);
		AbilityHelpers.sound(p, SoundEvents.PLAYER_ATTACK_STRONG, 1.2f, 0.5f);
		level.playSound(null, p.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 0.5f, 1.3f);
	}

	// ---- Z: Bull Rush (hold to charge for 5 s) ----------------------------------------------

	/** Press Z (hold): begin charging. Idempotent -- a repeated press while already charging or rushing is ignored. */
	private static void bullRushPress(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		if (res(p, "z_charge") > 0.5f || res(p, "z_run_end") > 0.5f) {
			return;
		}
		if (!ctx.cooldownReady()) {
			cooldownMessage(ctx);
			return;
		}
		res(p, "z_charge", p.level().getGameTime());
		rushGuards(p, true);
		MutationVisuals.play(p, "crouch_charge");
		AbilityHelpers.sound(p, SoundEvents.RAVAGER_ROAR, 0.8f, 0.55f);
	}

	/** Release Z: fire if the 5 s charge finished, otherwise cancel it. */
	private static void bullRushRelease(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		if (res(p, "z_charge") <= 0.5f) {
			return;
		}
		long held = p.level().getGameTime() - (long) res(p, "z_charge");
		if (held >= RUSH_CHARGE) {
			fireRush(ctx);
		} else {
			cancelRush(p);
		}
	}

	private static void bullRushTick(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		long now = p.level().getGameTime();
		ServerLevel level = ctx.level();

		// ---- charging ----
		float charge = res(p, "z_charge");
		if (charge > 0.5f) {
			long held = now - (long) charge;
			p.setDeltaMovement(p.getDeltaMovement().multiply(0.15, 1.0, 0.15));
			p.hurtMarked = true;
			MutationVisuals.ensure(p, "crouch_charge");
			double frac = Math.min(1.0, held / (double) RUSH_CHARGE);
			level.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.1, p.getZ(), 4 + (int) (frac * 8),
					0.5 * frac + 0.2, 0.05, 0.5 * frac + 0.2, 0.02);
			if (held == 20 || held == 60) {
				AbilityHelpers.sound(p, SoundEvents.PISTON_CONTRACT, 0.5f, 0.6f + (float) frac * 0.5f);
			}
			if (held >= RUSH_CHARGE) {
				fireRush(ctx);
			} else if (held > RUSH_CHARGE + 200 || held < 0) {
				cancelRush(p);
			}
			return;
		}

		// ---- rushing ----
		float runEnd = res(p, "z_run_end");
		if (runEnd <= 0.5f) {
			return;
		}
		Vec3 flat = BatchA.flatLook(p);
		double sprint = maxEffortActive(p) ? 0.62 : 0.55;
		p.setDeltaMovement(flat.x * sprint, Math.max(p.getDeltaMovement().y, -0.25), flat.z * sprint);
		p.hurtMarked = true;
		p.hasImpulse = true;
		BatchA.stance(p, KEY, "p01.rush");
		level.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.1, p.getZ(), 5, 0.3, 0.05, 0.3, 0.04);

		// Plough straight through -- hitting an enemy does NOT stop the rush.
		float m = maxEffortActive(p) ? 1.25f : 1.0f;
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position().add(flat.scale(1.3)), 2.0)) {
			AbilityHelpers.hurt(p, e, 24f * m);
			AbilityHelpers.knockbackFrom(e, p.position(), 4.2);
			AbilityHelpers.push(e, new Vec3(0, 0.45, 0));
		}
		long ticksRun = RUSH_RUN - ((long) runEnd - now);
		boolean stuck = ticksRun > 6 && p.horizontalCollision
				&& p.getDeltaMovement().horizontalDistanceSqr() < 0.06;
		if (now >= (long) runEnd || stuck || ticksRun > RUSH_RUN + 40) {
			endRush(ctx);
		}
	}

	/** The 5 s charge finished: launch the rush. */
	private static void fireRush(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		res(p, "z_charge", 0);
		MutationVisuals.stopIf(p, "crouch_charge");
		res(p, "z_run_end", p.level().getGameTime() + RUSH_RUN);
		PowerToggles.modifier(p, Attributes.STEP_HEIGHT, RUSH_STEP, 4.0, AttributeModifier.Operation.ADD_VALUE);
		MutationVisuals.play(p, "p01.rush");
		AbilityHelpers.sound(p, SoundEvents.RAVAGER_ROAR, 1.3f, 1.1f);
	}

	private static void cancelRush(ServerPlayer p) {
		res(p, "z_charge", 0);
		rushGuards(p, false);
		MutationVisuals.stopIf(p, "crouch_charge");
		AbilityHelpers.sound(p, SoundEvents.FIRE_EXTINGUISH, 0.5f, 1.2f);
	}

	private static void endRush(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		res(p, "z_run_end", 0);
		rushGuards(p, false);
		PowerToggles.clearModifier(p, Attributes.STEP_HEIGHT, RUSH_STEP);
		MutationVisuals.stopIf(p, "p01.rush");
		triggerCd(ctx, RUSH_CD);
		AbilityHelpers.sound(p, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0f, 0.7f);
	}

	/** Damage resistance + full knockback resistance, on while charging and rushing. */
	private static void rushGuards(ServerPlayer p, boolean on) {
		if (on) {
			PowerToggles.modifier(p, Attributes.KNOCKBACK_RESISTANCE, RUSH_KB, 1.0, AttributeModifier.Operation.ADD_VALUE);
			p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, RUSH_CHARGE + RUSH_RUN + 40, 0, false, false, true));
		} else {
			PowerToggles.clearModifier(p, Attributes.KNOCKBACK_RESISTANCE, RUSH_KB);
			p.removeEffect(MobEffects.DAMAGE_RESISTANCE);
		}
	}
}
