package com.projecthero.mod.miraculous.ladybug;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.data.MiraculousState;
import com.projecthero.mod.miraculous.ladybug.network.LadybugGlowPayload;
import com.projecthero.mod.miraculous.ladybug.network.LadybugPingPayload;
import com.projecthero.mod.squad.Squad;
import com.projecthero.mod.squad.SquadManager;
import com.projecthero.mod.titanshifter.TitanCombat;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * v0.16.6: Ladybug's Yo-yo moves (server side). Every move writes what the clients draw into the synced
 * {@code MiraculousState} ({@link LadybugKeys}) and plays a pose ({@code Miraculous.setAnim}); damage, roots, throws,
 * the line and the shield are all decided here.
 *
 * <p>The swing / zip <b>movement</b> runs on the owning client (the Spider-Man split: {@code SpiderSwing.applyRope}
 * there, no per-tick velocity packets); the server owns the hook, the rope, the end of the line and the anti-float
 * tolerance. Roots and flings are server-side sessions ticked from {@link #serverTick}.
 */
public final class LadybugMoves {
	/** Red dust, the Yo-yo's colour. */
	public static final DustParticleOptions RED = new DustParticleOptions(new Vector3f(0.92f, 0.08f, 0.12f), 1.0f);
	public static final DustParticleOptions BLACK = new DustParticleOptions(new Vector3f(0.05f, 0.05f, 0.06f), 0.8f);

	private record Root(UUID owner, ResourceKey<Level> dim, Vec3 pin, long until) {
	}

	private record Fling(UUID owner, long since) {
	}

	/** entity id -> where it is held and till when (bind, trip wire). */
	private static final Map<Integer, Root> ROOTS = new ConcurrentHashMap<>();
	/** entity id -> a Lasso Fling in the air. */
	private static final Map<Integer, Fling> FLINGS = new ConcurrentHashMap<>();
	/** the block each line hangs from. */
	private static final Map<UUID, BlockPos> LINE_BLOCK = new ConcurrentHashMap<>();
	/** the entities the current trip wire already caught (once each). */
	private static final Map<UUID, Set<Integer>> WIRE_CAUGHT = new ConcurrentHashMap<>();
	/** the last scan / phone glow list (the gametests read it). */
	private static final Map<UUID, List<Integer>> LAST_MARKED = new ConcurrentHashMap<>();

	private LadybugMoves() {
	}

	static void clearSessionState() {
		ROOTS.clear();
		FLINGS.clear();
		LINE_BLOCK.clear();
		WIRE_CAUGHT.clear();
		LAST_MARKED.clear();
		RESCUES.clear();
	}

	static void forget(UUID id) {
		LINE_BLOCK.remove(id);
		WIRE_CAUGHT.remove(id);
		LAST_MARKED.remove(id);
		RESCUES.remove(id);
		ROOTS.values().removeIf(r -> r.owner().equals(id));
	}

	// ---------------------------------------------------------------- helpers

	private static long now(ServerPlayer p) {
		return p.level().getGameTime();
	}

	private static MiraculousState st(ServerPlayer p) {
		return Miraculous.state(p);
	}

	private static void sound(ServerPlayer p, net.minecraft.sounds.SoundEvent s, float vol, float pitch) {
		p.level().playSound(null, p.getX(), p.getY(), p.getZ(), s, SoundSource.PLAYERS, vol, pitch);
	}

	private static void sound(ServerPlayer p, net.minecraft.core.Holder<net.minecraft.sounds.SoundEvent> s, float vol, float pitch) {
		p.level().playSound(null, p.getX(), p.getY(), p.getZ(), s, SoundSource.PLAYERS, vol, pitch);
	}

	private static Vec3 mid(Entity e) {
		return e.position().add(0, e.getBbHeight() * 0.5, 0);
	}

	/** Where the Yo-yo leaves her hand (server estimate: right shoulder height, a little ahead). */
	public static Vec3 hand(Player p) {
		Vec3 right = Vec3.directionFromRotation(0f, p.yBodyRot + 90f);
		return p.position().add(0, p.getBbHeight() * 0.72, 0).add(right.scale(-0.34)).add(p.getLookAngle().scale(0.4));
	}

	/** A pick along the look: a creature she may hit, else null. */
	private static @Nullable LivingEntity aimed(ServerPlayer p, double range) {
		LivingEntity t = AbilityHelpers.raycastEntity(p, range);
		return t != null && HeroTargets.canHarm(p, t) ? t : null;
	}

	/** The look's end point: the first block, or the full range. */
	private static Vec3 lookEnd(ServerPlayer p, double range) {
		BlockHitResult b = AbilityHelpers.raycastBlock(p, range);
		return b.getType() == HitResult.Type.MISS ? p.getEyePosition().add(p.getLookAngle().scale(range)) : b.getLocation();
	}

	private static void send(ServerPlayer to, net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
		if (ServerPlayNetworking.canSend(to, payload.type())) {
			ServerPlayNetworking.send(to, payload);
		}
	}

	/** v0.16.6: hold {@code e} where it is for {@code ticks} (bosses for at most {@link LadybugConfig#BIND_BOSS_TICKS}). */
	public static int root(ServerPlayer owner, LivingEntity e, int ticks) {
		if (e instanceof Mob m && TitanCombat.isBoss(m)) {
			ticks = Math.min(ticks, LadybugConfig.BIND_BOSS_TICKS);
		}
		if (e instanceof Player) {
			if (!AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, ticks, 6)) {
				return 0;
			}
			ticks = Math.max(10, ticks / 2);
		} else {
			e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 6, false, false, false));
		}
		ROOTS.put(e.getId(), new Root(owner.getUUID(), e.level().dimension(), e.position(), now(owner) + ticks));
		return ticks;
	}

	public static boolean rooted(Entity e) {
		Root r = ROOTS.get(e.getId());
		return r != null && r.until() > e.level().getGameTime();
	}

	private static void unroot(Entity e) {
		ROOTS.remove(e.getId());
		if (e instanceof LivingEntity le) {
			le.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
		}
	}

	// ---------------------------------------------------------------- R

	/** Yo-yo Strike: whipped out along the look; the first creature in the way takes {@link LadybugConfig#STRIKE_DAMAGE}. */
	public static boolean strike(ServerPlayer p) {
		if (!Miraculous.ready(p, LadybugKeys.STRIKE)) {
			return false;
		}
		LivingEntity t = aimed(p, LadybugConfig.STRIKE_RANGE);
		if (t == null) {
			t = strikeAssist(p); // v0.16.7 (user): a slight auto-aim
		}
		Vec3 tip;
		if (t != null) {
			tip = mid(t);
			if (AbilityHelpers.hurtBurst(p, t, LadybugConfig.STRIKE_DAMAGE) || AbilityHelpers.hurtLands(p, t, 0f)) {
				AbilityHelpers.knockbackFrom(t, p.position(), 0.45);
				((ServerLevel) p.level()).sendParticles(ParticleTypes.CRIT, tip.x, tip.y, tip.z, 10, 0.2, 0.2, 0.2, 0.2);
				sound(p, SoundEvents.PLAYER_ATTACK_STRONG, 0.8f, 1.3f);
			}
		} else {
			tip = lookEnd(p, LadybugConfig.STRIKE_RANGE);
		}
		Vec3 at = tip;
		Miraculous.edit(p, n -> n.vecs.put(LadybugKeys.P_TIP, List.of(at.x, at.y, at.z)));
		Miraculous.setAnim(p, LadybugKeys.ANIM_STRIKE);
		Miraculous.setCooldown(p, LadybugKeys.STRIKE, LadybugConfig.STRIKE_COOLDOWN);
		sound(p, SoundEvents.FISHING_BOBBER_THROW, 0.7f, 1.5f);
		return true;
	}

	/**
	 * v0.16.7 (user): Yo-yo Strike's slight auto-aim, when the crosshair itself is on nothing -- the enemy nearest the
	 * aim within {@link LadybugConfig#STRIKE_ASSIST_COS} of it, or within {@link LadybugConfig#STRIKE_ASSIST_RADIUS} blocks
	 * of the aim line (so a close one just off the crosshair counts), in reach and in sight. Never a squadmate or a pet.
	 */
	static @Nullable LivingEntity strikeAssist(ServerPlayer p) {
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		LivingEntity best = null;
		double bestScore = Double.MAX_VALUE;
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, eye, LadybugConfig.STRIKE_RANGE)) {
			Vec3 to = mid(e).subtract(eye);
			double along = to.dot(look);
			if (along <= 0.5 || along > LadybugConfig.STRIKE_RANGE) {
				continue;
			}
			double off = to.subtract(look.scale(along)).length();
			double cos = along / to.length();
			if ((cos < LadybugConfig.STRIKE_ASSIST_COS && off > LadybugConfig.STRIKE_ASSIST_RADIUS) || !p.hasLineOfSight(e)) {
				continue;
			}
			double score = off / along; // closest to the crosshair
			if (score < bestScore) {
				bestScore = score;
				best = e;
			}
		}
		return best;
	}

	/** Ricochet: up to {@link LadybugConfig#RICOCHET_TARGETS} creatures, each within a hop of the last, {@link LadybugConfig#RICOCHET_DAMAGE} each. */
	public static int ricochet(ServerPlayer p) {
		if (!Miraculous.ready(p, LadybugKeys.RICOCHET)) {
			return 0;
		}
		LivingEntity first = aimed(p, LadybugConfig.STRIKE_RANGE);
		if (first == null) {
			// a forgiving pick: the nearest threat near the look
			Vec3 look = p.getLookAngle();
			double best = 0.9;
			for (LivingEntity e : AbilityHelpers.hostilesAround(p, p.getEyePosition(), LadybugConfig.STRIKE_RANGE)) {
				Vec3 d = mid(e).subtract(p.getEyePosition()).normalize();
				double dot = d.dot(look);
				if (dot > best && p.hasLineOfSight(e)) {
					best = dot;
					first = e;
				}
			}
		}
		if (first == null) {
			Miraculous.say(p, "message.projecthero.ladybug.no_target", ChatFormatting.GRAY);
			return 0;
		}
		List<LivingEntity> hit = new ArrayList<>();
		hit.add(first);
		LivingEntity last = first;
		while (hit.size() < LadybugConfig.RICOCHET_TARGETS) {
			LivingEntity next = null;
			double bestD = Double.MAX_VALUE;
			for (LivingEntity e : AbilityHelpers.hostilesAround(p, mid(last), LadybugConfig.RICOCHET_HOP)) {
				if (hit.contains(e) || !last.hasLineOfSight(e)) {
					continue;
				}
				double d = e.distanceToSqr(last);
				if (d < bestD) {
					bestD = d;
					next = e;
				}
			}
			if (next == null) {
				break;
			}
			hit.add(next);
			last = next;
		}
		List<Double> pts = new ArrayList<>();
		ServerLevel level = (ServerLevel) p.level();
		Vec3 from = hand(p);
		for (LivingEntity e : hit) {
			AbilityHelpers.hurtBurst(p, e, LadybugConfig.RICOCHET_DAMAGE);
			Vec3 m = mid(e);
			AbilityHelpers.knockbackFrom(e, from, 0.3);
			level.sendParticles(ParticleTypes.CRIT, m.x, m.y, m.z, 8, 0.2, 0.2, 0.2, 0.2);
			pts.add(m.x);
			pts.add(m.y);
			pts.add(m.z);
			from = m;
		}
		Miraculous.edit(p, n -> n.vecs.put(LadybugKeys.P_RICO, List.copyOf(pts)));
		Miraculous.setAnim(p, LadybugKeys.ANIM_RICOCHET);
		Miraculous.setCooldown(p, LadybugKeys.RICOCHET, LadybugConfig.RICOCHET_COOLDOWN);
		sound(p, SoundEvents.FISHING_BOBBER_THROW, 0.7f, 1.8f);
		sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.6f, 1.4f);
		return hit.size();
	}

	// ---------------------------------------------------------------- G

	private static @Nullable LivingEntity boundTarget(ServerPlayer p) {
		MiraculousState s = st(p);
		if (s.val(LadybugKeys.V_BIND) == 0L || s.val(LadybugKeys.V_BIND_UNTIL) < now(p)) {
			return null;
		}
		Entity e = p.level().getEntity((int) (s.val(LadybugKeys.V_BIND) - 1L));
		return e instanceof LivingEntity le && le.isAlive() ? le : null;
	}

	/** G: bind (or, while one holds, yank it in). */
	public static boolean bindOrYank(ServerPlayer p) {
		LivingEntity bound = boundTarget(p);
		if (bound != null) {
			return yank(p, bound);
		}
		if (!Miraculous.ready(p, LadybugKeys.BIND)) {
			return false;
		}
		LivingEntity t = aimed(p, LadybugConfig.BIND_RANGE);
		if (t == null) {
			Miraculous.say(p, "message.projecthero.ladybug.no_target", ChatFormatting.GRAY);
			return false;
		}
		int ticks = root(p, t, LadybugConfig.BIND_TICKS);
		if (ticks <= 0) {
			return false;
		}
		long until = now(p) + ticks;
		Miraculous.edit(p, n -> {
			n.vals.put(LadybugKeys.V_BIND, t.getId() + 1L);
			n.vals.put(LadybugKeys.V_BIND_UNTIL, until);
		});
		Miraculous.setAnim(p, LadybugKeys.ANIM_BIND);
		Miraculous.setCooldown(p, LadybugKeys.BIND, LadybugConfig.BIND_COOLDOWN);
		sound(p, SoundEvents.FISHING_BOBBER_THROW, 0.7f, 1.2f);
		sound(p, SoundEvents.LEASH_KNOT_PLACE, 0.8f, 1.2f);
		return true;
	}

	private static void endBind(ServerPlayer p, @Nullable LivingEntity t) {
		if (t != null) {
			unroot(t);
		}
		Miraculous.edit(p, n -> {
			n.vals.remove(LadybugKeys.V_BIND);
			n.vals.remove(LadybugKeys.V_BIND_UNTIL);
		});
	}

	/** G again inside the bind: reeled in to just in front of her (never a boss). */
	public static boolean yank(ServerPlayer p, LivingEntity t) {
		endBind(p, t);
		if (t instanceof Mob m && TitanCombat.isBoss(m)) {
			Miraculous.say(p, "message.projecthero.ladybug.too_heavy", ChatFormatting.GRAY);
			return false;
		}
		Vec3 flat = new Vec3(p.getLookAngle().x, 0, p.getLookAngle().z);
		flat = flat.lengthSqr() < 1.0E-4 ? Vec3.ZERO : flat.normalize();
		Vec3 dest = p.position().add(flat.scale(LadybugConfig.YANK_STOP));
		Vec3 d = dest.subtract(t.position());
		Vec3 v = new Vec3(d.x * 0.28, Math.max(0.35, d.y * 0.25 + 0.3), d.z * 0.28);
		if (v.length() > 2.6) {
			v = v.normalize().scale(2.6);
		}
		t.setDeltaMovement(v);
		t.hurtMarked = true;
		t.resetFallDistance();
		Miraculous.setAnim(p, LadybugKeys.ANIM_YANK);
		Miraculous.edit(p, n -> n.vals.put(LadybugKeys.V_FLUNG, t.getId() + 1L));
		sound(p, SoundEvents.LEASH_KNOT_BREAK, 0.8f, 1.5f);
		return true;
	}

	/** Shift+G: Lasso Fling -- the bound creature (or one bound on the spot) thrown the way she looks. */
	public static boolean fling(ServerPlayer p) {
		if (!Miraculous.ready(p, LadybugKeys.FLING)) {
			return false;
		}
		LivingEntity t = boundTarget(p);
		if (t == null) {
			t = aimed(p, LadybugConfig.BIND_RANGE);
		}
		if (t == null) {
			Miraculous.say(p, "message.projecthero.ladybug.no_target", ChatFormatting.GRAY);
			return false;
		}
		if (t instanceof Mob m && TitanCombat.isBoss(m)) {
			Miraculous.say(p, "message.projecthero.ladybug.too_heavy", ChatFormatting.GRAY);
			return false;
		}
		if (t instanceof Player && !HeroTargets.canHarm(p, t)) {
			return false;
		}
		endBind(p, t);
		Vec3 v = p.getLookAngle().scale(LadybugConfig.FLING_SPEED).add(0, LadybugConfig.FLING_LIFT, 0);
		t.setDeltaMovement(v);
		t.hurtMarked = true;
		FLINGS.put(t.getId(), new Fling(p.getUUID(), now(p)));
		LivingEntity target = t;
		Miraculous.edit(p, n -> n.vals.put(LadybugKeys.V_FLUNG, target.getId() + 1L));
		Miraculous.setAnim(p, LadybugKeys.ANIM_FLING);
		Miraculous.setCooldown(p, LadybugKeys.FLING, LadybugConfig.FLING_COOLDOWN);
		sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.9f, 0.8f);
		return true;
	}

	public static boolean flying(Entity e) {
		return FLINGS.containsKey(e.getId());
	}

	// ---------------------------------------------------------------- X

	/** X (down): hook a block and swing (Shift: zip straight to it). X again / let go: release. */
	public static boolean lineDown(ServerPlayer p, boolean shift) {
		if (st(p).val(LadybugKeys.V_LINE) != 0L) {
			detach(p, true);
			return false;
		}
		String id = shift ? LadybugKeys.ZIP : LadybugKeys.SWING;
		if (!Miraculous.ready(p, id)) {
			return false;
		}
		// v0.16.7 (user): the Zip Line reaches 75 blocks, the Swing 45
		double range = shift ? LadybugConfig.ZIP_RANGE : LadybugConfig.SWING_RANGE;
		BlockHitResult b = AbilityHelpers.raycastBlock(p, range);
		if (b.getType() != HitResult.Type.BLOCK) {
			Miraculous.say(p, "message.projecthero.ladybug.no_hook", ChatFormatting.GRAY);
			return false;
		}
		Vec3 hook = b.getLocation();
		double rope = Math.max(3.0, Math.min(range + 2.0, hook.distanceTo(p.getEyePosition())));
		long t = now(p);
		Miraculous.edit(p, n -> {
			n.vals.put(LadybugKeys.V_LINE, shift ? 2L : 1L);
			n.vals.put(LadybugKeys.V_ROPE, Math.round(rope * 1000.0));
			n.vals.put(LadybugKeys.V_LINE_AT, t);
			n.vecs.put(LadybugKeys.P_HOOK, List.of(hook.x, hook.y, hook.z));
		});
		LINE_BLOCK.put(p.getUUID(), b.getBlockPos());
		Miraculous.setAnim(p, shift ? LadybugKeys.ANIM_ZIP : LadybugKeys.ANIM_SWING);
		if (shift) {
			Miraculous.setCooldown(p, LadybugKeys.ZIP, LadybugConfig.ZIP_COOLDOWN);
		}
		floatTolerance(p, true);
		sound(p, SoundEvents.FISHING_BOBBER_THROW, 0.8f, 1.1f);
		((ServerLevel) p.level()).sendParticles(RED, hook.x, hook.y, hook.z, 6, 0.1, 0.1, 0.1, 0.0);
		return true;
	}

	/** X up: a swing lets go (a zip runs to the end). */
	public static void lineUp(ServerPlayer p) {
		if (st(p).val(LadybugKeys.V_LINE) == 1L) {
			detach(p, true);
		}
	}

	public static void detach(ServerPlayer p, boolean sound) {
		MiraculousState s = st(p);
		long mode = s.val(LadybugKeys.V_LINE);
		LINE_BLOCK.remove(p.getUUID());
		floatTolerance(p, false);
		if (mode == 0L) {
			return;
		}
		Miraculous.edit(p, n -> {
			n.vals.remove(LadybugKeys.V_LINE);
			n.vals.remove(LadybugKeys.V_ROPE);
			n.vals.remove(LadybugKeys.V_LINE_AT);
			n.vecs.remove(LadybugKeys.P_HOOK);
		});
		if (mode == 1L) {
			Miraculous.setCooldown(p, LadybugKeys.SWING, LadybugConfig.SWING_COOLDOWN);
		}
		p.resetFallDistance();
		if (sound) {
			sound(p, SoundEvents.LEASH_KNOT_BREAK, 0.4f, 1.7f);
		}
	}

	private static void tickLine(ServerPlayer p, MiraculousState s) {
		long mode = s.val(LadybugKeys.V_LINE);
		if (mode == 0L) {
			return;
		}
		Vec3 hook = Miraculous.vec(s, LadybugKeys.P_HOOK);
		if (hook == null) {
			detach(p, false);
			return;
		}
		double dist = hook.distanceTo(p.getEyePosition());
		long age = now(p) - s.val(LadybugKeys.V_LINE_AT);
		BlockPos bp = LINE_BLOCK.get(p.getUUID());
		if (bp != null && p.tickCount % 5 == 0 && p.level().getBlockState(bp).getCollisionShape(p.level(), bp).isEmpty()) {
			detach(p, true);
			return;
		}
		if (mode == 2L) {
			if (dist < LadybugConfig.ZIP_ARRIVE || age > LadybugConfig.ZIP_MAX_TICKS) {
				detach(p, false);
				return;
			}
		} else if (dist > LadybugKeys.rope(s) + LadybugConfig.SWING_SLACK_BREAK) {
			detach(p, true);
			return;
		}
		p.resetFallDistance();
		floatTolerance(p, true);
	}

	/**
	 * Vanilla kicks a player airborne for 4 s with no downward motion ("flying is not enabled") -- a good swing looks
	 * just like that. The Spider-Man fix: {@code mayfly} for the line's duration, {@code flying} forced off, handed back
	 * after unless another power still flies her.
	 */
	static void floatTolerance(ServerPlayer p, boolean on) {
		if (p.getAbilities().instabuild || p.isSpectator()) {
			return;
		}
		if (on) {
			if (!p.getAbilities().mayfly || p.getAbilities().flying) {
				p.getAbilities().mayfly = true;
				p.getAbilities().flying = false;
				p.onUpdateAbilities();
			}
			return;
		}
		if (p.getAbilities().mayfly
				&& !com.projecthero.mod.hero.power.HeroFlight.isFlying(p)
				&& !com.projecthero.mod.power.ThorPowers.isFlying(p)
				&& !p.getAttachedOrElse(ModAttachments.IRON_MAN_FLYING, false)
				&& !p.getAttachedOrElse(ModAttachments.REPULSOR_BOOTS_FLYING, false)) {
			p.getAbilities().mayfly = false;
			p.getAbilities().flying = false;
			p.onUpdateAbilities();
		}
	}

	// ---------------------------------------------------------------- Z (held)

	public static boolean shieldUp(ServerPlayer p) {
		if (st(p).val(LadybugKeys.V_SHIELD) > 0L || !Miraculous.ready(p, LadybugKeys.SHIELD)) {
			return false;
		}
		Miraculous.setVal(p, LadybugKeys.V_SHIELD, now(p));
		sound(p, SoundEvents.TRIDENT_RIPTIDE_1, 0.6f, 1.6f);
		return true;
	}

	public static void shieldDown(ServerPlayer p) {
		if (st(p).val(LadybugKeys.V_SHIELD) == 0L) {
			return;
		}
		Miraculous.setVal(p, LadybugKeys.V_SHIELD, 0L);
		Miraculous.setCooldown(p, LadybugKeys.SHIELD, LadybugConfig.SHIELD_COOLDOWN);
	}

	private static Vec3 chest(Player p) {
		return p.position().add(0, p.getBbHeight() * 0.62, 0);
	}

	/** In front of her (within {@link LadybugConfig#SHIELD_FRONT_DOT} of the look, flattened). */
	private static boolean inFront(Player p, Vec3 at) {
		Vec3 d = at.subtract(chest(p));
		Vec3 look = p.getLookAngle();
		Vec3 flatD = new Vec3(d.x, 0, d.z);
		Vec3 flatL = new Vec3(look.x, 0, look.z);
		if (flatD.lengthSqr() < 1.0E-4 || flatL.lengthSqr() < 1.0E-4) {
			return true;
		}
		return flatD.normalize().dot(flatL.normalize()) > LadybugConfig.SHIELD_FRONT_DOT;
	}

	private static void tickShield(ServerPlayer p, MiraculousState s) {
		long since = s.val(LadybugKeys.V_SHIELD);
		if (since == 0L) {
			return;
		}
		if (now(p) - since > LadybugConfig.SHIELD_MAX_TICKS) {
			shieldDown(p);
			return;
		}
		ServerLevel level = (ServerLevel) p.level();
		Vec3 c = chest(p);
		double r = LadybugConfig.SHIELD_REACH;
		for (Projectile proj : level.getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(r + 1.5), e -> e.isAlive())) {
			if (proj.getOwner() == p || proj.position().distanceTo(c) > r + 0.6 || !inFront(p, proj.position())) {
				continue;
			}
			Vec3 v = proj.getDeltaMovement();
			if (v.lengthSqr() > 1.0E-4 && v.dot(c.subtract(proj.position())) <= 0.0) {
				continue; // moving away
			}
			Vec3 at = proj.position();
			if (proj instanceof AbstractArrow && !(proj instanceof ThrownTrident)) {
				proj.discard(); // arrows splinter on the disc
			} else {
				proj.deflect(ProjectileDeflection.REVERSE, p, p, true);
				proj.setOwner(p);
			}
			level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 8, 0.15, 0.15, 0.15, 0.25);
			level.sendParticles(RED, at.x, at.y, at.z, 4, 0.1, 0.1, 0.1, 0.0);
			sound(p, SoundEvents.SHIELD_BLOCK, 0.8f, 1.4f);
		}
		if ((now(p) - since) % 8 == 0) {
			sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.25f, 1.9f);
		}
	}

	/** While the shield spins: shots from the front stop, melee from the front mostly does and the attacker is thrown back. */
	static float shieldDamage(ServerPlayer p, DamageSource source, float amount) {
		if (st(p).val(LadybugKeys.V_SHIELD) == 0L) {
			return amount;
		}
		Entity direct = source.getDirectEntity();
		if (direct == null) {
			return amount;
		}
		if (!inFront(p, direct.position())) {
			return amount;
		}
		if (direct instanceof Projectile) {
			return 0f;
		}
		if (direct instanceof LivingEntity attacker && attacker != p && attacker.distanceTo(p) < 5.0) {
			AbilityHelpers.knockbackFrom(attacker, p.position(), LadybugConfig.SHIELD_KNOCKBACK);
			sound(p, SoundEvents.SHIELD_BLOCK, 0.9f, 1.2f);
			return amount * LadybugConfig.SHIELD_MELEE_PASS;
		}
		return amount;
	}

	// ---------------------------------------------------------------- V

	/** Pole Vault: the string planted ahead, up and over ({@link LadybugConfig#VAULT_UP} up, {@link LadybugConfig#VAULT_FORWARD} forward). */
	public static boolean vault(ServerPlayer p) {
		if (!Miraculous.ready(p, LadybugKeys.VAULT)) {
			return false;
		}
		Vec3 look = p.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0, look.z);
		flat = flat.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0f, p.getYRot()) : flat.normalize();
		Vec3 plant = p.position().add(flat.scale(1.6));
		Vec3 v = flat.scale(LadybugConfig.VAULT_FORWARD).add(0, LadybugConfig.VAULT_UP, 0);
		AbilityHelpers.launchSelf(p, v);
		Miraculous.edit(p, n -> n.vecs.put(LadybugKeys.P_VAULT, List.of(plant.x, plant.y, plant.z)));
		Miraculous.setAnim(p, LadybugKeys.ANIM_VAULT);
		Miraculous.setCooldown(p, LadybugKeys.VAULT, LadybugConfig.VAULT_COOLDOWN);
		sound(p, SoundEvents.FISHING_BOBBER_THROW, 0.7f, 0.9f);
		((ServerLevel) p.level()).sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.1, p.getZ(), 8, 0.3, 0.05, 0.3, 0.02);
		return true;
	}

	/** Shift+V: Trip Wire from her to the block she looks at; whatever crosses it is held {@link LadybugConfig#WIRE_ROOT_TICKS}. */
	public static boolean wire(ServerPlayer p) {
		if (!Miraculous.ready(p, LadybugKeys.WIRE)) {
			return false;
		}
		BlockHitResult b = AbilityHelpers.raycastBlock(p, LadybugConfig.WIRE_RANGE);
		if (b.getType() != HitResult.Type.BLOCK) {
			Miraculous.say(p, "message.projecthero.ladybug.no_hook", ChatFormatting.GRAY);
			return false;
		}
		Vec3 a = p.position().add(0, 0.55, 0);
		Vec3 end = b.getLocation();
		long until = now(p) + LadybugConfig.WIRE_TICKS;
		Miraculous.edit(p, n -> {
			n.vals.put(LadybugKeys.V_WIRE_UNTIL, until);
			n.vecs.put(LadybugKeys.P_WIRE_A, List.of(a.x, a.y, a.z));
			n.vecs.put(LadybugKeys.P_WIRE_B, List.of(end.x, end.y, end.z));
		});
		WIRE_CAUGHT.put(p.getUUID(), new HashSet<>());
		Miraculous.setAnim(p, LadybugKeys.ANIM_WIRE);
		Miraculous.setCooldown(p, LadybugKeys.WIRE, LadybugConfig.WIRE_COOLDOWN);
		sound(p, SoundEvents.TRIPWIRE_ATTACH, 1.0f, 1.0f);
		return true;
	}

	private static void tickWire(ServerPlayer p, MiraculousState s) {
		long until = s.val(LadybugKeys.V_WIRE_UNTIL);
		if (until == 0L) {
			return;
		}
		if (until <= now(p)) {
			WIRE_CAUGHT.remove(p.getUUID());
			Miraculous.edit(p, n -> {
				n.vals.remove(LadybugKeys.V_WIRE_UNTIL);
				n.vecs.remove(LadybugKeys.P_WIRE_A);
				n.vecs.remove(LadybugKeys.P_WIRE_B);
			});
			return;
		}
		Vec3 a = Miraculous.vec(s, LadybugKeys.P_WIRE_A);
		Vec3 b = Miraculous.vec(s, LadybugKeys.P_WIRE_B);
		if (a == null || b == null) {
			return;
		}
		Set<Integer> caught = WIRE_CAUGHT.computeIfAbsent(p.getUUID(), k -> new HashSet<>());
		AABB box = new AABB(a, b).inflate(0.6);
		for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, box, e -> e != p && e.isAlive())) {
			if (caught.contains(e.getId()) || !HeroTargets.canHarm(p, e)) {
				continue;
			}
			AABB eb = e.getBoundingBox().inflate(0.05);
			if (!eb.contains(a) && !eb.contains(b) && eb.clip(a, b).isEmpty()) {
				continue;
			}
			caught.add(e.getId());
			root(p, e, LadybugConfig.WIRE_ROOT_TICKS);
			Vec3 m = mid(e);
			((ServerLevel) p.level()).sendParticles(RED, m.x, e.getY() + 0.5, m.z, 10, 0.3, 0.2, 0.3, 0.0);
			p.level().playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.TRIPWIRE_CLICK_ON, SoundSource.PLAYERS, 1.0f, 1.2f);
		}
	}

	// ---------------------------------------------------------------- C

	/** Yo-yo Scan: every threat within {@link LadybugConfig#SCAN_RADIUS} outlined for her alone. */
	public static List<Integer> scan(ServerPlayer p) {
		if (!Miraculous.ready(p, LadybugKeys.SCAN)) {
			return List.of();
		}
		List<Integer> ids = new ArrayList<>();
		for (LivingEntity e : AbilityHelpers.hostilesAround(p, p.position(), LadybugConfig.SCAN_RADIUS)) {
			ids.add(e.getId());
		}
		LAST_MARKED.put(p.getUUID(), List.copyOf(ids));
		send(p, new LadybugGlowPayload(ids, LadybugConfig.SCAN_TICKS));
		Miraculous.setAnim(p, LadybugKeys.ANIM_SCAN);
		Miraculous.setCooldown(p, LadybugKeys.SCAN, LadybugConfig.SCAN_COOLDOWN);
		Miraculous.say(p, "message.projecthero.ladybug.scan", ChatFormatting.RED, ids.size());
		sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 0.8f, 1.6f);
		return ids;
	}

	/** v0.16.7 (user): Rescue Line pulls in flight: rescuer UUID -> [target entity id, start game time]. */
	private static final Map<UUID, long[]> RESCUES = new ConcurrentHashMap<>();

	/**
	 * v0.16.7 (user): Shift+C Rescue Line -- the Yo-yo string lassos the squadmate she aims at (up to
	 * {@link LadybugConfig#RESCUE_RANGE} blocks, in sight, within a few degrees of the crosshair -- a mob in between does
	 * not get in the way) and reels them to her side; landed, they get Resistance II and a moment of Slow Falling, and no
	 * fall damage. Returns the squadmate (null: nobody to rescue, no cooldown spent).
	 */
	public static @Nullable ServerPlayer rescue(ServerPlayer p) {
		if (!Miraculous.ready(p, LadybugKeys.RESCUE) || RESCUES.containsKey(p.getUUID())) {
			return null;
		}
		ServerPlayer target = rescueTarget(p);
		if (target == null) {
			Miraculous.say(p, "message.projecthero.ladybug.no_rescue", ChatFormatting.GRAY);
			return null;
		}
		RESCUES.put(p.getUUID(), new long[] { target.getId(), now(p) });
		Miraculous.setAnim(p, LadybugKeys.ANIM_YANK);
		Miraculous.setCooldown(p, LadybugKeys.RESCUE, LadybugConfig.RESCUE_COOLDOWN);
		Miraculous.say(p, "message.projecthero.ladybug.rescuing", ChatFormatting.RED, target.getDisplayName());
		sound(p, SoundEvents.FISHING_BOBBER_THROW, 1.0f, 1.2f);
		return target;
	}

	/** The nearest squadmate her aim line passes through (their box, a little enlarged), in range and in sight. */
	static @Nullable ServerPlayer rescueTarget(ServerPlayer p) {
		Vec3 eye = p.getEyePosition();
		Vec3 end = eye.add(p.getLookAngle().scale(LadybugConfig.RESCUE_RANGE));
		ServerPlayer best = null;
		double bestD = Double.MAX_VALUE;
		for (ServerPlayer m : p.serverLevel().players()) {
			if (m == p || !m.isAlive() || m.isSpectator() || !com.projecthero.mod.squad.Squads.areAllies(p, m)) {
				continue;
			}
			java.util.Optional<Vec3> hit = m.getBoundingBox().inflate(LadybugConfig.RESCUE_AIM_INFLATE).clip(eye, end);
			if (hit.isEmpty() && !m.getBoundingBox().inflate(LadybugConfig.RESCUE_AIM_INFLATE).contains(eye)) {
				continue;
			}
			double d = m.distanceTo(p);
			if (d < bestD && p.hasLineOfSight(m)) {
				bestD = d;
				best = m;
			}
		}
		return best;
	}

	/** A Rescue Line in flight: reel the squadmate in; done beside her (or after {@link LadybugConfig#RESCUE_MAX_TICKS}). */
	private static void tickRescue(ServerPlayer p) {
		long[] r = RESCUES.get(p.getUUID());
		if (r == null) {
			return;
		}
		Entity e = p.level().getEntity((int) r[0]);
		long age = now(p) - r[1];
		if (!(e instanceof ServerPlayer m) || !m.isAlive() || m.level() != p.level() || age > LadybugConfig.RESCUE_MAX_TICKS) {
			RESCUES.remove(p.getUUID());
			return;
		}
		Vec3 home = p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(1.2)).add(0, 0.3, 0);
		Vec3 to = home.subtract(m.position());
		double d = to.length();
		m.resetFallDistance();
		if (d < 1.6) {
			RESCUES.remove(p.getUUID());
			m.setDeltaMovement(Vec3.ZERO);
			m.hurtMarked = true;
			m.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, LadybugConfig.RESCUE_RESISTANCE_TICKS, 1, false, true));
			m.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 30, 0, false, false));
			p.serverLevel().sendParticles(RED, m.getX(), m.getY() + 1.0, m.getZ(), 20, 0.4, 0.6, 0.4, 0.0);
			sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 0.9f, 1.5f);
			return;
		}
		double speed = Math.min(LadybugConfig.RESCUE_SPEED, 0.3 + d * 0.25);
		m.setDeltaMovement(to.scale(speed / d).add(0, d > 4 ? 0.08 : 0.0, 0));
		m.hurtMarked = true;
		// the string between her hand and them
		Vec3 a = p.getEyePosition().subtract(0, 0.4, 0), b = m.getBoundingBox().getCenter();
		for (int i = 1; i < 8; i++) {
			Vec3 q = a.lerp(b, i / 8.0);
			p.serverLevel().sendParticles(RED, q.x, q.y, q.z, 1, 0, 0, 0, 0.0);
		}
	}

	public static List<Integer> lastMarked(UUID id) {
		return LAST_MARKED.getOrDefault(id, List.of());
	}

	// ---------------------------------------------------------------- per player / per server tick

	static void tick(ServerPlayer p) {
		MiraculousState s = st(p);
		tickLine(p, s);
		tickShield(p, s);
		tickWire(p, s);
		tickRescue(p);
		if (s.val(LadybugKeys.V_BIND) != 0L && (s.val(LadybugKeys.V_BIND_UNTIL) < now(p) || boundTarget(p) == null)) {
			endBind(p, null);
		}
		if (s.val(LadybugKeys.V_FLUNG) != 0L && s.animId != LadybugKeys.ANIM_FLING && s.animId != LadybugKeys.ANIM_YANK) {
			Miraculous.setVal(p, LadybugKeys.V_FLUNG, 0L);
		}
	}

	/** Detransformation: the line, the shield, her binds and her wire go. */
	static void end(ServerPlayer p) {
		LINE_BLOCK.remove(p.getUUID());
		WIRE_CAUGHT.remove(p.getUUID());
		floatTolerance(p, false);
		UUID id = p.getUUID();
		for (Map.Entry<Integer, Root> e : ROOTS.entrySet()) {
			if (e.getValue().owner().equals(id)) {
				Entity en = p.level().getEntity(e.getKey());
				if (en != null) {
					unroot(en);
				}
			}
		}
		ROOTS.values().removeIf(r -> r.owner().equals(id));
	}

	/** END_SERVER_TICK: the roots hold, the flings land. */
	static void serverTick(MinecraftServer server) {
		if (!ROOTS.isEmpty()) {
			ROOTS.entrySet().removeIf(en -> {
				Root r = en.getValue();
				ServerLevel level = server.getLevel(r.dim());
				Entity e = level == null ? null : level.getEntity(en.getKey());
				if (e == null || !e.isAlive() || level.getGameTime() >= r.until()) {
					return true;
				}
				Vec3 v = e.getDeltaMovement();
				if (e instanceof ServerPlayer sp) {
					if (sp.position().subtract(r.pin()).horizontalDistanceSqr() > 0.09) {
						sp.connection.teleport(r.pin().x, sp.getY(), r.pin().z, sp.getYRot(), sp.getXRot());
					}
				} else {
					e.setDeltaMovement(0.0, Math.min(v.y, 0.0), 0.0);
					e.setPos(r.pin().x, e.getY(), r.pin().z);
					if (e instanceof Mob m) {
						m.getNavigation().stop();
					}
				}
				if (level.getGameTime() % 4 == 0) {
					level.sendParticles(RED, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 1, 0.3, 0.4, 0.3, 0.0);
				}
				return false;
			});
		}
		if (!FLINGS.isEmpty()) {
			FLINGS.entrySet().removeIf(en -> {
				Fling f = en.getValue();
				Entity e = null;
				ServerLevel at = null;
				for (ServerLevel l : server.getAllLevels()) {
					e = l.getEntity(en.getKey());
					if (e != null) {
						at = l;
						break;
					}
				}
				if (!(e instanceof LivingEntity le) || !le.isAlive()) {
					return true;
				}
				long age = at.getGameTime() - f.since();
				if (age > LadybugConfig.FLING_MAX_TICKS) {
					return true;
				}
				if (age > 3 && (le.onGround() || le.horizontalCollision || le.isInWater())) {
					Player owner = at.getPlayerByUUID(f.owner());
					if (owner instanceof ServerPlayer op) {
						AbilityHelpers.hurtBurst(op, le, at.damageSources().source(net.minecraft.world.damagesource.DamageTypes.FLY_INTO_WALL, op),
								LadybugConfig.FLING_IMPACT_DAMAGE);
					} else {
						le.hurt(at.damageSources().flyIntoWall(), LadybugConfig.FLING_IMPACT_DAMAGE);
					}
					at.sendParticles(ParticleTypes.EXPLOSION, le.getX(), le.getY() + 0.3, le.getZ(), 1, 0, 0, 0, 0);
					at.playSound(null, le.getX(), le.getY(), le.getZ(), SoundEvents.GENERIC_BIG_FALL, SoundSource.PLAYERS, 1.0f, 0.9f);
					return true;
				}
				return false;
			});
		}
	}

}
