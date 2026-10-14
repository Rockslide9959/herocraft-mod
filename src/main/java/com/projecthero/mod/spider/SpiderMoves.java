package com.projecthero.mod.spider;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.spider.data.SpiderMoveState;
import com.projecthero.mod.spider.entity.ThrownBlockEntity;
import com.projecthero.mod.spider.web.WebGeometry;
import com.projecthero.mod.spider.web.WebStructureEntity;
import com.projecthero.mod.spider.web.WebStructures;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.1: Spider-Man's 18-move kit -- tap / Shift / hold on each of R G X Z V C -- plus the input resolver, the move
 * sessions and the move-state upkeep.
 *
 * <pre>
 *        tap                  Shift+key              hold (8+ ticks)
 *   R    Web Shot             Web Bomb               Impact Web (charged)
 *   G    Point Zip            Web Yank               Zip Kick
 *   X    Web Strike           Uppercut Launch        Spider-Sense Counter
 *   Z    Web Whip             Grab &amp; Hurl           Web Swing-Throw
 *   V    Spider-Sense Pulse   Web Cocoon             Web Blossom
 *   C    Tightrope            Web Wall               Weave Mode
 * </pre>
 *
 * <b>Input.</b> Shift is read when the key goes down and fires the Shift move at once. A plain press waits: let go
 * within {@code holdTicks} (8) and the tap fires on release; keep holding and the hold move starts (and its release
 * ends it, where it has one). Every move has its own cooldown id ({@link #TAP} / {@link #SHIFT} / {@link #HOLD}), so
 * tapping never puts the Shift or hold move on cooldown. Swinging is not here at all -- it is right-click with an empty
 * hand ({@link #swingStart}).
 *
 * <p>All sessions are static maps dropped by {@link #clearSessionState} ({@code ServerStateReset}) and by
 * {@link #clear} on death / relog / dimension change.
 */
public final class SpiderMoves {
	// ---- ids (cooldown keys and lang keys "projecthero.spider_man.ability.<id>") ----
	public static final String WEB_SHOT = SpiderAbilities.WEB_SHOT;
	public static final String WEB_BOMB = "web_bomb";
	public static final String IMPACT_WEB = SpiderCombat.IMPACT_WEB;
	public static final String POINT_ZIP = "point_zip";
	public static final String WEB_YANK = SpiderAbilities.WEB_YANK;
	public static final String ZIP_KICK = "zip_kick";
	public static final String WEB_STRIKE = "web_strike";
	public static final String UPPERCUT = "uppercut";
	public static final String SPIDER_COUNTER = "spider_counter";
	public static final String WEB_WHIP = "web_whip";
	public static final String GRAB_HURL = "grab_hurl";
	public static final String SWING_THROW = "swing_throw";
	public static final String TIGHTROPE = "tightrope";
	public static final String WEB_WALL = "web_wall";
	public static final String WEAVE = "weave";
	public static final String SPIDER_PULSE = "spider_pulse";
	public static final String WEB_COCOON = "web_cocoon";
	public static final String WEB_BLOSSOM = SpiderAbilities.WEB_BLOSSOM;
	/** Passive traversal: Sprint + Jump in the air. */
	public static final String ZIP_BOOST = "zip_boost";

	/** By slot index (R G X Z V C = AbilitySlot 1..6). */
	public static final String[] TAP = {WEB_SHOT, POINT_ZIP, WEB_STRIKE, WEB_WHIP, SPIDER_PULSE, TIGHTROPE};
	public static final String[] SHIFT = {WEB_BOMB, WEB_YANK, UPPERCUT, GRAB_HURL, WEB_COCOON, WEB_WALL};
	public static final String[] HOLD = {IMPACT_WEB, ZIP_KICK, SPIDER_COUNTER, SWING_THROW, WEB_BLOSSOM, WEAVE};

	/** Hold moves that take over once their key has been down this long; a shorter press is a tap. */
	public static int holdTicks() {
		return Math.max(2, SpiderManConfig.webs().holdTicks);
	}

	/** The slot a move id is bound to (tap, Shift or hold), or null. */
	/**
	 * v0.16.2: the move id actually on slot {@code i} for this player -- kind 0 = tap, 1 = Shift, 2 = hold. The black
	 * suit swaps six of them for symbiote moves ({@code SymbioteBlackSuitAbilities}); the HUD and menus read this.
	 */
	public static String idFor(net.minecraft.world.entity.player.Player player, int i, int kind) {
		String swap = com.projecthero.mod.symbiote.SymbioteBlackSuitAbilities.swapFor(player, i, kind);
		if (swap != null) {
			return swap;
		}
		return kind == 0 ? TAP[i] : kind == 1 ? SHIFT[i] : HOLD[i];
	}

	/** Full cooldown (ticks) of any move id on the Spider-Man keys, the black suit's included. */
	public static int maxCooldown(String id) {
		int symbiote = com.projecthero.mod.symbiote.SymbioteBlackSuitAbilities.cooldownOf(id);
		return symbiote > 0 ? symbiote : cfg(id).cooldown;
	}

	public static AbilitySlot slotOf(String id) {
		for (int i = 0; i < 6; i++) {
			if (TAP[i].equals(id) || SHIFT[i].equals(id) || HOLD[i].equals(id)) {
				return AbilitySlot.byNumber(i + 1);
			}
		}
		return null;
	}

	/** The config entry (cooldown / cost / damage) of a move id. */
	public static SpiderManConfig.Move cfg(String id) {
		SpiderManConfig.Moves m = SpiderManConfig.moves();
		return switch (id) {
			case WEB_SHOT -> m.webShot;
			case WEB_BOMB -> m.webBomb;
			case IMPACT_WEB -> m.impactWeb;
			case POINT_ZIP -> m.pointZip;
			case WEB_YANK -> m.webYank;
			case ZIP_KICK -> m.zipKick;
			case WEB_STRIKE -> m.webStrike;
			case UPPERCUT -> m.uppercut;
			case SPIDER_COUNTER -> m.spiderCounter;
			case WEB_WHIP -> m.webWhip;
			case GRAB_HURL -> m.grabHurl;
			case SWING_THROW -> m.swingThrow;
			case TIGHTROPE -> m.tightrope;
			case WEB_WALL -> m.webWall;
			case WEAVE -> m.weave;
			case SPIDER_PULSE -> m.spiderPulse;
			case WEB_COCOON -> m.webCocoon;
			case WEB_BLOSSOM -> m.webBlossom;
			case ZIP_BOOST -> m.zipBoost;
			default -> new SpiderManConfig.Move();
		};
	}

	// ---------------- sessions ----------------

	private static final class Press {
		final long at;
		final boolean shift;
		boolean holding;
		boolean crush;

		Press(long at, boolean shift) {
			this.at = at;
			this.shift = shift;
		}
	}

	/** Web Strike / Zip Kick: hauled at a target until in reach. */
	private record Haul(int targetId, long start, boolean kick) {
	}

	/** Web Strike's three punches, landing from {@code start}. */
	private static final class Combo {
		final int targetId;
		final long start;
		int hits;

		Combo(int targetId, long start) {
			this.targetId = targetId;
			this.start = start;
		}
	}

	/** Grab &amp; Hurl: the grabbed entity (mob or thrown block) hauled to the hand, then thrown. */
	private record Hurl(int entityId, long start, boolean block) {
	}

	/** A thrown body that hurts what it hits (and whoever it lands in). */
	private record Thrown(int entityId, long start, float damage) {
	}

	/** v0.16.1: an open Weave Mode -- its anchors (no centre until it is finished: the centre is their centroid). */
	private static final class Weave {
		final Vec3 normal;
		final List<Vec3> ends = new ArrayList<>();
		final long start;
		/** Webbing spent on anchors so far (refunded if the weave is cancelled). */
		float spent;
		WebStructureEntity web;

		Weave(Vec3 normal, long start) {
			this.normal = normal;
			this.start = start;
		}
	}

	private static final Map<UUID, Press[]> PRESSES = new ConcurrentHashMap<>();
	private static final Map<UUID, Haul> HAULS = new ConcurrentHashMap<>();
	private static final Map<UUID, Combo> COMBOS = new ConcurrentHashMap<>();
	private static final Map<UUID, long[]> AIR_COMBO = new ConcurrentHashMap<>(); // {targetId, until}
	private static final Map<UUID, Long> COUNTER_UNTIL = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> DODGE_UNTIL = new ConcurrentHashMap<>();
	private static final Map<UUID, Hurl> HURLS = new ConcurrentHashMap<>();
	private static final Map<UUID, List<Thrown>> THROWN = new ConcurrentHashMap<>();
	private static final Map<UUID, Weave> WEAVES = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> ANCHOR_A_AT = new ConcurrentHashMap<>();
	/** v0.16.1: the web anchor A sits on, when it was placed on a strand (a junction). */
	private static final Map<UUID, UUID> ANCHOR_A_PARENT = new ConcurrentHashMap<>();
	private static final Map<UUID, Boolean> THWIP_HAND = new ConcurrentHashMap<>();
	/** {lastY, lastVy, lastHorizontalSpeed, wasOnGround(1/0), lastX, lastZ} for the landing / release-flip poses. */
	private static final Map<UUID, double[]> MOTION = new ConcurrentHashMap<>();

	private SpiderMoves() {
	}

	public static void initialize() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(SpiderMoves::onAllowDamage);
	}

	public static void clearSessionState() {
		PRESSES.clear();
		HAULS.clear();
		SNAGS.clear();
		COMBOS.clear();
		AIR_COMBO.clear();
		COUNTER_UNTIL.clear();
		DODGE_UNTIL.clear();
		HURLS.clear();
		THROWN.clear();
		WEAVES.clear();
		ANCHOR_A_AT.clear();
		ANCHOR_A_PARENT.clear();
		THWIP_HAND.clear();
		MOTION.clear();
		HANGS.clear();
	}

	/** Death / relog / dimension change / power loss: drop every session and stance. */
	public static void clear(ServerPlayer player) {
		UUID id = player.getUUID();
		PRESSES.remove(id);
		HAULS.remove(id);
		SNAGS.remove(id);
		COMBOS.remove(id);
		AIR_COMBO.remove(id);
		COUNTER_UNTIL.remove(id);
		DODGE_UNTIL.remove(id);
		HURLS.remove(id);
		THROWN.remove(id);
		Weave w = WEAVES.remove(id);
		if (w != null && w.web != null && !w.web.isRemoved()) {
			w.web.dissolve(false); // an unfinished weave (only a preview) never outlives Weave Mode
		}
		ANCHOR_A_AT.remove(id);
		ANCHOR_A_PARENT.remove(id);
		MOTION.remove(id);
		HANGS.remove(id);
		SpiderMoveState s = player.getAttachedOrElse(ModAttachments.SPIDER_MOVE_STATE, null);
		if (s != null && (s.flags != 0 || s.animId != SpiderAnim.NONE)) {
			player.setAttached(ModAttachments.SPIDER_MOVE_STATE, new SpiderMoveState());
		}
	}

	// ---------------- move state ----------------

	public static SpiderMoveState state(Player player) {
		return player.getAttachedOrCreate(ModAttachments.SPIDER_MOVE_STATE);
	}

	private static void save(ServerPlayer player, SpiderMoveState s) {
		player.setAttached(ModAttachments.SPIDER_MOVE_STATE, s);
	}

	/** Start an animation on every client. */
	public static void anim(ServerPlayer player, int animId) {
		SpiderMoveState c = state(player).copy();
		c.animId = animId;
		c.animStart = player.level().getGameTime();
		save(player, c);
	}

	public static void setFlag(ServerPlayer player, int flag, boolean on) {
		SpiderMoveState s = state(player);
		if (s.has(flag) == on) {
			return;
		}
		SpiderMoveState c = s.copy();
		c.set(flag, on);
		if (on) {
			c.chargeStart = player.level().getGameTime();
		}
		save(player, c);
	}

	/** Which hand a strand leaves from right now (the thwip alternates; everything else is the right). */
	public static boolean rightHand(ServerPlayer player) {
		return state(player).animId != SpiderAnim.THWIP_LEFT;
	}

	/** Flip and return the thwip hand (true = right). */
	static boolean nextThwipHand(ServerPlayer player) {
		boolean right = !THWIP_HAND.getOrDefault(player.getUUID(), false);
		THWIP_HAND.put(player.getUUID(), right);
		return right;
	}

	/** The web-shooter thwip, at a pitch. */
	public static void thwip(ServerPlayer player, float pitch) {
		play(player, SpiderSounds.THWIP, 0.8f, pitch);
	}

	/** v0.16.1: Shift+N has muted his notification sounds (Spider-Sense, the web alarm, web structures). Both sides. */
	public static boolean soundsOff(Player player) {
		return player.getAttachedOrElse(ModAttachments.SPIDER_SOUNDS_OFF, false);
	}

	/** v0.16.1: show the Spider-Sense tingle lines round his head for a moment (synced; only re-saved near the end). */
	public static void tingle(ServerPlayer player, long now) {
		SpiderMoveState s = state(player);
		if (s.tingleUntil - now > 10) {
			return;
		}
		SpiderMoveState c = s.copy();
		c.tingleUntil = now + 30;
		save(player, c);
	}

	/** A notification sound (Spider-Sense, web structures): skipped while he has them muted. */
	static void notify(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
		if (!soundsOff(player)) {
			play(player, sound, volume, pitch);
		}
	}

	/** Shift+N: mute / unmute his notification sounds (saved per player; default on). Returns true when they are on. */
	public static boolean toggleSounds(ServerPlayer player) {
		if (!SpiderMan.hasPower(player)) {
			return false;
		}
		boolean off = !soundsOff(player);
		player.setAttached(ModAttachments.SPIDER_SOUNDS_OFF, off);
		player.displayClientMessage(Component.translatable(off
				? "message.projecthero.spider_man.sounds_off" : "message.projecthero.spider_man.sounds_on"), true);
		return !off;
	}

	static void play(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, volume,
				pitch * (0.95f + player.getRandom().nextFloat() * 0.1f));
	}

	private static boolean blackSuit(ServerPlayer player) {
		return com.projecthero.mod.symbiote.Symbiote.isActive(player)
				&& com.projecthero.mod.symbiote.SymbioteHostType.of(player) == com.projecthero.mod.symbiote.SymbioteHostType.SPIDER_MAN;
	}

	private static boolean gate(ServerPlayer player, String id) {
		return SpiderAbilities.gate(player, id, cfg(id).cost);
	}

	private static void fired(ServerPlayer player, String id) {
		SpiderAbilities.fired(player, id, cfg(id).cost, cfg(id).cooldown);
	}

	private static void noTarget(ServerPlayer player) {
		player.displayClientMessage(Component.translatable("message.projecthero.spider_man.no_target"), true);
	}

	private static boolean harmable(ServerPlayer player, LivingEntity e) {
		return e != player && e.isAlive() && HeroTargets.canHarm(player, e)
				&& !com.projecthero.mod.squad.Squads.areAllies(player, e);
	}

	// ---------------- input ----------------

	/** A key edge from {@code SpiderManAbilityManager}: tap / Shift / hold resolution. */
	public static void handle(ServerPlayer player, AbilitySlot slot, boolean pressed) {
		int i = slot.index();
		if (i >= 6) {
			return;
		}
		long now = player.level().getGameTime();
		Press[] presses = PRESSES.computeIfAbsent(player.getUUID(), k -> new Press[6]);
		if (pressed) {
			if (presses[i] != null) {
				return; // key repeat
			}
			// v0.16.2 FIX (Weave Mode while hanging upside down): Sneak is the "lower me" key on the hang line, so it is
			// usually held there -- it must not turn C into Shift+C (Web Wall), or holding C never opened Weave Mode
			boolean shift = player.isShiftKeyDown() && !(i == 5 && hangingUpsideDown(player));
			Press p = new Press(now, shift);
			presses[i] = p;
			if (p.shift) {
				shiftMove(player, i, p);
			}
			return;
		}
		Press p = presses[i];
		presses[i] = null;
		if (p == null) {
			return;
		}
		if (p.crush) {
			com.projecthero.mod.symbiote.SymbioteBlackSuitAbilities.releaseConsume(player);
			return;
		}
		if (p.shift) {
			return;
		}
		if (!p.holding && now - p.at >= holdTicks()) {
			p.holding = true;
			holdStart(player, i);
		}
		if (p.holding) {
			holdRelease(player, i);
		} else {
			tap(player, i);
		}
	}

	private static void tap(ServerPlayer player, int i) {
		if (i == 2 && blackSuit(player)) {
			com.projecthero.mod.symbiote.SymbioteBlackSuitAbilities.tendrilLash(player); // v0.16.2: the black suit's X
			return;
		}
		switch (i) {
			case 0 -> SpiderAbilities.webShot(player);
			case 1 -> SpiderAbilities.webZip(player);
			case 2 -> webStrike(player);
			case 3 -> webWhip(player);
			case 4 -> spiderPulse(player);
			case 5 -> tightrope(player);
			default -> {
			}
		}
	}

	private static void shiftMove(ServerPlayer player, int i, Press p) {
		boolean black = blackSuit(player);
		switch (i) {
			case 0 -> webBomb(player);
			case 1 -> {
				if (!SpiderCombat.tryDisarm(player)) {
					SpiderAbilities.webYank(player);
				}
			}
			case 2 -> {
				if (black) {
					com.projecthero.mod.symbiote.SymbioteBlackSuitAbilities.spikeBurst(player);
				} else {
					uppercut(player);
				}
			}
			case 3 -> {
				if (endUpsideHang(player)) {
					return; // v0.16.1: Shift+Z while hanging upside down lets go -- before Grab & Hurl / the Black Suit's move
				}
				if (black) {
					p.crush = true;
					com.projecthero.mod.symbiote.SymbioteBlackSuitAbilities.beginConsume(player);
				} else {
					grabHurl(player);
				}
			}
			case 4 -> {
				if (webSnag(player)) {
					// v0.16.2 (user): standing on his tightrope / woven web, Shift+V snags a target onto it, cocooned
				} else if (black) {
					com.projecthero.mod.symbiote.SymbioteBlackSuitAbilities.inkShroud(player);
				} else {
					webCocoon(player);
				}
			}
			case 5 -> {
				// v0.16.2: the black suit's Shift+C is only ever the Slam (no Web Wall while it cools down)
				if (black) {
					com.projecthero.mod.symbiote.SymbioteBlackSuitAbilities.slam(player);
				} else {
					webWall(player);
				}
			}
			default -> {
			}
		}
	}

	private static void holdStart(ServerPlayer player, int i) {
		if (i == 4 && blackSuit(player)) {
			com.projecthero.mod.symbiote.SymbioteBlackSuitAbilities.takeOver(player); // v0.16.2: hold V
			return;
		}
		switch (i) {
			case 0 -> beginImpactCharge(player);
			case 1 -> zipKick(player);
			case 2 -> counterStance(player);
			case 3 -> SpiderCombat.beginThrow(player);
			case 4 -> SpiderAbilities.beginWebBlossom(player);
			case 5 -> beginWeave(player);
			default -> {
			}
		}
	}

	private static void holdRelease(ServerPlayer player, int i) {
		switch (i) {
			case 0 -> releaseImpact(player);
			case 3 -> SpiderCombat.releaseThrow(player);
			case 4 -> {
				if (!blackSuit(player)) {
					SpiderAbilities.releaseWebBlossom(player);
				}
			}
			case 5 -> finishWeave(player);
			default -> {
			}
		}
	}

	// ---------------- R ----------------

	/** Shift+R Web Bomb: a splash of webbing at the aimed point that roots everything in it. */
	public static void webBomb(ServerPlayer player) {
		if (!gate(player, WEB_BOMB)) {
			return;
		}
		SpiderManConfig.Moves m = SpiderManConfig.moves();
		ServerLevel level = (ServerLevel) player.level();
		LivingEntity aimed = AbilityHelpers.raycastEntity(player, 30.0);
		Vec3 at;
		if (aimed != null) {
			at = SpiderCombat.center(aimed);
		} else {
			BlockHitResult hit = AbilityHelpers.raycastBlock(player, 30.0);
			at = hit.getType() == HitResult.Type.BLOCK ? hit.getLocation()
					: player.getEyePosition().add(player.getLookAngle().scale(30.0));
		}
		// v0.16.1: a real projectile -- a big web glob lobbed in an arc; the splash happens where it lands (webBombBurst)
		Vec3 from = player.getEyePosition().add(player.getLookAngle().scale(0.6)).add(0, -0.15, 0);
		level.addFreshEntity(new com.projecthero.mod.spider.entity.SpiderWebBombEntity(level, player, from, at));
		thwip(player, 0.8f);
		anim(player, SpiderAnim.THWIP_BOTH);
		fired(player, WEB_BOMB);
	}

	/** v0.16.1: the Web Bomb's splash where its glob lands: the root, the damage, the web rings and real cobwebs. */
	public static int webBombBurst(ServerPlayer player, Vec3 at) {
		SpiderManConfig.Moves m = SpiderManConfig.moves();
		if (!(player.level() instanceof ServerLevel level)) {
			return 0;
		}
		int caught = 0;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(m.webBombRadius),
				x -> harmable(player, x) && x.distanceToSqr(at) <= m.webBombRadius * m.webBombRadius * 1.5)) {
			AbilityHelpers.hurt(player, e, cfg(WEB_BOMB).damage);
			int held = SpiderWebs.cocoonFor(player, e, m.webBombRootTicks);
			SpiderWebs.gunk(e, held); // v0.16.2 (user): web-bombed, it cannot fire projectiles while held
			SpiderWebs.addStickStack(e, level.getGameTime());
			e.setDeltaMovement(0, Math.min(0.0, e.getDeltaMovement().y), 0);
			e.hurtMarked = true;
			caught++;
		}
		for (int ring = 1; ring <= 3; ring++) {
			double r = m.webBombRadius * ring / 3.0;
			for (int a = 0; a < 360; a += 20) {
				double rad = Math.toRadians(a);
				level.sendParticles(ParticleTypes.ITEM_COBWEB, at.x + Math.cos(rad) * r, at.y + 0.3, at.z + Math.sin(rad) * r,
						1, 0.05, 0.05, 0.05, 0.0);
			}
		}
		level.sendParticles(ParticleTypes.ITEM_COBWEB, at.x, at.y + 0.5, at.z, 40, 1.0, 0.6, 1.0, 0.05);
		// v0.16.1: real (temporary) cobwebs fill the air in the splash
		SpiderCobwebs.fillAround(player, at, m.webBombRadius * 0.75, SpiderManConfig.webs().cobwebsPerCast);
		level.playSound(null, at.x, at.y, at.z, SpiderSounds.SPLAT, SoundSource.PLAYERS, 1.2f, 0.8f);
		if (caught > 0) {
			player.displayClientMessage(Component.translatable("message.projecthero.spider_man.web_bomb", caught), true);
		}
		return caught;
	}

	private static void beginImpactCharge(ServerPlayer player) {
		if (!gate(player, IMPACT_WEB)) {
			return;
		}
		setFlag(player, SpiderMoveState.FLAG_CHARGING_IMPACT, true);
		play(player, SoundEvents.SPIDER_AMBIENT, 0.4f, 1.5f);
	}

	private static void releaseImpact(ServerPlayer player) {
		SpiderMoveState s = state(player);
		if (!s.has(SpiderMoveState.FLAG_CHARGING_IMPACT)) {
			return;
		}
		float charge = Math.min(1.0f, (player.level().getGameTime() - s.chargeStart)
				/ (float) Math.max(1, SpiderManConfig.moves().impactChargeTicks));
		setFlag(player, SpiderMoveState.FLAG_CHARGING_IMPACT, false);
		SpiderCombat.impactWeb(player, charge);
	}

	// ---------------- G ----------------

	/** Hold G Zip Kick: zip at the aimed enemy and land a flying kick. */
	public static void zipKick(ServerPlayer player) {
		if (HAULS.containsKey(player.getUUID()) || !gate(player, ZIP_KICK)) {
			return;
		}
		LivingEntity target = SpiderCombat.pickTarget(player, SpiderManConfig.moves().zipKickRange);
		if (target == null || !harmable(player, target)) {
			noTarget(player);
			return;
		}
		startHaul(player, target, true);
		fired(player, ZIP_KICK);
	}

	private static void startHaul(ServerPlayer player, LivingEntity target, boolean kick) {
		SpiderSwing.detach(player, false);
		HAULS.put(player.getUUID(), new Haul(target.getId(), player.level().getGameTime(), kick));
		SpiderCombat.sendStrand(player, SpiderCombat.SLOT_STRIKE, target, SpiderCombat.center(target), 40, 0);
		thwip(player, kick ? 0.9f : 1.0f);
		play(player, SpiderSounds.ZIP, 0.8f, 1.0f);
		anim(player, kick ? SpiderAnim.ZIP_KICK : SpiderAnim.ZIP);
	}

	// ---------------- v0.16.2 Web Snag (Shift+V on his own web) ----------------

	/** A target being reeled onto his web: where on the web it goes. */
	private record Snag(int targetId, long start, Vec3 dest) {
	}

	private static final Map<UUID, Snag> SNAGS = new ConcurrentHashMap<>();
	private static final double SNAG_RANGE = 24.0;
	private static final double SNAG_SPEED = 1.3;
	private static final int SNAG_MAX_TICKS = 30;

	/**
	 * Shift+V while standing on one of his tightropes / woven webs: a line to the target he is aiming at reels it
	 * through the air onto the strand beside him, where it is cocooned ({@link SpiderWrap}, for as long as the web holds).
	 * Returns false when he is not on a web (Shift+V does its normal move then). Web Cocoon's cost and cooldown.
	 */
	public static boolean webSnag(ServerPlayer player) {
		WebStructures.StrandHit under = WebStructures.strandAtFeet(player);
		if (under == null || SNAGS.containsKey(player.getUUID())) {
			return under != null;
		}
		LivingEntity target = SpiderCombat.pickTarget(player, SNAG_RANGE);
		if (target == null || !harmable(player, target)) {
			noTarget(player);
			return true;
		}
		if (SpiderWebs.isBoss(target) || SpiderWebs.tooHeavyToThrow(target) || SpiderWebs.ignoresWebs(target)) {
			player.displayClientMessage(Component.translatable("message.projecthero.spider_man.too_heavy"), true);
			return true;
		}
		if (!gate(player, WEB_COCOON)) {
			return true;
		}
		// a spot on the web about 1.6 blocks from him, toward the target; it hangs with the strand through its chest
		Vec3 toward = target.position().subtract(player.position());
		Vec3 flat = new Vec3(toward.x, 0, toward.z);
		flat = flat.lengthSqr() < 1.0e-4 ? player.getLookAngle() : flat.normalize();
		Vec3 onWeb = WebStructures.nearestPoint(player, under.point().add(flat.scale(1.6)), 3.0);
		if (onWeb == null) {
			onWeb = under.point();
		}
		Vec3 dest = onWeb.subtract(0, target.getBbHeight() * 0.6, 0);
		WebStructures.tearFree(target);
		SNAGS.put(player.getUUID(), new Snag(target.getId(), player.level().getGameTime(), dest));
		SpiderCombat.sendStrand(player, SpiderCombat.SLOT_YANK, target, SpiderCombat.center(target), SNAG_MAX_TICKS, 8);
		anim(player, SpiderAnim.THWIP_BOTH);
		thwip(player, 0.9f);
		fired(player, WEB_COCOON);
		return true;
	}

	private static void tickSnag(ServerPlayer player, Snag sn) {
		ServerLevel level = (ServerLevel) player.level();
		Entity e = level.getEntity(sn.targetId());
		if (!(e instanceof LivingEntity target) || !target.isAlive() || level.getGameTime() - sn.start() > SNAG_MAX_TICKS + 10) {
			SNAGS.remove(player.getUUID());
			return;
		}
		Vec3 delta = sn.dest().subtract(target.position());
		double dist = delta.length();
		if (dist <= SNAG_SPEED || level.getGameTime() - sn.start() >= SNAG_MAX_TICKS) {
			target.teleportTo(sn.dest().x, sn.dest().y, sn.dest().z);
			target.setDeltaMovement(Vec3.ZERO);
			SpiderWrap.wrap(target);
			SNAGS.remove(player.getUUID());
			return;
		}
		// reeled straight in along the line -- no gravity fighting it
		Vec3 step = delta.scale(SNAG_SPEED / dist);
		target.teleportTo(target.getX() + step.x, target.getY() + step.y, target.getZ() + step.z);
		target.setDeltaMovement(Vec3.ZERO);
		target.resetFallDistance();
		target.hurtMarked = true;
	}

	private static void tickHaul(ServerPlayer player, Haul h) {
		ServerLevel level = (ServerLevel) player.level();
		Entity e = level.getEntity(h.targetId());
		if (!(e instanceof LivingEntity target) || !target.isAlive() || level.getGameTime() - h.start() > 40) {
			endHaul(player, e instanceof LivingEntity le ? SpiderCombat.center(le) : player.position());
			return;
		}
		Vec3 to = SpiderCombat.center(target);
		Vec3 me = player.position().add(0, player.getBbHeight() * 0.5, 0);
		Vec3 delta = to.subtract(me);
		double dist = delta.length();
		if (dist <= 2.4) {
			endHaul(player, to);
			if (h.kick()) {
				kickHit(player, target, delta.normalize());
			} else {
				COMBOS.put(player.getUUID(), new Combo(target.getId(), level.getGameTime()));
				anim(player, SpiderAnim.STRIKE_COMBO);
				AbilityHelpers.launchSelf(player, Vec3.ZERO);
			}
			return;
		}
		AbilityHelpers.launchSelf(player, delta.normalize().scale(Math.min(1.9, 0.9 + dist * 0.1)));
	}

	private static void endHaul(ServerPlayer player, Vec3 last) {
		HAULS.remove(player.getUUID());
		SpiderCombat.sendStrand(player, SpiderCombat.SLOT_STRIKE, null, last, 0, 30);
	}

	private static void kickHit(ServerPlayer player, LivingEntity target, Vec3 dir) {
		ServerLevel level = (ServerLevel) player.level();
		AbilityHelpers.hurtBurst(player, target, cfg(ZIP_KICK).damage);
		Vec3 flat = new Vec3(dir.x, 0, dir.z);
		flat = flat.lengthSqr() < 1.0e-4 ? player.getLookAngle() : flat.normalize();
		boolean heavy = SpiderWebs.tooHeavyToThrow(target); // v0.16.2: the throw rule
		if (!heavy) {
			target.setDeltaMovement(flat.scale(2.0).add(0, 0.45, 0));
			target.hurtMarked = true;
		}
		// bounce back off the kick
		AbilityHelpers.launchSelf(player, flat.scale(-0.45).add(0, 0.55, 0));
		Vec3 c = SpiderCombat.center(target);
		level.sendParticles(ParticleTypes.SWEEP_ATTACK, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 18, 0.3, 0.3, 0.3, 0.3);
		play(player, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0f, 0.8f);
		play(player, SoundEvents.PLAYER_ATTACK_STRONG, 1.0f, 0.7f);
	}

	// ---------------- X ----------------

	/** X Web Strike: zip in on a line and land a three-punch combo. */
	public static void webStrike(ServerPlayer player) {
		if (HAULS.containsKey(player.getUUID()) || COMBOS.containsKey(player.getUUID()) || !gate(player, WEB_STRIKE)) {
			return;
		}
		LivingEntity target = SpiderCombat.pickTarget(player, SpiderManConfig.moves().webStrikeRange);
		if (target == null || !harmable(player, target)) {
			noTarget(player);
			return;
		}
		if (target.distanceTo(player) <= 3.0) {
			COMBOS.put(player.getUUID(), new Combo(target.getId(), player.level().getGameTime()));
			anim(player, SpiderAnim.STRIKE_COMBO);
		} else {
			startHaul(player, target, false);
		}
		fired(player, WEB_STRIKE);
	}

	private static final int[] COMBO_AT = {1, 8, 15};

	private static void tickCombo(ServerPlayer player, Combo c) {
		ServerLevel level = (ServerLevel) player.level();
		Entity e = level.getEntity(c.targetId);
		long age = level.getGameTime() - c.start;
		if (!(e instanceof LivingEntity target) || !target.isAlive() || c.hits >= COMBO_AT.length || age > 30) {
			COMBOS.remove(player.getUUID());
			return;
		}
		if (age < COMBO_AT[c.hits]) {
			return;
		}
		boolean finisher = c.hits == COMBO_AT.length - 1;
		c.hits++;
		if (target.distanceTo(player) > 4.5) {
			return; // it got away: the punch whiffs
		}
		float dmg = finisher ? SpiderManConfig.moves().webStrikeFinisher : cfg(WEB_STRIKE).damage;
		AbilityHelpers.hurtBurst(player, target, dmg);
		Vec3 c2 = SpiderCombat.center(target);
		level.sendParticles(ParticleTypes.CRIT, c2.x, c2.y, c2.z, finisher ? 14 : 6, 0.25, 0.25, 0.25, 0.2);
		if (finisher) {
			AbilityHelpers.knockbackFrom(target, player.position(), 1.6);
			target.setDeltaMovement(target.getDeltaMovement().add(0, 0.3, 0));
			target.hurtMarked = true;
			play(player, SoundEvents.PLAYER_ATTACK_STRONG, 1.0f, 0.9f);
		} else {
			target.setDeltaMovement(target.getDeltaMovement().multiply(0.3, 1.0, 0.3));
			play(player, SoundEvents.PLAYER_ATTACK_WEAK, 1.0f, 1.2f);
		}
	}

	/** Shift+X Uppercut Launch: knock the enemy skyward and open an air-combo window. */
	public static void uppercut(ServerPlayer player) {
		if (!gate(player, UPPERCUT)) {
			return;
		}
		LivingEntity target = AbilityHelpers.raycastEntity(player, 5.0);
		if (target == null) {
			target = SpiderAbilities.nearestLivingInAim(player, 5.0);
		}
		anim(player, SpiderAnim.UPPERCUT);
		if (target == null || !harmable(player, target)) {
			noTarget(player);
			return;
		}
		ServerLevel level = (ServerLevel) player.level();
		AbilityHelpers.hurtBurst(player, target, cfg(UPPERCUT).damage);
		boolean heavy = SpiderWebs.tooHeavyToThrow(target); // v0.16.2: the throw rule
		target.setDeltaMovement(target.getDeltaMovement().multiply(0.2, 0, 0.2).add(0, heavy ? 0.35 : 1.05, 0));
		target.hurtMarked = true;
		AbilityHelpers.launchSelf(player, new Vec3(0, 0.95, 0));
		long until = level.getGameTime() + SpiderManConfig.moves().airComboTicks;
		AIR_COMBO.put(player.getUUID(), new long[] {target.getId(), until});
		setFlag(player, SpiderMoveState.FLAG_AIR_COMBO, true);
		Vec3 c = SpiderCombat.center(target);
		level.sendParticles(ParticleTypes.CLOUD, c.x, target.getY(), c.z, 10, 0.3, 0.05, 0.3, 0.05);
		level.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 14, 0.25, 0.4, 0.25, 0.3);
		play(player, SoundEvents.PLAYER_ATTACK_STRONG, 1.0f, 1.2f);
		fired(player, UPPERCUT);
	}

	/** Hold X Spider-Sense Counter: a short stance -- the next hit is dodged with a flip and countered. */
	public static void counterStance(ServerPlayer player) {
		if (!gate(player, SPIDER_COUNTER)) {
			return;
		}
		SpiderWebReserve.spend(player, cfg(SPIDER_COUNTER).cost, true);
		COUNTER_UNTIL.put(player.getUUID(), player.level().getGameTime() + SpiderManConfig.moves().counterStanceTicks);
		setFlag(player, SpiderMoveState.FLAG_COUNTER, true);
		notify(player, SpiderSounds.SENSE, 0.7f, 1.3f);
	}

	private static void endCounter(ServerPlayer player, boolean landed) {
		COUNTER_UNTIL.remove(player.getUUID());
		setFlag(player, SpiderMoveState.FLAG_COUNTER, false);
		// a whiffed stance is a short wait; a landed counter is the full cooldown
		SpiderMan.triggerCooldown(player, SPIDER_COUNTER, landed ? cfg(SPIDER_COUNTER).cooldown : 60);
	}

	/** True when the counter stance is waiting for a hit (tests). */
	public static boolean inCounterStance(ServerPlayer player) {
		Long until = COUNTER_UNTIL.get(player.getUUID());
		return until != null && player.level().getGameTime() <= until;
	}

	private static void counter(ServerPlayer player, DamageSource source) {
		endCounter(player, true);
		ServerLevel level = (ServerLevel) player.level();
		Entity attacker = source.getEntity();
		Entity direct = source.getDirectEntity();
		Vec3 from = attacker != null ? attacker.position() : direct != null ? direct.position() : player.position();
		Vec3 away = player.position().subtract(from);
		away = new Vec3(away.x, 0, away.z);
		away = away.lengthSqr() < 1.0e-4 ? player.getLookAngle().reverse() : away.normalize();
		AbilityHelpers.launchSelf(player, away.scale(0.35).add(0, 0.6, 0));
		anim(player, SpiderAnim.COUNTER_FLIP);
		notify(player, SpiderSounds.SENSE, 0.9f, 1.6f);
		level.sendParticles(ParticleTypes.CRIT, player.getX(), player.getY() + 1.0, player.getZ(), 10, 0.4, 0.5, 0.4, 0.2);
		if (direct instanceof Projectile p && !(direct instanceof LivingEntity)) {
			// sent straight back where it came from
			p.setDeltaMovement(p.getDeltaMovement().scale(-1.0));
			p.setOwner(player);
			p.hurtMarked = true;
		}
		if (attacker instanceof LivingEntity le && le != player && le.distanceTo(player) <= 10.0 && harmable(player, le)) {
			AbilityHelpers.hurtBurst(player, le, cfg(SPIDER_COUNTER).damage);
			AbilityHelpers.knockbackFrom(le, player.position(), 1.4);
			SpiderCombat.sendStrand(player, SpiderCombat.SLOT_COUNTER, le, SpiderCombat.center(le), 3, 20);
			play(player, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0f, 1.0f);
		}
	}

	// ---------------- Z ----------------

	/** Z Web Whip: spin a strand round you, hitting everything within reach. */
	public static void webWhip(ServerPlayer player) {
		// v0.16.1: on a ceiling / woven web / tightrope, Z drops into the upside-down hang; hanging, it zips back up
		if (hangKey(player)) {
			return;
		}
		if (!gate(player, WEB_WHIP)) {
			return;
		}
		ServerLevel level = (ServerLevel) player.level();
		double r = SpiderManConfig.moves().webWhipRadius;
		int hit = 0;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(r, 1.5, r),
				x -> harmable(player, x) && x.distanceToSqr(player) <= (r + 0.5) * (r + 0.5))) {
			AbilityHelpers.hurt(player, e, cfg(WEB_WHIP).damage);
			WebStructures.tearFree(e); // v0.16.1 the stuck-pull rule: the lash tears it off any web it was stuck on
			AbilityHelpers.knockbackFrom(e, player.position(), 1.1);
			SpiderWebs.addStickStack(e, level.getGameTime());
			hit++;
		}
		// (v0.16.1: the strand whirled round him is the real web line -- SpiderWebLineRenderer draws it from the WHIP anim)
		level.sendParticles(ParticleTypes.SWEEP_ATTACK, player.getX(), player.getY() + 1.0, player.getZ(), 4, r * 0.4, 0.1, r * 0.4, 0);
		thwip(player, 0.85f);
		play(player, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 0.75f);
		anim(player, SpiderAnim.WHIP);
		fired(player, WEB_WHIP);
	}

	/**
	 * Shift+Z Grab &amp; Hurl: web the mob you aim at -- or, with nothing to grab, a loose block in front of you -- haul it
	 * to your hand and throw it. A block must be an ordinary one (no block entity, breakable, not a fluid), and block
	 * grabbing obeys the {@code blockGrabbing} config, the mod's terrain-damage switch, the mobGriefing rule and any
	 * claim mod hooked into block breaking.
	 */
	public static void grabHurl(ServerPlayer player) {
		if (HURLS.containsKey(player.getUUID()) || !gate(player, GRAB_HURL)) {
			return;
		}
		ServerLevel level = (ServerLevel) player.level();
		double range = SpiderManConfig.moves().grabRange;
		LivingEntity target = AbilityHelpers.raycastEntity(player, range);
		if (target == null) {
			target = SpiderAbilities.nearestLivingInAim(player, range);
		}
		if (target != null && harmable(player, target)) {
			if (SpiderWebs.tooHeavyToThrow(target)) { // v0.16.2: bosses, iron golems, too big
				player.displayClientMessage(Component.translatable("message.projecthero.spider_man.too_heavy"), true);
				return;
			}
			WebStructures.tearFree(target); // v0.16.1 the stuck-pull rule: it comes off the web, and the web snaps
			HURLS.put(player.getUUID(), new Hurl(target.getId(), level.getGameTime(), false));
			SpiderCombat.sendStrand(player, SpiderCombat.SLOT_GRAB, target, SpiderCombat.center(target), 14, 30);
		} else {
			BlockHitResult hit = AbilityHelpers.raycastBlock(player, range);
			if (hit.getType() != HitResult.Type.BLOCK) {
				noTarget(player);
				return;
			}
			BlockPos pos = hit.getBlockPos();
			if (!canGrabBlock(player, pos)) {
				player.displayClientMessage(Component.translatable(SpiderManConfig.webs().blockGrabbing
						? "message.projecthero.spider_man.cannot_grab_block"
						: "message.projecthero.spider_man.block_grab_off"), true);
				return;
			}
			BlockState state = level.getBlockState(pos);
			level.removeBlock(pos, false);
			ThrownBlockEntity block = ThrownBlockEntity.create(level, player, state,
					Vec3.atBottomCenterOf(pos), cfg(GRAB_HURL).damage);
			block.held = true;
			level.addFreshEntity(block);
			HURLS.put(player.getUUID(), new Hurl(block.getId(), level.getGameTime(), true));
			SpiderCombat.sendStrand(player, SpiderCombat.SLOT_GRAB, block.getId(), Vec3.atCenterOf(pos), 14, 30);
		}
		thwip(player, 0.9f);
		anim(player, SpiderAnim.HURL);
		fired(player, GRAB_HURL);
	}

	/** Whether Grab &amp; Hurl may rip this block out (see {@link #grabHurl}). */
	public static boolean canGrabBlock(ServerPlayer player, BlockPos pos) {
		ServerLevel level = (ServerLevel) player.level();
		if (!SpiderManConfig.webs().blockGrabbing || !AbilityHelpers.canGrief()
				|| !level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)
				|| !player.mayBuild() || !level.mayInteract(player, pos)) {
			return false;
		}
		BlockState state = level.getBlockState(pos);
		if (state.isAir() || state.hasBlockEntity() || !state.getFluidState().isEmpty()) {
			return false;
		}
		float hardness = state.getDestroySpeed(level, pos);
		if (hardness < 0.0f || hardness > 25.0f) {
			return false; // bedrock, barriers, obsidian-class
		}
		return net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.BEFORE.invoker()
				.beforeBlockBreak(level, player, pos, state, null);
	}

	private static void tickHurl(ServerPlayer player, Hurl h) {
		ServerLevel level = (ServerLevel) player.level();
		Entity e = level.getEntity(h.entityId());
		long age = level.getGameTime() - h.start();
		if (e == null || !e.isAlive()) {
			HURLS.remove(player.getUUID());
			return;
		}
		Vec3 look = player.getLookAngle();
		Vec3 hold = player.getEyePosition().add(look.scale(2.4)).add(0, 0.4, 0);
		Vec3 centre = e.position().add(0, e.getBbHeight() * 0.5, 0);
		if (age < 9 && centre.distanceTo(hold) > 0.8) {
			Vec3 v = hold.subtract(centre).scale(0.45);
			if (v.length() > 2.2) {
				v = v.normalize().scale(2.2);
			}
			e.setDeltaMovement(v);
			e.hurtMarked = true;
			e.resetFallDistance();
			return;
		}
		// throw
		HURLS.remove(player.getUUID());
		e.setDeltaMovement(look.scale(2.3).add(0, 0.22, 0));
		e.hurtMarked = true;
		if (e instanceof ThrownBlockEntity block) {
			block.held = false;
		} else if (e instanceof LivingEntity le) {
			watchThrown(player, le, cfg(GRAB_HURL).damage);
		}
		SpiderCombat.sendStrand(player, SpiderCombat.SLOT_GRAB, null, centre, 0, 20);
		play(player, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 0.7f);
	}

	/** A thrown body hurts whatever it hits (a wall, the ground, another mob) once, within 1.5 s. */
	public static void watchThrown(ServerPlayer player, LivingEntity body, float damage) {
		THROWN.computeIfAbsent(player.getUUID(), k -> new ArrayList<>())
				.add(new Thrown(body.getId(), player.level().getGameTime(), damage));
	}

	private static void tickThrown(ServerPlayer player, List<Thrown> list) {
		ServerLevel level = (ServerLevel) player.level();
		long now = level.getGameTime();
		Iterator<Thrown> it = list.iterator();
		while (it.hasNext()) {
			Thrown t = it.next();
			Entity e = level.getEntity(t.entityId());
			long age = now - t.start();
			if (!(e instanceof LivingEntity body) || !body.isAlive() || age > 30) {
				it.remove();
				continue;
			}
			if (age < 2) {
				continue;
			}
			LivingEntity other = null;
			for (LivingEntity x : level.getEntitiesOfClass(LivingEntity.class, body.getBoundingBox().inflate(0.6),
					x -> x != body && harmable(player, x))) {
				other = x;
				break;
			}
			if (other == null && !body.horizontalCollision && !body.onGround()) {
				continue;
			}
			it.remove();
			AbilityHelpers.hurt(player, body, t.damage());
			if (other != null) {
				AbilityHelpers.hurt(player, other, t.damage() * 0.6f);
				AbilityHelpers.knockbackFrom(other, body.position(), 1.0);
			}
			Vec3 c = SpiderCombat.center(body);
			level.sendParticles(ParticleTypes.CLOUD, c.x, c.y, c.z, 10, 0.4, 0.3, 0.4, 0.05);
			level.playSound(null, c.x, c.y, c.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0f, 0.7f);
		}
		if (list.isEmpty()) {
			THROWN.remove(player.getUUID());
		}
	}

	// ---------------- C ----------------

	/** C Tightrope: first press sets anchor A, the second spins the line from A to the new surface. */
	public static void tightrope(ServerPlayer player) {
		if (!SpiderMan.abilityReady(player, TIGHTROPE)) {
			SpiderAbilities.gate(player, TIGHTROPE, 0.0f); // the cooldown message
			return;
		}
		ServerLevel level = (ServerLevel) player.level();
		SpiderManConfig.Webs w = SpiderManConfig.webs();
		BlockHitResult hit = AbilityHelpers.raycastBlock(player, w.tightropeMaxLength + 8.0);
		SpiderMoveState s = state(player);
		long now = level.getGameTime();
		Long aAt = ANCHOR_A_AT.get(player.getUUID());
		boolean hasA = s.has(SpiderMoveState.FLAG_ANCHOR_A) && aAt != null && now - aAt < 600;
		// v0.16.1: an anchor may also be a point on one of his webs' strands (a junction) -- whichever is nearer on the aim
		WebStructures.StrandHit strand = WebStructures.raycastStrand(player, w.tightropeMaxLength + 8.0);
		double blockDist = hit.getType() == HitResult.Type.BLOCK ? hit.getLocation().distanceTo(player.getEyePosition()) : Double.MAX_VALUE;
		boolean onStrand = strand != null && strand.distance() < blockDist;
		if (hit.getType() != HitResult.Type.BLOCK && !onStrand) {
			if (hasA) {
				clearAnchorA(player);
				player.displayClientMessage(Component.translatable("message.projecthero.spider_man.anchor_cleared"), true);
			} else {
				player.displayClientMessage(Component.translatable("message.projecthero.spider_man.no_surface"), true);
			}
			return;
		}
		Vec3 point = onStrand ? strand.point()
				: hit.getLocation().add(Vec3.atLowerCornerOf(hit.getDirection().getNormal()).scale(0.12));
		UUID parent = onStrand ? strand.web().getUUID() : null;
		anim(player, SpiderAnim.TIGHTROPE_SHOT);
		thwip(player, 1.0f);
		if (!hasA) {
			SpiderMoveState c = s.copy();
			c.set(SpiderMoveState.FLAG_ANCHOR_A, true);
			c.ax = point.x;
			c.ay = point.y;
			c.az = point.z;
			c.animId = SpiderAnim.TIGHTROPE_SHOT;
			c.animStart = now;
			save(player, c);
			ANCHOR_A_AT.put(player.getUUID(), now);
			if (parent != null) {
				ANCHOR_A_PARENT.put(player.getUUID(), parent);
			} else {
				ANCHOR_A_PARENT.remove(player.getUUID());
			}
			SpiderCombat.sendStrand(player, SpiderCombat.SLOT_TIGHTROPE, null, point, 600, 0);
			level.sendParticles(ParticleTypes.ITEM_COBWEB, point.x, point.y, point.z, 8, 0.1, 0.1, 0.1, 0.0);
			player.displayClientMessage(Component.translatable("message.projecthero.spider_man.anchor_set"), true);
			return;
		}
		Vec3 a = new Vec3(s.ax, s.ay, s.az);
		double len = a.distanceTo(point);
		if (len > w.tightropeMaxLength) {
			player.displayClientMessage(Component.translatable("message.projecthero.spider_man.rope_too_long",
					(int) w.tightropeMaxLength), true);
			return;
		}
		if (len < 2.0) {
			return;
		}
		if (!SpiderAbilities.gate(player, TIGHTROPE, cfg(TIGHTROPE).cost)) {
			return;
		}
		WebStructureEntity rope = buildTightrope(player, a, point);
		UUID parentA = ANCHOR_A_PARENT.get(player.getUUID());
		if (parentA != null) {
			rope.parents.add(parentA);
		}
		if (parent != null) {
			rope.parents.add(parent);
		}
		clearAnchorA(player);
		fired(player, TIGHTROPE);
	}

	/** Spin a tightrope from {@code a} to {@code b} (no cost or checks; the move and the tests call this). */
	public static WebStructureEntity buildTightrope(ServerPlayer player, Vec3 a, Vec3 b) {
		WebStructureEntity web = WebStructures.spawn(player, WebGeometry.tightrope(a, b), a.lerp(b, 0.5));
		ServerLevel level = (ServerLevel) player.level();
		// (v0.16.1: no particle line -- the rope itself is drawn the moment it exists, WebStructureRenderer)
		if (!soundsOff(player)) {
			level.playSound(null, b.x, b.y, b.z, SpiderSounds.SPLAT, SoundSource.PLAYERS, 0.8f, 1.2f);
		}
		return web;
	}

	private static void clearAnchorA(ServerPlayer player) {
		ANCHOR_A_AT.remove(player.getUUID());
		ANCHOR_A_PARENT.remove(player.getUUID());
		SpiderMoveState s = state(player);
		if (s.has(SpiderMoveState.FLAG_ANCHOR_A)) {
			SpiderMoveState c = s.copy();
			c.set(SpiderMoveState.FLAG_ANCHOR_A, false);
			save(player, c);
		}
		SpiderCombat.sendStrand(player, SpiderCombat.SLOT_TIGHTROPE, null, player.position(), 0, 0);
	}

	/** v0.16.2 (user): the Web Wall is 7 x 7 (was 3 x 3). */
	public static final double WALL_HALF_WIDTH = 3.5;
	public static final double WALL_HEIGHT = 7.0;

	/** Shift+C Web Wall: a sticky 7 x 7 barrier 2.5 blocks in front of you. */
	public static void webWall(ServerPlayer player) {
		if (!gate(player, WEB_WALL)) {
			return;
		}
		Direction facing = player.getDirection();
		Vec3 base = player.position().add(facing.getStepX() * 2.5, 0.0, facing.getStepZ() * 2.5);
		buildWall(player, base, facing.getAxis() == Direction.Axis.X ? 0 : 1);
		anim(player, SpiderAnim.WALL_CAST);
		thwip(player, 0.8f);
		fired(player, WEB_WALL);
	}

	public static WebStructureEntity buildWall(ServerPlayer player, Vec3 base, int axis) {
		WebStructureEntity web = WebStructures.spawn(player, WebGeometry.wall(base, axis, WALL_HALF_WIDTH, WALL_HEIGHT), base.add(0, WALL_HEIGHT * 0.5, 0)); // v0.16.2: 7 x 7
		ServerLevel level = (ServerLevel) player.level();
		level.sendParticles(ParticleTypes.ITEM_COBWEB, base.x, base.y + WALL_HEIGHT * 0.5, base.z, 60, 2.0, 2.0, 2.0, 0.02);
		if (!soundsOff(player)) {
			level.playSound(null, base.x, base.y, base.z, SpiderSounds.SPLAT, SoundSource.PLAYERS, 1.0f, 0.9f);
		}
		return web;
	}

	/**
	 * Hold C: open Weave Mode -- with NO centre yet. Each left-click places an anchor on the aimed surface; the web's centre
	 * is the centroid of all the anchors, so it comes out flat and stretched evenly between them (v0.16.1, user request).
	 */
	private static void beginWeave(ServerPlayer player) {
		if (WEAVES.containsKey(player.getUUID())) {
			return;
		}
		if (!SpiderMan.abilityReady(player, WEAVE)) {
			SpiderAbilities.gate(player, WEAVE, 0.0f);
			return;
		}
		openWeave(player, player.getLookAngle());
	}

	/** Open Weave Mode (no centre, no anchors yet); {@code normalHint} orients the web's plane until it has 3 anchors. */
	public static void openWeave(ServerPlayer player, Vec3 normalHint) {
		WEAVES.put(player.getUUID(), new Weave(normalHint, player.level().getGameTime()));
		SpiderMoveState c = state(player).copy();
		c.set(SpiderMoveState.FLAG_WEAVING, true);
		c.strands = 0;
		c.chargeStart = player.level().getGameTime();
		save(player, c);
		play(player, SoundEvents.SPIDER_AMBIENT, 0.6f, 1.2f);
		player.displayClientMessage(Component.translatable("message.projecthero.spider_man.weave_open"), true);
	}

	public static boolean isWeaving(ServerPlayer player) {
		return WEAVES.containsKey(player.getUUID());
	}

	/** A left-click in Weave Mode: an anchor on the aimed surface (within the strand reach). */
	public static boolean weaveStrand(ServerPlayer player) {
		Weave w = WEAVES.get(player.getUUID());
		if (w == null) {
			return false;
		}
		SpiderManConfig.Webs cfgW = SpiderManConfig.webs();
		if (w.ends.size() >= cfgW.weaveMaxStrands) {
			player.displayClientMessage(Component.translatable("message.projecthero.spider_man.weave_full"), true);
			return false;
		}
		BlockHitResult hit = AbilityHelpers.raycastBlock(player, cfgW.weaveStrandLength);
		if (hit.getType() != HitResult.Type.BLOCK) {
			player.displayClientMessage(Component.translatable("message.projecthero.spider_man.no_surface"), true);
			return false;
		}
		Vec3 end = hit.getLocation().add(Vec3.atLowerCornerOf(hit.getDirection().getNormal()).scale(0.12));
		return weaveAnchor(player, end);
	}

	/** Add an anchor at {@code end} (the click above and the tests); the preview re-centres on all the anchors. */
	public static boolean weaveAnchor(ServerPlayer player, Vec3 end) {
		Weave w = WEAVES.get(player.getUUID());
		if (w == null) {
			return false;
		}
		SpiderManConfig.Webs cfgW = SpiderManConfig.webs();
		if (w.ends.size() >= cfgW.weaveMaxStrands) {
			return false;
		}
		// (the reach is the click's raycast -- weaveStrand -- not a distance from wherever he stands now; an anchor too far
		// from the finished web's centre is dropped in finishWeave)
		for (Vec3 e : w.ends) {
			if (e.distanceToSqr(end) < 1.0) {
				return false; // the same spot twice
			}
		}
		float cost = cfg(WEAVE).cost;
		if (!SpiderWebReserve.spend(player, cost, true)) {
			player.displayClientMessage(Component.translatable("message.projecthero.spider_man.no_webbing"), true);
			return false;
		}
		w.spent += cost;
		w.ends.add(end);
		ServerLevel level = (ServerLevel) player.level();
		Vec3 centre = weaveCentroid(w.ends);
		Vec3 normal = weaveNormal(w.ends, w.normal);
		// the live preview: the centroid marker (synced centre) and, from two anchors on, faint radials to each anchor
		SpiderMoveState c = state(player).copy();
		c.strands = w.ends.size();
		c.cx = centre.x;
		c.cy = centre.y;
		c.cz = centre.z;
		c.animId = SpiderAnim.WEAVE_SHOT;
		c.animStart = level.getGameTime();
		save(player, c);
		if (w.ends.size() >= 2) {
			WebGeometry g = WebGeometry.weave(centre, orderAroundCentre(w.ends, centre, normal), normal, false, 1.0);
			if (w.web == null || w.web.isRemoved()) {
				w.web = WebStructures.spawn(player, g, centre);
			} else {
				w.web.setGeometry(g);
				w.web.setPos(centre);
			}
		}
		SpiderCombat.sendStrand(player, SpiderCombat.SLOT_WEAVE, null, end, 6, 20);
		level.sendParticles(ParticleTypes.ITEM_COBWEB, end.x, end.y, end.z, 10, 0.12, 0.12, 0.12, 0.0);
		level.sendParticles(ParticleTypes.END_ROD, centre.x, centre.y, centre.z, 4, 0.05, 0.05, 0.05, 0.0);
		thwip(player, 1.1f + w.ends.size() * 0.02f);
		return true;
	}

	/** C released: with 3+ anchors the web is centred on their centroid, radials sorted round it, spirals laid in. */
	public static WebStructureEntity finishWeave(ServerPlayer player) {
		Weave w = WEAVES.remove(player.getUUID());
		SpiderMoveState c = state(player).copy();
		c.set(SpiderMoveState.FLAG_WEAVING, false);
		c.strands = 0;
		save(player, c);
		SpiderCombat.sendStrand(player, SpiderCombat.SLOT_WEAVE, null, player.position(), 0, 0);
		if (w == null || w.ends.isEmpty()) {
			return null; // nothing was woven: no cost, no cooldown
		}
		double reach = SpiderManConfig.webs().weaveStrandLength;
		Vec3 centre = weaveCentroid(w.ends);
		List<Vec3> kept = new ArrayList<>();
		for (Vec3 e : w.ends) {
			if (e.distanceTo(centre) <= reach) {
				kept.add(e);
			}
		}
		if (kept.size() < 3) {
			// fewer than 3 anchors: no web -- take the preview down and give the webbing back
			if (w.web != null && !w.web.isRemoved()) {
				w.web.dissolve(false);
			}
			SpiderMan.setWebReserve(player, Math.min(SpiderWebReserve.maxFor(player), SpiderWebReserve.get(player) + w.spent));
			player.displayClientMessage(Component.translatable("message.projecthero.spider_man.weave_cancelled"), true);
			return null;
		}
		centre = weaveCentroid(kept);
		Vec3 normal = weaveNormal(kept, w.normal);
		List<Vec3> ordered = orderAroundCentre(kept, centre, normal);
		float finish = SpiderManConfig.moves().weaveFinishCost;
		boolean spirals = SpiderWebReserve.spend(player, finish, true);
		if (!spirals) {
			player.displayClientMessage(Component.translatable("message.projecthero.spider_man.no_webbing"), true);
		}
		WebGeometry g = WebGeometry.weave(centre, ordered, normal, spirals, 1.0);
		if (w.web == null || w.web.isRemoved()) {
			w.web = WebStructures.spawn(player, g, centre);
		} else {
			w.web.setGeometry(g);
			w.web.setPos(centre);
		}
		if (spirals) {
			anim(player, SpiderAnim.WEAVE_FINISH);
			notify(player, SpiderSounds.SPLAT, 1.0f, 0.7f);
			((ServerLevel) player.level()).sendParticles(ParticleTypes.ITEM_COBWEB, centre.x, centre.y, centre.z,
					30, 1.5, 1.5, 1.5, 0.02);
		}
		SpiderMan.triggerCooldown(player, WEAVE, cfg(WEAVE).cooldown);
		return w.web;
	}

	/** v0.16.1: the web's centre -- the plain average of its anchors (it lies on their best-fit plane). */
	public static Vec3 weaveCentroid(List<Vec3> anchors) {
		double x = 0, y = 0, z = 0;
		for (Vec3 a : anchors) {
			x += a.x;
			y += a.y;
			z += a.z;
		}
		int n = Math.max(1, anchors.size());
		return new Vec3(x / n, y / n, z / n);
	}

	/**
	 * v0.16.1: the anchors' best-fit plane normal (Newell's method over the anchors taken in order round their centroid),
	 * pointing the same way as {@code hint}; the hint itself with fewer than 3 anchors or a degenerate set.
	 */
	public static Vec3 weaveNormal(List<Vec3> anchors, Vec3 hint) {
		Vec3 h = hint == null || hint.lengthSqr() < 1.0e-6 ? new Vec3(0, 1, 0) : hint.normalize();
		if (anchors.size() < 3) {
			return h;
		}
		Vec3 c = weaveCentroid(anchors);
		List<Vec3> ord = orderAroundCentre(anchors, c, h);
		double nx = 0, ny = 0, nz = 0;
		for (int i = 0; i < ord.size(); i++) {
			Vec3 a = ord.get(i).subtract(c);
			Vec3 b = ord.get((i + 1) % ord.size()).subtract(c);
			Vec3 x = a.cross(b);
			nx += x.x;
			ny += x.y;
			nz += x.z;
		}
		Vec3 n = new Vec3(nx, ny, nz);
		if (n.lengthSqr() < 1.0e-6) {
			return h;
		}
		n = n.normalize();
		return n.dot(h) < 0 ? n.reverse() : n;
	}

	/** v0.16.1: the anchors sorted by angle round {@code centre} in the plane of {@code normal}, whatever the click order. */
	public static List<Vec3> orderAroundCentre(List<Vec3> anchors, Vec3 centre, Vec3 normal) {
		Vec3 n = normal.lengthSqr() < 1.0e-6 ? new Vec3(0, 1, 0) : normal.normalize();
		Vec3 u = Math.abs(n.y) < 0.95 ? n.cross(new Vec3(0, 1, 0)).normalize() : n.cross(new Vec3(1, 0, 0)).normalize();
		Vec3 v = n.cross(u).normalize();
		List<Vec3> out = new ArrayList<>(anchors);
		out.sort((p, q) -> Double.compare(Math.atan2(p.subtract(centre).dot(v), p.subtract(centre).dot(u)),
				Math.atan2(q.subtract(centre).dot(v), q.subtract(centre).dot(u))));
		return out;
	}

	// ---------------- V ----------------

	/** V Spider-Sense Pulse: hostiles light up for him alone, a burst of speed and a short dodge window. */
	public static void spiderPulse(ServerPlayer player) {
		if (!gate(player, SPIDER_PULSE)) {
			return;
		}
		SpiderManConfig.Moves m = SpiderManConfig.moves();
		long now = player.level().getGameTime();
		SpiderMoveState c = state(player).copy();
		c.pulseUntil = now + m.pulseHighlightTicks;
		c.animId = SpiderAnim.PULSE;
		c.animStart = now;
		save(player, c);
		DODGE_UNTIL.put(player.getUUID(), now + m.pulseDodgeTicks);
		player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, m.pulseHighlightTicks, 3, false, false, true));
		notify(player, SpiderSounds.SENSE, 1.0f, 1.0f);
		fired(player, SPIDER_PULSE);
	}

	/** True inside the Pulse's dodge window (tests). */
	public static boolean inDodgeWindow(ServerPlayer player) {
		Long until = DODGE_UNTIL.get(player.getUUID());
		return until != null && player.level().getGameTime() < until;
	}

	/** Shift+V Web Cocoon: wrap a weakened or webbed enemy completely; it takes extra damage while wrapped. */
	public static void webCocoon(ServerPlayer player) {
		if (!gate(player, WEB_COCOON)) {
			return;
		}
		LivingEntity target = SpiderCombat.pickTarget(player, 20.0);
		if (target == null || !harmable(player, target)) {
			noTarget(player);
			return;
		}
		if (SpiderWebs.isBoss(target) || SpiderWebs.ignoresWebs(target)) {
			player.displayClientMessage(Component.translatable("message.projecthero.spider_man.too_heavy"), true);
			return;
		}
		boolean weak = target.getHealth() <= target.getMaxHealth() * 0.4f || SpiderWebs.isWebImpaired(target)
				|| (target.getEffect(MobEffects.MOVEMENT_SLOWDOWN) != null
						&& target.getEffect(MobEffects.MOVEMENT_SLOWDOWN).getAmplifier() >= 2);
		if (!weak) {
			player.displayClientMessage(Component.translatable("message.projecthero.spider_man.cocoon_needs_weak"), true);
			return;
		}
		// v0.16.1: a thrown move -- a web ball homes in on the target and wraps it where it lands (cocoonLanded)
		ServerLevel level = (ServerLevel) player.level();
		anim(player, SpiderAnim.COCOON);
		Vec3 look = player.getLookAngle();
		Vec3 from = player.getEyePosition().add(look.scale(0.6)).subtract(0, 0.25, 0);
		level.addFreshEntity(new com.projecthero.mod.spider.entity.WebCocoonBallEntity(level, player, target, from));
		thwip(player, 0.9f);
		fired(player, WEB_COCOON);
	}

	/** The Web Cocoon ball reaching its target: the full wrap (it takes extra damage while wrapped). */
	public static void cocoonLanded(ServerPlayer player, LivingEntity target) {
		if (!target.isAlive() || SpiderWebs.ignoresWebs(target)) {
			return;
		}
		int ticks = SpiderManConfig.moves().webCocoonTicks;
		SpiderWebs.cocoonFor(player, target, ticks);
		SpiderWebs.markDeepCocoon(target, ticks);
		target.setDeltaMovement(0, Math.min(0.0, target.getDeltaMovement().y), 0);
		target.hurtMarked = true;
		ServerLevel level = (ServerLevel) player.level();
		Vec3 c = SpiderCombat.center(target);
		level.sendParticles(ParticleTypes.ITEM_COBWEB, c.x, c.y, c.z, 40, target.getBbWidth() * 0.5, target.getBbHeight() * 0.5,
				target.getBbWidth() * 0.5, 0.02);
		level.playSound(null, c.x, c.y, c.z, SpiderSounds.SPLAT, SoundSource.PLAYERS, 1.0f, 0.7f);
	}

	// ---------------- traversal: swinging + zip boost ----------------

	/** Right-click held with an empty main hand: fire a swing web (the client asks; this re-checks the hand). */
	public static boolean swingStart(ServerPlayer player) {
		if (!SpiderMan.hasPower(player) || !player.getMainHandItem().isEmpty() || player.isSpectator()) {
			return false;
		}
		boolean ok = SpiderSwing.fire(player);
		if (ok) {
			thwip(player, 1.0f);
		}
		return ok;
	}

	/** Right-click released: let go with every bit of momentum (a fast release somersaults). */
	public static void swingStop(ServerPlayer player) {
		boolean was = SpiderSwing.isSwinging(player);
		SpiderSwing.detach(player, true);
		HANGS.remove(player.getUUID());
		clearHangFlags(player);
		double[] m = MOTION.get(player.getUUID());
		if (was && !player.onGround() && m != null && m[2] > 0.45) {
			anim(player, SpiderAnim.RELEASE_FLIP);
		}
	}

	/** Sprint + Jump in the air: a quick zip forward. */
	public static boolean zipBoost(ServerPlayer player) {
		if (!SpiderMan.hasPower(player) || player.onGround() || SpiderSwing.isSwinging(player)
				|| SpiderClimb.attached(player) || player.isPassenger() || player.isInWater()) {
			return false;
		}
		if (!SpiderMan.abilityReady(player, ZIP_BOOST) || !SpiderWebReserve.has(player, cfg(ZIP_BOOST).cost)) {
			return false;
		}
		// v0.16.1 (user request): the zip needs a real surface to web onto within zipBoostRange -- never webs onto air
		BlockHitResult hit = AbilityHelpers.raycastBlock(player, SpiderManConfig.get().moves.zipBoostRange);
		if (hit.getType() != HitResult.Type.BLOCK) {
			return false;
		}
		Vec3 point = hit.getLocation();
		Vec3 toward = point.subtract(player.getEyePosition()).normalize();
		Vec3 dir = new Vec3(toward.x, Math.max(-0.2, toward.y) * 0.6 + 0.25, toward.z).normalize();
		AbilityHelpers.launchSelf(player, dir.scale(1.6));
		SpiderCombat.sendStrand(player, SpiderCombat.SLOT_ZIP, null, point, 3, 30);
		// v0.16.1: the same sound as firing a swing line (the thwip + the line going out)
		thwip(player, 1.0f);
		SpiderSwing.playAttachSound(player);
		anim(player, SpiderAnim.ZIP);
		fired(player, ZIP_BOOST);
		return true;
	}

	/** N: switch the automatic wall crawl on / off (saved per player; default on). Returns the new state (true = on). */
	public static boolean toggleWallCrawl(ServerPlayer player) {
		if (!SpiderMan.hasPower(player)) {
			return false;
		}
		boolean off = !player.getAttachedOrElse(ModAttachments.SPIDER_WALL_CRAWL_OFF, false);
		player.setAttached(ModAttachments.SPIDER_WALL_CRAWL_OFF, off);
		if (off) {
			SpiderClimb.requestRelease(player);
		}
		play(player, SoundEvents.SPIDER_STEP, 0.5f, off ? 0.8f : 1.6f);
		player.displayClientMessage(Component.translatable(off
				? "message.projecthero.spider_man.wall_crawl_off" : "message.projecthero.spider_man.wall_crawl_on"), true);
		return !off;
	}

	/** Sneak while standing on one of his tightropes: drop under it and hang from it (a short swing line). */
	public static boolean ropeHang(ServerPlayer player) {
		WebStructureEntity rope = SpiderMan.hasPower(player) ? WebStructures.ropeUnderfoot(player) : null;
		if (rope == null || SpiderSwing.isSwinging(player)) {
			return false;
		}
		Vec3 point = rope.geometry().closestPoint(player.position());
		if (!SpiderSwing.fireAt(player, point, 1.7)) {
			return false;
		}
		setFlag(player, SpiderMoveState.FLAG_HANGING, true);
		AbilityHelpers.launchSelf(player, new Vec3(0, -0.4, 0));
		return true;
	}

	/** Jump while hanging: flip back up on top of the rope. */
	public static void ropeClimb(ServerPlayer player) {
		if (!state(player).has(SpiderMoveState.FLAG_HANGING)) {
			return;
		}
		SpiderSwing.detach(player, false);
		setFlag(player, SpiderMoveState.FLAG_HANGING, false);
		AbilityHelpers.launchSelf(player, new Vec3(0, 1.0, 0));
		anim(player, SpiderAnim.RELEASE_FLIP);
	}

	// ---------------- v0.16.1: the upside-down hang (Z on a ceiling / woven web / tightrope) ----------------

	/** Where an upside-down hang came from: a ceiling (re-crawl it) or a web strand (stand back on it), and its anchor. */
	private record Hang(boolean ceiling, Vec3 anchor, UUID web, float yaw) {
	}

	private static final Map<UUID, Hang> HANGS = new ConcurrentHashMap<>();
	/** The line he drops on, and its limits (Jump reels in, Sneak pays out -- on his own client, see SpiderSwingClient). */
	public static final double HANG_START_LENGTH = 2.0;
	public static final double HANG_MIN_LENGTH = 0.8;
	/**
	 * v0.16.2 (user request: "no limit"): was 16. He lowers until his head is just above the ground / the first block
	 * below him (SpiderSwingClient clamps to that); this is only the sanity cap for a bottomless drop.
	 */
	public static final double HANG_MAX_LENGTH = 128.0;
	/** v0.16.2: top climb / lower speed on the line (blocks per tick; was a flat 0.15) and how fast it builds up. */
	public static final double HANG_REEL_SPEED = 0.4;
	public static final double HANG_REEL_ACCEL = 0.06;
	/** v0.16.2: the line is tied on where his feet meet, this far below the top of his (upright) box -- see SpiderHang. */
	public static final double HANG_FEET_BELOW_TOP = 0.38;
	/** v0.16.2: the flipped body's head pokes this far below the bottom of his box; it stops this far above the ground. */
	private static final double HANG_HEAD_BELOW_BOX = 0.08;
	private static final double HANG_HEAD_CLEARANCE = 0.12;
	/** The hang keeps him standing (PlayerMixin), so his box is always the standing one. */
	private static final double HANG_BOX_HEIGHT = 1.8;

	/** v0.16.2: where the bottom of his box sits on a hang line of {@code len} from {@code anchor} (feet at its end). */
	public static double hangBoxBottom(Vec3 anchor, double len) {
		return anchor.y - len + HANG_FEET_BELOW_TOP - HANG_BOX_HEIGHT;
	}

	/**
	 * v0.16.2: lowering the hang line from {@code from} to {@code to}: how far it may really go before his head would touch
	 * whatever is below him (the ground, a ledge, a single block) -- a thin slab under his head swept straight down. There
	 * is no other limit but {@link #HANG_MAX_LENGTH}.
	 */
	public static double hangLowerTo(Player player, Vec3 anchor, double from, double to) {
		if (to <= from) {
			return to;
		}
		AABB start = hangHeadSlab(player, anchor, from);
		if (player.level().noCollision(player, hangHeadSlab(player, anchor, to).minmax(start))) {
			return to;
		}
		double lo = from;
		double hi = to;
		for (int i = 0; i < 10; i++) {
			double mid = (lo + hi) * 0.5;
			if (player.level().noCollision(player, hangHeadSlab(player, anchor, mid).minmax(start))) {
				lo = mid;
			} else {
				hi = mid;
			}
		}
		return lo;
	}

	/** The slab just under his head (clearance included) on a hang line of length {@code len}. */
	private static AABB hangHeadSlab(Player player, Vec3 anchor, double len) {
		double y = hangBoxBottom(anchor, len) - HANG_HEAD_BELOW_BOX - HANG_HEAD_CLEARANCE;
		double hw = player.getBbWidth() * 0.5 - 0.02;
		return new AABB(anchor.x - hw, y, anchor.z - hw, anchor.x + hw, y + 0.05, anchor.z + hw);
	}

	/** True while he hangs upside down (either side: the flag is synced). */
	public static boolean hangingUpsideDown(Player player) {
		SpiderMoveState s = player.getAttachedOrElse(ModAttachments.SPIDER_MOVE_STATE, null);
		return s != null && s.has(SpiderMoveState.FLAG_HANG_UPSIDE) && s.has(SpiderMoveState.FLAG_HANGING);
	}

	/**
	 * Z (tap): on a ceiling, a woven web or a tightrope -> drop and hang upside down from that spot on a real web line;
	 * already hanging -> zip back up to where he came from. Returns false (Z is Web Whip as usual) when neither applies.
	 */
	public static boolean hangKey(ServerPlayer player) {
		if (!SpiderMan.hasPower(player)) {
			return false;
		}
		if (HANGS.containsKey(player.getUUID()) && hangingUpsideDown(player)) {
			zipBackUp(player);
			return true;
		}
		if (SpiderSwing.isSwinging(player) || player.isPassenger() || player.isInWater()) {
			return false;
		}
		Hang hang = null;
		if (SpiderClimb.onCeiling(player)) {
			BlockHitResult up = player.level().clip(new net.minecraft.world.level.ClipContext(player.getEyePosition(),
					player.getEyePosition().add(0, 2.0, 0), net.minecraft.world.level.ClipContext.Block.COLLIDER,
					net.minecraft.world.level.ClipContext.Fluid.NONE, player));
			double y = up.getType() == HitResult.Type.BLOCK ? up.getLocation().y : player.getBoundingBox().maxY;
			hang = new Hang(true, new Vec3(player.getX(), y, player.getZ()), null, player.getYRot());
		} else {
			WebStructures.StrandHit on = WebStructures.strandAtFeet(player);
			if (on != null) {
				hang = new Hang(false, on.point(), on.web().getUUID(), player.getYRot());
			}
		}
		if (hang == null) {
			return false;
		}
		if (!SpiderSwing.fireAt(player, hang.anchor(), HANG_START_LENGTH)) {
			player.displayClientMessage(Component.translatable("message.projecthero.spider_man.no_webbing"), true);
			return true; // Z was meant for the hang: no whip by accident
		}
		SpiderClimb.requestRelease(player);
		HANGS.put(player.getUUID(), hang);
		SpiderMoveState c = state(player).copy();
		c.set(SpiderMoveState.FLAG_HANGING, true);
		c.set(SpiderMoveState.FLAG_HANG_UPSIDE, true);
		save(player, c);
		AbilityHelpers.launchSelf(player, new Vec3(0, -0.2, 0));
		thwip(player, 0.95f);
		return true;
	}

	/** Tap Z while hanging: zipped back up -- crawling on the ceiling again, or standing on the strand again. */
	private static void zipBackUp(ServerPlayer player) {
		Hang h = HANGS.remove(player.getUUID());
		clearHangFlags(player);
		SpiderSwing.detach(player, false);
		if (h == null) {
			return;
		}
		if (h.ceiling()) {
			Vec3 to = h.anchor().subtract(0, 0.2, 0);
			Vec3 dir = to.subtract(player.getEyePosition());
			AbilityHelpers.launchSelf(player, dir.lengthSqr() < 1.0e-4 ? new Vec3(0, 1, 0) : dir.normalize().scale(1.4));
			SpiderCombat.beginZipPull(player, to);
			SpiderClimb.requestGrab(player);
			net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
					new com.projecthero.mod.network.SpiderClimbGrabPayload());
		} else {
			Entity web = ((ServerLevel) player.level()).getEntity(h.web());
			if (!(web instanceof WebStructureEntity w) || w.isRemoved()) {
				return; // the web is gone: he just drops
			}
			Vec3 dir = h.anchor().add(0, player.getEyeHeight(), 0).subtract(player.getEyePosition());
			AbilityHelpers.launchSelf(player, dir.lengthSqr() < 1.0e-4 ? new Vec3(0, 1, 0) : dir.normalize().scale(1.4));
			SpiderCombat.beginZipLanding(player, h.anchor(), h.yaw());
		}
		SpiderCombat.sendStrand(player, SpiderCombat.SLOT_ZIP, null, h.anchor(), 4, 20);
		anim(player, SpiderAnim.ZIP);
		thwip(player, 1.1f);
		play(player, SpiderSounds.ZIP, 0.7f, 1.1f);
	}

	/** Shift+Z while hanging: let go of the line and drop. Returns false when he is not hanging upside down. */
	public static boolean endUpsideHang(ServerPlayer player) {
		if (HANGS.remove(player.getUUID()) == null && !hangingUpsideDown(player)) {
			return false;
		}
		clearHangFlags(player);
		SpiderSwing.detach(player, true);
		return true;
	}

	private static void clearHangFlags(ServerPlayer player) {
		SpiderMoveState s = state(player);
		if (s.has(SpiderMoveState.FLAG_HANGING) || s.has(SpiderMoveState.FLAG_HANG_UPSIDE)) {
			SpiderMoveState c = s.copy();
			c.set(SpiderMoveState.FLAG_HANGING, false);
			c.set(SpiderMoveState.FLAG_HANG_UPSIDE, false);
			save(player, c);
		}
	}

	// ---------------- damage hooks ----------------

	private static boolean onAllowDamage(LivingEntity victim, DamageSource source, float amount) {
		long now = victim.level().getGameTime();
		if (victim instanceof ServerPlayer player && SpiderMan.hasPower(player)) {
			boolean attacked = (source.getEntity() != null || source.getDirectEntity() != null)
					&& !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
			if (attacked && inCounterStance(player)) {
				counter(player, source);
				return false;
			}
			Long dodge = DODGE_UNTIL.get(player.getUUID());
			if (attacked && dodge != null && now < dodge) {
				if (victim.level() instanceof ServerLevel sl) {
					sl.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.5, player.getZ(), 6, 0.3, 0.2, 0.3, 0.02);
				}
				return false;
			}
		}
		// the uppercut's air combo: every hit keeps the target aloft
		if (source.getEntity() instanceof ServerPlayer attacker && attacker != victim) {
			long[] combo = AIR_COMBO.get(attacker.getUUID());
			if (combo != null && combo[0] == victim.getId() && now < combo[1]) {
				Vec3 v = victim.getDeltaMovement();
				victim.setDeltaMovement(v.x * 0.3, 0.5, v.z * 0.3);
				victim.hurtMarked = true;
				victim.resetFallDistance();
				if (!attacker.onGround()) {
					AbilityHelpers.launchSelf(attacker, new Vec3(0, 0.42, 0));
				}
			}
		}
		return true;
	}

	// ---------------- per-tick ----------------

	/** Per-player server tick (from {@code SpiderManAbilityManager.serverTick}). */
	public static void tick(ServerPlayer player) {
		UUID id = player.getUUID();
		long now = player.level().getGameTime();
		Press[] presses = PRESSES.get(id);
		if (presses != null) {
			for (int i = 0; i < 6; i++) {
				Press p = presses[i];
				if (p == null || p.shift) {
					continue;
				}
				if (!p.holding && now - p.at >= holdTicks()) {
					p.holding = true;
					holdStart(player, i);
				} else if (now - p.at > 20 * 60) {
					presses[i] = null; // a release that never came
					holdRelease(player, i);
				}
			}
		}
		Haul h = HAULS.get(id);
		if (h != null) {
			tickHaul(player, h);
		}
		Snag sn = SNAGS.get(id);
		if (sn != null) {
			tickSnag(player, sn);
		}
		Combo c = COMBOS.get(id);
		if (c != null) {
			tickCombo(player, c);
		}
		Hurl hurl = HURLS.get(id);
		if (hurl != null) {
			tickHurl(player, hurl);
		}
		List<Thrown> thrown = THROWN.get(id);
		if (thrown != null) {
			tickThrown(player, thrown);
		}
		long[] air = AIR_COMBO.get(id);
		if (air != null && now >= air[1]) {
			AIR_COMBO.remove(id);
			setFlag(player, SpiderMoveState.FLAG_AIR_COMBO, false);
		}
		Long counterUntil = COUNTER_UNTIL.get(id);
		if (counterUntil != null && now > counterUntil) {
			endCounter(player, false);
		}
		Weave w = WEAVES.get(id);
		if (w != null && now - w.start > 20 * 30) {
			finishWeave(player); // Weave Mode closes itself after 30 s
		}
		Long aAt = ANCHOR_A_AT.get(id);
		if (aAt != null && now - aAt > 600) {
			clearAnchorA(player);
		}
		SpiderMoveState s = state(player);
		if (s.has(SpiderMoveState.FLAG_HANGING) && !SpiderSwing.isSwinging(player)) {
			HANGS.remove(id);
			clearHangFlags(player); // the line went (cut, reserve, anchor gone): an upside-down hang ends with it
		}
		if (s.has(SpiderMoveState.FLAG_CHARGING_IMPACT) && now - s.chargeStart > 60) {
			releaseImpact(player); // a full charge fires by itself
		}
		if (s.has(SpiderMoveState.FLAG_CHARGING_IMPACT) && player.level() instanceof ServerLevel sl && now % 3 == 0) {
			Vec3 hand = AbilityHelpers.handPosition(player);
			sl.sendParticles(ParticleTypes.ITEM_COBWEB, hand.x, hand.y, hand.z, 2, 0.08, 0.08, 0.08, 0.0);
		}
		tickMotion(player, now);
	}



	/**
	 * v0.16.2 (restored from v0.16.1): is he at a perch -- standing on something thin (a fence / wall / post top), or with a
	 * drop of 3+ blocks just in front of his feet where he faces?
	 */
	public static boolean perchSpot(Player player) {
		var level = player.level();
		// what he stands on: the block under his feet, or -- for a fence / wall, whose collision is 1.5 tall, so he stands
		// half a block above its block -- the one below that, when its shape actually reaches up to his feet
		for (double drop : new double[] {0.2, 0.7}) {
			BlockPos below = BlockPos.containing(player.getX(), player.getY() - drop, player.getZ());
			var shape = level.getBlockState(below).getCollisionShape(level, below);
			if (shape.isEmpty()) {
				continue;
			}
			AABB b = shape.bounds();
			if (below.getY() + b.maxY < player.getY() - 0.25) {
				continue; // does not reach his feet
			}
			if (b.getXsize() < 0.7 || b.getZsize() < 0.7) {
				return true;
			}
			break; // a full block under him: the ledge test below decides
		}
		Vec3 facing = Vec3.directionFromRotation(0.0f, player.getYRot());
		// v0.16.2: probe 0.75 ahead (was 0.6), so standing in the middle of a 1-block pillar top counts too (the probe
		// column, 0.24 wide, has to clear the block's edge 0.5 away)
		Vec3 p = player.position().add(facing.scale(0.75));
		AABB column = new AABB(p.x - 0.12, player.getY() - 3.0, p.z - 0.12, p.x + 0.12, player.getY() - 0.05, p.z + 0.12);
		return level.noCollision(column);
	}

	/**
	 * v0.16.2: may he perch right now -- sneaking, (almost) still, on the ground, not on a line / wall / mount / in water,
	 * at a perch spot? Once perched ({@code already}) a small shuffle (under 0.2 blocks a tick) keeps the perch and the
	 * spot is not re-checked.
	 */
	public static boolean perchAllowed(Player player, boolean already, double horizontalSpeed) {
		return player.onGround() && player.isShiftKeyDown() && horizontalSpeed < (already ? 0.2 : 0.06)
				&& !SpiderSwing.isSwinging(player) && !SpiderClimb.attached(player) && !player.isPassenger()
				&& !player.isInWater() && (already || perchSpot(player));
	}

	/** Landing / release-flip bookkeeping: the superhero landing after a long drop or a swing. */
	private static void tickMotion(ServerPlayer player, long now) {
		double[] m = MOTION.computeIfAbsent(player.getUUID(), k -> new double[] {player.getY(), 0, 0, 1, player.getX(), player.getZ()});
		double vy = player.getY() - m[0];
		double hs = Math.hypot(player.getX() - m[4], player.getZ() - m[5]);
		boolean ground = player.onGround();
		if (ground && m[3] == 0.0 && m[1] < -0.75 && !player.isShiftKeyDown()) {
			anim(player, SpiderAnim.LANDING);
			if (player.level() instanceof ServerLevel sl) {
				sl.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.05, player.getZ(), 12, 0.5, 0.02, 0.5, 0.04);
			}
			play(player, SoundEvents.PLAYER_BIG_FALL, 0.5f, 1.3f);
		}
		// v0.16.2: the ledge perch -- sneaking, still, on the ground, at a drop (or up on a fence / wall / post); sticky
		// once settled (a small shuffle at the edge does not drop him out of it)
		boolean was = state(player).has(SpiderMoveState.FLAG_PERCH);
		setFlag(player, SpiderMoveState.FLAG_PERCH, perchAllowed(player, was, hs));
		m[0] = player.getY();
		m[1] = vy;
		m[2] = hs;
		m[3] = ground ? 1.0 : 0.0;
		m[4] = player.getX();
		m[5] = player.getZ();
	}
}
