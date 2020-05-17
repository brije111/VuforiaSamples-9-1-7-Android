/*===============================================================================
Copyright (c) 2020 PTC Inc. All Rights Reserved.

Copyright (c) 2012-2014 Qualcomm Connected Experiences, Inc. All Rights Reserved.

Vuforia is a trademark of PTC Inc., registered in the United States and other 
countries.
===============================================================================*/

package com.vuforia.engine.SampleApplication;

import com.vuforia.State;


/**
 * The SampleApplicationControl interface is implemented
 * by each activity that uses SampleApplicationSession
  */
public interface SampleApplicationControl
{
    
    // To be called to initialize the trackers
    boolean doInitTrackers();
    
    
    // To be called to load the trackers' data
    boolean doLoadTrackersData();
    
    
    // To be called to start tracking with the initialized trackers and their
    // loaded data
    @SuppressWarnings("UnusedReturnValue")
    boolean doStartTrackers();
    
    
    // To be called to stop the trackers
    @SuppressWarnings("UnusedReturnValue")
    boolean doStopTrackers();
    
    
    // To be called to destroy the trackers' data
    boolean doUnloadTrackersData();
    
    
    // To be called to deinitialize the trackers
    boolean doDeinitTrackers();
    
    
    // This callback is called after the Vuforia Engine initialization is complete,
    // the trackers are initialized, their data loaded and
    // tracking is ready to start
    // If an exception is passed, it will notify the user and stop the experience
    void onInitARDone(SampleApplicationException e);
    
    
    // This callback is called every cycle
    void onVuforiaUpdate(State state);


    // This callback is called on Vuforia Engine resume
    void onVuforiaResumed();


    // This callback is called once Vuforia Engine has been started
    void onVuforiaStarted();
    
}
