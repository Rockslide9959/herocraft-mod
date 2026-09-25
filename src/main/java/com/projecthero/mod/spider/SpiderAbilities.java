package com.projecthero.mod.spider;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.power.AbilityHelpers;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The five non-swing web abilities, plus the ids and cooldowns for all six.
 *
 * <p>Everything here is server-side and re-validates the power, the reserve and the cooldown for
 * itself, so nothing depends on the client having asked nicely.
 *
 * <h2>Slot layout</h2>
 * The six universal HeroPack keys are reused exactly as they are -- no new keybinding category, no
 * duplicate inputs -- and the abilities are simply assigned to the slots whose default keys the
 * design asks for:
 * <pre>
 *   R (slot 1)  Web Swing     G (slot 2)  Web Zip
 *   Z (slot 4)  Web Shot      X (slot 3)  Web Yank
 *   C (slot 6)  Wall Crawl    V (slot 5)  Web Net
 * </pre>
 * Slot 6 is a toggle (v0.6.17: it replaced Web Cocoon -- the pin is now automatic on the third Web
 * Shot). Spider Sense, the double jump, the super jump and the physical enhancements are passives and
 * take up none of the six.
 */
public final class SpiderAbilities {
	public static final String WEB_SWING = "web_swing";
	public static final String WEB_ZIP = "web_zip";
	public static final String WEB_SHOT = "web_shot";
	public static final String WEB_YANK = "web_yank";
	/** Slot 6 (C): toggle the automatic wall-crawl mode on/off. */
	public static final String WALL_CRAWL = "wall_crawl";
	public static final String WEB_NET = "web_net";
	/** Not a slot on its own -- sneak + hold V for 3 s, then a radial web nova (v0.6.23). */
	public static final String WEB_BLOSSOM = "web_blossom";
	/** Not a slot -- the passive mid-air jump, cooldown-tracked so the HUD can show it. */
	public static final String DOUBLE_JUMP = "double_jump";
	/** Not a slot -- the sneak + jump super-leap, cooldown-tracked. */
	public static final String SUPER_JUMP = "super_jump";

	// ---- cooldowns, in ticks ----
	// v0.6.22: every web *ability* is off-cooldown now -- the webbing reserve drain is the only rate
	// limit. The two passive jumps keep a short gate (removing it turns double-jump-spam into free
	// flight), so they are deliberately NOT zero.
	public static final int CD_WEB_ZIP = 0;
	public static final int CD_WEB_SHOT = 0;
	public static final int CD_WEB_YANK = 0;
	public static final int CD_WEB_NET = 0;
	/** One second. The HUD no longer shows this cooldown at all. */
	public static final int CD_DOUBLE_JUMP = 20;
	/** Short gate on the sneak-jump super-leap so it cannot be mashed. */
	public static final int CD_SUPER_JUMP = 14;
	/** Web Blossom (v0.6.23): a short gate on top of its full-reserve cost. */
	public static final int CD_WEB_BLOSSOM = 8 * 20;

	// ---- Web Blossom tuning (v0.6.23) ----
	/** Sneak + hold V for this long (3 s) to charge Web Blossom. */
	public static final int BLOSSOM_CHARGE_TICKS = 3 * 20;
	/** v0.12.20: Combat Mode charges Web Blossom in 2 s instead. */
	public static final int BLOSSOM_CHARGE_TICKS_COMBAT = 2 * 20;

	/** Charge time for the given mode (the HUD passes the synced state's flag, the server the player's). */
	public static int blossomChargeTicks(boolean combatMode) {
		return combatMode ? BLOSSOM_CHARGE_TICKS_COMBAT : BLOSSOM_CHARGE_TICKS;
	}
	/** v0.10.11: a flat 75 webbing, no longer the entire reserve. */
	public static final float BLOSSOM_COST = 75.0f;
	private static final double BLOSSOM_RADIUS = 20.0;
	private static final float BLOSSOM_DAMAGE = 40.0f;
	/** Targets are cocooned / frozen in place for 20 s. */
	private static final int BLOSSOM_TRAP_TICKS = 20 * 20;

	public static final double ZIP_RANGE = 100.0;
	public static final double SHOT_RANGE = 26.0;
	/** v0.6.20: Web Shot now stings on impact. */
	public static final float WEB_SHOT_DAMAGE = 2.0f;
	/** v0.6.22: 50 blocks, and it aims straight down the crosshair (entity before item). */
	public static final double YANK_RANGE = 50.0;

	/**
	 * Black Suit Spider-Man: Enhanced Web Grab (spec) -- Web Yank's range and pull strength both go up,
	 * and it can shift heavier mobs than the base +20% pull-strength alone would suggest.
	 */
	private static double yankRange(ServerPlayer player) {
		return isBlackSuit(player) ? YANK_RANGE * 1.2 : YANK_RANGE;
	}

	private static double yankStrengthMult(ServerPlayer player) {
		return isBlackSuit(player) ? 1.2 : 1.0;
	}

	private static boolean isBlackSuit(ServerPlayer player) {
		return com.projecthero.mod.symbiote.Symbiote.isActive(player)
				&& com.projecthero.mod.symbiote.SymbioteHostType.of(player) == com.projecthero.mod.symbiote.SymbioteHostType.SPIDER_MAN;
	}
	/** v0.6.19: was 22. */
	public static final double NET_RANGE = 35.0;

	private SpiderAbilities() {
	}

	// ---------------- shared gates ----------------

	static boolean gate(ServerPlayer player, String ability, float cost) {
		if (!SpiderMan.abilityReady(player, ability)) {
			player.displayClientMessage(Component.translatable("message.projecthero.ability.on_cooldown",
					Component.translatable("projecthero.spider_man.ability." + ability),
					String.format(java.util.Locale.ROOT, "%.1f",
							SpiderMan.abilityCooldownRemaining(player, ability) / 20.0f)), true);
			return false;
		}
		if (!SpiderWebReserve.has(player, cost)) {
			player.displayClientMessage(Component.translatable("message.projecthero.spider_man.no_webbing"), true);
			return false;
		}
		return true;
	}

	static void fired(ServerPlayer player, String ability, float cost, int cooldown) {
		SpiderWebReserve.spend(player, cost, true);
		SpiderMan.triggerCooldown(player, ability, cooldown);
	}

	/** The web leaving the hand -- one short line of particles, drawn once, never per tick. */
	private static void webLine(ServerLevel level, Vec3 from, Vec3 to) {
		AbilityHelpers.line(level, from, to, ParticleTypes.ITEM_COBWEB, 1.2);
	}

	/**
	 * Where a web visibly leaves the player: the right hand, thrust forward along the aim. Offset from
	 * the body's right side (not the view), dropped to about hand height and pushed out in front, so
	 * the line reads as coming from the wrist rather than the chest.
	 */
	private static Vec3 handPos(ServerPlayer player) {
		Vec3 look = player.getLookAngle();
		Vec3 bodyRight = Vec3.directionFromRotation(0.0f, player.yBodyRot + 90.0f);
		// v0.6.20: the fist of a forward-pointing right arm -- roughly shoulder height (feet + 1.2),
		// offset to the body's right, then out along the aim. Sits a touch lower than before so the
		// web reads as leaving the hand rather than hovering just above it.
		return player.position()
				.add(0.0, 1.2, 0.0)
				.add(bodyRight.scale(0.32))
				.add(look.scale(0.5));
	}

	// ---------------- G -- Web Zip ----------------

	/**
	 * Short-to-medium range precision traversal: fire at what you are looking at and get pulled to it
	 * fast. Distinct from a swing -- no arc, no rope, just a hard yank along the line.
	 *
	 * <p>The player is <em>moved</em>, never teleported: the pull is applied as velocity, so ordinary
	 * collision resolves it and there is no way to end up inside geometry. Momentum is left on the
	 * player at the end, which is what lets a zip feed straight into a wall crawl or a swing.
	 */
	public static void webZip(ServerPlayer player) {
		if (!gate(player, WEB_ZIP, SpiderWebReserve.COST_WEB_ZIP)) {
			return;
		}
		BlockHitResult hit = AbilityHelpers.raycastBlock(player, ZIP_RANGE);
		if (hit.getType() != HitResult.Type.BLOCK) {
			player.displayClientMessage(Component.translatable("message.projecthero.spider_man.no_surface"), true);
			return;
		}
		Vec3 target = hit.getLocation();
		Vec3 eye = player.getEyePosition();
		Vec3 delta = target.subtract(eye);
		double dist = delta.length();
		if (dist < 1.5) {
			return;
		}
		Vec3 dir = delta.normalize();
		// v0.6.19: a harder yank. Speed still scales with distance so a short hop is not a rocket and a
		// long one still arrives, but the whole curve is pulled up.
		// v0.10.2: a zip aimed well below the player is a pull to a lower ledge, not a wall pin -- treat
		// it as an ordinary arced zip and do NOT arm adhesion, which is what used to leave the player
		// stuck against the block they zipped down onto.
		// v0.12.21: a sneaking zip is pulled ALL the way to the block (SpiderCombat.beginZipPull keeps hauling
		// every tick until arrival); only a zip that is not aimed well downward also arms wall adhesion.
		boolean sneaking = player.isShiftKeyDown();
		boolean grab = sneaking && dir.y > -0.35;

		if (sneaking) {
			// v0.6.21 -- "pin me to the wall": fire straight at the exact point aimed at (dir already
			// carries any up/down), drive into it a touch faster so contact is certain, and drop the
			// leftover sideways momentum so the player cannot skate past the lip. Then arm adhesion on
			// BOTH sides -- the server for authority, and the owning client (which actually simulates the
			// movement) via a marker packet -- so the instant the zip plants them on the surface the
			// climb engine sticks, no double-tap.
			double speed = Math.min(3.0, 1.1 + dist * 0.08);
			AbilityHelpers.launchSelf(player, dir.scale(speed));
			SpiderCombat.beginZipPull(player, target);
			if (grab) {
				SpiderClimb.requestGrab(player);
				net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
						new com.projecthero.mod.network.SpiderClimbGrabPayload());
			}
		} else {
			double speed = Math.min(2.6, 0.85 + dist * 0.075);
			double lift = Math.min(0.4, dist * 0.02);
			Vec3 pull = dir.scale(speed).add(0, lift, 0);
			// Clamp the downward component so a steep zip carries the player across to the lower surface
			// rather than drilling them into it and briefly wedging them there.
			pull = new Vec3(pull.x, Math.max(pull.y, -0.55), pull.z);
			AbilityHelpers.launchSelf(player, player.getDeltaMovement().scale(0.2).add(pull));
		}

		// A zip is traversal, so it also breaks a swing -- the two must not fight over the player.
		// It does NOT grant the anti-float mayfly tolerance: a zip is a single brief impulse, well
		// under vanilla's 80-tick airborne threshold, and leaving mayfly set after one was exactly how
		// "double jumping sometimes makes you fly" happened -- vanilla's own double-tap-jump then
		// toggled creative flight (v0.6.17).
		SpiderSwing.detach(player, false);

		ServerLevel level = (ServerLevel) player.level();
		// v0.12.20: the real web line, hand -> anchor, holding briefly and then phasing out over 5 s.
		SpiderCombat.sendStrand(player, SpiderCombat.SLOT_ZIP, null, target, 6, SpiderCombat.STRAND_FADE_TICKS);
		AbilityHelpers.burst(level, target, ParticleTypes.ITEM_COBWEB, 6, 0.15);
		AbilityHelpers.sound(player, SoundEvents.FISHING_BOBBER_RETRIEVE, 0.7f, 1.5f);
		fired(player, WEB_ZIP, SpiderWebReserve.COST_WEB_ZIP, CD_WEB_ZIP);
	}

	// ---------------- Z -- Web Shot ----------------

	/**
	 * Organic webbing at whatever is being aimed at. Each hit compounds: the first badly hampers an
	 * ordinary mob, and enough of them inside the same window pin it in place outright. Bosses feel
	 * every hit but can never be fully pinned.
	 *
	 * <p>Also puts fires out -- webbing smothers them -- which is what makes this useful outside a
	 * fight. Aiming at nothing in particular while burning douses the player.
	 */
	public static void webShot(ServerPlayer player) {
		if (!gate(player, WEB_SHOT, SpiderWebReserve.COST_WEB_SHOT)) {
			return;
		}
		ServerLevel level = (ServerLevel) player.level();
		LivingEntity target = AbilityHelpers.raycastEntity(player, SHOT_RANGE);

		if (target != null) {
			webLine(level, handPos(player), target.position().add(0, target.getBbHeight() * 0.5, 0));
			AbilityHelpers.hurt(player, target, WEB_SHOT_DAMAGE);

			boolean boss = SpiderWebs.isBoss(target);
			// v0.6.20: each hit is a stickiness stack (max 3). It slows the target and weighs its jump
			// down, worse with every stack -- and a follow-up landed within 3s of the first hit is what
			// pushes the count up. On the third stack an ordinary mob is wrapped in a full cocoon.
			int stacks = SpiderWebs.addStickStack(target, level.getGameTime());
			AbilityHelpers.applyControl(target, MobEffects.MOVEMENT_SLOWDOWN,
					SpiderWebs.STICK_EFFECT_TICKS, boss ? 1 : Math.min(4, stacks));
			// v0.6.22: webbed targets are also weakened.
			AbilityHelpers.applyControl(target, MobEffects.WEAKNESS,
					SpiderWebs.STICK_EFFECT_TICKS, boss ? 0 : 1);
			if (!boss) {
				// negative Jump Boost = a lower jump; -2, -3, -4 across the three stacks
				AbilityHelpers.applyControl(target, MobEffects.JUMP,
						SpiderWebs.STICK_EFFECT_TICKS, -(stacks + 1));
			}
			if (!boss && stacks >= SpiderWebs.MAX_STICK_STACKS) {
				SpiderWebs.cocoonFor(player, target, SpiderWebs.WEB_SHOT_COCOON_TICKS);
				SpiderWebs.clearStickStacks(target);
				level.playSound(null, target.blockPosition(), SoundEvents.SPIDER_STEP,
						net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 0.6f);
				AbilityHelpers.burst(level, target.position().add(0, target.getBbHeight() * 0.5, 0),
						ParticleTypes.CLOUD, 12, 0.4);
			}
			if (target.isOnFire()) {
				target.clearFire();
			}
			AbilityHelpers.burst(level, target.position().add(0, target.getBbHeight() * 0.5, 0),
					ParticleTypes.ITEM_COBWEB, 14, 0.35);
		} else {
			BlockHitResult hit = AbilityHelpers.raycastBlock(player, SHOT_RANGE);
			Vec3 at = hit.getType() == HitResult.Type.BLOCK ? hit.getLocation()
					: player.getEyePosition().add(player.getLookAngle().scale(SHOT_RANGE));
			webLine(level, handPos(player), at);
			AbilityHelpers.burst(level, at, ParticleTypes.ITEM_COBWEB, 8, 0.2);
			extinguishAround(player, level, at);
		}

		if (player.isOnFire()) {
			player.clearFire();
		}
		AbilityHelpers.sound(player, SoundEvents.SLIME_SQUISH, 0.55f, 1.9f);
		fired(player, WEB_SHOT, SpiderWebReserve.COST_WEB_SHOT, CD_WEB_SHOT);
	}

	/** Small fires within a block or two of the impact go out. */
	private static void extinguishAround(ServerPlayer player, ServerLevel level, Vec3 at) {
		if (!AbilityHelpers.canGrief()) {
			return;
		}
		BlockPos centre = BlockPos.containing(at);
		for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-1, -1, -1), centre.offset(1, 1, 1))) {
			BlockState state = level.getBlockState(pos);
			if (state.is(net.minecraft.world.level.block.Blocks.FIRE)
					|| state.is(net.minecraft.world.level.block.Blocks.SOUL_FIRE)) {
				level.removeBlock(pos, false);
				level.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
						6, 0.2, 0.2, 0.2, 0.01);
			}
		}
	}

	// ---------------- X -- Web Yank ----------------

	/**
	 * Fire a line at something and haul on it. What moves depends on what is on the other end: light
	 * things come to you, heavy things pull you to them, and dropped items come flying -- which is the
	 * whole point of having this while standing at the edge of a ravine or a lava lake.
	 */
	public static void webYank(ServerPlayer player) {
		if (!gate(player, WEB_YANK, SpiderWebReserve.COST_WEB_YANK)) {
			return;
		}
		ServerLevel level = (ServerLevel) player.level();
		Vec3 hand = handPos(player);

		// v0.6.22: whatever is under the crosshair wins. A living entity in the aim line is pulled
		// first; only if the ray hits no mob at all do we fall back to the item cone. This is the fix
		// for "yank grabs the nearest thing instead of what I'm actually looking at".
		LivingEntity aimed = AbilityHelpers.raycastEntity(player, yankRange(player));
		ItemEntity item = aimed == null ? nearestItemInAim(player) : null;
		if (item != null) {
			webLine(level, hand, item.position());
			// v0.6.19: yanked straight at the player -- a hard pull aimed right at the chest, not skidded
			// across the floor and not lobbed in an arc. Just enough of an upward bias to unstick it from
			// the ground on the first tick; distance only makes the pull faster.
			Vec3 grab = player.getEyePosition().subtract(0, 0.4, 0);
			Vec3 delta = grab.subtract(item.position());
			double d = Math.max(1.0, delta.length());
			double speed = Math.min(3.2, 0.9 + d * 0.13);
			Vec3 pull = delta.normalize().scale(speed).add(0, 0.12, 0);
			item.setDeltaMovement(pull);
			item.setNoPickUpDelay();
			item.hurtMarked = true;
			AbilityHelpers.sound(player, SoundEvents.FISHING_BOBBER_RETRIEVE, 0.6f, 1.7f);
			fired(player, WEB_YANK, SpiderWebReserve.COST_WEB_YANK, CD_WEB_YANK);
			return;
		}

		// A flying / hovering mob (Vex, Phantom, Allay...) is rarely dead-centre on the crosshair and is
		// often "behind" a block it just phased through, so the view-vector raycast misses it. Fall back
		// to a loose aim cone that ignores walls, exactly like the item pickup does.
		LivingEntity target = aimed != null ? aimed : nearestLivingInAim(player);
		if (target == null) {
			player.displayClientMessage(Component.translatable("message.projecthero.spider_man.no_target"), true);
			return;
		}
		webLine(level, hand, target.position().add(0, target.getBbHeight() * 0.5, 0));

		Vec3 toPlayer = player.position().subtract(target.position());
		double dist = Math.max(1.0, toPlayer.length());
		boolean boss = SpiderWebs.isBoss(target);
		boolean airborne = isAirborneTarget(target);
		// Rough stand-in for mass: a tall, healthy, knockback-resistant thing is heavy.
		double mass = target.getBbHeight() * target.getBbWidth() * (1.0 + target.getMaxHealth() / 40.0);
		// Black Suit Spider-Man: stronger pull, and what counts as "movable" reaches further up the
		// mass scale -- the spec's "allow pulling heavier mobs".
		double strength = yankStrengthMult(player);

		if (boss || target.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE) >= 1.0) {
			// Immovable: the line pulls the spider instead. No exploiting a boss as a yo-yo.
			Vec3 pull = toPlayer.reverse().normalize().scale(Math.min(1.5, 0.5 + dist * 0.05) * strength).add(0, 0.28, 0);
			AbilityHelpers.launchSelf(player, pull);
		} else if (airborne) {
			// Yank it out of the air: a hard pull straight at the chest. Its flight AI will fight back
			// on later ticks, so the pull is strong and the target is de-aggroed and briefly grounded so
			// it actually arrives instead of orbiting.
			Vec3 grab = player.getEyePosition().subtract(0, 0.3, 0);
			Vec3 delta = grab.subtract(target.position());
			double d = Math.max(1.0, delta.length());
			target.setDeltaMovement(delta.normalize().scale(Math.min(3.0, 1.1 + d * 0.12) * strength));
			target.setNoGravity(false);
			target.hurtMarked = true;
			if (target instanceof net.minecraft.world.entity.Mob mob) {
				mob.setTarget(null);
			}
		} else if (mass > 5.0 * strength) {
			// Heavy but movable: both ends move, neither very far.
			AbilityHelpers.push(target, toPlayer.normalize().scale(0.45 * strength));
			AbilityHelpers.launchSelf(player, player.getDeltaMovement()
					.add(toPlayer.reverse().normalize().scale(0.35)).add(0, 0.12, 0));
		} else {
			Vec3 pull = toPlayer.normalize().scale(Math.min(1.7, 0.45 + dist * 0.085) * strength).add(0, 0.3, 0);
			target.setDeltaMovement(pull);
			target.hurtMarked = true;
		}

		AbilityHelpers.burst(level, target.position().add(0, target.getBbHeight() * 0.5, 0),
				ParticleTypes.ITEM_COBWEB, 10, 0.3);
		AbilityHelpers.sound(player, SoundEvents.FISHING_BOBBER_RETRIEVE, 0.75f, 1.1f);
		fired(player, WEB_YANK, SpiderWebReserve.COST_WEB_YANK, CD_WEB_YANK);
	}

	/** True for mobs that hold themselves off the ground -- Web Yank has to fight their flight AI. */
	private static boolean isAirborneTarget(LivingEntity target) {
		return target.isNoGravity()
				|| target instanceof net.minecraft.world.entity.FlyingMob
				|| target instanceof net.minecraft.world.entity.monster.Vex
				|| target instanceof net.minecraft.world.entity.ambient.AmbientCreature;
	}

	/**
	 * The nearest living entity roughly under the crosshair, ignoring walls -- the Web Yank fallback
	 * for flying mobs that phase through blocks and never sit still enough for the strict view-vector
	 * raycast. Mirrors {@link #nearestItemInAim}: a ~13-degree cone, closest wins.
	 */
	private static LivingEntity nearestLivingInAim(ServerPlayer player) {
		return nearestLivingInAim(player, yankRange(player));
	}

	/** Same loose aim cone, with an explicit reach (the Combat Mode moves use their own ranges). */
	static LivingEntity nearestLivingInAim(ServerPlayer player, double range) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		AABB box = player.getBoundingBox().expandTowards(look.scale(range)).inflate(3.0);
		LivingEntity best = null;
		double bestScore = 0.972; // ~13 degrees
		for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, box,
				e -> e != player && e.isAlive() && !(e instanceof net.minecraft.world.entity.decoration.ArmorStand))) {
			Vec3 delta = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eye);
			double d = delta.length();
			if (d < 0.5 || d > range) {
				continue;
			}
			double aim = delta.normalize().dot(look);
			if (aim > bestScore) {
				bestScore = aim;
				best = e;
			}
		}
		return best;
	}

	private static ItemEntity nearestItemInAim(ServerPlayer player) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		AABB box = player.getBoundingBox().expandTowards(look.scale(yankRange(player))).inflate(2.5);
		ItemEntity best = null;
		double bestScore = 0.985; // v0.6.22: ~10 degrees -- must be genuinely aimed at
		for (ItemEntity item : player.level().getEntitiesOfClass(ItemEntity.class, box, ItemEntity::isAlive)) {
			Vec3 delta = item.position().add(0, 0.2, 0).subtract(eye);
			double dist = delta.length();
			if (dist < 0.5 || dist > yankRange(player)) {
				continue;
			}
			double aim = delta.normalize().dot(look);
			if (aim > bestScore) {
				bestScore = aim;
				best = item;
			}
		}
		return best;
	}

	// ---------------- C -- Wall Crawl toggle ----------------

	/**
	 * Flip the automatic wall-crawl mode (v0.6.17, replacing Web Cocoon). While it is on, brushing a
	 * climbable surface in mid-air sticks the player to it with no double-tap needed; the double-tap
	 * gestures still work regardless. Costs no webbing -- it is a stance, not an ability.
	 */
	public static void toggleWallCrawl(ServerPlayer player) {
		com.projecthero.mod.spider.data.SpiderManState c = SpiderMan.state(player).copy();
		c.wallCrawlEnabled = !c.wallCrawlEnabled;
		SpiderMan.save(player, c);
		AbilityHelpers.sound(player, SoundEvents.SPIDER_STEP, 0.5f, c.wallCrawlEnabled ? 1.6f : 0.8f);
		player.displayClientMessage(Component.translatable(c.wallCrawlEnabled
				? "message.projecthero.spider_man.wall_crawl_on"
				: "message.projecthero.spider_man.wall_crawl_off"), true);
	}

	// ---------------- passive: the sneak-jump super leap ----------------

	/**
	 * Sneak + jump from the ground launches Spider-Man ~10 blocks straight up (v0.9.10, was ~6).
	 * Applied as a velocity impulse and gated by a short cooldown; the client only asks for it on the
	 * exact gesture, and the server re-checks it is grounded and off cooldown.
	 */
	public static boolean superJump(ServerPlayer player) {
		if (!SpiderMan.hasPower(player) || player.isSpectator()) {
			return false;
		}
		if (!player.onGround() || player.isPassenger() || player.isInWater() || player.isInLava()
				|| player.onClimbable() || SpiderClimb.attached(player) || SpiderSwing.isSwinging(player)) {
			return false;
		}
		if (!SpiderMan.abilityReady(player, SUPER_JUMP)) {
			return false;
		}
		SpiderMan.triggerCooldown(player, SUPER_JUMP, CD_SUPER_JUMP);

		Vec3 look = player.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0, look.z);
		flat = flat.lengthSqr() < 1.0E-4 ? Vec3.ZERO : flat.normalize().scale(0.28);
		// v0.9.10: ~1.35 blocks/tick of initial rise -- clears a full 10-block pillar under vanilla
		// gravity/drag (peak ~10.1, simulated), up from the ~6 of v0.6.19's 1.05.
		AbilityHelpers.launchSelf(player, new Vec3(flat.x, 1.35, flat.z));

		ServerLevel level = (ServerLevel) player.level();
		level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY(), player.getZ(),
				16, 0.3, 0.05, 0.3, 0.04);
		level.sendParticles(ParticleTypes.ITEM_COBWEB, player.getX(), player.getY() + 0.1, player.getZ(),
				10, 0.25, 0.02, 0.25, 0.0);
		AbilityHelpers.sound(player, SoundEvents.SLIME_JUMP, 0.6f, 0.7f);
		return true;
	}

	// ---------------- V -- Web Net ----------------

	/**
	 * Spin a temporary platform of webbing where you are aiming -- a bridge over a gap, a floor over a
	 * drop, a landing pad mid-swing, or a trap in a doorway. It dissolves on its own and puts back
	 * whatever it covered, so it can never litter or grief the world.
	 */
	public static void webNet(ServerPlayer player) {
		if (!gate(player, WEB_NET, SpiderWebReserve.COST_WEB_NET)) {
			return;
		}
		ServerLevel level = (ServerLevel) player.level();
		BlockHitResult hit = AbilityHelpers.raycastBlock(player, NET_RANGE);
		Vec3 centre = hit.getType() == HitResult.Type.BLOCK
				? hit.getLocation().add(0, 0.1, 0)
				: player.getEyePosition().add(player.getLookAngle().scale(6.0));

		int placed = SpiderWebs.weaveNet(player, level, centre, 2);
		if (placed == 0) {
			player.displayClientMessage(Component.translatable("message.projecthero.spider_man.net_failed"), true);
			return;
		}
		webLine(level, handPos(player), centre);
		AbilityHelpers.burst(level, centre, ParticleTypes.ITEM_COBWEB, 24, 0.8);
		AbilityHelpers.sound(player, SoundEvents.WOOL_PLACE, 0.7f, 1.4f);
		fired(player, WEB_NET, SpiderWebReserve.COST_WEB_NET, CD_WEB_NET);
	}

	// ---------------- sneak + V -- Web Blossom (v0.6.23) ----------------

	/**
	 * Web Blossom: sneak and <b>hold V for 3 seconds</b> to charge, then Spider-Man leaps into the air
	 * and fires webbing in every direction. Everything within {@link #BLOSSOM_RADIUS} blocks takes
	 * {@link #BLOSSOM_DAMAGE} damage and is trapped in cobwebs -- frozen in place -- for
	 * {@link #BLOSSOM_TRAP_TICKS} (20 s). Costs {@link #BLOSSOM_COST} (75) webbing.
	 *
	 * <p>Charge / release / tick all mirror Thor's God of Thunder's Wrath: the charge is held in the
	 * synced {@link ModAttachments#SPIDER_BLOSSOM_CHARGE} attachment so the HUD can draw a buildup
	 * bar, it fires automatically once full, and letting go of sneak or V before then cancels it with
	 * nothing spent.
	 */
	public static void beginWebBlossom(ServerPlayer player) {
		if (player.getAttachedOrElse(ModAttachments.SPIDER_BLOSSOM_CHARGE, 0) > 0) {
			return; // already charging
		}
		if (!gate(player, WEB_BLOSSOM, BLOSSOM_COST)) {
			return;
		}
		player.setAttached(ModAttachments.SPIDER_BLOSSOM_CHARGE, 1);
		AbilityHelpers.sound(player, SoundEvents.SPIDER_AMBIENT, 0.7f, 0.6f);
	}

	/** V (or sneak) released: fire if fully charged, otherwise cancel harmlessly. */
	public static void releaseWebBlossom(ServerPlayer player) {
		int c = player.getAttachedOrElse(ModAttachments.SPIDER_BLOSSOM_CHARGE, 0);
		if (c <= 0) {
			return;
		}
		player.setAttached(ModAttachments.SPIDER_BLOSSOM_CHARGE, 0);
		if (c >= blossomChargeTicks(SpiderMan.state(player).combatMode)) {
			fireWebBlossom(player);
		} else {
			AbilityHelpers.sound(player, SoundEvents.FIRE_EXTINGUISH, 0.5f, 1.3f);
		}
	}

	/** Advances an in-progress Web Blossom charge; fires it at 3 s. Called every server tick. */
	public static void tickWebBlossom(ServerPlayer player) {
		int c = player.getAttachedOrElse(ModAttachments.SPIDER_BLOSSOM_CHARGE, 0);
		if (c <= 0) {
			return;
		}
		if (!SpiderMan.hasPower(player) || !player.isShiftKeyDown()
				|| !SpiderWebReserve.has(player, BLOSSOM_COST)) {
			player.setAttached(ModAttachments.SPIDER_BLOSSOM_CHARGE, 0);
			return;
		}
		c++;
		int chargeTicks = blossomChargeTicks(SpiderMan.state(player).combatMode);
		if (player.level() instanceof ServerLevel level) {
			double a = Math.min(1.0, c / (double) chargeTicks);
			level.sendParticles(ParticleTypes.ITEM_COBWEB, player.getX(), player.getY() + 1.0, player.getZ(),
					(int) (2 + a * 10), 0.5 + a * 0.6, 0.9, 0.5 + a * 0.6, 0.02);
			if (c % 6 == 0) {
				AbilityHelpers.sound(player, SoundEvents.SLIME_SQUISH, 0.4f, 0.6f + (float) a);
			}
		}
		if (c >= chargeTicks) {
			player.setAttached(ModAttachments.SPIDER_BLOSSOM_CHARGE, 0);
			fireWebBlossom(player);
		} else {
			player.setAttached(ModAttachments.SPIDER_BLOSSOM_CHARGE, c);
		}
	}

	private static void fireWebBlossom(ServerPlayer player) {
		if (!SpiderWebReserve.has(player, BLOSSOM_COST)) {
			return;
		}
		ServerLevel level = (ServerLevel) player.level();

		// Leap into the air.
		Vec3 dm = player.getDeltaMovement();
		AbilityHelpers.launchSelf(player, new Vec3(dm.x * 0.4, 1.15, dm.z * 0.4));

		Vec3 centre = player.position();
		Vec3 hand = player.getEyePosition();
		int caught = 0;
		AABB box = player.getBoundingBox().inflate(BLOSSOM_RADIUS);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box,
				t -> t != player && t.isAlive() && !(t instanceof Player))) {
			if (e.distanceToSqr(centre) > BLOSSOM_RADIUS * BLOSSOM_RADIUS) {
				continue;
			}
			AbilityHelpers.hurt(player, e, BLOSSOM_DAMAGE);
			// cocoonFor already applies Slowness/Weakness/Mining-fatigue and the tick loop that pins the
			// entity in place; add a hard jump-lock so it cannot hop out.
			SpiderWebs.cocoonFor(player, e, BLOSSOM_TRAP_TICKS);
			AbilityHelpers.applyControl(e, MobEffects.JUMP, BLOSSOM_TRAP_TICKS, -10);
			e.setDeltaMovement(0, Math.min(0.0, e.getDeltaMovement().y), 0);
			e.hurtMarked = true;
			webLine(level, hand, e.position().add(0, e.getBbHeight() * 0.5, 0));
			AbilityHelpers.burst(level, e.position().add(0, e.getBbHeight() * 0.5, 0),
					ParticleTypes.ITEM_COBWEB, 20, 0.4);
			caught++;
		}

		// A big radial web nova regardless of what it caught.
		for (int ring = 4; ring <= (int) BLOSSOM_RADIUS; ring += 4) {
			for (int a = 0; a < 360; a += 12) {
				double rad = Math.toRadians(a);
				level.sendParticles(ParticleTypes.ITEM_COBWEB,
						centre.x + Math.cos(rad) * ring, centre.y + 0.5, centre.z + Math.sin(rad) * ring,
						1, 0.0, 0.0, 0.0, 0.0);
			}
		}
		level.sendParticles(ParticleTypes.EXPLOSION, centre.x, centre.y + 1.0, centre.z, 1, 0, 0, 0, 0);
		AbilityHelpers.sound(player, SoundEvents.SLIME_SQUISH, 1.2f, 0.5f);
		AbilityHelpers.sound(player, SoundEvents.WOOL_PLACE, 1.0f, 0.7f);
		player.displayClientMessage(Component.translatable("message.projecthero.spider_man.web_blossom", caught), true);

		SpiderWebReserve.spend(player, BLOSSOM_COST, true);
		SpiderMan.triggerCooldown(player, WEB_BLOSSOM, CD_WEB_BLOSSOM);
	}

	// ---------------- passive: the mid-air jump ----------------

	/**
	 * The second jump. Server-authoritative in every respect that matters: it refuses unless the
	 * player really is airborne, off cooldown, and not doing something that owns their movement
	 * already (riding, swimming, elytra, ladders, creative flight), so a client that spams the request
	 * gets exactly one extra jump per second like everybody else.
	 */
	public static boolean doubleJump(ServerPlayer player) {
		if (!SpiderMan.hasPower(player) || player.isSpectator()) {
			return false;
		}
		if (player.onGround() || player.isPassenger() || player.isFallFlying() || player.getAbilities().flying
				|| player.onClimbable() || player.isInWater() || player.isInLava() || player.isSwimming()
				|| SpiderClimb.attached(player)
				// On a line, jump already means "reel in" -- and a rope is a better second jump anyway.
				|| SpiderSwing.isSwinging(player)) {
			return false;
		}
		// Refuse when the player could just land and jump normally: pressing jump a beat before touching
		// down was being eaten as an accidental double jump, which is what "it goes on cooldown even
		// though I only jumped once" was (v0.6.6).
		if (!player.level().noCollision(player, player.getBoundingBox().expandTowards(0.0, -1.15, 0.0))) {
			return false;
		}
		// v0.6.17: step assist (STEP_HEIGHT +0.4) briefly lifts the player over a block edge while they
		// are pressed into it -- that is a horizontal collision, never a real leap, and must not be read
		// as the mid-air jump.
		if (player.horizontalCollision) {
			return false;
		}
		long now = player.level().getGameTime();
		if (now < SpiderMan.state(player).doubleJumpReadyAt) {
			return false;
		}

		com.projecthero.mod.spider.data.SpiderManState c = SpiderMan.state(player).copy();
		c.doubleJumpReadyAt = now + CD_DOUBLE_JUMP;
		SpiderMan.save(player, c);
		SpiderMan.triggerCooldown(player, DOUBLE_JUMP, CD_DOUBLE_JUMP);

		Vec3 v = player.getDeltaMovement();
		Vec3 look = player.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0, look.z);
		flat = flat.lengthSqr() < 1.0E-4 ? Vec3.ZERO : flat.normalize().scale(0.34);
		// v0.6.21: ~0.8 blocks/tick of initial rise -- a clean 4-block jump under vanilla gravity/drag
		// (was 0.62, roughly 2.5 blocks).
		AbilityHelpers.launchSelf(player, new Vec3(v.x * 0.55 + flat.x, 0.8, v.z * 0.55 + flat.z));

		ServerLevel level = (ServerLevel) player.level();
		level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY(), player.getZ(),
				10, 0.25, 0.05, 0.25, 0.02);
		AbilityHelpers.sound(player, SoundEvents.PHANTOM_FLAP, 0.5f, 1.6f);
		return true;
	}

	/**
	 * Wall leap (v0.6.23): jumping while clung to a wall launches Spider-Man roughly <b>7 blocks</b>
	 * directly in the direction he is looking, with a slight upward bias and a small shove off the
	 * surface so he clears its lip. Free, on no cooldown.
	 */
	public static void leapFromSurface(ServerPlayer player, Vec3 normal) {
		Vec3 look = player.getLookAngle();
		Vec3 leap = look.scale(0.78).add(normal.scale(0.18));
		// always at least a little up, more when the player is looking upward
		leap = new Vec3(leap.x, Math.max(0.30, leap.y + 0.30), leap.z);
		AbilityHelpers.launchSelf(player, leap);
		player.resetFallDistance();
		player.hurtMarked = true;
		AbilityHelpers.sound(player, SoundEvents.SPIDER_STEP, 0.6f, 1.4f);
		if (player.level() instanceof ServerLevel sl) {
			sl.sendParticles(ParticleTypes.ITEM_COBWEB, player.getX(), player.getY() + 0.9, player.getZ(),
					10, 0.25, 0.3, 0.25, 0.02);
		}
	}

	/**
	 * Which of the six universal keys fires {@code abilityId}. Single source of truth for the slot
	 * layout documented above, so the guide and the HUD cannot drift from the router.
	 */
	public static char slotKeyOf(String abilityId) {
		return switch (abilityId) {
			case WEB_SWING -> com.projecthero.mod.hero.AbilitySlot.SLOT_1.defaultKey();
			case WEB_ZIP -> com.projecthero.mod.hero.AbilitySlot.SLOT_2.defaultKey();
			case WEB_YANK -> com.projecthero.mod.hero.AbilitySlot.SLOT_3.defaultKey();
			case WEB_SHOT -> com.projecthero.mod.hero.AbilitySlot.SLOT_4.defaultKey();
			case WEB_NET -> com.projecthero.mod.hero.AbilitySlot.SLOT_5.defaultKey();
			case WALL_CRAWL -> com.projecthero.mod.hero.AbilitySlot.SLOT_6.defaultKey();
			default -> '-';
		};
	}
}
