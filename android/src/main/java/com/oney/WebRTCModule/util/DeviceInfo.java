package com.oney.WebRTCModule.util;

import android.content.Context;
import android.os.Build;
import android.support.annotation.NonNull;

// NOTE: this is a stripped version (release_3.13.0-orca from 09/06/18) of the DeviceInfo class from atheer.util package
public enum DeviceInfo {

    // Supported devices
    // *** Make sure to add mapping to appsettings.json on AirApi for OTA to work ***
    ASUS_NEXUS_7          ("asus",                    "Nexus 7"), // QA DEVICE
    ATHEER_AIRGLASSES     ("ATHEER",                  "Atheer Krypto"),
    EPSON_BT300           ("EPSON",                   "EMBT3C"),
    EPSON_BT350           ("EPSON",                   "EMBT3S"),
    HTC_NEXUS_9           ("htc",                     "Nexus 9"),
    ODG_R7                ("Osterhout_Design_Group",  "R7-W"),
    REALWEAR              ("RealWear inc.",           "T1100G"),
    REALWEAR_1Z1          ("RealWear inc.",           "T1100S"),
    SAMSUNG_TAB_S2_8_T710 ("samsung",                 "SM-T710"),
    SAMSUNG_TAB_S2_8      ("samsung",                 "SM-T713"),
    SAMSUNG_TAB_S2_10     ("samsung",                 "SM-T813"),
    SAMSUNG_TAB_S2_10_V   ("samsung",                 "SM-T818V"), // verizon lte
    SAMSUNG_TAB_S3        ("samsung",                 "SM-T820"),
    VUZIX_M300            ("vuzix",                   "M300"),

    CURRENT               (Build.MANUFACTURER,        Build.MODEL);

    private static final Size DEFAULT_CAMERA_SIZE = new Size(1280, 720);
    private static final Size VUZIX_M300_VIDEO_SIZE = new Size(640, 480);

    private String manufacturer;
    private String model;

    DeviceInfo(String manufacturer, String model) {
        this.manufacturer = manufacturer;
        this.model = model;
    }

    public static boolean isCamera2Supported(@NonNull Context context) {
        // ODG R7 is considered legacy by camera2, but is fully supported by webrtc using camera2
        if (equals(CURRENT, ODG_R7)) {
            return true;
        }

        return CameraUtil.isCamera2Supported(context);
    }

    public static Size getCameraResolution() {
        return getCameraResolution(CURRENT);
    }

    public static Size getCameraResolution(DeviceInfo device) {
        // Limit vuzix m300 to 480p video
        if (equals(device, VUZIX_M300)) {
            return VUZIX_M300_VIDEO_SIZE;
        }

        // Leave it for WebRTC to decide
        return DEFAULT_CAMERA_SIZE;
    }

    private static boolean equals(DeviceInfo lhs, DeviceInfo rhs) {
        return lhs.manufacturer.equalsIgnoreCase(rhs.manufacturer) && lhs.model.equalsIgnoreCase(rhs.model);
    }
}