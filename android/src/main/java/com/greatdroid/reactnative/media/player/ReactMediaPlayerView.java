package com.greatdroid.reactnative.media.player;

import android.content.Context;
import android.util.Log;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.ReactContext;
import com.google.android.exoplayer.ExoPlayer;
import com.google.android.exoplayer.metadata.id3.BinaryFrame;
import com.google.android.exoplayer.metadata.id3.Id3Frame;
import com.google.android.exoplayer.metadata.id3.TxxxFrame;
import com.google.android.exoplayer.text.Cue;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ReactMediaPlayerView extends FrameLayout implements LifecycleEventListener {
    private static final String TAG = "ReactMediaPlayerView";

    private final Runnable measureAndLayout = new Runnable() {
        @Override
        public void run() {
            Log.d(TAG, "measure...w=" + getWidth() + ", h=" + getHeight());
            measure(
                    MeasureSpec.makeMeasureSpec(getWidth(), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(getHeight(), MeasureSpec.EXACTLY));

            Log.d(TAG, "layout...l=" + getLeft() + ", t=" + getTop() + ", r=" + getRight() + ", b=" + getBottom());
            layout(getLeft(), getTop(), getRight(), getBottom());
        }
    };

    private MediaPlayerControllerOwner mediaPlayerControllerOwner;
    private MediaPlayerController mediaPlayerController;

    private String uri;
    private boolean loop;
    private boolean autoplay;
    private boolean muted;
    private String preload;

    private boolean playWhenReadySnapshot;
    private long playPositionSnapshot = 0;

    private MediaPlayerListener mediaPlayerListener;

    private Pattern pattern1;
    private Pattern pattern2;
    private Pattern pattern3;
    private Pattern pattern4;
    private Pattern pattern5;
    private Runnable onProgress = new Runnable() {
        @Override
        public void run() {
            notifyProgress();
            postDelayed(onProgress, 500);
        }
    };
    private final MediaPlayerController.BaseEventListener l = new MediaPlayerController.BaseEventListener() {

        @Override
        public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
            measureAndLayout.run();
        }

        @Override
        public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
            Log.d(TAG, "onPlayerStateChanged...playWhenReady=" + playWhenReady + ", state=" + descPlaybackState(playbackState));
            if (mediaPlayerListener != null) {
                switch (playbackState) {
                    case ExoPlayer.STATE_BUFFERING:
                        if (!playWhenReady) {
                            mediaPlayerListener.onPlayerPaused();
                        }
                    case ExoPlayer.STATE_PREPARING:
                        mediaPlayerListener.onPlayerBuffering();
                        break;
                    case ExoPlayer.STATE_ENDED:
                        notifyProgress();
                        mediaPlayerListener.onPlayerFinished();
                        break;
                    case ExoPlayer.STATE_IDLE:
                        break;
                    case ExoPlayer.STATE_READY:
                        mediaPlayerListener.onPlayerBufferReady();
                        if (playWhenReady) {
                            mediaPlayerListener.onPlayerPlaying();
                        } else {
                            mediaPlayerListener.onPlayerPaused();
                        }
                        break;
                    default:
                        break;
                }
            }

            if (playbackState == ExoPlayer.STATE_READY) {
                notifyProgress();
            }
            if (playbackState == ExoPlayer.STATE_READY && mediaPlayerController != null && mediaPlayerController.getPlayWhenReady()) {
                startProgressTimer();
            } else {
                stopProgressTimer();
            }
        }

        @Override
        public void onCues(List<Cue> cues) {

        }

        @Override
        public void onMetadata(List<Id3Frame> metadata) {
            for (Id3Frame id3Frame : metadata) {
                JSONObject jsonObject = new JSONObject();
                if (id3Frame instanceof BinaryFrame) {
                    BinaryFrame binaryFrame = (BinaryFrame) id3Frame;
                    try {
                        String string = new String(binaryFrame.data, "ISO-8859-1");

                        Matcher matcher1 = pattern1.matcher(string);
                        if (matcher1.find()) {
                            Matcher matcher2 = pattern2.matcher(matcher1.group(0));
                            if (matcher2.find()) {
                                try {
                                    jsonObject.put("ut", matcher2.group(0));
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                        Matcher matcher3 = pattern3.matcher(string);
                        if (matcher3.find()) {
                            Matcher matcher4 = pattern4.matcher(matcher3.group(0));
                            if (matcher4.find()) {
                                try {
                                    jsonObject.put("tc", matcher4.group(0));
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                        mediaPlayerListener.onMetadata(jsonObject.toString());
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                } else if (id3Frame instanceof TxxxFrame) {
                    TxxxFrame txxxFrame = (TxxxFrame) id3Frame;

                    Matcher matcher5 = pattern5.matcher(txxxFrame.value);
                    if (matcher5.find()) {
                        try {
                            jsonObject.put("tc", matcher5.group(0));
                            mediaPlayerListener.onMetadata(jsonObject.toString());
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                }
                Log.d(TAG,"onMetadata---> jsonObject: " + jsonObject.toString());
            }
        }
    };

    public ReactMediaPlayerView(final Context context) {
        super(context);

        pattern1 = Pattern.compile("\"ut\":\"[\\d]+\"");
        pattern2 = Pattern.compile("[\\d]+");
        pattern3 = Pattern.compile("\"tc\":\"[\\d]{2}:[\\d]{2}:[\\d]{2}:[\\d]{2}\"");
        pattern4 = Pattern.compile("[\\d]{2}:[\\d]{2}:[\\d]{2}:[\\d]{2}");
        pattern5 = Pattern.compile("[\\d]{2}:[\\d]{2}:[\\d]{2}.[\\d]+");

        mediaPlayerControllerOwner = new MediaPlayerControllerOwner() {

            @Override
            public void onOwnershipChanged(MediaPlayerControllerOwner owner, MediaPlayerController controller) {
                if (owner == mediaPlayerControllerOwner) {
                    Log.d(TAG, "onOwnershipChanged...add view");
                    controller.addEventListener(l);
                    mediaPlayerController = controller;

                    addView(controller.getView(), new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));
                    updateProps(controller);
                    if (playPositionSnapshot > 0) {
                        controller.seekTo(playPositionSnapshot);
                    }
                } else {
                    Log.d(TAG, "onOwnershipChanged...remove view");
                    if (mediaPlayerController != null) {
                        playPositionSnapshot = mediaPlayerController.getCurrentPosition();
                        mediaPlayerController.removeEventListener(l);
                        mediaPlayerController.setSurfaceTexture(null);
                        mediaPlayerController.setContentUri(null);
                        removeView(mediaPlayerController.getView());
                    }

                    stopProgressTimer();
                    if (mediaPlayerListener != null) {
                        mediaPlayerListener.onPlayerFinished();
                    }
                    mediaPlayerController = null;
                }
            }
        };
    }

    public MediaPlayerController getMediaPlayerController() {
        return mediaPlayerControllerOwner.requestOwnership(getContext());
    }

    //for debug info
    private String descPlaybackState(int state) {
        switch (state) {
            case ExoPlayer.STATE_BUFFERING:
                return "buffering";
            case ExoPlayer.STATE_PREPARING:
                return "preparing";
            case ExoPlayer.STATE_ENDED:
                return "ended";
            case ExoPlayer.STATE_IDLE:
                return "idle";
            case ExoPlayer.STATE_READY:
                return "ready";
            default:
                return "unknown";
        }
    }

    public void setUri(String uri) {
        this.uri = uri;
        updateProps(mediaPlayerController);
    }

    public void setLoop(boolean loop) {
        this.loop = loop;
        updateProps(mediaPlayerController);
    }

    public void setPreload(String preload) {
        this.preload = preload;
        updateProps(mediaPlayerController);
    }

    public void setAutoplay(boolean autoplay) {
        this.autoplay = autoplay;
        updateProps(mediaPlayerController);
    }

    public void setMuted(boolean muted) {
        this.muted = muted;
        updateProps(mediaPlayerController);
    }

    private void updateProps(MediaPlayerController playerController) {
        if (playerController != null) {
            playerController.setContentUri(uri);
            if (autoplay) {
                playerController.play();
            } else {
                if (preload != null && preload.equals("auto")) {
                    playerController.prepareToPlay();
                }
            }
            playerController.setLoop(loop);
            playerController.setMuted(muted);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        Log.d(TAG, "onAttachedToWindow...");
        super.onAttachedToWindow();
        if (getContext() instanceof ReactContext) {
            ((ReactContext) getContext()).addLifecycleEventListener(this);
        }

        if (autoplay || "auto".equals(preload)) {
            mediaPlayerControllerOwner.requestOwnership(getContext());
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        Log.d(TAG, "onDetachedFromWindow...");
        super.onDetachedFromWindow();
        if (getContext() instanceof ReactContext) {
            ((ReactContext) getContext()).removeLifecycleEventListener(this);
        }
        mediaPlayerControllerOwner.abandonOwnership();
    }

    @Override
    public void onHostResume() {
        Log.d(TAG, "onHostResume...");
        if (playWhenReadySnapshot) {
            if (mediaPlayerController != null) {
                mediaPlayerController.play();
            }
        }
    }

    @Override
    public void onHostPause() {
        Log.d(TAG, "onHostPause...");
        if (mediaPlayerController != null) {
            playWhenReadySnapshot = mediaPlayerController.getPlayWhenReady();
            mediaPlayerController.pause();
        }
    }

    @Override
    public void onHostDestroy() {
        Log.d(TAG, "onHostDestroy...");
        mediaPlayerControllerOwner.abandonOwnership();
    }

    private void notifyProgress() {
        if (mediaPlayerController != null) {
            long current = mediaPlayerController.getCurrentPosition();
            long total = mediaPlayerController.getDuration();
            long buffered = mediaPlayerController.getBufferedPosition();
            if (mediaPlayerListener != null) {
                mediaPlayerListener.onPlayerProgress(current, total, buffered);
            }
        }
    }

    private void startProgressTimer() {
        post(onProgress);
    }

    private void stopProgressTimer() {
        removeCallbacks(onProgress);
    }

    public void setMediaPlayerListener(MediaPlayerListener listener) {
        this.mediaPlayerListener = listener;
    }

    public interface MediaPlayerListener {

        void onPlayerPlaying();

        void onPlayerPaused();

        void onPlayerFinished();

        void onPlayerBuffering();

        void onPlayerBufferReady();

        void onPlayerProgress(long current, long total, long buffered);

        void onMetadata(String metadata);
    }

    private static abstract class MediaPlayerControllerOwner {
        private static MediaPlayerController mediaPlayerController;
        private static MediaPlayerControllerOwner activeOwner;

        public MediaPlayerControllerOwner() {
        }

        public final void abandonOwnership() {
            if (activeOwner == this) {
                onOwnershipChanged(null, null);
                activeOwner = null;
                if (mediaPlayerController != null) {
                    mediaPlayerController.release();
                    mediaPlayerController = null;
                }
            }
        }

        public abstract void onOwnershipChanged(MediaPlayerControllerOwner owner, MediaPlayerController controller);


        public MediaPlayerController requestOwnership(Context context) {
            if (mediaPlayerController == null) {
                mediaPlayerController = new MediaPlayerController(context);
            }

            if (activeOwner != this) {
                if (activeOwner != null) {
                    activeOwner.onOwnershipChanged(this, mediaPlayerController);
                }
                activeOwner = this;
                this.onOwnershipChanged(this, mediaPlayerController);
            }

            return mediaPlayerController;
        }
    }
}
