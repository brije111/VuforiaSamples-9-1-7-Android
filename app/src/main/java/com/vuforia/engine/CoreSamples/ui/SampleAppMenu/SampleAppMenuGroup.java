/*===============================================================================
Copyright (c) 2020 PTC Inc. All Rights Reserved.

Copyright (c) 2012-2015 Qualcomm Connected Experiences, Inc. All Rights Reserved.

Vuforia is a trademark of PTC Inc., registered in the United States and other 
countries.
===============================================================================*/


package com.vuforia.engine.CoreSamples.ui.SampleAppMenu;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;

import com.vuforia.engine.CoreSamples.R;

import java.lang.ref.WeakReference;

/**
 * With this class, you can create menu options as part of a group
 * You can add text, radio buttons, and switches
 */
public class SampleAppMenuGroup
{
    private final WeakReference<Activity> mActivityRef;
    private final WeakReference<SampleAppMenuInterface> mMenuInterfaceRef;
    private final LinearLayout mLayout;
    private final LayoutParams mLayoutParams;
    private final LayoutInflater inflater;
    private final int dividerResource;

    private final float mEntriesTextSize;
    private final int mEntriesSidesPadding;
    private final int mEntriesUpDownPadding;
    private final int mEntriesUpDownRadioPadding;
    private final Typeface mFont;

    private final int selectorResource;

    private final SampleAppMenu mSampleAppMenu;
    private RadioGroup mRadioGroup;

    private final OnClickListener mClickListener;
    private final OnCheckedChangeListener mOnCheckedListener;
    private final OnCheckedChangeListener mOnRadioCheckedListener;
    
    
    @SuppressLint("InflateParams")
    public SampleAppMenuGroup(SampleAppMenuInterface menuInterface,
        Activity context, SampleAppMenu parent, boolean hasTitle, String title,
        int width)
    {
        mActivityRef = new WeakReference<>(context);
        mMenuInterfaceRef = new WeakReference<>(menuInterface);
        mSampleAppMenu = parent;
        mLayoutParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        
        inflater = LayoutInflater.from(mActivityRef.get());
        mLayout = (LinearLayout) inflater.inflate(
            R.layout.sample_app_menu_group, null, false);
        mLayout.setLayoutParams(new LinearLayout.LayoutParams(width,
            LinearLayout.LayoutParams.WRAP_CONTENT));
        
        mEntriesTextSize = mActivityRef.get().getResources().getDimension(
            R.dimen.menu_entries_text);
        
        mEntriesSidesPadding = (int) mActivityRef.get().getResources().getDimension(
            R.dimen.menu_entries_sides_padding);
        mEntriesUpDownPadding = (int) mActivityRef.get().getResources().getDimension(
            R.dimen.menu_entries_top_down_padding);
        mEntriesUpDownRadioPadding = (int) mActivityRef.get().getResources()
            .getDimension(R.dimen.menu_entries_top_down_radio_padding);
        dividerResource = R.layout.sample_app_menu_group_divider;
        
        selectorResource = android.R.drawable.list_selector_background;
        
        mFont = Typeface.create("sans-serif", Typeface.NORMAL);
        
        TextView titleView = mLayout.findViewById(R.id.menu_group_title);
        titleView.setText(title);
        titleView.setTextSize(mActivityRef.get().getResources().getDimension(
            R.dimen.menu_entries_title));
        titleView.setClickable(false);
        
        if (!hasTitle)
        {
            mLayout.removeView(titleView);
            View dividerView = mLayout
                .findViewById(R.id.menu_group_title_divider);
            mLayout.removeView(dividerView);
        }
        
        mClickListener = new OnClickListener()
        {
            
            @Override
            public void onClick(View v)
            {
                int command = Integer.parseInt(v.getTag().toString());
                mMenuInterfaceRef.get().menuProcess(command);
                mSampleAppMenu.hideMenu();
            }
        };
        
        mOnCheckedListener = new OnCheckedChangeListener()
        {
            
            @Override
            public void onCheckedChanged(CompoundButton switchView,
                boolean isChecked)
            {
                boolean result;
                int command = Integer.parseInt(switchView.getTag().toString());
                result = mMenuInterfaceRef.get().menuProcess(command);
                if (!result)
                {
                    switchView.setChecked(!isChecked);
                } else
                    mSampleAppMenu.hideMenu();
            }
        };
        
        mOnRadioCheckedListener = new OnCheckedChangeListener()
        {
            
            @Override
            public void onCheckedChanged(CompoundButton switchView,
                boolean isChecked)
            {
                if(isChecked)
                {
                    boolean result;
                    int command = Integer.parseInt(switchView.getTag().toString());
                    result = mMenuInterfaceRef.get().menuProcess(command);
                    if (result)
                    {
                        mSampleAppMenu.hideMenu();
                    }
                }
            }
        };
    }
    
    
    @SuppressWarnings({"deprecation", "UnusedReturnValue"})
    public View addTextItem(String text, int command)
    {
        Drawable selectorDrawable = mActivityRef.get().getResources().getDrawable(
            selectorResource);
        
        TextView newTextView = new TextView(mActivityRef.get());
        newTextView.setText(text);
        
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.JELLY_BEAN)
            newTextView.setBackground(selectorDrawable);
        else
            newTextView.setBackgroundDrawable(selectorDrawable);
        
        newTextView.setTypeface(mFont);
        newTextView.setTextSize(mEntriesTextSize);
        newTextView.setTag(command);
        newTextView.setVisibility(View.VISIBLE);
        newTextView.setPadding(mEntriesSidesPadding, mEntriesUpDownPadding,
            mEntriesSidesPadding, mEntriesUpDownPadding);
        newTextView.setClickable(true);
        newTextView.setOnClickListener(mClickListener);
        mLayout.addView(newTextView, mLayoutParams);
        
        View divider = inflater.inflate(dividerResource, null);
        mLayout.addView(divider, mLayoutParams);

        return  newTextView;
    }
    

    // Add a switch menu option
    @SuppressWarnings("deprecation")
    public View addSelectionItem(String text, int command, boolean on)
    {
        
        Drawable selectorDrawable = mActivityRef.get().getResources().getDrawable(
            selectorResource);
        View returnView;
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)
        {
            Switch newSwitchView = new Switch(mActivityRef.get());
            newSwitchView.setText(text);
            
            newSwitchView.setBackground(selectorDrawable);
            
            newSwitchView.setTypeface(mFont);
            newSwitchView.setTextSize(mEntriesTextSize);
            newSwitchView.setTag(command);
            newSwitchView.setVisibility(View.VISIBLE);
            newSwitchView.setPadding(mEntriesSidesPadding,
                mEntriesUpDownPadding, mEntriesSidesPadding,
                mEntriesUpDownPadding);
            newSwitchView.setChecked(on);
            newSwitchView.setOnCheckedChangeListener(mOnCheckedListener);
            mLayout.addView(newSwitchView, mLayoutParams);
            returnView = newSwitchView;
        } else
        {
            CheckBox newView = new CheckBox(mActivityRef.get());
            
            int leftPadding = newView.getPaddingLeft();
            
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.JELLY_BEAN)
                newView.setBackground(selectorDrawable);
            else
                newView.setBackgroundDrawable(selectorDrawable);
            
            newView.setText(text);
            newView.setTypeface(mFont);
            newView.setTextSize(mEntriesTextSize);
            newView.setTag(command);
            newView.setVisibility(View.VISIBLE);
            newView.setPadding(mEntriesSidesPadding + leftPadding,
                mEntriesUpDownPadding, mEntriesSidesPadding,
                mEntriesUpDownPadding);
            newView.setChecked(on);
            newView.setOnCheckedChangeListener(mOnCheckedListener);
            mLayout.addView(newView, mLayoutParams);
            returnView = newView;
        }
        
        View divider = inflater.inflate(dividerResource, null);
        mLayout.addView(divider, mLayoutParams);
        
        return returnView;
    }
    
    
    @SuppressLint("InflateParams")
    @SuppressWarnings({"deprecation", "UnusedReturnValue"})
    public View addRadioItem(String text, int command, boolean isSelected)
    {
        if (mRadioGroup == null)
        {
            mRadioGroup = new RadioGroup(mActivityRef.get());
            mRadioGroup.setVisibility(View.VISIBLE);
            mLayout.addView(mRadioGroup, mLayoutParams);
        }
        
        Drawable selectorDrawable = mActivityRef.get().getResources().getDrawable(
            selectorResource);
        
        RadioButton newRadioButton = (RadioButton) inflater.inflate(
            R.layout.sample_app_menu_group_radio_button, null, false);
        newRadioButton.setText(text);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
            newRadioButton.setBackground(selectorDrawable);
        else
            newRadioButton.setBackgroundDrawable(selectorDrawable);
        
        newRadioButton.setTypeface(mFont);
        newRadioButton.setTextSize(mEntriesTextSize);
        newRadioButton.setPadding(mEntriesSidesPadding,
            mEntriesUpDownRadioPadding, mEntriesSidesPadding,
            mEntriesUpDownRadioPadding);
        newRadioButton.setCompoundDrawablePadding(0);
        newRadioButton.setTag(command);
        newRadioButton.setVisibility(View.VISIBLE);
        mRadioGroup.addView(newRadioButton, mLayoutParams);
        
        View divider = inflater.inflate(dividerResource, null);
        mRadioGroup.addView(divider, mLayoutParams);
        
        if (isSelected)
        {
            mRadioGroup.check(newRadioButton.getId());
        }

        // Set the listener after changing the UI state to avoid calling the radio button functionality when creating the menu 
        newRadioButton.setOnCheckedChangeListener(mOnRadioCheckedListener);

        return newRadioButton;
    }
    
    
    LinearLayout getMenuLayout()
    {
        return mLayout;
    }
}
