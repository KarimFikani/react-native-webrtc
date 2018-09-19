package com.oney.WebRTCModule.util;

import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.os.Build;
import android.util.Log;

// NOTE: this is a stripped version (release_3.13.0-orca from 09/06/18) of the CameraUtil class from atheer.util package
public class CameraUtil {

    private static final String TAG = CameraUtil.class.getSimpleName();

    public static boolean isCamera2Supported(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CameraManager cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);

            try {
                String[] cameraIdList = cameraManager.getCameraIdList();
                int len = cameraIdList.length;

                for (int i = 0; i < len; ++i) {
                    String id = cameraIdList[i];
                    CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(id);
                    int support = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
                    if (support == CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
                        return false;
                    }
                }

                return true;
            } catch (CameraAccessException e) {
                Log.e(TAG, "Unable to access camera", e);
                return false;
            }
        } else {
            return false;
        }
    }
}

