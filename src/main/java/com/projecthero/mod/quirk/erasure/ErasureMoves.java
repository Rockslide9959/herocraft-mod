package com.projecthero.mod.quirk.erasure;

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
import com.projecthero.mod.hero.PowerSuppression;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.quirk.PlusUltra;
import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.QuirkCombos;
import com.projecthero.mod.quirk.QuirkPerks;
import com.projecthero.mod.quirk.QuirkThreat;
import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.data.QuirkState;
import com.projecthero.mod.quirk.fx.QuirkCamera;
import com.projecthero.mod.quirk.fx.QuirkFx;
import com.projecthero.mod.quirk.fx.QuirkParticles;
import com.projecthero.mod.quirk.meter.QuirkBand;
import com.projecthero.mod.quirk.meter.QuirkMeters;
import com.projecthero.mod.squad.Squads;
import com.projecthero.mod.titanshifter.TitanCombat;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * v0.16.7 revamp (docs/QUIRK_REVAMP_V0167.md): the Erasure Quirk's twelve moves (server side) and the sessions of the
 * ones that run over time: the stare and Dry Eyes, the scarf line (swing / pull), binds (roots), Bind and Spin, Close
 * Combat and the Counter window. Every public move is called by {@link ErasureKit#handle} once its cooldown is ready,
 * and by the gametests directly. Everything the clients draw (the hair, the red eyes, the scarf) comes from the synced
 * kit vals ({@link ErasureConfig}).
 *
 * <p>The swing / pull <b>movement</b> runs on the owning client (the Spider-Man split, like Ladybug's Yo-yo); the server
 * picks the hook and ends the line. Static state holds entity ids, never entities; dropped by {@link #forget} /
 * {@link #clearSessionState} (ServerStateReset through the kit).
 */
public final class ErasureMoves {
	/** The capture weapon's grey and the stare's red. */
	public static final DustParticleOptions SCARF = new DustParticleOptions(new Vector3f(0.62f, 0.63f, 0.66f), 1.0f);
	public static final DustParticleOptions RED = new DustParticleOptions(new Vector3f(0.95f, 0.08f, 0.08f), 0.8f);

	/** A root (bind): where the creature is pinned and till when. */
	private record Root(UUID owner, ResourceKey<Level> dim, Vec3 pin, long until, boolean hadNoGravity) {
	}

	private static final Map<Integer, Root> ROOTS = new ConcurrentHashMap<>();
	/** the block the swing hangs from. */
	private static final Map<UUID, BlockPos> LINE_BLOCK = new ConcurrentHashMap<>();
	/** the creatures the last tick of the stare erased (tests / HUD). */
	private static final Map<UUID, List<Integer>> LAST_ERASED = new ConcurrentHashMap<>();
	/** Bind and Spin: who the swung body already hit. */
	private static final Map<UUID, Set<Integer>> SPIN_HIT = new ConcurrentHashMap<>();

	private ErasureMoves() {
	}

	/** v0.17.1 Reel Lock: who is reeling which creature (id) since when, and the pin it hangs from. */
	private record Reel(int target, long since, Vec3 pin) {
	}

	private static final Map<UUID, Reel> REELS = new ConcurrentHashMap<>();
	/** v0.17.1: rooted creatures strung up on the scarf (Reel Lock, the takedown's finish): entity id -> the height it hangs at. */
	private static final Map<Integer, Double> HOIST = new ConcurrentHashMap<>();

	/** Strings {@code e} up: while rooted it rises to (and hangs at) {@code y}. */
	static void hoist(Entity e, double y) {
		HOIST.put(e.getId(), y);
	}

	static void unhoist(Entity e) {
		if (e != null && HOIST.remove(e.getId()) != null) {
			e.resetFallDistance();
		}
	}

	/** Strung up right now (tests). */
	public static boolean hoisted(Entity e) {
		return HOIST.containsKey(e.getId());
	}
	/** v0.17.1 Multi-Bind: the creatures (ids) each Eraser has tied up. */
	private static final Map<UUID, List<Integer>> MULTI = new ConcurrentHashMap<>();

	static void clearSessionState() {
		ROOTS.clear();
		LINE_BLOCK.clear();
		LAST_ERASED.clear();
		SPIN_HIT.clear();
		SPIN_START.clear();
		REELS.clear();
		MULTI.clear();
		HOIST.clear();
		ErasureUltimate.clearSessionState();
	}

	static void forget(UUID id) {
		LINE_BLOCK.remove(id);
		LAST_ERASED.remove(id);
		SPIN_HIT.remove(id);
		SPIN_START.remove(id);
		REELS.remove(id);
		MULTI.remove(id);
		ErasureUltimate.forget(id);
		ROOTS.values().removeIf(r -> r.owner().equals(id));
	}

	// ---------------------------------------------------------------- helpers

	private static long now(ServerPlayer p) {
		return p.level().getGameTime();
	}

	private static QuirkState st(ServerPlayer p) {
		return Quirks.state(p);
	}

	private static void sound(ServerPlayer p, SoundEvent s, float vol, float pitch) {
		p.level().playSound(null, p.getX(), p.getY(), p.getZ(), s, SoundSource.PLAYERS, vol, pitch);
	}

	private static void sound(ServerPlayer p, net.minecraft.core.Holder<SoundEvent> s, float vol, float pitch) {
		p.level().playSound(null, p.getX(), p.getY(), p.getZ(), s, SoundSource.PLAYERS, vol, pitch);
	}

	static Vec3 mid(Entity e) {
		return e.position().add(0, e.getBbHeight() * 0.5, 0);
	}

	/** Where the scarf leaves him (server estimate: right hand, a little ahead). */
	public static Vec3 hand(Player p) {
		Vec3 right = Vec3.directionFromRotation(0f, p.yBodyRot + 90f);
		return p.position().add(0, p.getBbHeight() * 0.72, 0).add(right.scale(-0.34)).add(p.getLookAngle().scale(0.4));
	}

	public static boolean isBoss(LivingEntity e) {
		return e.getMaxHealth() >= ErasureConfig.BOSS_HEALTH_THRESHOLD || TitanCombat.isBoss(e);
	}

	/** May his attacks touch {@code e}? (the mod-wide rule: never himself, a squadmate, a friendly pet; players with PvP). */
	public static boolean isTarget(ServerPlayer p, LivingEntity e) {
		return HeroTargets.canHarm(p, e);
	}

	/**
	 * May his stare erase {@code e}? Everyone but himself and his squad -- players too, PvP or not (Erasure does no
	 * harm; user decision: it cancels ALL powers, players included). Not his own pets.
	 */
	public static boolean erasable(ServerPlayer p, LivingEntity e) {
		if (e == p || !e.isAlive() || e.isSpectator() || e.isRemoved()) {
			return false;
		}
		if (e instanceof Player other) {
			return !Squads.areAllies(p, other) && !other.getAbilities().invulnerable;
		}
		UUID owner = HeroTargets.ownerOf(e);
		return owner == null || !owner.equals(p.getUUID());
	}

	/**
	 * v0.17.1: a hit with the capture SCARF (the whip, the pull, a swung body, a bind): an erased creature it lands on is
	 * tied up for {@link ErasureConfig#ERASED_TIE_TICKS} as well (docs 3.5, "every scarf hit on an erased target ties it").
	 */
	static boolean scarfStrike(ServerPlayer p, LivingEntity t, Vec3 origin, float damage, double knockback, double lift) {
		boolean hit = strike(p, t, origin, damage, knockback, lift);
		if (hit && t.isAlive() && PowerSuppression.isSuppressed(t) && !isBoss(t) && !rooted(t)) {
			root(p, t, ErasureConfig.ERASED_TIE_TICKS);
			Vec3 c = mid(t);
			QuirkFx.ring(p.serverLevel(), c).colours(0xD8D8DC, 0x5A5A62).size(1.4f).thickness(0.18f).life(8).spawn();
		}
		return hit;
	}

	/** The move's damage in the current Dry Eyes band (the spec's per-band multipliers). */
	static float banded(ServerPlayer p, float damage) {
		return damage * QuirkMeters.damageMult(p);
	}

	/** One hit: damage (boss-capped), knockback away from {@code origin}, lift. True if it landed. */
	static boolean strike(ServerPlayer p, LivingEntity t, Vec3 origin, float damage, double knockback, double lift) {
		if (!isTarget(p, t)) {
			return false;
		}
		boolean boss = isBoss(t);
		float dmg = boss ? Math.min(damage, t.getMaxHealth() * ErasureConfig.BOSS_MAX_FRACTION_PER_HIT) : damage;
		if (!AbilityHelpers.hurtBurst(p, t, p.level().damageSources().playerAttack(p), Math.max(1f, dmg))
				&& !AbilityHelpers.hurtLands(p, t, 0f)) {
			return false;
		}
		if (!boss) {
			if (knockback > 0.0) {
				AbilityHelpers.knockbackFrom(t, origin, knockback);
			}
			if (lift != 0.0) {
				AbilityHelpers.push(t, new Vec3(0.0, lift, 0.0));
			}
		}
		Vec3 c = mid(t);
		p.serverLevel().sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 6, 0.25, 0.3, 0.25, 0.2);
		return true;
	}

	/** The creature under the crosshair, else the valid one nearest it within {@code degrees} and {@code range} he can see. */
	static @Nullable LivingEntity aimed(ServerPlayer p, double range, double degrees) {
		LivingEntity direct = AbilityHelpers.raycastEntity(p, range);
		if (direct != null && isTarget(p, direct)) {
			return direct;
		}
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		double bestDot = Math.cos(Math.toRadians(degrees));
		LivingEntity best = null;
		for (LivingEntity le : p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(range),
				e -> e != p && e.isAlive() && isTarget(p, e))) {
			Vec3 to = mid(le).subtract(eye);
			double d = to.length();
			if (d < 0.5 || d > range) {
				continue;
			}
			double dot = to.scale(1.0 / d).dot(look);
			if (dot > bestDot && p.hasLineOfSight(le)) {
				bestDot = dot;
				best = le;
			}
		}
		return best;
	}

	private static Vec3 lookEnd(ServerPlayer p, double range) {
		BlockHitResult b = AbilityHelpers.raycastBlock(p, range);
		return b.getType() == HitResult.Type.MISS ? p.getEyePosition().add(p.getLookAngle().scale(range)) : b.getLocation();
	}

	private static @Nullable LivingEntity byVal(ServerPlayer p, QuirkState s, String key) {
		long v = s.val(key);
		if (v == 0L) {
			return null;
		}
		Entity e = p.level().getEntity((int) (v - 1L));
		return e instanceof LivingEntity le && le.isAlive() ? le : null;
	}

	/** The scarf whipped out to {@code tip} (drawn out and back by every client). */
	static void whipTo(ServerPlayer p, Vec3 tip) {
		long t = now(p);
		Quirks.edit(p, n -> {
			n.vals.put(ErasureConfig.V_LASH_AT, t);
			n.vecs.put(ErasureConfig.P_LASH, List.of(tip.x, tip.y, tip.z));
		});
	}

	/** The scarf's crack: the cloth whip and snap (v0.17.1: the kit's own sounds, a vanilla cloth layer under them). */
	static void crack(ServerPlayer p, float pitch) {
		sound(p, ErasureSounds.SCARF_WHIP, 0.9f, pitch);
		sound(p, SoundEvents.ARMOR_EQUIP_LEATHER, 0.45f, pitch);
	}

	/** v0.17.1: the scarf snapping taut round {@code at} -- the snap, a grey cloth ring and a puff of fibres. */
	static void snapFx(ServerPlayer p, Vec3 at, float size) {
		ServerLevel level = p.serverLevel();
		ErasureSounds.play(level, at, ErasureSounds.SCARF_SNAP, 0.9f, 0.9f + p.getRandom().nextFloat() * 0.2f);
		QuirkFx.ring(level, at).colours(0xF2F2F4, 0x6A6A72).size(size).thickness(0.22f).life(9).flags(QuirkFx.F_VERTICAL)
				.vec(p.getLookAngle()).spawn();
		level.sendParticles(SCARF, at.x, at.y, at.z, 6, 0.2, 0.2, 0.2, 0.0);
	}

	/** v0.17.1: the cloth capture-scarf ribbon from his hand to {@code to} (the shared toolkit's cloth style). */
	static void scarfRibbon(ServerPlayer p, Vec3 to, int life) {
		QuirkFx.ribbon(p.serverLevel(), hand(p), to).style(QuirkFx.RIBBON_CLOTH).colours(0xF4F4F6, 0xB4B4BC).size(0.085f).life(life).spawn();
	}

	// ---------------------------------------------------------------- roots (binds)

	/** Pin {@code e} for {@code ticks}. Bosses are immune (0). Players get the softened crowd-control rules. Returns the ticks it holds. */
	public static int root(ServerPlayer owner, LivingEntity e, int ticks) {
		if (isBoss(e)) {
			return 0;
		}
		if (e instanceof Player) {
			if (!AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, ticks, 6)) {
				return 0;
			}
			ticks = Math.max(10, ticks / 2);
		} else {
			e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 6, false, false, false));
		}
		Root old = ROOTS.get(e.getId());
		boolean hadNoGravity = old != null ? old.hadNoGravity() : e.isNoGravity();
		ROOTS.put(e.getId(), new Root(owner.getUUID(), e.level().dimension(), e.position(), now(owner) + ticks, hadNoGravity));
		return ticks;
	}

	public static boolean rooted(Entity e) {
		Root r = ROOTS.get(e.getId());
		return r != null && r.until() > e.level().getGameTime();
	}

	private static void unroot(Entity e) {
		Root r = ROOTS.remove(e.getId());
		release(e, r);
	}

	private static void release(Entity e, @Nullable Root r) {
		if (e instanceof LivingEntity le && r != null) {
			le.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
		}
	}

	// ================================================================ R: Capture Bind / Shift+R: Capture Pull

	/** R: the scarf whips out and wraps the target: tied up, rooted {@link ErasureConfig#BIND_TICKS}. Bosses break free. */
	public static @Nullable LivingEntity bind(ServerPlayer p) {
		LivingEntity t = aimed(p, ErasureConfig.BIND_RANGE, 4.0);
		Quirks.setAnim(p, ErasureConfig.ANIM_BIND);
		crack(p, 1.0f);
		if (t == null) {
			whipTo(p, lookEnd(p, ErasureConfig.BIND_RANGE));
			Quirks.say(p, "message.projecthero.quirk.erasure.no_target", ChatFormatting.GRAY);
			Quirks.setCooldown(p, ErasureConfig.BIND, ErasureConfig.MISS_COOLDOWN);
			return null;
		}
		whipTo(p, mid(t));
		if (isBoss(t)) {
			Quirks.say(p, "message.projecthero.quirk.erasure.boss_immune", ChatFormatting.GRAY);
			Quirks.setCooldown(p, ErasureConfig.BIND, ErasureConfig.MISS_COOLDOWN);
			QuirkThreat.add(t, p, 4f); // the boss noticed who tried
			return null;
		}
		if (!bindTarget(p, t, ErasureConfig.BIND_TICKS)) {
			Quirks.setCooldown(p, ErasureConfig.BIND, ErasureConfig.MISS_COOLDOWN);
			return null;
		}
		QuirkCombos.hit(p, t, ErasureConfig.BIND); // opens Strung Up (Bind -> Spin)
		Quirks.setCooldown(p, ErasureConfig.BIND, ErasureConfig.BIND_COOLDOWN);
		return t;
	}

	/** Wraps {@code t} in the scarf for {@code ticks} (it becomes his bound creature). False if it can't be held. */
	static boolean bindTarget(ServerPlayer p, LivingEntity t, int ticks) {
		endBind(p);
		int held = root(p, t, ticks);
		if (held <= 0) {
			return false;
		}
		long until = now(p) + held;
		Quirks.edit(p, n -> {
			n.vals.put(ErasureConfig.V_BIND, t.getId() + 1L);
			n.vals.put(ErasureConfig.V_BIND_UNTIL, until);
		});
		scarfRibbon(p, mid(t), 8);
		if (t instanceof Mob m) {
			m.getNavigation().stop();
		}
		ServerLevel level = p.serverLevel();
		level.sendParticles(SCARF, t.getX(), t.getY() + t.getBbHeight() * 0.5, t.getZ(), 10, t.getBbWidth() * 0.6, t.getBbHeight() * 0.35,
				t.getBbWidth() * 0.6, 0.0);
		ErasureSounds.play(level, mid(t), ErasureSounds.BIND, 1.0f, 1.0f);
		QuirkFx.ring(level, t.position().add(0, t.getBbHeight() * 0.55, 0)).colours(0xF0F0F2, 0x70707A).size(t.getBbWidth() * 2.4f + 0.6f)
				.thickness(0.2f).life(10).spawn();
		level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.LEASH_KNOT_PLACE, SoundSource.PLAYERS, 0.7f, 0.9f);
		return true;
	}

	/**
	 * Shift+R: the scarf yanks the bound creature (or the one he looks at) in to him: {@link ErasureConfig#LASH_DAMAGE}
	 * (v0.17.1: 6, and the yank leaves it in reach of a free follow-up). v0.17.1 <b>Pull to safety</b>: aimed at an ally
	 * (a squadmate, his own pet) it does no harm and drags them to him -- out of lava, water or a fall.
	 */
	public static @Nullable LivingEntity pull(ServerPlayer p) {
		LivingEntity t = boundTarget(p);
		if (t == null) {
			LivingEntity ally = aimedAlly(p, ErasureConfig.LASH_RANGE);
			LivingEntity foe = aimed(p, ErasureConfig.LASH_RANGE, 4.0);
			t = ally != null && (foe == null || ally.distanceToSqr(p) <= foe.distanceToSqr(p)) ? ally : foe;
		}
		Quirks.setAnim(p, ErasureConfig.ANIM_PULL);
		crack(p, 1.3f);
		if (t == null) {
			whipTo(p, lookEnd(p, ErasureConfig.LASH_RANGE));
			Quirks.setCooldown(p, ErasureConfig.LASH, ErasureConfig.MISS_COOLDOWN);
			return null;
		}
		endBind(p);
		whipTo(p, mid(t));
		scarfRibbon(p, mid(t), 7);
		snapFx(p, mid(t), 1.1f);
		if (!isTarget(p, t)) {
			rescue(p, t);
			Quirks.setCooldown(p, ErasureConfig.LASH, ErasureConfig.LASH_COOLDOWN);
			return t;
		}
		QuirkCombos.Route r = QuirkCombos.hit(p, t, ErasureConfig.LASH);
		if (scarfStrike(p, t, p.position(), banded(p, ErasureConfig.LASH_DAMAGE) * QuirkCombos.mult(r), 0.0, 0.0) && !isBoss(t)) {
			Vec3 to = p.position().subtract(t.position());
			double d = to.length();
			if (d > 1.8) {
				Vec3 v = to.normalize().scale(Math.min(ErasureConfig.LASH_PULL, d * 0.18)).add(0, 0.32, 0);
				t.setDeltaMovement(v);
				t.hurtMarked = true;
			}
			QuirkCamera.preset(p, QuirkCamera.Preset.LIGHT_HIT);
		}
		Quirks.setCooldown(p, ErasureConfig.LASH, ErasureConfig.LASH_COOLDOWN);
		return t;
	}

	/** v0.17.1: an ally he is looking at (a squadmate or his own pet) within {@code range}, or null. */
	static @Nullable LivingEntity aimedAlly(ServerPlayer p, double range) {
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		double bestDot = Math.cos(Math.toRadians(6.0));
		LivingEntity best = null;
		for (LivingEntity le : p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(range),
				e -> e != p && e.isAlive() && !e.isSpectator() && isAlly(p, e))) {
			Vec3 to = mid(le).subtract(eye);
			double d = to.length();
			if (d < 0.5 || d > range) {
				continue;
			}
			double dot = to.scale(1.0 / d).dot(look);
			if (dot > bestDot && p.hasLineOfSight(le)) {
				bestDot = dot;
				best = le;
			}
		}
		return best;
	}

	/** A squadmate or one of his own pets. */
	static boolean isAlly(ServerPlayer p, LivingEntity e) {
		if (e instanceof Player other) {
			return Squads.areAllies(p, other);
		}
		UUID owner = HeroTargets.ownerOf(e);
		return owner != null && owner.equals(p.getUUID());
	}

	/** Pull to safety: the ally is dragged to him, out of whatever it was in, and lands softly. */
	static void rescue(ServerPlayer p, LivingEntity ally) {
		boolean danger = ally.isInLava() || ally.isOnFire() || ally.isInWater() || ally.fallDistance > 3f
				|| (!ally.onGround() && ally.getDeltaMovement().y < -0.5);
		Vec3 to = p.position().add(0, 0.4, 0).subtract(ally.position());
		double d = to.length();
		Vec3 v = d < 0.1 ? Vec3.ZERO : to.normalize().scale(Math.min(1.6, 0.35 + d * 0.2)).add(0, 0.42, 0);
		ally.resetFallDistance();
		ally.setDeltaMovement(v);
		ally.hurtMarked = true;
		ally.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, ErasureConfig.ALLY_PULL_SLOW_FALL, 0, false, false, true));
		if (ally.isInLava()) {
			ally.clearFire();
		}
		if (danger) {
			PlusUltra.add(p, PlusUltra.ALLY_SAVE);
		}
	}

	/** The creature his scarf holds right now, or null. */
	public static @Nullable LivingEntity boundTarget(ServerPlayer p) {
		QuirkState s = st(p);
		if (s.val(ErasureConfig.V_BIND) == 0L || s.val(ErasureConfig.V_BIND_UNTIL) < now(p)) {
			return null;
		}
		return byVal(p, s, ErasureConfig.V_BIND);
	}

	static void endBind(ServerPlayer p) {
		QuirkState s = st(p);
		if (s.val(ErasureConfig.V_BIND) == 0L) {
			return;
		}
		Entity e = p.level().getEntity((int) (s.val(ErasureConfig.V_BIND) - 1L));
		if (e != null) {
			Root r = ROOTS.get(e.getId());
			if (r != null && r.owner().equals(p.getUUID())) {
				unroot(e);
			}
		}
		Quirks.edit(p, n -> {
			n.vals.remove(ErasureConfig.V_BIND);
			n.vals.remove(ErasureConfig.V_BIND_UNTIL);
		});
	}

	// ================================================================ G: Close Combat / Shift+G: Counter

	/** G: a fast four-strike martial-arts combo (jab, elbow, knee, spinning kick) on whoever is in front of him. */
	public static boolean combo(ServerPlayer p) {
		Quirks.setVal(p, ErasureConfig.V_COMBO_AT, now(p));
		Quirks.setAnim(p, ErasureConfig.ANIM_COMBO);
		Quirks.setCooldown(p, ErasureConfig.COMBO, ErasureConfig.COMBO_COOLDOWN);
		sound(p, SoundEvents.PLAYER_ATTACK_NODAMAGE, 0.8f, 1.3f);
		return true;
	}

	private static void tickCombo(ServerPlayer p, QuirkState s) {
		long at = s.val(ErasureConfig.V_COMBO_AT);
		if (at == 0L) {
			return;
		}
		long age = now(p) - at;
		if (age > ErasureConfig.COMBO_TICKS || age < 0) {
			Quirks.setVal(p, ErasureConfig.V_COMBO_AT, 0L);
			return;
		}
		for (int i = 0; i < ErasureConfig.COMBO_AT.length; i++) {
			if (age == ErasureConfig.COMBO_AT[i]) {
				comboHit(p, i);
			}
		}
	}

	/** One strike of the combo: re-aimed each time, a short lunge in, the last one a spinning kick that sends them flying. */
	static void comboHit(ServerPlayer p, int i) {
		boolean last = i == ErasureConfig.COMBO_AT.length - 1;
		LivingEntity t = aimed(p, ErasureConfig.COMBO_RANGE + (last ? 0.6 : 0.0), 35.0);
		sound(p, last ? SoundEvents.PLAYER_ATTACK_SWEEP : SoundEvents.PLAYER_ATTACK_WEAK, 0.9f, 0.9f + i * 0.1f);
		if (t == null) {
			return;
		}
		Vec3 to = t.position().subtract(p.position());
		Vec3 flat = new Vec3(to.x, 0, to.z);
		if (flat.length() > 1.6) {
			AbilityHelpers.launchSelf(p, flat.normalize().scale(0.35).add(0, p.getDeltaMovement().y, 0));
		}
		float dmg = banded(p, ErasureConfig.COMBO_DAMAGE[i]);
		if (strike(p, t, p.position(), dmg, last ? 1.1 : 0.15, last ? 0.35 : 0.0)) {
			Vec3 c = mid(t);
			ServerLevel level = p.serverLevel();
			level.sendParticles(last ? ParticleTypes.SWEEP_ATTACK : QuirkParticles.SPARK, c.x, c.y, c.z, last ? 1 : 5, 0.2, 0.2, 0.2, 0.15);
			com.projecthero.mod.quirk.fx.QuirkSounds.play(level, c, last ? com.projecthero.mod.quirk.fx.QuirkSounds.IMPACT_HEAVY
					: com.projecthero.mod.quirk.fx.QuirkSounds.IMPACT, 0.9f, last ? 0.9f : 1.0f + 0.1f * i);
			if (last) {
				QuirkFx.ring(level, c).colours(0xFFFFFF, 0xB8B8C0).size(2.2f).thickness(0.3f).life(8).flags(QuirkFx.F_VERTICAL)
						.vec(c.subtract(p.getEyePosition()).normalize()).spawn();
				QuirkCamera.preset(p, QuirkCamera.Preset.HEAVY_HIT);
			} else {
				QuirkCamera.preset(p, QuirkCamera.Preset.LIGHT_HIT);
			}
		}
	}

	/** Shift+G: a parry stance -- the next hit within {@link ErasureConfig#COUNTER_WINDOW} is caught and answered. */
	public static boolean counter(ServerPlayer p) {
		Quirks.setVal(p, ErasureConfig.V_COUNTER, now(p) + ErasureConfig.COUNTER_WINDOW);
		Quirks.setAnim(p, ErasureConfig.ANIM_COUNTER);
		Quirks.setCooldown(p, ErasureConfig.COUNTER, ErasureConfig.COUNTER_COOLDOWN);
		sound(p, SoundEvents.ARMOR_EQUIP_LEATHER, 0.8f, 0.7f);
		return true;
	}

	/** The parry window is open. */
	public static boolean countering(ServerPlayer p) {
		return st(p).val(ErasureConfig.V_COUNTER) > now(p);
	}

	/**
	 * A hit lands while the parry window is open: it is caught (no damage). A melee attacker within reach is answered:
	 * {@link ErasureConfig#COUNTER_DAMAGE}, wrapped in the scarf for {@link ErasureConfig#COUNTER_BIND_TICKS} and thrown
	 * down. Returns the damage he still takes.
	 */
	static float modifyIncomingDamage(ServerPlayer p, DamageSource source, float amount) {
		if (flipping(p) && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return 0f; // v0.17.1 Evade Flip: dodged
		}
		if (!countering(p) || source.getEntity() == null) {
			return amount;
		}
		Quirks.setVal(p, ErasureConfig.V_COUNTER, 0L);
		Quirks.setAnim(p, ErasureConfig.ANIM_COUNTER_HIT);
		ServerLevel level = p.serverLevel();
		Vec3 c = p.position().add(0, 1.2, 0);
		level.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 14, 0.4, 0.4, 0.4, 0.3);
		sound(p, SoundEvents.SHIELD_BLOCK, 1.0f, 1.2f);
		QuirkFx.ring(level, c).colours(0xFFFFFF, 0xC8C8D0).size(1.8f).thickness(0.25f).life(7).flags(QuirkFx.F_VERTICAL).vec(p.getLookAngle()).spawn();
		if (source.getEntity() instanceof LivingEntity attacker && attacker.distanceTo(p) <= ErasureConfig.COUNTER_REACH && attacker != p) {
			scarfStrike(p, attacker, p.position(), banded(p, ErasureConfig.COUNTER_DAMAGE), 0.4, -0.3);
			if (!isBoss(attacker)) {
				bindTarget(p, attacker, ErasureConfig.COUNTER_BIND_TICKS);
			}
			whipTo(p, mid(attacker));
			snapFx(p, mid(attacker), 1.2f);
			QuirkCamera.preset(p, QuirkCamera.Preset.HEAVY_HIT);
			sound(p, SoundEvents.PLAYER_ATTACK_CRIT, 1.0f, 0.8f);
		}
		return 0f;
	}

	/** v0.17.1: the Evade Flip's i-frames are running (every hit is dodged). */
	public static boolean flipping(ServerPlayer p) {
		return st(p).val(ErasureConfig.V_FLIP_UNTIL) > now(p);
	}

	// ================================================================ V: Erasure Stare (hold) / Shift+V: Eye Drops

	/** V down: the stare starts (refused while his eyes are forced shut). */
	public static boolean eraseDown(ServerPlayer p) {
		if (st(p).val(ErasureConfig.V_ERASE) != 0L) {
			return false;
		}
		if (!Quirks.ready(p, ErasureConfig.ERASE)) {
			return false;
		}
		if (eyesShut(p)) {
			Quirks.say(p, "message.projecthero.quirk.overloaded.erasure", ChatFormatting.RED);
			return false;
		}
		long t = now(p);
		Quirks.edit(p, n -> {
			n.vals.put(ErasureConfig.V_ERASE, 1L);
			n.vals.put(ErasureConfig.V_ERASE_AT, t);
			n.vals.remove(ErasureConfig.V_STARE_TARGET);
		});
		Quirks.setAnim(p, ErasureConfig.ANIM_STARE);
		sound(p, ErasureSounds.GLARE, 0.7f, 1.0f);
		sound(p, SoundEvents.WARDEN_HEARTBEAT, 0.35f, 1.6f);
		tickErase(p, st(p));
		return true;
	}

	/** V up (the blink) / his eyes shut / he was stopped: the stare ends. */
	public static void eraseUp(ServerPlayer p) {
		if (st(p).val(ErasureConfig.V_ERASE) == 0L) {
			return;
		}
		Quirks.edit(p, n -> {
			n.vals.remove(ErasureConfig.V_ERASE);
			n.vals.remove(ErasureConfig.V_ERASE_AT);
			n.vals.remove(ErasureConfig.V_STARE_TARGET);
		});
		LAST_ERASED.remove(p.getUUID());
		Quirks.setCooldown(p, ErasureConfig.ERASE, ErasureConfig.ERASE_COOLDOWN);
		if (st(p).animId == ErasureConfig.ANIM_STARE) {
			Quirks.setAnim(p, QuirkState.ANIM_NONE);
		}
	}

	/** v0.17.1: the stare's reach in the current band (Strained and past: 32 blocks). */
	public static double stareRange(ServerPlayer p) {
		QuirkBand b = QuirkMeters.band(p);
		return b == QuirkBand.LOW ? ErasureConfig.ERASE_RANGE : ErasureConfig.ERASE_RANGE_STRAINED;
	}

	/** v0.17.1: the Goggles perk works -- the goggles are down, or the full Eraser Head suit makes it passive. */
	public static boolean gogglesPerk(ServerPlayer p) {
		return st(p).val(ErasureConfig.V_GOGGLES) != 0L || QuirkPerks.fullSet(p);
	}

	/**
	 * Who the stare holds this tick: the one he looks at (the crosshair, else the nearest inside a narrow cone). Goggles
	 * down: the one already held stays held anywhere inside {@link ErasureConfig#GOGGLES_LOCK_DEGREES} while he can see
	 * it -- it can't dodge out of his eyeline.
	 */
	public static List<LivingEntity> stareTargets(ServerPlayer p) {
		QuirkState s = st(p);
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		double range = stareRange(p);
		int want = QuirkMeters.band(p) == QuirkBand.RED ? ErasureConfig.BURNING_TARGETS : 1;
		LivingEntity locked = null;
		if (gogglesPerk(p)) {
			LivingEntity l = byVal(p, s, ErasureConfig.V_STARE_TARGET);
			if (l != null && erasable(p, l) && p.hasLineOfSight(l)) {
				Vec3 to = mid(l).subtract(eye);
				double d = to.length();
				if (d <= range + l.getBbWidth() && (d < 0.5 || to.scale(1.0 / d).dot(look) >= Math.cos(Math.toRadians(ErasureConfig.GOGGLES_LOCK_DEGREES)))) {
					locked = l;
					if (want == 1) {
						return List.of(l);
					}
				}
			}
		}
		double cos = Math.cos(Math.toRadians(ErasureConfig.ERASE_CONE_DEGREES * (want > 1 ? 2.0 : 1.0)));
		// the creature under the crosshair always counts (big bodies, the dragon's parts)
		LivingEntity direct = AbilityHelpers.raycastEntity(p, range);
		List<LivingEntity> found = new ArrayList<>();
		List<Double> dots = new ArrayList<>();
		for (LivingEntity e : p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(range), e -> erasable(p, e))) {
			Vec3 to = mid(e).subtract(eye);
			double d = to.length();
			if (d > range + e.getBbWidth()) {
				continue;
			}
			double dot = d < 0.5 ? 1.0 : to.scale(1.0 / d).dot(look);
			if ((dot < cos && e != direct) || !p.hasLineOfSight(e)) {
				continue;
			}
			double score = e == locked ? 3.0 : e == direct ? 2.0 : dot;
			int at = 0;
			while (at < dots.size() && dots.get(at) >= score) {
				at++;
			}
			found.add(at, e);
			dots.add(at, score);
		}
		return found.size() <= want ? List.copyOf(found) : List.copyOf(found.subList(0, want));
	}

	private static void tickErase(ServerPlayer p, QuirkState s) {
		if (s.val(ErasureConfig.V_ERASE) == 0L) {
			return;
		}
		List<LivingEntity> targets = stareTargets(p);
		List<Integer> ids = new ArrayList<>(targets.size());
		ServerLevel level = p.serverLevel();
		for (LivingEntity e : targets) {
			PowerSuppression.suppress(e, ErasureConfig.SUPPRESS_TICKS);
			ids.add(e.getId());
			if (p.tickCount % 4 == 0) {
				// above the head, never in front of the eyes (an erased player would see red blobs)
				Vec3 c = e.position().add(0, e.getBbHeight() + 0.45, 0);
				level.sendParticles(RED, c.x, c.y, c.z, 2, 0.2, 0.08, 0.2, 0.0);
			}
		}
		long lock = targets.isEmpty() ? 0L : targets.get(0).getId() + 1L;
		if (lock != s.val(ErasureConfig.V_STARE_TARGET)) {
			if (lock != 0L) {
				// caught: the glare -- a hum and a red ring round the one he holds -- so the stare reads on both ends
				LivingEntity c = targets.get(0);
				ErasureSounds.play(level, mid(c), ErasureSounds.GLARE, 0.8f, 1.15f);
				QuirkFx.ring(level, c.position().add(0, c.getBbHeight() + 0.3, 0)).colours(0xFF3030, 0x800808).size(1.2f).thickness(0.12f)
						.life(10).spawn();
				if (isBoss(c)) {
					QuirkThreat.add(c, p, 6f); // the boss turns on whoever switched its powers off
				}
			}
			Quirks.setVal(p, ErasureConfig.V_STARE_TARGET, lock);
		}
		LAST_ERASED.put(p.getUUID(), List.copyOf(ids));
		float dry = ErasureConfig.STARE_DRY * (gogglesPerk(p) ? ErasureConfig.GOGGLES_DRY_MULT : 1f);
		if (Quirks.addMeterPaced(p, dry) >= 100f) { // v0.17.1: x ErasureMeter.BUILD
			shutEyes(p);
		}
	}

	/** v0.17.1 Burning band (Dry Eyes 90+): staring burns him 0.5 HP a second (never the killing blow). */
	private static void burn(ServerPlayer p) {
		if (p.tickCount % 20 == 0 && QuirkMeters.band(p) == QuirkBand.RED && p.getHealth() > 1.5f) {
			p.hurt(p.damageSources().magic(), ErasureConfig.BURNING_HP_PER_SECOND);
		}
	}

	/** Client-safe: his eyes are forced shut (Dry Eyes hit 100). */
	public static boolean eyesShut(Player p) {
		QuirkState s = Quirks.peek(p);
		return s != null && s.val(ErasureConfig.V_SHUT) != 0L;
	}

	/** Dry Eyes at 100: his eyes are forced shut -- the stare ends and can't start until they recover. */
	public static void shutEyes(ServerPlayer p) {
		eraseUp(p);
		long t = now(p);
		Quirks.edit(p, n -> {
			n.vals.put(ErasureConfig.V_SHUT, 1L);
			n.vals.put(ErasureConfig.V_SHUT_AT, t);
		});
		Quirks.setAnim(p, ErasureConfig.ANIM_BLINK);
		Quirks.say(p, "message.projecthero.quirk.overloaded.erasure", ChatFormatting.RED);
		sound(p, SoundEvents.ARMOR_EQUIP_LEATHER, 0.5f, 0.6f);
	}

	private static void openEyes(ServerPlayer p) {
		Quirks.edit(p, n -> {
			n.vals.remove(ErasureConfig.V_SHUT);
			n.vals.remove(ErasureConfig.V_SHUT_AT);
		});
	}

	public static List<Integer> lastErased(UUID id) {
		return LAST_ERASED.getOrDefault(id, List.of());
	}

	/** An Eye Drops item in his inventory (creative: none needed). */
	private static ItemStack findDrops(ServerPlayer p) {
		for (ItemStack st : p.getInventory().items) {
			if (st.is(ErasureItems.EYE_DROPS)) {
				return st;
			}
		}
		ItemStack off = p.getOffhandItem();
		return off.is(ErasureItems.EYE_DROPS) ? off : ItemStack.EMPTY;
	}

	/**
	 * Eye Drops from the inventory (used up): Dry Eyes back to 0, forced-shut eyes open. False without one. (v0.17.1: no
	 * longer on a key -- the item is right-clicked; kept for anything that wants to spend one from the inventory.)
	 */
	public static boolean eyeDrops(ServerPlayer p) {
		ItemStack drops = findDrops(p);
		if (drops.isEmpty() && !p.getAbilities().instabuild) {
			Quirks.say(p, "message.projecthero.quirk.erasure.no_drops", ChatFormatting.GRAY);
			return false;
		}
		if (!p.getAbilities().instabuild) {
			drops.shrink(1);
		}
		refresh(p);
		Quirks.setCooldown(p, ErasureConfig.DROPS, ErasureConfig.DROPS_COOLDOWN);
		return true;
	}

	/** Eyes refreshed (the Eye Drops move, or the item used by hand). */
	public static void refresh(ServerPlayer p) {
		Quirks.setMeter(p, 0f);
		Quirks.edit(p, n -> n.meter = 0f);
		openEyes(p);
		Quirks.setAnim(p, ErasureConfig.ANIM_DROPS);
		Vec3 eye = p.getEyePosition().add(p.getLookAngle().scale(0.25));
		p.serverLevel().sendParticles(ParticleTypes.FALLING_WATER, eye.x, eye.y + 0.1, eye.z, 8, 0.12, 0.05, 0.12, 0.0);
		sound(p, SoundEvents.BOTTLE_EMPTY, 0.7f, 1.6f);
		sound(p, SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, 0.6f, 1.8f);
		Quirks.say(p, "message.projecthero.quirk.erasure.drops", ChatFormatting.AQUA);
	}

	// ================================================================ X: Scarf Swing / Shift+X: Scarf Pull

	/** Hold X: the scarf hooks a block and he swings (let go / X again: release). */
	public static boolean swingDown(ServerPlayer p) {
		if (st(p).val(ErasureConfig.V_LINE) != 0L) {
			detach(p, true);
			return false;
		}
		BlockHitResult b = AbilityHelpers.raycastBlock(p, ErasureConfig.SWING_RANGE);
		if (b.getType() != HitResult.Type.BLOCK) {
			Quirks.say(p, "message.projecthero.quirk.erasure.no_hook", ChatFormatting.GRAY);
			return false;
		}
		Vec3 hook = b.getLocation();
		double rope = Math.max(3.0, Math.min(ErasureConfig.SWING_RANGE + 2.0, hook.distanceTo(p.getEyePosition())));
		long t = now(p);
		Quirks.edit(p, n -> {
			n.vals.put(ErasureConfig.V_LINE, 1L);
			n.vals.put(ErasureConfig.V_ROPE, Math.round(rope * 1000.0));
			n.vals.put(ErasureConfig.V_LINE_AT, t);
			n.vals.remove(ErasureConfig.V_ZIP_TARGET);
			n.vecs.put(ErasureConfig.P_HOOK, List.of(hook.x, hook.y, hook.z));
		});
		LINE_BLOCK.put(p.getUUID(), b.getBlockPos());
		Quirks.setAnim(p, ErasureConfig.ANIM_SWING);
		floatTolerance(p, true);
		sound(p, SoundEvents.FISHING_BOBBER_THROW, 0.8f, 0.8f);
		p.serverLevel().sendParticles(SCARF, hook.x, hook.y, hook.z, 6, 0.1, 0.1, 0.1, 0.0);
		return true;
	}

	/** X up: a swing lets go (a pull runs to the end). */
	public static void swingUp(ServerPlayer p) {
		if (st(p).val(ErasureConfig.V_LINE) == 1L) {
			detach(p, true);
		}
	}

	/**
	 * X (tap): Scarf Pull -- he yanks himself to the latched point: mid-swing, up the line to its hook; otherwise the
	 * scarf latches the block (or the creature) he looks at and reels him to it. Returns true if a pull started.
	 */
	public static boolean zip(ServerPlayer p) {
		QuirkState s = st(p);
		Vec3 hook;
		LivingEntity t = null;
		if (s.val(ErasureConfig.V_LINE) == 1L && Quirks.vec(s, ErasureConfig.P_HOOK) != null) {
			hook = Quirks.vec(s, ErasureConfig.P_HOOK);
		} else {
			if (s.val(ErasureConfig.V_LINE) != 0L) {
				detach(p, false);
			}
			BlockHitResult b = AbilityHelpers.raycastBlock(p, ErasureConfig.ZIP_RANGE);
			LivingEntity e = AbilityHelpers.raycastEntity(p, ErasureConfig.ZIP_RANGE);
			if (e != null && isTarget(p, e) && (b.getType() == HitResult.Type.MISS || e.distanceTo(p) < b.getLocation().distanceTo(p.getEyePosition()))) {
				t = e;
				hook = mid(e);
			} else if (b.getType() == HitResult.Type.BLOCK) {
				hook = b.getLocation();
				LINE_BLOCK.put(p.getUUID(), b.getBlockPos());
			} else {
				Quirks.say(p, "message.projecthero.quirk.erasure.no_hook", ChatFormatting.GRAY);
				return false;
			}
		}
		final Vec3 h = hook;
		final LivingEntity target = t;
		long at = now(p);
		Quirks.edit(p, n -> {
			n.vals.put(ErasureConfig.V_LINE, target != null ? 2L : 3L);
			n.vals.put(ErasureConfig.V_LINE_AT, at);
			if (target != null) {
				n.vals.put(ErasureConfig.V_ZIP_TARGET, target.getId() + 1L);
			} else {
				n.vals.remove(ErasureConfig.V_ZIP_TARGET);
			}
			n.vals.remove(ErasureConfig.V_ROPE);
			n.vecs.put(ErasureConfig.P_HOOK, List.of(h.x, h.y, h.z));
		});
		Quirks.setAnim(p, ErasureConfig.ANIM_ZIP);
		Quirks.setCooldown(p, ErasureConfig.ZIP, ErasureConfig.ZIP_COOLDOWN);
		floatTolerance(p, true);
		sound(p, SoundEvents.FISHING_BOBBER_RETRIEVE, 0.7f, 0.8f);
		crack(p, 1.2f);
		scarfRibbon(p, h, 6);
		QuirkCamera.preset(p, QuirkCamera.Preset.DASH);
		return true;
	}

	/**
	 * v0.17.1 Shift+X: <b>Evade Flip</b> -- a backflip about five blocks away from where he looks, with
	 * {@link ErasureConfig#FLIP_IFRAMES} ticks of i-frames (every hit in them is dodged). The whole body flips
	 * ({@code ErasureSpinMixin}).
	 */
	public static boolean flip(ServerPlayer p) {
		detach(p, false);
		Vec3 l = p.getLookAngle();
		Vec3 back = new Vec3(-l.x, 0, -l.z);
		back = back.lengthSqr() < 1.0e-4 ? Vec3.directionFromRotation(0f, p.getYRot() + 180f) : back.normalize();
		AbilityHelpers.launchSelf(p, back.scale(ErasureConfig.FLIP_BACK).add(0, ErasureConfig.FLIP_UP, 0));
		long t = now(p);
		Quirks.setVal(p, ErasureConfig.V_FLIP_UNTIL, t + ErasureConfig.FLIP_IFRAMES);
		Quirks.setAnim(p, ErasureConfig.ANIM_FLIP);
		Quirks.setCooldown(p, ErasureConfig.FLIP, ErasureConfig.FLIP_COOLDOWN);
		p.resetFallDistance();
		ServerLevel level = p.serverLevel();
		QuirkFx.trail(level, p).colours(0xE8E8EC, 0x6A6A74).style(QuirkFx.RIBBON_CLOTH).size(0.12f).size2(10f).life(18)
				.vec(new Vec3(0.3, 0.9, -0.1)).spawn();
		QuirkFx.ring(level, p.position().add(0, 0.05, 0)).colours(0xFFFFFF, 0x9A9AA4).size(2.2f).thickness(0.3f).life(10).ground().spawn();
		level.sendParticles(QuirkParticles.DUST_CHUNK, p.getX(), p.getY() + 0.1, p.getZ(), 6, 0.3, 0.05, 0.3, 0.05);
		sound(p, com.projecthero.mod.quirk.fx.QuirkSounds.WHOOSH, 0.9f, 1.1f);
		QuirkCamera.preset(p, QuirkCamera.Preset.DASH);
		return true;
	}

	public static void detach(ServerPlayer p, boolean withSound) {
		QuirkState s = st(p);
		long mode = s.val(ErasureConfig.V_LINE);
		LINE_BLOCK.remove(p.getUUID());
		floatTolerance(p, false);
		if (mode == 0L) {
			return;
		}
		Quirks.edit(p, n -> {
			n.vals.remove(ErasureConfig.V_LINE);
			n.vals.remove(ErasureConfig.V_ROPE);
			n.vals.remove(ErasureConfig.V_LINE_AT);
			n.vals.remove(ErasureConfig.V_ZIP_TARGET);
			n.vecs.remove(ErasureConfig.P_HOOK);
		});
		if (mode == 1L) {
			Quirks.setCooldown(p, ErasureConfig.SWING, ErasureConfig.SWING_COOLDOWN);
			if (s.animId == ErasureConfig.ANIM_SWING) {
				Quirks.setAnim(p, QuirkState.ANIM_NONE); // the held first-person clip ends with the swing
			}
		}
		p.resetFallDistance();
		if (withSound) {
			sound(p, SoundEvents.LEASH_KNOT_BREAK, 0.4f, 1.3f);
		}
	}

	private static void tickLine(ServerPlayer p, QuirkState s) {
		long mode = s.val(ErasureConfig.V_LINE);
		if (mode == 0L) {
			return;
		}
		long age = now(p) - s.val(ErasureConfig.V_LINE_AT);
		if (mode == 2L) {
			LivingEntity t = byVal(p, s, ErasureConfig.V_ZIP_TARGET);
			if (t == null || age > ErasureConfig.ZIP_MAX_TICKS) {
				detach(p, false);
				return;
			}
			double dist = Math.sqrt(AbilityHelpers.distanceSqToBox(t, p.position().add(0, p.getBbHeight() * 0.5, 0)));
			if (dist < ErasureConfig.ZIP_ARRIVE - 1.0 || mid(t).distanceTo(p.getEyePosition()) < ErasureConfig.ZIP_ARRIVE) {
				if (strike(p, t, p.position(), banded(p, ErasureConfig.ZIP_KICK_DAMAGE), 0.6, 0.2)) {
					QuirkFx.ring(p.serverLevel(), mid(t)).colours(0xFFFFFF, 0xB8B8C0).size(1.8f).thickness(0.25f).life(8).flags(QuirkFx.F_VERTICAL)
							.vec(p.getLookAngle()).spawn();
					QuirkCamera.preset(p, QuirkCamera.Preset.HEAVY_HIT);
				}
				com.projecthero.mod.quirk.fx.QuirkSounds.play(p.serverLevel(), mid(t), com.projecthero.mod.quirk.fx.QuirkSounds.IMPACT, 1.0f, 0.9f);
				detach(p, false);
				return;
			}
			p.resetFallDistance();
			floatTolerance(p, true);
			return;
		}
		Vec3 hook = Quirks.vec(s, ErasureConfig.P_HOOK);
		if (hook == null) {
			detach(p, false);
			return;
		}
		double dist = hook.distanceTo(p.getEyePosition());
		if (mode == 3L) {
			if (dist < ErasureConfig.ZIP_ARRIVE || age > ErasureConfig.ZIP_MAX_TICKS) {
				detach(p, false);
				return;
			}
			p.resetFallDistance();
			floatTolerance(p, true);
			return;
		}
		BlockPos bp = LINE_BLOCK.get(p.getUUID());
		if (bp != null && p.tickCount % 5 == 0 && p.level().getBlockState(bp).getCollisionShape(p.level(), bp).isEmpty()) {
			detach(p, true);
			return;
		}
		if (dist > s.val(ErasureConfig.V_ROPE) / 1000.0 + ErasureConfig.SWING_SLACK_BREAK) {
			detach(p, true);
			return;
		}
		p.resetFallDistance();
		floatTolerance(p, true);
	}

	/** Vanilla kicks a player airborne 4 s with no downward motion -- a swing looks just like that (Spider-Man fix). */
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

	// ================================================================ Z: Bind and Spin / Shift+Z: Disarm Throw

	/**
	 * Z: the bound creature (or the one he looks at, bound on the spot) is swung round him on the scarf -- two full turns
	 * at {@link ErasureConfig#SPIN_RADIUS}, smashing into everything it passes -- then flung the way he looks.
	 */
	public static @Nullable LivingEntity spin(ServerPlayer p) {
		LivingEntity t = boundTarget(p);
		if (t == null) {
			t = aimed(p, ErasureConfig.SPIN_RANGE, 4.0);
		}
		Quirks.setAnim(p, ErasureConfig.ANIM_BIND);
		crack(p, 0.9f);
		if (t == null || isBoss(t)) {
			whipTo(p, t == null ? lookEnd(p, ErasureConfig.SPIN_RANGE) : mid(t));
			Quirks.say(p, t == null ? "message.projecthero.quirk.erasure.no_target" : "message.projecthero.quirk.erasure.boss_immune", ChatFormatting.GRAY);
			Quirks.setCooldown(p, ErasureConfig.SPIN, ErasureConfig.MISS_COOLDOWN);
			return null;
		}
		endBind(p);
		LivingEntity target = t;
		long at = now(p);
		Quirks.edit(p, n -> {
			n.vals.put(ErasureConfig.V_SPIN, target.getId() + 1L);
			n.vals.put(ErasureConfig.V_SPIN_AT, at);
		});
		SPIN_HIT.put(p.getUUID(), new HashSet<>(Set.of(target.getId())));
		SPIN_ROUTE.put(p.getUUID(), QuirkCombos.mult(QuirkCombos.hit(p, target, ErasureConfig.SPIN))); // Strung Up
		Quirks.setAnim(p, ErasureConfig.ANIM_SPIN);
		Quirks.setCooldown(p, ErasureConfig.SPIN, ErasureConfig.SPIN_COOLDOWN);
		scarfRibbon(p, mid(target), 8);
		ErasureSounds.play(p.serverLevel(), mid(target), ErasureSounds.BIND, 0.9f, 0.8f);
		return target;
	}

	/** Bind and Spin: the fling's combo multiplier (Strung Up), per Eraser. */
	private static final Map<UUID, Float> SPIN_ROUTE = new ConcurrentHashMap<>();

	/** Client-safe: the spin's angle (radians) at {@code age} ticks -- two eased turns. Starts toward the target's side. */
	public static double spinAngle(float age) {
		float t = Math.max(0f, Math.min(1f, age / ErasureConfig.SPIN_TICKS));
		float e = t * t * (3f - 2f * t);
		return e * Math.PI * 4.0;
	}

	private static void tickSpin(ServerPlayer p, QuirkState s) {
		if (s.val(ErasureConfig.V_SPIN) == 0L) {
			return;
		}
		LivingEntity t = byVal(p, s, ErasureConfig.V_SPIN);
		long age = now(p) - s.val(ErasureConfig.V_SPIN_AT);
		if (t == null || age < 0) {
			endSpin(p);
			return;
		}
		ServerLevel level = p.serverLevel();
		if (age >= ErasureConfig.SPIN_TICKS) {
			// let go: flung the way he looks
			Vec3 look = p.getLookAngle();
			float bonus = SPIN_ROUTE.getOrDefault(p.getUUID(), 1f);
			Vec3 v = new Vec3(look.x, Math.max(0.25, look.y + 0.3), look.z).normalize().scale(ErasureConfig.SPIN_THROW_SPEED * (bonus > 1f ? 1.35 : 1.0));
			scarfStrike(p, t, p.position(), banded(p, ErasureConfig.SPIN_THROW_DAMAGE) * bonus, 0.0, 0.0);
			t.setDeltaMovement(v);
			t.hurtMarked = true;
			snapFx(p, mid(t), 1.6f);
			QuirkFx.trail(level, t).colours(0xE8E8EC, 0x7A7A84).style(QuirkFx.RIBBON_CLOTH).size(0.2f).size2(8f).life(16).vec(new Vec3(0, 0.6, 0))
					.spawn();
			QuirkCamera.preset(p, bonus > 1f ? QuirkCamera.Preset.FINISHER : QuirkCamera.Preset.HEAVY_HIT);
			sound(p, com.projecthero.mod.quirk.fx.QuirkSounds.WHOOSH, 1.0f, 0.7f);
			sound(p, SoundEvents.LEASH_KNOT_BREAK, 0.6f, 0.9f);
			endSpin(p);
			return;
		}
		// the orbit starts where the target was when the spin began
		double a = spinStart(p, t, age) + spinAngle(age);
		Vec3 orbit = p.position().add(Math.cos(a) * ErasureConfig.SPIN_RADIUS, 0.6 + 0.3 * Math.sin(age * 0.4), Math.sin(a) * ErasureConfig.SPIN_RADIUS);
		Vec3 delta = orbit.subtract(t.position());
		if (t instanceof Player) {
			t.setDeltaMovement(delta.scale(0.6));
		} else {
			t.setDeltaMovement(delta);
			t.setPos(t.getX() + delta.x * 0.6, t.getY() + delta.y * 0.6, t.getZ() + delta.z * 0.6);
		}
		t.hurtMarked = true;
		t.resetFallDistance();
		Set<Integer> hit = SPIN_HIT.computeIfAbsent(p.getUUID(), k -> new HashSet<>());
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, t.getBoundingBox().inflate(0.6),
				e -> e != p && e != t && isTarget(p, e) && !hit.contains(e.getId()))) {
			hit.add(e.getId());
			scarfStrike(p, e, t.position(), banded(p, ErasureConfig.SPIN_HIT_DAMAGE), 1.0, 0.35);
			strike(p, t, e.position(), 2f, 0.0, 0.0);
			com.projecthero.mod.quirk.fx.QuirkSounds.play(level, mid(e), com.projecthero.mod.quirk.fx.QuirkSounds.IMPACT, 1.0f, 0.85f);
			level.sendParticles(QuirkParticles.DUST_CHUNK, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 6, 0.2, 0.2, 0.2, 0.08);
			QuirkCamera.preset(p, QuirkCamera.Preset.LIGHT_HIT);
		}
		if (age % 6 == 0) {
			sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.6f, 0.7f + age * 0.02f);
		}
		level.sendParticles(SCARF, t.getX(), t.getY() + t.getBbHeight() * 0.5, t.getZ(), 1, 0.2, 0.2, 0.2, 0.0);
	}

	/** The orbit's starting angle (fixed at the first tick of the spin, kept per player). */
	private static final Map<UUID, Double> SPIN_START = new ConcurrentHashMap<>();

	private static double spinStart(ServerPlayer p, LivingEntity t, long age) {
		if (age == 0 || !SPIN_START.containsKey(p.getUUID())) {
			SPIN_START.put(p.getUUID(), Math.atan2(t.getZ() - p.getZ(), t.getX() - p.getX()));
		}
		return SPIN_START.get(p.getUUID());
	}

	/** The spin in progress (gametests). */
	public static boolean spinning(ServerPlayer p) {
		return st(p).val(ErasureConfig.V_SPIN) != 0L;
	}

	private static void endSpin(ServerPlayer p) {
		SPIN_HIT.remove(p.getUUID());
		SPIN_START.remove(p.getUUID());
		SPIN_ROUTE.remove(p.getUUID());
		if (st(p).val(ErasureConfig.V_SPIN) == 0L) {
			return;
		}
		Quirks.edit(p, n -> {
			n.vals.remove(ErasureConfig.V_SPIN);
			n.vals.remove(ErasureConfig.V_SPIN_AT);
		});
	}

	/**
	 * Shift+Z: Disarm Throw -- the scarf snaps round a hand and rips the held item away; it flies back to land at his feet.
	 * Mobs, and players he may fight (PvP); not bosses.
	 */
	public static ItemStack disarm(ServerPlayer p) {
		LivingEntity t = aimed(p, ErasureConfig.DISARM_RANGE, 4.0);
		Quirks.setAnim(p, ErasureConfig.ANIM_DISARM);
		crack(p, 1.3f);
		if (t == null || isBoss(t) || !(t instanceof Mob || t instanceof Player)) {
			whipTo(p, t != null ? mid(t) : lookEnd(p, ErasureConfig.DISARM_RANGE));
			Quirks.say(p, t == null ? "message.projecthero.quirk.erasure.no_target" : "message.projecthero.quirk.erasure.no_disarm", ChatFormatting.GRAY);
			Quirks.setCooldown(p, ErasureConfig.DISARM, ErasureConfig.MISS_COOLDOWN);
			return ItemStack.EMPTY;
		}
		EquipmentSlot slot = !t.getMainHandItem().isEmpty() ? EquipmentSlot.MAINHAND : !t.getOffhandItem().isEmpty() ? EquipmentSlot.OFFHAND : null;
		whipTo(p, mid(t));
		if (slot == null) {
			Quirks.say(p, "message.projecthero.quirk.erasure.no_disarm", ChatFormatting.GRAY);
			Quirks.setCooldown(p, ErasureConfig.DISARM, ErasureConfig.MISS_COOLDOWN);
			return ItemStack.EMPTY;
		}
		ItemStack taken = t.getItemBySlot(slot).copy();
		t.setItemSlot(slot, ItemStack.EMPTY);
		if (t.isUsingItem()) {
			t.stopUsingItem();
		}
		// thrown back to him on the scarf
		Vec3 from = mid(t);
		ItemEntity drop = new ItemEntity(p.level(), from.x, from.y, from.z, taken);
		Vec3 to = p.position().add(0, 0.5, 0).subtract(from);
		double d = Math.max(0.1, to.length());
		drop.setDeltaMovement(to.x * 0.1, Math.min(0.5, 0.15 + d * 0.03), to.z * 0.1);
		drop.setPickUpDelay(10);
		p.level().addFreshEntity(drop);
		scarfRibbon(p, from, 8);
		snapFx(p, from, 0.9f);
		sound(p, SoundEvents.ITEM_PICKUP, 0.8f, 0.8f);
		Quirks.setCooldown(p, ErasureConfig.DISARM, ErasureConfig.DISARM_COOLDOWN);
		return taken;
	}

	// ================================================================ C: Scarf Whip / Shift+C: Goggles

	/**
	 * C: a cracking lash of the scarf at whoever he looks at, 10 blocks: {@link ErasureConfig#WHIP_DAMAGE} and a stagger. The
	 * cloth snaps out as a ribbon and the crack rings round the tip.
	 */
	public static @Nullable LivingEntity whip(ServerPlayer p) {
		LivingEntity t = aimed(p, ErasureConfig.WHIP_RANGE, 6.0);
		Quirks.setAnim(p, ErasureConfig.ANIM_WHIP);
		crack(p, 1.6f);
		Quirks.setCooldown(p, ErasureConfig.WHIP, ErasureConfig.WHIP_COOLDOWN);
		Vec3 tip = t == null ? lookEnd(p, ErasureConfig.WHIP_RANGE) : mid(t);
		whipTo(p, tip);
		scarfRibbon(p, tip, 7);
		if (t == null) {
			QuirkFx.ring(p.serverLevel(), tip).colours(0xFFFFFF, 0x8A8A92).size(0.9f).thickness(0.15f).life(6).flags(QuirkFx.F_VERTICAL)
					.vec(p.getLookAngle()).spawn();
			return null;
		}
		if (scarfStrike(p, t, p.position(), banded(p, ErasureConfig.WHIP_DAMAGE), 0.35, 0.1)) {
			AbilityHelpers.applyControl(t, MobEffects.MOVEMENT_SLOWDOWN, ErasureConfig.WHIP_STAGGER_TICKS, 2);
			if (t instanceof Mob m && !isBoss(t)) {
				m.getNavigation().stop(); // the stagger
			}
			snapFx(p, tip, 1.5f);
			QuirkCombos.hit(p, t, ErasureConfig.WHIP); // opens Crack and Yank (Whip -> Capture Pull)
			QuirkCamera.preset(p, QuirkCamera.Preset.LIGHT_HIT);
		}
		return t;
	}

	/**
	 * v0.17.1 Shift+C: <b>Multi-Bind</b> -- the scarf fans out across a cone and ties up to three creatures for 2.5 s (bosses
	 * break free). Returns who it caught.
	 */
	public static List<LivingEntity> multiBind(ServerPlayer p) {
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		double cos = Math.cos(Math.toRadians(ErasureConfig.MULTI_CONE_DEGREES));
		List<LivingEntity> all = new ArrayList<>();
		for (LivingEntity e : p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(ErasureConfig.MULTI_RANGE),
				e -> e != p && e.isAlive() && isTarget(p, e))) {
			Vec3 to = mid(e).subtract(eye);
			double d = to.length();
			if (d > ErasureConfig.MULTI_RANGE || d < 0.3 || to.scale(1.0 / d).dot(look) < cos || !p.hasLineOfSight(e)) {
				continue;
			}
			all.add(e);
		}
		all.sort((a, b) -> Double.compare(a.distanceToSqr(p), b.distanceToSqr(p)));
		Quirks.setAnim(p, ErasureConfig.ANIM_MULTI);
		crack(p, 1.1f);
		endMulti(p);
		List<LivingEntity> caught = new ArrayList<>();
		ServerLevel level = p.serverLevel();
		for (LivingEntity e : all) {
			if (caught.size() >= ErasureConfig.MULTI_TARGETS) {
				break;
			}
			scarfRibbon(p, mid(e), 9);
			if (isBoss(e)) {
				QuirkThreat.add(e, p, 4f);
				continue;
			}
			if (root(p, e, ErasureConfig.MULTI_TICKS) > 0) {
				caught.add(e);
				if (e instanceof Mob m) {
					m.getNavigation().stop();
				}
				ErasureSounds.play(level, mid(e), ErasureSounds.BIND, 0.8f, 1.1f);
				QuirkFx.ring(level, mid(e)).colours(0xF0F0F2, 0x70707A).size(e.getBbWidth() * 2.4f + 0.6f).thickness(0.2f).life(10).spawn();
			}
		}
		if (all.isEmpty()) {
			for (int i = -1; i <= 1; i++) {
				Vec3 dir = look.yRot((float) Math.toRadians(i * 22.0));
				scarfRibbon(p, eye.add(dir.scale(ErasureConfig.MULTI_RANGE * 0.8)), 8);
			}
		}
		long until = now(p) + ErasureConfig.MULTI_TICKS;
		List<Integer> ids = new ArrayList<>();
		for (LivingEntity e : caught) {
			ids.add(e.getId());
		}
		if (!ids.isEmpty()) {
			MULTI.put(p.getUUID(), ids);
		}
		Quirks.edit(p, n -> {
			for (int i = 0; i < ErasureConfig.MULTI_TARGETS; i++) {
				if (i < ids.size()) {
					n.vals.put(ErasureConfig.V_MULTI_PREFIX + i, ids.get(i) + 1L);
				} else {
					n.vals.remove(ErasureConfig.V_MULTI_PREFIX + i);
				}
			}
			if (ids.isEmpty()) {
				n.vals.remove(ErasureConfig.V_MULTI_UNTIL);
			} else {
				n.vals.put(ErasureConfig.V_MULTI_UNTIL, until);
			}
		});
		Quirks.setCooldown(p, ErasureConfig.MULTI, caught.isEmpty() ? ErasureConfig.MISS_COOLDOWN * 2 : ErasureConfig.MULTI_COOLDOWN);
		if (!caught.isEmpty()) {
			QuirkCamera.preset(p, QuirkCamera.Preset.LIGHT_HIT);
		}
		return caught;
	}

	/** Multi-Bind's ties come off (time's up, or he was stopped). */
	static void endMulti(ServerPlayer p) {
		List<Integer> ids = MULTI.remove(p.getUUID());
		if (ids != null) {
			for (int id : ids) {
				Entity e = p.level().getEntity(id);
				Root r = ROOTS.get(id);
				if (e != null && r != null && r.owner().equals(p.getUUID())) {
					unroot(e);
				}
			}
		}
		QuirkState s = st(p);
		if (s.val(ErasureConfig.V_MULTI_UNTIL) != 0L) {
			Quirks.edit(p, n -> {
				for (int i = 0; i < ErasureConfig.MULTI_TARGETS; i++) {
					n.vals.remove(ErasureConfig.V_MULTI_PREFIX + i);
				}
				n.vals.remove(ErasureConfig.V_MULTI_UNTIL);
			});
		}
	}

	/** Tests / HUD: Multi-Bind's creatures right now. */
	public static List<Integer> multiBound(ServerPlayer p) {
		return MULTI.getOrDefault(p.getUUID(), List.of());
	}

	// ================================================================ hold R: Reel Lock (v0.17.1)

	/**
	 * Hold R on a bound creature: it is strung up on the scarf -- hoisted {@link ErasureConfig#REEL_LIFT} and held there as long
	 * as he holds, 2 damage a second, Dry Eyes +5 a second (paced). False without a bound creature.
	 */
	public static boolean reelStart(ServerPlayer p) {
		if (REELS.containsKey(p.getUUID())) {
			return true;
		}
		LivingEntity t = boundTarget(p);
		if (t == null || isBoss(t) || !Quirks.ready(p, ErasureConfig.REEL)) {
			return false;
		}
		Vec3 pin = new Vec3(t.getX(), t.getY() + ErasureConfig.REEL_LIFT, t.getZ());
		long at = now(p);
		REELS.put(p.getUUID(), new Reel(t.getId(), at, pin));
		hoist(t, pin.y); // strung up from the moment the reel starts (tickReel keeps it there), not a tick later
		Quirks.edit(p, n -> {
			n.vals.put(ErasureConfig.V_REEL, 1L);
			n.vals.put(ErasureConfig.V_REEL_AT, at);
		});
		Quirks.setAnim(p, ErasureConfig.ANIM_REEL);
		snapFx(p, mid(t), 1.2f);
		ErasureSounds.play(p.serverLevel(), mid(t), ErasureSounds.BIND, 1.0f, 0.75f);
		return true;
	}

	/** R let go: the reel ends (the creature drops back; the bind runs out on its own clock). */
	public static void reelStop(ServerPlayer p) {
		Reel r = REELS.remove(p.getUUID());
		if (st(p).val(ErasureConfig.V_REEL) != 0L) {
			Quirks.edit(p, n -> {
				n.vals.remove(ErasureConfig.V_REEL);
				n.vals.remove(ErasureConfig.V_REEL_AT);
			});
		}
		if (r != null) {
			Quirks.setCooldown(p, ErasureConfig.REEL, ErasureConfig.REEL_COOLDOWN);
			if (st(p).animId == ErasureConfig.ANIM_REEL) {
				Quirks.setAnim(p, QuirkState.ANIM_NONE);
			}
			unhoist(p.level().getEntity(r.target())); // let down, no fall damage
		}
	}

	/** Reeling right now (tests / HUD). */
	public static boolean reeling(ServerPlayer p) {
		return REELS.containsKey(p.getUUID());
	}

	private static void tickReel(ServerPlayer p) {
		Reel r = REELS.get(p.getUUID());
		if (r == null) {
			return;
		}
		long age = now(p) - r.since();
		Entity e = p.level().getEntity(r.target());
		if (!(e instanceof LivingEntity t) || !t.isAlive() || age > ErasureConfig.REEL_MAX_TICKS || t.distanceToSqr(p) > 24 * 24) {
			reelStop(p);
			return;
		}
		// the bind is kept alive while he reels
		Root root = ROOTS.get(t.getId());
		if (root == null || root.until() < now(p) + 4) {
			root(p, t, 10);
			Quirks.setVal(p, ErasureConfig.V_BIND_UNTIL, now(p) + 10);
		}
		// hoisted: rising into the pin, held there (the root's server tick moves it)
		hoist(t, r.pin().y);
		t.resetFallDistance();
		if (age > 0 && age % ErasureConfig.REEL_DAMAGE_EVERY == 0) {
			t.invulnerableTime = 0;
			if (scarfStrike(p, t, t.position(), banded(p, ErasureConfig.REEL_DAMAGE), 0.0, 0.0)) {
				ErasureSounds.play(p.serverLevel(), mid(t), ErasureSounds.SCARF_SNAP, 0.6f, 1.3f);
			}
		}
		if (Quirks.addMeterPaced(p, ErasureConfig.REEL_DRY) >= 100f) { // v0.17.1: the eyes stay open on him
			shutEyes(p);
		}
	}

	/**
	 * Shift+V (v0.17.1, from Shift+C): goggles down over the eyes (night vision; the client outlines powered players / special
	 * mobs; the stare locks on and dries the eyes slower) or back up.
	 */
	public static boolean goggles(ServerPlayer p) {
		boolean on = st(p).val(ErasureConfig.V_GOGGLES) == 0L;
		setGoggles(p, on);
		Quirks.setAnim(p, ErasureConfig.ANIM_GOGGLES);
		Quirks.setCooldown(p, ErasureConfig.GOGGLES, ErasureConfig.GOGGLES_COOLDOWN);
		return on;
	}

	static void setGoggles(ServerPlayer p, boolean on) {
		Quirks.setVal(p, ErasureConfig.V_GOGGLES, on ? 1L : 0L);
		if (on) {
			nightVision(p);
		} else {
			clearNightVision(p);
		}
		sound(p, ErasureSounds.GOGGLES, 0.8f, on ? 1.1f : 0.9f);
	}

	private static void nightVision(ServerPlayer p) {
		p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, ErasureConfig.GOGGLES_NIGHT_VISION, 0, true, false, true));
	}

	private static void clearNightVision(ServerPlayer p) {
		MobEffectInstance nv = p.getEffect(MobEffects.NIGHT_VISION);
		if (nv != null && nv.getDuration() <= ErasureConfig.GOGGLES_NIGHT_VISION && nv.isAmbient()) {
			p.removeEffect(MobEffects.NIGHT_VISION);
		}
	}

	// ================================================================ per player / per server tick

	static void tick(ServerPlayer p) {
		QuirkState s = st(p);
		if (s.val(ErasureConfig.V_INIT) == 0L) {
			// a new Quirk -- or a save from before the revamp, when the meter counted down from 100 -- starts fresh
			Quirks.setMeter(p, 0f);
			Quirks.setVal(p, ErasureConfig.V_INIT, 1L);
			s = st(p);
		}
		boolean erasing = s.val(ErasureConfig.V_ERASE) != 0L;
		if (erasing) {
			tickErase(p, s);
			burn(p);
		} else if (REELS.containsKey(p.getUUID())) {
			// reeling keeps the eyes open on him: no recovery (tickReel dries them)
		} else if (s.val(ErasureConfig.V_SHUT) != 0L) {
			float m = Quirks.addMeterPaced(p, -ErasureConfig.SHUT_RECOVER);
			if (m <= ErasureConfig.SHUT_OPEN_AT && now(p) - s.val(ErasureConfig.V_SHUT_AT) >= ErasureConfig.SHUT_MIN_TICKS) {
				openEyes(p);
			}
		} else {
			Quirks.addMeterPaced(p, -ErasureConfig.RECOVER);
		}
		s = st(p);
		tickLine(p, s);
		tickSpin(p, st(p));
		tickCombo(p, st(p));
		tickReel(p);
		ErasureUltimate.tick(p);
		s = st(p);
		if (s.val(ErasureConfig.V_MULTI_UNTIL) != 0L && s.val(ErasureConfig.V_MULTI_UNTIL) <= now(p)) {
			endMulti(p);
		}
		if (s.val(ErasureConfig.V_FLIP_UNTIL) != 0L && s.val(ErasureConfig.V_FLIP_UNTIL) <= now(p) - ErasureConfig.FLIP_TICKS) {
			Quirks.setVal(p, ErasureConfig.V_FLIP_UNTIL, 0L);
		}
		s = st(p);
		if (s.val(ErasureConfig.V_COUNTER) != 0L && s.val(ErasureConfig.V_COUNTER) <= now(p)) {
			Quirks.setVal(p, ErasureConfig.V_COUNTER, 0L);
		}
		if (s.val(ErasureConfig.V_BIND) != 0L && (s.val(ErasureConfig.V_BIND_UNTIL) < now(p) || boundTarget(p) == null) && !REELS.containsKey(p.getUUID())) {
			endBind(p);
		}
		if (s.val(ErasureConfig.V_GOGGLES) != 0L) {
			if (p.tickCount % 40 == 0) {
				nightVision(p);
			}
			if (p.tickCount % 10 == 0) {
				sendGoggles(p);
			}
		}
	}

	/** The powered players and special mobs the goggles pick out right now. */
	public static List<Integer> gogglesMarks(ServerPlayer p) {
		List<Integer> ids = new ArrayList<>();
		for (LivingEntity e : p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(ErasureConfig.GOGGLES_RANGE),
				e -> e != p && e.isAlive() && !e.isSpectator())) {
			boolean mark;
			if (e instanceof ServerPlayer sp) {
				mark = com.projecthero.mod.hero.HeroTiers.hasHeroTier(sp) || com.projecthero.mod.hero.HeroTiers.hasExperimental(sp)
						|| Quirks.hasPower(sp);
			} else {
				mark = ErasureMobs.isSpecial(e);
			}
			if (mark && ids.size() < 256) {
				ids.add(e.getId());
			}
		}
		return ids;
	}

	private static void sendGoggles(ServerPlayer p) {
		if (net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(p, ErasureGogglesPayload.TYPE)) {
			net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p, new ErasureGogglesPayload(gogglesMarks(p)));
		}
	}

	/** Everything running ends (revoke / death / Erased). The goggles stay where they are. */
	static void stopAll(ServerPlayer p) {
		if (!Quirks.holds(p, Quirk.ERASURE)) {
			return;
		}
		eraseUp(p);
		detach(p, false);
		reelStop(p);
		endBind(p);
		endSpin(p);
		endMulti(p);
		ErasureUltimate.stop(p);
		if (st(p).val(ErasureConfig.V_COMBO_AT) != 0L || st(p).val(ErasureConfig.V_COUNTER) != 0L) {
			Quirks.edit(p, n -> {
				n.vals.remove(ErasureConfig.V_COMBO_AT);
				n.vals.remove(ErasureConfig.V_COUNTER);
			});
		}
		UUID id = p.getUUID();
		for (Map.Entry<Integer, Root> e : ROOTS.entrySet()) {
			if (e.getValue().owner().equals(id)) {
				Entity en = p.level().getEntity(e.getKey());
				if (en != null) {
					release(en, e.getValue());
				}
			}
		}
		ROOTS.values().removeIf(r -> r.owner().equals(id));
	}

	/** END_SERVER_TICK: the roots hold. */
	static void serverTick(MinecraftServer server) {
		if (ROOTS.isEmpty()) {
			return;
		}
		ROOTS.entrySet().removeIf(en -> {
			Root r = en.getValue();
			ServerLevel level = server.getLevel(r.dim());
			Entity e = level == null ? null : level.getEntity(en.getKey());
			if (e == null || !e.isAlive() || level.getGameTime() >= r.until()) {
				if (e != null) {
					release(e, r);
				}
				HOIST.remove(en.getKey());
				return true;
			}
			Double hoistY = HOIST.get(en.getKey());
			if (e instanceof ServerPlayer sp) {
				if (sp.position().subtract(r.pin()).horizontalDistanceSqr() > 0.09) {
					sp.connection.teleport(r.pin().x, sp.getY(), r.pin().z, sp.getYRot(), sp.getXRot());
				}
				if (hoistY != null) {
					sp.resetFallDistance();
				}
			} else {
				Vec3 v = e.getDeltaMovement();
				double y = e.getY();
				if (hoistY != null) {
					// strung up: raised by position (it works on any mob, AI or not), hanging still on the scarf
					y += Mth.clamp((hoistY - y) * 0.35, -0.15, 0.3);
					e.setDeltaMovement(0.0, 0.0, 0.0);
					e.resetFallDistance();
				} else {
					e.setDeltaMovement(0.0, Math.min(v.y, 0.0), 0.0);
				}
				e.setPos(r.pin().x, y, r.pin().z);
				if (e instanceof Mob m) {
					m.getNavigation().stop();
				}
			}
			if (level.getGameTime() % 5 == 0) {
				level.sendParticles(SCARF, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 1, 0.3, 0.4, 0.3, 0.0);
			}
			return false;
		});
	}
}
