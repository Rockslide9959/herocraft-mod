package com.projecthero.mod.gametest;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.network.SymbioteColourPayload;
import com.projecthero.mod.symbiote.Symbiote;
import com.projecthero.mod.symbiote.SymbioteColourPreset;
import com.projecthero.mod.symbiote.SymbioteColours;
import com.projecthero.mod.symbiote.SymbioteSuitColours;
import com.projecthero.mod.symbiote.SymbioteSuitStyle;

import io.netty.buffer.Unpooled;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

/**
 * v0.17.5 Symbiote suit style (Classic / Lethal Protector): it rides in the colours attachment, so it defaults to Classic (old saves
 * without the field too), saves, survives a death and losing the bond, syncs and travels over the network; {@code /symbiote style}
 * sets it and keeps the colours, the colour commands and presets keep the style; both styles take the player's colours.
 */
public class SymbioteSuitStyleGameTests implements FabricGameTest {
	private static ServerPlayer host(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		Symbiote.grant(player);
		return player;
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void defaultIsClassicAndOldSavesReadClassic(GameTestHelper helper) {
		ServerPlayer player = host(helper);
		helper.assertTrue(SymbioteSuitColours.get(player).style() == SymbioteSuitStyle.CLASSIC, "a new host wears the Classic suit");
		helper.assertTrue(SymbioteColours.CLASSIC.style() == SymbioteSuitStyle.CLASSIC, "the default look is the Classic suit");
		helper.assertTrue(new SymbioteColours(1, 2, 3).style() == SymbioteSuitStyle.CLASSIC, "three colours mean Classic");
		helper.assertTrue(new SymbioteColours(1, 2, 3, null).style() == SymbioteSuitStyle.CLASSIC, "a null style is Classic");
		// a v0.17.5 colours save from before the style existed
		CompoundTag old = new CompoundTag();
		old.putInt("primary", 0x1E4FB8);
		old.putInt("secondary", 0xC81E2A);
		old.putInt("eyes", 0xF4F6FA);
		SymbioteColours parsed = SymbioteColours.CODEC.parse(NbtOps.INSTANCE, old).getOrThrow();
		helper.assertTrue(parsed.style() == SymbioteSuitStyle.CLASSIC && parsed.primary() == 0x1E4FB8, "an old save loads as Classic");
		CompoundTag lp = old.copy();
		lp.putString("style", "lethal_protector");
		helper.assertTrue(SymbioteColours.CODEC.parse(NbtOps.INSTANCE, lp).getOrThrow().style() == SymbioteSuitStyle.LETHAL_PROTECTOR,
				"a saved Lethal Protector loads");
		CompoundTag junk = old.copy();
		junk.putString("style", "banana");
		helper.assertTrue(SymbioteColours.CODEC.parse(NbtOps.INSTANCE, junk).getOrThrow().style() == SymbioteSuitStyle.CLASSIC,
				"an unknown style loads as Classic");
		helper.assertTrue(SymbioteSuitStyle.byOrdinal(-1) == SymbioteSuitStyle.CLASSIC && SymbioteSuitStyle.byOrdinal(7) == SymbioteSuitStyle.CLASSIC,
				"out-of-range ordinals are Classic");
		helper.assertTrue(SymbioteSuitStyle.byId(" Lethal_Protector ").orElse(null) == SymbioteSuitStyle.LETHAL_PROTECTOR, "byId, trimmed");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void styleSurvivesSaveDeathAndLosingTheBond(GameTestHelper helper) {
		ServerPlayer player = host(helper);
		SymbioteColours mine = new SymbioteColours(0xB3121B, 0x1A0A0C, 0xF6F1EA, SymbioteSuitStyle.LETHAL_PROTECTOR);
		SymbioteSuitColours.set(player, mine);
		CompoundTag saved = player.saveWithoutId(new CompoundTag()).getCompound("fabric:attachments");
		SymbioteColours back = SymbioteColours.CODEC.parse(NbtOps.INSTANCE, saved.get("projecthero:symbiote_colours")).getOrThrow();
		helper.assertTrue(back.equals(mine), "the save reads back the same style and colours: " + back);
		player.die(player.damageSources().generic());
		helper.assertTrue(SymbioteSuitColours.get(player).style() == SymbioteSuitStyle.LETHAL_PROTECTOR, "dying keeps the style");
		Symbiote.remove(player);
		helper.assertTrue(SymbioteSuitColours.get(player).equals(mine), "kept through losing the bond");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void styleSyncsAndTheNetworkKeepsIt(GameTestHelper helper) {
		helper.assertTrue(ModAttachments.SYMBIOTE_COLOURS.isSynced(), "synced (other players must see your suit)");
		for (SymbioteSuitStyle style : SymbioteSuitStyle.values()) {
			SymbioteColours mine = new SymbioteColours(0x5B2A86, 0x121016, 0xF2E9FF, style);
			RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
			SymbioteColourPayload.CODEC.encode(buf, new SymbioteColourPayload(mine));
			helper.assertTrue(SymbioteColourPayload.CODEC.decode(buf).colours().equals(mine), "the payload round-trips " + style.id());
			RegistryFriendlyByteBuf sync = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
			SymbioteColours.STREAM_CODEC.encode(sync, mine);
			helper.assertTrue(SymbioteColours.STREAM_CODEC.decode(sync).equals(mine), "the attachment sync round-trips " + style.id());
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void commandSetsTheStyleAndKeepsColours(GameTestHelper helper) {
		ServerPlayer player = host(helper);
		SymbioteSuitColours.setPreset(player, SymbioteColourPreset.TOXIN);
		var source = player.createCommandSourceStack().withPermission(4).withSuppressedOutput();
		var commands = helper.getLevel().getServer().getCommands();
		commands.performPrefixedCommand(source, "projecthero power tune symbiote style lethal_protector");
		SymbioteColours now = SymbioteSuitColours.get(player);
		helper.assertTrue(now.style() == SymbioteSuitStyle.LETHAL_PROTECTOR, "/symbiote style lethal_protector sets it");
		helper.assertTrue(now.sameColours(SymbioteColourPreset.TOXIN.colours()), "and keeps the colours");
		commands.performPrefixedCommand(source, "projecthero power tune symbiote colour carnage");
		now = SymbioteSuitColours.get(player);
		helper.assertTrue(now.style() == SymbioteSuitStyle.LETHAL_PROTECTOR && now.sameColours(SymbioteColourPreset.CARNAGE.colours()),
				"/symbiote colour <preset> keeps the style");
		commands.performPrefixedCommand(source, "projecthero power tune symbiote colour hex 102030 405060 708090");
		now = SymbioteSuitColours.get(player);
		helper.assertTrue(now.style() == SymbioteSuitStyle.LETHAL_PROTECTOR && now.primary() == 0x102030, "/symbiote colour hex keeps the style");
		commands.performPrefixedCommand(source, "projecthero power tune symbiote style classic");
		now = SymbioteSuitColours.get(player);
		helper.assertTrue(now.style() == SymbioteSuitStyle.CLASSIC && now.primary() == 0x102030, "/symbiote style classic, colours kept");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void bothStylesTakeColours(GameTestHelper helper) {
		for (SymbioteSuitStyle style : SymbioteSuitStyle.values()) {
			helper.assertTrue(style.recolourable(), style.id() + " takes the player's colours");
		}
		SymbioteColours lpClassic = SymbioteColours.CLASSIC.withStyle(SymbioteSuitStyle.LETHAL_PROTECTOR);
		helper.assertTrue(lpClassic.hasClassicColours() && !lpClassic.isClassic(), "Classic colours on the Lethal Protector are not the default look");
		helper.assertTrue(SymbioteColourPreset.matching(lpClassic).orElse(null) == SymbioteColourPreset.CLASSIC, "presets match on either style");
		helper.assertTrue(!lpClassic.key().equals(SymbioteColours.CLASSIC.key()), "each style bakes its own texture");
		helper.assertTrue("14161f_f2f4f8_f4f6fa".equals(SymbioteColours.CLASSIC.key()), "Classic-style keys are unchanged");
		SymbioteColours carnage = SymbioteColourPreset.CARNAGE.colours().withStyle(SymbioteSuitStyle.LETHAL_PROTECTOR);
		helper.assertTrue(carnage.withPrimary(0x123456).style() == SymbioteSuitStyle.LETHAL_PROTECTOR
				&& carnage.withChannel(2, 0x00FF00).style() == SymbioteSuitStyle.LETHAL_PROTECTOR, "editing a colour keeps the style");
		// the screen's payload: style and colours together, bonded only
		ServerPlayer stranger = helper.makeMockServerPlayerInLevel();
		helper.assertFalse(SymbioteSuitColours.apply(stranger, carnage), "no bond, refused");
		ServerPlayer host = host(helper);
		helper.assertTrue(SymbioteSuitColours.apply(host, carnage), "bonded, applied");
		helper.assertTrue(SymbioteSuitColours.get(host).equals(carnage), "stored with its style");
		SymbioteSuitColours.setPreset(host, SymbioteColourPreset.SCREAM);
		helper.assertTrue(SymbioteSuitColours.get(host).style() == SymbioteSuitStyle.LETHAL_PROTECTOR, "a preset keeps the style");
		helper.succeed();
	}
}
