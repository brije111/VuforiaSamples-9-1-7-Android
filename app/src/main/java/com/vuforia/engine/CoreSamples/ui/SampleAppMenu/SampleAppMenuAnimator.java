/*===============================================================================
Copyright (c) 2020 PTC Inc. All Rights Reserved.

Copyright (c) 2012-2014 Qualcomm Connected Experiences, Inc. All Rights Reserved.

Vuforia is a trademark of PTC Inc., registered in the United States and other 
countries.
===============================================================================*/

package com.vuforia.engine.CoreSamples.ui.SampleAppMenu;

import android.animation.Animator;
import android.animation.ValueAnimator;


/**
 * This class handles the menu animation lifecycle and configuration
 */
class SampleAppMenuAnimator extends ValueAnimator implements
    ValueAnimator.AnimatorUpdateListener, ValueAnimator.AnimatorListener
{
    
    private static final long MENU_ANIMATION_DURATION = 300;
    private final SampleAppMenu mSampleAppMenu;
    private float mMaxX;
    private float mEndX;
    
    SampleAppMenuAnimator(SampleAppMenu menu)
    {
        mSampleAppMenu = menu;
        setDuration(MENU_ANIMATION_DURATION);
        addUpdateListener(this);
        addListener(this);
    }
    
    
    @Override
    public void onAnimationUpdate(ValueAnimator animation)
    {
        Float f = (Float) animation.getAnimatedValue();
        mSampleAppMenu.setAnimationX(f);
    }
    
    
    @Override
    public void onAnimationCancel(Animator animation)
    {
    }
    
    
    @Override
    public void onAnimationEnd(Animator animation)
    {
        mSampleAppMenu.setDockMenu(mEndX == mMaxX);
        if (mEndX == 0)
            mSampleAppMenu.hide();
    }
    
    
    @Override
    public void onAnimationRepeat(Animator animation)
    {
    }
    
    
    @Override
    public void onAnimationStart(Animator animation)
    {
    }
    
    
    public void setStartEndX(float start, float end)
    {
        mEndX = end;
        setFloatValues(start, end);
        setDuration((int) (MENU_ANIMATION_DURATION * (Math.abs(end - start) / mMaxX)));
    }
    
    
    public void setMaxX(float maxX)
    {
        mMaxX = maxX;
    }
    
}
