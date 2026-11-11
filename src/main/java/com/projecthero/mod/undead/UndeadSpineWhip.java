package com.projecthero.mod.undead;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.undead.data.UndeadState;
import com.projecthero.mod.undead.entity.UndeadCloneEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 tap Shift+V (user: "give shift+V another combat ability that isnt the rib cage shield"): Spine Whip. He reaches over
 * his shoulder, tears his own spine out through his back ({@link UndeadKitConfig#SPINE_WINDUP} ticks) and lashes it as a long
 * segmented bone whip in a wide arc in front of him ({@link UndeadKitConfig#SPINE_SWEEP} ticks, from his whip-hand side across to
 * the other, {@code 2 x} {@link UndeadKitConfig#SPINE_ARC} degrees, out to {@link UndeadKitConfig#SPINE_RANGE} blocks):
 * {@link UndeadKitConfig#SPINE_DAMAGE} to everything the whip passes through and a shove out and along the swing. Its barbed tip
 * snags the one creature nearest his crosshair (at least {@link UndeadKitConfig#SPINE_SNAG_MIN} blocks out) and yanks it in to
 * him instead. Then he lets go and the spine crumbles; the hole in his back is drawn and closes like a regrowing part, without
 * taking the torso off (which would drop his chestplate) -- as the Rib Shotgun's chest does. {@link UndeadKitConfig#SPINE_COOLDOWN}
 * from the lash.
 *
 * <p>Static state is dropped by {@link #clearSessionState} / {@link #forget} (from {@link UndeadKit}).
 */
public final class UndeadSpineWhip {
	public static final String ID = "spine_whip";

	private static final class Lash {
		final long start;
		final Set<Integer> hit = new HashSet<>();
		/** The creature the tip is to snag (-1: none), picked as the lash begins. */
		int snag = -1;

		Lash(long start) {
			this.start = start;
		}
	}

	/** player -> the lash under way (from the tear to the end of the sweep). */
	private static final Map<UUID, Lash> LASHES = new ConcurrentHashMap<>();

	private UndeadSpineWhip() {
	}

	static void clearSessionState() {
		LASHES.clear();
	}

	static void forget(UUID id) {
		LASHES.remove(id);
	}

	/** Tearing the spine out, or lashing it (the sweep not over yet). */
	public static boolean active(ServerPlayer p) {
		return LASHES.containsKey(p.getUUID());
	}

	/** Tearing the spine out (before the lash). */
	public static boolean windingUp(ServerPlayer p) {
		Lash l = LASHES.get(p.getUUID());
		return l != null && p.level().getGameTime() - l.start < UndeadKitConfig.SPINE_WINDUP;
	}

	/** Tap Shift+V (on the release): he reaches back for his spine; the lash comes {@link UndeadKitConfig#SPINE_WINDUP} ticks later. */
	public static boolean start(ServerPlayer p) {
		if (!Undead.canAct(p) || LASHES.containsKey(p.getUUID())) {
			return false;
		}
		UndeadState s = Undead.state(p);
		if (s.boneSword() || UndeadRibShot.windingUp(p)) {
			return false;
		}
		int cd = Undead.cooldownRemaining(p, ID);
		if (cd > 0) {
			Undead.say(p, "message.projecthero.undead.cooldown", ChatFormatting.GRAY, Component.translatable("projecthero.undead.ability." + ID),
					String.format(java.util.Locale.ROOT, "%.1f", cd / 20.0f));
			return false;
		}
		if (s.broken(UndeadState.BODY)) {
			Undead.say(p, "message.projecthero.undead.no_spine", ChatFormatting.GRAY);
			return false;
		}
		int arm = Undead.shootingArm(p);
		if (arm < 0) {
			Undead.say(p, "message.projecthero.undead.no_arm", ChatFormatting.GRAY);
			return false;
		}
		long now = Math.max(1L, p.level().getGameTime());
		LASHES.put(p.getUUID(), new Lash(now));
		UndeadState n = s.copy();
		n.animId = UndeadKitConfig.ANIM_SPINE_WHIP;
		n.animStart = now;
		n.setFlag(UndeadState.FLAG_OFF_ARM, arm != Undead.mainArm(p));
		Undead.save(p, n);
		ServerLevel level = (ServerLevel) p.level();
		Vec3 b = back(p);
		// the wet crack of it coming loose (kept soft)
		UndeadSfx.play(level, b, UndeadSoundEvents.PART_BREAK, 0.35f, 0.9f);
		level.playSound(null, b.x, b.y, b.z, SoundEvents.SKELETON_HURT, SoundSource.PLAYERS, 0.2f, 0.5f);
		return true;
	}

	/** Every tick for an Undead: the tear, then the lash sweeping through whatever is in front of him. */
	static void tick(ServerPlayer p) {
		Lash l = LASHES.get(p.getUUID());
		if (l == null) {
			return;
		}
		long now = p.level().getGameTime();
		UndeadState s = Undead.state(p);
		if (!p.isAlive() || s.broken(UndeadState.BODY) || s.reformUntil > now || s.boneSword() || s.animId != UndeadKitConfig.ANIM_SPINE_WHIP) {
			cancel(p);
			return;
		}
		long age = now - l.start;
		ServerLevel level = (ServerLevel) p.level();
		if (age == UndeadKitConfig.SPINE_WINDUP - 2) {
			// torn free: the crack of bone as it comes out over his shoulder
			Vec3 b = back(p);
			level.playSound(null, b.x, b.y, b.z, SoundEvents.BONE_BLOCK_BREAK, SoundSource.PLAYERS, 0.55f, 0.6f);
			level.sendParticles(Undead.BLOOD, b.x, b.y + 0.2, b.z, 3, 0.12, 0.12, 0.12, 0.0);
		}
		if (age < UndeadKitConfig.SPINE_WINDUP) {
			return;
		}
		int k = (int) (age - UndeadKitConfig.SPINE_WINDUP); // 0 = the lash starts
		if (k == 0) {
			UndeadState n = s.copy();
			n.abilityReadyAt.put(ID, now + UndeadKitConfig.SPINE_COOLDOWN);
			Undead.save(p, n);
			l.snag = pickSnag(p);
			Vec3 c = chest(p);
			UndeadSfx.play(level, c, UndeadSoundEvents.LAUNCH_WHOOSH, 0.35f, 0.75f);
			level.playSound(null, c.x, c.y, c.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.35f, 0.6f);
		}
		sweep(p, l, k);
		if (k >= UndeadKitConfig.SPINE_SWEEP - 1) {
			// the crack at the end of the lash, then he lets go of it
			Vec3 c = chest(p);
			level.playSound(null, c.x, c.y, c.z, SoundEvents.BONE_BLOCK_HIT, SoundSource.PLAYERS, 0.4f, 0.7f);
			LASHES.remove(p.getUUID());
		}
	}

	/** Broken off before (or during) the lash: no cooldown if it never lashed. */
	static void cancel(ServerPlayer p) {
		if (LASHES.remove(p.getUUID()) == null) {
			return;
		}
		UndeadState s = Undead.state(p);
		if (s.animId == UndeadKitConfig.ANIM_SPINE_WHIP) {
			UndeadState n = s.copy();
			n.animId = UndeadState.ANIM_NONE;
			Undead.save(p, n);
		}
	}

	/** Gametests: break it off now (as losing the torso would). */
	public static void forTestsCancel(ServerPlayer p) {
		cancel(p);
	}

	/** Whip-hand side: +1 when the whip is in his right hand (the lash goes right to left), -1 in his left. */
	public static float side(Player p, UndeadState s) {
		int arm = s.flag(UndeadState.FLAG_OFF_ARM) ? Undead.otherArm(Undead.mainArm(p)) : Undead.mainArm(p);
		return arm == UndeadState.RIGHT_ARM ? 1f : -1f;
	}

	/**
	 * The whip's angle off his look (degrees, + to his right) {@code t} (0..1) through the sweep: from {@code side} x the arc's
	 * edge across to the other edge, quick in the middle.
	 */
	public static float angle(float side, float t) {
		t = Mth.clamp(t, 0f, 1f);
		float e = t * t * (3f - 2f * t);
		return side * (float) UndeadKitConfig.SPINE_ARC * (1f - 2f * e);
	}

	/** Sweep tick {@code k}: everything the whip passed through between the last tick's angle and this one's is hit. */
	private static void sweep(ServerPlayer p, Lash l, int k) {
		float side = side(p, Undead.state(p));
		float a0 = k == 0 ? side * (float) (UndeadKitConfig.SPINE_ARC + 12.0) : angle(side, k / (float) UndeadKitConfig.SPINE_SWEEP);
		float a1 = k >= UndeadKitConfig.SPINE_SWEEP - 1 ? -side * (float) (UndeadKitConfig.SPINE_ARC + 12.0) : angle(side, (k + 1) / (float) UndeadKitConfig.SPINE_SWEEP);
		float lo = Math.min(a0, a1), hi = Math.max(a0, a1);
		Vec3 f = flat(p);
		Vec3 right = f.cross(new Vec3(0, 1, 0));
		Vec3 c = chest(p);
		ServerLevel level = (ServerLevel) p.level();
		for (LivingEntity e : inReach(p)) {
			if (l.hit.contains(e.getId())) {
				continue;
			}
			Vec3 to = new Vec3(e.getX() - p.getX(), 0, e.getZ() - p.getZ());
			double d = to.length();
			double half = e.getBbWidth() * 0.5;
			boolean hitNow;
			if (d < 1.2) {
				hitNow = k == 0; // right up against him: caught as it starts
			} else {
				double a = Math.toDegrees(Math.atan2(to.dot(right), to.dot(f)));
				double pad = Math.toDegrees(Math.atan2(half + 0.3, d));
				hitNow = a >= lo - pad && a <= hi + pad;
			}
			if (!hitNow || !clear(p, c, e)) {
				continue;
			}
			l.hit.add(e.getId());
			Vec3 at = e.position().add(0, e.getBbHeight() * 0.5, 0);
			if (e.getId() == l.snag) {
				snag(p, e);
			} else {
				UndeadAbilities.strike(p, e, p.position(), UndeadKitConfig.SPINE_DAMAGE, UndeadKitConfig.SPINE_KNOCK, UndeadKitConfig.SPINE_LIFT);
				if (!UndeadAbilities.isBoss(e)) {
					// swept along the swing as well as out
					Vec3 along = right.scale(-side * UndeadKitConfig.SPINE_SWEEP_PUSH);
					com.projecthero.mod.hero.power.AbilityHelpers.push(e, along);
				}
			}
			level.sendParticles(UndeadBoneSword.BONE_CHIP, at.x, at.y, at.z, 3, 0.1, 0.1, 0.1, 0.0);
			level.sendParticles(UndeadBoneSword.BLOOD_FLECK, at.x, at.y, at.z, 3, 0.12, 0.12, 0.12, 0.0);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.35f, 0.7f);
		}
	}

	/** The barbed tip catches it: the hit, then it is yanked in to land just in front of him. */
	private static void snag(ServerPlayer p, LivingEntity e) {
		UndeadAbilities.strike(p, e, p.position(), UndeadKitConfig.SPINE_DAMAGE, 0.0, 0.0);
		if (UndeadAbilities.isBoss(e)) {
			return;
		}
		Vec3 to = new Vec3(p.getX() - e.getX(), 0, p.getZ() - e.getZ());
		double d = to.length();
		if (d > UndeadKitConfig.SPINE_YANK_TO) {
			double speed = Math.min(UndeadKitConfig.SPINE_YANK_MAX, (d - UndeadKitConfig.SPINE_YANK_TO) * UndeadKitConfig.SPINE_YANK_PER_BLOCK);
			Vec3 v = to.normalize().scale(speed).add(0, UndeadKitConfig.SPINE_YANK_LIFT, 0);
			e.setDeltaMovement(v);
			e.hurtMarked = true;
			e.hasImpulse = true;
		}
		ServerLevel level = (ServerLevel) p.level();
		Vec3 at = e.position().add(0, e.getBbHeight() * 0.5, 0);
		UndeadKitNet.fx(level, UndeadKitNet.SPINE_SNAG, at, new Vec3(p.getX(), p.getY(), p.getZ()), e.getId());
		level.playSound(null, at.x, at.y, at.z, SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.PLAYERS, 0.5f, 0.6f);
	}

	/** The creature the tip will snag: the one in the arc nearest the crosshair, at least SPINE_SNAG_MIN out (-1 if none). */
	private static int pickSnag(ServerPlayer p) {
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		Vec3 f = flat(p);
		double best = Math.cos(Math.toRadians(UndeadKitConfig.SPINE_SNAG_CONE));
		int id = -1;
		for (LivingEntity e : inReach(p)) {
			Vec3 to = new Vec3(e.getX() - p.getX(), 0, e.getZ() - p.getZ());
			if (to.length() < UndeadKitConfig.SPINE_SNAG_MIN || to.normalize().dot(f) < Math.cos(Math.toRadians(UndeadKitConfig.SPINE_ARC))) {
				continue;
			}
			Vec3 mid = e.position().add(0, e.getBbHeight() * 0.5, 0);
			double dot = mid.subtract(eye).normalize().dot(look);
			if (dot > best && clear(p, chest(p), e)) {
				best = dot;
				id = e.getId();
			}
		}
		return id;
	}

	/** Everything that can be hurt within the whip's reach and its height band. */
	private static List<LivingEntity> inReach(ServerPlayer p) {
		double r = UndeadKitConfig.SPINE_RANGE;
		double y = chest(p).y;
		AABB box = new AABB(p.getX() - r - 1, y - UndeadKitConfig.SPINE_BELOW, p.getZ() - r - 1, p.getX() + r + 1, y + UndeadKitConfig.SPINE_ABOVE, p.getZ() + r + 1);
		List<LivingEntity> out = new ArrayList<>();
		for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, box,
				e -> e != p && e.isAlive() && !(e instanceof UndeadCloneEntity) && HeroTargets.canHarm(p, e))) {
			double dx = e.getX() - p.getX(), dz = e.getZ() - p.getZ();
			double half = e.getBbWidth() * 0.5;
			if (Math.sqrt(dx * dx + dz * dz) - half <= r) {
				out.add(e);
			}
		}
		return out;
	}

	/** Nothing solid between his chest and it (the whip does not cut through walls). */
	private static boolean clear(ServerPlayer p, Vec3 from, LivingEntity e) {
		Vec3 to = e.position().add(0, Math.min(e.getBbHeight() * 0.5, 1.0), 0);
		return p.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p)).getType() == HitResult.Type.MISS;
	}

	private static Vec3 flat(ServerPlayer p) {
		Vec3 look = p.getLookAngle();
		Vec3 f = new Vec3(look.x, 0, look.z);
		return f.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : f.normalize();
	}

	/** The whip's height: his chest. */
	public static Vec3 chest(ServerPlayer p) {
		return p.position().add(0, p.getBbHeight() * 0.62, 0);
	}

	/** Where the spine comes out: the middle of his back. */
	private static Vec3 back(ServerPlayer p) {
		return p.position().add(0, p.getBbHeight() * 0.6, 0).subtract(flat(p).scale(0.2));
	}
}
