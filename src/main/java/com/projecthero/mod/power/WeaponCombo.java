package com.projecthero.mod.power;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.item.ModItems;
import com.projecthero.mod.squad.Squads;
import com.projecthero.mod.titanshifter.TitanCombat;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.20: the 3-hit melee combo shared by Mjolnir and Stormbreaker. Every full-strength melee hit (left click, attack
 * strength at least {@link #MIN_STRENGTH}) that lands with one of them in the main hand is the next step:
 * <ol>
 *   <li>a horizontal swing;</li>
 *   <li>a backhand on the opposite diagonal;</li>
 *   <li>the finisher, a heavy overhead slam: +{@link #FINISHER_BONUS} x the hit's damage on the target, and an area hit
 *       around it -- Mjolnir a thunder-crack shockwave ring ({@link #MJOLNIR_SHOCK_RADIUS} blocks, sparks + thunder),
 *       Stormbreaker a wide cleave through everything in front ({@link #STORM_CLEAVE_RADIUS} blocks, 150 degrees).
 *       The combo then starts over.</li>
 * </ol>
 * The next hit must come within {@link #COMBO_WINDOW_TICKS} ticks of the swing being recharged
 * ({@link #windowTicks}) or the combo starts over at the first swing. A weak, spam-clicked hit neither advances nor
 * resets it (and never gets the finisher); a miss doesn't touch it. The base weapon damage is never changed.
 *
 * <p>Hooked from {@code PlayerAttackComboMixin} around {@code Player.attack}: the attack strength is read there before
 * vanilla resets it, the hit is confirmed through {@code AFTER_DAMAGE} (so a shield-blocked or cancelled hit doesn't
 * count), and the step is resolved when the attack returns. Every counted step is written to the synced
 * {@link ModAttachments#WEAPON_COMBO}, which is both the combo's own state and what every viewer's swing pose
 * ({@code WeaponComboPose}) plays from. The finisher's area hit never touches the wielder, their pets or squad allies.
 */
public final class WeaponCombo {
	public static final int HITS = 3;
	/** 1.25 s after the swing has recharged to land the next step. */
	public static final int COMBO_WINDOW_TICKS = 25;
	/** Attack strength (0..1) a hit needs to count. */
	public static final float MIN_STRENGTH = 0.9f;
	/** The finisher's extra damage on the target, as a fraction of the hit's own damage (+50%). */
	public static final float FINISHER_BONUS = 0.5f;

	public static final double MJOLNIR_SHOCK_RADIUS = 3.0;
	public static final float MJOLNIR_SHOCK_DAMAGE = 4.0f;
	public static final double MJOLNIR_SHOCK_KNOCKBACK = 0.8;
	public static final double MJOLNIR_SHOCK_LIFT = 0.25;

	public static final double STORM_CLEAVE_RADIUS = 4.5;
	/** cos(75 degrees): the cleave reaches 75 degrees either side of where the wielder faces. */
	public static final double STORM_CLEAVE_MIN_DOT = 0.2588;
	public static final float STORM_CLEAVE_DAMAGE = 6.0f;
	public static final double STORM_CLEAVE_KNOCKBACK = 0.6;

	/** Swing animation lengths in ticks (the client pose tables end here too). */
	public static final int SWING_TICKS = 11;
	public static final int FINISHER_TICKS = 15;

	private static final class Ctx {
		final ServerPlayer player;
		final LivingEntity target;
		final int weapon;
		final boolean strong;
		boolean landed;
		float damage;

		Ctx(ServerPlayer player, LivingEntity target, int weapon, boolean strong) {
			this.player = player;
			this.target = target;
			this.weapon = weapon;
			this.strong = strong;
		}
	}

	private static final ThreadLocal<Ctx> CTX = new ThreadLocal<>();

	private WeaponCombo() {
	}

	public static void initialize() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			Ctx c = CTX.get();
			if (c != null && entity == c.target && !blocked && base > 0.0f && source.getEntity() == c.player
					&& source.getDirectEntity() == c.player) {
				c.landed = true;
				c.damage = base;
			}
		});
	}

	/** Which combo weapon {@code stack} is ({@link WeaponComboState#WEAPON_NONE} for anything else). */
	public static int weaponOf(ItemStack stack) {
		if (stack.is(ModItems.MJOLNIR)) {
			return WeaponComboState.WEAPON_MJOLNIR;
		}
		if (stack.is(ModItems.STORMBREAKER)) {
			return WeaponComboState.WEAPON_STORMBREAKER;
		}
		return WeaponComboState.WEAPON_NONE;
	}

	/** Ticks after the last counted step that the next one may still land: the swing's recharge + 1.25 s. */
	public static int windowTicks(Player player) {
		return Mth.ceil(player.getCurrentItemAttackStrengthDelay()) + COMBO_WINDOW_TICKS;
	}

	/**
	 * The step a counted hit at {@code now} with {@code weapon} would be, after {@code last}. Shared by the server and
	 * the client's swing prediction.
	 */
	public static int nextStep(WeaponComboState last, int weapon, long now, int window) {
		if (last.step() <= 0 || last.step() >= HITS || last.weapon() != weapon || now - last.start() > window
				|| now < last.start()) {
			return 1;
		}
		return last.step() + 1;
	}

	public static WeaponComboState state(Player player) {
		return player.getAttachedOrElse(ModAttachments.WEAPON_COMBO, WeaponComboState.EMPTY);
	}

	/** The step the last counted hit was (1..3), or 0 if there is none or its window has run out. For tests / HUD. */
	public static int step(Player player) {
		WeaponComboState s = state(player);
		long now = player.level().getGameTime();
		return s.step() <= 0 || now - s.start() > windowTicks(player) || now < s.start() ? 0 : s.step();
	}

	// ---------------- the hooks around Player.attack ----------------

	/** {@code Player.attack} HEAD (server only): note the attack and its strength before vanilla resets it. */
	public static void beforeAttack(ServerPlayer player, Entity target) {
		int weapon = weaponOf(player.getMainHandItem());
		if (weapon == WeaponComboState.WEAPON_NONE || !(target instanceof LivingEntity living)) {
			CTX.remove();
			return;
		}
		CTX.set(new Ctx(player, living, weapon, player.getAttackStrengthScale(0.5f) >= MIN_STRENGTH));
	}

	/** {@code Player.attack} RETURN (server only): if the hit landed at full strength, it is the combo's next step. */
	public static void afterAttack(ServerPlayer player) {
		Ctx c = CTX.get();
		if (c == null || c.player != player) {
			return;
		}
		CTX.remove();
		if (!c.landed || !c.strong) {
			return;
		}
		long now = player.level().getGameTime();
		int step = nextStep(state(player), c.weapon, now, windowTicks(player));
		player.setAttached(ModAttachments.WEAPON_COMBO, new WeaponComboState(step, c.weapon, now));
		if (step >= HITS) {
			finisher(player, c);
		} else {
			swingFx(player, c, step);
		}
	}

	private static void swingFx(ServerPlayer player, Ctx c, int step) {
		ServerLevel level = player.serverLevel();
		Vec3 at = c.target.position().add(0, c.target.getBbHeight() * 0.5, 0);
		level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		if (c.weapon == WeaponComboState.WEAPON_MJOLNIR) {
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 4 + 3 * step, 0.25, 0.3, 0.25, 0.08);
		}
		level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.8f,
				step == 1 ? 1.0f : 0.85f);
	}

	private static void finisher(ServerPlayer player, Ctx c) {
		ServerLevel level = player.serverLevel();
		LivingEntity target = c.target;
		// +50% on the target. It was hurt a moment ago, so it is inside its hurt cooldown: vanilla then applies only
		// what a bigger hit exceeds the last one by -- exactly the bonus, and for player targets too.
		if (target.isAlive()) {
			DamageSource source = player.damageSources().playerAttack(player);
			target.hurt(source, c.damage * (1.0f + FINISHER_BONUS));
		}
		Vec3 at = target.position().add(0, target.getBbHeight() * 0.5, 0);
		if (c.weapon == WeaponComboState.WEAPON_MJOLNIR) {
			Vec3 ground = target.position();
			for (LivingEntity e : AbilityHelpers.enemiesAround(player, ground, MJOLNIR_SHOCK_RADIUS)) {
				if (e == target || friendly(player, e)) {
					continue;
				}
				if (AbilityHelpers.hurt(player, e, MJOLNIR_SHOCK_DAMAGE)) {
					knock(e, ground, MJOLNIR_SHOCK_KNOCKBACK, MJOLNIR_SHOCK_LIFT);
				}
			}
			for (int i = 0; i < 24; i++) {
				double a = i * (Math.PI * 2.0 / 24.0);
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, ground.x + Math.cos(a) * MJOLNIR_SHOCK_RADIUS * 0.8,
						ground.y + 0.15, ground.z + Math.sin(a) * MJOLNIR_SHOCK_RADIUS * 0.8, 2, 0.1, 0.05, 0.1, 0.05);
			}
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 30, 0.35, 0.5, 0.35, 0.25);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.55f, 1.7f);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.MACE_SMASH_GROUND, SoundSource.PLAYERS, 0.9f, 0.9f);
		} else {
			Vec3 origin = player.position();
			Vec3 facing = player.getLookAngle().multiply(1.0, 0.0, 1.0);
			facing = facing.lengthSqr() < 1.0e-6 ? Vec3.ZERO : facing.normalize();
			for (LivingEntity e : AbilityHelpers.enemiesAround(player, origin.add(0, 1.0, 0), STORM_CLEAVE_RADIUS)) {
				if (e == target || friendly(player, e) || !inCleave(origin, facing, e)) {
					continue;
				}
				if (AbilityHelpers.hurt(player, e, STORM_CLEAVE_DAMAGE)) {
					knock(e, origin, STORM_CLEAVE_KNOCKBACK, 0.1);
				}
			}
			// the arc of the cleave
			float yaw = player.getYRot() * Mth.DEG_TO_RAD;
			for (int i = -5; i <= 5; i++) {
				double a = yaw + i * (75.0 / 5.0) * Mth.DEG_TO_RAD;
				double r = STORM_CLEAVE_RADIUS * 0.7;
				level.sendParticles(ParticleTypes.SWEEP_ATTACK, origin.x - Math.sin(a) * r, origin.y + 1.0,
						origin.z + Math.cos(a) * r, 1, 0.0, 0.0, 0.0, 0.0);
			}
			level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 18, 0.35, 0.35, 0.35, 0.35);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 10, 0.3, 0.3, 0.3, 0.1);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0f, 0.7f);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.MACE_SMASH_AIR, SoundSource.PLAYERS, 0.9f, 0.8f);
		}
		knock(target, player.position(), 0.6, 0.15);
	}

	/** In front of {@code origin} within the cleave's arc (the radius is already applied). */
	static boolean inCleave(Vec3 origin, Vec3 facing, LivingEntity e) {
		if (facing == Vec3.ZERO) {
			return true;
		}
		Vec3 to = new Vec3(e.getX() - origin.x, 0.0, e.getZ() - origin.z);
		double len = to.length();
		return len < 0.75 || to.scale(1.0 / len).dot(facing) >= STORM_CLEAVE_MIN_DOT;
	}

	/** Never the wielder, one of their pets or a squad ally. */
	public static boolean friendly(ServerPlayer player, LivingEntity target) {
		if (target == player) {
			return true;
		}
		if (com.projecthero.mod.combat.HeroTargets.isFriendlyPet(player, target)) { // v0.15.11: + squadmates' pets
			return true;
		}
		return Squads.areAllies(player, target);
	}

	private static void knock(LivingEntity target, Vec3 origin, double strength, double lift) {
		if (TitanCombat.isBoss(target) || !target.isAlive()) {
			return;
		}
		AbilityHelpers.knockbackFrom(target, origin, strength);
		if (lift > 0.0) {
			AbilityHelpers.push(target, new Vec3(0.0, lift, 0.0));
		}
	}

	/** Test hook: put the combo at {@code step}, counted {@code ticksAgo} ago, with {@code weapon}. */
	public static void setForTests(Player player, int step, int weapon, long ticksAgo) {
		player.setAttached(ModAttachments.WEAPON_COMBO,
				new WeaponComboState(step, weapon, player.level().getGameTime() - ticksAgo));
	}
}
