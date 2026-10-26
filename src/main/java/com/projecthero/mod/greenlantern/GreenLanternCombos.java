package com.projecthero.mod.greenlantern;

import java.util.EnumMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.network.GreenLanternCuePayload;

import org.joml.Vector3f;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1 (user-approved revamp): Construct Combos. Two of the ring's moves landed on the same creature within
 * {@link GreenLanternConfig#COMBO_WINDOW_TICKS} (3 s) set off a bonus.
 *
 * <p>Every hit site calls {@link #strike} (a move that can FINISH a combo -- it returns the damage multiplier for that
 * hit) or {@link #mark} (a move that only OPENS one -- held / ticking constructs refresh their mark every pulse). Marks
 * are server-side, per attacker -> target, expire after the window and are cleared on server stop
 * ({@link #clearSessionState}, via {@code GreenLanternAbilityManager}) and when the Lantern's transient state goes.
 *
 * <table>
 * <tr><th>Combo</th><th>Opener</th><th>Finisher</th><th>Bonus</th></tr>
 * <tr><td>Crush</td><td>Chain Snare</td><td>Anvil Drop</td><td>x2 anvil damage + a shockwave round the target</td></tr>
 * <tr><td>Slingshot</td><td>Giant Hand</td><td>Construct Fist</td><td>x1.5, the hand lets go and the target is launched</td></tr>
 * <tr><td>Overcharge</td><td>Ring Bolt x3</td><td>Continuous Beam</td><td>the first beam pulse hits x5</td></tr>
 * <tr><td>Detonation</td><td>Protective Dome push</td><td>Blast Wave</td><td>x2 + a hard-light burst</td></tr>
 * <tr><td>Shatter</td><td>Buzzsaw</td><td>War Hammer</td><td>x1.25 and the target's armour is broken for 8 s</td></tr>
 * <tr><td>Pin</td><td>Battering Ram</td><td>your Hard-Light Wall</td><td>slammed into the wall: +10 damage, 2 s stun</td></tr>
 * <tr><td>Bullseye</td><td>Bear Trap</td><td>Sniper Rifle</td><td>x2 shot</td></tr>
 * <tr><td>Home Run</td><td>Fly Swatter</td><td>Boxing Glove</td><td>x1.5 and knocked sky-high</td></tr>
 * </table>
 *
 * <p>A landed combo: a popup on the Lantern's HUD (the combo's name in green, {@code GreenLanternComboPopup}), a distinct
 * sound and a burst of green light at the target, {@link GreenLanternConfig#COMBO_XP_BONUS} Willpower XP and, now and
 * then, the ring calling it out ({@link GreenLanternRingVoice}).
 */
public final class GreenLanternCombos {
	/** The moves the combo table knows about. Appended only. */
	public enum Move {
		RING_BOLT, BEAM, BLAST_WAVE, FIST, HAMMER, GIANT_HAND, BUZZSAW, ANVIL, CHAINS, RAM, DOME, BEAR_TRAP, SNIPER,
		SWATTER, GLOVE
	}

	/** The combo table. Appended only (the ordinal is not saved anywhere, but keep it stable for the client popup). */
	public enum Combo {
		CRUSH(Move.CHAINS, Move.ANVIL, 1),
		SLINGSHOT(Move.GIANT_HAND, Move.FIST, 1),
		OVERCHARGE(Move.RING_BOLT, Move.BEAM, GreenLanternConfig.OVERCHARGE_BOLTS),
		DETONATION(Move.DOME, Move.BLAST_WAVE, 1),
		SHATTER(Move.BUZZSAW, Move.HAMMER, 1),
		/** Finished by the target hitting one of the Lantern's walls, not by a move -- see {@link #tick}. */
		PIN(Move.RAM, null, 1),
		BULLSEYE(Move.BEAR_TRAP, Move.SNIPER, 1),
		HOME_RUN(Move.SWATTER, Move.GLOVE, 1);

		public final Move opener;
		public final Move finisher;
		public final int openerCount;

		Combo(Move opener, Move finisher, int openerCount) {
			this.opener = opener;
			this.finisher = finisher;
			this.openerCount = openerCount;
		}

		public String id() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}

		public String nameKey() {
			return "projecthero.green_lantern.combo." + id();
		}

		public String descKey() {
			return nameKey() + ".desc";
		}

		public static Combo byId(int ordinal) {
			Combo[] all = values();
			return ordinal >= 0 && ordinal < all.length ? all[ordinal] : CRUSH;
		}
	}

	/** When a move last touched a target, and how many times in a row inside the window (Ring Bolt x3). */
	private static final class Mark {
		long tick;
		int count;
	}

	/** A rammed target flying through the air: pinned if it meets one of its rammer's walls before {@code until}. */
	private record PendingPin(UUID owner, ServerLevel level, int targetId, long until) {
	}

	/** owner -> target UUID -> move -> mark. */
	private static final Map<UUID, Map<UUID, EnumMap<Move, Mark>>> MARKS = new ConcurrentHashMap<>();
	private static final Map<UUID, PendingPin> PINS = new ConcurrentHashMap<>();
	/** target UUID -> game time its broken armour comes back. */
	private static final Map<UUID, Long> SHATTERED = new ConcurrentHashMap<>();
	private static final Map<UUID, ServerLevel> SHATTERED_LEVEL = new ConcurrentHashMap<>();
	/** Tests: the last combo each Lantern landed, and how many in all. */
	private static final Map<UUID, Combo> LAST = new ConcurrentHashMap<>();
	private static final Map<UUID, Integer> COUNT = new ConcurrentHashMap<>();

	private static final ResourceLocation SHATTER_ARMOR = ResourceLocation.fromNamespaceAndPath("projecthero", "gl_combo_shatter_armor");
	private static final ResourceLocation SHATTER_TOUGHNESS = ResourceLocation.fromNamespaceAndPath("projecthero", "gl_combo_shatter_toughness");

	private static final ParticleOptions BRIGHT = new DustParticleOptions(new Vector3f(0.55f, 1.0f, 0.62f), 2.2f);
	private static final ParticleOptions GREEN = new DustParticleOptions(new Vector3f(0.208f, 0.941f, 0.459f), 1.8f);

	private GreenLanternCombos() {
	}

	public static void clearSessionState() {
		MARKS.clear();
		PINS.clear();
		SHATTERED.clear();
		SHATTERED_LEVEL.clear();
		LAST.clear();
		COUNT.clear();
	}

	/** Death / logout / dimension change / power loss: this Lantern's open combos are gone. */
	public static void onCleanup(UUID owner) {
		MARKS.remove(owner);
		PINS.remove(owner);
	}

	// ---------------------------------------------------------------- hit sites

	/** An opener-only move touched {@code target} (refreshes the mark; never finishes a combo). */
	public static void mark(ServerPlayer owner, LivingEntity target, Move move) {
		if (owner == null || target == null || target == owner) {
			return;
		}
		long now = owner.level().getGameTime();
		EnumMap<Move, Mark> marks = MARKS.computeIfAbsent(owner.getUUID(), k -> new ConcurrentHashMap<>())
				.computeIfAbsent(target.getUUID(), k -> new EnumMap<>(Move.class));
		synchronized (marks) {
			Mark m = marks.get(move);
			if (m == null || now - m.tick > GreenLanternConfig.COMBO_WINDOW_TICKS) {
				m = new Mark();
				marks.put(move, m);
				m.count = 1;
			} else if (now != m.tick) {
				m.count++;
			}
			m.tick = now;
		}
	}

	/**
	 * {@code move} is about to hit {@code target}: if it finishes a combo whose opener is still fresh on that target, the
	 * combo goes off (popup, sound, burst, its side effects) and the returned multiplier applies to this hit; otherwise
	 * 1. Either way the move is recorded as a possible opener.
	 */
	public static float strike(ServerPlayer owner, LivingEntity target, Move move) {
		if (owner == null || target == null || target == owner || !target.isAlive()) {
			return 1f;
		}
		Combo landed = null;
		Map<UUID, EnumMap<Move, Mark>> byTarget = MARKS.get(owner.getUUID());
		EnumMap<Move, Mark> marks = byTarget == null ? null : byTarget.get(target.getUUID());
		long now = owner.level().getGameTime();
		if (marks != null) {
			synchronized (marks) {
				for (Combo c : Combo.values()) {
					if (c.finisher != move) {
						continue;
					}
					Mark m = marks.get(c.opener);
					if (m != null && now - m.tick <= GreenLanternConfig.COMBO_WINDOW_TICKS && m.count >= c.openerCount) {
						marks.remove(c.opener); // consumed: one opener, one combo
						landed = c;
						break;
					}
				}
			}
		}
		mark(owner, target, move);
		if (landed == null) {
			return 1f;
		}
		return trigger(owner, target, landed);
	}

	/** The Battering Ram struck {@code target}: it is an opener for Pin while it flies (checked in {@link #tick}). */
	public static void rammed(ServerPlayer owner, LivingEntity target) {
		mark(owner, target, Move.RAM);
		PINS.put(owner.getUUID(), new PendingPin(owner.getUUID(), owner.serverLevel(), target.getId(),
				owner.level().getGameTime() + GreenLanternConfig.PIN_WINDOW_TICKS));
	}

	// ---------------------------------------------------------------- the bonuses

	private static float trigger(ServerPlayer owner, LivingEntity target, Combo combo) {
		ServerLevel level = owner.serverLevel();
		boolean boss = com.projecthero.mod.titanshifter.TitanCombat.isBoss(target);
		float power = GreenLanternRanks.power(owner);
		float mult = 1f;
		Vec3 at = target.position().add(0, target.getBbHeight() * 0.5, 0);
		switch (combo) {
			case CRUSH -> {
				mult = GreenLanternConfig.CRUSH_DAMAGE_MULT;
				for (LivingEntity o : AbilityHelpers.living(level, target.position(), GreenLanternConfig.CRUSH_SHOCKWAVE_RADIUS,
						x -> x != target && com.projecthero.mod.combat.HeroTargets.canHarm(owner, x))) {
					o.invulnerableTime = 0;
					AbilityHelpers.hurt(owner, o, GreenLanternConfig.CRUSH_SHOCKWAVE_DAMAGE * power);
					AbilityHelpers.knockbackFrom(o, target.position(), 1.2);
				}
				ring(level, target.position().add(0, 0.15, 0), GreenLanternConfig.CRUSH_SHOCKWAVE_RADIUS);
				level.sendParticles(ParticleTypes.EXPLOSION, target.getX(), target.getY() + 0.3, target.getZ(), 3, 1.0, 0.1, 1.0, 0);
			}
			case SLINGSHOT -> {
				mult = GreenLanternConfig.SLINGSHOT_DAMAGE_MULT;
				GreenLanternConstructAttacks.letGoOf(owner, target);
				if (!boss) {
					launch(target, owner.getLookAngle().scale(GreenLanternConfig.SLINGSHOT_LAUNCH_SPEED).add(0, 0.55, 0));
				}
			}
			case OVERCHARGE -> {
				mult = GreenLanternConfig.OVERCHARGE_BEAM_MULT;
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 30, 0.4, 0.5, 0.4, 0.3);
			}
			case DETONATION -> {
				mult = GreenLanternConfig.DETONATION_DAMAGE_MULT;
				for (LivingEntity o : AbilityHelpers.living(level, at, GreenLanternConfig.DETONATION_RADIUS,
						x -> x != target && com.projecthero.mod.combat.HeroTargets.canHarm(owner, x))) {
					o.invulnerableTime = 0;
					AbilityHelpers.hurt(owner, o, GreenLanternConfig.DETONATION_SPLASH_DAMAGE * power);
					AbilityHelpers.knockbackFrom(o, at, 1.4);
				}
				level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 1, 0, 0, 0, 0);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.9f, 1.4f);
			}
			case SHATTER -> {
				mult = GreenLanternConfig.SHATTER_DAMAGE_MULT;
				shatter(target, level);
			}
			case PIN -> {
				target.invulnerableTime = 0;
				AbilityHelpers.hurtBurst(owner, target, GreenLanternConfig.PIN_DAMAGE * power);
				AbilityHelpers.applyControl(target, MobEffects.MOVEMENT_SLOWDOWN, GreenLanternConfig.PIN_STUN_TICKS, 9);
				AbilityHelpers.applyControl(target, MobEffects.WEAKNESS, GreenLanternConfig.PIN_STUN_TICKS, 1);
				target.setDeltaMovement(Vec3.ZERO);
				target.hurtMarked = true;
				level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.9f, 0.7f);
			}
			case BULLSEYE -> mult = GreenLanternConfig.BULLSEYE_DAMAGE_MULT;
			case HOME_RUN -> {
				mult = GreenLanternConfig.HOME_RUN_DAMAGE_MULT;
				if (!boss) {
					Vec3 flat = owner.getLookAngle().multiply(1, 0, 1);
					flat = flat.lengthSqr() < 1.0e-4 ? Vec3.ZERO : flat.normalize();
					launch(target, flat.scale(GreenLanternConfig.HOME_RUN_LAUNCH_SPEED * 0.6).add(0, GreenLanternConfig.HOME_RUN_LAUNCH_SPEED * 0.75, 0));
				}
			}
		}
		// the shared flourish: a burst of light, a distinct chord, the popup, XP, now and then the ring's callout
		level.sendParticles(BRIGHT, at.x, at.y, at.z, 46, 0.6, 0.7, 0.6, 0.25);
		level.sendParticles(GREEN, at.x, at.y, at.z, 30, 1.0, 0.8, 1.0, 0.05);
		level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 18, 0.3, 0.4, 0.3, 0.2);
		level.sendParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 1, 0, 0, 0, 0);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 1.2f, 0.6f);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0f, 1.9f);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.FIREWORK_ROCKET_TWINKLE, SoundSource.PLAYERS, 0.8f, 1.2f);
		LAST.put(owner.getUUID(), combo);
		COUNT.merge(owner.getUUID(), 1, Integer::sum);
		GreenLanternRanks.award(owner, GreenLanternConfig.COMBO_XP_BONUS);
		popup(owner, combo);
		GreenLanternRingVoice.combo(owner, combo);
		return mult;
	}

	private static void popup(ServerPlayer owner, Combo combo) {
		if (ServerPlayNetworking.canSend(owner, GreenLanternCuePayload.TYPE)) {
			ServerPlayNetworking.send(owner, new GreenLanternCuePayload(GreenLanternCuePayload.COMBO, combo.id(), ""));
		} else {
			owner.displayClientMessage(Component.translatable(combo.nameKey()).withStyle(net.minecraft.ChatFormatting.GREEN,
					net.minecraft.ChatFormatting.BOLD), true);
		}
	}

	private static void launch(LivingEntity target, Vec3 v) {
		target.setDeltaMovement(v);
		target.hurtMarked = true;
		target.resetFallDistance();
		if (target instanceof ServerPlayer sp) {
			sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(sp));
		}
	}

	private static void ring(ServerLevel level, Vec3 c, double r) {
		for (int ring = 1; ring <= 2; ring++) {
			double rr = r * ring / 2.0;
			int points = 18 * ring;
			for (int i = 0; i < points; i++) {
				double a = i * Math.PI * 2 / points;
				level.sendParticles(GREEN, c.x + Math.cos(a) * rr, c.y, c.z + Math.sin(a) * rr, 1, 0, 0.05, 0, 0);
			}
		}
	}

	/** Shatter: armour and toughness down to nothing (transient modifiers -- never saved) until {@link #tick} restores them. */
	private static void shatter(LivingEntity target, ServerLevel level) {
		AttributeInstance armor = target.getAttribute(Attributes.ARMOR);
		if (armor != null && !armor.hasModifier(SHATTER_ARMOR)) {
			armor.addTransientModifier(new AttributeModifier(SHATTER_ARMOR, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
		AttributeInstance tough = target.getAttribute(Attributes.ARMOR_TOUGHNESS);
		if (tough != null && !tough.hasModifier(SHATTER_TOUGHNESS)) {
			tough.addTransientModifier(new AttributeModifier(SHATTER_TOUGHNESS, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
		SHATTERED.put(target.getUUID(), level.getGameTime() + GreenLanternConfig.SHATTER_TICKS);
		SHATTERED_LEVEL.put(target.getUUID(), level);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.SHIELD_BREAK, SoundSource.PLAYERS, 1.0f, 0.7f);
	}

	private static void unshatter(LivingEntity target) {
		AttributeInstance armor = target.getAttribute(Attributes.ARMOR);
		if (armor != null) {
			armor.removeModifier(SHATTER_ARMOR);
		}
		AttributeInstance tough = target.getAttribute(Attributes.ARMOR_TOUGHNESS);
		if (tough != null) {
			tough.removeModifier(SHATTER_TOUGHNESS);
		}
	}

	/** Whether {@code target}'s armour is currently broken by a Shatter combo. */
	public static boolean isShattered(LivingEntity target) {
		return SHATTERED.containsKey(target.getUUID());
	}

	// ---------------------------------------------------------------- tick

	/** Once a server tick (from {@code GreenLanternConstructs#tick}): Pin checks, Shatter expiry, stale marks. */
	public static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		if (!PINS.isEmpty()) {
			for (Iterator<Map.Entry<UUID, PendingPin>> it = PINS.entrySet().iterator(); it.hasNext();) {
				PendingPin pin = it.next().getValue();
				ServerPlayer owner = server.getPlayerList().getPlayer(pin.owner());
				Entity e = pin.level().getEntity(pin.targetId());
				if (owner == null || owner.level() != pin.level() || !(e instanceof LivingEntity target) || !target.isAlive()
						|| pin.level().getGameTime() > pin.until()) {
					it.remove();
					continue;
				}
				if (touchesOwnWall(pin.level(), target, pin.owner())) {
					it.remove();
					Map<UUID, EnumMap<Move, Mark>> byTarget = MARKS.get(pin.owner());
					if (byTarget != null) {
						EnumMap<Move, Mark> marks = byTarget.get(target.getUUID());
						if (marks != null) {
							synchronized (marks) {
								marks.remove(Move.RAM);
							}
						}
					}
					trigger(owner, target, Combo.PIN);
				}
			}
		}
		if (!SHATTERED.isEmpty()) {
			for (Iterator<Map.Entry<UUID, Long>> it = SHATTERED.entrySet().iterator(); it.hasNext();) {
				Map.Entry<UUID, Long> en = it.next();
				ServerLevel level = SHATTERED_LEVEL.get(en.getKey());
				Entity e = level == null ? null : level.getEntity(en.getKey());
				boolean over = level == null || level.getGameTime() >= en.getValue();
				if (e instanceof LivingEntity le) {
					if (over || !le.isAlive()) {
						unshatter(le);
					} else if (level.getGameTime() % 5 == 0) {
						level.sendParticles(ParticleTypes.CRIT, le.getX(), le.getY() + le.getBbHeight() * 0.6, le.getZ(), 2, 0.3, 0.3, 0.3, 0.02);
					}
				}
				if (over || e == null || !e.isAlive()) {
					it.remove();
					SHATTERED_LEVEL.remove(en.getKey());
				}
			}
		}
		if (now % 40 == 0) {
			for (Iterator<Map.Entry<UUID, Map<UUID, EnumMap<Move, Mark>>>> it = MARKS.entrySet().iterator(); it.hasNext();) {
				Map<UUID, EnumMap<Move, Mark>> byTarget = it.next().getValue();
				for (Iterator<Map.Entry<UUID, EnumMap<Move, Mark>>> jt = byTarget.entrySet().iterator(); jt.hasNext();) {
					EnumMap<Move, Mark> marks = jt.next().getValue();
					synchronized (marks) {
						marks.values().removeIf(m -> now - m.tick > GreenLanternConfig.COMBO_WINDOW_TICKS * 2L);
						if (marks.isEmpty()) {
							jt.remove();
						}
					}
				}
				if (byTarget.isEmpty()) {
					it.remove();
				}
			}
		}
	}

	/** Whether any block {@code target}'s body touches (padded a little) is a cell of one of {@code owner}'s Hard-Light Walls. */
	private static boolean touchesOwnWall(ServerLevel level, LivingEntity target, UUID owner) {
		var box = target.getBoundingBox().inflate(0.45);
		for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ),
				BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
			if (owner.equals(com.projecthero.mod.greenlantern.construct.GreenLanternConstructs.wallOwnerAt(level, pos))) {
				return true;
			}
		}
		return false;
	}

	// ---------------------------------------------------------------- tests

	public static Combo lastCombo(ServerPlayer owner) {
		return LAST.get(owner.getUUID());
	}

	public static int comboCount(ServerPlayer owner) {
		return COUNT.getOrDefault(owner.getUUID(), 0);
	}
}
