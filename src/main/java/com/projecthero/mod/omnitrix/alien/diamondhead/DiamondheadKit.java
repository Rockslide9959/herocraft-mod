package com.projecthero.mod.omnitrix.alien.diamondhead;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.joml.Vector3f;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.power.PowerToggles;
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

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;

/**
 * v0.17.1: Diamondhead, the Petrosapien -- a body of living green crystal with a pointed crystal head and shard ridges down
 * his back and shoulders. Heavy armour, a little slow; a share of the arrows and projectiles that reach him glance back the
 * way they came. Every construct he raises is temporary {@link DiamondheadBlocks#CRYSTAL} (through {@code TempBlocks}).
 *
 * <p>v0.17.2 revamp (user: "his blade hand thing should be a full on combat mode and he should also have a shield ... do research
 * on his powers and look at the stuff he does in the show"): in the show he grows his arms into blades, raises crystal shields
 * and walls, fires shards and refracts lasers back at whoever fired them. So: a held crystal SHIELD on G that reflects what hits
 * it (and stores the light it catches for the Prism Beam), and a Combat Mode on V that turns both forearms into blades and
 * swaps R / Shift+R / Z for blade moves.
 *
 * <pre>
 *   Passive  Petrosapien Body  40% of projectiles glance back; melee attackers are cut on his crystal (2)
 *   R        Shard Volley      a fan of 5 crystal shards (4 each)             Shift+R  Crystal Spear    one huge spear (18, knockback)
 *   G        Spike Line        crystal spikes burst up in a 12-block line ahead (16, launch)
 *                                                                            Shift+G  Crystal Pillar   a pillar bursts up under the one you look at
 *                                                                                                      (22 blocks; 14, launched)
 *   X        Crystal Slide     toggle: ride a crystal ramp along your look   Shift+X  Crystal Vault    a pillar shoots up under you
 *   Z        Crystal Eruption  rings of spikes all round you (r 6; 12)       Shift+Z  Titan Shards     ultimate: lifts 3 giant shards, hurls them
 *   V        Combat Mode       toggle: both forearms are blades -- +6 melee, +1 reach, faster swings, every swing sweeps
 *                                                                            Shift+V  Crystal Construct  a wall ahead; look up: a dome round you;
 *                                                                                                      look down: a bridge ahead
 *   C        Crystal Prison    seal the creature you look at for 3 s (a boss 1 s), then it shatters (14)
 *            Shift+C  Crystal Tether   crystal reaches out: hauls a foe in, or pulls him to a wall
 *   Combat Mode keys: R Blade Flurry (slash 8, slash 8, stab 14 -- chained within 1.5 s), Shift+R Crystal Cyclone (r 4; 12,
 *   slowed); held Use the crystal shield (frontal hits blocked and reflected; 40 HP), running into foes behind it bashes them
 *   v0.17.2 (user: "dont make the projectiles shoot from his face, make them shoot from his hands"): every shard leaves a hand
 *   Attack   Crystal Edge (blades: the slash sweeps)   Use on a block (empty hand)   a crystal block for 30 s
 * </pre>
 */
public final class DiamondheadKit extends AlienKit {
	public static final String ID = "diamondhead";
	public static final int COLOUR = 0x4FE0C8;
	public static final int DEEP = 0x1E9A6A;

	public static final int ANIM_VOLLEY = 1;
	public static final int ANIM_SPEAR = 2;
	/** v0.17.2: Combat Mode on -- the blades grow out of both forearms. */
	public static final int ANIM_COMBAT_ON = 3;
	/** The Shatter Line (the old Spike Line's pose). */
	public static final int ANIM_SHATTER = 4;
	public static final int ANIM_SLIDE = 5;
	public static final int ANIM_VAULT = 6;
	public static final int ANIM_WALL = 7;
	public static final int ANIM_BARRAGE = 9;
	public static final int ANIM_PRISON = 10;
	public static final int ANIM_SLASH = 11;
	public static final int ANIM_PLACE = 12;
	public static final int ANIM_PILLAR = 13;
	public static final int ANIM_ERUPT = 14;
	public static final int ANIM_COMBAT_OFF = 15;
	public static final int ANIM_SHIELD = 16;
	public static final int ANIM_SHIELD_DOWN = 17;
	public static final int ANIM_BASH = 18;
	/** The Blade Flurry's three beats: a right-to-left slash, a left-to-right slash, the stab. */
	public static final int ANIM_FLURRY_1 = 19;
	public static final int ANIM_FLURRY_2 = 20;
	public static final int ANIM_STAB = 21;
	public static final int ANIM_CYCLONE = 22;
	public static final int ANIM_SHIELD_BREAK = 23;

	/** v0.17.2: Combat Mode (the bit the old Blade Arm had). */
	public static final int F_COMBAT = 1;
	public static final int F_SHIELD = 2;
	public static final int F_SLIDING = 4;
	public static final int F_BASH = 8;

	public static final String VOLLEY = "shard_volley";
	public static final String SPEAR = "crystal_spear";
	public static final String SLIDE = "crystal_slide";
	public static final String VAULT = "crystal_vault";
	public static final String WALL = "crystal_wall";
	public static final String BARRAGE = "diamond_barrage";
	public static final String PRISON = "crystal_prison";
	/** v0.17.2 (user: "Make G spike line and shift+G a different attack move"): the pillar that bursts up under a foe. */
	public static final String PILLAR = "crystal_pillar";
	public static final String EDGE = "crystal_edge";
	public static final String PLACE = "crystal_block";
	/** v0.17.2: Z -- the Crystal Eruption. */
	public static final String ERUPT = "crystal_eruption";
	/** v0.17.2 revamp: G held, Shift+G, V. */
	public static final String SHIELD = "crystal_shield";
	public static final String BASH = "shield_bash";
	public static final String COMBAT = "combat_mode";
	/** Combat Mode's R / Shift+R / Z. */
	public static final String FLURRY = "blade_flurry";
	public static final String CYCLONE = "crystal_cyclone";
	public static final String SHATTER = "shatter_line";

	// v0.17.2 (user: "change the damage on the ben 10 aliens to be on par with the elemental power abilities"): was 3
	public static final float SHARD_DAMAGE = 4f;
	public static final float SPEAR_DAMAGE = 18f; // v0.17.2: was 12
	/** v0.17.2: the Diamond Barrage's falling shards (was SHARD_DAMAGE, 3): about 26 a target over its 2 s. */
	public static final float BARRAGE_SHARD_DAMAGE = 5f;
	/** Combat Mode's melee bonus (the old Blade Arm's), reach and swing speed (+50% of the base). */
	public static final double BLADE_BONUS = 6.0;
	public static final double BLADE_REACH = 1.0;
	public static final double BLADE_SPEED = 0.5;
	/** The Shatter Line (the old Spike Line, now Combat Mode's Z). */
	public static final float SPIKE_DAMAGE = 16f; // v0.17.2: was 6
	public static final int SPIKE_LENGTH = 12;
	public static final int SHATTER_COOLDOWN = 160;
	/** v0.17.2: the Crystal Eruption -- damage (a Z "stronger move", on par with the elemental powers'), reach and cooldown. */
	public static final float ERUPT_DAMAGE = 12f;
	public static final double ERUPT_RADIUS = 6.0;
	/** The eruption's rings, one a tick, from {@link #ERUPT_INNER} out to {@link #ERUPT_RADIUS}. */
	public static final int ERUPT_RINGS = 5;
	public static final double ERUPT_INNER = 1.5;
	public static final int ERUPT_COOLDOWN = 180;
	/** Slowness II on whatever it throws (a boss only a quarter of it). */
	public static final int ERUPT_SLOW_TICKS = 60;
	public static final int WALL_TICKS = 160;
	/** v0.17.2: how long each crystal of the slide's ramp stays behind him (ticks). */
	public static final int SLIDE_TICKS = 40;
	public static final int PLACE_TICKS = 600;
	public static final int BARRAGE_TICKS = 40;
	/** v0.17.2: C -- the Crystal Prison: reach, how long it holds (a boss a third of it), the shatter's damage and the cooldown. */
	public static final double PRISON_RANGE = 16.0;
	public static final int PRISON_TICKS = 60;
	public static final float PRISON_DAMAGE = 14f;
	public static final int PRISON_COOLDOWN = 240;
	/** v0.17.2: Shift+C -- the Prism Beam: reach, damage, the open-daylight bonus and the cooldown. */
	public static final double PILLAR_RANGE = 22.0;
	public static final float PILLAR_DAMAGE = 14f;
	public static final int PILLAR_COOLDOWN = 140;
	/** Shift+V looking this far up builds the dome, this far down the bridge (degrees of pitch). */
	public static final float CONSTRUCT_PITCH = 30f;
	/** The dome: its radius (blocks, a shell one block thick round his feet). */
	public static final double DOME_RADIUS = 3.6;
	/** The passive: the share of incoming projectiles that glance back. */
	public static final float REFLECT_CHANCE = 0.4f;
	/** The passive: what a melee attacker takes from his sharp crystal. */
	public static final float CUT_DAMAGE = 2f;

	/** The Crystal Shield: what it soaks before it shatters, how long it takes to regrow after, and its regrowth while lowered. */
	public static final float SHIELD_HP = 40f;
	public static final int SHIELD_BROKEN_TICKS = 160;
	/** HP back every 10 ticks while the shield is lowered (4 a second: empty to full in 10 s). */
	public static final float SHIELD_REGEN = 2f;
	/** Slower behind it (-25% speed). */
	public static final double SHIELD_SLOW = -0.25;
	/**
	 * v0.17.2 (user): the Shield Bash -- running into a foe behind the raised shield: the damage, how fast he must be going (blocks a
	 * tick, toward where he faces), and how soon the same foe can be bashed again.
	 */
	public static final float BASH_DAMAGE = 10f;
	public static final double BASH_MIN_SPEED = 0.12;
	public static final int BASH_REHIT_TICKS = 20;
	/** The shield stays up this long after the last right-click use arrives (vanilla repeats uses while the button is held). */
	public static final int SHIELD_HOLD_GRACE = 6;
	/** v0.17.2 (user): G -- the Crystal Tether. */
	public static final String TETHER = "crystal_tether";
	public static final double TETHER_RANGE = 22.0;
	public static final float TETHER_DAMAGE = 6f;
	public static final int TETHER_COOLDOWN = 80;
	/** v0.17.2 (user): Shift+G -- the Crystal Bridge. */
	public static final String BRIDGE = "crystal_bridge";
	public static final int BRIDGE_LENGTH = 20;
	public static final int BRIDGE_TICKS = 400;
	public static final int BRIDGE_COOLDOWN = 300;
	/** Combat Mode R: the Blade Flurry -- two slashes then the stab, each within {@link #FLURRY_WINDOW} of the last. */
	public static final float FLURRY_SLASH_DAMAGE = 8f;
	public static final float FLURRY_STAB_DAMAGE = 14f;
	public static final int FLURRY_WINDOW = 30;
	public static final int FLURRY_GAP = 5;
	/** After the stab. */
	public static final int FLURRY_COOLDOWN = 40;
	public static final double FLURRY_REACH = 4.0;
	public static final double STAB_REACH = 5.5;
	/** Combat Mode Shift+R: the Crystal Cyclone. */
	public static final float CYCLONE_DAMAGE = 12f;
	public static final double CYCLONE_RADIUS = 4.0;
	public static final int CYCLONE_COOLDOWN = 140;
	public static final int CYCLONE_SLOW_TICKS = 60;

	// v0.17.1 sound rework: OmnitrixSfx recipes from vanilla samples (see docs/OMNITRIX_SOUNDS_0171.md)
	/** A crystal shard flung: a quick whoosh and a short crystal crack. */
	public static final OmnitrixSfx.Cue SND_SHARD = OmnitrixSfx.cue("diamondhead_shard")
			.layer(SoundEvents.TRIDENT_THROW, 0.28f, 1.5f)
			.layer(SoundEvents.POINTED_DRIPSTONE_BREAK, 0.35f, 1.3f)
			.gap(3);
	/** Crystal growing: a dense crunch, no ring. */
	public static final OmnitrixSfx.Cue SND_GROW = OmnitrixSfx.cue("diamondhead_grow")
			.layer(SoundEvents.CALCITE_PLACE, 0.45f, 0.7f)
			.layer(SoundEvents.POINTED_DRIPSTONE_BREAK, 0.25f, 0.8f)
			.layer(SoundEvents.DEEPSLATE_PLACE, 0.25f, 0.8f, 1);
	/** Crystal shattering: a short, low glass crash and a ceramic crunch. */
	public static final OmnitrixSfx.Cue SND_SHATTER = OmnitrixSfx.cue("diamondhead_shatter")
			.layer(SoundEvents.GLASS_BREAK, 0.3f, 0.8f)
			.layer(SoundEvents.DECORATED_POT_SHATTER, 0.35f, 1.0f)
			.layer(SoundEvents.CALCITE_BREAK, 0.3f, 0.9f)
			.gap(3);
	/** The arm blades forming: a crystal crunch and a swipe. */
	public static final OmnitrixSfx.Cue SND_BLADE = OmnitrixSfx.cue("diamondhead_blade")
			.layer(SoundEvents.CALCITE_PLACE, 0.35f, 0.6f)
			.layer(SoundEvents.POINTED_DRIPSTONE_BREAK, 0.3f, 0.7f)
			.layer(SoundEvents.PLAYER_ATTACK_SWEEP, 0.25f, 1.3f);
	/** v0.17.2: the shield growing over the arm: a deep amethyst chime over a crystal crunch. */
	public static final OmnitrixSfx.Cue SND_SHIELD_UP = OmnitrixSfx.cue("diamondhead_shield_up")
			.layer(SoundEvents.AMETHYST_BLOCK_PLACE, 0.6f, 0.6f)
			.layer(SoundEvents.CALCITE_PLACE, 0.4f, 0.7f)
			.layer(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.3f, 0.8f, 2)
			.gap(4);
	/** A hit on the shield: a dull block and a ringing crystal knock. */
	public static final OmnitrixSfx.Cue SND_SHIELD_HIT = OmnitrixSfx.cue("diamondhead_shield_hit")
			.layer(SoundEvents.SHIELD_BLOCK, 0.4f, 0.8f)
			.layer(SoundEvents.AMETHYST_BLOCK_HIT, 0.6f, 0.7f)
			.layer(SoundEvents.AMETHYST_CLUSTER_STEP, 0.4f, 1.2f, 1)
			.gap(2);
	/** The shield shattering: a big glass crash, crystal clusters breaking, a low ceramic crunch. */
	public static final OmnitrixSfx.Cue SND_SHIELD_BREAK = OmnitrixSfx.cue("diamondhead_shield_break")
			.layer(SoundEvents.GLASS_BREAK, 0.6f, 0.6f)
			.layer(SoundEvents.AMETHYST_CLUSTER_BREAK, 0.6f, 0.7f)
			.layer(SoundEvents.DECORATED_POT_SHATTER, 0.45f, 0.8f)
			.layer(SoundEvents.AMETHYST_BLOCK_BREAK, 0.5f, 0.9f, 2);
	/** The Shield Bash's impact. */
	public static final OmnitrixSfx.Cue SND_BASH = OmnitrixSfx.cue("diamondhead_bash")
			.layer(SoundEvents.SHIELD_BLOCK, 0.55f, 0.6f)
			.layer(SoundEvents.IRON_GOLEM_ATTACK, 0.4f, 0.9f)
			.layer(SoundEvents.CALCITE_BREAK, 0.45f, 0.7f)
			.gap(3);
	/** A blade slash: a sweep and a bright crystal edge. */
	public static final OmnitrixSfx.Cue SND_SLASH = OmnitrixSfx.cue("diamondhead_slash")
			.layer(SoundEvents.PLAYER_ATTACK_SWEEP, 0.45f, 1.2f)
			.layer(SoundEvents.AMETHYST_BLOCK_HIT, 0.4f, 1.5f)
			.layer(SoundEvents.AMETHYST_CLUSTER_STEP, 0.3f, 1.8f, 1)
			.gap(2);
	/** The stab: a thrust, a crit and a crystal crack. */
	public static final OmnitrixSfx.Cue SND_STAB = OmnitrixSfx.cue("diamondhead_stab")
			.layer(SoundEvents.TRIDENT_THROW, 0.4f, 0.8f)
			.layer(SoundEvents.PLAYER_ATTACK_CRIT, 0.45f, 0.8f)
			.layer(SoundEvents.POINTED_DRIPSTONE_BREAK, 0.45f, 1.0f, 1)
			.gap(3);
	/** The Crystal Cyclone: a deep whirl and a ringing chime. */
	public static final OmnitrixSfx.Cue SND_CYCLONE = OmnitrixSfx.cue("diamondhead_cyclone")
			.layer(SoundEvents.PLAYER_ATTACK_SWEEP, 0.55f, 0.7f)
			.layer(SoundEvents.BREEZE_WIND_CHARGE_BURST, 0.2f, 1.2f)
			.layer(SoundEvents.PLAYER_ATTACK_SWEEP, 0.45f, 0.9f, 3)
			.layer(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.45f, 1.3f, 4)
			.gap(4);
	/** The beam: a bright crystal chime over a beacon's hum. */
	public static final OmnitrixSfx.Cue SND_BEAM = OmnitrixSfx.cue("diamondhead_beam")
			.layer(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.6f, 1.4f)
			.layer(SoundEvents.BEACON_POWER_SELECT, 0.35f, 1.8f)
			.layer(SoundEvents.AMETHYST_CLUSTER_BREAK, 0.4f, 1.2f, 2);

	private static final ResourceLocation BLADE_DAMAGE_ID = PowerToggles.id("omnitrix_diamondhead_blade");
	private static final ResourceLocation BLADE_REACH_ID = PowerToggles.id("omnitrix_diamondhead_blade_reach");
	private static final ResourceLocation BLADE_SPEED_ID = PowerToggles.id("omnitrix_diamondhead_blade_speed");
	private static final ResourceLocation SHIELD_SLOW_ID = PowerToggles.id("omnitrix_diamondhead_shield_slow");
	/** An entity tag: this projectile was already judged by the reflection passive (once per flight). */
	public static final String SEEN_TAG = "projecthero.diamondhead_seen";
	static final DustParticleOptions SPARKLE = new DustParticleOptions(new Vector3f(0.45f, 1.0f, 0.82f), 0.9f);

	/** player -> the Shatter Line running. */
	private static final Map<UUID, SpikeLine> LINES = new ConcurrentHashMap<>();
	/** player -> where the Diamond Barrage rains. */
	private static final Map<UUID, Storm> STORMS = new ConcurrentHashMap<>();
	/** player -> the Titan Shards being raised and thrown. */
	private static final Map<UUID, Titan> TITANS = new ConcurrentHashMap<>();
	/** player -> the creature his Crystal Prison holds. */
	private static final Map<UUID, Prison> PRISONS = new ConcurrentHashMap<>();
	/** player -> the Crystal Eruption spreading out from him. */
	private static final Map<UUID, Eruption> ERUPTIONS = new ConcurrentHashMap<>();
	/** player -> creatures this Shield Bash already struck. */
	private static final Map<UUID, Map<Integer, Long>> BASH_HITS = new ConcurrentHashMap<>();
	/** Set while the passive's cut lands (a cut never answers a cut: two Diamondheads would trade them forever). */
	private static boolean cutting;

	private record SpikeLine(ResourceKey<Level> dim, Vec3 from, Vec3 dir, long start, Set<Integer> hit) {
	}

	private record Storm(ResourceKey<Level> dim, Vec3 at) {
	}

	/** The Titan Shards: when it began, the shards' entity ids (-1 before each rises), which are thrown, the target (or -1). */
	private static final class Titan {
		final ResourceKey<Level> dim;
		final long start;
		final int[] ids = { -1, -1, -1 };
		final boolean[] thrown = new boolean[3];
		int target;
		Vec3 point;

		Titan(ResourceKey<Level> dim, long start, int target, Vec3 point) {
			this.dim = dim;
			this.start = start;
			this.target = target;
			this.point = point;
		}
	}

	/**
	 * v0.17.2 (user: "i want him to lift up 3 giant diamond shards from the ground and then throw them at his target, this has to
	 * have a big of a chargeup to it and have good animations"): the Titan Shards' timeline (ticks from the press) -- each shard
	 * cracks up out of the ground at {@link #TITAN_RISE_AT} and climbs for {@link #TITAN_RISE_TICKS} to hang round him, turning
	 * to point at the target, and he hurls them at {@link #TITAN_THROW_AT}: right, left, then the biggest over his head.
	 */
	public static final int[] TITAN_RISE_AT = { 4, 10, 16 };
	public static final int TITAN_RISE_TICKS = 18;
	public static final int[] TITAN_THROW_AT = { 42, 50, 58 };
	/** How long a thrown shard flies before it shatters on its own; and the whole move's end. */
	public static final int TITAN_FLIGHT = 50, TITAN_END = 58 + 50;
	/** Each shard's size (the crystal is 3.4x this long) and its place round him: right, up, forward (blocks). */
	private static final float[] TITAN_SIZE = { 1.05f, 1.05f, 1.45f };
	private static final double[][] TITAN_HOVER = { { 2.0, 2.3, 0.2 }, { -2.0, 2.3, 0.2 }, { 0.0, 3.9, -0.7 } };
	public static final float TITAN_DAMAGE = 18f, TITAN_SPLASH = 8f, TITAN_SPLASH_RADIUS = 3.5f;
	public static final double TITAN_SPEED = 1.9, TITAN_RANGE = 40;
	private static final ResourceLocation TITAN_SLOW_ID = PowerToggles.id("omnitrix_diamondhead_titan_slow");


	private record Prison(ResourceKey<Level> dim, int target, Vec3 at, long end, List<BlockPos> shell) {
	}

	private record Eruption(ResourceKey<Level> dim, Vec3 at, long start, Set<Integer> hit) {
	}

	private DiamondheadKit() {
	}

	public static void register() {
		DiamondheadBlocks.initialize();
		Aliens.register(new DiamondheadKit());
		AlienProjectileEntity.onHit("diamondhead_titan", (proj, hit) -> {
			if (proj.getOwner() instanceof ServerPlayer owner) {
				titanImpact(owner, (ServerLevel) proj.level(), hit.getLocation(), null, proj.size());
			}
		});
		AlienProjectileEntity.trail("diamondhead_shard", SPARKLE);
		AlienProjectileEntity.trail("diamondhead_spear", SPARKLE);
		AlienProjectileEntity.onHit("diamondhead_shard", (proj, hit) -> {
			if (proj.level() instanceof ServerLevel l) {
				Vec3 at = hit.getLocation();
				l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, crystal()), at.x, at.y, at.z, 6, 0.1, 0.1, 0.1, 0.1);
				OmnitrixSounds.play(l, at, SND_SHATTER, 0.4f, 1.6f + l.random.nextFloat() * 0.3f);
			}
		});
		AlienProjectileEntity.onHit("diamondhead_spear", (proj, hit) -> {
			if (proj.level() instanceof ServerLevel l) {
				Vec3 at = hit.getLocation();
				QuirkFx.shards(l, at).block(crystal()).size(1.6f).count(16).life(26).spawn();
				OmnitrixSounds.play(l, at, SND_SHATTER, 1.0f, 0.9f);
				if (hit instanceof net.minecraft.world.phys.EntityHitResult eh && eh.getEntity() instanceof LivingEntity le) {
					shove(le, proj.position().subtract(proj.getDeltaMovement()), 1.3, 0.35);
				}
			}
		});
	}

	public static void clearSessionState() {
		LINES.clear();
		STORMS.clear();
		ERUPTIONS.clear();
		PRISONS.clear();
		BASH_HITS.clear();
		cutting = false;
		DiamondheadRise.clearSessionState();
	}

	static BlockState crystal() {
		return DiamondheadBlocks.CRYSTAL.defaultBlockState();
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public AlienStats stats() {
		// v0.17.2 (user: "diamondheads armour diamond level"): a full diamond set, 20 armour and 8 toughness (was 12 / 4)
		return AlienStats.builder().health(34f).speed(0.88f).scale(1.1f).armor(20f, 8f).knockbackResist(0.5f).attack(4f).step(0.6f).safeFall(3f).build();
	}

	@Override
	protected List<AlienMove> declareMoves() {
		return List.of(
				AlienMove.tap(VOLLEY, AbilitySlot.SLOT_1, 24), AlienMove.shift(SPEAR, AbilitySlot.SLOT_1, 120),
				// v0.17.2 (user: "i wanted diamondheads shield to be the right click for his combat mode, shield bash can happen when he
				// holds out the shield and runs into enemies ... replace his g and Shift+G with other moves that he does in the show"): G is
				// the Crystal Tether (his crystal reaching out to grab -- a foe hauled in, or him zipped to a wall), Shift+G the Crystal
				// Bridge (the crystal span he grows over a gap); the shield is held right-click in Combat Mode (its cooldown is the
				// regrowth after it shatters) and the bash is running into foes behind it
				// v0.17.2 (user: "Make G spike line and shift+G a different attack move please i dont like the beam attack"): G is the
				// Spike Line (it was Combat Mode's Z), Shift+G the Crystal Pillar; the Tether moved to Shift+C (the Prism Beam is gone) and
				// the Bridge into Shift+V's Crystal Construct
				AlienMove.tap(SHATTER, AbilitySlot.SLOT_2, SHATTER_COOLDOWN), AlienMove.shift(PILLAR, AbilitySlot.SLOT_2, PILLAR_COOLDOWN),
				AlienMove.vanilla(SHIELD, "Combat Mode: hold Use", SHIELD_BROKEN_TICKS),
				AlienMove.vanilla(BASH, "Combat Mode: run into foes behind the shield", 0),
				AlienMove.tap(SLIDE, AbilitySlot.SLOT_3, 80), AlienMove.shift(VAULT, AbilitySlot.SLOT_3, 140),
				// v0.17.2 (user: "change the damage on the ben 10 aliens to be on par with the elemental power abilities"): the
				// Barrage's cooldown 500 -> 600, the ultimate's 30 s
				AlienMove.tap(ERUPT, AbilitySlot.SLOT_4, ERUPT_COOLDOWN), AlienMove.shift(BARRAGE, AbilitySlot.SLOT_4, 600),
				// v0.17.2 revamp: V is Combat Mode, the wall on Shift+V (the dome is gone)
				// v0.17.2 (user: "Make the shift+V move the building move, using it normally places a wall, looking up builds the dome,
				// looking down builds the bridge"): the Crystal Construct
				AlienMove.tap(COMBAT, AbilitySlot.SLOT_5, 20), AlienMove.shift(WALL, AbilitySlot.SLOT_5, 200),
				// v0.17.2 (user: "Remove crystal regrowth ... and crystal armour"): the Crystal Prison and the Prism Beam
				AlienMove.tap(PRISON, AbilitySlot.SLOT_6, PRISON_COOLDOWN), AlienMove.shift(TETHER, AbilitySlot.SLOT_6, TETHER_COOLDOWN),
				// Combat Mode's swapped keys (the HUD shows them on R / Shift+R while it is on, see liveMoveOn)
				AlienMove.vanilla(FLURRY, "Combat Mode: R", FLURRY_COOLDOWN), AlienMove.vanilla(CYCLONE, "Combat Mode: Shift+R", CYCLONE_COOLDOWN),
				AlienMove.vanilla(EDGE, "Attack", 0), AlienMove.vanilla(PLACE, "Use on a block (empty hand)", 4));
	}

	/** Combat Mode swaps R, Shift+R and Z for the blade moves. */
	@Override
	public AlienMove liveMoveOn(Player p, AbilitySlot slot, boolean shift, boolean hold) {
		if (flag(p, F_COMBAT) && !hold) {
			if (slot == AbilitySlot.SLOT_1) {
				return move(shift ? CYCLONE : FLURRY);
			}
		}
		return moveOn(slot, shift, hold);
	}

	@Override
	public boolean keyActive(Player p, AbilitySlot slot) {
		return slot == AbilitySlot.SLOT_5 ? flag(p, F_COMBAT) : slot == AbilitySlot.SLOT_3 && flag(p, F_SLIDING);
	}

	// ---------------------------------------------------------------- lifecycle

	@Override
	public void onTransform(ServerPlayer p) {
		ServerLevel l = level(p);
		OmnitrixSounds.play(l, p.position(), SND_GROW, 1.0f, 1.0f);
		QuirkFx.shards(l, p.position().add(0, 1, 0)).block(crystal()).size(1.4f).count(14).life(16).reassemble().spawn();
	}

	@Override
	public void onRevert(ServerPlayer p) {
		setCombat(p, false);
		setShield(p, false);
		setFlag(p, F_BASH, false);
		endSlide(p);
		for (String t : new String[] { "shield_hp", "bash_end", "flurry_at", "flurry_step", "storm_end", "shield_use", "tether_safe" }) {
			setTimer(p, t, 0);
		}
		PRISONS.remove(p.getUUID());
		LINES.remove(p.getUUID());
		STORMS.remove(p.getUUID());
		endTitan(p, true);
		ERUPTIONS.remove(p.getUUID());
		BASH_HITS.remove(p.getUUID());
	}

	@Override
	public void tick(ServerPlayer p, OmnitrixState s, long now) {
		ServerLevel l = level(p);
		// the shield first: every frontal projectile it meets goes back to its shooter
		if (flag(p, F_SHIELD) || flag(p, F_BASH)) {
			for (Projectile pr : l.getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(3.0), pr -> pr.getOwner() != p)) {
				Vec3 to = p.position().add(0, p.getBbHeight() * 0.5, 0).subtract(pr.position());
				if (pr.getDeltaMovement().lengthSqr() < 0.01 || pr.getDeltaMovement().dot(to) <= 0 || !inFront(p, pr.position())) {
					continue;
				}
				pr.addTag(SEEN_TAG);
				shieldReflect(p, pr);
			}
		}
		// the passive: projectiles reaching him may glance back
		for (Projectile pr : l.getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(2.2),
				pr -> pr.getOwner() != p && !pr.getTags().contains(SEEN_TAG))) {
			Vec3 to = p.position().add(0, p.getBbHeight() * 0.5, 0).subtract(pr.position());
			if (pr.getDeltaMovement().lengthSqr() < 0.01 || pr.getDeltaMovement().dot(to) <= 0) {
				continue;
			}
			pr.addTag(SEEN_TAG);
			tryReflect(p, pr, p.getRandom().nextFloat());
		}
		if (now % 20 == 0) {
			// keep the modifiers on (a relog drops transient modifiers)
			if (flag(p, F_COMBAT)) {
				applyBladeModifiers(p);
			}
			if (flag(p, F_SHIELD)) {
				PowerToggles.modifier(p, Attributes.MOVEMENT_SPEED, SHIELD_SLOW_ID, SHIELD_SLOW, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
			}
		}
		tickShield(p, l, now);
		tickBash(p, l, now);
		tickFlurry(p, now);
		tickPrison(p, l, now);
		slideTick(p, l, now);
		tickSpikes(p, l, now);
		tickStorm(p, l, now);
		tickTitan(p, l, now);
		tickEruption(p, l, now);
	}

	/** The reflection passive on one projectile: {@code roll} under {@link #REFLECT_CHANCE} sends it back. */
	public boolean tryReflect(ServerPlayer p, Projectile pr, float roll) {
		if (roll >= REFLECT_CHANCE) {
			return false;
		}
		sendBack(p, pr);
		ServerLevel l = level(p);
		l.sendParticles(SPARKLE, pr.getX(), pr.getY(), pr.getZ(), 8, 0.15, 0.15, 0.15, 0.05);
		OmnitrixSounds.play(l, pr.position(), SND_SHARD, 0.8f, 1.8f);
		return true;
	}

	/** Turns {@code pr} round at its shooter (or straight back), a touch faster, and makes it his. */
	private static void sendBack(ServerPlayer p, Projectile pr) {
		Vec3 v = pr.getDeltaMovement();
		double speed = Math.max(0.6, v.length()) * 1.1;
		Entity owner = pr.getOwner();
		Vec3 back = owner != null && owner.isAlive() && owner.distanceToSqr(p) < 48 * 48
				? owner.getEyePosition().subtract(pr.position()).normalize()
				: v.scale(-1).normalize();
		pr.setDeltaMovement(back.scale(speed));
		pr.setOwner(p);
		pr.hurtMarked = true;
		pr.hasImpulse = true;
	}

	/** In front of him: within about 75 degrees either side of where he faces. */
	public boolean inFront(Player p, Vec3 from) {
		Vec3 to = from.subtract(p.position());
		Vec3 h = new Vec3(to.x, 0, to.z);
		if (h.lengthSqr() < 1.0e-4) {
			return true;
		}
		return h.normalize().dot(Vec3.directionFromRotation(0, p.getYRot())) > 0.25;
	}

	// ---------------------------------------------------------------- keys

	@Override
	public void press(ServerPlayer p, AbilitySlot slot, boolean shift) {
		boolean combat = flag(p, F_COMBAT);
		switch (slot) {
			case SLOT_1 -> {
				if (combat && shift) {
					cyclone(p);
				} else if (combat) {
					bladeFlurry(p);
				} else if (shift) {
					crystalSpear(p);
				} else {
					shardVolley(p);
				}
			}
			case SLOT_2 -> {
				if (shift) {
					crystalPillar(p);
				} else {
					shatterLine(p);
				}
			}
			case SLOT_3 -> {
				if (shift) {
					vault(p);
				} else {
					slide(p);
				}
			}
			case SLOT_4 -> {
				if (shift) {
					barrage(p);
				} else {
					eruption(p);
				}
			}
			case SLOT_5 -> {
				if (shift) {
					wall(p);
				} else {
					toggleCombat(p);
				}
			}
			case SLOT_6 -> {
				if (shift) {
					crystalTether(p);
				} else {
					crystalPrison(p);
				}
			}
			default -> {
			}
		}
	}

	// ---------------------------------------------------------------- Combat Mode's held right-click: the shield

	/**
	 * A right-click in Combat Mode (on air, a block or a creature): the shield comes up, or stays up. Vanilla repeats the use while
	 * the button is held (every tick on air, every 4 on a block), so the shield is "held" while uses keep coming:
	 * {@link #SHIELD_HOLD_GRACE} ticks after the last one it lowers ({@link #tickShield}).
	 */
	private InteractionResult shieldUse(ServerPlayer p) {
		if (!flag(p, F_COMBAT)) {
			return InteractionResult.PASS;
		}
		setTimer(p, "shield_use", p.level().getGameTime());
		if (!flag(p, F_SHIELD)) {
			raiseShield(p);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public InteractionResult onUse(ServerPlayer p, InteractionHand hand) {
		return hand == InteractionHand.MAIN_HAND && p.getMainHandItem().isEmpty() ? shieldUse(p) : InteractionResult.PASS;
	}

	@Override
	public InteractionResult onUseEntity(ServerPlayer p, InteractionHand hand, Entity target) {
		return hand == InteractionHand.MAIN_HAND && p.getMainHandItem().isEmpty() ? shieldUse(p) : InteractionResult.PASS;
	}

	// ---------------------------------------------------------------- G: the Crystal Tether, Shift+G: the Crystal Bridge

	/**
	 * G: crystal shoots from his palm along his aim -- a creature it strikes is cut and hauled to him (a boss only cut and slowed); a
	 * wall or the ground it strikes anchors it and yanks him there. Up to {@link #TETHER_RANGE} blocks.
	 */
	public boolean crystalTether(ServerPlayer p) {
		if (!use(p, TETHER)) {
			return false;
		}
		ServerLevel l = level(p);
		Vec3 eye = p.getEyePosition(), look = p.getLookAngle(), far = eye.add(look.scale(TETHER_RANGE));
		BlockHitResult bh = l.clip(new net.minecraft.world.level.ClipContext(eye, far, net.minecraft.world.level.ClipContext.Block.COLLIDER,
				net.minecraft.world.level.ClipContext.Fluid.NONE, p));
		Vec3 stop = bh.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? far : bh.getLocation();
		net.minecraft.world.phys.EntityHitResult eh = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(l, p, eye, stop,
				p.getBoundingBox().expandTowards(look.scale(TETHER_RANGE)).inflate(1.0),
				e -> e instanceof LivingEntity le && le.isAlive() && !e.isSpectator() && !com.projecthero.mod.omnitrix.OmnitrixRiding.partners(p, e), 0.3f);
		anim(p, ANIM_SPEAR);
		Vec3 hitAt;
		if (eh != null && eh.getEntity() instanceof LivingEntity le) {
			hitAt = le.position().add(0, le.getBbHeight() * 0.5, 0);
			le.invulnerableTime = 0;
			hurt(p, le, TETHER_DAMAGE, false);
			if (AlienStatus.isBoss(le)) {
				le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2), p);
			} else {
				Vec3 pull = p.position().subtract(le.position());
				double d = pull.length();
				le.setDeltaMovement(pull.normalize().scale(Math.min(1.7, 0.4 + d * 0.13)).add(0, 0.38, 0));
				le.hurtMarked = true;
				le.hasImpulse = true;
			}
		} else if (bh.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
			hitAt = bh.getLocation();
			Vec3 pull = hitAt.subtract(p.position());
			double d = pull.length();
			p.setDeltaMovement(pull.normalize().scale(Math.min(2.1, 0.5 + d * 0.12)).add(0, 0.3, 0));
			p.hurtMarked = true;
			p.fallDistance = 0f;
			// no fall damage from the landing a tether flings him into
			setTimer(p, "tether_safe", p.level().getGameTime() + 60);
			QuirkFx.shards(l, hitAt).block(crystal()).size(0.9f).count(8).life(30).spawn();
		} else {
			hitAt = far;
		}
		Vec3 from = hand(p, true);
		QuirkFx.ribbon(l, from, hitAt).colours(0xE0FFF6, COLOUR).size(0.3f).life(9).style(QuirkFx.RIBBON_LIGHT).spawn();
		QuirkFx.ribbon(l, from, hitAt).colours(0xFFFFFF, 0xB8FFF0).size(0.12f).life(7).style(QuirkFx.RIBBON_LIGHT).spawn();
		OmnitrixSounds.play(l, p.position(), SND_SHARD, 1.0f, 0.8f);
		OmnitrixSounds.play(l, hitAt, SND_GROW, 0.8f, 1.4f);
		return true;
	}

	/**
	 * Shift+G: a 3-wide crystal span grows out ahead of him for {@link #BRIDGE_LENGTH} blocks at his feet's level -- level, or a
	 * staircase climbing (looking up) or descending (looking down) a block every two -- rising into place block by block, and gone
	 * after {@link #BRIDGE_TICKS}. Break any block of it and the whole thing falls.
	 */
	public boolean crystalBridge(ServerPlayer p) {
		ServerLevel l = level(p);
		if (!p.mayBuild()) {
			return false;
		}
		Vec3 f = flatLook(p);
		Vec3 side = new Vec3(-f.z, 0, f.x);
		float pitch = p.getXRot();
		// v0.17.2: built by Shift+V looking down, so it runs level unless he looks down steeply (then it steps down a block every two)
		int climb = pitch > 65f ? -1 : 0;
		DiamondheadRise.Batch b = DiamondheadRise.batch(l, DiamondheadRise.RISE_TICKS);
		int y = p.blockPosition().getY() - 1;
		for (int i = 1; i <= BRIDGE_LENGTH; i++) {
			if (climb != 0 && i > 1 && i % 2 == 0) {
				y += climb;
			}
			Vec3 c = p.position().add(f.scale(i));
			for (int w = -1; w <= 1; w++) {
				BlockPos q = BlockPos.containing(c.x + side.x * w, y, c.z + side.z * w);
				if (l.getBlockState(q).canBeReplaced() && l.mayInteract(p, q)) {
					b.place(q, BRIDGE_TICKS);
				}
			}
		}
		b.start();
		anim(p, ANIM_WALL);
		OmnitrixSounds.play(l, p.position(), SND_GROW, 1.0f, 0.8f);
		return true;
	}

	private static Vec3 flatLook(ServerPlayer p) {
		Vec3 l = p.getLookAngle();
		Vec3 h = new Vec3(l.x, 0, l.z);
		return h.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : h.normalize();
	}

	public boolean shardVolley(ServerPlayer p) {
		if (!use(p, VOLLEY)) {
			return false;
		}
		anim(p, ANIM_VOLLEY);
		Vec3 aim = aimPoint(p);
		for (int i = -2; i <= 2; i++) {
			// the fan leaves both hands (the left two from the left, the rest from the right), converging on the crosshair
			Vec3 from = hand(p, i >= 0);
			Vec3 to = aim.subtract(from).normalize();
			Vec3 side = to.cross(new Vec3(0, 1, 0));
			side = side.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : side.normalize();
			fromHand(AlienProjectileEntity.of(p, "diamondhead_shard"), from).colour(COLOUR).size(0.28f).damage(SHARD_DAMAGE).speed(2.0).life(40)
					.direction(to.add(side.scale(i * 0.09)).add(0, Math.abs(i) * -0.015, 0)).spawn();
		}
		OmnitrixSounds.play(level(p), p.position(), SND_SHARD, 1.0f, 1.0f + p.getRandom().nextFloat() * 0.2f);
		return true;
	}

	public boolean crystalSpear(ServerPlayer p) {
		if (!use(p, SPEAR)) {
			return false;
		}
		anim(p, ANIM_SPEAR);
		Vec3 from = hand(p, true);
		fromHand(AlienProjectileEntity.of(p, "diamondhead_spear"), from).colour(0xA8FFE8).size(0.7f).damage(SPEAR_DAMAGE).speed(2.4).life(50)
				.direction(aimPoint(p).subtract(from)).spawn();
		ServerLevel l = level(p);
		OmnitrixSounds.play(l, p.position(), SND_GROW, 0.9f, 0.7f);
		OmnitrixSounds.play(l, p.position(), SND_SHARD, 1.0f, 0.6f);
		return true;
	}

	// ---------------------------------------------------------------- G: the Crystal Shield, Shift+G: Shield Bash

	/** The shield's HP now (client-safe; full when unset). 0 while it is shattered and regrowing. */
	public float shieldHp(Player p) {
		if (cooldownLeft(p, SHIELD) > 0) {
			return 0f;
		}
		long t = timer(p, "shield_hp");
		return t <= 0 ? SHIELD_HP : (t - 1) / 10f;
	}

	private void setShieldHp(ServerPlayer p, float hp) {
		// stored in tenths, +1 (a timer of 0 is "unset", which reads as full)
		setTimer(p, "shield_hp", hp >= SHIELD_HP ? 0 : Math.round(Math.max(0f, hp) * 10f) + 1L);
	}

	/** Whether the shield is shattered (regrowing). */
	public boolean shieldBroken(Player p) {
		return cooldownLeft(p, SHIELD) > 0;
	}

	/** G down: the tower shield grows over his left forearm and stays while G is held. False while it is shattered. */
	public boolean raiseShield(ServerPlayer p) {
		if (flag(p, F_SHIELD)) {
			return true;
		}
		if (shieldBroken(p)) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.diamondhead.shield_broken",
					String.format(java.util.Locale.ROOT, "%.1f", cooldownLeft(p, SHIELD) / 20f)).withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		setShield(p, true);
		anim(p, ANIM_SHIELD);
		ServerLevel l = level(p);
		Vec3 at = shieldCentre(p);
		QuirkFx.shards(l, at).block(crystal()).size(1.1f).count(10).life(10).reassemble().spawn();
		OmnitrixSounds.play(l, at, SND_SHIELD_UP, 0.9f, 1.0f);
		return true;
	}

	/** G up: the shield melts back into his arm. */
	public void lowerShield(ServerPlayer p) {
		if (!flag(p, F_SHIELD)) {
			return;
		}
		setShield(p, false);
		if (!flag(p, F_BASH)) {
			anim(p, ANIM_SHIELD_DOWN);
		}
		level(p).sendParticles(SPARKLE, p.getX(), p.getY() + 1.1, p.getZ(), 6, 0.4, 0.3, 0.4, 0.02);
	}

	private void setShield(ServerPlayer p, boolean on) {
		setFlag(p, F_SHIELD, on);
		if (on) {
			PowerToggles.modifier(p, Attributes.MOVEMENT_SPEED, SHIELD_SLOW_ID, SHIELD_SLOW, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		} else {
			PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, SHIELD_SLOW_ID);
		}
	}

	/** Where the shield sits in the world: a little ahead of his chest. */
	private static Vec3 shieldCentre(ServerPlayer p) {
		return p.position().add(0, p.getBbHeight() * 0.55, 0).add(Vec3.directionFromRotation(0, p.getYRot()).scale(0.9));
	}

	/** The shield takes {@code amount}: cracks, and shatters at 0 (the rest of that hit is still blocked). Returns the HP left. */
	public float damageShield(ServerPlayer p, float amount) {
		float left = shieldHp(p) - amount;
		ServerLevel l = level(p);
		Vec3 at = shieldCentre(p);
		if (left <= 0f) {
			shatterShield(p);
			return 0f;
		}
		setShieldHp(p, left);
		l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, crystal()), at.x, at.y, at.z, 6, 0.3, 0.3, 0.3, 0.1);
		l.sendParticles(SPARKLE, at.x, at.y, at.z, 4, 0.3, 0.3, 0.3, 0.05);
		OmnitrixSounds.play(l, at, SND_SHIELD_HIT, 0.9f, 0.8f + 0.4f * left / SHIELD_HP);
		return left;
	}

	private void shatterShield(ServerPlayer p) {
		ServerLevel l = level(p);
		Vec3 at = shieldCentre(p);
		setShield(p, false);
		setFlag(p, F_BASH, false);
		setTimer(p, "bash_end", 0);
		setTimer(p, "shield_hp", 0);
		cooldown(p, SHIELD, -1);
		anim(p, ANIM_SHIELD_BREAK);
		QuirkFx.shards(l, at).block(crystal()).size(2.0f).count(26).life(26).spawn();
		l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, crystal()), at.x, at.y, at.z, 30, 0.5, 0.6, 0.5, 0.2);
		OmnitrixSounds.play(l, at, SND_SHIELD_BREAK, 1.2f, 1.0f);
		QuirkCamera.shake(p, 0.3f, 6);
	}

	/** Every frontal projectile goes back at its shooter off the shield (a little chip off it). */
	private void shieldReflect(ServerPlayer p, Projectile pr) {
		sendBack(p, pr);
		ServerLevel l = level(p);
		l.sendParticles(SPARKLE, pr.getX(), pr.getY(), pr.getZ(), 10, 0.2, 0.2, 0.2, 0.08);
		l.sendParticles(ParticleTypes.END_ROD, pr.getX(), pr.getY(), pr.getZ(), 3, 0.1, 0.1, 0.1, 0.05);
		OmnitrixSounds.play(l, pr.position(), SND_SHIELD_HIT, 0.8f, 1.4f);
	}

	private void tickShield(ServerPlayer p, ServerLevel l, long now) {
		// v0.17.2: held right-click -- down once the uses stop coming (the button let go) or Combat Mode is off
		if (flag(p, F_SHIELD) && (!flag(p, F_COMBAT) || now - timer(p, "shield_use") > SHIELD_HOLD_GRACE || now < timer(p, "shield_use"))) {
			lowerShield(p);
		}
		if (flag(p, F_SHIELD) || flag(p, F_BASH)) {
			if (now % 5 == 0) {
				Vec3 at = shieldCentre(p);
				l.sendParticles(SPARKLE, at.x, at.y, at.z, 1, 0.35, 0.5, 0.35, 0.0);
			}
			return;
		}
		// lowered: it regrows
		long t = timer(p, "shield_hp");
		if (t > 0 && now % 10 == 0 && !shieldBroken(p)) {
			setShieldHp(p, shieldHp(p) + SHIELD_REGEN);
		}
	}

	/**
	 * v0.17.2 (user: "shield bash can happen when he holds out the shield and runs into enemies"): with the shield up and moving at
	 * {@link #BASH_MIN_SPEED} or more toward where he faces, every foe he runs into just ahead is battered aside and staggered (the
	 * same foe again only after {@link #BASH_REHIT_TICKS}).
	 */
	private void tickBash(ServerPlayer p, ServerLevel l, long now) {
		if (!flag(p, F_SHIELD)) {
			return;
		}
		Vec3 dir = Vec3.directionFromRotation(0, p.getYRot());
		Vec3 moved = new Vec3(p.getX() - p.xo, 0, p.getZ() - p.zo);
		if (moved.length() < BASH_MIN_SPEED || moved.normalize().dot(dir) < 0.5) {
			return;
		}
		Map<Integer, Long> hits = BASH_HITS.computeIfAbsent(p.getUUID(), k -> new ConcurrentHashMap<>());
		hits.values().removeIf(at -> now - at > BASH_REHIT_TICKS || now < at);
		for (LivingEntity e : around(p, p.position().add(dir.scale(1.1)).add(0, 1, 0), 1.3)) {
			if (hits.putIfAbsent(e.getId(), now) != null) {
				continue;
			}
			anim(p, ANIM_BASH);
			e.invulnerableTime = 0;
			hurt(p, e, BASH_DAMAGE, false);
			shove(e, p.position(), 1.6, 0.35);
			// staggered: slowed hard and weakened for a moment
			int stagger = AlienStatus.isBoss(e) ? 10 : 30;
			e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, stagger, 3), p);
			e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, stagger, 1), p);
			Vec3 at = e.position().add(0, e.getBbHeight() * 0.5, 0);
			QuirkFx.ring(l, at).flags(QuirkFx.F_VERTICAL).vec(dir).colours(0xE0FFF6, COLOUR).size(1.8f).thickness(0.4f).life(8).spawn();
			l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, crystal()), at.x, at.y, at.z, 10, 0.3, 0.3, 0.3, 0.15);
			OmnitrixSounds.play(l, at, SND_BASH, 1.1f, 1.0f);
			QuirkCamera.preset(p, QuirkCamera.Preset.HEAVY_HIT);
		}
		if (now % 2 == 0) {
			BlockState under = l.getBlockState(p.blockPosition().below());
			if (!under.isAir()) {
				l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, under), p.getX(), p.getY() + 0.1, p.getZ(), 4, 0.3, 0.05, 0.3, 0.1);
			}
		}
	}

	// ---------------------------------------------------------------- V: Combat Mode, Shift+V: Crystal Wall

	/** V: Combat Mode on / off -- both forearms grow into long crystal blades (R / Shift+R / Z turn into blade moves). */
	public boolean toggleCombat(ServerPlayer p) {
		if (!use(p, COMBAT)) {
			return false;
		}
		boolean on = !flag(p, F_COMBAT);
		setCombat(p, on);
		anim(p, on ? ANIM_COMBAT_ON : ANIM_COMBAT_OFF);
		ServerLevel l = level(p);
		OmnitrixSounds.play(l, p.position(), on ? SND_BLADE : SND_SHATTER, 0.9f, on ? 1.0f : 1.4f);
		l.sendParticles(SPARKLE, p.getX(), p.getY() + 1.1, p.getZ(), 14, 0.5, 0.3, 0.5, 0.02);
		if (!on) {
			QuirkFx.shards(l, p.position().add(0, 1.1, 0)).block(crystal()).size(1.0f).count(10).life(14).spawn();
		}
		p.displayClientMessage(Component.translatable(on ? "message.projecthero.omnitrix.diamondhead.combat_on" : "message.projecthero.omnitrix.diamondhead.combat_off")
				.withStyle(on ? ChatFormatting.AQUA : ChatFormatting.GRAY), true);
		return true;
	}

	private void setCombat(ServerPlayer p, boolean on) {
		setFlag(p, F_COMBAT, on);
		if (on) {
			applyBladeModifiers(p);
		} else {
			PowerToggles.clearModifier(p, Attributes.ATTACK_DAMAGE, BLADE_DAMAGE_ID);
			PowerToggles.clearModifier(p, Attributes.ENTITY_INTERACTION_RANGE, BLADE_REACH_ID);
			PowerToggles.clearModifier(p, Attributes.ATTACK_SPEED, BLADE_SPEED_ID);
			setTimer(p, "flurry_at", 0);
			setTimer(p, "flurry_step", 0);
		}
	}

	private static void applyBladeModifiers(ServerPlayer p) {
		PowerToggles.modifier(p, Attributes.ATTACK_DAMAGE, BLADE_DAMAGE_ID, BLADE_BONUS, AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(p, Attributes.ENTITY_INTERACTION_RANGE, BLADE_REACH_ID, BLADE_REACH, AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(p, Attributes.ATTACK_SPEED, BLADE_SPEED_ID, BLADE_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
	}

	// ---------------------------------------------------------------- Combat Mode R: Blade Flurry, Shift+R: Crystal Cyclone

	/** The Blade Flurry beat the next R plays (0, 1 the slashes, 2 the stab; client-safe). */
	public int flurryStep(Player p) {
		long at = timer(p, "flurry_at");
		long step = timer(p, "flurry_step");
		if (at <= 0 || step <= 0 || p.level().getGameTime() - at > FLURRY_WINDOW) {
			return 0;
		}
		return (int) Math.min(2, step - 1);
	}

	/**
	 * Combat Mode R: the Blade Flurry -- a slash (8), a back-slash (8) and a thrusting stab (14), each R within 1.5 s of the last;
	 * the slashes cut everything in a wide arc in front, the stab runs through a line. The cooldown starts after the stab.
	 * Returns the beat played (0-2), or -1 if refused.
	 */
	public int bladeFlurry(ServerPlayer p) {
		long now = p.level().getGameTime();
		long at = timer(p, "flurry_at");
		if (at > 0 && now - at < FLURRY_GAP) {
			return -1; // still mid-swing
		}
		int beat = flurryStep(p);
		if (beat == 0 && !ready(p, FLURRY)) {
			use(p, FLURRY); // the "not ready" line
			return -1;
		}
		ServerLevel l = level(p);
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		if (beat < 2) {
			anim(p, beat == 0 ? ANIM_FLURRY_1 : ANIM_FLURRY_2);
			int n = 0;
			for (LivingEntity e : around(p, eye.add(look.scale(FLURRY_REACH * 0.5)), FLURRY_REACH)) {
				Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eye);
				if (to.length() > FLURRY_REACH + e.getBbWidth() * 0.5 || to.normalize().dot(look) < 0.45) {
					continue;
				}
				e.invulnerableTime = 0;
				hurt(p, e, FLURRY_SLASH_DAMAGE, false);
				shove(e, p.position(), 0.35, 0.1);
				l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, crystal()), e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), 6, 0.2, 0.3, 0.2, 0.1);
				n++;
			}
			Vec3 c = eye.add(look.scale(2.0)).add(0, -0.3, 0);
			l.sendParticles(ParticleTypes.SWEEP_ATTACK, c.x, c.y, c.z, 1, 0, 0, 0, 0);
			QuirkFx.ring(l, c).flags(QuirkFx.F_VERTICAL).vec(look).style(QuirkFx.RING_SOFT).colours(0xE0FFF6, COLOUR).size(2.4f).life(6).spawn();
			OmnitrixSounds.play(l, c, SND_SLASH, 1.0f, beat == 0 ? 1.0f : 1.15f);
			if (n > 0) {
				QuirkCamera.preset(p, QuirkCamera.Preset.LIGHT_HIT);
			}
			setTimer(p, "flurry_at", now);
			setTimer(p, "flurry_step", beat + 2L);
			return beat;
		}
		// the stab
		anim(p, ANIM_STAB);
		cooldown(p, FLURRY, -1);
		setTimer(p, "flurry_at", 0);
		setTimer(p, "flurry_step", 0);
		var hit = l.clip(new net.minecraft.world.level.ClipContext(eye, eye.add(look.scale(STAB_REACH)), net.minecraft.world.level.ClipContext.Block.COLLIDER,
				net.minecraft.world.level.ClipContext.Fluid.NONE, p));
		double len = hit.getLocation().distanceTo(eye);
		for (LivingEntity e : around(p, eye.add(look.scale(len * 0.5)), len * 0.5 + 1.5)) {
			Vec3 c = e.position().add(0, e.getBbHeight() * 0.5, 0);
			double along = c.subtract(eye).dot(look);
			if (along < 0 || along > len + 0.5 || eye.add(look.scale(along)).distanceTo(c) > e.getBbWidth() * 0.5 + 0.9) {
				continue;
			}
			e.invulnerableTime = 0;
			hurt(p, e, FLURRY_STAB_DAMAGE, false);
			shove(e, p.position(), 1.2, 0.25);
			QuirkFx.shards(l, c).block(crystal()).size(1.0f).count(8).life(14).spawn();
		}
		Vec3 tip = eye.add(look.scale(Math.min(len, 3.5))).add(0, -0.3, 0);
		QuirkFx.spike(l, eye.add(look.scale(0.8)).add(0, -0.35, 0), look).block(crystal()).colours(0xC8FFF0, COLOUR).size((float) Math.min(len, 4.0)).size2(0.3f)
				.life(8).spawn();
		QuirkFx.ring(l, tip).flags(QuirkFx.F_VERTICAL).vec(look).colours(0xFFFFFF, COLOUR).size(1.6f).thickness(0.4f).life(8).spawn();
		OmnitrixSounds.play(l, tip, SND_STAB, 1.1f, 1.0f);
		QuirkCamera.preset(p, QuirkCamera.Preset.HEAVY_HIT);
		return 2;
	}

	private void tickFlurry(ServerPlayer p, long now) {
		long at = timer(p, "flurry_at");
		if (at > 0 && now - at > FLURRY_WINDOW) {
			// the chain lapsed: it starts over (no cooldown -- that only follows the stab)
			setTimer(p, "flurry_at", 0);
			setTimer(p, "flurry_step", 0);
		}
	}

	/** Combat Mode Shift+R: the Crystal Cyclone -- one spinning slash all round him (radius 4): 12, thrown out, slowed. Returns how many it hit (-1 refused). */
	public int cyclone(ServerPlayer p) {
		if (!use(p, CYCLONE)) {
			return -1;
		}
		anim(p, ANIM_CYCLONE);
		ServerLevel l = level(p);
		Vec3 c = p.position().add(0, 1.0, 0);
		int n = 0;
		for (LivingEntity e : around(p, c, CYCLONE_RADIUS)) {
			if (Math.abs(e.getY() - p.getY()) > 2.5) {
				continue;
			}
			e.invulnerableTime = 0;
			hurt(p, e, CYCLONE_DAMAGE, false);
			shove(e, p.position(), 0.9, 0.3);
			int slow = AlienStatus.isBoss(e) ? CYCLONE_SLOW_TICKS / 4 : CYCLONE_SLOW_TICKS;
			e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, slow, 1), p);
			l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, crystal()), e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), 8, 0.25, 0.3, 0.25, 0.12);
			n++;
		}
		QuirkFx.ring(l, c).colours(0xE0FFF6, COLOUR).size((float) CYCLONE_RADIUS + 0.5f).thickness(0.5f).life(10).spawn();
		QuirkFx.ring(l, c.add(0, -0.3, 0)).style(QuirkFx.RING_SOFT).colours(0xFFFFFF, COLOUR).size((float) CYCLONE_RADIUS).life(12).spawn();
		for (int i = 0; i < 8; i++) {
			double a = i * Math.PI / 4;
			l.sendParticles(ParticleTypes.SWEEP_ATTACK, c.x + Math.cos(a) * 2.2, c.y, c.z + Math.sin(a) * 2.2, 1, 0, 0, 0, 0);
		}
		l.sendParticles(SPARKLE, c.x, c.y, c.z, 24, 2.0, 0.3, 2.0, 0.05);
		OmnitrixSounds.play(l, c, SND_CYCLONE, 1.1f, 1.0f);
		QuirkCamera.shake(p, 0.25f, 6);
		return n;
	}

	// ---------------------------------------------------------------- Combat Mode Z: the Shatter Line

	private void tickSpikes(ServerPlayer p, ServerLevel l, long now) {
		SpikeLine line = LINES.get(p.getUUID());
		if (line == null) {
			return;
		}
		int step = (int) (now - line.start());
		if (step >= SPIKE_LENGTH || line.dim() != l.dimension()) {
			LINES.remove(p.getUUID());
			return;
		}
		Vec3 at = line.from().add(line.dir().scale(1.5 + step));
		BlockPos ground = groundUnder(l, BlockPos.containing(at));
		if (ground == null) {
			return;
		}
		Vec3 base = Vec3.atBottomCenterOf(ground.above());
		QuirkFx.spike(l, base, new Vec3(0, 1, 0)).block(crystal()).colours(0xC8FFF0, COLOUR).size(1.8f + step * 0.06f).size2(0.45f).life(36).spawn();
		// two smaller teeth either side, leaning out
		Vec3 side = new Vec3(-line.dir().z, 0, line.dir().x);
		for (int s = -1; s <= 1; s += 2) {
			Vec3 b2 = base.add(side.scale(s * 0.75)).add(line.dir().scale(0.3));
			QuirkFx.spike(l, b2, side.scale(s * 0.45).add(0, 1, 0).normalize()).block(crystal()).colours(0xC8FFF0, COLOUR).size(1.0f + step * 0.04f).size2(0.3f)
					.life(30).spawn();
		}
		if (step % 2 == 0) {
			OmnitrixSounds.play(l, base, SND_GROW, 0.6f, 1.2f + step * 0.03f);
		}
		for (LivingEntity e : around(p, base.add(0, 0.8, 0), 1.6)) {
			if (line.hit().add(e.getId())) {
				e.invulnerableTime = 0;
				hurt(p, e, SPIKE_DAMAGE, false);
				e.push(0, 0.75 * Math.max(0.3, 1.0 - e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)), 0);
				e.hurtMarked = true;
			}
		}
	}

	/** The highest solid block at or a few below / above {@code pos} with room over it, or null. */
	private static BlockPos groundUnder(ServerLevel l, BlockPos pos) {
		for (int dy = 2; dy >= -4; dy--) {
			BlockPos b = pos.offset(0, dy, 0);
			if (!l.getBlockState(b).getCollisionShape(l, b).isEmpty() && l.getBlockState(b.above()).getCollisionShape(l, b.above()).isEmpty()) {
				return b;
			}
		}
		return null;
	}

	/** Combat Mode Z: the blade driven into the ground -- crystal spikes erupt in a 12-block line ahead (16, launch). */
	public boolean shatterLine(ServerPlayer p) {
		if (!use(p, SHATTER)) {
			return false;
		}
		anim(p, ANIM_SHATTER);
		ServerLevel l = level(p);
		LINES.put(p.getUUID(), new SpikeLine(p.level().dimension(), p.position(), flatLook(p), p.level().getGameTime(), new HashSet<>()));
		Vec3 c = p.position().add(flatLook(p).scale(1.2));
		QuirkFx.ring(l, c.add(0, 0.05, 0)).colours(0xE0FFF6, COLOUR).size(2.2f).thickness(0.5f).life(10).ground().spawn();
		l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, crystal()), c.x, c.y + 0.1, c.z, 16, 0.4, 0.05, 0.4, 0.2);
		OmnitrixSounds.play(l, c, SND_STAB, 0.9f, 0.7f);
		QuirkCamera.shakeAround(l, p.position(), 10, 0.25f, 8);
		return true;
	}

	private void tickStorm(ServerPlayer p, ServerLevel l, long now) {
		Storm st = STORMS.get(p.getUUID());
		if (st == null) {
			return;
		}
		long end = timer(p, "storm_end");
		if (now >= end || st.dim() != l.dimension()) {
			STORMS.remove(p.getUUID());
			setTimer(p, "storm_end", 0);
			return;
		}
		for (int k = 0; k < 2; k++) {
			double a = p.getRandom().nextDouble() * Math.PI * 2;
			double r = Math.sqrt(p.getRandom().nextDouble()) * 4.0;
			Vec3 from = st.at().add(Math.cos(a) * r, 9 + p.getRandom().nextDouble() * 2, Math.sin(a) * r);
			AlienProjectileEntity shard = AlienProjectileEntity.of(p, "diamondhead_shard").colour(COLOUR).size(0.3f).damage(BARRAGE_SHARD_DAMAGE).speed(1.6)
					.life(30).direction(new Vec3((p.getRandom().nextDouble() - 0.5) * 0.2, -1, (p.getRandom().nextDouble() - 0.5) * 0.2));
			shard.moveTo(from.x, from.y, from.z);
			shard.spawn();
		}
		if (now % 6 == 0) {
			OmnitrixSounds.play(l, st.at(), SND_SHARD, 0.7f, 0.8f + p.getRandom().nextFloat() * 0.4f);
		}
	}

	// ---------------------------------------------------------------- Z: Crystal Eruption

	/**
	 * Z: the Crystal Eruption -- v0.17.2 (user: "Make Z an aoe spike ability"). He slams a fist into the ground and rings of
	 * crystal spikes burst up all round him, one ring a tick, out to {@link #ERUPT_RADIUS}: whatever stands in them takes
	 * {@link #ERUPT_DAMAGE} (once), is thrown up and out and is slowed.
	 */
	public boolean eruption(ServerPlayer p) {
		if (!use(p, ERUPT)) {
			return false;
		}
		anim(p, ANIM_ERUPT);
		ServerLevel l = level(p);
		ERUPTIONS.put(p.getUUID(), new Eruption(l.dimension(), p.position(), l.getGameTime(), new HashSet<>()));
		QuirkFx.ring(l, p.position().add(0, 0.05, 0)).colours(0xE0FFF6, COLOUR).size((float) ERUPT_RADIUS + 0.5f).thickness(0.7f).life(12).ground().spawn();
		l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, crystal()), p.getX(), p.getY() + 0.1, p.getZ(), 30, 0.6, 0.05, 0.6, 0.25);
		OmnitrixSounds.play(l, p.position(), SND_SHATTER, 1.1f, 0.6f);
		OmnitrixSounds.play(l, p.position(), SND_GROW, 1.2f, 0.7f);
		QuirkCamera.shakeAround(l, p.position(), 12, 0.35f, 10);
		return true;
	}

	/** The radius of the eruption's {@code ring}-th ring. */
	public static double eruptionRing(int ring) {
		return ERUPT_INNER + (ERUPT_RADIUS - ERUPT_INNER) * ring / (ERUPT_RINGS - 1);
	}

	private void tickEruption(ServerPlayer p, ServerLevel l, long now) {
		Eruption er = ERUPTIONS.get(p.getUUID());
		if (er == null) {
			return;
		}
		int ring = (int) (now - er.start());
		if (ring >= ERUPT_RINGS || er.dim() != l.dimension()) {
			ERUPTIONS.remove(p.getUUID());
			return;
		}
		if (ring < 0) {
			return;
		}
		double r = eruptionRing(ring);
		int n = Math.max(6, (int) Math.round(Math.PI * 2 * r / 1.35));
		// each ring turned half a step against the last, so the spikes stagger like teeth
		double turn = (ring % 2) * Math.PI / n;
		for (int i = 0; i < n; i++) {
			double a = i * Math.PI * 2 / n + turn;
			double jr = r + (l.random.nextDouble() - 0.5) * 0.5;
			Vec3 at = er.at().add(Math.cos(a) * jr, 0, Math.sin(a) * jr);
			BlockPos ground = groundUnder(l, BlockPos.containing(at.x, er.at().y - 0.5, at.z));
			if (ground == null) {
				continue;
			}
			Vec3 base = new Vec3(at.x, ground.getY() + 1.0, at.z);
			// leaning out from him, tallest in the middle rings
			Vec3 lean = new Vec3(Math.cos(a) * 0.38, 1, Math.sin(a) * 0.38).normalize();
			float tall = 1.5f + (float) Math.sin(Math.PI * (ring + 1) / (ERUPT_RINGS + 1)) * 1.1f + l.random.nextFloat() * 0.5f;
			QuirkFx.spike(l, base, lean).block(crystal()).colours(0xC8FFF0, COLOUR).size(tall).size2(0.42f).life(30 - ring * 2).spawn();
			if (i % 3 == 0) {
				l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, crystal()), base.x, base.y + 0.1, base.z, 3, 0.2, 0.05, 0.2, 0.15);
				l.sendParticles(SPARKLE, base.x, base.y + 0.6, base.z, 1, 0.2, 0.4, 0.2, 0.0);
			}
		}
		OmnitrixSounds.play(l, er.at(), SND_GROW, 0.8f, 1.0f + ring * 0.12f);
		// this ring and everything inside it not yet thrown
		for (LivingEntity e : around(p, er.at(), r + 0.9)) {
			if (Math.abs(e.getY() - er.at().y) > 3.0 || !er.hit().add(e.getId())) {
				continue;
			}
			e.invulnerableTime = 0;
			hurt(p, e, ERUPT_DAMAGE, false);
			double resist = Math.max(0.3, 1.0 - e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
			Vec3 out = new Vec3(e.getX() - er.at().x, 0, e.getZ() - er.at().z);
			out = out.lengthSqr() < 1.0e-4 ? Vec3.ZERO : out.normalize().scale(0.45 * resist);
			e.push(out.x, 0.7 * resist, out.z);
			e.hurtMarked = true;
			int slow = AlienStatus.isBoss(e) ? ERUPT_SLOW_TICKS / 4 : ERUPT_SLOW_TICKS;
			e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, slow, 1), p);
		}
	}

	// ---------------------------------------------------------------- X: Crystal Slide, Shift+X: Crystal Vault

	/** Steps up a block on the slide without stopping (the client drives the ride). */
	private static final ResourceLocation SLIDE_STEP = PowerToggles.id("omnitrix_diamondhead_slide_step");

	/**
	 * X: the Crystal Slide -- v0.17.2 (user: "make diamondheads crystal slide ability work similar to cryokinesis ice slide"):
	 * Cryokinesis' Ice Slide in crystal. A toggle (X again steps off): his own client rides him along his look (climbing when
	 * he looks up, riding down when he looks down -- {@code CryokinesisClient.ride}) while a 3-wide crystal ramp grows under
	 * him and back along the way he came ({@code CryokinesisHandlers.layRamp}), shattering 2 s behind him. Returns 1 when it
	 * starts, 0 otherwise.
	 */
	public int slide(ServerPlayer p) {
		if (flag(p, F_SLIDING)) {
			endSlide(p);
			return 0;
		}
		if (p.getAbilities().flying || p.isPassenger() || !use(p, SLIDE)) {
			return 0;
		}
		anim(p, ANIM_SLIDE);
		setFlag(p, F_SLIDING, true);
		PowerToggles.modifier(p, Attributes.STEP_HEIGHT, SLIDE_STEP, 0.65, AttributeModifier.Operation.ADD_VALUE);
		if (p.onGround()) {
			// a little hop so the first crystal goes in under him
			p.setDeltaMovement(p.getDeltaMovement().x, 0.25, p.getDeltaMovement().z);
			p.hurtMarked = true;
		}
		p.resetFallDistance();
		ServerLevel l = level(p);
		QuirkFx.trail(l, p).colours(0xE0FFF6, COLOUR).size(0.35f).size2(8f).life(16).vec(new Vec3(0, 0.1, 0)).style(QuirkFx.RIBBON_LIGHT).spawn();
		OmnitrixSounds.play(l, p.position(), SND_GROW, 0.9f, 1.3f);
		return 1;
	}

	/** Steps off the slide; 3 s of no fall damage after. */
	public void endSlide(ServerPlayer p) {
		PowerToggles.clearModifier(p, Attributes.STEP_HEIGHT, SLIDE_STEP);
		if (flag(p, F_SLIDING)) {
			setFlag(p, F_SLIDING, false);
			setTimer(p, "slide_grace", p.level().getGameTime() + 60);
		}
	}

	/** The ride's server half: the ramp under him, the sparkle, the crystal steps -- and no fall damage meanwhile. */
	private void slideTick(ServerPlayer p, ServerLevel l, long now) {
		if (!flag(p, F_SLIDING)) {
			if (now < timer(p, "slide_grace")) {
				p.resetFallDistance();
			}
			return;
		}
		if (!p.isAlive() || p.getAbilities().flying || p.isPassenger()) {
			endSlide(p);
			return;
		}
		p.resetFallDistance();
		Vec3 mv = p.getKnownMovement();
		Vec3 flat = new Vec3(mv.x, 0, mv.z);
		Vec3 dir = flat.lengthSqr() > 1.0e-4 ? flat.normalize() : flatLook(p);
		// as the Ice Slide: nothing while falling, nothing where he is about to be (the server sees him a tick late), and
		// looking down rides down instead of laying crystal under him
		if (p.getXRot() <= com.projecthero.mod.hero.power.p09.CryokinesisHandlers.DESCEND_PITCH && mv.y > -0.15) {
			net.minecraft.world.phys.AABB ahead = p.getBoundingBox().expandTowards(mv.x * 3, Math.min(0, mv.y * 3), mv.z * 3).inflate(0.3, 0.0, 0.3);
			// v0.17.2 revamp: each crystal of the ramp shoots up into place under him (solid at once)
			DiamondheadRise.Batch ramp = DiamondheadRise.batch(l, 3);
			com.projecthero.mod.hero.power.p09.CryokinesisHandlers.layRamp(l, p.position(), dir, mv.length(), ahead, cell -> ramp.place(cell, SLIDE_TICKS));
			ramp.start();
		}
		if (now % 2 == 0) {
			l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, crystal()), p.getX(), p.getY() + 0.1, p.getZ(), 3, 0.3, 0.05, 0.3, 0.05);
			l.sendParticles(SPARKLE, p.getX(), p.getY() + 0.15, p.getZ(), 1, 0.3, 0.05, 0.3, 0.0);
		}
		if (now % 6 == 0) {
			l.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_STEP, net.minecraft.sounds.SoundSource.PLAYERS, 0.7f, 1.3f);
		}
	}

	/** Shift+X: a crystal pillar shoots up under you. */
	public int vault(ServerPlayer p) {
		ServerLevel l = level(p);
		BlockPos base = p.blockPosition();
		if (!l.noCollision(p, p.getBoundingBox().move(0, 3.1, 0))) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.diamondhead.no_room").withStyle(ChatFormatting.GRAY), true);
			return 0;
		}
		if (!use(p, VAULT)) {
			return 0;
		}
		anim(p, ANIM_VAULT);
		p.teleportTo(p.getX(), p.getY() + 3.05, p.getZ());
		p.setDeltaMovement(p.getDeltaMovement().x, 0.6, p.getDeltaMovement().z);
		p.hurtMarked = true;
		p.resetFallDistance();
		// v0.17.2 revamp: the pillar punches up out of the ground under him
		DiamondheadRise.Batch pillar = DiamondheadRise.batch(l, 4);
		for (int y = 0; y < 3; y++) {
			pillar.place(base.above(y), 120);
		}
		pillar.start();
		int placed = pillar.size();
		QuirkFx.spike(l, Vec3.atBottomCenterOf(base), new Vec3(0, 1, 0)).block(crystal()).colours(0xC8FFF0, COLOUR).size(3.2f).size2(0.7f).life(30).spawn();
		OmnitrixSounds.play(l, p.position(), SND_GROW, 1.0f, 0.8f);
		return placed;
	}

	/**
	 * Shift+V: the Crystal Construct -- v0.17.2 (user: "using it normally places a wall, looking up builds the dome, looking down
	 * builds the bridge"). Looking more than {@link #CONSTRUCT_PITCH} degrees up raises a dome round him, as far down grows the
	 * bridge ({@link #crystalBridge}), otherwise the wall. Returns how many blocks it raised (0 if refused).
	 */
	public int wall(ServerPlayer p) {
		if (!p.mayBuild() || !use(p, WALL)) {
			return 0;
		}
		float pitch = p.getXRot();
		if (pitch < -CONSTRUCT_PITCH) {
			return dome(p);
		}
		if (pitch > CONSTRUCT_PITCH) {
			return crystalBridge(p) ? BRIDGE_LENGTH * 3 : 0;
		}
		return crystalWall(p);
	}

	/** A 5-wide, 3-high wall of crystal 2.5 blocks ahead. */
	private int crystalWall(ServerPlayer p) {
		anim(p, ANIM_WALL);
		ServerLevel l = level(p);
		Vec3 f = flatLook(p);
		Vec3 side = new Vec3(-f.z, 0, f.x);
		Vec3 c = p.position().add(f.scale(2.5));
		// v0.17.2 revamp: the wall grinds up out of the ground, the middle column first
		DiamondheadRise.Batch wall = DiamondheadRise.batch(l, DiamondheadRise.RISE_TICKS);
		for (int w = -2; w <= 2; w++) {
			for (int h = 0; h < 3; h++) {
				wall.place(BlockPos.containing(c.x + side.x * w, p.getY() + h + 0.01, c.z + side.z * w), WALL_TICKS);
			}
		}
		wall.start();
		int placed = wall.size();
		QuirkCamera.shakeAround(l, c, 10, 0.2f, 10);
		OmnitrixSounds.play(l, c, SND_GROW, 1.1f, 0.9f);
		return placed;
	}

	/**
	 * Shift+V looking up: a dome of crystal round him -- a shell {@link #DOME_RADIUS} across his feet, rising from the ground in
	 * rings (the lowest first, the cap last), for {@link #WALL_TICKS}.
	 */
	private int dome(ServerPlayer p) {
		anim(p, ANIM_WALL);
		ServerLevel l = level(p);
		BlockPos c = p.blockPosition();
		DiamondheadRise.Batch dome = DiamondheadRise.batch(l, DiamondheadRise.RISE_TICKS);
		int r = (int) Math.ceil(DOME_RADIUS);
		for (int dy = 0; dy <= r; dy++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
					if (d > DOME_RADIUS || d < DOME_RADIUS - 1.0) {
						continue;
					}
					BlockPos q = c.offset(dx, dy, dz);
					if (l.getBlockState(q).canBeReplaced() && l.mayInteract(p, q)) {
						dome.place(q, WALL_TICKS);
					}
				}
			}
		}
		dome.start();
		int placed = dome.size();
		QuirkFx.ring(l, p.position().add(0, 0.05, 0)).colours(0xE0FFF6, COLOUR).style(QuirkFx.RING_SOFT).size((float) DOME_RADIUS * 2f).thickness(0.4f)
				.life(14).ground().spawn();
		QuirkCamera.shakeAround(l, p.position(), 10, 0.2f, 10);
		OmnitrixSounds.play(l, p.position(), SND_GROW, 1.1f, 0.75f);
		return placed;
	}

	// ---------------------------------------------------------------- Shift+G: the Crystal Pillar

	/**
	 * Shift+G: the Crystal Pillar -- v0.17.2 (user: "shift+G a different attack move"; in the show he makes crystal burst out of the
	 * ground under his enemies). He drives a hand down; the ground under the creature he looks at (up to {@link #PILLAR_RANGE}
	 * blocks, else the spot) splits and a pillar of crystal spears up: {@link #PILLAR_DAMAGE} and thrown high (a boss only
	 * staggered), the pillar standing a moment before it shatters.
	 */
	public boolean crystalPillar(ServerPlayer p) {
		if (!use(p, PILLAR)) {
			return false;
		}
		anim(p, ANIM_PILLAR);
		ServerLevel l = level(p);
		LivingEntity t = lookTarget(p, PILLAR_RANGE);
		Vec3 at;
		if (t != null) {
			at = t.position();
		} else {
			Vec3 eye = p.getEyePosition();
			var hit = l.clip(new net.minecraft.world.level.ClipContext(eye, eye.add(p.getLookAngle().scale(PILLAR_RANGE)),
					net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, p));
			at = hit.getLocation();
		}
		// the ground under it: down to the first solid block
		BlockPos base = BlockPos.containing(at);
		for (int k = 0; k < 6 && l.getBlockState(base.below()).getCollisionShape(l, base.below()).isEmpty(); k++) {
			base = base.below();
		}
		Vec3 foot = Vec3.atBottomCenterOf(base);
		for (LivingEntity e : around(p, foot.add(0, 1, 0), 1.6)) {
			e.invulnerableTime = 0;
			hurt(p, e, PILLAR_DAMAGE, false);
			if (AlienStatus.isBoss(e)) {
				e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 2), p);
			} else {
				e.setDeltaMovement(e.getDeltaMovement().x * 0.3, 1.15, e.getDeltaMovement().z * 0.3);
				e.hurtMarked = true;
				e.hasImpulse = true;
			}
		}
		QuirkFx.decal(l, foot.add(0, 0.02, 0)).style(QuirkFx.DECAL_CRACK).colours(0xE0FFF6, COLOUR).size(2.6f).life(50).spawn();
		QuirkFx.spike(l, foot.add(0, 0.05, 0), new Vec3(0, 1, 0)).block(crystal()).colours(0xC8FFF0, COLOUR).size(4.2f).size2(0.9f).life(26).spawn();
		QuirkFx.ring(l, foot.add(0, 0.05, 0)).colours(0xE0FFF6, COLOUR).style(QuirkFx.RING_SHOCK).size(3.2f).thickness(0.4f).life(10).ground().spawn();
		QuirkFx.shards(l, foot.add(0, 0.4, 0)).block(crystal()).size(1.1f).count(12).life(24).spawn();
		OmnitrixSounds.play(l, foot, DiamondheadRise.SND_RISE, 1.2f, 0.9f);
		OmnitrixSounds.play(l, foot, SND_STAB, 1.0f, 0.7f);
		QuirkCamera.shakeAround(l, foot, 12, 0.3f, 8);
		return true;
	}

	// ---------------------------------------------------------------- his hands

	/**
	 * Where his hand is (blocks): a little out from the shoulder, at chest height and a half-block ahead -- v0.17.2 (user: "dont
	 * make the projectiles shoot from his face, make them shoot from his hands").
	 */
	static Vec3 hand(ServerPlayer p, boolean right) {
		Vec3 f = Vec3.directionFromRotation(0, p.getYRot());
		Vec3 side = new Vec3(-f.z, 0, f.x);
		double scale = p.getScale();
		return p.position().add(f.scale(0.55 * scale)).add(side.scale((right ? 0.42 : -0.42) * scale)).add(0, 1.15 * scale, 0);
	}

	/** What his crosshair is on (a creature's middle, else the first block, 40 blocks out at most): where hand-thrown shards head. */
	private static Vec3 aimPoint(ServerPlayer p) {
		LivingEntity t = lookTarget(p, TITAN_RANGE);
		return t != null ? t.getBoundingBox().getCenter() : lookPoint(p, level(p));
	}

	private static AlienProjectileEntity fromHand(AlienProjectileEntity e, Vec3 hand) {
		e.moveTo(hand.x, hand.y, hand.z, e.getYRot(), e.getXRot());
		return e;
	}

	/**
	 * Shift+Z: the Titan Shards. He plants himself (slowed to a crawl) and heaves three giant crystal shards up out of the ground
	 * round him, then hurls them one after another at what he is looking at (up to {@link #TITAN_RANGE} blocks; re-aimed at each
	 * throw, homing a little): {@link #TITAN_DAMAGE} to what a shard strikes and {@link #TITAN_SPLASH} round where it shatters.
	 */
	public boolean barrage(ServerPlayer p) {
		if (TITANS.containsKey(p.getUUID()) || !use(p, BARRAGE)) {
			return false;
		}
		anim(p, ANIM_BARRAGE);
		ServerLevel l = level(p);
		LivingEntity t = lookTarget(p, TITAN_RANGE);
		TITANS.put(p.getUUID(), new Titan(l.dimension(), l.getGameTime(), t == null ? -1 : t.getId(), lookPoint(p, l)));
		PowerToggles.modifier(p, Attributes.MOVEMENT_SPEED, TITAN_SLOW_ID, -0.75, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		// he braces: a crack spreads under him and the ground groans
		QuirkFx.decal(l, p.position().add(0, 0.02, 0)).style(QuirkFx.DECAL_CRACK).colours(0xE0FFF6, COLOUR).size(4.5f).life(TITAN_THROW_AT[2] + 10).spawn();
		QuirkFx.ring(l, p.position().add(0, 0.05, 0)).colours(0xE0FFF6, COLOUR).style(QuirkFx.RING_SOFT).size(3.5f).thickness(0.4f).life(14).ground().spawn();
		OmnitrixSounds.play(l, p.position(), DiamondheadRise.SND_RISE, 1.1f, 0.6f);
		QuirkCamera.shakeAround(l, p.position(), 12, 0.2f, 12);
		return true;
	}

	private static Vec3 lookPoint(ServerPlayer p, ServerLevel l) {
		Vec3 eye = p.getEyePosition();
		var hit = l.clip(new net.minecraft.world.level.ClipContext(eye, eye.add(p.getLookAngle().scale(TITAN_RANGE)),
				net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, p));
		return hit.getLocation();
	}

	/** Where shard {@code i} hangs round him (his look's yaw, so they swing round as he aims). */
	private static Vec3 titanHover(ServerPlayer p, int i, float age) {
		Vec3 fwd = flatLook(p);
		Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
		double[] h = TITAN_HOVER[i];
		double bob = Math.sin(age * 0.15 + i * 2.1) * 0.12;
		return p.position().add(right.scale(h[0])).add(fwd.scale(h[2])).add(0, h[1] + bob, 0);
	}

	/** What the shards fly at: what he looks at now, else the last target, else the point he looks at. */
	private static Vec3 titanAim(ServerPlayer p, ServerLevel l, Titan tt) {
		LivingEntity t = lookTarget(p, TITAN_RANGE);
		if (t != null) {
			tt.target = t.getId();
		}
		Entity e = tt.target == -1 ? null : l.getEntity(tt.target);
		if (e instanceof LivingEntity le && le.isAlive() && le.distanceToSqr(p) < TITAN_RANGE * TITAN_RANGE) {
			return le.getBoundingBox().getCenter();
		}
		tt.target = -1;
		tt.point = lookPoint(p, l);
		return tt.point;
	}

	private void tickTitan(ServerPlayer p, ServerLevel l, long now) {
		Titan tt = TITANS.get(p.getUUID());
		if (tt == null) {
			return;
		}
		if (tt.dim != l.dimension()) {
			endTitan(p, true);
			return;
		}
		long age = now - tt.start;
		Vec3 aim = titanAim(p, l, tt);
		for (int i = 0; i < 3; i++) {
			if (age >= TITAN_RISE_AT[i] && tt.ids[i] == -1) {
				// it breaks the ground: a crack, chips, and the crystal starts up from below
				Vec3 h = titanHover(p, i, age);
				Vec3 ground = new Vec3(h.x, p.getY(), h.z);
				float len = TITAN_SIZE[i] * 3.4f;
				AlienProjectileEntity s = AlienProjectileEntity.of(p, "diamondhead_titan").colour(COLOUR).size(TITAN_SIZE[i]).damage(TITAN_DAMAGE)
						.life(TITAN_END + 20).held(true);
				s.setDeltaMovement(Vec3.ZERO);
				s.moveTo(ground.x, ground.y - len, ground.z, 0f, -90f);
				s.spawn();
				tt.ids[i] = s.getId();
				QuirkFx.decal(l, ground.add(0, 0.02, 0)).style(QuirkFx.DECAL_CRACK).colours(0xE0FFF6, COLOUR).size(2.2f).life(60).spawn();
				QuirkFx.shards(l, ground.add(0, 0.3, 0)).block(crystal()).size(0.9f).count(10).life(22).spawn();
				l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, l.getBlockState(BlockPos.containing(ground).below())), ground.x, ground.y + 0.1,
						ground.z, 14, 0.5, 0.1, 0.5, 0.15);
				OmnitrixSounds.play(l, ground, DiamondheadRise.SND_RISE, 1.2f, 0.7f + i * 0.08f);
				OmnitrixSounds.play(l, ground, SND_GROW, 0.9f, 0.6f);
				QuirkCamera.shakeAround(l, ground, 10, 0.25f, 8);
			}
			Entity e = tt.ids[i] == -1 ? null : l.getEntity(tt.ids[i]);
			if (!(e instanceof AlienProjectileEntity s) || !s.isAlive()) {
				continue;
			}
			if (!tt.thrown[i]) {
				Vec3 h = titanHover(p, i, age);
				float len = TITAN_SIZE[i] * 3.4f;
				long up = age - TITAN_RISE_AT[i];
				float k = Mth.clamp(up / (float) TITAN_RISE_TICKS, 0f, 1f);
				float ease = 1f - (1f - k) * (1f - k) * (1f - k);
				double y = Mth.lerp(ease, p.getY() - len, h.y);
				// upright as it climbs, then it swings round to point at the target
				float turn = Mth.clamp((up - TITAN_RISE_TICKS * 0.7f) / 10f, 0f, 1f);
				Vec3 pt = new Vec3(h.x, y, h.z);
				Vec3 to = aim.subtract(pt);
				Vec3 dir = new Vec3(0, 1, 0).lerp(to.lengthSqr() > 1.0e-4 ? to.normalize() : new Vec3(0, 1, 0), turn);
				s.moveTo(pt.x, pt.y, pt.z, yawOf(dir), pitchOf(dir));
				s.setDeltaMovement(Vec3.ZERO);
				if (age >= TITAN_THROW_AT[i]) {
					tt.thrown[i] = true;
					Vec3 d = to.normalize();
					s.held(false);
					s.setDeltaMovement(d.scale(TITAN_SPEED));
					s.hurtMarked = true;
					OmnitrixSounds.play(l, pt, SND_SHARD, 1.3f, 0.55f);
					l.playSound(null, pt.x, pt.y, pt.z, SoundEvents.PLAYER_ATTACK_SWEEP, net.minecraft.sounds.SoundSource.PLAYERS, 1.2f, 0.5f);
					QuirkFx.ring(l, pt).colours(0xE0FFF6, COLOUR).style(QuirkFx.RING_SHOCK).size(2.0f).thickness(0.3f).life(8).vec(d).spawn();
				}
				continue;
			}
			// in flight: homing a little on the target, and a giant shard strikes whatever it brushes, not only what its tip meets
			Vec3 v = s.getDeltaMovement();
			Vec3 want = aim.subtract(s.position());
			if (want.lengthSqr() > 1.0e-4 && v.lengthSqr() > 1.0e-4) {
				Vec3 nd = v.normalize().lerp(want.normalize(), 0.3).normalize();
				s.setDeltaMovement(nd.scale(TITAN_SPEED));
				s.setYRot(yawOf(nd));
				s.setXRot(pitchOf(nd));
				s.hurtMarked = true;
			}
			LivingEntity struck = null;
			for (LivingEntity c : around(p, s.position(), 1.5 + TITAN_SIZE[i] * 0.9)) {
				struck = c;
				break;
			}
			if (struck != null) {
				struck.invulnerableTime = 0; // each giant shard lands in full, however close behind the last
				hurt(p, struck, TITAN_DAMAGE, false);
				titanImpact(p, l, s.position(), struck, s.size());
				s.discard();
			} else if (age >= TITAN_THROW_AT[i] + TITAN_FLIGHT) {
				titanImpact(p, l, s.position(), null, s.size());
				s.discard();
			}
		}
		if (age >= TITAN_THROW_AT[2] + 2) {
			PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, TITAN_SLOW_ID);
		}
		if (age >= TITAN_END) {
			endTitan(p, false);
		}
	}

	/** A Titan Shard shattering at {@code at}: {@link #TITAN_SPLASH} round it (not again on {@code skip}), thrown back, and a crash. */
	static void titanImpact(ServerPlayer p, ServerLevel l, Vec3 at, LivingEntity skip, float size) {
		for (LivingEntity e : around(p, at, TITAN_SPLASH_RADIUS)) {
			if (e != skip) {
				e.invulnerableTime = 0;
				hurt(p, e, TITAN_SPLASH, false);
			}
			shove(e, at, 1.1, 0.45);
		}
		QuirkFx.shards(l, at).block(crystal()).size(1.4f + size).count(22).life(34).spawn();
		QuirkFx.ring(l, at).colours(0xE0FFF6, COLOUR).style(QuirkFx.RING_SHOCK).size(3.5f + size * 2f).thickness(0.5f).life(12).spawn();
		QuirkFx.decal(l, at).style(QuirkFx.DECAL_CRACK).colours(0xE0FFF6, COLOUR).size(3f).life(80).spawn();
		OmnitrixSounds.play(l, at, SND_SHATTER, 1.4f, 0.6f);
		l.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), net.minecraft.sounds.SoundSource.PLAYERS, 0.7f, 1.3f);
		QuirkCamera.shakeAround(l, at, 16, 0.45f, 12);
	}

	/** The move is over (or cut short: the shards still held crumble away). */
	private static void endTitan(ServerPlayer p, boolean cut) {
		Titan tt = TITANS.remove(p.getUUID());
		PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, TITAN_SLOW_ID);
		if (tt == null || !(p.level() instanceof ServerLevel l)) {
			return;
		}
		for (int i = 0; i < 3; i++) {
			Entity e = tt.ids[i] == -1 ? null : l.getEntity(tt.ids[i]);
			if (e instanceof AlienProjectileEntity s && s.isAlive() && (cut || !tt.thrown[i])) {
				QuirkFx.shards(l, s.position()).block(crystal()).size(1.2f).count(10).life(20).spawn();
				s.discard();
			}
		}
	}

	private static float yawOf(Vec3 d) {
		return (float) (Mth.atan2(-d.x, d.z) * Mth.RAD_TO_DEG);
	}

	private static float pitchOf(Vec3 d) {
		return (float) (-Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG);
	}

	// ---------------------------------------------------------------- vanilla keys

	@Override
	public boolean onAttack(ServerPlayer p, Entity target) {
		if (!(target instanceof LivingEntity le)) {
			return false;
		}
		anim(p, ANIM_SLASH);
		ServerLevel l = level(p);
		l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, crystal()), le.getX(), le.getY() + le.getBbHeight() * 0.6, le.getZ(), 8, 0.2, 0.3, 0.2,
				0.1);
		if (flag(p, F_COMBAT)) {
			// the blades' slash sweeps what stands beside the target
			for (LivingEntity e : around(p, le.position(), 2.5)) {
				if (e != le) {
					e.invulnerableTime = 0;
					hurt(p, e, 4f, false);
				}
			}
			l.sendParticles(ParticleTypes.SWEEP_ATTACK, le.getX(), le.getY() + le.getBbHeight() * 0.5, le.getZ(), 1, 0, 0, 0, 0);
			OmnitrixSounds.play(l, le.position(), SND_SLASH, 0.6f, 1.3f);
		}
		return false;
	}

	@Override
	public InteractionResult onUseBlock(ServerPlayer p, InteractionHand hand, BlockHitResult hit) {
		if (hand != InteractionHand.MAIN_HAND || !p.getMainHandItem().isEmpty()) {
			return InteractionResult.PASS;
		}
		if (flag(p, F_COMBAT)) {
			return shieldUse(p); // v0.17.2: in Combat Mode the right-click is the shield, not a crystal block
		}
		return placeCrystal(p, hit.getBlockPos().relative(hit.getDirection())) ? InteractionResult.SUCCESS : InteractionResult.PASS;
	}

	/** Use on a block: a crystal block on that face for 30 s. */
	public boolean placeCrystal(ServerPlayer p, BlockPos at) {
		ServerLevel l = level(p);
		if (!p.mayBuild() || !l.mayInteract(p, at) || !ready(p, PLACE)) {
			return false;
		}
		DiamondheadRise.Batch one = DiamondheadRise.batch(l, 6);
		if (!l.isUnobstructed(crystal(), at, net.minecraft.world.phys.shapes.CollisionContext.empty()) || !one.place(at, PLACE_TICKS)) {
			return false;
		}
		one.start();
		cooldown(p, PLACE, -1);
		anim(p, ANIM_PLACE);
		p.swing(InteractionHand.MAIN_HAND, true);
		OmnitrixSounds.play(l, Vec3.atCenterOf(at), SND_GROW, 0.6f, 1.6f);
		return true;
	}

	// ---------------------------------------------------------------- damage

	/** The Crystal Shield (and the Bash behind it): anything from in front is blocked and chips the shield instead. */
	@Override
	public boolean allowDamage(ServerPlayer p, DamageSource source, float amount) {
		// v0.17.2: the landing a Crystal Tether flings him into never hurts
		if (source.is(DamageTypeTags.IS_FALL) && p.level().getGameTime() < timer(p, "tether_safe")) {
			return false;
		}
		if ((flag(p, F_SHIELD) || flag(p, F_BASH)) && source.getSourcePosition() != null && !source.is(DamageTypeTags.BYPASSES_ARMOR)
				&& !source.is(DamageTypeTags.BYPASSES_SHIELD) && inFront(p, source.getSourcePosition())) {
			damageShield(p, amount);
			cut(p, source);
			return false;
		}
		return true;
	}

	@Override
	public float incomingDamage(ServerPlayer p, DamageSource source, float amount) {
		cut(p, source);
		return amount;
	}

	/** The passive: whoever strikes him bare-handed or with a blade (a melee hit) is cut on his crystal. */
	private void cut(ServerPlayer p, DamageSource source) {
		if (cutting || !(source.getEntity() instanceof LivingEntity attacker) || source.getDirectEntity() != attacker || attacker == p
				|| attacker.distanceToSqr(p) > 36 || source.is(DamageTypeTags.IS_PROJECTILE)) {
			return;
		}
		cutting = true;
		try {
			attacker.invulnerableTime = 0;
			hurt(p, attacker, CUT_DAMAGE, false);
			level(p).sendParticles(SPARKLE, attacker.getX(), attacker.getY() + attacker.getBbHeight() * 0.6, attacker.getZ(), 4, 0.2, 0.2, 0.2, 0.02);
		} finally {
			cutting = false;
		}
	}

	// ---------------------------------------------------------------- v0.17.2 C / Shift+C (replacing Regrowth and Crystal Armour)

	/**
	 * C: the Crystal Prison -- the creature he looks at (within {@link #PRISON_RANGE}) is sealed in a shell of crystal for
	 * {@link #PRISON_TICKS} (a boss a third of it) and can't move; then the shell shatters for {@link #PRISON_DAMAGE}. A creature too big
	 * for a shell is held by crystal spikes instead. False (no cooldown) with nothing to aim at.
	 */
	public boolean crystalPrison(ServerPlayer p) {
		LivingEntity t = lookTarget(p, PRISON_RANGE);
		if (t == null || !use(p, PRISON)) {
			return false;
		}
		anim(p, ANIM_PRISON);
		ServerLevel l = level(p);
		boolean boss = AlienStatus.isBoss(t);
		int ticks = boss ? PRISON_TICKS / 3 : PRISON_TICKS;
		List<BlockPos> cells = new java.util.ArrayList<>();
		// v0.17.2 revamp: the shell grows up out of the ground round it
		DiamondheadRise.Batch shellRise = DiamondheadRise.batch(l, 5);
		var box = t.getBoundingBox();
		if (box.getXsize() <= 3.0 && box.getYsize() <= 4.0) {
			// a shell one block out from every block the creature fills; it melts away with the prison
			int x0 = net.minecraft.util.Mth.floor(box.minX) - 1, x1 = net.minecraft.util.Mth.floor(box.maxX - 1.0e-3) + 1;
			int y0 = net.minecraft.util.Mth.floor(box.minY) - 1, y1 = net.minecraft.util.Mth.floor(box.maxY - 1.0e-3) + 1;
			int z0 = net.minecraft.util.Mth.floor(box.minZ) - 1, z1 = net.minecraft.util.Mth.floor(box.maxZ - 1.0e-3) + 1;
			for (int x = x0; x <= x1; x++) {
				for (int y = y0; y <= y1; y++) {
					for (int z = z0; z <= z1; z++) {
						boolean shell = x == x0 || x == x1 || y == y0 || y == y1 || z == z0 || z == z1;
						BlockPos at = new BlockPos(x, y, z);
						if (shell && l.getBlockState(at).canBeReplaced() && shellRise.place(at, ticks + 20)) {
							cells.add(at);
						}
					}
				}
			}
		} else {
			for (int i = 0; i < 6; i++) {
				double a = i * Math.PI / 3;
				Vec3 base = t.position().add(Math.cos(a) * box.getXsize() * 0.55, 0, Math.sin(a) * box.getXsize() * 0.55);
				QuirkFx.spike(l, base, new Vec3(-Math.cos(a) * 0.4, 1, -Math.sin(a) * 0.4).normalize()).block(crystal()).colours(0xC8FFF0, COLOUR)
						.size(2.6f).size2(0.5f).life(ticks).spawn();
			}
		}
		shellRise.start();
		PRISONS.put(p.getUUID(), new Prison(l.dimension(), t.getId(), t.position(), l.getGameTime() + ticks, cells));
		QuirkFx.ribbon(l, p.position().add(0, 1.3, 0), t.position().add(0, t.getBbHeight() * 0.5, 0)).colours(0xE0FFF6, COLOUR).size(0.25f).life(8)
				.style(QuirkFx.RIBBON_LIGHT).spawn();
		OmnitrixSounds.play(l, t.position(), SND_GROW, 1.2f, 0.6f);
		return true;
	}

	/** Whether {@code e} is sealed in someone's Crystal Prison right now (server side). */
	public static boolean imprisoned(Entity e) {
		for (Prison pr : PRISONS.values()) {
			if (pr.target() == e.getId() && pr.dim() == e.level().dimension()) {
				return true;
			}
		}
		return false;
	}

	private void tickPrison(ServerPlayer p, ServerLevel l, long now) {
		Prison pr = PRISONS.get(p.getUUID());
		if (pr == null) {
			return;
		}
		Entity e = l.getEntity(pr.target());
		if (pr.dim() != l.dimension() || !(e instanceof LivingEntity t) || !t.isAlive()) {
			PRISONS.remove(p.getUUID());
			return;
		}
		if (now < pr.end()) {
			// held: no walking out
			t.setDeltaMovement(0, Math.min(0, t.getDeltaMovement().y), 0);
			t.teleportTo(pr.at().x, t.getY(), pr.at().z);
			t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 5, 9, false, false, false), p);
			t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 5, 4, false, false, false), p);
			if (now % 4 == 0) {
				l.sendParticles(SPARKLE, t.getX(), t.getY() + t.getBbHeight() * 0.5, t.getZ(), 2, t.getBbWidth() * 0.6, t.getBbHeight() * 0.4,
						t.getBbWidth() * 0.6, 0.0);
			}
			return;
		}
		// the shell shatters, there and then
		PRISONS.remove(p.getUUID());
		// (shattered, not sunk)
		PetrosapienCrystalBlock.shattering = true;
		try {
			for (BlockPos c : pr.shell()) {
				if (l.getBlockState(c).is(DiamondheadBlocks.CRYSTAL)) {
					l.setBlock(c, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
				}
			}
		} finally {
			PetrosapienCrystalBlock.shattering = false;
		}
		t.invulnerableTime = 0;
		hurt(p, t, PRISON_DAMAGE, false);
		QuirkFx.shards(l, t.position().add(0, t.getBbHeight() * 0.5, 0)).block(crystal()).size(1.8f).count(22).life(22).spawn();
		OmnitrixSounds.play(l, t.position(), SND_SHATTER, 1.2f, 0.8f);
	}

}
