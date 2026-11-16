package com.projecthero.mod.powerarmour;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.hero.PowerClass;
import com.projecthero.mod.powerarmour.entity.PowerArmourFrameEntity;
import com.projecthero.mod.powerarmour.kit.PowerArmourAbilities;
import com.projecthero.mod.powerarmour.kit.PowerArmourModules;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.5 Power Armour -- the <b>Secondary power</b> you only have while sealed in a suit. It never replaces or is replaced by a
 * Primary power ({@link PowerClass#SECONDARY}, like the Symbiote): any hero can own frames and step into one. While sealed the
 * armour's kit owns R / G / Z / X / C / V ({@code AbilityRouter} hands them to {@link com.projecthero.mod.powerarmour.kit.PowerArmourAbilityManager}
 * before any Primary), the Primary's passives keep running, and its keys come back when you step out. H stays the power
 * selector; N is swallowed while sealed.
 *
 * <h2>Sequence</h2>
 * Right-click a frame: it splits open ({@link PowerArmourState#ENTER_OPEN}), you walk up and turn your back to it, step back in
 * ({@link PowerArmourState#ENTER_WALK}; the client drives the walk from {@link #drivenPose}), the frame leaves the world and the
 * suit closes round you panel by panel ({@link PowerArmourState#SEALING}) until the visor lights ({@link PowerArmourState#SEALED}).
 * Hold Shift+V: it opens on you ({@link PowerArmourState#UNSEALING}), the frame stands again and you step forward out of it
 * ({@link PowerArmourState#EXIT_WALK}).
 *
 * <h2>Rules (the choices)</h2>
 * <ul>
 *   <li>Worn armour is stored when you step in and handed back when you step out (anything put on while sealed drops into the
 *       inventory). Iron Man pieces, the Hulk, a Titan, an Omnitrix alien and every other transformation or suit power refuse
 *       the frame; one starting while sealed (the Hulk's rage) bursts the suit open.</li>
 *   <li>Dying inside: the suit ejects you and stands where you fell, {@link PowerArmourConfig#DEATH_INTEGRITY_LOSS} of its
 *       integrity gone; at zero integrity it also ejects you and stands as a wreck that will not seal until repaired.</li>
 *   <li>Integrity: every point of damage you take while sealed costs a point of integrity (before armour).</li>
 * </ul>
 */
public final class PowerArmour {
	public static final PowerClass POWER_CLASS = PowerClass.SECONDARY;

	private static final ResourceLocation MOD_ARMOR = ProjectHeroMod.id("power_armour_armor");
	private static final ResourceLocation MOD_TOUGH = ProjectHeroMod.id("power_armour_toughness");
	private static final ResourceLocation MOD_KB = ProjectHeroMod.id("power_armour_knockback");
	private static final ResourceLocation MOD_SPEED = ProjectHeroMod.id("power_armour_speed");
	private static final ResourceLocation MOD_STEP = ProjectHeroMod.id("power_armour_step");
	private static final EquipmentSlot[] ARMOUR = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };

	/** Re-entry guard for the cancel-and-reapply damage cuts (Vibranium blasts, the Shield Projector). */
	private static final ThreadLocal<Boolean> REAPPLY = ThreadLocal.withInitial(() -> false);

	private PowerArmour() {
	}

	// ================================================================== queries (both sides)

	public static PowerArmourState state(Player player) {
		return player.getAttached(PowerArmourAttachments.STATE);
	}

	/** Fully sealed: the kit is live. */
	public static boolean sealed(Player player) {
		PowerArmourState s = state(player);
		return s != null && s.sealed();
	}

	/** Has the suit on (sealing, sealed or opening up). */
	public static boolean wearing(Player player) {
		PowerArmourState s = state(player);
		return s != null && s.wearing();
	}

	/** Any part of the enter / sealed / exit sequence: the armour owns the ability keys (and swallows them while busy). */
	public static boolean ownsKeys(Player player) {
		PowerArmourState s = state(player);
		return s != null && s.phase != PowerArmourState.NONE;
	}

	public static SuitData suit(Player player) {
		PowerArmourState s = state(player);
		return s == null ? null : s.suit;
	}

	/** Daylight on open sky (Solar Plate). */
	public static boolean inSunlight(Level level, BlockPos pos) {
		return level.isDay() && !level.isRaining() && level.canSeeSky(pos);
	}

	public static boolean isRepairItem(ArmourTier tier, ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		return stack.is(tier.repairItem()) || stack.is(com.projecthero.mod.loot.LootItems.SUIT_REPAIR_KIT);
	}

	public static float repairShare(ArmourTier tier, ItemStack stack) {
		return stack.is(com.projecthero.mod.loot.LootItems.SUIT_REPAIR_KIT) ? PowerArmourConfig.REPAIR_KIT_SHARE : tier.repairFraction();
	}

	/** Forward unit vector for a yaw (degrees). */
	public static Vec3 forward(float yaw) {
		float r = yaw * Mth.DEG_TO_RAD;
		return new Vec3(-Mth.sin(r), 0.0, Mth.cos(r));
	}

	/**
	 * Where the player should stand (x, y, z) and face (yaw) at {@code time} during a driven step of the sequence, or null when
	 * nothing drives them. Shared by the client (which moves the local player) and the server (the snap at the end).
	 */
	public static double[] drivenPose(PowerArmourState s, float time) {
		if (s == null) {
			return null;
		}
		float t = time - s.phaseStart;
		Vec3 fwd = forward(s.fyaw);
		double ax = s.fx + fwd.x * PowerArmourConfig.APPROACH, az = s.fz + fwd.z * PowerArmourConfig.APPROACH;
		switch (s.phase) {
			case PowerArmourState.ENTER_OPEN -> {
				float k = Mth.clamp(t / PowerArmourConfig.ENTER_OPEN_TICKS, 0f, 1f);
				float walk = smooth(Mth.clamp(k / 0.6f, 0f, 1f));
				float turn = smooth(Mth.clamp((k - 0.6f) / 0.4f, 0f, 1f));
				double x = Mth.lerp(walk, s.sx, ax), z = Mth.lerp(walk, s.sz, az), y = Mth.lerp(walk, s.sy, s.fy);
				float facing = s.fyaw + 180f; // looking at the frame
				float yaw = turn <= 0f ? Mth.rotLerp(walk, s.syaw, facing) : Mth.rotLerp(turn, facing, facing + 180f);
				return new double[] { x, y, z, yaw };
			}
			case PowerArmourState.ENTER_WALK -> {
				float k = smooth(Mth.clamp(t / PowerArmourConfig.ENTER_WALK_TICKS, 0f, 1f));
				return new double[] { Mth.lerp(k, ax, s.fx), s.fy, Mth.lerp(k, az, s.fz), s.fyaw };
			}
			case PowerArmourState.SEALING, PowerArmourState.UNSEALING -> {
				return new double[] { s.fx, s.fy, s.fz, s.fyaw };
			}
			case PowerArmourState.EXIT_WALK -> {
				float k = smooth(Mth.clamp(t / PowerArmourConfig.EXIT_WALK_TICKS, 0f, 1f));
				return new double[] { Mth.lerp(k, s.fx, s.sx), s.fy, Mth.lerp(k, s.fz, s.sz), s.fyaw };
			}
			default -> {
				return null;
			}
		}
	}

	private static float smooth(float k) {
		return k * k * (3f - 2f * k);
	}

	// ================================================================== entering

	/** Why {@code player} may not step into anything right now (a translation key), or null. */
	public static String whyNotEnter(ServerPlayer player) {
		if (player.isSpectator() || player.isSleeping() || player.isPassenger()) {
			return "message.projecthero.power_armour.cant_now";
		}
		if (otherSuitActive(player)) {
			return "message.projecthero.power_armour.other_suit";
		}
		return null;
	}

	/** Another suit or transformation is on (Iron Man, Hulk, Titan, Omnitrix alien, Moon Knight, Black Panther, ...). */
	public static boolean otherSuitActive(ServerPlayer player) {
		return com.projecthero.mod.ironman.IronManArmor.wearingAnyIronMan(player)
				|| com.projecthero.mod.hulk.Hulk.isHulk(player)
				|| com.projecthero.mod.titanshifter.TitanShifter.phase(player).insideForm()
				|| com.projecthero.mod.omnitrix.Omnitrix.transformed(player)
				|| com.projecthero.mod.moonknight.ability.MoonKnightAbilityManager.hasContext(player)
				|| com.projecthero.mod.blackpanther.BlackPanther.suited(player)
				|| com.projecthero.mod.maxsteel.MaxSteel.isTransformed(player)
				|| com.projecthero.mod.shazam.Shazam.transformed(player)
				|| com.projecthero.mod.miraculous.Miraculous.transformed(player)
				|| com.projecthero.mod.symbiote.Symbiote.isActive(player)
				|| com.projecthero.mod.thorarmor.ThorArmor.isWearing(player);
	}

	/** Right-click on a frame: start stepping in. */
	public static boolean tryEnter(ServerPlayer player, PowerArmourFrameEntity frame) {
		PowerArmourState cur = state(player);
		if (cur != null && cur.phase != PowerArmourState.NONE) {
			return false;
		}
		if (frame.occupied() || frame.anim() == PowerArmourFrameEntity.CLOSING) {
			return false;
		}
		if (player.distanceTo(frame) > PowerArmourConfig.ENTER_REACH) {
			return false;
		}
		SuitData suit = frame.suit();
		if (suit.wrecked()) {
			say(player, "message.projecthero.power_armour.wrecked", ChatFormatting.RED);
			return false;
		}
		String why = whyNotEnter(player);
		if (why != null) {
			say(player, why, ChatFormatting.RED);
			return false;
		}
		long now = player.level().getGameTime();
		frame.claim(player);
		if (frame.anim() != PowerArmourFrameEntity.OPEN) {
			frame.setAnim(PowerArmourFrameEntity.OPENING);
		}
		PowerArmourState s = new PowerArmourState();
		s.phase = PowerArmourState.ENTER_OPEN;
		s.phaseStart = now;
		s.fx = frame.getX();
		s.fy = frame.getY();
		s.fz = frame.getZ();
		s.fyaw = frame.getYRot();
		s.sx = player.getX();
		s.sy = player.getY();
		s.sz = player.getZ();
		s.syaw = player.getYRot();
		s.frameId = frame.getId();
		// the turning point in front of the frame must be free, else turn round inside it
		Vec3 fwd = forward(s.fyaw);
		AABB at = player.getBoundingBox().move(s.fx + fwd.x * PowerArmourConfig.APPROACH - player.getX(), s.fy - player.getY(),
				s.fz + fwd.z * PowerArmourConfig.APPROACH - player.getZ());
		if (!player.level().noCollision(player, at)) {
			s.sx = s.fx;
			s.sz = s.fz;
		}
		player.setAttached(PowerArmourAttachments.STATE, s);
		player.stopUsingItem();
		ServerLevel level = player.serverLevel();
		level.playSound(null, frame.getX(), frame.getY() + 1.0, frame.getZ(), SoundEvents.PISTON_EXTEND, SoundSource.PLAYERS, 0.8f, 0.7f);
		level.playSound(null, frame.getX(), frame.getY() + 1.0, frame.getZ(), SoundEvents.IRON_DOOR_OPEN, SoundSource.PLAYERS, 0.7f, 0.6f);
		steam(level, frame.position().add(0, 1.2, 0), 10);
		return true;
	}

	/** Steps straight in (no walk) -- the gametests' and the admin path. */
	public static void enterInstant(ServerPlayer player, PowerArmourFrameEntity frame) {
		PowerArmourState s = new PowerArmourState();
		s.phase = PowerArmourState.ENTER_WALK;
		s.phaseStart = player.level().getGameTime() - PowerArmourConfig.ENTER_WALK_TICKS;
		s.fx = frame.getX();
		s.fy = frame.getY();
		s.fz = frame.getZ();
		s.fyaw = frame.getYRot();
		s.frameId = frame.getId();
		frame.claim(player);
		player.setAttached(PowerArmourAttachments.STATE, s);
		beginSeal(player, s);
		PowerArmourState sealed = state(player);
		if (sealed != null && sealed.phase == PowerArmourState.SEALING) {
			PowerArmourState n = sealed.copy();
			n.phase = PowerArmourState.SEALED;
			player.setAttached(PowerArmourAttachments.STATE, n);
			applyAttributes(player, n.suit);
		}
	}

	/** Leaves the suit straight away (no walk): the frame stands where the player is. Returns it. */
	public static PowerArmourFrameEntity exitInstant(ServerPlayer player) {
		PowerArmourState s = state(player);
		if (s == null || s.suit == null) {
			return null;
		}
		PowerArmourFrameEntity frame = standFrame(player, s.suit, player.position(), player.getYRot(), PowerArmourFrameEntity.CLOSED);
		finishExit(player);
		return frame;
	}

	private static void beginSeal(ServerPlayer player, PowerArmourState s) {
		Entity e = player.level().getEntity(s.frameId);
		if (!(e instanceof PowerArmourFrameEntity frame) || frame.isRemoved() || !frame.claimedBy(player)) {
			cancel(player);
			return;
		}
		SuitData suit = frame.suit().copy();
		frame.discard();
		PowerArmourState n = s.copy();
		n.suit = suit;
		n.phase = PowerArmourState.SEALING;
		n.phaseStart = player.level().getGameTime();
		n.frameId = -1;
		n.readyAt.clear();
		player.teleportTo(s.fx, s.fy, s.fz);
		player.setYRot(s.fyaw);
		player.setYHeadRot(s.fyaw);
		player.setYBodyRot(s.fyaw);
		stashArmour(player);
		player.setAttached(PowerArmourAttachments.STATE, n);
		player.serverLevel().playSound(null, player.getX(), player.getY() + 1.0, player.getZ(), SoundEvents.IRON_DOOR_CLOSE,
				SoundSource.PLAYERS, 0.8f, 0.6f);
	}

	private static void cancel(ServerPlayer player) {
		PowerArmourState s = state(player);
		if (s != null && s.frameId >= 0 && player.level().getEntity(s.frameId) instanceof PowerArmourFrameEntity frame) {
			frame.release();
		}
		player.removeAttached(PowerArmourAttachments.STATE);
	}

	// ================================================================== leaving

	/** Shift+V was held long enough: the suit opens on the player. */
	public static void beginUnseal(ServerPlayer player) {
		PowerArmourState s = state(player);
		if (s == null || !s.sealed()) {
			return;
		}
		PowerArmourAbilities.endAll(player, s);
		PowerArmourState n = state(player).copy();
		n.phase = PowerArmourState.UNSEALING;
		n.phaseStart = player.level().getGameTime();
		n.charge = 0;
		n.visor = false;
		n.thrusting = false;
		n.fx = player.getX();
		n.fy = player.getY();
		n.fz = player.getZ();
		n.fyaw = player.getYRot();
		player.setAttached(PowerArmourAttachments.STATE, n);
		ServerLevel level = player.serverLevel();
		level.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(), SoundEvents.PISTON_EXTEND, SoundSource.PLAYERS, 0.8f, 0.7f);
		level.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(), SoundEvents.IRON_DOOR_OPEN, SoundSource.PLAYERS, 0.7f, 0.6f);
		steam(level, player.position().add(0, 1.2, 0), 12);
	}

	/** The suit has opened: the frame stands where the player was, and they step out forward. */
	private static void beginExitWalk(ServerPlayer player, PowerArmourState s) {
		PowerArmourFrameEntity frame = standFrame(player, s.suit, new Vec3(s.fx, s.fy, s.fz), s.fyaw, PowerArmourFrameEntity.OPEN);
		PowerArmourState n = s.copy();
		n.suit = null;
		n.phase = PowerArmourState.EXIT_WALK;
		n.phaseStart = player.level().getGameTime();
		n.frameId = frame == null ? -1 : frame.getId();
		Vec3 fwd = forward(s.fyaw);
		Vec3 out = new Vec3(s.fx + fwd.x * PowerArmourConfig.APPROACH, s.fy, s.fz + fwd.z * PowerArmourConfig.APPROACH);
		AABB at = player.getBoundingBox().move(out.x - player.getX(), out.y - player.getY(), out.z - player.getZ());
		if (!player.level().noCollision(player, at)) {
			out = new Vec3(s.fx, s.fy, s.fz);
		}
		n.sx = out.x;
		n.sy = out.y;
		n.sz = out.z;
		removeAttributes(player);
		restoreArmour(player);
		player.setAttached(PowerArmourAttachments.STATE, n);
	}

	private static void finishExit(ServerPlayer player) {
		PowerArmourState s = state(player);
		if (s != null && s.suit != null) {
			PowerArmourAbilities.endAll(player, s);
		}
		if (s != null && s.frameId >= 0 && player.level().getEntity(s.frameId) instanceof PowerArmourFrameEntity frame) {
			frame.release();
			frame.setAnim(PowerArmourFrameEntity.CLOSING);
			player.serverLevel().playSound(null, frame.getX(), frame.getY() + 1.0, frame.getZ(), SoundEvents.PISTON_CONTRACT,
					SoundSource.PLAYERS, 0.6f, 0.9f);
		}
		removeAttributes(player);
		restoreArmour(player);
		player.removeAttached(PowerArmourAttachments.STATE);
	}

	/** The suit bursts open and stands where the player is (death, zero integrity, a transformation inside it). */
	public static void eject(ServerPlayer player, SuitData suit, String messageKey) {
		PowerArmourState s = state(player);
		if (s != null) {
			PowerArmourAbilities.endAll(player, s);
		}
		standFrame(player, suit, player.position(), player.getYRot(), PowerArmourFrameEntity.CLOSING);
		removeAttributes(player);
		restoreArmour(player);
		player.removeAttached(PowerArmourAttachments.STATE);
		ServerLevel level = player.serverLevel();
		level.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(), SoundEvents.IRON_DOOR_OPEN, SoundSource.PLAYERS, 1f, 0.5f);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() + 1.0, player.getZ(), 20, 0.4, 0.7, 0.4, 0.03);
		if (messageKey != null) {
			say(player, messageKey, ChatFormatting.RED);
		}
	}

	private static PowerArmourFrameEntity standFrame(ServerPlayer player, SuitData suit, Vec3 at, float yaw, int anim) {
		ServerLevel level = player.serverLevel();
		PowerArmourFrameEntity frame = PowerArmourMod.FRAME.create(level);
		if (frame == null) {
			return null;
		}
		frame.moveTo(at.x, at.y, at.z, yaw, 0f);
		frame.setSuit(suit);
		level.addFreshEntity(frame);
		frame.setAnim(anim);
		if (anim == PowerArmourFrameEntity.OPEN) {
			frame.claim(player);
		}
		return frame;
	}

	// ================================================================== armour stash

	private static void stashArmour(ServerPlayer player) {
		List<ItemStack> stash = new ArrayList<>(4);
		boolean any = false;
		for (EquipmentSlot slot : ARMOUR) {
			ItemStack piece = player.getItemBySlot(slot);
			stash.add(piece.copy());
			any |= !piece.isEmpty();
			player.setItemSlot(slot, ItemStack.EMPTY);
		}
		List<ItemStack> old = player.getAttached(PowerArmourAttachments.STASH);
		if (old != null) {
			old.forEach(stack -> giveOrDrop(player, stack));
		}
		if (any) {
			player.setAttached(PowerArmourAttachments.STASH, stash);
		} else {
			player.removeAttached(PowerArmourAttachments.STASH);
		}
	}

	private static void restoreArmour(ServerPlayer player) {
		List<ItemStack> stash = player.getAttached(PowerArmourAttachments.STASH);
		if (stash == null) {
			return;
		}
		for (int i = 0; i < Math.min(stash.size(), ARMOUR.length); i++) {
			ItemStack piece = stash.get(i);
			if (piece.isEmpty()) {
				continue;
			}
			if (player.getItemBySlot(ARMOUR[i]).isEmpty()) {
				player.setItemSlot(ARMOUR[i], piece.copy());
			} else {
				giveOrDrop(player, piece.copy());
			}
		}
		player.removeAttached(PowerArmourAttachments.STASH);
	}

	static void giveOrDrop(ServerPlayer player, ItemStack stack) {
		if (stack.isEmpty()) {
			return;
		}
		if (!player.getInventory().add(stack)) {
			player.drop(stack, false);
		}
	}

	// ================================================================== tick

	public static void tick(ServerPlayer player) {
		PowerArmourState s = state(player);
		if (s == null) {
			return;
		}
		long now = player.level().getGameTime();
		long t = now - s.phaseStart;
		switch (s.phase) {
			case PowerArmourState.ENTER_OPEN -> {
				if (!(player.level().getEntity(s.frameId) instanceof PowerArmourFrameEntity frame) || frame.isRemoved()) {
					cancel(player);
				} else if (t >= PowerArmourConfig.ENTER_OPEN_TICKS) {
					PowerArmourState n = s.copy();
					n.phase = PowerArmourState.ENTER_WALK;
					n.phaseStart = now;
					player.setAttached(PowerArmourAttachments.STATE, n);
				}
			}
			case PowerArmourState.ENTER_WALK -> {
				if (t >= PowerArmourConfig.ENTER_WALK_TICKS) {
					beginSeal(player, s);
				}
			}
			case PowerArmourState.SEALING -> {
				holdStill(player, s);
				if (t == 6 || t == 16 || t == 26) {
					player.serverLevel().playSound(null, player.getX(), player.getY() + 1.0, player.getZ(), SoundEvents.PISTON_CONTRACT,
							SoundSource.PLAYERS, 0.6f, 0.9f + t * 0.01f);
				}
				if (t >= PowerArmourConfig.SEAL_TICKS) {
					PowerArmourState n = s.copy();
					n.phase = PowerArmourState.SEALED;
					n.phaseStart = now;
					player.setAttached(PowerArmourAttachments.STATE, n);
					applyAttributes(player, n.suit);
					player.serverLevel().playSound(null, player.getX(), player.getY() + 1.6, player.getZ(), SoundEvents.BEACON_ACTIVATE,
							SoundSource.PLAYERS, 0.7f, 1.7f);
					say(player, n.suit.powered() ? "message.projecthero.power_armour.sealed" : "message.projecthero.power_armour.sealed_unpowered",
							n.suit.powered() ? ChatFormatting.AQUA : ChatFormatting.GRAY);
				}
			}
			case PowerArmourState.SEALED -> tickSealed(player, s, now);
			case PowerArmourState.UNSEALING -> {
				holdStill(player, s);
				if (t >= PowerArmourConfig.UNSEAL_TICKS) {
					beginExitWalk(player, s);
				}
			}
			case PowerArmourState.EXIT_WALK -> {
				if (t >= PowerArmourConfig.EXIT_WALK_TICKS) {
					finishExit(player);
				}
			}
			default -> player.removeAttached(PowerArmourAttachments.STATE);
		}
	}

	private static void holdStill(ServerPlayer player, PowerArmourState s) {
		Vec3 v = player.getDeltaMovement();
		player.setDeltaMovement(0.0, Math.min(0.0, v.y), 0.0);
	}

	private static void tickSealed(ServerPlayer player, PowerArmourState s, long now) {
		SuitData suit = s.suit;
		if (suit.integrity <= 0f) {
			eject(player, suit, "message.projecthero.power_armour.broken");
			return;
		}
		if (otherSuitActive(player)) {
			eject(player, suit, "message.projecthero.power_armour.burst");
			return;
		}
		boolean poweredBefore = suit.powered();
		float drain = PowerArmourConfig.IDLE_DRAIN * suit.tier.drain * (suit.has(ModuleType.OVERCLOCK) ? PowerArmourConfig.OVERCLOCK_DRAIN : 1f);
		if (s.visor) {
			drain += PowerArmourConfig.VISOR_PER_SECOND / 20f;
		}
		suit.drain(drain);
		if (now % 20 == 0) {
			if (suit.has(ModuleType.SOLAR_PLATE) && inSunlight(player.level(), BlockPos.containing(player.getEyePosition()))) {
				suit.charge(PowerArmourConfig.SOLAR_PER_SECOND);
			}
			if (suit.has(ModuleType.CAPACITOR_BANK) && suit.bank < suit.bankCapacity() && suit.coreCharge > suit.capacity() * 0.5f) {
				float move = Math.min(PowerArmourConfig.BANK_SIPHON, suit.bankCapacity() - suit.bank);
				suit.coreCharge -= move;
				suit.bank += move;
			}
		}
		if (s.visor && !suit.powered()) {
			s.visor = false;
		}
		// Shift+V held: open up
		if (s.charge == 3 && now - s.chargeStart >= PowerArmourConfig.EXIT_HOLD_TICKS) {
			beginUnseal(player);
			return;
		}
		PowerArmourModules.tickPassives(player, s, now);
		PowerArmourAbilities.tick(player, s, now);
		// armour put on over the suit falls into the bag
		for (EquipmentSlot slot : ARMOUR) {
			ItemStack piece = player.getItemBySlot(slot);
			if (!piece.isEmpty()) {
				player.setItemSlot(slot, ItemStack.EMPTY);
				giveOrDrop(player, piece);
			}
		}
		if (player.isInWater()) {
			player.setSprinting(false);
			player.setSwimming(false);
		}
		if (suit.tier.fireproof() && player.isOnFire()) {
			player.clearFire();
		}
		boolean poweredNow = suit.powered();
		applyAttributes(player, suit); // cheap: each modifier is only touched when it changes
		if (poweredBefore && !poweredNow) {
			say(player, "message.projecthero.power_armour.unpowered", ChatFormatting.GRAY);
			player.serverLevel().playSound(null, player.getX(), player.getY() + 1.6, player.getZ(), SoundEvents.BEACON_DEACTIVATE,
					SoundSource.PLAYERS, 0.7f, 0.8f);
		}
		if (now % 10 == 0 || poweredBefore != poweredNow) {
			sync(player);
		}
	}

	/**
	 * Pushes the in-place changes of the player's state to every client. The live object stays the stored one, so the rest of a
	 * tick can keep changing it (the sealed tick and the kit change the state in place and sync when it matters).
	 */
	public static void sync(ServerPlayer player) {
		PowerArmourState s = state(player);
		if (s != null) {
			player.setAttached(PowerArmourAttachments.STATE, s);
		}
	}

	// ================================================================== attributes

	public static void applyAttributes(ServerPlayer player, SuitData suit) {
		ArmourTier tier = suit.tier;
		set(player, Attributes.ARMOR, MOD_ARMOR, tier.armour, AttributeModifier.Operation.ADD_VALUE);
		set(player, Attributes.ARMOR_TOUGHNESS, MOD_TOUGH, tier.toughness, AttributeModifier.Operation.ADD_VALUE);
		float kb = suit.has(ModuleType.STABILISERS) ? 1f : tier.knockback;
		set(player, Attributes.KNOCKBACK_RESISTANCE, MOD_KB, kb, AttributeModifier.Operation.ADD_VALUE);
		float speed = tier.speed + (suit.has(ModuleType.SERVO_BOOSTERS) ? PowerArmourConfig.SERVO_BOOST : 0f);
		if (!suit.powered()) {
			speed = PowerArmourConfig.UNPOWERED_SPEED;
		}
		set(player, Attributes.MOVEMENT_SPEED, MOD_SPEED, speed, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		set(player, Attributes.STEP_HEIGHT, MOD_STEP, suit.powered() ? PowerArmourConfig.STEP_HEIGHT : 0f, AttributeModifier.Operation.ADD_VALUE);
	}

	public static void removeAttributes(ServerPlayer player) {
		remove(player, Attributes.ARMOR, MOD_ARMOR);
		remove(player, Attributes.ARMOR_TOUGHNESS, MOD_TOUGH);
		remove(player, Attributes.KNOCKBACK_RESISTANCE, MOD_KB);
		remove(player, Attributes.MOVEMENT_SPEED, MOD_SPEED);
		remove(player, Attributes.STEP_HEIGHT, MOD_STEP);
	}

	private static void set(ServerPlayer player, Holder<Attribute> attr, ResourceLocation id, double amount, AttributeModifier.Operation op) {
		AttributeInstance inst = player.getAttribute(attr);
		if (inst == null) {
			return;
		}
		AttributeModifier cur = inst.getModifier(id);
		if (cur != null && cur.amount() == amount && cur.operation() == op) {
			return;
		}
		inst.addOrUpdateTransientModifier(new AttributeModifier(id, amount, op));
	}

	private static void remove(ServerPlayer player, Holder<Attribute> attr, ResourceLocation id) {
		AttributeInstance inst = player.getAttribute(attr);
		if (inst != null && inst.getModifier(id) != null) {
			inst.removeModifier(id);
		}
	}

	// ================================================================== damage + death

	/** {@code ServerLivingEntityEvents.ALLOW_DAMAGE}: falls, fire, blasts, the shield, integrity, Reactive Plating. */
	public static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (!(entity instanceof ServerPlayer player) || REAPPLY.get()) {
			return true;
		}
		PowerArmourState s = state(player);
		if (s == null || !s.wearing()) {
			return true;
		}
		SuitData suit = s.suit;
		if (source.is(DamageTypeTags.IS_FALL)) {
			if (suit.tier.absorbsImpacts()) {
				suit.charge(PowerArmourConfig.VIBRANIUM_FALL_GAIN * amount);
				landingDust(player, amount);
				return false;
			}
			if (suit.spend(PowerArmourConfig.FALL_COST * amount)) {
				landingDust(player, amount);
				return false;
			}
			suit.drain(suit.energy());
		}
		if (source.is(DamageTypeTags.IS_FIRE) && suit.tier.fireproof()) {
			player.clearFire();
			return false;
		}
		long now = player.level().getGameTime();
		boolean shield = s.shieldUntil > now;
		if (shield && source.getDirectEntity() instanceof Projectile) {
			player.serverLevel().playSound(null, player.getX(), player.getY() + 1.0, player.getZ(), SoundEvents.SHIELD_BLOCK,
					SoundSource.PLAYERS, 0.8f, 1.4f);
			return false;
		}
		float cut = 1f;
		if (source.is(DamageTypeTags.IS_EXPLOSION) && suit.tier.absorbsImpacts()) {
			suit.charge(PowerArmourConfig.VIBRANIUM_BLAST_GAIN * amount);
			cut *= PowerArmourConfig.VIBRANIUM_BLAST_CUT;
		}
		if (shield) {
			cut *= 1f - PowerArmourConfig.SHIELD_CUT;
		}
		if (!source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			suit.integrity = Math.max(0f, suit.integrity - amount * cut * PowerArmourConfig.INTEGRITY_PER_DAMAGE);
		}
		if (source.getEntity() instanceof LivingEntity attacker && attacker != player) {
			PowerArmourModules.onHurtBy(player, s, attacker, now);
		}
		if (cut < 0.999f) {
			REAPPLY.set(true);
			try {
				player.hurt(source, amount * cut);
			} finally {
				REAPPLY.set(false);
			}
			return false;
		}
		return true;
	}

	private static void landingDust(ServerPlayer player, float amount) {
		if (amount >= 3f) {
			player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.IRON_GOLEM_DAMAGE,
					SoundSource.PLAYERS, 0.6f, 0.6f);
			player.serverLevel().sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.1, player.getZ(),
					Math.min(30, (int) amount * 2), 0.6, 0.05, 0.6, 0.04);
		}
	}

	/** {@code ServerLivingEntityEvents.ALLOW_DEATH}: the suit ejects its wearer and stands where they fell. */
	public static boolean allowDeath(LivingEntity entity, DamageSource source, float amount) {
		if (!(entity instanceof ServerPlayer player)) {
			return true;
		}
		PowerArmourState s = state(player);
		if (s == null) {
			return true;
		}
		if (s.wearing()) {
			SuitData suit = s.suit.copy();
			suit.integrity = Math.max(0f, suit.integrity - suit.tier.integrity * PowerArmourConfig.DEATH_INTEGRITY_LOSS);
			eject(player, suit, null);
		} else {
			cancel(player);
			restoreArmour(player);
		}
		return true;
	}

	/** Join: a sequence cut off by a logout is tidied (sealing finishes, entering is dropped, leaving completes). */
	public static void onJoin(ServerPlayer player) {
		PowerArmourState s = state(player);
		if (s == null) {
			removeAttributes(player);
			restoreArmour(player);
			return;
		}
		switch (s.phase) {
			case PowerArmourState.SEALING, PowerArmourState.SEALED -> {
				PowerArmourState n = s.copy();
				n.phase = PowerArmourState.SEALED;
				n.charge = 0;
				n.grabbed = -1;
				n.thrusting = false;
				player.setAttached(PowerArmourAttachments.STATE, n);
				applyAttributes(player, n.suit);
			}
			case PowerArmourState.UNSEALING -> beginExitWalk(player, s);
			default -> {
				player.removeAttached(PowerArmourAttachments.STATE);
				removeAttributes(player);
				restoreArmour(player);
			}
		}
	}

	// ================================================================== fx + text

	public static void steam(ServerLevel level, Vec3 at, int count) {
		level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, count, 0.35, 0.5, 0.35, 0.02);
		level.sendParticles(ParticleTypes.WHITE_SMOKE, at.x, at.y, at.z, count, 0.3, 0.6, 0.3, 0.01);
	}

	static void say(ServerPlayer player, String key, ChatFormatting colour) {
		player.displayClientMessage(Component.translatable(key).withStyle(colour), true);
	}
}
