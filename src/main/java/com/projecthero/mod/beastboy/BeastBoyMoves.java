package com.projecthero.mod.beastboy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.beastboy.data.BeastBoyState;
import com.projecthero.mod.beastboy.network.BeastBoyFxPayload;
import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.hero.power.AbilityHelpers;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 Beast Boy: every move. Human form's are <b>quick morphs</b>: he flashes into the animal for half a second
 * ({@link BeastBoy#flashQuick}), the hit lands at once and he is himself again; each landed quick-morph hit feeds the Morph
 * Chain. In a sustained form R / G / C are that animal's own moves (paid for by the form's drain, so no stamina cost, only
 * cooldowns). Moves resolve on the press (what's in reach or along the look is hit right then); the lunges also carry him
 * there. The short-lived effects on other creatures -- bleeding, fear, the octopus' hold -- tick here once a server tick
 * ({@link #tickWorld}); their maps hold ids only and are cleared by {@code ServerStateReset}.
 */
public final class BeastBoyMoves {
	private record Bleed(ResourceKey<Level> level, UUID attacker, int ticksLeft) {
	}

	private record Fear(ResourceKey<Level> level, UUID from, long until) {
	}

	private record Hold(ResourceKey<Level> level, UUID owner, long until) {
	}

	private static final Map<UUID, Bleed> BLEEDS = new ConcurrentHashMap<>();
	private static final Map<UUID, Fear> FEARS = new ConcurrentHashMap<>();
	private static final Map<UUID, Hold> HOLDS = new ConcurrentHashMap<>();

	private BeastBoyMoves() {
	}

	static void clearSessionState() {
		BLEEDS.clear();
		FEARS.clear();
		HOLDS.clear();
	}

	static void forget(UUID id) {
		HOLDS.values().removeIf(h -> h.owner.equals(id));
	}

	/** Is {@code e} bleeding / afraid / held right now? For the tests. */
	public static boolean bleeding(Entity e) {
		return BLEEDS.containsKey(e.getUUID());
	}

	public static boolean afraid(Entity e) {
		return FEARS.containsKey(e.getUUID());
	}

	public static boolean held(Entity e) {
		return HOLDS.containsKey(e.getUUID());
	}

	static void tick(ServerPlayer player) {
		// per player: nothing yet (the world effects tick in tickWorld)
	}

	// ---------------------------------------------------------------- routing

	/**
	 * Uses move {@code id}. False (nothing spent) when it can't: dazed, cooling down, a harmless form, too little stamina, or (for a
	 * move that needs one) nothing in reach. Creative pays nothing but still cools down.
	 */
	public static boolean use(ServerPlayer player, String id) {
		if (id == null || !BeastBoy.hasPower(player)) {
			return false;
		}
		if (BeastBoy.dazed(player)) {
			return false;
		}
		BeastForm form = BeastBoy.form(player);
		if (BeastBoy.cooldownRemaining(player, id) > 0) {
			return false;
		}
		boolean ok = switch (id) {
			case BeastBoyAbilityManager.RAM -> ram(player);
			case BeastBoyAbilityManager.POUNCE -> pounce(player);
			case BeastBoyAbilityManager.GORILLA_SMASH -> gorillaSmash(player);
			case BeastBoyAbilityManager.SNAKE_STRIKE -> snakeStrike(player);
			case BeastBoyAbilityManager.TREX_ROAR -> quickRoar(player);
			case BeastBoyAbilityManager.THE_BEAST -> theBeast(player);
			case BeastBoyAbilityManager.CONTEXT_MORPH -> contextMorph(player);
			case BeastBoyAbilityManager.FLY_MORPH -> flyMorph(player);
			case BeastBoyAbilityManager.ELEPHANT_STOMP -> quickStomp(player);
			case BeastBoyAbilityManager.OCTOPUS_GRAB -> quickOctopus(player);
			case BeastBoyAbilityManager.ANIMAL_SENSES -> senses(player);
			default -> form != BeastForm.HUMAN && formMove(player, form, id);
		};
		if (ok) {
			BeastBoy.startCooldown(player, id, BeastBoyAbilityManager.maxCooldown(id));
		}
		return ok;
	}

	// ---------------------------------------------------------------- targeting

	/** The creature he looks at within {@code range}, or the nearest harmable one in a tight cone. */
	static LivingEntity target(ServerPlayer p, double range) {
		LivingEntity e = AbilityHelpers.raycastEntity(p, range);
		if (e != null && HeroTargets.canHarm(p, e)) {
			return e;
		}
		List<LivingEntity> cone = cone(p, range, 0.92);
		return cone.isEmpty() ? null : cone.get(0);
	}

	/** Harmable creatures within {@code range} whose direction is within {@code cos} of his look, nearest first. */
	static List<LivingEntity> cone(ServerPlayer p, double range, double cos) {
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		List<LivingEntity> out = new ArrayList<>();
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, eye, range)) {
			Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
			double d = to.length();
			if (d < 1.0e-3 || to.scale(1.0 / d).dot(look) >= cos || d < e.getBbWidth() * 0.5 + 1.0) {
				out.add(e);
			}
		}
		out.sort(Comparator.comparingDouble(e -> e.distanceToSqr(p)));
		return out;
	}

	static List<LivingEntity> around(ServerPlayer p, double radius) {
		return AbilityHelpers.enemiesAround(p, p.position().add(0, p.getBbHeight() * 0.5, 0), radius);
	}

	private static Vec3 flatLook(ServerPlayer p) {
		Vec3 l = p.getLookAngle();
		Vec3 f = new Vec3(l.x, 0, l.z);
		return f.lengthSqr() < 1.0e-6 ? new Vec3(0, 0, 1) : f.normalize();
	}

	private static void knock(LivingEntity e, ServerPlayer from, double strength, double up) {
		Vec3 d = e.position().subtract(from.position());
		Vec3 flat = new Vec3(d.x, 0, d.z);
		flat = flat.lengthSqr() < 1.0e-6 ? flatLook(from) : flat.normalize();
		e.push(flat.x * strength, up, flat.z * strength);
		e.hurtMarked = true;
	}

	private static ServerLevel level(ServerPlayer p) {
		return (ServerLevel) p.level();
	}

	private static void shake(ServerPlayer p, float strength, int ticks) {
		if (ServerPlayNetworking.canSend(p, BeastBoyFxPayload.TYPE)) {
			ServerPlayNetworking.send(p, new BeastBoyFxPayload(BeastBoyFxPayload.SHAKE, strength, ticks));
		}
	}

	private static void sparks(ServerPlayer p, Vec3 at, int n) {
		level(p).sendParticles(BeastBoy.dust(BeastBoy.GREEN, 1.0f), at.x, at.y, at.z, n, 0.3, 0.3, 0.3, 0.0);
	}

	private static Vec3 mid(LivingEntity e) {
		return e.position().add(0, e.getBbHeight() * 0.5, 0);
	}

	/** A quick morph: pay, flash into the animal, animate. False (nothing spent) without the stamina. */
	private static boolean quick(ServerPlayer p, BeastForm animal, float cost, String move) {
		if (BeastBoy.morphed(p) || !BeastBoy.spendStamina(p, cost)) {
			return false;
		}
		BeastBoy.flashQuick(p, animal);
		BeastBoy.animate(p, move, BeastBoyConfig.QUICK_TICKS);
		BeastBoy.puff(p);
		return true;
	}

	/** A quick-morph hit on {@code e} with {@code animal}: the chain moves on and the damage carries it. */
	private static boolean chainHit(ServerPlayer p, LivingEntity e, BeastForm animal, float base) {
		if (!HeroTargets.canHarm(p, e)) {
			return false;
		}
		float mult = BeastBoy.chainHit(p, animal);
		return BeastBoy.hit(p, e, base * mult);
	}

	// ---------------------------------------------------------------- human form: the quick morphs

	/** R: Ram -- a half-second rhino charge; everything in the 6-block lane takes 10 and a big knockback. */
	static boolean ram(ServerPlayer p) {
		if (!quick(p, BeastForm.RHINO, BeastBoyConfig.RAM_COST, "charge")) {
			return false;
		}
		Vec3 dir = flatLook(p);
		Vec3 start = p.position().add(0, p.getBbHeight() * 0.5, 0);
		Vec3 end = start.add(dir.scale(BeastBoyConfig.RAM_RANGE));
		boolean first = true;
		float mult = 1f;
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, start.add(dir.scale(BeastBoyConfig.RAM_RANGE * 0.5)), BeastBoyConfig.RAM_RANGE * 0.5 + 1.5)) {
			if (distToSegment(mid(e), start, end) > 1.2 + e.getBbWidth() * 0.5) {
				continue;
			}
			if (first) {
				mult = BeastBoy.chainHit(p, BeastForm.RHINO);
				first = false;
			}
			if (BeastBoy.hit(p, e, BeastBoyConfig.RAM_DAMAGE * mult)) {
				knock(e, p, 1.6, 0.45);
				sparks(p, mid(e), 10);
			}
		}
		AbilityHelpers.launchSelf(p, dir.scale(1.35).add(0, 0.1, 0));
		BeastBoy.sound(p, SoundEvents.HOGLIN_ATTACK, 0.45f, 0.9f);
		return true;
	}

	static double distToSegment(Vec3 q, Vec3 a, Vec3 b) {
		Vec3 ab = b.subtract(a);
		double t = Math.max(0, Math.min(1, q.subtract(a).dot(ab) / Math.max(1.0e-6, ab.lengthSqr())));
		return q.distanceTo(a.add(ab.scale(t)));
	}

	/** Shift+R: Pounce -- a tiger leap onto the target: 8 and bleeding for 3 s. */
	static boolean pounce(ServerPlayer p) {
		if (!quick(p, BeastForm.TIGER, BeastBoyConfig.POUNCE_COST, "pounce")) {
			return false;
		}
		LivingEntity t = target(p, BeastBoyConfig.POUNCE_RANGE);
		if (t != null) {
			Vec3 to = t.position().subtract(p.position());
			AbilityHelpers.launchSelf(p, AbilityHelpers.ballisticLaunch(to, Math.max(1.0, to.horizontalDistance() - 0.8), p.onGround()));
			if (chainHit(p, t, BeastForm.TIGER, BeastBoyConfig.POUNCE_DAMAGE)) {
				bleed(p, t);
				sparks(p, mid(t), 10);
			}
		} else {
			AbilityHelpers.launchSelf(p, AbilityHelpers.ballisticLaunch(p.getLookAngle(), 6.0, p.onGround()));
		}
		BeastBoy.sound(p, SoundEvents.OCELOT_HURT, 0.4f, 0.6f);
		return true;
	}

	static void bleed(ServerPlayer p, LivingEntity t) {
		BLEEDS.put(t.getUUID(), new Bleed(t.level().dimension(), p.getUUID(), BeastBoyConfig.BLEED_TICKS));
	}

	/** G: Gorilla Smash -- gorilla arms slam everything in front: 12, knocked down. */
	static boolean gorillaSmash(ServerPlayer p) {
		if (!quick(p, BeastForm.GORILLA, BeastBoyConfig.SMASH_COST, "smash")) {
			return false;
		}
		List<LivingEntity> hit = cone(p, BeastBoyConfig.SMASH_REACH, 0.45);
		float mult = hit.isEmpty() ? 1f : BeastBoy.chainHit(p, BeastForm.GORILLA);
		for (LivingEntity e : hit) {
			if (BeastBoy.hit(p, e, BeastBoyConfig.SMASH_DAMAGE * mult)) {
				knockDown(p, e);
			}
		}
		level(p).sendParticles(ParticleTypes.CLOUD, p.getX() + flatLook(p).x * 1.5, p.getY() + 0.1, p.getZ() + flatLook(p).z * 1.5, 10, 0.6, 0.05, 0.6, 0.02);
		BeastBoy.sound(p, SoundEvents.IRON_GOLEM_ATTACK, 0.45f, 0.9f);
		return true;
	}

	private static void knockDown(ServerPlayer p, LivingEntity e) {
		e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 2), p);
		Vec3 v = e.getDeltaMovement();
		e.setDeltaMovement(v.x * 0.3, -0.6, v.z * 0.3);
		knock(e, p, 0.5, 0.0);
	}

	/** Shift+G: Snake Strike -- lunge as a snake: 5, Poison II and Slowness for 4 s. */
	static boolean snakeStrike(ServerPlayer p) {
		if (!quick(p, BeastForm.SNAKE, BeastBoyConfig.SNAKE_COST, "strike")) {
			return false;
		}
		LivingEntity t = target(p, BeastBoyConfig.SNAKE_RANGE);
		if (t != null) {
			Vec3 to = t.position().subtract(p.position());
			Vec3 flat = new Vec3(to.x, 0, to.z);
			if (flat.lengthSqr() > 1.0e-4) {
				AbilityHelpers.launchSelf(p, flat.normalize().scale(Math.min(1.2, flat.length() * 0.35)).add(0, 0.1, 0));
			}
			if (chainHit(p, t, BeastForm.SNAKE, BeastBoyConfig.SNAKE_DAMAGE)) {
				AbilityHelpers.applyControl(t, MobEffects.POISON, BeastBoyConfig.SNAKE_EFFECT_TICKS, 1);
				AbilityHelpers.applyControl(t, MobEffects.MOVEMENT_SLOWDOWN, BeastBoyConfig.SNAKE_EFFECT_TICKS, 0);
			}
		} else {
			AbilityHelpers.launchSelf(p, flatLook(p).scale(0.9).add(0, 0.1, 0));
		}
		BeastBoy.sound(p, SoundEvents.SILVERFISH_HURT, 0.4f, 0.7f);
		return true;
	}

	/** Z: T-Rex Roar -- a cone: 6, mobs flee 4 s, players' screens shake. */
	static boolean quickRoar(ServerPlayer p) {
		if (!quick(p, BeastForm.TREX, BeastBoyConfig.ROAR_COST, "roar")) {
			return false;
		}
		List<LivingEntity> hit = cone(p, BeastBoyConfig.ROAR_RANGE, 0.5);
		float mult = hit.isEmpty() ? 1f : BeastBoy.chainHit(p, BeastForm.TREX);
		roarAt(p, hit, BeastBoyConfig.ROAR_DAMAGE * mult);
		return true;
	}

	/** A roar on {@code hit}: the damage, the fear, the shake; the sound and the rings. */
	private static void roarAt(ServerPlayer p, List<LivingEntity> hit, float damage) {
		for (LivingEntity e : hit) {
			BeastBoy.hit(p, e, damage);
			scare(p, e);
		}
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		for (int i = 1; i <= 6; i++) {
			Vec3 c = eye.add(look.scale(i * 1.2));
			level(p).sendParticles(ParticleTypes.SONIC_BOOM, c.x, c.y, c.z, 1, 0, 0, 0, 0);
			if (i > 2) {
				break;
			}
		}
		BeastBoy.sound(p, SoundEvents.RAVAGER_ROAR, 0.5f, 0.9f);
	}

	/** Fear: a mob runs from him for 4 s; a player's screen shakes. */
	static void scare(ServerPlayer p, LivingEntity e) {
		if (e instanceof ServerPlayer other) {
			if (HeroTargets.canHarm(p, other)) {
				shake(other, 1.6f, 20);
			}
			return;
		}
		if (e instanceof PathfinderMob) {
			FEARS.put(e.getUUID(), new Fear(e.level().dimension(), p.getUUID(), p.level().getGameTime() + BeastBoyConfig.FEAR_TICKS));
			if (e instanceof net.minecraft.world.entity.Mob m && m.getTarget() == p) {
				m.setTarget(null);
			}
		}
	}

	/** Shift+Z: The Beast -- 30 s of the rage form (60 form health), Morph Chain locked at 5. */
	static boolean theBeast(ServerPlayer p) {
		if (BeastBoy.isBeast(p)) {
			return false;
		}
		if (!p.getAbilities().instabuild && BeastBoy.stamina(p) + 1.0e-3f < BeastBoyConfig.BEAST_COST) {
			BeastBoy.say(p, "message.projecthero.beast_boy.no_stamina", ChatFormatting.GRAY);
			return false;
		}
		if (!BeastBoy.roomFor(p, BeastForm.BEAST)) {
			BeastBoy.say(p, "message.projecthero.beast_boy.no_room", ChatFormatting.GRAY);
			return false;
		}
		BeastBoy.spendStamina(p, BeastBoyConfig.BEAST_COST);
		BeastBoy.setForm(p, BeastForm.BEAST);
		BeastBoyState n = BeastBoy.state(p).copy();
		n.timers = n.timers.withBeast(p.level().getGameTime() + BeastBoyConfig.BEAST_TICKS);
		BeastBoy.save(p, n);
		BeastBoy.animate(p, "roar", 20);
		BeastBoy.sound(p, SoundEvents.RAVAGER_ROAR, 0.6f, 0.7f);
		BeastBoy.say(p, "message.projecthero.beast_boy.beast", ChatFormatting.DARK_GREEN);
		return true;
	}

	/**
	 * X: Context Morph -- the movement animal for where he is: in water the dolphin (with a swim burst), in the air the hawk, on
	 * the ground the cheetah. Again (from any of them) back to human.
	 */
	static boolean contextMorph(ServerPlayer p) {
		BeastForm cur = BeastBoy.form(p);
		if (cur == BeastForm.BEAST) {
			return false;
		}
		if (cur == BeastForm.CHEETAH || cur == BeastForm.HAWK || cur == BeastForm.DOLPHIN) {
			BeastBoy.toHuman(p);
			return true;
		}
		BeastForm want = contextFor(p);
		if (!BeastBoy.morph(p, want, BeastBoyConfig.CONTEXT_MORPH_COST)) {
			return false;
		}
		if (want == BeastForm.DOLPHIN) {
			AbilityHelpers.launchSelf(p, p.getLookAngle().scale(1.4));
		}
		return true;
	}

	/** The animal X picks right now. */
	public static BeastForm contextFor(Player p) {
		if (p.isInWater() || p.isUnderWater()) {
			return BeastForm.DOLPHIN;
		}
		if (!p.onGround() && !p.isPassenger()) {
			return BeastForm.HAWK;
		}
		return BeastForm.CHEETAH;
	}

	/** Shift+X: Fly Morph -- a fly: tiny, slips through gaps, can't attack. Again = human. */
	static boolean flyMorph(ServerPlayer p) {
		BeastForm cur = BeastBoy.form(p);
		if (cur == BeastForm.BEAST) {
			return false;
		}
		if (cur == BeastForm.FLY) {
			BeastBoy.toHuman(p);
			return true;
		}
		return BeastBoy.morph(p, BeastForm.FLY, BeastBoyConfig.CONTEXT_MORPH_COST);
	}

	/** C: Elephant Stomp -- 14 to everything within 4 blocks, slowed. */
	static boolean quickStomp(ServerPlayer p) {
		if (!quick(p, BeastForm.ELEPHANT, BeastBoyConfig.STOMP_COST, "stomp")) {
			return false;
		}
		List<LivingEntity> hit = around(p, BeastBoyConfig.STOMP_RADIUS);
		float mult = hit.isEmpty() ? 1f : BeastBoy.chainHit(p, BeastForm.ELEPHANT);
		stompAt(p, hit, BeastBoyConfig.STOMP_DAMAGE * mult);
		return true;
	}

	private static void stompAt(ServerPlayer p, List<LivingEntity> hit, float damage) {
		for (LivingEntity e : hit) {
			if (BeastBoy.hit(p, e, damage)) {
				AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, 60, 1);
				knock(e, p, 0.4, 0.25);
			}
		}
		ring(p, BeastBoyConfig.STOMP_RADIUS);
		BeastBoy.sound(p, SoundEvents.GENERIC_EXPLODE.value(), 0.25f, 0.6f);
	}

	private static void ring(ServerPlayer p, double r) {
		for (int i = 0; i < 24; i++) {
			double a = i * Math.PI * 2 / 24;
			level(p).sendParticles(ParticleTypes.CLOUD, p.getX() + Math.cos(a) * r, p.getY() + 0.1, p.getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0.01);
		}
	}

	/** Shift+C: Octopus Grab -- four tentacles pull the nearest creatures in and hold them 2 s. */
	static boolean quickOctopus(ServerPlayer p) {
		if (!quick(p, BeastForm.OCTOPUS, BeastBoyConfig.OCTO_COST, "grab")) {
			return false;
		}
		int n = grab(p);
		if (n > 0) {
			BeastBoy.chainHit(p, BeastForm.OCTOPUS);
		}
		return true;
	}

	/** Pulls up to four creatures within 6 blocks in and holds them; returns how many. */
	static int grab(ServerPlayer p) {
		List<LivingEntity> near = around(p, BeastBoyConfig.OCTO_RADIUS);
		near.sort(Comparator.comparingDouble(e -> e.distanceToSqr(p)));
		int n = 0;
		long until = p.level().getGameTime() + BeastBoyConfig.OCTO_HOLD_TICKS;
		for (LivingEntity e : near) {
			if (n >= BeastBoyConfig.OCTO_GRABS) {
				break;
			}
			HOLDS.put(e.getUUID(), new Hold(e.level().dimension(), p.getUUID(), until));
			AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, BeastBoyConfig.OCTO_HOLD_TICKS, 4);
			AbilityHelpers.line(level(p), p.getEyePosition().add(0, -0.4, 0), mid(e), BeastBoy.dust(BeastBoy.GREEN, 0.9f), 3);
			n++;
		}
		BeastBoy.sound(p, SoundEvents.SQUID_SQUIRT, 0.4f, 1.0f);
		return n;
	}

	/** Shift+V: Animal Senses -- a wolf's nose for 10 s (the footprint trails are drawn on his screen). */
	static boolean senses(ServerPlayer p) {
		if (!BeastBoy.spendStamina(p, BeastBoyConfig.SENSES_COST)) {
			return false;
		}
		BeastBoyState n = BeastBoy.state(p).copy();
		n.timers = n.timers.withSenses(p.level().getGameTime() + BeastBoyConfig.SENSES_TICKS);
		BeastBoy.save(p, n);
		BeastBoy.sound(p, SoundEvents.WOLF_PANT, 0.35f, 1.2f);
		return true;
	}

	// ---------------------------------------------------------------- the sustained forms' own moves

	private static boolean formMove(ServerPlayer p, BeastForm form, String id) {
		if (BeastBoyForms.harmless(form)) {
			return false;
		}
		String[] own = BeastBoyForms.moves(form);
		if (!id.equals(own[0]) && !id.equals(own[1]) && !id.equals(own[2])) {
			return false;
		}
		return switch (id) {
			case "trex_bite" -> bite(p, BeastBoyConfig.TREX_BITE, 5.0, "bite", SoundEvents.RAVAGER_ATTACK);
			case "trex_tail" -> sweep(p, BeastBoyConfig.TREX_TAIL, 4.5, 1.3, "swipe");
			case "trex_roar" -> formRoar(p, cone(p, 10.0, 0.4), BeastBoyConfig.ROAR_DAMAGE);
			case "gorilla_punch" -> bite(p, BeastBoyConfig.GORILLA_PUNCH, 3.8, "smash", SoundEvents.IRON_GOLEM_ATTACK);
			case "gorilla_throw" -> gorillaThrow(p);
			case "gorilla_beat" -> chestBeat(p);
			case "rhino_charge" -> rhinoCharge(p);
			case "elephant_trunk" -> trunkThrow(p);
			case "elephant_stomp" -> {
				BeastBoy.animate(p, "stomp", 12);
				stompAt(p, around(p, BeastBoyConfig.STOMP_RADIUS), BeastBoyConfig.ELEPHANT_STOMP);
				yield true;
			}
			case "elephant_spray" -> spray(p);
			case "wolf_bite" -> bite(p, BeastBoyConfig.WOLF_BITE, 3.2, "bite", SoundEvents.WOLF_GROWL);
			case "wolf_howl" -> howl(p);
			case "bear_maul" -> bite(p, BeastBoyConfig.BEAR_MAUL, 3.6, "swipe", SoundEvents.POLAR_BEAR_HURT);
			case "bear_roar" -> formRoar(p, around(p, 6.0), 0f);
			case "ptero_dive" -> dive(p);
			case "hawk_talon" -> bite(p, BeastBoyConfig.HAWK_TALON, 3.0, "strike", SoundEvents.PARROT_HURT);
			case "hawk_screech" -> screech(p);
			case "bat_pulse" -> echolocate(p);
			case "shark_bite" -> bite(p, BeastBoyConfig.SHARK_BITE, 3.8, "bite", SoundEvents.GUARDIAN_ATTACK);
			case "octopus_ink" -> ink(p);
			case "octopus_grab_form" -> {
				BeastBoy.animate(p, "grab", 14);
				grab(p);
				yield true;
			}
			case "whale_slam" -> sweep(p, BeastBoyConfig.WHALE_SLAM, 5.5, 1.6, "slam");
			case "beast_swipe" -> bite(p, BeastBoyConfig.BEAST_SWIPE * BeastBoy.chainMult(BeastBoyConfig.CHAIN_MAX), 3.8, "swipe", SoundEvents.POLAR_BEAR_WARNING);
			case "beast_leap" -> beastLeap(p);
			case "beast_roar" -> formRoar(p, around(p, 8.0), BeastBoyConfig.ROAR_DAMAGE);
			default -> false;
		};
	}

	/** A bite / punch / maul: the creature he looks at in reach (the nearest in front otherwise). Fires (animates) even on a miss. */
	private static boolean bite(ServerPlayer p, float damage, double reach, String move, net.minecraft.sounds.SoundEvent sound) {
		BeastBoy.animate(p, move, 10);
		LivingEntity t = target(p, reach + p.getBbWidth() * 0.5);
		if (t == null) {
			List<LivingEntity> c = cone(p, reach + p.getBbWidth() * 0.5, 0.5);
			t = c.isEmpty() ? null : c.get(0);
		}
		if (t != null && BeastBoy.hit(p, t, damage)) {
			knock(t, p, 0.4, 0.15);
			sparks(p, mid(t), 6);
		}
		BeastBoy.sound(p, sound, 0.45f, 0.9f);
		return true;
	}

	/** A tail sweep / whale slam: everything round him, knocked away. */
	private static boolean sweep(ServerPlayer p, float damage, double radius, double knock, String move) {
		BeastBoy.animate(p, move, 14);
		for (LivingEntity e : around(p, radius)) {
			if (BeastBoy.hit(p, e, damage)) {
				knock(e, p, knock, 0.35);
			}
		}
		ring(p, radius * 0.8);
		BeastBoy.sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.5f, 0.6f);
		return true;
	}

	private static boolean formRoar(ServerPlayer p, List<LivingEntity> hit, float damage) {
		BeastBoy.animate(p, "roar", 20);
		if (damage > 0f) {
			roarAt(p, hit, damage);
		} else {
			for (LivingEntity e : hit) {
				scare(p, e);
				knock(e, p, 0.6, 0.2);
			}
			BeastBoy.sound(p, SoundEvents.POLAR_BEAR_WARNING, 0.5f, 0.8f);
		}
		return true;
	}

	/** Gorilla G: grab the creature in front and hurl it along the look (6, and whatever the landing does). */
	private static boolean gorillaThrow(ServerPlayer p) {
		LivingEntity t = target(p, 4.0);
		if (t == null) {
			return false;
		}
		BeastBoy.animate(p, "grab", 12);
		if (BeastBoy.hit(p, t, BeastBoyConfig.GORILLA_THROW)) {
			Vec3 v = p.getLookAngle().scale(1.8).add(0, 0.55, 0);
			t.setDeltaMovement(v);
			t.hurtMarked = true;
		}
		BeastBoy.sound(p, SoundEvents.IRON_GOLEM_ATTACK, 0.45f, 0.7f);
		return true;
	}

	/** Gorilla C: the chest-beat -- Strength I for him and every squadmate within 16 blocks for 10 s. */
	private static boolean chestBeat(ServerPlayer p) {
		BeastBoy.animate(p, "chest_beat", 20);
		for (Player other : p.level().getEntitiesOfClass(Player.class, p.getBoundingBox().inflate(16.0),
				o -> o == p || com.projecthero.mod.squad.Squads.areAllies(p, o))) {
			other.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, BeastBoyConfig.CHEST_BEAT_TICKS, 0, false, true, true));
		}
		BeastBoy.sound(p, SoundEvents.IRON_GOLEM_REPAIR, 0.5f, 0.6f);
		return true;
	}

	/** Rhino R: charge 8 blocks, 16 to everything in the lane, through soft blocks. */
	private static boolean rhinoCharge(ServerPlayer p) {
		BeastBoy.animate(p, "charge", 14);
		Vec3 dir = flatLook(p);
		Vec3 start = p.position().add(0, p.getBbHeight() * 0.5, 0);
		Vec3 end = start.add(dir.scale(BeastBoyConfig.RHINO_RANGE));
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, start.add(dir.scale(BeastBoyConfig.RHINO_RANGE * 0.5)),
				BeastBoyConfig.RHINO_RANGE * 0.5 + 2.0)) {
			if (distToSegment(mid(e), start, end) <= 1.4 + e.getBbWidth() * 0.5 && BeastBoy.hit(p, e, BeastBoyConfig.RHINO_CHARGE)) {
				knock(e, p, 1.8, 0.5);
			}
		}
		AbilityHelpers.launchSelf(p, dir.scale(1.6).add(0, 0.1, 0));
		BeastBoy.sound(p, SoundEvents.HOGLIN_ATTACK, 0.5f, 0.7f);
		return true;
	}

	/** Elephant R: the trunk throws the creature in front up and away (8). */
	private static boolean trunkThrow(ServerPlayer p) {
		LivingEntity t = target(p, 4.5);
		if (t == null) {
			return false;
		}
		BeastBoy.animate(p, "grab", 12);
		if (BeastBoy.hit(p, t, BeastBoyConfig.ELEPHANT_TRUNK)) {
			Vec3 away = flatLook(p);
			t.setDeltaMovement(away.x * 1.2, 1.0, away.z * 1.2);
			t.hurtMarked = true;
		}
		BeastBoy.sound(p, SoundEvents.SNIFFER_HAPPY, 0.45f, 0.6f);
		return true;
	}

	/** Elephant C: water spray -- puts out fire (blocks and creatures) in a 7-block cone and pushes creatures back. */
	private static boolean spray(ServerPlayer p) {
		BeastBoy.animate(p, "spray", 20);
		p.clearFire();
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		ServerLevel level = level(p);
		for (int i = 1; i <= 7; i++) {
			Vec3 c = eye.add(look.scale(i));
			level.sendParticles(ParticleTypes.SPLASH, c.x, c.y, c.z, 12, 0.3 + i * 0.08, 0.3, 0.3 + i * 0.08, 0.1);
			BlockPos center = BlockPos.containing(c);
			for (BlockPos bp : BlockPos.betweenClosed(center.offset(-1, -2, -1), center.offset(1, 1, 1))) {
				BlockState st = level.getBlockState(bp);
				if (st.is(BlockTags.FIRE)) {
					level.removeBlock(bp, false);
				} else if (st.hasProperty(net.minecraft.world.level.block.CampfireBlock.LIT) && st.getValue(net.minecraft.world.level.block.CampfireBlock.LIT)) {
					level.setBlock(bp, st.setValue(net.minecraft.world.level.block.CampfireBlock.LIT, false), 3);
				}
			}
		}
		for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, new AABB(eye, eye.add(look.scale(7))).inflate(2.0),
				e -> e != p && e.isAlive())) {
			Vec3 to = mid(e).subtract(eye);
			if (to.length() > 8 || to.normalize().dot(look) < 0.6) {
				continue;
			}
			e.clearFire();
			if (HeroTargets.canHarm(p, e)) {
				knock(e, p, 0.8, 0.1);
			}
		}
		BeastBoy.sound(p, SoundEvents.GENERIC_SPLASH, 0.5f, 1.0f);
		return true;
	}

	/** Wolf G: howl -- Speed II for him and squadmates within 24 blocks for 15 s. */
	private static boolean howl(ServerPlayer p) {
		BeastBoy.animate(p, "howl", 24);
		for (Player other : p.level().getEntitiesOfClass(Player.class, p.getBoundingBox().inflate(24.0),
				o -> o == p || com.projecthero.mod.squad.Squads.areAllies(p, o))) {
			other.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, BeastBoyConfig.HOWL_TICKS, 1, false, true, true));
		}
		BeastBoy.sound(p, SoundEvents.WOLF_HOWL, 0.5f, 1.0f);
		return true;
	}

	/** Pterodactyl R: dive claw -- swoop onto the creature he looks at within 12 blocks: 12. */
	private static boolean dive(ServerPlayer p) {
		LivingEntity t = target(p, BeastBoyConfig.PTERO_RANGE);
		BeastBoy.animate(p, "dive", 14);
		if (t != null) {
			Vec3 to = mid(t).subtract(p.position());
			AbilityHelpers.launchSelf(p, to.normalize().scale(Math.min(2.2, to.length() * 0.35)));
			if (BeastBoy.hit(p, t, BeastBoyConfig.PTERO_DIVE)) {
				knock(t, p, 0.6, 0.3);
			}
		} else {
			AbilityHelpers.launchSelf(p, p.getLookAngle().scale(1.6));
		}
		BeastBoy.sound(p, SoundEvents.PHANTOM_SWOOP, 0.45f, 0.9f);
		return true;
	}

	/** Hawk G: screech -- marks the creature he looks at (it glows through walls and is weakened) for 10 s. */
	private static boolean screech(ServerPlayer p) {
		LivingEntity t = target(p, 32.0);
		if (t == null) {
			return false;
		}
		BeastBoy.animate(p, "screech", 12);
		t.addEffect(new MobEffectInstance(MobEffects.GLOWING, BeastBoyConfig.SCREECH_TICKS, 0, false, false, true), p);
		AbilityHelpers.applyControl(t, MobEffects.WEAKNESS, BeastBoyConfig.SCREECH_TICKS, 0);
		BeastBoy.sound(p, SoundEvents.PARROT_IMITATE_PHANTOM, 0.45f, 1.4f);
		return true;
	}

	/** Bat R: echolocation -- every creature within 24 blocks glows through walls for 6 s. */
	private static boolean echolocate(ServerPlayer p) {
		BeastBoy.animate(p, "screech", 12);
		for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(BeastBoyConfig.BAT_PULSE_RADIUS),
				e -> e != p && e.isAlive() && !com.projecthero.mod.squad.Squads.areAllies(p, e))) {
			e.addEffect(new MobEffectInstance(MobEffects.GLOWING, BeastBoyConfig.BAT_PULSE_TICKS, 0, false, false, true), p);
		}
		ring(p, 3.0);
		BeastBoy.sound(p, SoundEvents.BAT_AMBIENT, 0.5f, 0.6f);
		return true;
	}

	/** Octopus R: ink -- a cloud that blinds everything within 4 blocks for 4 s. */
	private static boolean ink(ServerPlayer p) {
		BeastBoy.animate(p, "ink", 14);
		for (LivingEntity e : around(p, 4.0)) {
			AbilityHelpers.applyControl(e, MobEffects.BLINDNESS, BeastBoyConfig.INK_TICKS, 0);
		}
		Vec3 c = p.position().add(0, 0.5, 0);
		level(p).sendParticles(ParticleTypes.SQUID_INK, c.x, c.y, c.z, 60, 1.5, 0.8, 1.5, 0.05);
		BeastBoy.sound(p, SoundEvents.SQUID_SQUIRT, 0.5f, 0.8f);
		return true;
	}

	/** The Beast G: a great leap along the look; the landing slams everything within 3 blocks. */
	private static boolean beastLeap(ServerPlayer p) {
		BeastBoy.animate(p, "pounce", 16);
		AbilityHelpers.launchSelf(p, flatLook(p).scale(1.3).add(0, 0.75, 0));
		BeastBoyState n = BeastBoy.state(p).copy();
		n.timers = n.timers.withLeap(p.level().getGameTime());
		BeastBoy.save(p, n);
		BeastBoy.sound(p, SoundEvents.POLAR_BEAR_WARNING, 0.4f, 1.1f);
		return true;
	}

	/** The Beast's landing (from {@code BeastBoy.formTick}). */
	static void leapSlam(ServerPlayer p) {
		BeastBoy.animate(p, "slam", 10);
		for (LivingEntity e : around(p, 3.0)) {
			if (BeastBoy.hit(p, e, BeastBoyConfig.BEAST_SLAM * BeastBoy.chainMult(BeastBoyConfig.CHAIN_MAX))) {
				knock(e, p, 0.8, 0.5);
			}
		}
		ring(p, 3.0);
		BeastBoy.sound(p, SoundEvents.GENERIC_EXPLODE.value(), 0.2f, 0.7f);
	}

	/** The Beast always, the rhino while charging: soft blocks (dirt, sand, leaves, glass...) in front break, if mobGriefing is on. */
	static void smashAhead(ServerPlayer p, BeastForm f) {
		if (f == BeastForm.RHINO && !"charge".equals(BeastBoy.move(p))) {
			return;
		}
		ServerLevel level = level(p);
		if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
			return;
		}
		Vec3 v = p.getDeltaMovement();
		Vec3 dir = new Vec3(v.x, 0, v.z);
		if (dir.lengthSqr() < 0.004 && f != BeastForm.RHINO) {
			return;
		}
		dir = dir.lengthSqr() < 1.0e-6 ? flatLook(p) : dir.normalize();
		AABB box = p.getBoundingBox().move(dir.scale(0.6)).deflate(0.05, 0.0, 0.05);
		box = new AABB(box.minX, box.minY + 0.55, box.minZ, box.maxX, box.maxY, box.maxZ); // the floor stays
		int broken = 0;
		for (BlockPos bp : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
			if (broken >= 6) {
				break;
			}
			BlockState st = level.getBlockState(bp);
			if (st.isAir() || !st.getFluidState().isEmpty()) {
				continue;
			}
			float hard = st.getDestroySpeed(level, bp);
			if (hard >= 0f && hard <= 1.0f && !st.getCollisionShape(level, bp).isEmpty()) {
				level.destroyBlock(bp.immutable(), true, p);
				broken++;
			}
		}
	}

	// ---------------------------------------------------------------- the world effects (once a server tick)

	public static void tickWorld(MinecraftServer server) {
		if (BLEEDS.isEmpty() && FEARS.isEmpty() && HOLDS.isEmpty()) {
			return;
		}
		Iterator<Map.Entry<UUID, Bleed>> bi = BLEEDS.entrySet().iterator();
		while (bi.hasNext()) {
			Map.Entry<UUID, Bleed> en = bi.next();
			Bleed b = en.getValue();
			ServerLevel level = server.getLevel(b.level);
			Entity e = level == null ? null : level.getEntity(en.getKey());
			if (!(e instanceof LivingEntity le) || !le.isAlive() || b.ticksLeft <= 0) {
				bi.remove();
				continue;
			}
			int left = b.ticksLeft - 1;
			if (left % BeastBoyConfig.BLEED_EVERY == 0) {
				ServerPlayer attacker = server.getPlayerList().getPlayer(b.attacker);
				if (attacker != null) {
					BeastBoy.hit(attacker, le, BeastBoyConfig.BLEED_DAMAGE);
				}
				level.sendParticles(BeastBoy.dust(0x8A1010, 0.8f), le.getX(), le.getY() + le.getBbHeight() * 0.6, le.getZ(), 4, 0.2, 0.2, 0.2, 0);
			}
			en.setValue(new Bleed(b.level, b.attacker, left));
		}
		Iterator<Map.Entry<UUID, Fear>> fi = FEARS.entrySet().iterator();
		while (fi.hasNext()) {
			Map.Entry<UUID, Fear> en = fi.next();
			Fear f = en.getValue();
			ServerLevel level = server.getLevel(f.level);
			Entity e = level == null ? null : level.getEntity(en.getKey());
			if (!(e instanceof PathfinderMob mob) || !mob.isAlive() || level.getGameTime() >= f.until) {
				fi.remove();
				continue;
			}
			ServerPlayer from = server.getPlayerList().getPlayer(f.from);
			if (mob.getTarget() == from) {
				mob.setTarget(null);
			}
			if (from != null && (level.getGameTime() + mob.getId()) % 10 == 0) {
				Vec3 away = DefaultRandomPos.getPosAway(mob, 16, 7, from.position());
				if (away != null) {
					mob.getNavigation().moveTo(away.x, away.y, away.z, 1.4);
				}
			}
		}
		Iterator<Map.Entry<UUID, Hold>> hi = HOLDS.entrySet().iterator();
		while (hi.hasNext()) {
			Map.Entry<UUID, Hold> en = hi.next();
			Hold h = en.getValue();
			ServerLevel level = server.getLevel(h.level);
			Entity e = level == null ? null : level.getEntity(en.getKey());
			ServerPlayer owner = server.getPlayerList().getPlayer(h.owner);
			if (!(e instanceof LivingEntity le) || !le.isAlive() || owner == null || owner.level() != level || level.getGameTime() >= h.until) {
				hi.remove();
				continue;
			}
			Vec3 anchor = owner.position().add(flatLook(owner).scale(1.2 + le.getBbWidth() * 0.5 + owner.getBbWidth() * 0.5));
			Vec3 to = anchor.subtract(le.position());
			if (le instanceof ServerPlayer) {
				le.setDeltaMovement(to.scale(0.35));
				le.hurtMarked = true;
			} else {
				Vec3 step = to.length() > 0.6 ? to.scale(0.4) : to;
				le.moveTo(le.getX() + step.x, le.getY() + Math.max(-0.5, Math.min(0.5, step.y)), le.getZ() + step.z, le.getYRot(), le.getXRot());
				le.setDeltaMovement(Vec3.ZERO);
			}
			if (level.getGameTime() % 4 == 0) {
				AbilityHelpers.line(level, owner.getEyePosition().add(0, -0.5, 0), mid(le), BeastBoy.dust(BeastBoy.GREEN, 0.7f), 2);
			}
		}
	}
}
