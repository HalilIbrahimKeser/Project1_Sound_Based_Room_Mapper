package com.example.projectsoundbasedroommapper;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.media.ToneGenerator;
import android.os.AsyncTask;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.projectsoundbasedroommapper.classes.RoomView;
import com.example.projectsoundbasedroommapper.classes.SoundGraphView;
import com.example.projectsoundbasedroommapper.classes.XYCoordinates;
import com.example.projectsoundbasedroommapper.fft.RealDoubleFFT;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Timer;
import java.util.TimerTask;

public class SoundBasedMapperFragment extends Fragment implements SensorEventListener {

    private SensorManager mSensorManager;
    private final float[] accelerometerReading = new float[3];
    private final float[] magnetometerReading = new float[3];
    private final float[] rotationMatrix = new float[9];
    private final float[] orientationAngles = new float[3];

    private Sensor accelerometer;
    private Sensor magneticField;

    private Canvas roomCanvas;
    private TextView tvRotationMatrix;
    private TextView tvOrientationAngles;
    private ImageView ivRoom;
    private ImageView ivGraph;

    private Bitmap bmRoom;
    private Bitmap bmGraph;
    private Canvas graphCanvas;
    private Timer timer;
    private boolean isRunning = false;

    private Button btSoundPlayer;
    private RoomView roomView;
    private SoundGraphView soundGraphView;
    private MediaRecorder recorder;
    private MediaPlayer recordedMediaPlayer;
    private ToneGenerator tone;
    private int blockSize = 256; //for fft

    File fileSpike;
    FileWriter streamSpike;
    private static String pathSpikeMaxValue = null;
    private static final String FILE_NAME_SPIKE = "fileNameSpikeMaxValue.txt";

    File fileRecord;
    FileWriter streamRecord;
    private static String fileNameAudioRecord = null;
    private static final String FILE_NAME_RECORD = "fileNameAudioRecord.txt";

    private RealDoubleFFT fft;
    private AudioRecord audioRecord;
    int frequency = 8000;
    int channelConfiguration = AudioFormat.CHANNEL_IN_MONO;
    int audioEncoding = AudioFormat.ENCODING_PCM_16BIT;
    private RecordAudio recordTask;

    //NYTT
    int timeStep;
    //ArrayList<XYCoordinates> soundGraphCoordinates;
    //double[] afterFFT;
    //private double beforeFFT;


    public SoundBasedMapperFragment() {
    }

    public static SoundBasedMapperFragment newInstance() {
        SoundBasedMapperFragment fragment = new SoundBasedMapperFragment();
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_sound_based_mapper, container, false);
    }

    @Override
    public void onResume() {
        super.onResume();
        accelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (accelerometer != null) {
            mSensorManager.registerListener(this, accelerometer, mSensorManager.SENSOR_DELAY_NORMAL);
        }
        magneticField = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        if (magneticField != null) {
            mSensorManager.registerListener(this, magneticField, mSensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        btSoundPlayer = view.findViewById(R.id.playSound);

        fft = new RealDoubleFFT(blockSize);

//        fileNameAudioRecord = requireContext().getFilesDir().getAbsolutePath(); //path
//        fileNameAudioRecord += "/audiorecord.3gp";

        pathSpikeMaxValue = requireContext().getFilesDir().getAbsolutePath();
        fileSpike = new File(pathSpikeMaxValue, FILE_NAME_SPIKE);

        boolean hasMic = checkMicAvailability();
        mSensorManager = (SensorManager) getActivity().getSystemService(Context.SENSOR_SERVICE);
        tvOrientationAngles = view.findViewById(R.id.tvOrientationAngles);
        tvRotationMatrix = view.findViewById(R.id.tvRotationMatrix);

        ivRoom = view.findViewById(R.id.ivRoom);
        bmRoom = Bitmap.createBitmap(1500, 1500, Bitmap.Config.ARGB_8888);
        roomCanvas = new Canvas(bmRoom);

        ivGraph = view.findViewById(R.id.ivGraph);
        ivGraph.setScaleY(-1);
        bmGraph = Bitmap.createBitmap(420, 250, Bitmap.Config.ARGB_8888);
        graphCanvas = new Canvas(bmGraph);

        ivRoom.setImageBitmap(bmRoom);
        ivGraph.setImageBitmap(bmGraph); //nytt halil

        roomView = new RoomView(requireActivity());
        roomView.draw(roomCanvas);

        soundGraphView = new SoundGraphView(requireActivity());
        soundGraphView.draw(graphCanvas);

        btSoundPlayer.setOnClickListener(view1 -> {
            if (!isRunning) {
                if (hasMic) {
                    //ask for recording permissions
                    askForRecordingPermission();
                } else {
                    Toast.makeText(requireActivity(),
                            "This feature will only play a sound without a microphone. Please enjoy this tone.",
                            Toast.LENGTH_SHORT).show();
                    playSound();
                }
            } else {
                stopProgram();
            }
        });
    }

    @SuppressLint("StaticFieldLeak")
    private class RecordAudio extends AsyncTask<Void, double[], Void> {
        @Override
        protected Void doInBackground(Void... arg0) {

            try {
                int bufferSize = AudioRecord.getMinBufferSize(frequency,
                        channelConfiguration, audioEncoding);
                //Har checkpermission ved trykk av knappen før denne
                if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.
                    Toast.makeText(requireContext(), "Permission not given.", Toast.LENGTH_SHORT).show();
                }
                AudioRecord audioRecord = new AudioRecord(
                        MediaRecorder.AudioSource.MIC, frequency,
                        channelConfiguration, audioEncoding, bufferSize);

                short[] buffer = new short[blockSize];
                double[] toTransform = new double[blockSize];
                //double[] transformed = new double[blockSize];


                audioRecord.startRecording();

                // started = true; hopes this should true before calling
                // following while loop

                while (isRunning) {
                    int bufferReadResult = audioRecord.read(buffer, 0,
                            blockSize);

                    for (int i = 0; i < blockSize && i < bufferReadResult; i++) {
                        toTransform[i] = (double) buffer[i] / 32768.0; // signed
                    }
                    fft.ft(toTransform);
                    publishProgress(toTransform);

                    ArrayList<XYCoordinates> graphCoordinates = new ArrayList<XYCoordinates>();

                    //Se på Logcat Debug og tast inn FFT
                    //Bruker ToneGenerator.TONE_DTMF_1. Frekvensene havner i to områder, en mellom 40 og 50 og en mellom 70 og 80 (fra 1 - 256)
                    // -----Jeg har endret litt i (((bmGraph = Bitmap.createBitmap(420, 250, Bitmap.Config.ARGB_8888);))) og den ((new XYCoordinates((float) ((i + 8) * 1.55), (float) toTransform[i] * 10);))


                    //Vi kan velge enten den som ligger mellom 40 og 50 eller den som ligger mellom 70 og 80 (arrayene frequency40 og frequency70)
                    //Høyeste verdi i hvert array er "spikes", da må vi få max verdi i arrayet.
                    //Gjennomsnittsverdien til maksene gir oss et ca nivå på hvor høyt den verdien skal være i forhold til avstand.
                    //Gjennomsnittsverdiene kan vi bruke til kalibrering. Da må vi spille i forskjellige avstander fra f.eks en vegg
                    // -----Høres bra ut


                    //Lydvolumet må være KONSTANT og frequency som er sendt til Audiorecord er på 8000
                    //Tar ikke hensyn på materialet til objektet
                    //Vi må kanskje lagre maks til arrayene i en fil for å kunne bruke til kalibrering?
                    // -----Jeg lagrer nå begge max verdiene i en egen fil

                    // Vi må ha kalibreringsverdier for f.eks 0 avstand, 10 cm avstand, osv.... Så interpolerer vi resten?
                    // Han sa målingen var ikke så nøye, vi prøver så godt vi kan

                    //Ideen har ikke tatt hensyn til andre sensorverdier, men vi kan sammenligne med avstandssensor


                    // -----Husker du han sendte oss angående denne: "This file should look something like: date-time:distance:type(filtered,fft,none)"
                    //har du den fortsatt så kan jeg lagre fft i den andre filen

                    ArrayList<Double> frequency40 = new ArrayList<Double>();
                    ArrayList<Double> frequency70 = new ArrayList<Double>();

                    for (int i = 0; i < toTransform.length; i++) {
                        XYCoordinates coordinate = new XYCoordinates((float) ((i + 8) * 1.55), (float) toTransform[i] * 10);
                        if (i > 39 && i < 51) {
                            frequency40.add(toTransform[i]);
                        }
                        if (i > 69 && i < 81) {
                            frequency70.add(toTransform[i]);
                        }
                        graphCoordinates.add(coordinate);
                        Log.d("FFT value" + i + ": ", String.valueOf(toTransform[i] * 100));
                    }
                    double max40 = Collections.max(frequency40); //spike i 40 området, brukes til kalibrering, lagres med distanseverdi
                    double max70 = Collections.max(frequency70); //spike i 70 området, brukes til kalibrering, lagres med distanseverdi

                    soundGraphView.setGraphCoordinates(graphCoordinates);

                    saveSpikeMaxInFile(max40, max70);
                }

                audioRecord.stop();

            } catch (Throwable t) {
                t.printStackTrace();
                Log.e("AudioRecord", "Recording Failed");
            }
            return null;
        }
    }
    private void saveSpikeMaxInFile(double max40, double max70) {
        try {
            streamSpike = new FileWriter(fileSpike, true); //false for å slette gamle verdier i filen
            streamSpike.write("Max 40:" + max40 + ", Max70: " + max70);
            streamSpike.write("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            try {
                streamSpike.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public boolean checkMicAvailability() {
        if (requireActivity().getApplicationContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_MICROPHONE)) {
            return true;
        } else {
            return false;
        }
    }

    public void askForRecordingPermission() {
        if (ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED) {
            // You can use the API that requires the permission.
            //performAction(...);
            startProgram();
        } else if (shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
            // In an educational UI, explain to the user why your app requires this
            // permission for a specific feature to behave as expected. In this UI,
            // include a "cancel" or "no thanks" button that allows the user to
            // continue using your app without granting the permission.
            //showInContextUI(...);
            Toast.makeText(requireActivity(), "Needs dialog.", Toast.LENGTH_SHORT).show();
        } else {
            // You can directly ask for the permission.
            // The registered ActivityResultCallback gets the result of this request.
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
        }
    }

    private ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    // Permission is granted. Continue the action or workflow in your
                    // app.
                    startProgram();
                } else {
                    // Explain to the user that the feature is unavailable because the
                    // features requires a permission that the user has denied. At the
                    // same time, respect the user's decision. Don't link to system
                    // settings in an effort to convince the user to change their
                    // decision.
                    Toast.makeText(requireActivity(), "This button will only play a sound.", Toast.LENGTH_SHORT).show();
                }
            });

    private void startProgram() {
        isRunning = true;
        btSoundPlayer.setText("Stop");
        recordSound();
        playSound();
    }

    private void stopProgram() {
        isRunning = false;
        btSoundPlayer.setText("Play");
        stopRecording();
        stopSound();
    }

    private void playSound() {
        if (recordedMediaPlayer != null) {
            recordedMediaPlayer.release();
            recordedMediaPlayer = null;
        }
        tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
        tone.startTone(ToneGenerator.TONE_DTMF_1);
    }

    private void stopSound() {
        tone.stopTone();
        tone.release();
        tone = null;
    }

    private void recordSound() {
        recordTask = new RecordAudio();
        recordTask.execute();
        startDrawing();
    }

    private void playRecorded() {
        recordedMediaPlayer = new MediaPlayer();
        try {
            recordedMediaPlayer.setDataSource(fileNameAudioRecord);
            recordedMediaPlayer.prepare();
            recordedMediaPlayer.start();
        } catch (IOException e) {
            Toast.makeText(requireContext(), "Mediaplayer has failed.", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopRecording() {
        recordTask.cancel(true);
    }

    private void stopPlayingRecorded() {
        recordedMediaPlayer.stop();
        recordedMediaPlayer.release();
        recordedMediaPlayer = null;

    }

    @Override
    public void onStop() {
        super.onStop();
        mSensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        tvOrientationAngles.setText("");
        if (sensorEvent.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(sensorEvent.values, 0, accelerometerReading,
                    0, accelerometerReading.length);
        } else if (sensorEvent.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(sensorEvent.values, 0, magnetometerReading,
                    0, magnetometerReading.length);
        }
        updateOrientationAngles();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    public void updateOrientationAngles() {
        SensorManager.getRotationMatrix(rotationMatrix, null,
                accelerometerReading, magnetometerReading);

        SensorManager.getOrientation(rotationMatrix, orientationAngles);

        for (int i = 0; i < orientationAngles.length; i++) {
            if (i != 2) {
                tvOrientationAngles.append(i + ": " + orientationAngles[i] + ", ");
            } else {
                tvOrientationAngles.append(i + ": " + orientationAngles[i]);
            }
        }
    }

    public void startDrawing() {
        if (timer == null) {
            timer = new Timer();
            try {
                timer.scheduleAtFixedRate(new TimerTask() {
                    @Override
                    public void run() {
                        drawGraph();
                        //Log.d("bufferReadResult, Before toTransform:", String.valueOf(beforeFFT));
                        //Log.d("bufferReadResult, After toTransform:", String.valueOf(afterFFT));
                    }
                }, 0, 50);
            } catch (IllegalArgumentException | IllegalStateException iae) {
                iae.printStackTrace();
            }
        } else {
            timer = null;
        }
    }

    public void drawGraph() {
        graphCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.MULTIPLY);
        soundGraphView.draw(graphCanvas);
        ivGraph.setImageBitmap(bmGraph);
    }
}