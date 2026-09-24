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

	private static final float MAX_STRAIN = 500.0f;
	private static final float STRAIN_DRAIN = MAX_STRAIN / (23 * 20); // full Giant form lasts ~23 s
	private static final float STRAIN_REGEN = MAX_STRAIN / (45 * 20); // and recovers slowly over ~45 s once out
	private static final float STRAIN_MIN_ENTER = 60.0f; // need a little in the tank to go Giant again

	private enum Form { NORMAL, TINY, LARGE, GIANT }

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
			case LARGE -> 5.0;
			case GIANT -> 15.0;
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
			case GIANT -> 15.0f;
			case LARGE -> 5.0f;
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
			float punch = (10.0f + formDamageBonus(p)) * formDamageMult(p);
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
			float stompDmg = (8.0f + formDamageBonus(p)) * formDamageMult(p);
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
			p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 3, false, false, false));
			p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 15, 2, false, false, false));
			AbilityHelpers.burst(ctx.level(), p.position(), ParticleTypes.POOF, 12, 0.2);
			ctx.triggerCooldown();
		}));

		com.projecthero.mod.hero.PowerPassives.register(KEY, (player, active) -> {
			if (!active) {
				clearFormNow(player);
			}
		});
		com.projecthero.mod.hero.PowerPassives.registerTick(KEY, player -> {
			tickScale(player);
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
		return true;
	}

	/** Leave the current form back to normal, with an imploding-particle flourish. */
	private static void exitForm(ServerPlayer p) {
		Form was = currentForm(p);
		clearForm(p);
		sizeChangeFx(p, was, Form.NORMAL);
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
		float dmg = 8.0f;
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position(), foot)) {
			if (e.getBbHeight() < p.getBbHeight() * 0.5 && e.invulnerableTime <= 0) {
				AbilityHelpers.hurt(p, e, dmg);
				AbilityHelpers.knockbackFrom(e, p.position(), 0.8);
			}
		}
	}
}
