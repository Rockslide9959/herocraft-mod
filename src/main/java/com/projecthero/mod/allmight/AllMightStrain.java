package com.projecthero.mod.allmight;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.allmight.data.AllMightState;
import com.projecthero.mod.allmight.data.AllMightState.Limb;
import com.projecthero.mod.attachment.ModAttachments;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7 (user: "come up with a better strain system for one for all"): One For All's body -- the safe limit, Output past it,
 * and per-limb strain. Design and numbers: {@code docs/OFA_STRAIN.md}; every number is in {@link AllMightConfig}.
 *
 * <ul>
 * <li><b>Safe limit</b> ({@link #limit}): the mastery percentage his body can take. Starts at 20%, rises a little with every
 * Smash (more when thrown past it), up to 100%. A borrowed One For All has a fixed 15% limit.</li>
 * <li><b>Output</b> ({@link AllMight#outputPct}): landed hits charge it up to the limit; Plus Ultra and the United States of Smash
 * push it past.</li>
 * <li><b>Over the limit</b> ({@link #cast}): a Smash thrown with Output past the limit strains the limb that threw it --
 * {@code weight x (output - limit) x 0.75} -- and costs a little HP. Full Cowl past the limit (Plus Ultra) strains every limb
 * slowly ({@link #tick}).</li>
 * <li><b>Limbs</b>: Strained from 40 (that limb's Smashes x0.85), Hurt from 70 (x0.65), Broken at 100 -- that limb's Smashes are
 * refused until it mends back to 50. Limbs heal every second (faster in the Base Form, with Regeneration, food or a bed).</li>
 * </ul>
 * Client-safe readers take a {@link Player} and only read the synced {@link AllMightState}.
 */
public final class AllMightStrain {
	/** Last seen food level per player (eating raises it: that heals). */
	private static final Map<UUID, Integer> LAST_FOOD = new ConcurrentHashMap<>();
	/** Game time of the last strain tick per player (a player ticked twice in one game tick -- a test driving it too -- steps once). */
	private static final Map<UUID, Long> LAST_TICK = new ConcurrentHashMap<>();

	private AllMightStrain() {
	}

	public static void clearSessionState() {
		LAST_FOOD.clear();
		LAST_TICK.clear();
	}

	public static void forget(UUID id) {
		LAST_FOOD.remove(id);
		LAST_TICK.remove(id);
	}

	// ---------------------------------------------------------------- which limb throws which move

	private static final Limb[] RIGHT = { Limb.RIGHT_ARM };
	private static final Limb[] LEFT = { Limb.LEFT_ARM };
	private static final Limb[] BOTH_ARMS = { Limb.RIGHT_ARM, Limb.LEFT_ARM };
	private static final Limb[] LEGS = { Limb.LEGS };
	private static final Limb[] NONE = {};

	/** The limb(s) a move is thrown with (empty: not a Smash -- the Leap and Plus Ultra). */
	public static Limb[] limbsOf(String abilityId) {
		return switch (abilityId) {
			case AllMightAbilities.DETROIT, AllMightAbilities.MISSOURI, AllMightAbilities.UNITED_STATES -> RIGHT;
			case AllMightAbilities.TEXAS, AllMightAbilities.DELAWARE -> LEFT;
			case AllMightAbilities.CAROLINA -> BOTH_ARMS;
			case AllMightAbilities.NEW_HAMPSHIRE, AllMightAbilities.MANCHESTER, AllMightAbilities.OKLAHOMA, AllMightAbilities.ST_LOUIS -> LEGS;
			default -> NONE;
		};
	}

	/** How hard a move tears at its limb past the limit (x {@code OVER_STRAIN_PER_POINT} per point over). */
	public static float weightOf(String abilityId) {
		return switch (abilityId) {
			case AllMightAbilities.DETROIT, AllMightAbilities.TEXAS, AllMightAbilities.MANCHESTER -> 1.0f;
			case AllMightAbilities.NEW_HAMPSHIRE, AllMightAbilities.OKLAHOMA, AllMightAbilities.MISSOURI, AllMightAbilities.ST_LOUIS -> 0.9f;
			case AllMightAbilities.CAROLINA -> 0.7f; // on each arm
			case AllMightAbilities.DELAWARE -> 0.5f; // a finger flick
			case AllMightAbilities.UNITED_STATES -> 1.5f;
			default -> 0f;
		};
	}

	// ---------------------------------------------------------------- client-safe reads

	private static @Nullable AllMightState peek(Player player) {
		return player.getAttachedOrElse(ModAttachments.ALL_MIGHT_STATE, null);
	}

	/** The safe limit of this state, percent. */
	public static float limit(AllMightState s) {
		return s.loanRole == AllMightState.LOAN_BORROWER ? AllMightConfig.BORROWED_LIMIT : s.mastery;
	}

	public static float limit(Player player) {
		AllMightState s = peek(player);
		return s == null ? AllMightConfig.MASTERY_START : limit(s);
	}

	/** The highest Output this holder can ever reach (Plus Ultra): 100%, or 45% on a borrowed One For All. */
	public static float outputCap(AllMightState s) {
		return s.loanRole == AllMightState.LOAN_BORROWER ? AllMightConfig.BORROWED_OUTPUT_CAP : 100f;
	}

	/** How high landed hits can charge Output right now: the limit, or the cap during Plus Ultra. */
	public static float outputCeiling(AllMightState s) {
		return s.plusUltra ? outputCap(s) : Math.min(limit(s), outputCap(s));
	}

	public static float limbStrain(Player player, Limb limb) {
		AllMightState s = peek(player);
		return s == null ? 0f : s.limb(limb);
	}

	public static boolean broken(Player player, Limb limb) {
		AllMightState s = peek(player);
		return s != null && s.hasPower && s.isBroken(limb);
	}

	/** 0 fine, 1 strained, 2 hurt, 3 broken. */
	public static int tier(AllMightState s, Limb limb) {
		if (s.isBroken(limb)) {
			return 3;
		}
		float v = s.limb(limb);
		return v >= AllMightConfig.LIMB_HURT ? 2 : v >= AllMightConfig.LIMB_STRAINED ? 1 : 0;
	}

	/** The damage factor of a limb's tier (a broken limb throws nothing: 0). */
	public static float tierFactor(int tier) {
		return switch (tier) {
			case 0 -> 1f;
			case 1 -> AllMightConfig.LIMB_STRAINED_FACTOR;
			case 2 -> AllMightConfig.LIMB_HURT_FACTOR;
			default -> 0f;
		};
	}

	/** The weakest of the move's limbs (1 for a move with no limb). */
	public static float limbFactor(AllMightState s, String abilityId) {
		float f = 1f;
		for (Limb l : limbsOf(abilityId)) {
			f = Math.min(f, tierFactor(tier(s, l)));
		}
		return f;
	}

	/** The first broken limb this move needs, or null. */
	public static @Nullable Limb brokenLimbFor(AllMightState s, String abilityId) {
		for (Limb l : limbsOf(abilityId)) {
			if (s.isBroken(l)) {
				return l;
			}
		}
		return null;
	}

	/** Client-safe: this key's move is locked by a broken limb (the HUD crosses it out). */
	public static boolean lockedByInjury(Player player, String abilityId) {
		AllMightState s = peek(player);
		return s != null && s.hasPower && brokenLimbFor(s, abilityId) != null;
	}

	// ---------------------------------------------------------------- casting a Smash

	/**
	 * A Smash is being thrown at {@code output} percent: the mastery gain, the strain past the limit (on its limb, with a little HP
	 * lost), and the damage multiplier it lands with -- the Output multiplier times its limb's factor, read BEFORE this cast strained
	 * it. The caller has already checked {@link #brokenLimbFor} (a broken limb refuses the move).
	 */
	public static float cast(ServerPlayer player, String abilityId, float output) {
		AllMightState s = AllMight.state(player);
		float mult = AllMight.multiplierAt(output) * limbFactor(s, abilityId);
		Limb[] limbs = limbsOf(abilityId);
		if (limbs.length == 0) {
			return mult;
		}
		float limit = limit(s);
		float over = output - limit;
		gainMastery(player, AllMightConfig.MASTERY_PER_SMASH + (over > 0.01f ? AllMightConfig.MASTERY_PER_OVER_SMASH : 0f));
		if (over > 0.01f) {
			float strain = weightOf(abilityId) * over * AllMightConfig.OVER_STRAIN_PER_POINT;
			float total = 0f;
			for (Limb l : limbs) {
				total += addLimbStrain(player, l, strain);
			}
			overLimitFx(player, limbs, total);
		}
		return mult;
	}

	private static void overLimitFx(ServerPlayer player, Limb[] limbs, float strain) {
		if (strain <= 0.01f) {
			return;
		}
		ServerLevel level = (ServerLevel) player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_HURT_SWEET_BERRY_BUSH, SoundSource.PLAYERS, 0.9f, 0.6f);
		Vec3 c = limbPos(player, limbs[0]);
		level.sendParticles(net.minecraft.core.particles.DustParticleOptions.REDSTONE, c.x, c.y, c.z, 6, 0.15, 0.2, 0.15, 0.0);
		level.sendParticles(ParticleTypes.CLOUD, c.x, c.y, c.z, 3, 0.1, 0.15, 0.1, 0.02);
		// the body pays: a little real damage, never the last heart
		float hp = strain / AllMightConfig.OVER_STRAIN_HP_DIVISOR;
		float room = player.getHealth() - 1f;
		if (hp > 0.25f && room > 0f && player.isAlive() && !player.getAbilities().invulnerable) {
			player.setHealth(player.getHealth() - Math.min(hp, room));
			player.hurtMarked = true;
			level.broadcastEntityEvent(player, (byte) 2); // the red hurt flash, without a knockback or a second damage pass
		}
	}

	/** Roughly where a limb is (shoulder-to-fist middle, or the knees). */
	static Vec3 limbPos(ServerPlayer player, Limb limb) {
		double h = Math.max(1.8, player.getBbHeight());
		Vec3 fwd = Vec3.directionFromRotation(0, player.yBodyRot);
		Vec3 side = new Vec3(-fwd.z, 0, fwd.x);
		return switch (limb) {
			case RIGHT_ARM -> player.position().add(side.scale(0.37 * h / 1.8)).add(0, h * 0.58, 0);
			case LEFT_ARM -> player.position().add(side.scale(-0.37 * h / 1.8)).add(0, h * 0.58, 0);
			case LEGS -> player.position().add(0, h * 0.25, 0);
		};
	}

	// ---------------------------------------------------------------- strain / healing / mastery

	/** Adds strain to one limb (x2 on a borrowed One For All); reaching 100 breaks it. Returns the strain actually added. */
	public static float addLimbStrain(ServerPlayer player, Limb limb, float amount) {
		AllMightState s = AllMight.state(player);
		if (!s.hasPower || amount <= 0f) {
			return 0f;
		}
		float scaled = s.loanRole == AllMightState.LOAN_BORROWER ? amount * AllMightConfig.BORROWED_STRAIN_MULTIPLIER : amount;
		AllMightState n = s.copy();
		float before = s.limb(limb);
		float after = Math.min(AllMightConfig.LIMB_BROKEN, before + scaled);
		n.limbs[limb.ordinal()] = after;
		boolean breaks = after >= AllMightConfig.LIMB_BROKEN - 1.0e-3f && !s.isBroken(limb);
		if (breaks) {
			n.broken |= limb.bit();
		}
		AllMight.save(player, n);
		if (breaks) {
			onBreak(player, limb, s.loanRole == AllMightState.LOAN_BORROWER);
		} else if (before < AllMightConfig.LIMB_HURT && after >= AllMightConfig.LIMB_HURT) {
			AllMight.say(player, "message.projecthero.all_might.limb_hurt." + limb.id, ChatFormatting.RED);
		}
		return after - before;
	}

	/** Heals one limb; a broken one mends (usable again) once it is down to {@code LIMB_MENDED}. */
	public static void healLimb(ServerPlayer player, Limb limb, float amount) {
		AllMightState s = AllMight.state(player);
		if (amount <= 0f || s.limb(limb) <= 0f) {
			return;
		}
		AllMightState n = s.copy();
		n.limbs[limb.ordinal()] = Math.max(0f, s.limb(limb) - amount);
		boolean mends = s.isBroken(limb) && n.limbs[limb.ordinal()] <= AllMightConfig.LIMB_MENDED;
		if (mends) {
			n.broken &= ~limb.bit();
		}
		AllMight.save(player, n);
		if (mends) {
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8f, 1.2f);
			player.displayClientMessage(Component.translatable("message.projecthero.all_might.limb_mended." + limb.id).withStyle(ChatFormatting.GREEN), true);
		}
	}

	/** Heals every limb by {@code amount}. */
	public static void healAll(ServerPlayer player, float amount) {
		for (Limb l : Limb.values()) {
			healLimb(player, l, amount);
		}
	}

	/** Sets a limb's strain outright (tests, the harness); 100 breaks it, below the mend line mends it. */
	public static void setLimb(ServerPlayer player, Limb limb, float value) {
		AllMightState n = AllMight.state(player).copy();
		n.limbs[limb.ordinal()] = Math.max(0f, Math.min(100f, value));
		if (value >= AllMightConfig.LIMB_BROKEN) {
			n.broken |= limb.bit();
		} else if (value <= AllMightConfig.LIMB_MENDED) {
			n.broken &= ~limb.bit();
		}
		AllMight.save(player, n);
	}

	private static void onBreak(ServerPlayer player, Limb limb, boolean borrower) {
		ServerLevel level = (ServerLevel) player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SKELETON_HURT, SoundSource.PLAYERS, 1.3f, 0.5f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 1.0f, 0.6f);
		Vec3 c = limbPos(player, limb);
		level.sendParticles(net.minecraft.core.particles.DustParticleOptions.REDSTONE, c.x, c.y, c.z, 14, 0.2, 0.25, 0.2, 0.0);
		AllMight.steam(level, player, 4);
		player.displayClientMessage(Component.translatable("message.projecthero.all_might.limb_broken." + limb.id)
				.withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
		if (limb != Limb.LEGS) {
			AllMightAbilities.cancelCharge(player, false); // a broken arm drops the United States of Smash charge
		}
		if (borrower) {
			AllMightPassOn.endLoanFromBorrower(player, AllMightPassOn.EndReason.STRAIN);
		}
	}

	/** Raises the safe limit (capped at 100); says so each time it crosses a 5% line. Borrowers do not train the lender's power. */
	public static void gainMastery(ServerPlayer player, float amount) {
		AllMightState s = AllMight.state(player);
		if (!s.hasPower || s.loanRole == AllMightState.LOAN_BORROWER || s.mastery >= AllMightConfig.MASTERY_MAX) {
			return;
		}
		setMastery(player, s.mastery + amount);
		float step = AllMightConfig.MASTERY_MILESTONE;
		int before = (int) Math.floor(s.mastery / step);
		int after = (int) Math.floor(AllMight.state(player).mastery / step);
		if (after > before) {
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.4f);
			player.displayClientMessage(Component.translatable("message.projecthero.all_might.limit_up", Math.round(after * step))
					.withStyle(ChatFormatting.GOLD), false);
		}
	}

	public static void setMastery(ServerPlayer player, float value) {
		AllMightState n = AllMight.state(player).copy();
		n.mastery = Math.max(0f, Math.min(AllMightConfig.MASTERY_MAX, value));
		AllMight.save(player, n);
	}

	// ---------------------------------------------------------------- the tick

	/**
	 * Every {@code STRAIN_STEP_TICKS}: Full Cowl past the limit strains every limb; otherwise the limbs heal (Base Form fastest,
	 * Regeneration and sleeping faster still, a broken limb slower). Eating heals at once. A broken limb's drawback is kept up.
	 */
	public static void tick(ServerPlayer player, long now) {
		Long prev = LAST_TICK.put(player.getUUID(), now);
		if (prev != null && prev == now) {
			return;
		}
		// food: any rise in the food level since the last look is a meal
		int food = player.getFoodData().getFoodLevel();
		Integer last = LAST_FOOD.put(player.getUUID(), food);
		if (last != null && food > last) {
			healAll(player, (food - last) * AllMightConfig.HEAL_PER_FOOD_POINT);
		}
		AllMightState s = AllMight.state(player);
		if (s.broken != 0) {
			keepBrokenEffects(player, s);
		}
		if (now % AllMightConfig.STRAIN_STEP_TICKS != 0L) {
			return;
		}
		float dt = AllMightConfig.STRAIN_STEP_TICKS / 20.0f;
		float over = s.fullPower ? AllMight.outputPct(player) - limit(s) : 0f;
		if (over > 0.01f) {
			float per = AllMightConfig.COWL_OVER_STRAIN_PER_SECOND * over / 100f * dt;
			for (Limb l : Limb.values()) {
				addLimbStrain(player, l, per);
			}
			return; // nothing heals past the limit
		}
		float rate = s.fullPower ? AllMightConfig.HEAL_POWER_FORM : AllMightConfig.HEAL_BASE_FORM;
		MobEffectInstance regen = player.getEffect(MobEffects.REGENERATION);
		if (regen != null) {
			rate += AllMightConfig.HEAL_REGENERATION_PER_LEVEL * (regen.getAmplifier() + 1);
		}
		if (player.isSleeping()) {
			rate += AllMightConfig.HEAL_SLEEPING;
		}
		for (Limb l : Limb.values()) {
			if (s.limb(l) > 0f) {
				healLimb(player, l, rate * dt * (s.isBroken(l) ? AllMightConfig.HEAL_BROKEN_FACTOR : 1f));
			}
		}
	}

	private static void keepBrokenEffects(ServerPlayer player, AllMightState s) {
		int arms = (s.isBroken(Limb.RIGHT_ARM) ? 1 : 0) + (s.isBroken(Limb.LEFT_ARM) ? 1 : 0);
		if (arms > 0) {
			AllMight.keepEffect(player, MobEffects.WEAKNESS, AllMightConfig.BROKEN_ARM_WEAKNESS_AMPLIFIER + arms - 1);
		}
		if (s.isBroken(Limb.LEGS)) {
			AllMight.keepEffect(player, MobEffects.MOVEMENT_SLOWDOWN, AllMightConfig.BROKEN_LEGS_SLOWNESS_AMPLIFIER);
		}
	}
}
