package com.projecthero.mod.omnitrix.alien.ghostfreak;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.mixin.LivingEntityAccessor;
import com.projecthero.mod.mixin.MobGoalsAccessor;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.OmnitrixDamage;
import com.projecthero.mod.omnitrix.OmnitrixSfx;
import com.projecthero.mod.omnitrix.OmnitrixSounds;
import com.projecthero.mod.omnitrix.alien.AlienKit;
import com.projecthero.mod.omnitrix.alien.AlienMove;
import com.projecthero.mod.omnitrix.alien.AlienStats;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.data.OmnitrixState;
import com.projecthero.mod.omnitrix.entity.AlienProjectileEntity;
import com.projecthero.mod.quirk.QuirkThreat;
import com.projecthero.mod.quirk.fx.QuirkCamera;
import com.projecthero.mod.quirk.fx.QuirkFx;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1: Ghostfreak, the Ectonurite -- a pale grey-white phantom striped with black, one purple eye, floating on a wispy tail
 * instead of legs. v0.17.2 (user: "remove the slow fall from ghostfreak but dont make him take fall damage"): he falls at normal speed but never takes fall damage; physical blows do half, he never drowns, and the sun burns him a little.
 *
 * <p>v0.17.2 revamp 2 (docs/OMNITRIX_REVAMP_WILDMUTT_GHOSTFREAK.md, approved; user: "Ghostfreak can posses players for like 10
 * seconds", "Yes replace the soul siphon ability", "make sure he also has an invisibility move", "remove the flight moves from his
 * keybinds and make him fly by double pressing space bar and replace the flight move with another ability"):
 * <pre>
 *   R        Spectral Claw    three-swipe combo: 10, 10, the Reaping Rake 18   Shift+R  Wail            shriek cone: 8, blown back, flee 5 s
 *   G        Possess          dive INTO a mob and STEER it (WASD, look, left-  Shift+G  Phantom Grasp   his claw passes inside a body: 12
 *                             click attacks with it at +50%); 15 s, bosses                              ignoring armour, Slowness + Mining
 *                             throw him out after 3 s; a PLAYER for 10 s (they                          Fatigue 2 s
 *                             lose control to him); G / sneak / a move: burst 14
 *   X        Phase            TOGGLE (no time limit): through walls, up through Shift+X  Ecto Rush       intangible dash ~11 blocks through
 *                             ceilings, down through floors while sneaking;                            walls, floors and bodies (10, weakens)
 *                             pushed out of blocks when it ends; chills 4
 *   Z        Chest Tentacles  up to 3 in front grabbed, constricted (6 + 3x4)  Shift+Z  True Form       ULT: the skin peels open (1 s), terror
 *                                                                                                       burst r 10 (10, flee 4 s); 20 s of
 *                                                                                                       lashing tentacles (4/s x3), claws +30%,
 *                                                                                                       a stare that scares; no physical
 *                                                                                                       resistance, sun burns 5/s
 *   V        Spirit Sight     toggle: night vision, the living outlined        Shift+V  Lights Out      darkness within 12 for 8 s: Darkness,
 *                                                                                                       no outlines / name tags
 *   C        Invisibility     (Fade) TOGGLE, truly unseen: no armour, items,   Shift+C  Fright Aura     10 s: everything near panics, 2/s
 *                             name tag or particles; mobs cannot target him;
 *                             the first blow out of it is an Ambush (+10, blinds)
 *   Attack   Chilling Touch (+2, slows)   Use (empty hand)  Spectral Bolt (5)   Shift+Use (empty hand)  Tendril Snatch (16 blocks: 6, yanked in)
 *   Passives Ghost Flight (double-tap jump, no key; directional, 15 b/s / 30 sprinting), Ectonurite Body, Protective Skin, Creature of the Dark (light 4 or less: +20%
 *            damage, cooldowns 25% faster); weakness: sunlight (5 / s, True Form too), daylight slows cooldowns 15%
 * </pre>
 */
public final class GhostfreakKit extends AlienKit {
	public static final String ID = "ghostfreak";
	public static final int COLOUR = 0xC8D2D2;
	public static final int PURPLE = 0x9A3CFF;

	public static final int ANIM_CLAW = 1;
	public static final int ANIM_SCREAM = 2;
	public static final int ANIM_POSSESS = 3;
	/** v0.17.2 revamp 2: was Soul Siphon's pose; now Phantom Grasp's. */
	public static final int ANIM_GRASP = 4;
	public static final int ANIM_PHASE = 5;
	public static final int ANIM_FLIGHT = 6;
	public static final int ANIM_FADE = 7;
	public static final int ANIM_TENTACLES = 8;
	public static final int ANIM_AURA = 9;
	/** v0.17.2 revamp 2: was Haunt's; now True Form's skin peel. */
	public static final int ANIM_PEEL = 10;
	public static final int ANIM_TOUCH = 11;
	public static final int ANIM_BOLT = 12;
	public static final int ANIM_RAKE = 14;
	public static final int ANIM_RUSH = 15;
	public static final int ANIM_BURST = 16;
	public static final int ANIM_CLAW_BACK = 17;
	public static final int ANIM_WRAP = 18;
	public static final int ANIM_LIGHTS_OUT = 19;
	public static final int ANIM_SNATCH = 20;
	public static final int ANIM_HOST_STRIKE = 21;

	public static final int F_PHASE = 1;
	public static final int F_INVIS = 2;
	/** Flying right now (a double-tap of jump). */
	public static final int F_FLY = 4;
	public static final int F_AURA = 8;
	/** Inside a possessed mob or player. */
	public static final int F_POSSESS = 16;
	public static final int F_RUSH = 64;
	/** v0.17.2: True Form (the skin peeled open) -- from the peel's first frame to the end of the wrap. */
	public static final int F_TRUE = 128;
	/** v0.17.2: Lights Out is on around him. */
	public static final int F_DARK = 256;
	/** v0.17.2: Creature of the Dark is active (light 4 or less, or Lights Out) -- the eye's purple rim. */
	public static final int F_NIGHT = 512;

	public static final String CLAW = "spectral_claw";
	public static final String SCREAM = "scream";
	public static final String POSSESS = "possess";
	public static final String GRASP = "phantom_grasp";
	public static final String PHASE = "phase";
	public static final String RUSH = "ecto_rush";
	/** Ghost Flight is a passive now (double-tap jump), not a move: no key, no entry (user: "remove the flight moves from his keybinds"). */
	public static final String FLIGHT = "ghost_flight";
	public static final String FADE = "fade";
	public static final String TENTACLES = "chest_tentacles";
	public static final String AURA = "fright_aura";
	public static final String TRUE_FORM = "true_form";
	public static final String LIGHTS_OUT = "lights_out";
	public static final String TOUCH = "chilling_touch";
	public static final String BOLT = "spectral_bolt";
	public static final String SNATCH = "tendril_snatch";

	/** The claw combo: two swipes of {@link #CLAW_DAMAGE}, then the Reaping Rake. A press within {@link #COMBO_WINDOW} carries it on. */
	// v0.17.2 (user: "why is ghost freaks damage so high? lower the damage of his R move"): 10 / 18 -> 5 / 9
	// v0.17.2 (user: "can you make the damage of all of ben 10s alien stronger ... make each alien stronger accordingly please"): about x1.6, kept at the low end of each band (he must not be overpowered); old -> new in docs/OMNITRIX_DAMAGE_PASS_PROTOTYPE.md
	public static final float CLAW_DAMAGE = 8f;
	public static final float RAKE_DAMAGE = 14f;
	public static final int CLAW_GAP = 8;
	public static final int RAKE_COOLDOWN = 40;
	public static final int COMBO_WINDOW = 30;
	public static final float SCREAM_DAMAGE = 13f;
	public static final int FEAR_TICKS = 100;
	/** Possess: up to 15 s inside a mob, 10 s inside a player, 3 s before a boss throws him out; the cooldown starts when he leaves. */
	public static final int POSSESS_TICKS = 300;
	public static final int POSSESS_PLAYER_TICKS = 200;
	public static final int POSSESS_BOSS_TICKS = 60;
	public static final int POSSESS_COOLDOWN = 300;
	public static final float BURST_DAMAGE = 22f;
	public static final float BURST_SPLASH = 10f;
	/** A possessed host's blows: +50% of its own damage, one every {@link #HOST_ATTACK_GAP} ticks. */
	public static final float HOST_DAMAGE_FACTOR = 1.5f;
	public static final int HOST_ATTACK_GAP = 10;
	/** Phantom Grasp: {@link #GRASP_DAMAGE} straight through armour, then slowness and mining fatigue for {@link #GRASP_TICKS}. */
	public static final float GRASP_DAMAGE = 20f;
	public static final double GRASP_RANGE = 5;
	public static final int GRASP_TICKS = 40;
	public static final int GRASP_COOLDOWN = 200;
	/**
	 * Phase: v0.17.2 (user: "make ghost freaks phase toggleable and remove the time limit please") -- X on, X off, no time
	 * limit; only a short cooldown after turning it off so it cannot be spammed (was 5 s, then a 10 s cooldown).
	 */
	public static final int PHASE_COOLDOWN = 20;
	public static final float PHASE_CHILL = 6f;
	/** Ecto Rush: {@link #RUSH_TICKS} of {@link #RUSH_SPEED} blocks a tick (about 11 blocks), through walls and bodies. */
	public static final int RUSH_TICKS = 6;
	public static final double RUSH_SPEED = 1.8;
	public static final float RUSH_DAMAGE = 16f;
	public static final int RUSH_COOLDOWN = 120;
	/**
	 * Fade: v0.17.2 (user: "make ghost freaks invisibility toggleable and remove the time limits on it") -- C on, C off (or his
	 * first blow out of it, the Ambush), no time limit; a short cooldown after it ends (was 10 s, then a 20 s cooldown).
	 */
	public static final int FADE_COOLDOWN = 20;
	public static final float AMBUSH_DAMAGE = 16f;
	/** Chest Tentacles: up to {@link #TENTACLE_MAX} grabbed within {@link #TENTACLE_RANGE}, held {@link #TENTACLE_TICKS}. */
	public static final int TENTACLE_MAX = 3;
	public static final double TENTACLE_RANGE = 8;
	public static final int TENTACLE_TICKS = 40;
	public static final float TENTACLE_HIT = 10f;
	public static final float TENTACLE_TICK = 7f;
	public static final int TENTACLE_COOLDOWN = 220;
	/** Tendril Snatch: one creature up to {@link #SNATCH_RANGE} away, {@link #SNATCH_DAMAGE}, yanked in and held {@link #SNATCH_TICKS}. */
	public static final double SNATCH_RANGE = 16;
	public static final float SNATCH_DAMAGE = 10f;
	public static final int SNATCH_TICKS = 20;
	public static final int SNATCH_COOLDOWN = 160;
	/**
	 * True Form: the peel ({@link #PEEL_TICKS}), the terror burst ({@link #TRUE_BURST} within {@link #TRUE_RADIUS}, flee
	 * {@link #TRUE_FEAR}), then {@link #TRUE_TICKS} of lashing ({@link #LASH_DAMAGE} to the nearest {@link #LASH_MAX} within
	 * {@link #LASH_RANGE} every second). The cooldown starts at the end.
	 */
	public static final int PEEL_TICKS = 20;
	public static final int TRUE_TICKS = 400;
	public static final int TRUE_COOLDOWN = 900;
	// v0.17.2 (user: "increase the damage of every alien ultimate that deals damage to be between 40-70 damage"): the terror burst 45
	// v0.17.2 (user: "can you make the damage of all of ben 10s alien stronger ... make each alien stronger accordingly please"): 45 -> 72
	public static final float TRUE_BURST = 72f;
	public static final double TRUE_RADIUS = 10;
	public static final int TRUE_FEAR = 80;
	public static final float LASH_DAMAGE = 7f;
	public static final double LASH_RANGE = 6;
	public static final int LASH_MAX = 3;
	public static final float TRUE_CLAW_FACTOR = 1.3f;
	/** His stare in True Form: a mob held in it for {@link #STARE_TICKS} flees for {@link #STARE_FEAR}. */
	public static final int STARE_TICKS = 20;
	public static final int STARE_FEAR = 60;
	public static final float TRUE_SUN_DAMAGE = 5f;
	/** True Form's skin wraps back on its own in sunlight at this health or below (proposal G4). */
	public static final float TRUE_SUN_FLOOR = 6f;
	/** Lights Out: {@link #DARK_RADIUS} blocks for {@link #DARK_TICKS}. */
	public static final double DARK_RADIUS = 12;
	public static final int DARK_TICKS = 160;
	public static final int DARK_COOLDOWN = 500;
	/** Creature of the Dark: light this low or lower. */
	public static final int DARK_LIGHT = 4;
	public static final float DARK_DAMAGE_FACTOR = 1.2f;
	public static final int AURA_TICKS = 200;
	public static final float AURA_DREAD = 3f;
	public static final float TOUCH_DAMAGE = 2f;
	public static final float BOLT_DAMAGE = 8f;
	public static final float PHYSICAL_FACTOR = 0.5f;
	public static final float SUN_DAMAGE = 5f; // v0.17.2 (user): 5 a second, was 1 every 2 s
	public static final float FLY_SPEED = 0.035f;

	// v0.17.1 sound rework: OmnitrixSfx recipes from vanilla samples (see docs/OMNITRIX_SOUNDS_0171.md)
	/** A claw swipe with a ghostly breath behind it. */
	public static final OmnitrixSfx.Cue SND_CLAW = OmnitrixSfx.cue("ghostfreak_claw")
			.layer(SoundEvents.PLAYER_ATTACK_SWEEP, 0.35f, 1.1f)
			.layer(SoundEvents.PHANTOM_BITE, 0.3f, 1.2f)
			.layer(SoundEvents.SOUL_ESCAPE, 0.25f, 1.0f);
	/** The scream: a low moan and a rushing breath (no shriek). */
	public static final OmnitrixSfx.Cue SND_SCREAM = OmnitrixSfx.cue("ghostfreak_scream")
			.layer(SoundEvents.PHANTOM_AMBIENT, 0.45f, 0.6f)
			.layer(SoundEvents.VEX_CHARGE, 0.3f, 0.7f)
			.layer(SoundEvents.SOUL_ESCAPE, 0.5f, 0.6f)
			.gap(10);
	/** Phasing: a breathy ghostly whoosh. */
	public static final OmnitrixSfx.Cue SND_PHASE = OmnitrixSfx.cue("ghostfreak_phase")
			.layer(SoundEvents.SOUL_ESCAPE, 0.5f, 0.8f)
			.layer(SoundEvents.PHANTOM_FLAP, 0.25f, 0.6f)
			.layer(SoundEvents.BREEZE_INHALE, 0.2f, 0.6f);
	/** The laugh: a low, slowed moan with a whisper on it. */
	public static final OmnitrixSfx.Cue SND_LAUGH = OmnitrixSfx.cue("ghostfreak_laugh")
			.layer(SoundEvents.PHANTOM_AMBIENT, 0.4f, 0.55f)
			.layer(SoundEvents.SOUL_ESCAPE, 0.4f, 0.6f)
			.layer(SoundEvents.VEX_AMBIENT, 0.22f, 0.5f, 3)
			.gap(10);
	/** A tendril lashing out: a wet whoosh. */
	public static final OmnitrixSfx.Cue SND_TENDRIL = OmnitrixSfx.cue("ghostfreak_tendril")
			.layer(SoundEvents.PHANTOM_FLAP, 0.3f, 0.7f)
			.layer(SoundEvents.VEX_CHARGE, 0.22f, 0.6f)
			.layer(SoundEvents.SLIME_SQUISH_SMALL, 0.15f, 0.6f)
			.gap(3);

	static final DustParticleOptions WISP = new DustParticleOptions(new Vector3f(0.85f, 0.88f, 0.9f), 1.0f);
	static final DustParticleOptions VIOLET = new DustParticleOptions(new Vector3f(0.62f, 0.25f, 1.0f), 0.9f);

	/** player -> the mob or player he is inside (and in which dimension). */
	private static final Map<UUID, Possession> POSSESSED = new ConcurrentHashMap<>();
	/** player -> creatures fleeing from him -> until when. */
	private static final Map<UUID, Map<Integer, Long>> FEARED = new ConcurrentHashMap<>();
	/** player -> the creatures his tentacles hold (Chest Tentacles, Tendril Snatch). */
	private static final Map<UUID, Grip> GRIPS = new ConcurrentHashMap<>();
	/** player -> the creatures one Phase (or one Ecto Rush) already went through. */
	private static final Map<UUID, Set<Integer>> PASSED = new ConcurrentHashMap<>();
	/** player -> his Ecto Rush heading. */
	private static final Map<UUID, Vec3> RUSHES = new ConcurrentHashMap<>();
	/** player -> the claw combo's next step (0, 1, 2 = the Rake). */
	private static final Map<UUID, Integer> COMBO = new ConcurrentHashMap<>();
	/** player -> what his True Form stare rests on: {entity id, since}. */
	private static final Map<UUID, long[]> STARE = new ConcurrentHashMap<>();

	private record Possession(ResourceKey<Level> dim, int hostId, boolean player, boolean boss, long until) {
	}

	private record Grip(ResourceKey<Level> dim, List<Integer> held) {
	}

	private GhostfreakKit() {
	}

	public static void register() {
		Aliens.register(new GhostfreakKit());
		AlienProjectileEntity.trail("ghostfreak_bolt", VIOLET);
		AlienProjectileEntity.onHit("ghostfreak_bolt", (proj, hit) -> {
			if (proj.level() instanceof ServerLevel l) {
				Vec3 at = hit.getLocation();
				l.sendParticles(ParticleTypes.SOUL, at.x, at.y, at.z, 6, 0.2, 0.2, 0.2, 0.02);
				if (hit instanceof net.minecraft.world.phys.EntityHitResult eh && eh.getEntity() instanceof LivingEntity le) {
					le.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0));
				}
			}
		});
		// v0.17.2 (user: "Ghostfreak can posses players for like 10 seconds"): the possessed player's client takes Ghostfreak's keys
		PayloadTypeRegistry.playS2C().register(GhostfreakPossessPayload.TYPE, GhostfreakPossessPayload.CODEC);
		// a possessed player who dies, logs out or leaves the dimension is let go at once (the possessor's own exits revert him)
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer victim) {
				releaseVictim(victim);
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			if (handler.getPlayer() != null) {
				releaseVictim(handler.getPlayer());
			}
		});
		ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> releaseVictim(player));
		// and while possessed, their own hands do nothing (the client already takes no keys; this is the server's word on it)
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> !level.isClientSide() && player instanceof ServerPlayer sp
				&& possessorOf(sp) != null ? InteractionResult.FAIL : InteractionResult.PASS);
		UseItemCallback.EVENT.register((player, level, hand) -> !level.isClientSide() && player instanceof ServerPlayer sp && possessorOf(sp) != null
				? InteractionResultHolder.fail(player.getItemInHand(hand)) : InteractionResultHolder.pass(player.getItemInHand(hand)));
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> !level.isClientSide() && player instanceof ServerPlayer sp
				&& possessorOf(sp) != null ? InteractionResult.FAIL : InteractionResult.PASS);
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> !level.isClientSide() && player instanceof ServerPlayer sp
				&& possessorOf(sp) != null ? InteractionResult.FAIL : InteractionResult.PASS);
	}

	public static void clearSessionState() {
		POSSESSED.clear();
		FEARED.clear();
		GRIPS.clear();
		PASSED.clear();
		RUSHES.clear();
		COMBO.clear();
		STARE.clear();
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public AlienStats stats() {
		// v0.17.2 revamp: was 24 health, +2 fist
		// v0.17.2 (user: "give armour ratings to the aliens you think deserve armour ratings please but dont make it terrible"): 4 (blows already do half)
		// v0.17.2 (user set the unarmed melee bonuses: Four Arms +10, Diamondhead +8 (+4 more with the blades), Upgrade +5, Ghostfreak +5, Stinkfly +4, Wildmutt +6, Ripjaws +5, Grey Matter unchanged)
		return AlienStats.builder().health(26f).speed(1.0f).armor(4f, 0f).attack(5f).safeFall(30f).build();
	}

	@Override
	protected List<AlienMove> declareMoves() {
		return List.of(
				AlienMove.tap(CLAW, AbilitySlot.SLOT_1, CLAW_GAP), AlienMove.shift(SCREAM, AbilitySlot.SLOT_1, 240),
				AlienMove.tap(POSSESS, AbilitySlot.SLOT_2, POSSESS_COOLDOWN), AlienMove.shift(GRASP, AbilitySlot.SLOT_2, GRASP_COOLDOWN),
				AlienMove.tap(PHASE, AbilitySlot.SLOT_3, PHASE_COOLDOWN), AlienMove.shift(RUSH, AbilitySlot.SLOT_3, RUSH_COOLDOWN),
				AlienMove.tap(TENTACLES, AbilitySlot.SLOT_4, TENTACLE_COOLDOWN), AlienMove.shift(TRUE_FORM, AbilitySlot.SLOT_4, TRUE_COOLDOWN),
				AlienMove.tap(SIGHT, AbilitySlot.SLOT_5, 40), AlienMove.shift(LIGHTS_OUT, AbilitySlot.SLOT_5, DARK_COOLDOWN),
				// v0.17.2 (user: "make sure he also has an invisibility move"): Fade is the C tap now, the Fright Aura moved to Shift+C
				AlienMove.tap(FADE, AbilitySlot.SLOT_6, FADE_COOLDOWN), AlienMove.shift(AURA, AbilitySlot.SLOT_6, 600),
				AlienMove.vanilla(TOUCH, "Attack", 0), AlienMove.vanilla(BOLT, "Use (empty hand)", 12),
				// v0.17.2 (user: "replace the flight move with another ability"): flight is the double-tap passive; its entry is this
				AlienMove.vanilla(SNATCH, "Shift+Use (empty hand)", SNATCH_COOLDOWN));
	}

	// ---------------------------------------------------------------- lifecycle

	@Override
	public void onTransform(ServerPlayer p) {
		ServerLevel l = level(p);
		// v0.17.2 (user: "i want him to be able fly by double tapping space bar"): always ready to fly, no key
		p.getAbilities().mayfly = true;
		p.getAbilities().setFlyingSpeed(FLY_SPEED);
		p.onUpdateAbilities();
		OmnitrixSounds.play(l, p.position(), SND_LAUGH, 1.0f, 1.0f);
		l.sendParticles(WISP, p.getX(), p.getY() + 1, p.getZ(), 30, 0.4, 0.8, 0.4, 0.0);
	}

	@Override
	public void onRevert(ServerPlayer p) {
		revertV(p);
		endPhase(p, false);
		endRush(p);
		endFade(p);
		endPossession(p, false);
		endTrueForm(p, false);
		endLightsOut(p);
		setFlag(p, F_AURA, false);
		setFlag(p, F_NIGHT, false);
		setTimer(p, "aura_end", 0);
		FEARED.remove(p.getUUID());
		GRIPS.remove(p.getUUID());
		setTimer(p, "tentacles_end", 0);
		COMBO.remove(p.getUUID());
		STARE.remove(p.getUUID());
		setFlag(p, F_FLY, false);
		GameType mode = p.gameMode.getGameModeForPlayer();
		if (mode != GameType.CREATIVE && mode != GameType.SPECTATOR) {
			p.getAbilities().mayfly = false;
			p.getAbilities().flying = false;
		}
		p.getAbilities().setFlyingSpeed(0.05f);
		p.onUpdateAbilities();
		p.resetFallDistance();
	}

	/** Phase or Ecto Rush is on: movement ignores blocks (the framework's collision mixin). */
	@Override
	public boolean phasing(Player p) {
		return flag(p, F_PHASE) || flag(p, F_RUSH);
	}

	/**
	 * v0.17.2 (user: "make sure ghost freaks ... phasing works"): phased, he also goes UP through ceilings (rising, flying up) and
	 * DOWN through floors while sneaking; the Ecto Rush goes through whatever is in its way. Otherwise floors hold him.
	 */
	@Override
	public boolean phasesVertically(Player p, double dy) {
		return flag(p, F_RUSH) || (flag(p, F_PHASE) && (dy > 0.0 || p.isShiftKeyDown()));
	}

	/** Client-safe: a Ghostfreak phased right now (Phase or the Ecto Rush) -- stays upright in walls, no in-wall overlay. */
	public static boolean phasedGhost(@Nullable Entity e) {
		return e instanceof Player pl && ID.equals(Omnitrix.active(pl)) && (Omnitrix.state(pl).kitFlags & (F_PHASE | F_RUSH)) != 0;
	}

	/** Any other move bursts him out of the host he is inside (G itself is the clean way out; Spirit Sight is only his eyes). */
	@Override
	protected void onUse(ServerPlayer p, String moveId) {
		if (!POSSESS.equals(moveId) && !SIGHT.equals(moveId) && POSSESSED.containsKey(p.getUUID())) {
			endPossession(p, true);
		}
	}

	@Override
	public void tick(ServerPlayer p, OmnitrixState s, long now) {
		ServerLevel l = level(p);
		tickV(p, l, now);
		p.setAirSupply(p.getMaxAirSupply()); // nothing to breathe
		// flight: always ready (a double-tap of jump), slow and floaty
		if (!p.getAbilities().mayfly) {
			p.getAbilities().mayfly = true;
			p.getAbilities().setFlyingSpeed(FLY_SPEED);
			p.onUpdateAbilities();
		}
		boolean flying = p.getAbilities().flying;
		if (flying != flag(p, F_FLY)) {
			setFlag(p, F_FLY, flying);
			if (flying && !hidden(p)) {
				anim(p, ANIM_FLIGHT);
				l.sendParticles(WISP, p.getX(), p.getY() + 0.3, p.getZ(), 16, 0.4, 0.2, 0.4, 0.0);
				OmnitrixSfx.playOn(p, SND_PHASE, 0.5f, 1.4f, net.minecraft.sounds.SoundSource.PLAYERS);
			}
		}
		if (now % 4 == 0 && !hidden(p)) {
			l.sendParticles(WISP, p.getX(), p.getY() + 0.2, p.getZ(), 1, 0.15, 0.1, 0.15, 0.0);
		}
		// the sun burns an Ectonurite (not while he hides inside a host, nor during the show transformation)
		if (now % 20 == 0 && !flag(p, F_POSSESS) && !flag(p, F_TRUE) && inSunlight(p) && !com.projecthero.mod.omnitrix.OmnitrixLongTransform.playing(p)) { // v0.17.3 (user): no burn during the show transformation
			p.hurt(p.damageSources().inFire(), SUN_DAMAGE);
			l.sendParticles(ParticleTypes.SMOKE, p.getX(), p.getY() + 1.2, p.getZ(), 8, 0.3, 0.5, 0.3, 0.01);
			if (now % 200 == 0) {
				p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.ghostfreak.sun").withStyle(ChatFormatting.GOLD), true);
			}
		}
		// Creature of the Dark / daylight: the cooldowns run faster in the dark, slower in the sun
		if (now % 20 == 0) {
			boolean night = darkHere(p);
			if (night != flag(p, F_NIGHT)) {
				setFlag(p, F_NIGHT, night);
			}
			shiftCooldowns(p, now, night ? -5 : inSunlight(p) ? 3 : 0);
		}
		// Phase -- v0.17.2 (user: "make ghost freaks phase toggleable and remove the time limit please"): lasts until X again
		if (flag(p, F_PHASE)) {
			p.resetFallDistance();
			passThrough(p, l, PHASE_CHILL, false);
			if (now % 3 == 0) {
				l.sendParticles(ParticleTypes.SOUL, p.getX(), p.getY() + 1, p.getZ(), 1, 0.3, 0.6, 0.3, 0.01);
			}
		}
		tickRush(p, l, now);
		// Fade -- v0.17.2 (user: "make ghost freaks invisibility toggleable and remove the time limits on it"): lasts until C again
		if (flag(p, F_INVIS)) {
			if (now % 20 == 0) {
				p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 40, 0, false, false, false));
			}
			if (now % 5 == 0) {
				loseTrackOfMe(p, 32);
			}
		}
		if (COMBO.containsKey(p.getUUID()) && now > timer(p, "combo_until")) {
			COMBO.remove(p.getUUID());
		}
		tickPossession(p, l, now);
		tickFear(p, l, now);
		tickTentacles(p, l, now);
		tickTrueForm(p, l, now);
		tickLightsOut(p, l, now);
		// Fright Aura
		if (flag(p, F_AURA)) {
			if (now >= timer(p, "aura_end")) {
				setFlag(p, F_AURA, false);
				setTimer(p, "aura_end", 0);
			} else {
				if (now % 20 == 0) {
					for (LivingEntity e : around(p, p.position(), 7)) {
						frighten(p, e, 40);
						e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 0));
						e.invulnerableTime = 0;
						hurt(p, e, dmg(p, AURA_DREAD), false); // v0.17.2: the dread itself hurts
					}
				}
				if (now % 2 == 0 && !hidden(p)) {
					double a = now * 0.3;
					l.sendParticles(VIOLET, p.getX() + Math.cos(a) * 1.6, p.getY() + 0.6, p.getZ() + Math.sin(a) * 1.6, 1, 0, 0.1, 0, 0);
					l.sendParticles(VIOLET, p.getX() - Math.cos(a) * 1.6, p.getY() + 1.4, p.getZ() - Math.sin(a) * 1.6, 1, 0, 0.1, 0, 0);
				}
			}
		}
	}

	/** v0.17.2: the toggles light their HUD boxes while on -- X while phased, C while invisible. */
	@Override
	public boolean keyActive(Player p, AbilitySlot slot) {
		return switch (slot) {
			case SLOT_3 -> flag(p, F_PHASE);
			case SLOT_6 -> flag(p, F_INVIS);
			default -> false;
		};
	}

	/** Faded or inside a host: nothing about him shows (no wisps, no flight puffs). */
	private boolean hidden(ServerPlayer p) {
		return flag(p, F_INVIS) || flag(p, F_POSSESS);
	}

	public static boolean inSunlight(ServerPlayer p) {
		Level l = p.level();
		if (!l.dimensionType().hasSkyLight() || !l.isDay() || p.isInWaterRainOrBubble()) {
			return false;
		}
		BlockPos head = BlockPos.containing(p.getX(), p.getEyeY(), p.getZ());
		return l.canSeeSky(head) && l.getBrightness(net.minecraft.world.level.LightLayer.SKY, head) >= 12;
	}

	/** v0.17.2 Creature of the Dark: the light at his eyes is {@link #DARK_LIGHT} or less (night, caves) -- or his Lights Out is on. */
	public boolean darkHere(ServerPlayer p) {
		if (flag(p, F_DARK)) {
			return true;
		}
		BlockPos eye = BlockPos.containing(p.getX(), p.getEyeY(), p.getZ());
		return p.level().getMaxLocalRawBrightness(eye) <= DARK_LIGHT;
	}

	/** What {@code base} damage comes to from him now: +20% in the dark (Creature of the Dark). */
	public float dmg(ServerPlayer p, float base) {
		return flag(p, F_NIGHT) ? base * DARK_DAMAGE_FACTOR : base;
	}

	/** Moves every running cooldown of his by {@code ticks} (negative = sooner), never past now. One state save. */
	private void shiftCooldowns(ServerPlayer p, long now, int ticks) {
		if (ticks == 0) {
			return;
		}
		OmnitrixState s = Omnitrix.state(p);
		OmnitrixState n = null;
		for (AlienMove m : moves()) {
			String k = key(m.id());
			long at = s.readyAt(k);
			if (at > now + 1) {
				if (n == null) {
					n = s.copy();
				}
				n.timers.put(k, Math.max(now, at + ticks));
			}
		}
		if (n != null) {
			Omnitrix.save(p, n);
		}
	}

	// ---------------------------------------------------------------- keys

	@Override
	public void press(ServerPlayer p, AbilitySlot slot, boolean shift) {
		switch (slot) {
			case SLOT_1 -> {
				if (shift) {
					scream(p);
				} else {
					claw(p);
				}
			}
			case SLOT_2 -> {
				if (shift) {
					phantomGrasp(p, lookTarget(p, GRASP_RANGE));
				} else if (POSSESSED.containsKey(p.getUUID())) {
					endPossession(p, true); // G again: burst out
				} else {
					possess(p, lookTarget(p, 12));
				}
			}
			case SLOT_3 -> {
				if (shift) {
					ectoRush(p);
				} else if (flag(p, F_PHASE)) {
					endPhase(p, true);
				} else {
					phase(p);
				}
			}
			case SLOT_4 -> {
				if (shift) {
					trueForm(p);
				} else {
					tentacles(p);
				}
			}
			case SLOT_5 -> vKey(p, shift); // v0.17.1 (user: "add abilities to the V key for every alien")
			case SLOT_6 -> {
				if (shift) {
					frightAura(p);
				} else if (flag(p, F_INVIS)) {
					endFade(p); // C again: seen again
				} else {
					fade(p);
				}
			}
			default -> {
			}
		}
	}

	/** Living creatures in a cone of {@code deg} degrees, {@code range} blocks in front. */
	public static List<LivingEntity> cone(ServerPlayer p, double range, double deg) {
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		List<LivingEntity> out = new ArrayList<>();
		double cos = Math.cos(Math.toRadians(deg / 2));
		for (LivingEntity e : around(p, eye.add(look.scale(range / 2)), range)) {
			Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eye);
			double d = to.length();
			if (d <= range + e.getBbWidth() * 0.5 && (d < 1.0 || to.normalize().dot(look) >= cos)) {
				out.add(e);
			}
		}
		return out;
	}

	private static boolean boss(ServerPlayer p, LivingEntity e, float threat) {
		return QuirkThreat.add(e, p, threat) || e.getMaxHealth() >= 150f || e instanceof EnderDragon || e instanceof WitherBoss;
	}

	// ---------------------------------------------------------------- R: the claw combo / Shift+R: Wail

	/** The step the next R press will be (0, 1, or 2 = the Reaping Rake). */
	public static int comboStep(ServerPlayer p) {
		return COMBO.getOrDefault(p.getUUID(), 0);
	}

	/** v0.17.2: True Form has peeled off his protective skin -- no framework Resistance while it lasts. */
	@Override
	public boolean toughBody(ServerPlayer p) {
		return !flag(p, F_TRUE);
	}

	/** One claw swipe's damage now: in the dark +20%, in True Form +30%. */
	public float clawDamage(ServerPlayer p, boolean rake) {
		float d = dmg(p, rake ? RAKE_DAMAGE : CLAW_DAMAGE);
		return flag(p, F_TRUE) ? d * TRUE_CLAW_FACTOR : d;
	}

	/** R: one swipe of the combo -- two quick rakes, then the Reaping Rake (wide, heavy, withering, knocks back). */
	public int claw(ServerPlayer p) {
		if (!use(p, CLAW)) {
			return 0;
		}
		int step = comboStep(p);
		boolean rake = step >= 2;
		long now = p.level().getGameTime();
		if (rake) {
			COMBO.remove(p.getUUID());
			setTimer(p, "combo_until", 0);
			cooldown(p, CLAW, RAKE_COOLDOWN);
		} else {
			COMBO.put(p.getUUID(), step + 1);
			setTimer(p, "combo_until", now + COMBO_WINDOW);
		}
		anim(p, rake ? ANIM_RAKE : step == 0 ? ANIM_CLAW : ANIM_CLAW_BACK);
		ServerLevel l = level(p);
		int n = 0;
		float damage = clawDamage(p, rake);
		for (LivingEntity e : cone(p, rake ? 4.5 : 3.5, rake ? 130 : 80)) {
			e.invulnerableTime = 0;
			hurt(p, e, damage, false);
			e.addEffect(new MobEffectInstance(MobEffects.WITHER, rake ? 60 : 40, rake ? 1 : 0));
			if (rake) {
				shove(e, p.position(), 1.1, 0.35);
			}
			l.sendParticles(ParticleTypes.SOUL, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), rake ? 8 : 4, 0.2, 0.3, 0.2, 0.02);
			n++;
		}
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		Vec3 side = look.cross(new Vec3(0, 1, 0));
		side = side.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : side.normalize();
		// each swipe from the other side; the Rake is a wide, low sweep of five
		double dir = step == 1 ? -1 : 1;
		int lines = rake ? 2 : 1;
		for (int k = -lines; k <= lines; k++) {
			double w = rake ? 2.2 : 1.4;
			Vec3 a = eye.add(look.scale(1.0)).add(side.scale(w * dir)).add(0, (rake ? 0.1 : 0.5) + k * 0.22, 0);
			Vec3 b = eye.add(look.scale(rake ? 3.4 : 2.6)).add(side.scale(-w * dir)).add(0, (rake ? -0.2 : -0.6) + k * 0.22, 0);
			QuirkFx.ribbon(l, a, b).colours(0xFFFFFF, PURPLE).size(rake ? 0.26f : 0.18f).life(rake ? 10 : 7).style(QuirkFx.RIBBON_LIGHT).spawn();
		}
		if (rake) {
			QuirkFx.ring(l, eye.add(look.scale(2.0))).colours(0xFFFFFF, PURPLE).size(2.4f).thickness(0.25f).life(9).vec(look)
					.flags(QuirkFx.F_VERTICAL).spawn();
			QuirkCamera.shakeAround(l, p.position(), 6, 0.2f, 6);
		}
		OmnitrixSounds.play(l, p.position(), SND_CLAW, rake ? 1.3f : 1.0f, rake ? 0.7f : 0.9f + p.getRandom().nextFloat() * 0.2f);
		return n;
	}

	public int scream(ServerPlayer p) {
		if (!use(p, SCREAM)) {
			return 0;
		}
		anim(p, ANIM_SCREAM);
		ServerLevel l = level(p);
		int n = 0;
		for (LivingEntity e : cone(p, 10.0, 60)) {
			e.invulnerableTime = 0;
			hurt(p, e, dmg(p, SCREAM_DAMAGE), false);
			e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, FEAR_TICKS, 1));
			shove(e, p.position(), 0.9, 0.25); // v0.17.2: the wail blows them back
			frighten(p, e, FEAR_TICKS);
			n++;
		}
		QuirkFx.cone(l, p.getEyePosition().add(0, -0.1, 0), p.getLookAngle()).colours(0xFFFFFF, PURPLE).size(3.2f).length(10f).life(14)
				.style(QuirkFx.CONE_DEBRIS).spawn();
		for (int i = 1; i <= 3; i++) {
			QuirkFx.ring(l, p.getEyePosition().add(p.getLookAngle().scale(i * 2.5))).colours(0xFFFFFF, PURPLE).size(0.8f + i * 0.7f).thickness(0.2f).life(10)
					.vec(p.getLookAngle()).flags(QuirkFx.F_VERTICAL).spawn();
		}
		QuirkCamera.shakeAround(l, p.position(), 10, 0.25f, 10);
		OmnitrixSounds.play(l, p.position(), SND_SCREAM, 1.3f, 1.0f);
		return n;
	}

	/** Makes {@code e} run from him for {@code ticks} (bosses only note the threat; players are just weakened). */
	public void frighten(ServerPlayer p, LivingEntity e, int ticks) {
		if (boss(p, e, 4f) || !(e instanceof PathfinderMob mob) || POSSESSED.values().stream().anyMatch(ps -> ps.hostId() == e.getId())) {
			return;
		}
		Map<Integer, Long> mine = FEARED.computeIfAbsent(p.getUUID(), k -> new ConcurrentHashMap<>());
		mine.merge(mob.getId(), p.level().getGameTime() + ticks, Math::max);
		flee(mob, p.position());
	}

	private static void flee(PathfinderMob mob, Vec3 from) {
		mob.setTarget(null);
		Vec3 away = DefaultRandomPos.getPosAway(mob, 16, 7, from);
		if (away != null) {
			mob.getNavigation().moveTo(away.x, away.y, away.z, 1.5);
		}
	}

	/** Is {@code mob} fleeing from {@code p} right now? */
	public static boolean fleeing(ServerPlayer p, LivingEntity mob) {
		Map<Integer, Long> mine = FEARED.get(p.getUUID());
		Long until = mine == null ? null : mine.get(mob.getId());
		return until != null && until > p.level().getGameTime();
	}

	private void tickFear(ServerPlayer p, ServerLevel l, long now) {
		Map<Integer, Long> mine = FEARED.get(p.getUUID());
		if (mine == null || mine.isEmpty() || now % 10 != 0) {
			return;
		}
		mine.entrySet().removeIf(en -> {
			if (en.getValue() <= now || !(l.getEntity(en.getKey()) instanceof PathfinderMob mob) || !mob.isAlive()) {
				return true;
			}
			if (mob.getNavigation().isDone() || mob.getTarget() != null) {
				flee(mob, p.position());
			}
			return false;
		});
	}

	// ---------------------------------------------------------------- G: Possess (steering the host) / Shift+G: Phantom Grasp

	/**
	 * Client-safe: is {@code e} a Ghostfreak riding inside a possessed host right now? The seat ({@code OmnitrixRiding#seat})
	 * sinks him into its body with his eyes at its eyes.
	 */
	public static boolean insideHost(@Nullable Entity e) {
		return e instanceof Player pl && ID.equals(Omnitrix.active(pl)) && (Omnitrix.state(pl).kitFlags & F_POSSESS) != 0;
	}

	/**
	 * G: dives INTO {@code target} and takes it over. A mob: he steers it (WASD walks it the way he faces, jump jumps it, it looks
	 * where he looks, left-click strikes with it at +50% of its own damage) for up to 15 s -- a boss throws him out after 3 s.
	 * A player: they lose control to him for {@link #POSSESS_PLAYER_TICKS} (their client plays his keys instead of theirs).
	 * Inside, he is invisible and untouchable. G again, sneaking, or any other move bursts him out ({@link #BURST_DAMAGE});
	 * the host's death, turning back, or a player's time running out lets him slip out quietly.
	 */
	public boolean possess(ServerPlayer p, @Nullable LivingEntity target) {
		if (target == null || target.isPassenger() || target.isVehicle()) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.ghostfreak.no_host").withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		boolean player = target instanceof Player;
		if (player) {
			if (!(target instanceof ServerPlayer victim) || victim.gameMode.isCreative() || victim.gameMode.getGameModeForPlayer() == GameType.SPECTATOR || !victim.isAlive()
					|| possessorOf(victim) != null || com.projecthero.mod.hulk.Hulk.isHulk(victim)
					|| (com.projecthero.mod.squad.Squads.areAllies(p, victim) && !com.projecthero.mod.squad.Squads.friendlyFireOn(p))) {
				p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.ghostfreak.cant_possess_player").withStyle(ChatFormatting.GRAY), true);
				return false;
			}
		} else if (!(target instanceof Mob) || target instanceof EnderDragon || target instanceof WitherBoss) {
			p.displayClientMessage(Component.translatable(target instanceof Mob ? "message.projecthero.omnitrix.ghostfreak.too_strong"
					: "message.projecthero.omnitrix.ghostfreak.no_host").withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		if (POSSESSED.containsKey(p.getUUID()) || !use(p, POSSESS)) {
			return false;
		}
		boolean boss = !player && boss(p, target, 6f);
		int ticks = player ? POSSESS_PLAYER_TICKS : boss ? POSSESS_BOSS_TICKS : POSSESS_TICKS;
		endPhase(p, false);
		endRush(p);
		if (p.isPassenger()) {
			p.stopRiding();
		}
		p.getAbilities().flying = false;
		p.onUpdateAbilities();
		long now = p.level().getGameTime();
		POSSESSED.put(p.getUUID(), new Possession(p.level().dimension(), target.getId(), player, boss, now + ticks));
		setFlag(p, F_POSSESS, true); // before he rides: the seat is worked out from it
		if (!p.startRiding(target, true)) {
			POSSESSED.remove(p.getUUID());
			setFlag(p, F_POSSESS, false);
			cooldown(p, POSSESS, 0);
			return false;
		}
		anim(p, ANIM_POSSESS);
		ServerLevel l = level(p);
		QuirkFx.ribbon(l, p.position().add(0, 1.2, 0), target.position().add(0, target.getBbHeight() * 0.6, 0)).colours(0xFFFFFF, PURPLE).size(0.45f).life(12)
				.style(QuirkFx.RIBBON_SHADOW).spawn();
		setTimer(p, "possess_end", now + ticks);
		cooldown(p, POSSESS, ticks); // the real cooldown starts when he leaves it
		p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetPassengersPacket(target));
		p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 40, 0, false, false, false));
		if (target instanceof Mob mob) {
			mob.setTarget(null);
			takeOver(mob, true);
			mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, ticks, 0, false, false, false)); // the host's faint outline
			if (boss) {
				p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.ghostfreak.boss_fights").withStyle(ChatFormatting.GOLD), true);
			}
		} else if (target instanceof ServerPlayer victim) {
			victim.setSprinting(false);
			sendControl(p, victim, ticks + now);
			victim.displayClientMessage(Component.translatable("message.projecthero.omnitrix.ghostfreak.possessed_you").withStyle(ChatFormatting.DARK_PURPLE), true);
		}
		l.sendParticles(ParticleTypes.SOUL, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 16, 0.3, 0.5, 0.3, 0.03);
		OmnitrixSounds.play(l, target.position(), SND_LAUGH, 1.0f, 0.8f);
		return true;
	}

	/** The mob's own legs, eyes and jumps go to him (or back to it): its goals that move, look or jump are switched off. */
	private static void takeOver(Mob mob, boolean on) {
		GoalSelector goals = ((MobGoalsAccessor) mob).projecthero$goalSelector();
		for (Goal.Flag f : new Goal.Flag[] { Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP }) {
			if (on) {
				goals.disableControlFlag(f);
			} else {
				goals.enableControlFlag(f);
			}
		}
		if (on) {
			mob.getNavigation().stop();
		}
	}

	/** The mob he is inside (null if none, or a player). */
	@Nullable
	public static Mob possessed(ServerPlayer p) {
		return host(p) instanceof Mob m ? m : null;
	}

	/** The mob or player he is inside right now, or null. */
	@Nullable
	public static LivingEntity host(ServerPlayer p) {
		Possession ps = POSSESSED.get(p.getUUID());
		if (ps == null || ps.dim() != p.level().dimension()) {
			return null;
		}
		return p.serverLevel().getEntity(ps.hostId()) instanceof LivingEntity le && le.isAlive() ? le : null;
	}

	/** The Ghostfreak inside {@code victim} right now, or null. */
	@Nullable
	public static ServerPlayer possessorOf(ServerPlayer victim) {
		if (POSSESSED.isEmpty()) {
			return null;
		}
		for (Map.Entry<UUID, Possession> en : POSSESSED.entrySet()) {
			Possession ps = en.getValue();
			if (ps.player() && ps.hostId() == victim.getId() && ps.dim() == victim.level().dimension()) {
				return victim.server.getPlayerList().getPlayer(en.getKey());
			}
		}
		return null;
	}

	/** {@code victim} died, logged out or left the dimension: whoever possesses them slips out at once. */
	public static void releaseVictim(ServerPlayer victim) {
		ServerPlayer gf = possessorOf(victim);
		if (gf != null && Aliens.kit(ID) instanceof GhostfreakKit kit) {
			kit.endPossession(gf, false); // tells the victim's client too
		}
	}

	private static void sendControl(ServerPlayer gf, ServerPlayer victim, long until) {
		if (ServerPlayNetworking.canSend(victim, GhostfreakPossessPayload.TYPE)) {
			ServerPlayNetworking.send(victim, new GhostfreakPossessPayload(gf.getId(), until, gf.zza, gf.xxa, ((LivingEntityAccessor) gf).projecthero$isJumping(),
					gf.getYRot(), gf.getXRot()));
		}
	}

	/** Ghostfreak's movement keys turned into a direction on the ground ({@code zza} forward, {@code xxa} left), like vanilla travel. */
	public static Vec3 steer(float forward, float strafe, float yawDeg) {
		Vec3 in = new Vec3(strafe, 0, forward);
		if (in.lengthSqr() < 1.0e-3) {
			return Vec3.ZERO;
		}
		in = in.lengthSqr() > 1.0 ? in.normalize() : in;
		float s = (float) Math.sin(Math.toRadians(yawDeg));
		float c = (float) Math.cos(Math.toRadians(yawDeg));
		return new Vec3(in.x * c - in.z * s, 0, in.z * c + in.x * s);
	}

	private void tickPossession(ServerPlayer p, ServerLevel l, long now) {
		Possession ps = POSSESSED.get(p.getUUID());
		if (ps == null) {
			return;
		}
		LivingEntity host = host(p);
		if (host == null) {
			endPossession(p, false); // the host died (or is gone): he simply drifts out
			return;
		}
		if (p.getVehicle() != host) {
			endPossession(p, true); // he sneaked out: he bursts out through it
			return;
		}
		if (now >= ps.until()) {
			if (ps.boss()) {
				throwOut(p, host); // its will is too strong to hold for long
			} else {
				endPossession(p, !ps.player()); // a mob is torn open; a player's 10 s just end
			}
			return;
		}
		if (now % 20 == 0) {
			p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 40, 0, false, false, false));
		}
		p.resetFallDistance();
		boolean jump = ((LivingEntityAccessor) p).projecthero$isJumping();
		if (host instanceof Mob mob) {
			steerMob(p, mob, p.zza, p.xxa, jump);
		} else if (host instanceof ServerPlayer victim) {
			victim.setSprinting(false);
			sendControl(p, victim, ps.until());
		}
		if (now % 6 == 0) {
			l.sendParticles(VIOLET, host.getX(), host.getY() + host.getBbHeight() * 0.7, host.getZ(), 1, host.getBbWidth() * 0.4, 0.3, host.getBbWidth() * 0.4, 0);
		}
	}

	/** One tick of steering a possessed mob: where he walks it, whether it jumps, where it looks. */
	private static void steerMob(ServerPlayer p, Mob mob, float forward, float strafe, boolean jump) {
		takeOver(mob, true);
		if (mob.getTarget() != null) {
			mob.setTarget(null);
		}
		mob.getNavigation().stop();
		Vec3 look = p.getLookAngle();
		Vec3 d = steer(forward, strafe, p.getYRot());
		if (d.lengthSqr() > 1.0e-3) {
			double y = mob.getY();
			if (mob.getMoveControl() instanceof FlyingMoveControl) {
				y += look.y * 3.0 * Math.signum(forward == 0f ? 1f : forward);
			}
			mob.getMoveControl().setWantedPosition(mob.getX() + d.x * 3.0, y, mob.getZ() + d.z * 3.0, 1.0);
			if (mob.horizontalCollision && mob.onGround()) {
				mob.getJumpControl().jump(); // up a step or a block
			}
		} else {
			mob.getMoveControl().setWantedPosition(mob.getX(), mob.getY(), mob.getZ(), 0.0);
			mob.setYRot(p.getYRot());
			mob.yBodyRot = p.getYRot();
		}
		if (jump && mob.onGround()) {
			mob.getJumpControl().jump();
		}
		Vec3 eye = mob.getEyePosition();
		mob.getLookControl().setLookAt(eye.x + look.x * 8, eye.y + look.y * 8, eye.z + look.z * 8);
	}

	/**
	 * Left-click while possessing: the HOST strikes what he looks at (its own blow, +50%) -- a creeper lights its fuse, an archer
	 * looses at it. Returns whether a blow landed.
	 */
	public boolean hostStrike(ServerPlayer p, @Nullable Entity clicked) {
		LivingEntity host = host(p);
		if (host == null) {
			return false;
		}
		long now = p.level().getGameTime();
		if (now < timer(p, "host_strike")) {
			return false;
		}
		LivingEntity target = lookTarget(p, host instanceof RangedAttackMob ? 24 : 4.5);
		if (target == null && clicked instanceof LivingEntity le && le != host && le != p) {
			target = le;
		}
		setTimer(p, "host_strike", now + HOST_ATTACK_GAP);
		host.swing(InteractionHand.MAIN_HAND, true);
		if (host instanceof Creeper creeper) {
			creeper.ignite();
			return true;
		}
		if (target == null) {
			return false;
		}
		if (host instanceof RangedAttackMob archer && host instanceof Mob) {
			archer.performRangedAttack(target, 1.0f);
			return true;
		}
		double base = host.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE) ? host.getAttributeValue(Attributes.ATTACK_DAMAGE) : 1.0;
		float amount = (float) Math.max(1.0, base) * HOST_DAMAGE_FACTOR;
		DamageSource src = host instanceof Player hp ? host.damageSources().playerAttack(hp) : host.damageSources().mobAttack(host);
		target.invulnerableTime = Math.min(target.invulnerableTime, 10);
		boolean hit = target.hurt(src, amount);
		if (hit) {
			shove(target, host.position(), 0.4, 0.2);
		}
		return hit;
	}

	/** A boss throws him out: he is flung clear, quietly (no burst), and told so. */
	private void throwOut(ServerPlayer p, LivingEntity host) {
		endPossession(p, false);
		Vec3 away = p.position().subtract(host.position()).multiply(1, 0, 1);
		away = away.lengthSqr() < 1.0e-4 ? p.getLookAngle().multiply(-1, 0, -1) : away;
		away = away.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : away.normalize();
		p.setDeltaMovement(away.x * 1.2, 0.5, away.z * 1.2);
		p.hurtMarked = true;
		p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.ghostfreak.thrown_out").withStyle(ChatFormatting.GOLD), true);
	}

	/** Out of the host: a {@code burst} tears through it (and splashes around it); otherwise he slips out quietly. */
	public void endPossession(ServerPlayer p, boolean burst) {
		Possession ps = POSSESSED.remove(p.getUUID());
		if (ps == null) {
			return;
		}
		setFlag(p, F_POSSESS, false);
		setTimer(p, "possess_end", 0);
		setTimer(p, "host_strike", 0);
		if (cooldownLeft(p, POSSESS) < POSSESS_COOLDOWN) {
			cooldown(p, POSSESS, POSSESS_COOLDOWN);
		}
		Entity vehicle = p.getVehicle();
		if (vehicle != null && vehicle.getId() == ps.hostId()) {
			p.stopRiding();
		}
		p.resetFallDistance();
		if (!flag(p, F_INVIS)) {
			MobEffectInstance inv = p.getEffect(MobEffects.INVISIBILITY);
			if (inv != null && inv.getDuration() <= 40) {
				p.removeEffect(MobEffects.INVISIBILITY);
			}
		}
		if (!(p.server.getLevel(ps.dim()) instanceof ServerLevel l) || !(l.getEntity(ps.hostId()) instanceof LivingEntity host)) {
			return;
		}
		if (host instanceof Mob mob) {
			takeOver(mob, false);
			mob.setTarget(null);
			mob.removeEffect(MobEffects.GLOWING);
		} else if (host instanceof ServerPlayer victim) {
			if (ServerPlayNetworking.canSend(victim, GhostfreakPossessPayload.TYPE)) {
				ServerPlayNetworking.send(victim, GhostfreakPossessPayload.RELEASE);
			}
			if (victim.isAlive()) {
				victim.displayClientMessage(Component.translatable("message.projecthero.omnitrix.ghostfreak.released_you").withStyle(ChatFormatting.GRAY), true);
			}
		}
		l.sendParticles(ParticleTypes.SOUL, host.getX(), host.getY() + host.getBbHeight() * 0.5, host.getZ(), 10, 0.3, 0.5, 0.3, 0.03);
		if (!burst || !host.isAlive()) {
			return;
		}
		anim(p, ANIM_BURST);
		Vec3 at = host.position().add(0, host.getBbHeight() * 0.5, 0);
		host.invulnerableTime = 0;
		host.hurt(OmnitrixDamage.alienStrike(p), dmg(p, BURST_DAMAGE)); // AlienKit.hurt skips riding partners; he has left it now anyway
		for (LivingEntity e : around(p, at, 3)) {
			if (e != host) {
				e.invulnerableTime = 0;
				hurt(p, e, dmg(p, BURST_SPLASH), false);
				shove(e, at, 0.8, 0.3);
			}
		}
		Vec3 out = p.getLookAngle().multiply(1, 0, 1);
		p.push(out.x * 0.6, 0.45, out.z * 0.6);
		p.hurtMarked = true;
		QuirkFx.blast(l, at).colours(0xF0E8FF, PURPLE).size(2.2f).life(12).flags(QuirkFx.F_NO_SMOKE).spawn();
		l.sendParticles(ParticleTypes.SOUL, at.x, at.y, at.z, 16, 0.4, 0.5, 0.4, 0.08);
		OmnitrixSounds.play(l, at, SND_SCREAM, 1.0f, 1.3f);
	}

	/**
	 * Shift+G: Phantom Grasp -- his claw passes INTO {@code t} and squeezes: {@link #GRASP_DAMAGE} straight through armour, and
	 * slowness and mining fatigue for 2 s. Returns the damage dealt (0 if nothing in reach).
	 */
	public float phantomGrasp(ServerPlayer p, @Nullable LivingEntity t) {
		if (t == null) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.ghostfreak.no_host").withStyle(ChatFormatting.GRAY), true);
			return 0f;
		}
		if (!use(p, GRASP)) {
			return 0f;
		}
		anim(p, ANIM_GRASP);
		ServerLevel l = level(p);
		float before = t.getHealth();
		t.invulnerableTime = 0;
		if (!com.projecthero.mod.omnitrix.OmnitrixRiding.partners(p, t)) {
			// magic: the one vanilla kind armour does nothing against -- his hand is inside them, not hitting them
			t.hurt(p.damageSources().indirectMagic(p, p), dmg(p, GRASP_DAMAGE));
		}
		t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, GRASP_TICKS, 1));
		t.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, GRASP_TICKS, 1));
		Vec3 in = t.position().add(0, t.getBbHeight() * 0.6, 0);
		QuirkFx.ribbon(l, chest(p), in).colours(0xFFFFFF, PURPLE).size(0.3f).life(8).style(QuirkFx.RIBBON_SHADOW).spawn();
		QuirkFx.ring(l, in).colours(0xFFFFFF, PURPLE).size(0.9f).thickness(0.15f).life(8).vec(p.getLookAngle()).flags(QuirkFx.F_VERTICAL).spawn();
		OmnitrixSounds.play(l, t.position(), SND_TENDRIL, 0.9f, 0.5f);
		return before - t.getHealth();
	}

	// ---------------------------------------------------------------- X: Phase / Shift+X: Ecto Rush / C: Fade

	public boolean phase(ServerPlayer p) {
		if (flag(p, F_PHASE) || !use(p, PHASE)) {
			return false;
		}
		anim(p, ANIM_PHASE);
		setFlag(p, F_PHASE, true);
		cooldown(p, PHASE, 0); // v0.17.2: a toggle with no time limit -- the cooldown starts when he turns it off
		PASSED.put(p.getUUID(), new HashSet<>());
		ServerLevel l = level(p);
		l.sendParticles(ParticleTypes.SOUL, p.getX(), p.getY() + 1, p.getZ(), 12, 0.4, 0.8, 0.4, 0.02);
		OmnitrixSounds.play(l, p.position(), SND_PHASE, 1.0f, 1.0f);
		return true;
	}

	/** Phase ends (X again, turning back, possessing): solid again -- and pushed out of any block he ended inside. */
	public void endPhase(ServerPlayer p, boolean cooldown) {
		if (!flag(p, F_PHASE)) {
			return;
		}
		setFlag(p, F_PHASE, false);
		if (!flag(p, F_RUSH)) {
			PASSED.remove(p.getUUID());
		}
		if (cooldown) {
			cooldown(p, PHASE, PHASE_COOLDOWN);
			OmnitrixSounds.play(level(p), p.position(), SND_PHASE, 0.7f, 0.7f);
		}
		if (!phasing(p)) {
			settleOutsideBlocks(p);
		}
	}

	/**
	 * v0.17.2 (user: "make sure ghost freaks ... phasing works"): solid again inside a wall, a floor or a ceiling -- out to the
	 * nearest spot he fits (the closest first, up preferred over down), else straight up, else onto the surface. Returns
	 * whether he had to be moved.
	 */
	public static boolean settleOutsideBlocks(ServerPlayer p) {
		ServerLevel level = p.serverLevel();
		AABB box = p.getBoundingBox();
		if (level.noCollision(p, box)) {
			return false;
		}
		double bx = Math.floor(p.getX()) + 0.5;
		double by = Math.floor(p.getY() + 1.0e-3);
		double bz = Math.floor(p.getZ()) + 0.5;
		Vec3 best = null;
		double bestD = Double.MAX_VALUE;
		for (int dy = -4; dy <= 4; dy++) {
			for (int dx = -4; dx <= 4; dx++) {
				for (int dz = -4; dz <= 4; dz++) {
					Vec3 at = new Vec3(bx + dx, by + dy, bz + dz);
					double d = at.distanceToSqr(p.position());
					if (at.y < p.getY() - 1.0e-3) {
						d = d * 1.5 + 1.0; // up (out of a floor he sank into) before down
					}
					if (d < bestD && level.noCollision(p, box.move(at.subtract(p.position())))) {
						best = at;
						bestD = d;
					}
				}
			}
		}
		if (best != null) {
			moveTo(p, best);
			return true;
		}
		for (int up = 5; up <= 384 && by + up < level.getMaxBuildHeight(); up++) {
			Vec3 at = new Vec3(p.getX(), by + up, p.getZ());
			if (level.noCollision(p, box.move(at.subtract(p.position())))) {
				moveTo(p, at);
				return true;
			}
		}
		BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, BlockPos.containing(p.getX(), p.getY(), p.getZ()));
		moveTo(p, new Vec3(top.getX() + 0.5, top.getY(), top.getZ() + 0.5));
		return true;
	}

	private static void moveTo(ServerPlayer p, Vec3 at) {
		p.teleportTo(at.x, at.y, at.z);
		p.setDeltaMovement(Vec3.ZERO);
		p.resetFallDistance();
	}

	/** While intangible: each creature his body passes through takes {@code damage} once (and is slowed, or weakened by the rush). */
	private int passThrough(ServerPlayer p, ServerLevel l, float damage, boolean rush) {
		Set<Integer> done = PASSED.computeIfAbsent(p.getUUID(), k -> new HashSet<>());
		AABB body = p.getBoundingBox().inflate(rush ? 0.8 : 0.3);
		int n = 0;
		for (LivingEntity e : around(p, p.position(), 3)) {
			if (!e.getBoundingBox().intersects(body) || !done.add(e.getId())) {
				continue;
			}
			e.invulnerableTime = 0;
			hurt(p, e, dmg(p, damage), false);
			e.addEffect(rush ? new MobEffectInstance(MobEffects.WEAKNESS, 80, 0) : new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
			l.sendParticles(ParticleTypes.SNOWFLAKE, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), 6, 0.2, 0.3, 0.2, 0.02);
			n++;
		}
		return n;
	}

	/** Shift+X: Ecto Rush -- a short intangible dash where he aims, through walls, floors and everything in the way. */
	public boolean ectoRush(ServerPlayer p) {
		if (!use(p, RUSH)) {
			return false;
		}
		anim(p, ANIM_RUSH);
		Vec3 dir = p.getLookAngle();
		dir = new Vec3(dir.x, Math.max(-0.6, Math.min(0.6, dir.y)), dir.z).normalize();
		RUSHES.put(p.getUUID(), dir);
		if (!flag(p, F_PHASE)) {
			PASSED.put(p.getUUID(), new HashSet<>());
		}
		setFlag(p, F_RUSH, true);
		setTimer(p, "rush_end", p.level().getGameTime() + RUSH_TICKS);
		p.setDeltaMovement(dir.scale(RUSH_SPEED));
		p.hurtMarked = true;
		ServerLevel l = level(p);
		l.sendParticles(WISP, p.getX(), p.getY() + 1, p.getZ(), 12, 0.3, 0.6, 0.3, 0.02);
		OmnitrixSfx.playOn(p, SND_PHASE, 1.1f, 1.5f, net.minecraft.sounds.SoundSource.PLAYERS);
		return true;
	}

	private void tickRush(ServerPlayer p, ServerLevel l, long now) {
		Vec3 dir = RUSHES.get(p.getUUID());
		if (dir == null) {
			return;
		}
		if (now >= timer(p, "rush_end")) {
			endRush(p);
			return;
		}
		p.setDeltaMovement(dir.scale(RUSH_SPEED));
		p.hurtMarked = true;
		p.resetFallDistance();
		passThrough(p, l, RUSH_DAMAGE, true);
		QuirkFx.ribbon(l, p.position().add(0, 1, 0).subtract(dir.scale(RUSH_SPEED)), p.position().add(0, 1, 0)).colours(0xFFFFFF, PURPLE).size(0.5f)
				.life(6).style(QuirkFx.RIBBON_LIGHT).spawn();
	}

	public void endRush(ServerPlayer p) {
		if (RUSHES.remove(p.getUUID()) == null && !flag(p, F_RUSH)) {
			return;
		}
		setFlag(p, F_RUSH, false);
		setTimer(p, "rush_end", 0);
		if (!flag(p, F_PHASE)) {
			PASSED.remove(p.getUUID());
		}
		p.setDeltaMovement(p.getDeltaMovement().scale(0.3));
		p.hurtMarked = true;
		if (!phasing(p)) {
			settleOutsideBlocks(p);
		}
	}

	/**
	 * C: Fade -- truly invisible until C again (user: "make sure he also has an invisibility move"; v0.17.2 user: "make ghost
	 * freaks invisibility toggleable and remove the time limits on it"): his body, his armour, what he
	 * holds and his name tag are hidden from every player (GhostfreakClient / the layer mixins), he leaves no wisps, and no
	 * mob can target him ({@code OmnitrixGhostfreakFadeMixin}; bosses still can). Not in True Form (the bare form cannot hide).
	 */
	public boolean fade(ServerPlayer p) {
		if (flag(p, F_TRUE)) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.ghostfreak.no_fade").withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		if (!use(p, FADE)) {
			return false;
		}
		anim(p, ANIM_FADE);
		setFlag(p, F_INVIS, true);
		cooldown(p, FADE, 0); // v0.17.2: a toggle with no time limit -- the cooldown starts when it ends
		p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 40, 0, false, false, false));
		loseTrackOfMe(p, 32);
		ServerLevel l = level(p);
		QuirkFx.ring(l, p.position().add(0, 1.0, 0)).colours(0xFFFFFF, PURPLE).size(1.4f).thickness(0.12f).life(10).style(QuirkFx.RING_SOFT).spawn();
		OmnitrixSounds.play(l, p.position(), SND_LAUGH, 0.7f, 1.3f);
		return true;
	}

	public void endFade(ServerPlayer p) {
		if (!flag(p, F_INVIS)) {
			return;
		}
		setFlag(p, F_INVIS, false);
		cooldown(p, FADE, FADE_COOLDOWN); // v0.17.2: ~1 s once it ends, so it cannot be spammed
		MobEffectInstance inv = p.getEffect(MobEffects.INVISIBILITY);
		if (inv != null && inv.getDuration() <= 40 && !flag(p, F_POSSESS)) {
			p.removeEffect(MobEffects.INVISIBILITY);
		}
	}

	/** Client-safe: is {@code p} a faded (or possessing) Ghostfreak -- nothing of him may be drawn, no mob may target him? */
	public static boolean unseen(@Nullable Entity e) {
		return e instanceof Player pl && ID.equals(Omnitrix.active(pl)) && (Omnitrix.state(pl).kitFlags & (F_INVIS | F_POSSESS)) != 0;
	}

	/** Server: may {@code mob} target {@code p}? Not while he is faded or inside a host -- unless it is a boss. */
	public static boolean hiddenFrom(Mob mob, Player p) {
		return unseen(p) && !(mob.getMaxHealth() >= 150f || mob instanceof EnderDragon || mob instanceof WitherBoss);
	}

	/** Mobs hunting him lose him (not bosses). */
	private static void loseTrackOfMe(ServerPlayer p, double range) {
		for (Mob m : p.level().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(range), m -> m.getTarget() == p)) {
			if (!(QuirkThreat.add(m, p, 0f) || m.getMaxHealth() >= 150f)) {
				m.setTarget(null);
			}
		}
	}

	// ---------------------------------------------------------------- Z: Chest Tentacles / Shift+Z: True Form / Shift+C: Fright Aura

	private static Vec3 chest(ServerPlayer p) {
		return p.position().add(0, p.getBbHeight() * 0.62, 0);
	}

	/**
	 * Z: his chest splits open and tentacles lash out -- up to {@link #TENTACLE_MAX} creatures in front (8 blocks) are grabbed
	 * ({@link #TENTACLE_HIT}), yanked in close and constricted for 2 s ({@link #TENTACLE_TICK} every 10 ticks). Bosses are
	 * held and hurt, but too heavy to yank. Half the cooldown in True Form.
	 */
	public int tentacles(ServerPlayer p) {
		if (!use(p, TENTACLES)) {
			return 0;
		}
		if (flag(p, F_TRUE)) {
			cooldown(p, TENTACLES, TENTACLE_COOLDOWN / 2);
		}
		anim(p, ANIM_TENTACLES);
		ServerLevel l = level(p);
		List<LivingEntity> in = cone(p, TENTACLE_RANGE, 80);
		in.sort((a, b) -> Double.compare(a.distanceToSqr(p), b.distanceToSqr(p)));
		List<Integer> held = new ArrayList<>();
		for (LivingEntity e : in) {
			if (held.size() >= TENTACLE_MAX) {
				break;
			}
			grab(p, l, e, dmg(p, TENTACLE_HIT));
			held.add(e.getId());
		}
		if (!held.isEmpty()) {
			GRIPS.put(p.getUUID(), new Grip(p.level().dimension(), held));
			setTimer(p, "tentacles_end", p.level().getGameTime() + TENTACLE_TICKS);
		}
		OmnitrixSounds.play(l, p.position(), SND_TENDRIL, 1.2f, 0.7f);
		return held.size();
	}

	/** One tentacle takes hold of {@code e}: the hit, and the yank toward the front of him (not a boss). */
	private void grab(ServerPlayer p, ServerLevel l, LivingEntity e, float damage) {
		e.invulnerableTime = 0;
		hurt(p, e, damage, false);
		if (!boss(p, e, 3f)) {
			Vec3 front = p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(1.8));
			Vec3 pull = front.subtract(e.position());
			double d = pull.length();
			if (d > 0.5) {
				e.setDeltaMovement(pull.scale(Math.min(1.0, 0.35 + d * 0.12) / d * Math.min(d, 2.5)).add(0, 0.25, 0));
				e.hurtMarked = true;
			}
		}
		QuirkFx.ribbon(l, chest(p), e.position().add(0, e.getBbHeight() * 0.6, 0)).colours(0x2A0A3A, 0x05020A).size(0.3f).life(8)
				.style(QuirkFx.RIBBON_SHADOW).spawn();
	}

	/**
	 * Shift+Use (empty hand): Tendril Snatch -- one true-form tentacle shoots from his chest at the creature he looks at (up to
	 * {@link #SNATCH_RANGE} away): {@link #SNATCH_DAMAGE}, and it is yanked to him and held a second ({@link #SNATCH_TICKS}).
	 * Zs'Skayr's tentacles snatching Ben in "Ghostfreaked Out". Bosses are hurt, not pulled.
	 */
	public boolean snatch(ServerPlayer p, @Nullable LivingEntity t) {
		if (t == null) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.ghostfreak.no_host").withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		if (!use(p, SNATCH)) {
			return false;
		}
		anim(p, ANIM_SNATCH);
		ServerLevel l = level(p);
		grab(p, l, t, dmg(p, SNATCH_DAMAGE));
		if (!boss(p, t, 0f)) {
			Vec3 pull = p.position().subtract(t.position());
			double d = pull.length();
			if (d > 2.0) {
				t.setDeltaMovement(pull.normalize().scale(Math.min(2.2, 0.5 + d * 0.16)).add(0, 0.35, 0));
				t.hurtMarked = true;
			}
		}
		GRIPS.put(p.getUUID(), new Grip(l.dimension(), new ArrayList<>(List.of(t.getId()))));
		setTimer(p, "tentacles_end", l.getGameTime() + SNATCH_TICKS);
		OmnitrixSounds.play(l, p.position(), SND_TENDRIL, 1.2f, 1.1f);
		return true;
	}

	private void tickTentacles(ServerPlayer p, ServerLevel l, long now) {
		Grip g = GRIPS.get(p.getUUID());
		if (g == null) {
			return;
		}
		long end = timer(p, "tentacles_end");
		if (now >= end || g.dim() != l.dimension()) {
			GRIPS.remove(p.getUUID());
			setTimer(p, "tentacles_end", 0);
			return;
		}
		boolean bite = (end - now) % 10 == 0;
		Vec3 front = p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(1.8));
		for (int id : g.held()) {
			if (!(l.getEntity(id) instanceof LivingEntity e) || !e.isAlive()) {
				continue;
			}
			boolean boss = boss(p, e, 0f);
			e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 15, boss ? 1 : 9));
			if (!boss && e.distanceToSqr(front) > 1.0 && now % 2 == 0) {
				Vec3 pull = front.subtract(e.position());
				e.setDeltaMovement(pull.normalize().scale(Math.min(0.5, pull.length() * 0.3)));
				e.hurtMarked = true;
			}
			if (bite) {
				e.invulnerableTime = 0;
				hurt(p, e, dmg(p, TENTACLE_TICK), false);
			}
			if (now % 2 == 0) {
				QuirkFx.ribbon(l, chest(p), e.position().add(0, e.getBbHeight() * 0.55, 0)).colours(0x2A0A3A, 0x05020A).size(0.22f).life(3)
						.style(QuirkFx.RIBBON_SHADOW).spawn();
			}
		}
		if (now % 20 == 0) {
			OmnitrixSounds.play(l, p.position(), SND_TENDRIL, 0.6f, 1.0f);
		}
	}

	/** Whether {@code e} is held in his tentacles right now. */
	public static boolean gripped(ServerPlayer p, LivingEntity e) {
		Grip g = GRIPS.get(p.getUUID());
		return g != null && g.held().contains(e.getId());
	}

	public boolean frightAura(ServerPlayer p) {
		if (!use(p, AURA)) {
			return false;
		}
		anim(p, ANIM_AURA);
		setFlag(p, F_AURA, true);
		setTimer(p, "aura_end", p.level().getGameTime() + AURA_TICKS);
		ServerLevel l = level(p);
		QuirkFx.ring(l, p.position().add(0, 0.1, 0)).colours(0xFFFFFF, PURPLE).size(7f).thickness(0.4f).life(16).ground().spawn();
		OmnitrixSounds.play(l, p.position(), SND_LAUGH, 1.2f, 0.85f);
		return true;
	}

	/**
	 * Shift+Z: True Form -- he grabs his chest and peels the skin open ({@link #PEEL_TICKS}); the reveal is a terror burst, then
	 * 20 s of the bare Ectonurite. The skin wraps back at the end (or in the sun at {@link #TRUE_SUN_FLOOR} health or less).
	 */
	public boolean trueForm(ServerPlayer p) {
		if (flag(p, F_TRUE) || !use(p, TRUE_FORM)) {
			return false;
		}
		long now = p.level().getGameTime();
		endFade(p);
		anim(p, ANIM_PEEL);
		setFlag(p, F_TRUE, true);
		setTimer(p, "true_start", now);
		setTimer(p, "true_burst", now + PEEL_TICKS);
		setTimer(p, "true_end", now + PEEL_TICKS + TRUE_TICKS);
		cooldown(p, TRUE_FORM, PEEL_TICKS + TRUE_TICKS + TRUE_COOLDOWN); // the real cooldown starts at the end
		OmnitrixSounds.play(level(p), p.position(), SND_TENDRIL, 1.0f, 0.5f);
		return true;
	}

	/** Is the bare true form out (the peel done, the burst past)? */
	public boolean trueFormOut(Player p) {
		return flag(p, F_TRUE) && timer(p, "true_burst") <= 0;
	}

	/** The reveal: {@link #TRUE_BURST} to everything within {@link #TRUE_RADIUS}, and they flee for {@link #TRUE_FEAR}. */
	private int terrorBurst(ServerPlayer p, ServerLevel l) {
		int n = 0;
		for (LivingEntity e : around(p, p.position(), TRUE_RADIUS)) {
			e.invulnerableTime = 0;
			hurt(p, e, dmg(p, TRUE_BURST), false);
			frighten(p, e, TRUE_FEAR);
			e.addEffect(new MobEffectInstance(e instanceof Player ? MobEffects.DARKNESS : MobEffects.WEAKNESS, TRUE_FEAR, 0));
			n++;
		}
		QuirkFx.ring(l, p.position().add(0, 0.15, 0)).colours(0x101018, PURPLE).size((float) TRUE_RADIUS).thickness(0.5f).life(18).ground().spawn();
		QuirkCamera.shakeAround(l, p.position(), 12, 0.3f, 12);
		OmnitrixSounds.play(l, p.position(), SND_SCREAM, 1.2f, 0.55f);
		OmnitrixSounds.play(l, p.position(), SND_LAUGH, 1.0f, 0.6f);
		return n;
	}

	private void tickTrueForm(ServerPlayer p, ServerLevel l, long now) {
		if (!flag(p, F_TRUE)) {
			return;
		}
		long burst = timer(p, "true_burst");
		if (burst > 0) {
			if (now >= burst) {
				setTimer(p, "true_burst", 0);
				terrorBurst(p, l);
			}
			return;
		}
		if (now >= timer(p, "true_end")) {
			endTrueForm(p, true);
			return;
		}
		// the sun is what beat Zs'Skayr: 3 a second on the bare form; the skin wraps back before it kills him
		if (now % 20 == 0 && !flag(p, F_POSSESS) && inSunlight(p)) {
			p.hurt(p.damageSources().inFire(), TRUE_SUN_DAMAGE);
			l.sendParticles(ParticleTypes.SMOKE, p.getX(), p.getY() + 1.2, p.getZ(), 6, 0.3, 0.5, 0.3, 0.01);
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.ghostfreak.sun_true").withStyle(ChatFormatting.GOLD), true);
			if (p.getHealth() <= TRUE_SUN_FLOOR) {
				endTrueForm(p, true);
				return;
			}
		}
		// four tentacles stay out and lash the nearest three every second
		if (now % 20 == 0) {
			lash(p, l);
		}
		// his stare: a mob held in it for a second flees
		LivingEntity looked = lookTarget(p, 16);
		long[] st = STARE.get(p.getUUID());
		if (looked == null) {
			STARE.remove(p.getUUID());
		} else if (st == null || st[0] != looked.getId()) {
			STARE.put(p.getUUID(), new long[] { looked.getId(), now });
		} else if (now - st[1] >= STARE_TICKS) {
			frighten(p, looked, STARE_FEAR);
			STARE.put(p.getUUID(), new long[] { looked.getId(), now + STARE_FEAR }); // not again until it has run a while
		}
	}

	/** The true form's tentacles: {@link #LASH_DAMAGE} to the nearest {@link #LASH_MAX} within {@link #LASH_RANGE}. */
	public int lash(ServerPlayer p, ServerLevel l) {
		List<LivingEntity> near = around(p, p.position(), LASH_RANGE);
		near.sort((a, b) -> Double.compare(a.distanceToSqr(p), b.distanceToSqr(p)));
		int n = 0;
		for (LivingEntity e : near) {
			if (n >= LASH_MAX) {
				break;
			}
			e.invulnerableTime = 0;
			hurt(p, e, dmg(p, LASH_DAMAGE), false);
			QuirkFx.ribbon(l, chest(p), e.position().add(0, e.getBbHeight() * 0.6, 0)).colours(0x202028, 0x05020A).size(0.25f).life(6)
					.style(QuirkFx.RIBBON_SHADOW).spawn();
			n++;
		}
		if (n > 0) {
			OmnitrixSounds.play(l, p.position(), SND_TENDRIL, 0.7f, 0.8f);
		}
		return n;
	}

	/** True Form ends: the skin wraps back ({@code cooldown}: its 45 s cooldown starts now). */
	public void endTrueForm(ServerPlayer p, boolean cooldown) {
		if (!flag(p, F_TRUE)) {
			return;
		}
		setFlag(p, F_TRUE, false);
		setTimer(p, "true_start", 0);
		setTimer(p, "true_burst", 0);
		setTimer(p, "true_end", 0);
		STARE.remove(p.getUUID());
		if (cooldown) {
			anim(p, ANIM_WRAP);
			cooldown(p, TRUE_FORM, TRUE_COOLDOWN);
			OmnitrixSounds.play(level(p), p.position(), SND_TENDRIL, 0.8f, 0.6f);
		}
	}

	// ---------------------------------------------------------------- vanilla keys

	/**
	 * Chilling Touch: +{@link #TOUCH_DAMAGE} and a slow; straight out of Fade it is an Ambush (+{@link #AMBUSH_DAMAGE}, blinds).
	 * Inside a host, a left-click is the HOST's blow instead ({@link #hostStrike}).
	 */
	@Override
	public boolean onAttack(ServerPlayer p, Entity target) {
		if (POSSESSED.containsKey(p.getUUID())) {
			hostStrike(p, target);
			return true; // never his own hand on (or through) the body he wears
		}
		boolean ambush = flag(p, F_INVIS);
		endFade(p);
		if (target instanceof LivingEntity le) {
			anim(p, ANIM_TOUCH);
			le.invulnerableTime = 0;
			hurt(p, le, dmg(p, TOUCH_DAMAGE + (ambush ? AMBUSH_DAMAGE : 0f)), false);
			le.invulnerableTime = 0;
			le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0));
			if (ambush) {
				ServerLevel l = level(p);
				le.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0));
				QuirkFx.blast(l, le.position().add(0, le.getBbHeight() * 0.6, 0)).colours(0xFFFFFF, PURPLE).size(1.2f).life(8).flags(QuirkFx.F_NO_SMOKE).spawn();
				OmnitrixSounds.play(l, le.position(), SND_CLAW, 1.2f, 0.6f);
			}
		}
		return false;
	}

	@Override
	public InteractionResult onUse(ServerPlayer p, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND || !p.getMainHandItem().isEmpty()) {
			return InteractionResult.PASS;
		}
		if (p.isShiftKeyDown()) {
			return snatch(p, lookTarget(p, SNATCH_RANGE)) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
		}
		if (!use(p, BOLT)) {
			return InteractionResult.FAIL;
		}
		anim(p, ANIM_BOLT);
		AlienProjectileEntity.of(p, "ghostfreak_bolt").colour(PURPLE).size(0.3f).damage(dmg(p, BOLT_DAMAGE)).speed(1.6).life(40).spawn();
		OmnitrixSounds.play(level(p), p.position(), SND_CLAW, 0.5f, 1.6f);
		p.swing(hand, true);
		return InteractionResult.SUCCESS;
	}

	/** Shift+right-click right on a creature snatches it too. */
	@Override
	public InteractionResult onUseEntity(ServerPlayer p, InteractionHand hand, Entity target) {
		if (hand == InteractionHand.MAIN_HAND && p.isShiftKeyDown() && p.getMainHandItem().isEmpty() && target instanceof LivingEntity le) {
			return snatch(p, le) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
		}
		return InteractionResult.PASS;
	}

	// ---------------------------------------------------------------- damage

	/** A blow, a bite, an arrow, a fall: what an intangible body only half feels. */
	public static boolean physical(DamageSource s) {
		if (s.is(DamageTypeTags.IS_FIRE) || s.is(DamageTypeTags.IS_EXPLOSION) || s.is(DamageTypeTags.WITCH_RESISTANT_TO) || s.is(DamageTypes.WITHER)
				|| s.is(DamageTypes.SONIC_BOOM) || s.is(DamageTypes.LIGHTNING_BOLT) || s.is(DamageTypes.FREEZE) || s.is(DamageTypes.STARVE)
				|| s.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return false;
		}
		return s.is(DamageTypeTags.IS_PROJECTILE) || s.is(DamageTypeTags.IS_FALL) || s.is(DamageTypes.MOB_ATTACK) || s.is(DamageTypes.MOB_ATTACK_NO_AGGRO)
				|| s.is(DamageTypes.PLAYER_ATTACK) || s.is(DamageTypes.STING) || s.is(DamageTypes.THORNS) || s.is(DamageTypes.FALLING_BLOCK)
				|| s.is(DamageTypes.FALLING_ANVIL) || s.is(DamageTypes.FALLING_STALACTITE) || s.is(DamageTypes.CACTUS)
				|| s.is(DamageTypes.SWEET_BERRY_BUSH) || s.is(DamageTypes.FLY_INTO_WALL) || s.is(DamageTypes.CRAMMING)
				|| s.is(DamageTypes.STALAGMITE) || s.is(OmnitrixDamage.ALIEN_STRIKE);
	}

	@Override
	public boolean allowDamage(ServerPlayer p, DamageSource source, float amount) {
		if (source.is(DamageTypes.DROWN) || source.is(DamageTypes.IN_WALL)) {
			return false; // nothing to breathe; a ghost in a wall does not suffocate
		}
		if (source.is(DamageTypeTags.IS_FALL)) {
			return false; // v0.17.2: normal gravity now, but a landing never hurts him
		}
		if (flag(p, F_POSSESS) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return false; // v0.17.2: hidden inside his host, nothing reaches him
		}
		return !(phasing(p) && physical(source)); // intangible: nothing physical touches him
	}

	/** Physical blows do half -- except to the bare True Form, which has lost the skin's protection. */
	@Override
	public float incomingDamage(ServerPlayer p, DamageSource source, float amount) {
		return physical(source) && !flag(p, F_TRUE) ? amount * PHYSICAL_FACTOR : amount;
	}

	// ---------------------------------------------------------------- V: Spirit Sight / Shift+V: Lights Out

	public static final String SIGHT = "spirit_sight";
	public static final int ANIM_SIGHT = 13;
	public static final int F_SIGHT = 32;
	public static final double SIGHT_RANGE = 32;
	private static final int SIGHT_NV = 260;

	private void vKey(ServerPlayer p, boolean shift) {
		if (shift) {
			lightsOut(p);
		} else {
			spiritSight(p);
		}
	}

	/**
	 * V: Spirit Sight on / off -- the dead see what the living cannot: night vision, and every living creature within 32 blocks
	 * glows through walls (the outline is drawn on the wearer's own client only; GhostfreakClient#glows).
	 */
	public boolean spiritSight(ServerPlayer p) {
		if (!use(p, SIGHT)) {
			return false;
		}
		boolean on = !flag(p, F_SIGHT);
		setFlag(p, F_SIGHT, on);
		anim(p, ANIM_SIGHT);
		ServerLevel l = level(p);
		if (on) {
			p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, SIGHT_NV, 0, false, false, false));
		} else {
			clearSight(p);
		}
		OmnitrixSounds.play(l, p.position(), SND_LAUGH, 0.5f, on ? 1.5f : 1.8f);
		p.displayClientMessage(Component.translatable(on ? "message.projecthero.omnitrix.ghostfreak.sight_on" : "message.projecthero.omnitrix.ghostfreak.sight_off")
				.withStyle(on ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GRAY), true);
		return true;
	}

	/** Takes back only his own short night vision (never a real potion's). */
	private static void clearSight(ServerPlayer p) {
		MobEffectInstance nv = p.getEffect(MobEffects.NIGHT_VISION);
		if (nv != null && nv.getDuration() <= SIGHT_NV && !nv.isVisible()) {
			p.removeEffect(MobEffects.NIGHT_VISION);
		}
	}

	private void revertV(ServerPlayer p) {
		if (flag(p, F_SIGHT)) {
			setFlag(p, F_SIGHT, false);
			clearSight(p);
		}
	}

	private void tickV(ServerPlayer p, ServerLevel l, long now) {
		if (flag(p, F_SIGHT) && now % 20 == 0) {
			p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, SIGHT_NV, 0, false, false, false));
		}
	}

	/**
	 * Shift+V: Lights Out (Zs'Skayr's plan in "Be Afraid of the Dark") -- darkness falls within {@link #DARK_RADIUS} of him for
	 * {@link #DARK_TICKS}: every creature there gets vanilla Darkness and loses its outline (Glowing) and its name tag (drawn by
	 * no one; GhostfreakClient), and it counts as dark for Creature of the Dark. It moves with him.
	 */
	public boolean lightsOut(ServerPlayer p) {
		if (!use(p, LIGHTS_OUT)) {
			return false;
		}
		anim(p, ANIM_LIGHTS_OUT);
		setFlag(p, F_DARK, true);
		setFlag(p, F_NIGHT, true);
		setTimer(p, "dark_end", p.level().getGameTime() + DARK_TICKS);
		ServerLevel l = level(p);
		darken(p);
		QuirkFx.decal(l, p.position().add(0, 0.05, 0)).style(QuirkFx.DECAL_POOL).colours(0x05030A, 0x000000).size((float) DARK_RADIUS).life(DARK_TICKS).ground().spawn();
		QuirkFx.ring(l, p.position().add(0, 0.1, 0)).colours(0x1A0A2A, 0x000000).size((float) DARK_RADIUS).thickness(0.6f).life(20).style(QuirkFx.RING_SOFT).ground().spawn();
		OmnitrixSounds.play(l, p.position(), SND_LAUGH, 1.0f, 0.5f);
		return true;
	}

	/** Lights Out's touch on everything near him: Darkness, and no Glowing outline. */
	private void darken(ServerPlayer p) {
		for (LivingEntity e : around(p, p.position(), DARK_RADIUS)) {
			e.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 40, 0, false, false, true));
			if (!(e instanceof Mob m && POSSESSED.values().stream().anyMatch(ps -> ps.hostId() == m.getId()))) {
				e.removeEffect(MobEffects.GLOWING);
			}
		}
	}

	private void tickLightsOut(ServerPlayer p, ServerLevel l, long now) {
		if (!flag(p, F_DARK)) {
			return;
		}
		if (now >= timer(p, "dark_end")) {
			endLightsOut(p);
			return;
		}
		if (now % 10 == 0) {
			darken(p);
		}
	}

	public void endLightsOut(ServerPlayer p) {
		if (!flag(p, F_DARK)) {
			return;
		}
		setFlag(p, F_DARK, false);
		setTimer(p, "dark_end", 0);
	}

	/** Client-safe: is {@code e} within the Lights Out of some Ghostfreak in its level (not that Ghostfreak himself)? */
	public static boolean inLightsOut(Entity e) {
		for (Player pl : e.level().players()) {
			if (pl != e && ID.equals(Omnitrix.active(pl)) && (Omnitrix.state(pl).kitFlags & F_DARK) != 0
					&& pl.distanceToSqr(e) <= DARK_RADIUS * DARK_RADIUS) {
				return true;
			}
		}
		return false;
	}
}
