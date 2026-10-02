package com.projecthero.mod.hero.power.p27;

import com.projecthero.mod.hero.Ability;
import com.projecthero.mod.hero.AbilityContext;
import com.projecthero.mod.hero.AbilityHandlers;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.Handlers;
import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.hero.visual.MutationVisuals;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Power 27 — Size Manipulation. Three toggle forms on their own keys, mutually exclusive: X = Tiny,
 * C = Large, Z = Giant. Pressing a form key again returns to normal; pressing a different form key
 * swaps straight to that form. Uses {@link Attributes#SCALE}, validated against a hitbox-fit check so
 * growth never forces the player into blocks.
 *
 * <p>v0.13.22 revamp (batch E): every form change eases over one second (both ways, geometric), every move
 * animates, damage ~+20%, cooldowns ~-15%, the Size Strain reserve holds 15% more, and two new utility moves:
 * H <b>Shrink Punch</b> (the target is shrunk to half size and hits 40% softer for 8 s -- {@link ShrunkenEffect})
 * and N <b>Mount</b> (Tiny: ride the mob you are looking at; Large / Giant: pick it up and carry it in your hand;
 * press again to dismount / throw; always released safely on a form change, on death or after the time limit).
 */
public final class SizeHandlers {
	private static final String KEY = "power_27_size_manipulation";
	private static final net.minecraft.resources.ResourceLocation SCALE = com.projecthero.mod.ProjectHeroMod.id("size_scale");
	private static final net.minecraft.resources.ResourceLocation ATK = com.projecthero.mod.ProjectHeroMod.id("size_atk");
	private static final net.minecraft.resources.ResourceLocation REACH = com.projecthero.mod.ProjectHeroMod.id("size_reach");
	private static final net.minecraft.resources.ResourceLocation STEP = com.projecthero.mod.ProjectHeroMod.id("size_step");
	private static final net.minecraft.resources.ResourceLocation HP = com.projecthero.mod.ProjectHeroMod.id("size_hp");
	private static final net.minecraft.resources.ResourceLocation JUMP = com.projecthero.mod.ProjectHeroMod.id("size_jump");

	/** Player base height is 1.8; scales chosen so Tiny ≈ 0.5 blocks, Large ≈ 6, Giant ≈ 15. */
	private static final double TINY_SCALE = 0.28;
	private static final double LARGE_SCALE = 3.33;
	private static final double GIANT_SCALE = 8.33;

	/** v0.13.22: +15% capacity (was 500) at the same drain -- a full Giant form now lasts ~26 s. */
	public static final float MAX_STRAIN = 575.0f;
	private static final float STRAIN_DRAIN = 500.0f / (23 * 20);
	private static final float STRAIN_REGEN = 500.0f / (45 * 20); // and recovers slowly once out
	private static final float STRAIN_MIN_ENTER = 60.0f; // need a little in the tank to go Giant again

	private enum Form { NORMAL, TINY, LARGE, GIANT }

	static final float SHRINK_PUNCH_DAMAGE = 8.0f;
	/** Shrunken lasts 8 s. */
	public static final int SHRINK_TICKS = 160;
	/** Mount bounds: ride up to 60 s, carry up to 20 s. */
	public static final int RIDE_TICKS = 1200;
	public static final int CARRY_TICKS = 400;

	/** v0.12.1: every size change takes one second -- the scale eases from its current value to the target. */
	private static final int SCALE_ANIM_TICKS = 20;
	/** Per player: {fromScale, toScale, startGameTime}. Static world-object cache -- reset via {@link #clearSessionState}. */
	private static final java.util.Map<java.util.UUID, double[]> SCALE_ANIM = new java.util.concurrent.ConcurrentHashMap<>();

	public static void clearSessionState() {
		SCALE_ANIM.clear();
	}

	private static double currentScale(ServerPlayer p) {
		net.minecraft.world.entity.ai.attributes.AttributeInstance inst = p.getAttribute(Attributes.SCALE);
		AttributeModifier m = inst == null ? null : inst.getModifier(SCALE);
		return m == null ? 1.0 : 1.0 + m.amount();
	}

	private static void writeScale(ServerPlayer p, double scale) {
		if (Math.abs(scale - 1.0) < 1.0E-3) {
			PowerToggles.clearModifier(p, Attributes.SCALE, SCALE);
		} else {
			PowerToggles.modifier(p, Attributes.SCALE, SCALE, scale - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		}
	}

	/** Start (or keep) easing toward {@code target}; a no-op if already there or already heading there. */
	private static void setScaleTarget(ServerPlayer p, double target) {
		double[] a = SCALE_ANIM.get(p.getUUID());
		if (a != null && a[1] == target) {
			return;
		}
		double cur = currentScale(p);
		if (Math.abs(cur - target) < 1.0E-3) {
			SCALE_ANIM.remove(p.getUUID());
			return;
		}
		SCALE_ANIM.put(p.getUUID(), new double[] {cur, target, p.level().getGameTime()});
	}

	/** Advance the ease by one tick. Growth waits (and retries) if the bigger body would not fit. */
	private static void tickScale(ServerPlayer p) {
		double[] a = SCALE_ANIM.get(p.getUUID());
		if (a == null) {
			return;
		}
		double t = Math.min(1.0, (p.level().getGameTime() - (long) a[2]) / (double) SCALE_ANIM_TICKS);
		// geometric interpolation so 0.28 <-> 8.33 grows by the same proportion every tick
		double scale = t >= 1.0 ? a[1] : a[0] * Math.pow(a[1] / a[0], t);
		if (scale > currentScale(p) && !fits(p, scale)) {
			a[2] += 1; // hold the animation clock while blocked
			return;
		}
		writeScale(p, scale);
		if (t >= 1.0) {
			SCALE_ANIM.remove(p.getUUID());
		}
	}

	/** Ability ids of the three form toggles, in slot order X / Z / C. */
	private static final String[] FORM_TOGGLES = {"shrink", "giant_form", "large_form"};

	private SizeHandlers() {
	}

	// ---------------- form application ----------------

	private static boolean fits(ServerPlayer p, double scale) {
		if (scale <= 1.0) {
			return true;
		}
		AABB base = p.getBoundingBox();
		Vec3 c = base.getCenter();
		double hw = (base.getXsize() / 2) * scale;
		double h = base.getYsize() * scale;
		AABB grown = new AABB(c.x - hw, base.minY, c.z - hw, c.x + hw, base.minY + h, c.z + hw);
		return p.level().noCollision(p, grown);
	}

	private static double scaleFor(Form f) {
		return switch (f) {
			case TINY -> TINY_SCALE;
			case LARGE -> LARGE_SCALE;
			case GIANT -> GIANT_SCALE;
			default -> 1.0;
		};
	}

	private static void applyForm(ServerPlayer p, Form f) {
		double scale = scaleFor(f);
		setScaleTarget(p, scale);
		double atk = switch (f) {
			case TINY -> -2.0;
			case LARGE -> 6.0;
			case GIANT -> 18.0;
			default -> 0.0;
		};
		// v0.10.13: bigger forms reach proportionally further -- roughly their own height. Base
		// interaction range is 3, so Large (≈6 blocks tall) lands near 7 and Giant (≈15) near 16.
		double reach = switch (f) {
			case TINY -> -1.5;
			case LARGE -> 4.0;
			case GIANT -> 13.0;
			default -> 0.0;
		};
		double step = switch (f) {
			case TINY -> -0.3;
			case LARGE -> 2.0;
			case GIANT -> 6.0;
			default -> 0.0;
		};
		double hp = switch (f) {
			case LARGE -> 8.0;
			case GIANT -> 20.0;
			default -> 0.0;
		};
		double jump = switch (f) {
			case LARGE -> 0.6;
			case GIANT -> 1.9;
			default -> 0.0;
		};
		PowerToggles.modifier(p, Attributes.ATTACK_DAMAGE, ATK, atk, AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(p, Attributes.ENTITY_INTERACTION_RANGE, REACH, reach, AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(p, Attributes.STEP_HEIGHT, STEP, step, AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(p, Attributes.MAX_HEALTH, HP, hp, AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(p, Attributes.JUMP_STRENGTH, JUMP, jump, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		if (f == Form.TINY) {
			p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 1, false, false, false));
		}
	}

	/** Drop the stat modifiers now and ease the body back to normal size over a second. */
	private static void clearForm(ServerPlayer p) {
		clearStats(p);
		setScaleTarget(p, 1.0);
	}

	/** Power removed / deactivated: no animation, everything gone immediately. */
	private static void clearFormNow(ServerPlayer p) {
		SCALE_ANIM.remove(p.getUUID());
		PowerToggles.clearModifier(p, Attributes.SCALE, SCALE);
		clearStats(p);
	}

	private static void clearStats(ServerPlayer p) {
		PowerToggles.clearModifier(p, Attributes.ATTACK_DAMAGE, ATK);
		PowerToggles.clearModifier(p, Attributes.ENTITY_INTERACTION_RANGE, REACH);
		PowerToggles.clearModifier(p, Attributes.STEP_HEIGHT, STEP);
		PowerToggles.clearModifier(p, Attributes.MAX_HEALTH, HP);
		PowerToggles.clearModifier(p, Attributes.JUMP_STRENGTH, JUMP);
	}

	private static Ability byId(Power power, String id) {
		if (power == null) {
			return null;
		}
		for (Ability a : power.abilities()) {
			if (a.id().equals(id)) {
				return a;
			}
		}
		return null;
	}

	private static boolean toggled(ServerPlayer p, Power power, String abilityId) {
		Ability a = byId(power, abilityId);
		return a != null && ExperimentalPowers.isToggled(p, power, a);
	}

	private static Form currentForm(ServerPlayer p) {
		Power power = Powers.byKey(KEY);
		if (toggled(p, power, "giant_form")) {
			return Form.GIANT;
		}
		if (toggled(p, power, "large_form")) {
			return Form.LARGE;
		}
		if (toggled(p, power, "shrink")) {
			return Form.TINY;
		}
		return Form.NORMAL;
	}

	/** Turn off every OTHER form toggle before entering {@code entering}. */
	private static void makeExclusive(ServerPlayer p, String entering) {
		Power power = Powers.byKey(KEY);
		for (String other : FORM_TOGGLES) {
			if (other.equals(entering)) {
				continue;
			}
			Ability ab = byId(power, other);
			if (ab != null && ExperimentalPowers.isToggled(p, power, ab)) {
				ExperimentalPowers.setToggled(p, power, ab, false);
			}
		}
	}

	private static float formDamageBonus(ServerPlayer p) {
		return switch (currentForm(p)) {
			case GIANT -> 18.0f;
			case LARGE -> 6.0f;
			default -> 0.0f;
		};
	}

	/** Tiny fists barely land -- Giant Punch / Stomp hit for a fraction of their normal force in small form. */
	private static float formDamageMult(ServerPlayer p) {
		return currentForm(p) == Form.TINY ? 0.3f : 1.0f;
	}

	/**
	 * How far a giant's fists and stomp reach, scaled so it makes sense for how tall the player is:
	 * a 15-block giant swings roughly four times as far as a normal player.
	 */
	private static double reachScale(ServerPlayer p) {
		return switch (currentForm(p)) {
			case GIANT -> 4.0;
			case LARGE -> 2.0;
			case TINY -> 0.6;
			default -> 1.0;
		};
	}

	// ---------------- registration ----------------

	public static void register() {
		AbilityHandlers.register(KEY, "giant_punch", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			double range = 6.5 * reachScale(p);
			LivingEntity t = AbilityHelpers.raycastEntity(p, range);
			float punch = (12.0f + formDamageBonus(p)) * formDamageMult(p);
			MutationVisuals.play(p, "haymaker");
			if (t == null) {
				// a giant's fist is wide -- sweep everything in an arc ahead, not just a pinpoint ray
				for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.getEyePosition().add(p.getLookAngle().scale(range * 0.5)), range * 0.5)) {
					AbilityHelpers.hurt(p, e, punch);
					AbilityHelpers.knockbackFrom(e, p.position(), 1.6);
				}
			} else {
				AbilityHelpers.hurt(p, t, punch);
				AbilityHelpers.knockbackFrom(t, p.position(), 1.6);
			}
			AbilityHelpers.burst(ctx.level(), p.getEyePosition().add(p.getLookAngle().scale(3 * reachScale(p))), ParticleTypes.SWEEP_ATTACK, 4, 0.3);
			AbilityHelpers.sound(p, SoundEvents.PLAYER_ATTACK_CRIT, 1.0f, 0.4f);
			ctx.triggerCooldown();
		}));

		AbilityHandlers.register(KEY, "stomp", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			double radius = (3.5 + currentForm(p).ordinal()) * reachScale(p);
			float stompDmg = (9.6f + formDamageBonus(p)) * formDamageMult(p);
			MutationVisuals.play(p, "stomp");
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position(), radius)) {
				AbilityHelpers.hurt(p, e, stompDmg);
				AbilityHelpers.knockbackFrom(e, p.position(), 1.2);
				AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, 40, 2);
			}
			ctx.level().sendParticles(ParticleTypes.EXPLOSION, p.getX(), p.getY(), p.getZ(), 1, 0, 0, 0, 0);
			AbilityHelpers.sound(p, SoundEvents.GENERIC_EXPLODE, 0.7f, 0.5f);
			ctx.triggerCooldown();
		}));

		AbilityHandlers.register(KEY, "shrink", Handlers.toggle(
				ctx -> enter(ctx, "shrink", Form.TINY),
				ctx -> exitForm(ctx.player()),
				ctx -> applyForm(ctx.player(), Form.TINY)));

		AbilityHandlers.register(KEY, "large_form", Handlers.toggle(
				ctx -> enter(ctx, "large_form", Form.LARGE),
				ctx -> exitForm(ctx.player()),
				ctx -> {
					applyForm(ctx.player(), Form.LARGE);
					itemMagnet(ctx.player(), 6.0);
				}));

		AbilityHandlers.register(KEY, "giant_form", Handlers.toggle(
				ctx -> {
					com.projecthero.mod.hero.power.ModeMeter.ensureSeeded(ctx, "giant_form", MAX_STRAIN);
					if (!com.projecthero.mod.hero.power.ModeMeter.hasCharge(ctx, "giant_form", STRAIN_MIN_ENTER)) {
						ctx.setToggled(false);
						ctx.actionBar("message.projecthero.size.strain_low");
						return;
					}
					if (!enter(ctx, "giant_form", Form.GIANT)) {
						return;
					}
					AbilityHelpers.sound(ctx.player(), SoundEvents.RAVAGER_ROAR, 1.2f, 0.3f);
				},
				ctx -> exitForm(ctx.player()), // strain is NOT reset -- it recharges on its own once you shrink back
				SizeHandlers::giantTick));

		AbilityHandlers.register(KEY, "tiny_dash", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			AbilityHelpers.addImpulse(p, p.getLookAngle().scale(1.8));
			MutationVisuals.play(p, "dash_forward");
			p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 3, false, false, false));
			p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 15, 2, false, false, false));
			AbilityHelpers.burst(ctx.level(), p.position(), ParticleTypes.POOF, 12, 0.2);
			ctx.triggerCooldown();
		}));

		// H -- Shrink Punch: the target shrinks to half size (smaller hitbox, shorter reach, 40% weaker) for 8 s.
		AbilityHandlers.register(KEY, "shrink_punch", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			MutationVisuals.play(p, "punch_right");
			LivingEntity t = AbilityHelpers.raycastEntity(p, 4.5 * reachScale(p));
			if (t == null) {
				AbilityHelpers.sound(p, SoundEvents.PLAYER_ATTACK_WEAK, 0.8f, 1.2f);
				return; // a whiff costs nothing
			}
			shrinkPunch(p, t);
			ctx.triggerCooldown();
		}));

		// N -- Mount: Tiny -> ride the mob you look at; Large / Giant -> pick it up and carry it. Press again to
		// dismount / throw. No sneak variant (Sneak+N is reserved for power combos).
		AbilityHandlers.register(KEY, "mount", Handlers.instantTicking(SizeHandlers::mountPress, SizeHandlers::mountTick));

		// dying with a mob in hand (or on a mount) lets it go safely
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer sp && ExperimentalPowers.owns(sp, KEY)) {
				releaseAll(sp);
			}
		});

		com.projecthero.mod.hero.PowerPassives.register(KEY, (player, active) -> {
			if (!active) {
				releaseAll(player);
				clearFormNow(player);
			}
		});
		com.projecthero.mod.hero.PowerPassives.registerTick(KEY, player -> {
			tickScale(player);
			// v0.13.22: seed the Size Strain reserve so its (always-on) HUD bar shows from the start
			if (!ExperimentalPowers.state(player).resources.containsKey(KEY + "/giant_form")) {
				ExperimentalPowers.setResource(player, Powers.byKey(KEY), "giant_form", MAX_STRAIN, MAX_STRAIN);
			}
			// keep the current form's modifiers applied (respawn-safe); giant handled by its own tick
			Form f = currentForm(player);
			if ((f == Form.TINY || f == Form.LARGE) && player.tickCount % 20 == 0) {
				applyForm(player, f);
				if (f == Form.LARGE) {
					itemMagnet(player, 6.0);
				}
			}
			// the size-strain meter recovers slowly whenever the player is not currently a giant
			com.projecthero.mod.hero.power.ModeMeter.regen(player, Powers.byKey(KEY), "giant_form",
					MAX_STRAIN, STRAIN_REGEN, f == Form.GIANT);
		});
	}

	// ---------------- H: Shrink Punch ----------------

	/** Shrink Punch payload, public for the gametests: damage plus 8 s of {@link ShrunkenEffect} (bosses: damage only). */
	public static void shrinkPunch(ServerPlayer p, LivingEntity t) {
		AbilityHelpers.hurt(p, t, SHRINK_PUNCH_DAMAGE * formDamageMult(p));
		if (t.getMaxHealth() <= 200.0f) {
			AbilityHelpers.applyControl(t, ShrunkenEffect.HOLDER, SHRINK_TICKS, 0);
		}
		AbilityHelpers.knockbackFrom(t, p.position(), 0.5);
		ServerLevel sl = AbilityHelpers.level(p);
		double y = t.getY() + t.getBbHeight() * 0.5;
		sl.sendParticles(ParticleTypes.REVERSE_PORTAL, t.getX(), y, t.getZ(), 30, t.getBbWidth() * 0.6, t.getBbHeight() * 0.4,
				t.getBbWidth() * 0.6, 0.02);
		sl.sendParticles(ParticleTypes.POOF, t.getX(), y, t.getZ(), 12, 0.3, 0.3, 0.3, -0.05);
		AbilityHelpers.sound(p, SoundEvents.AMETHYST_BLOCK_BREAK, 1.0f, 1.8f);
		AbilityHelpers.sound(p, SoundEvents.PLAYER_ATTACK_STRONG, 0.8f, 1.3f);
	}

	// ---------------- N: Mount / Carry ----------------

	private static final int RIDE = 1;
	private static final int CARRY = 2;

	private static float res(net.minecraft.world.entity.player.Player p, String name) {
		com.projecthero.mod.hero.data.ExperimentalState st =
				p.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.EXPERIMENTAL_STATE, null);
		return st == null ? 0.0f : st.resources.getOrDefault(KEY + "/" + name, 0.0f);
	}

	/** Riding a mob in Tiny form (synced). */
	public static boolean riding(net.minecraft.world.entity.player.Player p) {
		return Math.round(res(p, "mount_mode")) == RIDE;
	}

	/** Carrying a mob in Large / Giant form (synced). */
	public static boolean carrying(net.minecraft.world.entity.player.Player p) {
		return Math.round(res(p, "mount_mode")) == CARRY;
	}

	/** The mob being ridden / carried, or null. */
	public static LivingEntity mounted(ServerPlayer p) {
		int id = Math.round(res(p, "mount_id"));
		return id != 0 && p.level().getEntity(id) instanceof LivingEntity le && le.isAlive() ? le : null;
	}

	private static void mountPress(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		if (res(p, "mount_mode") > 0.5f) {
			if (carrying(p)) {
				LivingEntity held = mounted(p);
				releaseAll(p);
				if (held != null) {
					// throw it where you look
					held.setDeltaMovement(p.getLookAngle().scale(1.3 + reachScale(p) * 0.15).add(0, 0.35, 0));
					held.hurtMarked = true;
					held.hasImpulse = true;
					MutationVisuals.play(p, "throw_right");
					AbilityHelpers.sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 0.5f);
				}
			} else {
				releaseAll(p);
			}
			ctx.triggerCooldown();
			return;
		}
		Form f = currentForm(p);
		if (f == Form.NORMAL) {
			ctx.actionBar("message.projecthero.size.mount_needs_form");
			return;
		}
		LivingEntity t = AbilityHelpers.raycastEntity(p, f == Form.TINY ? 4.0 : 5.0 * reachScale(p));
		if (t == null || t instanceof net.minecraft.world.entity.player.Player || t.getMaxHealth() > 200.0f
				|| t instanceof net.minecraft.world.entity.decoration.ArmorStand || t.isPassenger() || t.isVehicle()) {
			ctx.actionBar("message.projecthero.size.mount_none");
			return;
		}
		if (!mountTarget(p, t)) {
			ctx.actionBar(f == Form.TINY ? "message.projecthero.size.mount_none" : "message.projecthero.size.mount_too_big");
			return;
		}
		ctx.level().sendParticles(ParticleTypes.POOF, t.getX(), t.getY() + t.getBbHeight() * 0.5, t.getZ(), 10, 0.3, 0.3, 0.3, 0.02);
	}

	/**
	 * Mounts {@code t}: rides it in Tiny form, picks it up in Large / Giant form. Returns false when the form does not
	 * allow it (Normal form; a mob more than 60% of your height to carry). Public for the gametests.
	 */
	public static boolean mountTarget(ServerPlayer p, LivingEntity t) {
		Power power = Powers.byKey(KEY);
		Form f = currentForm(p);
		if (f == Form.TINY) {
			if (!p.startRiding(t, true)) {
				return false;
			}
			ExperimentalPowers.setResource(p, power, "mount_mode", RIDE, 3);
			ExperimentalPowers.setResource(p, power, "mount_id", t.getId(), 1.0e9f);
			ExperimentalPowers.setResource(p, power, "mount_ticks", RIDE_TICKS, RIDE_TICKS);
			MutationVisuals.play(p, "p27.ride");
			AbilityHelpers.sound(p, SoundEvents.HORSE_SADDLE, 0.8f, 1.6f);
			return true;
		}
		if ((f != Form.LARGE && f != Form.GIANT) || t.getBbHeight() > p.getBbHeight() * 0.6) {
			return false;
		}
		ExperimentalPowers.setResource(p, power, "mount_mode", CARRY, 3);
		ExperimentalPowers.setResource(p, power, "mount_id", t.getId(), 1.0e9f);
		ExperimentalPowers.setResource(p, power, "mount_ticks", CARRY_TICKS, RIDE_TICKS);
		MutationVisuals.play(p, "p27.carry");
		AbilityHelpers.sound(p, SoundEvents.ARMOR_EQUIP_LEATHER.value(), 1.0f, 0.5f);
		return true;
	}

	private static void mountTick(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		int mode = Math.round(ctx.resource("mount_mode"));
		if (mode == 0) {
			return;
		}
		float left = ctx.resource("mount_ticks") - 1;
		LivingEntity m = mounted(p);
		Form f = currentForm(p);
		boolean formOk = mode == RIDE ? f == Form.TINY : (f == Form.LARGE || f == Form.GIANT);
		if (m == null || !p.isAlive() || left <= 0 || !formOk || p.distanceToSqr(m) > 24.0 * 24.0
				|| (mode == RIDE && p.getVehicle() != m)) {
			releaseAll(p);
			ctx.triggerCooldown();
			return;
		}
		ctx.setResource("mount_ticks", left, RIDE_TICKS);
		if (mode == RIDE) {
			MutationVisuals.ensure(p, "p27.ride");
			p.resetFallDistance();
			return;
		}
		holdCarried(p, m);
		MutationVisuals.ensure(p, "p27.carry");
	}

	/** Carry: the mob sits in the right hand, out in front at about two-thirds of your height. */
	static void holdCarried(ServerPlayer p, LivingEntity m) {
		Vec3 look = p.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0, look.z);
		flat = flat.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : flat.normalize();
		Vec3 right = new Vec3(-flat.z, 0, flat.x);
		Vec3 hold = p.position().add(flat.scale(p.getBbWidth() * 0.75 + m.getBbWidth() * 0.5))
				.add(right.scale(p.getBbWidth() * 0.35)).add(0, p.getBbHeight() * 0.62 - m.getBbHeight() * 0.5, 0);
		m.setPos(hold.x, hold.y, hold.z);
		m.setDeltaMovement(Vec3.ZERO);
		m.fallDistance = 0.0f;
		m.hurtMarked = true;
	}

	/** Lets go of whatever is ridden / carried, safely: no launch, no fall damage (Slow Falling when airborne). */
	public static void releaseAll(ServerPlayer p) {
		Power power = Powers.byKey(KEY);
		if (power == null || !ExperimentalPowers.owns(p, power) || res(p, "mount_mode") < 0.5f) {
			return;
		}
		int mode = Math.round(res(p, "mount_mode"));
		LivingEntity m = mounted(p);
		ExperimentalPowers.setResource(p, power, "mount_mode", 0, 3);
		ExperimentalPowers.setResource(p, power, "mount_id", 0, 1.0e9f);
		ExperimentalPowers.setResource(p, power, "mount_ticks", 0, RIDE_TICKS);
		MutationVisuals.stopIf(p, "p27.ride");
		MutationVisuals.stopIf(p, "p27.carry");
		if (mode == RIDE && p.isPassenger()) {
			p.stopRiding();
			p.resetFallDistance();
		}
		if (m != null) {
			m.setDeltaMovement(0, -0.05, 0);
			m.fallDistance = 0.0f;
			m.hurtMarked = true;
			if (mode == CARRY && !m.onGround()) {
				m.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0, false, false, false));
			}
		}
	}

	/** Enter {@code form}, making it exclusive. Returns false (and un-toggles) if a big form will not fit. */
	private static boolean enter(AbilityContext ctx, String abilityId, Form form) {
		ServerPlayer p = ctx.player();
		if (form != Form.NORMAL && form != Form.TINY && !fits(p, scaleFor(form))) {
			p.setPos(p.getX(), p.getY() + 0.5, p.getZ());
			if (!fits(p, scaleFor(form))) {
				p.setPos(p.getX(), p.getY() - 0.5, p.getZ());
				ctx.setToggled(false);
				ctx.actionBar("message.projecthero.size.no_room");
				return false;
			}
		}
		Form was = currentForm(p);
		makeExclusive(p, abilityId);
		// If you go Large / Giant while already at full health, the extra hearts come pre-filled --
		// otherwise you would grow with a big empty gap and have to eat/regen to use the new HP. If you
		// were already hurt, the new hearts start empty and you heal into them as normal.
		boolean wasFullHp = p.getHealth() >= p.getMaxHealth() - 0.01f;
		applyForm(p, form);
		if (wasFullHp && (form == Form.LARGE || form == Form.GIANT)) {
			p.setHealth(p.getMaxHealth());
		}
		ctx.actionBar("message.projecthero.size.mode_" + form.name().toLowerCase(java.util.Locale.ROOT));
		sizeChangeFx(p, was, form);
		MutationVisuals.play(p, form == Form.TINY ? "p27.shrink" : form == Form.GIANT ? "power_up" : "flex");
		return true;
	}

	/** Leave the current form back to normal, with an imploding-particle flourish. */
	private static void exitForm(ServerPlayer p) {
		Form was = currentForm(p);
		clearForm(p);
		sizeChangeFx(p, was, Form.NORMAL);
		MutationVisuals.play(p, was == Form.TINY ? "flex" : "p27.shrink");
	}

	/**
	 * The visual "pop" as the player changes size. Growing throws an expanding shell of cloud and
	 * spark outward; shrinking implodes a puff of poof toward the player. Scaled to how big the change is.
	 */
	private static void sizeChangeFx(ServerPlayer p, Form from, Form to) {
		if (!(p.level() instanceof ServerLevel sl) || from == to) {
			return;
		}
		double y = p.getY() + p.getBbHeight() * 0.5;
		boolean growing = scaleFor(to) > scaleFor(from);
		double spread = Math.max(scaleFor(from), scaleFor(to)) * 0.9 + 0.5;
		int count = 40 + (int) (spread * 12);
		if (growing) {
			sl.sendParticles(ParticleTypes.CLOUD, p.getX(), y, p.getZ(), count, spread, spread * 0.7, spread, 0.12);
			sl.sendParticles(ParticleTypes.END_ROD, p.getX(), y, p.getZ(), count / 3, spread * 0.6, spread * 0.5, spread * 0.6, 0.15);
			sl.sendParticles(ParticleTypes.EXPLOSION, p.getX(), y, p.getZ(), Math.max(1, (int) spread), spread * 0.4, 0.2, spread * 0.4, 0.0);
			AbilityHelpers.sound(p, SoundEvents.BREEZE_INHALE, 1.2f, growing ? 0.7f : 1.4f);
		} else {
			sl.sendParticles(ParticleTypes.POOF, p.getX(), y, p.getZ(), count, spread * 0.6, spread * 0.5, spread * 0.6, -0.08);
			sl.sendParticles(ParticleTypes.CLOUD, p.getX(), y, p.getZ(), count / 2, spread * 0.5, spread * 0.4, spread * 0.5, 0.02);
			AbilityHelpers.sound(p, SoundEvents.AMETHYST_BLOCK_BREAK, 1.0f, 1.5f);
		}
	}

	private static void giantTick(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		applyForm(p, Form.GIANT);
		itemMagnet(p, 15.0);
		stepOn(p);
		ctx.addResource("giant_form", -STRAIN_DRAIN, MAX_STRAIN);
		if (ctx.resource("giant_form") <= 0.0f) {
			ctx.setToggled(false);
			clearForm(p);
			ctx.setResource("giant_form", 0, MAX_STRAIN);
			ctx.actionBar("message.projecthero.size.strain_out");
		}
	}

	private static void itemMagnet(ServerPlayer p, double r) {
		if (!(p.level() instanceof ServerLevel sl)) {
			return;
		}
		Vec3 to = p.position().add(0, p.getBbHeight() * 0.3, 0);
		for (ItemEntity item : sl.getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(r))) {
			Vec3 dir = to.subtract(item.position());
			if (dir.lengthSqr() > 1.0) {
				item.setDeltaMovement(item.getDeltaMovement().add(dir.normalize().scale(0.4)));
				item.setPickUpDelay(0);
			}
		}
	}

	private static void stepOn(ServerPlayer p) {
		double foot = p.getBbWidth() * 0.6 + 1.0;
		float dmg = 9.6f;
		for (LivingEntity e : AbilityHelpers.hostilesAround(p, p.position(), foot)) {
			if (e.getBbHeight() < p.getBbHeight() * 0.5 && e.invulnerableTime <= 0) {
				AbilityHelpers.hurt(p, e, dmg);
				AbilityHelpers.knockbackFrom(e, p.position(), 0.8);
			}
		}
	}
}
