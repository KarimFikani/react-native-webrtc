package com.oney.WebRTCModule;

public class CameraSetting {
    /** width of the picture */
    public int width;
    /** height of the picture */
    public int height;
    /** Framerate */
    public int fps;

    /**
     * Sets the dimensions for pictures.
     *
     * @param w the photo width (pixels)
     * @param h the photo height (pixels)
     */
    public CameraSetting(int w, int h, int f) {
        width = w;
        height = h;
        fps = f;
    }
    /**
     * Compares {@code obj} to this size.
     *
     * @param obj the object to compare this size with.
     * @return {@code true} if the width and height of {@code obj} is the
     *         same as those of this size. {@code false} otherwise.
     */
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof CameraSetting)) {
            return false;
        }
        CameraSetting s = (CameraSetting) obj;
        return width == s.width && height == s.height && fps == s.fps;
    }
    @Override
    public int hashCode() {
        return width * 32713 + height;
    }
};
