package com.ryce.frugalist.view.create;

import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.places.AutocompleteFilter;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.ui.PlaceAutocomplete;
import com.ryce.frugalist.R;
import com.ryce.frugalist.model.Deal;
import com.ryce.frugalist.model.MockDatastore;
import com.ryce.frugalist.network.ImgurRequest;
import com.ryce.frugalist.network.ImgurResponse;
import com.ryce.frugalist.network.ImgurServiceHelper;
import com.ryce.frugalist.util.Utils;
import com.squareup.picasso.Picasso;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Created by shivand on 2/15/2016.
 */
public class CreateListingActivity extends AppCompatActivity {

    // arbitrary request code for image capture
    private static final int CAPTURE_IMAGE_REQUEST_CODE = 2;
    // request code for autocompleting the address and store
    private static final int PLACE_AUTOCOMPLETE_REQUEST_CODE_STORE = 9;

    ImageView mPhotoImageView;
    FloatingActionButton mUploadButton;
    ProgressDialog mProgressDialog;

    EditText mProductInput;
    EditText mPriceInput;
    EditText mUnitInput;
    Button mInsertLocationButton;
    TextView mStoreInput;
    TextView mAddressInput;

    // current image file
    private File mImgFile;

    // callback for imgur image upload request
    Callback<ImgurResponse> mImgurResponseCallback = new Callback<ImgurResponse>() {
        @Override
        public void onResponse(Call<ImgurResponse> call, Response<ImgurResponse> response) {

            if (response.isSuccess()) {

                Deal deal = new Deal(
                        response.body().data.link,
                        mPriceInput.getText().toString(),
                        mProductInput.getText().toString(),
                        0,
                        mUnitInput.getText().toString(),
                        mStoreInput.getText().toString(),
                        mAddressInput.getText().toString());

                MockDatastore.getInstance().addDeal(deal);

                Snackbar.make(findViewById(android.R.id.content), "Deal posted, thanks for sharing!", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();

                cleanupRequest();

            } else {

                try {
                    Log.i("IMGUR", "Error: " + response.errorBody().string());
                } catch (IOException e) {
                    // not handling
                }

                // something went wrong!
                Snackbar.make(findViewById(android.R.id.content), "Something went wrong! Please try again", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();

                // just dismiss the dialog instead of cleaning up temp img
                // so user can try to re-upload the file
                dismissDialog();
            }
        }

        @Override
        public void onFailure(Call<ImgurResponse> call, Throwable t) {
            Snackbar.make(findViewById(android.R.id.content), "Failed! " + t.getMessage(), Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();
            cleanupRequest();
        }

    };

    /**
     * Clean up previous request, delete temp img, etc
     */
    private void cleanupRequest() {
        // delete image after we are done with it!
        if (mImgFile != null && mImgFile.exists()) {
            mImgFile.delete();
            mImgFile = null;
        }
        dismissDialog();
    }

    /**
     * Dismiss the progress dialog
     */
    private void dismissDialog() {
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
            mProgressDialog = null;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_listing);

        if (savedInstanceState == null) {
            //getSupportFragmentManager().beginTransaction().add(R.id.container, new PlaceholderFragment()).commit();
        }

        // TODO: get intent extras to determine if we are creating a deal or a freebie!

        // make sure we can write to disk
        // TODO: verify user allowed permission?
        Utils.verifyStoragePermissions(this);

        mPhotoImageView = (ImageView) findViewById(R.id.cameraView);
        mProductInput = (EditText) findViewById(R.id.productInput);
        mPriceInput = (EditText) findViewById(R.id.priceInput);
        mUnitInput = (EditText) findViewById(R.id.unitInput);
        mInsertLocationButton = (Button) findViewById(R.id.insertLocationbtn);
        mStoreInput = (TextView) findViewById(R.id.storeInput);
        mAddressInput = (TextView) findViewById(R.id.addressInput);
        mUploadButton = (FloatingActionButton) findViewById(R.id.uploadFab);

        // set default unit
        // TODO: make this a drop-down!
        mUnitInput.setText("ea");

        // setup listener for camera button
        mPhotoImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dispatchImageCaptureIntent();
            }
        });

        // setup listener for upload button
        mUploadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // TODO: do mediation for user data
                executeUpload(view);
            }
        });

        // set up the autocomplete intents for store and address
        mInsertLocationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dispatchPlaceAutocompleteIntent();
            }
        });

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (requestCode == CAPTURE_IMAGE_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {

                // we want to scale down image file from camera
                mImgFile = scaleDownImage(mImgFile);

                // load image into image view
                Picasso.with(getBaseContext())
                        .load(mImgFile)
                        .fit()
                        .into(mPhotoImageView);

            } else if (resultCode == RESULT_CANCELED) {

                // if user cancels out of the camera without taking a picture, we need to remove the temporary file
                if (mImgFile != null && mImgFile.exists()) {
                    mImgFile.delete();
                    mImgFile = null;
                }

            }
        } else if (requestCode == PLACE_AUTOCOMPLETE_REQUEST_CODE_STORE) {
            if (resultCode == RESULT_OK) {
                Place place = PlaceAutocomplete.getPlace(this, data);
                Log.i("...", "Place: " + place.getLatLng());
                mStoreInput.setText(place.getName());
                mAddressInput.setText(place.getAddress());
            } else if (resultCode == PlaceAutocomplete.RESULT_ERROR) {
                Status status = PlaceAutocomplete.getStatus(this, data);
                // TODO: Handle the error.
                Log.i("...", status.getStatusMessage());

            } else if (resultCode == RESULT_CANCELED) {
                // The user canceled the operation.
            }
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    /**
     * Initiate place autocomplete intent
     */
    private void dispatchPlaceAutocompleteIntent() {
        try {

            AutocompleteFilter storeFilter = new AutocompleteFilter.Builder()
                    .setTypeFilter(AutocompleteFilter.TYPE_FILTER_ESTABLISHMENT)
                    .build();

            Intent intent = new PlaceAutocomplete.IntentBuilder(PlaceAutocomplete.MODE_OVERLAY)
                    .setFilter(storeFilter)
                    .build(CreateListingActivity.this);

            startActivityForResult(intent, PLACE_AUTOCOMPLETE_REQUEST_CODE_STORE);

        } catch (GooglePlayServicesRepairableException e) {
            // TODO: Handle the error.
        } catch (GooglePlayServicesNotAvailableException e) {
            // TODO: Handle the error.
        }
    }

    /**
     * Initiate camera capture intent
     */
    private void dispatchImageCaptureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        // Ensure that there's a camera activity to handle the intent
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {

            // clean up previous request
            cleanupRequest();

            // Create the File where the photo should go
            try {
                mImgFile = createImageFile();
            } catch (IOException ex) {
                // Error occurred while creating the File
                ex.printStackTrace();
            }

            // Continue only if the File was successfully created
            if (mImgFile != null) {
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(mImgFile));
                startActivityForResult(takePictureIntent, CAPTURE_IMAGE_REQUEST_CODE);
            }

        } else {
            // TODO: no camera... what
        }
    }

    /**
     * Create a temp image file
     * @return
     * @throws IOException
     */
    private File createImageFile() throws IOException {

        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";

        // create a temporary image file in DCIM folder
        File storageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );

        return image;
    }

    /**
     * Execute image upload request
     * @param parentView
     */
    private void executeUpload(View parentView) {
        if (mImgFile != null && mImgFile.exists()) {

            ImgurRequest request = new ImgurRequest();
            request.image = mImgFile;

            // start wait dialog
            mProgressDialog = ProgressDialog.show(parentView.getContext(), "Uploading deal...", "Please wait", true);
            // do request
            ImgurServiceHelper.getInstance().doPostImage(getBaseContext(), request, mImgurResponseCallback);

        } else {
            Snackbar.make(parentView, "No image to upload/File no longer exists", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();
        }
    }

    /**
     * Scale down and compress an image (from camera)
     * Will delete the old image
     *
     * @param oldImageFile
     * @return
     */
    private File scaleDownImage(File oldImageFile) {
        if (oldImageFile == null || !oldImageFile.exists()) {
            return null;
        }

        // get reduced image
        Bitmap image = Utils.decodeSampledBitmapFromFile(oldImageFile.getAbsolutePath(), 720, 1280);

        File newImageFile = null;
        try {
            // create new image file
            newImageFile = createImageFile();

            FileOutputStream fos = new FileOutputStream(newImageFile);

            // store compressed bitmap in file
            image.compress(Bitmap.CompressFormat.JPEG, 50, fos);

            fos.close();

            // delete old file
            oldImageFile.delete();

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return newImageFile;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle app bar item clicks here. The app bar
        // automatically handles clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        if(id == R.id.action_logout){
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

}