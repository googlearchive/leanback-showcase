/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 *
 */

package android.support.v17.leanback.supportleanbackshowcase.app.media;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.support.v17.leanback.app.PlaybackOverlayFragment;
import android.support.v17.leanback.widget.Action;
import android.support.v17.leanback.widget.ArrayObjectAdapter;
import android.support.v17.leanback.widget.PlaybackControlsRow;
import android.support.v17.leanback.widget.PlaybackControlsRowPresenter;
import android.util.Log;
import android.view.SurfaceHolder;

import java.io.IOException;

/**<p>>
 * This glue extends the {@link MediaPlayerGlue} and handles all the
 * interactions between the fragment, playback controls, and the video player implemented as an
 * Android media player. It makes asynchronous calls to the media player and notifies the fragment
 * for playback status changes.
 * <p/>
 */
public abstract class VideoMediaPlayerGlue extends MediaPlayerGlue implements
        AudioManager.OnAudioFocusChangeListener{

    private final PlaybackControlsRow.ClosedCaptioningAction mClosedCaptioningAction;
    private final PlaybackControlsRow.PictureInPictureAction mPipAction;
    private MediaPlayer mPlayer;
    private AudioManager mAudioManager;
    int mAudioFocus = AudioManager.AUDIOFOCUS_LOSS;
    private static final String TAG = "VideoMediaPlayerGlue";

    public VideoMediaPlayerGlue(Context context, PlaybackOverlayFragment fragment) {
        super(context, fragment);

        mPlayer = new MediaPlayer();
        mAudioManager = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
        // Instantiate secondary actions
        mClosedCaptioningAction = new PlaybackControlsRow.ClosedCaptioningAction(context);
        mPipAction = new PlaybackControlsRow.PictureInPictureAction(context);
        setFadingEnabled(true);
    }

    @Override protected void addSecondaryActions(ArrayObjectAdapter secondaryActionsAdapter) {
        secondaryActionsAdapter.add(mClosedCaptioningAction);
        secondaryActionsAdapter.add(mThumbsDownAction);
        secondaryActionsAdapter.add(mThumbsUpAction);
        if (VideoExampleActivity.supportsPictureInPicture(getContext())) {
            secondaryActionsAdapter.add(mPipAction);
        }
    }

    @Override public void onActionClicked(Action action) {
        super.onActionClicked(action);
        if (action == mClosedCaptioningAction) {
            mClosedCaptioningAction.nextIndex();
        } else if (action == mPipAction) {
            ((Activity) getContext()).enterPictureInPictureMode();
        }
    }

    public void setupControlsRowPresenter(PlaybackControlsRowPresenter presenter) {
        // TODO: hahnr@ move into resources
        presenter.setProgressColor(Color.parseColor("#EEFF41"));
        presenter.setBackgroundColor(Color.parseColor("#007236"));
    }

    public boolean requestAudioFocus() {
        return mAudioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    public boolean abandonAudioFocus() {
        return mAudioManager.abandonAudioFocus(this) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    /**
     * Will reset the {@link MediaPlayer} and the glue such that a new file can be played. You are
     * not required to call this method before playing the first file. However you have to call it
     * before playing a second one.
     */
    void reset() {
        mInitialized = false;
        if (mPlayer != null) {
            mPlayer.reset();
        }
    }

    /**
     * @see MediaPlayer#setDisplay(SurfaceHolder)
     */
    public void setDisplay(SurfaceHolder surfaceHolder) {
        mPlayer.setDisplay(surfaceHolder);
    }

    @Override public boolean isMediaPlaying() {
        return mPlayer.isPlaying();
    }

    @Override public int getMediaDuration() {
        return mInitialized ? mPlayer.getDuration() : 0;
    }

    @Override public int getCurrentPosition() {
        return mInitialized ? mPlayer.getCurrentPosition() : 0;
    }

    @Override protected void startPlayback(int speed) throws IllegalStateException {
        if (requestAudioFocus()) {
            mAudioFocus = AudioManager.AUDIOFOCUS_GAIN;
        } else {
            Log.e(TAG, "Video player could not obtain audio focus in startPlayback");
            return;
        }
        prepareIfNeededAndPlay(mMediaMetaData);
    }

    @Override protected void pausePlayback() {
        if (mPlayer.isPlaying()) {
            mPlayer.pause();
        }
    }

    /**
     * Called whenever the user presses fast-forward/rewind or when the user keeps the corresponding
     * action pressed.
     *
     * @param newPosition The new position of the media track in milliseconds.
     */
    @Override
    protected void seekTo(int newPosition) {
        mPlayer.seekTo(newPosition);
    }

    public void prepareIfNeededAndPlay(MediaMetaData mediaMetaData) {
        if (mediaMetaData == null) {
            throw new RuntimeException("Provided metadata is null!");
        }
        if (requestAudioFocus()) {
            mAudioFocus = AudioManager.AUDIOFOCUS_GAIN;
        } else {
            Log.e(TAG, "Video player could not obtain audio focus in prepareIfNeededAndPlay");
            return;
        }
        if (mInitialized && isMediaItemsTheSame(mMediaMetaData, mediaMetaData)) {
            if (!isMediaPlaying()) {
                // This media item had been already playing but is being paused. Will resume the player.
                // No need to reset the player.
                mPlayer.start();
                onStateChanged();
            }
        } else {
            prepareNewMedia(mediaMetaData);
        }
        mMediaMetaData = mediaMetaData;
    }

    public void saveUIState() {
        onMetadataChanged();
        onStateChanged();
    }

    private boolean isMediaItemsTheSame(MediaMetaData currentMediaMetaData,
                                        MediaMetaData newMediaMetaData) {
        if (currentMediaMetaData == newMediaMetaData) {
            return true;
        }
        if (currentMediaMetaData == null || newMediaMetaData == null) {
            return false;
        }
        if (newMediaMetaData.getMediaSourceUri() != null) {
            return currentMediaMetaData.getMediaSourceUri().equals(
                    newMediaMetaData.getMediaSourceUri()
            );
        }
        if (newMediaMetaData.getMediaSourcePath() != null) {
            return currentMediaMetaData.getMediaSourcePath().equals(
                    newMediaMetaData.getMediaSourcePath()
            );
        }
        return false;
    }

    private void prepareNewMedia(final MediaMetaData mediaMetaData) {
        reset();
        try {
            if (mediaMetaData.getMediaSourceUri() != null) {
                mPlayer.setDataSource(getContext(), mediaMetaData.getMediaSourceUri());
            }
            else mPlayer.setDataSource(mediaMetaData.getMediaSourcePath());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        mPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override public void onPrepared(MediaPlayer mp) {
                mInitialized = true;
                mPlayer.start();
                onMetadataChanged();
                onStateChanged();
                updateProgress();
            }
        });
        mPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override public void onCompletion(MediaPlayer mp) {
                if (mInitialized && mMediaFileStateChangeListener != null)
                    mMediaFileStateChangeListener.onMediaStateChanged(mediaMetaData,
                            MediaUtils.MEDIA_STATE_COMPLETED);
            }
        });
        mPlayer.setOnBufferingUpdateListener(new MediaPlayer.OnBufferingUpdateListener() {
            @Override public void onBufferingUpdate(MediaPlayer mp, int percent) {
                mControlsRow.setBufferedProgress((int) (mp.getDuration() * (percent / 100f)));
            }
        });
        mPlayer.prepareAsync();
        onStateChanged();
    }

    public void releaseResources() {
        if (mPlayer != null) {
            mPlayer.reset();
            mPlayer.release();
            mPlayer = null;
        }
        if (!abandonAudioFocus() ) {
            Log.e(TAG, "Video player could not abandon audio focus in releaseResources");
        }
    }

    @Override
    public void onAudioFocusChange(int focusChange) {

    }
}
