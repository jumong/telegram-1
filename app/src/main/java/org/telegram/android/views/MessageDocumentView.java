package org.telegram.android.views;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import org.telegram.android.R;
import org.telegram.android.core.background.MediaSender;
import org.telegram.android.core.background.SenderListener;
import org.telegram.android.core.model.MessageState;
import org.telegram.android.core.model.media.TLLocalDocument;
import org.telegram.android.core.model.media.TLLocalEncryptedFileLocation;
import org.telegram.android.core.model.media.TLLocalFileDocument;
import org.telegram.android.core.model.media.TLUploadingDocument;
import org.telegram.android.core.wireframes.MessageWireframe;
import org.telegram.android.media.DownloadListener;
import org.telegram.android.media.DownloadManager;
import org.telegram.android.media.DownloadState;
import org.telegram.android.ui.FontController;
import org.telegram.android.ui.TextUtil;

/**
 * Created by ex3ndr on 15.12.13.
 */
public class MessageDocumentView extends BaseMsgView {

    private DownloadListener downloadListener;
    private SenderListener senderListener;

    private Paint progressPaint;
    private Paint progressBgPaint;
    private Paint downloadBgPaint;
    private Paint iconBgPaint;
    private TextPaint clockOutPaint;
    private TextPaint fileNamePaint;
    private TextPaint fileDeskPaint;
    private Paint clockIconPaint;
    private Drawable documentIconOut;
    private Drawable documentIconIn;
    private Drawable documentIcon;

    private TextPaint senderPaint;

    private Drawable statePending;
    private Drawable stateSent;
    private Drawable stateHalfCheck;
    private Drawable stateFailure;

    private int timeWidth;
    private boolean showState;
    private String date;
    private int state;
    private int prevState;
    private long stateChangeTime;

    private static final int COLOR_NORMAL = 0xff70B15C;
    private static final int COLOR_ERROR = 0xffDB4942;
    private static final int COLOR_IN = 0xffA1AAB3;

    private int downloadProgress;
    private int oldDownloadProgress;
    private long downloadStateTime;
    private String downloadString;
    private String key;

    private int databaseId;

    private String fileName;
    private String fileNameMeasured;
    private String fileSize;

    private boolean isDownloaded;

    private int contentW;
    private int contentH;

    public MessageDocumentView(Context context) {
        super(context);
    }

    public MessageDocumentView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MessageDocumentView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    protected void init() {
        super.init();

        documentIconOut = getResources().getDrawable(R.drawable.st_bubble_ic_doc_out);
        documentIconIn = getResources().getDrawable(R.drawable.st_bubble_ic_doc_in);

        progressPaint = new Paint();
        progressPaint.setStyle(Paint.Style.FILL);
        progressPaint.setColor(0xFF669dd8);

        downloadBgPaint = new Paint();
        downloadBgPaint.setStyle(Paint.Style.FILL);
        downloadBgPaint.setColor(0xB6000000);

        progressBgPaint = new Paint();
        progressBgPaint.setStyle(Paint.Style.FILL);
        progressBgPaint.setColor(0x0F669dd8);

        iconBgPaint = new Paint();
        iconBgPaint.setStyle(Paint.Style.FILL);
        iconBgPaint.setColor(0xffdff4bd);

        if (FontController.USE_SUBPIXEL) {
            clockOutPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        } else {
            clockOutPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        }
        clockOutPaint.setTypeface(FontController.loadTypeface(getContext(), "regular"));
        clockOutPaint.setTextSize(getSp(12f));
        clockOutPaint.setColor(0xff70B15C);

        if (FontController.USE_SUBPIXEL) {
            fileNamePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        } else {
            fileNamePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        }
        fileNamePaint.setTypeface(FontController.loadTypeface(getContext(), "regular"));
        fileNamePaint.setTextSize(getSp(18f));
        fileNamePaint.setColor(0xff000000);

        if (FontController.USE_SUBPIXEL) {
            fileDeskPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        } else {
            fileDeskPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        }
        fileDeskPaint.setTypeface(FontController.loadTypeface(getContext(), "regular"));
        fileDeskPaint.setTextSize(getSp(14f));
        fileDeskPaint.setColor(0xff000000);


        clockIconPaint = new Paint();
        clockIconPaint.setStyle(Paint.Style.STROKE);
        clockIconPaint.setColor(0xff12C000);
        clockIconPaint.setStrokeWidth(getPx(1));
        clockIconPaint.setAntiAlias(true);
        clockIconPaint.setFlags(Paint.ANTI_ALIAS_FLAG);

        if (FontController.USE_SUBPIXEL) {
            senderPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        } else {
            senderPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        }
        senderPaint.setTypeface(FontController.loadTypeface(application, "regular"));
        senderPaint.setTextSize(getSp(16));
        senderPaint.setColor(0xff000000);

        statePending = getResources().getDrawable(R.drawable.st_bubble_ic_clock);
        stateSent = getResources().getDrawable(R.drawable.st_bubble_ic_check);
        stateHalfCheck = getResources().getDrawable(R.drawable.st_bubble_ic_halfcheck);
        stateFailure = getResources().getDrawable(R.drawable.st_bubble_ic_warning);

        downloadListener = new DownloadListener() {
            @Override
            public void onStateChanged(final String _key, final DownloadState state, final int percent) {
                if (!_key.equals(key))
                    return;

                if (downloadProgress != percent) {
                    oldDownloadProgress = downloadProgress;
                    downloadProgress = percent;
                    downloadStateTime = SystemClock.uptimeMillis();
                }
                switch (state) {
                    case CANCELLED:
                        downloadString = getResources().getString(R.string.st_bubble_media_cancelled);
                        break;
                    case FAILURE:
                        downloadString = getResources().getString(R.string.st_bubble_media_try_again);
                        break;
                    case NONE:
                        downloadString = getResources().getString(R.string.st_bubble_media_download);
                        break;
                    case IN_PROGRESS:
                    case PENDING:
                        downloadString = getResources().getString(R.string.st_bubble_media_in_progress);
                        break;
                    case COMPLETED:
                        downloadString = null;
                        isDownloaded = true;
                        rebind();
                        break;
                }
                postInvalidate();
            }
        };
        application.getDownloadManager().registerListener(downloadListener);

        senderListener = new SenderListener() {
            @Override
            public void onUploadStateChanged(int localId, MediaSender.SendState state) {
                if (databaseId != localId)
                    return;

                if (downloadProgress != state.getUploadProgress()) {
                    oldDownloadProgress = downloadProgress;
                    downloadProgress = state.getUploadProgress();
                    downloadStateTime = SystemClock.uptimeMillis();
                }

                if (state.isCanceled()) {
                    downloadString = getResources().getString(R.string.st_bubble_media_cancelled);
                } else if (state.isUploaded()) {
                    downloadString = null;
                } else if (state.isSent()) {
                    downloadString = null;
                    isDownloaded = true;
                    // rebind();
                } else {
                    downloadString = getResources().getString(R.string.st_bubble_media_in_progress);
                }
                postInvalidate();
            }
        };
        application.getMediaSender().registerListener(senderListener);
    }

    @Override
    protected void bindNewView(MessageWireframe message) {
        this.state = message.message.getState();
        this.prevState = -1;
        databaseId = message.databaseId;
        downloadProgress = 0;

        if (message.message.isOut()) {
            iconBgPaint.setColor(0xffdef3bd);
            documentIcon = documentIconOut;
            fileDeskPaint.setColor(0xff97bb7c);
        } else {
            iconBgPaint.setColor(0xfff1f4f6);
            documentIcon = documentIconIn;
            fileDeskPaint.setColor(0xffafb7c3);
        }

        requestLayout();
    }

    @Override
    protected void bindCommon(MessageWireframe message) {
        if (this.state != message.message.getState()) {
            this.prevState = this.state;
            this.state = message.message.getState();
            this.stateChangeTime = SystemClock.uptimeMillis();
        }
        if (message.message.getExtras() instanceof TLUploadingDocument) {
            TLUploadingDocument doc = (TLUploadingDocument) message.message.getExtras();
            fileName = doc.getFileName();
            int index = fileName.lastIndexOf('.');
            if (index > 0) {
                String ext = fileName.substring(index + 1).trim();
                if (ext.length() > 4) {
                    ext = ext.substring(0, 3) + "\u2026";
                }
                fileSize = TextUtil.formatFileSize(doc.getFileSize()) + " " + ext.toUpperCase();
            } else {
                fileSize = TextUtil.formatFileSize(doc.getFileSize());
            }
            isDownloaded = false;

            MediaSender.SendState state = application.getMediaSender().getSendState(databaseId);
            if (state != null) {
                if (downloadProgress != state.getUploadProgress()) {
                    oldDownloadProgress = downloadProgress;
                    downloadProgress = state.getUploadProgress();
                    downloadStateTime = SystemClock.uptimeMillis();
                }
                if (state.isCanceled()) {
                    downloadString = getResources().getString(R.string.st_bubble_media_cancelled);
                } else if (state.isUploaded()) {
                    downloadString = null;
                } else {
                    downloadString = getResources().getString(R.string.st_bubble_media_in_progress);
                }
            }
        } else if (message.message.getExtras() instanceof TLLocalDocument) {
            TLLocalDocument doc = (TLLocalDocument) message.message.getExtras();
            fileName = doc.getFileName();
            int index = fileName.lastIndexOf('.');
            if (index > 0) {
                String ext = fileName.substring(index + 1).trim();
                if (ext.length() > 4) {
                    ext = ext.substring(0, 3) + "\u2026";
                }
                if (doc.getFileLocation() instanceof TLLocalFileDocument) {
                    fileSize = TextUtil.formatFileSize(((TLLocalFileDocument) doc.getFileLocation()).getSize()) + " " + ext.toUpperCase();
                } else if (doc.getFileLocation() instanceof TLLocalEncryptedFileLocation) {
                    fileSize = TextUtil.formatFileSize(((TLLocalEncryptedFileLocation) doc.getFileLocation()).getSize()) + " " + ext.toUpperCase();
                } else {
                    fileSize = ext.toUpperCase();
                }

            } else {
                if (doc.getFileLocation() instanceof TLLocalFileDocument) {
                    fileSize = TextUtil.formatFileSize(((TLLocalFileDocument) doc.getFileLocation()).getSize());
                } else if (doc.getFileLocation() instanceof TLLocalEncryptedFileLocation) {
                    fileSize = TextUtil.formatFileSize(((TLLocalEncryptedFileLocation) doc.getFileLocation()).getSize());
                } else {
                    fileSize = "";
                }
            }
            key = DownloadManager.getDocumentKey(doc);

            if (application.getDownloadManager().getState(key) == DownloadState.COMPLETED) {
                isDownloaded = true;
            } else {
                if (downloadProgress != application.getDownloadManager().getDownloadProgress(key)) {
                    oldDownloadProgress = downloadProgress;
                    downloadProgress = application.getDownloadManager().getDownloadProgress(key);
                    downloadStateTime = SystemClock.uptimeMillis();
                }
            }
        } else {
            fileSize = "";
            fileName = "unknown";
        }

        if (message.message.isOut()) {
            downloadBgPaint.setColor(0xffe6ffd1);
        } else {
            downloadBgPaint.setColor(Color.WHITE);
        }

        this.date = org.telegram.android.ui.TextUtil.formatTime(message.message.getDate(), getContext());
        this.showState = message.message.isOut();
    }

    @Override
    protected void measureBubbleContent(int width) {
        contentW = getPx(220);
        contentH = getPx(56);
        timeWidth = (int) clockOutPaint.measureText(date) + getPx((showState ? 23 : 0) + 6);
        fileNameMeasured = TextUtils.ellipsize(fileName, fileNamePaint, getPx(160), TextUtils.TruncateAt.END).toString();
        setBubbleMeasuredContent(contentW, contentH);
    }

    @Override
    protected int getOutPressedBubbleResource() {
        return R.drawable.st_bubble_out_media_overlay;
    }

    @Override
    protected int getInPressedBubbleResource() {
        return R.drawable.st_bubble_in_media_overlay;
    }

    @Override
    protected int getInBubbleResource() {
        return R.drawable.st_bubble_in_media_normal;
    }

    @Override
    protected int getOutBubbleResource() {
        return R.drawable.st_bubble_out_media_normal;
    }

    private Drawable getStateDrawable(int state) {
        switch (state) {
            default:
            case MessageState.SENT:
                return stateSent;
            case MessageState.READED:
                return stateHalfCheck;
            case MessageState.FAILURE:
                return stateFailure;
            case MessageState.PENDING:
                return statePending;
        }
    }

    @Override
    protected boolean drawBubble(Canvas canvas) {

        boolean isAnimated = false;

        canvas.drawRect(new RectF(0, 0, contentW, contentH), downloadBgPaint);

        canvas.drawText(fileNameMeasured, getPx(60), getPx(26), fileNamePaint);
        canvas.drawText(fileSize, getPx(60), getPx(46), fileDeskPaint);

        canvas.drawRect(new Rect(getPx(4), getPx(4), getPx(4 + 48), getPx(4 + 48)), iconBgPaint);

        documentIcon.setBounds(new Rect(getPx(12), getPx(12), getPx(12 + 32), getPx(12 + 32)));
        documentIcon.draw(canvas);

        int layoutHeight = getPx(56);
        int layoutWidth = contentW - getPx(4);

        if (showState) {
            if (state == MessageState.PENDING) {
                canvas.save();
                canvas.translate(layoutWidth - getPx(12), layoutHeight - getPx(12) - getPx(3));
                canvas.drawCircle(getPx(6), getPx(6), getPx(6), clockIconPaint);
                double time = (System.currentTimeMillis() / 10.0) % (12 * 60);
                double angle = (time / (6 * 60)) * Math.PI;

                int x = (int) (Math.sin(-angle) * getPx(4));
                int y = (int) (Math.cos(-angle) * getPx(4));
                canvas.drawLine(getPx(6), getPx(6), getPx(6) + x, getPx(6) + y, clockIconPaint);

                x = (int) (Math.sin(-angle * 12) * getPx(5));
                y = (int) (Math.cos(-angle * 12) * getPx(5));
                canvas.drawLine(getPx(6), getPx(6), getPx(6) + x, getPx(6) + y, clockIconPaint);

                canvas.restore();

                clockOutPaint.setColor(COLOR_NORMAL);

                isAnimated = true;
            } else if (state == MessageState.READED && prevState == MessageState.SENT && (SystemClock.uptimeMillis() - stateChangeTime < STATE_ANIMATION_TIME)) {
                long animationTime = SystemClock.uptimeMillis() - stateChangeTime;
                float progress = easeStateFade(animationTime / (float) STATE_ANIMATION_TIME);
                int offset = (int) (getPx(5) * progress);
                int alphaNew = (int) (progress * 255);

                bounds(stateSent, layoutWidth - stateSent.getIntrinsicWidth() - offset,
                        layoutHeight - stateSent.getIntrinsicHeight() - getPx(3));
                stateSent.setAlpha(255);
                stateSent.draw(canvas);

                bounds(stateHalfCheck, layoutWidth - stateHalfCheck.getIntrinsicWidth() + getPx(5) - offset,
                        layoutHeight - stateHalfCheck.getIntrinsicHeight() - getPx(3));
                stateHalfCheck.setAlpha(alphaNew);
                stateHalfCheck.draw(canvas);

                clockOutPaint.setColor(COLOR_NORMAL);

                isAnimated = true;
            } else {
                Drawable stateDrawable = getStateDrawable(state);

                bounds(stateDrawable, layoutWidth - stateDrawable.getIntrinsicWidth(), layoutHeight - stateDrawable.getIntrinsicHeight() - getPx(3));
                stateDrawable.setAlpha(255);
                stateDrawable.draw(canvas);

                if (state == MessageState.READED) {
                    bounds(stateSent, layoutWidth - stateSent.getIntrinsicWidth() - getPx(5),
                            layoutHeight - stateDrawable.getIntrinsicHeight() - getPx(3));
                    stateSent.setAlpha(255);
                    stateSent.draw(canvas);
                }

                if (state == MessageState.FAILURE) {
                    clockOutPaint.setColor(COLOR_ERROR);
                } else {
                    clockOutPaint.setColor(COLOR_NORMAL);
                }
            }
        } else {
            clockOutPaint.setColor(COLOR_IN);
        }

        canvas.drawText(date, layoutWidth - timeWidth + getPx(6), getPx(52), clockOutPaint);

        if (!isDownloaded) {
            int visibleProgress = (downloadProgress * (contentW + getPx(6))) / 100;
            canvas.drawRect(new RectF(-getPx(3), -getPx(3), visibleProgress - getPx(3), contentH), progressBgPaint);
            canvas.drawRect(new RectF(-getPx(3), contentH, visibleProgress - getPx(3), contentH + getPx(3)), progressPaint);
        }

        return isAnimated;
    }
}
