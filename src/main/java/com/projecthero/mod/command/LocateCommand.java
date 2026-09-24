package com.projecthero.mod.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.datafixers.util.Pair;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * v0.12.16: {@code /projecthero locate <structure>} -- finds the nearest structure added by this mod
 * (every {@code projecthero:} structure in the registry), op-only. The same search vanilla
 * {@code /locate structure} runs, restricted to this mod's structures for tab completion.
 */
public final class LocateCommand {
	private static final int SEARCH_RADIUS_CHUNKS = 200;

	private static final SuggestionProvider<CommandSourceStack> STRUCTURES = (ctx, builder) -> {
		Registry<Structure> registry = ctx.getSource().getServer().registryAccess().registryOrThrow(Registries.STRUCTURE);
		return SharedSuggestionProvider.suggest(registry.keySet().stream()
				.filter(id -> id.getNamespace().equals(ProjectHeroMod.MOD_ID))
				.map(ResourceLocation::getPath), builder);
	};

	private LocateCommand() {
	}

	public static LiteralArgumentBuilder<CommandSourceStack> build() {
		return Commands.literal("locate")
				.requires(source -> source.hasPermission(2))
				.then(Commands.argument("structure", StringArgumentType.word()).suggests(STRUCTURES)
						.executes(LocateCommand::locate));
	}

	private static int locate(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		String path = StringArgumentType.getString(c, "structure");
		CommandSourceStack source = c.getSource();
		ServerLevel level = source.getLevel();
		Registry<Structure> registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
		ResourceKey<Structure> key = ResourceKey.create(Registries.STRUCTURE, ProjectHeroMod.id(path));
		var holder = registry.getHolder(key);
		if (holder.isEmpty()) {
			source.sendFailure(Component.literal("Unknown Project Hero structure: " + path));
			return 0;
		}
		BlockPos here = BlockPos.containing(source.getPosition());
		Pair<BlockPos, Holder<Structure>> found = level.getChunkSource().getGenerator()
				.findNearestMapStructure(level, HolderSet.direct(holder.get()), here, SEARCH_RADIUS_CHUNKS, false);
		if (found == null) {
			source.sendFailure(Component.literal("No " + path + " found within " + SEARCH_RADIUS_CHUNKS
					+ " chunks in this dimension."));
			return 0;
		}
		BlockPos at = found.getFirst();
		int dist = (int) Math.round(Math.sqrt(here.distSqr(new BlockPos(at.getX(), here.getY(), at.getZ()))));
		Component coords = Component.literal("[" + at.getX() + ", ~, " + at.getZ() + "]")
				.withStyle(style -> style.withColor(net.minecraft.ChatFormatting.GREEN)
						.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
								"/tp @s " + at.getX() + " ~ " + at.getZ()))
						.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to copy a teleport"))));
		source.sendSuccess(() -> Component.literal("The nearest " + path + " is at ").append(coords)
				.append(Component.literal(" (" + dist + " blocks away)")), false);
		return dist;
	}
}
