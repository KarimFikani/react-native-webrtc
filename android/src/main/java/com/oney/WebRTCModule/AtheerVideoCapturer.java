/*
 *  Copyright 2016 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package com.oney.WebRTCModule;

import org.webrtc.*;

import android.content.Context;
import android.os.SystemClock;

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

public class AtheerVideoCapturer implements VideoCapturer {

  private CapturerObserver capturerObserver;
  private final Timer timer = new Timer();

  private final TimerTask tickTask = new TimerTask() {
    @Override
    public void run() {
      tick();
    }
  };

  public AtheerVideoCapturer() throws IOException {
  }

  public void tick() {
    capturerObserver.onFrameCaptured(getNextAtheerFrame());
  }

  private VideoFrame getNextAtheerFrame() {
    final long captureTimeNs = TimeUnit.MILLISECONDS.toNanos(SystemClock.elapsedRealtime());
    VideoFrame.Buffer atheerBuffer;

    if (AtheerBuffer.getReadBuffer() == 0) {
      atheerBuffer= new NV21Buffer(AtheerBuffer.buffer0, AtheerBuffer.getWidth(), AtheerBuffer.getHeight(), () -> {
      });
    } else {
      atheerBuffer= new NV21Buffer(AtheerBuffer.buffer1, AtheerBuffer.getWidth(), AtheerBuffer.getHeight(), () -> {
      });
    }

    return new VideoFrame(atheerBuffer, 0 /* rotation */, captureTimeNs);
  }

  @Override
  public void initialize(SurfaceTextureHelper surfaceTextureHelper, Context applicationContext,
      CapturerObserver capturerObserver) {
    this.capturerObserver = capturerObserver;
  }

  @Override
  public void startCapture(int width, int height, int framerate) {
    timer.schedule(tickTask, 0, 1000 / framerate);
  }

  @Override
  public void stopCapture() throws InterruptedException {
    timer.cancel();
  }

  @Override
  public void changeCaptureFormat(int width, int height, int framerate) {
    // Empty on purpose
  }

  @Override
  public void dispose() {
  }

  @Override
  public boolean isScreencast() {
    return false;
  }
}
