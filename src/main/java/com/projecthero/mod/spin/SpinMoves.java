package com.projecthero.mod.spin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.spin.data.SpinState;
import com.projecthero.mod.spin.entity.SteelBallEntity;
import com.projecthero.mod.spin.network.SpinFxPayload;
import com.projecthero.mod.squad.Squads;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 the Spin: every Spin move ({@link SpinAbilityManager} routes the keys here). Each press checks, in order, the move's
 * cooldown, the balls it needs, and the Spin it costs; a move that can't find what it needs (no one in reach) costs nothing. The
 * two holds (Golden Spin, Ball Breaker) run the Golden Rectangle: {@link #startCharge} on the press, {@link #releaseCharge} on the
 * release, a Perfect Spin when the release lands within {@link SpinConfig#PERFECT_WINDOW} ticks of the close.
 *
 * <p>Static state, all UUID-keyed or holding entities only while a move runs: {@link #STRIKES} (Ball Breaker's figure on its way)
 * and {@link #SHIELD_HITS}; {@code ServerStateReset} clears both.
 */
public final class SpinMoves {
	private record Strike(LivingEntity target, long at) {
	}

	private static final Map<UUID, Strike> STRIKES = new ConcurrentHashMap<>();
	/** Spin Shield: player -> entity id -> last time the orbit hit it. */
	private static final Map<UUID, Map<Integer, Long>> SHIELD_HITS = new ConcurrentHashMap<>();

	private SpinMoves() {
	}

	static void clearSessionState() {
		STRIKES.clear();
		SHIELD_HITS.clear();
	}

	static void forget(UUID id) {
		STRIKES.remove(id);
		SHIELD_HITS.remove(id);
	}

	// ---------------------------------------------------------------- shared checks

	/** Cooldown, balls, Spin. True if the move may go (nothing spent yet). */
	private static boolean ready(ServerPlayer p, String id) {
		int cd = SpinAbilityManager.cooldownRemaining(p, id);
		if (cd > 0) {
			Spin.say(p, "message.projecthero.spin.cooldown", ChatFormatting.GRAY,
					net.minecraft.network.chat.Component.translatable("projecthero.spin.ability." + id), (cd + 19) / 20);
			return false;
		}
		if (!SpinAbilityManager.ballsAllow(p, id)) {
			Spin.say(p, SpinAbilityManager.ballsNeeded(id) >= 2 ? "message.projecthero.spin.need_balls" : "message.projecthero.spin.need_ball",
					ChatFormatting.GRAY);
			return false;
		}
		if (!Spin.hasSpin(p, SpinAbilityManager.cost(id))) {
			Spin.say(p, "message.projecthero.spin.no_spin", ChatFormatting.GRAY);
			return false;
		}
		return true;
	}

	/** Pays for a move that went: Spin and cooldown. */
	private static void pay(ServerPlayer p, String id, float cost, int cooldown) {
		Spin.spendSpin(p, cost);
		if (cooldown > 0) {
			Spin.startCooldown(p, id, cooldown);
		}
	}

	private static void fx(ServerPlayer p, SpinFxPayload payload) {
		for (ServerPlayer viewer : PlayerLookup.tracking(p)) {
			if (viewer != p && ServerPlayNetworking.canSend(viewer, SpinFxPayload.TYPE)) {
				ServerPlayNetworking.send(viewer, payload);
			}
		}
		if (ServerPlayNetworking.canSend(p, SpinFxPayload.TYPE)) {
			ServerPlayNetworking.send(p, payload);
		}
	}

	private static void fxSelf(ServerPlayer p, SpinFxPayload payload) {
		if (ServerPlayNetworking.canSend(p, SpinFxPayload.TYPE)) {
			ServerPlayNetworking.send(p, payload);
		}
	}

	/** The first creature he may harm along his aim within {@code range}, walls in the way. */
	public static LivingEntity aimed(ServerPlayer p, double range) {
		Vec3 eye = p.getEyePosition();
		Vec3 end = eye.add(p.getViewVector(1f).scale(range));
		BlockHitResult block = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		if (block.getType() != HitResult.Type.MISS) {
			end = block.getLocation();
		}
		AABB box = p.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0);
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(p.level(), p, eye, end, box,
				e -> e instanceof LivingEntity le && le.isAlive() && e.isPickable() && HeroTargets.canHarm(p, e), 0.3f);
		return hit != null && hit.getEntity() instanceof LivingEntity le ? le : null;
	}

	/** Where his aim lands within {@code range}: a creature, a block, or the end of the range. */
	static Vec3 aimPoint(ServerPlayer p, double range) {
		Vec3 eye = p.getEyePosition();
		LivingEntity t = aimed(p, range);
		if (t != null) {
			return t.position().add(0, t.getBbHeight() * 0.55, 0);
		}
		Vec3 end = eye.add(p.getViewVector(1f).scale(range));
		BlockHitResult block = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		return block.getType() != HitResult.Type.MISS ? block.getLocation() : end;
	}

	private static Vec3 right(Player p) {
		float yaw = p.getYRot() * ((float) Math.PI / 180f);
		return new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
	}

	// ---------------------------------------------------------------- the taps

	/** A tap move (everything but the two holds). True if it went. */
	public static boolean use(ServerPlayer p, String id) {
		if (Spin.charging(p) != SpinState.CHARGE_NONE) {
			return false;
		}
		// R with no ball in the main hand: call back a ball that was dropped or left somewhere
		if (SpinAbilityManager.STEEL_BALL.equals(id) && Spin.readyBall(p) < 0 && p.getMainHandItem().isEmpty() && SpinBalls.recall(p)) {
			return true;
		}
		if (!ready(p, id)) {
			return false;
		}
		return switch (id) {
			case SpinAbilityManager.STEEL_BALL -> steelBall(p);
			case SpinAbilityManager.TWIN_BALLS -> twinBalls(p);
			case SpinAbilityManager.SPIN_TOUCH -> spinTouch(p);
			case SpinAbilityManager.SCAN -> scan(p);
			case SpinAbilityManager.SPIN_LEAP -> spinLeap(p);
			case SpinAbilityManager.VALKYRIE -> valkyrie(p);
			case SpinAbilityManager.SPIN_QUAKE -> spinQuake(p);
			case SpinAbilityManager.SPIN_SHIELD -> spinShield(p);
			case SpinAbilityManager.HEALING_SPIN -> healingSpin(p);
			case SpinAbilityManager.FACE_SPIN -> faceSpin(p);
			default -> false;
		};
	}

	/** R: one ball, 30 blocks out and back. */
	static boolean steelBall(ServerPlayer p) {
		int i = Spin.readyBall(p);
		if (i < 0) {
			return false;
		}
		Vec3 look = p.getViewVector(1f);
		SteelBallEntity.launch(p, i, SteelBallEntity.MODE_HIT, look.scale(SpinConfig.BALL_SPEED),
				p.getEyePosition().add(look.scale(SpinConfig.BALL_RANGE)), false, SpinConfig.BALL_RANGE, false);
		Spin.sound(p, SpinSounds.BALL_THROW, 0.7f, 1.0f);
		pay(p, SpinAbilityManager.STEEL_BALL, SpinConfig.BALL_COST, SpinConfig.BALL_COOLDOWN);
		p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
		return true;
	}

	/** Shift+R: both balls, curving in from either side onto the aim point. */
	static boolean twinBalls(ServerPlayer p) {
		Vec3 look = p.getViewVector(1f);
		Vec3 aim = aimPoint(p, SpinConfig.BALL_RANGE);
		Vec3 side = right(p).scale(SpinConfig.TWIN_SIDE_SPEED);
		int main = SpinBalls.inHand(p, net.minecraft.world.InteractionHand.MAIN_HAND);
		int off = SpinBalls.inHand(p, net.minecraft.world.InteractionHand.OFF_HAND);
		if (main < 0 || off < 0) {
			return false;
		}
		// the main-hand ball swings out to the right, the off-hand one to the left
		SteelBallEntity.launch(p, main, net.minecraft.world.InteractionHand.MAIN_HAND, SteelBallEntity.MODE_HIT,
				look.scale(SpinConfig.BALL_SPEED * 0.85).add(side), aim, true, SpinConfig.BALL_RANGE, false);
		SteelBallEntity.launch(p, off, net.minecraft.world.InteractionHand.OFF_HAND, SteelBallEntity.MODE_HIT,
				look.scale(SpinConfig.BALL_SPEED * 0.85).subtract(side), aim, true, SpinConfig.BALL_RANGE, false);
		Spin.sound(p, SpinSounds.BALL_THROW, 0.8f, 0.9f);
		pay(p, SpinAbilityManager.TWIN_BALLS, SpinConfig.TWIN_COST, SpinConfig.TWIN_COOLDOWN);
		p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
		return true;
	}

	/** G: the spinning ball pressed to the target -- its joints lock for 2 s; a player drops what he holds. */
	static boolean spinTouch(ServerPlayer p) {
		LivingEntity t = aimed(p, SpinConfig.TOUCH_REACH);
		if (t == null) {
			Spin.say(p, "message.projecthero.spin.no_target", ChatFormatting.GRAY);
			return false;
		}
		Spin.hit(p, t, SpinConfig.TOUCH_DAMAGE, false);
		SpinMarks.lock(t, SpinConfig.TOUCH_LOCK_TICKS);
		SpinMarks.spin(t, SpinConfig.TOUCH_LOCK_TICKS);
		if (t instanceof ServerPlayer victim) {
			ItemStack held = victim.getMainHandItem();
			if (!held.isEmpty()) {
				victim.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
				ItemEntity drop = victim.drop(held, true, false);
				if (drop != null) {
					drop.setPickUpDelay(40);
				}
			}
		}
		Vec3 c = t.position().add(0, t.getBbHeight() * 0.6, 0);
		((ServerLevel) p.level()).sendParticles(Spin.dust(Spin.GOLD, 1.0f), c.x, c.y, c.z, 14, 0.3, 0.4, 0.3, 0.0);
		p.level().playSound(null, t.getX(), t.getY() + 1, t.getZ(), SpinSounds.BODY_SPIN, SoundSource.PLAYERS, 0.7f, 1.2f);
		pay(p, SpinAbilityManager.SPIN_TOUCH, SpinConfig.TOUCH_COST, SpinConfig.TOUCH_COOLDOWN);
		p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
		return true;
	}

	/** Z: a ball spins on the ground; everything within 40 blocks glows through walls for him for 8 s. */
	static boolean scan(ServerPlayer p) {
		int i = Spin.readyBall(p);
		if (i < 0) {
			return false;
		}
		Vec3 eye = p.getEyePosition();
		Vec3 end = eye.add(p.getViewVector(1f).scale(5.0));
		BlockHitResult block = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		Vec3 spot = block.getType() != HitResult.Type.MISS ? block.getLocation() : p.position().add(p.getViewVector(1f).multiply(2, 0, 2));
		BlockHitResult down = p.level().clip(new ClipContext(spot.add(0, 0.5, 0), spot.add(0, -6, 0), ClipContext.Block.COLLIDER,
				ClipContext.Fluid.NONE, p));
		if (down.getType() != HitResult.Type.MISS) {
			spot = down.getLocation();
		}
		Spin.takeBusy(p, i, SpinConfig.SCAN_BALL_TICKS, net.minecraft.world.InteractionHand.MAIN_HAND);
		List<LivingEntity> found = p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(SpinConfig.SCAN_RADIUS),
				e -> e != p && e.isAlive() && e.distanceToSqr(p) <= SpinConfig.SCAN_RADIUS * SpinConfig.SCAN_RADIUS);
		int[] ids = new int[found.size()];
		for (int k = 0; k < ids.length; k++) {
			ids[k] = found.get(k).getId();
		}
		fx(p, SpinFxPayload.at(SpinFxPayload.SCAN_BALL, spot, SpinConfig.SCAN_BALL_TICKS));
		fxSelf(p, new SpinFxPayload(SpinFxPayload.SCAN_GLOW, -1, spot, Vec3.ZERO, SpinConfig.SCAN_GLOW_TICKS, false, ids));
		p.level().playSound(null, spot.x, spot.y, spot.z, SpinSounds.SCAN, SoundSource.PLAYERS, 0.7f, 1.0f);
		p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.projecthero.spin.scan", found.size())
				.withStyle(ChatFormatting.GOLD), true);
		pay(p, SpinAbilityManager.SCAN, SpinConfig.SCAN_COST, SpinConfig.SCAN_COOLDOWN);
		lastScan = found.size();
		return true;
	}

	/** For the tests: how many creatures the last Scan found. */
	public static int lastScan;

	/** X: the Spin in his legs -- about 12 blocks forward and 5 up, no fall damage after. On a horse, the horse leaps. */
	static boolean spinLeap(ServerPlayer p) {
		Vec3 look = p.getViewVector(1f);
		Vec3 flat = new Vec3(look.x, 0, look.z);
		flat = flat.lengthSqr() < 1.0e-4 ? Vec3.ZERO : flat.normalize();
		Vec3 v = flat.scale(SpinConfig.LEAP_FORWARD).add(0, SpinConfig.LEAP_UP, 0);
		Entity mover = p.getVehicle() != null ? p.getVehicle() : p;
		mover.setDeltaMovement(v);
		mover.hurtMarked = true;
		mover.resetFallDistance();
		if (mover != p) {
			mover.fallDistance = 0f;
		}
		SpinState n = Spin.state(p).copy();
		n.leapUntil = Spin.now(p) + SpinConfig.LEAP_SAFE_TICKS;
		Spin.save(p, n);
		ServerLevel level = (ServerLevel) p.level();
		level.sendParticles(Spin.dust(Spin.GOLD, 1.2f), p.getX(), p.getY() + 0.2, p.getZ(), 16, 0.3, 0.1, 0.3, 0.0);
		level.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.1, p.getZ(), 6, 0.3, 0.05, 0.3, 0.02);
		Spin.sound(p, SpinSounds.LEAP, 0.8f, 1.0f);
		pay(p, SpinAbilityManager.SPIN_LEAP, SpinConfig.LEAP_COST, SpinConfig.LEAP_COOLDOWN);
		return true;
	}

	/** Shift+X: whistle for Valkyrie. */
	static boolean valkyrie(ServerPlayer p) {
		if (!SpinValkyrie.whistle(p)) {
			return false;
		}
		Spin.startCooldown(p, SpinAbilityManager.VALKYRIE, SpinConfig.VALKYRIE_WHISTLE_COOLDOWN);
		return true;
	}

	/** C: the ball hammers the ground -- 14 damage within 6 blocks, knocked down and slowed for 2 s. */
	static boolean spinQuake(ServerPlayer p) {
		int i = Spin.readyBall(p);
		if (i < 0) {
			return false;
		}
		Spin.takeBusy(p, i, SpinConfig.QUAKE_BALL_TICKS, net.minecraft.world.InteractionHand.MAIN_HAND);
		ServerLevel level = (ServerLevel) p.level();
		Vec3 c = p.position();
		for (LivingEntity t : HeroTargets.harmable(level, p, p.getBoundingBox().inflate(SpinConfig.QUAKE_RADIUS, 2.0, SpinConfig.QUAKE_RADIUS))) {
			if (t.distanceToSqr(c) > SpinConfig.QUAKE_RADIUS * SpinConfig.QUAKE_RADIUS) {
				continue;
			}
			if (Spin.hit(p, t, SpinConfig.QUAKE_DAMAGE, false)) {
				Vec3 away = t.position().subtract(c).multiply(1, 0, 1);
				away = away.lengthSqr() < 1.0e-4 ? Vec3.ZERO : away.normalize();
				t.setDeltaMovement(away.x * 0.5, 0.45, away.z * 0.5);
				t.hurtMarked = true;
				t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SpinConfig.QUAKE_SLOW_TICKS, 3));
			}
		}
		BlockState floor = level.getBlockState(p.blockPosition().below());
		if (!floor.isAir()) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, floor), c.x, c.y + 0.1, c.z, 80, 2.5, 0.1, 2.5, 0.2);
		}
		fx(p, SpinFxPayload.at(SpinFxPayload.QUAKE, c, 12)); // the spinning shock ring (rendered; the block dust stays)
		level.playSound(null, c.x, c.y, c.z, SpinSounds.QUAKE, SoundSource.PLAYERS, 0.9f, 1.0f);
		pay(p, SpinAbilityManager.SPIN_QUAKE, SpinConfig.QUAKE_COST, SpinConfig.QUAKE_COOLDOWN);
		p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
		return true;
	}

	/** Shift+C: both balls orbit him for 4 s. */
	static boolean spinShield(ServerPlayer p) {
		int main = SpinBalls.inHand(p, net.minecraft.world.InteractionHand.MAIN_HAND);
		int off = SpinBalls.inHand(p, net.minecraft.world.InteractionHand.OFF_HAND);
		if (main < 0 || off < 0) {
			return false;
		}
		Spin.takeBusy(p, main, SpinConfig.SHIELD_TICKS, net.minecraft.world.InteractionHand.MAIN_HAND);
		Spin.takeBusy(p, off, SpinConfig.SHIELD_TICKS, net.minecraft.world.InteractionHand.OFF_HAND);
		SpinState n = Spin.state(p).copy();
		n.shieldUntil = Spin.now(p) + SpinConfig.SHIELD_TICKS;
		Spin.save(p, n);
		SHIELD_HITS.remove(p.getUUID());
		Spin.sound(p, SpinSounds.BALL_THROW, 0.7f, 1.3f);
		pay(p, SpinAbilityManager.SPIN_SHIELD, SpinConfig.SHIELD_COST, SpinConfig.SHIELD_COOLDOWN);
		return true;
	}

	/** V: 6 hearts over 3 s and poison / slowness cleared -- on himself, or on the squadmate he's aiming at. */
	static boolean healingSpin(ServerPlayer p) {
		LivingEntity who = healTarget(p);
		SpinMarks.heal(who, SpinConfig.HEAL_TOTAL / SpinConfig.HEAL_TICKS, SpinConfig.HEAL_TICKS);
		who.removeEffect(MobEffects.POISON);
		who.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
		Vec3 c = who.position().add(0, who.getBbHeight() * 0.5, 0);
		ServerLevel level = (ServerLevel) p.level();
		level.sendParticles(Spin.dust(Spin.GREEN, 1.0f), c.x, c.y, c.z, 20, 0.35, 0.5, 0.35, 0.0);
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, c.x, c.y, c.z, 8, 0.4, 0.5, 0.4, 0.0);
		level.playSound(null, c.x, c.y, c.z, SpinSounds.HEAL, SoundSource.PLAYERS, 0.7f, 1.0f);
		if (who != p && who instanceof ServerPlayer mate) {
			mate.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.projecthero.spin.healed_by",
					p.getDisplayName()).withStyle(ChatFormatting.GREEN), true);
			p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.projecthero.spin.heal_mate",
					mate.getDisplayName()).withStyle(ChatFormatting.GREEN), true);
		}
		pay(p, SpinAbilityManager.HEALING_SPIN, SpinConfig.HEAL_COST, SpinConfig.HEAL_COOLDOWN);
		return true;
	}

	/** The squadmate he's aiming at within 16 blocks, else himself. */
	public static LivingEntity healTarget(ServerPlayer p) {
		Vec3 eye = p.getEyePosition();
		Vec3 end = eye.add(p.getViewVector(1f).scale(SpinConfig.HEAL_AIM_RANGE));
		BlockHitResult block = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		if (block.getType() != HitResult.Type.MISS) {
			end = block.getLocation();
		}
		AABB box = p.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0);
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(p.level(), p, eye, end, box,
				e -> e instanceof Player other && other != p && other.isAlive() && Squads.areAllies(p, other), 0.5f);
		return hit != null && hit.getEntity() instanceof LivingEntity le ? le : p;
	}

	/** Shift+V: Face Spin, a 20 s disguise. */
	static boolean faceSpin(ServerPlayer p) {
		SpinState n = Spin.state(p).copy();
		n.disguiseUntil = Spin.now(p) + SpinConfig.FACE_TICKS;
		Spin.save(p, n);
		SpinDisguise.start(p);
		Vec3 c = p.getEyePosition();
		((ServerLevel) p.level()).sendParticles(Spin.dust(Spin.GOLD, 0.8f), c.x, c.y, c.z, 16, 0.25, 0.25, 0.25, 0.0);
		Spin.sound(p, SpinSounds.BODY_SPIN, 0.6f, 1.4f);
		p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.projecthero.spin.disguised").withStyle(ChatFormatting.GOLD), true);
		pay(p, SpinAbilityManager.FACE_SPIN, SpinConfig.FACE_COST, SpinConfig.FACE_COOLDOWN);
		return true;
	}

	// ---------------------------------------------------------------- the holds (the Golden Rectangle)

	/** Shift+G / Shift+Z went down. True if the rectangle started. */
	public static boolean startCharge(ServerPlayer p, String id) {
		if (Spin.charging(p) != SpinState.CHARGE_NONE || !ready(p, id)) {
			return false;
		}
		SpinState n = Spin.state(p).copy();
		n.chargeStart = Spin.now(p);
		n.chargeMove = SpinAbilityManager.BALL_BREAKER.equals(id) ? SpinState.CHARGE_BREAKER : SpinState.CHARGE_GOLDEN;
		n.chargeTicks = Spin.chargeTicksFor(p);
		Spin.save(p, n);
		return true;
	}

	/** The key came up: fire (Perfect if it lined up). True if a move went. */
	public static boolean releaseCharge(ServerPlayer p, String id) {
		SpinState s = Spin.state(p);
		if (s.chargeStart <= 0L) {
			return false;
		}
		int elapsed = (int) Math.max(0L, Spin.now(p) - s.chargeStart);
		int target = s.chargeTicks > 0 ? s.chargeTicks : Spin.chargeTicksFor(p);
		int move = s.chargeMove;
		SpinState n = s.copy();
		n.chargeStart = 0L;
		n.chargeMove = SpinState.CHARGE_NONE;
		Spin.save(p, n);
		boolean perfect = Spin.isPerfect(elapsed, target);
		fxSelf(p, new SpinFxPayload(SpinFxPayload.PERFECT, -1, Vec3.ZERO, Vec3.ZERO, 0, perfect, new int[0]));
		return move == SpinState.CHARGE_BREAKER ? ballBreaker(p, perfect) : goldenSpin(p, perfect);
	}

	/** Shift+G released: 35 damage (70 and through armour on a Perfect Spin) to everything on a 40-block line, walls or not. */
	static boolean goldenSpin(ServerPlayer p, boolean perfect) {
		int i = Spin.readyBall(p);
		if (i < 0 || !Spin.hasSpin(p, SpinConfig.GOLDEN_COST)) {
			Spin.say(p, i < 0 ? "message.projecthero.spin.need_ball" : "message.projecthero.spin.no_spin", ChatFormatting.GRAY);
			return false;
		}
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getViewVector(1f);
		Vec3 end = eye.add(look.scale(SpinConfig.GOLDEN_RANGE));
		float dmg = perfect ? SpinConfig.GOLDEN_PERFECT_DAMAGE : SpinConfig.GOLDEN_DAMAGE;
		AABB sweep = new AABB(eye, end).inflate(SpinConfig.GOLDEN_WIDTH + 1.0);
		List<LivingEntity> line = new ArrayList<>();
		for (LivingEntity t : p.level().getEntitiesOfClass(LivingEntity.class, sweep, e -> e != p && HeroTargets.canHarm(p, e))) {
			if (t.getBoundingBox().inflate(SpinConfig.GOLDEN_WIDTH).clip(eye, end).isPresent() || t.getBoundingBox().contains(eye)) {
				line.add(t);
			}
		}
		for (LivingEntity t : line) {
			if (Spin.hit(p, t, dmg, perfect)) {
				SpinMarks.spin(t, SpinConfig.SPIN_TICKS);
			}
		}
		SteelBallEntity.launch(p, i, SteelBallEntity.MODE_PIERCE, look.scale(SpinConfig.BALL_SPEED * 1.8), end, false,
				SpinConfig.GOLDEN_RANGE, true);
		fx(p, new SpinFxPayload(SpinFxPayload.GOLDEN_LINE, -1, Spin.handPos(p), end, 10, perfect, new int[0])); // fades over 0.5 s
		Spin.sound(p, SpinSounds.GOLDEN_RELEASE, perfect ? 1.0f : 0.8f, perfect ? 1.25f : 1.0f);
		if (perfect) {
			p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.projecthero.spin.perfect").withStyle(ChatFormatting.GOLD,
					ChatFormatting.BOLD), true);
		}
		pay(p, SpinAbilityManager.GOLDEN_SPIN, SpinConfig.GOLDEN_COST, SpinConfig.GOLDEN_COOLDOWN);
		lastGoldenHits = line.size();
		p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
		return true;
	}

	/** For the tests: how many creatures the last Golden Spin's line held. */
	public static int lastGoldenHits;

	/** For the tests: why the last Ball Breaker release failed. */
	public static String lastRefusal = "";

	/**
	 * Shift+Z released: only a Perfect Spin calls Ball Breaker out of the ball -- it strikes the creature he aims at for 60 (through
	 * armour) and ages it. A release that isn't Perfect fails and costs {@link SpinConfig#BREAKER_FAIL_COST}.
	 */
	static boolean ballBreaker(ServerPlayer p, boolean perfect) {
		if (!perfect) {
			lastRefusal = "not perfect";
			Spin.spendSpin(p, Math.min(SpinConfig.BREAKER_FAIL_COST, Spin.state(p).spin));
			p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.projecthero.spin.breaker_failed")
					.withStyle(ChatFormatting.RED), true);
			return false;
		}
		int i = Spin.readyBall(p);
		if (i < 0 || !Spin.hasSpin(p, SpinConfig.BREAKER_COST)) {
			lastRefusal = "ball " + i + " spin " + Spin.state(p).spin;
			Spin.say(p, i < 0 ? "message.projecthero.spin.need_ball" : "message.projecthero.spin.no_spin", ChatFormatting.GRAY);
			return false;
		}
		LivingEntity t = aimed(p, SpinConfig.BREAKER_RANGE);
		if (t == null) {
			lastRefusal = "no target";
			Spin.say(p, "message.projecthero.spin.no_target", ChatFormatting.GRAY);
			return false;
		}
		Vec3 to = t.position().add(0, t.getBbHeight() * 0.55, 0);
		Vec3 from = Spin.handPos(p);
		Vec3 dir = to.subtract(from);
		double dist = Math.max(0.5, dir.length());
		SteelBallEntity.launch(p, i, SteelBallEntity.MODE_BREAKER, dir.normalize().scale(Math.max(SpinConfig.BALL_SPEED, dist / SpinConfig.BREAKER_STRIKE_DELAY)),
				to, false, SpinConfig.BREAKER_RANGE + 2, true);
		STRIKES.put(p.getUUID(), new Strike(t, Spin.now(p) + SpinConfig.BREAKER_STRIKE_DELAY));
		fx(p, new SpinFxPayload(SpinFxPayload.BREAKER, t.getId(), from, to, SpinConfig.BREAKER_STRIKE_DELAY, true, new int[0]));
		Spin.sound(p, SpinSounds.BALL_BREAKER, 1.0f, 1.0f);
		p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.projecthero.spin.ball_breaker").withStyle(ChatFormatting.GOLD,
				ChatFormatting.BOLD), true);
		pay(p, SpinAbilityManager.BALL_BREAKER, SpinConfig.BREAKER_COST, SpinConfig.BREAKER_COOLDOWN);
		p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
		return true;
	}

	/** Ball Breaker's figure is on its way (for the tests). */
	public static boolean strikePending(ServerPlayer p) {
		return STRIKES.containsKey(p.getUUID());
	}

	// ---------------------------------------------------------------- tick

	static void tick(ServerPlayer p) {
		SpinState s = Spin.state(p);
		long now = Spin.now(p);
		// a hold held too long goes on its own (never Perfect); losing the ball cancels it
		if (s.chargeStart > 0L) {
			int target = s.chargeTicks > 0 ? s.chargeTicks : Spin.chargeTicksFor(p);
			if (Spin.readyBall(p) < 0) { // the ball left his main hand
				SpinState n = s.copy();
				n.chargeStart = 0L;
				n.chargeMove = SpinState.CHARGE_NONE;
				Spin.save(p, n);
			} else if (now - s.chargeStart > target + SpinConfig.CHARGE_OVERHOLD) {
				releaseCharge(p, s.chargeMove == SpinState.CHARGE_BREAKER ? SpinAbilityManager.BALL_BREAKER : SpinAbilityManager.GOLDEN_SPIN);
			}
		}
		// Ball Breaker strikes
		Strike strike = STRIKES.get(p.getUUID());
		if (strike != null && now >= strike.at) {
			STRIKES.remove(p.getUUID());
			LivingEntity t = strike.target;
			if (t.isAlive() && !t.isRemoved() && t.level() == p.level()) {
				Spin.hit(p, t, SpinConfig.BREAKER_DAMAGE, true);
				if (t.isAlive()) {
					SpinMarks.age(t, SpinConfig.AGING_TICKS);
				}
				Vec3 c = t.position().add(0, t.getBbHeight() * 0.6, 0);
				ServerLevel level = (ServerLevel) p.level();
				level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 1, 0, 0, 0, 0);
				level.sendParticles(Spin.dust(0xE8E4D8, 1.6f), c.x, c.y, c.z, 30, 0.4, 0.6, 0.4, 0.0);
				level.playSound(null, c.x, c.y, c.z, SpinSounds.BALL_HIT, SoundSource.PLAYERS, 1.0f, 0.7f);
			}
		}
		// Spin Shield
		if (s.shieldUntil > now) {
			shieldTick(p);
		} else if (s.shieldUntil > 0L) {
			SpinState n = s.copy();
			n.shieldUntil = 0L;
			Spin.save(p, n);
			SHIELD_HITS.remove(p.getUUID());
		}
	}

	/** The orbit: projectiles heading in are knocked back out (his now); anything close is struck every 10 ticks. */
	private static void shieldTick(ServerPlayer p) {
		Vec3 c = p.position().add(0, 1.0, 0);
		AABB box = p.getBoundingBox().inflate(SpinConfig.SHIELD_DEFLECT_RADIUS);
		for (Projectile proj : p.level().getEntitiesOfClass(Projectile.class, box, e -> !(e instanceof SteelBallEntity) && e.getOwner() != p)) {
			Vec3 v = proj.getDeltaMovement();
			Vec3 toMe = c.subtract(proj.position());
			if (v.lengthSqr() < 1.0e-4 || v.dot(toMe) <= 0) {
				continue; // already going away
			}
			Vec3 out = proj.position().subtract(c).normalize();
			proj.setDeltaMovement(out.scale(Math.max(0.6, v.length())));
			proj.setOwner(p);
			proj.hurtMarked = true;
			p.level().playSound(null, proj.getX(), proj.getY(), proj.getZ(), SpinSounds.BALL_HIT, SoundSource.PLAYERS, 0.5f, 1.4f);
			deflected++;
		}
		Map<Integer, Long> hits = SHIELD_HITS.computeIfAbsent(p.getUUID(), k -> new HashMap<>());
		long now = Spin.now(p);
		for (LivingEntity t : HeroTargets.harmable(p.level(), p, p.getBoundingBox().inflate(SpinConfig.SHIELD_HIT_RADIUS))) {
			Long last = hits.get(t.getId());
			if (last != null && now - last < SpinConfig.SHIELD_HIT_GAP && now >= last) {
				continue;
			}
			hits.put(t.getId(), now);
			if (Spin.hit(p, t, SpinConfig.SHIELD_DAMAGE, false)) {
				Vec3 away = t.position().subtract(p.position()).multiply(1, 0, 1);
				if (away.lengthSqr() > 1.0e-4) {
					t.knockback(0.3, -away.x, -away.z);
				}
			}
		}
	}

	/** For the tests: projectiles turned away by Spin Shields so far. */
	public static int deflected;

	/** Every ball he has out comes home at once (revoke, death, a new world). */
	static void recallBalls(ServerPlayer p) {
		if (!(p.level() instanceof ServerLevel level)) {
			return;
		}
		for (SteelBallEntity ball : level.getEntitiesOfClass(SteelBallEntity.class, p.getBoundingBox().inflate(SpinConfig.GOLDEN_RANGE + 16),
				b -> b.getOwner() == p)) {
			ball.discard();
		}
	}
}
