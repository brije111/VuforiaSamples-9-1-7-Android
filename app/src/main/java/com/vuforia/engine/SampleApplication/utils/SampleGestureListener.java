/*===============================================================================
Copyright (c) 2020 PTC Inc. All Rights Reserved.

Vuforia is a trademark of PTC Inc., registered in the United States and other
countries.
===============================================================================*/

package com.vuforia.engine.SampleApplication.utils;

import android.view.MotionEvent;

/**
 * Listener class for detecting input gestures
 * Can be used to detect single and double touch gestures
 */
public class SampleGestureListener
{
    private final TouchEvent firstTouch;
    private final TouchEvent secondTouch;

    private int mActivePointerId = -1;
    private int mSecondActivePointerId = -1;

    private boolean mIsRotating = false;
    private float mAngle = 0;

    public static final int ACTION_INVALID = -1;

    // Used to delay translation for it not to start as soon as a touch is detected
    private static final float START_TRANSLATING_THRESHOLD = 30.0f;


    public SampleGestureListener()
    {
        firstTouch = new TouchEvent();
        secondTouch = new TouchEvent();
    }

    public int onTouchEvent(MotionEvent ev)
    {

        final int action = ev.getActionMasked();

        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                final int pointerIndex = ev.getActionIndex();
                final float x = ev.getX(pointerIndex);
                final float y = ev.getY(pointerIndex);

                // Save the ID of this pointer (for dragging)
                mActivePointerId = ev.getPointerId(pointerIndex);
                firstTouch.setStartX(x);
                firstTouch.setStartY(y);

                break;
            }

            case MotionEvent.ACTION_POINTER_DOWN:
            {
                if(ev.findPointerIndex(mActivePointerId) != -1)
                {
                    firstTouch.setStartX(ev.getX(ev.findPointerIndex(mActivePointerId)));
                    firstTouch.setStartY(ev.getY(ev.findPointerIndex(mActivePointerId)));
                }
                else
                {
                    mActivePointerId = ACTION_INVALID;
                }

                mSecondActivePointerId = ev.getPointerId(ev.getActionIndex());
                secondTouch.setStartX(ev.getX(ev.findPointerIndex(mSecondActivePointerId)));
                secondTouch.setStartY(ev.getY(ev.findPointerIndex(mSecondActivePointerId)));
            }

            case MotionEvent.ACTION_MOVE: {
                if(mActivePointerId == ACTION_INVALID)
                {
                    break;
                }

                // Find the index of the active pointer and fetch its position
                final int pointerIndex = ev.findPointerIndex(mActivePointerId);

                final float x = ev.getX(pointerIndex);
                final float y = ev.getY(pointerIndex);

                firstTouch.setX(x);
                firstTouch.setY(y);

                firstTouch.setLastX(x);
                firstTouch.setLastY(y);
                if(mSecondActivePointerId != ACTION_INVALID)
                {
                    final int secondPointerIndex = ev.findPointerIndex(mSecondActivePointerId);

                    if(secondPointerIndex != -1)
                    {
                        secondTouch.setLastX(ev.getX(secondPointerIndex));
                        secondTouch.setLastY(ev.getY(secondPointerIndex));

                        mAngle = twoLinesAngle(firstTouch.getStartX(), firstTouch.getStartY(),
                                firstTouch.getLastX(), firstTouch.getLastY(),
                                secondTouch.getStartX(), secondTouch.getStartY(),
                                secondTouch.getLastX(), secondTouch.getLastY());

                        mIsRotating = true;
                    }
                }
                break;
            }

            case MotionEvent.ACTION_UP: {
                mActivePointerId = ACTION_INVALID;
                mSecondActivePointerId = ACTION_INVALID;
                mIsRotating = false;
                break;
            }

            case MotionEvent.ACTION_CANCEL: {
                mActivePointerId = ACTION_INVALID;
                mSecondActivePointerId = ACTION_INVALID;
                mIsRotating = false;
                break;
            }

            case MotionEvent.ACTION_POINTER_UP: {
                mActivePointerId = ACTION_INVALID;
                mSecondActivePointerId = ACTION_INVALID;
                mIsRotating = false;
                break;
            }
        }

        return action;
    }

    public boolean isRotating()
    {
        return mIsRotating;
    }

    public float getRotationAngle()
    {
        return mAngle;
    }

    private float twoLinesAngle (float firstTouchX, float firstTouchY, float firstTouchLastX, float firstTouchLastY,
                                 float secondTouchX, float secondTouchY, float secondTouchLastX, float secondTouchLastY)
    {
        float firstTouchAngle = (float) Math.atan2( (firstTouchY - secondTouchY), (firstTouchX - secondTouchX) );
        float secondTouchAngle = (float) Math.atan2( (firstTouchLastY - secondTouchLastY), (firstTouchLastX - secondTouchLastX) );

        float angle = ((float)Math.toDegrees(firstTouchAngle - secondTouchAngle) * 3.0f) % 360; // 3.0f to increase 3x gesture sensibility

        if (angle < 0)
        {
            angle += 360.0f;
        }

        if (angle >= 360.0f)
        {
            angle -= 360.0f;
        }

        return angle;
    }

    public boolean isFirstTouchInsideThreshold()
    {
        float dx = firstTouch.getLastX() - firstTouch.getStartX();
        float dy = firstTouch.getLastY() - firstTouch.getStartY();
        dx = dx < 0 ? -dx : dx;
        dy = dy < 0 ? -dy : dy;

        return (dx < START_TRANSLATING_THRESHOLD && dy < START_TRANSLATING_THRESHOLD);
    }

    public TouchEvent getFirstTouch()
    {
        return firstTouch;
    }

}
