package com.projecthero.mod.gametest;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.projecthero.mod.ironman.furnace.StarkFurnaceBlock;
import com.projecthero.mod.ironman.furnace.StarkFurnaces;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * v0.15.1: the Stark Furnace / Smelter / Smoker got multi-element models. Their block states must not move (the
 * v0.14.22 lesson: state-id shifts break client/server compat), and every blockstate -> model -> texture reference has
 * to resolve.
 */
public class StarkFurnaceModelsV0151GameTests implements FabricGameTest {
	private static final String[] IDS = { "stark_furnace", "stark_smelter", "stark_smoker" };

	private static InputStream open(String path) {
		return StarkFurnaceModelsV0151GameTests.class.getResourceAsStream(path);
	}

	private static JsonObject read(String path) {
		try (InputStream in = open(path)) {
			if (in == null) {
				throw new IllegalStateException("missing resource " + path);
			}
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (java.io.IOException e) {
			throw new IllegalStateException("cannot read " + path, e);
		}
	}

	private static boolean exists(String path) {
		try (InputStream in = open(path)) {
			return in != null;
		} catch (java.io.IOException e) {
			return false;
		}
	}

	private static String path(String resourceLocation, String folder, String ext) {
		String[] p = resourceLocation.split(":", 2);
		return "/assets/" + p[0] + "/" + folder + "/" + p[1] + ext;
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void starkFurnaceBlockStatesAreUnchanged(GameTestHelper helper) {
		for (StarkFurnaceBlock b : new StarkFurnaceBlock[] { StarkFurnaces.FURNACE, StarkFurnaces.SMELTER, StarkFurnaces.SMOKER }) {
			helper.assertTrue(Set.copyOf(b.getStateDefinition().getProperties()).equals(Set.of(AbstractFurnaceBlock.FACING, AbstractFurnaceBlock.LIT)),
					b.kind().id() + " keeps exactly FACING + LIT");
			helper.assertTrue(b.getStateDefinition().getPossibleStates().size() == 8, b.kind().id() + " has 8 states");
			BlockState d = b.defaultBlockState();
			helper.assertTrue(d.getValue(AbstractFurnaceBlock.FACING) == Direction.NORTH && !d.getValue(AbstractFurnaceBlock.LIT),
					b.kind().id() + " defaults to north, unlit");
			helper.assertTrue(d.getLightEmission() == 3 && d.setValue(AbstractFurnaceBlock.LIT, true).getLightEmission() == 14,
					b.kind().id() + " glows 3 idle / 14 lit");
			helper.assertTrue(!d.canOcclude(), b.kind().id() + " doesn't cull neighbours (its model isn't a full cube)");
			helper.assertTrue(d.isCollisionShapeFullBlock(helper.getLevel(), BlockPos.ZERO), b.kind().id() + " still collides as a full block");
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void starkFurnaceModelsResolve(GameTestHelper helper) {
		for (String id : IDS) {
			JsonObject variants = read("/assets/projecthero/blockstates/" + id + ".json").getAsJsonObject("variants");
			helper.assertTrue(variants.size() == 8, id + " blockstate has 8 variants");
			for (String facing : new String[] { "north", "east", "south", "west" }) {
				for (boolean lit : new boolean[] { false, true }) {
					String key = "facing=" + facing + ",lit=" + lit;
					helper.assertTrue(variants.has(key), id + " has variant " + key);
					String model = variants.getAsJsonObject(key).get("model").getAsString();
					helper.assertTrue(model.equals("projecthero:block/" + id + (lit ? "_on" : "")), id + " " + key + " -> " + model);
				}
			}
			for (String model : new String[] { id, id + "_on" }) {
				JsonObject m = read("/assets/projecthero/models/block/" + model + ".json");
				JsonObject textures = m.getAsJsonObject("textures");
				helper.assertTrue(textures.has("particle"), model + " has a particle texture");
				for (Map.Entry<String, JsonElement> t : textures.entrySet()) {
					String png = path(t.getValue().getAsString(), "textures", ".png");
					helper.assertTrue(exists(png), model + " texture #" + t.getKey() + " exists (" + png + ")");
				}
				helper.assertTrue(m.getAsJsonArray("elements").size() >= 20, model + " is a multi-element model");
				for (JsonElement e : m.getAsJsonArray("elements")) {
					JsonObject el = e.getAsJsonObject();
					for (int k = 0; k < 3; k++) {
						float from = el.getAsJsonArray("from").get(k).getAsFloat(), to = el.getAsJsonArray("to").get(k).getAsFloat();
						helper.assertTrue(from >= 0 && to <= 16 && from <= to, model + " element stays inside the block");
					}
					for (Map.Entry<String, JsonElement> f : el.getAsJsonObject("faces").entrySet()) {
						String ref = f.getValue().getAsJsonObject().get("texture").getAsString();
						helper.assertTrue(ref.startsWith("#") && textures.has(ref.substring(1)), model + " face " + f.getKey() + " uses a defined texture " + ref);
					}
				}
			}
			JsonObject item = read("/assets/projecthero/models/item/" + id + ".json");
			helper.assertTrue(("projecthero:block/" + id).equals(item.get("parent").getAsString()), id + " item shows the 3D block model");
		}
		helper.succeed();
	}
}
