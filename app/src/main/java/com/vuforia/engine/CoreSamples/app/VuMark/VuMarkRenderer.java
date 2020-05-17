/*===============================================================================
Copyright (c) 2020 PTC Inc. All Rights Reserved.

Copyright (c) 2012-2014 Qualcomm Connected Experiences, Inc. All Rights Reserved.

Vuforia is a trademark of PTC Inc., registered in the United States and other
countries.
===============================================================================*/

package com.vuforia.engine.CoreSamples.app.VuMark;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.Log;

import com.vuforia.CameraCalibration;
import com.vuforia.Device;
import com.vuforia.DeviceTrackableResult;
import com.vuforia.Image;
import com.vuforia.InstanceId;
import com.vuforia.Matrix44F;
import com.vuforia.PIXEL_FORMAT;
import com.vuforia.Renderer;
import com.vuforia.State;
import com.vuforia.Tool;
import com.vuforia.TrackableResult;
import com.vuforia.TrackableResultList;
import com.vuforia.Vec2F;
import com.vuforia.Vec3F;
import com.vuforia.VuMarkTarget;
import com.vuforia.VuMarkTargetResult;
import com.vuforia.VuMarkTemplate;
import com.vuforia.Vuforia;
import com.vuforia.engine.SampleApplication.SampleAppRenderer;
import com.vuforia.engine.SampleApplication.SampleAppRendererControl;
import com.vuforia.engine.SampleApplication.SampleApplicationSession;
import com.vuforia.engine.SampleApplication.SampleRendererBase;
import com.vuforia.engine.SampleApplication.utils.CubeShaders;
import com.vuforia.engine.SampleApplication.utils.LoadingDialogHandler;
import com.vuforia.engine.SampleApplication.utils.SampleMath;
import com.vuforia.engine.SampleApplication.utils.SampleUtils;
import com.vuforia.engine.SampleApplication.utils.Texture;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Vector;


/**
 * The renderer class for the VuMark sample.
 *
 * In the renderFrame() function you can render augmentations to display over the Target
 */
public class VuMarkRenderer extends SampleRendererBase implements SampleAppRendererControl
{
    private static final String LOGTAG = "VuMarkRenderer";

    private final VuMark mActivity;

    private int shaderProgramID;
    private int vertexHandle;
    private int textureCoordHandle;
    private int mvpMatrixHandle;
    private int texSampler2DHandle;
    private int calphaHandle;

    private Renderer mRenderer;

    private double t0;

    // Object to be rendered
    private final Plane mPlaneObj;

    // Ratio to apply so that the augmentation surrounds the VuMark
    private static final float VUMARK_SCALE = 1.02f;
    private String currentVumarkIdOnCard;

    private boolean mIsTargetCurrentlyTracked = false;

    VuMarkRenderer(VuMark activity, SampleApplicationSession session)
    {
        mActivity = activity;
        vuforiaAppSession = session;
        t0 = -1.0;

        mPlaneObj = new Plane();

        // SampleAppRenderer used to encapsulate the use of RenderingPrimitives setting
        // the device mode AR/VR and stereo mode
        mSampleAppRenderer = new SampleAppRenderer(this, mActivity,
                vuforiaAppSession.getVideoMode(),  .01f, 100f);
    }


    public void setActive(boolean active)
    {
        mSampleAppRenderer.setActive(active);
    }


    @Override
    public void initRendering()
    {
        mRenderer = Renderer.getInstance();

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
        calphaHandle = GLES20.glGetUniformLocation(shaderProgramID,
                "calpha");

        mActivity.loadingDialogHandler.sendEmptyMessage(LoadingDialogHandler.HIDE_LOADING_DIALOG);
    }


    // Determines whether the VuMark augmentation is displayed or not, causing a blinking effect
    private float blinkVumark(boolean reset)
    {
        if (reset || t0 < 0.0f)
        {
            t0 = System.currentTimeMillis();
        }
        if (reset) 
        {
            return 0.0f;
        }
        double time = System.currentTimeMillis();
        double delta = (time-t0);

        if (delta > 1000.0f)
        {
            return 1.0f;
        }

        if ((delta < 300.0f) || ((delta > 500.0f) && (delta < 800.0f)))
        {
            return 1.0f;
        }

        return 0.0f;
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
        // Renders video background replacing Renderer.DrawVideoBackground()
        mSampleAppRenderer.renderVideoBackground();

        GLES20.glEnable(GLES20.GL_DEPTH_TEST);

        GLES20.glEnable(GLES20.GL_CULL_FACE);
        GLES20.glCullFace(GLES20.GL_BACK);

        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        boolean gotVuMark = false;
        boolean isMainVuMark;

        String markerType = "";
        String markerValue = "";
        Bitmap markerBitmap = null;
        TrackableResult vuMarkToDisplay = null;

        // Set the device pose matrix as identity
        Matrix44F devicePoseMatrix = SampleMath.Matrix44FIdentity();

        // Read device pose from the state and create a corresponding view matrix (inverse of the device pose)
        if (state.getDeviceTrackableResult() != null)
        {
            int statusInfo = state.getDeviceTrackableResult().getStatusInfo();
            int trackerStatus = state.getDeviceTrackableResult().getStatus();

            mActivity.checkForRelocalization(statusInfo);

            if (trackerStatus != TrackableResult.STATUS.NO_POSE)
            {
                Matrix44F modelMatrix = Tool.convertPose2GLMatrix(state.getDeviceTrackableResult().getPose());

                // We transpose here because Matrix44FInverse returns a transposed matrix
                devicePoseMatrix = SampleMath.Matrix44FTranspose(SampleMath.Matrix44FInverse(modelMatrix));
            }
        }

        TrackableResultList trackableResultList = state.getTrackableResults();
        if (trackableResultList.size() > 1)
        {
            float minimumDistance = Float.MAX_VALUE;
            CameraCalibration cameraCalibration = state.getCameraCalibration();
            Vec2F screenSize = cameraCalibration.getSize();
            Vec2F screenCenter = new Vec2F(screenSize.getData()[0] / 2.0f, screenSize.getData()[1] / 2.0f);

            for (TrackableResult result : trackableResultList)
            {
                if (result.isOfType(VuMarkTargetResult.getClassType()) && result.getStatus() != TrackableResult.STATUS.LIMITED)
                {
                    Vec3F point = new Vec3F(0, 0, 0);
                    Vec2F projection = Tool.projectPoint(cameraCalibration, result.getPose(), point);

                    float distance = distanceSquared(projection, screenCenter);
                    if (distance < minimumDistance)
                    {
                        minimumDistance = distance;
                        vuMarkToDisplay = result;
                    }
                }
            }
        }

        // Determine if target is currently being tracked
        setIsTargetCurrentlyTracked(trackableResultList);

        // Iterate through trackable results and render any augmentations
        for (TrackableResult result : trackableResultList)
        {
            Matrix44F modelViewMatrix_Vuforia = Tool
                .convertPose2GLMatrix(result.getPose());
            float[] modelViewMatrix = modelViewMatrix_Vuforia.getData();

            if (result.isOfType(VuMarkTargetResult.getClassType()))
            {
                VuMarkTargetResult vmtResult = (VuMarkTargetResult) result;
                VuMarkTarget vmTgt = (VuMarkTarget) vmtResult.getTrackable();

                VuMarkTemplate vmTmp = vmTgt.getTemplate();

                // user data contains the the SVG layer corresponding to the contour
                // of the VuMark template
                // look at the iOS sample app to see how this data can be used to dynamically
                // render an OpenGL object on top of the contour.
                // String userData = vmTmp.getVuMarkUserData();

                InstanceId instanceId = vmTgt.getInstanceId();
                isMainVuMark = ((vuMarkToDisplay == null) || (vuMarkToDisplay.equals(result)));
                gotVuMark = true;

                // Set the VuMark parameters for the one we've detected
                if (isMainVuMark)
                {
                    markerValue = instanceIdToValue(instanceId);
                    markerType = instanceIdToType(instanceId);
                    Image instanceImage = vmTgt.getInstanceImage();
                    markerBitmap = getBitMapFromImage(instanceImage);

                    // Hide the augmentation and reset the blink animation
                    // if this VuMark is not the one we've detected
                    if (! markerValue.equalsIgnoreCase(currentVumarkIdOnCard))
                    {
                        mActivity.hideCard();
                        blinkVumark(true);
                    }
                }

                // Add a translation to recenter the augmentation
                // at the center of the VuMark, with respect to the origin
                Vec2F origin = vmTmp.getOrigin();
                float translX = -origin.getData()[0];
                float translY = -origin.getData()[1];
                Matrix.translateM(modelViewMatrix, 0, translX, translY, 0);

                // Scales the plane relative to the target
                float vumarkWidth = vmTgt.getSize().getData()[0];
                float vumarkHeight = vmTgt.getSize().getData()[1];
                Matrix.scaleM(modelViewMatrix, 0, vumarkWidth * VUMARK_SCALE,
                        vumarkHeight * VUMARK_SCALE, 1.0f);
            }
            else
            {
                continue;
            }

            renderModel(projectionMatrix, devicePoseMatrix.getData(), modelViewMatrix, isMainVuMark);
            SampleUtils.checkGLError("VuMark Render Frame");
        }

        if(gotVuMark)
        {
            // If we have a detection, let's make sure
            // the card is visible
            mActivity.showCard(markerType, markerValue, markerBitmap);
            currentVumarkIdOnCard = markerValue;
        }
        else
        {
            // We reset the state of the animation so that
            // it triggers next time a vumark is detected
            blinkVumark(true);
            // We also reset the value of the current value of the vumark on card
            // so that we hide and show the mumark if we redetect the same vumark instance
            currentVumarkIdOnCard = null;
        }
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glDisable(GLES20.GL_BLEND);

        mRenderer.end();
    }


    private void renderModel(float[] projectionMatrix, float[] viewMatrix, float[] modelMatrix, boolean isMainVuMark)
    {
        int textureIndex = 0;
        float[] modelViewProjection = new float[16];

        // Combine device pose (view matrix) with model matrix
        Matrix.multiplyMM(modelMatrix, 0, viewMatrix, 0, modelMatrix, 0);

        // Do the final combination with the projection matrix
        Matrix.multiplyMM(modelViewProjection, 0, projectionMatrix, 0, modelMatrix, 0);

        // activate the shader program and bind the vertex/normal/tex coords
        GLES20.glUseProgram(shaderProgramID);

        GLES20.glVertexAttribPointer(vertexHandle, 3, GLES20.GL_FLOAT,
                false, 0, mPlaneObj.getVertices());
        GLES20.glVertexAttribPointer(textureCoordHandle, 2,
                GLES20.GL_FLOAT, false, 0, mPlaneObj.getTexCoords());

        GLES20.glEnableVertexAttribArray(vertexHandle);
        GLES20.glEnableVertexAttribArray(textureCoordHandle);

        // activate texture 0, bind it, and pass to shader
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,
                mTextures.get(textureIndex).mTextureID[0]);
        GLES20.glUniform1i(texSampler2DHandle, 0);
        GLES20.glUniform1f(calphaHandle, isMainVuMark ? blinkVumark(false) : 1.0f);

        // pass the model view matrix to the shader
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false,
                modelViewProjection, 0);

        // finally draw the plane
        GLES20.glDrawElements(GLES20.GL_TRIANGLES,
                mPlaneObj.getNumObjectIndex(), GLES20.GL_UNSIGNED_SHORT,
                mPlaneObj.getIndices());

        // disable the enabled arrays
        GLES20.glDisableVertexAttribArray(vertexHandle);
        GLES20.glDisableVertexAttribArray(textureCoordHandle);
    }


    private float distanceSquared(Vec2F p1, Vec2F p2)
    {
        return (float) (Math.pow(p1.getData()[0] - p2.getData()[0], 2.0) +
                        Math.pow(p1.getData()[1] - p2.getData()[1], 2.0));
    }


    public void setTextures(Vector<Texture> textures)
    {
        mTextures = textures;
    }


    private String instanceIdToType(InstanceId instanceId)
    {
        switch(instanceId.getDataType())
        {
            case InstanceId.ID_DATA_TYPE.STRING:
                return "String";
            
            case InstanceId.ID_DATA_TYPE.BYTES:
                return "Bytes";
            
            case InstanceId.ID_DATA_TYPE.NUMERIC:
                return "Numeric";
        }

        return "Unknown";
    }


    private static final String hexTable = "0123456789abcdef";

    // Parse the VuMark value into a String
    private String instanceIdToValue(InstanceId instanceId)
    {
        ByteBuffer instanceIdBuffer = instanceId.getBuffer();
        byte[] instanceIdBytes = new byte[instanceIdBuffer.remaining()];
        instanceIdBuffer.get(instanceIdBytes);

        String instanceIdStr;
        switch(instanceId.getDataType())
        {
            case InstanceId.ID_DATA_TYPE.STRING:
                instanceIdStr = new String(instanceIdBytes, Charset.forName("US-ASCII"));
                break;

            case InstanceId.ID_DATA_TYPE.BYTES:
                StringBuilder instanceIdStrBuilder = new StringBuilder();

                for (int i = instanceIdBytes.length - 1; i >= 0; i--)
                {
                    byte byteValue = instanceIdBytes[i];

                    instanceIdStrBuilder.append(hexTable.charAt((byteValue & 0xf0) >> 4));
                    instanceIdStrBuilder.append(hexTable.charAt(byteValue & 0x0f));
                }

                instanceIdStr = instanceIdStrBuilder.toString();
                break;

            case InstanceId.ID_DATA_TYPE.NUMERIC:
                BigInteger instanceIdNumeric = instanceId.getNumericValue();
                Long instanceIdLong = instanceIdNumeric.longValue();
                instanceIdStr = Long.toString(instanceIdLong);
                break;

            default:
                return "Unknown";
        }

        return instanceIdStr;
    }


    private Bitmap getBitMapFromImage(Image image)
    {
        // we handle only RGB888 in this example
        if (image.getFormat() != PIXEL_FORMAT.RGBA8888) {
            // the default 'stock' image will be used for the instance image
            return null;
        }
        ByteBuffer pixels = image.getPixels();
        byte[] imgData = new byte[pixels.remaining()];
        pixels.get(imgData, 0, imgData.length);
        int width = image.getWidth();
        int height = image.getHeight();
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

        int[] colors = new int[width * height];
        int r,g,b,a;
        for (int ci = 0; ci < colors.length; ci++)
        {
            r = (0xFF & imgData[4*ci]);
            g = (0xFF & imgData[4*ci+1]);
            b = (0xFF & imgData[4*ci+2]);
            a = (0xFF & imgData[4*ci+3]);
            colors[ci] = Color.argb(a, r, g, b);
        }

        bitmap.setPixels(colors, 0, width, 0, 0, width, height);
        return bitmap;
    }

    private void setIsTargetCurrentlyTracked(TrackableResultList trackableResultList)
    {
        for(TrackableResult result : trackableResultList)
        {
            // Check the tracking status for result types
            // other than DeviceTrackableResult. ie: VuMarkTargetResult
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
