/*===============================================================================
Copyright (c) 2020 PTC Inc. All Rights Reserved.

Vuforia is a trademark of PTC Inc., registered in the United States and other
countries.
===============================================================================*/

package com.vuforia.engine.CoreSamples.app.GroundPlane;

import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.AsyncTask;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.View;

import com.vuforia.Anchor;
import com.vuforia.AnchorList;
import com.vuforia.AnchorResult;
import com.vuforia.Device;
import com.vuforia.DeviceTrackableResult;
import com.vuforia.HitTestResult;
import com.vuforia.HitTestResultList;
import com.vuforia.Illumination;
import com.vuforia.Matrix34F;
import com.vuforia.Matrix44F;
import com.vuforia.PositionalDeviceTracker;
import com.vuforia.SmartTerrain;
import com.vuforia.State;
import com.vuforia.Tool;
import com.vuforia.TrackableResult;
import com.vuforia.TrackableResultList;
import com.vuforia.TrackerManager;
import com.vuforia.Vec2F;
import com.vuforia.Vec3F;
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
import java.util.Vector;


/**
 * The renderer class for the GroundPlane sample.
 *
 * In the renderFrame() function you can render augmentations to display over the Target
 */
public class GroundPlaneRenderer extends SampleRendererBase implements SampleAppRendererControl
{
    private static final String LOGTAG = "GroundPlaneRenderer";

    private final GroundPlane mActivity;

    private int planeShaderProgramID;
    private int planeVertexHandle;
    private int planeTextureCoordHandle;
    private int planeMvpMatrixHandle;
    private int planeTexSampler2DHandle;
    private int planeColorHandle;

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

    // This plane shows a real-world surface if one is detected using SmartTerrain
    // If no real-world surface is detected, this plane is displayed on the screen surface
    private Plane mPlane;

    // Ground Plane augmentations
    private SampleApplicationV3DModel mAstronaut, mDrone, mFurniture;

    // Tracking state confidence & degradation
    private int mCurrentStatusInfo = TrackableResult.STATUS_INFO.UNKNOWN;
    private boolean mIsTargetCurrentlyTracked = false;

    // Flags specific to the furniture model
    private boolean mIsModelTranslating = false;
    private boolean mIsModelRotating = false;

    private boolean mModelsAreLoaded = false;
    private boolean initializedModelShaders = false;

    // Ground Plane modes
    static final int SAMPLE_APP_INTERACTIVE_MODE = 0;
    static final int SAMPLE_APP_MIDAIR_MODE = 1;
    static final int SAMPLE_APP_FURNITURE_MODE = 2;

    static final int INSTRUCTION_POINT_TO_GROUND = 0;
    static final int INSTRUCTION_TAP_TO_PLACE = 1;
    static final int INSTRUCTION_GESTURES_INSTRUCTIONS = 2;
    static final int INSTRUCTION_PRODUCT_PLACEMENT = 3;
    static final int INSTRUCTION_LIMITED_MOVE_DEVICE = 4;
    static final int INSTRUCTION_UNDEFINED = 5;

    private static final int PRODUCT_PLACEMENT_STATE_TRANSLATING = 0;
    private static final int PRODUCT_PLACEMENT_STATE_IDLE = 1;

    private static final int PLANE_2D_RETICLE_TEXTURE_INDEX = 2;
    private static final int MIDAIR_RETICLE_TEXTURE_INDEX = 3;
    private static final int PLANE_3D_RETICLE_TEXTURE_INDEX = 4;
    private static final int SHADOW_TEXTURE_INDEX = 5;
    private static final int TRANSLATE_TEXTURE_INDEX = 6;
    private static final int ROTATE_TEXTURE_INDEX = 7;

    // Device and Smart Terrain poses
    private Matrix44F mDevicePoseMatrix;
    private Matrix44F mHitTestPoseMatrix;
    private Matrix44F mMidAirPoseMatrix;
    private Matrix44F mFurniturePoseMatrix;
    private Matrix44F mReticlePose;

    private Vec2F translateCoords;
    private Vec2F mLastTranslationCoords;

    // Anchor for registering content with latest hit test result
    private Anchor mHitTestAnchor, mMidAirAnchor, mFurnitureAnchor;
    private boolean mIsAnchorResultAvailable;
    private boolean mMidAirAnchorCreationEnabled;
    private boolean mSurfaceAnchorCreationEnabled;
    private boolean mIsDeviceResultAvailable;
    private boolean mSetDroneNewPosition;
    private boolean mIsFurniturePlaced;
    private boolean mRepositionFurniture;

    private int mProductPlacementState;
    private float mProductScale;
    private float mProductRotation;
    private float mNewProductRotation;

    int mCurrentMode;

    private boolean mPlaceAnchorContent = false;

    private final static String HIT_TEST_ANCHOR_NAME = "hitTestAnchor";
    private final static String MID_AIR_ANCHOR_NAME = "midAirAnchor";
    private final static String FURNITURE_ANCHOR_NAME = "furnitureAnchor";

    // Define an assumed device height above the plane where you'd like to place content.
    // The world coordinate system will be scaled accordingly to meet this device height value
    // once you create the first successful anchor from a HitTestResult. If your users are adults
    // that will place something on the floor use appx. 1.4m. For a tabletop experience use appx. 0.5m.
    // In apps targeted for kids reduce the assumptions to ~80% of these values.
    private final static float DEFAULT_HEIGHT_ABOVE_GROUND = 1.4f;  // In meters

    GroundPlaneRenderer(GroundPlane activity, SampleApplicationSession session)
    {
        mActivity = activity;
        vuforiaAppSession = session;

        // SampleAppRenderer used to encapsulate the use of RenderingPrimitives setting
        // the device mode AR/VR and stereo mode
        mSampleAppRenderer = new SampleAppRenderer(this, mActivity, vuforiaAppSession.getVideoMode(),
                0.1f, 10f);
    }


    public void setActive(boolean active)
    {
        mSampleAppRenderer.setActive(active);
    }


    @Override
    public void initRendering()
    {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, Vuforia.requiresAlpha() ? 0.0f
                : 1.0f);

        mActivity.setInstructionsState(INSTRUCTION_UNDEFINED);

        for (Texture t : mTextures)
        {
            GLES20.glGenTextures(1, t.mTextureID, 0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, t.mTextureID[0]);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT);

            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, t.mWidth, t.mHeight, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, t.mData);
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

        if (!mModelsAreLoaded)
        {
            LoadModelTask astronautModelTask = new LoadModelTask(this);
            astronautModelTask.execute("astronaut.v3d");

            LoadModelTask droneModelTask = new LoadModelTask(this);
            droneModelTask.execute("drone.v3d");

            LoadModelTask chairModelTask = new LoadModelTask(this);
            chairModelTask.execute("GroundPlane/chair.v3d");
        }

        mDevicePoseMatrix = SampleMath.Matrix44FIdentity();
        mMidAirPoseMatrix = SampleMath.Matrix44FIdentity();
        mHitTestPoseMatrix = SampleMath.Matrix44FIdentity();
        mFurniturePoseMatrix = SampleMath.Matrix44FIdentity();
        mReticlePose = SampleMath.Matrix44FIdentity();

        translateCoords = new Vec2F();
        mLastTranslationCoords = new Vec2F();

        mIsAnchorResultAvailable = false;
        mIsDeviceResultAvailable = false;
        mSetDroneNewPosition = false;
        mIsFurniturePlaced = false;
        mRepositionFurniture = false;

        mProductPlacementState = PRODUCT_PLACEMENT_STATE_IDLE;
        mProductRotation = 0;
        mNewProductRotation = 0;
        mProductScale = 0.5f;
    }


    private static class LoadModelTask extends AsyncTask<String, Integer, Boolean>
    {
        private final WeakReference<GroundPlaneRenderer> rendererRef;

        LoadModelTask(GroundPlaneRenderer gpRenderer)
        {
            rendererRef = new WeakReference<>(gpRenderer);
        }

        protected Boolean doInBackground(String... params)
        {
            boolean isModelLoaded = false;

            GroundPlaneRenderer renderer = rendererRef.get();
            GroundPlane activity = renderer.mActivity;

            switch(params[0])
            {
                case "astronaut.v3d":
                {
                    renderer.mAstronaut = new SampleApplicationV3DModel(false);
                    isModelLoaded = renderer.mAstronaut.loadModel(activity.getResources().getAssets(), params[0]);

                    break;
                }
                case "drone.v3d":
                {
                    renderer.mDrone = new SampleApplicationV3DModel(false);
                    isModelLoaded = renderer.mDrone.loadModel(activity.getResources().getAssets(), params[0]);

                    break;
                }
                case "GroundPlane/chair.v3d":
                {
                    renderer.mFurniture = new SampleApplicationV3DModel(true);
                    isModelLoaded = renderer.mFurniture.loadModel(activity.getResources().getAssets(), params[0]);
                    renderer.mFurniture.setTransparency(.5f);

                    break;
                }
            }

            return isModelLoaded;
        }

        protected void onPostExecute(Boolean result)
        {
            GroundPlaneRenderer renderer = rendererRef.get();
            GroundPlane activity = renderer.mActivity;

            // Hide the Loading Dialog
            activity.showProgressIndicator(false);
            activity.mGroundPlaneLayout.setVisibility(View.VISIBLE);
            renderer.mModelsAreLoaded = renderer.mFurniture != null
                    && renderer.mDrone != null && renderer.mAstronaut != null
                    && renderer.mFurniture.isLoaded() && renderer.mDrone.isLoaded()
                    && renderer.mAstronaut.isLoaded();
        }
    }


    void unloadModels()
    {
        if (mAstronaut != null)
        {
            mAstronaut.unloadModel();
            mAstronaut = null;
        }

        if (mFurniture != null)
        {
            mFurniture.unloadModel();
            mFurniture = null;
        }

        if (mDrone != null)
        {
            mDrone.unloadModel();
            mDrone = null;
        }
    }


    public void updateRenderingPrimitives()
    {
        mSampleAppRenderer.updateRenderingPrimitives();
    }


    // The render function.
    // This function is called from the SampleAppRenderer by using the RenderingPrimitives views.
    // The state is owned by SampleAppRenderer which is controlling its lifecycle.
    // NOTE: State should not be cached outside this method.
    public void renderFrame(State state, float[] projectionMatrix)
    {
        boolean render3DReticle = false;
        boolean renderAstronaut = false;
        boolean renderDrone = false;
        boolean renderFurniture = false;

        // Prevent rendering augmentations until models are loaded
        if (!mModelsAreLoaded)
        {
            return;
        }

        if (!initializedModelShaders && mFurniture != null)
        {
            mFurniture.initShaders();
            initializedModelShaders = true;
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

        // Renders video background replacing Renderer.DrawVideoBackground()
        mSampleAppRenderer.renderVideoBackground();

        GLES20.glEnable(GLES20.GL_DEPTH_TEST);

        GLES20.glEnable(GLES20.GL_CULL_FACE);
        GLES20.glCullFace(GLES20.GL_BACK);
        GLES20.glFrontFace(GLES20.GL_CCW);   // Back camera

        if (state.getTrackableResults().empty())
        {
            Log.i(LOGTAG, "No trackables");
        }
        else
        {
            Matrix34F devicePoseTemp = new Matrix34F();
            boolean furnitureAnchorExists = false;

            TrackableResultList trackableResultList = state.getTrackableResults();

            // Determine if target is currently being tracked
            setIsTargetCurrentlyTracked(trackableResultList);

            // Iterate through trackable results and render any augmentations
            for (TrackableResult result : trackableResultList)
            {
                Matrix44F modelViewMatrix = Tool.convertPose2GLMatrix(result.getPose());

                Matrix44F projMatrix = new Matrix44F();
                projMatrix.setData(projectionMatrix);

                // Look for a device pose to be able to move around in world space
                if (result.isOfType(DeviceTrackableResult.getClassType()))
                {
                    devicePoseTemp.setData(result.getPose().getData());
                    mDevicePoseMatrix = SampleMath.Matrix44FTranspose(SampleMath.Matrix44FInverse(modelViewMatrix));
                    mIsDeviceResultAvailable = true;

                    setStatusInfoUpdate(result.getStatusInfo());

                    int deviceTrackerResultStatus = result.getStatus();
                    int deviceTrackerResultStatusInfo = result.getStatusInfo();

                    // Ensure anchor is only created under good conditions
                    mMidAirAnchorCreationEnabled = (deviceTrackerResultStatus == DeviceTrackableResult.STATUS.TRACKED ||
                                                deviceTrackerResultStatus == DeviceTrackableResult.STATUS.EXTENDED_TRACKED) &&
                                                (deviceTrackerResultStatusInfo == DeviceTrackableResult.STATUS_INFO.NORMAL);

                    mSurfaceAnchorCreationEnabled = mMidAirAnchorCreationEnabled;
                }
                // Look for an anchor pose so that we can place the model there
                else if (result.isOfType(AnchorResult.getClassType()))
                {
                    mIsAnchorResultAvailable = true;

                    if (result.getTrackable().getName().equals(HIT_TEST_ANCHOR_NAME))
                    {
                        renderAstronaut = true;
                        mHitTestPoseMatrix = modelViewMatrix;
                    }

                    if (result.getTrackable().getName().equals(MID_AIR_ANCHOR_NAME))
                    {
                        renderDrone = true;
                        mMidAirPoseMatrix = modelViewMatrix;
                    }

                    if (result.getTrackable().getName().equals(FURNITURE_ANCHOR_NAME))
                    {
                        furnitureAnchorExists = true;

                        if(!mRepositionFurniture)
                        {
                            renderFurniture = true;
                        }

                        mIsFurniturePlaced = true;

                        if (mIsModelTranslating)
                        {
                            updateFurnitureMatrix(state, translateCoords);
                        }
                        else
                        {
                            mFurniturePoseMatrix = modelViewMatrix;
                        }
                    }
                }
            }

            if(!furnitureAnchorExists)
            {
                mIsFurniturePlaced = false;
            }

            if (mPlaceAnchorContent)
            {
                if (mCurrentMode == SAMPLE_APP_INTERACTIVE_MODE
                        || mCurrentMode == SAMPLE_APP_FURNITURE_MODE)
                {
                    if (!isModelTranslating() && mSurfaceAnchorCreationEnabled)
                    {
                        if (mRepositionFurniture)
                        {
                            performHitTest(state, mLastTranslationCoords.getData()[0], mLastTranslationCoords.getData()[1], true);
                            mRepositionFurniture = false;
                        }
                        else
                        {
                            performHitTest(state, 0.5f, 0.5f, true);
                        }
                    }
                    else
                    {
                        Log.e(LOGTAG, "Could not create surface anchor");
                    }
                }
                else if (mMidAirAnchorCreationEnabled)
                {
                    mSetDroneNewPosition = true;
                }
                else
                {
                    Log.e(LOGTAG, "Could not create mid air anchor");
                }

                mPlaceAnchorContent = false;
            }

            // Should we set a new position for the drone?
            if (mSetDroneNewPosition)
            {
                Matrix34F midAirPose = new Matrix34F();
                midAirPose.setData(devicePoseTemp.getData());

                // Matrix to translate drone at a given distance in front of camera
                Matrix34F translationMat = new Matrix34F();
                float[] translationArray = new float[12];
                translationArray[0] = 1;
                translationArray[5] = 1;
                translationArray[10] = 1;

                translationMat.setData(translationArray);
                Tool.setTranslation(translationMat, new Vec3F(0f, 0f, -3f));

                midAirPose = Tool.multiply(midAirPose, translationMat);

                // We remove the orientation from the device pose to create the anchor
                float[] midAirPoseArray = midAirPose.getData();
                midAirPoseArray[0] = 1;
                midAirPoseArray[1] = 0;
                midAirPoseArray[2] = 0;

                midAirPoseArray[4] = 0;
                midAirPoseArray[5] = 1;
                midAirPoseArray[6] = 0;
                
                midAirPoseArray[8] = 0;
                midAirPoseArray[9] = 0;
                midAirPoseArray[10] = 1;
                midAirPose.setData(midAirPoseArray);

                // Create an anchor for the drone
                createMidAirAnchor(midAirPose);
                mSetDroneNewPosition = false;
            }

            // To render a placement-reticle we need to perform a hit test to get an intersection point
            // with ground plane. Until an anchor from HitTestResult is created successfully the device
            // is assumed to be at deviceHeight along gravity vector above the plane.
            if (!mIsAnchorResultAvailable || mCurrentMode == SAMPLE_APP_INTERACTIVE_MODE
                    || mCurrentMode == SAMPLE_APP_FURNITURE_MODE)
            {
                // Perform a hit test on the center of screen
                if(!(renderFurniture && mCurrentMode == SAMPLE_APP_FURNITURE_MODE))
                    render3DReticle = performHitTest(state, 0.5f, 0.5f, false);
            }

            boolean shouldPointToGround = (!render3DReticle && mCurrentMode == SAMPLE_APP_INTERACTIVE_MODE) ||
                        (!renderFurniture && !render3DReticle && mCurrentMode == SAMPLE_APP_FURNITURE_MODE);

            if (!mMidAirAnchorCreationEnabled || !mSurfaceAnchorCreationEnabled)
            {
                mActivity.setInstructionsState(INSTRUCTION_LIMITED_MOVE_DEVICE);
            }
            else if(shouldPointToGround)
            {
                mActivity.setInstructionsState(INSTRUCTION_POINT_TO_GROUND);
            }
            else if(mCurrentMode == SAMPLE_APP_FURNITURE_MODE && renderFurniture)
            {
                if(mProductPlacementState == PRODUCT_PLACEMENT_STATE_IDLE)
                {
                    mActivity.setInstructionsState(INSTRUCTION_GESTURES_INSTRUCTIONS);
                }
                else
                {
                    mActivity.setInstructionsState(INSTRUCTION_PRODUCT_PLACEMENT);
                }
            }
            else
            {
                mActivity.setInstructionsState(INSTRUCTION_TAP_TO_PLACE);
            }

            // If we have the device and anchor results, we translate and scale the models
            // so they are positioned at the center of the reticle.
            // For the drone, we also want to align it vertically since it is in mid-air
            if (mIsDeviceResultAvailable && mIsAnchorResultAvailable)
            {
                if (renderFurniture)
                {
                    float[] chairMV = mFurniturePoseMatrix.getData();
                    float[] chairMVP = new float[16];

                    Matrix.translateM(chairMV, 0, 0f, 0f, 0);
                    Matrix.rotateM(chairMV, 0, mProductRotation + mNewProductRotation, 0.0f, 1.0f, 0.0f);
                    Matrix.scaleM(chairMV, 0, mProductScale, mProductScale, mProductScale);

                    Matrix.multiplyMM(chairMV, 0, mDevicePoseMatrix.getData(), 0, chairMV, 0);
                    Matrix.multiplyMM(chairMVP, 0, projectionMatrix, 0, chairMV, 0);

                    float shadowScale = mProductScale * 1.0f;
                    float gesturesScale = mProductScale * 2.0f;
                    float[] shadowMV = mFurniturePoseMatrix.getData();
                    float[] gesturesMV = mFurniturePoseMatrix.getData();

                    Matrix.rotateM(shadowMV, 0, -90, 1.0f, 0.0f, 0.0f);
                    Matrix.scaleM(shadowMV, 0, shadowScale, shadowScale, shadowScale);
                    Matrix.rotateM(gesturesMV, 0, -90, 1.0f, 0.0f, 0.0f);
                    Matrix.scaleM(gesturesMV, 0, gesturesScale, gesturesScale, gesturesScale);

                    GLES20.glDisable(GLES20.GL_DEPTH_TEST);

                    Matrix44F projMatrix = new Matrix44F();
                    projMatrix.setData(projectionMatrix);
                    Matrix44F shadowMVMatrix = new Matrix44F();
                    shadowMVMatrix.setData(shadowMV);
                    Matrix44F gesturesMVMatrix = new Matrix44F();
                    gesturesMVMatrix.setData(gesturesMV);

                    // Renders the shadow that will be placed underneath the furniture
                    renderPlaneTexturedWithProjectionMatrix(projMatrix, shadowMVMatrix, mTextures.get(SHADOW_TEXTURE_INDEX).mTextureID[0], true, false);

                    // If any gestures are being performed on the furniture,
                    // render the corresponding texture
                    if(mProductPlacementState != PRODUCT_PLACEMENT_STATE_IDLE)
                    {
                        int gestureTexture = isModelRotating() ? ROTATE_TEXTURE_INDEX : TRANSLATE_TEXTURE_INDEX;
                        renderPlaneTexturedWithProjectionMatrix(projMatrix, gesturesMVMatrix, mTextures.get(gestureTexture).mTextureID[0], false, false);
                    }

                    GLES20.glEnable(GLES20.GL_DEPTH_TEST);

                    mFurniture.setRenderingColorCorrection(mColorCorrection, mIntensityCorrection);
                    mFurniture.render(chairMV, chairMVP);
                }

                if (renderAstronaut)
                {
                    float[] astronautMV = mHitTestPoseMatrix.getData();
                    Matrix.translateM(astronautMV, 0, -0.30f, 0, 0);
                    Matrix.scaleM(astronautMV, 0, 10f, 10f, 10f);

                    renderModelV3D(mAstronaut, astronautMV,
                            projectionMatrix, SAMPLE_APP_INTERACTIVE_MODE);
                }

                if (renderDrone)
                {
                    float[] droneMV = mMidAirPoseMatrix.getData();
                    Matrix.translateM(droneMV, 0, -0.75f, -0.375f, -0.75f);
                    Matrix.scaleM(droneMV, 0, 10f, 10f, 10f);
                    renderModelV3D(mDrone, droneMV,
                            projectionMatrix, SAMPLE_APP_MIDAIR_MODE);
                }

            }
        }

        // Render the reticle depending on which mode we are in
        if ((!render3DReticle || !mIsAnchorResultAvailable) && !(renderFurniture && mCurrentMode == SAMPLE_APP_FURNITURE_MODE))
        {
            render2DReticle();
        }
        else if (mCurrentMode == SAMPLE_APP_INTERACTIVE_MODE)
        {
            Matrix44F projMatrix = new Matrix44F();
            projMatrix.setData(projectionMatrix);
            renderReticleWithProjectionMatrix(projMatrix, false);
        }
        else if(mCurrentMode == SAMPLE_APP_FURNITURE_MODE && !mIsFurniturePlaced)
        {
            float[] furnitureMVPMatrix = mReticlePose.getData();
            float[] furnitureMV = mReticlePose.getData();
            Matrix.scaleM(furnitureMV, 0, mProductScale, mProductScale, mProductScale);
            float[] poseMatrix =  new float[16];
            Matrix.multiplyMM(poseMatrix, 0, mDevicePoseMatrix.getData(), 0, furnitureMV, 0);

            Matrix.multiplyMM(furnitureMVPMatrix, 0, projectionMatrix, 0, poseMatrix, 0);

            float[] shadowMV = mReticlePose.getData();
            float shadowScale = mProductScale * 1.0f;

            Matrix.rotateM(shadowMV, 0, -90, 1.0f, 0.0f, 0);
            Matrix.scaleM(shadowMV, 0 , shadowScale, shadowScale, shadowScale);

            Matrix44F shadowMVMatrix = new Matrix44F();
            shadowMVMatrix.setData(shadowMV);

            Matrix44F projMatrix = new Matrix44F();
            projMatrix.setData(projectionMatrix);

            // Disable depth test so the shadow does not occlude the furniture
            GLES20.glDisable(GLES20.GL_DEPTH_TEST);
            renderPlaneTexturedWithProjectionMatrix(projMatrix, shadowMVMatrix, mTextures.get(SHADOW_TEXTURE_INDEX).mTextureID[0], true, false);
            GLES20.glEnable(GLES20.GL_DEPTH_TEST);

            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
            GLES20.glBlendEquation(GLES20.GL_FUNC_ADD);
            mFurniture.setRenderingColorCorrection(mColorCorrection, mIntensityCorrection);
            mFurniture.render(furnitureMV, furnitureMVPMatrix);

        }

        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glDisable(GLES20.GL_CULL_FACE);
    }


    private void render2DReticle()
    {
        // Calculate aspect ratio
        DisplayMetrics metrics = new DisplayMetrics();
        mActivity.getWindowManager().getDefaultDisplay().getMetrics(metrics);
        float aspectRatio = (float) metrics.widthPixels / (float) metrics.heightPixels;

        // Create orthographic matrix
        float[] reticleProj = SampleMath.Matrix44FIdentity().getData();

        float orthoScale = 1.25f;
        Matrix.orthoM(reticleProj, 0, aspectRatio * -orthoScale, aspectRatio * orthoScale, -orthoScale, orthoScale, 1f, -1f);

        Matrix44F reticleProjection = new Matrix44F();
        reticleProjection.setData(reticleProj);

        mReticlePose = SampleMath.Matrix44FIdentity();

        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        renderReticleWithProjectionMatrix(reticleProjection, true);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
    }


    private void renderReticleWithProjectionMatrix(Matrix44F projectionMatrix, boolean isReticle2D)
    {
        int textureIndex = (mCurrentMode == SAMPLE_APP_INTERACTIVE_MODE || mCurrentMode == SAMPLE_APP_FURNITURE_MODE)
                ? PLANE_2D_RETICLE_TEXTURE_INDEX : MIDAIR_RETICLE_TEXTURE_INDEX;

        if (mCurrentMode == SAMPLE_APP_INTERACTIVE_MODE && !isReticle2D)
        {
            textureIndex = PLANE_3D_RETICLE_TEXTURE_INDEX;
        }

        Matrix44F reticleMV = new Matrix44F();
        reticleMV.setData(mReticlePose.getData());

        // We rotate the reticle for it sit on the plane where we intend to render the reticle instead of intersecting it
        float[] reticleTransform = reticleMV.getData();
        Matrix.rotateM(reticleTransform, 0, 90, -1, 0, 0);

        reticleMV.setData(reticleTransform);

        renderPlaneTexturedWithProjectionMatrix(projectionMatrix, reticleMV, mTextures.get(textureIndex).mTextureID[0], false, isReticle2D);
    }


    private void renderPlaneTexturedWithProjectionMatrix(Matrix44F projectionMatrix, Matrix44F modelViewMatrix, int textureHandle, boolean isSubstractingColors, boolean isRender2D)
    {
        float[] modelViewProjectionMatrix = projectionMatrix.getData();
        float[] poseMatrix = SampleMath.Matrix44FIdentity().getData();

        if (!isRender2D)
        {
            Matrix.multiplyMM(poseMatrix, 0, mDevicePoseMatrix.getData(), 0, modelViewMatrix.getData(), 0);
        }

        Matrix.multiplyMM(modelViewProjectionMatrix, 0, projectionMatrix.getData(), 0, poseMatrix, 0);

        GLES20.glEnable(GLES20.GL_BLEND);
        if(!isSubstractingColors)
        {
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
            GLES20.glBlendEquation(GLES20.GL_FUNC_ADD);
        }
        else
        {
            GLES20.glBlendFuncSeparate(GLES20.GL_SRC_ALPHA, GLES20.GL_DST_ALPHA, GLES20.GL_SRC_ALPHA, GLES20.GL_DST_ALPHA);
            GLES20.glBlendEquationSeparate(GLES20.GL_FUNC_REVERSE_SUBTRACT, GLES20.GL_FUNC_ADD);
        }

        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandle);

        GLES20.glEnableVertexAttribArray(planeVertexHandle);
        GLES20.glVertexAttribPointer(planeVertexHandle, 3, GLES20.GL_FLOAT, false, 0, mPlane.getVertices());

        GLES20.glEnableVertexAttribArray(planeTextureCoordHandle);
        GLES20.glVertexAttribPointer(planeTextureCoordHandle, 2, GLES20.GL_FLOAT, false, 0, mPlane.getTexCoords());

        GLES20.glUseProgram(planeShaderProgramID);
        GLES20.glUniformMatrix4fv(planeMvpMatrixHandle, 1, false, modelViewProjectionMatrix, 0);
        GLES20.glUniform4f(planeColorHandle, 1, 1, 1, 1);
        GLES20.glUniform1i(planeTexSampler2DHandle, 0);

        GLES20.glDrawElements(GLES20.GL_TRIANGLES, Plane.NUM_PLANE_INDEX, GLES20.GL_UNSIGNED_SHORT, mPlane.getIndices());

        GLES20.glDisableVertexAttribArray(planeTextureCoordHandle);
        GLES20.glDisableVertexAttribArray(planeVertexHandle);
        GLES20.glUseProgram(0);

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

        GLES20.glBlendFuncSeparate(GLES20.GL_SRC_ALPHA, GLES20.GL_DST_ALPHA, GLES20.GL_SRC_ALPHA, GLES20.GL_DST_ALPHA);
        GLES20.glBlendEquationSeparate(GLES20.GL_FUNC_ADD, GLES20.GL_FUNC_ADD);
        GLES20.glDisable(GLES20.GL_BLEND);

        SampleUtils.checkGLError("renderPlaneTextured");
    }


    private void renderModelV3D(SampleApplicationV3DModel model,
                                float[] modelPoseMatrix, float[] projectionMatrix, int mode)
    {
        float[] modelViewProjection = new float[16];
        float[] poseMatrix = new float[16];

        Matrix.setIdentityM(poseMatrix, 0);

        Matrix.multiplyMM(poseMatrix, 0, mDevicePoseMatrix.getData(), 0, modelPoseMatrix, 0);
        Matrix.multiplyMM(modelViewProjection, 0, projectionMatrix, 0, poseMatrix, 0);

        GLES20.glUseProgram(shaderProgramID);

        GLES20.glVertexAttribPointer(vertexHandle, 3, GLES20.GL_FLOAT, false, 0, model.getVertices());
        GLES20.glVertexAttribPointer(normalHandle, 3, GLES20.GL_FLOAT, false, 0, model.getNormals());
        GLES20.glVertexAttribPointer(textureCoordHandle, 2, GLES20.GL_FLOAT, false, 0, model.getTexCoords());

        GLES20.glEnableVertexAttribArray(vertexHandle);
        GLES20.glEnableVertexAttribArray(normalHandle);
        GLES20.glEnableVertexAttribArray(textureCoordHandle);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextures.elementAt(mode).mTextureID[0]);

        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, modelViewProjection, 0);
        GLES20.glUniformMatrix4fv(mvMatrixHandle, 1, false, poseMatrix, 0);

        float[] inverseMatrix = new float[16];
        Matrix.invertM(inverseMatrix, 0, poseMatrix, 0);

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

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, model.getNumObjectVertex());

        GLES20.glDisableVertexAttribArray(vertexHandle);
        GLES20.glDisableVertexAttribArray(normalHandle);
        GLES20.glDisableVertexAttribArray(textureCoordHandle);

        SampleUtils.checkGLError("Render Model V3D");
    }

    void handleTap()
    {
        if (!(mCurrentMode == SAMPLE_APP_FURNITURE_MODE && mIsFurniturePlaced))
        {
            mPlaceAnchorContent = true;
        }
    }

    private boolean performHitTest(State state, float normalTouchPointX, float normalTouchPointY,
                                   boolean createAnchor)
    {
        Log.i(LOGTAG, "Perform hit test with normalized touch point ("
                + normalTouchPointX + ", " + normalTouchPointY + ")");

        TrackerManager trackerManager = TrackerManager.getInstance();
        PositionalDeviceTracker deviceTracker = (PositionalDeviceTracker) trackerManager.getTracker(PositionalDeviceTracker.getClassType());
        SmartTerrain smartTerrain = (SmartTerrain) trackerManager.getTracker(SmartTerrain.getClassType());

        if (deviceTracker == null || smartTerrain == null)
        {
            Log.e(LOGTAG, "Failed to perform hit test, trackers not initialized");
            return false;
        }

        Vec2F hitTestPoint = new Vec2F(normalTouchPointX, normalTouchPointY);
        int hitTestHint = SmartTerrain.HITTEST_HINT.HITTEST_HINT_NONE; // hit test hint is currently unused

        // A hit test is performed for a given State at normalized screen coordinates.
        // The deviceHeight is a developer provided assumption as explained in the
        // definition of DEFAULT_HEIGHT_ABOVE_GROUND.
        HitTestResultList hitTestResults = smartTerrain.hitTest(hitTestPoint, hitTestHint, state, DEFAULT_HEIGHT_ABOVE_GROUND);

        if (!hitTestResults.empty())
        {
            // Use first HitTestResult
            final HitTestResult hitTestResult = hitTestResults.at(0);

            if (createAnchor)
            {
                if(state.getDeviceTrackableResult().getStatus() == TrackableResult.STATUS.TRACKED)
                    createSurfaceAnchor(hitTestResult);
            }

            mReticlePose = Tool.convertPose2GLMatrix(hitTestResult.getPose());
            mIsAnchorResultAvailable = true;
            return true;
        }
        else
        {
            Log.i(LOGTAG, "Hit test returned no results");
            return false;
        }
    }


    private void createSurfaceAnchor(HitTestResult hitTestResult)
    {
        TrackerManager trackerManager = TrackerManager.getInstance();
        PositionalDeviceTracker deviceTracker = (PositionalDeviceTracker) trackerManager.getTracker(PositionalDeviceTracker.getClassType());

        if (mCurrentMode == SAMPLE_APP_INTERACTIVE_MODE)
        {
            // Destroy previous hit test anchor if needed
            if (mHitTestAnchor != null)
            {
                Log.i(LOGTAG, "Destroying hit test anchor with name " + HIT_TEST_ANCHOR_NAME);
                boolean result = deviceTracker.destroyAnchor(mHitTestAnchor);
                Log.i(LOGTAG, "Hit test anchor " + (result ? "successfully destroyed" : "failed to destroy"));
            }

            mHitTestAnchor = deviceTracker.createAnchor(HIT_TEST_ANCHOR_NAME, hitTestResult);

            if (mHitTestAnchor != null)
            {
                Log.i(LOGTAG, "Successfully created hit test anchor with name " + mHitTestAnchor.getName());
            }
            else
            {
                Log.e(LOGTAG, "Failed to create hit test anchor");
            }

            AnchorList anchors = deviceTracker.getAnchors();
            Log.i(LOGTAG, "Number of anchors: " + anchors.size());
        }
        else if (mCurrentMode == SAMPLE_APP_FURNITURE_MODE)
        {
            // Destroy previous hit test anchor if needed
            if (mFurnitureAnchor != null)
            {
                Log.i(LOGTAG, "Destroying hit test anchor with name " + FURNITURE_ANCHOR_NAME);
                boolean result = deviceTracker.destroyAnchor(mFurnitureAnchor);
                Log.i(LOGTAG, "Hit test anchor " + (result ? "successfully destroyed" : "failed to destroy"));
            }

            mFurnitureAnchor = deviceTracker.createAnchor(FURNITURE_ANCHOR_NAME, hitTestResult);

            if (mFurnitureAnchor != null)
            {
                Log.i(LOGTAG, "Successfully created hit test anchor with name " + mFurnitureAnchor.getName());
                mFurniture.setTransparency(1.0f);
                mActivity.setInstructionsState(PRODUCT_PLACEMENT_STATE_IDLE);
            }
            else
            {
                Log.e(LOGTAG, "Failed to create hit test anchor");
            }
            AnchorList anchors = deviceTracker.getAnchors();
            Log.i(LOGTAG, "Number of anchors: " + anchors.size());
        }
    }


    private void createMidAirAnchor(Matrix34F anchorPoseMatrix)
    {
        Log.i(LOGTAG, "Create Mid Air Anchor");

        TrackerManager trackerManager = TrackerManager.getInstance();
        PositionalDeviceTracker deviceTracker = (PositionalDeviceTracker)
                trackerManager.getTracker(PositionalDeviceTracker.getClassType());

        if (mMidAirAnchor != null)
        {
            Log.i(LOGTAG, "Destroying hit test anchor with name " + MID_AIR_ANCHOR_NAME);
            boolean result = deviceTracker.destroyAnchor(mMidAirAnchor);
            Log.i(LOGTAG, "Hit test anchor " + (result ? "successfully destroyed" : "failed to destroy"));
        }

        mMidAirAnchor = deviceTracker.createAnchor(MID_AIR_ANCHOR_NAME, anchorPoseMatrix);

        if (mMidAirAnchor != null)
        {
            Log.i(LOGTAG, "Successfully created hit test anchor with name " + mMidAirAnchor.getName());
        }
        else
        {
            Log.e(LOGTAG, "Failed to create mid air anchor");
        }
        AnchorList anchors = deviceTracker.getAnchors();
        Log.i(LOGTAG, "Number of anchors: " + anchors.size());
    }


    private void updateFurnitureMatrix(State state, Vec2F screenCoords)
    {
        TrackerManager trackerManager = TrackerManager.getInstance();
        PositionalDeviceTracker deviceTracker = (PositionalDeviceTracker) trackerManager.getTracker(PositionalDeviceTracker.getClassType());
        SmartTerrain smartTerrain = (SmartTerrain) trackerManager.getTracker(SmartTerrain.getClassType());

        if (deviceTracker == null || smartTerrain == null)
        {
            Log.e(LOGTAG, "Failed to perform hit test, trackers not initialized");
            return;
        }

        Vec2F hitTestPoint = new Vec2F(screenCoords.getData()[0], screenCoords.getData()[1]);
        int hitTestHint = SmartTerrain.HITTEST_HINT.HITTEST_HINT_NONE; // hit test hint is currently unused
        mLastTranslationCoords.setData(screenCoords.getData());

        // A hit test is performed for a given State at normalized screen coordinates.
        // The deviceHeight is an developer provided assumption as explained on
        // definition of DEFAULT_HEIGHT_ABOVE_GROUND.
        HitTestResultList hitTestResults = smartTerrain.hitTest(hitTestPoint, hitTestHint, state, DEFAULT_HEIGHT_ABOVE_GROUND);

        if (hitTestResults.empty())
        {
            Log.i(LOGTAG, "Hit test returned no results");
            return;
        }

        // Use first HitTestResult
        final HitTestResult hitTestResult = hitTestResults.at(0);
        mFurniturePoseMatrix = Tool.convertPose2GLMatrix(hitTestResult.getPose());
    }


    private void setStatusInfoUpdate(int status)
    {
        if(mCurrentStatusInfo != status)
        {
            mCurrentStatusInfo = status;
            mActivity.setStatusInfo(status);
        }
    }


    void resetGroundPlane(boolean resetTrackers)
    {
        mDevicePoseMatrix = SampleMath.Matrix44FIdentity();
        mMidAirPoseMatrix = SampleMath.Matrix44FIdentity();
        mHitTestPoseMatrix = SampleMath.Matrix44FIdentity();
        mFurniturePoseMatrix = SampleMath.Matrix44FIdentity();
        mReticlePose = SampleMath.Matrix44FIdentity();

        mIsAnchorResultAvailable = false;
        mIsDeviceResultAvailable = false;
        mSetDroneNewPosition = false;
        mIsFurniturePlaced = false;
        mRepositionFurniture = false;

        mFurniture.setTransparency(0.5f);

        if (resetTrackers)
        {
            resetTrackers();
        }

        mHitTestAnchor = null;
        mMidAirAnchor = null;
        mFurnitureAnchor = null;

        mCurrentStatusInfo = TrackableResult.STATUS_INFO.UNKNOWN;

        setMode(SAMPLE_APP_FURNITURE_MODE);
    }


    private void resetTrackers()
    {
        TrackerManager trackerManager = TrackerManager.getInstance();
        PositionalDeviceTracker deviceTracker = (PositionalDeviceTracker) trackerManager.
                getTracker(PositionalDeviceTracker.getClassType());
        SmartTerrain smartTerrain = (SmartTerrain) trackerManager.getTracker(SmartTerrain.getClassType());

        if (deviceTracker == null || smartTerrain == null)
        {
            Log.e(LOGTAG, "Failed to reset trackers, trackers not initialized");
            return;
        }

        // Anchor not valid anymore after stop() call, anchor poses should be discarded
        if (mHitTestAnchor != null)
        {
            deviceTracker.destroyAnchor(mHitTestAnchor);
            mHitTestAnchor = null;
        }

        if (mMidAirAnchor != null)
        {
            deviceTracker.destroyAnchor(mMidAirAnchor);
            mMidAirAnchor = null;
        }

        if (mFurnitureAnchor != null)
        {
            deviceTracker.destroyAnchor(mFurnitureAnchor);
            mFurnitureAnchor = null;
        }

        smartTerrain.stop();
        smartTerrain.start();
        deviceTracker.reset();
    }


    public void setTextures(Vector<Texture> textures)
    {
        mTextures = textures;
    }


    public void setMode(int mode)
    {
        if (mode >= SAMPLE_APP_INTERACTIVE_MODE
                && mode <= SAMPLE_APP_FURNITURE_MODE)
        {
            mCurrentMode = mode;
        }
        else
        {
            Log.e(LOGTAG, "Invalid mode: " + mode);
        }

        // Set UI
        mActivity.setModeUI(mode);
    }

    boolean isModelRotating()
    {
        return mIsModelRotating;
    }

    void setModelRotating(boolean modelRotating)
    {
        mIsModelRotating = modelRotating;

        mProductPlacementState = modelRotating ? PRODUCT_PLACEMENT_STATE_TRANSLATING : PRODUCT_PLACEMENT_STATE_IDLE;
        mActivity.setInstructionsState(mProductPlacementState);

        if(!modelRotating)
        {
            mProductRotation += mNewProductRotation;
            if(mProductRotation < 0)
                mProductRotation += 360;
            mProductRotation %= 360;
        }

        mNewProductRotation = 0;
    }

    void setCurrentRotation(float angle)
    {
        mNewProductRotation = angle;
    }


    boolean isModelTranslating()
    {
        return mIsModelTranslating;
    }


    Anchor getFurnitureAnchor()
    {
        return mFurnitureAnchor;
    }


    void setModelTranslating(boolean modelTranslating)
    {
        mIsModelTranslating = modelTranslating;

        mProductPlacementState = modelTranslating ? PRODUCT_PLACEMENT_STATE_TRANSLATING : PRODUCT_PLACEMENT_STATE_IDLE;
        mActivity.setInstructionsState(mProductPlacementState);

        if (!mIsModelTranslating)
        {
            mPlaceAnchorContent = true;
            mRepositionFurniture = true;
        }
    }


    void setTranslateCoordinates(Vec2F coords)
    {
        DisplayMetrics metrics = new DisplayMetrics();
        Display defaultDisplay =  mActivity.getWindowManager().getDefaultDisplay();
        defaultDisplay.getMetrics(metrics);
        int deviceRotation = defaultDisplay.getRotation();

        float normalizedX;
        float normalizedY;

        switch(deviceRotation)
        {
            // Portrait
            case Surface.ROTATION_0:
                normalizedX = coords.getData()[1] / metrics.heightPixels;
                normalizedY = 1 - (coords.getData()[0] / metrics.widthPixels);
                break;

            // Landscape
            case Surface.ROTATION_90:
                normalizedX = coords.getData()[0] / metrics.widthPixels;
                normalizedY = coords.getData()[1] / metrics.heightPixels;
                break;

            // Upside-down portrait
            case Surface.ROTATION_180:
                normalizedX = 1 - (coords.getData()[1] / metrics.heightPixels);
                normalizedY = coords.getData()[0] / metrics.widthPixels;
                break;

            // Reverse-landscape
            case Surface.ROTATION_270:
                normalizedX = 1 - (coords.getData()[0] / metrics.widthPixels);
                normalizedY = 1 - (coords.getData()[1] / metrics.heightPixels);
                break;

            // Default to landscape mode
            default:
                normalizedX = coords.getData()[0] / metrics.widthPixels;
                normalizedY = coords.getData()[1] / metrics.heightPixels;
                break;
        }

        float[] normalizedCoords = {normalizedX, normalizedY};

        translateCoords.setData(normalizedCoords);
    }


    boolean areModelsLoaded()
    {
        return mModelsAreLoaded;
    }


    private void setIsTargetCurrentlyTracked(TrackableResultList trackableResultList)
    {
        for(TrackableResult result : trackableResultList)
        {
            // Check the tracking status for result types
            // other than DeviceTrackableResult. ie: AnchorResult
            if (!result.isOfType(DeviceTrackableResult.getClassType()))
            {
                int currentStatus = result.getStatus();
                int currentStatusInfo = result.getStatusInfo();

                // The anchor is currently being tracked if the status is TRACKED|NORMAL
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
