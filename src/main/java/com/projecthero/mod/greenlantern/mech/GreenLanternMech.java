package com.projecthero.mod.greenlantern.mech;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.joml.Vector3f;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.greenlantern.GreenLantern;
import com.projecthero.mod.greenlantern.GreenLanternBattery;
import com.projecthero.mod.greenlantern.GreenLanternConfig;
import com.projecthero.mod.greenlantern.GreenLanternEnergy;
import com.projecthero.mod.greenlantern.GreenLanternFlight;
import com.projecthero.mod.greenlantern.GreenLanternRanks;
import com.projecthero.mod.greenlantern.GreenLanternVisuals;
import com.projecthero.mod.greenlantern.data.GreenLanternFx;
import com.projecthero.mod.greenlantern.entity.HardLightConstructEntity;
import com.projecthero.mod.greenlantern.entity.HardLightConstructEntity.Shape;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.power.AbilityHelpers;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1: the Hard-Light Mech, server side -- forming it (a Construct Menu entry, {@code ConstructType.HARD_LIGHT_MECH}),
 * the pilot's keys while inside, every mech move, its upkeep, leaving it (N) and it shattering. The entity is
 * {@link HardLightMechEntity}.
 *
 * <pre>
 *   R          punch combo -- alternating fists, big knockback (hold R to keep swinging)
 *   Shift+R    Rocket Fist -- the right forearm flies out 24 blocks, hits everything on the way and comes back
 *   G          Ground Slam -- both fists into the ground, a shockwave ring out to 9 blocks
 *   X          Thruster Dash (movement) -- a burst forward, ramming what it meets
 *   Z (hold)   Chest Lantern Beam -- a beam from the lantern on the chest, drains Ring Charge while held
 *   V (hold)   Hard-Light Shield -- a wall in front: projectiles bounce back, frontal hits do 20%
 *   C          Missile Pods -- 12 homing missiles from the shoulder pods
 *   N          leave the mech (it dissolves and sets you down)
 * </pre>
 * Static state ({@link #MECHS}) is cleared on server stop through {@code GreenLanternAbilityManager#clearSessionState}.
 */
public final class GreenLanternMech {
	public static final EntityType<HardLightMechEntity> TYPE = register("hard_light_mech",
			EntityType.Builder.<HardLightMechEntity>of(HardLightMechEntity::new, MobCategory.MISC)
					.sized(GreenLanternMechConfig.WIDTH, GreenLanternMechConfig.HEIGHT)
					.eyeHeight(GreenLanternMechConfig.HEIGHT * 0.88f)
					.fireImmune().noSave().noSummon().clientTrackingRange(10).updateInterval(1)
					.build("hard_light_mech"));

	private static final ParticleOptions GREEN = new DustParticleOptions(new Vector3f(0.208f, 0.941f, 0.459f), 1.8f);
	private static final ParticleOptions PALE = new DustParticleOptions(new Vector3f(0.75f, 1.0f, 0.82f), 1.2f);

	/** Pilot -> their mech. Cleared on server stop. */
	private static final Map<UUID, HardLightMechEntity> MECHS = new ConcurrentHashMap<>();

	private GreenLanternMech() {
	}

	private static <T extends Entity> EntityType<T> register(String path, EntityType<T> type) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id(path));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, type);
	}

	public static void initialize() {
		FabricDefaultAttributeRegistry.register(TYPE, HardLightMechEntity.createAttributes());
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(GreenLanternMech::onPilotHurt);
		GreenLanternMechSounds.initialize(); // v0.17.1 construct combos
	}

	public static void clearSessionState() {
		MECHS.clear();
	}

	/** Death / respawn / logout / dimension change / losing the ring: the mech goes at once and the pilot stays put. */
	public static void onCleanup(ServerPlayer p) {
		HardLightMechEntity m = MECHS.remove(p.getUUID());
		HardLightMechEntity riding = HardLightMechEntity.mechOf(p);
		if (riding != null) {
			p.stopRiding();
			riding.discard();
		}
		if (m != null && !m.isRemoved()) {
			m.discard();
		}
	}

	static void forget(HardLightMechEntity m) {
		UUID o = m.ownerId();
		if (o != null) {
			MECHS.remove(o, m);
		}
	}

	public static HardLightMechEntity mechOf(ServerPlayer p) {
		return HardLightMechEntity.mechOf(p);
	}

	public static boolean isPiloting(ServerPlayer p) {
		return HardLightMechEntity.isPilot(p);
	}

	public static float maxIntegrity(ServerPlayer p) {
		return GreenLanternMechConfig.BASE_INTEGRITY * (1f + GreenLanternMechConfig.INTEGRITY_PER_RANK * (GreenLanternRanks.rank(p) - 1));
	}

	// ================================================================ forming

	/** The Construct Menu entry (or its key): form the mech round you -- or, from inside it, leave it. */
	public static void deploy(ServerPlayer p) {
		HardLightMechEntity in = mechOf(p);
		if (in != null) {
			exit(p);
			return;
		}
		if (!GreenLantern.abilityReady(p, GreenLanternMechConfig.COOLDOWN_ID)) {
			int s = (GreenLantern.cooldownRemaining(p, GreenLanternMechConfig.COOLDOWN_ID) + 19) / 20;
			p.displayClientMessage(Component.translatable("message.projecthero.green_lantern.mech.cooldown", s).withStyle(ChatFormatting.RED), true);
			return;
		}
		if (p.isPassenger()) {
			p.displayClientMessage(Component.translatable("message.projecthero.green_lantern.mech.riding").withStyle(ChatFormatting.RED), true);
			return;
		}
		ServerLevel level = p.serverLevel();
		float hw = GreenLanternMechConfig.WIDTH / 2f;
		AABB box = new AABB(p.getX() - hw, p.getY() + 0.05, p.getZ() - hw, p.getX() + hw, p.getY() + GreenLanternMechConfig.HEIGHT, p.getZ() + hw);
		if (!level.noCollision(box)) {
			p.displayClientMessage(Component.translatable("message.projecthero.green_lantern.mech.no_room").withStyle(ChatFormatting.RED), true);
			return;
		}
		if (!GreenLanternEnergy.spend(p, GreenLanternMechConfig.FORM_COST)) {
			GreenLanternEnergy.feedback(p, "message.projecthero.ability.low_charge");
			return;
		}
		GreenLanternBattery.onAbilityUsed(p);
		if (GreenLanternFlight.isFlying(p)) {
			GreenLanternFlight.forceStop(p, false);
		}
		HardLightMechEntity m = new HardLightMechEntity(TYPE, level);
		m.moveTo(p.getX(), p.getY(), p.getZ(), p.getYRot(), 0f);
		m.setYBodyRot(p.getYRot());
		m.setYHeadRot(p.getYRot());
		m.bind(p, maxIntegrity(p), GreenLanternRanks.golden(p));
		level.addFreshEntity(m);
		p.startRiding(m, true);
		MECHS.put(p.getUUID(), m);
		GreenLanternVisuals.anim(p, GreenLanternFx.ANIM_CONSTRUCT);
		sound(level, m.position(), SoundEvents.BEACON_ACTIVATE, 1.6f, 0.7f);
		sound(level, m.position(), SoundEvents.RESPAWN_ANCHOR_CHARGE, 1.2f, 0.8f);
		p.displayClientMessage(Component.translatable("message.projecthero.green_lantern.mech.formed").withStyle(ChatFormatting.GREEN), true);
	}

	/** N (or the entry again): the mech dissolves round the pilot, then sets them down. */
	public static void exit(ServerPlayer p) {
		HardLightMechEntity m = mechOf(p);
		if (m == null || m.state() == HardLightMechEntity.STATE_DISSOLVING) {
			return;
		}
		dissolve(m);
	}

	public static void dissolve(HardLightMechEntity m) {
		if (m.state() == HardLightMechEntity.STATE_DISSOLVING) {
			return;
		}
		stopChannels(m);
		m.setState(HardLightMechEntity.STATE_DISSOLVING);
		sound((ServerLevel) m.level(), m.position(), SoundEvents.BEACON_DEACTIVATE, 1.6f, 0.7f);
	}

	/** Integrity 0: it bursts into shards of light and drops the pilot safely. */
	public static void shatter(HardLightMechEntity m) {
		ServerLevel level = (ServerLevel) m.level();
		ServerPlayer p = m.pilot();
		Vec3 c = m.position().add(0, 3.2, 0);
		level.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 2, 0.5, 0.5, 0.5, 0);
		level.sendParticles(GREEN, c.x, c.y, c.z, 140, 1.4, 2.6, 1.4, 0.2);
		level.sendParticles(PALE, c.x, c.y, c.z, 80, 1.2, 2.4, 1.2, 0.35);
		level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 40, 1.2, 2.2, 1.2, 0.25);
		sound(level, c, SoundEvents.GLASS_BREAK, 2.0f, 0.5f);
		sound(level, c, SoundEvents.AMETHYST_CLUSTER_BREAK, 2.0f, 0.6f);
		sound(level, c, SoundEvents.GENERIC_EXPLODE.value(), 0.8f, 1.4f);
		if (p != null) {
			eject(m, p);
			GreenLantern.triggerCooldown(p, GreenLanternMechConfig.COOLDOWN_ID, GreenLanternMechConfig.SHATTER_COOLDOWN_TICKS);
			p.displayClientMessage(Component.translatable("message.projecthero.green_lantern.mech.shattered").withStyle(ChatFormatting.RED), true);
		}
		forget(m);
		m.discard();
	}

	private static void finishDissolve(HardLightMechEntity m, ServerPlayer p) {
		ServerLevel level = (ServerLevel) m.level();
		Vec3 c = m.position().add(0, 3.2, 0);
		level.sendParticles(GREEN, c.x, c.y, c.z, 60, 1.2, 2.4, 1.2, 0.05);
		eject(m, p);
		GreenLantern.triggerCooldown(p, GreenLanternMechConfig.COOLDOWN_ID, GreenLanternMechConfig.REFORM_COOLDOWN_TICKS);
		forget(m);
		m.discard();
	}

	/** Out of the cockpit and down on the ground where the mech stood, unhurt. */
	private static void eject(HardLightMechEntity m, ServerPlayer p) {
		stopChannels(m);
		if (p.getVehicle() == m) {
			p.stopRiding();
		}
		Vec3 at = m.position();
		p.teleportTo(at.x, at.y + 0.05, at.z);
		p.resetFallDistance();
		p.setDeltaMovement(Vec3.ZERO);
		p.hurtMarked = true;
	}

	private static void stopChannels(HardLightMechEntity m) {
		m.setFlag(HardLightMechEntity.FLAG_BEAM, false);
		m.setFlag(HardLightMechEntity.FLAG_SHIELD, false);
		m.punchHeld = false;
		if (m.rocketFist != null) {
			m.rocketFist.discard();
			m.rocketFist = null;
		}
		m.setFlag(HardLightMechEntity.FLAG_ROCKET_OUT, false);
		GreenLanternMechCombos.stopAll(m); // v0.17.1: the katana is sheathed, a drill stops
	}

	// ================================================================ the pilot's keys

	/** From {@code GreenLanternAbilityManager#handle}: true when the pilot's key went to the mech (always, while inside). */
	public static boolean handleKey(ServerPlayer p, AbilitySlot slot, boolean pressed) {
		HardLightMechEntity m = mechOf(p);
		if (m == null) {
			return false;
		}
		if (!m.isActive()) {
			if (!pressed) {
				stopHolds(m, slot);
			}
			return true;
		}
		// v0.17.1 construct combos: Shift + a combo key (MechComboKeys), and the release of the drill's spin key
		if (GreenLanternMechCombos.key(m, p, slot, pressed)) {
			return true;
		}
		if (pressed && GreenLanternMechCombos.drillBusy(m)) {
			return true; // the drill arm is busy: everything else waits for the charge
		}
		boolean katana = m.katanaDrawn();
		boolean katanaMoving = m.katanaOut() && !katana; // drawing / sheathing: the hand is busy
		switch (slot) {
			case SLOT_1 -> {
				if (pressed) {
					if (katanaMoving) {
						return true;
					}
					if (katana) { // v0.17.1: the katana's R (three-cut chain) and Shift+R (iaido draw-cut)
						if (p.isShiftKeyDown()) {
							GreenLanternMechCombos.iaido(m, p);
						} else {
							m.slashHeld = true;
							GreenLanternMechCombos.slash(m, p);
						}
					} else if (p.isShiftKeyDown()) {
						rocketFist(m, p);
					} else {
						m.punchHeld = true;
						punch(m, p);
					}
				} else {
					m.punchHeld = false;
					m.slashHeld = false;
				}
			}
			case SLOT_2 -> {
				if (pressed && !katanaMoving) {
					if (katana) {
						GreenLanternMechCombos.spin(m, p); // v0.17.1: the katana's spinning sweep
					} else {
						groundSlam(m, p);
					}
				}
			}
			case SLOT_3 -> {
				if (pressed) {
					dash(m, p);
				}
			}
			case SLOT_4 -> {
				if (pressed) {
					beamStart(m, p);
				} else {
					m.setFlag(HardLightMechEntity.FLAG_BEAM, false);
				}
			}
			case SLOT_5 -> {
				if (pressed) {
					shieldStart(m, p);
				} else {
					m.setFlag(HardLightMechEntity.FLAG_SHIELD, false);
				}
			}
			case SLOT_6 -> {
				if (pressed) {
					missiles(m, p);
				}
			}
		}
		return true;
	}

	private static void stopHolds(HardLightMechEntity m, AbilitySlot slot) {
		switch (slot) {
			case SLOT_1 -> {
				m.punchHeld = false;
				m.slashHeld = false;
			}
			case SLOT_4 -> m.setFlag(HardLightMechEntity.FLAG_BEAM, false);
			case SLOT_5 -> m.setFlag(HardLightMechEntity.FLAG_SHIELD, false);
			default -> {
			}
		}
	}

	// ================================================================ moves

	public static boolean punch(HardLightMechEntity m, ServerPlayer p) {
		if (!m.ready(HardLightMechEntity.CD_PUNCH) || !pay(p, GreenLanternMechConfig.PUNCH_COST)) {
			return false;
		}
		m.startCooldown(HardLightMechEntity.CD_PUNCH, GreenLanternMechConfig.PUNCH_COOLDOWN_TICKS);
		boolean left = m.leftNext;
		m.leftNext = !left;
		m.play(left ? "punch_l" : "punch_r");
		sound((ServerLevel) m.level(), m.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.2f, 0.5f);
		m.schedule(GreenLanternMechConfig.PUNCH_IMPACT_TICKS, () -> punchImpact(m, left));
		return true;
	}

	private static void punchImpact(HardLightMechEntity m, boolean left) {
		ServerPlayer p = m.pilot();
		if (p == null) {
			return;
		}
		ServerLevel level = (ServerLevel) m.level();
		Vec3 look = aimDir(p);
		Vec3 shoulder = m.local(left ? 1.75 : -1.75, 4.3, 0.4);
		Vec3 centre = shoulder.add(look.scale(3.4));
		float dmg = GreenLanternMechConfig.PUNCH_DAMAGE * GreenLanternRanks.power(p);
		Vec3 push = flat(look).scale(GreenLanternMechConfig.PUNCH_KNOCKBACK).add(0, 0.45, 0);
		for (LivingEntity t : AbilityHelpers.living(level, centre, 2.8, e -> canHit(p, m, e))) {
			AbilityHelpers.hurtBurst(p, t, dmg);
			push(t, push);
		}
		level.sendParticles(ParticleTypes.FLASH, centre.x, centre.y, centre.z, 1, 0, 0, 0, 0);
		level.sendParticles(PALE, centre.x, centre.y, centre.z, 18, 0.5, 0.5, 0.5, 0.15);
		sound(level, centre, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.4f, 0.6f);
		sound(level, centre, SoundEvents.IRON_GOLEM_ATTACK, 1.2f, 0.7f);
	}

	public static boolean rocketFist(HardLightMechEntity m, ServerPlayer p) {
		if (m.rocketFist != null || !m.ready(HardLightMechEntity.CD_ROCKET) || !pay(p, GreenLanternMechConfig.ROCKET_COST)) {
			return false;
		}
		m.startCooldown(HardLightMechEntity.CD_ROCKET, GreenLanternMechConfig.ROCKET_COOLDOWN_TICKS);
		m.play("rocket_fist");
		m.setFlag(HardLightMechEntity.FLAG_ROCKET_OUT, true);
		m.schedule(GreenLanternMechConfig.ROCKET_LAUNCH_TICKS, () -> {
			ServerPlayer pilot = m.pilot();
			if (pilot == null) {
				return;
			}
			ServerLevel level = (ServerLevel) m.level();
			Vec3 from = m.local(-1.75, 4.3, 2.2);
			HardLightConstructEntity fist = HardLightConstructEntity.create(level, Shape.FIST, pilot.getUUID(), from, 2.6f, 60);
			fist.phase = 1; // moved here, not by the Construct Fist's own tick
			m.rocketDir = aimDir(pilot);
			fist.face(m.rocketDir);
			level.addFreshEntity(fist);
			m.rocketFist = fist;
			m.rocketTravel = 0;
			m.rocketBack = false;
			m.rocketAge = 0;
			m.rocketHit.clear();
			sound(level, from, SoundEvents.FIREWORK_ROCKET_LAUNCH, 1.6f, 0.6f);
			level.sendParticles(GREEN, from.x, from.y, from.z, 20, 0.4, 0.4, 0.4, 0.1);
		});
		return true;
	}

	private static void tickRocket(HardLightMechEntity m, ServerPlayer p) {
		HardLightConstructEntity f = m.rocketFist;
		if (f == null) {
			return;
		}
		ServerLevel level = (ServerLevel) m.level();
		if (f.isRemoved() || ++m.rocketAge > 50) {
			f.discard();
			m.rocketFist = null;
			m.setFlag(HardLightMechEntity.FLAG_ROCKET_OUT, false);
			return;
		}
		Vec3 pos = f.position();
		if (!m.rocketBack) {
			Vec3 next = pos.add(m.rocketDir.scale(GreenLanternMechConfig.ROCKET_SPEED));
			BlockHitResult bh = level.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, f));
			Vec3 end = bh.getType() == HitResult.Type.MISS ? next : bh.getLocation();
			float dmg = GreenLanternMechConfig.ROCKET_DAMAGE * GreenLanternRanks.power(p);
			for (LivingEntity t : along(level, p, m, pos, end, 1.4)) {
				if (m.rocketHit.add(t.getId())) {
					AbilityHelpers.hurtBurst(p, t, dmg);
					push(t, m.rocketDir.scale(1.6).add(0, 0.4, 0));
					level.sendParticles(ParticleTypes.FLASH, t.getX(), t.getY() + t.getBbHeight() * 0.5, t.getZ(), 1, 0, 0, 0, 0);
					sound(level, t.position(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.4f, 0.5f);
				}
			}
			f.setPos(end.x, end.y, end.z);
			m.rocketTravel += end.distanceTo(pos);
			if (bh.getType() != HitResult.Type.MISS || m.rocketTravel >= GreenLanternMechConfig.ROCKET_RANGE) {
				m.rocketBack = true;
				if (bh.getType() != HitResult.Type.MISS) {
					level.sendParticles(PALE, end.x, end.y, end.z, 16, 0.4, 0.4, 0.4, 0.2);
					sound(level, end, SoundEvents.ANVIL_LAND, 0.8f, 0.6f);
				}
			}
		} else {
			Vec3 home = m.local(-1.75, 4.3, 1.4);
			Vec3 d = home.subtract(pos);
			double len = d.length();
			if (len < GreenLanternMechConfig.ROCKET_SPEED * 1.2) {
				f.discard();
				m.rocketFist = null;
				m.setFlag(HardLightMechEntity.FLAG_ROCKET_OUT, false);
				sound(level, home, SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 1.2f, 0.6f);
				return;
			}
			Vec3 step = d.scale(GreenLanternMechConfig.ROCKET_SPEED * 1.15 / len);
			Vec3 next = pos.add(step);
			f.setPos(next.x, next.y, next.z);
		}
		level.sendParticles(GREEN, pos.x, pos.y, pos.z, 2, 0.15, 0.15, 0.15, 0.0);
		level.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 1, 0.05, 0.05, 0.05, 0.0);
	}

	public static boolean groundSlam(HardLightMechEntity m, ServerPlayer p) {
		if (!m.ready(HardLightMechEntity.CD_SLAM) || !pay(p, GreenLanternMechConfig.SLAM_COST)) {
			return false;
		}
		m.startCooldown(HardLightMechEntity.CD_SLAM, GreenLanternMechConfig.SLAM_COOLDOWN_TICKS);
		m.play("ground_slam");
		java.util.Set<Integer> hit = new java.util.HashSet<>();
		m.schedule(GreenLanternMechConfig.SLAM_IMPACT_TICKS, () -> {
			m.markSlam();
			ServerLevel level = (ServerLevel) m.level();
			Vec3 c = m.local(0, 0.1, 1.6);
			level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 1, 0, 0, 0, 0);
			sound(level, c, SoundEvents.GENERIC_EXPLODE.value(), 1.4f, 0.6f);
			sound(level, c, SoundEvents.ANVIL_LAND, 1.4f, 0.5f);
		});
		for (int i = 0; i <= GreenLanternMechConfig.SLAM_WAVE_TICKS; i++) {
			final int step = i;
			m.schedule(GreenLanternMechConfig.SLAM_IMPACT_TICKS + i, () -> slamWave(m, step, hit));
		}
		return true;
	}

	private static void slamWave(HardLightMechEntity m, int step, java.util.Set<Integer> hit) {
		ServerPlayer p = m.pilot();
		if (p == null) {
			return;
		}
		ServerLevel level = (ServerLevel) m.level();
		double R = GreenLanternMechConfig.SLAM_RADIUS;
		double r = 1.2 + (R - 1.2) * step / (double) GreenLanternMechConfig.SLAM_WAVE_TICKS;
		Vec3 c = m.local(0, 0, 1.6);
		float dmg = GreenLanternMechConfig.SLAM_DAMAGE * GreenLanternRanks.power(p);
		for (LivingEntity t : AbilityHelpers.living(level, c, r + 0.6, e -> canHit(p, m, e) && Math.abs(e.getY() - m.getY()) < 3.5)) {
			if (hit.add(t.getId())) {
				AbilityHelpers.hurtBurst(p, t, dmg);
				Vec3 out = flat(t.position().subtract(c));
				push(t, out.scale(1.1).add(0, 0.85, 0));
			}
		}
		// the ring: dust + broken ground round its edge
		int n = Math.max(12, (int) (r * 7));
		BlockPos under = BlockPos.containing(c.x, c.y - 0.5, c.z);
		BlockState ground = level.getBlockState(under);
		for (int i = 0; i < n; i++) {
			double a = i * Math.PI * 2 / n;
			double x = c.x + Math.cos(a) * r, z = c.z + Math.sin(a) * r;
			level.sendParticles(GREEN, x, c.y + 0.2, z, 1, 0.05, 0.05, 0.05, 0);
			if (!ground.isAir() && i % 2 == 0) {
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), x, c.y + 0.2, z, 2, 0.2, 0.1, 0.2, 0.15);
			}
		}
	}

	public static boolean dash(HardLightMechEntity m, ServerPlayer p) {
		if (!m.ready(HardLightMechEntity.CD_DASH) || !pay(p, GreenLanternMechConfig.DASH_COST)) {
			return false;
		}
		m.startCooldown(HardLightMechEntity.CD_DASH, GreenLanternMechConfig.DASH_COOLDOWN_TICKS);
		m.play("dash");
		Vec3 dir = flat(p.getLookAngle());
		m.dashDir = dir;
		m.dashTicksLeft = GreenLanternMechConfig.DASH_HIT_TICKS;
		m.dashHit.clear();
		m.impulse(dir.scale(GreenLanternMechConfig.DASH_SPEED).add(0, 0.32, 0));
		ServerLevel level = (ServerLevel) m.level();
		Vec3 back = m.local(0, 3.6, -1.6);
		level.sendParticles(GREEN, back.x, back.y, back.z, 30, 0.5, 0.5, 0.5, 0.15);
		level.sendParticles(ParticleTypes.FLAME, back.x, back.y - 0.6, back.z, 10, 0.3, 0.2, 0.3, 0.05);
		sound(level, m.position(), SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 1.6f, 0.6f);
		return true;
	}

	private static void tickDash(HardLightMechEntity m, ServerPlayer p) {
		if (m.dashTicksLeft <= 0) {
			return;
		}
		m.dashTicksLeft--;
		ServerLevel level = (ServerLevel) m.level();
		float dmg = GreenLanternMechConfig.DASH_DAMAGE * GreenLanternRanks.power(p);
		AABB box = m.getBoundingBox().inflate(1.0).expandTowards(m.dashDir.scale(1.5));
		for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class, box, e -> canHit(p, m, e))) {
			if (m.dashHit.add(t.getId())) {
				AbilityHelpers.hurtBurst(p, t, dmg);
				push(t, m.dashDir.scale(1.6).add(0, 0.5, 0));
				sound(level, t.position(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.4f, 0.5f);
			}
		}
		Vec3 back = m.local(0, 3.2, -1.6);
		level.sendParticles(GREEN, back.x, back.y, back.z, 4, 0.3, 0.3, 0.3, 0.05);
	}

	public static boolean beamStart(HardLightMechEntity m, ServerPlayer p) {
		if (!GreenLanternEnergy.canSpend(p, GreenLanternMechConfig.BEAM_DRAIN_PER_SEC / 20f)) {
			GreenLanternEnergy.feedback(p, "message.projecthero.ability.low_charge");
			return false;
		}
		m.setFlag(HardLightMechEntity.FLAG_SHIELD, false);
		m.setFlag(HardLightMechEntity.FLAG_BEAM, true);
		m.setBeamEnd(beamTarget(m, p));
		sound((ServerLevel) m.level(), m.chest(), SoundEvents.BEACON_POWER_SELECT, 1.6f, 0.6f);
		return true;
	}

	private static void tickBeam(HardLightMechEntity m, ServerPlayer p) {
		if (!m.hasFlag(HardLightMechEntity.FLAG_BEAM)) {
			return;
		}
		if (!GreenLanternEnergy.drainTick(p, GreenLanternMechConfig.BEAM_DRAIN_PER_SEC / 20f)) {
			m.setFlag(HardLightMechEntity.FLAG_BEAM, false);
			GreenLanternEnergy.feedback(p, "message.projecthero.ability.low_charge");
			return;
		}
		ServerLevel level = (ServerLevel) m.level();
		Vec3 from = m.chest();
		Vec3 end = beamTarget(m, p);
		m.setBeamEnd(end);
		if (m.tickCount % GreenLanternMechConfig.BEAM_INTERVAL_TICKS == 0) {
			float dmg = GreenLanternMechConfig.BEAM_DAMAGE * GreenLanternRanks.power(p);
			for (LivingEntity t : along(level, p, m, from, end, 1.0)) {
				AbilityHelpers.hurtBurst(p, t, dmg);
				push(t, end.subtract(from).normalize().scale(0.25));
			}
			level.sendParticles(PALE, end.x, end.y, end.z, 8, 0.3, 0.3, 0.3, 0.12);
			level.sendParticles(ParticleTypes.END_ROD, end.x, end.y, end.z, 3, 0.2, 0.2, 0.2, 0.05);
		}
		if (m.tickCount % 10 == 0) {
			sound(level, from, SoundEvents.BEACON_AMBIENT, 1.4f, 1.4f);
		}
	}

	public static boolean shieldStart(HardLightMechEntity m, ServerPlayer p) {
		if (m.hasFlag(HardLightMechEntity.FLAG_SHIELD) || !pay(p, GreenLanternMechConfig.SHIELD_COST)) {
			return false;
		}
		m.setFlag(HardLightMechEntity.FLAG_BEAM, false);
		m.setFlag(HardLightMechEntity.FLAG_SHIELD, true);
		sound((ServerLevel) m.level(), m.position(), SoundEvents.BEACON_ACTIVATE, 1.2f, 1.5f);
		return true;
	}

	/** Where the shield wall stands: in front of the mech, the size of its body. */
	public static AABB shieldBox(HardLightMechEntity m) {
		Vec3 c = m.local(0, 3.2, 2.4);
		return new AABB(c.x - 3.2, c.y - 3.4, c.z - 3.2, c.x + 3.2, c.y + 3.6, c.z + 3.2);
	}

	private static void tickShield(HardLightMechEntity m, ServerPlayer p) {
		if (!m.hasFlag(HardLightMechEntity.FLAG_SHIELD)) {
			return;
		}
		if (!GreenLanternEnergy.drainTick(p, GreenLanternMechConfig.SHIELD_DRAIN_PER_SEC / 20f)) {
			m.setFlag(HardLightMechEntity.FLAG_SHIELD, false);
			GreenLanternEnergy.feedback(p, "message.projecthero.ability.low_charge");
			return;
		}
		ServerLevel level = (ServerLevel) m.level();
		Vec3 fwd = Vec3.directionFromRotation(0f, m.getYRot());
		Vec3 wall = m.local(0, 3.2, 2.6);
		for (Projectile pr : level.getEntitiesOfClass(Projectile.class, shieldBox(m), x -> x.getOwner() != p)) {
			Vec3 v = pr.getDeltaMovement();
			// only what is coming at the mech, and still in front of the wall
			Vec3 rel = pr.position().subtract(wall);
			if (v.dot(fwd) >= 0 || rel.dot(fwd) < -0.6) {
				continue;
			}
			pr.setDeltaMovement(v.scale(-0.9));
			pr.setOwner(p);
			pr.hurtMarked = true;
			level.sendParticles(PALE, pr.getX(), pr.getY(), pr.getZ(), 6, 0.1, 0.1, 0.1, 0.05);
			sound(level, pr.position(), SoundEvents.SHIELD_BLOCK, 1.0f, 1.3f);
		}
	}

	public static boolean missiles(HardLightMechEntity m, ServerPlayer p) {
		if (!m.ready(HardLightMechEntity.CD_MISSILES) || !pay(p, GreenLanternMechConfig.MISSILE_COST)) {
			return false;
		}
		m.startCooldown(HardLightMechEntity.CD_MISSILES, GreenLanternMechConfig.MISSILE_COOLDOWN_TICKS);
		m.play("missiles");
		ServerLevel level = (ServerLevel) m.level();
		Vec3 look = aimDir(p);
		List<LivingEntity> targets = new ArrayList<>();
		LivingEntity aimed = aimedEntity(p, m, GreenLanternMechConfig.MISSILE_TARGET_RANGE);
		if (aimed != null && canHit(p, m, aimed)) {
			targets.add(aimed);
		}
		Vec3 eye = p.getEyePosition();
		AbilityHelpers.living(level, eye, GreenLanternMechConfig.MISSILE_TARGET_RANGE,
				e -> e != aimed && canHit(p, m, e) && HeroTargets.isHostile(p, e)
						&& e.position().subtract(m.position()).normalize().dot(look) > 0.2).stream()
				.sorted(Comparator.comparingDouble(e -> e.distanceToSqr(m)))
				.limit(GreenLanternMechConfig.MISSILE_COUNT)
				.forEach(targets::add);
		Vec3 aimPoint = aimPoint(p, m, GreenLanternMechConfig.MISSILE_TARGET_RANGE);
		float dmg = GreenLanternMechConfig.MISSILE_DAMAGE * GreenLanternRanks.power(p);
		for (int i = 0; i < GreenLanternMechConfig.MISSILE_COUNT; i++) {
			final int k = i;
			m.schedule(4 + i, () -> {
				ServerPlayer pilot = m.pilot();
				if (pilot == null) {
					return;
				}
				double side = k % 2 == 0 ? -1 : 1;
				int slot = (k / 2) % 6;
				Vec3 pod = m.local(side * (1.6 + (slot % 3) * 0.22), 5.95 + (slot / 3) * 0.17, 0.45);
				HardLightConstructEntity mi = HardLightConstructEntity.create(level, Shape.MISSILE, pilot.getUUID(), pod, 1.4f,
						GreenLanternConfig.MISSILE_LIFE_TICKS + 20);
				Vec3 outward = m.local(side, 0, 0).subtract(m.position());
				mi.velocity = look.scale(0.45).add(outward.scale(0.35)).add(0, 0.7, 0);
				mi.homingId = targets.isEmpty() ? -1 : targets.get(k % targets.size()).getId();
				mi.origin = aimPoint;
				mi.damage = dmg;
				mi.face(mi.velocity);
				level.addFreshEntity(mi);
				level.sendParticles(GREEN, pod.x, pod.y, pod.z, 4, 0.1, 0.1, 0.1, 0.05);
				sound(level, pod, SoundEvents.FIREWORK_ROCKET_LAUNCH, 0.9f, 0.7f + 0.05f * (k % 4));
			});
		}
		return true;
	}

	// ================================================================ per-tick

	static void tickMech(HardLightMechEntity m, ServerPlayer p) {
		ServerLevel level = (ServerLevel) m.level();
		long t = m.stateTicks();
		switch (m.state()) {
			case HardLightMechEntity.STATE_ASSEMBLING -> {
				tickAssembly(m, p, t);
				if (t >= GreenLanternMechConfig.ASSEMBLE_TICKS) {
					m.setState(HardLightMechEntity.STATE_ACTIVE);
					sound(level, m.position(), SoundEvents.BEACON_POWER_SELECT, 1.6f, 0.8f);
					Vec3 c = m.position().add(0, 3.2, 0);
					level.sendParticles(PALE, c.x, c.y, c.z, 50, 1.2, 2.6, 1.2, 0.12);
				}
				return;
			}
			case HardLightMechEntity.STATE_DISSOLVING -> {
				if (m.tickCount % 2 == 0) {
					Vec3 c = m.position().add(0, 3.2, 0);
					level.sendParticles(GREEN, c.x, c.y, c.z, 10, 1.2, 2.4, 1.2, 0.03);
				}
				if (t >= GreenLanternMechConfig.DISSOLVE_TICKS) {
					finishDissolve(m, p);
				}
				return;
			}
			default -> {
			}
		}
		// upkeep: an empty ring can't hold it together
		if (m.tickCount % 20 == 0 && !GreenLanternEnergy.spend(p, GreenLanternMechConfig.UPKEEP_PER_SEC)) {
			p.displayClientMessage(Component.translatable("message.projecthero.green_lantern.mech.no_charge").withStyle(ChatFormatting.RED), true);
			dissolve(m);
			return;
		}
		if (m.punchHeld && m.ready(HardLightMechEntity.CD_PUNCH)) {
			punch(m, p);
		}
		tickRocket(m, p);
		tickDash(m, p);
		tickBeam(m, p);
		tickShield(m, p);
		GreenLanternMechCombos.tick(m, p); // v0.17.1 construct combos
		// the pilot is protected: no fire, no air loss
		if (p.isOnFire()) {
			p.clearFire();
		}
		p.setAirSupply(p.getMaxAirSupply());
		p.resetFallDistance();
		// a thruster burst whenever it lifts off (the pilot's client jumps it; the server sees it rise)
		double dy = m.getY() - m.lastServerY;
		if (dy > 0.25) {
			for (int s = -1; s <= 1; s += 2) {
				Vec3 n = m.local(s * 0.5, 3.4, -1.35);
				level.sendParticles(GREEN, n.x, n.y, n.z, 6, 0.15, 0.1, 0.15, 0.05);
				level.sendParticles(ParticleTypes.FLAME, n.x, n.y - 0.2, n.z, 3, 0.1, 0.1, 0.1, 0.04);
			}
			if (m.tickCount % 4 == 0) {
				sound(level, m.position(), SoundEvents.FIRECHARGE_USE, 0.9f, 0.6f);
			}
		}
	}

	/** Light streaming out of the ring into the mech's plates as they snap together. */
	private static void tickAssembly(HardLightMechEntity m, ServerPlayer p, long t) {
		ServerLevel level = (ServerLevel) m.level();
		Vec3 ring = AbilityHelpers.handPosition(p);
		var rnd = level.getRandom();
		float k = Math.min(1f, t / (float) GreenLanternMechConfig.ASSEMBLE_TICKS);
		for (int i = 0; i < 4; i++) {
			// the build runs core first, then outward and up: aim the streams at roughly where plates are arriving
			double y = 2.2 + (rnd.nextDouble() - 0.3) * 4.4 * (0.4 + k);
			Vec3 target = m.local((rnd.nextDouble() - 0.5) * 4.6 * (0.5 + k), Mth.clamp(y, 0.2, 6.3), (rnd.nextDouble() - 0.5) * 2.0);
			Vec3 d = target.subtract(ring);
			level.sendParticles(GREEN, ring.x, ring.y, ring.z, 0, d.x, d.y, d.z, 0.12);
			level.sendParticles(PALE, target.x, target.y, target.z, 1, 0.05, 0.05, 0.05, 0.0);
		}
		if (t % 6 == 0) {
			sound(level, m.position(), SoundEvents.AMETHYST_BLOCK_PLACE, 1.4f, 0.6f + k * 0.8f);
			sound(level, m.position(), SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 1.0f, 0.5f + k * 0.4f);
		}
	}

	// ================================================================ damage onto the mech

	/** Every hit on a pilot inside their mech lands on the mech instead. */
	private static boolean onPilotHurt(LivingEntity entity, DamageSource source, float amount) {
		if (!(entity instanceof ServerPlayer p)) {
			return true;
		}
		HardLightMechEntity m = mechOf(p);
		if (m == null || !m.isAlive()) {
			return true;
		}
		if (source.is(DamageTypes.GENERIC_KILL) || source.is(DamageTypes.FELL_OUT_OF_WORLD)) {
			return true;
		}
		if (!source.is(DamageTypeTags.IS_FALL) && !source.is(DamageTypes.DROWN) && !source.is(DamageTypes.IN_WALL)
				&& !source.is(DamageTypeTags.IS_FIRE) && !source.is(DamageTypes.STARVE)) {
			m.hurt(source, amount);
		}
		return false;
	}

	// ================================================================ helpers

	static boolean pay(ServerPlayer p, float cost) {
		if (GreenLanternEnergy.spend(p, cost)) {
			return true;
		}
		GreenLanternEnergy.feedback(p, "message.projecthero.ability.low_charge");
		return false;
	}

	/** The GL target rules, never the mech itself. */
	static boolean canHit(ServerPlayer p, HardLightMechEntity m, LivingEntity e) {
		return e != m && e != p && HeroTargets.canHarm(p, e);
	}

	static void push(LivingEntity t, Vec3 v) {
		if (com.projecthero.mod.titanshifter.TitanCombat.isBoss(t)) {
			v = v.scale(0.2);
		}
		AbilityHelpers.push(t, v);
	}

	static Vec3 flat(Vec3 v) {
		Vec3 f = new Vec3(v.x, 0, v.z);
		return f.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : f.normalize();
	}

	/** The pilot's aim, pitch included but kept off the straight up / down. */
	static Vec3 aimDir(ServerPlayer p) {
		Vec3 l = p.getLookAngle();
		return new Vec3(l.x, Mth.clamp(l.y, -0.7, 0.7), l.z).normalize();
	}

	/** The creature the pilot aims at (never the mech they are sitting in). */
	private static LivingEntity aimedEntity(ServerPlayer p, HardLightMechEntity m, double range) {
		Vec3 eye = p.getEyePosition();
		Vec3 end = eye.add(p.getLookAngle().scale(range));
		BlockHitResult bh = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		if (bh.getType() != HitResult.Type.MISS) {
			end = bh.getLocation();
		}
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(p.level(), p, eye, end, new AABB(eye, end).inflate(1.0),
				e -> e != p && e != m && e.isPickable() && AbilityHelpers.asLiving(e) != null);
		return hit == null ? null : AbilityHelpers.asLiving(hit.getEntity());
	}

	private static Vec3 aimPoint(ServerPlayer p, HardLightMechEntity m, double range) {
		LivingEntity e = aimedEntity(p, m, range);
		if (e != null) {
			return e.position().add(0, e.getBbHeight() * 0.5, 0);
		}
		Vec3 eye = p.getEyePosition();
		Vec3 end = eye.add(p.getLookAngle().scale(range));
		BlockHitResult bh = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		return bh.getType() == HitResult.Type.MISS ? end : bh.getLocation();
	}

	/** Where the chest beam ends: the pilot's aim point, clipped by blocks on the way from the chest. */
	private static Vec3 beamTarget(HardLightMechEntity m, ServerPlayer p) {
		Vec3 aim = aimPoint(p, m, GreenLanternMechConfig.BEAM_RANGE);
		Vec3 from = m.chest();
		Vec3 d = aim.subtract(from);
		if (d.lengthSqr() < 1.0) {
			aim = from.add(Vec3.directionFromRotation(0f, m.getYRot()).scale(GreenLanternMechConfig.BEAM_RANGE));
		}
		BlockHitResult bh = m.level().clip(new ClipContext(from, aim, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, m));
		return bh.getType() == HitResult.Type.MISS ? aim : bh.getLocation();
	}

	static List<LivingEntity> along(ServerLevel level, ServerPlayer p, HardLightMechEntity m, Vec3 from, Vec3 to, double radius) {
		Vec3 d = to.subtract(from);
		double len = d.length();
		List<LivingEntity> out = new ArrayList<>();
		if (len < 1.0e-3) {
			return out;
		}
		Vec3 dir = d.scale(1.0 / len);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(from, to).inflate(radius + 1.0), x -> canHit(p, m, x))) {
			Vec3 c = e.position().add(0, e.getBbHeight() * 0.5, 0);
			double t = Mth.clamp(c.subtract(from).dot(dir), 0, len);
			double reach = radius + e.getBbWidth() * 0.5;
			if (c.distanceToSqr(from.add(dir.scale(t))) <= reach * reach + e.getBbHeight() * e.getBbHeight() * 0.25) {
				out.add(e);
			}
		}
		return out;
	}

	static void sound(ServerLevel level, Vec3 at, SoundEvent s, float vol, float pitch) {
		level.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, vol, pitch);
	}
}
