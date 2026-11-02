package com.projecthero.mod.omnitrix.alien.upgrade;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.omnitrix.OmnitrixSfx;
import com.projecthero.mod.omnitrix.OmnitrixSounds;
import com.projecthero.mod.omnitrix.alien.AlienKit;
import com.projecthero.mod.omnitrix.alien.AlienMove;
import com.projecthero.mod.omnitrix.alien.AlienStats;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.data.OmnitrixState;
import com.projecthero.mod.quirk.QuirkThreat;
import com.projecthero.mod.quirk.fx.QuirkCamera;
import com.projecthero.mod.quirk.fx.QuirkFx;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.AbstractGolem;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1: Upgrade, the Galvanic Mechamorph -- living liquid technology: a black body run through with green circuit lines
 * and one round green eye. He merges with machines to make them better, squeezes through gaps and shorts things out.
 *
 * <pre>
 *   R        Optic Beam       a beam from the eye (12, 24 blocks)             Shift+R  Overload        the machine or creature you look at shorts out
 *                                                                                                       (8, golems 16)
 *   G        Tech Grab        liquid metal wraps the target: stunned 3 s (6)   Shift+G  Circuit Lash    a whip that yanks the target to you (8)
 *   X        Liquid Form      toggle: a fast puddle through soft/half blocks, climbs   Shift+X  Stretch Grapple  stretch to a block up to 22 away and pull there
 *   Z        Charged Beam     1 s charge, then a piercing beam (18) and a      Shift+Z  EMP Burst       ultimate: everything within 8 shorts out, 26 at
 *                             burst (6)                                                                the centre to 16 at the edge (golems x2)
 *   V       Overclock        merge into the held tool / weapon for 15 s: +100% mining speed, +4 damage, it mends
 *   C        Merge            merge into a furnace / dispenser / dropper / piston (supercharged) or an iron / snow golem,
 *                             minecart or boat (you pilot it), unseen; C again leaves
 *   Shift+C  Upgrade Tool     repair half the held tool's wear; already whole: +1 Unbreaking (to III)
 *   Attack   Circuit Jolt (+2, shorts golems)     Use on an iron door / trapdoor (empty hand)   it opens for you
 * </pre>
 *
 * <p>v0.17.2 (user: "change the damage on the ben 10 aliens to be on par with the elemental power abilities"): the rebalance
 * above -- Charged Beam to Z, Overload to Shift+R, EMP Burst his ultimate.
 *
 * <p>v0.17.2 revamp (user: "Yes, build them" -- docs/OMNITRIX_REVAMP_UPGRADE_GREYMATTER_XLR8.md, Upgrade). The kit now:
 * <pre>
 *   R        Optic Beam       12, 24 blocks, cd 1 s                           Shift+R  Stretch Fist    a liquid-metal fist out to 8 blocks (10,
 *                                                                                                       knockback 1.5), cd 5 s
 *   G        Liquid Engulf    6, stun 3 s; a machine is MERGED instead, cd 6 s Shift+G  Circuit Lash    8, yanks to you, cd 5 s
 *   X        Liquid Form      toggle; also through iron bars, fences, doors;    Shift+X  Stretch Grapple 22 blocks, cd 3 s
 *                             any attack key in it: Geyser (8 + launch, cd 4 s)
 *   Z        Charged Beam     18 + burst 6, cd 11 s                            Shift+Z  EMP Burst       ultimate (26 -> 16, machines x2); machines
 *                                                                                                       within 8 taken over 10 s; cd 30 s
 *   V        Overclock        15 s: +100% mining, +4 damage, mends; bows: arrows pierce 1 and fly 50% faster; the mod's guns: +50%
 *                             fire rate, no reload; shields: no cooldown after a disabling hit; cd 30 s
 *   Shift+V  Upgrade Tool     (was Shift+C) repair half / +1 Unbreaking; also the offhand item or, looking down, worn armour; cd 30 s
 *   C        Merge            as before, plus Ally Upgrade: onto a squad mate / teammate as living armour (+8 armour, +2 toughness,
 *                             their hits fire your Optic Beam (6) every 2 s, you take no damage), 20 s, cd 15 s
 *   Shift+C  Tech Takeover    a nanite bolt (16): a machine fights for you 15 s (max 2; bosses only stunned 2 s); a living target
 *                             is shorted out instead (the old Overload: 8, slowed and weakened 5 s); cd 20 s
 *   Passives Liquid Metal Body (25% of arrows / thrown things pass through), Tech Sense (machines and powered redstone within 16
 *            outlined green, client), Merged Shell. Weaknesses: Electric Shock (+50%, and a hit while merged throws him out,
 *            stunned 1 s), Water (the Liquid Form 40% slower, the Charged Beam's burst shorts out).
 * </pre>
 * Open questions, the parent's defaults: Ally Upgrade only on squad mates / teammates; Tech Takeover never hijacks another
 * player's Iron Man suit (machines only -- a player is shorted out); EMP Burst stays his ultimate; Overload folds into Takeover.
 */
public final class UpgradeKit extends AlienKit {
	public static final String ID = "upgrade";
	public static final int COLOUR = 0x38E05A;
	public static final int GLOW = 0xB8FFB0;

	public static final int ANIM_BEAM = 1;
	public static final int ANIM_CHARGE = 2;
	public static final int ANIM_CHARGED = 3;
	public static final int ANIM_GRAB = 4;
	public static final int ANIM_LASH = 5;
	public static final int ANIM_SLIDE = 6;
	public static final int ANIM_GRAPPLE = 7;
	public static final int ANIM_OVERLOAD = 8;
	public static final int ANIM_EMP = 9;
	public static final int ANIM_MERGE = 10;
	public static final int ANIM_REPAIR = 11;
	public static final int ANIM_JOLT = 12;
	// v0.17.2 revamp: Stretch Fist, Tech Takeover (a machine; a living target plays ANIM_OVERLOAD), Geyser, Ally Upgrade's beam
	public static final int ANIM_FIST = 14;
	public static final int ANIM_TAKEOVER = 15;
	public static final int ANIM_GEYSER = 16;
	public static final int ANIM_ALLY_BEAM = 17;

	public static final int F_SLIDE = 1;
	public static final int F_MERGED = 2;
	public static final int F_CHARGING = 4;

	public static final String BEAM = "optic_beam";
	public static final String CHARGED = "charged_beam";
	/** v0.17.2 revamp: Tech Grab is now Liquid Engulf (was "tech_grab"). */
	public static final String GRAB = "liquid_engulf";
	public static final String LASH = "circuit_lash";
	public static final String SLIDE = "liquid_slide";
	public static final String GRAPPLE = "stretch_grapple";
	/** v0.17.2 revamp: no longer a move of its own -- Tech Takeover's living-target branch (and its cue / pose). */
	public static final String OVERLOAD = "overload";
	public static final String EMP = "emp_burst";
	public static final String MERGE = "merge";
	public static final String REPAIR = "upgrade_tool";
	public static final String JOLT = "circuit_jolt";
	public static final String INTERFACE = "interface";
	/** v0.17.2 revamp: Shift+R, Shift+C, and the Liquid Form's attack. */
	public static final String FIST = "stretch_fist";
	public static final String TAKEOVER = "tech_takeover";
	public static final String GEYSER = "geyser";

	// v0.17.2 revamp (user: "Yes, build them"): the new moves' numbers
	public static final float FIST_DAMAGE = 10f;
	public static final double FIST_RANGE = 8, FIST_KNOCKBACK = 1.5;
	public static final int FIST_COOLDOWN = 100;
	public static final float GEYSER_DAMAGE = 8f;
	public static final double GEYSER_REACH = 2.5;
	public static final int GEYSER_COOLDOWN = 80;
	public static final double TAKEOVER_RANGE = 16;
	public static final int TAKEOVER_COOLDOWN = 400, BOSS_STUN = 40;
	/** The living-target branch of Tech Takeover: the old Overload -- 8, slowed and weakened for 5 s. */
	public static final int OVERLOAD_SLOW_TICKS = 100;
	/** Ally Upgrade: +8 armour, +2 toughness for the one he merges onto; their hits fire his Optic Beam (6) every 2 s. */
	public static final double ALLY_ARMOR = 8, ALLY_TOUGHNESS = 2;
	public static final float ALLY_BEAM_DAMAGE = 6f;
	public static final int ALLY_BEAM_EVERY = 40;
	/** Electric Shock: lightning / electric damage x1.5; a hit while merged throws him out, stunned this long. */
	public static final float SHOCK_MULT = 1.5f;
	public static final int SHOCK_STUN = 20;
	/** Water: the Liquid Form is 40% slower in it. */
	public static final double SLIDE_WATER = -0.4;
	/** Liquid Metal Body: one in four arrows / thrown things passes through him. */
	public static final int PASS_ONE_IN = 4;
	/** Overclock's weapon boost: arrows fly this much faster and pierce one. */
	public static final double ARROW_SPEED = 1.5;

	// v0.17.2 (user: "change the damage on the ben 10 aliens to be on par with the elemental power abilities"): was 6
	public static final float BEAM_DAMAGE = 12f;
	// v0.17.2 (user: "change the damage on the ben 10 aliens to be on par with the elemental power abilities"): was 14
	public static final float CHARGED_DAMAGE = 18f;
	// v0.17.2 (user: "change the damage on the ben 10 aliens to be on par with the elemental power abilities"): was 5
	public static final float CHARGED_BURST = 6f;
	public static final int CHARGE_TICKS = 20;
	public static final int STUN_TICKS = 60;
	/** v0.17.2 revamp: Liquid Engulf reaches 8 blocks (Tech Grab reached 5). */
	public static final double ENGULF_RANGE = 8;
	// v0.17.2 (user: "change the damage on the ben 10 aliens to be on par with the elemental power abilities"): was 3
	public static final float GRAB_DAMAGE = 6f;
	// v0.17.2 (user: "change the damage on the ben 10 aliens to be on par with the elemental power abilities"): was 4
	public static final float LASH_DAMAGE = 8f;
	/**
	 * v0.17.2 (user: "swap out upgrade liquid slide ability for him morphing into a liquid on the floor"): the Liquid Form,
	 * a 0.25-of-a-player puddle (0.45 tall: under half-block gaps), with no time limit.
	 */
	public static final double SLIDE_SCALE = 0.25;
	public static final double SLIDE_SPEED = 0.8;
	public static final int OVERLOAD_TICKS = 100;
	// v0.17.2 (user: "change the damage on the ben 10 aliens to be on par with the elemental power abilities"): was 3, golems 6
	public static final float OVERLOAD_DAMAGE = 8f;
	public static final float OVERLOAD_GOLEM_DAMAGE = OVERLOAD_DAMAGE * 2f;
	/**
	 * v0.17.2 (user: "change the damage on the ben 10 aliens to be on par with the elemental power abilities"): EMP Burst is his
	 * ultimate -- was a flat 3 (6 to golems); now {@link #EMP_CENTER} at the centre falling to {@link #EMP_EDGE} at
	 * {@link #EMP_RADIUS} (golems twice that), cooldown 30 s.
	 */
	public static final float EMP_CENTER = 26f, EMP_EDGE = 16f;
	public static final double EMP_RADIUS = 8;
	public static final int EMP_COOLDOWN = 600;
	public static final int MERGE_TICKS = 400;
	public static final int MERGE_COOLDOWN = 300;
	public static final float JOLT_DAMAGE = 2f;

	// v0.17.1 sound rework: OmnitrixSfx recipes from vanilla samples (see docs/OMNITRIX_SOUNDS_0171.md)
	/** The optic beam: a digital zap, electric crackle, a relay click. */
	public static final OmnitrixSfx.Cue SND_BEAM = OmnitrixSfx.cue("upgrade_beam")
			.layer(OmnitrixSounds.SWEEP, 0.3f, 1.8f)
			.layer(SoundEvents.SCULK_BLOCK_CHARGE, 0.35f, 1.6f)
			.layer(SoundEvents.COPPER_BULB_TURN_ON, 0.4f, 0.8f);
	/** Charging up: a low techno hum rising, a crackle. */
	public static final OmnitrixSfx.Cue SND_CHARGE = OmnitrixSfx.cue("upgrade_charge")
			.layer(OmnitrixSounds.WHIRR, 0.35f, 0.7f)
			.layer(SoundEvents.SCULK_BLOCK_CHARGE, 0.3f, 1.2f)
			.layer(SoundEvents.COPPER_BULB_TURN_ON, 0.3f, 1.0f, 4);
	/** Merging into tech: a gooey flow, then the low hum and a glitch. */
	public static final OmnitrixSfx.Cue SND_MERGE = OmnitrixSfx.cue("upgrade_merge")
			.layer(SoundEvents.HONEY_BLOCK_PLACE, 0.35f, 0.8f)
			.layer(OmnitrixSounds.WHIRR, 0.25f, 0.6f, 2)
			.layer(SoundEvents.SCULK_BLOCK_CHARGE, 0.25f, 1.4f, 3);
	/** The EMP: power dropping out with a zap. */
	public static final OmnitrixSfx.Cue SND_EMP = OmnitrixSfx.cue("upgrade_emp")
			.layer(OmnitrixSounds.POWER_DOWN, 0.45f, 0.9f)
			.layer(OmnitrixSounds.SWEEP, 0.3f, 0.7f)
			.layer(SoundEvents.SCULK_BLOCK_CHARGE, 0.4f, 0.8f);
	/** Sliding as liquid metal: a slick goo slide with a faint hum. */
	public static final OmnitrixSfx.Cue SND_SLIDE = OmnitrixSfx.cue("upgrade_slide")
			.layer(SoundEvents.HONEY_BLOCK_SLIDE, 0.4f, 0.9f)
			.layer(SoundEvents.SLIME_SQUISH_SMALL, 0.25f, 1.2f)
			.layer(OmnitrixSounds.WHIRR, 0.15f, 0.5f);
	/** v0.17.2 revamp: the Stretch Fist -- a gooey stretch, then a heavy metal punch landing. */
	public static final OmnitrixSfx.Cue SND_FIST = OmnitrixSfx.cue("upgrade_fist")
			.layer(SoundEvents.HONEY_BLOCK_PLACE, 0.35f, 1.3f)
			.layer(SoundEvents.IRON_GOLEM_ATTACK, 0.5f, 1.2f, 2)
			.layer(SoundEvents.SLIME_SQUISH, 0.25f, 0.8f, 2);
	/** v0.17.2 revamp: Tech Takeover -- a digital chirp, the nanites' whirr, a relay locking in. */
	public static final OmnitrixSfx.Cue SND_TAKEOVER = OmnitrixSfx.cue("upgrade_takeover")
			.layer(SoundEvents.BEACON_POWER_SELECT, 0.3f, 1.8f)
			.layer(OmnitrixSounds.WHIRR, 0.3f, 1.4f, 2)
			.layer(SoundEvents.COPPER_BULB_TURN_ON, 0.45f, 0.6f, 6);
	/** v0.17.2 revamp: the Geyser -- liquid metal bursting up out of the puddle. */
	public static final OmnitrixSfx.Cue SND_GEYSER = OmnitrixSfx.cue("upgrade_geyser")
			.layer(SoundEvents.SLIME_JUMP, 0.45f, 0.7f)
			.layer(SoundEvents.HONEY_BLOCK_SLIDE, 0.35f, 1.4f)
			.layer(SoundEvents.SCULK_BLOCK_CHARGE, 0.3f, 1.5f, 2);

	private static final ResourceLocation SLIDE_SCALE_ID = PowerToggles.id("omnitrix_upgrade_slide_scale");
	private static final ResourceLocation SLIDE_SPEED_ID = PowerToggles.id("omnitrix_upgrade_slide_speed");
	// v0.17.2 revamp: the Liquid Form in water, and Ally Upgrade's armour on the one he merges onto
	private static final ResourceLocation SLIDE_WATER_ID = PowerToggles.id("omnitrix_upgrade_slide_water");
	private static final ResourceLocation ALLY_ARMOR_ID = PowerToggles.id("omnitrix_upgrade_ally_armor");
	private static final ResourceLocation ALLY_TOUGH_ID = PowerToggles.id("omnitrix_upgrade_ally_toughness");
	// v0.17.2 (user: "please upgrade upgrades vfx aswell please"): Mechamorph green #39FF6A, and drops of his black liquid metal
	static final DustParticleOptions SPARK = new DustParticleOptions(new Vector3f(0.224f, 1.0f, 0.416f), 0.8f);
	static final DustParticleOptions SPARK_BIG = new DustParticleOptions(new Vector3f(0.224f, 1.0f, 0.416f), 1.4f);
	static final DustParticleOptions METAL = new DustParticleOptions(new Vector3f(0.03f, 0.045f, 0.04f), 1.1f);

	// ---------------------------------------------------------------- v0.17.2: what the client draws, in the synced kit timers
	// (user: "when upgrade takes over a block put the upgrade techno texture on it ... make an animation for him taking over the
	// block"; "please upgrade upgrades vfx aswell please"). MERGES is server-only, so the merge target goes out in the timers
	// (synced to every client) and UpgradeFx coats it; a move's end point / target does too, for its beam or tendril.

	/** The merge: {@link MergeKind} ordinal + 1, entity id + 1 (golem / minecart / boat), block x / y / z, the game time it began. */
	public static final String T_MERGE_KIND = "merge_kind", T_MERGE_ENT = "merge_ent", T_MERGE_X = "merge_x", T_MERGE_Y = "merge_y",
			T_MERGE_Z = "merge_z", T_MERGE_AT = "merge_at";
	/** The last move's end point (x / y / z) and target (entity id + 1). */
	public static final String T_FX_X = "fx_x", T_FX_Y = "fx_y", T_FX_Z = "fx_z", T_FX_ENT = "fx_ent";
	/** Keeps an encoded coordinate positive (a timer at 0 or below is removed). */
	public static final long COORD_BIAS = 1L << 40;

	public static long encode(double v) {
		return Math.round(v * 64.0) + COORD_BIAS;
	}

	public static double decode(long v) {
		return (v - COORD_BIAS) / 64.0;
	}

	/** Several kit timers in one state save (0 or less removes one). */
	private void putTimers(ServerPlayer p, Object... nameValue) {
		OmnitrixState n = com.projecthero.mod.omnitrix.Omnitrix.state(p).copy();
		for (int i = 0; i + 1 < nameValue.length; i += 2) {
			String k = key((String) nameValue[i]);
			long v = ((Number) nameValue[i + 1]).longValue();
			if (v <= 0L) {
				n.timers.remove(k);
			} else {
				n.timers.put(k, v);
			}
		}
		com.projecthero.mod.omnitrix.Omnitrix.save(p, n);
	}

	/** The move just used ends at {@code end} (and hit {@code target}, or nothing): the client's beam / tendril goes there. */
	private void fxTarget(ServerPlayer p, Vec3 end, @Nullable Entity target) {
		putTimers(p, T_FX_X, encode(end.x), T_FX_Y, encode(end.y), T_FX_Z, encode(end.z), T_FX_ENT, target == null ? 0L : target.getId() + 1L);
	}

	/** What Upgrade has merged into. */
	public enum MergeKind {
		// v0.17.2 revamp: GOLEM is any machine he can pilot (golems, Sentinels, Ultron's robots...); ALLY is Ally Upgrade
		FURNACE, DISPENSER, PISTON, GOLEM, MINECART, BOAT, ALLY
	}

	/** player -> the merge in progress. */
	private static final Map<UUID, Merge> MERGES = new ConcurrentHashMap<>();
	/** v0.17.2 revamp: Ally Upgrade -- the one he is merged onto -> him. */
	private static final Map<UUID, UUID> ALLY_HOSTS = new ConcurrentHashMap<>();
	/** v0.17.2: player -> the game tick his kit last ran (the once-a-tick guard in {@link #tick}). */
	private static final Map<Integer, Long> LAST_TICK = new ConcurrentHashMap<>();

	/** {@code ally}: Ally Upgrade's host (null for anything else). */
	public record Merge(MergeKind kind, ResourceKey<Level> dim, int entityId, BlockPos pos, @Nullable UUID ally) {
		public Merge(MergeKind kind, ResourceKey<Level> dim, int entityId, BlockPos pos) {
			this(kind, dim, entityId, pos, null);
		}
	}

	private UpgradeKit() {
	}

	public static void register() {
		UpgradeKit kit = new UpgradeKit();
		Aliens.register(kit);
		ServerTickEvents.END_SERVER_TICK.register(UpgradeEmp::tick);
		// v0.17.2 revamp: Tech Takeover's holds, their payload, the guns' fire rate, Ally Upgrade's beam, the boosted arrows
		UpgradeTakeover.register();
		net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playS2C().register(UpgradeTakeoverPayload.TYPE, UpgradeTakeoverPayload.CODEC);
		UpgradeOverclockHooks.register();
		net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (!world.isClientSide() && player instanceof ServerPlayer host && entity instanceof LivingEntity le) {
				kit.allyAttack(host, le);
			}
			return InteractionResult.PASS;
		});
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (entity instanceof net.minecraft.world.entity.projectile.AbstractArrow arrow) {
				boostArrow(arrow);
			}
		});
	}

	public static void clearSessionState() {
		MERGES.clear();
		ALLY_HOSTS.clear();
		LAST_TICK.clear();
		UpgradeEmp.clearSessionState();
		UpgradeTakeover.clearSessionState();
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public AlienStats stats() {
		return AlienStats.builder().health(28f).speed(1.05f).armor(6f, 0f).knockbackResist(0.2f).attack(3f).step(0.6f).safeFall(8f).build();
	}

	@Override
	protected List<AlienMove> declareMoves() {
		return List.of(
				// v0.17.2 (user: "change the damage on the ben 10 aliens to be on par with the elemental power abilities"): was
				// Shift+R Charged Beam, Z Overload, Shift+Z EMP Burst (400)
				// v0.17.2 revamp (user: "Yes, build them"): Shift+R Overload -> Stretch Fist (Overload folds into Shift+C Tech
				// Takeover), Tech Grab -> Liquid Engulf, Upgrade Tool C -> Shift+V, the Liquid Form's Geyser
				AlienMove.tap(BEAM, AbilitySlot.SLOT_1, 20), AlienMove.shift(FIST, AbilitySlot.SLOT_1, FIST_COOLDOWN),
				AlienMove.tap(GRAB, AbilitySlot.SLOT_2, 120), AlienMove.shift(LASH, AbilitySlot.SLOT_2, 100),
				AlienMove.tap(SLIDE, AbilitySlot.SLOT_3, 10), AlienMove.shift(GRAPPLE, AbilitySlot.SLOT_3, 60),
				AlienMove.tap(CHARGED, AbilitySlot.SLOT_4, 220), AlienMove.shift(EMP, AbilitySlot.SLOT_4, EMP_COOLDOWN),
				AlienMove.tap(OVERCLOCK, AbilitySlot.SLOT_5, 600), AlienMove.shift(REPAIR, AbilitySlot.SLOT_5, 600),
				AlienMove.tap(MERGE, AbilitySlot.SLOT_6, MERGE_COOLDOWN), AlienMove.shift(TAKEOVER, AbilitySlot.SLOT_6, TAKEOVER_COOLDOWN),
				AlienMove.vanilla(JOLT, "Attack", 0), AlienMove.vanilla(INTERFACE, "Use on an iron door / trapdoor (empty hand)", 10),
				AlienMove.vanilla(GEYSER, "Any attack key in the Liquid Form", GEYSER_COOLDOWN));
	}

	// ---------------------------------------------------------------- lifecycle

	@Override
	public void onTransform(ServerPlayer p) {
		ServerLevel l = level(p);
		OmnitrixSounds.play(l, p.position(), SND_MERGE, 0.9f, 1.2f);
		l.sendParticles(SPARK, p.getX(), p.getY() + 1, p.getZ(), 30, 0.4, 0.8, 0.4, 0.0);
	}

	@Override
	public void onRevert(ServerPlayer p) {
		revertV(p);
		endMerge(p, false);
		endSlide(p, true);
		setFlag(p, F_CHARGING, false);
		setTimer(p, "charge_at", 0);
		// v0.17.2 revamp: every machine he holds is let go; the once-a-tick guard forgets him
		UpgradeTakeover.releaseAll(p);
		LAST_TICK.remove(p.getId());
	}

	/** v0.17.2 revamp: the HUD lights X in the Liquid Form, V while overclocked and C while merged. */
	@Override
	public boolean keyActive(Player p, AbilitySlot slot) {
		return switch (slot) {
			case SLOT_3 -> flag(p, F_SLIDE);
			case SLOT_5 -> flag(p, F_OVERCLOCK);
			case SLOT_6 -> flag(p, F_MERGED);
			default -> false;
		};
	}

	public static boolean sliding(Player p) {
		return (com.projecthero.mod.omnitrix.Omnitrix.state(p).kitFlags & F_SLIDE) != 0;
	}

	@Nullable
	public static Merge merge(ServerPlayer p) {
		return MERGES.get(p.getUUID());
	}

	@Override
	public void tick(ServerPlayer p, OmnitrixState s, long now) {
		// v0.17.2: once per game tick -- the timed pulses (the merge's dispenser / piston, the ally beam) must not double up if
		// he is ticked twice in a tick (a gametest's mock player is ticked by the server and by the test's own pump)
		Long last = LAST_TICK.put(p.getId(), now);
		if (last != null && last == now) {
			return;
		}
		ServerLevel l = level(p);
		tickV(p, l, now);
		// v0.17.2 revamp (rendered effects over particles): the idle spark and the puddle's sparks are gone -- the glowing
		// circuits and UpgradeFx's puddle say it. Water: the Liquid Form disperses (40% slower)
		if (flag(p, F_SLIDE) && p.isInWater()) {
			PowerToggles.modifier(p, Attributes.MOVEMENT_SPEED, SLIDE_WATER_ID, SLIDE_WATER, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		} else {
			PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, SLIDE_WATER_ID);
		}
		// Charged Beam
		long charge = timer(p, "charge_at");
		if (charge > 0) {
			if (now >= charge) {
				setTimer(p, "charge_at", 0);
				setFlag(p, F_CHARGING, false);
				fireChargedBeam(p);
			} else {
				// (the charge's orb and rings are UpgradeFx's; v0.17.2 revamp: no particle spiral on top)
				p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 4, 3, false, false, false));
			}
		}
		tickMerge(p, l, now);
	}

	// ---------------------------------------------------------------- keys

	@Override
	public void press(ServerPlayer p, AbilitySlot slot, boolean shift) {
		// v0.17.2 revamp: in the Liquid Form any attack key (R, G, Z and their Shift versions) is the Geyser when a foe is over
		// the puddle; otherwise the move rises him and runs as usual
		if ((slot == AbilitySlot.SLOT_1 || slot == AbilitySlot.SLOT_2 || slot == AbilitySlot.SLOT_4) && geyser(p)) {
			return;
		}
		switch (slot) {
			case SLOT_1 -> {
				if (shift) {
					stretchFist(p); // v0.17.2 revamp: was Overload (now Tech Takeover's living-target branch)
				} else {
					opticBeam(p);
				}
			}
			case SLOT_2 -> {
				if (shift) {
					lash(p, lookTarget(p, 12));
				} else {
					engulf(p, lookTarget(p, ENGULF_RANGE));
				}
			}
			case SLOT_3 -> {
				if (shift) {
					grapple(p);
				} else {
					liquidSlide(p);
				}
			}
			case SLOT_4 -> {
				if (shift) {
					empBurst(p);
				} else {
					chargedBeam(p); // v0.17.2: was Shift+R
				}
			}
			case SLOT_5 -> vKey(p, shift); // v0.17.1 (user: "add abilities to the V key for every alien")
			case SLOT_6 -> {
				if (shift) {
					techTakeover(p); // v0.17.2 revamp: Upgrade Tool moved to Shift+V
				} else if (MERGES.containsKey(p.getUUID())) {
					endMerge(p, true);
				} else {
					merge(p, true);
				}
			}
			default -> {
			}
		}
	}

	private static BlockHitResult blockLook(ServerPlayer p, double range) {
		Vec3 eye = p.getEyePosition();
		return p.level().clip(new ClipContext(eye, eye.add(p.getLookAngle().scale(range)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
	}

	static boolean boss(ServerPlayer p, LivingEntity e, float threat) {
		return QuirkThreat.add(e, p, threat) || e.getMaxHealth() >= 150f;
	}

	public boolean opticBeam(ServerPlayer p) {
		if (!use(p, BEAM)) {
			return false;
		}
		anim(p, ANIM_BEAM);
		ServerLevel l = level(p);
		Vec3 eye = p.getEyePosition();
		LivingEntity t = lookTarget(p, 24);
		Vec3 end = t != null ? t.position().add(0, t.getBbHeight() * 0.6, 0) : blockLook(p, 24).getLocation();
		if (t != null) {
			t.invulnerableTime = 0;
			hurt(p, t, BEAM_DAMAGE, false);
		}
		// v0.17.2 VFX: the beam itself is UpgradeFx's (a green energy tube with a white core); here only the hit
		fxTarget(p, end, t);
		impact(l, end, t == null ? blockLook(p, 24) : null, 0.6f);
		OmnitrixSounds.play(l, p.position(), SND_BEAM, 0.9f, 1.1f + p.getRandom().nextFloat() * 0.2f);
		return true;
	}

	/**
	 * v0.17.2 VFX: where a beam lands -- electric sparks, a puff of green and, on a block, a scorched circuit print (a dark
	 * scorch with a green line glow). {@code k} sizes it.
	 */
	private static void impact(ServerLevel l, Vec3 at, @Nullable BlockHitResult block, float k) {
		l.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, (int) (14 * k) + 4, 0.15 * k, 0.15 * k, 0.15 * k, 0.25);
		l.sendParticles(SPARK_BIG, at.x, at.y, at.z, (int) (8 * k) + 2, 0.2 * k, 0.2 * k, 0.2 * k, 0.0);
		if (block != null && block.getType() == HitResult.Type.BLOCK && block.getDirection() == Direction.UP) {
			QuirkFx.decal(l, at).style(QuirkFx.DECAL_SCORCH).colour(0x0B1A10).size(0.7f * k + 0.3f).life(80).spawn();
		}
	}

	public boolean chargedBeam(ServerPlayer p) {
		if (!use(p, CHARGED)) {
			return false;
		}
		anim(p, ANIM_CHARGE);
		setFlag(p, F_CHARGING, true);
		setTimer(p, "charge_at", p.level().getGameTime() + CHARGE_TICKS);
		OmnitrixSounds.play(level(p), p.position(), SND_CHARGE, 1.0f, 1.0f);
		return true;
	}

	/** The charged beam itself: everything along 32 blocks takes 18, and a burst (6) where it ends (v0.17.2: was 14 and 5). */
	public int fireChargedBeam(ServerPlayer p) {
		anim(p, ANIM_CHARGED);
		ServerLevel l = level(p);
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		Vec3 end = blockLook(p, 32).getLocation();
		double len = end.distanceTo(eye);
		int hits = 0;
		for (LivingEntity e : around(p, eye.add(look.scale(len / 2)), len / 2 + 2)) {
			Vec3 c = e.position().add(0, e.getBbHeight() * 0.5, 0);
			double t = Math.max(0, Math.min(len, c.subtract(eye).dot(look)));
			if (c.distanceTo(eye.add(look.scale(t))) <= 0.9 + e.getBbWidth() * 0.5) {
				e.invulnerableTime = 0;
				hurt(p, e, CHARGED_DAMAGE, false);
				shove(e, eye, 0.8, 0.25);
				hits++;
			}
		}
		// v0.17.2 revamp (Water weakness): fired from in the water, the burst shorts out -- no splash damage, no blast
		boolean shorted = p.isInWater();
		if (!shorted) {
			for (LivingEntity e : around(p, end, 2.5)) {
				e.invulnerableTime = 0;
				hurt(p, e, CHARGED_BURST, false);
			}
		}
		// v0.17.2 VFX: the beam is UpgradeFx's, a thick tube with circuit rings racing down it
		fxTarget(p, end, null);
		impact(l, end, blockLook(p, 32), shorted ? 0.5f : 1.4f);
		if (!shorted) {
			QuirkFx.blast(l, end).colours(0xFFFFFF, COLOUR).size(2.2f).life(14).flags(QuirkFx.F_NO_SMOKE).spawn();
		}
		QuirkCamera.shake(p, 0.3f, 6);
		OmnitrixSounds.play(l, p.position(), SND_BEAM, 1.3f, 0.6f);
		OmnitrixSounds.play(l, end, SND_EMP, 0.9f, 1.3f);
		return hits;
	}

	/**
	 * G: Liquid Engulf (v0.17.2 revamp, was Tech Grab, 5 blocks): liquid metal pours over the creature you aim at (8 blocks) and
	 * sets hard -- 6, stunned 3 s (bosses only slowed). A machine he can pilot is MERGED instead, from range (as C).
	 */
	public boolean engulf(ServerPlayer p, LivingEntity t) {
		if (t == null) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.upgrade.no_target").withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		if (pilotable(p, t) && !MERGES.containsKey(p.getUUID()) && ready(p, GRAB) && ready(p, MERGE)) {
			if (mergeWith(p, t)) { // starts the merge's own cooldown too
				cooldown(p, GRAB, -1);
				return true;
			}
		}
		if (!use(p, GRAB)) {
			return false;
		}
		anim(p, ANIM_GRAB);
		boolean boss = boss(p, t, 6f);
		stun(t, boss ? 30 : STUN_TICKS, boss);
		t.invulnerableTime = 0;
		hurt(p, t, GRAB_DAMAGE, false);
		ServerLevel l = level(p);
		// v0.17.2 VFX: UpgradeFx draws the liquid-metal tendril and the coils binding the target
		fxTarget(p, t.position().add(0, t.getBbHeight() * 0.5, 0), t);
		l.sendParticles(METAL, t.getX(), t.getY() + t.getBbHeight() * 0.5, t.getZ(), 12, t.getBbWidth() * 0.4, t.getBbHeight() * 0.3, t.getBbWidth() * 0.4, 0.0);
		l.sendParticles(ParticleTypes.ELECTRIC_SPARK, t.getX(), t.getY() + t.getBbHeight() * 0.5, t.getZ(), 8, 0.3, 0.4, 0.3, 0.15);
		OmnitrixSounds.play(l, t.position(), SND_MERGE, 0.8f, 1.5f);
		return true;
	}

	/** Can barely move or swing ({@code light}: a boss shrugs most of it off). */
	public static void stun(LivingEntity t, int ticks, boolean light) {
		t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, light ? 2 : 9));
		t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, light ? 0 : 4));
		t.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, ticks, light ? 0 : 3));
		if (!light && t instanceof Mob m) {
			m.getNavigation().stop();
			m.setTarget(null);
		}
		t.setDeltaMovement(t.getDeltaMovement().multiply(0, 1, 0));
		t.hurtMarked = true;
	}

	public boolean lash(ServerPlayer p, LivingEntity t) {
		if (t == null) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.upgrade.no_target").withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		if (!use(p, LASH)) {
			return false;
		}
		anim(p, ANIM_LASH);
		ServerLevel l = level(p);
		t.invulnerableTime = 0;
		hurt(p, t, LASH_DAMAGE, false);
		if (!boss(p, t, 4f)) {
			Vec3 pull = p.position().subtract(t.position());
			double d = pull.length();
			double res = 1.0 - Math.min(1.0, t.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
			t.setDeltaMovement(pull.normalize().scale(Math.min(1.6, 0.25 + d * 0.14) * Math.max(0.35, res)).add(0, 0.35, 0));
			t.hurtMarked = true;
		}
		// v0.17.2 VFX: UpgradeFx draws the whip (a liquid-metal strand cracking out and snapping back)
		fxTarget(p, t.position().add(0, t.getBbHeight() * 0.5, 0), t);
		l.sendParticles(ParticleTypes.ELECTRIC_SPARK, t.getX(), t.getY() + t.getBbHeight() * 0.6, t.getZ(), 10, 0.25, 0.3, 0.25, 0.25);
		OmnitrixSounds.play(l, p.position(), SND_SLIDE, 0.8f, 1.6f);
		return true;
	}

	// ---------------------------------------------------------------- the Liquid Form

	/**
	 * X: melt into (or rise out of) the Liquid Form -- a toggle with no time limit. He only rises where there is room to
	 * stand; any other move rises him first ({@link #onUse}).
	 */
	public boolean liquidSlide(ServerPlayer p) {
		if (flag(p, F_SLIDE)) {
			if (!roomToStand(p)) {
				p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.upgrade.no_room").withStyle(ChatFormatting.GRAY), true);
				return false;
			}
			endSlide(p, false);
			return true;
		}
		if (!use(p, SLIDE)) {
			return false;
		}
		anim(p, ANIM_SLIDE);
		setFlag(p, F_SLIDE, true);
		PowerToggles.modifier(p, Attributes.SCALE, SLIDE_SCALE_ID, SLIDE_SCALE - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		PowerToggles.modifier(p, Attributes.MOVEMENT_SPEED, SLIDE_SPEED_ID, SLIDE_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		p.refreshDimensions();
		Vec3 l = p.getLookAngle();
		Vec3 f = new Vec3(l.x, 0, l.z);
		if (f.lengthSqr() > 1.0e-4) {
			p.setDeltaMovement(f.normalize().scale(0.9));
			p.hurtMarked = true;
		}
		ServerLevel level = level(p);
		OmnitrixSounds.play(level, p.position(), SND_SLIDE, 1.0f, 1.0f);
		return true;
	}

	/** Room to stand at full size where he is now? */
	public static boolean roomToStand(ServerPlayer p) {
		AABB box = AABB.ofSize(p.position().add(0, 0.9, 0), 0.6, 1.8, 0.6).deflate(1.0e-3);
		return p.level().noCollision(p, box);
	}

	private void endSlide(ServerPlayer p, boolean force) {
		boolean was = flag(p, F_SLIDE);
		PowerToggles.clearModifier(p, Attributes.SCALE, SLIDE_SCALE_ID);
		PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, SLIDE_SPEED_ID);
		PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, SLIDE_WATER_ID); // v0.17.2 revamp
		setFlag(p, F_SLIDE, false);
		p.refreshDimensions();
		if (was) {
			if (!roomToStand(p)) {
				com.projecthero.mod.hero.power.p04.SuperSpeedHandlers.settleOutsideBlocks(p);
			}
			if (!force) {
				OmnitrixSounds.play(level(p), p.position(), SND_SLIDE, 0.7f, 0.7f);
			}
		}
	}

	/** Any other move rises him out of the Liquid Form first. */
	@Override
	protected void onUse(ServerPlayer p, String moveId) {
		if (!SLIDE.equals(moveId) && flag(p, F_SLIDE)) {
			endSlide(p, false);
		}
	}

	/** Liquid Form: pressed into a wall while looking up, he flows straight up it (the speedsters' wall-run pitch). */
	@Override
	public boolean climbs(Player p) {
		return flag(p, F_SLIDE) && p.horizontalCollision && !p.isShiftKeyDown()
				&& p.getXRot() <= com.projecthero.mod.hero.power.p04.SuperSpeedHandlers.WALL_RUN_PITCH;
	}

	/**
	 * Blocks the Liquid Form seeps through: soft ones and half blocks; v0.17.2 revamp: also iron bars, fences, fence gates and
	 * doors (he runs through the gaps and under the door).
	 */
	public static boolean soft(net.minecraft.world.level.block.state.BlockState state) {
		return state.is(Blocks.IRON_BARS) || state.is(net.minecraft.tags.BlockTags.FENCES) || state.is(net.minecraft.tags.BlockTags.FENCE_GATES)
				|| state.is(net.minecraft.tags.BlockTags.DOORS) || state.is(net.minecraft.tags.BlockTags.LEAVES) || state.is(net.minecraft.tags.BlockTags.WOOL)
				|| state.is(net.minecraft.tags.BlockTags.WOOL_CARPETS) || state.is(net.minecraft.tags.BlockTags.SLABS)
				|| state.is(net.minecraft.tags.BlockTags.STAIRS) || state.is(net.minecraft.tags.BlockTags.BEDS)
				|| state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.HAY_BLOCK) || state.is(Blocks.MOSS_BLOCK)
				|| state.is(Blocks.SPONGE) || state.is(Blocks.WET_SPONGE) || state.is(Blocks.SLIME_BLOCK) || state.is(Blocks.HONEY_BLOCK);
	}

	/**
	 * v0.17.2: no collision for {@code state} at {@code pos} -- the colliding entity is Upgrade in the Liquid Form, the block
	 * is soft or a half block, and he is not standing on top of it (so floors of slabs or leaves still hold him). Read from
	 * the synced kit flags, so the client's movement and the server's check agree ({@code SuperSpeedLeavesMixin}).
	 */
	public static boolean seepsThrough(net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.level.BlockGetter level,
			BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) {
		return context instanceof net.minecraft.world.phys.shapes.EntityCollisionContext ec && ec.getEntity() instanceof Player pl
				&& soft(state) && sliding(pl) && AlienKit.activeKit(pl) instanceof UpgradeKit
				&& !context.isAbove(state.getShape(level, pos), pos, false);
	}

	public boolean grapple(ServerPlayer p) {
		BlockHitResult hit = blockLook(p, 22);
		if (hit.getType() != HitResult.Type.BLOCK) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.upgrade.no_anchor").withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		if (!use(p, GRAPPLE)) {
			return false;
		}
		anim(p, ANIM_GRAPPLE);
		Vec3 to = hit.getLocation().subtract(p.getEyePosition());
		double d = to.length();
		p.setDeltaMovement(to.normalize().scale(Math.min(2.2, 0.5 + d * 0.11)).add(0, 0.3, 0));
		p.hurtMarked = true;
		p.resetFallDistance();
		ServerLevel l = level(p);
		// v0.17.2 VFX: UpgradeFx draws the stretched arm, anchored where it struck; a splash of metal there
		fxTarget(p, hit.getLocation(), null);
		Vec3 at = hit.getLocation();
		l.sendParticles(METAL, at.x, at.y, at.z, 10, 0.15, 0.15, 0.15, 0.0);
		l.sendParticles(SPARK, at.x, at.y, at.z, 6, 0.2, 0.2, 0.2, 0.0);
		OmnitrixSounds.play(l, p.position(), SND_SLIDE, 0.9f, 1.3f);
		return true;
	}

	// ---------------------------------------------------------------- v0.17.2 revamp: Stretch Fist, Geyser, Tech Takeover

	/**
	 * Shift+R: Stretch Fist (v0.17.2 revamp, replaces Overload) -- an arm shoots out up to {@link #FIST_RANGE} blocks as a
	 * liquid-metal fist and slams what it hits back ({@link #FIST_DAMAGE}, knockback {@link #FIST_KNOCKBACK}). It punches out even
	 * with nothing there.
	 */
	public boolean stretchFist(ServerPlayer p) {
		if (!use(p, FIST)) {
			return false;
		}
		anim(p, ANIM_FIST);
		ServerLevel l = level(p);
		LivingEntity t = lookTarget(p, FIST_RANGE);
		Vec3 end;
		if (t != null) {
			end = t.position().add(0, t.getBbHeight() * 0.55, 0);
			t.invulnerableTime = 0;
			hurt(p, t, FIST_DAMAGE, false);
			if (!boss(p, t, 5f)) {
				shove(t, p.position(), FIST_KNOCKBACK, 0.35);
			}
			QuirkCamera.shake(p, 0.15f, 4);
		} else {
			BlockHitResult hit = blockLook(p, FIST_RANGE);
			end = hit.getLocation();
		}
		// UpgradeFx draws the arm (a stretched liquid-metal strand) and the fist ballooning as it lands
		fxTarget(p, end, t);
		OmnitrixSounds.play(l, p.position(), SND_FIST, 1.0f, 1.0f);
		return true;
	}

	/**
	 * v0.17.2 revamp: the Geyser -- any attack key while in the Liquid Form with a foe over the puddle (within
	 * {@link #GEYSER_REACH}): he rises up through it as a column of liquid metal ({@link #GEYSER_DAMAGE} and a launch) and
	 * reforms. False (the key does its own move) when he isn't a puddle, nothing is there, or it cools down.
	 */
	public boolean geyser(ServerPlayer p) {
		if (!flag(p, F_SLIDE) || cooldownLeft(p, GEYSER) > 0 || !roomToStand(p)) {
			return false;
		}
		LivingEntity foe = null;
		double best = Double.MAX_VALUE;
		for (LivingEntity e : around(p, p.position().add(0, 0.5, 0), GEYSER_REACH)) {
			double dx = e.getX() - p.getX(), dz = e.getZ() - p.getZ();
			double d = dx * dx + dz * dz;
			if (d <= GEYSER_REACH * GEYSER_REACH && e.getY() >= p.getY() - 1.0 && e.getY() <= p.getY() + 2.0 && d < best) {
				best = d;
				foe = e;
			}
		}
		if (foe == null || !use(p, GEYSER)) { // use() rises him out of the puddle (onUse)
			return false;
		}
		anim(p, ANIM_GEYSER);
		ServerLevel l = level(p);
		foe.invulnerableTime = 0;
		hurt(p, foe, GEYSER_DAMAGE, false);
		if (!boss(p, foe, 5f)) {
			double res = 1.0 - Math.min(1.0, foe.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
			foe.setDeltaMovement(foe.getDeltaMovement().multiply(0.3, 0, 0.3).add(0, 1.05 * Math.max(0.35, res), 0));
			foe.hurtMarked = true;
		}
		p.setDeltaMovement(p.getDeltaMovement().multiply(0.2, 0, 0.2).add(0, 0.55, 0));
		p.hurtMarked = true;
		p.resetFallDistance();
		fxTarget(p, foe.position().add(0, foe.getBbHeight() * 0.5, 0), foe);
		OmnitrixSounds.play(l, p.position(), SND_GEYSER, 1.0f, 1.0f);
		return true;
	}

	/** A machine he can merge with and pilot (C, or Liquid Engulf from range): not a boss. */
	public static boolean pilotable(ServerPlayer p, Entity e) {
		return e instanceof Mob m && m.isAlive() && UpgradeTakeover.mechanical(e) && !boss(p, m, 0f);
	}

	/**
	 * Shift+C: Tech Takeover (v0.17.2 revamp, user: "Yes, build them") -- a nanite bolt out to {@link #TAKEOVER_RANGE} blocks. A
	 * machine turns green-lined and fights for him for {@link UpgradeTakeover#TICKS} (max {@link UpgradeTakeover#MAX}); a boss is
	 * only stunned {@link #BOSS_STUN} ticks. Anything living is shorted out instead -- the old Overload (8, slowed and weakened
	 * 5 s; another player's Iron Man suit counts as living: no hijacking players). A block jams the redstone round it.
	 */
	public boolean techTakeover(ServerPlayer p) {
		ServerLevel l = level(p);
		LivingEntity t = lookTarget(p, TAKEOVER_RANGE);
		BlockHitResult hit = t == null ? blockLook(p, TAKEOVER_RANGE) : null;
		if (t == null && (hit == null || hit.getType() != HitResult.Type.BLOCK)) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.upgrade.no_target").withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		if (!use(p, TAKEOVER)) {
			return false;
		}
		Vec3 at;
		if (t != null && UpgradeTakeover.mechanical(t) && t instanceof Mob mob) {
			anim(p, ANIM_TAKEOVER);
			at = t.position().add(0, t.getBbHeight() * 0.5, 0);
			if (boss(p, t, 6f)) {
				stunMachine(t, BOSS_STUN);
				p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.upgrade.too_big").withStyle(ChatFormatting.GRAY), true);
			} else {
				UpgradeTakeover.take(p, mob, UpgradeTakeover.TICKS, true);
				p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.upgrade.taken_over", t.getDisplayName())
						.withStyle(ChatFormatting.GREEN), true);
			}
			OmnitrixSounds.play(l, at, SND_TAKEOVER, 1.0f, 1.0f);
		} else {
			anim(p, ANIM_OVERLOAD);
			if (t != null) {
				at = t.position().add(0, t.getBbHeight() * 0.5, 0);
				overloadLiving(p, t);
			} else {
				BlockPos pos = hit.getBlockPos();
				at = Vec3.atCenterOf(pos);
				UpgradeEmp.jam(l, pos, 3, OVERLOAD_TICKS);
			}
			l.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 10, 0.4, 0.4, 0.4, 0.25);
			OmnitrixSounds.play(l, at, SND_EMP, 1.0f, 1.2f);
		}
		// UpgradeFx draws the nanite bolt and the tether (a machine) or the crackling arc and cage (anything else)
		fxTarget(p, at, t);
		return true;
	}

	/** Tech Takeover's living branch, the old Overload: {@link #OVERLOAD_DAMAGE}, slowed and weakened 5 s (a boss 2 s). */
	public void overloadLiving(ServerPlayer p, LivingEntity e) {
		boolean boss = boss(p, e, 6f);
		int ticks = boss ? BOSS_STUN : OVERLOAD_SLOW_TICKS;
		e.invulnerableTime = 0;
		hurt(p, e, OVERLOAD_DAMAGE, false);
		e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, boss ? 0 : 2));
		e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, boss ? 0 : 1));
		if (e instanceof Mob m && m.getTarget() == p && !boss) {
			m.setTarget(null);
		}
	}

	/** A boss machine shrugs the takeover off, stunned briefly (Ultron's robots freeze outright). */
	private static void stunMachine(LivingEntity t, int ticks) {
		if (t instanceof com.projecthero.mod.ultron.entity.UltronRobot robot) {
			robot.stun(ticks);
		}
		stun(t, ticks, true);
	}

	// ---------------------------------------------------------------- Overload / EMP

	/** Golems and the mod's robots: they short out twice as hard. */
	private static boolean machine(LivingEntity e) {
		return e instanceof AbstractGolem || UpgradeTakeover.mechanical(e);
	}

	/** Shorts one creature out for Overload's {@link #OVERLOAD_DAMAGE} (golems twice that). */
	public void shortOut(ServerPlayer p, LivingEntity e, int ticks) {
		shortOut(p, e, ticks, OVERLOAD_DAMAGE);
	}

	/**
	 * Shorts one creature out: golems (machines) are knocked offline and take twice {@code damage}; anything else takes
	 * {@code damage} and is stunned briefly. v0.17.2: the damage is the caller's (was a flat 3, golems 6).
	 */
	public void shortOut(ServerPlayer p, LivingEntity e, int ticks, float damage) {
		boolean boss = boss(p, e, 6f);
		e.invulnerableTime = 0;
		if (machine(e)) {
			hurt(p, e, damage * 2f, false);
			stun(e, boss ? ticks / 3 : ticks, boss);
		} else {
			hurt(p, e, damage, false);
			stun(e, boss ? 20 : ticks / 2, boss);
		}
		if (e instanceof Mob m && m.getTarget() == p && !boss) {
			m.setTarget(null);
		}
		level(p).sendParticles(ParticleTypes.ELECTRIC_SPARK, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), 16, 0.3, 0.4, 0.3, 0.2);
	}

	/** v0.17.2: EMP Burst's damage {@code dist} blocks from its centre -- {@link #EMP_CENTER} falling to {@link #EMP_EDGE} at {@link #EMP_RADIUS}. */
	public static float empDamage(double dist) {
		float f = (float) Math.min(1.0, Math.max(0.0, dist / EMP_RADIUS));
		return EMP_CENTER + (EMP_EDGE - EMP_CENTER) * f;
	}

	/** Shift+Z (v0.17.2: his ultimate): everything within 8 shorts out, 26 at the centre to 16 at the edge (golems twice that). */
	public boolean empBurst(ServerPlayer p) {
		if (!use(p, EMP)) {
			return false;
		}
		anim(p, ANIM_EMP);
		ServerLevel l = level(p);
		Vec3 c = p.position().add(0, 1, 0);
		for (LivingEntity e : around(p, c, EMP_RADIUS)) {
			if (UpgradeTakeover.controlledBy(e, p)) {
				continue; // his own machines ride it out
			}
			shortOut(p, e, machine(e) ? 120 : 60, empDamage(e.position().add(0, e.getBbHeight() * 0.5, 0).distanceTo(c)));
			// v0.17.2 revamp: a machine left standing is his for 10 s (no cap; a boss is only shorted out)
			if (e.isAlive() && e instanceof Mob mob && UpgradeTakeover.mechanical(e) && !boss(p, e, 0f)) {
				UpgradeTakeover.take(p, mob, UpgradeTakeover.EMP_TICKS, false);
			}
		}
		for (AbstractMinecart cart : l.getEntitiesOfClass(AbstractMinecart.class, p.getBoundingBox().inflate(8))) {
			cart.setDeltaMovement(Vec3.ZERO);
		}
		UpgradeEmp.jam(l, p.blockPosition(), 6, 120);
		// v0.17.2 VFX: UpgradeFx draws the shockwave (a wall of green racing out to the edge, a dome, circuit lines cracking
		// across the ground); the server adds the sparks and a scorch
		QuirkFx.decal(l, p.position()).style(QuirkFx.DECAL_SCORCH).colour(0x08140C).size(3.2f).life(120).ground().spawn();
		l.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 16, 2.5, 1.2, 2.5, 0.4); // v0.17.2 revamp: was 60 + 40 dust
		QuirkCamera.shakeAround(l, c, 12, 0.25f, 8);
		OmnitrixSounds.play(l, c, SND_EMP, 1.4f, 0.9f);
		return true;
	}

	// ---------------------------------------------------------------- Merge

	/** C: merge with what you look at (6 blocks). False (and why) if there is nothing to merge with. */
	public boolean merge(ServerPlayer p, boolean announce) {
		ServerLevel l = level(p);
		LivingEntity living = lookTarget(p, 6);
		Entity vehicle = null;
		// v0.17.2 revamp: Ally Upgrade -- onto a squad mate / teammate as living armour (anyone else: refused, the parent's U1
		// default)
		if (living instanceof ServerPlayer other) {
			if (allyOf(p, other)) {
				return mergeWithAlly(p, other);
			}
			if (announce) {
				p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.upgrade.not_ally").withStyle(ChatFormatting.GRAY), true);
			}
			return false;
		}
		if (!pilotable(p, living)) { // v0.17.2 revamp: any machine he can pilot (was iron / snow golems only)
			living = null;
			Vec3 eye = p.getEyePosition();
			AABB box = p.getBoundingBox().expandTowards(p.getLookAngle().scale(6)).inflate(1.0);
			var hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(p, eye, eye.add(p.getLookAngle().scale(6)), box,
					e -> (e instanceof AbstractMinecart || e instanceof Boat) && e.isAlive() && e.getPassengers().isEmpty(), 36);
			vehicle = hit == null ? null : hit.getEntity();
		}
		if (living != null) {
			return mergeWith(p, living);
		}
		if (vehicle != null) {
			return mergeWith(p, vehicle);
		}
		BlockHitResult hit = blockLook(p, 6);
		if (hit.getType() == HitResult.Type.BLOCK && mergeKindOf(l.getBlockState(hit.getBlockPos()), l, hit.getBlockPos()) != null) {
			return mergeWith(p, hit.getBlockPos());
		}
		if (announce) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.upgrade.nothing_to_merge").withStyle(ChatFormatting.GRAY), true);
		}
		return false;
	}

	@Nullable
	public static MergeKind mergeKindOf(BlockState s, Level l, BlockPos pos) {
		if (l.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity) {
			return MergeKind.FURNACE;
		}
		if (s.getBlock() instanceof DispenserBlock) {
			return MergeKind.DISPENSER;
		}
		if (s.getBlock() instanceof PistonBaseBlock) {
			return MergeKind.PISTON;
		}
		return null;
	}

	/** Merges into a machine (a golem; v0.17.2 revamp: or a Sentinel, an Ultron robot...), a minecart or a boat (you pilot it). */
	public boolean mergeWith(ServerPlayer p, Entity e) {
		MergeKind kind = e instanceof AbstractMinecart ? MergeKind.MINECART : e instanceof Boat ? MergeKind.BOAT
				: pilotable(p, e) ? MergeKind.GOLEM : null;
		if (kind == null || MERGES.containsKey(p.getUUID()) || !use(p, MERGE)) {
			return false;
		}
		if (kind != MergeKind.GOLEM && !p.startRiding(e, true)) {
			cooldown(p, MERGE, 20);
			return false;
		}
		if (kind == MergeKind.GOLEM && e instanceof Mob m) {
			UpgradeTakeover.release(m); // he pilots it himself now
			m.setTarget(null);
			m.getNavigation().stop();
			p.teleportTo(e.getX(), e.getY(), e.getZ());
		}
		begin(p, new Merge(kind, p.level().dimension(), e.getId(), null), e.position().add(0, e.getBbHeight() * 0.5, 0));
		return true;
	}

	/** v0.17.2 revamp: Ally Upgrade works on squad mates and (scoreboard) teammates only. */
	public static boolean allyOf(ServerPlayer p, Entity other) {
		return other instanceof ServerPlayer o && o != p && (com.projecthero.mod.squad.Squads.areAllies(p, o) || p.isAlliedTo(o));
	}

	/**
	 * C on a squad mate (v0.17.2 revamp: Ally Upgrade, "merging to make things better"): he pours over them as living armour for
	 * {@link #MERGE_TICKS} -- +{@link #ALLY_ARMOR} armour, +{@link #ALLY_TOUGHNESS} toughness, and every
	 * {@link #ALLY_BEAM_EVERY} ticks their hits fire his Optic Beam ({@link #ALLY_BEAM_DAMAGE}). He rides on their upper back,
	 * unseen and untouchable, and can still use R. C again (or Sneak) leaves.
	 */
	public boolean mergeWithAlly(ServerPlayer p, ServerPlayer ally) {
		if (!allyOf(p, ally) || MERGES.containsKey(p.getUUID()) || !ally.isAlive() || ally.isSpectator() || ALLY_HOSTS.containsKey(ally.getUUID())
				|| !ally.getPassengers().isEmpty() || ally.getVehicle() == p) {
			return false;
		}
		if (!use(p, MERGE)) {
			return false;
		}
		if (p.isPassenger()) {
			p.stopRiding();
		}
		if (!p.startRiding(ally, true)) {
			cooldown(p, MERGE, 20);
			return false;
		}
		ALLY_HOSTS.put(ally.getUUID(), p.getUUID());
		PowerToggles.modifier(ally, Attributes.ARMOR, ALLY_ARMOR_ID, ALLY_ARMOR, AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(ally, Attributes.ARMOR_TOUGHNESS, ALLY_TOUGH_ID, ALLY_TOUGHNESS, AttributeModifier.Operation.ADD_VALUE);
		begin(p, new Merge(MergeKind.ALLY, p.level().dimension(), ally.getId(), null, ally.getUUID()), ally.position().add(0, ally.getBbHeight() * 0.5, 0));
		ally.displayClientMessage(Component.translatable("message.projecthero.omnitrix.upgrade.ally_on", p.getDisplayName()).withStyle(ChatFormatting.GREEN), true);
		return true;
	}

	/**
	 * Ally Upgrade: {@code host} (someone he is merged onto) hit {@code target} -- his Optic Beam fires from their chest, once
	 * every {@link #ALLY_BEAM_EVERY} ticks. From the attack callback; true if it fired.
	 */
	public boolean allyAttack(ServerPlayer host, LivingEntity target) {
		UUID up = ALLY_HOSTS.isEmpty() ? null : ALLY_HOSTS.get(host.getUUID());
		ServerPlayer p = up == null || host.getServer() == null ? null : host.getServer().getPlayerList().getPlayer(up);
		Merge m = p == null ? null : MERGES.get(p.getUUID());
		if (m == null || m.kind() != MergeKind.ALLY || !target.isAlive() || target == p || com.projecthero.mod.squad.Squads.areAllies(p, target)) {
			return false;
		}
		long now = p.level().getGameTime();
		if (timer(p, "ally_beam") > now) {
			return false;
		}
		setTimer(p, "ally_beam", now + ALLY_BEAM_EVERY);
		anim(p, ANIM_ALLY_BEAM);
		target.invulnerableTime = 0;
		hurt(p, target, ALLY_BEAM_DAMAGE, false);
		target.invulnerableTime = 0; // the host's own swing still lands
		fxTarget(p, target.position().add(0, target.getBbHeight() * 0.6, 0), target);
		OmnitrixSounds.play(level(p), host.position(), SND_BEAM, 0.8f, 1.3f);
		return true;
	}

	/** Merges into a furnace, dispenser / dropper or piston and supercharges it. */
	public boolean mergeWith(ServerPlayer p, BlockPos pos) {
		ServerLevel l = level(p);
		MergeKind kind = mergeKindOf(l.getBlockState(pos), l, pos);
		if (kind == null || MERGES.containsKey(p.getUUID()) || !l.mayInteract(p, pos) || !use(p, MERGE)) {
			return false;
		}
		begin(p, new Merge(kind, l.dimension(), 0, pos.immutable()), Vec3.atCenterOf(pos));
		return true;
	}

	private void begin(ServerPlayer p, Merge m, Vec3 at) {
		MERGES.put(p.getUUID(), m);
		anim(p, ANIM_MERGE);
		setFlag(p, F_MERGED, true);
		setTimer(p, "merge_end", p.level().getGameTime() + MERGE_TICKS);
		p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 40, 0, false, false, false));
		ServerLevel l = level(p);
		// v0.17.2 (user: "when upgrade takes over a block put the upgrade techno texture on it ... make an animation for him taking
		// over the block"): the target goes out in the synced timers; UpgradeFx pours him onto it, coats it in his techno skin
		// and lights its circuits (the ribbon is gone -- the client's liquid strand replaces it)
		BlockPos bp = m.pos();
		putTimers(p, T_MERGE_KIND, m.kind().ordinal() + 1L, T_MERGE_ENT, bp == null ? m.entityId() + 1L : 0L, T_MERGE_X, bp == null ? 0L : encode(bp.getX()),
				T_MERGE_Y, bp == null ? 0L : encode(bp.getY()), T_MERGE_Z, bp == null ? 0L : encode(bp.getZ()), T_MERGE_AT, p.level().getGameTime());
		l.sendParticles(SPARK, at.x, at.y, at.z, 24, 0.5, 0.5, 0.5, 0.0);
		l.sendParticles(METAL, at.x, at.y, at.z, 16, 0.4, 0.4, 0.4, 0.0);
		OmnitrixSounds.play(l, at, SND_MERGE, 1.1f, 1.0f);
		p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.upgrade.merged." + m.kind().name().toLowerCase(java.util.Locale.ROOT))
				.withStyle(ChatFormatting.GREEN), true);
	}

	/** Leaves the merge (C again, the timer, the target gone, turning back). */
	public void endMerge(ServerPlayer p, boolean sound) {
		Merge m = MERGES.remove(p.getUUID());
		boolean was = flag(p, F_MERGED);
		setFlag(p, F_MERGED, false);
		setTimer(p, "merge_end", 0);
		if (m == null && !was) {
			return;
		}
		// v0.17.2: the client sees the target go and peels the coat back off it
		putTimers(p, T_MERGE_KIND, 0L, T_MERGE_ENT, 0L, T_MERGE_X, 0L, T_MERGE_Y, 0L, T_MERGE_Z, 0L, T_MERGE_AT, 0L);
		if (m != null && m.pos() != null) {
			Vec3 c = Vec3.atCenterOf(m.pos());
			level(p).sendParticles(METAL, c.x, c.y, c.z, 18, 0.45, 0.45, 0.45, 0.0);
			level(p).sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 10, 0.4, 0.4, 0.4, 0.15);
		}
		MobEffectInstance inv = p.getEffect(MobEffects.INVISIBILITY);
		if (inv != null && inv.getDuration() <= 40) {
			p.removeEffect(MobEffects.INVISIBILITY);
		}
		if (m != null && (m.kind() == MergeKind.MINECART || m.kind() == MergeKind.BOAT) && p.isPassenger()) {
			p.stopRiding();
		}
		if (m != null && m.kind() == MergeKind.ALLY) {
			// v0.17.2 revamp: Ally Upgrade -- off their back, their armour back to their own
			ServerPlayer ally = m.ally() == null || p.getServer() == null ? null : p.getServer().getPlayerList().getPlayer(m.ally());
			if (m.ally() != null) {
				ALLY_HOSTS.remove(m.ally(), p.getUUID());
			}
			if (ally != null) {
				PowerToggles.clearModifier(ally, Attributes.ARMOR, ALLY_ARMOR_ID);
				PowerToggles.clearModifier(ally, Attributes.ARMOR_TOUGHNESS, ALLY_TOUGH_ID);
			}
			if (p.isPassenger() && (ally == null || p.getVehicle() == ally)) {
				p.stopRiding();
			}
			p.resetFallDistance();
		}
		if (m != null && m.kind() == MergeKind.GOLEM && level(p).getEntity(m.entityId()) instanceof Mob golem) {
			golem.setTarget(null);
			// step out beside it
			Vec3 side = golem.position().add(golem.getLookAngle().multiply(1, 0, 1).normalize().scale(-1.4));
			if (level(p).noCollision(p, p.getBoundingBox().move(side.subtract(p.position())))) {
				p.teleportTo(side.x, side.y, side.z);
			}
		}
		if (sound) {
			OmnitrixSounds.play(level(p), p.position(), SND_MERGE, 0.8f, 0.7f);
		}
	}

	private void tickMerge(ServerPlayer p, ServerLevel l, long now) {
		Merge m = MERGES.get(p.getUUID());
		if (m == null) {
			return;
		}
		if (now >= timer(p, "merge_end") || m.dim() != l.dimension()) {
			endMerge(p, true);
			return;
		}
		if (now % 20 == 0) {
			p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 40, 0, false, false, false));
		}
		switch (m.kind()) {
			case ALLY -> {
				// v0.17.2 revamp: Ally Upgrade holds while they live, stay in reach and he stays on (Sneak gets him off)
				ServerPlayer ally = m.ally() == null || p.getServer() == null ? null : p.getServer().getPlayerList().getPlayer(m.ally());
				if (ally == null || !ally.isAlive() || ally.level() != l || p.getVehicle() != ally) {
					endMerge(p, true);
					return;
				}
				p.resetFallDistance();
				PowerToggles.modifier(ally, Attributes.ARMOR, ALLY_ARMOR_ID, ALLY_ARMOR, AttributeModifier.Operation.ADD_VALUE);
				PowerToggles.modifier(ally, Attributes.ARMOR_TOUGHNESS, ALLY_TOUGH_ID, ALLY_TOUGHNESS, AttributeModifier.Operation.ADD_VALUE);
			}
			case FURNACE, DISPENSER, PISTON -> {
				BlockState s = l.getBlockState(m.pos());
				if (mergeKindOf(s, l, m.pos()) != m.kind() || p.distanceToSqr(Vec3.atCenterOf(m.pos())) > 25) {
					endMerge(p, true);
					return;
				}
				p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 5, 6, false, false, false));
				if (m.kind() == MergeKind.FURNACE && l.getBlockEntity(m.pos()) instanceof AbstractFurnaceBlockEntity be) {
					for (int k = 0; k < 3; k++) {
						AbstractFurnaceBlockEntity.serverTick(l, m.pos(), l.getBlockState(m.pos()), be); // four times the speed
					}
				} else if (m.kind() == MergeKind.DISPENSER && now % 4 == 0) {
					s.tick(l, m.pos(), l.random); // a shot every 4 ticks
				} else if (m.kind() == MergeKind.PISTON && now % 10 == 0) {
					pistonPunch(p, l, m.pos(), s);
				}
				if (now % 3 == 0) {
					Vec3 c = Vec3.atCenterOf(m.pos());
					l.sendParticles(SPARK, c.x, c.y, c.z, 3, 0.4, 0.4, 0.4, 0.0);
				}
			}
			case GOLEM -> {
				if (!(l.getEntity(m.entityId()) instanceof Mob golem) || !golem.isAlive()) {
					endMerge(p, true);
					return;
				}
				golem.moveTo(p.getX(), p.getY(), p.getZ(), p.getYRot(), 0f);
				golem.setYHeadRot(p.getYHeadRot());
				golem.yBodyRot = p.getYRot();
				golem.setDeltaMovement(Vec3.ZERO);
				golem.getNavigation().stop();
				if (golem.getTarget() == p) {
					golem.setTarget(null);
				}
				golem.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 30, 1, false, false));
				golem.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 30, 0, false, false));
				if (now % 6 == 0) {
					l.sendParticles(SPARK, golem.getX(), golem.getY() + golem.getBbHeight() * 0.6, golem.getZ(), 2, 0.4, 0.6, 0.4, 0.0);
				}
			}
			case MINECART, BOAT -> {
				Entity v = l.getEntity(m.entityId());
				if (v == null || !v.isAlive() || p.getVehicle() != v) {
					endMerge(p, true);
					return;
				}
				if (v instanceof AbstractMinecart cart) {
					// supercharged: always at full tilt, in whichever way it rolls (or where you look, from a standstill)
					Vec3 vel = cart.getDeltaMovement();
					Vec3 flat = new Vec3(vel.x, 0, vel.z);
					if (flat.lengthSqr() < 1.0e-4) {
						Vec3 look = p.getLookAngle();
						flat = new Vec3(look.x, 0, look.z);
					}
					if (flat.lengthSqr() > 1.0e-4) {
						cart.setDeltaMovement(flat.normalize().scale(0.6).add(0, vel.y, 0));
					}
				}
				if (now % 4 == 0) {
					l.sendParticles(SPARK, v.getX(), v.getY() + 0.4, v.getZ(), 3, 0.5, 0.2, 0.5, 0.0);
				}
			}
		}
	}

	/** A supercharged piston: whatever stands in front of it (up to 6 blocks) is hurled away. */
	private void pistonPunch(ServerPlayer p, ServerLevel l, BlockPos pos, BlockState s) {
		Direction facing = s.getValue(PistonBaseBlock.FACING);
		Vec3 dir = Vec3.atLowerCornerOf(facing.getNormal());
		AABB box = new AABB(pos.relative(facing)).expandTowards(dir.scale(5)).inflate(0.4);
		for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, box, e -> e != p && e.isAlive())) {
			e.invulnerableTime = 0;
			hurt(p, e, 4f, false);
			double res = 1.0 - Math.min(1.0, e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
			e.push(dir.x * 2.2 * res, 0.35 + dir.y * 2.2 * res, dir.z * 2.2 * res);
			e.hurtMarked = true;
		}
		Vec3 face = Vec3.atCenterOf(pos).add(dir.scale(0.6));
		QuirkFx.cone(l, face, dir).colours(0xFFFFFF, COLOUR).size(0.9f).length(5f).life(6).style(QuirkFx.CONE_DEBRIS).spawn();
		l.playSound(null, pos, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.8f, 1.4f);
	}

	// ---------------------------------------------------------------- Shift+C: upgrade the held tool

	/** Can the Upgrade Tool do anything for {@code s} (worn, or short of Unbreaking III)? */
	private static boolean upgradable(ServerPlayer p, ItemStack s) {
		if (s.isEmpty() || !s.isDamageableItem()) {
			return false;
		}
		var unbreaking = p.level().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.UNBREAKING);
		return s.isDamaged() || EnchantmentHelper.getItemEnchantmentLevel(unbreaking, s) < 3;
	}

	/**
	 * v0.17.2 revamp: what the Upgrade Tool works on -- the main-hand item; failing that the offhand one; failing that, while he
	 * looks down (pitch 45+), the most worn armour piece he wears (or the first short of Unbreaking III). Null if none.
	 */
	@Nullable
	public static ItemStack toolTarget(ServerPlayer p) {
		ItemStack main = p.getMainHandItem();
		if (!main.isEmpty() && main.isDamageableItem()) {
			return main;
		}
		ItemStack off = p.getOffhandItem();
		if (!off.isEmpty() && off.isDamageableItem()) {
			return off;
		}
		if (p.getXRot() >= 45f) {
			ItemStack best = null;
			for (net.minecraft.world.entity.EquipmentSlot slot : new net.minecraft.world.entity.EquipmentSlot[] { net.minecraft.world.entity.EquipmentSlot.CHEST,
					net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET, net.minecraft.world.entity.EquipmentSlot.HEAD }) {
				ItemStack s = p.getItemBySlot(slot);
				if (!upgradable(p, s)) {
					continue;
				}
				if (best == null || s.getDamageValue() > best.getDamageValue()) {
					best = s;
				}
			}
			return best;
		}
		return null;
	}

	/**
	 * Shift+V (v0.17.2 revamp, was Shift+C): repairs half the wear of {@link #toolTarget}; an unworn one gains a level of
	 * Unbreaking (up to III). True if anything changed.
	 */
	public boolean upgradeTool(ServerPlayer p) {
		ItemStack held = toolTarget(p);
		if (held == null) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.upgrade.no_tool").withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		var unbreaking = p.level().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.UNBREAKING);
		int level = EnchantmentHelper.getItemEnchantmentLevel(unbreaking, held);
		if (!held.isDamaged() && level >= 3) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.upgrade.maxed").withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		if (!use(p, REPAIR)) {
			return false;
		}
		anim(p, ANIM_REPAIR);
		if (held.isDamaged()) {
			int fix = Math.max(1, held.getMaxDamage() / 2);
			held.setDamageValue(Math.max(0, held.getDamageValue() - fix));
		} else {
			held.enchant(unbreaking, level + 1);
		}
		ServerLevel l = level(p);
		l.sendParticles(SPARK, p.getX(), p.getY() + 1.1, p.getZ(), 24, 0.35, 0.3, 0.35, 0.0);
		l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SMITHING_TABLE_USE, SoundSource.PLAYERS, 0.6f, 1.6f);
		OmnitrixSounds.play(l, p.position(), SND_MERGE, 0.7f, 1.5f);
		p.swing(InteractionHand.MAIN_HAND, true);
		return true;
	}

	// ---------------------------------------------------------------- vanilla keys

	@Override
	public boolean onAttack(ServerPlayer p, Entity target) {
		// v0.17.2 revamp: a punch from the puddle is the Geyser
		if (flag(p, F_SLIDE) && geyser(p)) {
			return true;
		}
		Merge m = MERGES.get(p.getUUID());
		if (m != null && m.kind() == MergeKind.GOLEM && level(p).getEntity(m.entityId()) instanceof Mob golem && target instanceof LivingEntity le) {
			// piloting a golem: the golem swings, not you
			if (golem instanceof SnowGolem snow) {
				snow.performRangedAttack(le, 1f);
			} else {
				golem.doHurtTarget(le);
				level(p).broadcastEntityEvent(golem, (byte) 4);
			}
			return true;
		}
		if (target instanceof LivingEntity le) {
			anim(p, ANIM_JOLT);
			fxTarget(p, le.position().add(0, le.getBbHeight() * 0.6, 0), le); // v0.17.2 VFX: the arc from his fist
			le.invulnerableTime = 0;
			hurt(p, le, JOLT_DAMAGE, false);
			le.invulnerableTime = 0;
			if (machine(le)) {
				le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 2));
			}
			level(p).sendParticles(ParticleTypes.ELECTRIC_SPARK, le.getX(), le.getY() + le.getBbHeight() * 0.6, le.getZ(), 6, 0.2, 0.3, 0.2, 0.15);
		}
		return false;
	}

	@Override
	public InteractionResult onUseBlock(ServerPlayer p, InteractionHand hand, BlockHitResult hit) {
		if (hand != InteractionHand.MAIN_HAND || !p.getMainHandItem().isEmpty()) {
			return InteractionResult.PASS;
		}
		return interfaceWith(p, hit.getBlockPos()) ? InteractionResult.SUCCESS : InteractionResult.PASS;
	}

	/** Use on an iron door or trapdoor: Upgrade talks to the mechanism and it opens (or shuts). */
	public boolean interfaceWith(ServerPlayer p, BlockPos pos) {
		ServerLevel l = level(p);
		BlockState s = l.getBlockState(pos);
		if (!l.mayInteract(p, pos) || !ready(p, INTERFACE)) {
			return false;
		}
		if (s.is(Blocks.IRON_DOOR) && s.getBlock() instanceof DoorBlock door) {
			door.setOpen(p, l, s, pos, !door.isOpen(s));
		} else if (s.is(Blocks.IRON_TRAPDOOR)) {
			boolean open = !s.getValue(TrapDoorBlock.OPEN);
			l.setBlock(pos, s.setValue(TrapDoorBlock.OPEN, open), 10);
			l.playSound(null, pos, open ? SoundEvents.IRON_TRAPDOOR_OPEN : SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 1.0f, 1.0f);
		} else {
			return false;
		}
		cooldown(p, INTERFACE, -1);
		anim(p, ANIM_JOLT);
		fxTarget(p, Vec3.atCenterOf(pos), null); // v0.17.2 VFX: the arc into the mechanism
		l.sendParticles(SPARK, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 10, 0.3, 0.4, 0.3, 0.0);
		p.swing(InteractionHand.MAIN_HAND, true);
		return true;
	}

	// ---------------------------------------------------------------- damage

	@Override
	public boolean allowDamage(ServerPlayer p, DamageSource source, float amount) {
		// v0.17.2 revamp: his own held machines never hurt him (UpgradeTakeover's damage rule covers it too)
		if (source.getEntity() != null && UpgradeTakeover.controlledBy(source.getEntity(), p)) {
			return false;
		}
		Merge m = MERGES.get(p.getUUID());
		// v0.17.2 revamp (Electric Shock): a lightning / electric hit while merged throws him out, stunned 1 s; then it lands
		// as usual (x1.5, incomingDamage)
		if (m != null && com.projecthero.mod.ultron.UltronCombat.isElectric(source)) {
			endMerge(p, true);
			stun(p, SHOCK_STUN, false);
			level(p).sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 1, p.getZ(), 10, 0.3, 0.5, 0.3, 0.2);
			m = null;
		}
		if (m == null) {
			return !(flag(p, F_SLIDE) && source.is(DamageTypes.IN_WALL));
		}
		if (source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.FALL)) {
			return false;
		}
		if (m.kind() == MergeKind.ALLY) {
			// Ally Upgrade: he is their armour -- nothing reaches him (the void and /kill still do)
			return source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
		}
		if (m.kind() == MergeKind.GOLEM && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
				&& level(p).getEntity(m.entityId()) instanceof LivingEntity golem && golem.isAlive()) {
			golem.hurt(source, amount); // inside the golem: it takes the hit
			return false;
		}
		return true;
	}

	@Override
	public float incomingDamage(ServerPlayer p, DamageSource source, float amount) {
		Merge m = MERGES.get(p.getUUID());
		float a = m != null && m.kind() != MergeKind.GOLEM ? amount * 0.5f : amount;
		// v0.17.2 revamp (Electric Shock): lightning and electric attacks hit living circuitry half as hard again
		return com.projecthero.mod.ultron.UltronCombat.isElectric(source) ? a * SHOCK_MULT : a;
	}

	// ---------------------------------------------------------------- v0.17.2 revamp: passives the mixins ask (both sides)

	/** Liquid Metal Body: a projectile that passed through him leaves a green ripple where it went in, and flies on. */
	public static final net.minecraft.world.entity.projectile.ProjectileDeflection PASS_THROUGH = (proj, entity, random) -> {
		if (proj.level() instanceof ServerLevel l) {
			QuirkFx.ring(l, proj.position()).style(QuirkFx.RING_SOFT).colours(0xB8FFB0, COLOUR).size(0.9f).thickness(0.12f).life(8).spawn();
		}
	};

	/**
	 * Liquid Metal Body: does {@code proj} pass straight through {@code self}? An Upgrade (synced state), an arrow or a thrown
	 * thing, and one in {@link #PASS_ONE_IN} of them -- picked from the projectile's own id, so both sides agree. Beams, fireballs
	 * and the like always hit.
	 */
	public static boolean passesThrough(Entity self, net.minecraft.world.entity.projectile.Projectile proj) {
		if (!(self instanceof Player pl) || !(AlienKit.activeKit(pl) instanceof UpgradeKit)) {
			return false;
		}
		if (!(proj instanceof net.minecraft.world.entity.projectile.AbstractArrow || proj instanceof net.minecraft.world.entity.projectile.ThrowableProjectile)) {
			return false;
		}
		return Math.floorMod(proj.getUUID().getLeastSignificantBits(), PASS_ONE_IN) == 0;
	}

	/**
	 * Ally Upgrade's seat: on {@code vehicle}'s upper back, his eyes a little over their head (clear of their aim), turned with
	 * their body. Null unless {@code passenger} is a merged Upgrade riding a player. Synced state only.
	 */
	@Nullable
	public static Vec3 allySeat(Entity vehicle, Entity passenger) {
		if (!(passenger instanceof Player up) || !(vehicle instanceof Player host) || !(AlienKit.activeKit(up) instanceof UpgradeKit)
				|| (com.projecthero.mod.omnitrix.Omnitrix.state(up).kitFlags & F_MERGED) == 0) {
			return null;
		}
		double y = Math.max(0.0, host.getEyeHeight() - up.getEyeHeight() + 0.35);
		return new Vec3(0.0, y, -0.5).yRot(-host.yBodyRot * net.minecraft.util.Mth.DEG_TO_RAD);
	}

	/** Is {@code p} an Upgrade with Overclock running (the gun hooks ask)? */
	public static boolean overclocked(Player p) {
		AlienKit k = AlienKit.activeKit(p);
		return k instanceof UpgradeKit u && u.flag(p, F_OVERCLOCK);
	}

	/**
	 * Overclock's weapon boost: an arrow just loosed by an overclocked Upgrade (a bow or crossbow in hand, the arrow still at his
	 * side and flying) pierces one target and flies {@link #ARROW_SPEED}x as fast. True if it was boosted.
	 */
	public static boolean boostArrow(net.minecraft.world.entity.projectile.AbstractArrow arrow) {
		if (!(arrow.getOwner() instanceof ServerPlayer p) || !overclocked(p) || arrow.tickCount > 1 || arrow.distanceToSqr(p) > 16
				|| arrow.getDeltaMovement().lengthSqr() < 0.25) {
			return false;
		}
		boolean bow = p.getMainHandItem().getItem() instanceof net.minecraft.world.item.ProjectileWeaponItem
				|| p.getOffhandItem().getItem() instanceof net.minecraft.world.item.ProjectileWeaponItem;
		if (!bow) {
			return false;
		}
		if (arrow.getPierceLevel() < 1) {
			((com.projecthero.mod.mixin.OmnitrixUpgradeArrowAccessor) arrow).projecthero$setPierceLevel((byte) 1);
		}
		arrow.setDeltaMovement(arrow.getDeltaMovement().scale(ARROW_SPEED));
		arrow.hasImpulse = true;
		return true;
	}

	// ---------------------------------------------------------------- v0.17.1 (user: "add abilities to the V key for every alien"): V Overclock

	public static final String OVERCLOCK = "overclock";
	public static final int ANIM_OVERCLOCK = 13;
	public static final int F_OVERCLOCK = 8;
	public static final int OVERCLOCK_TICKS = 300;
	public static final double OVERCLOCK_MINING = 1.0;
	public static final double OVERCLOCK_DAMAGE = 4.0;
	private static final net.minecraft.resources.ResourceLocation OC_MINE = com.projecthero.mod.hero.power.PowerToggles.id("omnitrix_upgrade_overclock_mine");
	private static final net.minecraft.resources.ResourceLocation OC_HIT = com.projecthero.mod.hero.power.PowerToggles.id("omnitrix_upgrade_overclock_hit");

	private void vKey(ServerPlayer p, boolean shift) {
		if (shift) {
			upgradeTool(p); // v0.17.2 revamp: was Shift+C
		} else {
			overclock(p);
		}
	}

	/**
	 * V: Overclock -- Upgrade flows into the tool or weapon in his hand and runs it past its limits for 15 s: +100% mining speed,
	 * +4 damage, and its wear knits back a point a second. Needs something in the main hand. v0.17.2 revamp (Ultimate Upgrade's
	 * weapon boost): a bow / crossbow's arrows pierce one and fly 50% faster ({@link #boostArrow}); the mod's guns fire 50%
	 * faster ({@code UpgradeOverclockHooks}) and never need reloading; a shield shrugs off a disabling hit (no cooldown).
	 */
	public boolean overclock(ServerPlayer p) {
		if (p.getMainHandItem().isEmpty()) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.upgrade.needs_gear").withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		if (flag(p, F_OVERCLOCK) || !use(p, OVERCLOCK)) {
			return false;
		}
		setFlag(p, F_OVERCLOCK, true);
		setTimer(p, "overclock_end", p.level().getGameTime() + OVERCLOCK_TICKS);
		com.projecthero.mod.hero.power.PowerToggles.modifier(p, net.minecraft.world.entity.ai.attributes.Attributes.BLOCK_BREAK_SPEED, OC_MINE, OVERCLOCK_MINING,
				net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		com.projecthero.mod.hero.power.PowerToggles.modifier(p, net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE, OC_HIT, OVERCLOCK_DAMAGE,
				net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE);
		anim(p, ANIM_OVERCLOCK);
		ServerLevel l = level(p);
		l.sendParticles(SPARK, p.getX(), p.getY() + 1, p.getZ(), 30, 0.4, 0.6, 0.4, 0.0);
		OmnitrixSounds.play(l, p.position(), SND_MERGE, 0.9f, 1.5f);
		p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.upgrade.overclock_on", p.getMainHandItem().getHoverName())
				.withStyle(ChatFormatting.GREEN), true);
		return true;
	}

	private void endOverclock(ServerPlayer p) {
		if (!flag(p, F_OVERCLOCK)) {
			return;
		}
		setFlag(p, F_OVERCLOCK, false);
		setTimer(p, "overclock_end", 0);
		com.projecthero.mod.hero.power.PowerToggles.clearModifier(p, net.minecraft.world.entity.ai.attributes.Attributes.BLOCK_BREAK_SPEED, OC_MINE);
		com.projecthero.mod.hero.power.PowerToggles.clearModifier(p, net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE, OC_HIT);
		OmnitrixSounds.play(level(p), p.position(), SND_MERGE, 0.6f, 0.8f);
	}

	private void revertV(ServerPlayer p) {
		endOverclock(p);
	}

	private void tickV(ServerPlayer p, ServerLevel l, long now) {
		if (!flag(p, F_OVERCLOCK)) {
			return;
		}
		if (now >= timer(p, "overclock_end") || p.getMainHandItem().isEmpty()) {
			endOverclock(p);
			return;
		}
		net.minecraft.world.item.ItemStack held = p.getMainHandItem();
		if (now % 20 == 0 && held.isDamaged()) {
			held.setDamageValue(held.getDamageValue() - 1);
		}
		// v0.17.2 revamp (weapon boost; the hand's rings are UpgradeClient's, so the spark stream is gone): a gun from the mod's
		// firearm engine never runs dry or reloads; a shield is never disabled
		boostGear(p, held);
		boostGear(p, p.getOffhandItem());
	}

	/** Overclock's weapon boost on one held stack: a firearm stays loaded, a shield's cooldown is wiped. */
	static void boostGear(ServerPlayer p, ItemStack s) {
		if (s.isEmpty()) {
			return;
		}
		com.projecthero.mod.firearm.FirearmData gun = com.projecthero.mod.firearm.Firearms.of(s);
		if (gun != null) {
			if (com.projecthero.mod.firearm.FirearmStack.isReloading(s)) {
				com.projecthero.mod.firearm.FirearmReload.cancel(s);
			}
			if (com.projecthero.mod.firearm.FirearmStack.magazine(s, gun) < gun.magazineSize) {
				com.projecthero.mod.firearm.FirearmStack.setMagazine(s, gun.magazineSize);
			}
		}
		if (s.getItem() instanceof net.minecraft.world.item.ShieldItem && p.getCooldowns().isOnCooldown(s.getItem())) {
			p.getCooldowns().removeCooldown(s.getItem());
		}
	}
}
