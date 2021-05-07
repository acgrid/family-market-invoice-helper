package com.example.myfirstapp;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import android.media.Image;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.widget.Toast;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.Barcode;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.common.InputImage;

import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RequiresApi(api = Build.VERSION_CODES.O)
public class ScanActivity extends AppCompatActivity {
    private static final String TAG = "SCAN";
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private ImageAnalysis imageAnalysis;
    private final Pattern outerPattern = Pattern.compile("q=(.+)$");
    private final Pattern innerPattern = Pattern.compile("on=(\\d+)");
    private final Base64.Decoder decoder = Base64.getUrlDecoder();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan);
        initScanner();
        initCamera();
    }

    void initCamera () {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindPreview(cameraProvider);
            } catch (Throwable e) {
                Toast.makeText(getApplicationContext(), "Camera Failure", Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    void initScanner () {
        BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build();
        BarcodeScanner scanner = BarcodeScanning.getClient(options);
        imageAnalysis = new ImageAnalysis.Builder()
                .setTargetResolution(new Size(1280, 720))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this), imageProxy -> {
            @OptIn(markerClass = androidx.camera.core.ExperimentalGetImage.class)
            Image image = imageProxy.getImage();
            if (image != null) {
                InputImage inputImage = InputImage.fromMediaImage(image, imageProxy.getImageInfo().getRotationDegrees());
                scanner.process(inputImage)
                        .addOnCompleteListener(task -> imageProxy.close())
                        .addOnSuccessListener(barCodes -> {
                            for (Barcode barcode: barCodes) {
                                String url = barcode.getRawValue();
                                if (url == null) continue;
                                Log.d(TAG, url);
                                Matcher matcher = outerPattern.matcher(url.trim());
                                if (matcher.find()) {
                                    String q = matcher.group(1);
                                    Log.i(TAG, q);
                                    if (q != null) {
                                        String query = new String(decoder.decode(q));
                                        Log.i(TAG, query);
                                        if (query.length() > 10) {
                                            Matcher innerMatcher = innerPattern.matcher(query);
                                            if (innerMatcher.find()) {
                                                String orderNo = innerMatcher.group(1);
                                                Toast.makeText(getApplicationContext(), orderNo, Toast.LENGTH_LONG).show();
                                            }
                                        }
                                    }
                                }
                            }
                        })
                        .addOnFailureListener(e -> Log.e(TAG, "Decode barcode", e));
            }
        });
    }

    void bindPreview (@NonNull ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder()
                .build();
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        PreviewView previewView = findViewById(R.id.previewView);
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        cameraProvider.bindToLifecycle((LifecycleOwner) this, cameraSelector, imageAnalysis, preview);
    }
}