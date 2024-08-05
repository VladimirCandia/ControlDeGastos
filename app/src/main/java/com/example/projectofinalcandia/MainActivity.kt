package com.example.projectofinalcandia

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.database.*


class MainActivity : AppCompatActivity() {

    private lateinit var etInitialAmount: EditText
    private lateinit var etPurchaseId: EditText
    private lateinit var etPurchaseName: EditText
    private lateinit var etPurchasePrice: EditText
    private lateinit var rgPaymentMethod: RadioGroup
    private lateinit var btnSaveInitialAmount: Button
    private lateinit var btnSavePurchase: Button
    private lateinit var btnViewPurchase: Button
    private lateinit var btnModifyPurchase: Button
    private lateinit var btnDeletePurchase: Button
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var btnClearFields: Button
    private lateinit var btnExit: Button


    private lateinit var databaseReference: DatabaseReference


    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
    }

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etInitialAmount = findViewById(R.id.gastoinicial)
        etPurchaseId = findViewById(R.id.idcompra)
        etPurchaseName = findViewById(R.id.nombrecompra)
        etPurchasePrice = findViewById(R.id.preciocompra)
        rgPaymentMethod = findViewById(R.id.metododepago)
        btnSaveInitialAmount = findViewById(R.id.guardargastoinicial)
        btnSavePurchase = findViewById(R.id.guardar)
        btnViewPurchase = findViewById(R.id.ver)
        btnModifyPurchase = findViewById(R.id.modificar)
        btnDeletePurchase = findViewById(R.id.eliminar)
        btnClearFields = findViewById(R.id.limpiar)


        sharedPreferences = getSharedPreferences("GastosApp", Context.MODE_PRIVATE)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        databaseReference = FirebaseDatabase.getInstance().reference.child("Compras")


        btnSaveInitialAmount.setOnClickListener {
            val initialAmount = etInitialAmount.text.toString()
            if (initialAmount.isNotEmpty()) {
                val editor = sharedPreferences.edit()
                editor.putFloat("remaining_amount", initialAmount.toFloat())
                editor.apply()
                Toast.makeText(this, "Cantidad inicial guardada", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Por favor, ingrese una cantidad válida", Toast.LENGTH_SHORT).show()
            }
        }

        btnSavePurchase.setOnClickListener {
            val purchaseId = etPurchaseId.text.toString()
            val purchaseName = etPurchaseName.text.toString()
            val purchasePriceStr = etPurchasePrice.text.toString()
            val selectedPaymentMethodId = rgPaymentMethod.checkedRadioButtonId
            val selectedPaymentMethodButton = findViewById<RadioButton>(selectedPaymentMethodId)
            val paymentMethod = selectedPaymentMethodButton.text.toString()


            if (purchaseId.isNotEmpty() && purchaseName.isNotEmpty() && purchasePriceStr.isNotEmpty() && selectedPaymentMethodId != -1) {
                val purchasePrice = purchasePriceStr.toFloat()
                var remainingAmount = sharedPreferences.getFloat("remaining_amount", 0f)

                if (purchasePrice <= remainingAmount) {
                    remainingAmount -= purchasePrice
                    val editor = sharedPreferences.edit()
                    editor.putFloat("remaining_amount", remainingAmount)
                    editor.apply()

                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                            if (location != null) {
                                val latitude = location.latitude
                                val longitude = location.longitude

                                val purchase = Purchase(purchaseId, purchaseName, purchasePrice, paymentMethod, latitude, longitude)
                                databaseReference.child(purchaseId).setValue(purchase).addOnCompleteListener { task ->
                                    if (task.isSuccessful) {
                                        val message = "Compra: $purchaseName, Precio: $purchasePrice, Tipo: $paymentMethod, Coordenadas: ($latitude, $longitude), Dinero Restante: $remainingAmount"
                                        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                                    } else {
                                        Toast.makeText(this, "Error al guardar la compra", Toast.LENGTH_SHORT).show()
                                    }
                                }.addOnFailureListener {
                                    Toast.makeText(this, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(this, "No se pudo obtener la ubicación", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
                    }
                } else {
                    Toast.makeText(this, "No hay suficiente dinero para esta compra", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Por favor, complete todos los campos", Toast.LENGTH_SHORT).show()
            }
        }

        btnViewPurchase.setOnClickListener {
            val purchaseId = etPurchaseId.text.toString()
            if (purchaseId.isNotEmpty()) {
                databaseReference.child(purchaseId).addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (snapshot.exists()) {
                            val purchase = snapshot.getValue(Purchase::class.java)
                            if (purchase != null) {
                                etPurchaseName.setText(purchase.name)
                                etPurchasePrice.setText(purchase.price.toString())
                                if (purchase.paymentMethod == "Efectivo") {
                                    rgPaymentMethod.check(R.id.efectibo)
                                } else {
                                    rgPaymentMethod.check(R.id.tarjeta)
                                }
                                Toast.makeText(this@MainActivity, "Compra encontrada", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(this@MainActivity, "Compra no encontrada", Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Toast.makeText(this@MainActivity, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                    }
                })
            } else {
                Toast.makeText(this, "Por favor, ingrese un ID de compra", Toast.LENGTH_SHORT).show()
            }
        }

        btnModifyPurchase.setOnClickListener {
            val purchaseId = etPurchaseId.text.toString()
            val purchaseName = etPurchaseName.text.toString()
            val purchasePriceStr = etPurchasePrice.text.toString()
            val selectedPaymentMethodId = rgPaymentMethod.checkedRadioButtonId
            val selectedPaymentMethodButton = findViewById<RadioButton>(selectedPaymentMethodId)
            val paymentMethod = selectedPaymentMethodButton.text.toString()

            if (purchaseId.isNotEmpty() && purchaseName.isNotEmpty() && purchasePriceStr.isNotEmpty() && selectedPaymentMethodId != -1) {
                val purchasePrice = purchasePriceStr.toFloat()
                val updatedPurchase = mapOf(
                    "name" to purchaseName,
                    "price" to purchasePrice,
                    "paymentMethod" to paymentMethod
                )

                databaseReference.child(purchaseId).updateChildren(updatedPurchase).addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Toast.makeText(this, "Compra modificada", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Error al modificar la compra", Toast.LENGTH_SHORT).show()
                    }
                }.addOnFailureListener {
                    Toast.makeText(this, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Por favor, complete todos los campos", Toast.LENGTH_SHORT).show()
            }
        }

        btnDeletePurchase.setOnClickListener {
            val purchaseId = etPurchaseId.text.toString()
            if (purchaseId.isNotEmpty()) {
                databaseReference.child(purchaseId).removeValue().addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Toast.makeText(this, "Compra eliminada", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Error al eliminar la compra", Toast.LENGTH_SHORT).show()
                    }
                }.addOnFailureListener {
                    Toast.makeText(this, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Por favor, ingrese un ID de compra", Toast.LENGTH_SHORT).show()
            }
        }

        btnClearFields.setOnClickListener {
            etInitialAmount.text.clear()
            etPurchaseId.text.clear()
            etPurchaseName.text.clear()
            etPurchasePrice.text.clear()
            rgPaymentMethod.clearCheck()
        }
        btnExit = findViewById(R.id.btnExit)
        btnExit.setOnClickListener {
            finishAffinity() // Cierra la aplicación
        }
    }
}

data class Purchase(
    val id: String = "",
    val name: String = "",
    val price: Float = 0f,
    val paymentMethod: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
)
