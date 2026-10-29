package com.projecthero.mod.greenlantern.mech;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.projecthero.mod.greenlantern.GreenLantern;
import com.projecthero.mod.greenlantern.GreenLanternEnergy;
import com.projecthero.mod.greenlantern.GreenLanternRanks;
import com.projecthero.mod.greenlantern.GreenLanternSurvival;
import com.projecthero.mod.greenlantern.construct.GreenLanternConstructs;
import com.projecthero.mod.greenlantern.mech.MechComboKeys.Combo;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.network.GreenLanternCuePayload;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1 (user: "combo moves with the mech construct ... using the drill construct while in the mech activates a giant
 * drill attack like in gurren lagann and using energy blade while in the mech spawns in a giant katana for the mech to
 * wield"): the Hard-Light Mech's construct combos, server side. {@link MechComboKeys} decides which key fires each one
 * (Shift + the key the construct is bound to outside the mech, else a fixed free Shift key).
 *
 * <h2>Giga Drill (Mining Drill)</h2>
 * Hold the combo key: the right forearm becomes a huge spiral hard-light drill that spins up over 2 s (it drains charge,
 * the mech slows to a crawl, the drill follows the camera). Let go: the mech charges along the aim, boring a round
 * tunnel its own size through terrain (a PLAYER break path: build permission, claims, spawn protection, adventure mode
 * and every {@code PlayerBlockBreakEvents.BEFORE} guard; never bedrock / unbreakables / wither-immune blocks / block
 * entities / hard light, nothing harder than obsidian) and drilling through everything in its way with multi-hits that
 * carry them along. A full spin ends in <b>GIGA DRILL BREAK</b>: a giant drill flash, a crater, a huge last hit and a
 * screen shake. The drill then shrinks back into the arm; a cooldown follows.
 *
 * <h2>Hard-Light Katana (Energy Blade)</h2>
 * Tap the combo key: the mech draws a giant hard-light katana (tap again to sheathe it). While it is drawn R is a
 * three-cut chain, Shift+R the iaido draw-cut (a crescent of light that flies forward through enemies and cuts grass and
 * leaves, never terrain) and G a spinning sweep. Cuts that connect hit-stop and shake. It costs a little upkeep and is
 * sheathed when the pilot climbs out.
 *
 * <p>All state lives on the {@link HardLightMechEntity} (no static maps). Damage goes through the GL path (HeroTargets,
 * {@code GreenLanternRanks.power}, a player-attack source -- so bosses' threat tables credit the pilot).
 */
public final class GreenLanternMechCombos {
	// full-bright particles only (dust is lit by the world and goes grey-black at night against the glowing hard light)
	private static final ParticleOptions GREEN = ParticleTypes.GLOW;
	private static final ParticleOptions PALE = ParticleTypes.END_ROD;
	private static final ParticleOptions BIG = ParticleTypes.END_ROD;

	private GreenLanternMechCombos() {
	}

	// ================================================================ keys

	/**
	 * A mech key went down or up (from {@code GreenLanternMech#handleKey}). Returns true when a combo took it: Shift + a
	 * combo key, or the release of the key that is spinning the drill.
	 */
	static boolean key(HardLightMechEntity m, ServerPlayer p, AbilitySlot slot, boolean pressed) {
		if (!pressed) {
			if (m.drillSlot == slot && m.drillState() == HardLightMechEntity.DRILL_SPIN) {
				drillRelease(m, p);
				return true;
			}
			return false;
		}
		char base = MechComboKeys.baseOf(slot);
		if (base == 0 || !p.isShiftKeyDown()) {
			return false;
		}
		Combo c = MechComboKeys.comboOn(GreenLantern.state(p), base);
		if (c == null) {
			return false;
		}
		use(m, p, c, slot);
		return true;
	}

	/**
	 * Starts {@code c} ({@code slot} = the key that holds the Giga Drill's spin, or null from the Construct Menu, where the
	 * drill spins up to full by itself and lets go into a GIGA DRILL BREAK). Returns whether it started.
	 */
	public static boolean use(HardLightMechEntity m, ServerPlayer p, Combo c, AbilitySlot slot) {
		if (moveBusy(m) && c != Combo.KATANA) {
			return false; // a combo move is already playing
		}
		return switch (c) {
			case GIGA_DRILL -> {
				boolean ok = drillPress(m, p, slot);
				if (ok && slot == null) {
					m.schedule(GreenLanternMechConfig.MENU_DRILL_SPIN_TICKS, () -> {
						ServerPlayer pilot = m.pilot();
						if (pilot != null && m.drillSlot == null) {
							drillRelease(m, pilot);
						}
					});
				}
				yield ok;
			}
			case KATANA -> !moveBusy(m) && katanaToggle(m, p);
			case METEOR_MACE -> mace(m, p);
			case WRECKING_WHIRL -> whirl(m, p);
			case LANTERN_CANNON -> cannon(m, p);
		};
	}

	/**
	 * v0.17.1 (user: "if the player holds c while in the mech they can still open the construct menu, using the energy
	 * blade summons the katana and using the drill activates giga drill"): a construct used from the Construct Menu while
	 * piloting. A construct with a mech combo ({@link MechComboKeys#comboFor}) starts that combo; returns false for the
	 * rest (they are summoned as themselves).
	 */
	public static boolean fromMenu(ServerPlayer p, com.projecthero.mod.greenlantern.construct.ConstructType type) {
		HardLightMechEntity m = GreenLanternMech.mechOf(p);
		Combo c = type == null ? null : MechComboKeys.comboFor(type);
		if (m == null || c == null) {
			return false;
		}
		if (m.isActive() && !drillBusy(m)) {
			use(m, p, c, null);
		}
		return true; // a combo construct is never summoned as itself inside the mech
	}

	/** While the drill spins or charges the right arm is busy: every other mech move waits. */
	static boolean drillBusy(HardLightMechEntity m) {
		int d = m.drillState();
		return d == HardLightMechEntity.DRILL_SPIN || d == HardLightMechEntity.DRILL_CHARGE || moveBusy(m);
	}

	/** v0.17.1 round 2: a one-shot combo move (mace / whirl / cannon) is playing: every other move waits for it. */
	public static boolean moveBusy(HardLightMechEntity m) {
		return m.move() != HardLightMechEntity.MOVE_NONE;
	}

	/** Climbing out / dissolving / shattering: the katana goes, a drill stops where it is (no finisher). */
	public static void stopAll(HardLightMechEntity m) {
		if (m.katanaOut()) {
			m.setKatanaState(HardLightMechEntity.KATANA_NONE);
		}
		m.slashHeld = false;
		m.waveActive = false;
		if (m.drillState() != HardLightMechEntity.DRILL_NONE) {
			m.setDrillState(HardLightMechEntity.DRILL_NONE);
		}
		m.drillSlot = null;
		if (m.move() != HardLightMechEntity.MOVE_NONE) {
			m.setMove(HardLightMechEntity.MOVE_NONE);
		}
	}

	/** Per tick while the mech is active (from {@code GreenLanternMech#tickMech}). */
	static void tick(HardLightMechEntity m, ServerPlayer p) {
		tickDrill(m, p);
		tickKatana(m, p);
		tickWave(m, p);
		tickMove(m, p);
	}

	// ================================================================ v0.17.1 round 2: Meteor Mace / Wrecking Whirl / Lantern Cannon

	private static boolean startMove(HardLightMechEntity m, ServerPlayer p, int move, int cd, float cost, String anim) {
		if (!m.ready(cd)) {
			int s = (m.cooldownLeft(cd) + 19) / 20;
			p.displayClientMessage(Component.translatable("message.projecthero.green_lantern.mech.move_cooldown", s).withStyle(ChatFormatting.RED), true);
			return false;
		}
		if (!GreenLanternMech.pay(p, cost)) {
			return false;
		}
		if (m.katanaOut() && move != HardLightMechEntity.MOVE_CANNON) {
			sheathe(m); // the right hand takes the new weapon
		}
		m.punchHeld = false;
		m.slashHeld = false;
		m.setFlag(HardLightMechEntity.FLAG_BEAM, false);
		m.setFlag(HardLightMechEntity.FLAG_SHIELD, false);
		m.setMoveDir(p.getLookAngle());
		m.setMove(move);
		m.moveHitAny = false;
		m.play(anim);
		return true;
	}

	/** Emerald Mace: METEOR MACE -- the mace forms, goes up overhead in both hands and comes down in front. */
	public static boolean mace(HardLightMechEntity m, ServerPlayer p) {
		if (!startMove(m, p, HardLightMechEntity.MOVE_MACE, HardLightMechEntity.CD_MACE, GreenLanternMechConfig.MACE_COST, "mace_smash")) {
			return false;
		}
		ServerLevel level = (ServerLevel) m.level();
		GreenLanternMech.sound(level, m.position(), SoundEvents.BEACON_POWER_SELECT, 1.6f, 0.6f);
		m.schedule(6, () -> GreenLanternMech.sound(level, m.local(0, 7, 0), SoundEvents.ANVIL_PLACE, 0.8f, 0.5f));
		m.schedule(GreenLanternMechConfig.MACE_IMPACT_TICKS - 3, () -> GreenLanternMech.sound(level, m.local(0, 6, 1), GreenLanternMechSounds.KATANA_SWISH, 2.0f, 0.5f));
		m.schedule(GreenLanternMechConfig.MACE_IMPACT_TICKS, () -> maceImpact(m));
		popup(p, "mech_meteor_mace");
		return true;
	}

	/** Where the mace head comes down: on the ground, {@link GreenLanternMechConfig#MACE_REACH} ahead. */
	public static Vec3 maceImpactPoint(HardLightMechEntity m) {
		return m.local(-0.8, 0.0, GreenLanternMechConfig.MACE_REACH);
	}

	private static void maceImpact(HardLightMechEntity m) {
		ServerPlayer p = m.pilot();
		if (p == null || m.move() != HardLightMechEntity.MOVE_MACE) {
			return;
		}
		ServerLevel level = (ServerLevel) m.level();
		Vec3 c = maceImpactPoint(m);
		float dmg = GreenLanternMechConfig.MACE_DAMAGE * GreenLanternRanks.power(p);
		for (LivingEntity t : AbilityHelpers.living(level, c.add(0, 1.5, 0), GreenLanternMechConfig.MACE_RADIUS + 2.0,
				e -> GreenLanternMech.canHit(p, m, e) && e.getY() > m.getY() - 2.0 && e.getY() < m.getY() + 6.0)) {
			Vec3 rel = new Vec3(t.getX() - c.x, 0, t.getZ() - c.z);
			double d = rel.length();
			if (d > GreenLanternMechConfig.MACE_RADIUS + t.getBbWidth() * 0.5) {
				continue;
			}
			float k = (float) (1.0 - 0.5 * Math.min(1.0, d / GreenLanternMechConfig.MACE_RADIUS)); // full in the middle
			if (AbilityHelpers.hurtBurst(p, t, dmg * k)) {
				moveConnected(m, p);
			}
			Vec3 out = d < 0.3 ? m.local(0, 0, 1).subtract(m.position()) : GreenLanternMech.flat(rel);
			GreenLanternMech.push(t, out.scale(1.2).add(0, 1.15, 0));
		}
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 0.3, c.z, 1, 0, 0, 0, 0);
		level.sendParticles(BIG, c.x, c.y + 0.4, c.z, 70, 1.6, 0.4, 1.6, 0.25);
		level.sendParticles(GREEN, c.x, c.y + 0.6, c.z, 50, 2.4, 0.6, 2.4, 0.1);
		var rnd = level.getRandom();
		for (int i = 0; i < 24; i++) {
			double a = rnd.nextDouble() * Math.PI * 2, r = 1.0 + rnd.nextDouble() * GreenLanternMechConfig.MACE_RADIUS;
			BlockPos under = BlockPos.containing(c.x + Math.cos(a) * r, c.y - 0.5, c.z + Math.sin(a) * r);
			BlockState st = level.getBlockState(under);
			if (!st.isAir()) {
				level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, st), under.getX() + 0.5,
						under.getY() + 1.1, under.getZ() + 0.5, 6, 0.3, 0.1, 0.3, 0.2);
			}
		}
		GreenLanternMech.sound(level, c, SoundEvents.GENERIC_EXPLODE.value(), 2.2f, 0.6f);
		GreenLanternMech.sound(level, c, SoundEvents.ANVIL_LAND, 1.6f, 0.4f);
		m.markHit();
	}

	/** Wrecking Ball: WRECKING WHIRL -- the ball on its chain, swung round the mech twice. */
	public static boolean whirl(HardLightMechEntity m, ServerPlayer p) {
		if (!startMove(m, p, HardLightMechEntity.MOVE_WHIRL, HardLightMechEntity.CD_WHIRL, GreenLanternMechConfig.WHIRL_COST, "wrecking_whirl")) {
			return false;
		}
		ServerLevel level = (ServerLevel) m.level();
		GreenLanternMech.sound(level, m.position(), SoundEvents.CHAIN_PLACE, 2.0f, 0.5f);
		for (int t = GreenLanternMechConfig.WHIRL_START_TICKS; t < GreenLanternMechConfig.WHIRL_END_TICKS; t += 6) {
			m.schedule(t, () -> GreenLanternMech.sound(level, m.local(0, 4, 0), GreenLanternMechSounds.KATANA_SWISH, 2.0f, 0.45f));
		}
		popup(p, "mech_wrecking_whirl");
		return true;
	}

	/** Where the whirling ball is (world), at move tick {@code t} (the clip turns the body two full turns). */
	public static Vec3 whirlBall(HardLightMechEntity m, float t) {
		float a = whirlAngle(t);
		double r = GreenLanternMechConfig.WHIRL_RADIUS - 1.5;
		double yaw = Math.toRadians(m.getYRot()) + a;
		return new Vec3(m.getX() - Math.sin(yaw) * r, m.getY() + 3.2, m.getZ() + Math.cos(yaw) * r);
	}

	/** The whirl's turn (radians, from facing forward) at move tick {@code t}: wind-up, two turns, settle. */
	public static float whirlAngle(float t) {
		float s = GreenLanternMechConfig.WHIRL_START_TICKS, e = GreenLanternMechConfig.WHIRL_END_TICKS;
		float k = Mth.clamp((t - s) / (e - s), 0f, 1f);
		return (float) (Math.PI / 2 - k * Math.PI * 4);
	}

	private static void tickWhirl(HardLightMechEntity m, ServerPlayer p, long t) {
		if (t < GreenLanternMechConfig.WHIRL_START_TICKS || t >= GreenLanternMechConfig.WHIRL_END_TICKS) {
			return;
		}
		ServerLevel level = (ServerLevel) m.level();
		Vec3 ball = whirlBall(m, t);
		level.sendParticles(GREEN, ball.x, ball.y, ball.z, 3, 0.5, 0.5, 0.5, 0.02);
		if ((t - GreenLanternMechConfig.WHIRL_START_TICKS) % GreenLanternMechConfig.WHIRL_HIT_EVERY != 0) {
			return;
		}
		float dmg = GreenLanternMechConfig.WHIRL_DAMAGE * GreenLanternRanks.power(p);
		Vec3 c = m.position();
		for (LivingEntity e : AbilityHelpers.living(level, c.add(0, 3, 0), GreenLanternMechConfig.WHIRL_RADIUS + 2.0,
				x -> GreenLanternMech.canHit(p, m, x) && x.getY() > m.getY() - 2.0 && x.getY() < m.getY() + 7.0)) {
			Vec3 rel = new Vec3(e.getX() - c.x, 0, e.getZ() - c.z);
			if (rel.length() > GreenLanternMechConfig.WHIRL_RADIUS + e.getBbWidth() * 0.5 || rel.length() < 1.2) {
				continue;
			}
			if (AbilityHelpers.hurtBurst(p, e, dmg)) {
				moveConnected(m, p);
			}
			// flung outward and along the swing
			Vec3 out = GreenLanternMech.flat(rel);
			Vec3 along = new Vec3(-out.z, 0, out.x);
			GreenLanternMech.push(e, out.scale(1.3).add(along.scale(0.5)).add(0, 0.45, 0));
			Vec3 at = e.position().add(0, e.getBbHeight() * 0.6, 0);
			level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 8, 0.3, 0.3, 0.3, 0.3);
		}
	}

	/** Emerald Cannon: LANTERN CANNON -- the left forearm becomes a cannon, charges and fires one huge shell. */
	public static boolean cannon(HardLightMechEntity m, ServerPlayer p) {
		if (!startMove(m, p, HardLightMechEntity.MOVE_CANNON, HardLightMechEntity.CD_CANNON, GreenLanternMechConfig.CANNON_COST, "lantern_cannon")) {
			return false;
		}
		ServerLevel level = (ServerLevel) m.level();
		GreenLanternMech.sound(level, m.position(), SoundEvents.BEACON_ACTIVATE, 1.8f, 1.4f);
		m.schedule(GreenLanternMechConfig.CANNON_FIRE_TICKS, () -> cannonFire(m));
		popup(p, "mech_lantern_cannon");
		return true;
	}

	/** The cannon's muzzle (world): out past the left fist, along the aim. */
	public static Vec3 cannonMuzzle(HardLightMechEntity m, Vec3 dir) {
		return m.local(1.75, 4.2, 0.9).add(dir.scale(3.4));
	}

	private static void cannonFire(HardLightMechEntity m) {
		ServerPlayer p = m.pilot();
		if (p == null || m.move() != HardLightMechEntity.MOVE_CANNON) {
			return;
		}
		ServerLevel level = (ServerLevel) m.level();
		Vec3 dir = p.getLookAngle();
		m.setMoveDir(dir);
		Vec3 from = cannonMuzzle(m, dir);
		Vec3 eye = p.getEyePosition();
		Vec3 far = eye.add(dir.scale(GreenLanternMechConfig.CANNON_RANGE));
		net.minecraft.world.phys.BlockHitResult bh = level.clip(new net.minecraft.world.level.ClipContext(eye, far,
				net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, m));
		Vec3 end = bh.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? far : bh.getLocation();
		// the first enemy along the line stops the shell
		AABB sweep = new AABB(eye, end).inflate(1.5);
		double best = Double.MAX_VALUE;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, sweep, x -> GreenLanternMech.canHit(p, m, x))) {
			var hit = e.getBoundingBox().inflate(0.6).clip(eye, end);
			if (hit.isPresent() && hit.get().distanceToSqr(eye) < best) {
				best = hit.get().distanceToSqr(eye);
			}
		}
		if (best < Double.MAX_VALUE) {
			end = eye.add(dir.scale(Math.sqrt(best)));
		}
		m.markShot(end);
		float dmg = GreenLanternMechConfig.CANNON_DAMAGE * GreenLanternRanks.power(p);
		for (LivingEntity e : AbilityHelpers.living(level, end, GreenLanternMechConfig.CANNON_BLAST_RADIUS, x -> GreenLanternMech.canHit(p, m, x))) {
			double d = e.position().add(0, e.getBbHeight() * 0.5, 0).distanceTo(end);
			float k = (float) (1.0 - 0.6 * Math.min(1.0, d / GreenLanternMechConfig.CANNON_BLAST_RADIUS));
			if (AbilityHelpers.hurtBurst(p, e, dmg * k)) {
				moveConnected(m, p);
			}
			Vec3 out = e.position().subtract(end);
			out = out.lengthSqr() < 1.0e-4 ? dir : out.normalize();
			GreenLanternMech.push(e, out.scale(1.4).add(0, 0.6, 0));
		}
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, end.x, end.y, end.z, 1, 0, 0, 0, 0);
		level.sendParticles(BIG, end.x, end.y, end.z, 60, 1.4, 1.4, 1.4, 0.3);
		level.sendParticles(GREEN, from.x, from.y, from.z, 30, 0.4, 0.4, 0.4, 0.25);
		GreenLanternMech.sound(level, from, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 2.4f, 0.5f);
		GreenLanternMech.sound(level, from, SoundEvents.GENERIC_EXPLODE.value(), 1.2f, 1.4f);
		GreenLanternMech.sound(level, end, SoundEvents.GENERIC_EXPLODE.value(), 2.4f, 0.7f);
		m.impulse(dir.multiply(1, 0, 1).scale(-0.35)); // the recoil shoves the mech back a step
	}

	private static void tickMove(HardLightMechEntity m, ServerPlayer p) {
		int mv = m.move();
		if (mv == HardLightMechEntity.MOVE_NONE) {
			return;
		}
		long t = m.moveTicks();
		int len = switch (mv) {
			case HardLightMechEntity.MOVE_MACE -> GreenLanternMechConfig.MACE_TICKS;
			case HardLightMechEntity.MOVE_WHIRL -> GreenLanternMechConfig.WHIRL_TICKS;
			default -> GreenLanternMechConfig.CANNON_TICKS;
		};
		if (mv == HardLightMechEntity.MOVE_WHIRL) {
			tickWhirl(m, p, t);
		} else if (mv == HardLightMechEntity.MOVE_CANNON && t < GreenLanternMechConfig.CANNON_FIRE_TICKS) {
			m.setMoveDir(p.getLookAngle()); // the cannon tracks the camera while it charges
			Vec3 mz = cannonMuzzle(m, p.getLookAngle());
			((ServerLevel) m.level()).sendParticles(GREEN, mz.x, mz.y, mz.z, 2, 0.6, 0.6, 0.6, -0.08);
		}
		if (t >= len) {
			m.setMove(HardLightMechEntity.MOVE_NONE);
			int cd = switch (mv) {
				case HardLightMechEntity.MOVE_MACE -> HardLightMechEntity.CD_MACE;
				case HardLightMechEntity.MOVE_WHIRL -> HardLightMechEntity.CD_WHIRL;
				default -> HardLightMechEntity.CD_CANNON;
			};
			m.startCooldown(cd, HardLightMechEntity.CD_LENGTHS[cd]);
		}
	}

	/** A combo move connected: shake (clients read the mark) and Willpower XP once per move. */
	private static void moveConnected(HardLightMechEntity m, ServerPlayer p) {
		if (!m.moveHitAny) {
			m.moveHitAny = true;
			GreenLanternRanks.award(p, GreenLanternMechConfig.COMBO_XP);
		}
	}

	// ================================================================ Giga Drill

	public static boolean drillPress(HardLightMechEntity m, ServerPlayer p, AbilitySlot slot) {
		if (m.drillState() != HardLightMechEntity.DRILL_NONE) {
			return false;
		}
		if (!m.ready(HardLightMechEntity.CD_DRILL)) {
			int s = (m.cooldownLeft(HardLightMechEntity.CD_DRILL) + 19) / 20;
			p.displayClientMessage(Component.translatable("message.projecthero.green_lantern.mech.combo_cooldown", s).withStyle(ChatFormatting.RED), true);
			return false;
		}
		if (!GreenLanternMech.pay(p, GreenLanternMechConfig.DRILL_START_COST)) {
			return false;
		}
		if (m.katanaOut()) {
			m.setKatanaState(HardLightMechEntity.KATANA_NONE); // the arm becomes the drill: the katana goes
			m.slashHeld = false;
		}
		m.setFlag(HardLightMechEntity.FLAG_BEAM, false);
		m.setFlag(HardLightMechEntity.FLAG_SHIELD, false);
		m.punchHeld = false;
		m.drillSlot = slot;
		m.drillHeld = 0;
		m.setDrillPower(0f);
		m.setDrillDir(GreenLanternMech.aimDir(p));
		m.setDrillState(HardLightMechEntity.DRILL_SPIN);
		ServerLevel level = (ServerLevel) m.level();
		Vec3 arm = m.local(-1.75, 3.6, 1.2);
		level.sendParticles(ParticleTypes.FLASH, arm.x, arm.y, arm.z, 1, 0, 0, 0, 0);
		level.sendParticles(BIG, arm.x, arm.y, arm.z, 24, 0.5, 0.6, 0.5, 0.1);
		GreenLanternMech.sound(level, arm, SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 1.4f, 0.5f);
		GreenLanternMech.sound(level, arm, GreenLanternMechSounds.DRILL_SPIN, 1.4f, 0.6f);
		return true;
	}

	/** The spin key let go (or the hold ran out / the ring ran dry): charge -- or, barely spun, just wind down. */
	public static void drillRelease(HardLightMechEntity m, ServerPlayer p) {
		if (m.drillState() != HardLightMechEntity.DRILL_SPIN) {
			return;
		}
		m.drillSlot = null;
		int held = m.drillHeld;
		ServerLevel level = (ServerLevel) m.level();
		if (held < GreenLanternMechConfig.DRILL_MIN_SPIN_TICKS) {
			m.setDrillState(HardLightMechEntity.DRILL_RECOVER);
			m.startCooldown(HardLightMechEntity.CD_DRILL, GreenLanternMechConfig.DRILL_FIZZLE_COOLDOWN_TICKS);
			GreenLanternMech.sound(level, m.position(), SoundEvents.BEACON_DEACTIVATE, 1.0f, 1.6f);
			return;
		}
		float k = Math.min(1f, held / (float) GreenLanternMechConfig.DRILL_SPIN_TICKS);
		Vec3 dir = chargeDir(p);
		m.drillAim = dir;
		m.drillStart = m.position().add(0, GreenLanternMechConfig.DRILL_AXIS_Y, 0);
		m.drillProgress = 0;
		m.drillBored = 0.4;
		m.drillEnd = GreenLanternMechConfig.DRILL_RANGE_MIN + (GreenLanternMechConfig.DRILL_RANGE_MAX - GreenLanternMechConfig.DRILL_RANGE_MIN) * k;
		m.drillCharge = 0;
		m.drillStall = 0;
		m.drillLastAlong = 0;
		m.drillHits.clear();
		m.drillDone.clear();
		m.drillHitAny = false;
		m.setDrillPower(k);
		m.setDrillDir(dir);
		m.setDrillState(HardLightMechEntity.DRILL_CHARGE);
		// clear the first stretch at once so the mech can lunge straight in
		double block = bore(m, p, m.drillBored, GreenLanternMechConfig.DRILL_BORE_AHEAD + 1.6);
		m.drillBored = GreenLanternMechConfig.DRILL_BORE_AHEAD + 1.6;
		if (block < Double.MAX_VALUE) {
			m.drillEnd = Math.min(m.drillEnd, Math.max(0.0, block - 2.0));
		}
		m.impulse(dir.scale(GreenLanternMechConfig.DRILL_SPEED));
		Vec3 back = m.local(0, 3.6, -1.6);
		level.sendParticles(GREEN, back.x, back.y, back.z, 40, 0.6, 0.6, 0.6, 0.2);
		level.sendParticles(ParticleTypes.FLAME, back.x, back.y - 0.6, back.z, 6, 0.2, 0.2, 0.2, 0.04);
		GreenLanternMech.sound(level, m.position(), GreenLanternMechSounds.DRILL_CHARGE, 2.0f, 0.9f + 0.2f * k);
		GreenLanternMech.sound(level, m.position(), SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 1.6f, 0.5f);
		popup(p, "mech_giga_drill");
	}

	/** The charge's line: the camera's aim, kept off the straight up / down. */
	private static Vec3 chargeDir(ServerPlayer p) {
		Vec3 l = p.getLookAngle();
		// a near-level aim bores a level tunnel (a slight tilt would leave the mech catching its feet on the floor)
		double y = Math.abs(l.y) < 0.12 ? 0.0 : Mth.clamp(l.y, -0.55, 0.55);
		return new Vec3(l.x, y, l.z).normalize();
	}

	private static void tickDrill(HardLightMechEntity m, ServerPlayer p) {
		switch (m.drillState()) {
			case HardLightMechEntity.DRILL_SPIN -> tickSpin(m, p);
			case HardLightMechEntity.DRILL_CHARGE -> tickCharge(m, p);
			case HardLightMechEntity.DRILL_RECOVER -> {
				if (m.drillStateTicks() >= GreenLanternMechConfig.DRILL_RECOVER_TICKS) {
					m.setDrillState(HardLightMechEntity.DRILL_NONE);
				}
			}
			default -> {
			}
		}
	}

	/** Where the spinning drill's point is, roughly (the effects come off it). */
	static Vec3 drillTip(HardLightMechEntity m, Vec3 dir, float k) {
		return m.local(-1.75, 4.1, 0.6).add(dir.scale(2.6 + 2.4 * k));
	}

	private static void tickSpin(HardLightMechEntity m, ServerPlayer p) {
		ServerLevel level = (ServerLevel) m.level();
		m.drillHeld++;
		if (!GreenLanternEnergy.drainTick(p, GreenLanternMechConfig.DRILL_SPIN_DRAIN_PER_SEC / 20f)) {
			GreenLanternEnergy.feedback(p, "message.projecthero.ability.low_charge");
			drillRelease(m, p);
			return;
		}
		float k = Math.min(1f, m.drillHeld / (float) GreenLanternMechConfig.DRILL_SPIN_TICKS);
		boolean full = k >= 1f && m.drillPower() < 1f;
		m.setDrillPower(k);
		Vec3 dir = GreenLanternMech.aimDir(p);
		m.setDrillDir(dir);
		Vec3 tip = drillTip(m, dir, k);
		var rnd = level.getRandom();
		int n = 1 + (int) (k * 4);
		for (int i = 0; i < n; i++) {
			Vec3 s = dir.scale(0.3).add((rnd.nextDouble() - 0.5) * 0.8, (rnd.nextDouble() - 0.2) * 0.6, (rnd.nextDouble() - 0.5) * 0.8);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, tip.x, tip.y, tip.z, 0, s.x, s.y, s.z, 0.6 + k);
		}
		if (m.tickCount % 2 == 0) {
			double a = m.tickCount * (0.5 + k * 1.2);
			Vec3 side = new Vec3(-dir.z, 0, dir.x).normalize();
			Vec3 up = side.cross(dir).normalize();
			double r = 0.7 + 0.5 * k;
			Vec3 ring = tip.subtract(dir.scale(1.6 + 1.2 * k)).add(side.scale(Math.cos(a) * r)).add(up.scale(Math.sin(a) * r));
			level.sendParticles(GREEN, ring.x, ring.y, ring.z, 1, 0.02, 0.02, 0.02, 0);
		}
		if (m.drillHeld % 6 == 1) {
			GreenLanternMech.sound(level, tip, GreenLanternMechSounds.DRILL_SPIN, 1.2f + 0.6f * k, 0.6f + 0.9f * k);
		}
		if (full) { // fully spun: the drill flares -- let go now for GIGA DRILL BREAK
			level.sendParticles(ParticleTypes.FLASH, tip.x, tip.y, tip.z, 1, 0, 0, 0, 0);
			level.sendParticles(BIG, tip.x, tip.y, tip.z, 30, 0.6, 0.6, 0.6, 0.15);
			GreenLanternMech.sound(level, tip, SoundEvents.BEACON_POWER_SELECT, 1.6f, 1.9f);
			p.displayClientMessage(Component.translatable("message.projecthero.green_lantern.mech.drill_full").withStyle(ChatFormatting.GREEN,
					ChatFormatting.BOLD), true);
		}
		if (m.drillHeld >= GreenLanternMechConfig.DRILL_MAX_HOLD_TICKS) {
			drillRelease(m, p);
		}
	}

	private static void tickCharge(HardLightMechEntity m, ServerPlayer p) {
		ServerLevel level = (ServerLevel) m.level();
		m.drillCharge++;
		Vec3 dir = m.drillAim;
		Vec3 start = m.drillStart;
		Vec3 axisNow = m.position().add(0, GreenLanternMechConfig.DRILL_AXIS_Y, 0);
		double along = axisNow.subtract(start).dot(dir);
		float k = m.drillPower();
		// the bore runs ahead of the mech, never more than DRILL_LEAD (the pilot's client moves the mech itself)
		if (m.drillProgress < m.drillEnd && m.drillProgress - along < GreenLanternMechConfig.DRILL_LEAD) {
			m.drillProgress = Math.min(m.drillEnd, m.drillProgress + GreenLanternMechConfig.DRILL_SPEED);
		}
		double target = m.drillProgress + GreenLanternMechConfig.DRILL_BORE_AHEAD;
		int brokeBefore = m.drillBroken;
		if (target > m.drillBored) {
			double block = bore(m, p, m.drillBored, target);
			m.drillBored = target;
			if (block < Double.MAX_VALUE) {
				m.drillEnd = Math.min(m.drillEnd, Math.max(0.0, block - 2.0));
				m.drillProgress = Math.min(m.drillProgress, m.drillEnd);
			}
		}
		boolean biting = m.drillBroken > brokeBefore;
		// steer the mech down the line: toward the progress point, pulled back onto the axis
		double gap = m.drillProgress - along;
		Vec3 onLine = start.add(dir.scale(along));
		Vec3 drift = onLine.subtract(axisNow);
		Vec3 vel = dir.scale(Mth.clamp(gap, 0.0, GreenLanternMechConfig.DRILL_SPEED * 1.25)).add(drift.scale(0.35));
		m.impulse(vel);
		// enemies inside the drill: a hit every few ticks, carried along in front of it
		Vec3 tip = drillTip(m, dir, k);
		if (m.drillCharge % GreenLanternMechConfig.DRILL_HIT_INTERVAL == 0) {
			Vec3 from = start.add(dir.scale(Math.max(0.0, along) + 0.4));
			Vec3 to = start.add(dir.scale(Math.max(along + 3.0, m.drillProgress + 3.4)));
			float dmg = GreenLanternMechConfig.DRILL_HIT_DAMAGE * GreenLanternRanks.power(p) * (0.6f + 0.4f * k);
			for (LivingEntity t : GreenLanternMech.along(level, p, m, from, to, GreenLanternMechConfig.DRILL_HIT_RADIUS)) {
				int hits = m.drillHits.getOrDefault(t.getId(), 0);
				if (hits >= GreenLanternMechConfig.DRILL_MAX_HITS) {
					continue;
				}
				if (AbilityHelpers.hurtBurst(p, t, dmg)) {
					m.drillHitAny = true;
				}
				m.drillHits.put(t.getId(), hits + 1);
				GreenLanternMech.push(t, dir.scale(GreenLanternMechConfig.DRILL_SPEED * 1.1).add(0, 0.18, 0));
				Vec3 c = t.position().add(0, t.getBbHeight() * 0.5, 0);
				level.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 8, 0.3, 0.3, 0.3, 0.4);
				level.sendParticles(PALE, c.x, c.y, c.z, 6, 0.3, 0.3, 0.3, 0.1);
				GreenLanternMech.sound(level, c, SoundEvents.PLAYER_ATTACK_CRIT, 1.2f, 0.6f);
			}
		}
		var rnd = level.getRandom();
		for (int i = 0; i < 5; i++) {
			Vec3 s = dir.scale(-0.4).add((rnd.nextDouble() - 0.5) * 1.4, (rnd.nextDouble() - 0.3) * 1.0, (rnd.nextDouble() - 0.5) * 1.4);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, tip.x, tip.y, tip.z, 0, s.x, s.y, s.z, 1.4);
		}
		level.sendParticles(GREEN, tip.x, tip.y, tip.z, 3, 0.6, 0.6, 0.6, 0.05);
		Vec3 back = m.local(0, 3.6, -1.6);
		level.sendParticles(ParticleTypes.FLAME, back.x, back.y, back.z, 1, 0.15, 0.15, 0.15, 0.02);
		if (m.drillCharge % 4 == 0) {
			GreenLanternMech.sound(level, tip, biting ? GreenLanternMechSounds.DRILL_GRIND : GreenLanternMechSounds.DRILL_SPIN, 1.6f,
					biting ? 0.8f + 0.2f * rnd.nextFloat() : 1.5f);
		}
		// done: at the end of the line (the mech caught up), stuck, or out of time
		if (along > m.drillLastAlong + 0.05) {
			m.drillStall = 0;
		} else {
			m.drillStall++;
		}
		m.drillLastAlong = Math.max(m.drillLastAlong, along);
		boolean arrived = m.drillProgress >= m.drillEnd - 1.0e-3 && along >= m.drillProgress - 1.5;
		boolean stuck = m.drillStall >= 8 && m.drillProgress >= m.drillEnd - 1.0e-3;
		if (arrived || stuck || m.drillCharge >= GreenLanternMechConfig.DRILL_MAX_CHARGE_TICKS) {
			finishCharge(m, p);
		}
	}

	private static void finishCharge(HardLightMechEntity m, ServerPlayer p) {
		ServerLevel level = (ServerLevel) m.level();
		float k = m.drillPower();
		m.impulse(new Vec3(0, -0.1, 0));
		Vec3 dir = m.drillAim;
		if (k >= 0.999f) {
			gigaBreak(m, p, drillTip(m, dir, k));
		} else {
			Vec3 tip = drillTip(m, dir, k);
			level.sendParticles(BIG, tip.x, tip.y, tip.z, 30, 0.8, 0.8, 0.8, 0.2);
			GreenLanternMech.sound(level, tip, SoundEvents.ANVIL_LAND, 1.0f, 0.6f);
			if (m.drillHitAny) {
				GreenLanternRanks.award(p, GreenLanternMechConfig.COMBO_XP);
			}
		}
		m.setDrillState(HardLightMechEntity.DRILL_RECOVER);
		m.startCooldown(HardLightMechEntity.CD_DRILL, GreenLanternMechConfig.DRILL_COOLDOWN_TICKS);
	}

	/** The full-spin finisher at {@code tip}: a giant drill of light punches on through -- crater, a huge hit, the shake. */
	public static void gigaBreak(HardLightMechEntity m, ServerPlayer p, Vec3 tip) {
		ServerLevel level = (ServerLevel) m.level();
		Vec3 dir = m.drillAim;
		m.markBreak(tip);
		m.play("drill_break");
		float dmg = GreenLanternMechConfig.GIGA_BREAK_DAMAGE * GreenLanternRanks.power(p);
		Vec3 from = tip.subtract(dir.scale(2.0));
		Vec3 to = tip.add(dir.scale(GreenLanternMechConfig.GIGA_BREAK_REACH));
		boolean hit = false;
		for (LivingEntity t : GreenLanternMech.along(level, p, m, from, to, GreenLanternMechConfig.GIGA_BREAK_RADIUS)) {
			if (AbilityHelpers.hurtBurst(p, t, dmg)) {
				hit = true;
			}
			GreenLanternMech.push(t, dir.scale(3.2).add(0, 0.75, 0));
		}
		// the drill punches a crater on through the rock ahead
		Vec3 c = tip.add(dir.scale(2.0));
		int r = (int) Math.ceil(GreenLanternMechConfig.GIGA_BREAK_BORE_RADIUS);
		List<BlockPos> hole = new ArrayList<>();
		BlockPos centre = BlockPos.containing(c);
		for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-r, -r, -r), centre.offset(r, r, r))) {
			if (Vec3.atCenterOf(pos).distanceTo(c) <= GreenLanternMechConfig.GIGA_BREAK_BORE_RADIUS) {
				hole.add(pos.immutable());
			}
		}
		hole.sort(Comparator.comparingDouble(b -> b.distToCenterSqr(c)));
		var rnd = level.getRandom();
		for (BlockPos pos : hole) {
			BlockState st = level.getBlockState(pos);
			if (!st.isAir() && st.getFluidState().isEmpty() && canBore(p, level, pos, st)) {
				grind(level, p, pos, st, rnd, hole.indexOf(pos) % 12 == 0);
			}
		}
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 2, 0.6, 0.6, 0.6, 0);
		level.sendParticles(ParticleTypes.FLASH, tip.x, tip.y, tip.z, 3, 0.4, 0.4, 0.4, 0);
		level.sendParticles(BIG, c.x, c.y, c.z, 60, 2.2, 2.2, 2.2, 0.25);
		level.sendParticles(GREEN, c.x, c.y, c.z, 60, 2.0, 2.0, 2.0, 0.3);
		for (int i = 0; i < 3; i++) {
			Vec3 s = tip.add(dir.scale(i * 2.4));
			level.sendParticles(ParticleTypes.SONIC_BOOM, s.x, s.y, s.z, 1, 0, 0, 0, 0);
		}
		GreenLanternMech.sound(level, tip, GreenLanternMechSounds.GIGA_BREAK, 3.0f, 1.0f);
		GreenLanternMech.sound(level, tip, SoundEvents.GENERIC_EXPLODE.value(), 2.0f, 0.6f);
		popup(p, "mech_giga_drill_break");
		if (hit || m.drillHitAny) {
			GreenLanternRanks.award(p, GreenLanternMechConfig.GIGA_BREAK_XP);
		}
	}

	/**
	 * Cuts the tunnel between {@code from} and {@code to} blocks along the charge line: every block within
	 * {@link GreenLanternMechConfig#DRILL_BORE_RADIUS} of the axis that may be bored. Returns how far along the first
	 * blocker is (a solid block near the middle that may not be bored), or {@link Double#MAX_VALUE}.
	 */
	public static double bore(HardLightMechEntity m, ServerPlayer p, double from, double to) {
		if (to <= from) {
			return Double.MAX_VALUE;
		}
		ServerLevel level = (ServerLevel) m.level();
		Vec3 start = m.drillStart;
		Vec3 dir = m.drillAim;
		double R = GreenLanternMechConfig.DRILL_BORE_RADIUS;
		Vec3 a = start.add(dir.scale(from));
		Vec3 b = start.add(dir.scale(to));
		AABB box = new AABB(a, b).inflate(R + 1.0);
		Vec3 side = new Vec3(-dir.z, 0, dir.x);
		side = side.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : side.normalize();
		double blocker = Double.MAX_VALUE;
		List<BlockPos> cut = new ArrayList<>();
		List<Double> cutAt = new ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
			Vec3 c = Vec3.atCenterOf(pos);
			double t = c.subtract(start).dot(dir);
			if (t < from || t >= to) {
				continue;
			}
			Vec3 rel = c.subtract(start.add(dir.scale(t)));
			double perp = rel.length();
			// the round bore, plus everything the mech's own body (3.2 x 6.5, a little margin) would touch on the way --
			// a block-centred circle alone leaves the corners of its head catching the tunnel's rim
			double lat = Math.abs(rel.x * side.x + rel.z * side.z);
			boolean body = lat < 2.15 && rel.y > -3.6 && rel.y < 4.0;
			if (perp > R && !body) {
				continue;
			}
			if (!m.drillDone.add(pos.asLong())) {
				continue;
			}
			BlockState st = level.getBlockState(pos);
			if (st.isAir() || !st.getFluidState().isEmpty()) {
				continue;
			}
			if (canBore(p, level, pos, st)) {
				cut.add(pos.immutable());
				cutAt.add(t);
			} else if ((perp <= 2.4 || body) && !st.getCollisionShape(level, pos).isEmpty()) {
				blocker = Math.min(blocker, t);
			}
		}
		var rnd = level.getRandom();
		int n = 0;
		for (int i = 0; i < cut.size(); i++) {
			if (cutAt.get(i) >= blocker - 0.5) {
				continue; // never bore past what stops the drill
			}
			BlockPos pos = cut.get(i);
			BlockState st = level.getBlockState(pos);
			if (grind(level, p, pos, st, rnd, n++ % 10 == 0)) {
				m.drillBroken++;
			}
		}
		return blocker;
	}

	/**
	 * Breaks one bored block as the pilot: a share drops as items (TNT-like), the rest is ground to light. Only every few
	 * blocks shows the vanilla break burst (hundreds of them at once is a wall of dark debris) -- the rest go quietly.
	 */
	private static boolean grind(ServerLevel level, ServerPlayer p, BlockPos pos, BlockState st, net.minecraft.util.RandomSource rnd, boolean burst) {
		boolean drop = !p.isCreative() && rnd.nextFloat() < GreenLanternMechConfig.DRILL_DROP_CHANCE;
		if (burst) {
			return level.destroyBlock(pos, drop, p);
		}
		if (drop) {
			net.minecraft.world.level.block.Block.dropResources(st, level, pos, null, p, net.minecraft.world.item.ItemStack.EMPTY);
		}
		return level.removeBlock(pos, false);
	}

	/**
	 * The drill may grind this block away: the server allows ability terrain damage, the block is breakable, not harder
	 * than obsidian, no block entity (containers!), not wither-immune (bedrock, barriers, portals, command blocks), not
	 * hard light -- and the pilot may break it here (adventure mode, spawn protection, claims, every break guard).
	 */
	public static boolean canBore(ServerPlayer p, ServerLevel level, BlockPos pos, BlockState st) {
		if (!AbilityHelpers.canGrief()) {
			return false;
		}
		if (!level.isLoaded(pos) || !level.isInWorldBounds(pos) || !level.getWorldBorder().isWithinBounds(pos)) {
			return false;
		}
		if (st.isAir() || st.hasBlockEntity() || st.is(BlockTags.WITHER_IMMUNE)) {
			return false;
		}
		float h = st.getDestroySpeed(level, pos);
		if (h < 0f || h > GreenLanternMechConfig.DRILL_MAX_HARDNESS) {
			return false;
		}
		return mayBreak(p, level, pos, st);
	}

	private static boolean mayBreak(ServerPlayer p, ServerLevel level, BlockPos pos, BlockState st) {
		if (GreenLanternConstructs.isTrackedCell(level, pos) || GreenLanternSurvival.isTrackedCell(level, pos)) {
			return false;
		}
		if (!p.mayBuild() || !level.mayInteract(p, pos) || p.blockActionRestricted(level, pos, p.gameMode.getGameModeForPlayer())) {
			return false;
		}
		return PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, p, pos, st, null);
	}

	// ================================================================ Hard-Light Katana

	public static boolean katanaToggle(HardLightMechEntity m, ServerPlayer p) {
		if (drillBusy(m) || !m.ready(HardLightMechEntity.CD_KATANA)) {
			return false;
		}
		ServerLevel level = (ServerLevel) m.level();
		switch (m.katanaState()) {
			case HardLightMechEntity.KATANA_NONE -> {
				if (!GreenLanternMech.pay(p, GreenLanternMechConfig.KATANA_DRAW_COST)) {
					return false;
				}
				m.punchHeld = false;
				m.katanaHitAny = false;
				m.slashStep = -1;
				m.setKatanaState(HardLightMechEntity.KATANA_DRAWING);
				m.startCooldown(HardLightMechEntity.CD_KATANA, GreenLanternMechConfig.KATANA_DRAW_TICKS + GreenLanternMechConfig.KATANA_TOGGLE_COOLDOWN_TICKS);
				m.play("katana_draw");
				m.schedule(4, () -> {
					Vec3 h = m.local(1.2, 2.6, 0.6);
					level.sendParticles(BIG, h.x, h.y, h.z, 18, 0.3, 0.3, 0.3, 0.06);
					GreenLanternMech.sound(level, h, GreenLanternMechSounds.KATANA_DRAW, 1.8f, 1.0f);
				});
				m.schedule(GreenLanternMechConfig.KATANA_DRAW_TICKS, () -> {
					if (m.katanaState() == HardLightMechEntity.KATANA_DRAWING) {
						m.setKatanaState(HardLightMechEntity.KATANA_DRAWN);
					}
				});
				popup(p, "mech_katana");
				return true;
			}
			case HardLightMechEntity.KATANA_DRAWN -> {
				sheathe(m);
				return true;
			}
			default -> {
				return false;
			}
		}
	}

	/** Sheathe it (with the clip): it goes back into light at the hip. */
	public static void sheathe(HardLightMechEntity m) {
		if (m.katanaState() != HardLightMechEntity.KATANA_DRAWN) {
			return;
		}
		ServerLevel level = (ServerLevel) m.level();
		m.slashHeld = false;
		m.setKatanaState(HardLightMechEntity.KATANA_SHEATHING);
		m.startCooldown(HardLightMechEntity.CD_KATANA, GreenLanternMechConfig.KATANA_SHEATH_TICKS + GreenLanternMechConfig.KATANA_TOGGLE_COOLDOWN_TICKS);
		m.play("katana_sheath");
		m.schedule(GreenLanternMechConfig.KATANA_SHEATH_TICKS - 3, () -> GreenLanternMech.sound(level, m.local(1.2, 2.6, 0.4),
				GreenLanternMechSounds.KATANA_SHEATH, 1.6f, 1.0f));
		m.schedule(GreenLanternMechConfig.KATANA_SHEATH_TICKS, () -> {
			if (m.katanaState() == HardLightMechEntity.KATANA_SHEATHING) {
				m.setKatanaState(HardLightMechEntity.KATANA_NONE);
			}
		});
	}

	private static void tickKatana(HardLightMechEntity m, ServerPlayer p) {
		if (!m.katanaOut()) {
			return;
		}
		if (m.tickCount % 20 == 0 && !GreenLanternEnergy.spend(p, GreenLanternMechConfig.KATANA_UPKEEP_PER_SEC)) {
			GreenLanternEnergy.feedback(p, "message.projecthero.ability.low_charge");
			if (m.katanaDrawn()) {
				sheathe(m);
			} else {
				m.setKatanaState(HardLightMechEntity.KATANA_NONE);
			}
			return;
		}
		if (m.slashHeld && m.katanaDrawn() && m.ready(HardLightMechEntity.CD_SLASH)) {
			slash(m, p);
		}
	}

	/** R with the katana drawn: the next cut of the three-cut chain. */
	public static boolean slash(HardLightMechEntity m, ServerPlayer p) {
		if (!m.katanaDrawn() || !m.ready(HardLightMechEntity.CD_SLASH)) {
			return false;
		}
		long now = m.level().getGameTime();
		int step = now - m.lastSlashAt > GreenLanternMechConfig.SLASH_CHAIN_RESET_TICKS || m.slashStep < 0 ? 0 : (m.slashStep + 1) % 3;
		if (!GreenLanternMech.pay(p, GreenLanternMechConfig.SLASH_COST[step])) {
			return false;
		}
		m.slashStep = step;
		m.lastSlashAt = now;
		m.startCooldown(HardLightMechEntity.CD_SLASH, GreenLanternMechConfig.SLASH_COOLDOWN_TICKS[step]);
		m.play("katana_slash_" + (step + 1));
		m.markSlash(step);
		ServerLevel level = (ServerLevel) m.level();
		int impact = GreenLanternMechConfig.SLASH_IMPACT_TICKS[step];
		m.schedule(Math.max(0, impact - 2), () -> GreenLanternMech.sound(level, m.local(0, 4, 2), GreenLanternMechSounds.KATANA_SWISH, 1.6f,
				step == 2 ? 0.75f : step == 1 ? 1.12f : 0.95f));
		m.schedule(impact, () -> slashImpact(m, step));
		return true;
	}

	private static void slashImpact(HardLightMechEntity m, int step) {
		ServerPlayer p = m.pilot();
		if (p == null || !m.katanaDrawn()) {
			return;
		}
		ServerLevel level = (ServerLevel) m.level();
		Vec3 fwd = Vec3.directionFromRotation(0f, m.getYRot());
		Vec3 left = m.local(1, 0, 0).subtract(m.position());
		Vec3 centre = m.position();
		double cos = Math.cos(Math.toRadians(GreenLanternMechConfig.SLASH_ARC_DEG[step]));
		float dmg = GreenLanternMechConfig.SLASH_DAMAGE[step] * GreenLanternRanks.power(p);
		Vec3 push = switch (step) {
			case 0 -> left.scale(-0.9).add(fwd.scale(0.9)).add(0, 0.3, 0);
			case 1 -> left.scale(0.9).add(fwd.scale(0.9)).add(0, 0.6, 0);
			default -> fwd.scale(2.1).add(0, 0.5, 0);
		};
		boolean hit = false;
		for (LivingEntity t : AbilityHelpers.living(level, centre.add(0, 3, 0), GreenLanternMechConfig.SLASH_REACH + 3.0,
				e -> GreenLanternMech.canHit(p, m, e) && e.getY() > m.getY() - 1.5 && e.getY() < m.getY() + 7.5)) {
			Vec3 rel = new Vec3(t.getX() - centre.x, 0, t.getZ() - centre.z);
			double d = rel.length();
			if (d > GreenLanternMechConfig.SLASH_REACH + t.getBbWidth() * 0.5 || d > 0.5 && rel.scale(1.0 / d).dot(fwd) < cos) {
				continue;
			}
			if (AbilityHelpers.hurtBurst(p, t, dmg)) {
				hit = true;
			}
			GreenLanternMech.push(t, push);
			Vec3 c = t.position().add(0, t.getBbHeight() * 0.6, 0);
			level.sendParticles(ParticleTypes.SWEEP_ATTACK, c.x, c.y, c.z, 1, 0, 0, 0, 0);
			level.sendParticles(PALE, c.x, c.y, c.z, 10, 0.3, 0.4, 0.3, 0.15);
		}
		if (hit) {
			katanaConnected(m, p, level, m.local(0, 4, 3));
		}
	}

	/** Shift+R with the katana drawn: the iaido draw-cut and its crescent of light. */
	public static boolean iaido(HardLightMechEntity m, ServerPlayer p) {
		if (!m.katanaDrawn() || !m.ready(HardLightMechEntity.CD_IAIDO) || !GreenLanternMech.pay(p, GreenLanternMechConfig.IAIDO_COST)) {
			return false;
		}
		m.startCooldown(HardLightMechEntity.CD_IAIDO, GreenLanternMechConfig.IAIDO_COOLDOWN_TICKS);
		m.startCooldown(HardLightMechEntity.CD_SLASH, GreenLanternMechConfig.IAIDO_LAUNCH_TICKS + 8);
		m.slashHeld = false;
		m.play("katana_iaido");
		m.markSlash(HardLightMechEntity.SLASH_IAIDO);
		m.schedule(GreenLanternMechConfig.IAIDO_LAUNCH_TICKS, () -> launchWave(m));
		return true;
	}

	private static void launchWave(HardLightMechEntity m) {
		ServerPlayer p = m.pilot();
		if (p == null || !m.katanaDrawn()) {
			return;
		}
		ServerLevel level = (ServerLevel) m.level();
		Vec3 l = p.getLookAngle();
		Vec3 dir = new Vec3(l.x, Mth.clamp(l.y, -0.35, 0.35), l.z).normalize();
		Vec3 from = m.local(0, 3.6, 2.2);
		m.launchWave(from, dir, (float) GreenLanternMechConfig.IAIDO_RANGE);
		m.waveActive = true;
		m.waveTravel = 0;
		m.waveHit.clear();
		level.sendParticles(BIG, from.x, from.y, from.z, 20, 1.6, 0.3, 1.6, 0.1);
		GreenLanternMech.sound(level, from, GreenLanternMechSounds.KATANA_IAIDO, 2.0f, 1.0f);
	}

	private static void tickWave(HardLightMechEntity m, ServerPlayer p) {
		if (!m.waveActive) {
			return;
		}
		ServerLevel level = (ServerLevel) m.level();
		Vec3 from = m.waveFrom();
		Vec3 dir = m.waveDir();
		Vec3 prev = from.add(dir.scale(m.waveTravel));
		m.waveTravel += GreenLanternMechConfig.IAIDO_SPEED;
		Vec3 cur = from.add(dir.scale(m.waveTravel));
		Vec3 side = new Vec3(-dir.z, 0, dir.x);
		side = side.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : side.normalize();
		// terrain stops it (grass and leaves it cuts)
		BlockPos mid = BlockPos.containing(cur);
		BlockState ms = level.getBlockState(mid);
		if (!ms.isAir() && !cuttable(ms) && !ms.getCollisionShape(level, mid).isEmpty()) {
			m.waveActive = false;
			m.setWaveLen((float) Math.max(0.0, m.waveTravel - GreenLanternMechConfig.IAIDO_SPEED * 0.5));
			level.sendParticles(BIG, cur.x, cur.y, cur.z, 24, 1.2, 0.4, 1.2, 0.15);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, cur.x, cur.y, cur.z, 16, 1.4, 0.3, 1.4, 0.3);
			GreenLanternMech.sound(level, cur, SoundEvents.AMETHYST_CLUSTER_BREAK, 1.4f, 0.8f);
			return;
		}
		// cut the grass, flowers and leaves it passes through
		double hw = GreenLanternMechConfig.IAIDO_HALF_WIDTH;
		AABB box = new AABB(prev, cur).inflate(hw, 1.2, hw);
		for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
			Vec3 c = Vec3.atCenterOf(pos);
			Vec3 rel = c.subtract(prev);
			double t = rel.dot(dir);
			if (t < -0.5 || t > GreenLanternMechConfig.IAIDO_SPEED + 0.5 || Math.abs(rel.dot(side)) > hw || Math.abs(c.y - (prev.y + dir.y * t)) > 1.2) {
				continue;
			}
			BlockState st = level.getBlockState(pos);
			if (cuttable(st) && mayBreak(p, level, pos, st)) {
				level.destroyBlock(pos, true, p);
			}
		}
		float dmg = GreenLanternMechConfig.IAIDO_DAMAGE * GreenLanternRanks.power(p);
		boolean hit = false;
		for (LivingEntity t : GreenLanternMech.along(level, p, m, prev, cur, GreenLanternMechConfig.IAIDO_HIT_RADIUS)) {
			if (!m.waveHit.add(t.getId())) {
				continue;
			}
			if (AbilityHelpers.hurtBurst(p, t, dmg)) {
				hit = true;
			}
			GreenLanternMech.push(t, dir.scale(1.4).add(0, 0.35, 0));
			Vec3 c = t.position().add(0, t.getBbHeight() * 0.6, 0);
			level.sendParticles(ParticleTypes.SWEEP_ATTACK, c.x, c.y, c.z, 2, 0.3, 0.2, 0.3, 0);
			level.sendParticles(PALE, c.x, c.y, c.z, 12, 0.3, 0.4, 0.3, 0.2);
		}
		if (hit) {
			katanaConnected(m, p, level, cur);
		}
		for (int i = -2; i <= 2; i++) {
			Vec3 s = cur.add(side.scale(i * hw * 0.42)).subtract(dir.scale(Math.abs(i) * 0.45));
			level.sendParticles(i == 0 ? ParticleTypes.END_ROD : GREEN, s.x, s.y, s.z, 1, 0.05, 0.05, 0.05, 0);
		}
		if (m.waveTravel >= m.waveLen()) {
			m.waveActive = false;
		}
	}

	/** Grass, ferns, flowers, saplings, vines and leaves -- what the crescent cuts (never anything holding water). */
	public static boolean cuttable(BlockState st) {
		if (st.isAir() || !st.getFluidState().isEmpty() || st.hasBlockEntity()) {
			return false;
		}
		return st.is(BlockTags.LEAVES) || st.is(BlockTags.REPLACEABLE_BY_TREES) || st.is(BlockTags.FLOWERS) || st.is(BlockTags.SAPLINGS);
	}

	/** G with the katana drawn: one spinning sweep all round the mech. */
	public static boolean spin(HardLightMechEntity m, ServerPlayer p) {
		if (!m.katanaDrawn() || !m.ready(HardLightMechEntity.CD_SPIN) || !GreenLanternMech.pay(p, GreenLanternMechConfig.SPIN_COST)) {
			return false;
		}
		m.startCooldown(HardLightMechEntity.CD_SPIN, GreenLanternMechConfig.SPIN_COOLDOWN_TICKS);
		m.startCooldown(HardLightMechEntity.CD_SLASH, GreenLanternMechConfig.SPIN_IMPACT_TICKS + 8);
		m.slashHeld = false;
		m.play("katana_spin");
		m.markSlash(HardLightMechEntity.SLASH_SPIN);
		ServerLevel level = (ServerLevel) m.level();
		m.schedule(3, () -> GreenLanternMech.sound(level, m.local(0, 4, 0), GreenLanternMechSounds.KATANA_SWISH, 1.8f, 0.7f));
		m.schedule(GreenLanternMechConfig.SPIN_IMPACT_TICKS, () -> spinImpact(m));
		return true;
	}

	private static void spinImpact(HardLightMechEntity m) {
		ServerPlayer p = m.pilot();
		if (p == null || !m.katanaDrawn()) {
			return;
		}
		ServerLevel level = (ServerLevel) m.level();
		Vec3 c = m.position();
		float dmg = GreenLanternMechConfig.SPIN_DAMAGE * GreenLanternRanks.power(p);
		boolean hit = false;
		for (LivingEntity t : AbilityHelpers.living(level, c.add(0, 3, 0), GreenLanternMechConfig.SPIN_RADIUS + 3.0,
				e -> GreenLanternMech.canHit(p, m, e) && e.getY() > m.getY() - 1.5 && e.getY() < m.getY() + 7.0)) {
			Vec3 rel = new Vec3(t.getX() - c.x, 0, t.getZ() - c.z);
			if (rel.length() > GreenLanternMechConfig.SPIN_RADIUS + t.getBbWidth() * 0.5) {
				continue;
			}
			if (AbilityHelpers.hurtBurst(p, t, dmg)) {
				hit = true;
			}
			GreenLanternMech.push(t, GreenLanternMech.flat(rel).scale(1.8).add(0, 0.55, 0));
			Vec3 e = t.position().add(0, t.getBbHeight() * 0.6, 0);
			level.sendParticles(ParticleTypes.SWEEP_ATTACK, e.x, e.y, e.z, 1, 0, 0, 0, 0);
		}
		int n = 36;
		for (int i = 0; i < n; i++) {
			double a = i * Math.PI * 2 / n;
			level.sendParticles(i % 3 == 0 ? ParticleTypes.END_ROD : GREEN, c.x + Math.cos(a) * 6.2, c.y + 3.8, c.z + Math.sin(a) * 6.2, 1, 0.05, 0.05, 0.05, 0);
		}
		if (hit) {
			katanaConnected(m, p, level, m.local(0, 4, 0));
		}
	}

	/** A katana move connected: hit-stop + shake (clients read the mark), the impact ring, XP once per draw. */
	private static void katanaConnected(HardLightMechEntity m, ServerPlayer p, ServerLevel level, Vec3 at) {
		m.markHit();
		GreenLanternMech.sound(level, at, GreenLanternMechSounds.KATANA_HIT, 1.6f, 0.9f + level.getRandom().nextFloat() * 0.2f);
		if (!m.katanaHitAny) {
			m.katanaHitAny = true;
			GreenLanternRanks.award(p, GreenLanternMechConfig.COMBO_XP);
		}
	}

	// ================================================================ helpers

	/** The GL combo popup on the pilot's HUD (the same one Construct Combos use), with the mech combo's own name. */
	static void popup(ServerPlayer p, String id) {
		if (ServerPlayNetworking.canSend(p, GreenLanternCuePayload.TYPE)) {
			ServerPlayNetworking.send(p, new GreenLanternCuePayload(GreenLanternCuePayload.COMBO, id, ""));
		} else {
			p.displayClientMessage(Component.translatable("projecthero.green_lantern.combo." + id).withStyle(ChatFormatting.GREEN,
					ChatFormatting.BOLD), true);
		}
	}
}
