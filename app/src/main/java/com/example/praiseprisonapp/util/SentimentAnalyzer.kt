package com.example.praiseprisonapp.util

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class SentimentAnalyzer private constructor(context: Context) {
    private var interpreter: Interpreter? = null
    private var vocabulary: Map<String, Int> = emptyMap()
    private val maxLength = 128
    private val unkToken = "[UNK]"
    private val clsToken = "[CLS]"
    private val sepToken = "[SEP]"
    private val padToken = "[PAD]"
    private val subwordPrefix = "##"

    // KLUE-BERT 감정 레이블 그룹
    enum class EmotionGroup {
        ANGER,      // 분노 그룹
        SADNESS,    // 슬픔 그룹
        ANXIETY,    // 불안 그룹
        HURT,       // 상처 그룹
        EMBARRASSMENT, // 당황 그룹
        JOY,        // 기쁨 그룹
        NEUTRAL     // 중립
    }

    // 감정 그룹 한글 이름
    private val emotionGroupNames = mapOf(
        EmotionGroup.ANGER to "분노",
        EmotionGroup.SADNESS to "슬픔",
        EmotionGroup.ANXIETY to "불안",
        EmotionGroup.HURT to "상처",
        EmotionGroup.EMBARRASSMENT to "당황",
        EmotionGroup.JOY to "기쁨",
        EmotionGroup.NEUTRAL to "중립"
    )

    // 감정 레이블과 그룹 매핑
    private val emotionLabels = listOf(
        // 분노 그룹
        "분노", "툴툴대는", "좌절한", "짜증내는", "방어적인", "악의적인", "안달하는", "구역질 나는", "노여워하는", "성가신",
        // 슬픔 그룹
        "슬픔", "실망한", "비통한", "후회되는", "우울한", "마비된", "염세적인", "눈물이 나는", "낙담한", "환멸을 느끼는",
        // 불안 그룹
        "불안", "두려운", "스트레스 받는", "취약한", "혼란스러운", "당혹스러운", "회의적인", "걱정스러운", "조심스러운", "초조한",
        // 상처 그룹
        "상처", "질투하는", "배신당한", "고립된", "충격 받은", "가난한 불우한", "희생된", "억울한", "괴로워하는", "버려진",
        // 당황 그룹
        "당황", "고립된(당황한)", "남의 시선을 의식하는", "외로운", "열등감", "죄책감의", "부끄러운", "혐오스러운", "한심한", "혼란스러운(당황한)",
        // 기쁨 그룹
        "기쁨", "감사하는", "신뢰하는", "편안한", "만족스러운", "흥분", "느긋", "안도", "신이 난", "자신하는"
    )

    private val emotionToGroup = mapOf(
        // 분노 그룹
        "분노" to EmotionGroup.ANGER, "툴툴대는" to EmotionGroup.ANGER, "좌절한" to EmotionGroup.ANGER,
        "짜증내는" to EmotionGroup.ANGER, "방어적인" to EmotionGroup.ANGER, "악의적인" to EmotionGroup.ANGER,
        "안달하는" to EmotionGroup.ANGER, "구역질 나는" to EmotionGroup.ANGER, "노여워하는" to EmotionGroup.ANGER,
        "성가신" to EmotionGroup.ANGER,

        // 슬픔 그룹
        "슬픔" to EmotionGroup.SADNESS, "실망한" to EmotionGroup.SADNESS, "비통한" to EmotionGroup.SADNESS,
        "후회되는" to EmotionGroup.SADNESS, "우울한" to EmotionGroup.SADNESS, "마비된" to EmotionGroup.SADNESS,
        "염세적인" to EmotionGroup.SADNESS, "눈물이 나는" to EmotionGroup.SADNESS, "낙담한" to EmotionGroup.SADNESS,
        "환멸을 느끼는" to EmotionGroup.SADNESS,

        // 불안 그룹
        "불안" to EmotionGroup.ANXIETY, "두려운" to EmotionGroup.ANXIETY, "스트레스 받는" to EmotionGroup.ANXIETY,
        "취약한" to EmotionGroup.ANXIETY, "혼란스러운" to EmotionGroup.ANXIETY, "당혹스러운" to EmotionGroup.ANXIETY,
        "회의적인" to EmotionGroup.ANXIETY, "걱정스러운" to EmotionGroup.ANXIETY, "조심스러운" to EmotionGroup.ANXIETY,
        "초조한" to EmotionGroup.ANXIETY,

        // 상처 그룹
        "상처" to EmotionGroup.HURT, "질투하는" to EmotionGroup.HURT, "배신당한" to EmotionGroup.HURT,
        "고립된" to EmotionGroup.HURT, "충격 받은" to EmotionGroup.HURT, "가난한 불우한" to EmotionGroup.HURT,
        "희생된" to EmotionGroup.HURT, "억울한" to EmotionGroup.HURT, "괴로워하는" to EmotionGroup.HURT,
        "버려진" to EmotionGroup.HURT,

        // 당황 그룹
        "당황" to EmotionGroup.EMBARRASSMENT, "고립된(당황한)" to EmotionGroup.EMBARRASSMENT,
        "남의 시선을 의식하는" to EmotionGroup.EMBARRASSMENT, "외로운" to EmotionGroup.EMBARRASSMENT,
        "열등감" to EmotionGroup.EMBARRASSMENT, "죄책감의" to EmotionGroup.EMBARRASSMENT,
        "부끄러운" to EmotionGroup.EMBARRASSMENT, "혐오스러운" to EmotionGroup.EMBARRASSMENT,
        "한심한" to EmotionGroup.EMBARRASSMENT, "혼란스러운(당황한)" to EmotionGroup.EMBARRASSMENT,

        // 기쁨 그룹
        "기쁨" to EmotionGroup.JOY, "감사하는" to EmotionGroup.JOY, "신뢰하는" to EmotionGroup.JOY,
        "편안한" to EmotionGroup.JOY, "만족스러운" to EmotionGroup.JOY, "흥분" to EmotionGroup.JOY,
        "느긋" to EmotionGroup.JOY, "안도" to EmotionGroup.JOY, "신이 난" to EmotionGroup.JOY,
        "자신하는" to EmotionGroup.JOY
    )

    data class EmotionResult(
        val emotion: String,          // 구체적인 감정 레이블
        val emotionGroup: EmotionGroup, // 감정 그룹
        val confidence: Float,        // 신뢰도
        val needsAttention: Boolean   // 주의가 필요한지 여부
    )

    init {
        try {
            Log.d(TAG, "감정 분석기 초기화 중...")
            interpreter = loadModelFile(context)
            vocabulary = loadVocabularyFromJson(context)
            Log.d(TAG, "감정 분석기 초기화 완료")
        } catch (e: Exception) {
            Log.e(TAG, "감정 분석기 초기화 중 오류 발생", e)
        }
    }

    fun analyze(text: String): EmotionResult {
        if (interpreter == null || vocabulary.isEmpty()) {
            Log.e(TAG, "모델 또는 어휘 사전이 초기화되지 않음")
            return EmotionResult("", EmotionGroup.NEUTRAL, 0f, false)
        }

        try {
            val tokens = tokenize(text)
            
            val inputIds = IntArray(maxLength) { tokens[it] }
            val attentionMask = IntArray(maxLength) { if (tokens[it] != (vocabulary[padToken] ?: 0)) 1 else 0 }
            val tokenTypeIds = IntArray(maxLength) { 0 }

            val inputBuffer = ByteBuffer.allocateDirect(maxLength * 4)
                .order(ByteOrder.nativeOrder())
                .asIntBuffer()
                .put(inputIds)
                .rewind()

            val attentionBuffer = ByteBuffer.allocateDirect(maxLength * 4)
                .order(ByteOrder.nativeOrder())
                .asIntBuffer()
                .put(attentionMask)
                .rewind()

            val tokenTypeBuffer = ByteBuffer.allocateDirect(maxLength * 4)
                .order(ByteOrder.nativeOrder())
                .asIntBuffer()
                .put(tokenTypeIds)
                .rewind()

            val inputs = arrayOf<Any>(inputBuffer, attentionBuffer, tokenTypeBuffer)
            val outputBuffer = ByteBuffer.allocateDirect(emotionLabels.size * 4)
                .order(ByteOrder.nativeOrder())
            val outputs = mutableMapOf<Int, Any>(0 to outputBuffer)

            interpreter?.runForMultipleInputsOutputs(inputs, outputs)

            val results = FloatArray(emotionLabels.size)
            outputBuffer.rewind()
            outputBuffer.asFloatBuffer().get(results)

            val exp = results.map { Math.exp(it.toDouble()).toFloat() }
            val softmax = exp.map { it / exp.sum() }

            val maxIndex = softmax.indices.maxByOrNull { softmax[it] } ?: 0
            val emotion = emotionLabels[maxIndex]
            val emotionGroup = emotionToGroup[emotion] ?: EmotionGroup.NEUTRAL
            val confidence = softmax[maxIndex]

            val result = EmotionResult(
                emotion = emotion,
                emotionGroup = emotionGroup,
                confidence = confidence,
                needsAttention = emotionGroup != EmotionGroup.JOY && confidence > 0.1
            )

            Log.d(TAG, "분석 완료. 감정: ${result.emotion}, 신뢰도: ${String.format("%.1f", result.confidence * 100)}%")
            return result
        } catch (e: Exception) {
            Log.e(TAG, "감정 분석 중 오류 발생", e)
            return EmotionResult("", EmotionGroup.NEUTRAL, 0f, false)
        }
    }

    private fun tokenize(text: String): IntArray {
        if (vocabulary.isEmpty()) {
            Log.e(TAG, "어휘 사전이 비어있음")
            return IntArray(maxLength)
        }

        val tokens = mutableListOf<Int>()
        tokens.add(vocabulary[clsToken] ?: 2)

        val words = text.trim().split(" ")
        for (word in words) {
            var start = 0
            while (start < word.length) {
                var end = word.length
                var matched: String? = null

                while (start < end) {
                    val sub = if (start == 0) {
                        word.substring(start, end)
                    } else {
                        "$subwordPrefix${word.substring(start, end)}"
                    }
                    if (vocabulary.containsKey(sub)) {
                        matched = sub
                        break
                    }
                    end--
                }

                if (matched != null) {
                    tokens.add(vocabulary[matched]!!)
                    start += if (matched.startsWith(subwordPrefix)) matched.length - 2 else matched.length
                } else {
                    tokens.add(vocabulary[unkToken] ?: 1)
                    start++
                }
            }
        }

        tokens.add(vocabulary[sepToken] ?: 3)
        while (tokens.size < maxLength) {
            tokens.add(vocabulary[padToken] ?: 0)
        }
        return tokens.take(maxLength).toIntArray()
    }

    private fun loadModelFile(context: Context): Interpreter? {
        return try {
            val fileDescriptor = context.assets.openFd("sentiment_model_improved.tflite")
            val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = fileDescriptor.startOffset
            val declaredLength = fileDescriptor.declaredLength
            val mappedByteBuffer = fileChannel.map(
                FileChannel.MapMode.READ_ONLY,
                startOffset,
                declaredLength
            )
            Interpreter(mappedByteBuffer)
        } catch (e: Exception) {
            Log.e(TAG, "모델 파일 로드 실패", e)
            null
        }
    }

    private fun loadVocabularyFromJson(context: Context): Map<String, Int> {
        return try {
            val jsonStr = context.assets.open("tokenizer.json").bufferedReader().use { it.readText() }
            val root = Gson().fromJson(jsonStr, JsonObject::class.java)
            val vocabObj = root.getAsJsonObject("model").getAsJsonObject("vocab")
            val vocabMap = mutableMapOf<String, Int>()
            
            for ((token, value) in vocabObj.entrySet()) {
                vocabMap[token] = value.asInt
            }
            
            Log.d(TAG, "어휘 사전 로드 완료: ${vocabMap.size}개 토큰")
            vocabMap
        } catch (e: Exception) {
            Log.e(TAG, "어휘 사전 로드 실패", e)
            emptyMap()
        }
    }

    companion object {
        private const val TAG = "감정분석기"
        
        @Volatile
        private var instance: SentimentAnalyzer? = null

        fun getInstance(context: Context): SentimentAnalyzer {
            return instance ?: synchronized(this) {
                instance ?: SentimentAnalyzer(context).also { instance = it }
            }
        }
    }
} 