/*===============================================================================
Copyright (c) 2020 PTC Inc. All Rights Reserved.

Copyright (c) 2012-2014 Qualcomm Connected Experiences, Inc. All Rights Reserved.

Vuforia is a trademark of PTC Inc., registered in the United States and other 
countries.
===============================================================================*/

package com.vuforia.engine.CoreSamples.app.ModelTargets;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Vector;

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
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.vuforia.CameraDevice;
import com.vuforia.DataSet;
import com.vuforia.HINT;
import com.vuforia.ModelTarget;
import com.vuforia.ObjectTracker;
import com.vuforia.PositionalDeviceTracker;
import com.vuforia.State;
import com.vuforia.STORAGE_TYPE;
import com.vuforia.Trackable;
import com.vuforia.Tracker;
import com.vuforia.TrackerManager;
import com.vuforia.TrackableResult;
import com.vuforia.Vuforia;
import com.vuforia.engine.CoreSamples.ui.SampleAppMessage;
import com.vuforia.engine.SampleApplication.SampleActivityBase;
import com.vuforia.engine.SampleApplication.utils.SampleAppTimer;
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


public class ModelTargets extends SampleActivityBase implements SampleApplicationControl,
    SampleAppMenuInterface
{
    private static final String LOGTAG = "ModelTargets";
    
    SampleApplicationSession vuforiaAppSession;

    DataSet mDataset;

    private static int NUM_DATASETS = 3;

    private int mCurrentDatasetSelectionIndex = 0;
    private int mStartDatasetsIndex = 0;
    private int mActiveGuideViewIndex = 0;

    private final ArrayList<String> mDatasetStrings = new ArrayList<>();

    private boolean mSwitchDatasetAsap = false;

    // Our OpenGL view:
    private SampleApplicationGLView mGlView;
    
    // Our renderer:
    private ModelTargetRenderer mRenderer;
    
    private GestureDetector mGestureDetector;
    
    // The textures we will use for rendering:
    private Vector<Texture> mTextures;
    private HashMap<String, Integer> mSymbolicGuideViewIndices;
    
    private RelativeLayout mUILayout;
    private Button mBtnLayout;

    private SampleAppMenu mSampleAppMenu;
    ArrayList<View> mSettingsAdditionalViews = new ArrayList<>();

    private boolean mContAutofocus = false;

    LoadingDialogHandler loadingDialogHandler = new LoadingDialogHandler(this);
    
    // Alert Dialog used to display SDK errors
    private AlertDialog mErrorDialog;

    private SampleAppMessage mSampleAppMessage;
    private SampleAppTimer mRelocalizationTimer;
    private SampleAppTimer mStatusDelayTimer;

    private int mCurrentStatusInfo;

    // Called when the activity first starts or the user navigates back to an
    // activity.
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        Log.d(LOGTAG, "onCreate");
        super.onCreate(savedInstanceState);
        
        vuforiaAppSession = new SampleApplicationSession(this, CameraDevice.MODE.MODE_DEFAULT);
        
        startLoadingAnimation();

        setViewsReferences();

        mDatasetStrings.add("ModelTargets/VuforiaMars_ModelTarget.xml");
        mDatasetStrings.add("ModelTargets/VuforiaMars_ModelTarget360.xml");

        vuforiaAppSession
            .initAR(this, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        
        mGestureDetector = new GestureDetector(this, new GestureListener());
        
        // Load any sample specific textures:
        mTextures = new Vector<>();
        mSymbolicGuideViewIndices = new HashMap<>();
        loadTextures();

        // Relocalization timer and message
        mSampleAppMessage = new SampleAppMessage(this, mUILayout, mUILayout.findViewById(R.id.topbar_layout), false);
        mRelocalizationTimer = new SampleAppTimer(10000, 1000)
        {
            @Override
            public void onFinish()
            {
                if (vuforiaAppSession != null)
                {
                    vuforiaAppSession.resetDeviceTracker();
                }

                super.onFinish();
            }
        };

        mStatusDelayTimer = new SampleAppTimer(1000, 1000)
        {
            @Override
            public void onFinish()
            {
                if (mRenderer.isTargetCurrentlyTracked())
                {
                    super.onFinish();
                    return;
                }

                if (!mRelocalizationTimer.isRunning())
                {
                    mRelocalizationTimer.startTimer();
                }

                runOnUiThread(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        mSampleAppMessage.show(getString(R.string.instruct_relocalize));
                    }
                });

                super.onFinish();
            }
        };
    }

    void setViewsReferences()
    {
        mSettingsAdditionalViews.add(mBtnLayout);
    }
    
    // Process Single Tap event to trigger autofocus
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
        
        
        @Override
        public boolean onSingleTapUp(MotionEvent e)
        {
            // Generates a Handler to trigger autofocus
            // after 1 second
            autofocusHandler.postDelayed(new Runnable()
            {
                public void run()
                {
                    boolean result = CameraDevice.getInstance().setFocusMode(
                        CameraDevice.FOCUS_MODE.FOCUS_MODE_TRIGGERAUTO);
                    
                    if (!result)
                        Log.e("SingleTapUp", "Unable to trigger focus");
                }
            }, 1000L);
            
            return true;
        }
    }
    
    
    // We want to load specific textures from the APK, which we will later use
    // for rendering.
    
    private void loadTextures()
    {
        mTextures.add(Texture.loadTextureFromApk("Lander.png", getAssets()));
        mTextures.add(Texture.loadTextureFromApk("ModelTargets/Lander360.png", getAssets()));

        mSymbolicGuideViewIndices.put("Vuforia_MarsLander", 1);
    }
    
    
    // Called when the activity will start interacting with the user.
    @Override
    protected void onResume()
    {
        Log.d(LOGTAG, "onResume");
        super.onResume();

        showProgressIndicator(true);
        
        vuforiaAppSession.onResume();
    }
    
    
    // Callback for configuration changes the activity handles itself
    @Override
    public void onConfigurationChanged(Configuration config)
    {
        Log.d(LOGTAG, "onConfigurationChanged");
        super.onConfigurationChanged(config);
        
        vuforiaAppSession.onConfigurationChanged();

        Handler handler = new Handler();
        handler.postDelayed(new Runnable()
        {
            @Override
            public void run()
            {
                if (mRenderer != null)
                {
                    mRenderer.updateRenderingPrimitives();
                }

                showProgressIndicator(false);
            }
        }, 100);
    }
    
    
    // Called when the system is about to start resuming a previous activity.
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
    
    
    // The final call you receive before your activity is destroyed.
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

        mSymbolicGuideViewIndices.clear();
        mSymbolicGuideViewIndices = null;
        
        System.gc();
    }
    
    
    // Initializes AR application components.
    private void initApplicationAR()
    {
        // Create OpenGL ES view:
        int depthSize = 16;
        int stencilSize = 0;
        boolean translucent = Vuforia.requiresAlpha();
        
        mGlView = new SampleApplicationGLView(this);
        mGlView.init(translucent, depthSize, stencilSize);

        mRenderer = new ModelTargetRenderer(this, vuforiaAppSession);
        mRenderer.setTextures(mTextures, mSymbolicGuideViewIndices);

        mGlView.setRenderer(mRenderer);
        mGlView.setPreserveEGLContextOnPause(true);

        setRendererReference(mRenderer);
    }
    
    
    private void startLoadingAnimation()
    {
        mUILayout = (RelativeLayout) View.inflate(this, R.layout.camera_overlay_model_targets,
            null);
        
        mUILayout.setVisibility(View.VISIBLE);
        mUILayout.setBackgroundColor(Color.BLACK);

        mBtnLayout = mUILayout.findViewById(R.id.reset_btn);

        RelativeLayout topbarLayout = mUILayout.findViewById(R.id.topbar_layout);
        topbarLayout.setVisibility(View.VISIBLE);

        TextView title = mUILayout.findViewById(R.id.topbar_title);
        title.setText(getText(R.string.feature_model_targets));

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


    public void setBtnLayoutVisibility(int visibility)
    {
        mBtnLayout.setVisibility(visibility);
    }


    // Methods to load and destroy tracking data.
    @Override
    public boolean doLoadTrackersData()
    {
        TrackerManager tManager = TrackerManager.getInstance();
        ObjectTracker objectTracker = (ObjectTracker) tManager
            .getTracker(ObjectTracker.getClassType());

        if (objectTracker == null)
        {
            return false;
        }

        if (mDataset == null)
        {
            mDataset = objectTracker.createDataSet();
        }

        if (mDataset == null)
        {
            return false;
        }

        if (!mDataset.load(mDatasetStrings.get(mCurrentDatasetSelectionIndex), STORAGE_TYPE.STORAGE_APPRESOURCE))
        {
            return false;
        }

        return objectTracker.activateDataSet(mDataset);
    }
    
    
    @Override
    public boolean doUnloadTrackersData()
    {
        // To return true if the trackers were unloaded correctly
        boolean result = true;

        TrackerManager tManager = TrackerManager.getInstance();
        ObjectTracker objectTracker = (ObjectTracker) tManager
            .getTracker(ObjectTracker.getClassType());
        if (objectTracker == null)
            return false;

        if (mDataset != null && mDataset.isActive())
        {
            if (objectTracker.getActiveDataSets().at(0).equals(mDataset)
                    && !objectTracker.deactivateDataSet(mDataset))
            {
                result = false;
            }
            else if (!objectTracker.destroyDataSet(mDataset))
            {
                result = false;
            }

            mDataset = null;
        }

        return result;
    }
    
    
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

            // Sets the layout background to transparent
            mUILayout.setBackgroundColor(Color.TRANSPARENT);

            Button resetBtn = mUILayout.findViewById(R.id.reset_btn);

            resetBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view)
                {
                    resetTrackers();
                }
            });

            if (mRenderer.areModelsLoaded())
            {
                showProgressIndicator(false);
            }

            vuforiaAppSession.startAR();

            mSampleAppMenu = new SampleAppMenu(this, this, getString(R.string.feature_model_targets),
                mGlView, mUILayout, mSettingsAdditionalViews);
            setSampleAppMenuSettings();

        } else
        {
            Log.e(LOGTAG, exception.getString());
            showInitializationErrorMessage(exception.getString());
        }
        
    }

    public void resetTrackers()
    {
        TrackerManager trackerManager = TrackerManager.getInstance();
        ObjectTracker objectTracker = (ObjectTracker) trackerManager.
                getTracker(ObjectTracker.getClassType());
        PositionalDeviceTracker deviceTracker = (PositionalDeviceTracker) trackerManager.
                getTracker(PositionalDeviceTracker.getClassType());

        if (deviceTracker != null)
        {
            deviceTracker.reset();
        }

        // Reset dataset
        if (objectTracker != null)
        {
            objectTracker.stop();

            if (mDataset != null && mDataset.isActive())
            {
                objectTracker.deactivateDataSet(mDataset);
                objectTracker.activateDataSet(mDataset);
            }

            objectTracker.start();
        }
    }

    public void showProgressIndicator(boolean show)
    {
        if (loadingDialogHandler != null)
        {
            if (show)
            {
                loadingDialogHandler
                        .sendEmptyMessage(LoadingDialogHandler.SHOW_LOADING_DIALOG);
            }
            else
            {
                loadingDialogHandler
                        .sendEmptyMessage(LoadingDialogHandler.HIDE_LOADING_DIALOG);
            }
        }
    }
    
    
    // Shows initialization error messages as System dialogs
    public void showInitializationErrorMessage(String message)
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
                    ModelTargets.this);
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
    
    
    @Override
    public void onVuforiaUpdate(State state)
    {
        if (mSwitchDatasetAsap)
        {
            mSwitchDatasetAsap = false;
            TrackerManager trackerManager = TrackerManager.getInstance();
            ObjectTracker objTracker = (ObjectTracker) trackerManager.getTracker(ObjectTracker.getClassType());

            if (objTracker == null || mDataset == null
                    || objTracker.getActiveDataSets().at(0) == null)
            {
                Log.d(LOGTAG, "Failed to swap datasets");
                return;
            }

            doUnloadTrackersData();
            doLoadTrackersData();
        }
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

        if (mRenderer.areModelsLoaded())
        {
            showProgressIndicator(false);
        }
    }


    @Override
    public boolean doInitTrackers()
    {
        // Indicate if the trackers were initialized correctly
        boolean result = true;

        TrackerManager trackerManager = TrackerManager.getInstance();

        // Initialize the object tracker:
        Tracker tracker = trackerManager.initTracker(ObjectTracker.getClassType());

        if (tracker == null)
        {
            Log.d(LOGTAG, "Failed to initialize ObjectTracker.");
            result = false;
        } else
        {
            Log.d(LOGTAG, "Successfully initialized ObjectTracker.");
        }

        Vuforia.setHint(HINT.HINT_MODEL_TARGET_RECO_WHILE_EXTENDED_TRACKED, 1);

        // Initialize the Positional Device Tracker
        PositionalDeviceTracker deviceTracker = (PositionalDeviceTracker)
                trackerManager.initTracker(PositionalDeviceTracker.getClassType());

        if (deviceTracker != null)
        {
            Log.i(LOGTAG, "Successfully initialized Device Tracker");
        }
        else
        {
            Log.e(LOGTAG, "Failed to initialize Device Tracker");
        }
        return result;
    }
    
    
    @Override
    public boolean doStartTrackers()
    {
        // Indicate if the trackers were started correctly
        boolean result = true;
        
        Tracker objectTracker = TrackerManager.getInstance().getTracker(
            ObjectTracker.getClassType());
        if (objectTracker != null)
            objectTracker.start();

        return setDeviceTrackerEnabled(true);
    }
    
    
    @Override
    public boolean doStopTrackers()
    {
        // Returns true if the trackers were stopped correctly
        Tracker objectTracker = TrackerManager.getInstance().getTracker(
            ObjectTracker.getClassType());
        if (objectTracker != null)
        {
            objectTracker.stop();
        }

        return setDeviceTrackerEnabled(false);
    }

    boolean setDeviceTrackerEnabled(boolean enabled)
    {
        boolean result = true;

        Tracker deviceTracker = TrackerManager.getInstance().getTracker(
                PositionalDeviceTracker.getClassType());

        if (deviceTracker == null)
        {
            Log.e(LOGTAG, "ERROR: Could not toggle device tracker state");
            return false;
        }

        if (enabled)
        {
            if (deviceTracker.start())
            {
                Log.d(LOGTAG, "Successfully started device tracker");
            }
            else
            {
                result = false;
                Log.e(LOGTAG, "Failed to start device tracker");
            }
        }
        else
        {
            deviceTracker.stop();
            Log.d(LOGTAG, "Successfully stopped device tracker");
        }

        return result;
    }
    
    
    @Override
    public boolean doDeinitTrackers()
    {
        // Returns true if the trackers were deinitialized correctly
        boolean result;

        TrackerManager tManager = TrackerManager.getInstance();
        result = tManager.deinitTracker(ObjectTracker.getClassType());
        result = result && tManager.deinitTracker(PositionalDeviceTracker.getClassType());
        
        return result;
    }
    
    
    @Override
    public boolean onTouchEvent(MotionEvent event)
    {
        // Process the Gestures
        return mSampleAppMenu != null && mSampleAppMenu.processEvent(event) ||
                mGestureDetector.onTouchEvent(event);

    }


    private final static int CMD_BACK = -1;
    private final static int CMD_AUTOFOCUS = 0;
    private final static int CMD_NEXT_GUIDEVIEW = 1;
    private final static int CMD_DATASET_START_INDEX = 2;

    
    // This method sets the menu's settings
    private void setSampleAppMenuSettings()
    {
        SampleAppMenuGroup group;
        
        group = mSampleAppMenu.addGroup("", false);
        group.addTextItem(getString(R.string.menu_back), CMD_BACK);

        group = mSampleAppMenu.addGroup(getString(R.string.menu_camera), true);
        group.addSelectionItem(getString(R.string.menu_contAutofocus),
                CMD_AUTOFOCUS, mContAutofocus);

        group.addTextItem(getString(R.string.menu_next_guide_view), CMD_NEXT_GUIDEVIEW);

        group = mSampleAppMenu
                .addGroup(getString(R.string.menu_datasets), true);
        mStartDatasetsIndex = CMD_DATASET_START_INDEX;

        group.addRadioItem("Model Targets", mStartDatasetsIndex, true);
        group.addRadioItem("Advanced Model Targets", mStartDatasetsIndex + 1, false);

        mSampleAppMenu.attachMenu();
    }

    // Helper method to avoid code duplication
    private ModelTarget getModelTarget()
    {
        // assuming there is only one CAD trackable in dataSetOt1
        ModelTarget modelTarget = null;
        for(int i = 0; i < mDataset.getTrackables().size(); ++i)
        {
            Trackable trackable = mDataset.getTrackables().at(i);
            if (trackable instanceof ModelTarget)
            {
                modelTarget = (ModelTarget)trackable;
                break;
            }
        }
        return modelTarget;
    }


    @Override
    public boolean menuProcess(int command)
    {
        boolean result = true;
        
        switch (command)
        {
            case CMD_BACK:
                finish();
                break;

            case CMD_AUTOFOCUS:

                if (mContAutofocus)
                {
                    result = CameraDevice.getInstance().setFocusMode(
                            CameraDevice.FOCUS_MODE.FOCUS_MODE_NORMAL);

                    if (result)
                    {
                        mContAutofocus = false;
                    } else
                    {
                        showToast(getString(R.string.menu_contAutofocus_error_off));
                        Log.e(LOGTAG,
                                getString(R.string.menu_contAutofocus_error_off));
                    }
                } else
                {
                    result = CameraDevice.getInstance().setFocusMode(
                            CameraDevice.FOCUS_MODE.FOCUS_MODE_CONTINUOUSAUTO);

                    if (result)
                    {
                        mContAutofocus = true;
                    } else
                    {
                        showToast(getString(R.string.menu_contAutofocus_error_on));
                        Log.e(LOGTAG,
                                getString(R.string.menu_contAutofocus_error_on));
                    }
                }

                break;

            case CMD_NEXT_GUIDEVIEW:
                ModelTarget modelTarget = getModelTarget();

                if (modelTarget == null)
                {
                    Log.e(LOGTAG, "Could not toggle guide view. Model Target not found.");
                    return false;
                }

                int maxGuideViewIndex = modelTarget.getGuideViews().size() - 1;

                mActiveGuideViewIndex = (modelTarget.getActiveGuideViewIndex() < maxGuideViewIndex) ?
                        mActiveGuideViewIndex + 1 : 0;

                result = modelTarget.setActiveGuideViewIndex(mActiveGuideViewIndex);
                break;

            default:
                if (command >= mStartDatasetsIndex
                        && command < mStartDatasetsIndex + NUM_DATASETS)
                {
                    mActiveGuideViewIndex = 0;
                    mSwitchDatasetAsap = true;
                    mCurrentDatasetSelectionIndex = command - mStartDatasetsIndex;
                }
                break;
        }
        
        return result;
    }

    private void showToast(String text)
    {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }


    public void checkForRelocalization(final int statusInfo)
    {
        if (mCurrentStatusInfo == statusInfo)
        {
            return;
        }

        mCurrentStatusInfo = statusInfo;

        if (mCurrentStatusInfo == TrackableResult.STATUS_INFO.RELOCALIZING)
        {
            // If the status is RELOCALIZING, start the timer
            if (!mStatusDelayTimer.isRunning())
            {
                mStatusDelayTimer.startTimer();
            }
        }
        else
        {
            // If the status is not RELOCALIZING, stop the timers and hide the message
            if (mStatusDelayTimer.isRunning())
            {
                mStatusDelayTimer.stopTimer();
            }

            if (mRelocalizationTimer.isRunning())
            {
                mRelocalizationTimer.stopTimer();
            }

            runOnUiThread(new Runnable()
            {
                @Override
                public void run()
                {
                    mSampleAppMessage.hide();
                }
            });
        }
    }
}
