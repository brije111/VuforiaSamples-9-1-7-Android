/*===============================================================================
Copyright (c) 2020 PTC Inc. All Rights Reserved.

Copyright (c) 2012-2014 Qualcomm Connected Experiences, Inc. All Rights Reserved.

Vuforia is a trademark of PTC Inc., registered in the United States and other 
countries.
===============================================================================*/

package com.vuforia.engine.CoreSamples.app.VirtualButtons;

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
import com.vuforia.ObjectTracker;
import com.vuforia.Rectangle;
import com.vuforia.STORAGE_TYPE;
import com.vuforia.State;
import com.vuforia.Trackable;
import com.vuforia.Tracker;
import com.vuforia.TrackerManager;
import com.vuforia.VirtualButton;
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
 * The main activity for the VirtualButtons sample.
 * Virtual Buttons allows users to create user-interactive areas within a target.
 * To trigger the button, cover the area with an object such as your finger
 *
 * This class does high-level handling of the Vuforia lifecycle and any UI updates
 *
 * For VirtualButton-specific rendering, check out VirtualButtonRenderer.java
 * For the low-level Vuforia lifecycle code, check out SampleApplicationSession.java
 */
public class VirtualButtons extends SampleActivityBase implements
    SampleApplicationControl, SampleAppMenuInterface
{
    private static final String LOGTAG = "VirtualButtons";
    
    private SampleApplicationSession vuforiaAppSession;

    private SampleApplicationGLView mGlView;

    private VirtualButtonRenderer mRenderer;
    
    private RelativeLayout mUILayout;
    
    private GestureDetector mGestureDetector;
    
    private SampleAppMenu mSampleAppMenu;
    ArrayList<View> mSettingsAdditionalViews = new ArrayList<>();
    
    private final LoadingDialogHandler loadingDialogHandler = new LoadingDialogHandler(
        this);
    
    // The textures we will use for rendering:
    private Vector<Texture> mTextures;
    
    private DataSet mDataSet = null;
    
    // Virtual Button runtime creation:
    private boolean updateBtns = false;
    public final String[] virtualButtonColors = { "red", "blue", "yellow", "green" };
    
    // Enumeration for masking button indices into single integer:
    private static final int BUTTON_1 = 1;
    private static final int BUTTON_2 = 2;
    private static final int BUTTON_3 = 4;
    private static final int BUTTON_4 = 8;
    
    private byte buttonMask = 0;
    static final int NUM_BUTTONS = 4;
    
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
        mTextures.add(Texture.loadTextureFromApk("TextureTeapotBrass.png",
            getAssets()));
        mTextures.add(Texture.loadTextureFromApk("TextureTeapotRed.png",
            getAssets()));
        mTextures.add(Texture.loadTextureFromApk("TextureTeapotBlue.png",
            getAssets()));
        mTextures.add(Texture.loadTextureFromApk(
            "VirtualButtons/TextureTeapotYellow.png", getAssets()));
        mTextures.add(Texture.loadTextureFromApk(
            "VirtualButtons/TextureTeapotGreen.png", getAssets()));
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
        return ((mSampleAppMenu != null && mSampleAppMenu.processEvent(event))
                || mGestureDetector.onTouchEvent(event));
    }
    
    
    private void startLoadingAnimation()
    {
        mUILayout = (RelativeLayout) View.inflate(this, R.layout.camera_overlay,null);
        
        mUILayout.setVisibility(View.VISIBLE);
        mUILayout.setBackgroundColor(Color.BLACK);

        RelativeLayout topbarLayout = mUILayout.findViewById(R.id.topbar_layout);
        topbarLayout.setVisibility(View.VISIBLE);

        TextView title = mUILayout.findViewById(R.id.topbar_title);
        title.setText(getText(R.string.feature_virtual_buttons));

        mSettingsAdditionalViews.add(topbarLayout);

        loadingDialogHandler.mLoadingDialogContainer = mUILayout
            .findViewById(R.id.loading_indicator);
        
        // Shows the loading indicator at start
        showProgressIndicator(true);

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
        
        mRenderer = new VirtualButtonRenderer(this, vuforiaAppSession);
        mRenderer.setTextures(mTextures);
        mGlView.setRenderer(mRenderer);
        mGlView.setPreserveEGLContextOnPause(true);

        setRendererReference(mRenderer);
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
    public boolean doStartTrackers()
    {
        // Indicate if the trackers were started correctly
        boolean result = true;
        
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
        
        if (mDataSet != null)
        {
            if (!objectTracker.deactivateDataSet(mDataSet))
            {
                Log.d(
                    LOGTAG,
                    "Failed to destroy the tracking data set because the data set could not be deactivated.");
                result = false;
            } else if (!objectTracker.destroyDataSet(mDataSet))
            {
                Log.d(LOGTAG, "Failed to destroy the tracking data set.");
                result = false;
            }
            
            if (result)
                Log.d(LOGTAG, "Successfully destroyed the data set.");
            
            mDataSet = null;
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

            loadingDialogHandler.sendEmptyMessage(LoadingDialogHandler.HIDE_LOADING_DIALOG);
            
            // Sets the layout background to transparent
            mUILayout.setBackgroundColor(Color.TRANSPARENT);
            
            mSampleAppMenu = new SampleAppMenu(this, this, "Virtual Buttons",
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
                    VirtualButtons.this);
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
        if (updateBtns)
        {
            // Update() runs in the tracking thread, therefore it is guaranteed
            // that the tracker is not doing anything at this point.
            // Hence reconfiguration is possible.
            
            ObjectTracker ot = (ObjectTracker) (TrackerManager.getInstance()
                .getTracker(ObjectTracker.getClassType()));
            
            // Deactivate the data set prior to reconfiguration:
            ot.deactivateDataSet(mDataSet);

            if (mDataSet.getTrackables().size() <= 0)
            {
                Log.e(LOGTAG, "Could not deactivate dataset!");
                return;
            }

            Trackable trackable = mDataSet.getTrackables().at(0);
            ImageTarget imageTarget = (ImageTarget) (trackable);

            // Check to see if any button has been enabled/disabled from the menu
            if ((buttonMask & BUTTON_1) != 0)
            {
                Log.d(LOGTAG, "Toggle Button 1");
                
                if (toggleVirtualButton(imageTarget, virtualButtonColors[0],
                    -0.10868f, -0.05352f, -0.07575f, -0.06587f))
                {
                    Log.d(LOGTAG, "Successfully toggled Button 1");
                }
                else
                {
                    Log.e(LOGTAG, "Failed to toggle Button 1");
                }
                
            }
            if ((buttonMask & BUTTON_2) != 0)
            {
                Log.d(LOGTAG, "Toggle Button 2");
                
                if (toggleVirtualButton(imageTarget, virtualButtonColors[1],
                    -0.04528f, -0.05352f, -0.01235f, -0.06587f))
                {
                    Log.d(LOGTAG, "Successfully toggled Button 2");
                }
                else
                {
                    Log.e(LOGTAG, "Failed to toggle Button 2");
                }
            }
            if ((buttonMask & BUTTON_3) != 0)
            {
                Log.d(LOGTAG, "Toggle Button 3");
                
                if (toggleVirtualButton(imageTarget, virtualButtonColors[2],
                    0.01482f, -0.05352f, 0.04775f, -0.06587f))
                {
                    Log.d(LOGTAG, "Successfully toggled Button 3");
                }
                else
                {
                    Log.e(LOGTAG, "Failed to toggle Button 3");
                }
            }
            if ((buttonMask & BUTTON_4) != 0)
            {
                Log.d(LOGTAG, "Toggle Button 4");
                
                if (toggleVirtualButton(imageTarget, virtualButtonColors[3],
                    0.07657f, -0.05352f, 0.10950f, -0.06587f))
                {
                    Log.d(LOGTAG, "Successfully toggled Button 4");
                }
                else
                {
                    Log.e(LOGTAG, "Failed to toggle Button 4");
                }
            }
            
            // Reactivate the data set:
            ot.activateDataSet(mDataSet);
            
            buttonMask = 0;
            updateBtns = false;
        }
    }
    
    
    // Create/destroy a Virtual Button at runtime
    //
    // NOTE: This will NOT work if the tracker is active!
    private boolean toggleVirtualButton(ImageTarget imageTarget, String name,
                                        float left, float top, float right, float bottom)
    {
        Log.d(LOGTAG, "toggleVirtualButton");
        
        boolean buttonToggleSuccess = false;
        
        VirtualButton virtualButton = imageTarget.getVirtualButton(name);

        if (virtualButton != null)
        {
            Log.d(LOGTAG, "Destroying Virtual Button> " + name);
            buttonToggleSuccess = imageTarget
                .destroyVirtualButton(virtualButton);
        }
        else
        {
            Log.d(LOGTAG, "Creating Virtual Button> " + name);
            Rectangle vbRectangle = new Rectangle(left, top, right, bottom);
            VirtualButton virtualButton2 = imageTarget.createVirtualButton(
                name, vbRectangle);

            if (virtualButton2 != null)
            {
                // This is just a showcase. The values used here a set by
                // default on Virtual Button creation
                virtualButton2.setEnabled(true);
                virtualButton2.setSensitivity(VirtualButton.SENSITIVITY.MEDIUM);
                buttonToggleSuccess = true;
            }
        }
        
        return buttonToggleSuccess;
    }
    

    // Toggles the enabled state of the Virtual Button
    private void addButtonToToggle(int virtualButtonIdx)
    {
        Log.d(LOGTAG, "addButtonToToggle");
        
        if (!(virtualButtonIdx >= 0 && virtualButtonIdx < NUM_BUTTONS))
        {
            Log.e(LOGTAG, "Could not add button");
            return;
        }
        
        switch (virtualButtonIdx)
        {
            case 0:
                buttonMask |= BUTTON_1;
                break;
            
            case 1:
                buttonMask |= BUTTON_2;
                break;
            
            case 2:
                buttonMask |= BUTTON_3;
                break;
            
            case 3:
                buttonMask |= BUTTON_4;
                break;
        }
        updateBtns = true;
    }
    
    
    @Override
    public boolean doLoadTrackersData()
    {
        // Get the image tracker:
        TrackerManager trackerManager = TrackerManager.getInstance();
        ObjectTracker objectTracker = (ObjectTracker) (trackerManager
            .getTracker(ObjectTracker.getClassType()));
        if (objectTracker == null)
        {
            Log.d(
                LOGTAG,
                "Failed to load tracking data set because the ObjectTracker has not been initialized.");
            return false;
        }
        
        // Create the data set:
        mDataSet = objectTracker.createDataSet();
        if (mDataSet == null)
        {
            Log.d(LOGTAG, "Failed to create a new tracking data.");
            return false;
        }
        
        // Load the data set:
        if (!mDataSet.load("VirtualButtons/Wood.xml",
            STORAGE_TYPE.STORAGE_APPRESOURCE))
        {
            Log.d(LOGTAG, "Failed to load data set.");
            return false;
        }
        
        // Activate the data set:
        if (!objectTracker.activateDataSet(mDataSet))
        {
            Log.d(LOGTAG, "Failed to activate data set.");
            return false;
        }
        
        Log.d(LOGTAG, "Successfully loaded and activated data set.");
        return true;
    }

    // Menu options
    private final static int CMD_BACK = -1;
    private final static int CMD_BUTTON_RED = 1;
    private final static int CMD_BUTTON_BLUE = 2;
    private final static int CMD_BUTTON_YELLOW = 3;
    private final static int CMD_BUTTON_GREEN = 4;

    private void setSampleAppMenuSettings()
    {
        SampleAppMenuGroup group;
        
        group = mSampleAppMenu.addGroup("", false);
        group.addTextItem(getString(R.string.menu_back), -1);
        
        group = mSampleAppMenu.addGroup(getString(R.string.menu_virtual_buttons), true);
        group.addSelectionItem(getString(R.string.menu_button_red),
            CMD_BUTTON_RED, true);
        group.addSelectionItem(getString(R.string.menu_button_blue),
            CMD_BUTTON_BLUE, true);
        group.addSelectionItem(getString(R.string.menu_button_yellow),
            CMD_BUTTON_YELLOW, true);
        group.addSelectionItem(getString(R.string.menu_button_green),
            CMD_BUTTON_GREEN, true);
        
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
            
            case CMD_BUTTON_RED:
                addButtonToToggle(0);
                break;
            
            case CMD_BUTTON_BLUE:
                addButtonToToggle(1);
                break;
            
            case CMD_BUTTON_YELLOW:
                addButtonToToggle(2);
                break;
            
            case CMD_BUTTON_GREEN:
                addButtonToToggle(3);
                break;

            default:
                result = false;
                break;
        }
        
        return result;
    }
}
