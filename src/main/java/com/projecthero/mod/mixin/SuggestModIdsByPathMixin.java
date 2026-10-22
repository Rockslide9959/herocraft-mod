package com.projecthero.mod.mixin;

import java.util.function.Consumer;
import java.util.function.Function;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.16.7 (user): "/give rockslide titan..." should suggest Project Hero items. Vanilla only matches a typed word
 * against the PATH of {@code minecraft:} ids, so a mod item only came up once you typed {@code projecthero:}. This
 * matches the path of every namespace, so typing "titan" suggests {@code projecthero:titan_serum} as well. Typing a
 * colon keeps vanilla's full-id matching.
 */
@Mixin(SharedSuggestionProvider.class)
public interface SuggestModIdsByPathMixin {
	@Inject(method = "filterResources(Ljava/lang/Iterable;Ljava/lang/String;Ljava/util/function/Function;Ljava/util/function/Consumer;)V",
			at = @At("HEAD"), cancellable = true)
	private static <T> void projecthero$matchAnyNamespacePath(Iterable<T> resources, String input,
			Function<T, ResourceLocation> idOf, Consumer<T> out, CallbackInfo ci) {
		boolean full = input.indexOf(':') > -1;
		for (T t : resources) {
			ResourceLocation id = idOf.apply(t);
			if (full ? SharedSuggestionProvider.matchesSubStr(input, id.toString())
					: SharedSuggestionProvider.matchesSubStr(input, id.getNamespace())
							|| SharedSuggestionProvider.matchesSubStr(input, id.getPath())) {
				out.accept(t);
			}
		}
		ci.cancel();
	}
}
