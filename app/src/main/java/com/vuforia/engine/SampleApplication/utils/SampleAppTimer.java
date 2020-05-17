/*===============================================================================
Copyright (c) 2020 PTC Inc. All Rights Reserved.

Vuforia is a trademark of PTC Inc., registered in the United States and other
countries.
===============================================================================*/

package com.vuforia.engine.SampleApplication.utils;

import android.os.CountDownTimer;

/**
 * This class configures a timer that keeps track of its running state
 * This can be used throughout the sample
 *
 * To perform some action when the timer is finished,
 * override the onFinish() function when creating an instance of this object
 */
public class SampleAppTimer extends CountDownTimer
{
    private static final String LOGTAG = "SampleAppTimer";
    private boolean mIsRunning = false;


    protected SampleAppTimer(long timerLength, long timerInterval)
    {
        super(timerLength, timerInterval);
    }


    @Override
    public void onTick(long l)
    {
        if (!mIsRunning)
        {
            mIsRunning = true;
        }
    }


    @Override
    public void onFinish()
    {
        mIsRunning = false;
    }


    public boolean isRunning()
    {
        return mIsRunning;
    }


    public void stopTimer()
    {
        cancel();
        mIsRunning = false;
    }


    public void startTimer()
    {
        mIsRunning = true;
        start();
    }
}
