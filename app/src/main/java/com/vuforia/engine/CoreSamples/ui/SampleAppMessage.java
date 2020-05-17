/*===============================================================================
Copyright (c) 2020 PTC Inc. All Rights Reserved.

Vuforia is a trademark of PTC Inc., registered in the United States and other
countries.
===============================================================================*/

package com.vuforia.engine.CoreSamples.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.vuforia.engine.CoreSamples.R;


/**
 * This class configures and creates a custom message that can be used throughout the sample
 *
 * To change the length of time the toast appears on screen, modify FADE_IN_OUT_DURATION (ms)
 * For additional configuration, modify the AnimationListener
 */
public class SampleAppMessage
{
    private final View mView;
    private final TextView mTextMessageView;
    private final Animation mFadeIn;
    private final Animation mFadeOut;


    public SampleAppMessage(Context context, ViewGroup parentView, View placementReferenceView, boolean placeAboveView)
    {
        int FADE_IN_OUT_DURATION = 2000;

        mView = View.inflate(context, R.layout.sample_app_message_view, null);
        mTextMessageView = mView.findViewById(R.id.message_text_view);

        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);

        params.addRule(placeAboveView ? RelativeLayout.ABOVE : RelativeLayout.BELOW, placementReferenceView.getId());
        params.addRule(RelativeLayout.CENTER_HORIZONTAL, RelativeLayout.TRUE);

        mView.setLayoutParams(params);
        mView.setVisibility(View.GONE);

        parentView.addView(mView);

        mFadeIn = new AlphaAnimation(0, 1);
        mFadeIn.setInterpolator(new DecelerateInterpolator());
        mFadeIn.setDuration(FADE_IN_OUT_DURATION);
        mFadeIn.setAnimationListener(null);

        mFadeOut = new AlphaAnimation(1, 0);
        mFadeOut.setInterpolator(new DecelerateInterpolator());
        mFadeOut.setDuration(FADE_IN_OUT_DURATION);
        mFadeOut.setAnimationListener(new Animation.AnimationListener()
        {
            @Override
            public void onAnimationStart(Animation arg0)
            {
            }
            @Override
            public void onAnimationRepeat(Animation arg0)
            {
            }
            @Override
            public void onAnimationEnd(Animation arg0)
            {
                mView.setVisibility(View.GONE);
            }
        });
    }


    public void hide()
    {
        if(mView.getVisibility() == View.VISIBLE)
        {
            if (mFadeIn.hasStarted())
            {
                mFadeIn.cancel();
                mView.animate().cancel();
            }

            mView.clearAnimation();
            mView.startAnimation(mFadeOut);
        }
    }

    public void show(String message)
    {
        if (mFadeOut.hasStarted())
        {
            mFadeOut.cancel();
            mView.animate().cancel();
        }

        mView.clearAnimation();
        mView.startAnimation(mFadeIn);
        mTextMessageView.setText(message);
        mView.setVisibility(View.VISIBLE);
    }


    public String getMessage()
    {
        return mTextMessageView.getText().toString();
    }


    public boolean isHidden()
    {
        return mView.getVisibility() != View.VISIBLE;
    }
}
