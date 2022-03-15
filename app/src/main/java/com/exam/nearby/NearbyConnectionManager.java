package com.exam.nearby;

import android.app.Activity;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.collection.SimpleArrayMap;

import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.AdvertisingOptions;
import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback;
import com.google.android.gms.nearby.connection.ConnectionResolution;
import com.google.android.gms.nearby.connection.ConnectionsClient;
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo;
import com.google.android.gms.nearby.connection.DiscoveryOptions;
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadCallback;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate;
import com.google.android.gms.nearby.connection.Strategy;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class NearbyConnectionManager {
    // Constant variables
    // My name (using device name at the moment)
    private static final String myName = Utils.getDeviceName();
    private static final String TAG = "MainActivity-TAG";
    private static final int REQUEST_CODE_REQUIRED_PERMISSIONS = 101;
    private static final String SERVICE_ID = "google_connection_service";
    private static final Strategy STRATEGY = Strategy.P2P_STAR;
    private static final String TEXT_MESSAGE_PREFIX = "msg:";

    // Connections client
    public ConnectionsClient connectionsClient;
    private final MainActivity activity;

    private final ArrayList<Contact> contactList = new ArrayList<>();
    private Status status = Status.FINISHED;

    // Listeners
    private Consumer<List<Contact>> contactUpdateListener;

    public NearbyConnectionManager(MainActivity activity) {
        connectionsClient = Nearby.getConnectionsClient(activity);
        this.activity = activity;
    }

    public void setContactsUpdateListener(Consumer<List<Contact>> listener) {
        this.contactUpdateListener = listener;
    }

    public void startProcessing() {
        startAdvertising();
        startDiscovery();
    }

    public void stopProcessing() {
        connectionsClient.stopAdvertising();
        connectionsClient.stopDiscovery();
        connectionsClient.stopAllEndpoints();
    }

    private void startAdvertising() {
        AdvertisingOptions advertisingOptions = new AdvertisingOptions.Builder().setStrategy(STRATEGY).build();
        connectionsClient.startAdvertising(myName, SERVICE_ID, connectionLifecycleCallback, advertisingOptions)
                .addOnSuccessListener(
                        (Void unused) -> {
                            // We're advertising!
                            Log.w(TAG, "We're advertising");
                        })
                .addOnFailureListener(
                        (Exception e) -> {
                            // We were unable to start advertising.
                            Log.w(TAG, "We're not able to advertise: " + e.toString());
                        });
    }

    private void startDiscovery() {
        DiscoveryOptions discoveryOptions = new DiscoveryOptions.Builder().setStrategy(STRATEGY).build();
        connectionsClient.startDiscovery(SERVICE_ID, endpointDiscoveryCallback, discoveryOptions)
                .addOnSuccessListener(
                        (Void unused) -> {
                            // We're discovering!
                            Log.w(TAG, "We're discovering");
                        })
                .addOnFailureListener(
                        (Exception e) -> {
                            // We're unable to start discovering.
                            Log.w(TAG, "We're not able to discover: " + e.toString());
                        });
    }

    private final ConnectionLifecycleCallback connectionLifecycleCallback =
            new ConnectionLifecycleCallback() {
                @Override
                public void onConnectionInitiated(@NonNull String endpointId, @NonNull ConnectionInfo connectionInfo) {
                    // Automatically accept the connection on both sides.
                    connectionsClient.acceptConnection(endpointId, payloadCallback);

                    Contact contact = new Contact(connectionInfo.getEndpointName(), endpointId);
                    addContact(contact);
                }

                @Override
                public void onConnectionResult(@NonNull String endpointId, ConnectionResolution result) {
                    if(!result.getStatus().isSuccess()) {
                        removeContact(endpointId);
                    } else {
                        connectionsClient.stopAdvertising();
                        connectionsClient.stopDiscovery();
                    }
                }

                @Override
                public void onDisconnected(@NonNull String endpointId) {
                    // We've been disconnected from this endpoint. No more data can be
                    // sent or received.
                    Log.w(TAG, "We've been disconnected from this endpoint: " + endpointId);
                    removeContact(endpointId);
                }
            };

    private final EndpointDiscoveryCallback endpointDiscoveryCallback =
            new EndpointDiscoveryCallback() {
                @Override
                public void onEndpointFound(@NonNull String endpointId, @NonNull DiscoveredEndpointInfo info) {
                    // An endpoint was found. We request a connection to it.
                    connectionsClient.requestConnection(myName, endpointId, connectionLifecycleCallback)
                            .addOnSuccessListener(
                                    (Void unused) -> {
                                        // We successfully requested a connection. Now both sides
                                        // must accept before the connection is established.
                                        Log.w(TAG, "We successfully requested a connection");
                                    })
                            .addOnFailureListener(
                                    (Exception e) -> {
                                        // Nearby Connections failed to request the connection.
                                        Log.w(TAG, "Nearby connections failed");
                                    });
                }

                @Override
                public void onEndpointLost(@NonNull String endpointId) {
                    // A previously discovered endpoint has gone away.
                    Log.w(TAG, "Previous discovered endpoint has gone away:" + endpointId);
                    removeContact(endpointId);
                }
            };

    private final PayloadCallback payloadCallback = new PayloadCallback() {
        private final SimpleArrayMap<Long, Payload> incomingFilePayloads = new SimpleArrayMap<>();
        private final SimpleArrayMap<Long, Payload> completedFilePayloads = new SimpleArrayMap<>();
        private final SimpleArrayMap<Long, String> filePayloadFilenames = new SimpleArrayMap<>();

        @Override
        public void onPayloadReceived(@NonNull String s, @NonNull Payload payload) {
            status = Status.RECEIVING;
            switch (payload.getType()) {
                case Payload.Type.BYTES:
                    String payloadFilenameMessage = new String(payload.asBytes(), StandardCharsets.UTF_8);
                    long payloadId = addPayloadFilename(payloadFilenameMessage);
                    processFilePayload(payloadId);
                    break;
                case Payload.Type.FILE:
                    incomingFilePayloads.put(payload.getId(), payload);
                    break;
            }
        }

        @Override
        public void onPayloadTransferUpdate(@NonNull String endpointId, @NonNull PayloadTransferUpdate payloadTransferUpdate) {
            switch (payloadTransferUpdate.getStatus()) {
                case PayloadTransferUpdate.Status.IN_PROGRESS:
                    if(status == Status.SENDING) {
                        activity.showMyProgressBar();
                    } else if(status == Status.RECEIVING) {
                        activity.showReceivedProgressBar();
                    }
                    break;
                case PayloadTransferUpdate.Status.SUCCESS:
                    long payloadId = payloadTransferUpdate.getPayloadId();
                    Payload payload = incomingFilePayloads.remove(payloadId);
                    if(payload != null) {
                        completedFilePayloads.put(payloadId, payload);
                        if (payload.getType() == Payload.Type.FILE) {
                            processFilePayload(payloadId);
                        }
                    }

                    if(status == Status.SENDING) {
                        activity.hideMyProgressBar();
                    } else if(status == Status.RECEIVING) {
                        activity.hideReceivedProgressBar();
                    }
                    status = Status.FINISHED;
                    break;
                case PayloadTransferUpdate.Status.FAILURE:
                    Toast.makeText(activity, "Transfer failed", Toast.LENGTH_SHORT).show();
                    if(status == Status.SENDING) {
                        activity.hideMyProgressBar();
                    } else if(status == Status.RECEIVING) {
                        activity.hideReceivedProgressBar();
                    }
                    status = Status.FINISHED;
                    break;
                case PayloadTransferUpdate.Status.CANCELED:
                    Toast.makeText(activity, "Transfer cancelled", Toast.LENGTH_SHORT).show();
                    if(status == Status.SENDING) {
                        activity.hideMyProgressBar();
                    } else if(status == Status.RECEIVING) {
                        activity.hideReceivedProgressBar();
                    }
                    status = Status.FINISHED;
                    break;
            }
        }

        /**
         * Extracts the payloadId and filename from the message and stores it in the
         * filePayloadFilenames map. The format is payloadId:filename.
         */
        private long addPayloadFilename(String payloadFilenameMessage) {
            String[] parts = payloadFilenameMessage.split(":");
            long payloadId = Long.parseLong(parts[0]);
            String extension = parts[1];
            String filename = parts[2] + "." + extension;
            filePayloadFilenames.put(payloadId, filename);
            return payloadId;
        }

        private void processFilePayload(long payloadId) {
            // BYTES and FILE could be received in any order, so we call when either the BYTES or the FILE
            // payload is completely received. The file payload is considered complete only when both have
            // been received.
            Payload filePayload = completedFilePayloads.get(payloadId);
            String filename = filePayloadFilenames.get(payloadId);
            if (filePayload != null && filename != null) {
                completedFilePayloads.remove(payloadId);
                filePayloadFilenames.remove(payloadId);

                // Get the received file (which will be in the Downloads folder)
                // Because of https://developer.android.com/preview/privacy/scoped-storage, we are not
                // allowed to access filepaths from another process directly. Instead, we must open the
                // uri using our ContentResolver.
                SimpleFileDialog FileSaveDialog =  new SimpleFileDialog(activity, "FileSave",
                        chosenDir -> {
                            // The code in this function will be executed when the dialog OK button is pushed
                            Toast.makeText(activity, "Chosen File: " +
                                    chosenDir, Toast.LENGTH_LONG).show();

                            Uri uri = filePayload.asFile().asUri();
                            try {
                                // Copy the file to a new location.
                                InputStream in = activity.getContentResolver().openInputStream(uri);
                                copyStream(in, new FileOutputStream(chosenDir));
                            } catch (IOException e) {
                                // Log the error.
                            } finally {
                                // Delete the original file.
                                activity.getContentResolver().delete(uri, null, null);
                            }
                            activity.showReceivedImage(chosenDir);
                        });

                //You can change the default filename using the public variable "Default_File_Name"
                FileSaveDialog.Default_File_Name = filename;
                FileSaveDialog.chooseFile_or_Dir();
            }
        }

        /** Copies a stream from one location to another. */
        private void copyStream(InputStream in, OutputStream out) throws IOException {
            try {
                byte[] buffer = new byte[1024];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                out.flush();
            } finally {
                in.close();
                out.close();
            }
        }
    };

    private void addContact(Contact contact) {
        boolean found = false;
        for (Contact item: contactList) {
            if(item.isSame(contact)) {
                found = true;
                break;
            }
        }

        if(!found) {
            contactList.add(contact);
            if(contactUpdateListener != null) {
                contactUpdateListener.accept(contactList);
            }
        }
    }

    private void removeContact(String endPointId) {
        int index = -1;
        for (int i = 0; i < contactList.size(); i ++) {
            if(contactList.get(i).getEndPointId().equals(endPointId)) {
                index = i;
                break;
            }
        }

        if(index != -1) {
            contactList.remove(index);
            if(contactUpdateListener != null) {
                contactUpdateListener.accept(contactList);
            }
        }
    }

    public void sendImage(Uri uri, List<Contact> contacts) {
        Payload filePayload;
        try {
            ParcelFileDescriptor pfd = activity.getContentResolver().openFileDescriptor(uri, "r");
            filePayload = Payload.fromFile(pfd);
        } catch (Exception e) {
            Log.e(TAG, "File not found: " + e.toString());
            return;
        }

        // Construct a simple message mapping the ID of the file payload to the desired filename.
        String filenameMessage = filePayload.getId() + ":" + Utils.getFileExtension(activity, uri) + ":" + uri.getLastPathSegment();

        for (Contact contact: contacts) {
            // Send the filename message as a bytes payload.
            Payload filenameBytesPayload =
                    Payload.fromBytes(filenameMessage.getBytes(StandardCharsets.UTF_8));
            connectionsClient.sendPayload(contact.getEndPointId(), filenameBytesPayload);

            // Finally, send the file payload.
            connectionsClient.sendPayload(contact.getEndPointId(), filePayload);
            status = Status.SENDING;
        }
    }
}
