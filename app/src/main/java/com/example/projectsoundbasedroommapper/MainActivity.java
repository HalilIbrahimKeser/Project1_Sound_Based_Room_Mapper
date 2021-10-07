package com.example.projectsoundbasedroommapper;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import android.os.Bundle;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (savedInstanceState == null) {
            SoundBasedMapperFragment soundBasedMapperFragment = SoundBasedMapperFragment.newInstance();
            replaceFragmentWith(soundBasedMapperFragment, false);
        }
    }

    public void replaceFragmentWith (Fragment newFragment, boolean addToBackStack){
        FragmentManager fragmentManager = getSupportFragmentManager();
        if (addToBackStack)
            fragmentManager
                    .beginTransaction()
                    .setReorderingAllowed(true)
                    .addToBackStack(null)
                    .add(R.id.fragment_container, newFragment)
                    .commit();
        else
            fragmentManager
                    .beginTransaction()
                    .setReorderingAllowed(true)
                    .add(R.id.fragment_container, newFragment)
                    .commit();
    }
}