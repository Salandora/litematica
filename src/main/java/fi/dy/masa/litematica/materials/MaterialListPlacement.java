package fi.dy.masa.litematica.materials;

import java.util.List;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import net.minecraft.client.resources.I18n;

public class MaterialListPlacement extends MaterialListBase
{
    private final SchematicPlacement placement;

    public MaterialListPlacement(SchematicPlacement placement)
    {
        super();

        this.placement = placement;
    }

    @Override
    public String getName()
    {
        return this.placement.getName();
    }

    @Override
    public String getTitle()
    {
        return I18n.format("litematica.gui.title.material_list.placement", this.getName());
    }

    @Override
    protected List<MaterialListEntry> createMaterialListEntries()
    {
        return MaterialListUtils.createMaterialListFor(this.placement);
    }
}
