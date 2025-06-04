package UselessDuck.CactusMod;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

public class ShowStringCommand extends CommandBase {
    private static boolean showString = false;

    public ShowStringCommand() {
        showString = ModConfig.getShowString();
    }

    @Override
    public String getCommandName() {
        return "showstring";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/showstring - Toggle RBG String";
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) throws CommandException {
        showString = !showString;
        ModConfig.setShowString(showString);

        String status = showString ? "enabled" : "disabled";
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.LIGHT_PURPLE + "Rainbow string highlighting " + status));
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    public static boolean isShowStringEnabled() {
        return showString;
    }
    
    public static void toggleShowString() {
        showString = !showString;
        ModConfig.setShowString(showString);
    }
}