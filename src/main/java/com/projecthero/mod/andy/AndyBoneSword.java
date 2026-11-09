package com.projecthero.mod.andy;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.andy.data.AndyState;
import com.projecthero.mod.andy.entity.AndyCloneEntity;
import com.projecthero.mod.andy.entity.AndyPartEntity;
import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.PowerToggles;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * v0.17.3 (user: "add an ability for shift+V where the player rips an arm off and uses it as a bone sword with the accents being
 * blood and the guard of the sword should be blood too"; "make the players moveset change when they are using the bone sword"):
 * Shift+V tears off his off arm and holds it in the main hand as a sword of bone with a crossguard of congealed blood. The arm
 * does not grow back while the sword is out ({@link AndyState#boneSwordArm}, checked by {@code Andy.scheduleRegrow}); putting it
 * away (Shift+V, {@link AndyConfig#BONE_SWORD_MAX}, losing the hand, death) bursts it into blood and bone and the arm regrows.
 *
 * <p>While it is out his keys are the sword kit ({@link #idsOf}):
 * <pre>
 *   R      Bone Slash (a three-hit combo, the third a wide sweep)   Shift+R  Marrow Thrust (a lunging stab)
 *   G      Blood Edge Wave (a crescent of blood thrown forward)     Shift+G  Rib Cage Guard (ribs spring up: a block that reflects)
 *   X      Blood-jet Dash (dash along the aim, slicing through)
 *   Z      Bone Splinter Volley (shards in a cone)                  Shift+Z  Impale Throw (the sword thrown; it is gone after)
 *   V      Regeneration speed (as ever)                             Shift+V  put the sword away
 *   C      Parry (a short window; a melee hit in it is countered)
 * </pre>
 * Left click is a sword: {@link AndyConfig#BONE_SWORD_DAMAGE}, a sweep and a longer reach.
 */
public final class AndyBoneSword {
	public static final String BONE_SWORD = "bone_sword";
	public static final String SLASH = "bone_slash";
	public static final String THRUST = "marrow_thrust";
	public static final String EDGE = "blood_edge_wave";
	public static final String GUARD = "rib_cage_guard";
	public static final String SPLINTER = "bone_splinter_volley";
	public static final String IMPALE = "impale_throw";
	public static final String DASH = "blood_jet_dash";
	public static final String PARRY = "bone_parry";
	/** v0.17.3: V while the sword is out (the HUD and move list name). */
	public static final String AWAY = "bone_sword_away";

	// ---------------------------------------------------------------- the kit's numbers
	public static final float[] SLASH_DAMAGE = { 8f, 8f, 12f };
	public static final double SLASH_REACH = 3.6;
	public static final int SLASH_COOLDOWN = 7;
	public static final int SLASH_FINISH_COOLDOWN = 14;
	/** The next press within this long carries the combo on. */
	public static final int COMBO_WINDOW = 30;
	public static final float THRUST_DAMAGE = 14f;
	public static final int THRUST_TICKS = 3;
	public static final double THRUST_SPEED = 1.1;
	public static final int THRUST_COOLDOWN = 60;
	public static final float EDGE_DAMAGE = 12f;
	public static final double EDGE_RANGE = 12.0;
	public static final double EDGE_SPEED = 1.1;
	public static final int EDGE_COOLDOWN = 100;
	public static final int GUARD_TICKS = 30;
	public static final int GUARD_COOLDOWN = 160;
	/** v0.17.3: a fleck of bone (Rib Cage Guard blocks): small, so nothing big pops up in front of the camera. */
	public static final DustParticleOptions BONE_CHIP = new DustParticleOptions(new Vector3f(0.90f, 0.87f, 0.78f), 0.45f);
	/** v0.17.3: a small fleck of blood (Rib Cage Guard blocks; {@link Andy#BLOOD} is too big that close to the camera). */
	public static final DustParticleOptions BLOOD_FLECK = new DustParticleOptions(new Vector3f(0.72f, 0.03f, 0.06f), 0.5f);
	public static final int SPLINTERS = 7;
	public static final float SPLINTER_DAMAGE = 5f;
	public static final double SPLINTER_SPEED = 2.2;
	public static final int SPLINTER_COOLDOWN = 120;
	public static final float IMPALE_DAMAGE = 18f;
	public static final double IMPALE_SPEED = 2.0;
	public static final int IMPALE_PIN_TICKS = 40;
	public static final float DASH_DAMAGE = 10f;
	public static final int DASH_TICKS = 5;
	public static final double DASH_SPEED = 1.5;
	public static final int DASH_COOLDOWN = 50;
	public static final int PARRY_TICKS = 12;
	public static final float PARRY_COUNTER_DAMAGE = 14f;
	public static final int PARRY_COOLDOWN = 60;
	/** A landed parry leaves only this much of the cooldown. */
	public static final int PARRY_REFUND_COOLDOWN = 10;

	private static final ResourceLocation DAMAGE_ID = PowerToggles.id("andy_bone_sword_damage");
	private static final ResourceLocation REACH_ID = PowerToggles.id("andy_bone_sword_reach");
	private static final ResourceLocation SPEED_ID = PowerToggles.id("andy_bone_sword_speed");

	/** A lunge (Marrow Thrust, Blood-jet Dash): along {@code dir} for {@code ticks}, hitting what it passes once each. */
	private record Lunge(Vec3 dir, long start, int ticks, double speed, float damage, Set<Integer> hit) {
	}

	/** Blood Edge Wave: where it runs from, which way, since when and who it hit. */
	private record Wave(Vec3 from, Vec3 dir, long start, Set<Integer> hit) {
	}

	/** player -> {combo step, when}. */
	private static final Map<UUID, long[]> COMBO = new ConcurrentHashMap<>();
	private static final Map<UUID, Lunge> LUNGES = new ConcurrentHashMap<>();
	private static final Map<UUID, Wave> WAVES = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> GUARD_UNTIL = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> PARRY_UNTIL = new ConcurrentHashMap<>();

	private AndyBoneSword() {
	}

	public static void initialize() {
		// left click with the sword out: a sword's sweep round the target, and a wet slash
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (!world.isClientSide() && player instanceof ServerPlayer sp && entity instanceof LivingEntity target && held(sp)
					&& !AndyMod.handGone(sp, hand)) {
				melee(sp, target, sp.getAttackStrengthScale(0.5f));
			}
			return InteractionResult.PASS;
		});
	}

	static void clearSessionState() {
		COMBO.clear();
		LUNGES.clear();
		WAVES.clear();
		GUARD_UNTIL.clear();
		PARRY_UNTIL.clear();
	}

	static void forget(UUID id) {
		COMBO.remove(id);
		LUNGES.remove(id);
		WAVES.remove(id);
		GUARD_UNTIL.remove(id);
		PARRY_UNTIL.remove(id);
	}

	/** Death, a world change, leaving, the shatter: the sword bursts (the arm grows back) and every running sword move ends. */
	static void clear(ServerPlayer p) {
		forget(p.getUUID());
		AndyState s = Andy.peek(p);
		if (s != null && s.boneSword()) {
			putAway(p, true, false);
		}
	}

	// ---------------------------------------------------------------- state

	/** The Bone Sword is out (client-safe). */
	public static boolean held(Player p) {
		AndyState s = Andy.peek(p);
		return s != null && s.hasPower && s.boneSword();
	}

	/** The hand holding the sword: the arm that was not torn off. */
	public static int hand(AndyState s) {
		return Andy.otherArm(s.boneSwordArm());
	}

	public static boolean guarding(ServerPlayer p) {
		Long u = GUARD_UNTIL.get(p.getUUID());
		return u != null && p.level().getGameTime() < u;
	}

	/** Gametests: drop the Rib Cage Guard early. */
	public static void forTestsEndGuard(ServerPlayer p) {
		GUARD_UNTIL.remove(p.getUUID());
	}

	public static boolean parrying(ServerPlayer p) {
		Long u = PARRY_UNTIL.get(p.getUUID());
		return u != null && p.level().getGameTime() < u;
	}

	/** From {@code Andy.reconcile}: the sword's damage, reach and speed while it is out. */
	static void attributes(ServerPlayer p, AndyState s) {
		if (s != null && s.hasPower && s.boneSword()) {
			PowerToggles.modifier(p, Attributes.ATTACK_DAMAGE, DAMAGE_ID, AndyConfig.BONE_SWORD_DAMAGE - 1.0 - AndyConfig.BLOODY_ARTS_MELEE,
					AttributeModifier.Operation.ADD_VALUE);
			PowerToggles.modifier(p, Attributes.ENTITY_INTERACTION_RANGE, REACH_ID, AndyConfig.BONE_SWORD_REACH_BONUS, AttributeModifier.Operation.ADD_VALUE);
			PowerToggles.modifier(p, Attributes.ATTACK_SPEED, SPEED_ID, AndyConfig.BONE_SWORD_ATTACK_SPEED - 4.0, AttributeModifier.Operation.ADD_VALUE);
		} else {
			PowerToggles.clearModifier(p, Attributes.ATTACK_DAMAGE, DAMAGE_ID);
			PowerToggles.clearModifier(p, Attributes.ENTITY_INTERACTION_RANGE, REACH_ID);
			PowerToggles.clearModifier(p, Attributes.ATTACK_SPEED, SPEED_ID);
		}
	}

	// ---------------------------------------------------------------- keys

	/** The move ids on a slot while the sword is out (plain, Shift); null when it is not out. */
	public static String[] idsOf(Player player, AbilitySlot slot) {
		if (!held(player)) {
			return null;
		}
		return switch (slot) {
			case SLOT_1 -> new String[] { SLASH, THRUST };
			case SLOT_2 -> new String[] { EDGE, GUARD };
			case SLOT_3 -> new String[] { DASH };
			case SLOT_4 -> new String[] { SPLINTER, IMPALE };
			case SLOT_5 -> new String[] { AWAY, AndyAbilities.REGEN };
			case SLOT_6 -> new String[] { PARRY };
			default -> new String[0];
		};
	}

	public static int maxCooldown(String id) {
		return switch (id) {
			case BONE_SWORD -> AndyConfig.BONE_SWORD_COOLDOWN;
			case SLASH -> SLASH_FINISH_COOLDOWN;
			case THRUST -> THRUST_COOLDOWN;
			case EDGE -> EDGE_COOLDOWN;
			case GUARD -> GUARD_COOLDOWN;
			case SPLINTER -> SPLINTER_COOLDOWN;
			case IMPALE -> AndyConfig.BONE_SWORD_COOLDOWN;
			case DASH -> DASH_COOLDOWN;
			case PARRY -> PARRY_COOLDOWN;
			default -> -1;
		};
	}

	/** A key while the sword is out; true if the sword kit took it. V puts the sword away; Shift+V is the regeneration speed. */
	static boolean handle(ServerPlayer p, AbilitySlot slot, boolean pressed, boolean shift) {
		switch (slot) {
			case SLOT_1 -> {
				if (pressed) {
					if (shift) {
						thrust(p);
					} else {
						slash(p);
					}
				}
				return true;
			}
			case SLOT_2 -> {
				if (pressed) {
					if (shift) {
						guard(p);
					} else {
						edgeWave(p);
					}
				}
				return true;
			}
			case SLOT_3 -> {
				if (pressed) {
					dash(p);
				}
				return true;
			}
			case SLOT_4 -> {
				if (pressed) {
					if (shift) {
						impale(p);
					} else {
						splinters(p);
					}
				}
				return true;
			}
			case SLOT_5 -> {
				// v0.17.3: V puts it away, Shift+V is still the regeneration speed
				if (pressed) {
					if (shift) {
						if (Andy.canAct(p)) {
							Andy.cycleSpeed(p);
						}
					} else {
						putAway(p, true, true);
					}
				}
				return true;
			}
			case SLOT_6 -> {
				if (pressed) {
					parry(p);
				}
				return true;
			}
			default -> {
				return false;
			}
		}
	}

	// ---------------------------------------------------------------- hold V: drawing; V again: putting away

	/** Draws the sword at once (no struggle), or puts it away (commands, tests). */
	public static void toggle(ServerPlayer p) {
		if (held(p)) {
			putAway(p, true, true);
		} else {
			draw(p);
		}
	}

	/**
	 * Whether he can draw the sword now: not cooling down, one arm to tear off and one to hold it, a body. If not and {@code say},
	 * a line and a dull click say why.
	 */
	public static boolean canDraw(ServerPlayer p, boolean say) {
		if (!Andy.canAct(p)) {
			return false;
		}
		int cd = Andy.cooldownRemaining(p, BONE_SWORD);
		if (cd > 0) {
			if (say) {
				Andy.say(p, "message.projecthero.andy.cooldown", ChatFormatting.GRAY, Component.translatable("projecthero.andy.ability." + BONE_SWORD),
						String.format(java.util.Locale.ROOT, "%.1f", cd / 20.0f));
			}
			return false;
		}
		AndyState s = Andy.state(p);
		long now = p.level().getGameTime();
		int hand = Andy.mainArm(p);
		int arm = Andy.otherArm(hand);
		if (!s.armReady(hand, now) || !s.armReady(arm, now) || s.broken(AndyState.BODY)) {
			if (say) {
				Andy.say(p, "message.projecthero.andy.no_spare_arm", ChatFormatting.GRAY);
				sound(p, SoundEvents.DISPENSER_FAIL, 0.6f, 1.4f);
			}
			return false;
		}
		return true;
	}

	/**
	 * Hold V, past the tap: his sword hand reaches over, grabs the off arm at the shoulder and starts wrenching at it
	 * ({@link AndyState#ANIM_SWORD_STRUGGLE}). False (having said why) if he can't draw the sword.
	 */
	public static boolean startStruggle(ServerPlayer p) {
		if (!canDraw(p, true)) {
			return false;
		}
		AndyAbilities.cancelPunch(p);
		long now = p.level().getGameTime();
		AndyState n = Andy.state(p).copy();
		n.animId = AndyState.ANIM_SWORD_STRUGGLE;
		n.animStart = now;
		Andy.save(p, n);
		AndySfx.punchStretch(level(p), AndyParts.centre(p, Andy.otherArm(Andy.mainArm(p))));
		return true;
	}

	/** Every tick of the struggle ({@code held}: ticks V has been down): the sinew stretching and a trickle at the shoulder. */
	public static void struggling(ServerPlayer p, long held) {
		if (!canDraw(p, false)) {
			cancelStruggle(p); // lost an arm / the body meanwhile
			return;
		}
		long t = held - AndyConfig.BONE_SWORD_TAP;
		Vec3 at = AndyParts.centre(p, Andy.otherArm(Andy.mainArm(p))).add(0, 0.25, 0);
		// the wrenches come quicker as it gives
		if (t == 9 || t == 18 || t == 25 || t == 31) {
			AndySfx.punchStretch(level(p), at);
		}
		if (t == 22) {
			sound(p, SoundEvents.SKELETON_HURT, 0.35f, 0.55f); // a crack at the shoulder
		}
		if (t > 14 && t % 3 == 0) {
			level(p).sendParticles(Andy.BLOOD, at.x, at.y, at.z, 1, 0.06, 0.06, 0.06, 0.0);
		}
	}

	/** V let go before the arm came off: he lets go of it. */
	public static void cancelStruggle(ServerPlayer p) {
		AndyState s = Andy.state(p);
		if (s.animId != AndyState.ANIM_SWORD_STRUGGLE) {
			return;
		}
		AndyState n = s.copy();
		n.animId = AndyState.ANIM_NONE;
		Andy.save(p, n);
	}

	/**
	 * Tears off the off arm and holds it as the sword (the end of the hold). The arm comes away in his hand; he grips it and
	 * {@link AndyConfig#BONE_SWORD_REVEAL} later it bursts round the blade ({@link #tick}). False (with a line) if he can't.
	 */
	public static boolean draw(ServerPlayer p) {
		if (!canDraw(p, true)) {
			return false;
		}
		AndyState s = Andy.state(p);
		long now = p.level().getGameTime();
		int arm = Andy.otherArm(Andy.mainArm(p));
		Vec3 at = AndyParts.centre(p, arm);
		AndyAbilities.cancelPunch(p);
		Andy.breakPart(p, arm, AndyState.KIND_LAUNCHED); // no pixel burst: the arm is in his hand
		AndyState n = Andy.state(p).copy();
		n.setBoneSword(arm, now);
		n.animId = AndyState.ANIM_SWORD_RIP;
		n.animStart = now;
		Andy.save(p, n);
		COMBO.remove(p.getUUID());
		Andy.reconcile(p);
		ServerLevel level = level(p);
		AndySfx.partBreak(level, at);
		AndySfx.bloodBurst(level, at, 0.6f);
		sound(p, SoundEvents.SKELETON_HURT, 0.5f, 0.6f);
		return true;
	}

	/** {@link AndyConfig#BONE_SWORD_REVEAL} after the rip: the arm bursts in his grip and the sword is left. */
	private static void reveal(ServerPlayer p, AndyState s) {
		ServerLevel level = level(p);
		Vec3 at = AndyAbilities.handOrigin(p, hand(s)).add(p.getLookAngle().scale(0.35));
		AndySfx.bloodBurst(level, at, 0.8f);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.BONE_BLOCK_BREAK, SoundSource.PLAYERS, 1.0f, 0.75f);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.SKELETON_HURT, SoundSource.PLAYERS, 0.6f, 0.5f);
	}

	// ---------------------------------------------------------------- tap V: Tear Off

	/**
	 * Tap V: Tear Off -- he wrenches off his most damaged limb (the off arm when none is hurt) and flings it aside, and with it
	 * goes whatever is on him: the fire, poison, wither, slowness and every other harmful effect. It regrows as ever (and is paid
	 * for like any regrowth). He needs a hand to do it with, and never tears off the last one.
	 */
	public static boolean tearOff(ServerPlayer p) {
		if (!Andy.canAct(p)) {
			return false;
		}
		String id = AndyAbilities.TEAR_OFF;
		int cd = Andy.cooldownRemaining(p, id);
		if (cd > 0) {
			Andy.say(p, "message.projecthero.andy.cooldown", ChatFormatting.GRAY, Component.translatable("projecthero.andy.ability." + id),
					String.format(java.util.Locale.ROOT, "%.1f", cd / 20.0f));
			return false;
		}
		int limb = tearOffTarget(p);
		if (limb < 0) {
			Andy.say(p, "message.projecthero.andy.nothing_to_tear", ChatFormatting.GRAY);
			sound(p, SoundEvents.DISPENSER_FAIL, 0.6f, 1.4f);
			return false;
		}
		long now = p.level().getGameTime();
		ServerLevel level = level(p);
		Vec3 at = AndyParts.centre(p, limb);
		AndyAbilities.cancelPunch(p);
		Andy.breakPart(p, limb, AndyState.KIND_LAUNCHED); // no pixel burst: the limb goes flying whole
		AndyState n = Andy.state(p).copy();
		n.abilityReadyAt.put(id, now + AndyConfig.TEAR_OFF_COOLDOWN);
		n.animId = AndyState.ANIM_TEAR_OFF;
		n.animStart = now;
		n.setEx(AndyState.X_TORN, limb);
		Andy.save(p, n);
		// the hand that does it swings (first person sees the wrench as the arm's swing)
		p.swing(limb == Andy.mainArm(p) ? net.minecraft.world.InteractionHand.OFF_HAND : net.minecraft.world.InteractionHand.MAIN_HAND, true);
		// the limb, flung out to his side
		Vec3 side = AndyParts.right(p).scale(limb == AndyState.RIGHT_ARM || limb == AndyState.RIGHT_LEG ? 1 : -1);
		Vec3 fling = side.scale(0.2).add(flatLook(p).scale(0.08)).add(0, 0.25, 0);
		AndyPartEntity.drop(p, limb, AndyPartEntity.STYLE_PUNCH, at.subtract(0, 0.35, 0), p.getYRot(), fling);
		// and everything on him with it
		boolean cleansed = p.isOnFire();
		p.clearFire();
		for (MobEffectInstance e : List.copyOf(p.getActiveEffects())) {
			if (e.getEffect().value().getCategory() == net.minecraft.world.effect.MobEffectCategory.HARMFUL) {
				p.removeEffect(e.getEffect());
				cleansed = true;
			}
		}
		AndySfx.partBreak(level, at);
		AndySfx.bloodBurst(level, at, 0.5f);
		sound(p, SoundEvents.SKELETON_HURT, 0.45f, 0.6f);
		if (cleansed) {
			level.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 6, 0.15, 0.15, 0.15, 0.01);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5f, 1.4f);
		}
		return true;
	}

	/** The limb Tear Off takes: the most damaged arm or leg he has, else the off arm; never his last hand. -1: none. */
	public static int tearOffTarget(ServerPlayer p) {
		AndyState s = Andy.state(p);
		long now = p.level().getGameTime();
		int main = Andy.mainArm(p), off = Andy.otherArm(main);
		boolean mainOk = s.armReady(main, now), offOk = s.armReady(off, now);
		if (!mainOk && !offOk) {
			return -1; // nothing to tear it off with
		}
		int best = -1;
		float worst = 2f;
		for (int part = AndyState.RIGHT_ARM; part <= AndyState.LEFT_LEG; part++) {
			if (s.broken(part)) {
				continue;
			}
			boolean arm = part == main || part == off;
			if (arm && !(mainOk && offOk)) {
				continue; // the only hand he has stays
			}
			float frac = s.hp(part) / AndyConfig.PART_HP[part];
			if (frac < 0.999f && frac < worst) {
				worst = frac;
				best = part;
			}
		}
		if (best >= 0) {
			return best;
		}
		if (mainOk && offOk) {
			return off;
		}
		for (int part = AndyState.RIGHT_LEG; part <= AndyState.LEFT_LEG; part++) {
			if (!s.broken(part)) {
				return part; // one arm left and nothing hurt: a leg
			}
		}
		return -1;
	}

	/**
	 * Puts the sword away: it crumbles into blood and bone bits ({@code burst}) and the torn-off arm grows back as usual (bone,
	 * muscle, skin). {@code cooldown}: the {@link AndyConfig#BONE_SWORD_COOLDOWN} before it can be drawn again.
	 */
	public static void putAway(ServerPlayer p, boolean burst, boolean cooldown) {
		AndyState s = Andy.state(p);
		if (!s.boneSword()) {
			return;
		}
		long now = p.level().getGameTime();
		int hand = hand(s);
		AndyState n = s.copy();
		n.setBoneSword(-1, 0L);
		if (burst) {
			n.animId = AndyState.ANIM_SWORD_AWAY;
			n.animStart = now;
		}
		if (cooldown) {
			n.abilityReadyAt.put(BONE_SWORD, now + AndyConfig.BONE_SWORD_COOLDOWN);
		}
		Andy.save(p, n);
		forget(p.getUUID());
		Andy.reconcile(p);
		if (burst) {
			ServerLevel level = level(p);
			Vec3 at = AndyAbilities.handOrigin(p, hand).add(p.getLookAngle().scale(0.4));
			AndySfx.bloodBurst(level, at, 0.5f);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.BONE_BLOCK_BREAK, SoundSource.PLAYERS, 0.9f, 1.2f);
		}
	}

	// ---------------------------------------------------------------- left click

	/** A melee hit with the sword (the damage itself is vanilla's, from {@link #attributes}): the sweep, blood and sound. */
	public static void melee(ServerPlayer p, LivingEntity target, float strength) {
		ServerLevel level = level(p);
		Vec3 c = target.position().add(0, target.getBbHeight() * 0.5, 0);
		level.sendParticles(Andy.BLOOD, c.x, c.y, c.z, 5, 0.2, 0.25, 0.2, 0.0);
		AndySfx.splat(level, c);
		if (strength < 0.9f) {
			return;
		}
		sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.9f, 0.85f);
		Vec3 f = flatLook(p);
		level.sendParticles(ParticleTypes.SWEEP_ATTACK, p.getX() + f.x, p.getY() + p.getBbHeight() * 0.5, p.getZ() + f.z, 1, 0, 0, 0, 0);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(1.0, 0.25, 1.0),
				e -> e != p && e != target && !(e instanceof AndyCloneEntity) && HeroTargets.canHarm(p, e)
						&& p.distanceToSqr(e) < Math.pow(p.entityInteractionRange() + 0.5, 2))) {
			AndyAbilities.strike(p, e, p.position(), AndyConfig.BONE_SWORD_SWEEP_DAMAGE, 0.4, 0.05);
		}
	}

	// ---------------------------------------------------------------- the sword kit

	private static boolean begin(ServerPlayer p, String id) {
		if (!held(p) || !Andy.canAct(p)) {
			return false;
		}
		int cd = Andy.cooldownRemaining(p, id);
		if (cd > 0) {
			if (cd > 10) {
				Andy.say(p, "message.projecthero.andy.cooldown", ChatFormatting.GRAY, Component.translatable("projecthero.andy.ability." + id),
						String.format(java.util.Locale.ROOT, "%.1f", cd / 20.0f));
			}
			return false;
		}
		return true;
	}

	private static void cooldownAnim(ServerPlayer p, String id, int ticks, int anim) {
		long now = p.level().getGameTime();
		AndyState n = Andy.state(p).copy();
		n.abilityReadyAt.put(id, now + ticks);
		if (anim >= 0) {
			n.animId = anim;
			n.animStart = now;
		}
		Andy.save(p, n);
	}

	/** R: Bone Slash -- 8, 8, then a wide 12 sweep; the next press within {@link #COMBO_WINDOW} carries it on. */
	public static void slash(ServerPlayer p) {
		if (!begin(p, SLASH)) {
			return;
		}
		long now = p.level().getGameTime();
		long[] c = COMBO.get(p.getUUID());
		int step = c != null && now - c[1] <= COMBO_WINDOW && now >= c[1] ? (int) ((c[0] + 1) % 3) : 0;
		COMBO.put(p.getUUID(), new long[] { step, now });
		boolean finish = step == 2;
		cooldownAnim(p, SLASH, finish ? SLASH_FINISH_COOLDOWN : SLASH_COOLDOWN, AndyState.ANIM_SWORD_SLASH1 + step);
		double reach = SLASH_REACH + (finish ? 0.6 : 0.0);
		Vec3 o = p.position().add(0, 1.0, 0);
		for (LivingEntity e : arc(p, reach, finish ? -0.25 : 0.3)) {
			AndyAbilities.strike(p, e, o, SLASH_DAMAGE[step], finish ? 0.9 : 0.35, finish ? 0.15 : 0.0);
		}
		// the blood arc the blade leaves
		Vec3 f = flatLook(p), r = AndyParts.right(p);
		double span = finish ? 1.9 : 1.1;
		double tilt = step == 0 ? 0.5 : step == 1 ? -0.5 : 0.0;
		for (int i = -6; i <= 6; i++) {
			double a = i / 6.0 * span;
			double side = step == 1 ? -1 : 1;
			Vec3 at = o.add(f.scale(Math.cos(a) * 2.2)).add(r.scale(Math.sin(a) * 2.2 * side)).add(0, Math.sin(a) * tilt * 0.6 + 0.2, 0);
			level(p).sendParticles(Andy.BLOOD, at.x, at.y, at.z, 1, 0.03, 0.03, 0.03, 0.0);
		}
		if (finish) {
			level(p).sendParticles(ParticleTypes.SWEEP_ATTACK, o.x + f.x * 1.6, o.y, o.z + f.z * 1.6, 1, 0, 0, 0, 0);
		}
		sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, finish ? 1.0f : 0.7f, finish ? 0.7f : 1.0f + step * 0.12f);
		AndySfx.swordSwing(level(p), o, finish ? 1f : 0.6f);
	}

	/** Shift+R: Marrow Thrust -- a lunge along the aim, the point first, through everything in the way. */
	public static void thrust(ServerPlayer p) {
		if (!begin(p, THRUST)) {
			return;
		}
		cooldownAnim(p, THRUST, THRUST_COOLDOWN, AndyState.ANIM_SWORD_THRUST);
		Vec3 dir = lungeDir(p);
		LUNGES.put(p.getUUID(), new Lunge(dir, p.level().getGameTime(), THRUST_TICKS, THRUST_SPEED, THRUST_DAMAGE, new HashSet<>()));
		AbilityHelpers.launchSelf(p, dir.scale(THRUST_SPEED));
		tickLunge(p, LUNGES.get(p.getUUID()), p.level().getGameTime()); // the first stab lands at once
		AndySfx.swordSwing(level(p), p.getEyePosition(), 0.9f);
		sound(p, SoundEvents.TRIDENT_THROW.value(), 0.8f, 0.7f);
	}

	/** X: Blood-jet Dash -- a jet of blood out of the stump throws him along the aim, slicing what he passes. */
	public static void dash(ServerPlayer p) {
		if (!begin(p, DASH)) {
			return;
		}
		cooldownAnim(p, DASH, DASH_COOLDOWN, AndyState.ANIM_SWORD_DASH);
		Vec3 dir = lungeDir(p);
		LUNGES.put(p.getUUID(), new Lunge(dir, p.level().getGameTime(), DASH_TICKS, DASH_SPEED, DASH_DAMAGE, new HashSet<>()));
		AbilityHelpers.launchSelf(p, dir.scale(DASH_SPEED));
		tickLunge(p, LUNGES.get(p.getUUID()), p.level().getGameTime());
		AndySfx.jetStart(level(p), p.position().add(0, 1, 0));
		AndySfx.swordSwing(level(p), p.getEyePosition(), 1f);
	}

	private static Vec3 lungeDir(ServerPlayer p) {
		Vec3 look = p.getLookAngle();
		// mostly along the ground: a steep look only tilts it
		Vec3 d = new Vec3(look.x, Math.max(-0.35, Math.min(0.35, look.y)), look.z);
		return d.lengthSqr() < 1.0e-4 ? flatLook(p) : d.normalize();
	}

	private static void tickLunge(ServerPlayer p, Lunge l, long now) {
		long age = now - l.start();
		if (age >= l.ticks()) {
			LUNGES.remove(p.getUUID());
			return;
		}
		if (age > 0) {
			AbilityHelpers.launchSelf(p, l.dir().scale(l.speed()));
		}
		p.resetFallDistance();
		Vec3 c = p.position().add(0, 1.0, 0);
		Vec3 tip = c.add(l.dir().scale(1.4));
		for (LivingEntity e : level(p).getEntitiesOfClass(LivingEntity.class, new AABB(c, tip).inflate(1.1),
				e -> e != p && !(e instanceof AndyCloneEntity) && HeroTargets.canHarm(p, e))) {
			if (l.hit().add(e.getId())) {
				AndyAbilities.strike(p, e, c, l.damage(), 0.6, 0.12);
				AndySfx.swordHit(level(p), e.position().add(0, e.getBbHeight() * 0.5, 0));
			}
		}
		level(p).sendParticles(Andy.BLOOD, c.x - l.dir().x, c.y - 0.3, c.z - l.dir().z, 3, 0.15, 0.2, 0.15, 0.0);
	}

	/** G: Blood Edge Wave -- a swing that throws a crescent of blood {@link #EDGE_RANGE} blocks forward. */
	public static void edgeWave(ServerPlayer p) {
		if (!begin(p, EDGE)) {
			return;
		}
		cooldownAnim(p, EDGE, EDGE_COOLDOWN, AndyState.ANIM_SWORD_EDGE);
		WAVES.put(p.getUUID(), new Wave(p.position().add(0, 1.0, 0), lungeDir(p), p.level().getGameTime(), new HashSet<>()));
		sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 1.1f, 0.6f);
		AndySfx.spray(level(p), p.getEyePosition());
	}

	private static void tickWave(ServerPlayer p, Wave w, long now) {
		long age = now - w.start() - 3; // the swing comes round first
		if (age < 0) {
			return;
		}
		double d = 1.0 + age * EDGE_SPEED;
		if (d > EDGE_RANGE) {
			WAVES.remove(p.getUUID());
			return;
		}
		Vec3 front = w.from().add(w.dir().scale(d));
		Vec3 side = new Vec3(-w.dir().z, 0, w.dir().x).normalize();
		ServerLevel level = level(p);
		double width = 1.6;
		// (the crescent itself and the blood it rains are drawn client-side: AndySwordFx)
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(front, front).inflate(width + 0.4, 1.4, width + 0.4),
				e -> e != p && !(e instanceof AndyCloneEntity) && HeroTargets.canHarm(p, e))) {
			if (w.hit().add(e.getId())) {
				AndyAbilities.strike(p, e, front.subtract(w.dir()), EDGE_DAMAGE, 0.7, 0.15);
				AndySfx.swordHit(level, e.position().add(0, e.getBbHeight() * 0.5, 0));
			}
		}
	}

	/** Shift+G: Rib Cage Guard -- bone ribs spring up round him: hits are blocked and projectiles thrown back for a moment. */
	public static void guard(ServerPlayer p) {
		if (!begin(p, GUARD)) {
			return;
		}
		cooldownAnim(p, GUARD, GUARD_COOLDOWN, AndyState.ANIM_SWORD_GUARD);
		GUARD_UNTIL.put(p.getUUID(), p.level().getGameTime() + GUARD_TICKS);
		sound(p, SoundEvents.SKELETON_STEP, 1.0f, 0.6f);
		sound(p, SoundEvents.BONE_BLOCK_PLACE, 1.0f, 0.7f);
		AndySfx.spray(level(p), p.position().add(0, 1, 0));
	}

	private static void tickGuard(ServerPlayer p) {
		// incoming projectiles bounce back off the ribs, now his
		Vec3 c = p.position().add(0, 1.0, 0);
		for (Projectile pr : level(p).getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(2.6), pr -> pr.getOwner() != p
				&& !(pr instanceof AndyPartEntity))) {
			Vec3 v = pr.getDeltaMovement();
			if (v.lengthSqr() > 0.01 && v.dot(c.subtract(pr.position())) > 0) {
				pr.setDeltaMovement(v.scale(-1.1));
				pr.setOwner(p);
				pr.hurtMarked = true;
				level(p).playSound(null, pr.getX(), pr.getY(), pr.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.8f, 1.3f);
			}
		}
	}

	/** Z: Bone Splinter Volley -- the blade sheds a cone of bone shards. */
	public static void splinters(ServerPlayer p) {
		if (!begin(p, SPLINTER)) {
			return;
		}
		cooldownAnim(p, SPLINTER, SPLINTER_COOLDOWN, AndyState.ANIM_SWORD_SPLINTER);
		AndyState s = Andy.state(p);
		Vec3 from = AndyAbilities.handOrigin(p, hand(s)).add(p.getLookAngle().scale(0.4));
		Vec3 look = AndyAbilities.aimDir(p, from);
		Vec3 r = AndyParts.right(p);
		Vec3 up = r.cross(look).normalize();
		for (int i = 0; i < SPLINTERS; i++) {
			double a = (i - (SPLINTERS - 1) / 2.0) * 0.11;
			double b = (p.getRandom().nextDouble() - 0.5) * 0.08;
			Vec3 v = look.add(r.scale(a)).add(up.scale(b)).normalize().scale(SPLINTER_SPEED);
			AndyPartEntity.shoot(p, AndyPartEntity.STYLE_SPLINTER, -1, from, v, SPLINTER_DAMAGE);
		}
		sound(p, SoundEvents.SKELETON_SHOOT, 1.0f, 0.7f);
		level(p).playSound(null, from.x, from.y, from.z, SoundEvents.BONE_BLOCK_BREAK, SoundSource.PLAYERS, 0.8f, 1.6f);
	}

	/** Shift+Z: Impale Throw -- the sword thrown point first; it pins what it hits and is gone (the arm grows back). */
	public static void impale(ServerPlayer p) {
		if (!begin(p, IMPALE)) {
			return;
		}
		AndyState s = Andy.state(p);
		Vec3 from = AndyAbilities.handOrigin(p, hand(s));
		putAway(p, false, true);
		AndyState n = Andy.state(p).copy();
		n.animId = AndyState.ANIM_SWORD_IMPALE;
		n.animStart = p.level().getGameTime();
		Andy.save(p, n);
		AndyPartEntity.shoot(p, AndyPartEntity.STYLE_SWORD, -1, from, AndyAbilities.aimDir(p, from).scale(IMPALE_SPEED), IMPALE_DAMAGE);
		sound(p, SoundEvents.TRIDENT_THROW.value(), 1.0f, 0.6f);
		AndySfx.launch(level(p), from);
	}

	/** The thrown sword hit {@code target}: it is pinned (a heavy slow and no jumping) for {@link #IMPALE_PIN_TICKS}. */
	public static void pin(ServerPlayer p, LivingEntity target) {
		if (!AndyAbilities.isBoss(target)) {
			target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, IMPALE_PIN_TICKS, 6, false, true), p);
			target.addEffect(new MobEffectInstance(MobEffects.JUMP, IMPALE_PIN_TICKS, 128, false, false), p);
			target.setDeltaMovement(Vec3.ZERO);
			target.hurtMarked = true;
		} else {
			target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, IMPALE_PIN_TICKS / 2, 1, false, true), p);
		}
	}

	/** C: Parry -- for {@link #PARRY_TICKS} a hit from in front does nothing, and a melee hit is countered. */
	public static void parry(ServerPlayer p) {
		if (!begin(p, PARRY)) {
			return;
		}
		cooldownAnim(p, PARRY, PARRY_COOLDOWN, AndyState.ANIM_SWORD_PARRY);
		PARRY_UNTIL.put(p.getUUID(), p.level().getGameTime() + PARRY_TICKS);
		AndySfx.swordSwing(level(p), p.getEyePosition(), 0.5f);
	}

	/**
	 * From {@code AndyDamage.allowDamage}: false if the Rib Cage Guard or a parry stops the hit (a parried melee hit is
	 * countered at once).
	 */
	public static boolean blocks(ServerPlayer p, DamageSource source) {
		if (!held(p) && !guarding(p)) {
			return false;
		}
		boolean guard = guarding(p);
		boolean parry = parrying(p);
		if (!guard && !parry) {
			return false;
		}
		Vec3 from = source.getSourcePosition();
		if (from != null && !guard) {
			Vec3 to = from.subtract(p.position());
			Vec3 look = flatLook(p);
			if (new Vec3(to.x, 0, to.z).normalize().dot(look) < -0.1) {
				return false; // the parry only covers what is in front
			}
		} else if (from == null && !guard) {
			return false;
		}
		ServerLevel level = level(p);
		Vec3 c = p.getEyePosition().add(flatLook(p).scale(0.6));
		if (parry) {
			Entity attacker = source.getEntity();
			if (attacker instanceof LivingEntity le && source.getDirectEntity() == attacker && le != p) {
				PARRY_UNTIL.remove(p.getUUID());
				AndyState n = Andy.state(p).copy();
				long now = p.level().getGameTime();
				n.abilityReadyAt.put(PARRY, now + PARRY_REFUND_COOLDOWN);
				n.animId = AndyState.ANIM_SWORD_COUNTER;
				n.animStart = now;
				Andy.save(p, n);
				AndyAbilities.strike(p, le, p.position(), PARRY_COUNTER_DAMAGE, 1.0, 0.2);
				AndySfx.swordHit(level, le.position().add(0, le.getBbHeight() * 0.5, 0));
				level.playSound(null, c.x, c.y, c.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.35f, 1.8f);
			}
			level.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 8, 0.2, 0.2, 0.2, 0.2);
			level.playSound(null, c.x, c.y, c.z, SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0f, 0.8f);
		} else {
			// v0.17.3: small bone chips and a drop off the ribs at his chest (vanilla's bone-meal item cracks were huge right in
			// front of the camera in first person)
			// (on the ribs themselves, just in front of the chest: below a first-person view looking ahead)
			Vec3 chest = p.position().add(0, p.getBbHeight() * 0.58, 0).add(flatLook(p).scale(0.25));
			level.sendParticles(BONE_CHIP, chest.x, chest.y, chest.z, 4, 0.16, 0.1, 0.16, 0.0);
			level.sendParticles(BLOOD_FLECK, chest.x, chest.y, chest.z, 2, 0.14, 0.08, 0.14, 0.0);
			level.playSound(null, c.x, c.y, c.z, SoundEvents.SKELETON_HURT, SoundSource.PLAYERS, 0.6f, 0.7f);
		}
		return true;
	}

	// ---------------------------------------------------------------- tick

	/** From {@link AndyAbilities#tick}, every tick (also while re-forming). */
	static void tick(ServerPlayer p) {
		AndyState s = Andy.state(p);
		long now = p.level().getGameTime();
		if (s.boneSword()) {
			int hand = hand(s);
			if (s.reformUntil > now || s.broken(hand) || s.broken(AndyState.BODY) || !s.broken(s.boneSwordArm())) {
				putAway(p, true, false); // lost the hand (or the body), or the arm is somehow back
				return;
			}
			if (now - s.boneSwordSince() >= AndyConfig.BONE_SWORD_MAX) {
				putAway(p, true, true);
				return;
			}
			if (s.animId == AndyState.ANIM_SWORD_RIP && now - s.animStart == AndyConfig.BONE_SWORD_REVEAL) {
				reveal(p, s);
			}
			// (the drops off the blade's tip and guard are client-side: AndyBoneSwordRender)
		}
		Lunge l = LUNGES.get(p.getUUID());
		if (l != null) {
			tickLunge(p, l, now);
		}
		Wave w = WAVES.get(p.getUUID());
		if (w != null) {
			tickWave(p, w, now);
		}
		if (guarding(p)) {
			tickGuard(p);
		}
	}

	// ---------------------------------------------------------------- helpers

	private static ServerLevel level(ServerPlayer p) {
		return (ServerLevel) p.level();
	}

	private static void sound(ServerPlayer p, SoundEvent s, float vol, float pitch) {
		p.level().playSound(null, p.getX(), p.getY() + 1.0, p.getZ(), s, SoundSource.PLAYERS, vol, pitch);
	}

	private static Vec3 flatLook(ServerPlayer p) {
		Vec3 look = p.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0, look.z);
		return flat.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : flat.normalize();
	}

	/** What is in a frontal arc of {@code reach} blocks and {@code cos} half-angle. */
	private static List<LivingEntity> arc(ServerPlayer p, double reach, double cos) {
		Vec3 c = p.position().add(0, 0.9, 0);
		Vec3 f = flatLook(p);
		return level(p).getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(reach), e -> {
			if (e == p || e instanceof AndyCloneEntity || !HeroTargets.canHarm(p, e)) {
				return false;
			}
			Vec3 at = e.position().add(0, e.getBbHeight() * 0.5, 0);
			if (at.distanceToSqr(c) > (reach + e.getBbWidth() * 0.5) * (reach + e.getBbWidth() * 0.5)) {
				return false;
			}
			Vec3 to = new Vec3(e.getX() - p.getX(), 0, e.getZ() - p.getZ());
			return to.lengthSqr() < 0.8 || to.normalize().dot(f) >= cos;
		});
	}
}
