package com.projecthero.mod.omnitrix.alien.bigchill;

import java.util.List;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.omnitrix.OmnitrixSfx;
import com.projecthero.mod.omnitrix.alien.AlienKit;
import com.projecthero.mod.omnitrix.alien.AlienMove;
import com.projecthero.mod.omnitrix.alien.AlienStats;
import com.projecthero.mod.omnitrix.alien.AlienStatus;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.ghostfreak.GhostfreakKit;
import com.projecthero.mod.omnitrix.alien.jetray.RecalKitMath;
import com.projecthero.mod.omnitrix.data.OmnitrixState;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 (Recalibrated Omnitrix, user: "Just make them basic versions of the aliens for now please that we can revamp later"):
 * Big Chill, the Necrofriggian -- a moth-like ghost of the ice. He breathes freezing ice, flies on moth wings, turns intangible
 * and slips through walls, and wraps his wings round him as a cloak. The cold cannot touch him; fire burns him badly.
 *
 * <pre>
 *   R        Freezing Breath  a cone (8): 12 + chilled; the already chilled   Shift+R  Ice Shard       a shard (20): 7 + slowed
 *                             freeze solid (2 s)
 *   G        Frost Grip       the creature in reach (4.5): 13, frozen 3 s     Shift+G  Frost Nova      a burst (r 5): 14 + chilled
 *   X        Moth Flight      take off / land                                 Shift+X  Phase           intangible 5 s: through walls, no
 *                                                                                                      physical harm (X again ends it)
 *   Z        Ice Dive         climb, then plunge: r 4, 17 + chilled           Shift+Z  Frost Floor     water within 6 freezes over
 *   V        Cloak            the wings wrap round: invisible 10 s, hunters   Shift+V  Frost Armour    6 s: -40% damage, attackers chilled
 *                             lose track (ends on attacking)
 *   C        Blizzard         a 4 s snowstorm round him (r 6): 8 x 2          Shift+C  Absolute Zero   ULTIMATE: 1 s wind-up, r 8: 45, frozen 4 s
 * </pre>
 * "Chilled" creatures are slowed and frost over (vanilla's freezing, synced: the client draws the ice shell on a creature frozen
 * past the powder-snow maximum). Passives: immune to freezing and powder snow, no fall damage. Weakness: fire does x1.5.
 */
public final class BigChillKit extends AlienKit {
	public static final String ID = Aliens.BIG_CHILL.id();
	public static final int ICE = 0x9AE0FF;

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

	// ---------------------------------------------------------------- poses (client: BigChillClient)
	public static final int ANIM_BREATH = 1, ANIM_SHARD = 2, ANIM_GRIP = 3, ANIM_NOVA = 4, ANIM_TAKEOFF = 5, ANIM_PHASE = 6, ANIM_DIVE = 7,
			ANIM_FLOOR = 8, ANIM_CLOAK = 9, ANIM_ARMOUR = 10, ANIM_BLIZZARD = 11, ANIM_ZERO = 12;

	public static final int F_FLYING = 1;
	public static final int F_PHASE = 2;
	public static final int F_CLOAK = 4;
	public static final int F_ARMOUR = 8;
	public static final int F_BLIZZARD = 16;
	public static final int F_ZERO = 32;
	public static final int F_DIVING = 64;

	public static final String BREATH_MOVE = "freezing_breath", ICE_SHARD = "ice_shard", FROST_GRIP = "frost_grip", FROST_NOVA = "frost_nova",
			FLIGHT = "moth_flight", PHASE_MOVE = "phase", ICE_DIVE = "ice_dive", FROST_FLOOR = "frost_floor", CLOAK_MOVE = "cloak",
			FROST_ARMOUR = "frost_armour", BLIZZARD = "blizzard", ABSOLUTE_ZERO = "absolute_zero";

	public static final float BREATH_DAMAGE = 12f;
	public static final double BREATH_RANGE = 8.0;
	public static final float SHARD_DAMAGE = 7f;
	public static final float GRIP_DAMAGE = 13f;
	public static final float NOVA_DAMAGE = 14f;
	public static final float DIVE_DAMAGE = 17f;
	public static final float BLIZZARD_DAMAGE = 2f;
	public static final int BLIZZARD_TICKS = 80;
	public static final double BLIZZARD_RADIUS = 6.0;
	public static final float ZERO_DAMAGE = 45f;
	public static final double ZERO_RADIUS = 8.0;
	public static final int ZERO_WINDUP = 20;
	public static final int PHASE_TICKS = 100;
	public static final int PHASE_COOLDOWN = 200;
	public static final int CLOAK_TICKS = 200;
	public static final int ARMOUR_TICKS = 120;
	public static final float FLY_SPEED = 0.07f;
	/** Chilled: frozen this far (vanilla's powder-snow maximum is 140; it thaws 2 a tick), so it stays "chilled" for 1.5 s. */
	public static final int CHILL = 200;
	/** Frozen solid: from here up the client draws the ice shell ({@code ticksFrozen} is synced). */
	public static final int SHELL_AT = 260;

	public static void register() {
		Aliens.register(new BigChillKit());
	}

	/** Nothing static per player: every running move is kit flags and timers in the synced state. */
	public static void clearSessionState() {
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public AlienStats stats() {
		return AlienStats.builder().health(28).armor(4, 0).attack(3).knockbackResist(0.15f).safeFall(8).build();
	}

	@Override
	protected List<AlienMove> declareMoves() {
		return List.of(AlienMove.tap(BREATH_MOVE, AbilitySlot.SLOT_1, 60), AlienMove.shift(ICE_SHARD, AbilitySlot.SLOT_1, 18),
				AlienMove.tap(FROST_GRIP, AbilitySlot.SLOT_2, 140), AlienMove.shift(FROST_NOVA, AbilitySlot.SLOT_2, 180),
				AlienMove.tap(FLIGHT, AbilitySlot.SLOT_3, 10), AlienMove.shift(PHASE_MOVE, AbilitySlot.SLOT_3, PHASE_COOLDOWN),
				AlienMove.tap(ICE_DIVE, AbilitySlot.SLOT_4, 200), AlienMove.shift(FROST_FLOOR, AbilitySlot.SLOT_4, 200),
				AlienMove.tap(CLOAK_MOVE, AbilitySlot.SLOT_5, 400), AlienMove.shift(FROST_ARMOUR, AbilitySlot.SLOT_5, 300),
				AlienMove.tap(BLIZZARD, AbilitySlot.SLOT_6, 300), AlienMove.shift(ABSOLUTE_ZERO, AbilitySlot.SLOT_6, 900));
	}

	// ---------------------------------------------------------------- lifecycle

	@Override
	public void onTransform(ServerPlayer p) {
		boolean air = !p.onGround() && !p.isInWater() && !p.isPassenger();
		RecalKitMath.wings(p, air, FLY_SPEED);
		setFlag(p, F_FLYING, air);
		p.setTicksFrozen(0);
		OmnitrixSfx.playOn(p, WINGS, 1.0f, 1.0f, SoundSource.PLAYERS);
	}

	@Override
	public void onRevert(ServerPlayer p) {
		boolean wasPhased = flag(p, F_PHASE);
		endCloak(p);
		setFlag(p, F_FLYING | F_PHASE | F_CLOAK | F_ARMOUR | F_BLIZZARD | F_ZERO | F_DIVING, false);
		for (String t : new String[] { "phase_end", "cloak_end", "armour_end", "blizzard_end", "zero_at", "dive_at", "dive_end" }) {
			if (timer(p, t) > 0) {
				setTimer(p, t, 0);
			}
		}
		RecalKitMath.groundWings(p);
		if (wasPhased) {
			GhostfreakKit.settleOutsideBlocks(p);
		}
	}

	@Override
	public boolean keyActive(Player p, AbilitySlot slot) {
		return slot == AbilitySlot.SLOT_3 && (flag(p, F_FLYING) || flag(p, F_PHASE)) || slot == AbilitySlot.SLOT_5 && (flag(p, F_CLOAK) || flag(p, F_ARMOUR))
				|| slot == AbilitySlot.SLOT_6 && flag(p, F_BLIZZARD);
	}

	@Override
	public boolean phasing(Player p) {
		return flag(p, F_PHASE);
	}

	@Override
	public void tick(ServerPlayer p, OmnitrixState s, long now) {
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
		if (now % 30 == 0) {
			l.sendParticles(ParticleTypes.SNOWFLAKE, p.getX(), p.getY() + 1.0, p.getZ(), 1, 0.3, 0.4, 0.3, 0.01); // frost drifting off him
		}
		if (flag(p, F_PHASE) && now >= timer(p, "phase_end")) {
			endPhase(p);
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
		if (flag(p, F_BLIZZARD)) {
			tickBlizzard(p, l, now);
		}
		tickDive(p, l, now);
		if (flag(p, F_ZERO)) {
			p.setDeltaMovement(0, Math.max(0, p.getDeltaMovement().y) * 0.5, 0);
			p.hurtMarked = true;
			if (now % 3 == 0) {
				l.sendParticles(ParticleTypes.SNOWFLAKE, p.getX(), p.getY() + 1.0, p.getZ(), 3, 1.2, 0.8, 1.2, 0.02);
			}
			if (now >= timer(p, "zero_at")) {
				fireAbsoluteZero(p, l);
			}
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
					iceShard(p);
				} else {
					breath(p);
				}
			}
			case SLOT_2 -> {
				if (shift) {
					frostNova(p);
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
					frostFloor(p);
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
					absoluteZero(p);
				} else {
					blizzard(p);
				}
			}
			default -> {
			}
		}
	}

	/** Any attack ends the cloak (the wings open). */
	@Override
	protected void onUse(ServerPlayer p, String moveId) {
		if (flag(p, F_CLOAK) && !moveId.equals(CLOAK_MOVE) && !moveId.equals(FLIGHT) && !moveId.equals(PHASE_MOVE) && !moveId.equals(FROST_FLOOR)) {
			endCloak(p);
		}
	}

	@Override
	public boolean onAttack(ServerPlayer p, Entity target) {
		if (flag(p, F_CLOAK)) {
			endCloak(p);
		}
		if (target instanceof LivingEntity le) {
			chill(p, le, 40); // a frosty touch
		}
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
		e.setTicksFrozen(Math.max(e.getTicksFrozen(), SHELL_AT + ticks * 2));
		AlienStatus.root(by, e, ticks, 30);
	}

	/** R: the freezing breath -- a cone of ice in front. True if it fired. */
	public boolean breath(ServerPlayer p) {
		if (!use(p, BREATH_MOVE)) {
			return false;
		}
		anim(p, ANIM_BREATH);
		ServerLevel l = level(p);
		for (LivingEntity e : RecalKitMath.cone(p, BREATH_RANGE, 24)) {
			hurt(p, e, BREATH_DAMAGE, false);
			chill(p, e, 40);
		}
		Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
		for (int i = 1; i <= 4; i++) {
			Vec3 at = eye.add(look.scale(i * 1.8));
			l.sendParticles(ParticleTypes.SNOWFLAKE, at.x, at.y, at.z, 2, 0.2 * i, 0.2 * i, 0.2 * i, 0.02);
		}
		OmnitrixSfx.play(l, eye, BREATH, 1.1f, 1.0f);
		return true;
	}

	private boolean iceShard(ServerPlayer p) {
		if (!use(p, ICE_SHARD)) {
			return false;
		}
		anim(p, ANIM_SHARD);
		LivingEntity t = RecalKitMath.first(p, p.getLookAngle(), 20.0, 0.4);
		if (t != null) {
			hurt(p, t, SHARD_DAMAGE, false);
			chill(p, t, 0);
			level(p).sendParticles(ParticleTypes.ITEM_SNOWBALL, t.getX(), t.getY() + t.getBbHeight() * 0.6, t.getZ(), 6, 0.2, 0.2, 0.2, 0.05);
		}
		OmnitrixSfx.play(level(p), p.getEyePosition(), SHARD, 1.0f, 1.0f);
		return true;
	}

	private boolean frostGrip(ServerPlayer p) {
		LivingEntity t = lookTarget(p, 4.5);
		if (t == null) {
			p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.projecthero.omnitrix.big_chill.no_target")
					.withStyle(net.minecraft.ChatFormatting.GRAY), true);
			return false;
		}
		if (!use(p, FROST_GRIP)) {
			return false;
		}
		anim(p, ANIM_GRIP);
		hurt(p, t, GRIP_DAMAGE, false);
		freezeSolid(p, t, 60);
		OmnitrixSfx.play(level(p), t.position(), FREEZE, 1.0f, 1.0f);
		return true;
	}

	private boolean frostNova(ServerPlayer p) {
		if (!use(p, FROST_NOVA)) {
			return false;
		}
		anim(p, ANIM_NOVA);
		for (LivingEntity e : around(p, p.position(), 5)) {
			hurt(p, e, NOVA_DAMAGE, false);
			chill(p, e, 30);
			shove(e, p.position(), 0.5, 0.2);
		}
		level(p).sendParticles(ParticleTypes.SNOWFLAKE, p.getX(), p.getY() + 0.8, p.getZ(), 30, 2.5, 0.5, 2.5, 0.05);
		OmnitrixSfx.play(level(p), p.position(), FREEZE, 1.3f, 0.8f);
		return true;
	}

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
			p.setDeltaMovement(p.getDeltaMovement().add(0, 0.4, 0));
			p.hurtMarked = true;
			OmnitrixSfx.playOn(p, WINGS, 1.0f, 1.0f, SoundSource.PLAYERS);
		}
		return true;
	}

	/** Shift+X: intangible for 5 s (Shift+X again ends it early). True if it started or ended. */
	public boolean phase(ServerPlayer p) {
		if (flag(p, F_PHASE)) {
			endPhase(p);
			return true;
		}
		if (!use(p, PHASE_MOVE)) {
			return false;
		}
		anim(p, ANIM_PHASE);
		setFlag(p, F_PHASE, true);
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
		cooldown(p, PHASE_MOVE, PHASE_COOLDOWN);
		GhostfreakKit.settleOutsideBlocks(p);
		OmnitrixSfx.playOn(p, PHASE, 0.7f, 0.7f, SoundSource.PLAYERS);
	}

	// ---------------------------------------------------------------- Z: Ice Dive

	private boolean iceDive(ServerPlayer p) {
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

	/** The Ice Dive lands. Public for tests. */
	public void diveImpact(ServerPlayer p, ServerLevel l) {
		setFlag(p, F_DIVING, false);
		setTimer(p, "dive_end", 0);
		for (LivingEntity e : around(p, p.position(), 4)) {
			hurt(p, e, DIVE_DAMAGE, false);
			chill(p, e, 40);
			shove(e, p.position(), 0.7, 0.4);
		}
		anim(p, ANIM_NOVA);
		p.resetFallDistance();
		l.sendParticles(ParticleTypes.SNOWFLAKE, p.getX(), p.getY() + 0.3, p.getZ(), 30, 2.0, 0.3, 2.0, 0.08);
		OmnitrixSfx.play(l, p.position(), FREEZE, 1.4f, 0.7f);
	}

	/** Shift+Z: the water round him (r 6, a few blocks down) freezes over into frosted ice, which thaws back by itself. */
	private boolean frostFloor(ServerPlayer p) {
		if (!use(p, FROST_FLOOR)) {
			return false;
		}
		anim(p, ANIM_FLOOR);
		ServerLevel l = level(p);
		BlockPos c = p.blockPosition();
		int r = 6;
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				if (dx * dx + dz * dz > r * r) {
					continue;
				}
				for (int dy = 1; dy >= -3; dy--) {
					BlockPos at = c.offset(dx, dy, dz);
					var st = l.getBlockState(at);
					if (st.getBlock() instanceof LiquidBlock && st.getFluidState().is(Fluids.WATER) && st.getFluidState().isSource()
							&& l.getBlockState(at.above()).isAir()) {
						l.setBlockAndUpdate(at, Blocks.FROSTED_ICE.defaultBlockState());
						l.scheduleTick(at, Blocks.FROSTED_ICE, 60 + l.random.nextInt(60));
						break;
					}
				}
			}
		}
		l.sendParticles(ParticleTypes.SNOWFLAKE, p.getX(), p.getY() + 0.2, p.getZ(), 30, 3.0, 0.2, 3.0, 0.02);
		OmnitrixSfx.play(l, p.position(), FREEZE, 1.0f, 1.2f);
		return true;
	}

	// ---------------------------------------------------------------- V: Cloak / Shift+V: Frost Armour

	private boolean cloak(ServerPlayer p) {
		if (flag(p, F_CLOAK)) {
			endCloak(p);
			return true;
		}
		if (!use(p, CLOAK_MOVE)) {
			return false;
		}
		anim(p, ANIM_CLOAK);
		setFlag(p, F_CLOAK, true);
		setTimer(p, "cloak_end", p.level().getGameTime() + CLOAK_TICKS);
		p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 30, 0, false, false, false));
		for (Mob m : level(p).getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(24))) {
			AlienStatus.loseTrack(m, p);
		}
		OmnitrixSfx.playOn(p, CLOAK, 1.0f, 1.0f, SoundSource.PLAYERS);
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

	private boolean frostArmour(ServerPlayer p) {
		if (!use(p, FROST_ARMOUR)) {
			return false;
		}
		anim(p, ANIM_ARMOUR);
		setFlag(p, F_ARMOUR, true);
		setTimer(p, "armour_end", p.level().getGameTime() + ARMOUR_TICKS);
		OmnitrixSfx.play(level(p), p.position(), FREEZE, 1.0f, 1.3f);
		return true;
	}

	// ---------------------------------------------------------------- C: Blizzard / Shift+C: Absolute Zero

	private boolean blizzard(ServerPlayer p) {
		if (flag(p, F_BLIZZARD) || !use(p, BLIZZARD)) {
			return false;
		}
		anim(p, ANIM_BLIZZARD);
		setFlag(p, F_BLIZZARD, true);
		setTimer(p, "blizzard_end", p.level().getGameTime() + BLIZZARD_TICKS);
		OmnitrixSfx.play(level(p), p.position(), STORM, 1.2f, 1.0f);
		return true;
	}

	private void tickBlizzard(ServerPlayer p, ServerLevel l, long now) {
		long end = timer(p, "blizzard_end");
		if (now % 10 == 0) {
			for (LivingEntity e : around(p, p.position(), BLIZZARD_RADIUS)) {
				hurt(p, e, BLIZZARD_DAMAGE, false);
				chill(p, e, 0);
			}
			l.sendParticles(ParticleTypes.SNOWFLAKE, p.getX(), p.getY() + 1.0, p.getZ(), 12, 3.5, 1.0, 3.5, 0.08);
			OmnitrixSfx.play(l, p.position(), STORM, 0.6f, 1.0f);
		}
		if (now >= end) {
			setFlag(p, F_BLIZZARD, false);
			setTimer(p, "blizzard_end", 0);
		}
	}

	private boolean absoluteZero(ServerPlayer p) {
		if (!use(p, ABSOLUTE_ZERO)) {
			return false;
		}
		anim(p, ANIM_ZERO);
		setFlag(p, F_ZERO, true);
		setTimer(p, "zero_at", p.level().getGameTime() + ZERO_WINDUP);
		OmnitrixSfx.playOn(p, BREATH, 1.4f, 0.6f, SoundSource.PLAYERS);
		return true;
	}

	/** Absolute Zero goes off: 45 to everything within 8, frozen solid 4 s. Public for tests. */
	public void fireAbsoluteZero(ServerPlayer p, ServerLevel l) {
		setFlag(p, F_ZERO, false);
		setTimer(p, "zero_at", 0);
		for (LivingEntity e : around(p, p.position(), ZERO_RADIUS)) {
			hurt(p, e, ZERO_DAMAGE, false);
			freezeSolid(p, e, 80);
		}
		l.sendParticles(ParticleTypes.SNOWFLAKE, p.getX(), p.getY() + 1.0, p.getZ(), 60, 4.0, 1.0, 4.0, 0.12);
		OmnitrixSfx.play(l, p.position(), ZERO, 1.6f, 1.0f);
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

	/** Whether {@code e} wears the ice shell (frozen solid by Big Chill; synced, client-safe). */
	public static boolean shelled(LivingEntity e) {
		return e.getTicksFrozen() >= SHELL_AT;
	}
}
