/*===============================================================================
Copyright (c) 2016,2018 PTC Inc. All Rights Reserved.

Copyright (c) 2014 Qualcomm Connected Experiences, Inc. All Rights Reserved.

Vuforia is a trademark of PTC Inc., registered in the United States and other 
countries.
===============================================================================*/

package com.vuforia.engine.SampleApplication.utils;


/**
 * These shaders are used to render the video background on screen
 */
public class VideoBackgroundShader
{
    
    public static final String VB_VERTEX_SHADER =          
        "attribute vec4 vertexPosition;\n" +
        "attribute vec2 vertexTexCoord;\n" +
        "uniform mat4 projectionMatrix;\n" +
    
        "varying vec2 texCoord;\n" +
       
        "void main()\n" +
        "{\n" +
        "    gl_Position = projectionMatrix * vertexPosition;\n" +
        "    texCoord = vertexTexCoord;\n" +
        "}\n";
    
    public static final String VB_FRAGMENT_SHADER =
        "precision mediump float;\n" +
        "varying vec2 texCoord;\n" +
        "uniform sampler2D texSampler2D;\n" +
        "void main ()\n" +
        "{\n" +
        "    gl_FragColor = texture2D(texSampler2D, texCoord);\n" +
        "}\n";

}
