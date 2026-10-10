package com.projecthero.mod.hulk;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hulk.data.HulkState;
import com.projecthero.mod.hulk.entity.HulkBoulderEntity;
import com.projecthero.mod.hulk.entity.HulkEntities;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * v0.13.14: V -- the Hulk's hands.
 * <ul>
 *   <li><b>V</b> picks up the mob you are looking at (6 blocks) and holds it overhead; <b>V</b> again throws it -- it
 *       smashes into whatever it hits (16 to it and to everything round the impact).</li>
 *   <li><b>Shift+V</b> while holding a mob crushes it (26 damage) and drops it.</li>
 *   <li><b>Shift+V</b> with empty hands tears a chunk of earth out of the ground ({@link HulkBoulderEntity}); <b>V</b>
 *       throws it and it explodes on impact (30 in 4 blocks).</li>
 * </ul>
 * The 8-second cooldown starts at the throw / crush. Bosses and anything too big (wider than 2.5 blocks or taller than
 * 3.5) cannot be picked up; enemy players only with PvP on. v0.13.17: a <b>squad-mate</b> can always be picked up -- carried
 * safely overhead, set down with Shift+V (or by sneaking), or thrown (they bowl over enemies and land unhurt).
 */
public final class HulkGrab {
	/** Who is holding what (entity id -- a mob or a boulder). */
	private static final Map<UUID, Integer> HELD = new ConcurrentHashMap<>();

	private record Thrown(int entityId, long since, Set<Integer> hit) {
	}

	/** Mobs in flight after a throw, per thrower. */
	private static final Map<UUID, java.util.List<Thrown>> THROWN = new ConcurrentHashMap<>();

	private HulkGrab() {
	}

	/** v0.13.17: squad-mates the Hulk carried or threw take no fall damage until this game time. */
	private static final Map<UUID, Long> SAFE_LANDING = new ConcurrentHashMap<>();
	private static final int SAFE_LANDING_TICKS = 8 * 20;

	public static void clearSessionState() {
		HELD.clear();
		THROWN.clear();
		SAFE_LANDING.clear();
	}

	/** v0.13.17: was this player just carried or thrown by a squad-mate Hulk (so the landing doesn't hurt)? */
	public static boolean safeLanding(ServerPlayer player) {
		Long until = SAFE_LANDING.get(player.getUUID());
		if (until == null) {
			return false;
		}
		if (player.level().getGameTime() > until) {
			SAFE_LANDING.remove(player.getUUID());
			return false;
		}
		return true;
	}

	static void protectLanding(ServerPlayer mate) {
		SAFE_LANDING.put(mate.getUUID(), mate.level().getGameTime() + SAFE_LANDING_TICKS);
		mate.resetFallDistance();
	}

	private static boolean isTeammate(ServerPlayer hulk, Entity e) {
		return e instanceof ServerPlayer mate && HulkCombat.ally(hulk, mate);
	}

	/** v0.13.17: put a carried squad-mate down gently in front of him. */
	private static void setDown(ServerPlayer player, ServerPlayer mate) {
		HELD.remove(player.getUUID());
		setHolding(player, false);
		HulkAbilities.anim(player, HulkState.ANIM_PICKUP);
		Vec3 at = player.position().add(HulkAbilities.flatLook(player).scale(1.6));
		mate.teleportTo(mate.serverLevel(), at.x, player.getY(), at.z, mate.getYRot(), mate.getXRot());
		mate.setDeltaMovement(Vec3.ZERO);
		mate.hurtMarked = true;
		protectLanding(mate);
	}

	/** Death / logout / dimension change / revert: let go of whatever he holds (a boulder crumbles). */
	public static void release(ServerPlayer player) {
		Integer id = HELD.remove(player.getUUID());
		THROWN.remove(player.getUUID());
		if (id != null) {
			Entity e = ((ServerLevel) player.level()).getEntity(id);
			if (e instanceof HulkBoulderEntity boulder) {
				boulder.crumble();
			} else if (e instanceof ServerPlayer mate) {
				protectLanding(mate); // dropped from overhead: no fall damage
			}
		}
		setHolding(player, false);
	}

	public static boolean holding(ServerPlayer player) {
		return HELD.containsKey(player.getUUID());
	}

	private static void setHolding(ServerPlayer player, boolean holding) {
		HulkState s = Hulk.state(player);
		if (s.combat.holding != holding) {
			HulkState n = s.copy();
			n.combat.holding = holding;
			Hulk.save(player, n);
		}
	}

	/** V pressed ({@code shift}: Shift+V). */
	public static void press(ServerPlayer player, boolean shift) {
		if (!HulkAbilities.canAct(player)) {
			return;
		}
		Entity held = heldEntity(player);
		if (held != null) {
			if (shift && isTeammate(player, held)) {
				setDown(player, (ServerPlayer) held); // v0.13.17: a squad-mate is set down, never crushed
			} else if (shift && held instanceof LivingEntity mob) {
				crush(player, mob);
			} else {
				throwHeld(player, held);
			}
			return;
		}
		if (!HulkAbilities.ready(player, HulkAbilities.GRAB)) {
			return;
		}
		if (shift) {
			tearEarth(player);
		} else {
			grab(player);
		}
	}

	private static Entity heldEntity(ServerPlayer player) {
		Integer id = HELD.get(player.getUUID());
		if (id == null) {
			return null;
		}
		Entity e = ((ServerLevel) player.level()).getEntity(id);
		if (e == null || e.isRemoved() || (e instanceof LivingEntity l && !l.isAlive())) {
			HELD.remove(player.getUUID());
			setHolding(player, false);
			return null;
		}
		return e;
	}

	// ---------------------------------------------------------------- grab / crush / throw

	private static void grab(ServerPlayer player) {
		LivingEntity target = AbilityHelpers.raycastEntity(player, HulkConfig.abilities().grabRange);
		if (target == null || !target.isAlive()) {
			return;
		}
		// v0.13.17: a squad-mate can be picked up too -- carried safely, set down with Shift+V, or thrown with no harm to them
		boolean teammate = target instanceof ServerPlayer mate && HulkCombat.ally(player, mate);
		if (com.projecthero.mod.titanshifter.TitanCombat.isBoss(target) || target.getBbWidth() > 2.5f || target.getBbHeight() > 3.5f
				|| (!teammate && HulkCombat.targets(player, target.getBoundingBox().inflate(0.1)).stream().noneMatch(e -> e == target))) {
			Hulk.say(player, "message.projecthero.hulk.grab_refused", ChatFormatting.GRAY);
			return;
		}
		if (teammate) {
			((ServerPlayer) target).displayClientMessage(net.minecraft.network.chat.Component.translatable(
					"message.projecthero.hulk.carried", player.getDisplayName()).withStyle(ChatFormatting.GREEN), true);
		}
		target.stopRiding();
		target.ejectPassengers();
		HELD.put(player.getUUID(), target.getId());
		setHolding(player, true);
		HulkAbilities.anim(player, HulkState.ANIM_PICKUP);
		ServerLevel level = (ServerLevel) player.level();
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 1.2f, 0.6f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_ATTACK, SoundSource.PLAYERS, 0.9f, 0.8f);
	}

	private static void crush(ServerPlayer player, LivingEntity mob) {
		HELD.remove(player.getUUID());
		setHolding(player, false);
		HulkAbilities.anim(player, HulkState.ANIM_CRUSH);
		HulkAbilities.cooldown(player, HulkAbilities.GRAB, HulkConfig.abilities().grabCooldownTicks);
		mob.invulnerableTime = 0;
		AbilityHelpers.hurtLands(player, mob, HulkConfig.abilities().crushDamage);
		ServerLevel level = (ServerLevel) player.level();
		Vec3 c = mob.position().add(0, mob.getBbHeight() * 0.5, 0);
		level.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 20, 0.3, 0.3, 0.3, 0.3);
		level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, c.x, c.y, c.z, 8, 0.3, 0.3, 0.3, 0.1);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.2f, 0.5f);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.SKELETON_HURT, SoundSource.PLAYERS, 1.0f, 0.5f);
		// dropped at his feet, in front
		Vec3 drop = player.position().add(HulkAbilities.flatLook(player).scale(1.5));
		mob.teleportTo(drop.x, drop.y, drop.z);
		mob.setDeltaMovement(Vec3.ZERO);
		mob.hurtMarked = true;
	}

	private static void throwHeld(ServerPlayer player, Entity held) {
		HELD.remove(player.getUUID());
		setHolding(player, false);
		HulkAbilities.anim(player, HulkState.ANIM_THROW);
		HulkAbilities.cooldown(player, HulkAbilities.GRAB, HulkConfig.abilities().grabCooldownTicks);
		ServerLevel level = (ServerLevel) player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.2f, 0.5f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 0.7f, 1.3f);
		if (held instanceof HulkBoulderEntity boulder) {
			boulder.launch(player.getLookAngle().scale(HulkConfig.abilities().boulderSpeed).add(0, 0.15, 0));
			return;
		}
		Vec3 v = player.getLookAngle().scale(HulkConfig.abilities().throwSpeed).add(0, 0.25, 0);
		held.setDeltaMovement(v);
		held.hurtMarked = true;
		if (isTeammate(player, held)) {
			protectLanding((ServerPlayer) held); // v0.13.17: a thrown squad-mate lands unhurt (and still bowls over enemies)
		}
		Set<Integer> hit = new HashSet<>();
		hit.add(held.getId());
		THROWN.computeIfAbsent(player.getUUID(), k -> new java.util.ArrayList<>())
				.add(new Thrown(held.getId(), level.getGameTime(), hit));
	}

	// ---------------------------------------------------------------- the earth chunk

	private static void tearEarth(ServerPlayer player) {
		ServerLevel level = (ServerLevel) player.level();
		Vec3 fwd = HulkAbilities.flatLook(player);
		BlockPos under = BlockPos.containing(player.position().add(fwd.scale(1.5))).below();
		BlockState ground = level.getBlockState(under);
		if (ground.isAir()) {
			ground = level.getBlockState(player.blockPosition().below());
		}
		if (ground.isAir() || !ground.getFluidState().isEmpty()) {
			Hulk.say(player, "message.projecthero.hulk.no_earth", ChatFormatting.GRAY);
			return;
		}
		HulkBoulderEntity boulder = HulkEntities.BOULDER.create(level);
		if (boulder == null) {
			return;
		}
		boulder.setOwner(player);
		Vec3 at = holdPoint(player, boulder);
		boulder.moveTo(at.x, at.y, at.z, player.getYRot(), 0.0f);
		level.addFreshEntity(boulder);
		HELD.put(player.getUUID(), boulder.getId());
		setHolding(player, true);
		HulkAbilities.anim(player, HulkState.ANIM_PICKUP);
		// rip it out of the ground: a pit where it came from
		if (HulkCombat.canBreakBlocks(level)) {
			BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					for (int dy = 0; dy >= -1; dy--) {
						p.set(under.getX() + dx, under.getY() + dy, under.getZ() + dz);
						if (Math.abs(dx) + Math.abs(dz) + (-dy) <= 2 && HulkCombat.breakable(level, p, level.getBlockState(p))) {
							level.destroyBlock(p, false, player);
						}
					}
				}
			}
		}
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), under.getX() + 0.5, under.getY() + 1.0, under.getZ() + 0.5,
				50, 0.8, 0.4, 0.8, 0.2);
		level.playSound(null, under.getX(), under.getY(), under.getZ(), SoundEvents.ROOTED_DIRT_BREAK, SoundSource.PLAYERS, 1.5f, 0.5f);
		level.playSound(null, under.getX(), under.getY(), under.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.6f, 1.2f);
	}

	/** Where a held thing sits: above his head, a little forward. */
	private static Vec3 holdPoint(ServerPlayer player, Entity held) {
		Vec3 fwd = HulkAbilities.flatLook(player);
		return player.position().add(fwd.scale(0.3)).add(0, player.getBbHeight() + 0.35, 0);
	}

	// ---------------------------------------------------------------- tick

	/** Every Gamma player, every tick (from {@link Hulk#tick}). */
	static void tick(ServerPlayer player) {
		Integer id = HELD.get(player.getUUID());
		if (id != null) {
			HulkState s = Hulk.state(player);
			Entity held = heldEntity(player);
			long now = player.level().getGameTime();
			if (held != null && (!s.hulk || s.rampaging(now) || !player.isAlive() || held.distanceToSqr(player) > 144.0)) {
				release(player);
			} else if (held instanceof ServerPlayer mate && mate.isShiftKeyDown() && isTeammate(player, mate)) {
				setDown(player, mate); // v0.13.17: a carried squad-mate sneaks to get down
			} else if (held != null) {
				Vec3 at = holdPoint(player, held);
				if (held instanceof ServerPlayer sp) {
					sp.teleportTo(sp.serverLevel(), at.x, at.y, at.z, sp.getYRot(), sp.getXRot());
				} else {
					held.moveTo(at.x, at.y, at.z, held.getYRot(), held.getXRot());
				}
				held.setDeltaMovement(Vec3.ZERO);
				held.fallDistance = 0.0f;
				held.hurtMarked = true;
			}
		}
		tickThrown(player);
	}

	private static void tickThrown(ServerPlayer player) {
		java.util.List<Thrown> list = THROWN.get(player.getUUID());
		if (list == null || list.isEmpty()) {
			return;
		}
		ServerLevel level = (ServerLevel) player.level();
		long now = level.getGameTime();
		Iterator<Thrown> it = list.iterator();
		while (it.hasNext()) {
			Thrown t = it.next();
			Entity e = level.getEntity(t.entityId());
			if (e == null || e.isRemoved() || now - t.since() > 80) {
				it.remove();
				continue;
			}
			if (now - t.since() < 2) {
				continue;
			}
			LivingEntity struck = null;
			for (LivingEntity other : HulkCombat.targets(player, e.getBoundingBox().inflate(0.4))) {
				if (other != e && !t.hit().contains(other.getId())) {
					struck = other;
					break;
				}
			}
			boolean landed = e.horizontalCollision || (e.onGround() && now - t.since() > 3) || e.isInWater();
			if (struck == null && !landed) {
				if (now % 2 == 0) {
					level.sendParticles(ParticleTypes.CLOUD, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 1, 0.1, 0.1, 0.1, 0.0);
				}
				continue;
			}
			it.remove();
			impact(player, e);
		}
	}

	/** A thrown mob hits something: it and everything round the impact take the hit. */
	private static void impact(ServerPlayer player, Entity thrown) {
		ServerLevel level = (ServerLevel) player.level();
		float dmg = HulkConfig.abilities().throwImpactDamage;
		Vec3 at = thrown.position().add(0, thrown.getBbHeight() * 0.5, 0);
		HulkCombat.Hit hit = new HulkCombat.Hit(dmg, 1.2, 0.3);
		if (thrown instanceof LivingEntity l && !isTeammate(player, l)) {
			l.invulnerableTime = 0;
			HulkCombat.strike(player, l, at, hit, 1.0f);
		}
		HulkCombat.radial(player, at, 2.5, hit, false);
		level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 12, 0.5, 0.3, 0.5, 0.05);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_BIG_FALL, SoundSource.PLAYERS, 1.4f, 0.6f);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0f, 0.6f);
	}

	/**
	 * v0.15.3: may the Hulk lay hands on {@code target} to hurt it -- V's rules for an enemy (no bosses, nothing wider than
	 * 2.5 or taller than 3.5 blocks, only what {@link HulkCombat#targets} allows). Shared with the Gladiator's Arena Grapple.
	 */
	public static boolean grabbable(ServerPlayer player, LivingEntity target) {
		return target != null && target.isAlive() && !com.projecthero.mod.titanshifter.TitanCombat.isBoss(target)
				&& target.getBbWidth() <= 2.5f && target.getBbHeight() <= 3.5f && !isHeld(target)
				&& HulkCombat.targets(player, target.getBoundingBox().inflate(0.1)).stream().anyMatch(e -> e == target);
	}

	/** True if {@code e} is currently held overhead by some Hulk (for the rider / AI checks). */
	public static boolean isHeld(Entity e) {
		return HELD.containsValue(e.getId());
	}

	/** Used by the HUD / animations through {@link HulkState.Combat#holding}; kept for tests. */
	static boolean holdingSomething(Player player) {
		return player instanceof ServerPlayer sp && holding(sp);
	}
}
