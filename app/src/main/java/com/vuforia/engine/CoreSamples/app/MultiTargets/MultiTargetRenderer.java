/*===============================================================================
Copyright (c) 2020 PTC Inc. All Rights Reserved.

Copyright (c) 2012-2014 Qualcomm Connected Experiences, Inc. All Rights Reserved.

Vuforia is a trademark of PTC Inc., registered in the United States and other 
countries.
===============================================================================*/

package com.vuforia.engine.CoreSamples.app.MultiTargets;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.Log;

import com.vuforia.Device;
import com.vuforia.Matrix44F;
import com.vuforia.MultiTargetResult;
import com.vuforia.Renderer;
import com.vuforia.State;
import com.vuforia.Tool;
import com.vuforia.TrackableResult;
import com.vuforia.TrackableResultList;
import com.vuforia.Vuforia;
import com.vuforia.engine.SampleApplication.SampleAppRenderer;
import com.vuforia.engine.SampleApplication.SampleAppRendererControl;
import com.vuforia.engine.SampleApplication.SampleApplicationSession;
import com.vuforia.engine.SampleApplication.SampleRendererBase;
import com.vuforia.engine.SampleApplication.utils.CubeObject;
import com.vuforia.engine.SampleApplication.utils.CubeShaders;
import com.vuforia.engine.SampleApplication.utils.SampleUtils;
import com.vuforia.engine.SampleApplication.utils.Texture;

import java.util.Vector;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;


/**
 * The renderer class for the MultiTargets sample.
 *
 * In the renderFrame() function you can render augmentations to display over the Target
 */
public class MultiTargetRenderer extends SampleRendererBase implements SampleAppRendererControl
{
    private static final String LOGTAG = "MultiTargetRenderer";
    
    private int shaderProgramID;
    
    private int vertexHandle;
    private int textureCoordHandle;
    private int mvpMatrixHandle;
    private int texSampler2DHandle;
    
    private double prevTime;
    private float rotateAngle;

    // Bowl and spoon will be rotated around the cube (multi target)
    private final CubeObject cubeObject = new CubeObject();
    private final BowlAndSpoonObject bowlAndSpoonObject = new BowlAndSpoonObject();
    
    // Constants:
    private final static float kCubeScaleX = 0.12f * 0.75f / 2.0f;
    private final static float kCubeScaleY = 0.12f * 1.00f / 2.0f;
    private final static float kCubeScaleZ = 0.12f * 0.50f / 2.0f;

    private final static float kBowlScaleX = 0.12f * 0.15f;
    private final static float kBowlScaleY = 0.12f * 0.15f;
    private final static float kBowlScaleZ = 0.12f * 0.15f;
    
    
    MultiTargetRenderer(MultiTargets activity, SampleApplicationSession session)
    {
        vuforiaAppSession = session;

        // SampleAppRenderer used to encapsulate the use of RenderingPrimitives setting
        // the device mode AR/VR and stereo mode
        mSampleAppRenderer = new SampleAppRenderer(this, activity,
                vuforiaAppSession.getVideoMode(), 0.010f, 5f);
    }


    public void setActive(boolean active)
    {
        mSampleAppRenderer.setActive(active);
    }


    @Override
    public void initRendering()
    {
        Log.d(LOGTAG, "initRendering");
        
        // Define clear color
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, Vuforia.requiresAlpha() ? 0.0f
            : 1.0f);
        
        // Now generate the OpenGL texture objects and add settings
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
        SampleUtils.checkGLError("Check gl errors prior render Frame");

        // Renders video background replacing Renderer.DrawVideoBackground()
        mSampleAppRenderer.renderVideoBackground();
        
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        TrackableResultList trackableResultList = state.getTrackableResults();

        // Did we find any trackables this frame?
        if (!trackableResultList.empty())
        {
            // Get the trackable:
            TrackableResult result = null;

            // Browse results searching for the MultiTarget
            for (TrackableResult tr : trackableResultList)
            {
                if (tr.isOfType(MultiTargetResult.getClassType()))
                {
                    result = tr;
                    break;
                }
            }
            
            // If it was not found, exit
            if (result == null || result.getStatus() == TrackableResult.STATUS.LIMITED)
            {
                // Clean up and leave
                GLES20.glDisable(GLES20.GL_BLEND);
                GLES20.glDisable(GLES20.GL_DEPTH_TEST);
                
                Renderer.getInstance().end();
                return;
            }
            
            Matrix44F modelViewMatrix_Vuforia = Tool
                .convertPose2GLMatrix(result.getPose());
            float[] modelViewMatrix = modelViewMatrix_Vuforia.getData();
            
            float[] modelViewProjection = new float[16];
            Matrix.scaleM(modelViewMatrix, 0, kCubeScaleX, kCubeScaleY,
                kCubeScaleZ);
            Matrix.multiplyMM(modelViewProjection, 0, projectionMatrix, 0, modelViewMatrix, 0);
            
            GLES20.glUseProgram(shaderProgramID);
            
            // Draw the cube:
            GLES20.glEnable(GLES20.GL_CULL_FACE);
            GLES20.glCullFace(GLES20.GL_BACK);

            GLES20.glVertexAttribPointer(vertexHandle, 3, GLES20.GL_FLOAT,
                false, 0, cubeObject.getVertices());
            GLES20.glVertexAttribPointer(textureCoordHandle, 2,
                GLES20.GL_FLOAT, false, 0, cubeObject.getTexCoords());
            
            GLES20.glEnableVertexAttribArray(vertexHandle);
            GLES20.glEnableVertexAttribArray(textureCoordHandle);
            
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,
                mTextures.get(0).mTextureID[0]);
            GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false,
                modelViewProjection, 0);
            GLES20.glUniform1i(texSampler2DHandle, 0);
            GLES20.glDrawElements(GLES20.GL_TRIANGLES,
                cubeObject.getNumObjectIndex(), GLES20.GL_UNSIGNED_SHORT,
                cubeObject.getIndices());
            
            GLES20.glDisable(GLES20.GL_CULL_FACE);
            
            // Draw the bowl:
            modelViewMatrix = modelViewMatrix_Vuforia.getData();
            
            // Remove the following line to make the bowl stop spinning:
            animateBowl(modelViewMatrix);
            
            Matrix.translateM(modelViewMatrix, 0, 0.0f, -0.50f * 0.12f,
                0.00135f * 0.12f);
            Matrix.rotateM(modelViewMatrix, 0, -90.0f, 1.0f, 0, 0);
            
            Matrix.scaleM(modelViewMatrix, 0, kBowlScaleX, kBowlScaleY,
                kBowlScaleZ);
            Matrix.multiplyMM(modelViewProjection, 0, projectionMatrix, 0, modelViewMatrix, 0);
            
            GLES20.glVertexAttribPointer(vertexHandle, 3, GLES20.GL_FLOAT,
                false, 0, bowlAndSpoonObject.getVertices());
            GLES20.glVertexAttribPointer(textureCoordHandle, 2,
                GLES20.GL_FLOAT, false, 0, bowlAndSpoonObject.getTexCoords());
            
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,
                mTextures.get(1).mTextureID[0]);
            GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false,
                modelViewProjection, 0);
            GLES20.glDrawElements(GLES20.GL_TRIANGLES,
                bowlAndSpoonObject.getNumObjectIndex(),
                GLES20.GL_UNSIGNED_SHORT, bowlAndSpoonObject.getIndices());
            
            GLES20.glDisableVertexAttribArray(vertexHandle);
            GLES20.glDisableVertexAttribArray(textureCoordHandle);
            
            SampleUtils.checkGLError("MultiTargets renderFrame");
            
        }
        
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        
        Renderer.getInstance().end();
    }
    
    
    private void animateBowl(float[] modelViewMatrix)
    {
        double time = System.currentTimeMillis(); // Get real time difference
        float dt = (float) (time - prevTime) / 1000; // from frame to frame
        
        rotateAngle += dt * 180.0f / 3.1415f; // Animate angle based on time
        rotateAngle %= 360;
        Log.d(LOGTAG, "Delta animation time: " + rotateAngle);
        
        Matrix.rotateM(modelViewMatrix, 0, rotateAngle, 0.0f, 1.0f, 0.0f);
        
        prevTime = time;
    }
    
    
    public void setTextures(Vector<Texture> textures)
    {
        mTextures = textures;
    }
}
