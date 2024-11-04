package com.rmrbranco.quicktap

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.Toast
import com.google.firebase.FirebaseApp
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime

class MainActivity : AppCompatActivity() {

    private var clickCount = 0  // Variável para armazenar o número de cliques
    private lateinit var countDownTimer: CountDownTimer  // Declaração do timer
    private var isTimerRunning = false  // Flag para verificar se o timer está ativo
    private lateinit var database: DatabaseReference  // Referência ao Firebase Database
    val currentTimeMillis = System.currentTimeMillis()
    val ldt = LocalDateTime.now().toString()
    private lateinit var deviceId: String  // Declare a variável deviceId
    private var isDialogShowing = false // Flag para verificar se o diálogo está sendo exibido


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
        val button2 = findViewById<Button>(R.id.button2) // Botão para mostrar o score board
        val button3 = findViewById<Button>(R.id.button3)  // Botão para reiniciar o contador
        val textView1 = findViewById<TextView>(R.id.textView1)  // Exibe o número de cliques
        val textView2 = findViewById<TextView>(R.id.textView2)  // Exibe a contagem decrescente

        // Obter o ID único do dispositivo
        deviceId = retrieveDeviceId()  // Inicializa a variável deviceId

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

        // Configura o clique do botão de score board
        button2.setOnClickListener {

            // Verifica a conexão com a internet antes de mostrar o score board
            val isConnected = checkInternetConnection()
            if (isConnected) {
                showScoreBoardDialog()
            } else {
                Toast.makeText(
                    this@MainActivity,
                    "Conexão à internet: $isConnected",
                    Toast.LENGTH_SHORT
                ).show()
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
                            "username" to "guest$currentTimeMillis",
                            "click_count" to clickCount,
                            "date" to ldt
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

                        recordRef.updateChildren(
                            mapOf(
                                "click_count" to clickCount,
                                "date" to ldt
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

    // Classe para armazenar os dados de pontuação
    data class ScoreItem(val username: String, val score: Int, var ranking: Int)

    // Função para exibir o diálogo de score board
    @SuppressLint("InflateParams")
    private fun showScoreBoardDialog() {
        if (isDialogShowing) return // Não abre o diálogo se já estiver aberto

        val dialog = Dialog(this)
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_score_board, null)
        dialog.setContentView(dialogView)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // Atualiza a variável para indicar que o diálogo está aberto
        isDialogShowing = true

        // Referência ao Firebase Database
        val databaseRef = FirebaseDatabase.getInstance().reference.child("data")

        // Lista para armazenar os dados de pontuação
        val scoreList = mutableListOf<ScoreItem>()

        // Obter a ListView do layout do diálogo
        val listView = dialogView.findViewById<ListView>(R.id.score_list_view)

        // Primeiro, buscamos o username do dispositivo atual
        val currentDeviceId = deviceId // Assumindo que você já tem o deviceId definido
        databaseRef.child(currentDeviceId).child("username").get()
            .addOnSuccessListener { snapshot ->
                val currentUsername = snapshot.getValue(String::class.java) ?: "Unknown"

                // Adiciona um listener para ler os dados de pontuação
                databaseRef.addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        for (userSnapshot in snapshot.children) {
                            // Ler os dados de cada usuário
                            val username =
                                userSnapshot.child("username").getValue(String::class.java)
                                    ?: "Unknown"
                            val score =
                                userSnapshot.child("click_count").getValue(Int::class.java) ?: 0

                            // Adiciona o item à lista de pontuação
                            scoreList.add(
                                ScoreItem(
                                    username,
                                    score,
                                    0
                                )
                            )  // O ranking será atribuído depois
                        }

                        // Ordena a lista de pontuação do maior para o menor e atribui o ranking
                        scoreList.sortByDescending { it.score }
                        scoreList.forEachIndexed { index, scoreItem ->
                            scoreItem.ranking = index + 1  // Define o ranking
                        }

                        // Configura o adaptador da ListView com o contexto correto
                        val adapter = ScoreAdapter(this@MainActivity, scoreList, currentUsername)
                        listView.adapter = adapter

                        // Exibe o diálogo após carregar os dados
                        dialog.show()
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Log.e("Firebase", "Erro ao buscar dados: ${error.message}")
                    }
                })

                dialog.setOnDismissListener {
                    // Atualiza a variável quando o diálogo é fechado
                    isDialogShowing = false
                }
            }
    }

    // Classe ScoreAdapter para gerenciar a exibição dos itens na ListView
    class ScoreAdapter(
        private val context: Context,
        private val scores: List<ScoreItem>,
        private val currentUsername: String // Adicione esta propriedade
    ) : BaseAdapter() {

        override fun getCount(): Int {
            return scores.size
        }

        override fun getItem(position: Int): Any {
            return scores[position]
        }

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        @SuppressLint("SetTextI18n")
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view: View = convertView ?: LayoutInflater.from(context)
                .inflate(R.layout.list_item_score, parent, false)

            // Obter as referências dos TextViews do layout
            val rankingText = view.findViewById<TextView>(R.id.ranking)
            val usernameText = view.findViewById<TextView>(R.id.username)
            val scoreText = view.findViewById<TextView>(R.id.score)

            // Obter o ScoreItem atual
            val scoreItem = getItem(position) as ScoreItem

            // Definir os valores nos TextViews
            rankingText.text = scoreItem.ranking.toString()
            usernameText.text = scoreItem.username
            scoreText.text = scoreItem.score.toString()

            // Verifica se o item pertence ao usuário atual
            if (scoreItem.username == currentUsername) {
                usernameText.text = "you - ${scoreItem.username}"

            }

            return view
        }
    }

    // Função para verificar a conexão com a internet
    private fun checkInternetConnection(): Boolean {
        return try {
            val url = URL("https://clients3.google.com/generate_204")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 3000 // Tempo limite de conexão
            connection.connect()
            connection.responseCode == 204
        } catch (e: Exception) {
            false
        }
    }

}


