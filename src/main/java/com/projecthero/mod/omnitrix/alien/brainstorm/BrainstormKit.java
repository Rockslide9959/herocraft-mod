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
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.OmnitrixSfx;
import com.projecthero.mod.omnitrix.alien.AlienKit;
import com.projecthero.mod.omnitrix.alien.AlienMove;
import com.projecthero.mod.omnitrix.alien.AlienStats;
import com.projecthero.mod.omnitrix.alien.AlienStatus;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.data.OmnitrixState;
import com.projecthero.mod.omnitrix.entity.AlienProjectileEntity;
import com.projecthero.mod.quirk.QuirkThreat;
import com.projecthero.mod.quirk.fx.QuirkCamera;
import com.projecthero.mod.quirk.fx.QuirkFx;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2: Brainstorm, the Cerebrocrustacean (Recalibrated Omnitrix). v0.17.2 revamp (docs/OMNITRIX_REVAMP_BRAINSTORM.md; user:
 * "can you work on revamps for the rest of the alien force aliens please, look at their powers and abilities in the show and do
 * research and make them good please"): a crab-shelled genius who opens his skull and thinks lightning. His combo engine is
 * <b>Static</b>: every electric hit charges the creature, and the third charge Overloads it. No Ultimate Brainstorm.
 *
 * v0.17.2 damage pass (user: "make the damage of all of ben 10s alien stronger"; docs/OMNITRIX_DAMAGE_PASS_ALIENFORCE.md): the
 * damage numbers in the table below are the pre-pass ones (~x1.6 now) -- the constants are the source of truth.
 *
 * <pre>
 *   R          Lightning Bolt   a bolt from his brain at the crosshair (20): 7, 1 s
 *   Shift+R    Chain Lightning  14, then jumps to 4 more within 7: 12 each, 8 s
 *   G          Arc Pincer       a shocking claw snap (cone 3.5): 13 + 1 s stun, 5 s
 *   Shift+G    Ball Lightning   a slow orb that zaps every foe within 3 for 4 each half second, then bursts (r 3.5): 16, 8 s
 *   X          Static Hop       tap: a charged leap (no fall damage), 2 s
 *   Hold X     Field Lift       he rides a disc of force field: hover and glide where he looks, up to 3 s, 6 s
 *   Shift+X    Bolt Dash        8 blocks as a bolt: 6 to all he passes, 6 s
 *   Z          Discharge        r 5: 17 + thrown back (in water: r 8, and it shorts him for 3), 10 s
 *   Shift+Z    Electric Storm   ULTIMATE: a 6 s storm cloud (r 8): a strike on every foe under it each 0.5 s (4), then a 22 finale
 *                               (up to 70), 45 s
 *   C          Static Cage      12 + a 3 s lightning cage (stun), 12 s
 *   Shift+C    Mega Bolt        1 s charge with the skull wide open, then a piercing beam (24): 19, 15 s
 *   V          Genius Analysis  reads a creature (24): +25% from his moves for 10 s, and it Overloads at 2 Static, 15 s
 *   Shift+V    Force Field      6 s bubble: projectiles bounce, -60%, creatures shoved out; allies inside get Resistance II, 25 s
 *   Attack     Shocking Pincers his punch zaps +2 and charges Static
 *   Passives   lightning immune; Static (3 charges = Overload: +6 and a 1 s stun); armour 6
 *   Weak       water (half damage; Discharge shorts him); slow shell (0.9x); sonic headache (sonic x1.5, breaks his charge / lift)
 * </pre>
 *
 * <p>The bolts are drawn by the client from short-lived held {@link AlienProjectileEntity} carriers: {@code brainstorm_arc} sits at
 * the END of a bolt and its colour says where it starts (0 = the owner's open skull, n = entity id n - 1), {@code brainstorm_strike}
 * is a bolt from the sky down to it, {@code brainstorm_cloud} the storm cloud (size = radius), {@code brainstorm_cage} the cage round
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
	// v0.17.2 revamp
	public static final OmnitrixSfx.Cue OVERLOAD = OmnitrixSfx.cue("brainstorm_overload")
			.layer(SoundEvents.LIGHTNING_BOLT_IMPACT, 0.25f, 1.8f)
			.layer(SoundEvents.AMETHYST_CLUSTER_BREAK, 0.35f, 1.4f)
			.gap(3);
	public static final OmnitrixSfx.Cue LIFT = OmnitrixSfx.cue("brainstorm_lift")
			.layer(SoundEvents.BEACON_AMBIENT, 0.35f, 1.6f)
			.layer(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.2f, 1.9f);
	public static final OmnitrixSfx.Cue SHORT = OmnitrixSfx.cue("brainstorm_short")
			.layer(SoundEvents.FIRE_EXTINGUISH, 0.35f, 1.4f)
			.layer(SoundEvents.FIRECHARGE_USE, 0.2f, 0.7f);

	// ---------------------------------------------------------------- poses, flags, moves
	public static final int ANIM_BOLT = 1, ANIM_CHAIN = 2, ANIM_PINCER = 3, ANIM_ORB = 4, ANIM_HOP = 5, ANIM_DASH = 6, ANIM_DISCHARGE = 7,
			ANIM_MEGA = 8, ANIM_ANALYZE = 9, ANIM_FIELD = 10, ANIM_STORM = 11, ANIM_CAGE = 12, ANIM_LIFT = 13, ANIM_PUNCH = 14;

	/** The Force Field is up. */
	public static final int F_FIELD = 1;
	/** A hop / dash / lift in flight (no fall damage until he lands). */
	public static final int F_HOP = 2;
	/** The Mega Bolt is charging. */
	public static final int F_CHARGE = 4;
	/** v0.17.2 revamp: riding the Field Lift disc (Hold X). */
	public static final int F_LIFT = 8;
	/** v0.17.2 revamp: his storm hangs overhead (the client darkens the cloud's shadow under it). */
	public static final int F_STORM = 16;

	public static final String BOLT = "lightning_bolt", CHAIN_LIGHTNING = "chain_lightning", PINCER = "arc_pincer", BALL = "ball_lightning",
			STATIC_HOP = "static_hop", FIELD_LIFT = "field_lift", BOLT_DASH = "bolt_dash", DISCHARGE = "discharge", MEGA_BOLT = "mega_bolt",
			ANALYSIS = "genius_analysis", FORCE_FIELD = "force_field", ELECTRIC_STORM = "electric_storm", STATIC_CAGE = "static_cage",
			SHOCK_PINCERS = "shocking_pincers";

	/** Kit timers: the Force Field's end, the Mega Bolt's release, the hop's start, the lift's start. */
	public static final String FIELD_END = "field_end", MEGA_AT = "mega_at", HOP_AT = "hop_at", LIFT_AT = "lift_at";

	// v0.17.2 (user: "can you make the damage of all of ben 10s alien stronger, i want each alien to feel like its own entire ability and i want them to be strong aswell"): ~x1.6 damage pass (docs/OMNITRIX_DAMAGE_PASS_ALIENFORCE.md)
	public static final float BOLT_DAMAGE = 11f, CHAIN_FIRST = 24f, CHAIN_DAMAGE = 20f, PINCER_DAMAGE = 22f, BALL_DAMAGE = 26f, BALL_ZAP = 7f,
			DASH_DAMAGE = 10f, DISCHARGE_DAMAGE = 28f, MEGA_DAMAGE = 32f, STORM_DAMAGE = 7f, STORM_FINALE = 26f, CAGE_DAMAGE = 20f, PUNCH_BONUS = 4f,
			OVERLOAD_DAMAGE = 10f, SHORT_SELF = 3f;
	public static final double BOLT_RANGE = 20, CHAIN_RANGE = 7, PINCER_RANGE = 3.5, DISCHARGE_RADIUS = 5, DISCHARGE_WATER_RADIUS = 8,
			MEGA_RANGE = 24, ANALYZE_RANGE = 24, STORM_RADIUS = 8, STORM_RANGE = 24, CAGE_RANGE = 16, DASH_DISTANCE = 8, FIELD_RADIUS = 2.2,
			BALL_ZAP_RANGE = 3.0;
	public static final int CHAIN_JUMPS = 4, PINCER_STUN = 20, FIELD_TICKS = 120, MEGA_CHARGE = 20, STORM_TICKS = 120, STORM_PULSE = 10,
			MARK_TICKS = 200, CAGE_TICKS = 60, LIFT_MAX = 60, BALL_PULSE = 10;
	/** Static: charges on one creature, how long they last, the stun an Overload gives. */
	public static final int STATIC_MAX = 3, STATIC_MARKED = 2, STATIC_TICKS = 120, OVERLOAD_STUN = 20;
	/** Genius Analysis: a read creature takes this much more from his moves. */
	public static final float MARK_FACTOR = 1.25f;
	/** The Force Field keeps this share of damage that gets through. */
	public static final float FIELD_TAKEN = 0.4f;
	/** In water his electricity shorts out: his moves deal this share. */
	public static final float WATER_FACTOR = 0.5f;
	/** Sonic damage gives him a headache. */
	public static final float SONIC_FACTOR = 1.5f;

	// ---------------------------------------------------------------- per-player state (cleared in onRevert / clearSessionState)
	/** creature -> who analysed it and until when. */
	private static final Map<UUID, Mark> MARKS = new ConcurrentHashMap<>();
	/** player -> their storm. */
	private static final Map<UUID, Storm> STORMS = new ConcurrentHashMap<>();
	/** v0.17.2 revamp: creature -> its Static charges (and whose). */
	private static final Map<UUID, Charge> STATIC = new ConcurrentHashMap<>();
	/** v0.17.2 revamp: once per game tick (the storm / ball pulses must not double up if he is ticked twice -- RipjawsKit.tick). */
	private static final Map<UUID, Long> LAST_TICK = new ConcurrentHashMap<>();

	private record Mark(UUID owner, long until) {
	}

	private record Charge(UUID owner, int n, long until) {
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
				QuirkFx.ring(level, at).colours(0xFFFFFF, 0xFFE060).size(3.5f).thickness(0.35f).life(10).spawn();
				OmnitrixSfx.play(level, at, BOOM, 0.8f, 1.2f);
			}
		});
	}

	public static void clearSessionState() {
		MARKS.clear();
		for (Storm s : STORMS.values()) {
			if (s.cloud != null) {
				s.cloud.discard();
			}
		}
		STORMS.clear();
		STATIC.clear();
		LAST_TICK.clear();
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public AlienStats stats() {
		return AlienStats.builder().health(22).speed(0.9f).scale(0.9f).armor(12, 1).knockbackResist(0.2f).attack(4).build(); // v0.17.2 (user set the unarmed melee bonuses: "Swampfire +9, Echo Echo +4, Humungousaur +15, Jetray +5, Big Chill +6, Chromastone +7, Brainstorm +4, Spidermonkey +5, Goop +5")
	}

	@Override
	protected List<AlienMove> declareMoves() {
		return List.of(AlienMove.tap(BOLT, AbilitySlot.SLOT_1, 20), AlienMove.shift(CHAIN_LIGHTNING, AbilitySlot.SLOT_1, 160),
				AlienMove.tap(PINCER, AbilitySlot.SLOT_2, 100), AlienMove.shift(BALL, AbilitySlot.SLOT_2, 160),
				AlienMove.tap(STATIC_HOP, AbilitySlot.SLOT_3, 40), AlienMove.hold(FIELD_LIFT, AbilitySlot.SLOT_3, 120),
				AlienMove.shift(BOLT_DASH, AbilitySlot.SLOT_3, 120),
				AlienMove.tap(DISCHARGE, AbilitySlot.SLOT_4, 200), AlienMove.shift(ELECTRIC_STORM, AbilitySlot.SLOT_4, 900),
				AlienMove.tap(ANALYSIS, AbilitySlot.SLOT_5, 300), AlienMove.shift(FORCE_FIELD, AbilitySlot.SLOT_5, 500),
				AlienMove.tap(STATIC_CAGE, AbilitySlot.SLOT_6, 240), AlienMove.shift(MEGA_BOLT, AbilitySlot.SLOT_6, 300),
				AlienMove.vanilla(SHOCK_PINCERS, "Attack", 0));
	}

	// ---------------------------------------------------------------- lifecycle

	@Override
	public void onRevert(ServerPlayer p) {
		UUID id = p.getUUID();
		dropStorm(id);
		MARKS.values().removeIf(m -> m.owner().equals(id));
		STATIC.values().removeIf(c -> c.owner().equals(id));
		LAST_TICK.remove(id);
		OmnitrixState s = Omnitrix.state(p);
		OmnitrixState n = s.copy();
		n.kitFlags = 0;
		for (String k : new String[] { FIELD_END, MEGA_AT, HOP_AT, LIFT_AT }) {
			n.timers.remove(key(k));
		}
		Omnitrix.save(p, n);
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

	/** The Static charges {@code p} has on {@code e} right now (0..2; an Overload empties them). */
	public static int charges(ServerPlayer p, LivingEntity e) {
		Charge c = STATIC.get(e.getUUID());
		return c == null || !c.owner().equals(p.getUUID()) || c.until() <= p.level().getGameTime() ? 0 : c.n();
	}

	/** Anything of {@code p}'s left in the kit's maps (gametests: turning back leaves nothing). */
	public static int leftovers(ServerPlayer p) {
		UUID id = p.getUUID();
		int n = (STORMS.containsKey(id) ? 1 : 0) + (LAST_TICK.containsKey(id) ? 1 : 0);
		for (Mark m : MARKS.values()) {
			n += m.owner().equals(id) ? 1 : 0;
		}
		for (Charge c : STATIC.values()) {
			n += c.owner().equals(id) ? 1 : 0;
		}
		return n;
	}

	@Override
	public void tick(ServerPlayer p, OmnitrixState s, long now) {
		// v0.17.2 revamp: once per game tick (RipjawsKit.tick)
		Long last = LAST_TICK.put(p.getUUID(), now);
		if (last != null && last == now) {
			return;
		}
		ServerLevel level = level(p);
		// the Force Field: creatures shoved out, incoming projectiles turned back, allies inside sheltered
		if (flag(p, F_FIELD)) {
			if (now >= timer(p, FIELD_END)) {
				setFlag(p, F_FIELD, false);
				setTimer(p, FIELD_END, 0);
				OmnitrixSfx.playOn(p, FIELD_DOWN, 0.8f, 1f, SoundSource.PLAYERS);
			} else {
				tickField(p, level, now);
			}
		}
		// the hop / lift lands
		if (flag(p, F_HOP) && !flag(p, F_LIFT) && p.onGround() && now - timer(p, HOP_AT) > 4) {
			setFlag(p, F_HOP, false);
		}
		if (flag(p, F_LIFT)) {
			tickLift(p, now);
		}
		// the Mega Bolt fires when charged
		if (flag(p, F_CHARGE) && now >= timer(p, MEGA_AT)) {
			setFlag(p, F_CHARGE, false);
			setTimer(p, MEGA_AT, 0);
			fireMega(p);
		}
		// the storm
		Storm st = STORMS.get(p.getUUID());
		if (st != null) {
			if (st.dim != level.dimension()) {
				dropStorm(p.getUUID());
				setFlag(p, F_STORM, false);
			} else if (now >= st.until) {
				stormFinale(p, st);
				dropStorm(p.getUUID());
				setFlag(p, F_STORM, false);
			} else if (now >= st.next) {
				st.next = now + STORM_PULSE;
				stormPulse(p, st);
			}
		}
		// Ball Lightning arcs at what it passes
		if (now % BALL_PULSE == 0) {
			ballPulse(p, level);
		}
		if (now % 40 == 0) {
			if (!MARKS.isEmpty()) {
				MARKS.values().removeIf(m -> m.until() <= now);
			}
			if (!STATIC.isEmpty()) {
				STATIC.values().removeIf(c -> c.until() <= now);
			}
		}
	}

	private void tickField(ServerPlayer p, ServerLevel level, long now) {
		Vec3 c = p.position().add(0, p.getBbHeight() * 0.5, 0);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(FIELD_RADIUS + 0.6))) {
			if (e == p || !e.isAlive()) {
				continue;
			}
			if (ally(p, e)) {
				// v0.17.2 revamp (he shields his friends in the show): allies inside the bubble are sheltered
				if (now % 10 == 0) {
					e.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 15, 1, false, false, true), p);
				}
			} else if (!AlienStatus.isBoss(e)) {
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
			}
		}
	}

	/** A squad ally or one of his own pets. */
	private static boolean ally(ServerPlayer p, LivingEntity e) {
		return com.projecthero.mod.squad.Squads.areAllies(p, e) || (e instanceof OwnableEntity o && p.getUUID().equals(o.getOwnerUUID()));
	}

	// ---------------------------------------------------------------- keys

	@Override
	public boolean holdable(AbilitySlot slot) {
		return slot == AbilitySlot.SLOT_3; // tap X = Static Hop, hold X = Field Lift
	}

	@Override
	public void press(ServerPlayer p, AbilitySlot slot, boolean shift) {
		if (flag(p, F_CHARGE)) {
			return; // the skull is wide open, focusing
		}
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
					electricStorm(p);
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
					megaBolt(p);
				} else {
					staticCage(p);
				}
			}
			default -> {
			}
		}
	}

	@Override
	public void holdStart(ServerPlayer p, AbilitySlot slot) {
		if (slot == AbilitySlot.SLOT_3 && !flag(p, F_CHARGE)) {
			fieldLift(p);
		}
	}

	@Override
	public void holdEnd(ServerPlayer p, AbilitySlot slot, int heldTicks) {
		if (slot == AbilitySlot.SLOT_3) {
			endLift(p);
		}
	}

	@Override
	public boolean keyActive(Player p, AbilitySlot slot) {
		return switch (slot) {
			case SLOT_5 -> flag(p, F_FIELD);
			case SLOT_3 -> flag(p, F_LIFT);
			case SLOT_4 -> flag(p, F_STORM);
			case SLOT_6 -> flag(p, F_CHARGE);
			default -> false;
		};
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
		float a = amount;
		if (sonic(source)) {
			// v0.17.2 revamp: a sonic headache -- x1.5, and his focus breaks (the Mega Bolt's charge, the lift)
			a *= SONIC_FACTOR;
			if (flag(p, F_CHARGE)) {
				setFlag(p, F_CHARGE, false);
				setTimer(p, MEGA_AT, 0);
			}
			if (flag(p, F_LIFT)) {
				endLift(p);
			}
		}
		if (flag(p, F_FIELD) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			a *= FIELD_TAKEN;
		}
		return a;
	}

	/** Sonic damage: a warden's boom, or anything an Echo Echo throws at him. */
	public static boolean sonic(DamageSource source) {
		if (source.is(DamageTypes.SONIC_BOOM)) {
			return true;
		}
		return source.getEntity() instanceof Player pl && Omnitrix.hasPower(pl) && Aliens.ECHO_ECHO.id().equals(Omnitrix.active(pl));
	}

	/** One of his electric hits that charges Static (see {@link #zap(ServerPlayer, LivingEntity, float, boolean)}). */
	public static boolean zap(ServerPlayer p, LivingEntity e, float amount) {
		return zap(p, e, amount, true);
	}

	/**
	 * One of his electric hits: x1.25 on a creature he analysed, half while he stands in water. With {@code charge}, it adds a Static
	 * charge; the third (the second on an analysed creature) Overloads it -- +6 on this hit and a 1 s stun (a boss a quarter).
	 */
	public static boolean zap(ServerPlayer p, LivingEntity e, float amount, boolean charge) {
		float a = amount;
		boolean overload = false;
		if (charge) {
			long now = p.level().getGameTime();
			int n = charges(p, e) + 1;
			if (n >= (marked(p, e) ? STATIC_MARKED : STATIC_MAX)) {
				overload = true;
				STATIC.remove(e.getUUID());
				a += OVERLOAD_DAMAGE;
			} else {
				STATIC.put(e.getUUID(), new Charge(p.getUUID(), n, now + STATIC_TICKS));
			}
		}
		if (marked(p, e)) {
			a *= MARK_FACTOR;
		}
		if (p.isInWaterOrBubble()) {
			a *= WATER_FACTOR;
		}
		boolean hit = hurt(p, e, a, false);
		if (hit && overload && p.level() instanceof ServerLevel level) {
			AlienStatus.stun(p, e, OVERLOAD_STUN);
			Vec3 c = centre(e);
			QuirkFx.ring(level, c).flags(QuirkFx.F_VERTICAL).vec(c.subtract(p.getEyePosition())).colours(0xFFFFFF, 0xFFE060)
					.size(Math.max(1.4f, e.getBbHeight() * 0.9f)).thickness(0.25f).life(8).spawn();
			OmnitrixSfx.play(level, c, OVERLOAD, 0.9f, 1f);
		}
		return hit;
	}

	// ---------------------------------------------------------------- the visual carriers

	/** A bolt drawn to {@code end}: from the owner's open skull (from null), or from creature {@code from}. Lasts {@code life} ticks. */
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

	private static void say(ServerPlayer p, String key) {
		p.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.GRAY), true);
	}

	// ---------------------------------------------------------------- R / Shift+R

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
		OmnitrixSfx.playOn(p, ZAP, 0.9f, 1f, SoundSource.PLAYERS);
		return true;
	}

	/** Shift+R: the bolt jumps from creature to creature. Returns false if nothing was in the way (no cooldown). */
	public boolean chainLightning(ServerPlayer p) {
		LivingEntity first = lookTarget(p, BOLT_RANGE);
		if (first == null) {
			say(p, "message.projecthero.omnitrix.brainstorm.no_target");
			return false;
		}
		if (!use(p, CHAIN_LIGHTNING)) {
			return false;
		}
		anim(p, ANIM_CHAIN);
		List<LivingEntity> hit = chain(p, first);
		OmnitrixSfx.playOn(p, CHAIN, 1f, 1f, SoundSource.PLAYERS);
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

	// ---------------------------------------------------------------- G / Shift+G

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
		QuirkFx.cone(level(p), eye.add(look.scale(0.6)).add(0, -0.3, 0), look).colours(0xFFFFFF, 0xFFE060).size(1.6f).length((float) PINCER_RANGE)
				.life(6).spawn();
		OmnitrixSfx.playOn(p, SNAP, 0.9f, 1f, SoundSource.PLAYERS);
		return true;
	}

	/** Shift+G: a slow crackling orb that zaps what it passes, then bursts. */
	public boolean ballLightning(ServerPlayer p) {
		if (!use(p, BALL)) {
			return false;
		}
		anim(p, ANIM_ORB);
		AlienProjectileEntity.of(p, "brainstorm_ball").colour(0xFFE873).size(0.55f).damage(0f).blast(3.5f, BALL_DAMAGE).speed(0.4).life(80).spawn();
		OmnitrixSfx.playOn(p, ORB, 0.9f, 1f, SoundSource.PLAYERS);
		return true;
	}

	/** v0.17.2 revamp: each of his orbs in flight arcs at every foe within 3 (4 each, Static). Returns how many it struck (tests). */
	public int ballPulse(ServerPlayer p, ServerLevel level) {
		int n = 0;
		for (AlienProjectileEntity ball : level.getEntitiesOfClass(AlienProjectileEntity.class, p.getBoundingBox().inflate(48),
				b -> b.getOwner() == p && "brainstorm_ball".equals(b.kind()) && b.isAlive())) {
			Vec3 c = ball.position().add(0, ball.getBbHeight() * 0.5, 0);
			for (LivingEntity e : around(p, c, BALL_ZAP_RANGE)) {
				if (zap(p, e, BALL_ZAP)) {
					n++;
				}
				arc(p, centre(e), ball, 5, false);
			}
		}
		return n;
	}

	// ---------------------------------------------------------------- X / Hold X / Shift+X

	/** X (tap): a charged leap forward. */
	public boolean staticHop(ServerPlayer p) {
		if (flag(p, F_LIFT) || !use(p, STATIC_HOP)) {
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
		QuirkFx.ring(level(p), p.position().add(0, 0.05, 0)).colours(0xFFFFFF, 0xFFE060).size(2.2f).thickness(0.25f).life(8).ground().spawn();
		OmnitrixSfx.playOn(p, HOP, 0.9f, 1f, SoundSource.PLAYERS);
		return true;
	}

	/**
	 * v0.17.2 revamp, Hold X: Field Lift -- he rides a disc of force field (the show's electric levitation): he hovers, rises when he
	 * looks up, sinks when he looks down and glides where he looks, for up to 3 s. No fall damage until he lands.
	 */
	public boolean fieldLift(ServerPlayer p) {
		if (flag(p, F_LIFT) || !use(p, FIELD_LIFT)) {
			return false;
		}
		anim(p, ANIM_LIFT);
		long now = p.level().getGameTime();
		OmnitrixState n = Omnitrix.state(p).copy();
		n.kitFlags |= F_LIFT | F_HOP;
		n.timers.put(key(LIFT_AT), now);
		n.timers.put(key(HOP_AT), now);
		Omnitrix.save(p, n);
		if (p.onGround()) {
			p.setDeltaMovement(p.getDeltaMovement().add(0, 0.45, 0));
		}
		p.hurtMarked = true;
		OmnitrixSfx.playOn(p, LIFT, 0.9f, 1f, SoundSource.PLAYERS);
		return true;
	}

	/** Whether he is riding the lift disc (gametests). */
	public boolean lifting(Player p) {
		return flag(p, F_LIFT);
	}

	private void tickLift(ServerPlayer p, long now) {
		long held = now - timer(p, LIFT_AT);
		if (held >= LIFT_MAX) {
			endLift(p);
			return;
		}
		Vec3 look = p.getLookAngle();
		Vec3 v = p.getDeltaMovement();
		double y = look.y > 0.35 ? 0.16 : look.y < -0.35 ? -0.14 : 0.0;
		Vec3 flat = new Vec3(look.x, 0, look.z);
		flat = flat.lengthSqr() < 1.0e-4 ? Vec3.ZERO : flat.normalize();
		Vec3 h = new Vec3(v.x, 0, v.z).scale(0.85).add(flat.scale(0.07));
		if (h.length() > 0.55) {
			h = h.normalize().scale(0.55);
		}
		p.setDeltaMovement(h.x, Mth.lerp(0.35, v.y, y), h.z);
		p.hurtMarked = true;
		p.fallDistance = 0f;
		if (held % 20 == 10) {
			OmnitrixSfx.playOn(p, LIFT, 0.4f, 1.2f, SoundSource.PLAYERS);
		}
	}

	/** The lift disc winks out: he drops softly (no fall damage until he lands). */
	public void endLift(ServerPlayer p) {
		if (!flag(p, F_LIFT)) {
			return;
		}
		OmnitrixState n = Omnitrix.state(p).copy();
		n.kitFlags &= ~F_LIFT;
		n.kitFlags |= F_HOP;
		n.timers.remove(key(LIFT_AT));
		n.timers.put(key(HOP_AT), p.level().getGameTime());
		Omnitrix.save(p, n);
		if (Omnitrix.state(p).animId == ANIM_LIFT) {
			anim(p, 0);
		}
	}

	/** Shift+X: he crackles 8 blocks ahead as a bolt; whatever he passes through takes 6. */
	public boolean boltDash(ServerPlayer p) {
		if (!use(p, BOLT_DASH)) {
			return false;
		}
		anim(p, ANIM_DASH);
		endLift(p);
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

	// ---------------------------------------------------------------- Z / Shift+Z

	/** Z: every creature within 5 is struck and thrown back. In water the charge spreads to 8 -- and shorts him out for 3. */
	public boolean discharge(ServerPlayer p) {
		if (!use(p, DISCHARGE)) {
			return false;
		}
		anim(p, ANIM_DISCHARGE);
		boolean wet = p.isInWaterOrBubble();
		double r = wet ? DISCHARGE_WATER_RADIUS : DISCHARGE_RADIUS;
		Vec3 c = p.position().add(0, 1, 0);
		for (LivingEntity e : around(p, c, r)) {
			zap(p, e, DISCHARGE_DAMAGE);
			shove(e, p.position(), 1.1, 0.45);
			arc(p, centre(e), null, 6, false);
		}
		ServerLevel l = level(p);
		QuirkFx.ring(l, c.add(0, -0.4, 0)).colours(0xFFFFFF, 0xFFE060).size((float) r).thickness(0.5f).life(10).spawn();
		QuirkFx.ring(l, c.add(0, 0.2, 0)).style(QuirkFx.RING_SOFT).colours(0xFFF8C0, 0xA898FF).size((float) r * 0.8f).thickness(0.8f).life(8).spawn();
		QuirkCamera.shakeAround(l, c, 10, 0.25f, 5);
		if (wet) {
			// water conducts both ways: he shorts himself out
			p.hurt(p.damageSources().magic(), SHORT_SELF);
			OmnitrixSfx.play(l, c, SHORT, 0.9f, 1f);
		}
		OmnitrixSfx.playOn(p, BOOM, 1.1f, 1f, SoundSource.PLAYERS);
		return true;
	}

	/** Shift+Z (ULTIMATE): a storm cloud over the crosshair (or over him), striking every foe under it each half second, then a finale. */
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
		setFlag(p, F_STORM, true);
		OmnitrixSfx.playOn(p, STORM, 1.2f, 1f, SoundSource.PLAYERS);
		return true;
	}

	/** One storm pulse: a strike on every foe under the cloud (or a random strike on the ground if none). No Static (the ult is enough). */
	private void stormPulse(ServerPlayer p, Storm st) {
		ServerLevel level = level(p);
		List<LivingEntity> under = under(p, st);
		for (LivingEntity e : under) {
			zap(p, e, STORM_DAMAGE, false);
			strike(p, e.position());
		}
		if (under.isEmpty()) {
			double a = level.random.nextDouble() * Mth.TWO_PI, r = level.random.nextDouble() * STORM_RADIUS * 0.8;
			strike(p, st.centre.add(Math.cos(a) * r, 0, Math.sin(a) * r));
		}
		OmnitrixSfx.play(level, st.centre, THUNDER, 0.7f, 1f);
	}

	private static List<LivingEntity> under(ServerPlayer p, Storm st) {
		List<LivingEntity> out = new ArrayList<>();
		for (LivingEntity e : around(p, st.centre, STORM_RADIUS)) {
			if (Math.abs(e.getY() - st.centre.y) < 8) {
				out.add(e);
			}
		}
		return out;
	}

	/** v0.17.2 revamp: the storm's last word -- one huge strike on every foe under it (22), a shock ring and a shake. */
	private void stormFinale(ServerPlayer p, Storm st) {
		ServerLevel level = level(p);
		for (LivingEntity e : under(p, st)) {
			zap(p, e, STORM_FINALE, false);
			shove(e, st.centre, 0.6, 0.5);
			strike(p, e.position());
		}
		strike(p, st.centre);
		QuirkFx.ring(level, st.centre.add(0, 0.1, 0)).colours(0xFFFFFF, 0xC8B8FF).size((float) STORM_RADIUS).thickness(0.7f).life(12).ground().spawn();
		QuirkFx.decal(level, st.centre.add(0, 0.03, 0)).style(QuirkFx.DECAL_SCORCH).colour(0x1A1620).size(3f).life(160).spawn();
		QuirkCamera.shakeAround(level, st.centre, 16, 0.45f, 8);
		OmnitrixSfx.play(level, st.centre, BOOM, 1.4f, 0.8f);
	}

	/** Storm pulses for the gametests (pump them without the server ticking the mock). */
	public int stormPulseNow(ServerPlayer p) {
		Storm st = STORMS.get(p.getUUID());
		if (st == null) {
			return 0;
		}
		int before = under(p, st).size();
		stormPulse(p, st);
		return before;
	}

	/** The storm's finale for the gametests (ends it). */
	public int stormFinaleNow(ServerPlayer p) {
		Storm st = STORMS.get(p.getUUID());
		if (st == null) {
			return 0;
		}
		int n = under(p, st).size();
		stormFinale(p, st);
		dropStorm(p.getUUID());
		setFlag(p, F_STORM, false);
		return n;
	}

	private static void strike(ServerPlayer p, Vec3 at) {
		AlienProjectileEntity s = AlienProjectileEntity.of(p, "brainstorm_strike");
		s.moveTo(at.x, at.y, at.z, 0f, 0f);
		s.colour(0).size(3f).held(true).speed(0).life(6).damage(0f).spawn();
		QuirkFx.decal(level(p), at.add(0, 0.03, 0)).style(QuirkFx.DECAL_SCORCH).colour(0x1A1620).size(0.9f).life(60).spawn();
	}

	// ---------------------------------------------------------------- V / Shift+V

	/** V: reads the creature under the crosshair -- its numbers in chat, outlined, +25% from his moves for 10 s, Overload at 2. */
	public boolean analyze(ServerPlayer p) {
		LivingEntity t = lookTarget(p, ANALYZE_RANGE);
		if (t == null) {
			say(p, "message.projecthero.omnitrix.brainstorm.no_target");
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
		QuirkFx.ring(level(p), centre(t)).flags(QuirkFx.F_VERTICAL).vec(p.getLookAngle()).colours(0xFFFFFF, 0xF0C060).size(1.6f).thickness(0.12f)
				.life(14).spawn();
		OmnitrixSfx.playOn(p, THINK, 0.9f, 1f, SoundSource.PLAYERS);
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

	/** Shift+V: a 6 s force-field bubble (allies inside are sheltered). */
	public boolean forceField(ServerPlayer p) {
		if (!use(p, FORCE_FIELD)) {
			return false;
		}
		anim(p, ANIM_FIELD);
		setFlag(p, F_FIELD, true);
		setTimer(p, FIELD_END, p.level().getGameTime() + FIELD_TICKS);
		OmnitrixSfx.playOn(p, FIELD, 1f, 1f, SoundSource.PLAYERS);
		return true;
	}

	/** The Force Field's shelter for the gametests (one pass without ticking). */
	public void fieldNow(ServerPlayer p) {
		tickField(p, level(p), 0);
	}

	// ---------------------------------------------------------------- C / Shift+C

	/** C: a cage of lightning round the creature under the crosshair: 12 and stunned 3 s. */
	public boolean staticCage(ServerPlayer p) {
		LivingEntity t = lookTarget(p, CAGE_RANGE);
		if (t == null) {
			say(p, "message.projecthero.omnitrix.brainstorm.no_target");
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
		OmnitrixSfx.playOn(p, CHAIN, 1f, 0.9f, SoundSource.PLAYERS);
		return true;
	}

	/** Shift+C: a 1 s charge, then a beam through everything along the crosshair. */
	public boolean megaBolt(ServerPlayer p) {
		if (flag(p, F_CHARGE) || !use(p, MEGA_BOLT)) {
			return false;
		}
		anim(p, ANIM_MEGA);
		setFlag(p, F_CHARGE, true);
		setTimer(p, MEGA_AT, p.level().getGameTime() + MEGA_CHARGE);
		OmnitrixSfx.playOn(p, CHARGE, 1f, 1f, SoundSource.PLAYERS);
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
		ServerLevel l = level(p);
		QuirkFx.ring(l, end).flags(QuirkFx.F_VERTICAL).vec(look).colours(0xFFFFFF, 0xA898FF).size(2.4f).thickness(0.4f).life(10).spawn();
		QuirkCamera.shake(p, 0.3f, 6);
		OmnitrixSfx.playOn(p, BOOM, 1.4f, 0.85f, SoundSource.PLAYERS);
		return n;
	}

	// ---------------------------------------------------------------- Attack

	@Override
	public boolean onAttack(ServerPlayer p, Entity target) {
		if (target instanceof LivingEntity le && le.isAlive()) {
			// v0.17.2 revamp: Shocking Pincers -- +2 and a Static charge (its own hit; vanilla's punch still lands after it)
			zap(p, le, PUNCH_BONUS);
			le.invulnerableTime = 0;
			anim(p, ANIM_PUNCH);
		}
		return false;
	}
}
