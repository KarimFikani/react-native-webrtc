
#import <Foundation/Foundation.h>
#import <WebRTC/RTCCameraVideoCapturer.h>

#import <WebRTC/RTCAtheerVideoCapturer.h>

@interface VideoCaptureController : NSObject

-(instancetype)initWithCapturer:(RTCCameraVideoCapturer *)capturer
                withAtheerCapturer: (RTCAtheerVideoCapturer *)atheerCapturer
                 andConstraints:(NSDictionary *)constraints;
-(instancetype)initWithAtheerCapturer:(RTCAtheerVideoCapturer *)atheerCapturer
andConstraints:(NSDictionary *)constraints;
-(void)startCapture;
-(void)stopCapture;
-(void)switchCamera;
-(void)switchAtheerBuffer;

@end
