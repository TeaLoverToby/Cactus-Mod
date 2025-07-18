package UselessDuck.CactusMod;

import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

@Mod(
        modid = "CactusMod",
        useMetadata = true
)
public class CactusMod {
    public static final String MODID = "CactusMod";
    public static final String VERSION = "1.0";

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        ModConfig.init(event.getSuggestedConfigurationFile());
        Keybinds.register();
        MinecraftForge.EVENT_BUS.register(new ClientEventHandler());
        MinecraftForge.EVENT_BUS.register(new TeleportSafetyListener());
        ClientCommandHandler.instance.registerCommand(new ShowStringCommand());
    }
}