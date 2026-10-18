package com.projecthero.mod.miraculous.pegasus;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.pegasus.entity.HorseshoeEntity;
import com.projecthero.mod.miraculous.pegasus.entity.PegasusEntityTypes;
import com.projecthero.mod.miraculous.pegasus.entity.PegasusPortalEntity;
import com.projecthero.mod.miraculous.pegasus.network.PegasusVoyageMenuPayload;
import com.projecthero.mod.squad.Squad;
import com.projecthero.mod.squad.SquadManager;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.6: Pegasus's twelve moves (see {@code docs/MIRACULOUS_PEGASUS.md}) and the per-player sessions of the ones that
 * run over time (the Trample dash, the Portal Shield, Sky Gallop, the Voyage menu, the Linked Portals). Every public move
 * is called by {@link PegasusKit#handle} once its cooldown is ready, and by the gametests directly.
 *
 * <p>The sessions are static and keyed by player; they hold entity ids, never entities, and are dropped by
 * {@link PegasusKit#forget} / {@link PegasusKit#clearSessionState}. Every teleport is checked by {@link PegasusTeleport}.
 */
public final class PegasusMoves {
	/** A Voyage destination as the menu offered it (re-resolved when picked: a squadmate may have moved). */
	public record Target(int kind, String name, ResourceKey<Level> dim, Vec3 pos, @Nullable UUID mate) {
	}

	static final class Session {
		// Gallop Trample
		long trampleAt;
		Vec3 trampleFrom = Vec3.ZERO;
		Vec3 trampleDir = Vec3.ZERO;
		double trampleLength;
		final Set<Integer> trampled = new HashSet<>();
		// the Portal Shield
		int shieldId = -1;
		int shieldExitId = -1;
		int lastAttacker = -1;
		// Sky Gallop
		boolean sky;
		// the Voyage menu
		List<Target> menu = List.of();
		long menuUntil;
	}

	private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();
	/** Players being carried across worlds by their own Voyage right now (the world change must not close it). */
	public static final Set<UUID> CROSSING = ConcurrentHashMap.newKeySet();

	private PegasusMoves() {
	}

	/** Players whose client has had their waypoints this session. */
	private static final Set<UUID> SYNCED = ConcurrentHashMap.newKeySet();

	static void clearSessionState() {
		SESSIONS.clear();
		CROSSING.clear();
		SYNCED.clear();
	}

	static void forget(UUID id) {
		SESSIONS.remove(id);
		SYNCED.remove(id);
	}

	private static Session session(ServerPlayer p) {
		return SESSIONS.computeIfAbsent(p.getUUID(), k -> new Session());
	}

	private static long now(ServerPlayer p) {
		return p.level().getGameTime();
	}

	private static Vec3 flatLook(ServerPlayer p) {
		Vec3 l = p.getLookAngle();
		Vec3 f = new Vec3(l.x, 0, l.z);
		return f.lengthSqr() < 1.0e-4 ? Vec3.directionFromRotation(0f, p.getYRot()) : f.normalize();
	}

	private static void sound(ServerPlayer p, net.minecraft.sounds.SoundEvent s, float vol, float pitch) {
		p.level().playSound(null, p.getX(), p.getY(), p.getZ(), s, SoundSource.PLAYERS, vol, pitch);
	}

	private static void sound(ServerPlayer p, net.minecraft.core.Holder<net.minecraft.sounds.SoundEvent> s, float vol, float pitch) {
		p.level().playSound(null, p.getX(), p.getY(), p.getZ(), s, SoundSource.PLAYERS, vol, pitch);
	}

	/** Opens a portal of {@code kind}; null if the entity could not be made. */
	static @Nullable PegasusPortalEntity portal(ServerPlayer p, ServerLevel level, int kind, Vec3 center, float yaw, float pitch, float radius, int life) {
		PegasusPortalEntity e = PegasusEntityTypes.PORTAL.create(level);
		if (e == null) {
			return null;
		}
		e.setup(p, kind, center, yaw, pitch, radius, life);
		level.addFreshEntity(e);
		PegasusPortalEntity.ACTIVE.add(e);
		level.playSound(null, center.x, center.y, center.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.9f, 1.5f);
		level.playSound(null, center.x, center.y, center.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.35f, 1.8f);
		PegasusCombat.glow(level, center, 10, radius * 0.5);
		return e;
	}

	// ================================================================ R: Horseshoe Throw / Shift+R: Horseshoe Volley

	/** The spinning boomerang throw (refused while the Horseshoe is still out). */
	public static @Nullable HorseshoeEntity throwShoe(ServerPlayer p) {
		if (Miraculous.state(p).val(Pegasus.V_SHOE_OUT) != 0L) {
			Miraculous.say(p, "message.projecthero.pegasus.shoe_out", ChatFormatting.GOLD);
			return null;
		}
		HorseshoeEntity h = PegasusEntityTypes.HORSESHOE.create(p.serverLevel());
		if (h == null) {
			return null;
		}
		Vec3 look = p.getLookAngle();
		Vec3 from = p.getEyePosition().add(0, -0.25, 0).add(look.scale(0.6));
		LivingEntity lock = PegasusCombat.aimed(p, PegasusConfig.THROW_RANGE, PegasusConfig.THROW_ASSIST_DEGREES);
		Vec3 aim = lock != null ? lock.position().add(0, lock.getBbHeight() * 0.5, 0) : AbilityHelpers.aimPoint(p, PegasusConfig.THROW_RANGE);
		h.launch(p, from, aim, lock);
		p.serverLevel().addFreshEntity(h);
		Miraculous.setVal(p, Pegasus.V_SHOE_OUT, 1L);
		Miraculous.setAnim(p, Pegasus.ANIM_THROW);
		sound(p, SoundEvents.TRIDENT_THROW.value(), 0.8f, 1.6f);
		return h;
	}

	/** The Horseshoe is back in his hand; the throw's cooldown starts now. */
	public static void shoeCaught(ServerPlayer p) {
		if (!Miraculous.holds(p, Kwami.KAALKI)) {
			return;
		}
		Miraculous.setVal(p, Pegasus.V_SHOE_OUT, 0L);
		if (Miraculous.transformed(p)) {
			Miraculous.setAnim(p, Pegasus.ANIM_CATCH);
			Miraculous.setCooldown(p, PegasusConfig.THROW, PegasusConfig.THROW_COOLDOWN);
		}
		sound(p, SoundEvents.ARMOR_EQUIP_GOLD, 0.8f, 1.4f);
	}

	/** Three small golden horseshoes in a fan, {@link PegasusConfig#VOLLEY_DAMAGE} each. */
	public static List<HorseshoeEntity> volley(ServerPlayer p) {
		List<HorseshoeEntity> out = new ArrayList<>();
		Vec3 from = p.getEyePosition().add(0, -0.2, 0).add(p.getLookAngle().scale(0.6));
		int n = PegasusConfig.VOLLEY_COUNT;
		for (int i = 0; i < n; i++) {
			double off = (i - (n - 1) / 2.0) * PegasusConfig.VOLLEY_SPREAD_DEGREES;
			HorseshoeEntity h = PegasusEntityTypes.HORSESHOE.create(p.serverLevel());
			if (h == null) {
				continue;
			}
			h.launchSmall(p, from, Vec3.directionFromRotation(p.getXRot(), (float) (p.getYRot() + off)));
			p.serverLevel().addFreshEntity(h);
			out.add(h);
		}
		Miraculous.setAnim(p, Pegasus.ANIM_VOLLEY);
		Miraculous.setCooldown(p, PegasusConfig.VOLLEY, PegasusConfig.VOLLEY_COOLDOWN);
		PegasusCombat.glow(p.serverLevel(), from, 8, 0.3);
		sound(p, SoundEvents.TRIDENT_THROW.value(), 0.7f, 1.9f);
		sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 0.8f, 1.7f);
		return out;
	}

	// ================================================================ G: Hoof Kick / Shift+G: Gallop Trample

	/** A powerful buck: everything within {@link PegasusConfig#KICK_REACH} in front of him or behind him is kicked flying. */
	public static int kick(ServerPlayer p) {
		Miraculous.setAnim(p, Pegasus.ANIM_KICK);
		Miraculous.setCooldown(p, PegasusConfig.KICK, PegasusConfig.KICK_COOLDOWN);
		ServerLevel level = p.serverLevel();
		Vec3 f = flatLook(p);
		Vec3 c = p.position().add(0, 0.6, 0);
		int n = 0;
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, c, PegasusConfig.KICK_REACH + 1.0)) {
			Vec3 to = new Vec3(e.getX() - p.getX(), 0, e.getZ() - p.getZ());
			double d = Math.sqrt(AbilityHelpers.distanceSqToBox(e, c));
			if (d > PegasusConfig.KICK_REACH || (to.lengthSqr() > 0.04 && Math.abs(to.normalize().dot(f)) < 0.3)) {
				continue; // only in front or behind, not off to the side
			}
			if (PegasusCombat.strike(p, e, p.position(), PegasusConfig.KICK_DAMAGE, PegasusConfig.KICK_KNOCKBACK, PegasusConfig.KICK_LIFT)) {
				n++;
			}
		}
		for (int side = -1; side <= 1; side += 2) {
			Vec3 at = c.add(f.scale(side * 1.6));
			level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 1, 0, 0, 0, 0);
			level.sendParticles(PegasusCombat.GOLD, at.x, at.y, at.z, 8, 0.4, 0.3, 0.4, 0.0);
		}
		level.sendParticles(ParticleTypes.POOF, p.getX(), p.getY() + 0.1, p.getZ(), 8, 0.5, 0.05, 0.5, 0.02);
		sound(p, SoundEvents.HORSE_ANGRY, 0.8f, 1.3f);
		sound(p, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0f, 0.7f);
		return n;
	}

	/** A 12-block charge along the ground; everything along the path is trampled ({@link #tick}). */
	public static void trample(ServerPlayer p) {
		Session s = session(p);
		Vec3 dir = flatLook(p);
		Vec3 start = p.position();
		// as far along the ground as the body fits (a wall ends the charge early; a step of one block is fine)
		double len = 0.0;
		AABB bb = p.getBoundingBox();
		for (double d = 0.5; d <= PegasusConfig.TRAMPLE_DISTANCE; d += 0.5) {
			Vec3 off = dir.scale(d);
			if (!p.level().noCollision(p, bb.move(off)) && !p.level().noCollision(p, bb.move(off.add(0, 1.05, 0)))) {
				break;
			}
			len = d;
		}
		s.trampleAt = now(p);
		s.trampleFrom = start;
		s.trampleDir = dir;
		s.trampleLength = len;
		s.trampled.clear();
		Miraculous.setVal(p, Pegasus.V_TRAMPLE, now(p) + PegasusConfig.TRAMPLE_TICKS);
		Miraculous.setAnim(p, Pegasus.ANIM_TRAMPLE);
		Miraculous.setCooldown(p, PegasusConfig.TRAMPLE, PegasusConfig.TRAMPLE_COOLDOWN);
		sound(p, SoundEvents.HORSE_GALLOP, 1.0f, 1.1f);
		sound(p, SoundEvents.HORSE_BREATHE, 0.8f, 1.2f);
		trampleStep(p, s, 0);
	}

	private static void trampleStep(ServerPlayer p, Session s, long k) {
		double step = PegasusConfig.TRAMPLE_DISTANCE / PegasusConfig.TRAMPLE_TICKS;
		double a = Math.min(s.trampleLength, Math.max(0.0, (k - 1) * step));
		double b = Math.min(s.trampleLength, (k + 1) * step);
		Vec3 from = s.trampleFrom.add(s.trampleDir.scale(a));
		Vec3 to = s.trampleFrom.add(s.trampleDir.scale(b));
		PegasusCombat.alongPath(p, from, to, PegasusConfig.TRAMPLE_WIDTH, PegasusConfig.TRAMPLE_DAMAGE, PegasusConfig.TRAMPLE_KNOCKBACK,
				PegasusConfig.TRAMPLE_LIFT, s.trampled);
		if (k < PegasusConfig.TRAMPLE_TICKS) {
			AbilityHelpers.launchSelf(p, new Vec3(s.trampleDir.x * step, Math.min(0.0, p.getDeltaMovement().y), s.trampleDir.z * step));
		}
		ServerLevel level = p.serverLevel();
		level.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.1, p.getZ(), 3, 0.3, 0.05, 0.3, 0.01);
		level.sendParticles(PegasusCombat.GOLD, p.getX(), p.getY() + 0.2, p.getZ(), 4, 0.35, 0.1, 0.35, 0.0);
	}

	/** Test hook: everything the running (or last) Trample has hit. */
	public static Set<Integer> trampled(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		return s == null ? Set.of() : Set.copyOf(s.trampled);
	}

	// ================================================================ X: Portal Hop / Shift+X: Gallop

	/** Where Portal Hop would put him (a safe spot on the ground near what he looks at, 20 blocks at most), or null. */
	public static @Nullable Vec3 hopTarget(ServerPlayer p) {
		ServerLevel level = p.serverLevel();
		Vec3 look = p.getLookAngle();
		BlockHitResult hit = AbilityHelpers.raycastBlock(p, PegasusConfig.HOP_RANGE);
		Vec3 aim;
		if (hit.getType() == HitResult.Type.MISS) {
			aim = p.getEyePosition().add(look.scale(PegasusConfig.HOP_RANGE));
		} else if (hit.getDirection() == net.minecraft.core.Direction.UP) {
			aim = hit.getLocation().add(0, 0.01, 0);
		} else {
			// against a wall or a ceiling: step back off it
			aim = hit.getLocation().subtract(look.scale(0.7));
			if (hit.getDirection() == net.minecraft.core.Direction.DOWN) {
				aim = aim.subtract(0, p.getBbHeight(), 0);
			}
		}
		return PegasusTeleport.landing(level, p, aim, 2);
	}

	/** A portal opens before him and he steps out of another where he looked. False: no safe spot there. */
	public static boolean hop(ServerPlayer p) {
		ServerLevel level = p.serverLevel();
		Vec3 to = hopTarget(p);
		if (to == null) {
			Miraculous.say(p, "message.projecthero.pegasus.no_landing", ChatFormatting.GOLD);
			return false;
		}
		if (to.distanceTo(p.position()) < 2.0) {
			Miraculous.say(p, "message.projecthero.pegasus.too_close", ChatFormatting.GOLD);
			return false;
		}
		Vec3 f = flatLook(p);
		float yaw = p.getYRot();
		portal(p, level, PegasusPortalEntity.HOP, p.position().add(0, 1.05, 0).add(f.scale(0.5)), yaw + 180f, 0f, PegasusConfig.HOP_RADIUS,
				PegasusConfig.HOP_PORTAL_TICKS);
		portal(p, level, PegasusPortalEntity.HOP, to.add(0, 1.05, 0).subtract(f.scale(0.4)), yaw, 0f, PegasusConfig.HOP_RADIUS, PegasusConfig.HOP_PORTAL_TICKS);
		PegasusTeleport.move(p, level, to, yaw, p.getXRot(), f.scale(0.2));
		Miraculous.setAnim(p, Pegasus.ANIM_HOP);
		Miraculous.setCooldown(p, PegasusConfig.HOP, PegasusConfig.HOP_COOLDOWN);
		level.playSound(null, to.x, to.y, to.z, SoundEvents.HORSE_GALLOP, SoundSource.PLAYERS, 0.8f, 1.3f);
		return true;
	}

	/** Shift+X: Gallop on (Speed III, golden hoof prints) or off. */
	public static boolean gallop(ServerPlayer p) {
		boolean on = Miraculous.state(p).val(Pegasus.V_GALLOP) == 0L;
		Miraculous.setVal(p, Pegasus.V_GALLOP, on ? 1L : 0L);
		Miraculous.setCooldown(p, PegasusConfig.GALLOP, PegasusConfig.GALLOP_COOLDOWN);
		if (on) {
			refreshGallop(p);
			Miraculous.setAnim(p, Pegasus.ANIM_GALLOP);
			sound(p, SoundEvents.HORSE_AMBIENT, 0.8f, 1.3f);
			sound(p, SoundEvents.HORSE_GALLOP, 0.8f, 1.2f);
			PegasusCombat.glow(p.serverLevel(), p.position().add(0, 0.2, 0), 12, 0.4);
		} else {
			stopGallop(p);
			sound(p, SoundEvents.HORSE_BREATHE, 0.8f, 1.1f);
		}
		return on;
	}

	private static void refreshGallop(ServerPlayer p) {
		MobEffectInstance cur = p.getEffect(MobEffects.MOVEMENT_SPEED);
		if (cur == null || (cur.getAmplifier() <= PegasusConfig.GALLOP_SPEED_AMPLIFIER && cur.getDuration() < 30)) {
			p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, PegasusConfig.GALLOP_SPEED_AMPLIFIER, true, false, true));
		}
	}

	private static void stopGallop(ServerPlayer p) {
		MobEffectInstance cur = p.getEffect(MobEffects.MOVEMENT_SPEED);
		if (cur != null && cur.getAmplifier() == PegasusConfig.GALLOP_SPEED_AMPLIFIER && cur.getDuration() <= 40 && cur.isAmbient()) {
			p.removeEffect(MobEffects.MOVEMENT_SPEED);
		}
	}

	// ================================================================ Z: Portal Trap / Shift+Z: Voyage

	/**
	 * A portal opens under the creature he aims at; it drops out of another {@link PegasusConfig#TRAP_HEIGHT} blocks up
	 * (lower under a roof, refused under less than {@link PegasusConfig#TRAP_MIN_HEIGHT}) and falls -- fall damage applies.
	 * Never a boss. Returns the trapped creature, or null.
	 */
	public static @Nullable LivingEntity trap(ServerPlayer p) {
		LivingEntity t = PegasusCombat.aimed(p, PegasusConfig.TRAP_RANGE, 5.0);
		if (t == null) {
			Miraculous.say(p, "message.projecthero.pegasus.no_target", ChatFormatting.GOLD);
			return null;
		}
		if (PegasusCombat.isBoss(t)) {
			Miraculous.say(p, "message.projecthero.pegasus.boss", ChatFormatting.GOLD, t.getDisplayName());
			return null;
		}
		ServerLevel level = p.serverLevel();
		Vec3 top = PegasusTeleport.openAbove(level, t, PegasusConfig.TRAP_HEIGHT, PegasusConfig.TRAP_MIN_HEIGHT);
		if (top == null) {
			Miraculous.say(p, "message.projecthero.pegasus.no_room_above", ChatFormatting.GOLD);
			return null;
		}
		float r = Math.max(PegasusConfig.TRAP_RADIUS, t.getBbWidth() * 0.9f);
		Vec3 below = t.position();
		portal(p, level, PegasusPortalEntity.TRAP_LOW, below.add(0, 0.06, 0), t.getYRot(), -90f, r, PegasusConfig.TRAP_PORTAL_TICKS);
		portal(p, level, PegasusPortalEntity.TRAP_HIGH, top.add(0, t.getBbHeight() + 0.15, 0), t.getYRot(), 90f, r, PegasusConfig.TRAP_PORTAL_TICKS);
		level.sendParticles(PegasusCombat.GOLD_BIG, below.x, below.y + 0.2, below.z, 16, 0.4, 0.1, 0.4, 0.0);
		PegasusTeleport.move(t, level, top, t.getYRot(), t.getXRot(), new Vec3(0, -0.1, 0));
		if (t instanceof net.minecraft.world.entity.Mob m) {
			m.getNavigation().stop();
		}
		Miraculous.setAnim(p, Pegasus.ANIM_TRAP);
		Miraculous.setCooldown(p, PegasusConfig.TRAP, PegasusConfig.TRAP_COOLDOWN);
		level.playSound(null, top.x, top.y, top.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 1.5f);
		return t;
	}

	/**
	 * Shift+Z: the destination menu -- your bed / spawn point, the world spawn, every squadmate online, your waypoints --
	 * sent to your client (it opens the Voyage screen; the pick comes back to {@link #chooseVoyage}). Returns the list
	 * (empty: refused).
	 */
	public static List<Target> voyage(ServerPlayer p) {
		if (Miraculous.state(p).val(Pegasus.V_VOYAGE) > now(p)) {
			Miraculous.say(p, "message.projecthero.pegasus.voyage_open", ChatFormatting.GOLD);
			return List.of();
		}
		List<Target> list = destinations(p);
		Session s = session(p);
		s.menu = list;
		s.menuUntil = now(p) + PegasusConfig.VOYAGE_MENU_TICKS;
		List<PegasusVoyageMenuPayload.Dest> out = new ArrayList<>();
		for (Target t : list) {
			out.add(new PegasusVoyageMenuPayload.Dest(t.kind(), t.name(), t.dim().location().toString(), Mth.floor(t.pos().x), Mth.floor(t.pos().y),
					Mth.floor(t.pos().z)));
		}
		if (ServerPlayNetworking.canSend(p, PegasusVoyageMenuPayload.TYPE)) {
			ServerPlayNetworking.send(p, new PegasusVoyageMenuPayload(out, p.level().dimension().location().toString()));
		}
		sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 0.8f, 1.2f);
		return list;
	}

	/** Every place the Voyage can open to for {@code p}, in menu order. */
	public static List<Target> destinations(ServerPlayer p) {
		List<Target> list = new ArrayList<>();
		BlockPos bed = p.getRespawnPosition();
		if (bed != null && p.server.getLevel(p.getRespawnDimension()) != null) {
			list.add(new Target(PegasusVoyageMenuPayload.BED, "", p.getRespawnDimension(), Vec3.atBottomCenterOf(bed), null));
		}
		ServerLevel overworld = p.server.overworld();
		list.add(new Target(PegasusVoyageMenuPayload.WORLD_SPAWN, "", overworld.dimension(), Vec3.atBottomCenterOf(overworld.getSharedSpawnPos()), null));
		Squad squad = SquadManager.get(p.server).squadOf(p.getUUID());
		if (squad != null) {
			for (UUID id : squad.members()) {
				ServerPlayer mate = p.server.getPlayerList().getPlayer(id);
				if (mate != null && mate != p && !mate.isSpectator()) {
					list.add(new Target(PegasusVoyageMenuPayload.SQUADMATE, mate.getGameProfile().getName(), mate.level().dimension(), mate.position(), id));
				}
			}
		}
		List<PegasusWaypoints.Waypoint> wps = PegasusWaypoints.get(p.server).of(p.getUUID());
		for (int i = 0; i < wps.size(); i++) {
			PegasusWaypoints.Waypoint w = wps.get(i);
			ResourceKey<Level> dim = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
					net.minecraft.resources.ResourceLocation.parse(w.dim()));
			if (p.server.getLevel(dim) != null) {
				list.add(new Target(PegasusVoyageMenuPayload.WAYPOINT, Integer.toString(i + 1), dim, w.pos(), null));
			}
		}
		return list;
	}

	/** The menu's answer. -1 closes it. True: the Voyage opened. */
	public static boolean chooseVoyage(ServerPlayer p, int index) {
		Session s = SESSIONS.get(p.getUUID());
		if (s == null || s.menuUntil < now(p) || index < 0 || index >= s.menu.size()) {
			if (s != null) {
				s.menu = List.of();
			}
			return false;
		}
		Target t = s.menu.get(index);
		s.menu = List.of();
		if (!Miraculous.canAct(p) || !Miraculous.ready(p, PegasusConfig.VOYAGE)) {
			return false;
		}
		ServerLevel dest = p.server.getLevel(t.dim());
		Vec3 pos = t.pos();
		if (t.kind() == PegasusVoyageMenuPayload.SQUADMATE && t.mate() != null) {
			ServerPlayer mate = p.server.getPlayerList().getPlayer(t.mate());
			if (mate == null) {
				Miraculous.say(p, "message.projecthero.pegasus.mate_gone", ChatFormatting.GOLD, t.name());
				return false;
			}
			dest = mate.serverLevel();
			// a couple of steps in front of them
			pos = mate.position().add(Vec3.directionFromRotation(0f, mate.getYRot()).scale(2.0));
		}
		if (dest == null) {
			return false;
		}
		return openVoyage(p, dest, pos, label(t));
	}

	private static Component label(Target t) {
		return switch (t.kind()) {
			case PegasusVoyageMenuPayload.BED -> Component.translatable("screen.projecthero.pegasus_voyage.bed");
			case PegasusVoyageMenuPayload.WORLD_SPAWN -> Component.translatable("screen.projecthero.pegasus_voyage.world_spawn");
			case PegasusVoyageMenuPayload.SQUADMATE -> Component.literal(t.name());
			default -> Component.translatable("screen.projecthero.pegasus_voyage.waypoint", t.name());
		};
	}

	/**
	 * The Voyage itself: a big portal opens {@link PegasusConfig#VOYAGE_DISTANCE} ahead of him, its twin at a safe spot by
	 * {@code target} in {@code dest} (its chunk loaded and held while it is open), both open for
	 * {@link PegasusConfig#VOYAGE_TICKS} and both ways; anyone can walk through (across worlds: players only). The
	 * Miraculous countdown starts. False (no cooldown) if there is no safe spot at either end.
	 */
	public static boolean openVoyage(ServerPlayer p, ServerLevel dest, Vec3 target, Component name) {
		ServerLevel here = p.serverLevel();
		Vec3 f = flatLook(p);
		Vec3 src = PegasusTeleport.landing(here, p, p.position().add(f.scale(PegasusConfig.VOYAGE_DISTANCE)), 1);
		if (src == null) {
			Miraculous.say(p, "message.projecthero.pegasus.no_room", ChatFormatting.GOLD);
			return false;
		}
		BlockPos tp = BlockPos.containing(target);
		if (!dest.getWorldBorder().isWithinBounds(tp)) {
			Miraculous.say(p, "message.projecthero.pegasus.voyage_unsafe", ChatFormatting.GOLD, name);
			return false;
		}
		dest.getChunk(tp); // load (or generate) the far end before checking it
		Vec3 arrive = PegasusTeleport.landing(dest, p, target.add(0, 0.05, 0), 3);
		if (arrive == null) {
			Miraculous.say(p, "message.projecthero.pegasus.voyage_unsafe", ChatFormatting.GOLD, name);
			return false;
		}
		float yaw = p.getYRot();
		float r = PegasusConfig.VOYAGE_RADIUS;
		boolean cross = dest != here;
		int life = PegasusConfig.VOYAGE_TICKS;
		Vec3 back = PegasusTeleport.landing(here, p, src.subtract(f.scale(1.4)), 1);
		if (back == null) {
			back = p.position();
		}
		PegasusPortalEntity a = portal(p, here, PegasusPortalEntity.VOYAGE, src.add(0, r * 0.95, 0), yaw + 180f, 0f, r, life);
		PegasusPortalEntity b = portal(p, dest, PegasusPortalEntity.VOYAGE, arrive.add(0, r * 0.95, 0).subtract(Vec3.directionFromRotation(0f, yaw).scale(1.3)),
				yaw, 0f, r, life);
		if (a == null || b == null) {
			if (a != null) {
				a.discard();
			}
			if (b != null) {
				b.discard();
			}
			return false;
		}
		a.setExit(dest.dimension(), arrive, yaw, cross);
		b.setExit(here.dimension(), back, yaw + 180f, cross);
		a.holdChunk();
		b.holdChunk();
		Miraculous.setVal(p, Pegasus.V_VOYAGE, now(p) + life);
		Miraculous.setAnim(p, Pegasus.ANIM_VOYAGE);
		Miraculous.setCooldown(p, PegasusConfig.VOYAGE, PegasusConfig.VOYAGE_COOLDOWN);
		Miraculous.startCountdown(p);
		here.sendParticles(ParticleTypes.FLASH, src.x, src.y + r, src.z, 1, 0, 0, 0, 0);
		sound(p, SoundEvents.HORSE_AMBIENT, 1.0f, 1.2f);
		sound(p, SoundEvents.BEACON_POWER_SELECT, 0.8f, 1.5f);
		p.displayClientMessage(Component.translatable("message.projecthero.pegasus.voyage_opened", name, PegasusConfig.VOYAGE_TICKS / 20)
				.withStyle(ChatFormatting.GOLD), true);
		return true;
	}

	/** Called by a Voyage portal just after it carried its own opener across worlds (the world change cleared his values). */
	public static void crossed(ServerPlayer p, long voyageUntil) {
		if (Miraculous.transformedAs(p, Kwami.KAALKI) && voyageUntil > now(p)) {
			Miraculous.setVal(p, Pegasus.V_VOYAGE, voyageUntil);
		}
	}

	// ================================================================ V: Portal Shield / Shift+V: Sky Gallop

	/** A portal before him for {@link PegasusConfig#SHIELD_TICKS} swallows projectiles; V again closes it. */
	public static boolean shield(ServerPlayer p) {
		Session s = session(p);
		if (Miraculous.state(p).val(Pegasus.V_SHIELD) > now(p)) {
			endShield(p, s);
			return false;
		}
		ServerLevel level = p.serverLevel();
		PegasusPortalEntity a = portal(p, level, PegasusPortalEntity.SHIELD, shieldSpot(p), p.getYRot(), p.getXRot(), PegasusConfig.SHIELD_RADIUS,
				PegasusConfig.SHIELD_TICKS);
		PegasusPortalEntity b = portal(p, level, PegasusPortalEntity.SHIELD_EXIT, exitSpot(p), p.getYRot(), 0f, PegasusConfig.SHIELD_RADIUS * 0.7f,
				PegasusConfig.SHIELD_TICKS);
		s.shieldId = a == null ? -1 : a.getId();
		s.shieldExitId = b == null ? -1 : b.getId();
		s.lastAttacker = -1;
		Miraculous.setVal(p, Pegasus.V_SHIELD, now(p) + PegasusConfig.SHIELD_TICKS);
		Miraculous.setAnim(p, Pegasus.ANIM_SHIELD);
		sound(p, SoundEvents.SHIELD_BLOCK, 0.6f, 1.6f);
		return true;
	}

	private static Vec3 shieldSpot(ServerPlayer p) {
		return p.getEyePosition().add(0, -0.3, 0).add(p.getLookAngle().scale(PegasusConfig.SHIELD_DISTANCE));
	}

	private static Vec3 exitSpot(ServerPlayer p) {
		Vec3 f = flatLook(p);
		return p.position().add(new Vec3(-f.z, 0, f.x).scale(1.7)).add(0, 2.3, 0);
	}

	private static void endShield(ServerPlayer p, Session s) {
		ServerLevel level = p.serverLevel();
		for (int id : new int[] { s.shieldId, s.shieldExitId }) {
			if (id >= 0 && level.getEntity(id) instanceof PegasusPortalEntity e) {
				e.closeSoon();
			}
		}
		s.shieldId = -1;
		s.shieldExitId = -1;
		Miraculous.setVal(p, Pegasus.V_SHIELD, 0L);
		Miraculous.setCooldown(p, PegasusConfig.SHIELD, PegasusConfig.SHIELD_COOLDOWN);
	}

	/** Remembers who is attacking him while the shield is up (the exit portal turns to face them). */
	static void noteAttacker(ServerPlayer p, DamageSource source) {
		Session s = SESSIONS.get(p.getUUID());
		if (s != null && s.shieldId >= 0 && source.getEntity() instanceof LivingEntity le && le != p) {
			s.lastAttacker = le.getId();
		}
	}

	/** True while the shield stands between him and {@code from} (a projectile hit from the front is swallowed). */
	static boolean shieldCovers(ServerPlayer p, Vec3 from) {
		if (Miraculous.state(p).val(Pegasus.V_SHIELD) <= now(p)) {
			return false;
		}
		Vec3 to = from.subtract(p.getEyePosition());
		return to.lengthSqr() > 1.0e-4 && to.normalize().dot(p.getLookAngle()) > 0.35;
	}

	/** Every projectile about to pass the shield's disc this tick goes in, and out of the exit back at whoever shot it. */
	private static int shieldTick(ServerPlayer p, Session s) {
		ServerLevel level = p.serverLevel();
		if (!(level.getEntity(s.shieldId) instanceof PegasusPortalEntity shield) || !(level.getEntity(s.shieldExitId) instanceof PegasusPortalEntity exit)) {
			return 0;
		}
		// the shield hangs before his eyes, wherever he looks; the exit floats at his shoulder, turned to the attacker
		Vec3 spot = shieldSpot(p);
		shield.setPos(spot.x, spot.y, spot.z);
		shield.face(p.getYRot(), p.getXRot());
		Vec3 ex = exitSpot(p);
		exit.setPos(ex.x, ex.y, ex.z);
		Entity attacker = s.lastAttacker >= 0 ? level.getEntity(s.lastAttacker) : null;
		if (attacker != null && attacker.isAlive()) {
			Vec3 d = attacker.getEyePosition().subtract(ex);
			exit.face((float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f, (float) -(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG));
		} else {
			exit.face(p.getYRot(), 0f);
		}
		Vec3 c = shield.position();
		Vec3 n = shield.normal();
		double r = shield.radius() * 1.15;
		int n2 = 0;
		for (Projectile pr : level.getEntitiesOfClass(Projectile.class, new AABB(c, c).inflate(6.0), pr -> pr.getOwner() != p && pr.isAlive())) {
			Vec3 pos = pr.position();
			Vec3 v = pr.getDeltaMovement();
			double vn = v.dot(n);
			if (vn >= -1.0e-3) {
				continue; // not coming at him
			}
			double d0 = pos.subtract(c).dot(n);
			if (d0 < -0.4 || d0 + vn > 0.5 && d0 > 1.0) {
				continue; // behind the shield already, or not reaching it this tick
			}
			double t = Mth.clamp(d0 / -vn, 0.0, 1.0);
			Vec3 at = pos.add(v.scale(t));
			Vec3 lateral = at.subtract(c).subtract(n.scale(at.subtract(c).dot(n)));
			if (lateral.length() > r) {
				continue;
			}
			Entity shooter = pr.getOwner();
			if (shooter instanceof LivingEntity le && le != p) {
				s.lastAttacker = le.getId();
			}
			spit(p, pr, exit, shooter != null && shooter.isAlive() ? shooter : attacker);
			level.sendParticles(PegasusCombat.GOLD_BIG, at.x, at.y, at.z, 8, 0.15, 0.15, 0.15, 0.0);
			n2++;
		}
		return n2;
	}

	/** Out of the exit portal at {@code target} (or straight out of it), as his own projectile now. */
	private static void spit(ServerPlayer p, Projectile pr, PegasusPortalEntity exit, @Nullable Entity target) {
		Vec3 from = exit.position();
		Vec3 dir = target != null ? target.getBoundingBox().getCenter().subtract(from) : exit.normal();
		if (dir.lengthSqr() < 1.0e-4) {
			dir = exit.normal();
		}
		dir = dir.normalize();
		double speed = Math.max(pr.getDeltaMovement().length(), PegasusConfig.SHIELD_RETURN_SPEED);
		Vec3 start = from.add(dir.scale(0.6));
		pr.setOwner(p);
		pr.setPos(start.x, start.y, start.z);
		pr.setDeltaMovement(dir.scale(speed));
		pr.setYRot((float) (Mth.atan2(dir.x, dir.z) * Mth.RAD_TO_DEG));
		pr.setXRot((float) (Mth.atan2(dir.y, dir.horizontalDistance()) * Mth.RAD_TO_DEG));
		pr.yRotO = pr.getYRot();
		pr.xRotO = pr.getXRot();
		pr.hurtMarked = true;
		p.serverLevel().sendParticles(PegasusCombat.GOLD_BIG, start.x, start.y, start.z, 8, 0.15, 0.15, 0.15, 0.0);
		p.level().playSound(null, start.x, start.y, start.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.9f, 1.8f);
	}

	/** Shift+V: running on the air for {@link PegasusConfig#SKY_TICKS} (the owning client holds him up; see PegasusSkyClient). */
	public static void sky(ServerPlayer p) {
		Session s = session(p);
		s.sky = true;
		Miraculous.setVal(p, Pegasus.V_SKY, now(p) + PegasusConfig.SKY_TICKS);
		Miraculous.setAnim(p, Pegasus.ANIM_SKY);
		floatTolerance(p, true);
		if (p.onGround()) {
			AbilityHelpers.launchSelf(p, p.getDeltaMovement().add(0, 0.5, 0));
		}
		sound(p, SoundEvents.HORSE_AMBIENT, 0.8f, 1.5f);
		sound(p, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.8f, 1.6f);
		PegasusCombat.glow(p.serverLevel(), p.position(), 14, 0.4);
	}

	private static void endSky(ServerPlayer p, Session s, boolean cooldown) {
		s.sky = false;
		Miraculous.setVal(p, Pegasus.V_SKY, 0L);
		floatTolerance(p, false);
		p.resetFallDistance();
		if (cooldown) {
			Miraculous.setCooldown(p, PegasusConfig.SKY, PegasusConfig.SKY_COOLDOWN);
		}
	}

	/**
	 * Vanilla kicks a player who floats for 4 s with no downward motion ("flying is not enabled") -- running on air looks
	 * just like that. The Spider-Man / Ladybug fix: {@code mayfly} for the duration, {@code flying} forced off, handed
	 * back after unless another power still flies him.
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
		if (p.getAbilities().mayfly && !com.projecthero.mod.hero.power.HeroFlight.isFlying(p) && !com.projecthero.mod.power.ThorPowers.isFlying(p)
				&& !p.getAttachedOrElse(ModAttachments.IRON_MAN_FLYING, false) && !p.getAttachedOrElse(ModAttachments.REPULSOR_BOOTS_FLYING, false)) {
			p.getAbilities().mayfly = false;
			p.getAbilities().flying = false;
			p.onUpdateAbilities();
		}
	}

	// ================================================================ C: Mark Waypoint / Shift+C: Linked Portals

	/** Saves where he stands as a waypoint (the oldest of three replaced). Returns its slot. */
	public static int markWaypoint(ServerPlayer p) {
		PegasusWaypoints w = PegasusWaypoints.get(p.server);
		Vec3 at = p.position();
		int slot = w.add(p.getUUID(), new PegasusWaypoints.Waypoint(p.level().dimension().location().toString(), at.x, at.y, at.z, p.getYRot(),
				p.server.overworld().getGameTime()));
		PegasusWaypoints.sync(p);
		Miraculous.setAnim(p, Pegasus.ANIM_WAYPOINT);
		Miraculous.setCooldown(p, PegasusConfig.WAYPOINT, PegasusConfig.WAYPOINT_COOLDOWN);
		p.displayClientMessage(Component.translatable("message.projecthero.pegasus.waypoint_saved", slot, Mth.floor(at.x), Mth.floor(at.y), Mth.floor(at.z))
				.withStyle(ChatFormatting.GOLD), true);
		ServerLevel level = p.serverLevel();
		for (int i = 0; i < 12; i++) {
			double a = Math.PI * 2 * i / 12;
			level.sendParticles(PegasusCombat.GOLD, at.x + Math.cos(a) * 0.8, at.y + 0.1, at.z + Math.sin(a) * 0.8, 1, 0, 0, 0, 0);
		}
		sound(p, SoundEvents.ANVIL_LAND, 0.3f, 2.0f);
		sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 0.8f, 1.4f);
		return slot;
	}

	/**
	 * Shift+C: portal A where he stands, portal B where he looks (up to {@link PegasusConfig#LINK_RANGE}), both open for
	 * {@link PegasusConfig#LINK_TICKS}; anyone -- players, mobs, items -- passing through one comes out of the other.
	 * Returns [A, B], or null (no safe spot there).
	 */
	public static PegasusPortalEntity @Nullable [] link(ServerPlayer p) {
		ServerLevel level = p.serverLevel();
		Vec3 f = flatLook(p);
		float yaw = (float) (Mth.atan2(f.z, f.x) * Mth.RAD_TO_DEG) - 90f;
		Vec3 aim = AbilityHelpers.aimPoint(p, PegasusConfig.LINK_RANGE);
		BlockHitResult hit = AbilityHelpers.raycastBlock(p, PegasusConfig.LINK_RANGE);
		if (hit.getType() != HitResult.Type.MISS && hit.getDirection() != net.minecraft.core.Direction.UP) {
			aim = hit.getLocation().subtract(p.getLookAngle().scale(0.7));
		}
		Vec3 bFeet = PegasusTeleport.landing(level, p, aim, 2);
		if (bFeet == null) {
			Miraculous.say(p, "message.projecthero.pegasus.no_landing", ChatFormatting.GOLD);
			return null;
		}
		if (bFeet.distanceTo(p.position()) < 3.0) {
			Miraculous.say(p, "message.projecthero.pegasus.too_close", ChatFormatting.GOLD);
			return null;
		}
		Vec3 aFeet = p.position();
		float r = PegasusConfig.LINK_RADIUS;
		// out of each on the far side (along his look), on the ground there
		Vec3 outOfB = PegasusTeleport.landing(level, p, bFeet.add(f.scale(1.2)), 1);
		Vec3 outOfA = PegasusTeleport.landing(level, p, aFeet.add(f.scale(1.2)), 1);
		if (outOfB == null) {
			outOfB = bFeet;
		}
		if (outOfA == null) {
			outOfA = aFeet;
		}
		int life = PegasusConfig.LINK_TICKS;
		PegasusPortalEntity a = portal(p, level, PegasusPortalEntity.LINK, aFeet.add(0, r * 0.95, 0), yaw, 0f, r, life);
		PegasusPortalEntity b = portal(p, level, PegasusPortalEntity.LINK, bFeet.add(0, r * 0.95, 0), yaw, 0f, r, life);
		if (a == null || b == null) {
			return null;
		}
		a.setExit(level.dimension(), outOfB, yaw, false);
		b.setExit(level.dimension(), outOfA, yaw, false);
		// he is standing in A: not sent off at once
		PegasusPortalEntity.PASSED.put(p.getUUID(), p.server.getTickCount() + PegasusConfig.PASS_COOLDOWN);
		Miraculous.setVal(p, Pegasus.V_LINK, now(p) + life);
		Miraculous.setAnim(p, Pegasus.ANIM_LINK);
		sound(p, SoundEvents.HORSE_AMBIENT, 0.6f, 1.4f);
		return new PegasusPortalEntity[] { a, b };
	}

	// ================================================================ the tick

	static void tick(ServerPlayer p) {
		long now = now(p);
		var st = Miraculous.state(p);
		if (SYNCED.add(p.getUUID())) {
			PegasusWaypoints.sync(p); // (a jewel just picked up again: his old waypoints come back)
		}
		Session s = SESSIONS.get(p.getUUID());
		// Gallop: Speed III while it is on
		if (st.val(Pegasus.V_GALLOP) != 0L) {
			if (p.tickCount % 10 == 0) {
				refreshGallop(p);
			}
			if (p.isSprinting() && p.onGround() && p.tickCount % 3 == 0) {
				p.serverLevel().sendParticles(PegasusCombat.GOLD, p.getX(), p.getY() + 0.05, p.getZ(), 2, 0.2, 0.0, 0.2, 0.0);
			}
		}
		// the Linked Portals / the Voyage closing: their cooldown / timer
		long link = st.val(Pegasus.V_LINK);
		if (link != 0L && now >= link) {
			Miraculous.setVal(p, Pegasus.V_LINK, 0L);
			Miraculous.setCooldown(p, PegasusConfig.LINK, PegasusConfig.LINK_COOLDOWN);
		}
		long voyage = st.val(Pegasus.V_VOYAGE);
		if (voyage != 0L && now >= voyage) {
			Miraculous.setVal(p, Pegasus.V_VOYAGE, 0L);
		}
		if (s == null) {
			return;
		}
		// Gallop Trample
		if (s.trampleAt > 0) {
			long k = now - s.trampleAt;
			if (k > PegasusConfig.TRAMPLE_TICKS) {
				s.trampleAt = 0;
				Miraculous.setVal(p, Pegasus.V_TRAMPLE, 0L);
				AbilityHelpers.launchSelf(p, s.trampleDir.scale(0.3));
			} else if (k > 0) {
				trampleStep(p, s, k);
			}
		}
		// the Portal Shield
		if (s.shieldId >= 0) {
			if (st.val(Pegasus.V_SHIELD) <= now) {
				endShield(p, s);
			} else {
				shieldTick(p, s);
			}
		}
		// Sky Gallop
		if (s.sky) {
			if (st.val(Pegasus.V_SKY) <= now) {
				endSky(p, s, true);
			} else {
				p.resetFallDistance();
				floatTolerance(p, true);
			}
		}
		// the Voyage menu lapses
		if (!s.menu.isEmpty() && s.menuUntil < now) {
			s.menu = List.of();
		}
	}

	/** Test hook: run the Portal Shield's projectile check now. */
	public static int shieldTickForTest(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		return s == null ? 0 : shieldTick(p, s);
	}

	/** Detransformation / logout / ServerStateReset: everything of his ends and goes. */
	static void endAll(ServerPlayer p) {
		boolean crossing = CROSSING.contains(p.getUUID());
		for (PegasusPortalEntity e : List.copyOf(PegasusPortalEntity.ACTIVE)) {
			if (p.getUUID().equals(e.ownerId()) && !(crossing && e.kind() == PegasusPortalEntity.VOYAGE)) {
				e.discard();
			}
		}
		ServerLevel level = p.serverLevel();
		for (HorseshoeEntity h : level.getEntitiesOfClass(HorseshoeEntity.class, p.getBoundingBox().inflate(128.0), h -> h.ownerEntityId() == p.getId())) {
			h.discard();
		}
		stopGallop(p);
		Session s = SESSIONS.remove(p.getUUID());
		if (s != null && s.sky) {
			floatTolerance(p, false);
		}
	}
}
