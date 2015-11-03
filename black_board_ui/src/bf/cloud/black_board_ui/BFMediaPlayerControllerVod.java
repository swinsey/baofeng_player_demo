package bf.cloud.black_board_ui;

import bf.cloud.android.playutils.VodPlayer;
import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
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
		attachPlayer(mVodPlayer);
	}

	public BFMediaPlayerControllerVod(Context context, AttributeSet attrs) {
		super(context, attrs);
		initViews();
		attachPlayer(mVodPlayer);
	}

	public BFMediaPlayerControllerVod(Context context, AttributeSet attrs,
			int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		initViews();
		attachPlayer(mVodPlayer);
	}

	@Override
	public void onError(int errorCode) {
		super.onError(errorCode);
		//���ദ����������
	}

	@Override
	public void onEvent(int eventCode) {
		super.onEvent(eventCode);
		//���ദ������¼�
	}

	@Override
	protected void initViews() {
		removeAllViews();
		FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
				LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
		layoutParams.gravity = Gravity.CENTER;
		// ��Ƶ�������²�
		mVodPlayer = (VodPlayer) mLayoutInflater.inflate(
				R.layout.vp_video_vod_player, this, false);
		mVodPlayer.setBackgroundColor(Color.BLACK);
		addView(mVodPlayer, layoutParams);
		// ���Ž�����
		mPlayCompleteFrame = (RelativeLayout) mLayoutInflater.inflate(
				R.layout.vp_play_complete, this, false);
		initPlayCompleteFrame();
		addView(mPlayCompleteFrame, layoutParams);
		super.initViews();
	}

	private void initPlayCompleteFrame() {
		btPlayCompleteFrameStart = mPlayCompleteFrame
				.findViewById(R.id.play_button);
		btPlayCompleteFrameStart.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				if (mVodPlayer != null) {
					// ���ϣ��������ʲô�����¶�������Ƶ�������������־
//					mVodPlayer.setForceStartFlag(true);
					mVodPlayer.stop();
					mVodPlayer.start();
				}
			}
		});
		tvPlayCompleteFrameMessage = (TextView) mPlayCompleteFrame
				.findViewById(R.id.message_textview);
		tvPlayCompleteFrameMessage.setText("");
		mPlayCompleteFrame.setVisibility(View.INVISIBLE);
	}
	
	public VodPlayer getVodPlayer(){
		return mVodPlayer;
	}
}
