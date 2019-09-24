package app.artyomd.injector.example;

import android.os.Bundle;
import android.widget.FrameLayout;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import app.artyomd.injector.example.lib.BlankFragment;

public class MainActivity extends FragmentActivity {

	private static final int CONTENT_VIEW_ID = 10101010;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		FrameLayout frameLayout = findViewById(R.id.container);
		frameLayout.setId(CONTENT_VIEW_ID);
		Fragment newFragment = new BlankFragment();
		getSupportFragmentManager()
				.beginTransaction()
				.add(CONTENT_VIEW_ID, newFragment)
				.commit();
	}
}
