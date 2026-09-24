package com.ae2powertools.client.model;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

import javax.annotation.Nullable;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ICustomModelLoader;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import net.minecraftforge.common.model.IModelState;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.parts.IPartBakedModel;
import appeng.client.render.cablebus.CubeBuilder;

import com.ae2powertools.Tags;
import com.ae2powertools.features.monitor.dependent.DisplayLogic;


/**
 * Loads the three storage-display corner overlays as AE2 {@link IPartBakedModel}s.
 * <p>
 * A cable bus is rendered as AE2's single {@code BlockCableBus}, whose block-color handler
 * owns JSON tint indices for the cable color. Consequently a normal JSON {@code tintindex}
 * cannot access the storage display's live color. AE2 instead passes each part's render flag
 * to {@link IPartBakedModel#getPartQuads(Long, long)} while rebuilding the cable-bus model.
 * This model writes that ARGB value into the quad vertex colors before the quads are returned.
 */
@SideOnly(Side.CLIENT)
@Mod.EventBusSubscriber(modid = Tags.MODID, value = Side.CLIENT)
public final class StorageDisplayCornerModelLoader implements ICustomModelLoader {

    private static final StorageDisplayCornerModelLoader INSTANCE = new StorageDisplayCornerModelLoader();

    @SubscribeEvent
    public static void registerModels(ModelRegistryEvent event) {
        ModelLoaderRegistry.registerLoader(INSTANCE);
    }

    @Override
    public boolean accepts(ResourceLocation modelLocation) {
        return ModelDefinition.forLocation(modelLocation) != null;
    }

    @Override
    public IModel loadModel(ResourceLocation modelLocation) {
        ModelDefinition definition = ModelDefinition.forLocation(modelLocation);
        if (definition == null) {
            throw new IllegalArgumentException("Unsupported storage display corner model: " + modelLocation);
        }
        return new StorageDisplayCornerModel(definition);
    }

    @Override
    public void onResourceManagerReload(IResourceManager resourceManager) {
        // The atlas supplies fresh sprites after a resource reload; the model is rebaked then
    }

    private enum ModelDefinition {
        FULL_SIZE(StorageDisplayCornerModels.FULL_SIZE,
            new ResourceLocation(Tags.MODID, "blocks/display_color_corner"), 0.0F, 16.0F),
        SMALLER(StorageDisplayCornerModels.SMALLER,
            new ResourceLocation(Tags.MODID, "blocks/display_color_corner_smaller"), 1.0F, 15.0F),
        SMALLERER(StorageDisplayCornerModels.SMALLERER,
            new ResourceLocation(Tags.MODID, "blocks/display_color_corner_smallerer"), 2.0F, 14.0F);

        private final ResourceLocation loaderLocation;
        private final ResourceLocation texture;
        private final float minimum;
        private final float maximum;

        ModelDefinition(ResourceLocation modelLocation, ResourceLocation texture, float minimum, float maximum) {
            this.loaderLocation = new ResourceLocation(modelLocation.getNamespace(),
                "models/" + modelLocation.getPath());
            this.texture = texture;
            this.minimum = minimum;
            this.maximum = maximum;
        }

        @Nullable
        private static ModelDefinition forLocation(ResourceLocation location) {
            for (ModelDefinition definition : values()) {
                if (definition.loaderLocation.equals(location)) return definition;
            }

            return null;
        }
    }

    private static final class StorageDisplayCornerModel implements IModel {

        private final ModelDefinition definition;

        private StorageDisplayCornerModel(ModelDefinition definition) {
            this.definition = definition;
        }

        @Override
        public List<ResourceLocation> getTextures() {
            // AE2 requests this model during CableBusModel dependency baking.
            return Collections.singletonList(definition.texture);
        }

        @Override
        public IBakedModel bake(IModelState state, VertexFormat format,
                Function<ResourceLocation, TextureAtlasSprite> bakedTextureGetter) {
            return new StorageDisplayCornerBakedModel(
                format, bakedTextureGetter.apply(definition.texture), definition.minimum, definition.maximum);
        }
    }

    private static final class StorageDisplayCornerBakedModel implements IBakedModel, IPartBakedModel {

        /** Only the idle, above, and below states are normally present at once. */
        private static final int MAX_CACHED_COLORS = 4;

        private final VertexFormat format;
        private final TextureAtlasSprite texture;
        private final float minimum;
        private final float maximum;
        private final LoadingCache<Integer, List<BakedQuad>> quadsByColor;

        private StorageDisplayCornerBakedModel(VertexFormat format, TextureAtlasSprite texture,
                float minimum, float maximum) {
            this.format = format;
            this.texture = texture;
            this.minimum = minimum;
            this.maximum = maximum;
            this.quadsByColor = CacheBuilder.newBuilder()
                .maximumSize(MAX_CACHED_COLORS)
                .build(new CacheLoader<Integer, List<BakedQuad>>() {
                    @Override
                    public List<BakedQuad> load(Integer color) {
                        return StorageDisplayCornerBakedModel.this.buildQuads(color);
                    }
                });
        }

        @Override
        public List<BakedQuad> getPartQuads(@Nullable Long partFlags, long rand) {
            int color = partFlags == null ? DisplayLogic.getIdleCornerColor() : (int) (long) partFlags;
            try {
                return quadsByColor.get(color);
            } catch (ExecutionException e) {
                // Do not make one malformed cache entry suppress the entire cable-bus model.
                return buildQuads(color);
            }
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long rand) {
            if (side != null) return Collections.emptyList();
            return getPartQuads(null, rand);
        }

        private List<BakedQuad> buildQuads(int color) {
            CubeBuilder builder = new CubeBuilder(format);
            builder.setTexture(texture);
            builder.setColor(color);

            // CubeBuilder.addQuad takes block-space coordinates (0..1), unlike addCube,
            // which converts JSON-style 0..16 pixel coordinates itself. The display model
            // definitions are intentionally in JSON pixels to match their collision/model
            // files, so normalise them here before making the single north-front quad.

            // The center and corner sprites have interlocking opaque pixels, so sharing z = 0
            // matches the static screen-center model without z-fighting.
            builder.addQuad(EnumFacing.NORTH,
                minimum / 16.0F, minimum / 16.0F, 0.0F,
                maximum / 16.0F, maximum / 16.0F, 0.0F);
            return Collections.unmodifiableList(builder.getOutput());
        }

        @Override
        public boolean isAmbientOcclusion() {
            return false;
        }

        @Override
        public boolean isGui3d() {
            return true;
        }

        @Override
        public boolean isBuiltInRenderer() {
            return false;
        }

        @Override
        public TextureAtlasSprite getParticleTexture() {
            return texture;
        }

        @Override
        public ItemOverrideList getOverrides() {
            return ItemOverrideList.NONE;
        }
    }
}
