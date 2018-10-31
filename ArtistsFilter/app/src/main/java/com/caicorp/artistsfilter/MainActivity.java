package com.caicorp.artistsfilter;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.media.Image;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.BitmapCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.DefaultHttpClient;


import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    private final int GALLERY_CODE=1112;
    public static final String FILE_UPLOAD_URL = "http://10.0.2.2:8000/upload_file";    // path to django/flask upload endpoint
    public static String opFilePath = "";           // global filepath to temporary stored bitmap
    public static long totalSize = 0;               // global size of temporary stored bitmap

    private ImageView imageView;
    private ImageView imageView2;
    private Bitmap transImage;
    private String fname;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        // Permission Check
        int permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "권한 있음.", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "권한 없음.", Toast.LENGTH_LONG).show();
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                Toast.makeText(this, "권한 설명 필요함.", Toast.LENGTH_LONG).show();
            } else {
                ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
            }
        }

        imageView = (ImageView) findViewById(R.id.imageView);
        imageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectGallery();
            }
        });


        Button button = (Button) findViewById(R.id.button);
        button.setOnClickListener(new View.OnClickListener()
        {
            public void onClick(View v)
            {
                /* File that points to bitmap */
                File img_file = null;

                imageView.invalidate();
                BitmapDrawable drawable = (BitmapDrawable) imageView.getDrawable();
                Bitmap img = drawable.getBitmap();

                if (fname != null) {
                    int cut = fname.lastIndexOf('/');
                    if (cut != -1) {
                        fname = fname.substring(cut + 1);
                    }
                }

                Log.d("Log", "File name: " + fname);

                try{
                    img_file = saveBitmap(img, fname+".jpg");
                } catch (IOException e) {
                    Log.d("Log", "File not found");
                }

                /* Set path and size global vars */
                opFilePath =  img_file.getAbsolutePath();
                totalSize = BitmapCompat.getAllocationByteCount(img);

                /* Trigger upload function asynctask */
                UploadFileToServer uploadFileToServer = new UploadFileToServer();
                uploadFileToServer.execute();
            }
        });


    }



    private void selectGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setData(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        startActivityForResult(intent, GALLERY_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK) {

            switch (requestCode) {

                case GALLERY_CODE:
                    fname = data.getDataString();
                    Uri uri = data.getData();
                    Bitmap bitmap = null;
                    try {
                        bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    imageView.setImageBitmap(bitmap);//이미지 뷰에 비트맵 넣기
                    break;

                default:
                    break;
            }

        }
    }


    public class UploadFileToServer extends AsyncTask<Void, Integer, String> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        /* show result of upload in a toast */
        @Override
        protected String doInBackground(Void... params) {
            final String ret = uploadFile();
            runOnUiThread(new Runnable() {
                public void run() {
                    Toast.makeText(getApplicationContext(),ret, Toast.LENGTH_LONG).show();
                }
            });
            return ret;
        }

        private String uploadFile() {
            String checkResponse = null;
            HttpClient httpclient = new DefaultHttpClient();
            HttpPost httppost = new HttpPost(FILE_UPLOAD_URL);


            try {
                AndroidMultiPartEntity entity = new AndroidMultiPartEntity(
                        new AndroidMultiPartEntity.ProgressListener() {
                            @Override
                            public void transferred(long num) {
                                publishProgress((int) ((num / (float) totalSize) * 100));
                            }
                        });
                File file = new File(opFilePath);

                // the name here needs to match on Django
                entity.addPart("pic", new FileBody(file));

                totalSize = entity.getContentLength();
                httppost.setEntity(entity);
                HttpResponse response = httpclient.execute(httppost);

                int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode == 200) {

                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    response.getEntity().writeTo(out);
                    out.close();

                    byte[] bytes = out.toByteArray();
                    transImage = BitmapFactory.decodeByteArray(bytes,0,bytes.length);

                    runOnUiThread(new Runnable() {

                        @Override
                        public void run() {

                            // Stuff that updates the UI
                            ImageView imageView2 = (ImageView)findViewById(R.id.imageView2);
                            imageView2.setImageBitmap(transImage);

                        }
                    });
                    checkResponse = "Success";

                } else {
                    checkResponse = "Error occurred, Http Status Code: " + statusCode;
                }
            } catch (ClientProtocolException e) {
                checkResponse = e.toString();
            } catch (IOException e) {
                checkResponse = e.toString();

            }

            return checkResponse;
        }
    }


    /* saves bitmap to local cache dir */
    public File saveBitmap(Bitmap bmp, String fname) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.JPEG, 100, bytes);
        File f = new File(getCacheDir() + File.separator + fname);
        if (f.exists())
            return f;
        f.createNewFile();
        FileOutputStream fo = new FileOutputStream(f);
        fo.write(bytes.toByteArray());
        fo.close();
        return f;
    }

}
