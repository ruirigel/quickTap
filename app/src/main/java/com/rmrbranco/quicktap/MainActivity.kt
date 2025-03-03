package com.rmrbranco.quicktap

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime

class MainActivity : AppCompatActivity() {

    private var clickCount = 0  // Variável para armazenar o número de cliques
    private lateinit var countDownTimer: CountDownTimer  // Declaração do timer
    private var isTimerRunning = false  // Flag para verificar se o timer está ativo
    private lateinit var database: DatabaseReference  // Referência ao Firebase Database
    val currentTimeMillis = System.currentTimeMillis() // Obtém o tempo atual em milissegundos
    val ldt = LocalDateTime.now().toString() // Obtém a data e hora atual
    private lateinit var deviceId: String  // Declare a variável deviceId
    private var isDialogShowing = false // Flag para verificar se o diálogo está sendo exibido
    private var isAnimating = true // Controle da animação
    private var isProgressAnimating = false // Controle da animação
    private var isSharing = false // Flag para evitar múltiplos toques

    @SuppressLint("SetTextI18n")
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

        // Autenticação anônima
        signInAnonymously()

        // Referência ao Firebase Database
        database = FirebaseDatabase.getInstance().reference

        val button1 = findViewById<Button>(R.id.button1) // Botão para incrementar o contador
        val button2 = findViewById<Button>(R.id.button2) // Botão para mostrar o score board
        val button3 = findViewById<Button>(R.id.button3) // Botão para reiniciar o contador
        val button4 = findViewById<Button>(R.id.button4) // Botão para compartilhar o score
        val textView1 = findViewById<TextView>(R.id.textView1)  // Exibe o número de cliques
        val textView2 = findViewById<TextView>(R.id.textView2)  // Exibe a contagem decrescente

        // Encontre a View uma vez e reutilize a variável
        val borderView = findViewById<View>(R.id.borderView)

        // Animação do progresso
        val progressAnimation = AnimationUtils.loadAnimation(this, R.anim.progress_animation)
        isProgressAnimating = false

        // Animação no aro (usando a mesma borderView)
        val pulseAnimation = AnimationUtils.loadAnimation(this, R.anim.pulse_animation)
        borderView.startAnimation(pulseAnimation)
        isAnimating = true

        // Obter o ID único do dispositivo
        deviceId = retrieveDeviceId()  // Inicializa a variável deviceId

        // Função para inicializar o timer
        fun initializeTimer() {
            countDownTimer = object :
                CountDownTimer(100000, 1000) {  // 100 segundos, decrementando a cada 1 segundo
                @SuppressLint("SetTextI18n")
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
                    button1.setText(R.string.end)

                    // Cancela a animação do progresso
                    borderView.clearAnimation()
                    borderView.visibility = View.GONE
                    isAnimating = false
                }
            }
        }

        // Inicializa o timer na primeira execução
        initializeTimer()

        // Configura o clique do botão de incremento
        button1.setOnClickListener {

            // Verifica se a animação do aro está em execução
            if (isAnimating) {

                // Cancela o animacao do aro
                borderView.clearAnimation()
                borderView.visibility = View.GONE
                isAnimating = false

                // Inicia a animação do progresso
                borderView.startAnimation(progressAnimation)
                isProgressAnimating = true

                // Atualiza o texto do botão para "Tap"
                button1.setText(R.string.tap)
            }

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
            CoroutineScope(Dispatchers.Main).launch {
                val isConnected = withContext(Dispatchers.IO) {
                    checkInternetConnection()
                }
                if (isConnected) {
                    showScoreBoardDialog()
                } else {
                    @Suppress("KotlinConstantConditions")
                    Toast.makeText(
                        this@MainActivity,
                        "Conexão à internet: $isConnected",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        // Configura o clique do botão de reinício
        button3.setOnClickListener {

            // Reinicia a animação do aro
            if (!isAnimating) {

                // Cancela a animação do progresso
                borderView.clearAnimation()
                borderView.visibility = View.GONE
                isProgressAnimating = false

                borderView.startAnimation(pulseAnimation)
                isAnimating = true
                button1.setText(R.string.go)
            }

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

        button4.setOnClickListener {
            fetchUserScoreAndShare(this, deviceId)
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
                        dialog.window?.setLayout(
                            (resources.displayMetrics.widthPixels * 0.9).toInt()
                                .coerceIn(600, 1100), // Mínimo 600px, Máximo 900px
                            1500 // Altura fixa
                        )
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
        private val currentUsername: String, // Adicione esta propriedade
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
            connection.connectTimeout = 3000
            connection.readTimeout = 3000
            connection.connect()
            connection.responseCode == 204
        } catch (e: Exception) {
            Log.e("NetworkCheck", "Erro ao verificar conexão: ${e.message}")
            false
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun shareScoreImage(context: Context, username: String, score: Int) {
        if (isSharing) return

        isSharing = true

        val originalWidth = 834
        val originalHeight = 834

        // Criar uma nova imagem maior (ex: 1200x1200) para evitar corte na preview
        val newSize = 1200
        val bitmapWithPadding = Bitmap.createBitmap(newSize, newSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmapWithPadding)

        // Preencher com um fundo transparente
        canvas.drawColor(Color.TRANSPARENT)

        // Carregar e desenhar a imagem original no centro
        val originalBitmap =
            Bitmap.createBitmap(originalWidth, originalHeight, Bitmap.Config.ARGB_8888)
        val originalCanvas = Canvas(originalBitmap)

        val backgroundBitmap =
            BitmapFactory.decodeResource(context.resources, R.drawable.background)
        val scaledBackground =
            Bitmap.createScaledBitmap(backgroundBitmap, originalWidth, originalHeight, true)
        originalCanvas.drawBitmap(scaledBackground, 0f, 0f, null)

        // Adicionar o ícone da app
        ContextCompat.getDrawable(context, R.mipmap.ic_launcher)?.let { appIcon ->
            appIcon.setBounds(30, 30, 150, 150)
            appIcon.draw(originalCanvas)
        }

        val paint = Paint().apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = 120f
        }

        // Desenhar textos no canvas
        // paint.textSize = 50f
        // originalCanvas.drawText(username, originalWidth / 2f, 225f, paint)
        // originalCanvas.drawText("My QuickTap score:", originalWidth / 2f, 290f, paint)

        // Escrever o Score
        originalCanvas.drawText("$score", originalWidth / 2f, 390f, paint)
        originalCanvas.drawText("Taps!", originalWidth / 2f, 510f, paint)

        // Agora desenhamos a imagem original no centro do bitmap maior
        val left = (newSize - originalWidth) / 2f
        val top = (newSize - originalHeight) / 2f
        canvas.drawBitmap(originalBitmap, left, top, null)

        // Salvar no MediaStore
        val imageUri = saveImageToMediaStore(context, bitmapWithPadding)

        if (imageUri != null) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, imageUri)
                putExtra(Intent.EXTRA_TITLE, "QuickTap - Share Score")
                putExtra(Intent.EXTRA_TEXT, "Can you beat me?\nQuickTap https://shorturl.at/kntDf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(Intent.createChooser(shareIntent, "Share via"))

            Handler(Looper.getMainLooper()).postDelayed({
                isSharing = false
            }, 1000)

        } else {
            Toast.makeText(context, "Erro ao salvar imagem", Toast.LENGTH_SHORT).show()
            isSharing = false
        }
    }

    private fun saveImageToMediaStore(context: Context, bitmap: Bitmap): Uri? {
        val fileName = "QuickTap_score.png" // Sempre o mesmo nome

        val contentResolver = context.contentResolver

        // Verificar se a imagem já existe e excluir antes de salvar a nova
        val existingUri = contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID),
            "${MediaStore.Images.Media.DISPLAY_NAME} = ?",
            arrayOf(fileName),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
            } else {
                null
            }
        }

        existingUri?.let { contentResolver.delete(it, null, null) }

        // Inserir a nova imagem
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/QuickTap")
        }

        val uri =
            contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        uri?.let {
            contentResolver.openOutputStream(it)?.use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            }
        }

        return uri
    }

    private fun fetchUserScoreAndShare(context: Context, deviceId: String) {
        val recordRef = database.child("data/$deviceId")

        recordRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val username =
                        snapshot.child("username").getValue(String::class.java) ?: "Guest"
                    val score = snapshot.child("click_count").getValue(Int::class.java) ?: 0

                    // Agora chamamos a função para gerar e compartilhar a imagem
                    shareScoreImage(context, username, score)
                } else {
                    Toast.makeText(context, "No score found for this device!", Toast.LENGTH_SHORT)
                        .show()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("Firebase", "Error retrieving data: ${error.message}")
            }
        })
    }

    // Autenticação anônima
    private fun signInAnonymously() {
        val auth = FirebaseAuth.getInstance()
        auth.signInAnonymously()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null) {
                        // Usuário autenticado com sucesso, você pode acessar o banco de dados agora
                        val userId = user.uid
                        Log.d("Firebase", "User authenticated successfully. UID: $userId")
                    }
                } else {
                    Log.e("Firebase", "Authentication error: ${task.exception?.message}")
                }
            }
    }


}


