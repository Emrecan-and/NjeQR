package com.emre.njeqr.view

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.camera.core.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.emre.njeqr.cameraPermissionRequest
import com.emre.njeqr.databinding.ActivityMainBinding
import com.emre.njeqr.isPermissionGranted
import com.emre.njeqr.openPermissionSetting
import com.google.mlkit.vision.barcode.common.Barcode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.create
import java.io.File
import java.io.FileOutputStream
import java.sql.Timestamp
import java.util.Locale
import java.util.TimeZone
import kotlin.random.Random

class MainActivity : AppCompatActivity() {
    val timeZone: TimeZone = TimeZone.getTimeZone("Europe/Budapest")
    val isDaylightSavingTime: Boolean = timeZone.inDaylightTime(java.util.Date(System.currentTimeMillis()))
    private lateinit var binding: ActivityMainBinding
    private val cameraPermission=android.Manifest.permission.CAMERA
    var value:String?=null //Barkod değeri
    var timestamp:Timestamp?=null
    var excelFileYolu:String?=null
    var valueList:ArrayList<String> =ArrayList()
    var timeList:ArrayList<String> =ArrayList()
    private val requestPermissionLauncher=registerForActivityResult(ActivityResultContracts.RequestPermission()){
        isGranted->
        if(isGranted){
            startScanner()
        }
    }
    private lateinit var myDatabase:SQLiteDatabase
    private val WRITE_EXTERNAL_STORAGE_PERMISSION_REQUEST_CODE = 2
    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        binding.button2.isEnabled=false
        binding.ExcelButton.isEnabled=false
        setContentView(view)
        try {
            myDatabase=this.openOrCreateDatabase("NjeQr",Context.MODE_PRIVATE,null)
            myDatabase.execSQL("CREATE TABLE IF NOT EXISTS qr (value VARCHAR PRIMARY KEY,timestamp TIMESTAMP)")
        }catch (e:Exception){
            e.printStackTrace()
        }
        val locale:Locale=Locale.getDefault()
        binding.button.setOnClickListener {
            requestCameraAndStartScanner()
            //Veriler burada buradan çek
        }
        binding.button2.setOnClickListener {
                timestamp =Timestamp(System.currentTimeMillis()+timeZone.rawOffset + if (isDaylightSavingTime) timeZone.dstSavings else 0)
                try {
                    myDatabase.execSQL("INSERT INTO qr (value,timestamp) VALUES ('$value','$timestamp')")
                    Toast.makeText(this,"Submitted Successfully",Toast.LENGTH_LONG)
                    binding.ExcelButton.isEnabled=true
                    binding.button2.isEnabled=false
                }catch (e:Exception){
                    e.printStackTrace()
                }
            }
        binding.englSh.setOnClickListener{
            if(locale.language!="en"){
                setLocale("en",this)
                recreate()
            }
        }
        binding.hungary.setOnClickListener {
            if(locale.language!="hu"){
                setLocale("hu",this)
                recreate()
            }
        }
        binding.ExcelButton.setOnClickListener {
            try {
                var cursor=myDatabase.rawQuery("SELECT * FROM qr",null)
                val valueIx=cursor.getColumnIndex("value")
                val timeIx=cursor.getColumnIndex("timestamp")
                while (cursor.moveToNext()){
                    valueList.add(cursor.getString(valueIx))
                    timeList.add(cursor.getString(timeIx))//BU YANLIŞ OLABİLİR STRING DONDURDU
                }
            }
            catch (e:Exception){
                e.printStackTrace()
                Toast.makeText(this, "Veritabanı işlemi sırasında bir hata oluştu: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            if (checkWriteExternalStoragePermission()) {
                createExcelFile(valueList,timeList)
                binding.ExcelButton.isEnabled=false
            } else {
                // İzin verilmemişse, izni talep et
                requestWriteExternalStoragePermission()
            }

        }
    }

   @RequiresApi(Build.VERSION_CODES.M)
   private fun requestCameraAndStartScanner(){
       if(isPermissionGranted(cameraPermission)){
           startScanner()
       }
       else{
           requestCameraPermission()
       }
   }
@RequiresApi(Build.VERSION_CODES.M)
private fun requestCameraPermission(){
    when{
        shouldShowRequestPermissionRationale(cameraPermission) ->{
            cameraPermissionRequest {
                openPermissionSetting()
            }
        }
        else ->{
            requestPermissionLauncher.launch(cameraPermission)
        }
    }
}
    private fun startScanner(){
        SecondActivity.startScanner(this) { barcodes ->
            barcodes.forEach() { barcode ->
                when (barcode.valueType) {
                    Barcode.TYPE_URL -> {
                        binding.textViewQrType.text = "URL"
                        binding.textViewQrContent.text = barcode.url.toString()
                    }
                    Barcode.TYPE_CONTACT_INFO -> {
                        binding.textViewQrType.text = "Contact"
                        binding.textViewQrContent.text = barcode.contactInfo.toString()
                    }
                    else -> {
                        binding.textViewQrType.text = "Barcode"
                        binding.textViewQrContent.text = barcode.rawValue.toString()
                        value = barcode.rawValue.toString()
                        binding.button2.isEnabled=true
                        //Bunu dene olmadı barcode.displayValue rawdATA
                    }
                }
            }
        }
    }
    fun setLocale(languageCode:String,context: Context){
        val locale = Locale(languageCode)
        Locale.setDefault(locale)

        val config = Configuration()
        config.setLocale(locale)

        context.resources.updateConfiguration(config, context.resources.displayMetrics)
    }
    @SuppressLint("SuspiciousIndentation")
    private fun createExcelFile(valueList:ArrayList<String>, timeList:ArrayList<String>){
        val workbook=XSSFWorkbook() //çalışma kitabı oluşturdum
        val sheet=workbook.createSheet("NjeQR")
        val mergedRegion = CellRangeAddress(0, 0, 0, 1)
        sheet.addMergedRegion(mergedRegion)
        val headerRow=sheet.createRow(0)
        val header1=headerRow.createCell(0)
        header1.setCellValue("Barcode Value")
        val dataCellStyle = workbook.createCellStyle()
        dataCellStyle.setFillForegroundColor(IndexedColors.RED.getIndex())
        dataCellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND)
        val headerFont = workbook.createFont()
        headerFont.bold = true
        dataCellStyle.setFont(headerFont)
        header1.cellStyle=dataCellStyle
        val mergedRegio = CellRangeAddress(0, 0, 2, 4)
        sheet.addMergedRegion(mergedRegio)
        val header2=headerRow.createCell(2)
            header2.setCellValue("Time")
        header2.cellStyle=dataCellStyle
        for(i in 0..(valueList.size-1)){
            val row=sheet.createRow(i+1)
            row.createCell(0).setCellValue(valueList[i])
            row.createCell(2).setCellValue(timeList[i])
        }
        loadFile(workbook)
        myDatabase.delete("qr",null,null)

    }
    private fun loadFile(workbook:XSSFWorkbook){
        val filePath="nje_qr_${Random.nextInt(0,5000000)}.xlsx"
        val excelDosyaYolu = "${getExternalFilesDir(null)}/$filePath"
        try {
            val fileOut=FileOutputStream(excelDosyaYolu)
            workbook.write(fileOut)
            fileOut.close()
            val alertDialog = AlertDialog.Builder(this)
                .setTitle("EXPORTED SUCCESSFULLY")
                .setMessage("FILE NAME= ${excelDosyaYolu}")
                .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                .create()

            alertDialog.show()
            excelFileYolu=excelDosyaYolu
            excelAç()
        }catch (e:Exception){
            e.printStackTrace()
            Toast.makeText(this, "DOSYA KAYIT işlemi sırasında bir hata oluştu: ${e.message}", Toast.LENGTH_SHORT).show()
            binding.textViewQrType.text=e.message
        }finally {
            workbook.close()
        }

    }
    private fun excelAç(){

        val file = File(excelFileYolu)
        if (file.exists()) {
            val uri = FileProvider.getUriForFile(
                this, // Bu, Context nesnesini temsil eder
                "com.yourapp.fileprovider", // Dosya sağlayıcının yetki adı (Uygulama paket adını içerebilir)
                file
            )

            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(uri, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            try {
                startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                // İlgili bir uygulama bulunamadığında hata işleme yapılabilir
                Toast.makeText(this, "No app to handle this file type", Toast.LENGTH_SHORT).show()
            }
        } else {
            // Dosya bulunamadığında işlemler yapılabilir
            Toast.makeText(this, "File not found", Toast.LENGTH_SHORT).show()
        }

    }

    private fun checkWriteExternalStoragePermission(): Boolean {
        val permission = "android.permission.WRITE_EXTERNAL_STORAGE"
        val permissionGranted = ContextCompat.checkSelfPermission(this, permission)
        return permissionGranted == PackageManager.PERMISSION_GRANTED
    }

    // Dosya yazma iznini isteyen fonksiyon
    private fun requestWriteExternalStoragePermission() {
        val permission = "android.permission.WRITE_EXTERNAL_STORAGE"
        if (checkWriteExternalStoragePermission()) {
            createExcelFile(valueList, timeList)
            // İzin zaten verilmişse, işlemi gerçekleştir
            // Dosya yazma işlemi burada yapılabilir
        } else {
            // İzin henüz verilmediyse, izni talep et
            ActivityCompat.requestPermissions(
                this,
                arrayOf(permission),
                WRITE_EXTERNAL_STORAGE_PERMISSION_REQUEST_CODE
            )
        }
    }

    // İzin talebi sonuçlarını kontrol eden fonksiyon
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == WRITE_EXTERNAL_STORAGE_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Yazma izni verildi
                // Dosya yazma işlemi burada yapılabilir
                createExcelFile(valueList, timeList)
            } else {
                // Yazma izni reddedildi
                // Kullanıcıya açıklama yapabilir veya gerekli eylemi alabilirsiniz
                Toast.makeText(
                    this,
                    "Dosya yazma izni reddedildi.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}
//https://www.youtube.com/watch?v=jRb2rrUnYKw