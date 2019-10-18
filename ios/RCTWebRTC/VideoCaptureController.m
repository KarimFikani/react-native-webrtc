
#import "VideoCaptureController.h"

static int DEFAULT_WIDTH  = 1280;
static int DEFAULT_HEIGHT = 720;
static int DEFAULT_FPS    = 30;


@implementation VideoCaptureController {
    RTCCameraVideoCapturer *_capturer;
    NSString *_sourceId;
    BOOL _usingFrontCamera;
    int _width;
    int _height;
    int _fps;
}

-(instancetype)initWithCapturer:(RTCCameraVideoCapturer *)capturer
                 andConstraints:(NSDictionary *)constraints {
    self = [super init];
    if (self) {
        _capturer = capturer;

        // Default to the front camera.
        _usingFrontCamera = YES;
        _width = DEFAULT_WIDTH;
        _height = DEFAULT_HEIGHT;
        _fps = DEFAULT_FPS;

        NSLog(@"[VideoCaptureController] Sanjay Constraints %@", constraints );

        // Check the video contraints: examine facingMode and sourceId
        // and pick a default if neither are specified.
        id facingMode = constraints[@"facingMode"];
        id optionalConstraints = constraints[@"optional"];
        id mandatoryConstraints = constraints[@"mandatory"];

        if (facingMode && [facingMode isKindOfClass:[NSString class]]) {
            AVCaptureDevicePosition position;
            if ([facingMode isEqualToString:@"environment"]) {
                position = AVCaptureDevicePositionBack;
            } else if ([facingMode isEqualToString:@"user"]) {
                position = AVCaptureDevicePositionFront;
            } else {
                // If the specified facingMode value is not supported, fall back
                // to the front camera.
                position = AVCaptureDevicePositionFront;
            }

            _usingFrontCamera = position == AVCaptureDevicePositionFront;
        } else if (optionalConstraints && [optionalConstraints isKindOfClass:[NSArray class]]) {
            NSArray *options = optionalConstraints;
            for (id item in options) {
                if ([item isKindOfClass:[NSDictionary class]]) {
                    NSString *sourceId = ((NSDictionary *)item)[@"sourceId"];
                    if (sourceId && sourceId.length > 0) {
                        _sourceId = sourceId;
                        break;
                    }
                }
            }
        }

        if (mandatoryConstraints && [mandatoryConstraints isKindOfClass:[NSDictionary class]]) {
              NSString *minWidth = ((NSDictionary *)mandatoryConstraints)[@"minWidth"];
              NSString *minHeight = ((NSDictionary *)mandatoryConstraints)[@"minHeight"];
              NSString *minFps = ((NSDictionary *)mandatoryConstraints)[@"minFrameRate"];

              NSString *maxWidth = ((NSDictionary *)mandatoryConstraints)[@"maxWidth"];
              NSString *maxHeight = ((NSDictionary *)mandatoryConstraints)[@"maxHeight"];
              NSString *maxFps = ((NSDictionary *)mandatoryConstraints)[@"maxFrameRate"];

              if (minWidth && minWidth.length > 0) {
                  _width = [minWidth intValue];;
              }

              if (maxWidth && maxWidth.length > 0) {
                  _width = [maxWidth intValue];;
              }

              if (minHeight && minHeight.length > 0) {
                  _height = [minHeight intValue];;
              }

              if (maxHeight && maxHeight.length > 0) {
                  _height = [maxHeight intValue];;
              }

              if (minFps && minFps.length > 0) {
                  _fps = [minFps intValue];;
              }

              if (maxFps && maxFps.length > 0) {
                  _fps = [maxFps intValue];;
              }

              NSLog(@"[VideoCaptureController] Constraints width %@", minWidth);
              NSLog(@"[VideoCaptureController] Constraints height  %@", minHeight);
              NSLog(@"[VideoCaptureController] Constraints fps  %@", minFps);

              NSLog(@"[VideoCaptureController] Constraints width %@", maxWidth);
              NSLog(@"[VideoCaptureController] Constraints height  %@", maxHeight);
              NSLog(@"[VideoCaptureController] Constraints fps  %@", maxFps);
        }

        NSLog(@"[VideoCaptureController] Computed Constraints width %d", _width);
        NSLog(@"[VideoCaptureController] Computed Constraints height  %d", _height);
        NSLog(@"[VideoCaptureController] Computed Constraints fps  %d", _fps);
    }

    return self;
}

-(void)startCapture {
    AVCaptureDevice *device;
    if (_sourceId) {
        device = [AVCaptureDevice deviceWithUniqueID:_sourceId];
    }
    if (!device) {
        AVCaptureDevicePosition position
            = _usingFrontCamera
                ? AVCaptureDevicePositionFront
                : AVCaptureDevicePositionBack;
        device = [self findDeviceForPosition:position];
    }

    // TODO: Extract width and height from constraints.
    AVCaptureDeviceFormat *format
        = [self selectFormatForDevice:device
                      withTargetWidth:_width
                     withTargetHeight:_height];
    if (!format) {
        NSLog(@"[VideoCaptureController] No valid formats for device %@", device);

        return;
    }

    // TODO: Extract fps from constraints.
    [_capturer startCaptureWithDevice:device format:format fps:_fps];

    NSLog(@"[VideoCaptureController] Capture started");
}

-(void)stopCapture {
    [_capturer stopCapture];

    NSLog(@"[VideoCaptureController] Capture stopped");
}

-(void)switchCamera {
    _usingFrontCamera = !_usingFrontCamera;

    [self startCapture];
}

#pragma mark Private

- (AVCaptureDevice *)findDeviceForPosition:(AVCaptureDevicePosition)position {
    NSArray<AVCaptureDevice *> *captureDevices = [RTCCameraVideoCapturer captureDevices];
    for (AVCaptureDevice *device in captureDevices) {
        if (device.position == position) {
            return device;
        }
    }

    return captureDevices[0];
}

- (AVCaptureDeviceFormat *)selectFormatForDevice:(AVCaptureDevice *)device
                                 withTargetWidth:(int)targetWidth
                                withTargetHeight:(int)targetHeight {
    NSArray<AVCaptureDeviceFormat *> *formats =
    [RTCCameraVideoCapturer supportedFormatsForDevice:device];
    AVCaptureDeviceFormat *selectedFormat = nil;
    int currentDiff = INT_MAX;

    for (AVCaptureDeviceFormat *format in formats) {
        CMVideoDimensions dimension = CMVideoFormatDescriptionGetDimensions(format.formatDescription);
        FourCharCode pixelFormat = CMFormatDescriptionGetMediaSubType(format.formatDescription);
        int diff = abs(targetWidth - dimension.width) + abs(targetHeight - dimension.height);
        if (diff < currentDiff) {
            selectedFormat = format;
            currentDiff = diff;
        } else if (diff == currentDiff && pixelFormat == [_capturer preferredOutputPixelFormat]) {
            selectedFormat = format;
        }
    }

    return selectedFormat;
}

@end
