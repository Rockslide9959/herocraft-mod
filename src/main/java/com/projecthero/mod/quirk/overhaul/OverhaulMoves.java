package com.projecthero.mod.quirk.overhaul;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.hero.power.p05.GeoTerrain;
import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.squad.Squads;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7: Overhaul's twelve moves and the per-player sessions of the ones that play out over a few ticks (the Spike
 * Wave rolling forward, the Overhaul Field spreading ring by ring, the wall rising, the pit's lid). Every public move
 * is called by {@link OverhaulKit#handle} once its cooldown is ready, and by the gametests directly; each returns
 * whether it happened (refusals say why on the action bar and spend nothing).
 *
 * <p>Overhaul works by touch: every move needs an EMPTY main hand. Every move spends Focus ({@link Quirks#meter}).
 * Every block he reshapes is temporary ({@link OverhaulTerrain}).
 */
public final class OverhaulMoves {
	private record Job(long at, Runnable run) {
	}

	static final class Session {
		final List<Job> jobs = new ArrayList<>();
		long lastSpend = -1000L;
		long safeFallUntil;
	}

	private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

	/** The Fusion size modifier on Attributes.SCALE. */
	public static final ResourceLocation SCALE_ID = PowerToggles.id("quirk_overhaul_fusion_scale");
	private static final ResourceLocation ATTACK_ID = PowerToggles.id("quirk_overhaul_fusion_attack");
	private static final ResourceLocation ARMOR_ID = PowerToggles.id("quirk_overhaul_fusion_armor");
	private static final ResourceLocation TOUGH_ID = PowerToggles.id("quirk_overhaul_fusion_toughness");
	private static final ResourceLocation KB_ID = PowerToggles.id("quirk_overhaul_fusion_knockback");
	private static final ResourceLocation REACH_ID = PowerToggles.id("quirk_overhaul_fusion_reach");
	private static final ResourceLocation BLOCK_REACH_ID = PowerToggles.id("quirk_overhaul_fusion_block_reach");
	private static final ResourceLocation SPEED_ID = PowerToggles.id("quirk_overhaul_fusion_speed");

	private OverhaulMoves() {
	}

	static void clearSessionState() {
		SESSIONS.clear();
	}

	static void forget(UUID id) {
		SESSIONS.remove(id);
	}

	private static Session session(ServerPlayer p) {
		return SESSIONS.computeIfAbsent(p.getUUID(), k -> new Session());
	}

	private static long now(ServerPlayer p) {
		return p.level().getGameTime();
	}

	private static void later(ServerPlayer p, int delay, Runnable r) {
		session(p).jobs.add(new Job(now(p) + delay, r));
	}

	/** Moves still waiting to play out (gametests). */
	public static int pendingJobs(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		return s == null ? 0 : s.jobs.size();
	}

	private static void sound(ServerPlayer p, SoundEvent s, float vol, float pitch) {
		p.level().playSound(null, p.getX(), p.getY(), p.getZ(), s, SoundSource.PLAYERS, vol, pitch);
	}

	private static void sound(ServerPlayer p, Holder<SoundEvent> s, float vol, float pitch) {
		p.level().playSound(null, p.getX(), p.getY(), p.getZ(), s, SoundSource.PLAYERS, vol, pitch);
	}

	/** The hand-touch: a sharp clap of the gloved fingers. */
	private static void touchSound(ServerPlayer p) {
		sound(p, SoundEvents.PLAYER_ATTACK_STRONG, 0.7f, 1.55f);
		sound(p, SoundEvents.ARMOR_EQUIP_LEATHER, 0.8f, 1.3f);
	}

	private static Vec3 flatLook(ServerPlayer p) {
		Vec3 l = p.getLookAngle();
		Vec3 f = new Vec3(l.x, 0, l.z);
		return f.lengthSqr() < 1.0e-4 ? Vec3.directionFromRotation(0f, p.getYRot()) : f.normalize();
	}

	// ================================================================ the rules every move shares

	/** Touch moves need an empty main hand (says so). */
	static boolean bareHand(ServerPlayer p) {
		if (p.getMainHandItem().isEmpty()) {
			return true;
		}
		Quirks.say(p, "message.projecthero.overhaul.bare_hand", ChatFormatting.RED);
		return false;
	}

	/** Enough Focus for {@code cost} (says so if not). */
	static boolean hasFocus(ServerPlayer p, float cost) {
		if (Quirks.meter(p) + 0.01f >= cost) {
			return true;
		}
		Quirks.say(p, "message.projecthero.overhaul.focus", ChatFormatting.RED, (int) Math.ceil(cost));
		return false;
	}

	private static boolean canStart(ServerPlayer p, float cost) {
		return bareHand(p) && hasFocus(p, cost);
	}

	/** Spends Focus; spent to nothing, his focus breaks for a moment (no moves). */
	static void spend(ServerPlayer p, float cost) {
		float left = Quirks.addMeter(p, -cost);
		long now = now(p);
		session(p).lastSpend = now;
		if (left < 1f) {
			// not Quirks.overload: that stops every running move (Fusion would end the moment it began)
			long until = now + OverhaulConfig.FOCUS_SPENT_TICKS;
			Quirks.edit(p, n -> n.overloadUntil = Math.max(n.overloadUntil, until));
		}
	}

	private static void done(ServerPlayer p, String id, int cooldown, int anim) {
		Quirks.setCooldown(p, id, cooldown);
		Quirks.setAnim(p, anim);
	}

	private static @Nullable BlockPos groundUnder(ServerPlayer p) {
		return OverhaulTerrain.groundCell(p.serverLevel(), Mth.floor(p.getX()), Mth.floor(p.getY() + 0.2), Mth.floor(p.getZ()));
	}

	private static boolean needGround(ServerPlayer p, @Nullable BlockPos cell) {
		if (cell != null) {
			return true;
		}
		Quirks.say(p, "message.projecthero.overhaul.no_ground", ChatFormatting.RED);
		return false;
	}

	// ================================================================ R: Disassemble / Shift+R: Shrapnel Burst

	/** A touch-punch: {@link OverhaulConfig#DISASSEMBLE_DAMAGE}, the body bursts apart. */
	public static boolean disassemble(ServerPlayer p) {
		if (!canStart(p, OverhaulConfig.DISASSEMBLE_COST)) {
			return false;
		}
		LivingEntity t = OverhaulCombat.aimed(p, OverhaulCombat.reach(p), 20.0);
		if (t == null) {
			Quirks.say(p, "message.projecthero.overhaul.no_target", ChatFormatting.GRAY);
			return false;
		}
		spend(p, OverhaulConfig.DISASSEMBLE_COST);
		touchSound(p);
		OverhaulCombat.strike(p, t, p.position(), OverhaulCombat.scaled(p, OverhaulConfig.DISASSEMBLE_DAMAGE), OverhaulConfig.DISASSEMBLE_KNOCKBACK, 0.1);
		if (!t.isAlive()) {
			OverhaulCombat.disassembleFx(p.serverLevel(), t, 3f);
			sound(p, SoundEvents.STONE_BREAK, 1.0f, 0.6f);
		}
		done(p, OverhaulConfig.DISASSEMBLE, OverhaulConfig.DISASSEMBLE_COOLDOWN, Overhaul.ANIM_DISASSEMBLE);
		return true;
	}

	/** The touched body bursts and its shards hit everything near it. Returns the number hit (the target included), -1 refused. */
	public static int shrapnel(ServerPlayer p) {
		if (!canStart(p, OverhaulConfig.SHRAPNEL_COST)) {
			return -1;
		}
		LivingEntity t = OverhaulCombat.aimed(p, OverhaulCombat.reach(p), 20.0);
		if (t == null) {
			Quirks.say(p, "message.projecthero.overhaul.no_target", ChatFormatting.GRAY);
			return -1;
		}
		spend(p, OverhaulConfig.SHRAPNEL_COST);
		touchSound(p);
		ServerLevel level = p.serverLevel();
		Vec3 c = t.position().add(0, t.getBbHeight() * 0.55, 0);
		int n = OverhaulCombat.strike(p, t, p.position(), OverhaulCombat.scaled(p, OverhaulConfig.SHRAPNEL_DAMAGE), 0.3, 0.0) ? 1 : 0;
		OverhaulCombat.disassembleFx(level, t, 2.5f);
		BlockParticleOption shard = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.NETHER_WART_BLOCK.defaultBlockState());
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, t.getBoundingBox().inflate(OverhaulConfig.SHRAPNEL_RADIUS),
				e -> e != t && OverhaulCombat.isTarget(p, e) && e.distanceTo(t) <= OverhaulConfig.SHRAPNEL_RADIUS)) {
			Vec3 ec = e.position().add(0, e.getBbHeight() * 0.5, 0);
			AbilityHelpers.line(level, c, ec, shard, 2.0);
			if (OverhaulCombat.strike(p, e, c, OverhaulCombat.scaled(p, OverhaulConfig.SHRAPNEL_SPLASH_DAMAGE), 0.6, 0.15)) {
				n++;
			}
		}
		level.sendParticles(OverhaulCombat.RED_BIG, c.x, c.y, c.z, 20, OverhaulConfig.SHRAPNEL_RADIUS * 0.3, 0.6, OverhaulConfig.SHRAPNEL_RADIUS * 0.3, 0.0);
		sound(p, SoundEvents.STONE_BREAK, 1.0f, 0.7f);
		done(p, OverhaulConfig.SHRAPNEL, OverhaulConfig.SHRAPNEL_COOLDOWN, Overhaul.ANIM_SHRAPNEL);
		return n;
	}

	// ================================================================ G: Spike Wave / Shift+G: Spike Field

	/** Hits every valid target within {@code radius} (flat) of {@code at} not yet in {@code hit}. */
	private static void spikeHits(ServerPlayer p, Vec3 at, double radius, float damage, double lift, Set<Integer> hit) {
		for (LivingEntity e : p.serverLevel().getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius + 1, 2.5, radius + 1),
				e -> OverhaulCombat.isTarget(p, e) && !hit.contains(e.getId()))) {
			double dx = e.getX() - at.x, dz = e.getZ() - at.z;
			if (dx * dx + dz * dz > (radius + e.getBbWidth() * 0.5) * (radius + e.getBbWidth() * 0.5) || Math.abs(e.getY() - at.y) > 2.5) {
				continue;
			}
			hit.add(e.getId());
			OverhaulCombat.strike(p, e, at.subtract(0, 1, 0), damage, 0.25, lift);
		}
	}

	/** A line of spikes erupts from the ground toward the target (or the way he looks), one block a tick. */
	public static boolean spikeWave(ServerPlayer p) {
		if (!canStart(p, OverhaulConfig.SPIKE_WAVE_COST)) {
			return false;
		}
		BlockPos start = groundUnder(p);
		if (!needGround(p, start)) {
			return false;
		}
		spend(p, OverhaulConfig.SPIKE_WAVE_COST);
		LivingEntity aim = OverhaulCombat.aimed(p, 16.0, 25.0);
		Vec3 dir = aim == null ? flatLook(p) : new Vec3(aim.getX() - p.getX(), 0, aim.getZ() - p.getZ());
		dir = dir.lengthSqr() < 1.0e-4 ? flatLook(p) : dir.normalize();
		final Vec3 d = dir;
		final int[] y = { start.getY() };
		final boolean[] stopped = { false };
		final Set<Integer> hit = new HashSet<>();
		float dmg = OverhaulCombat.scaled(p, OverhaulConfig.SPIKE_WAVE_DAMAGE);
		OverhaulTerrain.burstAt(p.serverLevel(), start, p.serverLevel().getBlockState(start.below()), 14);
		touchSound(p);
		for (int i = 1; i <= OverhaulConfig.SPIKE_WAVE_LENGTH; i++) {
			final int step = i;
			later(p, i, () -> {
				if (stopped[0]) {
					return;
				}
				ServerLevel level = p.serverLevel();
				BlockPos cell = OverhaulTerrain.groundCell(level, Mth.floor(startX(start) + d.x * step), y[0],
						Mth.floor(startZ(start) + d.z * step));
				if (cell == null || Math.abs(cell.getY() - y[0]) > 2) {
					stopped[0] = true;
					return;
				}
				y[0] = cell.getY();
				spikeHits(p, Vec3.atBottomCenterOf(cell), OverhaulConfig.SPIKE_HIT_RADIUS, dmg, OverhaulConfig.SPIKE_LIFT, hit);
				OverhaulTerrain.spike(p, level, cell, step % 3 == 0 ? 3 : 2, OverhaulConfig.SPIKE_TTL);
			});
		}
		done(p, OverhaulConfig.SPIKE_WAVE, OverhaulConfig.SPIKE_WAVE_COOLDOWN, Overhaul.ANIM_SPIKE_WAVE);
		return true;
	}

	private static double startX(BlockPos start) {
		return start.getX() + 0.5;
	}

	private static double startZ(BlockPos start) {
		return start.getZ() + 0.5;
	}

	/** Two rings of spikes erupt all around him. */
	public static boolean spikeField(ServerPlayer p) {
		if (!canStart(p, OverhaulConfig.SPIKE_FIELD_COST)) {
			return false;
		}
		BlockPos center = groundUnder(p);
		if (!needGround(p, center)) {
			return false;
		}
		spend(p, OverhaulConfig.SPIKE_FIELD_COST);
		float dmg = OverhaulCombat.scaled(p, OverhaulConfig.SPIKE_FIELD_DAMAGE);
		Set<Integer> hit = new HashSet<>();
		Vec3 c = Vec3.atBottomCenterOf(center);
		touchSound(p);
		later(p, 2, () -> ring(p, center, OverhaulConfig.SPIKE_FIELD_INNER, 2, 1.6));
		later(p, 2, () -> spikeHitsRing(p, c, 0.8, OverhaulConfig.SPIKE_FIELD_OUTER + 0.6, dmg, hit));
		later(p, 5, () -> ring(p, center, OverhaulConfig.SPIKE_FIELD_OUTER, 3, 1.6));
		done(p, OverhaulConfig.SPIKE_FIELD, OverhaulConfig.SPIKE_FIELD_COOLDOWN, Overhaul.ANIM_SPIKE_FIELD);
		return true;
	}

	/** Every valid target between {@code inner} and {@code outer} (flat) from {@code c}, knocked outward and up. */
	private static void spikeHitsRing(ServerPlayer p, Vec3 c, double inner, double outer, float dmg, Set<Integer> hit) {
		for (LivingEntity e : p.serverLevel().getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(outer + 1, 3, outer + 1),
				e -> OverhaulCombat.isTarget(p, e) && !hit.contains(e.getId()))) {
			double dist = Math.hypot(e.getX() - c.x, e.getZ() - c.z);
			if (dist < inner || dist > outer + e.getBbWidth() * 0.5 || Math.abs(e.getY() - c.y) > 3) {
				continue;
			}
			hit.add(e.getId());
			OverhaulCombat.strike(p, e, c, dmg, 0.7, OverhaulConfig.SPIKE_LIFT);
		}
	}

	/** One ring of spikes of {@code height} around {@code center}, about one every {@code spacing} blocks. */
	private static void ring(ServerPlayer p, BlockPos center, double radius, int height, double spacing) {
		ServerLevel level = p.serverLevel();
		int n = Math.max(6, (int) Math.round(2 * Math.PI * radius / spacing));
		Set<Long> done = new HashSet<>();
		for (int i = 0; i < n; i++) {
			double a = i * 2 * Math.PI / n;
			int x = Mth.floor(center.getX() + 0.5 + Math.cos(a) * radius);
			int z = Mth.floor(center.getZ() + 0.5 + Math.sin(a) * radius);
			BlockPos cell = OverhaulTerrain.groundCell(level, x, center.getY(), z);
			if (cell == null || Math.abs(cell.getY() - center.getY()) > 2 || !done.add(cell.asLong())) {
				continue;
			}
			OverhaulTerrain.spike(p, level, cell, height, OverhaulConfig.SPIKE_TTL);
		}
	}

	// ================================================================ X: Reshape Wall / Shift+X: Launch Ramp

	/** The ground ahead rips up into a wall ({@link OverhaulConfig#WALL_WIDTH} x {@link OverhaulConfig#WALL_HEIGHT}), rising row by row. */
	public static boolean wall(ServerPlayer p) {
		if (!canStart(p, OverhaulConfig.WALL_COST)) {
			return false;
		}
		BlockPos under = groundUnder(p);
		if (!needGround(p, under)) {
			return false;
		}
		ServerLevel level = p.serverLevel();
		Direction facing = p.getDirection();
		Direction right = facing.getClockWise();
		BlockPos base = under.relative(facing, OverhaulConfig.WALL_DISTANCE);
		List<BlockPos> columns = new ArrayList<>();
		int half = OverhaulConfig.WALL_WIDTH / 2;
		for (int j = -half; j <= half; j++) {
			BlockPos col = base.relative(right, j);
			BlockPos cell = OverhaulTerrain.groundCell(level, col.getX(), under.getY(), col.getZ());
			if (cell != null && Math.abs(cell.getY() - under.getY()) <= 2) {
				columns.add(cell);
			}
		}
		if (!needGround(p, columns.isEmpty() ? null : under)) {
			return false;
		}
		spend(p, OverhaulConfig.WALL_COST);
		touchSound(p);
		for (int h = 0; h < OverhaulConfig.WALL_HEIGHT; h++) {
			final int row = h;
			later(p, 1 + h * 2, () -> {
				for (BlockPos cell : columns) {
					BlockState ground = level.getBlockState(cell.below());
					BlockPos at = cell.above(row);
					OverhaulTerrain.burstAt(level, at, OverhaulTerrain.material(ground), 6);
					if (row == 0 || level.getBlockState(at.below()).isSolid()) {
						OverhaulTerrain.place(p, level, at, OverhaulTerrain.material(ground), OverhaulConfig.WALL_TTL - row * 2);
					}
				}
				level.playSound(null, base, SoundEvents.STONE_PLACE, SoundSource.PLAYERS, 1.0f, 0.6f + row * 0.1f);
				level.playSound(null, base, SoundEvents.STONE_BREAK, SoundSource.PLAYERS, 0.8f, 0.7f);
			});
		}
		Quirks.setVal(p, Overhaul.V_WALL, now(p) + OverhaulConfig.WALL_TTL);
		done(p, OverhaulConfig.WALL, OverhaulConfig.WALL_COOLDOWN, Overhaul.ANIM_WALL);
		return true;
	}

	/** The ground ahead rises as a ramp and flings him forward (no fall damage for a few seconds). */
	public static boolean ramp(ServerPlayer p) {
		if (!canStart(p, OverhaulConfig.RAMP_COST)) {
			return false;
		}
		BlockPos under = groundUnder(p);
		if (!needGround(p, under)) {
			return false;
		}
		spend(p, OverhaulConfig.RAMP_COST);
		ServerLevel level = p.serverLevel();
		Direction facing = p.getDirection();
		Direction right = facing.getClockWise();
		BlockState mat = OverhaulTerrain.material(level.getBlockState(under.below()));
		for (int dist = 1; dist <= 2; dist++) {
			for (int j = -1; j <= 1; j++) {
				BlockPos col = under.relative(facing, dist).relative(right, j);
				BlockPos cell = OverhaulTerrain.groundCell(level, col.getX(), under.getY(), col.getZ());
				if (cell == null || Math.abs(cell.getY() - under.getY()) > 1) {
					continue;
				}
				for (int h = 0; h < dist; h++) {
					if (!OverhaulTerrain.place(p, level, cell.above(h), mat, OverhaulConfig.RAMP_TTL)) {
						break;
					}
				}
				OverhaulTerrain.burstAt(level, cell, mat, 5);
			}
		}
		Vec3 f = flatLook(p);
		AbilityHelpers.launchSelf(p, new Vec3(f.x * OverhaulConfig.RAMP_FORWARD, OverhaulConfig.RAMP_UP, f.z * OverhaulConfig.RAMP_FORWARD));
		session(p).safeFallUntil = now(p) + OverhaulConfig.RAMP_SAFE_TICKS;
		touchSound(p);
		sound(p, SoundEvents.STONE_PLACE, 1.0f, 0.5f);
		done(p, OverhaulConfig.RAMP, OverhaulConfig.RAMP_COOLDOWN, Overhaul.ANIM_RAMP);
		return true;
	}

	// ================================================================ Z: Restore / Shift+Z: Reassemble Armour

	private static boolean ally(ServerPlayer p, LivingEntity e) {
		return e != p && e.isAlive() && (Squads.areAllies(p, e) || HeroTargets.isOwnedBy(e, p) || HeroTargets.isFriendlyPet(p, e));
	}

	/** Takes a body apart and puts it back whole: full health, harmful effects and fire gone. */
	static void restoreBody(ServerLevel level, LivingEntity e) {
		e.setHealth(e.getMaxHealth());
		e.clearFire();
		List<Holder<MobEffect>> bad = new ArrayList<>();
		for (MobEffectInstance inst : e.getActiveEffects()) {
			if (inst.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
				bad.add(inst.getEffect());
			}
		}
		bad.forEach(e::removeEffect);
		OverhaulCombat.disassembleFx(level, e, 1.5f);
		Vec3 c = e.position().add(0, e.getBbHeight() * 0.5, 0);
		level.sendParticles(OverhaulCombat.MEND, c.x, c.y, c.z, 18, e.getBbWidth() * 0.4, e.getBbHeight() * 0.35, e.getBbWidth() * 0.4, 0.0);
		level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 6, 0.3, 0.5, 0.3, 0.02);
		level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.2f, 1.2f);
		level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.STONE_BREAK, SoundSource.PLAYERS, 0.6f, 1.4f);
	}

	/**
	 * Restores himself, or the ally he looks at, and every squadmate within {@link OverhaulConfig#RESTORE_SQUAD_RADIUS}.
	 * Returns how many bodies were restored (-1 refused).
	 */
	public static int restore(ServerPlayer p) {
		if (!canStart(p, OverhaulConfig.RESTORE_COST)) {
			return -1;
		}
		spend(p, OverhaulConfig.RESTORE_COST);
		ServerLevel level = p.serverLevel();
		LivingEntity looked = AbilityHelpers.raycastEntity(p, OverhaulConfig.RESTORE_RANGE);
		Set<LivingEntity> bodies = new java.util.LinkedHashSet<>();
		bodies.add(looked != null && ally(p, looked) ? looked : p);
		for (Player other : level.getEntitiesOfClass(Player.class, p.getBoundingBox().inflate(OverhaulConfig.RESTORE_SQUAD_RADIUS),
				o -> o != p && Squads.areAllies(p, o) && o.distanceTo(p) <= OverhaulConfig.RESTORE_SQUAD_RADIUS)) {
			bodies.add(other);
		}
		for (LivingEntity e : bodies) {
			restoreBody(level, e);
		}
		touchSound(p);
		Quirks.say(p, "message.projecthero.overhaul.restored", ChatFormatting.AQUA, bodies.size());
		done(p, OverhaulConfig.RESTORE, OverhaulConfig.RESTORE_COOLDOWN, Overhaul.ANIM_RESTORE);
		return bodies.size();
	}

	/** His worn armour rebuilt to full durability, and Absorption. Returns the number of pieces mended (-1 refused). */
	public static int reassemble(ServerPlayer p) {
		if (!canStart(p, OverhaulConfig.REASSEMBLE_COST)) {
			return -1;
		}
		spend(p, OverhaulConfig.REASSEMBLE_COST);
		int n = 0;
		for (EquipmentSlot slot : new EquipmentSlot[] { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET }) {
			ItemStack st = p.getItemBySlot(slot);
			if (!st.isEmpty() && st.isDamageableItem() && st.getDamageValue() > 0) {
				st.setDamageValue(0);
				n++;
			}
		}
		p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, OverhaulConfig.REASSEMBLE_ABSORPTION_TICKS, OverhaulConfig.REASSEMBLE_ABSORPTION_AMP, false, true, true));
		ServerLevel level = p.serverLevel();
		Vec3 c = p.position().add(0, 1.0, 0);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.IRON_BLOCK.defaultBlockState()), c.x, c.y, c.z, 24, 0.35, 0.6, 0.35, 0.1);
		level.sendParticles(OverhaulCombat.MEND, c.x, c.y, c.z, 14, 0.4, 0.6, 0.4, 0.0);
		sound(p, SoundEvents.ARMOR_EQUIP_IRON, 1.0f, 0.9f);
		sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 1.0f);
		done(p, OverhaulConfig.REASSEMBLE, OverhaulConfig.REASSEMBLE_COOLDOWN, Overhaul.ANIM_REASSEMBLE);
		return n;
	}

	// ================================================================ V: Overhaul Field / Shift+V: Collapse Pit

	/** Everything around him shatters and reforms as a forest of spikes, ring by ring out to {@link OverhaulConfig#FIELD_RADIUS}. */
	public static boolean field(ServerPlayer p) {
		if (!canStart(p, OverhaulConfig.FIELD_COST)) {
			return false;
		}
		BlockPos center = groundUnder(p);
		if (!needGround(p, center)) {
			return false;
		}
		spend(p, OverhaulConfig.FIELD_COST);
		float dmg = OverhaulCombat.scaled(p, OverhaulConfig.FIELD_DAMAGE);
		Set<Integer> hit = new HashSet<>();
		Vec3 c = Vec3.atBottomCenterOf(center);
		touchSound(p);
		sound(p, SoundEvents.GENERIC_EXPLODE, 0.6f, 0.6f);
		int rings = (int) Math.ceil(OverhaulConfig.FIELD_RADIUS);
		for (int r = 2; r <= rings; r++) {
			final int radius = r;
			later(p, r - 1, () -> {
				ServerLevel level = p.serverLevel();
				for (int dx = -radius; dx <= radius; dx++) {
					for (int dz = -radius; dz <= radius; dz++) {
						double dist = Math.sqrt(dx * dx + dz * dz);
						if (dist < radius - 0.5 || dist >= radius + 0.5 || dist > OverhaulConfig.FIELD_RADIUS + 0.5) {
							continue;
						}
						BlockPos cell = OverhaulTerrain.groundCell(level, center.getX() + dx, center.getY(), center.getZ() + dz);
						if (cell == null || Math.abs(cell.getY() - center.getY()) > 2) {
							continue;
						}
						if (((dx + dz) & 1) == 0 && level.random.nextFloat() < 0.75f) {
							OverhaulTerrain.spike(p, level, cell, 2 + level.random.nextInt(2), OverhaulConfig.FIELD_TTL);
						} else {
							OverhaulTerrain.burstAt(level, cell, level.getBlockState(cell.below()), 3);
						}
					}
				}
				spikeHitsRing(p, c, radius - 1.0, radius + 0.5, dmg, hit);
			});
		}
		done(p, OverhaulConfig.FIELD, OverhaulConfig.FIELD_COOLDOWN, Overhaul.ANIM_FIELD);
		return true;
	}

	/**
	 * The ground under the target drops into a {@link OverhaulConfig#PIT_DEPTH}-deep pit and closes over it for
	 * {@link OverhaulConfig#PIT_TICKS}. Bosses and anything too big for the hole are refused. Returns the trapped target.
	 */
	public static @Nullable LivingEntity pit(ServerPlayer p) {
		if (!canStart(p, OverhaulConfig.PIT_COST)) {
			return null;
		}
		LivingEntity t = OverhaulCombat.aimed(p, OverhaulConfig.PIT_RANGE, 15.0);
		if (t == null) {
			Quirks.say(p, "message.projecthero.overhaul.no_target", ChatFormatting.GRAY);
			return null;
		}
		if (OverhaulCombat.isBoss(t) || t.getBbWidth() > OverhaulConfig.PIT_MAX_WIDTH || t.getBbHeight() > OverhaulConfig.PIT_MAX_HEIGHT) {
			Quirks.say(p, "message.projecthero.overhaul.too_big", ChatFormatting.RED);
			return null;
		}
		ServerLevel level = p.serverLevel();
		BlockPos feet = BlockPos.containing(t.getX(), t.getY() + 0.2, t.getZ());
		List<BlockPos> hole = new ArrayList<>();
		for (int i = 1; i <= OverhaulConfig.PIT_DEPTH; i++) {
			hole.add(feet.below(i));
		}
		BlockPos floor = feet.below(OverhaulConfig.PIT_DEPTH + 1);
		BlockState feetState = level.getBlockState(feet);
		boolean ok = t.onGround() && (feetState.isAir() || feetState.canBeReplaced()) && feetState.getFluidState().isEmpty()
				&& level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP) && GeoTerrain.canEdit(p, level, feet);
		for (BlockPos h : hole) {
			ok &= GeoTerrain.canEdit(p, level, h) && GeoTerrain.diggable(level, h) && !GeoTerrain.isTemp(level, h);
		}
		if (!ok) {
			Quirks.say(p, "message.projecthero.overhaul.no_ground", ChatFormatting.RED);
			return null;
		}
		spend(p, OverhaulConfig.PIT_COST);
		BlockState lid = OverhaulTerrain.material(level.getBlockState(hole.get(0)));
		for (BlockPos h : hole) {
			OverhaulTerrain.burstAt(level, h, level.getBlockState(h), 12);
			GeoTerrain.pocket(level, h, t, OverhaulConfig.PIT_TICKS + 4, 10);
		}
		BlockPos bottom = hole.get(hole.size() - 1);
		t.teleportTo(bottom.getX() + 0.5, bottom.getY(), bottom.getZ() + 0.5);
		t.setDeltaMovement(Vec3.ZERO);
		t.hurtMarked = true;
		OverhaulCombat.strike(p, t, t.position(), OverhaulCombat.scaled(p, OverhaulConfig.PIT_DAMAGE), 0.0, 0.0);
		level.playSound(null, feet, SoundEvents.GRAVEL_BREAK, SoundSource.PLAYERS, 1.2f, 0.6f);
		level.playSound(null, feet, SoundEvents.STONE_BREAK, SoundSource.PLAYERS, 1.0f, 0.5f);
		later(p, 3, () -> {
			OverhaulTerrain.place(p, level, feet, lid, OverhaulConfig.PIT_TICKS - 3);
			OverhaulTerrain.burstAt(level, feet, lid, 10);
			level.playSound(null, feet, SoundEvents.STONE_PLACE, SoundSource.PLAYERS, 1.0f, 0.6f);
		});
		touchSound(p);
		Quirks.setVal(p, Overhaul.V_PIT, now(p) + OverhaulConfig.PIT_TICKS);
		done(p, OverhaulConfig.PIT, OverhaulConfig.PIT_COOLDOWN, Overhaul.ANIM_PIT);
		return t;
	}

	// ================================================================ C: Fusion / Shift+C: Shard Shot

	/** Fusion on (absorbing the ground around him) -- or, pressed again, off early. */
	public static boolean fusion(ServerPlayer p) {
		if (Overhaul.fused(p)) {
			endFusion(p);
			return true;
		}
		if (!canStart(p, OverhaulConfig.FUSION_COST)) {
			return false;
		}
		spend(p, OverhaulConfig.FUSION_COST);
		Quirks.setVal(p, Overhaul.V_FUSION, now(p) + OverhaulConfig.FUSION_TICKS);
		applyFusion(p);
		ServerLevel level = p.serverLevel();
		// the ground around him breaks into cubes and streams into his body
		Vec3 c = p.position().add(0, 1.0, 0);
		double r = OverhaulConfig.FUSION_ABSORB_RADIUS;
		for (int i = 0; i < 40; i++) {
			double a = level.random.nextDouble() * Math.PI * 2;
			double d = 1.5 + level.random.nextDouble() * (r - 1.5);
			int x = Mth.floor(p.getX() + Math.cos(a) * d), z = Mth.floor(p.getZ() + Math.sin(a) * d);
			BlockPos cell = OverhaulTerrain.groundCell(level, x, Mth.floor(p.getY()), z);
			if (cell == null) {
				continue;
			}
			BlockState g = level.getBlockState(cell.below());
			Vec3 from = Vec3.atBottomCenterOf(cell);
			Vec3 v = c.subtract(from).scale(0.18);
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, g), from.x, from.y + 0.1, from.z, 0, v.x, v.y + 0.25, v.z, 1.0);
		}
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()), c.x, c.y, c.z, 40, 0.5, 0.8, 0.5, 0.15);
		touchSound(p);
		sound(p, SoundEvents.STONE_BREAK, 1.2f, 0.5f);
		sound(p, SoundEvents.ANVIL_LAND, 0.4f, 0.5f);
		sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 0.6f);
		Quirks.setAnim(p, Overhaul.ANIM_FUSION);
		return true;
	}

	private static void applyFusion(ServerPlayer p) {
		PowerToggles.modifier(p, Attributes.ATTACK_DAMAGE, ATTACK_ID, OverhaulConfig.FUSION_ATTACK_BONUS, AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(p, Attributes.ARMOR, ARMOR_ID, OverhaulConfig.FUSION_ARMOR, AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(p, Attributes.ARMOR_TOUGHNESS, TOUGH_ID, OverhaulConfig.FUSION_TOUGHNESS, AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(p, Attributes.KNOCKBACK_RESISTANCE, KB_ID, OverhaulConfig.FUSION_KNOCKBACK_RESIST, AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(p, Attributes.ENTITY_INTERACTION_RANGE, REACH_ID, OverhaulConfig.FUSION_REACH_BONUS, AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(p, Attributes.BLOCK_INTERACTION_RANGE, BLOCK_REACH_ID, OverhaulConfig.FUSION_REACH_BONUS, AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(p, Attributes.MOVEMENT_SPEED, SPEED_ID, OverhaulConfig.FUSION_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
	}

	private static void clearFusionModifiers(ServerPlayer p, boolean scaleToo) {
		PowerToggles.clearModifier(p, Attributes.ATTACK_DAMAGE, ATTACK_ID);
		PowerToggles.clearModifier(p, Attributes.ARMOR, ARMOR_ID);
		PowerToggles.clearModifier(p, Attributes.ARMOR_TOUGHNESS, TOUGH_ID);
		PowerToggles.clearModifier(p, Attributes.KNOCKBACK_RESISTANCE, KB_ID);
		PowerToggles.clearModifier(p, Attributes.ENTITY_INTERACTION_RANGE, REACH_ID);
		PowerToggles.clearModifier(p, Attributes.BLOCK_INTERACTION_RANGE, BLOCK_REACH_ID);
		PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, SPEED_ID);
		if (scaleToo) {
			PowerToggles.clearModifier(p, Attributes.SCALE, SCALE_ID);
		}
	}

	/** Fusion ends: the stone falls away (the body shrinks back over a few ticks); the cooldown starts now. */
	public static void endFusion(ServerPlayer p) {
		if (Quirks.state(p).val(Overhaul.V_FUSION) == 0L) {
			return;
		}
		Quirks.setVal(p, Overhaul.V_FUSION, 0L);
		clearFusionModifiers(p, false);
		ServerLevel level = p.serverLevel();
		Vec3 c = p.position().add(0, p.getBbHeight() * 0.5, 0);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()), c.x, c.y, c.z, 50, 0.6, 0.9, 0.6, 0.2);
		sound(p, SoundEvents.STONE_BREAK, 1.2f, 0.7f);
		Quirks.setCooldown(p, OverhaulConfig.FUSION, OverhaulConfig.FUSION_COOLDOWN);
		Quirks.setAnim(p, Overhaul.ANIM_FUSION_END);
	}

	/** The body eases toward its Fusion size (or back), waiting if the bigger body would not fit. */
	private static void tickScale(ServerPlayer p, boolean fused) {
		AttributeInstance inst = p.getAttribute(Attributes.SCALE);
		if (inst == null) {
			return;
		}
		AttributeModifier m = inst.getModifier(SCALE_ID);
		double cur = m == null ? 0.0 : m.amount();
		double target = fused ? OverhaulConfig.FUSION_SCALE : 0.0;
		if (Math.abs(cur - target) < 1.0e-4) {
			return;
		}
		double step = OverhaulConfig.FUSION_SCALE / Math.max(1, OverhaulConfig.FUSION_GROW_TICKS);
		double next = cur < target ? Math.min(target, cur + step) : Math.max(target, cur - step);
		next = Math.round(next * 1000.0) / 1000.0;
		if (next > cur) {
			double w = 0.6 * (1.0 + next);
			double h = 1.8 * (1.0 + next);
			AABB box = new AABB(p.getX() - w / 2, p.getY(), p.getZ() - w / 2, p.getX() + w / 2, p.getY() + h, p.getZ() + w / 2);
			if (!p.level().noCollision(p, box)) {
				return;
			}
		}
		if (next <= 1.0e-4) {
			PowerToggles.clearModifier(p, Attributes.SCALE, SCALE_ID);
		} else {
			PowerToggles.modifier(p, Attributes.SCALE, SCALE_ID, next, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		}
	}

	/**
	 * The block he looks at bursts (natural ground is taken away for a few seconds, then put back) and its pieces fly on
	 * in a cone. Returns the number hit (-1 refused).
	 */
	public static int shard(ServerPlayer p) {
		if (!canStart(p, OverhaulConfig.SHARD_COST)) {
			return -1;
		}
		BlockHitResult hr = AbilityHelpers.raycastBlock(p, OverhaulConfig.SHARD_BLOCK_RANGE);
		ServerLevel level = p.serverLevel();
		if (hr == null || hr.getType() != HitResult.Type.BLOCK || level.getBlockState(hr.getBlockPos()).isAir()) {
			Quirks.say(p, "message.projecthero.overhaul.no_block", ChatFormatting.GRAY);
			return -1;
		}
		spend(p, OverhaulConfig.SHARD_COST);
		BlockPos pos = hr.getBlockPos();
		BlockState state = level.getBlockState(pos);
		Vec3 apex = Vec3.atCenterOf(pos);
		BlockParticleOption bits = new BlockParticleOption(ParticleTypes.BLOCK, state);
		level.sendParticles(bits, apex.x, apex.y, apex.z, 30, 0.4, 0.4, 0.4, 0.2);
		if (GeoTerrain.canEdit(p, level, pos) && GeoTerrain.diggable(level, pos) && !GeoTerrain.isTemp(level, pos)) {
			GeoTerrain.pocket(level, pos, null, OverhaulConfig.SHARD_BLOCK_TTL, 0);
		}
		Vec3 axis = flatLook(p);
		double cos = Math.cos(Math.toRadians(OverhaulConfig.SHARD_HALF_ANGLE));
		// the shards themselves: a fan of block-bit streaks
		for (int i = -2; i <= 2; i++) {
			Vec3 ray = axis.yRot((float) Math.toRadians(i * OverhaulConfig.SHARD_HALF_ANGLE / 2.0)).add(0, 0.15, 0).normalize();
			AbilityHelpers.line(level, apex.add(0, 0.6, 0), apex.add(0, 0.6, 0).add(ray.scale(OverhaulConfig.SHARD_RANGE * 0.8)), bits, 1.5);
		}
		int n = 0;
		float dmg = OverhaulCombat.scaled(p, OverhaulConfig.SHARD_DAMAGE);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(apex, apex).inflate(OverhaulConfig.SHARD_RANGE),
				e -> OverhaulCombat.isTarget(p, e))) {
			Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(apex);
			Vec3 flat = new Vec3(to.x, 0, to.z);
			double d = to.length();
			if (d > OverhaulConfig.SHARD_RANGE || flat.lengthSqr() < 1.0e-4 || Math.abs(to.y) > 3.5) {
				continue;
			}
			if (flat.normalize().dot(axis) < cos) {
				continue;
			}
			if (OverhaulCombat.strike(p, e, apex, dmg, 0.6, 0.1)) {
				n++;
			}
		}
		touchSound(p);
		sound(p, SoundEvents.STONE_BREAK, 1.2f, 0.9f);
		sound(p, SoundEvents.ARROW_SHOOT, 0.9f, 0.6f);
		done(p, OverhaulConfig.SHARD, OverhaulConfig.SHARD_COOLDOWN, Overhaul.ANIM_SHARD);
		return n;
	}

	// ================================================================ the tick / lifecycle

	static void tick(ServerPlayer p) {
		long now = now(p);
		com.projecthero.mod.quirk.data.QuirkState st = Quirks.state(p);
		if (st.val(Overhaul.V_INIT) == 0L) {
			Quirks.setMeter(p, 100f);
			Quirks.setVal(p, Overhaul.V_INIT, 1L);
			st = Quirks.state(p);
		}
		Session s = session(p);
		long fusionUntil = st.val(Overhaul.V_FUSION);
		boolean fused = fusionUntil > now;
		if (fusionUntil != 0L && !fused) {
			endFusion(p);
		}
		if (fused) {
			applyFusion(p);
			if (now % 10 == 0) {
				Vec3 c = p.position().add(0, p.getBbHeight() * 0.5, 0);
				p.serverLevel().sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()),
						c.x, c.y, c.z, 2, 0.4, 0.6, 0.4, 0.0);
			}
		} else if (now - s.lastSpend >= OverhaulConfig.FOCUS_REGEN_DELAY || now < s.lastSpend) {
			Quirks.addMeter(p, OverhaulConfig.FOCUS_REGEN_PER_TICK);
		}
		tickScale(p, fused);
		if (s.safeFallUntil > now && !p.onGround()) {
			p.resetFallDistance();
		}
		if (!s.jobs.isEmpty()) {
			List<Job> due = new ArrayList<>();
			Iterator<Job> it = s.jobs.iterator();
			while (it.hasNext()) {
				Job j = it.next();
				if (j.at <= now) {
					due.add(j);
					it.remove();
				}
			}
			for (Job j : due) {
				j.run.run();
			}
		}
	}

	/** The quirk stops (death, logout, world change, Erased, revoke): Fusion ends at once, waiting moves are dropped. */
	static void stopAll(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		if (s != null) {
			s.jobs.clear();
		}
		if (Quirks.state(p).val(Overhaul.V_FUSION) != 0L) {
			endFusion(p);
		}
		clearFusionModifiers(p, true);
	}

	/** Fall damage is cancelled for a while after a Launch Ramp. */
	static float modifyIncomingDamage(ServerPlayer p, DamageSource source, float amount) {
		Session s = SESSIONS.get(p.getUUID());
		if (s != null && s.safeFallUntil > now(p) && source.is(DamageTypes.FALL)) {
			return 0f;
		}
		return amount;
	}

	/** True while Overhaul holds the quirk (used by the kit's checks). */
	static boolean holds(ServerPlayer p) {
		return Quirks.holds(p, Quirk.OVERHAUL);
	}

}
