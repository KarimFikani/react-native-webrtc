#import "RTCAtheerBuffer.h"

@implementation RTCAtheerBuffer

static CVPixelBufferRef pixelBuffer0 = nil;
static CVPixelBufferRef pixelBuffer1 = nil;

static int writeBuffer = 0;
static int readBuffer = 1;

static int testValue = 1;

static size_t imageHeight0 = 0;
static size_t imageWidth0 = 0;
static size_t imageHeight1 = 0;
static size_t imageWidth1 = 0;

+ (int)getWriteBufferId {
    return writeBuffer;
}

+ (int)getReadBufferId {
    return readBuffer;
}

+ (CVPixelBufferRef)getWriteBuffer {
    //NSLog(@"hao check get write buffer AA");
    if (writeBuffer == 0) {
        return pixelBuffer0;
    } else {
        return pixelBuffer1;
    }
}

+ (CVPixelBufferRef)getReadBuffer{
    if (readBuffer == 0) {
        //NSLog(@"hao check buffre: get read buffer 0 %p", &pixelBuffer0);
        return pixelBuffer0;
    } else {
        //NSLog(@"hao check buffre: get read buffer 1 %p", &pixelBuffer1);
        return pixelBuffer1;
    }
}

+ (void)commitWriteBuffer {
    if (writeBuffer == 0) {
        writeBuffer = 1;
        readBuffer = 0;
    } else {
        writeBuffer = 0;
        readBuffer = 1;
    }
}

+ (void)writeBuffer: (CGImageRef) image {
    NSDictionary *options = @{
                              (NSString*)kCVPixelBufferCGImageCompatibilityKey : @YES,
                              (NSString*)kCVPixelBufferCGBitmapContextCompatibilityKey : @YES,
                              };

    if (writeBuffer == 0) {
        if (CGImageGetWidth(image) != imageWidth0 || CGImageGetHeight(image) != imageHeight0) {
            pixelBuffer0 = NULL;
            CVReturn status = CVPixelBufferCreate(kCFAllocatorDefault, CGImageGetWidth(image),
                                CGImageGetHeight(image), kCVPixelFormatType_32ARGB, (__bridge CFDictionaryRef) options,
                                &pixelBuffer0);
            if (status!=kCVReturnSuccess) {
                NSLog(@"Operation failed");
            }
            imageWidth0 = CGImageGetWidth(image);
            imageHeight0 = CGImageGetHeight(image);
            
            NSParameterAssert(status == kCVReturnSuccess && pixelBuffer0 != NULL);
        }

        CVPixelBufferLockBaseAddress(pixelBuffer0, 0);
        void *pxdata = CVPixelBufferGetBaseAddress(pixelBuffer0);
        size_t bytesPerRow = CVPixelBufferGetBytesPerRow(pixelBuffer0);

        CGColorSpaceRef rgbColorSpace = CGColorSpaceCreateDeviceRGB();
        CGContextRef context = CGBitmapContextCreate(pxdata, imageWidth0,
                                                     imageHeight0, 8, bytesPerRow, rgbColorSpace,
                                                     kCGImageAlphaNoneSkipFirst);
        NSParameterAssert(context);

        CGContextDrawImage(context, CGRectMake(0, 0, imageWidth0, imageHeight0), image);
        CGColorSpaceRelease(rgbColorSpace);
        CGContextRelease(context);

        CVPixelBufferUnlockBaseAddress(pixelBuffer0, 0);
    } else {
        if (CGImageGetWidth(image) != imageWidth1 || CGImageGetHeight(image) != imageHeight1) {
            pixelBuffer1 = NULL;
            CVReturn status = CVPixelBufferCreate(kCFAllocatorDefault, CGImageGetWidth(image),
                                CGImageGetHeight(image), kCVPixelFormatType_32ARGB, (__bridge CFDictionaryRef) options,
                                &pixelBuffer1);
            if (status!=kCVReturnSuccess) {
                NSLog(@"Operation failed");
            }
            imageWidth1 = CGImageGetWidth(image);
            imageHeight1 = CGImageGetHeight(image);
            
            NSParameterAssert(status == kCVReturnSuccess && pixelBuffer1 != NULL);
        }

        CVPixelBufferLockBaseAddress(pixelBuffer1, 0);
        void *pxdata = CVPixelBufferGetBaseAddress(pixelBuffer1);
        size_t bytesPerRow = CVPixelBufferGetBytesPerRow(pixelBuffer1);

        CGColorSpaceRef rgbColorSpace = CGColorSpaceCreateDeviceRGB();
        CGContextRef context = CGBitmapContextCreate(pxdata, imageWidth1,
                                                     imageHeight1, 8, bytesPerRow, rgbColorSpace,
                                                     kCGImageAlphaNoneSkipFirst);
        NSParameterAssert(context);

        CGContextDrawImage(context, CGRectMake(0, 0, imageWidth1, imageHeight1), image);
        CGColorSpaceRelease(rgbColorSpace);
        CGContextRelease(context);

        CVPixelBufferUnlockBaseAddress(pixelBuffer1, 0);
    }
}

+ (void)writeBufferFromBuffer: (CVPixelBufferRef) pixelBuffer {
    if (writeBuffer == 0) {
        pixelBuffer0 = NULL;
        pixelBuffer0 = pixelBuffer;
    } else {
        pixelBuffer1 = NULL;
        pixelBuffer1 = pixelBuffer;
    }
}

+ (void)testFromBufferYUV: (CVPixelBufferRef *) pixelBuffer {
    if (readBuffer == 0) {
        CVPixelBufferLockBaseAddress(*pixelBuffer, 0);
        int bufferWidth = (int)CVPixelBufferGetWidth(*pixelBuffer);
        int bufferHeight = (int)CVPixelBufferGetHeight(*pixelBuffer);
        
        pixelBuffer0 = NULL;
        CVReturn status = CVPixelBufferCreate(kCFAllocatorDefault, bufferWidth,
        bufferHeight, CVPixelBufferGetPixelFormatType(*pixelBuffer), NULL,
        &pixelBuffer0);
        if (status!=kCVReturnSuccess) {
            NSLog(@"Operation failed");
        }
        CVPixelBufferLockBaseAddress(pixelBuffer0, 0);
        uint8_t *yDestPlane = CVPixelBufferGetBaseAddressOfPlane(pixelBuffer0, 0);
        //YUV
        uint8_t *yPlane = CVPixelBufferGetBaseAddressOfPlane(*pixelBuffer, 0);
        memcpy(yDestPlane, yPlane, bufferWidth * bufferHeight);
        uint8_t *uvDestPlane = CVPixelBufferGetBaseAddressOfPlane(pixelBuffer0, 1);
        uint8_t *uvPlane = CVPixelBufferGetBaseAddressOfPlane(*pixelBuffer, 1);
        memcpy(uvDestPlane, uvPlane, bufferWidth * bufferHeight/2);
        
        CVPixelBufferUnlockBaseAddress(*pixelBuffer, 0);
        CVPixelBufferUnlockBaseAddress(pixelBuffer0, 0);
    } else {
        CVPixelBufferLockBaseAddress(*pixelBuffer, 0);
        int bufferWidth = (int)CVPixelBufferGetWidth(*pixelBuffer);
        int bufferHeight = (int)CVPixelBufferGetHeight(*pixelBuffer);
        
        pixelBuffer1 = NULL;
        CVReturn status = CVPixelBufferCreate(kCFAllocatorDefault, bufferWidth,
        bufferHeight, CVPixelBufferGetPixelFormatType(*pixelBuffer), NULL,
        &pixelBuffer1);
        if (status!=kCVReturnSuccess) {
            NSLog(@"Operation failed");
        }
        CVPixelBufferLockBaseAddress(pixelBuffer1, 0);
        uint8_t *yDestPlane = CVPixelBufferGetBaseAddressOfPlane(pixelBuffer1, 0);
        //YUV
        uint8_t *yPlane = CVPixelBufferGetBaseAddressOfPlane(*pixelBuffer, 0);
        memcpy(yDestPlane, yPlane, bufferWidth * bufferHeight);
        uint8_t *uvDestPlane = CVPixelBufferGetBaseAddressOfPlane(pixelBuffer1, 1);
        uint8_t *uvPlane = CVPixelBufferGetBaseAddressOfPlane(*pixelBuffer, 1);
        memcpy(uvDestPlane, uvPlane, bufferWidth * bufferHeight/2);
        
        CVPixelBufferUnlockBaseAddress(*pixelBuffer, 0);
        CVPixelBufferUnlockBaseAddress(pixelBuffer1, 0);
    }
    
    // release the reference to original pixelbuffer
    pixelBuffer = NULL;
}

+ (void)deepCopyBufferPointerFromBufferRGB: (uint8_t *) plane : (int) bufferWidth : (int) bufferHeight : (size_t) bytesPerRow {
    NSDictionary *options = @{
                                     (NSString*)kCVPixelBufferCGImageCompatibilityKey : @YES,
                                     (NSString*)kCVPixelBufferCGBitmapContextCompatibilityKey : @YES,
                                     };
    if (readBuffer == 0) {
        pixelBuffer0 = NULL;
        CVReturn status = CVPixelBufferCreate(kCFAllocatorDefault, bufferWidth,
        bufferHeight, kCVPixelFormatType_32ARGB, (__bridge CFDictionaryRef) options,
        &pixelBuffer0);
        if (status!=kCVReturnSuccess) {
            NSLog(@"Operation failed");
        }
        CVPixelBufferLockBaseAddress(pixelBuffer0, 0);
        void *pxdata = CVPixelBufferGetBaseAddress(pixelBuffer0);
        memcpy(pxdata, plane, bufferHeight * bytesPerRow);

        CVPixelBufferUnlockBaseAddress(pixelBuffer0, 0);
    } else {
        pixelBuffer1 = NULL;
        CVReturn status = CVPixelBufferCreate(kCFAllocatorDefault, bufferWidth,
        bufferHeight, kCVPixelFormatType_32ARGB, (__bridge CFDictionaryRef) options,
        &pixelBuffer1);
        if (status!=kCVReturnSuccess) {
            NSLog(@"Operation failed");
        }
        CVPixelBufferLockBaseAddress(pixelBuffer1, 0);
        void *pxdata = CVPixelBufferGetBaseAddress(pixelBuffer1);
        memcpy(pxdata, plane, bufferHeight * bytesPerRow);

        CVPixelBufferUnlockBaseAddress(pixelBuffer0, 0);
    }
}

+ (void)deepCopyBufferPointerFromBufferYUV: (uint8_t *) yPlane :
                                            (uint8_t *) uvPlane :
                                            (int) bufferWidth :
                                            (int) bufferHeight {
    if (readBuffer == 0) {
        pixelBuffer0 = NULL;
        CVReturn status = CVPixelBufferCreate(kCFAllocatorDefault, bufferWidth,
        bufferHeight, kCVPixelFormatType_32ARGB, NULL,
        &pixelBuffer0);
        if (status!=kCVReturnSuccess) {
            NSLog(@"Operation failed");
        }
        CVPixelBufferLockBaseAddress(pixelBuffer0, 0);
        uint8_t *yDestPlane = CVPixelBufferGetBaseAddressOfPlane(pixelBuffer0, 0);
        //YUV
        memcpy(yDestPlane, yPlane, bufferWidth * bufferHeight);
        uint8_t *uvDestPlane = CVPixelBufferGetBaseAddressOfPlane(pixelBuffer0, 1);
        memcpy(uvDestPlane, uvPlane, bufferWidth * bufferHeight/2);

        CVPixelBufferUnlockBaseAddress(pixelBuffer0, 0);
    } else {
        pixelBuffer1 = NULL;
        CVReturn status = CVPixelBufferCreate(kCFAllocatorDefault, bufferWidth,
        bufferHeight, kCVPixelFormatType_32ARGB, NULL,
        &pixelBuffer1);
        if (status!=kCVReturnSuccess) {
            NSLog(@"Operation failed");
        }
        CVPixelBufferLockBaseAddress(pixelBuffer1, 0);
        uint8_t *yDestPlane = CVPixelBufferGetBaseAddressOfPlane(pixelBuffer1, 0);
        //YUV
        memcpy(yDestPlane, yPlane, bufferWidth * bufferHeight);
        uint8_t *uvDestPlane = CVPixelBufferGetBaseAddressOfPlane(pixelBuffer1, 1);
        memcpy(uvDestPlane, uvPlane, bufferWidth * bufferHeight/2);

        CVPixelBufferUnlockBaseAddress(pixelBuffer1, 0);
    }
}

+ (void)deepCopyBufferFromBufferYUV: (CVPixelBufferRef) pixelBuffer {
    if (readBuffer == 0) {
        CVPixelBufferLockBaseAddress(pixelBuffer, 0);
        int bufferWidth = (int)CVPixelBufferGetWidth(pixelBuffer);
        int bufferHeight = (int)CVPixelBufferGetHeight(pixelBuffer);
        
        pixelBuffer0 = NULL;
        CVReturn status = CVPixelBufferCreate(kCFAllocatorDefault, bufferWidth,
        bufferHeight, CVPixelBufferGetPixelFormatType(pixelBuffer), NULL,
        &pixelBuffer0);
        if (status!=kCVReturnSuccess) {
            NSLog(@"Operation failed");
        }
        CVPixelBufferLockBaseAddress(pixelBuffer0, 0);
        uint8_t *yDestPlane = CVPixelBufferGetBaseAddressOfPlane(pixelBuffer0, 0);
        //YUV
        uint8_t *yPlane = CVPixelBufferGetBaseAddressOfPlane(pixelBuffer, 0);
        memcpy(yDestPlane, yPlane, bufferWidth * bufferHeight);
        uint8_t *uvDestPlane = CVPixelBufferGetBaseAddressOfPlane(pixelBuffer0, 1);
        uint8_t *uvPlane = CVPixelBufferGetBaseAddressOfPlane(pixelBuffer, 1);
        memcpy(uvDestPlane, uvPlane, bufferWidth * bufferHeight/2);
        
        CVPixelBufferUnlockBaseAddress(pixelBuffer, 0);
        CVPixelBufferUnlockBaseAddress(pixelBuffer0, 0);
    } else {
        CVPixelBufferLockBaseAddress(pixelBuffer, 0);
        int bufferWidth = (int)CVPixelBufferGetWidth(pixelBuffer);
        int bufferHeight = (int)CVPixelBufferGetHeight(pixelBuffer);
        
        pixelBuffer1 = NULL;
        CVReturn status = CVPixelBufferCreate(kCFAllocatorDefault, bufferWidth,
        bufferHeight, CVPixelBufferGetPixelFormatType(pixelBuffer), NULL,
        &pixelBuffer1);
        if (status!=kCVReturnSuccess) {
            NSLog(@"Operation failed");
        }
        CVPixelBufferLockBaseAddress(pixelBuffer1, 0);
        uint8_t *yDestPlane = CVPixelBufferGetBaseAddressOfPlane(pixelBuffer1, 0);
        //YUV
        uint8_t *yPlane = CVPixelBufferGetBaseAddressOfPlane(pixelBuffer, 0);
        memcpy(yDestPlane, yPlane, bufferWidth * bufferHeight);
        uint8_t *uvDestPlane = CVPixelBufferGetBaseAddressOfPlane(pixelBuffer1, 1);
        uint8_t *uvPlane = CVPixelBufferGetBaseAddressOfPlane(pixelBuffer, 1);
        memcpy(uvDestPlane, uvPlane, bufferWidth * bufferHeight/2);
        
        CVPixelBufferUnlockBaseAddress(pixelBuffer, 0);
        CVPixelBufferUnlockBaseAddress(pixelBuffer1, 0);
    }
    
    // release the reference to original pixelbuffer
    pixelBuffer = NULL;
}

+ (void)deepCopyBufferFromBufferRGB: (CVPixelBufferRef) pixelBuffer {
    NSDictionary *options = @{
    (NSString*)kCVPixelBufferCGImageCompatibilityKey : @YES,
    (NSString*)kCVPixelBufferCGBitmapContextCompatibilityKey : @YES,
    };
    if (readBuffer == 0) {
        CVPixelBufferLockBaseAddress(pixelBuffer, 0);
        int bufferWidth = (int)CVPixelBufferGetWidth(pixelBuffer);
        int bufferHeight = (int)CVPixelBufferGetHeight(pixelBuffer);
        size_t bytesPerRow = CVPixelBufferGetBytesPerRow(pixelBuffer);
        uint8_t *baseAddress = CVPixelBufferGetBaseAddress(pixelBuffer);
        
        pixelBuffer0 = NULL;
        CVReturn status = CVPixelBufferCreate(kCFAllocatorDefault, bufferWidth,
        bufferHeight, kCVPixelFormatType_32ARGB, (__bridge CFDictionaryRef) options,
        &pixelBuffer0);
        if (status!=kCVReturnSuccess) {
            NSLog(@"Operation failed");
        }
        CVPixelBufferLockBaseAddress(pixelBuffer0, 0);
        void *pxdata = CVPixelBufferGetBaseAddress(pixelBuffer0);
        memcpy(pxdata, baseAddress, bufferHeight * bytesPerRow);
        
        CVPixelBufferUnlockBaseAddress(pixelBuffer, 0);
        CVPixelBufferUnlockBaseAddress(pixelBuffer0, 0);
    } else {
        CVPixelBufferLockBaseAddress(pixelBuffer, 0);
        int bufferWidth = (int)CVPixelBufferGetWidth(pixelBuffer);
        int bufferHeight = (int)CVPixelBufferGetHeight(pixelBuffer);
        size_t bytesPerRow = CVPixelBufferGetBytesPerRow(pixelBuffer);
        uint8_t *baseAddress = CVPixelBufferGetBaseAddress(pixelBuffer);
        
        pixelBuffer1 = NULL;
        CVReturn status = CVPixelBufferCreate(kCFAllocatorDefault, bufferWidth,
        bufferHeight, kCVPixelFormatType_32ARGB, (__bridge CFDictionaryRef) options,
        &pixelBuffer1);
        if (status!=kCVReturnSuccess) {
            NSLog(@"Operation failed");
        }
        CVPixelBufferLockBaseAddress(pixelBuffer1, 0);
        void *pxdata = CVPixelBufferGetBaseAddress(pixelBuffer1);
        memcpy(pxdata, baseAddress, bufferHeight * bytesPerRow);
        
        CVPixelBufferUnlockBaseAddress(pixelBuffer, 0);
        CVPixelBufferUnlockBaseAddress(pixelBuffer1, 0);
    }
    
    // release the reference to original pixelbuffer
    pixelBuffer = NULL;
}

+ (void)writeReadBufferFromBuffer: (CVPixelBufferRef) pixelBuffer {
    if (readBuffer == 0) {
        pixelBuffer0 = NULL;
        pixelBuffer0 = pixelBuffer;
        NSLog(@"hao check buffre: setting pixelbuffer0 to %p", &pixelBuffer0);
    } else {
        pixelBuffer1 = NULL;
        pixelBuffer1 = pixelBuffer;
        NSLog(@"hao check buffre: setting pixelbuffer1 to %p", &pixelBuffer1);
    }
}

+ (void)safeWriteReadBufferFromBuffer: (CVPixelBufferRef) pixelBuffer {
    if (readBuffer == 0) {
        CVPixelBufferLockBaseAddress(pixelBuffer0, 0);
        pixelBuffer0 = NULL;
        pixelBuffer0 = pixelBuffer;
        CVPixelBufferUnlockBaseAddress(pixelBuffer0, 0);
    } else {
        CVPixelBufferLockBaseAddress(pixelBuffer1, 0);
        pixelBuffer1 = NULL;
        pixelBuffer1 = pixelBuffer;
        CVPixelBufferUnlockBaseAddress(pixelBuffer1, 0);
    }
}

+ (void)haoTest2: (CGImageRef) image {
    NSDictionary *options = @{
                              (NSString*)kCVPixelBufferCGImageCompatibilityKey : @YES,
                              (NSString*)kCVPixelBufferCGBitmapContextCompatibilityKey : @YES,
                              };

    if (CGImageGetWidth(image) != imageWidth1 || CGImageGetHeight(image) != imageHeight1) {
        pixelBuffer1 = NULL;
        CVReturn status = CVPixelBufferCreate(kCFAllocatorDefault, CGImageGetWidth(image),
                            CGImageGetHeight(image), kCVPixelFormatType_32ARGB, (__bridge CFDictionaryRef) options,
                            &pixelBuffer1);
        if (status!=kCVReturnSuccess) {
            NSLog(@"Operation failed");
        }
        imageWidth1 = CGImageGetWidth(image);
        imageHeight1 = CGImageGetHeight(image);
        
        NSParameterAssert(status == kCVReturnSuccess && pixelBuffer1 != NULL);
    }

    CVPixelBufferLockBaseAddress(pixelBuffer1, 0);
    void *pxdata = CVPixelBufferGetBaseAddress(pixelBuffer1);
    //size_t bytesPerRow = CVPixelBufferGetBytesPerRow(testPixelBuffer);

    CGColorSpaceRef rgbColorSpace = CGColorSpaceCreateDeviceRGB();
    CGContextRef context = CGBitmapContextCreate(pxdata, CGImageGetWidth(image),
                                                 CGImageGetHeight(image), 8, 4*CGImageGetWidth(image), rgbColorSpace,
                                                 kCGImageAlphaNoneSkipFirst);
    NSParameterAssert(context);

    CGContextDrawImage(context, CGRectMake(0, 0, CGImageGetWidth(image),
                                           CGImageGetHeight(image)), image);
    CGColorSpaceRelease(rgbColorSpace);
    CGContextRelease(context);

    CVPixelBufferUnlockBaseAddress(pixelBuffer1, 0);
}

+ (void)writeBufferFromImage: (CGImageRef) image {
    if (writeBuffer == 0) {
        NSDictionary *options = @{
                                  (NSString*)kCVPixelBufferCGImageCompatibilityKey : @YES,
                                  (NSString*)kCVPixelBufferCGBitmapContextCompatibilityKey : @YES,
                                  };

        pixelBuffer0 = NULL;
        CVReturn status = CVPixelBufferCreate(kCFAllocatorDefault, CGImageGetWidth(image),
                            CGImageGetHeight(image), kCVPixelFormatType_32ARGB, (__bridge CFDictionaryRef) options,
                            &pixelBuffer0);
        if (status!=kCVReturnSuccess) {
            NSLog(@"Operation failed");
        }
        NSParameterAssert(status == kCVReturnSuccess && pixelBuffer0 != NULL);

        CVPixelBufferLockBaseAddress(pixelBuffer0, 0);
        void *pxdata = CVPixelBufferGetBaseAddress(pixelBuffer0);
        //size_t bytesPerRow = CVPixelBufferGetBytesPerRow(testPixelBuffer);

        CGColorSpaceRef rgbColorSpace = CGColorSpaceCreateDeviceRGB();
        CGContextRef context = CGBitmapContextCreate(pxdata, CGImageGetWidth(image),
                                                     CGImageGetHeight(image), 8, 4*CGImageGetWidth(image), rgbColorSpace,
                                                     kCGImageAlphaNoneSkipFirst);
        NSParameterAssert(context);

        CGContextDrawImage(context, CGRectMake(0, 0, CGImageGetWidth(image),
                                               CGImageGetHeight(image)), image);
        CGColorSpaceRelease(rgbColorSpace);
        CGContextRelease(context);

        CVPixelBufferUnlockBaseAddress(pixelBuffer0, 0);
    } else {
        NSDictionary *options = @{
                                  (NSString*)kCVPixelBufferCGImageCompatibilityKey : @YES,
                                  (NSString*)kCVPixelBufferCGBitmapContextCompatibilityKey : @YES,
                                  };

        pixelBuffer1 = NULL;
        CVReturn status = CVPixelBufferCreate(kCFAllocatorDefault, CGImageGetWidth(image),
                            CGImageGetHeight(image), kCVPixelFormatType_32ARGB, (__bridge CFDictionaryRef) options,
                            &pixelBuffer1);
        if (status!=kCVReturnSuccess) {
            NSLog(@"Operation failed");
        }
        NSParameterAssert(status == kCVReturnSuccess && pixelBuffer1 != NULL);

        CVPixelBufferLockBaseAddress(pixelBuffer1, 0);
        void *pxdata = CVPixelBufferGetBaseAddress(pixelBuffer1);
        //size_t bytesPerRow = CVPixelBufferGetBytesPerRow(testPixelBuffer);

        CGColorSpaceRef rgbColorSpace = CGColorSpaceCreateDeviceRGB();
        CGContextRef context = CGBitmapContextCreate(pxdata, CGImageGetWidth(image),
                                                     CGImageGetHeight(image), 8, 4*CGImageGetWidth(image), rgbColorSpace,
                                                     kCGImageAlphaNoneSkipFirst);
        NSParameterAssert(context);

        CGContextDrawImage(context, CGRectMake(0, 0, CGImageGetWidth(image),
                                               CGImageGetHeight(image)), image);
        CGColorSpaceRelease(rgbColorSpace);
        CGContextRelease(context);

        CVPixelBufferUnlockBaseAddress(pixelBuffer1, 0);
    }
    
    //CGImageRelease(image);
}

// Below are hao's test functions

+ (CVPixelBufferRef)getTestPixelBuffer {
    return pixelBuffer0;
}

+ (void)test {
    testValue = 2;
}

+ (void)verifyTest {
    NSLog(@"hao check testvalue %d", testValue);
}

+ (void)haotest {
    NSLog(@"hao check haotest called");
    CGRect rect = CGRectMake(0, 0, 500, 500);
    UIGraphicsBeginImageContext(rect.size);
    CGContextRef context = UIGraphicsGetCurrentContext();
    CGContextSetFillColorWithColor(context, [[UIColor whiteColor] CGColor]);
    CGContextFillRect(context, rect);
    UIImage *image = UIGraphicsGetImageFromCurrentImageContext();
    UIGraphicsEndImageContext();
    
    [self setTestPixelBufferFromImage:image.CGImage];
    //[self pixelBufferFromCGImage:image.CGImage];
}

+(void)pixelBufferFromCGImage:(CGImageRef)image{
    NSDictionary *pixelAttributes = @{(id)kCVPixelBufferIOSurfacePropertiesKey : @{}};
    pixelBuffer0 = NULL;
    size_t width = CGImageGetWidth(image);
    size_t height = CGImageGetHeight(image);
    CVReturn result = CVPixelBufferCreate(kCFAllocatorDefault,
                                          width,
                                          height,
                                          kCVPixelFormatType_420YpCbCr8BiPlanarVideoRange,
                                          (__bridge CFDictionaryRef)(pixelAttributes),
                                          &pixelBuffer0);

    CVPixelBufferLockBaseAddress(pixelBuffer0, 0);
    uint8_t *yDestPlane = CVPixelBufferGetBaseAddressOfPlane(pixelBuffer0, 0);
    //memcpy(yDestPlane, yPlane, width * height);
    NSLog(@"hao check pb width %zu", width );
    NSLog(@"hao check pb height %zu", height );
    
    for (int i = 0; i < width * height; i++) {
        yDestPlane[i] = 0xff;
    }
    uint8_t *uvDestPlane = CVPixelBufferGetBaseAddressOfPlane(pixelBuffer0, 1);
    for (int i = 0; i < width * height / 2; i++) {
        uvDestPlane[i] = 0xff;
    }
    //memcpy(uvDestPlane, uvPlane, numberOfElementsForChroma);
    CVPixelBufferUnlockBaseAddress(pixelBuffer0, 0);

    if (result != kCVReturnSuccess) {
        
    }

    //CIImage *coreImage = [CIImage imageWithCVPixelBuffer:pixelBuffer]; //success!
    //CVPixelBufferRelease(pixelBuffer);
}


+ (void) setTestPixelBufferFromImage: (CGImageRef) image
{
    NSDictionary *options = @{
                              (NSString*)kCVPixelBufferCGImageCompatibilityKey : @YES,
                              (NSString*)kCVPixelBufferCGBitmapContextCompatibilityKey : @YES,
                              };

    pixelBuffer1 = NULL;
    CVReturn status = CVPixelBufferCreate(kCFAllocatorDefault, CGImageGetWidth(image),
                        CGImageGetHeight(image), kCVPixelFormatType_32ARGB, (__bridge CFDictionaryRef) options,
                        &pixelBuffer1);
    if (status!=kCVReturnSuccess) {
        NSLog(@"Operation failed");
    }
    NSParameterAssert(status == kCVReturnSuccess && pixelBuffer1 != NULL);

    CVPixelBufferLockBaseAddress(pixelBuffer1, 0);
    void *pxdata = CVPixelBufferGetBaseAddress(pixelBuffer1);
    //size_t bytesPerRow = CVPixelBufferGetBytesPerRow(testPixelBuffer);

    CGColorSpaceRef rgbColorSpace = CGColorSpaceCreateDeviceRGB();
    CGContextRef context = CGBitmapContextCreate(pxdata, CGImageGetWidth(image),
                                                 CGImageGetHeight(image), 8, 4*CGImageGetWidth(image), rgbColorSpace,
                                                 kCGImageAlphaNoneSkipFirst);
    NSParameterAssert(context);

    CGContextConcatCTM(context, CGAffineTransformMakeRotation(0));
    CGAffineTransform flipVertical = CGAffineTransformMake( 1, 0, 0, -1, 0, CGImageGetHeight(image) );
    CGContextConcatCTM(context, flipVertical);
    CGAffineTransform flipHorizontal = CGAffineTransformMake( -1.0, 0.0, 0.0, 1.0, CGImageGetWidth(image), 0.0 );
    CGContextConcatCTM(context, flipHorizontal);

    CGContextDrawImage(context, CGRectMake(0, 0, CGImageGetWidth(image),
                                           CGImageGetHeight(image)), image);
    CGColorSpaceRelease(rgbColorSpace);
    CGContextRelease(context);

    CVPixelBufferUnlockBaseAddress(pixelBuffer1, 0);
}

@end
