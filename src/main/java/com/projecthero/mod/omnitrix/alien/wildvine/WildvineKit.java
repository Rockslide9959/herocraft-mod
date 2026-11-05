package com.projecthero.mod.omnitrix.alien.wildvine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.omnitrix.Omnitrix;
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

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * v0.17.2 (user: "also create a moveset for wildvine please ... do research on his powers and look at what he does in the show ...
 * Make this another alien you unlock on the 11th mistransformation"). Wildvine, a Florauna: a walking plant whose limbs stretch like
 * vines. He lashes from far off, swings on his own vines, throws the black seed pods on his back as bombs and plants them as mines,
 * burrows through the ground and bursts back up, walks through trees, climbs any wall, wraps foes up and regrows what he loses -- but
 * fire and frost hit a plant hard. Spec: {@code docs/OMNITRIX_REVAMP_WILDVINE.md}.
 *
 * <p>Found like Cannonbolt: not on the prototype's dial until a mistransform lands on him ({@link Aliens#hidden}).
 */
public final class WildvineKit extends AlienKit {
	public static final String ID = "wildvine";
	public static final int COLOUR = 0x5FB043;

	// ---------------------------------------------------------------- synced flags
	public static final int F_SWING = 1;
	/** Root Burrow: under the ground (drawn as a moving mound, untouchable). */
	public static final int F_BURROW = 4;
	/** Thicket Camouflage. */
	public static final int F_CAMO = 8;
	/** In the air off one of his vines (no fall damage till he lands). */
	public static final int F_AIR = 16;

	// ---------------------------------------------------------------- poses
	public static final int ANIM_LASH = 1, ANIM_SLASH = 2, ANIM_THROW = 3, ANIM_PLANT = 4, ANIM_SWING = 5, ANIM_WRAP = 6, ANIM_SNARE = 7,
			ANIM_REGROW = 8, ANIM_OVERGROWTH = 9, ANIM_EMERGE = 10, ANIM_PUNCH = 11, ANIM_DIVE = 12, ANIM_SLAM = 13, ANIM_DETONATE = 14, ANIM_CAMO = 15,
			ANIM_RELEASE = 16;

	// ---------------------------------------------------------------- moves (lang projecthero.omnitrix.wildvine.<id>)
	public static final String LASH = "vine_lash", SLASH = "vine_slash", SEED_BOMB = "seed_bomb", SEED_MINES = "seed_mines", SWING = "vine_swing",
			BURROW = "root_burrow", CONSTRICT = "constrict", OVERGROWTH = "overgrowth", REGROWTH = "regrowth", CAMO = "thicket_camouflage", SNARE = "vine_snare",
			SLAM = "vine_slam", ARMS = "stretch_arms", CLIMB = "creeper_climb", SUN = "photosynthesis", GREENWALK = "green_walk";

	// ---------------------------------------------------------------- numbers (user's where given, else the alien damage scale)
	public static final float LASH_DAMAGE = 12f, SLASH_DAMAGE = 18f, SEED_DIRECT = 4f, SEED_DAMAGE = 18f, MINE_DAMAGE = 16f, EMERGE_DAMAGE = 14f,
			SQUEEZE_DAMAGE = 4f, SNARE_DAMAGE = 8f, OVERGROWTH_DAMAGE = 22f, SLAM_DAMAGE = 20f, SLAM_SPLASH = 8f, REGROW_HEAL = 10f;
	public static final double LASH_RANGE = 12, SLASH_RANGE = 20, SLASH_HEIGHT = 10, SWING_RANGE = 45, CONSTRICT_RANGE = 12, SLAM_RANGE = 16, SLAM_RADIUS = 3.0,
			SEED_RADIUS = 3.0, MINE_TRIGGER = 1.6, MINE_RADIUS = 2.5, EMERGE_RADIUS = 3.0, SNARE_RADIUS = 5.0, OVERGROWTH_RADIUS = 7.0;
	public static final int MINES = 3, MINE_ARM = 20, MINE_LIFE = 400, CONSTRICT_TICKS = 60, SQUEEZE_EVERY = 10, SNARE_ROOT = 40, OVERGROWTH_ROOT = 60,
			CAMO_TICKS = 120;
	/** v0.17.2 (user: "make vine lash work similar to ladybugs yoyo R ability"): her out / back ticks and cooldown, her slight auto-aim. */
	public static final int LASH_COOLDOWN = 16, LASH_OUT = 3, LASH_BACK = 4;
	public static final double LASH_ASSIST_COS = 0.966, LASH_ASSIST_RADIUS = 1.0;
	/** Vine Slash's cone: half its opening (degrees, as a cosine); how fast the tendrils run out (blocks a tick). */
	public static final double SLASH_HALF_COS = 0.819, SLASH_SPEED = 2.5;
	public static final int SLASH_COOLDOWN = 200, SLASH_WINDUP = 3, SLASH_LIFE = 22;
	/** Vine Slam: the lift (ticks up), the fall (ticks down), the height over him and how far ahead it lands. */
	public static final int SLAM_COOLDOWN = 240, SLAM_LIFT = 9, SLAM_FALL = 4;
	/** Ticks between it reaching the ground and the blow: other clients draw a mob a few ticks behind, and the impact must not beat it down. */
	public static final int SLAM_SETTLE = 2;
	public static final double SLAM_APEX = 4.5, SLAM_AHEAD = 3.0;
	/** The swing's rope: Ladybug's -- letting go starts a 0.3 s cooldown, the line snaps this far past its length. */
	public static final int SWING_COOLDOWN = 6;
	public static final double SWING_SLACK_BREAK = 14.0;
	/** v0.17.2 (user: "reduce seed bomb cooldown to 1 second", "make it so that the player regenerates 1 seed bomb every 3 seconds"). */
	public static final int SEED_BOMB_COOLDOWN = 20, SEED_MINES_COOLDOWN = 300;
	/** The six pods on his back ARE his stock: bombs and mines both take from it, and one regrows every {@link #POD_REGROW} ticks. */
	public static final int PODS = 6, POD_REGROW = 60;
	/** The burrow's cooldown runs from when he comes back up; the dive's length (the mound takes over after it). */
	public static final int BURROW_COOLDOWN = 300, DIVE_TICKS = 6;
	/** Pose beats: when the thrown pod leaves his hand, the mines reach the ground, the snare and overgrowth land, the cloak closes. */
	public static final int THROW_AT = 3, PLANT_AT = 5, SNARE_AT = 4, OVERGROWTH_AT = 7, CAMO_AT = 6;
	/** Weaknesses: fire and frost hurt a plant this much more. */
	public static final float FIRE_WEAKNESS = 1.75f, FROST_WEAKNESS = 1.75f;

	/** The trees and plants his body passes on foot (data-driven; a modpack can add to them). */
	public static final TagKey<Block> PLANTS = TagKey.create(Registries.BLOCK, ProjectHeroMod.id("wildvine_plants"));
	/** The ground he phases through with Shift held in the burrow. */
	public static final TagKey<Block> EARTH = TagKey.create(Registries.BLOCK, ProjectHeroMod.id("wildvine_earth"));
	/** A block whose top is at his feet (this close) is his floor: he stands on it, he does not sink through. */
	private static final double FLOOR_SLOP = 0.01;
	/** The burrowed body: low to the ground, the camera with it. */
	private static final EntityDimensions MOUND_BODY = EntityDimensions.scalable(0.6f, 0.6f).withEyeHeight(0.45f);
	/** How far up a surfacing Wildvine looks for room. */
	private static final int RISE_MAX = 64;

	private static final ResourceLocation BURROW_SPEED = PowerToggles.id("omnitrix_wildvine_burrow");
	private static final ResourceLocation BURROW_SNEAK = PowerToggles.id("omnitrix_wildvine_burrow_sneak");

	// the line he swings on (Spidermonkey's synced keys: the owning client moves him)
	public static final String LINE = "line", LINE_AT = "line_at", HOOK_X = "hook_x", HOOK_Y = "hook_y", HOOK_Z = "hook_z", ROPE = "rope";
	private static final String[] LINE_KEYS = { LINE, LINE_AT, HOOK_X, HOOK_Y, HOOK_Z, ROPE };
	private static final String BURROW_AT = "burrow_at", CAMO_END = "camo_end", AIR_AT = "air_at", PODS_SPENT = "pods_spent", POD_NEXT = "pod_next";

	// ---------------------------------------------------------------- sounds
	public static final OmnitrixSfx.Cue SND_LASH = OmnitrixSfx.cue("wildvine_lash")
			.layer(SoundEvents.FISHING_BOBBER_THROW, 0.7f, 0.7f)
			.layer(SoundEvents.CAVE_VINES_PLACE, 0.6f, 0.9f);
	public static final OmnitrixSfx.Cue SND_SNAP = OmnitrixSfx.cue("wildvine_snap")
			.layer(SoundEvents.PLAYER_ATTACK_SWEEP, 0.5f, 1.3f)
			.layer(SoundEvents.BIG_DRIPLEAF_TILT_DOWN, 0.6f, 0.8f);
	public static final OmnitrixSfx.Cue SND_GROW = OmnitrixSfx.cue("wildvine_grow")
			.layer(SoundEvents.CAVE_VINES_PLACE, 0.7f, 0.6f)
			.layer(SoundEvents.ROOTED_DIRT_BREAK, 0.6f, 0.7f, 2)
			.layer(SoundEvents.AZALEA_LEAVES_PLACE, 0.5f, 0.8f, 4);
	public static final OmnitrixSfx.Cue SND_POD = OmnitrixSfx.cue("wildvine_pod")
			.layer(SoundEvents.BIG_DRIPLEAF_PLACE, 0.6f, 1.4f)
			.layer(SoundEvents.SNIFFER_EGG_PLOP, 0.5f, 1.2f);
	public static final OmnitrixSfx.Cue SND_BURST = OmnitrixSfx.cue("wildvine_burst")
			.layer(SoundEvents.GENERIC_EXPLODE, 0.45f, 1.4f)
			.layer(SoundEvents.SLIME_SQUISH, 0.7f, 0.6f);
	public static final OmnitrixSfx.Cue SND_SQUEEZE = OmnitrixSfx.cue("wildvine_squeeze")
			.layer(SoundEvents.BAMBOO_BREAK, 0.6f, 0.6f)
			.layer(SoundEvents.CAVE_VINES_STEP, 0.6f, 0.7f);
	public static final OmnitrixSfx.Cue SND_DIG = OmnitrixSfx.cue("wildvine_dig")
			.layer(SoundEvents.ROOTED_DIRT_BREAK, 0.7f, 0.7f)
			.layer(SoundEvents.GRAVEL_BREAK, 0.5f, 0.8f);

	// ---------------------------------------------------------------- server-only move state
	/** A swing line: the hook, the block it is stuck to, the rope, when, its drawn vine. */
	private record Line(Vec3 hook, BlockPos block, double rope, long start, AlienProjectileEntity vine) {
	}

	/** A creature in his coils (Constrict): squeezed every {@link #SQUEEZE_EVERY} ticks till {@code end}. */
	private record Coil(LivingEntity target, long start, long end, AlienProjectileEntity vine) {
	}

	/** Vine Slash running out from {@code from} along {@code dir}: {@code hit} holds the ids it has already cut. */
	private record Slash(Vec3 from, Vec3 dir, long start, Set<Integer> hit) {
	}

	/** Vine Slam: creature {@code target} (by id), lifted from {@code from} over {@code apex} and down to {@code land}, or only hit (a boss). */
	private record Slam(int target, long start, Vec3 from, Vec3 apex, Vec3 land, boolean lift, AlienProjectileEntity vine) {
	}

	/** A move's beat still to come (its pose reaches the moment): run at game time {@code at}, with the wearer as he is by then. */
	private record Cue(long at, Consumer<ServerPlayer> run) {
	}

	private static final Map<UUID, Line> LINES = new ConcurrentHashMap<>();
	private static final Map<UUID, Coil> COILS = new ConcurrentHashMap<>();
	private static final Map<UUID, Slash> SLASHES = new ConcurrentHashMap<>();
	private static final Map<UUID, Slam> SLAMS = new ConcurrentHashMap<>();
	private static final Map<UUID, List<Cue>> CUES = new ConcurrentHashMap<>();
	/** His planted seed mines (each a held projectile, armed {@link #MINE_ARM} ticks after planting). */
	private static final Map<UUID, List<AlienProjectileEntity>> MINE_LIST = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_TICK = new ConcurrentHashMap<>();

	private WildvineKit() {
	}

	public static void register() {
		Aliens.register(new WildvineKit());
		// the seed bomb bursts into a cloud of splinters and spores where it lands
		AlienProjectileEntity.onHit("wildvine_seed", (proj, hit) -> {
			if (proj.level() instanceof ServerLevel l) {
				burstFx(l, hit.getLocation(), (float) SEED_RADIUS);
			}
		});
	}

	public static void clearSessionState() {
		LINES.clear();
		COILS.clear();
		SLASHES.clear();
		SLAMS.clear();
		CUES.clear();
		MINE_LIST.clear();
		LAST_TICK.clear();
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public AlienStats stats() {
		// a tall, light, wiry plant: quick on his feet, long reach, not much armour -- his vines do the work
		return AlienStats.builder().health(30f).speed(1.05f).scale(1.1f).armor(4f, 0f).attack(5f).step(0.6f).jump(0.05f).safeFall(6f).reach(2.0f)
				.build();
	}

	@Override
	protected List<AlienMove> declareMoves() {
		return List.of(
				AlienMove.tap(LASH, AbilitySlot.SLOT_1, LASH_COOLDOWN), AlienMove.shift(SLASH, AbilitySlot.SLOT_1, SLASH_COOLDOWN),
				AlienMove.tap(SEED_BOMB, AbilitySlot.SLOT_2, SEED_BOMB_COOLDOWN), AlienMove.shift(SEED_MINES, AbilitySlot.SLOT_2, SEED_MINES_COOLDOWN),
				AlienMove.tap(SWING, AbilitySlot.SLOT_3, SWING_COOLDOWN), AlienMove.shift(BURROW, AbilitySlot.SLOT_3, BURROW_COOLDOWN),
				AlienMove.tap(CONSTRICT, AbilitySlot.SLOT_4, 200), AlienMove.shift(OVERGROWTH, AbilitySlot.SLOT_4, 800),
				AlienMove.tap(REGROWTH, AbilitySlot.SLOT_5, 400), AlienMove.shift(CAMO, AbilitySlot.SLOT_5, 500),
				AlienMove.tap(SNARE, AbilitySlot.SLOT_6, 260), AlienMove.shift(SLAM, AbilitySlot.SLOT_6, SLAM_COOLDOWN),
				AlienMove.vanilla(ARMS, "Attack", 0), AlienMove.vanilla(CLIMB, "Passive", 0), AlienMove.vanilla(GREENWALK, "Passive", 0),
				AlienMove.vanilla(SUN, "Passive", 0));
	}

	@Override
	public boolean keyActive(Player p, AbilitySlot slot) {
		return switch (slot) {
			case SLOT_3 -> flag(p, F_SWING) || flag(p, F_BURROW);
			case SLOT_5 -> flag(p, F_CAMO);
			default -> false;
		};
	}

	// ---------------------------------------------------------------- the body

	@Override
	public boolean climbs(Player p) {
		// the show: his vines grip any surface -- he climbs a wall he is pressed against like a creeper plant
		return p.horizontalCollision && !flag(p, F_BURROW);
	}

	/** Burrowed he is a low mound: the hitbox and the camera go down to the ground. */
	@Override
	@Nullable
	public EntityDimensions body(Player p, Pose pose) {
		return flag(p, F_BURROW) ? MOUND_BODY : null;
	}

	/**
	 * v0.17.2 (user: "Make wildvine able to walk through nature blocks like wood, leaves and stuff", "make player able to phase through
	 * half blocks like fences and iron bars in this form", "if player shifts in his form they can phase through nature blocks please,
	 * releasing shift stops the phasing"). A block he stands ON stays his floor (so he walks over a canopy and never sinks into the
	 * ground); everything else in the way follows these rules: trees and plants always, Sneak dropping him through them too; in the
	 * burrow also thin blocks, and with Shift held the earth and rock.
	 */
	@Override
	public boolean passesBlock(Player p, BlockState state, BlockGetter level, BlockPos pos, VoxelShape shape) {
		boolean floor = pos.getY() + shape.max(Direction.Axis.Y) <= p.getY() + FLOOR_SLOP;
		if (state.is(PLANTS)) {
			return !floor || p.isShiftKeyDown();
		}
		if (floor || !flag(p, F_BURROW)) {
			return false;
		}
		return thin(state, shape) || p.isShiftKeyDown() && state.is(EARTH);
	}

	/** A block a mound slips round: anything not full and more than ankle high -- but never slabs and stairs, which he walks up. */
	private static boolean thin(BlockState state, VoxelShape shape) {
		Block b = state.getBlock();
		return !(b instanceof SlabBlock) && !(b instanceof StairBlock) && shape.max(Direction.Axis.Y) > 0.5 && !Block.isShapeFullBlock(shape);
	}

	/** Whether his feet or his eyes are inside a tree or plant (client-safe). */
	public static boolean inPlant(Player p) {
		return p.level().getBlockState(p.blockPosition()).is(PLANTS) || eyesInPlant(p);
	}

	private static boolean eyesInPlant(Player p) {
		return p.level().getBlockState(BlockPos.containing(p.getEyePosition())).is(PLANTS);
	}

	@Override
	public boolean clearInBlocks(Player p, net.minecraft.world.level.block.state.BlockState blocking) {
		return flag(p, F_BURROW) || blocking.is(PLANTS);
	}

	// ---------------------------------------------------------------- the seed pods on his back

	/** Seed pods on his back right now, 0..{@link #PODS} (client-safe: {@code WildvineFx.backPods} and the HUD draw these). */
	public static int pods(Player p) {
		return Math.max(0, PODS - (int) synced(p, PODS_SPENT));
	}

	/** How far the next pod has regrown, 0..1 (client-safe). */
	public static float podRegrowth(Player p, float partial) {
		long next = synced(p, POD_NEXT);
		return next <= 0L ? 0f : Math.min(1f, Math.max(0f, 1f - (next - p.level().getGameTime() - partial) / POD_REGROW));
	}

	private void spendPods(ServerPlayer p, int n) {
		OmnitrixState s = Omnitrix.state(p).copy();
		long spent = s.readyAt(key(PODS_SPENT));
		if (spent <= 0L) {
			s.timers.put(key(POD_NEXT), p.level().getGameTime() + POD_REGROW);
		}
		s.timers.put(key(PODS_SPENT), Math.min(PODS, spent + n));
		Omnitrix.save(p, s);
	}

	private void tickPods(ServerPlayer p, long now) {
		long next = timer(p, POD_NEXT);
		if (next <= 0L || now < next) {
			return;
		}
		OmnitrixState s = Omnitrix.state(p).copy();
		long spent = s.readyAt(key(PODS_SPENT)) - 1L;
		if (spent <= 0L) {
			s.timers.remove(key(PODS_SPENT));
			s.timers.remove(key(POD_NEXT));
		} else {
			s.timers.put(key(PODS_SPENT), spent);
			s.timers.put(key(POD_NEXT), now + POD_REGROW);
		}
		Omnitrix.save(p, s);
	}

	// ---------------------------------------------------------------- lifecycle

	@Override
	public void onTransform(ServerPlayer p) {
		ServerLevel l = level(p);
		setTimer(p, PODS_SPENT, 0);
		setTimer(p, POD_NEXT, 0);
		OmnitrixSounds.play(l, p.position(), SND_GROW, 1.0f, 1.0f);
		QuirkFx.ring(l, p.position().add(0, 0.05, 0)).colours(0xC8F080, COLOUR).size(3f).thickness(0.6f).life(14).ground().spawn();
		leaves(l, p.position().add(0, 1, 0), 16, 0.5);
	}

	@Override
	public void onRevert(ServerPlayer p) {
		endLine(p, false);
		if (flag(p, F_BURROW)) {
			surface(p, false);
		}
		endCamo(p);
		endCoil(p);
		endSlam(p);
		SLASHES.remove(p.getUUID());
		CUES.remove(p.getUUID());
		List<AlienProjectileEntity> mines = MINE_LIST.remove(p.getUUID());
		if (mines != null) {
			mines.forEach(Entity::discard);
		}
		for (int f : new int[] { F_SWING, F_BURROW, F_CAMO, F_AIR }) {
			setFlag(p, f, false);
		}
		PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, BURROW_SPEED);
		PowerToggles.clearModifier(p, Attributes.SNEAKING_SPEED, BURROW_SNEAK);
		LAST_TICK.remove(p.getUUID());
	}

	/** Any other move pulls him out of his camouflage. */
	@Override
	protected void onUse(ServerPlayer p, String moveId) {
		if (!moveId.equals(CAMO)) {
			endCamo(p);
		}
	}

	/** {@code run} in {@code ticks} ticks, when the move's pose gets there. Reverting or diving drops it. */
	private static void after(ServerPlayer p, int ticks, Consumer<ServerPlayer> run) {
		CUES.computeIfAbsent(p.getUUID(), k -> new ArrayList<>()).add(new Cue(p.level().getGameTime() + ticks, run));
	}

	private static void tickCues(ServerPlayer p, long now) {
		List<Cue> cues = CUES.get(p.getUUID());
		if (cues == null) {
			return;
		}
		for (Cue c : List.copyOf(cues)) {
			if (now >= c.at()) {
				cues.remove(c);
				c.run().accept(p);
			}
		}
		if (cues.isEmpty()) {
			CUES.remove(p.getUUID());
		}
	}

	// ---------------------------------------------------------------- keys

	@Override
	public void press(ServerPlayer p, AbilitySlot slot, boolean shift) {
		if (flag(p, F_BURROW)) {
			// underground only the burrow's key does anything: it brings him up
			if (slot == AbilitySlot.SLOT_3) {
				surface(p, true);
			}
			return;
		}
		switch (slot) {
			case SLOT_1 -> {
				if (shift) {
					vineSlash(p);
				} else {
					vineLash(p);
				}
			}
			case SLOT_2 -> {
				if (shift) {
					seedMines(p);
				} else {
					seedBomb(p);
				}
			}
			case SLOT_3 -> {
				// either X again lets go of a line (Spidermonkey's / Ladybug's X)
				if (LINES.containsKey(p.getUUID())) {
					endLine(p, true);
				} else if (shift) {
					rootBurrow(p);
				} else {
					vineSwing(p);
				}
			}
			case SLOT_4 -> {
				if (shift) {
					overgrowth(p);
				} else {
					constrict(p);
				}
			}
			case SLOT_5 -> {
				if (shift) {
					camouflage(p);
				} else {
					regrowth(p);
				}
			}
			case SLOT_6 -> {
				if (shift) {
					vineSlam(p);
				} else {
					vineSnare(p);
				}
			}
			default -> {
			}
		}
	}

	@Override
	public void release(ServerPlayer p, AbilitySlot slot, boolean shift, int heldTicks) {
		// the swing runs while X is held; letting go flings him off the end of the vine
		if (slot == AbilitySlot.SLOT_3 && !shift && swinging(p)) {
			endLine(p, true);
		}
	}

	/** Left-click: a stretched vine-arm punch (his reach is the body's {@code reach} stat). Nothing from underground. */
	@Override
	public boolean onAttack(ServerPlayer p, Entity target) {
		if (flag(p, F_BURROW)) {
			return true;
		}
		endCamo(p);
		anim(p, ANIM_PUNCH);
		return false;
	}

	// ---------------------------------------------------------------- R / Shift+R

	/**
	 * R: Vine Lash (user: "make vine lash work similar to ladybugs yoyo R ability please, dont make it pull enemies closer and make it
	 * autotrack slightly please") -- Ladybug's Yo-yo Strike with a vine: whipped out along the look (12 blocks) and straight back, 12 to
	 * the first creature in the way, knocked away. The crosshair just off a creature still finds it ({@link #assist}).
	 */
	public boolean vineLash(ServerPlayer p) {
		if (!use(p, LASH)) {
			return false;
		}
		anim(p, ANIM_LASH);
		ServerLevel l = level(p);
		LivingEntity t = aimed(p, LASH_RANGE);
		Vec3 end;
		if (t != null) {
			end = mid(t);
			if (hurt(p, t, LASH_DAMAGE, false)) {
				shove(t, p.position(), 0.45, 0.15);
			}
			l.sendParticles(ParticleTypes.SWEEP_ATTACK, end.x, end.y, end.z, 1, 0, 0, 0, 0);
			OmnitrixSounds.play(l, end, SND_SNAP, 1.0f, 1.0f);
		} else {
			BlockHitResult b = blockAlong(p, p.getLookAngle(), LASH_RANGE);
			end = b != null ? b.getLocation() : p.getEyePosition().add(p.getLookAngle().scale(LASH_RANGE));
		}
		AlienProjectileEntity s = AlienProjectileEntity.of(p, "wildvine_lash");
		s.moveTo(end.x, end.y, end.z, 0f, 0f);
		s.colour(t == null ? 0 : t.getId() + 1).size((float) Math.max(0.6, p.position().distanceTo(end) / 3.0)).held(true).speed(0)
				.life(LASH_OUT + LASH_BACK + 1).damage(0f).spawn();
		OmnitrixSounds.play(l, p.position(), SND_LASH, 0.9f, 1.2f);
		return true;
	}

	private static Vec3 mid(Entity e) {
		return e.position().add(0, e.getBbHeight() * 0.55, 0);
	}

	/** The creature under the crosshair he may hit, else the one {@link #assist} finds. */
	@Nullable
	private static LivingEntity aimed(ServerPlayer p, double range) {
		LivingEntity t = lookTarget(p, range);
		return t != null && HeroTargets.canHarm(p, t) ? t : assist(p, range);
	}

	/**
	 * Ladybug's slight auto-aim, when the crosshair itself is on nothing: the enemy nearest the aim within about 15 degrees of it, or
	 * within a block of the aim line, in reach and in sight. Never a squadmate.
	 */
	@Nullable
	static LivingEntity assist(ServerPlayer p, double range) {
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		LivingEntity best = null;
		double bestScore = Double.MAX_VALUE;
		for (LivingEntity e : around(p, eye, range)) {
			if (!HeroTargets.canHarm(p, e)) {
				continue;
			}
			Vec3 to = mid(e).subtract(eye);
			double along = to.dot(look);
			if (along <= 0.5 || along > range) {
				continue;
			}
			double off = to.subtract(look.scale(along)).length();
			if (along / to.length() < LASH_ASSIST_COS && off > LASH_ASSIST_RADIUS || !sees(p, e)) {
				continue;
			}
			double score = off / along;
			if (score < bestScore) {
				bestScore = score;
				best = e;
			}
		}
		return best;
	}

	/** Nothing solid between his eyes and {@code e} (a tree or a bush does not stop a vine). */
	private static boolean sees(ServerPlayer p, LivingEntity e) {
		BlockHitResult hit = p.level().clip(new ClipContext(p.getEyePosition(), mid(e), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
		return hit.getType() == HitResult.Type.MISS || p.level().getBlockState(hit.getBlockPos()).is(PLANTS);
	}

	/**
	 * Shift+R: Vine Slash (user: "make it so that the player spawns a bunch of tendril in a 20 block cone in front of them and attacks
	 * enemies in that range to 18 damage") -- he sweeps both arms across and a fan of tendrils tears out along the ground, 20 blocks
	 * ahead in a 70-degree cone, cutting everything they reach for 18 as they get there.
	 */
	public boolean vineSlash(ServerPlayer p) {
		if (!use(p, SLASH)) {
			return false;
		}
		anim(p, ANIM_SLASH);
		OmnitrixSounds.play(level(p), p.position(), SND_LASH, 1.0f, 0.7f);
		after(p, SLASH_WINDUP, this::startSlash);
		return true;
	}

	private void startSlash(ServerPlayer p) {
		ServerLevel l = level(p);
		Vec3 c = p.position();
		Vec3 dir = flat(p);
		SLASHES.put(p.getUUID(), new Slash(c, dir, p.level().getGameTime(), new HashSet<>()));
		AlienProjectileEntity g = AlienProjectileEntity.of(p, "wildvine_slash");
		g.moveTo(c.x, c.y, c.z, p.getYRot(), 0f);
		g.size((float) SLASH_RANGE).held(true).speed(0).life(SLASH_LIFE).damage(0f).spawn();
		dirt(l, c.add(dir.scale(1.5)), 20);
		OmnitrixSounds.play(l, c, SND_GROW, 1.1f, 1.0f);
		OmnitrixSounds.play(l, c, SND_SNAP, 1.0f, 0.7f);
	}

	/** Whether {@code e} stands in the slash's cone: within 20 blocks across the ground, 35 degrees off the heading, not far above or below. */
	public static boolean inCone(Vec3 from, Vec3 dir, LivingEntity e) {
		Vec3 to = e.position().subtract(from);
		double flatLen = Math.sqrt(to.x * to.x + to.z * to.z);
		if (flatLen > SLASH_RANGE || Math.abs(to.y) > SLASH_HEIGHT) {
			return false;
		}
		return flatLen < 1.0 || (to.x * dir.x + to.z * dir.z) / flatLen >= SLASH_HALF_COS;
	}

	private void tickSlash(ServerPlayer p, ServerLevel l, long now) {
		Slash s = SLASHES.get(p.getUUID());
		if (s == null) {
			return;
		}
		double reach = (now - s.start() + 1) * SLASH_SPEED;
		for (LivingEntity e : around(p, s.from(), Math.min(reach, SLASH_RANGE) + 1.0)) {
			if (s.hit().contains(e.getId()) || !inCone(s.from(), s.dir(), e) || e.position().distanceTo(s.from()) > reach || !sees(p, e)) {
				continue;
			}
			s.hit().add(e.getId());
			hurt(p, e, SLASH_DAMAGE, false);
			AlienProjectileEntity st = AlienProjectileEntity.of(p, "wildvine_stab");
			st.moveTo(e.getX(), e.getY(), e.getZ(), 0f, 0f);
			st.colour(e.getId() + 1).size(Math.max(0.8f, e.getBbHeight())).held(true).speed(0).life(12).damage(0f).spawn();
			leaves(l, mid(e), 8, 0.3);
			OmnitrixSounds.play(l, e.position(), SND_SNAP, 0.9f, 1.1f);
		}
		if (reach > SLASH_RANGE + 2.0) {
			SLASHES.remove(p.getUUID());
		}
	}

	// ---------------------------------------------------------------- G / Shift+G

	/**
	 * G: he plucks a seed pod off his back and throws it -- it arcs and bursts where it lands: up to 18 within 3 blocks. Each throw takes
	 * a pod from the six on his back; one regrows every 3 s.
	 */
	public boolean seedBomb(ServerPlayer p) {
		if (pods(p) <= 0) {
			say(p, "message.projecthero.omnitrix.wildvine.no_pods");
			return false;
		}
		if (!use(p, SEED_BOMB)) {
			return false;
		}
		spendPods(p, 1);
		anim(p, ANIM_THROW);
		after(p, THROW_AT, q -> {
			AlienProjectileEntity.of(q, "wildvine_seed").colour(0x07030F).size(0.42f).damage(SEED_DIRECT).blast((float) SEED_RADIUS, SEED_DAMAGE)
					.gravity(0.05).speed(1.35).life(80).spawn();
			OmnitrixSounds.play(level(q), q.position(), SND_POD, 1.0f, 1.0f);
		});
		return true;
	}

	/**
	 * Shift+G: he plants three pods off his back in the ground around him (as many as he has, fewer than three). Each arms after 1 s and
	 * bursts when a hostile creature comes within 1.6 blocks: 16 within 2.5. They last 20 s. v0.17.2 (user: "Make seed mines able to
	 * manually explode by reactivating the ability"): Shift+G again while any are out blows them all up at once, armed or not.
	 */
	public boolean seedMines(ServerPlayer p) {
		if (mineCount(p) > 0) {
			detonateMines(p);
			return true;
		}
		int have = pods(p);
		if (have <= 0) {
			say(p, "message.projecthero.omnitrix.wildvine.no_pods");
			return false;
		}
		if (!use(p, SEED_MINES)) {
			return false;
		}
		int n = Math.min(MINES, have);
		spendPods(p, n);
		anim(p, ANIM_PLANT);
		after(p, PLANT_AT, q -> plantMines(q, n));
		return true;
	}

	private void plantMines(ServerPlayer p, int n) {
		ServerLevel l = level(p);
		List<AlienProjectileEntity> mines = new ArrayList<>();
		Vec3 fwd = flat(p);
		for (int i = 0; i < n; i++) {
			double a = Math.toRadians(-50 + 50 * i);
			Vec3 dir = new Vec3(fwd.x * Math.cos(a) - fwd.z * Math.sin(a), 0, fwd.x * Math.sin(a) + fwd.z * Math.cos(a));
			Vec3 at = ground(l, p.position().add(dir.scale(2.5)));
			if (at == null) {
				continue;
			}
			AlienProjectileEntity m = AlienProjectileEntity.of(p, "wildvine_mine");
			m.moveTo(at.x, at.y, at.z, p.getYRot() + i * 47f, 0f);
			m.colour(0x07030F).size(0.6f).held(true).speed(0).life(MINE_LIFE).damage(0f).spawn();
			mines.add(m);
			BlockState under = l.getBlockState(BlockPos.containing(at).below());
			if (!under.isAir()) {
				l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, under), at.x, at.y + 0.1, at.z, 6, 0.15, 0.05, 0.15, 0.05);
			}
		}
		MINE_LIST.put(p.getUUID(), mines);
		OmnitrixSounds.play(l, p.position(), SND_DIG, 0.9f, 1.2f);
	}

	/** Shift+G with mines out: every one of them bursts now. */
	private void detonateMines(ServerPlayer p) {
		List<AlienProjectileEntity> mines = MINE_LIST.remove(p.getUUID());
		if (mines == null) {
			return;
		}
		anim(p, ANIM_DETONATE);
		ServerLevel l = level(p);
		for (AlienProjectileEntity m : mines) {
			if (m.isAlive() && m.level() == l) {
				detonate(p, l, m.position().add(0, 0.3, 0), MINE_RADIUS, MINE_DAMAGE);
			}
			m.discard();
		}
	}

	/** The top of the ground at {@code at} (searching 3 blocks up and down), or null over a drop. */
	@Nullable
	private static Vec3 ground(ServerLevel l, Vec3 at) {
		BlockHitResult hit = l.clip(new ClipContext(at.add(0, 1.5, 0), at.add(0, -3.0, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
				CollisionContext.empty()));
		return hit.getType() == HitResult.Type.BLOCK && hit.getDirection() == Direction.UP ? hit.getLocation() : null;
	}

	// ---------------------------------------------------------------- X / Shift+X

	/**
	 * X (held): Vine Swing (user: "Make vine swing work similar to ladybugs yoyo swing ability please but with widlvines vfx and stuff")
	 * -- her Yo-yo Swing: a vine from his hand to the block under the crosshair (45 blocks) and he swings on it, for as long as X is held
	 * (Jump reels in, Sneak pays out). Letting go carries the arc's speed on, and starts its 0.3 s cooldown.
	 */
	public boolean vineSwing(ServerPlayer p) {
		BlockHitResult hit = blockAlong(p, p.getLookAngle(), SWING_RANGE);
		if (hit == null) {
			say(p, "message.projecthero.omnitrix.wildvine.no_anchor");
			return false;
		}
		if (!use(p, SWING)) {
			return false;
		}
		anim(p, ANIM_SWING);
		Vec3 at = hit.getLocation();
		double rope = Math.max(3.0, Math.min(SWING_RANGE + 2.0, at.distanceTo(p.getEyePosition())));
		startLine(p, at, hit.getBlockPos(), rope);
		leaves(level(p), at, 6, 0.1);
		OmnitrixSounds.play(level(p), p.position(), SND_LASH, 1.0f, 0.9f);
		return true;
	}

	/**
	 * Shift+X: Root Burrow -- he sinks into the ground and moves under it as a mound for as long as he likes (faster, untouchable,
	 * regrowing 2 health a second, creatures lose track of him), slipping through fences, bars and anything else not a full block; Shift
	 * held, he phases through earth and rock too. X or Shift+X brings him up: 14 within 3 blocks, thrown up. Its 15 s cooldown runs from
	 * then. Needs ground under him.
	 */
	public boolean rootBurrow(ServerPlayer p) {
		if (!p.onGround() || p.isInWater()) {
			say(p, "message.projecthero.omnitrix.wildvine.no_ground");
			return false;
		}
		if (!use(p, BURROW)) {
			return false;
		}
		cooldown(p, BURROW, 0); // it runs from when he comes back up
		endLine(p, false);
		CUES.remove(p.getUUID());
		anim(p, ANIM_DIVE);
		ServerLevel l = level(p);
		setFlag(p, F_BURROW, true);
		setTimer(p, BURROW_AT, p.level().getGameTime());
		PowerToggles.modifier(p, Attributes.MOVEMENT_SPEED, BURROW_SPEED, 0.3, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		// Shift is his phase key here, not a crawl
		PowerToggles.modifier(p, Attributes.SNEAKING_SPEED, BURROW_SNEAK, 1.0, AttributeModifier.Operation.ADD_VALUE);
		// (no invisibility: it would hide the mound he moves as -- the creatures hunting him lose track instead, every 0.5 s)
		for (Mob m : l.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(32))) {
			AlienStatus.loseTrack(m, p);
		}
		dirt(l, p.position(), 24);
		OmnitrixSounds.play(l, p.position(), SND_DIG, 1.1f, 0.8f);
		return true;
	}

	/** When he went under (game time), client-safe; 0 if he is not. */
	public static long burrowAt(Player p) {
		return synced(p, BURROW_AT);
	}

	/** Up out of the ground: the burst ({@code burst}: 14 within 3 blocks, thrown up), the flag and the speed gone. */
	private void surface(ServerPlayer p, boolean burst) {
		setFlag(p, F_BURROW, false);
		setTimer(p, BURROW_AT, 0);
		PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, BURROW_SPEED);
		PowerToggles.clearModifier(p, Attributes.SNEAKING_SPEED, BURROW_SNEAK);
		// from here on the blocks he was phasing are solid again: find him room to stand
		rise(p, EntityDimensions.scalable(0.6f, 1.8f).scale(p.getScale()).makeBoundingBox(p.position()));
		if (!burst) {
			return;
		}
		cooldown(p, BURROW, -1);
		ServerLevel l = level(p);
		anim(p, ANIM_EMERGE);
		Vec3 c = p.position();
		for (LivingEntity e : around(p, c, EMERGE_RADIUS)) {
			hurt(p, e, EMERGE_DAMAGE, false);
			shove(e, c, 0.4, 0.8);
		}
		Vec3 v = p.getDeltaMovement();
		p.setDeltaMovement(v.x, 0.55, v.z);
		p.hurtMarked = true;
		dirt(l, c, 36);
		leaves(l, c.add(0, 0.6, 0), 14, 0.8);
		QuirkFx.ring(l, c.add(0, 0.05, 0)).colours(0x8A6A40, COLOUR).size(4f).thickness(0.7f).life(12).ground().spawn();
		QuirkCamera.shakeAround(l, c, 8, 0.3f, 8);
		OmnitrixSounds.play(l, c, SND_DIG, 1.2f, 0.6f);
		OmnitrixSounds.play(l, c, SND_GROW, 1.0f, 1.2f);
	}

	/** Straight up to the first spot {@code box} fits (a phase that ends inside a hill comes out on top of it), if any is near. */
	private static void rise(ServerPlayer p, AABB box) {
		ServerLevel l = level(p);
		AABB b = box.deflate(1.0e-3);
		for (int dy = 0; dy <= RISE_MAX; dy++) {
			if (l.noCollision(p, b.move(0, dy, 0))) {
				if (dy > 0) {
					dirt(l, p.position(), 12);
					p.teleportTo(p.getX(), p.getY() + dy, p.getZ());
					dirt(l, p.position(), 12);
				}
				return;
			}
		}
	}

	// ---------------------------------------------------------------- Z / Shift+Z

	/**
	 * Z: Constrict -- his vines shoot out and coil round the creature he aims at (12 blocks, Vine Lash's slight auto-aim): rooted 3 s and squeezed every
	 * 0.5 s for 4 (24 in all). A boss is held only briefly but still squeezed.
	 */
	public boolean constrict(ServerPlayer p) {
		LivingEntity t = aimed(p, CONSTRICT_RANGE);
		if (t == null) {
			say(p, "message.projecthero.omnitrix.wildvine.no_target");
			return false;
		}
		if (!use(p, CONSTRICT)) {
			return false;
		}
		anim(p, ANIM_WRAP);
		endCoil(p);
		long now = p.level().getGameTime();
		AlienStatus.root(p, t, CONSTRICT_TICKS, 20);
		wrap(p, t, CONSTRICT_TICKS);
		COILS.put(p.getUUID(), new Coil(t, now, now + CONSTRICT_TICKS, vine(p, null, t, CONSTRICT_TICKS)));
		OmnitrixSounds.play(level(p), t.position(), SND_LASH, 1.0f, 0.7f);
		return true;
	}

	private static void endCoil(ServerPlayer p) {
		Coil c = COILS.remove(p.getUUID());
		if (c != null) {
			c.vine().discard();
		}
	}

	/**
	 * Shift+Z (ultimate): Overgrowth -- he drives his arms into the ground and a thicket of vines erupts all round him: everything within
	 * 7 blocks takes 22, is thrown up and is wrapped (rooted 3 s).
	 */
	public boolean overgrowth(ServerPlayer p) {
		if (!use(p, OVERGROWTH)) {
			return false;
		}
		anim(p, ANIM_OVERGROWTH);
		OmnitrixSounds.play(level(p), p.position(), SND_GROW, 0.8f, 1.3f);
		after(p, OVERGROWTH_AT, this::overgrowthLands);
		return true;
	}

	private void overgrowthLands(ServerPlayer p) {
		ServerLevel l = level(p);
		Vec3 c = p.position();
		for (LivingEntity e : around(p, c, OVERGROWTH_RADIUS)) {
			hurt(p, e, OVERGROWTH_DAMAGE, false);
			shove(e, c, 0.3, 0.6);
			AlienStatus.root(p, e, OVERGROWTH_ROOT, 30);
			wrap(p, e, OVERGROWTH_ROOT);
		}
		QuirkFx.ring(l, c.add(0, 0.05, 0)).colours(0xC8F080, COLOUR).size((float) OVERGROWTH_RADIUS + 1f).thickness(1.0f).life(18).ground().spawn();
		QuirkFx.ring(l, c.add(0, 0.05, 0)).colours(0x8A6A40, 0x3F7A2A).size((float) OVERGROWTH_RADIUS * 0.6f).thickness(0.8f).life(14).ground().spawn();
		thicket(p, c, (float) OVERGROWTH_RADIUS, 34);
		for (int i = 0; i < 40; i++) {
			double a = i * Math.PI * 2 / 40, r = 1.5 + p.getRandom().nextDouble() * (OVERGROWTH_RADIUS - 1.5);
			leaves(l, c.add(Math.cos(a) * r, 0.2, Math.sin(a) * r), 2, 0.2);
		}
		dirt(l, c, 50);
		QuirkCamera.shakeAround(l, c, 14, 0.45f, 12);
		OmnitrixSounds.play(l, c, SND_GROW, 1.4f, 0.6f);
		OmnitrixSounds.play(l, c, SND_DIG, 1.2f, 0.5f);
	}

	/** Tendrils rearing out of the ground in a {@code radius}-block circle for {@code life} ticks (colour = life; WildvineFx.thicket). */
	private static void thicket(ServerPlayer p, Vec3 c, float radius, int life) {
		AlienProjectileEntity g = AlienProjectileEntity.of(p, "wildvine_thicket");
		g.moveTo(c.x, c.y, c.z, 0f, 0f);
		g.colour(life).size(radius).held(true).speed(0).life(life).damage(0f).spawn();
	}

	// ---------------------------------------------------------------- V / Shift+V

	/**
	 * V: Regrowth -- the show's Florauna regrow what they lose: 10 health back at once, and slowness, poison, wither and burning shed
	 * with the old growth.
	 */
	public boolean regrowth(ServerPlayer p) {
		if (!use(p, REGROWTH)) {
			return false;
		}
		anim(p, ANIM_REGROW);
		p.heal(REGROW_HEAL);
		for (var eff : List.of(MobEffects.MOVEMENT_SLOWDOWN, MobEffects.POISON, MobEffects.WITHER, MobEffects.WEAKNESS)) {
			p.removeEffect(eff);
		}
		p.clearFire();
		p.setTicksFrozen(0);
		ServerLevel l = level(p);
		AlienProjectileEntity w = AlienProjectileEntity.of(p, "wildvine_regrow");
		w.moveTo(p.getX(), p.getY(), p.getZ(), 0f, 0f);
		w.colour(p.getId() + 1).size(1.2f).held(true).speed(0).life(20).damage(0f).spawn();
		l.sendParticles(ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + 1.0, p.getZ(), 20, 0.4, 0.7, 0.4, 0.05);
		leaves(l, p.position().add(0, 1.0, 0), 12, 0.5);
		OmnitrixSounds.play(l, p.position(), SND_GROW, 0.9f, 1.4f);
		return true;
	}

	/** Shift+V: Thicket Camouflage -- he blends into the green for 6 s: invisible, and creatures hunting him lose track (any move ends it). */
	public boolean camouflage(ServerPlayer p) {
		if (!use(p, CAMO)) {
			return false;
		}
		anim(p, ANIM_CAMO);
		ServerLevel l = level(p);
		setFlag(p, F_CAMO, true);
		setTimer(p, CAMO_END, p.level().getGameTime() + CAMO_TICKS);
		for (Mob m : l.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(24))) {
			AlienStatus.loseTrack(m, p);
		}
		leaves(l, p.position().add(0, 1.0, 0), 20, 0.5);
		OmnitrixSounds.play(l, p.position(), SND_GROW, 0.7f, 1.6f);
		// he folds himself up first, then the green closes over him
		after(p, CAMO_AT, q -> {
			if (flag(q, F_CAMO)) {
				q.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, CAMO_TICKS - CAMO_AT, 0, false, false, true));
				leaves(level(q), q.position().add(0, 1.0, 0), 16, 0.4);
			}
		});
		return true;
	}

	private void endCamo(ServerPlayer p) {
		if (!flag(p, F_CAMO)) {
			return;
		}
		setFlag(p, F_CAMO, false);
		setTimer(p, CAMO_END, 0);
		p.removeEffect(MobEffects.INVISIBILITY);
	}

	// ---------------------------------------------------------------- C / Shift+C

	/** C: Vine Snare -- he slams his palms down and vines whip out of the ground all round him: everything within 5 blocks: 8, and rooted 2 s. */
	public boolean vineSnare(ServerPlayer p) {
		if (!use(p, SNARE)) {
			return false;
		}
		anim(p, ANIM_SNARE);
		after(p, SNARE_AT, q -> {
			ServerLevel l = level(q);
			Vec3 c = q.position();
			for (LivingEntity e : around(q, c, SNARE_RADIUS)) {
				hurt(q, e, SNARE_DAMAGE, false);
				AlienStatus.root(q, e, SNARE_ROOT, 20);
				wrap(q, e, SNARE_ROOT);
				leaves(l, e.position().add(0, 0.4, 0), 6, 0.3);
			}
			QuirkFx.ring(l, c.add(0, 0.05, 0)).colours(0xC8F080, COLOUR).size((float) SNARE_RADIUS + 1f).thickness(0.7f).life(14).ground().spawn();
			thicket(q, c, (float) SNARE_RADIUS, 24);
			dirt(l, c, 20);
			OmnitrixSounds.play(l, c, SND_GROW, 1.0f, 1.0f);
		});
		return true;
	}

	/**
	 * Shift+C: Vine Slam -- a vine snatches the creature under the crosshair (16 blocks, Vine Lash's slight auto-aim), hoists it overhead
	 * in one long heave and smashes it into the ground in front of him: 20, and 8 to everything within 3 blocks of the impact. A boss is
	 * too big to lift: the same blow lands where it stands. Nothing to grab, no cooldown.
	 */
	public boolean vineSlam(ServerPlayer p) {
		LivingEntity t = aimed(p, SLAM_RANGE);
		if (t == null) {
			say(p, "message.projecthero.omnitrix.wildvine.no_target");
			return false;
		}
		if (!use(p, SLAM)) {
			return false;
		}
		anim(p, ANIM_SLAM);
		endSlam(p);
		Vec3 fwd = flat(p);
		Vec3 apex = p.position().add(0, SLAM_APEX, 0).add(fwd.scale(0.6));
		Vec3 ahead = p.position().add(fwd.scale(SLAM_AHEAD));
		Vec3 land = ground(level(p), ahead);
		SLAMS.put(p.getUUID(), new Slam(t.getId(), p.level().getGameTime(), t.position(), apex, land == null ? ahead : land, !AlienStatus.isBoss(t),
				vine(p, null, t, SLAM_LIFT + SLAM_FALL + SLAM_SETTLE + 4)));
		OmnitrixSounds.play(level(p), t.position(), SND_LASH, 1.0f, 0.7f);
		return true;
	}

	private static void endSlam(ServerPlayer p) {
		Slam s = SLAMS.remove(p.getUUID());
		if (s != null) {
			s.vine().discard();
		}
	}

	private static double ease(double k) {
		return k * k * (3.0 - 2.0 * k);
	}

	private void tickSlam(ServerPlayer p, ServerLevel l, long now) {
		Slam s = SLAMS.get(p.getUUID());
		if (s == null) {
			return;
		}
		int age = (int) (now - s.start());
		LivingEntity t = l.getEntity(s.target()) instanceof LivingEntity e && e.isAlive() ? e : null;
		if (t == null) {
			endSlam(p);
			return;
		}
		if (age >= SLAM_LIFT + SLAM_FALL + SLAM_SETTLE) {
			endSlam(p);
			Vec3 at = t.position();
			hurt(p, t, SLAM_DAMAGE, false);
			for (LivingEntity e : around(p, at, SLAM_RADIUS)) {
				if (e != t) {
					hurt(p, e, SLAM_SPLASH, false);
					shove(e, at, 0.5, 0.35);
				}
			}
			if (s.lift()) {
				AlienStatus.root(p, t, 20, 20);
			}
			t.resetFallDistance();
			dirt(l, at, 40);
			leaves(l, at.add(0, 0.5, 0), 12, 0.6);
			QuirkFx.ring(l, at.add(0, 0.05, 0)).colours(0xC8F080, 0x8A6A40).size(4.5f).thickness(0.8f).life(12).ground().spawn();
			QuirkCamera.shakeAround(l, at, 10, 0.4f, 9);
			OmnitrixSounds.play(l, at, SND_BURST, 1.1f, 0.7f);
			OmnitrixSounds.play(l, at, SND_DIG, 1.2f, 0.6f);
			return;
		}
		if (!s.lift() || age >= SLAM_LIFT + SLAM_FALL) {
			return;
		}
		// up in one long heave, then down hard
		Vec3 to = age < SLAM_LIFT ? s.from().lerp(s.apex(), ease((age + 1) / (double) SLAM_LIFT))
				: s.apex().lerp(s.land(), Math.pow((age + 1 - SLAM_LIFT) / (double) SLAM_FALL, 2));
		Vec3 d = to.subtract(t.position());
		if (t instanceof ServerPlayer) {
			t.setDeltaMovement(d); // a player's own client moves them
			t.hurtMarked = true;
		} else {
			t.move(MoverType.SELF, d);
			t.setDeltaMovement(Vec3.ZERO);
			if (t instanceof Mob m) {
				m.getNavigation().stop();
			}
		}
		t.resetFallDistance();
	}

	// ---------------------------------------------------------------- tick

	@Override
	public void tick(ServerPlayer p, OmnitrixState s, long now) {
		Long last = LAST_TICK.put(p.getUUID(), now);
		if (last != null && last == now) {
			return;
		}
		ServerLevel l = level(p);
		tickCues(p, now);
		tickPods(p, now);
		tickLine(p, now);
		tickCoil(p, l, now);
		tickMines(p, l, now);
		tickSlash(p, l, now);
		tickSlam(p, l, now);
		if (flag(p, F_BURROW)) {
			if (p.isInWater()) {
				surface(p, true);
			} else {
				// v0.17.2 (user: "releasing shift stops the phasing"): Shift let go with rock all round him, he comes up on top of it
				if (!p.isShiftKeyDown() && !l.noCollision(p, p.getBoundingBox().deflate(1.0e-3))) {
					rise(p, p.getBoundingBox());
				}
				if (now % 10 == 0) {
					p.heal(1.0f); // regrowing: 2 health a second underground
				}
				if (now % 2 == 0) {
					dirt(l, p.position(), 3);
				}
				if (now % 10 == 0) {
					for (Mob m : l.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(24))) {
						AlienStatus.loseTrack(m, p); // nothing to see but a moving mound
					}
				}
				if (now % 8 == 0) {
					OmnitrixSounds.play(l, p.position(), SND_DIG, 0.4f, 0.9f);
				}
			}
		}
		long camo = timer(p, CAMO_END);
		if (camo > 0 && now >= camo) {
			endCamo(p);
		}
		if (flag(p, F_AIR) && !LINES.containsKey(p.getUUID()) && now - timer(p, AIR_AT) > 4 && (p.onGround() || p.isInWater())) {
			setFlag(p, F_AIR, false);
		}
		// Photosynthesis: in daylight under open sky (or standing in greenery) he slowly heals
		if (now % 60 == 0 && p.getHealth() < p.getMaxHealth() && sunlit(p, l)) {
			p.heal(1.0f);
			l.sendParticles(ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + 1.2, p.getZ(), 2, 0.3, 0.4, 0.3, 0);
		}
	}

	/** In daylight under open sky, or standing on grass / moss / leaves. */
	public static boolean sunlit(ServerPlayer p, ServerLevel l) {
		BlockState under = l.getBlockState(p.blockPosition().below());
		boolean green = under.is(Blocks.GRASS_BLOCK) || under.is(Blocks.MOSS_BLOCK) || under.is(net.minecraft.tags.BlockTags.LEAVES);
		return green || l.isDay() && !l.isRaining() && l.canSeeSky(BlockPos.containing(p.getEyePosition()));
	}

	private void tickCoil(ServerPlayer p, ServerLevel l, long now) {
		Coil c = COILS.get(p.getUUID());
		if (c == null) {
			return;
		}
		LivingEntity t = c.target();
		if (now >= c.end() || !t.isAlive() || t.level() != p.level() || t.distanceToSqr(p) > (CONSTRICT_RANGE + 6) * (CONSTRICT_RANGE + 6)
				|| t.isOnFire()) {
			// a burning foe burns through his vines (fire weakness)
			endCoil(p);
			leaves(l, t.position().add(0, t.getBbHeight() * 0.5, 0), 8, 0.4);
			return;
		}
		if ((now - c.start()) % SQUEEZE_EVERY == SQUEEZE_EVERY - 1) {
			if (hurt(p, t, SQUEEZE_DAMAGE, false)) {
				OmnitrixSounds.play(l, t.position(), SND_SQUEEZE, 0.8f, 0.9f + (now - c.start()) * 0.005f);
				leaves(l, t.position().add(0, t.getBbHeight() * 0.5, 0), 4, 0.3);
			}
		}
	}

	private void tickMines(ServerPlayer p, ServerLevel l, long now) {
		List<AlienProjectileEntity> mines = MINE_LIST.get(p.getUUID());
		if (mines == null) {
			return;
		}
		mines.removeIf(m -> !m.isAlive() || m.level() != p.level());
		for (AlienProjectileEntity m : new ArrayList<>(mines)) {
			if (m.tickCount < MINE_ARM) {
				continue;
			}
			if (m.tickCount % 20 == 0) {
				l.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, m.getX(), m.getY() + 0.3, m.getZ(), 1, 0.1, 0.1, 0.1, 0);
			}
			Vec3 at = m.position().add(0, 0.3, 0);
			boolean tripped = false;
			for (LivingEntity e : around(p, at, MINE_TRIGGER)) {
				if (e instanceof Enemy || e instanceof Mob mob && mob.getTarget() == p || e instanceof Player) {
					tripped = true;
					break;
				}
			}
			if (tripped) {
				detonate(p, l, at, MINE_RADIUS, MINE_DAMAGE);
				m.discard();
				mines.remove(m);
			}
		}
		if (mines.isEmpty()) {
			MINE_LIST.remove(p.getUUID());
		}
	}

	/** A seed pod bursting at {@code at}: up to {@code damage} within {@code radius} (half at the edge), knocked back. */
	private static void detonate(ServerPlayer p, ServerLevel l, Vec3 at, double radius, float damage) {
		for (LivingEntity e : around(p, at, radius)) {
			double d = Math.sqrt(e.distanceToSqr(at));
			hurt(p, e, (float) (damage * (1.0 - 0.5 * Math.min(1.0, d / radius))), false);
			shove(e, at, 0.6, 0.35);
		}
		burstFx(l, at, (float) radius);
	}

	private static void burstFx(ServerLevel l, Vec3 at, float radius) {
		l.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.2, at.z, 1, 0, 0, 0, 0);
		l.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, at.x, at.y + 0.4, at.z, 30, radius * 0.4, 0.4, radius * 0.4, 0.02);
		l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OAK_LEAVES.defaultBlockState()), at.x, at.y + 0.4, at.z, 24, radius * 0.3,
				0.3, radius * 0.3, 0.15);
		l.sendParticles(ParticleTypes.SMOKE, at.x, at.y + 0.3, at.z, 12, 0.4, 0.3, 0.4, 0.03);
		OmnitrixSounds.play(l, at, SND_BURST, 1.0f, 1.0f);
	}

	// ---------------------------------------------------------------- the line (Ladybug's swing)

	/** Whether {@code p} hangs on a swing vine (gametests). */
	public static boolean swinging(ServerPlayer p) {
		return LINES.containsKey(p.getUUID());
	}

	private static long synced(Player p, String name) {
		return Omnitrix.state(p).readyAt(ID + "." + name);
	}

	/** The vine he is on, client-safe: 0 none, 1 swing. */
	public static int lineMode(Player p) {
		return (int) synced(p, LINE);
	}

	/** When the current vine was cast (game time), client-safe -- a new value is a new vine. */
	public static long lineAt(Player p) {
		return synced(p, LINE_AT);
	}

	/** Where the current vine is stuck, client-safe; null without one. */
	@Nullable
	public static Vec3 hook(Player p) {
		OmnitrixState s = Omnitrix.state(p);
		Long x = s.timers.get(ID + "." + HOOK_X), y = s.timers.get(ID + "." + HOOK_Y), z = s.timers.get(ID + "." + HOOK_Z);
		return x == null || y == null || z == null ? null : new Vec3(x / 1000.0, y / 1000.0, z / 1000.0);
	}

	/** The swing vine's rope length when cast, client-safe. */
	public static double rope(Player p) {
		return synced(p, ROPE) / 1000.0;
	}

	private void startLine(ServerPlayer p, Vec3 at, BlockPos block, double rope) {
		endLine(p, false);
		long now = p.level().getGameTime();
		// (it hangs as long as he does: an hour is "for good" for a held vine)
		AlienProjectileEntity v = vine(p, at, null, 72000);
		LINES.put(p.getUUID(), new Line(at, block.immutable(), rope, now, v));
		OmnitrixState n = Omnitrix.state(p).copy();
		n.timers.put(key(LINE), 1L);
		n.timers.put(key(LINE_AT), now);
		n.timers.put(key(HOOK_X), Math.round(at.x * 1000.0));
		n.timers.put(key(HOOK_Y), Math.round(at.y * 1000.0));
		n.timers.put(key(HOOK_Z), Math.round(at.z * 1000.0));
		n.timers.put(key(ROPE), Math.round(rope * 1000.0));
		n.timers.put(key(AIR_AT), now);
		n.kitFlags |= F_SWING | F_AIR;
		Omnitrix.save(p, n);
		floatTolerance(p, true);
		p.resetFallDistance();
	}

	private void tickLine(ServerPlayer p, long now) {
		Line l = LINES.get(p.getUUID());
		if (l == null) {
			return;
		}
		if (!l.vine().isAlive() || l.vine().level() != p.level()) {
			endLine(p, false);
			return;
		}
		long age = now - l.start();
		// (the block's own shape, whoever is asking: he can hang off a tree he could walk through)
		if (age % 5 == 0 && p.level().getBlockState(l.block()).getCollisionShape(p.level(), l.block()).isEmpty()) {
			endLine(p, true);
			return;
		}
		if (l.hook().distanceTo(p.getEyePosition()) > l.rope() + SWING_SLACK_BREAK) {
			endLine(p, true);
			return;
		}
		p.resetFallDistance();
		floatTolerance(p, true);
	}

	/** Lets go of the vine (the fling is his client's: it carries the arc's speed on). */
	public void endLine(ServerPlayer p, boolean sound) {
		Line l = LINES.remove(p.getUUID());
		if (l == null) {
			return;
		}
		l.vine().discard();
		floatTolerance(p, false);
		long now = p.level().getGameTime();
		OmnitrixState n = Omnitrix.state(p).copy();
		for (String k : LINE_KEYS) {
			n.timers.remove(key(k));
		}
		n.kitFlags &= ~F_SWING;
		n.kitFlags |= F_AIR; // no fall damage till he lands
		n.timers.put(key(AIR_AT), now);
		n.timers.put(key(SWING), now + SWING_COOLDOWN); // Ladybug's: the wait starts when she lets go
		n.animId = ANIM_RELEASE;
		n.animStart = now;
		Omnitrix.save(p, n);
		p.resetFallDistance();
		if (sound) {
			p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.CAVE_VINES_BREAK, SoundSource.PLAYERS, 0.6f, 1.2f);
		}
	}

	/** Vanilla kicks a player airborne with no downward motion ("flying is not enabled") -- a swing looks just like that (Spidermonkey's fix). */
	private static void floatTolerance(ServerPlayer p, boolean on) {
		if (p.getAbilities().instabuild || p.isSpectator()) {
			return;
		}
		if (on) {
			if (!p.getAbilities().mayfly || p.getAbilities().flying) {
				p.getAbilities().mayfly = true;
				p.getAbilities().flying = false;
				p.onUpdateAbilities();
			}
			return;
		}
		if (p.getAbilities().mayfly && !com.projecthero.mod.hero.power.HeroFlight.isFlying(p) && !com.projecthero.mod.power.ThorPowers.isFlying(p)
				&& !p.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.IRON_MAN_FLYING, false)
				&& !p.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.REPULSOR_BOOTS_FLYING, false)) {
			p.getAbilities().mayfly = false;
			p.getAbilities().flying = false;
			p.onUpdateAbilities();
		}
	}

	// ---------------------------------------------------------------- helpers

	/**
	 * A vine from his hand for {@code life} ticks: to the fixed point {@code at}, or, {@code to} given, to that creature wherever it goes
	 * (colour = its id + 1). Drawn by {@code WildvineClient}.
	 */
	private static AlienProjectileEntity vine(ServerPlayer p, @Nullable Vec3 at, @Nullable LivingEntity to, int life) {
		AlienProjectileEntity s = AlienProjectileEntity.of(p, "wildvine_vine");
		Vec3 end = to == null ? at : mid(to);
		s.moveTo(end.x, end.y, end.z, 0f, 0f);
		s.colour(to == null ? 0 : to.getId() + 1).size((float) Math.max(0.6, p.position().distanceTo(end) / 3.0)).held(true).speed(0).life(life)
				.damage(0f).spawn();
		return s;
	}

	/** Vines coiled round {@code e} for {@code ticks} (a boss a quarter, like its root). */
	private static void wrap(ServerPlayer p, LivingEntity e, int ticks) {
		int t = AlienStatus.isBoss(e) ? Math.max(5, ticks / 4) : ticks;
		AlienProjectileEntity w = AlienProjectileEntity.of(p, "wildvine_wrap");
		w.moveTo(e.getX(), e.getY(), e.getZ(), 0f, 0f);
		w.colour(e.getId() + 1).size(Math.max(0.6f, e.getBbHeight() * 0.6f)).held(true).speed(0).life(t).damage(0f).spawn();
	}

	/** The first block along {@code dir}, its real shape: his own walking through trees must not let a vine through them. */
	@Nullable
	private static BlockHitResult blockAlong(ServerPlayer p, Vec3 dir, double range) {
		Vec3 eye = p.getEyePosition();
		BlockHitResult hit = p.level().clip(new ClipContext(eye, eye.add(dir.scale(range)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
				CollisionContext.empty()));
		return hit.getType() == HitResult.Type.BLOCK ? hit : null;
	}

	private static void say(ServerPlayer p, String key) {
		p.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.GRAY), true);
	}

	private static Vec3 flat(Player p) {
		return Vec3.directionFromRotation(0, p.getYRot());
	}

	private static void leaves(ServerLevel l, Vec3 at, int n, double spread) {
		l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OAK_LEAVES.defaultBlockState()), at.x, at.y, at.z, n, spread, spread, spread,
				0.1);
	}

	private static void dirt(ServerLevel l, Vec3 c, int n) {
		BlockState under = l.getBlockState(BlockPos.containing(c).below());
		BlockState st = under.isAir() ? Blocks.DIRT.defaultBlockState() : under;
		l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, st), c.x, c.y + 0.1, c.z, n, 0.5, 0.1, 0.5, 0.15);
	}

	// ---------------------------------------------------------------- damage

	@Override
	public boolean allowDamage(ServerPlayer p, DamageSource source, float amount) {
		if (source.is(DamageTypeTags.IS_FALL) && (flag(p, F_AIR) || flag(p, F_SWING))) {
			return false; // off his own vines
		}
		if (source.is(DamageTypes.IN_WALL) && (flag(p, F_BURROW) || inPlant(p))) {
			return false; // he belongs in there
		}
		if (source.is(DamageTypes.CACTUS)) {
			return false; // a plant among plants
		}
		if (flag(p, F_BURROW) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && !source.is(DamageTypeTags.IS_FIRE)) {
			return false; // under the ground (fire still finds a plant)
		}
		return true;
	}

	@Override
	public float incomingDamage(ServerPlayer p, DamageSource source, float amount) {
		if (source.is(DamageTypeTags.IS_FIRE)) {
			return amount * FIRE_WEAKNESS; // a plant burns
		}
		if (source.is(DamageTypeTags.IS_FREEZING)) {
			p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2)); // frost stiffens his vines
			return amount * FROST_WEAKNESS;
		}
		if (source.is(DamageTypeTags.IS_FALL)) {
			return amount * 0.5f; // a pliant body
		}
		return amount;
	}

	/** Gametests: whether a Constrict coil is running for {@code p}. */
	public static boolean coiling(ServerPlayer p) {
		return COILS.containsKey(p.getUUID());
	}

	/** Gametests: whether a Vine Slam is under way for {@code p}. */
	public static boolean slamming(ServerPlayer p) {
		return SLAMS.containsKey(p.getUUID());
	}

	/** Gametests: how many seed mines {@code p} has planted and still live. */
	public static int mineCount(ServerPlayer p) {
		List<AlienProjectileEntity> m = MINE_LIST.get(p.getUUID());
		return m == null ? 0 : (int) m.stream().filter(Entity::isAlive).count();
	}

	/** Gametests: the seed mines (to step a creature onto one). */
	public static List<AlienProjectileEntity> mines(ServerPlayer p) {
		List<AlienProjectileEntity> m = MINE_LIST.get(p.getUUID());
		return m == null ? List.of() : List.copyOf(m);
	}
}
