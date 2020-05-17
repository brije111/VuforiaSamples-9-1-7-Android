/*===============================================================================
Copyright (c) 2020 PTC Inc. All Rights Reserved.

Copyright (c) 2012-2015 Qualcomm Connected Experiences, Inc. All Rights Reserved.

Vuforia is a trademark of PTC Inc., registered in the United States and other
countries.
===============================================================================*/

package com.vuforia.engine.SampleApplication;

import android.app.Activity;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Bundle;


public abstract class SampleActivityBase extends Activity
{
    private DisplayManager.DisplayListener mDisplayListener;
    private DisplayManager mDisplayManager;

    private int mDeviceOrientation;

    protected SampleRendererBase mBaseRenderer;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)
        {
            mDisplayListener = new DisplayManager.DisplayListener()
            {
                @Override
                public void onDisplayAdded(int displayId)
                {
                }

                @Override
                public void onDisplayChanged(int displayId)
                {
                    int newOrientation = getDeviceOrientation();

                    if (mDeviceOrientation != newOrientation)
                    {
                        // onSurfaceChanged() does not get called when switching from
                        // portrait to reverse portrait or landscape to reverse landscape,
                        // so we must handle this explicitly here
                        // For upside down rotation, the difference in orientation values will be 2
                        // ie: Surface.ROTATION_0 has an enum of 0 and Surface.ROTATION_180 has an enum of 2
                        boolean isUpsideDownRotation = Math.abs(mDeviceOrientation - newOrientation) == 2;

                        if (isUpsideDownRotation)
                        {
                            if (mBaseRenderer != null)
                            {
                                mBaseRenderer.onConfigurationChanged();
                            }
                        }

                        mDeviceOrientation = newOrientation;
                    }
                }

                @Override
                public void onDisplayRemoved(int displayId)
                {
                }
            };

            mDisplayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        }
    }


    @Override
    protected void onResume()
    {
        super.onResume();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1
                && mDisplayManager != null)
        {
            mDisplayManager.registerDisplayListener(mDisplayListener, null);
        }
    }


    @Override
    protected void onPause()
    {
        super.onPause();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1
                && mDisplayManager != null)
        {
            mDisplayManager.unregisterDisplayListener(mDisplayListener);
        }
    }


    private int getDeviceOrientation()
    {
        return getWindowManager().getDefaultDisplay().getRotation();
    }


    public void setRendererReference(SampleRendererBase renderer)
    {
        mBaseRenderer = renderer;
    }
}
