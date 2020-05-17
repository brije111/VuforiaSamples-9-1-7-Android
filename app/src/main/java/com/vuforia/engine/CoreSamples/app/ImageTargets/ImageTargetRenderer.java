/*===============================================================================
Copyright (c) 2020 PTC Inc. All Rights Reserved.

Copyright (c) 2012-2014 Qualcomm Connected Experiences, Inc. All Rights Reserved.

Vuforia is a trademark of PTC Inc., registered in the United States and other 
countries.
===============================================================================*/

package com.vuforia.engine.CoreSamples.app.ImageTargets;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Vector;

import android.opengl.GLES20;
import android.opengl.Matrix;
import android.util.Log;

import com.vuforia.Device;
import com.vuforia.DeviceTrackableResult;
import com.vuforia.ImageTargetResult;
import com.vuforia.Matrix44F;
import com.vuforia.State;
import com.vuforia.Tool;
import com.vuforia.Trackable;
import com.vuforia.TrackableResult;
import com.vuforia.TrackableResultList;
import com.vuforia.Vuforia;
import com.vuforia.engine.SampleApplication.SampleAppRenderer;
import com.vuforia.engine.SampleApplication.SampleAppRendererControl;
import com.vuforia.engine.SampleApplication.SampleRendererBase;
import com.vuforia.engine.SampleApplication.SampleApplicationSession;
import com.vuforia.engine.SampleApplication.utils.CubeShaders;
import com.vuforia.engine.SampleApplication.utils.LoadingDialogHandler;
import com.vuforia.engine.SampleApplication.utils.MeshObject;
import com.vuforia.engine.SampleApplication.utils.SampleApplication3DModel;
import com.vuforia.engine.SampleApplication.utils.SampleMath;
import com.vuforia.engine.SampleApplication.utils.SampleUtils;
import com.vuforia.engine.SampleApplication.utils.Teapot;
import com.vuforia.engine.SampleApplication.utils.Texture;


/**
 * The renderer class for the Image Targets sample.
 *
 * In the renderFrame() function you can render augmentations to display over the Target
 */
public class ImageTargetRenderer extends SampleRendererBase implements SampleAppRendererControl
{
    private static final String LOGTAG = "ImageTargetRenderer";
    
    private final WeakReference<ImageTargets> mActivityRef;
    
    private int shaderProgramID;
    private int vertexHandle;
    private int textureCoordHandle;
    private int mvpMatrixHandle;
    private int texSampler2DHandle;

    // Object to be rendered
    private Teapot mTeapot;
    
    private static final float BUILDING_SCALE = 0.012f;
    private SampleApplication3DModel mBuildingsModel;

    private boolean mModelIsLoaded = false;
    private boolean mIsTargetCurrentlyTracked = false;
    
    private static final float OBJECT_SCALE_FLOAT = 0.003f;
    
    ImageTargetRenderer(ImageTargets activity, SampleApplicationSession session)
    {
        mActivityRef = new WeakReference<>(activity);
        vuforiaAppSession = session;

        // SampleAppRenderer used to encapsulate the use of RenderingPrimitives setting
        // the device mode AR/VR and stereo mode
        mSampleAppRenderer = new SampleAppRenderer(this, mActivityRef.get(), vuforiaAppSession.getVideoMode(),
                0.01f , 5f);
    }


    public void updateRenderingPrimitives()
    {
        mSampleAppRenderer.updateRenderingPrimitives();
    }


    public void setActive(boolean active)
    {
        mSampleAppRenderer.setActive(active);
    }


    // The render function.
    // This function is called from the SampleAppRenderer by using the RenderingPrimitives views.
    // The state is owned by SampleAppRenderer which is controlling its lifecycle.
    // NOTE: State should not be cached outside this method.
    public void renderFrame(State state, float[] projectionMatrix)
    {
        // Renders video background replacing Renderer.DrawVideoBackground()
        mSampleAppRenderer.renderVideoBackground();

        // Set the device pose matrix as identity
        Matrix44F devicePoseMatrix = SampleMath.Matrix44FIdentity();
        Matrix44F modelMatrix;

        GLES20.glEnable(GLES20.GL_DEPTH_TEST);

        GLES20.glEnable(GLES20.GL_CULL_FACE);
        GLES20.glCullFace(GLES20.GL_BACK);
        GLES20.glFrontFace(GLES20.GL_CCW);   // Back camera

        // Read device pose from the state and create a corresponding view matrix (inverse of the device pose)
        if (state.getDeviceTrackableResult() != null)
        {
            int statusInfo = state.getDeviceTrackableResult().getStatusInfo();
            int trackerStatus = state.getDeviceTrackableResult().getStatus();

            mActivityRef.get().checkForRelocalization(statusInfo);

            if (trackerStatus != TrackableResult.STATUS.NO_POSE)
            {
                modelMatrix = Tool.convertPose2GLMatrix(state.getDeviceTrackableResult().getPose());

                // We transpose here because Matrix44FInverse returns a transposed matrix
                devicePoseMatrix = SampleMath.Matrix44FTranspose(SampleMath.Matrix44FInverse(modelMatrix));
            }
        }

        TrackableResultList trackableResultList = state.getTrackableResults();

        // Determine if target is currently being tracked
        setIsTargetCurrentlyTracked(trackableResultList);

        // Iterate through trackable results and render any augmentations
        for (TrackableResult result : trackableResultList)
        {
            Trackable trackable = result.getTrackable();

            if (result.isOfType(ImageTargetResult.getClassType()) && result.getStatus() != TrackableResult.STATUS.LIMITED)
            {
                int textureIndex;
                modelMatrix = Tool.convertPose2GLMatrix(result.getPose());

                textureIndex = trackable.getName().equalsIgnoreCase("stones") ? 0
                    : 1;
                textureIndex = trackable.getName().equalsIgnoreCase("tarmac") ? 2
                    : textureIndex;

                textureIndex = mActivityRef.get().isDeviceTrackingActive() ? 3 : textureIndex;

                renderModel(projectionMatrix, devicePoseMatrix.getData(), modelMatrix.getData(), textureIndex);

                SampleUtils.checkGLError("Image Targets renderFrame");
            }
        }

        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
    }

    @Override
    public void initRendering()
    {
        if (mTextures == null)
        {
            return;
        }

        GLES20.glClearColor(0.0f, 0.0f, 0.0f, Vuforia.requiresAlpha() ? 0.0f
                : 1.0f);

        for (Texture t : mTextures)
        {
            GLES20.glGenTextures(1, t.mTextureID, 0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, t.mTextureID[0]);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                    t.mWidth, t.mHeight, 0, GLES20.GL_RGBA,
                    GLES20.GL_UNSIGNED_BYTE, t.mData);
        }

        shaderProgramID = SampleUtils.createProgramFromShaderSrc(
                CubeShaders.CUBE_MESH_VERTEX_SHADER,
                CubeShaders.CUBE_MESH_FRAGMENT_SHADER);

        vertexHandle = GLES20.glGetAttribLocation(shaderProgramID,
                "vertexPosition");
        textureCoordHandle = GLES20.glGetAttribLocation(shaderProgramID,
                "vertexTexCoord");
        mvpMatrixHandle = GLES20.glGetUniformLocation(shaderProgramID,
                "modelViewProjectionMatrix");
        texSampler2DHandle = GLES20.glGetUniformLocation(shaderProgramID,
                "texSampler2D");

        if(!mModelIsLoaded)
        {
            mTeapot = new Teapot();

            try {
                mBuildingsModel = new SampleApplication3DModel();
                mBuildingsModel.loadModel(mActivityRef.get().getResources().getAssets(),
                        "ImageTargets/Buildings.txt");
                mModelIsLoaded = true;
            } catch (IOException e)
            {
                Log.e(LOGTAG, "Unable to load buildings");
            }

            // Hide the Loading Dialog
            mActivityRef.get().loadingDialogHandler
                    .sendEmptyMessage(LoadingDialogHandler.HIDE_LOADING_DIALOG);
        }
    }


    private void renderModel(float[] projectionMatrix, float[] viewMatrix, float[] modelMatrix, int textureIndex)
    {
        MeshObject model;
        float[] modelViewProjection = new float[16];

        // Apply local transformation to our model
        if (mActivityRef.get().isDeviceTrackingActive())
        {
            Matrix.translateM(modelMatrix, 0, 0, -0.06f, 0);
            Matrix.rotateM(modelMatrix, 0, 90.0f, 1.0f, 0, 0);
            Matrix.scaleM(modelMatrix, 0, BUILDING_SCALE, BUILDING_SCALE, BUILDING_SCALE);

            model = mBuildingsModel;
        }
        else
        {
            Matrix.translateM(modelMatrix, 0, 0, 0, OBJECT_SCALE_FLOAT);
            Matrix.scaleM(modelMatrix, 0, OBJECT_SCALE_FLOAT, OBJECT_SCALE_FLOAT, OBJECT_SCALE_FLOAT);

            model = mTeapot;
        }

        // Combine device pose (view matrix) with model matrix
        Matrix.multiplyMM(modelMatrix, 0, viewMatrix, 0, modelMatrix, 0);

        // Do the final combination with the projection matrix
        Matrix.multiplyMM(modelViewProjection, 0, projectionMatrix, 0, modelMatrix, 0);

        // Activate the shader program and bind the vertex and tex coords
        GLES20.glUseProgram(shaderProgramID);

        GLES20.glVertexAttribPointer(vertexHandle, 3, GLES20.GL_FLOAT, false, 0, model.getVertices());
        GLES20.glVertexAttribPointer(textureCoordHandle, 2, GLES20.GL_FLOAT, false, 0, model.getTexCoords());

        GLES20.glEnableVertexAttribArray(vertexHandle);
        GLES20.glEnableVertexAttribArray(textureCoordHandle);

        // Activate texture 0, bind it, pass to shader
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextures.get(textureIndex).mTextureID[0]);
        GLES20.glUniform1i(texSampler2DHandle, 0);

        // Pass the model view matrix to the shader
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, modelViewProjection, 0);

        // Finally draw the model
        if (mActivityRef.get().isDeviceTrackingActive())
        {
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, model.getNumObjectVertex());
        }
        else
        {
            GLES20.glDrawElements(GLES20.GL_TRIANGLES, model.getNumObjectIndex(), GLES20.GL_UNSIGNED_SHORT, model.getIndices());
        }

        // Disable the enabled arrays
        GLES20.glDisableVertexAttribArray(vertexHandle);
        GLES20.glDisableVertexAttribArray(textureCoordHandle);
    }


    public void setTextures(Vector<Texture> textures)
    {
        mTextures = textures;
    }


    private void setIsTargetCurrentlyTracked(TrackableResultList trackableResultList)
    {
        for(TrackableResult result : trackableResultList)
        {
            // Check the tracking status for result types
            // other than DeviceTrackableResult. ie: ImageTargetResult
            if (!result.isOfType(DeviceTrackableResult.getClassType()))
            {
                int currentStatus = result.getStatus();
                int currentStatusInfo = result.getStatusInfo();

                // The target is currently being tracked if the status is TRACKED|NORMAL
                if (currentStatus == TrackableResult.STATUS.TRACKED
                        || currentStatusInfo == TrackableResult.STATUS_INFO.NORMAL)
                {
                    mIsTargetCurrentlyTracked = true;
                    return;
                }
            }
        }

        mIsTargetCurrentlyTracked = false;
    }


    boolean isTargetCurrentlyTracked()
    {
        return mIsTargetCurrentlyTracked;
    }
}
