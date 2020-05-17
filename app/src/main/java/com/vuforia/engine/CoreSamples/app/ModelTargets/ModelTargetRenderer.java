/*===============================================================================
Copyright (c) 2020 PTC Inc. All Rights Reserved.

Copyright (c) 2012-2014 Qualcomm Connected Experiences, Inc. All Rights Reserved.

Vuforia is a trademark of PTC Inc., registered in the United States and other 
countries.
===============================================================================*/

package com.vuforia.engine.CoreSamples.app.ModelTargets;

import android.content.res.Configuration;
import android.graphics.Point;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.AsyncTask;
import android.util.Log;
import android.view.View;

import com.vuforia.Device;
import com.vuforia.DeviceTrackableResult;
import com.vuforia.GuideView;
import com.vuforia.Illumination;
import com.vuforia.Image;
import com.vuforia.Matrix44F;
import com.vuforia.ModelTargetResult;
import com.vuforia.State;
import com.vuforia.Tool;
import com.vuforia.TrackableResult;
import com.vuforia.ModelTarget;
import com.vuforia.TrackableResultList;
import com.vuforia.Vec2F;
import com.vuforia.Vec4F;
import com.vuforia.Vuforia;
import com.vuforia.engine.SampleApplication.SampleAppRenderer;
import com.vuforia.engine.SampleApplication.SampleAppRendererControl;
import com.vuforia.engine.SampleApplication.SampleApplicationSession;
import com.vuforia.engine.SampleApplication.SampleRendererBase;
import com.vuforia.engine.SampleApplication.utils.LightingShaders;
import com.vuforia.engine.SampleApplication.utils.Plane;
import com.vuforia.engine.SampleApplication.utils.SampleApplicationV3DModel;
import com.vuforia.engine.SampleApplication.utils.SampleMath;
import com.vuforia.engine.SampleApplication.utils.SampleUtils;
import com.vuforia.engine.SampleApplication.utils.Texture;
import com.vuforia.engine.SampleApplication.utils.TextureColorShaders;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Vector;

import javax.microedition.khronos.opengles.GL10;


// The renderer class for the ModelTargets sample.
public class ModelTargetRenderer extends SampleRendererBase implements SampleAppRendererControl
{
    private static final String LOGTAG = "ModelTargetRenderer";

    private ModelTargets mActivity;
    
    private int planeShaderProgramID;
    private int planeVertexHandle;
    private int planeTextureCoordHandle;
    private int planeMvpMatrixHandle;
    private int planeTexSampler2DHandle;
    private int planeColorHandle;

    private float mPlaneWidth;
    private float mPlaneHeight;

    private int guideViewHandle;
    private Vec2F mGuideViewScale;

    private int shaderProgramID;
    private int vertexHandle;
    private int mvpMatrixHandle;
    private int mvMatrixHandle;
    private int normalHandle;
    private int textureCoordHandle;
    private int texSampler2DHandle;
    private int normalMatrixHandle;
    private int lightPositionHandle;
    private int lightColorHandle;
    private int colorCorrectionHandle;
    private int intensityCorrectionHandle;

    // No color correction by default
    private Vec4F mColorCorrection = new Vec4F(1.0f, 1.0f, 1.0f, 1.0f);
    private float mIntensityCorrection = 1.0f;

    private Plane mPlane;
    private SampleApplicationV3DModel mLanderModel;

    private boolean mAreModelsLoaded = false;

    private boolean mIsTargetCurrentlyTracked = false;

    private HashMap<String, Integer> mSymbolicGuideViewIndices;

    private String mCurrentModelTargetId;
    private int mActiveGuideViewIndex = -1;

    ModelTargetRenderer(ModelTargets activity, SampleApplicationSession session)
    {
        mActivity = activity;
        vuforiaAppSession = session;
        // SampleAppRenderer used to encapsulate the use of RenderingPrimitives setting
        // the device mode AR/VR and stereo mode
        mSampleAppRenderer = new SampleAppRenderer(this, mActivity,
                vuforiaAppSession.getVideoMode(), 0.01f , 5f);

        guideViewHandle = -1;
    }

    
    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height)
    {
        super.onSurfaceChanged(gl, width, height);

        // Invalidate guide view handle so it is regenerated onSurfaceChanged()
        guideViewHandle = -1;
        mCurrentModelTargetId = null;
    }


    public void setActive(boolean active)
    {
        mSampleAppRenderer.setActive(active);
    }


    // Function for initializing the renderer.
    @Override
    public void initRendering()
    {
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
        
        planeShaderProgramID = SampleUtils.createProgramFromShaderSrc(
            TextureColorShaders.TEXTURE_COLOR_VERTEX_SHADER,
            TextureColorShaders.TEXTURE_COLOR_FRAGMENT_SHADER);

        mPlane = new Plane();

        if (planeShaderProgramID > 0)
        {
            planeVertexHandle = GLES20.glGetAttribLocation(planeShaderProgramID,
                    "vertexPosition");
            planeTextureCoordHandle = GLES20.glGetAttribLocation(planeShaderProgramID,
                    "vertexTexCoord");
            planeMvpMatrixHandle = GLES20.glGetUniformLocation(planeShaderProgramID,
                    "modelViewProjectionMatrix");
            planeTexSampler2DHandle = GLES20.glGetUniformLocation(planeShaderProgramID,
                    "texSampler2D");
            planeColorHandle = GLES20.glGetUniformLocation(planeShaderProgramID,
                    "uniformColor");

        }
        else
        {
            Log.e(LOGTAG, "Could not init plane shader");
        }

        shaderProgramID = SampleUtils.createProgramFromShaderSrc(
                LightingShaders.LIGHTING_VERTEX_SHADER,
                LightingShaders.LIGHTING_FRAGMENT_SHADER);

        if (shaderProgramID > 0)
        {
            vertexHandle = GLES20.glGetAttribLocation(shaderProgramID, "vertexPosition");
            normalHandle = GLES20.glGetAttribLocation(shaderProgramID, "vertexNormal");
            textureCoordHandle = GLES20.glGetAttribLocation(shaderProgramID, "vertexTexCoord");
            mvpMatrixHandle = GLES20.glGetUniformLocation(shaderProgramID, "u_mvpMatrix");
            mvMatrixHandle = GLES20.glGetUniformLocation(shaderProgramID, "u_mvMatrix");
            normalMatrixHandle = GLES20.glGetUniformLocation(shaderProgramID, "u_normalMatrix");
            lightPositionHandle = GLES20.glGetUniformLocation(shaderProgramID, "u_lightPos");
            lightColorHandle = GLES20.glGetUniformLocation(shaderProgramID, "u_lightColor");
            colorCorrectionHandle = GLES20.glGetUniformLocation(shaderProgramID, "u_colorCorrection");
            intensityCorrectionHandle = GLES20.glGetUniformLocation(shaderProgramID, "u_intensityCorrection");
            texSampler2DHandle = GLES20.glGetUniformLocation(shaderProgramID, "texSampler2D");

        }
        else
        {
            Log.e(LOGTAG, "Could not init lighting shader");
        }

        if(!mAreModelsLoaded)
        {
            LoadModelTask modelTask = new LoadModelTask(this);
            modelTask.execute();
        }

        mGuideViewScale = new Vec2F(1.0f, 1.0f);
    }


    public void updateRenderingPrimitives()
    {
        mSampleAppRenderer.updateRenderingPrimitives();
    }


    private static class LoadModelTask extends AsyncTask<Void, Integer, Boolean>
    {
        private final WeakReference<ModelTargetRenderer> mRendererRef;

        LoadModelTask(ModelTargetRenderer mtRenderer)
        {
            mRendererRef = new WeakReference<>(mtRenderer);
        }

        protected Boolean doInBackground(Void... params)
        {
            ModelTargetRenderer renderer = mRendererRef.get();
            ModelTargets activity = renderer.mActivity;

            renderer.mLanderModel = new SampleApplicationV3DModel(false);
            boolean landerLoaded = renderer.mLanderModel.loadModel(activity.getResources().getAssets(), "Lander.v3d");

            renderer.mAreModelsLoaded = landerLoaded;

            return renderer.mAreModelsLoaded;
        }

        protected void onPostExecute(Boolean result)
        {
            ModelTargets activity = mRendererRef.get().mActivity;

            // Hide the Loading Dialog
            activity.showProgressIndicator(false);
            activity.setBtnLayoutVisibility(View.VISIBLE);
        }
    }


    // The render function called from SampleAppRendering by using RenderingPrimitives views.
    // The state is owned by SampleAppRenderer which is controlling it's lifecycle.
    // State should not be cached outside this method.
    public void renderFrame(State state, float[] projectionMatrix)
    {
        // Renders video background replacing Renderer.DrawVideoBackground()
        mSampleAppRenderer.renderVideoBackground();

        GLES20.glEnable(GLES20.GL_DEPTH_TEST);

        GLES20.glEnable(GLES20.GL_CULL_FACE);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glCullFace(GLES20.GL_BACK);

        if (state.getDeviceTrackableResult() != null)
        {
            int statusInfo = state.getDeviceTrackableResult().getStatusInfo();
            mActivity.checkForRelocalization(statusInfo);
        }

        // Use color correction if illumination information is available
        Illumination illumination = state.getIllumination();
        if (illumination != null)
        {
            // Assumes we are rendering in gamma space, as per Google ARCore documentation
            // https://developers.google.com/ar/reference/c/group/light#group__light_1ga70e0426f83e94a3f8f4c103a060b3414
            // These correction values are being used in the shaders used for augmentations rendering: LightingShaders and DiffuseLightMaterials
            mIntensityCorrection = illumination.getIntensityCorrection() / 0.466f;
            mColorCorrection = illumination.getColorCorrection();
        }

        GuideView activeGuideView;
        Image textureImage;

        // Set the device pose matrix as identity
        Matrix44F devicePoseMatrix = SampleMath.Matrix44FIdentity();
        Matrix44F modelMatrix;

        // Read device pose from the state and create a corresponding view matrix (inverse of the device pose)
        if (state.getDeviceTrackableResult() != null
                && state.getDeviceTrackableResult().getStatus() != TrackableResult.STATUS.NO_POSE)
        {
            modelMatrix = Tool.convertPose2GLMatrix(state.getDeviceTrackableResult().getPose());

            // We transpose here because Matrix44FInverse returns a transposed matrix
            devicePoseMatrix = SampleMath.Matrix44FTranspose(SampleMath.Matrix44FInverse(modelMatrix));
        }

        TrackableResult currentTrackableResult;
        TrackableResultList trackableResultList = state.getTrackableResults();

        // Determine if target is currently being tracked
        setIsTargetCurrentlyTracked(trackableResultList);

        boolean guideViewOrModelChanged = false;
        boolean hasModelTargetResult = false;

        ArrayList<ModelTarget> modelsBeingSearchedFor = new ArrayList<>();
        ModelTarget modelRequiringGuidance = null;

        // Iterate through trackable results and render any augmentations
        for (TrackableResult trackableResult : trackableResultList)
        {
            if (trackableResult.isOfType(ModelTargetResult.getClassType()))
            {
                ModelTarget modelTarget = (ModelTarget) trackableResult.getTrackable();

                if (trackableResult.getStatus() == TrackableResult.STATUS.TRACKED
                    || trackableResult.getStatus() == TrackableResult.STATUS.EXTENDED_TRACKED)
                {
                    currentTrackableResult = trackableResult;
                    String trackableName = currentTrackableResult.getTrackable().getName();

                    modelMatrix = Tool.convertPose2GLMatrix(currentTrackableResult.getPose());
                    renderModel(projectionMatrix, devicePoseMatrix.getData(), modelMatrix.getData());

                    SampleUtils.checkGLError("renderFrame(), tracking");

                    hasModelTargetResult = true;
                }
                else if (trackableResult.getStatusInfo()
                        == TrackableResult.STATUS_INFO.NO_DETECTION_RECOMMENDING_GUIDANCE)
                {
                    modelRequiringGuidance = modelTarget;
                }
                else if (trackableResult.getStatusInfo() == TrackableResult.STATUS_INFO.INITIALIZING)
                {
                    modelsBeingSearchedFor.add(modelTarget);
                }
            }
        }

        // If we are not tracking any models, see if any models are initializing
        if (!hasModelTargetResult)
        {
            // Is there a model that requires alignment? If so, render the guide view for that model
            if (modelRequiringGuidance != null)
            {
                String modelId = modelRequiringGuidance.getUniqueTargetId();
                int activeIndex = modelRequiringGuidance.getActiveGuideViewIndex();

                // Determine if new model has been recognized
                if (mCurrentModelTargetId == null || !mCurrentModelTargetId.equals(modelId)
                    || mActiveGuideViewIndex != activeIndex)
                {
                    guideViewOrModelChanged = true;
                    mCurrentModelTargetId = modelId;
                    mActiveGuideViewIndex = activeIndex;
                }

                // Update the guide view texture if necessary
                if (guideViewOrModelChanged && !modelRequiringGuidance.getGuideViews().empty()
                        && mActiveGuideViewIndex > -1)
                {
                    activeGuideView = modelRequiringGuidance.getGuideViews().at(mActiveGuideViewIndex);

                    if (activeGuideView != null && activeGuideView.getImage() != null
                        && state.getCameraCalibration() != null)
                    {
                        textureImage = activeGuideView.getImage();
                        updateGuideViewTexture(textureImage, state);
                    }
                }

                // Render guide view
                renderGuideView(modelRequiringGuidance);
                SampleUtils.checkGLError("renderFrame(), no trackables");
            }
            else if (modelsBeingSearchedFor.size() > 0)
            {
                // Get the current time fraction and scale it by 2*PI, then normalize the cos result,
                // so we get an alpha value between 0 and 1
                double currentTime = (double) System.currentTimeMillis() / 2000f;
                int nonFractionPart = (int) currentTime;
                double fractionPart = (currentTime - nonFractionPart) + 0.5f;

                float alphaValue = (float) (Math.cos(fractionPart * Math.PI * 2) + 1f) / 2f;

                ArrayList<Integer> symbolicGuideViewIndices = new ArrayList<>();

                for (ModelTarget model: modelsBeingSearchedFor)
                {
                    Integer newIndex = mSymbolicGuideViewIndices.get(model.getName());

                    if (newIndex != null)
                    {
                        symbolicGuideViewIndices.add(newIndex);
                    }
                }

                if (symbolicGuideViewIndices.isEmpty())
                {
                    Log.e(LOGTAG, "Could not find any symbolic guide views");
                }
                else
                {
                    int modelToShow = nonFractionPart % symbolicGuideViewIndices.size();
                    int symbolicGuideViewIndex = symbolicGuideViewIndices.get(modelToShow);


                    float aspectRatio = mPlaneWidth / mPlaneHeight;
                    boolean isLandscape = mActivity.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
                    Vec2F orthoScale = new Vec2F((!isLandscape ? aspectRatio : 1) * 0.66f, (isLandscape ? aspectRatio : 1) * 0.66f);
                    int textureHandle = mTextures.get(symbolicGuideViewIndex).mTextureID[0];

                    renderPlaneTextured(orthoScale, textureHandle, alphaValue);
                }
            }
        }

        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
    }

    private void renderModel(float[] projectionMatrix, float[] viewMatrix, float[] modelMatrix)
    {
        int modelTextureIndex = 0;

        SampleApplicationV3DModel currentModel = mLanderModel;

        if (!mAreModelsLoaded)
        {
            return;
        }

        float[] modelViewProjection = new float[16];

        // Combine device pose (view matrix) with model matrix
        Matrix.multiplyMM(modelMatrix, 0, viewMatrix, 0, modelMatrix, 0);

        // Do the final combination with the projection matrix
        Matrix.multiplyMM(modelViewProjection, 0, projectionMatrix, 0, modelMatrix, 0);

        // activate the shader program and bind the vertex/normal/tex coords
        GLES20.glUseProgram(shaderProgramID);

        GLES20.glDisable(GLES20.GL_CULL_FACE);

        GLES20.glVertexAttribPointer(vertexHandle, 3, GLES20.GL_FLOAT,
                false, 0, currentModel.getVertices());
        GLES20.glVertexAttribPointer(normalHandle, 3, GLES20.GL_FLOAT, false, 0, currentModel.getNormals());
        GLES20.glVertexAttribPointer(textureCoordHandle, 2,
                GLES20.GL_FLOAT, false, 0, currentModel.getTexCoords());

        GLES20.glEnableVertexAttribArray(vertexHandle);
        GLES20.glEnableVertexAttribArray(normalHandle);
        GLES20.glEnableVertexAttribArray(textureCoordHandle);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,
                mTextures.get(modelTextureIndex).mTextureID[0]);

        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false,
                modelViewProjection, 0);
        GLES20.glUniformMatrix4fv(mvMatrixHandle, 1, false, modelMatrix, 0);

        float[] inverseMatrix = new float[16];
        Matrix.invertM(inverseMatrix, 0, modelMatrix, 0);

        float[] normalMatrix = new float[16];
        Matrix.transposeM(normalMatrix, 0, inverseMatrix, 0);

        GLES20.glUniformMatrix4fv(normalMatrixHandle, 1, false, normalMatrix, 0);

        GLES20.glUniform4f(lightPositionHandle, 0.2f, -1.0f, 0.5f, -1.0f);
        GLES20.glUniform4f(lightColorHandle, 0.5f, 0.5f, 0.5f, 1.0f);
        GLES20.glUniform4f(colorCorrectionHandle,
                mColorCorrection.getData()[0],
                mColorCorrection.getData()[1],
                mColorCorrection.getData()[2],
                mColorCorrection.getData()[3]);
        GLES20.glUniform1f(intensityCorrectionHandle, mIntensityCorrection);

        GLES20.glUniform1i(texSampler2DHandle, 0);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0,
                currentModel.getNumObjectVertex());

        GLES20.glDisableVertexAttribArray(vertexHandle);
        GLES20.glDisableVertexAttribArray(normalHandle);
        GLES20.glDisableVertexAttribArray(textureCoordHandle);
    }

    private void renderGuideView(ModelTarget modelTarget)
    {
        if (modelTarget == null || guideViewHandle < 0)
        {
            Log.e(LOGTAG, "Could not render guide view, " +
                    "invalid model target or guide view handle");
            return;
        }

        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glDisable(GLES20.GL_CULL_FACE);

        renderPlaneTextured(mGuideViewScale, guideViewHandle, 1.0f);

        GLES20.glEnable(GLES20.GL_CULL_FACE);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
    }


    private void renderPlaneTextured(Vec2F scale, int textureHandle, float alpha)
    {
        Vec4F color = new Vec4F(1.0f, 1.0f, 1.0f, alpha);

        float[] modelViewProjectionMatrix = new float[16];
        Matrix.orthoM(modelViewProjectionMatrix, 0, -0.5f, 0.5f, -0.5f, 0.5f, 0, 1);

        Matrix44F modelMatrix = SampleMath.Matrix44FIdentity();
        float[] scaledModelMatrixArray = modelMatrix.getData();

        Matrix.scaleM(scaledModelMatrixArray, 0, scale.getData()[0], scale.getData()[1], 1.0f);
        Matrix.multiplyMM(modelViewProjectionMatrix, 0, modelViewProjectionMatrix, 0, scaledModelMatrixArray, 0);

        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandle);

        GLES20.glEnableVertexAttribArray(planeVertexHandle);
        GLES20.glVertexAttribPointer(planeVertexHandle, 3, GLES20.GL_FLOAT, false, 0, mPlane.getVertices());

        GLES20.glEnableVertexAttribArray(planeTextureCoordHandle);
        GLES20.glVertexAttribPointer(planeTextureCoordHandle, 2, GLES20.GL_FLOAT, false, 0, mPlane.getTexCoords());

        GLES20.glUseProgram(planeShaderProgramID);
        GLES20.glUniformMatrix4fv(planeMvpMatrixHandle, 1, false, modelViewProjectionMatrix, 0);
        GLES20.glUniform4f(planeColorHandle, color.getData()[0], color.getData()[1], color.getData()[2], color.getData()[3]);
        GLES20.glUniform1i(planeTexSampler2DHandle, 0);

        // Draw
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, Plane.NUM_PLANE_INDEX, GLES20.GL_UNSIGNED_SHORT, mPlane.getIndices());

        // disable input data structures

        GLES20.glDisableVertexAttribArray(planeTextureCoordHandle);
        GLES20.glDisableVertexAttribArray(planeVertexHandle);
        GLES20.glUseProgram(0);

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

        GLES20.glDisable(GLES20.GL_BLEND);
    }

    private void updateGuideViewTexture(Image textureImage, State state)
    {
        if (textureImage == null)
        {
            Log.e(LOGTAG, "Guide view image null");
            return;
        }

        if (guideViewHandle > 0)
        {
            SampleUtils.deleteTexture(guideViewHandle);
        }
        guideViewHandle = SampleUtils.createTexture(textureImage);

        float guideViewAspectRatio = (float)textureImage.getWidth() / textureImage.getHeight();
        Point size = new Point();
        mActivity.getWindowManager().getDefaultDisplay().getSize(size);

        float cameraAspectRatio = (float)size.x / size.y;

        // doing this calculation in world space, at an assumed camera near plane distance of 0.01f;
        // this is also what the Unity rendering code does
        float planeDistance = 0.01f;
        float fieldOfView = state.getCameraCalibration().getFieldOfViewRads().getData()[1];
        float nearPlaneHeight = (float)(2.0f * planeDistance * Math.tan(fieldOfView * 0.5f));
        float nearPlaneWidth = nearPlaneHeight * cameraAspectRatio;
        
        if(guideViewAspectRatio >= 1.0f && cameraAspectRatio >= 1.0f) // guideview landscape, camera landscape
        {
            // scale so that the long side of the camera (width)
            // is the same length as guideview width
            mPlaneWidth = nearPlaneWidth;
            mPlaneHeight = mPlaneWidth / guideViewAspectRatio;
        }
        
        else if(guideViewAspectRatio < 1.0f && cameraAspectRatio < 1.0f) // guideview portrait, camera portrait
        {
            // scale so that the long side of the camera (height)
            // is the same length as guideview height
            mPlaneHeight = nearPlaneHeight;
            mPlaneWidth = mPlaneHeight * guideViewAspectRatio;
        }
        else if (cameraAspectRatio < 1.0f) // guideview landscape, camera portrait
        {
            // scale so that the long side of the camera (height)
            // is the same length as guideview width
            mPlaneWidth = nearPlaneHeight;
            mPlaneHeight = mPlaneWidth / guideViewAspectRatio;
        }
        else // guideview portrait, camera landscape
        {
            // scale so that the long side of the camera (width)
            // is the same length as guideview height
            mPlaneHeight = nearPlaneWidth;
            mPlaneWidth = mPlaneHeight * guideViewAspectRatio;
        }

        // normalize world space plane sizes into view space again
        mGuideViewScale = new Vec2F(mPlaneWidth / nearPlaneWidth, -mPlaneHeight / nearPlaneHeight);
    }

    public void setTextures(Vector<Texture> textures, HashMap<String, Integer> symbolicTextureIndices)
    {
        mTextures = textures;
        mSymbolicGuideViewIndices = symbolicTextureIndices;
    }

    boolean areModelsLoaded()
    {
        return mAreModelsLoaded;
    }


    private void setIsTargetCurrentlyTracked(TrackableResultList trackableResultList)
    {
        for(TrackableResult result : trackableResultList)
        {
            // Check the tracking status for result types
            // other than DeviceTrackableResult. ie: ModelTargetResult
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
