package fi.dy.masa.litematica.gui;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.gui.GuiConfigs.ConfigGuiTab;
import fi.dy.masa.malilib.gui.GuiRenderLayerEditBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.gui.interfaces.IGuiIcon;
import fi.dy.masa.malilib.gui.widgets.WidgetCheckBox;
import fi.dy.masa.malilib.util.LayerRange;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;

public class GuiRenderLayer extends GuiRenderLayerEditBase
{
    @Override
    public void initGui()
    {
        super.initGui();

        int x = 10;
        int y = 26;

        x += this.createTabButton(x, y, -1, ConfigGuiTab.GENERIC);
        x += this.createTabButton(x, y, -1, ConfigGuiTab.INFO_OVERLAYS);
        x += this.createTabButton(x, y, -1, ConfigGuiTab.VISUALS);
        x += this.createTabButton(x, y, -1, ConfigGuiTab.COLORS);
        x += this.createTabButton(x, y, -1, ConfigGuiTab.HOTKEYS);
        x += this.createTabButton(x, y, -1, ConfigGuiTab.RENDER_LAYERS);

        x = 10;
        y = 60;

        this.createLayerEditControls(x, y, this.getLayerRange());
    }

    @Override
    protected LayerRange getLayerRange()
    {
        return DataManager.getRenderLayerRange();
    }

    @Override
    protected IGuiIcon getValueAdjustButtonIcon()
    {
        return Icons.BUTTON_PLUS_MINUS_16;
    }

    private int createTabButton(int x, int y, int width, ConfigGuiTab tab)
    {
        ButtonListenerTab listener = new ButtonListenerTab(tab);
        boolean enabled = DataManager.getConfigGuiTab() != tab;
        String label = tab.getDisplayName();

        if (width < 0)
        {
            width = this.mc.fontRenderer.getStringWidth(label) + 10;
        }

        ButtonGeneric button = new ButtonGeneric(x, y, width, 20, label);
        button.enabled = enabled;
        this.addButton(button, listener);

        return width + 2;
    }

    @Override
    protected int createHotkeyCheckBoxes(int x, int y, LayerRange layerRange)
    {
        String label = I18n.format("litematica.gui.label.render_layers.hotkey");
        String hover = I18n.format("litematica.gui.label.render_layers.hover.hotkey");

        WidgetCheckBox cb = new WidgetCheckBox(x, y + 4, this.zLevel, Icons.CHECKBOX_UNSELECTED, Icons.CHECKBOX_SELECTED, label, this.mc, hover);
        cb.setChecked(layerRange.getMoveLayerRangeMax(), false);
        cb.setListener(new RangeHotkeyListener(layerRange, true));
        this.addWidget(cb);

        y += 23;
        cb = new WidgetCheckBox(x, y + 4, this.zLevel, Icons.CHECKBOX_UNSELECTED, Icons.CHECKBOX_SELECTED, label, this.mc, hover);
        cb.setChecked(layerRange.getMoveLayerRangeMin(), false);
        cb.setListener(new RangeHotkeyListener(layerRange, false));
        this.addWidget(cb);

        return y;
    }

    private static class ButtonListenerTab implements IButtonActionListener<ButtonGeneric>
    {
        private final ConfigGuiTab tab;

        public ButtonListenerTab(ConfigGuiTab tab)
        {
            this.tab = tab;
        }

        @Override
        public void actionPerformed(ButtonGeneric control)
        {
        }

        @Override
        public void actionPerformedWithButton(ButtonGeneric control, int mouseButton)
        {
            DataManager.setConfigGuiTab(this.tab);
            Minecraft.getMinecraft().displayGuiScreen(new GuiConfigs());
        }
    }
}
