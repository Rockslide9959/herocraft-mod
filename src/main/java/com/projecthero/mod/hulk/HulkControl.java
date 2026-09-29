package com.projecthero.mod.hulk;

import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hulk.data.HulkState;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;

/**
 * v0.13.14: keeping control of the Hulk. While he keeps dealing damage the player is fully in charge. Once he goes
 * {@code graceTicks} without landing a hit, control starts to slip ({@code drainPerSecond}) and a key prompt appears
 * every {@code promptEveryTicks}: press the shown movement key (forward / left / back / right) within the window to
 * claw control back; the wrong key, or none, loses more. At 0 the Hulk <b>rampages</b> for {@code rampageTicks}: he
 * picks the nearest living thing, turns on it, charges and pounds it, leaps about, smashes the ground and anything soft
 * in his way -- and the player's ability keys, H, the calm-down and riders are all shut out until it ends. Then the
 * player wrestles back {@code rampageRestore} control. Numbers in {@link HulkConfig#control()}.
 *
 * <p>v0.13.15: only an <b>unwilling</b> Hulk ({@link HulkState.Combat#unwilling} -- rage hit 100, or the death save) fights
 * for control. A Hulk the player let out with H stays theirs for as long as he lasts.
 */
public final class HulkControl {
	public static final int KEY_FORWARD = 1;
	public static final int KEY_LEFT = 2;
	public static final int KEY_BACK = 3;
	public static final int KEY_RIGHT = 4;

	/** Rampage bookkeeping (server only): the current victim, next swing, next leap, wander heading. */
	private static final class Rampage {
		int targetId = -1;
		long nextSwing;
		long nextLeap;
		long nextStomp;
		Vec3 wander = Vec3.ZERO;
		long nextWander;
	}

	private static final Map<UUID, Rampage> RAMPAGES = new ConcurrentHashMap<>();

	private HulkControl() {
	}

	public static void clearSessionState() {
		RAMPAGES.clear();
	}

	public static void clear(UUID id) {
		RAMPAGES.remove(id);
	}

	/** Client-safe: is he rampaging right now? */
	public static boolean rampaging(net.minecraft.world.entity.player.Player player) {
		HulkState s = player.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.HULK_STATE, null);
		return s != null && s.rampaging(player.level().getGameTime());
	}

	/** The Hulk just landed a hit: full control again, prompt cleared. */
	static void onDealtDamage(ServerPlayer player) {
		HulkState s = Hulk.state(player);
		if (!s.hulk) {
			return;
		}
		long now = player.level().getGameTime();
		if (s.rampaging(now)) {
			return; // hits during a rampage are the Hulk's, not the player's
		}
		HulkState n = s.copy();
		n.combat.lastDealtAt = now;
		n.combat.control = 100.0f;
		n.combat.promptKey = 0;
		n.combat.promptUntil = 0L;
		Hulk.save(player, n);
	}

	/** The player pressed a movement key while a prompt was showing. */
	public static void answer(ServerPlayer player, int key) {
		HulkState s = Hulk.state(player);
		long now = player.level().getGameTime();
		if (!s.hulk || s.combat.promptKey == 0 || now > s.combat.promptUntil || s.rampaging(now)) {
			return;
		}
		HulkConfig.Control cfg = HulkConfig.control();
		HulkState n = s.copy();
		boolean right = key == s.combat.promptKey;
		n.combat.control = Math.max(0.0f, Math.min(100.0f, s.combat.control + (right ? cfg.promptRestore : -cfg.promptPenalty)));
		n.combat.promptKey = 0;
		n.combat.promptUntil = now + (right ? cfg.promptEveryTicks : cfg.promptEveryTicks / 2);
		Hulk.save(player, n);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				right ? SoundEvents.NOTE_BLOCK_CHIME.value() : SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, right ? 0.7f : 0.5f,
				right ? 1.2f : 1.5f);
	}

	/** Every tick for a Gamma player (from {@link Hulk#tick}). */
	static void tick(ServerPlayer player) {
		HulkState s = Hulk.state(player);
		long now = player.level().getGameTime();
		if (!s.hulk || !HulkConfig.control().enabled || !s.combat.unwilling) {
			if (s.combat.rampageUntil != 0L || s.combat.promptKey != 0 || s.combat.control != 100.0f) {
				HulkState n = s.copy();
				n.combat.rampageUntil = 0L;
				n.combat.promptKey = 0;
				n.combat.promptUntil = 0L;
				n.combat.control = 100.0f;
				n.combat.lastDealtAt = now;
				Hulk.save(player, n);
			}
			RAMPAGES.remove(player.getUUID());
			return;
		}
		if (s.combat.rampageUntil != 0L) {
			if (now >= s.combat.rampageUntil) {
				endRampage(player);
			} else {
				tickRampage(player, now);
			}
			return;
		}
		HulkConfig.Control cfg = HulkConfig.control();
		// a fresh Hulk (or one straight out of the change) starts in full control
		long since = now - Math.max(s.combat.lastDealtAt, s.formChangedAt + HulkConfig.FORCED_CHANGE_TICKS);
		if (since < cfg.graceTicks || s.combat.calming) {
			return;
		}
		HulkState n = s.copy();
		boolean dirty = false;
		if (now % 20L == 0L) {
			n.combat.control = Math.max(0.0f, n.combat.control - cfg.drainPerSecond);
			dirty = true;
		}
		// the prompt: show one, or time an unanswered one out
		if (n.combat.promptKey != 0 && now > n.combat.promptUntil) {
			n.combat.control = Math.max(0.0f, n.combat.control - cfg.promptPenalty);
			n.combat.promptKey = 0;
			n.combat.promptUntil = now + cfg.promptEveryTicks / 2;
			dirty = true;
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 0.5f, 1.5f);
		} else if (n.combat.promptKey == 0 && now >= n.combat.promptUntil) {
			n.combat.promptKey = 1 + player.getRandom().nextInt(4);
			n.combat.promptUntil = now + cfg.promptWindowTicks;
			dirty = true;
		}
		if (n.combat.control <= 0.0f) {
			Hulk.save(player, n);
			startRampage(player);
			return;
		}
		if (dirty) {
			Hulk.save(player, n);
		}
	}

	// ---------------------------------------------------------------- the rampage

	static void startRampage(ServerPlayer player) {
		long now = player.level().getGameTime();
		HulkState n = Hulk.state(player).copy();
		n.combat.rampageUntil = now + HulkConfig.control().rampageTicks;
		n.combat.promptKey = 0;
		n.combat.promptUntil = 0L;
		n.combat.control = 0.0f;
		n.combat.smashChargeStart = 0L;
		n.leapChargeStart = 0L;
		n.combat.calming = false;
		Hulk.save(player, n);
		HulkAbilities.endCharge(player);
		HulkGrab.release(player);
		player.ejectPassengers();
		RAMPAGES.put(player.getUUID(), new Rampage());
		ServerLevel level = (ServerLevel) player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 3.0f, 0.5f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_ROAR, SoundSource.PLAYERS, 1.5f, 0.8f);
		player.displayClientMessage(Component.translatable("message.projecthero.hulk.rampage_start")
				.withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.BOLD), true);
	}

	private static void endRampage(ServerPlayer player) {
		RAMPAGES.remove(player.getUUID());
		long now = player.level().getGameTime();
		HulkState n = Hulk.state(player).copy();
		n.combat.rampageUntil = 0L;
		n.combat.control = HulkConfig.control().rampageRestore;
		n.combat.lastDealtAt = now;
		n.combat.promptUntil = now + HulkConfig.control().promptEveryTicks;
		Hulk.save(player, n);
		player.displayClientMessage(Component.translatable("message.projecthero.hulk.rampage_end")
				.withStyle(ChatFormatting.GREEN), true);
	}

	/** v0.13.17: how far a rampaging Hulk looks for something to smash. */
	public static final double RAMPAGE_RANGE = 100.0;

	/**
	 * v0.13.17: out in the open -- standing at (or above) the surface: no more than 2 blocks under the highest solid block
	 * of its column (leaves don't count, so things under trees are fair game; things in caves are not).
	 */
	public static boolean aboveGround(LivingEntity e) {
		int surface = e.level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, e.getBlockX(), e.getBlockZ());
		return e.getY() >= surface - 2.0;
	}

	private static boolean fairGame(ServerPlayer player, LivingEntity e) {
		if (e instanceof OwnableEntity pet && player.getUUID().equals(pet.getOwnerUUID())) {
			return false; // never his own pets
		}
		return !(e instanceof Villager) || player.getRandom().nextInt(3) == 0; // villagers mostly get ignored
	}

	private static void tickRampage(ServerPlayer player, long now) {
		Rampage r = RAMPAGES.computeIfAbsent(player.getUUID(), k -> new Rampage());
		ServerLevel level = (ServerLevel) player.level();
		// v0.13.17: he hunts anything above ground within 100 blocks (was 20) -- nothing down in caves under him
		LivingEntity target = r.targetId < 0 || !(level.getEntity(r.targetId) instanceof LivingEntity l) || !l.isAlive()
				|| l.distanceToSqr(player) > (RAMPAGE_RANGE + 10.0) * (RAMPAGE_RANGE + 10.0) || !aboveGround(l) ? null : l;
		if (target == null || now % 20L == 0L) {
			target = HulkCombat.targets(player, player.getBoundingBox().inflate(RAMPAGE_RANGE, 64.0, RAMPAGE_RANGE)).stream()
					.filter(e -> e.distanceToSqr(player) <= RAMPAGE_RANGE * RAMPAGE_RANGE && aboveGround(e) && fairGame(player, e))
					.min(Comparator.comparingDouble(e -> e.distanceToSqr(player))).orElse(null);
			r.targetId = target == null ? -1 : target.getId();
		}
		Vec3 dir;
		if (target != null) {
			Vec3 to = target.position().subtract(player.position());
			dir = new Vec3(to.x, 0, to.z);
			dir = dir.lengthSqr() < 1.0e-4 ? HulkAbilities.flatLook(player) : dir.normalize();
			if (now % 4L == 0L) {
				player.lookAt(EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
			}
			double reach = 3.0 + player.getBbWidth();
			double dist = Math.sqrt(to.x * to.x + to.z * to.z);
			if (dist <= reach && now >= r.nextSwing) {
				player.resetAttackStrengthTicker();
				player.attack(target);
				player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
				r.nextSwing = now + 12;
			}
			if (dist > 9.0 && now >= r.nextLeap && player.onGround()) {
				HulkState n = Hulk.state(player).copy();
				HulkAbilities.launch(player, n, Math.min(40.0, dist), to.normalize().add(0, 0.25, 0));
				Hulk.save(player, n);
				r.nextLeap = now + 60;
				return;
			}
		} else {
			// nothing to fight: stomp about, smashing the scenery
			if (now >= r.nextWander || r.wander.lengthSqr() < 1.0e-4) {
				double a = player.getRandom().nextDouble() * Math.PI * 2.0;
				r.wander = new Vec3(Math.cos(a), 0, Math.sin(a));
				r.nextWander = now + 40 + player.getRandom().nextInt(40);
				player.setYRot((float) Math.toDegrees(Math.atan2(-r.wander.x, r.wander.z)));
				player.lookAt(EntityAnchorArgument.Anchor.EYES, player.getEyePosition().add(r.wander.scale(5)));
			}
			dir = r.wander;
			if (now >= r.nextLeap && player.onGround() && player.getRandom().nextInt(80) == 0) {
				HulkState n = Hulk.state(player).copy();
				HulkAbilities.launch(player, n, 18.0, dir.add(0, 0.4, 0));
				Hulk.save(player, n);
				r.nextLeap = now + 60;
				return;
			}
		}
		if (now >= r.nextStomp && player.onGround() && player.getRandom().nextInt(50) == 0) {
			HulkAbilities.anim(player, HulkState.ANIM_SMASH);
			HulkAbilities.schedule(player, HulkAbilities.SMASH_IMPACT_TICKS, () -> HulkAbilities.groundSmashImpact(player));
			r.nextStomp = now + 80;
		}
		if (!Hulk.state(player).leaping) {
			HulkAbilities.breakAhead(player, dir, true);
			double vy = player.onGround() ? -0.05 : Math.max(player.getDeltaMovement().y - 0.08, -1.2);
			AbilityHelpers.launchSelf(player, new Vec3(dir.x * 0.42, vy, dir.z * 0.42));
			player.setSprinting(true);
		}
		if (now % 10L == 0L) {
			level.sendParticles(HulkAbilities.GAMMA, player.getX(), player.getY() + player.getBbHeight() * 0.6, player.getZ(), 4, 0.5,
					player.getBbHeight() * 0.3, 0.5, 0.02);
		}
		if (now % 50L == 0L) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 1.5f, 0.6f);
		}
	}
}
