package com.projecthero.mod.undead;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.undead.data.UndeadState;
import com.projecthero.mod.undead.entity.UndeadPartEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 tap V (user: "a new combat V move for the undead ability"): Rib Shotgun. He grabs his chest with both hands and tears it
 * open ({@link UndeadKitConfig#RIB_SHOT_WINDUP} ticks), then his ribs burst out of it as a spread of
 * {@link UndeadKitConfig#RIB_SHOT_COUNT} bone spikes in a {@code 2 x} {@link UndeadKitConfig#RIB_SHOT_CONE} degree cone, aimed at
 * the crosshair: {@link UndeadKitConfig#RIB_SHOT_DAMAGE} each, out to {@link UndeadKitConfig#RIB_SHOT_RANGE} blocks, a little
 * knockback; a spike that hits a block sticks in it for {@link UndeadKitConfig#RIB_SHOT_EMBED} ticks and crumbles. The blast kicks
 * him back a little. The hole in his chest is drawn (every viewer, from the animation: {@code client.undead.UndeadRibShotFx}) and
 * closes like a regrowing part -- {@link UndeadConfig#REGROW_DELAY}, then bone, flesh and skin over the body's
 * {@code UndeadConfig.FRAMES} -- without taking the torso off (which would drop his chestplate every few seconds).
 * {@link UndeadKitConfig#RIB_SHOT_COOLDOWN} from the shot. Fired with the Rib Cage Guard up, the guard's ribs go too (it folds,
 * its cooldown starts).
 *
 * <p>Static state is dropped by {@link #clearSessionState} / {@link #forget} (from {@link UndeadKit}).
 */
public final class UndeadRibShot {
	public static final String ID = "rib_shotgun";

	/** player -> the game time the ribs fly (the wind-up runs until then). */
	private static final Map<UUID, Long> WINDUPS = new ConcurrentHashMap<>();
	/** player -> the last tick a spike's impact was heard (seven landing at once make one soft knock, not seven). */
	private static final Map<UUID, Long> LAST_KNOCK = new ConcurrentHashMap<>();

	private UndeadRibShot() {
	}

	static void clearSessionState() {
		WINDUPS.clear();
		LAST_KNOCK.clear();
	}

	static void forget(UUID id) {
		WINDUPS.remove(id);
		LAST_KNOCK.remove(id);
	}

	public static boolean windingUp(ServerPlayer p) {
		return WINDUPS.containsKey(p.getUUID());
	}

	/** Tap V (on the release): the chest torn open; the ribs fly {@link UndeadKitConfig#RIB_SHOT_WINDUP} ticks later. */
	public static boolean start(ServerPlayer p) {
		if (!Undead.canAct(p) || WINDUPS.containsKey(p.getUUID())) {
			return false;
		}
		UndeadState s = Undead.state(p);
		if (s.boneSword()) {
			return false;
		}
		int cd = Undead.cooldownRemaining(p, ID);
		if (cd > 0) {
			Undead.say(p, "message.projecthero.undead.cooldown", ChatFormatting.GRAY, Component.translatable("projecthero.undead.ability." + ID),
					String.format(java.util.Locale.ROOT, "%.1f", cd / 20.0f));
			return false;
		}
		if (s.broken(UndeadState.BODY)) {
			Undead.say(p, "message.projecthero.undead.no_chest", ChatFormatting.GRAY);
			return false;
		}
		long now = Math.max(1L, p.level().getGameTime());
		WINDUPS.put(p.getUUID(), now + UndeadKitConfig.RIB_SHOT_WINDUP);
		UndeadState n = s.copy();
		n.animId = UndeadKitConfig.ANIM_RIB_SHOT;
		n.animStart = now;
		Undead.save(p, n);
		ServerLevel level = (ServerLevel) p.level();
		Vec3 c = chest(p);
		// the wet tear as the chest is pulled open (soft: the user finds loud blood sounds annoying)
		UndeadSfx.play(level, c, UndeadSoundEvents.PART_BREAK, 0.4f, 1.15f);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.SKELETON_HURT, SoundSource.PLAYERS, 0.25f, 0.55f);
		return true;
	}

	/** Every tick for an Undead: the wind-up's end fires; losing the torso, re-forming or dying meanwhile ends it free. */
	static void tick(ServerPlayer p) {
		Long at = WINDUPS.get(p.getUUID());
		if (at == null) {
			return;
		}
		long now = p.level().getGameTime();
		UndeadState s = Undead.state(p);
		if (!p.isAlive() || s.broken(UndeadState.BODY) || s.reformUntil > now || s.boneSword() || s.animId != UndeadKitConfig.ANIM_RIB_SHOT) {
			cancel(p);
			return;
		}
		if (now >= at) {
			WINDUPS.remove(p.getUUID());
			fire(p);
		}
	}

	/** The wind-up is broken off (no cooldown). */
	static void cancel(ServerPlayer p) {
		if (WINDUPS.remove(p.getUUID()) == null) {
			return;
		}
		UndeadState s = Undead.state(p);
		if (s.animId == UndeadKitConfig.ANIM_RIB_SHOT) {
			UndeadState n = s.copy();
			n.animId = UndeadState.ANIM_NONE;
			Undead.save(p, n);
		}
	}

	/** The ribs burst out of his chest as the spread of spikes. */
	private static void fire(ServerPlayer p) {
		ServerLevel level = (ServerLevel) p.level();
		long now = level.getGameTime();
		if (UndeadRibGuard.up(p)) {
			UndeadRibGuard.forTestsLower(p); // the guard's ribs are the ones that fly
		}
		Vec3 from = chest(p);
		Vec3 f = aim(p, from);
		Vec3 right = f.cross(new Vec3(0, 1, 0));
		right = right.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : right.normalize();
		Vec3 up = right.cross(f).normalize();
		for (int i = 0; i < UndeadKitConfig.RIB_SHOT_COUNT; i++) {
			Vec3 d = spread(f, right, up, i, level.random.nextFloat(), level.random.nextFloat());
			UndeadPartEntity.shoot(p, UndeadPartEntity.STYLE_RIB, -1, from.add(d.scale(0.15)), d.scale(UndeadKitConfig.RIB_SHOT_SPEED),
					UndeadKitConfig.RIB_SHOT_DAMAGE);
		}
		UndeadState n = Undead.state(p).copy();
		n.abilityReadyAt.put(ID, now + UndeadKitConfig.RIB_SHOT_COOLDOWN);
		Undead.save(p, n);
		// the kick of it: a little shove back along the shot
		Vec3 back = new Vec3(-f.x, 0, -f.z);
		if (back.lengthSqr() > 1.0e-4) {
			back = back.normalize().scale(UndeadKitConfig.RIB_SHOT_RECOIL);
			com.projecthero.mod.hero.power.AbilityHelpers.launchSelf(p, p.getDeltaMovement().add(back.x, 0.04, back.z));
		}
		// a crack of bone and a wet burst, both kept soft
		level.playSound(null, from.x, from.y, from.z, SoundEvents.BONE_BLOCK_BREAK, SoundSource.PLAYERS, 0.7f, 0.65f);
		level.playSound(null, from.x, from.y, from.z, SoundEvents.SKELETON_HURT, SoundSource.PLAYERS, 0.3f, 0.45f);
		UndeadSfx.play(level, from, UndeadSoundEvents.BLOOD_BURST, 0.35f, 1.1f);
		UndeadSfx.play(level, from, UndeadSoundEvents.LAUNCH_WHOOSH, 0.3f, 1.35f);
		level.sendParticles(Undead.BLOOD, from.x + f.x * 0.9, from.y + f.y * 0.9, from.z + f.z * 0.9, 4, 0.15, 0.15, 0.15, 0.0);
	}

	/**
	 * Spike {@code i}'s direction: the first straight along {@code f}, the others round it in a ring -- every other one at the
	 * cone's edge, the rest two thirds out -- each nudged a little by {@code ja} / {@code jb} (0..1).
	 */
	public static Vec3 spread(Vec3 f, Vec3 right, Vec3 up, int i, float ja, float jb) {
		if (i == 0) {
			return f;
		}
		int ring = Math.max(1, UndeadKitConfig.RIB_SHOT_COUNT - 1);
		double theta = (i - 1) * (Math.PI * 2 / ring) + Math.PI / ring + (ja - 0.5) * 0.35;
		double edge = (i % 2 == 0 ? 1.0 : 0.66) * UndeadKitConfig.RIB_SHOT_CONE;
		double r = Math.toRadians(Math.min(UndeadKitConfig.RIB_SHOT_CONE, edge + (jb - 0.5) * 2.0));
		Vec3 side = right.scale(Math.cos(theta)).add(up.scale(Math.sin(theta)));
		return f.scale(Math.cos(r)).add(side.scale(Math.sin(r))).normalize();
	}

	/** Where the ribs come out: the middle of his chest, a little in front of it. */
	public static Vec3 chest(ServerPlayer p) {
		Vec3 look = p.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0, look.z);
		flat = flat.lengthSqr() < 1.0e-4 ? Vec3.ZERO : flat.normalize();
		return p.position().add(0, p.getBbHeight() * 0.62, 0).add(flat.scale(0.3));
	}

	/** From the chest toward whatever the crosshair is on (a block within range, else the far end of the range). */
	private static Vec3 aim(ServerPlayer p, Vec3 from) {
		Vec3 eye = p.getEyePosition();
		Vec3 far = eye.add(p.getLookAngle().scale(UndeadKitConfig.RIB_SHOT_RANGE));
		BlockHitResult bhr = p.level().clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		Vec3 target = bhr.getType() != HitResult.Type.MISS ? bhr.getLocation() : far;
		Vec3 d = target.subtract(from);
		// right up against a wall the chest-to-crosshair line can bend wildly: then just along the look
		return d.lengthSqr() < 4.0 ? p.getLookAngle() : d.normalize();
	}

	/**
	 * A spike landed ({@link UndeadPartEntity}): bone chips (and blood on a creature) and, at most once a tick per volley, a soft
	 * knock -- the bone tick of a spike sticking in a block, or a wet thud in flesh.
	 */
	public static void impact(ServerPlayer owner, Vec3 at, boolean flesh) {
		ServerLevel level = (ServerLevel) owner.level();
		level.sendParticles(UndeadBoneSword.BONE_CHIP, at.x, at.y, at.z, flesh ? 3 : 4, 0.08, 0.08, 0.08, 0.0);
		if (flesh) {
			level.sendParticles(UndeadBoneSword.BLOOD_FLECK, at.x, at.y, at.z, 3, 0.1, 0.1, 0.1, 0.0);
		}
		long now = level.getGameTime();
		Long last = LAST_KNOCK.get(owner.getUUID());
		if (last != null && last == now) {
			return;
		}
		LAST_KNOCK.put(owner.getUUID(), now);
		if (flesh) {
			UndeadSfx.play(level, at, UndeadSoundEvents.BLOOD_SPLAT, 0.3f, 1.1f);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.ARROW_HIT, SoundSource.PLAYERS, 0.35f, 0.8f);
		} else {
			level.playSound(null, at.x, at.y, at.z, SoundEvents.BONE_BLOCK_HIT, SoundSource.PLAYERS, 0.45f, 1.2f);
		}
	}
}
