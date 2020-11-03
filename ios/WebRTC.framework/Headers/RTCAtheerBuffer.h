#import <Foundation/Foundation.h>

@interface RTCAtheerBuffer : NSObject

+ (int)getWriteBufferId;
+ (int)getReadBufferId;
+ (CVPixelBufferRef)getWriteBuffer;
+ (CVPixelBufferRef)getReadBuffer;
+ (void)commitWriteBuffer;
+ (void)writeBuffer: (CGImageRef) image;

// force write to read buffer (single buffer mode)
+ (void)writeReadBufferFromBuffer: (CVPixelBufferRef) pixelBuffer;
+ (void)safeWriteReadBufferFromBuffer: (CVPixelBufferRef) pixelBuffer;
+ (void)haoTest2: (CGImageRef) image;

// Test Functions
+ (void)writeBufferFromBuffer: (CVPixelBufferRef) pixelBuffer;
+ (void)writeBufferFromImage: (CGImageRef) image;
+ (void)testFromBufferYUV: (CVPixelBufferRef *) pixelBuffer;
+ (void)deepCopyBufferFromBufferYUV: (CVPixelBufferRef) pixelBuffer;
+ (void)deepCopyBufferFromBufferRGB: (CVPixelBufferRef) pixelBuffer;
+ (void)deepCopyBufferPointerFromBufferYUV: (uint8_t *) yPlane : (uint8_t *) uvPlane : (int) bufferWidth : (int) bufferHeight;
+ (void)deepCopyBufferPointerFromBufferRGB: (uint8_t *) plane : (int) bufferWidth : (int) bufferHeight : (size_t) bytesPerRow;
+ (void)test;
+ (void)verifyTest;
+ (void)haotest;
+ (void)setTestPixelBufferFromImage: (CGImageRef) image;
+ (CVPixelBufferRef)getTestPixelBuffer;

@end
