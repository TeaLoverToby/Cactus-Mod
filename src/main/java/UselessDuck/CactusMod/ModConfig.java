package UselessDuck.CactusMod;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import java.io.File;

public class ModConfig {
    private static Configuration config;
    private static Property showStringProperty;

    public static void init(File configFile) {
        config = new Configuration(configFile);
        loadConfig();
    }

    public static void loadConfig() {
        showStringProperty = config.get(Configuration.CATEGORY_CLIENT, "showString", false, 
            "RBG STRING On/off");

        if (config.hasChanged()) {
            config.save();
        }
    }

    public static boolean getShowString() {
        return showStringProperty.getBoolean();
    }

    public static void setShowString(boolean value) {
        showStringProperty.set(value);
        config.save();
    }
}
