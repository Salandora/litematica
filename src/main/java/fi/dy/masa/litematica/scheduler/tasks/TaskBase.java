package fi.dy.masa.litematica.scheduler.tasks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import fi.dy.masa.litematica.config.Configs;
import fi.dy.masa.litematica.render.infohud.IInfoHudRenderer;
import fi.dy.masa.litematica.render.infohud.RenderPhase;
import fi.dy.masa.litematica.scheduler.ITask;
import fi.dy.masa.litematica.scheduler.TaskTimer;
import fi.dy.masa.litematica.util.PositionUtils;
import fi.dy.masa.litematica.world.WorldSchematic;
import fi.dy.masa.malilib.interfaces.ICompletionListener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.text.TextFormatting;

public abstract class TaskBase implements ITask, IInfoHudRenderer
{
    private TaskTimer timer = new TaskTimer(1);

    protected final Minecraft mc;
    protected List<String> infoHudLines = new ArrayList<>();
    @Nullable protected ICompletionListener completionListener;

    protected TaskBase()
    {
        this.mc = Minecraft.getInstance();
    }

    @Override
    public TaskTimer getTimer()
    {
        return this.timer;
    }

    @Override
    public void createTimer(int interval)
    {
        this.timer = new TaskTimer(interval);
    }

    public void setCompletionListener(ICompletionListener listener)
    {
        this.completionListener = listener;
    }

    @Override
    public boolean canExecute()
    {
        return this.mc.world != null;
    }

    @Override
    public boolean shouldRemove()
    {
        return this.canExecute() == false;
    }

    @Override
    public void init()
    {
    }

    @Override
    public void stop()
    {
    }

    protected boolean areSurroundingChunksLoaded(ChunkPos pos, WorldClient world, int radius)
    {
        if (radius <= 0)
        {
            return world.isChunkLoaded(pos.x, pos.z, false);
        }

        int chunkX = pos.x;
        int chunkZ = pos.z;

        for (int cx = chunkX - radius; cx <= chunkX + radius; ++cx)
        {
            for (int cz = chunkZ - radius; cz <= chunkZ + radius; ++cz)
            {
                if (world.isChunkLoaded(cx, cz, false) == false)
                {
                    return false;
                }
            }
        }

        return true;
    }

    protected void updateInfoHudLinesMissingChunks(Set<ChunkPos> requiredChunks)
    {
        List<String> hudLines = new ArrayList<>();
        EntityPlayer player = this.mc.player;

        if (player != null)
        {
            List<ChunkPos> list = new ArrayList<>();
            list.addAll(requiredChunks);
            PositionUtils.CHUNK_POS_COMPARATOR.setReferencePosition(new BlockPos(player.getPositionVector()));
            PositionUtils.CHUNK_POS_COMPARATOR.setClosestFirst(true);
            Collections.sort(list, PositionUtils.CHUNK_POS_COMPARATOR);

            String pre = TextFormatting.WHITE.toString() + TextFormatting.BOLD.toString();
            String title = I18n.format("litematica.gui.label.missing_chunks", requiredChunks.size());
            hudLines.add(String.format("%s%s%s", pre, title, TextFormatting.RESET.toString()));

            int maxLines = Math.min(list.size(), Configs.InfoOverlays.INFO_HUD_MAX_LINES.getIntegerValue());

            for (int i = 0; i < maxLines; ++i)
            {
                ChunkPos pos = list.get(i);
                hudLines.add(String.format("cx: %5d, cz: %5d (x: %d, z: %d)", pos.x, pos.z, pos.x << 4, pos.z << 4));
            }
        }

        this.infoHudLines = hudLines;
    }

    @Override
    public boolean getShouldRenderText(RenderPhase phase)
    {
        return phase == RenderPhase.POST;
    }

    @Override
    public List<String> getText(RenderPhase phase)
    {
        return this.infoHudLines;
    }
}
