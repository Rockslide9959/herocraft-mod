package com.projecthero.mod.spider;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.spider.data.SpiderMoveState;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.2: Spider-Man is hard to notice. Every mob's target search multiplies its detection range by
 * {@code getVisibilityPercent} ({@code SpiderStealthVisibilityMixin}); for Spider-Man that becomes:
 *
 * <ul>
 * <li>walking: 85% &middot; sneaking: 60% &middot; wall-crawling / on a ceiling / perched / hanging: 40%</li>
 * <li>x0.75 more in the dark (light 7 or less)</li>
 * <li>swinging, airborne at speed, or <b>exposed</b> (he hit something, was hit, or fired a loud move in the last
 * 5 s): 100%</li>
 * </ul>
 * Bosses and the Warden (it hunts by sound) see him normally, and so does anything within 1.5 blocks. Stealth only
 * stops a mob <em>noticing</em> him -- a mob already chasing keeps chasing, unless he breaks line of sight and stays
 * hidden for 4 s (then it loses track; bosses never do).
 *
 * <p>Ambush: the first hit on a mob that has not noticed him does +50% and stuns it for 1.5 s (10 s cooldown).
 *
 * <p>The stealth level is synced to the owner ({@link ModAttachments#SPIDER_STEALTH}) for the HUD eye.
 */
public final class SpiderStealth {
	public static final int EXPOSED = 0;
	public static final int SEEN = 1;     // walking / noisy
	public static final int LOW = 2;      // sneaking
	public static final int HIDDEN = 3;   // crawling / perched / hanging

	private static final double WALK = 0.85;
	private static final double SNEAK = 0.60;
	private static final double HIDE = 0.40;
	private static final double DARK = 0.75;
	private static final double VANILLA_SNEAK = 0.8; // vanilla already applies this to a crouching player
	private static final int EXPOSE_TICKS = 100;
	private static final int LOSE_TRACK_TICKS = 80;
	private static final int AMBUSH_COOLDOWN = 200;
	private static final float AMBUSH_BONUS = 0.5f;
	private static final int AMBUSH_STUN = 30;

	/** player id -> game time his exposure ends. Transient. */
	private static final Map<Integer, Long> EXPOSED_UNTIL = new ConcurrentHashMap<>();
	/** player id -> game time his ambush is ready again. */
	private static final Map<Integer, Long> AMBUSH_READY = new ConcurrentHashMap<>();
	/** mob id -> game time it last saw the Spider-Man it is chasing. */
	private static final Map<Integer, Long> LAST_SAW = new ConcurrentHashMap<>();
	/** target id -> attacker id, for a hit judged an ambush before it landed. */
	private static final Map<Integer, Integer> PENDING_AMBUSH = new ConcurrentHashMap<>();
	private static final ThreadLocal<Boolean> REENTRANT = ThreadLocal.withInitial(() -> false);

	private SpiderStealth() {
	}

	public static void initialize() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (!REENTRANT.get() && source.getEntity() instanceof ServerPlayer p && p != entity && SpiderMan.hasPower(p)
					&& entity instanceof Mob mob && unaware(mob, p) && ambushReady(p)) {
				PENDING_AMBUSH.put(entity.getId(), p.getId());
			}
			return true;
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (source.getEntity() instanceof ServerPlayer p && SpiderMan.hasPower(p) && p != entity) {
				expose(p);
				Integer by = PENDING_AMBUSH.remove(entity.getId());
				if (by != null && by == p.getId() && !blocked && taken > 0.0f && entity.isAlive()) {
					ambush(p, entity, taken);
				}
			}
			if (entity instanceof ServerPlayer p && SpiderMan.hasPower(p) && taken > 0.0f) {
				expose(p);
			}
		});
	}

	// ---------------- the visibility factor (from the mixin) ----------------

	/** Multiplier on {@code looker}'s detection range against {@code player} (1 = untouched). */
	public static double factor(Player player, Entity looker) {
		if (!(looker instanceof Mob mob) || !SpiderMan.hasPower(player) || player.level().isClientSide) {
			return 1.0;
		}
		if (SpiderWebs.isBoss(mob) || mob instanceof Warden || mob.distanceToSqr(player) < 2.25) {
			return 1.0;
		}
		int level = level(player);
		double f = switch (level) {
			case HIDDEN -> HIDE;
			case LOW -> SNEAK;
			case SEEN -> WALK;
			default -> 1.0;
		};
		if (level >= SEEN && dark(player)) {
			f *= DARK;
		}
		if (player.isDiscrete()) {
			f /= VANILLA_SNEAK; // vanilla already took its 0.8 off a crouching player -- the table is the total
		}
		return Math.min(1.0, f);
	}

	/** The stealth level right now (server side). */
	public static int level(Player player) {
		long now = player.level().getGameTime();
		Long until = EXPOSED_UNTIL.get(player.getId());
		if (until != null && now < until) {
			return EXPOSED;
		}
		if (SpiderSwing.isSwinging(player) && !SpiderMoves.hangingUpsideDown(player)) {
			return EXPOSED; // a swing is anything but quiet
		}
		SpiderMoveState ms = SpiderMoves.state(player);
		if (SpiderClimb.attached(player) || SpiderMoves.hangingUpsideDown(player)
				|| (ms != null && ms.has(SpiderMoveState.FLAG_PERCH))) {
			return HIDDEN;
		}
		if (!player.onGround() && player.getDeltaMovement().horizontalDistanceSqr() > 0.09) {
			return EXPOSED; // flying past at speed
		}
		return player.isShiftKeyDown() ? LOW : SEEN;
	}

	private static boolean dark(Player player) {
		return player.level().getMaxLocalRawBrightness(player.blockPosition()) <= 7;
	}

	/** He hit something, was hit, or fired a loud move: normal visibility for 5 s. */
	public static void expose(ServerPlayer player) {
		EXPOSED_UNTIL.put(player.getId(), player.level().getGameTime() + EXPOSE_TICKS);
	}

	// ---------------- ambush ----------------

	private static boolean unaware(Mob mob, ServerPlayer p) {
		return mob.getTarget() != p && mob.getLastHurtByMob() != p && !SpiderWebs.isBoss(mob);
	}

	private static boolean ambushReady(ServerPlayer p) {
		Long ready = AMBUSH_READY.get(p.getId());
		return ready == null || p.level().getGameTime() >= ready;
	}

	private static void ambush(ServerPlayer p, LivingEntity target, float taken) {
		AMBUSH_READY.put(p.getId(), p.level().getGameTime() + AMBUSH_COOLDOWN);
		REENTRANT.set(true);
		try {
			target.invulnerableTime = 0;
			AbilityHelpers.hurt(p, target, taken * AMBUSH_BONUS);
		} finally {
			REENTRANT.set(false);
		}
		AbilityHelpers.applyControl(target, MobEffects.MOVEMENT_SLOWDOWN, AMBUSH_STUN, 6);
		AbilityHelpers.applyControl(target, MobEffects.WEAKNESS, AMBUSH_STUN, 1);
		if (target instanceof Mob mob) {
			mob.getNavigation().stop();
		}
		if (p.level() instanceof ServerLevel level) {
			// the silent flourish: a quick web wrap round it, no sound
			level.sendParticles(ParticleTypes.ITEM_COBWEB, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
					12, target.getBbWidth() * 0.4, target.getBbHeight() * 0.35, target.getBbWidth() * 0.4, 0.02);
		}
		p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.projecthero.spider_man.ambush")
				.withStyle(net.minecraft.ChatFormatting.GRAY), true);
	}

	// ---------------- per tick (from SpiderManAbilityManager) ----------------

	public static void serverTick(ServerPlayer player) {
		long now = player.level().getGameTime();
		if (player.tickCount % 5 == 0) {
			int level = level(player);
			if (level >= SEEN && dark(player) && level < HIDDEN) {
				level++; // the HUD eye shows the dark's help as one step more hidden
			}
			if (player.getAttachedOrElse(ModAttachments.SPIDER_STEALTH, 1) != level) {
				player.setAttached(ModAttachments.SPIDER_STEALTH, level);
			}
		}
		// lose track: a chaser that has not seen him for 4 s while he stays hidden gives up
		if (player.tickCount % 10 != 0) {
			return;
		}
		boolean hiding = level(player) >= LOW;
		for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(48.0),
				m -> m.getTarget() == player)) {
			if (SpiderWebs.isBoss(mob) || mob instanceof Warden) {
				continue;
			}
			if (!hiding || mob.getSensing().hasLineOfSight(player)) {
				LAST_SAW.put(mob.getId(), now);
				continue;
			}
			long saw = LAST_SAW.computeIfAbsent(mob.getId(), k -> now);
			if (now - saw >= LOSE_TRACK_TICKS) {
				mob.setTarget(null);
				mob.setLastHurtByMob(null);
				mob.getNavigation().stop();
				LAST_SAW.remove(mob.getId());
			}
		}
	}

	public static void clearFor(ServerPlayer player) {
		EXPOSED_UNTIL.remove(player.getId());
		AMBUSH_READY.remove(player.getId());
	}

	public static void clearSessionState() {
		EXPOSED_UNTIL.clear();
		AMBUSH_READY.clear();
		LAST_SAW.clear();
		PENDING_AMBUSH.clear();
	}
}
