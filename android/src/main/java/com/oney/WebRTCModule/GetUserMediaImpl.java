package com.oney.WebRTCModule;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.ImageFormat;
import android.graphics.YuvImage;
import android.media.projection.MediaProjectionManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;
import android.net.Uri;
import android.os.Environment;
import android.os.HandlerThread;
import android.os.Handler;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.media.MediaScannerConnection;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.BaseActivityEventListener;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.UiThreadUtil;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Base64;
import java.util.Collections;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.webrtc.*;

/**
 * The implementation of {@code getUserMedia} extracted into a separate file in
 * order to reduce complexity and to (somewhat) separate concerns.
 */
class GetUserMediaImpl {
    /**
     * The {@link Log} tag with which {@code GetUserMediaImpl} is to log.
     */
    private static final String TAG = WebRTCModule.TAG;

    private static final int PERMISSION_REQUEST_CODE = (int) (Math.random() * Short.MAX_VALUE);
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

    private Promise displayMediaPromise;
    private Intent mediaProjectionPermissionResultData;

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

        reactContext.addActivityEventListener(new BaseActivityEventListener() {
            @Override
            public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
                super.onActivityResult(activity, requestCode, resultCode, data);
                if (requestCode == PERMISSION_REQUEST_CODE) {
                    if (resultCode != Activity.RESULT_OK) {
                        displayMediaPromise.reject("DOMException", "NotAllowedError");
                        displayMediaPromise = null;
                        return;
                    }

                    mediaProjectionPermissionResultData = data;
                    createScreenStream();
                }
            }
        });

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

    ReadableArray enumerateDevices() {
        WritableArray array = Arguments.createArray();
        String[] devices = cameraEnumerator.getDeviceNames();

        for (int i = 0; i < devices.length; ++i) {
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
            ReadableMap videoConstraintsMap = constraints.getMap("video");

            Log.d(TAG, "getUserMedia(video): " + videoConstraintsMap);

            CameraCaptureController cameraCaptureController = new CameraCaptureController(
                cameraEnumerator,
                videoConstraintsMap);

            videoTrack = createVideoTrack(cameraCaptureController);
        }

        if (audioTrack == null && videoTrack == null) {
            // Fail with DOMException with name AbortError as per:
            // https://www.w3.org/TR/mediacapture-streams/#dom-mediadevices-getusermedia
            errorCallback.invoke("DOMException", "AbortError");
            return;
        }

        createStream(new MediaStreamTrack[]{audioTrack, videoTrack}, (streamId, tracksInfo) -> {
            WritableArray tracksInfoWritableArray = Arguments.createArray();

            for (WritableMap trackInfo : tracksInfo) {
                tracksInfoWritableArray.pushMap(trackInfo);
            }

            successCallback.invoke(streamId, tracksInfoWritableArray);
        });
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
        if (track != null && track.videoCaptureController instanceof CameraCaptureController) {
            CameraCaptureController cameraCaptureController = (CameraCaptureController) track.videoCaptureController;
            cameraCaptureController.switchCamera();
        }
    }

    void toggleAtheerBuffer(String trackId) {
        TrackPrivate track = tracks.get(trackId);
        if (track != null && track.videoCaptureController instanceof CameraCaptureController) {
            CameraCaptureController cameraCaptureController = (CameraCaptureController) track.videoCaptureController;
            cameraCaptureController.toggleAtheerBuffer();
        }
    }

    boolean hasTorch(String trackId) {
        TrackPrivate track = tracks.get(trackId);
        if (track != null && track.videoCaptureController instanceof CameraCaptureController) {
            CameraCaptureController cameraCaptureController = (CameraCaptureController) track.videoCaptureController;
            CameraVideoCapturer cameraVideoCapturer = (CameraVideoCapturer) cameraCaptureController.videoCapturer;
            return cameraVideoCapturer != null && cameraVideoCapturer.hasTorch();
        } else {
            return false;
        }
    }

    boolean toggleFlashlight(String trackId, boolean flashlightState) {
        TrackPrivate track = tracks.get(trackId);
        if (track != null && track.videoCaptureController instanceof CameraCaptureController) {
            CameraCaptureController cameraCaptureController = (CameraCaptureController) track.videoCaptureController;
            CameraVideoCapturer cameraVideoCapturer = (CameraVideoCapturer) cameraCaptureController.videoCapturer;
            return hasTorch(trackId) && cameraVideoCapturer.setTorch(flashlightState);
        } else {
            return false;
        }
    }

    void captureScreenshot(String trakId, Callback successCallback, Callback errorCallback) {
        if (trakId != null)
            takePicture(trakId, successCallback, errorCallback);
    }

    void getDisplayMedia(Promise promise) {
        if (this.displayMediaPromise != null) {
            promise.reject(new RuntimeException("Another operation is pending."));
            return;
        }

        Activity currentActivity = this.reactContext.getCurrentActivity();
        if (currentActivity == null) {
            promise.reject(new RuntimeException("No current Activity."));
            return;
        }

        this.displayMediaPromise = promise;

        MediaProjectionManager mediaProjectionManager =
            (MediaProjectionManager) currentActivity.getApplication().getSystemService(
                Context.MEDIA_PROJECTION_SERVICE);

        if (mediaProjectionManager != null) {
            UiThreadUtil.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    currentActivity.startActivityForResult(
                        mediaProjectionManager.createScreenCaptureIntent(), PERMISSION_REQUEST_CODE);
                }
            });

        } else {
            promise.reject(new RuntimeException("MediaProjectionManager is null."));
        }
    }

    private void createScreenStream() {
        VideoTrack track = createScreenTrack();

        createStream(new MediaStreamTrack[]{track}, (streamId, tracksInfo) -> {
            WritableMap data = Arguments.createMap();

            data.putString("streamId", streamId);
            data.putMap("track", tracksInfo.get(0));

            displayMediaPromise.resolve(data);

            // Cleanup
            mediaProjectionPermissionResultData = null;
            displayMediaPromise = null;
        });
    }

    private void createStream(MediaStreamTrack[] tracks, BiConsumer<String, ArrayList<WritableMap>> successCallback) {
        String streamId = UUID.randomUUID().toString();
        MediaStream mediaStream = webRTCModule.mFactory.createLocalMediaStream(streamId);

        ArrayList<WritableMap> tracksInfo = new ArrayList<>();

        for (MediaStreamTrack track : tracks) {
            if (track == null) {
                continue;
            }

            if (track instanceof AudioTrack) {
                mediaStream.addTrack((AudioTrack) track);
            } else {
                mediaStream.addTrack((VideoTrack) track);
            }

            WritableMap trackInfo = Arguments.createMap();
            String trackId = track.id();

            trackInfo.putBoolean("enabled", track.enabled());
            trackInfo.putString("id", trackId);
            trackInfo.putString("kind", track.kind());
            trackInfo.putString("label", trackId);
            trackInfo.putString("readyState", track.state().toString());
            trackInfo.putBoolean("remote", false);
            tracksInfo.add(trackInfo);
        }

        Log.d(TAG, "MediaStream id: " + streamId);
        webRTCModule.localStreams.put(streamId, mediaStream);

        successCallback.accept(streamId, tracksInfo);
    }

    private VideoTrack createScreenTrack() {
        DisplayMetrics displayMetrics = getDisplayMetrics();
        int width = displayMetrics.widthPixels;
        int height = displayMetrics.heightPixels;
        int fps = 30;
        ScreenCaptureController screenCaptureController = new ScreenCaptureController(width, height, fps, mediaProjectionPermissionResultData);
        VideoTrack track = createVideoTrack(screenCaptureController);

        return track;
    }

    private VideoTrack createVideoTrack(AbstractVideoCaptureController videoCaptureController) {
        videoCaptureController.initializeVideoCapturer();

        VideoCapturer videoCapturer = videoCaptureController.videoCapturer;
        if (videoCapturer == null) {
            return null;
        }

        PeerConnectionFactory pcFactory = webRTCModule.mFactory;
        EglBase.Context eglContext = EglUtils.getRootEglBaseContext();
        SurfaceTextureHelper surfaceTextureHelper =
            SurfaceTextureHelper.create("CaptureThread", eglContext);

        if (surfaceTextureHelper == null) {
            Log.d(TAG, "Error creating SurfaceTextureHelper");
            return null;
        }

        VideoSource videoSource = pcFactory.createVideoSource(videoCapturer.isScreencast());
        videoCapturer.initialize(surfaceTextureHelper, reactContext, videoSource.getCapturerObserver());
        videoCaptureController.setCapturerObserver(videoSource.getCapturerObserver());

        String id = UUID.randomUUID().toString();
        VideoTrack track = pcFactory.createVideoTrack(id, videoSource);

        track.setEnabled(true);
        tracks.put(id, new TrackPrivate(track, videoSource, videoCaptureController));

        videoCaptureController.startCapture();

        return track;
    }

    private DisplayMetrics getDisplayMetrics() {
        Activity currentActivity = this.reactContext.getCurrentActivity();
        DisplayMetrics displayMetrics = new DisplayMetrics();
        WindowManager windowManager =
            (WindowManager) currentActivity.getApplication().getSystemService(Context.WINDOW_SERVICE);
        windowManager.getDefaultDisplay().getRealMetrics(displayMetrics);
        return displayMetrics;
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
        if (vc instanceof AtheerVideoCapturer) {
            AtheerVideoCapturer atheerVideoCapturer = (AtheerVideoCapturer) vc;
            VideoFrame vf = atheerVideoCapturer.getAtheerVideoFrame();
            try {
                String path = saveAtheerVideoFrame(vf, maxSize, maxJpegQuality);
                if (path != null) {
                    successCallback.invoke(path);
                } else {
                    String message = "AR Error saving picture";
                    errorCallback.invoke(message);
                }
            } catch (Exception e) {
                Log.e(TAG, "AtheerVideoCapturer: Exception", e);
            }

        } else if ( !(vc instanceof CameraCapturer) ) {
            errorCallback.invoke("Wrong class in package");
        } else {
            CameraCapturer camCap = (CameraCapturer) vc;
            camCap.takeSnapshot(new CameraCapturer.SingleCaptureCallBack() {
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
        public final AbstractVideoCaptureController videoCaptureController;

        /**
         * Whether this object has been disposed or not.
         */
        private boolean disposed;

        /**
         * Initializes a new {@code TrackPrivate} instance.
         *
         * @param track
         * @param mediaSource            the {@code MediaSource} from which the specified
         *                               {@code code} was created
         * @param videoCaptureController the {@code AbstractVideoCaptureController} from which the
         *                               specified {@code mediaSource} was created if the specified
         *                               {@code track} is a {@link VideoTrack}
         */
        public TrackPrivate(
            MediaStreamTrack track,
            MediaSource mediaSource,
            AbstractVideoCaptureController videoCaptureController) {
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

    private interface BiConsumer<T, U> {
        void accept(T t, U u);
    }

    // Capturing the image from AR video frame/buffer
    private String saveAtheerVideoFrame(VideoFrame vf, int maxSize, double jpegQuality) {
        VideoFrame.Buffer vbr = vf.getBuffer();
        int width = vbr.getWidth();
        int height = vbr.getHeight();
        byte[] nv21Data = createNV21Data(vbr.toI420());
        YuvImage yuvImage = new YuvImage(nv21Data, ImageFormat.NV21, width, height, null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, width, height), 100, out);
        byte[] imageBytes = out.toByteArray();
        String filename = UUID.randomUUID().toString();

        File file = getTempMediaFile(filename);
        try {
            writePictureToFile(imageBytes, file, maxSize, jpegQuality, vf.getRotation());
        } catch (Exception e) {
            Log.e(TAG, "saveAtheerVideoFrame Exception:" + e.getMessage());
        }
        if (file != null && file.exists() && file.length() != 0) {
            return file.getAbsolutePath();
        }
        return null;
    }

    public static byte[] createNV21Data(VideoFrame.I420Buffer i420Buffer) {
        final int width = i420Buffer.getWidth();
        final int height = i420Buffer.getHeight();
        final int chromaStride = width;
        final int chromaWidth = (width + 1) / 2;
        final int chromaHeight = (height + 1) / 2;
        final int ySize = width * height;
        final ByteBuffer nv21Buffer = ByteBuffer.allocateDirect(ySize + chromaStride * chromaHeight);

        final byte[] nv21Data = nv21Buffer.array();
        for (int y = 0; y < height; ++y) {
            for (int x = 0; x < width; ++x) {
                final byte yValue = i420Buffer.getDataY().get(y * i420Buffer.getStrideY() + x);
                nv21Data[y * width + x] = yValue;
            }
        }
        for (int y = 0; y < chromaHeight; ++y) {
            for (int x = 0; x < chromaWidth; ++x) {
                final byte uValue = i420Buffer.getDataU().get(y * i420Buffer.getStrideU() + x);
                final byte vValue = i420Buffer.getDataV().get(y * i420Buffer.getStrideV() + x);
                nv21Data[ySize + y * chromaStride + 2 * x + 0] = vValue;
                nv21Data[ySize + y * chromaStride + 2 * x + 1] = uValue;
            }
        }
        return nv21Data;
    }
}
