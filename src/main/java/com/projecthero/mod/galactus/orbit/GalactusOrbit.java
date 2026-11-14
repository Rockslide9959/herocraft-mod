package com.projecthero.mod.galactus.orbit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.galactus.GalactusEvent;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 Galactus raid v2: the orbit -- {@code projecthero:galactus_orbit}, a void datapack dimension (fixed night, no
 * weather, no spawns) drawn with its own sky ({@code GalactusOrbitSky}: stars, nebulae, the Earth, the sun). Each running
 * raid gets its own <b>instance</b>, {@link #SPACING} blocks apart along X, so two raids never meet; an instance is wiped
 * (every non-air section in its box, every non-player entity) before it is built and after the raid ends, so a crash can
 * never leave islands behind.
 *
 * <p>Also owned here: the per-player <b>return point</b> (where you were lifted from; a logout in orbit after the raid has
 * ended sends you back there) and the <b>grave</b> (items of a fighter who dies in orbit are kept, not dropped into the
 * void, and handed back on respawn). Both are saved ({@link Store}, overworld data).
 */
public final class GalactusOrbit {
	public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION, ProjectHeroMod.id("galactus_orbit"));
	public static final ResourceLocation EFFECTS = ProjectHeroMod.id("galactus_orbit");
	/** The island plane (the top of every island and of the hull). */
	public static final int PLANE_Y = 128;
	public static final int SPACING = 20000;
	/** Half the width of an instance's box (blocks), and its height range. */
	public static final int HALF = 112;
	public static final int MIN_Y = 24;
	public static final int MAX_Y = 240;
	/** The chunk ticket radius that keeps an instance loaded (not saved: re-added when the raid ticks after a restart). */
	private static final int TICKET_RADIUS = 8;
	private static final TicketType<ChunkPos> TICKET = TicketType.create("projecthero_galactus_orbit", Comparator.comparingLong(ChunkPos::toLong));

	private GalactusOrbit() {
	}

	private static final ResourceLocation GRAVE_PHASE = ProjectHeroMod.id("galactus_orbit_grave");

	/** Vanilla's totem check runs after the death hooks: leave the inventory alone if a held totem is about to save them. */
	public static boolean totemWillSave(ServerPlayer p, net.minecraft.world.damagesource.DamageSource source) {
		if (source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return false;
		}
		return p.getMainHandItem().is(net.minecraft.world.item.Items.TOTEM_OF_UNDYING) || p.getOffhandItem().is(net.minecraft.world.item.Items.TOTEM_OF_UNDYING);
	}

	public static void initialize() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> onJoin(handler.getPlayer())));
		// v0.17.4: the grave is taken LAST, once every other death hook has agreed to the death. It used to run first: a power
		// that cancels death (a death save) then left the player alive with an empty inventory (the items sat in a grave
		// that is only handed back on respawn), and a Totem of Undying was stashed before it could fire, so it never did.
		ServerLivingEntityEvents.ALLOW_DEATH.addPhaseOrdering(net.fabricmc.fabric.api.event.Event.DEFAULT_PHASE, GRAVE_PHASE);
		ServerLivingEntityEvents.ALLOW_DEATH.register(GRAVE_PHASE, (entity, source, amount) -> {
			if (entity instanceof ServerPlayer p && inOrbit(p) && !totemWillSave(p, source)) {
				stashOnDeath(p);
			}
			return true;
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			if (!alive) {
				returnGrave(newPlayer);
			}
		});
	}

	// ---------------------------------------------------------------- geometry

	public static ServerLevel level(MinecraftServer server) {
		return server.getLevel(DIMENSION);
	}

	public static boolean inOrbit(Entity e) {
		return e.level().dimension() == DIMENSION;
	}

	/** The centre of instance {@code slot}: the Anchor island's top. */
	public static BlockPos origin(int slot) {
		return new BlockPos(SPACING * (slot + 1), PLANE_Y, 0);
	}

	public static int slotOf(double x) {
		return (int) Math.round(x / SPACING) - 1;
	}

	public static AABB box(int slot) {
		BlockPos o = origin(slot);
		return new AABB(o.getX() - HALF, MIN_Y, o.getZ() - HALF, o.getX() + HALF + 1, MAX_Y, o.getZ() + HALF + 1);
	}

	/** The lowest instance slot no running Galactus raid holds. */
	public static int freeSlot(MinecraftServer server) {
		java.util.Set<Integer> used = new java.util.HashSet<>();
		for (GalactusEvent e : GalactusEvent.all(server)) {
			if (e.orbitSlot() >= 0) {
				used.add(e.orbitSlot());
			}
		}
		int s = 0;
		while (used.contains(s)) {
			s++;
		}
		return s;
	}

	public static GalactusEvent eventForSlot(MinecraftServer server, int slot) {
		for (GalactusEvent e : GalactusEvent.all(server)) {
			if (e.orbitSlot() == slot) {
				return e;
			}
		}
		return null;
	}

	// ---------------------------------------------------------------- loading and wiping

	/** Keeps the instance's chunks loaded (and loads them now). */
	public static void hold(ServerLevel orbit, int slot) {
		BlockPos o = origin(slot);
		ChunkPos c = new ChunkPos(o);
		orbit.getChunkSource().addRegionTicket(TICKET, c, TICKET_RADIUS, c);
		int r = (HALF >> 4) + 1;
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				orbit.getChunk(c.x + dx, c.z + dz);
			}
		}
	}

	public static void release(ServerLevel orbit, int slot) {
		ChunkPos c = new ChunkPos(origin(slot));
		orbit.getChunkSource().removeRegionTicket(TICKET, c, TICKET_RADIUS, c);
	}

	/**
	 * Wipes the instance: every block in its box back to air (sections that hold only air are skipped, so an empty instance
	 * costs almost nothing) and every non-player entity removed. Returns the blocks cleared.
	 */
	public static int wipe(ServerLevel orbit, int slot) {
		BlockPos o = origin(slot);
		int minCx = (o.getX() - HALF) >> 4, maxCx = (o.getX() + HALF) >> 4;
		int minCz = (o.getZ() - HALF) >> 4, maxCz = (o.getZ() + HALF) >> 4;
		BlockState air = Blocks.AIR.defaultBlockState();
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		int cleared = 0;
		for (int cx = minCx; cx <= maxCx; cx++) {
			for (int cz = minCz; cz <= maxCz; cz++) {
				LevelChunk chunk = orbit.getChunk(cx, cz);
				LevelChunkSection[] sections = chunk.getSections();
				for (int si = 0; si < sections.length; si++) {
					LevelChunkSection s = sections[si];
					if (s == null || s.hasOnlyAir()) {
						continue;
					}
					int baseY = orbit.getSectionYFromSectionIndex(si) << 4;
					for (int y = 0; y < 16; y++) {
						for (int z = 0; z < 16; z++) {
							for (int x = 0; x < 16; x++) {
								if (!s.getBlockState(x, y, z).isAir()) {
									m.set((cx << 4) + x, baseY + y, (cz << 4) + z);
									orbit.setBlock(m, air, 2 | 16);
									cleared++;
								}
							}
						}
					}
				}
			}
		}
		for (Entity e : orbit.getEntities((Entity) null, box(slot), e -> !(e instanceof ServerPlayer))) {
			e.discard();
		}
		return cleared;
	}

	// ---------------------------------------------------------------- travel

	/** Into the orbit at {@code at}; the first time (from another dimension) their return point is saved. */
	public static void sendIn(ServerPlayer p, ServerLevel orbit, Vec3 at, float yaw) {
		if (!inOrbit(p)) {
			Store.get(p.server).setReturn(p.getUUID(), p.level().dimension(), p.position());
		}
		p.fallDistance = 0;
		p.teleportTo(orbit, at.x, at.y, at.z, yaw, 0f);
		p.setDeltaMovement(Vec3.ZERO);
		p.hurtMarked = true;
	}

	/** Back to where they were lifted from (or the world spawn). */
	public static void sendHome(ServerPlayer p) {
		Store store = Store.get(p.server);
		Store.Return r = store.takeReturn(p.getUUID());
		ServerLevel to = r == null ? null : p.server.getLevel(r.dimension());
		Vec3 at;
		if (to == null || to.dimension() == DIMENSION) {
			to = p.server.overworld();
			BlockPos s = to.getSharedSpawnPos();
			at = Vec3.atBottomCenterOf(to.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, s));
		} else {
			at = r.pos();
		}
		p.fallDistance = 0;
		p.teleportTo(to, at.x, at.y, at.z, p.getYRot(), 0f);
		p.setDeltaMovement(Vec3.ZERO);
	}

	/** A player logging in up here: back into their raid if it is still going, else home. */
	static void onJoin(ServerPlayer p) {
		if (!inOrbit(p)) {
			return;
		}
		GalactusEvent e = eventForSlot(p.server, slotOf(p.getX()));
		if (e != null && e.isFighter(p.getUUID()) && e.orbitActive()) {
			e.rejoinOrbit(p);
		} else {
			sendHome(p);
		}
	}

	/** Every few seconds: anyone up here whose raid has gone is sent home. */
	/** Test hook: the stray sweep off (harnesses that build an instance with no raid). */
	public static boolean sweepOff;

	public static void sweepStrays(MinecraftServer server) {
		if (sweepOff) {
			return;
		}
		ServerLevel orbit = level(server);
		if (orbit == null) {
			return;
		}
		for (ServerPlayer p : new ArrayList<>(orbit.players())) {
			if (p.isSpectator() && p.isCreative()) {
				continue;
			}
			GalactusEvent e = eventForSlot(server, slotOf(p.getX()));
			if (e == null || !e.orbitActive()) {
				sendHome(p);
			}
		}
	}

	// ---------------------------------------------------------------- the grave

	public static void stashOnDeath(ServerPlayer p) {
		if (p.serverLevel().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY) || p.isSpectator()) {
			return;
		}
		Inventory inv = p.getInventory();
		List<ItemStack> kept = new ArrayList<>();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (s.isEmpty()) {
				continue;
			}
			inv.setItem(i, ItemStack.EMPTY);
			if (s.is(com.projecthero.mod.galactus.item.GalactusItems.ULTIMATE_NULLIFIER)) {
				// the Nullifier stays in the fight: it drops on the deck for someone else to pick up
				BlockPos o = origin(slotOf(p.getX()));
				net.minecraft.world.entity.item.ItemEntity ie = new net.minecraft.world.entity.item.ItemEntity(p.serverLevel(), o.getX() + 0.5, o.getY() + 2,
						o.getZ() + 0.5, s.copy());
				ie.setUnlimitedLifetime();
				ie.setInvulnerable(true);
				ie.setGlowingTag(true);
				p.serverLevel().addFreshEntity(ie);
			} else if (!s.is(com.projecthero.mod.galactus.item.GalactusItems.COSMIC_CELL)
					&& !s.is(com.projecthero.mod.galactus.item.GalactusItems.NULLIFIER_PART)) {
				kept.add(s.copy());
			}
		}
		if (!kept.isEmpty()) {
			Store.get(p.server).addGrave(p.getUUID(), kept);
		}
	}

	public static void returnGrave(ServerPlayer p) {
		List<ItemStack> items = Store.get(p.server).takeGrave(p.getUUID());
		if (items.isEmpty()) {
			return;
		}
		for (ItemStack s : items) {
			if (!p.getInventory().add(s)) {
				p.drop(s, false);
			}
		}
		p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.projecthero.galactus.grave_returned")
				.withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE), false);
	}

	/** Test hook: a player's waiting grave (copied). */
	public static List<ItemStack> grave(ServerPlayer p) {
		return new ArrayList<>(Store.get(p.server).graves.getOrDefault(p.getUUID(), List.of()));
	}

	// ---------------------------------------------------------------- saved data

	public static final class Store extends SavedData {
		private static final String FILE = "projecthero_galactus_orbit";
		private static final SavedData.Factory<Store> FACTORY = new SavedData.Factory<>(Store::new, Store::load, DataFixTypes.LEVEL);

		public record Return(ResourceKey<Level> dimension, Vec3 pos) {
		}

		final Map<UUID, Return> returns = new HashMap<>();
		final Map<UUID, List<ItemStack>> graves = new HashMap<>();
		private HolderLookup.Provider lookup;

		public static Store get(MinecraftServer server) {
			Store s = server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE);
			s.lookup = server.registryAccess();
			return s;
		}

		public void setReturn(UUID id, ResourceKey<Level> dim, Vec3 pos) {
			returns.put(id, new Return(dim, pos));
			setDirty();
		}

		public Return takeReturn(UUID id) {
			Return r = returns.remove(id);
			if (r != null) {
				setDirty();
			}
			return r;
		}

		public boolean hasReturn(UUID id) {
			return returns.containsKey(id);
		}

		void addGrave(UUID id, List<ItemStack> items) {
			graves.computeIfAbsent(id, k -> new ArrayList<>()).addAll(items);
			setDirty();
		}

		List<ItemStack> takeGrave(UUID id) {
			List<ItemStack> l = graves.remove(id);
			if (l != null) {
				setDirty();
			}
			return l == null ? List.of() : l;
		}

		private static Store load(CompoundTag tag, HolderLookup.Provider registries) {
			Store s = new Store();
			ListTag rl = tag.getList("Returns", Tag.TAG_COMPOUND);
			for (int i = 0; i < rl.size(); i++) {
				CompoundTag t = rl.getCompound(i);
				ResourceLocation dim = ResourceLocation.tryParse(t.getString("Dim"));
				if (dim == null || !t.hasUUID("Id")) {
					continue;
				}
				s.returns.put(t.getUUID("Id"), new Return(ResourceKey.create(Registries.DIMENSION, dim),
						new Vec3(t.getDouble("X"), t.getDouble("Y"), t.getDouble("Z"))));
			}
			ListTag gl = tag.getList("Graves", Tag.TAG_COMPOUND);
			for (int i = 0; i < gl.size(); i++) {
				CompoundTag t = gl.getCompound(i);
				if (!t.hasUUID("Id")) {
					continue;
				}
				List<ItemStack> items = new ArrayList<>();
				ListTag il = t.getList("Items", Tag.TAG_COMPOUND);
				for (int j = 0; j < il.size(); j++) {
					ItemStack.parse(registries, il.getCompound(j)).ifPresent(items::add);
				}
				if (!items.isEmpty()) {
					s.graves.put(t.getUUID("Id"), items);
				}
			}
			return s;
		}

		@Override
		public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
			ListTag rl = new ListTag();
			returns.forEach((id, r) -> {
				CompoundTag t = new CompoundTag();
				t.putUUID("Id", id);
				t.putString("Dim", r.dimension().location().toString());
				t.putDouble("X", r.pos().x);
				t.putDouble("Y", r.pos().y);
				t.putDouble("Z", r.pos().z);
				rl.add(t);
			});
			tag.put("Returns", rl);
			ListTag gl = new ListTag();
			graves.forEach((id, items) -> {
				CompoundTag t = new CompoundTag();
				t.putUUID("Id", id);
				ListTag il = new ListTag();
				for (ItemStack s : items) {
					if (!s.isEmpty()) {
						il.add(s.save(registries));
					}
				}
				t.put("Items", il);
				gl.add(t);
			});
			tag.put("Graves", gl);
			return tag;
		}
	}
}
