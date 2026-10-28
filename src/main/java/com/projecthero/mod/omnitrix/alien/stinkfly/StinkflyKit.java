package com.projecthero.mod.omnitrix.alien.stinkfly;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.joml.Vector3f;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.omnitrix.OmnitrixSfx;
import com.projecthero.mod.omnitrix.OmnitrixSounds;
import com.projecthero.mod.omnitrix.alien.AlienKit;
import com.projecthero.mod.omnitrix.alien.AlienMove;
import com.projecthero.mod.omnitrix.alien.AlienStats;
import com.projecthero.mod.omnitrix.alien.AlienStatus;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.data.OmnitrixState;
import com.projecthero.mod.omnitrix.entity.AlienProjectileEntity;
import com.projecthero.mod.quirk.fx.QuirkCamera;
import com.projecthero.mod.quirk.fx.QuirkFx;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1: Stinkfly, the Lepidopterran -- a green insect with four eyes on stalks, translucent wings and a stinger tail. He
 * flies for real, spits slowing goo and gasses whatever is below him. Light: 14 health, but no fall damage at all.
 *
 * <pre>
 *   R        Slime Shot     a goo glob (3): roots for 1.5 s, then slows       Shift+R  Slime Net      a wide net: roots everything it lands on (3 s)
 *   G        Toxic Gas      a stinking cloud ahead (5 s: poison, nausea)      Shift+G  Stinger Stab   the tail jabs (6 + Poison II)
 *   X        Wing Flight    take off / land (double-tap jump works too)       Shift+X  Wing Dash      a burst of speed where you look
 *   Z        Dive Bomb      plunge and splash down (up to 8, slows)           Shift+Z  Wing Gust      a blast of wind: shoves, deflects projectiles
 *   C        Goo Storm      goo rains on an area for 6 s (slows, stings)     Shift+C  Slime Barrage  five globs in a fan
 *   Attack   stinger-tipped jab (poisons)       Use   a quick goo spit (empty hand)
 * </pre>
 */
public final class StinkflyKit extends AlienKit {
	public static final String ID = "stinkfly";
	public static final int COLOUR = 0x9BCB3F;
	public static final int GOO = 0xB8E04A;

	// v0.17.1 sound rework: OmnitrixSfx recipes from vanilla samples (see docs/OMNITRIX_SOUNDS_0171.md)
	/** The wings: an insect buzz (the flying loop plays it quietly every 16 ticks). */
	public static final OmnitrixSfx.Cue BUZZ = OmnitrixSfx.cue("stinkfly_buzz")
			.layer(SoundEvents.BEE_POLLINATE, 0.4f, 0.8f)
			.layer(SoundEvents.PHANTOM_FLAP, 0.18f, 1.8f)
			.jitter(0.08f).gap(6);
	/** Slime spat: a wet spit. */
	public static final OmnitrixSfx.Cue SPIT = OmnitrixSfx.cue("stinkfly_spit")
			.layer(SoundEvents.LLAMA_SPIT, 0.45f, 1.0f)
			.layer(SoundEvents.SLIME_SQUISH_SMALL, 0.3f, 1.2f);
	/** Slime landing: a wet splat. */
	public static final OmnitrixSfx.Cue SPLAT = OmnitrixSfx.cue("stinkfly_splat")
			.layer(SoundEvents.SLIME_SQUISH, 0.45f, 0.9f)
			.layer(SoundEvents.HONEY_BLOCK_FALL, 0.3f, 0.8f)
			.gap(3);
	/** A stink cloud: a hiss and a bubbling. */
	public static final OmnitrixSfx.Cue GAS = OmnitrixSfx.cue("stinkfly_gas")
			.layer(SoundEvents.FIRE_EXTINGUISH, 0.3f, 0.5f)
			.layer(SoundEvents.BREWING_STAND_BREW, 0.3f, 0.6f)
			.layer(SoundEvents.SLIME_SQUISH, 0.2f, 0.6f);
	public static final OmnitrixSfx.Cue STING = OmnitrixSfx.cue("stinkfly_sting")
			.layer(SoundEvents.BEE_STING, 0.45f, 0.8f)
			.layer(SoundEvents.PLAYER_ATTACK_WEAK, 0.3f, 1.2f);
	/** A dive: the wings revving into a rush. */
	public static final OmnitrixSfx.Cue DIVE = OmnitrixSfx.cue("stinkfly_dive")
			.layer(SoundEvents.BEE_POLLINATE, 0.45f, 1.1f)
			.layer(SoundEvents.WIND_CHARGE_THROW, 0.3f, 1.0f)
			.layer(SoundEvents.PHANTOM_FLAP, 0.25f, 1.4f);

	public static final int ANIM_SHOT = 1;
	public static final int ANIM_NET = 2;
	public static final int ANIM_GAS = 3;
	public static final int ANIM_STAB = 4;
	public static final int ANIM_DASH = 5;
	public static final int ANIM_DIVE = 6;
	public static final int ANIM_GUST = 7;
	public static final int ANIM_STORM = 8;
	public static final int ANIM_BARRAGE = 9;
	public static final int ANIM_JAB = 10;
	public static final int ANIM_TAKEOFF = 11;
	public static final int ANIM_SPLASH = 12;

	public static final int F_FLYING = 1;
	public static final int F_DIVING = 2;

	public static final String SLIME_SHOT = "slime_shot";
	public static final String SLIME_NET = "slime_net";
	public static final String TOXIC_GAS = "toxic_gas";
	public static final String STINGER = "stinger_stab";
	public static final String FLIGHT = "wing_flight";
	public static final String WING_DASH = "wing_dash";
	public static final String DIVE_BOMB = "dive_bomb";
	public static final String WING_GUST = "wing_gust";
	public static final String GOO_STORM = "goo_storm";
	public static final String BARRAGE = "slime_barrage";
	public static final String JAB = "stinger_jab";
	public static final String GOO_SPIT = "goo_spit";

	public static final float SHOT_DAMAGE = 3f;
	public static final int SHOT_ROOT = 30;
	public static final float NET_RADIUS = 2.75f;
	public static final int NET_ROOT = 60;
	public static final float GAS_RADIUS = 3.25f;
	public static final int GAS_TICKS = 100;
	public static final float GAS_DAMAGE = 1.5f;
	public static final float STAB_DAMAGE = 6f;
	public static final float DIVE_DAMAGE = 8f;
	public static final float DIVE_RADIUS = 3.5f;
	public static final float GUST_DAMAGE = 2f;
	public static final float STORM_RADIUS = 6f;
	public static final int STORM_TICKS = 120;
	public static final float STORM_DAMAGE = 1.5f;
	public static final float BARRAGE_DAMAGE = 2.5f;
	public static final float FLY_SPEED = 0.06f;

	private static final DustParticleOptions GOO_DUST = new DustParticleOptions(new Vector3f(0.72f, 0.88f, 0.29f), 1.2f);
	private static final DustParticleOptions GAS_DUST = new DustParticleOptions(new Vector3f(0.55f, 0.7f, 0.2f), 2.0f);

	/** player -> the gas cloud / goo storm they left (centre, dimension, end time). */
	private static final Map<UUID, Area> GAS_CLOUDS = new ConcurrentHashMap<>();
	private static final Map<UUID, Area> STORMS = new ConcurrentHashMap<>();

	private record Area(ResourceKey<Level> dim, Vec3 at, long until) {
	}

	private StinkflyKit() {
	}

	public static void register() {
		Aliens.register(new StinkflyKit());
		AlienProjectileEntity.onHit("stinkfly_slime", (proj, hit) -> splat(proj, hit, false));
		AlienProjectileEntity.onHit("stinkfly_spit", (proj, hit) -> splat(proj, hit, false));
		AlienProjectileEntity.onHit("stinkfly_net", (proj, hit) -> splat(proj, hit, true));
	}

	public static void clearSessionState() {
		GAS_CLOUDS.clear();
		STORMS.clear();
	}

	private static void splat(AlienProjectileEntity proj, HitResult hit, boolean net) {
		if (!(proj.level() instanceof ServerLevel l) || !(proj.getOwner() instanceof ServerPlayer owner)) {
			return;
		}
		Vec3 at = hit.getLocation();
		l.sendParticles(ParticleTypes.ITEM_SLIME, at.x, at.y, at.z, net ? 40 : 14, net ? 1.0 : 0.25, 0.2, net ? 1.0 : 0.25, 0.1);
		l.sendParticles(GOO_DUST, at.x, at.y, at.z, net ? 20 : 6, net ? 1.0 : 0.2, 0.2, net ? 1.0 : 0.2, 0);
		OmnitrixSounds.play(l, at, SPLAT, net ? 1.0f : 0.7f, net ? 0.8f : 1.1f + l.random.nextFloat() * 0.2f);
		if (net) {
			QuirkFx.decal(l, groundBelow(l, at)).style(QuirkFx.DECAL_POOL).colour(0x6A9A1A).size(NET_RADIUS * 1.4f).life(160).spawn();
			QuirkFx.ring(l, at).colours(0xE8FFB0, GOO).size(NET_RADIUS).thickness(0.35f).life(10).spawn();
			for (LivingEntity e : around(owner, at, NET_RADIUS)) {
				AlienStatus.root(owner, e, NET_ROOT, 40);
			}
		} else if (hit instanceof EntityHitResult eh && eh.getEntity() instanceof LivingEntity e) {
			if ("stinkfly_slime".equals(proj.kind())) {
				AlienStatus.root(owner, e, SHOT_ROOT, 60);
			} else {
				e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1), owner);
			}
		} else {
			QuirkFx.decal(l, at).style(QuirkFx.DECAL_POOL).colour(0x6A9A1A).size(0.9f).life(100).spawn();
		}
	}

	private static Vec3 groundBelow(ServerLevel l, Vec3 at) {
		var hit = l.clip(new ClipContext(at.add(0, 0.5, 0), at.add(0, -6, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
				net.minecraft.world.phys.shapes.CollisionContext.empty()));
		return hit.getType() == HitResult.Type.BLOCK ? hit.getLocation().add(0, 0.05, 0) : at;
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public AlienStats stats() {
		return AlienStats.builder().health(14f).speed(1.1f).scale(0.9f).armor(1f, 0f).attack(1f).jump(0.08f).build();
	}

	@Override
	protected List<AlienMove> declareMoves() {
		return List.of(
				AlienMove.tap(SLIME_SHOT, AbilitySlot.SLOT_1, 16), AlienMove.shift(SLIME_NET, AbilitySlot.SLOT_1, 160),
				AlienMove.tap(TOXIC_GAS, AbilitySlot.SLOT_2, 220), AlienMove.shift(STINGER, AbilitySlot.SLOT_2, 50),
				AlienMove.tap(FLIGHT, AbilitySlot.SLOT_3, 8), AlienMove.shift(WING_DASH, AbilitySlot.SLOT_3, 60),
				AlienMove.tap(DIVE_BOMB, AbilitySlot.SLOT_4, 140), AlienMove.shift(WING_GUST, AbilitySlot.SLOT_4, 120),
				AlienMove.tap(GOO_STORM, AbilitySlot.SLOT_6, 600), AlienMove.shift(BARRAGE, AbilitySlot.SLOT_6, 240),
				AlienMove.vanilla(JAB, "Attack", 20), AlienMove.vanilla(GOO_SPIT, "Use", 12));
	}

	// ---------------------------------------------------------------- lifecycle

	@Override
	public void onTransform(ServerPlayer p) {
		ServerLevel l = level(p);
		p.getAbilities().mayfly = true;
		p.getAbilities().setFlyingSpeed(FLY_SPEED);
		p.onUpdateAbilities();
		OmnitrixSounds.play(l, p.position(), BUZZ, 1.0f, 1.0f);
		l.sendParticles(ParticleTypes.ITEM_SLIME, p.getX(), p.getY() + 1.0, p.getZ(), 16, 0.4, 0.5, 0.4, 0.05);
	}

	@Override
	public void onRevert(ServerPlayer p) {
		GAS_CLOUDS.remove(p.getUUID());
		STORMS.remove(p.getUUID());
		setFlag(p, F_FLYING | F_DIVING, false);
		if (timer(p, "dive_at") > 0) {
			setTimer(p, "dive_at", 0);
		}
		if (timer(p, "dive_end") > 0) {
			setTimer(p, "dive_end", 0);
		}
		net.minecraft.world.level.GameType mode = p.gameMode.getGameModeForPlayer();
		if (mode != net.minecraft.world.level.GameType.CREATIVE && mode != net.minecraft.world.level.GameType.SPECTATOR) {
			p.getAbilities().mayfly = false;
			p.getAbilities().flying = false;
		}
		p.getAbilities().setFlyingSpeed(0.05f);
		p.onUpdateAbilities();
		p.resetFallDistance();
	}

	@Override
	public void tick(ServerPlayer p, OmnitrixState s, long now) {
		ServerLevel l = level(p);
		// the wings: always ready (X or a double-tap of jump takes off); others see them beat
		if (!p.getAbilities().mayfly) {
			p.getAbilities().mayfly = true;
			p.getAbilities().setFlyingSpeed(FLY_SPEED);
			p.onUpdateAbilities();
		}
		boolean flying = p.getAbilities().flying;
		if (flying != flag(p, F_FLYING)) {
			setFlag(p, F_FLYING, flying);
		}
		if (flying && p.tickCount % 16 == 0) {
			OmnitrixSounds.play(l, p.position(), BUZZ, 0.25f, 0.95f + p.getRandom().nextFloat() * 0.1f);
		}
		if (p.tickCount % 40 == 0) {
			l.sendParticles(ParticleTypes.ITEM_SLIME, p.getX(), p.getY() + 0.4, p.getZ(), 1, 0.15, 0.1, 0.15, 0); // a goo drip
		}
		p.resetFallDistance();
		// Dive Bomb: the hop up, then the plunge, then the splash
		long diveAt = timer(p, "dive_at");
		if (diveAt > 0 && now >= diveAt) {
			setTimer(p, "dive_at", 0);
			startDive(p, now);
		}
		if (flag(p, F_DIVING)) {
			long end = timer(p, "dive_end");
			boolean landed = p.onGround() || p.isInWaterOrBubble() || p.horizontalCollision || p.verticalCollisionBelow;
			if (landed || now >= end) {
				diveImpact(p);
			} else {
				Vec3 v = p.getDeltaMovement();
				if (v.y > -1.6) {
					p.setDeltaMovement(v.x, Math.min(v.y, -0.9) - 0.12, v.z);
					p.hurtMarked = true;
				}
				l.sendParticles(GOO_DUST, p.getX(), p.getY() + 0.5, p.getZ(), 3, 0.2, 0.2, 0.2, 0);
			}
		}
		// the gas cloud
		Area gas = GAS_CLOUDS.get(p.getUUID());
		if (gas != null) {
			if (now >= gas.until() || gas.dim() != l.dimension()) {
				GAS_CLOUDS.remove(p.getUUID());
			} else {
				if (now % 3 == 0) {
					l.sendParticles(ParticleTypes.SNEEZE, gas.at().x, gas.at().y + 0.8, gas.at().z, 10, GAS_RADIUS * 0.55, 0.6, GAS_RADIUS * 0.55, 0.01);
					l.sendParticles(GAS_DUST, gas.at().x, gas.at().y + 0.6, gas.at().z, 6, GAS_RADIUS * 0.5, 0.5, GAS_RADIUS * 0.5, 0);
				}
				if (now % 10 == 0) {
					for (LivingEntity e : around(p, gas.at().add(0, 0.8, 0), GAS_RADIUS)) {
						hurt(p, e, GAS_DAMAGE, false);
						e.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0), p);
						e.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100, 0), p);
					}
				}
			}
		}
		// the goo storm
		Area storm = STORMS.get(p.getUUID());
		if (storm != null) {
			if (now >= storm.until() || storm.dim() != l.dimension()) {
				STORMS.remove(p.getUUID());
			} else {
				if (now % 2 == 0) {
					for (int k = 0; k < 4; k++) {
						double a = l.random.nextDouble() * Math.PI * 2, r = Math.sqrt(l.random.nextDouble()) * STORM_RADIUS;
						double x = storm.at().x + Math.cos(a) * r, z = storm.at().z + Math.sin(a) * r;
						l.sendParticles(ParticleTypes.ITEM_SLIME, x, storm.at().y + 5.5, z, 0, 0, -1.0, 0, 0.6);
						l.sendParticles(GOO_DUST, x, storm.at().y + 4 + l.random.nextDouble() * 2, z, 1, 0, 0, 0, 0);
					}
				}
				if (now % 10 == 0) {
					for (LivingEntity e : around(p, storm.at().add(0, 1, 0), STORM_RADIUS)) {
						e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 2), p);
						if (now % 20 == 0) {
							hurt(p, e, STORM_DAMAGE, false);
						}
					}
				}
				if (now % 30 == 0) {
					OmnitrixSounds.play(l, storm.at(), SPLAT, 0.6f, 0.7f + l.random.nextFloat() * 0.3f);
				}
			}
		}
	}

	// ---------------------------------------------------------------- keys

	@Override
	public void press(ServerPlayer p, AbilitySlot slot, boolean shift) {
		switch (slot) {
			case SLOT_1 -> {
				if (shift) {
					slimeNet(p);
				} else {
					slimeShot(p);
				}
			}
			case SLOT_2 -> {
				if (shift) {
					stingerStab(p);
				} else {
					toxicGas(p);
				}
			}
			case SLOT_3 -> {
				if (shift) {
					wingDash(p);
				} else {
					wingFlight(p);
				}
			}
			case SLOT_4 -> {
				if (shift) {
					wingGust(p);
				} else {
					diveBomb(p);
				}
			}
			case SLOT_6 -> {
				if (shift) {
					slimeBarrage(p);
				} else {
					gooStorm(p);
				}
			}
			default -> {
			}
		}
	}

	public boolean slimeShot(ServerPlayer p) {
		if (!use(p, SLIME_SHOT)) {
			return false;
		}
		anim(p, ANIM_SHOT);
		AlienProjectileEntity.of(p, "stinkfly_slime").colour(GOO).size(0.4f).damage(SHOT_DAMAGE).gravity(0.012).speed(1.6).life(50).spawn();
		OmnitrixSounds.play(level(p), p.position(), SPIT, 0.9f, 0.95f + p.getRandom().nextFloat() * 0.15f);
		return true;
	}

	public boolean slimeNet(ServerPlayer p) {
		if (!use(p, SLIME_NET)) {
			return false;
		}
		anim(p, ANIM_NET);
		AlienProjectileEntity.of(p, "stinkfly_net").colour(0xD8F070).size(0.85f).damage(2f).gravity(0.025).speed(1.15).life(60).spawn();
		OmnitrixSounds.play(level(p), p.position(), SPIT, 1.0f, 0.7f);
		return true;
	}

	public boolean toxicGas(ServerPlayer p) {
		if (!use(p, TOXIC_GAS)) {
			return false;
		}
		anim(p, ANIM_GAS);
		ServerLevel l = level(p);
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		var hit = l.clip(new ClipContext(eye, eye.add(look.scale(6)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		Vec3 at = groundBelow(l, hit.getType() == HitResult.Type.BLOCK ? hit.getLocation().subtract(look.scale(0.5)) : eye.add(look.scale(4)));
		GAS_CLOUDS.put(p.getUUID(), new Area(l.dimension(), at, l.getGameTime() + GAS_TICKS));
		QuirkFx.flipbook(l, at.add(0, 1.0, 0)).style(QuirkFx.BOOK_SMOKE).colours(0xC8E070, 0x5A7A20).size(GAS_RADIUS * 1.5f).life(40).spawn();
		QuirkFx.ring(l, at).colours(0xC8E070, 0x6A8A20).style(QuirkFx.RING_STEAM).size(GAS_RADIUS + 0.5f).thickness(0.8f).life(GAS_TICKS / 2).ground().spawn();
		OmnitrixSounds.play(l, at, GAS, 1.0f, 1.0f);
		return true;
	}

	public boolean stingerStab(ServerPlayer p) {
		if (!use(p, STINGER)) {
			return false;
		}
		anim(p, ANIM_STAB);
		ServerLevel l = level(p);
		OmnitrixSounds.play(l, p.position(), STING, 1.0f, 1.0f);
		LivingEntity t = lookTarget(p, 3.75);
		if (t != null) {
			hurt(p, t, STAB_DAMAGE, false);
			t.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 1), p);
			l.sendParticles(ParticleTypes.CRIT, t.getX(), t.getY() + t.getBbHeight() * 0.6, t.getZ(), 10, 0.2, 0.2, 0.2, 0.2);
			l.sendParticles(GOO_DUST, t.getX(), t.getY() + t.getBbHeight() * 0.6, t.getZ(), 6, 0.2, 0.2, 0.2, 0);
		}
		return true;
	}

	/** X: take off (a hop up into flight) or land. The wings are always ready, so a double-tap of jump flies too. */
	public boolean wingFlight(ServerPlayer p) {
		if (!use(p, FLIGHT)) {
			return false;
		}
		ServerLevel l = level(p);
		p.getAbilities().mayfly = true;
		p.getAbilities().setFlyingSpeed(FLY_SPEED);
		if (p.getAbilities().flying) {
			p.getAbilities().flying = false;
			p.onUpdateAbilities();
			setFlag(p, F_FLYING, false);
			OmnitrixSounds.play(l, p.position(), BUZZ, 0.6f, 0.8f);
			return true;
		}
		p.getAbilities().flying = true;
		p.onUpdateAbilities();
		setFlag(p, F_FLYING, true);
		anim(p, ANIM_TAKEOFF);
		p.push(0, 0.5, 0);
		p.hurtMarked = true;
		OmnitrixSounds.play(l, p.position(), BUZZ, 1.0f, 1.1f);
		QuirkFx.ring(l, p.position().add(0, 0.05, 0)).colours(0xF0FFE0, COLOUR).style(QuirkFx.RING_SOFT).size(2.2f).thickness(0.4f).life(10).ground().spawn();
		return true;
	}

	public boolean wingDash(ServerPlayer p) {
		if (!use(p, WING_DASH)) {
			return false;
		}
		anim(p, ANIM_DASH);
		Vec3 look = p.getLookAngle();
		p.setDeltaMovement(look.scale(1.7).add(0, 0.1, 0));
		p.hurtMarked = true;
		ServerLevel l = level(p);
		QuirkFx.trail(l, p).colours(0xF0FFE0, COLOUR).size(0.35f).size2(8f).life(16).vec(new Vec3(0, 0.9, 0)).spawn();
		OmnitrixSounds.play(l, p.position(), BUZZ, 1.0f, 1.4f);
		return true;
	}

	public boolean diveBomb(ServerPlayer p) {
		if (!use(p, DIVE_BOMB)) {
			return false;
		}
		ServerLevel l = level(p);
		long now = l.getGameTime();
		if (p.onGround()) {
			// a hop up first, then the plunge
			anim(p, ANIM_TAKEOFF);
			p.setDeltaMovement(p.getDeltaMovement().x, 1.25, p.getDeltaMovement().z);
			p.hurtMarked = true;
			setTimer(p, "dive_at", now + 9);
			OmnitrixSounds.play(l, p.position(), BUZZ, 1.0f, 1.2f);
		} else {
			startDive(p, now);
		}
		return true;
	}

	private void startDive(ServerPlayer p, long now) {
		anim(p, ANIM_DIVE);
		if (p.getAbilities().flying) {
			p.getAbilities().flying = false;
			p.onUpdateAbilities();
		}
		Vec3 look = p.getLookAngle();
		Vec3 dir = new Vec3(look.x, Math.min(-0.65, look.y), look.z).normalize();
		p.setDeltaMovement(dir.scale(2.1));
		p.hurtMarked = true;
		setFlag(p, F_DIVING, true);
		setTimer(p, "dive_end", now + 60);
		ServerLevel l = level(p);
		QuirkFx.trail(l, p).colours(0xF0FFE0, GOO).size(0.4f).size2(10f).life(24).vec(new Vec3(0, 0.6, 0)).spawn();
		OmnitrixSounds.play(l, p.position(), DIVE, 1.0f, 1.0f);
	}

	/** The splash-down: a goo shockwave around where he lands. */
	public void diveImpact(ServerPlayer p) {
		setFlag(p, F_DIVING, false);
		setTimer(p, "dive_end", 0);
		anim(p, ANIM_SPLASH);
		ServerLevel l = level(p);
		Vec3 c = p.position();
		QuirkFx.blast(l, c.add(0, 0.3, 0)).colours(0xF0FFB0, GOO).size(DIVE_RADIUS * 0.7f).life(16).flags(QuirkFx.F_NO_SMOKE).spawn();
		QuirkFx.ring(l, c.add(0, 0.05, 0)).colours(0xF0FFB0, GOO).size(DIVE_RADIUS + 0.5f).thickness(0.6f).life(14).ground().spawn();
		QuirkFx.decal(l, c.add(0, 0.05, 0)).style(QuirkFx.DECAL_POOL).colour(0x6A9A1A).size(DIVE_RADIUS * 1.3f).life(200).spawn();
		l.sendParticles(ParticleTypes.ITEM_SLIME, c.x, c.y + 0.3, c.z, 40, DIVE_RADIUS * 0.4, 0.2, DIVE_RADIUS * 0.4, 0.2);
		OmnitrixSounds.play(l, c, SPLAT, 1.3f, 0.6f);
		QuirkCamera.shakeAround(l, c, 10, 0.35f, 8);
		for (LivingEntity e : around(p, c.add(0, 0.5, 0), DIVE_RADIUS)) {
			double d = Math.sqrt(e.distanceToSqr(c));
			hurt(p, e, (float) (DIVE_DAMAGE * (1.0 - 0.4 * Math.min(1.0, d / DIVE_RADIUS))), false);
			e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1), p);
			shove(e, c, 0.6, 0.3);
		}
		p.resetFallDistance();
	}

	public boolean wingGust(ServerPlayer p) {
		if (!use(p, WING_GUST)) {
			return false;
		}
		anim(p, ANIM_GUST);
		ServerLevel l = level(p);
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		QuirkFx.cone(l, eye.add(look.scale(0.6)).add(0, -0.3, 0), look).colours(0xF4FFF0, 0xA8D870).style(QuirkFx.CONE_DEBRIS).size(2.6f).length(7f)
				.life(12).spawn();
		l.sendParticles(ParticleTypes.CLOUD, eye.x + look.x * 2, eye.y + look.y * 2 - 0.3, eye.z + look.z * 2, 14, 0.6, 0.4, 0.6, 0.15);
		OmnitrixSounds.play(l, p.position(), BUZZ, 1.2f, 0.6f);
		p.clearFire();
		for (LivingEntity e : around(p, eye.add(look.scale(3.5)), 4.5)) {
			Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eye);
			if (to.length() <= 7.5 && to.normalize().dot(look) > Math.cos(Math.toRadians(40))) {
				hurt(p, e, GUST_DAMAGE, false);
				e.clearFire();
				e.push(look.x * 1.5, 0.35, look.z * 1.5);
				e.hurtMarked = true;
			}
		}
		// projectiles caught in the gust are blown away
		for (Projectile pr : l.getEntitiesOfClass(Projectile.class, p.getBoundingBox().expandTowards(look.scale(6)).inflate(1.5),
				pr -> pr.getOwner() != p)) {
			l.sendParticles(ParticleTypes.CLOUD, pr.getX(), pr.getY(), pr.getZ(), 4, 0.1, 0.1, 0.1, 0.02);
			pr.discard();
		}
		return true;
	}

	public boolean gooStorm(ServerPlayer p) {
		if (!use(p, GOO_STORM)) {
			return false;
		}
		anim(p, ANIM_STORM);
		ServerLevel l = level(p);
		Vec3 eye = p.getEyePosition();
		var hit = l.clip(new ClipContext(eye, eye.add(p.getLookAngle().scale(20)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		Vec3 at = hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : groundBelow(l, p.position());
		at = groundBelow(l, at.add(0, 0.5, 0));
		STORMS.put(p.getUUID(), new Area(l.dimension(), at, l.getGameTime() + STORM_TICKS));
		QuirkFx.decal(l, at.add(0, 0.05, 0)).style(QuirkFx.DECAL_POOL).colour(0x6A9A1A).size(STORM_RADIUS * 1.2f).life(STORM_TICKS + 80).spawn();
		QuirkFx.ring(l, at.add(0, 0.05, 0)).colours(0xF0FFB0, GOO).size(STORM_RADIUS).thickness(0.5f).life(20).ground().spawn();
		OmnitrixSounds.play(l, at, GAS, 1.0f, 0.6f);
		OmnitrixSounds.play(l, p.position(), SPIT, 1.0f, 0.6f);
		return true;
	}

	public boolean slimeBarrage(ServerPlayer p) {
		if (!use(p, BARRAGE)) {
			return false;
		}
		anim(p, ANIM_BARRAGE);
		Vec3 look = p.getLookAngle();
		for (int k = -2; k <= 2; k++) {
			Vec3 dir = look.yRot((float) Math.toRadians(k * 9)).add(0, (p.getRandom().nextDouble() - 0.5) * 0.06, 0);
			AlienProjectileEntity.of(p, "stinkfly_slime").colour(GOO).size(0.32f).damage(BARRAGE_DAMAGE).gravity(0.015).direction(dir).speed(1.45)
					.life(40).spawn();
		}
		OmnitrixSounds.play(level(p), p.position(), SPIT, 1.0f, 1.3f);
		return true;
	}

	// ---------------------------------------------------------------- vanilla keys

	@Override
	public boolean onAttack(ServerPlayer p, Entity target) {
		if (target instanceof LivingEntity le) {
			anim(p, ANIM_JAB);
			if (ready(p, JAB)) {
				cooldown(p, JAB, -1);
				le.addEffect(new MobEffectInstance(MobEffects.POISON, 40, 0), p);
				level(p).sendParticles(GOO_DUST, le.getX(), le.getY() + le.getBbHeight() * 0.6, le.getZ(), 5, 0.2, 0.2, 0.2, 0);
			}
		}
		return false;
	}

	@Override
	public InteractionResult onUse(ServerPlayer p, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND || !p.getItemInHand(hand).isEmpty()) {
			return InteractionResult.PASS;
		}
		if (!use(p, GOO_SPIT)) {
			return InteractionResult.FAIL;
		}
		anim(p, ANIM_SHOT);
		AlienProjectileEntity.of(p, "stinkfly_spit").colour(0xC8E860).size(0.24f).damage(2f).gravity(0.02).speed(1.4).life(30).spawn();
		OmnitrixSounds.play(level(p), p.position(), SPIT, 0.6f, 1.4f);
		p.swing(hand, true);
		return InteractionResult.SUCCESS;
	}

	// ---------------------------------------------------------------- damage

	/** No fall damage, ever, and no "flew into a wall" either: he lands like a bug. */
	@Override
	public boolean allowDamage(ServerPlayer p, DamageSource source, float amount) {
		return !source.is(DamageTypeTags.IS_FALL) && !source.is(DamageTypes.FLY_INTO_WALL);
	}
}
