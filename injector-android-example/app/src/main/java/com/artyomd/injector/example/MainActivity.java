package com.artyomd.injector.example;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.os.Bundle;
import android.widget.FrameLayout;

import com.artyomd.injector.example.lib.BlankFragment;

public class MainActivity extends Activity {

	private static final int CONTENT_VIEW_ID = 10101010;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		FrameLayout frameLayout = findViewById(R.id.container);
		frameLayout.setId(CONTENT_VIEW_ID);
		Fragment newFragment = new BlankFragment();
		FragmentTransaction ft = getFragmentManager().beginTransaction();
		ft.add(CONTENT_VIEW_ID, newFragment).commit();
	}
}
