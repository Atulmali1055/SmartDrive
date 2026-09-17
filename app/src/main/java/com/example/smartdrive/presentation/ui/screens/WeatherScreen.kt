package com.example.smartdrive.presentation.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.smartdrive.MainViewModel
import com.example.smartdrive.SmartDriveApplication
import com.example.smartdrive.presentation.ble.BlePacket
import com.example.smartdrive.presentation.weather.WeatherSender
import com.example.smartdrive.presentation.weather.WeatherService
import kotlinx.coroutines.launch

@Composable
fun WeatherScreen(vm: MainViewModel, app: SmartDriveApplication) {
    val scope = rememberCoroutineScope()
    val enabled by vm.settings.weatherEnabled.collectAsStateWithLifecycle()

    var status by remember { mutableStateOf<String?>(null) }
    var city by remember { mutableStateOf("Bengaluru") }
    var lat by remember { mutableStateOf("12.9716") }
    var lon by remember { mutableStateOf("77.5946") }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Weather", style = MaterialTheme.typography.headlineSmall)

        Card { Column(Modifier.padding(16.dp)) {
            Row {
                Text("Enabled", Modifier.weight(1f))
                Switch(checked = enabled, onCheckedChange = { vm.settings.setWeatherEnabled(it) })
            }
        }}

        OutlinedTextField(value = city, onValueChange = { city = it }, label = { Text("City name") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = lat, onValueChange = { lat = it }, label = { Text("Latitude") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = lon, onValueChange = { lon = it }, label = { Text("Longitude") }, modifier = Modifier.fillMaxWidth())

        Button(
            onClick = {
                scope.launch {
                    status = "Fetching…"
                    val bundle = WeatherService.fetch(lat.toDoubleOrNull() ?: 12.9716,
                                                       lon.toDoubleOrNull() ?: 77.5946, city)
                    if (bundle == null) { status = "Fetch failed"; return@launch }
                    WeatherSender.send(bundle) { app.bleManager.sendData(it) }
                    status = "Sent: ${bundle.current.tempC}°C, ${bundle.forecast.size}-day forecast"
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Fetch & Send to Device") }

        status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}
