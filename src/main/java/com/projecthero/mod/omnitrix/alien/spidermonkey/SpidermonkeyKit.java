package com.projecthero.mod.omnitrix.alien.spidermonkey;

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
import com.projecthero.mod.quirk.fx.QuirkCamera;
import com.projecthero.mod.quirk.fx.QuirkFx;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2: Spidermonkey, the Arachnichimp (Recalibrated Omnitrix). v0.17.2 revamp (docs/OMNITRIX_REVAMP_SPIDERMONKEY.md; user: "can
 * you work on revamps for the rest of the alien force aliens please, look at their powers and abilities in the show and do research
 * and make them good please"): a four-armed monkey acrobat who spins web from his tail. Where Spider-Man is a city swinger, he is a
 * jungle gym: chained swings (Monkey Bars), ceiling hangs, a swing kick off a web line, and four fists. No Ultimate Spidermonkey.
 *
 * <pre>
 *   R          Web Shot        a glob of web from his tail: 6, rooted 2 s, 1 s
 *   Shift+R    Web Net         a spinning net (r 2.5): 12, rooted 3 s, 8 s
 *   G          Quad Flurry     four fists in front: 4 x 3.5 (+1 each on a webbed foe), 5 s
 *   Shift+G    Web Yank        a line at a creature (16): 12, reeled in -- in the air or on a wall he instead swings in on it for a
 *                              Swing Kick (15 + knocked flying), 8 s
 *   X          Web Swing       HOLD: a line from his tail to the block under the crosshair (50; nothing there: ahead and above), a
 *                              pendulum while X is held (Jump reels in, Sneak pays out), let go to fling; re-catch in the air within 2 s
 *                              = no cooldown (Monkey Bars), 1 s
 *   Shift+X    Web Zip         a line to a block (75): pulled straight to it at 2.2 blocks a tick (44 b/s), a hop up at the end, 2 s
 *   Z          Tail Whip       a spin (r 3.2): 13 + thrown (webbed foes torn loose and thrown twice as far), 6 s
 *   Shift+Z    Cocoon Crush    ULTIMATE: wraps a creature (16): rooted 5 s, squeezed 5 every 0.5 s (50), then slammed: 14 + launched
 *                              (64), 45 s
 *   C          Web Trap        a web on the ground (r 2.5, 15 s, two at a time): 5 + rooted 3 s, 15 s
 *   Shift+C    Web Barrage     six webs in a fan: 5 each, rooted 1.5 s, 10 s
 *   V          Wall Crawl      toggle (on when he transforms): climbs walls; Sneak under a ceiling = Ceiling Hang
 *   Shift+V    Web Shield      a web wall in front, 8 s: shots stop dead, creatures wade through, 20 s
 *   Attack     Four Fists      every 4th punch within 2 s lands all four: +4 and a shove
 *   Passives   Wall Crawl + Ceiling Hang; nimble (x1.25 speed, a better jump, safe fall +4, no fall damage after his lines);
 *              Sticky (webbed foes take +1 from his fists)
 *   Weak       fire (x1.5; a burning creature can't be webbed); Tangled (his own ground web slows him); light frame (22 HP, armour 2)
 * </pre>
 *
 * <p>v0.17.2 (user: "make spidermonkeys webs shoot out from his tail, thats how it works in the show, increase his web zip range to 75
 * blocks please and make the pull stronger aswell, also make the web swings range further aswell, i think it would actually be better
 * if it functioned the same as ladybugs yoyo swing and yoyo zip line thing, make the swing x and web zip shift+X please"): X / Shift+X
 * are Ladybug's Yo-yo Swing / Zip Line ({@code LadybugMoves.lineDown}) -- the server picks the hook, writes it into the synced state
 * ({@link #lineMode}, {@link #hook}, {@link #rope}) and ends the line; the owning client moves him ({@code SpidermonkeyLineClient}:
 * Spider-Man's rope model for the swing, a straight pull at {@link #ZIP_SPEED} for the zip). Every web leaves from his tail tip
 * ({@link #tailTip}).
 *
 * <p>Webs are drawn by the client from held {@link AlienProjectileEntity} carriers: {@code spidermonkey_strand} sits at a strand's
 * far end (the anchor) and draws a line to its owner's tail (colour 0) or to creature colour - 1 ({@code spidermonkey_wrap} wraps
 * creature colour - 1), {@code spidermonkey_trap} a ground web of radius size, {@code spidermonkey_shield} the web wall (its yaw).
 */
public final class SpidermonkeyKit extends AlienKit {
	public static final String ID = Aliens.SPIDERMONKEY.id();

	// ---------------------------------------------------------------- sounds
	public static final OmnitrixSfx.Cue THWIP = OmnitrixSfx.cue("spidermonkey_thwip")
			.layer(SoundEvents.SLIME_SQUISH_SMALL, 0.3f, 1.7f)
			.layer(SoundEvents.FISHING_BOBBER_THROW, 0.3f, 1.4f)
			.gap(2);
	public static final OmnitrixSfx.Cue NET = OmnitrixSfx.cue("spidermonkey_net")
			.layer(SoundEvents.FISHING_BOBBER_THROW, 0.35f, 0.9f)
			.layer(SoundEvents.SLIME_SQUISH, 0.3f, 1.3f)
			.layer(SoundEvents.WOOL_PLACE, 0.3f, 0.8f, 3);
	public static final OmnitrixSfx.Cue STICK = OmnitrixSfx.cue("spidermonkey_stick")
			.layer(SoundEvents.SLIME_SQUISH_SMALL, 0.35f, 1.2f)
			.layer(SoundEvents.COBWEB_PLACE, 0.3f, 1.2f);
	public static final OmnitrixSfx.Cue CHATTER = OmnitrixSfx.cue("spidermonkey_chatter")
			.layer(SoundEvents.SPIDER_AMBIENT, 0.2f, 1.7f)
			.layer(SoundEvents.FOX_SCREECH, 0.15f, 1.6f, 1)
			.gap(10);
	public static final OmnitrixSfx.Cue PUNCH = OmnitrixSfx.cue("spidermonkey_punch")
			.layer(SoundEvents.PLAYER_ATTACK_WEAK, 0.35f, 1.4f)
			.layer(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.2f, 1.6f)
			.gap(1);
	public static final OmnitrixSfx.Cue WHIP = OmnitrixSfx.cue("spidermonkey_whip")
			.layer(SoundEvents.PLAYER_ATTACK_SWEEP, 0.4f, 1.3f)
			.layer(SoundEvents.SPIDER_AMBIENT, 0.18f, 1.5f);
	public static final OmnitrixSfx.Cue ZIP = OmnitrixSfx.cue("spidermonkey_zip")
			.layer(SoundEvents.FISHING_BOBBER_RETRIEVE, 0.4f, 1.3f)
			.layer(SoundEvents.FISHING_BOBBER_THROW, 0.3f, 1.6f);
	public static final OmnitrixSfx.Cue SQUEEZE = OmnitrixSfx.cue("spidermonkey_squeeze")
			.layer(SoundEvents.WOOL_BREAK, 0.4f, 0.8f)
			.layer(SoundEvents.SLIME_SQUISH, 0.25f, 0.8f)
			.gap(6);
	// v0.17.2 revamp
	public static final OmnitrixSfx.Cue KICK = OmnitrixSfx.cue("spidermonkey_kick")
			.layer(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.45f, 1.1f)
			.layer(SoundEvents.PLAYER_ATTACK_STRONG, 0.3f, 1.3f)
			.layer(SoundEvents.FOX_SCREECH, 0.12f, 1.8f, 2);
	public static final OmnitrixSfx.Cue SLAM = OmnitrixSfx.cue("spidermonkey_slam")
			.layer(SoundEvents.WOOL_BREAK, 0.5f, 0.6f)
			.layer(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.4f, 0.7f)
			.layer(SoundEvents.GENERIC_SMALL_FALL, 0.4f, 0.6f, 2);

	// ---------------------------------------------------------------- poses, flags, moves
	public static final int ANIM_SHOT = 1, ANIM_NET = 2, ANIM_FLURRY = 3, ANIM_YANK = 4, ANIM_ZIP = 5, ANIM_SWING = 6, ANIM_WHIP = 7, ANIM_TRAP = 8,
			ANIM_CRAWL = 9, ANIM_SHIELD = 10, ANIM_COCOON = 11, ANIM_BARRAGE = 12, ANIM_KICK = 13, ANIM_FISTS = 14, ANIM_SLAM = 15;

	/** Wall Crawl is on ({@link #climbs}). */
	public static final int F_CRAWL = 1;
	/** Swinging on a strand. */
	public static final int F_SWING = 2;
	/** Airborne after a zip or swing: no fall damage until he lands. */
	public static final int F_AIR = 4;
	/** v0.17.2 revamp: flying in on a line for a Swing Kick. */
	public static final int F_KICK = 8;
	/** v0.17.2 (user: Ladybug's zip line): being pulled along a Web Zip line. */
	public static final int F_ZIP = 16;

	public static final String WEB_SHOT = "web_shot", WEB_NET = "web_net", QUAD_FLURRY = "quad_flurry", WEB_YANK = "web_yank", WEB_ZIP = "web_zip",
			WEB_SWING = "web_swing", TAIL_WHIP = "tail_whip", WEB_TRAP = "web_trap", WALL_CRAWL = "wall_crawl", WEB_SHIELD = "web_shield",
			COCOON_CRUSH = "cocoon_crush", WEB_BARRAGE = "web_barrage", FOUR_FISTS = "four_fists", CEILING_HANG = "ceiling_hang";

	public static final String AIR_AT = "air_at", SWING_FREE_UNTIL = "swing_free_until";
	/**
	 * v0.17.2 (user: Ladybug's yo-yo swing / zip line): the synced line the owning client moves him on -- {@code line} 1 = swing, 2 = zip;
	 * {@code line_at} when it was cast; the hook and rope in thousandths of a block.
	 */
	public static final String LINE = "line", LINE_AT = "line_at", HOOK_X = "hook_x", HOOK_Y = "hook_y", HOOK_Z = "hook_z", ROPE = "rope";

	public static final float SHOT_DAMAGE = 6f, NET_DAMAGE = 12f, FLURRY_HIT = 3.5f, YANK_DAMAGE = 12f, KICK_DAMAGE = 15f, WHIP_DAMAGE = 13f,
			TRAP_DAMAGE = 5f, COCOON_PULSE_DAMAGE = 5f, COCOON_SLAM = 14f, BARRAGE_DAMAGE = 5f, FISTS_BONUS = 4f, STICKY_BONUS = 1f;
	public static final int SHOT_ROOT = 40, NET_ROOT = 60, FLURRY_HITS = 4, FLURRY_GAP = 2, TRAP_TICKS = 300, TRAP_ROOT = 60, SHIELD_TICKS = 160,
			COCOON_TICKS = 100, COCOON_PULSE = 10, BARRAGE_ROOT = 30, SWING_MAX = 600, SWING_FREE = 40, KICK_MAX = 24, FISTS_WINDOW = 40,
			TANGLE_TICKS = 30;
	// v0.17.2 (user: "increase his web zip range to 75 blocks please and make the pull stronger aswell, also make the web swings range
	// further aswell"): Zip 28 -> 75 (Ladybug's Zip Line), Swing 24 -> 50 (past Ladybug's 45); the zip pulls at 2.2 blocks a tick
	// (Ladybug 1.6) the whole way instead of one 2.4 shove that died off
	public static final double FLURRY_RANGE = 3.2, YANK_RANGE = 16, ZIP_RANGE = 75, SWING_RANGE = 50, WHIP_RADIUS = 3.2, TRAP_RADIUS = 2.5,
			TRAP_RANGE = 12, COCOON_RANGE = 16, NET_RADIUS = 2.5;
	/** v0.17.2: the zip line's pull (blocks a tick) and where it lets go (blocks from the hook); its longest pull (ticks). */
	public static final double ZIP_SPEED = 2.2, ZIP_ARRIVE = 1.8;
	public static final int ZIP_MAX = 50;
	/** v0.17.2: a swing line snaps when he is this much further from the hook than its rope (Ladybug's SWING_SLACK_BREAK). */
	public static final double SWING_SLACK_BREAK = 14.0;
	public static final int SWING_COOLDOWN = 20, ZIP_COOLDOWN = 40;
	/** v0.17.2: how far his webs aim (the crosshair's creature or block, else this far along the look). */
	public static final double AIM_RANGE = 48.0;
	/**
	 * v0.17.2 (user: "make spidermonkeys webs shoot out from his tail"): his tail tip at rest per unit of scale -- up from his feet and
	 * back from his middle (SpidermonkeyBody's idle curl: the tip 6.45 px down his back and 7.8 px behind it, at the renderer's 0.9375).
	 */
	public static final double TAIL_UP = 1.03, TAIL_BACK = 0.46;
	public static final float FIRE_TAKEN = 1.5f;

	// ---------------------------------------------------------------- per-player state (cleared in onRevert / clearSessionState)
	private static final Map<UUID, Flurry> FLURRIES = new ConcurrentHashMap<>();
	/** v0.17.2: player -> his swing / zip line (Ladybug's line). */
	private static final Map<UUID, Line> LINES = new ConcurrentHashMap<>();
	private static final Map<UUID, List<Trap>> TRAPS = new ConcurrentHashMap<>();
	private static final Map<UUID, Shield> SHIELDS = new ConcurrentHashMap<>();
	/** creature -> its cocoon. */
	private static final Map<UUID, Cocoon> COCOONS = new ConcurrentHashMap<>();
	/** v0.17.2 revamp: player -> the Swing Kick in flight. */
	private static final Map<UUID, Kick> KICKS = new ConcurrentHashMap<>();
	/** v0.17.2 revamp: player -> his punch count (Four Fists). */
	private static final Map<UUID, long[]> PUNCHES = new ConcurrentHashMap<>();
	/** v0.17.2 revamp: once per game tick (flurry, cocoon pulses -- RipjawsKit.tick). */
	private static final Map<UUID, Long> LAST_TICK = new ConcurrentHashMap<>();

	private static final class Flurry {
		int left = FLURRY_HITS;
		long next;
	}

	/** v0.17.2: a swing (mode 1) or zip (mode 2) line: the hook, the block it is stuck to, the rope, when, its drawn strand. */
	private record Line(int mode, Vec3 hook, BlockPos block, double rope, long start, AlienProjectileEntity strand) {
	}

	private record Trap(ResourceKey<Level> dim, Vec3 at, long until, AlienProjectileEntity web, Set<UUID> caught) {
	}

	private record Shield(ResourceKey<Level> dim, Vec3 centre, Vec3 normal, long until, AlienProjectileEntity web) {
	}

	private record Kick(LivingEntity target, long until, AlienProjectileEntity strand) {
	}

	private static final class Cocoon {
		final UUID owner;
		final LivingEntity target;
		final long until;
		long next;
		final AlienProjectileEntity wrap;

		Cocoon(UUID owner, LivingEntity target, long until, long next, AlienProjectileEntity wrap) {
			this.owner = owner;
			this.target = target;
			this.until = until;
			this.next = next;
			this.wrap = wrap;
		}
	}

	public static void register() {
		Aliens.register(new SpidermonkeyKit());
		AlienProjectileEntity.onHit("spidermonkey_web", (proj, hit) -> {
			if (proj.getOwner() instanceof ServerPlayer owner && hit instanceof EntityHitResult eh && eh.getEntity() instanceof LivingEntity le) {
				webbed(owner, le, proj.colour() > 0 ? proj.colour() : SHOT_ROOT);
			}
		});
		AlienProjectileEntity.onHit("spidermonkey_net", (proj, hit) -> {
			if (proj.getOwner() instanceof ServerPlayer owner) {
				for (LivingEntity e : around(owner, hit.getLocation(), NET_RADIUS)) {
					webbed(owner, e, NET_ROOT);
				}
				if (proj.level() instanceof ServerLevel level) {
					Vec3 at = hit.getLocation();
					QuirkFx.ring(level, at).style(QuirkFx.RING_SOFT).colours(0xFFFFFF, 0xE8ECF4).size((float) NET_RADIUS).thickness(0.3f).life(8).spawn();
					OmnitrixSfx.play(level, at, STICK, 0.9f, 0.9f);
				}
			}
		});
	}

	public static void clearSessionState() {
		FLURRIES.clear();
		for (Line l : LINES.values()) {
			l.strand().discard();
		}
		LINES.clear();
		for (List<Trap> l : TRAPS.values()) {
			l.forEach(t -> t.web().discard());
		}
		TRAPS.clear();
		for (Shield s : SHIELDS.values()) {
			s.web().discard();
		}
		SHIELDS.clear();
		for (Cocoon c : COCOONS.values()) {
			c.wrap.discard();
		}
		COCOONS.clear();
		for (Kick k : KICKS.values()) {
			k.strand().discard();
		}
		KICKS.clear();
		PUNCHES.clear();
		LAST_TICK.clear();
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public AlienStats stats() {
		return AlienStats.builder().health(22).speed(1.25f).scale(0.85f).armor(2, 0).attack(2).jump(0.1f).safeFall(4).step(0.4f).build();
	}

	@Override
	protected List<AlienMove> declareMoves() {
		return List.of(AlienMove.tap(WEB_SHOT, AbilitySlot.SLOT_1, 20), AlienMove.shift(WEB_NET, AbilitySlot.SLOT_1, 160),
				AlienMove.tap(QUAD_FLURRY, AbilitySlot.SLOT_2, 100), AlienMove.shift(WEB_YANK, AbilitySlot.SLOT_2, 160),
				// v0.17.2 (user: "make the swing x and web zip shift+X please")
				AlienMove.tap(WEB_SWING, AbilitySlot.SLOT_3, SWING_COOLDOWN), AlienMove.shift(WEB_ZIP, AbilitySlot.SLOT_3, ZIP_COOLDOWN),
				AlienMove.tap(TAIL_WHIP, AbilitySlot.SLOT_4, 120), AlienMove.shift(COCOON_CRUSH, AbilitySlot.SLOT_4, 900),
				AlienMove.tap(WALL_CRAWL, AbilitySlot.SLOT_5, 10), AlienMove.shift(WEB_SHIELD, AbilitySlot.SLOT_5, 400),
				AlienMove.tap(WEB_TRAP, AbilitySlot.SLOT_6, 300), AlienMove.shift(WEB_BARRAGE, AbilitySlot.SLOT_6, 200),
				AlienMove.vanilla(FOUR_FISTS, "Attack", 0), AlienMove.vanilla(CEILING_HANG, "Sneak (under a ceiling)", 0));
	}

	// ---------------------------------------------------------------- lifecycle

	@Override
	public void onTransform(ServerPlayer p) {
		setFlag(p, F_CRAWL, true); // Wall Crawl starts on
	}

	@Override
	public void onRevert(ServerPlayer p) {
		UUID id = p.getUUID();
		FLURRIES.remove(id);
		endLine(p, false);
		endKick(p);
		List<Trap> traps = TRAPS.remove(id);
		if (traps != null) {
			traps.forEach(t -> t.web().discard());
		}
		Shield sh = SHIELDS.remove(id);
		if (sh != null) {
			sh.web().discard();
		}
		COCOONS.values().removeIf(c -> {
			if (c.owner.equals(id)) {
				c.wrap.discard();
				return true;
			}
			return false;
		});
		PUNCHES.remove(id);
		LAST_TICK.remove(id);
		OmnitrixState n = Omnitrix.state(p).copy();
		n.kitFlags = 0;
		n.timers.remove(key(AIR_AT));
		n.timers.remove(key(SWING_FREE_UNTIL));
		for (String k : LINE_KEYS) {
			n.timers.remove(key(k));
		}
		Omnitrix.save(p, n);
	}

	/** Anything of {@code p}'s left in the kit's maps (gametests: turning back leaves nothing). */
	public static int leftovers(ServerPlayer p) {
		UUID id = p.getUUID();
		int n = (FLURRIES.containsKey(id) ? 1 : 0) + (LINES.containsKey(id) ? 1 : 0) + (TRAPS.containsKey(id) ? 1 : 0) + (SHIELDS.containsKey(id) ? 1 : 0)
				+ (KICKS.containsKey(id) ? 1 : 0) + (PUNCHES.containsKey(id) ? 1 : 0) + (LAST_TICK.containsKey(id) ? 1 : 0);
		for (Cocoon c : COCOONS.values()) {
			if (c.owner.equals(id)) {
				n++;
			}
		}
		return n;
	}

	@Override
	public boolean climbs(Player p) {
		// only while pressed into a wall (last tick's collision, as vanilla's ladders read it): on open ground he falls and
		// sneaks normally instead of hanging in the air like on a ladder. v0.17.2 revamp: Ceiling Hang -- sneaking with a
		// ceiling right over his head counts as climbing, and a sneaking climber doesn't slide down (vanilla ladder rules)
		return flag(p, F_CRAWL) && (p.horizontalCollision || hangs(p));
	}

	/** v0.17.2 revamp: Ceiling Hang -- Wall Crawl on, sneaking, off the ground, a sturdy ceiling within a few pixels of his head. */
	public boolean hangs(Player p) {
		if (!flag(p, F_CRAWL) || !p.isShiftKeyDown() || p.onGround() || p.isInWater()) {
			return false;
		}
		BlockPos up = BlockPos.containing(p.getX(), p.getY() + p.getBbHeight() + 0.25, p.getZ());
		return p.level().getBlockState(up).isFaceSturdy(p.level(), up, Direction.DOWN);
	}

	/** Wall Crawl is switched on (gametests; {@link #climbs} also needs a wall). */
	public boolean crawlOn(Player p) {
		return flag(p, F_CRAWL);
	}

	@Override
	public boolean keyActive(Player p, AbilitySlot slot) {
		return (slot == AbilitySlot.SLOT_5 && flag(p, F_CRAWL)) || (slot == AbilitySlot.SLOT_3 && (flag(p, F_SWING) || flag(p, F_ZIP)));
	}

	@Override
	public boolean allowDamage(ServerPlayer p, DamageSource source, float amount) {
		return !(source.is(DamageTypeTags.IS_FALL) && (flag(p, F_AIR) || flag(p, F_SWING) || flag(p, F_ZIP) || flag(p, F_KICK)));
	}

	@Override
	public float incomingDamage(ServerPlayer p, DamageSource source, float amount) {
		return source.is(DamageTypeTags.IS_FIRE) ? amount * FIRE_TAKEN : amount;
	}

	@Override
	public void tick(ServerPlayer p, OmnitrixState s, long now) {
		// v0.17.2 revamp: once per game tick (RipjawsKit.tick)
		Long last = LAST_TICK.put(p.getUUID(), now);
		if (last != null && last == now) {
			return;
		}
		ServerLevel level = level(p);
		UUID id = p.getUUID();
		if (flag(p, F_AIR) && p.onGround() && now - timer(p, AIR_AT) > 4 && !LINES.containsKey(id)) {
			setFlag(p, F_AIR, false);
		}
		// Ceiling Hang: no sliding off (the ladder rule stops the fall; this keeps him from drifting)
		if (hangs(p)) {
			Vec3 v = p.getDeltaMovement();
			if (v.y < 0) {
				p.setDeltaMovement(v.x * 0.6, 0, v.z * 0.6);
				p.hurtMarked = true;
			}
			p.fallDistance = 0f;
		}
		// the flurry: a fist every 2 ticks
		Flurry f = FLURRIES.get(id);
		if (f != null && now >= f.next) {
			f.next = now + FLURRY_GAP;
			flurryHit(p, f.left);
			if (--f.left <= 0) {
				FLURRIES.remove(id);
			}
		}
		// v0.17.2: the swing / zip line (the owning client moves him; the server ends it)
		tickLine(p, now);
		tickKick(p, now);
		// the web traps
		List<Trap> traps = TRAPS.get(id);
		if (traps != null) {
			traps.removeIf(t -> {
				if (now >= t.until() || t.dim() != level.dimension()) {
					t.web().discard();
					return true;
				}
				for (LivingEntity e : around(p, t.at(), TRAP_RADIUS)) {
					if (Math.abs(e.getY() - t.at().y) < 1.5 && t.caught().add(e.getUUID())) {
						webbed(p, e, TRAP_ROOT);
						hurt(p, e, TRAP_DAMAGE, false);
						OmnitrixSfx.play(level, e.position(), STICK, 0.8f, 1f);
					}
				}
				// v0.17.2 revamp, Tangled (the show: he gets caught in his own webs): walking through his own web slows him
				if (p.onGround() && p.position().distanceToSqr(t.at()) < TRAP_RADIUS * TRAP_RADIUS * 0.6 && Math.abs(p.getY() - t.at().y) < 1.0) {
					p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, TANGLE_TICKS, 1, false, false, true));
				}
				return false;
			});
			if (traps.isEmpty()) {
				TRAPS.remove(id);
			}
		}
		// the web shield: shots that reach it stop dead; creatures pushing through are slowed
		Shield sh = SHIELDS.get(id);
		if (sh != null) {
			if (now >= sh.until() || sh.dim() != level.dimension()) {
				sh.web().discard();
				SHIELDS.remove(id);
			} else {
				for (Projectile pr : level.getEntitiesOfClass(Projectile.class, new AABB(sh.centre(), sh.centre()).inflate(2.2),
						pr -> pr.getOwner() != p && !(pr instanceof AlienProjectileEntity))) {
					if (Math.abs(pr.position().subtract(sh.centre()).dot(sh.normal())) < 0.8) {
						pr.setDeltaMovement(Vec3.ZERO);
						pr.hurtMarked = true;
						pr.setNoGravity(false);
					}
				}
				for (LivingEntity e : around(p, sh.centre(), 1.9)) {
					e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 3, false, false), p);
				}
			}
		}
		// the cocoons squeeze, then slam
		if (!COCOONS.isEmpty()) {
			COCOONS.values().removeIf(c -> {
				if (!c.owner.equals(id)) {
					return false;
				}
				if (!c.target.isAlive()) {
					c.wrap.discard();
					return true;
				}
				if (now >= c.until) {
					c.wrap.discard();
					cocoonSlam(p, c.target);
					return true;
				}
				if (now >= c.next) {
					c.next = now + COCOON_PULSE;
					c.target.invulnerableTime = 0;
					hurt(p, c.target, COCOON_PULSE_DAMAGE, false);
					OmnitrixSfx.play(level, c.target.position(), SQUEEZE, 0.7f, 1f);
				}
				return false;
			});
		}
		if (now % 20 == 0) {
			long[] pc = PUNCHES.get(id);
			if (pc != null && now - pc[1] > FISTS_WINDOW) {
				PUNCHES.remove(id);
			}
		}
	}

	// ---------------------------------------------------------------- keys

	@Override
	public void press(ServerPlayer p, AbilitySlot slot, boolean shift) {
		switch (slot) {
			case SLOT_1 -> {
				if (shift) {
					webNet(p);
				} else {
					webShot(p);
				}
			}
			case SLOT_2 -> {
				if (shift) {
					webYank(p);
				} else {
					quadFlurry(p);
				}
			}
			case SLOT_3 -> {
				// v0.17.2 (user: "make the swing x and web zip shift+X please"): Ladybug's X -- either key again lets go of the line
				if (LINES.containsKey(p.getUUID())) {
					endLine(p, true);
				} else if (shift) {
					webZip(p);
				} else {
					webSwing(p);
				}
			}
			case SLOT_4 -> {
				if (shift) {
					cocoonCrush(p);
				} else {
					tailWhip(p);
				}
			}
			case SLOT_5 -> {
				if (shift) {
					webShield(p);
				} else {
					wallCrawl(p);
				}
			}
			case SLOT_6 -> {
				if (shift) {
					webBarrage(p);
				} else {
					webTrap(p);
				}
			}
			default -> {
			}
		}
	}

	@Override
	public void release(ServerPlayer p, AbilitySlot slot, boolean shift, int heldTicks) {
		// v0.17.2: Web Swing runs while X is held; letting go flings him off the end of the line (a zip runs to the end)
		if (slot == AbilitySlot.SLOT_3 && !shift && swinging(p)) {
			endLine(p, true);
		}
	}

	// ---------------------------------------------------------------- helpers

	/**
	 * {@code e} is stuck in his web: rooted {@code ticks} (a boss a quarter), with the white wrap drawn round it. v0.17.2 revamp:
	 * a burning creature burns straight through it (fire weakness).
	 */
	public static void webbed(ServerPlayer p, LivingEntity e, int ticks) {
		if (e.isOnFire()) {
			return;
		}
		AlienStatus.root(p, e, ticks, 20);
		int t = AlienStatus.isBoss(e) ? Math.max(5, ticks / 4) : ticks;
		AlienProjectileEntity w = AlienProjectileEntity.of(p, "spidermonkey_wrap");
		w.moveTo(e.getX(), e.getY(), e.getZ(), 0f, 0f);
		w.colour(e.getId() + 1).size(Math.max(0.6f, e.getBbHeight() * 0.6f)).held(true).speed(0).life(t).damage(0f).spawn();
	}

	/** Stuck in a web right now (rooted: Slowness 10). */
	public static boolean stuck(LivingEntity e) {
		MobEffectInstance s = e.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
		return s != null && s.getAmplifier() >= 9;
	}

	/**
	 * A strand from his tail tip for {@code life} ticks: to the fixed point {@code at} (colour 0), or, {@code to} given, to that creature
	 * wherever it goes (colour id + 1). v0.17.2 (user: "make spidermonkeys webs shoot out from his tail"): both ends are drawn from his
	 * tail tip now (SpidermonkeyFx), never from a hand or his eyes.
	 */
	private static AlienProjectileEntity strand(ServerPlayer p, Vec3 at, @Nullable LivingEntity to, int life) {
		AlienProjectileEntity s = AlienProjectileEntity.of(p, "spidermonkey_strand");
		Vec3 end = to == null ? at : to.position().add(0, to.getBbHeight() * 0.55, 0);
		s.moveTo(end.x, end.y, end.z, 0f, 0f);
		double len = p.position().distanceTo(end);
		s.colour(to == null ? 0 : to.getId() + 1).size((float) Math.max(0.6, len / 3.0)).held(true).speed(0).life(life).damage(0f).spawn();
		return s;
	}

	/**
	 * v0.17.2 (user: "make spidermonkeys webs shoot out from his tail, thats how it works in the show"): where his tail tip is -- the
	 * server's stand-in for the client's simulated tail (SpidermonkeyBody.tailTip): behind and above his hips, by his body's facing,
	 * scaled with his size.
	 */
	public static Vec3 tailTip(Entity p) {
		float yaw = (p instanceof LivingEntity le ? le.yBodyRot : p.getYRot()) * Mth.DEG_TO_RAD;
		Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
		float s = p instanceof LivingEntity le ? le.getScale() : 1f;
		double crouch = p.isCrouching() ? -0.12 * s : 0.0;
		return p.position().add(0, TAIL_UP * s + crouch, 0).subtract(fwd.scale(TAIL_BACK * s));
	}

	/** v0.17.2: what his webs aim at -- the creature under the crosshair, else the block, else {@link #AIM_RANGE} along the look. */
	public static Vec3 aimPoint(ServerPlayer p) {
		LivingEntity t = lookTarget(p, AIM_RANGE);
		if (t != null) {
			return t.position().add(0, t.getBbHeight() * 0.5, 0);
		}
		BlockHitResult b = blockAlong(p, p.getLookAngle(), AIM_RANGE);
		return b != null ? b.getLocation() : p.getEyePosition().add(p.getLookAngle().scale(AIM_RANGE));
	}

	/** v0.17.2: a web projectile of {@code kind} leaving his tail tip toward {@code aim}. */
	private static AlienProjectileEntity fromTail(ServerPlayer p, String kind, Vec3 dir) {
		AlienProjectileEntity e = AlienProjectileEntity.of(p, kind);
		Vec3 tip = tailTip(p);
		e.moveTo(tip.x, tip.y, tip.z, p.getYRot(), p.getXRot());
		return e.direction(dir.lengthSqr() < 1.0e-6 ? p.getLookAngle() : dir);
	}

	@Nullable
	private static BlockHitResult blockAlong(ServerPlayer p, Vec3 dir, double range) {
		Vec3 eye = p.getEyePosition();
		HitResult hit = p.level().clip(new ClipContext(eye, eye.add(dir.scale(range)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		return hit.getType() == HitResult.Type.BLOCK ? (BlockHitResult) hit : null;
	}

	private static void say(ServerPlayer p, String key) {
		p.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.GRAY), true);
	}

	// ---------------------------------------------------------------- R / Shift+R

	/** R: a glob of web -- 6 and rooted 2 s. */
	public boolean webShot(ServerPlayer p) {
		if (!use(p, WEB_SHOT)) {
			return false;
		}
		anim(p, ANIM_SHOT);
		// v0.17.2 (user: "make spidermonkeys webs shoot out from his tail"): from the tail tip at what the crosshair is on
		fromTail(p, "spidermonkey_web", aimPoint(p).subtract(tailTip(p))).colour(SHOT_ROOT).size(0.3f).damage(SHOT_DAMAGE).speed(2.2).life(40).spawn();
		OmnitrixSfx.playOn(p, THWIP, 0.9f, 1f, SoundSource.PLAYERS);
		return true;
	}

	/** Shift+R: a spinning net -- everything within 2.5 of where it lands: 12 and rooted 3 s. */
	public boolean webNet(ServerPlayer p) {
		if (!use(p, WEB_NET)) {
			return false;
		}
		anim(p, ANIM_NET);
		fromTail(p, "spidermonkey_net", aimPoint(p).subtract(tailTip(p))).colour(NET_ROOT).size(0.8f).damage(0f).blast((float) NET_RADIUS, NET_DAMAGE).gravity(0.02).speed(1.3)
				.life(60).spawn();
		OmnitrixSfx.playOn(p, NET, 1f, 1f, SoundSource.PLAYERS);
		return true;
	}

	// ---------------------------------------------------------------- G / Shift+G

	/** G: four fists in front, one every 2 ticks. */
	public boolean quadFlurry(ServerPlayer p) {
		if (!use(p, QUAD_FLURRY)) {
			return false;
		}
		anim(p, ANIM_FLURRY);
		Flurry f = new Flurry();
		f.next = p.level().getGameTime();
		FLURRIES.put(p.getUUID(), f);
		return true;
	}

	/** One of the flurry's fists (public: the gametests throw them without ticking). +1 on a webbed foe. Returns how many it hit. */
	public int flurryHit(ServerPlayer p, int n) {
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		int hits = 0;
		for (LivingEntity e : around(p, eye.add(look.scale(FLURRY_RANGE * 0.5)), FLURRY_RANGE)) {
			Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eye);
			if (to.length() <= FLURRY_RANGE + e.getBbWidth() * 0.5 && to.normalize().dot(look) > 0.4) {
				e.invulnerableTime = 0;
				if (hurt(p, e, FLURRY_HIT + (stuck(e) ? STICKY_BONUS : 0f), false)) {
					hits++;
				}
				if (n == 1) {
					shove(e, p.position(), 0.6, 0.25);
				}
			}
		}
		OmnitrixSfx.playOn(p, PUNCH, 0.8f, 1f + (FLURRY_HITS - n) * 0.08f, SoundSource.PLAYERS);
		return hits;
	}

	/**
	 * Shift+G: a line at a creature. On the ground: 12, and it is reeled in to him (a boss is not). v0.17.2 revamp: in the air or on a
	 * wall, the line pulls HIM in instead for a Swing Kick (15, knocked flying).
	 */
	public boolean webYank(ServerPlayer p) {
		LivingEntity t = lookTarget(p, YANK_RANGE);
		if (t == null) {
			say(p, "message.projecthero.omnitrix.spidermonkey.no_target");
			return false;
		}
		if (!use(p, WEB_YANK)) {
			return false;
		}
		if (!p.onGround()) {
			return swingKick(p, t);
		}
		anim(p, ANIM_YANK);
		hurt(p, t, YANK_DAMAGE, false);
		if (!AlienStatus.isBoss(t)) {
			Vec3 pull = p.position().subtract(t.position());
			double d = pull.length();
			double res = 1.0 - Math.min(1.0, t.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE));
			t.setDeltaMovement(pull.normalize().scale(Math.min(2.0, 0.35 + d * 0.13) * Math.max(0.35, res)).add(0, 0.35, 0));
			t.hurtMarked = true;
		}
		strand(p, t.position(), t, 10);
		OmnitrixSfx.playOn(p, ZIP, 1f, 1f, SoundSource.PLAYERS);
		return true;
	}

	/** v0.17.2 revamp: the Swing Kick -- he flies in on a line at {@code t} (tests call it straight). */
	public boolean swingKick(ServerPlayer p, LivingEntity t) {
		endLine(p, false);
		anim(p, ANIM_KICK);
		endKick(p);
		AlienProjectileEntity s = strand(p, t.position().add(0, t.getBbHeight() * 0.6, 0), null, KICK_MAX + 4);
		KICKS.put(p.getUUID(), new Kick(t, p.level().getGameTime() + KICK_MAX, s));
		setFlag(p, F_KICK, true);
		setFlag(p, F_AIR, true);
		setTimer(p, AIR_AT, p.level().getGameTime());
		OmnitrixSfx.playOn(p, ZIP, 1f, 1.2f, SoundSource.PLAYERS);
		OmnitrixSfx.playOn(p, CHATTER, 0.6f, 1.1f, SoundSource.PLAYERS);
		return true;
	}

	/** Whether a Swing Kick is in flight (tests). */
	public static boolean kicking(ServerPlayer p) {
		return KICKS.containsKey(p.getUUID());
	}

	private void tickKick(ServerPlayer p, long now) {
		Kick k = KICKS.get(p.getUUID());
		if (k == null) {
			return;
		}
		LivingEntity t = k.target();
		if (!t.isAlive() || now >= k.until() || t.level() != p.level()) {
			endKick(p);
			return;
		}
		Vec3 to = t.position().add(0, t.getBbHeight() * 0.5, 0).subtract(p.position().add(0, p.getBbHeight() * 0.5, 0));
		if (to.length() <= 1.4 + t.getBbWidth() * 0.5) {
			kickLands(p, t);
			return;
		}
		p.setDeltaMovement(to.normalize().scale(Math.min(1.6, 0.6 + to.length() * 0.2)));
		p.hurtMarked = true;
		p.fallDistance = 0f;
	}

	/** The Swing Kick lands: 15, and the creature is knocked flying (tests call it straight). */
	public void kickLands(ServerPlayer p, LivingEntity t) {
		endKick(p);
		Vec3 dir = t.position().subtract(p.position());
		hurt(p, t, KICK_DAMAGE, false);
		shove(t, p.position(), 1.4, 0.45);
		Vec3 back = new Vec3(-dir.x, 0, -dir.z);
		back = back.lengthSqr() < 1.0e-4 ? Vec3.ZERO : back.normalize();
		p.setDeltaMovement(back.scale(0.4).add(0, 0.45, 0)); // he flips off it
		p.hurtMarked = true;
		setTimer(p, AIR_AT, p.level().getGameTime());
		anim(p, ANIM_KICK);
		QuirkFx.ring(level(p), t.position().add(0, t.getBbHeight() * 0.5, 0)).flags(QuirkFx.F_VERTICAL).vec(dir).colours(0xFFFFFF, 0xC8D4F0).size(1.6f)
				.thickness(0.3f).life(8).spawn();
		QuirkCamera.hitStop(p, 2);
		OmnitrixSfx.play(level(p), t.position(), KICK, 1f, 1f);
	}

	private void endKick(ServerPlayer p) {
		Kick k = KICKS.remove(p.getUUID());
		if (k != null) {
			k.strand().discard();
		}
		if (flag(p, F_KICK)) {
			setFlag(p, F_KICK, false);
		}
	}

	// ---------------------------------------------------------------- X / Shift+X

	/** The synced line keys (cleared on revert). */
	private static final String[] LINE_KEYS = { LINE, LINE_AT, HOOK_X, HOOK_Y, HOOK_Z, ROPE };

	private static long synced(Player p, String name) {
		return Omnitrix.state(p).readyAt(ID + "." + name);
	}

	/** v0.17.2: the line he is on, client-safe: 0 none, 1 Web Swing, 2 Web Zip. */
	public static int lineMode(Player p) {
		return (int) synced(p, LINE);
	}

	/** v0.17.2: when the current line was cast (game time), client-safe -- a new value is a new line. */
	public static long lineAt(Player p) {
		return synced(p, LINE_AT);
	}

	/** v0.17.2: where the current line is stuck, client-safe; null without a line. */
	@Nullable
	public static Vec3 hook(Player p) {
		OmnitrixState s = Omnitrix.state(p);
		Long x = s.timers.get(ID + "." + HOOK_X), y = s.timers.get(ID + "." + HOOK_Y), z = s.timers.get(ID + "." + HOOK_Z);
		return x == null || y == null || z == null ? null : new Vec3(x / 1000.0, y / 1000.0, z / 1000.0);
	}

	/** v0.17.2: the swing line's rope length when cast, client-safe. */
	public static double rope(Player p) {
		return synced(p, ROPE) / 1000.0;
	}

	/**
	 * v0.17.2 (user: "make the pull stronger"): the zip line's pull this tick from {@code eye} toward {@code hook} -- straight at it at
	 * {@link #ZIP_SPEED}, easing off only for the last stretch (Ladybug's zip; the client runs it, the gametests check it).
	 */
	public static Vec3 zipVelocity(Vec3 eye, Vec3 hook) {
		Vec3 to = hook.subtract(eye);
		double d = to.length();
		return d <= ZIP_ARRIVE || d < 1.0e-6 ? Vec3.ZERO : to.scale(Math.min(ZIP_SPEED, d * 0.6) / d);
	}

	/** Writes the line the owning client moves him on (one sync), with its flag. */
	private void writeLine(ServerPlayer p, int mode, Vec3 hook, double rope, long now) {
		OmnitrixState n = Omnitrix.state(p).copy();
		n.timers.put(key(LINE), (long) mode);
		n.timers.put(key(LINE_AT), now);
		n.timers.put(key(HOOK_X), Math.round(hook.x * 1000.0));
		n.timers.put(key(HOOK_Y), Math.round(hook.y * 1000.0));
		n.timers.put(key(HOOK_Z), Math.round(hook.z * 1000.0));
		n.timers.put(key(ROPE), Math.round(rope * 1000.0));
		n.timers.put(key(AIR_AT), now);
		n.kitFlags = (n.kitFlags & ~(F_SWING | F_ZIP)) | (mode == 1 ? F_SWING : F_ZIP) | F_AIR;
		Omnitrix.save(p, n);
	}

	/**
	 * Shift+X: Web Zip -- v0.17.2 (user: "increase his web zip range to 75 blocks please and make the pull stronger aswell ... the same as
	 * ladybugs ... yoyo zip line"): a line from his tail to the block under the crosshair (75) and he is pulled straight along it at
	 * {@link #ZIP_SPEED} till he arrives (a hop up onto the ledge), {@link #ZIP_MAX} ticks at most. False (no cooldown) with nothing to
	 * stick to.
	 */
	public boolean webZip(ServerPlayer p) {
		if (!ready(p, WEB_ZIP)) {
			use(p, WEB_ZIP); // the "not ready" line
			return false;
		}
		BlockHitResult hit = blockAlong(p, p.getLookAngle(), ZIP_RANGE);
		if (hit == null) {
			say(p, "message.projecthero.omnitrix.spidermonkey.no_anchor");
			return false;
		}
		use(p, WEB_ZIP);
		anim(p, ANIM_ZIP);
		Vec3 at = hit.getLocation();
		startLine(p, 2, at, hit.getBlockPos(), at.distanceTo(p.getEyePosition()), ZIP_MAX + 10);
		OmnitrixSfx.playOn(p, ZIP, 1f, 1.1f, SoundSource.PLAYERS);
		return true;
	}

	/**
	 * X (held): Web Swing -- v0.17.2 (user: "make the web swings range further aswell ... functioned the same as ladybugs yoyo swing"):
	 * a line from his tail to the block under the crosshair (50 blocks; with nothing there above him, a point ahead and above), and he
	 * swings on it while X is held -- Spider-Man's rope (Jump reels in, Sneak pays out, W pumps), run on his own client. Letting go in
	 * the air flings him on with the arc's speed. Monkey Bars: a re-catch while still airborne within 2 s of letting go costs no
	 * cooldown, so swings chain.
	 */
	public boolean webSwing(ServerPlayer p) {
		Vec3 look = p.getLookAngle();
		BlockHitResult hit = blockAlong(p, look, SWING_RANGE);
		if (hit == null || hit.getLocation().y < p.getY() + 1.0) {
			// nothing to hook up there: aim up and ahead, at least 45 degrees up, whatever the crosshair says
			Vec3 flat = look.multiply(1, 0, 1);
			flat = flat.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : flat.normalize();
			BlockHitResult up = blockAlong(p, flat.add(0, Math.max(1.0, look.y * 2), 0).normalize(), SWING_RANGE);
			if (up != null && up.getLocation().y >= p.getY() + 1.5) {
				hit = up;
			}
		}
		if (hit == null || hit.getLocation().y < p.getY() + 1.0) {
			say(p, "message.projecthero.omnitrix.spidermonkey.no_anchor");
			return false;
		}
		long now = p.level().getGameTime();
		boolean chain = !p.onGround() && now <= timer(p, SWING_FREE_UNTIL);
		if (!chain && !use(p, WEB_SWING)) {
			return false;
		}
		anim(p, ANIM_SWING);
		Vec3 at = hit.getLocation();
		// Ladybug's rope: the distance from his eyes, never under 3 or past the range
		double rope = Math.max(3.0, Math.min(SWING_RANGE + 2.0, at.distanceTo(p.getEyePosition())));
		startLine(p, 1, at, hit.getBlockPos(), rope, SWING_MAX + 10);
		setTimer(p, SWING_FREE_UNTIL, 0);
		OmnitrixSfx.playOn(p, THWIP, 1f, chain ? 1.1f : 0.9f, SoundSource.PLAYERS);
		return true;
	}

	private void startLine(ServerPlayer p, int mode, Vec3 at, BlockPos block, double rope, int life) {
		endLine(p, false);
		long now = p.level().getGameTime();
		AlienProjectileEntity s = strand(p, at, null, life);
		LINES.put(p.getUUID(), new Line(mode, at, block.immutable(), rope, now, s));
		writeLine(p, mode, at, rope, now);
		floatTolerance(p, true);
		p.resetFallDistance();
	}

	/** Whether {@code p} hangs on a swing line (gametests). */
	public static boolean swinging(ServerPlayer p) {
		Line l = LINES.get(p.getUUID());
		return l != null && l.mode() == 1;
	}

	/** v0.17.2: whether {@code p} is being pulled along a zip line (gametests). */
	public static boolean zipping(ServerPlayer p) {
		Line l = LINES.get(p.getUUID());
		return l != null && l.mode() == 2;
	}

	/**
	 * The server half of the line (Ladybug's {@code tickLine}): it ends when the block it is stuck to goes, a zip arrives or runs out
	 * of time, a swing is pulled {@link #SWING_SLACK_BREAK} past its rope or runs {@link #SWING_MAX}, or he changes world.
	 */
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
		floatTolerance(p, true);
	}

	/**
	 * Lets go of the line. The fling itself is his client's (it carries the arc's speed on, like Ladybug's release); here a swing let go
	 * in the air opens the Monkey Bars window for a free re-catch.
	 */
	public void endLine(ServerPlayer p, boolean sound) {
		Line l = LINES.remove(p.getUUID());
		if (l == null) {
			return;
		}
		l.strand().discard();
		floatTolerance(p, false);
		long now = p.level().getGameTime();
		OmnitrixState n = Omnitrix.state(p).copy();
		for (String k : LINE_KEYS) {
			n.timers.remove(key(k));
		}
		n.kitFlags &= ~(F_SWING | F_ZIP);
		n.kitFlags |= F_AIR; // no fall damage till he lands
		n.timers.put(key(AIR_AT), now);
		if (l.mode() == 1 && !p.onGround()) {
			n.timers.put(key(SWING_FREE_UNTIL), now + SWING_FREE);
		}
		Omnitrix.save(p, n);
		p.resetFallDistance();
		if (sound) {
			p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.LEASH_KNOT_BREAK, SoundSource.PLAYERS, 0.4f, 1.7f);
		}
	}

	/** Kept for older callers: lets go of the swing line. */
	public void endSwing(ServerPlayer p, boolean fling) {
		endLine(p, fling);
	}

	/**
	 * Vanilla kicks a player airborne for 4 s with no downward motion ("flying is not enabled") -- a good swing looks just like that.
	 * Ladybug's / Spider-Man's fix: {@code mayfly} for the line's duration, {@code flying} forced off, handed back after unless another
	 * power still flies him.
	 */
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

	// ---------------------------------------------------------------- Z / Shift+Z

	/** Z: a spin with the tail -- everything within 3.2: 13 and thrown (webbed foes torn loose and thrown twice as far). */
	public boolean tailWhip(ServerPlayer p) {
		if (!use(p, TAIL_WHIP)) {
			return false;
		}
		anim(p, ANIM_WHIP);
		for (LivingEntity e : around(p, p.position().add(0, 0.6, 0), WHIP_RADIUS)) {
			boolean webbed = stuck(e);
			if (webbed) {
				e.removeEffect(MobEffects.MOVEMENT_SLOWDOWN); // torn out of the web
			}
			hurt(p, e, WHIP_DAMAGE, false);
			shove(e, p.position(), webbed ? 1.8 : 0.9, webbed ? 0.55 : 0.35);
		}
		QuirkFx.ring(level(p), p.position().add(0, 0.5, 0)).colours(0xFFFFFF, 0x9AB0E8).size((float) WHIP_RADIUS).thickness(0.4f).life(7).spawn();
		OmnitrixSfx.playOn(p, WHIP, 1f, 1f, SoundSource.PLAYERS);
		return true;
	}

	/**
	 * Shift+Z (ULTIMATE): wraps the creature under the crosshair (16) in a cocoon: rooted 5 s, squeezed for 5 every half second, then
	 * he slams it (14, launched).
	 */
	public boolean cocoonCrush(ServerPlayer p) {
		LivingEntity t = lookTarget(p, COCOON_RANGE);
		if (t == null) {
			say(p, "message.projecthero.omnitrix.spidermonkey.no_target");
			return false;
		}
		if (!use(p, COCOON_CRUSH)) {
			return false;
		}
		anim(p, ANIM_COCOON);
		long now = p.level().getGameTime();
		t.clearFire(); // the cocoon smothers it
		AlienStatus.root(p, t, COCOON_TICKS, 20);
		AlienProjectileEntity w = AlienProjectileEntity.of(p, "spidermonkey_wrap");
		w.moveTo(t.getX(), t.getY(), t.getZ(), 0f, 0f);
		// colour = id + 1, negative: a full cocoon rather than a few strands
		w.colour(-(t.getId() + 1)).size(Math.max(0.6f, t.getBbHeight() * 0.6f)).held(true).speed(0).life(COCOON_TICKS).damage(0f).spawn();
		Cocoon old = COCOONS.put(t.getUUID(), new Cocoon(p.getUUID(), t, now + COCOON_TICKS, now + 4, w));
		if (old != null) {
			old.wrap.discard();
		}
		strand(p, t.position(), t, 12);
		OmnitrixSfx.playOn(p, NET, 1.2f, 0.8f, SoundSource.PLAYERS);
		OmnitrixSfx.playOn(p, CHATTER, 0.8f, 1f, SoundSource.PLAYERS);
		return true;
	}

	/** v0.17.2 revamp: the cocoon's finish -- he swings it down: 14 and launched (tests call it straight). */
	public void cocoonSlam(ServerPlayer p, LivingEntity t) {
		anim(p, ANIM_SLAM);
		t.invulnerableTime = 0;
		hurt(p, t, COCOON_SLAM, false);
		t.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
		if (!AlienStatus.isBoss(t)) {
			t.setDeltaMovement(t.getDeltaMovement().add(0, 0.9, 0));
			t.hurtMarked = true;
		}
		ServerLevel l = level(p);
		QuirkFx.ring(l, t.position().add(0, 0.05, 0)).colours(0xFFFFFF, 0xC8D4F0).size(2.6f).thickness(0.5f).life(10).ground().spawn();
		QuirkCamera.shakeAround(l, t.position(), 10, 0.25f, 5);
		OmnitrixSfx.play(l, t.position(), SLAM, 1f, 1f);
	}

	/** Whether {@code e} is wrapped in a cocoon. */
	public static boolean cocooned(LivingEntity e) {
		return COCOONS.containsKey(e.getUUID());
	}

	// ---------------------------------------------------------------- C / Shift+C

	/** C: a web on the ground ahead (within 12): the first time anything walks in, 5 and rooted 3 s. Lasts 15 s, two at a time. */
	public boolean webTrap(ServerPlayer p) {
		BlockHitResult hit = blockAlong(p, p.getLookAngle(), TRAP_RANGE);
		Vec3 flat = p.getLookAngle().multiply(1, 0, 1);
		flat = flat.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : flat.normalize();
		Vec3 at = hit != null ? hit.getLocation() : p.position().add(flat.scale(3));
		if (!use(p, WEB_TRAP)) {
			return false;
		}
		anim(p, ANIM_TRAP);
		// settle it on the floor below the point
		Vec3 ground = at;
		HitResult down = p.level().clip(new ClipContext(at.add(0, 0.5, 0), at.add(0, -6, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		if (down.getType() == HitResult.Type.BLOCK) {
			ground = down.getLocation();
		}
		AlienProjectileEntity web = AlienProjectileEntity.of(p, "spidermonkey_trap");
		web.moveTo(ground.x, ground.y + 0.02, ground.z, p.getYRot(), 0f);
		web.colour(0).size((float) TRAP_RADIUS).held(true).speed(0).life(TRAP_TICKS + 5).damage(0f).spawn();
		List<Trap> traps = TRAPS.computeIfAbsent(p.getUUID(), k -> new ArrayList<>());
		while (traps.size() >= 2) {
			traps.remove(0).web().discard();
		}
		traps.add(new Trap(p.level().dimension(), ground, p.level().getGameTime() + TRAP_TICKS, web, new HashSet<>()));
		strand(p, ground, null, 6); // v0.17.2 (user: webs from his tail): shot down from the tail tip
		OmnitrixSfx.playOn(p, NET, 0.9f, 1.1f, SoundSource.PLAYERS);
		return true;
	}

	/** Shift+C: six webs in a fan -- 5 each, rooted 1.5 s. */
	public boolean webBarrage(ServerPlayer p) {
		if (!use(p, WEB_BARRAGE)) {
			return false;
		}
		anim(p, ANIM_BARRAGE);
		// v0.17.2 (user: webs from his tail): the fan leaves the tail tip, centred on what the crosshair is on
		Vec3 tip = tailTip(p);
		Vec3 look = aimPoint(p).subtract(tip).normalize();
		for (int i = 0; i < 6; i++) {
			float yaw = (i - 2.5f) * 8f * Mth.DEG_TO_RAD;
			Vec3 d = look.yRot(yaw).add(0, (i % 2) * 0.05, 0);
			fromTail(p, "spidermonkey_web", d).colour(BARRAGE_ROOT).size(0.25f).damage(BARRAGE_DAMAGE).speed(2.0).life(30).spawn();
		}
		OmnitrixSfx.playOn(p, THWIP, 1.1f, 0.9f, SoundSource.PLAYERS);
		OmnitrixSfx.playOn(p, NET, 0.6f, 1.3f, SoundSource.PLAYERS);
		return true;
	}

	// ---------------------------------------------------------------- V / Shift+V

	/** V: Wall Crawl on / off. */
	public boolean wallCrawl(ServerPlayer p) {
		if (!use(p, WALL_CRAWL)) {
			return false;
		}
		boolean on = !flag(p, F_CRAWL);
		setFlag(p, F_CRAWL, on);
		anim(p, ANIM_CRAWL);
		p.displayClientMessage(Component.translatable(on ? "message.projecthero.omnitrix.spidermonkey.crawl_on" : "message.projecthero.omnitrix.spidermonkey.crawl_off")
				.withStyle(ChatFormatting.BLUE), true);
		OmnitrixSfx.playOn(p, CHATTER, 0.7f, 1f, SoundSource.PLAYERS);
		return true;
	}

	/** Shift+V: a web wall 2 blocks in front for 8 s -- shots stop dead in it, creatures wade through slowly. */
	public boolean webShield(ServerPlayer p) {
		if (!use(p, WEB_SHIELD)) {
			return false;
		}
		anim(p, ANIM_SHIELD);
		Vec3 flat = p.getLookAngle().multiply(1, 0, 1);
		flat = flat.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : flat.normalize();
		Vec3 c = p.position().add(flat.scale(2)).add(0, 1.4, 0);
		AlienProjectileEntity web = AlienProjectileEntity.of(p, "spidermonkey_shield");
		web.moveTo(c.x, c.y, c.z, p.getYRot(), 0f);
		web.colour(0).size(1.5f).held(true).speed(0).life(SHIELD_TICKS + 5).damage(0f).spawn();
		Shield old = SHIELDS.put(p.getUUID(), new Shield(p.level().dimension(), c, flat, p.level().getGameTime() + SHIELD_TICKS, web));
		if (old != null) {
			old.web().discard();
		}
		OmnitrixSfx.playOn(p, NET, 1f, 0.8f, SoundSource.PLAYERS);
		return true;
	}

	// ---------------------------------------------------------------- Attack

	@Override
	public boolean onAttack(ServerPlayer p, Entity target) {
		if (!(target instanceof LivingEntity le) || !le.isAlive()) {
			return false;
		}
		long now = p.level().getGameTime();
		long[] pc = PUNCHES.computeIfAbsent(p.getUUID(), k -> new long[] { 0, now });
		if (now - pc[1] > FISTS_WINDOW) {
			pc[0] = 0;
		}
		pc[0]++;
		pc[1] = now;
		float extra = stuck(le) ? STICKY_BONUS : 0f;
		if (pc[0] >= 4) {
			// v0.17.2 revamp: Four Fists -- every 4th punch, all four arms land at once
			pc[0] = 0;
			extra += FISTS_BONUS;
			shove(le, p.position(), 0.7, 0.25);
			anim(p, ANIM_FISTS);
			OmnitrixSfx.playOn(p, PUNCH, 1f, 0.8f, SoundSource.PLAYERS);
		}
		if (extra > 0f) {
			hurt(p, le, extra, false); // its own hit; vanilla's punch still lands after it
			le.invulnerableTime = 0;
		}
		return false;
	}
}
