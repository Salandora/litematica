package fi.dy.masa.litematica.gui;

import javax.annotation.Nullable;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.gui.widgets.WidgetListSchematicVersions;
import fi.dy.masa.litematica.gui.widgets.WidgetSchematicVersion;
import fi.dy.masa.litematica.schematic.projects.SchematicProject;
import fi.dy.masa.litematica.schematic.projects.SchematicVersion;
import fi.dy.masa.litematica.selection.SelectionManager;
import fi.dy.masa.litematica.util.SchematicUtils;
import fi.dy.masa.malilib.gui.GuiConfirmAction;
import fi.dy.masa.malilib.gui.GuiListBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.gui.interfaces.ISelectionListener;
import fi.dy.masa.malilib.interfaces.ICompletionListener;
import fi.dy.masa.malilib.interfaces.IConfirmationListener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.math.BlockPos;

public class GuiSchematicProjectManager extends GuiListBase<SchematicVersion, WidgetSchematicVersion, WidgetListSchematicVersions>
                                        implements ISelectionListener<SchematicVersion>, ICompletionListener
{
    private final SchematicProject project;

    public GuiSchematicProjectManager(SchematicProject project)
    {
        super(10, 24);

        this.project = project;
        this.title = I18n.format("litematica.gui.title.schematic_project_manager");
    }

    @Override
    protected int getBrowserWidth()
    {
        return this.width - 20;
    }

    @Override
    protected int getBrowserHeight()
    {
        return this.height - 74;
    }

    @Override
    public void initGui()
    {
        super.initGui();

        this.createElements();
    }

    private void createElements()
    {
        int x = 10;
        int y = this.height - 46;

        x += this.createButton(x, y, false, ButtonListener.Type.SAVE_VERSION);
        x += this.createButton(x, y, false, ButtonListener.Type.OPEN_AREA_EDITOR);
        x += this.createButton(x, y, false, ButtonListener.Type.MOVE_ORIGIN);
        x += this.createButton(x, y, false, ButtonListener.Type.PLACE_TO_WORLD);
        x += this.createButton(x, y, false, ButtonListener.Type.DELETE_AREA);

        y += 22;
        x = 10;
        x += this.createButton(x, y, false, ButtonListener.Type.OPEN_PROJECT_BROWSER);
        x += this.createButton(x, y, false, ButtonListener.Type.CLOSE_PROJECT);
    }

    private int createButton(int x, int y, boolean rightAlign, ButtonListener.Type type)
    {
        ButtonGeneric button = ButtonGeneric.createGeneric(x, y, -1, rightAlign, type.getTranslationKey());
        String hover = type.getHoverText();

        if (hover != null)
        {
            button.setHoverStrings(hover);
        }

        this.addButton(button, new ButtonListener(type, this));

        return button.getButtonWidth() + 2;
    }

    private void reCreateGuiElements()
    {
        this.clearButtons();
        this.clearWidgets();

        this.createElements();
    }

    @Override
    protected ISelectionListener<SchematicVersion> getSelectionListener()
    {
        return this;
    }

    @Override
    public void onSelectionChange(@Nullable SchematicVersion entry)
    {
        if (entry != null)
        {
            this.project.switchVersion(entry, true);
            this.getListWidget().refreshEntries();
        }

        this.reCreateGuiElements();
    }

    @Override
    public void onTaskCompleted()
    {
        this.getListWidget().refreshEntries();
    }

    @Override
    protected WidgetListSchematicVersions createListWidget(int listX, int listY)
    {
        return new WidgetListSchematicVersions(listX, listY, this.getBrowserWidth() - 186, this.getBrowserHeight(), this.zLevel, this.project, this);
    }

    private static class ButtonListener implements IButtonActionListener<ButtonGeneric>
    {
        private final Type type;
        private final GuiSchematicProjectManager gui;

        public ButtonListener(Type type, GuiSchematicProjectManager gui)
        {
            this.type = type;
            this.gui = gui;
        }

        @Override
        public void actionPerformed(ButtonGeneric control)
        {
        }

        @Override
        public void actionPerformedWithButton(ButtonGeneric control, int mouseButton)
        {
            if (this.type == Type.OPEN_PROJECT_BROWSER)
            {
                GuiSchematicProjectsBrowser gui = new GuiSchematicProjectsBrowser();
                this.gui.mc.displayGuiScreen(gui);
            }
            else if (this.type == Type.SAVE_VERSION)
            {
                SchematicUtils.saveSchematic(false);
            }
            else if (this.type == Type.OPEN_AREA_EDITOR)
            {
                SelectionManager manager = DataManager.getSelectionManager();

                if (manager.getCurrentSelection() != null)
                {
                    manager.openEditGui(this.gui.mc.currentScreen);
                }
            }
            else if (this.type == Type.PLACE_TO_WORLD)
            {
                PlaceToWorldExecutor executor = new PlaceToWorldExecutor();
                String title = "litematica.gui.title.schematic_projects.confirm_place_to_world";
                String msg = "litematica.gui.message.schematic_projects.confirm_place_to_world";
                GuiConfirmAction gui = new GuiConfirmAction(320, title, executor, this.gui, msg);
                this.gui.mc.displayGuiScreen(gui);
            }
            else if (this.type == Type.DELETE_AREA)
            {
                DeleteAreaExecutor executor = new DeleteAreaExecutor();
                String title = "litematica.gui.title.schematic_projects.confirm_delete_area";
                String msg = "litematica.gui.message.schematic_projects.confirm_delete_area";
                GuiConfirmAction gui = new GuiConfirmAction(320, title, executor, this.gui, msg);
                this.gui.mc.displayGuiScreen(gui);
            }
            else if (this.type == Type.MOVE_ORIGIN)
            {
                SchematicProject project = DataManager.getSchematicProjectsManager().getCurrentProject();

                if (project != null)
                {
                    project.setOrigin(new BlockPos(this.gui.mc.player));
                    this.gui.reCreateGuiElements();
                }
            }
            else if (this.type == Type.CLOSE_PROJECT)
            {
                DataManager.getSchematicProjectsManager().closeCurrentProject();
                GuiSchematicProjectsBrowser gui = new GuiSchematicProjectsBrowser();
                this.gui.mc.displayGuiScreen(gui);
            }
        }

        public enum Type
        {
            CLOSE_PROJECT           ("litematica.gui.button.schematic_projects.close_project"),
            DELETE_AREA             ("litematica.gui.button.schematic_projects.delete_area", "litematica.gui.button.hover.schematic_projects.delete_area"),
            MOVE_ORIGIN             ("litematica.gui.button.schematic_projects.move_origin_to_player", "litematica.gui.button.hover.schematic_projects.move_origin_to_player"),
            OPEN_AREA_EDITOR        ("litematica.gui.button.schematic_projects.open_area_editor"),
            OPEN_PROJECT_BROWSER    ("litematica.gui.button.schematic_projects.open_project_browser"),
            PLACE_TO_WORLD          ("litematica.gui.button.schematic_projects.place_to_world", "litematica.gui.button.hover.schematic_projects.place_to_world_warning"),
            SAVE_VERSION            ("litematica.gui.button.schematic_projects.save_version", "litematica.gui.button.hover.schematic_projects.save_new_version");

            private final String translationKey;
            @Nullable private final String hoverText;

            private Type(String label)
            {
                this(label, null);
            }

            private Type(String translationKey, String hoverText)
            {
                this.translationKey = translationKey;
                this.hoverText = hoverText;
            }

            public String getTranslationKey()
            {
                return this.translationKey;
            }

            @Nullable
            public String getHoverText()
            {
                return this.hoverText != null ? I18n.format(this.hoverText) : null;
            }
        }
    }

    public static class PlaceToWorldExecutor implements IConfirmationListener
    {
        @Override
        public boolean onActionConfirmed()
        {
            DataManager.getSchematicProjectsManager().pasteCurrentVersionToWorld();
            return true;
        }

        @Override
        public boolean onActionCancelled()
        {
            return false;
        }
    }

    public static class DeleteAreaExecutor implements IConfirmationListener
    {
        @Override
        public boolean onActionConfirmed()
        {
            DataManager.getSchematicProjectsManager().deleteLastSeenArea(Minecraft.getMinecraft());
            return true;
        }

        @Override
        public boolean onActionCancelled()
        {
            return false;
        }
    }
}
