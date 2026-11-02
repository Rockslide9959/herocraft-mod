package com.projecthero.mod.omnitrix.alien.brainstorm;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.omnitrix.OmnitrixSfx;
import com.projecthero.mod.omnitrix.alien.AlienKit;
import com.projecthero.mod.omnitrix.alien.AlienMove;
import com.projecthero.mod.omnitrix.alien.AlienStats;
import com.projecthero.mod.omnitrix.alien.AlienStatus;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.data.OmnitrixState;
import com.projecthero.mod.omnitrix.entity.AlienProjectileEntity;
import com.projecthero.mod.quirk.QuirkThreat;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 (the Recalibrated Omnitrix, basic version -- user: "Just make them basic versions of the aliens for now please that we
 * can revamp later"): Brainstorm, the Cerebrocrustacean -- a crab-like genius whose shell opens on a huge brain that throws
 * high-voltage lightning.
 *
 * <pre>
 *   R        Lightning Bolt     a bolt at the crosshair (20): 7                    Shift+R  Chain Lightning  14, then jumps to 4 more within 7: 12 each
 *   G        Arc Pincer         a shocking claw snap (cone 3.5): 13 + 1 s stun     Shift+G  Ball Lightning   a slow crackling orb: blast r 3.5, 16
 *   X        Static Hop         a charged leap (no fall damage)                    Shift+X  Bolt Dash        8 blocks as a bolt: 6 to all he passes
 *   Z        Discharge          r 5 around him: 17 + thrown back                   Shift+Z  Mega Bolt        1 s charge, a piercing beam (24): 19
 *   V        Genius Analysis    reads a creature (24): +25% from his moves 10 s   Shift+V  Force Field      6 s bubble: projectiles stopped, -60%
 *   C        Electric Storm     ULTIMATE: a 6 s storm cloud (r 8): a strike on      Shift+C  Static Cage      12 + a 3 s lightning cage (stun)
 *                               every foe under it each 0.5 s, 5 (60 max)
 * </pre>
 *
 * Passives: lightning never hurts him; the Force Field. Weaknesses: water shorts him out (his moves deal half while he is in it);
 * a slow, heavy shell (0.9x speed).
 *
 * <p>The bolts are drawn by the client from short-lived held {@link AlienProjectileEntity} carriers: {@code brainstorm_arc} sits at
 * the END of a bolt and its colour says where it starts (0 = the owner's claws, n = entity id n - 1), {@code brainstorm_strike} is
 * a bolt from the sky down to it, {@code brainstorm_cloud} the storm cloud (size = radius), {@code brainstorm_cage} the cage round
 * the creature with id colour - 1.
 */
public final class BrainstormKit extends AlienKit {
	public static final String ID = Aliens.BRAINSTORM.id();

	// ---------------------------------------------------------------- sounds (vanilla samples, quiet and layered)
	public static final OmnitrixSfx.Cue ZAP = OmnitrixSfx.cue("brainstorm_zap")
			.layer(SoundEvents.TRIDENT_THUNDER, 0.12f, 1.9f)
			.layer(SoundEvents.AMETHYST_BLOCK_HIT, 0.25f, 1.8f)
			.layer(SoundEvents.FIRECHARGE_USE, 0.18f, 1.7f)
			.gap(2);
	public static final OmnitrixSfx.Cue CHAIN = OmnitrixSfx.cue("brainstorm_chain")
			.layer(SoundEvents.TRIDENT_THUNDER, 0.2f, 1.6f)
			.layer(SoundEvents.FIRECHARGE_USE, 0.2f, 1.5f)
			.layer(SoundEvents.FIRECHARGE_USE, 0.15f, 1.8f, 3)
			.layer(SoundEvents.FIRECHARGE_USE, 0.12f, 1.95f, 6);
	public static final OmnitrixSfx.Cue SNAP = OmnitrixSfx.cue("brainstorm_snap")
			.layer(SoundEvents.TURTLE_EGG_CRACK, 0.35f, 0.8f)
			.layer(SoundEvents.FIRECHARGE_USE, 0.2f, 1.8f)
			.layer(SoundEvents.PLAYER_ATTACK_STRONG, 0.25f, 1.2f);
	public static final OmnitrixSfx.Cue ORB = OmnitrixSfx.cue("brainstorm_orb")
			.layer(SoundEvents.BEACON_ACTIVATE, 0.2f, 1.9f)
			.layer(SoundEvents.FIRECHARGE_USE, 0.18f, 1.3f);
	public static final OmnitrixSfx.Cue HOP = OmnitrixSfx.cue("brainstorm_hop")
			.layer(SoundEvents.ARMADILLO_SCUTE_DROP, 0.4f, 0.8f)
			.layer(SoundEvents.FIRECHARGE_USE, 0.15f, 1.9f);
	public static final OmnitrixSfx.Cue BOOM = OmnitrixSfx.cue("brainstorm_boom")
			.layer(SoundEvents.LIGHTNING_BOLT_IMPACT, 0.3f, 1.4f)
			.layer(SoundEvents.TRIDENT_THUNDER, 0.25f, 1.2f)
			.layer(SoundEvents.GENERIC_EXPLODE, 0.12f, 1.8f, 1);
	public static final OmnitrixSfx.Cue CHARGE = OmnitrixSfx.cue("brainstorm_charge")
			.layer(SoundEvents.BEACON_POWER_SELECT, 0.25f, 1.6f)
			.layer(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.22f, 1.6f)
			.layer(SoundEvents.BEACON_POWER_SELECT, 0.2f, 1.95f, 10);
	public static final OmnitrixSfx.Cue THINK = OmnitrixSfx.cue("brainstorm_think")
			.layer(SoundEvents.BOOK_PAGE_TURN, 0.3f, 1.2f)
			.layer(SoundEvents.AMETHYST_BLOCK_CHIME, 0.3f, 1.5f)
			.layer(SoundEvents.LEVER_CLICK, 0.15f, 1.6f, 3);
	public static final OmnitrixSfx.Cue FIELD = OmnitrixSfx.cue("brainstorm_field")
			.layer(SoundEvents.BEACON_ACTIVATE, 0.3f, 1.4f)
			.layer(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.3f, 1.2f);
	public static final OmnitrixSfx.Cue FIELD_DOWN = OmnitrixSfx.cue("brainstorm_field_down")
			.layer(SoundEvents.BEACON_DEACTIVATE, 0.3f, 1.5f);
	public static final OmnitrixSfx.Cue THUNDER = OmnitrixSfx.cue("brainstorm_thunder")
			.layer(SoundEvents.LIGHTNING_BOLT_THUNDER, 0.2f, 1.3f)
			.layer(SoundEvents.TRIDENT_THUNDER, 0.25f, 1.0f)
			.gap(4);
	public static final OmnitrixSfx.Cue STORM = OmnitrixSfx.cue("brainstorm_storm")
			.layer(SoundEvents.LIGHTNING_BOLT_THUNDER, 0.3f, 0.8f)
			.layer(SoundEvents.WEATHER_RAIN_ABOVE, 0.3f, 0.7f)
			.layer(SoundEvents.TRIDENT_THUNDER, 0.25f, 0.9f, 6);

	// ---------------------------------------------------------------- poses, flags, moves
	public static final int ANIM_BOLT = 1, ANIM_CHAIN = 2, ANIM_PINCER = 3, ANIM_ORB = 4, ANIM_HOP = 5, ANIM_DASH = 6, ANIM_DISCHARGE = 7,
			ANIM_MEGA = 8, ANIM_ANALYZE = 9, ANIM_FIELD = 10, ANIM_STORM = 11, ANIM_CAGE = 12;

	/** The Force Field is up. */
	public static final int F_FIELD = 1;
	/** A hop / dash in flight (no fall damage until he lands). */
	public static final int F_HOP = 2;
	/** The Mega Bolt is charging. */
	public static final int F_CHARGE = 4;

	public static final String BOLT = "lightning_bolt", CHAIN_LIGHTNING = "chain_lightning", PINCER = "arc_pincer", BALL = "ball_lightning",
			STATIC_HOP = "static_hop", BOLT_DASH = "bolt_dash", DISCHARGE = "discharge", MEGA_BOLT = "mega_bolt", ANALYSIS = "genius_analysis",
			FORCE_FIELD = "force_field", ELECTRIC_STORM = "electric_storm", STATIC_CAGE = "static_cage";

	/** Kit timers: the Force Field's end, the Mega Bolt's release, the hop's start. */
	public static final String FIELD_END = "field_end", MEGA_AT = "mega_at", HOP_AT = "hop_at";

	public static final float BOLT_DAMAGE = 7f, CHAIN_FIRST = 14f, CHAIN_DAMAGE = 12f, PINCER_DAMAGE = 13f, BALL_DAMAGE = 16f, DASH_DAMAGE = 6f,
			DISCHARGE_DAMAGE = 17f, MEGA_DAMAGE = 19f, STORM_DAMAGE = 5f, CAGE_DAMAGE = 12f;
	public static final double BOLT_RANGE = 20, CHAIN_RANGE = 7, PINCER_RANGE = 3.5, DISCHARGE_RADIUS = 5, MEGA_RANGE = 24, ANALYZE_RANGE = 24,
			STORM_RADIUS = 8, STORM_RANGE = 24, CAGE_RANGE = 16, DASH_DISTANCE = 8, FIELD_RADIUS = 2.2;
	public static final int CHAIN_JUMPS = 4, PINCER_STUN = 20, FIELD_TICKS = 120, MEGA_CHARGE = 20, STORM_TICKS = 120, STORM_PULSE = 10,
			MARK_TICKS = 200, CAGE_TICKS = 60;
	/** Genius Analysis: a read creature takes this much more from his moves. */
	public static final float MARK_FACTOR = 1.25f;
	/** The Force Field keeps this share of damage that gets through. */
	public static final float FIELD_TAKEN = 0.4f;
	/** In water his electricity shorts out: his moves deal this share. */
	public static final float WATER_FACTOR = 0.5f;

	// ---------------------------------------------------------------- per-player state (cleared in onRevert / clearSessionState)
	/** creature -> who analysed it and until when. */
	private static final Map<UUID, Mark> MARKS = new ConcurrentHashMap<>();
	/** player -> their storm. */
	private static final Map<UUID, Storm> STORMS = new ConcurrentHashMap<>();

	private record Mark(UUID owner, long until) {
	}

	private static final class Storm {
		final ResourceKey<Level> dim;
		final Vec3 centre;
		final long until;
		long next;
		@Nullable
		AlienProjectileEntity cloud;

		Storm(ResourceKey<Level> dim, Vec3 centre, long until, long next) {
			this.dim = dim;
			this.centre = centre;
			this.until = until;
			this.next = next;
		}
	}

	public static void register() {
		Aliens.register(new BrainstormKit());
		AlienProjectileEntity.onHit("brainstorm_ball", (proj, hit) -> {
			if (proj.level() instanceof ServerLevel level) {
				Vec3 at = hit.getLocation();
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 0.3, at.z, 24, 1.2, 0.6, 1.2, 0.2);
				OmnitrixSfx.play(level, at, BOOM, 0.8f, 1.2f);
			}
		});
		AlienProjectileEntity.trail("brainstorm_ball", ParticleTypes.ELECTRIC_SPARK);
	}

	public static void clearSessionState() {
		MARKS.clear();
		for (Storm s : STORMS.values()) {
			if (s.cloud != null) {
				s.cloud.discard();
			}
		}
		STORMS.clear();
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public AlienStats stats() {
		return AlienStats.builder().health(22).speed(0.9f).scale(0.9f).armor(6, 1).knockbackResist(0.2f).attack(3).build();
	}

	@Override
	protected List<AlienMove> declareMoves() {
		return List.of(AlienMove.tap(BOLT, AbilitySlot.SLOT_1, 20), AlienMove.shift(CHAIN_LIGHTNING, AbilitySlot.SLOT_1, 160),
				AlienMove.tap(PINCER, AbilitySlot.SLOT_2, 100), AlienMove.shift(BALL, AbilitySlot.SLOT_2, 160),
				AlienMove.tap(STATIC_HOP, AbilitySlot.SLOT_3, 40), AlienMove.shift(BOLT_DASH, AbilitySlot.SLOT_3, 120),
				AlienMove.tap(DISCHARGE, AbilitySlot.SLOT_4, 200), AlienMove.shift(MEGA_BOLT, AbilitySlot.SLOT_4, 300),
				AlienMove.tap(ANALYSIS, AbilitySlot.SLOT_5, 300), AlienMove.shift(FORCE_FIELD, AbilitySlot.SLOT_5, 500),
				AlienMove.tap(ELECTRIC_STORM, AbilitySlot.SLOT_6, 900), AlienMove.shift(STATIC_CAGE, AbilitySlot.SLOT_6, 240));
	}

	// ---------------------------------------------------------------- lifecycle

	@Override
	public void onRevert(ServerPlayer p) {
		dropStorm(p.getUUID());
		MARKS.values().removeIf(m -> m.owner().equals(p.getUUID()));
		OmnitrixState s = com.projecthero.mod.omnitrix.Omnitrix.state(p);
		if (s.kitFlags != 0) {
			OmnitrixState n = s.copy();
			n.kitFlags = 0;
			com.projecthero.mod.omnitrix.Omnitrix.save(p, n);
		}
	}

	private static void dropStorm(UUID id) {
		Storm s = STORMS.remove(id);
		if (s != null && s.cloud != null) {
			s.cloud.discard();
		}
	}

	/** Whether {@code p}'s storm is running (gametests). */
	public static boolean storming(ServerPlayer p) {
		return STORMS.containsKey(p.getUUID());
	}

	/** Whether {@code e} is analysed by {@code p} right now. */
	public static boolean marked(ServerPlayer p, LivingEntity e) {
		Mark m = MARKS.get(e.getUUID());
		return m != null && m.owner().equals(p.getUUID()) && m.until() > p.level().getGameTime();
	}

	@Override
	public void tick(ServerPlayer p, OmnitrixState s, long now) {
		ServerLevel level = level(p);
		// the Force Field: creatures shoved out, incoming projectiles turned back
		if (flag(p, F_FIELD)) {
			if (now >= timer(p, FIELD_END)) {
				setFlag(p, F_FIELD, false);
				setTimer(p, FIELD_END, 0);
				OmnitrixSfx.playOn(p, FIELD_DOWN, 0.8f, 1f, net.minecraft.sounds.SoundSource.PLAYERS);
			} else {
				Vec3 c = p.position().add(0, p.getBbHeight() * 0.5, 0);
				for (LivingEntity e : around(p, c, FIELD_RADIUS + 0.4)) {
					if (!AlienStatus.isBoss(e)) {
						shove(e, c, 0.45, 0.1);
					}
				}
				for (Projectile pr : level.getEntitiesOfClass(Projectile.class, new AABB(c, c).inflate(FIELD_RADIUS + 1.5),
						pr -> pr.getOwner() != p && !(pr instanceof AlienProjectileEntity))) {
					Vec3 v = pr.getDeltaMovement();
					Vec3 out = pr.position().subtract(c);
					if (v.dot(out) < 0) {
						pr.setDeltaMovement(v.scale(-0.4));
						pr.hurtMarked = true;
						level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pr.getX(), pr.getY(), pr.getZ(), 4, 0.1, 0.1, 0.1, 0.1);
					}
				}
			}
		}
		// the hop lands
		if (flag(p, F_HOP) && p.onGround() && now - timer(p, HOP_AT) > 4) {
			setFlag(p, F_HOP, false);
		}
		// the Mega Bolt fires when charged
		if (flag(p, F_CHARGE)) {
			if (now % 3 == 0) {
				Vec3 c = p.getEyePosition().add(0, 0.3, 0);
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 3, 0.25, 0.2, 0.25, 0.05);
			}
			if (now >= timer(p, MEGA_AT)) {
				setFlag(p, F_CHARGE, false);
				setTimer(p, MEGA_AT, 0);
				fireMega(p);
			}
		}
		// the storm
		Storm st = STORMS.get(p.getUUID());
		if (st != null) {
			if (st.dim != level.dimension() || now >= st.until) {
				dropStorm(p.getUUID());
			} else if (now >= st.next) {
				st.next = now + STORM_PULSE;
				stormPulse(p, st);
			}
		}
		if (!MARKS.isEmpty() && now % 40 == 0) {
			MARKS.values().removeIf(m -> m.until() <= now);
		}
	}

	// ---------------------------------------------------------------- keys

	@Override
	public void press(ServerPlayer p, AbilitySlot slot, boolean shift) {
		switch (slot) {
			case SLOT_1 -> {
				if (shift) {
					chainLightning(p);
				} else {
					lightningBolt(p);
				}
			}
			case SLOT_2 -> {
				if (shift) {
					ballLightning(p);
				} else {
					arcPincer(p);
				}
			}
			case SLOT_3 -> {
				if (shift) {
					boltDash(p);
				} else {
					staticHop(p);
				}
			}
			case SLOT_4 -> {
				if (shift) {
					megaBolt(p);
				} else {
					discharge(p);
				}
			}
			case SLOT_5 -> {
				if (shift) {
					forceField(p);
				} else {
					analyze(p);
				}
			}
			case SLOT_6 -> {
				if (shift) {
					staticCage(p);
				} else {
					electricStorm(p);
				}
			}
			default -> {
			}
		}
	}

	@Override
	public boolean keyActive(net.minecraft.world.entity.player.Player p, AbilitySlot slot) {
		return slot == AbilitySlot.SLOT_5 && flag(p, F_FIELD);
	}

	// ---------------------------------------------------------------- damage

	@Override
	public boolean allowDamage(ServerPlayer p, DamageSource source, float amount) {
		if (source.is(DamageTypes.LIGHTNING_BOLT)) {
			return false; // his own element
		}
		if (source.is(DamageTypeTags.IS_FALL) && flag(p, F_HOP)) {
			return false;
		}
		if (flag(p, F_FIELD) && source.is(DamageTypeTags.IS_PROJECTILE)) {
			return false; // the Force Field stops shots
		}
		return true;
	}

	@Override
	public float incomingDamage(ServerPlayer p, DamageSource source, float amount) {
		if (flag(p, F_FIELD) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return amount * FIELD_TAKEN;
		}
		return amount;
	}

	/** One of his electric hits: x1.25 on a creature he analysed, half while he stands in water. */
	public static boolean zap(ServerPlayer p, LivingEntity e, float amount) {
		float a = amount;
		if (marked(p, e)) {
			a *= MARK_FACTOR;
		}
		if (p.isInWaterOrBubble()) {
			a *= WATER_FACTOR;
		}
		boolean hit = hurt(p, e, a, false);
		if (hit && p.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), 6, 0.25, 0.3, 0.25, 0.12);
		}
		return hit;
	}

	// ---------------------------------------------------------------- the visual carriers

	/** A bolt drawn to {@code end}: from the owner's claws (from null), or from creature {@code from}. Lasts {@code life} ticks. */
	static void arc(ServerPlayer p, Vec3 end, @Nullable Entity from, int life, boolean mega) {
		AlienProjectileEntity a = AlienProjectileEntity.of(p, mega ? "brainstorm_mega" : "brainstorm_arc");
		a.moveTo(end.x, end.y, end.z, 0f, 0f);
		double len = from == null ? p.getEyePosition().distanceTo(end) : from.position().distanceTo(end);
		a.colour(from == null ? 0 : from.getId() + 1).size((float) Math.max(0.6, len / 3.0)).held(true).speed(0).life(life).damage(0f).spawn();
	}

	private static Vec3 centre(Entity e) {
		return e.position().add(0, e.getBbHeight() * 0.55, 0);
	}

	/** The block (or air) point the crosshair reaches within {@code range}. */
	private static Vec3 aimPoint(ServerPlayer p, double range) {
		Vec3 eye = p.getEyePosition();
		Vec3 end = eye.add(p.getLookAngle().scale(range));
		HitResult hit = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		return hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
	}

	// ---------------------------------------------------------------- moves (public for the gametests)

	/** R: a bolt to the creature under the crosshair, else to where it points. */
	public boolean lightningBolt(ServerPlayer p) {
		if (!use(p, BOLT)) {
			return false;
		}
		anim(p, ANIM_BOLT);
		LivingEntity t = lookTarget(p, BOLT_RANGE);
		if (t != null) {
			zap(p, t, BOLT_DAMAGE);
			arc(p, centre(t), null, 6, false);
		} else {
			arc(p, aimPoint(p, BOLT_RANGE), null, 6, false);
		}
		OmnitrixSfx.playOn(p, ZAP, 0.9f, 1f, net.minecraft.sounds.SoundSource.PLAYERS);
		return true;
	}

	/** Shift+R: the bolt jumps from creature to creature. Returns false if nothing was in the way (no cooldown). */
	public boolean chainLightning(ServerPlayer p) {
		LivingEntity first = lookTarget(p, BOLT_RANGE);
		if (first == null) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.brainstorm.no_target").withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		if (!use(p, CHAIN_LIGHTNING)) {
			return false;
		}
		anim(p, ANIM_CHAIN);
		List<LivingEntity> hit = chain(p, first);
		OmnitrixSfx.playOn(p, CHAIN, 1f, 1f, net.minecraft.sounds.SoundSource.PLAYERS);
		return !hit.isEmpty();
	}

	/** The chain from {@code first}: every creature struck, in order. */
	public static List<LivingEntity> chain(ServerPlayer p, LivingEntity first) {
		List<LivingEntity> out = new ArrayList<>();
		Set<UUID> seen = new HashSet<>();
		LivingEntity cur = first;
		zap(p, cur, CHAIN_FIRST);
		arc(p, centre(cur), null, 8, false);
		out.add(cur);
		seen.add(cur.getUUID());
		for (int i = 0; i < CHAIN_JUMPS; i++) {
			LivingEntity next = null;
			double best = Double.MAX_VALUE;
			for (LivingEntity e : around(p, cur.position(), CHAIN_RANGE)) {
				if (seen.contains(e.getUUID())) {
					continue;
				}
				double d = e.distanceToSqr(cur);
				if (d < best && d <= CHAIN_RANGE * CHAIN_RANGE) {
					best = d;
					next = e;
				}
			}
			if (next == null) {
				break;
			}
			zap(p, next, CHAIN_DAMAGE);
			arc(p, centre(next), cur, 8, false);
			out.add(next);
			seen.add(next.getUUID());
			cur = next;
		}
		return out;
	}

	/** G: a crackling claw snap at everything in front. */
	public boolean arcPincer(ServerPlayer p) {
		if (!use(p, PINCER)) {
			return false;
		}
		anim(p, ANIM_PINCER);
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		for (LivingEntity e : around(p, eye.add(look.scale(PINCER_RANGE * 0.5)), PINCER_RANGE)) {
			Vec3 to = centre(e).subtract(eye);
			if (to.length() <= PINCER_RANGE + e.getBbWidth() * 0.5 && to.normalize().dot(look) > 0.45) {
				zap(p, e, PINCER_DAMAGE);
				AlienStatus.stun(p, e, PINCER_STUN);
				shove(e, p.position(), 0.4, 0.2);
			}
		}
		OmnitrixSfx.playOn(p, SNAP, 0.9f, 1f, net.minecraft.sounds.SoundSource.PLAYERS);
		return true;
	}

	/** Shift+G: a slow crackling orb that bursts. */
	public boolean ballLightning(ServerPlayer p) {
		if (!use(p, BALL)) {
			return false;
		}
		anim(p, ANIM_ORB);
		AlienProjectileEntity.of(p, "brainstorm_ball").colour(0xFFE873).size(0.55f).damage(0f).blast(3.5f, BALL_DAMAGE).speed(0.55).life(80).spawn();
		OmnitrixSfx.playOn(p, ORB, 0.9f, 1f, net.minecraft.sounds.SoundSource.PLAYERS);
		return true;
	}

	/** X: a charged leap forward. */
	public boolean staticHop(ServerPlayer p) {
		if (!use(p, STATIC_HOP)) {
			return false;
		}
		anim(p, ANIM_HOP);
		Vec3 f = p.getLookAngle().multiply(1, 0, 1);
		f = f.lengthSqr() < 1.0e-4 ? Vec3.ZERO : f.normalize();
		p.setDeltaMovement(f.scale(1.15).add(0, 0.72, 0));
		p.hurtMarked = true;
		p.fallDistance = 0f;
		setFlag(p, F_HOP, true);
		setTimer(p, HOP_AT, p.level().getGameTime());
		level(p).sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 0.1, p.getZ(), 14, 0.4, 0.05, 0.4, 0.15);
		OmnitrixSfx.playOn(p, HOP, 0.9f, 1f, net.minecraft.sounds.SoundSource.PLAYERS);
		return true;
	}

	/** Shift+X: he crackles 8 blocks ahead as a bolt; whatever he passes through takes 6. */
	public boolean boltDash(ServerPlayer p) {
		if (!use(p, BOLT_DASH)) {
			return false;
		}
		anim(p, ANIM_DASH);
		Vec3 from = p.position();
		Vec3 dir = p.getLookAngle().multiply(1, 0, 1);
		dir = dir.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : dir.normalize();
		// step forward until a wall stops him (keeps his feet on the level he started)
		Vec3 to = from;
		for (double d = 0.5; d <= DASH_DISTANCE; d += 0.5) {
			Vec3 at = from.add(dir.scale(d));
			if (!p.level().noCollision(p, p.getBoundingBox().move(at.subtract(from)))) {
				break;
			}
			to = at;
		}
		Set<UUID> struck = new HashSet<>();
		for (double d = 0; d <= from.distanceTo(to) + 0.01; d += 0.75) {
			Vec3 at = from.add(dir.scale(d));
			for (LivingEntity e : around(p, at.add(0, 0.9, 0), 1.4)) {
				if (struck.add(e.getUUID())) {
					zap(p, e, DASH_DAMAGE);
				}
			}
		}
		p.teleportTo(to.x, to.y, to.z);
		p.fallDistance = 0f;
		arc(p, from.add(0, 0.9, 0), null, 8, false);
		OmnitrixSfx.play(level(p), to, ZAP, 1.1f, 0.9f);
		return true;
	}

	/** Z: every creature within 5 is struck and thrown back. */
	public boolean discharge(ServerPlayer p) {
		if (!use(p, DISCHARGE)) {
			return false;
		}
		anim(p, ANIM_DISCHARGE);
		Vec3 c = p.position().add(0, 1, 0);
		for (LivingEntity e : around(p, c, DISCHARGE_RADIUS)) {
			zap(p, e, DISCHARGE_DAMAGE);
			shove(e, p.position(), 1.1, 0.45);
			arc(p, centre(e), null, 6, false);
		}
		level(p).sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 40, 2.0, 0.6, 2.0, 0.3);
		OmnitrixSfx.playOn(p, BOOM, 1.1f, 1f, net.minecraft.sounds.SoundSource.PLAYERS);
		return true;
	}

	/** Shift+Z: a 1 s charge, then a beam through everything along the crosshair. */
	public boolean megaBolt(ServerPlayer p) {
		if (flag(p, F_CHARGE) || !use(p, MEGA_BOLT)) {
			return false;
		}
		anim(p, ANIM_MEGA);
		setFlag(p, F_CHARGE, true);
		setTimer(p, MEGA_AT, p.level().getGameTime() + MEGA_CHARGE);
		OmnitrixSfx.playOn(p, CHARGE, 1f, 1f, net.minecraft.sounds.SoundSource.PLAYERS);
		return true;
	}

	/** The Mega Bolt's beam (public: the gametests fire it without waiting). Returns how many it struck. */
	public int fireMega(ServerPlayer p) {
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		Vec3 end = aimPoint(p, MEGA_RANGE);
		double len = eye.distanceTo(end);
		int n = 0;
		for (LivingEntity e : around(p, eye.add(look.scale(len / 2)), len / 2 + 2)) {
			Vec3 c = centre(e);
			double t = Mth.clamp(c.subtract(eye).dot(look), 0, len);
			if (c.distanceTo(eye.add(look.scale(t))) <= 1.0 + e.getBbWidth() * 0.5) {
				zap(p, e, MEGA_DAMAGE);
				n++;
			}
		}
		arc(p, end, null, 10, true);
		OmnitrixSfx.playOn(p, BOOM, 1.4f, 0.85f, net.minecraft.sounds.SoundSource.PLAYERS);
		return n;
	}

	/** V: reads the creature under the crosshair -- its numbers in chat, outlined, +25% from his moves for 10 s. */
	public boolean analyze(ServerPlayer p) {
		LivingEntity t = lookTarget(p, ANALYZE_RANGE);
		if (t == null) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.brainstorm.no_target").withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		if (!use(p, ANALYSIS)) {
			return false;
		}
		anim(p, ANIM_ANALYZE);
		long now = p.level().getGameTime();
		MARKS.put(t.getUUID(), new Mark(p.getUUID(), now + MARK_TICKS));
		t.addEffect(new MobEffectInstance(MobEffects.GLOWING, MARK_TICKS, 0, false, false), p);
		QuirkThreat.add(t, p, 2f);
		p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.brainstorm.analysis", t.getDisplayName(),
				String.format(java.util.Locale.ROOT, "%.0f", t.getHealth()), String.format(java.util.Locale.ROOT, "%.0f", t.getMaxHealth()),
				t.getArmorValue(), weakness(t)).withStyle(ChatFormatting.GOLD), false);
		OmnitrixSfx.playOn(p, THINK, 0.9f, 1f, net.minecraft.sounds.SoundSource.PLAYERS);
		return true;
	}

	private static Component weakness(LivingEntity t) {
		String k;
		if (t.isInvertedHealAndHarm()) {
			k = "undead";
		} else if (t.getType().is(net.minecraft.tags.EntityTypeTags.ARTHROPOD)) {
			k = "arthropod";
		} else if (t.fireImmune()) {
			k = "fireproof";
		} else if (t.getArmorValue() >= 10) {
			k = "armoured";
		} else {
			k = "none";
		}
		return Component.translatable("message.projecthero.omnitrix.brainstorm.weak." + k);
	}

	/** Shift+V: a 6 s force-field bubble. */
	public boolean forceField(ServerPlayer p) {
		if (!use(p, FORCE_FIELD)) {
			return false;
		}
		anim(p, ANIM_FIELD);
		setFlag(p, F_FIELD, true);
		setTimer(p, FIELD_END, p.level().getGameTime() + FIELD_TICKS);
		OmnitrixSfx.playOn(p, FIELD, 1f, 1f, net.minecraft.sounds.SoundSource.PLAYERS);
		return true;
	}

	/** C (ULTIMATE): a storm cloud over the crosshair (or over him), striking every foe under it each half second. */
	public boolean electricStorm(ServerPlayer p) {
		if (!use(p, ELECTRIC_STORM)) {
			return false;
		}
		anim(p, ANIM_STORM);
		LivingEntity t = lookTarget(p, STORM_RANGE);
		Vec3 c = t != null ? t.position() : aimPoint(p, STORM_RANGE);
		if (c.distanceTo(p.position()) > STORM_RANGE) {
			c = p.position();
		}
		long now = p.level().getGameTime();
		dropStorm(p.getUUID());
		Storm st = new Storm(p.level().dimension(), c, now + STORM_TICKS, now + 6);
		AlienProjectileEntity cloud = AlienProjectileEntity.of(p, "brainstorm_cloud");
		cloud.moveTo(c.x, c.y + 7, c.z, 0f, 0f);
		cloud.colour(0).size((float) STORM_RADIUS).held(true).speed(0).life(STORM_TICKS + 10).damage(0f).spawn();
		st.cloud = cloud;
		STORMS.put(p.getUUID(), st);
		OmnitrixSfx.playOn(p, STORM, 1.2f, 1f, net.minecraft.sounds.SoundSource.PLAYERS);
		return true;
	}

	/** One storm pulse: a strike on every foe under the cloud (or a random strike on the ground if none). */
	private void stormPulse(ServerPlayer p, Storm st) {
		ServerLevel level = level(p);
		List<LivingEntity> under = new ArrayList<>();
		for (LivingEntity e : around(p, st.centre, STORM_RADIUS)) {
			if (Math.abs(e.getY() - st.centre.y) < 8) {
				under.add(e);
			}
		}
		for (LivingEntity e : under) {
			zap(p, e, STORM_DAMAGE);
			strike(p, e.position());
		}
		if (under.isEmpty()) {
			double a = level.random.nextDouble() * Mth.TWO_PI, r = level.random.nextDouble() * STORM_RADIUS * 0.8;
			strike(p, st.centre.add(Math.cos(a) * r, 0, Math.sin(a) * r));
		}
		OmnitrixSfx.play(level, st.centre, THUNDER, 0.7f, 1f);
	}

	/** Storm pulses for the gametests (pump them without the server ticking the mock). */
	public int stormPulseNow(ServerPlayer p) {
		Storm st = STORMS.get(p.getUUID());
		if (st == null) {
			return 0;
		}
		int before = around(p, st.centre, STORM_RADIUS).size();
		stormPulse(p, st);
		return before;
	}

	private static void strike(ServerPlayer p, Vec3 at) {
		AlienProjectileEntity s = AlienProjectileEntity.of(p, "brainstorm_strike");
		s.moveTo(at.x, at.y, at.z, 0f, 0f);
		s.colour(0).size(3f).held(true).speed(0).life(6).damage(0f).spawn();
		level(p).sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 0.2, at.z, 8, 0.4, 0.1, 0.4, 0.2);
	}

	/** Shift+C: a cage of lightning round the creature under the crosshair: 12 and stunned 3 s. */
	public boolean staticCage(ServerPlayer p) {
		LivingEntity t = lookTarget(p, CAGE_RANGE);
		if (t == null) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.brainstorm.no_target").withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		if (!use(p, STATIC_CAGE)) {
			return false;
		}
		anim(p, ANIM_CAGE);
		zap(p, t, CAGE_DAMAGE);
		AlienStatus.stun(p, t, CAGE_TICKS);
		AlienProjectileEntity c = AlienProjectileEntity.of(p, "brainstorm_cage");
		c.moveTo(t.getX(), t.getY(), t.getZ(), 0f, 0f);
		c.colour(t.getId() + 1).size(Math.max(0.6f, t.getBbHeight() * 0.5f)).held(true).speed(0)
				.life(AlienStatus.isBoss(t) ? CAGE_TICKS / 4 : CAGE_TICKS).damage(0f).spawn();
		arc(p, centre(t), null, 6, false);
		OmnitrixSfx.playOn(p, CHAIN, 1f, 0.9f, net.minecraft.sounds.SoundSource.PLAYERS);
		return true;
	}
}
