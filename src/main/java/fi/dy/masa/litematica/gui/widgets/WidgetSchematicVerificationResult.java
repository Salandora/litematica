package fi.dy.masa.litematica.gui.widgets;

import java.util.List;
import javax.annotation.Nullable;
import fi.dy.masa.litematica.config.Configs;
import fi.dy.masa.litematica.gui.GuiSchematicVerifier;
import fi.dy.masa.litematica.gui.GuiSchematicVerifier.BlockMismatchEntry;
import fi.dy.masa.litematica.gui.Icons;
import fi.dy.masa.litematica.schematic.verifier.SchematicVerifier;
import fi.dy.masa.litematica.schematic.verifier.SchematicVerifier.BlockMismatch;
import fi.dy.masa.litematica.schematic.verifier.SchematicVerifier.MismatchType;
import fi.dy.masa.litematica.schematic.verifier.SchematicVerifier.SortCriteria;
import fi.dy.masa.litematica.util.BlockUtils;
import fi.dy.masa.litematica.util.ItemUtils;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.gui.widgets.WidgetListEntrySortable;
import fi.dy.masa.malilib.render.RenderUtils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockFlowerPot;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.BlockModelShapes;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.resources.I18n;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.IRegistry;

public class WidgetSchematicVerificationResult extends WidgetListEntrySortable<BlockMismatchEntry>
{
    public static final String HEADER_EXPECTED = "litematica.gui.label.schematic_verifier.expected";
    public static final String HEADER_FOUND = "litematica.gui.label.schematic_verifier.found";
    public static final String HEADER_COUNT = "litematica.gui.label.schematic_verifier.count";

    private static int maxNameLengthExpected;
    private static int maxNameLengthFound;
    private static int maxCountLength;

    private final BlockModelShapes blockModelShapes;
    private final GuiSchematicVerifier guiSchematicVerifier;
    private final WidgetListSchematicVerificationResults listWidget;
    private final SchematicVerifier verifier;
    private final BlockMismatchEntry mismatchEntry;
    @Nullable private final String header1;
    @Nullable private final String header2;
    @Nullable private final String header3;
    @Nullable private final BlockMismatchInfo mismatchInfo;
    private final Minecraft mc;
    private final int count;
    private final boolean isOdd;
    @Nullable private final ButtonGeneric buttonIgnore;

    public WidgetSchematicVerificationResult(int x, int y, int width, int height, float zLevel, boolean isOdd,
            WidgetListSchematicVerificationResults listWidget, GuiSchematicVerifier guiSchematicVerifier,
            BlockMismatchEntry entry, int listIndex)
    {
        super(x, y, width, height, zLevel, entry, listIndex);

        this.columnCount = 3;
        this.mc = Minecraft.getInstance();
        this.blockModelShapes = this.mc.getBlockRendererDispatcher().getBlockModelShapes();
        this.mismatchEntry = entry;
        this.guiSchematicVerifier = guiSchematicVerifier;
        this.listWidget = listWidget;
        this.verifier = guiSchematicVerifier.getPlacement().getSchematicVerifier();
        this.isOdd = isOdd;

        // Main header
        if (entry.header1 != null && entry.header2 != null)
        {
            this.header1 = entry.header1;
            this.header2 = entry.header2;
            this.header3 = GuiBase.TXT_BOLD + I18n.format(HEADER_COUNT) + GuiBase.TXT_RST;
            this.mismatchInfo = null;
            this.count = 0;
            this.buttonIgnore = null;
        }
        // Category title
        else if (entry.header1 != null)
        {
            this.header1 = entry.header1;
            this.header2 = null;
            this.header3 = null;
            this.mismatchInfo = null;
            this.count = 0;
            this.buttonIgnore = null;
        }
        // Mismatch entry
        else
        {
            this.header1 = null;
            this.header2 = null;
            this.header3 = null;
            this.mismatchInfo = new BlockMismatchInfo(entry.blockMismatch.stateExpected, entry.blockMismatch.stateFound);
            this.count = entry.blockMismatch.count;

            if (entry.mismatchType != MismatchType.CORRECT_STATE)
            {
                this.buttonIgnore = this.createButton(this.x + this.width, y + 1, ButtonListener.ButtonType.IGNORE_MISMATCH);
            }
            else
            {
                this.buttonIgnore = null;
            }
        }
    }

    public static void setMaxNameLengths(List<BlockMismatch> mismatches, Minecraft mc)
    {
        FontRenderer font = mc.fontRenderer;
        maxNameLengthExpected = font.getStringWidth(GuiBase.TXT_BOLD + I18n.format(HEADER_EXPECTED) + GuiBase.TXT_RST);
        maxNameLengthFound = font.getStringWidth(GuiBase.TXT_BOLD + I18n.format(HEADER_FOUND) + GuiBase.TXT_RST);
        maxCountLength = 7 * font.getStringWidth("8");

        for (BlockMismatch entry : mismatches)
        {
            ItemStack stack = ItemUtils.getItemForState(entry.stateExpected);
            String name = BlockMismatchInfo.getDisplayName(entry.stateExpected, stack);
            maxNameLengthExpected = Math.max(maxNameLengthExpected, font.getStringWidth(name));

            stack = ItemUtils.getItemForState(entry.stateFound);
            name = BlockMismatchInfo.getDisplayName(entry.stateFound, stack);
            maxNameLengthFound = Math.max(maxNameLengthFound, font.getStringWidth(name));
        }

        maxCountLength = Math.max(maxCountLength, font.getStringWidth(GuiBase.TXT_BOLD + I18n.format(HEADER_COUNT) + GuiBase.TXT_RST));
    }

    private ButtonGeneric createButton(int x, int y, ButtonListener.ButtonType type)
    {
        String label = I18n.format(type.getLabelKey());
        int buttonWidth = mc.fontRenderer.getStringWidth(label) + 10;
        x -= buttonWidth;
        ButtonGeneric button = new ButtonGeneric(x, y, buttonWidth, 20, label);
        this.addButton(button, new ButtonListener(type, this.mismatchEntry, this.guiSchematicVerifier));

        return button;
    }

    @Override
    protected int getCurrentSortColumn()
    {
        return this.verifier.getSortCriteria().ordinal();
    }

    @Override
    protected boolean getSortInReverse()
    {
        return this.verifier.getSortInReverse();
    }

    @Override
    protected int getColumnPosX(int column)
    {
        int x1 = this.x + 4;
        int x2 = x1 + maxNameLengthExpected + 40; // including item icon
        int x3 = x2 + maxNameLengthFound + 40;

        switch (column)
        {
            case 0: return x1;
            case 1: return x2;
            case 2: return x3;
            case 3: return x3 + maxCountLength + 20;
            default: return x1;
        }
    }

    @Override
    protected boolean onMouseClickedImpl(int mouseX, int mouseY, int mouseButton)
    {
        if (super.onMouseClickedImpl(mouseX, mouseY, mouseButton))
        {
            return true;
        }

        if (this.mismatchEntry.type != BlockMismatchEntry.Type.HEADER)
        {
            return false;
        }

        int column = this.getMouseOverColumn(mouseX, mouseY);

        switch (column)
        {
            case 0:
                this.verifier.setSortCriteria(SortCriteria.NAME_EXPECTED);
                break;
            case 1:
                this.verifier.setSortCriteria(SortCriteria.NAME_FOUND);
                break;
            case 2:
                this.verifier.setSortCriteria(SortCriteria.COUNT);
                break;
            default:
                return false;
        }

        // Re-create the widgets
        this.listWidget.refreshEntries();

        return true;
    }

    @Override
    public boolean canSelectAt(int mouseX, int mouseY, int mouseButton)
    {
        return (this.buttonIgnore == null || mouseX < this.buttonIgnore.x) && super.canSelectAt(mouseX, mouseY, mouseButton);
    }

    protected boolean shouldRenderAsSelected()
    {
        if (this.mismatchEntry.type == BlockMismatchEntry.Type.CATEGORY_TITLE)
        {
            return this.verifier.isMismatchCategorySelected(this.mismatchEntry.mismatchType);
        }
        else if (this.mismatchEntry.type == BlockMismatchEntry.Type.DATA)
        {
            return this.verifier.isMismatchEntrySelected(this.mismatchEntry.blockMismatch);
        }

        return false;
    }

    @Override
    public void render(int mouseX, int mouseY, boolean selected)
    {
        selected = this.shouldRenderAsSelected();

        // Default color for even entries
        int color = 0xA0303030;

        // Draw a lighter background for the hovered and the selected entry
        if (selected)
        {
            color = 0xA0707070;
        }
        else if (this.isMouseOver(mouseX, mouseY))
        {
            color = 0xA0505050;
        }
        // Draw a slightly darker background for odd entries
        else if (this.isOdd)
        {
            color = 0xA0101010;
        }

        GuiBase.drawRect(this.x, this.y, this.x + this.width, this.y + this.height, color);

        if (selected)
        {
            RenderUtils.drawOutline(this.x, this.y, this.width, this.height, 0xFFE0E0E0);
        }

        Minecraft mc = this.mc;
        int x1 = this.getColumnPosX(0);
        int x2 = this.getColumnPosX(1);
        int x3 = this.getColumnPosX(2);
        int y = this.y + 7;
        color = 0xFFFFFFFF;

        if (this.header1 != null && this.header2 != null)
        {
            mc.fontRenderer.drawString(this.header1, x1, y, color);
            mc.fontRenderer.drawString(this.header2, x2, y, color);
            mc.fontRenderer.drawString(this.header3, x3, y, color);

            this.renderColumnHeader(mouseX, mouseY, Icons.ARROW_DOWN, Icons.ARROW_UP);
        }
        else if (this.header1 != null)
        {
            mc.fontRenderer.drawString(this.header1, this.x + 4, this.y + 7, color);
        }
        else if (this.mismatchInfo != null &&
                (this.mismatchEntry.mismatchType != MismatchType.CORRECT_STATE ||
                 this.mismatchEntry.blockMismatch.stateExpected.isAir() == false))
        {
            mc.fontRenderer.drawString(this.mismatchInfo.nameExpected, x1 + 20, y, color);

            if (this.mismatchEntry.mismatchType != MismatchType.CORRECT_STATE)
            {
                mc.fontRenderer.drawString(this.mismatchInfo.nameFound, x2 + 20, y, color);
            }

            mc.fontRenderer.drawString(String.valueOf(this.count), x3, y, color);

            y = this.y + 3;
            Gui.drawRect(x1, y, x1 + 16, y + 16, 0x20FFFFFF); // light background for the item

            boolean useBlockModelConfig = Configs.Visuals.SCHEMATIC_VERIFIER_BLOCK_MODELS.getBooleanValue();
            boolean hasModelExpected = this.mismatchInfo.stateExpected.getRenderType() == EnumBlockRenderType.MODEL;
            boolean hasModelFound    = this.mismatchInfo.stateFound.getRenderType() == EnumBlockRenderType.MODEL;
            boolean isAirItemExpected = this.mismatchInfo.stackExpected.isEmpty();
            boolean isAirItemFound    = this.mismatchInfo.stackExpected.isEmpty();
            boolean useBlockModelExpected = hasModelExpected && (isAirItemExpected || useBlockModelConfig || this.mismatchInfo.stateExpected.getBlock() == Blocks.FLOWER_POT);
            boolean useBlockModelFound    = hasModelFound    && (isAirItemFound    || useBlockModelConfig || this.mismatchInfo.stateFound.getBlock() == Blocks.FLOWER_POT);

            GlStateManager.pushMatrix();
            RenderHelper.enableGUIStandardItemLighting();

            IBakedModel model;

            if (useBlockModelExpected)
            {
                model = this.blockModelShapes.getModelForState(this.mismatchInfo.stateExpected);
                RenderUtils.renderModelInGui(x1, y, model, this.mismatchInfo.stateExpected, 1);
            }
            else
            {
                mc.getRenderItem().renderItemAndEffectIntoGUI(mc.player, this.mismatchInfo.stackExpected, x1, y);
                mc.getRenderItem().renderItemOverlayIntoGUI(mc.fontRenderer, this.mismatchInfo.stackExpected, x1, y, null);
            }

            if (this.mismatchEntry.mismatchType != MismatchType.CORRECT_STATE)
            {
                Gui.drawRect(x2, y, x2 + 16, y + 16, 0x20FFFFFF); // light background for the item

                if (useBlockModelFound)
                {
                    model = this.blockModelShapes.getModelForState(this.mismatchInfo.stateFound);
                    RenderUtils.renderModelInGui(x2, y, model, this.mismatchInfo.stateFound, 1);
                }
                else
                {
                    mc.getRenderItem().renderItemAndEffectIntoGUI(mc.player, this.mismatchInfo.stackFound, x2, y);
                    mc.getRenderItem().renderItemOverlayIntoGUI(mc.fontRenderer, this.mismatchInfo.stackFound, x2, y, null);
                }
            }

            GlStateManager.disableBlend();
            RenderHelper.disableStandardItemLighting();
            GlStateManager.popMatrix();
        }

        super.render(mouseX, mouseY, selected);
    }

    @Override
    public void postRenderHovered(int mouseX, int mouseY, boolean selected)
    {
        if (this.mismatchInfo != null && this.buttonIgnore != null && mouseX < this.buttonIgnore.x)
        {
            GlStateManager.pushMatrix();
            GlStateManager.translatef(0f, 0f, 200f);

            int x = mouseX + 10;
            int y = mouseY;
            int width = this.mismatchInfo.getTotalWidth();
            int height = this.mismatchInfo.getTotalHeight();

            if (x + width > this.mc.currentScreen.width)
            {
                x = mouseX - width - 10;
            }

            if (y + height > this.mc.currentScreen.height)
            {
                y = mouseY - height - 2;
            }

            this.mismatchInfo.render(x, y, this.mc);

            GlStateManager.popMatrix();
        }
    }

    public static class BlockMismatchInfo
    {
        private final IBlockState stateExpected;
        private final IBlockState stateFound;
        private final ItemStack stackExpected;
        private final ItemStack stackFound;
        private final String blockRegistrynameExpected;
        private final String blockRegistrynameFound;
        private final String nameExpected;
        private final String nameFound;
        private final int totalWidth;
        private final int totalHeight;
        private final int columnWidthExpected;

        public BlockMismatchInfo(IBlockState stateExpected, IBlockState stateFound)
        {
            this.stateExpected = stateExpected;
            this.stateFound = stateFound;

            this.stackExpected = ItemUtils.getItemForState(this.stateExpected);
            this.stackFound = ItemUtils.getItemForState(this.stateFound);

            Minecraft mc = Minecraft.getInstance();
            Block blockExpected = this.stateExpected.getBlock();
            Block blockFound = this.stateFound.getBlock();
            ResourceLocation rl1 = Block.REGISTRY.getNameForObject(blockExpected);
            ResourceLocation rl2 = Block.REGISTRY.getNameForObject(blockFound);

            this.blockRegistrynameExpected = rl1 != null ? rl1.toString() : "<null>";
            this.blockRegistrynameFound = rl2 != null ? rl2.toString() : "<null>";

            this.nameExpected = getDisplayName(stateExpected, this.stackExpected);
            this.nameFound =    getDisplayName(stateFound,    this.stackFound);

            List<String> propsExpected = BlockUtils.getFormattedBlockStateProperties(this.stateExpected);
            List<String> propsFound = BlockUtils.getFormattedBlockStateProperties(this.stateFound);

            int w1 = Math.max(mc.fontRenderer.getStringWidth(this.nameExpected) + 20, mc.fontRenderer.getStringWidth(this.blockRegistrynameExpected));
            int w2 = Math.max(mc.fontRenderer.getStringWidth(this.nameFound) + 20, mc.fontRenderer.getStringWidth(this.blockRegistrynameFound));
            w1 = Math.max(w1, fi.dy.masa.litematica.render.RenderUtils.getMaxStringRenderLength(propsExpected, mc));
            w2 = Math.max(w2, fi.dy.masa.litematica.render.RenderUtils.getMaxStringRenderLength(propsFound, mc));

            this.columnWidthExpected = w1;
            this.totalWidth = this.columnWidthExpected + w2 + 40;
            this.totalHeight = Math.max(propsExpected.size(), propsFound.size()) * (mc.fontRenderer.FONT_HEIGHT + 2) + 60;
        }

        public static String getDisplayName(IBlockState state, ItemStack stack)
        {
            Block block = state.getBlock();
            String key = block.getTranslationKey() + ".name";
            String name = I18n.format(key);
            name = key.equals(name) == false ? name : stack.getDisplayName();

            if (block == Blocks.FLOWER_POT && state.getValue(BlockFlowerPot.CONTENTS) != BlockFlowerPot.EnumFlowerType.EMPTY)
            {
                name = ((new ItemStack(Items.FLOWER_POT)).getDisplayName()) + " & " + name;
            }

            return name;
        }

        public int getTotalWidth()
        {
            return this.totalWidth;
        }

        public int getTotalHeight()
        {
            return this.totalHeight;
        }

        public void render(int x, int y, Minecraft mc)
        {
            if (this.stateExpected != null && this.stateFound != null)
            {
                GlStateManager.pushMatrix();

                RenderUtils.drawOutlinedBox(x, y, this.totalWidth, this.totalHeight, 0xFF000000, GuiBase.COLOR_HORIZONTAL_BAR);

                int x1 = x + 10;
                int x2 = x + this.columnWidthExpected + 30;
                y += 4;

                String pre = GuiBase.TXT_WHITE + GuiBase.TXT_BOLD;
                String strExpected = pre + I18n.format("litematica.gui.label.schematic_verifier.expected") + GuiBase.TXT_RST;
                String strFound =    pre + I18n.format("litematica.gui.label.schematic_verifier.found") + GuiBase.TXT_RST;
                mc.fontRenderer.drawString(strExpected, x1, y, 0xFFFFFFFF);
                mc.fontRenderer.drawString(strFound,    x2, y, 0xFFFFFFFF);

                y += 12;

                GlStateManager.disableLighting();
                RenderHelper.enableGUIStandardItemLighting();

                boolean useBlockModelConfig = Configs.Visuals.SCHEMATIC_VERIFIER_BLOCK_MODELS.getBooleanValue();
                boolean hasModelExpected = this.stateExpected.getRenderType() == EnumBlockRenderType.MODEL;
                boolean hasModelFound    = this.stateFound.getRenderType() == EnumBlockRenderType.MODEL;
                boolean isAirItemExpected = this.stackExpected.isEmpty();
                boolean isAirItemFound    = this.stackExpected.isEmpty();
                boolean useBlockModelExpected = hasModelExpected && (isAirItemExpected || useBlockModelConfig || this.stateExpected.getBlock() == Blocks.FLOWER_POT);
                boolean useBlockModelFound    = hasModelFound    && (isAirItemFound    || useBlockModelConfig || this.stateFound.getBlock() == Blocks.FLOWER_POT);
                BlockModelShapes blockModelShapes = mc.getBlockRendererDispatcher().getBlockModelShapes();

                //mc.getRenderItem().zLevel += 100;
                Gui.drawRect(x1, y, x1 + 16, y + 16, 0x50C0C0C0); // light background for the item
                Gui.drawRect(x2, y, x2 + 16, y + 16, 0x50C0C0C0); // light background for the item

                IBakedModel model;

                if (useBlockModelExpected)
                {
                    model = blockModelShapes.getModelForState(this.stateExpected);
                    RenderUtils.renderModelInGui(x1, y, model, this.stateExpected, 1);
                }
                else
                {
                    mc.getRenderItem().renderItemAndEffectIntoGUI(mc.player, this.stackExpected, x1, y);
                    mc.getRenderItem().renderItemOverlayIntoGUI(mc.fontRenderer, this.stackExpected, x1, y, null);
                }

                if (useBlockModelFound)
                {
                    model = blockModelShapes.getModelForState(this.stateFound);
                    RenderUtils.renderModelInGui(x2, y, model, this.stateFound, 1);
                }
                else
                {
                    mc.getRenderItem().renderItemAndEffectIntoGUI(mc.player, this.stackFound, x2, y);
                    mc.getRenderItem().renderItemOverlayIntoGUI(mc.fontRenderer, this.stackFound, x2, y, null);
                }

                //mc.getRenderItem().zLevel -= 100;

                //GlStateManager.disableBlend();
                RenderHelper.disableStandardItemLighting();

                mc.fontRenderer.drawString(this.nameExpected, x1 + 20, y + 4, 0xFFFFFFFF);
                mc.fontRenderer.drawString(this.nameFound,    x2 + 20, y + 4, 0xFFFFFFFF);

                y += 20;
                mc.fontRenderer.drawString(this.blockRegistrynameExpected, x1, y, 0xFF4060FF);
                mc.fontRenderer.drawString(this.blockRegistrynameFound,    x2, y, 0xFF4060FF);
                y += mc.fontRenderer.FONT_HEIGHT + 4;

                List<String> propsExpected = BlockUtils.getFormattedBlockStateProperties(this.stateExpected);
                List<String> propsFound = BlockUtils.getFormattedBlockStateProperties(this.stateFound);
                RenderUtils.renderText(x1, y, 0xFFB0B0B0, propsExpected, mc.fontRenderer);
                RenderUtils.renderText(x2, y, 0xFFB0B0B0, propsFound, mc.fontRenderer);

                GlStateManager.popMatrix();
            }
        }
    }

    private static class ButtonListener implements IButtonActionListener<ButtonGeneric>
    {
        private final ButtonType type;
        private final GuiSchematicVerifier guiSchematicVerifier;
        private final BlockMismatchEntry mismatchEntry;

        public ButtonListener(ButtonType type, BlockMismatchEntry mismatchEntry, GuiSchematicVerifier guiSchematicVerifier)
        {
            this.type = type;
            this.mismatchEntry = mismatchEntry;
            this.guiSchematicVerifier = guiSchematicVerifier;
        }

        @Override
        public void actionPerformed(ButtonGeneric control)
        {
            if (this.type == ButtonType.IGNORE_MISMATCH)
            {
                this.guiSchematicVerifier.getPlacement().getSchematicVerifier().ignoreStateMismatch(this.mismatchEntry.blockMismatch);
                this.guiSchematicVerifier.initGui();
            }
        }

        @Override
        public void actionPerformedWithButton(ButtonGeneric control, int mouseButton)
        {
            this.actionPerformed(control);
        }

        public enum ButtonType
        {
            IGNORE_MISMATCH ("litematica.gui.button.schematic_verifier.ignore");

            private final String labelKey;

            private ButtonType(String labelKey)
            {
                this.labelKey = labelKey;
            }

            public String getLabelKey()
            {
                return this.labelKey;
            }
        }
    }
}
