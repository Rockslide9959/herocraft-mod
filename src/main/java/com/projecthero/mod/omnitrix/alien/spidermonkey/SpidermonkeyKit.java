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

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 (the Recalibrated Omnitrix, basic version -- user: "Just make them basic versions of the aliens for now please that we
 * can revamp later"): Spidermonkey, the Arachnichimp -- a small, agile, four-armed ape with four eyes and a tail that shoots webs.
 *
 * <pre>
 *   R        Web Shot        a glob of web: 6, roots 2 s                        Shift+R  Web Net         a net (r 2.5): 12, roots 3 s
 *   G        Quad Flurry     four fists in front: 4 x 3.5                       Shift+G  Web Yank        a strand at a creature (16): 12, reeled in
 *   X        Web Zip         a strand to a block (28): zipped to it             Shift+X  Web Swing       a strand to a point ahead and above: swing
 *   Z        Tail Whip       a spin round (r 3.2): 13 + thrown                  Shift+Z  Web Trap        a web on the ground (r 2.5, 15 s): roots, 5
 *   V        Wall Crawl      toggle (on when he transforms): climbs walls       Shift+V  Web Shield      a web wall in front, 8 s: stops shots
 *   C        Cocoon Crush    ULTIMATE: wraps a creature (16): rooted 5 s,       Shift+C  Web Barrage     6 webs in a fan: 5 each, roots 1.5 s
 *                            5 every 0.5 s (50)
 * </pre>
 *
 * Passives: Wall Crawl ({@code climbs}), nimble (x1.25 speed, a better jump, no fall damage after his own zips and swings, safe
 * fall +4). Weaknesses: fire (x1.5: fur and webbing burn), a light frame (22 HP, armour 2).
 *
 * <p>Webs are drawn by the client from held {@link AlienProjectileEntity} carriers: {@code spidermonkey_strand} sits at a strand's
 * far end (the anchor) and draws a line to its owner's hand (colour 0) or to creature colour - 1 ({@code spidermonkey_wrap} wraps
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

	// ---------------------------------------------------------------- poses, flags, moves
	public static final int ANIM_SHOT = 1, ANIM_NET = 2, ANIM_FLURRY = 3, ANIM_YANK = 4, ANIM_ZIP = 5, ANIM_SWING = 6, ANIM_WHIP = 7, ANIM_TRAP = 8,
			ANIM_CRAWL = 9, ANIM_SHIELD = 10, ANIM_COCOON = 11, ANIM_BARRAGE = 12;

	/** Wall Crawl is on ({@link #climbs}). */
	public static final int F_CRAWL = 1;
	/** Swinging on a strand. */
	public static final int F_SWING = 2;
	/** Airborne after a zip or swing: no fall damage until he lands. */
	public static final int F_AIR = 4;

	public static final String WEB_SHOT = "web_shot", WEB_NET = "web_net", QUAD_FLURRY = "quad_flurry", WEB_YANK = "web_yank", WEB_ZIP = "web_zip",
			WEB_SWING = "web_swing", TAIL_WHIP = "tail_whip", WEB_TRAP = "web_trap", WALL_CRAWL = "wall_crawl", WEB_SHIELD = "web_shield",
			COCOON_CRUSH = "cocoon_crush", WEB_BARRAGE = "web_barrage";

	public static final String AIR_AT = "air_at";

	public static final float SHOT_DAMAGE = 6f, NET_DAMAGE = 12f, FLURRY_HIT = 3.5f, YANK_DAMAGE = 12f, WHIP_DAMAGE = 13f, TRAP_DAMAGE = 5f,
			COCOON_PULSE_DAMAGE = 5f, BARRAGE_DAMAGE = 5f;
	public static final int SHOT_ROOT = 40, NET_ROOT = 60, FLURRY_HITS = 4, FLURRY_GAP = 2, TRAP_TICKS = 300, TRAP_ROOT = 60, SHIELD_TICKS = 160,
			COCOON_TICKS = 100, COCOON_PULSE = 10, BARRAGE_ROOT = 30, SWING_MAX = 60;
	public static final double FLURRY_RANGE = 3.2, YANK_RANGE = 16, ZIP_RANGE = 28, SWING_RANGE = 24, WHIP_RADIUS = 3.2, TRAP_RADIUS = 2.5,
			TRAP_RANGE = 12, COCOON_RANGE = 16, NET_RADIUS = 2.5;
	public static final float FIRE_TAKEN = 1.5f;

	// ---------------------------------------------------------------- per-player state
	private static final Map<UUID, Flurry> FLURRIES = new ConcurrentHashMap<>();
	private static final Map<UUID, Swing> SWINGS = new ConcurrentHashMap<>();
	private static final Map<UUID, List<Trap>> TRAPS = new ConcurrentHashMap<>();
	private static final Map<UUID, Shield> SHIELDS = new ConcurrentHashMap<>();
	/** creature -> its cocoon. */
	private static final Map<UUID, Cocoon> COCOONS = new ConcurrentHashMap<>();

	private static final class Flurry {
		int left = FLURRY_HITS;
		long next;
	}

	private static final class Swing {
		final Vec3 anchor;
		final double length;
		final long until;
		final AlienProjectileEntity strand;

		Swing(Vec3 anchor, double length, long until, AlienProjectileEntity strand) {
			this.anchor = anchor;
			this.length = length;
			this.until = until;
			this.strand = strand;
		}
	}

	private record Trap(ResourceKey<Level> dim, Vec3 at, long until, AlienProjectileEntity web, Set<UUID> caught) {
	}

	private record Shield(ResourceKey<Level> dim, Vec3 centre, Vec3 normal, long until, AlienProjectileEntity web) {
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
					level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COBWEB.defaultBlockState()), at.x, at.y, at.z, 20, 1.0, 0.4, 1.0,
							0.05);
					OmnitrixSfx.play(level, at, STICK, 0.9f, 0.9f);
				}
			}
		});
	}

	public static void clearSessionState() {
		FLURRIES.clear();
		for (Swing s : SWINGS.values()) {
			s.strand.discard();
		}
		SWINGS.clear();
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
				AlienMove.tap(WEB_ZIP, AbilitySlot.SLOT_3, 30), AlienMove.shift(WEB_SWING, AbilitySlot.SLOT_3, 60),
				AlienMove.tap(TAIL_WHIP, AbilitySlot.SLOT_4, 120), AlienMove.shift(WEB_TRAP, AbilitySlot.SLOT_4, 300),
				AlienMove.tap(WALL_CRAWL, AbilitySlot.SLOT_5, 10), AlienMove.shift(WEB_SHIELD, AbilitySlot.SLOT_5, 400),
				AlienMove.tap(COCOON_CRUSH, AbilitySlot.SLOT_6, 900), AlienMove.shift(WEB_BARRAGE, AbilitySlot.SLOT_6, 200));
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
		endSwing(p, false);
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
		OmnitrixState s = Omnitrix.state(p);
		if (s.kitFlags != 0) {
			OmnitrixState n = s.copy();
			n.kitFlags = 0;
			Omnitrix.save(p, n);
		}
	}

	/** Anything of {@code p}'s left in the kit's maps (gametests: turning back leaves nothing). */
	public static int leftovers(ServerPlayer p) {
		UUID id = p.getUUID();
		int n = (FLURRIES.containsKey(id) ? 1 : 0) + (SWINGS.containsKey(id) ? 1 : 0) + (TRAPS.containsKey(id) ? 1 : 0) + (SHIELDS.containsKey(id) ? 1 : 0);
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
		// sneaks normally instead of hanging in the air like on a ladder
		return flag(p, F_CRAWL) && p.horizontalCollision;
	}

	/** Wall Crawl is switched on (gametests; {@link #climbs} also needs a wall). */
	public boolean crawlOn(Player p) {
		return flag(p, F_CRAWL);
	}

	@Override
	public boolean keyActive(Player p, AbilitySlot slot) {
		return (slot == AbilitySlot.SLOT_5 && flag(p, F_CRAWL)) || (slot == AbilitySlot.SLOT_3 && flag(p, F_SWING));
	}

	@Override
	public boolean allowDamage(ServerPlayer p, DamageSource source, float amount) {
		if (source.is(DamageTypeTags.IS_FALL) && (flag(p, F_AIR) || flag(p, F_SWING))) {
			return false;
		}
		return true;
	}

	@Override
	public float incomingDamage(ServerPlayer p, DamageSource source, float amount) {
		return source.is(DamageTypeTags.IS_FIRE) ? amount * FIRE_TAKEN : amount;
	}

	@Override
	public void tick(ServerPlayer p, OmnitrixState s, long now) {
		ServerLevel level = level(p);
		UUID id = p.getUUID();
		if (flag(p, F_AIR) && p.onGround() && now - timer(p, AIR_AT) > 4 && !flag(p, F_SWING)) {
			setFlag(p, F_AIR, false);
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
		// the swing: a pendulum on the strand
		Swing sw = SWINGS.get(id);
		if (sw != null) {
			if (now >= sw.until || (p.onGround() && now - (sw.until - SWING_MAX) > 6) || !sw.strand.isAlive()) {
				endSwing(p, true);
			} else {
				swingTick(p, sw);
			}
		}
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
		// the cocoons squeeze
		if (!COCOONS.isEmpty()) {
			COCOONS.values().removeIf(c -> {
				if (!c.owner.equals(id)) {
					return false;
				}
				if (now >= c.until || !c.target.isAlive()) {
					c.wrap.discard();
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
				if (SWINGS.containsKey(p.getUUID())) {
					endSwing(p, true); // X / Shift+X again lets go of the strand
				} else if (shift) {
					webSwing(p);
				} else {
					webZip(p);
				}
			}
			case SLOT_4 -> {
				if (shift) {
					webTrap(p);
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
					cocoonCrush(p);
				}
			}
			default -> {
			}
		}
	}

	// ---------------------------------------------------------------- helpers

	/** {@code e} is stuck in his web: rooted {@code ticks} (a boss a quarter), with the white wrap drawn round it. */
	public static void webbed(ServerPlayer p, LivingEntity e, int ticks) {
		AlienStatus.root(p, e, ticks, 20);
		int t = AlienStatus.isBoss(e) ? Math.max(5, ticks / 4) : ticks;
		AlienProjectileEntity w = AlienProjectileEntity.of(p, "spidermonkey_wrap");
		w.moveTo(e.getX(), e.getY(), e.getZ(), 0f, 0f);
		w.colour(e.getId() + 1).size(Math.max(0.6f, e.getBbHeight() * 0.6f)).held(true).speed(0).life(t).damage(0f).spawn();
	}

	/** A strand drawn from the far end {@code at} to his hand (or, {@code to} given, to that creature) for {@code life} ticks. */
	private static AlienProjectileEntity strand(ServerPlayer p, Vec3 at, @Nullable LivingEntity to, int life) {
		AlienProjectileEntity s = AlienProjectileEntity.of(p, "spidermonkey_strand");
		s.moveTo(at.x, at.y, at.z, 0f, 0f);
		double len = (to == null ? p.position() : to.position()).distanceTo(at);
		s.colour(to == null ? 0 : to.getId() + 1).size((float) Math.max(0.6, len / 3.0)).held(true).speed(0).life(life).damage(0f).spawn();
		return s;
	}

	@Nullable
	private static BlockHitResult blockAlong(ServerPlayer p, Vec3 dir, double range) {
		Vec3 eye = p.getEyePosition();
		HitResult hit = p.level().clip(new ClipContext(eye, eye.add(dir.scale(range)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		return hit.getType() == HitResult.Type.BLOCK ? (BlockHitResult) hit : null;
	}

	// ---------------------------------------------------------------- moves (public for the gametests)

	/** R: a glob of web -- 6 and rooted 2 s. */
	public boolean webShot(ServerPlayer p) {
		if (!use(p, WEB_SHOT)) {
			return false;
		}
		anim(p, ANIM_SHOT);
		AlienProjectileEntity.of(p, "spidermonkey_web").colour(SHOT_ROOT).size(0.3f).damage(SHOT_DAMAGE).speed(2.2).life(40).spawn();
		OmnitrixSfx.playOn(p, THWIP, 0.9f, 1f, SoundSource.PLAYERS);
		return true;
	}

	/** Shift+R: a spinning net -- everything within 2.5 of where it lands: 12 and rooted 3 s. */
	public boolean webNet(ServerPlayer p) {
		if (!use(p, WEB_NET)) {
			return false;
		}
		anim(p, ANIM_NET);
		AlienProjectileEntity.of(p, "spidermonkey_net").colour(NET_ROOT).size(0.8f).damage(0f).blast((float) NET_RADIUS, NET_DAMAGE).gravity(0.02).speed(1.3)
				.life(60).spawn();
		OmnitrixSfx.playOn(p, NET, 1f, 1f, SoundSource.PLAYERS);
		return true;
	}

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

	/** One of the flurry's fists (public: the gametests throw them without ticking). Returns how many it hit. */
	public int flurryHit(ServerPlayer p, int n) {
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		int hits = 0;
		for (LivingEntity e : around(p, eye.add(look.scale(FLURRY_RANGE * 0.5)), FLURRY_RANGE)) {
			Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eye);
			if (to.length() <= FLURRY_RANGE + e.getBbWidth() * 0.5 && to.normalize().dot(look) > 0.4) {
				e.invulnerableTime = 0;
				if (hurt(p, e, FLURRY_HIT, false)) {
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

	/** Shift+G: a strand at a creature -- 12, and it is reeled in to him (a boss is not). */
	public boolean webYank(ServerPlayer p) {
		LivingEntity t = lookTarget(p, YANK_RANGE);
		if (t == null) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.spidermonkey.no_target").withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		if (!use(p, WEB_YANK)) {
			return false;
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
		strand(p, p.getEyePosition(), t, 10);
		OmnitrixSfx.playOn(p, ZIP, 1f, 1f, SoundSource.PLAYERS);
		return true;
	}

	/** X: a strand to the block under the crosshair -- zipped toward it. False (no cooldown) with nothing to stick to. */
	public boolean webZip(ServerPlayer p) {
		BlockHitResult hit = blockAlong(p, p.getLookAngle(), ZIP_RANGE);
		if (hit == null) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.spidermonkey.no_anchor").withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		if (!use(p, WEB_ZIP)) {
			return false;
		}
		anim(p, ANIM_ZIP);
		Vec3 at = hit.getLocation();
		Vec3 to = at.subtract(p.position());
		double d = to.length();
		p.setDeltaMovement(to.normalize().scale(Math.min(2.4, 0.6 + d * 0.11)).add(0, 0.25, 0));
		p.hurtMarked = true;
		p.fallDistance = 0f;
		setFlag(p, F_AIR, true);
		setTimer(p, AIR_AT, p.level().getGameTime());
		strand(p, at, null, 12);
		OmnitrixSfx.playOn(p, ZIP, 1f, 1.1f, SoundSource.PLAYERS);
		return true;
	}

	/** Shift+X: a strand to a point ahead and above, then a pendulum swing on it (X again lets go). */
	public boolean webSwing(ServerPlayer p) {
		Vec3 look = p.getLookAngle();
		Vec3 flat = look.multiply(1, 0, 1);
		flat = flat.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : flat.normalize();
		// aim up and ahead: at least 45 degrees up, whatever the crosshair says
		Vec3 dir = flat.add(0, Math.max(1.0, look.y * 2), 0).normalize();
		BlockHitResult hit = blockAlong(p, dir, SWING_RANGE);
		if (hit == null) {
			hit = blockAlong(p, look, SWING_RANGE);
		}
		if (hit == null || hit.getLocation().y < p.getY() + 1.5) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.spidermonkey.no_anchor").withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		if (!use(p, WEB_SWING)) {
			return false;
		}
		anim(p, ANIM_SWING);
		Vec3 at = hit.getLocation();
		long now = p.level().getGameTime();
		AlienProjectileEntity s = strand(p, at, null, SWING_MAX + 10);
		SWINGS.put(p.getUUID(), new Swing(at, at.distanceTo(p.position().add(0, 1.0, 0)) * 0.9, now + SWING_MAX, s));
		setFlag(p, F_SWING, true);
		setFlag(p, F_AIR, true);
		setTimer(p, AIR_AT, now);
		// a push off the ground into the arc
		p.setDeltaMovement(p.getDeltaMovement().add(flat.scale(0.6)).add(0, 0.35, 0));
		p.hurtMarked = true;
		OmnitrixSfx.playOn(p, THWIP, 1f, 0.9f, SoundSource.PLAYERS);
		return true;
	}

	/** Whether {@code p} hangs on a swing strand (gametests). */
	public static boolean swinging(ServerPlayer p) {
		return SWINGS.containsKey(p.getUUID());
	}

	private void swingTick(ServerPlayer p, Swing sw) {
		Vec3 body = p.position().add(0, 1.0, 0);
		Vec3 out = body.subtract(sw.anchor);
		double d = out.length();
		Vec3 v = p.getDeltaMovement();
		Vec3 flat = p.getLookAngle().multiply(1, 0, 1);
		flat = flat.lengthSqr() < 1.0e-4 ? Vec3.ZERO : flat.normalize();
		v = v.add(flat.scale(0.06)); // he pumps the swing toward where he looks
		if (d > sw.length && d > 1.0e-3) {
			Vec3 n = out.scale(1.0 / d);
			double outward = v.dot(n);
			if (outward > 0) {
				v = v.subtract(n.scale(outward)); // the strand is taut: no moving away from the anchor
			}
			v = v.subtract(n.scale(Math.min(0.5, (d - sw.length) * 0.3)));
		}
		double sp = v.length();
		if (sp > 1.8) {
			v = v.scale(1.8 / sp);
		}
		p.setDeltaMovement(v);
		p.hurtMarked = true;
		p.fallDistance = 0f;
	}

	private void endSwing(ServerPlayer p, boolean fling) {
		Swing sw = SWINGS.remove(p.getUUID());
		if (sw == null) {
			return;
		}
		sw.strand.discard();
		setFlag(p, F_SWING, false);
		if (fling && !p.onGround()) {
			p.setDeltaMovement(p.getDeltaMovement().scale(1.15).add(0, 0.25, 0));
			p.hurtMarked = true;
			setTimer(p, AIR_AT, p.level().getGameTime());
		}
	}

	/** Z: a spin with the tail -- everything within 3.2: 13 and thrown. */
	public boolean tailWhip(ServerPlayer p) {
		if (!use(p, TAIL_WHIP)) {
			return false;
		}
		anim(p, ANIM_WHIP);
		for (LivingEntity e : around(p, p.position().add(0, 0.6, 0), WHIP_RADIUS)) {
			hurt(p, e, WHIP_DAMAGE, false);
			shove(e, p.position(), 0.9, 0.35);
		}
		level(p).sendParticles(ParticleTypes.SWEEP_ATTACK, p.getX(), p.getY() + 0.6, p.getZ(), 3, 1.2, 0.1, 1.2, 0);
		OmnitrixSfx.playOn(p, WHIP, 1f, 1f, SoundSource.PLAYERS);
		return true;
	}

	/** Shift+Z: a web on the ground ahead (within 12): the first time anything walks in, 5 and rooted 3 s. Lasts 15 s. */
	public boolean webTrap(ServerPlayer p) {
		BlockHitResult hit = blockAlong(p, p.getLookAngle(), TRAP_RANGE);
		Vec3 at = hit != null ? hit.getLocation() : p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(3));
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
		OmnitrixSfx.playOn(p, NET, 0.9f, 1.1f, SoundSource.PLAYERS);
		return true;
	}

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

	/** C (ULTIMATE): wraps the creature under the crosshair (16) in a cocoon: rooted 5 s, squeezed for 5 every half second. */
	public boolean cocoonCrush(ServerPlayer p) {
		LivingEntity t = lookTarget(p, COCOON_RANGE);
		if (t == null) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.spidermonkey.no_target").withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		if (!use(p, COCOON_CRUSH)) {
			return false;
		}
		anim(p, ANIM_COCOON);
		long now = p.level().getGameTime();
		AlienStatus.root(p, t, COCOON_TICKS, 20);
		AlienProjectileEntity w = AlienProjectileEntity.of(p, "spidermonkey_wrap");
		w.moveTo(t.getX(), t.getY(), t.getZ(), 0f, 0f);
		// colour = id + 1, negative: a full cocoon rather than a few strands
		w.colour(-(t.getId() + 1)).size(Math.max(0.6f, t.getBbHeight() * 0.6f)).held(true).speed(0).life(COCOON_TICKS).damage(0f).spawn();
		Cocoon old = COCOONS.put(t.getUUID(), new Cocoon(p.getUUID(), t, now + COCOON_TICKS, now + 4, w));
		if (old != null) {
			old.wrap.discard();
		}
		strand(p, p.getEyePosition(), t, 12);
		OmnitrixSfx.playOn(p, NET, 1.2f, 0.8f, SoundSource.PLAYERS);
		OmnitrixSfx.playOn(p, CHATTER, 0.8f, 1f, SoundSource.PLAYERS);
		return true;
	}

	/** Whether {@code e} is wrapped in a cocoon. */
	public static boolean cocooned(LivingEntity e) {
		return COCOONS.containsKey(e.getUUID());
	}

	/** Shift+C: six webs in a fan -- 5 each, rooted 1.5 s. */
	public boolean webBarrage(ServerPlayer p) {
		if (!use(p, WEB_BARRAGE)) {
			return false;
		}
		anim(p, ANIM_BARRAGE);
		Vec3 look = p.getLookAngle();
		for (int i = 0; i < 6; i++) {
			float yaw = (i - 2.5f) * 8f * Mth.DEG_TO_RAD;
			Vec3 d = look.yRot(yaw).add(0, (i % 2) * 0.05, 0);
			AlienProjectileEntity.of(p, "spidermonkey_web").colour(BARRAGE_ROOT).size(0.25f).damage(BARRAGE_DAMAGE).speed(2.0).life(30).direction(d).spawn();
		}
		OmnitrixSfx.playOn(p, THWIP, 1.1f, 0.9f, SoundSource.PLAYERS);
		OmnitrixSfx.playOn(p, NET, 0.6f, 1.3f, SoundSource.PLAYERS);
		return true;
	}
}
