package com.projecthero.mod.doctorfate;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.doctorfate.data.FateState;
import com.projecthero.mod.doctorfate.helmet.FateHelmet;
import com.projecthero.mod.doctorfate.helmet.FateHelmetEntity;
import com.projecthero.mod.doctorfate.helmet.FateHelmetGuard;
import com.projecthero.mod.doctorfate.helmet.FateHelmetRegistry;
import com.projecthero.mod.doctorfate.helmet.FateHelmetTag;
import com.projecthero.mod.doctorfate.network.FateSpellFxPayload;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.4 (user): the Helmet of Fate on and off his head, as a real object.
 * <ul>
 *   <li><b>H wearing it</b>: the lift-off sequence ({@link DoctorFateConfig#HELMET_REMOVE_TICKS}), then the Helmet of Fate item in
 *   your inventory (at your feet if it's full).</li>
 *   <li><b>Shift+right-click the item</b>: the put-on sequence ({@link DoctorFateConfig#HELMET_EQUIP_TICKS}).</li>
 *   <li><b>H not wearing it</b>: it's <b>summoned</b>. From your own inventory it goes straight on. Anywhere else (set down, dropped,
 *   in a chest, another player's or a mob's inventory, another dimension, an unloaded chunk) the owner's generation is bumped
 *   first ({@link FateHelmetRegistry#bump}) so every existing copy is a ghost; the copy is pulled out at once if it's somewhere
 *   loaded (it visibly flies to you out of a small ankh portal there), and otherwise it comes out of a big ankh portal beside you,
 *   and its ghost deletes itself the moment its chunk loads ({@link com.projecthero.mod.doctorfate.helmet.FateHelmetGuard}). Either
 *   way it arrives over {@link DoctorFateConfig#HELMET_SUMMON_TICKS} and the put-on sequence follows (into the inventory instead
 *   during the lockout after a Possession).</li>
 *   <li><b>While worn</b>: Regeneration I, and no armour (anything in the four armour slots is moved to the inventory, with a short
 *   action-bar line).</li>
 *   <li>When a Possession ends (or he dies possessed) the helmet falls from his head and lands as a {@link FateHelmetEntity}.</li>
 * </ul>
 * There's only ever one helmet per player: no copy exists while it's worn or flying in, and a summon always makes the old copy a
 * ghost before the new one exists. Static state is cleared by {@link DoctorFate#clearSessionState}.
 */
public final class DoctorFateHelmet {
	/** v0.17.4: a summon during the lockout lands in the inventory at this time instead of on the head. */
	private static final Map<UUID, Long> PENDING_GIVE = new ConcurrentHashMap<>();

	private DoctorFateHelmet() {
	}

	static void clearSessionState() {
		PENDING_GIVE.clear();
	}

	static void forget(UUID id) {
		PENDING_GIVE.remove(id);
	}

	/** A summon is flying in (to the head, or to the inventory during the lockout). Client-safe for the head case. */
	public static boolean summoning(ServerPlayer p) {
		FateState s = DoctorFate.peek(p);
		return PENDING_GIVE.containsKey(p.getUUID()) || (s != null && s.helmetChangeAt > DoctorFate.now(p));
	}

	// ---------------------------------------------------------------- H

	/** H: take it off when it's on, summon it when it isn't. False (with the reason) when nothing happened. */
	public static boolean pressH(ServerPlayer p) {
		FateState s = DoctorFate.state(p);
		if (!s.hasPower || DoctorFate.cooldownRemaining(p, DoctorFateAbilityManager.HELMET) > 0) {
			return false;
		}
		if (s.helmetChangeAt > 0L || PENDING_GIVE.containsKey(p.getUUID())) {
			return false; // mid-sequence: a double press never makes a second helmet
		}
		if (DoctorFate.possessed(p)) {
			DoctorFate.say(p, "message.projecthero.doctor_fate.cant_remove", ChatFormatting.GOLD);
			return false;
		}
		boolean ok = s.helmetOn ? beginRemove(p) : summon(p);
		if (ok) {
			DoctorFate.startCooldown(p, DoctorFateAbilityManager.HELMET, DoctorFateConfig.HELMET_COOLDOWN);
		}
		return ok;
	}

	static boolean beginRemove(ServerPlayer p) {
		FateState n = DoctorFate.state(p).copy();
		n.helmetChangeAt = DoctorFate.now(p);
		n.helmetGoingOn = false;
		n.shieldUp = false;
		DoctorFate.save(p, n);
		DoctorFate.sound(p, SoundEvents.ARMOR_EQUIP_GOLD.value(), 0.6f, 1.2f);
		DoctorFate.sound(p, SoundEvents.BEACON_DEACTIVATE, 0.35f, 1.6f);
		return true;
	}

	/** The put-on sequence starts at {@code at} (now, or when a summon arrives). */
	static void beginEquip(ServerPlayer p, long at) {
		FateState n = DoctorFate.state(p).copy();
		n.helmetChangeAt = at;
		n.helmetGoingOn = true;
		DoctorFate.save(p, n);
	}

	/**
	 * Shift+right-click with the item. True if the put-on sequence started (the caller empties the hand). Only its owner can wear it;
	 * a helmet nobody owns yet (a {@code /give}n one) becomes the wearer's and retires his old one.
	 */
	public static boolean wearFromItem(ServerPlayer p, ItemStack stack, InteractionHand hand) {
		FateHelmetRegistry reg = FateHelmetRegistry.get(p.serverLevel());
		FateHelmetTag tag = stack.get(FateHelmet.TAG);
		if (reg.isStale(stack)) {
			p.setItemInHand(hand, ItemStack.EMPTY);
			return false;
		}
		if (!DoctorFate.hasPower(p) || (tag != null && !tag.owner().equals(p.getUUID()))) {
			DoctorFate.say(p, "message.projecthero.doctor_fate.not_yours", ChatFormatting.GOLD);
			return false;
		}
		FateState s = DoctorFate.state(p);
		if (s.helmetOn || s.helmetChangeAt > 0L || PENDING_GIVE.containsKey(p.getUUID())) {
			return false;
		}
		if (DoctorFate.possessed(p)) {
			return false;
		}
		int lock = DoctorFate.lockoutTicksLeft(p);
		if (lock > 0) {
			DoctorFate.say(p, "message.projecthero.doctor_fate.lockout", ChatFormatting.GOLD, (lock + 19) / 20);
			return false;
		}
		if (tag == null) {
			reg.bump(p.getUUID()); // claiming an unowned helmet: the old one is a ghost now
		}
		reg.note(p.getUUID(), FateHelmetRegistry.Where.WORN, p.serverLevel(), p.blockPosition(), p.getUUID());
		beginEquip(p, DoctorFate.now(p));
		DoctorFate.sound(p, SoundEvents.ARMOR_EQUIP_GOLD.value(), 0.7f, 0.8f);
		return true;
	}

	/** H with the helmet off: call it from wherever it is. */
	static boolean summon(ServerPlayer p) {
		UUID id = p.getUUID();
		MinecraftServer server = p.server;
		FateHelmetRegistry reg = FateHelmetRegistry.get(server);
		long now = DoctorFate.now(p);
		boolean locked = DoctorFate.lockoutTicksLeft(p) > 0;
		// in his own inventory: it goes straight on (no flight)
		if (takeFrom(p, id, reg, true)) {
			if (locked) {
				giveBack(p); // the copy we took is the live one; put it back
				DoctorFate.say(p, "message.projecthero.doctor_fate.lockout", ChatFormatting.GOLD, (DoctorFate.lockoutTicksLeft(p) + 19) / 20);
				return false;
			}
			reg.note(id, FateHelmetRegistry.Where.WORN, p.serverLevel(), p.blockPosition(), id);
			beginEquip(p, now);
			DoctorFate.sound(p, SoundEvents.ARMOR_EQUIP_GOLD.value(), 0.7f, 0.8f);
			return true;
		}
		Found found = removeLiveCopy(server, reg, id, p);
		reg.bump(id); // every copy left anywhere is a ghost from now on
		reg.note(id, FateHelmetRegistry.Where.NONE, null, null, null);
		ServerLevel level = p.serverLevel();
		Vec3 eye = p.getEyePosition();
		Vec3 from;
		boolean flies = found != null && found.level == level && found.pos.distanceTo(eye) < 160.0;
		if (flies) {
			from = found.pos;
			DoctorFateFx.send(level, FateSpellFxPayload.PORTAL, from, eye.subtract(from), 1.0f, 18);
		} else {
			// unloaded, another dimension, or nowhere: a big ankh portal opens beside him and it comes out of that
			Vec3 look = p.getLookAngle();
			Vec3 flat = new Vec3(look.x, 0, look.z);
			flat = flat.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : flat.normalize();
			from = eye.add(flat.scale(2.6)).add(0, 1.3, 0);
			DoctorFateFx.send(level, FateSpellFxPayload.PORTAL, from, eye.subtract(from), 2.0f, DoctorFateConfig.HELMET_SUMMON_TICKS + 8);
			DoctorFate.sound(level, from, SoundEvents.PORTAL_TRIGGER, 0.25f, 1.8f);
		}
		if (found != null && found.level != level) {
			DoctorFateFx.send(found.level, FateSpellFxPayload.PORTAL, found.pos, new Vec3(0, 1, 0), 1.0f, 18);
			DoctorFate.sound(found.level, found.pos, SoundEvents.BEACON_DEACTIVATE, 0.6f, 1.6f);
		}
		DoctorFateFx.send(level, FateSpellFxPayload.SUMMON, from, Vec3.ZERO, flies ? 1f : 0f, DoctorFateConfig.HELMET_SUMMON_TICKS, p.getId(), DoctorFate.GOLD);
		DoctorFate.sound(level, from, SoundEvents.BEACON_POWER_SELECT, 0.6f, 1.4f);
		DoctorFate.sound(p, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0f, 0.8f);
		if (locked) {
			PENDING_GIVE.put(id, now + DoctorFateConfig.HELMET_SUMMON_TICKS);
		} else {
			beginEquip(p, now + DoctorFateConfig.HELMET_SUMMON_TICKS);
		}
		return true;
	}

	private record Found(ServerLevel level, Vec3 pos) {
	}

	/**
	 * Takes the live helmet out of wherever the registry last saw it (and out of every online player's inventory), if that's
	 * loaded. Null if it wasn't found loaded: the generation bump then retires it where it lies.
	 */
	private static Found removeLiveCopy(MinecraftServer server, FateHelmetRegistry reg, UUID owner, ServerPlayer summoner) {
		Found found = null;
		for (ServerPlayer q : server.getPlayerList().getPlayers()) {
			if (q != summoner && takeFrom(q, owner, reg, false)) {
				found = new Found(q.serverLevel(), q.getEyePosition());
				DoctorFate.sound(q, SoundEvents.ARMOR_EQUIP_GOLD.value(), 0.6f, 1.3f);
			}
		}
		FateHelmetRegistry.Entry e = reg.entry(owner);
		ServerLevel level = levelOf(server, e.dim());
		if (level == null) {
			return found;
		}
		switch (e.where()) {
			case PLACED, ITEM_ENTITY -> {
				Entity ent = e.thing().map(level::getEntity).orElse(null);
				if (ent != null && !ent.isRemoved() && holds(ent, owner)) {
					Vec3 at = ent.position().add(0, 0.4, 0);
					ent.discard();
					return new Found(level, at);
				}
				// a mob picked the dropped helmet up, or a hopper swallowed it: look right around where it was
				Found near = searchAround(level, e.blockPos(), owner);
				return near != null ? near : found;
			}
			case CONTAINER -> {
				Found near = searchAround(level, e.blockPos(), owner);
				return near != null ? near : found;
			}
			default -> {
				return found;
			}
		}
	}

	private static ServerLevel levelOf(MinecraftServer server, String dim) {
		if (dim == null || dim.isEmpty()) {
			return null;
		}
		ResourceLocation rl = ResourceLocation.tryParse(dim);
		return rl == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, rl));
	}

	private static boolean holds(Entity ent, UUID owner) {
		if (ent instanceof FateHelmetEntity h) {
			return h.owner().map(owner::equals).orElse(false);
		}
		return ent instanceof ItemEntity item && FateHelmet.belongsTo(item.getItem(), owner);
	}

	/** Loaded containers and creatures within 2 blocks of {@code pos} that hold {@code owner}'s helmet: it's taken out of them. */
	private static Found searchAround(ServerLevel level, BlockPos pos, UUID owner) {
		if (!level.isLoaded(pos)) {
			return null;
		}
		Found found = null;
		for (BlockPos q : BlockPos.betweenClosed(pos.offset(-2, -2, -2), pos.offset(2, 2, 2))) {
			if (!level.isLoaded(q)) {
				continue;
			}
			BlockEntity be = level.getBlockEntity(q);
			if (be instanceof Container c && FateHelmetGuard.takeFrom(c, owner)) {
				found = new Found(level, Vec3.atCenterOf(q));
			}
		}
		for (Entity ent : level.getEntities((Entity) null, new AABB(pos).inflate(3.0), x -> !(x instanceof net.minecraft.world.entity.player.Player))) {
			if (holds(ent, owner)) {
				found = new Found(level, ent.position().add(0, 0.4, 0));
				ent.discard();
			} else if (ent instanceof LivingEntity living) {
				for (EquipmentSlot slot : EquipmentSlot.values()) {
					if (FateHelmet.belongsTo(living.getItemBySlot(slot), owner)) {
						living.setItemSlot(slot, ItemStack.EMPTY);
						found = new Found(level, living.getEyePosition());
					}
				}
			} else if (ent instanceof Container c && FateHelmetGuard.takeFrom(c, owner)) {
				found = new Found(level, ent.position());
			}
		}
		return found;
	}

	/** Takes {@code owner}'s helmet out of {@code q}'s inventory, ender chest and cursor; {@code liveOnly}: ghosts don't count. */
	private static boolean takeFrom(ServerPlayer q, UUID owner, FateHelmetRegistry reg, boolean liveOnly) {
		boolean found = false;
		for (Container c : new Container[] { q.getInventory(), q.getEnderChestInventory() }) {
			for (int i = 0; i < c.getContainerSize(); i++) {
				ItemStack s = c.getItem(i);
				if (FateHelmet.belongsTo(s, owner)) {
					boolean live = !reg.isStale(s);
					c.setItem(i, ItemStack.EMPTY);
					found |= live || !liveOnly;
				}
			}
		}
		ItemStack carried = q.containerMenu.getCarried();
		if (FateHelmet.belongsTo(carried, owner)) {
			found |= !reg.isStale(carried) || !liveOnly;
			q.containerMenu.setCarried(ItemStack.EMPTY);
		}
		if (found) {
			q.containerMenu.broadcastChanges();
		}
		return found;
	}

	/** The helmet into his inventory (at his feet if it's full), as the current generation. */
	static void giveBack(ServerPlayer p) {
		FateHelmetRegistry reg = FateHelmetRegistry.get(p.server);
		ItemStack stack = FateHelmet.stack(p, reg.generation(p.getUUID()));
		if (p.getInventory().add(stack)) {
			reg.note(p.getUUID(), FateHelmetRegistry.Where.INVENTORY, p.serverLevel(), p.blockPosition(), p.getUUID());
		} else {
			p.drop(stack, false); // the item entity tells the registry where it is as it loads
		}
	}

	// ---------------------------------------------------------------- the sequences finishing (DoctorFate.tick)

	static void finishEquip(ServerPlayer p) {
		DoctorFate.setHelmet(p, true);
		FateHelmetRegistry.get(p.server).note(p.getUUID(), FateHelmetRegistry.Where.WORN, p.serverLevel(), p.blockPosition(), p.getUUID());
		ServerLevel level = p.serverLevel();
		// a ring of gold round the head (never inside it: the wearer's own camera is there)
		Vec3 head = p.getEyePosition().add(0, 0.15, 0);
		for (int i = 0; i < 16; i++) {
			double a = i * Math.PI * 2 / 16;
			level.sendParticles(DoctorFate.dust(i % 2 == 0 ? DoctorFateFx.WHITE_GOLD : DoctorFate.GOLD, 1.1f), head.x + Math.cos(a) * 1.0,
					head.y + 0.85, head.z + Math.sin(a) * 1.0, 1, 0, 0.05, 0, 0.0);
		}
		level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, head.x, head.y + 0.9, head.z, 6, 0.3, 0.15, 0.3, 0.04);
		DoctorFate.sound(p, SoundEvents.BEACON_ACTIVATE, 0.45f, 1.5f);
		DoctorFate.sound(p, SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 0.8f, 0.9f);
		com.projecthero.mod.quirk.fx.QuirkCamera.vignette(p, DoctorFate.GOLD, 0.35f, 14);
	}

	static void finishRemove(ServerPlayer p) {
		DoctorFate.setHelmet(p, false);
		giveBack(p);
		ServerLevel level = p.serverLevel();
		Vec3 c = p.position().add(0, 1.0, 0).add(p.getLookAngle().multiply(1, 0, 1).scale(0.4));
		level.sendParticles(DoctorFate.dust(DoctorFate.GOLD, 1.0f), c.x, c.y, c.z, 14, 0.25, 0.25, 0.25, 0.0);
		DoctorFate.sound(p, SoundEvents.ITEM_PICKUP, 0.5f, 0.8f);
	}

	/** A Possession ended (or he died possessed): the helmet falls from his head and lands as a {@link FateHelmetEntity}. */
	static void fallOff(ServerPlayer p) {
		ServerLevel level = p.serverLevel();
		FateHelmetRegistry reg = FateHelmetRegistry.get(level);
		FateHelmetTag tag = new FateHelmetTag(p.getUUID(), p.getGameProfile().getName(), reg.generation(p.getUUID()));
		Vec3 at = p.getEyePosition().add(p.getLookAngle().multiply(1, 0, 1).scale(0.3));
		FateHelmetEntity e = FateHelmetEntity.place(level, at, tag, p.getYRot() + 180f, true);
		e.setDeltaMovement(p.getLookAngle().multiply(0.15, 0, 0.15).add(0, 0.15, 0));
		level.addFreshEntity(e);
		reg.note(p.getUUID(), FateHelmetRegistry.Where.PLACED, level, e.blockPosition(), e.getUUID());
	}

	/** The power was taken away: every copy anywhere becomes a ghost. */
	static void retire(ServerPlayer p) {
		PENDING_GIVE.remove(p.getUUID());
		FateHelmetRegistry.get(p.server).retire(p.getUUID());
		FateHelmetGuard.purgePlayer(FateHelmetRegistry.get(p.server), p);
	}

	// ---------------------------------------------------------------- every tick

	static void tick(ServerPlayer p, FateState s, long now) {
		Long give = PENDING_GIVE.get(p.getUUID());
		if (give != null && now >= give) {
			PENDING_GIVE.remove(p.getUUID());
			giveBack(p);
			DoctorFate.sound(p, SoundEvents.ITEM_PICKUP, 0.6f, 0.7f);
		}
		boolean dressing = s.helmetChangeAt > 0L && s.helmetGoingOn && now >= s.helmetChangeAt;
		if (s.helmetOn || dressing) {
			stripArmour(p, !dressing || now - s.helmetChangeAt > 1);
		}
		if (s.helmetChangeAt > 0L && s.helmetGoingOn && now - s.helmetChangeAt == 14L) {
			// the helmet held high: the ankh flares behind it
			Vec3 up = p.getEyePosition().add(0, 0.9, 0).add(p.getLookAngle().multiply(1, 0, 1).scale(-0.2));
			DoctorFateFx.send(p.serverLevel(), FateSpellFxPayload.ANKH, up, p.getLookAngle().multiply(1, 0, 1), 1.6f, 14);
			DoctorFate.sound(p, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0f, 1.1f);
		}
		if (s.helmetOn) {
			regen(p);
		} else {
			endRegen(p);
		}
	}

	/** Nothing in the armour slots while the helmet is on: it all goes to the inventory (or the ground). */
	private static void stripArmour(ServerPlayer p, boolean tell) {
		boolean moved = false;
		for (EquipmentSlot slot : new EquipmentSlot[] { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET }) {
			ItemStack worn = p.getItemBySlot(slot);
			if (worn.isEmpty()) {
				continue;
			}
			p.setItemSlot(slot, ItemStack.EMPTY);
			if (!p.getInventory().add(worn)) {
				p.drop(worn, false);
			}
			moved = true;
		}
		if (moved) {
			DoctorFate.say(p, tell ? "message.projecthero.doctor_fate.no_armour" : "message.projecthero.doctor_fate.armour_off", ChatFormatting.GOLD);
			DoctorFate.sound(p, SoundEvents.ARMOR_EQUIP_GENERIC.value(), 0.5f, 0.7f);
		}
	}

	private static void regen(ServerPlayer p) {
		MobEffectInstance r = p.getEffect(MobEffects.REGENERATION);
		if (r == null || (r.getAmplifier() == 0 && r.getDuration() < DoctorFateConfig.HELMET_REGEN_REFRESH_BELOW && !r.isInfiniteDuration())) {
			p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, DoctorFateConfig.HELMET_REGEN_TICKS, 0, true, false, true));
		}
	}

	/** Takes off the helmet's own Regeneration (ambient, level I, no longer than it gives), never a potion's. */
	private static void endRegen(ServerPlayer p) {
		MobEffectInstance r = p.getEffect(MobEffects.REGENERATION);
		if (r != null && r.isAmbient() && r.getAmplifier() == 0 && !r.isInfiniteDuration() && r.getDuration() <= DoctorFateConfig.HELMET_REGEN_TICKS) {
			p.removeEffect(MobEffects.REGENERATION);
		}
	}

	/** Does {@code level} hold any live copy of {@code owner}'s helmet as an entity right now (tests)? */
	public static int liveEntities(ServerLevel level, UUID owner, AABB box) {
		FateHelmetRegistry reg = FateHelmetRegistry.get(level);
		int n = 0;
		for (Entity e : level.getEntities((Entity) null, box, x -> x instanceof FateHelmetEntity || x instanceof ItemEntity)) {
			if (e.isRemoved()) {
				continue;
			}
			if (e instanceof FateHelmetEntity h && h.owner().map(owner::equals).orElse(false) && !reg.isStale(h.tag())) {
				n++;
			} else if (e instanceof ItemEntity item && FateHelmet.isLive(reg, item.getItem(), owner)) {
				n++;
			}
		}
		return n;
	}
}
