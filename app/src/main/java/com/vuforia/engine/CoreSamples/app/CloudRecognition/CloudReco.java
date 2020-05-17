/*===============================================================================
Copyright (c) 2020 PTC Inc. All Rights Reserved.

Copyright (c) 2012-2014 Qualcomm Connected Experiences, Inc. All Rights Reserved.

Vuforia is a trademark of PTC Inc., registered in the United States and other 
countries.
===============================================================================*/

package com.vuforia.engine.CoreSamples.app.CloudRecognition;

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
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.TranslateAnimation;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.vuforia.CameraDevice;
import com.vuforia.ObjectTracker;
import com.vuforia.PositionalDeviceTracker;
import com.vuforia.State;
import com.vuforia.TargetFinder;
import com.vuforia.TargetSearchResult;
import com.vuforia.CloudRecoSearchResult;
import com.vuforia.TargetFinderQueryResult;
import com.vuforia.TargetSearchResultList;
import com.vuforia.TrackableResult;
import com.vuforia.Tracker;
import com.vuforia.TrackerManager;
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

import java.util.ArrayList;
import java.util.Vector;


/**
 * The main activity for the CloudReco sample.
 * Cloud Recognition allows users to detect a dataset using an internet connection by
 * creating and storing the dataset on the cloud (via the Target Manager) as opposed
 * to storing the dataset within your project.
 *
 * This class does high-level handling of the Vuforia lifecycle and any UI updates
 *
 * For CloudReco-specific rendering, check out CloudRecoRenderer.java
 * For the low-level Vuforia lifecycle code, check out SampleApplicationSession.java
 */
public class CloudReco extends SampleActivityBase implements SampleApplicationControl,
    SampleAppMenuInterface
{
    private static final String LOGTAG = "CloudReco";

    private SampleApplicationSession vuforiaAppSession;

    // Cloud Recognition specific error codes
    // These codes match the ones defined for the TargetFinder in Vuforia.jar
    private static final int UPDATE_ERROR_AUTHORIZATION_FAILED = -1;
    private static final int UPDATE_ERROR_PROJECT_SUSPENDED = -2;
    private static final int UPDATE_ERROR_NO_NETWORK_CONNECTION = -3;
    private static final int UPDATE_ERROR_SERVICE_NOT_AVAILABLE = -4;
    private static final int UPDATE_ERROR_BAD_FRAME_QUALITY = -5;
    private static final int UPDATE_ERROR_UPDATE_SDK = -6;
    private static final int UPDATE_ERROR_TIMESTAMP_OUT_OF_RANGE = -7;
    private static final int UPDATE_ERROR_REQUEST_TIMEOUT = -8;

    private SampleApplicationGLView mGlView;

    private CloudRecoRenderer mRenderer;
    
    private SampleAppMenu mSampleAppMenu;
    ArrayList<View> mSettingsAdditionalViews = new ArrayList<>();

    private SampleAppMessage mSampleAppMessage;
    private SampleAppTimer mRelocalizationTimer;
    private SampleAppTimer mStatusDelayTimer;

    private int mCurrentStatusInfo;
    
    private boolean mDeviceTracker = false;
    private boolean mFinderStarted = false;
    private boolean mResetTargetFinderTrackables = false;
    
    // The textures we will use for rendering:
    private Vector<Texture> mTextures;

    // The keys necessary in order to use Cloud Recognition
    // Generate your own keys via the Target Manager on the Vuforia developer website
    private static final String kAccessKey = "7f0e89e71629c9504a8ebe5b5086d50bf7281e81";
    private static final String kSecretKey = "f164075a59d802f12caa27337399515af29ee007";
    
    // View overlays to be displayed in the Augmented View
    private RelativeLayout mUILayout;
    private Button mBtnLayout;
    
    // Error message handling:
    private int mlastErrorCode = 0;
    private int mInitErrorCode = 0;
    private boolean mFinishActivityOnError;
    
    // Alert Dialog used to display SDK errors
    private AlertDialog mErrorDialog;
    
    private GestureDetector mGestureDetector;
    
    private final LoadingDialogHandler loadingDialogHandler = new LoadingDialogHandler(
        this);

    // Scan line and animation
    private View scanLine;
    private TranslateAnimation scanAnimation;

    private double mLastErrorTime;

    private boolean mIsDroidDevice = false;

    // The TargetFinder is used to dynamically search
    // for targets using an internet connection
    private TargetFinder mTargetFinder;


    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        Log.d(LOGTAG, "onCreate");
        super.onCreate(savedInstanceState);
        
        vuforiaAppSession = new SampleApplicationSession(this);
        
        startLoadingAnimation();
        
        vuforiaAppSession
            .initAR(this, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        
        // Creates the GestureDetector listener for processing double tap
        mGestureDetector = new GestureDetector(this, new GestureListener());
        
        mTextures = new Vector<>();
        loadTextures();
        
        mIsDroidDevice = android.os.Build.MODEL.toLowerCase().startsWith(
            "droid");

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
        mTextures.add(Texture.loadTextureFromApk("TextureTeapotRed.png",
            getAssets()));
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
        
        vuforiaAppSession.onPause();

        if (mGlView != null)
        {
            mGlView.setVisibility(View.INVISIBLE);
            mGlView.onPause();
        }
    }
    

    @Override
    protected void onDestroy()
    {
        Log.d(LOGTAG, "onDestroy");
        super.onDestroy();

        deinitCloudReco();

        try
        {
            vuforiaAppSession.stopAR();
        } catch (SampleApplicationException e)
        {
            Log.e(LOGTAG, e.getString());
        }
        
        System.gc();
    }
    
    
    private void deinitCloudReco()
    {
        if (mTargetFinder == null)
        {
            Log.e(LOGTAG,
                "Could not deinit cloud reco because it was not initialized");
            return;
        }
        
        mTargetFinder.deinit();
    }
    
    
    private void startLoadingAnimation()
    {
        // Inflates the Overlay Layout to be displayed above the Camera View
        mUILayout = (RelativeLayout) View.inflate(this, R.layout.camera_overlay_with_scanline,
            null);
        
        mUILayout.setVisibility(View.VISIBLE);
        mUILayout.setBackgroundColor(Color.BLACK);

        RelativeLayout topbarLayout = mUILayout.findViewById(R.id.topbar_layout);
        topbarLayout.setVisibility(View.VISIBLE);

        TextView title = mUILayout.findViewById(R.id.topbar_title);
        title.setText(getText(R.string.feature_cloud_reco));

        mSettingsAdditionalViews.add(topbarLayout);

        mBtnLayout = mUILayout.findViewById(R.id.reset_btn);

        loadingDialogHandler.mLoadingDialogContainer = mUILayout
            .findViewById(R.id.loading_indicator);
        loadingDialogHandler.mLoadingDialogContainer
            .setVisibility(View.VISIBLE);

        scanLine = mUILayout.findViewById(R.id.scan_line);
        scanLine.setVisibility(View.GONE);
        scanAnimation = new TranslateAnimation(
            TranslateAnimation.ABSOLUTE, 0f,
            TranslateAnimation.ABSOLUTE, 0f,
            TranslateAnimation.RELATIVE_TO_PARENT, 0f,
            TranslateAnimation.RELATIVE_TO_PARENT, 1.0f);
        scanAnimation.setDuration(4000);
        scanAnimation.setRepeatCount(-1);
        scanAnimation.setRepeatMode(Animation.REVERSE);
        scanAnimation.setInterpolator(new LinearInterpolator());

        addContentView(mUILayout, new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));
    }
    

    private void initApplicationAR()
    {
        // Create OpenGL ES view:
        int depthSize = 16;
        int stencilSize = 0;
        boolean translucent = Vuforia.requiresAlpha();
        
        // Initialize the GLView with proper flags
        mGlView = new SampleApplicationGLView(this);
        mGlView.init(translucent, depthSize, stencilSize);
        
        // Sets up the Renderer of the GLView
        mRenderer = new CloudRecoRenderer(vuforiaAppSession, this);
        mRenderer.setTextures(mTextures);
        mGlView.setRenderer(mRenderer);
        mGlView.setPreserveEGLContextOnPause(true);

        setRendererReference(mRenderer);
    }
    
    
    // Returns the error message for each error code
    private String getStatusDescString(int code)
    {
        if (code == UPDATE_ERROR_AUTHORIZATION_FAILED)
            return getString(R.string.UPDATE_ERROR_AUTHORIZATION_FAILED_DESC);
        if (code == UPDATE_ERROR_PROJECT_SUSPENDED)
            return getString(R.string.UPDATE_ERROR_PROJECT_SUSPENDED_DESC);
        if (code == UPDATE_ERROR_NO_NETWORK_CONNECTION)
            return getString(R.string.UPDATE_ERROR_NO_NETWORK_CONNECTION_DESC);
        if (code == UPDATE_ERROR_SERVICE_NOT_AVAILABLE)
            return getString(R.string.UPDATE_ERROR_SERVICE_NOT_AVAILABLE_DESC);
        if (code == UPDATE_ERROR_UPDATE_SDK)
            return getString(R.string.UPDATE_ERROR_UPDATE_SDK_DESC);
        if (code == UPDATE_ERROR_TIMESTAMP_OUT_OF_RANGE)
            return getString(R.string.UPDATE_ERROR_TIMESTAMP_OUT_OF_RANGE_DESC);
        if (code == UPDATE_ERROR_REQUEST_TIMEOUT)
            return getString(R.string.UPDATE_ERROR_REQUEST_TIMEOUT_DESC);
        if (code == UPDATE_ERROR_BAD_FRAME_QUALITY)
            return getString(R.string.UPDATE_ERROR_BAD_FRAME_QUALITY_DESC);
        else
        {
            return getString(R.string.UPDATE_ERROR_UNKNOWN_DESC);
        }
    }
    
    
    // Returns the error message header for each error code
    private String getStatusTitleString(int code)
    {
        if (code == UPDATE_ERROR_AUTHORIZATION_FAILED)
            return getString(R.string.UPDATE_ERROR_AUTHORIZATION_FAILED_TITLE);
        if (code == UPDATE_ERROR_PROJECT_SUSPENDED)
            return getString(R.string.UPDATE_ERROR_PROJECT_SUSPENDED_TITLE);
        if (code == UPDATE_ERROR_NO_NETWORK_CONNECTION)
            return getString(R.string.UPDATE_ERROR_NO_NETWORK_CONNECTION_TITLE);
        if (code == UPDATE_ERROR_SERVICE_NOT_AVAILABLE)
            return getString(R.string.UPDATE_ERROR_SERVICE_NOT_AVAILABLE_TITLE);
        if (code == UPDATE_ERROR_UPDATE_SDK)
            return getString(R.string.UPDATE_ERROR_UPDATE_SDK_TITLE);
        if (code == UPDATE_ERROR_TIMESTAMP_OUT_OF_RANGE)
            return getString(R.string.UPDATE_ERROR_TIMESTAMP_OUT_OF_RANGE_TITLE);
        if (code == UPDATE_ERROR_REQUEST_TIMEOUT)
            return getString(R.string.UPDATE_ERROR_REQUEST_TIMEOUT_TITLE);
        if (code == UPDATE_ERROR_BAD_FRAME_QUALITY)
            return getString(R.string.UPDATE_ERROR_BAD_FRAME_QUALITY_TITLE);
        else
        {
            return getString(R.string.UPDATE_ERROR_UNKNOWN_TITLE);
        }
    }
    

    private void showErrorMessage(int errorCode, double errorTime, boolean finishActivityOnError)
    {
        if (errorTime < (mLastErrorTime + 5.0) || errorCode == mlastErrorCode)
            return;

        mLastErrorTime = errorTime;
        mlastErrorCode = errorCode;
        mFinishActivityOnError = finishActivityOnError;
        
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
                    CloudReco.this);
                builder
                    .setMessage(
                        getStatusDescString(CloudReco.this.mlastErrorCode))
                    .setTitle(
                        getStatusTitleString(CloudReco.this.mlastErrorCode))
                    .setCancelable(false)
                    .setIcon(0)
                    .setPositiveButton(getString(R.string.button_OK),
                        new DialogInterface.OnClickListener()
                        {
                            public void onClick(DialogInterface dialog, int id)
                            {
                                if(mFinishActivityOnError)
                                {
                                    finish();
                                }
                                else
                                {
                                    dialog.dismiss();
                                }
                            }
                        });
                
                mErrorDialog = builder.create();
                mErrorDialog.show();
            }
        });
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
                    CloudReco.this);
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
    
    
    public void startFinderIfStopped()
    {
        if(!mFinderStarted)
        {
            if (mTargetFinder == null)
            {
                Log.e(LOGTAG, "Tried to start TargetFinder but was not initialized");
                return;
            }

            mTargetFinder.startRecognition();
            scanlineStart();
            
            mFinderStarted = true;
        }
    }
    
    
    public void stopFinderIfStarted()
    {
        if(mFinderStarted)
        {
            if (mTargetFinder == null)
            {
                Log.e(LOGTAG, "Tried to stop TargetFinder but was not initialized");
                return;
            }
            
            mTargetFinder.stop();
            scanlineStop();
            
            mFinderStarted = false;
        }
    }
    
    
    @Override
    public boolean onTouchEvent(MotionEvent event)
    {
        // Process the Gestures
        return (mSampleAppMenu != null && mSampleAppMenu.processEvent(event))
                || mGestureDetector.onTouchEvent(event);
    }
    
    
    @Override
    public boolean doLoadTrackersData()
    {
        Log.d(LOGTAG, "initCloudReco");
        
        // Get the object tracker:
        TrackerManager trackerManager = TrackerManager.getInstance();
        ObjectTracker objectTracker = (ObjectTracker) trackerManager
            .getTracker(ObjectTracker.getClassType());

        // Start the target finder using keys
        TargetFinder targetFinder = objectTracker.getTargetFinder();
        targetFinder.startInit(kAccessKey, kSecretKey);
        
        targetFinder.waitUntilInitFinished();
        
        int resultCode = targetFinder.getInitState();
        if (resultCode != TargetFinder.INIT_SUCCESS)
        {
            if(resultCode == TargetFinder.INIT_ERROR_NO_NETWORK_CONNECTION)
            {
                mInitErrorCode = UPDATE_ERROR_NO_NETWORK_CONNECTION;
            }
            else
            {
                mInitErrorCode = UPDATE_ERROR_SERVICE_NOT_AVAILABLE;
            }
                
            Log.e(LOGTAG, "Failed to initialize target finder.");
            return false;
        }

        mTargetFinder = targetFinder;
        
        return true;
    }
    
    
    @Override
    public boolean doUnloadTrackersData()
    {
        return true;
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

            mUILayout.bringToFront();
            mBtnLayout.setVisibility(View.VISIBLE);

            Button resetBtn = (Button) mUILayout.findViewById(R.id.reset_btn);

            resetBtn.setOnClickListener(new View.OnClickListener()
            {
                @Override
                public void onClick(View view)
                {
                    mResetTargetFinderTrackables = true;
                }
            });
            
            // Hides the Loading Dialog
            loadingDialogHandler.sendEmptyMessage(LoadingDialogHandler.HIDE_LOADING_DIALOG);
            
            mUILayout.setBackgroundColor(Color.TRANSPARENT);

            mSettingsAdditionalViews.add(mBtnLayout);
            mSampleAppMenu = new SampleAppMenu(this, this, "Cloud Reco",
                mGlView, mUILayout, mSettingsAdditionalViews);
            setSampleAppMenuSettings();

            vuforiaAppSession.startAR();

        }
        else  // Show error message if an exception was thrown during the init process
        {
            Log.e(LOGTAG, exception.getString());
            if(mInitErrorCode != 0)
            {
                showErrorMessage(mInitErrorCode,10, true);
            }
            else
            {
                showInitializationErrorMessage(exception.getString());
            }
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
    

    // Called every frame
    @Override
    public void onVuforiaUpdate(State state)
    {
        
        TargetFinder finder = mTargetFinder;
        if (finder == null)
        {
            Log.e(LOGTAG, "Tried to query TargetFinder but was not initialized");
            return;
        }
        
        // Check if there are new results available:
        TargetFinderQueryResult queryResult = finder.updateQueryResults();
        int queryStatus = queryResult.getStatus();
        
        // Show a message if we encountered an error:
        if (queryStatus < 0)
        {
            boolean closeAppAfterError = (
                            queryStatus == UPDATE_ERROR_NO_NETWORK_CONNECTION ||
                            queryStatus == UPDATE_ERROR_SERVICE_NOT_AVAILABLE);
            
            showErrorMessage(queryStatus, state.getFrame().getTimeStamp(), closeAppAfterError);
            
        }
        else if (queryStatus == TargetFinder.UPDATE_RESULTS_AVAILABLE)
        {
            TargetSearchResultList queryResultsList = queryResult.getResults();

            // Process new search results
            if (!queryResultsList.empty())
            {
                TargetSearchResult result = queryResultsList.at(0);
                CloudRecoSearchResult cloudRecoResult = null;
                if (result.isOfType(CloudRecoSearchResult.getClassType()))
                {
                    cloudRecoResult = (CloudRecoSearchResult)result;
                }
                
                // Check if this target is suitable for tracking:
                if (cloudRecoResult != null && cloudRecoResult.getTrackingRating() > 0)
                {
                    finder.enableTracking(cloudRecoResult);
                }
            }
        }

        if(mResetTargetFinderTrackables)
        {
            finder.clearTrackables();
            mResetTargetFinderTrackables = false;
        }
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
            Log.e(LOGTAG,
                  "Tracker not initialized. Tracker already initialized or the camera is already started");
            result = false;
        }
        else
        {
            Log.i(LOGTAG, "Tracker successfully initialized");
        }

        // Initialize the Positional Device Tracker
        PositionalDeviceTracker deviceTracker = (PositionalDeviceTracker)
                tManager.initTracker(PositionalDeviceTracker.getClassType());

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

        if (mTargetFinder == null)
        {
            Log.e(LOGTAG, "Tried to start TargetFinder but was not initialized");
            return false;
        }

        mTargetFinder.startRecognition();
        scanlineStart();
        mFinderStarted = true;

        // Start the Object Tracker
        // The Object Tracker tracks the target recognized by the target finder
        Tracker objectTracker = TrackerManager.getInstance().getTracker(
                ObjectTracker.getClassType());

        if (objectTracker != null)
        {
            result = objectTracker.start();
        }

        // Start device tracker if enabled
        if (isDeviceTrackerActive())
        {
            TrackerManager tManager = TrackerManager.getInstance();
            PositionalDeviceTracker deviceTracker = (PositionalDeviceTracker) tManager
                    .getTracker(PositionalDeviceTracker.getClassType());

            if (deviceTracker != null && deviceTracker.start())
            {
                Log.i(LOGTAG, "Successfully started Device Tracker");
            }
            else
            {
                Log.e(LOGTAG, "Failed to start Device Tracker");
            }
        }
        
        return result;
    }
    
    
    @Override
    public boolean doStopTrackers()
    {
        // Indicate if the trackers were stopped correctly
        boolean result = true;
        
        TrackerManager trackerManager = TrackerManager.getInstance();
        ObjectTracker objectTracker = (ObjectTracker) trackerManager
            .getTracker(ObjectTracker.getClassType());
        
        if(objectTracker != null)
        {
            objectTracker.stop();

            if (mTargetFinder == null)
            {
                Log.e(LOGTAG, "Tried to stop TargetFinder but was not initialized");
                return false;
            }

            TargetFinder targetFinder = mTargetFinder;
            targetFinder.stop();
            scanlineStop();
            mFinderStarted = false;
            
            // Clears the trackables
            targetFinder.clearTrackables();
        }
        else
        {
            result = false;
        }

        // Stop device tracker
        if(isDeviceTrackerActive())
        {
            Tracker deviceTracker = trackerManager.getTracker(PositionalDeviceTracker.getClassType());

            if (deviceTracker != null)
            {
                deviceTracker.stop();
                Log.i(LOGTAG, "Successfully stopped device tracker");
            }
            else
            {
                Log.e(LOGTAG, "Could not stop device tracker");
            }
        }
        
        return result;
    }
    
    
    @Override
    public boolean doDeinitTrackers()
    {
        TrackerManager tManager = TrackerManager.getInstance();

        // Indicate if the trackers were deinitialized correctly
        boolean result = tManager.deinitTracker(ObjectTracker.getClassType());
        tManager.deinitTracker(PositionalDeviceTracker.getClassType());
        
        return result;
    }


    private boolean isDeviceTrackerActive()
    {
        return mDeviceTracker;
    }


    // Menu options
    private final static int CMD_BACK = -1;
    private final static int CMD_DEVICE_TRACKER = 1;

    private void setSampleAppMenuSettings()
    {
        SampleAppMenuGroup group;
        
        group = mSampleAppMenu.addGroup("", false);
        group.addTextItem(getString(R.string.menu_back), -1);
        
        group = mSampleAppMenu.addGroup("", true);
        group.addSelectionItem(getString(R.string.menu_device_tracker),
                CMD_DEVICE_TRACKER, false);
        
        mSampleAppMenu.attachMenu();
    }
    

    // In this function you can define the desired behavior for each menu option
    // Each case corresponds to a menu option
    @Override
    public boolean menuProcess(int command)
    {
        boolean result = true;
        
        switch (command)
        {
            case CMD_BACK:
                finish();
                break;
            
            case CMD_DEVICE_TRACKER:

                TrackerManager trackerManager = TrackerManager.getInstance();
                PositionalDeviceTracker deviceTracker = (PositionalDeviceTracker)
                        trackerManager.getTracker(PositionalDeviceTracker.getClassType());

                if (deviceTracker != null)
                {
                    if (!mDeviceTracker)
                    {
                        if (!deviceTracker.start())
                        {
                            Log.e(LOGTAG,"Failed to start device tracker");
                            result = false;
                        }
                        else
                        {
                            Log.d(LOGTAG,"Successfully started device tracker");
                        }
                    }
                    else
                    {
                        deviceTracker.stop();
                        clearSampleAppMessage();
                    }
                }
                else
                {
                    Log.e(LOGTAG, "Device tracker is null!");
                    result = false;
                }

                if (result)
                {
                    mDeviceTracker = !mDeviceTracker;
                }
                else
                {
                    clearSampleAppMessage();
                }
                
                break;
        }
        
        return result;
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


    private void clearSampleAppMessage()
    {
        runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                if (mSampleAppMessage != null)
                {
                    mSampleAppMessage.hide();
                }
            }
        });
    }


    private void scanlineStart()
    {
        this.runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                scanLine.setVisibility(View.VISIBLE);
                scanLine.setAnimation(scanAnimation);
            }
        });
    }


    private void scanlineStop()
    {
        this.runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                scanLine.setVisibility(View.GONE);
                scanLine.clearAnimation();
            }
        });
    }
}
