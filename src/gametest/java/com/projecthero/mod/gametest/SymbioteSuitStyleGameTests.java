package com.projecthero.mod.gametest;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.network.SymbioteColourPayload;
import com.projecthero.mod.symbiote.Symbiote;
import com.projecthero.mod.symbiote.SymbioteColourPreset;
import com.projecthero.mod.symbiote.SymbioteColours;
import com.projecthero.mod.symbiote.SymbioteSuit;
import com.projecthero.mod.symbiote.SymbioteSuitColours;
import com.projecthero.mod.symbiote.item.SymbioteHostItems;

import io.netty.buffer.Unpooled;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

/**
 * v0.17.5 (user: "Remove all the classic suit styles from this power and just make it the lethal protector variants please. when
 * player gets the symbiote they get a random colour. Remove the enchantment glow or tint from the suit please"):
 * <ul>
 *   <li>there is no style any more: saves that still carry {@code "style": "classic"} / {@code "lethal_protector"} / junk load as plain
 *   colours (the field is ignored, the suit is the Lethal Protector for everyone), the network carries only the three colours and
 *   the {@code style} command is gone;</li>
 *   <li>a fresh bond rolls a random scheme (deterministic from the player's RNG seed), kept through relog and death, rolled again
 *   on re-bonding;</li>
 *   <li>the suit pieces show no enchantment foil, worn or in an inventory.</li>
 * </ul>
 */
public class SymbioteSuitStyleGameTests implements FabricGameTest {
	private static ServerPlayer host(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		Symbiote.grant(player);
		return player;
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void oldStyleSavesLoadAsPlainColours(GameTestHelper helper) {
		CompoundTag old = new CompoundTag();
		old.putInt("primary", 0x1E4FB8);
		old.putInt("secondary", 0xC81E2A);
		old.putInt("eyes", 0xF4F6FA);
		SymbioteColours expected = new SymbioteColours(0x1E4FB8, 0xC81E2A, 0xF4F6FA);
		for (String style : new String[] { "classic", "lethal_protector", "banana" }) {
			CompoundTag tag = old.copy();
			tag.putString("style", style);
			SymbioteColours parsed = SymbioteColours.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
			helper.assertTrue(parsed.equals(expected), "a save with style=" + style + " loads its colours (style ignored): " + parsed);
		}
		helper.assertTrue(SymbioteColours.CODEC.parse(NbtOps.INSTANCE, old).getOrThrow().equals(expected), "a save without it too");
		// a fresh save no longer writes a style
		CompoundTag written = (CompoundTag) SymbioteColours.CODEC.encodeStart(NbtOps.INSTANCE, expected).getOrThrow();
		helper.assertFalse(written.contains("style"), "no style written: " + written);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theNetworkCarriesOnlyTheColours(GameTestHelper helper) {
		helper.assertTrue(ModAttachments.SYMBIOTE_COLOURS.isSynced(), "synced (other players must see your suit)");
		SymbioteColours mine = new SymbioteColours(0x5B2A86, 0x121016, 0xF2E9FF);
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
		SymbioteColourPayload.CODEC.encode(buf, new SymbioteColourPayload(mine));
		helper.assertTrue(buf.readableBytes() == 16, "four ints (body, emblem, eyes, mouth), no style: " + buf.readableBytes() + " bytes");
		helper.assertTrue(SymbioteColourPayload.CODEC.decode(buf).colours().equals(mine), "the payload round-trips");
		RegistryFriendlyByteBuf sync = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
		SymbioteColours.STREAM_CODEC.encode(sync, mine);
		helper.assertTrue(SymbioteColours.STREAM_CODEC.decode(sync).equals(mine), "the attachment sync round-trips");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theStyleCommandIsGone(GameTestHelper helper) {
		ServerPlayer player = host(helper);
		SymbioteSuitColours.setPreset(player, SymbioteColourPreset.TOXIN);
		var source = player.createCommandSourceStack().withPermission(4).withSuppressedOutput();
		var commands = helper.getLevel().getServer().getCommands();
		var parsed = commands.getDispatcher().parse("projecthero power tune symbiote style classic", source);
		helper.assertTrue(parsed.getReader().canRead() || !parsed.getExceptions().isEmpty() || parsed.getContext().getCommand() == null,
				"/symbiote style no longer parses");
		commands.performPrefixedCommand(source, "projecthero power tune symbiote colour carnage");
		helper.assertTrue(SymbioteSuitColours.get(player).equals(SymbioteColourPreset.CARNAGE.colours()), "/symbiote colour <preset> still works");
		commands.performPrefixedCommand(source, "projecthero power tune symbiote colour hex 102030 405060 708090");
		helper.assertTrue(SymbioteSuitColours.get(player).equals(new SymbioteColours(0x102030, 0x405060, 0x708090)), "colour hex still works");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void randomSchemesAreDeterministicAndVaried(GameTestHelper helper) {
		helper.assertTrue(SymbioteSuitColours.randomScheme(RandomSource.create(42)).equals(SymbioteSuitColours.randomScheme(RandomSource.create(42))),
				"the same seed gives the same scheme");
		int presets = 0, customs = 0;
		java.util.Set<SymbioteColours> distinct = new java.util.HashSet<>();
		for (long seed = 0; seed < 400; seed++) {
			// (scrambled: a legacy RNG's first draw is badly spread over consecutive small seeds; a player's RNG is never reseeded)
			SymbioteColours c = SymbioteSuitColours.randomScheme(RandomSource.create(seed * 6364136223846793005L + 1442695040888963407L));
			distinct.add(c);
			if (SymbioteColourPreset.matching(c).isPresent()) {
				presets++;
			} else {
				customs++;
				// a custom scheme must still read: the emblem stands out from the body
				float d = Math.abs(lum(c.primary()) - lum(c.secondary()));
				helper.assertTrue(d > 0.25f, "custom emblem contrasts with the body (" + SymbioteColours.hex(c.primary()) + " / "
						+ SymbioteColours.hex(c.secondary()) + ", d " + d + ")");
			}
		}
		float share = presets / 400f;
		helper.assertTrue(share > 0.6f && share < 0.8f, "about 70% presets, got " + share);
		helper.assertTrue(customs > 0 && distinct.size() > 100, "varied: " + distinct.size() + " distinct schemes");
		helper.succeed();
	}

	private static float lum(int rgb) {
		return (0.299f * ((rgb >> 16) & 0xFF) + 0.587f * ((rgb >> 8) & 0xFF) + 0.114f * (rgb & 0xFF)) / 255f;
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void aFreshBondRollsTheSchemeAndRelogKeepsIt(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.getRandom().setSeed(1234L);
		SymbioteColours expected = SymbioteSuitColours.randomScheme(RandomSource.create(1234L));
		helper.assertTrue(Symbiote.grant(player), "bonded");
		SymbioteColours rolled = SymbioteSuitColours.get(player);
		helper.assertTrue(rolled.equals(expected), "the bond rolled the seeded scheme: " + rolled + " vs " + expected);
		// relog: the attachment saves and reads back
		CompoundTag saved = player.saveWithoutId(new CompoundTag()).getCompound("fabric:attachments");
		SymbioteColours back = SymbioteColours.CODEC.parse(NbtOps.INSTANCE, saved.get("projecthero:symbiote_colours")).getOrThrow();
		helper.assertTrue(back.equals(rolled), "relog keeps the rolled scheme");
		// granting again while bonded is a no-op and must not reroll
		helper.assertFalse(Symbiote.grant(player), "already bonded");
		helper.assertTrue(SymbioteSuitColours.get(player).equals(rolled), "no reroll while bonded");
		// death keeps it
		player.die(player.damageSources().generic());
		helper.assertTrue(SymbioteSuitColours.get(player).equals(rolled), "dying keeps the scheme");
		// losing the bond keeps it; bonding again rolls a new one
		Symbiote.remove(player);
		helper.assertTrue(SymbioteSuitColours.get(player).equals(rolled), "kept through losing the bond");
		player.getRandom().setSeed(99L);
		SymbioteColours second = SymbioteSuitColours.randomScheme(RandomSource.create(99L));
		Symbiote.grant(player);
		helper.assertTrue(SymbioteSuitColours.get(player).equals(second), "re-bonding rolls again");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theSuitHasNoEnchantmentFoil(GameTestHelper helper) {
		helper.assertFalse(new ItemStack(SymbioteHostItems.CHESTPLATE).hasFoil(), "a plain suit item (creative / JEI) has no foil");
		ServerPlayer player = host(helper);
		SymbioteSuit.equipAndCapture(player);
		for (EquipmentSlot slot : new EquipmentSlot[] { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET }) {
			ItemStack worn = player.getItemBySlot(slot);
			helper.assertTrue(worn.getItem() instanceof com.projecthero.mod.symbiote.item.SymbioteHostArmorItem, "suit piece on " + slot);
			helper.assertFalse(worn.hasFoil(), "the worn " + slot + " piece shows no enchantment glint (binding curse hidden)");
		}
		helper.succeed();
	}
}
