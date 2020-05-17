/*===============================================================================
Copyright (c) 2020 PTC, Inc. All Rights Reserved.

Vuforia is a trademark of PTC Inc., registered in the United States and other
countries.
===============================================================================*/

package com.vuforia.engine.SampleApplication.utils;

/**
 * These shaders are used by the SampleApplicationV3DModel class to
 * add lighting to a model by using the normals
 */
public class LightingShaders {

    public static final String LIGHTING_VERTEX_SHADER = " \n"
            // different transformation matrices
            + "uniform mat4 u_mvpMatrix; \n"
            + "uniform mat4 u_mvMatrix; \n"
            + "uniform mat4 u_normalMatrix; \n"

            // lighting
            + "uniform vec4 u_lightPos; \n"
            + "uniform vec4 u_lightColor; \n"
            + "uniform vec4 u_colorCorrection; \n"
            + "uniform float u_intensityCorrection; \n"

            // position and normal of the vertices
            + "attribute vec4 vertexPosition; \n"
            + "attribute vec3 vertexNormal; \n"
            + "attribute vec2 vertexTexCoord; \n"

            // normals to pass on
            + "varying vec3 v_eyespaceNormal; \n"

            + "varying vec2 v_texCoord; \n"

            + "varying vec3 v_lightDir; \n"
            + "varying vec3 v_eyeVec; \n"

            + "void main() { \n"
                // normal
            + "    v_eyespaceNormal = vec3(u_normalMatrix * vec4(vertexNormal, 0.0)); \n"

                // the vertex position
            + "    vec4 position = u_mvpMatrix * vertexPosition; \n"

                // light dir
                // Add position to the light to include the device rotation
            + "    v_lightDir = ((u_mvMatrix * u_lightPos).xyz); \n"

                // Inverse position to have a vector pointing the eye
            + "    v_eyeVec = -(position.xyz);  \n"

            + "    v_texCoord = vertexTexCoord; \n"

            + "    gl_Position = position; \n"
            + "} \n";

    public static final String LIGHTING_FRAGMENT_SHADER = " \n"
            + "precision mediump float; \n"

            // lighting
            + "uniform vec4 u_lightPos; \n"
            + "uniform vec4 u_lightColor; \n"
            + "uniform vec4 u_colorCorrection; \n"
            + "uniform float u_intensityCorrection; \n"

            // normals to pass on
            + "varying vec3 v_eyespaceNormal; \n"

            + "varying vec3 v_lightDir; \n"
            + "varying vec3 v_eyeVec; \n"
            + "varying vec2 v_texCoord; \n"

            + "uniform sampler2D texSampler2D; \n"

            + "void main() { \n"
                // the + 0.5 is there to avoid rounding errors when converting to an int
            + "    vec4 ambientColor = texture2D(texSampler2D, v_texCoord); \n"
            + "    vec4 diffuseColor = ambientColor; \n"
            + "    vec4 specularColor = vec4(0.5, 0.5, 0.5, 1.0);  \n"

            + "    float shininess = 1.0;  \n"

            + "    vec3 N = normalize(v_eyespaceNormal);  \n"
            + "    vec3 E = normalize(v_eyeVec);  \n"
                // First light
            + "    vec3 L = normalize(v_lightDir);  \n"
                // Second light opposite so we can see the back with diffuse lighting
            + "    vec3 IL = -L;  \n"

                // Reflect the vector. Use this or reflect(incidentV, N);
            + "    vec3 reflectV = reflect(-L, N);  \n"

                // Get lighting terms
            + "    vec4 ambientTerm = ambientColor;  \n"
                // Add diffuse term plus inverse back lighting with attenuation pow 2
            + "    vec4 diffuseTerm = diffuseColor * max(dot(N, L), 0.0) + (diffuseColor * vec4(0.5)) * max(dot(N, IL), 0.0);  \n"
                // Add specular lighting in the model it seems it has inverted normals in some modules from the model
            + "    vec4 specularTerm = specularColor * pow(max(dot(reflectV, E), 0.0), shininess) + specularColor * pow(max(dot(-reflectV, E), 0.0), shininess);  \n"

                // + specularTerm;// Sum of three lightings
            + "    vec4 colorTerm = (ambientTerm + diffuseTerm) * u_lightColor;  \n"

                // Apply color correction
            + "    colorTerm = colorTerm * u_colorCorrection * u_intensityCorrection;  \n"

                // Transparency for alpha in group remember to activate the GL_BLEND
            + "    colorTerm.a = 1.0;  \n"
            + "    gl_FragColor = colorTerm;  \n"
            + "} ";
}
