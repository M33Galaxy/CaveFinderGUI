public class Launcher {
    static {
        System.setProperty("log4j2.isThreadContextMapInheritable", "true");
        System.setProperty("log4j2.disable.jmx", "true");
    }
    public static void main(String[] args) {
        try {
            Class.forName("net.minecraft.SharedConstants");
            System.out.println("SharedConstants 预加载成功");
        } catch (Exception e) {
            System.err.println("SharedConstants 预加载失败: " + e.getMessage());
        }
        try {
            Class.forName("nl.jellejurre.seedchecker.SeedCheckerSettings");
            System.out.println("SeedCheckerSettings 预加载成功");
        } catch (Exception e) {
            System.err.println("SeedCheckerSettings 预加载失败: " + e.getMessage());
        }
        CavefinderGUI.main(args);
    }
}