package bf.cloud.android.components.mediaplayer;

import bf.cloud.android.components.mediaplayer.proxy.MediaplayerExo;
import bf.cloud.android.components.mediaplayer.proxy.MediaplayerExo.SizeChangedListener;
import bf.cloud.android.playutils.VideoFrame;
import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;

public class VideoViewExo extends VideoViewBase{
	
	private float mVideoAspectRatio = -1;
	private Handler mHandler = new Handler(new Handler.Callback() {
		
		@Override
		public boolean handleMessage(Message msg) {
			Log.d(TAG, "handleMessage ");
			if (mVideoFrame == null)
				mVideoFrame = (VideoFrame) getParent();
			mVideoFrame.showPlaceHolder(false);
			requestLayout();
			return false;
		}
	});

	public VideoViewExo(Context context) {
		super(context);
	}
	
	public VideoViewExo(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public VideoViewExo(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
	}

	
	@Override
	protected int openVideo() {
    	Log.d(TAG, "VideoViewExo openVideo mPath:" + mPath);
    	if (mPath == null || mPath.length() == 0 || mSurfaceTexture == null) {
            return -1;
        }
    	// we shouldn't clear the target state, because somebody might have
        // called start() previously
    	release(false);
    	//now we can create the MediaPlayerProxy
    	mMediaPlayerProxy = new MediaplayerExo(mContext);
    	mMediaPlayerProxy.setVrFlag(mIsVr);
    	mMediaPlayerProxy.setDataSource(mPath);
    	mMediaPlayerProxy.setSurfaceSize(mSurfaceWidth, mSurfaceHeight);
    	((MediaplayerExo)mMediaPlayerProxy).registSizeChangedListener(new SizeChangedListener() {
			
			@Override
			public void onSizeChanged(float ratio) {
				Log.d(TAG, "onSizeChanged ratio:" + ratio);
				mVideoAspectRatio = ratio;
				mHandler.sendEmptyMessage(0);
			}
		});
    	if (mMediaPlayerStateChangedListener != null)
    		mMediaPlayerProxy.registStateChangedListener(mMediaPlayerStateChangedListener);
    	mMediaPlayerProxy.setDisplay(mSurfaceTexture);
    	mVideoFrame = (VideoFrame) getParent();
    	mVideoFrame.showPlaceHolder(true);
    	mCurrentState = STATE_PREPARED;
    	return 0;
	}
	
	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		Log.d(TAG, "onMeasure widthMeasureSpec:" + widthMeasureSpec + "/heightMeasureSpec:" + heightMeasureSpec);
		final float MAX_ASPECT_RATIO_DEFORMATION_PERCENT = 0.01f;
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
		
		int width = getMeasuredWidth();
		int height = getMeasuredHeight();
		Log.d(TAG, "onMeasure width:" + width + "/height:" + height);
		if (mVideoAspectRatio  > 0 && !mIsVr) {
			float viewAspectRatio = (float) width / height;
			float aspectDeformation = mVideoAspectRatio / viewAspectRatio - 1;
			if (aspectDeformation > MAX_ASPECT_RATIO_DEFORMATION_PERCENT) {
				height = (int) (width / mVideoAspectRatio);
			} else if (aspectDeformation < -MAX_ASPECT_RATIO_DEFORMATION_PERCENT) {
				width = (int) (height * mVideoAspectRatio);
			}
		}
		setMeasuredDimension(width, height);
	}
}
