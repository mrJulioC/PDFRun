package com.solumetals.pdfrun;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.IntentSender;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Size;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.mlkit.vision.documentscanner.GmsDocumentScanner;
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final int PICK_IMAGES = 10;
    private static final int PICK_TEXT = 11;
    private static final int SCAN_DOCUMENT = 12;

    private final List<PageItem> pages = new ArrayList<>();
    private final int turquoise = Color.rgb(36, 215, 176);
    private final int background = Color.rgb(6, 22, 26);
    private final int panel = Color.rgb(12, 37, 42);

    private LinearLayout pageList;
    private LinearLayout documentPanel;
    private TextView counter;
    private EditText fileName;
    private Button saveButton;
    private Button shareButton;
    private Button scanButton;
    private ProgressBar scannerProgress;
    private Uri lastPdf;

    private static class PageItem {
        Uri uri;
        String text;
        String name;
        boolean image;

        static PageItem image(Uri uri, String name) {
            PageItem item = new PageItem();
            item.uri = uri;
            item.name = name;
            item.image = true;
            return item;
        }

        static PageItem text(String text, String name) {
            PageItem item = new PageItem();
            item.text = text;
            item.name = name;
            return item;
        }
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private TextView label(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private GradientDrawable roundedBackground(int fill, int stroke, int radius, int strokeWidth) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(dp(radius));
        if (strokeWidth > 0) shape.setStroke(dp(strokeWidth), stroke);
        return shape;
    }

    private Button button(String value, boolean primary) {
        Button view = new Button(this);
        view.setText(value);
        view.setTextSize(16);
        view.setAllCaps(false);
        view.setTextColor(primary ? Color.rgb(3, 33, 27) : Color.WHITE);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setLetterSpacing(0.03f);
        view.setBackground(roundedBackground(
                primary ? turquoise : Color.rgb(9, 31, 36),
                turquoise, 18, primary ? 0 : 1));
        view.setMinHeight(dp(56));
        view.setStateListAnimator(null);
        return view;
    }

    private void buildUi() {
        getWindow().setStatusBarColor(background);
        getWindow().setNavigationBarColor(background);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(background);

        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setGravity(Gravity.CENTER_HORIZONTAL);
        body.setPadding(dp(18), dp(20), dp(18), dp(30));
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.mipmap.ic_launcher);
        logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        body.addView(logo, new LinearLayout.LayoutParams(dp(112), dp(112)));

        TextView title = label("PDF Run", 30, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setLetterSpacing(0.09f);
        title.setGravity(Gravity.CENTER);
        body.addView(title, new LinearLayout.LayoutParams(-1, dp(52)));

        TextView subtitle = label("Escanea o carga tus páginas", 15, Color.rgb(161, 190, 187));
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(-1, dp(34));
        subtitleParams.setMargins(0, 0, 0, dp(12));
        body.addView(subtitle, subtitleParams);

        Button loadButton = button("Cargar archivo", true);
        scanButton = button("Escanear documento", false);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(-1, dp(60));
        actionParams.setMargins(dp(18), dp(5), dp(18), dp(5));
        body.addView(loadButton, actionParams);
        LinearLayout.LayoutParams scanParams = new LinearLayout.LayoutParams(-1, dp(60));
        scanParams.setMargins(dp(18), dp(5), dp(18), dp(5));
        body.addView(scanButton, scanParams);

        scannerProgress = new ProgressBar(this);
        scannerProgress.setIndeterminate(true);
        scannerProgress.setVisibility(View.GONE);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(dp(34), dp(34));
        progressParams.setMargins(0, dp(8), 0, 0);
        body.addView(scannerProgress, progressParams);

        loadButton.setOnClickListener(view -> chooseFileType());
        scanButton.setOnClickListener(view -> openDocumentScanner());

        documentPanel = new LinearLayout(this);
        documentPanel.setOrientation(LinearLayout.VERTICAL);
        documentPanel.setVisibility(View.GONE);
        body.addView(documentPanel, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.setPadding(dp(4), dp(22), dp(4), dp(10));
        TextView documentTitle = label("Documento", 20, Color.WHITE);
        documentTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        heading.addView(documentTitle, new LinearLayout.LayoutParams(0, -2, 1));
        counter = label("0 páginas", 14, Color.rgb(155, 185, 182));
        heading.addView(counter);
        documentPanel.addView(heading);

        pageList = new LinearLayout(this);
        pageList.setOrientation(LinearLayout.VERTICAL);
        documentPanel.addView(pageList);

        fileName = new EditText(this);
        fileName.setText("PDF-Run");
        fileName.setHint("Nombre del PDF");
        fileName.setSingleLine(true);
        fileName.setTextColor(Color.WHITE);
        fileName.setHintTextColor(Color.GRAY);
        fileName.setBackground(roundedBackground(panel, Color.rgb(28, 91, 91), 16, 1));
        fileName.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(-1, dp(56));
        nameParams.setMargins(0, dp(8), 0, dp(10));
        documentPanel.addView(fileName, nameParams);

        saveButton = button("Guardar PDF", true);
        saveButton.setOnClickListener(view -> savePdf());
        documentPanel.addView(saveButton, new LinearLayout.LayoutParams(-1, dp(60)));

        shareButton = button("Compartir PDF", false);
        shareButton.setVisibility(View.GONE);
        shareButton.setOnClickListener(view -> sharePdf());
        LinearLayout.LayoutParams shareParams = new LinearLayout.LayoutParams(-1, dp(58));
        shareParams.setMargins(0, dp(9), 0, 0);
        documentPanel.addView(shareButton, shareParams);

        setContentView(root);
    }

    private void chooseFileType() {
        new AlertDialog.Builder(this)
                .setTitle("Cargar archivo")
                .setItems(new String[]{"Imágenes", "Archivo TXT"}, (dialog, which) -> {
                    if (which == 0) pickImages(); else pickText();
                })
                .show();
    }

    private void pickImages() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(intent, PICK_IMAGES);
    }

    private void pickText() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        startActivityForResult(intent, PICK_TEXT);
    }

    private void openDocumentScanner() {
        GmsDocumentScannerOptions options = new GmsDocumentScannerOptions.Builder()
                .setGalleryImportAllowed(true)
                .setPageLimit(50)
                .setResultFormats(
                        GmsDocumentScannerOptions.RESULT_FORMAT_JPEG,
                        GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                .build();
        GmsDocumentScanner scanner = GmsDocumentScanning.getClient(options);
        setScannerLoading(true);
        scanner.getStartScanIntent(this)
                .addOnSuccessListener(intentSender -> {
                    setScannerLoading(false);
                    try {
                        startIntentSenderForResult(intentSender, SCAN_DOCUMENT, null, 0, 0, 0);
                    } catch (IntentSender.SendIntentException error) {
                        showScannerError();
                    }
                })
                .addOnFailureListener(error -> {
                    setScannerLoading(false);
                    showScannerError();
                });
    }

    private void setScannerLoading(boolean loading) {
        scannerProgress.setVisibility(loading ? View.VISIBLE : View.GONE);
        scanButton.setEnabled(!loading);
        scanButton.setText(loading ? "Preparando escáner…" : "Escanear documento");
    }

    private void showScannerError() {
        setScannerLoading(false);
        Toast.makeText(this,
                "No se pudo abrir el escáner. Actualiza Servicios de Google Play e inténtalo de nuevo.",
                Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == SCAN_DOCUMENT) {
            setScannerLoading(false);
            if (resultCode != RESULT_OK) return;
            GmsDocumentScanningResult result =
                    GmsDocumentScanningResult.fromActivityResultIntent(data);
            if (result == null || result.getPages() == null || result.getPages().isEmpty()) {
                Toast.makeText(this, "El escaneo no devolvió páginas", Toast.LENGTH_LONG).show();
                return;
            }
            int pageNumber = pages.size() + 1;
            for (GmsDocumentScanningResult.Page page : result.getPages()) {
                pages.add(PageItem.image(page.getImageUri(), "Escaneo " + pageNumber++));
            }
            lastPdf = null;
            renderPages();
            Toast.makeText(this, result.getPages().size() + " página(s) agregada(s)", Toast.LENGTH_SHORT).show();
            return;
        }

        if (resultCode != RESULT_OK || data == null) return;
        if (requestCode == PICK_IMAGES) {
            if (data.getClipData() != null) {
                for (int index = 0; index < data.getClipData().getItemCount(); index++) {
                    Uri uri = data.getClipData().getItemAt(index).getUri();
                    keepReadPermission(uri, data.getFlags());
                    pages.add(PageItem.image(uri, "Imagen " + (pages.size() + 1)));
                }
            } else if (data.getData() != null) {
                Uri uri = data.getData();
                keepReadPermission(uri, data.getFlags());
                pages.add(PageItem.image(uri, "Imagen " + (pages.size() + 1)));
            }
            lastPdf = null;
            renderPages();
        } else if (requestCode == PICK_TEXT && data.getData() != null) {
            Uri uri = data.getData();
            keepReadPermission(uri, data.getFlags());
            try {
                pages.add(PageItem.text(readText(uri), "Archivo TXT"));
                lastPdf = null;
                renderPages();
            } catch (Exception error) {
                Toast.makeText(this, "No se pudo leer el archivo TXT", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void keepReadPermission(Uri uri, int flags) {
        try {
            getContentResolver().takePersistableUriPermission(
                    uri, flags & Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {
        }
    }

    private String readText(Uri uri) throws Exception {
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(getContentResolver().openInputStream(uri)))) {
            String line;
            while ((line = reader.readLine()) != null) result.append(line).append('\n');
        }
        return result.toString();
    }

    private void renderPages() {
        pageList.removeAllViews();
        for (int index = 0; index < pages.size(); index++) {
            final int position = index;
            PageItem item = pages.get(index);

            LinearLayout card = new LinearLayout(this);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(9), dp(8), dp(8), dp(8));
            card.setBackground(roundedBackground(panel, Color.rgb(22, 70, 72), 18, 1));
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, dp(88));
            cardParams.setMargins(0, 0, 0, dp(8));

            ImageView preview = new ImageView(this);
            preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
            preview.setBackground(roundedBackground(Color.rgb(7, 28, 32), turquoise, 12, 1));
            card.addView(preview, new LinearLayout.LayoutParams(dp(70), dp(70)));
            if (item.image) loadThumbnail(preview, item.uri);
            else preview.setImageResource(android.R.drawable.ic_menu_edit);

            TextView itemLabel = label((index + 1) + ". " + item.name, 16, Color.WHITE);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, -1, 1);
            labelParams.leftMargin = dp(12);
            card.addView(itemLabel, labelParams);

            Button deleteButton = button("Borrar", false);
            deleteButton.setTextColor(Color.rgb(255, 150, 156));
            deleteButton.setTextSize(13);
            deleteButton.setOnClickListener(view -> {
                pages.remove(position);
                lastPdf = null;
                renderPages();
            });
            card.addView(deleteButton, new LinearLayout.LayoutParams(dp(82), dp(50)));
            pageList.addView(card, cardParams);
        }

        boolean hasPages = !pages.isEmpty();
        documentPanel.setVisibility(hasPages ? View.VISIBLE : View.GONE);
        counter.setText(pages.size() + (pages.size() == 1 ? " página" : " páginas"));
        saveButton.setEnabled(hasPages);
        shareButton.setVisibility(lastPdf == null ? View.GONE : View.VISIBLE);
    }

    private void loadThumbnail(ImageView target, Uri uri) {
        new Thread(() -> {
            try {
                Bitmap thumbnail;
                if (Build.VERSION.SDK_INT >= 29) {
                    thumbnail = getContentResolver().loadThumbnail(uri, new Size(dp(70), dp(70)), null);
                } else {
                    Bitmap full = decodeBitmap(uri);
                    thumbnail = Bitmap.createScaledBitmap(full, dp(70), dp(70), true);
                    if (thumbnail != full) full.recycle();
                }
                runOnUiThread(() -> target.setImageBitmap(thumbnail));
            } catch (Exception error) {
                runOnUiThread(() -> target.setImageResource(android.R.drawable.ic_menu_report_image));
            }
        }).start();
    }

    private Bitmap decodeBitmap(Uri uri) throws Exception {
        if (Build.VERSION.SDK_INT >= 28) {
            return ImageDecoder.decodeBitmap(
                    ImageDecoder.createSource(getContentResolver(), uri),
                    (decoder, info, source) -> {
                        decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                        int longest = Math.max(info.getSize().getWidth(), info.getSize().getHeight());
                        if (longest > 2600) decoder.setTargetSampleSize((int) Math.ceil(longest / 2600.0));
                    });
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream first = getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(first, null, bounds);
        }
        int sample = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / sample > 2600) sample *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        try (InputStream second = getContentResolver().openInputStream(uri)) {
            Bitmap bitmap = BitmapFactory.decodeStream(second, null, options);
            if (bitmap == null) throw new Exception("Imagen no compatible");
            return bitmap;
        }
    }

    private void savePdf() {
        saveButton.setEnabled(false);
        Toast.makeText(this, "Creando PDF…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            Uri destination = null;
            try {
                PdfDocument pdf = buildPdf();
                destination = createPdfDestination();
                try (OutputStream output = getContentResolver().openOutputStream(destination)) {
                    if (output == null) throw new Exception("No se pudo abrir el archivo de destino");
                    pdf.writeTo(output);
                } finally {
                    pdf.close();
                }
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues ready = new ContentValues();
                    ready.put(MediaStore.MediaColumns.IS_PENDING, 0);
                    getContentResolver().update(destination, ready, null, null);
                }
                lastPdf = destination;
                runOnUiThread(() -> {
                    saveButton.setEnabled(true);
                    shareButton.setVisibility(View.VISIBLE);
                    Toast.makeText(this, "Guardado en Descargas/PDF", Toast.LENGTH_LONG).show();
                });
            } catch (Exception error) {
                if (destination != null) getContentResolver().delete(destination, null, null);
                String message = error.getMessage() == null ? "Error desconocido" : error.getMessage();
                runOnUiThread(() -> {
                    saveButton.setEnabled(true);
                    Toast.makeText(this, "No se pudo crear el PDF: " + message, Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private Uri createPdfDestination() throws Exception {
        String name = fileName.getText().toString().trim();
        if (name.isEmpty()) name = "PDF-Run";
        name = name.replaceAll("[\\\\/:*?\"<>|]", "-");
        if (!name.toLowerCase().endsWith(".pdf")) name += ".pdf";

        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
        Uri collection;
        if (Build.VERSION.SDK_INT >= 29) {
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/PDF");
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);
            collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
        } else {
            File directory = new File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "PDF");
            if (!directory.exists() && !directory.mkdirs()) throw new Exception("No se pudo crear la carpeta PDF");
            values.put(MediaStore.MediaColumns.DATA, new File(directory, name).getAbsolutePath());
            collection = MediaStore.Files.getContentUri("external");
        }
        Uri uri = getContentResolver().insert(collection, values);
        if (uri == null) throw new Exception("No se pudo crear el archivo");
        return uri;
    }

    private PdfDocument buildPdf() throws Exception {
        PdfDocument pdf = new PdfDocument();
        for (PageItem item : pages) {
            if (item.image) addImagePage(pdf, item.uri);
            else addTextPages(pdf, item.text);
        }
        return pdf;
    }

    private void addImagePage(PdfDocument pdf, Uri uri) throws Exception {
        Bitmap bitmap = decodeBitmap(uri);
        PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(
                1240, 1754, pdf.getPages().size() + 1).create();
        PdfDocument.Page page = pdf.startPage(info);
        Canvas canvas = page.getCanvas();
        canvas.drawColor(Color.WHITE);
        float scale = Math.min(1160f / bitmap.getWidth(), 1674f / bitmap.getHeight());
        float width = bitmap.getWidth() * scale;
        float height = bitmap.getHeight() * scale;
        float left = (1240 - width) / 2;
        float top = (1754 - height) / 2;
        canvas.drawBitmap(bitmap, null,
                new RectF(left, top, left + width, top + height),
                new Paint(Paint.FILTER_BITMAP_FLAG));
        pdf.finishPage(page);
        bitmap.recycle();
    }

    private void addTextPages(PdfDocument pdf, String value) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.BLACK);
        paint.setTextSize(30);
        List<String> lines = wrapText(value, paint, 1120);
        int position = 0;
        while (position < lines.size()) {
            PdfDocument.Page page = pdf.startPage(new PdfDocument.PageInfo.Builder(
                    1240, 1754, pdf.getPages().size() + 1).create());
            Canvas canvas = page.getCanvas();
            canvas.drawColor(Color.WHITE);
            float y = 75;
            while (position < lines.size() && y < 1680) {
                canvas.drawText(lines.get(position++), 60, y, paint);
                y += 42;
            }
            pdf.finishPage(page);
        }
    }

    private List<String> wrapText(String value, Paint paint, float maxWidth) {
        List<String> lines = new ArrayList<>();
        for (String paragraph : value.replace("\r", "").split("\n", -1)) {
            if (paragraph.isEmpty()) {
                lines.add("");
                continue;
            }
            String current = "";
            for (String word : paragraph.split(" ")) {
                String candidate = current.isEmpty() ? word : current + " " + word;
                if (paint.measureText(candidate) > maxWidth && !current.isEmpty()) {
                    lines.add(current);
                    current = word;
                } else {
                    current = candidate;
                }
            }
            lines.add(current);
        }
        if (lines.isEmpty()) lines.add("");
        return lines;
    }

    private void sharePdf() {
        if (lastPdf == null) return;
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("application/pdf");
        intent.putExtra(Intent.EXTRA_STREAM, lastPdf);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, "Compartir PDF"));
    }
}
