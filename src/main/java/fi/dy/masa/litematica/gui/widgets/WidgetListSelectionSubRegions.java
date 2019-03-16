package fi.dy.masa.litematica.gui.widgets;

import java.util.Collection;
import java.util.Comparator;
import fi.dy.masa.litematica.gui.GuiAreaSelectionEditorNormal;
import fi.dy.masa.litematica.gui.Icons;
import fi.dy.masa.litematica.selection.AreaSelection;
import fi.dy.masa.malilib.gui.LeftRight;
import fi.dy.masa.malilib.gui.widgets.WidgetListBase;
import fi.dy.masa.malilib.gui.widgets.WidgetSearchBar;
import fi.dy.masa.malilib.util.AlphaNumComparator.AlphaNumStringComparator;
import net.minecraft.client.Minecraft;

public class WidgetListSelectionSubRegions extends WidgetListBase<String, WidgetSelectionSubRegion>
{
    private final GuiAreaSelectionEditorNormal gui;
    private final AreaSelection selection;

    public WidgetListSelectionSubRegions(int x, int y, int width, int height,
            AreaSelection selection, GuiAreaSelectionEditorNormal gui)
    {
        super(x, y, width, height, gui);

        this.gui = gui;
        this.selection = selection;
        this.browserEntryHeight = 22;
        this.widgetSearchBar = new WidgetSearchBar(x + 2, y + 4, width - 14, 14, zLevel, 0, Icons.FILE_ICON_SEARCH, LeftRight.LEFT, Minecraft.getInstance());
        this.browserEntriesOffsetY = this.widgetSearchBar.getHeight() + 3;
        this.shouldSortList = true;
    }

    public GuiAreaSelectionEditorNormal getEditorGui()
    {
        return this.gui;
    }

    @Override
    protected Collection<String> getAllEntries()
    {
        return this.selection.getAllSubRegionNames();
    }

    @Override
    protected Comparator<String> getComparator()
    {
        return new AlphaNumStringComparator();
    }

    @Override
    protected boolean entryMatchesFilter(String entry, String filterText)
    {
        return entry.toLowerCase().indexOf(filterText) != -1;
    }

    @Override
    protected WidgetSelectionSubRegion createListEntryWidget(int x, int y, int listIndex, boolean isOdd, String entry)
    {
        return new WidgetSelectionSubRegion(x, y, this.browserEntryWidth, this.browserEntryHeight,
                this.zLevel, isOdd, entry, listIndex, this.mc, this.selection, this);
    }
}
