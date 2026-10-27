package com.projecthero.mod.quirk.hellflame;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.p05.GeoTerrain;
import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.QuirkCombos;
import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.fx.QuirkCamera;
import com.projecthero.mod.quirk.fx.QuirkFx;
import com.projecthero.mod.quirk.fx.QuirkParticles;
import com.projecthero.mod.quirk.fx.QuirkSounds;
import com.projecthero.mod.quirk.meter.QuirkMeters;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7, v0.17.1 (the Quirks revamp, {@code docs/QUIRKS_REVAMP_PROPOSAL.md} 3.1): Endeavor's moves, Body Heat and its
 * bands, the Overheat push and burnout, the Prominence Burn ultimate, the passives (hot hands, freeze immunity, melting,
 * the Forge Hand) and the per-player sessions of everything that runs over time.
 * <pre>
 *   key  tap                  Shift                     hold
 *   R    Hell Spider          Hell Spider: Mesh
 *   G    Flashfire Fist       Vanishing Fist            Flashfire Fist: Hellblast (charge, then the beam)
 *   X    Jet Burst            Flashfire Fist: Jet Burn  Fire Jet Flight
 *   Z    Hellflame Barrage    Hellflame Pillar
 *   C    Flame Wave           Flame Cloak               Plus Ultra (full gauge): Prominence Burn
 *   V    Heat Vent            Fire Wall
 * </pre>
 * {@link HellflameKit#handle} calls the public moves once their cooldown is ready; the gametests call them directly.
 * Sessions hold entity ids, never entities, and are dropped by {@link #forget} / {@link #clearSessionState}.
 *
 * <p><b>Body Heat</b> ({@link #heat}): every flame he throws heats him (paced, {@link HellflameMeter#BUILD}); it vents once
 * no fire has burned for a moment (faster wet). Kindle 0-39 x0.85, Blaze 40-79 x1.0 (hot hands, the flame beard), Inferno
 * 80-100 x1.3 (he takes 0.5 a second, snow and ice melt round him). At 100 he pushes past it -- the Overheat, 5 s at x1.5 and
 * 1 HP a second -- then burns out: 8 s with no fire and Slowness while the Heat drains to 40. Heat Vent (V) works through
 * all of it. After the Prominence Burn ultimate he is <b>burnt out</b>: 10 s, the Heat pinned at 100, nothing works.
 *
 * <p>Fire blocks: only the Hellflame Pillar and the end of the Hellblast place any, always temporary (the {@link GeoTerrain}
 * ledger takes them back), only with the terrain-damage config and {@code mobGriefing} on, where he may build, on solid ground
 * and never within two blocks of anything that burns ({@link #tryFire}). The Fire Wall is drawn flame, never blocks.
 */
public final class HellflameMoves {
	static final class Session {
		long lastFireAt = Long.MIN_VALUE / 2;
		boolean legacyChecked;
		// tap / hold keys: the tick a plain press went down (-1: none) and whether its hold move started
		final long[] pressAt = { -1L, -1L, -1L, -1L, -1L, -1L };
		final boolean[] holdStarted = new boolean[6];
		// X (hold): Fire Jet Flight
		boolean flight;
		boolean floating;
		// X: Jet Burst
		long burstUntil;
		Vec3 burstDir = Vec3.ZERO;
		// Shift+X: the Jet Burn dash
		long dashUntil;
		Vec3 dashDir = Vec3.ZERO;
		final Set<Integer> dashHit = new HashSet<>();
		// Shift+R: the Mesh
		long meshUntil;
		Vec3 meshCenter = Vec3.ZERO;
		final Set<Integer> meshed = new HashSet<>();
		// Shift+V: Fire Wall
		long wallUntil;
		Vec3 wallA = Vec3.ZERO;
		Vec3 wallB = Vec3.ZERO;
		// Shift+C: the Cloak
		long cloakUntil;
		// Z: the Barrage
		int barrageLeft;
		int barrageIndex;
		long barrageNext;
		// Shift+Z: the Pillar
		long pillarAt;
		Vec3 pillarPos = Vec3.ZERO;
		boolean pillarErupted;
		// G: Flashfire Fist
		int fistStep;
		long fistAt = -1000L;
		// hold G: Hellblast
		boolean charge;
		long chargeAt;
		boolean beam;
		long beamAt;
		long beamUntil;
		boolean released;
		// Shift+G: Vanishing Fist
		int vanishTarget = -1;
		long vanishStrikeAt;
		// the Prominence Burn ultimate, and the burnout after it
		long promAt;
		Vec3 promDir = Vec3.ZERO;
		final Set<Integer> promHit = new HashSet<>();
		long burnoutUntil;
		// the Forge Hand
		long forgeReadyAt;
		// the fire blocks he has lit: position -> when they go out
		final Map<Long, Long> fires = new java.util.HashMap<>();
	}

	private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();
	/** The Flame Cloak's +2 armour. */
	static final net.minecraft.resources.ResourceLocation CLOAK_ARMOR_ID = ProjectHeroMod.id("hellflame_cloak_armor");

	private HellflameMoves() {
	}

	static void clearSessionState() {
		SESSIONS.clear();
	}

	static void forget(UUID id) {
		SESSIONS.remove(id);
	}

	static Session session(ServerPlayer p) {
		return SESSIONS.computeIfAbsent(p.getUUID(), k -> new Session());
	}

	private static @Nullable Session peekSession(ServerPlayer p) {
		return SESSIONS.get(p.getUUID());
	}

	private static long now(ServerPlayer p) {
		return p.level().getGameTime();
	}

	private static void sound(ServerPlayer p, SoundEvent s, float vol, float pitch) {
		p.level().playSound(null, p.getX(), p.getY(), p.getZ(), s, SoundSource.PLAYERS, vol, pitch);
	}

	private static void soundAt(ServerLevel level, Vec3 at, SoundEvent s, float vol, float pitch) {
		level.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, vol, pitch);
	}

	private static Vec3 flatLook(ServerPlayer p) {
		Vec3 l = p.getLookAngle();
		Vec3 f = new Vec3(l.x, 0, l.z);
		return f.lengthSqr() < 1.0e-4 ? Vec3.directionFromRotation(0f, p.getYRot()) : f.normalize();
	}

	/** Where the flames leave his (right) hand. */
	private static Vec3 hand(ServerPlayer p) {
		return AbilityHelpers.handPosition(p);
	}

	/** A fist out in front: right ({@code right}) or left, at shoulder height along the look. */
	private static Vec3 fist(ServerPlayer p, boolean right) {
		Vec3 look = p.getLookAngle();
		Vec3 r = look.cross(new Vec3(0, 1, 0));
		r = r.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : r.normalize();
		return p.getEyePosition().add(look.scale(0.75)).add(r.scale(right ? 0.35 : -0.35)).add(0, -0.35, 0);
	}

	/** The first block on the way {@code from} + {@code dir} * {@code range}, else the full length. */
	private static Vec3 clip(ServerPlayer p, Vec3 from, Vec3 dir, double range) {
		Vec3 to = from.add(dir.scale(range));
		BlockHitResult hit = p.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		return hit.getType() == HitResult.Type.MISS ? to : hit.getLocation();
	}

	/** The top of the ground at or under {@code point} (searches {@code depth} blocks down), or null. */
	private static @Nullable Vec3 ground(ServerPlayer p, Vec3 point, double depth) {
		BlockHitResult hit = p.level().clip(new ClipContext(point.add(0, 0.5, 0), point.add(0, -depth, 0), ClipContext.Block.COLLIDER,
				ClipContext.Fluid.NONE, p));
		return hit.getType() == HitResult.Type.MISS ? null : hit.getLocation();
	}

	// ================================================================ damage by band

	/** {@code base} damage times the Heat band's multiplier (Kindle x0.85, Blaze x1.0, Inferno x1.3, the Overheat x1.5). */
	static float dmg(ServerPlayer p, float base) {
		return base * QuirkMeters.damageMult(p);
	}

	/** Size multiplier (1: kept for the moves that still scale with it). */
	static double size(ServerPlayer p) {
		return 1.0;
	}

	private static void cooldown(ServerPlayer p, String id, int ticks) {
		Quirks.setCooldown(p, id, ticks);
	}

	// ================================================================ Body Heat / Overheat / burnout

	/**
	 * Adds Body Heat from a fire move (raw, paced by {@link HellflameMeter#BUILD}; venting pauses). At 100 he pushes past it
	 * (the Overheat: {@link QuirkMeters#pushPast}); burnt out or in the ultimate the Heat is pinned. Returns the Heat.
	 */
	public static float heat(ServerPlayer p, float amount) {
		Session s = session(p);
		s.lastFireAt = now(p);
		if (s.burnoutUntil > now(p) || s.promAt != 0L || QuirkMeters.beyond(p)) {
			return Quirks.meter(p);
		}
		float h = Quirks.addMeterPaced(p, amount);
		if (h >= 100f && !Quirks.overloaded(p) && !QuirkMeters.pushPast(p)) {
			burnout(p);
		}
		return h;
	}

	/** True while he is pushed past 100 (the Overheat: x1.5, 1 HP a second, the burnout after). */
	public static boolean overheating(ServerPlayer p) {
		return QuirkMeters.beyond(p);
	}

	/**
	 * The burnout after the Overheat (the spec's crash): every move ends, no fire moves for {@link HellflameConfig.Heat#overheatTicks}
	 * while the Heat drains to {@link HellflameConfig.Heat#overheatRecoverTo}, Slowness II. Heat Vent still works.
	 */
	public static void burnout(ServerPlayer p) {
		boolean airborne = !p.onGround();
		int ticks = HellflameConfig.heat().overheatTicks;
		Quirks.overload(p, ticks); // runs stopAll
		Quirks.setMeter(p, 100f);
		p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 1, false, true, true));
		if (airborne) {
			p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0, false, false, true));
		}
		Quirks.setAnim(p, Hellflame.ANIM_OVERHEAT);
		ServerLevel level = p.serverLevel();
		Vec3 c = p.position().add(0, 1.0, 0);
		level.sendParticles(QuirkParticles.SMOKE_PUFF, c.x, p.getY() + 0.3, c.z, 14, 0.5, 0.3, 0.5, 0.03);
		QuirkFx.ring(level, p.position().add(0, 0.05, 0)).style(QuirkFx.RING_STEAM).colours(0xE8F0FF, 0x8A8A90).size(3f).thickness(0.7f).life(24).ground()
				.spawn();
		sound(p, SoundEvents.FIRE_EXTINGUISH, 1.0f, 0.6f);
		sound(p, HellflameSounds.VENT, 0.8f, 0.6f);
		QuirkCamera.vignette(p, 0xC02010, 0.45f, 30);
		Quirks.say(p, "message.projecthero.quirk.overloaded.hellflame", ChatFormatting.RED);
	}

	/** Test / compat: the old name -- straight to the burnout. */
	public static void overheat(ServerPlayer p) {
		burnout(p);
	}

	private static boolean wet(ServerPlayer p) {
		return p.isInWaterOrRain();
	}

	// ================================================================ fire blocks (temporary, safe)

	/**
	 * Lights a temporary fire at {@code cell} (or the air cell just above / below it on solid ground), taken back by the
	 * {@link GeoTerrain} ledger after {@code ttl}. Only with the terrain-damage config and {@code mobGriefing} on, where he may
	 * build ({@link GeoTerrain#canEdit}), never on ground that burns forever and never within two blocks of anything
	 * flammable (so it cannot spread). True if a fire is there now.
	 */
	public static boolean tryFire(ServerPlayer p, BlockPos cell, int ttl) {
		ServerLevel level = p.serverLevel();
		if (!AbilityHelpers.canGrief() || !level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
			return false;
		}
		BlockPos at = null;
		for (int dy = 1; dy >= -2; dy--) {
			BlockPos c = cell.offset(0, dy, 0);
			BlockState st = level.getBlockState(c);
			BlockPos under = c.below();
			if (st.isAir() && level.getBlockState(under).isFaceSturdy(level, under, Direction.UP)) {
				at = c.immutable();
				break;
			}
		}
		if (at == null || !GeoTerrain.canEdit(p, level, at) || level.getBlockState(at.below()).is(level.dimensionType().infiniburn())) {
			return false;
		}
		for (BlockPos q : BlockPos.betweenClosed(at.offset(-2, -1, -2), at.offset(2, 3, 2))) {
			BlockState st = level.getBlockState(q);
			if (st.ignitedByLava() || !st.getFluidState().isEmpty() && q.getY() >= at.getY()) {
				return false;
			}
		}
		if (!GeoTerrain.placeTemp(p, level, at, BaseFireBlock.getState(level, at), ttl)) {
			return false;
		}
		session(p).fires.put(at.asLong(), level.getGameTime() + ttl + 2);
		return true;
	}

	private static void firesTick(ServerPlayer p, Session s, long now) {
		if (s.fires.isEmpty() || now % 10 != 0) {
			return;
		}
		ServerLevel level = p.serverLevel();
		s.fires.entrySet().removeIf(en -> {
			if (now < en.getValue()) {
				return false;
			}
			BlockPos pos = BlockPos.of(en.getKey());
			if (level.hasChunkAt(pos) && level.getBlockState(pos).getBlock() instanceof BaseFireBlock) {
				level.removeBlock(pos, false);
			}
			return true;
		});
	}

	/** Test read: fires he has lit that are still on his clock. */
	public static int firesLit(ServerPlayer p) {
		Session s = peekSession(p);
		return s == null ? 0 : s.fires.size();
	}

	// ================================================================ tap / hold keys

	static void keyDown(ServerPlayer p, int slot) {
		Session s = session(p);
		s.pressAt[slot] = now(p);
		s.holdStarted[slot] = false;
	}

	/** The key came up: true if it was a tap (its hold never started); clears the press either way. */
	static boolean keyUp(ServerPlayer p, int slot) {
		Session s = peekSession(p);
		if (s == null || s.pressAt[slot] < 0L) {
			return false;
		}
		boolean tap = !s.holdStarted[slot];
		s.pressAt[slot] = -1L;
		s.holdStarted[slot] = false;
		return tap;
	}

	static boolean holdDue(ServerPlayer p, int slot) {
		Session s = peekSession(p);
		if (s == null || s.pressAt[slot] < 0L || s.holdStarted[slot]) {
			return false;
		}
		if (now(p) - s.pressAt[slot] >= HellflameConfig.HOLD_TICKS) {
			s.holdStarted[slot] = true;
			return true;
		}
		return false;
	}

	/** True while a move that holds every other key runs (the ultimate, the Vanishing Fist's strike). */
	public static boolean busy(ServerPlayer p) {
		Session s = peekSession(p);
		return s != null && (s.promAt != 0L || s.vanishTarget >= 0);
	}

	// ================================================================ X: Jet Burst / Shift+X: Jet Burn / hold X: Fire Jet Flight

	/** A flame-thrust dash along the look (works in the air), about 10 blocks, no damage. */
	public static void jetBurst(ServerPlayer p) {
		Session s = session(p);
		Vec3 look = p.getLookAngle();
		s.burstDir = s.flight || !p.onGround() ? look : new Vec3(look.x, Mth.clamp(look.y, -0.2, 0.6), look.z).normalize();
		s.burstUntil = now(p) + HellflameConfig.BURST_TICKS;
		Quirks.setVal(p, Hellflame.V_BURST, s.burstUntil);
		Quirks.setAnim(p, Hellflame.ANIM_BURST);
		cooldown(p, HellflameConfig.BURST, HellflameConfig.BURST_COOLDOWN);
		ServerLevel level = p.serverLevel();
		for (int side : new int[] { 1, -1 }) {
			QuirkFx.trail(level, p).colours(0xFFF4C0, 0xFF5A10).size(0.28f).size2(8f).life(20).vec(new Vec3(0.45 * side, 0.85, -0.25))
					.style(QuirkFx.RIBBON_FLAME).spawn();
		}
		QuirkFx.cone(level, p.position().add(0, 0.9, 0), s.burstDir.scale(-1)).colours(0xFFF4C0, 0xFF4A10).size(0.9f).length(3f).life(8).spawn();
		sound(p, HellflameSounds.JET, 1.1f, 1.1f);
		QuirkCamera.preset(p, QuirkCamera.Preset.DASH);
		heat(p, HellflameConfig.BURST_HEAT);
		AbilityHelpers.launchSelf(p, s.burstDir.scale(HellflameConfig.BURST_SPEED));
	}

	public static boolean bursting(ServerPlayer p) {
		Session s = peekSession(p);
		return s != null && s.burstUntil > now(p);
	}

	private static void burstTick(ServerPlayer p, Session s, long now) {
		if (s.burstUntil == 0L) {
			return;
		}
		p.resetFallDistance();
		if (now >= s.burstUntil) {
			s.burstUntil = 0L;
			Quirks.setVal(p, Hellflame.V_BURST, 0L);
			AbilityHelpers.launchSelf(p, p.getDeltaMovement().scale(0.35));
			return;
		}
		AbilityHelpers.launchSelf(p, s.burstDir.scale(HellflameConfig.BURST_SPEED));
		if (now % 2 == 0) {
			p.serverLevel().sendParticles(QuirkParticles.SMOKE_PUFF, p.getX(), p.getY() + 0.8, p.getZ(), 1, 0.2, 0.2, 0.2, 0.01);
		}
	}

	/** He rockets along the look on flame, his burning fist punching through everything in a 14-block line (16 each). */
	public static void jetBurn(ServerPlayer p) {
		Session s = session(p);
		Vec3 look = p.getLookAngle();
		Vec3 dir = s.flight ? look : new Vec3(look.x, Mth.clamp(look.y, -0.5, 0.5), look.z).normalize();
		s.dashDir = dir;
		s.dashUntil = now(p) + HellflameConfig.JET_DASH_TICKS;
		s.dashHit.clear();
		Quirks.setVal(p, Hellflame.V_DASH, s.dashUntil);
		Quirks.setAnim(p, Hellflame.ANIM_JET);
		cooldown(p, HellflameConfig.JET, HellflameConfig.JET_COOLDOWN);
		ServerLevel level = p.serverLevel();
		QuirkFx.trail(level, p).colours(0xFFFFFF, 0xFF5A10).size(0.5f).size2(10f).life(22).vec(new Vec3(0, 1.0, 0)).style(QuirkFx.RIBBON_FLAME).spawn();
		QuirkFx.ring(level, p.position().add(0, 0.9, 0)).flags(QuirkFx.F_VERTICAL).vec(dir).colours(0xFFFFFF, 0xFF7A1A).size(1.8f).thickness(0.3f)
				.life(8).spawn();
		sound(p, HellflameSounds.JET, 1.3f, 0.85f);
		QuirkCamera.preset(p, QuirkCamera.Preset.DASH);
		heat(p, HellflameConfig.JET_HEAT);
		if (s.dashUntil == 0L) {
			return; // burnt out on the spot
		}
		dashHits(p, s);
		AbilityHelpers.launchSelf(p, dir.scale(HellflameConfig.JET_DASH_SPEED));
	}

	public static boolean dashing(ServerPlayer p) {
		Session s = peekSession(p);
		return s != null && s.dashUntil > now(p);
	}

	/** Everything in reach of the fist this tick takes the burning punch (each once a dash). Returns how many. */
	private static int dashHits(ServerPlayer p, Session s) {
		Vec3 fist = p.position().add(0, 1.0, 0).add(s.dashDir.scale(0.8));
		ServerLevel level = p.serverLevel();
		int n = 0;
		for (LivingEntity e : HellflameCombat.around(p, fist, HellflameConfig.JET_REACH)) {
			if (!s.dashHit.add(e.getId())) {
				continue;
			}
			Vec3 c = e.position().add(0, e.getBbHeight() * 0.5, 0);
			if (HellflameCombat.strike(p, e, p.position(), dmg(p, HellflameConfig.JET_DAMAGE), HellflameConfig.JET_BURN_SECONDS, HellflameConfig.JET_KNOCKBACK,
					0.35, true)) {
				n++;
				QuirkFx.blast(level, c).colours(0xFFFFFF, 0xFF6A10).size(1.6f).life(12).flags(QuirkFx.F_NO_RING).spawn();
				soundAt(level, c, QuirkSounds.IMPACT_HEAVY, 1.0f, 1.0f);
				QuirkCamera.hitStop(p, 2);
			}
		}
		return n;
	}

	/** Test read: everything the running (or last) Jet Burn has hit. */
	public static Set<Integer> dashHit(ServerPlayer p) {
		Session s = peekSession(p);
		return s == null ? Set.of() : Set.copyOf(s.dashHit);
	}

	private static void dashTick(ServerPlayer p, Session s, long now) {
		if (s.dashUntil == 0L) {
			return;
		}
		p.resetFallDistance();
		if (now >= s.dashUntil) {
			s.dashUntil = 0L;
			Quirks.setVal(p, Hellflame.V_DASH, 0L);
			AbilityHelpers.launchSelf(p, p.getDeltaMovement().scale(0.3));
			return;
		}
		ServerLevel level = p.serverLevel();
		Vec3 front = p.position().add(0, 1.0, 0).add(s.dashDir.scale(0.9));
		if (now % 2 == 0) {
			QuirkFx.cone(level, front, s.dashDir).colours(0xFFFFFF, 0xFF5A10).size(0.9f).length(2.4f).life(5).spawn();
			Vec3 g = ground(p, p.position(), 2.0);
			if (g != null) {
				QuirkFx.decal(level, g).style(QuirkFx.DECAL_SCORCH).colour(0x1A120C).size(1.1f).life(260).spawn();
			}
		}
		dashHits(p, s);
		AbilityHelpers.launchSelf(p, s.dashDir.scale(HellflameConfig.JET_DASH_SPEED));
	}

	/** Toggles the flight; returns whether he flies now. */
	public static boolean toggleFlight(ServerPlayer p) {
		Session s = session(p);
		if (s.flight) {
			endFlight(p);
			return false;
		}
		s.flight = true;
		Quirks.setVal(p, Hellflame.V_FLIGHT, now(p));
		Quirks.setAnim(p, Hellflame.ANIM_FLIGHT);
		floatTolerance(p, s, true);
		if (p.onGround()) {
			AbilityHelpers.launchSelf(p, p.getDeltaMovement().add(0, 0.65, 0));
		}
		ServerLevel level = p.serverLevel();
		QuirkFx.ring(level, p.position().add(0, 0.05, 0)).colours(0xFFF4C0, 0xFF5A10).size(3f).thickness(0.5f).life(12).ground().spawn();
		QuirkFx.cone(level, p.position().add(0, 0.2, 0), new Vec3(0, -1, 0)).colours(0xFFF4C0, 0xFF4A10).size(1.2f).length(2.2f).life(10).spawn();
		flightTrails(p);
		sound(p, HellflameSounds.JET, 1.0f, 0.8f);
		s.lastFireAt = now(p);
		return true;
	}

	/** The jets: flame ribbons from both feet and both palms (renewed while he flies). */
	private static void flightTrails(ServerPlayer p) {
		ServerLevel level = p.serverLevel();
		for (int side : new int[] { 1, -1 }) {
			QuirkFx.trail(level, p).colours(0xFFF4C0, 0xFF4A10).size(0.24f).size2(8f).life(44).vec(new Vec3(0.13 * side, 0.05, 0)).style(QuirkFx.RIBBON_FLAME)
					.spawn();
			QuirkFx.trail(level, p).colours(0xFFF4C0, 0xFF4A10).size(0.18f).size2(6f).life(44).vec(new Vec3(0.5 * side, 0.75, -0.15))
					.style(QuirkFx.RIBBON_FLAME).spawn();
		}
	}

	public static void endFlight(ServerPlayer p) {
		Session s = peekSession(p);
		if (s == null || !s.flight) {
			return;
		}
		s.flight = false;
		Quirks.setVal(p, Hellflame.V_FLIGHT, 0L);
		Quirks.setCooldown(p, HellflameConfig.FLIGHT, HellflameConfig.FLIGHT_COOLDOWN);
		floatTolerance(p, s, false);
		p.resetFallDistance();
		sound(p, SoundEvents.FIRE_EXTINGUISH, 0.5f, 1.3f);
	}

	public static boolean flying(ServerPlayer p) {
		Session s = peekSession(p);
		return s != null && s.flight;
	}

	/** Heat a tick of flight at this horizontal speed (blocks a tick). */
	public static float flightHeatPerTick(double speed) {
		HellflameConfig.Heat h = HellflameConfig.heat();
		float perSecond = speed < 0.12 ? h.flightHoverPerSecond : speed > 1.1 ? h.flightSprintPerSecond : h.flightPerSecond;
		return perSecond / 20f;
	}

	private static void flightTick(ServerPlayer p, Session s, long now) {
		floatTolerance(p, s, true);
		p.resetFallDistance();
		if (p.getAbilities().flying && !p.getAbilities().instabuild && !p.isSpectator()) {
			p.getAbilities().flying = false;
			p.onUpdateAbilities();
		}
		long age = now - Hellflame.val(p, Hellflame.V_FLIGHT);
		if (age > 0 && age % 40 == 0) {
			flightTrails(p);
		}
		if (now % 16 == 0) {
			sound(p, HellflameSounds.ROAR, 0.35f, 0.7f + p.getRandom().nextFloat() * 0.15f);
		}
		if (now % 5 == 0) {
			p.serverLevel().sendParticles(QuirkParticles.SMOKE_PUFF, p.getX(), p.getY() - 0.1, p.getZ(), 1, 0.15, 0.1, 0.15, 0.01);
		}
		double speed = Math.hypot(p.getX() - p.xo, p.getZ() - p.zo);
		heat(p, flightHeatPerTick(speed));
	}

	/**
	 * Vanilla kicks a player who floats with no downward motion ("flying is not enabled"); the jets look just like that.
	 * {@code mayfly} for the duration, {@code flying} kept off, handed back after unless another power still flies him.
	 */
	private static void floatTolerance(ServerPlayer p, Session s, boolean on) {
		if (p.getAbilities().instabuild || p.isSpectator()) {
			return;
		}
		if (on) {
			if (!p.getAbilities().mayfly) {
				p.getAbilities().mayfly = true;
				p.getAbilities().flying = false;
				p.onUpdateAbilities();
				s.floating = true;
			}
			return;
		}
		if (s.floating) {
			s.floating = false;
			if (p.getAbilities().mayfly && !com.projecthero.mod.hero.power.HeroFlight.isFlying(p) && !com.projecthero.mod.power.ThorPowers.isFlying(p)
					&& !p.getAttachedOrElse(ModAttachments.IRON_MAN_FLYING, false) && !p.getAttachedOrElse(ModAttachments.REPULSOR_BOOTS_FLYING, false)) {
				p.getAbilities().mayfly = false;
				p.getAbilities().flying = false;
				p.onUpdateAbilities();
			}
		}
	}

	// ================================================================ R: Hell Spider / Shift+R: Mesh

	/**
	 * Five thin cutting strings flick out from his fingertips and sweep right to left across the look (drawn by the client
	 * from {@link Hellflame#V_SPIDER} / {@link Hellflame#VEC_SPIDER}); everything they cross is cut once, and the ground they
	 * rake is scorched in a line.
	 */
	public static int hellSpider(ServerPlayer p) {
		ServerLevel level = p.serverLevel();
		Vec3 from = hand(p);
		double sz = size(p);
		Set<Integer> hit = new HashSet<>();
		int n = 0;
		int threads = HellflameConfig.SPIDER_THREADS;
		int steps = HellflameConfig.SPIDER_SWEEP_STEPS;
		for (int k = 0; k < steps; k++) {
			double sweep = Mth.lerp(k / (double) (steps - 1), HellflameConfig.SPIDER_SWEEP_DEGREES, -HellflameConfig.SPIDER_SWEEP_DEGREES);
			for (int i = 0; i < threads; i++) {
				Vec3 dir = spiderDir(p.getYRot(), p.getXRot(), i, sweep);
				Vec3 end = clip(p, from, dir, HellflameConfig.SPIDER_RANGE * sz);
				for (LivingEntity e : HellflameCombat.alongPath(p, from, end, HellflameConfig.SPIDER_THREAD_RADIUS * sz, hit)) {
					if (HellflameCombat.strike(p, e, from, dmg(p, HellflameConfig.SPIDER_DAMAGE), HellflameConfig.SPIDER_BURN_SECONDS, 0.3, 0.0, true)) {
						n++;
						Vec3 c = e.position().add(0, e.getBbHeight() * 0.55, 0);
						level.sendParticles(HellflameCombat.WHITE_HOT, c.x, c.y, c.z, 6, 0.3, 0.05, 0.3, 0.0);
					}
				}
			}
		}
		// the scorch line the sweep leaves on the ground
		Vec3 flat = flatLook(p);
		for (double d = 2.0; d <= HellflameConfig.SPIDER_RANGE - 1.0; d += 1.6) {
			Vec3 g = ground(p, p.position().add(flat.scale(d)).add(0, 1.0, 0), 3.0);
			if (g != null) {
				QuirkFx.decal(level, g).style(QuirkFx.DECAL_SCORCH).colour(0x1A100A).size(0.75f).life(240).yaw(p.getYRot()).spawn();
			}
		}
		Quirks.setVal(p, Hellflame.V_SPIDER, now(p));
		Quirks.setVec(p, Hellflame.VEC_SPIDER, new Vec3(p.getYRot(), p.getXRot(), sz));
		Quirks.setAnim(p, Hellflame.ANIM_SPIDER);
		cooldown(p, HellflameConfig.SPIDER, HellflameConfig.SPIDER_COOLDOWN);
		sound(p, HellflameSounds.WHIP, 1.2f, 1.0f);
		if (n > 0) {
			QuirkCamera.preset(p, QuirkCamera.Preset.LIGHT_HIT);
		}
		heat(p, HellflameConfig.SPIDER_HEAT);
		return n;
	}

	/**
	 * CLIENT-SAFE: the direction of string {@code i} (0..threads-1) with the fan swept {@code sweepDegrees} (positive: to his
	 * right). Alternate strings tilt a little up and down: a fan, not a flat sheet.
	 */
	public static Vec3 spiderDir(float yaw, float pitch, int i, double sweepDegrees) {
		int threads = HellflameConfig.SPIDER_THREADS;
		double off = (i - (threads - 1) / 2.0) * HellflameConfig.SPIDER_SPREAD_DEGREES;
		float pitchOff = (i % 2 == 0 ? -1.5f : 1.5f) * (i == threads / 2 ? 0f : 1f);
		return Vec3.directionFromRotation(pitch + pitchOff, (float) (yaw + off + sweepDegrees));
	}

	/** A dome of burning strings round the aimed spot: everything inside is trapped (rooted; a boss only slowed) and burns. */
	public static int mesh(ServerPlayer p) {
		Session s = session(p);
		LivingEntity lock = HellflameCombat.aimed(p, HellflameConfig.MESH_RANGE, 8.0);
		Vec3 center = lock != null ? lock.position() : AbilityHelpers.aimPoint(p, HellflameConfig.MESH_RANGE);
		s.meshCenter = center;
		s.meshUntil = now(p) + HellflameConfig.MESH_TICKS;
		s.meshed.clear();
		double r = HellflameConfig.MESH_RADIUS * size(p);
		for (LivingEntity e : HellflameCombat.around(p, center.add(0, 1.0, 0), r)) {
			trap(p, s, e);
			HellflameCombat.strike(p, e, center, dmg(p, HellflameConfig.MESH_BURN_DAMAGE), 3, 0.0, 0.0, true);
		}
		ServerLevel level = p.serverLevel();
		QuirkFx.ring(level, center.add(0, 0.05, 0)).colours(0xFFF4C0, 0xFF5A10).size((float) r).thickness(0.35f).life(16).ground().spawn();
		QuirkFx.decal(level, center).style(QuirkFx.DECAL_SCORCH).colour(0x1A100A).size((float) r).life(HellflameConfig.MESH_TICKS + 100).spawn();
		Quirks.setVal(p, Hellflame.V_MESH, s.meshUntil);
		Quirks.setVal(p, Hellflame.V_MESH_AT, now(p));
		Quirks.setVec(p, Hellflame.VEC_MESH, center);
		Quirks.setAnim(p, Hellflame.ANIM_MESH);
		cooldown(p, HellflameConfig.MESH, HellflameConfig.MESH_COOLDOWN);
		QuirkCombos.used(p, HellflameConfig.MESH);
		sound(p, HellflameSounds.WHIP, 1.0f, 0.8f);
		soundAt(level, center, HellflameSounds.ROAR, 1.0f, 0.9f);
		heat(p, HellflameConfig.MESH_HEAT);
		return s.meshed.size();
	}

	private static void trap(ServerPlayer p, Session s, LivingEntity e) {
		if (!s.meshed.add(e.getId())) {
			return;
		}
		int left = (int) Math.max(1, s.meshUntil - now(p));
		// a boss is only slowed (crowd control is capped on bosses)
		AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, left, HellflameCombat.isBoss(e) ? 0 : 9);
	}

	private static void meshTick(ServerPlayer p, Session s, long now) {
		if (s.meshUntil == 0L) {
			return;
		}
		if (now >= s.meshUntil) {
			s.meshUntil = 0L;
			s.meshed.clear();
			return;
		}
		ServerLevel level = p.serverLevel();
		double r = HellflameConfig.MESH_RADIUS * size(p);
		if (now % 5 == 0) {
			for (LivingEntity e : HellflameCombat.around(p, s.meshCenter.add(0, 1.0, 0), r)) {
				trap(p, s, e);
			}
		}
		boolean burn = (s.meshUntil - now) % HellflameConfig.MESH_BURN_INTERVAL == 0;
		for (Iterator<Integer> it = s.meshed.iterator(); it.hasNext();) {
			Entity e = level.getEntity(it.next());
			if (!(e instanceof LivingEntity le) || !le.isAlive()) {
				it.remove();
				continue;
			}
			if (!HellflameCombat.isBoss(le)) {
				Vec3 v = le.getDeltaMovement();
				le.setDeltaMovement(0, Math.min(0.0, v.y), 0);
				le.hurtMarked = true;
				if (le instanceof Mob m) {
					m.getNavigation().stop();
				}
			}
			if (burn) {
				HellflameCombat.strike(p, le, s.meshCenter, dmg(p, HellflameConfig.MESH_BURN_DAMAGE), 2, 0.0, 0.0, true);
			}
		}
		if (now % 4 == 0) {
			double a = p.getRandom().nextDouble() * Math.PI * 2;
			Vec3 c = s.meshCenter.add(Math.cos(a) * r, 0.3 + p.getRandom().nextDouble() * r * 0.8, Math.sin(a) * r);
			level.sendParticles(QuirkParticles.EMBER, c.x, c.y, c.z, 2, 0.1, 0.1, 0.1, 0.02);
		}
		if (now % 20 == 0) {
			soundAt(level, s.meshCenter, SoundEvents.FIRE_AMBIENT, 0.8f, 1.1f);
		}
	}

	/** Test read: entities trapped in the running Mesh. */
	public static Set<Integer> meshed(ServerPlayer p) {
		Session s = peekSession(p);
		return s == null || s.meshUntil == 0L ? Set.of() : Set.copyOf(s.meshed);
	}

	// ================================================================ G: Flashfire Fist / Shift+G: Vanishing Fist / hold G: Hellblast

	/**
	 * One punch of the Flashfire Fist chain: a jab (6), a cross (6), then a jet-assisted straight (10, a burst of flame and a
	 * shock ring). Each G tap within {@link HellflameConfig#FIST_WINDOW} of the last is the next punch. Returns the punch
	 * thrown (1..3).
	 */
	public static int flashfireFist(ServerPlayer p) {
		Session s = session(p);
		long now = now(p);
		int step = s.fistStep >= 1 && s.fistStep <= 2 && now - s.fistAt <= HellflameConfig.FIST_WINDOW ? s.fistStep + 1 : 1;
		s.fistStep = step;
		s.fistAt = now;
		boolean finish = step == 3;
		boolean right = step != 2;
		ServerLevel level = p.serverLevel();
		Vec3 look = p.getLookAngle();
		if (finish) {
			AbilityHelpers.launchSelf(p, p.getDeltaMovement().add(new Vec3(look.x, 0, look.z).normalize().scale(HellflameConfig.FIST_LUNGE)));
		}
		Vec3 eye = p.getEyePosition();
		double reach = finish ? HellflameConfig.FIST_FINISH_REACH : HellflameConfig.FIST_REACH;
		double cos = Math.cos(Math.toRadians(HellflameConfig.FIST_HALF_ANGLE));
		int n = 0;
		for (LivingEntity e : HellflameCombat.around(p, eye, reach + 1.0)) {
			Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eye);
			double d = to.length();
			if (d > reach + e.getBbWidth() * 0.5 || (d > 1.0 && to.scale(1.0 / d).dot(look) < cos)) {
				continue;
			}
			if (HellflameCombat.strike(p, e, p.position(), dmg(p, HellflameConfig.FIST_DAMAGE[step - 1]), HellflameConfig.FIST_BURN_SECONDS,
					finish ? 1.3 : 0.35, finish ? 0.3 : 0.05, true)) {
				n++;
			}
		}
		Vec3 f = fist(p, right);
		QuirkFx.cone(level, f, look).colours(0xFFFFFF, 0xFF5A10).size(finish ? 1.1f : 0.6f).length(finish ? 4.2f : 2.4f).life(finish ? 10 : 7).spawn();
		if (finish) {
			QuirkFx.ring(level, f.add(look.scale(0.8))).flags(QuirkFx.F_VERTICAL).vec(look).colours(0xFFFFFF, 0xFF8A20).size(2.4f).thickness(0.35f).life(9)
					.spawn();
			QuirkFx.trail(level, p).colours(0xFFF4C0, 0xFF5A10).size(0.35f).size2(6f).life(12).vec(new Vec3(-0.4, 0.8, -0.3)).style(QuirkFx.RIBBON_FLAME)
					.spawn();
		}
		sound(p, HellflameSounds.ROAR, finish ? 1.2f : 0.8f, finish ? 0.9f : 1.25f + 0.1f * step);
		if (n > 0) {
			soundAt(level, f, finish ? QuirkSounds.IMPACT_HEAVY : QuirkSounds.IMPACT, 1.0f, 1.0f);
			QuirkCamera.preset(p, finish ? QuirkCamera.Preset.HEAVY_HIT : QuirkCamera.Preset.LIGHT_HIT);
		}
		Quirks.setVal(p, Hellflame.V_FIST, step);
		Quirks.setVal(p, Hellflame.V_FIST_AT, now);
		Quirks.setAnim(p, step == 1 ? Hellflame.ANIM_FIST_1 : step == 2 ? Hellflame.ANIM_FIST_2 : Hellflame.ANIM_FIST_3);
		cooldown(p, HellflameConfig.FIST, finish ? HellflameConfig.FIST_COOLDOWN : HellflameConfig.FIST_STEP_COOLDOWN);
		if (finish) {
			s.fistStep = 0;
		}
		heat(p, HellflameConfig.FIST_HEAT);
		return step;
	}

	/** Hold G: the fist is drawn to the hip and flame gathers (0.5 - 2 s); the release (or a full charge) fires the beam. */
	public static boolean startHellblast(ServerPlayer p) {
		Session s = session(p);
		if (s.charge || s.beam) {
			return false;
		}
		s.charge = true;
		s.chargeAt = now(p);
		s.released = false;
		s.lastFireAt = now(p);
		Quirks.setVal(p, Hellflame.V_CHARGE, now(p));
		Quirks.setAnim(p, Hellflame.ANIM_HELLBLAST);
		QuirkSounds.play(p.serverLevel(), p.position(), QuirkSounds.CHARGE_UP, 0.9f, 0.8f);
		return true;
	}

	/** The key came up: a charge fires (once it has its 0.5 s minimum). */
	public static void releaseHellblast(ServerPlayer p) {
		Session s = peekSession(p);
		if (s == null || !s.charge) {
			return;
		}
		if (now(p) - s.chargeAt >= HellflameConfig.HELLBLAST_MIN_CHARGE) {
			fireBeam(p, s);
		} else {
			s.released = true;
		}
	}

	/** The beam fires now: it burns for {@link HellflameConfig#HELLBLAST_BASE_TICKS} plus the charge. */
	static void fireBeam(ServerPlayer p, Session s) {
		int charged = (int) Mth.clamp(now(p) - s.chargeAt, HellflameConfig.HELLBLAST_MIN_CHARGE, HellflameConfig.HELLBLAST_MAX_CHARGE);
		s.charge = false;
		s.beam = true;
		s.beamAt = now(p);
		s.beamUntil = now(p) + HellflameConfig.HELLBLAST_BASE_TICKS + charged;
		Quirks.setVal(p, Hellflame.V_CHARGE, 0L);
		Quirks.setVal(p, Hellflame.V_BEAM, now(p));
		Quirks.setVal(p, Hellflame.V_BEAM_UNTIL, s.beamUntil);
		Quirks.setAnim(p, Hellflame.ANIM_BEAM);
		Vec3 h = hand(p);
		ServerLevel level = p.serverLevel();
		QuirkFx.ring(level, h.add(p.getLookAngle().scale(1.5))).flags(QuirkFx.F_VERTICAL).vec(p.getLookAngle()).colours(0xFFFFFF, 0xFF8A20).size(1.7f)
				.thickness(0.28f).life(10).spawn();
		sound(p, HellflameSounds.BEAM, 1.6f, 1.0f);
		QuirkCamera.preset(p, QuirkCamera.Preset.HEAVY_HIT);
		AbilityHelpers.launchSelf(p, p.getDeltaMovement().add(p.getLookAngle().scale(-0.25)));
	}

	public static void endBeam(ServerPlayer p) {
		Session s = peekSession(p);
		if (s == null || !s.beam) {
			return;
		}
		s.beam = false;
		s.released = false;
		Quirks.setVal(p, Hellflame.V_BEAM, 0L);
		Quirks.setVal(p, Hellflame.V_BEAM_UNTIL, 0L);
		cooldown(p, HellflameConfig.HELLBLAST, HellflameConfig.HELLBLAST_COOLDOWN);
		sound(p, SoundEvents.FIRE_EXTINGUISH, 0.8f, 0.9f);
	}

	public static boolean beaming(ServerPlayer p) {
		Session s = peekSession(p);
		return s != null && s.beam;
	}

	public static boolean charging(ServerPlayer p) {
		Session s = peekSession(p);
		return s != null && s.charge;
	}

	/** The beam's segment now: from the hand to the first block (or full range). */
	public static Vec3[] beamSegment(ServerPlayer p) {
		Vec3 from = hand(p);
		return new Vec3[] { from, clip(p, from, p.getLookAngle(), HellflameConfig.HELLBLAST_RANGE) };
	}

	private static void chargeTick(ServerPlayer p, Session s, long now) {
		s.lastFireAt = now;
		long age = now - s.chargeAt;
		if (age >= HellflameConfig.HELLBLAST_MAX_CHARGE || (s.released && age >= HellflameConfig.HELLBLAST_MIN_CHARGE)) {
			fireBeam(p, s);
			return;
		}
		ServerLevel level = p.serverLevel();
		Vec3 h = hand(p);
		for (int i = 0; i < 2; i++) {
			Vec3 d = new Vec3(p.getRandom().nextGaussian(), p.getRandom().nextGaussian(), p.getRandom().nextGaussian()).normalize().scale(1.0);
			Vec3 from = h.add(d);
			level.sendParticles(QuirkParticles.EMBER, from.x, from.y, from.z, 0, -d.x, -d.y, -d.z, 0.18);
		}
	}

	/** One beam tick; returns how many were burned this tick. */
	static int beamTick(ServerPlayer p, Session s, long now) {
		long age = now - s.beamAt;
		if (now >= s.beamUntil) {
			endBeam(p);
			return 0;
		}
		int n = 0;
		Vec3[] seg = beamSegment(p);
		ServerLevel level = p.serverLevel();
		if (age % HellflameConfig.HELLBLAST_HIT_INTERVAL == 0) {
			for (LivingEntity e : HellflameCombat.alongPath(p, seg[0], seg[1], HellflameConfig.HELLBLAST_RADIUS * size(p), null)) {
				Vec3 push = p.getLookAngle().scale(HellflameConfig.HELLBLAST_PUSH);
				if (HellflameCombat.strike(p, e, seg[0], dmg(p, HellflameConfig.HELLBLAST_DAMAGE), HellflameConfig.HELLBLAST_BURN_SECONDS, 0.0, 0.0, true)) {
					n++;
					if (!HellflameCombat.isBoss(e)) {
						AbilityHelpers.push(e, push);
					}
				}
			}
		}
		if (age % 6 == 0) {
			QuirkFx.blast(level, seg[1]).colours(0xFFFFFF, 0xFF6A10).size(1.8f).life(12).flags(QuirkFx.F_NO_SMOKE).spawn();
		}
		if (age % 10 == 0) {
			soundAt(level, seg[1], HellflameSounds.ROAR, 1.2f, 0.7f);
			QuirkCamera.shake(p, 0.12f, 6);
			if (seg[1].distanceToSqr(seg[0]) < HellflameConfig.HELLBLAST_RANGE * HellflameConfig.HELLBLAST_RANGE - 1.0) {
				tryFire(p, BlockPos.containing(seg[1].subtract(p.getLookAngle().scale(0.3))), 100);
			}
		}
		heat(p, HellflameConfig.HELLBLAST_HEAT_PER_SECOND / 20f);
		return n;
	}

	/** Test hook: one beam tick now (on a hit beat). */
	public static int beamTickForTest(ServerPlayer p) {
		Session s = peekSession(p);
		if (s == null || !s.beam) {
			return 0;
		}
		long t = now(p);
		long age = t - s.beamAt;
		long beat = s.beamAt + age - age % HellflameConfig.HELLBLAST_HIT_INTERVAL;
		return beamTick(p, s, beat);
	}

	/** Test hook: the charge is done now. */
	public static void fireBeamForTest(ServerPlayer p) {
		Session s = peekSession(p);
		if (s != null && s.charge) {
			fireBeam(p, s);
		}
	}

	/** Test read: when the running beam ends (0: none). */
	public static long beamUntil(ServerPlayer p) {
		Session s = peekSession(p);
		return s == null || !s.beam ? 0L : s.beamUntil;
	}

	/**
	 * A burst of flame leaves a burning decoy where he stood (anything hunting him loses him -- not a boss, which keeps its
	 * threat table), and he reappears behind the aimed creature and strikes (16). Returns the target, or null (nothing aimed
	 * at: nothing happens, no cooldown).
	 */
	public static @Nullable LivingEntity vanishingFist(ServerPlayer p) {
		LivingEntity target = HellflameCombat.aimed(p, HellflameConfig.VANISH_RANGE, 10.0);
		if (target == null) {
			Quirks.say(p, "message.projecthero.hellflame.no_target", ChatFormatting.GOLD);
			return null;
		}
		Session s = session(p);
		ServerLevel level = p.serverLevel();
		Vec3 from = p.position();
		long now = now(p);
		Quirks.setVal(p, Hellflame.V_DECOY, now + HellflameConfig.VANISH_DECOY_TICKS);
		Quirks.setVal(p, Hellflame.V_DECOY_YAW, Math.round(Mth.wrapDegrees(p.getYRot()) * 10f) + 100000L);
		Quirks.setVec(p, Hellflame.VEC_DECOY, from);
		HellflameCombat.flare(level, from.add(0, 1.0, 0), 20, 0.5);
		QuirkFx.flipbook(level, from.add(0, 1.0, 0)).style(QuirkFx.BOOK_SMOKE).colour(0x6A625A).size(1.6f).life(20).spawn();
		for (Mob m : level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(24.0), m -> m.getTarget() == p && !HellflameCombat.isBoss(m))) {
			m.setTarget(null);
			m.getNavigation().moveTo(from.x, from.y, from.z, 1.0);
		}
		soundAt(level, from, HellflameSounds.ROAR, 1.0f, 1.3f);
		soundAt(level, from, SoundEvents.ILLUSIONER_MIRROR_MOVE, 0.9f, 0.8f);
		Vec3 back = behind(p, target);
		QuirkFx.ribbon(level, from.add(0, 1.0, 0), back.add(0, 1.0, 0)).colours(0xFFFFFF, 0xFF5A10).size(0.5f).life(10).style(QuirkFx.RIBBON_FLAME).spawn();
		float yaw = (float) (Mth.atan2(target.getZ() - back.z, target.getX() - back.x) * Mth.RAD_TO_DEG) - 90f;
		p.teleportTo(level, back.x, back.y, back.z, yaw, 10f);
		p.setYHeadRot(yaw);
		p.resetFallDistance();
		HellflameCombat.flare(level, back.add(0, 1.0, 0), 18, 0.5);
		s.vanishTarget = target.getId();
		s.vanishStrikeAt = now + HellflameConfig.VANISH_STRIKE_DELAY;
		Quirks.setAnim(p, Hellflame.ANIM_VANISH);
		cooldown(p, HellflameConfig.VANISH, HellflameConfig.VANISH_COOLDOWN);
		heat(p, HellflameConfig.VANISH_HEAT);
		return target;
	}

	private static Vec3 behind(ServerPlayer p, LivingEntity target) {
		Vec3 facing = Vec3.directionFromRotation(0f, target.getYRot());
		Vec3 toHim = new Vec3(p.getX() - target.getX(), 0, p.getZ() - target.getZ());
		double gap = target.getBbWidth() * 0.5 + 1.1;
		Vec3[] tries = { facing.scale(-1), facing.yRot((float) Math.PI / 2f), facing.yRot((float) -Math.PI / 2f),
				toHim.lengthSqr() < 1.0e-4 ? facing : toHim.normalize() };
		for (Vec3 d : tries) {
			Vec3 at = target.position().add(d.scale(gap));
			AABB box = p.getBoundingBox().move(at.subtract(p.position()));
			if (p.level().noCollision(p, box) && p.level().getWorldBorder().isWithinBounds(BlockPos.containing(at))) {
				return at;
			}
		}
		return p.position();
	}

	private static void vanishTick(ServerPlayer p, Session s, long now) {
		if (s.vanishTarget < 0 || now < s.vanishStrikeAt) {
			return;
		}
		Entity e = p.serverLevel().getEntity(s.vanishTarget);
		s.vanishTarget = -1;
		if (!(e instanceof LivingEntity target) || !target.isAlive() || target.distanceTo(p) > 4.5) {
			return;
		}
		ServerLevel level = p.serverLevel();
		Vec3 c = target.position().add(0, target.getBbHeight() * 0.5, 0);
		HellflameCombat.strike(p, target, p.position(), dmg(p, HellflameConfig.VANISH_DAMAGE), HellflameConfig.VANISH_BURN_SECONDS,
				HellflameConfig.VANISH_KNOCKBACK, 0.3, true);
		QuirkFx.cone(level, p.getEyePosition().add(0, -0.3, 0), c.subtract(p.getEyePosition())).colours(0xFFFFFF, 0xFF5A10).size(1.0f).length(3.2f).life(9)
				.spawn();
		QuirkFx.blast(level, c).colours(0xFFFFFF, 0xFF6A10).size(1.7f).life(12).flags(QuirkFx.F_NO_RING).spawn();
		soundAt(level, c, QuirkSounds.IMPACT_HEAVY, 1.1f, 0.9f);
		QuirkCamera.preset(p, QuirkCamera.Preset.HEAVY_HIT);
	}

	/** Test hook: strike now. */
	public static void vanishStrikeForTest(ServerPlayer p) {
		Session s = peekSession(p);
		if (s != null && s.vanishTarget >= 0) {
			vanishTick(p, s, s.vanishStrikeAt);
		}
	}

	// ================================================================ Z: Hellflame Barrage / Shift+Z: Hellflame Pillar

	/** Eight rapid jabs, each a short burst of flame from alternating fists; the last one launches. */
	public static void barrage(ServerPlayer p) {
		Session s = session(p);
		s.barrageLeft = HellflameConfig.BARRAGE_JABS;
		s.barrageIndex = 0;
		s.barrageNext = now(p);
		Quirks.setVal(p, Hellflame.V_BARRAGE, now(p) + (long) HellflameConfig.BARRAGE_JABS * HellflameConfig.BARRAGE_INTERVAL + 2L);
		Quirks.setAnim(p, Hellflame.ANIM_BARRAGE);
		cooldown(p, HellflameConfig.BARRAGE, HellflameConfig.BARRAGE_COOLDOWN);
		barrageTick(p, s, now(p));
	}

	/** One jab; returns how many it burned. */
	static int jab(ServerPlayer p, int index) {
		ServerLevel level = p.serverLevel();
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		boolean last = index == HellflameConfig.BARRAGE_JABS - 1;
		double range = HellflameConfig.BARRAGE_RANGE * size(p);
		double cos = Math.cos(Math.toRadians(HellflameConfig.BARRAGE_HALF_ANGLE));
		int n = 0;
		for (LivingEntity e : HellflameCombat.around(p, eye, range + 1.0)) {
			Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eye);
			double d = to.length();
			if (d > range + e.getBbWidth() * 0.5 || (d > 1.0 && to.scale(1.0 / d).dot(look) < cos)) {
				continue;
			}
			if (HellflameCombat.strike(p, e, p.position(), dmg(p, HellflameConfig.BARRAGE_DAMAGE), HellflameConfig.BARRAGE_BURN_SECONDS,
					last ? HellflameConfig.BARRAGE_LAUNCH : HellflameConfig.BARRAGE_PUSH, last ? HellflameConfig.BARRAGE_LAUNCH_LIFT : 0.05, true)) {
				n++;
			}
		}
		Vec3 f = fist(p, index % 2 == 0);
		QuirkFx.flipbook(level, f.add(look.scale(1.2))).style(QuirkFx.BOOK_FIREBALL).size(last ? 1.6f : 1.0f).life(10).spawn();
		QuirkFx.cone(level, f, look).colours(0xFFF4C0, 0xFF5A10).size(0.45f).length(last ? 4f : 2.6f).life(6).spawn();
		sound(p, HellflameSounds.ROAR, 0.5f, 1.4f + p.getRandom().nextFloat() * 0.3f);
		if (n > 0) {
			soundAt(level, f, last ? QuirkSounds.IMPACT_HEAVY : QuirkSounds.IMPACT, 0.7f, 1.1f);
			if (last) {
				QuirkCamera.preset(p, QuirkCamera.Preset.HEAVY_HIT);
			}
		}
		heat(p, HellflameConfig.BARRAGE_HEAT_PER_JAB);
		return n;
	}

	private static void barrageTick(ServerPlayer p, Session s, long now) {
		if (s.barrageLeft <= 0 || now < s.barrageNext) {
			return;
		}
		jab(p, s.barrageIndex++);
		s.barrageLeft--;
		s.barrageNext = now + HellflameConfig.BARRAGE_INTERVAL;
		if (s.barrageLeft <= 0) {
			Quirks.setVal(p, Hellflame.V_BARRAGE, 0L);
		}
	}

	/** Test read: jabs left. */
	public static int barrageLeft(ServerPlayer p) {
		Session s = peekSession(p);
		return s == null ? 0 : s.barrageLeft;
	}

	/** The ground under the target cracks and glows, then a pillar of fire erupts there. Returns the eruption point. */
	public static Vec3 firePillar(ServerPlayer p) {
		Session s = session(p);
		LivingEntity lock = HellflameCombat.aimed(p, HellflameConfig.PILLAR_RANGE, 10.0);
		Vec3 point = lock != null ? lock.position() : AbilityHelpers.aimPoint(p, HellflameConfig.PILLAR_RANGE);
		Vec3 g = ground(p, point.add(0, 0.5, 0), 8.0);
		Vec3 base = g == null ? point : g;
		s.pillarPos = base;
		s.pillarAt = now(p) + HellflameConfig.PILLAR_WARN_TICKS;
		s.pillarErupted = false;
		Quirks.setVal(p, Hellflame.V_PILLAR, s.pillarAt);
		Quirks.setVec(p, Hellflame.VEC_PILLAR, base);
		Quirks.setAnim(p, Hellflame.ANIM_PILLAR);
		cooldown(p, HellflameConfig.PILLAR, HellflameConfig.PILLAR_COOLDOWN);
		ServerLevel level = p.serverLevel();
		QuirkFx.decal(level, base).style(QuirkFx.DECAL_CRACK).colour(0x2A0A04).size((float) HellflameConfig.PILLAR_RADIUS * 1.3f)
				.life(HellflameConfig.PILLAR_WARN_TICKS + HellflameConfig.PILLAR_BURN_TICKS + 60).spawn();
		soundAt(level, base, SoundEvents.LAVA_AMBIENT, 1.2f, 0.6f);
		soundAt(level, base, HellflameSounds.ROAR, 0.6f, 0.5f);
		heat(p, HellflameConfig.PILLAR_HEAT);
		return base;
	}

	private static List<LivingEntity> inPillar(ServerPlayer p, Session s) {
		double r = HellflameConfig.PILLAR_RADIUS * size(p);
		Vec3 b = s.pillarPos;
		AABB col = new AABB(b.x - r, b.y - 0.5, b.z - r, b.x + r, b.y + HellflameConfig.PILLAR_HEIGHT, b.z + r);
		return p.serverLevel().getEntitiesOfClass(LivingEntity.class, col, e -> e != p && e.isAlive() && HellflameCombat.isTarget(p, e)
				&& Math.hypot(e.getX() - b.x, e.getZ() - b.z) <= r + e.getBbWidth() * 0.5);
	}

	/** Erupts now; returns how many it hit. */
	static int erupt(ServerPlayer p, Session s) {
		s.pillarErupted = true;
		ServerLevel level = p.serverLevel();
		Vec3 b = s.pillarPos;
		QuirkCombos.Route route = QuirkCombos.used(p, HellflameConfig.PILLAR);
		float mult = QuirkCombos.mult(route);
		int n = 0;
		for (LivingEntity e : inPillar(p, s)) {
			if (HellflameCombat.strike(p, e, b.add(0, -1, 0), dmg(p, HellflameConfig.PILLAR_DAMAGE) * mult, HellflameConfig.PILLAR_BURN_SECONDS, 0.2,
					HellflameConfig.PILLAR_LIFT, true)) {
				n++;
			}
		}
		double r = HellflameConfig.PILLAR_RADIUS * size(p);
		QuirkFx.cone(level, b, new Vec3(0, 1, 0)).colours(0xFFFFFF, 0xFF4A10).size((float) r * 1.1f).length((float) HellflameConfig.PILLAR_HEIGHT).life(24)
				.ease(QuirkFx.Ease.OUT_EXPO).spawn();
		QuirkFx.blast(level, b.add(0, 0.6, 0)).colours(0xFFFFFF, 0xFF6A10).size((float) r * 1.3f).life(16).spawn();
		QuirkFx.ring(level, b.add(0, 0.05, 0)).colours(0xFFF4C0, 0xFF5A10).size((float) r * 2.4f).thickness(0.5f).life(14).ground().spawn();
		QuirkFx.decal(level, b).style(QuirkFx.DECAL_SCORCH).colour(0x1A100A).size((float) r * 1.4f).life(400).spawn();
		for (int i = 0; i < 8; i++) {
			double a = i / 8.0 * Math.PI * 2.0;
			tryFire(p, BlockPos.containing(b.x + Math.cos(a) * r * 0.8, b.y + 0.2, b.z + Math.sin(a) * r * 0.8), 80);
		}
		soundAt(level, b, HellflameSounds.ROAR, 1.8f, 0.6f);
		soundAt(level, b, QuirkSounds.IMPACT_HEAVY, 1.4f, 0.8f);
		QuirkCamera.shakeAround(level, b, 20, 0.45f, 10);
		return n;
	}

	private static void pillarTick(ServerPlayer p, Session s, long now) {
		if (s.pillarAt == 0L) {
			return;
		}
		ServerLevel level = p.serverLevel();
		if (!s.pillarErupted) {
			if (now >= s.pillarAt) {
				erupt(p, s);
			} else if ((s.pillarAt - now) % 3 == 0) {
				Vec3 b = s.pillarPos;
				level.sendParticles(QuirkParticles.EMBER, b.x, b.y + 0.1, b.z, 4, HellflameConfig.PILLAR_RADIUS * 0.5, 0.05, HellflameConfig.PILLAR_RADIUS * 0.5,
						0.03);
				if ((s.pillarAt - now) % 6 == 0) {
					soundAt(level, b, SoundEvents.LAVA_POP, 1.0f, 0.7f + 0.1f * (s.pillarAt - now) / 5f);
				}
			}
			return;
		}
		long since = now - s.pillarAt;
		if (since >= HellflameConfig.PILLAR_BURN_TICKS) {
			s.pillarAt = 0L;
			Quirks.setVal(p, Hellflame.V_PILLAR, 0L);
			return;
		}
		if (since > 0 && since % 15 == 0) {
			double r = HellflameConfig.PILLAR_RADIUS * size(p);
			QuirkFx.cone(level, s.pillarPos, new Vec3(0, 1, 0)).colours(0xFFF4C0, 0xFF4A10).size((float) r * 0.9f)
					.length((float) HellflameConfig.PILLAR_HEIGHT * 0.85f).life(20).spawn();
		}
		if (since % HellflameConfig.PILLAR_LINGER_INTERVAL == HellflameConfig.PILLAR_LINGER_INTERVAL - 1) {
			for (LivingEntity e : inPillar(p, s)) {
				HellflameCombat.strike(p, e, s.pillarPos, dmg(p, HellflameConfig.PILLAR_LINGER_DAMAGE), 3, 0.0, 0.1, true);
			}
		}
	}

	/** Test hook: erupt the waiting pillar now (skipping the warning); returns the hits. */
	public static int eruptForTest(ServerPlayer p) {
		Session s = peekSession(p);
		return s == null || s.pillarAt == 0L ? -1 : erupt(p, s);
	}

	// ================================================================ C: Flame Wave / Shift+C: Flame Cloak

	/** A wide rolling 90-degree wave of fire, 10 blocks: 11, set burning 4 s, thrown back. Returns how many it hit. */
	public static int flameWave(ServerPlayer p) {
		ServerLevel level = p.serverLevel();
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		double range = HellflameConfig.WAVE_RANGE;
		double cos = Math.cos(Math.toRadians(HellflameConfig.WAVE_HALF_ANGLE));
		int n = 0;
		for (LivingEntity e : HellflameCombat.around(p, eye, range + 1.0)) {
			Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eye);
			double d = to.length();
			if (d > range + e.getBbWidth() * 0.5 || (d > 1.2 && to.scale(1.0 / d).dot(look) < cos) || !p.hasLineOfSight(e)) {
				continue;
			}
			if (HellflameCombat.strike(p, e, p.position(), dmg(p, HellflameConfig.WAVE_DAMAGE), HellflameConfig.WAVE_BURN_SECONDS,
					HellflameConfig.WAVE_KNOCKBACK, 0.25, true)) {
				n++;
			}
		}
		Vec3 h = p.getEyePosition().add(look.scale(1.8)).add(0, -0.4, 0);
		QuirkFx.cone(level, h, look).colours(0xFFE0A0, 0xFF4A10).size((float) (range * 0.85)).length((float) range).life(16).spawn();
		QuirkFx.cone(level, h, look).colours(0xFFF4C0, 0xFF6A10).size((float) (range * 0.5)).length((float) (range * 0.8)).life(12).spawn();
		// the wave's flipbook edge, rolling out across the arc
		Vec3 flat = flatLook(p);
		for (int i = -3; i <= 3; i++) {
			Vec3 d = flat.yRot((float) Math.toRadians(i * 13.0));
			Vec3 at = eye.add(d.scale(range * 0.85)).add(0, -0.8, 0);
			QuirkFx.flipbook(level, at).style(QuirkFx.BOOK_FIREBALL).size(1.8f).life(14).spawn();
			Vec3 g = ground(p, at, 3.0);
			if (g != null && (i & 1) == 0) {
				QuirkFx.decal(level, g).style(QuirkFx.DECAL_SCORCH).colour(0x1A100A).size(1.4f).life(260).spawn();
			}
		}
		sound(p, HellflameSounds.ROAR, 1.8f, 0.75f);
		sound(p, HellflameSounds.JET, 0.8f, 0.6f);
		QuirkCamera.preset(p, n > 0 ? QuirkCamera.Preset.HEAVY_HIT : QuirkCamera.Preset.LIGHT_HIT);
		Quirks.setAnim(p, Hellflame.ANIM_WAVE);
		cooldown(p, HellflameConfig.WAVE, HellflameConfig.WAVE_COOLDOWN);
		heat(p, HellflameConfig.WAVE_HEAT);
		return n;
	}

	/** Starts (or ends early) the 12 s Flame Cloak; returns whether it burns now. */
	public static boolean toggleCloak(ServerPlayer p) {
		Session s = session(p);
		if (s.cloakUntil > now(p)) {
			endCloak(p, s);
			return false;
		}
		s.cloakUntil = now(p) + HellflameConfig.CLOAK_TICKS;
		Quirks.setVal(p, Hellflame.V_CLOAK, s.cloakUntil);
		Quirks.setAnim(p, Hellflame.ANIM_CLOAK);
		AttributeInstance armor = p.getAttribute(Attributes.ARMOR);
		if (armor != null && armor.getModifier(CLOAK_ARMOR_ID) == null) {
			armor.addTransientModifier(new AttributeModifier(CLOAK_ARMOR_ID, HellflameConfig.CLOAK_ARMOR, AttributeModifier.Operation.ADD_VALUE));
		}
		ServerLevel level = p.serverLevel();
		QuirkFx.ring(level, p.position().add(0, 0.05, 0)).colours(0xFFF4C0, 0xFF5A10).size((float) HellflameConfig.CLOAK_RADIUS + 1f).thickness(0.6f).life(14)
				.ground().spawn();
		QuirkFx.blast(level, p.position().add(0, 1.0, 0)).colours(0xFFFFFF, 0xFF6A10).size(1.8f).life(12).flags(QuirkFx.F_NO_RING | QuirkFx.F_NO_SMOKE)
				.spawn();
		sound(p, HellflameSounds.ROAR, 1.4f, 0.7f);
		s.lastFireAt = now(p);
		return true;
	}

	private static void endCloak(ServerPlayer p, Session s) {
		s.cloakUntil = 0L;
		Quirks.setVal(p, Hellflame.V_CLOAK, 0L);
		AttributeInstance armor = p.getAttribute(Attributes.ARMOR);
		if (armor != null) {
			armor.removeModifier(CLOAK_ARMOR_ID);
		}
		if (Quirks.holds(p, Quirk.HELLFLAME)) {
			Quirks.setCooldown(p, HellflameConfig.CLOAK, HellflameConfig.CLOAK_COOLDOWN);
		}
		sound(p, SoundEvents.FIRE_EXTINGUISH, 0.6f, 1.2f);
	}

	public static boolean cloaked(ServerPlayer p) {
		Session s = peekSession(p);
		return s != null && s.cloakUntil > now(p);
	}

	/** One Cloak tick; returns how many the aura burned (only on its beat). */
	static int cloakTick(ServerPlayer p, Session s, long now) {
		if (now >= s.cloakUntil) {
			endCloak(p, s);
			return 0;
		}
		if (p.getRemainingFireTicks() > 0) {
			p.clearFire();
		}
		if (now % 20 == 0) {
			sound(p, SoundEvents.FIRE_AMBIENT, 0.7f, 0.9f);
		}
		int n = 0;
		if (now % HellflameConfig.CLOAK_AURA_INTERVAL == 0) {
			for (LivingEntity e : HellflameCombat.around(p, p.position().add(0, 0.9, 0), HellflameConfig.CLOAK_RADIUS * size(p))) {
				if (HellflameCombat.strike(p, e, p.position(), dmg(p, HellflameConfig.CLOAK_AURA_DAMAGE), HellflameConfig.CLOAK_IGNITE_SECONDS, 0.2, 0.0, true)) {
					n++;
				}
			}
		}
		if (now % 30 == 0) {
			QuirkFx.ring(p.serverLevel(), p.position().add(0, 0.05, 0)).style(QuirkFx.RING_SOFT).colours(0xFFC040, 0xFF4A10)
					.size((float) HellflameConfig.CLOAK_RADIUS).thickness(0.4f).life(20).ground().spawn();
		}
		heat(p, HellflameConfig.heat().cloakPerSecond / 20f);
		return n;
	}

	/** Test hook: one aura beat now. */
	public static int cloakPulseForTest(ServerPlayer p) {
		Session s = peekSession(p);
		if (s == null || s.cloakUntil <= now(p)) {
			return 0;
		}
		long beat = now(p) - now(p) % HellflameConfig.CLOAK_AURA_INTERVAL;
		return cloakTick(p, s, beat);
	}

	/** Melee while cloaked or from Blaze up (hot hands): the target catches fire. */
	public static void hotHands(ServerPlayer p, LivingEntity target) {
		if (!HellflameCombat.isTarget(p, target) || !(cloaked(p) || Quirks.meter(p) >= HellflameConfig.HOT_HANDS_FROM)) {
			return;
		}
		target.igniteForSeconds(cloaked(p) ? HellflameConfig.CLOAK_IGNITE_SECONDS + 1 : HellflameConfig.HOT_HANDS_SECONDS);
		Vec3 c = target.position().add(0, target.getBbHeight() * 0.5, 0);
		p.serverLevel().sendParticles(QuirkParticles.EMBER, c.x, c.y, c.z, 8, 0.25, 0.3, 0.25, 0.05);
		p.serverLevel().sendParticles(HellflameCombat.ORANGE, c.x, c.y, c.z, 4, 0.25, 0.3, 0.25, 0.0);
		soundAt(p.serverLevel(), c, SoundEvents.FIRECHARGE_USE, 0.4f, 1.4f);
	}

	/** Compat name (pre-v0.17.1 callers): the Cloak's burning melee. */
	public static void cloakMelee(ServerPlayer p, LivingEntity target) {
		hotHands(p, target);
	}

	/**
	 * Damage about to hit him. His body is built for heat: ordinary fire does nothing (except while burnt out, when he is
	 * burning up from the inside) and the cold never freezes him. Lava still hurts. The Cloak sets close attackers alight.
	 */
	public static float incoming(ServerPlayer p, DamageSource source, float amount) {
		boolean ordinaryFire = source.is(DamageTypes.IN_FIRE) || source.is(DamageTypes.ON_FIRE) || source.is(DamageTypes.HOT_FLOOR)
				|| source.is(DamageTypes.CAMPFIRE);
		if (ordinaryFire && !Quirks.overloaded(p)) {
			return 0f;
		}
		if (source.is(DamageTypes.FREEZE)) {
			return 0f;
		}
		if (cloaked(p) && source.getEntity() instanceof LivingEntity attacker && attacker != p
				&& attacker.distanceTo(p) <= HellflameConfig.CLOAK_RADIUS + 2.5 && HellflameCombat.isTarget(p, attacker)) {
			attacker.igniteForSeconds(HellflameConfig.CLOAK_IGNITE_SECONDS + 1);
			Vec3 c = attacker.position().add(0, attacker.getBbHeight() * 0.5, 0);
			p.serverLevel().sendParticles(QuirkParticles.EMBER, c.x, c.y, c.z, 12, 0.3, 0.4, 0.3, 0.05);
		}
		return amount;
	}

	// ================================================================ V: Heat Vent / Shift+V: Fire Wall

	/**
	 * Dumps up to 60 Heat in a steam-and-flame burst round him: 4 + 0.18 per Heat vented, radius 3 to 4, knockback. It ends an
	 * Overheat burnout, puts out fires on him and his allies close by and evaporates the 3x3 of water round his feet. Refused
	 * while burnt out after Prominence Burn. Returns the hit count (-1 refused).
	 */
	public static int coolDown(ServerPlayer p) {
		if (Hellflame.burntOut(p)) {
			Quirks.say(p, "message.projecthero.hellflame.burnt_out", ChatFormatting.RED);
			return -1;
		}
		float h = Quirks.meter(p);
		float vented = Math.min(HellflameConfig.COOL_MAX_VENT, h);
		float damage = HellflameConfig.COOL_BASE_DAMAGE + HellflameConfig.COOL_DAMAGE_PER_HEAT * vented;
		double r = HellflameConfig.COOL_BASE_RADIUS + HellflameConfig.COOL_RADIUS_PER_HEAT * vented;
		ServerLevel level = p.serverLevel();
		Vec3 c = p.position().add(0, 1.0, 0);
		int n = 0;
		for (LivingEntity e : HellflameCombat.around(p, c, r)) {
			if (HellflameCombat.strike(p, e, p.position(), dmg(p, damage), HellflameConfig.COOL_BURN_SECONDS, HellflameConfig.COOL_KNOCKBACK, 0.3, true)) {
				n++;
			}
		}
		Quirks.setMeter(p, h - vented);
		if (QuirkMeters.beyond(p)) {
			Quirks.setVal(p, QuirkMeters.V_BEYOND, 0L); // venting at 100 ends the push before its crash
		}
		if (Quirks.overloaded(p)) {
			Quirks.edit(p, st -> st.overloadUntil = 0L);
			p.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
		}
		p.clearFire();
		// allies close by (anything he may not harm: squadmates, pets, peaceful players) are put out
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(HellflameConfig.COOL_ALLY_RADIUS),
				e -> e != p && e.isAlive() && e.isOnFire() && !HellflameCombat.isTarget(p, e))) {
			e.clearFire();
		}
		int dried = evaporate(p);
		QuirkFx.ring(level, p.position().add(0, 0.05, 0)).style(QuirkFx.RING_STEAM).colours(0xF0F4FF, 0xC8D0DA).size((float) r + 1.5f).thickness(0.9f).life(26)
				.ground().spawn();
		QuirkFx.ring(level, p.position().add(0, 0.1, 0)).colours(0xFFFFFF, 0xFF6A10).size((float) r).thickness(0.5f).life(12).ground().spawn();
		QuirkFx.blast(level, c).colours(0xFFFFFF, 0xFFB060).size((float) (1.5 + vented / 30.0)).life(12).flags(QuirkFx.F_NO_SMOKE | QuirkFx.F_NO_RING).spawn();
		level.sendParticles(QuirkParticles.STEAM, c.x, c.y, c.z, 30 + dried * 4, 0.8, 0.8, 0.8, 0.06);
		Quirks.setAnim(p, Hellflame.ANIM_COOL);
		Quirks.setCooldown(p, HellflameConfig.COOL, HellflameConfig.COOL_COOLDOWN);
		sound(p, HellflameSounds.VENT, 1.5f, 1.0f);
		QuirkCamera.preset(p, n > 0 ? QuirkCamera.Preset.HEAVY_HIT : QuirkCamera.Preset.LIGHT_HIT);
		session(p).lastFireAt = now(p) - HellflameConfig.heat().ventDelayTicks;
		return n;
	}

	/** Evaporates the water (source and flowing, not waterlogged blocks) in the 3x3 round his feet and just under it. */
	static int evaporate(ServerPlayer p) {
		ServerLevel level = p.serverLevel();
		BlockPos feet = p.blockPosition();
		int r = HellflameConfig.COOL_WATER_RADIUS;
		int n = 0;
		for (BlockPos q : BlockPos.betweenClosed(feet.offset(-r, -1, -r), feet.offset(r, 1, r))) {
			BlockState st = level.getBlockState(q);
			if (st.getBlock() instanceof LiquidBlock && st.getFluidState().is(FluidTags.WATER) && p.mayBuild() && level.mayInteract(p, q)) {
				level.setBlock(q, Blocks.AIR.defaultBlockState(), 3);
				n++;
			}
		}
		return n;
	}

	/** A 7x3 wall of flame across his front for 8 s: it burns what crosses it (4) and stops projectiles. It lights no blocks. */
	public static int fireWall(ServerPlayer p) {
		Session s = session(p);
		Vec3 fwd = flatLook(p);
		Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
		double half = HellflameConfig.WALL_HALF_LENGTH * size(p);
		Vec3 c = p.position().add(fwd.scale(HellflameConfig.WALL_DISTANCE));
		Vec3 g = ground(p, c.add(0, 1.0, 0), 4.0);
		if (g != null) {
			c = g;
		}
		s.wallA = c.subtract(right.scale(half));
		s.wallB = c.add(right.scale(half));
		s.wallUntil = now(p) + HellflameConfig.WALL_TICKS;
		ServerLevel level = p.serverLevel();
		int cells = (int) Math.round(half * 2);
		for (int i = 0; i <= cells; i += 2) {
			Vec3 at = s.wallA.lerp(s.wallB, i / (double) cells);
			QuirkFx.decal(level, at).style(QuirkFx.DECAL_SCORCH).colour(0x1A100A).size(1.2f).life(HellflameConfig.WALL_TICKS + 120).spawn();
		}
		Quirks.setVal(p, Hellflame.V_WALL, s.wallUntil);
		Quirks.setVal(p, Hellflame.V_WALL_AT, now(p));
		Quirks.setVec(p, Hellflame.VEC_WALL_A, s.wallA);
		Quirks.setVec(p, Hellflame.VEC_WALL_B, s.wallB);
		Quirks.setAnim(p, Hellflame.ANIM_WALL);
		cooldown(p, HellflameConfig.WALL, HellflameConfig.WALL_COOLDOWN);
		soundAt(level, c, HellflameSounds.ROAR, 1.6f, 0.55f);
		heat(p, HellflameConfig.WALL_HEAT);
		return cells + 1;
	}

	private static boolean inWall(Session s, Vec3 pos, double pad) {
		Vec3 a = s.wallA, b = s.wallB;
		Vec3 ab = new Vec3(b.x - a.x, 0, b.z - a.z);
		double len2 = Math.max(1.0e-6, ab.lengthSqr());
		double t = Mth.clamp(((pos.x - a.x) * ab.x + (pos.z - a.z) * ab.z) / len2, 0.0, 1.0);
		double nx = a.x + ab.x * t - pos.x, nz = a.z + ab.z * t - pos.z;
		return nx * nx + nz * nz <= Math.pow(HellflameConfig.WALL_THICKNESS * 0.5 + pad, 2) && pos.y >= a.y - 1.0
				&& pos.y <= a.y + HellflameConfig.WALL_HEIGHT + 0.5;
	}

	private static void wallTick(ServerPlayer p, Session s, long now) {
		if (s.wallUntil == 0L) {
			return;
		}
		if (now >= s.wallUntil) {
			s.wallUntil = 0L;
			Quirks.setVal(p, Hellflame.V_WALL, 0L);
			return;
		}
		ServerLevel level = p.serverLevel();
		AABB box = new AABB(s.wallA, s.wallB).inflate(1.5, 0, 1.5).expandTowards(0, HellflameConfig.WALL_HEIGHT + 1.0, 0).move(0, -1.0, 0);
		if ((s.wallUntil - now) % HellflameConfig.WALL_HIT_INTERVAL == 0) {
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != p && e.isAlive() && HellflameCombat.isTarget(p, e))) {
				if (inWall(s, e.position().add(0, 0.1, 0), e.getBbWidth() * 0.5)) {
					HellflameCombat.strike(p, e, e.position().add(0, 0.5, 0), dmg(p, HellflameConfig.WALL_DAMAGE), HellflameConfig.WALL_BURN_SECONDS, 0.0, 0.0,
							true);
				}
			}
		}
		for (Projectile pr : level.getEntitiesOfClass(Projectile.class, box, pr -> pr.isAlive() && pr.getOwner() != p)) {
			if (inWall(s, pr.position(), 0.3) || inWall(s, pr.position().add(pr.getDeltaMovement()), 0.3)) {
				Vec3 at = pr.position();
				HellflameCombat.flare(level, at, 8, 0.2);
				soundAt(level, at, SoundEvents.FIRE_EXTINGUISH, 0.6f, 1.6f);
				pr.discard();
			}
		}
		if ((s.wallUntil - now) % 20 == 0) {
			soundAt(level, s.wallA.lerp(s.wallB, 0.5), SoundEvents.FIRE_AMBIENT, 1.0f, 0.8f);
		}
	}

	public static boolean wallUp(ServerPlayer p) {
		Session s = peekSession(p);
		return s != null && s.wallUntil > now(p);
	}

	// ================================================================ Plus Ultra: Prominence Burn

	/**
	 * The ultimate (hold C at a full Plus Ultra gauge; the gauge is already spent): 1.5 s of ignition -- he becomes a
	 * white-hot sun, the Heat pinned at 100 -- then a comet charge up to 40 blocks along the aim, burning through every target
	 * (20 each), detonating at the end for 45 in radius 8. Afterwards a 10 s burnout.
	 */
	public static void prominenceBurn(ServerPlayer p) {
		Session s = session(p);
		endFlight(p);
		s.charge = false;
		if (s.beam) {
			endBeam(p);
		}
		s.barrageLeft = 0;
		s.burstUntil = 0L;
		s.dashUntil = 0L;
		s.promAt = now(p);
		s.promHit.clear();
		s.promDir = Vec3.ZERO;
		Quirks.setVal(p, Hellflame.V_PROM, s.promAt);
		Quirks.setMeter(p, 100f);
		Quirks.setAnim(p, Hellflame.ANIM_PROMINENCE);
		floatTolerance(p, s, true);
		ServerLevel level = p.serverLevel();
		QuirkFx.ring(level, p.position().add(0, 0.05, 0)).colours(0xFFFFFF, 0xFF6A10).size(8f).thickness(0.9f).life(24).ground().spawn();
		QuirkFx.blast(level, p.position().add(0, 1.0, 0)).colours(0xFFFFFF, 0xFFC040).size(2.6f).life(30).flags(QuirkFx.F_NO_SMOKE | QuirkFx.F_NO_RING)
				.spawn();
		sound(p, HellflameSounds.ROAR, 2.0f, 0.5f);
		QuirkCamera.vignette(p, 0xFF7A1A, 0.6f, 50);
		QuirkCamera.shakeAround(level, p.position(), 24, 0.35f, 30);
	}

	public static boolean inProminence(ServerPlayer p) {
		Session s = peekSession(p);
		return s != null && s.promAt != 0L;
	}

	private static void prominenceTick(ServerPlayer p, Session s, long now) {
		long age = now - s.promAt;
		ServerLevel level = p.serverLevel();
		p.resetFallDistance();
		Quirks.setMeter(p, 100f);
		if (age < HellflameConfig.PROM_IGNITE_TICKS) {
			// the sun: hanging in the air, burning white
			Vec3 v = p.getDeltaMovement();
			p.setDeltaMovement(v.x * 0.5, age < 8 ? 0.12 : 0.0, v.z * 0.5);
			p.hurtMarked = true;
			if (age % 3 == 0) {
				Vec3 c = p.position().add(0, 1.0, 0);
				level.sendParticles(QuirkParticles.EMBER, c.x, c.y, c.z, 6, 0.8, 0.8, 0.8, 0.12);
			}
			return;
		}
		if (s.promDir == Vec3.ZERO || s.promDir.lengthSqr() < 1.0e-4) {
			s.promDir = p.getLookAngle();
			QuirkFx.trail(level, p).colours(0xFFFFFF, 0xFF6A10).size(1.1f).size2(14f).life(HellflameConfig.PROM_MAX_TICKS + 16).vec(new Vec3(0, 1.0, 0))
					.style(QuirkFx.RIBBON_FLAME).spawn();
			sound(p, HellflameSounds.COMET, 2.6f, 1.0f);
			QuirkCamera.preset(p, QuirkCamera.Preset.DASH);
		}
		long k = age - HellflameConfig.PROM_IGNITE_TICKS;
		Vec3 body = p.position().add(0, 1.0, 0);
		Vec3 step = s.promDir.scale(HellflameConfig.PROM_SPEED);
		BlockHitResult wall = level.clip(new ClipContext(body, body.add(step.scale(1.4)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		// burning through everything it passes
		for (LivingEntity e : HellflameCombat.around(p, body.add(step.scale(0.5)), HellflameConfig.PROM_HIT_RADIUS)) {
			if (s.promHit.add(e.getId())) {
				HellflameCombat.strike(p, e, body, dmg(p, HellflameConfig.PROM_PASS_DAMAGE), 8, 0.8, 0.4, true);
				QuirkCamera.hitStop(p, 1);
			}
		}
		if (wall.getType() != HitResult.Type.MISS || k >= HellflameConfig.PROM_MAX_TICKS) {
			detonateProminence(p, s, wall.getType() != HitResult.Type.MISS ? wall.getLocation().subtract(s.promDir.scale(0.6)) : body);
			return;
		}
		AbilityHelpers.launchSelf(p, step);
		if (k % 2 == 0) {
			Vec3 g = ground(p, p.position(), 3.0);
			if (g != null) {
				QuirkFx.decal(level, g).style(QuirkFx.DECAL_SCORCH).colour(0x140A06).size(1.8f).life(400).spawn();
			}
		}
	}

	/** The comet's end: 45 in radius 8 (falling to 60% at the edge), a crater of scorch, then the burnout. Returns the hits. */
	static int detonateProminence(ServerPlayer p, Session s, Vec3 at) {
		ServerLevel level = p.serverLevel();
		s.promAt = 0L;
		Quirks.setVal(p, Hellflame.V_PROM, 0L);
		double r = HellflameConfig.PROM_BLAST_RADIUS;
		int n = 0;
		for (LivingEntity e : HellflameCombat.around(p, at, r)) {
			double d = Math.sqrt(AbilityHelpers.distanceSqToBox(e, at));
			float f = (float) (1.0 - 0.4 * Math.min(1.0, d / r));
			if (HellflameCombat.strike(p, e, at, dmg(p, HellflameConfig.PROM_BLAST_DAMAGE) * f, 10, 2.2, 0.8, true)) {
				n++;
			}
		}
		QuirkFx.blast(level, at).colours(0xFFFFFF, 0xFF5A10).size(8f).life(32).spawn();
		QuirkFx.blast(level, at.add(0, 1.5, 0)).colours(0xFFFFFF, 0xFFC040).size(4.5f).life(22).flags(QuirkFx.F_NO_SMOKE | QuirkFx.F_NO_RING).spawn();
		QuirkFx.ring(level, at).colours(0xFFFFFF, 0xFF6A10).size(18f).thickness(1.3f).life(24).ground().spawn();
		Vec3 g = ground(p, at, 6.0);
		Vec3 crater = g == null ? at : g;
		QuirkFx.decal(level, crater).style(QuirkFx.DECAL_SCORCH).colour(0x0E0604).size(8f).life(800).spawn();
		QuirkFx.decal(level, crater).style(QuirkFx.DECAL_CRACK).colour(0x2A0A04).size(6f).life(800).spawn();
		level.sendParticles(QuirkParticles.EMBER, at.x, at.y, at.z, 80, 2.5, 1.5, 2.5, 0.35);
		soundAt(level, at, QuirkSounds.IMPACT_HEAVY, 3.0f, 0.6f);
		soundAt(level, at, HellflameSounds.VENT, 2.0f, 0.5f);
		QuirkCamera.preset(p, QuirkCamera.Preset.ULTIMATE);
		QuirkCamera.shakeAround(level, at, 48, 1.0f, 24);
		AbilityHelpers.launchSelf(p, s.promDir.scale(-0.3).add(0, 0.5, 0));
		// the drawback: 10 s burnt out (Heat pinned at 100, no fire), Slowness and Weakness
		int burn = HellflameConfig.heat().burnoutSeconds * 20;
		s.burnoutUntil = now(p) + burn;
		Quirks.setVal(p, Hellflame.V_BURNOUT, s.burnoutUntil);
		Quirks.overload(p, burn); // runs stopAll
		Quirks.setMeter(p, 100f);
		p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, burn, 1, false, true, true));
		p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, burn, 0, false, true, true));
		p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 80, 0, false, false, true));
		Quirks.say(p, "message.projecthero.hellflame.burnout", ChatFormatting.RED);
		return n;
	}

	/** Test hook: the comet launches now (skips the ignition) and flies to its end at once. Returns the detonation hits. */
	public static int prominenceForTest(ServerPlayer p, boolean detonateNow) {
		Session s = peekSession(p);
		if (s == null || s.promAt == 0L) {
			return -1;
		}
		s.promAt = now(p) - HellflameConfig.PROM_IGNITE_TICKS;
		if (!detonateNow) {
			return 0;
		}
		s.promDir = p.getLookAngle();
		return detonateProminence(p, s, p.position().add(0, 1.0, 0));
	}

	/** Test read: creatures the comet has burned through. */
	public static Set<Integer> prominenceHit(ServerPlayer p) {
		Session s = peekSession(p);
		return s == null ? Set.of() : Set.copyOf(s.promHit);
	}

	/** Test read: burnt out. */
	public static boolean burntOut(ServerPlayer p) {
		Session s = peekSession(p);
		return s != null && s.burnoutUntil > now(p);
	}

	// ================================================================ survival: the Forge Hand

	/**
	 * Sneak + use with an empty main hand and something that smelts in the off hand (raw food, ore): it is cooked / smelted
	 * on the spot, one at a time ({@link HellflameConfig#FORGE_HEAT} Heat each). Returns true when it smelted one.
	 */
	public static boolean forge(ServerPlayer p) {
		Session s = session(p);
		long now = now(p);
		ItemStack off = p.getOffhandItem();
		if (now < s.forgeReadyAt || off.isEmpty() || !p.getMainHandItem().isEmpty() || Quirks.overloaded(p)) {
			return false;
		}
		ServerLevel level = p.serverLevel();
		var recipe = level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(off), level);
		if (recipe.isEmpty()) {
			return false;
		}
		ItemStack out = recipe.get().value().getResultItem(level.registryAccess()).copy();
		if (out.isEmpty()) {
			return false;
		}
		off.shrink(1);
		if (!p.getInventory().add(out)) {
			p.drop(out, false);
		}
		s.forgeReadyAt = now + HellflameConfig.FORGE_COOLDOWN;
		Vec3 h = p.position().add(0, 1.0, 0).add(flatLook(p).scale(0.75));
		QuirkFx.blast(level, h).colours(0xFFD890, 0xFF8A20).size(0.32f).life(10).flags(QuirkFx.F_NO_RING | QuirkFx.F_NO_SMOKE).spawn();
		level.sendParticles(QuirkParticles.EMBER, h.x, h.y, h.z, 8, 0.15, 0.15, 0.15, 0.04);
		level.sendParticles(QuirkParticles.SMOKE_PUFF, h.x, h.y + 0.2, h.z, 2, 0.1, 0.1, 0.1, 0.02);
		sound(p, HellflameSounds.ROAR, 0.6f, 1.5f);
		sound(p, SoundEvents.FURNACE_FIRE_CRACKLE, 1.0f, 1.0f);
		Quirks.setAnim(p, Hellflame.ANIM_FORGE);
		heat(p, HellflameConfig.FORGE_HEAT);
		return true;
	}

	/** Client and server: the Forge Hand would take this use (so the off-hand item is not eaten / placed instead). */
	public static boolean forgeWants(net.minecraft.world.entity.player.Player p, InteractionHand hand) {
		return hand == InteractionHand.OFF_HAND && p.isShiftKeyDown() && p.getMainHandItem().isEmpty() && !p.getOffhandItem().isEmpty()
				&& Hellflame.is(p) && !Quirks.overloaded(p)
				&& p.level().getRecipeManager().getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(p.getOffhandItem()), p.level()).isPresent();
	}

	// ================================================================ the tick

	static void tick(ServerPlayer p) {
		Session s = session(p);
		long now = now(p);
		if (!s.legacyChecked) {
			s.legacyChecked = true;
			var st = Quirks.state(p);
			boolean legacy = false;
			for (String k : Hellflame.LEGACY_VALS) {
				legacy |= st.vals.containsKey(k);
			}
			if (legacy) {
				Quirks.edit(p, n -> {
					for (String k : Hellflame.LEGACY_VALS) {
						n.vals.remove(k);
					}
				});
			}
		}
		firesTick(p, s, now);
		// freeze immune: powder snow and the cold never take hold
		if (p.getTicksFrozen() > 0) {
			p.setTicksFrozen(0);
		}
		if (s.burnoutUntil != 0L && now >= s.burnoutUntil) {
			s.burnoutUntil = 0L;
			Quirks.setVal(p, Hellflame.V_BURNOUT, 0L);
			s.lastFireAt = now - HellflameConfig.heat().ventDelayTicks;
		}
		if (s.promAt != 0L) {
			prominenceTick(p, s, now);
			if (s.promAt != 0L) {
				return;
			}
		}
		if (Quirks.overloaded(p)) {
			if (s.burnoutUntil > now) {
				Quirks.setMeter(p, 100f);
			} else {
				HellflameConfig.Heat h = HellflameConfig.heat();
				float rate = (100f - h.overheatRecoverTo) / Math.max(1, h.overheatTicks);
				if (Quirks.meter(p) > h.overheatRecoverTo) {
					Quirks.addMeter(p, -rate * (wet(p) ? h.wetMultiplier : 1f));
				}
			}
			if (now % 4 == 0) {
				p.serverLevel().sendParticles(QuirkParticles.SMOKE_PUFF, p.getX(), p.getY() + 1.6, p.getZ(), 1, 0.3, 0.3, 0.3, 0.01);
			}
			return;
		}
		// Inferno: it burns him (0.5 a second), and the snow and ice round him melt
		float heatNow = Quirks.meter(p);
		if (heatNow >= HellflameConfig.HEAT_WARN && !QuirkMeters.beyond(p)) {
			if (now % 20 == 0 && p.getHealth() > HellflameConfig.INFERNO_HP_PER_SECOND + 1f) {
				p.hurt(p.damageSources().magic(), HellflameConfig.INFERNO_HP_PER_SECOND);
			}
			if (now % 10 == 0) {
				melt(p);
			}
		}
		// each of these can burn him out, which ends everything: re-check as we go
		if (s.flight) {
			flightTick(p, s, now);
		}
		if (s.charge && !Quirks.overloaded(p)) {
			chargeTick(p, s, now);
		}
		if (s.beam && !Quirks.overloaded(p)) {
			beamTick(p, s, now);
		}
		if (s.cloakUntil != 0L && !Quirks.overloaded(p)) {
			cloakTick(p, s, now);
		}
		if (!Quirks.overloaded(p)) {
			barrageTick(p, s, now);
		}
		burstTick(p, s, now);
		dashTick(p, s, now);
		meshTick(p, s, now);
		wallTick(p, s, now);
		pillarTick(p, s, now);
		vanishTick(p, s, now);
		HellflameConfig.Heat h = HellflameConfig.heat();
		if (!Quirks.overloaded(p) && !QuirkMeters.beyond(p) && now - s.lastFireAt > h.ventDelayTicks && Quirks.meter(p) > 0f) {
			Quirks.addMeterPaced(p, -h.ventPerSecond / 20f * (wet(p) ? h.wetMultiplier : 1f));
		}
	}

	/** In Inferno: the snow and ice within {@link HellflameConfig#MELT_RADIUS} of his feet melt (where he may build). Returns how many. */
	static int melt(ServerPlayer p) {
		ServerLevel level = p.serverLevel();
		BlockPos feet = p.blockPosition();
		int r = HellflameConfig.MELT_RADIUS;
		int n = 0;
		for (BlockPos q : BlockPos.betweenClosed(feet.offset(-r, -1, -r), feet.offset(r, 2, r))) {
			BlockState st = level.getBlockState(q);
			boolean snow = st.is(Blocks.SNOW) || st.is(Blocks.SNOW_BLOCK) || st.is(Blocks.POWDER_SNOW);
			boolean ice = st.is(Blocks.ICE) || st.is(Blocks.FROSTED_ICE);
			if (!snow && !ice || !p.mayBuild() || !level.mayInteract(p, q)) {
				continue;
			}
			BlockState to = ice && !level.dimensionType().ultraWarm() ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
			level.setBlock(q, to, 3);
			level.sendParticles(QuirkParticles.STEAM, q.getX() + 0.5, q.getY() + 0.8, q.getZ() + 0.5, 2, 0.2, 0.1, 0.2, 0.02);
			n++;
		}
		return n;
	}

	/** Test hook: pretend the last fire move was long ago (so the Heat starts venting now). */
	public static void coolForTest(ServerPlayer p) {
		session(p).lastFireAt = now(p) - 1000L;
	}

	/** Every running move ends and goes (revoke / death / logout / Erased / the burnout). The Heat and the burnout stay. */
	static void stopAll(ServerPlayer p) {
		Session s = peekSession(p);
		if (s == null) {
			return;
		}
		if (s.beam && Quirks.holds(p, Quirk.HELLFLAME)) {
			Quirks.setCooldown(p, HellflameConfig.HELLBLAST, HellflameConfig.HELLBLAST_COOLDOWN);
		}
		boolean wasFlying = s.flight || s.promAt != 0L;
		s.flight = false;
		floatTolerance(p, s, false);
		if (wasFlying) {
			p.resetFallDistance();
			if (!p.onGround() && p.isAlive()) {
				p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0, false, false, true));
			}
		}
		AttributeInstance armor = p.getAttribute(Attributes.ARMOR);
		if (armor != null) {
			armor.removeModifier(CLOAK_ARMOR_ID);
		}
		s.beam = false;
		s.charge = false;
		s.released = false;
		s.burstUntil = 0L;
		s.dashUntil = 0L;
		s.meshUntil = 0L;
		s.meshed.clear();
		s.wallUntil = 0L;
		s.cloakUntil = 0L;
		s.barrageLeft = 0;
		s.pillarAt = 0L;
		s.vanishTarget = -1;
		s.promAt = 0L;
		s.fistStep = 0;
		java.util.Arrays.fill(s.pressAt, -1L);
		java.util.Arrays.fill(s.holdStarted, false);
		if (Quirks.holds(p, Quirk.HELLFLAME)) {
			Quirks.edit(p, n -> {
				for (String k : new String[] { Hellflame.V_FLIGHT, Hellflame.V_CLOAK, Hellflame.V_DASH, Hellflame.V_BURST, Hellflame.V_MESH, Hellflame.V_WALL,
						Hellflame.V_BARRAGE, Hellflame.V_PILLAR, Hellflame.V_CHARGE, Hellflame.V_BEAM, Hellflame.V_BEAM_UNTIL, Hellflame.V_PROM, Hellflame.V_FIST }) {
					n.vals.remove(k);
				}
			});
		}
	}
}
