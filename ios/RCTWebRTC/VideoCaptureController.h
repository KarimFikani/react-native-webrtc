
#import <Foundation/Foundation.h>
#import <WebRTC/RTCCameraVideoCapturer.h>

#import <WebRTC/RTCAtheerVideoCapturer.h>

@interface VideoCaptureController : NSObject

-(instancetype)initWithCapturer:(RTCCameraVideoCapturer *)capturer
                 andConstraints:(NSDictionary *)constraints;
-(void)startCapture;
-(void)stopCapture;
-(void)switchCamera;
-(void)setAtheerCapturer:(RTCCameraVideoCapturer *)atheerCapturer;
-(void)switchAtheerBuffer;

@end
