/*===============================================================================
Copyright (c) 2016,2018 PTC Inc. All Rights Reserved.

Copyright (c) 2012-2014 Qualcomm Connected Experiences, Inc. All Rights Reserved.

Vuforia is a trademark of PTC Inc., registered in the United States and other 
countries.
===============================================================================*/

package com.vuforia.engine.CoreSamples.app.UserDefinedTargets;

import android.util.Log;

import com.vuforia.ImageTargetBuilder;
import com.vuforia.ObjectTracker;
import com.vuforia.Renderer;
import com.vuforia.TrackableSource;
import com.vuforia.TrackerManager;
import com.vuforia.Vec2F;
import com.vuforia.VideoBackgroundConfig;
import com.vuforia.engine.SampleApplication.utils.SampleUtils;


public class RefFreeFrame
{
    
    private static final String LOGTAG = "RefFreeFrame";
    
    // Some helper functions
    enum STATUS
    {
        STATUS_IDLE, STATUS_SCANNING, STATUS_CREATING, STATUS_SUCCESS
    }
    
    private STATUS curStatus;
    
    // / Current color of the target finder. This changes color
    // / depending on frame quality.
    private final float[] colorFrame;
    
    // / Half of the screen size, used often in the rendering pipeline
    private final Vec2F halfScreenSize;
    
    // / Keep track of the time between frames for color transitions
    private long lastFrameTime;
    
    // All rendering methods are contained in this class for easy
    // extraction/abstraction
    private final RefFreeFrameGL frameGL;
    
    // The latest trackable source to be extracted from the Target Builder
    private TrackableSource trackableSource;

    private final UserDefinedTargets mActivity;
    
    
    // Function used to transition in the range [0, 1]
    private float transition(float v0, float inc)
    {
        float minValue = 0.0f;
        float maxValue = 1.0f;

        float vOut = v0 + inc;
        return (vOut < minValue ? minValue : (vOut > maxValue ? maxValue : vOut));
    }
    
    
    RefFreeFrame(UserDefinedTargets activity)
    {
        mActivity = activity;
        colorFrame = new float[4];
        curStatus = STATUS.STATUS_IDLE;
        trackableSource = null;
        colorFrame[0] = 1.0f;
        colorFrame[1] = 0.0f;
        colorFrame[2] = 0.0f;
        colorFrame[3] = 0.75f;
        
        frameGL = new RefFreeFrameGL(mActivity);
        halfScreenSize = new Vec2F();
    }
    
    
    void init()
    {
        // load the frame texture
        frameGL.getTextures();
        
        trackableSource = null;
    }
    
    
    void deInit()
    {
        TrackerManager trackerManager = TrackerManager.getInstance();
        ObjectTracker objectTracker = (ObjectTracker) (trackerManager
            .getTracker(ObjectTracker.getClassType()));
        if (objectTracker != null)
        {
            ImageTargetBuilder targetBuilder = objectTracker
                .getImageTargetBuilder();
            if (targetBuilder != null
                && (targetBuilder.getFrameQuality() != ImageTargetBuilder.FRAME_QUALITY.FRAME_QUALITY_NONE))
            {
                targetBuilder.stopScan();
            }
        }
    }
    
    
    void initGL(int screenWidth, int screenHeight)
    {
        if (!frameGL.init(screenWidth, screenHeight))
        {
            Log.e(LOGTAG, "Failed to init RefFreeFrameGL");
            return;
        }
        
        Renderer renderer = Renderer.getInstance();
        VideoBackgroundConfig vc = renderer.getVideoBackgroundConfig();
        int temp[] = vc.getSize().getData();
        float[] videoBackgroundConfigSize = new float[2];
        videoBackgroundConfigSize[0] = temp[0] * 0.5f;
        videoBackgroundConfigSize[1] = temp[1] * 0.5f;
        
        halfScreenSize.setData(videoBackgroundConfigSize);
        
        // sets last frame timer
        lastFrameTime = System.currentTimeMillis();
        
        reset();
    }
    
    
    void reset()
    {
        curStatus = STATUS.STATUS_IDLE;
        
    }
    
    
    void setCreating()
    {
        curStatus = STATUS.STATUS_CREATING;
    }


    private void updateUIState(ImageTargetBuilder targetBuilder, int frameQuality)
    {
        // ** Elapsed time
        long elapsedTimeMS = System.currentTimeMillis() - lastFrameTime;
        lastFrameTime += elapsedTimeMS;
        
        // This is a time-dependent value used for transitions in 
        // the range [0,1] over the period of half of a second.
        float transitionHalfSecond = elapsedTimeMS * 0.002f;
        
        STATUS newStatus = curStatus;
        
        switch (curStatus)
        {
            case STATUS_IDLE:
                if (frameQuality != ImageTargetBuilder.FRAME_QUALITY.FRAME_QUALITY_NONE)
                    newStatus = STATUS.STATUS_SCANNING;
                
                break;
            
            case STATUS_SCANNING:
                switch (frameQuality)
                {
                // bad target quality, render the frame white until a match is
                // made, then go to green
                    case ImageTargetBuilder.FRAME_QUALITY.FRAME_QUALITY_LOW:
                        colorFrame[0] = 1.0f;
                        colorFrame[1] = 1.0f;
                        colorFrame[2] = 1.0f;
                        
                        break;
                    
                    // good target, switch to green over half a second
                    case ImageTargetBuilder.FRAME_QUALITY.FRAME_QUALITY_HIGH:
                    case ImageTargetBuilder.FRAME_QUALITY.FRAME_QUALITY_MEDIUM:
                        colorFrame[0] = transition(colorFrame[0],
                            -transitionHalfSecond);
                        colorFrame[1] = transition(colorFrame[1],
                            transitionHalfSecond);
                        colorFrame[2] = transition(colorFrame[2],
                            -transitionHalfSecond);
                        
                        break;
                }
                break;
            
            case STATUS_CREATING:
            {
                // check for new result
                // if found, set to success, success time and:
                TrackableSource newTrackableSource = targetBuilder
                    .getTrackableSource();
                if (newTrackableSource != null)
                {
                    newStatus = STATUS.STATUS_SUCCESS;
                    trackableSource = newTrackableSource;
                    
                    mActivity.targetCreated();
                }
            }
            default:
                break;
        }
        
        curStatus = newStatus;
    }
    
    
    void render()
    {
        // Get the image tracker
        TrackerManager trackerManager = TrackerManager.getInstance();
        ObjectTracker objectTracker = (ObjectTracker) (trackerManager
            .getTracker(ObjectTracker.getClassType()));
        
        // Get the frame quality from the target builder
        ImageTargetBuilder targetBuilder = objectTracker.getImageTargetBuilder();
        int frameQuality = targetBuilder.getFrameQuality();
        
        // Update the UI internal state variables
        updateUIState(targetBuilder, frameQuality);
        
        if (curStatus == STATUS.STATUS_SUCCESS)
        {
            curStatus = STATUS.STATUS_IDLE;
            
            Log.d(LOGTAG, "Built target, reactivating dataset with new target");
            mActivity.doStartTrackers();
        }
        
        // Renders the hints
        switch (curStatus)
        {
            case STATUS_SCANNING:
                renderScanningViewfinder();
                break;
            default:
                break;
        
        }
        
        SampleUtils.checkGLError("RefFreeFrame render");
    }
    
    
    private void renderScanningViewfinder()
    {
        frameGL.setModelViewScale();
        frameGL.setColor(colorFrame);
        frameGL.renderViewfinder();
    }
    
    
    boolean hasNewTrackableSource()
    {
        return (trackableSource != null);
    }
    
    
    TrackableSource getNewTrackableSource()
    {
        TrackableSource result = trackableSource;
        trackableSource = null;
        return result;
    }
}
