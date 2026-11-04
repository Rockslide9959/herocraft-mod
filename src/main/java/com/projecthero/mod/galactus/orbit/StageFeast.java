package com.projecthero.mod.galactus.orbit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.projecthero.mod.galactus.GalactusCombat;
import com.projecthero.mod.galactus.GalactusConfig;
import com.projecthero.mod.galactus.GalactusEvent;
import com.projecthero.mod.galactus.GalactusFx;
import com.projecthero.mod.galactus.entity.ElementConverterEntity;
import com.projecthero.mod.galactus.entity.GalactusEntityTypes;
import com.projecthero.mod.galactus.entity.PunisherRobotEntity;
import com.projecthero.mod.galactus.item.GalactusItems;
import com.projecthero.mod.quirk.fx.QuirkCamera;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 Galactus raid v2, <b>Stage 2: the Feast</b>. The islands are the Worldship's hull now; Galactus kneels over the
 * Earth and drinks it through 3-5 <b>Element Converters</b> standing on the deck (World Consumption climbs; at 100% the
 * world is lost). The way to win is to turn his own energy on him:
 * <ol>
 * <li>a converter is shielded while its two Punisher guards live;</li>
 * <li>the feed beams throw off <b>Cosmic Cells</b>: pick one up and you are the <b>Carrier</b> (you glow, you're slower,
 * you can't fly, and a hit of 6+ knocks it out of your hands);</li>
 * <li>right-click an unshielded converter with it: it <b>overloads</b> for 6 s -- defend it, Punishers in reach stall
 * it -- then its beam flips into Galactus and it blows apart, dropping an Ultimate Nullifier Part.</li>
 * </ol>
 * Meanwhile he sweeps eye beams across the deck (Carriers first), bolts any converter that starts to overload, and the
 * Worldship drops Punishers. Every converter reversed ends the stage. Saved with the event (counts only; the converters
 * and their guards are re-made on load).
 */
public final class StageFeast {
	private static final Map<UUID, Long> CARRIER_SINCE = new HashMap<>();

	public final List<UUID> converters = new ArrayList<>();
	public int total;
	public int reversed;
	public final Map<UUID, Integer> overload = new HashMap<>();
	private final Map<UUID, Long> bolted = new HashMap<>();
	private final List<UUID> cells = new ArrayList<>();
	private long nextCellAt = -1;
	private long nextPunishersAt = -1;
	/** The eye beam: 0 idle, 1 telegraph, 2 sweep. */
	private int beamState;
	private long beamAt;
	private int beamCooldown = 160;
	private Vec3 beamFrom = Vec3.ZERO, beamTo = Vec3.ZERO;
	private final Set<UUID> beamHit = new HashSet<>();
	/** Where he kneels: polar angle round the origin (the +Z side). */
	public static final double KNEEL_R = 118, KNEEL_Y = -178;

	public static void clearStatics() {
		CARRIER_SINCE.clear();
	}

	// ---------------------------------------------------------------- setup

	public static Vec3 kneelFeet(BlockPos o) {
		return new Vec3(o.getX() + 0.5, o.getY() + KNEEL_Y, o.getZ() + 0.5 + KNEEL_R);
	}

	/** Him kneeling over the Earth, his hands cupped down round it, his eyes on the deck. */
	public static void kneelPose(DevourerEntity d, BlockPos o, float k) {
		Vec3 feet = kneelFeet(o);
		d.setFeet(feet.x, feet.y, feet.z);
		d.setFacing(180f);
		d.setLean(Mth.lerp(k, d.lean(), 0.75f));
		Vec3 earth = new Vec3(o.getX() + 0.5, o.getY() - 320, o.getZ() + 0.5 + KNEEL_R + 30);
		d.setArm(true, earth.add(-30, 0, 0), k);
		d.setArm(false, earth.add(30, 0, 0), k);
		d.setFlag(DevourerEntity.FLAG_FIST_R, false);
		d.setFlag(DevourerEntity.FLAG_FIST_L, false);
		d.setLook(Vec3.atCenterOf(o));
	}

	public static int converterCount(int fighters) {
		GalactusConfig.Stage2 c = GalactusConfig.stage2();
		return Math.min(c.maxConverters, c.converters + c.convertersPerTwoExtraFighters * (Math.max(0, fighters - 1) / 2));
	}

	public void spawnConverters(GalactusEvent ev, ServerLevel orbit, BlockPos o, int count, boolean guards) {
		converters.clear();
		overload.clear();
		for (int i = 0; i < count; i++) {
			double a = i * Math.PI * 2 / count + Math.PI / 2 + Math.PI / count;
			Vec3 at = OrbitArena.hullSpot(o, 25, a);
			ElementConverterEntity conv = GalactusEntityTypes.ELEMENT_CONVERTER.create(orbit);
			if (conv == null) {
				continue;
			}
			conv.moveTo(at.x, at.y, at.z, orbit.random.nextFloat() * 360f, 0f);
			conv.configure(i, GalactusConfig.converterHealthFor(ev.fighterCount()));
			conv.cellOnly = true;
			ev.adoptOrbit(conv);
			orbit.addFreshEntity(conv);
			conv.beginFall(at.y + 60, 30);
			converters.add(conv.getUUID());
			if (guards) {
				for (int g = 0; g < 2; g++) {
					spawnPunisher(ev, orbit, at.add(Math.cos(a + (g == 0 ? 0.5 : -0.5)) * 6, 0, Math.sin(a + (g == 0 ? 0.5 : -0.5)) * 6), conv);
				}
			}
			conv.setShielded(guards);
		}
	}

	private PunisherRobotEntity spawnPunisher(GalactusEvent ev, ServerLevel orbit, Vec3 at, ElementConverterEntity guardOf) {
		PunisherRobotEntity p = GalactusEntityTypes.PUNISHER.create(orbit);
		if (p == null) {
			return null;
		}
		p.moveTo(at.x, at.y, at.z, orbit.random.nextFloat() * 360f, 0f);
		p.finalizeSpawn(orbit, orbit.getCurrentDifficultyAt(BlockPos.containing(at)), MobSpawnType.EVENT, null);
		p.configure(GalactusConfig.punisherHealthFor(ev.fighterCount()));
		if (guardOf != null) {
			p.guard(guardOf, at);
		}
		ev.adoptOrbit(p);
		orbit.addFreshEntity(p);
		GalactusFx.forced(orbit, ParticleTypes.END_ROD, at.x, at.y + 12, at.z, 30, 0.3, 12, 0.3, 0.02);
		return p;
	}

	public List<ElementConverterEntity> live(ServerLevel orbit) {
		List<ElementConverterEntity> out = new ArrayList<>();
		for (UUID u : converters) {
			if (orbit.getEntity(u) instanceof ElementConverterEntity c && c.isAlive() && !c.isDeadOrDying()) {
				out.add(c);
			}
		}
		return out;
	}

	public boolean done() {
		return total > 0 && reversed >= total;
	}

	// ---------------------------------------------------------------- tick

	public void tick(GalactusEvent ev, ServerLevel orbit, DevourerEntity d, long now) {
		GalactusConfig.Stage2 c = GalactusConfig.stage2();
		BlockPos o = GalactusOrbit.origin(ev.orbitSlot());
		List<ServerPlayer> fighters = ev.orbitFighters(orbit);
		List<ElementConverterEntity> live = live(orbit);
		if (d != null) {
			kneelPose(d, o, 1f);
			if (beamState == 0 && d.eyes() > 0.4f) {
				d.setEyes(Math.max(0.4f, d.eyes() - 0.02f));
			}
		}
		// the feed: each live converter draws a violet stream up toward him
		if (now % 4 == 0 && d != null) {
			for (ElementConverterEntity conv : live) {
				Vec3 top = conv.position().add(0, 18, 0);
				Vec3 to = d.pose().chest;
				Vec3 mid = top.lerp(to, 0.08 + (now % 40) / 40.0 * 0.1);
				GalactusFx.line(orbit, GalactusFx.COSMIC_SMALL, top, mid, 1.5);
			}
		}
		// shields
		if (now % 10 == 0) {
			for (ElementConverterEntity conv : live) {
				boolean guarded = false;
				for (PunisherRobotEntity p : ev.orbitPunishers(orbit)) {
					if (conv.getUUID().equals(p.guardOf()) && p.isAlive()) {
						guarded = true;
						break;
					}
				}
				conv.setShielded(guarded);
			}
		}
		tickOverloads(ev, orbit, d, live, now);
		tickCells(ev, orbit, live, fighters, now);
		tickCarriers(ev, orbit, fighters, now);
		tickBeam(ev, orbit, d, fighters, now);
		// Punisher drops from the Worldship
		if (nextPunishersAt < 0) {
			nextPunishersAt = now + c.punisherIntervalTicks;
		}
		if (now >= nextPunishersAt) {
			nextPunishersAt = now + c.punisherIntervalTicks;
			int free = 0;
			for (PunisherRobotEntity p : ev.orbitPunishers(orbit)) {
				if (p.guardOf() == null) {
					free++;
				}
			}
			for (int i = 0; i < 2 && free < c.punisherCap; i++, free++) {
				double a = orbit.random.nextDouble() * Math.PI * 2;
				PunisherRobotEntity p = spawnPunisher(ev, orbit, OrbitArena.hullSpot(o, 8 + orbit.random.nextDouble() * 18, a).add(0, 30, 0), null);
				if (p != null) {
					p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 200, 0, false, false));
				}
			}
		}
	}

	// ---------------------------------------------------------------- the cells

	private void tickCells(GalactusEvent ev, ServerLevel orbit, List<ElementConverterEntity> live, List<ServerPlayer> fighters, long now) {
		GalactusConfig.Stage2 c = GalactusConfig.stage2();
		cells.removeIf(u -> !(orbit.getEntity(u) instanceof ItemEntity ie) || !ie.isAlive());
		for (UUID u : cells) {
			if (orbit.getEntity(u) instanceof ItemEntity ie) {
				if (ie.getY() < GalactusOrbit.PLANE_Y - 30) {
					ie.discard();
				} else if (now % 5 == 0) {
					orbit.sendParticles(ParticleTypes.END_ROD, ie.getX(), ie.getY() + 0.5, ie.getZ(), 2, 0.2, 0.3, 0.2, 0.02);
				}
			}
		}
		if (nextCellAt < 0) {
			nextCellAt = now + 80;
		}
		if (now >= nextCellAt && !live.isEmpty()) {
			nextCellAt = now + c.cellIntervalTicks;
			int carried = 0;
			for (ServerPlayer p : fighters) {
				carried += p.getInventory().countItem(GalactusItems.COSMIC_CELL);
			}
			if (cells.size() + carried < c.maxLooseCells) {
				ElementConverterEntity src = live.get(orbit.random.nextInt(live.size()));
				BlockPos o = GalactusOrbit.origin(ev.orbitSlot());
				Vec3 toward = new Vec3(o.getX() + 0.5 - src.getX(), 0, o.getZ() + 0.5 - src.getZ()).normalize();
				Vec3 at = src.position().add(toward.scale(8 + orbit.random.nextDouble() * 4)).add(0, 1.2, 0);
				ItemEntity item = new ItemEntity(orbit, at.x, at.y, at.z, new ItemStack(GalactusItems.COSMIC_CELL));
				item.setUnlimitedLifetime();
				item.setInvulnerable(true);
				item.setGlowingTag(true);
				item.setDeltaMovement(0, 0.2, 0);
				orbit.addFreshEntity(item);
				cells.add(item.getUUID());
				GalactusFx.line(orbit, GalactusFx.COSMIC, src.position().add(0, 18, 0), at, 1.0);
				orbit.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 2.0f, 1.8f);
				for (ServerPlayer p : fighters) {
					p.displayClientMessage(Component.translatable("message.projecthero.galactus.cell_spawned").withStyle(ChatFormatting.LIGHT_PURPLE), true);
				}
			}
		}
	}

	public static boolean carrying(ServerPlayer p) {
		return p.getInventory().countItem(GalactusItems.COSMIC_CELL) > 0;
	}

	private void tickCarriers(GalactusEvent ev, ServerLevel orbit, List<ServerPlayer> fighters, long now) {
		for (ServerPlayer p : fighters) {
			if (!carrying(p)) {
				CARRIER_SINCE.remove(p.getUUID());
				continue;
			}
			if (!CARRIER_SINCE.containsKey(p.getUUID())) {
				CARRIER_SINCE.put(p.getUUID(), now);
				p.displayClientMessage(Component.translatable("message.projecthero.galactus.carrier").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
			}
			if (now % 20 == 0) {
				p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 0, true, false, true));
				p.addEffect(new MobEffectInstance(MobEffects.GLOWING, 30, 0, true, false, false));
			}
			if (p.getAbilities().flying || p.isFallFlying() || !p.onGround() && p.getY() > GalactusOrbit.PLANE_Y + 6) {
				OrbitRules.ground(p, 20);
			}
			if (now % 4 == 0) {
				orbit.sendParticles(GalactusFx.COSMIC_SMALL, p.getX(), p.getY() + 1.2, p.getZ(), 3, 0.3, 0.5, 0.3, 0.02);
			}
			// Punishers go for the Carrier
			for (PunisherRobotEntity pr : ev.orbitPunishers(orbit)) {
				if (pr.distanceToSqr(p) < 30 * 30 && pr.getTarget() != p && now % 20 == 0) {
					pr.setTarget(p);
				}
			}
		}
	}

	/** A Carrier took a big hit: the cell is knocked out of their hands. */
	public void onCarrierHurt(GalactusEvent ev, ServerLevel orbit, ServerPlayer p, float dealt) {
		if (dealt < GalactusConfig.stage2().carrierDropDamage || !carrying(p)) {
			return;
		}
		int n = 0;
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
			ItemStack s = p.getInventory().getItem(i);
			if (s.is(GalactusItems.COSMIC_CELL)) {
				n += s.getCount();
				p.getInventory().setItem(i, ItemStack.EMPTY);
			}
		}
		for (int i = 0; i < n; i++) {
			ItemEntity item = new ItemEntity(orbit, p.getX(), p.getY() + 1, p.getZ(), new ItemStack(GalactusItems.COSMIC_CELL));
			item.setUnlimitedLifetime();
			item.setInvulnerable(true);
			item.setGlowingTag(true);
			item.setPickUpDelay(25);
			item.setDeltaMovement((orbit.random.nextDouble() - 0.5) * 0.5, 0.35, (orbit.random.nextDouble() - 0.5) * 0.5);
			orbit.addFreshEntity(item);
			cells.add(item.getUUID());
		}
		p.displayClientMessage(Component.translatable("message.projecthero.galactus.cell_dropped").withStyle(ChatFormatting.RED), true);
		orbit.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 1.0f, 0.6f);
	}

	// ---------------------------------------------------------------- sockets and overloads

	/** {@code p} right-clicked {@code conv}. True if the click did something. */
	public boolean socket(GalactusEvent ev, ServerLevel orbit, ServerPlayer p, ElementConverterEntity conv) {
		if (!converters.contains(conv.getUUID()) || overload.containsKey(conv.getUUID())) {
			return false;
		}
		if (!carrying(p)) {
			p.displayClientMessage(Component.translatable("message.projecthero.galactus.need_cell").withStyle(ChatFormatting.LIGHT_PURPLE), true);
			return true;
		}
		if (conv.isShielded()) {
			p.displayClientMessage(Component.translatable("message.projecthero.galactus.converter_shielded").withStyle(ChatFormatting.LIGHT_PURPLE), true);
			return true;
		}
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
			ItemStack s = p.getInventory().getItem(i);
			if (s.is(GalactusItems.COSMIC_CELL)) {
				s.shrink(1);
				break;
			}
		}
		overload.put(conv.getUUID(), 0);
		orbit.playSound(null, conv.getX(), conv.getY(), conv.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 4.0f, 0.6f);
		orbit.playSound(null, conv.getX(), conv.getY(), conv.getZ(), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.HOSTILE, 4.0f, 0.5f);
		ev.broadcastOrbit(orbit, Component.translatable("message.projecthero.galactus.overloading", p.getDisplayName()).withStyle(ChatFormatting.GOLD));
		return true;
	}

	private void tickOverloads(GalactusEvent ev, ServerLevel orbit, DevourerEntity d, List<ElementConverterEntity> live, long now) {
		GalactusConfig.Stage2 c = GalactusConfig.stage2();
		for (Iterator<Map.Entry<UUID, Integer>> it = overload.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Integer> e = it.next();
			ElementConverterEntity conv = orbit.getEntity(e.getKey()) instanceof ElementConverterEntity cv ? cv : null;
			if (conv == null || !conv.isAlive()) {
				it.remove();
				continue;
			}
			boolean contested = false;
			for (PunisherRobotEntity p : ev.orbitPunishers(orbit)) {
				if (p.distanceToSqr(conv) < 7 * 7) {
					contested = true;
					break;
				}
			}
			int prog = e.getValue() + (contested ? -1 : 1);
			prog = Math.max(0, prog);
			e.setValue(prog);
			double k = prog / (double) c.overloadTicks;
			if (now % 3 == 0) {
				GalactusFx.forced(orbit, ParticleTypes.ELECTRIC_SPARK, conv.getX(), conv.getY() + 4 + k * 14, conv.getZ(), (int) (6 + k * 20), 2, 4, 2, 0.2);
				GalactusFx.ring(orbit, contested ? GalactusFx.BLUE : GalactusFx.EMBER, conv.position().add(0, 0.3, 0), 3 + k * 3, 24);
			}
			if (now % 10 == 0) {
				orbit.playSound(null, conv.getX(), conv.getY(), conv.getZ(), SoundEvents.BEACON_AMBIENT, SoundSource.HOSTILE, 3.0f, 0.6f + (float) k);
			}
			// he sees it and bolts it (once per overload)
			Long b = bolted.get(conv.getUUID());
			if (b == null) {
				bolted.put(conv.getUUID(), now + 30);
			} else if (b > 0 && now >= b && d != null) {
				if (now < b + 30) {
					if (now % 2 == 0) {
						GalactusFx.ring(orbit, GalactusFx.EMBER, conv.position().add(0, 0.3, 0), 6, 40);
					}
				} else {
					bolted.put(conv.getUUID(), -1L);
					Vec3 face = d.pose().face;
					GalactusFx.line(orbit, GalactusFx.COSMIC, face, conv.position().add(0, 1, 0), 2.0);
					GalactusFx.forced(orbit, ParticleTypes.EXPLOSION, conv.getX(), conv.getY() + 1, conv.getZ(), 6, 3, 1, 3, 0);
					orbit.playSound(null, conv.getX(), conv.getY(), conv.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 3.0f, 0.8f);
					for (ServerPlayer p : ev.orbitFighters(orbit)) {
						if (p.distanceToSqr(conv.getX(), p.getY(), conv.getZ()) < 6 * 6 && Math.abs(p.getY() - conv.getY()) < 5) {
							GalactusCombat.hit(orbit, d, p, (float) c.boltDamage);
						}
					}
				}
			}
			if (prog >= c.overloadTicks) {
				it.remove();
				bolted.remove(conv.getUUID());
				reverse(ev, orbit, d, conv);
			}
		}
	}

	/** The converter's beam flips into him and it blows apart. */
	private void reverse(GalactusEvent ev, ServerLevel orbit, DevourerEntity d, ElementConverterEntity conv) {
		Vec3 top = conv.position().add(0, 18, 0);
		if (d != null) {
			Vec3 chest = d.pose().chest;
			GalactusFx.line(orbit, GalactusFx.COSMIC, top, chest, 1.2);
			GalactusFx.line(orbit, ParticleTypes.END_ROD, top, chest, 2.0);
			GalactusFx.forced(orbit, GalactusFx.COSMIC, chest.x, chest.y, chest.z, 160, 10, 10, 10, 0.3);
			orbit.playSound(null, chest.x, chest.y, chest.z, SoundEvents.ENDER_DRAGON_HURT, SoundSource.HOSTILE, 8.0f, 0.4f);
			orbit.playSound(null, chest.x, chest.y, chest.z, SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 8.0f, 0.6f);
			d.setEyes(1f);
			d.setLean(0.3f); // he jolts upright, then sinks back into the kneel
		}
		for (ServerPlayer p : ev.orbitFighters(orbit)) {
			QuirkCamera.shake(p, 5f, 18);
		}
		converters.remove(conv.getUUID());
		reversed++;
		ev.dropPart(orbit, conv.position().add(0, 1.5, 0));
		conv.setShielded(false);
		conv.kill();
		ev.broadcastOrbit(orbit, Component.translatable("message.projecthero.galactus.converter_reversed", reversed, total).withStyle(ChatFormatting.LIGHT_PURPLE));
		if (!done()) {
			ev.surferLineOrbit(orbit, "part");
		}
	}

	// ---------------------------------------------------------------- the eye beam

	private void tickBeam(GalactusEvent ev, ServerLevel orbit, DevourerEntity d, List<ServerPlayer> fighters, long now) {
		GalactusConfig.Stage2 c = GalactusConfig.stage2();
		if (d == null || fighters.isEmpty()) {
			return;
		}
		BlockPos o = GalactusOrbit.origin(ev.orbitSlot());
		switch (beamState) {
			case 0 -> {
				if (--beamCooldown > 0) {
					return;
				}
				LivingEntity target = null;
				for (ServerPlayer p : fighters) {
					if (carrying(p)) {
						target = p;
						break;
					}
				}
				if (target == null) {
					target = ev.devourerTarget(orbit, d);
				}
				if (target == null) {
					target = fighters.get(orbit.random.nextInt(fighters.size()));
				}
				Vec3 at = new Vec3(target.getX(), GalactusOrbit.PLANE_Y + 1.1, target.getZ());
				Vec3 fromHim = at.subtract(d.getX(), at.y, d.getZ()).normalize();
				Vec3 perp = new Vec3(-fromHim.z, 0, fromHim.x);
				beamFrom = clampToHull(o, at.add(perp.scale(20)));
				beamTo = clampToHull(o, at.add(perp.scale(-20)));
				beamState = 1;
				beamAt = now;
				beamHit.clear();
				d.setEyes(0.8f);
				orbit.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 4.0f, 0.5f);
			}
			case 1 -> {
				if (now % 3 == 0) {
					GalactusFx.line(orbit, GalactusFx.EMBER, beamFrom, beamTo, 1.0);
				}
				d.setLook(beamFrom);
				if (now - beamAt >= c.beamTelegraphTicks) {
					beamState = 2;
					beamAt = now;
				}
			}
			default -> {
				double k = Mth.clamp((now - beamAt) / (double) c.beamSweepTicks, 0, 1);
				Vec3 point = beamFrom.lerp(beamTo, k);
				Vec3 face = d.pose().face;
				d.setEyes(1f);
				d.setLook(point);
				GalactusFx.line(orbit, GalactusFx.COSMIC, face, point, 3.0);
				GalactusFx.forced(orbit, ParticleTypes.FLAME, point.x, point.y, point.z, 8, 0.6, 0.2, 0.6, 0.02);
				for (ServerPlayer p : fighters) {
					if (beamHit.contains(p.getUUID())) {
						continue;
					}
					double dx = p.getX() - point.x, dz = p.getZ() - point.z;
					if (dx * dx + dz * dz < 2.5 * 2.5 && p.getY() < GalactusOrbit.PLANE_Y + 4) {
						beamHit.add(p.getUUID());
						GalactusCombat.hit(orbit, d, p, (float) c.beamDamage);
						p.igniteForSeconds(4);
					}
				}
				if (now % 4 == 0) {
					orbit.playSound(null, point.x, point.y, point.z, SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 2.0f, 0.5f);
				}
				if (k >= 1) {
					beamState = 0;
					beamCooldown = c.beamCooldownTicks + orbit.random.nextInt(40);
				}
			}
		}
	}

	private static Vec3 clampToHull(BlockPos o, Vec3 p) {
		double dx = p.x - (o.getX() + 0.5), dz = p.z - (o.getZ() + 0.5);
		double r = Math.sqrt(dx * dx + dz * dz);
		double max = OrbitArena.HULL_R - 1;
		if (r <= max) {
			return p;
		}
		return new Vec3(o.getX() + 0.5 + dx / r * max, p.y, o.getZ() + 0.5 + dz / r * max);
	}

	// ---------------------------------------------------------------- save

	public CompoundTag save() {
		CompoundTag t = new CompoundTag();
		t.putInt("Total", total);
		t.putInt("Reversed", reversed);
		return t;
	}

	public void load(CompoundTag t) {
		total = t.getInt("Total");
		reversed = t.getInt("Reversed");
		converters.clear();
		overload.clear();
	}

	/** Every cell (loose or carried) goes when the stage ends. */
	public void clearCells(ServerLevel orbit, List<ServerPlayer> players) {
		for (UUID u : cells) {
			if (orbit.getEntity(u) instanceof ItemEntity ie) {
				ie.discard();
			}
		}
		cells.clear();
		for (ServerPlayer p : players) {
			for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
				if (p.getInventory().getItem(i).is(GalactusItems.COSMIC_CELL)) {
					p.getInventory().setItem(i, ItemStack.EMPTY);
				}
			}
		}
	}
}
