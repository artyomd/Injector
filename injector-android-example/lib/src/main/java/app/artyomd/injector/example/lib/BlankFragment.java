package app.artyomd.injector.example.lib;

import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.airbnb.lottie.LottieAnimationView;
import com.airbnb.lottie.LottieComposition;
import com.airbnb.lottie.LottieCompositionFactory;
import com.airbnb.lottie.LottieListener;

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
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		final LottieAnimationView lottieAnimationView = view.findViewById(R.id.lottie_view);
		LottieCompositionFactory.fromAsset(getActivity(), "tick.json").addListener(new LottieListener<LottieComposition>() {
			@Override
			public void onResult(LottieComposition result) {
				lottieAnimationView.setComposition(result);
				lottieAnimationView.playAnimation();
			}
		});
	}
}
