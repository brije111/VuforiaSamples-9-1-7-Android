/*===============================================================================
Copyright (c) 2020 PTC Inc. All Rights Reserved.

Copyright (c) 2012-2015 Qualcomm Connected Experiences, Inc. All Rights Reserved.

Vuforia is a trademark of PTC Inc., registered in the United States and other 
countries.
===============================================================================*/


package com.vuforia.engine.SampleApplication;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.os.AsyncTask;
import android.util.Log;
import android.view.WindowManager;

import com.vuforia.CameraDevice;
import com.vuforia.Device;
import com.vuforia.FUSION_PROVIDER_TYPE;
import com.vuforia.INIT_ERRORCODE;
import com.vuforia.INIT_FLAGS;
import com.vuforia.PositionalDeviceTracker;
import com.vuforia.State;
import com.vuforia.TrackerManager;
import com.vuforia.Vuforia;
import com.vuforia.Vuforia.UpdateCallbackInterface;
import com.vuforia.engine.CoreSamples.R;

import java.lang.ref.WeakReference;

/**
 * This class handles the Vuforia Engine lifecycle
 * This class is used by all of the feature activities in this sample
 */
public class SampleApplicationSession implements UpdateCallbackInterface
{
    private static final String LOGTAG = "SampleAppSession";

    private WeakReference<Activity> mActivityRef;
    private final WeakReference<SampleApplicationControl> mSessionControlRef;
    
    // Vuforia Engine status flags
    private boolean mStarted = false;
    private boolean mCameraRunning = false;

    private int mVideoMode = CameraDevice.MODE.MODE_DEFAULT;
    
    // The async tasks that initialize the Vuforia Engine and Trackers:
    private InitVuforiaTask mInitVuforiaTask;
    private LoadTrackerTask mLoadTrackerTask;
    private ResumeVuforiaTask mResumeVuforiaTask;
    
    // An object used for synchronizing Vuforia Engine initialization, dataset loading
    // and the Android onDestroy() life cycle event. If the application is
    // destroyed while a data set is still being loaded, then we wait for the
    // loading operation to finish before shutting down Vuforia Engine:
    private final Object mLifecycleLock = new Object();
    
    // Vuforia Engine initialization flags:
    private int mVuforiaFlags = 0;
    
    public SampleApplicationSession(SampleApplicationControl sessionControl)
    {
        mSessionControlRef = new WeakReference<>(sessionControl);
    }

    public SampleApplicationSession(SampleApplicationControl sessionControl, int videoMode)
    {
        mSessionControlRef = new WeakReference<>(sessionControl);
        mVideoMode = videoMode;
    }
    
    
    // Initializes Vuforia Engine and sets up preferences.
    public void initAR(Activity activity, int screenOrientation)
    {
        SampleApplicationException vuforiaException = null;
        mActivityRef = new WeakReference<>(activity);
        
        if (screenOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR)
        {
            screenOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR;
        }
        
        // Apply screen orientation
        mActivityRef.get().setRequestedOrientation(screenOrientation);
        
        // As long as this window is visible to the user, keep the device's
        // screen turned on and bright:
        mActivityRef.get().getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Determines whether to use OpenGL 2.0,
        // OpenGL 3.0, DirectX (UWP), or Metal (iOS)
        mVuforiaFlags = INIT_FLAGS.GL_20;
        
        // Initialize Vuforia Engine asynchronously to avoid blocking the
        // main (UI) thread.
        //
        // NOTE: This task instance must be created and invoked on the
        // UI thread and it can be executed only once!
        if (mInitVuforiaTask != null)
        {
            String logMessage = "Cannot initialize SDK twice";
            vuforiaException = new SampleApplicationException(
                SampleApplicationException.VUFORIA_ALREADY_INITIALIZATED,
                logMessage);
            Log.e(LOGTAG, logMessage);
        }

        // Init Vuforia Engine if no exception was thrown
        if (vuforiaException == null)
        {
            try {
                mInitVuforiaTask = new InitVuforiaTask(this);
                mInitVuforiaTask.execute();
            }
            catch (Exception e)
            {
                String logMessage = "Initializing Vuforia Engine failed";
                vuforiaException = new SampleApplicationException(
                    SampleApplicationException.INITIALIZATION_FAILURE,
                    logMessage);
                Log.e(LOGTAG, logMessage);
            }
        }

        // If an exception was thrown, send it to the application
        // and call onInitARDone to stop the initialization process
        if (vuforiaException != null)
        {
            mSessionControlRef.get().onInitARDone(vuforiaException);
        }
    }


    // Sets the fusion provider type for DeviceTracker optimization
    // This setting only affects the Tracker if the DeviceTracker is used.
    // By default, the provider type is set to FUSION_OPTIMIZE_MODEL_TARGETS_AND_SMART_TERRAIN
    public boolean setFusionProviderType(int providerType)
    {
        int provider =  Vuforia.getActiveFusionProvider();

        if ((provider & ~providerType) != 0)
        {
            if (Vuforia.setAllowedFusionProviders(providerType) == FUSION_PROVIDER_TYPE.FUSION_PROVIDER_INVALID_OPERATION)
            {
                Log.e(LOGTAG,"Failed to set fusion provider type: " + providerType);
                return false;
            }
        }

        Log.d(LOGTAG, "Successfully set fusion provider type: " + providerType);

        return true;
    }


    // Starts Vuforia Engine asynchronously
    public void startAR()
    {
        SampleApplicationException vuforiaException = null;

        try
        {
            StartVuforiaTask mStartVuforiaTask = new StartVuforiaTask(this);
            mStartVuforiaTask.execute();
        }
        catch (Exception e)
        {
            String logMessage = "Starting Vuforia Engine failed";
            vuforiaException = new SampleApplicationException(
                    SampleApplicationException.CAMERA_INITIALIZATION_FAILURE,
                    logMessage);
            Log.e(LOGTAG, logMessage);
        }

        // If an exception was thrown, send it to the application
        // and call onInitARDone to stop the initialization process
        if (vuforiaException != null)
        {
            mSessionControlRef.get().onInitARDone(vuforiaException);
        }
    }

    
    // Stops any ongoing initialization,
    // deinitializes Vuforia Engine, the camera, and trackers
    public void stopAR() throws SampleApplicationException
    {
        // Cancel potentially running tasks
        if (mInitVuforiaTask != null
            && mInitVuforiaTask.getStatus() != InitVuforiaTask.Status.FINISHED)
        {
            mInitVuforiaTask.cancel(true);
            mInitVuforiaTask = null;
        }
        
        if (mLoadTrackerTask != null
            && mLoadTrackerTask.getStatus() != LoadTrackerTask.Status.FINISHED)
        {
            mLoadTrackerTask.cancel(true);
            mLoadTrackerTask = null;
        }
        
        mInitVuforiaTask = null;
        mLoadTrackerTask = null;
        
        mStarted = false;
        
        stopCamera();
        
        // Ensure that all asynchronous operations to initialize Vuforia Engine
        // and loading the tracker datasets do not overlap:
        synchronized (mLifecycleLock)
        {
            
            boolean unloadTrackersResult;
            boolean deinitTrackersResult;
            
            // Destroy the tracking data set:
            unloadTrackersResult = mSessionControlRef.get().doUnloadTrackersData();
            
            // Deinitialize the trackers:
            deinitTrackersResult = mSessionControlRef.get().doDeinitTrackers();
            
            // Deinitialize Vuforia Engine:
            Vuforia.deinit();
            
            if (!unloadTrackersResult)
                throw new SampleApplicationException(
                    SampleApplicationException.UNLOADING_TRACKERS_FAILURE,
                    "Failed to unload trackers\' data");
            
            if (!deinitTrackersResult)
                throw new SampleApplicationException(
                    SampleApplicationException.TRACKERS_DEINITIALIZATION_FAILURE,
                    "Failed to deinitialize trackers");
            
        }
    }
    

    // Resumes Vuforia Engine, restarts the trackers and the camera
    private void resumeAR()
    {
        SampleApplicationException vuforiaException = null;

        try {
            mResumeVuforiaTask = new ResumeVuforiaTask(this);
            mResumeVuforiaTask.execute();
        }
        catch (Exception e)
        {
            String logMessage = "Resuming Vuforia failed";
            vuforiaException = new SampleApplicationException(
                    SampleApplicationException.INITIALIZATION_FAILURE,
                    logMessage);
            Log.e(LOGTAG, logMessage);
        }

        // If an exception was thrown, send it to the application
        // and call onInitARDone to stop the initialization process
        if (vuforiaException != null)
        {
            mSessionControlRef.get().onInitARDone(vuforiaException);
        }
    }


    // Initializes, configures, and starts the camera and trackers
    private void startCameraAndTrackers() throws SampleApplicationException
    {
        String error;
        if(mCameraRunning)
        {
            error = "Camera already running, unable to open again";
            Log.e(LOGTAG, error);
            throw new SampleApplicationException(
                    SampleApplicationException.CAMERA_INITIALIZATION_FAILURE, error);
        }

        if (!CameraDevice.getInstance().init())
        {
            error = "Unable to open camera device";
            Log.e(LOGTAG, error);
            throw new SampleApplicationException(
                    SampleApplicationException.CAMERA_INITIALIZATION_FAILURE, error);
        }

        if (!CameraDevice.getInstance().selectVideoMode(mVideoMode))
        {
            error = "Unable to set video mode";
            Log.e(LOGTAG, error);
            throw new SampleApplicationException(
                    SampleApplicationException.CAMERA_INITIALIZATION_FAILURE, error);
        }

        if (!CameraDevice.getInstance().start())
        {
            error = "Unable to start camera device";
            Log.e(LOGTAG, error);
            throw new SampleApplicationException(
                    SampleApplicationException.CAMERA_INITIALIZATION_FAILURE, error);
        }

        mSessionControlRef.get().doStartTrackers();

        mCameraRunning = true;
    }


    private void stopCamera()
    {
        if (mCameraRunning)
        {
            mSessionControlRef.get().doStopTrackers();
            mCameraRunning = false;
            CameraDevice.getInstance().stop();
            CameraDevice.getInstance().deinit();
        }
    }
    
    
    // Callback called every cycle
    @Override
    public void Vuforia_onUpdate(State s)
    {
        mSessionControlRef.get().onVuforiaUpdate(s);
    }
    
    
    // Called whenever the device orientation or screen resolution changes
    public void onConfigurationChanged()
    {
        if (mStarted)
        {
            Device.getInstance().setConfigurationChanged();
        }
    }
    

    public void onResume()
    {
        if (mResumeVuforiaTask == null
                || mResumeVuforiaTask.getStatus() == ResumeVuforiaTask.Status.FINISHED)
        {
            // onResume() will sometimes be called twice depending on the screen lock mode
            // This will prevent redundant AsyncTasks from being executed
            resumeAR();
        }
    }
    
    
    public void onPause()
    {
        if (mStarted)
        {
            stopCamera();
        }

        Vuforia.onPause();
    }
    
    
    void onSurfaceChanged(int width, int height)
    {
        Vuforia.onSurfaceChanged(width, height);
    }
    
    
    void onSurfaceCreated()
    {
        Vuforia.onSurfaceCreated();
    }


    // An async task to configure and initialize Vuforia Engine asynchronously.
    private static class InitVuforiaTask extends AsyncTask<Void, Integer, Boolean>
    {
        // Initialize with invalid value:
        private int mProgressValue = -1;

        private final WeakReference<SampleApplicationSession> appSessionRef;

        InitVuforiaTask(SampleApplicationSession session)
        {
            appSessionRef = new WeakReference<>(session);
        }
        
        protected Boolean doInBackground(Void... params)
        {
            SampleApplicationSession session = appSessionRef.get();

            // Prevent the onDestroy() method to overlap with initialization:
            synchronized (session.mLifecycleLock)
            {
                // Configure Vuforia Engine
                // Note: license key goes in the third parameter
                Vuforia.setInitParameters(session.mActivityRef.get(), session.mVuforiaFlags, "");
                
                do
                {
                    // Vuforia.init() blocks until an initialization step is
                    // complete, then it proceeds to the next step and reports
                    // progress in percents (0 ... 100%).
                    // If Vuforia.init() returns -1, it indicates an error.
                    // Initialization is done when progress has reached 100%.
                    mProgressValue = Vuforia.init();
                    
                    // Publish the progress value:
                    publishProgress(mProgressValue);
                    
                    // We check whether the task has been canceled in the
                    // meantime (by calling AsyncTask.cancel(true)).
                    // and bail out if it has, thus stopping this thread.
                    // This is necessary as the AsyncTask will run to completion
                    // regardless of the status of the component that
                    // started is.
                } while (!isCancelled() && mProgressValue >= 0
                    && mProgressValue < 100);

                return (mProgressValue > 0);
            }
        }
        
        
        protected void onProgressUpdate(Integer... values)
        {
            // Do something with the progress value "values[0]", e.g. update
            // splash screen, progress bar, etc.
        }
        
        
        protected void onPostExecute(Boolean result)
        {

            Log.d(LOGTAG, "InitVuforiaTask.onPostExecute: execution "
                    + (result ? "successful" : "failed"));
            
            SampleApplicationException vuforiaException = null;
            SampleApplicationSession session = appSessionRef.get();

            // Done initializing Vuforia Engine, next we will try initializing the tracker
            if (result)
            {
                try
                {
                    InitTrackerTask mInitTrackerTask = new InitTrackerTask(session);
                    mInitTrackerTask.execute();
                }
                catch (Exception e)
                {
                    String logMessage = "Failed to initialize tracker.";
                    vuforiaException = new SampleApplicationException(
                            SampleApplicationException.TRACKERS_INITIALIZATION_FAILURE,
                            logMessage);
                    Log.e(LOGTAG, logMessage);
                }
            }
            else
            {
                String logMessage;
                
                // NOTE: Check if initialization failed because the device is
                // not supported. At this point the user should be informed
                // with a message.
                logMessage = appSessionRef.get().getInitializationErrorString(mProgressValue);
                Log.e(LOGTAG, "InitVuforiaTask.onPostExecute: " + logMessage + " Exiting.");

                vuforiaException = new SampleApplicationException(
                    SampleApplicationException.INITIALIZATION_FAILURE,
                    logMessage);
            }

            // If an exception was thrown, send it to the application
            // and call onInitARDone to stop the initialization process
            if (vuforiaException != null)
            {
                session.mSessionControlRef.get().onInitARDone(vuforiaException);
            }
        }
    }

    // An async task to resume Vuforia Engine asynchronously
    private static class ResumeVuforiaTask extends AsyncTask<Void, Void, Void>
    {
        private final WeakReference<SampleApplicationSession> appSessionRef;

        ResumeVuforiaTask(SampleApplicationSession session)
        {
            appSessionRef = new WeakReference<>(session);
        }

        protected Void doInBackground(Void... params)
        {
            // Prevent the concurrent lifecycle operations:
            synchronized (appSessionRef.get().mLifecycleLock)
            {
                Vuforia.onResume();
            }

            return null;
        }

        protected void onPostExecute(Void result)
        {
            Log.d(LOGTAG, "ResumeVuforiaTask.onPostExecute");

            SampleApplicationSession session = appSessionRef.get();

            // We may start the camera only if the Vuforia Engine  has already been
            // initialized and the camera has not already been started
            if (session.mStarted)
            {
                if (!session.mCameraRunning)
                {
                    session.startAR();
                }
                else
                {
                    session.mSessionControlRef.get().onVuforiaStarted();
                }

                session.mSessionControlRef.get().onVuforiaResumed();
            }
        }
    }

    // An async task to initialize trackers asynchronously
    private static class InitTrackerTask extends AsyncTask<Void, Integer, Boolean>
    {
        private final WeakReference<SampleApplicationSession> appSessionRef;

        InitTrackerTask(SampleApplicationSession session)
        {
            appSessionRef = new WeakReference<>(session);
        }

        protected  Boolean doInBackground(Void... params)
        {
            synchronized (appSessionRef.get().mLifecycleLock)
            {
                // Load the tracker data set:
                return appSessionRef.get().mSessionControlRef.get().doInitTrackers();
            }
        }

        protected void onPostExecute(Boolean result)
        {
            SampleApplicationException vuforiaException = null;
            SampleApplicationSession session = appSessionRef.get();

            Log.d(LOGTAG, "InitTrackerTask.onPostExecute: execution "
                + (result ? "successful" : "failed"));


            // Done initializing the tracker, next we will try loading the tracker
            if (result)
            {
                try
                {
                    session.mLoadTrackerTask = new LoadTrackerTask(session);
                    session.mLoadTrackerTask.execute();
                }
                catch (Exception e)
                {
                    String logMessage = "Failed to load tracker data.";
                    Log.e(LOGTAG, logMessage);

                    vuforiaException = new SampleApplicationException(
                            SampleApplicationException.LOADING_TRACKERS_FAILURE,
                            logMessage);
                }
            }
            else
            {
                String logMessage = "Failed to initialize trackers.";
                Log.e(LOGTAG, logMessage);

                // Error initializing trackers
                vuforiaException = new SampleApplicationException(
                        SampleApplicationException.TRACKERS_INITIALIZATION_FAILURE,
                        logMessage);
            }

            // If an exception was thrown, send it to the application
            // and call onInitARDone to stop the initialization process
            if (vuforiaException != null)
            {
                session.mSessionControlRef.get().onInitARDone(vuforiaException);
            }
        }
    }
    
    // An async task to load the tracker data asynchronously.
    private static class LoadTrackerTask extends AsyncTask<Void, Void, Boolean>
    {
        private final WeakReference<SampleApplicationSession> appSessionRef;

        LoadTrackerTask(SampleApplicationSession session)
        {
            appSessionRef = new WeakReference<>(session);
        }

        protected Boolean doInBackground(Void... params)
        {
            // Prevent the concurrent lifecycle operations:
            synchronized (appSessionRef.get().mLifecycleLock)
            {
                // Load the tracker data set:
                return appSessionRef.get().mSessionControlRef.get().doLoadTrackersData();
            }
        }
        
        protected void onPostExecute(Boolean result)
        {
            SampleApplicationException vuforiaException = null;
            SampleApplicationSession session = appSessionRef.get();
            
            Log.d(LOGTAG, "LoadTrackerTask.onPostExecute: execution "
                + (result ? "successful" : "failed"));
            
            if (!result)
            {
                String logMessage = "Failed to load tracker data.";
                Log.e(LOGTAG, logMessage);

                vuforiaException = new SampleApplicationException(
                    SampleApplicationException.LOADING_TRACKERS_FAILURE,
                    logMessage);
            }
            else
            {
                // Hint to the virtual machine that it would be a good time to
                // run the garbage collector:
                //
                // NOTE: This is only a hint. There is no guarantee that the
                // garbage collector will actually be run.
                System.gc();
                
                Vuforia.registerCallback(session);

                session.mStarted = true;
            }
            
            // Done loading the tracker. Update the application status
            // and pass the exception to check errors
            session.mSessionControlRef.get().onInitARDone(vuforiaException);
        }
    }

    // An async task to start the camera and trackers
    private static class StartVuforiaTask extends AsyncTask<Void, Void, Boolean>
    {
        SampleApplicationException vuforiaException = null;

        private final WeakReference<SampleApplicationSession> appSessionRef;

        StartVuforiaTask(SampleApplicationSession session)
        {
            appSessionRef = new WeakReference<>(session);
        }

        protected Boolean doInBackground(Void... params)
        {
            SampleApplicationSession session = appSessionRef.get();

            // Prevent the concurrent lifecycle operations:
            synchronized (session.mLifecycleLock)
            {
                try {
                    session.startCameraAndTrackers();
                }
                catch (SampleApplicationException e)
                {
                    Log.e(LOGTAG, "StartVuforiaTask.doInBackground: Could not start AR with exception: " + e);
                    vuforiaException = e;
                }
            }

            return true;
        }

        protected void onPostExecute(Boolean result)
        {
            Log.d(LOGTAG, "StartVuforiaTask.onPostExecute: execution "
                + (result ? "successful" : "failed"));

            SampleApplicationControl sessionControl = appSessionRef.get().mSessionControlRef.get();

            sessionControl.onVuforiaStarted();

            // If an exception was thrown, send it to the application
            // and call onInitARDone to stop the initialization process
            if (vuforiaException != null)
            {
                sessionControl.onInitARDone(vuforiaException);
            }
        }
    }
    

    private String getInitializationErrorString(int code)
    {
        if (code == INIT_ERRORCODE.INIT_DEVICE_NOT_SUPPORTED)
            return mActivityRef.get().getString(R.string.INIT_ERROR_DEVICE_NOT_SUPPORTED);
        if (code == INIT_ERRORCODE.INIT_NO_CAMERA_ACCESS)
            return mActivityRef.get().getString(R.string.INIT_ERROR_NO_CAMERA_ACCESS);
        if (code == INIT_ERRORCODE.INIT_LICENSE_ERROR_MISSING_KEY)
            return mActivityRef.get().getString(R.string.INIT_LICENSE_ERROR_MISSING_KEY);
        if (code == INIT_ERRORCODE.INIT_LICENSE_ERROR_INVALID_KEY)
            return mActivityRef.get().getString(R.string.INIT_LICENSE_ERROR_INVALID_KEY);
        if (code == INIT_ERRORCODE.INIT_LICENSE_ERROR_NO_NETWORK_TRANSIENT)
            return mActivityRef.get().getString(R.string.INIT_LICENSE_ERROR_NO_NETWORK_TRANSIENT);
        if (code == INIT_ERRORCODE.INIT_LICENSE_ERROR_NO_NETWORK_PERMANENT)
            return mActivityRef.get().getString(R.string.INIT_LICENSE_ERROR_NO_NETWORK_PERMANENT);
        if (code == INIT_ERRORCODE.INIT_LICENSE_ERROR_CANCELED_KEY)
            return mActivityRef.get().getString(R.string.INIT_LICENSE_ERROR_CANCELED_KEY);
        if (code == INIT_ERRORCODE.INIT_LICENSE_ERROR_PRODUCT_TYPE_MISMATCH)
            return mActivityRef.get().getString(R.string.INIT_LICENSE_ERROR_PRODUCT_TYPE_MISMATCH);
        else
        {
            return mActivityRef.get().getString(R.string.INIT_LICENSE_ERROR_UNKNOWN_ERROR);
        }
    }


    public int getVideoMode()
    {
        return mVideoMode;
    }


    public boolean resetDeviceTracker()
    {
        TrackerManager trackerManager = TrackerManager.getInstance();
        PositionalDeviceTracker deviceTracker = (PositionalDeviceTracker) trackerManager
                .getTracker(PositionalDeviceTracker.getClassType());

        boolean result = false;

        if (deviceTracker != null)
        {
            result = deviceTracker.reset();
        }

        if (result)
        {
            Log.i(LOGTAG, "Successfully reset DeviceTracker");
        }
        else
        {
            Log.e(LOGTAG, "Could not reset DeviceTracker");
        }

        return result;
    }
}
