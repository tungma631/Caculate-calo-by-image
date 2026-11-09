package vn.calolens.app

import android.app.DatePickerDialog
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val Pine = Color(0xFF183E35)
private val Sage = Color(0xFFEAF0DF)
private val Lime = Color(0xFFD7EDAE)
private val Paper = Color(0xFFF7F8F4)
private val Ink = Color(0xFF202E27)
private val Muted = Color(0xFF718078)
private val Orange = Color(0xFFD78C51)
private val vi = Locale("vi", "VN")
private val dateLabel = DateTimeFormatter.ofPattern("EEE, dd/MM", vi)
private fun Double.pretty() = if (this == roundToInt().toDouble()) roundToInt().toString() else String.format(vi, "%.1f", this)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Pine, secondary = Pine, background = Paper,
                surface = Color.White, onPrimary = Color.White, onBackground = Ink, onSurface = Ink)) {
                CaloLens()
            }
        }
    }
}

@Composable
private fun CaloLens(model: AppModel = viewModel()) {
    val snack = remember { SnackbarHostState() }
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val gate: (() -> Unit) -> Unit = { action -> if (model.consent) action() else pending = action }
    LaunchedEffect(model.message) {
        model.message?.let { value -> model.message = null; snack.showSnackbar(value) }
    }
    Scaffold(containerColor = Paper, snackbarHost = { SnackbarHost(snack) }, bottomBar = {
        Surface(color = Color.White, shadowElevation = 8.dp) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceAround) {
                listOf("Hôm nay", "Nhật ký", "Kế hoạch", "Cá nhân").forEachIndexed { i, label ->
                    val selected = model.tab == i
                    Column(Modifier.clip(RoundedCornerShape(18.dp)).clickable { model.tab = i }
                        .background(if (selected) Sage else Color.Transparent).padding(horizontal = 16.dp, vertical = 9.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Glyph(i, if (selected) Pine else Muted)
                        Spacer(Modifier.height(4.dp))
                        Text(label, fontSize = 10.sp, color = if (selected) Pine else Muted, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (model.busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = Pine, trackColor = Sage)
                Text(model.progress, Modifier.padding(horizontal = 24.dp, vertical = 8.dp), color = Pine, fontSize = 12.sp)
            }
            when (model.tab) {
                0 -> HomeScreen(model, gate)
                1 -> DiaryScreen(model)
                2 -> PlanScreen(model, gate)
                else -> ProfileScreen(model)
            }
        }
    }
    if (pending != null) AlertDialog(onDismissRequest = { pending = null }, title = { Text("Cho phép xử lý bằng AI?") },
        text = { Text("Ảnh, mô tả món ăn hoặc thông tin hồ sơ cần thiết sẽ được gửi đến dịch vụ AI bên thứ ba. Ứng dụng không lưu ảnh trên backend; nhà cung cấp có thể có chính sách lưu riêng.\n\nKết nối AI hiện tại dùng HTTP chưa mã hóa. Chỉ nên dùng ảnh và hồ sơ thử nghiệm ở giai đoạn này. Bạn có thể thu hồi quyền trong Cá nhân.") },
        confirmButton = { TextButton(onClick = { val action = pending; pending = null; model.allowAI(); action?.invoke() }) { Text("Đồng ý và tiếp tục") } },
        dismissButton = { TextButton(onClick = { pending = null }) { Text("Để sau") } })
    model.error?.let { value -> AlertDialog(onDismissRequest = { model.error = null }, title = { Text("Chưa hoàn tất") },
        text = { Text(value) }, confirmButton = { TextButton(onClick = { model.error = null }) { Text("Đã hiểu") } }) }
}

@Composable
private fun PageTitle(eyebrow: String, title: String, right: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(eyebrow.uppercase(vi), color = Muted, fontSize = 10.sp, letterSpacing = 1.8.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(7.dp))
            Text(title, color = Ink, fontSize = 29.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
        }
        right()
    }
}

@Composable
private fun Panel(modifier: Modifier = Modifier, color: Color = Color.White, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(color).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

@Composable
private fun Primary(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = RoundedCornerShape(16.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        Text(label, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SmallNote(text: String) { Text(text, color = Muted, fontSize = 12.sp, lineHeight = 18.sp) }

@Composable
private fun SectionLabel(label: String, action: String? = null, onClick: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (action != null) TextButton(onClick = onClick) { Text(action, fontSize = 12.sp) }
    }
}

@Composable
private fun HomeScreen(model: AppModel, gate: (() -> Unit) -> Unit) {
    var scanning by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = scanning) { scanning = false }
    if (scanning) { ScanScreen(model, gate, { scanning = false }); return }
    val today = LocalDate.now()
    val foods = model.meals.filter { it.date == today.toString() }.flatMap { it.foods }
    val calories = foods.sumOf { it.calories }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        PageTitle("Ăn có kế hoạch · Sống cân bằng", "CaloLens") {
            Box(Modifier.size(42.dp).clip(CircleShape).background(Sage), contentAlignment = Alignment.Center) { Glyph(4, Pine) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).background(Pine, CircleShape))
            Text(today.format(DateTimeFormatter.ofPattern("EEEE, dd 'tháng' MM", vi)).replaceFirstChar { it.uppercase() },
                Modifier.padding(start = 8.dp), color = Muted, fontSize = 12.sp)
        }
        Panel(color = Pine) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("NĂNG LƯỢNG HÔM NAY", color = Lime, fontSize = 10.sp, letterSpacing = 1.3.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(calories.roundToInt().toString(), color = Color.White, fontSize = 47.sp, fontWeight = FontWeight.Bold, letterSpacing = (-2).sp)
                    Text("/ ${model.profile.dailyCalories} kcal mục tiêu", color = Color(0xFFBBCFC1), fontSize = 12.sp)
                }
                Box(Modifier.size(92.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawArc(Color(0xFF426054), -90f, 360f, false, style = Stroke(8.dp.toPx(), cap = StrokeCap.Round))
                        drawArc(Lime, -90f, (calories / model.profile.dailyCalories).coerceIn(0.0, 1.0).toFloat() * 360f,
                            false, style = Stroke(8.dp.toPx(), cap = StrokeCap.Round))
                    }
                    Text("${(calories * 100 / model.profile.dailyCalories).roundToInt()}%", color = Lime, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            }
            Divider(color = Color(0xFF426054), modifier = Modifier.padding(vertical = 6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Macro("Protein", foods.sumOf { it.protein }, Color(0xFFDFC7A3), true)
                Macro("Carbs", foods.sumOf { it.carbs }, Lime, true)
                Macro("Chất béo", foods.sumOf { it.fat }, Color(0xFFB8D8E5), true)
            }
            Text(if (calories <= model.profile.dailyCalories) "Còn ${(model.profile.dailyCalories - calories).roundToInt()} kcal so với mục tiêu đã đặt"
                else "Đã vượt ${(calories - model.profile.dailyCalories).roundToInt()} kcal so với mục tiêu đã đặt", color = Color(0xFFBBCFC1), fontSize = 11.sp)
        }
        Panel(color = Sage) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Hiểu bữa ăn\nqua một bức ảnh.", fontSize = 25.sp, fontWeight = FontWeight.Bold, lineHeight = 31.sp, letterSpacing = (-0.7).sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Chụp món ăn. Xem ước tính.\nĐiều chỉnh theo khẩu phần.", fontSize = 12.sp, color = Muted, lineHeight = 18.sp)
                }
                Plate(Modifier.size(104.dp))
            }
            Primary("+  Phân tích bữa ăn", !model.busy) { scanning = true }
        }
        if (!model.profileComplete) {
            Panel {
                Text("Bắt đầu từ mục tiêu của bạn", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                SmallNote("2.000 kcal là giá trị khởi tạo. Lưu hồ sơ và đặt mục tiêu phù hợp trước khi tạo kế hoạch.")
                TextButton(onClick = { model.tab = 3 }) { Text("Thiết lập hồ sơ  →") }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionLabel("Bữa ăn hôm nay", "Nhật ký →") { model.tab = 1 }
            if (foods.isEmpty()) EmptyState("Một ngày mới, một khởi đầu tốt", "Chưa có bữa ăn được ghi nhận. Thêm bữa đầu tiên của bạn.")
            else model.meals.filter { it.date == today.toString() }.forEach { meal ->
                Panel {
                    Row { Text(meal.slot, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Text("${meal.calories.roundToInt()} kcal", color = Pine, fontWeight = FontWeight.Bold) }
                    SmallNote(meal.foods.joinToString(" · ") { it.name })
                }
            }
        }
        SmallNote("Các giá trị dinh dưỡng là ước tính. Kiểm tra món ăn và khẩu phần trước khi lưu.")
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Macro(label: String, value: Double, color: Color, dark: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(5.dp).background(color, CircleShape))
            Text(label, Modifier.padding(start = 5.dp), color = if (dark) Color(0xFFBBCFC1) else Muted, fontSize = 11.sp)
        }
        Text("${value.roundToInt()} g", color = if (dark) Color.White else Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ScanScreen(model: AppModel, gate: (() -> Unit) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val cameraUri = remember { Uri.parse("content://${context.packageName}.photos/capture.jpg") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let(model::loadPhoto) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success -> if (success) model.loadPhoto(cameraUri) }
    var description by rememberSaveable { mutableStateOf("") }
    var slot by rememberSaveable { mutableStateOf("Trưa") }
    var mealDate by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var editing by remember { mutableStateOf<Int?>(null) }
    var adding by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) { Text("←  Hôm nay") }
        PageTitle("Gemini · Phân tích hình ảnh", "Bữa ăn của bạn")
        Box(Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(26.dp)).background(Sage), contentAlignment = Alignment.Center) {
            val photo = model.photo
            if (photo != null) Image(photo.asImageBitmap(), contentDescription = "Ảnh bữa ăn đã chọn", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Plate(Modifier.size(105.dp))
                Text("Chọn ảnh rõ, thấy toàn bộ món ăn", color = Pine, fontSize = 12.sp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { try { camera.launch(cameraUri) } catch (_: Exception) { model.error = "Không tìm thấy ứng dụng máy ảnh." } },
                enabled = !model.busy, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) { Text("Chụp ảnh") }
            OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                enabled = !model.busy, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) { Text("Thư viện") }
        }
        OutlinedTextField(value = description, onValueChange = { description = it.take(1000) }, label = { Text("Mô tả thêm (không bắt buộc)") },
            placeholder = { Text("Ví dụ: cơm gà, ăn nửa phần, ít sốt") }, modifier = Modifier.fillMaxWidth(), enabled = !model.busy, shape = RoundedCornerShape(16.dp))
        Primary(if (model.analysis == null) "Ước tính dinh dưỡng" else "Phân tích lại ảnh", model.photo != null && !model.busy) { gate { model.analyze(description) } }
        model.analysis?.let { result ->
            if (!result.isFood) Panel(color = Color(0xFFFFF0DF)) {
                Text("Chưa nhận diện được món ăn", fontWeight = FontWeight.Bold)
                SmallNote(result.notes.ifBlank { "Thử chụp lại ảnh rõ hơn hoặc nhập món thủ công." })
            }
            else {
                SectionLabel("Kết quả ước tính")
                Panel(color = Sage) {
                    Text("${result.foods.sumOf { it.calories }.roundToInt()} kcal", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Pine)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Macro("Protein", result.foods.sumOf { it.protein }, Orange)
                        Macro("Carbs", result.foods.sumOf { it.carbs }, Pine)
                        Macro("Chất béo", result.foods.sumOf { it.fat }, Color(0xFF83A6B8))
                    }
                    SmallNote(result.notes)
                }
                result.foods.forEachIndexed { i, food ->
                    Panel {
                        FoodLine(food)
                        Row {
                            TextButton(onClick = { editing = i }, enabled = !model.busy) { Text("Sửa món / khẩu phần") }
                            TextButton(onClick = { model.removeFood(i) }, enabled = !model.busy) { Text("Bỏ món", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        }
        TextButton(onClick = { adding = true }, enabled = !model.busy) { Text("+  Thêm món thủ công") }
        if (!model.analysis?.foods.isNullOrEmpty()) {
            SectionLabel("Ghi vào nhật ký")
            SlotSelector(slot) { slot = it }
            DateButton(LocalDate.parse(mealDate), "Ngày ăn") { mealDate = it.toString() }
            Primary("Xác nhận và lưu bữa ăn", !model.busy) { model.saveAnalysis(slot, LocalDate.parse(mealDate)); onBack() }
        }
        SmallNote("Ảnh không cho biết chính xác khối lượng, dầu hoặc đường. Hãy kiểm tra và sửa khẩu phần trước khi xác nhận.")
    }
    editing?.let { index -> model.analysis?.foods?.getOrNull(index)?.let { food ->
        FoodDialog(food, "Chỉnh sửa món ăn", { editing = null }) { model.editFood(index, it); editing = null }
    } }
    if (adding) FoodDialog(Food("", 100.0, 0.0, 0.0, 0.0, 0.0), "Thêm món ăn", { adding = false }) { model.addFood(it); adding = false }
}

@Composable
private fun FoodLine(food: Food) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(food.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(5.dp))
            SmallNote("${food.grams.pretty()} g · P ${food.protein.roundToInt()} · C ${food.carbs.roundToInt()} · F ${food.fat.roundToInt()} g")
        }
        Text("${food.calories.roundToInt()}", Modifier.padding(start = 12.dp), color = Pine, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(" kcal", Modifier.padding(top = 4.dp), fontSize = 10.sp, color = Muted)
    }
}

@Composable
private fun SlotSelector(selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        mealSlots.forEach { label -> Pill(label, selected == label) { onSelect(label) } }
    }
}

@Composable
private fun Pill(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(label, Modifier.clip(RoundedCornerShape(30.dp)).background(if (selected) Pine else Color.White)
        .clickable(onClick = onClick).padding(horizontal = 19.dp, vertical = 12.dp),
        color = if (selected) Color.White else Muted, fontWeight = FontWeight.Medium, fontSize = 12.sp)
}

@Composable
private fun DateButton(date: LocalDate, label: String = "Ngày", onSelect: (LocalDate) -> Unit) {
    val context = LocalContext.current
    OutlinedButton(onClick = {
        DatePickerDialog(context, { _, y, m, d -> onSelect(LocalDate.of(y, m + 1, d)) }, date.year, date.monthValue - 1, date.dayOfMonth).show()
    }, shape = RoundedCornerShape(14.dp)) { Text("$label: ${date.format(dateLabel)}  ▾") }
}

@Composable
private fun DiaryScreen(model: AppModel) {
    var selected by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var deleting by remember { mutableStateOf<Meal?>(null) }
    var editing by remember { mutableStateOf<Pair<Meal, Int>?>(null) }
    val dayMeals = model.meals.filter { it.date == selected }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        PageTitle("Mỗi bữa ăn đều được ghi nhận", "Nhật ký")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { selected = LocalDate.parse(selected).minusDays(1).toString() }) { Text("←") }
            DateButton(LocalDate.parse(selected)) { selected = it.toString() }
            TextButton(onClick = { selected = LocalDate.parse(selected).plusDays(1).toString() }) { Text("→") }
        }
        Panel(color = Sage) {
            Text("${dayMeals.sumOf { it.calories }.roundToInt()} kcal", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Pine)
            SmallNote("${dayMeals.size} bữa đã lưu · mục tiêu hiện tại ${model.profile.dailyCalories} kcal/ngày")
        }
        mealSlots.forEach { slot ->
            val entries = dayMeals.filter { it.slot == slot }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel(slot)
                if (entries.isEmpty()) SmallNote("Chưa ghi nhận bữa ăn.")
                entries.forEach { meal ->
                    Panel {
                        meal.foods.forEachIndexed { index, food ->
                            Column {
                                FoodLine(food)
                                TextButton(onClick = { editing = meal to index }) { Text("Chỉnh sửa", fontSize = 12.sp) }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (meal.source == "manual") "Tự nhập" else "Ước tính · đã xác nhận", fontSize = 11.sp, color = Muted, modifier = Modifier.weight(1f))
                            TextButton(onClick = { deleting = meal }) { Text("Xóa bữa", color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                        }
                    }
                }
            }
        }
        SmallNote("Kế hoạch và nhật ký được lưu riêng. Chỉ những bữa đã xác nhận mới được tính là đã ăn.")
    }
    deleting?.let { meal -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Xóa bữa ăn?") },
        text = { Text("Bữa ${meal.slot.lowercase(vi)} ngày ${meal.date} sẽ được xóa khỏi nhật ký.") },
        confirmButton = { TextButton(onClick = { model.deleteMeal(meal.id); deleting = null }) { Text("Xóa") } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text("Giữ lại") } }) }
    editing?.let { (meal, index) ->
        FoodDialog(meal.foods[index], "Sửa bữa đã lưu", { editing = null }) { food -> model.editSavedFood(meal.id, index, food); editing = null }
    }
}

@Composable
private fun PlanScreen(model: AppModel, gate: (() -> Unit) -> Unit) {
    var period by rememberSaveable { mutableIntStateOf(1) }
    var start by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var replacing by remember { mutableStateOf(false) }
    var logging by remember { mutableStateOf<Pair<PlanDay, PlanMeal>?>(null) }
    val startDate = LocalDate.parse(start)
    val end = when (period) { 0 -> startDate.plusDays(1); 1 -> startDate.plusDays(7); else -> startDate.plusMonths(1) }
    val visibleDays = model.plan.filter { !LocalDate.parse(it.date).isBefore(startDate) && LocalDate.parse(it.date).isBefore(end) }
    val generate = { gate { model.generatePlan(period, startDate) } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        PageTitle("Phù hợp với nhịp sống của bạn", "Kế hoạch ăn uống")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("Ngày", "Tuần", "Tháng").forEachIndexed { i, label -> Pill(label, period == i) { if (!model.busy) period = i } }
        }
        Panel(color = Sage) {
            Text("Ăn ngon, đúng mục tiêu.", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Pine)
            Text("${model.profile.dailyCalories} kcal/ngày · ${model.profile.goal}", color = Pine, fontSize = 12.sp)
            SmallNote("${model.profile.preferences}\nNgân sách: ${model.profile.budget}")
            DateButton(startDate, "Bắt đầu") { if (!model.busy) start = it.toString() }
            SmallNote("${startDate.format(dateLabel)} → ${end.minusDays(1).format(dateLabel)}. Thực đơn tháng được tạo theo từng nhóm tối đa 7 ngày.")
            Primary(if (visibleDays.isEmpty()) "Tạo kế hoạch với Gemini" else "Tạo lại kế hoạch", !model.busy) {
                if (visibleDays.isNotEmpty()) replacing = true else generate()
            }
        }
        SmallNote("Dinh dưỡng trong thực đơn do AI ước tính. Kiểm tra nguyên liệu, đặc biệt khi có dị ứng. Mục tiêu kcal do bạn thiết lập; ứng dụng không thay thế tư vấn dinh dưỡng chuyên môn.")
        if (visibleDays.isEmpty()) EmptyState("Dành chỗ cho những bữa ăn tốt", "Hoàn thiện hồ sơ rồi tạo kế hoạch phù hợp với sở thích và ngân sách của bạn.")
        visibleDays.forEach { day ->
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(LocalDate.parse(day.date).format(dateLabel).replaceFirstChar { it.uppercase() }, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("${day.meals.sumOf { it.food.calories }.roundToInt()} kcal", color = Muted, fontSize = 12.sp)
                }
                day.meals.forEach { meal ->
                    val logged = model.meals.any { it.planKey == "${day.date}:${meal.slot}" }
                    Panel {
                        Text(meal.slot.uppercase(vi), fontSize = 10.sp, letterSpacing = 1.sp, color = Muted)
                        FoodLine(meal.food)
                        if (logged) Text("✓ Đã ghi nhật ký", fontSize = 12.sp, color = Pine)
                        else TextButton(onClick = { logging = day to meal }) { Text("Ghi nhận đã ăn / sửa khẩu phần", fontSize = 12.sp) }
                    }
                }
            }
        }
    }
    if (replacing) AlertDialog(onDismissRequest = { replacing = false }, title = { Text("Tạo lại kế hoạch?") },
        text = { Text("Các ngày trong khoảng đã chọn sẽ được thay thế sau khi tạo thành công. Nhật ký bữa đã ăn được giữ nguyên.") },
        confirmButton = { TextButton(onClick = { replacing = false; generate() }) { Text("Tạo lại") } },
        dismissButton = { TextButton(onClick = { replacing = false }) { Text("Hủy") } })
    logging?.let { (day, meal) ->
        FoodDialog(meal.food, "Xác nhận bữa đã ăn", { logging = null }, showDate = true) { food, date ->
            model.logPlanned(day, meal, food, date); logging = null
        }
    }
}

@Composable
private fun ProfileScreen(model: AppModel) {
    val source = model.profile
    var age by remember(source) { mutableStateOf(source.age.toString()) }
    var height by remember(source) { mutableStateOf(source.height.pretty()) }
    var weight by remember(source) { mutableStateOf(source.weight.pretty()) }
    var target by remember(source) { mutableStateOf(source.dailyCalories.toString()) }
    var goal by remember(source) { mutableStateOf(source.goal) }
    var preferences by remember(source) { mutableStateOf(source.preferences) }
    var allergies by remember(source) { mutableStateOf(source.allergies) }
    var budget by remember(source) { mutableStateOf(source.budget) }
    var deleting by remember { mutableStateOf(false) }
    var validation by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        PageTitle("Mọi kế hoạch bắt đầu từ bạn", "Hồ sơ cá nhân")
        Panel(color = Sage) {
            Text("Mục tiêu của riêng bạn", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Pine)
            SmallNote("Ứng dụng hiện dành cho người từ 18 tuổi. Nhập mục tiêu năng lượng bạn đã lựa chọn; cân nặng và chiều cao không được dùng để tự động kê chế độ ăn.")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberField("Tuổi", age, { age = it }, Modifier.weight(1f), !model.busy)
            NumberField("Cao (cm)", height, { height = it }, Modifier.weight(1f), !model.busy)
            NumberField("Nặng (kg)", weight, { weight = it }, Modifier.weight(1f), !model.busy)
        }
        SectionLabel("Bạn muốn hướng tới")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Giảm cân", "Duy trì cân nặng", "Tăng cân").forEach { label ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White)
                    .clickable(enabled = !model.busy) { goal = label }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = goal == label, onClick = { goal = label }, enabled = !model.busy)
                    Text(label, fontSize = 14.sp)
                }
            }
        }
        NumberField("Mục tiêu năng lượng (kcal/ngày)", target, { target = it }, enabled = !model.busy)
        SmallNote("2.000 kcal chỉ là giá trị khởi tạo. Khoảng nhập 1.200–5.000 là giới hạn của bản thử nghiệm, không phải khuyến nghị cá nhân.")
        OutlinedTextField(preferences, { preferences = it.take(1000) }, label = { Text("Sở thích / chế độ ăn / thời gian nấu") },
            modifier = Modifier.fillMaxWidth(), enabled = !model.busy, shape = RoundedCornerShape(14.dp))
        OutlinedTextField(allergies, { allergies = it.take(500) }, label = { Text("Dị ứng / nguyên liệu cần tránh") },
            placeholder = { Text("Ví dụ: đậu phộng, tôm, sữa") }, modifier = Modifier.fillMaxWidth(), enabled = !model.busy, shape = RoundedCornerShape(14.dp))
        OutlinedTextField(budget, { budget = it.take(200) }, label = { Text("Ngân sách ăn uống") }, modifier = Modifier.fillMaxWidth(), enabled = !model.busy, shape = RoundedCornerShape(14.dp))
        if (validation.isNotBlank()) Text(validation, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        Primary("Lưu hồ sơ cá nhân", !model.busy) {
            val a = age.toIntOrNull(); val h = height.number(); val w = weight.number(); val kcal = target.toIntOrNull()
            if (a == null || a !in 18..100 || h == null || h !in 100.0..230.0 || w == null || w !in 30.0..300.0 || kcal == null || kcal !in 1200..5000) {
                validation = "Kiểm tra tuổi (18–100), chiều cao (100–230 cm), cân nặng (30–300 kg) và kcal (1.200–5.000)."
            } else { validation = ""; model.saveProfile(Profile(a, h, w, kcal, goal, preferences, allergies, budget)) }
        }
        Panel {
            Text("Dữ liệu và quyền riêng tư", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            SmallNote("Hồ sơ, nhật ký và kế hoạch được lưu trên thiết bị. Ảnh chỉ nằm tạm trên thiết bị; yêu cầu AI được gửi khi bạn đồng ý. Xóa dữ liệu ứng dụng hoặc gỡ ứng dụng sẽ mất dữ liệu này.")
            if (model.consent) TextButton(onClick = { model.revokeAI() }, enabled = !model.busy) { Text("Thu hồi quyền gửi dữ liệu đến AI") }
            else SmallNote("Chưa cho phép gửi dữ liệu đến AI.")
            TextButton(onClick = { deleting = true }, enabled = !model.busy) { Text("Xóa toàn bộ dữ liệu thiết bị", color = MaterialTheme.colorScheme.error) }
        }
        SmallNote("CaloLens · Bản thử nghiệm 0.1.0")
    }
    if (deleting) AlertDialog(onDismissRequest = { deleting = false }, title = { Text("Xóa toàn bộ dữ liệu?") },
        text = { Text("Hồ sơ, nhật ký và kế hoạch trên thiết bị sẽ bị xóa. Thao tác này không thể hoàn tác.") },
        confirmButton = { TextButton(onClick = { model.resetData(); deleting = false }) { Text("Xóa dữ liệu") } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text("Hủy") } })
}

private fun String.number() = replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

@Composable
private fun NumberField(label: String, value: String, change: (String) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedTextField(value, { change(it.take(9)) }, label = { Text(label, fontSize = 12.sp) }, modifier = modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, enabled = enabled, shape = RoundedCornerShape(14.dp))
}

@Composable
private fun FoodDialog(food: Food, title: String, onDismiss: () -> Unit, onSave: (Food) -> Unit) {
    FoodDialog(food, title, onDismiss, false) { value, _ -> onSave(value) }
}

@Composable
private fun FoodDialog(food: Food, title: String, onDismiss: () -> Unit, showDate: Boolean, onSave: (Food, LocalDate) -> Unit) {
    var name by remember { mutableStateOf(food.name) }
    var grams by remember { mutableStateOf(food.grams.pretty()) }
    var calories by remember { mutableStateOf(food.calories.pretty()) }
    var protein by remember { mutableStateOf(food.protein.pretty()) }
    var carbs by remember { mutableStateOf(food.carbs.pretty()) }
    var fat by remember { mutableStateOf(food.fat.pretty()) }
    var invalid by remember { mutableStateOf(false) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(name, { name = it.take(160) }, label = { Text("Tên món") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
            NumberField("Khẩu phần (g)", grams, { value ->
                grams = value
                value.number()?.takeIf { it > 0 && it <= 5000 }?.let { amount ->
                    val scaled = food.scaled(amount)
                    calories = scaled.calories.pretty(); protein = scaled.protein.pretty(); carbs = scaled.carbs.pretty(); fat = scaled.fat.pretty()
                }
            })
            SmallNote("Đổi khối lượng sẽ tính lại theo tỷ lệ của món gốc. Bạn có thể sửa các giá trị bên dưới cho toàn bộ khẩu phần.")
            NumberField("Năng lượng (kcal)", calories, { calories = it })
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NumberField("P (g)", protein, { protein = it }, Modifier.weight(1f))
                NumberField("C (g)", carbs, { carbs = it }, Modifier.weight(1f))
                NumberField("F (g)", fat, { fat = it }, Modifier.weight(1f))
            }
            if (showDate) DateButton(date, "Ngày thực tế") { date = it }
            if (invalid) Text("Nhập tên món, khối lượng 1–5.000 g, kcal 0–10.000 và các chất 0–1.000 g.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }
    }, confirmButton = { TextButton(onClick = {
        val g = grams.number(); val c = calories.number(); val p = protein.number(); val cb = carbs.number(); val f = fat.number()
        if (name.isBlank() || g == null || g !in 1.0..5000.0 || c == null || c !in 0.0..10000.0 || p == null || p !in 0.0..1000.0 || cb == null || cb !in 0.0..1000.0 || f == null || f !in 0.0..1000.0) invalid = true
        else onSave(Food(name.trim(), g, c, p, cb, f), date)
    }) { Text("Xác nhận") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } })
}

@Composable
private fun EmptyState(title: String, detail: String) {
    Panel {
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        SmallNote(detail)
    }
}

@Composable
private fun Plate(modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        drawCircle(Color(0xFFD6DFCC), w * .47f, Offset(w * .51f, h * .53f))
        drawCircle(Color.White, w * .45f, Offset(w * .5f, h * .49f))
        drawCircle(Color(0xFFF2F2E6), w * .34f, Offset(w * .5f, h * .49f))
        drawOval(Color(0xFFEAD7B2), Offset(w * .25f, h * .39f), Size(w * .35f, h * .3f))
        drawOval(Orange, Offset(w * .51f, h * .31f), Size(w * .22f, h * .38f))
        drawLine(Color(0xFFF5C296), Offset(w * .56f, h * .35f), Offset(w * .67f, h * .62f), w * .025f, StrokeCap.Round)
        listOf(.29f to .28f, .4f to .27f, .32f to .36f, .4f to .36f).forEach { (x, y) ->
            drawCircle(Color(0xFF638752), w * .07f, Offset(w * x, h * y))
        }
        drawCircle(Color(0xFFC76545), w * .055f, Offset(w * .4f, h * .65f))
        drawCircle(Color(0xFFC76545), w * .045f, Offset(w * .51f, h * .68f))
    }
}

@Composable
private fun Glyph(kind: Int, color: Color) {
    Canvas(Modifier.size(22.dp)) {
        val w = size.width; val h = size.height; val stroke = Stroke(1.7.dp.toPx(), cap = StrokeCap.Round)
        when (kind) {
            0 -> {
                val path = Path().apply { moveTo(w * .12f, h * .46f); lineTo(w * .5f, h * .13f); lineTo(w * .88f, h * .46f)
                    moveTo(w * .23f, h * .43f); lineTo(w * .23f, h * .86f); lineTo(w * .77f, h * .86f); lineTo(w * .77f, h * .43f)
                    moveTo(w * .43f, h * .86f); lineTo(w * .43f, h * .61f); lineTo(w * .6f, h * .61f); lineTo(w * .6f, h * .86f) }
                drawPath(path, color, style = stroke)
            }
            1 -> {
                drawRoundRect(color, Offset(w * .2f, h * .14f), Size(w * .62f, h * .74f), CornerRadius(w * .1f), style = stroke)
                for (y in listOf(.34f, .52f, .7f)) drawLine(color, Offset(w * .35f, h * y), Offset(w * .66f, h * y), 1.5.dp.toPx(), StrokeCap.Round)
            }
            2 -> {
                drawRoundRect(color, Offset(w * .13f, h * .22f), Size(w * .74f, h * .65f), CornerRadius(w * .1f), style = stroke)
                drawLine(color, Offset(w * .13f, h * .43f), Offset(w * .87f, h * .43f), 1.7.dp.toPx())
                for (x in listOf(.33f, .67f)) drawLine(color, Offset(w * x, h * .12f), Offset(w * x, h * .31f), 1.7.dp.toPx(), StrokeCap.Round)
                drawCircle(color, w * .05f, Offset(w * .36f, h * .62f)); drawCircle(color, w * .05f, Offset(w * .64f, h * .62f))
            }
            3 -> {
                drawCircle(color, w * .17f, Offset(w * .5f, h * .3f), style = stroke)
                drawArc(color, 180f, 180f, false, Offset(w * .17f, h * .57f), Size(w * .66f, h * .5f), style = stroke)
            }
            else -> {
                val path = Path().apply { moveTo(w * .22f, h * .8f); cubicTo(w * .05f, h * .3f, w * .45f, h * .1f, w * .86f, h * .13f)
                    cubicTo(w * .87f, h * .65f, w * .53f, h * .95f, w * .22f, h * .8f)
                    moveTo(w * .2f, h * .87f); lineTo(w * .64f, h * .38f) }
                drawPath(path, color, style = stroke)
            }
        }
    }
}
