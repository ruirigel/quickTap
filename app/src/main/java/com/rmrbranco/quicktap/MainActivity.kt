package com.rmrbranco.quicktap

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import android.provider.Settings
import android.util.Log
import com.google.firebase.FirebaseApp

class MainActivity : AppCompatActivity() {

    private var clickCount = 0  // Variável para armazenar o número de cliques
    private lateinit var countDownTimer: CountDownTimer  // Declaração do timer
    private var isTimerRunning = false  // Flag para verificar se o timer está ativo
    private lateinit var database: DatabaseReference  // Referência ao Firebase Database

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Inicializar o Firebase Database
        FirebaseApp.initializeApp(this)
        // Referência ao Firebase Database
        database = FirebaseDatabase.getInstance().reference

        val button1 = findViewById<Button>(R.id.button1) // Botão para incrementar o contador
        val button3 = findViewById<Button>(R.id.button3)  // Botão para reiniciar o contador
        val textView1 = findViewById<TextView>(R.id.textView1)  // Exibe o número de cliques
        val textView2 = findViewById<TextView>(R.id.textView2)  // Exibe a contagem decrescente

        // Obter o ID único do dispositivo
        val deviceId = retrieveDeviceId()

        // Função para inicializar o timer
        fun initializeTimer() {
            countDownTimer = object :
                CountDownTimer(100000, 1000) {  // 100 segundos, decrementando a cada 1 segundo
                override fun onTick(millisUntilFinished: Long) {
                    val secondsRemaining = millisUntilFinished / 1000
                    textView2.text =
                        secondsRemaining.toString() // Exibe a contagem decrescente em segundos
                }

                override fun onFinish() {
                    textView2.text = "0"  // Define 0 quando o tempo acaba
                    button1.isEnabled = false  // Desativa o botão quando a contagem termina
                    isTimerRunning = false  // Atualiza a flag
                    // Salvar o recorde de cliques no Firebase quando o tempo acabar
                    checkAndSaveRecord(deviceId, clickCount)
                }
            }
        }

        // Inicializa o timer na primeira execução
        initializeTimer()

        // Configura o clique do botão de incremento
        button1.setOnClickListener {
            // Incrementa o contador de cliques e exibe no textView1
            clickCount++
            textView1.text = clickCount.toString()

            // Inicia o contador decrescente na primeira vez que o botão é pressionado
            if (!isTimerRunning) {
                countDownTimer.start()
                isTimerRunning = true
            }
        }

        // Configura o clique do botão de reinício
        button3.setOnClickListener {
            // Cancela o timer se estiver rodando
            if (isTimerRunning) {
                countDownTimer.cancel()
                isTimerRunning = false
            }

            // Reinicia as variáveis
            clickCount = 0
            textView1.text =
                getString(R.string.click_count)  // Reinicia o contador de cliques na tela
            textView2.text =
                getString(R.string.initial_count)  // Reinicia o contador de tempo na tela
            button1.isEnabled = true  // Reativa o button1

            // Recria o timer
            initializeTimer()
        }
    }

    // Função para verificar e salvar o recorde no Firebase
    private fun checkAndSaveRecord(deviceId: String, clickCount: Int) {
        val recordRef = database.child("data/$deviceId")

        recordRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    Log.d("Firebase", "Registro não encontrado, criando novo para o ID: $deviceId")

                    recordRef.setValue(
                        mapOf(
                            "click_count" to clickCount,
                            "timestamp" to System.currentTimeMillis()
                        )
                    ).addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Log.d("Firebase", "Novo registro criado para o ID: $deviceId")
                        } else {
                            Log.e(
                                "Firebase",
                                "Falha ao criar novo registro: ${task.exception?.message}"
                            )
                        }
                    }
                } else {
                    Log.d("Firebase", "Registro encontrado, verificando se o clickCount é maior")

                    val currentRecord = snapshot.child("click_count").getValue(Int::class.java) ?: 0
                    if (clickCount > currentRecord) {
                        Log.d(
                            "Firebase",
                            "Novo recorde encontrado, atualizando para o ID: $deviceId"
                        )

                        recordRef.setValue(
                            mapOf(
                                "click_count" to clickCount,
                                "timestamp" to System.currentTimeMillis()
                            )
                        ).addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                Log.d("Firebase", "Recorde atualizado para o ID: $deviceId")
                            } else {
                                Log.e(
                                    "Firebase",
                                    "Falha ao atualizar recorde: ${task.exception?.message}"
                                )
                            }
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("Firebase", "Error retrieving data: ${error.message}")
            }
        })
    }

    @SuppressLint("HardwareIds")
    private fun retrieveDeviceId(): String {
        return Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
    }

}