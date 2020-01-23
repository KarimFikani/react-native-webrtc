package com.oney.WebRTCModule;

import android.util.Log;

import com.facebook.react.bridge.ReadableMap;

import org.webrtc.CameraEnumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.CapturerObserver;
import org.webrtc.FileVideoCapturer;
import org.webrtc.VideoCapturer;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class VideoCaptureController {
    /**
     * The {@link Log} tag with which {@code VideoCaptureController} is to log.
     */
    private static final String TAG
        = VideoCaptureController.class.getSimpleName();


    private boolean isFrontFacing;
    private boolean isAtheerCamera = false;

    /**
     * Values for width, height and fps (respectively) which will be
     * used to open the camera at.
     */
    private int width;
    private int height;
    private int fps;

    private final String deviceId;
    private final String facingMode;

    private CameraEnumerator cameraEnumerator;

    private CapturerObserver capturerObserver;

    /**
     * The {@link CameraEventsHandler} used with
     * {@link CameraEnumerator#createCapturer}. Cached because the
     * implementation does not do anything but logging unspecific to the camera
     * device's name anyway.
     */
    private final CameraEventsHandler cameraEventsHandler
        = new CameraEventsHandler();

    /**
     * {@link VideoCapturer} which this controller manages.
     */
    private VideoCapturer videoCapturer;
    private VideoCapturer savedVideoCapturer;

    public VideoCaptureController(CameraEnumerator cameraEnumerator, ReadableMap constraints) {
        this.cameraEnumerator = cameraEnumerator;

        width = constraints.getInt("width");
        height = constraints.getInt("height");
        fps = constraints.getInt("frameRate");

        deviceId = ReactBridgeUtil.getMapStrValue(constraints, "deviceId");
        facingMode = ReactBridgeUtil.getMapStrValue(constraints, "facingMode");

        Log.d(TAG, "Creating Video Capturer:Constraints Computed:" + width + ":" + height + ":" + fps);

        videoCapturer = createVideoCapturer(deviceId, facingMode);

    }

    public void dispose() {
        if (videoCapturer != null) {
            videoCapturer.dispose();
            videoCapturer = null;
        }
    }

    public VideoCapturer getVideoCapturer() {
        return videoCapturer;
    }

    public void startCapture() {
        try {
            Log.d(TAG, "Start Capture:Use OverRide:" + DeviceInfo.useOverRide());
            if(DeviceInfo.useOverRide()) {
                CameraSetting cameraSetting = DeviceInfo.getCameraSetting();
                Log.d(TAG, "Start Capture:Resolution Config:Camera Setting" + cameraSetting.width + ":" + cameraSetting.height + ":" + cameraSetting.fps);
                videoCapturer.startCapture(cameraSetting.width, cameraSetting.height, cameraSetting.fps);
            } else {
                Log.d(TAG, "Start Capture:Resolution Config:Constraints" + width + ":" + height + ":" + fps);
                videoCapturer.startCapture(width, height, fps);
            }
        } catch (RuntimeException e) {
            // XXX This can only fail if we initialize the capturer incorrectly,
            // which we don't. Thus, ignore any failures here since we trust
            // ourselves.
        }
    }

    public boolean stopCapture() {
        try {
            videoCapturer.stopCapture();
            return true;
        } catch (InterruptedException e) {
            return false;
        }
    }

    public void setCapturerObserver(CapturerObserver capturerObserver) {
        this.capturerObserver = capturerObserver;
    }

    public void toggleAtheerBuffer() {
        if (this.isAtheerCamera) {
            switchToRegularVideoCapturer();
        } else {
            switchToAtheerVideoCapturer();
        }
    }

    public void switchCamera() {
        if (videoCapturer instanceof CameraVideoCapturer) {
            CameraVideoCapturer capturer = (CameraVideoCapturer) videoCapturer;
            String[] deviceNames = cameraEnumerator.getDeviceNames();
            int deviceCount = deviceNames.length;

            // Nothing to switch to.
            if (deviceCount < 2) {
                return;
            }

            // The usual case.
            if (deviceCount == 2) {
                capturer.switchCamera(new CameraVideoCapturer.CameraSwitchHandler() {
                    @Override
                    public void onCameraSwitchDone(boolean b) {
                        isFrontFacing = b;
                    }

                    @Override
                    public void onCameraSwitchError(String s) {
                        Log.e(TAG, "Error switching camera: " + s);
                    }
                });
                return;
            }

            // If we are here the device has more than 2 cameras. Cycle through them
            // and switch to the first one of the desired facing mode.
            switchCamera(!isFrontFacing, deviceCount);
        }
    }

    /**
     * Helper function which tries to switch cameras until the desired facing mode is found.
     *
     * @param desiredFrontFacing - The desired front facing value.
     * @param tries - How many times to try switching.
     */
    private void switchCamera(boolean desiredFrontFacing, int tries) {
        CameraVideoCapturer capturer = (CameraVideoCapturer) videoCapturer;

        capturer.switchCamera(new CameraVideoCapturer.CameraSwitchHandler() {
            @Override
            public void onCameraSwitchDone(boolean b) {
                if (b != desiredFrontFacing) {
                    int newTries = tries-1;
                    if (newTries > 0) {
                        switchCamera(desiredFrontFacing, newTries);
                    }
                } else {
                    isFrontFacing = desiredFrontFacing;
                }
            }

            @Override
            public void onCameraSwitchError(String s) {
                Log.e(TAG, "Error switching camera: " + s);
            }
        });
    }

    private void switchToAtheerVideoCapturer() {
        try {
            stopCapture();
            savedVideoCapturer = videoCapturer;
            videoCapturer = new AtheerVideoCapturer();
            videoCapturer.initialize(null, null, capturerObserver);
            startCapture();
            this.isAtheerCamera = true;
            Log.d(TAG, "atheer video capturer created successfully");
        } catch (IOException e) {
            Log.d(TAG, "atheer video capturer failed");
            e.printStackTrace();
        }
    }

    private void switchToRegularVideoCapturer() {
        stopCapture();
        videoCapturer = savedVideoCapturer;
        savedVideoCapturer = null;
        startCapture();
        this.isAtheerCamera = false;
        Log.d(TAG, "switch back to regular camera successfully");
    }

    /**
     * Constructs a new {@code VideoCapturer} instance attempting to satisfy
     * specific constraints.
     *
     * @param deviceId the ID of the requested video device. If not
     * {@code null} and a {@code VideoCapturer} can be created for it, then
     * {@code facingMode} is ignored.
     * @param facingMode the facing of the requested video source such as
     * {@code user} and {@code environment}. If {@code null}, "user" is
     * presumed.
     * @return a {@code VideoCapturer} satisfying the {@code facingMode} or
     * {@code deviceId} constraint
     */
    private VideoCapturer createVideoCapturer(String deviceId, String facingMode) {
        String[] deviceNames = cameraEnumerator.getDeviceNames();
        List<String> failedDevices = new ArrayList<>();

        // If deviceId is specified, then it takes precedence over facingMode.
        if (deviceId != null) {
            for (String name : deviceNames) {
                if (name.equals(deviceId)) {
                    VideoCapturer videoCapturer
                        = cameraEnumerator.createCapturer(name, cameraEventsHandler);
                    String message = "Create user-specified camera " + name;
                    if (videoCapturer != null) {
                        Log.d(TAG, message + " succeeded");
                        this.isFrontFacing = cameraEnumerator.isFrontFacing(name);
                        return videoCapturer;
                    } else {
                        Log.d(TAG, message + " failed");
                        failedDevices.add(name);
                        break; // fallback to facingMode
                    }
                }
            }
        }

        // Otherwise, use facingMode (defaulting to front/user facing).
        final boolean isFrontFacing
            = facingMode == null || !facingMode.equals("environment");

        for (String name : deviceNames) {
            if (failedDevices.contains(name)) {
                continue;
            }
            try {
                // This can throw an exception when using the Camera 1 API.
                if (cameraEnumerator.isFrontFacing(name) != isFrontFacing) {
                    continue;
                }
            } catch (Exception e) {
                Log.e(
                    TAG,
                    "Failed to check the facing mode of camera " + name,
                    e);
                failedDevices.add(name);
                continue;
            }
            VideoCapturer videoCapturer
                = cameraEnumerator.createCapturer(name, cameraEventsHandler);
            String message = "Create camera " + name;
            if (videoCapturer != null) {
                Log.d(TAG, message + " succeeded");
                this.isFrontFacing = cameraEnumerator.isFrontFacing(name);
                return videoCapturer;
            } else {
                Log.d(TAG, message + " failed");
                failedDevices.add(name);
            }
        }

        // Fallback to any available camera.
        for (String name : deviceNames) {
            if (!failedDevices.contains(name)) {
                VideoCapturer videoCapturer
                    = cameraEnumerator.createCapturer(name, cameraEventsHandler);
                String message = "Create fallback camera " + name;
                if (videoCapturer != null) {
                    Log.d(TAG, message + " succeeded");
                    this.isFrontFacing = cameraEnumerator.isFrontFacing(name);
                    return videoCapturer;
                } else {
                    Log.d(TAG, message + " failed");
                    failedDevices.add(name);
                    // fallback to the next device.
                }
            }
        }

        Log.w(TAG, "Unable to identify a suitable camera.");

        return null;
    }
}
