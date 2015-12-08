package functional.tests.core.Device.Android;

import functional.tests.core.Enums.DeviceType;
import functional.tests.core.Enums.OSType;
import functional.tests.core.Exceptions.DeviceException;
import functional.tests.core.Find.Wait;
import functional.tests.core.Log.Log;
import functional.tests.core.OSUtils.FileSystem;
import functional.tests.core.OSUtils.OSUtils;
import functional.tests.core.Settings.Settings;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeoutException;

public class Adb {

    private static final String adbPath = System.getenv("ANDROID_HOME") + File.separator + "platform-tools" + File.separator + "adb";
    private static final String androidPath = System.getenv("ANDROID_HOME") + File.separator + "tools" + File.separator + "android";
    private static final String emulatorPath = System.getenv("ANDROID_HOME") + File.separator + "tools" + File.separator + "emulator";
    private static final String emulatorStartLogPath = Settings.baseLogDir + File.separator + "emulator.logs";

    private static String runAdbCommand(String command) {
        String adbCommand = adbPath + " " + command;
        String output = OSUtils.runProcess(true, adbCommand);
        if (output.toLowerCase().contains("address already in use")) {
            killAdbProcess();
            output = OSUtils.runProcess(true, adbCommand);
        }
        return output;
    }

    private static String runAdbCommand(String deviceId, String command) {
        String adbCommand = adbPath + " -s " + deviceId + " " + command;
        String output = OSUtils.runProcess(true, adbCommand);
        if (output.toLowerCase().contains("address already in use")) {
            killAdbProcess();
            output = OSUtils.runProcess(true, adbCommand);
        }
        return output;
    }

    private static List<String> getDevices() {
        String rowData = runAdbCommand("devices");
        String[] list = rowData.split("\\r?\\n");
        return Arrays.asList(list);
    }

    public static void startAdb() {
        Log.info("Start adb");
        runAdbCommand("start-server");
    }

    public static void stopAdb() {
        Log.info("Stop adb");
        runAdbCommand("kill-server");
    }

    public static void killAdbProcess() {
        if (Settings.OS == OSType.Windows) {
            OSUtils.stopProcess("adb.exe");
        } else {
            OSUtils.stopProcess("adb");
        }
    }

    public static List<String> getInstalledApps() {
        String rowData = runAdbCommand(Settings.deviceId, "shell pm list packages");
        String trimData = rowData.replace("package:", "");
        String[] list = trimData.split("\\r?\\n");
        return Arrays.asList(list);
    }

    public static void stopApp(String appId) {
        String stopCommand = runAdbCommand(Settings.deviceId, "shell am force-stop " + appId);
        OSUtils.runProcess(true, stopCommand);
    }

    public static void uninstallApp(String appId) {
        stopApp(appId);

        String uninstallResult = runAdbCommand(Settings.deviceId, "shell pm uninstall -k " + appId);

        if (uninstallResult.contains("Success")) {
            Log.info(appId + " successfully uninstalled.");
        } else {
            Log.error("Failed to uninstall " + appId + ". Error: " + uninstallResult);
        }
    }

    // If emulator with same name exists, do nothing, else create emulator
    public static void createEmulator(String avdName, String options) throws DeviceException {

        String avds = OSUtils.runProcess(true, androidPath + " list avds");
        if (avds.contains(avdName + ".avd")) {
            Log.info(avdName + " already exists.");
        } else {
            // Create emulator
            String command;
            if (Settings.OS == OSType.Windows) {
                Log.fatal("Create emulator not implemented for Windows systems.");
                throw new UnsupportedOperationException("Create emulator not implemented for Windows systems.");
            } else {
                command = "echo no | " + androidPath + " -s create avd -n " + avdName + " " + options + " -f";
            }

            Log.info("Create emulator with command: ");
            Log.info(command);
            OSUtils.runProcess(true, command);

            // Verify it exists
            avds = OSUtils.runProcess(true, androidPath + " list avds");
            if (avds.contains(avdName + ".avd")) {
                Log.info(avdName + " created successfully.");
            } else {
                String error = "Emulator " + avdName + " is not available!";
                Log.fatal(error);
                throw new DeviceException(error);
            }
        }
    }

    public static void startEmulator(String avdName, int port) {
        String command = emulatorPath + " -port " + port + " -avd " + avdName;
        if (Settings.emulatorOptions != null) {
            command = command + " " + Settings.emulatorOptions;
        }
        command = command + " > " + emulatorStartLogPath + " &";
        Log.info("Starting emulator with command: " + command);
        OSUtils.runProcess(false, command);
    }

    public static void stopEmulator() {
        if (Settings.OS == OSType.Windows) {
            OSUtils.stopProcess("emulator64-x86.exe");
            OSUtils.stopProcess("emulator-x86.exe");
            OSUtils.stopProcess("emulator-arm.exe");
        } else {
            OSUtils.stopProcess("emulator64-x86");
            OSUtils.stopProcess("emulator-x86");
            OSUtils.stopProcess("emulator-arm");
        }
        Log.info("Emulator killed.");
    }

    public static void waitForDevice(String deviceId, int timeOut) throws TimeoutException {
        long startTime = new Date().getTime();
        for (int i = 0; i < 999; i++) {

            boolean found = false;
            long currentTime = new Date().getTime();

            String emulatorStartupLog = "";
            if ((currentTime - startTime) < timeOut * 1000) {

                List<String> devices = getDevices();

                for (String device : devices) {
                    if (device.contains(deviceId) && device.contains("device")) {
                        found = true;
                        break;
                    } else if (Settings.deviceType == DeviceType.Emulator) {
                        try {
                            emulatorStartupLog = FileSystem.readFile(emulatorStartLogPath);
                        } catch (IOException e) {
                            Log.error("Failed to read emulator log: " + emulatorStartLogPath);
                        }
                        if (emulatorStartupLog.contains("ERROR")) {
                            found = false;
                            break;
                        }
                    }
                }

                if (found) {
                    Log.info("Device " + deviceId + " found.");
                    break;
                } else {
                    Log.info("Device " + deviceId + " not found. Wait...");
                    Wait.sleep(3000);
                }
            } else {
                String error = "Failed to find device "
                        + deviceId + " in " + String.valueOf(timeOut)
                        + " seconds.";
                Log.fatal(error);
                if (Settings.deviceType == DeviceType.Emulator) {
                    Log.separator();
                    Log.error(emulatorStartupLog);
                }

                throw new TimeoutException(error);
            }
        }
    }

    public static void waitUntilEmulatorBoot(String deviceId, int timeOut) throws TimeoutException {
        long startTime = new Date().getTime();
        for (int i = 0; i < 999; i++) {

            boolean found = false;
            long currentTime = new Date().getTime();

            if ((currentTime - startTime) < timeOut * 1000) {

                String rowData = runAdbCommand(deviceId, "shell dumpsys activity");
                String[] list = rowData.split("\\r?\\n");

                for (String line : list) {
                    if (line.contains("Recent #0") && line.contains("com.android.launcher")) {
                        found = true;
                        break;
                    }
                }

                if (found) {
                    Log.info("Emulator is up and running.");
                    break;
                } else {
                    Log.info("Booting...");
                    Wait.sleep(3000);
                }
            } else {
                String error = "Failed to load com.android.launcher activity in "
                        + String.valueOf(timeOut)
                        + " seconds.";
                Log.fatal(error);
                throw new TimeoutException(error);
            }
        }
    }

    public static boolean isLocked(String deviceId) {
        String output = runAdbCommand(deviceId, "shell dumpsys window windows");
        if (output.contains("mDrawState=HAS_DRAWN mLastHidden=true")) {
            return true;
        } else {
            return false;
        }
    }

    public static void unlock(String deviceId) {
        runAdbCommand(deviceId, "shell input keyevent 82");
    }
}
