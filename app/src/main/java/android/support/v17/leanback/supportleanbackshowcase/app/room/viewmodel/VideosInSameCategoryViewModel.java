/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.support.v17.leanback.supportleanbackshowcase.app.room.viewmodel;

import android.app.Application;
import android.arch.lifecycle.AndroidViewModel;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.ViewModel;
import android.arch.lifecycle.ViewModelProvider;
import android.support.annotation.NonNull;
import android.support.v17.leanback.supportleanbackshowcase.app.room.db.repo.VideosRepository;
import android.support.v17.leanback.supportleanbackshowcase.app.room.db.entity.VideoEntity;

import java.util.List;

public class VideosInSameCategoryViewModel extends AndroidViewModel {

    // The parameter used to create view model
    private final String mCategory;

    /**
     * List of VideoEntities in same category
     */
    private final LiveData<List<VideoEntity>> mVideosInSameCategory;

    // instance of the repository
    private final VideosRepository mRepository;


    public VideosInSameCategoryViewModel(@NonNull Application application, final String category) {
        super(application);
        mCategory = category;
        mRepository = VideosRepository.getVideosRepositoryInstance();

        mVideosInSameCategory =mRepository.getVideosInSameCategoryLiveData(mCategory);
    }

    /**
     * Return the video entity list in same category using live data
     *
     * @return live data
     */
    public LiveData<List<VideoEntity>> getVideosInSameCategory() {
        return mVideosInSameCategory;
    }

    /**
     * The factory can take category as the parameter to create according view model
     */
    public static class Factory extends ViewModelProvider.NewInstanceFactory {
        @NonNull
        private final Application mApplication;

        private final String mCategory;

        public Factory(@NonNull Application application, String category) {
            mApplication = application;
            mCategory = category;
        }

        @Override
        public <T extends ViewModel> T create(Class<T> modelClass) {
            return (T) new VideosInSameCategoryViewModel(mApplication, mCategory);
        }
    }

    public void updateDatabase(VideoEntity video, String category, String value) {
        mRepository.updateDatabase(video, category, value);
    }
}
