/*===============================================================================
Copyright (c) 2020 PTC Inc. All Rights Reserved.

Copyright (c) 2012-2015 Qualcomm Connected Experiences, Inc. All Rights Reserved.

Vuforia is a trademark of PTC Inc., registered in the United States and other 
countries.
===============================================================================*/


package com.vuforia.engine.CoreSamples.ui.ActivityList;

import android.app.ListActivity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.vuforia.engine.CoreSamples.R;


/**
 * This class will display the Vuforia Engine features list and start the selected activity
  */

public class ActivityLauncher extends ListActivity
{
    
    private final String[] mActivities = { "Image Targets", "Model Targets", "Ground Plane", "VuMark", "Cylinder Targets",
            "Multi Targets", "User Defined Targets", "Object Reco", "Cloud Reco", "Virtual Buttons"};
    
    
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
            R.layout.activities_list_text_view, mActivities);
        
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN);
        
        setContentView(R.layout.activities_list);
        setListAdapter(adapter);
    }
    
    
    @Override
    public void onListItemClick(ListView l, View v, int position, long id)
    {
        Intent intent = new Intent(this, AboutScreen.class);
        intent.putExtra("ABOUT_TEXT_TITLE", mActivities[position]);
        
        switch (position)
        {
            case 0:
                intent.putExtra("ACTIVITY_TO_LAUNCH",
                    "app.ImageTargets.ImageTargets");
                intent.putExtra("ABOUT_TEXT", "ImageTargets/IT_about.html");
                break;
            case 1:
                intent.putExtra("ACTIVITY_TO_LAUNCH",
                        "app.ModelTargets.ModelTargets");
                intent.putExtra("ABOUT_TEXT", "ModelTargets/MD_about.html");
                break;
            case 2:
                intent.putExtra("ACTIVITY_TO_LAUNCH",
                        "app.GroundPlane.GroundPlane");
                intent.putExtra("ABOUT_TEXT", "GroundPlane/GP_about.html");
                break;
            case 3:
                intent.putExtra("ACTIVITY_TO_LAUNCH",
                        "app.VuMark.VuMark");
                intent.putExtra("ABOUT_TEXT", "VuMark/VM_about.html");
                break;
            case 4:
                intent.putExtra("ACTIVITY_TO_LAUNCH",
                    "app.CylinderTargets.CylinderTargets");
                intent.putExtra("ABOUT_TEXT", "CylinderTargets/CY_about.html");
                break;
            case 5:
                intent.putExtra("ACTIVITY_TO_LAUNCH",
                    "app.MultiTargets.MultiTargets");
                intent.putExtra("ABOUT_TEXT", "MultiTargets/MT_about.html");
                break;
            case 6:
                intent.putExtra("ACTIVITY_TO_LAUNCH",
                    "app.UserDefinedTargets.UserDefinedTargets");
                intent.putExtra("ABOUT_TEXT",
                    "UserDefinedTargets/UD_about.html");
                break;
            case 7:
                intent.putExtra("ACTIVITY_TO_LAUNCH",
                    "app.ObjectRecognition.ObjectTargets");
                intent.putExtra("ABOUT_TEXT", "ObjectRecognition/OR_about.html");
                break;
            case 8:
                intent.putExtra("ACTIVITY_TO_LAUNCH",
                    "app.CloudRecognition.CloudReco");
                intent.putExtra("ABOUT_TEXT", "CloudReco/CR_about.html");
                break;
            case 9:
                intent.putExtra("ACTIVITY_TO_LAUNCH",
                    "app.VirtualButtons.VirtualButtons");
                intent.putExtra("ABOUT_TEXT", "VirtualButtons/VB_about.html");
                break;
            default:
                Log.e("ActivityLauncher", "Invalid activity");
                break;
        }
        
        startActivity(intent);
    }
}
