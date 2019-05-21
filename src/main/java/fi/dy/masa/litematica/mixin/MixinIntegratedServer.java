package fi.dy.masa.litematica.mixin;

import java.io.File;

import net.minecraft.client.Minecraft;
import net.minecraft.command.Commands;
import net.minecraft.world.WorldSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.mojang.authlib.GameProfileRepository;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import fi.dy.masa.litematica.scheduler.TaskScheduler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.management.PlayerProfileCache;

@Mixin(IntegratedServer.class)
public abstract class MixinIntegratedServer extends MinecraftServer
{
    private MixinIntegratedServer(Minecraft clientIn,
                                  String folderNameIn,
                                  String worldNameIn,
                                  WorldSettings worldSettingsIn,
                                  YggdrasilAuthenticationService authServiceIn,
                                  MinecraftSessionService sessionServiceIn,
                                  GameProfileRepository profileRepoIn,
                                  PlayerProfileCache profileCacheIn)
    {
        super(new File(clientIn.gameDir, "saves"),
                clientIn.getProxy(),
                clientIn.getDataFixer(),
                new Commands(false),
                authServiceIn,
                sessionServiceIn,
                profileRepoIn,
                profileCacheIn
        );
    }

    @Inject(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/MinecraftServer;tick(Ljava/util/function/BooleanSupplier;)V", shift = Shift.AFTER))
    private void onPostTick(CallbackInfo ci)
    {
        TaskScheduler.getInstanceServer().runTasks();
    }
}
