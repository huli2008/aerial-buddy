package com.aerial.intercom;

import android.app.Activity;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;

/**
 * Invisible activity that grabs the MediaProjection token and starts {@link MirrorService}.
 *
 * <p>Opening it directly (rather than from MainActivity) is what lets the TV begin mirroring from the
 * boot receiver without showing any UI. The system consent dialog is answered by
 * {@link MirrorAutoGrant}, so the whole path stays silent.
 */
public class ConsentActivity extends Activity {

    private static final int REQ = 77;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        try {
            MediaProjectionManager mgr = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            startActivityForResult(mgr.createScreenCaptureIntent(), REQ);
        } catch (Exception e) {
            finish();
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ && res == RESULT_OK && data != null) {
            MirrorService.start(this, res, data);
        }
        finish();
    }
}
