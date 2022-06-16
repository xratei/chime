package dev.emi.chime.mixin;

import java.io.InputStreamReader;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;

import org.apache.logging.log4j.LogManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.emi.chime.override.ChimeArmorOverrideLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.ModelLoader;
import net.minecraft.client.render.model.UnbakedModel;
import net.minecraft.client.render.model.json.JsonUnbakedModel;
import net.minecraft.client.render.model.json.ModelOverride;
import net.minecraft.client.render.model.json.ModelOverrideList;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

@Mixin(JsonUnbakedModel.class)
public class JsonUnbakedModelMixin {
	@Unique
	private static final Gson GSON = new Gson();
	@Unique
	private boolean chimeOverrides = false;
	@Shadow
	private List<ModelOverride> overrides;
	@Shadow
	public String id;

	@Inject(at = @At("HEAD"), method = "compileOverrides")
	private void compileOverrides(ModelLoader modelLoader, JsonUnbakedModel parent, CallbackInfoReturnable<ModelOverrideList> info) {
		calculateChimeOverrides();
	}

	@Inject(at = @At("HEAD"), method = "getModelDependencies")
	private void getModelDependencies(CallbackInfoReturnable<Collection<Identifier>> info) {
		calculateChimeOverrides();
	}

	@Inject(at = @At("HEAD"), method = "getTextureDependencies")
	private void getTextureDependencies(Function<Identifier, UnbakedModel> unbakedModelGetter, Set<Pair<String, String>> unresolvedTextureReferences, CallbackInfoReturnable<Collection<SpriteIdentifier>> info) {
		calculateChimeOverrides();
	}

	@Unique
	void calculateChimeOverrides() {
		if (chimeOverrides) {
			return;
		}
		ModelOverride.Deserializer deserializer = new ChimeArmorOverrideLoader.Deserializer2();
		ResourceManager manager = MinecraftClient.getInstance().getResourceManager();
		try {
			if (id.contains("#") || id.contains(" ")) { // Gets rid of an error when loading vanilla models
				return;
			}
			Identifier baseId = new Identifier(id);
			Identifier path = new Identifier(baseId.getNamespace(), "overrides/" + baseId.getPath() + ".json");

			for (Resource r : manager.getAllResources(path)) {
				try (InputStreamReader reader = new InputStreamReader(r.getInputStream())) {
					JsonObject object = GSON.fromJson(reader, JsonObject.class);
					JsonArray arr = object.getAsJsonArray("overrides");
					for (JsonElement el : arr) {
						// What harm could passing null ever have?
						overrides.add(deserializer.deserialize(el, null, null));
					}
				} catch (Exception e) {
					LogManager.getLogger("chime").warn("[chime] Malformed json for item override: " + path.getPath(), e);
				}
			}

		} catch (Exception e) {
			LogManager.getLogger("chime").warn("[chime] IO error reading item overrides: " + id, e);
		}
		chimeOverrides = true;
	}
}
