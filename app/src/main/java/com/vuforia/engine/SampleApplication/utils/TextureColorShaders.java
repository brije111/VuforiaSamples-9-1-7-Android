/*===============================================================================
Copyright (c) 2020 PTC Inc. All Rights Reserved.

Copyright (c) 2012-2014 Qualcomm Connected Experiences, Inc. All Rights Reserved.

Vuforia is a trademark of PTC Inc., registered in the United States and other
countries.
===============================================================================*/

package com.vuforia.engine.SampleApplication.utils;

/**
 * This shader is used to combine color and the texture
 */
public class TextureColorShaders
{

    public static final String TEXTURE_COLOR_VERTEX_SHADER = " \n" + "\n"
            + "attribute vec4 vertexPosition; \n"
            + "attribute vec2 vertexTexCoord; \n" + "\n"
            + "varying vec2 texCoord; \n" + "\n"
            + "uniform mat4 modelViewProjectionMatrix; \n" + "\n"
            + "void main() \n" + "{ \n"
            + "   gl_Position = modelViewProjectionMatrix * vertexPosition; \n"
            + "   texCoord = vertexTexCoord; \n"
            + "} \n";

    public static final String TEXTURE_COLOR_FRAGMENT_SHADER = " \n" + "\n"
            + "precision mediump float; \n" + " \n"
            + "varying vec2 texCoord; \n"
            + "uniform vec4 uniformColor; \n"
            + "uniform sampler2D texSampler2D; \n" + " \n"
            + "void main() \n" + "{ \n"
            + "   vec4 texColor = texture2D(texSampler2D, texCoord); \n"
            + "   gl_FragColor = texColor * uniformColor; \n"
            + "} \n";

}
