/*===============================================================================
Copyright (c) 2020 PTC Inc. All Rights Reserved.

Copyright (c) 2012-2014 Qualcomm Connected Experiences, Inc. All Rights Reserved.

Vuforia is a trademark of PTC Inc., registered in the United States and other 
countries.
===============================================================================*/

package com.vuforia.engine.CoreSamples.app.VirtualButtons;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.Log;

import com.vuforia.Device;
import com.vuforia.ImageTargetResult;
import com.vuforia.Rectangle;
import com.vuforia.Renderer;
import com.vuforia.State;
import com.vuforia.Tool;
import com.vuforia.TrackableResult;
import com.vuforia.VirtualButton;
import com.vuforia.VirtualButtonResult;
import com.vuforia.VirtualButtonResultList;
import com.vuforia.Vuforia;
import com.vuforia.engine.SampleApplication.SampleAppRenderer;
import com.vuforia.engine.SampleApplication.SampleAppRendererControl;
import com.vuforia.engine.SampleApplication.SampleApplicationSession;
import com.vuforia.engine.SampleApplication.SampleRendererBase;
import com.vuforia.engine.SampleApplication.utils.CubeShaders;
import com.vuforia.engine.SampleApplication.utils.LineShaders;
import com.vuforia.engine.SampleApplication.utils.SampleUtils;
import com.vuforia.engine.SampleApplication.utils.Teapot;
import com.vuforia.engine.SampleApplication.utils.Texture;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Vector;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;


/**
 * The renderer class for the Virtual Buttons sample.
 *
 * In the renderFrame() function you can render augmentations to display over the Target
 */
public class VirtualButtonRenderer extends SampleRendererBase implements SampleAppRendererControl
{
    private static final String LOGTAG = "VirtualButtonRenderer";
    
    private final VirtualButtons mActivity;

    // Object to be rendered
    private final Teapot mTeapot = new Teapot();
    
    // OpenGL ES 2.0 specific (3D model):
    private int shaderProgramID = 0;
    private int vertexHandle = 0;
    private int textureCoordHandle = 0;
    private int mvpMatrixHandle = 0;
    private int texSampler2DHandle = 0;
    
    private int lineOpacityHandle = 0;
    private int lineColorHandle = 0;
    private int mvpMatrixButtonsHandle = 0;
    
    // OpenGL ES 2.0 specific (Virtual Buttons):
    private int vbShaderProgramID = 0;
    private int vbVertexHandle = 0;

    private static final float kTeapotScale = 0.003f;

    // Define the coordinates of the virtual buttons to render the area of action,
    // These values are the same as those in Wood.xml
    static private final float[] RED_VB_BUTTON =  {-0.10868f, -0.05352f, -0.07575f, -0.06587f};
    static private final float[] BLUE_VB_BUTTON =  {-0.04528f, -0.05352f, -0.01235f, -0.06587f};
    static private final float[] YELLOW_VB_BUTTON =  {0.01482f, -0.05352f, 0.04775f, -0.06587f};
    static private final float[] GREEN_VB_BUTTON =  {0.07657f, -0.05352f, 0.10950f, -0.06587f};

    VirtualButtonRenderer(VirtualButtons activity,
        SampleApplicationSession session)
    {
        mActivity = activity;
        vuforiaAppSession = session;

        // SampleAppRenderer used to encapsulate the use of RenderingPrimitives setting
        // the device mode AR/VR and stereo mode
        mSampleAppRenderer = new SampleAppRenderer(this, mActivity,
                vuforiaAppSession.getVideoMode(), 0.01f, 5f);
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
        
        // OpenGL setup for Virtual Buttons
        vbShaderProgramID = SampleUtils.createProgramFromShaderSrc(
            LineShaders.LINE_VERTEX_SHADER, LineShaders.LINE_FRAGMENT_SHADER);
        
        mvpMatrixButtonsHandle = GLES20.glGetUniformLocation(vbShaderProgramID,
            "modelViewProjectionMatrix");
        vbVertexHandle = GLES20.glGetAttribLocation(vbShaderProgramID,
            "vertexPosition");
        lineOpacityHandle = GLES20.glGetUniformLocation(vbShaderProgramID,
            "opacity");
        lineColorHandle = GLES20.glGetUniformLocation(vbShaderProgramID,
            "color");
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

        // Did we find any trackables this frame?
        if (!state.getTrackableResults().empty())
        {
            // Get the trackable:
            TrackableResult trackableResult = state.getTrackableResults().at(0);
            float[] modelViewMatrix = Tool.convertPose2GLMatrix(
                trackableResult.getPose()).getData();
            
            // The image target specific result:
            ImageTargetResult imageTargetResult = (ImageTargetResult) trackableResult;
            VirtualButtonResultList virtualButtonResultList = imageTargetResult.getVirtualButtonResults();

            // Set transformations:
            float[] modelViewProjection = new float[16];
            Matrix.multiplyMM(modelViewProjection, 0, projectionMatrix, 0, modelViewMatrix, 0);
            
            // Set the texture used for the teapot model:
            int textureIndex = 0;
            
            float vbVertices[] = new float[virtualButtonResultList.size() * 24];
            short vbCounter = 0;
            
            // Iterate through this targets virtual buttons:
            for (VirtualButtonResult buttonResult : virtualButtonResultList)
            {
                VirtualButton button = buttonResult.getVirtualButton();
                
                int buttonIndex = 0;

                // Run through button name array to find button index
                for (int j = 0; j < VirtualButtons.NUM_BUTTONS; ++j)
                {
                    if (button.getName().compareTo(
                        mActivity.virtualButtonColors[j]) == 0)
                    {
                        buttonIndex = j;
                        break;
                    }
                }
                
                // If the button is pressed, than use this texture:
                if (buttonResult.isPressed())
                {
                    textureIndex = buttonIndex + 1;
                }

                // Define the four virtual buttons as Rectangle using the same values as the dataset
                Rectangle vbRectangle[] = new Rectangle[4];
                vbRectangle[0] = new Rectangle(RED_VB_BUTTON[0], RED_VB_BUTTON[1],
                        RED_VB_BUTTON[2], RED_VB_BUTTON[3]);
                vbRectangle[1] = new Rectangle(BLUE_VB_BUTTON[0], BLUE_VB_BUTTON[1],
                        BLUE_VB_BUTTON[2], BLUE_VB_BUTTON[3]);
                vbRectangle[2] = new Rectangle(YELLOW_VB_BUTTON[0], YELLOW_VB_BUTTON[1],
                        YELLOW_VB_BUTTON[2], YELLOW_VB_BUTTON[3]);
                vbRectangle[3] = new Rectangle(GREEN_VB_BUTTON[0], GREEN_VB_BUTTON[1],
                        GREEN_VB_BUTTON[2], GREEN_VB_BUTTON[3]);
                
                // We add the vertices to a common array in order to have one
                // single draw call. This is more efficient than having multiple
                // glDrawArray calls
                vbVertices[vbCounter] = vbRectangle[buttonIndex].getLeftTopX();
                vbVertices[vbCounter + 1] = vbRectangle[buttonIndex]
                    .getLeftTopY();
                vbVertices[vbCounter + 2] = 0.0f;
                vbVertices[vbCounter + 3] = vbRectangle[buttonIndex]
                    .getRightBottomX();
                vbVertices[vbCounter + 4] = vbRectangle[buttonIndex]
                    .getLeftTopY();
                vbVertices[vbCounter + 5] = 0.0f;
                vbVertices[vbCounter + 6] = vbRectangle[buttonIndex]
                    .getRightBottomX();
                vbVertices[vbCounter + 7] = vbRectangle[buttonIndex]
                    .getLeftTopY();
                vbVertices[vbCounter + 8] = 0.0f;
                vbVertices[vbCounter + 9] = vbRectangle[buttonIndex]
                    .getRightBottomX();
                vbVertices[vbCounter + 10] = vbRectangle[buttonIndex]
                    .getRightBottomY();
                vbVertices[vbCounter + 11] = 0.0f;
                vbVertices[vbCounter + 12] = vbRectangle[buttonIndex]
                    .getRightBottomX();
                vbVertices[vbCounter + 13] = vbRectangle[buttonIndex]
                    .getRightBottomY();
                vbVertices[vbCounter + 14] = 0.0f;
                vbVertices[vbCounter + 15] = vbRectangle[buttonIndex]
                    .getLeftTopX();
                vbVertices[vbCounter + 16] = vbRectangle[buttonIndex]
                    .getRightBottomY();
                vbVertices[vbCounter + 17] = 0.0f;
                vbVertices[vbCounter + 18] = vbRectangle[buttonIndex]
                    .getLeftTopX();
                vbVertices[vbCounter + 19] = vbRectangle[buttonIndex]
                    .getRightBottomY();
                vbVertices[vbCounter + 20] = 0.0f;
                vbVertices[vbCounter + 21] = vbRectangle[buttonIndex]
                    .getLeftTopX();
                vbVertices[vbCounter + 22] = vbRectangle[buttonIndex]
                    .getLeftTopY();
                vbVertices[vbCounter + 23] = 0.0f;
                vbCounter += 24;
                
            }
            
            // We only render if there is something on the array
            if (vbCounter > 0)
            {
                // Render frame around button
                GLES20.glUseProgram(vbShaderProgramID);
                
                GLES20.glVertexAttribPointer(vbVertexHandle, 3,
                    GLES20.GL_FLOAT, false, 0, fillBuffer(vbVertices));
                
                GLES20.glEnableVertexAttribArray(vbVertexHandle);
                
                GLES20.glUniform1f(lineOpacityHandle, 1.0f);
                GLES20.glUniform3f(lineColorHandle, 1.0f, 1.0f, 1.0f);
                
                GLES20.glUniformMatrix4fv(mvpMatrixButtonsHandle, 1, false,
                    modelViewProjection, 0);
                
                // We multiply by 8 because that's the number of vertices per
                // button
                // The reason is that GL_LINES considers only pairs. So some
                // vertices
                // must be repeated.
                GLES20.glDrawArrays(GLES20.GL_LINES, 0, virtualButtonResultList.size() * 8);
                
                SampleUtils.checkGLError("VirtualButtons drawButton");
                
                GLES20.glDisableVertexAttribArray(vbVertexHandle);
            }

            Texture thisTexture = mTextures.get(textureIndex);
            
            // Scale 3D model
            Matrix.scaleM(modelViewMatrix, 0, kTeapotScale, kTeapotScale,
                kTeapotScale);
            
            float[] modelViewProjectionScaled = new float[16];
            Matrix.multiplyMM(modelViewProjectionScaled, 0, projectionMatrix, 0, modelViewMatrix, 0);
            
            // Render 3D model
            GLES20.glUseProgram(shaderProgramID);
            
            GLES20.glVertexAttribPointer(vertexHandle, 3, GLES20.GL_FLOAT,
                false, 0, mTeapot.getVertices());
            GLES20.glVertexAttribPointer(textureCoordHandle, 2,
                GLES20.GL_FLOAT, false, 0, mTeapot.getTexCoords());
            
            GLES20.glEnableVertexAttribArray(vertexHandle);
            GLES20.glEnableVertexAttribArray(textureCoordHandle);
            
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,
                thisTexture.mTextureID[0]);
            GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false,
                modelViewProjectionScaled, 0);
            GLES20.glUniform1i(texSampler2DHandle, 0);

            GLES20.glDrawElements(GLES20.GL_TRIANGLES,
                mTeapot.getNumObjectIndex(), GLES20.GL_UNSIGNED_SHORT,
                mTeapot.getIndices());
            
            GLES20.glDisableVertexAttribArray(vertexHandle);
            GLES20.glDisableVertexAttribArray(textureCoordHandle);
            
            SampleUtils.checkGLError("VirtualButtons renderFrame");
        }
        
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        
        Renderer.getInstance().end();
    }
    
    
    private Buffer fillBuffer(float[] array)
    {
        // Convert to floats because OpenGL doesnt work on doubles, and manually
        // casting each input value would take too much time.
        // Each float takes four bytes
        ByteBuffer bb = ByteBuffer.allocateDirect(4 * array.length);
        bb.order(ByteOrder.LITTLE_ENDIAN);
        for (float d : array)
            bb.putFloat(d);
        bb.rewind();
        
        return bb;
        
    }
    
    
    public void setTextures(Vector<Texture> textures)
    {
        mTextures = textures;
    }
}
