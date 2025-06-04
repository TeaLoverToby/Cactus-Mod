package UselessDuck.CactusMod;

import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraft.client.Minecraft;

public class Keybinds {
    public static final KeyBinding toggleKey = new KeyBinding("Toggle Cactus Mod", Keyboard.KEY_P, "Cactus Mod");
    public static final KeyBinding toggleStringKey = new KeyBinding("RGB String", Keyboard.KEY_O, "Cactus Mod");

    public static boolean isEnabled = false;
    private static boolean wasStringKeyPressed = false;

    public static void register() {
        ClientRegistry.registerKeyBinding(toggleKey);
        ClientRegistry.registerKeyBinding(toggleStringKey);
        MinecraftForge.EVENT_BUS.register(new Keybinds());
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            if (toggleKey.isPressed()) {
                isEnabled = !isEnabled;
                String status = isEnabled ? "\u00a7aEnabled" : "\u00a7cDisabled";
                String cactusBold = "\u00a7f\u00a7lCactus\u00a7b\u00a7lMod";
                String messageBold = "\u00a7a\u00a7lSaiCo\u00a7d\u00a7lPvP " + cactusBold + " " + status;
                ClientEventHandler.addChatMessage(messageBold);
               // System.out.println("[CactusMod] Main toggle - Mod " + status);
            }
            if (toggleStringKey.isPressed() && !wasStringKeyPressed) {
                wasStringKeyPressed = true;
                try {
                    ShowStringCommand.toggleShowString();
                    boolean newState = ShowStringCommand.isShowStringEnabled();
                    String status = newState ? "\u00a7aEnabled" : "\u00a7cDisabled";
                    String stringBold = "\u00a7f\u00a7lRGB\u00a7b\u00a7lString";
                    String messageBold = "\u00a7a\u00a7lSaiCo\u00a7d\u00a7lPvP " + stringBold + " " + status;
                    ClientEventHandler.addChatMessage(messageBold);
                   // System.out.println("[CactusMod] RGB String toggled: " + (newState ? "enabled" : "disabled"));
                } catch (Exception e) {
                   // System.out.println("[CactusMod] Error toggling RGB string: " + e.getMessage());
                }
            } else if (!toggleStringKey.isPressed()) {
                wasStringKeyPressed = false;
            }
        }
    }
}
