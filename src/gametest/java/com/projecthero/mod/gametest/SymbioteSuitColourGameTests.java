package com.projecthero.mod.gametest;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.network.SymbioteColourPayload;
import com.projecthero.mod.symbiote.Symbiote;
import com.projecthero.mod.symbiote.SymbioteColourPreset;
import com.projecthero.mod.symbiote.SymbioteColours;
import com.projecthero.mod.symbiote.SymbioteSuitColours;

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
 * v0.17.5 Symbiote suit colours: the colour triple is clamped to plain RGB on every way in, presets set exactly their colours,
 * the choice survives a save (relog) and a death, is synced to every player, travels intact over the network, and only a
 * bonded player can change it through the colour screen's payload.
 */
public class SymbioteSuitColourGameTests implements FabricGameTest {
	private static ServerPlayer host(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		Symbiote.grant(player);
		return player;
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void defaultIsClassicAndPresetsSetTheirColours(GameTestHelper helper) {
		helper.assertTrue(SymbioteSuitColours.get(helper.makeMockServerPlayerInLevel()).equals(SymbioteColours.CLASSIC),
				"the attachment default (never bonded) is Classic");
		ServerPlayer player = host(helper);
		helper.assertTrue(SymbioteColours.CLASSIC.equals(SymbioteColourPreset.CLASSIC.colours()), "CLASSIC constant = the Classic preset");
		for (SymbioteColourPreset preset : SymbioteColourPreset.values()) {
			SymbioteSuitColours.setPreset(player, preset);
			helper.assertTrue(SymbioteSuitColours.get(player).equals(preset.colours()), "preset " + preset.id() + " is stored as is");
			helper.assertTrue(SymbioteColourPreset.matching(SymbioteSuitColours.get(player)).orElse(null) == preset,
					"the screen recognises " + preset.id());
			helper.assertTrue(SymbioteColourPreset.byId(preset.id()).orElse(null) == preset, "byId(" + preset.id() + ")");
		}
		SymbioteSuitColours.setPreset(player, SymbioteColourPreset.ANTI_VENOM);
		SymbioteColours av = SymbioteSuitColours.get(player);
		helper.assertTrue(av.primary() == 0xEEF0F2 && av.secondary() == 0x16171C && av.eyes() == 0x101114,
				"Anti-Venom: white body, black emblem and eyes");
		SymbioteSuitColours.setPreset(player, SymbioteColourPreset.CARNAGE);
		helper.assertTrue(SymbioteSuitColours.get(player).primary() == 0xB3121B, "Carnage: red body");
		helper.assertTrue(SymbioteColours.CLASSIC.primary() == 0x14161F && SymbioteColours.CLASSIC.secondary() == 0xF2F4F8
				&& SymbioteColours.CLASSIC.eyes() == 0xF4F6FA, "Classic: black body, white emblem and eyes");
		helper.assertTrue(SymbioteColourPreset.byId("nope").isEmpty(), "an unknown preset id is empty");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void invalidInputIsClamped(GameTestHelper helper) {
		SymbioteColours c = new SymbioteColours(0xFF123456, -1, 0x1000000);
		helper.assertTrue(c.primary() == 0x123456, "the alpha byte is dropped, got " + Integer.toHexString(c.primary()));
		helper.assertTrue(c.secondary() == 0xFFFFFF, "-1 becomes white, got " + Integer.toHexString(c.secondary()));
		helper.assertTrue(c.eyes() == 0, "anything above 24 bits is dropped, got " + Integer.toHexString(c.eyes()));
		helper.assertTrue(SymbioteColours.rgb(300, -4, 128) == 0xFF0080, "channels clamp to 0..255");
		helper.assertTrue(SymbioteColours.parseHex("#1e4fb8").orElse(-1) == 0x1E4FB8, "#rrggbb");
		helper.assertTrue(SymbioteColours.parseHex("0xABCDEF").orElse(-1) == 0xABCDEF, "0xRRGGBB");
		helper.assertTrue(SymbioteColours.parseHex(" fff ").orElse(-1) == 0xFFFFFF, "short #rgb, trimmed");
		helper.assertTrue(SymbioteColours.parseHex("#12345").isEmpty(), "wrong length");
		helper.assertTrue(SymbioteColours.parseHex("#12345G").isEmpty(), "not hex");
		helper.assertTrue(SymbioteColours.parseHex("").isEmpty() && SymbioteColours.parseHex(null).isEmpty(), "empty / null");
		helper.assertTrue("#14161F".equals(SymbioteColours.hex(0xFF14161F)), "hex() prints #RRGGBB without alpha");
		// a save with out-of-range numbers loads clamped
		CompoundTag tag = new CompoundTag();
		tag.putInt("primary", 0x7F00FF00);
		tag.putInt("secondary", -16777216);
		SymbioteColours parsed = SymbioteColours.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
		helper.assertTrue(parsed.primary() == 0x00FF00 && parsed.secondary() == 0 && parsed.eyes() == SymbioteColours.CLASSIC.eyes(),
				"a bad save is clamped and a missing field is Classic");
		// and the server stores only clean values
		ServerPlayer player = host(helper);
		SymbioteSuitColours.set(player, new SymbioteColours(0xAA102030, 0xBB405060, 0xCC708090));
		SymbioteColours stored = SymbioteSuitColours.get(player);
		helper.assertTrue(stored.primary() == 0x102030 && stored.secondary() == 0x405060 && stored.eyes() == 0x708090, "stored clean");
		SymbioteSuitColours.set(player, null);
		helper.assertTrue(SymbioteSuitColours.get(player).isClassic(), "null resets to Classic");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void coloursSurviveRelogAndDeath(GameTestHelper helper) {
		ServerPlayer player = host(helper);
		SymbioteColours mine = new SymbioteColours(0x1E4FB8, 0xC81E2A, 0x33FF66);
		SymbioteSuitColours.set(player, mine);
		// relog: the attachment is written with the player and reads back through the codec
		helper.assertTrue(ModAttachments.SYMBIOTE_COLOURS.isPersistent(), "persistent");
		CompoundTag saved = player.saveWithoutId(new CompoundTag());
		CompoundTag attachments = saved.getCompound("fabric:attachments");
		helper.assertTrue(attachments.contains("projecthero:symbiote_colours"), "saved with the player: " + attachments.getAllKeys());
		SymbioteColours back = SymbioteColours.CODEC.parse(NbtOps.INSTANCE, attachments.get("projecthero:symbiote_colours")).getOrThrow();
		helper.assertTrue(back.equals(mine), "the save reads back the same colours");
		// death: Fabric copies a copyOnDeath attachment onto the respawned player instance (PlayerList#respawn, which a mock
		// player can't go through), and dying itself never touches it
		helper.assertTrue(ModAttachments.SYMBIOTE_COLOURS.copyOnDeath(), "copied on death");
		player.die(player.damageSources().generic());
		helper.assertTrue(SymbioteSuitColours.get(player).equals(mine), "dying keeps the colours");
		// losing the bond keeps the choice (a cosmetic preference): bond again and it is the same suit
		Symbiote.remove(player);
		helper.assertTrue(SymbioteSuitColours.get(player).equals(mine), "kept through losing the bond");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void coloursSyncToEveryoneAndTheNetworkKeepsThem(GameTestHelper helper) {
		helper.assertTrue(ModAttachments.SYMBIOTE_COLOURS.isSynced(), "synced (other players must see your colours)");
		SymbioteColours mine = new SymbioteColours(0xE6C21E, 0xC2241F, 0xF7F4E8);
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
		SymbioteColourPayload.CODEC.encode(buf, new SymbioteColourPayload(mine));
		helper.assertTrue(SymbioteColourPayload.CODEC.decode(buf).colours().equals(mine), "the payload round-trips");
		// a hand-made packet with junk in the alpha bytes decodes clamped
		RegistryFriendlyByteBuf junk = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
		junk.writeInt(0xFF00FF00);
		junk.writeInt(0x80FFFFFF);
		junk.writeInt(-1);
		junk.writeInt(0x7F000000); // v0.17.5: the mouth colour
		SymbioteColours decoded = SymbioteColourPayload.CODEC.decode(junk).colours();
		helper.assertTrue(decoded.primary() == 0x00FF00 && decoded.secondary() == 0xFFFFFF && decoded.eyes() == 0xFFFFFF
				&& decoded.mouth() == 0x000000, "junk packet clamped");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void onlyABondedPlayerCanRecolour(GameTestHelper helper) {
		ServerPlayer stranger = helper.makeMockServerPlayerInLevel();
		helper.assertFalse(SymbioteSuitColours.apply(stranger, SymbioteColourPreset.TOXIN.colours()), "no bond, refused");
		helper.assertTrue(SymbioteSuitColours.get(stranger).isClassic(), "unchanged");
		ServerPlayer host = host(helper);
		helper.assertTrue(SymbioteSuitColours.apply(host, SymbioteColourPreset.TOXIN.colours()), "bonded, applied");
		helper.assertTrue(SymbioteSuitColours.get(host).equals(SymbioteColourPreset.TOXIN.colours()), "stored");
		helper.assertFalse(SymbioteSuitColours.apply(host, null), "null refused");
		helper.succeed();
	}
}
