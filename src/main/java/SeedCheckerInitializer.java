import nl.jellejurre.seedchecker.SeedChecker;
import nl.jellejurre.seedchecker.SeedCheckerDimension;
import nl.jellejurre.seedchecker.TargetState;
public class SeedCheckerInitializer {
    private static volatile boolean initialized = false;
    private static final Object lock = new Object();
    public static void initialize() {
        if (initialized) {
            return;
        }
        synchronized (lock) {
            if (initialized) {
                return;
            }
            try {
                System.out.println("Initializing SeedChecker...");
                SeedChecker preInit = new SeedChecker(0L, TargetState.NO_STRUCTURES, SeedCheckerDimension.OVERWORLD);
                preInit.clearMemory();
                initialized = true;
                System.out.println("SeedChecker Initialize successfully");
            } catch (Exception e) {
                System.err.println("SeedChecker Initialize failed: " + e.getMessage());
                initialized = true;
            }
        }
    }
    public static boolean isInitialized() {
        return initialized;
    }
}