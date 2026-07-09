package com.jagr.fridamusic.presentation.onboarding

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.jagr.fridamusic.R
import com.jagr.fridamusic.domain.model.AppTheme
import com.jagr.fridamusic.presentation.viewmodels.SettingsViewModel
import com.jagr.fridamusic.presentation.viewmodels.LibraryViewModel

@Composable
fun OnboardingScreen(
    viewModel: SetupViewModel,
    settingsViewModel: SettingsViewModel,
    libraryViewModel: LibraryViewModel,
    onFinish: () -> Unit
) {
    val currentStep by viewModel.currentStep.collectAsState()

    Scaffold(
        bottomBar = {
            OnboardingBottomBar(
                step = currentStep.stepNumber,
                totalSteps = 10,
                onNext = { 
                    if (currentStep == SetupStep.FINAL) onFinish() else viewModel.nextStep()
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp)) {
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    if (targetState.stepNumber > initialState.stepNumber) {
                        (slideInHorizontally { it } + fadeIn()).togetherWith(slideOutHorizontally { -it } + fadeOut())
                    } else {
                        (slideInHorizontally { -it } + fadeIn()).togetherWith(slideOutHorizontally { it } + fadeOut())
                    }
                }, label = ""
            ) { step ->
                when (step) {
                    SetupStep.WELCOME -> WelcomeStep()
                    SetupStep.PERMISSIONS_MEDIA -> PermissionsMediaStep(libraryViewModel, viewModel)
                    SetupStep.PERMISSIONS_NOTIFICATIONS -> PermissionsNotificationsStep(viewModel)
                    SetupStep.BACKUP -> BackupStep(viewModel)
                    SetupStep.FOLDERS -> FoldersStep(settingsViewModel, viewModel)
                    SetupStep.THEME -> ThemeStep(settingsViewModel)
                    SetupStep.LIBRARY_LAYOUT -> LibraryLayoutStep(settingsViewModel)
                    SetupStep.NAVIGATION_STYLE -> NavigationStyleStep(settingsViewModel)
                    SetupStep.ALARMS -> AlarmsStep(viewModel)
                    SetupStep.BATTERY -> BatteryStep(viewModel)
                    SetupStep.FINAL -> FinalStep()
                }
            }
        }
    }
}

@Composable
private fun WelcomeStep() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Bienvenido a\nFrida Music",
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 42.sp, fontWeight = FontWeight.Black, lineHeight = 48.sp),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(16.dp)) {
            Text("β Beta", modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(48.dp))
        IllustrationBox(icon = Icons.Default.MusicNote, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(48.dp))
        Text("Vamos a dejar todo listo para ti.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun PermissionsMediaStep(libraryViewModel: LibraryViewModel, setupViewModel: SetupViewModel) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results.values.all { it }
        if (granted) {
            libraryViewModel.loadSongs()
            setupViewModel.nextStep()
        } else {
            Toast.makeText(context, "El permiso es necesario para crear tu biblioteca.", Toast.LENGTH_LONG).show()
        }
    }

    SetupStepTemplate(
        title = "Permiso de medios",
        description = "Frida Music necesita acceso a tus archivos de audio para crear tu biblioteca musical.",
        illustration = { IllustrationBox(icon = Icons.Default.Folder, color = Color(0xFF60A5FA)) },
        actionButton = {
            Button(
                onClick = {
                    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
                    } else {
                        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                    }
                    permissionLauncher.launch(permissions)
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(28.dp)
            ) {
                Text("Conceder permiso de medios")
            }
        }
    )
}

@Composable
private fun PermissionsNotificationsStep(setupViewModel: SetupViewModel) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            setupViewModel.nextStep()
        } else {
            Toast.makeText(context, "Notificaciones desactivadas. Puedes activarlas luego.", Toast.LENGTH_SHORT).show()
            setupViewModel.nextStep()
        }
    }

    SetupStepTemplate(
        title = "Notificaciones",
        description = "Activa las notificaciones para controlar la música desde la pantalla de bloqueo y el panel.",
        illustration = { IllustrationBox(icon = Icons.Default.Notifications, color = Color(0xFFF43F5E)) },
        actionButton = {
            Button(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        setupViewModel.nextStep()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(28.dp)
            ) {
                Text("Activar notificaciones")
            }
        },
        skipButton = {
            TextButton(onClick = { setupViewModel.nextStep() }) {
                Text("Ahora no", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

@Composable
private fun BackupStep(setupViewModel: SetupViewModel) {
    val context = LocalContext.current
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            Toast.makeText(context, "Copia de seguridad importada con éxito", Toast.LENGTH_SHORT).show()
            setupViewModel.nextStep()
        }
    }

    SetupStepTemplate(
        title = "¿Tienes una copia de seguridad?",
        description = "Si ya tienes una copia de Frida Music, restáurala ahora y omite la mayor parte del resto de la configuración en este dispositivo.",
        illustration = { IllustrationBox(icon = Icons.Default.Backup, color = Color(0xFF34D399)) },
        actionButton = {
            Button(
                onClick = { importLauncher.launch("application/json") },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(28.dp)
            ) {
                Text("Importar copia")
            }
        },
        skipButton = {
            TextButton(onClick = { setupViewModel.nextStep() }) {
                Text("Omitir / Ahora no", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

@Composable
private fun FoldersStep(settingsViewModel: SettingsViewModel, setupViewModel: SetupViewModel) {
    val context = LocalContext.current
    val excludedFolders by settingsViewModel.excludedFolderUris.collectAsState()
    
    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            settingsViewModel.addExcludedFolder(uri.toString())
            Toast.makeText(context, "Carpeta añadida a exclusiones", Toast.LENGTH_SHORT).show()
        }
    }

    SetupStepTemplate(
        title = "Carpetas excluidas",
        description = "Por defecto se escanean todas las carpetas. Elige ubicaciones que quieras ignorar al crear la biblioteca.",
        illustration = { IllustrationBox(icon = Icons.Default.CreateNewFolder, color = Color(0xFFFBBF24)) },
        content = {
            if (excludedFolders.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Carpetas a ignorar:", fontWeight = FontWeight.Bold)
                    excludedFolders.forEach { folderUri ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(8.dp)
                        ) {
                            Icon(Icons.Default.Folder, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = Uri.parse(folderUri).path?.split("/")?.lastOrNull() ?: "Carpeta",
                                modifier = Modifier.weight(1f),
                                maxLines = 1
                            )
                            IconButton(onClick = { settingsViewModel.removeExcludedFolder(folderUri) }) {
                                Icon(Icons.Default.Close, null)
                            }
                        }
                    }
                }
            }
        },
        actionButton = {
            Button(
                onClick = { folderPicker.launch(null) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(28.dp)
            ) {
                Text("Elegir carpetas a ignorar")
            }
        },
        skipButton = {
            TextButton(onClick = { setupViewModel.nextStep() }) {
                Text(if (excludedFolders.isEmpty()) "Omitir / Ahora no" else "Continuar", color = MaterialTheme.colorScheme.primary)
            }
        }
    )
}

@Composable
private fun ThemeStep(settingsViewModel: SettingsViewModel) {
    val currentTheme by settingsViewModel.currentTheme.collectAsState()
    SetupStepTemplate(
        title = "Tema de la app",
        description = "Elige el aspecto antes de explorar tu biblioteca.",
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ThemeOption("Oscuro", "El aspecto oscuro Material 3 predeterminado.", currentTheme == AppTheme.DARK, true) { settingsViewModel.updateTheme(AppTheme.DARK) }
                ThemeOption("Claro", "Un aspecto más luminoso en toda la app.", currentTheme == AppTheme.LIGHT) { settingsViewModel.updateTheme(AppTheme.LIGHT) }
                ThemeOption("Seguir el sistema", "Coincidir con el tema del teléfono.", currentTheme == AppTheme.SYSTEM) { settingsViewModel.updateTheme(AppTheme.SYSTEM) }
            }
        }
    )
}

@Composable
private fun LibraryLayoutStep(settingsViewModel: SettingsViewModel) {
    // Note: This step could be used to set a "Compact Mode" preference in the future
    var isCompact by remember { mutableStateOf(false) }

    SetupStepTemplate(
        title = "Disposición de la biblioteca",
        description = "Elige cómo quieres navegar por tu biblioteca.",
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(24.dp)
                ) {
                    Column {
                        Text("Biblioteca", style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("CANCIONES", style = MaterialTheme.typography.labelSmall)
                            Text("ÁLBUMES", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Modo compacto", fontWeight = FontWeight.Bold)
                            Text("Fila de pestañas estándar", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = isCompact, onCheckedChange = { isCompact = it })
                    }
                }
            }
        }
    )
}

@Composable
private fun NavigationStyleStep(settingsViewModel: SettingsViewModel) {
    val isFloating by settingsViewModel.useFloatingNavBar.collectAsState()

    SetupStepTemplate(
        title = "Navegación de la app",
        description = "Elige el estilo de la barra inferior.",
        content = {
            Column(
                verticalArrangement = Arrangement.spacedBy(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                NavigationStylePreview(isFloating = isFloating)

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isFloating) "Estilo Flotante" else "Estilo Clásico",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (isFloating) "Píldora moderna con esquinas redondeadas" else "Barra estándar de ancho completo",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(checked = isFloating, onCheckedChange = { settingsViewModel.updateNavBarStyle(it) })
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun NavigationStylePreview(isFloating: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), RoundedCornerShape(32.dp)),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(modifier = Modifier.width(120.dp).height(12.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)))
            Box(modifier = Modifier.width(200.dp).height(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)))
            Box(modifier = Modifier.width(160.dp).height(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)))
        }

        AnimatedContent(
            targetState = isFloating,
            transitionSpec = { (fadeIn() + scaleIn()).togetherWith(fadeOut() + scaleOut()) },
            label = "nav_preview"
        ) { floating ->
            if (floating) {
                Box(
                    modifier = Modifier.padding(bottom = 16.dp).width(200.dp).height(56.dp).shadow(12.dp, CircleShape).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center
                ) {
                    Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Home, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        Icon(Icons.Default.Search, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Icon(Icons.Default.LibraryMusic, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().height(64.dp).background(MaterialTheme.colorScheme.surfaceContainerLowest).padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PreviewNavItem(Icons.Default.Home, "Home", true)
                    PreviewNavItem(Icons.Default.Search, "Search", false)
                    PreviewNavItem(Icons.Default.LibraryMusic, "Library", false)
                }
            }
        }
    }
}

@Composable
private fun PreviewNavItem(icon: ImageVector, label: String, selected: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, modifier = Modifier.size(20.dp), tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, fontSize = 10.sp, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AlarmsStep(setupViewModel: SetupViewModel) {
    val context = LocalContext.current
    SetupStepTemplate(
        title = "Alarmas y recordatorios",
        description = "Opcional, pero recomendado si usas el temporizador de sueño y quieres que Frida Music detenga la reproducción a la hora exacta.",
        illustration = { IllustrationBox(icon = Icons.Default.Alarm, color = Color(0xFF818CF8)) },
        actionButton = {
            Button(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                        if (!alarmManager.canScheduleExactAlarms()) {
                            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                            context.startActivity(intent)
                        } else {
                            setupViewModel.nextStep()
                        }
                    } else {
                        setupViewModel.nextStep()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(28.dp)
            ) {
                Text("Conceder permiso")
            }
        },
        skipButton = {
            TextButton(onClick = { setupViewModel.nextStep() }) {
                Text("Ahora no", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

@Composable
private fun BatteryStep(setupViewModel: SetupViewModel) {
    val context = LocalContext.current
    SetupStepTemplate(
        title = "Optimización de batería",
        description = "Algunos dispositivos cierran apps en segundo plano con fuerza. Desactiva la optimización de batería para Frida Music y evita cortes inesperados.",
        illustration = { IllustrationBox(icon = Icons.Default.BatteryChargingFull, color = Color(0xFFF472B6)) },
        actionButton = {
            Button(
                onClick = {
                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(28.dp)
            ) {
                Text("Desactivar optimización")
            }
        },
        skipButton = {
            TextButton(onClick = { setupViewModel.nextStep() }) {
                Text("Confirmar y continuar", color = MaterialTheme.colorScheme.primary)
            }
        }
    )
}

@Composable
private fun FinalStep() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "¡Todo listo!",
            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(48.dp))
        IllustrationBox(icon = Icons.Default.CheckCircle, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(48.dp))
        Text("Ya puedes disfrutar de tu música.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun SetupStepTemplate(
    title: String,
    description: String,
    illustration: @Composable (() -> Unit)? = null,
    content: @Composable (() -> Unit)? = null,
    actionButton: @Composable (() -> Unit)? = null,
    skipButton: @Composable (() -> Unit)? = null
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        Text(text = title, style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold), textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = description, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 12.dp))
        Spacer(modifier = Modifier.height(48.dp))
        if (illustration != null) illustration()
        if (content != null) content()
        Spacer(modifier = Modifier.weight(1f))
        if (skipButton != null) {
            skipButton()
            Spacer(modifier = Modifier.height(8.dp))
        }
        if (actionButton != null) actionButton()
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun ThemeOption(title: String, description: String, isSelected: Boolean, recommended: Boolean = false, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.border(width = 2.dp, color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent, shape = RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isSelected) 0.8f else 0.4f)
    ) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                Icon(when(title) { "Oscuro" -> Icons.Default.DarkMode; "Claro" -> Icons.Default.LightMode; else -> Icons.Default.SettingsSuggest }, null)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    if (recommended) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), shape = RoundedCornerShape(8.dp)) {
                            Text("Recomendado", modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            RadioButton(selected = isSelected, onClick = onClick)
        }
    }
}

@Composable
private fun IllustrationBox(icon: ImageVector, color: Color) {
    Box(modifier = Modifier.size(200.dp).clip(RoundedCornerShape(40.dp)).background(color.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.size(120.dp).rotate(15f).clip(RoundedCornerShape(24.dp)).background(color.copy(alpha = 0.2f)))
        Box(modifier = Modifier.size(100.dp).rotate(-10f).clip(RoundedCornerShape(20.dp)).background(color.copy(alpha = 0.4f)))
        Icon(icon, null, modifier = Modifier.size(64.dp), tint = color)
    }
}

@Composable
private fun OnboardingBottomBar(step: Int, totalSteps: Int, onNext: () -> Unit) {
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 24.dp, bottom = 24.dp + navBarPadding), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        if (step > 0) {
            Text("Paso $step de $totalSteps", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            TextButton(onClick = onNext) { Text("¡Vamos!", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onBackground) }
        }
        FloatingActionButton(onClick = onNext, containerColor = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(24.dp), modifier = Modifier.size(64.dp)) {
            Icon(if (step == totalSteps) Icons.Default.Check else Icons.AutoMirrored.Filled.ArrowForward, "Siguiente")
        }
    }
}
