
#import <Foundation/Foundation.h>
#import <WebRTC/RTCCameraVideoCapturer.h>

#import <WebRTC/RTCAtheerVideoCapturer.h>

@interface VideoCaptureController : NSObject

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
