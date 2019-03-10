package fi.dy.masa.litematica.materials;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import com.google.common.collect.ImmutableMap;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.container.LitematicaBlockStateContainer;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SubRegionPlacement.RequiredEnabled;
import fi.dy.masa.litematica.selection.Box;
import fi.dy.masa.litematica.util.BlockInfoListType;
import fi.dy.masa.litematica.util.PositionUtils;
import fi.dy.masa.litematica.util.SchematicWorldRefresher;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import fi.dy.masa.malilib.util.InventoryUtils;
import fi.dy.masa.malilib.util.ItemType;
import fi.dy.masa.malilib.util.LayerRange;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;

public class MaterialListUtils
{
    public static List<MaterialListEntry> createMaterialListFor(LitematicaSchematic schematic)
    {
        return createMaterialListFor(schematic, schematic.getAreas().keySet());
    }

    public static List<MaterialListEntry> createMaterialListFor(LitematicaSchematic schematic, Collection<String> subRegions)
    {
        Object2IntOpenHashMap<IBlockState> countsTotal = new Object2IntOpenHashMap<>();

        for (String regionName : subRegions)
        {
            LitematicaBlockStateContainer container = schematic.getSubRegionContainer(regionName);

            if (container != null)
            {
                Vec3i size = container.getSize();
                final int sizeX = size.getX();
                final int sizeY = size.getY();
                final int sizeZ = size.getZ();

                for (int y = 0; y < sizeY; ++y)
                {
                    for (int z = 0; z < sizeZ; ++z)
                    {
                        for (int x = 0; x < sizeX; ++x)
                        {
                            IBlockState state = container.get(x, y, z);
                            countsTotal.addTo(state, 1);
                        }
                    }
                }
            }
        }

        Minecraft mc = Minecraft.getInstance();

        return getMaterialList(countsTotal, countsTotal, new Object2IntOpenHashMap<>(), mc.player);
    }

    public static List<MaterialListEntry> createMaterialListFor(SchematicPlacement placement)
    {
        Minecraft mc = Minecraft.getInstance();
        World worldSchematic = SchematicWorldHandler.getSchematicWorld();
        World worldClient = mc.world;

        if (mc.player == null || worldSchematic == null || worldClient == null)
        {
            return Collections.emptyList();
        }

        Object2IntOpenHashMap<IBlockState> countsTotal = new Object2IntOpenHashMap<>();
        Object2IntOpenHashMap<IBlockState> countsMissing = new Object2IntOpenHashMap<>();
        Object2IntOpenHashMap<IBlockState> countsMismatch = new Object2IntOpenHashMap<>();

        //if (placement.getMaterialListType() == BlockInfoListType.RENDER_LAYERS)
        {
            LayerRange range = DataManager.getRenderLayerRange();

            if (placement.getMaterialList().getMaterialListType() == BlockInfoListType.ALL)
            {
                range = new LayerRange(SchematicWorldRefresher.INSTANCE);
            }

            EnumFacing.Axis axis = range.getAxis();
            ImmutableMap<String, Box> subRegionBoxes = placement.getSubRegionBoxes(RequiredEnabled.PLACEMENT_ENABLED);
            BlockPos.MutableBlockPos posMutable = new BlockPos.MutableBlockPos();

            for (Map.Entry<String, Box> entry : subRegionBoxes.entrySet())
            {
                Box box = entry.getValue();
                BlockPos pos1 = PositionUtils.getMinCorner(box.getPos1(), box.getPos2());
                BlockPos pos2 = PositionUtils.getMaxCorner(box.getPos1(), box.getPos2());
                final int startX = axis == EnumFacing.Axis.X ? Math.max(pos1.getX(), range.getLayerMin()) : pos1.getX();
                final int startY = axis == EnumFacing.Axis.Y ? Math.max(pos1.getY(), range.getLayerMin()) : pos1.getY();
                final int startZ = axis == EnumFacing.Axis.Z ? Math.max(pos1.getZ(), range.getLayerMin()) : pos1.getZ();
                final int endX = axis == EnumFacing.Axis.X ? Math.min(pos2.getX(), range.getLayerMax()) : pos2.getX();
                final int endY = axis == EnumFacing.Axis.Y ? Math.min(pos2.getY(), range.getLayerMax()) : pos2.getY();
                final int endZ = axis == EnumFacing.Axis.Z ? Math.min(pos2.getZ(), range.getLayerMax()) : pos2.getZ();

                for (int y = startY; y <= endY; ++y)
                {
                    for (int z = startZ; z <= endZ; ++z)
                    {
                        for (int x = startX; x <= endX; ++x)
                        {
                            posMutable.setPos(x, y, z);
                            IBlockState stateSchematic = worldSchematic.getBlockState(posMutable).getActualState(worldSchematic, posMutable);

                            if (stateSchematic.getBlock() != Blocks.AIR)
                            {
                                IBlockState stateClient = worldClient.getBlockState(posMutable).getActualState(worldClient, posMutable);
                                countsTotal.addTo(stateSchematic, 1);

                                if (stateClient.getBlock() == Blocks.AIR)
                                {
                                    countsMissing.addTo(stateSchematic, 1);
                                }
                                else if (stateClient != stateSchematic)
                                {
                                    countsMissing.addTo(stateSchematic, 1);
                                    countsMismatch.addTo(stateSchematic, 1);
                                }
                            }
                        }
                    }
                }
            }
        }

        return getMaterialList(countsTotal, countsMissing, countsMismatch, mc.player);
    }

    private static List<MaterialListEntry> getMaterialList(
            Object2IntOpenHashMap<IBlockState> countsTotal,
            Object2IntOpenHashMap<IBlockState> countsMissing,
            Object2IntOpenHashMap<IBlockState> countsMismatch,
            EntityPlayer player)
    {
        List<MaterialListEntry> list = new ArrayList<>();

        if (countsTotal.isEmpty() == false)
        {
            MaterialCache cache = MaterialCache.getInstance();
            Object2IntOpenHashMap<ItemType> itemTypesTotal = new Object2IntOpenHashMap<>();
            Object2IntOpenHashMap<ItemType> itemTypesMissing = new Object2IntOpenHashMap<>();
            Object2IntOpenHashMap<ItemType> itemTypesMismatch = new Object2IntOpenHashMap<>();

            convertStatesToStacks(countsTotal, itemTypesTotal, cache);
            convertStatesToStacks(countsMissing, itemTypesMissing, cache);
            convertStatesToStacks(countsMismatch, itemTypesMismatch, cache);

            Object2IntOpenHashMap<ItemType> playerInvItems = InventoryUtils.getInventoryItemCounts(player.inventory);

            for (ItemType type : itemTypesTotal.keySet())
            {
                int countAvailable = playerInvItems.getInt(type);
                list.add(new MaterialListEntry(type.getStack().copy(),
                        itemTypesTotal.getInt(type),
                        itemTypesMissing.getInt(type),
                        itemTypesMismatch.getInt(type),
                        countAvailable));
            }
        }

        return list;
    }

    private static void convertStatesToStacks(
            Object2IntOpenHashMap<IBlockState> blockStatesIn,
            Object2IntOpenHashMap<ItemType> itemTypesOut,
            MaterialCache cache)
    {
        // Convert from counts per IBlockState to counts per different stacks
        for (IBlockState state : blockStatesIn.keySet())
        {
            if (cache.requiresMultipleItems(state))
            {
                for (ItemStack stack : cache.getItems(state))
                {
                    ItemType type = new ItemType(stack, false, true);
                    itemTypesOut.addTo(type, blockStatesIn.getInt(state) * stack.getCount());
                }
            }
            else
            {
                ItemStack stack = cache.getItemForState(state);

                if (stack.isEmpty() == false)
                {
                    ItemType type = new ItemType(stack, false, true);
                    itemTypesOut.addTo(type, blockStatesIn.getInt(state) * stack.getCount());
                }
            }
        }
    }

    public static void updateAvailableCounts(List<MaterialListEntry> list, EntityPlayer player)
    {
        Object2IntOpenHashMap<ItemType> playerInvItems = InventoryUtils.getInventoryItemCounts(player.inventory);

        for (MaterialListEntry entry : list)
        {
            ItemType type = new ItemType(entry.getStack(), false, true);
            int countAvailable = playerInvItems.getInt(type);
            entry.setCountAvailable(countAvailable);
        }
    }
}
