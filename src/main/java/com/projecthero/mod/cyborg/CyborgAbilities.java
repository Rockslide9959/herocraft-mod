package com.projecthero.mod.cyborg;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.cyborg.data.CyborgState;
import com.projecthero.mod.cyborg.network.CyborgScanPayload;
import com.projecthero.mod.darkseid.entity.BoomTubeEntity;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.network.MoonKnightScholarSightPayload;
import com.projecthero.mod.squad.Squads;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Cyborg's twelve moves (v0.16.5, re-slotted in the balance pass). Each is gated by {@link #begin} (off cooldown, enough
 * Power Core; any move drops the hologram), runs as a small per-player session ticked from {@link #tick}, and deals damage
 * through {@link CyborgCombat}.
 *
 * <pre>
 *   R (1)  Sonic Cannon (hold to charge)   Shift+R  Sonic Burst
 *   G (2)  Hydraulic Punch                 Shift+G  Arm Saw
 *   X (3)  Jet Dash                        Shift+X  Grapple Claw
 *   Z (4)  Missile Barrage                 Shift+Z  Scan
 *   V (5)  Circuit Link (CyborgCircuit)    Shift+V  the Boom Tube menu (CyborgBoomTube)
 *   C (6)  Defense Matrix                  Shift+C  Self-Repair
 * </pre>
 * Jet Flight is double-tap jump in the air (no key).
 */
public final class CyborgAbilities {
	public static final String CANNON = "sonic_cannon";
	public static final String BURST = "sonic_burst";
	public static final String PUNCH = "hydraulic_punch";
	public static final String GRAPPLE = "grapple_claw";
	public static final String SAW = "arm_saw";
	public static final String DASH = "jet_dash";
	public static final String HOVER = "hover";
	public static final String MISSILES = "missile_barrage";
	public static final String SCAN = "scan";
	public static final String LINK = "circuit_link";
	public static final String BOOM = "boom_tube";
	public static final String MATRIX = "defense_matrix";
	public static final String REPAIR = "self_repair";

	private static final class Dash {
		final Vec3 dir;
		final long start;
		Vec3 last;
		double travelled;

		Dash(Vec3 dir, long start, Vec3 last) {
			this.dir = dir;
			this.start = start;
			this.last = last;
		}
	}

	private static final class Pull {
		final Vec3 to;
		final long start;

		Pull(Vec3 to, long start) {
			this.to = to;
			this.start = start;
		}
	}

	/** One micro-missile in flight. */
	private static final class Missile {
		Vec3 pos;
		Vec3 vel;
		final int target;
		final long born;

		Missile(Vec3 pos, Vec3 vel, int target, long born) {
			this.pos = pos;
			this.vel = vel;
			this.target = target;
			this.born = born;
		}
	}

	/** A Missile Barrage: missiles still to launch, and those in the air. */
	private static final class Barrage {
		int left = CyborgConfig.MISSILES;
		long next;
		final List<Integer> targets;
		final List<Missile> live = new ArrayList<>();

		Barrage(long next, List<Integer> targets) {
			this.next = next;
			this.targets = targets;
		}
	}

	private static final Map<UUID, Dash> DASHES = new ConcurrentHashMap<>();
	private static final Map<UUID, Pull> PULLS = new ConcurrentHashMap<>();
	/** Self-Repair: game time the healing ends. */
	private static final Map<UUID, Long> REPAIRS = new ConcurrentHashMap<>();
	/** Conversion effect start times. */
	private static final Map<UUID, Long> CONVERT_FX = new ConcurrentHashMap<>();
	private static final Map<UUID, Barrage> BARRAGES = new ConcurrentHashMap<>();
	/** Players hovering with the boot thrusters (so their flight can be taken back). */
	private static final Set<UUID> HOVER_GRANTED = ConcurrentHashMap.newKeySet();

	private CyborgAbilities() {
	}

	static void clearSessionState() {
		DASHES.clear();
		PULLS.clear();
		REPAIRS.clear();
		CONVERT_FX.clear();
		BARRAGES.clear();
		HOVER_GRANTED.clear();
		CyborgCircuit.clearSessionState();
	}

	public static void clear(ServerPlayer p) {
		UUID id = p.getUUID();
		DASHES.remove(id);
		PULLS.remove(id);
		REPAIRS.remove(id);
		CONVERT_FX.remove(id);
		BARRAGES.remove(id);
		CyborgCircuit.forget(id);
	}

	public static boolean running(ServerPlayer p, String id) {
		UUID u = p.getUUID();
		return switch (id) {
			case CANNON -> Cyborg.state(p).chargeStart != 0L;
			case DASH -> DASHES.containsKey(u);
			case GRAPPLE -> PULLS.containsKey(u);
			case HOVER -> Cyborg.state(p).hovering;
			case REPAIR -> REPAIRS.containsKey(u);
			case MATRIX -> Cyborg.matrixUp(p);
			case SAW -> Cyborg.state(p).fx.sawUntil > p.level().getGameTime();
			case MISSILES -> BARRAGES.containsKey(u);
			case LINK -> CyborgCircuit.holding(p);
			default -> false;
		};
	}

	// ---------------------------------------------------------------- the gate

	static boolean begin(ServerPlayer p, String id, float cost) {
		if (!Cyborg.canAct(p)) {
			return false;
		}
		int cd = Cyborg.cooldownRemaining(p, id);
		if (cd > 0) {
			Cyborg.say(p, "message.projecthero.cyborg.cooldown", ChatFormatting.GRAY,
					Component.translatable("projecthero.cyborg.ability." + id), String.format(java.util.Locale.ROOT, "%.1f", cd / 20.0f));
			return false;
		}
		if (!Cyborg.spendCore(p, cost)) {
			Cyborg.say(p, "message.projecthero.cyborg.low_core", ChatFormatting.AQUA, (int) Math.ceil(cost), (int) Math.floor(Cyborg.core(p)));
			return false;
		}
		return true;
	}

	static void cooldown(ServerPlayer p, String id, int ticks) {
		CyborgState n = Cyborg.state(p).copy();
		n.abilityReadyAt.put(id, p.level().getGameTime() + ticks);
		Cyborg.save(p, n);
	}

	private static ServerLevel level(ServerPlayer p) {
		return (ServerLevel) p.level();
	}

	private static void sound(ServerPlayer p, net.minecraft.sounds.SoundEvent s, float vol, float pitch) {
		level(p).playSound(null, p.getX(), p.getY(), p.getZ(), s, SoundSource.PLAYERS, vol, pitch);
	}

	static Vec3 hand(ServerPlayer p) {
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		Vec3 right = look.cross(new Vec3(0, 1, 0));
		right = right.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : right.normalize();
		return eye.add(look.scale(0.7)).add(right.scale(0.35)).add(0, -0.35, 0);
	}

	// ================================================================ the conversion

	static void beginConversionFx(ServerPlayer p) {
		CONVERT_FX.put(p.getUUID(), p.level().getGameTime());
		sound(p, SoundEvents.BEACON_ACTIVATE, 1.2f, 0.7f);
		sound(p, SoundEvents.ANVIL_USE, 0.6f, 0.6f);
	}

	private static void tickConversionFx(ServerPlayer p, long start, long now) {
		long age = now - start;
		if (age > CyborgConfig.CONVERT_TICKS) {
			CONVERT_FX.remove(p.getUUID());
			sound(p, SoundEvents.BEACON_POWER_SELECT, 1.2f, 1.2f);
			Cyborg.say(p, "message.projecthero.cyborg.online", ChatFormatting.AQUA);
			return;
		}
		ServerLevel level = level(p);
		if (age % 4 == 0) {
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 1.2, p.getZ(), 6, 0.35, 0.6, 0.35, 0.12);
		}
		if (age % 20 == 0) {
			sound(p, SoundEvents.ANVIL_PLACE, 0.25f, 1.8f);
			sound(p, SoundEvents.BEACON_AMBIENT, 0.8f, 1.5f);
		}
	}

	// ================================================================ R: Sonic Cannon (hold to charge, release to fire)

	public static void startCharge(ServerPlayer p) {
		if (Cyborg.state(p).chargeStart != 0L || !begin(p, CANNON, 0f)) {
			return;
		}
		if (Cyborg.core(p) + 1.0e-3f < CyborgConfig.CANNON_COST) {
			Cyborg.say(p, "message.projecthero.cyborg.low_core", ChatFormatting.AQUA, (int) CyborgConfig.CANNON_COST, (int) Math.floor(Cyborg.core(p)));
			return;
		}
		long now = p.level().getGameTime();
		CyborgState n = Cyborg.state(p).copy();
		n.chargeStart = now;
		n.animId = CyborgState.ANIM_CANNON;
		n.animStart = now;
		Cyborg.save(p, n);
		sound(p, SoundEvents.BEACON_POWER_SELECT, 0.8f, 1.8f);
	}

	/** Released: the beam fires with whatever charge built up. */
	public static void fireCannon(ServerPlayer p) {
		CyborgState s = Cyborg.state(p);
		if (s.chargeStart == 0L) {
			return;
		}
		long now = p.level().getGameTime();
		float charge = Math.min(1f, (now - s.chargeStart) / (float) CyborgConfig.CANNON_CHARGE_TICKS);
		CyborgState n = s.copy();
		n.chargeStart = 0L;
		Cyborg.save(p, n);
		if (!Cyborg.combat(p) || !Cyborg.spendCore(p, CyborgConfig.CANNON_COST)) {
			return;
		}
		cooldown(p, CANNON, CyborgConfig.CANNON_COOLDOWN);
		LivingEntity[] hit = new LivingEntity[1];
		Vec3 end = CyborgCombat.ray(p, CyborgConfig.CANNON_RANGE, 0.4 + 0.4 * charge, hit);
		float dmg = CyborgConfig.CANNON_MIN_DAMAGE + (CyborgConfig.CANNON_MAX_DAMAGE - CyborgConfig.CANNON_MIN_DAMAGE) * charge;
		if (hit[0] != null) {
			CyborgCombat.strike(p, hit[0], p.position(), dmg, 0.6 + 1.4 * charge, 0.2 + 0.3 * charge);
		}
		CyborgState b = Cyborg.state(p).copy();
		b.fx.beamUntil = now + 6;
		b.fx.beamEnd = List.of(end.x, end.y, end.z);
		b.animId = CyborgState.ANIM_CANNON;
		b.animStart = now - CyborgConfig.CANNON_CHARGE_TICKS; // straight into the recoil part of the pose
		Cyborg.save(p, b);
		ServerLevel level = level(p);
		level.sendParticles(Cyborg.BLUE_BIG, end.x, end.y, end.z, 12 + (int) (charge * 20), 0.3, 0.3, 0.3, 0.0);
		level.sendParticles(ParticleTypes.SONIC_BOOM, end.x, end.y, end.z, 1, 0, 0, 0, 0);
		sound(p, SoundEvents.WARDEN_SONIC_BOOM, 0.5f + 0.5f * charge, 1.6f - 0.4f * charge);
	}

	private static void tickCharge(ServerPlayer p, CyborgState s, long now) {
		long age = now - s.chargeStart;
		if (!Cyborg.combat(p) || !p.isAlive() || age > 200) {
			CyborgState n = s.copy();
			n.chargeStart = 0L;
			Cyborg.save(p, n);
			return;
		}
		if (age % 3 == 0) {
			Vec3 h = hand(p);
			level(p).sendParticles(Cyborg.BLUE, h.x, h.y, h.z, 2 + (int) Math.min(6, age / 5), 0.08, 0.08, 0.08, 0.0);
		}
		if (age == CyborgConfig.CANNON_CHARGE_TICKS) {
			sound(p, SoundEvents.BEACON_ACTIVATE, 0.6f, 2.0f);
		}
	}

	// ================================================================ Shift+R: Sonic Burst

	public static void burst(ServerPlayer p) {
		if (!begin(p, BURST, CyborgConfig.BURST_COST)) {
			return;
		}
		cooldown(p, BURST, CyborgConfig.BURST_COOLDOWN);
		Cyborg.setAnim(p, CyborgState.ANIM_BURST);
		ServerLevel level = level(p);
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		for (LivingEntity e : CyborgCombat.within(p, eye, CyborgConfig.BURST_RANGE, e -> CyborgCombat.isTarget(p, e))) {
			Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eye);
			if (to.lengthSqr() < 1.0e-4 || to.normalize().dot(look) >= CyborgConfig.BURST_CONE) {
				CyborgCombat.strike(p, e, eye, CyborgConfig.BURST_DAMAGE, 2.0, 0.35);
			}
		}
		for (int i = 1; i <= 6; i++) {
			Vec3 c = eye.add(look.scale(i));
			level.sendParticles(Cyborg.BLUE, c.x, c.y, c.z, 4 + i * 2, i * 0.12, i * 0.12, i * 0.12, 0.0);
		}
		level.sendParticles(ParticleTypes.SONIC_BOOM, eye.x + look.x * 2, eye.y + look.y * 2, eye.z + look.z * 2, 1, 0, 0, 0, 0);
		sound(p, SoundEvents.WARDEN_SONIC_BOOM, 0.8f, 1.2f);
	}

	// ================================================================ G: Hydraulic Punch

	public static void punch(ServerPlayer p) {
		if (!begin(p, PUNCH, CyborgConfig.PUNCH_COST)) {
			return;
		}
		cooldown(p, PUNCH, CyborgConfig.PUNCH_COOLDOWN);
		Cyborg.setAnim(p, CyborgState.ANIM_PUNCH);
		LivingEntity[] hit = new LivingEntity[1];
		Vec3 end = CyborgCombat.ray(p, CyborgConfig.PUNCH_RANGE, 0.5, hit);
		ServerLevel level = level(p);
		sound(p, SoundEvents.PISTON_EXTEND, 1.0f, 0.6f);
		if (hit[0] != null && CyborgCombat.isTarget(p, hit[0])) {
			LivingEntity t = hit[0];
			if (t instanceof Player tp && tp.isBlocking()) {
				// guard break: the shield is knocked aside for 5 s
				tp.getCooldowns().addCooldown(Items.SHIELD, 100);
				tp.stopUsingItem();
				level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.SHIELD_BREAK, SoundSource.PLAYERS, 1.0f, 0.8f);
			}
			CyborgCombat.strike(p, t, p.position(), CyborgConfig.PUNCH_DAMAGE, 1.6, 0.3);
			level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.6f, 1.4f);
		}
		level.sendParticles(ParticleTypes.CLOUD, end.x, end.y, end.z, 6, 0.15, 0.15, 0.15, 0.05);
	}

	// ================================================================ Shift+X: Grapple Claw

	public static void grapple(ServerPlayer p) {
		if (!begin(p, GRAPPLE, 0f)) {
			return;
		}
		LivingEntity[] hit = new LivingEntity[1];
		Vec3 end = CyborgCombat.ray(p, CyborgConfig.GRAPPLE_RANGE, 0.3, hit);
		ServerLevel level = level(p);
		Vec3 from = hand(p);
		if (hit[0] == null && end.distanceToSqr(p.getEyePosition()) >= (CyborgConfig.GRAPPLE_RANGE - 0.5) * (CyborgConfig.GRAPPLE_RANGE - 0.5)) {
			Cyborg.say(p, "message.projecthero.cyborg.grapple_miss", ChatFormatting.GRAY);
			return; // nothing in reach: no cooldown
		}
		if (!Cyborg.spendCore(p, CyborgConfig.GRAPPLE_COST)) {
			Cyborg.say(p, "message.projecthero.cyborg.low_core", ChatFormatting.AQUA, (int) CyborgConfig.GRAPPLE_COST, (int) Math.floor(Cyborg.core(p)));
			return;
		}
		cooldown(p, GRAPPLE, CyborgConfig.GRAPPLE_COOLDOWN);
		Cyborg.setAnim(p, CyborgState.ANIM_GRAPPLE);
		AbilityHelpers.line(level, from, end, ParticleTypes.ELECTRIC_SPARK, 3.0);
		sound(p, SoundEvents.CROSSBOW_SHOOT, 1.0f, 0.6f);
		sound(p, SoundEvents.CHAIN_PLACE, 1.0f, 1.2f);
		// the cable: out to the claw, held while it pulls
		long now = p.level().getGameTime();
		Vec3 endF = end;
		LivingEntity caught = hit[0];
		CyborgState cs = Cyborg.state(p).copy();
		cs.fx.cableUntil = now + (caught != null ? 10 : 32);
		cs.fx.cableEnd = List.of(endF.x, endF.y, endF.z);
		cs.fx.cableTarget = caught != null ? caught.getId() : -1;
		Cyborg.save(p, cs);
		if (hit[0] != null) {
			LivingEntity t = hit[0];
			if (CyborgCombat.isTarget(p, t) && !CyborgCombat.isBoss(t) && !(t instanceof Player && !com.projecthero.mod.combat.HeroTargets.pvpOn(p))) {
				Vec3 pull = p.position().subtract(t.position());
				double dist = pull.length();
				if (dist > 1.5) {
					t.setDeltaMovement(pull.normalize().scale(Math.min(2.2, 0.4 + dist * 0.12)).add(0, 0.4, 0));
					t.hurtMarked = true;
				}
			}
			return;
		}
		PULLS.put(p.getUUID(), new Pull(end, p.level().getGameTime()));
	}

	private static void tickPull(ServerPlayer p, Pull pull, long now) {
		Vec3 to = pull.to.subtract(p.position());
		if (now - pull.start > 45 || to.length() < 1.6 || !p.isAlive()) {
			PULLS.remove(p.getUUID());
			p.resetFallDistance();
			CyborgState n = Cyborg.state(p).copy();
			n.fx.cableUntil = now + 2; // reel in
			Cyborg.save(p, n);
			return;
		}
		AbilityHelpers.launchSelf(p, to.normalize().scale(1.4).add(0, 0.12, 0));
		if ((now - pull.start) % 2 == 0) {
			AbilityHelpers.line(level(p), hand(p), pull.to, ParticleTypes.ELECTRIC_SPARK, 1.5);
		}
	}

	// ================================================================ X: Jet Dash
	//
	// v0.16.5 (user): "a 20 block dash that uses the jetpack" -- the jetpack builds on, he blasts 20 blocks along his look
	// (up and down too), and it folds away again unless he is flying.

	public static void dash(ServerPlayer p) {
		if (DASHES.containsKey(p.getUUID()) || !begin(p, DASH, 0f)) {
			return;
		}
		cooldown(p, DASH, CyborgConfig.DASH_COOLDOWN);
		Cyborg.setAnim(p, CyborgState.ANIM_DASH);
		Vec3 dir = p.getLookAngle();
		if (p.onGround() && dir.y < 0.0) {
			dir = new Vec3(dir.x, 0.0, dir.z); // on the ground, never into it
		}
		dir = dir.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : dir.normalize();
		long now = p.level().getGameTime();
		CyborgState n = Cyborg.state(p).copy();
		if (!n.hovering && n.fx.jetDashUntil <= now) {
			n.fx.jetChange = now;
		}
		n.fx.jetDashUntil = now + CyborgConfig.DASH_MAX_TICKS + 2;
		Cyborg.save(p, n);
		DASHES.put(p.getUUID(), new Dash(dir, now, p.position()));
		sound(p, SoundEvents.FIREWORK_ROCKET_LAUNCH, 1.2f, 0.7f);
		sound(p, SoundEvents.BEACON_POWER_SELECT, 0.6f, 1.8f);
	}

	private static void tickDash(ServerPlayer p, Dash d, long now) {
		d.travelled += p.position().subtract(d.last).length();
		d.last = p.position();
		long age = now - d.start;
		if (d.travelled >= CyborgConfig.DASH_DISTANCE || age > CyborgConfig.DASH_MAX_TICKS || (age > 2 && (p.horizontalCollision
				|| (d.dir.y < -0.3 && p.onGround()))) || !p.isAlive()) {
			DASHES.remove(p.getUUID());
			AbilityHelpers.launchSelf(p, p.getDeltaMovement().scale(0.2));
			p.resetFallDistance();
			// the jetpack folds away (unless he is flying on it)
			CyborgState n = Cyborg.state(p).copy();
			n.fx.jetDashUntil = 0L;
			if (!n.hovering) {
				n.fx.jetChange = now;
			}
			Cyborg.save(p, n);
			return;
		}
		AbilityHelpers.launchSelf(p, d.dir.scale(CyborgConfig.DASH_SPEED));
		p.resetFallDistance();
		if (age % 2 == 0) {
			Vec3 back = p.position().subtract(d.dir.scale(0.6)).add(0, 0.9, 0);
			level(p).sendParticles(ParticleTypes.ELECTRIC_SPARK, back.x, back.y, back.z, 2, 0.1, 0.1, 0.1, 0.02);
			level(p).sendParticles(Cyborg.BLUE, back.x, back.y, back.z, 2, 0.12, 0.12, 0.12, 0.0);
		}
	}

	// ================================================================ double-tap jump: Jet Flight (toggle)
	//
	// v0.16.5 (user): "able to fly by double pressing space bar, doing so he projects energy from his feet and builds a
	// jetpack on his back". The movement is the shared directional flight (client.flight.DirectionalFlight, the Max Steel
	// numbers); the jetpack building on and the energy jets are client-side (client.cyborg.CyborgJetpack).

	public static void toggleHover(ServerPlayer p) {
		if (Cyborg.state(p).hovering) {
			stopHover(p);
			sound(p, SoundEvents.BEACON_DEACTIVATE, 0.6f, 1.8f);
			return;
		}
		if (!begin(p, HOVER, 0f)) {
			return;
		}
		if (Cyborg.core(p) < CyborgConfig.HOVER_COST_PER_SECOND) {
			Cyborg.say(p, "message.projecthero.cyborg.low_core", ChatFormatting.AQUA, (int) CyborgConfig.HOVER_COST_PER_SECOND,
					(int) Math.floor(Cyborg.core(p)));
			return;
		}
		CyborgState n = Cyborg.state(p).copy();
		n.hovering = true;
		n.fx.jetChange = p.level().getGameTime();
		Cyborg.save(p, n);
		if (!p.getAbilities().mayfly) {
			HOVER_GRANTED.add(p.getUUID());
			p.getAbilities().mayfly = true;
		}
		p.getAbilities().flying = true;
		p.onUpdateAbilities();
		AbilityHelpers.launchSelf(p, p.getDeltaMovement().add(0, 0.35, 0));
		sound(p, SoundEvents.FIREWORK_ROCKET_LAUNCH, 0.8f, 1.4f);
	}

	/** Ends the hover and gives back exactly the flight it lent (creative / other powers keep theirs). */
	public static void stopHover(ServerPlayer p) {
		CyborgState s = p.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.CYBORG_STATE, null);
		if (s != null && s.hovering) {
			CyborgState n = s.copy();
			n.hovering = false;
			n.fx.jetChange = p.level().getGameTime();
			Cyborg.save(p, n);
		}
		if (HOVER_GRANTED.remove(p.getUUID())) {
			p.getAbilities().mayfly = false;
			p.getAbilities().flying = false;
			p.onUpdateAbilities();
			p.resetFallDistance();
		}
	}

	private static void tickHover(ServerPlayer p, long now) {
		if (!Cyborg.combat(p) || !p.isAlive()) {
			stopHover(p);
			return;
		}
		if (now % 5 == 0 && !Cyborg.spendCore(p, CyborgConfig.HOVER_COST_PER_SECOND / 4f)) {
			Cyborg.say(p, "message.projecthero.cyborg.core_empty", ChatFormatting.AQUA);
			stopHover(p);
			return;
		}
		// the shared landing rule: touching down (past the take-off grace, not rising) folds the jetpack away
		if (com.projecthero.mod.flight.FlightLanding.landed(p, Cyborg.state(p).fx.jetChange)) {
			stopHover(p);
			sound(p, SoundEvents.BEACON_DEACTIVATE, 0.5f, 1.9f);
			return;
		}
		if (!p.getAbilities().flying && !p.onGround()) {
			p.getAbilities().flying = true;
			p.onUpdateAbilities();
		}
		p.resetFallDistance();
		if (now % 3 == 0) {
			// sparks off the energy jets (the jets themselves are drawn client-side)
			level(p).sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() - 0.15, p.getZ(), 1, 0.1, 0.05, 0.1, 0.01);
		}
		if (now % 20 == 0) {
			sound(p, SoundEvents.BEACON_AMBIENT, 0.35f, 1.8f);
		}
	}

	// ================================================================ Shift+G: Arm Saw
	//
	// v0.16.5 (user: "give Shift+G another move"): a buzzsaw builds on his forearm for 8 s -- +10 melee on top of his +9,
	// and every hit with it knocks a raised shield aside (CyborgDamage). The disc is client-side (CyborgTech).

	public static void saw(ServerPlayer p) {
		if (Cyborg.state(p).fx.sawUntil > p.level().getGameTime() || !begin(p, SAW, CyborgConfig.SAW_COST)) {
			return;
		}
		cooldown(p, SAW, CyborgConfig.SAW_COOLDOWN);
		long now = p.level().getGameTime();
		CyborgState n = Cyborg.state(p).copy();
		n.fx.sawUntil = now + CyborgConfig.SAW_TICKS;
		n.animId = CyborgState.ANIM_SAW;
		n.animStart = now;
		Cyborg.save(p, n);
		Cyborg.reconcile(p);
		sound(p, SoundEvents.GRINDSTONE_USE, 1.0f, 1.4f);
		sound(p, SoundEvents.PISTON_EXTEND, 0.8f, 1.6f);
	}

	private static void tickSaw(ServerPlayer p, CyborgState s, long now) {
		if (s.fx.sawUntil <= now) {
			CyborgState n = s.copy();
			n.fx.sawUntil = 0L;
			Cyborg.save(p, n);
			Cyborg.reconcile(p);
			sound(p, SoundEvents.PISTON_CONTRACT, 0.6f, 1.6f);
			return;
		}
		if ((now - s.animStart) % 12 == 0) {
			sound(p, SoundEvents.GRINDSTONE_USE, 0.25f, 1.9f);
		}
	}

	// ================================================================ Z: Missile Barrage
	//
	// v0.16.5 (user: "change the Z key to a move that deals damage"): a launcher pops up on his shoulder and fires eight
	// homing micro-missiles, two ticks apart, spread across the targets in front of him (or straight ahead if there are
	// none). Each bursts for 5 in a 2.5-block blast -- no block damage. Simulated here; the trails are particles and the
	// launcher is client-side (CyborgTech).

	public static void missiles(ServerPlayer p) {
		if (BARRAGES.containsKey(p.getUUID()) || !begin(p, MISSILES, CyborgConfig.MISSILE_COST)) {
			return;
		}
		cooldown(p, MISSILES, CyborgConfig.MISSILE_COOLDOWN);
		Cyborg.setAnim(p, CyborgState.ANIM_MISSILES);
		// targets in front, nearest to the crosshair first
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		List<LivingEntity> found = CyborgCombat.within(p, eye, CyborgConfig.MISSILE_RANGE, e -> CyborgCombat.isTarget(p, e) && e != p
				&& e.isAlive() && p.hasLineOfSight(e));
		found.removeIf(e -> e.getEyePosition().subtract(eye).normalize().dot(look) < 0.55);
		found.sort((a, b) -> Double.compare(b.getEyePosition().subtract(eye).normalize().dot(look), a.getEyePosition().subtract(eye).normalize().dot(look)));
		List<Integer> ids = new ArrayList<>();
		for (int i = 0; i < Math.min(4, found.size()); i++) {
			ids.add(found.get(i).getId());
		}
		BARRAGES.put(p.getUUID(), new Barrage(p.level().getGameTime() + 4, ids));
		sound(p, SoundEvents.PISTON_EXTEND, 0.8f, 1.8f);
		sound(p, SoundEvents.CROSSBOW_LOADING_END.value(), 1.0f, 1.4f);
	}

	/** The launcher on the shoulder opposite his cannon arm. */
	private static Vec3 launcher(ServerPlayer p) {
		Vec3 look = p.getLookAngle();
		Vec3 right = look.cross(new Vec3(0, 1, 0));
		right = right.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : right.normalize();
		double side = p.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT ? -1.0 : 1.0;
		return p.position().add(0, 1.75, 0).add(right.scale(0.35 * side)).subtract(look.scale(0.1));
	}

	private static void tickBarrage(ServerPlayer p, Barrage b, long now) {
		ServerLevel level = level(p);
		if (!p.isAlive()) {
			BARRAGES.remove(p.getUUID());
			return;
		}
		if (b.left > 0 && now >= b.next) {
			int i = CyborgConfig.MISSILES - b.left;
			Vec3 look = p.getLookAngle();
			Vec3 right = look.cross(new Vec3(0, 1, 0));
			right = right.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : right.normalize();
			// fanned out up and to the sides, then they curve in
			double fan = ((i % 4) - 1.5) * 0.28;
			Vec3 vel = look.scale(0.7).add(right.scale(fan)).add(0, 0.35 + (i % 2) * 0.12, 0);
			int target = b.targets.isEmpty() ? -1 : b.targets.get(i % b.targets.size());
			b.live.add(new Missile(launcher(p), vel, target, now));
			b.left--;
			b.next = now + CyborgConfig.MISSILE_INTERVAL;
			Vec3 l = launcher(p);
			level.sendParticles(ParticleTypes.SMOKE, l.x, l.y, l.z, 4, 0.05, 0.05, 0.05, 0.02);
			level.playSound(null, l.x, l.y, l.z, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 0.6f, 1.6f + 0.05f * i);
		}
		java.util.Iterator<Missile> it = b.live.iterator();
		while (it.hasNext()) {
			Missile m = it.next();
			Entity t = m.target >= 0 ? level.getEntity(m.target) : null;
			long age = now - m.born;
			double speed = Math.min(CyborgConfig.MISSILE_SPEED, 0.5 + age * 0.12);
			if (t instanceof LivingEntity lt && lt.isAlive() && age >= 3) {
				Vec3 want = lt.position().add(0, lt.getBbHeight() * 0.55, 0).subtract(m.pos);
				Vec3 steer = want.lengthSqr() < 1.0e-4 ? m.vel : want.normalize();
				m.vel = m.vel.normalize().scale(0.55).add(steer.scale(0.45)).normalize().scale(speed);
			} else {
				Vec3 dir = age < 3 ? m.vel : m.vel.normalize().scale(0.6).add(p.getLookAngle().scale(0.4));
				m.vel = dir.normalize().scale(speed);
			}
			Vec3 next = m.pos.add(m.vel);
			net.minecraft.world.phys.BlockHitResult bhr = level.clip(new ClipContext(m.pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
			Vec3 boom = null;
			if (bhr.getType() == HitResult.Type.BLOCK) {
				boom = bhr.getLocation();
			} else {
				for (LivingEntity e : CyborgCombat.within(p, next, 1.1, e -> CyborgCombat.isTarget(p, e) && e != p && e.isAlive())) {
					boom = next;
					break;
				}
			}
			if (boom == null && age > CyborgConfig.MISSILE_LIFE) {
				boom = next;
			}
			// the trail
			level.sendParticles(ParticleTypes.SMOKE, m.pos.x, m.pos.y, m.pos.z, 1, 0.02, 0.02, 0.02, 0.0);
			level.sendParticles(Cyborg.BLUE, next.x, next.y, next.z, 1, 0.0, 0.0, 0.0, 0.0);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, next.x, next.y, next.z, 1, 0.02, 0.02, 0.02, 0.01);
			if (boom != null) {
				final Vec3 at = boom;
				for (LivingEntity e : CyborgCombat.within(p, at, CyborgConfig.MISSILE_BLAST, e -> CyborgCombat.isTarget(p, e) && e != p)) {
					CyborgCombat.strike(p, e, at, CyborgConfig.MISSILE_DAMAGE, 0.4, 0.15);
				}
				level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0, 0, 0, 0);
				level.sendParticles(Cyborg.BLUE_BIG, at.x, at.y, at.z, 6, 0.3, 0.3, 0.3, 0.0);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.5f, 1.7f);
				it.remove();
			} else {
				m.pos = next;
			}
		}
		if (b.left <= 0 && b.live.isEmpty()) {
			BARRAGES.remove(p.getUUID());
		}
	}

	// ================================================================ Shift+Z: Scan

	public static void scan(ServerPlayer p) {
		if (!begin(p, SCAN, 0f)) {
			return;
		}
		cooldown(p, SCAN, CyborgConfig.SCAN_COOLDOWN);
		long now = p.level().getGameTime();
		CyborgState n = Cyborg.state(p).copy();
		n.scanUntil = now + CyborgConfig.SCAN_TICKS;
		n.animId = CyborgState.ANIM_SCAN;
		n.animStart = now;
		Cyborg.save(p, n);
		ServerLevel level = level(p);
		// creatures: outlined for him alone
		List<Integer> ids = new ArrayList<>();
		for (LivingEntity e : CyborgCombat.within(p, p.position(), CyborgConfig.SCAN_RANGE, e -> !(e instanceof Player && e == p))) {
			if (ids.size() < 256) {
				ids.add(e.getId());
			}
		}
		ServerPlayNetworking.send(p, new CyborgScanPayload(ids, CyborgConfig.SCAN_TICKS));
		// ores: through-wall boxes (Moon Knight's Scholar's Sight renderer), nearest first
		List<long[]> ores = new ArrayList<>();
		BlockPos c = p.blockPosition();
		int r = (int) CyborgConfig.SCAN_RANGE;
		int vr = 16;
		for (BlockPos q : BlockPos.betweenClosed(c.offset(-r, -vr, -r), c.offset(r, vr, r))) {
			BlockState st = level.getBlockState(q);
			if (st.is(ConventionalBlockTags.ORES)) {
				ores.add(new long[] { q.asLong(), (long) c.distSqr(q), oreColour(st) });
			}
		}
		ores.sort((a, b) -> Long.compare(a[1], b[1]));
		int count = Math.min(CyborgConfig.SCAN_MAX_ORES, ores.size());
		long[] positions = new long[count];
		int[] colours = new int[count];
		for (int i = 0; i < count; i++) {
			positions[i] = ores.get(i)[0];
			colours[i] = (int) ores.get(i)[2];
		}
		ServerPlayNetworking.send(p, new MoonKnightScholarSightPayload(positions, colours, CyborgConfig.SCAN_TICKS));
		level.sendParticles(Cyborg.BLUE, p.getX(), p.getEyeY(), p.getZ(), 30, 1.5, 0.5, 1.5, 0.0);
		sound(p, SoundEvents.BEACON_AMBIENT, 1.2f, 2.0f);
		sound(p, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0f, 1.6f);
		Cyborg.say(p, "message.projecthero.cyborg.scanned", ChatFormatting.AQUA, ids.size(), count);
	}

	private static int oreColour(BlockState st) {
		String id = BuiltInRegistries.BLOCK.getKey(st.getBlock()).getPath();
		if (id.contains("diamond")) {
			return 0x5CF0F0;
		}
		if (id.contains("emerald")) {
			return 0x3CE070;
		}
		if (id.contains("gold")) {
			return 0xFFD040;
		}
		if (id.contains("redstone")) {
			return 0xFF3030;
		}
		if (id.contains("lapis")) {
			return 0x3050FF;
		}
		if (id.contains("iron")) {
			return 0xE0B090;
		}
		if (id.contains("copper")) {
			return 0xE07040;
		}
		if (id.contains("debris")) {
			return 0xA06040;
		}
		return 0xC0C0C0;
	}

	// ================================================================ V: Circuit Link -> CyborgCircuit; Shift+V: the Boom Tube menu -> CyborgBoomTube

	// ================================================================ C: Defense Matrix / Shift+C: Self-Repair

	public static void matrix(ServerPlayer p) {
		if (Cyborg.matrixUp(p) || !begin(p, MATRIX, 0f)) {
			return;
		}
		cooldown(p, MATRIX, CyborgConfig.MATRIX_COOLDOWN);
		CyborgState n = Cyborg.state(p).copy();
		n.shieldUntil = p.level().getGameTime() + CyborgConfig.MATRIX_TICKS;
		n.shieldHp = CyborgConfig.MATRIX_HP;
		Cyborg.save(p, n);
		sound(p, SoundEvents.BEACON_ACTIVATE, 1.0f, 1.8f);
	}

	public static void repair(ServerPlayer p) {
		if (REPAIRS.containsKey(p.getUUID())) {
			return;
		}
		if (!p.getAbilities().instabuild && p.getInventory().countItem(Items.IRON_INGOT) < CyborgConfig.REPAIR_IRON) {
			if (Cyborg.canAct(p)) {
				Cyborg.say(p, "message.projecthero.cyborg.need_iron", ChatFormatting.GRAY, CyborgConfig.REPAIR_IRON);
			}
			return;
		}
		if (!begin(p, REPAIR, 0f)) {
			return;
		}
		if (!p.getAbilities().instabuild) {
			int left = CyborgConfig.REPAIR_IRON;
			for (int i = 0; i < p.getInventory().getContainerSize() && left > 0; i++) {
				ItemStack st = p.getInventory().getItem(i);
				if (st.is(Items.IRON_INGOT)) {
					int take = Math.min(left, st.getCount());
					st.shrink(take);
					left -= take;
				}
			}
		}
		cooldown(p, REPAIR, CyborgConfig.REPAIR_COOLDOWN);
		Cyborg.setAnim(p, CyborgState.ANIM_REPAIR);
		REPAIRS.put(p.getUUID(), p.level().getGameTime() + CyborgConfig.REPAIR_TICKS);
		sound(p, SoundEvents.ANVIL_USE, 0.6f, 1.6f);
	}

	private static void tickRepair(ServerPlayer p, long until, long now) {
		if (now >= until || !p.isAlive()) {
			REPAIRS.remove(p.getUUID());
			return;
		}
		if (now % 10 == 0) {
			p.heal(CyborgConfig.REPAIR_HEAL / (CyborgConfig.REPAIR_TICKS / 10f));
			level(p).sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 1.0, p.getZ(), 6, 0.3, 0.5, 0.3, 0.1);
			level(p).sendParticles(ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + 1.2, p.getZ(), 2, 0.3, 0.4, 0.3, 0.0);
			sound(p, SoundEvents.ANVIL_PLACE, 0.15f, 2.0f);
		}
	}

	// ================================================================ tick

	static void tick(ServerPlayer p) {
		UUID u = p.getUUID();
		long now = p.level().getGameTime();
		Long fx = CONVERT_FX.get(u);
		if (fx != null) {
			tickConversionFx(p, fx, now);
		}
		CyborgState s = Cyborg.state(p);
		if (s.chargeStart != 0L) {
			tickCharge(p, s, now);
		}
		if (s.hovering) {
			tickHover(p, now);
		}
		Dash d = DASHES.get(u);
		if (d != null) {
			tickDash(p, d, now);
		}
		Pull pull = PULLS.get(u);
		if (pull != null) {
			tickPull(p, pull, now);
		}
		Long rep = REPAIRS.get(u);
		if (rep != null) {
			tickRepair(p, rep, now);
		}
		if (s.fx.sawUntil != 0L) {
			tickSaw(p, Cyborg.state(p), now);
		}
		Barrage b = BARRAGES.get(u);
		if (b != null) {
			tickBarrage(p, b, now);
		}
		CyborgCircuit.tick(p, now);
		if (s.shieldUntil != 0L && s.shieldUntil <= now) {
			CyborgState n = s.copy();
			n.shieldUntil = 0L;
			n.shieldHp = 0f;
			Cyborg.save(p, n);
		}
	}
}
