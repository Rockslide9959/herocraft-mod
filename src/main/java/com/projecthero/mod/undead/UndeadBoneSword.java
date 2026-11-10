package com.projecthero.mod.undead;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.undead.data.UndeadState;
import com.projecthero.mod.undead.entity.UndeadCloneEntity;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * v0.17.3 hold V (user: "Bone sword, make it look like a bone katana and give him his sword skill moves that he does in the anime
 * and manga when in this mode please"): Kurikara, the bone katana. Hold V and he wrenches his off arm loose; it tears off and
 * bursts in his grip, leaving a long, slender, slightly curved bone blade with a bone guard and a wrapped grip. The arm does not
 * grow back while it is out ({@link UndeadState#boneSwordArm}, checked by {@code Undead.scheduleRegrow}); putting it away (V or
 * Shift+V, {@link UndeadConfig#BONE_SWORD_MAX}, losing the hand, death) bursts it into blood and bone and the arm regrows.
 *
 * <p>While it is out his keys are the katana's sword skills ({@link #idsOf}; X stays his own):
 * <pre>
 *   R      Crimson Crescent Moon (Kurenai Mikazuki): a red crescent slash thrown off the blade
 *   G      Great Deep-Crimson Crescent Moon: two-handed from overhead, a huge crescent that tears the ground's length
 *   Z      Crimson Flash: an iaido draw -- he blurs forward through everything in his path; the cuts open behind him and he
 *          drinks the blood they draw
 *   C      Blood Whirl: two full turns of a blood-coated blade, all round him
 *   V / Shift+V   put it away
 * </pre>
 * Left click is a sword: {@link UndeadConfig#BONE_SWORD_DAMAGE}, a sweep and a longer reach.
 *
 * <p>Also the hub for the rest of the v0.17.3 kit's server side (its tick, session state and lifecycle):
 * {@link UndeadVortex} (Z), {@link UndeadMeteor} (Shift+Z), {@link UndeadRibGuard} (Shift+V), {@link UndeadShoulderSword}.
 */
public final class UndeadBoneSword {
	public static final String BONE_SWORD = "bone_sword";
	/** V while the sword is out (the HUD and move list name). */
	public static final String AWAY = "bone_sword_away";
	public static final String K_CRESCENT = "katana_crescent_moon";
	public static final String K_GREAT = "katana_great_crescent_moon";
	public static final String K_FLASH = "crimson_flash";
	public static final String K_WHIRL = "blood_whirl";

	/** A fleck of bone (the ribs being hit): small, so nothing big pops up in front of the camera. */
	public static final DustParticleOptions BONE_CHIP = new DustParticleOptions(new Vector3f(0.90f, 0.87f, 0.78f), 0.45f);
	/** A small fleck of blood ({@link Undead#BLOOD} is too big that close to the camera). */
	public static final DustParticleOptions BLOOD_FLECK = new DustParticleOptions(new Vector3f(0.72f, 0.03f, 0.06f), 0.5f);
	/** The thrown sword (old Impale Throw pieces still flying from a save) pins this long. */
	public static final int IMPALE_PIN_TICKS = 40;

	private static final ResourceLocation DAMAGE_ID = PowerToggles.id("undead_bone_sword_damage");
	private static final ResourceLocation REACH_ID = PowerToggles.id("undead_bone_sword_reach");
	private static final ResourceLocation SPEED_ID = PowerToggles.id("undead_bone_sword_speed");

	/** A crescent in flight (R small, G great): where from, which way, when it sets off, its size and its hit, who it cut. */
	private record Wave(Vec3 from, Vec3 dir, long start, int delay, double speed, double range, double width, double height, float damage,
			double knock, double lift, Set<Integer> hit) {
	}

	/** Crimson Flash: when, from, to, who it passed through, whether the cuts have opened. */
	private static final class Flash {
		final long start;
		final Vec3 from;
		final Vec3 to;
		final Set<Integer> passed = new HashSet<>();
		boolean opened;

		Flash(long start, Vec3 from, Vec3 to) {
			this.start = start;
			this.from = from;
			this.to = to;
		}
	}

	private static final Map<UUID, List<Wave>> WAVES = new ConcurrentHashMap<>();
	private static final Map<UUID, Flash> FLASHES = new ConcurrentHashMap<>();
	/** Blood Whirl: player -> when it started. */
	private static final Map<UUID, Long> WHIRLS = new ConcurrentHashMap<>();

	private UndeadBoneSword() {
	}

	public static void initialize() {
		// left click with the sword out: a sword's sweep round the target, and a wet slash
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (!world.isClientSide() && player instanceof ServerPlayer sp && entity instanceof LivingEntity target && held(sp)
					&& !UndeadMod.handGone(sp, hand)) {
				melee(sp, target, sp.getAttackStrengthScale(0.5f));
			}
			return InteractionResult.PASS;
		});
		UndeadKit.initialize(); // v0.17.3: the rest of the kit (Z, Shift+Z, Shift+V, the shoulder sword)
	}

	static void clearSessionState() {
		WAVES.clear();
		FLASHES.clear();
		WHIRLS.clear();
		UndeadKit.clearSessionState();
	}

	static void forget(UUID id) {
		forgetSword(id);
		UndeadKit.forget(id);
	}

	private static void forgetSword(UUID id) {
		WAVES.remove(id);
		FLASHES.remove(id);
		WHIRLS.remove(id);
	}

	/** Death, a world change, leaving, the shatter: the sword bursts (the arm grows back) and every running kit move ends. */
	static void clear(ServerPlayer p) {
		forgetSword(p.getUUID());
		UndeadKit.clear(p);
		UndeadState s = Undead.peek(p);
		if (s != null && s.boneSword()) {
			putAway(p, true, false);
		}
	}

	// ---------------------------------------------------------------- state

	/** The katana is out (client-safe). */
	public static boolean held(Player p) {
		UndeadState s = Undead.peek(p);
		return s != null && s.hasPower && s.boneSword();
	}

	/** The hand holding the sword: the arm that was not torn off. */
	public static int hand(UndeadState s) {
		return Undead.otherArm(s.boneSwordArm());
	}

	/** From {@code Undead.reconcile}: the sword's damage, reach and speed while it is out (and the shoulder sword's). */
	static void attributes(ServerPlayer p, UndeadState s) {
		if (s != null && s.hasPower && s.boneSword()) {
			PowerToggles.modifier(p, Attributes.ATTACK_DAMAGE, DAMAGE_ID, UndeadConfig.BONE_SWORD_DAMAGE - 1.0 - UndeadConfig.BLOODY_ARTS_MELEE,
					AttributeModifier.Operation.ADD_VALUE);
			PowerToggles.modifier(p, Attributes.ENTITY_INTERACTION_RANGE, REACH_ID, UndeadConfig.BONE_SWORD_REACH_BONUS, AttributeModifier.Operation.ADD_VALUE);
			PowerToggles.modifier(p, Attributes.ATTACK_SPEED, SPEED_ID, UndeadConfig.BONE_SWORD_ATTACK_SPEED - 4.0, AttributeModifier.Operation.ADD_VALUE);
		} else {
			PowerToggles.clearModifier(p, Attributes.ATTACK_DAMAGE, DAMAGE_ID);
			PowerToggles.clearModifier(p, Attributes.ENTITY_INTERACTION_RANGE, REACH_ID);
			PowerToggles.clearModifier(p, Attributes.ATTACK_SPEED, SPEED_ID);
		}
		UndeadShoulderSword.attributes(p);
	}

	// ---------------------------------------------------------------- keys

	/** The move ids on a slot while the sword is out (plain, Shift); null when it is not out or the slot stays his own (X). */
	public static String[] idsOf(Player player, AbilitySlot slot) {
		if (!held(player)) {
			return null;
		}
		return switch (slot) {
			case SLOT_1 -> new String[] { K_CRESCENT };
			case SLOT_2 -> new String[] { K_GREAT };
			case SLOT_4 -> new String[] { K_FLASH };
			case SLOT_5 -> new String[] { AWAY, AWAY };
			case SLOT_6 -> new String[] { K_WHIRL };
			default -> null;
		};
	}

	/** The HUD's full cooldown for every move of the v0.17.3 kit (Z, Shift+Z, V, Shift+V, the katana); -1 for anything else. */
	public static int maxCooldown(String id) {
		return switch (id) {
			case BONE_SWORD -> UndeadConfig.BONE_SWORD_COOLDOWN;
			case K_CRESCENT -> UndeadKitConfig.K_CRESCENT_COOLDOWN;
			case K_GREAT -> UndeadKitConfig.K_GREAT_COOLDOWN;
			case K_FLASH -> UndeadKitConfig.K_FLASH_COOLDOWN;
			case K_WHIRL -> UndeadKitConfig.K_WHIRL_COOLDOWN;
			case UndeadAbilities.VORTEX -> UndeadKitConfig.VORTEX_COOLDOWN;
			case UndeadMeteor.ID -> UndeadKitConfig.METEOR_COOLDOWN;
			case UndeadRibGuard.ID -> UndeadKitConfig.RIB_COOLDOWN;
			default -> -1;
		};
	}

	/** A key while the sword is out; true if the sword kit took it (X is left to his own kit). V or Shift+V puts it away. */
	static boolean handle(ServerPlayer p, AbilitySlot slot, boolean pressed, boolean shift) {
		switch (slot) {
			case SLOT_1 -> {
				if (pressed) {
					crescent(p);
				}
				return true;
			}
			case SLOT_2 -> {
				if (pressed) {
					greatCrescent(p);
				}
				return true;
			}
			case SLOT_4 -> {
				if (pressed) {
					flash(p);
				}
				return true;
			}
			case SLOT_5 -> {
				if (pressed) {
					putAway(p, true, true);
				}
				return true;
			}
			case SLOT_6 -> {
				if (pressed) {
					whirl(p);
				}
				return true;
			}
			default -> {
				return false;
			}
		}
	}

	// ---------------------------------------------------------------- hold V: drawing; V again: putting away

	/** Draws the sword at once (no struggle), or puts it away (commands, tests). */
	public static void toggle(ServerPlayer p) {
		if (held(p)) {
			putAway(p, true, true);
		} else {
			draw(p);
		}
	}

	/**
	 * Whether he can draw the sword now: not cooling down, one arm to tear off and one to hold it, a body. If not and {@code say},
	 * a line and a dull click say why.
	 */
	public static boolean canDraw(ServerPlayer p, boolean say) {
		if (!Undead.canAct(p)) {
			return false;
		}
		int cd = Undead.cooldownRemaining(p, BONE_SWORD);
		if (cd > 0) {
			if (say) {
				Undead.say(p, "message.projecthero.undead.cooldown", ChatFormatting.GRAY, Component.translatable("projecthero.undead.ability." + BONE_SWORD),
						String.format(java.util.Locale.ROOT, "%.1f", cd / 20.0f));
			}
			return false;
		}
		UndeadState s = Undead.state(p);
		long now = p.level().getGameTime();
		int hand = Undead.mainArm(p);
		int arm = Undead.otherArm(hand);
		if (!s.armReady(hand, now) || !s.armReady(arm, now) || s.broken(UndeadState.BODY)) {
			if (say) {
				Undead.say(p, "message.projecthero.undead.no_spare_arm", ChatFormatting.GRAY);
				sound(p, SoundEvents.DISPENSER_FAIL, 0.6f, 1.4f);
			}
			return false;
		}
		return true;
	}

	/**
	 * Hold V, past the tap: his sword hand reaches over, grabs the off arm at the shoulder and starts wrenching at it
	 * ({@link UndeadState#ANIM_SWORD_STRUGGLE}). False (having said why) if he can't draw the sword.
	 */
	public static boolean startStruggle(ServerPlayer p) {
		if (!canDraw(p, true)) {
			return false;
		}
		UndeadAbilities.cancelPunch(p);
		long now = p.level().getGameTime();
		UndeadState n = Undead.state(p).copy();
		n.animId = UndeadState.ANIM_SWORD_STRUGGLE;
		n.animStart = now;
		Undead.save(p, n);
		UndeadSfx.punchStretch(level(p), UndeadParts.centre(p, Undead.otherArm(Undead.mainArm(p))));
		return true;
	}

	/** Every tick of the struggle ({@code held}: ticks V has been down): the sinew stretching and a trickle at the shoulder. */
	public static void struggling(ServerPlayer p, long held) {
		if (!canDraw(p, false)) {
			cancelStruggle(p); // lost an arm / the body meanwhile
			return;
		}
		long t = held - UndeadConfig.BONE_SWORD_TAP;
		Vec3 at = UndeadParts.centre(p, Undead.otherArm(Undead.mainArm(p))).add(0, 0.25, 0);
		// the wrenches come quicker as it gives
		if (t == 9 || t == 18 || t == 25 || t == 31) {
			UndeadSfx.punchStretch(level(p), at);
		}
		if (t == 22) {
			sound(p, SoundEvents.SKELETON_HURT, 0.35f, 0.55f); // a crack at the shoulder
		}
		if (t > 14 && t % 3 == 0) {
			level(p).sendParticles(Undead.BLOOD, at.x, at.y, at.z, 1, 0.06, 0.06, 0.06, 0.0);
		}
	}

	/** V let go before the arm came off: he lets go of it. */
	public static void cancelStruggle(ServerPlayer p) {
		UndeadState s = Undead.state(p);
		if (s.animId != UndeadState.ANIM_SWORD_STRUGGLE) {
			return;
		}
		UndeadState n = s.copy();
		n.animId = UndeadState.ANIM_NONE;
		Undead.save(p, n);
	}

	/**
	 * Tears off the off arm and holds it as the sword (the end of the hold). The arm comes away in his hand; he grips it and
	 * {@link UndeadConfig#BONE_SWORD_REVEAL} later it bursts round the blade ({@link #tick}). False (with a line) if he can't.
	 */
	public static boolean draw(ServerPlayer p) {
		if (!canDraw(p, true)) {
			return false;
		}
		long now = p.level().getGameTime();
		int arm = Undead.otherArm(Undead.mainArm(p));
		Vec3 at = UndeadParts.centre(p, arm);
		UndeadAbilities.cancelPunch(p);
		Undead.breakPart(p, arm, UndeadState.KIND_LAUNCHED); // no pixel burst: the arm is in his hand
		UndeadState n = Undead.state(p).copy();
		n.setBoneSword(arm, now);
		n.animId = UndeadState.ANIM_SWORD_RIP;
		n.animStart = now;
		Undead.save(p, n);
		Undead.reconcile(p);
		ServerLevel level = level(p);
		UndeadSfx.partBreak(level, at);
		UndeadSfx.bloodBurst(level, at, 0.6f);
		sound(p, SoundEvents.SKELETON_HURT, 0.5f, 0.6f);
		return true;
	}

	/** {@link UndeadConfig#BONE_SWORD_REVEAL} after the rip: the arm bursts in his grip and the katana is left. */
	private static void reveal(ServerPlayer p, UndeadState s) {
		ServerLevel level = level(p);
		Vec3 at = UndeadAbilities.handOrigin(p, hand(s)).add(p.getLookAngle().scale(0.35));
		UndeadSfx.bloodBurst(level, at, 0.8f);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.BONE_BLOCK_BREAK, SoundSource.PLAYERS, 0.8f, 0.75f);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.SKELETON_HURT, SoundSource.PLAYERS, 0.4f, 0.5f);
	}

	/**
	 * Puts the sword away: it crumbles into blood and bone bits ({@code burst}) and the torn-off arm grows back as usual (bone,
	 * muscle, skin). {@code cooldown}: the {@link UndeadConfig#BONE_SWORD_COOLDOWN} before it can be drawn again.
	 */
	public static void putAway(ServerPlayer p, boolean burst, boolean cooldown) {
		UndeadState s = Undead.state(p);
		if (!s.boneSword()) {
			return;
		}
		long now = p.level().getGameTime();
		int hand = hand(s);
		UndeadState n = s.copy();
		n.setBoneSword(-1, 0L);
		if (burst) {
			n.animId = UndeadState.ANIM_SWORD_AWAY;
			n.animStart = now;
		}
		if (cooldown) {
			n.abilityReadyAt.put(BONE_SWORD, now + UndeadConfig.BONE_SWORD_COOLDOWN);
		}
		Undead.save(p, n);
		forgetSword(p.getUUID());
		Undead.reconcile(p);
		if (burst) {
			ServerLevel level = level(p);
			Vec3 at = UndeadAbilities.handOrigin(p, hand).add(p.getLookAngle().scale(0.4));
			UndeadSfx.bloodBurst(level, at, 0.5f);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.BONE_BLOCK_BREAK, SoundSource.PLAYERS, 0.7f, 1.2f);
		}
	}

	// ---------------------------------------------------------------- left click

	/** A melee hit with the katana (the damage itself is vanilla's, from {@link #attributes}): the sweep, blood and sound. */
	public static void melee(ServerPlayer p, LivingEntity target, float strength) {
		ServerLevel level = level(p);
		Vec3 c = target.position().add(0, target.getBbHeight() * 0.5, 0);
		level.sendParticles(Undead.BLOOD, c.x, c.y, c.z, 5, 0.2, 0.25, 0.2, 0.0);
		UndeadSfx.splat(level, c);
		if (strength < 0.9f) {
			return;
		}
		sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.6f, 1.05f);
		Vec3 f = flatLook(p);
		level.sendParticles(ParticleTypes.SWEEP_ATTACK, p.getX() + f.x, p.getY() + p.getBbHeight() * 0.5, p.getZ() + f.z, 1, 0, 0, 0, 0);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(1.0, 0.25, 1.0),
				e -> e != p && e != target && !(e instanceof UndeadCloneEntity) && HeroTargets.canHarm(p, e)
						&& p.distanceToSqr(e) < Math.pow(p.entityInteractionRange() + 0.5, 2))) {
			UndeadAbilities.strike(p, e, p.position(), UndeadConfig.BONE_SWORD_SWEEP_DAMAGE, 0.4, 0.05);
		}
	}

	// ---------------------------------------------------------------- the sword skills

	private static boolean begin(ServerPlayer p, String id) {
		if (!held(p) || !Undead.canAct(p)) {
			return false;
		}
		int cd = Undead.cooldownRemaining(p, id);
		if (cd > 0) {
			if (cd > 10) {
				Undead.say(p, "message.projecthero.undead.cooldown", ChatFormatting.GRAY, Component.translatable("projecthero.undead.ability." + id),
						String.format(java.util.Locale.ROOT, "%.1f", cd / 20.0f));
			}
			return false;
		}
		return true;
	}

	private static void cooldownAnim(ServerPlayer p, String id, int ticks, int anim) {
		long now = p.level().getGameTime();
		UndeadState n = Undead.state(p).copy();
		n.abilityReadyAt.put(id, now + ticks);
		n.animId = anim;
		n.animStart = now;
		Undead.save(p, n);
	}

	/** R: Crimson Crescent Moon -- a quick draw-cut that throws a red crescent {@link UndeadKitConfig#K_CRESCENT_RANGE} blocks. */
	public static void crescent(ServerPlayer p) {
		if (!begin(p, K_CRESCENT)) {
			return;
		}
		cooldownAnim(p, K_CRESCENT, UndeadKitConfig.K_CRESCENT_COOLDOWN, UndeadKitConfig.ANIM_K_CRESCENT);
		wave(p, new Wave(p.position().add(0, 1.0, 0), lungeDir(p), p.level().getGameTime(), 2, UndeadKitConfig.K_CRESCENT_SPEED,
				UndeadKitConfig.K_CRESCENT_RANGE, UndeadKitConfig.K_CRESCENT_WIDTH, 0.9, UndeadKitConfig.K_CRESCENT_DAMAGE, 0.7, 0.15, new HashSet<>()));
		sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.55f, 0.75f);
		UndeadSfx.swordSwing(level(p), p.getEyePosition(), 0.6f);
	}

	/** G: Great Deep-Crimson Crescent Moon -- overhead, two-handed, and a huge crescent tears {@link UndeadKitConfig#K_GREAT_RANGE} blocks out. */
	public static void greatCrescent(ServerPlayer p) {
		if (!begin(p, K_GREAT)) {
			return;
		}
		cooldownAnim(p, K_GREAT, UndeadKitConfig.K_GREAT_COOLDOWN, UndeadKitConfig.ANIM_K_GREAT);
		wave(p, new Wave(p.position().add(0, 0.2, 0), flatLook(p), p.level().getGameTime(), UndeadKitConfig.K_GREAT_WINDUP, UndeadKitConfig.K_GREAT_SPEED,
				UndeadKitConfig.K_GREAT_RANGE, UndeadKitConfig.K_GREAT_WIDTH, UndeadKitConfig.K_GREAT_HEIGHT, UndeadKitConfig.K_GREAT_DAMAGE, 1.5, 0.5,
				new HashSet<>()));
		sound(p, SoundEvents.ARMOR_EQUIP_LEATHER.value(), 0.6f, 0.6f); // the grip settling for the overhead cut
	}

	private static void wave(ServerPlayer p, Wave w) {
		WAVES.computeIfAbsent(p.getUUID(), k -> new ArrayList<>()).add(w);
	}

	/** One crescent's tick: its front runs on, cutting what it reaches once each. */
	private static boolean tickWave(ServerPlayer p, Wave w, long now) {
		long age = now - w.start() - w.delay();
		ServerLevel level = level(p);
		if (age == 0 && w.height() > 2.0) {
			// the great one: the blade comes down
			level.playSound(null, p.getX(), p.getY() + 1, p.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8f, 0.5f);
			level.playSound(null, p.getX(), p.getY() + 1, p.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.25f, 1.2f);
			Vec3 f = w.from().add(w.dir().scale(1.5));
			level.sendParticles(Undead.BLOOD_BIG, f.x, f.y + 0.3, f.z, 10, 0.6, 0.1, 0.6, 0.0);
		}
		if (age < 0) {
			return true;
		}
		double d = 1.0 + age * w.speed();
		if (d > w.range()) {
			return false;
		}
		Vec3 front = w.from().add(w.dir().scale(d));
		boolean great = w.height() > 2.0;
		// the box: the crescent's full width across, its height up from the ground for the great one, round its middle for the small
		AABB box = great ? new AABB(front.x - w.width(), front.y - 0.5, front.z - w.width(), front.x + w.width(), front.y + w.height(), front.z + w.width())
				: new AABB(front, front).inflate(w.width() + 0.3, w.height(), w.width() + 0.3);
		Vec3 side = new Vec3(-w.dir().z, 0, w.dir().x).normalize();
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != p && !(e instanceof UndeadCloneEntity) && HeroTargets.canHarm(p, e))) {
			// only what is actually across the crescent's line (not behind its horns)
			Vec3 rel = e.position().subtract(front);
			if (Math.abs(rel.dot(side)) > w.width() + e.getBbWidth() * 0.5 || Math.abs(rel.dot(w.dir())) > 1.6 + w.speed()) {
				continue;
			}
			if (w.hit().add(e.getId())) {
				UndeadAbilities.strike(p, e, front.subtract(w.dir().scale(1.5)), w.damage(), w.knock(), w.lift());
				UndeadSfx.swordHit(level, e.position().add(0, e.getBbHeight() * 0.5, 0));
			}
		}
		if (great && age % 2 == 0) {
			// the ground torn along its path
			level.sendParticles(Undead.BLOOD, front.x, front.y + 0.2, front.z, 4, w.width() * 0.5, 0.05, 0.3, 0.0);
			level.sendParticles(ParticleTypes.SWEEP_ATTACK, front.x, front.y + 1.0, front.z, 1, w.width() * 0.4, 0.3, 0.2, 0);
		}
		return true;
	}

	/**
	 * Z: Crimson Flash -- the iaido draw. He blurs {@link UndeadKitConfig#K_FLASH_DISTANCE} blocks forward along the ground in
	 * {@link UndeadKitConfig#K_FLASH_TICKS} ticks (stopping short of a wall), the blade out and back in its grip; everything he
	 * passed through is cut open {@link UndeadKitConfig#K_FLASH_DELAY} later, and each cut heals him.
	 */
	public static void flash(ServerPlayer p) {
		if (!begin(p, K_FLASH)) {
			return;
		}
		cooldownAnim(p, K_FLASH, UndeadKitConfig.K_FLASH_COOLDOWN, UndeadKitConfig.ANIM_K_FLASH);
		Vec3 dir = flatLook(p);
		Vec3 from = p.position();
		double dist = UndeadKitConfig.K_FLASH_DISTANCE;
		// stop short of a wall (at the feet and at the chest)
		for (double y : new double[] { 0.3, 1.2 }) {
			Vec3 a = from.add(0, y, 0);
			BlockHitResult bhr = p.level().clip(new ClipContext(a, a.add(dir.scale(dist + 0.6)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
			if (bhr.getType() != HitResult.Type.MISS) {
				dist = Math.min(dist, Math.max(0.0, bhr.getLocation().distanceTo(a) - 0.6));
			}
		}
		Vec3 to = from.add(dir.scale(dist));
		FLASHES.put(p.getUUID(), new Flash(p.level().getGameTime(), from, to));
		sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.5f, 1.4f);
		UndeadSfx.swordSwing(level(p), p.getEyePosition(), 1f);
		tickFlash(p, FLASHES.get(p.getUUID()), p.level().getGameTime());
	}

	private static void tickFlash(ServerPlayer p, Flash f, long now) {
		long age = now - f.start;
		int ticks = UndeadKitConfig.K_FLASH_TICKS;
		ServerLevel level = level(p);
		if (age <= ticks) {
			double k0 = Math.max(0, age - 1) / (double) ticks, k1 = Math.min(1.0, age / (double) ticks);
			Vec3 a = f.from.lerp(f.to, k0), b = f.from.lerp(f.to, k1);
			if (age > 0) {
				p.teleportTo(b.x, b.y, b.z);
				p.setDeltaMovement(f.to.subtract(f.from).normalize().scale(0.3));
				p.hurtMarked = true;
				p.resetFallDistance();
			}
			// who he passes through
			AABB box = new AABB(a, b).inflate(UndeadKitConfig.K_FLASH_WIDTH, 1.0, UndeadKitConfig.K_FLASH_WIDTH).expandTowards(0, 1.0, 0);
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != p && !(e instanceof UndeadCloneEntity) && HeroTargets.canHarm(p, e))) {
				f.passed.add(e.getId());
			}
			level.sendParticles(BLOOD_FLECK, b.x, b.y + 1.0, b.z, 6, 0.4, 0.3, 0.4, 0.0);
		}
		if (!f.opened && age >= ticks + UndeadKitConfig.K_FLASH_DELAY) {
			f.opened = true;
			int cut = 0;
			for (int id : f.passed) {
				if (level.getEntity(id) instanceof LivingEntity e && e.isAlive()
						&& UndeadAbilities.strike(p, e, e.position().subtract(f.to.subtract(f.from).normalize()), UndeadKitConfig.K_FLASH_DAMAGE, 0.3, 0.1)) {
					cut++;
					Vec3 c = e.position().add(0, e.getBbHeight() * 0.5, 0);
					level.sendParticles(Undead.BLOOD_BIG, c.x, c.y, c.z, 8, 0.25, 0.3, 0.25, 0.0);
					UndeadSfx.swordHit(level, c);
				}
			}
			if (cut > 0) {
				p.heal(UndeadKitConfig.K_FLASH_HEAL * cut);
			}
			// the blade clicks home in its grip
			level.playSound(null, p.getX(), p.getY() + 1, p.getZ(), SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.PLAYERS, 0.4f, 1.6f);
			FLASHES.remove(p.getUUID());
		}
	}

	/** Gametests: where the Crimson Flash ends. */
	public static Vec3 forTestsFlashEnd(ServerPlayer p) {
		Flash f = FLASHES.get(p.getUUID());
		return f == null ? null : f.to;
	}

	/** C: Blood Whirl -- two turns with the blade coated in blood: everything round him within {@link UndeadKitConfig#K_WHIRL_RADIUS}, twice. */
	public static void whirl(ServerPlayer p) {
		if (!begin(p, K_WHIRL)) {
			return;
		}
		cooldownAnim(p, K_WHIRL, UndeadKitConfig.K_WHIRL_COOLDOWN, UndeadKitConfig.ANIM_K_WHIRL);
		WHIRLS.put(p.getUUID(), p.level().getGameTime());
		sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.5f, 0.85f);
		tickWhirl(p, p.level().getGameTime(), p.level().getGameTime());
	}

	private static void tickWhirl(ServerPlayer p, long start, long now) {
		long age = now - start;
		if (age > UndeadKitConfig.K_WHIRL_TICKS) {
			WHIRLS.remove(p.getUUID());
			return;
		}
		ServerLevel level = level(p);
		if (age == UndeadKitConfig.K_WHIRL_HIT1 || age == UndeadKitConfig.K_WHIRL_HIT2) {
			Vec3 c = p.position().add(0, 0.9, 0);
			double r = UndeadKitConfig.K_WHIRL_RADIUS;
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r, 1.6, r),
					e -> e != p && !(e instanceof UndeadCloneEntity) && HeroTargets.canHarm(p, e))) {
				double dx = e.getX() - p.getX(), dz = e.getZ() - p.getZ();
				if (dx * dx + dz * dz <= (r + e.getBbWidth() * 0.5) * (r + e.getBbWidth() * 0.5)) {
					UndeadAbilities.strike(p, e, p.position(), UndeadKitConfig.K_WHIRL_DAMAGE, age == UndeadKitConfig.K_WHIRL_HIT2 ? 1.0 : 0.4, 0.15);
					UndeadSfx.swordHit(level, e.position().add(0, e.getBbHeight() * 0.5, 0));
				}
			}
			UndeadSfx.swordSwing(level, c, 0.8f);
			sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.45f, age == UndeadKitConfig.K_WHIRL_HIT2 ? 0.7f : 0.95f);
		}
	}

	/** The thrown sword hit {@code target}: it is pinned (a heavy slow and no jumping) for {@link #IMPALE_PIN_TICKS}. */
	public static void pin(ServerPlayer p, LivingEntity target) {
		if (!UndeadAbilities.isBoss(target)) {
			target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, IMPALE_PIN_TICKS, 6, false, true), p);
			target.addEffect(new MobEffectInstance(MobEffects.JUMP, IMPALE_PIN_TICKS, 128, false, false), p);
			target.setDeltaMovement(Vec3.ZERO);
			target.hurtMarked = true;
		} else {
			target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, IMPALE_PIN_TICKS / 2, 1, false, true), p);
		}
	}

	/** From {@code UndeadDamage.allowDamage}: false if the Rib Cage Guard (Shift+V) stops the hit ({@link UndeadRibGuard#blocks}). */
	public static boolean blocks(ServerPlayer p, DamageSource source, float amount) {
		return UndeadRibGuard.blocks(p, source, amount);
	}

	// ---------------------------------------------------------------- tick

	/** From {@link UndeadAbilities#tick}, every tick (also while re-forming): the katana, then the rest of the kit. */
	static void tick(ServerPlayer p) {
		UndeadState s = Undead.state(p);
		long now = p.level().getGameTime();
		if (s.boneSword()) {
			int hand = hand(s);
			if (s.reformUntil > now || s.broken(hand) || s.broken(UndeadState.BODY) || !s.broken(s.boneSwordArm())) {
				putAway(p, true, false); // lost the hand (or the body), or the arm is somehow back
			} else if (now - s.boneSwordSince() >= UndeadConfig.BONE_SWORD_MAX) {
				putAway(p, true, true);
			} else if (s.animId == UndeadState.ANIM_SWORD_RIP && now - s.animStart == UndeadConfig.BONE_SWORD_REVEAL) {
				reveal(p, s);
			}
		}
		List<Wave> waves = WAVES.get(p.getUUID());
		if (waves != null) {
			for (Iterator<Wave> it = waves.iterator(); it.hasNext();) {
				if (!tickWave(p, it.next(), now)) {
					it.remove();
				}
			}
			if (waves.isEmpty()) {
				WAVES.remove(p.getUUID());
			}
		}
		Flash f = FLASHES.get(p.getUUID());
		if (f != null) {
			tickFlash(p, f, now);
		}
		Long w = WHIRLS.get(p.getUUID());
		if (w != null) {
			tickWhirl(p, w, now);
		}
		UndeadKit.tick(p);
	}

	// ---------------------------------------------------------------- helpers

	private static ServerLevel level(ServerPlayer p) {
		return (ServerLevel) p.level();
	}

	private static void sound(ServerPlayer p, SoundEvent s, float vol, float pitch) {
		p.level().playSound(null, p.getX(), p.getY() + 1.0, p.getZ(), s, SoundSource.PLAYERS, vol, pitch);
	}

	private static Vec3 flatLook(ServerPlayer p) {
		Vec3 look = p.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0, look.z);
		return flat.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : flat.normalize();
	}

	private static Vec3 lungeDir(ServerPlayer p) {
		Vec3 look = p.getLookAngle();
		// mostly along the ground: a steep look only tilts it
		Vec3 d = new Vec3(look.x, Math.max(-0.35, Math.min(0.35, look.y)), look.z);
		return d.lengthSqr() < 1.0e-4 ? flatLook(p) : d.normalize();
	}
}
