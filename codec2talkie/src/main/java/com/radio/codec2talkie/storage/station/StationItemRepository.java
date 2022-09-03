package com.radio.codec2talkie.storage.station;

import android.app.Application;

import androidx.lifecycle.LiveData;

import com.radio.codec2talkie.storage.AppDatabase;

import java.util.List;

public class StationItemRepository {
    private static final String TAG = StationItemRepository.class.getSimpleName();

    private final StationItemDao _stationItemDao;
    private final LiveData<List<StationItem>> _stationItems;

    public StationItemRepository(Application application) {
        AppDatabase appDatabase = AppDatabase.getDatabase(application);
        _stationItemDao = appDatabase.stationitemDao();
        _stationItems = _stationItemDao.getAllStationItems();
    }

    public LiveData<List<StationItem>> getAllStationItems() { return _stationItems; }

    public void upsertStationItem(StationItem stationItem) {
        AppDatabase.getDatabaseExecutor().execute(() -> {
            StationItem oldStationItem  = _stationItemDao.getStationItem(stationItem.getSrcCallsign());
            if (oldStationItem != null) {
                oldStationItem.updateFrom(stationItem);
                _stationItemDao.updateStationItem(oldStationItem);
                Log.i(TAG, "UPDATE " + oldStationItem.getSrcCallsign());
            } else {
                _stationItemDao.insertStationItem(stationItem);
                Log.i(TAG, "INSERT " + stationItem.getSrcCallsign());
            }
        });
    }
}
