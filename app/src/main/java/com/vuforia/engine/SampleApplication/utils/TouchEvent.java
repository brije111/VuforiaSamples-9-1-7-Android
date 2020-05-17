/*===============================================================================
Copyright (c) 2020 PTC Inc. All Rights Reserved.

Vuforia is a trademark of PTC Inc., registered in the United States and other
countries.
===============================================================================*/

package com.vuforia.engine.SampleApplication.utils;


/**
 * Support class for detecting touch input
 * Can be used to detect single and double touch gestures
 */
public class TouchEvent
{
    private boolean isActive = false;
    private int actionType = SampleGestureListener.ACTION_INVALID;
    private int pointerId = -1;
    private float x, y;
    private float lastX, lastY;
    private float startX, startY;
    private float tapX, tapY;
    private long startTime;
    private long dt;
    private float dist2;
    private boolean didTap = false;

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public int getActionType() {
        return actionType;
    }

    public void setActionType(int actionType) {
        this.actionType = actionType;
    }

    public int getPointerId() {
        return pointerId;
    }

    public void setPointerId(int pointerId) {
        this.pointerId = pointerId;
    }

    public float getX() {
        return x;
    }

    public void setX(float x) {
        this.x = x;
    }

    public float getY() {
        return y;
    }

    public void setY(float y) {
        this.y = y;
    }

    public float getLastX() {
        return lastX;
    }

    public void setLastX(float lastX) {
        this.lastX = lastX;
    }

    public float getLastY() {
        return lastY;
    }

    public void setLastY(float lastY) {
        this.lastY = lastY;
    }

    public float getStartX() {
        return startX;
    }

    public void setStartX(float startX) {
        this.startX = startX;
    }

    public float getStartY() {
        return startY;
    }

    public void setStartY(float startY) {
        this.startY = startY;
    }

    public float getTapX() {
        return tapX;
    }

    public void setTapX(float tapX) {
        this.tapX = tapX;
    }

    public float getTapY() {
        return tapY;
    }

    public void setTapY(float tapY) {
        this.tapY = tapY;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getDt() {
        return dt;
    }

    public void setDt(long dt) {
        this.dt = dt;
    }

    public float getDist2() {
        return dist2;
    }

    public void setDist2(float dist2) {
        this.dist2 = dist2;
    }

    public boolean isDidTap() {
        return didTap;
    }

    public void setDidTap(boolean didTap) {
        this.didTap = didTap;
    }
}
