package com.oney.WebRTCModule;

import android.os.Build;
import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Base64;
import java.util.Collections;

import android.net.Uri;
import android.os.Environment;
import android.os.HandlerThread;
import android.os.Handler;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.media.MediaScannerConnection;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.webrtc.*;

import androidx.annotation.RequiresApi;

/**
 * The implementation of {@code getUserMedia} extracted into a separate file in
 * order to reduce complexity and to (somewhat) separate concerns.
 */
class GetUserMediaImpl {
    /**
     * The {@link Log} tag with which {@code GetUserMediaImpl} is to log.
     */
    private static final String TAG = WebRTCModule.TAG;
    public static final int RCT_CAMERA_CAPTURE_TARGET_MEMORY = 0;
    public static final int RCT_CAMERA_CAPTURE_TARGET_TEMP = 1;
    public static final double RCT_CAMERA_CAPTURE_IMAGE_QUALITY = 0.60;
    public static final int RCT_CAMERA_CAPTURE_IMAGE_MAX_SIZE = 5000;

    private final CameraEnumerator cameraEnumerator;
    private final ReactApplicationContext reactContext;
    private final HandlerThread imageProcessingThread;
    private Handler imageProcessingHandler;

    /**
     * The application/library-specific private members of local
     * {@link MediaStreamTrack}s created by {@code GetUserMediaImpl} mapped by
     * track ID.
     */
    private final Map<String, TrackPrivate> tracks = new HashMap<>();

    private final WebRTCModule webRTCModule;

    private CameraVideoCapturer cameraVideoCapturer;

    GetUserMediaImpl(WebRTCModule webRTCModule, ReactApplicationContext reactContext) {
        this.webRTCModule = webRTCModule;
        this.reactContext = reactContext;

        boolean camera2supported = false;

        try {
            camera2supported = Camera2Enumerator.isSupported(reactContext);
        } catch (Throwable tr) {
            // Some devices will crash here with: Fatal Exception: java.lang.AssertionError: Supported FPS ranges cannot be null.
            // Make sure we don't.
            Log.w(TAG, "Error checking for Camera2 API support.", tr);
        }

        if (camera2supported) {
            Log.d(TAG, "Creating video capturer using Camera2 API.");
            cameraEnumerator = new Camera2Enumerator(reactContext);
        } else {
            Log.d(TAG, "Creating video capturer using Camera1 API.");
            cameraEnumerator = new Camera1Enumerator(false);
        }
        imageProcessingThread = new HandlerThread("SnapshotThread");
        imageProcessingThread.start();
        imageProcessingHandler = new Handler(imageProcessingThread.getLooper());
    }

    private AudioTrack createAudioTrack(ReadableMap constraints) {
        ReadableMap audioConstraintsMap = constraints.getMap("audio");

        Log.d(TAG, "getUserMedia(audio): " + audioConstraintsMap);

        String id = UUID.randomUUID().toString();
        PeerConnectionFactory pcFactory = webRTCModule.mFactory;
        AudioSource audioSource = pcFactory.createAudioSource(webRTCModule.constraintsForOptions(audioConstraintsMap));
        AudioTrack track = pcFactory.createAudioTrack(id, audioSource);
        tracks.put(
            id,
            new TrackPrivate(track, audioSource, /* videoCapturer */ null));

        return track;
    }

    private VideoTrack createVideoTrack(ReadableMap constraints) {
        ReadableMap videoConstraintsMap = constraints.getMap("video");

        Log.d(TAG, "getUserMedia(video): " + videoConstraintsMap);

        VideoCaptureController videoCaptureController
            = new VideoCaptureController(cameraEnumerator, videoConstraintsMap);
        VideoCapturer videoCapturer = videoCaptureController.getVideoCapturer();
        if (videoCapturer == null) {
            return null;
        }

        cameraVideoCapturer = (CameraVideoCapturer) videoCapturer;

        PeerConnectionFactory pcFactory = webRTCModule.mFactory;
        EglBase.Context eglContext = EglUtils.getRootEglBaseContext();
        SurfaceTextureHelper surfaceTextureHelper =
            SurfaceTextureHelper.create("CaptureThread", eglContext);
        VideoSource videoSource = pcFactory.createVideoSource(videoCapturer.isScreencast());
        videoCapturer.initialize(surfaceTextureHelper, reactContext, videoSource.getCapturerObserver());
        videoCaptureController.setCapturerObserver(videoSource.getCapturerObserver());

        String id = UUID.randomUUID().toString();
        VideoTrack track = pcFactory.createVideoTrack(id, videoSource);

        track.setEnabled(true);
        videoCaptureController.startCapture();

        tracks.put(id, new TrackPrivate(track, videoSource, videoCaptureController));

        return track;
    }

    ReadableArray enumerateDevices() {
        WritableArray array = Arguments.createArray();
        String[] devices = cameraEnumerator.getDeviceNames();

        for(int i = 0; i < devices.length; ++i) {
            String deviceName = devices[i];
            boolean isFrontFacing;
            try {
                // This can throw an exception when using the Camera 1 API.
                isFrontFacing = cameraEnumerator.isFrontFacing(deviceName);
            } catch (Exception e) {
                Log.e(TAG, "Failed to check the facing mode of camera");
                continue;
            }
            WritableMap params = Arguments.createMap();
            params.putString("facing", isFrontFacing ? "front" : "environment");
            params.putString("deviceId", "" + i);
            params.putString("groupId", "");
            params.putString("label", deviceName);
            params.putString("kind", "videoinput");
            array.pushMap(params);
        }

        WritableMap audio = Arguments.createMap();
        audio.putString("deviceId", "audio-1");
        audio.putString("groupId", "");
        audio.putString("label", "Audio");
        audio.putString("kind", "audioinput");
        array.pushMap(audio);

        return array;
    }

    MediaStreamTrack getTrack(String id) {
        TrackPrivate private_ = tracks.get(id);

        return private_ == null ? null : private_.track;
    }

    /**
     * Implements {@code getUserMedia}. Note that at this point constraints have
     * been normalized and permissions have been granted. The constraints only
     * contain keys for which permissions have already been granted, that is,
     * if audio permission was not granted, there will be no "audio" key in
     * the constraints map.
     */
    void getUserMedia(
            final ReadableMap constraints,
            final Callback successCallback,
            final Callback errorCallback) {
        // TODO: change getUserMedia constraints format to support new syntax
        //   constraint format seems changed, and there is no mandatory any more.
        //   and has a new syntax/attrs to specify resolution
        //   should change `parseConstraints()` according
        //   see: https://www.w3.org/TR/mediacapture-streams/#idl-def-MediaTrackConstraints

        AudioTrack audioTrack = null;
        VideoTrack videoTrack = null;

        if (constraints.hasKey("audio")) {
            audioTrack = createAudioTrack(constraints);
        }

        if (constraints.hasKey("video")) {
            videoTrack = createVideoTrack(constraints);
        }

        if (audioTrack == null && videoTrack == null) {
             // Fail with DOMException with name AbortError as per:
             // https://www.w3.org/TR/mediacapture-streams/#dom-mediadevices-getusermedia
             errorCallback.invoke("DOMException","AbortError");
             return;
        }

        String streamId = UUID.randomUUID().toString();
        MediaStream mediaStream
            = webRTCModule.mFactory.createLocalMediaStream(streamId);
        WritableArray tracks = Arguments.createArray();

        for (MediaStreamTrack track : new MediaStreamTrack[]{audioTrack, videoTrack}) {
            if (track == null) {
                continue;
            }

            if (track instanceof AudioTrack) {
                mediaStream.addTrack((AudioTrack) track);
            } else {
                mediaStream.addTrack((VideoTrack) track);
            }

            WritableMap track_ = Arguments.createMap();
            String trackId = track.id();

            track_.putBoolean("enabled", track.enabled());
            track_.putString("id", trackId);
            track_.putString("kind", track.kind());
            track_.putString("label", trackId);
            track_.putString("readyState", track.state().toString());
            track_.putBoolean("remote", false);
            tracks.pushMap(track_);
        }

        Log.d(TAG, "MediaStream id: " + streamId);
        webRTCModule.localStreams.put(streamId, mediaStream);

        successCallback.invoke(streamId, tracks);
    }

    void mediaStreamTrackSetEnabled(String trackId, final boolean enabled) {
        TrackPrivate track = tracks.get(trackId);
        if (track != null && track.videoCaptureController != null) {
            if (enabled) {
                track.videoCaptureController.startCapture();
            } else {
                track.videoCaptureController.stopCapture();
            }
        }
    }

    void disposeTrack(String id) {
        TrackPrivate track = tracks.remove(id);
        if (track != null) {
            track.dispose();
        }
        imageProcessingHandler.removeCallbacksAndMessages(null);
        imageProcessingThread.quit();
    }

    void switchCamera(String trackId) {
        TrackPrivate track = tracks.get(trackId);
        if (track != null && track.videoCaptureController != null) {
            track.videoCaptureController.switchCamera();
        }
    }

    void toggleAtheerBuffer(String trackId) {
        TrackPrivate track = tracks.get(trackId);
        if (track != null && track.videoCaptureController != null) {
            track.videoCaptureController.toggleAtheerBuffer();
        }
    }

    boolean hasTorch() {
        return cameraVideoCapturer != null && cameraVideoCapturer.hasTorch();
    }

    boolean toggleFlashlight(boolean flashlightState) {
        return hasTorch() && cameraVideoCapturer.setTorch(flashlightState);
    }

    void captureScreenshot(String trakId, Callback successCallback, Callback errorCallback) {
        if (trakId != null)
            takePicture(trakId, successCallback, errorCallback);
    }


    /**
     * Application/library-specific private members of local
     * {@code MediaStreamTrack}s created by {@code GetUserMediaImpl}.
     */
    private static class TrackPrivate {
        /**
         * The {@code MediaSource} from which {@link #track} was created.
         */
        public final MediaSource mediaSource;

        public final MediaStreamTrack track;

        /**
         * The {@code VideoCapturer} from which {@link #mediaSource} was created
         * if {@link #track} is a {@link VideoTrack}.
         */
        public final VideoCaptureController videoCaptureController;

        /**
         * Whether this object has been disposed or not.
         */
        private boolean disposed;

        /**
         * Initializes a new {@code TrackPrivate} instance.
         *
         * @param track
         * @param mediaSource the {@code MediaSource} from which the specified
         * {@code code} was created
         * @param videoCaptureController the {@code VideoCaptureController} from which the
         * specified {@code mediaSource} was created if the specified
         * {@code track} is a {@link VideoTrack}
         */
        public TrackPrivate(
                MediaStreamTrack track,
                MediaSource mediaSource,
                VideoCaptureController videoCaptureController) {
            this.track = track;
            this.mediaSource = mediaSource;
            this.videoCaptureController = videoCaptureController;
            this.disposed = false;
        }

        public void dispose() {
            if (!disposed) {
                if (videoCaptureController != null) {
                    if (videoCaptureController.stopCapture()) {
                        videoCaptureController.dispose();
                    }
                }
                mediaSource.dispose();
                track.dispose();
                disposed = true;
            }
        }
    }

    public void takePicture(final String trackId, final Callback successCallback, final Callback errorCallback) {
        final int captureTarget = RCT_CAMERA_CAPTURE_TARGET_TEMP;
        final double maxJpegQuality = RCT_CAMERA_CAPTURE_IMAGE_QUALITY;
        final int maxSize = RCT_CAMERA_CAPTURE_IMAGE_MAX_SIZE;

        if (!tracks.containsKey(trackId)) {
            errorCallback.invoke("Invalid trackId " + trackId);
            return ;
        }

        VideoCapturer vc = tracks.get(trackId).videoCaptureController.getVideoCapturer();

        if ( !(vc instanceof CameraCapturer) ) {
            errorCallback.invoke("Wrong class in package");
        } else {
            CameraCapturer camCap = (CameraCapturer) vc;
            camCap.takeSnapshot(new CameraCapturer.SingleCaptureCallBack() {
                @RequiresApi(api = Build.VERSION_CODES.O)
                @Override
                public void captureSuccess(byte[] jpeg, int orientation) {
                    Log.d("TAG", "captureSuccess:" + jpeg.length / 1024 + " mb" + "-" + captureTarget + ",orientation:" + orientation);
                    if (captureTarget == RCT_CAMERA_CAPTURE_TARGET_MEMORY)
                        successCallback.invoke(Base64.getEncoder().encodeToString(jpeg));
                    else {
                        try {
                            String path = savePicture(jpeg, captureTarget, maxJpegQuality, maxSize, orientation);
                            successCallback.invoke(path);
                        } catch (IOException e){
                            String message = "Error saving picture";
                            Log.d(TAG, message, e);
                            errorCallback.invoke(message);
                        }
                    }
                }

                @Override
                public void captureFailed(String err) {
                    errorCallback.invoke(err);
                }
            }, this.imageProcessingHandler);
        }
    }

    private synchronized String savePicture(byte[] jpeg, int captureTarget, double maxJpegQuality, int maxSize, int orientation) throws IOException {
        String filename = UUID.randomUUID().toString();
        File file = null;
        switch (captureTarget) {
            case RCT_CAMERA_CAPTURE_TARGET_TEMP: {
                file = getTempMediaFile(filename);
                writePictureToFile(jpeg, file, maxSize, maxJpegQuality, orientation);
                break;
            }
        }
        return file.getAbsolutePath();
    }

    private String writePictureToFile(byte[] jpeg, File file, int maxSize, double jpegQuality, int orientation) throws IOException {
        FileOutputStream output = new FileOutputStream(file);
        output.write(jpeg);
        output.close();
        Matrix matrix = new Matrix();
        matrix.setRotate(orientation);

        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
        // scale if needed
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        // only resize if image larger than maxSize
        if (width > maxSize && height > maxSize) {
            Rect originalRect = new Rect(0, 0, width, height);
            Rect scaledRect = scaleDimension(originalRect, maxSize);
            Log.d(TAG, "scaled width = " + scaledRect.width() + ", scaled height = " + scaledRect.height());
            // calculate the scale
            float scaleWidth = ((float) scaledRect.width()) / width;
            float scaleHeight = ((float) scaledRect.height()) / height;
            matrix.postScale(scaleWidth, scaleHeight);
        }

        FileOutputStream finalOutput = new FileOutputStream(file, false);
        int compression = (int) (100 * jpegQuality);
        Bitmap rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, compression, finalOutput);
        finalOutput.close();
        return file.getAbsolutePath();
    }


    private File getTempMediaFile(String fileName) {
        try {
            File outputDir = reactContext.getCacheDir();
            File outputFile;
            outputFile = File.createTempFile(fileName, ".jpg", outputDir);
            return outputFile;
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
            return null;
        }
    }

    private static Rect scaleDimension(Rect originalRect, int maxSize) {
        int originalWidth = originalRect.width();
        int originalHeight = originalRect.height();
        int newWidth = originalWidth;
        int newHeight = originalHeight;
        // first check if we need to scale width
        if (originalWidth > maxSize) {
            //scale width to fit
            newWidth = maxSize;
            //scale height to maintain aspect ratio
            newHeight = (newWidth * originalHeight) / originalWidth;
        }
        // then check if we need to scale even with the new height
        if (newHeight > maxSize) {
            //scale height to fit instead
            newHeight = maxSize;
            //scale width to maintain aspect ratio
            newWidth = (newHeight * originalWidth) / originalHeight;
        }
        return new Rect(0, 0, newWidth, newHeight);
    }
}
