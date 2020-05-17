/*===============================================================================
Copyright (c) 2020 PTC Inc. All Rights Reserved.

Copyright (c) 2012-2015 Qualcomm Connected Experiences, Inc. All Rights Reserved.

Vuforia is a trademark of PTC Inc., registered in the United States and other
countries.
===============================================================================*/

package com.vuforia.engine.SampleApplication;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Point;
import android.opengl.GLES20;
import android.util.Log;

import com.vuforia.CameraDevice;
import com.vuforia.Device;
import com.vuforia.GLTextureUnit;
import com.vuforia.Matrix34F;
import com.vuforia.Mesh;
import com.vuforia.Renderer;
import com.vuforia.RenderingPrimitives;
import com.vuforia.State;
import com.vuforia.Tool;
import com.vuforia.TrackerManager;
import com.vuforia.VIEW;
import com.vuforia.Vec2I;
import com.vuforia.Vec4I;
import com.vuforia.VideoBackgroundConfig;
import com.vuforia.VideoMode;
import com.vuforia.engine.SampleApplication.utils.SampleUtils;
import com.vuforia.engine.SampleApplication.utils.VideoBackgroundShader;

import java.lang.ref.WeakReference;


/**
 * The SampleAppRenderer class handles the initialization and configuration of the
 * video background rendering and rendering primitives
 *
 * The render() function of this class calls renderFrame() which is implemented
 * in each feature renderer to render augmentations
 *
 * This class is used by all of the feature activities in this sample
 */
public class SampleAppRenderer
{
    private static final String LOGTAG = "SampleAppRenderer";

    private RenderingPrimitives mRenderingPrimitives = null;
    private final SampleAppRendererControl mRenderingInterface;
    private final WeakReference<Activity> mActivityRef;

    private int mVideoMode;

    private final Renderer mRenderer;
    private float mNearPlane = -1.0f;
    private float mFarPlane = -1.0f;

    private GLTextureUnit videoBackgroundTex = null;

    // Shader user to render the video background on AR mode
    private int vbShaderProgramID = 0;
    private int vbTexSampler2DHandle = 0;
    private int vbVertexHandle = 0;
    private int vbTexCoordHandle = 0;
    private int vbProjectionMatrixHandle = 0;

    // Display size of the device:
    private int mScreenWidth = 0;
    private int mScreenHeight = 0;

    // Stores orientation
    private boolean mIsPortrait = false;
    private boolean mIsActive = false;

    private boolean mIsRenderingInit = false;


    public SampleAppRenderer(SampleAppRendererControl renderingInterface, Activity activity,
                             int videoMode, float nearPlane, float farPlane)
    {
        mActivityRef = new WeakReference<>(activity);

        mRenderingInterface = renderingInterface;
        mRenderer = Renderer.getInstance();

        if(farPlane < nearPlane)
        {
            Log.e(LOGTAG, "Far plane should be greater than near plane");
            throw new IllegalArgumentException();
        }

        setNearFarPlanes(nearPlane, farPlane);

        mVideoMode = videoMode;
    }


    void onSurfaceCreated()
    {
        initRendering();
    }

    // Called whenever the device orientation or screen resolution changes
    // and we need to update the rendering primitives
    public void onConfigurationChanged()
    {
        updateActivityOrientation();
        storeScreenDimensions();

        configureVideoBackground();
        updateRenderingPrimitives();

        if (!mIsRenderingInit)
        {
            mRenderingInterface.initRendering();
            mIsRenderingInit = true;
        }
    }


    public void setActive(boolean value)
    {
        if (mIsActive == value)
        {
            return;
        }

        mIsActive = value;
        configureVideoBackground();
    }


    public synchronized void updateRenderingPrimitives()
    {
        mRenderingPrimitives = Device.getInstance().getRenderingPrimitives();
    }

    // Initializes shader
    private void initRendering()
    {
        vbShaderProgramID = SampleUtils.createProgramFromShaderSrc(VideoBackgroundShader.VB_VERTEX_SHADER,
                VideoBackgroundShader.VB_FRAGMENT_SHADER);

        // Rendering configuration for video background
        if (vbShaderProgramID > 0)
        {
            // Activate shader:
            GLES20.glUseProgram(vbShaderProgramID);

            // Retrieve handler for texture sampler shader uniform variable:
            vbTexSampler2DHandle = GLES20.glGetUniformLocation(vbShaderProgramID, "texSampler2D");

            // Retrieve handler for projection matrix shader uniform variable:
            vbProjectionMatrixHandle = GLES20.glGetUniformLocation(vbShaderProgramID, "projectionMatrix");

            vbVertexHandle = GLES20.glGetAttribLocation(vbShaderProgramID, "vertexPosition");
            vbTexCoordHandle = GLES20.glGetAttribLocation(vbShaderProgramID, "vertexTexCoord");
            vbProjectionMatrixHandle = GLES20.glGetUniformLocation(vbShaderProgramID, "projectionMatrix");
            vbTexSampler2DHandle = GLES20.glGetUniformLocation(vbShaderProgramID, "texSampler2D");

            // Stop using the program
            GLES20.glUseProgram(0);
        }

        videoBackgroundTex = new GLTextureUnit();
    }

    // Main rendering method
    // The method setup state for rendering, setup 3D transformations required for AR augmentation
    // and call any specific rendering method
    public void render()
    {
        if (!mIsActive)
        {
            return;
        }

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        // Get our current state
        State state;
        state = TrackerManager.getInstance().getStateUpdater().updateState();
        mRenderer.begin(state);

        GLES20.glFrontFace(GLES20.GL_CCW);  // Back camera

        // Get the viewport for that specific view
        Vec4I viewport;
        viewport = mRenderingPrimitives.getViewport(VIEW.VIEW_SINGULAR);

        // Set viewport for current view
        GLES20.glViewport(viewport.getData()[0], viewport.getData()[1], viewport.getData()[2], viewport.getData()[3]);

        // Set scissor
        GLES20.glScissor(viewport.getData()[0], viewport.getData()[1], viewport.getData()[2], viewport.getData()[3]);

        // Get projection matrix for the current view.
        Matrix34F projMatrix = mRenderingPrimitives.getProjectionMatrix(VIEW.VIEW_SINGULAR,
                                                                        state.getCameraCalibration());

        // Create GL matrix setting up the near and far planes
        float[] projectionMatrix = Tool.convertPerspectiveProjection2GLMatrix(
                projMatrix,
                mNearPlane,
                mFarPlane)
                .getData();

        mRenderingInterface.renderFrame(state, projectionMatrix);

        mRenderer.end();
    }


    private void setNearFarPlanes(float near, float far)
    {
        mNearPlane = near;
        mFarPlane = far;
    }


    public void renderVideoBackground()
    {
        // Bind the video bg texture and get the Texture ID from Vuforia Engine
        int vbVideoTextureUnit = 0;
        videoBackgroundTex.setTextureUnit(vbVideoTextureUnit);

        if (!mRenderer.updateVideoBackgroundTexture(videoBackgroundTex))
        {
            Log.e(LOGTAG, "Unable to update video background texture");
            return;
        }

        float[] vbProjectionMatrix = Tool.convert2GLMatrix(
                mRenderingPrimitives.getVideoBackgroundProjectionMatrix(VIEW.VIEW_SINGULAR)).getData();

        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glDisable(GLES20.GL_CULL_FACE);
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);

        Mesh vbMesh = mRenderingPrimitives.getVideoBackgroundMesh(VIEW.VIEW_SINGULAR);

        // Load the shader and upload the vertex/texcoord/index data
        GLES20.glUseProgram(vbShaderProgramID);
        GLES20.glVertexAttribPointer(vbVertexHandle, 3, GLES20.GL_FLOAT, false, 0, vbMesh.getPositions().asFloatBuffer());
        GLES20.glVertexAttribPointer(vbTexCoordHandle, 2, GLES20.GL_FLOAT, false, 0, vbMesh.getUVs().asFloatBuffer());

        GLES20.glUniform1i(vbTexSampler2DHandle, vbVideoTextureUnit);

        // Render the video background with the custom shader
        // First, we enable the vertex arrays
        GLES20.glEnableVertexAttribArray(vbVertexHandle);
        GLES20.glEnableVertexAttribArray(vbTexCoordHandle);

        // Pass the projection matrix to OpenGL
        GLES20.glUniformMatrix4fv(vbProjectionMatrixHandle, 1, false, vbProjectionMatrix, 0);

        // Then, we issue the render call
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, vbMesh.getNumTriangles() * 3, GLES20.GL_UNSIGNED_SHORT,
                vbMesh.getTriangles().asShortBuffer());

        // Finally, we disable the vertex arrays
        GLES20.glDisableVertexAttribArray(vbVertexHandle);
        GLES20.glDisableVertexAttribArray(vbTexCoordHandle);

        SampleUtils.checkGLError("Rendering of the video background failed");
    }


    // Configures the video mode and sets offsets for the camera's image
    private void configureVideoBackground()
    {
        if (!mIsActive)
        {
            return;
        }

        CameraDevice cameraDevice = CameraDevice.getInstance();
        VideoMode vm = cameraDevice.getVideoMode(mVideoMode);

        if (vm.getWidth() == 0 || vm.getHeight() == 0)
        {
            return;
        }

        VideoBackgroundConfig config = new VideoBackgroundConfig();
        config.setPosition(new Vec2I(0, 0));

        int xSize, ySize;

        // We keep the aspect ratio to keep the video correctly rendered. If it is portrait we
        // preserve the height and scale width and vice versa if it is landscape, we preserve
        // the width and we check if the selected values fill the screen, otherwise we invert
        // the selection
        if (mIsPortrait)
        {
            xSize = (int) (vm.getHeight() * (mScreenHeight / (float) vm
                    .getWidth()));
            ySize = mScreenHeight;

            if (xSize < mScreenWidth)
            {
                xSize = mScreenWidth;
                ySize = (int) (mScreenWidth * (vm.getWidth() / (float) vm
                        .getHeight()));
            }
        }
        else
        {
            xSize = mScreenWidth;
            ySize = (int) (vm.getHeight() * (mScreenWidth / (float) vm
                    .getWidth()));

            if (ySize < mScreenHeight)
            {
                xSize = (int) (mScreenHeight * (vm.getWidth() / (float) vm
                        .getHeight()));
                ySize = mScreenHeight;
            }
        }

        config.setSize(new Vec2I(xSize, ySize));

        Log.i(LOGTAG, "Configure Video Background : Video (" + vm.getWidth()
                + " , " + vm.getHeight() + "), Screen (" + mScreenWidth + " , "
                + mScreenHeight + "), mSize (" + xSize + " , " + ySize + ")");

        Renderer.getInstance().setVideoBackgroundConfig(config);
    }


    private void storeScreenDimensions()
    {
        // Query display dimensions:
        Point size = new Point();
        mActivityRef.get().getWindowManager().getDefaultDisplay().getRealSize(size);
        mScreenWidth = size.x;
        mScreenHeight = size.y;
    }


    // Stores the orientation depending on the current resources configuration
    private void updateActivityOrientation()
    {
        Configuration config = mActivityRef.get().getResources().getConfiguration();

        switch (config.orientation)
        {
            case Configuration.ORIENTATION_PORTRAIT:
                mIsPortrait = true;
                break;
            case Configuration.ORIENTATION_LANDSCAPE:
                mIsPortrait = false;
                break;
            case Configuration.ORIENTATION_UNDEFINED:
            default:
                break;
        }

        Log.i(LOGTAG, "Activity is in "
                + (mIsPortrait ? "PORTRAIT" : "LANDSCAPE"));
    }
}
