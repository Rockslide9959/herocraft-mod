package com.projecthero.mod.omnitrix;

import java.util.List;

import com.projecthero.mod.omnitrix.alien.AlienStatus;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.diamondhead.DiamondheadKit;
import com.projecthero.mod.omnitrix.alien.fourarms.FourArmsKit;
import com.projecthero.mod.omnitrix.alien.ghostfreak.GhostfreakKit;
import com.projecthero.mod.omnitrix.alien.greymatter.GreyMatterKit;
import com.projecthero.mod.omnitrix.alien.heatblast.HeatblastKit;
import com.projecthero.mod.omnitrix.alien.ripjaws.RipjawsKit;
import com.projecthero.mod.omnitrix.alien.stinkfly.StinkflyKit;
import com.projecthero.mod.omnitrix.alien.upgrade.UpgradeKit;
import com.projecthero.mod.omnitrix.alien.wildmutt.WildmuttKit;
import com.projecthero.mod.omnitrix.alien.xlr8.Xlr8Kit;
import com.projecthero.mod.omnitrix.data.OmnitrixState;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * v0.17.2 (user: "an omnitrix automatic transform ... depending on the situation the player is in holding R for 1 second turns the
 * player into an alien"): <b>hold R</b> as a human. The press raises the watch as always; held {@link #HOLD_TICKS} the dial spins
 * on its own, the Omnitrix reads the situation ({@link #pick}) and slaps itself down into the alien that fits. Recharge, the
 * timeout and Master Control apply as for any transform. Let go early and nothing happens (the watch just stays up).
 *
 * <pre>
 *    1  underwater / drowning                         Ripjaws
 *    2  falling 4+ blocks, the void, thrown up         Stinkfly (Ghostfreak if Stinkfly can't)
 *    3  on fire, in lava, beside lava in the Nether    Heatblast
 *    4  in combat, below 30% health                    Diamondhead
 *    5  in combat with a boss or a big brute (after him or within 8 blocks)  Four Arms
 *    6  in combat, mostly ranged attackers             Diamondhead
 *    7  in combat with a crowd (4+ close)              Heatblast
 *    8  in combat with one or two melee mobs           Wildmutt
 *    9  sneaking near hostiles, unseen                 Ghostfreak
 *   10  riding a cart / boat, or at a machine          Upgrade
 *   11  a 1-block gap / tight tunnel ahead             Grey Matter
 *   12  sprinting, no threats                          XLR8
 *   13  in the dark, no threats                        Wildmutt
 *   14  anything else                                  the alien on the dial
 * </pre>
 * The first rule that matches with a playable alien wins; a rule whose alien can't be used falls through to the next.
 */
public final class OmnitrixAuto {
	/** How long R is held before the Omnitrix decides. */
	public static final int HOLD_TICKS = 20;
	/** Timer key (synced): the game time R went down for an auto-transform (the HUD ring and the dial spin read it). */
	public static final String HOLD_TIMER = "omnitrix.auto_hold";
	/** "In combat": hit or were hit this recently (ticks), or a hostile targeting you within {@link #THREAT_RANGE}. */
	public static final int COMBAT_TICKS = 160;
	public static final double THREAT_RANGE = 16.0;
	public static final double CROWD_RANGE = 8.0;
	public static final int CROWD = 4;
	public static final float LOW_HEALTH = 0.3f;
	public static final float FALL_BLOCKS = 4f;

	/** One decision: the alien and the reason's lang key ({@code message.projecthero.omnitrix.auto.<reason>}). */
	public record Pick(String alien, String reason) {
	}

	private OmnitrixAuto() {
	}

	// ---------------------------------------------------------------- the hold

	/** R went down and raised the watch: start counting. */
	static void startHold(ServerPlayer p) {
		OmnitrixState n = Omnitrix.state(p).copy();
		n.timers.put(HOLD_TIMER, p.level().getGameTime());
		Omnitrix.save(p, n);
	}

	/** R came up (or the watch went down): stop counting. */
	static void stopHold(ServerPlayer p) {
		OmnitrixState s = Omnitrix.state(p);
		if (s.timers.containsKey(HOLD_TIMER)) {
			OmnitrixState n = s.copy();
			n.timers.remove(HOLD_TIMER);
			Omnitrix.save(p, n);
		}
	}

	/** Client-safe: 0..1 of the hold, or -1 when R isn't being held for one. */
	public static float holdProgress(OmnitrixState s, long now, float partial) {
		Long at = s.timers.get(HOLD_TIMER);
		if (at == null || s.transformed()) {
			return -1f;
		}
		return Math.max(0f, Math.min(1f, (now - at + partial) / HOLD_TICKS));
	}

	/** Every tick: a hold that has run its second decides. */
	static void tick(ServerPlayer p) {
		OmnitrixState s = Omnitrix.state(p);
		Long at = s.timers.get(HOLD_TIMER);
		if (at == null) {
			return;
		}
		if (s.transformed() || !s.selecting) {
			stopHold(p);
			return;
		}
		if (p.level().getGameTime() - at >= HOLD_TICKS) {
			stopHold(p);
			fire(p);
		}
	}

	/** The hold is done: pick, put it on the dial and slap. True if the slap went in (or is queued for the core). */
	public static boolean fire(ServerPlayer p) {
		Pick pick = pick(p);
		OmnitrixState n = Omnitrix.state(p).copy();
		n.selected = pick.alien();
		Omnitrix.save(p, n);
		if (!Omnitrix.slap(p)) {
			return false;
		}
		Aliens.Alien a = Aliens.byId(pick.alien());
		p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.auto." + pick.reason(),
				Component.translatable(a == null ? "projecthero.omnitrix.alien.unknown" : a.nameKey())).withStyle(ChatFormatting.GREEN), true);
		return true;
	}

	// ---------------------------------------------------------------- the decision

	/** What the Omnitrix would turn {@code p} into right now. */
	public static Pick pick(ServerPlayer p) {
		Level l = p.level();
		// 1 underwater
		if (p.isEyeInFluid(FluidTags.WATER) || (p.isInWater() && p.getAirSupply() < p.getMaxAirSupply())) {
			Pick r = first("underwater", RipjawsKit.ID);
			if (r != null) {
				return r;
			}
		}
		// 2 falling
		boolean falling = !p.onGround() && !p.isInWater() && !p.getAbilities().flying
				&& (p.fallDistance >= FALL_BLOCKS || p.getY() < l.getMinBuildHeight() || p.getDeltaMovement().y < -0.8);
		if (falling) {
			Pick r = first("falling", StinkflyKit.ID, GhostfreakKit.ID);
			if (r != null) {
				return r;
			}
		}
		// 3 fire
		if (p.isOnFire() || p.isInLava() || (l.dimension() == Level.NETHER && lavaNear(p, 3))) {
			Pick r = first("fire", HeatblastKit.ID);
			if (r != null) {
				return r;
			}
		}
		List<Mob> hostiles = l.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(THREAT_RANGE),
				m -> m.isAlive() && m instanceof Enemy && m.distanceToSqr(p) <= THREAT_RANGE * THREAT_RANGE);
		boolean targeted = hostiles.stream().anyMatch(m -> m.getTarget() == p);
		int since = Math.min(p.tickCount - p.getLastHurtByMobTimestamp(), p.tickCount - p.getLastHurtMobTimestamp());
		boolean recent = (p.getLastHurtByMobTimestamp() > 0 || p.getLastHurtMobTimestamp() > 0) && since >= 0 && since <= COMBAT_TICKS;
		boolean combat = targeted || (recent && !hostiles.isEmpty());
		if (combat) {
			// what he is actually fighting: whatever is after him, or right on him (within the crowd range) -- not a mob 16 blocks off
			List<Mob> engaged = hostiles.stream().filter(m -> m.getTarget() == p || m.distanceToSqr(p) <= CROWD_RANGE * CROWD_RANGE).toList();
			// 4 hurting
			if (p.getHealth() < p.getMaxHealth() * LOW_HEALTH) {
				Pick r = first("low_health", DiamondheadKit.ID);
				if (r != null) {
					return r;
				}
			}
			// 5 a boss / a brute
			if (engaged.stream().anyMatch(OmnitrixAuto::brute)) {
				Pick r = first("boss", FourArmsKit.ID);
				if (r != null) {
					return r;
				}
			}
			// 6 mostly ranged
			long ranged = engaged.stream().filter(OmnitrixAuto::ranged).count();
			if (ranged > 0 && ranged * 2 > engaged.size()) {
				Pick r = first("ranged", DiamondheadKit.ID);
				if (r != null) {
					return r;
				}
			}
			// 7 a crowd
			long close = engaged.stream().filter(m -> m.distanceToSqr(p) <= CROWD_RANGE * CROWD_RANGE).count();
			if (close >= CROWD) {
				Pick r = first("crowd", HeatblastKit.ID);
				if (r != null) {
					return r;
				}
			}
			// 8 one or two
			Pick r = first("melee", WildmuttKit.ID, FourArmsKit.ID);
			if (r != null) {
				return r;
			}
		}
		// 9 sneaking up on them
		if (p.isShiftKeyDown() && !hostiles.isEmpty() && !targeted) {
			Pick r = first("stealth", GhostfreakKit.ID);
			if (r != null) {
				return r;
			}
		}
		// 10 tech
		if (techNear(p)) {
			Pick r = first("tech", UpgradeKit.ID);
			if (r != null) {
				return r;
			}
		}
		// 11 a tight gap
		if (tightAhead(p)) {
			Pick r = first("tight", GreyMatterKit.ID);
			if (r != null) {
				return r;
			}
		}
		boolean threats = !hostiles.isEmpty();
		// 12 travelling
		if (p.isSprinting() && !threats) {
			Pick r = first("travel", Xlr8Kit.ID);
			if (r != null) {
				return r;
			}
		}
		// 13 the dark
		if (!threats && l.getMaxLocalRawBrightness(BlockPos.containing(p.getEyePosition())) <= 4) {
			Pick r = first("dark", WildmuttKit.ID);
			if (r != null) {
				return r;
			}
		}
		// 14 whatever is on the dial
		String sel = Omnitrix.state(p).selected;
		if (!Aliens.playable(sel) && !Aliens.playable().isEmpty()) {
			sel = Aliens.playable().get(0).id();
		}
		return new Pick(sel, "dial");
	}

	private static Pick first(String reason, String... aliens) {
		for (String a : aliens) {
			if (Aliens.playable(a)) {
				return new Pick(a, reason);
			}
		}
		return null;
	}

	/** A boss, or a brute: 100+ health or wider than a player and a half (iron golem, ravager, Warden, Titans...). */
	static boolean brute(LivingEntity e) {
		return AlienStatus.isBoss(e) || e.getMaxHealth() >= 100f || e.getBbWidth() >= 1.35f;
	}

	/** Fights from range: bows, crossbows, potions, fireballs, shulker bullets. */
	static boolean ranged(Mob m) {
		return m instanceof RangedAttackMob || m instanceof Blaze || m instanceof Ghast || m instanceof Shulker;
	}

	private static boolean lavaNear(ServerPlayer p, int r) {
		BlockPos c = p.blockPosition();
		for (BlockPos q : BlockPos.betweenClosed(c.offset(-r, -r, -r), c.offset(r, r, r))) {
			if (p.level().getFluidState(q).is(FluidTags.LAVA)) {
				return true;
			}
		}
		return false;
	}

	/** Riding a cart or boat, a cart within reach, or looking at a redstone machine within 4 blocks. */
	static boolean techNear(ServerPlayer p) {
		if (p.getVehicle() instanceof AbstractMinecart || p.getVehicle() instanceof Boat) {
			return true;
		}
		if (!p.level().getEntitiesOfClass(AbstractMinecart.class, p.getBoundingBox().inflate(3.0)).isEmpty()) {
			return true;
		}
		HitResult hit = p.pick(4.0, 1f, false);
		if (hit instanceof BlockHitResult bh && hit.getType() == HitResult.Type.BLOCK) {
			BlockState st = p.level().getBlockState(bh.getBlockPos());
			return st.isSignalSource() || st.getBlock() instanceof net.minecraft.world.level.block.piston.PistonBaseBlock
					|| st.getBlock() instanceof net.minecraft.world.level.block.DispenserBlock
					|| st.getBlock() instanceof net.minecraft.world.level.block.HopperBlock
					|| st.getBlock() instanceof net.minecraft.world.level.block.RedStoneWireBlock
					|| st.getBlock() instanceof net.minecraft.world.level.block.CrafterBlock;
		}
		return false;
	}

	/** A 1-block-high opening right in front: the feet's block open, the head's block solid. */
	static boolean tightAhead(ServerPlayer p) {
		Direction d = p.getDirection();
		BlockPos feet = p.blockPosition().relative(d);
		Level l = p.level();
		boolean feetOpen = l.getBlockState(feet).getCollisionShape(l, feet).isEmpty();
		boolean headShut = !l.getBlockState(feet.above()).getCollisionShape(l, feet.above()).isEmpty();
		return feetOpen && headShut;
	}
}
