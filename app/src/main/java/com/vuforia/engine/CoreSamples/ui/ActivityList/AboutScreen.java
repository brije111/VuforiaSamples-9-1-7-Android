/*===============================================================================
Copyright (c) 2020 PTC Inc. All Rights Reserved.

Copyright (c) 2012-2014 Qualcomm Connected Experiences, Inc. All Rights Reserved.

Vuforia is a trademark of PTC Inc., registered in the United States and other 
countries.
===============================================================================*/

package com.vuforia.engine.CoreSamples.ui.ActivityList;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.TextView;

import com.vuforia.Vuforia;
import com.vuforia.engine.CoreSamples.R;


/**
 * This class is used to generate the About screen view for each activity
 */
public class AboutScreen extends Activity implements OnClickListener
{
    private static final String LOGTAG = "AboutScreen";

    private String mClassToLaunch;
    private String mClassToLaunchPackage;
    private WebView mAboutWebText;
    
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN);
        
        setContentView(R.layout.about_screen);
        
        Bundle extras = getIntent().getExtras();

        String webText = "";

        if (extras != null)
        {
            webText = extras.getString("ABOUT_TEXT");
            mClassToLaunchPackage = getPackageName();
            mClassToLaunch = mClassToLaunchPackage + "."
                    + extras.getString("ACTIVITY_TO_LAUNCH");

            TextView mAboutTextTitle = findViewById(R.id.about_text_title);
            mAboutTextTitle.setText(extras.getString("ABOUT_TEXT_TITLE"));
        }

        mAboutWebText = findViewById(R.id.about_html_text);
        
        AboutWebViewClient aboutWebClient = new AboutWebViewClient();
        mAboutWebText.setWebViewClient(aboutWebClient);

        StringBuilder aboutText = new StringBuilder();

        if (webText != null)
        {
            try
            {
                InputStream is = getAssets().open(webText);
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(is));

                String line;
                while ((line = reader.readLine()) != null)
                {
                    aboutText.append(line);
                }
            } catch (IOException e)
            {
                Log.e(LOGTAG, "About html loading failed");
            }

            String about = aboutText.toString().replace("Vuforia version",
                    "Vuforia Engine " + Vuforia.getLibraryVersion());
            about = about.replace("Android version",
                    "Android API level " + Build.VERSION.SDK_INT);
            mAboutWebText.loadData(about, "text/html", "UTF-8");
        }

        Button mStartButton = findViewById(R.id.button_start);
        mStartButton.setOnClickListener(this);
    }


    @Override
    protected void onDestroy()
    {
        super.onDestroy();

        mAboutWebText.destroy();
        mAboutWebText = null;
    }


    private void startARActivity()
    {
        Intent i = new Intent();
        i.setClassName(mClassToLaunchPackage, mClassToLaunch);
        startActivity(i);
    }
    
    
    @Override
    public void onClick(View v)
    {
        if (v.getId() == R.id.button_start)
        {
                startARActivity();
        }
    }


    private class AboutWebViewClient extends WebViewClient
    {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url)
        {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
            return true;
        }
    }
}
