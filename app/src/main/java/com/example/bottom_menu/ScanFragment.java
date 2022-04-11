package com.example.bottom_menu;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.media.Image;
import android.os.Bundle;

import androidx.annotation.NonNull;

import android.graphics.Point;
import android.graphics.Rect;

import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import android.util.Size;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.google.android.gms.tasks.Task;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ScanFragment extends Fragment {

    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private ExecutorService cameraExecutor;
    public PreviewView previewView;
    private MyImageAnalyzer analyzer;
    private ImageView btnFlashOn, btnFlashOff;
    private final boolean lightOn = false;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_scan, container, false);
        previewView = view.findViewById(R.id.previewView);

        btnFlashOn = view.findViewById(R.id.btn_flash_on);
        btnFlashOff = view.findViewById(R.id.btn_flash_off);

        cameraExecutor = Executors.newSingleThreadExecutor();
        cameraProviderFuture = ProcessCameraProvider.getInstance(requireActivity());
        analyzer = new MyImageAnalyzer(getFragmentManager());

        cameraProviderFuture.addListener(() -> {
            try {
                if (ActivityCompat.checkSelfPermission(requireActivity(), Manifest.permission.CAMERA) != (PackageManager.PERMISSION_GRANTED)) {
                    ActivityCompat.requestPermissions(requireActivity(), new String[] {Manifest.permission.CAMERA}, 101);
                }else {
                    ProcessCameraProvider processCameraProvider = cameraProviderFuture.get();
                    bindPreview(processCameraProvider);
                }

            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(requireActivity()));

        return view;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == 101 && grantResults.length >0) {
            ProcessCameraProvider processCameraProvider = null;
            try {
                processCameraProvider = cameraProviderFuture.get();
            } catch ( InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
            bindPreview(Objects.requireNonNull(processCameraProvider));
        }
    }

    private void bindPreview(ProcessCameraProvider processCameraProvider) {

        Preview preview = new Preview.Builder().build();
        CameraSelector cameraSelector = new CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());
        processCameraProvider.unbindAll();
        ImageCapture imageCapture = new ImageCapture.Builder().build();
        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setTargetResolution(new Size(1280, 720))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();
        imageAnalysis.setAnalyzer(cameraExecutor,analyzer);
        processCameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture, imageAnalysis);
        btnFlashOn.setOnClickListener(view1 -> {
            Camera cam = processCameraProvider.bindToLifecycle(this,cameraSelector, preview, imageCapture, imageAnalysis);
            if ( cam.getCameraInfo().hasFlashUnit() ) {
                cam.getCameraControl().enableTorch(!lightOn);
                btnFlashOn.setVisibility(View.GONE);
                btnFlashOff.setVisibility(View.VISIBLE);
            }

        });
        previewView.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
    }

    public static class MyImageAnalyzer implements ImageAnalysis.Analyzer {
        private final FragmentManager fragmentManager;
        private final resultUrl bd;

        public MyImageAnalyzer(FragmentManager fragmentManager) {
            this.fragmentManager = fragmentManager;
            bd = new resultUrl();
        }

        @Override
        public void analyze(@NonNull ImageProxy image) {
            scanBarcodes(image);
        }

        private void scanBarcodes(ImageProxy image) {
            @SuppressLint("UnsafeOptInUsageError") Image image1 = image.getImage();
            assert image1 != null;
            InputImage inputImage = InputImage.fromMediaImage(image1, image.getImageInfo().getRotationDegrees());
            BarcodeScannerOptions options =
                    new BarcodeScannerOptions.Builder()
                            .setBarcodeFormats(
                                    Barcode.FORMAT_QR_CODE,
                                    Barcode.FORMAT_AZTEC,
                                    Barcode.FORMAT_ALL_FORMATS)
                            .build();
            BarcodeScanner scanner = BarcodeScanning.getClient(options);
            // Task failed with an exception
            // ...
            // Task completed successfully
            // [START_EXCLUDE]
            // [START get_barcodes]
            // [END get_barcodes]
            // [END_EXCLUDE]
            Task<List<Barcode>> result = scanner.process(inputImage)
                    .addOnSuccessListener(this::readerBarcodeData)
                    .addOnFailureListener(Throwable::printStackTrace)
                    .addOnCompleteListener(task -> image.close());
        }

        private void readerBarcodeData(List<Barcode> barcodes) {
            for (Barcode barcode : barcodes) {
                Rect bounds = barcode.getBoundingBox();
                Point[] corners = barcode.getCornerPoints();

                String rawValue = barcode.getRawValue();

                int valueType = barcode.getValueType();
                // See API reference for complete list of supported types
                switch (valueType) {
                    case Barcode.TYPE_WIFI:
                        String ssid = Objects.requireNonNull(barcode.getWifi()).getSsid();
                        String password = barcode.getWifi().getPassword();
                        int type = barcode.getWifi().getEncryptionType();
                        break;
                    case Barcode.TYPE_URL:
                        if (!bd.isAdded()) {
                            bd.show(fragmentManager, "");
                        }
                        bd.fetchUrl(Objects.requireNonNull(barcode.getUrl()).getUrl());
                        String title = barcode.getUrl().getTitle();
                        String url = barcode.getUrl().getUrl();
                        break;
                }
            }
        }
    }


}