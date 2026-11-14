package com.projecthero.mod.omnitrix.alien.snareoh;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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
import com.projecthero.mod.omnitrix.alien.jetray.RecalKitMath;
import com.projecthero.mod.omnitrix.data.OmnitrixState;
import com.projecthero.mod.omnitrix.entity.AlienProjectileEntity;
import com.projecthero.mod.quirk.fx.QuirkFx;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 (user: "make aliens based on their abilities and stuff they do in the show please"). Snare-oh, a Thep Khufan (prototype
 * Omnitrix, a hidden alien found by a mistransform): a mummy whose bandages stretch, lash and wrap whatever they reach. His body is hollow
 * and full of sand: he can come apart into loose bandages for a moment and put himself back together.
 * <p>
 * v0.17.4 revamp (user: "improve their abilities, take out any abilities that aren't good or don't make sense, add new ones, and give all
 * the abilities shifted variants and hold abilities if it makes sense"; design: docs/OMNITRIX_PROTOTYPE_REVAMP_0174.md). Detach (V) is gone
 * (a second grab-and-drag next to Grapple Pull, and an attack on the utility key); Bandage Swing is a real hold now.
 *
 * <pre>
 *   R / Shift+R / Hold R   Bandage Whip (7) / Bandage Bind (6, bound 3 s) / Lash Flurry (4 every 0.25 s for up to 2 s, slowed)
 *   G / Shift+G / Hold G   Grapple Pull (8, yanked in) / Bandage Slam (lift and slam: 15, 6 round the impact) / Tether Throw (whirl it
 *                          round you, 4 to what it hits; let go to fling it: 12 + 6 where it lands)
 *   X / Shift+X / Hold X   Bandage Zip (reel to a block, 40) / Recoil (snapped 10 blocks backwards) / Bandage Swing (Jump reels in, Sneak
 *                          pays out, let go to fling)
 *   Z / Shift+Z / Hold Z   Mummy Wrap (16 within 7, all bound 2.5 s) / Sand Coffin (every creature HE has bound within 12: 20, the
 *                          binding ends) / Cocoon (wrapped up for up to 4 s: no moving, no damage, 1 heart a second; burst: 6 within 3)
 *   V / Shift+V            Scatter (1.5 s untouchable, fast, mobs lose you) / Bandage Net (a 5-wide net at the crosshair for 6 s: what
 *                          touches it is bound 1.5 s)
 *   C / Shift+C / Hold C   Unravel Spin (three lashes of 5) / Sand Gust (8-block cone: 9, blinded 2 s) / Whirlwind (spin while walking,
 *                          up to 3 s: 4 every 0.5 s round you)
 *   Passives   Hollow Body (shots do half); Sand Filled (no fall damage under 8 blocks, never suffocates or drowns)
 *   Weakness   Dry Wrappings (fire does half again, and burns longer)
 * </pre>
 */
public final class SnareohKit extends AlienKit {
	public static final String ID = "snare_oh";
	/** His bandages and the dust off them. */
	public static final int BANDAGE = 0xB4AE7E, BANDAGE_DARK = 0x857D55, GLOW = 0x71D32A;

	// ---------------------------------------------------------------- synced flags / poses
	public static final int F_SWING = 1, F_ZIP = 2, F_AIR = 4, F_SCATTER = 16, F_SPIN = 32, F_LASH = 64, F_TETHER = 128, F_COCOON = 256, F_WHIRL = 512;
	public static final int ANIM_WHIP = 1, ANIM_BIND = 2, ANIM_PULL = 3, ANIM_SWING = 4, ANIM_ZIP = 5, ANIM_WRAP = 6, ANIM_SPIN = 7, ANIM_SCATTER = 9,
			ANIM_REFORM = 10, ANIM_PUNCH = 11, ANIM_LASH = 13, ANIM_SLAM = 14, ANIM_TETHER = 15, ANIM_FLING = 16, ANIM_RECOIL = 17, ANIM_COFFIN = 18,
			ANIM_COCOON = 19, ANIM_BURST = 20, ANIM_NET = 21, ANIM_GUST = 22, ANIM_WHIRL = 23;

	// ---------------------------------------------------------------- moves (lang projecthero.omnitrix.snare_oh.<id>)
	public static final String WHIP = "bandage_whip", BIND = "bandage_bind", LASH = "lash_flurry", PULL = "grapple_pull", SLAM = "bandage_slam",
			TETHER = "tether_throw", ZIP = "bandage_zip", RECOIL = "recoil", SWING = "bandage_swing", WRAP = "mummy_wrap", COFFIN = "sand_coffin",
			COCOON = "cocoon", SCATTER = "scatter", NET = "bandage_net", SPIN = "unravel_spin", GUST = "sand_gust", WHIRL = "whirlwind",
			HOLLOW = "hollow_body", SAND = "sand_filled", DRY = "dry_wrappings";

	/** The synced line the owning client moves him on (Spidermonkey's keys): 1 = swing, 2 = zip; the hook and rope in thousandths. */
	public static final String LINE = "line", LINE_AT = "line_at", HOOK_X = "hook_x", HOOK_Y = "hook_y", HOOK_Z = "hook_z", ROPE = "rope";
	private static final String[] LINE_KEYS = { LINE, LINE_AT, HOOK_X, HOOK_Y, HOOK_Z, ROPE };
	public static final String SCATTER_UNTIL = "scatter_until", SPIN_AT = "spin_at", LASH_AT = "lash_at", COCOON_AT = "cocoon_at", WHIRL_AT = "whirl_at",
			TETHER_AT = "tether_at";
	private static final String[] HOLD_KEYS = { SCATTER_UNTIL, SPIN_AT, LASH_AT, COCOON_AT, WHIRL_AT, TETHER_AT };

	// ---------------------------------------------------------------- numbers (basic 5-8, specials 12-17, big 17-20, ultimates 21-36)
	public static final float WHIP_DAMAGE = 7f, BIND_DAMAGE = 6f, LASH_DAMAGE = 4f, PULL_DAMAGE = 8f, SLAM_DAMAGE = 15f, SLAM_SPLASH = 6f, TETHER_BUMP = 4f,
			FLING_DAMAGE = 12f, FLING_SPLASH = 6f, WRAP_DAMAGE = 16f, COFFIN_DAMAGE = 20f, COCOON_BURST = 6f, SPIN_DAMAGE = 5f, GUST_DAMAGE = 9f,
			WHIRL_DAMAGE = 4f, REFORM_DAMAGE = 6f;
	public static final float SHOT_TAKEN = 0.5f, FIRE_TAKEN = 1.5f, SAFE_FALL = 8f, COCOON_HEAL = 2f;
	public static final int BIND_TICKS = 60, WRAP_TICKS = 50, NET_BIND_TICKS = 30, SPIN_HITS = 3, SPIN_GAP = 5, SCATTER_TICKS = 30, ZIP_MAX = 50,
			SWING_MAX = 600;
	/** The holds: their longest run and the gap between pulses (ticks). */
	public static final int LASH_MAX = 40, LASH_EVERY = 5, TETHER_MAX = 40, COCOON_MAX = 80, WHIRL_MAX = 60, WHIRL_EVERY = 10, GUST_BLIND = 40, NET_TICKS = 120,
			SLAM_DROP_AT = 7, FLING_MAX = 30;
	public static final double WHIP_RANGE = 8, BIND_RANGE = 14, PULL_RANGE = 16, SLAM_RANGE = 12, SLAM_SPLASH_RADIUS = 2.5, TETHER_RANGE = 12, TETHER_RADIUS = 2.6,
			SWING_RANGE = 36, ZIP_RANGE = 40, RECOIL_SPEED = 1.5, WRAP_RADIUS = 7, COFFIN_RADIUS = 12, COCOON_BURST_RADIUS = 3, NET_RANGE = 16,
			NET_HALF_WIDE = 2.5, NET_HALF_HIGH = 1.5, SPIN_RADIUS = 4.5, GUST_RANGE = 8, GUST_HALF_ANGLE = 35, WHIRL_RADIUS = 3.5, REFORM_RADIUS = 3,
			ZIP_SPEED = 1.8, ZIP_ARRIVE = 1.8, SWING_SLACK_BREAK = 14.0;

	// ---------------------------------------------------------------- sounds (the sounds test: volume <= 0.75, pitch >= 0.5)
	public static final OmnitrixSfx.Cue SND_LASH = OmnitrixSfx.cue("snare_oh_lash")
			.layer(SoundEvents.PLAYER_ATTACK_SWEEP, 0.5f, 1.4f)
			.layer(SoundEvents.WOOL_PLACE, 0.6f, 0.8f)
			.layer(SoundEvents.LEASH_KNOT_PLACE, 0.4f, 1.3f)
			.gap(2);
	public static final OmnitrixSfx.Cue SND_WRAP = OmnitrixSfx.cue("snare_oh_wrap")
			.layer(SoundEvents.WOOL_BREAK, 0.6f, 0.7f)
			.layer(SoundEvents.LEASH_KNOT_PLACE, 0.5f, 0.9f)
			.layer(SoundEvents.SAND_STEP, 0.6f, 0.8f)
			.gap(3);
	public static final OmnitrixSfx.Cue SND_SAND = OmnitrixSfx.cue("snare_oh_sand")
			.layer(SoundEvents.SAND_BREAK, 0.6f, 0.8f)
			.layer(SoundEvents.SAND_FALL, 0.5f, 0.7f)
			.gap(4);
	public static final OmnitrixSfx.Cue SND_MOAN = OmnitrixSfx.cue("snare_oh_moan")
			.layer(SoundEvents.HUSK_AMBIENT, 0.5f, 0.8f)
			.layer(SoundEvents.SAND_FALL, 0.4f, 0.6f)
			.gap(10);
	/** v0.17.4: a body slammed down (Bandage Slam, Tether Throw's landing). */
	public static final OmnitrixSfx.Cue SND_SLAM = OmnitrixSfx.cue("snare_oh_slam")
			.layer(SoundEvents.GENERIC_EXPLODE.value(), 0.25f, 1.2f)
			.layer(SoundEvents.SAND_BREAK, 0.7f, 0.6f)
			.layer(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.6f, 0.8f)
			.gap(3);
	/** v0.17.4: Sand Coffin's crush. */
	public static final OmnitrixSfx.Cue SND_CRUSH = OmnitrixSfx.cue("snare_oh_crush")
			.layer(SoundEvents.SAND_BREAK, 0.75f, 0.5f)
			.layer(SoundEvents.WOOL_BREAK, 0.7f, 0.5f)
			.layer(SoundEvents.HUSK_HURT, 0.5f, 0.6f, 2)
			.gap(5);

	// ---------------------------------------------------------------- server-only state
	/** A swing (mode 1) or zip (mode 2) line: the hook, the block it is stuck to, the rope, when, its drawn bandage. */
	private record Line(int mode, Vec3 hook, BlockPos block, double rope, long start, AlienProjectileEntity strand) {
	}

	/** A creature he has bound: until when, and the wrap drawn round it (an entity id). */
	private record Bind(long until, int wrap) {
	}

	/** Bandage Slam / Tether Throw / the fling after it: the creature (an entity id), from when, its rope (if any). */
	private record Hold(int target, long start, @Nullable AlienProjectileEntity rope) {
	}

	/** A Bandage Net strung in the air: its middle, its width axis, when it comes down, who it has caught (and when). */
	private record Net(Vec3 centre, Vec3 right, long until, Map<Integer, Long> caught) {
	}

	private static final Map<UUID, Line> LINES = new ConcurrentHashMap<>();
	private static final Map<UUID, Integer> SPINS = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_TICK = new ConcurrentHashMap<>();
	private static final Map<UUID, Map<Integer, Bind>> BOUND = new ConcurrentHashMap<>();
	private static final Map<UUID, Hold> SLAMS = new ConcurrentHashMap<>();
	private static final Map<UUID, Hold> TETHERS = new ConcurrentHashMap<>();
	private static final Map<UUID, Hold> FLINGS = new ConcurrentHashMap<>();
	private static final Map<UUID, List<Net>> NETS = new ConcurrentHashMap<>();

	private SnareohKit() {
	}

	public static void register() {
		Aliens.register(new SnareohKit());
	}

	public static void clearSessionState() {
		LINES.clear();
		SPINS.clear();
		LAST_TICK.clear();
		BOUND.clear();
		SLAMS.clear();
		TETHERS.clear();
		FLINGS.clear();
		NETS.clear();
	}

	@Override
	public String id() {
		return ID;
	}

	public static boolean isSnareoh(Player p) {
		return Omnitrix.hasPower(p) && Omnitrix.state(p).transformed() && ID.equals(Omnitrix.state(p).active);
	}

	@Override
	public AlienStats stats() {
		// Ben's size, light (he's hollow) and quick
		return AlienStats.builder().health(30f).speed(1.1f).scale(1.05f).armor(6f, 0f).attack(5f).jump(0.15f).safeFall(SAFE_FALL).build();
	}

	@Override
	protected List<AlienMove> declareMoves() {
		return List.of(AlienMove.tap(WHIP, AbilitySlot.SLOT_1, 12), AlienMove.shift(BIND, AbilitySlot.SLOT_1, 160), AlienMove.hold(LASH, AbilitySlot.SLOT_1, 120),
				AlienMove.tap(PULL, AbilitySlot.SLOT_2, 100), AlienMove.shift(SLAM, AbilitySlot.SLOT_2, 200), AlienMove.hold(TETHER, AbilitySlot.SLOT_2, 240),
				AlienMove.tap(ZIP, AbilitySlot.SLOT_3, 40), AlienMove.shift(RECOIL, AbilitySlot.SLOT_3, 80), AlienMove.hold(SWING, AbilitySlot.SLOT_3, 10),
				AlienMove.tap(WRAP, AbilitySlot.SLOT_4, 500), AlienMove.shift(COFFIN, AbilitySlot.SLOT_4, 360), AlienMove.hold(COCOON, AbilitySlot.SLOT_4, 600),
				AlienMove.tap(SCATTER, AbilitySlot.SLOT_5, 240), AlienMove.shift(NET, AbilitySlot.SLOT_5, 320),
				AlienMove.tap(SPIN, AbilitySlot.SLOT_6, 120), AlienMove.shift(GUST, AbilitySlot.SLOT_6, 200), AlienMove.hold(WHIRL, AbilitySlot.SLOT_6, 240),
				AlienMove.vanilla(HOLLOW, "Passive", 0), AlienMove.vanilla(SAND, "Passive", 0), AlienMove.vanilla(DRY, "Weakness", 0));
	}

	@Override
	public boolean holdable(AbilitySlot slot) {
		return slot == AbilitySlot.SLOT_1 || slot == AbilitySlot.SLOT_2 || slot == AbilitySlot.SLOT_3 || slot == AbilitySlot.SLOT_4 || slot == AbilitySlot.SLOT_6;
	}

	@Override
	public boolean keyActive(Player p, AbilitySlot slot) {
		return switch (slot) {
			case SLOT_1 -> flag(p, F_LASH);
			case SLOT_2 -> flag(p, F_TETHER);
			case SLOT_3 -> flag(p, F_SWING) || flag(p, F_ZIP);
			case SLOT_4 -> flag(p, F_COCOON);
			case SLOT_5 -> flag(p, F_SCATTER);
			case SLOT_6 -> flag(p, F_SPIN) || flag(p, F_WHIRL);
			default -> false;
		};
	}

	private static void say(ServerPlayer p, String key, Object... args) {
		p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.snare_oh." + key, args).withStyle(ChatFormatting.GRAY), true);
	}

	// ---------------------------------------------------------------- bandages (drawn by the client: SnareohBandages)

	/**
	 * A bandage drawn from his hand to {@code end} (or to entity {@code to}'s middle, followed) for {@code life} ticks. {@code kind}:
	 * "snare_oh_whip" lashes out and back, "snare_oh_rope" hangs taut between them, "snare_oh_wrap" winds round {@code to}.
	 */
	public static AlienProjectileEntity bandage(ServerPlayer p, String kind, Vec3 end, @Nullable Entity to, int life) {
		AlienProjectileEntity a = AlienProjectileEntity.of(p, kind);
		a.moveTo(end.x, end.y, end.z, 0f, 0f);
		a.colour(to == null ? 0 : to.getId() + 1).size(1f).held(true).speed(0).life(life).damage(0f).spawn();
		return a;
	}

	private static Vec3 centre(Entity e) {
		return e.position().add(0, e.getBbHeight() * 0.55, 0);
	}

	private static Vec3 aimPoint(ServerPlayer p, double range) {
		Vec3 eye = p.getEyePosition();
		Vec3 end = eye.add(p.getLookAngle().scale(range));
		HitResult hit = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		return hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
	}

	@Nullable
	private static BlockHitResult blockAlong(ServerPlayer p, Vec3 dir, double range) {
		Vec3 eye = p.getEyePosition();
		HitResult hit = p.level().clip(new ClipContext(eye, eye.add(dir.scale(range)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		return hit.getType() == HitResult.Type.BLOCK ? (BlockHitResult) hit : null;
	}

	private static Vec3 flatLook(Player p) {
		Vec3 look = p.getLookAngle();
		Vec3 h = new Vec3(look.x, 0, look.z);
		return h.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : h.normalize();
	}

	/** {@code e} wrapped up: bound {@code ticks} (a boss a quarter of it), the wrap drawn round it, and remembered as bound by him. */
	private void bind(ServerPlayer p, LivingEntity e, int ticks) {
		AlienStatus.root(p, e, ticks, 20);
		int t = AlienStatus.isBoss(e) ? Math.max(5, ticks / 4) : ticks;
		AlienProjectileEntity wrap = bandage(p, "snare_oh_wrap", centre(e), e, t);
		Map<Integer, Bind> mine = BOUND.computeIfAbsent(p.getUUID(), k -> new ConcurrentHashMap<>());
		Bind old = mine.put(e.getId(), new Bind(p.level().getGameTime() + t, wrap.getId()));
		if (old != null && level(p).getEntity(old.wrap()) instanceof AlienProjectileEntity w) {
			w.discard(); // re-bound: one wrap round it, not two
		}
	}

	/** Whether {@code e} is bound by {@code p}'s bandages right now (Sand Coffin's targets). */
	public static boolean boundBy(ServerPlayer p, LivingEntity e) {
		Map<Integer, Bind> mine = BOUND.get(p.getUUID());
		Bind b = mine == null ? null : mine.get(e.getId());
		return b != null && b.until() > p.level().getGameTime() && e.isAlive();
	}

	/** Lets {@code e} out of his binding: the root and the wrap gone. */
	private void unbind(ServerPlayer p, LivingEntity e) {
		Map<Integer, Bind> mine = BOUND.get(p.getUUID());
		Bind b = mine == null ? null : mine.remove(e.getId());
		if (b != null && level(p).getEntity(b.wrap()) instanceof AlienProjectileEntity w) {
			w.discard();
		}
		MobEffectInstance slow = e.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
		if (slow != null && slow.getAmplifier() >= 9) {
			e.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
		}
	}

	private static void sand(ServerLevel l, Vec3 at, int n, double spread) {
		l.sendParticles(new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.SAND.defaultBlockState()), at.x, at.y, at.z, n, spread, spread * 0.6, spread, 0.02);
	}

	/** Dust kicked off the ground under {@code at}. */
	private static void groundDust(ServerLevel l, Vec3 at, int n, double spread) {
		BlockState under = l.getBlockState(BlockPos.containing(at.x, at.y - 0.2, at.z));
		if (!under.isAir()) {
			l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, under), at.x, at.y + 0.1, at.z, n, spread, 0.1, spread, 0.15);
		}
	}

	// ---------------------------------------------------------------- lifecycle

	@Override
	public void onTransform(ServerPlayer p) {
		OmnitrixSfx.play(level(p), p.position(), SND_MOAN, 0.6f, 1f);
	}

	@Override
	public void onRevert(ServerPlayer p) {
		endLine(p, false);
		UUID id = p.getUUID();
		SPINS.remove(id);
		LAST_TICK.remove(id);
		BOUND.remove(id);
		SLAMS.remove(id);
		Hold t = TETHERS.remove(id);
		if (t != null && t.rope() != null) {
			t.rope().discard();
		}
		FLINGS.remove(id);
		NETS.remove(id);
		OmnitrixState n = Omnitrix.state(p).copy();
		for (String k : LINE_KEYS) {
			n.timers.remove(key(k));
		}
		for (String k : HOLD_KEYS) {
			n.timers.remove(key(k));
		}
		n.kitFlags = 0;
		Omnitrix.save(p, n);
		p.setInvisible(false);
	}

	@Override
	public boolean allowDamage(ServerPlayer p, DamageSource source, float amount) {
		if ((flag(p, F_SCATTER) || flag(p, F_COCOON)) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return false; // nothing to hit: a cloud of loose bandages, or wrapped up tight in a cocoon of them
		}
		if ((flag(p, F_AIR) || flag(p, F_SWING) || flag(p, F_ZIP)) && source.is(DamageTypeTags.IS_FALL)) {
			return false;
		}
		// Sand Filled: nothing to suffocate or drown
		if (source.is(net.minecraft.world.damagesource.DamageTypes.IN_WALL) || source.is(DamageTypeTags.IS_DROWNING)) {
			return false;
		}
		return true;
	}

	@Override
	public float incomingDamage(ServerPlayer p, DamageSource source, float amount) {
		if (source.is(DamageTypeTags.IS_PROJECTILE)) {
			return amount * SHOT_TAKEN; // Hollow Body: through the wrappings and out the other side
		}
		if (source.is(DamageTypeTags.IS_FIRE)) {
			return amount * FIRE_TAKEN; // Dry Wrappings
		}
		return amount;
	}

	@Override
	public void tick(ServerPlayer p, OmnitrixState s, long now) {
		Long last = LAST_TICK.put(p.getUUID(), now);
		if (last != null && last == now) {
			return;
		}
		ServerLevel l = level(p);
		tickLine(p, now);
		if (flag(p, F_AIR) && !flag(p, F_SWING) && !flag(p, F_ZIP) && (p.onGround() || p.isInWater())) {
			setFlag(p, F_AIR, false);
		}
		if (flag(p, F_SPIN)) {
			spinTick(p, l, now);
		}
		if (flag(p, F_LASH)) {
			lashTick(p, l, now);
		}
		if (flag(p, F_TETHER)) {
			tetherTick(p, l, now);
		}
		if (flag(p, F_COCOON)) {
			cocoonTick(p, l, now);
		}
		if (flag(p, F_WHIRL)) {
			whirlTick(p, l, now);
		}
		if (SLAMS.containsKey(p.getUUID())) {
			slamTick(p, l, now);
		}
		if (FLINGS.containsKey(p.getUUID())) {
			flingTick(p, l, now);
		}
		if (NETS.containsKey(p.getUUID())) {
			netTick(p, l, now);
		}
		if (now % 20 == 0) {
			Map<Integer, Bind> mine = BOUND.get(p.getUUID());
			if (mine != null) {
				mine.values().removeIf(b -> b.until() <= now);
			}
		}
		if (flag(p, F_SCATTER)) {
			if (now >= timer(p, SCATTER_UNTIL)) {
				reform(p);
			} else {
				sand(l, p.position().add(0, 1.0, 0), 4, 0.5);
			}
		}
		// Dry Wrappings: fire clings to him
		if (p.isOnFire() && now % 20 == 0 && p.getRemainingFireTicks() < 60) {
			p.setRemainingFireTicks(p.getRemainingFireTicks() + 10);
		}
	}

	// ---------------------------------------------------------------- keys

	@Override
	public void press(ServerPlayer p, AbilitySlot slot, boolean shift) {
		if (flag(p, F_SCATTER) && !(slot == AbilitySlot.SLOT_5 && !shift)) {
			return; // he is all over the place: nothing to throw a punch with
		}
		if (flag(p, F_COCOON)) {
			return; // wrapped up tight
		}
		switch (slot) {
			case SLOT_1 -> {
				if (shift) {
					bindMove(p);
				} else {
					whip(p);
				}
			}
			case SLOT_2 -> {
				if (shift) {
					bandageSlam(p);
				} else {
					pull(p);
				}
			}
			case SLOT_3 -> {
				if (shift) {
					recoil(p);
				} else {
					zip(p);
				}
			}
			case SLOT_4 -> {
				if (shift) {
					sandCoffin(p);
				} else {
					mummyWrap(p);
				}
			}
			case SLOT_5 -> {
				if (shift) {
					bandageNet(p);
				} else {
					scatter(p);
				}
			}
			case SLOT_6 -> {
				if (shift) {
					sandGust(p);
				} else {
					spin(p);
				}
			}
			default -> {
			}
		}
	}

	@Override
	public void holdStart(ServerPlayer p, AbilitySlot slot) {
		if (flag(p, F_SCATTER) || flag(p, F_COCOON)) {
			return;
		}
		switch (slot) {
			case SLOT_1 -> startLash(p);
			case SLOT_2 -> startTether(p);
			case SLOT_3 -> swing(p);
			case SLOT_4 -> startCocoon(p);
			case SLOT_6 -> startWhirl(p);
			default -> {
			}
		}
	}

	@Override
	public void holdEnd(ServerPlayer p, AbilitySlot slot, int heldTicks) {
		switch (slot) {
			case SLOT_1 -> endLash(p);
			case SLOT_2 -> fling(p);
			case SLOT_3 -> {
				if (swinging(p)) {
					endLine(p, true); // letting go flings him on
				}
			}
			case SLOT_4 -> endCocoon(p);
			case SLOT_6 -> endWhirl(p);
			default -> {
			}
		}
	}

	@Override
	public void release(ServerPlayer p, AbilitySlot slot, boolean shift, int heldTicks) {
		// the swing runs while X is held; letting go flings him on (a zip runs to its end)
		if (slot == AbilitySlot.SLOT_3 && swinging(p)) {
			endLine(p, true);
		}
	}

	@Override
	public boolean onAttack(ServerPlayer p, Entity target) {
		anim(p, ANIM_PUNCH);
		return false;
	}

	// ---------------------------------------------------------------- R / Shift+R / Hold R

	/** R: a bandage lashes out at the crosshair and snaps back. */
	public boolean whip(ServerPlayer p) {
		if (!use(p, WHIP)) {
			return false;
		}
		anim(p, ANIM_WHIP);
		lash(p, WHIP_DAMAGE, 0.55, 9, 1.1f);
		return true;
	}

	/** One lash at the crosshair (the whip, and each of the Lash Flurry's). */
	private void lash(ServerPlayer p, float damage, double knock, int life, float pitch) {
		LivingEntity t = lookTarget(p, WHIP_RANGE);
		Vec3 end = t != null ? centre(t) : aimPoint(p, WHIP_RANGE);
		if (t != null) {
			t.invulnerableTime = 0;
			if (hurt(p, t, damage, false)) {
				shove(t, p.position(), knock, 0.2);
			}
			level(p).sendParticles(ParticleTypes.SWEEP_ATTACK, end.x, end.y, end.z, 1, 0, 0, 0, 0);
		}
		bandage(p, "snare_oh_whip", end, null, life);
		OmnitrixSfx.playOn(p, SND_LASH, 0.7f, pitch, SoundSource.PLAYERS);
	}

	/** Shift+R: a bandage winds round the creature you look at and binds it. */
	public boolean bindMove(ServerPlayer p) {
		LivingEntity t = lookTarget(p, BIND_RANGE);
		if (t == null) {
			say(p, "no_target");
			return false;
		}
		if (!use(p, BIND)) {
			return false;
		}
		anim(p, ANIM_BIND);
		t.invulnerableTime = 0;
		hurt(p, t, BIND_DAMAGE, false);
		bind(p, t, BIND_TICKS);
		bandage(p, "snare_oh_rope", centre(t), t, 10);
		OmnitrixSfx.playOn(p, SND_WRAP, 0.7f, 1f, SoundSource.PLAYERS);
		return true;
	}

	/** Hold R: the Lash Flurry -- he whips at the crosshair again and again while R is held (up to 2 s), slowed. */
	public boolean startLash(ServerPlayer p) {
		if (flag(p, F_LASH)) {
			return false;
		}
		if (!ready(p, LASH)) {
			return use(p, LASH); // the "not ready" line
		}
		anim(p, ANIM_LASH);
		setTimer(p, LASH_AT, p.level().getGameTime());
		setFlag(p, F_LASH, true);
		lash(p, LASH_DAMAGE, 0.15, 6, 1.3f); // the first lash on the hold
		return true;
	}

	private void lashTick(ServerPlayer p, ServerLevel l, long now) {
		long age = now - timer(p, LASH_AT);
		if (age >= LASH_MAX) {
			endLash(p);
			return;
		}
		p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 5, 1, false, false, false));
		if (age > 0 && age % LASH_EVERY == 0) {
			lash(p, LASH_DAMAGE, 0.15, 6, 1.2f + (age / LASH_EVERY % 3) * 0.12f);
		}
	}

	public void endLash(ServerPlayer p) {
		if (!flag(p, F_LASH)) {
			return;
		}
		setFlag(p, F_LASH, false);
		setTimer(p, LASH_AT, 0L);
		cooldown(p, LASH, -1);
		anim(p, ANIM_PUNCH);
	}

	// ---------------------------------------------------------------- G / Shift+G / Hold G

	/** G: a bandage catches the creature you look at and yanks it in; a boss is too heavy, so it pulls him to it. */
	public boolean pull(ServerPlayer p) {
		LivingEntity t = lookTarget(p, PULL_RANGE);
		if (t == null) {
			say(p, "no_target");
			return false;
		}
		if (!use(p, PULL)) {
			return false;
		}
		anim(p, ANIM_PULL);
		t.invulnerableTime = 0;
		hurt(p, t, PULL_DAMAGE, false);
		Vec3 d = p.position().subtract(t.position());
		double len = d.length();
		if (len > 1.0e-3) {
			Vec3 n = d.scale(1.0 / len);
			if (AlienStatus.isBoss(t)) {
				p.setDeltaMovement(n.scale(-Math.min(2.2, 0.35 + len * 0.16)).add(0, 0.35, 0));
				p.hurtMarked = true;
				setFlag(p, F_AIR, true);
			} else {
				double res = 1.0 - Math.min(1.0, t.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE));
				double k = Math.min(2.4, 0.3 + (len - 1.5) * 0.17) * Math.max(0.35, res);
				t.setDeltaMovement(n.scale(k).add(0, 0.25 + len * 0.012, 0));
				t.hurtMarked = true;
			}
		}
		bandage(p, "snare_oh_rope", centre(t), t, 10);
		OmnitrixSfx.playOn(p, SND_LASH, 0.75f, 0.85f, SoundSource.PLAYERS);
		return true;
	}

	/** Shift+G: Bandage Slam -- catch the creature you look at, haul it up into the air and slam it down. A boss is slammed where it stands. */
	public boolean bandageSlam(ServerPlayer p) {
		LivingEntity t = lookTarget(p, SLAM_RANGE);
		if (t == null) {
			say(p, "no_target");
			return false;
		}
		if (SLAMS.containsKey(p.getUUID()) || !use(p, SLAM)) {
			return false;
		}
		anim(p, ANIM_SLAM);
		OmnitrixSfx.playOn(p, SND_WRAP, 0.7f, 0.9f, SoundSource.PLAYERS);
		if (AlienStatus.isBoss(t)) {
			bandage(p, "snare_oh_rope", centre(t), t, 10);
			slamImpact(p, level(p), t);
			return true;
		}
		AlienProjectileEntity rope = bandage(p, "snare_oh_rope", centre(t), t, SLAM_DROP_AT + 14);
		SLAMS.put(p.getUUID(), new Hold(t.getId(), p.level().getGameTime(), rope));
		double res = 1.0 - Math.min(1.0, t.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE));
		t.setDeltaMovement(0, 0.8 + 0.35 * Math.max(0.3, res), 0);
		t.hurtMarked = true;
		return true;
	}

	private void slamTick(ServerPlayer p, ServerLevel l, long now) {
		Hold h = SLAMS.get(p.getUUID());
		Entity e = l.getEntity(h.target());
		long age = now - h.start();
		if (!(e instanceof LivingEntity t) || !t.isAlive() || age > SLAM_DROP_AT + 16) {
			SLAMS.remove(p.getUUID());
			return;
		}
		if (age == SLAM_DROP_AT) {
			t.setDeltaMovement(0, -2.6, 0); // the bandage whips it down
			t.hurtMarked = true;
			OmnitrixSfx.playOn(p, SND_LASH, 0.7f, 0.7f, SoundSource.PLAYERS);
		}
		if (age > SLAM_DROP_AT && (t.onGround() || t.isInWater() || age >= SLAM_DROP_AT + 12)) {
			SLAMS.remove(p.getUUID());
			if (h.rope() != null) {
				h.rope().discard();
			}
			slamImpact(p, l, t);
		}
	}

	private void slamImpact(ServerPlayer p, ServerLevel l, LivingEntity t) {
		t.invulnerableTime = 0;
		hurt(p, t, SLAM_DAMAGE, false);
		t.fallDistance = 0f;
		Vec3 at = t.position();
		for (LivingEntity o : around(p, at, SLAM_SPLASH_RADIUS)) {
			if (o != t) {
				o.invulnerableTime = 0;
				if (hurt(p, o, SLAM_SPLASH, false)) {
					shove(o, at, 0.6, 0.35);
				}
			}
		}
		groundDust(l, at, 30, 1.0);
		sand(l, at.add(0, 0.5, 0), 20, 0.8);
		QuirkFx.ring(l, at.add(0, 0.1, 0)).style(QuirkFx.RING_SHOCK).colours(0xF4EED2, BANDAGE).size((float) SLAM_SPLASH_RADIUS).thickness(0.3f).life(9).ground().spawn();
		OmnitrixSfx.play(l, at, SND_SLAM, 1f, 1f);
	}

	/** Hold G: Tether Throw -- a bandage catches the creature you look at and he whirls it round himself while G is held (up to 2 s). */
	public boolean startTether(ServerPlayer p) {
		if (flag(p, F_TETHER)) {
			return false;
		}
		if (!ready(p, TETHER)) {
			return use(p, TETHER);
		}
		LivingEntity t = lookTarget(p, TETHER_RANGE);
		if (t == null) {
			say(p, "no_target");
			return false;
		}
		if (AlienStatus.isBoss(t)) {
			say(p, "too_heavy");
			return false;
		}
		anim(p, ANIM_TETHER);
		AlienProjectileEntity rope = bandage(p, "snare_oh_rope", centre(t), t, TETHER_MAX + 10);
		TETHERS.put(p.getUUID(), new Hold(t.getId(), p.level().getGameTime(), rope));
		setTimer(p, TETHER_AT, p.level().getGameTime());
		setFlag(p, F_TETHER, true);
		t.invulnerableTime = 0;
		hurt(p, t, TETHER_BUMP, false);
		OmnitrixSfx.playOn(p, SND_WRAP, 0.7f, 1.1f, SoundSource.PLAYERS);
		return true;
	}

	/** Where the whirled creature is {@code age} ticks into the Tether Throw: a circle round him at waist height. */
	private static Vec3 whirlPos(ServerPlayer p, long age, LivingEntity t) {
		double a = Math.toRadians(p.getYRot()) + age * 0.5;
		return p.position().add(-Math.sin(a) * TETHER_RADIUS, 0.6 + Math.sin(age * 0.25) * 0.25, Math.cos(a) * TETHER_RADIUS);
	}

	private void tetherTick(ServerPlayer p, ServerLevel l, long now) {
		Hold h = TETHERS.get(p.getUUID());
		Entity e = h == null ? null : l.getEntity(h.target());
		if (!(e instanceof LivingEntity t) || !t.isAlive()) {
			endTether(p);
			return;
		}
		long age = now - h.start();
		if (age >= TETHER_MAX) {
			fling(p);
			return;
		}
		Vec3 at = whirlPos(p, age, t);
		t.teleportTo(at.x, at.y, at.z);
		t.setDeltaMovement(Vec3.ZERO);
		t.fallDistance = 0f;
		t.hurtMarked = true;
		if (age % 10 == 5) {
			// it batters whatever it swings through
			for (LivingEntity o : around(p, centre(t), 1.4)) {
				if (o != t) {
					o.invulnerableTime = 0;
					if (hurt(p, o, TETHER_BUMP, false)) {
						shove(o, p.position(), 0.8, 0.3);
					}
				}
			}
			OmnitrixSfx.play(l, at, SND_LASH, 0.5f, 0.8f + (age % 20) * 0.02f);
		}
		if (age % 2 == 0) {
			sand(l, centre(t), 2, 0.2);
		}
	}

	private void endTether(ServerPlayer p) {
		Hold h = TETHERS.remove(p.getUUID());
		if (h != null && h.rope() != null) {
			h.rope().discard();
		}
		if (flag(p, F_TETHER)) {
			setFlag(p, F_TETHER, false);
			setTimer(p, TETHER_AT, 0L);
			cooldown(p, TETHER, -1);
		}
	}

	/** Lets go of the Tether Throw: the creature is flung where he looks and hits for 12 where it lands. */
	public void fling(ServerPlayer p) {
		Hold h = TETHERS.get(p.getUUID());
		Entity e = h == null ? null : level(p).getEntity(h.target());
		endTether(p);
		if (!(e instanceof LivingEntity t) || !t.isAlive()) {
			return;
		}
		anim(p, ANIM_FLING);
		Vec3 look = p.getLookAngle();
		t.setDeltaMovement(look.x * 1.9, Math.max(0.3, look.y * 1.9 + 0.3), look.z * 1.9);
		t.hurtMarked = true;
		FLINGS.put(p.getUUID(), new Hold(t.getId(), p.level().getGameTime(), null));
		OmnitrixSfx.playOn(p, SND_LASH, 0.75f, 0.7f, SoundSource.PLAYERS);
	}

	private void flingTick(ServerPlayer p, ServerLevel l, long now) {
		Hold h = FLINGS.get(p.getUUID());
		Entity e = l.getEntity(h.target());
		long age = now - h.start();
		if (!(e instanceof LivingEntity t) || !t.isAlive() || age > FLING_MAX) {
			FLINGS.remove(p.getUUID());
			return;
		}
		sand(l, centre(t), 2, 0.15);
		boolean bumped = false;
		for (LivingEntity o : around(p, centre(t), 1.2)) {
			if (o != t) {
				bumped = true;
				break;
			}
		}
		if (age >= 3 && (t.onGround() || t.horizontalCollision || t.isInWater() || bumped)) {
			FLINGS.remove(p.getUUID());
			t.invulnerableTime = 0;
			hurt(p, t, FLING_DAMAGE, false);
			for (LivingEntity o : around(p, t.position(), SLAM_SPLASH_RADIUS)) {
				if (o != t) {
					o.invulnerableTime = 0;
					if (hurt(p, o, FLING_SPLASH, false)) {
						shove(o, t.position(), 0.6, 0.3);
					}
				}
			}
			groundDust(l, t.position(), 20, 0.8);
			OmnitrixSfx.play(l, t.position(), SND_SLAM, 0.9f, 1.1f);
		}
	}

	/** Test hook: the creature he is whirling round himself, or null. */
	@Nullable
	public static LivingEntity whirling(ServerPlayer p) {
		Hold h = TETHERS.get(p.getUUID());
		return h != null && level(p).getEntity(h.target()) instanceof LivingEntity le ? le : null;
	}

	// ---------------------------------------------------------------- X / Shift+X / Hold X (Spidermonkey's line)

	public static int lineMode(Player p) {
		return (int) Omnitrix.state(p).readyAt(ID + "." + LINE);
	}

	public static long lineAt(Player p) {
		return Omnitrix.state(p).readyAt(ID + "." + LINE_AT);
	}

	@Nullable
	public static Vec3 hook(Player p) {
		OmnitrixState s = Omnitrix.state(p);
		Long x = s.timers.get(ID + "." + HOOK_X), y = s.timers.get(ID + "." + HOOK_Y), z = s.timers.get(ID + "." + HOOK_Z);
		return x == null || y == null || z == null ? null : new Vec3(x / 1000.0, y / 1000.0, z / 1000.0);
	}

	public static double rope(Player p) {
		return Omnitrix.state(p).readyAt(ID + "." + ROPE) / 1000.0;
	}

	/** The zip's pull this tick from {@code eye} toward {@code hook} (the client runs it). */
	public static Vec3 zipVelocity(Vec3 eye, Vec3 hook) {
		Vec3 to = hook.subtract(eye);
		double d = to.length();
		return d <= ZIP_ARRIVE || d < 1.0e-6 ? Vec3.ZERO : to.scale(Math.min(ZIP_SPEED, d * 0.6) / d);
	}

	public static boolean swinging(ServerPlayer p) {
		Line l = LINES.get(p.getUUID());
		return l != null && l.mode() == 1;
	}

	public static boolean zipping(ServerPlayer p) {
		Line l = LINES.get(p.getUUID());
		return l != null && l.mode() == 2;
	}

	/** Hold X: a bandage up to the block under the crosshair (or up and ahead), and he swings on it while X is held. */
	public boolean swing(ServerPlayer p) {
		Vec3 look = p.getLookAngle();
		BlockHitResult hit = blockAlong(p, look, SWING_RANGE);
		if (hit == null || hit.getLocation().y < p.getY() + 1.0) {
			Vec3 flat = flatLook(p);
			BlockHitResult up = blockAlong(p, flat.add(0, Math.max(1.0, look.y * 2), 0).normalize(), SWING_RANGE);
			if (up != null && up.getLocation().y >= p.getY() + 1.5) {
				hit = up;
			}
		}
		if (hit == null || hit.getLocation().y < p.getY() + 1.0) {
			say(p, "no_anchor");
			return false;
		}
		if (!use(p, SWING)) {
			return false;
		}
		anim(p, ANIM_SWING);
		Vec3 at = hit.getLocation();
		double rope = Math.max(3.0, Math.min(SWING_RANGE + 2.0, at.distanceTo(p.getEyePosition())));
		startLine(p, 1, at, hit.getBlockPos(), rope, SWING_MAX + 10);
		OmnitrixSfx.playOn(p, SND_LASH, 0.6f, 0.8f, SoundSource.PLAYERS);
		return true;
	}

	/** X: a bandage to the block under the crosshair, and it reels him straight in. */
	public boolean zip(ServerPlayer p) {
		if (!ready(p, ZIP)) {
			use(p, ZIP);
			return false;
		}
		BlockHitResult hit = blockAlong(p, p.getLookAngle(), ZIP_RANGE);
		if (hit == null) {
			say(p, "no_anchor");
			return false;
		}
		use(p, ZIP);
		anim(p, ANIM_ZIP);
		Vec3 at = hit.getLocation();
		startLine(p, 2, at, hit.getBlockPos(), at.distanceTo(p.getEyePosition()), ZIP_MAX + 10);
		OmnitrixSfx.playOn(p, SND_LASH, 0.7f, 1.2f, SoundSource.PLAYERS);
		return true;
	}

	/** Shift+X: Recoil -- a bandage snaps to the ground behind him and yanks him about 10 blocks backwards, out of trouble. */
	public boolean recoil(ServerPlayer p) {
		if (!use(p, RECOIL)) {
			return false;
		}
		endLine(p, false);
		anim(p, ANIM_RECOIL);
		Vec3 back = flatLook(p).scale(-1);
		BlockHitResult hit = blockAlong(p, back.add(0, -0.35, 0).normalize(), 14);
		Vec3 anchor = hit != null ? hit.getLocation() : p.position().add(back.scale(10));
		bandage(p, "snare_oh_rope", anchor, null, 8);
		p.setDeltaMovement(back.scale(RECOIL_SPEED).add(0, 0.38, 0));
		p.hurtMarked = true;
		p.resetFallDistance();
		setFlag(p, F_AIR, true);
		sand(level(p), p.position().add(0, 0.8, 0), 12, 0.4);
		OmnitrixSfx.playOn(p, SND_LASH, 0.75f, 0.9f, SoundSource.PLAYERS);
		return true;
	}

	private void startLine(ServerPlayer p, int mode, Vec3 at, BlockPos block, double rope, int life) {
		endLine(p, false);
		long now = p.level().getGameTime();
		AlienProjectileEntity s = bandage(p, "snare_oh_rope", at, null, life);
		LINES.put(p.getUUID(), new Line(mode, at, block.immutable(), rope, now, s));
		OmnitrixState n = Omnitrix.state(p).copy();
		n.timers.put(key(LINE), (long) mode);
		n.timers.put(key(LINE_AT), now);
		n.timers.put(key(HOOK_X), Math.round(at.x * 1000.0));
		n.timers.put(key(HOOK_Y), Math.round(at.y * 1000.0));
		n.timers.put(key(HOOK_Z), Math.round(at.z * 1000.0));
		n.timers.put(key(ROPE), Math.round(rope * 1000.0));
		n.kitFlags = (n.kitFlags & ~(F_SWING | F_ZIP)) | (mode == 1 ? F_SWING : F_ZIP) | F_AIR;
		Omnitrix.save(p, n);
		p.resetFallDistance();
	}

	private void tickLine(ServerPlayer p, long now) {
		Line l = LINES.get(p.getUUID());
		if (l == null) {
			return;
		}
		if (!l.strand().isAlive() || l.strand().level() != p.level()) {
			endLine(p, false);
			return;
		}
		long age = now - l.start();
		if (age % 5 == 0 && p.level().getBlockState(l.block()).getCollisionShape(p.level(), l.block()).isEmpty()) {
			endLine(p, true);
			return;
		}
		double dist = l.hook().distanceTo(p.getEyePosition());
		if (l.mode() == 2) {
			if (dist < ZIP_ARRIVE + 0.4 || age > ZIP_MAX) {
				endLine(p, false);
				return;
			}
		} else if (dist > l.rope() + SWING_SLACK_BREAK || age > SWING_MAX) {
			endLine(p, true);
			return;
		}
		p.resetFallDistance();
	}

	/** Lets go of the line (the fling is his client's); no fall damage till he lands. */
	public void endLine(ServerPlayer p, boolean sound) {
		Line l = LINES.remove(p.getUUID());
		if (l == null) {
			return;
		}
		l.strand().discard();
		OmnitrixState n = Omnitrix.state(p).copy();
		for (String k : LINE_KEYS) {
			n.timers.remove(key(k));
		}
		n.kitFlags &= ~(F_SWING | F_ZIP);
		n.kitFlags |= F_AIR;
		Omnitrix.save(p, n);
		p.resetFallDistance();
		if (sound) {
			OmnitrixSfx.playOn(p, SND_LASH, 0.4f, 1.5f, SoundSource.PLAYERS);
		}
	}

	// ---------------------------------------------------------------- Z / Shift+Z / Hold Z

	/** Z: bandages burst out of him and wrap everything round him. */
	public boolean mummyWrap(ServerPlayer p) {
		if (!use(p, WRAP)) {
			return false;
		}
		anim(p, ANIM_WRAP);
		ServerLevel l = level(p);
		int n = 0;
		for (LivingEntity e : around(p, p.position(), WRAP_RADIUS)) {
			e.invulnerableTime = 0;
			hurt(p, e, WRAP_DAMAGE, false);
			bind(p, e, WRAP_TICKS);
			if (n++ < 10) {
				bandage(p, "snare_oh_rope", centre(e), e, 12);
			}
		}
		sand(l, p.position().add(0, 1.0, 0), 30, 1.2);
		OmnitrixSfx.play(l, p.position(), SND_WRAP, 0.75f, 0.8f);
		OmnitrixSfx.play(l, p.position(), SND_MOAN, 0.5f, 0.9f);
		return true;
	}

	/**
	 * Shift+Z: Sand Coffin -- every creature HIS bandages hold within 12 is crushed in its wrappings (20) and let go. Nothing bound: nothing
	 * happens and no cooldown is spent. Returns how many it crushed (-1 if not ready).
	 */
	public int sandCoffin(ServerPlayer p) {
		List<LivingEntity> bound = new ArrayList<>();
		for (LivingEntity e : around(p, p.position(), COFFIN_RADIUS)) {
			if (boundBy(p, e)) {
				bound.add(e);
			}
		}
		if (bound.isEmpty()) {
			if (cooldownLeft(p, COFFIN) > 0) {
				use(p, COFFIN);
				return -1;
			}
			say(p, "nothing_bound");
			return 0;
		}
		if (!use(p, COFFIN)) {
			return -1;
		}
		anim(p, ANIM_COFFIN);
		ServerLevel l = level(p);
		for (LivingEntity e : bound) {
			unbind(p, e);
			e.invulnerableTime = 0;
			hurt(p, e, COFFIN_DAMAGE, false);
			Vec3 c = centre(e);
			sand(l, c, 40, Math.max(0.4, e.getBbWidth() * 0.6));
			l.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 10, 0.3, 0.4, 0.3, 0.2);
			QuirkFx.ring(l, c).style(QuirkFx.RING_SOFT).colours(0xF4EED2, BANDAGE).size(Math.max(1.0f, e.getBbWidth() * 1.4f)).thickness(0.5f).life(8).spawn();
			OmnitrixSfx.play(l, c, SND_CRUSH, 0.9f, 1f);
		}
		return bound.size();
	}

	/** Hold Z: the Cocoon -- he wraps himself up tight while Z is held (up to 4 s): no moving, nothing hurts him, 1 heart a second. */
	public boolean startCocoon(ServerPlayer p) {
		if (flag(p, F_COCOON)) {
			return false;
		}
		if (!ready(p, COCOON)) {
			return use(p, COCOON);
		}
		endLine(p, false);
		anim(p, ANIM_COCOON);
		setTimer(p, COCOON_AT, p.level().getGameTime());
		setFlag(p, F_COCOON, true);
		sand(level(p), p.position().add(0, 1.0, 0), 20, 0.5);
		OmnitrixSfx.play(level(p), p.position(), SND_WRAP, 0.75f, 0.7f);
		return true;
	}

	private void cocoonTick(ServerPlayer p, ServerLevel l, long now) {
		long age = now - timer(p, COCOON_AT);
		if (age >= COCOON_MAX) {
			endCocoon(p);
			return;
		}
		p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 5, 9, false, false, false));
		p.setDeltaMovement(0, Math.min(0, p.getDeltaMovement().y), 0);
		p.hurtMarked = true;
		if (age > 0 && age % 20 == 0) {
			p.heal(COCOON_HEAL);
			l.sendParticles(ParticleTypes.HEART, p.getX(), p.getY() + p.getBbHeight() + 0.2, p.getZ(), 1, 0.2, 0.1, 0.2, 0);
		}
		if (age % 6 == 0) {
			sand(l, p.position().add(0, 0.2, 0), 3, 0.3);
		}
	}

	/** The cocoon bursts open: 6 within 3, knocked back. */
	public void endCocoon(ServerPlayer p) {
		if (!flag(p, F_COCOON)) {
			return;
		}
		setFlag(p, F_COCOON, false);
		setTimer(p, COCOON_AT, 0L);
		cooldown(p, COCOON, -1);
		p.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
		anim(p, ANIM_BURST);
		ServerLevel l = level(p);
		for (LivingEntity e : around(p, p.position(), COCOON_BURST_RADIUS)) {
			e.invulnerableTime = 0;
			if (hurt(p, e, COCOON_BURST, false)) {
				shove(e, p.position(), 0.8, 0.3);
			}
		}
		sand(l, p.position().add(0, 1.0, 0), 40, 1.0);
		QuirkFx.ring(l, p.position().add(0, 1.0, 0)).style(QuirkFx.RING_SOFT).colours(0xF4EED2, BANDAGE).size((float) COCOON_BURST_RADIUS).thickness(0.4f).life(9)
				.spawn();
		OmnitrixSfx.play(l, p.position(), SND_SAND, 0.75f, 0.9f);
		OmnitrixSfx.play(l, p.position(), SND_LASH, 0.7f, 0.7f);
	}

	// ---------------------------------------------------------------- V / Shift+V

	/** V: he comes apart into loose bandages -- untouchable and quick -- then pulls back together with a burst of sand. */
	public boolean scatter(ServerPlayer p) {
		if (flag(p, F_SCATTER)) {
			reform(p); // a second press pulls him back together early
			return true;
		}
		if (!use(p, SCATTER)) {
			return false;
		}
		endLine(p, false);
		anim(p, ANIM_SCATTER);
		setTimer(p, SCATTER_UNTIL, p.level().getGameTime() + SCATTER_TICKS);
		setFlag(p, F_SCATTER, true);
		p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, SCATTER_TICKS, 2, false, false, false));
		// mobs lose the thread for a moment
		for (LivingEntity e : around(p, p.position(), 16)) {
			if (e instanceof net.minecraft.world.entity.Mob m) {
				AlienStatus.loseTrack(m, p);
			}
		}
		sand(level(p), p.position().add(0, 1.0, 0), 30, 0.6);
		OmnitrixSfx.play(level(p), p.position(), SND_SAND, 0.7f, 1.1f);
		return true;
	}

	private void reform(ServerPlayer p) {
		setFlag(p, F_SCATTER, false);
		setTimer(p, SCATTER_UNTIL, 0L);
		anim(p, ANIM_REFORM);
		p.removeEffect(MobEffects.MOVEMENT_SPEED);
		ServerLevel l = level(p);
		for (LivingEntity e : around(p, p.position(), REFORM_RADIUS)) {
			e.invulnerableTime = 0;
			if (hurt(p, e, REFORM_DAMAGE, false)) {
				shove(e, p.position(), 0.5, 0.25);
			}
		}
		sand(l, p.position().add(0, 1.0, 0), 40, 1.0);
		OmnitrixSfx.play(l, p.position(), SND_SAND, 0.75f, 0.8f);
	}

	/** {@code p} is scattered right now (client-safe). */
	public static boolean scattered(Player p) {
		return isSnareoh(p) && (Omnitrix.state(p).kitFlags & F_SCATTER) != 0;
	}

	/** {@code p} is wrapped in his Cocoon right now (client-safe). */
	public static boolean cocooned(Player p) {
		return isSnareoh(p) && (Omnitrix.state(p).kitFlags & F_COCOON) != 0;
	}

	/**
	 * Shift+V: Bandage Net -- a net of bandages 5 blocks wide and 3 high strung across the crosshair (16 blocks) for 6 s. Whatever touches it
	 * is bound for 1.5 s (once a second and a half each).
	 */
	public boolean bandageNet(ServerPlayer p) {
		if (!use(p, NET)) {
			return false;
		}
		anim(p, ANIM_NET);
		ServerLevel l = level(p);
		Vec3 eye = p.getEyePosition();
		Vec3 hit = aimPoint(p, NET_RANGE);
		Vec3 centre = hit.subtract(p.getLookAngle().scale(Math.min(0.6, hit.distanceTo(eye) * 0.5)));
		// sit the bottom edge on the ground under it, if the ground is near
		BlockHitResult down = l.clip(new ClipContext(centre, centre.subtract(0, NET_HALF_HIGH + 2, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		if (down.getType() == HitResult.Type.BLOCK) {
			centre = new Vec3(centre.x, down.getLocation().y + NET_HALF_HIGH, centre.z);
		}
		Vec3 f = flatLook(p);
		Vec3 right = new Vec3(-f.z, 0, f.x);
		long until = p.level().getGameTime() + NET_TICKS;
		NETS.computeIfAbsent(p.getUUID(), k -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(new Net(centre, right, until, new ConcurrentHashMap<>()));
		// the strands: four across, six down, and the line from his hand that strung it
		for (int i = 0; i < 4; i++) {
			double y = -NET_HALF_HIGH + 0.3 + i * (NET_HALF_HIGH * 2 - 0.6) / 3;
			QuirkFx.ribbon(l, centre.add(right.scale(-NET_HALF_WIDE)).add(0, y, 0), centre.add(right.scale(NET_HALF_WIDE)).add(0, y, 0)).colours(BANDAGE, BANDAGE_DARK)
					.size(0.09f).life(NET_TICKS).spawn();
		}
		for (int i = 0; i < 6; i++) {
			double w = -NET_HALF_WIDE + i * NET_HALF_WIDE * 2 / 5;
			QuirkFx.ribbon(l, centre.add(right.scale(w)).add(0, -NET_HALF_HIGH, 0), centre.add(right.scale(w)).add(0, NET_HALF_HIGH, 0)).colours(BANDAGE, BANDAGE_DARK)
					.size(0.08f).life(NET_TICKS).spawn();
		}
		bandage(p, "snare_oh_whip", centre, null, 9);
		sand(l, centre, 20, 1.2);
		OmnitrixSfx.playOn(p, SND_LASH, 0.75f, 0.9f, SoundSource.PLAYERS);
		OmnitrixSfx.play(l, centre, SND_WRAP, 0.7f, 1.1f);
		return true;
	}

	/** Whether {@code e} touches the net (its middle within reach of the net's plane and inside its edges). */
	private static boolean touches(Net n, LivingEntity e) {
		Vec3 c = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(n.centre());
		Vec3 normal = new Vec3(n.right().z, 0, -n.right().x);
		double across = Math.abs(c.dot(n.right())), through = Math.abs(c.dot(normal)), up = Math.abs(c.y);
		return through <= 0.45 + e.getBbWidth() * 0.5 && across <= NET_HALF_WIDE + e.getBbWidth() * 0.5 && up <= NET_HALF_HIGH + e.getBbHeight() * 0.5;
	}

	private void netTick(ServerPlayer p, ServerLevel l, long now) {
		List<Net> nets = NETS.get(p.getUUID());
		for (Iterator<Net> it = nets.iterator(); it.hasNext();) {
			Net n = it.next();
			if (now >= n.until()) {
				nets.remove(n);
				continue;
			}
			if (now % 2 != 0) {
				continue;
			}
			for (LivingEntity e : around(p, n.centre(), NET_HALF_WIDE + 2)) {
				Long last = n.caught().get(e.getId());
				if ((last == null || now - last >= NET_BIND_TICKS) && touches(n, e)) {
					n.caught().put(e.getId(), now);
					bind(p, e, NET_BIND_TICKS);
					sand(l, centre(e), 8, 0.3);
					OmnitrixSfx.play(l, e.position(), SND_WRAP, 0.6f, 1.2f);
				}
			}
		}
		if (nets.isEmpty()) {
			NETS.remove(p.getUUID());
		}
	}

	/** Test hook: how many of his nets are up. */
	public static int nets(ServerPlayer p) {
		List<Net> n = NETS.get(p.getUUID());
		return n == null ? 0 : n.size();
	}

	// ---------------------------------------------------------------- C / Shift+C / Hold C

	/** C: he spins and his bandages fly loose round him: three lashes. */
	public boolean spin(ServerPlayer p) {
		if (flag(p, F_WHIRL) || !use(p, SPIN)) {
			return false;
		}
		anim(p, ANIM_SPIN);
		setFlag(p, F_SPIN, true);
		setTimer(p, SPIN_AT, p.level().getGameTime());
		SPINS.put(p.getUUID(), 0);
		return true;
	}

	private void spinTick(ServerPlayer p, ServerLevel l, long now) {
		long start = timer(p, SPIN_AT);
		int done = SPINS.getOrDefault(p.getUUID(), 0);
		if (done >= SPIN_HITS) {
			if (now - start >= SPIN_HITS * SPIN_GAP + 2) {
				setFlag(p, F_SPIN, false);
				setTimer(p, SPIN_AT, 0L);
				SPINS.remove(p.getUUID());
			}
			return;
		}
		if (now - start < done * SPIN_GAP) {
			return;
		}
		SPINS.put(p.getUUID(), done + 1);
		lashRound(p, l, SPIN_RADIUS, SPIN_DAMAGE, done == SPIN_HITS - 1 ? 0.9 : 0.3);
		OmnitrixSfx.play(l, p.position(), SND_LASH, 0.6f, 0.9f + done * 0.15f);
	}

	/** One lash of his loose bandages right round him. */
	private void lashRound(ServerPlayer p, ServerLevel l, double radius, float damage, double knock) {
		for (LivingEntity e : around(p, p.position(), radius)) {
			if (Math.abs(e.getY() - p.getY()) > 2.5) {
				continue;
			}
			e.invulnerableTime = 0;
			if (hurt(p, e, damage, false)) {
				shove(e, p.position(), knock, 0.2);
			}
		}
		l.sendParticles(ParticleTypes.SWEEP_ATTACK, p.getX(), p.getY() + 1.0, p.getZ(), 3, radius * 0.33, 0.2, radius * 0.33, 0);
	}

	/** Shift+C: Sand Gust -- he breathes out a gust of tomb sand (an 8-block cone): 9, blinded 2 s. Returns how many it hit. */
	public int sandGust(ServerPlayer p) {
		if (!use(p, GUST)) {
			return -1;
		}
		anim(p, ANIM_GUST);
		ServerLevel l = level(p);
		int n = 0;
		for (LivingEntity e : RecalKitMath.cone(p, GUST_RANGE, GUST_HALF_ANGLE)) {
			e.invulnerableTime = 0;
			if (hurt(p, e, GUST_DAMAGE, false)) {
				e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, GUST_BLIND, 0), p);
				shove(e, p.position(), 0.5, 0.15);
				n++;
			}
		}
		// the gust: sand blown out of his mouth along the cone (count 0: the offsets are a velocity)
		Vec3 m = p.getEyePosition().add(p.getLookAngle().scale(0.4)).add(0, -0.15, 0);
		Vec3 look = p.getLookAngle();
		double fan = Math.tan(Math.toRadians(GUST_HALF_ANGLE)) * 0.9;
		BlockParticleOption dust = new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.SAND.defaultBlockState());
		for (int i = 0; i < 40; i++) {
			Vec3 v = look.add((l.random.nextDouble() - 0.5) * fan, (l.random.nextDouble() - 0.5) * fan, (l.random.nextDouble() - 0.5) * fan).normalize();
			l.sendParticles(i % 4 == 0 ? ParticleTypes.CLOUD : dust, m.x, m.y, m.z, 0, v.x, v.y, v.z, i % 4 == 0 ? 0.3 : 0.7);
		}
		for (int i = 0; i < 12; i++) {
			double d = 1.5 + l.random.nextDouble() * GUST_RANGE * 0.85;
			Vec3 at = m.add(look.scale(d));
			sand(l, at, 3, d * 0.25);
		}
		OmnitrixSfx.play(l, m, SND_SAND, 0.75f, 0.7f);
		OmnitrixSfx.play(l, m, SND_MOAN, 0.5f, 1.2f);
		return n;
	}

	/** Hold C: Whirlwind -- he spins on while C is held (up to 3 s) and can keep walking: 4 every half second round him. */
	public boolean startWhirl(ServerPlayer p) {
		if (flag(p, F_WHIRL) || flag(p, F_SPIN)) {
			return false;
		}
		if (!ready(p, WHIRL)) {
			return use(p, WHIRL);
		}
		anim(p, ANIM_WHIRL);
		setTimer(p, WHIRL_AT, p.level().getGameTime());
		setFlag(p, F_WHIRL, true);
		return true;
	}

	private void whirlTick(ServerPlayer p, ServerLevel l, long now) {
		long age = now - timer(p, WHIRL_AT);
		if (age >= WHIRL_MAX) {
			endWhirl(p);
			return;
		}
		if (age % WHIRL_EVERY == 0) {
			lashRound(p, l, WHIRL_RADIUS, WHIRL_DAMAGE, 0.35);
			OmnitrixSfx.play(l, p.position(), SND_LASH, 0.55f, 0.9f + (age / WHIRL_EVERY % 3) * 0.1f);
		}
		if (age % 3 == 0) {
			sand(l, p.position().add(0, 0.3, 0), 4, 1.2);
		}
	}

	public void endWhirl(ServerPlayer p) {
		if (!flag(p, F_WHIRL)) {
			return;
		}
		setFlag(p, F_WHIRL, false);
		setTimer(p, WHIRL_AT, 0L);
		cooldown(p, WHIRL, -1);
		anim(p, ANIM_REFORM);
	}
}
