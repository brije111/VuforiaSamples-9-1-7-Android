/*===============================================================================
Copyright (c) 2020 PTC, Inc. All Rights Reserved.

Vuforia is a trademark of PTC Inc., registered in the United States and other
countries.
===============================================================================*/

package com.vuforia.engine.SampleApplication.utils;

import android.content.res.AssetManager;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.util.Log;

import com.vuforia.Vec4F;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * This class is used to load 3D models from a .v3d file
 * Note this file type is internal to Vuforia Engine
 *
 * We first read the magic number and parse the file to extract the number of vertices,
 * faces, materials, normals, and colors
 */
public class SampleApplicationV3DModel extends MeshObject
{

    private static final String LOGTAG = "V3DModel";

    private ByteBuffer _modelVertices;
    private ByteBuffer _modelTexCoords;
    private ByteBuffer _modelNormals;
    private ByteBuffer _modelMaterialIndices;
    private ByteBuffer _modelGroupAmbientColors;
    private ByteBuffer _modelGroupDiffuseColors;
    private ByteBuffer _modelGroupSpecularColors;
    private ByteBuffer _modelGroupDiffuseIndexes;
    private ByteBuffer _modelGroupDissolveFactor;
    private ByteBuffer _modelGroupVertexRanges;
    private int _nbVertices = -1;
    private int _nbGroups = -1;
    private int _nbFaces = -1;
    private int _nbMaterials = -1;
    private float mVersion = -1;
    private boolean mIsLoaded = false;

    private float _transparencyValue = 1.0f;
    private final float[] _lightColor = {0.5f, 0.5f, 0.5f, 1.0f};

    private static final int GEOMETRY_ARRAY = 0;
    private static final int NORMALS_ARRAY = 1;
    private static final int OBJ_MTL_EXTRA_ARRAY = 2;
    private static final int OBJ_AMBIENT_ARRAY = 3;
    private static final int OBJ_DIFFUSE_ARRAY = 4;

    private final int SHADERS_BUFFER_NUM = 5;
    private final int[] shaderBuffers = new int[SHADERS_BUFFER_NUM];

    private int objMtlProgramID;
    private int objMtlVertexHandle;
    private int objMtlNormalHandle;
    private int objMtlMvpMatrixHandle;
    private int objMtlMvMatrixHandle;
    private int objMtlNormalMatrixHandle;
    private int objMtlLightPosHandle;
    private int objMtlLightColorHandle;
    private int objTransparencyHandle;
    private int objMtlColorCorrectionHandle;
    private int objMtlIntensityCorrectionHandle;

    private int objMtlExtra;
    private int objMtlGroupAmbientColorsHandle;
    private int objMtlGroupDiffuseColorsHandle;
    private int objMtlGroupSpecularColorsHandle;
    private int objMtlGroupTransparencyHandle;

    // No color correction by default
    private Vec4F mColorCorrection = new Vec4F(1.0f, 1.0f, 1.0f, 1.0f);
    private float mIntensityCorrection = 1.0f;

    private final boolean mUseTransparency;

    public SampleApplicationV3DModel(boolean enableTransparency)
    {
        mUseTransparency = enableTransparency;
    }


    @SuppressWarnings("ResultOfMethodCallIgnored")
    public boolean loadModel(AssetManager assetManager, String filename)
    {
        if (mIsLoaded)
        {
            unloadModel();
        }

        try
        {
            InputStream inputStream = assetManager.open(filename);
            BufferedInputStream bufInputStream = new BufferedInputStream(inputStream);

            int numBytes = bufInputStream.available();

            if (numBytes < 0 || numBytes >= Integer.MAX_VALUE)
            {
                Log.e(LOGTAG, "Could not load model, number of bytes "
                        + numBytes + " exceeds limit");
                return false;
            }
            
            byte[] bytesArray = new byte[numBytes];

            bufInputStream.read(bytesArray);
            ByteBuffer byteBuffer = ByteBuffer.wrap(bytesArray);

            // Read magic number
            int magicNumber = byteBuffer.getInt();
            Log.d(LOGTAG, "MagicNumber: " + magicNumber);

            // Read version number
            mVersion = byteBuffer.getFloat();
            Log.d(LOGTAG, "NumVersion: " + mVersion);

            // Read vertices number
            _nbVertices = byteBuffer.getInt();
            Log.d(LOGTAG, "_nbVertices: " + _nbVertices);

            // Read faces number
            _nbFaces = byteBuffer.getInt();
            Log.d(LOGTAG, "_nbFaces: " + _nbFaces);

            // Read material number
            _nbMaterials = byteBuffer.getInt();
            Log.d(LOGTAG, "_nbMaterials: " + _nbMaterials);
            _nbGroups = _nbMaterials;

            // Read vertices
            int numFloatsToRead = _nbFaces * 3 * 3; // 3 vertices per face, 3 values per vertex x, y, z
            _modelVertices = ByteBuffer.allocateDirect(numFloatsToRead * (Float.SIZE / Byte.SIZE));
            _modelVertices.order(ByteOrder.nativeOrder());

            for (int i = 0; i < numFloatsToRead; ++i)
            {
                _modelVertices.putFloat(byteBuffer.getFloat());
            }

            _modelVertices.rewind();
            Log.d(LOGTAG, "First vertex: " + _modelVertices.getFloat(0) + "," + _modelVertices.getFloat(1) + "," + _modelVertices.getFloat(2));

            // Read normals
            numFloatsToRead = _nbFaces * 3 * 3; // 3 vertices per face, 3 values per vertex x, y, z
            _modelNormals = ByteBuffer.allocateDirect(numFloatsToRead * (Float.SIZE / Byte.SIZE));
            _modelNormals.order(ByteOrder.nativeOrder());

            for (int i = 0; i < numFloatsToRead; ++i)
            {
                _modelNormals.putFloat(byteBuffer.getFloat());
            }

            _modelNormals.rewind();
            Log.d(LOGTAG, "First normal: " + _modelNormals.getFloat(0) + "," + _modelNormals.getFloat(1) + "," + _modelNormals.getFloat(2));

            // Read texture coordinates
            numFloatsToRead = _nbFaces * 3 * 2; // 3 vertices per face, 2 values per vertex u, v
            _modelTexCoords = ByteBuffer.allocateDirect(numFloatsToRead * (Float.SIZE / Byte.SIZE));
            _modelTexCoords.order(ByteOrder.nativeOrder());

            for (int i = 0; i < numFloatsToRead; ++i)
            {
                _modelTexCoords.putFloat(byteBuffer.getFloat());
            }

            _modelTexCoords.rewind();
            Log.d(LOGTAG, "First tex coord: " + _modelTexCoords.getFloat(0) + "," + _modelTexCoords.getFloat(1));

            // Read material per face and shininess
            numFloatsToRead = _nbFaces * 3 * 2; // 3 vertices per face, 2 values per vertex material, shininess
            _modelMaterialIndices = ByteBuffer.allocateDirect(numFloatsToRead * (Float.SIZE / Byte.SIZE));
            _modelMaterialIndices.order(ByteOrder.nativeOrder());

            for (int i = 0; i < numFloatsToRead; ++i)
            {
                _modelMaterialIndices.putFloat(byteBuffer.getFloat());
            }

            _modelMaterialIndices.rewind();
            Log.d(LOGTAG, "First material and shininess: " + _modelMaterialIndices.getFloat(0) + "," + _modelMaterialIndices.getFloat(1));

            // Read material ambient color
            numFloatsToRead = _nbMaterials * 4; // 4 values per material r, g, b, a
            _modelGroupAmbientColors = ByteBuffer.allocateDirect(numFloatsToRead * (Float.SIZE / Byte.SIZE));
            _modelGroupAmbientColors.order(ByteOrder.nativeOrder());

            for (int i = 0; i < numFloatsToRead; ++i)
            {
                _modelGroupAmbientColors.putFloat(byteBuffer.getFloat());
            }

            _modelGroupAmbientColors.rewind();
            Log.d(LOGTAG, "First ambient color: " + _modelGroupAmbientColors.getFloat(0) + "," + _modelGroupAmbientColors.getFloat(1) + "," + _modelGroupAmbientColors.getFloat(2) + "," + _modelGroupAmbientColors.getFloat(3));

            // Read material diffuse color
            numFloatsToRead = _nbMaterials * 4; // 4 values per material r, g, b, a
            _modelGroupDiffuseColors = ByteBuffer.allocateDirect(numFloatsToRead * (Float.SIZE / Byte.SIZE));
            _modelGroupDiffuseColors.order(ByteOrder.nativeOrder());

            for (int i = 0; i < numFloatsToRead; ++i)
            {
                _modelGroupDiffuseColors.putFloat(byteBuffer.getFloat());
            }

            _modelGroupDiffuseColors.rewind();
            Log.d(LOGTAG, "First diffuse color: " + _modelGroupDiffuseColors.getFloat(0) + "," + _modelGroupDiffuseColors.getFloat(1) + "," + _modelGroupDiffuseColors.getFloat(2) + "," + _modelGroupDiffuseColors.getFloat(3));

            // Read material specular color
            numFloatsToRead = _nbMaterials * 4; // 4 values per material r, g, b, a
            _modelGroupSpecularColors = ByteBuffer.allocateDirect(numFloatsToRead * (Float.SIZE / Byte.SIZE));
            _modelGroupSpecularColors.order(ByteOrder.nativeOrder());

            for (int i = 0; i < numFloatsToRead; ++i)
            {
                _modelGroupSpecularColors.putFloat(byteBuffer.getFloat());
            }

            _modelGroupSpecularColors.rewind();
            Log.d(LOGTAG, "First specular color: " + _modelGroupSpecularColors.getFloat(0) + "," + _modelGroupSpecularColors.getFloat(1) + "," + _modelGroupSpecularColors.getFloat(2) + "," + _modelGroupSpecularColors.getFloat(3));

            // Read material diffuse texture indexes
            numFloatsToRead = _nbMaterials; // 1 index per material
            _modelGroupDiffuseIndexes = ByteBuffer.allocateDirect(numFloatsToRead * (Integer.SIZE / Byte.SIZE));
            _modelGroupDiffuseIndexes.order(ByteOrder.nativeOrder());

            for (int i = 0; i < numFloatsToRead; ++i)
            {
                _modelGroupDiffuseIndexes.putInt(byteBuffer.getInt());
            }

            _modelGroupDiffuseIndexes.rewind();
            Log.d(LOGTAG, "First material diffuse texture index: " + _modelGroupDiffuseIndexes.getInt(0));

            // Read material dissolve value (transparency)
            numFloatsToRead = _nbMaterials; // 1 value per material
            _modelGroupDissolveFactor = ByteBuffer.allocateDirect(numFloatsToRead * (Float.SIZE / Byte.SIZE));
            _modelGroupDissolveFactor.order(ByteOrder.nativeOrder());

            for (int i = 0; i < numFloatsToRead; ++i)
            {
                _modelGroupDissolveFactor.putFloat(byteBuffer.getFloat());
            }

            _modelGroupDissolveFactor.rewind();
            Log.d(LOGTAG, "First material dissolve: " + _modelGroupDissolveFactor.getFloat(0));

            // Read vertex range per group
            numFloatsToRead = _nbMaterials * 2; // 1 index per material
            _modelGroupVertexRanges = ByteBuffer.allocateDirect(numFloatsToRead * (Integer.SIZE / Byte.SIZE));
            _modelGroupVertexRanges.order(ByteOrder.nativeOrder());

            for (int i = 0; i < numFloatsToRead; ++i)
            {
                _modelGroupVertexRanges.putInt(byteBuffer.getInt());
            }
            
            _modelGroupVertexRanges.rewind();
            Log.d(LOGTAG, "First material diffuse texture index: " + _modelGroupVertexRanges.getInt(0) + "," + _modelGroupVertexRanges.getInt(1));

            // Read magic number again
            int magicNumberEnd = byteBuffer.getInt();
            Log.d(LOGTAG, "MagicNumber: " + magicNumber);

            if (magicNumberEnd == magicNumber)
            {
               // Set default light color to gray and alpha to 1.0f
                float[] color = {0.5f, 0.5f, 0.5f, 1.0f};
                setLightColor(color);
                setTransparency(0.5f);

                initShaders();

                mIsLoaded = true;
            }
            else
            {
                unloadModel();
                Log.d(LOGTAG, "Could not initialize shader");
            }

            bufInputStream.close();
            inputStream.close();

            return true;
        }
        catch (IOException e)
        {
            e.printStackTrace();
            Log.e(LOGTAG, "Could not load model " + filename);

            return false;
        }
    }

    public void unloadModel()
    {
        if (!mIsLoaded)
        {
            return;
        }

        mIsLoaded = false;
        mVersion = -1;
        _nbVertices = -1;
        _nbFaces = -1;
        _nbMaterials = -1;
        _modelVertices = null;
        _modelNormals = null;
        _modelTexCoords = null;
        _modelMaterialIndices = null;
        _modelGroupAmbientColors = null;
        _modelGroupDiffuseColors = null;
        _modelGroupSpecularColors = null;
        _modelGroupDiffuseIndexes = null;
        _modelGroupDissolveFactor = null;
        _modelGroupVertexRanges = null;

        GLES20.glDeleteBuffers(SHADERS_BUFFER_NUM, shaderBuffers, 0);
    }

    public boolean isLoaded()
    {
        return mIsLoaded;
    }

    public void initShaders()
    {
        Log.d(LOGTAG, "initShaders");

        GLES20.glGenBuffers(SHADERS_BUFFER_NUM, shaderBuffers, 0);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, shaderBuffers[GEOMETRY_ARRAY]);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, _nbVertices * 3 * (Float.SIZE / Byte.SIZE), _modelVertices, GLES20.GL_STATIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, shaderBuffers[NORMALS_ARRAY]);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, _nbVertices * 3 * (Float.SIZE / Byte.SIZE), _modelNormals, GLES20.GL_STATIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, shaderBuffers[OBJ_MTL_EXTRA_ARRAY]);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, _nbVertices * 2 * (Float.SIZE / Byte.SIZE), _modelMaterialIndices, GLES20.GL_STATIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, shaderBuffers[OBJ_AMBIENT_ARRAY]);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, _nbGroups * (Float.SIZE / Byte.SIZE), _modelGroupAmbientColors, GLES20.GL_STATIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, shaderBuffers[OBJ_DIFFUSE_ARRAY]);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, _nbGroups * (Float.SIZE / Byte.SIZE), _modelGroupDiffuseColors, GLES20.GL_STATIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);


        // objmtl program
        if (mUseTransparency)
        {
            objMtlProgramID = SampleUtils.createProgramFromShaderSrc(
                DiffuseLightMaterials.VERTEX_SHADER,
                DiffuseLightMaterials.FRAGMENT_SHADER);

            SampleUtils.checkGLError("v3d GLInitRendering #0");

            objMtlVertexHandle = GLES20.glGetAttribLocation(objMtlProgramID, "a_vertexPosition");
            objMtlNormalHandle = GLES20.glGetAttribLocation(objMtlProgramID, "a_vertexNormal");
        }
        else
        {
            objMtlProgramID = SampleUtils.createProgramFromShaderSrc(
                    LightingShaders.LIGHTING_VERTEX_SHADER,
                    LightingShaders.LIGHTING_FRAGMENT_SHADER);

            SampleUtils.checkGLError("v3d GLInitRendering #0");

            objMtlVertexHandle = GLES20.glGetAttribLocation(objMtlProgramID, "a_position");
            objMtlNormalHandle = GLES20.glGetAttribLocation(objMtlProgramID, "a_normal");

        }

        objMtlExtra = GLES20.glGetAttribLocation(objMtlProgramID, "a_vertexExtra");

        Log.d(LOGTAG, ">GL> objMtlVertexHandle= " + objMtlVertexHandle);
        Log.d(LOGTAG, ">GL> objMtlExtra= " + objMtlExtra);

        objMtlMvpMatrixHandle = GLES20.glGetUniformLocation(objMtlProgramID,
                "u_mvpMatrix");
        objMtlMvMatrixHandle = GLES20.glGetUniformLocation(objMtlProgramID, "u_mvMatrix");
        objMtlNormalMatrixHandle = GLES20.glGetUniformLocation(objMtlProgramID,
                "u_normalMatrix");

        objMtlLightPosHandle = GLES20.glGetUniformLocation(objMtlProgramID, "u_lightPos");
        objMtlLightColorHandle = GLES20.glGetUniformLocation(objMtlProgramID,
                "u_lightColor");
        objMtlColorCorrectionHandle = GLES20.glGetUniformLocation(objMtlProgramID, "u_colorCorrection");
        objMtlIntensityCorrectionHandle = GLES20.glGetUniformLocation(objMtlProgramID, "u_intensityCorrection");
        objTransparencyHandle = GLES20.glGetUniformLocation(objMtlProgramID, "u_transparency");

        objMtlGroupAmbientColorsHandle = GLES20.glGetUniformLocation(objMtlProgramID,
                "u_groupAmbientColors");
        objMtlGroupDiffuseColorsHandle = GLES20.glGetUniformLocation(objMtlProgramID,
                "u_groupDiffuseColors");
        objMtlGroupSpecularColorsHandle = GLES20.glGetUniformLocation(objMtlProgramID,
                "u_groupSpecularColors");
        objMtlGroupTransparencyHandle = GLES20.glGetUniformLocation(objMtlProgramID,
                "u_groupTransparency");

        SampleUtils.checkGLError("v3d GLInitRendering #1");

        int[] total = new int[1];
        GLES20.glGetProgramiv(objMtlProgramID, GLES20.GL_ACTIVE_UNIFORMS, total, 0);

        Log.d(LOGTAG, "@@ nb uniforms: " + total[0]);
        for (int i = 0; i < total[0]; ++i)
        {
            int[] uniformType = new int[1];
            int[] uniformSize = new int[1];
            String name = GLES20.glGetActiveUniform(objMtlProgramID, i, uniformSize, 0, uniformType, 0);
            int location = GLES20.glGetUniformLocation(objMtlProgramID, name);
            Log.d(LOGTAG, "@@ uniform(" + name + "), location= " + location);
        }

        Log.d(LOGTAG, "end of initShaders");
    }

    public void setRenderingColorCorrection(Vec4F colorCorrection, float intensityCorrection)
    {
        mColorCorrection = new Vec4F(colorCorrection);
        mIntensityCorrection = intensityCorrection;
    }

    public void render(float[] modelViewMatrix, float[] modelViewProjMatrix)
    {
        GLES20.glUseProgram(objMtlProgramID);

        if (mIsLoaded)
        {
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, shaderBuffers[GEOMETRY_ARRAY]);
            GLES20.glVertexAttribPointer(objMtlVertexHandle, 3, GLES20.GL_FLOAT, false, 0,
                    0);
            GLES20.glEnableVertexAttribArray(objMtlVertexHandle);

            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, shaderBuffers[NORMALS_ARRAY]);
            GLES20.glVertexAttribPointer(objMtlNormalHandle, 3, GLES20.GL_FLOAT, false, 0,
                    0);
            GLES20.glEnableVertexAttribArray(objMtlNormalHandle);

            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, shaderBuffers[OBJ_MTL_EXTRA_ARRAY]);
            GLES20.glVertexAttribPointer(objMtlExtra, 2, GLES20.GL_FLOAT, false, 0,
                    0);
            GLES20.glEnableVertexAttribArray(objMtlExtra);

            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);


            if (objMtlMvpMatrixHandle >= 0)
            {
                GLES20.glUniformMatrix4fv(objMtlMvpMatrixHandle, 1, false,
                        modelViewProjMatrix, 0);
            }

            GLES20.glUniformMatrix4fv(objMtlMvMatrixHandle, 1, false,
                    modelViewMatrix, 0);

            // compute normal matrix
            float[] inverseMatrix = new float[16];
            Matrix.invertM(inverseMatrix, 0, modelViewMatrix, 0);

            float[] normalMatrix = new float[16];
            Matrix.transposeM(normalMatrix, 0, inverseMatrix, 0);

            GLES20.glUniformMatrix4fv(objMtlNormalMatrixHandle, 1, false,
                    normalMatrix, 0);

            GLES20.glUniform4fv(objMtlGroupAmbientColorsHandle, _nbGroups,
                    _modelGroupAmbientColors.asFloatBuffer());
            GLES20.glUniform4fv(objMtlGroupDiffuseColorsHandle, _nbGroups,
                    _modelGroupDiffuseColors.asFloatBuffer());

            GLES20.glUniform4fv(objMtlGroupSpecularColorsHandle, _nbGroups,
                    _modelGroupSpecularColors.asFloatBuffer());
            GLES20.glUniform1fv(objMtlGroupTransparencyHandle, _nbGroups,
                    _modelGroupDissolveFactor.asFloatBuffer());

            GLES20.glUniform4f(objMtlLightPosHandle, 0.2f, -1.0f, 0.5f, -1.0f);
            GLES20.glUniform4f(objMtlLightColorHandle, _lightColor[0], _lightColor[1], _lightColor[2], _lightColor[3]);
            GLES20.glUniform1f(objTransparencyHandle, _transparencyValue);
            GLES20.glUniform4f(objMtlColorCorrectionHandle,
                    mColorCorrection.getData()[0],
                    mColorCorrection.getData()[1],
                    mColorCorrection.getData()[2],
                    mColorCorrection.getData()[3]);
            GLES20.glUniform1f(objMtlIntensityCorrectionHandle, mIntensityCorrection);

            boolean enableBlending = false;
            if (_transparencyValue < 1.0f)
            {
                enableBlending = true;
            }

            if (enableBlending)
            {
                GLES20.glEnable(GLES20.GL_BLEND);
                GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
            }

            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, _nbVertices);

            if (enableBlending)
            {
                GLES20.glDisable(GLES20.GL_BLEND);
            }

            GLES20.glDisableVertexAttribArray(objMtlVertexHandle);
            GLES20.glDisableVertexAttribArray(objMtlNormalHandle);
            GLES20.glDisableVertexAttribArray(objMtlExtra);
        }
        else
        {
            Log.d(LOGTAG, "Not Rendering V3d");
        }

        SampleUtils.checkGLError("v3d renderFrame");
    }

    private void setLightColor(float[] lightColor)
    {
        if (lightColor.length != 4)
        {
            Log.e(LOGTAG, "SetLightColor expects 4 values in the array");
            return;
        }

        System.arraycopy(lightColor, 0, _lightColor, 0, 4);
    }

    public void setTransparency(float transparency)
    {
        // Alpha value has to be between 0 and 1
        _transparencyValue = Math.max(Math.min(transparency, 1.0f), 0);
    }

    @Override
    public Buffer getBuffer(BUFFER_TYPE bufferType)
    {
        Buffer result = null;
        switch (bufferType)
        {
            case BUFFER_TYPE_VERTEX:
                result = _modelVertices;
                break;
            case BUFFER_TYPE_TEXTURE_COORD:
                result = _modelTexCoords;
                break;
            case BUFFER_TYPE_NORMALS:
                result = _modelNormals;
            default:
                break;
        }
        return result;
    }

    @Override
    public int getNumObjectVertex()
    {
        return _nbVertices;
    }

    @Override
    public int getNumObjectIndex()
    {
        return 0;
    }

}
