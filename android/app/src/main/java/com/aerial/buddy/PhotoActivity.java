package com.aerial.buddy;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 相册直投 / 换图标：选一张图，推到电视。
 *
 * <p>直投走电视端助手 /image（会按屏幕等比缩放居中）；换图标走 Aerial api/icon/pick。
 */
public class PhotoActivity extends Activity {

    private static final int PICK = 91;

    private Theme theme;
    private String ip;
    private String url;
    private String mode;
    private TextView status;
    private ImageView preview;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ip = getIntent().getStringExtra("ip");
        url = getIntent().getStringExtra("url");
        mode = getIntent().getStringExtra("mode");
        theme = Theme.of(getIntent().getIntExtra("theme", Theme.AURORA));

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(theme.light ? Color.parseColor("#F2F4FA") : Color.parseColor("#0E1117"));
        int pad = dp(18);
        page.setPadding(pad, dp(14), pad, dp(10));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = new TextView(this);
        back.setText("‹");
        back.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        back.setTextColor(theme.text);
        back.setPadding(0, 0, dp(10), 0);
        back.setOnClickListener(v -> finish());
        top.addView(back);
        TextView title = new TextView(this);
        title.setText("icon".equals(mode) ? "换图标" : "相册直投");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setTextColor(theme.text);
        top.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        page.addView(top);

        preview = new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        GradientDrawable pbg = new GradientDrawable();
        pbg.setCornerRadius(dp(18));
        pbg.setColor(theme.light ? Color.WHITE : theme.card);
        pbg.setStroke(dp(1), theme.cardStroke);
        preview.setBackground(pbg);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        plp.topMargin = dp(12);
        page.addView(preview, plp);

        Button pick = new Button(this);
        pick.setText("选择图片");
        pick.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.setType("image/*");
            startActivityForResult(Intent.createChooser(i, "选择图片"), PICK);
        });
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        blp.topMargin = dp(10);
        page.addView(pick, blp);

        status = new TextView(this);
        status.setText("icon".equals(mode)
                ? "选一张正方形图片作为电视应用图标（Aerial 会弹选择器）"
                : "选一张图片，电视上会按屏幕大小居中显示 4 秒");
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        status.setTextColor(theme.dim);
        status.setPadding(0, dp(8), 0, 0);
        page.addView(status);

        setContentView(page);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != PICK || res != RESULT_OK || data == null || data.getData() == null) return;
        final Uri uri = data.getData();
        new Thread(() -> {
            try {
                InputStream in = getContentResolver().openInputStream(uri);
                final Bitmap bmp = BitmapFactory.decodeStream(in);
                if (in != null) in.close();
                if (bmp == null) {
                    ui("图片读取失败");
                    return;
                }
                runOnUiThread(() -> preview.setImageBitmap(bmp));
                if ("icon".equals(mode)) {
                    uploadIcon(bmp);
                } else {
                    cast(bmp);
                }
            } catch (Exception e) {
                ui("处理失败：" + e.getMessage());
            }
        }, "photo").start();
    }

    private void cast(Bitmap bmp) {
        try {
            int w = Math.min(bmp.getWidth(), 1280);
            Bitmap small = Bitmap.createScaledBitmap(
                    bmp, w, (int) (bmp.getHeight() * (w / (float) bmp.getWidth())), true);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            small.compress(Bitmap.CompressFormat.JPEG, 88, bos);
            byte[] jpeg = bos.toByteArray();
            HttpURLConnection c = (HttpURLConnection) new URL("http://" + ip + ":8769/image").openConnection();
            c.setConnectTimeout(3000);
            c.setReadTimeout(10000);
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "image/jpeg");
            c.setFixedLengthStreamingMode(jpeg.length);
            OutputStream out = c.getOutputStream();
            out.write(jpeg);
            out.flush();
            out.close();
            int code = c.getResponseCode();
            c.disconnect();
            ui(code == 200 ? "已投到电视（居中显示 4 秒）" : ("电视端返回 " + code + "，请确认助手 0.7.3+"));
        } catch (Exception e) {
            ui("投送失败：" + e.getMessage());
        }
    }

    private void uploadIcon(Bitmap bmp) {
        try {
            int side = Math.min(bmp.getWidth(), bmp.getHeight());
            int x = (bmp.getWidth() - side) / 2;
            int y = (bmp.getHeight() - side) / 2;
            Bitmap square = Bitmap.createBitmap(bmp, x, y, side, side);
            Bitmap scaled = Bitmap.createScaledBitmap(square, 256, 256, true);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            scaled.compress(Bitmap.CompressFormat.PNG, 100, bos);
            byte[] png = bos.toByteArray();

            String boundary = "----AerialBuddy" + System.currentTimeMillis();
            HttpURLConnection c = (HttpURLConnection) new URL(url + "api/icon/pick").openConnection();
            c.setConnectTimeout(4000);
            c.setReadTimeout(12000);
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            OutputStream out = c.getOutputStream();
            out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"icon.png\"\r\n"
                    + "Content-Type: image/png\r\n\r\n").getBytes("UTF-8"));
            out.write(png);
            out.write(("\r\n--" + boundary + "--\r\n").getBytes("UTF-8"));
            out.flush();
            out.close();
            int code = c.getResponseCode();
            c.disconnect();
            if (code == 200) {
                ui("图标已上传，电视上会弹出选择，选好后即生效");
            } else {
                ui("上传返回 " + code + "：该版本 Aerial 可能不支持自定义图标，"
                        + "可到「管理」页在电视网页里换");
            }
        } catch (Exception e) {
            ui("上传失败：" + e.getMessage());
        }
    }

    private void ui(final String text) {
        runOnUiThread(() -> {
            status.setText(text);
            Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
        });
    }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }
}
