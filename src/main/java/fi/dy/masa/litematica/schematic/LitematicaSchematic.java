package fi.dy.masa.litematica.schematic;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import com.google.common.collect.ImmutableMap;
import fi.dy.masa.litematica.Litematica;
import fi.dy.masa.litematica.config.Configs;
import fi.dy.masa.litematica.mixin.IMixinNBTTagLongArray;
import fi.dy.masa.litematica.schematic.container.LitematicaBlockStateContainer;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SubRegionPlacement;
import fi.dy.masa.litematica.selection.AreaSelection;
import fi.dy.masa.litematica.selection.Box;
import fi.dy.masa.litematica.util.EntityUtils;
import fi.dy.masa.litematica.util.PositionUtils;
import fi.dy.masa.litematica.util.ReplaceBehavior;
import fi.dy.masa.litematica.util.WorldUtils;
import fi.dy.masa.malilib.gui.Message.MessageType;
import fi.dy.masa.malilib.interfaces.IStringConsumer;
import fi.dy.masa.malilib.util.Constants;
import fi.dy.masa.malilib.util.InfoUtils;
import fi.dy.masa.malilib.util.IntBoundingBox;
import fi.dy.masa.malilib.util.NBTUtils;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.fluid.Fluid;
import net.minecraft.init.Blocks;
import net.minecraft.init.Fluids;
import net.minecraft.inventory.IInventory;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.INBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLongArray;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Mirror;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.registry.IRegistry;
import net.minecraft.world.NextTickListEntry;
import net.minecraft.world.TickPriority;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

public class LitematicaSchematic
{
    public static final String FILE_EXTENSION = ".litematic";
    public static final int SCHEMATIC_VERSION = 5;
    public static final int MINECRAFT_DATA_VERSION = 1631; // MC 1.13.2

    private final Map<String, LitematicaBlockStateContainer> blockContainers = new HashMap<>();
    private final Map<String, Map<BlockPos, NBTTagCompound>> tileEntities = new HashMap<>();
    private final Map<String, Map<BlockPos, NextTickListEntry<Block>>> pendingBlockTicks = new HashMap<>();
    private final Map<String, Map<BlockPos, NextTickListEntry<Fluid>>> pendingFluidTicks = new HashMap<>();
    private final Map<String, List<EntityInfo>> entities = new HashMap<>();
    private final Map<String, BlockPos> subRegionPositions = new HashMap<>();
    private final Map<String, BlockPos> subRegionSizes = new HashMap<>();
    private final SchematicMetadata metadata = new SchematicMetadata();
    private int totalBlocks;
    @Nullable
    private final File schematicFile;

    private LitematicaSchematic(@Nullable File file)
    {
        this.schematicFile = file;
    }

    @Nullable
    public File getFile()
    {
        return this.schematicFile;
    }

    public Vec3i getTotalSize()
    {
        return this.metadata.getEnclosingSize();
    }

    public int getTotalBlocks()
    {
        return this.totalBlocks;
    }

    public SchematicMetadata getMetadata()
    {
        return this.metadata;
    }

    public int getSubRegionCount()
    {
        return this.blockContainers.size();
    }

    @Nullable
    public BlockPos getSubRegionPosition(String areaName)
    {
        return this.subRegionPositions.get(areaName);
    }

    public Map<String, BlockPos> getAreaPositions()
    {
        ImmutableMap.Builder<String, BlockPos> builder = ImmutableMap.builder();

        for (String name : this.subRegionPositions.keySet())
        {
            BlockPos pos = this.subRegionPositions.get(name);
            builder.put(name, pos);
        }

        return builder.build();
    }

    public Map<String, BlockPos> getAreaSizes()
    {
        ImmutableMap.Builder<String, BlockPos> builder = ImmutableMap.builder();

        for (String name : this.subRegionSizes.keySet())
        {
            BlockPos pos = this.subRegionSizes.get(name);
            builder.put(name, pos);
        }

        return builder.build();
    }

    @Nullable
    public BlockPos getAreaSize(String regionName)
    {
        return this.subRegionSizes.get(regionName);
    }

    public Map<String, Box> getAreas()
    {
        ImmutableMap.Builder<String, Box> builder = ImmutableMap.builder();

        for (String name : this.subRegionPositions.keySet())
        {
            BlockPos pos = this.subRegionPositions.get(name);
            BlockPos posEndRel = PositionUtils.getRelativeEndPositionFromAreaSize(this.subRegionSizes.get(name));
            Box box = new Box(pos, pos.add(posEndRel), name);
            builder.put(name, box);
        }

        return builder.build();
    }

    @Nullable
    public static LitematicaSchematic createFromWorld(World world, AreaSelection area, boolean ignoreEntities, String author, IStringConsumer feedback)
    {
        List<Box> boxes = PositionUtils.getValidBoxes(area);

        if (boxes.isEmpty())
        {
            feedback.setString(StringUtils.translate("litematica.error.schematic.create.no_selections"));
            return null;
        }

        LitematicaSchematic schematic = new LitematicaSchematic(null);
        long time = (new Date()).getTime();

        BlockPos origin = area.getEffectiveOrigin();
        schematic.setSubRegionPositions(boxes, origin);
        schematic.setSubRegionSizes(boxes);

        schematic.takeBlocksFromWorld(world, boxes);

        if (ignoreEntities == false)
        {
            schematic.takeEntitiesFromWorld(world, boxes, origin);
        }

        schematic.metadata.setAuthor(author);
        schematic.metadata.setName(area.getName());
        schematic.metadata.setTimeCreated(time);
        schematic.metadata.setTimeModified(time);
        schematic.metadata.setRegionCount(boxes.size());
        schematic.metadata.setTotalVolume(PositionUtils.getTotalVolume(boxes));
        schematic.metadata.setEnclosingSize(PositionUtils.getEnclosingAreaSize(boxes));
        schematic.metadata.setTotalBlocks(schematic.totalBlocks);

        return schematic;
    }

    /**
     * Creates an empty schematic with all the maps and lists and containers already created.
     * This is intended to be used for the chunk-wise schematic creation.
     * @param area
     * @param author
     * @param feedback
     * @return
     */
    public static LitematicaSchematic createEmptySchematic(AreaSelection area, String author)
    {
        List<Box> boxes = PositionUtils.getValidBoxes(area);

        if (boxes.isEmpty())
        {
            InfoUtils.showGuiOrInGameMessage(MessageType.ERROR, StringUtils.translate("litematica.error.schematic.create.no_selections"));
            return null;
        }

        LitematicaSchematic schematic = new LitematicaSchematic(null);
        schematic.setSubRegionPositions(boxes, area.getEffectiveOrigin());
        schematic.setSubRegionSizes(boxes);
        schematic.metadata.setAuthor(author);
        schematic.metadata.setName(area.getName());
        schematic.metadata.setRegionCount(boxes.size());
        schematic.metadata.setTotalVolume(PositionUtils.getTotalVolume(boxes));
        schematic.metadata.setEnclosingSize(PositionUtils.getEnclosingAreaSize(boxes));

        for (Box box : boxes)
        {
            String regionName = box.getName();
            BlockPos size = box.getSize();
            final int sizeX = Math.abs(size.getX());
            final int sizeY = Math.abs(size.getY());
            final int sizeZ = Math.abs(size.getZ());
            LitematicaBlockStateContainer container = new LitematicaBlockStateContainer(sizeX, sizeY, sizeZ);
            schematic.blockContainers.put(regionName, container);
            schematic.tileEntities.put(regionName, new HashMap<>());
            schematic.entities.put(regionName, new ArrayList<>());
            schematic.pendingBlockTicks.put(regionName, new HashMap<>());
            schematic.pendingFluidTicks.put(regionName, new HashMap<>());
        }

        return schematic;
    }

    public void takeEntityDataFromSchematicaSchematic(SchematicaSchematic schematic, String subRegionName)
    {
        this.tileEntities.put(subRegionName, schematic.getTiles());
        this.entities.put(subRegionName, schematic.getEntities());
    }

    public boolean placeToWorld(World world, SchematicPlacement schematicPlacement, boolean notifyNeighbors)
    {
        WorldUtils.setShouldPreventOnBlockAdded(true);

        ImmutableMap<String, SubRegionPlacement> relativePlacements = schematicPlacement.getEnabledRelativeSubRegionPlacements();
        BlockPos origin = schematicPlacement.getOrigin();

        for (String regionName : relativePlacements.keySet())
        {
            SubRegionPlacement placement = relativePlacements.get(regionName);

            if (placement.isEnabled())
            {
                BlockPos regionPos = placement.getPos();
                BlockPos regionSize = this.subRegionSizes.get(regionName);
                LitematicaBlockStateContainer container = this.blockContainers.get(regionName);
                Map<BlockPos, NBTTagCompound> tileMap = this.tileEntities.get(regionName);
                List<EntityInfo> entityList = this.entities.get(regionName);
                Map<BlockPos, NextTickListEntry<Block>> scheduledBlockTicks = this.pendingBlockTicks.get(regionName);
                Map<BlockPos, NextTickListEntry<Fluid>> scheduledFluidTicks = this.pendingFluidTicks.get(regionName);

                if (regionPos != null && regionSize != null && container != null && tileMap != null)
                {
                    this.placeBlocksToWorld(world, origin, regionPos, regionSize, schematicPlacement, placement, container, tileMap, scheduledBlockTicks, scheduledFluidTicks, notifyNeighbors);
                }
                else
                {
                    Litematica.logger.warn("Invalid/missing schematic data in schematic '{}' for sub-region '{}'", this.metadata.getName(), regionName);
                }

                if (schematicPlacement.ignoreEntities() == false && placement.ignoreEntities() == false && entityList != null)
                {
                    this.placeEntitiesToWorld(world, origin, regionPos, regionSize, schematicPlacement, placement, entityList);
                }
            }
        }

        WorldUtils.setShouldPreventOnBlockAdded(false);

        return true;
    }

    private boolean placeBlocksToWorld(World world, BlockPos origin, BlockPos regionPos, BlockPos regionSize,
            SchematicPlacement schematicPlacement, SubRegionPlacement placement,
            LitematicaBlockStateContainer container, Map<BlockPos, NBTTagCompound> tileMap,
            @Nullable Map<BlockPos, NextTickListEntry<Block>> scheduledBlockTicks,
            @Nullable Map<BlockPos, NextTickListEntry<Fluid>> scheduledFluidTicks, boolean notifyNeighbors)
    {
        // These are the untransformed relative positions
        BlockPos posEndRelSub = PositionUtils.getRelativeEndPositionFromAreaSize(regionSize);
        BlockPos posEndRel = posEndRelSub.add(regionPos);
        BlockPos posMinRel = PositionUtils.getMinCorner(regionPos, posEndRel);

        BlockPos regionPosTransformed = PositionUtils.getTransformedBlockPos(regionPos, schematicPlacement.getMirror(), schematicPlacement.getRotation());
        BlockPos posEndAbs = PositionUtils.getTransformedBlockPos(posEndRelSub, placement.getMirror(), placement.getRotation()).add(regionPosTransformed).add(origin);
        BlockPos regionPosAbs = regionPosTransformed.add(origin);

        if (PositionUtils.arePositionsWithinWorld(world, regionPosAbs, posEndAbs) == false)
        {
            return false;
        }

        final int sizeX = Math.abs(regionSize.getX());
        final int sizeY = Math.abs(regionSize.getY());
        final int sizeZ = Math.abs(regionSize.getZ());
        final IBlockState barrier = Blocks.BARRIER.getDefaultState();
        BlockPos.MutableBlockPos posMutable = new BlockPos.MutableBlockPos();
        ReplaceBehavior replace = (ReplaceBehavior) Configs.Generic.PASTE_REPLACE_BEHAVIOR.getOptionListValue();

        final Rotation rotationCombined = schematicPlacement.getRotation().add(placement.getRotation());
        final Mirror mirrorMain = schematicPlacement.getMirror();
        Mirror mirrorSub = placement.getMirror();

        if (mirrorSub != Mirror.NONE &&
            (schematicPlacement.getRotation() == Rotation.CLOCKWISE_90 ||
             schematicPlacement.getRotation() == Rotation.COUNTERCLOCKWISE_90))
        {
            mirrorSub = mirrorSub == Mirror.FRONT_BACK ? Mirror.LEFT_RIGHT : Mirror.FRONT_BACK;
        }

        for (int y = 0; y < sizeY; ++y)
        {
            for (int z = 0; z < sizeZ; ++z)
            {
                for (int x = 0; x < sizeX; ++x)
                {
                    IBlockState state = container.get(x, y, z);

                    if (state.getBlock() == Blocks.STRUCTURE_VOID)
                    {
                        continue;
                    }

                    posMutable.setPos(x, y, z);
                    NBTTagCompound teNBT = tileMap.get(posMutable);

                    posMutable.setPos(  posMinRel.getX() + x - regionPos.getX(),
                                        posMinRel.getY() + y - regionPos.getY(),
                                        posMinRel.getZ() + z - regionPos.getZ());

                    BlockPos pos = PositionUtils.getTransformedPlacementPosition(posMutable, schematicPlacement, placement);
                    pos = pos.add(regionPosTransformed).add(origin);

                    IBlockState stateOld = world.getBlockState(pos);

                    if ((replace == ReplaceBehavior.NONE && stateOld.isAir() == false) ||
                        (replace == ReplaceBehavior.WITH_NON_AIR && state.isAir()))
                    {
                        continue;
                    }

                    if (mirrorMain != Mirror.NONE) { state = state.mirror(mirrorMain); }
                    if (mirrorSub != Mirror.NONE)  { state = state.mirror(mirrorSub); }
                    if (rotationCombined != Rotation.NONE) { state = state.rotate(rotationCombined); }

                    if (stateOld == state)
                    {
                        continue;
                    }

                    TileEntity teOld = world.getTileEntity(pos);

                    if (teOld != null)
                    {
                        if (teOld instanceof IInventory)
                        {
                            ((IInventory) teOld).clear();
                        }

                        world.setBlockState(pos, barrier, 0x14);
                    }

                    if (world.setBlockState(pos, state, 0x12) && teNBT != null)
                    {
                        TileEntity te = world.getTileEntity(pos);

                        if (te != null)
                        {
                            teNBT = teNBT.copy();
                            teNBT.putInt("x", pos.getX());
                            teNBT.putInt("y", pos.getY());
                            teNBT.putInt("z", pos.getZ());

                            try
                            {
                                te.read(teNBT);

                                if (mirrorMain != Mirror.NONE) { te.mirror(mirrorMain); }
                                if (mirrorSub != Mirror.NONE)  { te.mirror(mirrorSub); }
                                if (rotationCombined != Rotation.NONE) { te.rotate(rotationCombined); }
                            }
                            catch (Exception e)
                            {
                                Litematica.logger.warn("Failed to load TileEntity data for {} @ {}", state, pos);
                            }
                        }
                    }
                }
            }
        }

        if (notifyNeighbors)
        {
            for (int y = 0; y < sizeY; ++y)
            {
                for (int z = 0; z < sizeZ; ++z)
                {
                    for (int x = 0; x < sizeX; ++x)
                    {
                        posMutable.setPos(  posMinRel.getX() + x - regionPos.getX(),
                                            posMinRel.getY() + y - regionPos.getY(),
                                            posMinRel.getZ() + z - regionPos.getZ());
                        BlockPos pos = PositionUtils.getTransformedPlacementPosition(posMutable, schematicPlacement, placement).add(origin);
                        world.notifyNeighbors(pos, world.getBlockState(pos).getBlock());
                    }
                }
            }
        }

        if (scheduledBlockTicks != null && scheduledBlockTicks.isEmpty() == false)
        {
            for (Map.Entry<BlockPos, NextTickListEntry<Block>> entry : scheduledBlockTicks.entrySet())
            {
                BlockPos pos = entry.getKey().add(regionPosAbs);
                NextTickListEntry<Block> tick = entry.getValue();
                world.getPendingBlockTicks().scheduleTick(pos, tick.getTarget(), (int) tick.scheduledTime, tick.priority);
            }
        }

        if (scheduledFluidTicks != null && scheduledFluidTicks.isEmpty() == false)
        {
            for (Map.Entry<BlockPos, NextTickListEntry<Fluid>> entry : scheduledFluidTicks.entrySet())
            {
                BlockPos pos = entry.getKey().add(regionPosAbs);
                IBlockState state = world.getBlockState(pos);

                if (state.getFluidState().isEmpty() == false)
                {
                    NextTickListEntry<Fluid> tick = entry.getValue();
                    world.getPendingFluidTicks().scheduleTick(pos, tick.getTarget(), (int) tick.scheduledTime, tick.priority);
                }
            }
        }

        return true;
    }

    private void placeEntitiesToWorld(World world, BlockPos origin, BlockPos regionPos, BlockPos regionSize, SchematicPlacement schematicPlacement, SubRegionPlacement placement, List<EntityInfo> entityList)
    {
        BlockPos regionPosRelTransformed = PositionUtils.getTransformedBlockPos(regionPos, schematicPlacement.getMirror(), schematicPlacement.getRotation());
        final int offX = regionPosRelTransformed.getX() + origin.getX();
        final int offY = regionPosRelTransformed.getY() + origin.getY();
        final int offZ = regionPosRelTransformed.getZ() + origin.getZ();

        final Rotation rotationCombined = schematicPlacement.getRotation().add(placement.getRotation());
        final Mirror mirrorMain = schematicPlacement.getMirror();
        Mirror mirrorSub = placement.getMirror();

        if (mirrorSub != Mirror.NONE &&
            (schematicPlacement.getRotation() == Rotation.CLOCKWISE_90 ||
             schematicPlacement.getRotation() == Rotation.COUNTERCLOCKWISE_90))
        {
            mirrorSub = mirrorSub == Mirror.FRONT_BACK ? Mirror.LEFT_RIGHT : Mirror.FRONT_BACK;
        }

        for (EntityInfo info : entityList)
        {
            Entity entity = EntityUtils.createEntityAndPassengersFromNBT(info.nbt, world);

            if (entity != null)
            {
                Vec3d pos = info.posVec;
                pos = PositionUtils.getTransformedPosition(pos, schematicPlacement.getMirror(), schematicPlacement.getRotation());
                pos = PositionUtils.getTransformedPosition(pos, placement.getMirror(), placement.getRotation());
                double x = pos.x + offX;
                double y = pos.y + offY;
                double z = pos.z + offZ;

                this.rotateEntity(entity, x, y, z, rotationCombined, mirrorMain, mirrorSub);
                EntityUtils.spawnEntityAndPassengersInWorld(entity, world);
            }
        }
    }

    public boolean placeToWorldWithinChunk(World world, ChunkPos chunkPos, SchematicPlacement schematicPlacement, boolean notifyNeighbors)
    {
        Set<String> regionsTouchingChunk = schematicPlacement.getRegionsTouchingChunk(chunkPos.x, chunkPos.z);
        BlockPos origin = schematicPlacement.getOrigin();

        for (String regionName : regionsTouchingChunk)
        {
            SubRegionPlacement placement = schematicPlacement.getRelativeSubRegionPlacement(regionName);

            if (placement.isEnabled())
            {
                BlockPos regionPos = placement.getPos();
                BlockPos regionSize = this.subRegionSizes.get(regionName);
                LitematicaBlockStateContainer container = this.blockContainers.get(regionName);
                Map<BlockPos, NBTTagCompound> tileMap = this.tileEntities.get(regionName);
                List<EntityInfo> entityList = this.entities.get(regionName);

                if (regionPos != null && regionSize != null && container != null && tileMap != null)
                {
                    this.placeBlocksWithinChunk(world, chunkPos, regionName, origin, regionPos, regionSize, schematicPlacement, placement, container, tileMap, notifyNeighbors);
                }
                else
                {
                    Litematica.logger.warn("Invalid/missing schematic data in schematic '{}' for sub-region '{}'", this.metadata.getName(), regionName);
                }

                if (schematicPlacement.ignoreEntities() == false && placement.ignoreEntities() == false && entityList != null)
                {
                    this.placeEntitiesToWorldWithinChunk(world, chunkPos, origin, regionPos, regionSize, schematicPlacement, placement, entityList);
                }
            }
        }

        return true;
    }

    private void placeBlocksWithinChunk(World world, ChunkPos chunkPos, String regionName,
            BlockPos origin, BlockPos regionPos, BlockPos regionSize,
            SchematicPlacement schematicPlacement, SubRegionPlacement placement,
            LitematicaBlockStateContainer container, Map<BlockPos, NBTTagCompound> tileMap, boolean notifyNeighbors)
    {
        IntBoundingBox bounds = schematicPlacement.getBoxWithinChunkForRegion(regionName, chunkPos.x, chunkPos.z);

        if (bounds == null)
        {
            return;
        }

        // These are the untransformed relative positions
        BlockPos posEndRel = PositionUtils.getRelativeEndPositionFromAreaSize(regionSize).add(regionPos);
        BlockPos posMinRel = PositionUtils.getMinCorner(regionPos, posEndRel);

        // The transformed sub-region origin position
        BlockPos regionPosTransformed = PositionUtils.getTransformedBlockPos(regionPos, schematicPlacement.getMirror(), schematicPlacement.getRotation());

        // The relative offset of the affected region's corners, to the sub-region's origin corner
        BlockPos boxMinRel = new BlockPos(bounds.minX - origin.getX() - regionPosTransformed.getX(), 0, bounds.minZ - origin.getZ() - regionPosTransformed.getZ());
        BlockPos boxMaxRel = new BlockPos(bounds.maxX - origin.getX() - regionPosTransformed.getX(), 0, bounds.maxZ - origin.getZ() - regionPosTransformed.getZ());

        // Reverse transform that relative offset, to get the untransformed orientation's offsets
        boxMinRel = PositionUtils.getReverseTransformedBlockPos(boxMinRel, placement.getMirror(), placement.getRotation());
        boxMaxRel = PositionUtils.getReverseTransformedBlockPos(boxMaxRel, placement.getMirror(), placement.getRotation());

        boxMinRel = PositionUtils.getReverseTransformedBlockPos(boxMinRel, schematicPlacement.getMirror(), schematicPlacement.getRotation());
        boxMaxRel = PositionUtils.getReverseTransformedBlockPos(boxMaxRel, schematicPlacement.getMirror(), schematicPlacement.getRotation());

        // Get the offset relative to the sub-region's minimum corner, instead of the origin corner (which can be at any corner)
        boxMinRel = boxMinRel.subtract(posMinRel.subtract(regionPos));
        boxMaxRel = boxMaxRel.subtract(posMinRel.subtract(regionPos));

        BlockPos posMin = PositionUtils.getMinCorner(boxMinRel, boxMaxRel);
        BlockPos posMax = PositionUtils.getMaxCorner(boxMinRel, boxMaxRel);

        final int startX = posMin.getX();
        final int startZ = posMin.getZ();
        final int endX = posMax.getX();
        final int endZ = posMax.getZ();

        final int startY = 0;
        final int endY = Math.abs(regionSize.getY()) - 1;
        BlockPos.MutableBlockPos posMutable = new BlockPos.MutableBlockPos();

        //System.out.printf("sx: %d, sy: %d, sz: %d => ex: %d, ey: %d, ez: %d\n", startX, startY, startZ, endX, endY, endZ);

        if (startX < 0 || startZ < 0 || endX >= container.getSize().getX() || endZ >= container.getSize().getZ())
        {
            System.out.printf("DEBUG ============= OUT OF BOUNDS - region: %s, sx: %d, sz: %d, ex: %d, ez: %d - size x: %d z: %d =============\n",
                    regionName, startX, startZ, endX, endZ, container.getSize().getX(), container.getSize().getZ());
            return;
        }

        final Rotation rotationCombined = schematicPlacement.getRotation().add(placement.getRotation());
        final Mirror mirrorMain = schematicPlacement.getMirror();
        final IBlockState barrier = Blocks.BARRIER.getDefaultState();
        Mirror mirrorSub = placement.getMirror();

        if (mirrorSub != Mirror.NONE &&
            (schematicPlacement.getRotation() == Rotation.CLOCKWISE_90 ||
             schematicPlacement.getRotation() == Rotation.COUNTERCLOCKWISE_90))
        {
            mirrorSub = mirrorSub == Mirror.FRONT_BACK ? Mirror.LEFT_RIGHT : Mirror.FRONT_BACK;
        }

        for (int y = startY; y <= endY; ++y)
        {
            for (int z = startZ; z <= endZ; ++z)
            {
                for (int x = startX; x <= endX; ++x)
                {
                    IBlockState state = container.get(x, y, z);

                    if (state.isAir())
                    {
                        continue;
                    }

                    posMutable.setPos(x, y, z);
                    NBTTagCompound teNBT = tileMap.get(posMutable);

                    posMutable.setPos(  posMinRel.getX() + x - regionPos.getX(),
                                        posMinRel.getY() + y - regionPos.getY(),
                                        posMinRel.getZ() + z - regionPos.getZ());

                    BlockPos pos = PositionUtils.getTransformedPlacementPosition(posMutable, schematicPlacement, placement);
                    pos = pos.add(regionPosTransformed).add(origin);

                    if (mirrorMain != Mirror.NONE) { state = state.mirror(mirrorMain); }
                    if (mirrorSub != Mirror.NONE)  { state = state.mirror(mirrorSub); }
                    if (rotationCombined != Rotation.NONE) { state = state.rotate(rotationCombined); }

                    if (teNBT != null)
                    {
                        TileEntity te = world.getTileEntity(pos);

                        if (te != null)
                        {
                            if (te instanceof IInventory)
                            {
                                ((IInventory) te).clear();
                            }

                            world.setBlockState(pos, barrier, 0x14);
                        }
                    }

                    if (world.setBlockState(pos, state, 0x12) && teNBT != null)
                    {
                        TileEntity te = world.getTileEntity(pos);

                        if (te != null)
                        {
                            teNBT = teNBT.copy();
                            teNBT.putInt("x", pos.getX());
                            teNBT.putInt("y", pos.getY());
                            teNBT.putInt("z", pos.getZ());

                            try
                            {
                                te.read(teNBT);

                                if (mirrorMain != Mirror.NONE) { te.mirror(mirrorMain); }
                                if (mirrorSub != Mirror.NONE)  { te.mirror(mirrorSub); }
                                if (rotationCombined != Rotation.NONE) { te.rotate(rotationCombined); }
                            }
                            catch (Exception e)
                            {
                                Litematica.logger.warn("Failed to load TileEntity data for {} @ {}", state, pos);
                            }
                        }
                    }
                }
            }
        }

        if (notifyNeighbors)
        {
            for (int y = startX; y <= endY; ++y)
            {
                for (int z = startY; z <= endZ; ++z)
                {
                    for (int x = startZ; x <= endX; ++x)
                    {
                        posMutable.setPos(  posMinRel.getX() + x - regionPos.getX(),
                                            posMinRel.getY() + y - regionPos.getY(),
                                            posMinRel.getZ() + z - regionPos.getZ());
                        BlockPos pos = PositionUtils.getTransformedPlacementPosition(posMutable, schematicPlacement, placement).add(origin);
                        world.notifyNeighbors(pos, world.getBlockState(pos).getBlock());
                    }
                }
            }
        }
    }

    private void placeEntitiesToWorldWithinChunk(World world, ChunkPos chunkPos, BlockPos origin, BlockPos regionPos, BlockPos regionSize,
            SchematicPlacement schematicPlacement, SubRegionPlacement placement, List<EntityInfo> entityList)
    {
        BlockPos regionPosRelTransformed = PositionUtils.getTransformedBlockPos(regionPos, schematicPlacement.getMirror(), schematicPlacement.getRotation());
        final int offX = regionPosRelTransformed.getX() + origin.getX();
        final int offY = regionPosRelTransformed.getY() + origin.getY();
        final int offZ = regionPosRelTransformed.getZ() + origin.getZ();
        final double minX = (chunkPos.x << 4);
        final double minZ = (chunkPos.z << 4);
        final double maxX = (chunkPos.x << 4) + 16;
        final double maxZ = (chunkPos.z << 4) + 16;

        final Rotation rotationCombined = schematicPlacement.getRotation().add(placement.getRotation());
        final Mirror mirrorMain = schematicPlacement.getMirror();
        Mirror mirrorSub = placement.getMirror();

        if (mirrorSub != Mirror.NONE &&
            (schematicPlacement.getRotation() == Rotation.CLOCKWISE_90 ||
             schematicPlacement.getRotation() == Rotation.COUNTERCLOCKWISE_90))
        {
            mirrorSub = mirrorSub == Mirror.FRONT_BACK ? Mirror.LEFT_RIGHT : Mirror.FRONT_BACK;
        }

        for (EntityInfo info : entityList)
        {
            Entity entity = EntityUtils.createEntityAndPassengersFromNBT(info.nbt, world);

            if (entity != null)
            {
                Vec3d pos = info.posVec;
                pos = PositionUtils.getTransformedPosition(pos, schematicPlacement.getMirror(), schematicPlacement.getRotation());
                pos = PositionUtils.getTransformedPosition(pos, placement.getMirror(), placement.getRotation());
                double x = pos.x + offX;
                double y = pos.y + offY;
                double z = pos.z + offZ;

                if (x >= minX && x < maxX && z >= minZ && z < maxZ)
                {
                    this.rotateEntity(entity, x, y, z, rotationCombined, mirrorMain, mirrorSub);
                    //System.out.printf("post: %.1f - rot: %s, mm: %s, ms: %s\n", rotationYaw, rotationCombined, mirrorMain, mirrorSub);
                    EntityUtils.spawnEntityAndPassengersInWorld(entity, world);
                }
            }
        }
    }

    private void rotateEntity(Entity entity, double x, double y, double z, Rotation rotationCombined, Mirror mirrorMain, Mirror mirrorSub)
    {
        float rotationYaw = entity.rotationYaw;

        if (mirrorMain != Mirror.NONE)          { rotationYaw = entity.getMirroredYaw(mirrorMain); }
        if (mirrorSub != Mirror.NONE)           { rotationYaw = entity.getMirroredYaw(mirrorSub); }
        if (rotationCombined != Rotation.NONE)  { rotationYaw += entity.rotationYaw - entity.getRotatedYaw(rotationCombined); }

        entity.setLocationAndAngles(x, y, z, rotationYaw, entity.rotationPitch);

        entity.prevRotationYaw = rotationYaw;
        entity.prevRotationPitch = entity.rotationPitch;

        if (entity instanceof EntityLivingBase)
        {
            EntityLivingBase livingBase = (EntityLivingBase) entity;
            livingBase.rotationYawHead = rotationYaw;
            livingBase.prevRotationYawHead = rotationYaw;
            livingBase.renderYawOffset = rotationYaw;
            livingBase.prevRenderYawOffset = rotationYaw;
        }
    }

    private void takeEntitiesFromWorld(World world, List<Box> boxes, BlockPos origin)
    {
        for (Box box : boxes)
        {
            AxisAlignedBB bb = PositionUtils.createEnclosingAABB(box.getPos1(), box.getPos2());
            BlockPos regionPosAbs = box.getPos1();
            List<EntityInfo> list = new ArrayList<>();
            List<Entity> entities = world.getEntitiesInAABBexcluding(null, bb, null);

            for (Entity entity : entities)
            {
                NBTTagCompound tag = new NBTTagCompound();

                if (entity.writeUnlessPassenger(tag))
                {
                    Vec3d posVec = new Vec3d(entity.posX - regionPosAbs.getX(), entity.posY - regionPosAbs.getY(), entity.posZ - regionPosAbs.getZ());
                    NBTUtils.writeEntityPositionToTag(posVec, tag);
                    list.add(new EntityInfo(posVec, tag));
                }
            }

            this.entities.put(box.getName(), list);
        }
    }

    public void takeEntitiesFromWorldWithinChunk(World world, int chunkX, int chunkZ,
            ImmutableMap<String, IntBoundingBox> volumes, ImmutableMap<String, Box> boxes,
            Set<UUID> existingEntities, BlockPos origin)
    {
        for (Map.Entry<String, IntBoundingBox> entry : volumes.entrySet())
        {
            String regionName = entry.getKey();
            List<EntityInfo> list = this.entities.get(regionName);
            Box box = boxes.get(regionName);

            if (box == null || list == null)
            {
                continue;
            }

            AxisAlignedBB bb = PositionUtils.createAABBFrom(entry.getValue());
            List<Entity> entities = world.getEntitiesInAABBexcluding(null, bb, null);
            BlockPos regionPosAbs = box.getPos1();

            for (Entity entity : entities)
            {
                UUID uuid = entity.getUniqueID();
                /*
                if (entity.posX >= bb.minX && entity.posX < bb.maxX &&
                    entity.posY >= bb.minY && entity.posY < bb.maxY &&
                    entity.posZ >= bb.minZ && entity.posZ < bb.maxZ)
                */
                if (existingEntities.contains(uuid) == false)
                {
                    NBTTagCompound tag = new NBTTagCompound();

                    if (entity.writeUnlessPassenger(tag))
                    {
                        Vec3d posVec = new Vec3d(entity.posX - regionPosAbs.getX(), entity.posY - regionPosAbs.getY(), entity.posZ - regionPosAbs.getZ());
                        NBTUtils.writeEntityPositionToTag(posVec, tag);
                        list.add(new EntityInfo(posVec, tag));
                        existingEntities.add(uuid);
                    }
                }
            }
        }
    }

    private void takeBlocksFromWorld(World world, List<Box> boxes)
    {
        BlockPos.MutableBlockPos posMutable = new BlockPos.MutableBlockPos(0, 0, 0);

        for (Box box : boxes)
        {
            BlockPos size = box.getSize();
            final int sizeX = Math.abs(size.getX());
            final int sizeY = Math.abs(size.getY());
            final int sizeZ = Math.abs(size.getZ());
            LitematicaBlockStateContainer container = new LitematicaBlockStateContainer(sizeX, sizeY, sizeZ);
            Map<BlockPos, NBTTagCompound> tileEntityMap = new HashMap<>();
            Map<BlockPos, NextTickListEntry<Block>> blockTickMap = new HashMap<>();
            Map<BlockPos, NextTickListEntry<Fluid>> fluidTickMap = new HashMap<>();

            // We want to loop nice & easy from 0 to n here, but the per-sub-region pos1 can be at
            // any corner of the area. Thus we need to offset from the total area origin
            // to the minimum/negative corner (ie. 0,0 in the loop) corner here.
            final BlockPos minCorner = PositionUtils.getMinCorner(box.getPos1(), box.getPos2());
            final int startX = minCorner.getX();
            final int startY = minCorner.getY();
            final int startZ = minCorner.getZ();

            for (int y = 0; y < sizeY; ++y)
            {
                for (int z = 0; z < sizeZ; ++z)
                {
                    for (int x = 0; x < sizeX; ++x)
                    {
                        posMutable.setPos(x + startX, y + startY, z + startZ);
                        IBlockState state = world.getBlockState(posMutable);
                        container.set(x, y, z, state);

                        if (state.isAir() == false)
                        {
                            this.totalBlocks++;
                        }

                        if (state.getBlock().hasTileEntity())
                        {
                            TileEntity te = world.getTileEntity(posMutable);

                            if (te != null)
                            {
                                // TODO Add a TileEntity NBT cache from the Chunk packets, to get the original synced data (too)
                                BlockPos pos = new BlockPos(x, y, z);
                                NBTTagCompound tag = te.write(new NBTTagCompound());
                                NBTUtils.writeBlockPosToTag(pos, tag);
                                tileEntityMap.put(pos, tag);
                            }
                        }
                    }
                }
            }

            if (world instanceof WorldServer)
            {
                IntBoundingBox tickBox = IntBoundingBox.createProper(
                        startX,         startY,         startZ,
                        startX + sizeX, startY + sizeY, startZ + sizeZ);
                List<NextTickListEntry<Block>> blockTicks = ((WorldServer) world).getPendingBlockTicks().getPending(tickBox.toVanillaBox(), false);

                if (blockTicks != null)
                {
                    this.getPendingTicksFromWorld(blockTickMap, blockTicks, minCorner, startY, tickBox.maxY, world.getGameTime());
                }

                List<NextTickListEntry<Fluid>> fluidTicks = ((WorldServer) world).getPendingFluidTicks().getPending(tickBox.toVanillaBox(), false);

                if (fluidTicks != null)
                {
                    this.getPendingTicksFromWorld(fluidTickMap, fluidTicks, minCorner, startY, tickBox.maxY, world.getGameTime());
                }
            }

            this.blockContainers.put(box.getName(), container);
            this.tileEntities.put(box.getName(), tileEntityMap);
            this.pendingBlockTicks.put(box.getName(), blockTickMap);
            this.pendingFluidTicks.put(box.getName(), fluidTickMap);
        }
    }

    private <T> void getPendingTicksFromWorld(Map<BlockPos, NextTickListEntry<T>> map, List<NextTickListEntry<T>> list,
            BlockPos minCorner, int startY, int maxY, final long currentTime)
    {
        final int listSize = list.size();

        for (int i = 0; i < listSize; ++i)
        {
            NextTickListEntry<T> entry = list.get(i);

            // The getPendingBlockUpdates() method doesn't check the y-coordinate... :-<
            if (entry.position.getY() >= startY && entry.position.getY() < maxY)
            {
                // Store the delay, ie. relative time
                BlockPos posRelative = new BlockPos(
                        entry.position.getX() - minCorner.getX(),
                        entry.position.getY() - minCorner.getY(),
                        entry.position.getZ() - minCorner.getZ());
                NextTickListEntry<T> newEntry = new NextTickListEntry<>(posRelative, entry.getTarget(), entry.scheduledTime - currentTime, entry.priority);

                map.put(posRelative, newEntry);
            }
        }
    }

    public void takeBlocksFromWorldWithinChunk(World world, int chunkX, int chunkZ,
            ImmutableMap<String, IntBoundingBox> volumes, ImmutableMap<String, Box> boxes)
    {
        BlockPos.MutableBlockPos posMutable = new BlockPos.MutableBlockPos(0, 0, 0);

        for (Map.Entry<String, IntBoundingBox> volumeEntry : volumes.entrySet())
        {
            String regionName = volumeEntry.getKey();
            IntBoundingBox bb = volumeEntry.getValue();
            Box box = boxes.get(regionName);

            if (box == null)
            {
                Litematica.logger.error("null Box for sub-region '{}' while trying to save chunk-wise schematic", regionName);
                continue;
            }

            LitematicaBlockStateContainer container = this.blockContainers.get(regionName);
            Map<BlockPos, NBTTagCompound> tileEntityMap = this.tileEntities.get(regionName);
            Map<BlockPos, NextTickListEntry<Block>> blockTickMap = this.pendingBlockTicks.get(regionName);
            Map<BlockPos, NextTickListEntry<Fluid>> fluidTickMap = this.pendingFluidTicks.get(regionName);

            if (container == null || tileEntityMap == null || blockTickMap == null || fluidTickMap == null)
            {
                Litematica.logger.error("null map(s) for sub-region '{}' while trying to save chunk-wise schematic", regionName);
                continue;
            }

            // We want to loop nice & easy from 0 to n here, but the per-sub-region pos1 can be at
            // any corner of the area. Thus we need to offset from the total area origin
            // to the minimum/negative corner (ie. 0,0 in the loop) corner here.
            final BlockPos minCorner = PositionUtils.getMinCorner(box.getPos1(), box.getPos2());
            final int offsetX = minCorner.getX();
            final int offsetY = minCorner.getY();
            final int offsetZ = minCorner.getZ();
            // Relative coordinates within the sub-region container:
            final int startX = bb.minX - minCorner.getX();
            final int startY = bb.minY - minCorner.getY();
            final int startZ = bb.minZ - minCorner.getZ();
            final int endX = startX + (bb.maxX - bb.minX);
            final int endY = startY + (bb.maxY - bb.minY);
            final int endZ = startZ + (bb.maxZ - bb.minZ);

            for (int y = startY; y <= endY; ++y)
            {
                for (int z = startZ; z <= endZ; ++z)
                {
                    for (int x = startX; x <= endX; ++x)
                    {
                        posMutable.setPos(x + offsetX, y + offsetY, z + offsetZ);
                        IBlockState state = world.getBlockState(posMutable);
                        container.set(x, y, z, state);

                        if (state.isAir() == false)
                        {
                            this.totalBlocks++;
                        }

                        if (state.getBlock().hasTileEntity())
                        {
                            TileEntity te = world.getTileEntity(posMutable);

                            if (te != null)
                            {
                                // TODO Add a TileEntity NBT cache from the Chunk packets, to get the original synced data (too)
                                BlockPos pos = new BlockPos(x, y, z);
                                NBTTagCompound tag = te.write(new NBTTagCompound());
                                NBTUtils.writeBlockPosToTag(pos, tag);
                                tileEntityMap.put(pos, tag);
                            }
                        }
                    }
                }
            }

            if (world instanceof WorldServer)
            {
                IntBoundingBox tickBox = IntBoundingBox.createProper(
                        offsetX + startX  , offsetY + startY  , offsetZ + startZ  ,
                        offsetX + endX + 1, offsetY + endY + 1, offsetZ + endZ + 1);
                List<NextTickListEntry<Block>> blockTicks = ((WorldServer) world).getPendingBlockTicks().getPending(tickBox.toVanillaBox(), false);

                if (blockTicks != null)
                {
                    this.getPendingTicksFromWorld(blockTickMap, blockTicks, minCorner, startY, tickBox.maxY, world.getGameTime());
                }

                List<NextTickListEntry<Fluid>> fluidTicks = ((WorldServer) world).getPendingFluidTicks().getPending(tickBox.toVanillaBox(), false);

                if (fluidTicks != null)
                {
                    this.getPendingTicksFromWorld(fluidTickMap, fluidTicks, minCorner, startY, tickBox.maxY, world.getGameTime());
                }
            }
        }
    }

    private void setSubRegionPositions(List<Box> boxes, BlockPos areaOrigin)
    {
        for (Box box : boxes)
        {
            this.subRegionPositions.put(box.getName(), box.getPos1().subtract(areaOrigin));
        }
    }

    private void setSubRegionSizes(List<Box> boxes)
    {
        for (Box box : boxes)
        {
            this.subRegionSizes.put(box.getName(), box.getSize());
        }
    }

    @Nullable
    public LitematicaBlockStateContainer getSubRegionContainer(String regionName)
    {
        return this.blockContainers.get(regionName);
    }

    private NBTTagCompound writeToNBT()
    {
        NBTTagCompound nbt = new NBTTagCompound();

        nbt.putInt("Version", SCHEMATIC_VERSION);
        nbt.putInt("MinecraftDataVersion", MINECRAFT_DATA_VERSION);
        nbt.put("Metadata", this.metadata.writeToNBT());
        nbt.put("Regions", this.writeSubRegionsToNBT());

        return nbt;
    }

    private NBTTagCompound writeSubRegionsToNBT()
    {
        NBTTagCompound wrapper = new NBTTagCompound();

        if (this.blockContainers.isEmpty() == false)
        {
            for (String regionName : this.blockContainers.keySet())
            {
                LitematicaBlockStateContainer blockContainer = this.blockContainers.get(regionName);
                Map<BlockPos, NBTTagCompound> tileMap = this.tileEntities.get(regionName);
                List<EntityInfo> entityList = this.entities.get(regionName);
                Map<BlockPos, NextTickListEntry<Block>> pendingBlockTicks = this.pendingBlockTicks.get(regionName);
                Map<BlockPos, NextTickListEntry<Fluid>> pendingFluidTicks = this.pendingFluidTicks.get(regionName);

                NBTTagCompound tag = new NBTTagCompound();

                tag.put("BlockStatePalette", blockContainer.getPalette().writeToNBT());
                tag.put("BlockStates", new NBTTagLongArray(blockContainer.getBackingLongArray()));
                tag.put("TileEntities", this.writeTileEntitiesToNBT(tileMap));

                if (pendingBlockTicks != null)
                {
                    tag.put("PendingBlockTicks", this.writePendingTicksToNBT(pendingBlockTicks));
                }

                if (pendingFluidTicks != null)
                {
                    tag.put("PendingFluidTicks", this.writePendingTicksToNBT(pendingFluidTicks));
                }

                // The entity list will not exist, if takeEntities is false when creating the schematic
                if (entityList != null)
                {
                    tag.put("Entities", this.writeEntitiesToNBT(entityList));
                }

                BlockPos pos = this.subRegionPositions.get(regionName);
                tag.put("Position", NBTUtils.createBlockPosTag(pos));

                pos = this.subRegionSizes.get(regionName);
                tag.put("Size", NBTUtils.createBlockPosTag(pos));

                wrapper.put(regionName, tag);
            }
        }

        return wrapper;
    }

    private NBTTagList writeEntitiesToNBT(List<EntityInfo> entityList)
    {
        NBTTagList tagList = new NBTTagList();

        if (entityList.isEmpty() == false)
        {
            for (EntityInfo info : entityList)
            {
                tagList.add(info.nbt);
            }
        }

        return tagList;
    }

    private <T> NBTTagList writePendingTicksToNBT(Map<BlockPos, NextTickListEntry<T>> tickMap)
    {
        NBTTagList tagList = new NBTTagList();

        if (tickMap.isEmpty() == false)
        {
            for (NextTickListEntry<T> entry : tickMap.values())
            {
                T target = entry.getTarget();
                String tagName;
                ResourceLocation rl;

                if (target instanceof Block)
                {
                    rl = IRegistry.BLOCK.getKey((Block) target);
                    tagName = "Block";
                }
                else
                {
                    rl = IRegistry.FLUID.getKey((Fluid) target);
                    tagName = "Fluid";
                }

                if (rl != null)
                {
                    NBTTagCompound tag = new NBTTagCompound();

                    tag.putString(tagName, rl.toString());
                    tag.putInt("Priority", entry.priority.getPriority());
                    tag.putInt("Time", (int) entry.scheduledTime);
                    tag.putInt("x", entry.position.getX());
                    tag.putInt("y", entry.position.getY());
                    tag.putInt("z", entry.position.getZ());

                    tagList.add(tag);
                }
            }
        }

        return tagList;
    }

    private NBTTagList writeTileEntitiesToNBT(Map<BlockPos, NBTTagCompound> tileMap)
    {
        NBTTagList tagList = new NBTTagList();

        if (tileMap.isEmpty() == false)
        {
            for (NBTTagCompound tag : tileMap.values())
            {
                tagList.add(tag);
            }
        }

        return tagList;
    }

    private boolean readFromNBT(NBTTagCompound nbt)
    {
        this.blockContainers.clear();
        this.tileEntities.clear();
        this.entities.clear();
        this.subRegionPositions.clear();
        this.subRegionSizes.clear();

        if (nbt.contains("Version", Constants.NBT.TAG_INT))
        {
            final int version = nbt.getInt("Version");

            if (version >= 1 && version <= SCHEMATIC_VERSION)
            {
                this.metadata.readFromNBT(nbt.getCompound("Metadata"));
                this.readSubRegionsFromNBT(nbt.getCompound("Regions"), version);

                return true;
            }
            else
            {
                InfoUtils.showGuiOrInGameMessage(MessageType.ERROR, "litematica.error.schematic_load.unsupported_schematic_version", version);
            }
        }
        else
        {
            InfoUtils.showGuiOrInGameMessage(MessageType.ERROR, "litematica.error.schematic_load.no_schematic_version_information");
        }

        return false;
    }

    private void readSubRegionsFromNBT(NBTTagCompound tag, int version)
    {
        for (String regionName : tag.keySet())
        {
            if (tag.get(regionName).getId() == Constants.NBT.TAG_COMPOUND)
            {
                NBTTagCompound regionTag = tag.getCompound(regionName);
                BlockPos regionPos = NBTUtils.readBlockPos(regionTag.getCompound("Position"));
                BlockPos regionSize = NBTUtils.readBlockPos(regionTag.getCompound("Size"));

                if (regionPos != null && regionSize != null)
                {
                    this.subRegionPositions.put(regionName, regionPos);
                    this.subRegionSizes.put(regionName, regionSize);

                    if (version >= 2)
                    {
                        this.tileEntities.put(regionName, this.readTileEntitiesFromNBT(regionTag.getList("TileEntities", Constants.NBT.TAG_COMPOUND)));
                        this.entities.put(regionName, this.readEntitiesFromNBT(regionTag.getList("Entities", Constants.NBT.TAG_COMPOUND)));
                    }
                    else if (version == 1)
                    {
                        this.tileEntities.put(regionName, this.readTileEntitiesFromNBT_v1(regionTag.getList("TileEntities", Constants.NBT.TAG_COMPOUND)));
                        this.entities.put(regionName, this.readEntitiesFromNBT_v1(regionTag.getList("Entities", Constants.NBT.TAG_COMPOUND)));
                    }

                    if (version >= 3)
                    {
                        NBTTagList list = regionTag.getList("PendingBlockTicks", Constants.NBT.TAG_COMPOUND);
                        this.pendingBlockTicks.put(regionName, this.readPendingTicksFromNBT(list, Blocks.AIR));
                    }

                    if (version >= 5)
                    {
                        NBTTagList list = regionTag.getList("PendingFluidTicks", Constants.NBT.TAG_COMPOUND);
                        this.pendingFluidTicks.put(regionName, this.readPendingTicksFromNBT(list, Fluids.EMPTY));
                    }

                    INBTBase nbtBase = regionTag.get("BlockStates");

                    // There are no convenience methods in NBTTagCompound yet in 1.12, so we'll have to do it the ugly way...
                    if (nbtBase != null && nbtBase.getId() == Constants.NBT.TAG_LONG_ARRAY)
                    {
                        NBTTagList palette = regionTag.getList("BlockStatePalette", Constants.NBT.TAG_COMPOUND);
                        long[] blockStateArr = ((IMixinNBTTagLongArray) nbtBase).getArray();

                        BlockPos posEndRel = PositionUtils.getRelativeEndPositionFromAreaSize(regionSize).add(regionPos);
                        BlockPos posMin = PositionUtils.getMinCorner(regionPos, posEndRel);
                        BlockPos posMax = PositionUtils.getMaxCorner(regionPos, posEndRel);
                        BlockPos size = posMax.subtract(posMin).add(1, 1, 1);

                        LitematicaBlockStateContainer container = LitematicaBlockStateContainer.createFrom(palette, blockStateArr, size);
                        this.blockContainers.put(regionName, container);
                    }
                }
            }
        }
    }

    private List<EntityInfo> readEntitiesFromNBT(NBTTagList tagList)
    {
        List<EntityInfo> entityList = new ArrayList<>();
        final int size = tagList.size();

        for (int i = 0; i < size; ++i)
        {
            NBTTagCompound entityData = tagList.getCompound(i);
            Vec3d posVec = NBTUtils.readEntityPositionFromTag(entityData);

            if (posVec != null && entityData.isEmpty() == false)
            {
                entityList.add(new EntityInfo(posVec, entityData));
            }
        }

        return entityList;
    }

    private Map<BlockPos, NBTTagCompound> readTileEntitiesFromNBT(NBTTagList tagList)
    {
        Map<BlockPos, NBTTagCompound> tileMap = new HashMap<>();
        final int size = tagList.size();

        for (int i = 0; i < size; ++i)
        {
            NBTTagCompound tag = tagList.getCompound(i);
            BlockPos pos = NBTUtils.readBlockPos(tag);

            if (pos != null && tag.isEmpty() == false)
            {
                tileMap.put(pos, tag);
            }
        }

        return tileMap;
    }

    @SuppressWarnings("unchecked")
    private <T> Map<BlockPos, NextTickListEntry<T>> readPendingTicksFromNBT(NBTTagList tagList, T clazz)
    {
        Map<BlockPos, NextTickListEntry<T>> tickMap = new HashMap<>();
        final int size = tagList.size();

        for (int i = 0; i < size; ++i)
        {
            NBTTagCompound tag = tagList.getCompound(i);

            if (tag.contains("Time", Constants.NBT.TAG_ANY_NUMERIC)) // XXX these were accidentally saved as longs in version 3
            {
                T target = null;

                // Don't crash on invalid ResourceLocation in 1.13+
                try
                {
                    if (clazz instanceof Block && tag.contains("Block", Constants.NBT.TAG_STRING))
                    {
                        target = (T) IRegistry.BLOCK.get(new ResourceLocation(tag.getString("Block")));

                        if (target == null || target == Blocks.AIR)
                        {
                            continue;
                        }
                    }
                    else if (clazz instanceof Fluid && tag.contains("Fluid", Constants.NBT.TAG_STRING))
                    {
                        target = (T) IRegistry.FLUID.get(new ResourceLocation(tag.getString("Fluid")));

                        if (target == null || target == Fluids.EMPTY)
                        {
                            continue;
                        }
                    }
                }
                catch (Exception e)
                {
                }

                if (target != null)
                {
                    BlockPos pos = new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
                    // Note: the time is a relative delay at this point
                    int scheduledTime = tag.getInt("Time");
                    TickPriority priority = TickPriority.getPriority(tag.getInt("Priority"));

                    NextTickListEntry<T> entry = new NextTickListEntry<>(pos, target, scheduledTime, priority);

                    tickMap.put(pos, entry);
                }
            }
        }

        return tickMap;
    }

    private List<EntityInfo> readEntitiesFromNBT_v1(NBTTagList tagList)
    {
        List<EntityInfo> entityList = new ArrayList<>();
        final int size = tagList.size();

        for (int i = 0; i < size; ++i)
        {
            NBTTagCompound tag = tagList.getCompound(i);
            Vec3d posVec = NBTUtils.readVec3d(tag);
            NBTTagCompound entityData = tag.getCompound("EntityData");

            if (posVec != null && entityData.isEmpty() == false)
            {
                // Update the correct position to the TileEntity NBT, where it is stored in version 2
                NBTUtils.writeEntityPositionToTag(posVec, entityData);
                entityList.add(new EntityInfo(posVec, entityData));
            }
        }

        return entityList;
    }

    private Map<BlockPos, NBTTagCompound> readTileEntitiesFromNBT_v1(NBTTagList tagList)
    {
        Map<BlockPos, NBTTagCompound> tileMap = new HashMap<>();
        final int size = tagList.size();

        for (int i = 0; i < size; ++i)
        {
            NBTTagCompound tag = tagList.getCompound(i);
            NBTTagCompound tileNbt = tag.getCompound("TileNBT");

            // Note: This within-schematic relative position is not inside the tile tag!
            BlockPos pos = NBTUtils.readBlockPos(tag);

            if (pos != null && tileNbt.isEmpty() == false)
            {
                // Update the correct position to the entity NBT, where it is stored in version 2
                NBTUtils.writeBlockPosToTag(pos, tileNbt);
                tileMap.put(pos, tileNbt);
            }
        }

        return tileMap;
    }

    public boolean writeToFile(File dir, String fileNameIn, boolean override)
    {
        String fileName = fileNameIn;

        if (fileName.endsWith(FILE_EXTENSION) == false)
        {
            fileName = fileName + FILE_EXTENSION;
        }

        File fileSchematic = new File(dir, fileName);

        try
        {
            if (dir.exists() == false && dir.mkdirs() == false)
            {
                InfoUtils.showGuiOrInGameMessage(MessageType.ERROR, "litematica.error.schematic_write_to_file_failed.directory_creation_failed", dir.getAbsolutePath());
                return false;
            }

            if (override == false && fileSchematic.exists())
            {
                InfoUtils.showGuiOrInGameMessage(MessageType.ERROR, "litematica.error.schematic_write_to_file_failed.exists", fileSchematic.getAbsolutePath());
                return false;
            }

            FileOutputStream os = new FileOutputStream(fileSchematic);
            CompressedStreamTools.writeCompressed(this.writeToNBT(), os);
            os.close();

            return true;
        }
        catch (Exception e)
        {
            InfoUtils.showGuiOrInGameMessage(MessageType.ERROR, "litematica.error.schematic_write_to_file_failed.exception", fileSchematic.getAbsolutePath());
            InfoUtils.showGuiOrInGameMessage(MessageType.ERROR, e.getMessage());
        }

        return false;
    }

    @Nullable
    public static LitematicaSchematic createFromFile(File dir, String fileName)
    {
        if (fileName.endsWith(FILE_EXTENSION) == false)
        {
            fileName = fileName + FILE_EXTENSION;
        }

        File fileSchematic = new File(dir, fileName);

        if (fileSchematic.exists() == false || fileSchematic.canRead() == false)
        {
            InfoUtils.showGuiOrInGameMessage(MessageType.ERROR, "litematica.error.schematic_read_from_file_failed.cant_read", fileSchematic.getAbsolutePath());
            return null;
        }

        try
        {
            FileInputStream is = new FileInputStream(fileSchematic);
            NBTTagCompound nbt = CompressedStreamTools.readCompressed(is);
            is.close();

            if (nbt != null)
            {
                LitematicaSchematic schematic = new LitematicaSchematic(fileSchematic);

                if (schematic.readFromNBT(nbt))
                {
                    return schematic;
                }
            }
        }
        catch (Exception e)
        {
            InfoUtils.showGuiOrInGameMessage(MessageType.ERROR, "litematica.error.schematic_read_from_file_failed.exception", fileSchematic.getAbsolutePath());
        }

        return null;
    }

    public static class EntityInfo
    {
        public final Vec3d posVec;
        public final NBTTagCompound nbt;

        public EntityInfo(Vec3d posVec, NBTTagCompound nbt)
        {
            this.posVec = posVec;
            this.nbt = nbt;
        }
    }
}
