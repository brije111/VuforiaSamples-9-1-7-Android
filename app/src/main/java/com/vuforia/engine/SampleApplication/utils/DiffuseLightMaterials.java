/*===============================================================================
Copyright (c) 2020 PTC, Inc. All Rights Reserved.

Vuforia is a trademark of PTC Inc., registered in the United States and other
countries.
===============================================================================*/

package com.vuforia.engine.SampleApplication.utils;


/**
 * These shaders are used by the SampleApplicationV3DModel class to
 * add transparency and lighting to a model by using the normals
 */
class DiffuseLightMaterials {

    static final String VERTEX_SHADER = " \n"
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
            + "attribute vec4 a_vertexPosition; \n"
            + "attribute vec3 a_vertexNormal; \n"
            + "attribute vec2 a_vertexExtra; \n"

            // normals to pass on
            + "varying vec3 v_eyespaceNormal; \n"

            + "varying vec3 v_lightDir; \n"
            + "varying vec3 v_eyeVec; \n"

            // extra information (index to material)
            + "varying vec2 v_extra; \n"

            + "void main() { \n"

            // extra information (index to material)
            + "    v_extra = a_vertexExtra; \n"

            // normal
            + "    v_eyespaceNormal = vec3(u_normalMatrix * vec4(a_vertexNormal, 0.0)); \n"

            // the vertex position
            + "    vec4 position = u_mvpMatrix * a_vertexPosition; \n"

            // light dir
            // Add position to the light to include the device rotation
            + "    v_lightDir = ((u_mvMatrix * u_lightPos).xyz); \n"

            // Inverse position to have a vector pointing the eye
            + "    v_eyeVec = -(position.xyz);  \n"

            + "    gl_Position = position; \n"
            + "} \n";

    static final String FRAGMENT_SHADER = " \n"
            + "precision mediump float; \n"

            // material
            + "uniform vec4 u_groupAmbientColors[5]; \n"
            + "uniform vec4 u_groupDiffuseColors[5]; \n"
            + "uniform vec4 u_groupSpecularColors[5]; \n"
            + "\n"

            // lighting
            + "uniform vec4 u_lightPos; \n"
            + "uniform vec4 u_lightColor; \n"
            + "uniform float u_transparency; \n"
            + "uniform vec4 u_colorCorrection; \n"
            + "uniform float u_intensityCorrection; \n"

            // normals to pass on
            + "varying vec3 v_eyespaceNormal; \n"

            + "varying vec3 v_lightDir; \n"
            + "varying vec3 v_eyeVec; \n"

            // extra information (material index)
            + "varying vec2 v_extra; \n"

            + "void main() { \n"
            // the + 0.5 is there to avoid rounding errors when converting to an int
            + "    vec4 ambientColor = u_groupAmbientColors[int(v_extra.x + 0.5)]; \n"
            + "    vec4 diffuseColor = u_groupDiffuseColors[int(v_extra.x + 0.5)]; \n"
            + "    vec4 specularColor = u_groupSpecularColors[int(v_extra.x + 0.5)];  \n"

            + "    float shininess = v_extra.y;  \n"

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
            + "    colorTerm.a = u_transparency;  \n"
            + "    gl_FragColor = colorTerm;  \n"
            + "} ";
}
