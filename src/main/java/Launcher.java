public class Launcher {
    static {
        System.setProperty("log4j2.isThreadContextMapInheritable", "true");
        System.setProperty("log4j2.disable.jmx", "true");
    }
    public static void main(String[] args) {
        try {
            Class.forName("net.minecraft.SharedConstants");
            System.out.println("SharedConstants preload successfully");
        } catch (Exception e) {
            System.err.println("SharedConstants preload failed: " + e.getMessage());
        }
        try {
            Class.forName("nl.jellejurre.seedchecker.SeedCheckerSettings");
            System.out.println("SeedCheckerSettings preload successfully");
        } catch (Exception e) {
            System.err.println("SeedCheckerSettings preload failed: " + e.getMessage());
        }
        CavefinderGUI.main(args);
    }
}