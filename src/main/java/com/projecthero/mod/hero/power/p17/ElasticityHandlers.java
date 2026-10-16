package com.projecthero.mod.hero.power.p17;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.hero.AbilityContext;
import com.projecthero.mod.hero.AbilityHandler;
import com.projecthero.mod.hero.AbilityHandlers;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.data.ExperimentalState;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.hero.revamp.BatchCFx;
import com.projecthero.mod.hero.revamp.batcha.BatchA;
import com.projecthero.mod.hero.visual.MutationVisuals;
import com.projecthero.mod.squad.Squads;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Power 17 -- Elasticity (v0.16.5 rework, docs/V0165_POWERS_PROPOSAL.md section 6): a Mr. Fantastic / Plastic Man /
 * Luffy rubber brawler. The limbs themselves stretch (the client lengthens the arm / leg model in the player's own
 * skin, from the synced {@code p17.*} visual values), and a <b>Stretch</b> meter replaces most cooldowns.
 *
 * <p>Stretch (0-100): every move spends some; it refills 15 a second (30 standing still) while no held move is
 * running. Run it dry and you go <b>limp</b>: Slowness II and no moves for a flat 8 s.
 *
 * <p>R Stretch Punch (hold to coil, up to 1.5 s) / Shift+R Gatling Barrage (held). G Elastic Grab, press again to hurl /
 * Shift+G Wrap &amp; Squeeze. X Slingshot (hold to pull back, release) / Shift+X Rubber Ball. Z Giant Hammer Fist (hold
 * to coil) / Shift+Z Spring Stomp. V Rubber Shield (held balloon) / Shift+V Parachute (toggle). C Long Reach (toggle) /
 * Shift+C Shape Shift. No H / N. All six slots are HOLD, so the handlers own their tap / Shift / release logic.
 */
public final class ElasticityHandlers {
	public static final String KEY = "power_17_elasticity";

	// ---- Stretch ----
	public static final float MAX_STRETCH = 100.0f;
	/** 15 a second; twice that standing still. */
	public static final float STRETCH_REGEN_PER_TICK = 15.0f / 20.0f;
	/** v0.16.5 (user): going limp lasts a flat 8 seconds (no moves, Slowness II), however fast the meter refills. */
	public static final int LIMP_TICKS = 8 * 20;

	public static final float COST_PUNCH = 10.0f;
	public static final float COST_GATLING_PER_SECOND = 12.0f;
	public static final float COST_GRAB = 15.0f;
	public static final float COST_WRAP = 25.0f;
	public static final float COST_SLING = 15.0f;
	public static final float COST_BALL = 30.0f;
	public static final float COST_HAMMER = 30.0f;
	public static final float COST_STOMP = 25.0f;
	public static final float COST_SHIELD_PER_SECOND = 8.0f;
	public static final float COST_SHAPE = 20.0f;

	// ---- R: Stretch Punch / Gatling Barrage ----
	/** R and Z coil for up to 1.5 s. */
	public static final int COIL_MAX_TICKS = 30;
	public static final float PUNCH_DAMAGE = 12.0f;
	public static final float PUNCH_DAMAGE_COILED = 24.0f;
	public static final double PUNCH_RANGE = 18.0;
	public static final double PUNCH_RANGE_COILED = 22.0;
	public static final float GATLING_DAMAGE = 12.8f; // v0.16.5 (user): 12.8 a punch
	public static final float GATLING_HITS_PER_SECOND = 6.0f;
	public static final double GATLING_RANGE = 10.0;
	/** cos of the cone's half-angle (~40 degrees). */
	private static final double GATLING_CONE = 0.76;

	// ---- G: Elastic Grab / Wrap & Squeeze ----
	public static final double GRAB_RANGE = 20.0;
	public static final int GRAB_HOLD_TICKS = 6 * 20;
	private static final double GRAB_HOLD_DISTANCE = 2.5;
	public static final float HURL_DAMAGE = 10.0f;
	public static final float HURL_IMPACT_BONUS = 6.0f;
	private static final int HURL_FLIGHT_TICKS = 30;
	public static final double WRAP_RANGE = 6.0;
	public static final int WRAP_TICKS = 4 * 20;
	public static final float WRAP_DAMAGE_PER_SECOND = 4.0f;

	// ---- X: Slingshot / Rubber Ball ----
	/** v0.16.5 (user): a longer reach for the anchor. */
	public static final double SLING_RANGE = 80.0;
	private static final int SLING_PULL_TICKS = 30;
	/** The launch: up to this many blocks a tick, from the coil (0..1) and the anchor's distance. */
	public static final double SLING_MAX_SPEED = 5.0;
	/** The no-drag flight ends after this long at most (and on landing, a wall or water). */
	private static final int SLING_FLY_TICKS = 15 * 20;
	public static final int BALL_TICKS = 6 * 20;
	public static final float BALL_HIT_DAMAGE = 6.0f;

	// ---- Z: Giant Hammer Fist / Spring Stomp ----
	public static final double HAMMER_RANGE = 12.0;
	public static final float HAMMER_DAMAGE_MIN = 20.0f;
	public static final float HAMMER_DAMAGE_MAX = 34.0f;
	public static final double HAMMER_RADIUS = 4.5;
	public static final int HAMMER_CD = 8 * 20;
	public static final float STOMP_DAMAGE = 20.0f;
	public static final double STOMP_RADIUS = 5.0;
	public static final int STOMP_CD = 6 * 20;
	/** Ticks of the stilt rise (a ~8-block launch) before the slam. */
	private static final int STOMP_RISE_TICKS = 13;

	// ---- V: Rubber Shield / Parachute ----
	/** The balloon takes 60% less damage. */
	public static final float SHIELD_DAMAGE_FACTOR = 0.4f;

	// ---- C: Long Reach / Shape Shift ----
	public static final double LONG_REACH = 12.0;
	public static final int SHAPE_CD = 10 * 20;

	// ---- passives ----
	public static final float BLUNT_FACTOR = 0.75f;
	public static final float FIRE_FACTOR = 1.25f;
	public static final double EXTRA_MELEE_REACH = 3.0;

	private static final ResourceLocation PASSIVE_KB = com.projecthero.mod.ProjectHeroMod.id("elastic_passive_kb");
	private static final ResourceLocation PASSIVE_REACH = com.projecthero.mod.ProjectHeroMod.id("elastic_passive_reach");
	private static final ResourceLocation LONG_REACH_ENTITY = com.projecthero.mod.ProjectHeroMod.id("elastic_long_reach_entity");
	private static final ResourceLocation LONG_REACH_BLOCK = com.projecthero.mod.ProjectHeroMod.id("elastic_long_reach_block");
	private static final ResourceLocation SHIELD_SPEED = com.projecthero.mod.ProjectHeroMod.id("elastic_shield_speed");
	private static final ResourceLocation SHIELD_KB = com.projecthero.mod.ProjectHeroMod.id("elastic_shield_kb");
	private static final ResourceLocation BALL_SPEED = com.projecthero.mod.ProjectHeroMod.id("elastic_ball_speed");
	private static final ResourceLocation BALL_SCALE = com.projecthero.mod.ProjectHeroMod.id("elastic_ball_scale");
	/** Pre-v0.16.5 modifiers, cleared out of old saves. */
	private static final ResourceLocation[] LEGACY = {
			com.projecthero.mod.ProjectHeroMod.id("elastic_passive_melee"), com.projecthero.mod.ProjectHeroMod.id("elastic_form_reach"),
			com.projecthero.mod.ProjectHeroMod.id("elastic_form_speed"), com.projecthero.mod.ProjectHeroMod.id("elastic_form_scale"),
			com.projecthero.mod.ProjectHeroMod.id("elastic_form_kb"), com.projecthero.mod.ProjectHeroMod.id("elastic_form_melee") };

	// ---- the stretched-limb visual ----
	/** How long a stretched arm stays drawn after a strike (the client shoots it out and snaps it back in this window). */
	public static final int ARM_STRIKE_TICKS = 14;
	/** Arm draw modes (sent +1 as the {@code p17.arm_mode} visual value). */
	public static final int ARM_RIGHT = 0;
	public static final int ARM_BOTH = 1;
	public static final int ARM_HAMMER = 2;
	public static final int ARM_HOLD = 3;
	public static final int ARM_HOLD_BOTH = 4;
	public static final int ARM_GATLING = 5;

	private ElasticityHandlers() {
	}

	// ================================================================ shared helpers / queries

	private static float res(ServerPlayer p, String name) {
		return BatchA.res(p, KEY, name);
	}

	private static void set(ServerPlayer p, String name, float v) {
		BatchA.set(p, KEY, name, v);
	}

	/** Reads a resource from the synced attachment (works on both sides; the client only has its own player's). */
	private static float resAny(Player p, String name) {
		ExperimentalState st = p.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		return st == null ? 0.0f : st.resources.getOrDefault(KEY + "/" + name, 0.0f);
	}

	public static boolean owns(Player p) {
		ExperimentalState st = p.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		return st != null && st.ownedPowers.contains(KEY);
	}

	public static float stretch(ServerPlayer p) {
		seed(p);
		return res(p, "stretch");
	}

	public static boolean limp(Player p) {
		return owns(p) && resAny(p, "limp") > 0.5f;
	}

	public static float coil(Player p) {
		return owns(p) ? resAny(p, "coil") : 0.0f;
	}

	/** Rubber Shield is inflated right now (the balloon body). */
	public static boolean shielding(Player p) {
		return owns(p) && resAny(p, "shielding") > 0.5f;
	}

	/** The Parachute canopy is out (also read by the client's glide steering). */
	public static boolean gliding(Player p) {
		return owns(p) && resAny(p, "glide") > 0.5f;
	}

	public static boolean balled(Player p) {
		return owns(p) && resAny(p, "ball") > 0.5f;
	}

	public static boolean gatling(Player p) {
		return owns(p) && resAny(p, "gatling") > 0.5f;
	}

	public static boolean longReach(Player p) {
		return owns(p) && resAny(p, "reach") > 0.5f;
	}

	/** On the stilts of a Spring Stomp (rising or at the top). */
	public static boolean onStilts(Player p) {
		return owns(p) && Math.round(resAny(p, "stomp")) == 1;
	}

	/** The Shape Shift block's state id + 1, or 0. */
	public static float shapeValue(Player p) {
		return owns(p) ? resAny(p, "shape") : 0.0f;
	}

	public static boolean shapeShifted(Player p) {
		return shapeValue(p) > 0.5f;
	}

	/** The id of the creature held in Wrap &amp; Squeeze, or 0. */
	public static float wrapId(Player p) {
		return owns(p) && resAny(p, "wrap_ticks") > 0.5f ? resAny(p, "wrap_id") : 0.0f;
	}

	/** The creature on the end of Elastic Grab, or 0. */
	public static float grabId(Player p) {
		return owns(p) && resAny(p, "grab_ticks") > 0.5f ? resAny(p, "grab_id") : 0.0f;
	}

	public static boolean armStretched(Player p) {
		return owns(p) && resAny(p, "arm_ticks") > 0.5f;
	}

	/** Stretched-arm length in blocks (0 when none) -- the {@code p17.arm_len} visual value. */
	public static float armLength(Player p) {
		return armStretched(p) ? resAny(p, "arm_len") : 0.0f;
	}

	/** Stretch start tick (game time mod 8192, +1 so it is never 0) -- the {@code p17.arm_t} visual value. */
	public static float armStart(Player p) {
		return armStretched(p) ? resAny(p, "arm_t") : 0.0f;
	}

	/** Arm draw mode + 1 -- the {@code p17.arm_mode} visual value. */
	public static float armMode(Player p) {
		return armStretched(p) ? resAny(p, "arm_mode") + 1.0f : 0.0f;
	}

	/**
	 * Start drawing a stretched arm: {@code blocks} long, for {@code ticks} ticks, in draw mode {@code mode}. Re-calling
	 * with a held mode every tick keeps the arm out.
	 */
	public static void stretchArm(ServerPlayer p, double blocks, int ticks, int mode) {
		boolean held = mode == ARM_HOLD || mode == ARM_HOLD_BOTH || mode == ARM_GATLING;
		boolean renewing = held && res(p, "arm_ticks") > 0.5f && Math.round(res(p, "arm_mode")) == mode;
		if (renewing && res(p, "arm_ticks") > 2.5f && Math.abs(res(p, "arm_len") - blocks) < 0.4) {
			return; // a held arm that has not moved: skip the resync
		}
		if (!renewing) {
			set(p, "arm_t", (float) (p.level().getGameTime() % 8192L) + 1.0f);
		}
		set(p, "arm_len", (float) Math.max(0.5, Math.min(SLING_RANGE, blocks)));
		set(p, "arm_ticks", ticks);
		set(p, "arm_mode", mode);
	}

	private static void seed(ServerPlayer p) {
		if (res(p, "seeded") < 0.5f) {
			set(p, "seeded", 1);
			set(p, "stretch", MAX_STRETCH);
		}
	}

	/** Refuses (with the limp message) while limp. */
	private static boolean canAct(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		seed(p);
		if (limp(p)) {
			ctx.actionBar("message.projecthero.elasticity.limp");
			return false;
		}
		endShape(p); // any move breaks the disguise
		return true;
	}

	/** Spends Stretch. The move that empties the meter still happens; then you go limp. */
	public static void spend(ServerPlayer p, float amount) {
		seed(p);
		float s = Math.max(0.0f, res(p, "stretch") - amount);
		set(p, "stretch", s);
		if (s <= 0.01f && !limp(p)) {
			goLimp(p);
		}
	}

	private static void goLimp(ServerPlayer p) {
		set(p, "limp", LIMP_TICKS); // counts down in the upkeep
		endChannels(p);
		p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, LIMP_TICKS, 1, false, false, true));
		p.displayClientMessage(Component.translatable("message.projecthero.elasticity.limp"), true);
		MutationVisuals.play(p, "p17.limp");
		AbilityHelpers.sound(p, SoundEvents.SLIME_SQUISH, 1.0f, 0.5f);
	}

	/** Everything held stops: coils, Gatling, the balloon, a slingshot being pulled back. */
	private static void endChannels(ServerPlayer p) {
		set(p, "coil", 0);
		set(p, "coil_kind", 0);
		set(p, "coil_start", 0);
		if (res(p, "gatling") > 0.5f) {
			set(p, "gatling", 0);
			MutationVisuals.stopIf(p, "p17.gatling");
		}
		if (res(p, "sling") > 0.5f) {
			set(p, "sling", 0);
			MutationVisuals.stopIf(p, "p17.sling");
		}
		endShield(p);
		MutationVisuals.stopIf(p, "p17.windup");
	}

	private static boolean channelling(ServerPlayer p) {
		return res(p, "coil_kind") > 0.5f || res(p, "gatling") > 0.5f || res(p, "shielding") > 0.5f || res(p, "sling") > 0.5f;
	}

	private static float coilFraction(ServerPlayer p) {
		float start = res(p, "coil_start");
		if (start <= 0.5f) {
			return 0.0f;
		}
		long held = p.level().getGameTime() - (long) start;
		return (float) Math.min(1.0, held / (double) COIL_MAX_TICKS);
	}

	private static boolean noBoss(LivingEntity t) {
		return !com.projecthero.mod.titanshifter.TitanCombat.isBoss(t) && t.getMaxHealth() <= 200.0f;
	}

	private static Vec3 mid(Entity e) {
		return e.position().add(0, e.getBbHeight() * 0.5, 0);
	}

	private static void slime(ServerLevel level, Vec3 at, int count, double spread) {
		level.sendParticles(ParticleTypes.ITEM_SLIME, at.x, at.y, at.z, count, spread, spread, spread, 0.08);
	}

	// ================================================================ registration

	public static void register() {
		// v0.16.5 (user: "make the parachute glide work the same as elytra"): the open canopy is a custom elytra -- vanilla
		// elytra flight (steer with your look, dive for speed, pull up to climb) for as long as it is out
		net.fabricmc.fabric.api.entity.event.v1.EntityElytraEvents.CUSTOM.register((entity, tickElytra) ->
				entity instanceof Player pl && gliding(pl) && !pl.getAbilities().flying);
		// R -- Stretch Punch (tap / hold to coil) / Gatling Barrage (Shift, held). Also runs the power-wide upkeep.
		AbilityHandlers.register(KEY, "stretch_punch", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				if (!canAct(ctx)) {
					return;
				}
				ServerPlayer p = ctx.player();
				if (p.isShiftKeyDown()) {
					set(p, "gatling", 1);
					set(p, "gatling_acc", 0.99f);
					MutationVisuals.play(p, "p17.gatling");
					AbilityHelpers.sound(p, SoundEvents.SLIME_JUMP, 1.0f, 1.4f);
					return;
				}
				startCoil(p, 1);
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				upkeep(ctx);
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (res(p, "gatling") > 0.5f) {
					set(p, "gatling", 0);
					MutationVisuals.stopIf(p, "p17.gatling");
				}
				if (Math.round(res(p, "coil_kind")) == 1) {
					float f = coilFraction(p);
					clearCoil(p);
					stretchPunch(p, f);
				}
			}
		});

		// G -- Elastic Grab (press again to hurl) / Wrap & Squeeze (Shift; press again to let go)
		AbilityHandlers.register(KEY, "elastic_grab", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (res(p, "wrap_ticks") > 0.5f) {
					endWrap(p);
					return;
				}
				if (!canAct(ctx)) {
					return;
				}
				if (p.isShiftKeyDown()) {
					wrap(ctx);
				} else if (res(p, "grab_ticks") > 0.5f) {
					hurl(p);
				} else {
					grab(ctx);
				}
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				grabTick(ctx.player(), ctx.level());
				hurlTick(ctx.player(), ctx.level());
				wrapTick(ctx.player(), ctx.level());
			}
		});

		// X -- Slingshot (hold to pull back, release to launch) / Rubber Ball (Shift)
		AbilityHandlers.register(KEY, "slingshot", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (p.isShiftKeyDown() && res(p, "ball") > 0.5f) {
					endBall(p); // a second Shift+X rolls back out early
					return;
				}
				if (!canAct(ctx)) {
					return;
				}
				if (p.isShiftKeyDown()) {
					startBall(p);
				} else {
					startSling(ctx);
				}
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				slingTick(ctx.player(), ctx.level());
				ballTick(ctx.player(), ctx.level());
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				releaseSling(ctx.player(), ctx.level());
			}
		});

		// Z -- Giant Hammer Fist (hold to coil) / Spring Stomp (Shift)
		AbilityHandlers.register(KEY, "giant_hammer_fist", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (p.isShiftKeyDown()) {
					if (res(p, "stomp_cd") > 0.5f) {
						cooling(ctx, "stomp_cd");
						return;
					}
					if (canAct(ctx)) {
						startStomp(p);
					}
					return;
				}
				if (res(p, "hammer_cd") > 0.5f) {
					cooling(ctx, "hammer_cd");
					return;
				}
				if (canAct(ctx)) {
					startCoil(p, 2);
				}
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				stompTick(ctx.player(), ctx.level());
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (Math.round(res(p, "coil_kind")) == 2) {
					float f = coilFraction(p);
					clearCoil(p);
					hammer(p, f);
				}
			}
		});

		// V -- Rubber Shield (held balloon) / Parachute (Shift, toggle)
		AbilityHandlers.register(KEY, "rubber_shield", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (p.isShiftKeyDown()) {
					if (res(p, "glide") > 0.5f) {
						endGlide(p);
					} else if (canAct(ctx)) {
						startGlide(p);
					}
					return;
				}
				if (!canAct(ctx)) {
					return;
				}
				startShield(p);
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				shieldTick(ctx.player());
				glideTick(ctx.player(), ctx.level());
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				endShield(ctx.player());
			}
		});

		// C -- Long Reach (toggle) / Shape Shift (Shift)
		AbilityHandlers.register(KEY, "long_reach", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (p.isShiftKeyDown()) {
					if (res(p, "shape") > 0.5f) {
						endShape(p);
						return;
					}
					if (res(p, "shape_cd") > 0.5f) {
						cooling(ctx, "shape_cd");
						return;
					}
					if (canAct(ctx)) {
						shapeShift(ctx);
					}
					return;
				}
				if (res(p, "reach") > 0.5f) {
					setReach(p, false);
					ctx.actionBar("message.projecthero.elasticity.reach_off");
				} else if (canAct(ctx)) {
					setReach(p, true);
					ctx.actionBar("message.projecthero.elasticity.reach_on");
					MutationVisuals.play(p, "p17.stretch_both");
					stretchArm(p, 4.0, 10, ARM_BOTH);
					AbilityHelpers.sound(p, SoundEvents.SLIME_SQUISH, 0.8f, 1.5f);
				}
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				reachTick(ctx.player(), ctx.level());
				shapeTick(ctx.player(), ctx.level());
			}
		});

		// the balloon flings melee attackers away (also when the hit was swallowed whole)
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (entity instanceof ServerPlayer sp && shielding(sp) && meleeAttacker(sp, source) instanceof LivingEntity attacker) {
				bounceAttacker(sp, attacker);
			}
		});
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(ElasticityHandlers::allowDamage);

		// Long Reach: the arm visibly stretches out to whatever you hit or use beyond normal reach
		AttackBlockCallback.EVENT.register((player, level, hand, pos, dir) -> {
			if (player instanceof ServerPlayer sp && owns(sp)) {
				endShape(sp); // so does hitting a block
			}
			reachVisual(player, Vec3.atCenterOf(pos));
			return InteractionResult.PASS;
		});
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (player instanceof ServerPlayer sp && owns(sp)) {
				endShape(sp); // using a block gives the disguise away
			}
			reachVisual(player, hit.getLocation());
			return InteractionResult.PASS;
		});
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (player instanceof ServerPlayer sp && owns(sp)) {
				endShape(sp); // attacking breaks the disguise
			}
			reachVisual(player, mid(entity));
			return InteractionResult.PASS;
		});
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			reachVisual(player, mid(entity));
			return InteractionResult.PASS;
		});

		registerPassives();
	}

	private static void cooling(AbilityContext ctx, String timer) {
		ctx.actionBar("message.projecthero.ability.on_cooldown", Component.translatable(ctx.ability().nameKey()),
				String.format(java.util.Locale.ROOT, "%.0f", Math.ceil(res(ctx.player(), timer) / 20.0f)));
	}

	// ================================================================ upkeep (every tick for an owner)

	private static void upkeep(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		seed(p);
		for (String timer : new String[] { "arm_ticks", "hammer_cd", "stomp_cd", "shape_cd" }) {
			float v = res(p, timer);
			if (v > 0) {
				set(p, timer, v - 1);
			}
		}
		// Stretch refills while nothing held is running -- twice as fast standing still
		float s = res(p, "stretch");
		if (s < MAX_STRETCH && !channelling(p)) {
			Vec3 mv = p.getKnownMovement();
			boolean still = p.onGround() && mv.x * mv.x + mv.z * mv.z < 1.0e-4;
			s = Math.min(MAX_STRETCH, s + STRETCH_REGEN_PER_TICK * (still ? 2.0f : 1.0f));
			set(p, "stretch", s);
		}
		if (limp(p)) {
			float left = res(p, "limp") - 1f;
			set(p, "limp", Math.max(0f, left));
			if (left < 0.5f) {
				MutationVisuals.stopIf(p, "p17.limp");
				AbilityHelpers.sound(p, SoundEvents.SLIME_JUMP_SMALL, 1.0f, 1.3f);
			} else {
				MutationVisuals.ensure(p, "p17.limp");
			}
		}
		// coils (R / Z): build the bar, twist the limb
		float kind = res(p, "coil_kind");
		if (kind > 0.5f) {
			float f = coilFraction(p);
			set(p, "coil", f * 100.0f);
			MutationVisuals.ensure(p, "p17.windup");
			long held = p.level().getGameTime() - (long) res(p, "coil_start");
			if (held > 6 * 20) {
				// safety: the release was lost
				clearCoil(p);
				if (Math.round(kind) == 2) {
					hammer(p, 1.0f);
				} else {
					stretchPunch(p, 1.0f);
				}
			} else if (held % 4 == 0) {
				ctx.level().sendParticles(ParticleTypes.ITEM_SLIME, p.getX(), p.getY() + 1.2, p.getZ(), 2 + (int) (f * 8),
						0.35, 0.3, 0.35, 0.02);
			}
		}
		gatlingTick(p, ctx.level());
		if (p.tickCount % 100 == 37) {
			dropLegacyState(p);
		}
	}

	/** Pre-v0.16.5 Elastic Form / shield modifiers and effects are inert now: clear them out of old saves. */
	private static void dropLegacyState(ServerPlayer p) {
		for (ResourceLocation id : LEGACY) {
			PowerToggles.clearModifier(p, Attributes.ATTACK_DAMAGE, id);
			PowerToggles.clearModifier(p, Attributes.ENTITY_INTERACTION_RANGE, id);
			PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, id);
			PowerToggles.clearModifier(p, Attributes.SCALE, id);
			PowerToggles.clearModifier(p, Attributes.KNOCKBACK_RESISTANCE, id);
		}
		var jump = p.getEffect(MobEffects.JUMP);
		if (jump != null && jump.isInfiniteDuration()) {
			p.removeEffect(MobEffects.JUMP);
		}
		var slow = p.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
		if (slow != null && slow.isInfiniteDuration()) {
			p.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
		}
	}

	// ================================================================ R: Stretch Punch / Gatling Barrage

	private static void startCoil(ServerPlayer p, int kind) {
		set(p, "coil_kind", kind);
		set(p, "coil_start", p.level().getGameTime());
		set(p, "coil", 0);
		MutationVisuals.play(p, "p17.windup");
		AbilityHelpers.sound(p, SoundEvents.SLIME_SQUISH_SMALL, 0.9f, 0.7f);
	}

	private static void clearCoil(ServerPlayer p) {
		set(p, "coil_kind", 0);
		set(p, "coil_start", 0);
		set(p, "coil", 0);
		MutationVisuals.stopIf(p, "p17.windup");
	}

	/** Damage of a Stretch Punch coiled for {@code coil} (0..1) of the full 1.5 s. */
	public static float punchDamage(float coil) {
		return PUNCH_DAMAGE + (PUNCH_DAMAGE_COILED - PUNCH_DAMAGE) * coil;
	}

	/** The arm shoots out (up to 18 blocks, 22 coiled) and snaps back. */
	public static void stretchPunch(ServerPlayer p, float coil) {
		ServerLevel level = AbilityHelpers.level(p);
		double range = PUNCH_RANGE + (PUNCH_RANGE_COILED - PUNCH_RANGE) * coil;
		LivingEntity t = AbilityHelpers.raycastEntity(p, range);
		Vec3 end = t != null ? mid(t) : AbilityHelpers.aimPoint(p, range);
		spend(p, COST_PUNCH);
		MutationVisuals.play(p, "p17.stretch_right");
		stretchArm(p, end.distanceTo(p.getEyePosition()) - 0.3, ARM_STRIKE_TICKS, ARM_RIGHT);
		if (t != null) {
			AbilityHelpers.hurt(p, t, punchDamage(coil));
			AbilityHelpers.knockbackFrom(t, p.position(), 0.8 + 1.8 * coil);
			slime(level, mid(t), 14 + (int) (coil * 20), 0.3);
		} else {
			slime(level, end, 8, 0.2);
		}
		AbilityHelpers.sound(p, SoundEvents.SLIME_ATTACK, 1.0f, 1.3f - coil * 0.5f);
		if (coil > 0.95f) {
			AbilityHelpers.sound(p, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0f, 0.7f);
		}
	}

	private static void gatlingTick(ServerPlayer p, ServerLevel level) {
		if (res(p, "gatling") < 0.5f) {
			return;
		}
		if (!p.isAlive()) {
			set(p, "gatling", 0);
			return;
		}
		MutationVisuals.ensure(p, "p17.gatling");
		stretchArm(p, 6.0 + (p.tickCount % 3), 4, ARM_GATLING);
		float acc = res(p, "gatling_acc") + GATLING_HITS_PER_SECOND / 20.0f;
		if (acc >= 1.0f) {
			acc -= 1.0f;
			gatlingVolley(p, level);
		}
		set(p, "gatling_acc", acc);
		spend(p, COST_GATLING_PER_SECOND / 20.0f);
	}

	/** One punch of the barrage: everything hostile in the cone in front takes 3. Returns how many were hit. */
	public static int gatlingVolley(ServerPlayer p, ServerLevel level) {
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		int hit = 0;
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, eye.add(look.scale(GATLING_RANGE * 0.5)), GATLING_RANGE * 0.5 + 1.5)) {
			Vec3 to = mid(e).subtract(eye);
			double d = to.length();
			if (d > GATLING_RANGE + e.getBbWidth() || d < 1.0e-3 || to.scale(1.0 / d).dot(look) < GATLING_CONE) {
				continue;
			}
			// the barrage keeps its own 6-a-second count, so it runs past the hit-invulnerability window
			if (AbilityHelpers.hurtBurst(p, e, GATLING_DAMAGE)) {
				hit++;
				AbilityHelpers.knockbackFrom(e, p.position(), 0.12);
				slime(level, mid(e), 4, 0.25);
			}
		}
		Vec3 at = eye.add(look.scale(2.5)).add((p.getRandom().nextDouble() - 0.5) * 1.6, (p.getRandom().nextDouble() - 0.5) * 1.0,
				(p.getRandom().nextDouble() - 0.5) * 1.6);
		level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 1, 0.05, 0.05, 0.05, 0.01);
		AbilityHelpers.sound(p, SoundEvents.SLIME_ATTACK, 0.6f, 1.3f + p.getRandom().nextFloat() * 0.4f);
		return hit;
	}

	// ================================================================ G: Elastic Grab / Wrap & Squeeze

	private static boolean grabbable(ServerPlayer p, LivingEntity t) {
		return t != null && AbilityHelpers.isValidGrabTarget(t, p) && noBoss(t);
	}

	private static void grab(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		LivingEntity t = AbilityHelpers.raycastEntity(p, GRAB_RANGE);
		if (!grabbable(p, t)) {
			MutationVisuals.play(p, "p17.stretch_right");
			stretchArm(p, AbilityHelpers.aimPoint(p, GRAB_RANGE).distanceTo(p.getEyePosition()), 10, ARM_RIGHT);
			ctx.actionBar("message.projecthero.elasticity.no_target");
			return;
		}
		spend(p, COST_GRAB);
		set(p, "grab_id", t.getId());
		set(p, "grab_ticks", GRAB_HOLD_TICKS);
		stretchArm(p, t.distanceTo(p), 6, ARM_HOLD);
		MutationVisuals.play(p, "p17.grab_hold");
		AbilityHelpers.sound(p, SoundEvents.SLIME_ATTACK, 1.0f, 1.3f);
		ctx.actionBar("message.projecthero.ability.grabbed");
	}

	/** The creature on the end of the grab, or null (and the grab is cleared). */
	private static LivingEntity held(ServerPlayer p, ServerLevel level) {
		int id = (int) res(p, "grab_id");
		if (id != 0 && level.getEntity(id) instanceof LivingEntity le && le.isAlive() && le.distanceToSqr(p) < 30 * 30) {
			return le;
		}
		return null;
	}

	private static void clearGrab(ServerPlayer p) {
		set(p, "grab_id", 0);
		set(p, "grab_ticks", 0);
		MutationVisuals.stopIf(p, "p17.grab_hold");
	}

	/** Reels the held creature in to arm's length and keeps it there. */
	private static void grabTick(ServerPlayer p, ServerLevel level) {
		float left = res(p, "grab_ticks");
		if (left < 0.5f) {
			return;
		}
		LivingEntity le = held(p, level);
		if (le == null || left <= 1.0f) {
			clearGrab(p);
			return;
		}
		set(p, "grab_ticks", left - 1);
		Vec3 hold = p.getEyePosition().add(p.getLookAngle().scale(GRAB_HOLD_DISTANCE)).subtract(0, le.getBbHeight() * 0.5, 0);
		Vec3 now = le.position();
		Vec3 next = now.distanceToSqr(hold) < 0.04 ? hold : now.add(hold.subtract(now).scale(0.35));
		le.setPos(next.x, next.y, next.z);
		le.setDeltaMovement(Vec3.ZERO);
		le.fallDistance = 0;
		le.hurtMarked = true;
		stretchArm(p, Math.max(1.0, le.distanceTo(p) - 0.4), 6, ARM_HOLD);
		MutationVisuals.ensure(p, "p17.grab_hold");
	}

	/** Hurls the held creature where you look. 10 on landing, 16 if it hits a wall or another mob. */
	public static boolean hurl(ServerPlayer p) {
		ServerLevel level = AbilityHelpers.level(p);
		LivingEntity le = held(p, level);
		clearGrab(p);
		if (le == null) {
			return false;
		}
		le.setDeltaMovement(p.getLookAngle().scale(2.4).add(0, 0.3, 0));
		le.hurtMarked = true;
		le.hasImpulse = true;
		set(p, "thrown_id", le.getId());
		set(p, "thrown_ticks", HURL_FLIGHT_TICKS);
		MutationVisuals.play(p, "throw_right");
		stretchArm(p, 3.0, 8, ARM_RIGHT);
		AbilityHelpers.sound(p, SoundEvents.SLIME_JUMP, 1.0f, 0.8f);
		return true;
	}

	private static void hurlTick(ServerPlayer p, ServerLevel level) {
		float left = res(p, "thrown_ticks");
		if (left < 0.5f) {
			return;
		}
		set(p, "thrown_ticks", left - 1);
		int age = HURL_FLIGHT_TICKS - (int) left;
		Entity e = level.getEntity((int) res(p, "thrown_id"));
		if (!(e instanceof LivingEntity le) || !le.isAlive()) {
			set(p, "thrown_ticks", 0);
			return;
		}
		LivingEntity struck = null;
		for (LivingEntity o : level.getEntitiesOfClass(LivingEntity.class, le.getBoundingBox().inflate(0.35),
				o -> o != le && o != p && o.isAlive() && !(o instanceof net.minecraft.world.entity.decoration.ArmorStand))) {
			struck = o;
			break;
		}
		if (le.horizontalCollision || struck != null) {
			hurlLanded(p, le, true);
			if (struck != null) {
				AbilityHelpers.hurtBurst(p, struck, HURL_IMPACT_BONUS);
				AbilityHelpers.knockbackFrom(struck, le.position(), 0.8);
			}
		} else if ((age > 3 && le.onGround()) || left <= 1.0f) {
			hurlLanded(p, le, false);
		}
	}

	/** The thrown creature hit something (or the ground): the damage lands now. */
	public static void hurlLanded(ServerPlayer p, LivingEntity le, boolean impact) {
		set(p, "thrown_ticks", 0);
		set(p, "thrown_id", 0);
		AbilityHelpers.hurtBurst(p, le, HURL_DAMAGE + (impact ? HURL_IMPACT_BONUS : 0.0f));
		ServerLevel level = AbilityHelpers.level(p);
		slime(level, mid(le), impact ? 24 : 10, 0.4);
		if (impact) {
			level.playSound(null, le.getX(), le.getY(), le.getZ(), SoundEvents.SLIME_SQUISH, net.minecraft.sounds.SoundSource.PLAYERS, 1.3f, 0.6f);
		}
	}

	private static void wrap(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		LivingEntity t = held(p, level);
		if (t == null) {
			t = AbilityHelpers.raycastEntity(p, WRAP_RANGE);
		}
		if (!grabbable(p, t) || !HeroTargets.canHarm(p, t) || Squads.areAllies(p, t)) {
			ctx.actionBar("message.projecthero.elasticity.no_target");
			return;
		}
		clearGrab(p);
		spend(p, COST_WRAP);
		set(p, "wrap_id", t.getId());
		set(p, "wrap_ticks", WRAP_TICKS);
		ExperimentalPowers.setMarker(p, Powers.byKey(KEY), "wrap_at", t.blockPosition());
		MutationVisuals.play(p, "p17.wrap");
		stretchArm(p, Math.max(1.0, t.distanceTo(p) - 0.3), 6, ARM_HOLD_BOTH);
		if (t instanceof Mob m) {
			m.setTarget(null);
			m.getNavigation().stop();
		}
		AbilityHelpers.sound(p, SoundEvents.SLIME_SQUISH, 1.2f, 0.6f);
		ctx.actionBar("message.projecthero.elasticity.wrapped");
	}

	private static void wrapTick(ServerPlayer p, ServerLevel level) {
		float left = res(p, "wrap_ticks");
		if (left < 0.5f) {
			return;
		}
		Entity e = level.getEntity((int) res(p, "wrap_id"));
		if (!(e instanceof LivingEntity t) || !t.isAlive() || left <= 1.0f || t.distanceToSqr(p) > 12 * 12 || !p.isAlive()) {
			endWrap(p);
			return;
		}
		set(p, "wrap_ticks", left - 1);
		// held fast: no moving, no attacking (the attack half is in allowDamage)
		BlockPos pin = ExperimentalPowers.getMarker(p, Powers.byKey(KEY), "wrap_at");
		if (pin != null) {
			t.setPos(pin.getX() + 0.5, Math.min(t.getY(), pin.getY() + 0.5), pin.getZ() + 0.5);
		}
		t.setDeltaMovement(0, Math.min(0, t.getDeltaMovement().y), 0);
		t.hurtMarked = true;
		if (t instanceof Mob m) {
			m.setTarget(null);
			m.getNavigation().stop();
		}
		int age = WRAP_TICKS - (int) left + 1; // ticks held, this one included
		if (age % 20 == 0) {
			wrapSqueeze(p, t);
		}
		stretchArm(p, Math.max(1.0, t.distanceTo(p) - 0.3), 6, ARM_HOLD_BOTH);
		MutationVisuals.ensure(p, "p17.wrap");
	}

	/** One squeeze of the wrap: 4 damage (once a second). */
	public static void wrapSqueeze(ServerPlayer p, LivingEntity t) {
		AbilityHelpers.hurtBurst(p, t, WRAP_DAMAGE_PER_SECOND);
		slime(AbilityHelpers.level(p), mid(t), 8, 0.3);
		AbilityHelpers.sound(p, SoundEvents.SLIME_SQUISH_SMALL, 1.0f, 0.8f);
	}

	public static void endWrap(ServerPlayer p) {
		if (res(p, "wrap_ticks") < 0.5f && res(p, "wrap_id") < 0.5f) {
			return;
		}
		set(p, "wrap_ticks", 0);
		set(p, "wrap_id", 0);
		MutationVisuals.stopIf(p, "p17.wrap");
		AbilityHelpers.sound(p, SoundEvents.SLIME_JUMP_SMALL, 1.0f, 1.2f);
	}

	/** True while {@code e} is wrapped up by any elastic player (it cannot attack). */
	public static boolean isWrapped(Entity e) {
		if (!(e.level() instanceof ServerLevel sl)) {
			return false;
		}
		for (ServerPlayer p : sl.players()) {
			if (owns(p) && res(p, "wrap_ticks") > 0.5f && (int) res(p, "wrap_id") == e.getId()) {
				return true;
			}
		}
		return false;
	}

	// ================================================================ X: Slingshot / Rubber Ball

	private static void startSling(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		BlockHitResult hit = AbilityHelpers.raycastBlock(p, SLING_RANGE);
		if (hit.getType() != HitResult.Type.BLOCK) {
			ctx.actionBar("message.projecthero.elasticity.no_anchor");
			return;
		}
		Vec3 a = Vec3.atCenterOf(hit.getBlockPos());
		set(p, "sling", 1);
		set(p, "sling_start", p.level().getGameTime());
		// the anchor is locked (the "sling_at" marker below): the arms grip this block, wherever you look, until you let go
		set(p, "sling_fly", 0);
		ExperimentalPowers.setMarker(p, Powers.byKey(KEY), "sling_at", hit.getBlockPos());
		MutationVisuals.play(p, "p17.sling");
		stretchArm(p, a.distanceTo(p.getEyePosition()) - 0.6, 6, ARM_HOLD_BOTH);
		AbilityHelpers.sound(p, SoundEvents.SLIME_ATTACK, 1.0f, 0.9f);
	}

	private static Vec3 slingAnchor(ServerPlayer p) {
		BlockPos b = ExperimentalPowers.getMarker(p, Powers.byKey(KEY), "sling_at");
		return b == null ? p.getEyePosition().add(p.getLookAngle().scale(10)) : Vec3.atCenterOf(b);
	}

	private static void slingTick(ServerPlayer p, ServerLevel level) {
		if (res(p, "sling_fly") > 0.5f) {
			float t = res(p, "sling_fly_t") + 1f;
			set(p, "sling_fly_t", t);
			// down (past a short take-off grace), into a wall or water, or simply long enough: the flight is over
			if ((t > 4f && (p.onGround() || p.horizontalCollision)) || p.isInWater() || t > SLING_FLY_TICKS || !p.isAlive()) {
				set(p, "sling_fly", 0);
			} else {
				p.resetFallDistance();
			}
		}
		if (res(p, "sling") < 0.5f) {
			return;
		}
		Vec3 a = slingAnchor(p);
		long held = p.level().getGameTime() - (long) res(p, "sling_start");
		float pull = (float) Math.min(1.0, held / (double) SLING_PULL_TICKS);
		set(p, "coil", pull * 100.0f); // the pull-back shows on the coil bar
		MutationVisuals.ensure(p, "p17.sling");
		stretchArm(p, a.distanceTo(p.getEyePosition()) - 0.6, 6, ARM_HOLD_BOTH);
		if (held > 8 * 20) {
			releaseSling(p, level); // safety: the release was lost
		} else if (held % 3 == 0) {
			level.sendParticles(ParticleTypes.ITEM_SLIME, a.x, a.y, a.z, 2, 0.15, 0.15, 0.15, 0.02);
		}
	}

	/**
	 * Launch speed (blocks a tick) of a slingshot pulled back for {@code pull} (0..1) from an anchor {@code distance} blocks
	 * away. v0.16.5 (user): "make the player coil charge stronger so player flies further based on the coil charge and
	 * also ... based on how far the anchor block is": a full coil hits more than twice as hard as a tap, and a far anchor
	 * stores more stretch than a near one.
	 */
	public static double slingSpeed(float pull, double distance) {
		double coil = 1.3 + 2.7 * Math.max(0f, Math.min(1f, pull));
		double reach = 0.55 + Math.min(SLING_RANGE, distance) / 32.0;
		return Math.min(SLING_MAX_SPEED, coil * reach);
	}

	/** The locked anchor while a Slingshot is held (its centre), or null. */
	public static Vec3 slingAnchorOf(Player p) {
		if (!(p instanceof ServerPlayer sp) || !owns(p) || resAny(p, "sling") < 0.5f
				|| ExperimentalPowers.getMarker(sp, Powers.byKey(KEY), "sling_at") == null) {
			return null;
		}
		return slingAnchor(sp); // a block marker: resources are clamped at 0, so they cannot hold a negative coordinate
	}

	/** Resources cannot go below 0: the launch velocity is stored shifted up by this much. */
	private static final float SLING_V_SHIFT = 16.0f;

	/** In the no-drag flight after a Slingshot launch (the client keeps the launch speed until a landing). */
	public static boolean slingFlying(Player p) {
		return owns(p) && resAny(p, "sling_fly") > 0.5f;
	}

	private static void releaseSling(ServerPlayer p, ServerLevel level) {
		if (res(p, "sling") < 0.5f) {
			return;
		}
		long held = p.level().getGameTime() - (long) res(p, "sling_start");
		float pull = (float) Math.min(1.0, held / (double) SLING_PULL_TICKS);
		set(p, "sling", 0);
		set(p, "coil", 0);
		MutationVisuals.stopIf(p, "p17.sling");
		Vec3 dir = slingAnchor(p).subtract(p.position().add(0, 0.9, 0));
		if (dir.lengthSqr() < 1.0e-3) {
			return;
		}
		spend(p, COST_SLING);
		Vec3 launch = dir.normalize().scale(slingSpeed(pull, dir.length())).add(0, 0.25, 0);
		AbilityHelpers.launchSelf(p, launch);
		p.resetFallDistance();
		// v0.16.5 (user): "they should carry all that momentum till they land" -- the client holds this speed in the air
		set(p, "sling_fly", 1);
		set(p, "sling_fly_t", 0);
		set(p, "sling_vx", (float) launch.x + SLING_V_SHIFT);
		set(p, "sling_vz", (float) launch.z + SLING_V_SHIFT);
		MutationVisuals.play(p, "dash_forward");
		stretchArm(p, 1.5, 8, ARM_BOTH);
		slime(level, p.position().add(0, 1, 0), 16, 0.4);
		AbilityHelpers.sound(p, SoundEvents.SLIME_JUMP, 1.2f, 0.6f + pull * 0.4f);
	}

	private static void startBall(ServerPlayer p) {
		spend(p, COST_BALL);
		set(p, "ball", BALL_TICKS);
		endGlide(p);
		PowerToggles.modifier(p, Attributes.MOVEMENT_SPEED, BALL_SPEED, 0.9, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		PowerToggles.modifier(p, Attributes.SCALE, BALL_SCALE, -0.4, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		MutationVisuals.stop(p);
		slime(AbilityHelpers.level(p), p.position().add(0, 0.6, 0), 30, 0.5);
		AbilityHelpers.sound(p, SoundEvents.SLIME_JUMP, 1.2f, 0.5f);
	}

	private static void ballTick(ServerPlayer p, ServerLevel level) {
		float left = res(p, "ball");
		if (left < 0.5f) {
			return;
		}
		if (left <= 1.0f || !p.isAlive()) {
			endBall(p);
			return;
		}
		set(p, "ball", left - 1);
		p.resetFallDistance();
		reflectProjectiles(p, 1.8);
		ballBowl(p, level);
	}

	/** Bowls through whatever the ball rolls into: 6 each (the hit-invulnerability window keeps it to one per pass). */
	public static int ballBowl(ServerPlayer p, ServerLevel level) {
		Vec3 mv = p.getKnownMovement();
		Vec3 dir = new Vec3(mv.x, 0, mv.z);
		dir = dir.lengthSqr() < 1.0e-4 ? p.getLookAngle().multiply(1, 0, 1) : dir;
		dir = dir.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : dir.normalize();
		int hit = 0;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(0.6),
				e -> e != p && e.isAlive() && HeroTargets.canHarm(p, e) && !Squads.areAllies(p, e))) {
			if (AbilityHelpers.hurt(p, e, BALL_HIT_DAMAGE)) {
				hit++;
				e.setDeltaMovement(e.getDeltaMovement().add(dir.scale(1.1)).add(0, 0.45, 0));
				e.hurtMarked = true;
				slime(level, mid(e), 10, 0.3);
				AbilityHelpers.sound(p, SoundEvents.SLIME_SQUISH, 1.0f, 1.2f);
			}
		}
		return hit;
	}

	public static void endBall(ServerPlayer p) {
		boolean was = res(p, "ball") > 0.5f;
		set(p, "ball", 0);
		PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, BALL_SPEED);
		PowerToggles.clearModifier(p, Attributes.SCALE, BALL_SCALE);
		if (was) {
			slime(AbilityHelpers.level(p), p.position().add(0, 0.6, 0), 20, 0.4);
			AbilityHelpers.sound(p, SoundEvents.SLIME_SQUISH, 1.0f, 1.4f);
		}
	}

	// ================================================================ Z: Giant Hammer Fist / Spring Stomp

	/** Damage of a Hammer Fist coiled for {@code coil} (0..1). */
	public static float hammerDamage(float coil) {
		return HAMMER_DAMAGE_MIN + (HAMMER_DAMAGE_MAX - HAMMER_DAMAGE_MIN) * coil;
	}

	/** The fist swells to a boulder and slams the aim point (12 blocks): 20-34 in a 4.5-block area, plus Slowness. */
	public static int hammer(ServerPlayer p, float coil) {
		ServerLevel level = AbilityHelpers.level(p);
		Vec3 at = AbilityHelpers.aimPoint(p, HAMMER_RANGE);
		spend(p, COST_HAMMER);
		set(p, "hammer_cd", HAMMER_CD);
		set(p, "hammer_size", 1.0f + coil); // the client swells the fist bigger the longer the coil
		MutationVisuals.play(p, "p17.hammer");
		stretchArm(p, at.distanceTo(p.getEyePosition()) - 0.6, 18, ARM_HAMMER);
		int hit = 0;
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, at, HAMMER_RADIUS)) {
			if (AbilityHelpers.hurt(p, e, hammerDamage(coil))) {
				hit++;
			}
			AbilityHelpers.knockbackFrom(e, at, 1.2 + coil);
			AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, 50, 2);
		}
		level.sendParticles(ParticleTypes.ITEM_SLIME, at.x, at.y, at.z, 70, 2.2, 0.4, 2.2, 0.2);
		level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0, 0, 0, 0);
		BlockState under = level.getBlockState(BlockPos.containing(at).below());
		if (!under.isAir()) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, under), at.x, at.y + 0.1, at.z, 40, 1.8, 0.1, 1.8, 0.15);
		}
		AbilityHelpers.sound(p, SoundEvents.SLIME_SQUISH, 1.4f, 0.4f);
		AbilityHelpers.sound(p, SoundEvents.GENERIC_EXPLODE, 0.6f, 1.4f);
		return hit;
	}

	private static void startStomp(ServerPlayer p) {
		spend(p, COST_STOMP);
		set(p, "stomp_cd", STOMP_CD);
		set(p, "stomp", 1);
		set(p, "stomp_t", 0);
		endGlide(p);
		AbilityHelpers.launchSelf(p, new Vec3(0, 1.25, 0));
		MutationVisuals.play(p, "p17.stilts");
		AbilityHelpers.sound(p, SoundEvents.SLIME_JUMP, 1.3f, 0.5f);
	}

	private static void stompTick(ServerPlayer p, ServerLevel level) {
		int phase = Math.round(res(p, "stomp"));
		if (phase == 0) {
			return;
		}
		float t = res(p, "stomp_t") + 1;
		set(p, "stomp_t", t);
		p.resetFallDistance();
		if (!p.isAlive() || t > 80) {
			set(p, "stomp", 0);
			return;
		}
		if (phase == 1) {
			MutationVisuals.ensure(p, "p17.stilts");
			if (t >= STOMP_RISE_TICKS) {
				set(p, "stomp", 2);
				AbilityHelpers.launchSelf(p, new Vec3(0, -2.8, 0));
				MutationVisuals.play(p, "slam_two_hand");
			}
		} else if (p.onGround() && t > STOMP_RISE_TICKS + 1) {
			stompImpact(p);
		}
	}

	/** The landing: 20 in a 5-block ring, launching mobs. Returns how many were hit. */
	public static int stompImpact(ServerPlayer p) {
		ServerLevel level = AbilityHelpers.level(p);
		set(p, "stomp", 0);
		int hit = 0;
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position(), STOMP_RADIUS)) {
			if (AbilityHelpers.hurt(p, e, STOMP_DAMAGE)) {
				hit++;
			}
			Vec3 away = e.position().subtract(p.position()).multiply(1, 0, 1);
			away = away.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 0) : away.normalize().scale(0.7);
			e.setDeltaMovement(away.x, 1.0, away.z);
			e.hurtMarked = true;
			e.hasImpulse = true;
		}
		MutationVisuals.play(p, "hero_landing");
		BatchCFx.flatRing(level, p.position().add(0, 0.2, 0), STOMP_RADIUS, 40, ParticleTypes.ITEM_SLIME, 0.15);
		level.sendParticles(ParticleTypes.EXPLOSION, p.getX(), p.getY() + 0.2, p.getZ(), 1, 0, 0, 0, 0);
		AbilityHelpers.sound(p, SoundEvents.SLIME_SQUISH, 1.5f, 0.4f);
		AbilityHelpers.sound(p, SoundEvents.GENERIC_EXPLODE, 0.5f, 1.5f);
		return hit;
	}

	// ================================================================ V: Rubber Shield / Parachute

	private static void startShield(ServerPlayer p) {
		set(p, "shielding", 1);
		PowerToggles.modifier(p, Attributes.MOVEMENT_SPEED, SHIELD_SPEED, -0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		PowerToggles.modifier(p, Attributes.KNOCKBACK_RESISTANCE, SHIELD_KB, 1.0, AttributeModifier.Operation.ADD_VALUE);
		MutationVisuals.play(p, "p17.inflate");
		slime(AbilityHelpers.level(p), p.position().add(0, 1, 0), 30, 0.6);
		AbilityHelpers.sound(p, SoundEvents.SLIME_JUMP, 1.2f, 0.45f);
	}

	private static void shieldTick(ServerPlayer p) {
		if (res(p, "shielding") < 0.5f) {
			return;
		}
		if (!p.isAlive()) {
			endShield(p);
			return;
		}
		MutationVisuals.ensure(p, "p17.inflate");
		p.resetFallDistance();
		reflectProjectiles(p, 3.5);
		spend(p, COST_SHIELD_PER_SECOND / 20.0f);
	}

	public static void endShield(ServerPlayer p) {
		PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, SHIELD_SPEED);
		PowerToggles.clearModifier(p, Attributes.KNOCKBACK_RESISTANCE, SHIELD_KB);
		if (res(p, "shielding") < 0.5f) {
			return;
		}
		set(p, "shielding", 0);
		MutationVisuals.stopIf(p, "p17.inflate");
		AbilityHelpers.sound(p, SoundEvents.SLIME_SQUISH_SMALL, 1.0f, 1.3f);
	}

	/** Every enemy projectile heading into you within {@code radius} bounces back -- at its shooter when there is one. */
	public static int reflectProjectiles(ServerPlayer p, double radius) {
		int bounced = 0;
		Vec3 centre = p.position().add(0, p.getBbHeight() * 0.5, 0);
		for (Projectile pr : p.level().getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(radius),
				pr -> pr.isAlive() && pr.getOwner() != p)) {
			Vec3 v = pr.getDeltaMovement();
			Vec3 to = centre.subtract(pr.position());
			if (v.lengthSqr() < 1.0e-3 || v.dot(to) <= 0.0) {
				continue;
			}
			bounceBack(p, pr);
			bounced++;
		}
		if (bounced > 0) {
			AbilityHelpers.sound(p, SoundEvents.SLIME_JUMP_SMALL, 1.2f, 1.6f);
		}
		return bounced;
	}

	private static void bounceBack(ServerPlayer p, Projectile pr) {
		Vec3 v = pr.getDeltaMovement();
		Entity owner = pr.getOwner();
		double speed = Math.max(0.9, v.length() * 1.15);
		Vec3 out;
		if (owner instanceof LivingEntity le && le.isAlive() && le.distanceToSqr(p) < 48.0 * 48.0) {
			out = le.getEyePosition().subtract(pr.position()).normalize().scale(speed);
		} else {
			out = v.lengthSqr() < 1.0e-4 ? p.getLookAngle().scale(speed) : v.normalize().reverse().scale(speed);
		}
		pr.setDeltaMovement(out);
		pr.setOwner(p);
		pr.hasImpulse = true;
		pr.hurtMarked = true;
		AbilityHelpers.level(p).sendParticles(ParticleTypes.ITEM_SLIME, pr.getX(), pr.getY(), pr.getZ(), 8, 0.15, 0.15, 0.15, 0.1);
	}

	/** Flings a melee attacker away off the balloon. */
	public static void bounceAttacker(ServerPlayer sp, LivingEntity attacker) {
		Vec3 away = attacker.position().subtract(sp.position());
		away = away.lengthSqr() < 1.0e-4 ? sp.getLookAngle().reverse() : away.normalize();
		attacker.setDeltaMovement(away.scale(3.0).add(0, 0.6, 0));
		attacker.hurtMarked = true;
		attacker.hasImpulse = true;
		AbilityHelpers.sound(sp, SoundEvents.SLIME_SQUISH, 1.2f, 0.6f);
		AbilityHelpers.level(sp).sendParticles(ParticleTypes.ITEM_SLIME, attacker.getX(), attacker.getY() + 1.0, attacker.getZ(),
				12, 0.3, 0.4, 0.3, 0.1);
	}

	private static void startGlide(ServerPlayer p) {
		if (p.onGround()) {
			AbilityHelpers.launchSelf(p, new Vec3(p.getDeltaMovement().x, 0.85, p.getDeltaMovement().z));
		}
		set(p, "glide", 1);
		set(p, "glide_t", 0);
		if (!p.onGround() && !p.isInWater()) {
			p.startFallFlying(); // in the air: the elytra flight starts at once
		}
		MutationVisuals.play(p, "p17.glide");
		slime(AbilityHelpers.level(p), p.position().add(0, 1, 0), 20, 0.6);
		AbilityHelpers.sound(p, SoundEvents.SLIME_JUMP, 1.0f, 0.7f);
	}

	private static void glideTick(ServerPlayer p, ServerLevel level) {
		if (res(p, "glide") < 0.5f) {
			return;
		}
		float t = res(p, "glide_t") + 1;
		set(p, "glide_t", Math.min(t, 1000));
		if (((p.onGround() || p.isInWater()) && t > 8) || !p.isAlive() || p.getAbilities().flying) {
			endGlide(p);
			return;
		}
		p.resetFallDistance();
		// v0.16.5: elytra flight (the canopy is a custom elytra) -- hopped up from the ground, it opens once airborne
		if (!p.isFallFlying() && !p.onGround() && !p.isInWater() && t > 2) {
			p.startFallFlying();
		}
		MutationVisuals.ensure(p, "p17.glide");
		if (p.tickCount % 3 == 0) {
			level.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 1.2, p.getZ(), 1, 0.6, 0.05, 0.6, 0.0);
		}
	}

	public static void endGlide(ServerPlayer p) {
		if (res(p, "glide") < 0.5f) {
			return;
		}
		set(p, "glide", 0);
		MutationVisuals.stopIf(p, "p17.glide");
		if (p.isFallFlying()) {
			p.stopFallFlying();
		}
		p.resetFallDistance();
	}

	// ================================================================ C: Long Reach / Shape Shift

	public static void setReach(ServerPlayer p, boolean on) {
		set(p, "reach", on ? 1 : 0);
		if (on) {
			// the passive already adds +3 to the 3-block melee reach: +6 more makes 12; blocks go 4.5 -> 12
			PowerToggles.modifier(p, Attributes.ENTITY_INTERACTION_RANGE, LONG_REACH_ENTITY, LONG_REACH - 3.0 - EXTRA_MELEE_REACH,
					AttributeModifier.Operation.ADD_VALUE);
			PowerToggles.modifier(p, Attributes.BLOCK_INTERACTION_RANGE, LONG_REACH_BLOCK, LONG_REACH - 4.5,
					AttributeModifier.Operation.ADD_VALUE);
		} else {
			PowerToggles.clearModifier(p, Attributes.ENTITY_INTERACTION_RANGE, LONG_REACH_ENTITY);
			PowerToggles.clearModifier(p, Attributes.BLOCK_INTERACTION_RANGE, LONG_REACH_BLOCK);
		}
	}

	private static void reachTick(ServerPlayer p, ServerLevel level) {
		if (res(p, "reach") < 0.5f || p.tickCount % 2 != 0 || p.isSpectator()) {
			return;
		}
		// pick up items from 12 blocks away: the hand reaches out and drags them in
		ItemEntity farthest = null;
		double far = 0;
		for (ItemEntity it : level.getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(LONG_REACH),
				it -> it.isAlive() && !it.hasPickUpDelay())) {
			double d = it.distanceTo(p);
			if (d < 1.2 || d > LONG_REACH) {
				continue;
			}
			Vec3 pull = p.position().add(0, 0.6, 0).subtract(it.position()).normalize().scale(Math.min(0.9, 0.25 + d * 0.06));
			it.setDeltaMovement(pull);
			it.hasImpulse = true;
			if (d > far) {
				far = d;
				farthest = it;
			}
		}
		if (farthest != null && p.tickCount % 10 == 0 && res(p, "arm_ticks") < 0.5f) {
			stretchArm(p, far, ARM_STRIKE_TICKS, ARM_RIGHT);
		}
	}

	/** Long Reach: whatever you hit or use past normal reach, the arm visibly stretches out to it. */
	private static void reachVisual(Player player, Vec3 at) {
		if (!(player instanceof ServerPlayer sp) || !owns(sp) || res(sp, "reach") < 0.5f) {
			return;
		}
		double d = at.distanceTo(sp.getEyePosition());
		if (d > 4.0 && res(sp, "arm_ticks") < 4.5f) {
			stretchArm(sp, d - 0.4, 10, ARM_RIGHT);
		}
	}

	/** The block a Shape Shift turns into: the one you are looking at, else a barrel. */
	private static BlockState shapeFor(ServerPlayer p) {
		BlockHitResult hit = AbilityHelpers.raycastBlock(p, LONG_REACH);
		if (hit.getType() == HitResult.Type.BLOCK) {
			BlockState s = p.level().getBlockState(hit.getBlockPos());
			if (!s.isAir() && s.getRenderShape() != RenderShape.INVISIBLE && s.getFluidState().isEmpty()) {
				return s;
			}
		}
		return Blocks.BARREL.defaultBlockState();
	}

	private static void shapeShift(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		if (!p.onGround()) {
			ctx.actionBar("message.projecthero.elasticity.shape_ground");
			return;
		}
		BlockState s = shapeFor(p);
		spend(p, COST_SHAPE);
		set(p, "shape_cd", SHAPE_CD);
		endGlide(p);
		endBall(p);
		// squash down onto the middle of the block you stand on
		BlockPos at = p.blockPosition();
		double x = at.getX() + 0.5;
		double z = at.getZ() + 0.5;
		p.connection.teleport(x, p.getY(), z, p.getYRot(), p.getXRot());
		set(p, "shape", Block.getId(s) + 1);
		ExperimentalPowers.setMarker(p, Powers.byKey(KEY), "shape_at", at);
		MutationVisuals.stop(p);
		dropAggro(p, (ServerLevel) p.level());
		slime(ctx.level(), p.position().add(0, 0.6, 0), 24, 0.4);
		AbilityHelpers.sound(p, SoundEvents.SLIME_SQUISH, 1.0f, 1.6f);
		ctx.actionBar("message.projecthero.elasticity.shape_on");
	}

	/**
	 * v0.16.5 (user: "let player move during shape shift but end it if they take damage or start sprinting or m1 or attack
	 * or interact with a block"): the block can creep about; it gives itself away by sprinting, by a swing of the arm (a
	 * left click -- the server hears every swing -- or using something), an attack, a block used, or a hit taken.
	 */
	private static void shapeTick(ServerPlayer p, ServerLevel level) {
		if (res(p, "shape") < 0.5f) {
			return;
		}
		if (!p.isAlive() || p.isSprinting() || p.swinging) {
			endShape(p);
			return;
		}
		if (p.tickCount % 5 == 0) {
			dropAggro(p, level);
		}
	}

	/** Every mob after you loses you. */
	private static void dropAggro(ServerPlayer p, ServerLevel level) {
		for (Mob m : level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(48.0), m -> m.getTarget() == p)) {
			m.setTarget(null);
			m.getNavigation().stop();
		}
	}

	public static void endShape(ServerPlayer p) {
		if (res(p, "shape") < 0.5f) {
			return;
		}
		set(p, "shape", 0);
		slime(AbilityHelpers.level(p), p.position().add(0, 0.6, 0), 16, 0.4);
		AbilityHelpers.sound(p, SoundEvents.SLIME_SQUISH_SMALL, 1.0f, 1.4f);
	}

	/** Whether a mob may target {@code target}: never a Shape-Shifted elastic hero. */
	public static boolean hiddenFromMobs(Entity target) {
		return target instanceof Player pl && !pl.level().isClientSide() && shapeShifted(pl);
	}

	// ================================================================ damage

	/** The attacker of a melee hit (a mob or player striking in person), else null. */
	private static Entity meleeAttacker(ServerPlayer p, DamageSource source) {
		Entity atk = source.getEntity();
		if (atk instanceof LivingEntity && source.getDirectEntity() == atk && !source.is(DamageTypeTags.IS_PROJECTILE)
				&& atk.distanceToSqr(p) < 100.0) {
			return atk;
		}
		return null;
	}

	/** Wrapped creatures cannot attack; the balloon swallows a hit whole and still flings the attacker. */
	private static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (amount > 0f && entity instanceof ServerPlayer hurt && owns(hurt) && shapeShifted(hurt)) {
			endShape(hurt); // a hit knocks the disguise off
		}
		Entity atk = source.getEntity();
		if (atk != null && source.getDirectEntity() == atk && isWrapped(atk)) {
			return false;
		}
		if (entity instanceof ServerPlayer sp && shielding(sp) && meleeAttacker(sp, source) instanceof LivingEntity attacker
				&& amount * SHIELD_DAMAGE_FACTOR * BLUNT_FACTOR < 0.5f) {
			bounceAttacker(sp, attacker);
		}
		return true;
	}

	/**
	 * The rubber body's damage rule, read by {@link com.projecthero.mod.hero.power.HeroDamageRules}: a negative result
	 * means "immune" (falls; projectiles off the balloon or the ball), otherwise a multiplier -- 0.75 for blunt hits
	 * (melee, explosions), 1.25 for fire and lava, and the balloon's 0.4 on top.
	 */
	public static float damageFactor(ServerPlayer p, DamageSource source) {
		if (source.is(DamageTypeTags.IS_FALL)) {
			p.resetFallDistance();
			return -1.0f;
		}
		boolean shield = shielding(p);
		if (source.is(DamageTypeTags.IS_PROJECTILE) && (shield || balled(p))) {
			if (source.getDirectEntity() instanceof Projectile pr && pr.getOwner() != p) {
				bounceBack(p, pr);
			}
			return -1.0f;
		}
		float f = 1.0f;
		if (source.is(DamageTypeTags.IS_FIRE)) {
			f *= FIRE_FACTOR;
		} else if (source.is(DamageTypeTags.IS_EXPLOSION) || source.is(DamageTypes.MOB_ATTACK)
				|| source.is(DamageTypes.MOB_ATTACK_NO_AGGRO) || source.is(DamageTypes.PLAYER_ATTACK)) {
			f *= BLUNT_FACTOR;
		}
		if (shield) {
			f *= SHIELD_DAMAGE_FACTOR;
		}
		return f;
	}

	// ================================================================ passives

	private static void registerPassives() {
		com.projecthero.mod.hero.PowerPassives.register(KEY, (player, owned) -> {
			if (!owned) {
				PowerToggles.clearModifier(player, Attributes.KNOCKBACK_RESISTANCE, PASSIVE_KB);
				PowerToggles.clearModifier(player, Attributes.ENTITY_INTERACTION_RANGE, PASSIVE_REACH);
				Power power = Powers.byKey(KEY);
				if (power != null && ExperimentalPowers.owns(player, power)) {
					// still owned while forget() runs its teardown: drop the transient states too
					endChannels(player);
					endBall(player);
					endGlide(player);
					endShape(player);
					endWrap(player);
					clearGrab(player);
					setReach(player, false);
					set(player, "stomp", 0);
					set(player, "arm_ticks", 0);
				} else {
					PowerToggles.clearModifier(player, Attributes.MOVEMENT_SPEED, BALL_SPEED);
					PowerToggles.clearModifier(player, Attributes.SCALE, BALL_SCALE);
					PowerToggles.clearModifier(player, Attributes.MOVEMENT_SPEED, SHIELD_SPEED);
					PowerToggles.clearModifier(player, Attributes.KNOCKBACK_RESISTANCE, SHIELD_KB);
					PowerToggles.clearModifier(player, Attributes.ENTITY_INTERACTION_RANGE, LONG_REACH_ENTITY);
					PowerToggles.clearModifier(player, Attributes.BLOCK_INTERACTION_RANGE, LONG_REACH_BLOCK);
				}
				MutationVisuals.stop(player);
			}
		});
		com.projecthero.mod.hero.PowerPassives.registerTick(KEY, player -> {
			// 50% knockback resistance and +3 melee reach, always on for the rubber body
			PowerToggles.modifier(player, Attributes.KNOCKBACK_RESISTANCE, PASSIVE_KB, 0.5, AttributeModifier.Operation.ADD_VALUE);
			PowerToggles.modifier(player, Attributes.ENTITY_INTERACTION_RANGE, PASSIVE_REACH, EXTRA_MELEE_REACH,
					AttributeModifier.Operation.ADD_VALUE);
			// respawn-safe re-apply of the toggles
			if (res(player, "reach") > 0.5f) {
				setReach(player, true);
			}
			if (res(player, "ball") < 0.5f) {
				PowerToggles.clearModifier(player, Attributes.MOVEMENT_SPEED, BALL_SPEED);
				PowerToggles.clearModifier(player, Attributes.SCALE, BALL_SCALE);
			}
		});
	}

	// ================================================================ synced visuals + HUD (called from RevampBatchE)

	public static void registerVisuals() {
		MutationVisuals.registerFlag("p17.arm", ElasticityHandlers::armStretched);
		MutationVisuals.registerValue("p17.arm_len", ElasticityHandlers::armLength);
		MutationVisuals.registerValue("p17.arm_t", ElasticityHandlers::armStart);
		MutationVisuals.registerValue("p17.arm_mode", ElasticityHandlers::armMode);
		MutationVisuals.registerValue("p17.hammer_size", p -> armStretched(p) ? resAny(p, "hammer_size") : 0.0);
		MutationVisuals.registerValue("p17.coil", p -> coil(p) / 100.0);
		MutationVisuals.registerValue("p17.coil_kind", p -> owns(p) ? resAny(p, "coil_kind") : 0.0);
		MutationVisuals.registerFlag("p17.coiling", p -> owns(p) && resAny(p, "coil_kind") > 0.5f);
		MutationVisuals.registerFlag("p17.balloon", ElasticityHandlers::shielding);
		MutationVisuals.registerFlag("p17.canopy", ElasticityHandlers::gliding);
		MutationVisuals.registerFlag("p17.ball", ElasticityHandlers::balled);
		MutationVisuals.registerFlag("p17.gatling", ElasticityHandlers::gatling);
		MutationVisuals.registerFlag("p17.stilts", ElasticityHandlers::onStilts);
		MutationVisuals.registerFlag("p17.limp", ElasticityHandlers::limp);
		MutationVisuals.registerValue("p17.shape", ElasticityHandlers::shapeValue);
		MutationVisuals.registerValue("p17.wrap", ElasticityHandlers::wrapId);
		// v0.16.5 Slingshot: the locked anchor (the arms grip it) and the no-drag flight
		MutationVisuals.registerValue("p17.sling_ax", p -> slingAnchorOf(p) == null ? 0.0 : slingAnchorOf(p).x);
		MutationVisuals.registerValue("p17.sling_ay", p -> slingAnchorOf(p) == null ? 0.0 : slingAnchorOf(p).y);
		MutationVisuals.registerValue("p17.sling_az", p -> slingAnchorOf(p) == null ? 0.0 : slingAnchorOf(p).z);
		MutationVisuals.registerFlag("p17.sling_anchor", p -> slingAnchorOf(p) != null);
		MutationVisuals.registerFlag("p17.sling_fly", ElasticityHandlers::slingFlying);
		MutationVisuals.registerValue("p17.sling_vx", p -> slingFlying(p) ? resAny(p, "sling_vx") - SLING_V_SHIFT : 0.0);
		MutationVisuals.registerValue("p17.sling_vz", p -> slingFlying(p) ? resAny(p, "sling_vz") - SLING_V_SHIFT : 0.0);
	}

	public static void registerMeters() {
		String k = KEY;
		int text = 0xFFFFD2A8;
		com.projecthero.mod.hero.visual.MutationMeters.register(new com.projecthero.mod.hero.visual.MutationMeters.Spec(k, "stretch",
				com.projecthero.mod.hero.visual.MutationMeters.Kind.RESERVE, com.projecthero.mod.hero.visual.MutationMeters.Style.HAIRLINE,
				"Stretch", MAX_STRETCH, 0xFFFF9A3C, true, true, false, text));
		com.projecthero.mod.hero.visual.MutationMeters.register(new com.projecthero.mod.hero.visual.MutationMeters.Spec(k, "limp",
				com.projecthero.mod.hero.visual.MutationMeters.Kind.TIMER, com.projecthero.mod.hero.visual.MutationMeters.Style.HAIRLINE,
				"LIMP", LIMP_TICKS, 0xFF8A8A8A, false, false, false, text));
		com.projecthero.mod.hero.visual.MutationMeters.register(new com.projecthero.mod.hero.visual.MutationMeters.Spec(k, "coil",
				com.projecthero.mod.hero.visual.MutationMeters.Kind.BUILD, com.projecthero.mod.hero.visual.MutationMeters.Style.HAIRLINE,
				"Coil", 100, 0xFFFFC27A, false, true, true, text));
		com.projecthero.mod.hero.visual.MutationMeters.register(new com.projecthero.mod.hero.visual.MutationMeters.Spec(k, "hammer_cd",
				com.projecthero.mod.hero.visual.MutationMeters.Kind.TIMER, com.projecthero.mod.hero.visual.MutationMeters.Style.HAIRLINE,
				"Hammer Fist", HAMMER_CD, 0xFF9A7A5A, false, false, true, text));
		com.projecthero.mod.hero.visual.MutationMeters.register(new com.projecthero.mod.hero.visual.MutationMeters.Spec(k, "stomp_cd",
				com.projecthero.mod.hero.visual.MutationMeters.Kind.TIMER, com.projecthero.mod.hero.visual.MutationMeters.Style.HAIRLINE,
				"Spring Stomp", STOMP_CD, 0xFF9A7A5A, false, false, true, text));
		com.projecthero.mod.hero.visual.MutationMeters.register(new com.projecthero.mod.hero.visual.MutationMeters.Spec(k, "shape_cd",
				com.projecthero.mod.hero.visual.MutationMeters.Kind.TIMER, com.projecthero.mod.hero.visual.MutationMeters.Style.HAIRLINE,
				"Shape Shift", SHAPE_CD, 0xFF9A7A5A, false, false, true, text));
		com.projecthero.mod.hero.visual.MutationMeters.register(new com.projecthero.mod.hero.visual.MutationMeters.Spec(k, "ball",
				com.projecthero.mod.hero.visual.MutationMeters.Kind.TIMER, com.projecthero.mod.hero.visual.MutationMeters.Style.HAIRLINE,
				"Rubber Ball", BALL_TICKS, 0xFFFF9A3C, false, false, true, text));
		com.projecthero.mod.hero.visual.MutationMeters.register(new com.projecthero.mod.hero.visual.MutationMeters.Spec(k, "wrap_ticks",
				com.projecthero.mod.hero.visual.MutationMeters.Kind.TIMER, com.projecthero.mod.hero.visual.MutationMeters.Style.HAIRLINE,
				"Wrap & Squeeze", WRAP_TICKS, 0xFFFF9A3C, false, false, true, text));
	}

	/** For the gametests: the Stretch meter set directly. */
	public static void setStretch(ServerPlayer p, float value) {
		seed(p);
		set(p, "stretch", Math.max(0, Math.min(MAX_STRETCH, value)));
	}
}
