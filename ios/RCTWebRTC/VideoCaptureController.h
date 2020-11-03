
#import <Foundation/Foundation.h>
#import <WebRTC/RTCCameraVideoCapturer.h>
#import "CaptureController.h"
#import <WebRTC/RTCAtheerVideoCapturer.h>

@interface VideoCaptureController : CaptureController

@property (nonatomic, readonly, copy) AVCaptureDeviceFormat *selectedFormat;
@property (nonatomic, readonly) int frameRate;

-(instancetype)initWithCapturer:(RTCCameraVideoCapturer *)capturer
                 andConstraints:(NSDictionary *)constraints;
-(void)startCapture;
-(void)stopCapture;
-(void)switchCamera;
-(void)setAtheerCapturer:(RTCCameraVideoCapturer *)atheerCapturer;
-(void)switchAtheerBuffer;

@end
