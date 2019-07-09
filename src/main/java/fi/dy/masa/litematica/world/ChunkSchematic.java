package fi.dy.masa.litematica.world;

import java.util.Arrays;
import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Biomes;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumLightType;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;

public class ChunkSchematic extends Chunk
{
    private final long timeCreated;

    public ChunkSchematic(World worldIn, int x, int z)
    {
        super(worldIn, x, z, new Biome[256]);

        this.timeCreated = worldIn.getGameTime();
        Arrays.fill(this.getBiomes(), Biomes.PLAINS);
    }

    @Override
    public IBlockState setBlockState(BlockPos pos, IBlockState state, boolean isMoving)
    {
        int x = pos.getX() & 15;
        int y = pos.getY();
        int z = pos.getZ() & 15;

        IBlockState stateOld = this.getBlockState(pos);

        if (stateOld == state)
        {
            return null;
        }
        else
        {
            Block blockNew = state.getBlock();
            Block blockOld = stateOld.getBlock();
            ChunkSection section = this.getSections()[y >> 4];

            if (section == EMPTY_SECTION)
            {
                if (state.isAir())
                {
                    return null;
                }

                section = new ChunkSection(y >> 4 << 4, false);
                this.getSections()[y >> 4] = section;
            }

            section.set(x, y & 15, z, state);

            if (blockOld != blockNew)
            {
                this.getWorld().removeTileEntity(pos);
            }

            if (section.get(x, y & 0xF, z).getBlock() != blockNew)
            {
                return null;
            }
            else
            {
                if (blockOld instanceof ITileEntityProvider)
                {
                    TileEntity tileentity = this.getTileEntity(pos, Chunk.EnumCreateEntityType.CHECK);

                    if (tileentity != null)
                    {
                        tileentity.updateContainingBlockInfo();
                    }
                }

                /*
                if (this.getWorld().isRemote && blockOld != blockNew)
                {
                    blockNew.onBlockAdded(this.getWorld(), pos, state);
                }
                */

                if (blockNew instanceof ITileEntityProvider)
                {
                    TileEntity te = this.getTileEntity(pos, Chunk.EnumCreateEntityType.CHECK);

                    if (te == null)
                    {
                        te = ((ITileEntityProvider) blockNew).createNewTileEntity(this.getWorld());
                        this.getWorld().setTileEntity(pos, te);
                    }

                    if (te != null)
                    {
                        te.updateContainingBlockInfo();
                    }
                }

                this.markDirty();

                return stateOld;
            }
        }
    }

    public long getTimeCreated()
    {
        return this.timeCreated;
    }

    @Override
    public int getLightFor(EnumLightType type, BlockPos pos)
    {
        return 15;
    }

    @Override
    public int getLightSubtracted(BlockPos pos, int amount)
    {
        return 15;
    }

    @Override
    public void setLightFor(EnumLightType type, BlockPos pos, int value)
    {
        // NO-OP
    }

    @Override
    public void tick(boolean skipRecheckGaps)
    {
        // NO-OP
    }

    @Override
    public void generateSkylightMap()
    {
        // NO-OP
    }

    @Override
    public void enqueueRelightChecks()
    {
        // NO-OP
    }
}
