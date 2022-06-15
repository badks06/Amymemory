package fr.adksoft.mymemory

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.ktx.storage
import fr.adksoft.mymemory.models.BoardSize
import fr.adksoft.mymemory.utils.*
import java.io.ByteArrayOutputStream

class CreateActivity : AppCompatActivity() {

companion object {
    private const val TAG = "CreateActivity"
    private const val PICK_PHOTO_CODE = 655
    private const val READ_EXTERNAL_PHOTO_CODE = 248
    private const val READ_PHOTOS_PERMISSION = android.Manifest.permission.READ_EXTERNAL_STORAGE
    private const val MIN_GAME_NAME_LENGTH = 3
    private const val MAX_GAME_NAME_LENGTH = 14
}
    private lateinit var rvImagePicker: RecyclerView
    private lateinit var etGameName: EditText
    private  lateinit var btnSave: Button
    private  lateinit var pbUploading: ProgressBar

    private lateinit var adapter: ImagePickerAdapter
    private  lateinit var boardSize: BoardSize
    private  var numImagesRequired = -1
    private val chosenImageUris = mutableListOf<Uri>()
    private  val storage = Firebase.storage
    private val db = Firebase.firestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create)

        rvImagePicker = findViewById(R.id.rvImagePicker)
        etGameName = findViewById(R.id.etGameName)
        btnSave = findViewById(R.id.btnSave)
        pbUploading = findViewById(R.id.pbUploading)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        boardSize = intent.getSerializableExtra(EXTRA_BOARD_SIZE) as BoardSize
        numImagesRequired = boardSize.getNumPairs()
        supportActionBar?.title = "${getString(R.string.Choose_Pic)} $numImagesRequired)"


        btnSave.setOnClickListener{
            savDataToFirebase()
        }
        etGameName.filters = arrayOf(InputFilter.LengthFilter(MAX_GAME_NAME_LENGTH))
        etGameName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                btnSave.isEnabled = shouldEnableSaveButton()
            }

        })

        adapter = ImagePickerAdapter(this, chosenImageUris, boardSize, object: ImagePickerAdapter.ImageClickListener {
            override fun onPlaceholderClicked() {
                if (isPermissionGranted(this@CreateActivity, READ_PHOTOS_PERMISSION)) {
                    launchIntentForPhotos()
                } else {
                    requestPermission(this@CreateActivity, READ_PHOTOS_PERMISSION, READ_EXTERNAL_PHOTO_CODE)
                }
            }

        })
        rvImagePicker.adapter = adapter
        rvImagePicker.setHasFixedSize(true)
        rvImagePicker.layoutManager = GridLayoutManager(this, boardSize.getWidth())
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == READ_EXTERNAL_PHOTO_CODE) {
            if(grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                launchIntentForPhotos()
            } else {
                Toast.makeText(this, getString(R.string.In_Order_To), Toast.LENGTH_LONG).show()
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != PICK_PHOTO_CODE || resultCode != Activity.RESULT_OK || data == null) {
            Log.w(TAG, "Did not get data back from the launched activity, user likely canceled flow")
        return
        }
        val selectedUri: Uri? = data.data
        val clipData: ClipData? = data.clipData
        if (clipData != null) {
            Log.i(TAG, "clipData numImages ${clipData.itemCount}: $clipData")
            for (i: Int in 0 until clipData.itemCount) {
                val clipItem = clipData.getItemAt(i)
                if (chosenImageUris.size < numImagesRequired) {
                    chosenImageUris.add(clipItem.uri)
                }
            }
        } else if (selectedUri != null) {
            Log.i(TAG, "data: $selectedUri")
            chosenImageUris.add(selectedUri)
        }
        adapter.notifyDataSetChanged()
        supportActionBar?.title = getString(R.string.Choose_Pic) + ({chosenImageUris.size/numImagesRequired})
        btnSave.isEnabled = shouldEnableSaveButton()
    }

    private fun savDataToFirebase() {
        btnSave.isEnabled = false
        Log.i(TAG, getString(R.string.saveDataToFirebase))
        val customGameName = etGameName.text.toString()
        // Check that we're not over writing someone else's data
        db.collection("games").document(customGameName).get().addOnSuccessListener { document ->
            if (document != null && document.data != null) {
                AlertDialog.Builder(this)
                    .setTitle("Name taken")
                    .setMessage(getString(R.string.game_already_exists) +  {customGameName} + getString(R.string.choose_another))
                    .show()
                btnSave.isEnabled = true
            } else {
                handleImageUploadimg(customGameName)
            }
        }.addOnFailureListener { exception->
            Log.e(TAG, getString(R.string.Error_saving), exception)
            Toast.makeText(this, getString(R.string.Error_saving), Toast.LENGTH_SHORT).show()
        btnSave.isEnabled = true
        }
    }

    private fun handleImageUploadimg(gameName: String) {
        pbUploading.visibility = View.VISIBLE
        var didEncouterError = false
        val uploadedImageUrls: MutableList<String> = mutableListOf<String>()
        for ((index, photoUri) in chosenImageUris.withIndex()) {
            val imageByteArray = getImageByteArray(photoUri)
            val filePath = "images/$gameName/${System.currentTimeMillis()}-${index}.jpg"
            val photoReference: StorageReference = storage.reference.child(filePath)
            photoReference.putBytes(imageByteArray)
                .continueWithTask { photoUploadTask ->
                    Log.i(TAG, "Uploaded bytes: ${photoUploadTask.result?.bytesTransferred}")
                    photoReference.downloadUrl
                }.addOnCompleteListener { downlaodUrlTask ->
                    if (!downlaodUrlTask.isSuccessful)  {
                        Log.e(TAG, getString(R.string.Error_Firebase), downlaodUrlTask.exception)
                        Toast.makeText(this, getString(R.string.Failed_Upload_Image), Toast.LENGTH_SHORT).show()
                        didEncouterError = true
                        return@addOnCompleteListener
                    }
                    if (didEncouterError) {
                        pbUploading.visibility = View.GONE
                        return@addOnCompleteListener
                    }
                    val downloadUrl = downlaodUrlTask.result.toString()
                    uploadedImageUrls.add(downloadUrl)
                    pbUploading.progress = uploadedImageUrls.size * 100 / chosenImageUris.size
//                    Log.i(TAG, "Finished uploading $photoUri, num uploaded ${uploadedImageUrls.size}")




                    Log.i(TAG,"'getString(R.string.Finished_uploading)' $photoUri, num uploaded ${uploadedImageUrls.size}")
                    if (uploadedImageUrls.size == chosenImageUris.size) {
                        handleAllImagesUploaded(gameName, uploadedImageUrls)
                    }
                }
        }
    }

    private fun handleAllImagesUploaded(gameName: String, imageUrls: MutableList<String>) {
        // TODO: upload this info to Firestore
        db.collection("games").document(gameName)
            .set(mapOf("images" to imageUrls))
            .addOnCompleteListener { gameCreationTask ->
                pbUploading.visibility = View.GONE
                if (!gameCreationTask.isSuccessful) {
                    Log.e(TAG, getString(R.string.Erreur_creation), gameCreationTask.exception)
                    Toast.makeText(this, getString(R.string.Erreur_creation), Toast.LENGTH_SHORT).show()
                    return@addOnCompleteListener
                }
                Log.i(TAG, getString(R.string.SuccessGameCreated) + {gameName})
                AlertDialog.Builder(this)
                    .setTitle( getString(R.string.UploadComplete) + {gameName})
                    .setPositiveButton("OK") { _, _ ->
                        val resultData = Intent()
                        resultData.putExtra(EXTRA_GAME_NAME, gameName)
                        setResult(Activity.RESULT_OK, resultData)
                        finish()
                    }.show()
            }
    }

    private fun getImageByteArray(photoUri: Uri): ByteArray {
        val originalBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source: ImageDecoder.Source = ImageDecoder.createSource(contentResolver, photoUri)
            ImageDecoder.decodeBitmap(source)
        } else {
            MediaStore.Images.Media.getBitmap(contentResolver, photoUri)
        }
        Log.i(TAG, "Original width ${originalBitmap.width} and height ${originalBitmap.height}")
        val scaledBitmap: Bitmap = BitmapScaler.scaleToFitHeight(originalBitmap, 250)
        Log.i(TAG, "Scaled width ${scaledBitmap.width} and height ${scaledBitmap.height}")
        val byteOutputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 60, byteOutputStream)
        return byteOutputStream.toByteArray()
    }

    private fun shouldEnableSaveButton(): Boolean {
        // Check if we should enable the save button or not
        if (chosenImageUris.size != numImagesRequired) {
            return false
        }
        if (etGameName.text.isBlank() || etGameName.text.length < MIN_GAME_NAME_LENGTH) {
            return false
        }
        return true
    }

    private fun launchIntentForPhotos() {
       val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        startActivityForResult(Intent.createChooser(intent, getString(R.string.Choose_Pic)), PICK_PHOTO_CODE)
    }
}

















