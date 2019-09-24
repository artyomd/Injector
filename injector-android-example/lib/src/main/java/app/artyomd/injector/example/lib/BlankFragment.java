package app.artyomd.injector.example.lib;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.airbnb.lottie.LottieAnimationView;
import com.airbnb.lottie.LottieCompositionFactory;

public class BlankFragment extends Fragment {

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		return inflater.inflate(R.layout.fragment_blank, container, false);
	}

	@Override
	public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		final LottieAnimationView lottieAnimationView = view.findViewById(R.id.lottie_view);
		LottieCompositionFactory.fromAsset(getActivity(), "tick.json").addListener(result -> {
			System.out.println("Loaded!!!!!!!!!!!!!!");
			lottieAnimationView.setComposition(result);
			lottieAnimationView.playAnimation();
		}).addFailureListener(result -> result.printStackTrace());
		System.out.println("openning!!!!!!!!!!!!!!");
	}
}
