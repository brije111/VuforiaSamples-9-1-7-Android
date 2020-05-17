/*===============================================================================
Copyright (c) 2020 PTC Inc. All Rights Reserved.

Copyright (c) 2012-2014 Qualcomm Connected Experiences, Inc. All Rights Reserved.

Vuforia is a trademark of PTC Inc., registered in the United States and other 
countries.
===============================================================================*/

package com.vuforia.engine.SampleApplication.utils;

import android.opengl.GLES20;
import android.util.Log;

import com.vuforia.Image;
import com.vuforia.PIXEL_FORMAT;

/**
 * Support class for the Vuforia sample applications
 * Contains helper functions for initializing shaders,
 * creating textures, and OpenGL error checking
 */
@SuppressWarnings({"unused", "SuspiciousNameCombination"})
public class SampleUtils
{
    
    private static final String LOGTAG = "SampleUtils";

    // Enable this flag to debug OpenGL errors
    private static final boolean DEBUG_GL = false;
    
    private static int initShader(int shaderType, String source)
    {
        int shader = GLES20.glCreateShader(shaderType);
        if (shader != 0)
        {
            GLES20.glShaderSource(shader, source);
            GLES20.glCompileShader(shader);
            
            int[] glStatusVar = { GLES20.GL_FALSE };
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, glStatusVar,
                0);
            if (glStatusVar[0] == GLES20.GL_FALSE)
            {
                Log.e(LOGTAG, "Could NOT compile shader " + shaderType + " : "
                    + GLES20.glGetShaderInfoLog(shader));
                GLES20.glDeleteShader(shader);
                shader = 0;
            }
            
        }
        
        return shader;
    }
    
    
    public static int createProgramFromShaderSrc(String vertexShaderSrc,
        String fragmentShaderSrc)
    {
        int vertShader = initShader(GLES20.GL_VERTEX_SHADER, vertexShaderSrc);
        int fragShader = initShader(GLES20.GL_FRAGMENT_SHADER,
            fragmentShaderSrc);
        
        if (vertShader == 0 || fragShader == 0)
            return 0;
        
        int program = GLES20.glCreateProgram();
        if (program != 0)
        {
            GLES20.glAttachShader(program, vertShader);
            checkGLError("glAttchShader(vert)");
            
            GLES20.glAttachShader(program, fragShader);
            checkGLError("glAttchShader(frag)");
            
            GLES20.glLinkProgram(program);
            int[] glStatusVar = { GLES20.GL_FALSE };
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, glStatusVar,
                0);
            if (glStatusVar[0] == GLES20.GL_FALSE)
            {
                Log.e(
                    LOGTAG,
                    "Could NOT link program : "
                        + GLES20.glGetProgramInfoLog(program));
                GLES20.glDeleteProgram(program);
                program = 0;
            }
        }
        
        return program;
    }
    
    
    public static void checkGLError(String op)
    {
        if (DEBUG_GL)
        {
            for (int error = GLES20.glGetError(); error != 0; error = GLES20.glGetError())
            {
                Log.e(LOGTAG, "After operation " + op + " got glError 0x"
                        + Integer.toHexString(error));
            }
        }
    }
    
    
    // Transforms a screen pixel to a pixel onto the camera image,
    // taking into account e.g. cropping of camera image to fit different aspect
    // ratio screen.
    // for the camera dimensions, the width is always bigger than the height
    // (always landscape orientation)
    // Top left of screen/camera is origin
    public static void screenCoordToCameraCoord(int screenX, int screenY,
        int screenDX, int screenDY, int screenWidth, int screenHeight,
        int cameraWidth, int cameraHeight, int[] cameraX, int[] cameraY,
        int[] cameraDX, int[] cameraDY, int displayRotation, int cameraRotation)
    {
        float videoWidth, videoHeight;
        videoWidth = (float) cameraWidth;
        videoHeight = (float) cameraHeight;

        // Compute the angle by which the camera image should be rotated clockwise so that it is
        // shown correctly on the display given its current orientation.
        int correctedRotation = ((((displayRotation*90)-cameraRotation)+360)%360)/90;

        switch (correctedRotation) {

            case 0:
                break;

            case 1:

                int tmp = screenX;
                screenX = screenHeight - screenY;
                screenY = tmp;

                tmp = screenDX;
                screenDX = screenDY;
                screenDY = tmp;

                tmp = screenWidth;
                screenWidth = screenHeight;
                screenHeight = tmp;

                break;

            case 2:
                screenX = screenWidth - screenX;
                screenY = screenHeight - screenY;
                break;

            case 3:

                tmp = screenX;
                screenX = screenY;
                screenY = screenWidth - tmp;

                tmp = screenDX;
                screenDX = screenDY;
                screenDY = tmp;

                tmp = screenWidth;
                screenWidth = screenHeight;
                screenHeight = tmp;

                break;
        }
        
        float videoAspectRatio = videoHeight / videoWidth;
        float screenAspectRatio = (float) screenHeight / (float) screenWidth;
        
        float scaledUpX;
        float scaledUpY;
        float scaledUpVideoWidth;
        float scaledUpVideoHeight;
        
        if (videoAspectRatio < screenAspectRatio)
        {
            // the video height will fit in the screen height
            scaledUpVideoWidth = (float) screenHeight / videoAspectRatio;
            scaledUpVideoHeight = screenHeight;
            scaledUpX = (float) screenX
                + ((scaledUpVideoWidth - (float) screenWidth) / 2.0f);
            scaledUpY = (float) screenY;
        } else
        {
            // the video width will fit in the screen width
            scaledUpVideoHeight = (float) screenWidth * videoAspectRatio;
            scaledUpVideoWidth = screenWidth;
            scaledUpY = (float) screenY
                + ((scaledUpVideoHeight - (float) screenHeight) / 2.0f);
            scaledUpX = (float) screenX;
        }
        
        if (cameraX != null && cameraX.length > 0)
        {
            cameraX[0] = (int) ((scaledUpX / scaledUpVideoWidth) * videoWidth);
        }
        
        if (cameraY != null && cameraY.length > 0)
        {
            cameraY[0] = (int) ((scaledUpY / scaledUpVideoHeight) * videoHeight);
        }
        
        if (cameraDX != null && cameraDX.length > 0)
        {
            cameraDX[0] = (int) (((float) screenDX / scaledUpVideoWidth) * videoWidth);
        }
        
        if (cameraDY != null && cameraDY.length > 0)
        {
            cameraDY[0] = (int) (((float) screenDY / scaledUpVideoHeight) * videoHeight);
        }
    }
    
    
    public static float[] getOrthoMatrix(float nLeft, float nRight,
        float nBottom, float nTop, float nNear, float nFar)
    {
        float[] nProjMatrix = new float[16];
        
        int i;
        for (i = 0; i < 16; i++)
            nProjMatrix[i] = 0.0f;
        
        nProjMatrix[0] = 2.0f / (nRight - nLeft);
        nProjMatrix[5] = 2.0f / (nTop - nBottom);
        nProjMatrix[10] = 2.0f / (nNear - nFar);
        nProjMatrix[12] = -(nRight + nLeft) / (nRight - nLeft);
        nProjMatrix[13] = -(nTop + nBottom) / (nTop - nBottom);
        nProjMatrix[14] = (nFar + nNear) / (nFar - nNear);
        nProjMatrix[15] = 1.0f;
        
        return nProjMatrix;
    }

    // Helper method to reduce code duplication
    private static void vuforiaToGLFormat(int vuforiaFormat, int formatTypePair[])
    {
        formatTypePair[0] = GLES20.GL_INVALID_ENUM;
        formatTypePair[1] = GLES20.GL_INVALID_ENUM;
        
        switch (vuforiaFormat)
        {
            case PIXEL_FORMAT.UNKNOWN_FORMAT:
            case PIXEL_FORMAT.NV12:
            case PIXEL_FORMAT.NV21:
            case PIXEL_FORMAT.YV12:
            case PIXEL_FORMAT.YUV420P:
            case PIXEL_FORMAT.YUYV:
                return;

            case PIXEL_FORMAT.RGB565:
                formatTypePair[0] = GLES20.GL_RGB;
                formatTypePair[1] = GLES20.GL_UNSIGNED_SHORT_5_6_5;
                break;
            case PIXEL_FORMAT.RGB888:
                formatTypePair[0] = GLES20.GL_RGB;
                formatTypePair[1] = GLES20.GL_UNSIGNED_BYTE;
                break;

            case PIXEL_FORMAT.RGBA8888:
                formatTypePair[0] = GLES20.GL_RGBA;
                formatTypePair[1] = GLES20.GL_UNSIGNED_BYTE;
                break;

            case PIXEL_FORMAT.GRAYSCALE:
                formatTypePair[0] = GLES20.GL_LUMINANCE;
                formatTypePair[1] = GLES20.GL_UNSIGNED_BYTE;
                break;

            default:
                return;
        }
    }
    
    public static void substituteTextureImage(int textureID, Image image)
    {
        int formatTypePair[] = new int[2];
        vuforiaToGLFormat(image.getFormat(), formatTypePair);
        if (formatTypePair[0] == GLES20.GL_INVALID_ENUM || 
            formatTypePair[1] == GLES20.GL_INVALID_ENUM)
        {
            Log.e(LOGTAG, "Invalid GL enum for texture substitution");
            return;
        }
        
        // Bind to the given texture
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureID);
        
        GLES20.glTexSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0,
                               image.getWidth(),
                               image.getHeight(),
                               formatTypePair[0],
                               formatTypePair[1],
                               image.getPixels());
                    
        // Unbind
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
    }
    
    public static int createTexture(Image image)
    {
        Texture texture = Texture.loadTextureFromImage(image);

        int glTextureID[] = new int[1];
        glTextureID[0] = -1;
        GLES20.glGenTextures(1, glTextureID, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, glTextureID[0]);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);

        int pixelFormat = image.getFormat();
        int formatTypePair[] = new int[2];
        vuforiaToGLFormat(pixelFormat, formatTypePair);
        if (formatTypePair[0] == GLES20.GL_INVALID_ENUM ||
            formatTypePair[1] == GLES20.GL_INVALID_ENUM)
        {
            return -1;
        }

        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, formatTypePair[0] , texture.mWidth, texture.mHeight, 0,
                formatTypePair[0], formatTypePair[1], texture.mData);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

        return glTextureID[0];
    }


    public static void deleteTexture(int textureId)
    {
        int glTextureId[] = new int[1];
        glTextureId[0] = textureId;
        GLES20.glDeleteTextures(1, glTextureId, 0);
    }
}
