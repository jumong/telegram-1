package org.telegram.android.media;

import android.graphics.*;
import android.media.MediaScannerConnection;
import android.os.*;
import com.extradea.framework.images.utils.ImageUtils;
import org.telegram.android.StelsApplication;
import org.telegram.android.core.model.media.*;
import org.telegram.android.log.Logger;
import org.telegram.android.ui.UiNotifier;
import org.telegram.android.util.IOUtils;
import org.telegram.api.*;
import org.telegram.api.engine.file.Downloader;
import org.telegram.mtproto.secure.CryptoUtils;
import org.telegram.mtproto.secure.Entropy;

import java.io.*;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.*;

/**
 * Author: Korshakov Stepan
 * Created: 10.08.13 20:46
 */
public class DownloadManager {

    private static final String TAG = "Downloader";

    public static String getVideoKey(TLLocalVideo video) {
        if (video.getVideoLocation() instanceof TLLocalFileVideoLocation) {
            return ((TLLocalFileVideoLocation) video.getVideoLocation()).getDcId() + "_" + ((TLLocalFileVideoLocation) video.getVideoLocation()).getVideoId();
        } else if (video.getVideoLocation() instanceof TLLocalEncryptedFileLocation) {
            return ((TLLocalEncryptedFileLocation) video.getVideoLocation()).getDcId() + "_" + ((TLLocalEncryptedFileLocation) video.getVideoLocation()).getId();
        }
        return null;
    }

    public static String getPhotoKey(TLLocalPhoto photo) {
        if (photo.getFullLocation() instanceof TLLocalFileLocation) {
            return ((TLLocalFileLocation) photo.getFullLocation()).getDcId() + "_" + ((TLLocalFileLocation) photo.getFullLocation()).getVolumeId() + "_" + ((TLLocalFileLocation) photo.getFullLocation()).getLocalId();
        } else if (photo.getFullLocation() instanceof TLLocalEncryptedFileLocation) {
            return ((TLLocalEncryptedFileLocation) photo.getFullLocation()).getDcId() + "_" + ((TLLocalEncryptedFileLocation) photo.getFullLocation()).getId();
        } else {
            return null;
        }
    }

    public static String getDocumentKey(TLLocalDocument document) {
        if (document.getFileLocation() instanceof TLLocalFileDocument) {
            return ((TLLocalFileDocument) document.getFileLocation()).getDcId() + "_" +
                    ((TLLocalFileDocument) document.getFileLocation()).getId() + "_" + document.getFileName();
        } else if (document.getFileLocation() instanceof TLLocalEncryptedFileLocation) {
            return ((TLLocalEncryptedFileLocation) document.getFileLocation()).getDcId() + "_" + ((TLLocalEncryptedFileLocation) document.getFileLocation()).getId();
        }
        return null;
    }


    private final int SMALL_THUMB_SIDE;

    private DownloadPersistence downloadPersistence;

    private HashSet<String> enqueued = new HashSet<String>();

    private class DownloadRecord {
        public TLLocalVideo video;
        public TLLocalPhoto photo;
        public TLLocalDocument doc;
        public int downloaded;
        public int downloadedPercent;
        public DownloadState state;
        public int downloadTask;
    }

    private ExecutorService service;

    private UiNotifier notifier = new UiNotifier();
    private StelsApplication application;

    private HashMap<String, DownloadRecord> records = new HashMap<String, DownloadRecord>();

    private CopyOnWriteArrayList<WeakReference<DownloadListener>> listeners = new CopyOnWriteArrayList<WeakReference<DownloadListener>>();

    public DownloadManager(StelsApplication application) {
        this.service = Executors.newFixedThreadPool(3);
        this.application = application;
        this.downloadPersistence = new DownloadPersistence(application);

        final int margin = (int) (4 * application.getResources().getDisplayMetrics().density);
        SMALL_THUMB_SIDE = ((Math.min(application.getResources().getDisplayMetrics().widthPixels, application.getResources().getDisplayMetrics().heightPixels) - 5 * margin) / 4);
    }

    public void registerListener(DownloadListener listener) {
        for (WeakReference<DownloadListener> ref : listeners) {
            if (ref.get() == listener) {
                return;
            }
        }
        listeners.add(new WeakReference<DownloadListener>(listener));
    }

    public void unregisterListener(DownloadListener listener) {
        for (WeakReference<DownloadListener> ref : listeners) {
            if (ref.get() == listener) {
                listeners.remove(ref);
                return;
            }
        }
    }

    public DownloadState getState(String key) {
        if (downloadPersistence.isDownloaded(key))
            return DownloadState.COMPLETED;

        if (records.containsKey(key)) {
            DownloadRecord record = records.get(key);
            return record.state;
        }

        return DownloadState.NONE;
    }

    public int getDownloadProgress(String key) {
        if (downloadPersistence.isDownloaded(key))
            return 100;

        if (records.containsKey(key)) {
            DownloadRecord record = records.get(key);
            return record.downloadedPercent;
        }

        return 0;
    }

    public void requestDownload(TLLocalDocument doc) {
        final String resourceKey = getDocumentKey(doc);

        if (downloadPersistence.isDownloaded(resourceKey))
            return;

        final DownloadRecord record;
        if (records.containsKey(resourceKey)) {
            record = records.get(resourceKey);
            if (record.state == DownloadState.CANCELLED ||
                    record.state == DownloadState.FAILURE ||
                    record.state == DownloadState.NONE) {
                record.state = DownloadState.PENDING;
                record.downloaded = 0;
                record.downloadedPercent = 0;
                record.doc = doc;
                updateState(resourceKey, record.state, 0, 0);
            }
        } else {
            record = new DownloadRecord();
            record.state = DownloadState.PENDING;
            record.downloaded = 0;
            record.downloadedPercent = 0;
            record.doc = doc;
            records.put(resourceKey, record);
            updateState(resourceKey, record.state, 0, 0);
        }

        requestDownload(resourceKey, record,
                getDownloadDocFile(resourceKey),
                doc.getFileLocation());
    }


    public void requestDownload(TLLocalPhoto photo) {

        final String resourceKey = getPhotoKey(photo);

        if (downloadPersistence.isDownloaded(resourceKey))
            return;

        final DownloadRecord record;
        if (records.containsKey(resourceKey)) {
            record = records.get(resourceKey);
            if (record.state == DownloadState.CANCELLED ||
                    record.state == DownloadState.FAILURE ||
                    record.state == DownloadState.NONE) {
                record.state = DownloadState.PENDING;
                record.downloaded = 0;
                record.downloadedPercent = 0;
                record.photo = photo;
                updateState(resourceKey, record.state, 0, 0);
            }
        } else {
            record = new DownloadRecord();
            record.state = DownloadState.PENDING;
            record.downloaded = 0;
            record.downloadedPercent = 0;
            record.photo = photo;
            records.put(resourceKey, record);
            updateState(resourceKey, record.state, 0, 0);
        }

        requestDownload(resourceKey, record,
                getDownloadImageFile(resourceKey),
                photo.getFullLocation());
    }

    public void requestDownload(TLLocalVideo video) {
        final String resourceKey = getVideoKey(video);
        if (downloadPersistence.isDownloaded(resourceKey))
            return;

        final DownloadRecord record;
        if (records.containsKey(resourceKey)) {
            record = records.get(resourceKey);
            if (record.state == DownloadState.CANCELLED ||
                    record.state == DownloadState.FAILURE ||
                    record.state == DownloadState.NONE) {
                record.state = DownloadState.PENDING;
                record.downloaded = 0;
                record.downloadedPercent = 0;
                record.video = video;
                updateState(resourceKey, record.state, 0, 0);
            }
        } else {
            record = new DownloadRecord();
            record.state = DownloadState.PENDING;
            record.downloaded = 0;
            record.downloadedPercent = 0;
            record.video = video;
            records.put(resourceKey, record);
            updateState(resourceKey, record.state, 0, 0);
        }

        requestDownload(resourceKey, record,
                getDownloadVideoFile(resourceKey),
                video.getVideoLocation());
    }

    private void requestDownload(final String key, final DownloadRecord record, final String fileName, final TLAbsLocalFileLocation fileLocation) {
        if (enqueued.contains(key)) {
            return;
        }
        enqueued.add(key);

        final TLAbsInputFileLocation location;
        final int dcId;
        final int size;
        if (fileLocation instanceof TLLocalFileVideoLocation) {
            dcId = ((TLLocalFileVideoLocation) fileLocation).getDcId();
            size = ((TLLocalFileVideoLocation) fileLocation).getSize();
            location = new TLInputVideoFileLocation(((TLLocalFileVideoLocation) fileLocation).getVideoId(), ((TLLocalFileVideoLocation) fileLocation).getAccessHash());
        } else if (fileLocation instanceof TLLocalFileLocation) {
            dcId = ((TLLocalFileLocation) fileLocation).getDcId();
            size = ((TLLocalFileLocation) fileLocation).getSize();
            location = new TLInputFileLocation(((TLLocalFileLocation) fileLocation).getVolumeId(), ((TLLocalFileLocation) fileLocation).getLocalId(), ((TLLocalFileLocation) fileLocation).getSecret());
        } else if (fileLocation instanceof TLLocalEncryptedFileLocation) {
            dcId = ((TLLocalEncryptedFileLocation) fileLocation).getDcId();
            size = ((TLLocalEncryptedFileLocation) fileLocation).getSize();
            location = new TLInputEncryptedFileLocation(((TLLocalEncryptedFileLocation) fileLocation).getId(), ((TLLocalEncryptedFileLocation) fileLocation).getAccessHash());
        } else if (fileLocation instanceof TLLocalFileDocument) {
            dcId = ((TLLocalFileDocument) fileLocation).getDcId();
            size = ((TLLocalFileDocument) fileLocation).getSize();
            location = new TLInputDocumentFileLocation(((TLLocalFileDocument) fileLocation).getId(), ((TLLocalFileDocument) fileLocation).getAccessHash());
        } else {
            return;
        }

        service.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
                    updateState(key, DownloadState.IN_PROGRESS, 0, 0);

                    String destFileName = getDownloadTempFile();

                    record.downloadTask = application.getApi().getDownloader().requestTask(dcId, location, size, destFileName, new org.telegram.api.engine.file.DownloadListener() {
                        @Override
                        public void onPartDownloaded(int percent, int downloadedSize) {
                            if (record.state != DownloadState.CANCELLED) {
                                updateState(key, DownloadState.IN_PROGRESS, percent, downloadedSize);
                            }
                        }

                        @Override
                        public void onDownloaded() {

                        }

                        @Override
                        public void onFailed() {

                        }
                    });

                    Logger.d(TAG, "Waiting for task #" + record.downloadTask);

                    application.getApi().getDownloader().waitForTask(record.downloadTask);

                    Logger.d(TAG, "Waiting for task ended #" + record.downloadTask);

                    if (application.getApi().getDownloader().getTaskState(record.downloadTask) != Downloader.FILE_COMPLETED) {
                        updateState(key, DownloadState.FAILURE, 0, 0);
                        return;
                    }

                    try {
                        if (fileLocation instanceof TLLocalEncryptedFileLocation) {
                            byte[] iv = ((TLLocalEncryptedFileLocation) fileLocation).getIv();
                            byte[] key = ((TLLocalEncryptedFileLocation) fileLocation).getKey();
                            CryptoUtils.AES256IGEDecrypt(new File(destFileName), new File(fileName), iv, key);
                        } else {
                            IOUtils.copy(new File(destFileName), new File(fileName));
                        }
                    } catch (IOException e) {
                        Logger.t(TAG, e);
                        updateState(key, DownloadState.FAILURE, 0, 0);
                        return;
                    }

                    if (record.photo != null) {
                        Logger.d(TAG, "@" + key + " = building thumb");
                        try {
                            saveDownloadImagePreview(key, fileName);
                        } catch (IOException e) {
                            e.printStackTrace();
                            Logger.t(TAG, e);
                            updateState(key, DownloadState.FAILURE, 100, size);
                            return;
                        }

                        try {
                            Logger.d(TAG, "@" + key + " = writing to gallery");
                            if (application.getUserSettings().isSaveToGalleryEnabled()) {
                                writeToGallery(fileName, key + ".jpg");
                            }
                        } catch (Exception e) {
                            Logger.t(TAG, e);
                        }
                    } else if (record.video != null) {
                        Logger.d(TAG, "@" + key + " = writing to gallery");
                        if (application.getUserSettings().isSaveToGalleryEnabled()) {
                            try {
                                writeToGallery(fileName, key + ".mp4");
                            } catch (Exception e) {
                                Logger.t(TAG, e);
                            }
                        }
                    } else if (record.doc != null) {

                    }
                    Logger.d(TAG, "@" + key + " = mark as downloaded");
                    downloadPersistence.markDownloaded(key);
                    updateState(key, DownloadState.COMPLETED, 100, size);
                } finally {
                    enqueued.remove(key);
                }
            }
        });
    }

    public void abortDownload(String key) {
        DownloadRecord record = records.get(key);
        if (record != null) {
            updateState(key, DownloadState.CANCELLED, record.downloadedPercent, record.downloaded);
            application.getApi().getDownloader().cancelTask(record.downloadTask);
        }
    }

    public void saveDownloadImage(String key, String fileName) throws IOException {
        IOUtils.copy(new File(fileName), new File(getDownloadImageFile(key)));
        downloadPersistence.markDownloaded(key);
    }

    public void saveDownloadImagePreview(String key, String fileName) throws IOException {
        Bitmap thumb = ImageUtils.getBitmapThumb(fileName, SMALL_THUMB_SIDE, SMALL_THUMB_SIDE);
        if (thumb == null) {
            updateState(key, DownloadState.FAILURE, 0, 0);
            return;
        }

        Logger.d(TAG, "@" + key + " = saving thumb");
        FileOutputStream outputStream = null;
        try {
            outputStream = new FileOutputStream(getPhotoThumbSmallFileName(key));
            thumb.compress(Bitmap.CompressFormat.JPEG, 87, outputStream);
        } finally {
            if (outputStream != null) {
                outputStream.close();
            }
        }
    }

    public void saveDownloadVideo(String key, String fileName) throws IOException {
        IOUtils.copy(new File(fileName), new File(getDownloadVideoFile(key)));
        downloadPersistence.markDownloaded(key);
    }

    public void saveDownloadDoc(String key, String fileName) throws IOException {
        IOUtils.copy(new File(fileName), new File(getDownloadDocFile(key)));
        downloadPersistence.markDownloaded(key);
    }

    public void writeToGallery(String fileName, String destName) throws IOException {
        String directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getAbsolutePath()
                + "/Telegram/";
        new File(directory).mkdirs();
        String file = directory + destName;
        File dest = new File(file);
        if (dest.exists()) {
            return;
        }
        IOUtils.copy(new File(fileName), dest);
        MediaScannerConnection.scanFile(application, new String[]{file}, null, null);
    }

    public String getVideoFileName(String key) {
        return getDownloadVideoFile(key);
    }

    public String getPhotoFileName(String key) {
        return getDownloadImageFile(key);
    }

    public String getDocFileName(String key) {
        return getDownloadDocFile(key);
    }

    public String getPhotoThumbSmallFileName(String key) {
        return getDownloadImageThumbFile(key);
    }

    private String getDownloadVideoFile(String key) {
        return application.getExternalCacheDir().getAbsolutePath() + "/video_" + key + ".mp4";
    }

    private String getDownloadImageFile(String key) {
        return application.getExternalCacheDir().getAbsolutePath() + "/image_" + key + ".jpg";
    }

    private String getDownloadDocFile(String key) {
        return application.getExternalCacheDir().getAbsolutePath() + "/doc_" + key;
    }

    private String getDownloadImageThumbFile(String key) {
        return application.getExternalCacheDir().getAbsolutePath() + "/image_thumb_" + key + ".jpg";
    }

    private String getDownloadTempFile() {
        return application.getExternalCacheDir().getAbsolutePath() + "/download_" + Entropy.generateRandomId() + ".bin";
    }


    private void updateState(final String key, final DownloadState state, final int percent, int bytes) {
        Logger.d(TAG, "State: " + key + " = " + state + " " + percent + "%");

        DownloadRecord record = records.get(key);
        record.state = state;
        record.downloadedPercent = percent;
        record.downloaded = bytes;

        notifier.notify(key, new Runnable() {
            @Override
            public void run() {
                for (WeakReference<DownloadListener> ref : listeners) {
                    DownloadListener listener = ref.get();
                    if (listener != null) {
                        listener.onStateChanged(key, state, percent);
                    }
                }
            }
        });
    }
}