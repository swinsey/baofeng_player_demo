package bf.cloud.android.modules.p2p;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import android.os.Handler;
import android.os.Message;
import android.os.Handler.Callback;
import android.os.HandlerThread;
import android.os.Process;
import android.util.Log;
import bf.cloud.android.base.BFYConst;
import bf.cloud.android.modules.p2p.MediaCenter.NetState;
import bf.cloud.android.playutils.VideoDefinition;

/**
 * Created by wangtonggui
 */
public final class BFStream {

	static final String TAG = BFStream.class.getSimpleName();

	private static MediaCenter mMediaCenter = MediaCenter.getInstance();
	private int mMediaHandle = MediaCenter.INVALID_MEDIA_HANDLE;
	private int mStreamId = MediaCenter.INVALID_STREAM_ID;
	private MediaCenter.MediaInfo mMediaInfo = null;
	private ArrayList<MediaCenter.StreamInfo> mStreamInfoList = new ArrayList<MediaCenter.StreamInfo>();
	private MediaCenter.StreamInfo mCurrentStreamInfo = null;
	private StateCallBackHandler mCallBackHandler = null;
	private BFStreamMessageListener mStreamListener = null;
	private static CopyOnWriteArrayList<BFP2PListener> mP2PListeners = new CopyOnWriteArrayList<BFP2PListener>();
	private int mStreamWaitToPlay = MediaCenter.INVALID_STREAM_ID;
	private int mPort = BFYConst.DEFAULT_P2P_PORT;
	private int mStreamMode = MediaCenter.StreamMode.STREAM_HLS_MODE;
	private static P2pHandlerThread mP2pHandlerThread = null;
	private static String mSettingDataPath = null;
	private static int mNetState = NetState.NET_NOT_REACHABLE;
	private static P2pState mP2pState = P2pState.NOT_INIT;

	public final static int NO_ERROR = 0; // 无错误
	public final static int UNKNOWN_ERROR = -1; // 未知错误
	public final static int INVALID_PARAM = -2; // 无效的参数
	public final static int INVALID_HANDLE = -3; // 无效句柄
	public final static int INIT_ERROR = -4; // 初始化错误
	public final static int PORT_BIND_FAILED = -5; // 端口绑定失败
	public final static int INVALID_STREAM_ID = -6; // 无效的流ID
	public final static int GENERATE_URL_FAILED = -8; // 生成URL失败
	public final static int INVALID_URL = -10; // 无效的URL
	public final static int NOT_ENOUGH_SPACE = -11; // 存储空间不足
	public final static int FILE_IO_ERROR = -12; // 文件IO错误
	public final static int ALLOC_MEMORY_FAILED = -13; // 分配内存失败

	public BFStream(String settingDataPath) {
		Log.d(TAG, "new BFStream settingDataPath:" + settingDataPath);
		if (settingDataPath == null) {
			throw new NullPointerException("dataPath is null");
		}
		mSettingDataPath = settingDataPath;
		mCallBackHandler = new StateCallBackHandler();
		mMediaCenter.setCallback(mCallBackHandler);
	}

	public static void startP2p() {
		Log.d(TAG, "startP2p");
		if (mP2pHandlerThread == null) {
			mP2pHandlerThread = new P2pHandlerThread(TAG,
					Process.THREAD_PRIORITY_FOREGROUND);
			mP2pHandlerThread.start();
		}
	}

	public static void stopP2P() {
		Log.d(TAG, "uninitP2p");
		if (mP2pHandlerThread != null) {
			mP2pHandlerThread.p2pHandler
					.sendEmptyMessage(P2pHandlerThread.UNINIT_MEDIA_CENTER);
		}
	}

	public interface BFP2PListener {
		/**
		 * MediaCenter初始化成功
		 */
		public void onMediaCenterInitSuccess();

		/**
		 * MediaCenter初始化失败
		 */
		public void onMediaCenterInitFailed(int error);
	}

	public interface BFStreamMessageListener {
		public final static int MSG_TYPE_ERROR = 0;
		public final static int MSG_TYPE_NORMAL = 1;

		/**
		 * 从MediaCenter里面回调的消息，分为MSG_TYPE_ERROR和MSG_TYPE_NORMAL两种
		 * 
		 * @param type
		 *            MSG_TYPE_ERROR或MSG_TYPE_NORMAL
		 * @param data
		 * @param error
		 *            错误码
		 */
		public void onMessage(int type, int data, int error);

		/**
		 * 流创建就绪
		 */
		public void onStreamReady();

		/**
		 * 获取媒体信息失败
		 */
		public void onMediaInfoNotFound();
	}

	public BFStreamMessageListener getListener() {
		return mStreamListener;
	}

	public void registerStreamListener(BFStreamMessageListener listener) {
		mStreamListener = listener;
	}

	public void unregisterStreamListener() {
		mStreamListener = null;
	}

	public void registerP2PListener(BFP2PListener listener) {
		if (mP2PListeners.contains(listener)) {
			Log.d(TAG, "listener exists");
		}
		synchronized (BFStream.TAG) {
			mP2PListeners.add(listener);
		}
	}

	public void unregisterP2PListener(BFP2PListener listener) {
		if (mP2PListeners.contains(listener)) {
			mP2PListeners.remove(listener);
		}
	}

	class StateCallBackHandler implements MediaCenter.HandleStateCallback {
		protected static final String LOG_TAG = "BFStream_StateCallBack";

		public void OnStateChanged(int handle, int state, int error) {
			Log.d(TAG, "Handle State Changed to [" + state
					+ "] (0.IDLE 1.RUNNABLE 2.RUNNING 3.ACCOMPLISH 4.ERROR)");
			if (handle != mMediaHandle) {
				Log.d(TAG, "mMediaHandle error");
				return;
			}

			if (mStreamListener != null) {
				if (MediaCenter.MediaHandleState.MEDIA_HANDLE_ERROR == state) {
					mStreamListener.onMessage(
							BFStreamMessageListener.MSG_TYPE_ERROR, state,
							error);
				} else {
					mStreamListener.onMessage(
							BFStreamMessageListener.MSG_TYPE_NORMAL, state,
							error);
				}
			}
			switch (state) {
			case MediaCenter.MediaHandleState.MEDIA_HANDLE_IDLE: {
				break;
			}
			case MediaCenter.MediaHandleState.MEDIA_HANDLE_RUNNABLE: {
				// update Media Information
				mMediaInfo = mMediaCenter.GetMediaInfo(mMediaHandle);
				if (mMediaInfo == null || mMediaInfo.mediaStreamCount <= 0) {
					if (mStreamListener != null)
						mStreamListener.onMediaInfoNotFound();
					return;
				}
				MediaCenter.StreamInfo streamInfoList[] = new MediaCenter.StreamInfo[mMediaInfo.mediaStreamCount];
				streamInfoList = mMediaCenter.GetStreamInfo(mMediaHandle,
						mMediaInfo.mediaStreamCount);
				mStreamInfoList.clear();
				for (int i = 0; i < mMediaInfo.mediaStreamCount; i++) {
					mStreamInfoList.add(i, streamInfoList[i]);
				}
				if (mStreamListener != null)
					mStreamListener.onStreamReady();
				break;
			}
			case MediaCenter.MediaHandleState.MEDIA_HANDLE_RUNNING:
				break;
			case MediaCenter.MediaHandleState.MEDIA_HANDLE_ACCOMPLISH:
				break;
			case MediaCenter.MediaHandleState.MEDIA_HANDLE_ERROR:
				break;
			default:
				break;
			}
		}
	}
	
	public String getVideoName(){
		if (mMediaInfo != null)
			return mMediaInfo.mediaName;
		return "";
	}

	private int getDefaultStreamId() {
		int result = MediaCenter.INVALID_STREAM_ID;

		int streamCount = mStreamInfoList.size();
		if (streamCount > 0) {
			for (int i = 0; i < streamCount; i++) {
				MediaCenter.StreamInfo streamInfo = mStreamInfoList.get(i);
				if (streamInfo.defaultStream) {
					result = streamInfo.streamId;
					updateCurrentStreamInfo(streamInfo);
					break;
				}
			}

			if (result == MediaCenter.INVALID_STREAM_ID) {
				result = mStreamInfoList.get(streamCount / 2).streamId;
			}
		}

		return result;
	}

	private static void initMediaCenter() {
		Log.d(TAG, "initMediaCenter");
		mP2pHandlerThread.p2pHandler
				.sendEmptyMessage(P2pHandlerThread.INIT_MEDIA_CENTER);
	}

	/**
	 * 创建流
	 * 
	 * @return 正常返回0，异常返回-1
	 */
	public int createStream(String url, String token, int mode) {
		if (P2pState.NOT_INIT == mP2pState)
			return -1;
		mMediaHandle = mMediaCenter.CreateMediaHandle(url, token);
		Log.d(TAG, "mMediaHandle = " + mMediaHandle);
		if (mMediaHandle == MediaCenter.INVALID_MEDIA_HANDLE) {
			return -1;
		}
		return 0;
	}

	/**
	 * 开启流(可以在BFStreamMessageListener的onStreamReady中调用)
	 * 流地址：http://127.0.0.1:mPort/live.m3u8
	 * 
	 * @param
	 */
	public int startStream() {
		Log.d(TAG, "startStream");
		int result = MediaCenter.NativeReturnType.NO_ERROR;
		if (mStreamWaitToPlay == MediaCenter.INVALID_STREAM_ID)
			mStreamId = getDefaultStreamId();
		else{
			mStreamId = mStreamWaitToPlay;
			if (mStreamInfoList != null && mStreamInfoList.size() != 0){
				for (MediaCenter.StreamInfo info : mStreamInfoList){
					if (info.streamId == mStreamId){
						updateCurrentStreamInfo(info);
					}
				}
			}
		}
		for (int i = 0; i < 50; i++) {
			if (mPort > 65400) {
				mPort = BFYConst.DEFAULT_P2P_PORT + i;
			}
			mPort += i;
			result = mMediaCenter.StartStreamService(mMediaHandle, mStreamId,
					mStreamMode, mPort);
			switch (result) {
			case MediaCenter.NativeReturnType.NO_ERROR: {
				Log.d(TAG, "Start Stream Bind Port[" + mPort + "] Success");
				return result;
			}
			case MediaCenter.NativeReturnType.PORT_BIND_FAILED:
				continue;
			default:
				return result;
			}
		}
		Log.d(TAG, "Bind Port fail, try to [" + mPort + "]");
		return result;
	}

	private void updateCurrentStreamInfo(MediaCenter.StreamInfo info) {
		mCurrentStreamInfo = info;
	}
	
	public String getCurrentStreamDefinition(){
		if (mCurrentStreamInfo == null)
			return null;
		return mCurrentStreamInfo.streamName;
	}

	/**
	 * 关闭流
	 */
	public int closeStream() {
		Log.d(TAG, "closeStream mMediaHandle:" + mMediaHandle);
		int ret = -1;
		if (MediaCenter.INVALID_MEDIA_HANDLE != mMediaHandle) {
			ret = mMediaCenter.StopStreamService(mMediaHandle);
			mStreamId = -1;
		}
		return ret;
	}

	/**
	 * 销毁流
	 */
	public int destoryStream() {
		Log.d(TAG, "destoryStream");
		int ret = -1;
		ret = closeStream();
		if (ret < 0)
			return ret;
		ret = mMediaCenter.DestroyMediaHandle(mMediaHandle);
		mMediaHandle = MediaCenter.INVALID_MEDIA_HANDLE;
		return ret;
	}

	// //////////////////////////////////////////////////////////////////////////////////////////////

	/**
	 * 设置网络类型，静态方法
	 * 
	 * @param state
	 * @return
	 */
	public static int setNetState(int state) {
		Log.d(TAG, "SetNetState [" + state + "]");
		return mMediaCenter.SetNetState(state);
	}

	public MediaCenter.MediaInfo getMediaInfo() {
		return mMediaInfo;
	}

	public ArrayList<MediaCenter.StreamInfo> getStreamInfoList() {
		return mStreamInfoList;
	}

	public int getState() {
		return mMediaCenter.GetHandleState(mMediaHandle);
	}

	public int getDownloadSpeed() {
		return mMediaCenter.GetDownloadSpeed(mMediaHandle);
	}

	public int getDownloadPercent() {
		return mMediaCenter.GetDownloadPercent(mMediaHandle);
	}

	public int calcCanPlayTime(int time) {
		return mMediaCenter.CalcCanPlayTime(mMediaHandle, time);
	}

	public int setCurPlayTime(int time) {
		return mMediaCenter.SetCurPlayTime(mMediaHandle, time);
	}

	public int getStreamId() {
		return mStreamId;
	}
	
	public String getStreamUrl() {
		String url = null;
		switch (mStreamMode) {
		case MediaCenter.StreamMode.STREAM_MP4_MODE:
			url = BFYConst.P2PSERVER + ":" + mPort;
			break;
		case MediaCenter.StreamMode.STREAM_HLS_MODE:
			url = BFYConst.P2PSERVER + ":" + mPort + "/bfcloud.m3u8";
			break;
		default:
			url = BFYConst.P2PSERVER + ":" + mPort;
			break;
		}
		Log.d(TAG, "getStreamUrl:" + url);
		return url;
	}

	private String getErrorInfo(int error) {
		return mMediaCenter.GetErrorInfo(error);
	}

	private enum P2pState {
		NOT_INIT(-1), INITING(0), INITED(1);
		private P2pState(int state) {
			this.state = state;
		}

		private int state = -1;

		private int value() {
			return state;
		}

	}
	
	public ArrayList<String> getAllDefinitions(){
		ArrayList<String> definitions = new ArrayList<String>();
		if (mStreamInfoList == null || mStreamInfoList.size() == 0)
			return null;
		for (MediaCenter.StreamInfo info : mStreamInfoList){
			definitions.add(info.streamName);
		}
		return definitions;
	}

	private static class P2pHandlerThread extends HandlerThread {
		private final static int INIT_MEDIA_CENTER = 1;
		private final static int UNINIT_MEDIA_CENTER = 2;

		private Handler p2pHandler = null;
		private Callback callback = new Callback() {

			@Override
			public boolean handleMessage(Message msg) {
				Log.d(TAG, "handleMessage what = " + msg.what);
				switch (msg.what) {
				case INIT_MEDIA_CENTER:
					if (mP2pState == P2pState.NOT_INIT) {
						Log.d(TAG, "Init Media Center. data_path:["
								+ mSettingDataPath + "]");
						int ret = -1;
						mP2pState = P2pState.INITING;
						ret = mMediaCenter.InitMediaCenter(mSettingDataPath,
								mNetState);
						if (ret < 0) {
							mP2pState = P2pState.NOT_INIT;
							for (int i = 0; i < mP2PListeners.size(); i++) {
								mP2PListeners.get(i).onMediaCenterInitFailed(
										ret);
							}
						} else {
							Log.d(TAG, "Init Media Center success");
							mP2pState = P2pState.INITED;
							for (int i = 0; i < mP2PListeners.size(); i++) {
								mP2PListeners.get(i).onMediaCenterInitSuccess();
							}
						}
					}
					break;
				case UNINIT_MEDIA_CENTER: {
					// 这里暂时对MediaCenter不进行卸载操作，思路是：一旦加载，就不卸载了
					Log.d(TAG, "uninit Media Center");
				}
				default:
					break;
				}
				return false;
			}
		};

		public P2pHandlerThread(String name, int priority) {
			super(name, priority);
			Log.d(TAG, "new P2pHandlerThread " + name);
		}

		@Override
		protected void onLooperPrepared() {
			Log.d(TAG, "thread " + getName() + " onLooperPrepared");
			p2pHandler = new Handler(getLooper(), callback);
			initMediaCenter();
			super.onLooperPrepared();
		}

		@Override
		public void run() {
			super.run();
		}
	}
	
	public void changeDefinition(String definition){
		int streamId = getStreamIdByName(definition);
		if (streamId < 0){
			return;
		}
		mStreamWaitToPlay = streamId;
	}
	
	private int getStreamIdByName(String name){
		int streamId = -1;
		if (mStreamInfoList == null || mStreamInfoList.size() == 0){
			Log.d(TAG, "You have not got the StreamInfo");
			return -1;
		}
		for (MediaCenter.StreamInfo info : mStreamInfoList){
			if (info.streamName.equalsIgnoreCase(name)){
				streamId = info.streamId;
				break;
			}
		}
		return streamId;
	}

	private int getStreamIdByDefinition(VideoDefinition definition) {
		int result = MediaCenter.INVALID_STREAM_ID;
		int streamCount = (mStreamInfoList != null) ? mStreamInfoList.size() : 0;

		if (streamCount > 0) {
			int defaultStreamId = MediaCenter.INVALID_STREAM_ID;
			for (int i = 0; i < streamCount; ++i) {
				MediaCenter.StreamInfo streamInfo = mStreamInfoList.get(i);
				if (streamInfo.defaultStream) {
					defaultStreamId = streamInfo.streamId;
					break;
				}
			}

			if (definition == VideoDefinition.UNKNOWN) {
				result = defaultStreamId;
			} else {
				for (int i = 0; i < streamCount; ++i) {
					MediaCenter.StreamInfo streamInfo = mStreamInfoList.get(i);
					if (streamInfo.streamName.equalsIgnoreCase(definition
							.toString())) {
						result = streamInfo.streamId;
						break;
					}
				}
			}

			if (result == MediaCenter.INVALID_STREAM_ID) {
				switch (definition) {
				case FLUENT:
					result = mStreamInfoList.get(0).streamId;
					break;
				case STANDARD:
					result = mStreamInfoList.get(1).streamId;
				case HIGH:
					result = mStreamInfoList.get(streamCount / 2).streamId;
					break;
				case P1080:
					result = mStreamInfoList.get(streamCount / 2 + 1).streamId;
					break;
				case K2:
					result = mStreamInfoList.get(streamCount - 1).streamId;
					break;
				default:
					result = mStreamInfoList.get(0).streamId;
					break;
				}
			}

			if (result == MediaCenter.INVALID_STREAM_ID) {
				result = defaultStreamId;
			}
		}

		return result;
	}
}
