package com.medora.paymentsms

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.medora.paymentsms.ui.AppRoot
import com.medora.paymentsms.ui.MainViewModel
import com.medora.paymentsms.ui.theme.MedoraTheme

class MainActivity : ComponentActivity() {
    private lateinit var vm: MainViewModel

    private val requestSms = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.permissionChanged()
    }

    private val requestNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        // Notification is only the visible status indicator; listening still works without it.
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vm = ViewModelProvider(this)[MainViewModel::class.java]

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            MedoraTheme {
                AppRoot(vm = vm, onRequestPermission = {
                    requestSms.launch(Manifest.permission.RECEIVE_SMS)
                })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::vm.isInitialized) vm.permissionChanged()
    }
}
