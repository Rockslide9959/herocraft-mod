package com.projecthero.mod.power;

import org.joml.Vector3f;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.attachment.CooldownState;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.item.ModItems;
import com.projecthero.mod.titanshifter.TitanCombat;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.7 Cosmic revamp (user-approved design): Thor's key routing, numbers and shared rules across his four weapon states
 * ({@link ThorState}). The moves themselves live in {@link ThorUnarmedKit}, {@link ThorMjolnirKit},
 * {@link ThorStormbreakerKit}, {@link ThorTwinKit} and {@link ThorUltimates}; the timed parts of them run on
 * {@link ThorSequences}.
 *
 * <p><b>Tier: Cosmic</b> ({@code PowerRank.COSMIC}) -- melee 21-28, abilities 22-30, ultimates 45-60 per hit. No single hit
 * goes over {@link #MAX_HIT} 60, whatever stacks on it (the storm buff and Sunder are capped in {@link ThorStorm#enter});
 * bosses keep their own per-hit caps. Every hit goes through {@link #hit}: never the caster, his pets, squad allies or their
 * pets ({@link WeaponCombo#friendly} / {@link ThorTargets#canAffect}).
 *
 * <p><b>State rules:</b> Unarmed cooldowns x0.75, Twin Gods Storm Energy x2 ({@link ThorState}); Mjolnir lightning stuns
 * briefly; Stormbreaker ability hits ignore armour and do x{@link #FINISH_MUL} on a foe under 30% health.
 *
 * <p><b>Ultimates</b> share one {@link ThorAbility#ULTIMATE} cooldown ({@link #ULT_CD} 120 s; 90 s unarmed): one box on the
 * HUD, and swapping weapons can never chain four ultimates back to back.
 */
public final class ThorKit {
	// ---------------------------------------------------------------- tier
	public static final float MAX_HIT = com.projecthero.mod.hero.PowerRank.COSMIC.ultimateMax;
	/** Stormbreaker: "finishes weakened enemies" -- x1.25 on a foe under {@link #EXECUTE_THRESHOLD} of its health. */
	public static final float FINISH_MUL = 1.25f;
	public static final float EXECUTE_THRESHOLD = 0.30f;
	/** Unarmed melee swings at this rate (attacks a second) -- the fastest of the four states. */
	public static final double UNARMED_ATTACK_SPEED = 1.8;

	// ---------------------------------------------------------------- Unarmed: Warrior of Asgard
	public static final float FIST_MIN = 24f, FIST_MAX = 30f;
	public static final int FIST_CHARGE_TICKS = 30, FIST_CD = 160;
	public static final float FIST_COST = 15f;
	public static final float PALM_DAMAGE = 22f;
	public static final double PALM_RANGE = 12.0;
	public static final int PALM_CD = 80;
	public static final float PALM_COST = 12f;
	public static final float GRAPPLE_DAMAGE = 24f;
	public static final int GRAPPLE_CD = 240;
	public static final float GRAPPLE_COST = 18f;
	public static final float RUSH_DAMAGE = 22f;
	public static final int RUSH_CD = 200;
	public static final float RUSH_COST = 20f;
	public static final float BURST_DAMAGE = 22f;
	public static final double BURST_RADIUS = 5.0;
	public static final int BURST_CD = 180;
	public static final float BURST_COST = 18f;
	public static final float POUND_DAMAGE = 26f;
	public static final double POUND_RADIUS = 5.0;
	public static final int POUND_CD = 200;
	public static final float POUND_COST = 20f;
	public static final int SPRINT_TICKS = 80, SPRINT_CD = 160;
	public static final float SPRINT_COST = 10f;
	public static final float LEAP_DAMAGE = 28f;
	public static final double LEAP_RADIUS = 5.0;
	public static final int LEAP_CD = 240;
	public static final float LEAP_COST = 20f;
	/** Lightning Incarnate: 10 s as lightning (aura every {@link #INCARNATE_PULSE} ticks), then the explosion. */
	public static final int INCARNATE_TICKS = 200, INCARNATE_PULSE = 20;
	public static final float INCARNATE_AURA = 22f, INCARNATE_BURST = 60f;
	public static final double INCARNATE_AURA_RADIUS = 3.5, INCARNATE_BURST_RADIUS = 10.0;

	// ---------------------------------------------------------------- Mjolnir: Storm Caller
	public static final float WHIRLWIND_DAMAGE = 24f;
	public static final int WHIRLWIND_PIERCE = 6, WHIRLWIND_WINDUP = 10, WHIRLWIND_CD = 200;
	public static final float WHIRLWIND_COST = 15f;
	public static final float STRIKE_DAMAGE = 26f, STRIKE_CHAIN_DAMAGE = 22f;
	public static final int STRIKE_CD = 70;
	public static final float STRIKE_COST = 18f;
	public static final float FIELD_PULSE = 22f;
	public static final int FIELD_CD = 300;
	public static final float FIELD_COST = 25f;
	public static final float CHAIN_DAMAGE = 24f;
	public static final int CHAIN_CD = 160;
	public static final float CHAIN_COST = 20f;
	public static final float CLAP_DAMAGE = 26f;
	public static final int CLAP_CD = 120;
	public static final float CLAP_COST = 15f;
	public static final float DASH_DAMAGE = 22f;
	public static final double DASH_DISTANCE = 9.0;
	public static final int DASH_CD = 100;
	public static final float DASH_COST = 10f;
	public static final float DIVE_DAMAGE = 28f;
	public static final double DIVE_RADIUS = 5.5;
	public static final int DIVE_CD = 240;
	public static final float DIVE_COST = 25f;
	/** Mjolnir's lightning stun: Slowness this strong for this long, mobs stop in their tracks. */
	public static final int STUN_TICKS = 16;
	/** Mjolnir's Storm Call lasts this much longer ("Storm-Charged lasts longer"). */
	public static final float MJOLNIR_STORM_MUL = 1.5f;

	// ---------------------------------------------------------------- Stormbreaker: Executioner
	public static final float HURL_DAMAGE = 28f;
	public static final int HURL_PIERCE = 4, HURL_CD = 200;
	public static final float HURL_COST = 15f;
	public static final float CLEAVE_DAMAGE = 30f;
	public static final int CLEAVE_CD = 120;
	public static final float CLEAVE_COST = 15f;
	public static final float STEP_DAMAGE = 26f;
	public static final double STEP_RANGE = 20.0;
	public static final int STEP_CD = 200;
	public static final float STEP_COST = 20f;
	public static final float ARC_DAMAGE = 24f;
	public static final double ARC_RADIUS = 6.5;
	public static final int ARC_CD = 160;
	public static final float ARC_COST = 18f;
	public static final float EXEC_DAMAGE = 28f, EXEC_MUL = 1.5f, EXEC_MAX = 30f;
	public static final double EXEC_RADIUS = 4.5;
	public static final int EXEC_CD = 200;
	public static final float EXEC_COST = 20f;
	public static final double BDASH_DISTANCE = 10.0;
	public static final int BDASH_CD = 80;
	public static final float BDASH_COST = 8f;

	// ---------------------------------------------------------------- Both: Twin Gods
	public static final float TWIN_THROW_DAMAGE = 28f;
	public static final double TWIN_THROW_RADIUS = 4.0;
	public static final int TWIN_THROW_CD = 240;
	public static final float TWIN_THROW_COST = 20f;
	public static final float THUNDERSTRUCK_DAMAGE = 28f;
	public static final int THUNDERSTRUCK_CD = 180;
	public static final float THUNDERSTRUCK_COST = 18f;
	public static final int ANCHOR_CD = 400;
	public static final float ANCHOR_COST = 25f;
	public static final float CROSSFIRE_DAMAGE = 24f;
	public static final int CROSSFIRE_CD = 180;
	public static final float CROSSFIRE_COST = 20f;
	public static final float TWIN_STRIKE_DAMAGE = 30f, TWIN_RING_DAMAGE = 22f;
	public static final int TWIN_STRIKE_CD = 300;
	public static final float TWIN_STRIKE_COST = 20f;

	// ---------------------------------------------------------------- beams (Shift+C, held): damage every BEAM_INTERVAL ticks
	public static final int BEAM_INTERVAL = 5;
	public static final float BEAM_MJOLNIR = 22f, BEAM_STORMBREAKER = 26f, BEAM_TWIN = 28f;
	/** Storm Energy a second (Twin is doubled by the state on top: 24 a second). */
	public static final float BEAM_DRAIN_MJOLNIR = 8f, BEAM_DRAIN_STORMBREAKER = 10f, BEAM_DRAIN_TWIN = 12f;

	// ---------------------------------------------------------------- ultimates (Shift+Z)
	public static final int ULT_CD = 2400;
	public static final float ULT_COST = 100f;
	public static final float WRATH_RAIN = 45f, WRATH_FINAL = 60f;
	public static final float JUDGEMENT_IMPALE = 60f, JUDGEMENT_BURST = 45f;
	public static final float RAGNAROK_RAIN = 45f, RAGNAROK_FINAL = 60f;

	// ---------------------------------------------------------------- every state
	public static final int STORM_CALL_CD = 1200;
	public static final float STORM_CALL_COST = 30f;
	public static final int GOD_TICKS = 300, GOD_CD = 900;
	public static final float GOD_COST = 40f;
	public static final float GOD_CHAIN_DAMAGE = 22f;
	public static final double GOD_CHAIN_RANGE = 7.0;
	public static final int GOD_CHAIN_GAP = 8;
	public static final double GOD_KNOCKBACK_RESIST = 0.5;

	private static final ResourceLocation MELEE_ID = ProjectHeroMod.id("thor_state_melee");
	private static final ResourceLocation SWING_ID = ProjectHeroMod.id("thor_state_swing");
	private static final ResourceLocation GOD_KB_ID = ProjectHeroMod.id("thor_god_of_thunder_knockback");
	private static final ResourceLocation SPRINT_ID = ProjectHeroMod.id("thor_storm_sprint");
	private static final ResourceLocation INCARNATE_ID = ProjectHeroMod.id("thor_lightning_incarnate");
	/** Mjolnir's base attack bonus (MjolnirItem.ATTACK_DAMAGE_BONUS, private there): 1 + 10 + 11 (Power of Thor) = 22. */
	private static final double MJOLNIR_ITEM_BONUS = 10.0;

	/** Re-entrancy guard: a God of Thunder chain bolt never chains again. */
	private static final ThreadLocal<Boolean> CHAINING = ThreadLocal.withInitial(() -> false);

	private ThorKit() {
	}

	public static void initialize() {
		ThorSequences.initialize();
		ThorSunder.initialize();
		// God of Thunder: his melee hits arc on as well (ability hits chain from inside hit())
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (!blocked && taken > 0f && source.getEntity() instanceof ServerPlayer p && source.getDirectEntity() == p
					&& entity != p) {
				godChain(p, entity);
			}
		});
	}

	// ================================================================== key routing

	/** Every Thor key (from {@code ThorAbilityAdapter}); the state from his hands picks the move. */
	public static void handle(ServerPlayer p, AbilitySlot slot, boolean pressed) {
		ThorState st = ThorState.of(p);
		boolean sh = p.isShiftKeyDown();
		switch (slot) {
			case SLOT_1 -> { // R: call the weapon; Shift+R the state's throw (Unarmed: hold to charge Thunder Fist)
				if (!pressed) {
					ThorUnarmedKit.releaseFist(p);
				} else if (!sh) {
					ThorPowers.callHammer(p);
				} else {
					switch (st) {
						case UNARMED -> ThorUnarmedKit.startFist(p);
						case MJOLNIR -> ThorMjolnirKit.whirlwindThrow(p);
						case STORMBREAKER -> ThorStormbreakerKit.bifrostHurl(p);
						case TWIN -> ThorTwinKit.twinThrow(p);
					}
				}
			}
			case SLOT_2 -> { // G / Shift+G
				if (pressed) {
					switch (st) {
						case UNARMED -> {
							if (sh) ThorUnarmedKit.grapple(p); else ThorUnarmedKit.lightningPalm(p);
						}
						case MJOLNIR -> {
							if (sh) ThorMjolnirKit.thunderField(p); else ThorPowers.lightningStrike(p);
						}
						case STORMBREAKER -> {
							if (sh) ThorStormbreakerKit.bifrostStep(p); else ThorStormbreakerKit.stormCleave(p);
						}
						case TWIN -> {
							if (sh) ThorTwinKit.stormAnchor(p); else ThorTwinKit.thunderstruckCleave(p);
						}
					}
				}
			}
			case SLOT_3 -> { // X movement / Shift+X
				if (pressed) {
					switch (st) {
						case UNARMED -> {
							if (sh) ThorUnarmedKit.thunderLeap(p); else ThorUnarmedKit.stormSprint(p);
						}
						case MJOLNIR, TWIN -> {
							if (sh) ThorMjolnirKit.meteorDive(p); else ThorMjolnirKit.stormDash(p);
						}
						case STORMBREAKER -> {
							if (sh) ThorStormbreakerKit.bifrostTravel(p); else ThorStormbreakerKit.bifrostDash(p);
						}
					}
				}
			}
			case SLOT_4 -> { // Z / Shift+Z ultimate
				if (pressed) {
					if (sh) {
						ThorUltimates.cast(p, st);
					} else {
						switch (st) {
							case UNARMED -> ThorUnarmedKit.groundPound(p);
							case MJOLNIR -> ThorPowers.thunderclap(p);
							case STORMBREAKER -> ThorStormbreakerKit.executionersDrop(p);
							case TWIN -> ThorTwinKit.twinThunderStrike(p);
						}
					}
				}
			}
			case SLOT_5 -> { // V Storm Call / Shift+V God of Thunder -- the same in every state
				if (pressed) {
					if (sh) godOfThunder(p); else ThorPowers.stormCall(p);
				}
			}
			case SLOT_6 -> { // C / Shift+C (held beam; Unarmed Static Burst)
				if (!pressed) {
					ThorPowers.setLaserActive(p, false);
				} else if (sh) {
					if (st == ThorState.UNARMED) ThorUnarmedKit.staticBurst(p); else ThorPowers.setLaserActive(p, true);
				} else {
					switch (st) {
						case UNARMED -> ThorUnarmedKit.stormRush(p);
						case MJOLNIR -> ThorPowers.chainLightningCast(p);
						case STORMBREAKER -> ThorStormbreakerKit.rendingArc(p);
						case TWIN -> ThorTwinKit.crossfire(p);
					}
				}
			}
			default -> {
			}
		}
	}

	// ================================================================== what the HUD / guide show

	/** The lang id ({@code projecthero.thor.ability.<id>}) of the move on {@code slot} (plain or Shift) in {@code st}. */
	public static String moveId(ThorState st, AbilitySlot slot, boolean shift) {
		return switch (slot) {
			case SLOT_1 -> !shift ? "call_weapon" : switch (st) {
				case UNARMED -> "thunder_fist";
				case MJOLNIR -> "whirlwind_throw";
				case STORMBREAKER -> "bifrost_hurl";
				case TWIN -> "twin_throw";
			};
			case SLOT_2 -> switch (st) {
				case UNARMED -> shift ? "asgardian_grapple" : "lightning_palm";
				case MJOLNIR -> shift ? "thunder_field" : "lightning_strike";
				case STORMBREAKER -> shift ? "bifrost_step" : "storm_cleave";
				case TWIN -> shift ? "storm_anchor" : "thunderstruck_cleave";
			};
			case SLOT_3 -> switch (st) {
				case UNARMED -> shift ? "thunder_leap" : "storm_sprint";
				case MJOLNIR, TWIN -> shift ? "meteor_dive" : "storm_dash";
				case STORMBREAKER -> shift ? "bifrost_travel" : "bifrost_dash";
			};
			case SLOT_4 -> shift ? switch (st) {
				case UNARMED -> "lightning_incarnate";
				case MJOLNIR -> "wrath_of_the_storm";
				case STORMBREAKER -> "bifrost_judgement";
				case TWIN -> "ragnarok";
			} : switch (st) {
				case UNARMED -> "ground_pound";
				case MJOLNIR -> "thunderclap";
				case STORMBREAKER -> "executioners_drop";
				case TWIN -> "twin_thunder";
			};
			case SLOT_5 -> shift ? "god_of_thunder" : "storm_call";
			case SLOT_6 -> switch (st) {
				case UNARMED -> shift ? "static_burst" : "storm_rush";
				case MJOLNIR -> shift ? "lightning_beam" : "chain_lightning";
				case STORMBREAKER -> shift ? "bifrost_beam" : "rending_arc";
				case TWIN -> shift ? "twin_beam" : "crossfire";
			};
			default -> null;
		};
	}

	/** The cooldown shown on {@code slot}'s box (null: none -- a held beam, the call key's anti-spam is not shown). */
	public static ThorAbility cooldownOf(ThorState st, AbilitySlot slot, boolean shift) {
		return switch (slot) {
			case SLOT_1 -> !shift ? null : switch (st) {
				case UNARMED -> ThorAbility.U_THUNDER_FIST;
				case MJOLNIR -> ThorAbility.M_WHIRLWIND;
				case STORMBREAKER -> ThorAbility.S_BIFROST_HURL;
				case TWIN -> ThorAbility.T_TWIN_THROW;
			};
			case SLOT_2 -> switch (st) {
				case UNARMED -> shift ? ThorAbility.U_GRAPPLE : ThorAbility.U_LIGHTNING_PALM;
				case MJOLNIR -> shift ? ThorAbility.THUNDER_FIELD : ThorAbility.LIGHTNING_STRIKE;
				case STORMBREAKER -> shift ? ThorAbility.S_BIFROST_STEP : ThorAbility.S_STORM_CLEAVE;
				case TWIN -> shift ? ThorAbility.T_STORM_ANCHOR : ThorAbility.T_THUNDERSTRUCK;
			};
			case SLOT_3 -> switch (st) {
				case UNARMED -> shift ? ThorAbility.U_THUNDER_LEAP : ThorAbility.U_STORM_SPRINT;
				case MJOLNIR, TWIN -> shift ? ThorAbility.M_METEOR_DIVE : ThorAbility.M_STORM_DASH;
				case STORMBREAKER -> shift ? null : ThorAbility.S_BIFROST_DASH;
			};
			case SLOT_4 -> shift ? ThorAbility.ULTIMATE : switch (st) {
				case UNARMED -> ThorAbility.U_GROUND_POUND;
				case MJOLNIR -> ThorAbility.THUNDERCLAP;
				case STORMBREAKER -> ThorAbility.S_EXECUTIONER;
				case TWIN -> ThorAbility.TWIN_THUNDER;
			};
			case SLOT_5 -> shift ? ThorAbility.GOD_OF_THUNDER_BUFF : ThorAbility.STORM_CALL;
			case SLOT_6 -> shift ? (st == ThorState.UNARMED ? ThorAbility.U_STATIC_BURST : null) : switch (st) {
				case UNARMED -> ThorAbility.U_STORM_RUSH;
				case MJOLNIR -> ThorAbility.CHAIN_LIGHTNING;
				case STORMBREAKER -> ThorAbility.S_RENDING_ARC;
				case TWIN -> ThorAbility.T_CROSSFIRE;
			};
			default -> null;
		};
	}

	// ================================================================== shared rules

	static CooldownState cooldowns(ServerPlayer p) {
		return p.getAttachedOrCreate(ModAttachments.COOLDOWNS);
	}

	public static boolean ready(Player p, ThorAbility a) {
		CooldownState s = p.getAttachedOrElse(ModAttachments.COOLDOWNS, null);
		return s == null || s.isReady(a, p.level().getGameTime());
	}

	/** Timed states keep their end in the cooldown map ("ready at" = when it ends). */
	public static boolean active(Player p, ThorAbility timer) {
		return !ready(p, timer);
	}

	/**
	 * Checks and pays for a move: off cooldown, Thor's kit, enough Storm Energy (x the state's multiplier). On success starts
	 * the cooldown (x the state's multiplier) and spends the energy. A refusal spends nothing.
	 */
	public static boolean begin(ServerPlayer p, ThorAbility a, int baseCd, float baseCost) {
		if (!ThorState.hasKit(p) || !p.isAlive() || p.isSpectator()) {
			return false;
		}
		ThorState st = ThorState.of(p);
		long now = p.level().getGameTime();
		CooldownState cds = cooldowns(p);
		if (!cds.isReady(a, now)) {
			if (a == ThorAbility.ULTIMATE) {
				p.displayClientMessage(Component.translatable("message.projecthero.thor.ultimate_cooldown",
						String.format(java.util.Locale.ROOT, "%.0f", Math.ceil(cds.remainingTicks(a, now) / 20.0))), true);
			}
			return false;
		}
		float cost = st.cost(baseCost);
		if (!StormEnergy.has(p, cost)) {
			p.displayClientMessage(Component.translatable("message.projecthero.no_storm_energy"), true);
			return false;
		}
		ThorPowers.triggerCooldown(p, a, st.cooldown(baseCd));
		StormEnergy.spend(p, cost);
		return true;
	}

	/** Starts a timed state ({@code GOD_ACTIVE} ...) for {@code ticks}. */
	static void startTimer(ServerPlayer p, ThorAbility timer, int ticks) {
		ThorPowers.triggerCooldown(p, timer, ticks);
	}

	static void endTimer(ServerPlayer p, ThorAbility timer) {
		ThorPowers.triggerCooldown(p, timer, 0);
	}

	public enum Kind {
		LIGHTNING, PHYSICAL
	}

	/** One move hit with every state rule: the Stormbreaker finish, the armour cut, the cap, the Mjolnir stun, God's chain. */
	public static boolean hit(ServerPlayer p, LivingEntity t, float amount, Kind kind) {
		return hit(p, t, amount, kind, true, true);
	}

	/**
	 * @param finish whether the Stormbreaker x1.25 finish applies (Executioner's Drop has its own rule)
	 * @param burst  whether to land through a non-player target's hurt cooldown (one-shot moves; never per-tick beams)
	 */
	public static boolean hit(ServerPlayer p, LivingEntity t, float amount, Kind kind, boolean finish, boolean burst) {
		if (t == p || !t.isAlive() || WeaponCombo.friendly(p, t) || !ThorTargets.canAffect(p, t)) {
			return false;
		}
		ServerLevel level = p.serverLevel();
		ThorState st = ThorState.of(p);
		float dmg = amount;
		if (finish && st == ThorState.STORMBREAKER && t.getHealth() < t.getMaxHealth() * EXECUTE_THRESHOLD) {
			dmg *= FINISH_MUL;
		}
		dmg = Math.min(dmg, MAX_HIT);
		DamageSource src = st == ThorState.STORMBREAKER
				? level.damageSources().indirectMagic(p, p) // the Executioner's blade goes straight through armour
				: kind == Kind.LIGHTNING ? ThorTargets.lightning(level, p) : level.damageSources().playerAttack(p);
		if (kind == Kind.LIGHTNING && t instanceof net.minecraft.world.entity.animal.Animal) {
			t.setRemainingFireTicks(Math.max(t.getRemainingFireTicks(), 20));
		}
		boolean landed = burst ? AbilityHelpers.hurtBurst(p, t, src, dmg) : AbilityHelpers.hurt(p, t, src, dmg);
		if (landed) {
			if (kind == Kind.LIGHTNING && st == ThorState.MJOLNIR) {
				stun(t);
			}
			// (God of Thunder's chain fires from the AFTER_DAMAGE listener in initialize(), for move and melee hits alike)
		}
		return landed;
	}

	/** Mjolnir's lightning stun: a brief Slowness wall, and a mob loses its path. Bosses shrug it off. */
	static void stun(LivingEntity t) {
		if (TitanCombat.isBoss(t)) {
			return;
		}
		t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, STUN_TICKS, 9, false, false, true));
		if (t instanceof Mob mob) {
			mob.getNavigation().stop();
		}
		if (t.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, t.getX(), t.getY() + t.getBbHeight() + 0.2, t.getZ(), 6, 0.25, 0.1,
					0.25, 0.02);
		}
	}

	/** God of Thunder: a struck foe arcs a bolt on to the nearest other enemy (22), at most once every 8 ticks. */
	static void godChain(ServerPlayer p, LivingEntity from) {
		if (CHAINING.get() || !active(p, ThorAbility.GOD_ACTIVE) || !ready(p, ThorAbility.GOD_CHAIN)) {
			return;
		}
		LivingEntity next = null;
		double best = Double.MAX_VALUE;
		for (LivingEntity e : ThorTargets.hostilesInRadius(p.serverLevel(), p, from.position(), GOD_CHAIN_RANGE)) {
			if (e == from || WeaponCombo.friendly(p, e)) {
				continue;
			}
			double d = e.distanceToSqr(from);
			if (d < best) {
				best = d;
				next = e;
			}
		}
		if (next == null) {
			return;
		}
		ThorPowers.triggerCooldown(p, ThorAbility.GOD_CHAIN, GOD_CHAIN_GAP);
		CHAINING.set(true);
		try {
			ThorPowers.sendLightningArc(p, 40 + (p.tickCount % 8), from, next, next.position(), 4, 8);
			hit(p, next, GOD_CHAIN_DAMAGE, Kind.LIGHTNING, true, true);
			sound(p.serverLevel(), next.position(), net.minecraft.sounds.SoundEvents.TRIDENT_THUNDER, 0.5f, 1.6f);
		} finally {
			CHAINING.set(false);
		}
	}

	// ================================================================== Shift+V God of Thunder

	public static void godOfThunder(ServerPlayer p) {
		if (!begin(p, ThorAbility.GOD_OF_THUNDER_BUFF, GOD_CD, GOD_COST)) {
			return;
		}
		startTimer(p, ThorAbility.GOD_ACTIVE, GOD_TICKS);
		ThorVisuals.anim(p, ThorFx.ANIM_GOD_OF_THUNDER);
		ServerLevel level = p.serverLevel();
		Vec3 at = p.position();
		ThorTwinThunder.visualBolt(level, p, at);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 1.0, at.z, 60, 0.6, 1.0, 0.6, 0.3);
		level.sendParticles(ParticleTypes.FLASH, at.x, at.y + 1.6, at.z, 1, 0, 0, 0, 0);
		sound(level, at, net.minecraft.sounds.SoundEvents.BEACON_POWER_SELECT, 1.0f, 0.6f);
		sound(level, at, net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_THUNDER, 0.8f, 0.8f);
		ThorTwinThunder.shake(level, at, 0.6f, 10, 14.0);
	}

	// ================================================================== per tick

	/** Every server tick (from {@code ThorPowers.serverTick}): melee per state, the timed states and their channels. */
	static void tick(ServerPlayer p) {
		reconcileMelee(p);
		boolean god = active(p, ThorAbility.GOD_ACTIVE) && ThorState.hasKit(p);
		boolean sprint = active(p, ThorAbility.SPRINT_ACTIVE) && ThorState.hasKit(p);
		boolean incarnate = active(p, ThorAbility.INCARNATE_ACTIVE);
		ThorVisuals.channel(p, ThorFx.CH_GOD, god);
		ThorVisuals.channel(p, ThorFx.CH_SPRINT, sprint);
		ThorVisuals.channel(p, ThorFx.CH_INCARNATE, incarnate);
		modifier(p, Attributes.KNOCKBACK_RESISTANCE, GOD_KB_ID, GOD_KNOCKBACK_RESIST, AttributeModifier.Operation.ADD_VALUE,
				god || incarnate);
		modifier(p, Attributes.MOVEMENT_SPEED, SPRINT_ID, 0.6, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, sprint);
		modifier(p, Attributes.MOVEMENT_SPEED, INCARNATE_ID, 0.45, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, incarnate);
		if (sprint && p.tickCount % 2 == 0) {
			Vec3 at = p.position().add(0, 0.9, 0);
			p.serverLevel().sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 4, 0.3, 0.5, 0.3, 0.05);
		}
		if (god && p.tickCount % 5 == 0) {
			Vec3 eye = p.getEyePosition();
			p.serverLevel().sendParticles(ParticleTypes.ELECTRIC_SPARK, eye.x, eye.y, eye.z, 2, 0.2, 0.15, 0.2, 0.02);
		}
		ThorUnarmedKit.tickIncarnate(p);
	}

	/**
	 * Melee per state (Cosmic 21-28): bare fists 24 at {@link #UNARMED_ATTACK_SPEED}, Mjolnir 24, Stormbreaker 26 (slow), Twin
	 * Gods 27 -- one transient modifier making up the difference from the item + the Power of Thor's +11. Anything else in the
	 * main hand keeps its own damage (+11 as before).
	 */
	public static void reconcileMelee(ServerPlayer p) {
		ItemStack main = p.getMainHandItem();
		boolean kit = ThorState.hasKit(p);
		ThorState st = ThorState.of(p);
		double delta = 0.0;
		double swing = 0.0;
		boolean want = false;
		if (kit) {
			boolean weaponMain = main.is(ModItems.MJOLNIR) || main.is(ModItems.STORMBREAKER);
			if (st == ThorState.UNARMED ? main.isEmpty() : weaponMain) {
				double item = main.is(ModItems.MJOLNIR) ? MJOLNIR_ITEM_BONUS
						: main.is(ModItems.STORMBREAKER) ? com.projecthero.mod.stormbreaker.StormbreakerItem.ATTACK_DAMAGE_BONUS : 0.0;
				double passive = ThorPassives.hasPowerOfThor(p) ? 11.0 : 0.0;
				AttributeInstance attack = p.getAttribute(Attributes.ATTACK_DAMAGE);
				double base = attack == null ? 1.0 : attack.getBaseValue();
				delta = st.melee - (base + item + passive);
				if (st == ThorState.UNARMED) {
					AttributeInstance speed = p.getAttribute(Attributes.ATTACK_SPEED);
					swing = UNARMED_ATTACK_SPEED - (speed == null ? 4.0 : speed.getBaseValue());
				}
				want = true;
			}
		}
		modifier(p, Attributes.ATTACK_DAMAGE, MELEE_ID, delta, AttributeModifier.Operation.ADD_VALUE, want && delta != 0.0);
		modifier(p, Attributes.ATTACK_SPEED, SWING_ID, swing, AttributeModifier.Operation.ADD_VALUE, want && swing != 0.0);
	}

	static void modifier(ServerPlayer p, Holder<Attribute> attribute, ResourceLocation id, double amount,
			AttributeModifier.Operation op, boolean wanted) {
		AttributeInstance inst = p.getAttribute(attribute);
		if (inst == null) {
			return;
		}
		AttributeModifier cur = inst.getModifier(id);
		if (wanted) {
			if (cur == null || cur.amount() != amount || cur.operation() != op) {
				inst.addOrUpdateTransientModifier(new AttributeModifier(id, amount, op));
			}
		} else if (cur != null) {
			inst.removeModifier(id);
		}
	}

	/** The flight speed for {@code p}'s state (weaponless grace keeps the last one). */
	static float flyingSpeed(ServerPlayer p) {
		ThorState st = ThorState.of(p);
		return st.flies() ? st.flyingSpeed : p.getAbilities().getFlyingSpeed();
	}

	// ================================================================== effect helpers

	/** The Bifrost's seven colours. */
	public static final int[] RAINBOW = { 0xFF3B3B, 0xFF9A2E, 0xFFE83A, 0x45F06A, 0x3AD8FF, 0x4A6BFF, 0xB84AFF };

	/** A burst of rainbow dust (the Bifrost palette) at {@code at}. */
	public static void prism(ServerLevel level, Vec3 at, int count, double spread, double spreadY) {
		for (int i = 0; i < count; i++) {
			int c = RAINBOW[(i + level.random.nextInt(RAINBOW.length)) % RAINBOW.length];
			DustParticleOptions dust = new DustParticleOptions(new Vector3f(((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f,
					(c & 0xFF) / 255f), 1.3f);
			level.sendParticles(dust, at.x + (level.random.nextDouble() - 0.5) * 2 * spread,
					at.y + (level.random.nextDouble() - 0.5) * 2 * spreadY, at.z + (level.random.nextDouble() - 0.5) * 2 * spread, 1,
					0, 0, 0, 0);
		}
	}

	/** A rainbow streak of dust from {@code a} to {@code b}. */
	public static void prismLine(ServerLevel level, Vec3 a, Vec3 b, double step) {
		Vec3 d = b.subtract(a);
		int n = Math.max(2, (int) (d.length() / step));
		for (int i = 0; i <= n; i++) {
			Vec3 at = a.add(d.scale(i / (double) n));
			int c = RAINBOW[i % RAINBOW.length];
			level.sendParticles(new DustParticleOptions(new Vector3f(((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f,
					(c & 0xFF) / 255f), 1.6f), at.x, at.y, at.z, 1, 0.05, 0.05, 0.05, 0);
		}
	}

	/** A flat ring of sparks on the ground at radius {@code r}. */
	public static void sparkRing(ServerLevel level, Vec3 c, double r, int points) {
		for (int i = 0; i < points; i++) {
			double a = Math.PI * 2 * i / points;
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x + Math.cos(a) * r, c.y + 0.15, c.z + Math.sin(a) * r, 1, 0.05,
					0.02, 0.05, 0.02);
		}
	}

	public static void sound(ServerLevel level, Vec3 at, SoundEvent s, float vol, float pitch) {
		level.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, vol, pitch);
	}

	public static void sound(ServerLevel level, Vec3 at, Holder<SoundEvent> s, float vol, float pitch) {
		level.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, vol, pitch);
	}

	/** The closest valid enemy along the aim within {@code range} (ray first, then a soft cone), or null. */
	public static LivingEntity aimTarget(ServerPlayer p, double range, double coneDeg) {
		net.minecraft.world.phys.HitResult hit = net.minecraft.world.entity.projectile.ProjectileUtil.getHitResultOnViewVector(p,
				e -> ThorPowers.aimable(p, e), range);
		if (hit instanceof net.minecraft.world.phys.EntityHitResult eh && eh.getEntity() instanceof LivingEntity le
				&& !WeaponCombo.friendly(p, le)) {
			return le;
		}
		return ThorPowers.findAimAssistTarget(p, range, coneDeg);
	}

	/** Where the aim lands within {@code range}: an entity, a block, or the end of the ray. */
	public static Vec3 aimPoint(ServerPlayer p, double range) {
		Vec3 eye = p.getEyePosition();
		Vec3 end = eye.add(p.getLookAngle().scale(range));
		net.minecraft.world.phys.BlockHitResult b = p.level().clip(new net.minecraft.world.level.ClipContext(eye, end,
				net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, p));
		return b.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? end : b.getLocation();
	}

	/** Horizontal facing (unit, y = 0). */
	static Vec3 flatLook(Entity e) {
		Vec3 f = e.getLookAngle().multiply(1, 0, 1);
		return f.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : f.normalize();
	}

	/** Knock {@code t} away from {@code from}; bosses are never moved. */
	static void knock(LivingEntity t, Vec3 from, double strength, double lift) {
		if (TitanCombat.isBoss(t) || !t.isAlive()) {
			return;
		}
		AbilityHelpers.knockbackFrom(t, from, strength);
		if (lift > 0) {
			AbilityHelpers.push(t, new Vec3(0, lift, 0));
		}
	}
}
