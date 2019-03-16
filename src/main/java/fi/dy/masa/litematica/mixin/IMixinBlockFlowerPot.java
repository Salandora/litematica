package fi.dy.masa.litematica.mixin;

import net.minecraft.block.Block;
import net.minecraft.block.BlockFlowerPot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BlockFlowerPot.class)
public interface IMixinBlockFlowerPot {
    @Accessor("field_196452_c")
    Block getFlower();
}
