package com.projecthero.mod.omnitrix.alien.snareoh;

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
import com.projecthero.mod.omnitrix.data.OmnitrixState;
import com.projecthero.mod.omnitrix.entity.AlienProjectileEntity;

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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 (user: "make aliens based on their abilities and stuff they do in the show please"). Snare-oh, a Thep Khufan (prototype
 * Omnitrix, a hidden alien found by a mistransform): a mummy whose bandages stretch, lash and wrap whatever they reach. His body is hollow
 * and full of sand: he can send a hand off on its own, or come apart into loose bandages for a moment and put himself back together.
 *
 * <pre>
 *   R          Bandage Whip       a bandage lashes out at the crosshair (8 blocks): 7, knocked back
 *   Shift+R    Bandage Bind       a bandage wraps the creature you look at (14): 6, bound in place 3 s (a boss 0.75 s)
 *   G          Grapple Pull       a bandage catches the creature you look at (16) and yanks it to you: 8 (a boss pulls you to it)
 *   X          Bandage Swing      HOLD: a bandage to the block under the crosshair (36; nothing there: up and ahead), swing on it while X is
 *                                 held (Spidermonkey's rope: Jump reels in, Sneak pays out); letting go flings you on
 *   Shift+X    Bandage Zip        a bandage to the block under the crosshair (40), and it reels you straight in
 *   Z          Mummy Wrap         bandages burst out of him at everything within 7: 16, bound 2.5 s
 *   C          Unravel Spin       he spins, his bandages flying loose round him (4.5 blocks): three lashes of 5 (15), knocked back
 *   V          Detach             a hand flies off on a bandage to the crosshair (20): 8, and it drags what it catches back to you
 *   Shift+V    Scatter            he comes apart into loose bandages for 1.5 s -- nothing touches him, he slips along fast -- and
 *                                 pulls himself back together in a burst of sand: 6 within 3
 *   Passives   Hollow Body (shots and arrows pass through his empty wrappings: half damage); Sand Filled (no fall damage under 8 blocks, no
 *              suffocating, no drowning)
 *   Weakness   Dry Wrappings (fire does half again, and burns longer)
 * </pre>
 */
public final class SnareohKit extends AlienKit {
	public static final String ID = "snare_oh";
	/** His bandages and the dust off them. */
	public static final int BANDAGE = 0xB4AE7E, GLOW = 0x71D32A;

	// ---------------------------------------------------------------- synced flags / poses
	public static final int F_SWING = 1, F_ZIP = 2, F_AIR = 4, F_HAND = 8, F_SCATTER = 16, F_SPIN = 32;
	public static final int ANIM_WHIP = 1, ANIM_BIND = 2, ANIM_PULL = 3, ANIM_SWING = 4, ANIM_ZIP = 5, ANIM_WRAP = 6, ANIM_SPIN = 7, ANIM_HAND = 8,
			ANIM_SCATTER = 9, ANIM_REFORM = 10, ANIM_PUNCH = 11, ANIM_CATCH = 12;

	// ---------------------------------------------------------------- moves (lang projecthero.omnitrix.snare_oh.<id>)
	public static final String WHIP = "bandage_whip", BIND = "bandage_bind", PULL = "grapple_pull", SWING = "bandage_swing", ZIP = "bandage_zip",
			WRAP = "mummy_wrap", SPIN = "unravel_spin", HAND = "detach", SCATTER = "scatter", HOLLOW = "hollow_body", SAND = "sand_filled",
			DRY = "dry_wrappings";

	/** The synced line the owning client moves him on (Spidermonkey's keys): 1 = swing, 2 = zip; the hook and rope in thousandths. */
	public static final String LINE = "line", LINE_AT = "line_at", HOOK_X = "hook_x", HOOK_Y = "hook_y", HOOK_Z = "hook_z", ROPE = "rope";
	private static final String[] LINE_KEYS = { LINE, LINE_AT, HOOK_X, HOOK_Y, HOOK_Z, ROPE };
	public static final String SCATTER_UNTIL = "scatter_until", SPIN_AT = "spin_at";

	// ---------------------------------------------------------------- numbers (basic 5-8, specials 12-17, big 17-20, ultimates 21-36)
	public static final float WHIP_DAMAGE = 7f, BIND_DAMAGE = 6f, PULL_DAMAGE = 8f, WRAP_DAMAGE = 16f, SPIN_DAMAGE = 5f, HAND_DAMAGE = 8f, REFORM_DAMAGE = 6f;
	public static final float SHOT_TAKEN = 0.5f, FIRE_TAKEN = 1.5f, SAFE_FALL = 8f;
	public static final int BIND_TICKS = 60, WRAP_TICKS = 50, SPIN_HITS = 3, SPIN_GAP = 5, SCATTER_TICKS = 30, HAND_MAX = 40, ZIP_MAX = 50, SWING_MAX = 600;
	public static final double WHIP_RANGE = 8, BIND_RANGE = 14, PULL_RANGE = 16, SWING_RANGE = 36, ZIP_RANGE = 40, WRAP_RADIUS = 7, SPIN_RADIUS = 4.5,
			HAND_RANGE = 20, HAND_SPEED = 1.6, REFORM_RADIUS = 3, ZIP_SPEED = 1.8, ZIP_ARRIVE = 1.8, SWING_SLACK_BREAK = 14.0;

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

	// ---------------------------------------------------------------- server-only state
	/** A swing (mode 1) or zip (mode 2) line: the hook, the block it is stuck to, the rope, when, its drawn bandage. */
	private record Line(int mode, Vec3 hook, BlockPos block, double rope, long start, AlienProjectileEntity strand) {
	}

	/** The flying hand: its carrier, where it is headed, what it caught, when it left; {@code back} once it turns home. */
	private static final class Hand {
		final AlienProjectileEntity carrier;
		final Vec3 aim;
		final long start;
		@Nullable
		LivingEntity caught;
		boolean back;

		Hand(AlienProjectileEntity carrier, Vec3 aim, long start) {
			this.carrier = carrier;
			this.aim = aim;
			this.start = start;
		}
	}

	private static final Map<UUID, Line> LINES = new ConcurrentHashMap<>();
	private static final Map<UUID, Hand> HANDS = new ConcurrentHashMap<>();
	private static final Map<UUID, Integer> SPINS = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_TICK = new ConcurrentHashMap<>();

	private SnareohKit() {
	}

	public static void register() {
		Aliens.register(new SnareohKit());
	}

	public static void clearSessionState() {
		LINES.clear();
		HANDS.clear();
		SPINS.clear();
		LAST_TICK.clear();
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
		return List.of(AlienMove.tap(WHIP, AbilitySlot.SLOT_1, 12), AlienMove.shift(BIND, AbilitySlot.SLOT_1, 160),
				AlienMove.tap(PULL, AbilitySlot.SLOT_2, 100),
				AlienMove.tap(SWING, AbilitySlot.SLOT_3, 10), AlienMove.shift(ZIP, AbilitySlot.SLOT_3, 40),
				AlienMove.tap(WRAP, AbilitySlot.SLOT_4, 500),
				AlienMove.tap(HAND, AbilitySlot.SLOT_5, 120), AlienMove.shift(SCATTER, AbilitySlot.SLOT_5, 240),
				AlienMove.tap(SPIN, AbilitySlot.SLOT_6, 120),
				AlienMove.vanilla(HOLLOW, "Passive", 0), AlienMove.vanilla(SAND, "Passive", 0), AlienMove.vanilla(DRY, "Weakness", 0));
	}

	@Override
	public boolean keyActive(Player p, AbilitySlot slot) {
		return switch (slot) {
			case SLOT_3 -> flag(p, F_SWING) || flag(p, F_ZIP);
			case SLOT_5 -> flag(p, F_HAND) || flag(p, F_SCATTER);
			case SLOT_6 -> flag(p, F_SPIN);
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

	/** {@code e} wrapped up: bound {@code ticks} (a boss a quarter of it), the wrap drawn round it. */
	private void bind(ServerPlayer p, LivingEntity e, int ticks) {
		AlienStatus.root(p, e, ticks, 20);
		int t = AlienStatus.isBoss(e) ? Math.max(5, ticks / 4) : ticks;
		bandage(p, "snare_oh_wrap", centre(e), e, t);
	}

	private static void sand(ServerLevel l, Vec3 at, int n, double spread) {
		l.sendParticles(new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.SAND.defaultBlockState()), at.x, at.y, at.z, n, spread, spread * 0.6, spread, 0.02);
	}

	// ---------------------------------------------------------------- lifecycle

	@Override
	public void onTransform(ServerPlayer p) {
		OmnitrixSfx.play(level(p), p.position(), SND_MOAN, 0.6f, 1f);
	}

	@Override
	public void onRevert(ServerPlayer p) {
		endLine(p, false);
		endHand(p);
		UUID id = p.getUUID();
		SPINS.remove(id);
		LAST_TICK.remove(id);
		OmnitrixState n = Omnitrix.state(p).copy();
		for (String k : LINE_KEYS) {
			n.timers.remove(key(k));
		}
		n.timers.remove(key(SCATTER_UNTIL));
		n.timers.remove(key(SPIN_AT));
		n.kitFlags = 0;
		Omnitrix.save(p, n);
		p.setInvisible(false);
	}

	@Override
	public boolean allowDamage(ServerPlayer p, DamageSource source, float amount) {
		if (flag(p, F_SCATTER) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return false; // nothing to hit: he is a cloud of loose bandages
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
		if (flag(p, F_HAND)) {
			handTick(p, l, now);
		}
		if (flag(p, F_SPIN)) {
			spinTick(p, l, now);
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
		if (flag(p, F_SCATTER) && !(slot == AbilitySlot.SLOT_5 && shift)) {
			return; // he is all over the place: nothing to throw a punch with
		}
		switch (slot) {
			case SLOT_1 -> {
				if (shift) {
					bindMove(p);
				} else {
					whip(p);
				}
			}
			case SLOT_2 -> pull(p);
			case SLOT_3 -> {
				if (shift) {
					zip(p);
				} else {
					swing(p);
				}
			}
			case SLOT_4 -> mummyWrap(p);
			case SLOT_5 -> {
				if (shift) {
					scatter(p);
				} else {
					detach(p);
				}
			}
			case SLOT_6 -> spin(p);
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

	// ---------------------------------------------------------------- R / Shift+R

	/** R: a bandage lashes out at the crosshair and snaps back. */
	public boolean whip(ServerPlayer p) {
		if (!use(p, WHIP)) {
			return false;
		}
		anim(p, ANIM_WHIP);
		LivingEntity t = lookTarget(p, WHIP_RANGE);
		Vec3 end = t != null ? centre(t) : aimPoint(p, WHIP_RANGE);
		if (t != null) {
			t.invulnerableTime = 0;
			if (hurt(p, t, WHIP_DAMAGE, false)) {
				shove(t, p.position(), 0.55, 0.2);
			}
			level(p).sendParticles(ParticleTypes.SWEEP_ATTACK, end.x, end.y, end.z, 1, 0, 0, 0, 0);
		}
		bandage(p, "snare_oh_whip", end, null, 9);
		OmnitrixSfx.playOn(p, SND_LASH, 0.7f, 1.1f, SoundSource.PLAYERS);
		return true;
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

	// ---------------------------------------------------------------- G

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

	// ---------------------------------------------------------------- X / Shift+X (Spidermonkey's line)

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

	/** X (held): a bandage up to the block under the crosshair (or up and ahead), and he swings on it while X is held. */
	public boolean swing(ServerPlayer p) {
		Vec3 look = p.getLookAngle();
		BlockHitResult hit = blockAlong(p, look, SWING_RANGE);
		if (hit == null || hit.getLocation().y < p.getY() + 1.0) {
			Vec3 flat = look.multiply(1, 0, 1);
			flat = flat.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : flat.normalize();
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

	/** Shift+X: a bandage to the block under the crosshair, and it reels him straight in. */
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

	// ---------------------------------------------------------------- Z

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

	// ---------------------------------------------------------------- C

	/** C: he spins and his bandages fly loose round him: three lashes. */
	public boolean spin(ServerPlayer p) {
		if (!use(p, SPIN)) {
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
		for (LivingEntity e : around(p, p.position(), SPIN_RADIUS)) {
			if (Math.abs(e.getY() - p.getY()) > 2.5) {
				continue;
			}
			e.invulnerableTime = 0;
			if (hurt(p, e, SPIN_DAMAGE, false)) {
				shove(e, p.position(), done == SPIN_HITS - 1 ? 0.9 : 0.3, 0.2);
			}
		}
		l.sendParticles(ParticleTypes.SWEEP_ATTACK, p.getX(), p.getY() + 1.0, p.getZ(), 3, 1.5, 0.2, 1.5, 0);
		OmnitrixSfx.play(l, p.position(), SND_LASH, 0.6f, 0.9f + done * 0.15f);
	}

	// ---------------------------------------------------------------- V / Shift+V

	/** V: a hand flies off on its bandage to the crosshair; it drags what it catches back to him. */
	public boolean detach(ServerPlayer p) {
		if (flag(p, F_HAND)) {
			return false;
		}
		if (!use(p, HAND)) {
			return false;
		}
		anim(p, ANIM_HAND);
		LivingEntity t = lookTarget(p, HAND_RANGE);
		Vec3 aim = t != null ? centre(t) : aimPoint(p, HAND_RANGE);
		Vec3 from = p.getEyePosition().add(p.getLookAngle().scale(0.6)).add(0, -0.35, 0);
		AlienProjectileEntity c = AlienProjectileEntity.of(p, "snare_oh_hand");
		c.moveTo(from.x, from.y, from.z, p.getYRot(), p.getXRot());
		c.size(1f).held(true).speed(0).life(HAND_MAX + 20).damage(0f).spawn();
		HANDS.put(p.getUUID(), new Hand(c, aim, p.level().getGameTime()));
		setFlag(p, F_HAND, true);
		OmnitrixSfx.playOn(p, SND_LASH, 0.6f, 1.3f, SoundSource.PLAYERS);
		return true;
	}

	private void handTick(ServerPlayer p, ServerLevel l, long now) {
		Hand h = HANDS.get(p.getUUID());
		if (h == null || !h.carrier.isAlive() || h.carrier.level() != p.level()) {
			endHand(p);
			return;
		}
		Vec3 at = h.carrier.position();
		Vec3 home = p.getEyePosition().add(0, -0.4, 0);
		if (!h.back) {
			Vec3 to = h.aim.subtract(at);
			double d = to.length();
			Vec3 next = d <= HAND_SPEED ? h.aim : at.add(to.scale(HAND_SPEED / d));
			h.carrier.setPos(next.x, next.y, next.z);
			// anything it passes is caught
			for (LivingEntity e : around(p, next, 1.0)) {
				e.invulnerableTime = 0;
				hurt(p, e, HAND_DAMAGE, false);
				h.caught = AlienStatus.isBoss(e) ? null : e;
				anim(p, ANIM_CATCH);
				OmnitrixSfx.play(l, next, SND_WRAP, 0.6f, 1.3f);
				h.back = true;
				break;
			}
			if (d <= HAND_SPEED || now - h.start > HAND_MAX / 2) {
				h.back = true;
			}
			return;
		}
		Vec3 to = home.subtract(at);
		double d = to.length();
		if (d <= HAND_SPEED + 0.6 || now - h.start > HAND_MAX) {
			endHand(p);
			return;
		}
		Vec3 next = at.add(to.scale(HAND_SPEED / d));
		h.carrier.setPos(next.x, next.y, next.z);
		if (h.caught != null && h.caught.isAlive()) {
			// dragged along behind the hand, dropped at his feet
			Vec3 drag = next.subtract(0, h.caught.getBbHeight() * 0.5, 0);
			if (drag.distanceTo(p.position()) > 1.6) {
				h.caught.teleportTo(drag.x, drag.y, drag.z);
				h.caught.setDeltaMovement(Vec3.ZERO);
				h.caught.fallDistance = 0f;
				h.caught.hurtMarked = true;
			}
		}
	}

	private void endHand(ServerPlayer p) {
		Hand h = HANDS.remove(p.getUUID());
		if (h != null) {
			h.carrier.discard();
		}
		if (flag(p, F_HAND)) {
			setFlag(p, F_HAND, false);
		}
	}

	/** Test hook: the creature the flying hand holds, or null. */
	@Nullable
	public static LivingEntity handHolds(ServerPlayer p) {
		Hand h = HANDS.get(p.getUUID());
		return h == null ? null : h.caught;
	}

	/** Shift+V: he comes apart into loose bandages -- untouchable and quick -- then pulls back together with a burst of sand. */
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
		p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, SCATTER_TICKS, 2, false, false, false));
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
		p.removeEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED);
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
}
