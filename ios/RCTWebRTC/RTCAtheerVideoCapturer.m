#import <WebRTC/WebRTC.h>
#import <React/RCTLog.h>

NSString *const kRTCFileVideoCapturerErrorDomain = @"org.webrtc.RTCFileVideoCapturer";

typedef NS_ENUM(NSInteger, RTCFileVideoCapturerErrorCode) {
    RTCFileVideoCapturerErrorCode_CapturerRunning = 2000,
    RTCFileVideoCapturerErrorCode_FileNotFound
};

typedef NS_ENUM(NSInteger, RTCAtheerVideoCapturerStatus) {
    RTCAtheerVideoCapturerStatusNotInitialized,
    RTCAtheerVideoCapturerStatusStarted,
    RTCAtheerVideoCapturerStatusStopped
};

@implementation RTCAtheerVideoCapturer {
    CMTime _lastPresentationTime;
    dispatch_queue_t _frameQueue;
    RTCAtheerVideoCapturerStatus _status;
}

- (void)startCapturingFromAtheerBuffer {
    RCTLogWarn(@"hao check atheer capturer started reading");
    if (_status == RTCAtheerVideoCapturerStatusStarted) {
        RCTLogWarn(@"hao check atheer video capturer already started");
        return;
    }
    _status = RTCAtheerVideoCapturerStatusStarted;
    [self readNextBuffer];
}

- (void)stopCapture {
    RTCLog(@"hao check atheer capturer stopped.");
    _status = RTCAtheerVideoCapturerStatusStopped;
}

#pragma mark - Private

- (dispatch_queue_t)frameQueue {
  if (!_frameQueue) {
    _frameQueue = dispatch_queue_create("org.webrtc.filecapturer.video", DISPATCH_QUEUE_SERIAL);
    dispatch_set_target_queue(_frameQueue,
                              dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_BACKGROUND, 0));
  }
  return _frameQueue;
}

- (void)readNextBuffer {
    if (_status == RTCAtheerVideoCapturerStatusStopped) {
      return;
    }

    [self publishImageBuffer];

}

- (void)publishImageBuffer {
  Float64 presentationDifference = 0.02;
  int64_t presentationDifferenceRound = lroundf(presentationDifference * NSEC_PER_SEC);

  __block dispatch_source_t timer = [self createStrictTimer];
  // Strict timer that will fire |presentationDifferenceRound| ns from now and never again.
  dispatch_source_set_timer(timer,
                            dispatch_time(DISPATCH_TIME_NOW, presentationDifferenceRound),
                            DISPATCH_TIME_FOREVER,
                            0);
  dispatch_source_set_event_handler(timer, ^{
    dispatch_source_cancel(timer);
    timer = nil;

    CVPixelBufferRef pixelBuffer = [RTCAtheerBuffer getReadBuffer];

    if (pixelBuffer && pixelBuffer != nil) {
        CVPixelBufferLockBaseAddress(pixelBuffer, 0);
        RTCCVPixelBuffer *rtcPixelBuffer = [[RTCCVPixelBuffer alloc] initWithPixelBuffer:pixelBuffer];
        NSTimeInterval timeStampSeconds = CACurrentMediaTime();
        int64_t timeStampNs = lroundf(timeStampSeconds * NSEC_PER_SEC);
        RTCVideoFrame *videoFrame =
            [[RTCVideoFrame alloc] initWithBuffer:rtcPixelBuffer rotation:0 timeStampNs:timeStampNs];
        CVPixelBufferUnlockBaseAddress(pixelBuffer, 0);

        dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT, 0), ^{
          [self readNextBuffer];
        });

        [self.delegate capturer:self didCaptureVideoFrame:videoFrame];
        pixelBuffer = NULL;
    } else {
        dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT, 0), ^{
          [self readNextBuffer];
        });
    }


  });
  dispatch_activate(timer);
}

- (dispatch_source_t)createStrictTimer {
  dispatch_source_t timer = dispatch_source_create(
      DISPATCH_SOURCE_TYPE_TIMER, 0, DISPATCH_TIMER_STRICT, [self frameQueue]);
  return timer;
}

- (void)dealloc {
  [self stopCapture];
}

@end
