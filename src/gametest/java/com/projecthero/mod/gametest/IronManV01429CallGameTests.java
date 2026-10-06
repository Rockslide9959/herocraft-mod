package com.projecthero.mod.gametest;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.projecthero.mod.ironman.IronManArmor;
import com.projecthero.mod.ironman.IronManBlocks;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.ProtocolPhoenix;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.data.StarkPlatformRegistry;
import com.projecthero.mod.ironman.data.StarkSuitReturnQueue;
import com.projecthero.mod.ironman.entity.IronManSuitPartEntity;
import com.projecthero.mod.ironman.fabricator.IronManSuitPlatformBlockEntity;
import com.projecthero.mod.ironman.item.IronManArmorItem;
import com.projecthero.mod.ironman.item.IronManItems;
import com.projecthero.mod.ironman.suit.IronManChunkTickets;
import com.projecthero.mod.ironman.suit.IronManPlatformReturn;
import com.projecthero.mod.ironman.suit.IronManSuit;
import com.projecthero.mod.ironman.suit.IronManSuitCall;
import com.projecthero.mod.ironman.suit.IronManSuitUpManager;
import com.projecthero.mod.ironman.suit.IronManSuits;
import com.projecthero.mod.ironman.ui.IronManSuitCompare;
import com.projecthero.mod.ironman.ui.IronManUiLayout;
import com.projecthero.mod.ironman.ui.IronManUiLayout.Rect;
import com.projecthero.mod.network.IronManSuitListPayload;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.29 agent D: "the suit calling and everything related has to work even if in unloaded chunks". Every platform
 * here sits ~700 blocks beyond its player, who is moved ~3.5 km out into a force-loaded chunk of its own, in a chunk the test first waits to see really
 * unloaded; then the call / send-home / Phoenix recall runs and the suit must end up in exactly one place.
 */
public class IronManV01429CallGameTests implements FabricGameTest {
	private static final ArmorItem.Type[] ALL = {
			ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS };

	private static ServerPlayer tony(GameTestHelper helper, BlockPos rel) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		TonyStark.grant(p);
		Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(rel));
		p.moveTo(at.x, at.y, at.z, 0f, 0f);
		return p;
	}

	/**
	 * Moves the player into a force-loaded chunk of its own {@code offset} blocks out (+x/+z), away from every other test's
	 * Suit Platforms -- an unowned platform of another test within the loaded-chunk scan would otherwise answer the call.
	 * Only that one chunk ticks there, which also exercises the "never launch a courier into a frozen chunk" guard.
	 */
	private static ChunkPos isolate(GameTestHelper helper, ServerPlayer p, int offset) {
		ServerLevel level = helper.getLevel();
		BlockPos base = helper.absolutePos(new BlockPos(2, 2, 2)).offset(offset, 0, offset);
		ChunkPos cp = new ChunkPos(base);
		level.setChunkForced(cp.x, cp.z, true);
		level.getChunk(cp.x, cp.z);
		p.moveTo(cp.getMiddleBlockX() + 0.5, base.getY(), cp.getMiddleBlockZ() + 0.5, 0f, 0f);
		return cp;
	}

	private static void release(GameTestHelper helper, ChunkPos cp) {
		helper.getLevel().setChunkForced(cp.x, cp.z, false);
	}

	/** A platform bound to {@code owner}, {@code offset} blocks beyond it (+x/+z), optionally racked with a full Mark III. */
	private static BlockPos farPlatform(GameTestHelper helper, ServerPlayer owner, int offset, boolean racked,
			float energy, float integrity) {
		ServerLevel level = helper.getLevel();
		// further out from the isolated player (away from the test grid): every other test's unowned platform in the
		// registry is callable too, so ours must be the nearest one
		BlockPos far = owner.blockPosition().offset(offset, 0, offset);
		IronManChunkTickets.hold(level, far);
		level.setBlockAndUpdate(far, IronManBlocks.IRON_MAN_SUIT_PLATFORM.defaultBlockState());
		IronManSuitPlatformBlockEntity be = (IronManSuitPlatformBlockEntity) level.getBlockEntity(far);
		be.bindTo(owner.getUUID());
		if (racked) {
			// energy / integrity are fractions of the Mark III's own capacity / max
			float cap = IronManSuits.byId("mark_iii").energyCapacity();
			float maxI = IronManEnergy.maxIntegrity("mark_iii");
			for (ArmorItem.Type t : ALL) {
				ItemStack piece = new ItemStack(IronManItems.armor("mark_iii", t));
				IronManEnergy.stampStack(piece, energy * cap, integrity * maxI);
				helper.assertTrue(be.store(piece), "the far platform takes the " + t);
			}
		}
		return far;
	}

	private static boolean unloaded(ServerLevel level, BlockPos pos) {
		ChunkPos cp = new ChunkPos(pos);
		return level.getChunkSource().getChunkNow(cp.x, cp.z) == null;
	}

	/** Every Mark III piece the player has anywhere: worn + pack + couriers / drops around them. */
	private static int piecesAround(GameTestHelper helper, ServerPlayer p) {
		int n = 0;
		for (ArmorItem.Type t : ALL) {
			if (p.getItemBySlot(IronManSuitUpManager.slotFor(t)).getItem() instanceof IronManArmorItem a && a.suitId().equals("mark_iii")) {
				n++;
			}
		}
		for (ItemStack s : p.getInventory().items) {
			if (s.getItem() instanceof IronManArmorItem a && a.suitId().equals("mark_iii")) {
				n += s.getCount();
			}
		}
		n += helper.getLevel().getEntitiesOfClass(IronManSuitPartEntity.class, p.getBoundingBox().inflate(64)).size();
		n += helper.getLevel().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(64),
				e -> e.getItem().getItem() instanceof IronManArmorItem).size();
		return n;
	}

	// ------------------------------------------------------------------ call from an unloaded platform

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 1200)
	public void callFromAnUnloadedPlatformDeliversTheSuitExactlyOnce(GameTestHelper helper) {
		ServerPlayer p = tony(helper, new BlockPos(2, 2, 2));
		ChunkPos cp = isolate(helper, p, 2500);
		ServerLevel level = helper.getLevel();
		BlockPos far = farPlatform(helper, p, 500, true, 0.6f, 0.75f);
		float racked = 0.6f * IronManSuits.byId("mark_iii").energyCapacity();
		GlobalPos gp = GlobalPos.of(level.dimension(), far);
		helper.startSequence()
				.thenWaitUntil(() -> helper.assertTrue(unloaded(level, far), "the far platform's chunk unloads"))
				.thenExecute(() -> {
					List<IronManSuitListPayload.Option> opts = IronManSuitCall.assemblableSuits(p);
					Optional<IronManSuitListPayload.Option> o = opts.stream()
							.filter(x -> x.suitId().equals("mark_iii") && x.source() == IronManSuitListPayload.SOURCE_PLATFORM).findFirst();
					helper.assertTrue(o.isPresent(), "the picker lists the Mark III on the unloaded platform");
					helper.assertTrue(Math.abs(o.get().energyFrac() - 0.6f) < 0.01f,
							"with the charge it was racked with (" + o.get().energyFrac() + ")");
					helper.assertTrue(o.get().distance() > 600, "and its real distance (" + o.get().distance() + ")");
					IronManSuitCall.execute(p, "mark_iii", IronManSuitListPayload.SOURCE_PLATFORM);
					helper.assertTrue(IronManSuitCall.hasPendingCall(p), "the call is travelling");
					helper.assertTrue(StarkPlatformRegistry.get(level).at(level, far).map(StarkPlatformRegistry.Entry::pieceMask).orElse(0) == 15,
							"nothing has left the platform while the suit travels");
					helper.assertTrue(unloaded(level, far), "and the call did not have to load the chunk yet");
					IronManSuitCall.skipPendingTravel(p);
				})
				.thenWaitUntil(() -> {
					helper.assertTrue(IronManArmor.wearingFullSuit(p, "mark_iii"), "the whole Mark III ends up on the player");
					helper.assertFalse(IronManSuitUpManager.inTransition(p), "and the suit-up has finished");
				})
				.thenExecute(() -> {
					helper.assertTrue(piecesAround(helper, p) == 4, "exactly four Mark III pieces exist around the player (no duplicate)");
					helper.assertTrue(StarkPlatformRegistry.get(level).at(level, far).map(StarkPlatformRegistry.Entry::pieceMask).orElse(-1) == 0,
							"the registry shows the platform empty");
					IronManSuitPlatformBlockEntity be = IronManChunkTickets.loadPlatform(level, far);
					helper.assertTrue(be != null && be.isEmptyPlatform(), "and the platform itself is empty");
					helper.assertTrue(Math.abs(IronManEnergy.energy(p, "mark_iii") - racked) < racked * 0.05f + 1f,
							"the worn suit carries the racked charge (" + IronManEnergy.energy(p, "mark_iii") + ")");
					Optional<GlobalPos> home = IronManPlatformReturn.homeOf(p.getItemBySlot(IronManSuitUpManager.slotFor(ArmorItem.Type.CHESTPLATE)));
					helper.assertTrue(home.isPresent() && home.get().equals(gp), "each piece remembers the platform it came off");
					// a nearer empty platform of the player's: send-home still prefers the suit's own platform
					BlockPos near = p.blockPosition().offset(2, 0, 0);
					level.setBlockAndUpdate(near, IronManBlocks.IRON_MAN_SUIT_PLATFORM.defaultBlockState());
					((IronManSuitPlatformBlockEntity) level.getBlockEntity(near)).bindTo(p.getUUID());
					Optional<IronManSuitListPayload.Option> back = IronManSuitCall.sendBackOptions(p).stream()
							.filter(x -> x.suitId().equals("mark_iii")).findFirst();
					helper.assertTrue(back.isPresent() && back.get().distance() > 600,
							"the worn suit is offered home to the platform it came from, not the nearer one");
					level.removeBlock(near, false);
					StarkPlatformRegistry.get(level).remove(level, near);
					release(helper, cp);
				})
				.thenSucceed();
	}

	// ------------------------------------------------------------------ send the worn suit home into an unloaded platform

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 1000)
	public void sendingTheWornSuitHomeRacksItOnAnUnloadedPlatform(GameTestHelper helper) {
		ServerPlayer p = tony(helper, new BlockPos(2, 2, 2));
		ChunkPos cp = isolate(helper, p, 2700);
		ServerLevel level = helper.getLevel();
		BlockPos far = farPlatform(helper, p, 500, false, 0f, 0f);
		helper.startSequence()
				.thenWaitUntil(() -> helper.assertTrue(unloaded(level, far), "the far platform's chunk unloads"))
				.thenExecute(() -> {
					helper.assertTrue(StarkPlatformRegistry.get(level).at(level, far).isPresent(), "the registry kept the unloaded platform");
					for (ArmorItem.Type t : ALL) {
						p.setItemSlot(IronManSuitUpManager.slotFor(t), new ItemStack(IronManItems.armor("mark_iii", t)));
					}
					TonyStark.setActiveSuit(p, "mark_iii");
					IronManEnergy.setEnergy(p, "mark_iii", 700f);
					IronManEnergy.setIntegrity(p, "mark_iii", 400f);
					helper.assertTrue(IronManSuitCall.sendBackOptions(p).stream().anyMatch(o -> o.suitId().equals("mark_iii")
							&& o.source() == IronManSuitListPayload.SOURCE_SEND_BACK && o.distance() > 600),
							"Sneak+C while suited offers the worn Mark III home to the unloaded platform");
					helper.assertTrue(IronManSuitCall.sendBack(p, "mark_iii") == 4, "all four worn pieces leave");
					helper.assertFalse(IronManArmor.wearingAnyIronMan(p), "the player is out of the suit");
				})
				.thenWaitUntil(() -> {
					int flying = helper.getLevel().getEntitiesOfClass(IronManSuitPartEntity.class, p.getBoundingBox().inflate(80)).size();
					var entry = StarkPlatformRegistry.get(level).at(level, far);
					helper.assertTrue(flying == 0 && entry.map(StarkPlatformRegistry.Entry::pieceMask).orElse(0) == 15,
							"the suit flies off and the unloaded platform ends up holding all four pieces (flying " + flying
									+ ", registry " + entry.map(e -> e.suitId() + "/" + e.pieceMask()).orElse("none")
									+ ", queued " + StarkSuitReturnQueue.get(level).hasPendingFor(level, far)
									+ ", with player " + piecesAround(helper, p) + ")");
				})
				.thenExecute(() -> {
					helper.assertFalse(StarkSuitReturnQueue.get(level).hasPendingFor(level, far),
							"racked at once through a chunk ticket -- nothing waits on the return queue");
					helper.assertTrue(piecesAround(helper, p) == 0, "nothing came back to the player (no duplicate)");
					IronManSuitPlatformBlockEntity be = IronManChunkTickets.loadPlatform(level, far);
					helper.assertTrue(be != null && be.isFull() && "mark_iii".equals(be.storedSuitId()), "the platform really holds the suit");
					helper.assertTrue(Math.abs(be.suitIntegrity() - 400f) < 1f, "with the integrity it was sent home with, to repair there");
					release(helper, cp);
				})
				.thenSucceed();
	}

	// ------------------------------------------------------------------ Protocol Phoenix from an unloaded platform

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 1200)
	public void protocolPhoenixRecallsASuitFromAnUnloadedPlatform(GameTestHelper helper) {
		ServerPlayer p = tony(helper, new BlockPos(2, 2, 2));
		ChunkPos cp = isolate(helper, p, 2900);
		ServerLevel level = helper.getLevel();
		BlockPos far = farPlatform(helper, p, 500, true, 0.9f, 0.9f);
		helper.startSequence()
				.thenWaitUntil(() -> helper.assertTrue(unloaded(level, far), "the far platform's chunk unloads"))
				.thenExecute(() -> {
					TonyStark.setPhoenixReadyAt(p, 0L);
					helper.assertTrue(ProtocolPhoenix.tryActivate(p, p.damageSources().generic()), "Phoenix takes over the death");
					helper.assertTrue(IronManSuitCall.hasPendingCall(p), "and calls the suit off the unloaded platform");
				})
				.thenWaitUntil(() -> {
					helper.assertTrue(IronManArmor.wearingFullSuit(p, "mark_iii"), "the emergency suit arrives");
					helper.assertFalse(TonyStark.phoenixEmergency(p), "and Phoenix completes");
				})
				.thenExecute(() -> {
					helper.assertTrue(piecesAround(helper, p) == 4, "exactly four pieces (no duplicate)");
					helper.assertTrue(StarkPlatformRegistry.get(level).at(level, far).map(StarkPlatformRegistry.Entry::pieceMask).orElse(-1) == 0,
							"the platform is empty");
					release(helper, cp);
				})
				.thenSucceed();
	}

	// ------------------------------------------------------------------ compare panel layout

	private static JsonObject lang;

	private static String t(String key) {
		if (lang == null) {
			try (InputStream in = IronManV01429CallGameTests.class.getResourceAsStream("/assets/projecthero/lang/en_us.json")) {
				lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			} catch (Exception e) {
				throw new IllegalStateException("cannot read en_us.json", e);
			}
		}
		if (!lang.has(key)) {
			throw new IllegalStateException("missing lang key " + key);
		}
		return lang.get(key).getAsString();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void comparePanelFitsBesideTheGridAtSmallGuiScales(GameTestHelper helper) {
		// GUI scale 4 (320x240), scale 3 at 720p (426x240), scale 4 at 1080p (480x270), scale 2 at 720p (640x360)
		int[][] screens = { { 320, 240 }, { 426, 240 }, { 480, 270 }, { 640, 360 }, { 960, 540 } };
		for (int[] s : screens) {
			Rect panel = IronManSuitCompare.panel(s[0], s[1]);
			helper.assertTrue(panel.insideScreen(s[0], s[1]), "panel inside " + s[0] + "x" + s[1]);
			helper.assertTrue(panel.bottom() <= s[1] - 36, "panel clear of the hint line and cancel button at " + s[0] + "x" + s[1]);
			for (int i = 0; i < 8; i++) {
				Rect c = IronManSuitCompare.card(i, s[0], 0);
				helper.assertTrue(c.x() >= 0 && c.right() <= s[0], "card " + i + " inside the width at " + s[0]);
				helper.assertFalse(c.overlaps(panel), "card " + i + " never under the panel at " + s[0] + "x" + s[1]);
			}
			int gx = IronManSuitCompare.card(0, s[0], 0).x() + IronManUiLayout.gridWidth(IronManSuitCompare.columns(s[0])) + 2;
			helper.assertTrue(gx + 2 <= panel.x(), "the scrollbar sits between grid and panel at " + s[0]);
			helper.assertTrue(IronManSuitCompare.abilitiesTop() + 3 * IronManSuitCompare.ROW_H <= panel.h(),
					"every stat row plus the abilities heading and two ability lines fit at " + s[0] + "x" + s[1]);
		}
		int vw = IronManSuitCompare.valueWidth();
		for (IronManSuitCompare.Stat stat : IronManSuitCompare.Stat.values()) {
			helper.assertTrue(IronManUiLayout.approxWidth(t(stat.key)) <= IronManSuitCompare.LABEL_W - 2, "label fits: " + stat.key);
		}
		helper.assertTrue(IronManUiLayout.approxWidth(t("screen.projecthero.suit_call.cmp.title"), true) <= IronManSuitCompare.innerWidth(),
				"title fits");
		helper.assertTrue(IronManUiLayout.approxWidth(t("screen.projecthero.suit_call.cmp.abilities"), true) <= IronManSuitCompare.innerWidth(),
				"abilities heading fits");
		helper.assertTrue(IronManUiLayout.approxWidth(t("screen.projecthero.suit_call.hint")) <= 320 - 12, "hint fits 320 wide");
		for (IronManSuit suit : IronManSuits.all()) {
			for (IronManSuitCompare.Stat stat : IronManSuitCompare.Stat.values()) {
				String v = IronManSuitCompare.format(stat, IronManSuitCompare.value(suit, stat, 1f, 1f));
				helper.assertTrue(IronManUiLayout.approxWidth(v) <= vw - 2, suit.id() + " " + stat + " value fits: " + v);
			}
			helper.assertTrue(IronManSuitCompare.flightSpeed(suit) > 0.0, suit.id() + " has a flight speed");
			if (suit.maxFlightSpeedMps() > 0) {
				helper.assertTrue(IronManSuitCompare.flightSpeed(suit) <= suit.maxFlightSpeedMps() + 0.01,
						suit.id() + " flight speed respects its ceiling");
			}
			for (String key : IronManSuitCompare.abilityKeys(suit)) {
				t(key); // every listed ability has a name (long ones word-wrap in the panel)
			}
			helper.assertFalse(IronManSuitCompare.abilityKeys(suit).contains("hud.projecthero.ironman.ability.suit_toggle"),
					"Store Suit is not a key ability");
		}
		helper.assertTrue(IronManSuitCompare.better(10f, 5f) == 1 && IronManSuitCompare.better(5f, 10f) == -1
				&& IronManSuitCompare.better(-1f, 5f) == 0, "comparison colouring rule");
		helper.succeed();
	}
}
