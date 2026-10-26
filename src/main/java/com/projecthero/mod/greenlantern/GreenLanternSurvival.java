package com.projecthero.mod.greenlantern;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.projecthero.mod.greenlantern.block.GreenLanternSurvivalBlocks;
import com.projecthero.mod.greenlantern.block.HardLightChestBlock;
import com.projecthero.mod.greenlantern.block.HardLightScaffoldBlock;
import com.projecthero.mod.greenlantern.construct.Construct;
import com.projecthero.mod.greenlantern.construct.ConstructType;
import com.projecthero.mod.greenlantern.construct.GreenLanternConstructs;
import com.projecthero.mod.greenlantern.data.GreenLanternFx;
import com.projecthero.mod.greenlantern.entity.HardLightConstructEntity;
import com.projecthero.mod.greenlantern.entity.HardLightConstructEntity.Shape;
import com.projecthero.mod.hero.power.AbilityHelpers;

import org.joml.Vector3f;

import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1: the Green Lantern's four SURVIVAL constructs ({@link ConstructType.Kind#SURVIVAL}), bound from the Construct
 * Menu's Building &amp; Utility section like every other construct:
 * <ul>
 *   <li><b>Hard-Light Chest</b> -- a glowing 27-slot chest where you aim. Only you and your squad can open it. It lasts
 *   until N, an empty ring or being punched apart ({@link GreenLanternConfig#CHEST_HITS} hits) -- and whichever way it
 *   ends, everything in it spills onto the ground. Upkeep per second; one at a time.</li>
 *   <li><b>Hard-Light Scaffold Tower</b> -- aimed at the ground: a column of climbable hard light up to
 *   {@link GreenLanternConfig#SCAFFOLD_HEIGHT} blocks (Space climbs, sneak sinks, like vanilla scaffolding) with a 3x3
 *   deck on top. Upkeep while it stands; pressing it again lets it go.</li>
 *   <li><b>Item Net</b> -- a net of light sweeps out {@link GreenLanternConfig#NET_RADIUS} blocks and reels every dropped
 *   item and XP orb in to you over three seconds, then dissolves. One-shot cost + cooldown.</li>
 *   <li><b>Hard-Light Tunneller</b> -- a giant drill-headed pickaxe bores a 3x3 tunnel up to
 *   {@link GreenLanternConfig#TUNNEL_LENGTH} blocks along your aim (snapped to the nearest straight line). Every block goes
 *   through the same break path as the Mining Drill ({@code ServerPlayerGameMode#destroyBlock}, so protection mods,
 *   spawn protection and adventure mode all apply) with a hard-light diamond pickaxe, and drops as items. Costs Ring
 *   Charge per block; it stops when it hits something unbreakable or the ring runs low.</li>
 * </ul>
 * Every cost goes through {@link GreenLanternEnergy#spend} (the emergency reserve, Lantern Rank discounts and the Oath's
 * double cost all apply). N, death, logout and dimension changes end everything ({@link #dismissAll}).
 */
public final class GreenLanternSurvival {
	private static final ParticleOptions GREEN_DUST = new DustParticleOptions(new Vector3f(0.208f, 0.941f, 0.459f), 1.4f);
	private static final ParticleOptions SPARK = new DustParticleOptions(new Vector3f(0.55f, 1.0f, 0.62f), 0.9f);
	public static final String NET_COOLDOWN_ID = "construct_item_net";

	/** One standing chest. Its items live here, not in a block entity. */
	public static final class Chest {
		public final UUID owner;
		public final ServerLevel level;
		public final BlockPos pos;
		final BlockState previous;
		final BlockState placed;
		public final LightContainer items;
		int hits;
		boolean ended;

		Chest(UUID owner, ServerLevel level, BlockPos pos, BlockState previous, BlockState placed) {
			this.owner = owner;
			this.level = level;
			this.pos = pos;
			this.previous = previous;
			this.placed = placed;
			this.items = new LightContainer(this);
		}
	}

	/** The chest's 27 slots: only valid while the chest stands and the viewer is within reach. */
	public static final class LightContainer extends SimpleContainer {
		private final Chest chest;

		LightContainer(Chest chest) {
			super(27);
			this.chest = chest;
		}

		@Override
		public boolean stillValid(Player player) {
			return !chest.ended && player.level() == chest.level
					&& player.distanceToSqr(Vec3.atCenterOf(chest.pos)) <= GreenLanternConfig.CHEST_REACH * GreenLanternConfig.CHEST_REACH;
		}

		@Override
		public void startOpen(Player player) {
			sound(chest.level, Vec3.atCenterOf(chest.pos), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.7f, 1.6f);
		}

		@Override
		public void stopOpen(Player player) {
			sound(chest.level, Vec3.atCenterOf(chest.pos), SoundEvents.AMETHYST_BLOCK_CHIME, 0.7f, 1.3f);
		}
	}

	/** One standing scaffold tower: its cells in build order (column bottom-up, then the deck). */
	public static final class Tower {
		public final UUID owner;
		public final ServerLevel level;
		public final List<Construct.Cell> cells = new ArrayList<>();
		public int built;
		public int columnHeight;

		Tower(UUID owner, ServerLevel level) {
			this.owner = owner;
			this.level = level;
		}
	}

	private static final Map<UUID, Chest> CHESTS = new ConcurrentHashMap<>();
	private static final Map<UUID, Tower> TOWERS = new ConcurrentHashMap<>();
	/** Player -> the net / tunneller entities still running (so N stops them). */
	private static final Map<UUID, List<HardLightConstructEntity>> ACTIVE = new ConcurrentHashMap<>();
	/** Every hard-light cell these constructs own, per dimension (the blocks' orphan check, the punch / open hooks). */
	private static final Map<ResourceKey<Level>, Map<BlockPos, Object>> CELLS = new ConcurrentHashMap<>();

	private GreenLanternSurvival() {
	}

	public static void initialize() {
		GreenLanternSurvivalBlocks.initialize();
		// right-click a Hard-Light Chest: open it (owner and squad only)
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level.isClientSide()) {
				// the client never places a held block against a chest of light (the server opens it)
				return hand == InteractionHand.MAIN_HAND && level.getBlockState(hit.getBlockPos()).is(GreenLanternSurvivalBlocks.HARD_LIGHT_CHEST)
						? InteractionResult.SUCCESS : InteractionResult.PASS;
			}
			if (hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp)) {
				return InteractionResult.PASS;
			}
			if (cellOwner(level, hit.getBlockPos()) instanceof Chest chest) {
				open(sp, chest);
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});
		// punching a chest chips at it (it spills when it breaks); tower cells just shrug a punch off
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
			if (!(player instanceof ServerPlayer sp)) {
				return InteractionResult.PASS;
			}
			Object owner = cellOwner(level, pos);
			if (owner instanceof Chest chest) {
				punch(sp, chest);
				return InteractionResult.SUCCESS;
			}
			return owner != null ? InteractionResult.SUCCESS : InteractionResult.PASS;
		});
		// never mined, not even in creative: a break attempt on a chest is a punch, on a tower nothing
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, be) -> {
			Object owner = cellOwner(level, pos);
			if (owner == null) {
				return true;
			}
			if (owner instanceof Chest chest && player instanceof ServerPlayer sp) {
				punch(sp, chest);
			}
			return false;
		});
	}

	/** Server stop: nothing in the maps survives. (Chests have already spilled -- players leave before the save.) */
	public static void clearSessionState() {
		CHESTS.clear();
		TOWERS.clear();
		ACTIVE.clear();
		CELLS.clear();
	}

	public static boolean isTrackedCell(ServerLevel level, BlockPos pos) {
		Map<BlockPos, Object> m = CELLS.get(level.dimension());
		return m != null && m.containsKey(pos);
	}

	private static Object cellOwner(Level level, BlockPos pos) {
		Map<BlockPos, Object> m = CELLS.get(level.dimension());
		return m == null ? null : m.get(pos);
	}

	private static void index(ServerLevel level, BlockPos pos, Object owner) {
		CELLS.computeIfAbsent(level.dimension(), k -> new ConcurrentHashMap<>()).put(pos.immutable(), owner);
	}

	private static void unindex(ServerLevel level, BlockPos pos, Object owner) {
		Map<BlockPos, Object> m = CELLS.get(level.dimension());
		if (m != null) {
			m.remove(pos, owner);
		}
	}

	public static Chest chest(UUID owner) {
		return CHESTS.get(owner);
	}

	public static Tower tower(UUID owner) {
		return TOWERS.get(owner);
	}

	public static List<HardLightConstructEntity> active(UUID owner) {
		return ACTIVE.getOrDefault(owner, List.of());
	}

	// ---------------------------------------------------------------- deploy

	public static void deploy(ServerPlayer p, ConstructType type) {
		switch (type) {
			case HARD_LIGHT_CHEST -> deployChest(p);
			case SCAFFOLD_TOWER -> deployTower(p);
			case ITEM_NET -> deployNet(p);
			case TUNNELLER -> deployTunneller(p);
			default -> {
			}
		}
	}

	/** N / death / logout / dimension change: everything ends; a chest spills. */
	public static void dismissAll(UUID owner) {
		Chest chest = CHESTS.get(owner);
		if (chest != null) {
			endChest(chest);
		}
		Tower tower = TOWERS.get(owner);
		if (tower != null) {
			endTower(tower);
		}
		List<HardLightConstructEntity> list = ACTIVE.remove(owner);
		if (list != null) {
			for (HardLightConstructEntity e : list) {
				if (!e.isRemoved()) {
					dissolve(e);
					e.discard();
				}
			}
		}
	}

	private static void keep(ServerPlayer p, HardLightConstructEntity e) {
		ACTIVE.computeIfAbsent(p.getUUID(), k -> new CopyOnWriteArrayList<>()).add(e);
	}

	/** Per player per tick: upkeeps, the tower building itself, a chest whose block was replaced. */
	public static void tick(ServerPlayer p) {
		UUID id = p.getUUID();
		Chest chest = CHESTS.get(id);
		if (chest != null) {
			if (chest.level.hasChunkAt(chest.pos) && chest.level.getBlockState(chest.pos) != chest.placed) {
				endChest(chest); // something replaced it (a command, another mod): it still spills
			} else if (!GreenLanternEnergy.drainTick(p, GreenLanternConfig.CHEST_UPKEEP_PER_SEC / 20f)) {
				GreenLanternEnergy.feedback(p, "message.projecthero.ability.low_charge");
				endChest(chest);
			}
		}
		Tower tower = TOWERS.get(id);
		if (tower != null) {
			buildTower(tower, GreenLanternConfig.SCAFFOLD_BUILD_PER_TICK);
			if (!GreenLanternEnergy.drainTick(p, GreenLanternConfig.SCAFFOLD_UPKEEP_PER_SEC / 20f)) {
				GreenLanternEnergy.feedback(p, "message.projecthero.ability.low_charge");
				endTower(tower);
			}
		}
		List<HardLightConstructEntity> list = ACTIVE.get(id);
		if (list != null) {
			list.removeIf(Entity::isRemoved);
			if (list.isEmpty()) {
				ACTIVE.remove(id, list);
			}
		}
	}

	/** From {@code GreenLanternArsenal#tickEntity} for the survival shapes. */
	public static void tickEntity(HardLightConstructEntity e, ServerPlayer owner) {
		switch (e.shape()) {
			case ITEM_NET -> tickNet(e, owner);
			case TUNNELLER -> tickTunneller(e, owner);
			default -> {
			}
		}
	}

	// ---------------------------------------------------------------- Hard-Light Chest

	private static void deployChest(ServerPlayer p) {
		if (CHESTS.containsKey(p.getUUID())) {
			GreenLanternEnergy.feedback(p, "message.projecthero.green_lantern.already_out");
			return;
		}
		ServerLevel level = p.serverLevel();
		BlockPos pos = chestSpot(p);
		if (pos == null) {
			GreenLanternEnergy.feedback(p, "message.projecthero.ability.invalid_target");
			return;
		}
		if (!pay(p, GreenLanternConfig.CHEST_COST)) {
			return;
		}
		BlockState previous = level.getBlockState(pos);
		BlockState placed = GreenLanternSurvivalBlocks.HARD_LIGHT_CHEST.defaultBlockState()
				.setValue(HardLightChestBlock.FACING, p.getDirection().getOpposite());
		Chest chest = new Chest(p.getUUID(), level, pos.immutable(), previous, placed);
		CHESTS.put(p.getUUID(), chest);
		index(level, chest.pos, chest);
		level.setBlock(chest.pos, placed, Block.UPDATE_ALL);
		Vec3 c = Vec3.atCenterOf(chest.pos);
		AbilityHelpers.line(level, AbilityHelpers.handPosition(p), c, SPARK, 1.5);
		level.sendParticles(GREEN_DUST, c.x, c.y, c.z, 24, 0.4, 0.35, 0.4, 0.02);
		constructFx(p);
		sound(level, c, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 1.2f);
		sound(level, c, SoundEvents.CHEST_LOCKED, 0.5f, 1.6f);
	}

	/** The air block where the aim lands (within reach), else the one in front of your feet; null if neither is free. */
	private static BlockPos chestSpot(ServerPlayer p) {
		ServerLevel level = p.serverLevel();
		BlockHitResult hit = AbilityHelpers.raycastBlock(p, GreenLanternConfig.CHEST_RANGE);
		if (hit.getType() != HitResult.Type.MISS) {
			BlockPos at = level.getBlockState(hit.getBlockPos()).canBeReplaced() ? hit.getBlockPos()
					: hit.getBlockPos().relative(hit.getDirection());
			if (canPlace(p, at, true)) {
				return at;
			}
		}
		BlockPos front = p.blockPosition().relative(p.getDirection());
		return canPlace(p, front, true) ? front : null;
	}

	/** Opens the chest for its owner or a squadmate; anyone else is told it is not theirs. */
	public static boolean open(ServerPlayer viewer, Chest chest) {
		if (chest.ended) {
			return false;
		}
		ServerPlayer owner = chest.level.getServer().getPlayerList().getPlayer(chest.owner);
		boolean allowed = viewer.getUUID().equals(chest.owner)
				|| owner != null && com.projecthero.mod.squad.Squads.areAllies(owner, viewer);
		if (!allowed) {
			viewer.displayClientMessage(Component.translatable("message.projecthero.green_lantern.chest_not_yours"), true);
			sound(chest.level, Vec3.atCenterOf(chest.pos), SoundEvents.CHEST_LOCKED, 0.6f, 1.4f);
			return false;
		}
		viewer.openMenu(new SimpleMenuProvider((id, inv, pl) -> ChestMenu.threeRows(id, inv, chest.items),
				Component.translatable("container.projecthero.hard_light_chest")));
		return true;
	}

	private static void punch(ServerPlayer by, Chest chest) {
		if (chest.ended) {
			return;
		}
		chest.hits++;
		Vec3 c = Vec3.atCenterOf(chest.pos);
		chest.level.sendParticles(SPARK, c.x, c.y, c.z, 6, 0.3, 0.3, 0.3, 0.05);
		sound(chest.level, c, SoundEvents.AMETHYST_BLOCK_HIT, 0.9f, 0.8f + chest.hits * 0.1f);
		if (chest.hits >= GreenLanternConfig.CHEST_HITS) {
			sound(chest.level, c, SoundEvents.GLASS_BREAK, 0.8f, 0.9f);
			endChest(chest);
		}
	}

	/** The chest dissolves and EVERYTHING in it lands on the ground -- nothing is ever deleted. */
	public static void endChest(Chest chest) {
		if (chest.ended) {
			return;
		}
		chest.ended = true;
		CHESTS.remove(chest.owner, chest);
		unindex(chest.level, chest.pos, chest);
		// anyone looking inside is shown out first, so nothing is left on a cursor in a dead menu
		for (ServerPlayer viewer : chest.level.getServer().getPlayerList().getPlayers()) {
			if (viewer.containerMenu instanceof ChestMenu menu && menu.getContainer() == chest.items) {
				viewer.closeContainer();
			}
		}
		if (chest.level.hasChunkAt(chest.pos) && chest.level.getBlockState(chest.pos) == chest.placed) {
			chest.level.setBlock(chest.pos, chest.previous, Block.UPDATE_ALL); // first, so the items drop into open air
		}
		ServerPlayer owner = chest.level.getServer().getPlayerList().getPlayer(chest.owner);
		if (chest.level.hasChunkAt(chest.pos) || owner == null) {
			chest.level.getChunk(chest.pos); // load it if its owner is gone too: the items are dropped and saved there
			Containers.dropContents(chest.level, chest.pos, chest.items);
		} else {
			// its chunk is unloaded (the Lantern wandered off): the light brings the contents to its owner instead
			Containers.dropContents(owner.level(), owner.blockPosition(), chest.items);
		}
		Vec3 c = Vec3.atCenterOf(chest.pos);
		chest.level.sendParticles(SPARK, c.x, c.y, c.z, 22, 0.4, 0.35, 0.4, 0.05);
		sound(chest.level, c, SoundEvents.AMETHYST_CLUSTER_BREAK, 0.8f, 1.3f);
	}

	// ---------------------------------------------------------------- Scaffold Tower

	private static void deployTower(ServerPlayer p) {
		Tower old = TOWERS.get(p.getUUID());
		if (old != null) {
			endTower(old); // pressing it again lets the tower go (free)
			sound(p.serverLevel(), p.position(), SoundEvents.BEACON_DEACTIVATE, 0.5f, 1.4f);
			return;
		}
		ServerLevel level = p.serverLevel();
		BlockHitResult hit = AbilityHelpers.raycastBlock(p, GreenLanternConfig.SCAFFOLD_RANGE);
		if (hit.getType() == HitResult.Type.MISS) {
			GreenLanternEnergy.feedback(p, "message.projecthero.ability.invalid_target");
			return;
		}
		BlockPos base = level.getBlockState(hit.getBlockPos()).canBeReplaced() ? hit.getBlockPos()
				: hit.getBlockPos().relative(hit.getDirection());
		// settle onto the ground under the aim point
		for (int i = 0; i < GreenLanternConfig.SCAFFOLD_RANGE && canPlace(p, base.below(), false)
				&& base.getY() > level.getMinBuildHeight(); i++) {
			base = base.below();
		}
		Tower t = new Tower(p.getUUID(), level);
		planTower(p, t, base);
		if (t.columnHeight < 2) {
			GreenLanternEnergy.feedback(p, "message.projecthero.ability.invalid_target");
			return;
		}
		if (!pay(p, GreenLanternConfig.SCAFFOLD_COST)) {
			return;
		}
		TOWERS.put(p.getUUID(), t);
		buildTower(t, GreenLanternConfig.SCAFFOLD_BUILD_PER_TICK);
		AbilityHelpers.line(level, AbilityHelpers.handPosition(p), Vec3.atBottomCenterOf(base), SPARK, 1.5);
		constructFx(p);
		sound(level, Vec3.atCenterOf(base), SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 1.0f);
		sound(level, p.position(), SoundEvents.BEACON_AMBIENT, 0.5f, 1.4f);
	}

	/** The column from {@code base} up (stopping under anything in the way) and the deck round its top cell. */
	static void planTower(ServerPlayer p, Tower t, BlockPos base) {
		BlockState column = GreenLanternSurvivalBlocks.HARD_LIGHT_SCAFFOLD.defaultBlockState();
		BlockState deck = column.setValue(HardLightScaffoldBlock.PLATFORM, true);
		BlockPos top = null;
		for (int i = 0; i < GreenLanternConfig.SCAFFOLD_HEIGHT; i++) {
			BlockPos at = base.above(i);
			if (at.getY() >= t.level.getMaxBuildHeight() || !canPlace(p, at, false)) {
				break;
			}
			t.cells.add(new Construct.Cell(at.immutable(), t.level.getBlockState(at), column));
			top = at;
		}
		t.columnHeight = t.cells.size();
		if (top == null || t.columnHeight < 2) {
			return;
		}
		for (Direction d : new Direction[] { Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST }) {
			BlockPos side = top.relative(d);
			BlockPos corner = side.relative(d.getClockWise());
			for (BlockPos at : new BlockPos[] { side, corner }) {
				if (canPlace(p, at, true)) {
					t.cells.add(new Construct.Cell(at.immutable(), t.level.getBlockState(at), deck));
				}
			}
		}
	}

	/** Places the next {@code count} cells (skipping any whose spot has changed since the tower was planned). */
	private static void buildTower(Tower t, int count) {
		int end = Math.min(t.cells.size(), t.built + (t.built >= t.columnHeight ? t.cells.size() : count));
		for (int i = t.built; i < end; i++) {
			Construct.Cell cell = t.cells.get(i);
			BlockPos pos = cell.pos();
			if (!t.level.hasChunkAt(pos) || t.level.getBlockState(pos) != cell.previous()) {
				continue;
			}
			boolean deck = cell.placed().getValue(HardLightScaffoldBlock.PLATFORM);
			if (deck && playerIn(t.level, pos)) {
				continue;
			}
			index(t.level, pos, t);
			t.level.setBlock(pos, cell.placed(), Block.UPDATE_ALL);
			t.level.sendParticles(SPARK, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 2, 0.3, 0.3, 0.3, 0.01);
		}
		if (end > t.built && end == t.cells.size()) {
			Construct.Cell last = t.cells.get(t.columnHeight - 1);
			sound(t.level, Vec3.atCenterOf(last.pos()), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.8f, 1.5f);
		}
		t.built = end;
	}

	public static void endTower(Tower t) {
		if (!TOWERS.remove(t.owner, t)) {
			return;
		}
		for (Construct.Cell cell : t.cells) {
			unindex(t.level, cell.pos(), t);
			if (t.level.hasChunkAt(cell.pos()) && t.level.getBlockState(cell.pos()) == cell.placed()) {
				t.level.setBlock(cell.pos(), cell.previous(), Block.UPDATE_ALL);
				t.level.sendParticles(SPARK, cell.pos().getX() + 0.5, cell.pos().getY() + 0.5, cell.pos().getZ() + 0.5, 2, 0.3,
						0.3, 0.3, 0.03);
			}
		}
		if (!t.cells.isEmpty()) {
			sound(t.level, Vec3.atCenterOf(t.cells.get(0).pos()), SoundEvents.AMETHYST_CLUSTER_BREAK, 0.8f, 1.3f);
		}
	}

	// ---------------------------------------------------------------- Item Net

	private static void deployNet(ServerPlayer p) {
		if (!GreenLantern.abilityReady(p, NET_COOLDOWN_ID)) {
			int seconds = (int) Math.ceil(GreenLantern.cooldownRemaining(p, NET_COOLDOWN_ID) / 20.0);
			p.displayClientMessage(Component.translatable("message.projecthero.green_lantern.construct_cooldown", seconds), true);
			return;
		}
		if (!pay(p, GreenLanternConfig.NET_COST)) {
			return;
		}
		GreenLantern.triggerCooldown(p, NET_COOLDOWN_ID, GreenLanternConfig.NET_COOLDOWN_TICKS);
		ServerLevel level = p.serverLevel();
		HardLightConstructEntity e = HardLightConstructEntity.create(level, Shape.ITEM_NET, p.getUUID(), p.position(),
				(float) GreenLanternConfig.NET_RADIUS, GreenLanternConfig.NET_TICKS);
		e.setYRot(p.getYRot());
		level.addFreshEntity(e);
		keep(p, e);
		constructFx(p);
		sound(level, p.position(), SoundEvents.FISHING_BOBBER_THROW, 1.0f, 0.6f);
		sound(level, p.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.8f, 1.8f);
	}

	/** How far out the net has swept {@code age} ticks in (shared with the renderer): out over 0.6 s, then reeling in. */
	public static double netReach(double radius, float age, int life) {
		float out = 12f;
		if (age < out) {
			return radius * (age / out);
		}
		float in = Math.max(1f, life - out);
		return radius * Math.max(0.0, 1.0 - (age - out) / in * 0.85);
	}

	private static void tickNet(HardLightConstructEntity e, ServerPlayer owner) {
		ServerLevel level = (ServerLevel) e.level();
		if (owner.level() != level) {
			e.discard();
			return;
		}
		Vec3 home = owner.position().add(0, 0.6, 0);
		e.setPos(owner.getX(), owner.getY(), owner.getZ());
		double reach = Math.max(netReach(e.scale(), e.tickCount, e.life()), e.tickCount >= 12 ? e.scale() : 0);
		AABB box = owner.getBoundingBox().inflate(e.scale() + 1.0);
		// catch: everything the sweep has passed over, kept caught (e.hit) until it is reeled in
		for (Entity x : level.getEntities((Entity) null, box, x -> (x instanceof ItemEntity || x instanceof ExperienceOrb) && x.isAlive())) {
			if (!e.hit.contains(x.getId()) && x.distanceToSqr(home) <= reach * reach) {
				e.hit.add(x.getId());
				level.sendParticles(SPARK, x.getX(), x.getY() + 0.2, x.getZ(), 3, 0.1, 0.1, 0.1, 0.02);
			}
			if (!e.hit.contains(x.getId())) {
				continue;
			}
			Vec3 to = home.subtract(x.position());
			double d = to.length();
			if (d <= 1.6) {
				if (x instanceof ItemEntity item) {
					item.playerTouch(owner);
				} else if (x instanceof ExperienceOrb orb) {
					owner.takeXpDelay = 0;
					orb.playerTouch(owner);
				}
				if (x.isAlive()) {
					x.setDeltaMovement(Vec3.ZERO); // waiting on its pickup delay: it hovers at your feet
				}
				continue;
			}
			double step = Math.min(d - 1.0, 0.45 + e.tickCount * 0.02);
			Vec3 next = x.position().add(to.scale(step / d));
			x.setPos(next.x, next.y, next.z);
			x.setDeltaMovement(to.scale(0.05 / d));
			x.hurtMarked = true;
			if (e.tickCount % 3 == 0) {
				level.sendParticles(GREEN_DUST, x.getX(), x.getY() + 0.25, x.getZ(), 1, 0.05, 0.05, 0.05, 0.0);
			}
		}
		if (e.tickCount % 10 == 0) {
			sound(level, owner.position(), SoundEvents.FISHING_BOBBER_RETRIEVE, 0.6f, 1.2f);
		}
	}

	// ---------------------------------------------------------------- Tunneller

	private static void deployTunneller(ServerPlayer p) {
		for (HardLightConstructEntity e : active(p.getUUID())) {
			if (!e.isRemoved() && e.shape() == Shape.TUNNELLER) {
				GreenLanternEnergy.feedback(p, "message.projecthero.green_lantern.already_out");
				return;
			}
		}
		if (!pay(p, GreenLanternConfig.TUNNEL_COST)) {
			return;
		}
		ServerLevel level = p.serverLevel();
		Vec3 look = p.getLookAngle();
		Direction dir = Direction.getNearest(look.x, look.y, look.z);
		BlockPos start = tunnelStart(p, dir);
		Vec3 at = Vec3.atCenterOf(start);
		HardLightConstructEntity e = HardLightConstructEntity.create(level, Shape.TUNNELLER, p.getUUID(), at, 1.0f,
				GreenLanternConfig.TUNNEL_LENGTH * GreenLanternConfig.TUNNEL_TICKS_PER_SLICE + 16);
		e.face(Vec3.atLowerCornerOf(dir.getNormal()));
		e.homingId = dir.get3DDataValue();
		e.origin = at;
		e.setEnd(at);
		level.addFreshEntity(e);
		keep(p, e);
		constructFx(p);
		sound(level, at, SoundEvents.BEACON_ACTIVATE, 0.7f, 1.8f);
		sound(level, at, SoundEvents.PISTON_EXTEND, 0.8f, 0.6f);
	}

	/** The centre of the first 3x3 slice: at head height straight ahead, under your feet going down, over your head up. */
	public static BlockPos tunnelStart(ServerPlayer p, Direction dir) {
		BlockPos feet = p.blockPosition();
		return switch (dir) {
			case DOWN -> feet.below();
			case UP -> feet.above(2);
			default -> feet.above().relative(dir);
		};
	}

	/** The nine blocks of slice {@code n} of a bore heading {@code dir} from {@code start}. */
	public static List<BlockPos> slice(BlockPos start, Direction dir, int n) {
		BlockPos c = start.relative(dir, n);
		Direction u;
		Direction v;
		if (dir.getAxis() == Direction.Axis.Y) {
			u = Direction.EAST;
			v = Direction.SOUTH;
		} else {
			u = dir.getClockWise();
			v = Direction.UP;
		}
		List<BlockPos> out = new ArrayList<>(9);
		out.add(c);
		for (int a = -1; a <= 1; a++) {
			for (int b = -1; b <= 1; b++) {
				if (a != 0 || b != 0) {
					out.add(c.relative(u, a).relative(v, b));
				}
			}
		}
		return out;
	}

	private static void tickTunneller(HardLightConstructEntity e, ServerPlayer owner) {
		ServerLevel level = (ServerLevel) e.level();
		if (e.phase >= GreenLanternConfig.TUNNEL_LENGTH || e.returning) {
			return; // finished: it fades out over the rest of its life
		}
		Direction dir = Direction.from3DDataValue(e.homingId);
		BlockPos start = BlockPos.containing(e.origin);
		// the drill head creeps forward between slices
		float into = (e.tickCount % GreenLanternConfig.TUNNEL_TICKS_PER_SLICE) / (float) GreenLanternConfig.TUNNEL_TICKS_PER_SLICE;
		Vec3 head = Vec3.atCenterOf(start.relative(dir, e.phase)).add(Vec3.atLowerCornerOf(dir.getNormal()).scale(into - 0.5));
		e.setPos(head.x, head.y, head.z);
		if (e.tickCount % 2 == 0) {
			level.sendParticles(SPARK, head.x, head.y, head.z, 4, 0.8, 0.8, 0.8, 0.08);
		}
		if (e.tickCount % GreenLanternConfig.TUNNEL_TICKS_PER_SLICE != 0) {
			return;
		}
		List<BlockPos> blocks = slice(start, dir, e.phase);
		BlockPos centre = blocks.get(0);
		if (!level.hasChunkAt(centre) || level.getBlockState(centre).getDestroySpeed(level, centre) < 0
				|| GreenLanternConstructs.isTrackedCell(level, centre) || isTrackedCell(level, centre)) {
			stopTunneller(e, level); // bedrock, a barrier, the world's edge, hard light: the bore stops here
			return;
		}
		int mined = 0;
		// what one block really costs right now (the Oath doubles it, a Lantern Rank cuts it) -- checked before each break
		float perBlock = GreenLanternConfig.TUNNEL_COST_PER_BLOCK * GreenLanternOath.multiplier(owner)
				* GreenLanternRanks.costMult(GreenLanternRanks.rank(owner));
		for (BlockPos pos : blocks) {
			if (!mineable(level, pos)) {
				continue;
			}
			if (!GreenLanternEnergy.canSpend(owner, perBlock)) {
				GreenLanternEnergy.feedback(owner, "message.projecthero.ability.low_charge");
				stopTunneller(e, level);
				return;
			}
			if (mine(owner, pos)) {
				GreenLanternEnergy.spend(owner, GreenLanternConfig.TUNNEL_COST_PER_BLOCK);
				mined++;
			}
		}
		e.phase++;
		e.markAction();
		Vec3 c = Vec3.atCenterOf(centre);
		level.sendParticles(GREEN_DUST, c.x, c.y, c.z, 10, 0.9, 0.9, 0.9, 0.02);
		sound(level, c, mined > 0 ? SoundEvents.STONE_BREAK : SoundEvents.BEACON_AMBIENT, 0.9f, 0.7f);
		if (e.phase % 2 == 0) {
			sound(level, c, SoundEvents.BEACON_AMBIENT, 0.5f, 2.0f);
		}
		if (e.phase >= GreenLanternConfig.TUNNEL_LENGTH) {
			stopTunneller(e, level);
		}
	}

	private static void stopTunneller(HardLightConstructEntity e, ServerLevel level) {
		e.returning = true;
		e.setLife(e.tickCount + 8);
		sound(level, e.position(), SoundEvents.BEACON_DEACTIVATE, 0.6f, 1.6f);
	}

	/** Air, fluids, unbreakable blocks and any construct's hard light are skipped. */
	private static boolean mineable(ServerLevel level, BlockPos pos) {
		if (!level.hasChunkAt(pos)) {
			return false;
		}
		BlockState state = level.getBlockState(pos);
		return !state.isAir() && !(state.getBlock() instanceof LiquidBlock) && state.getDestroySpeed(level, pos) >= 0
				&& !GreenLanternConstructs.isTrackedCell(level, pos) && !isTrackedCell(level, pos);
	}

	/**
	 * The Mining Drill's break path ({@code ServerPlayerGameMode#destroyBlock}: Fabric's break events, protection mods,
	 * spawn protection, adventure mode, tool drops, ore XP) swung with a hard-light diamond pickaxe put in the hand for
	 * the one call -- the Lantern's own held item is back before anything can see it gone.
	 */
	public static boolean mine(ServerPlayer p, BlockPos pos) {
		Inventory inv = p.getInventory();
		int slot = inv.selected;
		ItemStack held = inv.items.get(slot);
		ItemStack pick = new ItemStack(Items.DIAMOND_PICKAXE);
		pick.set(DataComponents.UNBREAKABLE, new Unbreakable(false));
		inv.items.set(slot, pick);
		try {
			return p.gameMode.destroyBlock(pos);
		} finally {
			inv.items.set(slot, held);
		}
	}

	// ---------------------------------------------------------------- helpers

	private static boolean pay(ServerPlayer p, float cost) {
		if (GreenLanternEnergy.spend(p, cost)) {
			GreenLanternBattery.onAbilityUsed(p);
			return true;
		}
		GreenLanternEnergy.feedback(p, "message.projecthero.ability.low_charge");
		return false;
	}

	/** Same rules as the block constructs: replaceable, breakable, no block entity, buildable here; solid cells never in a player. */
	private static boolean canPlace(ServerPlayer p, BlockPos pos, boolean solid) {
		ServerLevel level = p.serverLevel();
		if (!level.hasChunkAt(pos) || !level.isInWorldBounds(pos) || !level.mayInteract(p, pos)) {
			return false;
		}
		BlockState current = level.getBlockState(pos);
		if (!current.isAir() && !current.canBeReplaced() || current.getDestroySpeed(level, pos) < 0
				|| level.getBlockEntity(pos) != null || isTrackedCell(level, pos) || GreenLanternConstructs.isTrackedCell(level, pos)) {
			return false;
		}
		return !solid || !playerIn(level, pos);
	}

	private static boolean playerIn(ServerLevel level, BlockPos pos) {
		return !level.getEntitiesOfClass(Player.class, new AABB(pos), x -> !x.isSpectator()).isEmpty();
	}

	private static void constructFx(ServerPlayer p) {
		GreenLanternVisuals.anim(p, GreenLanternFx.ANIM_CONSTRUCT);
		Vec3 h = AbilityHelpers.handPosition(p);
		p.serverLevel().sendParticles(GREEN_DUST, h.x, h.y, h.z, 12, 0.15, 0.15, 0.15, 0.02);
	}

	private static void dissolve(HardLightConstructEntity e) {
		if (e.level() instanceof ServerLevel level) {
			level.sendParticles(SPARK, e.getX(), e.getY() + 0.5, e.getZ(), 14, 0.4, 0.4, 0.4, 0.05);
		}
	}

	static void sound(ServerLevel level, Vec3 at, SoundEvent s, float vol, float pitch) {
		level.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, vol, pitch);
	}
}
