package com.oney.WebRTCModule;

import android.content.Context;
import android.os.Build;
import android.support.annotation.NonNull;

import org.webrtc.*;

// NOTE: this is a stripped version (release_3.13.0-orca from 09/06/18) of the DeviceInfo class from atheer.util package
public class DeviceInfo {

    private static CameraSetting DEFAULT_CAMERA_SETTING = new CameraSetting(1280, 720, 30);
    private static CameraSetting cameraSetting;
    private static boolean useOverRide = false;

    public static boolean useCamera2(@NonNull Context context) {
        return true;
    }

    public static CameraSetting getCameraSetting() {
        if(cameraSetting != null) {
            return cameraSetting;
        } else {
            return DEFAULT_CAMERA_SETTING;
        }
    }

    public static void setCameraSetting(CameraSetting cameraSettingInfo) {
        useOverRide = true;
        cameraSetting = cameraSettingInfo;
    }

    public static boolean useOverRide() {
        return useOverRide;
    }
}
