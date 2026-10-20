package pdx.dev.fbactors.voice;

import java.util.ArrayList;
import java.util.List;

import pdx.dev.fbactors.take.ActorTake;


public final class MicDevices {

    public record Device(String name, boolean isDefault) {
    }

    private MicDevices() {
    }

    public static List<Device> list() {
        List<Device> devices = new ArrayList<>();
        devices.add(new Device(null, true));
        for (String name : MicCapture.listDevices()) {
            if (name != null && !name.isBlank()) {
                devices.add(new Device(name, false));
            }
        }
        return devices;
    }


    public static boolean usable(String name) {
        return MicCapture.canCapture();
    }
}

