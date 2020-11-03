package com.oney.WebRTCModule;

import android.util.Log;

import org.webrtc.VideoCapturer;
import org.webrtc.CapturerObserver;
import org.webrtc.FileVideoCapturer;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public abstract class AbstractVideoCaptureController {

    /**
     * The {@link Log} tag with which {@code CameraCaptureController} is to log.
     */
    private static final String TAG
        = AbstractVideoCaptureController.class.getSimpleName();

    private boolean isAtheerCamera = false;

    private int width;
    private int height;
    private int fps;

    /**
     * {@link VideoCapturer} which this controller manages.
     */
    protected VideoCapturer videoCapturer;
    protected VideoCapturer savedVideoCapturer;
    protected CapturerObserver capturerObserver;

    public AbstractVideoCaptureController(int width, int height, int fps) {
        this.width = width;
        this.height = height;
        this.fps = fps;

        Log.d(TAG, "Creating Video Capturer:Constraints Computed:" + width + ":" + height + ":" + fps);
    }

    public void initializeVideoCapturer() {
        videoCapturer = createVideoCapturer();
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

    protected abstract VideoCapturer createVideoCapturer();
}
