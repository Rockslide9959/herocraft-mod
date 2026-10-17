package com.projecthero.mod.miraculous.catnoir;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.miraculous.Miraculous;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * v0.16.6: <b>CATACLYSM</b> (Shift+Z), Cat Noir's special.
 *
 * <ul>
 *   <li><b>The call</b> ({@link #call}): his hand crackles with black destruction energy for
 *       {@link CatNoirConfig#CATACLYSM_CHARGE_TICKS}, and the core's 5-minute countdown starts NOW -- as in the show,
 *       calling the power is what spends the Miraculous, not landing it. One Cataclysm per transformation: once a touch
 *       has landed the baton is rotted black ({@link CatNoir#V_ROTTED}) for the rest of it. If the charge runs out
 *       untouched it fizzles and can be called again after {@link CatNoirConfig#CATACLYSM_COOLDOWN} (the countdown
 *       keeps running).</li>
 *   <li><b>A block</b> ({@link #touchBlock}: punched or used): it and every block joined to it face-to-face within
 *       {@link CatNoirConfig#CATACLYSM_RADIUS} crumble to dust in a spreading wave -- each ring cracks and darkens, then falls
 *       as dust, one block further out every {@link CatNoirConfig#CATACLYSM_STEP_TICKS} (about a second to the edge). Nothing
 *       drops. It follows the mod's block-damage rules: the {@code abilityTerrainDamage} config and the {@code mobGriefing}
 *       gamerule, spawn protection / claims ({@code mayInteract}), loaded chunks only; never fluids, bedrock and the
 *       other unbreakables (hardness -1 or {@code #wither_immune}), and never a block entity (chests, furnaces, spawners
 *       -- nothing is ever voided with its contents).</li>
 *   <li><b>A creature</b> ({@link #touchEntity}: attacked): {@link CatNoirConfig#CATACLYSM_DAMAGE} and Wither III; every
 *       piece of armour a mob wears shatters (a player's is spared). A boss takes at most {@link CatNoirConfig#BOSS_MAX_FRACTION_CATACLYSM} of its
 *       max health and keeps its armour.</li>
 * </ul>
 * The touch hooks are in {@link CatNoirMod} (attack block / use block / attack entity).
 */
public final class CatNoirCataclysm {
	/** One block of a crumble: where, which ring, the progress id its cracks are sent under. */
	private record Grain(BlockPos pos, int ring, int crackId) {
	}

	private static final class Crumble {
		final ServerLevel level;
		final long start;
		final List<Grain> grains;

		Crumble(ServerLevel level, long start, List<Grain> grains) {
			this.level = level;
			this.start = start;
			this.grains = grains;
		}
	}

	private static final List<Crumble> CRUMBLES = new CopyOnWriteArrayList<>();
	private static int nextCrackId = 0x6C000000;

	private CatNoirCataclysm() {
	}

	static void clearSessionState() {
		CRUMBLES.clear();
	}

	/** Shift+Z: call CATACLYSM. */
	public static void call(ServerPlayer p) {
		if (CatNoir.rotted(p)) {
			Miraculous.say(p, "message.projecthero.cat_noir.cataclysm_spent", ChatFormatting.DARK_PURPLE);
			return;
		}
		if (CatNoir.charged(p)) {
			return;
		}
		if (!CatNoirAbilities.begin(p, CatNoir.CATACLYSM)) {
			return;
		}
		long t = p.level().getGameTime();
		Miraculous.edit(p, n -> {
			n.vals.put(CatNoir.V_CATACLYSM, t + CatNoirConfig.CATACLYSM_CHARGE_TICKS);
			n.animId = CatNoir.ANIM_CATACLYSM_CALL;
			n.animStart = t;
		});
		Miraculous.startCountdown(p);
		Miraculous.say(p, "message.projecthero.cat_noir.cataclysm", ChatFormatting.DARK_PURPLE);
		ServerLevel level = (ServerLevel) p.level();
		level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WITHER_AMBIENT, SoundSource.PLAYERS, 0.6f, 1.6f);
		level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.PLAYERS, 0.4f, 1.8f);
		Vec3 hand = AbilityHelpers.handPosition(p);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, hand.x, hand.y + 0.3, hand.z, 3, 0.1, 0.2, 0.1, 0.02);
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, hand.x, hand.y + 0.3, hand.z, 10, 0.15, 0.3, 0.15, 0.05);
	}

	/** The charge ran out untouched: it fizzles (the countdown keeps running), and can be called again later. */
	static void tick(ServerPlayer p) {
		long until = CatNoir.val(p, CatNoir.V_CATACLYSM);
		if (until > 0L && p.level().getGameTime() >= until) {
			Miraculous.setVal(p, CatNoir.V_CATACLYSM, 0L);
			Miraculous.setCooldown(p, CatNoir.CATACLYSM, CatNoirConfig.CATACLYSM_COOLDOWN);
			Miraculous.say(p, "message.projecthero.cat_noir.cataclysm_fizzled", ChatFormatting.GRAY);
			Vec3 hand = AbilityHelpers.handPosition(p);
			((ServerLevel) p.level()).sendParticles(ParticleTypes.SMOKE, hand.x, hand.y, hand.z, 12, 0.1, 0.1, 0.1, 0.02);
		}
	}

	/** The touch landed: the charge is spent and the baton rots. */
	private static void spend(ServerPlayer p) {
		long t = p.level().getGameTime();
		Miraculous.edit(p, n -> {
			n.vals.remove(CatNoir.V_CATACLYSM);
			n.vals.put(CatNoir.V_ROTTED, 1L);
			n.animId = CatNoir.ANIM_CATACLYSM_TOUCH;
			n.animStart = t;
		});
		p.swing(InteractionHand.MAIN_HAND, true);
	}

	// ---------------------------------------------------------------- blocks

	/** May CATACLYSM crumble blocks here at all (the mod's terrain-damage config and the mobGriefing rule)? */
	public static boolean canCrumble(ServerLevel level) {
		return AbilityHelpers.canGrief() && level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
	}

	/** One block CATACLYSM can turn to dust. */
	public static boolean crumbles(ServerLevel level, ServerPlayer p, BlockPos pos) {
		if (!level.isLoaded(pos) || !level.mayInteract(p, pos)) {
			return false;
		}
		BlockState st = level.getBlockState(pos);
		if (st.isAir() || st.hasBlockEntity() || !st.getFluidState().isEmpty() || st.is(BlockTags.WITHER_IMMUNE)) {
			return false;
		}
		return st.getDestroySpeed(level, pos) >= 0.0f;
	}

	/** A charged touch on {@code pos}. False (nothing happens) if the hand isn't charged. */
	public static boolean touchBlock(ServerPlayer p, BlockPos pos) {
		if (!CatNoir.charged(p)) {
			return false;
		}
		spend(p);
		ServerLevel level = (ServerLevel) p.level();
		Vec3 c = Vec3.atCenterOf(pos);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.WITHER_BREAK_BLOCK, SoundSource.PLAYERS, 0.7f, 0.6f);
		level.sendParticles(ParticleTypes.SQUID_INK, c.x, c.y, c.z, 8, 0.4, 0.4, 0.4, 0.03);
		if (!canCrumble(level) || !crumbles(level, p, pos)) {
			return true; // the touch is spent all the same
		}
		List<Grain> grains = new ArrayList<>();
		Set<BlockPos> seen = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		queue.add(pos.immutable());
		seen.add(pos.immutable());
		double r2 = CatNoirConfig.CATACLYSM_RADIUS * CatNoirConfig.CATACLYSM_RADIUS;
		while (!queue.isEmpty() && grains.size() < CatNoirConfig.CATACLYSM_MAX_BLOCKS) {
			BlockPos b = queue.poll();
			int ring = (int) Math.round(Math.sqrt(b.distSqr(pos)));
			grains.add(new Grain(b, ring, nextCrackId++));
			for (Direction d : Direction.values()) {
				BlockPos n = b.relative(d);
				if (seen.contains(n) || n.distSqr(pos) > r2 + 1.0e-6) {
					continue;
				}
				seen.add(n);
				if (crumbles(level, p, n)) {
					queue.add(n);
				}
			}
		}
		CRUMBLES.add(new Crumble(level, level.getGameTime(), grains));
		return true;
	}

	/** The crumbles in this level, a step a tick (END_WORLD_TICK). */
	static void worldTick(ServerLevel level) {
		if (CRUMBLES.isEmpty()) {
			return;
		}
		long now = level.getGameTime();
		for (Iterator<Crumble> it = CRUMBLES.iterator(); it.hasNext();) {
			Crumble c = it.next();
			if (c.level != level) {
				continue;
			}
			long age = now - c.start;
			boolean left = false;
			for (Grain g : c.grains) {
				long crackAt = (long) g.ring() * CatNoirConfig.CATACLYSM_STEP_TICKS;
				long fallAt = crackAt + CatNoirConfig.CATACLYSM_CRACK_TICKS;
				if (age < fallAt) {
					left = true;
				}
				if (age >= crackAt && age < fallAt) {
					// cracking and darkening: the break overlay creeps up to stage 9, black ash smokes off it
					int stage = (int) Math.min(9, 2 + (age - crackAt) * 8 / CatNoirConfig.CATACLYSM_CRACK_TICKS);
					level.destroyBlockProgress(g.crackId(), g.pos(), stage);
					if ((age + g.pos().hashCode()) % 3 == 0) {
						Vec3 p = Vec3.atCenterOf(g.pos());
						level.sendParticles(new DustParticleOptions(new Vector3f(0.08f, 0.04f, 0.1f), 1.2f), p.x, p.y + 0.45, p.z, 2, 0.3, 0.1, 0.3, 0.0);
					}
				} else if (age == fallAt) {
					level.destroyBlockProgress(g.crackId(), g.pos(), -1);
					BlockState st = level.getBlockState(g.pos());
					if (!st.isAir() && !st.hasBlockEntity() && st.getFluidState().isEmpty()) {
						level.destroyBlock(g.pos(), false);
						Vec3 p = Vec3.atCenterOf(g.pos());
						level.sendParticles(new DustParticleOptions(new Vector3f(0.22f, 0.2f, 0.18f), 1.6f), p.x, p.y, p.z, 5, 0.3, 0.3, 0.3, 0.0);
						level.sendParticles(ParticleTypes.ASH, p.x, p.y, p.z, 6, 0.35, 0.35, 0.35, 0.0);
						if (g.ring() == 0 || (g.pos().hashCode() & 7) == 0) {
							level.playSound(null, p.x, p.y, p.z, SoundEvents.SAND_BREAK, SoundSource.BLOCKS, 0.6f, 0.6f);
						}
					}
				}
			}
			if (!left) {
				CRUMBLES.remove(c);
			}
		}
	}

	/** Test hook: crumbles still running anywhere. */
	public static int running() {
		return CRUMBLES.size();
	}

	// ---------------------------------------------------------------- creatures

	/** A charged touch on a creature. False if the hand isn't charged or it may not be harmed. */
	public static boolean touchEntity(ServerPlayer p, LivingEntity target) {
		if (!CatNoir.charged(p) || !CatNoirCombat.isTarget(p, target)) {
			return false;
		}
		spend(p);
		boolean boss = CatNoirCombat.isBoss(target);
		// the armour goes first, so the destruction lands in full
		if (!boss && !(target instanceof Player)) {
			// (a player's armour is spared: hero suits and enchanted sets are far too costly to void with one PvP touch)
			shatterArmour(target);
		}
		CatNoirCombat.strike(p, target, p.position(), CatNoirConfig.CATACLYSM_DAMAGE, 0.0, 0.0, CatNoirConfig.BOSS_MAX_FRACTION_CATACLYSM);
		if (target.isAlive()) {
			if (target instanceof Player) {
				AbilityHelpers.applyControl(target, MobEffects.WITHER, CatNoirConfig.CATACLYSM_WITHER_TICKS, CatNoirConfig.CATACLYSM_WITHER_AMPLIFIER);
			} else {
				target.addEffect(new MobEffectInstance(MobEffects.WITHER, CatNoirConfig.CATACLYSM_WITHER_TICKS, CatNoirConfig.CATACLYSM_WITHER_AMPLIFIER),
						p);
			}
		}
		ServerLevel level = (ServerLevel) p.level();
		Vec3 c = target.position().add(0, target.getBbHeight() * 0.5, 0);
		level.sendParticles(ParticleTypes.SQUID_INK, c.x, c.y, c.z, 8, target.getBbWidth() * 0.5, target.getBbHeight() * 0.4,
				target.getBbWidth() * 0.5, 0.05);
		level.sendParticles(new DustParticleOptions(new Vector3f(0.3f, 0.05f, 0.4f), 1.4f), c.x, c.y, c.z, 20, target.getBbWidth() * 0.5,
				target.getBbHeight() * 0.4, target.getBbWidth() * 0.5, 0.0);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.WITHER_HURT, SoundSource.PLAYERS, 0.7f, 0.7f);
		return true;
	}

	/** Every worn armour piece breaks (with the vanilla break sound and particles). */
	public static void shatterArmour(LivingEntity target) {
		for (EquipmentSlot slot : new EquipmentSlot[] { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
				EquipmentSlot.BODY }) {
			ItemStack worn = target.getItemBySlot(slot);
			if (worn.isEmpty() || !(worn.getItem() instanceof net.minecraft.world.item.ArmorItem)) {
				continue;
			}
			if (worn.isDamageableItem()) {
				worn.hurtAndBreak(worn.getMaxDamage() + 1, target, slot);
			}
			if (!target.getItemBySlot(slot).isEmpty()) {
				target.onEquippedItemBroken(worn.getItem(), slot);
				target.setItemSlot(slot, ItemStack.EMPTY);
			}
		}
	}
}
