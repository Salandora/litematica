package fi.dy.masa.litematica.render;

import javax.annotation.Nullable;

import fi.dy.masa.malilib.render.shader.ShaderProgram;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import fi.dy.masa.litematica.config.Configs;
import fi.dy.masa.litematica.config.Hotkeys;
import fi.dy.masa.litematica.render.schematic.WorldRendererSchematic;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.Entity;
import net.minecraft.util.BlockRenderLayer;

public class LitematicaRenderer
{
    private static final LitematicaRenderer INSTANCE = new LitematicaRenderer();

    private static final ShaderProgram SHADER_ALPHA = new ShaderProgram("litematica", null, "shaders/alpha.frag");

    private Minecraft mc;
    private WorldRendererSchematic worldRenderer;
    private int frameCount;
    private long finishTimeNano;

    private Entity entity;
    private ICamera camera;
    private boolean renderPiecewise;
    private boolean renderPiecewiseSchematic;
    private boolean renderPiecewiseBlocks;
    private boolean renderPiecewisePrepared;
    private boolean translucentSchematic;

    static
    {
        int program = SHADER_ALPHA.getProgram();
        GL20.glUseProgram(program);
        GL20.glUniform1i(GL20.glGetUniformLocation(program, "texture"), 0);
        GL20.glUseProgram(0);
    }

    public static LitematicaRenderer getInstance()
    {
        return INSTANCE;
    }

    public WorldRendererSchematic getWorldRenderer()
    {
        if (this.worldRenderer == null)
        {
            this.mc = Minecraft.getInstance();
            this.worldRenderer = new WorldRendererSchematic(this.mc);
        }

        return this.worldRenderer;
    }

    public void loadRenderers()
    {
        this.getWorldRenderer().loadRenderers();
    }

    public void onSchematicWorldChanged(@Nullable WorldClient worldClient)
    {
        this.getWorldRenderer().setWorldAndLoadRenderers(worldClient);
    }

    private void calculateFinishTime()
    {
        long fpsLimit = this.mc.gameSettings.limitFramerate;
        long fpsMin = Math.min(Minecraft.getDebugFPS(), fpsLimit);
        fpsMin = Math.max(fpsMin, 60L);

        if (Configs.Generic.RENDER_THREAD_NO_TIMEOUT.getBooleanValue())
        {
            this.finishTimeNano = Long.MAX_VALUE;
        }
        else
        {
            this.finishTimeNano = System.nanoTime() + Math.max(1000000000L / fpsMin / 2L, 0L);
        }
    }

    public void renderSchematicWorld(float partialTicks)
    {
        if (this.mc.skipRenderWorld == false)
        {
            this.mc.profiler.startSection("litematica_schematic_world_render");
            this.mc.profiler.startSection("litematica_level");

            if (this.mc.getRenderViewEntity() == null)
            {
                this.mc.setRenderViewEntity(this.mc.player);
            }

            GlStateManager.pushMatrix();
            GlStateManager.enableDepthTest();

            this.calculateFinishTime();
            this.renderWorld(partialTicks, this.finishTimeNano);
            this.cleanup();

            GlStateManager.popMatrix();

            this.mc.profiler.endSection();
            this.mc.profiler.endSection();
        }
    }

    private void renderWorld(float partialTicks, long finishTimeNano)
    {
        this.mc.profiler.endStartSection("litematica_culling");
        Entity entity = this.mc.getRenderViewEntity();
        ICamera icamera = this.createCamera(entity, partialTicks);

        GlStateManager.shadeModel(GL11.GL_SMOOTH);

        this.mc.profiler.endStartSection("litematica_prepare_terrain");
        this.mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        RenderHelper.disableStandardItemLighting();
        WorldRendererSchematic renderGlobal = this.getWorldRenderer();

        this.mc.profiler.endStartSection("litematica_terrain_setup");
        worldRenderer.setupTerrain(entity, partialTicks, icamera, this.frameCount++, this.mc.player.isSpectator());

        this.mc.profiler.endStartSection("litematica_update_chunks");
        renderGlobal.updateChunks(finishTimeNano);

        this.mc.profiler.endStartSection("litematica_terrain");
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.disableAlphaTest();
        GlStateManager.enableBlend();

        if (Configs.Visuals.ENABLE_SCHEMATIC_BLOCKS.getBooleanValue())
        {
            GlStateManager.pushMatrix();

            if (Configs.Visuals.RENDER_COLLIDING_SCHEMATIC_BLOCKS.getBooleanValue())
            {
                GlStateManager.enablePolygonOffset();
                GlStateManager.polygonOffset(-0.2f, -0.4f);
            }

            this.startShaderIfEnabled();

            GlStateManager.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            renderGlobal.renderBlockLayer(BlockRenderLayer.SOLID, partialTicks, entity);

            renderGlobal.renderBlockLayer(BlockRenderLayer.CUTOUT_MIPPED, partialTicks, entity);

            this.mc.getTextureManager().getTexture(TextureMap.LOCATION_BLOCKS_TEXTURE).setBlurMipmap(false, false);
            renderGlobal.renderBlockLayer(BlockRenderLayer.CUTOUT, partialTicks, entity);
            this.mc.getTextureManager().getTexture(TextureMap.LOCATION_BLOCKS_TEXTURE).restoreLastBlurMipmap();

            if (Configs.Visuals.RENDER_COLLIDING_SCHEMATIC_BLOCKS.getBooleanValue())
            {
                GlStateManager.polygonOffset(0f, 0f);
                GlStateManager.disablePolygonOffset();
            }

            GlStateManager.disableBlend();
            GlStateManager.shadeModel(GL11.GL_FLAT);
            GlStateManager.alphaFunc(GL11.GL_GREATER, 0.01F);

            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            GlStateManager.popMatrix();

            this.mc.profiler.endStartSection("litematica_entities");

            GlStateManager.pushMatrix();
            RenderHelper.enableStandardItemLighting();
            GlStateManager.enableBlend();
            GlStateManager.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);

            worldRenderer.renderEntities(entity, icamera, partialTicks);

            GlStateManager.disableFog(); // Fixes Structure Blocks breaking all rendering
            GlStateManager.disableBlend();
            RenderHelper.disableStandardItemLighting();

            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            GlStateManager.popMatrix();

            GlStateManager.enableCull();
            GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
            this.mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            GlStateManager.shadeModel(GL11.GL_SMOOTH);

            this.mc.profiler.endStartSection("litematica_translucent");
            GlStateManager.depthMask(false);

            GlStateManager.pushMatrix();
            GlStateManager.enableBlend();
            GlStateManager.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);

            renderGlobal.renderBlockLayer(BlockRenderLayer.TRANSLUCENT, partialTicks, entity);

            GlStateManager.popMatrix();

            this.disableShader();
        }

        this.mc.profiler.endStartSection("litematica_overlay");
        this.renderSchematicOverlay();

        GlStateManager.enableAlphaTest();
        GlStateManager.disableBlend();
        GlStateManager.depthMask(true);
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.enableCull();
    }

    public void renderSchematicOverlay()
    {
        boolean invert = Hotkeys.INVERT_OVERLAY_RENDER_STATE.getKeybind().isKeybindHeld();

        if (Configs.Visuals.ENABLE_SCHEMATIC_OVERLAY.getBooleanValue() != invert)
        {
            boolean renderThrough = Configs.Visuals.SCHEMATIC_OVERLAY_RENDER_THROUGH.getBooleanValue() || Hotkeys.RENDER_OVERLAY_THROUGH_BLOCKS.getKeybind().isKeybindHeld();
            float lineWidth = (float) (renderThrough ? Configs.Visuals.SCHEMATIC_OVERLAY_OUTLINE_WIDTH_THROUGH.getDoubleValue() : Configs.Visuals.SCHEMATIC_OVERLAY_OUTLINE_WIDTH.getDoubleValue());

            GlStateManager.pushMatrix();
            GlStateManager.disableTexture2D();
            GlStateManager.disableCull();
            GlStateManager.alphaFunc(GL11.GL_GREATER, 0.001F);
            GlStateManager.enablePolygonOffset();
            GlStateManager.polygonOffset(-0.4f, -0.8f);
            GlStateManager.enableBlend();
            GlStateManager.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
            GlStateManager.lineWidth(lineWidth);
            GlStateManager.color4f(1f, 1f, 1f, 1f);
            OpenGlHelper.glMultiTexCoord2f(OpenGlHelper.GL_TEXTURE1, 240, 240);

            if (renderThrough)
            {
                GlStateManager.disableDepthTest();
            }

            this.getWorldRenderer().renderBlockOverlays();

            GlStateManager.enableDepthTest();
            GlStateManager.polygonOffset(0f, 0f);
            GlStateManager.disablePolygonOffset();
            GlStateManager.enableTexture2D();
            GlStateManager.popMatrix();
        }
    }

    public void startShaderIfEnabled()
    {
        this.translucentSchematic = Configs.Visuals.RENDER_BLOCKS_AS_TRANSLUCENT.getBooleanValue() && OpenGlHelper.shadersSupported;

        if (this.translucentSchematic)
        {
            float alpha = (float) Configs.Visuals.GHOST_BLOCK_ALPHA.getDoubleValue();
            GL20.glUseProgram(SHADER_ALPHA.getProgram());
            GL20.glUniform1f(GL20.glGetUniformLocation(SHADER_ALPHA.getProgram(), "alpha_multiplier"), alpha);
        }
    }

    public void disableShader()
    {
        if (this.translucentSchematic)
        {
            GL20.glUseProgram(0);
        }
    }

    public void piecewisePrepareAndUpdate(float partialTicks)
    {
        this.renderPiecewise = Configs.Generic.BETTER_RENDER_ORDER.getBooleanValue() &&
                               Configs.Visuals.ENABLE_RENDERING.getBooleanValue() &&
                               this.mc.getRenderViewEntity() != null;
        this.renderPiecewisePrepared = false;
        this.renderPiecewiseBlocks = false;

        if (this.renderPiecewise)
        {
            boolean invert = Hotkeys.INVERT_GHOST_BLOCK_RENDER_STATE.getKeybind().isKeybindHeld();
            this.renderPiecewiseSchematic = Configs.Visuals.ENABLE_SCHEMATIC_RENDERING.getBooleanValue() != invert;
            this.renderPiecewiseBlocks = this.renderPiecewiseSchematic && Configs.Visuals.ENABLE_SCHEMATIC_BLOCKS.getBooleanValue();

            this.mc.profiler.startSection("litematica_culling");

            Entity entity = this.mc.getRenderViewEntity();
            ICamera icamera = this.createCamera(entity, partialTicks);

            this.calculateFinishTime();
            WorldRendererSchematic renderGlobal = this.getWorldRenderer();

            this.mc.profiler.endStartSection("litematica_terrain_setup");
            renderGlobal.setupTerrain(entity, partialTicks, icamera, this.frameCount++, this.mc.player.isSpectator());

            this.mc.profiler.endStartSection("litematica_update_chunks");
            renderGlobal.updateChunks(this.finishTimeNano);

            this.mc.profiler.endSection();

            this.renderPiecewisePrepared = true;
        }
    }

    public void piecewiseRenderSolid(boolean renderColliding, float partialTicks)
    {
        if (this.renderPiecewiseBlocks)
        {
            this.mc.profiler.endStartSection("litematica_blocks_solid");

            if (renderColliding)
            {
                GlStateManager.enablePolygonOffset();
                GlStateManager.polygonOffset(-0.3f, -0.6f);
            }

            this.startShaderIfEnabled();

            this.getWorldRenderer().renderBlockLayer(BlockRenderLayer.SOLID, partialTicks, this.entity);

            this.disableShader();

            if (renderColliding)
            {
                GlStateManager.polygonOffset(0f, 0f);
                GlStateManager.disablePolygonOffset();
            }
        }
    }

    public void piecewiseRenderCutoutMipped(boolean renderColliding, float partialTicks)
    {
        if (this.renderPiecewiseBlocks)
        {
            this.mc.profiler.endStartSection("litematica_blocks_cutout_mipped");

            if (renderColliding)
            {
                GlStateManager.enablePolygonOffset();
                GlStateManager.polygonOffset(-0.3f, -0.6f);
            }

            this.startShaderIfEnabled();

            this.getWorldRenderer().renderBlockLayer(BlockRenderLayer.CUTOUT_MIPPED, partialTicks, this.entity);

            this.disableShader();

            if (renderColliding)
            {
                GlStateManager.polygonOffset(0f, 0f);
                GlStateManager.disablePolygonOffset();
            }
        }
    }

    public void piecewiseRenderCutout(boolean renderColliding, float partialTicks)
    {
        if (this.renderPiecewiseBlocks)
        {
            this.mc.profiler.endStartSection("litematica_blocks_cutout");

            if (renderColliding)
            {
                GlStateManager.enablePolygonOffset();
                GlStateManager.polygonOffset(-0.3f, -0.6f);
            }

            this.startShaderIfEnabled();

            this.getWorldRenderer().renderBlockLayer(BlockRenderLayer.CUTOUT, partialTicks, this.entity);

            this.disableShader();

            if (renderColliding)
            {
                GlStateManager.polygonOffset(0f, 0f);
                GlStateManager.disablePolygonOffset();
            }
        }
    }

    public void piecewiseRenderTranslucent(boolean renderColliding, float partialTicks)
    {
        if (this.renderPiecewisePrepared)
        {
            if (this.renderPiecewiseBlocks)
            {
                this.mc.profiler.endStartSection("litematica_translucent");

                if (renderColliding)
                {
                    GlStateManager.enablePolygonOffset();
                    GlStateManager.polygonOffset(-0.3f, -0.6f);
                }

                this.startShaderIfEnabled();

                this.getWorldRenderer().renderBlockLayer(BlockRenderLayer.TRANSLUCENT, partialTicks, this.entity);

                this.disableShader();

                if (renderColliding)
                {
                    GlStateManager.polygonOffset(0f, 0f);
                    GlStateManager.disablePolygonOffset();
                }
            }

            if (this.renderPiecewiseSchematic)
            {
                this.mc.profiler.endStartSection("litematica_overlay");
                this.renderSchematicOverlay();
            }

            this.cleanup();
        }
    }

    public void piecewiseRenderEntities(float partialTicks)
    {
        if (this.renderPiecewiseBlocks)
        {
            this.mc.profiler.endStartSection("litematica_entities");

            GlStateManager.enableBlend();
            GlStateManager.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);

            this.startShaderIfEnabled();

            this.getWorldRenderer().renderEntities(this.entity, this.camera, partialTicks);

            this.disableShader();

            GlStateManager.disableBlend();
        }
    }

    private ICamera createCamera(Entity entity, float partialTicks)
    {
        double x = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * (double) partialTicks;
        double y = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * (double) partialTicks;
        double z = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * (double) partialTicks;

        this.entity = entity;
        this.camera = new Frustum();
        this.camera.setPosition(x, y, z);

        return this.camera;
    }

    private void cleanup()
    {
        this.entity = null;
        this.camera = null;
        this.renderPiecewise = false;
        this.renderPiecewisePrepared = false;
        this.renderPiecewiseBlocks = false;
    }
}
