/*===============================================================================
Copyright (c) 2020 PTC Inc. All Rights Reserved.

Copyright (c) 2012-2014 Qualcomm Connected Experiences, Inc. All Rights Reserved.

Vuforia is a trademark of PTC Inc., registered in the United States and other 
countries.
===============================================================================*/

package com.vuforia.engine.CoreSamples.app.CylinderTargets;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.vuforia.CameraDevice;
import com.vuforia.DataSet;
import com.vuforia.ObjectTracker;
import com.vuforia.STORAGE_TYPE;
import com.vuforia.State;
import com.vuforia.Tracker;
import com.vuforia.TrackerManager;
import com.vuforia.Vuforia;
import com.vuforia.engine.SampleApplication.SampleActivityBase;
import com.vuforia.engine.SampleApplication.SampleApplicationControl;
import com.vuforia.engine.SampleApplication.SampleApplicationException;
import com.vuforia.engine.SampleApplication.SampleApplicationSession;
import com.vuforia.engine.SampleApplication.utils.LoadingDialogHandler;
import com.vuforia.engine.SampleApplication.utils.SampleApplicationGLView;
import com.vuforia.engine.SampleApplication.utils.Texture;
import com.vuforia.engine.CoreSamples.R;
import com.vuforia.engine.CoreSamples.ui.SampleAppMenu.SampleAppMenu;
import com.vuforia.engine.CoreSamples.ui.SampleAppMenu.SampleAppMenuGroup;
import com.vuforia.engine.CoreSamples.ui.SampleAppMenu.SampleAppMenuInterface;

import java.util.ArrayList;
import java.util.Vector;

/**
 * The main activity for the CylinderTargets sample.
 * Cylinder Targets allows users to create cylindrical targets for detection and tracking
 *
 * This class does high-level handling of the Vuforia lifecycle and any UI updates
 *
 * For CylinderTarget-specific rendering, check out CylinderTargetRenderer.java
 * For the low-level Vuforia lifecycle code, check out SampleApplicationSession.java
 */
public class CylinderTargets extends SampleActivityBase implements
    SampleApplicationControl, SampleAppMenuInterface
{
    private static final String LOGTAG = "CylinderTargets";
    
    private SampleApplicationSession vuforiaAppSession;

    private SampleApplicationGLView mGlView;
    
    private CylinderTargetRenderer mRenderer;
    
    // The textures we will use for rendering:
    private Vector<Texture> mTextures;
    
    private RelativeLayout mUILayout;
    
    private DataSet dataSet;
    
    private GestureDetector mGestureDetector;
    
    private SampleAppMenu mSampleAppMenu;
    ArrayList<View> mSettingsAdditionalViews = new ArrayList<>();
    
    final LoadingDialogHandler loadingDialogHandler = new LoadingDialogHandler(this);
    
    // Alert Dialog used to display SDK errors
    private AlertDialog mErrorDialog;
    

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        Log.d(LOGTAG, "onCreate");
        super.onCreate(savedInstanceState);
        
        vuforiaAppSession = new SampleApplicationSession(this);
        
        startLoadingAnimation();
        
        vuforiaAppSession
            .initAR(this, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        mTextures = new Vector<>();
        loadTextures();
        
        mGestureDetector = new GestureDetector(this, new GestureListener());
        
    }


    private class GestureListener extends
        GestureDetector.SimpleOnGestureListener
    {
        // Used to set autofocus one second after a manual focus is triggered
        private final Handler autofocusHandler = new Handler();
        
        @Override
        public boolean onDown(MotionEvent e)
        {
            return true;
        }

        // Process Single Tap event to trigger autofocus
        @Override
        public boolean onSingleTapUp(MotionEvent e)
        {
            boolean result = CameraDevice.getInstance().setFocusMode(
                    CameraDevice.FOCUS_MODE.FOCUS_MODE_TRIGGERAUTO);
            if (!result)
                Log.e("SingleTapUp", "Unable to trigger focus");

            // Generates a Handler to trigger continuous auto-focus
            // after 1 second
            autofocusHandler.postDelayed(new Runnable()
            {
                public void run()
                {
                    final boolean autofocusResult = CameraDevice.getInstance().setFocusMode(
                            CameraDevice.FOCUS_MODE.FOCUS_MODE_CONTINUOUSAUTO);

                    if (!autofocusResult)
                        Log.e("SingleTapUp", "Unable to re-enable continuous auto-focus");
                }
            }, 1000L);
            
            return true;
        }
    }


    // Load specific textures from the APK, which we will later use for rendering.
    private void loadTextures()
    {
        mTextures.add(Texture.loadTextureFromApk(
            "CylinderTargets/TextureWireframeNoBorder.png", getAssets()));
        mTextures.add(Texture.loadTextureFromApk("CylinderTargets/sphere.png",
            getAssets()));
    }
    

    @Override
    protected void onResume()
    {
        Log.d(LOGTAG, "onResume");
        super.onResume();

        showProgressIndicator(true);
        
        vuforiaAppSession.onResume();
    }


    // Called whenever the device orientation or screen resolution changes
    @Override
    public void onConfigurationChanged(Configuration config)
    {
        Log.d(LOGTAG, "onConfigurationChanged");
        super.onConfigurationChanged(config);
        
        vuforiaAppSession.onConfigurationChanged();
    }
    

    @Override
    protected void onPause()
    {
        Log.d(LOGTAG, "onPause");
        super.onPause();
        
        if (mGlView != null)
        {
            mGlView.setVisibility(View.INVISIBLE);
            mGlView.onPause();
        }
        
        vuforiaAppSession.onPause();
    }
    

    @Override
    protected void onDestroy()
    {
        Log.d(LOGTAG, "onDestroy");
        super.onDestroy();
        
        try
        {
            vuforiaAppSession.stopAR();
        } catch (SampleApplicationException e)
        {
            Log.e(LOGTAG, e.getString());
        }
        
        // Unload texture:
        mTextures.clear();
        mTextures = null;
        
        System.gc();
    }
    
    
    private void startLoadingAnimation()
    {
        mUILayout = (RelativeLayout) View.inflate(this, R.layout.camera_overlay, null);
        
        mUILayout.setVisibility(View.VISIBLE);
        mUILayout.setBackgroundColor(Color.BLACK);

        RelativeLayout topbarLayout = mUILayout.findViewById(R.id.topbar_layout);
        topbarLayout.setVisibility(View.VISIBLE);

        TextView title = mUILayout.findViewById(R.id.topbar_title);
        title.setText(getText(R.string.feature_cylinder_targets));

        mSettingsAdditionalViews.add(topbarLayout);
        
        // Gets a reference to the loading dialog
        loadingDialogHandler.mLoadingDialogContainer = mUILayout
            .findViewById(R.id.loading_indicator);
        
        // Shows the loading indicator at start
        loadingDialogHandler
            .sendEmptyMessage(LoadingDialogHandler.SHOW_LOADING_DIALOG);
        
        // Adds the inflated layout to the view
        addContentView(mUILayout, new LayoutParams(LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT));
    }
    

    private void initApplicationAR()
    {
        // Create OpenGL ES view:
        int depthSize = 16;
        int stencilSize = 0;
        boolean translucent = Vuforia.requiresAlpha();
        
        mGlView = new SampleApplicationGLView(this);
        mGlView.init(translucent, depthSize, stencilSize);
        
        mRenderer = new CylinderTargetRenderer(this, vuforiaAppSession);
        mRenderer.setTextures(mTextures);
        mGlView.setRenderer(mRenderer);
        mGlView.setPreserveEGLContextOnPause(true);

        setRendererReference(mRenderer);
    }
    
    
    @Override
    public boolean onTouchEvent(MotionEvent event)
    {
        // Process the Gestures
        return ((mSampleAppMenu != null && mSampleAppMenu.processEvent(event))
                || mGestureDetector.onTouchEvent(event));
    }
    
    
    @Override
    public boolean doLoadTrackersData()
    {
        TrackerManager tManager = TrackerManager.getInstance();
        ObjectTracker objectTracker = (ObjectTracker) tManager
            .getTracker(ObjectTracker.getClassType());

        if (objectTracker == null)
            return false;
        
        dataSet = objectTracker.createDataSet();
        
        return dataSet != null
                && dataSet.load("CylinderTargets/sodacan.xml", STORAGE_TYPE.STORAGE_APPRESOURCE)
                && objectTracker.activateDataSet(dataSet);
    }
    
    
    @Override
    public boolean doUnloadTrackersData()
    {
        // Indicate if the trackers were unloaded correctly
        boolean result = true;
        
        TrackerManager tManager = TrackerManager.getInstance();
        ObjectTracker objectTracker = (ObjectTracker) tManager
            .getTracker(ObjectTracker.getClassType());

        if (objectTracker == null)
        {
            return false;
        }

        if (dataSet != null && dataSet.isActive())
        {
            if (objectTracker.getActiveDataSets().at(0).equals(dataSet)
                && !objectTracker.deactivateDataSet(dataSet))
            {
                result = false;
            }
            else if (!objectTracker.destroyDataSet(dataSet))
            {
                result = false;
            }
            
            dataSet = null;
        }
        
        return result;
    }


    @Override
    public void onVuforiaResumed()
    {
        if (mGlView != null)
        {
            mGlView.setVisibility(View.VISIBLE);
            mGlView.onResume();
        }
    }


    // Called once Vuforia has been initialized or
    // an error has caused Vuforia initialization to stop
    @Override
    public void onInitARDone(SampleApplicationException exception)
    {
        if (exception == null)
        {
            initApplicationAR();

            mRenderer.setActive(true);

            // Now add the GL surface view. It is important
            // that the OpenGL ES surface view gets added
            // BEFORE the camera is started and video
            // background is configured.
            addContentView(mGlView, new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));
            
            // Sets the UILayout to be drawn in front of the camera
            mUILayout.bringToFront();

            mUILayout.setBackgroundColor(Color.TRANSPARENT);
            
            mSampleAppMenu = new SampleAppMenu(this, this, "Cylinder Targets",
                mGlView, mUILayout, mSettingsAdditionalViews);
            setSampleAppMenuSettings();

            vuforiaAppSession.startAR();

        } else
        {
            Log.e(LOGTAG, exception.getString());
            showInitializationErrorMessage(exception.getString());
        }
    }


    @Override
    public void onVuforiaStarted()
    {
        mRenderer.updateRenderingPrimitives();

        // Set camera focus mode
        if(!CameraDevice.getInstance().setFocusMode(CameraDevice.FOCUS_MODE.FOCUS_MODE_CONTINUOUSAUTO))
        {
            // If continuous autofocus mode fails, attempt to set to a different mode
            if(!CameraDevice.getInstance().setFocusMode(CameraDevice.FOCUS_MODE.FOCUS_MODE_TRIGGERAUTO))
            {
                CameraDevice.getInstance().setFocusMode(CameraDevice.FOCUS_MODE.FOCUS_MODE_NORMAL);
            }
        }

        showProgressIndicator(false);
    }


    private void showProgressIndicator(boolean show)
    {
        if (show)
        {
            loadingDialogHandler.sendEmptyMessage(LoadingDialogHandler.SHOW_LOADING_DIALOG);
        }
        else
        {
            loadingDialogHandler.sendEmptyMessage(LoadingDialogHandler.HIDE_LOADING_DIALOG);
        }
    }
    

    private void showInitializationErrorMessage(String message)
    {
        final String errorMessage = message;
        runOnUiThread(new Runnable()
        {
            public void run()
            {
                if (mErrorDialog != null)
                {
                    mErrorDialog.dismiss();
                }
                
                // Generates an Alert Dialog to show the error message
                AlertDialog.Builder builder = new AlertDialog.Builder(
                    CylinderTargets.this);
                builder
                    .setMessage(errorMessage)
                    .setTitle(getString(R.string.INIT_ERROR))
                    .setCancelable(false)
                    .setIcon(0)
                    .setPositiveButton(getString(R.string.button_OK),
                        new DialogInterface.OnClickListener()
                        {
                            public void onClick(DialogInterface dialog, int id)
                            {
                                finish();
                            }
                        });
                
                mErrorDialog = builder.create();
                mErrorDialog.show();
            }
        });
    }
    

    // Called every frame
    @Override
    public void onVuforiaUpdate(State state)
    {
    }
    
    
    @Override
    public boolean doInitTrackers()
    {
        TrackerManager tManager = TrackerManager.getInstance();
        Tracker tracker;
        
        // Indicate if the trackers were initialized correctly
        boolean result = true;
        
        tracker = tManager.initTracker(ObjectTracker.getClassType());
        if (tracker == null)
        {
            Log.e(
                LOGTAG,
                "Tracker not initialized. Tracker already initialized or the camera is already started");
            result = false;
        } else
        {
            Log.i(LOGTAG, "Tracker successfully initialized");
        }
        
        return result;
    }
    
    
    @Override
    public boolean doStartTrackers()
    {
        // Indicate if the trackers were started correctly
        boolean result = false;
        
        Tracker objectTracker = TrackerManager.getInstance().getTracker(
            ObjectTracker.getClassType());

        if (objectTracker != null)
        {
            result = objectTracker.start();
        }
        
        return result;
    }
    
    
    @Override
    public boolean doStopTrackers()
    {
        // Indicate if the trackers were stopped correctly
        boolean result = true;

        Tracker objectTracker = TrackerManager.getInstance().getTracker(
            ObjectTracker.getClassType());

        if (objectTracker != null)
        {
            objectTracker.stop();
        }
        else
        {
            result = false;
        }

        return result;
    }
    
    
    @Override
    public boolean doDeinitTrackers()
    {
        TrackerManager tManager = TrackerManager.getInstance();
        return tManager.deinitTracker(ObjectTracker.getClassType());
    }

    // Menu options
    private final static int CMD_BACK = -1;

    private void setSampleAppMenuSettings()
    {
        SampleAppMenuGroup group;
        
        group = mSampleAppMenu.addGroup("", false);
        group.addTextItem(getString(R.string.menu_back), -1);
        
        mSampleAppMenu.attachMenu();
    }


    // In this function you can define the desired behavior for each menu option
    // Each case corresponds to a menu option
    @Override
    public boolean menuProcess(int command)
    {
        boolean result = false;

        switch (command)
        {
            case CMD_BACK:
                result = true;
                finish();
                break;
        }
        
        return result;
    }
}
