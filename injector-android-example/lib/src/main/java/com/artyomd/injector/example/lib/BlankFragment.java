package com.artyomd.injector.example.lib;

import android.app.Fragment;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.airbnb.lottie.LottieAnimationView;
import com.airbnb.lottie.LottieComposition;
import com.airbnb.lottie.OnCompositionLoadedListener;

public class BlankFragment extends Fragment {
	public BlankFragment() {
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		return inflater.inflate(R.layout.fragment_blank, container, false);
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		final LottieAnimationView lottieAnimationView = view.findViewById(R.id.lottie_view);
		LottieComposition.Factory.fromAssetFileName(getActivity(), "tick.json", new OnCompositionLoadedListener() {
			@Override
			public void onCompositionLoaded(@Nullable LottieComposition composition) {
				lottieAnimationView.setComposition(composition);
				lottieAnimationView.playAnimation();
			}
		});
	}
}
