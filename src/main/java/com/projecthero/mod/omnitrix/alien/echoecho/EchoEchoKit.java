package com.projecthero.mod.omnitrix.alien.echoecho;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.OmnitrixSfx;
import com.projecthero.mod.omnitrix.alien.AlienKit;
import com.projecthero.mod.omnitrix.alien.AlienMove;
import com.projecthero.mod.omnitrix.alien.AlienStats;
import com.projecthero.mod.omnitrix.alien.AlienStatus;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.data.OmnitrixState;
import com.projecthero.mod.quirk.fx.QuirkCamera;
import com.projecthero.mod.quirk.fx.QuirkFx;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2: Echo Echo, the Sonorosian (Recalibrated Omnitrix). v0.17.2 revamp (docs/OMNITRIX_REVAMP_ECHOECHO.md; user: "can you
 * work on revamps for the rest of the alien force aliens please, look at their powers and abilities in the show and do research
 * and make them good please"): a small white sound-maker whose clones are his whole game -- they scream with every sound move,
 * surround a target and scream inward, take a lethal hit for him, and fill out a chorus for the ultimate. No Ultimate Echo Echo.
 *
 * <pre>
 *   R          Sonic Scream     a sound wave along the look, 14 blocks (7, knocked back); each clone adds 3 at the same spot
 *   Shift+R    Wall of Sound    HOLD up to 2 s: a cone of sound, 9 blocks (3 every 0.5 s, clones +1 each), pushing back and
 *                               stopping shots flying into it; 6 s cooldown from the release
 *   G          Echo Ring        a ring of sound round him (12 in 5) and round every clone (4 in 3)
 *   Shift+G    Surround Sound   every clone runs round the creature you look at (16) and screams inward 0.75 s later: 5 each,
 *                               stunned 1 s (+0.5 s a clone past the first), and Resonating
 *   X          Sonic Leap       a blast at the ground (4 in 2) throws him up and forward; no fall damage for 1.5 s
 *   Shift+X    Echo Swap        changes places with the clone nearest your look (else the farthest)
 *   Z          Sonic Cannon     a focused beam of sound, 24 blocks, through everything in the line (16; clones +3 each); glass and
 *                               ice along it shatter
 *   Shift+Z    Ultimate Chorus  ULTIMATE: splits into a full chorus, 1 s build-up, then a dome of sound (55 centre, 40 at the
 *                               10-block edge, stunned 2 s) and a 4-block ring round every clone (8)
 *   C          Shatter Note     one pitched note at the creature you look at (20): 15 (x1.5 to golems and shulkers), Resonating
 *                               6 s, glass and ice round it shatter
 *   Shift+C    Echolocation     a ping: every creature within 32 glows 8 s
 *   V          Duplicate        splits off a clone (up to 3; 6 health each; they last until he turns back)
 *   Shift+V    Merge            calls the clones back into him (+2 health for each)
 *   Use        Sound Barrier    HOLD right-click (empty hand), up to 3 s: half damage, creatures pushed off, shots turned back
 *   Attack     Chorus Jab       each clone within 6 blocks of the target echoes the punch: +1.5 each
 *   Passives   Sonorosian (small, quick); Chorus (clones scream with him); Resonating (+25% from his sound);
 *              Which One's Real? (lethal damage with a clone up: that clone breaks instead and he stands in its place on 6
 *              health, 90 s)
 *   Weak       water (+25% taken, his sound halved); fragile (16 health, clones break at 6); hollow shell (blasts +50%)
 * </pre>
 */
public final class EchoEchoKit extends AlienKit {
	public static final String ID = Aliens.ECHO_ECHO.id();

	public static EntityType<EchoCloneEntity> CLONE;

	public static final int ANIM_SCREAM = 1;
	public static final int ANIM_BLAST = 2; // Wall of Sound
	public static final int ANIM_RING = 3;
	public static final int ANIM_HUM = 4; // Echolocation
	public static final int ANIM_LEAP = 5;
	public static final int ANIM_SWAP = 6;
	public static final int ANIM_BARRIER = 7;
	public static final int ANIM_ASSAULT = 8; // Surround Sound
	public static final int ANIM_SPLIT = 9;
	public static final int ANIM_MERGE = 10;
	public static final int ANIM_CANNON = 11;
	public static final int ANIM_CHARGE = 12;
	public static final int ANIM_CHORUS = 13;
	public static final int ANIM_NOTE = 14;
	public static final int ANIM_JAB = 15;

	public static final int F_BARRIER = 1;
	public static final int F_CHARGE = 2;
	public static final int F_LEAP = 4;
	public static final int F_WALL = 8;
	public static final int F_SURROUND = 16;

	public static final String SCREAM = "sonic_scream";
	public static final String WALL = "wall_of_sound";
	public static final String RING = "echo_ring";
	public static final String SURROUND = "surround_sound";
	public static final String LEAP = "sonic_leap";
	public static final String SWAP = "echo_swap";
	public static final String CANNON = "sonic_cannon";
	public static final String CHORUS = "ultimate_chorus";
	public static final String NOTE = "shatter_note";
	public static final String ECHOLOCATION = "echolocation";
	public static final String SPLIT = "duplicate";
	public static final String MERGE = "merge";
	public static final String BARRIER = "sound_barrier";
	public static final String JAB = "chorus_jab";
	public static final String DECOY = "which_ones_real";

	public static final String BARRIER_UNTIL = "barrier_until", BARRIER_USE = "barrier_use", LEAP_UNTIL = "leap_until", CHARGE_AT = "charge_at",
			AIM_TARGET = "aim_target", WALL_START = "wall_start", SURROUND_AT = "surround_at";

	public static final int MAX_CLONES = 10; // v0.17.2 (user: "make echo echos clones look the axact same as him and be the same size, reduce the cooldown on his clone to 0.5 seconds and make his max limit for clones be 10" / "make them all have our omnitrix badge aswell")
	public static final float SCREAM_DAMAGE = 7f, CLONE_DAMAGE = 3f;
	public static final double SCREAM_RANGE = 14.0;
	public static final float WALL_PULSE_DAMAGE = 3f, WALL_CLONE_DAMAGE = 1f;
	public static final double WALL_RANGE = 9.0, WALL_DEG = 55.0;
	public static final int WALL_MAX_TICKS = 40, WALL_PULSE = 10, WALL_COOLDOWN = 120;
	public static final float RING_DAMAGE = 12f, RING_CLONE_DAMAGE = 4f;
	public static final double RING_RADIUS = 5.0, RING_CLONE_RADIUS = 3.0;
	public static final float SURROUND_DAMAGE = 5f;
	public static final double SURROUND_RANGE = 16.0;
	public static final int SURROUND_DELAY = 15, SURROUND_STUN = 20, SURROUND_STUN_EXTRA = 10;
	public static final float LEAP_DAMAGE = 4f;
	public static final int LEAP_TICKS = 30;
	public static final float CANNON_DAMAGE = 16f;
	public static final double CANNON_RANGE = 24.0;
	public static final int CHARGE_TICKS = 20;
	public static final float CHORUS_DAMAGE = 55f, CHORUS_EDGE = 40f, CHORUS_RADIUS = 10f, CHORUS_CLONE_DAMAGE = 8f;
	public static final double CHORUS_CLONE_RADIUS = 4.0;
	public static final int CHORUS_STUN = 40;
	public static final float NOTE_DAMAGE = 15f, NOTE_SHELL_FACTOR = 1.5f;
	public static final double NOTE_RANGE = 20.0;
	public static final int RESONANT_TICKS = 120;
	public static final float RESONANT_FACTOR = 1.25f;
	public static final double ECHO_RADIUS = 32.0;
	public static final int ECHO_GLOW = 160;
	public static final int BARRIER_MAX_TICKS = 60, BARRIER_COOLDOWN = 100, BARRIER_GRACE = 6;
	public static final float BARRIER_FACTOR = 0.5f;
	public static final float JAB_DAMAGE = 1.5f;
	public static final double JAB_RANGE = 6.0;
	public static final int DECOY_COOLDOWN = 1800;
	public static final float DECOY_HEALTH = 6f;
	public static final float WATER_TAKEN = 1.25f, WATER_DEALT = 0.5f, BLAST_TAKEN = 1.5f;
	public static final float MERGE_HEAL = 2f;

	public static final OmnitrixSfx.Cue SND_SCREAM = OmnitrixSfx.cue("echo_echo_scream")
			.layer(SoundEvents.WARDEN_SONIC_BOOM, 0.22f, 1.9f)
			.layer(SoundEvents.ALLAY_HURT, 0.3f, 0.7f)
			.layer(SoundEvents.NOTE_BLOCK_BIT.value(), 0.2f, 1.4f, 1);
	public static final OmnitrixSfx.Cue SND_BLAST = OmnitrixSfx.cue("echo_echo_blast")
			.layer(SoundEvents.WARDEN_SONIC_BOOM, 0.35f, 1.5f)
			.layer(SoundEvents.BREEZE_WIND_CHARGE_BURST, 0.3f, 1.2f)
			.layer(SoundEvents.ALLAY_HURT, 0.3f, 0.6f, 1);
	public static final OmnitrixSfx.Cue SND_RING = OmnitrixSfx.cue("echo_echo_ring")
			.layer(SoundEvents.WARDEN_SONIC_CHARGE, 0.3f, 1.8f)
			.layer(SoundEvents.BELL_RESONATE, 0.3f, 1.6f)
			.layer(SoundEvents.WARDEN_SONIC_BOOM, 0.2f, 1.8f, 3);
	public static final OmnitrixSfx.Cue SND_HUM = OmnitrixSfx.cue("echo_echo_hum")
			.layer(SoundEvents.BEACON_AMBIENT, 0.5f, 1.4f)
			.layer(SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), 0.3f, 1.0f)
			.layer(SoundEvents.BELL_RESONATE, 0.25f, 0.8f, 4);
	public static final OmnitrixSfx.Cue SND_SPLIT = OmnitrixSfx.cue("echo_echo_split")
			.layer(SoundEvents.CHICKEN_EGG, 0.4f, 1.6f)
			.layer(SoundEvents.AMETHYST_BLOCK_CHIME, 0.4f, 1.4f)
			.layer(SoundEvents.NOTE_BLOCK_BIT.value(), 0.25f, 1.8f, 2);
	public static final OmnitrixSfx.Cue SND_MERGE = OmnitrixSfx.cue("echo_echo_merge")
			.layer(SoundEvents.AMETHYST_BLOCK_CHIME, 0.4f, 0.9f)
			.layer(SoundEvents.NOTE_BLOCK_BIT.value(), 0.25f, 1.0f, 2);
	public static final OmnitrixSfx.Cue SND_BARRIER = OmnitrixSfx.cue("echo_echo_barrier")
			.layer(SoundEvents.BEACON_ACTIVATE, 0.35f, 1.8f)
			.layer(SoundEvents.WARDEN_SONIC_CHARGE, 0.25f, 1.6f);
	public static final OmnitrixSfx.Cue SND_LEAP = OmnitrixSfx.cue("echo_echo_leap")
			.layer(SoundEvents.BREEZE_JUMP, 0.4f, 1.3f)
			.layer(SoundEvents.WARDEN_SONIC_BOOM, 0.15f, 2.0f);
	public static final OmnitrixSfx.Cue SND_CHORUS = OmnitrixSfx.cue("echo_echo_chorus")
			.layer(SoundEvents.WARDEN_SONIC_BOOM, 0.55f, 1.2f)
			.layer(SoundEvents.WARDEN_SONIC_BOOM, 0.4f, 1.7f, 2)
			.layer(SoundEvents.BELL_RESONATE, 0.5f, 1.3f)
			.layer(SoundEvents.GENERIC_EXPLODE, 0.3f, 1.4f, 3);
	public static final OmnitrixSfx.Cue SND_CHARGE = OmnitrixSfx.cue("echo_echo_charge")
			.layer(SoundEvents.WARDEN_SONIC_CHARGE, 0.45f, 1.5f)
			.layer(SoundEvents.BEACON_POWER_SELECT, 0.2f, 1.8f, 8);
	// v0.17.2 revamp
	public static final OmnitrixSfx.Cue SND_NOTE = OmnitrixSfx.cue("echo_echo_note")
			.layer(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.5f, 2.0f)
			.layer(SoundEvents.BELL_BLOCK, 0.3f, 2.0f)
			.layer(SoundEvents.GLASS_BREAK, 0.3f, 1.4f, 3);
	public static final OmnitrixSfx.Cue SND_SHATTER = OmnitrixSfx.cue("echo_echo_shatter")
			.layer(SoundEvents.GLASS_BREAK, 0.5f, 1.2f)
			.layer(SoundEvents.AMETHYST_CLUSTER_BREAK, 0.35f, 1.5f, 1);
	public static final OmnitrixSfx.Cue SND_PING = OmnitrixSfx.cue("echo_echo_ping")
			.layer(SoundEvents.NOTE_BLOCK_CHIME.value(), 0.35f, 1.9f)
			.layer(SoundEvents.SCULK_CLICKING, 0.4f, 1.2f, 2)
			.layer(SoundEvents.NOTE_BLOCK_CHIME.value(), 0.2f, 1.5f, 8);
	public static final OmnitrixSfx.Cue SND_DECOY = OmnitrixSfx.cue("echo_echo_decoy")
			.layer(SoundEvents.GLASS_BREAK, 0.45f, 1.7f)
			.layer(SoundEvents.CHICKEN_EGG, 0.4f, 1.2f)
			.layer(SoundEvents.AMETHYST_BLOCK_CHIME, 0.4f, 0.7f, 3);

	/** player -> his clones (server entities). */
	private static final Map<UUID, List<EchoCloneEntity>> CLONES = new ConcurrentHashMap<>();
	/** player -> when the Ultimate Chorus goes off. */
	private static final Map<UUID, Long> CHARGES = new ConcurrentHashMap<>();
	/** v0.17.2 revamp: player -> (creature id -> game time its Resonating ends). */
	private static final Map<UUID, Map<Integer, Long>> RESONANT = new ConcurrentHashMap<>();
	/** v0.17.2 revamp: once per game tick (the Wall of Sound pulses must not double up -- mock players can tick twice). */
	private static final Map<UUID, Long> LAST_TICK = new ConcurrentHashMap<>();

	private static final ResourceLocation EARLY = ProjectHeroMod.id("echo_echo_decoy");

	private EchoEchoKit() {
	}

	public static void register() {
		CLONE = Registry.register(BuiltInRegistries.ENTITY_TYPE, ResourceKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id("echo_echo_clone")),
				EntityType.Builder.<EchoCloneEntity>of(EchoCloneEntity::new, MobCategory.MISC).sized(0.36f, 1.1f).noSave().clientTrackingRange(8)
						.updateInterval(1).build("echo_echo_clone"));
		Aliens.register(new EchoEchoKit());
		// v0.17.2 revamp, Which One's Real?: before the Omnitrix failsafe (an early phase), a clone takes the lethal hit for him
		ServerLivingEntityEvents.ALLOW_DEATH.addPhaseOrdering(EARLY, Event.DEFAULT_PHASE);
		ServerLivingEntityEvents.ALLOW_DEATH.register(EARLY, (entity, source, amount) -> {
			if (entity instanceof ServerPlayer p && Aliens.kit(ID) instanceof EchoEchoKit k && k.isEcho(p)) {
				return !k.decoy(p, source);
			}
			return true;
		});
	}

	public static void clearSessionState() {
		CLONES.clear();
		CHARGES.clear();
		RESONANT.clear();
		LAST_TICK.clear();
	}

	@Override
	public String id() {
		return ID;
	}

	/** {@code p} is Echo Echo right now. */
	public boolean isEcho(net.minecraft.world.entity.player.Player p) {
		return Omnitrix.hasPower(p) && Omnitrix.state(p).transformed() && ID.equals(Omnitrix.state(p).active);
	}

	@Override
	public AlienStats stats() {
		// v0.17.2 revamp: fragile -- no armour of his own (only the hidden Resistance I every alien has)
		return AlienStats.builder().health(16).speed(1.15f).scale(0.6f).armor(0, 0).attack(1).safeFall(3).build();
	}

	@Override
	protected List<AlienMove> declareMoves() {
		return List.of(AlienMove.tap(SCREAM, AbilitySlot.SLOT_1, 20), AlienMove.shift(WALL, AbilitySlot.SLOT_1, WALL_COOLDOWN),
				AlienMove.tap(RING, AbilitySlot.SLOT_2, 120), AlienMove.shift(SURROUND, AbilitySlot.SLOT_2, 200),
				AlienMove.tap(LEAP, AbilitySlot.SLOT_3, 30), AlienMove.shift(SWAP, AbilitySlot.SLOT_3, 60),
				AlienMove.tap(CANNON, AbilitySlot.SLOT_4, 160), AlienMove.shift(CHORUS, AbilitySlot.SLOT_4, 1200),
				AlienMove.tap(SPLIT, AbilitySlot.SLOT_5, 10), AlienMove.shift(MERGE, AbilitySlot.SLOT_5, 40),
				AlienMove.tap(NOTE, AbilitySlot.SLOT_6, 140), AlienMove.shift(ECHOLOCATION, AbilitySlot.SLOT_6, 400),
				AlienMove.vanilla(BARRIER, "Hold Use (empty hand)", BARRIER_COOLDOWN), AlienMove.vanilla(JAB, "Attack", 0),
				AlienMove.vanilla(DECOY, "Passive (lethal damage)", DECOY_COOLDOWN));
	}

	// ---------------------------------------------------------------- clones

	/** His live clones (dead / removed ones are dropped first). */
	public static List<EchoCloneEntity> clones(ServerPlayer p) {
		List<EchoCloneEntity> l = CLONES.get(p.getUUID());
		if (l == null) {
			return List.of();
		}
		l.removeIf(c -> c.isRemoved());
		return List.copyOf(l);
	}

	/** Splits off one clone if there is room; null if he already has {@link #MAX_CLONES}. */
	public static EchoCloneEntity addClone(ServerPlayer p) {
		List<EchoCloneEntity> l = CLONES.computeIfAbsent(p.getUUID(), k -> new ArrayList<>());
		l.removeIf(c -> c.isRemoved());
		if (l.size() >= MAX_CLONES) {
			return null;
		}
		Set<Integer> used = new LinkedHashSet<>();
		for (EchoCloneEntity c : l) {
			used.add(c.slot());
		}
		int slot = 0;
		while (used.contains(slot)) {
			slot++;
		}
		EchoCloneEntity c = EchoCloneEntity.spawn(p, slot);
		l.add(c);
		QuirkFx.ring(level(p), c.position().add(0, 0.4, 0)).style(QuirkFx.RING_SOFT).colours(0xFFFFFF, WAVE).size(1.2f).thickness(0.2f).life(8).spawn();
		return c;
	}

	private static void removeClones(ServerPlayer p) {
		List<EchoCloneEntity> l = CLONES.remove(p.getUUID());
		if (l == null) {
			return;
		}
		for (EchoCloneEntity c : l) {
			if (!c.isRemoved()) {
				if (c.level() instanceof ServerLevel sl) {
					sl.sendParticles(ParticleTypes.POOF, c.getX(), c.getY() + 0.5, c.getZ(), 4, 0.1, 0.2, 0.1, 0.01);
				}
				c.discard();
			}
		}
	}

	/** The sound waves' colour (pale blue-white), for the QuirkFx shapes. */
	public static final int WAVE = 0xCFF4FF;

	// ---------------------------------------------------------------- lifecycle

	private static final String[] STATE_TIMERS = { BARRIER_UNTIL, BARRIER_USE, LEAP_UNTIL, CHARGE_AT, AIM_TARGET, WALL_START, SURROUND_AT };

	@Override
	public void onRevert(ServerPlayer p) {
		removeClones(p);
		CHARGES.remove(p.getUUID());
		RESONANT.remove(p.getUUID());
		LAST_TICK.remove(p.getUUID());
		p.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
		// the move state (never the cooldowns, which share the prefix)
		OmnitrixState n = Omnitrix.state(p).copy();
		for (String k : STATE_TIMERS) {
			n.timers.remove(key(k));
		}
		n.kitFlags = 0;
		Omnitrix.save(p, n);
	}

	public static boolean inWater(ServerPlayer p) {
		return p.isInWaterOrBubble();
	}

	@Override
	public boolean allowDamage(ServerPlayer p, DamageSource source, float amount) {
		return !(flag(p, F_LEAP) && source.is(DamageTypeTags.IS_FALL));
	}

	@Override
	public float incomingDamage(ServerPlayer p, DamageSource source, float amount) {
		float a = amount;
		if (inWater(p)) {
			a *= WATER_TAKEN;
		}
		// v0.17.2 revamp (Hollow shell): a light, hollow body -- blasts hit him half again as hard
		if (source.is(DamageTypeTags.IS_EXPLOSION)) {
			a *= BLAST_TAKEN;
		}
		if (flag(p, F_BARRIER) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			a *= BARRIER_FACTOR;
		}
		return a;
	}

	@Override
	public boolean keyActive(net.minecraft.world.entity.player.Player p, AbilitySlot slot) {
		return switch (slot) {
			case SLOT_1 -> flag(p, F_WALL);
			case SLOT_2 -> flag(p, F_SURROUND);
			case SLOT_4 -> flag(p, F_CHARGE);
			default -> false;
		};
	}

	@Override
	public void tick(ServerPlayer p, OmnitrixState s, long now) {
		// v0.17.2: once per game tick (RipjawsKit.tick) -- the pulses below must not double up
		Long last = LAST_TICK.put(p.getUUID(), now);
		if (last != null && last == now) {
			return;
		}
		ServerLevel l = level(p);
		if (flag(p, F_LEAP) && now >= timer(p, LEAP_UNTIL)) {
			setFlag(p, F_LEAP, false);
		}
		tickBarrier(p, l, now);
		tickWall(p, l, now);
		tickSurround(p, l, now);
		Long at = CHARGES.get(p.getUUID());
		if (at != null && now >= at) {
			CHARGES.remove(p.getUUID());
			setFlag(p, F_CHARGE, false);
			chorus(p);
		}
		if (now % 40 == 0) {
			Map<Integer, Long> r = RESONANT.get(p.getUUID());
			if (r != null) {
				r.values().removeIf(t -> t <= now);
				if (r.isEmpty()) {
					RESONANT.remove(p.getUUID());
				}
			}
		}
	}

	// ---------------------------------------------------------------- keys

	@Override
	public void press(ServerPlayer p, AbilitySlot slot, boolean shift) {
		if (flag(p, F_CHARGE)) {
			return;
		}
		switch (slot) {
			case SLOT_1 -> {
				if (shift) {
					wallStart(p);
				} else {
					scream(p);
				}
			}
			case SLOT_2 -> {
				if (shift) {
					surround(p);
				} else {
					ring(p);
				}
			}
			case SLOT_3 -> {
				if (shift) {
					swap(p);
				} else {
					leap(p);
				}
			}
			case SLOT_4 -> {
				if (shift) {
					chorusStart(p);
				} else {
					cannon(p);
				}
			}
			case SLOT_5 -> {
				if (shift) {
					merge(p);
				} else {
					split(p);
				}
			}
			case SLOT_6 -> {
				if (shift) {
					echolocation(p);
				} else {
					note(p);
				}
			}
			default -> {
			}
		}
	}

	@Override
	public void release(ServerPlayer p, AbilitySlot slot, boolean shift, int heldTicks) {
		if (slot == AbilitySlot.SLOT_1 && flag(p, F_WALL)) {
			stopWall(p); // the Wall of Sound runs while Shift+R is held
		}
	}

	private static void say(ServerPlayer p, String key) {
		p.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.GRAY), true);
	}

	/** His sound's damage on {@code e}: halved under water, +25% on a Resonating creature. */
	public float dealt(ServerPlayer p, LivingEntity e, float dmg) {
		float d = inWater(p) ? dmg * WATER_DEALT : dmg;
		return resonating(p, e) ? d * RESONANT_FACTOR : d;
	}

	/** Whether {@code e} is Resonating with {@code p}'s sound right now. */
	public static boolean resonating(ServerPlayer p, LivingEntity e) {
		Map<Integer, Long> r = RESONANT.get(p.getUUID());
		Long until = r == null ? null : r.get(e.getId());
		return until != null && until > p.level().getGameTime();
	}

	/** Marks {@code e} Resonating for {@link #RESONANT_TICKS}. */
	public static void resonate(ServerPlayer p, LivingEntity e) {
		RESONANT.computeIfAbsent(p.getUUID(), k -> new HashMap<>()).put(e.getId(), p.level().getGameTime() + RESONANT_TICKS);
	}

	/** Where the look lands, up to {@code range} (a block or open air). */
	private static Vec3 aimPoint(ServerPlayer p, double range) {
		Vec3 eye = p.getEyePosition();
		Vec3 end = eye.add(p.getLookAngle().scale(range));
		var hit = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		return hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
	}

	/** Creatures whose box comes within {@code radius} of the segment {@code a -> b}. */
	public static List<LivingEntity> line(ServerPlayer p, Vec3 a, Vec3 b, double radius) {
		List<LivingEntity> out = new ArrayList<>();
		Vec3 ab = b.subtract(a);
		double len2 = Math.max(1.0e-6, ab.lengthSqr());
		Vec3 mid = a.add(ab.scale(0.5));
		for (LivingEntity e : around(p, mid, Math.sqrt(len2) * 0.5 + radius + 1.0)) {
			Vec3 c = e.getBoundingBox().getCenter();
			double t = Math.max(0, Math.min(1, c.subtract(a).dot(ab) / len2));
			if (a.add(ab.scale(t)).distanceTo(c) <= radius + e.getBbWidth() * 0.5) {
				out.add(e);
			}
		}
		return out;
	}

	/** Creatures in a cone of {@code deg} degrees, {@code range} long, from {@code from} along {@code dir}. */
	public static List<LivingEntity> cone(ServerPlayer p, Vec3 from, Vec3 dir, double range, double deg) {
		double cos = Math.cos(Math.toRadians(deg / 2.0));
		List<LivingEntity> out = new ArrayList<>();
		for (LivingEntity e : around(p, from, range + 1.0)) {
			Vec3 to = e.getBoundingBox().getCenter().subtract(from);
			double d = to.length();
			if (d <= range + e.getBbWidth() * 0.5 && (d < 1.0 || to.normalize().dot(dir) >= cos)) {
				out.add(e);
			}
		}
		return out;
	}

	/** Glass and ice: what his pitch shatters (real blocks, only where he may build). */
	public static boolean shatterable(BlockState st) {
		return st.is(ConventionalBlockTags.GLASS_BLOCKS) || st.is(ConventionalBlockTags.GLASS_PANES) || st.is(BlockTags.ICE);
	}

	/** Shatters glass / ice within {@code r} of {@code at}. Returns how many broke. */
	public static int shatter(ServerPlayer p, Vec3 at, double r) {
		if (!p.mayBuild()) {
			return 0;
		}
		ServerLevel l = level(p);
		BlockPos c = BlockPos.containing(at);
		int n = (int) Math.ceil(r);
		int broke = 0;
		for (BlockPos bp : BlockPos.betweenClosed(c.offset(-n, -n, -n), c.offset(n, n, n))) {
			if (bp.distToCenterSqr(at) > r * r) {
				continue;
			}
			BlockState st = l.getBlockState(bp);
			if (shatterable(st) && l.mayInteract(p, bp) && broke < 24) {
				l.destroyBlock(bp.immutable(), false, p);
				broke++;
			}
		}
		if (broke > 0) {
			OmnitrixSfx.play(l, at, SND_SHATTER, 1f, 1f);
		}
		return broke;
	}

	// ---------------------------------------------------------------- R / Shift+R

	public boolean scream(ServerPlayer p) {
		if (!use(p, SCREAM)) {
			return false;
		}
		anim(p, ANIM_SCREAM);
		Vec3 eye = p.getEyePosition();
		Vec3 end = aimPoint(p, SCREAM_RANGE);
		for (LivingEntity e : line(p, eye, end, 1.1)) {
			if (hurt(p, e, dealt(p, e, SCREAM_DAMAGE), false)) {
				shove(e, eye, 0.6, 0.15);
			}
		}
		for (EchoCloneEntity c : clones(p)) {
			for (LivingEntity e : line(p, c.mouth(), end, 0.9)) {
				e.invulnerableTime = 0;
				hurt(p, e, dealt(p, e, CLONE_DAMAGE), false);
			}
		}
		OmnitrixSfx.play(level(p), p.position(), SND_SCREAM, 1f, 1f);
		return true;
	}

	/** Shift+R (held): the Wall of Sound -- a held cone of sound in pulses until the keys come up (2 s at most). */
	public boolean wallStart(ServerPlayer p) {
		if (flag(p, F_WALL) || !use(p, WALL)) {
			return false;
		}
		anim(p, ANIM_BLAST);
		setTimer(p, WALL_START, p.level().getGameTime());
		setFlag(p, F_WALL, true);
		OmnitrixSfx.play(level(p), p.position(), SND_BLAST, 1.2f, 1f);
		wallPulse(p, level(p));
		return true;
	}

	public void stopWall(ServerPlayer p) {
		if (!flag(p, F_WALL)) {
			return;
		}
		setFlag(p, F_WALL, false);
		setTimer(p, WALL_START, 0);
		cooldown(p, WALL, WALL_COOLDOWN); // the 6 s run from the release
		if (Omnitrix.state(p).animId == ANIM_BLAST) {
			anim(p, 0);
		}
	}

	private void tickWall(ServerPlayer p, ServerLevel l, long now) {
		if (!flag(p, F_WALL)) {
			return;
		}
		long held = now - timer(p, WALL_START);
		if (held >= WALL_MAX_TICKS) {
			stopWall(p);
			return;
		}
		// shots flying into the cone are stopped dead
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		double cos = Math.cos(Math.toRadians(WALL_DEG / 2.0));
		for (Projectile pr : l.getEntitiesOfClass(Projectile.class, new AABB(eye, eye).inflate(WALL_RANGE), pr -> pr.getOwner() != p)) {
			Vec3 to = pr.position().subtract(eye);
			if (to.length() <= WALL_RANGE && to.normalize().dot(look) >= cos && pr.getDeltaMovement().dot(to) < 0) {
				pr.setDeltaMovement(look.scale(0.4));
				pr.hurtMarked = true;
			}
		}
		if (held > 0 && held % WALL_PULSE == 0) {
			wallPulse(p, l);
		}
	}

	private void wallPulse(ServerPlayer p, ServerLevel l) {
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		for (LivingEntity e : cone(p, eye, look, WALL_RANGE, WALL_DEG)) {
			if (hurt(p, e, dealt(p, e, WALL_PULSE_DAMAGE), false)) {
				shove(e, eye, 0.55, 0.1);
			}
		}
		Vec3 aim = eye.add(look.scale(WALL_RANGE));
		for (EchoCloneEntity c : clones(p)) {
			Vec3 m = c.mouth();
			for (LivingEntity e : cone(p, m, aim.subtract(m).normalize(), WALL_RANGE, WALL_DEG * 0.6)) {
				e.invulnerableTime = 0;
				hurt(p, e, dealt(p, e, WALL_CLONE_DAMAGE), false);
			}
		}
		// the sound rings themselves are drawn by the client (EchoEchoClient's waves while F_WALL is up); a muzzle ring per pulse
		QuirkFx.ring(l, eye.add(look.scale(0.6))).flags(QuirkFx.F_VERTICAL).vec(look).style(QuirkFx.RING_SOFT).colours(0xFFFFFF, WAVE).size(1.4f).thickness(0.15f)
				.life(8).spawn();
		OmnitrixSfx.play(l, p.position(), SND_SCREAM, 0.7f, 0.85f);
	}

	// ---------------------------------------------------------------- G / Shift+G

	public boolean ring(ServerPlayer p) {
		if (!use(p, RING)) {
			return false;
		}
		anim(p, ANIM_RING);
		Vec3 c = p.position().add(0, 0.4, 0);
		for (LivingEntity e : around(p, c, RING_RADIUS)) {
			if (hurt(p, e, dealt(p, e, RING_DAMAGE), false)) {
				shove(e, c, 1.0, 0.3);
			}
		}
		for (EchoCloneEntity cl : clones(p)) {
			for (LivingEntity e : around(p, cl.position(), RING_CLONE_RADIUS)) {
				e.invulnerableTime = 0;
				if (hurt(p, e, dealt(p, e, RING_CLONE_DAMAGE), false)) {
					shove(e, cl.position(), 0.6, 0.2);
				}
			}
		}
		OmnitrixSfx.play(level(p), p.position(), SND_RING, 1.2f, 1f);
		return true;
	}

	/** Shift+G: Surround Sound -- every clone runs round the creature he looks at and screams inward. */
	public boolean surround(ServerPlayer p) {
		List<EchoCloneEntity> cl = clones(p);
		if (cl.isEmpty()) {
			say(p, "message.projecthero.omnitrix.echo_echo.no_clones");
			return false;
		}
		if (flag(p, F_SURROUND)) {
			return false;
		}
		LivingEntity t = lookTarget(p, SURROUND_RANGE);
		if (t == null) {
			say(p, "message.projecthero.omnitrix.echo_echo.no_target");
			return false;
		}
		if (!use(p, SURROUND)) {
			return false;
		}
		anim(p, ANIM_ASSAULT);
		long now = p.level().getGameTime();
		OmnitrixState n = Omnitrix.state(p).copy();
		n.timers.put(key(AIM_TARGET), t.getId() + 1L);
		n.timers.put(key(SURROUND_AT), now + SURROUND_DELAY);
		n.kitFlags |= F_SURROUND;
		Omnitrix.save(p, n);
		// the clones fan out evenly round it
		double r = 1.3 + t.getBbWidth() * 0.5;
		for (int i = 0; i < cl.size(); i++) {
			double a = Math.toRadians(p.getYRot() + 180 + i * 360.0 / cl.size() + (cl.size() == 1 ? 0 : 60));
			Vec3 at = t.position().add(-Math.sin(a) * r, 0, Math.cos(a) * r);
			cl.get(i).dashTo(at, SURROUND_DELAY + 10);
		}
		OmnitrixSfx.play(level(p), p.position(), SND_SPLIT, 0.9f, 1.4f);
		return true;
	}

	private void tickSurround(ServerPlayer p, ServerLevel l, long now) {
		if (!flag(p, F_SURROUND) || now < timer(p, SURROUND_AT)) {
			return;
		}
		long id = timer(p, AIM_TARGET);
		setFlag(p, F_SURROUND, false);
		setTimer(p, SURROUND_AT, 0);
		Entity e = id > 0 ? l.getEntity((int) (id - 1)) : null;
		if (e instanceof LivingEntity t && t.isAlive()) {
			surroundScream(p, t);
		}
	}

	/** The surround's inward scream (tests call it straight): 5 a clone, stunned 1 s (+0.5 s a clone past the first), Resonating. */
	public int surroundScream(ServerPlayer p, LivingEntity t) {
		List<EchoCloneEntity> cl = clones(p);
		ServerLevel l = level(p);
		Vec3 mid = t.position().add(0, t.getBbHeight() * 0.5, 0);
		for (EchoCloneEntity c : cl) {
			t.invulnerableTime = 0;
			hurt(p, t, dealt(p, t, SURROUND_DAMAGE), false);
			QuirkFx.ribbon(l, c.mouth(), mid).style(QuirkFx.RIBBON_LIGHT).colours(0xFFFFFF, WAVE).size(0.35f).life(8).spawn();
		}
		if (!cl.isEmpty()) {
			AlienStatus.stun(p, t, SURROUND_STUN + SURROUND_STUN_EXTRA * (cl.size() - 1));
			resonate(p, t);
			QuirkFx.ring(l, t.position().add(0, 0.1, 0)).style(QuirkFx.RING_SHOCK).colours(0xFFFFFF, WAVE).size(2.4f).thickness(0.3f).life(12).spawn();
			OmnitrixSfx.play(l, t.position(), SND_SCREAM, 1.2f, 1.2f);
		}
		return cl.size();
	}

	// ---------------------------------------------------------------- X / Shift+X

	public boolean leap(ServerPlayer p) {
		if (!use(p, LEAP)) {
			return false;
		}
		anim(p, ANIM_LEAP);
		Vec3 feet = p.position();
		for (LivingEntity e : around(p, feet, 2.0)) {
			if (hurt(p, e, dealt(p, e, LEAP_DAMAGE), false)) {
				shove(e, feet, 0.6, 0.2);
			}
		}
		Vec3 look = p.getLookAngle();
		Vec3 h = new Vec3(look.x, 0, look.z);
		h = h.lengthSqr() < 1.0e-4 ? Vec3.ZERO : h.normalize();
		p.setDeltaMovement(h.x * 0.85, 0.9, h.z * 0.85);
		p.hurtMarked = true;
		p.fallDistance = 0f;
		setTimer(p, LEAP_UNTIL, p.level().getGameTime() + LEAP_TICKS);
		setFlag(p, F_LEAP, true);
		OmnitrixSfx.play(level(p), p.position(), SND_LEAP, 1f, 1f);
		return true;
	}

	/** Shift+X: changes places with the clone nearest the look (else the farthest). */
	public boolean swap(ServerPlayer p) {
		EchoCloneEntity pick = null;
		double best = -2;
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		for (EchoCloneEntity c : clones(p)) {
			Vec3 to = c.mouth().subtract(eye);
			double d = to.lengthSqr() < 1.0e-4 ? 0 : to.normalize().dot(look);
			if (d > 0.9 && d > best) {
				best = d;
				pick = c;
			}
		}
		if (pick == null) {
			for (EchoCloneEntity c : clones(p)) {
				if (pick == null || c.distanceToSqr(p) > pick.distanceToSqr(p)) {
					pick = c;
				}
			}
		}
		if (pick == null) {
			say(p, "message.projecthero.omnitrix.echo_echo.no_clones");
			return false;
		}
		if (!use(p, SWAP)) {
			return false;
		}
		anim(p, ANIM_SWAP);
		Vec3 me = p.position();
		Vec3 it = pick.position();
		p.teleportTo(it.x, it.y, it.z);
		pick.setPos(me.x, me.y, me.z);
		p.fallDistance = 0f;
		QuirkFx.ribbon(level(p), me.add(0, 0.5, 0), it.add(0, 0.5, 0)).style(QuirkFx.RIBBON_LIGHT).colours(0xFFFFFF, WAVE).size(0.25f).life(8).spawn();
		OmnitrixSfx.play(level(p), it, SND_SPLIT, 0.8f, 1.3f);
		return true;
	}

	// ---------------------------------------------------------------- Z / Shift+Z

	public boolean cannon(ServerPlayer p) {
		if (!use(p, CANNON)) {
			return false;
		}
		anim(p, ANIM_CANNON);
		Vec3 eye = p.getEyePosition();
		// the beam passes glass and ice (it shatters them), so its reach is traced through them
		Vec3 end = eye.add(p.getLookAngle().scale(CANNON_RANGE));
		var hit = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		for (int i = 0; i < 6 && hit.getType() == HitResult.Type.BLOCK && shatterable(p.level().getBlockState(hit.getBlockPos())) && p.mayBuild()
				&& p.level().mayInteract(p, hit.getBlockPos()); i++) {
			shatter(p, Vec3.atCenterOf(hit.getBlockPos()), 1.2);
			hit = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		}
		Vec3 stop = hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
		for (LivingEntity e : line(p, eye, stop, 0.9)) {
			if (hurt(p, e, dealt(p, e, CANNON_DAMAGE), false)) {
				shove(e, eye, 1.0, 0.2);
			}
		}
		for (EchoCloneEntity c : clones(p)) {
			for (LivingEntity e : line(p, c.mouth(), stop, 0.7)) {
				e.invulnerableTime = 0;
				hurt(p, e, dealt(p, e, CLONE_DAMAGE), false);
			}
		}
		QuirkCamera.shake(p, 0.15f, 4);
		OmnitrixSfx.play(level(p), p.position(), SND_BLAST, 1.4f, 0.8f);
		return true;
	}

	public boolean chorusStart(ServerPlayer p) {
		if (!use(p, CHORUS)) {
			return false;
		}
		while (addClone(p) != null) {
			// a full chorus
		}
		anim(p, ANIM_CHARGE);
		p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, CHARGE_TICKS, 9, false, false, false));
		CHARGES.put(p.getUUID(), p.level().getGameTime() + CHARGE_TICKS);
		setTimer(p, CHARGE_AT, p.level().getGameTime() + CHARGE_TICKS);
		setFlag(p, F_CHARGE, true);
		OmnitrixSfx.play(level(p), p.position(), SND_CHARGE, 1f, 1f);
		return true;
	}

	/** The Ultimate Chorus going off (tests call it straight). */
	public void chorus(ServerPlayer p) {
		anim(p, ANIM_CHORUS);
		setTimer(p, CHARGE_AT, 0);
		ServerLevel l = level(p);
		Vec3 c = p.position().add(0, 0.5, 0);
		for (LivingEntity e : around(p, c, CHORUS_RADIUS)) {
			double d = Math.min(1.0, e.position().distanceTo(p.position()) / CHORUS_RADIUS);
			float dmg = (float) (CHORUS_DAMAGE + (CHORUS_EDGE - CHORUS_DAMAGE) * d);
			if (hurt(p, e, dealt(p, e, dmg), false)) {
				shove(e, c, 1.6, 0.5);
				AlienStatus.stun(p, e, CHORUS_STUN);
			}
		}
		for (EchoCloneEntity cl : clones(p)) {
			for (LivingEntity e : around(p, cl.position(), CHORUS_CLONE_RADIUS)) {
				e.invulnerableTime = 0;
				hurt(p, e, dealt(p, e, CHORUS_CLONE_DAMAGE), false);
			}
			QuirkFx.ring(l, cl.position().add(0, 0.1, 0)).style(QuirkFx.RING_SHOCK).colours(0xFFFFFF, WAVE).size((float) CHORUS_CLONE_RADIUS).thickness(0.4f)
					.life(14).spawn();
		}
		shatter(p, c, 4.0);
		QuirkFx.ring(l, c.add(0, -0.4, 0)).style(QuirkFx.RING_SHOCK).colours(0xFFFFFF, WAVE).size(CHORUS_RADIUS).thickness(1.0f).life(18).spawn();
		QuirkCamera.shakeAround(l, c, 18, 0.45f, 10);
		OmnitrixSfx.play(l, c, SND_CHORUS, 1.6f, 1f);
	}

	// ---------------------------------------------------------------- V / Shift+V

	public boolean split(ServerPlayer p) {
		if (clones(p).size() >= MAX_CLONES) {
			say(p, "message.projecthero.omnitrix.echo_echo.max_clones");
			return false;
		}
		if (!use(p, SPLIT)) {
			return false;
		}
		anim(p, ANIM_SPLIT);
		addClone(p);
		OmnitrixSfx.play(level(p), p.position(), SND_SPLIT, 1f, 1f);
		return true;
	}

	public boolean merge(ServerPlayer p) {
		int n = clones(p).size();
		if (n == 0) {
			say(p, "message.projecthero.omnitrix.echo_echo.no_clones");
			return false;
		}
		if (!use(p, MERGE)) {
			return false;
		}
		anim(p, ANIM_MERGE);
		removeClones(p);
		p.heal(MERGE_HEAL * n);
		OmnitrixSfx.play(level(p), p.position(), SND_MERGE, 1f, 1f);
		return true;
	}

	// ---------------------------------------------------------------- C / Shift+C

	/** C: Shatter Note -- one pitched note at the creature he looks at; golems and shulkers crack (x1.5); glass round it breaks. */
	public boolean note(ServerPlayer p) {
		LivingEntity t = lookTarget(p, NOTE_RANGE);
		if (t == null) {
			say(p, "message.projecthero.omnitrix.echo_echo.no_target");
			return false;
		}
		if (!use(p, NOTE)) {
			return false;
		}
		anim(p, ANIM_NOTE);
		ServerLevel l = level(p);
		float dmg = NOTE_DAMAGE * (t instanceof IronGolem || t instanceof SnowGolem || t instanceof Shulker ? NOTE_SHELL_FACTOR : 1f);
		hurt(p, t, dealt(p, t, dmg), false);
		resonate(p, t);
		Vec3 mid = t.position().add(0, t.getBbHeight() * 0.5, 0);
		QuirkFx.ribbon(l, p.getEyePosition().add(0, -0.1, 0), mid).style(QuirkFx.RIBBON_LIGHT).colours(0xFFFFFF, WAVE).size(0.2f).life(8).spawn();
		QuirkFx.ring(l, mid).flags(QuirkFx.F_VERTICAL).vec(p.getLookAngle()).style(QuirkFx.RING_SOFT).colours(0xFFFFFF, WAVE).size(1.6f).thickness(0.2f).life(10)
				.spawn();
		shatter(p, mid, 2.5);
		OmnitrixSfx.play(l, t.position(), SND_NOTE, 1f, 1f);
		return true;
	}

	/** Shift+C: Echolocation -- a ping: every creature within 32 blocks glows for 8 s. */
	public boolean echolocation(ServerPlayer p) {
		if (!use(p, ECHOLOCATION)) {
			return false;
		}
		anim(p, ANIM_HUM);
		int n = 0;
		for (LivingEntity e : around(p, p.position(), ECHO_RADIUS)) {
			e.addEffect(new MobEffectInstance(MobEffects.GLOWING, ECHO_GLOW, 0, false, false), p);
			n++;
		}
		QuirkFx.ring(level(p), p.position().add(0, 0.1, 0)).style(QuirkFx.RING_SOFT).colours(0xFFFFFF, WAVE).size(12f).thickness(0.3f).life(20).spawn();
		OmnitrixSfx.play(level(p), p.position(), SND_PING, 1f, 1f);
		return n >= 0;
	}

	// ---------------------------------------------------------------- vanilla keys

	@Override
	public InteractionResult onUse(ServerPlayer p, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND || !p.getMainHandItem().isEmpty() || flag(p, F_CHARGE)) {
			return InteractionResult.PASS;
		}
		return barrierUse(p);
	}

	/** A right-click (empty hand): the Sound Barrier comes up, or stays up while the uses keep coming (vanilla repeats them). */
	public InteractionResult barrierUse(ServerPlayer p) {
		long now = p.level().getGameTime();
		if (!flag(p, F_BARRIER)) {
			if (!ready(p, BARRIER)) {
				return InteractionResult.PASS;
			}
			anim(p, ANIM_BARRIER);
			setTimer(p, BARRIER_UNTIL, now + BARRIER_MAX_TICKS);
			setFlag(p, F_BARRIER, true);
			OmnitrixSfx.play(level(p), p.position(), SND_BARRIER, 1f, 1f);
		}
		setTimer(p, BARRIER_USE, now);
		return InteractionResult.SUCCESS;
	}

	/** Lowers the Sound Barrier and starts its 5 s cooldown. */
	public void lowerBarrier(ServerPlayer p) {
		setFlag(p, F_BARRIER, false);
		setTimer(p, BARRIER_UNTIL, 0);
		setTimer(p, BARRIER_USE, 0);
		cooldown(p, BARRIER, -1);
		if (Omnitrix.state(p).animId == ANIM_BARRIER) {
			anim(p, 0);
		}
	}

	private void tickBarrier(ServerPlayer p, ServerLevel l, long now) {
		if (!flag(p, F_BARRIER)) {
			return;
		}
		long use = timer(p, BARRIER_USE);
		if (now >= timer(p, BARRIER_UNTIL) || now - use > BARRIER_GRACE || now < use) {
			lowerBarrier(p);
			return;
		}
		if (now % 4 == 0) {
			Vec3 c = p.position().add(0, p.getBbHeight() * 0.5, 0);
			for (LivingEntity e : around(p, c, 2.6)) {
				shove(e, c, 0.5, 0.15);
			}
			for (Projectile pr : l.getEntitiesOfClass(Projectile.class, new AABB(c, c).inflate(3.0))) {
				if (pr.getOwner() != p && pr.getDeltaMovement().dot(c.subtract(pr.position())) > 0) {
					pr.setDeltaMovement(pr.getDeltaMovement().scale(-0.6));
					pr.setOwner(p);
					pr.hurtMarked = true;
				}
			}
		}
	}

	@Override
	public boolean onAttack(ServerPlayer p, Entity target) {
		if (target instanceof LivingEntity le) {
			// v0.17.2 revamp: Chorus Jab -- each clone near the target echoes the punch (its own small sound hit)
			anim(p, ANIM_JAB);
			int n = 0;
			for (EchoCloneEntity c : clones(p)) {
				if (c.distanceToSqr(le) <= JAB_RANGE * JAB_RANGE) {
					n++;
				}
			}
			if (n > 0) {
				le.invulnerableTime = 0;
				hurt(p, le, dealt(p, le, JAB_DAMAGE * n), false);
				le.invulnerableTime = 0; // vanilla's punch still lands after it
			}
		}
		return false;
	}

	// ---------------------------------------------------------------- passive: Which One's Real?

	/** The decoy passive is off cooldown (client-safe). */
	public boolean decoyReady(net.minecraft.world.entity.player.Player p) {
		return cooldownLeft(p, DECOY) <= 0;
	}

	/**
	 * v0.17.2 revamp, Which One's Real?: lethal damage while a clone is up -- the clone nearest him breaks instead, and he is
	 * standing where it stood on 6 health. Never against /kill or the void, never on cooldown. True when it saved him.
	 */
	public boolean decoy(ServerPlayer p, DamageSource source) {
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || !decoyReady(p)) {
			return false;
		}
		EchoCloneEntity pick = null;
		for (EchoCloneEntity c : clones(p)) {
			if (pick == null || c.distanceToSqr(p) < pick.distanceToSqr(p)) {
				pick = c;
			}
		}
		if (pick == null) {
			return false;
		}
		ServerLevel l = level(p);
		Vec3 was = p.position();
		Vec3 at = pick.position();
		pick.discard();
		List<EchoCloneEntity> list = CLONES.get(p.getUUID());
		if (list != null) {
			list.removeIf(c -> c.isRemoved());
		}
		p.setHealth(Math.min(p.getMaxHealth(), DECOY_HEALTH));
		p.teleportTo(at.x, at.y, at.z);
		p.fallDistance = 0f;
		p.invulnerableTime = 20;
		cooldown(p, DECOY, DECOY_COOLDOWN);
		for (Mob m : l.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(24))) {
			AlienStatus.loseTrack(m, p);
		}
		QuirkFx.shards(l, was.add(0, 0.4, 0)).colours(0xF2F6F8, 0x9AA8B0).size(1.2f).count(14).spawn();
		OmnitrixSfx.play(l, was, SND_DECOY, 1.2f, 1f);
		p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.echo_echo.decoy").withStyle(ChatFormatting.AQUA), true);
		return true;
	}

	// ---------------------------------------------------------------- tests

	/** Static state held for {@code p} (tests: nothing is left after turning back). */
	public static boolean holds(ServerPlayer p) {
		UUID id = p.getUUID();
		return CLONES.containsKey(id) || CHARGES.containsKey(id) || RESONANT.containsKey(id) || LAST_TICK.containsKey(id);
	}
}
