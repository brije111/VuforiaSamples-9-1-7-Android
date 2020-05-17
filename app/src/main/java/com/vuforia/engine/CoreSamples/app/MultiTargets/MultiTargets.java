/*===============================================================================
Copyright (c) 2020 PTC Inc. All Rights Reserved.

Copyright (c) 2012-2014 Qualcomm Connected Experiences, Inc. All Rights Reserved.

Vuforia is a trademark of PTC Inc., registered in the United States and other 
countries.
===============================================================================*/

package com.vuforia.engine.CoreSamples.app.MultiTargets;

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
import com.vuforia.ImageTarget;
import com.vuforia.Matrix34F;
import com.vuforia.MultiTarget;
import com.vuforia.MultiTargetResult;
import com.vuforia.ObjectTracker;
import com.vuforia.STORAGE_TYPE;
import com.vuforia.State;
import com.vuforia.Tool;
import com.vuforia.Trackable;
import com.vuforia.TrackableList;
import com.vuforia.Tracker;
import com.vuforia.TrackerManager;
import com.vuforia.Vec3F;
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
 * The main activity for the MultiTargets sample.
 * Cylinder Targets allows users to create targets with multiple sides for detection and tracking
 *
 * This class does high-level handling of the Vuforia lifecycle and any UI updates
 *
 * For CylinderTarget-specific rendering, check out CylinderTargetRenderer.java
 * For the low-level Vuforia lifecycle code, check out SampleApplicationSession.java
 */
public class MultiTargets extends SampleActivityBase implements SampleApplicationControl,
    SampleAppMenuInterface
{
    private static final String LOGTAG = "MultiTargets";
    
    private SampleApplicationSession vuforiaAppSession;

    private SampleApplicationGLView mGlView;

    private MultiTargetRenderer mRenderer;
    
    private RelativeLayout mUILayout;
    
    private GestureDetector mGestureDetector;
    
    private SampleAppMenu mSampleAppMenu;
    ArrayList<View> mSettingsAdditionalViews = new ArrayList<>();
    
    private final LoadingDialogHandler loadingDialogHandler = new LoadingDialogHandler(
        this);
    
    // The textures we will use for rendering:
    private Vector<Texture> mTextures;
    
    private MultiTarget mit = null;
    
    private DataSet dataSet = null;
    
    // Alert Dialog used to display SDK errors
    private AlertDialog mErrorDialog;
    
    private boolean mIsDroidDevice = false;
    

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        Log.d(LOGTAG, "onCreate");
        super.onCreate(savedInstanceState);
        
        vuforiaAppSession = new SampleApplicationSession(this);
        
        startLoadingAnimation();
        
        vuforiaAppSession
            .initAR(this, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        
        // Load any sample specific textures:
        mTextures = new Vector<>();
        loadTextures();
        
        mGestureDetector = new GestureDetector(this, new GestureListener());
        
        mIsDroidDevice = android.os.Build.MODEL.toLowerCase().startsWith(
            "droid");
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
            "MultiTargets/TextureWireframe.png", getAssets()));
        mTextures.add(Texture.loadTextureFromApk(
            "MultiTargets/TextureBowlAndSpoon.png", getAssets()));
    }
    

    @Override
    protected void onResume()
    {
        Log.d(LOGTAG, "onResume");
        super.onResume();

        showProgressIndicator(true);
        
        // This is needed for some Droid devices to force portrait
        if (mIsDroidDevice)
        {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }

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
    
    
    @Override
    public boolean onTouchEvent(MotionEvent event)
    {
        // Process the Gestures
        return mSampleAppMenu != null && mSampleAppMenu.processEvent(event)
            || mGestureDetector.onTouchEvent(event);
    }
    
    
    private void startLoadingAnimation()
    {
        mUILayout = (RelativeLayout) View.inflate(this, R.layout.camera_overlay, null);
        
        mUILayout.setVisibility(View.VISIBLE);
        mUILayout.setBackgroundColor(Color.BLACK);

        RelativeLayout topbarLayout = mUILayout.findViewById(R.id.topbar_layout);
        topbarLayout.setVisibility(View.VISIBLE);

        TextView title = mUILayout.findViewById(R.id.topbar_title);
        title.setText(getText(R.string.feature_multi_targets));

        mSettingsAdditionalViews.add(topbarLayout);

        loadingDialogHandler.mLoadingDialogContainer = mUILayout
            .findViewById(R.id.loading_indicator);
        
        // Shows the loading indicator at start
        loadingDialogHandler
            .sendEmptyMessage(LoadingDialogHandler.SHOW_LOADING_DIALOG);

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
        
        mRenderer = new MultiTargetRenderer(this, vuforiaAppSession);
        mRenderer.setTextures(mTextures);
        mGlView.setRenderer(mRenderer);
        mGlView.setPreserveEGLContextOnPause(true);

        setRendererReference(mRenderer);
    }

    // This function checks the current tracking setup for completeness. If
    // it finds that something is missing, then it creates it and configures
    // it:
    // Any MultiTarget and Part elements missing from the config.xml file
    // will be created.
    private void initMIT()
    {
        Log.d(LOGTAG, "Beginning to check the tracking setup");
        
        // Configuration data - identical to what is in the config.xml file
        //
        // If you want to recreate the trackable assets from the on-line TMS
        // server using the original images provided in the sample's media
        // folder, use the following trackable sizes on creation to get
        // identical visual results:
        // create a cuboid with width = 90 ; height = 120 ; length = 60.
        
        String names[] = { "FlakesBox.Front", "FlakesBox.Back",
                "FlakesBox.Left", "FlakesBox.Right", "FlakesBox.Top",
                "FlakesBox.Bottom" };
        float trans[] = { 0.0f, 0.0f, 30.0f, 0.0f, 0.0f, -30.0f, -45.0f, 0.0f,
                0.0f, 45.0f, 0.0f, 0.0f, 0.0f, 60.0f, 0.0f, 0.0f, -60.0f, 0.0f };
        float rots[] = { 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 180.0f,
                0.0f, 1.0f, 0.0f, -90.0f, 0.0f, 1.0f, 0.0f, 90.0f, 1.0f, 0.0f,
                0.0f, -90.0f, 1.0f, 0.0f, 0.0f, 90.0f };
        
        TrackerManager trackerManager = TrackerManager.getInstance();
        ObjectTracker objectTracker = (ObjectTracker) trackerManager
            .getTracker(ObjectTracker.getClassType());
        
        if (objectTracker == null || dataSet == null)
        {
            return;
        }
        
        // Go through all Trackables to find the MultiTarget instance
        TrackableList trackableList = dataSet.getTrackables();
        for (Trackable trackable : trackableList)
        {
            if (trackable.getType() == MultiTargetResult
                .getClassType())
            {
                Log.d(LOGTAG, "MultiTarget exists -> no need to create one");
                mit = (MultiTarget) (trackable);
                break;
            }
        }
        
        // If no MultiTarget was found, then let's create one.
        if (mit == null)
        {
            Log.d(LOGTAG, "No MultiTarget found -> creating one");
            mit = dataSet.createMultiTarget("FlakesBox");
            
            if (mit == null)
            {
                Log.d(LOGTAG,
                    "ERROR: Failed to create the MultiTarget - probably the Tracker is running");
                return;
            }
        }
        
        // Try to find each ImageTarget. If we find it, this actually means that
        // it is not part of the MultiTarget yet: ImageTargets that are part of
        // a MultiTarget don't show up in the list of Trackables.
        // Each ImageTarget that we found, is then made a part of the
        // MultiTarget and a correct pose (reflecting the pose of the
        // config.xml file) is set).
        int numAdded = 0;

        for (int i = 0; i < 6; i++)
        {
            ImageTarget it = findImageTarget(names[i]);
            if (it != null)
            {
                Log.d(LOGTAG,
                    "ImageTarget '%s' found -> adding it as to the MultiTarget"
                        + names[i]);
                
                int idx = mit.addPart(it);
                Vec3F t = new Vec3F(trans[i * 3], trans[i * 3 + 1],
                    trans[i * 3 + 2]);
                Vec3F a = new Vec3F(rots[i * 4], rots[i * 4 + 1],
                    rots[i * 4 + 2]);
                Matrix34F mat = new Matrix34F();
                
                Tool.setTranslation(mat, t);
                Tool.setRotation(mat, a, rots[i * 4 + 3]);
                mit.setPartOffset(idx, mat);
                numAdded++;
            }
        }
        
        Log.d(LOGTAG, "Added " + numAdded
            + " ImageTarget(s) to the MultiTarget");
        
        if (mit.getParts().size() != 6)
        {
            Log.d(LOGTAG,
                "ERROR: The MultiTarget should have 6 parts, but it reports "
                    + mit.getParts().size() + " parts");
        }
        
        Log.d(LOGTAG, "Finished checking the tracking setup");
    }
    
    
    private ImageTarget findImageTarget(String name)
    {
        TrackerManager trackerManager = TrackerManager.getInstance();
        ObjectTracker objectTracker = (ObjectTracker) trackerManager
            .getTracker(ObjectTracker.getClassType());
        
        if (objectTracker != null)
        {
            TrackableList trackableList = dataSet.getTrackables();
            for (Trackable trackable : trackableList)
            {
                if (trackable.getType() == MultiTargetResult
                    .getClassType())
                {
                    if (trackable.getName().compareTo(name) == 0)
                    {
                        return (ImageTarget) (trackable);
                    }
                }
            }
        }
        return null;
    }
    
    
    @Override
    public boolean doInitTrackers()
    {
        // Indicate if the trackers were initialized correctly
        boolean result = true;
        
        TrackerManager tManager = TrackerManager.getInstance();
        Tracker tracker;
        
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
    public boolean doLoadTrackersData()
    {
        // Get the image tracker:
        TrackerManager trackerManager = TrackerManager.getInstance();
        ObjectTracker objectTracker = (ObjectTracker) trackerManager
            .getTracker(ObjectTracker.getClassType());
        if (objectTracker == null)
        {
            Log.d(
                LOGTAG,
                "Failed to load tracking data set because the ObjectTracker has not been initialized.");
            return false;
        }
        
        // Create the data set:
        dataSet = objectTracker.createDataSet();
        if (dataSet == null)
        {
            Log.d(LOGTAG, "Failed to create a new tracking data.");
            return false;
        }
        
        // Load the data set:
        if (!dataSet.load("MultiTargets/FlakesBox.xml",
            STORAGE_TYPE.STORAGE_APPRESOURCE))
        {
            Log.d(LOGTAG, "Failed to load data set.");
            return false;
        }
        
        Log.d(LOGTAG, "Successfully loaded the data set.");
        
        // Validate the MultiTarget and setup programmatically if required:
        initMIT();
        
        return objectTracker.activateDataSet(dataSet);
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
    public boolean doUnloadTrackersData()
    {
        // Indicate if the trackers were unloaded correctly
        boolean result = true;
        
        // Get the image tracker:
        TrackerManager trackerManager = TrackerManager.getInstance();
        ObjectTracker objectTracker = (ObjectTracker) trackerManager
            .getTracker(ObjectTracker.getClassType());
        if (objectTracker == null)
        {
            Log.d(
                LOGTAG,
                "Failed to destroy the tracking data set because the ObjectTracker has not been initialized.");
            return false;
        }
        
        if (dataSet != null)
        {
            if (!objectTracker.deactivateDataSet(dataSet))
            {
                Log.d(
                    LOGTAG,
                    "Failed to destroy the tracking data set because the data set could not be deactivated.");
                result = false;
            } else if (!objectTracker.destroyDataSet(dataSet))
            {
                Log.d(LOGTAG, "Failed to destroy the tracking data set.");
                result = false;
            }
            
            if (result)
                Log.d(LOGTAG, "Successfully destroyed the data set.");
            
            dataSet = null;
            mit = null;
        }
        
        return result;
    }
    
    
    @Override
    public boolean doDeinitTrackers()
    {
        TrackerManager tManager = TrackerManager.getInstance();
        return tManager.deinitTracker(ObjectTracker.getClassType());
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

            loadingDialogHandler
                .sendEmptyMessage(LoadingDialogHandler.HIDE_LOADING_DIALOG);

            mUILayout.setBackgroundColor(Color.TRANSPARENT);
            
            mSampleAppMenu = new SampleAppMenu(this, this, "Multi Targets",
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
                    MultiTargets.this);
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
