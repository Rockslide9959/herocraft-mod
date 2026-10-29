package com.projecthero.mod.omnitrix.alien;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.OmnitrixDamage;
import com.projecthero.mod.omnitrix.data.OmnitrixState;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1: one Omnitrix alien's server-side kit. Extend it, register it with {@link Aliens#register} and the alien leaves
 * "coming soon" on the dial. The framework ({@code Omnitrix}, {@code OmnitrixAbilityManager}, {@code OmnitrixBody}) does
 * the rest: the transform and its flash, the 3-minute timer and the recharge, the body ({@link #stats()} as attributes, health
 * converted both ways), the HUD's key boxes and cooldowns ({@link #moves()}), the guide and "Your Power" pages, the key
 * routing below and the reset hooks.
 *
 * <h2>Keys</h2>
 * R ({@code SLOT_1}), G ({@code SLOT_2}), X ({@code SLOT_3}, movement), Z ({@code SLOT_4}), V ({@code SLOT_5}, utility / mode;
 * v0.17.1 user: "add abilities to the V key for every alien") and C ({@code SLOT_6}) and their Shift versions are the kit's. N is
 * the Omnitrix's (it turns the alien back; v0.17.1 user, was V), H is the power selector.
 * <ul>
 *   <li>{@link #press}: a key went down (Shift or not). For a key the kit declares {@link #holdable}, a plain press is held
 *       back: a release before {@code OmnitrixConfig.KIT_HOLD_TICKS} calls {@link #press} (the tap), a longer hold calls
 *       {@link #holdStart} and the release {@link #holdEnd}.</li>
 *   <li>{@link #release}: any key came up (after its press / hold handling).</li>
 *   <li>The vanilla keys: {@link #onAttack} (left-click on a creature; the fist's damage is the body's
 *       {@code attack} stat), {@link #onUse} (right-click on air, either hand -- an empty hand arrives through the
 *       Omnitrix's own payload), {@link #onUseBlock}, {@link #onUseEntity}.</li>
 * </ul>
 *
 * <h2>Helpers</h2>
 * {@link #ready} / {@link #use} (cooldowns from {@link #moves()}), {@link #anim} (a synced pose id for the client kit),
 * {@link #flag} / {@link #setFlag} (synced kit bits), {@link #lookTarget}, {@link #around}, {@link #hurt} (alien damage
 * sources: they feed boss threat tables like any player hit, respect squads and never hit the wearer).
 */
public abstract class AlienKit {
	private List<AlienMove> moves;

	/** The {@link Aliens} id this kit is for. */
	public abstract String id();

	/** The alien's body. Called once per transformation. */
	public abstract AlienStats stats();

	/** Every move, for the HUD boxes, the guide and the cooldown helpers (built once, then cached). */
	protected abstract List<AlienMove> declareMoves();

	public final List<AlienMove> moves() {
		if (moves == null) {
			moves = List.copyOf(new ArrayList<>(declareMoves()));
		}
		return moves;
	}

	@Nullable
	public final AlienMove move(String moveId) {
		for (AlienMove m : moves()) {
			if (m.id().equals(moveId)) {
				return m;
			}
		}
		return null;
	}

	/** The move on {@code slot} (Shift or not, hold or not), or null. */
	@Nullable
	public final AlienMove moveOn(AbilitySlot slot, boolean shift, boolean hold) {
		for (AlienMove m : moves()) {
			if (m.slot() == slot && m.shift() == shift && m.hold() == hold) {
				return m;
			}
		}
		return null;
	}

	// ---------------------------------------------------------------- lifecycle

	/** The wearer just became this alien (attributes and health already applied). */
	public void onTransform(ServerPlayer p) {
	}

	/** The wearer is turning back (timeout, N, death, logout, the pod). Stop every running move, give back flight, etc. */
	public void onRevert(ServerPlayer p) {
	}

	/** Every server tick while transformed (after the framework's own upkeep). */
	public void tick(ServerPlayer p, OmnitrixState s, long now) {
	}

	// ---------------------------------------------------------------- keys

	public boolean holdable(AbilitySlot slot) {
		return false;
	}

	public void press(ServerPlayer p, AbilitySlot slot, boolean shift) {
	}

	public void release(ServerPlayer p, AbilitySlot slot, boolean shift, int heldTicks) {
	}

	public void holdStart(ServerPlayer p, AbilitySlot slot) {
	}

	public void holdEnd(ServerPlayer p, AbilitySlot slot, int heldTicks) {
	}

	/** Left-click on a creature. Return true to replace vanilla's attack (the hit is then entirely the kit's). */
	public boolean onAttack(ServerPlayer p, Entity target) {
		return false;
	}

	/** Right-click on air (either hand). Anything but PASS stops vanilla's item use. */
	public InteractionResult onUse(ServerPlayer p, InteractionHand hand) {
		return InteractionResult.PASS;
	}

	public InteractionResult onUseBlock(ServerPlayer p, InteractionHand hand, BlockHitResult hit) {
		return InteractionResult.PASS;
	}

	public InteractionResult onUseEntity(ServerPlayer p, InteractionHand hand, Entity target) {
		return InteractionResult.PASS;
	}

	// ---------------------------------------------------------------- damage

	/** A veto on damage the wearer takes (immunities): false cancels it. */
	public boolean allowDamage(ServerPlayer p, DamageSource source, float amount) {
		return true;
	}

	/** A change to damage the wearer takes (resistances, stances): return the new amount. */
	public float incomingDamage(ServerPlayer p, DamageSource source, float amount) {
		return amount;
	}

	// ---------------------------------------------------------------- movement hooks (v0.17.1 wave A: XLR8, Ghostfreak)
	// All default off. They are asked on BOTH sides (the owner's client simulates its own movement), so answer from synced
	// state only ({@link #flag} / {@link #timer}). Mixins: OmnitrixAlienCollideMixin, OmnitrixAlienLivingMixin (main),
	// OmnitrixAlienPhaseClientMixin (client).

	/** Horizontal movement ignores blocks (vertical collision kept: through walls, never through floors). */
	public boolean phasing(Player p) {
		return false;
	}

	/** {@code fluid} holds the wearer up like ground (water running). */
	public boolean standsOnFluid(Player p, net.minecraft.world.level.material.FluidState fluid) {
		return false;
	}

	/** Pressing into a wall climbs it, like a ladder (wall running). */
	public boolean climbs(Player p) {
		return false;
	}

	/**
	 * v0.17.1 (Wildmutt): the hitbox and eye height for {@code pose} while transformed, or null for the player's own (scaled
	 * by the {@code scale} stat as usual). A quadruped answers STANDING / CROUCHING with a wide, low box. Asked on both sides
	 * ({@code mixin/OmnitrixAlienBodyMixin} refreshes the dimensions whenever the answer changes, so reverting restores the
	 * player's box). Answer from synced state only.
	 */
	@Nullable
	public net.minecraft.world.entity.EntityDimensions body(Player p, net.minecraft.world.entity.Pose pose) {
		return null;
	}

	/**
	 * v0.17.1 (Wildmutt rework 2, user: "make it so that the player cant use tools"): true for a body with no hands to hold
	 * anything (paws). While true, held items are not drawn (first or third person), using / placing them is refused on both
	 * sides ({@code OmnitrixMod}'s use callbacks), a held tool mines like a bare paw and a held weapon adds nothing to the hit
	 * ({@code mixin/OmnitrixPawsMixin}). Answer from synced state only.
	 */
	public boolean pawsOnly(Player p) {
		return false;
	}

	/** {@link #pawsOnly} of whatever alien {@code p} is right now (false for a human). Client-safe. */
	public static boolean pawsOnlyNow(Player p) {
		AlienKit k = activeKit(p);
		return k != null && k.pawsOnly(p);
	}

	/** The kit of the alien {@code p} is right now (client-safe: reads the synced state), or null for a human. */
	@Nullable
	public static AlienKit activeKit(Player p) {
		if (p == null || !Omnitrix.hasPower(p)) {
			return null;
		}
		OmnitrixState s = Omnitrix.state(p);
		return s.transformed() ? Aliens.kit(s.active) : null;
	}

	// ---------------------------------------------------------------- helpers

	/** {@code <alien>.<move>}: the cooldown key. */
	public final String key(String moveId) {
		return id() + "." + moveId;
	}

	public final boolean ready(ServerPlayer p, String moveId) {
		return Omnitrix.state(p).readyAt(key(moveId)) <= p.level().getGameTime();
	}

	/** Ticks until {@code moveId} is ready (0 = ready). */
	public final int cooldownLeft(Player p, String moveId) {
		return (int) Math.max(0L, Omnitrix.state(p).readyAt(key(moveId)) - p.level().getGameTime());
	}

	/** Starts {@code moveId}'s cooldown ({@link AlienMove#cooldown()} ticks, or {@code ticks} if given >= 0). */
	public final void cooldown(ServerPlayer p, String moveId, int ticks) {
		AlienMove m = move(moveId);
		int t = ticks >= 0 ? ticks : m == null ? 0 : m.cooldown();
		OmnitrixState n = Omnitrix.state(p).copy();
		n.timers.put(key(moveId), p.level().getGameTime() + t);
		Omnitrix.save(p, n);
	}

	/**
	 * The usual gate at the top of a move: false (and the "not ready" line on the action bar) while it cools down; otherwise
	 * the cooldown starts and the move may run.
	 */
	public final boolean use(ServerPlayer p, String moveId) {
		int left = cooldownLeft(p, moveId);
		if (left > 0) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.cooldown",
					Component.translatable("projecthero.omnitrix." + id() + "." + moveId),
					String.format(java.util.Locale.ROOT, "%.1f", left / 20f)).withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		cooldown(p, moveId, -1);
		return true;
	}

	/** Plays pose {@code animId} (the client kit maps it to keyframes) from now. */
	public final void anim(ServerPlayer p, int animId) {
		OmnitrixState n = Omnitrix.state(p).copy();
		n.animId = animId;
		n.animStart = p.level().getGameTime();
		Omnitrix.save(p, n);
	}

	public final boolean flag(Player p, int bit) {
		return (Omnitrix.state(p).kitFlags & bit) != 0;
	}

	public final void setFlag(ServerPlayer p, int bit, boolean on) {
		OmnitrixState s = Omnitrix.state(p);
		int f = on ? s.kitFlags | bit : s.kitFlags & ~bit;
		if (f != s.kitFlags) {
			OmnitrixState n = s.copy();
			n.kitFlags = f;
			Omnitrix.save(p, n);
		}
	}

	/** A kit timer (game time) under {@code <alien>.<name>}; 0 if unset. */
	public final long timer(Player p, String name) {
		return Omnitrix.state(p).readyAt(key(name));
	}

	public final void setTimer(ServerPlayer p, String name, long gameTime) {
		OmnitrixState n = Omnitrix.state(p).copy();
		if (gameTime <= 0L) {
			n.timers.remove(key(name));
		} else {
			n.timers.put(key(name), gameTime);
		}
		Omnitrix.save(p, n);
	}

	/** The creature under the crosshair within {@code range} blocks (line of sight), or null. */
	@Nullable
	public static LivingEntity lookTarget(ServerPlayer p, double range) {
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		Vec3 end = eye.add(look.scale(range));
		var blockHit = p.level().clip(new net.minecraft.world.level.ClipContext(eye, end, net.minecraft.world.level.ClipContext.Block.COLLIDER,
				net.minecraft.world.level.ClipContext.Fluid.NONE, p));
		double max = blockHit.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? range : blockHit.getLocation().distanceTo(eye);
		AABB box = p.getBoundingBox().expandTowards(look.scale(max)).inflate(1.0);
		EntityHitResult hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(p, eye, eye.add(look.scale(max)), box,
				e -> e instanceof LivingEntity le && le.isAlive() && !e.isSpectator() && e != p && !(e instanceof ArmorStand), max * max);
		return hit != null && hit.getEntity() instanceof LivingEntity le ? le : null;
	}

	/** Living creatures within {@code radius} of {@code at}, never the wearer, never squad allies (unless friendly fire is on). */
	public static List<LivingEntity> around(ServerPlayer p, Vec3 at, double radius) {
		List<LivingEntity> out = new ArrayList<>();
		for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius))) {
			if (e == p || !e.isAlive() || e.isSpectator() || e instanceof ArmorStand || e.distanceToSqr(at) > radius * radius * 1.4) {
				continue;
			}
			if (com.projecthero.mod.squad.Squads.areAllies(p, e) && !com.projecthero.mod.squad.Squads.friendlyFireOn(p)) {
				continue;
			}
			out.add(e);
		}
		return out;
	}

	/** Hits {@code target} for {@code amount} with the alien's damage ({@code fire} = {@code projecthero:alien_fire}). */
	public static boolean hurt(ServerPlayer p, LivingEntity target, float amount, boolean fire) {
		if (target == p || amount <= 0f) {
			return false;
		}
		DamageSource src = fire ? OmnitrixDamage.alienFire(p) : OmnitrixDamage.alienStrike(p);
		target.invulnerableTime = Math.min(target.invulnerableTime, 10);
		return target.hurt(src, amount);
	}

	/** Pushes {@code target} away from {@code from} (horizontal {@code power}, plus {@code up}). Honours knockback resistance. */
	public static void shove(LivingEntity target, Vec3 from, double power, double up) {
		Vec3 d = target.position().subtract(from);
		Vec3 h = new Vec3(d.x, 0, d.z);
		h = h.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : h.normalize();
		double res = 1.0 - Math.min(1.0, target.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE));
		target.push(h.x * power * res, up * Math.max(0.3, res), h.z * power * res);
		target.hurtMarked = true;
	}

	protected static ServerLevel level(ServerPlayer p) {
		return (ServerLevel) p.level();
	}
}
