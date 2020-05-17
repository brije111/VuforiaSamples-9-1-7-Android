/*===============================================================================
Copyright (c) 2020 PTC Inc. All Rights Reserved.

Vuforia is a trademark of PTC Inc., registered in the United States and other
countries.
===============================================================================*/

package com.vuforia.engine.CoreSamples.app.GroundPlane;

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
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.vuforia.CameraDevice;
import com.vuforia.DeviceTracker;
import com.vuforia.PositionalDeviceTracker;
import com.vuforia.SmartTerrain;
import com.vuforia.State;
import com.vuforia.TrackableResult;
import com.vuforia.Tracker;
import com.vuforia.TrackerManager;
import com.vuforia.Vec2F;
import com.vuforia.Vuforia;
import com.vuforia.engine.CoreSamples.R;
import com.vuforia.engine.SampleApplication.SampleActivityBase;
import com.vuforia.engine.SampleApplication.utils.SampleAppTimer;
import com.vuforia.engine.CoreSamples.ui.SampleAppMenu.SampleAppMenu;
import com.vuforia.engine.CoreSamples.ui.SampleAppMenu.SampleAppMenuGroup;
import com.vuforia.engine.CoreSamples.ui.SampleAppMenu.SampleAppMenuInterface;
import com.vuforia.engine.CoreSamples.ui.SampleAppMessage;
import com.vuforia.engine.SampleApplication.SampleApplicationControl;
import com.vuforia.engine.SampleApplication.SampleApplicationException;
import com.vuforia.engine.SampleApplication.SampleApplicationSession;
import com.vuforia.engine.SampleApplication.utils.LoadingDialogHandler;
import com.vuforia.engine.SampleApplication.utils.SampleApplicationGLView;
import com.vuforia.engine.SampleApplication.utils.SampleGestureListener;
import com.vuforia.engine.SampleApplication.utils.Texture;

import java.util.ArrayList;
import java.util.Vector;


/**
 * The main activity for the GroundPlane sample.
 * Ground Plane allows users to augment objects on a real-world surface or in mid-air.
 *
 * This class does high-level handling of the Vuforia lifecycle and any UI updates
 *
 * For GroundPlane-specific rendering, check out GroundPlaneRenderer.java
 * For the low-level Vuforia lifecycle code, check out SampleApplicationSession.java
 */
public class GroundPlane extends SampleActivityBase implements SampleApplicationControl,
        SampleAppMenuInterface
{
    private static final String LOGTAG = "GroundPlane";

    private SampleApplicationSession vuforiaAppSession;

    private SampleApplicationGLView mGlView;

    private GroundPlaneRenderer mRenderer;

    private GestureDetector mGestureDetector;
    private SampleGestureListener mGestureListener;

    // The textures we will use for rendering:
    private Vector<Texture> mTextures;

    private RelativeLayout mUILayout;

    private SampleAppMenu mSampleAppMenu;
    private SampleAppMessage mSampleAppMessage;

    private final LoadingDialogHandler loadingDialogHandler = new LoadingDialogHandler(this);

    RelativeLayout mGroundPlaneLayout;
    private LinearLayout mInstructionsView;

    // Buttons used to toggle different Ground Plane modes
    private ImageButton btnDrone;
    private ImageButton btnAstro;
    private ImageButton btnFurniture;

    private TextView topbarTitle;
    private TextView instructionText;
    private ImageView modeIndicator;
    private int mInstructionsState;

    private boolean mTrackersSuccessfullyInitialized = true;

    // Alert Dialog used to display SDK errors
    private AlertDialog mErrorDialog;

    private SampleAppTimer mRelocalizationTimer;
    private SampleAppTimer mStatusDelayTimer;

    // Called when the activity first starts or the user navigates back to an
    // activity.
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        Log.d(LOGTAG, "onCreate");
        super.onCreate(savedInstanceState);

        vuforiaAppSession = new SampleApplicationSession(this);

        startLoadingAnimation();

        vuforiaAppSession
                .initAR(this, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        mGroundPlaneLayout = mUILayout.findViewById(R.id.ground_plane_layout);
        mInstructionsView = mUILayout.findViewById(R.id.instructions_view);
        btnAstro = mUILayout.findViewById(R.id.btn_astro);
        btnDrone = mUILayout.findViewById(R.id.btn_drone);
        btnFurniture = mUILayout.findViewById(R.id.btn_furniture);
        ImageButton btnReset = mUILayout.findViewById(R.id.btn_reset);
        topbarTitle = mUILayout.findViewById(R.id.topbar_title);
        instructionText = mUILayout.findViewById(R.id.instruction_text);
        modeIndicator = mUILayout.findViewById(R.id.mode_indicator);

        mGestureDetector = new GestureDetector(this, new GestureListener());
        mGestureListener = new SampleGestureListener();

        mSampleAppMessage = new SampleAppMessage(this, mGroundPlaneLayout, mUILayout.findViewById(R.id.topbar_view), false);
        mRelocalizationTimer = new SampleAppTimer(10000, 1000)
        {
            @Override
            public void onFinish()
            {
                if (mRenderer != null)
                {
                    mRenderer.resetGroundPlane(true);
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

        // Load any sample specific textures:
        mTextures = new Vector<>();
        loadTextures();

        btnAstro.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                boolean isActivated = btnAstro.isActivated();
                if (!isActivated)
                {
                    mRenderer.setMode(GroundPlaneRenderer.SAMPLE_APP_INTERACTIVE_MODE);
                }
            }
        });

        btnDrone.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                boolean isActivated = btnDrone.isActivated();
                if (!isActivated)
                {
                    mRenderer.setMode(GroundPlaneRenderer.SAMPLE_APP_MIDAIR_MODE);
                }
            }
        });

        btnFurniture.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                boolean isActivated = btnFurniture.isActivated();
                if (!isActivated)
                {
                    mRenderer.setMode(GroundPlaneRenderer.SAMPLE_APP_FURNITURE_MODE);
                }
            }
        });

        btnReset.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                if (mRenderer != null)
                {
                    mRenderer.resetGroundPlane(true);
                }
            }
        });
    }


    private class GestureListener extends
            GestureDetector.SimpleOnGestureListener
    {
        // Used to set autofocus one second after a manual focus is triggered
        @Override
        public boolean onDown(MotionEvent e)
        {
            return true;
        }

        // Process Single Tap event to trigger autofocus
        @Override
        public boolean onSingleTapUp(MotionEvent e)
        {
            if (mRenderer == null) { return false; }

            mRenderer.handleTap();
            return true;
        }
    }


    // Load specific textures from the APK, which we will later use for rendering.
    private void loadTextures()
    {
        mTextures.add(Texture.loadTextureFromApk("astronaut.png", getAssets()));
        mTextures.add(Texture.loadTextureFromApk("drone.png", getAssets()));
        mTextures.add(Texture.loadTextureFromApk("GroundPlane/reticle_interactive_2d.png", getAssets()));
        mTextures.add(Texture.loadTextureFromApk("GroundPlane/reticle_midair.png", getAssets()));
        mTextures.add(Texture.loadTextureFromApk("GroundPlane/reticle_interactive_3d.png", getAssets()));
        mTextures.add(Texture.loadTextureFromApk("GroundPlane/shadow.png", getAssets()));
        mTextures.add(Texture.loadTextureFromApk("GroundPlane/reticle_translate.png", getAssets()));
        mTextures.add(Texture.loadTextureFromApk("GroundPlane/reticle_rotate.png", getAssets()));
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

        if (mRenderer != null)
        {
            mRenderer.unloadModels();
            mRenderer = null;
        }

        // Unload texture:
        mTextures.clear();
        mTextures = null;

        System.gc();
    }


    private void initApplicationAR()
    {
        // Create OpenGL ES view:
        int depthSize = 16;
        int stencilSize = 0;
        boolean translucent = Vuforia.requiresAlpha();

        mGlView = new SampleApplicationGLView(this);
        mGlView.init(translucent, depthSize, stencilSize);

        mRenderer = new GroundPlaneRenderer(this, vuforiaAppSession);
        mRenderer.setTextures(mTextures);
        mRenderer.setMode(GroundPlaneRenderer.SAMPLE_APP_FURNITURE_MODE);
        mGlView.setRenderer(mRenderer);
        mGlView.setPreserveEGLContextOnPause(true);

        setRendererReference(mRenderer);
    }


    private void startLoadingAnimation()
    {
        mUILayout = (RelativeLayout) View.inflate(this, R.layout.camera_overlay_ground_plane,
                null);

        mUILayout.setVisibility(View.VISIBLE);
        mUILayout.setBackgroundColor(Color.BLACK);

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


    @Override
    public boolean doLoadTrackersData()
    {
        return true;
    }


    @Override
    public boolean doUnloadTrackersData()
    {
        return true;
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

            if (mRenderer.areModelsLoaded())
            {
                showProgressIndicator(false);
            }

            ArrayList<View> viewsToHide = new ArrayList<>();
            viewsToHide.add(mGroundPlaneLayout);

            mSampleAppMenu = new SampleAppMenu(this, this, getString(R.string.feature_ground_plane),
                    mGlView, mUILayout, viewsToHide);
            setSampleAppMenuSettings();

            vuforiaAppSession.startAR();

        }
        else
        {
            Log.e(LOGTAG, exception.getString());
            showInitializationErrorMessage(exception.getString(), mTrackersSuccessfullyInitialized);
        }
    }


    public void showProgressIndicator(boolean show)
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


    private void showInitializationErrorMessage(String message, final boolean stopActivity)
    {
        final String errorMessage = message;
        runOnUiThread(new Runnable()
        {
            public void run()
            {
                // Generates an Alert Dialog to show the error message
                AlertDialog.Builder builder = new AlertDialog.Builder(
                        GroundPlane.this);
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
                                        if(stopActivity)
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
        // Initialize the Positional Device and Smart Terrain Trackers
        TrackerManager trackerManager = TrackerManager.getInstance();

        DeviceTracker deviceTracker = (PositionalDeviceTracker)
                trackerManager.initTracker(PositionalDeviceTracker.getClassType());

        Tracker smartTerrain = trackerManager.initTracker(SmartTerrain.getClassType());

        boolean trackersInitialized = true;

        if (deviceTracker != null)
        {
            Log.i(LOGTAG, "Successfully initialized Device Tracker");
        }
        else
        {
            Log.e(LOGTAG, "Failed to initialize Device Tracker");
            trackersInitialized = false;
        }

        if (smartTerrain != null)
        {
            Log.i(LOGTAG, "Successfully initialized Smart Terrain");
        }
        else
        {
            Log.e(LOGTAG, "Failed to initialize Smart Terrain");
            trackersInitialized = false;
        }

        if(!trackersInitialized)
        {
            showInitializationErrorMessage(getString(R.string.INIT_ERROR_TRACKERS_NOT_INITIALIZED), true);
        }

        mTrackersSuccessfullyInitialized = trackersInitialized;

        return trackersInitialized;
    }


    @Override
    public boolean doStartTrackers()
    {
        // Indicate if the trackers were started correctly
        TrackerManager trackerManager = TrackerManager.getInstance();
        Tracker deviceTracker = trackerManager.getTracker(PositionalDeviceTracker.getClassType());

        if (deviceTracker != null && deviceTracker.start())
        {
            Log.i(LOGTAG, "Successfully started Device Tracker");
        }
        else
        {
            Log.e(LOGTAG, "Failed to start Device Tracker");
            return false;
        }

        Tracker smartTerrain = trackerManager.getTracker(SmartTerrain.getClassType());

        if (smartTerrain != null && smartTerrain.start())
        {
            Log.i(LOGTAG, "Successfully started Smart Terrain");
        }
        else
        {
            Log.e(LOGTAG, "Failed to start Smart Terrain");
            return false;
        }

        return true;
    }


    @Override
    public boolean doStopTrackers()
    {
        TrackerManager trackerManager = TrackerManager.getInstance();

        // Stop the device tracker
        Tracker deviceTracker = trackerManager.getTracker(PositionalDeviceTracker.getClassType());

        boolean succesfullyStoppedTrackers = true;

        if (deviceTracker != null)
        {
            deviceTracker.stop();
            Log.i(LOGTAG, "Successfully stopped the Device Tracker");
        }
        else
        {
            Log.e(LOGTAG, "Failed to stop Device Tracker");
            succesfullyStoppedTrackers = false;
        }

        // Stop Smart Terrain
        Tracker smartTerrain = trackerManager.getTracker(SmartTerrain.getClassType());

        if (smartTerrain != null)
        {
            smartTerrain.stop();
            Log.i(LOGTAG, "Successfully stopped Smart Terrain");
        }
        else
        {
            Log.e(LOGTAG, "Failed to stop Smart Terrain");
            succesfullyStoppedTrackers = false;
        }

        return succesfullyStoppedTrackers;
    }


    @Override
    public boolean doDeinitTrackers()
    {
        TrackerManager trackerManager = TrackerManager.getInstance();

        if (trackerManager.deinitTracker(PositionalDeviceTracker.getClassType()))
        {
            Log.i(LOGTAG, "Successfully deinit Device Tracker");
        }
        else
        {
            Log.e(LOGTAG, "Failed to deinit Device Tracker");
            return false;
        }

        if (trackerManager.deinitTracker(SmartTerrain.getClassType()))
        {
            Log.i(LOGTAG, "Successfully deinit Smart Terrain");
        }
        else
        {
            Log.e(LOGTAG, "Failed to deinit Smart Terrain");
            return false;
        }

        return true;
    }


    // This function handles the gestures required to move the furniture model
    @Override
    public boolean onTouchEvent(MotionEvent event)
    {
        mGestureDetector.onTouchEvent(event);

        if (mRenderer == null) { return false; }

        if (mRenderer.mCurrentMode == GroundPlaneRenderer.SAMPLE_APP_FURNITURE_MODE)
        {
            int action = mGestureListener.onTouchEvent(event);

            switch (action)
            {
                case MotionEvent.ACTION_MOVE:
                {

                    if (mRenderer.getFurnitureAnchor() != null)
                    {
                        Vec2F dragCoords = new Vec2F(mGestureListener.getFirstTouch().getX(),
                                mGestureListener.getFirstTouch().getY());
                        mRenderer.setTranslateCoordinates(dragCoords);

                        if (!mRenderer.isModelTranslating() && !mGestureListener.isRotating() && !mGestureListener.isFirstTouchInsideThreshold())
                        {
                            mRenderer.setModelTranslating(true);
                        }

                        if(mGestureListener.isRotating())
                        {
                            mRenderer.setCurrentRotation(mGestureListener.getRotationAngle());
                        }
                    }

                    break;
                }

                case MotionEvent.ACTION_POINTER_DOWN:
                {
                    if(mRenderer.isModelTranslating())
                    {
                        mRenderer.setModelTranslating(false);
                    }

                    mRenderer.setModelRotating(true);
                    break;
                }

                case MotionEvent.ACTION_POINTER_UP:
                {
                    if(mRenderer.isModelRotating())
                    {
                        mRenderer.setModelRotating(false);
                    }
                    break;
                }

                case MotionEvent.ACTION_UP:
                {
                    if (mRenderer.isModelTranslating())
                    {
                        mRenderer.setModelTranslating(false);
                    }

                    if (mRenderer.isModelRotating())
                    {
                        mRenderer.setModelRotating(false);
                    }
                }
            }
        }


        // Process the Gestures
        if (mSampleAppMenu != null)
            mSampleAppMenu.processEvent(event);

        return true;
    }

    // Menu options
    private final static int CMD_BACK = -1;

    private void setSampleAppMenuSettings()
    {
        SampleAppMenuGroup group;

        group = mSampleAppMenu.addGroup("", false);
        group.addTextItem(getString(R.string.menu_back), CMD_BACK);

        mSampleAppMenu.setSwipeEnabled(false);

        mSampleAppMenu.attachMenu();
    }

    // In this function you can define the desired behavior for each menu option
    // Each case corresponds to a menu option
    @Override
    public boolean menuProcess(int command)
    {
        switch (command)
        {
            case CMD_BACK:
                finish();
                break;
        }

        return true;
    }


    public void setModeUI(int mode)
    {
        switch(mode)
        {
            case GroundPlaneRenderer.SAMPLE_APP_INTERACTIVE_MODE:
            {
                // Set button state
                btnAstro.setActivated(true);
                btnDrone.setActivated(false);
                btnFurniture.setActivated(false);

                // Set topbar elements
                topbarTitle.setText(getResources().getString(R.string.feature_ground_plane));
                modeIndicator.setBackgroundResource(R.drawable.icon_ground_plane);

                break;
            }

            case GroundPlaneRenderer.SAMPLE_APP_MIDAIR_MODE:
            {
                // Set button state
                btnDrone.setActivated(true);
                btnAstro.setActivated(false);
                btnFurniture.setActivated(false);

                // Set topbar elements
                topbarTitle.setText(getResources().getString(R.string.mode_mid_air));
                modeIndicator.setBackgroundResource(R.drawable.icon_mid_air);

                break;
            }
            case GroundPlaneRenderer.SAMPLE_APP_FURNITURE_MODE:
            {
                // Set button state
                btnFurniture.setActivated(true);
                btnAstro.setActivated(false);
                btnDrone.setActivated(false);

                // Set topbar elements
                topbarTitle.setText(getResources().getString(R.string.mode_product_placement));
                modeIndicator.setBackgroundResource(R.drawable.icon_content_placement);

                break;
            }
        }
    }


    public void setInstructionsState(final int instructionsState)
    {
        if (mInstructionsState != instructionsState)
        {
            final String instruction;
            int instructionStringId;
            final boolean hideInstructions;

            switch (instructionsState)
            {
                case GroundPlaneRenderer.INSTRUCTION_POINT_TO_GROUND:
                    instructionStringId = R.string.instruct_point_device;
                    hideInstructions = false;
                    break;
                case GroundPlaneRenderer.INSTRUCTION_TAP_TO_PLACE:
                    instructionStringId = R.string.instruct_touch_screen;
                    hideInstructions = false;
                    break;
                case GroundPlaneRenderer.INSTRUCTION_GESTURES_INSTRUCTIONS:
                    instructionStringId = R.string.instruct_gestures;
                    hideInstructions = false;
                    break;
                case GroundPlaneRenderer.INSTRUCTION_LIMITED_MOVE_DEVICE:
                    instructionStringId = R.string.instruct_limited_move_device;
                    hideInstructions = false;
                    break;
                case GroundPlaneRenderer.INSTRUCTION_PRODUCT_PLACEMENT:
                case GroundPlaneRenderer.INSTRUCTION_UNDEFINED:
                default:
                    instructionStringId = R.string.instruct_empty;
                    hideInstructions = true;
                    break;
            }

            instruction = getResources().getString(instructionStringId);

            runOnUiThread(new Runnable()
            {
                @Override
                public void run()
                {
                    instructionText.setText(instruction);
                    mInstructionsState = instructionsState;
                    mInstructionsView.setVisibility(hideInstructions ? View.INVISIBLE : View.VISIBLE);
                }
            });
        }
    }


    void setStatusInfo(final int statusInfo)
    {
        runOnUiThread(new Runnable()
        {
            public void run()
            {
                boolean hideToast = false;
                String statusMessage = null;

                switch(statusInfo)
                {
                    case TrackableResult.STATUS_INFO.NORMAL:
                    case TrackableResult.STATUS_INFO.UNKNOWN:
                        hideToast = true;
                        break;

                    case TrackableResult.STATUS_INFO.INITIALIZING:
                        statusMessage = "Point your device to the floor and move to scan!";
                        break;

                    case TrackableResult.STATUS_INFO.RELOCALIZING:

                        // Start timer to show relocalizing message after 1s
                        if (!mStatusDelayTimer.isRunning())
                        {
                            mStatusDelayTimer.startTimer();
                        }

                        break;

                    case TrackableResult.STATUS_INFO.INSUFFICIENT_FEATURES:
                        statusMessage = "Not enough visual features in the scene";
                        break;

                    case TrackableResult.STATUS_INFO.INSUFFICIENT_LIGHT:
                        statusMessage = "Not enough light in the scene";
                        break;

                    case TrackableResult.STATUS_INFO.EXCESSIVE_MOTION:
                        statusMessage = "Move slower";
                        break;

                    default:
                        hideToast = true;
                        break;
                }

                if (hideToast)
                {
                    mSampleAppMessage.hide();
                }
                else if (statusMessage != null
                    && (!statusMessage.equals(mSampleAppMessage.getMessage())
                    || mSampleAppMessage.isHidden()))
                {
                    mSampleAppMessage.show(statusMessage);
                }
            }
        });

        // Stop all timers if not relocalizing
        if (statusInfo != TrackableResult.STATUS_INFO.RELOCALIZING)
        {
            if (mStatusDelayTimer.isRunning())
            {
                mStatusDelayTimer.stopTimer();
            }

            if (mRelocalizationTimer.isRunning())
            {
                mRelocalizationTimer.stopTimer();
            }
        }
    }
}
