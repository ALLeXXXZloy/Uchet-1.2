package uz.uchet

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.launch
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TxDialog(
    initial: Tx?,
    cards: List<CardEntity>,
    expenseCategories: List<String>,
    incomeCategories: List<String>,
    currentLang: AppLanguage,
    isPendingSms: Boolean = false,
    autoStartVoice: Boolean = false,
    rates: List<ExchangeRateEntity>,
    vm: VM,
    onDismiss: () -> Unit,
    onSave: (Tx) -> Unit,
    onDelete: (() -> Unit)?
) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val initDt = initial?.let { Instant.ofEpochMilli(it.timestamp).atZone(zone) } ?: ZonedDateTime.now(zone)

    var type by remember { mutableStateOf(initial?.type ?: TxType.EXPENSE) }
    var category by remember { mutableStateOf(initial?.category ?: expenseCategories.lastOrNull() ?: "📦 Другое") }
    var amount by remember { mutableStateOf(initial?.let { BigDecimal(it.amountMinor).movePointLeft(2).stripTrailingZeros().toPlainString() } ?: "") }
    var currency by remember { mutableStateOf(initial?.currency ?: "UZS") }
    var selectedCardId by remember { mutableStateOf(initial?.cardId ?: "CASH") }
    var toCardId by remember { mutableStateOf(initial?.toCardId) }
    var toAmount by remember { mutableStateOf(initial?.toAmountMinor?.let { BigDecimal(it).movePointLeft(2).stripTrailingZeros().toPlainString() } ?: "") }

    var isCustomCard by remember { mutableStateOf(initial?.cardId?.let { !cards.map { c -> c.id }.contains(it) } ?: false) }
    var desc by remember { mutableStateOf(initial?.description ?: "") }
    var date by remember { mutableStateOf(initDt.format(DATE_FMT)) }
    var photoPath by remember { mutableStateOf(initial?.photoPath) }
    var error by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val t = { key: String -> Translations.t(key, currentLang) }

    var showTypeMenu by remember { mutableStateOf(false) }
    var showCategoryMenu by remember { mutableStateOf(false) }
    var showCardMenu by remember { mutableStateOf(false) }
    var showToCardMenu by remember { mutableStateOf(false) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var showAddCardDialog by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }

    var newCardName by remember { mutableStateOf("") }
    var newCardLast4 by remember { mutableStateOf("") }
    var newCardCurrency by remember { mutableStateOf("UZS") }

    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }
    
    val fromCardCurrency = cards.find { it.id == selectedCardId }?.currency ?: "UZS"
    val toCardCurrency = cards.find { it.id == toCardId }?.currency ?: "UZS"
    val isCurrencyDiff = type == TxType.TRANSFER && fromCardCurrency != toCardCurrency

    // ----- ГОЛОСОВОЙ ВВОД -----
    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                val parsed = VoiceParser.parseTransaction(spoken, expenseCategories, incomeCategories)
                parsed.type?.let { type = it }
                parsed.amountMinor?.let { amount = BigDecimal(it).movePointLeft(2).stripTrailingZeros().toPlainString() }
                if (parsed.isCash) selectedCardId = "CASH"
                else parsed.cardLast4?.let { l4 -> cards.find { it.last4 == l4 }?.let { selectedCardId = it.id } }
                parsed.categoryName?.let { category = it }
                if (parsed.description.isNotBlank()) desc = parsed.description
            }
        } else {
            scope.launch { snackbarHostState.showSnackbar(t("voice_error")) }
        }
    }

    fun startVoiceInput() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (currentLang == AppLanguage.UZ) "uz-UZ" else "ru-RU")
            putExtra(RecognizerIntent.EXTRA_PROMPT, t("voice_prompt"))
        }
        try {
            voiceLauncher.launch(intent)
        } catch (e: Exception) {
            scope.launch { snackbarHostState.showSnackbar(t("voice_not_available")) }
        }
    }

    LaunchedEffect(Unit) {
        if (autoStartVoice && !isPendingSms) startVoiceInput()
    }

    // ----- ОБРАБОТКА QR ИЗ ГАЛЕРЕИ/КАМЕРЫ -----
    fun processQrImage(bitmap: Bitmap?) {
        if (bitmap == null) return
        isLoading = true
        val image = InputImage.fromBitmap(bitmap, 0)
        val scanner = BarcodeScanning.getClient()
        
        scanner.process(image).addOnSuccessListener { barcodes ->
            isLoading = false
            if (barcodes.isNotEmpty()) {
                val rawValue = barcodes[0].rawValue ?: ""
                val qrData = QrReceiptParser.parse(rawValue)
                if (qrData != null && qrData.amountMinor != null) {
                    // Успешно распознали фискальный QR!
                    amount = BigDecimal(qrData.amountMinor).movePointLeft(2).stripTrailingZeros().toPlainString()
                    qrData.dateMillis?.let {
                        date = Instant.ofEpochMilli(it).atZone(zone).toLocalDate().format(DATE_FMT)
                    }
                    if (!qrData.merchant.isNullOrBlank()) desc = qrData.merchant
                    if (!qrData.receiptNo.isNullOrBlank() && desc.isBlank()) desc = "Чек №${qrData.receiptNo}"
                    category = expenseCategories.find { it.contains("Покупки", true) } ?: category
                    
                    scope.launch { snackbarHostState.showSnackbar("✅ Сумма и дата извлечены из QR-кода") }
                } else {
                    // QR есть, но не фискальный (например, просто ссылка на сайт)
                    desc = "QR: ${rawValue.take(30)}"
                    scope.launch { snackbarHostState.showSnackbar("QR-код не является фискальным чеком") }
                }
            } else {
                scope.launch { snackbarHostState.showSnackbar("QR-код не найден на фото") }
            }
        }.addOnFailureListener { 
            isLoading = false 
            scope.launch { snackbarHostState.showSnackbar(t("error")) }
        }
    }

    // ----- ОБРАБОТКА ФОТО (ЧЕКИ И СКРИНЫ) -----
    fun processReceiptImage(bitmap: Bitmap?) {
        if (bitmap == null) return
        isLoading = true
        
        val image = InputImage.fromBitmap(bitmap, 0)
        
        // Сначала пытаемся найти QR-код на фото!
        val scanner = BarcodeScanning.getClient()
        scanner.process(image).addOnSuccessListener { barcodes ->
            var foundAmountMinor: Long? = null
            
            if (barcodes.isNotEmpty()) {
                val rawValue = barcodes[0].rawValue ?: ""
                val qrData = QrReceiptParser.parse(rawValue)
                if (qrData != null && qrData.amountMinor != null) {
                    foundAmountMinor = qrData.amountMinor
                    qrData.dateMillis?.let {
                        val localDate = Instant.ofEpochMilli(it).atZone(zone).toLocalDate()
                        date = localDate.format(DATE_FMT)
                    }
                    if (!qrData.merchant.isNullOrBlank()) desc = qrData.merchant
                    if (!qrData.receiptNo.isNullOrBlank() && desc.isBlank()) desc = "Чек №${qrData.receiptNo}"
                    category = expenseCategories.find { it.contains("Покупки", true) } ?: category
                }
            }

            // Если QR не дал результата, включаем OCR
            if (foundAmountMinor == null) {
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                recognizer.process(image).addOnSuccessListener { visionText ->
                    val rawText = visionText.text
                    
                    // 1) Специализированный парсер бумажных чеков
                    val receipt = ReceiptParser.parse(rawText)
                    if (receipt.amountMinor != null) {
                        foundAmountMinor = receipt.amountMinor
                        receipt.sellerName?.let { desc = it }
                        receipt.dateMillis?.let {
                            val localDate = Instant.ofEpochMilli(it).atZone(zone).toLocalDate()
                            date = localDate.format(DATE_FMT)
                        }
                        category = expenseCategories.find { it.contains("Покупки", true) } ?: category
                    }
                    
                    // 2) Fallback: банковский скрин / SMS-подобный текст
                    if (foundAmountMinor == null) {
                        val parsed = SmsParser.parseForOcr(rawText)
                        if (parsed != null) {
                            val amt = runCatching {
                                BigDecimal(parsed.amountStr.replace(" ", "").replace(',', '.'))
                                    .movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()
                            }.getOrNull()
                            if (amt != null && amt > 0) {
                                foundAmountMinor = amt
                                if (parsed.description.isNotBlank()) desc = parsed.description
                            }
                        }
                    }
                    
                    // 3) Финальный fallback: самое крупное число в нижней части текста
                    if (foundAmountMinor == null) {
                        foundAmountMinor = ReceiptParser.extractBiggestAmount(rawText)
                    }

                    if (foundAmountMinor != null) {
                        amount = BigDecimal(foundAmountMinor).movePointLeft(2).stripTrailingZeros().toPlainString()
                    } else {
                        scope.launch { snackbarHostState.showSnackbar("Не удалось распознать сумму на фото") }
                    }
                    
                    // Сохраняем фото
                    val file = File(context.filesDir, "images/${UUID.randomUUID()}.jpg")
                    file.parentFile?.mkdirs()
                    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                    photoPath = file.absolutePath
                    isLoading = false
                }.addOnFailureListener { 
                    isLoading = false 
                    scope.launch { snackbarHostState.showSnackbar(t("error")) }
                }
            } else {
                // Сумма найдена через QR, OCR не нужен
                amount = BigDecimal(foundAmountMinor).movePointLeft(2).stripTrailingZeros().toPlainString()
                val file = File(context.filesDir, "images/${UUID.randomUUID()}.jpg")
                file.parentFile?.mkdirs()
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                photoPath = file.absolutePath
                isLoading = false
            }
        }.addOnFailureListener {
            isLoading = false
            scope.launch { snackbarHostState.showSnackbar(t("error")) }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.TakePicture()) { success ->
        if (success && tempCameraUri != null) {
            processReceiptImage(uriToBitmap(context, tempCameraUri!!))
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { processReceiptImage(uriToBitmap(context, it)) }
    }

    // Отдельная кнопка QR (сканирует картинку только на наличие QR)
    val qrLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { processQrImage(uriToBitmap(context, it)) }
    }

    // ----- ДИАЛОГИ ДОБАВЛЕНИЯ -----
    LaunchedEffect(type) {
        val currentList = if (type == TxType.INCOME) incomeCategories else expenseCategories
        if (!currentList.contains(category) && !showAddCategoryDialog) category = currentList.lastOrNull() ?: "📦 Другое"
    }

    if (showAddCategoryDialog) {
        AlertDialog(
            onDismissRequest = { showAddCategoryDialog = false },
            title = { Text(t("new_cat")) },
            text = { OutlinedTextField(value = newCategoryName, onValueChange = { newCategoryName = it }, label = { Text(t("name")) }) },
            confirmButton = {
                TextButton(onClick = {
                    if (newCategoryName.isNotBlank()) {
                        val newCat = CategoryEntity(name = newCategoryName.trim(), type = if (type == TxType.INCOME) TxType.INCOME else TxType.EXPENSE)
                        vm.saveCategory(newCat)
                        category = newCat.name
                        newCategoryName = ""
                        showAddCategoryDialog = false
                    }
                }) { Text(t("add")) }
            },
            dismissButton = { TextButton(onClick = { showAddCategoryDialog = false }) { Text(t("cancel")) } }
        )
    }

    if (showAddCardDialog) {
        AlertDialog(
            onDismissRequest = { showAddCardDialog = false },
            title = { Text(t("new_card")) },
            text = {
                Column {
                    OutlinedTextField(value = newCardName, onValueChange = { newCardName = it }, label = { Text(t("card_name")) }, singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = newCardLast4, onValueChange = { newCardLast4 = it.filter(Char::isDigit).take(4) }, label = { Text(t("last4")) }, singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    Box {
                        var expanded by remember { mutableStateOf(false) }
                        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text("${t("currency")}: $newCardCurrency") }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            listOf("UZS", "USD", "EUR", "RUB").forEach { ccy ->
                                DropdownMenuItem(text = { Text(ccy) }, onClick = { newCardCurrency = ccy; expanded = false })
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newCardName.isNotBlank() && newCardLast4.isNotBlank()) {
                        val id = "${newCardLast4}_${newCardCurrency}"
                        vm.saveCard(CardEntity(id = id, last4 = newCardLast4, name = newCardName, currency = newCardCurrency, initialBalanceMinor = 0))
                        selectedCardId = id
                        isCustomCard = false
                        newCardName = ""
                        newCardLast4 = ""
                        showAddCardDialog = false
                    }
                }) { Text(t("add")) }
            },
            dismissButton = { TextButton(onClick = { showAddCardDialog = false }) { Text(t("cancel")) } }
        )
    }

    // ----- ОСНОВНОЙ ДИАЛОГ -----
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(when { isPendingSms -> t("sms_title"); initial == null -> t("new_tx"); else -> t("edit") }) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                if (isPendingSms) { 
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) { 
                        Column(Modifier.padding(12.dp)) { 
                            Text(t("sms_data"), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Text(t("sms_title"), style = MaterialTheme.typography.bodySmall) 
                        } 
                    } 
                }

                Box(Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { showTypeMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(type.localizedLabel(currentLang))
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.Default.ArrowDropDown, null)
                    }
                    DropdownMenu(expanded = showTypeMenu, onDismissRequest = { showTypeMenu = false }) {
                        TxType.values().forEach { tt ->
                            DropdownMenuItem(text = { Text(tt.localizedLabel(currentLang)) }, onClick = { type = tt; showTypeMenu = false })
                        }
                    }
                }

                if (!isPendingSms) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            val file = File(context.filesDir, "images/${UUID.randomUUID()}.jpg")
                            file.parentFile?.mkdirs()
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            tempCameraUri = uri
                            cameraLauncher.launch(uri)
                        }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 12.dp)) {
                            Icon(Icons.Default.CameraAlt, contentDescription = t("take_photo"))
                        }

                        Button(onClick = { galleryLauncher.launch("image/*") }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 12.dp)) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = t("choose_photo"))
                        }

                        Button(onClick = { qrLauncher.launch("image/*") }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 12.dp)) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = t("scan_qr"))
                        }

                        Button(onClick = { startVoiceInput() }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)) {
                            Icon(Icons.Default.Mic, contentDescription = t("voice_input"))
                        }
                    }
                }

                if (photoPath != null) {
                    AsyncImage(model = File(photoPath!!), contentDescription = t("receipt"), modifier = Modifier.fillMaxWidth().height(120.dp), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(amount, { amount = it }, label = { Text(t("amount")) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    Box(Modifier.weight(0.5f)) {
                        var expanded by remember { mutableStateOf(false) }
                        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(currency) }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            listOf("UZS", "USD", "EUR", "RUB").forEach { ccy ->
                                DropdownMenuItem(text = { Text(ccy) }, onClick = { currency = ccy; expanded = false })
                            }
                        }
                    }
                }

                Box(Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { showCategoryMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(Translations.translateCategory(category, currentLang))
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.Default.ArrowDropDown, null)
                    }
                    DropdownMenu(expanded = showCategoryMenu, onDismissRequest = { showCategoryMenu = false }) {
                        val currentCategories = if (type == TxType.INCOME) incomeCategories else expenseCategories
                        currentCategories.forEach { c ->
                            DropdownMenuItem(text = { Text(Translations.translateCategory(c, currentLang)) }, onClick = { category = c; showCategoryMenu = false })
                        }
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text(t("add_new_cat"), color = MaterialTheme.colorScheme.primary) }, onClick = { showAddCategoryDialog = true; showCategoryMenu = false })
                    }
                }

                Box(Modifier.fillMaxWidth()) {
                    val cardName = cards.find { it.id == selectedCardId }?.let { "${it.name} (${it.currency})" } ?: t("cash")
                    OutlinedButton(onClick = { showCardMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("${t("account")}: $cardName")
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.Default.ArrowDropDown, null)
                    }
                    DropdownMenu(expanded = showCardMenu, onDismissRequest = { showCardMenu = false }) {
                        cards.forEach { c ->
                            DropdownMenuItem(text = { Text("${c.name} (${c.currency})") }, onClick = { selectedCardId = c.id; isCustomCard = false; showCardMenu = false })
                        }
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text(t("add_new_card"), color = MaterialTheme.colorScheme.primary) }, onClick = { showAddCardDialog = true; showCardMenu = false })
                    }
                }

                if (type == TxType.TRANSFER) {
                    Box(Modifier.fillMaxWidth()) {
                        val toCardName = cards.find { it.id == toCardId }?.let { "${it.name} (${it.currency})" } ?: t("other")
                        OutlinedButton(onClick = { showToCardMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("${t("to_account")}: $toCardName")
                            Spacer(Modifier.weight(1f))
                            Icon(Icons.Default.ArrowDropDown, null)
                        }
                        DropdownMenu(expanded = showToCardMenu, onDismissRequest = { showToCardMenu = false }) {
                            cards.forEach { c ->
                                DropdownMenuItem(text = { Text("${c.name} (${c.currency})") }, onClick = { toCardId = c.id; showToCardMenu = false })
                            }
                        }
                    }
                    
                    if (isCurrencyDiff) {
                        OutlinedTextField(
                            value = toAmount, 
                            onValueChange = { toAmount = it }, 
                            label = { Text("Сумма к зачислению (${toCardCurrency})") }, 
                            singleLine = true, 
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), 
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                OutlinedTextField(desc, { desc = it }, label = { Text(t("desc")) })
                OutlinedTextField(date, { date = it }, label = { Text(t("date")) }, singleLine = true)
                error?.let { Text(it, color = RED) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isLoading,
                onClick = {
                    var amountMinor = runCatching { BigDecimal(amount.replace(" ", "").replace(',', '.')).movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong() }.getOrNull()

                    if (currency != "UZS" && amountMinor != null) {
                        val rateEntity = rates.find { it.currency == currency }
                        if (rateEntity != null) {
                            amountMinor = (amountMinor.toDouble() * rateEntity.rateToUzs).toBigDecimal().setScale(0, RoundingMode.HALF_UP).toLong()
                        }
                    }
                    
                    val toAmountMinor = if (isCurrencyDiff) {
                        runCatching { BigDecimal(toAmount.replace(" ", "").replace(',', '.')).movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong() }.getOrNull()
                    } else null

                    val d = runCatching { LocalDate.parse(date.trim(), DATE_FMT) }.getOrNull()
                    when {
                        amountMinor == null || amountMinor <= 0 -> error = t("error")
                        isCurrencyDiff && (toAmountMinor == null || toAmountMinor <= 0) -> error = "Введите сумму зачисления"
                        d == null -> error = t("error")
                        type == TxType.TRANSFER && toCardId == null -> error = t("error")
                        else -> {
                            val finalCardId = if (selectedCardId.isEmpty() || selectedCardId == "CASH") null else selectedCardId
                            onSave(Tx(
                                id = initial?.id ?: 0, 
                                amountMinor = amountMinor, 
                                type = type, 
                                cardId = finalCardId, 
                                toCardId = toCardId, 
                                toAmountMinor = toAmountMinor,
                                description = desc.trim(), 
                                category = category, 
                                timestamp = d.atTime(initDt.toLocalTime()).atZone(zone).toInstant().toEpochMilli(), 
                                source = initial?.source ?: if (isPendingSms) "SMS" else "MANUAL", 
                                currency = currency, 
                                photoPath = photoPath
                            ))
                        }
                    }
                }) { Text(if (isPendingSms) t("confirm") else t("save")) }
        },
        dismissButton = { 
            Row { 
                onDelete?.let { TextButton(it) { Text(t("delete"), color = RED) } }
                TextButton(onDismiss) { Text(if (isPendingSms) t("reject") else t("cancel")) } 
            } 
        }
    )
}
