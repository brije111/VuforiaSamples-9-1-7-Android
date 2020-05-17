/*===============================================================================
Copyright (c) 2020 PTC Inc. All Rights Reserved.

Copyright (c) 2012-2015 Qualcomm Connected Experiences, Inc. All Rights Reserved.

Vuforia is a trademark of PTC Inc., registered in the United States and other
countries.
===============================================================================*/

package com.vuforia.engine.SampleApplication;

import android.opengl.GLSurfaceView;
import android.util.Log;

import com.vuforia.engine.SampleApplication.utils.Texture;

import java.util.Vector;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class SampleRendererBase implements GLSurfaceView.Renderer
{
    private static final String LOGTAG = "SampleRendererBase";

    protected SampleAppRenderer mSampleAppRenderer;
    protected SampleApplicationSession vuforiaAppSession;
    protected Vector<Texture> mTextures;


    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config)
    {
        Log.d(LOGTAG, "GLRenderer.onSurfaceCreated");

        // Call Vuforia function to (re)initialize rendering after first use
        // or after OpenGL ES context was lost (e.g. after onPause/onResume):
        vuforiaAppSession.onSurfaceCreated();

        mSampleAppRenderer.onSurfaceCreated();
    }

    
    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height)
    {
        Log.d(LOGTAG, "GLRenderer.onSurfaceChanged");

        // Call Vuforia function to handle render surface size changes:
        vuforiaAppSession.onSurfaceChanged(width, height);

        // RenderingPrimitives to be updated when some rendering change is done
        onConfigurationChanged();
    }


    @Override
    public void onDrawFrame(GL10 gl)
    {
        // Call our function to render content from SampleAppRenderer class
        mSampleAppRenderer.render();
    }


    public void onConfigurationChanged()
    {
        mSampleAppRenderer.onConfigurationChanged();
    }
}
