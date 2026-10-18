package com.projecthero.mod.hero.power.p09;

import static com.projecthero.mod.hero.power.p09.CryokinesisHandlers.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilityContext;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.revamp.BatchBScheduler;
import com.projecthero.mod.hero.visual.MutationVisuals;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.6 (user: "give all the elemental powers extra moves that are triggered by holding down the ability key"):
 * Cryokinesis's six <b>hold moves</b> ({@code HoldTap}: hold a key 0.6 s; a tap does exactly what it did before, Shift
 * moves are unchanged). Each has its own Cold cost and cooldown timer (the {@code *_cd} resources, ticked down by the
 * power's upkeep):
 * <ul>
 * <li>R -- <b>Ice Javelin</b>: a big ice spear forms in your raised hand for 0.5 s, then flies: pierces up to 4 creatures,
 * 18 damage and 3 Frost each. 25 Cold, 6 s.</li>
 * <li>G -- <b>Frozen Ground</b>: a sheet of ice spreads 10 blocks round you for 8 s; enemies on it slip about, are
 * slowed and take 1 Frost every 2 s. 30 Cold, 16 s.</li>
 * <li>X -- <b>Ice Wings</b>: beating wings of ice for 10 s of the shared directional flight, a frost trail behind you.
 * 30 Cold, 25 s.</li>
 * <li>Z -- <b>Blizzard</b>: a 10-block-wide snowstorm on the spot you aim at (up to 30 blocks) for 8 s -- Slowness III,
 * 1 Frost a second, blinding snow. 45 Cold, 24 s.</li>
 * <li>V -- <b>Ice Fortress</b>: an ice dome (radius 4) round you, enemies inside shoved out, melting after 15 s (needs
 * terrain effects on, like the Glacier Wall; it only fills empty cells and puts back what was there). 30 Cold, 25 s.</li>
 * <li>C -- <b>Ice Golem</b>: a golem of ice fights for you for 20 s; its punches add Frost; it never touches you or your
 * squad. 50 Cold, 40 s.</li>
 * </ul>
 * What a caster has summoned (golem, fields, fortress cells) is tracked here and dropped on a power switch, a logout,
 * death and {@code ServerStateReset} ({@link #endAll} / {@link #clearSessionState}).
 */
public final class CryoHoldMoves {
	private static final class Summons {
		IceGolemEntity golem;
		final List<CryoFieldEntity> fields = new ArrayList<>();
		ServerLevel fortressLevel;
		final List<BlockPos> fortress = new ArrayList<>();
	}

	private static final Map<UUID, Summons> BY_PLAYER = new HashMap<>();
	private static final String WINGS_LANDING = "cryo_wings";
	private static final BlockState PACKED = Blocks.PACKED_ICE.defaultBlockState();
	private static final BlockState BLUE = Blocks.BLUE_ICE.defaultBlockState();

	private CryoHoldMoves() {
	}

	private static Summons summons(ServerPlayer p) {
		return BY_PLAYER.computeIfAbsent(p.getUUID(), k -> new Summons());
	}

	/** A hold move's cooldown check: true (and a message) while {@code timer} is still running. */
	private static boolean coolingDown(AbilityContext ctx, String timer, String holdId) {
		if (res(ctx.player(), timer) > 0.5f) {
			cooling(ctx, timer, ab(holdId) + ".hold");
			return true;
		}
		return false;
	}

	// ================================================================ R: Ice Javelin

	public static void javelin(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		if (coolingDown(ctx, "javelin_cd", "ice_bolt") || !pay(ctx, COST_JAVELIN)) {
			return;
		}
		set(p, "javelin_cd", JAVELIN_CD);
		ServerLevel level = ctx.level();
		IceJavelinEntity j = new IceJavelinEntity(level, p);
		level.addFreshEntity(j);
		MutationVisuals.play(p, "p09.javelin");
		AbilityHelpers.sound(p, SoundEvents.GLASS_PLACE, 1.0f, 0.5f);
		AbilityHelpers.sound(p, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0f, 1.4f);
	}

	// ================================================================ G: Frozen Ground

	public static void frozenGround(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		if (coolingDown(ctx, "ground_cd", "freeze_beam") || !pay(ctx, COST_GROUND)) {
			return;
		}
		set(p, "ground_cd", GROUND_CD);
		ServerLevel level = ctx.level();
		double gy = groundAt(level, p.position().add(0, 0.5, 0));
		Vec3 at = new Vec3(p.getX(), Double.isNaN(gy) ? p.getY() : gy, p.getZ());
		CryoFieldEntity f = CryoFieldEntity.spawn(level, p, CryoFieldEntity.GROUND, at, (float) GROUND_RADIUS, FIELD_TICKS);
		if (f != null) {
			track(p, f);
		}
		level.sendParticles(ICE_DUST, at.x, at.y + 0.1, at.z, 40, 1.5, 0.05, 1.5, 0.1);
		MutationVisuals.play(p, "p09.ground");
		AbilityHelpers.sound(p, SoundEvents.GLASS_BREAK, 1.2f, 0.4f);
	}

	private static void track(ServerPlayer p, CryoFieldEntity f) {
		List<CryoFieldEntity> list = summons(p).fields;
		list.removeIf(CryoFieldEntity::isRemoved);
		list.add(f);
	}

	// ================================================================ X: Ice Wings

	public static boolean winged(ServerPlayer p) {
		return res(p, "wings") > 0.5f;
	}

	public static void wings(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		if (winged(p)) {
			return;
		}
		if (p.getAbilities().instabuild || com.projecthero.mod.power.ThorPowers.isFlying(p) || p.isPassenger()) {
			return;
		}
		if (coolingDown(ctx, "wings_cd", "ice_slide") || !pay(ctx, COST_WINGS)) {
			return;
		}
		set(p, "wings_cd", WINGS_CD);
		endSlide(p);
		Power power = Powers.byKey(KEY);
		set(p, "wings", WINGS_TICKS);
		// HeroFlight leaves a flight alone while a timed self-flight resource is up: Ice Wings are their own flight
		ExperimentalPowers.setResource(p, power, "flame_fly_ticks", 1, 1);
		com.projecthero.mod.flight.FlightLanding.started(p, WINGS_LANDING);
		p.setAttached(ModAttachments.HERO_FLYING, true); // the shared directional flight steers it
		p.getAbilities().mayfly = true;
		p.getAbilities().flying = true;
		p.getAbilities().setFlyingSpeed(com.projecthero.mod.hero.power.HeroFlight.HERO_FLYING_SPEED);
		p.onUpdateAbilities();
		if (p.onGround()) {
			AbilityHelpers.launchSelf(p, new Vec3(p.getDeltaMovement().x, 0.7, p.getDeltaMovement().z));
		}
		MutationVisuals.play(p, "p09.wings");
		ctx.level().sendParticles(ICE_DUST, p.getX(), p.getY() + 1.2, p.getZ(), 30, 0.8, 0.5, 0.8, 0.1);
		AbilityHelpers.sound(p, SoundEvents.GLASS_PLACE, 1.2f, 0.6f);
		AbilityHelpers.sound(p, SoundEvents.PHANTOM_FLAP, 1.0f, 1.3f);
	}

	public static void stopWings(ServerPlayer p) {
		if (!winged(p)) {
			return;
		}
		Power power = Powers.byKey(KEY);
		set(p, "wings", 0);
		ExperimentalPowers.setResource(p, power, "flame_fly_ticks", 0, 1);
		com.projecthero.mod.flight.FlightLanding.ended(p, WINGS_LANDING);
		p.setAttached(ModAttachments.HERO_FLYING, false);
		if (!p.getAbilities().instabuild && !p.isSpectator()) {
			p.getAbilities().mayfly = false;
			p.getAbilities().flying = false;
		}
		p.getAbilities().setFlyingSpeed(0.05f);
		p.onUpdateAbilities();
		set(p, "fall_grace", 60);
		p.resetFallDistance();
		MutationVisuals.stopIf(p, "p09.wings");
		if (p.level() instanceof ServerLevel sl) {
			sl.sendParticles(ICE_DUST, p.getX(), p.getY() + 1.2, p.getZ(), 24, 0.7, 0.5, 0.7, 0.12);
		}
		AbilityHelpers.sound(p, SoundEvents.GLASS_BREAK, 0.8f, 1.3f);
	}

	static void wingsTick(ServerPlayer p, ServerLevel level) {
		float left = res(p, "wings");
		if (left < 0.5f) {
			return;
		}
		if (!p.isAlive() || p.isPassenger() || p.getAbilities().instabuild || left - 1 < 0.5f
				|| com.projecthero.mod.flight.FlightLanding.landed(p, WINGS_LANDING)) {
			stopWings(p);
			return;
		}
		set(p, "wings", left - 1);
		Power power = Powers.byKey(KEY);
		ExperimentalPowers.setResource(p, power, "flame_fly_ticks", 1, 1);
		p.setAttached(ModAttachments.HERO_FLYING, true);
		if (!p.getAbilities().flying || !p.getAbilities().mayfly) {
			p.getAbilities().mayfly = true;
			p.getAbilities().flying = true;
			p.onUpdateAbilities();
		}
		p.resetFallDistance();
		MutationVisuals.ensure(p, "p09.wings");
		// the frost trail: snow shed off the wingtips, an ice mist behind
		Vec3 look = p.getLookAngle();
		Vec3 side = new Vec3(-look.z, 0, look.x);
		side = side.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : side.normalize();
		Vec3 back = p.position().add(0, 1.2, 0).subtract(look.scale(0.6));
		if (p.tickCount % 2 == 0) {
			for (int s = -1; s <= 1; s += 2) {
				Vec3 tip = back.add(side.scale(s * 2.2)).add(0, 0.8, 0);
				level.sendParticles(ParticleTypes.SNOWFLAKE, tip.x, tip.y, tip.z, 1, 0.05, 0.05, 0.05, 0.0);
			}
		}
		if (p.tickCount % 2 == 0) {
			level.sendParticles(ICE_DUST, back.x, back.y, back.z, 2, 0.3, 0.2, 0.3, 0.0);
			level.sendParticles(ParticleTypes.WHITE_ASH, back.x, back.y - 0.4, back.z, 2, 0.4, 0.3, 0.4, 0.0);
		}
		if (p.tickCount % 9 == 0) {
			level.playSound(null, p.blockPosition(), SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 0.6f, 1.4f);
		}
		// water you skim over freezes
		if (AbilityHelpers.canGrief() && p.tickCount % 2 == 0) {
			BlockPos below = BlockPos.containing(p.getX(), p.getY() - 1.5, p.getZ());
			for (int dy = 0; dy < 3; dy++) {
				BlockPos c = below.below(dy);
				BlockState st = level.getBlockState(c);
				if (st.is(Blocks.WATER) && st.getFluidState().isSource() && level.getBlockState(c.above()).isAir()) {
					CryoIce.place(level, c, Blocks.ICE.defaultBlockState(), 10 * 20);
					break;
				}
			}
		}
	}

	// ================================================================ Z: Blizzard

	/** Where the Blizzard lands: the ground at the block you aim at within 30, else 30 blocks out. */
	public static Vec3 blizzardTarget(ServerPlayer p, ServerLevel level) {
		Vec3 eye = p.getEyePosition();
		Vec3 far = eye.add(p.getLookAngle().scale(BLIZZARD_RANGE));
		BlockHitResult bhr = level.clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, p));
		Vec3 at = bhr.getType() == HitResult.Type.MISS ? far : bhr.getLocation();
		LivingEntity t = AbilityHelpers.raycastEntity(p, eye.distanceTo(at));
		if (t != null) {
			at = t.position();
		}
		// down to the ground (up to 12 below the aimed point)
		for (int i = 0; i < 12; i++) {
			double gy = groundAt(level, at.subtract(0, i * 4, 0));
			if (!Double.isNaN(gy)) {
				return new Vec3(at.x, gy, at.z);
			}
		}
		return at;
	}

	public static void blizzard(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		if (coolingDown(ctx, "blizzard_cd", "glacier_spikes") || !pay(ctx, COST_BLIZZARD)) {
			return;
		}
		set(p, "blizzard_cd", BLIZZARD_CD);
		ServerLevel level = ctx.level();
		Vec3 at = blizzardTarget(p, level);
		CryoFieldEntity f = CryoFieldEntity.spawn(level, p, CryoFieldEntity.BLIZZARD, at, (float) BLIZZARD_RADIUS, FIELD_TICKS);
		if (f != null) {
			track(p, f);
		}
		MutationVisuals.play(p, "p09.blizzard");
		AbilityHelpers.line(level, castPoint(p), at.add(0, BLIZZARD_HEIGHT * 0.6, 0), ParticleTypes.SNOWFLAKE, 1.5);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.POWDER_SNOW_BREAK, SoundSource.PLAYERS, 1.6f, 0.4f);
		AbilityHelpers.sound(p, SoundEvents.ELYTRA_FLYING, 0.4f, 0.6f);
	}

	// ================================================================ V: Ice Fortress

	/** The dome's cells round {@code centre} (feet block): a hollow hemisphere of radius {@link CryokinesisHandlers#FORTRESS_RADIUS}, bottom row first. */
	public static List<List<BlockPos>> fortressRows(BlockPos centre) {
		List<List<BlockPos>> rows = new ArrayList<>();
		int r = (int) FORTRESS_RADIUS;
		for (int dy = 0; dy <= r; dy++) {
			List<BlockPos> row = new ArrayList<>();
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
					if (d > FORTRESS_RADIUS - 0.55 && d <= FORTRESS_RADIUS + 0.45) {
						row.add(centre.offset(dx, dy, dz));
					}
				}
			}
			rows.add(row);
		}
		return rows;
	}

	public static void fortress(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		if (!AbilityHelpers.canGrief()) {
			ctx.actionBar("message.projecthero.cryo.no_terrain");
			return;
		}
		if (coolingDown(ctx, "fortress_cd", "glacier_wall") || !pay(ctx, COST_FORTRESS)) {
			return;
		}
		set(p, "fortress_cd", FORTRESS_CD);
		ServerLevel level = ctx.level();
		Summons s = summons(p);
		dropFortress(s); // a new dome replaces the old one
		BlockPos centre = p.blockPosition();
		Vec3 c = Vec3.atBottomCenterOf(centre);
		List<List<BlockPos>> rows = fortressRows(centre);
		s.fortressLevel = level;
		List<BlockPos> claimed = s.fortress;
		shoveOut(p, level, c, false);
		BatchBScheduler.schedule(level, age -> {
			shoveOut(p, level, c, age >= 4);
			if (age % 2 != 0) {
				return age < rows.size() * 2;
			}
			int row = age / 2;
			if (row >= rows.size()) {
				return false;
			}
			for (BlockPos cell : rows.get(row)) {
				if (!level.getEntitiesOfClass(LivingEntity.class, new AABB(cell)).isEmpty()) {
					continue; // never seal anyone inside the ice itself
				}
				if (CryoIce.place(level, cell, row == rows.size() - 1 || (cell.getX() + cell.getZ()) % 3 == 0 ? BLUE : PACKED,
						FORTRESS_TTL - age)) {
					claimed.add(cell.immutable());
					if (level.random.nextInt(3) == 0) {
						level.sendParticles(ICE_DUST, cell.getX() + 0.5, cell.getY() + 0.5, cell.getZ() + 0.5, 2, 0.3, 0.3, 0.3, 0.05);
					}
				}
			}
			level.playSound(null, centre.above(row), SoundEvents.GLASS_PLACE, SoundSource.PLAYERS, 1.0f, 0.5f + row * 0.15f);
			return row < rows.size() - 1;
		});
		MutationVisuals.play(p, "p09.fortress");
		AbilityHelpers.sound(p, SoundEvents.POWDER_SNOW_PLACE, 1.2f, 0.5f);
	}

	/** Enemies inside the dome's radius are thrown out past its wall (bosses stand their ground). */
	private static void shoveOut(ServerPlayer p, ServerLevel level, Vec3 c, boolean lift) {
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, c, FORTRESS_RADIUS + 0.3)) {
			if (!notBoss(e) || e.getY() > c.y + FORTRESS_RADIUS + 0.5) {
				continue;
			}
			Vec3 away = new Vec3(e.getX() - c.x, 0, e.getZ() - c.z);
			away = away.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : away.normalize();
			AbilityHelpers.push(e, away.scale(0.9).add(0, 0.35, 0));
			if (lift) {
				// still inside as the walls close (rooted, too slow): set it down just outside instead of sealing it in
				Vec3 out = c.add(away.scale(FORTRESS_RADIUS + 1.3));
				if (level.noCollision(e, e.getBoundingBox().move(out.x - e.getX(), 0, out.z - e.getZ()))) {
					e.teleportTo(out.x, e.getY(), out.z);
				}
			}
		}
	}

	public static int fortressCells(ServerPlayer p) {
		Summons s = BY_PLAYER.get(p.getUUID());
		return s == null ? 0 : s.fortress.size();
	}

	/** The cells the caster's standing fortress claimed (a copy). */
	public static List<BlockPos> fortressCellList(ServerPlayer p) {
		Summons s = BY_PLAYER.get(p.getUUID());
		return s == null ? List.of() : List.copyOf(s.fortress);
	}

	private static void dropFortress(Summons s) {
		if (s.fortressLevel != null && !s.fortress.isEmpty()) {
			CryoIce.meltEarly(s.fortressLevel, s.fortress);
		}
		s.fortress.clear();
		s.fortressLevel = null;
	}

	// ================================================================ C: Ice Golem

	public static IceGolemEntity golem(ServerPlayer p) {
		Summons s = BY_PLAYER.get(p.getUUID());
		return s == null || s.golem == null || s.golem.isRemoved() ? null : s.golem;
	}

	public static void iceGolem(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		if (coolingDown(ctx, "golem_cd", "ice_blade") || !pay(ctx, COST_GOLEM)) {
			return;
		}
		set(p, "golem_cd", GOLEM_CD);
		ServerLevel level = ctx.level();
		Summons s = summons(p);
		if (s.golem != null && !s.golem.isRemoved()) {
			s.golem.shatter(); // one at a time
		}
		IceGolemEntity g = CryoEntities.ICE_GOLEM.create(level);
		if (g == null) {
			return;
		}
		Vec3 dir = com.projecthero.mod.hero.revamp.BatchBUtil.flatLook(p);
		Vec3 at = p.position().add(dir.scale(2.2));
		double gy = groundAt(level, at);
		g.moveTo(at.x, Double.isNaN(gy) ? p.getY() : gy, at.z, p.getYRot(), 0.0f);
		g.setYHeadRot(p.getYRot());
		g.setYBodyRot(p.getYRot());
		g.bind(p);
		level.addFreshEntity(g);
		s.golem = g;
		set(p, "golem", GOLEM_TICKS);
		level.sendParticles(ICE_DUST, g.getX(), g.getY() + 1.3, g.getZ(), 50, 0.6, 1.0, 0.6, 0.12);
		level.sendParticles(ParticleTypes.SNOWFLAKE, g.getX(), g.getY() + 1.3, g.getZ(), 30, 0.7, 1.0, 0.7, 0.05);
		MutationVisuals.play(p, "p09.golem");
		level.playSound(null, g.getX(), g.getY(), g.getZ(), SoundEvents.IRON_GOLEM_REPAIR, SoundSource.PLAYERS, 1.0f, 1.3f);
		level.playSound(null, g.getX(), g.getY(), g.getZ(), SoundEvents.GLASS_PLACE, SoundSource.PLAYERS, 1.4f, 0.5f);
	}

	/** Per tick: the golem's HUD timer. */
	static void summonTick(ServerPlayer p) {
		float g = res(p, "golem");
		if (g > 0.5f) {
			set(p, "golem", golem(p) == null ? 0 : g - 1);
		}
	}

	// ================================================================ cleanup

	/** Drops everything this caster has out: wings, golem, fields, the fortress. */
	public static void endAll(ServerPlayer p) {
		stopWings(p);
		set(p, "golem", 0);
		Summons s = BY_PLAYER.remove(p.getUUID());
		if (s == null) {
			return;
		}
		if (s.golem != null && !s.golem.isRemoved()) {
			s.golem.shatter();
		}
		for (CryoFieldEntity f : s.fields) {
			if (!f.isRemoved() && f.level() instanceof ServerLevel sl) {
				f.end(sl);
			}
		}
		dropFortress(s);
	}

	/** Logout: the same without touching the (leaving) player's resources. */
	public static void forget(ServerPlayer p) {
		stopWings(p);
		Summons s = BY_PLAYER.remove(p.getUUID());
		if (s == null) {
			return;
		}
		if (s.golem != null && !s.golem.isRemoved()) {
			s.golem.discard();
		}
		for (CryoFieldEntity f : s.fields) {
			if (!f.isRemoved()) {
				f.discard();
			}
		}
		dropFortress(s);
	}

	public static int tracked() {
		return BY_PLAYER.size();
	}

	public static void clearSessionState() {
		BY_PLAYER.clear();
	}

	static Component name(String holdId) {
		return Component.translatable(ab(holdId) + ".hold");
	}
}
