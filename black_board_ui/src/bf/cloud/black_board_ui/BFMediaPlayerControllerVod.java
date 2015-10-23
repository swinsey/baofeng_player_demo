package bf.cloud.black_board_ui;

import bf.cloud.android.playutils.BasePlayer;
import bf.cloud.android.playutils.VodPlayer;
import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

public class BFMediaPlayerControllerVod extends BFMediaPlayerControllerBase {
	private VodPlayer mVodPlayer = null;
	private RelativeLayout mPlayCompleteFrame = null;
	// ���Ž�����İ�ť
	private View btPlayCompleteFrameStart = null;
	//
	private TextView tvPlayCompleteFrameMessage = null;

	public BFMediaPlayerControllerVod(Context context) {
		super(context);
		initViews();
	}

	public BFMediaPlayerControllerVod(Context context, AttributeSet attrs) {
		super(context, attrs);
		initViews();
	}

	public BFMediaPlayerControllerVod(Context context, AttributeSet attrs,
			int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		initViews();
	}

	@Override
	public void attachPlayer(BasePlayer bp) {
		super.attachPlayer(bp);
		mVodPlayer = (VodPlayer) bp;

	}

	@Override
	public void onError(int errorCode) {
		// TODO Auto-generated method stub
	}

	@Override
	public void onEvent(int eventCode) {
		// TODO Auto-generated method stub
	}

	@Override
	protected void initViews() {
		super.initViews();
		FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
				LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
		layoutParams.gravity = Gravity.CENTER;
		// ���Ž�����
		mPlayCompleteFrame = (RelativeLayout) mLayoutInflater.inflate(
				R.layout.vp_play_complete, this, false);
		initPlayCompleteFrame();
		addView(mPlayCompleteFrame, layoutParams);
	}

	private void initPlayCompleteFrame() {
		btPlayCompleteFrameStart = mPlayCompleteFrame
				.findViewById(R.id.play_button);
		btPlayCompleteFrameStart.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				if (mVodPlayer != null) {
					// ���ϣ��������ʲô�����¶�������Ƶ�������������־
					mVodPlayer.setForceStartFlag(true);
					mVodPlayer.stop();
					mVodPlayer.start();
				}
			}
		});
		tvPlayCompleteFrameMessage = (TextView) mPlayCompleteFrame
				.findViewById(R.id.message_textview);
		tvPlayCompleteFrameMessage.setText("");
	}
}