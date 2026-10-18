package com.projecthero.mod.quirk.erasure;

import java.util.ArrayList;
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
import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.data.QuirkState;
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
 * v0.16.7: the Erasure Quirk's twelve moves (server side) and the sessions of the ones that run over time: the stare,
 * the scarf line, binds / hoists (roots), Bind Slam and Double Bind. Every public move is called by
 * {@link ErasureKit#handle} once its cooldown is ready, and by the gametests directly. Everything the clients draw (the
 * hair, the red eyes, the scarf) comes from the synced kit vals ({@link ErasureConfig}).
 *
 * <p>The swing / zip <b>movement</b> runs on the owning client (the Spider-Man split, like Ladybug's Yo-yo); the server
 * picks the hook and ends the line. Static state holds entity ids, never entities; dropped by {@link #forget} /
 * {@link #clearSessionState}.
 */
public final class ErasureMoves {
	/** The capture weapon's grey and the stare's red. */
	public static final DustParticleOptions SCARF = new DustParticleOptions(new Vector3f(0.62f, 0.63f, 0.66f), 1.0f);
	public static final DustParticleOptions RED = new DustParticleOptions(new Vector3f(0.95f, 0.08f, 0.08f), 0.8f);

	/** A root (bind / hoist): where the creature is pinned and till when. */
	private record Root(UUID owner, ResourceKey<Level> dim, Vec3 pin, long until, boolean hoist, boolean hadNoGravity) {
	}

	private static final Map<Integer, Root> ROOTS = new ConcurrentHashMap<>();
	/** the block the swing hangs from. */
	private static final Map<UUID, BlockPos> LINE_BLOCK = new ConcurrentHashMap<>();
	/** the creatures the last tick of the stare erased (tests / HUD). */
	private static final Map<UUID, List<Integer>> LAST_ERASED = new ConcurrentHashMap<>();
	/** Double Bind's creatures that already hit (once). */
	private static final Set<UUID> BLINKING = ConcurrentHashMap.newKeySet();

	private ErasureMoves() {
	}

	static void clearSessionState() {
		ROOTS.clear();
		LINE_BLOCK.clear();
		LAST_ERASED.clear();
		BLINKING.clear();
	}

	static void forget(UUID id) {
		LINE_BLOCK.remove(id);
		LAST_ERASED.remove(id);
		BLINKING.remove(id);
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
	private static void whip(ServerPlayer p, Vec3 tip) {
		long t = now(p);
		Quirks.edit(p, n -> {
			n.vals.put(ErasureConfig.V_LASH_AT, t);
			n.vecs.put(ErasureConfig.P_LASH, List.of(tip.x, tip.y, tip.z));
		});
	}

	private static void scarfLine(ServerPlayer p, Vec3 a, Vec3 b) {
		AbilityHelpers.line(p.serverLevel(), a, b, SCARF, 1.5);
	}

	// ---------------------------------------------------------------- roots (bind / hoist)

	/**
	 * Pin {@code e} for {@code ticks} ({@code hoist}: hung {@link ErasureConfig#HOIST_HEIGHT} up, as far as there is
	 * room). Bosses are immune (0). Players get the softened crowd-control rules. Returns the ticks it holds.
	 */
	public static int root(ServerPlayer owner, LivingEntity e, int ticks, boolean hoist) {
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
		Vec3 pin = e.position();
		if (hoist) {
			double up = 0.0;
			for (double h = 0.25; h <= ErasureConfig.HOIST_HEIGHT + 1.0e-6; h += 0.25) {
				if (!e.level().noCollision(e, e.getBoundingBox().move(0, h, 0))) {
					break;
				}
				up = h;
			}
			pin = pin.add(0, up, 0);
		}
		Root old = ROOTS.get(e.getId());
		boolean hadNoGravity = old != null ? old.hadNoGravity() : e.isNoGravity();
		ROOTS.put(e.getId(), new Root(owner.getUUID(), e.level().dimension(), pin, now(owner) + ticks, hoist, hadNoGravity));
		if (hoist && !(e instanceof Player)) {
			e.setNoGravity(true);
		}
		return ticks;
	}

	public static boolean rooted(Entity e) {
		Root r = ROOTS.get(e.getId());
		return r != null && r.until() > e.level().getGameTime();
	}

	public static boolean hoisted(Entity e) {
		Root r = ROOTS.get(e.getId());
		return r != null && r.hoist() && r.until() > e.level().getGameTime();
	}

	private static void unroot(Entity e) {
		Root r = ROOTS.remove(e.getId());
		release(e, r);
	}

	private static void release(Entity e, @Nullable Root r) {
		if (r != null && r.hoist() && !(e instanceof Player)) {
			e.setNoGravity(r.hadNoGravity());
		}
		if (e instanceof LivingEntity le && r != null) {
			le.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
		}
	}

	// ================================================================ R: Capture Lash / Capture Disarm

	/** The scarf whips out: {@link ErasureConfig#LASH_DAMAGE} and the target is yanked in (never a boss). */
	public static @Nullable LivingEntity lash(ServerPlayer p) {
		LivingEntity t = aimed(p, ErasureConfig.LASH_RANGE, 4.0);
		Quirks.setAnim(p, ErasureConfig.ANIM_LASH);
		sound(p, SoundEvents.ARMOR_EQUIP_LEATHER, 1.0f, 1.6f);
		sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.6f, 1.6f);
		if (t == null) {
			whip(p, lookEnd(p, ErasureConfig.LASH_RANGE));
			Quirks.setCooldown(p, ErasureConfig.LASH, ErasureConfig.LASH_MISS_COOLDOWN);
			return null;
		}
		whip(p, mid(t));
		scarfLine(p, hand(p), mid(t));
		if (strike(p, t, p.position(), ErasureConfig.LASH_DAMAGE, 0.0, 0.0) && !isBoss(t)) {
			Vec3 to = p.position().subtract(t.position());
			double d = to.length();
			if (d > 1.8) {
				Vec3 v = to.normalize().scale(Math.min(ErasureConfig.LASH_PULL, d * 0.18)).add(0, 0.32, 0);
				t.setDeltaMovement(v);
				t.hurtMarked = true;
			}
		}
		Quirks.setCooldown(p, ErasureConfig.LASH, ErasureConfig.LASH_COOLDOWN);
		return t;
	}

	/** The scarf snaps round a mob's hand and yanks its held item to his feet (not a boss, not a player). */
	public static ItemStack disarm(ServerPlayer p) {
		LivingEntity t = aimed(p, ErasureConfig.DISARM_RANGE, 4.0);
		Quirks.setAnim(p, ErasureConfig.ANIM_DISARM);
		sound(p, SoundEvents.ARMOR_EQUIP_LEATHER, 1.0f, 1.3f);
		if (!(t instanceof Mob m) || isBoss(t)) {
			whip(p, t != null ? mid(t) : lookEnd(p, ErasureConfig.DISARM_RANGE));
			if (t == null) {
				Quirks.say(p, "message.projecthero.quirk.erasure.no_target", ChatFormatting.GRAY);
			} else {
				Quirks.say(p, "message.projecthero.quirk.erasure.no_disarm", ChatFormatting.GRAY);
			}
			Quirks.setCooldown(p, ErasureConfig.DISARM, ErasureConfig.LASH_MISS_COOLDOWN);
			return ItemStack.EMPTY;
		}
		EquipmentSlot slot = !m.getMainHandItem().isEmpty() ? EquipmentSlot.MAINHAND : !m.getOffhandItem().isEmpty() ? EquipmentSlot.OFFHAND : null;
		whip(p, mid(m));
		if (slot == null) {
			Quirks.say(p, "message.projecthero.quirk.erasure.no_disarm", ChatFormatting.GRAY);
			Quirks.setCooldown(p, ErasureConfig.DISARM, ErasureConfig.LASH_MISS_COOLDOWN);
			return ItemStack.EMPTY;
		}
		ItemStack taken = m.getItemBySlot(slot).copy();
		m.setItemSlot(slot, ItemStack.EMPTY);
		if (m.isUsingItem()) {
			m.stopUsingItem();
		}
		ItemEntity drop = new ItemEntity(p.level(), p.getX(), p.getY() + 0.3, p.getZ(), taken);
		drop.setPickUpDelay(10);
		p.level().addFreshEntity(drop);
		scarfLine(p, hand(p), mid(m));
		sound(p, SoundEvents.ITEM_PICKUP, 0.8f, 0.8f);
		Quirks.setCooldown(p, ErasureConfig.DISARM, ErasureConfig.DISARM_COOLDOWN);
		return taken;
	}

	// ================================================================ G: Erase / Erase Glare (held)

	/** G down: the stare starts (refused with dry eyes). */
	public static boolean eraseDown(ServerPlayer p, boolean glare) {
		if (st(p).val(ErasureConfig.V_ERASE) != 0L) {
			return false;
		}
		String id = glare ? ErasureConfig.GLARE : ErasureConfig.ERASE;
		if (!Quirks.ready(p, id)) {
			return false;
		}
		if (Quirks.meter(p) < ErasureConfig.ERASE_MIN_START) {
			Quirks.say(p, "message.projecthero.quirk.overloaded.erasure", ChatFormatting.RED);
			return false;
		}
		long t = now(p);
		Quirks.edit(p, n -> {
			n.vals.put(ErasureConfig.V_ERASE, glare ? 2L : 1L);
			n.vals.put(ErasureConfig.V_ERASE_AT, t);
		});
		sound(p, SoundEvents.BEACON_POWER_SELECT, 0.6f, glare ? 0.6f : 0.9f);
		tickErase(p, st(p));
		return true;
	}

	/** G up / the meter ran dry / he was stopped: the stare ends. */
	public static void eraseUp(ServerPlayer p) {
		long mode = st(p).val(ErasureConfig.V_ERASE);
		if (mode == 0L) {
			return;
		}
		Quirks.edit(p, n -> {
			n.vals.remove(ErasureConfig.V_ERASE);
			n.vals.remove(ErasureConfig.V_ERASE_AT);
		});
		LAST_ERASED.remove(p.getUUID());
		if (mode == 2L) {
			Quirks.setCooldown(p, ErasureConfig.GLARE, ErasureConfig.GLARE_COOLDOWN);
		} else {
			Quirks.setCooldown(p, ErasureConfig.ERASE, ErasureConfig.ERASE_COOLDOWN);
		}
	}

	/** Who the stare holds this tick: Erase -- the one he looks at; Glare -- everyone in the wide cone. */
	public static List<LivingEntity> stareTargets(ServerPlayer p, boolean glare) {
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		double range = ErasureConfig.ERASE_RANGE;
		double cos = Math.cos(Math.toRadians(glare ? ErasureConfig.GLARE_CONE_DEGREES : ErasureConfig.ERASE_CONE_DEGREES));
		List<LivingEntity> out = new ArrayList<>();
		LivingEntity best = null;
		double bestDot = -2.0;
		// the creature under the crosshair always counts (big bodies, the dragon's parts)
		LivingEntity direct = AbilityHelpers.raycastEntity(p, range);
		for (LivingEntity e : p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(range),
				e -> erasable(p, e))) {
			Vec3 to = mid(e).subtract(eye);
			double d = to.length();
			if (d > range + e.getBbWidth()) {
				continue;
			}
			double dot = d < 0.5 ? 1.0 : to.scale(1.0 / d).dot(look);
			if ((dot < cos && e != direct) || !p.hasLineOfSight(e)) {
				continue;
			}
			if (glare) {
				out.add(e);
			} else if (e == direct) {
				best = e;
				bestDot = 2.0;
			} else if (dot > bestDot) {
				best = e;
				bestDot = dot;
			}
		}
		if (!glare && best != null) {
			out.add(best);
		}
		return out;
	}

	private static void tickErase(ServerPlayer p, QuirkState s) {
		long mode = s.val(ErasureConfig.V_ERASE);
		if (mode == 0L) {
			return;
		}
		boolean glare = mode == 2L;
		List<LivingEntity> targets = stareTargets(p, glare);
		List<Integer> ids = new ArrayList<>(targets.size());
		ServerLevel level = p.serverLevel();
		for (LivingEntity e : targets) {
			PowerSuppression.suppress(e, ErasureConfig.SUPPRESS_TICKS);
			ids.add(e.getId());
			if (p.tickCount % 5 == 0) {
				Vec3 c = e.position().add(0, e.getBbHeight() * 0.9, 0);
				level.sendParticles(RED, c.x, c.y, c.z, 2, 0.2, 0.15, 0.2, 0.0);
			}
		}
		LAST_ERASED.put(p.getUUID(), List.copyOf(ids));
		if (p.tickCount % 4 == 0) {
			Vec3 eye = p.getEyePosition().add(p.getLookAngle().scale(0.3));
			level.sendParticles(RED, eye.x, eye.y, eye.z, 1, 0.08, 0.02, 0.08, 0.0);
		}
		float drain = ErasureConfig.ERASE_DRAIN * (glare ? ErasureConfig.GLARE_DRAIN_MULT : 1f);
		if (Quirks.addMeter(p, -drain) <= 0f) {
			blink(p);
		}
	}

	/** Dry Eyes at 0: the forced blink -- the stare ends, eyes shut {@link ErasureConfig#BLINK_TICKS}. */
	public static void blink(ServerPlayer p) {
		BLINKING.add(p.getUUID());
		try {
			Quirks.overload(p, ErasureConfig.BLINK_TICKS);
		} finally {
			BLINKING.remove(p.getUUID());
		}
		Quirks.setAnim(p, ErasureConfig.ANIM_BLINK);
		Quirks.say(p, "message.projecthero.quirk.overloaded.erasure", ChatFormatting.RED);
		sound(p, SoundEvents.ARMOR_EQUIP_LEATHER, 0.5f, 0.6f);
	}

	public static List<Integer> lastErased(UUID id) {
		return LAST_ERASED.getOrDefault(id, List.of());
	}

	// ================================================================ X: Capture Swing / Capture Zip

	/** X down: the scarf hooks a block and he swings (X again / let go: release). */
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

	/** X up: a swing lets go (a zip runs to the end). */
	public static void swingUp(ServerPlayer p) {
		if (st(p).val(ErasureConfig.V_LINE) == 1L) {
			detach(p, true);
		}
	}

	/** Shift+X: the scarf wraps the creature he looks at and reels him to it. */
	public static @Nullable LivingEntity zip(ServerPlayer p) {
		if (st(p).val(ErasureConfig.V_LINE) != 0L) {
			detach(p, true);
		}
		LivingEntity t = aimed(p, ErasureConfig.ZIP_RANGE, 5.0);
		if (t == null) {
			Quirks.say(p, "message.projecthero.quirk.erasure.no_target", ChatFormatting.GRAY);
			return null;
		}
		Vec3 hook = mid(t);
		long at = now(p);
		Quirks.edit(p, n -> {
			n.vals.put(ErasureConfig.V_LINE, 2L);
			n.vals.put(ErasureConfig.V_LINE_AT, at);
			n.vals.put(ErasureConfig.V_ZIP_TARGET, t.getId() + 1L);
			n.vals.remove(ErasureConfig.V_ROPE);
			n.vecs.put(ErasureConfig.P_HOOK, List.of(hook.x, hook.y, hook.z));
		});
		LINE_BLOCK.remove(p.getUUID());
		Quirks.setAnim(p, ErasureConfig.ANIM_ZIP);
		Quirks.setCooldown(p, ErasureConfig.ZIP, ErasureConfig.ZIP_COOLDOWN);
		floatTolerance(p, true);
		sound(p, SoundEvents.FISHING_BOBBER_THROW, 0.9f, 1.1f);
		return t;
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
				strike(p, t, p.position(), ErasureConfig.ZIP_KICK_DAMAGE, 0.6, 0.2);
				sound(p, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.9f, 1.1f);
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

	// ================================================================ Z: Capture Bind / Capture Hoist

	/** Z: wrap the target, rooted {@link ErasureConfig#BIND_TICKS} (Shift: hung up off the ground). Bosses are immune. */
	public static @Nullable LivingEntity bind(ServerPlayer p, boolean hoist) {
		LivingEntity t = aimed(p, ErasureConfig.BIND_RANGE, 4.0);
		Quirks.setAnim(p, hoist ? ErasureConfig.ANIM_HOIST : ErasureConfig.ANIM_BIND);
		sound(p, SoundEvents.ARMOR_EQUIP_LEATHER, 1.0f, 1.0f);
		String id = hoist ? ErasureConfig.HOIST : ErasureConfig.BIND;
		if (t == null) {
			whip(p, lookEnd(p, ErasureConfig.BIND_RANGE));
			Quirks.say(p, "message.projecthero.quirk.erasure.no_target", ChatFormatting.GRAY);
			Quirks.setCooldown(p, id, ErasureConfig.LASH_MISS_COOLDOWN);
			return null;
		}
		whip(p, mid(t));
		if (isBoss(t)) {
			Quirks.say(p, "message.projecthero.quirk.erasure.boss_immune", ChatFormatting.GRAY);
			Quirks.setCooldown(p, id, ErasureConfig.LASH_MISS_COOLDOWN);
			return null;
		}
		endBind(p);
		int ticks = root(p, t, ErasureConfig.BIND_TICKS, hoist);
		if (ticks <= 0) {
			Quirks.setCooldown(p, id, ErasureConfig.LASH_MISS_COOLDOWN);
			return null;
		}
		long until = now(p) + ticks;
		Quirks.edit(p, n -> {
			n.vals.put(ErasureConfig.V_BIND, t.getId() + 1L);
			n.vals.put(ErasureConfig.V_BIND_UNTIL, until);
			if (hoist) {
				n.vals.put(ErasureConfig.V_HOIST, 1L);
			} else {
				n.vals.remove(ErasureConfig.V_HOIST);
			}
		});
		scarfLine(p, hand(p), mid(t));
		if (t instanceof Mob m) {
			m.getNavigation().stop();
		}
		sound(p, SoundEvents.WOOL_PLACE, 1.0f, 0.8f);
		Quirks.setCooldown(p, id, hoist ? ErasureConfig.HOIST_COOLDOWN : ErasureConfig.BIND_COOLDOWN);
		return t;
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
			n.vals.remove(ErasureConfig.V_HOIST);
		});
	}

	// ================================================================ V: Bind Slam / Double Bind

	/** V: whip the bound (or the looked-at) creature up and slam it down -- {@link ErasureConfig#SLAM_DAMAGE} on landing. */
	public static @Nullable LivingEntity slam(ServerPlayer p) {
		LivingEntity t = boundTarget(p);
		if (t == null) {
			t = aimed(p, ErasureConfig.SLAM_RANGE, 4.0);
		}
		Quirks.setAnim(p, ErasureConfig.ANIM_SLAM);
		sound(p, SoundEvents.ARMOR_EQUIP_LEATHER, 1.0f, 0.8f);
		if (t == null) {
			whip(p, lookEnd(p, ErasureConfig.SLAM_RANGE));
			Quirks.say(p, "message.projecthero.quirk.erasure.no_target", ChatFormatting.GRAY);
			Quirks.setCooldown(p, ErasureConfig.SLAM, ErasureConfig.LASH_MISS_COOLDOWN);
			return null;
		}
		endBind(p);
		whip(p, mid(t));
		Quirks.setCooldown(p, ErasureConfig.SLAM, ErasureConfig.SLAM_COOLDOWN);
		if (isBoss(t)) {
			// a boss is never thrown about: the whip only stings
			strike(p, t, p.position(), ErasureConfig.SLAM_DAMAGE, 0.0, 0.0);
			return t;
		}
		LivingEntity target = t;
		long at = now(p);
		Quirks.edit(p, n -> {
			n.vals.put(ErasureConfig.V_SLAM, target.getId() + 1L);
			n.vals.put(ErasureConfig.V_SLAM_AT, at);
		});
		target.setDeltaMovement(0.0, ErasureConfig.SLAM_LIFT_SPEED, 0.0);
		target.hurtMarked = true;
		return t;
	}

	private static void tickSlam(ServerPlayer p, QuirkState s) {
		if (s.val(ErasureConfig.V_SLAM) == 0L) {
			return;
		}
		LivingEntity t = byVal(p, s, ErasureConfig.V_SLAM);
		long age = now(p) - s.val(ErasureConfig.V_SLAM_AT);
		if (t == null || age > ErasureConfig.SLAM_MAX_TICKS) {
			endSlam(p);
			return;
		}
		if (age < ErasureConfig.SLAM_LIFT_TICKS) {
			Vec3 v = t.getDeltaMovement();
			t.setDeltaMovement(v.x * 0.5, Math.max(v.y, ErasureConfig.SLAM_LIFT_SPEED * 0.6), v.z * 0.5);
			t.hurtMarked = true;
			return;
		}
		if (age == ErasureConfig.SLAM_LIFT_TICKS) {
			// over his head and down
			Vec3 toward = p.position().subtract(t.position());
			Vec3 flat = new Vec3(toward.x, 0, toward.z);
			Vec3 side = flat.lengthSqr() > 1.0e-4 ? flat.normalize().scale(0.25) : Vec3.ZERO;
			t.setDeltaMovement(side.x, -ErasureConfig.SLAM_DOWN_SPEED, side.z);
			t.hurtMarked = true;
			sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.9f, 0.7f);
			return;
		}
		if (t.onGround() || t.isInWater() || age >= ErasureConfig.SLAM_MAX_TICKS) {
			t.resetFallDistance();
			strike(p, t, t.position(), ErasureConfig.SLAM_DAMAGE, 0.0, 0.0);
			ServerLevel level = p.serverLevel();
			level.sendParticles(ParticleTypes.EXPLOSION, t.getX(), t.getY() + 0.2, t.getZ(), 1, 0, 0, 0, 0);
			level.sendParticles(ParticleTypes.POOF, t.getX(), t.getY() + 0.1, t.getZ(), 12, 0.6, 0.05, 0.6, 0.03);
			level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.GENERIC_BIG_FALL, SoundSource.PLAYERS, 1.0f, 0.8f);
			endSlam(p);
		}
	}

	private static void endSlam(ServerPlayer p) {
		if (st(p).val(ErasureConfig.V_SLAM) == 0L) {
			return;
		}
		Quirks.edit(p, n -> {
			n.vals.remove(ErasureConfig.V_SLAM);
			n.vals.remove(ErasureConfig.V_SLAM_AT);
		});
	}

	/** Shift+V: bind the looked-at creature and the nearest other one, and slam them together. Returns both (or none). */
	public static List<LivingEntity> doubleBind(ServerPlayer p) {
		LivingEntity a = aimed(p, ErasureConfig.DOUBLE_RANGE, 5.0);
		Quirks.setAnim(p, ErasureConfig.ANIM_DOUBLE);
		sound(p, SoundEvents.ARMOR_EQUIP_LEATHER, 1.0f, 0.9f);
		if (a == null) {
			whip(p, lookEnd(p, ErasureConfig.DOUBLE_RANGE));
			Quirks.say(p, "message.projecthero.quirk.erasure.no_target", ChatFormatting.GRAY);
			Quirks.setCooldown(p, ErasureConfig.DOUBLE, ErasureConfig.LASH_MISS_COOLDOWN);
			return List.of();
		}
		LivingEntity b = null;
		double best = Double.MAX_VALUE;
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, mid(a), ErasureConfig.DOUBLE_PARTNER_RADIUS)) {
			if (e == a || e == p) {
				continue;
			}
			double d = e.distanceToSqr(a);
			if (d < best) {
				best = d;
				b = e;
			}
		}
		if (b == null) {
			whip(p, mid(a));
			Quirks.say(p, "message.projecthero.quirk.erasure.no_partner", ChatFormatting.GRAY);
			Quirks.setCooldown(p, ErasureConfig.DOUBLE, ErasureConfig.LASH_MISS_COOLDOWN);
			return List.of();
		}
		endBind(p);
		LivingEntity fa = a, fb = b;
		long at = now(p);
		Quirks.edit(p, n -> {
			n.vals.put(ErasureConfig.V_DB_A, fa.getId() + 1L);
			n.vals.put(ErasureConfig.V_DB_B, fb.getId() + 1L);
			n.vals.put(ErasureConfig.V_DB_AT, at);
		});
		Quirks.setCooldown(p, ErasureConfig.DOUBLE, ErasureConfig.DOUBLE_COOLDOWN);
		scarfLine(p, hand(p), mid(a));
		scarfLine(p, hand(p), mid(b));
		return List.of(a, b);
	}

	private static void tickDouble(ServerPlayer p, QuirkState s) {
		if (s.val(ErasureConfig.V_DB_A) == 0L) {
			return;
		}
		LivingEntity a = byVal(p, s, ErasureConfig.V_DB_A);
		LivingEntity b = byVal(p, s, ErasureConfig.V_DB_B);
		long age = now(p) - s.val(ErasureConfig.V_DB_AT);
		if (a == null || b == null || age > ErasureConfig.DOUBLE_MAX_TICKS + 2) {
			endDouble(p);
			return;
		}
		double gap = mid(a).distanceTo(mid(b)) - (a.getBbWidth() + b.getBbWidth()) * 0.5;
		if (gap <= ErasureConfig.DOUBLE_HIT_DISTANCE || age >= ErasureConfig.DOUBLE_MAX_TICKS) {
			for (LivingEntity e : List.of(a, b)) {
				strike(p, e, mid(a).lerp(mid(b), 0.5), ErasureConfig.DOUBLE_DAMAGE, 0.0, 0.0);
				AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, ErasureConfig.DOUBLE_SLOW_TICKS, 2);
				Vec3 v = e.getDeltaMovement();
				e.setDeltaMovement(v.x * 0.1, v.y, v.z * 0.1);
				e.hurtMarked = true;
			}
			Vec3 c = mid(a).lerp(mid(b), 0.5);
			p.serverLevel().sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 1, 0, 0, 0, 0);
			p.serverLevel().playSound(null, c.x, c.y, c.z, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.PLAYERS, 0.8f, 0.9f);
			endDouble(p);
			return;
		}
		boolean bossA = isBoss(a), bossB = isBoss(b);
		Vec3 meet = bossA ? mid(a) : bossB ? mid(b) : mid(a).lerp(mid(b), 0.5);
		for (LivingEntity e : List.of(a, b)) {
			if (isBoss(e)) {
				continue;
			}
			Vec3 to = meet.subtract(mid(e));
			if (to.lengthSqr() > 1.0e-4) {
				Vec3 v = to.normalize().scale(ErasureConfig.DOUBLE_PULL);
				e.setDeltaMovement(v.x, Math.max(v.y, 0.05), v.z);
				e.hurtMarked = true;
			}
		}
		if (bossA && bossB) {
			// two bosses: neither moves, they take the hit next tick
			long t = now(p) - ErasureConfig.DOUBLE_MAX_TICKS;
			Quirks.edit(p, n -> n.vals.put(ErasureConfig.V_DB_AT, t));
		}
	}

	private static void endDouble(ServerPlayer p) {
		if (st(p).val(ErasureConfig.V_DB_A) == 0L) {
			return;
		}
		Quirks.edit(p, n -> {
			n.vals.remove(ErasureConfig.V_DB_A);
			n.vals.remove(ErasureConfig.V_DB_B);
			n.vals.remove(ErasureConfig.V_DB_AT);
		});
	}

	// ================================================================ C: Goggles / Eye Drops

	/** C: goggles down over the eyes (night vision; the client outlines powered players / special mobs) or back up. */
	public static boolean goggles(ServerPlayer p) {
		boolean on = st(p).val(ErasureConfig.V_GOGGLES) == 0L;
		Quirks.setVal(p, ErasureConfig.V_GOGGLES, on ? 1L : 0L);
		Quirks.setAnim(p, ErasureConfig.ANIM_GOGGLES);
		Quirks.setCooldown(p, ErasureConfig.GOGGLES, ErasureConfig.GOGGLES_COOLDOWN);
		if (on) {
			nightVision(p);
		} else {
			clearNightVision(p);
		}
		sound(p, SoundEvents.ARMOR_EQUIP_IRON.value(), 0.6f, on ? 1.5f : 1.2f);
		return on;
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

	/** Shift+C: Dry Eyes refilled at once; a forced blink ends (usable while blinking). */
	public static void eyeDrops(ServerPlayer p) {
		Quirks.setMeter(p, ErasureConfig.METER_FULL);
		Quirks.edit(p, n -> {
			n.overloadUntil = 0L;
			n.meter = ErasureConfig.METER_FULL;
		});
		Quirks.setAnim(p, ErasureConfig.ANIM_DROPS);
		Quirks.setCooldown(p, ErasureConfig.DROPS, ErasureConfig.DROPS_COOLDOWN);
		Vec3 eye = p.getEyePosition().add(p.getLookAngle().scale(0.25));
		p.serverLevel().sendParticles(ParticleTypes.FALLING_WATER, eye.x, eye.y + 0.1, eye.z, 6, 0.12, 0.05, 0.12, 0.0);
		sound(p, SoundEvents.BOTTLE_EMPTY, 0.7f, 1.6f);
		Quirks.say(p, "message.projecthero.quirk.erasure.drops", ChatFormatting.AQUA);
	}

	// ================================================================ per player / per server tick

	static void tick(ServerPlayer p) {
		QuirkState s = st(p);
		if (s.val(ErasureConfig.V_INIT) == 0L) {
			Quirks.setMeter(p, ErasureConfig.METER_FULL);
			Quirks.setVal(p, ErasureConfig.V_INIT, 1L);
			s = st(p);
		}
		boolean erasing = s.val(ErasureConfig.V_ERASE) != 0L;
		if (erasing) {
			tickErase(p, s);
		} else {
			Quirks.addMeter(p, Quirks.overloaded(p) ? ErasureConfig.BLINK_REFILL : ErasureConfig.REFILL);
		}
		s = st(p);
		tickLine(p, s);
		tickSlam(p, s);
		tickDouble(p, st(p));
		s = st(p);
		if (s.val(ErasureConfig.V_BIND) != 0L && (s.val(ErasureConfig.V_BIND_UNTIL) < now(p) || boundTarget(p) == null)) {
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

	/** Everything running ends (overload / revoke / death / Erased). A blink keeps the goggles on. */
	static void stopAll(ServerPlayer p) {
		if (!Quirks.holds(p, Quirk.ERASURE)) {
			return;
		}
		eraseUp(p);
		detach(p, false);
		endBind(p);
		endSlam(p);
		endDouble(p);
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
		if (!BLINKING.contains(id) && st(p).val(ErasureConfig.V_GOGGLES) != 0L) {
			Quirks.setVal(p, ErasureConfig.V_GOGGLES, 0L);
			clearNightVision(p);
		}
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
				return true;
			}
			if (e instanceof ServerPlayer sp) {
				double y = r.hoist() ? r.pin().y : sp.getY();
				if (sp.position().subtract(r.pin()).horizontalDistanceSqr() > 0.09 || (r.hoist() && Math.abs(sp.getY() - y) > 0.3)) {
					sp.connection.teleport(r.pin().x, y, r.pin().z, sp.getYRot(), sp.getXRot());
				}
				if (r.hoist()) {
					sp.resetFallDistance();
				}
			} else {
				Vec3 v = e.getDeltaMovement();
				e.setDeltaMovement(0.0, r.hoist() ? 0.0 : Math.min(v.y, 0.0), 0.0);
				e.setPos(r.pin().x, r.hoist() ? r.pin().y : e.getY(), r.pin().z);
				if (r.hoist()) {
					e.resetFallDistance();
				}
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
