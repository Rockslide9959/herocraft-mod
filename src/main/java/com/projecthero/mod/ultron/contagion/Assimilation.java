package com.projecthero.mod.ultron.contagion;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.ironman.IronManArmor;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ultron.UltronCombat;
import com.projecthero.mod.ultron.UltronFx;
import com.projecthero.mod.ultron.contagion.item.ContagionItems;
import com.projecthero.mod.ultron.entity.UltronRobot;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.1: what lives inside a Nexus's corruption.
 * <ul>
 *   <li><b>Assimilated mobs</b> ({@link #ASSIMILATED}): zombies, skeletons, spiders, creepers and pillagers that spawn in or
 *       walk into the corruption -- red eyes and half-metal plating (a client render layer), +4 armour, no poison, and a
 *       4-damage red bolt at 16 blocks. Skeletons' and pillagers' arrows become red bolts. Creepers become <b>Proximity
 *       Mines</b>: a faster fuse and an EMP that drains Iron Man suit energy. 20% drop Ultron Scrap. Checked when they
 *       spawn / load plus a slow sweep, never every tick.</li>
 *   <li><b>Ultron Golems</b> ({@link #ULTRON_GOLEM}): from 50% Saturation an iron golem in the corruption turns after 10 s
 *       -- red eyes, hostile, 150 health, a magnetic slam. The Nexus falling turns it back (it keeps its health).</li>
 *   <li><b>Compromised Stark machines</b>: refuse to work, glow red and build an Ultron Drone every 60 s (max 3 alive).</li>
 *   <li><b>Villagers</b> hide indoors (as if a bell rang) and refuse to trade.</li>
 * </ul>
 */
public final class Assimilation {
	public static final AttachmentType<Boolean> ASSIMILATED = AttachmentRegistry.create(ProjectHeroMod.id("assimilated"),
			b -> b.persistent(Codec.BOOL).initializer(() -> false).syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));
	public static final AttachmentType<Boolean> ULTRON_GOLEM = AttachmentRegistry.create(ProjectHeroMod.id("ultron_golem"),
			b -> b.persistent(Codec.BOOL).initializer(() -> false).syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));
	/** Ticks an iron golem has stood in the corruption (it turns at {@link ContagionConfig.Assimilation#golemConvertTicks}). */
	public static final AttachmentType<Integer> GOLEM_EXPOSURE = AttachmentRegistry.create(ProjectHeroMod.id("ultron_golem_exposure"),
			b -> b.persistent(Codec.INT).initializer(() -> 0));

	private static final ResourceLocation ARMOR_ID = ProjectHeroMod.id("assimilated_armor");
	private static final ResourceLocation HEALTH_ID = ProjectHeroMod.id("assimilated_health");
	private static final ResourceLocation GOLEM_HEALTH_ID = ProjectHeroMod.id("ultron_golem_health");

	private Assimilation() {
	}

	public static void initialize() {
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (isAssimilated(entity) && entity.level() instanceof ServerLevel server
					&& server.random.nextDouble() < ContagionConfig.assimilation().scrapChance) {
				entity.spawnAtLocation(new ItemStack(ContagionItems.ULTRON_SCRAP));
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof Creeper c && isAssimilated(c) && entity.getRemovalReason() == Entity.RemovalReason.DISCARDED
					&& c.getSwelling(1f) >= 0.9f) {
				proximityEmp(level, c.position());
			}
		});
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (!(level instanceof ServerLevel server) || server.dimension() != Level.OVERWORLD) {
				return InteractionResult.PASS;
			}
			ContagionState st = ContagionState.get(server);
			if (st.compromised.containsKey(hit.getBlockPos())) {
				player.displayClientMessage(Component.translatable("message.projecthero.contagion.compromised").withStyle(ChatFormatting.RED), true);
				server.playSound(null, hit.getBlockPos(), SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.8f, 1.6f);
				return InteractionResult.FAIL;
			}
			return InteractionResult.PASS;
		});
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (entity instanceof Villager v && level instanceof ServerLevel server && Contagion.corruptionAt(server, v.blockPosition()) != null) {
				v.setUnhappyCounter(40);
				server.playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1.0f, 1.0f);
				player.displayClientMessage(Component.translatable("message.projecthero.contagion.villager_refuses").withStyle(ChatFormatting.GRAY), true);
				return InteractionResult.FAIL;
			}
			return InteractionResult.PASS;
		});
	}

	public static boolean isAssimilated(Entity e) {
		return e.getAttachedOrElse(ASSIMILATED, false);
	}

	public static boolean isUltronGolem(Entity e) {
		return e.getAttachedOrElse(ULTRON_GOLEM, false);
	}

	/** Zombies (not zombified piglins), skeletons, spiders, creepers and pillagers. */
	public static boolean canAssimilate(Entity e) {
		return (e instanceof Zombie && !(e instanceof ZombifiedPiglin)) || e instanceof AbstractSkeleton || e instanceof Spider || e instanceof Creeper
				|| e instanceof Pillager;
	}

	// ---------------------------------------------------------------- assimilate / strip

	/** Makes {@code mob} Assimilated (idempotent). */
	public static void assimilate(Mob mob, ContagionState st) {
		if (isAssimilated(mob) || !canAssimilate(mob)) {
			return;
		}
		ContagionConfig.Assimilation cfg = ContagionConfig.assimilation();
		mob.setAttached(ASSIMILATED, true);
		addModifier(mob.getAttribute(Attributes.ARMOR), ARMOR_ID, cfg.armor, AttributeModifier.Operation.ADD_VALUE);
		double bonus = (st.saturation >= ContagionConfig.saturation().golemsAt ? cfg.saturationHealthBonus : 0.0)
				+ (ContagionConfig.unitHealthScale(st.version) - 1.0);
		if (bonus > 0) {
			addModifier(mob.getAttribute(Attributes.MAX_HEALTH), HEALTH_ID, bonus, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
			mob.setHealth(mob.getMaxHealth());
		}
		mob.removeEffect(MobEffects.POISON);
		if (mob instanceof Creeper c) {
			CompoundTag t = new CompoundTag();
			c.addAdditionalSaveData(t);
			t.putShort("Fuse", (short) Math.max(4, cfg.creeperFuse));
			c.readAdditionalSaveData(t);
		}
		if (mob.level() instanceof ServerLevel server) {
			server.sendParticles(UltronFx.RED, mob.getX(), mob.getY() + mob.getBbHeight() * 0.6, mob.getZ(), 10, 0.3, 0.4, 0.3, 0.02);
			server.sendParticles(UltronFx.STEEL, mob.getX(), mob.getY() + mob.getBbHeight() * 0.6, mob.getZ(), 8, 0.3, 0.4, 0.3, 0.02);
		}
	}

	/** A Purge Charge's EMP strips the plating: a normal mob again. */
	public static void strip(Mob mob) {
		if (!isAssimilated(mob)) {
			return;
		}
		mob.removeAttached(ASSIMILATED);
		removeModifier(mob.getAttribute(Attributes.ARMOR), ARMOR_ID);
		removeModifier(mob.getAttribute(Attributes.MAX_HEALTH), HEALTH_ID);
		if (mob.getHealth() > mob.getMaxHealth()) {
			mob.setHealth(mob.getMaxHealth());
		}
		if (mob instanceof Creeper c) {
			CompoundTag t = new CompoundTag();
			c.addAdditionalSaveData(t);
			t.putShort("Fuse", (short) 30);
			c.readAdditionalSaveData(t);
		}
	}

	/** Iron golem -> Ultron Golem. */
	public static void convertGolem(IronGolem golem) {
		if (isUltronGolem(golem)) {
			return;
		}
		golem.setAttached(ULTRON_GOLEM, true);
		golem.setPlayerCreated(false);
		double extra = ContagionConfig.assimilation().golemHealth - golem.getAttributeBaseValue(Attributes.MAX_HEALTH);
		if (extra > 0) {
			addModifier(golem.getAttribute(Attributes.MAX_HEALTH), GOLEM_HEALTH_ID, extra, AttributeModifier.Operation.ADD_VALUE);
			golem.setHealth(golem.getHealth() + (float) extra);
		}
		if (golem.level() instanceof ServerLevel server) {
			server.playSound(null, golem.getX(), golem.getY(), golem.getZ(), SoundEvents.IRON_GOLEM_DAMAGE, SoundSource.HOSTILE, 1.5f, 0.6f);
			server.sendParticles(UltronFx.RED_BIG, golem.getX(), golem.getY() + 2.2, golem.getZ(), 16, 0.4, 0.4, 0.4, 0.02);
		}
	}

	/** Ultron Golem -> iron golem (keeps its health, capped at the normal maximum). */
	public static void revertGolem(IronGolem golem) {
		if (!isUltronGolem(golem)) {
			return;
		}
		golem.removeAttached(ULTRON_GOLEM);
		golem.removeAttached(GOLEM_EXPOSURE);
		removeModifier(golem.getAttribute(Attributes.MAX_HEALTH), GOLEM_HEALTH_ID);
		if (golem.getHealth() > golem.getMaxHealth()) {
			golem.setHealth(golem.getMaxHealth());
		}
		golem.setTarget(null);
		if (golem.level() instanceof ServerLevel server) {
			server.sendParticles(UltronFx.CYAN, golem.getX(), golem.getY() + 2.2, golem.getZ(), 16, 0.4, 0.6, 0.4, 0.02);
		}
	}

	private static void addModifier(AttributeInstance attr, ResourceLocation id, double amount, AttributeModifier.Operation op) {
		if (attr != null && !attr.hasModifier(id)) {
			attr.addPermanentModifier(new AttributeModifier(id, amount, op));
		}
	}

	private static void removeModifier(AttributeInstance attr, ResourceLocation id) {
		if (attr != null) {
			attr.removeModifier(id);
		}
	}

	// ---------------------------------------------------------------- the sweep

	/** The slow sweep round every Nexus: assimilate, convert golems, hide villagers, compromise machines, fight. */
	public static void sweep(ServerLevel level, ContagionState st) {
		ContagionConfig.Assimilation cfg = ContagionConfig.assimilation();
		boolean golems = st.saturation >= ContagionConfig.saturation().golemsAt;
		long now = level.getGameTime();
		for (ContagionState.NexusRecord r : new ArrayList<>(st.nexus.values())) {
			if (r.radius <= 0 || !level.isPositionEntityTicking(r.pos)) {
				continue;
			}
			double rad = r.radius;
			AABB box = new AABB(r.pos).inflate(rad, 24, rad);
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
				double dx = e.getX() - r.pos.getX() - 0.5, dz = e.getZ() - r.pos.getZ() - 0.5;
				if (dx * dx + dz * dz > rad * rad) {
					continue;
				}
				if (e instanceof Mob mob && canAssimilate(mob)) {
					assimilate(mob, st);
					fight(level, mob);
				} else if (e instanceof IronGolem golem) {
					if (isUltronGolem(golem)) {
						fightGolem(level, golem, now);
					} else if (golems) {
						int t = golem.getAttachedOrElse(GOLEM_EXPOSURE, 0) + cfg.sweepTicks;
						golem.setAttached(GOLEM_EXPOSURE, t);
						if (t % 40 == 0 || t >= cfg.golemConvertTicks) {
							level.sendParticles(UltronFx.RED_SMALL, golem.getX(), golem.getY() + 2.4, golem.getZ(), 6, 0.3, 0.3, 0.3, 0.0);
						}
						if (t >= cfg.golemConvertTicks) {
							convertGolem(golem);
						}
					}
				} else if (e instanceof Villager v) {
					v.getBrain().setMemory(MemoryModuleType.HEARD_BELL_TIME, now);
				}
			}
			if (now % (cfg.sweepTicks * 5) == 0) {
				for (BlockEntity be : Contagion.blockEntitiesNear(level, r.pos, (int) Math.ceil(rad / 16.0))) {
					if (Contagion.isStarkMachine(be) && be.getBlockPos().distSqr(r.pos) <= rad * rad && !st.compromised.containsKey(be.getBlockPos())) {
						compromise(level, st, be.getBlockPos());
					}
				}
			}
		}
	}

	/** An Assimilated mob's red bolt (half the time each sweep, at a target in range and sight). */
	private static void fight(ServerLevel level, Mob mob) {
		ContagionConfig.Assimilation cfg = ContagionConfig.assimilation();
		LivingEntity target = mob.getTarget();
		if (target == null || !UltronCombat.canTarget(target)) {
			Player p = UltronCombat.nearestPlayer(level, mob.position(), cfg.boltRange);
			if (p != null) {
				mob.setTarget(p);
				target = p;
			}
		}
		if (target == null || mob instanceof Creeper || level.random.nextBoolean()) {
			return;
		}
		if (mob.distanceTo(target) <= cfg.boltRange && mob.hasLineOfSight(target)) {
			bolt(level, mob, target);
		}
	}

	/** A red bolt from {@code mob}'s eyes at {@code target}. */
	public static void bolt(ServerLevel level, Mob mob, LivingEntity target) {
		ContagionConfig.Assimilation cfg = ContagionConfig.assimilation();
		Vec3 from = mob.getEyePosition().add(mob.getLookAngle().scale(0.4));
		Vec3 dir = target.position().add(0, target.getBbHeight() * 0.55, 0).subtract(from);
		UltronCombat.hitscan(level, mob, from, dir, cfg.boltRange + 2, cfg.boltDamage, UltronFx.BOLT, 5, 0.25f);
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 0.6f, 2.0f);
	}

	/** An Ultron Golem: hunts players, and slams the magnetic ground when one is close. */
	private static void fightGolem(ServerLevel level, IronGolem golem, long now) {
		ContagionConfig.Assimilation cfg = ContagionConfig.assimilation();
		Player p = UltronCombat.nearestPlayer(level, golem.position(), 16);
		if (p != null && golem.getTarget() != p) {
			golem.setTarget(p);
		}
		if (p == null || golem.distanceTo(p) > 5.5 || (now + golem.getId()) % cfg.golemSlamCooldownTicks >= ContagionConfig.assimilation().sweepTicks) {
			return;
		}
		Vec3 c = golem.position();
		for (Player t : UltronCombat.playersNear(level, c, 6.0)) {
			Vec3 pull = c.subtract(t.position()).normalize().scale(0.6);
			t.setDeltaMovement(pull.x, 0.7, pull.z);
			t.hurtMarked = true;
			UltronCombat.hit(level, golem, t, cfg.golemSlamDamage);
		}
		UltronFx.ring(level, UltronFx.RED, c.add(0, 0.3, 0), 3.0, 24);
		UltronFx.ring(level, UltronFx.RED, c.add(0, 0.3, 0), 5.5, 40);
		level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y + 0.3, c.z, 2, 1.0, 0.1, 1.0, 0.0);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.IRON_GOLEM_ATTACK, SoundSource.HOSTILE, 2.0f, 0.5f);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.0f, 0.6f);
	}

	/** A Proximity Mine's EMP: every suited Iron Man within 5 blocks loses a quarter of the suit's energy. */
	public static void proximityEmp(ServerLevel level, Vec3 at) {
		ContagionConfig.Assimilation cfg = ContagionConfig.assimilation();
		UltronFx.forced(level, UltronFx.CYAN, at.x, at.y + 0.8, at.z, 40, cfg.empRadius * 0.3, 0.6, cfg.empRadius * 0.3, 0.1);
		UltronFx.ring(level, UltronFx.RED, at.add(0, 0.4, 0), cfg.empRadius, 36);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 2.0f, 0.6f);
		for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, new AABB(at, at).inflate(cfg.empRadius))) {
			String suit = IronManArmor.wornSuitId(p);
			if (suit != null && TonyStark.hasPower(p) && p.distanceToSqr(at) <= cfg.empRadius * cfg.empRadius) {
				float drain = (float) (IronManEnergy.capacity(suit) * cfg.empEnergyDrain);
				IronManEnergy.addEnergy(p, suit, -drain);
				p.displayClientMessage(Component.translatable("message.projecthero.contagion.emp").withStyle(ChatFormatting.AQUA), true);
			}
		}
	}

	// ---------------------------------------------------------------- spawn / load hooks

	/** New and loading entities: assimilate the right mobs in the corruption; Assimilated archers' arrows become red bolts. */
	public static void onEntityLoad(Entity entity, ServerLevel level) {
		if (level.dimension() != Level.OVERWORLD) {
			return;
		}
		if (entity instanceof AbstractArrow arrow && arrow.getOwner() instanceof Mob owner && isAssimilated(owner) && arrow.tickCount == 0) {
			LivingEntity target = owner.getTarget();
			level.getServer().execute(arrow::discard);
			if (target != null && UltronCombat.canTarget(target)) {
				bolt(level, owner, target);
			}
			return;
		}
		if (entity instanceof Mob mob && canAssimilate(mob) && !isAssimilated(mob) && entity.tickCount == 0) {
			ContagionState st = ContagionState.get(level);
			if ((st.stage == ContagionState.Stage.ACTIVE || st.stage == ContagionState.Stage.FINALE)
					&& Contagion.corruptionAt(level, mob.blockPosition()) != null) {
				assimilate(mob, st);
			}
		}
	}

	// ---------------------------------------------------------------- a Nexus falls

	/** The Nexus at {@code centre} fell: Assimilated mobs inside die (scrap), golems revert, machines come back. */
	public static void releaseAround(ServerLevel level, ContagionState st, Vec3 centre, double radius) {
		AABB box = new AABB(centre, centre).inflate(radius, 24, radius);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
			double dx = e.getX() - centre.x, dz = e.getZ() - centre.z;
			if (dx * dx + dz * dz > radius * radius) {
				continue;
			}
			if (e instanceof Mob mob && isAssimilated(mob)) {
				mob.spawnAtLocation(new ItemStack(ContagionItems.ULTRON_SCRAP));
				mob.removeAttached(ASSIMILATED); // no second scrap roll on death
				mob.hurt(level.damageSources().magic(), Float.MAX_VALUE);
				level.sendParticles(UltronFx.CYAN, mob.getX(), mob.getY() + 1, mob.getZ(), 8, 0.3, 0.5, 0.3, 0.05);
			} else if (e instanceof IronGolem golem) {
				revertGolem(golem);
			}
		}
		List<BlockPos> freed = new ArrayList<>();
		for (Map.Entry<BlockPos, ContagionState.Machine> m : st.compromised.entrySet()) {
			if (m.getKey().distSqr(BlockPos.containing(centre)) <= radius * radius) {
				freed.add(m.getKey());
			}
		}
		for (BlockPos p : freed) {
			uncompromise(level, st, p);
		}
	}

	// ---------------------------------------------------------------- Compromised Stark machines

	public static void compromise(ServerLevel level, ContagionState st, BlockPos pos) {
		ContagionState.Machine m = new ContagionState.Machine();
		ContagionState.NexusRecord rec = Contagion.corruptionAt(level, pos);
		if (rec == null) {
			double best = Double.MAX_VALUE;
			for (ContagionState.NexusRecord r : st.nexus.values()) {
				double d = r.pos.distSqr(pos);
				if (d < best) {
					best = d;
					rec = r;
				}
			}
		}
		m.nexus = rec != null ? rec.id : null;
		m.nextDroneAt = level.getGameTime() + 200;
		st.compromised.put(pos.immutable(), m);
		st.setDirty();
		level.playSound(null, pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.0f, 0.4f);
		UltronFx.forced(level, UltronFx.RED_BIG, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 12, 0.4, 0.4, 0.4, 0.0);
	}

	public static void uncompromise(ServerLevel level, ContagionState st, BlockPos pos) {
		if (st.compromised.remove(pos) != null) {
			st.setDirty();
			level.sendParticles(UltronFx.CYAN, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 12, 0.4, 0.3, 0.4, 0.02);
			level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0f, 1.4f);
		}
	}

	public static void releaseAllMachines(ServerLevel level, ContagionState st) {
		for (BlockPos p : new ArrayList<>(st.compromised.keySet())) {
			uncompromise(level, st, p);
		}
	}

	/** Once a second: red glow on each Compromised machine, and a drone every 60 s while fewer than 3 are alive. */
	public static void tickMachines(ServerLevel level, ContagionState st) {
		ContagionConfig.Assimilation cfg = ContagionConfig.assimilation();
		long now = level.getGameTime();
		List<BlockPos> gone = new ArrayList<>();
		for (Map.Entry<BlockPos, ContagionState.Machine> e : st.compromised.entrySet()) {
			BlockPos pos = e.getKey();
			if (!level.isLoaded(pos)) {
				continue;
			}
			BlockEntity be = level.getBlockEntity(pos);
			if (be == null || !Contagion.isStarkMachine(be)) {
				gone.add(pos);
				continue;
			}
			level.sendParticles(UltronFx.RED, pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, 4, 0.35, 0.35, 0.35, 0.0);
			ContagionState.Machine m = e.getValue();
			m.drones.removeIf(id -> !(level.getEntity(id) instanceof LivingEntity d) || !d.isAlive());
			if (now >= m.nextDroneAt) {
				m.nextDroneAt = now + cfg.machineDroneIntervalTicks;
				if (m.drones.size() < cfg.machineMaxDrones && level.isPositionEntityTicking(pos)) {
					Mob d = ContagionUnits.machineDrone(level, pos, st.version);
					if (d != null) {
						m.drones.add(d.getUUID());
					}
				}
			}
		}
		for (BlockPos p : gone) {
			st.compromised.remove(p);
		}
		if (!gone.isEmpty()) {
			st.setDirty();
		}
	}

	/** Every Ultron robot the Contagion owns -- stunned by a Purge Charge. */
	public static void stunRobots(ServerLevel level, Vec3 at, double r, int ticks) {
		for (UltronRobot robot : level.getEntitiesOfClass(UltronRobot.class, new AABB(at, at).inflate(r))) {
			if (robot.distanceToSqr(at) <= r * r) {
				robot.stun(ticks);
			}
		}
	}

	/** Test hook. */
	public static UUID nexusOf(ContagionState st, BlockPos machine) {
		ContagionState.Machine m = st.compromised.get(machine);
		return m == null ? null : m.nexus;
	}
}
