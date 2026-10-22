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
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7 revamp (docs/QUIRK_REVAMP_V0167.md): Overhaul's twelve moves and the per-player sessions of the ones that play
 * out over a few ticks (the spikes rolling forward, the wall rising, the pits' lids, the Strike dash, Restore's wave).
 * Every public move is called by {@link OverhaulKit#handle} once its cooldown is ready, and by the gametests directly;
 * each returns whether it happened (refusals say why on the action bar and spend nothing).
 *
 * <p>Overhaul works by touch, with the gloves OFF ({@link Overhaul#V_GLOVES}; C toggles them). Every move spends Focus
 * ({@link Quirks#meter}). Every block he reshapes is temporary ({@link OverhaulTerrain}: the Geokinesis ledger, the
 * terrain-damage config, build rights).
 */
public final class OverhaulMoves {
	private record Job(long at, Runnable run) {
	}

	static final class Session {
		final List<Job> jobs = new ArrayList<>();
		long lastSpend = -1000L;
		/** The Overhaul Strike dash: its direction, and whether it has touched someone yet. */
		Vec3 strikeDir = Vec3.ZERO;
		boolean strikeHit;
		/** Every temporary block he placed (gametests / debugging). */
		final List<BlockPos> placed = new ArrayList<>();
		/** Terrain Surf: who its wave already threw aside. */
		final Set<Integer> surfHit = new HashSet<>();
		/** No fall damage until (Terrain Surf / Pillar Launch). */
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
		OverhaulRestore.clearSessionState();
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

	/** The hand-touch: a sharp clap of bare fingers and a crack of breaking matter. */
	private static void touchSound(ServerPlayer p) {
		sound(p, SoundEvents.PLAYER_ATTACK_STRONG, 0.7f, 1.55f);
		sound(p, SoundEvents.AMETHYST_BLOCK_HIT, 0.8f, 0.6f);
	}

	private static Vec3 flatLook(ServerPlayer p) {
		Vec3 l = p.getLookAngle();
		Vec3 f = new Vec3(l.x, 0, l.z);
		return f.lengthSqr() < 1.0e-4 ? Vec3.directionFromRotation(0f, p.getYRot()) : f.normalize();
	}

	// ================================================================ the rules every move shares

	/** Touch moves need the gloves off (says so). */
	static boolean bareHands(ServerPlayer p) {
		if (Quirks.state(p).val(Overhaul.V_GLOVES) == 0L) {
			return true;
		}
		Quirks.say(p, "message.projecthero.overhaul.gloved", ChatFormatting.RED);
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
		return bareHands(p) && hasFocus(p, cost);
	}

	/** Spends Focus; spent to nothing, his focus breaks for a moment (no moves but Shift+C). */
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

	/** One temporary block, remembered for Restore. */
	private static boolean place(ServerPlayer p, ServerLevel level, BlockPos pos, BlockState state, int ttl) {
		if (!OverhaulTerrain.place(p, level, pos, state, ttl)) {
			return false;
		}
		remember(p, pos);
		return true;
	}

	private static int spike(ServerPlayer p, ServerLevel level, BlockPos cell, int height, int ttl) {
		int n = OverhaulTerrain.spike(p, level, cell, height, ttl);
		for (int i = 0; i < n; i++) {
			remember(p, cell.above(i));
		}
		return n;
	}

	private static void remember(ServerPlayer p, BlockPos pos) {
		List<BlockPos> list = session(p).placed;
		list.add(pos.immutable());
		if (list.size() > 512) {
			list.subList(0, list.size() - 512).clear();
		}
	}

	/** A palm pressed to the ground: the floor cracks in a ring of its own cubes. */
	private static void palmToGround(ServerPlayer p, BlockPos cell) {
		ServerLevel level = p.serverLevel();
		BlockState g = level.getBlockState(cell.below());
		OverhaulTerrain.burstAt(level, cell, g, 16);
		Vec3 c = Vec3.atBottomCenterOf(cell);
		for (int i = 0; i < 12; i++) {
			double a = i * Math.PI / 6;
			level.sendParticles(OverhaulCombat.RED, c.x + Math.cos(a) * 0.9, c.y + 0.05, c.z + Math.sin(a) * 0.9, 1, 0.05, 0.0, 0.05, 0.0);
		}
		level.playSound(null, cell, SoundEvents.DEEPSLATE_BREAK, SoundSource.PLAYERS, 1.0f, 0.6f);
	}

	// ================================================================ R: Disassemble / Shift+R: Reassemble

	/** A touch: {@link OverhaulConfig#DISASSEMBLE_DAMAGE}; the body bursts apart, and a body it kills bursts on everyone near. */
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
		ServerLevel level = p.serverLevel();
		Vec3 c = t.position().add(0, t.getBbHeight() * 0.55, 0);
		OverhaulCombat.strike(p, t, p.position(), OverhaulCombat.scaled(p, OverhaulConfig.DISASSEMBLE_DAMAGE), OverhaulConfig.DISASSEMBLE_KNOCKBACK, 0.1);
		OverhaulCombat.disassembleFx(level, t, 2f);
		if (!t.isAlive()) {
			// the body comes apart: its pieces hit everything around it
			OverhaulCombat.disassembleFx(level, t, 3.5f);
			BlockParticleOption shard = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.NETHER_WART_BLOCK.defaultBlockState());
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, t.getBoundingBox().inflate(OverhaulConfig.DISASSEMBLE_BURST_RADIUS),
					e -> e != t && OverhaulCombat.isTarget(p, e) && e.distanceTo(t) <= OverhaulConfig.DISASSEMBLE_BURST_RADIUS)) {
				AbilityHelpers.line(level, c, e.position().add(0, e.getBbHeight() * 0.5, 0), shard, 2.0);
				OverhaulCombat.strike(p, e, c, OverhaulCombat.scaled(p, OverhaulConfig.DISASSEMBLE_BURST_DAMAGE), 0.6, 0.15);
			}
			level.playSound(null, c.x, c.y, c.z, SoundEvents.STONE_BREAK, SoundSource.PLAYERS, 1.1f, 0.6f);
			level.playSound(null, c.x, c.y, c.z, SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 1.0f, 0.5f);
		}
		done(p, OverhaulConfig.DISASSEMBLE, OverhaulConfig.DISASSEMBLE_COOLDOWN, Overhaul.ANIM_DISASSEMBLE);
		return true;
	}

	private static boolean ally(ServerPlayer p, LivingEntity e) {
		return e != p && e.isAlive() && (Squads.areAllies(p, e) || HeroTargets.isOwnedBy(e, p) || HeroTargets.isFriendlyPet(p, e));
	}

	/** Takes a body apart and puts it back whole: full health, harmful effects and fire gone. */
	static void restoreBody(ServerLevel level, LivingEntity e) {
		e.setHealth(e.getMaxHealth());
		e.clearFire();
		cleanse(e);
		Vec3 c = e.position().add(0, e.getBbHeight() * 0.5, 0);
		// it comes apart into cubes...
		OverhaulCombat.disassembleFx(level, e, 1.5f);
		// ...and the pieces fly back in, mended
		for (int i = 0; i < 18; i++) {
			double a = i * Math.PI * 2 / 18;
			Vec3 from = c.add(Math.cos(a) * 1.4, (i % 3 - 1) * 0.5, Math.sin(a) * 1.4);
			Vec3 v = c.subtract(from).scale(0.2);
			level.sendParticles(OverhaulCombat.MEND, from.x, from.y, from.z, 0, v.x, v.y, v.z, 1.0);
		}
		level.sendParticles(OverhaulCombat.MEND, c.x, c.y, c.z, 18, e.getBbWidth() * 0.4, e.getBbHeight() * 0.35, e.getBbWidth() * 0.4, 0.0);
		level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 6, 0.3, 0.5, 0.3, 0.02);
		level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.2f, 1.2f);
		level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.STONE_BREAK, SoundSource.PLAYERS, 0.6f, 1.4f);
	}

	/** Harmful effects gone. Returns how many. */
	static int cleanse(LivingEntity e) {
		List<Holder<MobEffect>> bad = new ArrayList<>();
		for (MobEffectInstance inst : e.getActiveEffects()) {
			if (inst.getEffect().value().getCategory() == MobEffectCategory.HARMFUL
					&& !inst.getEffect().equals(com.projecthero.mod.hero.PowerSuppression.ErasedEffect.HOLDER)) {
				bad.add(inst.getEffect());
			}
		}
		bad.forEach(e::removeEffect);
		return bad.size();
	}

	/**
	 * Reassembles himself -- or the ally he looks at -- to full health, and Restores the broken ground around him
	 * ({@link #restoreGround}). Once per 60 s. Returns who (null refused).
	 */
	public static @Nullable LivingEntity reassemble(ServerPlayer p) {
		if (!canStart(p, OverhaulConfig.REASSEMBLE_COST)) {
			return null;
		}
		LivingEntity looked = AbilityHelpers.raycastEntity(p, OverhaulConfig.REASSEMBLE_RANGE);
		LivingEntity body = looked != null && ally(p, looked) ? looked : p;
		spend(p, OverhaulConfig.REASSEMBLE_COST);
		restoreBody(p.serverLevel(), body);
		restoreGround(p);
		touchSound(p);
		Quirks.say(p, "message.projecthero.overhaul.reassembled", ChatFormatting.AQUA, body.getDisplayName());
		done(p, OverhaulConfig.REASSEMBLE, OverhaulConfig.REASSEMBLE_COOLDOWN, Overhaul.ANIM_REASSEMBLE);
		return body;
	}

	// ================================================================ G: Ground Spikes / Shift+G: Spike Field

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

	/** He touches the floor: a line of stone spikes erupts toward the target (or the way he looks), one block a tick. */
	public static boolean spikeWave(ServerPlayer p) {
		if (!canStart(p, OverhaulConfig.SPIKE_WAVE_COST)) {
			return false;
		}
		BlockPos start = groundUnder(p);
		if (!needGround(p, start)) {
			return false;
		}
		spend(p, OverhaulConfig.SPIKE_WAVE_COST);
		LivingEntity aim = OverhaulCombat.aimed(p, 18.0, 25.0);
		Vec3 dir = aim == null ? flatLook(p) : new Vec3(aim.getX() - p.getX(), 0, aim.getZ() - p.getZ());
		dir = dir.lengthSqr() < 1.0e-4 ? flatLook(p) : dir.normalize();
		final Vec3 d = dir;
		final int[] y = { start.getY() };
		final boolean[] stopped = { false };
		final Set<Integer> hit = new HashSet<>();
		float dmg = OverhaulCombat.scaled(p, OverhaulConfig.SPIKE_WAVE_DAMAGE);
		palmToGround(p, start);
		touchSound(p);
		// the palm lands on tick 3 of the pose; the ground answers from tick 4
		for (int i = 1; i <= OverhaulConfig.SPIKE_WAVE_LENGTH; i++) {
			final int step = i;
			later(p, 2 + i, () -> {
				if (stopped[0]) {
					return;
				}
				ServerLevel level = p.serverLevel();
				BlockPos cell = OverhaulTerrain.groundCell(level, Mth.floor(start.getX() + 0.5 + d.x * step), y[0],
						Mth.floor(start.getZ() + 0.5 + d.z * step));
				if (cell == null || Math.abs(cell.getY() - y[0]) > 2) {
					stopped[0] = true;
					return;
				}
				y[0] = cell.getY();
				spikeHits(p, Vec3.atBottomCenterOf(cell), OverhaulConfig.SPIKE_HIT_RADIUS, dmg, OverhaulConfig.SPIKE_LIFT, hit);
				spike(p, level, cell, step % 3 == 0 ? 3 : 2, OverhaulConfig.SPIKE_TTL);
				// a flanking spike on alternate sides every other step (a jagged line, as in the anime)
				if (step % 2 == 0) {
					Vec3 side = new Vec3(-d.z, 0, d.x).scale((step / 2) % 2 == 0 ? 1.0 : -1.0);
					BlockPos flank = OverhaulTerrain.groundCell(level, Mth.floor(start.getX() + 0.5 + d.x * step + side.x),
							y[0], Mth.floor(start.getZ() + 0.5 + d.z * step + side.z));
					if (flank != null && Math.abs(flank.getY() - y[0]) <= 1) {
						spike(p, level, flank, 2, OverhaulConfig.SPIKE_TTL - 4);
					}
				}
			});
		}
		done(p, OverhaulConfig.SPIKE_WAVE, OverhaulConfig.SPIKE_WAVE_COOLDOWN, Overhaul.ANIM_SPIKE_WAVE);
		return true;
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
		palmToGround(p, center);
		touchSound(p);
		later(p, 4, () -> ring(p, center, OverhaulConfig.SPIKE_FIELD_INNER, 2, 1.6));
		later(p, 4, () -> spikeHitsRing(p, c, 0.8, OverhaulConfig.SPIKE_FIELD_OUTER + 0.6, dmg, hit));
		later(p, 7, () -> ring(p, center, OverhaulConfig.SPIKE_FIELD_OUTER, 3, 1.6));
		later(p, 7, () -> sound(p, SoundEvents.DRIPSTONE_BLOCK_BREAK, 1.2f, 0.5f));
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
		Set<Long> seen = new HashSet<>();
		for (int i = 0; i < n; i++) {
			double a = i * 2 * Math.PI / n;
			int x = Mth.floor(center.getX() + 0.5 + Math.cos(a) * radius);
			int z = Mth.floor(center.getZ() + 0.5 + Math.sin(a) * radius);
			BlockPos cell = OverhaulTerrain.groundCell(level, x, center.getY(), z);
			if (cell == null || Math.abs(cell.getY() - center.getY()) > 2 || !seen.add(cell.asLong())) {
				continue;
			}
			spike(p, level, cell, height, OverhaulConfig.SPIKE_TTL);
		}
	}

	// ================================================================ X: Wall Burst / Shift+X: Disassemble Terrain

	/** The ground ahead bursts up into a wall ({@link OverhaulConfig#WALL_WIDTH} x {@link OverhaulConfig#WALL_HEIGHT}), row by row. */
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
		palmToGround(p, under);
		touchSound(p);
		// whatever stands where the wall bursts up is thrown back and hurt (the wall then rises around it)
		Vec3 wallMid = Vec3.atBottomCenterOf(base);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(wallMid, wallMid).inflate(half + 0.6, 2.5, 1.0),
				e -> OverhaulCombat.isTarget(p, e))) {
			Vec3 away = Vec3.atLowerCornerOf(facing.getNormal());
			OverhaulCombat.strike(p, e, e.position().subtract(away), OverhaulCombat.scaled(p, OverhaulConfig.WALL_BURST_DAMAGE), 0.9, 0.35);
		}
		for (int h = 0; h < OverhaulConfig.WALL_HEIGHT; h++) {
			final int row = h;
			later(p, 3 + h * 2, () -> {
				for (BlockPos cell : columns) {
					BlockState ground = level.getBlockState(cell.below());
					BlockPos at = cell.above(row);
					OverhaulTerrain.burstAt(level, at, OverhaulTerrain.material(ground), 6);
					if (row == 0 || level.getBlockState(at.below()).isSolid()) {
						place(p, level, at, OverhaulTerrain.material(ground), OverhaulConfig.WALL_TTL - row * 2);
					}
				}
				level.playSound(null, base, SoundEvents.STONE_PLACE, SoundSource.PLAYERS, 1.0f, 0.6f + row * 0.1f);
				level.playSound(null, base, SoundEvents.DEEPSLATE_BREAK, SoundSource.PLAYERS, 0.9f, 0.6f);
			});
		}
		Quirks.setVal(p, Overhaul.V_WALL, now(p) + OverhaulConfig.WALL_TTL);
		done(p, OverhaulConfig.WALL, OverhaulConfig.WALL_COOLDOWN, Overhaul.ANIM_WALL);
		return true;
	}

	/** Where Disassemble Terrain lands: the creature he aims at, else the block he looks at. */
	private static @Nullable Vec3 pitCentre(ServerPlayer p) {
		LivingEntity t = OverhaulCombat.aimed(p, OverhaulConfig.PIT_RANGE, 12.0);
		if (t != null) {
			return t.position();
		}
		BlockHitResult hr = AbilityHelpers.raycastBlock(p, OverhaulConfig.PIT_RANGE);
		return hr == null || hr.getType() != HitResult.Type.BLOCK ? null : hr.getLocation();
	}

	/** One creature's pit: the floor under it drops {@link OverhaulConfig#PIT_DEPTH} blocks, then a lid closes over it. */
	private static boolean dropIntoPit(ServerPlayer p, LivingEntity t) {
		if (OverhaulCombat.isBoss(t) || t.getBbWidth() > OverhaulConfig.PIT_MAX_WIDTH || t.getBbHeight() > OverhaulConfig.PIT_MAX_HEIGHT) {
			return false;
		}
		ServerLevel level = p.serverLevel();
		BlockPos feet = BlockPos.containing(t.getX(), t.getY() + 0.2, t.getZ());
		List<BlockPos> hole = new ArrayList<>();
		for (int i = 1; i <= OverhaulConfig.PIT_DEPTH; i++) {
			hole.add(feet.below(i));
		}
		BlockPos floor = feet.below(OverhaulConfig.PIT_DEPTH + 1);
		BlockState feetState = level.getBlockState(feet);
		boolean ok = (t.onGround() || t.getY() - feet.getY() < 0.25) && (feetState.isAir() || feetState.canBeReplaced()) && feetState.getFluidState().isEmpty()
				&& level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP) && GeoTerrain.canEdit(p, level, feet);
		for (BlockPos h : hole) {
			ok &= GeoTerrain.canEdit(p, level, h) && GeoTerrain.diggable(level, h) && !GeoTerrain.isTemp(level, h);
		}
		if (!ok) {
			return false;
		}
		BlockState lid = OverhaulTerrain.material(level.getBlockState(hole.get(0)));
		for (BlockPos h : hole) {
			OverhaulTerrain.burstAt(level, h, level.getBlockState(h), 12);
			GeoTerrain.pocket(level, h, t, OverhaulConfig.PIT_TICKS + 4, 10);
			remember(p, h);
		}
		BlockPos bottom = hole.get(hole.size() - 1);
		t.teleportTo(bottom.getX() + 0.5, bottom.getY(), bottom.getZ() + 0.5);
		t.setDeltaMovement(Vec3.ZERO);
		t.hurtMarked = true;
		OverhaulCombat.strike(p, t, t.position(), OverhaulCombat.scaled(p, OverhaulConfig.PIT_DAMAGE), 0.0, 0.0);
		level.playSound(null, feet, SoundEvents.GRAVEL_BREAK, SoundSource.PLAYERS, 1.2f, 0.6f);
		later(p, 4, () -> {
			place(p, level, feet, lid, OverhaulConfig.PIT_TICKS - 4);
			OverhaulTerrain.burstAt(level, feet, lid, 10);
			level.playSound(null, feet, SoundEvents.STONE_PLACE, SoundSource.PLAYERS, 1.0f, 0.6f);
		});
		return true;
	}

	/**
	 * The floor breaks out from under every creature near where he aims (bosses and anything too big for a 1x1x2 hole
	 * stay up): each drops into its own pit, which closes over it, and later the ground closes back up and pushes it out.
	 * Returns the creatures caught (empty: refused).
	 */
	public static List<LivingEntity> pit(ServerPlayer p) {
		if (!canStart(p, OverhaulConfig.PIT_COST)) {
			return List.of();
		}
		Vec3 centre = pitCentre(p);
		if (centre == null) {
			Quirks.say(p, "message.projecthero.overhaul.no_target", ChatFormatting.GRAY);
			return List.of();
		}
		ServerLevel level = p.serverLevel();
		List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, new AABB(centre, centre).inflate(OverhaulConfig.PIT_AREA_RADIUS, 2.5,
				OverhaulConfig.PIT_AREA_RADIUS), e -> OverhaulCombat.isTarget(p, e));
		near.sort((a, b) -> Double.compare(a.position().distanceToSqr(centre), b.position().distanceToSqr(centre)));
		List<LivingEntity> caught = new ArrayList<>();
		for (LivingEntity e : near) {
			if (caught.size() >= OverhaulConfig.PIT_MAX_TARGETS) {
				break;
			}
			if (dropIntoPit(p, e)) {
				caught.add(e);
			}
		}
		if (caught.isEmpty()) {
			Quirks.say(p, near.isEmpty() ? "message.projecthero.overhaul.no_target" : "message.projecthero.overhaul.too_big", ChatFormatting.GRAY);
			return List.of();
		}
		spend(p, OverhaulConfig.PIT_COST);
		BlockPos under = groundUnder(p);
		if (under != null) {
			palmToGround(p, under);
		}
		// a crack runs from his palm to the pits
		AbilityHelpers.line(level, p.position().add(0, 0.1, 0), centre.add(0, 0.1, 0),
				new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(BlockPos.containing(centre).below())), 3.0);
		touchSound(p);
		sound(p, SoundEvents.GENERIC_EXPLODE, 0.4f, 1.6f);
		Quirks.setVal(p, Overhaul.V_PIT, now(p) + OverhaulConfig.PIT_TICKS);
		done(p, OverhaulConfig.PIT, OverhaulConfig.PIT_COOLDOWN, Overhaul.ANIM_PIT);
		return caught;
	}

	// ================================================================ Z: Overhaul Strike / Shift+Z: Disarm

	/** A low dash along the ground, his bare hand reaching out: the first enemy it touches is disassembled. */
	public static boolean strike(ServerPlayer p) {
		if (!canStart(p, OverhaulConfig.STRIKE_COST)) {
			return false;
		}
		spend(p, OverhaulConfig.STRIKE_COST);
		Session s = session(p);
		s.strikeDir = flatLook(p);
		s.strikeHit = false;
		Quirks.setVal(p, Overhaul.V_STRIKE, now(p) + OverhaulConfig.STRIKE_TICKS);
		AbilityHelpers.launchSelf(p, s.strikeDir.scale(OverhaulConfig.STRIKE_SPEED).add(0, 0.12, 0));
		sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.8f, 0.6f);
		sound(p, SoundEvents.ARMOR_EQUIP_LEATHER, 0.8f, 0.7f);
		// he may already be in reach
		tickStrike(p, s);
		done(p, OverhaulConfig.STRIKE, OverhaulConfig.STRIKE_COOLDOWN, Overhaul.ANIM_STRIKE);
		return true;
	}

	private static void tickStrike(ServerPlayer p, Session s) {
		long until = Quirks.state(p).val(Overhaul.V_STRIKE);
		if (until == 0L) {
			return;
		}
		if (now(p) >= until || s.strikeHit) {
			Quirks.setVal(p, Overhaul.V_STRIKE, 0L);
			return;
		}
		ServerLevel level = p.serverLevel();
		Vec3 hand = p.position().add(0, p.getBbHeight() * 0.6, 0).add(s.strikeDir.scale(0.9));
		// keep the dash going (vanilla friction eats it on the ground)
		Vec3 v = p.getDeltaMovement();
		if (p.onGround() && v.horizontalDistanceSqr() < OverhaulConfig.STRIKE_SPEED * OverhaulConfig.STRIKE_SPEED * 0.5) {
			AbilityHelpers.launchSelf(p, s.strikeDir.scale(OverhaulConfig.STRIKE_SPEED * 0.8).add(0, Math.max(v.y, 0.0), 0));
		}
		level.sendParticles(OverhaulCombat.RED, hand.x, hand.y, hand.z, 2, 0.1, 0.1, 0.1, 0.0);
		BlockPos under = BlockPos.containing(p.getX(), p.getY() - 0.2, p.getZ());
		BlockState g = level.getBlockState(under);
		if (!g.isAir()) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, g), p.getX(), p.getY() + 0.05, p.getZ(), 4, 0.25, 0.02, 0.25, 0.1);
		}
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(OverhaulConfig.STRIKE_HIT_RADIUS),
				e -> OverhaulCombat.isTarget(p, e))) {
			Vec3 to = e.position().subtract(p.position());
			if (new Vec3(to.x, 0, to.z).dot(s.strikeDir) < -0.3) {
				continue; // behind him
			}
			double d = to.lengthSqr();
			if (d < bestD) {
				bestD = d;
				best = e;
			}
		}
		if (best != null) {
			s.strikeHit = true;
			OverhaulCombat.strike(p, best, p.position(), OverhaulCombat.scaled(p, OverhaulConfig.STRIKE_DAMAGE), 1.0, 0.25);
			OverhaulCombat.disassembleFx(level, best, 2.5f);
			touchSound(p);
			level.playSound(null, best.getX(), best.getY(), best.getZ(), SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.PLAYERS, 0.6f, 1.2f);
			// he stops dead on the touch
			Vec3 vv = p.getDeltaMovement();
			AbilityHelpers.launchSelf(p, new Vec3(vv.x * 0.15, Math.max(vv.y, 0.0), vv.z * 0.15));
			Quirks.setVal(p, Overhaul.V_STRIKE, 0L);
		}
	}

	/**
	 * Touch what a creature holds -- or, empty-handed, one piece of its armour -- and it comes apart: destroyed outright on
	 * a mob, broken out of a player's grip (dropped) so nothing of theirs is lost for good. Not bosses. Returns what was
	 * taken (empty: nothing).
	 */
	public static ItemStack disarm(ServerPlayer p) {
		if (!canStart(p, OverhaulConfig.DISARM_COST)) {
			return ItemStack.EMPTY;
		}
		LivingEntity t = OverhaulCombat.aimed(p, OverhaulCombat.reach(p) + 0.5, 20.0);
		if (t == null || OverhaulCombat.isBoss(t)) {
			Quirks.say(p, t == null ? "message.projecthero.overhaul.no_target" : "message.projecthero.overhaul.too_big", ChatFormatting.GRAY);
			Quirks.setCooldown(p, OverhaulConfig.DISARM, OverhaulConfig.DISARM_MISS_COOLDOWN);
			return ItemStack.EMPTY;
		}
		EquipmentSlot slot = null;
		for (EquipmentSlot s : new EquipmentSlot[] { EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.CHEST, EquipmentSlot.HEAD,
				EquipmentSlot.LEGS, EquipmentSlot.FEET }) {
			if (!t.getItemBySlot(s).isEmpty()) {
				slot = s;
				break;
			}
		}
		if (slot == null) {
			Quirks.say(p, "message.projecthero.overhaul.nothing_held", ChatFormatting.GRAY);
			Quirks.setCooldown(p, OverhaulConfig.DISARM, OverhaulConfig.DISARM_MISS_COOLDOWN);
			return ItemStack.EMPTY;
		}
		spend(p, OverhaulConfig.DISARM_COST);
		ItemStack taken = t.getItemBySlot(slot).copy();
		t.setItemSlot(slot, ItemStack.EMPTY);
		if (t.isUsingItem()) {
			t.stopUsingItem();
		}
		ServerLevel level = p.serverLevel();
		Vec3 at = t.position().add(0, slot == EquipmentSlot.HEAD ? t.getBbHeight() * 0.9 : t.getBbHeight() * 0.55, 0);
		if (t instanceof Player) {
			ItemEntity drop = new ItemEntity(level, at.x, at.y, at.z, taken);
			drop.setPickUpDelay(40);
			level.addFreshEntity(drop);
		} else if (t instanceof Mob m) {
			m.setDropChance(slot, 0f);
		}
		level.sendParticles(new net.minecraft.core.particles.ItemParticleOption(ParticleTypes.ITEM, taken), at.x, at.y, at.z, 24, 0.25, 0.25, 0.25, 0.12);
		level.sendParticles(OverhaulCombat.RED, at.x, at.y, at.z, 8, 0.2, 0.2, 0.2, 0.0);
		touchSound(p);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 1.0f, 0.8f);
		done(p, OverhaulConfig.DISARM, OverhaulConfig.DISARM_COOLDOWN, Overhaul.ANIM_DISARM);
		return taken;
	}

	// ================================================================ V: Chronostasis Fusion / Shift+V: Restore

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
		for (int i = 0; i < 48; i++) {
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
		level.sendParticles(OverhaulCombat.RED_BIG, c.x, c.y, c.z, 16, 0.5, 0.8, 0.5, 0.0);
		touchSound(p);
		sound(p, SoundEvents.STONE_BREAK, 1.2f, 0.5f);
		sound(p, SoundEvents.ANVIL_LAND, 0.4f, 0.5f);
		sound(p, SoundEvents.WARDEN_HEARTBEAT, 1.2f, 0.7f);
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
	 * Restore (part of Reassemble since the slot rule): the broken ground around him puts itself back -- every block an
	 * explosion broke within {@link OverhaulConfig#RESTORE_RADIUS} in the last five minutes is rebuilt, nearest first, as
	 * a wave (the scattered debris -- those blocks' dropped items -- is drawn back in with it). Free (Reassemble pays).
	 * Returns the number of blocks it will rebuild.
	 */
	public static int restoreGround(ServerPlayer p) {
		ServerLevel level = p.serverLevel();
		List<OverhaulRestore.Broken> broken = OverhaulRestore.take(level, p.position(), OverhaulConfig.RESTORE_RADIUS, OverhaulConfig.RESTORE_MAX_BLOCKS);
		int n = broken.size();
		if (n == 0) {
			return 0;
		}
		sound(p, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2f, 0.7f);
		for (int i = 0; i < n; i++) {
			OverhaulRestore.Broken b = broken.get(i);
			int delay = 4 + (int) ((long) i * OverhaulConfig.RESTORE_WAVE_TICKS / Math.max(1, n));
			later(p, delay, () -> rebuild(p, level, b));
		}
		later(p, 4 + OverhaulConfig.RESTORE_WAVE_TICKS, () -> sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 1.2f, 1.1f));
		Quirks.say(p, "message.projecthero.overhaul.restored", ChatFormatting.AQUA, n);
		return n;
	}

	private static void rebuild(ServerPlayer p, ServerLevel level, OverhaulRestore.Broken b) {
		BlockPos pos = b.pos();
		BlockState now = level.getBlockState(pos);
		if (!level.hasChunkAt(pos) || !(now.isAir() || now.canBeReplaced()) || now.hasBlockEntity()
				|| !p.mayBuild() || !level.mayInteract(p, pos) || !level.getWorldBorder().isWithinBounds(pos)) {
			return;
		}
		// never into a living thing
		if (!level.getEntitiesOfClass(LivingEntity.class, new AABB(pos), e -> e.isAlive() && !e.isSpectator()).isEmpty()) {
			return;
		}
		level.setBlock(pos, b.state(), Block.UPDATE_ALL);
		// the debris it left is drawn back in: one dropped item of that block, if one lies near
		var item = b.state().getBlock().asItem();
		if (item != net.minecraft.world.item.Items.AIR) {
			for (ItemEntity ie : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(8.0), e -> e.getItem().is(item))) {
				ie.getItem().shrink(1);
				if (ie.getItem().isEmpty()) {
					ie.discard();
				}
				AbilityHelpers.line(level, ie.position(), Vec3.atCenterOf(pos), new BlockParticleOption(ParticleTypes.BLOCK, b.state()), 2.0);
				break;
			}
		}
		Vec3 c = Vec3.atCenterOf(pos);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, b.state()), c.x, c.y, c.z, 6, 0.3, 0.3, 0.3, 0.05);
		level.sendParticles(OverhaulCombat.MEND, c.x, c.y, c.z, 2, 0.3, 0.3, 0.3, 0.0);
		if (level.random.nextInt(4) == 0) {
			level.playSound(null, pos, b.state().getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.7f, 0.9f);
		}
	}

	// ================================================================ X: Terrain Surf / Shift+X: Pillar Launch (movement)

	/**
	 * The ground under him comes apart and reforms a step ahead, over and over: a wave of earth carries him along the way
	 * he looks for {@link OverhaulConfig#SURF_TICKS} (X again: off), throwing aside what it runs over. Returns true when
	 * it started (false: refused, or it was running and has stopped).
	 */
	public static boolean surf(ServerPlayer p) {
		if (Quirks.state(p).val(Overhaul.V_SURF) != 0L) {
			endSurf(p);
			return false;
		}
		if (!canStart(p, OverhaulConfig.SURF_COST)) {
			return false;
		}
		BlockPos under = groundUnder(p);
		if (!needGround(p, under)) {
			return false;
		}
		spend(p, OverhaulConfig.SURF_COST);
		Session s = session(p);
		s.surfHit.clear();
		s.safeFallUntil = now(p) + OverhaulConfig.SURF_TICKS + OverhaulConfig.SURF_SAFE_TICKS;
		Quirks.setVal(p, Overhaul.V_SURF, now(p) + OverhaulConfig.SURF_TICKS);
		palmToGround(p, under);
		touchSound(p);
		sound(p, SoundEvents.ROOTED_DIRT_BREAK, 1.0f, 0.6f);
		done(p, OverhaulConfig.SURF, OverhaulConfig.SURF_COOLDOWN, Overhaul.ANIM_SURF);
		return true;
	}

	static void endSurf(ServerPlayer p) {
		if (Quirks.state(p).val(Overhaul.V_SURF) == 0L) {
			return;
		}
		Quirks.setVal(p, Overhaul.V_SURF, 0L);
		Vec3 v = p.getDeltaMovement();
		AbilityHelpers.launchSelf(p, new Vec3(v.x * 0.4, Math.max(v.y, 0.0), v.z * 0.4));
		Quirks.setAnim(p, com.projecthero.mod.quirk.data.QuirkState.ANIM_NONE);
	}

	private static void tickSurf(ServerPlayer p, Session s) {
		long until = Quirks.state(p).val(Overhaul.V_SURF);
		if (until == 0L) {
			return;
		}
		if (now(p) >= until || p.isInWater() || p.isPassenger()) {
			endSurf(p);
			return;
		}
		ServerLevel level = p.serverLevel();
		Vec3 dir = flatLook(p);
		BlockPos cell = groundUnder(p);
		// ride the ground: forward, hugging the terrain (a step up or down at a time)
		double vy = cell == null ? -0.25 : Mth.clamp((cell.getY() + 0.15 - p.getY()) * 0.5, -0.35, 0.55);
		AbilityHelpers.launchSelf(p, new Vec3(dir.x * OverhaulConfig.SURF_SPEED, vy, dir.z * OverhaulConfig.SURF_SPEED));
		p.resetFallDistance();
		if (cell != null) {
			BlockState g = level.getBlockState(cell.below());
			// the wave: the ground bursting up under him and a short-lived ridge of it rising in his wake
			OverhaulTerrain.burstAt(level, cell, g, 8);
			level.sendParticles(OverhaulCombat.RED, p.getX(), p.getY() + 0.1, p.getZ(), 1, 0.3, 0.05, 0.3, 0.0);
			BlockPos wake = OverhaulTerrain.groundCell(level, Mth.floor(p.getX() - dir.x * 2.2), cell.getY(), Mth.floor(p.getZ() - dir.z * 2.2));
			if (wake != null && Math.abs(wake.getY() - cell.getY()) <= 1 && now(p) % 2 == 0) {
				place(p, level, wake, OverhaulTerrain.material(level.getBlockState(wake.below())), OverhaulConfig.SURF_WAKE_TTL);
			}
			if (now(p) % 4 == 0) {
				level.playSound(null, cell, SoundEvents.GRAVEL_BREAK, SoundSource.PLAYERS, 0.8f, 0.6f + level.random.nextFloat() * 0.2f);
			}
		}
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(0.8, 0.2, 0.8).move(dir.scale(0.8)),
				e -> OverhaulCombat.isTarget(p, e) && !s.surfHit.contains(e.getId()))) {
			s.surfHit.add(e.getId());
			Vec3 side = new Vec3(-dir.z, 0, dir.x);
			Vec3 from = e.position().subtract(side.scale(Math.signum(side.dot(e.position().subtract(p.position())) + 1.0e-3)));
			OverhaulCombat.strike(p, e, from, OverhaulCombat.scaled(p, OverhaulConfig.SURF_DAMAGE), 0.9, 0.45);
		}
	}

	/** The ground under him shoots up as a stone pillar and flings him high (no fall damage for a while). */
	public static boolean pillar(ServerPlayer p) {
		if (!canStart(p, OverhaulConfig.PILLAR_COST)) {
			return false;
		}
		BlockPos base = groundUnder(p);
		if (!needGround(p, base)) {
			return false;
		}
		spend(p, OverhaulConfig.PILLAR_COST);
		endSurf(p);
		ServerLevel level = p.serverLevel();
		BlockState mat = OverhaulTerrain.material(level.getBlockState(base.below()));
		Vec3 f = flatLook(p);
		AbilityHelpers.launchSelf(p, new Vec3(f.x * OverhaulConfig.PILLAR_FORWARD, OverhaulConfig.PILLAR_UP, f.z * OverhaulConfig.PILLAR_FORWARD));
		session(p).safeFallUntil = now(p) + OverhaulConfig.PILLAR_SAFE_TICKS;
		palmToGround(p, base);
		touchSound(p);
		sound(p, SoundEvents.PISTON_EXTEND, 1.0f, 0.5f);
		for (int h = 0; h < OverhaulConfig.PILLAR_HEIGHT; h++) {
			final int row = h;
			later(p, 3 + h, () -> { // he has risen clear of the cell by then (server-side position lags a tick or two)
				BlockPos at = base.above(row);
				OverhaulTerrain.burstAt(level, at, mat, 8);
				if (row == 0 || level.getBlockState(at.below()).isSolid()) {
					place(p, level, at, mat, OverhaulConfig.PILLAR_TTL - row * 2);
				}
				level.playSound(null, at, SoundEvents.STONE_PLACE, SoundSource.PLAYERS, 1.0f, 0.6f + row * 0.15f);
			});
		}
		done(p, OverhaulConfig.PILLAR, OverhaulConfig.PILLAR_COOLDOWN, Overhaul.ANIM_PILLAR);
		return true;
	}

	/** No fall damage while surfing and for a while after a surf / pillar launch. */
	static float modifyIncomingDamage(ServerPlayer p, net.minecraft.world.damagesource.DamageSource source, float amount) {
		Session s = SESSIONS.get(p.getUUID());
		if (s != null && s.safeFallUntil > now(p) && source.is(net.minecraft.world.damagesource.DamageTypes.FALL)) {
			return 0f;
		}
		return amount;
	}

	// ================================================================ Shift+C: Gloves

	/** The gloves on (touches held back, Focus refills twice as fast) or off. Returns true when they are now ON. */
	public static boolean gloves(ServerPlayer p) {
		boolean on = Quirks.state(p).val(Overhaul.V_GLOVES) == 0L;
		if (on && Overhaul.fused(p)) {
			endFusion(p);
		}
		if (on) {
			endSurf(p);
		}
		Quirks.setVal(p, Overhaul.V_GLOVES, on ? 1L : 0L);
		sound(p, SoundEvents.ARMOR_EQUIP_LEATHER, 1.0f, on ? 1.2f : 0.9f);
		if (!on) {
			// bare hands: a faint red shimmer as the Quirk wakes
			Vec3 h = AbilityHelpers.handPosition(p);
			p.serverLevel().sendParticles(OverhaulCombat.RED, h.x, h.y, h.z, 6, 0.15, 0.15, 0.15, 0.0);
		}
		Quirks.say(p, on ? "message.projecthero.overhaul.gloves_on" : "message.projecthero.overhaul.gloves_off", ChatFormatting.GRAY);
		done(p, OverhaulConfig.GLOVES, OverhaulConfig.GLOVES_COOLDOWN, on ? Overhaul.ANIM_GLOVES_ON : Overhaul.ANIM_GLOVES_OFF);
		return on;
	}

	// ================================================================ passive: the bare-hand touch

	/**
	 * A bare-handed melee hit with the gloves off (from {@code OverhaulMod}'s attack hook): the touch disassembles a
	 * little too -- {@link OverhaulConfig#TOUCH_PASSIVE_DAMAGE} a tick later (after vanilla's own hit), for Focus.
	 */
	public static void bareTouch(ServerPlayer p, LivingEntity t) {
		if (Quirks.state(p).val(Overhaul.V_GLOVES) != 0L || !p.getMainHandItem().isEmpty() || !OverhaulCombat.isTarget(p, t)
				|| Quirks.overloaded(p) || Quirks.meter(p) < OverhaulConfig.TOUCH_PASSIVE_COST || p.getAttackStrengthScale(0.5f) < 0.9f) {
			return;
		}
		spend(p, OverhaulConfig.TOUCH_PASSIVE_COST);
		later(p, 1, () -> {
			if (t.isAlive()) {
				OverhaulCombat.strike(p, t, p.position(), OverhaulCombat.scaled(p, OverhaulConfig.TOUCH_PASSIVE_DAMAGE), 0.0, 0.0);
			}
		});
		Quirks.setAnim(p, Overhaul.ANIM_TOUCH);
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
			if (now % 8 == 0) {
				Vec3 c = p.position().add(0, p.getBbHeight() * 0.5, 0);
				p.serverLevel().sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DEEPSLATE.defaultBlockState()),
						c.x, c.y, c.z, 2, 0.4, 0.6, 0.4, 0.0);
			}
		} else if (now - s.lastSpend >= OverhaulConfig.FOCUS_REGEN_DELAY || now < s.lastSpend) {
			float regen = OverhaulConfig.FOCUS_REGEN_PER_TICK * (st.val(Overhaul.V_GLOVES) != 0L ? OverhaulConfig.GLOVES_REGEN_MULT : 1f);
			Quirks.addMeter(p, regen);
		}
		tickScale(p, fused);
		tickStrike(p, s);
		tickSurf(p, s);
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
		if (Quirks.state(p).val(Overhaul.V_STRIKE) != 0L) {
			Quirks.setVal(p, Overhaul.V_STRIKE, 0L);
		}
		if (Quirks.state(p).val(Overhaul.V_SURF) != 0L) {
			Quirks.setVal(p, Overhaul.V_SURF, 0L);
		}
		if (Quirks.state(p).val(Overhaul.V_FUSION) != 0L) {
			endFusion(p);
		}
		clearFusionModifiers(p, true);
	}

	/** True while Overhaul holds the quirk (used by the kit's checks). */
	static boolean holds(ServerPlayer p) {
		return Quirks.holds(p, Quirk.OVERHAUL);
	}
}
