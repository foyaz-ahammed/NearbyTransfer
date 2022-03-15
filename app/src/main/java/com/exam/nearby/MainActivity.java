package com.exam.nearby;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.exam.nearby.databinding.ActivityMainBinding;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_CODE_REQUIRED_PERMISSIONS = 101;

    private ActivityMainBinding binding;
    private final ConnectorListAdapter adapter = new ConnectorListAdapter();

    private NearbyConnectionManager connectionManager;
    private List<Contact> contactList = new ArrayList<>();

    private Uri imageUri = null;

    ActivityResultLauncher<Intent> filePickActivityLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    Intent intent = result.getData();
                    if(intent != null) {
                        Uri uri = intent.getData();
                        if(uri != null) {
                            showImage(uri);
                        }
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        adapter.setContactItemUpdateListener(this::onContactItemUpdate);
        binding.connectors.setAdapter(adapter);

        connectionManager = new NearbyConnectionManager(this);
        connectionManager.setContactsUpdateListener(this::onContactsUpdate);

        onContactsUpdate(Collections.emptyList());

        binding.loadImage.setOnClickListener( (View view) -> {
            Intent intent = new Intent();
            intent.setType("image/*");
            intent.setAction(Intent.ACTION_GET_CONTENT);

            // pass the constant to compare it
            // with the returned requestCode
            filePickActivityLauncher.launch(intent);
        });

        binding.send.setOnClickListener((View view) -> {
            sendImage();
        });

        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                    new String[] { Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_CODE_REQUIRED_PERMISSIONS
            );
        } else {
            connectionManager.startProcessing();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResult) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResult);

        String errMsg = "Cannot start without required permissions";
        if (requestCode == REQUEST_CODE_REQUIRED_PERMISSIONS) {
            for (int result: grantResult) {
                if (result == PackageManager.PERMISSION_DENIED) {
                    Toast.makeText(this, errMsg, Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
            }
            recreate();
        }
    }

    private void onContactsUpdate(List<Contact> contacts) {
        this.contactList = contacts;

        if(contacts.isEmpty()) {
            binding.connectors.setVisibility(View.INVISIBLE);
            binding.noDevices.setVisibility(View.VISIBLE);
        } else {
            binding.connectors.setVisibility(View.VISIBLE);
            binding.noDevices.setVisibility(View.GONE);

            List<Integer> colors = Utils.generateColors(this, contacts.size());
            int i = 0;
            for (Contact item : contactList) {
                item.setColor(colors.get(i++));
            }

            adapter.submitList(contactList);
        }
    }

    private void onContactItemUpdate(Contact contact) {
        for (Contact item: contactList) {
            if(item.isSame(contact)) {
                item.setChecked(contact.isChecked());
            }
        }
    }

    public void showImage(Uri uri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            Drawable drawable = Drawable.createFromStream(inputStream, uri.toString() );
            if(drawable != null) {
                this.imageUri = uri;
                binding.myImage.setImageDrawable(drawable);
            } else {
                Toast.makeText(this, "Please select a valid image", Toast.LENGTH_SHORT).show();
            }
        } catch (FileNotFoundException e) {
            Toast.makeText(this, "Please select a valid image", Toast.LENGTH_SHORT).show();
        }
    }

    public void showReceivedImage(String path) {
        Drawable d = Drawable.createFromPath(path);
        if(d != null) {
            binding.receivedImage.setImageDrawable(d);
        } else {
            Toast.makeText(this, "Image is not valid or doesn't exist", Toast.LENGTH_SHORT).show();
        }
    }

    private void sendImage() {
        if(imageUri == null) {
            Toast.makeText(this, "Please load an image", Toast.LENGTH_SHORT).show();
        } else {
            List<Contact> selectedContacts = new ArrayList<>();
            for(Contact contact: contactList) {
                if(contact.isChecked()) {
                    selectedContacts.add(contact);
                }
            }

            if(selectedContacts.isEmpty()) {
                Toast.makeText(this, "You didn't select any devices", Toast.LENGTH_SHORT).show();
            } else {
                connectionManager.sendImage(imageUri, selectedContacts);
            }
        }
    }

    public void showMyProgressBar() {
        binding.progressBarMine.setVisibility(View.VISIBLE);
    }
    public void hideMyProgressBar() {
        binding.progressBarMine.setVisibility(View.GONE);
    }
    public void showReceivedProgressBar() {
        binding.progressBarReceived.setVisibility(View.VISIBLE);
    }
    public void hideReceivedProgressBar() {
        binding.progressBarReceived.setVisibility(View.GONE);
    }
}