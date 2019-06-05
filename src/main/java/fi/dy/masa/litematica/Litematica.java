package fi.dy.masa.litematica;

import fi.dy.masa.malilib.event.InitializationHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dimdev.riftloader.listener.InitializationListener;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.Mixins;

public class Litematica implements InitializationListener
{
    public static final Logger logger = LogManager.getLogger(Reference.MOD_ID);

    @Override
    public void onInitialization()
    {
        MixinBootstrap.init();
        Mixins.addConfiguration("mixins.litematica.json");

        InitializationHandler.getInstance().registerInitializationHandler(new InitHandler());
    }

    public static void logInfo(String message, Object... args)
    {
        //if (Configs.Generic.VERBOSE_LOGGING.getBooleanValue())
        {
            logger.info(message, args);
        }
    }
}
