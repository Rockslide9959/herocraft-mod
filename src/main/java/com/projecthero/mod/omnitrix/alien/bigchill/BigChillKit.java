package com.projecthero.mod.omnitrix.alien.bigchill;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.OmnitrixSfx;
import com.projecthero.mod.omnitrix.alien.AlienKit;
import com.projecthero.mod.omnitrix.alien.AlienMove;
import com.projecthero.mod.omnitrix.alien.AlienStats;
import com.projecthero.mod.omnitrix.alien.AlienStatus;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.ghostfreak.GhostfreakKit;
import com.projecthero.mod.omnitrix.alien.jetray.RecalKitMath;
import com.projecthero.mod.omnitrix.data.OmnitrixState;
import com.projecthero.mod.omnitrix.entity.AlienProjectileEntity;
import com.projecthero.mod.quirk.fx.QuirkCamera;
import com.projecthero.mod.quirk.fx.QuirkFx;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2: Big Chill, the Necrofriggian (Recalibrated Omnitrix). v0.17.2 revamp (docs/OMNITRIX_REVAMP_BIGCHILL.md; user: "can you
 * work on revamps for the rest of the alien force aliens please, look at their powers and abilities in the show and do research
 * and make them good please"): a moth-winged ghost of the ice. His engine is CHILL -> FROZEN SOLID -> SHATTER, and his phasing
 * freezes whatever he passes through from the inside (the show's signature trick). No Ultimate Big Chill (user: "dont add
 * ultimate aliens yet"). The body is only the player model in the user's two skins: wings wrapped round him (cloaked, his normal
 * look) or open (flying and attacking) -- {@link #F_OPEN} picks the skin.
 *
 * v0.17.2 damage pass (user: "make the damage of all of ben 10s alien stronger"; docs/OMNITRIX_DAMAGE_PASS_ALIENFORCE.md): the
 * damage numbers in the table below are the pre-pass ones (~x1.6 now) -- the constants are the source of truth.
 *
 * <pre>
 *   R          Ice Shard        a shard of ice (7, chilled), 0.6 s
 *   Shift+R    Freezing Breath  HOLD up to 2.5 s: a cone of freezing breath, reach 4 growing to 8; 3 every 0.5 s (up to 15),
 *                               chilled, frozen solid from the third pulse; water under it freezes over, fire goes out;
 *                               6 s cooldown from the release
 *   G          Frost Grip       seize the creature in reach (4.5): 13, frozen solid 3 s
 *   Shift+G    Shatter          every frozen-solid creature within 8 he can see bursts out of its ice: 16, flying ice 5 to
 *                               anything within 2 of it (refused, free, if nothing is frozen)
 *   X          Moth Flight      take off / land
 *   Shift+X    Phase            intangible 5 s: through walls, no physical harm; PHANTOM CHILL: passing through a creature
 *                               freezes it from the inside (8, frozen solid 2 s, once each per phase). Not while burning
 *   Z          Ice Dive         climb, then plunge: r 4, 17 + chilled, a frost crater
 *   Shift+Z    Absolute Zero    ULTIMATE: 1 s drawing the cold in (rooted), then r 8: 55 centre / 40 edge, frozen solid 4 s,
 *                               water freezes and fire goes out all round
 *   C          Blizzard         a 4 s snowstorm round him (r 6): 2 every 0.5 s (16), chilled
 *   Shift+C    Frost Field      frost spreads where he looks (12, r 4) for 8 s: 6 + chilled when it forms, then 1 + chilled
 *                               every 0.5 s to anything in it; water in it freezes over
 *   V          Cloak            the wings wrap round: invisible 10 s, hunters lose track; striking out of it is an AMBUSH
 *                               (+6, frozen solid 2 s) and opens the cloak
 *   Shift+V    Frost Armour     6 s: -40% damage, attackers within 4 chilled
 *   Attack     Frost Touch      chills (a chilled creature freezes solid 2 s)
 *   Passives   cold immune (freezing, powder snow), no fall damage (he drifts on his wings), Frost Mend (on snow / ice or in a
 *              snowy biome: +1 every 2 s)
 *   Weak       fire x1.5, and burning breaks Phase and the Cloak (and he can't phase while alight); heat: in the Nether or a
 *              desert / badlands his cold moves do 25% less
 * </pre>
 */
public final class BigChillKit extends AlienKit {
	public static final String ID = Aliens.BIG_CHILL.id();
	public static final int ICE = 0x9AE0FF;
	public static final int ICE_PALE = 0xE8F8FF;

	// ---------------------------------------------------------------- sounds
	public static final OmnitrixSfx.Cue BREATH = OmnitrixSfx.cue("big_chill_breath")
			.layer(SoundEvents.POWDER_SNOW_STEP, 0.5f, 0.6f)
			.layer(SoundEvents.PLAYER_HURT_FREEZE, 0.25f, 0.7f)
			.layer(SoundEvents.ELYTRA_FLYING, 0.15f, 1.7f)
			.gap(4);
	public static final OmnitrixSfx.Cue SHARD = OmnitrixSfx.cue("big_chill_shard")
			.layer(SoundEvents.GLASS_BREAK, 0.25f, 1.8f)
			.layer(SoundEvents.ARROW_SHOOT, 0.3f, 1.4f);
	public static final OmnitrixSfx.Cue FREEZE = OmnitrixSfx.cue("big_chill_freeze")
			.layer(SoundEvents.GLASS_PLACE, 0.4f, 0.7f)
			.layer(SoundEvents.POWDER_SNOW_PLACE, 0.4f, 0.8f)
			.layer(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.2f, 1.6f, 2)
			.gap(3);
	public static final OmnitrixSfx.Cue WINGS = OmnitrixSfx.cue("big_chill_wings")
			.layer(SoundEvents.PHANTOM_FLAP, 0.35f, 0.7f)
			.layer(SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM, 0.12f, 0.6f)
			.gap(6);
	public static final OmnitrixSfx.Cue PHASE = OmnitrixSfx.cue("big_chill_phase")
			.layer(SoundEvents.ALLAY_HURT, 0.2f, 0.5f)
			.layer(SoundEvents.SOUL_ESCAPE, 0.45f, 0.8f)
			.layer(SoundEvents.POWDER_SNOW_STEP, 0.3f, 0.5f);
	public static final OmnitrixSfx.Cue CLOAK = OmnitrixSfx.cue("big_chill_cloak")
			.layer(SoundEvents.PHANTOM_FLAP, 0.3f, 0.6f)
			.layer(SoundEvents.SOUL_ESCAPE, 0.35f, 0.6f);
	public static final OmnitrixSfx.Cue STORM = OmnitrixSfx.cue("big_chill_storm")
			.layer(SoundEvents.ELYTRA_FLYING, 0.25f, 0.6f)
			.layer(SoundEvents.POWDER_SNOW_BREAK, 0.4f, 0.6f)
			.gap(8);
	public static final OmnitrixSfx.Cue ZERO = OmnitrixSfx.cue("big_chill_zero")
			.layer(SoundEvents.GLASS_BREAK, 0.4f, 0.6f)
			.layer(SoundEvents.AMETHYST_BLOCK_BREAK, 0.45f, 0.5f)
			.layer(SoundEvents.PLAYER_HURT_FREEZE, 0.35f, 0.5f, 2)
			.layer(SoundEvents.WARDEN_SONIC_BOOM, 0.08f, 1.9f);
	// v0.17.2 revamp
	public static final OmnitrixSfx.Cue SHATTER_SND = OmnitrixSfx.cue("big_chill_shatter")
			.layer(SoundEvents.GLASS_BREAK, 0.5f, 0.8f)
			.layer(SoundEvents.AMETHYST_CLUSTER_BREAK, 0.45f, 0.7f)
			.layer(SoundEvents.GLASS_BREAK, 0.35f, 1.3f, 2);
	public static final OmnitrixSfx.Cue CHILL_THROUGH = OmnitrixSfx.cue("big_chill_through")
			.layer(SoundEvents.SOUL_ESCAPE, 0.4f, 1.3f)
			.layer(SoundEvents.GLASS_PLACE, 0.4f, 0.6f)
			.gap(3);

	// ---------------------------------------------------------------- poses (client: BigChillClient)
	public static final int ANIM_BREATH = 1, ANIM_SHARD = 2, ANIM_GRIP = 3, ANIM_SHATTER = 4, ANIM_TAKEOFF = 5, ANIM_PHASE = 6, ANIM_DIVE = 7,
			ANIM_FIELD = 8, ANIM_CLOAK = 9, ANIM_ARMOUR = 10, ANIM_BLIZZARD = 11, ANIM_ZERO = 12, ANIM_LAND = 13, ANIM_AMBUSH = 14;

	public static final int F_FLYING = 1;
	public static final int F_PHASE = 2;
	public static final int F_CLOAK = 4;
	public static final int F_ARMOUR = 8;
	public static final int F_BLIZZARD = 16;
	public static final int F_ZERO = 32;
	public static final int F_DIVING = 64;
	/** v0.17.2 revamp: the held Freezing Breath is running. */
	public static final int F_BREATH = 128;
	/** v0.17.2 revamp: his wings are open (the uncloaked skin); otherwise wrapped round him (the cloaked skin). */
	public static final int F_OPEN = 256;
	/** v0.17.2 revamp: his Frost Field lies on the ground. */
	public static final int F_FIELD = 512;

	public static final String ICE_SHARD = "ice_shard", BREATH_MOVE = "freezing_breath", FROST_GRIP = "frost_grip", SHATTER = "shatter",
			FLIGHT = "moth_flight", PHASE_MOVE = "phase", ICE_DIVE = "ice_dive", ABSOLUTE_ZERO = "absolute_zero", BLIZZARD = "blizzard",
			FROST_FIELD = "frost_field", CLOAK_MOVE = "cloak", FROST_ARMOUR = "frost_armour", FROST_TOUCH = "frost_touch";
	/** Synced kit timers. */
	public static final String BREATH_START = "breath_start", OPEN_UNTIL = "open_until", FIELD_END = "field_end";

	// v0.17.2 (user: "can you make the damage of all of ben 10s alien stronger, i want each alien to feel like its own entire ability and i want them to be strong aswell"): ~x1.6 damage pass (docs/OMNITRIX_DAMAGE_PASS_ALIENFORCE.md)
	public static final float SHARD_DAMAGE = 11f;
	public static final float BREATH_PULSE_DAMAGE = 8f;
	public static final int BREATH_MAX_TICKS = 50, BREATH_PULSE = 10, BREATH_COOLDOWN = 120;
	public static final double BREATH_RANGE = 8.0, BREATH_HALF_ANGLE = 28.0;
	public static final float GRIP_DAMAGE = 22f;
	public static final float SHATTER_DAMAGE = 28f, SHATTER_SPLASH = 10f;
	public static final double SHATTER_RANGE = 8.0, SHATTER_SPLASH_RADIUS = 2.0;
	public static final float PHANTOM_DAMAGE = 13f;
	public static final float DIVE_DAMAGE = 28f;
	public static final float BLIZZARD_DAMAGE = 7f;
	public static final int BLIZZARD_TICKS = 80;
	public static final double BLIZZARD_RADIUS = 6.0;
	public static final float FIELD_BURST = 10f, FIELD_DAMAGE = 4f; // the field pulses 16 times, so its tick stays under the 7-10 of a short DoT
	public static final int FIELD_TICKS = 160;
	public static final double FIELD_RADIUS = 4.0, FIELD_REACH = 12.0;
	public static final float ZERO_DAMAGE = 95f, ZERO_EDGE = 70f;
	public static final double ZERO_RADIUS = 8.0;
	public static final int ZERO_WINDUP = 20;
	public static final int PHASE_TICKS = 100;
	public static final int PHASE_COOLDOWN = 200;
	public static final int CLOAK_TICKS = 200;
	public static final float AMBUSH_DAMAGE = 10f;
	public static final int ARMOUR_TICKS = 120;
	public static final float FLY_SPEED = 0.07f;
	public static final float HEAT_FACTOR = 0.75f;
	/** How long his wings stay open after an attacking move (so the skin never flickers between quick moves). */
	public static final int OPEN_TICKS = 30;
	/** Chilled: frozen this far (vanilla's powder-snow maximum is 140; it thaws 2 a tick), so it stays "chilled" for 1.5 s. */
	public static final int CHILL = 200;
	/** Frozen solid: from here up the client draws the ice shell ({@code ticksFrozen} is synced). */
	public static final int SHELL_AT = 260;

	/** Positions are stored as kit timers: round(v * 64) + this, so they stay positive. */
	private static final long OFF = 1L << 40;

	/** v0.17.2 revamp: player -> the creatures his current Phase has already frozen from the inside. */
	private static final Map<UUID, Set<Integer>> PHASED_THROUGH = new ConcurrentHashMap<>();
	/** v0.17.2 revamp: once per game tick (the breath pulses / field ticks must not double up: RipjawsKit.tick). */
	private static final Map<UUID, Long> LAST_TICK = new ConcurrentHashMap<>();

	public static void register() {
		Aliens.register(new BigChillKit());
		AlienProjectileEntity.onHit("big_chill_shard", (proj, hit) -> {
			if (proj.level() instanceof ServerLevel l && proj.getOwner() instanceof ServerPlayer sp) {
				if (hit instanceof EntityHitResult eh && eh.getEntity() instanceof LivingEntity le) {
					chill(sp, le, 0);
				}
				QuirkFx.shards(l, hit.getLocation()).block(Blocks.ICE.defaultBlockState()).size(0.5f).count(5).life(14).spawn();
			}
		});
	}

	public static void clearSessionState() {
		PHASED_THROUGH.clear();
		LAST_TICK.clear();
	}

	@Override
	public String id() {
		return ID;
	}

	/** {@code p} is Big Chill right now. */
	public static boolean isBigChill(Player p) {
		return Omnitrix.hasPower(p) && Omnitrix.state(p).transformed() && ID.equals(Omnitrix.state(p).active);
	}

	@Override
	public AlienStats stats() {
		// v0.17.2 revamp: a slight ghost of a thing -- no armour of his own (only every alien's hidden Resistance I)
		return AlienStats.builder().health(28).armor(10, 0).attack(6).knockbackResist(0.15f).safeFall(8).build(); // v0.17.2 (user set the unarmed melee bonuses: "Swampfire +9, Echo Echo +4, Humungousaur +15, Jetray +5, Big Chill +6, Chromastone +7, Brainstorm +4, Spidermonkey +5, Goop +5")
	}

	@Override
	protected List<AlienMove> declareMoves() {
		return List.of(AlienMove.tap(ICE_SHARD, AbilitySlot.SLOT_1, 12), AlienMove.shift(BREATH_MOVE, AbilitySlot.SLOT_1, BREATH_COOLDOWN),
				AlienMove.tap(FROST_GRIP, AbilitySlot.SLOT_2, 140), AlienMove.shift(SHATTER, AbilitySlot.SLOT_2, 160),
				AlienMove.tap(FLIGHT, AbilitySlot.SLOT_3, 10), AlienMove.shift(PHASE_MOVE, AbilitySlot.SLOT_3, PHASE_COOLDOWN),
				AlienMove.tap(ICE_DIVE, AbilitySlot.SLOT_4, 200), AlienMove.shift(ABSOLUTE_ZERO, AbilitySlot.SLOT_4, 1200),
				AlienMove.tap(CLOAK_MOVE, AbilitySlot.SLOT_5, 400), AlienMove.shift(FROST_ARMOUR, AbilitySlot.SLOT_5, 300),
				AlienMove.tap(BLIZZARD, AbilitySlot.SLOT_6, 300), AlienMove.shift(FROST_FIELD, AbilitySlot.SLOT_6, 320),
				AlienMove.vanilla(FROST_TOUCH, "Attack", 0));
	}

	// ---------------------------------------------------------------- positions as synced timers

	private void setPos(ServerPlayer p, String name, Vec3 v) {
		OmnitrixState n = Omnitrix.state(p).copy();
		n.timers.put(key(name + "_x"), Math.round(v.x * 64) + OFF);
		n.timers.put(key(name + "_y"), Math.round(v.y * 64) + OFF);
		n.timers.put(key(name + "_z"), Math.round(v.z * 64) + OFF);
		Omnitrix.save(p, n);
	}

	/** A position stored with {@link #setPos} (client-safe), or null. */
	public static Vec3 pos(Player p, String name) {
		OmnitrixState s = Omnitrix.state(p);
		long x = s.readyAt(ID + "." + name + "_x");
		if (x <= 0) {
			return null;
		}
		return new Vec3((x - OFF) / 64.0, (s.readyAt(ID + "." + name + "_y") - OFF) / 64.0, (s.readyAt(ID + "." + name + "_z") - OFF) / 64.0);
	}

	// ---------------------------------------------------------------- lifecycle

	@Override
	public void onTransform(ServerPlayer p) {
		boolean air = !p.onGround() && !p.isInWater() && !p.isPassenger();
		RecalKitMath.wings(p, air, FLY_SPEED);
		setFlag(p, F_FLYING | F_OPEN, air);
		p.setTicksFrozen(0);
		OmnitrixSfx.playOn(p, WINGS, 1.0f, 1.0f, SoundSource.PLAYERS);
	}

	private static final String[] STATE_TIMERS = { "phase_end", "cloak_end", "armour_end", "blizzard_end", "zero_at", "dive_at", "dive_end", BREATH_START,
			OPEN_UNTIL, FIELD_END };

	@Override
	public void onRevert(ServerPlayer p) {
		boolean wasPhased = flag(p, F_PHASE);
		endCloak(p);
		PHASED_THROUGH.remove(p.getUUID());
		LAST_TICK.remove(p.getUUID());
		OmnitrixState n = Omnitrix.state(p).copy();
		for (String t : STATE_TIMERS) {
			n.timers.remove(key(t));
		}
		for (String axis : new String[] { "_x", "_y", "_z" }) {
			n.timers.remove(key("field" + axis));
		}
		n.kitFlags = 0;
		Omnitrix.save(p, n);
		RecalKitMath.groundWings(p);
		if (wasPhased) {
			GhostfreakKit.settleOutsideBlocks(p);
		}
	}

	@Override
	public boolean keyActive(Player p, AbilitySlot slot) {
		return switch (slot) {
			case SLOT_1 -> flag(p, F_BREATH);
			case SLOT_3 -> flag(p, F_FLYING) || flag(p, F_PHASE);
			case SLOT_4 -> flag(p, F_ZERO) || flag(p, F_DIVING);
			case SLOT_5 -> flag(p, F_CLOAK) || flag(p, F_ARMOUR);
			case SLOT_6 -> flag(p, F_BLIZZARD) || flag(p, F_FIELD);
			default -> false;
		};
	}

	@Override
	public boolean phasing(Player p) {
		return flag(p, F_PHASE);
	}

	/** v0.17.2 revamp (heat weakness): in the Nether or a desert / badlands his cold does 25% less. */
	public static float heat(Player p) {
		if (p.level().dimensionType().ultraWarm()) {
			return HEAT_FACTOR;
		}
		return p.level().getBiome(p.blockPosition()).value().getBaseTemperature() >= 1.5f ? HEAT_FACTOR : 1f;
	}

	/** v0.17.2 revamp (Frost Mend): on snow or ice, in powder snow, or in a snowy biome. */
	public static boolean cold(Player p) {
		BlockState under = p.level().getBlockState(p.blockPosition().below());
		BlockState at = p.level().getBlockState(p.blockPosition());
		return under.is(BlockTags.ICE) || under.is(BlockTags.SNOW) || at.is(Blocks.SNOW) || p.isInPowderSnow
				|| p.level().getBiome(p.blockPosition()).value().coldEnoughToSnow(p.blockPosition());
	}

	@Override
	public void tick(ServerPlayer p, OmnitrixState s, long now) {
		// v0.17.2 revamp: once per game tick -- timed pulses must not double up if he is ticked twice in a tick
		Long last = LAST_TICK.put(p.getUUID(), now);
		if (last != null && last == now) {
			return;
		}
		ServerLevel l = level(p);
		if (!p.getAbilities().mayfly) {
			RecalKitMath.wings(p, false, FLY_SPEED);
		}
		boolean flying = p.getAbilities().flying;
		if (flying != flag(p, F_FLYING)) {
			setFlag(p, F_FLYING, flying);
		}
		if (flying && p.tickCount % 24 == 0) {
			OmnitrixSfx.playOn(p, WINGS, 0.5f, 1.0f, SoundSource.PLAYERS);
		}
		// the cold never touches him
		if (p.getTicksFrozen() > 0) {
			p.setTicksFrozen(0);
		}
		p.resetFallDistance();
		// Frost Mend
		if (now % 40 == 0 && p.getHealth() < p.getMaxHealth() && p.isAlive() && cold(p)) {
			p.heal(1f);
		}
		// heat breaks his intangibility and his cloak
		if (p.isOnFire() && (flag(p, F_PHASE) || flag(p, F_CLOAK))) {
			endPhase(p);
			endCloak(p);
		}
		if (flag(p, F_PHASE)) {
			if (now >= timer(p, "phase_end")) {
				endPhase(p);
			} else {
				phantomChill(p);
			}
		}
		if (flag(p, F_CLOAK)) {
			if (now >= timer(p, "cloak_end")) {
				endCloak(p);
			} else if (now % 10 == 0) {
				p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 30, 0, false, false, false));
				for (Mob m : l.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(24))) {
					AlienStatus.loseTrack(m, p);
				}
			}
		}
		if (flag(p, F_ARMOUR) && now >= timer(p, "armour_end")) {
			setFlag(p, F_ARMOUR, false);
			setTimer(p, "armour_end", 0);
		}
		tickBreath(p, l, now);
		if (flag(p, F_BLIZZARD)) {
			tickBlizzard(p, l, now);
		}
		if (flag(p, F_FIELD)) {
			tickField(p, l, now);
		}
		tickDive(p, l, now);
		if (flag(p, F_ZERO)) {
			p.setDeltaMovement(0, Math.max(0, p.getDeltaMovement().y) * 0.5, 0);
			p.hurtMarked = true;
			if (now >= timer(p, "zero_at")) {
				fireAbsoluteZero(p, l);
			}
		}
		updateWings(p, now);
	}

	// ---------------------------------------------------------------- the wings: which skin

	/** Whether his wings are open right now (flying, diving, breathing, storming, the ultimate, or just attacked). */
	public boolean wingsOpen(ServerPlayer p, long now) {
		if (flag(p, F_CLOAK)) {
			return false; // the cloak IS the wings wrapped round him
		}
		return p.getAbilities().flying || flag(p, F_DIVING) || timer(p, "dive_at") > 0 || flag(p, F_ZERO) || flag(p, F_BLIZZARD) || flag(p, F_BREATH)
				|| now < timer(p, OPEN_UNTIL);
	}

	/** Swaps the skin when the wings open or fold: a soft frost ring and a wingbeat mark the swap. */
	public void updateWings(ServerPlayer p, long now) {
		boolean open = wingsOpen(p, now);
		if (open == flag(p, F_OPEN)) {
			return;
		}
		setFlag(p, F_OPEN, open);
		ServerLevel l = level(p);
		QuirkFx.ring(l, p.position().add(0, p.getBbHeight() * 0.6, 0)).style(QuirkFx.RING_SOFT).colours(ICE_PALE, ICE).size(open ? 1.6f : 1.1f).thickness(0.3f)
				.life(7).spawn();
		if (open) {
			OmnitrixSfx.playOn(p, WINGS, 0.7f, 1.1f, SoundSource.PLAYERS);
		}
	}

	// ---------------------------------------------------------------- keys

	@Override
	public void press(ServerPlayer p, AbilitySlot slot, boolean shift) {
		if (flag(p, F_ZERO)) {
			return;
		}
		switch (slot) {
			case SLOT_1 -> {
				if (shift) {
					breath(p);
				} else {
					iceShard(p);
				}
			}
			case SLOT_2 -> {
				if (shift) {
					shatter(p);
				} else {
					frostGrip(p);
				}
			}
			case SLOT_3 -> {
				if (shift) {
					phase(p);
				} else {
					flight(p);
				}
			}
			case SLOT_4 -> {
				if (shift) {
					absoluteZero(p);
				} else {
					iceDive(p);
				}
			}
			case SLOT_5 -> {
				if (shift) {
					frostArmour(p);
				} else {
					cloak(p);
				}
			}
			case SLOT_6 -> {
				if (shift) {
					frostField(p);
				} else {
					blizzard(p);
				}
			}
			default -> {
			}
		}
	}

	@Override
	public void release(ServerPlayer p, AbilitySlot slot, boolean shift, int heldTicks) {
		if (slot == AbilitySlot.SLOT_1 && flag(p, F_BREATH)) {
			stopBreath(p); // the Freezing Breath runs while Shift+R is held
		}
	}

	private static boolean attacking(String moveId) {
		return !moveId.equals(CLOAK_MOVE) && !moveId.equals(FLIGHT) && !moveId.equals(PHASE_MOVE) && !moveId.equals(FROST_ARMOUR);
	}

	/** Any attack opens the cloak and spreads his wings for a moment (the uncloaked skin). */
	@Override
	protected void onUse(ServerPlayer p, String moveId) {
		if (!attacking(moveId)) {
			return;
		}
		if (flag(p, F_CLOAK)) {
			endCloak(p);
		}
		long now = p.level().getGameTime();
		setTimer(p, OPEN_UNTIL, now + OPEN_TICKS);
		updateWings(p, now);
	}

	@Override
	public boolean onAttack(ServerPlayer p, Entity target) {
		long now = p.level().getGameTime();
		if (target instanceof LivingEntity le) {
			if (flag(p, F_CLOAK)) {
				// v0.17.2 revamp: the Ambush -- out of the dark, the wings flare open and the cold bites deep
				endCloak(p);
				anim(p, ANIM_AMBUSH);
				hurt(p, le, AMBUSH_DAMAGE * heat(p), false);
				freezeSolid(p, le, 40);
				QuirkFx.ring(level(p), le.position().add(0, le.getBbHeight() * 0.5, 0)).flags(QuirkFx.F_VERTICAL).vec(p.getLookAngle()).colours(ICE_PALE, ICE)
						.size(1.4f).thickness(0.3f).life(8).spawn();
				OmnitrixSfx.play(level(p), le.position(), FREEZE, 1.1f, 0.8f);
			} else {
				chill(p, le, 40); // a frosty touch
			}
		}
		setTimer(p, OPEN_UNTIL, now + OPEN_TICKS);
		updateWings(p, now);
		return false;
	}

	@Override
	public InteractionResult onUseEntity(ServerPlayer p, InteractionHand hand, Entity target) {
		return InteractionResult.PASS;
	}

	/**
	 * Chills {@code e}: slowed, frosted over ({@code ticksFrozen} past vanilla's maximum, so the client shell shows and vanilla's
	 * frost overlay and shiver play) -- and an already-chilled creature freezes solid for {@code solidTicks} (rooted).
	 */
	public static void chill(ServerPlayer by, LivingEntity e, int solidTicks) {
		boolean already = e.getTicksFrozen() >= e.getTicksRequiredToFreeze();
		e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1), by);
		if (already && solidTicks > 0) {
			freezeSolid(by, e, solidTicks);
		} else {
			e.setTicksFrozen(Math.max(e.getTicksFrozen(), CHILL));
		}
	}

	/** Frozen solid at once for {@code ticks}: the shell, rooted. */
	public static void freezeSolid(ServerPlayer by, LivingEntity e, int ticks) {
		int t = AlienStatus.isBoss(e) ? Math.max(5, ticks / 4) : ticks;
		e.setTicksFrozen(Math.max(e.getTicksFrozen(), SHELL_AT + t * 2));
		AlienStatus.root(by, e, ticks, 30);
	}

	/** Whether {@code e} wears the ice shell (frozen solid by Big Chill; synced, client-safe). */
	public static boolean shelled(LivingEntity e) {
		return e.getTicksFrozen() >= SHELL_AT;
	}

	private static void say(ServerPlayer p, String key) {
		p.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.GRAY), true);
	}

	// ---------------------------------------------------------------- R: Ice Shard / Shift+R: Freezing Breath (held)

	public boolean iceShard(ServerPlayer p) {
		if (!use(p, ICE_SHARD)) {
			return false;
		}
		anim(p, ANIM_SHARD);
		AlienProjectileEntity.of(p, "big_chill_shard").colour(ICE).size(0.3f).damage(SHARD_DAMAGE * heat(p)).speed(2.2).life(30).spawn();
		OmnitrixSfx.play(level(p), p.getEyePosition(), SHARD, 1.0f, 1.0f);
		return true;
	}

	/** Shift+R (held): the Freezing Breath starts; it pulses until the keys come up (2.5 s at most). True if it started. */
	public boolean breath(ServerPlayer p) {
		if (flag(p, F_BREATH) || !use(p, BREATH_MOVE)) {
			return false;
		}
		anim(p, ANIM_BREATH);
		setTimer(p, BREATH_START, p.level().getGameTime());
		setFlag(p, F_BREATH, true);
		OmnitrixSfx.play(level(p), p.getEyePosition(), BREATH, 1.1f, 1.0f);
		breathPulse(p, level(p), 0, 0); // the first breath lands at once
		return true;
	}

	/** The breath's reach after {@code held} ticks: 4 blocks, growing to 8 over 1.5 s. */
	public static double breathReach(float held) {
		return BREATH_RANGE * (0.5 + 0.5 * Math.min(1.0, held / 30.0));
	}

	public void stopBreath(ServerPlayer p) {
		if (!flag(p, F_BREATH)) {
			return;
		}
		setFlag(p, F_BREATH, false);
		setTimer(p, BREATH_START, 0);
		cooldown(p, BREATH_MOVE, BREATH_COOLDOWN); // the 6 s run from the release
		if (Omnitrix.state(p).animId == ANIM_BREATH) {
			anim(p, 0);
		}
		long now = p.level().getGameTime();
		setTimer(p, OPEN_UNTIL, now + OPEN_TICKS / 2);
	}

	private void tickBreath(ServerPlayer p, ServerLevel l, long now) {
		if (!flag(p, F_BREATH)) {
			return;
		}
		long held = now - timer(p, BREATH_START);
		if (held >= BREATH_MAX_TICKS) {
			stopBreath(p);
			return;
		}
		if (held > 0 && held % BREATH_PULSE == 0) {
			breathPulse(p, l, (int) (held / BREATH_PULSE), held);
		}
	}

	/** One pulse of the breath: 3 to everything in the cone, chilled (frozen solid from the third pulse); water freezes, fire dies. */
	public void breathPulse(ServerPlayer p, ServerLevel l, int index, long held) {
		double reach = breathReach(held);
		float dmg = BREATH_PULSE_DAMAGE * heat(p);
		for (LivingEntity e : RecalKitMath.cone(p, reach, BREATH_HALF_ANGLE)) {
			hurt(p, e, dmg, false);
			chill(p, e, index >= 2 ? 40 : 0);
		}
		// the world under the breath: water skins over with ice, fire goes out
		Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
		for (double d = 1.0; d <= reach; d += 1.0) {
			BlockPos at = BlockPos.containing(eye.add(look.scale(d)));
			for (int dy = 0; dy >= -3; dy--) {
				BlockPos b = at.offset(0, dy, 0);
				if (douse(l, b) || freezeWater(l, b)) {
					break;
				}
			}
		}
		if (index > 0) {
			OmnitrixSfx.play(l, eye, BREATH, 0.7f, 1.0f + index * 0.05f);
		}
	}

	/** Puts out a fire block at {@code at}. True if there was one. */
	private static boolean douse(ServerLevel l, BlockPos at) {
		if (l.getBlockState(at).is(BlockTags.FIRE)) {
			l.removeBlock(at, false);
			l.levelEvent(1009, at, 0); // the fizz
			return true;
		}
		return false;
	}

	/** A still water source at {@code at} with air over it freezes into frosted ice (which thaws by itself). True if it did. */
	private static boolean freezeWater(ServerLevel l, BlockPos at) {
		BlockState st = l.getBlockState(at);
		if (st.getBlock() instanceof LiquidBlock && st.getFluidState().is(Fluids.WATER) && st.getFluidState().isSource() && l.getBlockState(at.above()).isAir()) {
			l.setBlockAndUpdate(at, Blocks.FROSTED_ICE.defaultBlockState());
			l.scheduleTick(at, Blocks.FROSTED_ICE, 60 + l.random.nextInt(60));
			return true;
		}
		return false;
	}

	/** Freezes water and puts out fire in a disc of {@code r} round {@code c} (a few blocks up and down). */
	private static void frostGround(ServerLevel l, BlockPos c, int r) {
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				if (dx * dx + dz * dz > r * r) {
					continue;
				}
				for (int dy = 2; dy >= -3; dy--) {
					BlockPos at = c.offset(dx, dy, dz);
					if (douse(l, at) || freezeWater(l, at)) {
						break;
					}
				}
			}
		}
	}

	// ---------------------------------------------------------------- G: Frost Grip / Shift+G: Shatter

	public boolean frostGrip(ServerPlayer p) {
		LivingEntity t = lookTarget(p, 4.5);
		if (t == null) {
			say(p, "message.projecthero.omnitrix.big_chill.no_target");
			return false;
		}
		if (!use(p, FROST_GRIP)) {
			return false;
		}
		anim(p, ANIM_GRIP);
		hurt(p, t, GRIP_DAMAGE * heat(p), false);
		freezeSolid(p, t, 60);
		QuirkFx.ring(level(p), t.position().add(0, 0.05, 0)).style(QuirkFx.RING_STEAM).colours(ICE_PALE, ICE).size(1.6f).thickness(0.4f).life(10).ground().spawn();
		OmnitrixSfx.play(level(p), t.position(), FREEZE, 1.0f, 1.0f);
		return true;
	}

	/** Shift+G: every frozen-solid creature within 8 he can see bursts out of its ice. True if anything shattered. */
	public boolean shatter(ServerPlayer p) {
		List<LivingEntity> frozen = around(p, p.position(), SHATTER_RANGE).stream().filter(BigChillKit::shelled).filter(p::hasLineOfSight).toList();
		if (frozen.isEmpty()) {
			if (cooldownLeft(p, SHATTER) > 0) {
				return use(p, SHATTER); // the "not ready" line
			}
			say(p, "message.projecthero.omnitrix.big_chill.nothing_frozen");
			return false;
		}
		if (!use(p, SHATTER)) {
			return false;
		}
		anim(p, ANIM_SHATTER);
		ServerLevel l = level(p);
		float h = heat(p);
		Set<Integer> splashed = new HashSet<>();
		for (LivingEntity e : frozen) {
			// out of the shell (still chilled), the root gone
			e.setTicksFrozen(CHILL);
			MobEffectInstance slow = e.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
			if (slow != null && slow.getAmplifier() >= 9) {
				e.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
				e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1), p);
			}
			hurt(p, e, SHATTER_DAMAGE * h, false);
			Vec3 c = e.getBoundingBox().getCenter();
			for (LivingEntity n : around(p, c, SHATTER_SPLASH_RADIUS)) {
				if (n != e && !frozen.contains(n) && splashed.add(n.getId())) {
					hurt(p, n, SHATTER_SPLASH * h, false); // the flying ice
				}
			}
			QuirkFx.shards(l, c).block(Blocks.PACKED_ICE.defaultBlockState()).size(Math.max(1.0f, e.getBbHeight() * 0.6f)).count(16).life(24).spawn();
			QuirkFx.ring(l, c).flags(QuirkFx.F_VERTICAL).vec(c.subtract(p.getEyePosition())).colours(ICE_PALE, ICE).size(1.8f).thickness(0.35f).life(8).spawn();
			OmnitrixSfx.play(l, c, SHATTER_SND, 1.1f, 1.0f);
		}
		QuirkCamera.shake(p, 0.18f, 5);
		return true;
	}

	// ---------------------------------------------------------------- X: Moth Flight / Shift+X: Phase

	/** X: take off / land. */
	public boolean flight(ServerPlayer p) {
		if (!use(p, FLIGHT)) {
			return false;
		}
		boolean fly = !p.getAbilities().flying;
		RecalKitMath.wings(p, fly, FLY_SPEED);
		setFlag(p, F_FLYING, fly);
		if (fly) {
			anim(p, ANIM_TAKEOFF);
			setTimer(p, OPEN_UNTIL, p.level().getGameTime() + OPEN_TICKS); // the wings stay open through the lift-off
			p.setDeltaMovement(p.getDeltaMovement().add(0, 0.4, 0));
			p.hurtMarked = true;
			OmnitrixSfx.playOn(p, WINGS, 1.0f, 1.0f, SoundSource.PLAYERS);
			QuirkFx.ring(level(p), p.position().add(0, 0.05, 0)).style(QuirkFx.RING_SOFT).colours(ICE_PALE, ICE).size(2.2f).thickness(0.4f).life(10).ground()
					.spawn();
		} else {
			anim(p, ANIM_LAND);
		}
		updateWings(p, p.level().getGameTime());
		return true;
	}

	/** Shift+X: intangible for 5 s (Shift+X again ends it early). True if it started or ended. */
	public boolean phase(ServerPlayer p) {
		if (flag(p, F_PHASE)) {
			endPhase(p);
			return true;
		}
		if (p.isOnFire()) {
			say(p, "message.projecthero.omnitrix.big_chill.too_hot");
			return false;
		}
		if (!use(p, PHASE_MOVE)) {
			return false;
		}
		anim(p, ANIM_PHASE);
		setFlag(p, F_PHASE, true);
		PHASED_THROUGH.put(p.getUUID(), ConcurrentHashMap.newKeySet());
		setTimer(p, "phase_end", p.level().getGameTime() + PHASE_TICKS);
		cooldown(p, PHASE_MOVE, PHASE_TICKS + PHASE_COOLDOWN);
		OmnitrixSfx.playOn(p, PHASE, 1.0f, 1.0f, SoundSource.PLAYERS);
		return true;
	}

	/** Solid again -- and pushed out of any block he ended inside. */
	public void endPhase(ServerPlayer p) {
		if (!flag(p, F_PHASE)) {
			return;
		}
		setFlag(p, F_PHASE, false);
		setTimer(p, "phase_end", 0);
		PHASED_THROUGH.remove(p.getUUID());
		cooldown(p, PHASE_MOVE, PHASE_COOLDOWN);
		GhostfreakKit.settleOutsideBlocks(p);
		OmnitrixSfx.playOn(p, PHASE, 0.7f, 0.7f, SoundSource.PLAYERS);
	}

	/**
	 * v0.17.2 revamp, Phantom Chill (the show: he phases into a foe and freezes it from the inside): every creature his intangible
	 * body is passing through takes 8 and freezes solid for 2 s -- once each per Phase. Returns how many were frozen.
	 */
	public int phantomChill(ServerPlayer p) {
		Set<Integer> done = PHASED_THROUGH.computeIfAbsent(p.getUUID(), k -> ConcurrentHashMap.newKeySet());
		var box = p.getBoundingBox().inflate(0.15);
		int n = 0;
		for (LivingEntity e : around(p, p.position(), 3.0)) {
			if (!e.getBoundingBox().intersects(box) || !done.add(e.getId())) {
				continue;
			}
			hurt(p, e, PHANTOM_DAMAGE * heat(p), false);
			freezeSolid(p, e, 40);
			QuirkFx.ring(level(p), e.getBoundingBox().getCenter()).style(QuirkFx.RING_SOFT).colours(ICE_PALE, ICE).size(1.5f).thickness(0.4f).life(9).spawn();
			OmnitrixSfx.play(level(p), e.position(), CHILL_THROUGH, 1.0f, 1.0f);
			n++;
		}
		return n;
	}

	// ---------------------------------------------------------------- Z: Ice Dive / Shift+Z: Absolute Zero

	public boolean iceDive(ServerPlayer p) {
		if (flag(p, F_DIVING) || timer(p, "dive_at") > 0 || !use(p, ICE_DIVE)) {
			return false;
		}
		anim(p, ANIM_DIVE);
		long now = p.level().getGameTime();
		p.setDeltaMovement(p.getDeltaMovement().multiply(0.3, 0, 0.3).add(0, 0.9, 0));
		p.hurtMarked = true;
		setTimer(p, "dive_at", now + 8);
		setTimer(p, "dive_end", now + 48);
		OmnitrixSfx.playOn(p, WINGS, 1.2f, 0.8f, SoundSource.PLAYERS);
		updateWings(p, now);
		return true;
	}

	private void tickDive(ServerPlayer p, ServerLevel l, long now) {
		long at = timer(p, "dive_at");
		if (at > 0 && now >= at) {
			setTimer(p, "dive_at", 0);
			setFlag(p, F_DIVING, true);
			if (p.getAbilities().flying) {
				p.getAbilities().flying = false;
				p.onUpdateAbilities();
			}
		}
		if (!flag(p, F_DIVING)) {
			return;
		}
		Vec3 v = p.getDeltaMovement();
		if (v.y > -1.6) {
			p.setDeltaMovement(v.x, Math.min(v.y, -0.9) - 0.15, v.z);
			p.hurtMarked = true;
		}
		if (p.onGround() || p.isInWater() || p.verticalCollisionBelow || now >= timer(p, "dive_end")) {
			diveImpact(p, l);
		}
	}

	/** The Ice Dive lands: r 4, 17 and chilled, a frost crater. Public for tests. */
	public void diveImpact(ServerPlayer p, ServerLevel l) {
		setFlag(p, F_DIVING, false);
		setTimer(p, "dive_end", 0);
		for (LivingEntity e : around(p, p.position(), 4)) {
			hurt(p, e, DIVE_DAMAGE * heat(p), false);
			chill(p, e, 40);
			shove(e, p.position(), 0.7, 0.4);
		}
		anim(p, ANIM_FIELD);
		p.resetFallDistance();
		Vec3 c = p.position();
		QuirkFx.ring(l, c.add(0, 0.05, 0)).style(QuirkFx.RING_SHOCK).colours(ICE_PALE, ICE).size(4.5f).thickness(0.7f).life(12).ground().spawn();
		QuirkFx.decal(l, c.add(0, 0.03, 0)).style(QuirkFx.DECAL_CRACK).colour(0xBFE8FF).size(3.2f).yaw(p.getYRot()).life(160).spawn();
		QuirkFx.shards(l, c.add(0, 0.3, 0)).block(Blocks.PACKED_ICE.defaultBlockState()).size(1.6f).count(14).life(24).spawn();
		frostGround(l, p.blockPosition(), 3);
		QuirkCamera.shakeAround(l, c, 10, 0.35f, 7);
		OmnitrixSfx.play(l, c, FREEZE, 1.4f, 0.7f);
	}

	public boolean absoluteZero(ServerPlayer p) {
		if (!use(p, ABSOLUTE_ZERO)) {
			return false;
		}
		stopBreath(p);
		anim(p, ANIM_ZERO);
		setFlag(p, F_ZERO, true);
		setTimer(p, "zero_at", p.level().getGameTime() + ZERO_WINDUP);
		OmnitrixSfx.playOn(p, BREATH, 1.4f, 0.6f, SoundSource.PLAYERS);
		QuirkCamera.vignette(p, ICE, 0.45f, ZERO_WINDUP + 10);
		updateWings(p, p.level().getGameTime());
		return true;
	}

	/** Absolute Zero goes off: 55 at the centre down to 40 at 8 blocks, frozen solid 4 s, water and fire round him. Public for tests. */
	public void fireAbsoluteZero(ServerPlayer p, ServerLevel l) {
		setFlag(p, F_ZERO, false);
		setTimer(p, "zero_at", 0);
		float h = heat(p);
		for (LivingEntity e : around(p, p.position(), ZERO_RADIUS)) {
			double d = Math.min(1.0, e.distanceTo(p) / ZERO_RADIUS);
			hurt(p, e, (float) (ZERO_DAMAGE - (ZERO_DAMAGE - ZERO_EDGE) * d) * h, false);
			freezeSolid(p, e, 80);
		}
		Vec3 c = p.position();
		QuirkFx.ring(l, c.add(0, 0.05, 0)).style(QuirkFx.RING_SHOCK).colours(ICE_PALE, ICE).size((float) ZERO_RADIUS + 1f).thickness(1.0f).life(16).ground()
				.ease(QuirkFx.Ease.OUT_EXPO).spawn();
		QuirkFx.ring(l, c.add(0, 1.0, 0)).flags(QuirkFx.F_VERTICAL).vec(new Vec3(0, 1, 0)).style(QuirkFx.RING_SOFT).colours(ICE_PALE, ICE)
				.size((float) ZERO_RADIUS).thickness(0.8f).life(14).spawn();
		QuirkFx.decal(l, c.add(0, 0.03, 0)).style(QuirkFx.DECAL_POOL).colour(0xD8F2FF).size((float) ZERO_RADIUS * 0.9f).life(240).spawn();
		QuirkFx.shards(l, c.add(0, 0.5, 0)).block(Blocks.BLUE_ICE.defaultBlockState()).size(3.5f).count(26).life(30).spawn();
		frostGround(l, p.blockPosition(), (int) ZERO_RADIUS);
		QuirkCamera.shakeAround(l, c, 18, 0.6f, 10);
		OmnitrixSfx.play(l, c, ZERO, 1.6f, 1.0f);
	}

	// ---------------------------------------------------------------- V: Cloak / Shift+V: Frost Armour

	public boolean cloak(ServerPlayer p) {
		if (flag(p, F_CLOAK)) {
			endCloak(p);
			return true;
		}
		if (p.isOnFire()) {
			say(p, "message.projecthero.omnitrix.big_chill.too_hot");
			return false;
		}
		if (!use(p, CLOAK_MOVE)) {
			return false;
		}
		anim(p, ANIM_CLOAK);
		setFlag(p, F_CLOAK, true);
		setTimer(p, OPEN_UNTIL, 0);
		setTimer(p, "cloak_end", p.level().getGameTime() + CLOAK_TICKS);
		p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 30, 0, false, false, false));
		for (Mob m : level(p).getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(24))) {
			AlienStatus.loseTrack(m, p);
		}
		OmnitrixSfx.playOn(p, CLOAK, 1.0f, 1.0f, SoundSource.PLAYERS);
		updateWings(p, p.level().getGameTime());
		return true;
	}

	private void endCloak(ServerPlayer p) {
		if (!flag(p, F_CLOAK)) {
			return;
		}
		setFlag(p, F_CLOAK, false);
		setTimer(p, "cloak_end", 0);
		MobEffectInstance inv = p.getEffect(MobEffects.INVISIBILITY);
		if (inv != null && inv.getDuration() <= 30) {
			p.removeEffect(MobEffects.INVISIBILITY); // ours (a longer potion of the player's own is left alone)
		}
	}

	public boolean frostArmour(ServerPlayer p) {
		if (!use(p, FROST_ARMOUR)) {
			return false;
		}
		anim(p, ANIM_ARMOUR);
		setFlag(p, F_ARMOUR, true);
		setTimer(p, "armour_end", p.level().getGameTime() + ARMOUR_TICKS);
		QuirkFx.ring(level(p), p.position().add(0, 0.05, 0)).style(QuirkFx.RING_STEAM).colours(ICE_PALE, ICE).size(1.8f).thickness(0.5f).life(12).ground().spawn();
		OmnitrixSfx.play(level(p), p.position(), FREEZE, 1.0f, 1.3f);
		return true;
	}

	// ---------------------------------------------------------------- C: Blizzard / Shift+C: Frost Field

	public boolean blizzard(ServerPlayer p) {
		if (flag(p, F_BLIZZARD) || !use(p, BLIZZARD)) {
			return false;
		}
		anim(p, ANIM_BLIZZARD);
		setFlag(p, F_BLIZZARD, true);
		setTimer(p, "blizzard_end", p.level().getGameTime() + BLIZZARD_TICKS);
		QuirkFx.ring(level(p), p.position().add(0, 0.05, 0)).style(QuirkFx.RING_STEAM).colours(ICE_PALE, ICE).size((float) BLIZZARD_RADIUS).thickness(0.8f)
				.life(14).ground().spawn();
		OmnitrixSfx.play(level(p), p.position(), STORM, 1.2f, 1.0f);
		updateWings(p, p.level().getGameTime());
		return true;
	}

	private void tickBlizzard(ServerPlayer p, ServerLevel l, long now) {
		long end = timer(p, "blizzard_end");
		long left = end - now;
		if (left > 0 && left % 10 == 0) {
			for (LivingEntity e : around(p, p.position(), BLIZZARD_RADIUS)) {
				hurt(p, e, BLIZZARD_DAMAGE * heat(p), false);
				chill(p, e, 0);
			}
			OmnitrixSfx.play(l, p.position(), STORM, 0.6f, 1.0f);
		}
		if (now >= end) {
			setFlag(p, F_BLIZZARD, false);
			setTimer(p, "blizzard_end", 0);
		}
	}

	/** Where the Frost Field forms: the ground he looks at within 12, else under him. */
	private static Vec3 fieldSpot(ServerPlayer p) {
		Vec3 eye = p.getEyePosition();
		var hit = p.level().clip(new ClipContext(eye, eye.add(p.getLookAngle().scale(FIELD_REACH)), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, p));
		return hit.getType() == HitResult.Type.MISS ? p.position() : hit.getLocation();
	}

	/** Shift+C: frost spreads over the ground he looks at (r 4) for 8 s. True if it formed. */
	public boolean frostField(ServerPlayer p) {
		if (!use(p, FROST_FIELD)) {
			return false;
		}
		anim(p, ANIM_FIELD);
		ServerLevel l = level(p);
		Vec3 at = fieldSpot(p);
		setPos(p, "field", at);
		setFlag(p, F_FIELD, true);
		setTimer(p, FIELD_END, p.level().getGameTime() + FIELD_TICKS);
		for (LivingEntity e : around(p, at, FIELD_RADIUS)) {
			hurt(p, e, FIELD_BURST * heat(p), false);
			chill(p, e, 0);
		}
		frostGround(l, BlockPos.containing(at), (int) FIELD_RADIUS);
		QuirkFx.decal(l, at.add(0, 0.03, 0)).style(QuirkFx.DECAL_POOL).colour(0xDDF4FF).size((float) FIELD_RADIUS + 0.5f).life(FIELD_TICKS).spawn();
		QuirkFx.ring(l, at.add(0, 0.05, 0)).style(QuirkFx.RING_STEAM).colours(ICE_PALE, ICE).size((float) FIELD_RADIUS + 0.5f).thickness(0.6f).life(14).ground()
				.spawn();
		OmnitrixSfx.play(l, at, FREEZE, 1.2f, 0.9f);
		return true;
	}

	private void tickField(ServerPlayer p, ServerLevel l, long now) {
		long end = timer(p, FIELD_END);
		Vec3 at = pos(p, "field");
		if (at == null || now >= end) {
			setFlag(p, F_FIELD, false);
			setTimer(p, FIELD_END, 0);
			return;
		}
		if ((end - now) % 10 == 0) {
			for (LivingEntity e : around(p, at, FIELD_RADIUS)) {
				if (Math.abs(e.getY() - at.y) < 2.0) {
					hurt(p, e, FIELD_DAMAGE * heat(p), false);
					chill(p, e, 0);
				}
			}
		}
	}

	// ---------------------------------------------------------------- damage

	@Override
	public boolean allowDamage(ServerPlayer p, DamageSource source, float amount) {
		if (source.is(DamageTypes.FREEZE) || source.is(DamageTypeTags.IS_FREEZING) || source.is(DamageTypeTags.IS_FALL)) {
			return false; // the cold never touches him; he drifts down on his wings
		}
		if (phasing(p) && (source.is(DamageTypes.IN_WALL) || GhostfreakKit.physical(source))) {
			return false; // intangible
		}
		return true;
	}

	@Override
	public float incomingDamage(ServerPlayer p, DamageSource source, float amount) {
		float out = amount;
		if (source.is(DamageTypeTags.IS_FIRE)) {
			out *= 1.5f; // a creature of the cold: fire hurts him badly
		}
		if (flag(p, F_ARMOUR)) {
			out *= 0.6f;
			if (source.getEntity() instanceof LivingEntity att && att != p && att.distanceToSqr(p) < 16) {
				chill(p, att, 0);
			}
		}
		return out;
	}
}
