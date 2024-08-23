package com.example.boldo;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;
import androidx.media3.common.util.UnstableApi;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;


import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity2 extends AppCompatActivity {
    private ActivityResultLauncher<Intent> filePickerLauncher;
    private TextToSpeech tts;
    private static final int REQUEST_CAMERA_PERMISSION = 1;
    private TextRecognizer textRecognizer;
    private TextView textView;
    private ActivityResultLauncher<Intent> cameraLauncher;
    private Uri capturedImageUri;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);
        textView = findViewById(R.id.textView3); // Set your layout file
        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        Intent data = result.getData();
                        if (data != null) {
                            Uri fileUri = data.getData();
                            // Handle the selected file URI
                            handleSelectedFile(fileUri);
                        }
                    }
                }
        );


        // Initialize TextToSpeech
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = tts.setLanguage(Locale.US);
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("TTS", "Language not supported");
                }
            } else {
                Log.e("TTS", "Initialization failed");
            }
        });

        // Initialize TextRecognizer
        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        // Initialize the ActivityResultLauncher
        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        Intent data = result.getData();
                        if (data != null) {
                            Uri fileUri = data.getData();
                            handleSelectedFile(fileUri);
                        }
                    }
                }
        );
        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        // Handle the captured image result here
                        handleSelectedFile(capturedImageUri);
                    }
                });

        // Set up a button to trigger the file picker
        @SuppressLint({"MissingInflatedId", "LocalSuppress"})
        ImageButton btnChooseFile = findViewById(R.id.imageButton); // Use the correct button ID
        btnChooseFile.setOnClickListener(view -> openFilePicker());

        // Handle incoming text from MainActivity
        Intent intent = getIntent();
        String textToRead = intent.getStringExtra("text_to_read");
        if (textToRead != null && !textToRead.isEmpty()) {
            Toast.makeText(this, textToRead, Toast.LENGTH_SHORT).show();
            tts.speak(textToRead, TextToSpeech.QUEUE_FLUSH, null, null);
        }

    }



    private void openFilePicker() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Choose an option")
                .setItems(new String[]{"Take Photo", "Choose from Gallery"}, (dialog, which) -> {
                    if (which == 0) {
                        openCamera();
                    } else {
                        openImagePicker();
                    }
                });
        builder.create().show();
    }

    @SuppressLint("QueryPermissionsNeeded")
    private void openCamera() {
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
                File photoFile = null;
                try {
                    photoFile = createImageFile();
                } catch (IOException ex) {
                    Log.e("Camera", "Error creating image file", ex);
                }

                if (photoFile != null) {
                    // Replace with your authority
                    capturedImageUri = FileProvider.getUriForFile(this,
                            "com.example.boldo.fileprovider",
                            photoFile);
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, capturedImageUri);
                    cameraLauncher.launch(takePictureIntent);
                }
            } else {
                Toast.makeText(this, "No camera app available", Toast.LENGTH_SHORT).show();
            }
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission was granted, open the camera
                openCamera();
            } else {
                Toast.makeText(this, "Camera permission is required to take photos.", Toast.LENGTH_SHORT).show();
            }
        }
    }


    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        return File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        filePickerLauncher.launch(intent);
    }

    private void handleSelectedFile(Uri fileUri) {
        if (fileUri != null) {
            try {
                String extractedText = extractTextFromImage(fileUri);
                if (!extractedText.isEmpty()) {
                    Toast.makeText(this, extractedText, Toast.LENGTH_LONG).show();
                    tts.speak(extractedText, TextToSpeech.QUEUE_FLUSH, null, null);
                }
            } catch (Exception e) {
                Log.e("FileExtractionError", "Error extracting text", e);
                Toast.makeText(this, "Error extracting text. Please try again.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private String extractTextFromImage(Uri imageUri) {
        try {
            InputImage image = InputImage.fromFilePath(this, imageUri);
            return textRecognizer.process(image)
                    .addOnSuccessListener(this::onSuccess)
                    .addOnFailureListener(e -> {
                        Log.e("ImageExtractionError", "Error extracting text from image", e);
                        Toast.makeText(this, "Error extracting text from image", Toast.LENGTH_SHORT).show();
                    }).getResult().getText();
        } catch (IOException e) {
            Log.e("ImageExtractionError", "Error loading image", e);
            Toast.makeText(this, "Error loading image", Toast.LENGTH_SHORT).show();
            return "";
        }
    }


    @OptIn(markerClass = UnstableApi.class)
    private void onSuccess(Text text) {
        // Get the recognized text
        String recognizedText = text.getText();


        // 1. Log the recognized text
        androidx.media3.common.util.Log.d("OCR", "Recognized text: " + recognizedText);

        // 2. Display the recognized text in a TextView (if you have one in your layout)
        findViewById(R.id.textView3);
        textView.setText(recognizedText);

        // 3. Speak the recognized text using TextToSpeech
        tts.speak(recognizedText, TextToSpeech.QUEUE_FLUSH, null, null);
    }

}