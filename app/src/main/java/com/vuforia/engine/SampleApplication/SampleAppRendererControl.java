/*===============================================================================
Copyright (c) 2020 PTC Inc. All Rights Reserved.

Copyright (c) 2012-2014 Qualcomm Connected Experiences, Inc. All Rights Reserved.

Vuforia is a trademark of PTC Inc., registered in the United States and other
countries.
===============================================================================*/

package com.vuforia.engine.SampleApplication;

import com.vuforia.State;

/**
 * The SampleAppRendererControl interface is implemented
 * by each activity that uses SampleApplicationSession
 */
public interface SampleAppRendererControl
{
    // This method must be implemented by the Renderer class that handles the content rendering.
    // This function is called for each view inside of a loop
    void renderFrame(State state, float[] projectionMatrix);

    // Initializes shaders
    void initRendering();
}
