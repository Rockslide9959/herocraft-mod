package com.projecthero.mod.stand;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.hero.PressInput;
import com.projecthero.mod.stand.data.StandState;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Star Platinum's moves (v0.17.2) -- the first Stand. Key map in {@link StandAbilityManager}; numbers in
 * {@link StandConfig}; every hit through {@link StandCombat#hit} (so a hit inside a time stop is queued). A move pressed
 * while the Stand is away summons it first. Every Stand sound / particle goes to Stand users only.
 */
public final class StarPlatinum {
	/** Something Star Platinum threw: who threw it, how long it is tracked, how hard it hits. */
	private record Thrown(UUID owner, long until, float damage) {
	}

	private static final Map<UUID, Thrown> THROWN = new ConcurrentHashMap<>();
	/** Super Inhale: who has been pulled in (left winded at the end). */
	private static final Map<UUID, Set<Integer>> INHALED = new ConcurrentHashMap<>();

	private StarPlatinum() {
	}

	static void clearSessionState() {
		THROWN.clear();
		INHALED.clear();
	}

	// ---------------------------------------------------------------- geometry

	public static Vec3 forward(Player p) {
		float r = p.getYRot() * Mth.DEG_TO_RAD;
		return new Vec3(-Mth.sin(r), 0, Mth.cos(r));
	}

	public static Vec3 rightOf(Vec3 fwd) {
		return new Vec3(-fwd.z, 0, fwd.x);
	}

	/** Where the Stand floats at rest: behind the right shoulder (the model's feet). */
	public static Vec3 restPoint(Player p) {
		Vec3 f = forward(p);
		return p.position().subtract(f.scale(0.5)).add(rightOf(f).scale(0.75)).add(0, 0.35, 0);
	}

	/** Where it stands when it fights: in front. */
	public static Vec3 frontPoint(Player p) {
		return p.position().add(forward(p).scale(1.0));
	}

	private static Vec3 flatLook(ServerPlayer p) {
		Vec3 l = p.getLookAngle();
		Vec3 f = new Vec3(l.x, 0, l.z);
		return f.lengthSqr() < 1.0e-4 ? forward(p) : f.normalize();
	}

	/** The target under the crosshair within {@code range} (a little forgiving: else the nearest within 20 degrees). */
	static LivingEntity aimed(ServerPlayer p, double range) {
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		Vec3 end = eye.add(look.scale(range));
		BlockHitResult bh = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		double maxDist = bh.getType() == HitResult.Type.MISS ? range : bh.getLocation().distanceTo(eye);
		end = eye.add(look.scale(maxDist));
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		LivingEntity cone = null;
		double coneD = Double.MAX_VALUE;
		for (Entity e : p.level().getEntities(p, p.getBoundingBox().expandTowards(look.scale(range)).inflate(1.5))) {
			if (!StandCombat.isTarget(p, e)) {
				continue;
			}
			AABB bb = e.getBoundingBox().inflate(0.3);
			var hit = bb.clip(eye, end);
			if (hit.isPresent() || bb.contains(eye)) {
				double d = hit.map(eye::distanceTo).orElse(0.0);
				if (d < bestD) {
					bestD = d;
					best = (LivingEntity) e;
				}
				continue;
			}
			Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
			double d = to.length();
			if (d <= maxDist + e.getBbWidth() * 0.5 && to.normalize().dot(look) > 0.94 && d < coneD) {
				coneD = d;
				cone = (LivingEntity) e;
			}
		}
		return best != null ? best : cone;
	}

	/** Every target within Star Platinum's reach in a cone along {@code dir} (flat), up to a body's height off. */
	static List<LivingEntity> inFront(ServerPlayer p, Vec3 dir, double reach, double cos) {
		List<LivingEntity> out = new ArrayList<>();
		Vec3 base = p.position().add(0, p.getBbHeight() * 0.5, 0);
		for (Entity e : p.level().getEntities(p, p.getBoundingBox().inflate(reach + 1.0))) {
			if (!StandCombat.isTarget(p, e)) {
				continue;
			}
			Vec3 to = e.getBoundingBox().getCenter().subtract(base);
			if (Math.abs(to.y) > 2.2) {
				continue;
			}
			Vec3 flat = new Vec3(to.x, 0, to.z);
			double d = flat.length();
			if (d > reach + e.getBbWidth() * 0.5) {
				continue;
			}
			if (d < 0.6 || flat.normalize().dot(dir) >= cos) {
				out.add((LivingEntity) e);
			}
		}
		return out;
	}

	/** The rush's direction: the look, or (auto-face) the nearest enemy in a wide cone. */
	private static Vec3 rushDir(ServerPlayer p, StandState s) {
		Vec3 look = flatLook(p);
		if (!s.autoFace) {
			return look;
		}
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (LivingEntity e : inFront(p, look, StandConfig.REACH + 1.5, 0.5)) {
			double d = e.distanceToSqr(p);
			if (d < bestD && com.projecthero.mod.combat.HeroTargets.isHostile(p, e)) {
				bestD = d;
				best = e;
			}
		}
		if (best == null) {
			return look;
		}
		Vec3 to = best.position().subtract(p.position());
		to = new Vec3(to.x, 0, to.z);
		return to.lengthSqr() < 1.0e-4 ? look : to.normalize();
	}

	// ---------------------------------------------------------------- shared start

	/** Cooldown, Stamina, then the Stand comes out if it was away. False (with a message) if the move can't start. */
	private static boolean begin(ServerPlayer p, String moveId, float cost) {
		if (!Stands.ready(p, moveId)) {
			Stands.say(p, "message.projecthero.stand.cooldown", ChatFormatting.GRAY, (Stands.cooldownRemaining(p, moveId) + 19) / 20);
			return false;
		}
		if (!Stands.spendStamina(p, cost)) {
			return false;
		}
		if (!Stands.summoned(p)) {
			Stands.summon(p);
		}
		Stands.breakPose(p);
		return true;
	}

	private static ServerLevel level(ServerPlayer p) {
		return (ServerLevel) p.level();
	}

	private static void fx(ServerPlayer p, net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
		Stands.sound(level(p), p.position().add(0, 1.0, 0), sound, volume, pitch);
	}

	// ---------------------------------------------------------------- empty-hand Stand punches

	/**
	 * A left-click on a creature with an empty hand while the Stand is out: Star Platinum punches instead -- a 3-hit chain,
	 * the 3rd a knockback blow. Called from {@code AttackEntityCallback} (server side); SUCCESS cancels the vanilla hit.
	 */
	public static InteractionResult onAttack(ServerPlayer p, Entity target) {
		StandState s = Stands.state(p);
		if (!s.isUser() || !s.summoned || !p.getMainHandItem().isEmpty() || !(target instanceof LivingEntity le) || TimeStop.frozen(p)) {
			return InteractionResult.PASS;
		}
		long now = p.level().getGameTime();
		int step = now - s.comboAt <= StandConfig.PUNCH_COMBO_WINDOW && now >= s.comboAt ? (s.comboStep + 1) % 3 : 0;
		float strength = p.getAttackStrengthScale(0.5f);
		p.resetAttackStrengthTicker();
		float scale = 0.3f + 0.7f * strength * strength;
		boolean heavy = step == 2;
		float dmg = (heavy ? StandConfig.PUNCH_FINISHER_DAMAGE : StandConfig.PUNCH_DAMAGE) * scale;
		Vec3 knock = heavy ? StandCombat.away(p.position(), le, StandConfig.PUNCH_FINISHER_KNOCKBACK * scale, 0.3)
				: StandCombat.away(p.position(), le, 0.25, 0.05);
		StandCombat.hit(p, le, dmg, knock);
		StandState n = Stands.state(p).copy();
		n.comboStep = step;
		n.comboAt = now;
		n.animId = heavy ? StandState.ANIM_PUNCH_HEAVY : step == 0 ? StandState.ANIM_PUNCH_R : StandState.ANIM_PUNCH_L;
		n.animStart = now;
		Stands.save(p, n);
		Stands.breakPose(p);
		fx(p, heavy ? StandSounds.ORA_FINISHER : StandSounds.PUNCH, heavy ? 0.9f : 0.7f, 0.9f + p.getRandom().nextFloat() * 0.2f);
		return InteractionResult.SUCCESS;
	}

	// ---------------------------------------------------------------- R: ORA Rush (hold) / Shift+R: Star Finger

	public static void startRush(ServerPlayer p) {
		StandState s = Stands.state(p);
		if (s.rushing || !begin(p, StandMove.ORA_RUSH, StandConfig.RUSH_START_COST)) {
			return;
		}
		StandState n = Stands.state(p).copy();
		n.rushing = true;
		n.rushStart = p.level().getGameTime();
		n.guarding = false;
		Stands.save(p, n);
		fx(p, StandSounds.ORA_RUSH, 1.0f, 1.0f);
	}

	/** Letting go of R (or running dry): one ORAAA knockback punch. */
	public static void stopRush(ServerPlayer p) {
		StandState s = Stands.state(p);
		if (!s.rushing) {
			return;
		}
		Vec3 dir = rushDir(p, s);
		for (LivingEntity e : inFront(p, dir, StandConfig.REACH, 0.5)) {
			StandCombat.hit(p, e, StandConfig.RUSH_FINISHER_DAMAGE, dir.scale(StandConfig.RUSH_FINISHER_KNOCKBACK).add(0, 0.35, 0));
		}
		long now = p.level().getGameTime();
		StandState n = Stands.state(p).copy();
		n.rushing = false;
		n.animId = StandState.ANIM_RUSH_FINISH;
		n.animStart = now;
		n.abilityReadyAt.put(StandMove.ORA_RUSH, now + StandConfig.RUSH_COOLDOWN);
		Stands.save(p, n);
		fx(p, StandSounds.ORA_FINISHER, 1.1f, 1.0f);
		Vec3 c = p.position().add(dir.scale(1.8)).add(0, 1.2, 0);
		Stands.particles(level(p), ParticleTypes.SWEEP_ATTACK, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		Stands.particles(level(p), Stands.AURA_BIG, c.x, c.y, c.z, 10, 0.4, 0.4, 0.4, 0.05);
	}

	private static void tickRush(ServerPlayer p, StandState s, long now) {
		long t = now - s.rushStart;
		float st = TimeStop.isCaster(p) ? s.stamina : s.stamina - StandConfig.RUSH_STAMINA_PER_TICK;
		StandState n = s.copy();
		n.stamina = Math.max(0f, st);
		Stands.save(p, n);
		if (st <= 0f || t >= StandConfig.RUSH_MAX_TICKS) {
			stopRush(p);
			return;
		}
		Vec3 dir = rushDir(p, s);
		if (t % StandConfig.RUSH_HIT_INTERVAL == 0) {
			for (LivingEntity e : inFront(p, dir, StandConfig.REACH, 0.5)) {
				StandCombat.hit(p, e, StandConfig.RUSH_HIT_DAMAGE, dir.scale(0.06));
			}
		}
		// fists knock the arrows and fireballs aside
		Vec3 eye = p.getEyePosition();
		for (Projectile proj : p.level().getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(StandConfig.REACH + 0.5))) {
			if (proj.getOwner() == p) {
				continue;
			}
			Vec3 to = proj.position().subtract(eye);
			Vec3 flat = new Vec3(to.x, 0, to.z);
			if (flat.lengthSqr() > 1.0e-4 && flat.normalize().dot(dir) > 0.3) {
				proj.setDeltaMovement(proj.getDeltaMovement().scale(-0.8));
				proj.setOwner(p);
				proj.hurtMarked = true;
				Stands.particles(level(p), ParticleTypes.CRIT, proj.getX(), proj.getY(), proj.getZ(), 5, 0.1, 0.1, 0.1, 0.2);
			}
		}
		if (t % 4 == 0) {
			fx(p, StandSounds.PUNCH, 0.6f, 0.9f + p.getRandom().nextFloat() * 0.4f);
			Vec3 c = p.position().add(dir.scale(1.6)).add(0, 1.2, 0);
			Stands.particles(level(p), Stands.AURA, c.x, c.y, c.z, 4, 0.4, 0.4, 0.4, 0.05);
		}
		if (t % 12 == 0 && t > 0) {
			fx(p, StandSounds.ORA_RUSH, 0.8f, 1.0f);
		}
	}

	public static void starFinger(ServerPlayer p) {
		if (!begin(p, StandMove.STAR_FINGER, StandConfig.FINGER_COST)) {
			return;
		}
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		Vec3 end = eye.add(look.scale(StandConfig.FINGER_RANGE));
		BlockHitResult bh = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		if (bh.getType() != HitResult.Type.MISS) {
			end = bh.getLocation();
		}
		AABB sweep = new AABB(eye, end).inflate(1.0);
		int n = 0;
		for (Entity e : p.level().getEntities(p, sweep)) {
			if (!StandCombat.isTarget(p, e)) {
				continue;
			}
			AABB bb = e.getBoundingBox().inflate(0.4);
			if (bb.clip(eye, end).isPresent() || bb.contains(eye)) {
				LivingEntity le = (LivingEntity) e;
				if (StandCombat.hit(p, le, StandConfig.FINGER_DAMAGE, look.scale(0.3))) {
					StandCombat.bleed(p, le, StandConfig.FINGER_BLEED_TICKS);
					n++;
				}
			}
		}
		long now = p.level().getGameTime();
		StandState st = Stands.state(p).copy();
		st.animId = StandState.ANIM_FINGER;
		st.animStart = now;
		st.abilityReadyAt.put(StandMove.STAR_FINGER, now + StandConfig.FINGER_COOLDOWN);
		Stands.save(p, st);
		double len = end.distanceTo(eye);
		for (double d = 1.0; d < len; d += 0.35) {
			Vec3 at = eye.add(look.scale(d)).subtract(0, 0.25, 0);
			Stands.particles(level(p), Stands.AURA, at.x, at.y, at.z, 1, 0.02, 0.02, 0.02, 0.0);
		}
		fx(p, StandSounds.STAR_FINGER, 1.0f, 1.0f);
	}

	// ---------------------------------------------------------------- G: Power Grab / Shift+G: Ball Bearing Flick

	public static void grabKey(ServerPlayer p) {
		StandState s = Stands.state(p);
		if (s.grabbedEntity != -1 || s.grabbedBlock != 0) {
			hurl(p);
		} else {
			grab(p);
		}
	}

	private static void grab(ServerPlayer p) {
		if (!begin(p, StandMove.POWER_GRAB, 0f)) {
			return;
		}
		long now = p.level().getGameTime();
		ServerLevel level = level(p);
		Entity held = null;
		LivingEntity aimed = aimed(p, StandConfig.REACH);
		if (aimed != null && !StandCombat.isBoss(aimed) && !(aimed instanceof Player)) {
			held = aimed;
		} else {
			// a dropped item under the crosshair
			Vec3 eye = p.getEyePosition();
			Vec3 end = eye.add(p.getLookAngle().scale(StandConfig.REACH));
			for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(eye, end).inflate(0.8))) {
				if (item.getBoundingBox().inflate(0.5).clip(eye, end).isPresent()) {
					held = item;
					break;
				}
			}
		}
		int blockId = 0;
		if (held == null) {
			Vec3 eye = p.getEyePosition();
			BlockHitResult bh = level.clip(new ClipContext(eye, eye.add(p.getLookAngle().scale(StandConfig.REACH)), ClipContext.Block.OUTLINE,
					ClipContext.Fluid.NONE, p));
			if (bh.getType() == HitResult.Type.BLOCK) {
				BlockPos pos = bh.getBlockPos();
				BlockState state = level.getBlockState(pos);
				float hardness = state.getDestroySpeed(level, pos);
				if (!state.isAir() && !state.hasBlockEntity() && state.getFluidState().isEmpty() && hardness >= 0f
						&& hardness <= StandConfig.GRAB_MAX_HARDNESS && p.mayInteract(level, pos) && p.mayBuild() && !TimeStop.isCaster(p)) {
					level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
					level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
							12, 0.3, 0.3, 0.3, 0.05);
					blockId = Block.getId(state);
				}
			}
		}
		StandState n = Stands.state(p).copy();
		if (held == null && blockId == 0) {
			n.abilityReadyAt.put(StandMove.POWER_GRAB, now + 10);
			Stands.save(p, n);
			Stands.say(p, "message.projecthero.stand.grab_nothing", ChatFormatting.GRAY);
			return;
		}
		n.grabbedEntity = held == null ? -1 : held.getId();
		n.grabbedBlock = blockId;
		n.animId = StandState.ANIM_GRAB;
		n.animStart = now;
		n.abilityReadyAt.put(StandMove.POWER_GRAB, now + StandConfig.GRAB_COOLDOWN);
		Stands.save(p, n);
		fx(p, StandSounds.GRAB, 0.9f, 1.0f);
	}

	/** Where a held thing hangs: in Star Platinum's hand, in front at chest height. */
	private static Vec3 holdPoint(ServerPlayer p) {
		return p.getEyePosition().add(p.getLookAngle().scale(2.0)).subtract(0, 0.3, 0);
	}

	private static void tickHold(ServerPlayer p, StandState s, long now) {
		if (now - s.animStart > StandConfig.GRAB_MAX_TICKS && s.animId == StandState.ANIM_GRAB) {
			release(p, false);
			return;
		}
		if (s.grabbedEntity == -1) {
			return;
		}
		Entity e = p.level().getEntity(s.grabbedEntity);
		if (e == null || !e.isAlive() || e.distanceToSqr(p) > 64.0) {
			StandState n = Stands.state(p).copy();
			n.grabbedEntity = -1;
			Stands.save(p, n);
			return;
		}
		Vec3 at = holdPoint(p).subtract(0, e.getBbHeight() * 0.5, 0);
		e.setPos(at.x, at.y, at.z);
		e.setDeltaMovement(Vec3.ZERO);
		e.fallDistance = 0f;
		e.hurtMarked = true;
	}

	/** Let go without throwing (the Stand put away, death, timeout): a block drops as its item. */
	private static void release(ServerPlayer p, boolean silent) {
		StandState s = Stands.state(p);
		if (s.grabbedBlock != 0) {
			BlockState state = Block.stateById(s.grabbedBlock);
			ItemStack drop = new ItemStack(state.getBlock().asItem());
			if (!drop.isEmpty()) {
				Vec3 at = holdPoint(p);
				p.level().addFreshEntity(new ItemEntity(p.level(), at.x, at.y, at.z, drop));
			}
		}
		if (s.grabbedEntity != -1 || s.grabbedBlock != 0) {
			StandState n = s.copy();
			n.grabbedEntity = -1;
			n.grabbedBlock = 0;
			Stands.save(p, n);
		}
	}

	private static void hurl(ServerPlayer p) {
		StandState s = Stands.state(p);
		if (!Stands.spendStamina(p, StandConfig.THROW_COST)) {
			return;
		}
		ServerLevel level = level(p);
		Vec3 look = p.getLookAngle();
		long now = p.level().getGameTime();
		Entity thrown = null;
		float damage = 4f;
		if (s.grabbedEntity != -1) {
			thrown = level.getEntity(s.grabbedEntity);
			if (thrown != null) {
				thrown.setDeltaMovement(look.scale(StandConfig.THROW_SPEED).add(0, 0.15, 0));
				thrown.hurtMarked = true;
				damage = Mth.clamp(thrown.getBbWidth() * thrown.getBbHeight() * 6f, 4f, 14f);
			}
		} else if (s.grabbedBlock != 0) {
			BlockState state = Block.stateById(s.grabbedBlock);
			Vec3 at = holdPoint(p);
			BlockPos pos = BlockPos.containing(at);
			if (!level.getBlockState(pos).isAir()) {
				pos = BlockPos.containing(p.getEyePosition());
			}
			if (level.getBlockState(pos).isAir()) {
				FallingBlockEntity fb = FallingBlockEntity.fall(level, pos, state);
				fb.setDeltaMovement(look.scale(StandConfig.THROW_SPEED * 0.9).add(0, 0.2, 0));
				fb.hurtMarked = true;
				thrown = fb;
				float hardness = state.getDestroySpeed(level, pos);
				damage = Mth.clamp(4f + Math.max(0f, hardness) * 3f, 4f, 16f);
			} else {
				release(p, true);
			}
		}
		if (thrown != null) {
			THROWN.put(thrown.getUUID(), new Thrown(p.getUUID(), now + StandConfig.THROWN_TRACK_TICKS, damage));
		}
		StandState n = Stands.state(p).copy();
		n.grabbedEntity = -1;
		n.grabbedBlock = 0;
		n.animId = StandState.ANIM_THROW;
		n.animStart = now;
		n.abilityReadyAt.put(StandMove.POWER_GRAB, now + StandConfig.THROW_COOLDOWN);
		Stands.save(p, n);
		fx(p, StandSounds.THROW, 1.0f, 1.0f);
	}

	/** Server tick: thrown things hit the first creatures they fly into (and a thrown mob slams into walls). */
	public static void tickThrown(MinecraftServer server) {
		if (THROWN.isEmpty()) {
			return;
		}
		Iterator<Map.Entry<UUID, Thrown>> it = THROWN.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Thrown> en = it.next();
			Thrown th = en.getValue();
			ServerPlayer owner = server.getPlayerList().getPlayer(th.owner);
			if (owner == null) {
				it.remove();
				continue;
			}
			Entity e = ((ServerLevel) owner.level()).getEntity(en.getKey());
			if (e == null || !e.isAlive() || owner.level().getGameTime() > th.until) {
				it.remove();
				continue;
			}
			if (TimeStop.frozen(e)) {
				continue;
			}
			boolean done = false;
			for (Entity o : owner.level().getEntities(e, e.getBoundingBox().inflate(0.4))) {
				if (o != owner && o instanceof LivingEntity victim && StandCombat.isTarget(owner, victim)) {
					StandCombat.hit(owner, victim, th.damage, e.getDeltaMovement().scale(0.6).add(0, 0.2, 0));
					done = true;
				}
			}
			if (!done && e instanceof LivingEntity le && (e.horizontalCollision || e.verticalCollisionBelow && e.getDeltaMovement().y < -0.5)
					&& e.tickCount > 2) {
				StandCombat.hit(owner, le, th.damage * 0.75f, Vec3.ZERO);
				done = true;
			} else if (done && e instanceof LivingEntity le) {
				StandCombat.hit(owner, le, th.damage * 0.5f, Vec3.ZERO);
			}
			if (done) {
				it.remove();
			}
		}
	}

	public static void flick(ServerPlayer p) {
		boolean nugget = p.getInventory().contains(new ItemStack(Items.IRON_NUGGET)) || p.getAbilities().instabuild;
		if (!begin(p, StandMove.BEARING_FLICK, StandConfig.FLICK_COST)) {
			return;
		}
		if (nugget && !p.getAbilities().instabuild) {
			for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
				ItemStack st = p.getInventory().getItem(i);
				if (st.is(Items.IRON_NUGGET)) {
					st.shrink(1);
					break;
				}
			}
		}
		ServerLevel level = level(p);
		Vec3 eye = p.getEyePosition();
		for (int b = 0; b < StandConfig.BEARINGS; b++) {
			float yaw = p.getYRot() + (b - 1) * 1.6f;
			float pitch = p.getXRot() + (b == 1 ? 0f : 0.8f);
			Vec3 dir = Vec3.directionFromRotation(pitch, yaw);
			Vec3 end = eye.add(dir.scale(StandConfig.BEARING_RANGE));
			BlockHitResult bh = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
			if (bh.getType() != HitResult.Type.MISS) {
				end = bh.getLocation();
			}
			LivingEntity hit = null;
			double best = Double.MAX_VALUE;
			for (Entity e : level.getEntities(p, new AABB(eye, end).inflate(0.6))) {
				if (!StandCombat.isTarget(p, e)) {
					continue;
				}
				var c = e.getBoundingBox().inflate(0.15).clip(eye, end);
				if (c.isPresent() && c.get().distanceTo(eye) < best) {
					best = c.get().distanceTo(eye);
					hit = (LivingEntity) e;
				}
			}
			if (hit != null) {
				StandCombat.hit(p, hit, StandConfig.BEARING_DAMAGE, dir.scale(0.25));
				end = eye.add(dir.scale(best));
			}
			double len = end.distanceTo(eye);
			for (double d = 1.0; d < len; d += 1.2) {
				Vec3 at = eye.add(dir.scale(d));
				Stands.particles(level, ParticleTypes.CRIT, at.x, at.y, at.z, 1, 0, 0, 0, 0);
			}
		}
		long now = p.level().getGameTime();
		StandState n = Stands.state(p).copy();
		n.animId = StandState.ANIM_FLICK;
		n.animStart = now;
		n.abilityReadyAt.put(StandMove.BEARING_FLICK, now + (nugget ? StandConfig.FLICK_COOLDOWN : StandConfig.FLICK_COOLDOWN_FREE));
		Stands.save(p, n);
		fx(p, StandSounds.FLICK, 1.0f, 1.0f);
	}

	// ---------------------------------------------------------------- X: Stand Leap / Shift+X: Stand Dash

	/** The side of a wall touching the player (the way to bounce), or null. */
	private static Vec3 wallNormal(ServerPlayer p) {
		for (Direction d : Direction.Plane.HORIZONTAL) {
			AABB moved = p.getBoundingBox().move(d.getStepX() * 0.45, 0, d.getStepZ() * 0.45);
			if (!p.level().noCollision(p, moved)) {
				return new Vec3(-d.getStepX(), 0, -d.getStepZ());
			}
		}
		return null;
	}

	public static void leapKey(ServerPlayer p) {
		StandState s = Stands.state(p);
		long now = p.level().getGameTime();
		if (s.leapRebounds > 0 && !p.onGround()) {
			Vec3 normal = wallNormal(p);
			if (normal != null) {
				Vec3 v = normal.scale(StandConfig.REBOUND_SPEED).add(flatLook(p).scale(0.3)).add(0, StandConfig.REBOUND_UP, 0);
				p.setDeltaMovement(v);
				p.hurtMarked = true;
				p.fallDistance = 0f;
				StandState n = s.copy();
				n.leapRebounds = s.leapRebounds - 1;
				n.animId = StandState.ANIM_LEAP;
				n.animStart = now;
				Stands.save(p, n);
				fx(p, StandSounds.LEAP, 0.8f, 1.2f);
				Stands.particles(level(p), ParticleTypes.CLOUD, p.getX(), p.getY() + 0.5, p.getZ(), 6, 0.2, 0.3, 0.2, 0.02);
				return;
			}
		}
		if (!begin(p, StandMove.LEAP, StandConfig.LEAP_COST)) {
			return;
		}
		p.setDeltaMovement(flatLook(p).scale(StandConfig.LEAP_FORWARD).add(0, StandConfig.LEAP_UP, 0));
		p.hurtMarked = true;
		p.fallDistance = 0f;
		StandState n = Stands.state(p).copy();
		n.leapRebounds = StandConfig.LEAP_REBOUNDS;
		n.animId = StandState.ANIM_LEAP;
		n.animStart = now;
		n.abilityReadyAt.put(StandMove.LEAP, now + StandConfig.LEAP_COOLDOWN);
		Stands.save(p, n);
		fx(p, StandSounds.LEAP, 1.0f, 1.0f);
		Stands.particles(level(p), ParticleTypes.CLOUD, p.getX(), p.getY() + 0.1, p.getZ(), 10, 0.4, 0.1, 0.4, 0.03);
	}

	public static void dash(ServerPlayer p) {
		if (!begin(p, StandMove.DASH, StandConfig.DASH_COST)) {
			return;
		}
		Vec3 dir = PressInput.flatDirection(p, PressInput.move, flatLook(p));
		p.setDeltaMovement(dir.scale(StandConfig.DASH_SPEED).add(0, 0.12, 0));
		p.hurtMarked = true;
		long now = p.level().getGameTime();
		StandState n = Stands.state(p).copy();
		n.dashUntil = now + StandConfig.DASH_TICKS;
		n.animId = StandState.ANIM_DASH;
		n.animStart = now;
		n.abilityReadyAt.put(StandMove.DASH, now + StandConfig.DASH_COOLDOWN);
		Stands.save(p, n);
		fx(p, StandSounds.DASH, 1.0f, 1.0f);
	}

	/** Stand Dash's catch: the first hit that lands during the dash is caught. True if caught. */
	public static boolean tryDashCatch(ServerPlayer p, net.minecraft.world.damagesource.DamageSource source) {
		StandState s = Stands.state(p);
		long now = p.level().getGameTime();
		if (s.dashUntil <= now || source.getEntity() == null && source.getDirectEntity() == null) {
			return false;
		}
		StandState n = s.copy();
		n.dashUntil = 0L;
		n.animId = StandState.ANIM_CATCH;
		n.animStart = now;
		Stands.save(p, n);
		fx(p, StandSounds.CATCH, 1.0f, 1.0f);
		Vec3 c = p.position().add(forward(p).scale(0.8)).add(0, 1.2, 0);
		Stands.particles(level(p), Stands.AURA_BIG, c.x, c.y, c.z, 8, 0.2, 0.2, 0.2, 0.02);
		if (source.getDirectEntity() instanceof Projectile proj) {
			proj.discard();
		}
		return true;
	}

	// ---------------------------------------------------------------- Z: Star Breaker (hold)

	public static void startBreaker(ServerPlayer p) {
		StandState s = Stands.state(p);
		if (s.chargeStart != 0L) {
			return;
		}
		if (!begin(p, StandMove.STAR_BREAKER, 0f)) {
			return;
		}
		if (Stands.state(p).stamina + 1.0e-3f < StandConfig.BREAKER_COST) {
			Stands.say(p, "message.projecthero.stand.no_stamina", ChatFormatting.GRAY);
			return;
		}
		StandState n = Stands.state(p).copy();
		n.chargeStart = p.level().getGameTime();
		Stands.save(p, n);
		fx(p, StandSounds.BREAKER_CHARGE, 0.9f, 1.0f);
	}

	public static void releaseBreaker(ServerPlayer p) {
		StandState s = Stands.state(p);
		if (s.chargeStart == 0L) {
			return;
		}
		long now = p.level().getGameTime();
		float charge = Mth.clamp((now - s.chargeStart) / (float) StandConfig.BREAKER_FULL_CHARGE, 0f, 1f);
		StandState n0 = s.copy();
		n0.chargeStart = 0L;
		Stands.save(p, n0);
		if (!Stands.spendStamina(p, StandConfig.BREAKER_COST)) {
			return;
		}
		ServerLevel level = level(p);
		Vec3 look = p.getLookAngle();
		Vec3 flat = flatLook(p);
		LivingEntity target = aimed(p, StandConfig.REACH);
		if (target != null) {
			if (target instanceof Player pl && pl.isBlocking()) {
				pl.disableShield(); // it breaks shields
			}
			float dmg = Mth.lerp(charge, StandConfig.BREAKER_MIN_DAMAGE, StandConfig.BREAKER_MAX_DAMAGE);
			StandCombat.hit(p, target, dmg, flat.scale(1.0 + StandConfig.BREAKER_KNOCKBACK * charge * 0.66).add(0, StandConfig.BREAKER_LIFT * charge + 0.2, 0));
		}
		if (charge >= 0.6f && p.mayBuild() && !TimeStop.isCaster(p)) {
			Vec3 eye = p.getEyePosition();
			Set<BlockPos> done = new HashSet<>();
			for (double d = 1.0; d <= 2.6; d += 0.4) {
				BlockPos pos = BlockPos.containing(eye.add(look.scale(d)));
				for (BlockPos q : new BlockPos[] { pos, pos.below() }) {
					if (!done.add(q)) {
						continue;
					}
					BlockState st = level.getBlockState(q);
					float h = st.getDestroySpeed(level, q);
					if (!st.isAir() && !st.hasBlockEntity() && h >= 0f && h <= StandConfig.BREAKER_SMASH_HARDNESS && p.mayInteract(level, q)) {
						level.destroyBlock(q, true, p);
					}
				}
			}
		}
		StandState n = Stands.state(p).copy();
		n.animId = StandState.ANIM_BREAKER;
		n.animStart = now;
		n.abilityReadyAt.put(StandMove.STAR_BREAKER, now + StandConfig.BREAKER_COOLDOWN);
		Stands.save(p, n);
		fx(p, StandSounds.STAR_BREAKER, 1.2f, 1.0f - 0.2f * charge);
		Vec3 c = p.getEyePosition().add(look.scale(2.0));
		Stands.particles(level, ParticleTypes.EXPLOSION, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		Stands.particles(level, Stands.AURA_BIG, c.x, c.y, c.z, (int) (10 + 20 * charge), 0.5, 0.5, 0.5, 0.1);
	}

	// ---------------------------------------------------------------- Shift+Z: The World

	public static void theWorld(ServerPlayer p) {
		if (!Stands.summoned(p)) {
			Stands.summon(p);
		}
		Stands.breakPose(p);
		TimeStop.begin(p);
	}

	// ---------------------------------------------------------------- V: Stand Guard (hold) / Shift+V: Precision Sight

	public static void startGuard(ServerPlayer p) {
		StandState s = Stands.state(p);
		if (s.guarding || s.rushing || !begin(p, StandMove.GUARD, 0f)) {
			return;
		}
		StandState n = Stands.state(p).copy();
		n.guarding = true;
		Stands.save(p, n);
		fx(p, StandSounds.GUARD, 0.8f, 1.0f);
	}

	public static void stopGuard(ServerPlayer p) {
		StandState s = Stands.state(p);
		if (!s.guarding) {
			return;
		}
		StandState n = s.copy();
		n.guarding = false;
		n.abilityReadyAt.put(StandMove.GUARD, p.level().getGameTime() + StandConfig.GUARD_COOLDOWN);
		Stands.save(p, n);
	}

	/** Guarding and the hit comes from in front (within 75 degrees either side). */
	public static boolean guardsAgainst(ServerPlayer p, net.minecraft.world.damagesource.DamageSource source) {
		StandState s = Stands.state(p);
		if (!s.guarding || source.getSourcePosition() == null) {
			return false;
		}
		Vec3 to = source.getSourcePosition().subtract(p.position());
		to = new Vec3(to.x, 0, to.z);
		return to.lengthSqr() > 1.0e-4 && to.normalize().dot(flatLook(p)) > 0.25;
	}

	private static void tickGuard(ServerPlayer p, StandState s) {
		float st = TimeStop.isCaster(p) ? s.stamina : s.stamina - StandConfig.GUARD_STAMINA_PER_TICK;
		if (st <= 0f) {
			stopGuard(p);
			return;
		}
		StandState n = s.copy();
		n.stamina = st;
		Stands.save(p, n);
		catchProjectiles(p, flatLook(p), StandConfig.REACH, 0.2);
	}

	/** Star Platinum plucks incoming projectiles out of the air; {@code minDot} -1 = all round. */
	private static void catchProjectiles(ServerPlayer p, Vec3 dir, double range, double minDot) {
		for (Projectile proj : p.level().getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(range))) {
			if (proj.getOwner() == p || proj.getDeltaMovement().lengthSqr() < 0.04 || TimeStop.frozen(proj)) {
				continue;
			}
			Vec3 to = proj.position().subtract(p.position());
			Vec3 flat = new Vec3(to.x, 0, to.z);
			if (minDot > -1.0 && (flat.lengthSqr() < 1.0e-4 || flat.normalize().dot(dir) < minDot)) {
				continue;
			}
			Stands.particles(level(p), Stands.AURA, proj.getX(), proj.getY(), proj.getZ(), 5, 0.1, 0.1, 0.1, 0.02);
			fx(p, StandSounds.CATCH, 0.7f, 1.2f);
			proj.discard();
			StandState n = Stands.state(p).copy();
			n.animId = StandState.ANIM_CATCH;
			n.animStart = p.level().getGameTime();
			Stands.save(p, n);
		}
	}

	public static void toggleSight(ServerPlayer p) {
		StandState s = Stands.state(p);
		long now = p.level().getGameTime();
		if (s.sightUntil > now) {
			endSight(p);
			return;
		}
		if (!begin(p, StandMove.SIGHT, 0f)) {
			return;
		}
		StandState n = Stands.state(p).copy();
		n.sightUntil = now + StandConfig.SIGHT_MAX_TICKS;
		Stands.save(p, n);
		p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, StandConfig.SIGHT_MAX_TICKS, 0, true, false, false));
		fx(p, StandSounds.SIGHT, 0.8f, 1.0f);
	}

	private static void endSight(ServerPlayer p) {
		StandState s = Stands.state(p);
		if (s.sightUntil == 0L) {
			return;
		}
		StandState n = s.copy();
		n.sightUntil = 0L;
		n.abilityReadyAt.put(StandMove.SIGHT, p.level().getGameTime() + StandConfig.SIGHT_COOLDOWN);
		Stands.save(p, n);
		MobEffectInstance nv = p.getEffect(MobEffects.NIGHT_VISION);
		if (nv != null && nv.isAmbient() && nv.getDuration() <= StandConfig.SIGHT_MAX_TICKS) {
			p.removeEffect(MobEffects.NIGHT_VISION);
		}
	}

	// ---------------------------------------------------------------- C: Super Inhale / Shift+C: ORAAA Uppercut

	public static void inhale(ServerPlayer p) {
		if (!begin(p, StandMove.INHALE, StandConfig.INHALE_COST)) {
			return;
		}
		long now = p.level().getGameTime();
		StandState n = Stands.state(p).copy();
		n.inhaleUntil = now + StandConfig.INHALE_TICKS;
		n.animId = StandState.ANIM_INHALE;
		n.animStart = now;
		n.abilityReadyAt.put(StandMove.INHALE, now + StandConfig.INHALE_COOLDOWN);
		Stands.save(p, n);
		INHALED.put(p.getUUID(), new HashSet<>());
		fx(p, StandSounds.INHALE, 1.0f, 1.0f);
		// it clears lingering potion clouds
		for (AreaEffectCloud cloud : p.level().getEntitiesOfClass(AreaEffectCloud.class, p.getBoundingBox().inflate(StandConfig.INHALE_RANGE))) {
			cloud.discard();
		}
	}

	private static void tickInhale(ServerPlayer p, StandState s, long now) {
		Vec3 mouth = restPoint(p).add(0, 1.6, 0);
		Set<Integer> pulled = INHALED.computeIfAbsent(p.getUUID(), k -> new HashSet<>());
		for (Entity e : p.level().getEntities(p, p.getBoundingBox().inflate(StandConfig.INHALE_RANGE))) {
			Vec3 to = mouth.subtract(e.position());
			double d = to.length();
			if (d < 0.8 || d > StandConfig.INHALE_RANGE) {
				continue;
			}
			Vec3 pull = to.normalize().scale(StandConfig.INHALE_PULL * Math.min(1.0, 3.0 / Math.max(1.0, d) + 0.4));
			if (e instanceof ItemEntity || e instanceof ExperienceOrb) {
				e.setDeltaMovement(pull.scale(1.5));
				e.hurtMarked = true;
			} else if (e instanceof LivingEntity le && StandCombat.isTarget(p, le) && !StandCombat.isBoss(le)) {
				Vec3 v = le.getDeltaMovement().add(pull);
				if (v.length() > 0.8) {
					v = v.normalize().scale(0.8);
				}
				le.setDeltaMovement(v);
				le.hurtMarked = true;
				pulled.add(le.getId());
			} else if (e instanceof AreaEffectCloud cloud) {
				cloud.discard();
			}
		}
		if (now % 2 == 0) {
			var r = p.getRandom();
			for (int i = 0; i < 3; i++) {
				Vec3 from = mouth.add((r.nextDouble() - 0.5) * 8, (r.nextDouble() - 0.3) * 3, (r.nextDouble() - 0.5) * 8);
				Vec3 v = mouth.subtract(from).scale(0.12);
				Stands.particles(level(p), ParticleTypes.CLOUD, from.x, from.y, from.z, 0, v.x, v.y, v.z, 1.0);
			}
		}
		if (now + 1 >= s.inhaleUntil) {
			// pulled in and left winded
			for (int id : pulled) {
				if (p.level().getEntity(id) instanceof LivingEntity le && le.isAlive()) {
					le.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 1));
				}
			}
			INHALED.remove(p.getUUID());
			StandState n = Stands.state(p).copy();
			n.inhaleUntil = 0L;
			Stands.save(p, n);
		}
	}

	public static void uppercut(ServerPlayer p) {
		if (!begin(p, StandMove.UPPERCUT, StandConfig.UPPERCUT_COST)) {
			return;
		}
		LivingEntity target = aimed(p, StandConfig.REACH);
		if (target != null) {
			StandCombat.hit(p, target, StandConfig.UPPERCUT_DAMAGE, flatLook(p).scale(0.2).add(0, StandConfig.UPPERCUT_LIFT, 0));
		}
		long now = p.level().getGameTime();
		StandState n = Stands.state(p).copy();
		n.animId = StandState.ANIM_UPPERCUT;
		n.animStart = now;
		n.abilityReadyAt.put(StandMove.UPPERCUT, now + StandConfig.UPPERCUT_COOLDOWN);
		Stands.save(p, n);
		fx(p, StandSounds.UPPERCUT, 1.0f, 1.0f);
		Vec3 c = p.position().add(forward(p).scale(1.4)).add(0, 1.0, 0);
		Stands.particles(level(p), Stands.AURA_BIG, c.x, c.y, c.z, 12, 0.2, 0.8, 0.2, 0.05);
	}

	// ---------------------------------------------------------------- tick / clear

	public static void tick(ServerPlayer p) {
		StandState s = Stands.state(p);
		long now = p.level().getGameTime();
		if (!s.summoned) {
			if (s.rushing || s.guarding || s.chargeStart != 0L || s.grabbedEntity != -1 || s.grabbedBlock != 0 || s.inhaleUntil != 0L) {
				clear(p);
			}
			if (s.leapRebounds >= 0) {
				tickLeap(p, s);
			}
			return;
		}
		if (s.rushing) {
			tickRush(p, s, now);
			s = Stands.state(p);
		}
		if (s.chargeStart != 0L && now - s.chargeStart >= StandConfig.BREAKER_AUTO_RELEASE) {
			releaseBreaker(p);
			s = Stands.state(p);
		}
		if (s.guarding) {
			tickGuard(p, s);
			s = Stands.state(p);
		}
		if (s.grabbedEntity != -1 || s.grabbedBlock != 0) {
			tickHold(p, s, now);
			s = Stands.state(p);
		}
		if (s.leapRebounds >= 0) {
			tickLeap(p, s);
			s = Stands.state(p);
		}
		if (s.inhaleUntil != 0L) {
			tickInhale(p, s, now);
			s = Stands.state(p);
		}
		if (s.sightUntil != 0L) {
			if (s.sightUntil <= now) {
				endSight(p);
			} else {
				catchProjectiles(p, flatLook(p), 4.0, -1.0);
			}
		}
	}

	private static void tickLeap(ServerPlayer p, StandState s) {
		p.fallDistance = 0f;
		if (p.onGround() && p.level().getGameTime() - s.animStart > 5) {
			StandState n = s.copy();
			n.leapRebounds = -1;
			Stands.save(p, n);
		}
	}

	/** Everything running stops (no finisher); a held block drops as its item. */
	public static void clear(ServerPlayer p) {
		StandState s = p.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.STAND_STATE, null);
		if (s == null || !s.isUser()) {
			return;
		}
		release(p, true);
		INHALED.remove(p.getUUID());
		s = Stands.state(p);
		if (s.sightUntil != 0L) {
			endSight(p);
			s = Stands.state(p);
		}
		if (s.rushing || s.guarding || s.chargeStart != 0L || s.inhaleUntil != 0L) {
			StandState n = s.copy();
			n.rushing = false;
			n.guarding = false;
			n.chargeStart = 0L;
			n.inhaleUntil = 0L;
			Stands.save(p, n);
		}
	}
}
