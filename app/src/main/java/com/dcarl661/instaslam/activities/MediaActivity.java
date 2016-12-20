package com.dcarl661.instaslam.activities;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.CursorLoader;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.dcarl661.instaslam.R;
import com.dcarl661.instaslam.model.InstaImageModel;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.regex.Matcher;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class MediaActivity extends AppCompatActivity {
    /**
     * Whether or not the system UI should be auto-hidden after
     * {@link #AUTO_HIDE_DELAY_MILLIS} milliseconds.
     */
    private static final boolean AUTO_HIDE = true;

    /**
     * If {@link #AUTO_HIDE} is set, the number of milliseconds to wait after
     * user interaction before hiding the system UI.
     */
    private static final int AUTO_HIDE_DELAY_MILLIS = 3000;

    /**
     * Some older devices needs a small delay between UI widget updates
     * and a change of the status and navigation bar.
     */
    private static final int UI_ANIMATION_DELAY = 300;
    private final Handler mHideHandler = new Handler();
    private View mContentView;
    private final Runnable mHidePart2Runnable = new Runnable() {
        @SuppressLint("InlinedApi")
        @Override
        public void run() {
            // Delayed removal of status and navigation bar

            // Note that some of these constants are new as of API 16 (Jelly Bean)
            // and API 19 (KitKat). It is safe to use them, as they are inlined
            // at compile-time and do nothing on earlier devices.
            mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }
    };
    private View mControlsView;
    private final Runnable mShowPart2Runnable = new Runnable() {
        @Override
        public void run() {
            // Delayed display of UI elements
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                actionBar.show();
            }
            mControlsView.setVisibility(View.VISIBLE);
        }
    };
    private boolean mVisible;
    private final Runnable mHideRunnable = new Runnable() {
        @Override
        public void run() {
            hide();
        }
    };
    /**
     * Touch listener to use for in-layout UI controls to delay hiding the
     * system UI. This is to prevent the jarring behavior of controls going away
     * while interacting with activity UI.
     */
    private final View.OnTouchListener mDelayHideTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            if (AUTO_HIDE) {
                delayedHide(AUTO_HIDE_DELAY_MILLIS);
            }
            return false;
        }
    };

    final int PERMISSION_READ_EXTERNAL        = 111;
    private ArrayList<InstaImageModel> images = new ArrayList<InstaImageModel>();
    private ImageView selectedImage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_media);

        mVisible = true;
        mControlsView = findViewById(R.id.fullscreen_content_controls);
        mContentView = findViewById(R.id.fullscreen_content);


        // Set up the user interaction to manually show or hide the system UI.
        mContentView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggle();
            }
        });

        //SEtup the recycler view "boilderplate" the classes are in this java
        //content_images in recyclerview in the xml design
        selectedImage=(ImageView)findViewById(R.id.selected_image); //store reference to the selected image
        RecyclerView recyclerView = (RecyclerView)findViewById(R.id.content_images);
        //images is an array list added e
        ImagesAdapter adapter     = new ImagesAdapter(images);
        recyclerView.setAdapter(adapter);
        GridLayoutManager layoutManager=new GridLayoutManager(getBaseContext(),4); //four across
        layoutManager.setOrientation(GridLayoutManager.VERTICAL);
        recyclerView.setLayoutManager(layoutManager);


        //We have an added permission entry in the manifest
        // here we check to see if the User has granted.
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)<= PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},PERMISSION_READ_EXTERNAL);
            //note if they grant the grant call back also calls retrieveAndImages()
        }
        else{
            retrieveAndSetImages();
        }

        // Upon interacting with UI controls, delay any scheduled hide()
        // operations to prevent the jarring behavior of controls going away
        // while interacting with the UI.
        findViewById(R.id.dummy_button).setOnTouchListener(mDelayHideTouchListener);
    }

/*
    Here's how I got the images to the emulator

    C:\Program Files (x86)\Android\android-sdk\platform-tools> adb push C:\hold\xfer\training1.jpg /sdcard/Pictures
    adb server is out of date.  killing...
            * daemon started successfully *
            884 KB/s (88284 bytes in 0.097s)
    C:\Program Files (x86)\Android\android-sdk\platform-tools> adb push C:\hold\xfer\andromeda.jpg /sdcard/Pictures
    1162 KB/s (296660 bytes in 0.249s)

    C:\Program Files (x86)\Android\android-sdk\platform-tools> adb push C:\hold\xfer\theyliverrp2.jpg /sdcard/Pictures
    98 KB/s (5225 bytes in 0.052s)
*/

    public void retrieveAndSetImages()
    {
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                images.clear();//ArrayList  created above,  images
                //like an sql cursor but in androids f'ed up syntax
                Cursor cursor = getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,null,null,null,null);
                Log.v("FISH", "retrieveAndSetImages11111 getcount="+cursor.getCount());
                if(cursor != null)
                {
                    cursor.moveToFirst();
                    Log.v("FISH", "Move to first");
                }
                else
                {
                    Log.v("FISH", "Cursor Is NUll");
                    return;
                }
                Log.v("FISH", "retrieveAndSetImages33333");
                for(int x=0; x<cursor.getCount(); x++)
                {
                    cursor.moveToPosition(x);
                    String s=cursor.getString(1);
                    Log.v("FISH", "URL: " + s);
                    InstaImageModel instaImageModel=new InstaImageModel(Uri.parse(s));
                    images.add(instaImageModel);
                }
                cursor.close();
                //todo
                runOnUiThread(new Runnable(){
                    @Override
                    public void run(){
                        //set images on ercycler view adampoer
                        //update images
                    }
                });
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case PERMISSION_READ_EXTERNAL: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.v("FISH", "Calling retrieveAndSetImages");
                    retrieveAndSetImages();
                }
            }
        }
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        // Trigger the initial hide() shortly after the activity has been
        // created, to briefly hint to the user that UI controls
        // are available.
        delayedHide(100);
    }

    private void toggle() {
        if (mVisible) {
            hide();
        } else {
            show();
        }
    }

    private void hide() {
        // Hide UI first
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }
        mControlsView.setVisibility(View.GONE);
        mVisible = false;

        // Schedule a runnable to remove the status and navigation bar after a delay
        mHideHandler.removeCallbacks(mShowPart2Runnable);
        mHideHandler.postDelayed(mHidePart2Runnable, UI_ANIMATION_DELAY);
    }

    @SuppressLint("InlinedApi")
    private void show() {
        // Show the system bar
        mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        mVisible = true;

        // Schedule a runnable to display UI elements after a delay
        mHideHandler.removeCallbacks(mHidePart2Runnable);
        mHideHandler.postDelayed(mShowPart2Runnable, UI_ANIMATION_DELAY);
    }

    /**
     * Schedules a call to hide() in [delay] milliseconds, canceling any
     * previously scheduled calls.
     */
    private void delayedHide(int delayMillis) {
        mHideHandler.removeCallbacks(mHideRunnable);
        mHideHandler.postDelayed(mHideRunnable, delayMillis);
    }

    public class ImagesAdapter extends RecyclerView.Adapter<ImageViewHolder>
    {
        //put your property before generating the constructor and it will generate
        // the constructor with the parameter
        private ArrayList<InstaImageModel> images;
        public ImagesAdapter(ArrayList<InstaImageModel> images) {
            this.images = images;
        }

        @Override
        public ImageViewHolder onCreateViewHolder(ViewGroup parent, int viewType)
        {
            View card= LayoutInflater.from(parent.getContext()).inflate(R.layout.card_image,parent,false);
            return new ImageViewHolder(card);
        }

        @Override
        public void onBindViewHolder(ImageViewHolder holder, int position)
        {
            final InstaImageModel image=images.get(position);
            holder.updateUI(image);
            final ImageViewHolder vHolder=holder;//because we need the final keyword
            holder.itemView.setOnClickListener(new View.OnClickListener(){
                @Override
                public void onClick(View v){
                    selectedImage.setImageDrawable(vHolder.imageView.getDrawable());
                }
            });
        }

        @Override
        public int getItemCount(){
            return images.size();
        }

    }
    //note having the adapter and holder in the same file seems easier but it breaks encapsulation
    //   note that the private ImageView can be accessed in the adapter directly not via a method
    public class ImageViewHolder extends RecyclerView.ViewHolder
    {
        private ImageView imageView;
        public ImageViewHolder(View itemView)
        {
            super(itemView);
            imageView=(ImageView)itemView.findViewById(R.id.image_thumb);
        }
        public void updateUI(InstaImageModel image)
        {
            //call decodeURI sync
            this.imageView.setImageBitmap(decodeURI(image.getImgResourceURI().getPath()));
            //Async
            //DecodeBitmap task = new DecodeBitmap(imageView,image);
           // task.execute();
        }
    }

    //here you can tweek for better quality but  these are our thumbnails so lesser is better for ram
    public Bitmap decodeURI(String filePath){
        BitmapFactory.Options options=new BitmapFactory.Options();
        options.inJustDecodeBounds=true;
        BitmapFactory.decodeFile(filePath, options);

        //only scale if we need to 16384 buffer
        Boolean scaleByHeight = Math.abs(options.outHeight - 100) >= Math.abs(options.outWidth - 100);
        if( options.outHeight * options.outWidth * 2 >= 16384)
        {
            double sampleSize    = scaleByHeight ? options.outHeight/1000:options.outWidth/1000;
            options.inSampleSize = (int) Math.pow(2d,Math.floor(Math.log(sampleSize)/Math.log(2d)));
        }

        options.inJustDecodeBounds = false;
        options.inTempStorage      = new byte[512];
        Bitmap output              = BitmapFactory.decodeFile(filePath,options);
        return output;
    }

    class DecodeBitmap extends AsyncTask<Void, Void, Bitmap>
    {
        //weak and strong effect how this will live in memory will get garbagecollected
        private final WeakReference<ImageView> mImageViewWeakReference;
        private InstaImageModel image;

        public DecodeBitmap(ImageView imageView, InstaImageModel image) {
            this.mImageViewWeakReference = new WeakReference<ImageView>(imageView);
            this.image=image;
        }

        @Override
        protected Bitmap doInBackground(Void... params) {
            return decodeURI(image.getImgResourceURI().getPath());
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            super.onPostExecute(bitmap);
            final ImageView img = mImageViewWeakReference.get();

            if (img != null) {
                //can be null because this backgound task may end but the user
                // is already off the screen doing something else
                img.setImageBitmap(bitmap);
            }
        }

    }


}
