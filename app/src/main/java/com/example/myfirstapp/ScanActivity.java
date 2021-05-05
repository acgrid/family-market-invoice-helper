package com.example.myfirstapp;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import android.media.Image;
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

public class ScanActivity extends AppCompatActivity {
    private static final String TAG = "SCAN";
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private ImageAnalysis imageAnalysis;

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
                        .addOnCompleteListener(task -> {
                            Log.i(TAG, task.toString());
                            imageProxy.close();
                        })
                        .addOnSuccessListener(barCodes -> {
                            for (Barcode barcode: barCodes) {
                                Log.d(TAG, barcode.getRawValue());
                                if (barcode.getValueType() == Barcode.TYPE_URL) {
                                    // http://fpj.datarj.com/einv/fm?q=b249MDQyODIzNTYwMDA3MDA4NTE5MjAxJnNpPTVjZjU3Yjc4MGFhOTc2ZTVmNjlmYzVlYjFlM2NlMzIw
                                    // on=042823560007008519201&si=5cf57b780aa976e5f69fc5eb1e3ce320
                                    Toast.makeText(getApplicationContext(), barcode.getUrl().getUrl(), Toast.LENGTH_LONG).show();
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