package com.emre.njeqr.view

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageAnalysis.*
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.emre.njeqr.databinding.ActivitySecondBinding
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

class SecondActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySecondBinding
    private lateinit var cameraSelector:CameraSelector
    private lateinit var cameraPreview:Preview
    private lateinit var cameraProviderFuture:ListenableFuture<ProcessCameraProvider>
    private lateinit var processCameraProvider:ProcessCameraProvider
    private lateinit var imageAnalysis:ImageAnalysis

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySecondBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        cameraSelector=CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()
        cameraProviderFuture=ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            processCameraProvider=cameraProviderFuture.get()
            bindCameraPreview()
            bindInputAnalyser()
        },ContextCompat.getMainExecutor(this)
        )
    }
private fun bindCameraPreview(){
    cameraPreview=Preview.Builder().setTargetRotation(binding.previewView.display.rotation).build()
    cameraPreview.setSurfaceProvider(binding.previewView.surfaceProvider)
    processCameraProvider.bindToLifecycle(this,cameraSelector,cameraPreview)
}
    private fun bindInputAnalyser(){
        val barcodeScanner : BarcodeScanner=BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS).build()
        )
        imageAnalysis=ImageAnalysis.Builder().setTargetRotation(binding.previewView.display.rotation)
            .build()
        val cameraExecutor = Executors.newSingleThreadExecutor()
        imageAnalysis.setAnalyzer(cameraExecutor){
            imageProxy->
            processImageProxy(barcodeScanner,imageProxy)
        }
           processCameraProvider.bindToLifecycle(this,cameraSelector,imageAnalysis)
    }
    @SuppressLint("UnsafeOptInUsageError")
    private fun processImageProxy(barcodeScanner: BarcodeScanner, imageProxy: ImageProxy){
        val inputImage=InputImage.fromMediaImage(imageProxy.image!!,imageProxy.imageInfo.rotationDegrees)
        barcodeScanner.process(inputImage).addOnSuccessListener {
               barcodes ->
            if(barcodes.isNotEmpty()){
                onScan?.invoke(barcodes)
                onScan =null
                finish()
            }
        }.addOnFailureListener{
            it.printStackTrace()
        }.addOnCompleteListener{
            imageProxy.close()
        }
    }


companion object{
    private var onScan:((barcodes:List<Barcode>)->Unit)?=null
    fun startScanner(context: Context,onScan: (barcodes: List<Barcode>)->Unit){
        Companion.onScan =onScan
        Intent(context, SecondActivity::class.java).also{
            context.startActivity(it)
        }
    }
}
}