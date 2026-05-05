package com.sroadtutor.ui.subscriptions

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sroadtutor.data.remote.models.PlanRow
import com.sroadtutor.data.remote.models.SubscriptionResponse

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionsScreen(
    viewModel: SubscriptionsViewModel,
    onBack: () -> Unit
) {
    val plans by viewModel.plans.collectAsStateWithLifecycle()
    val mySub by viewModel.mySubscription.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val checkoutUrl by viewModel.checkoutUrl.collectAsStateWithLifecycle()
    
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.loadData()
    }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.resetError()
        }
    }

    LaunchedEffect(checkoutUrl) {
        checkoutUrl?.let {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(it))
            context.startActivity(intent)
            viewModel.resetCheckoutUrl()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Subscription") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isLoading && plans.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else {
                mySub?.let { sub ->
                    item {
                        CurrentPlanCard(sub)
                    }
                }

                item {
                    Text(text = "Available Plans", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                }

                items(plans, key = { it.tier }) { plan ->
                    PlanCard(
                        plan = plan,
                        isCurrent = mySub?.tier == plan.tier,
                        onUpgrade = { viewModel.upgrade(plan.tier) }
                    )
                }
            }
        }
    }
}

@Composable
fun CurrentPlanCard(sub: SubscriptionResponse) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Current Plan", style = MaterialTheme.typography.labelLarge)
            Text(text = sub.tier, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(text = "Usage & Limits", style = MaterialTheme.typography.titleMedium)
            
            sub.limits?.let { limits ->
                val usageMap = mapOf(
                    "Instructors" to (0 to limits.instructorLimit),
                    "Students" to (0 to limits.studentLimit),
                    "WhatsApp (Monthly)" to ((sub.usage?.waMeThisMonth ?: 0) to limits.waMeMonthlyLimit)
                )

                usageMap.forEach { (label, data) ->
                    val (used, limit) = data
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = label, style = MaterialTheme.typography.bodySmall)
                            Text(text = "$used / $limit", style = MaterialTheme.typography.bodySmall)
                        }
                        LinearProgressIndicator(
                            progress = { if (limit > 0) (used.toFloat() / limit).coerceAtMost(1f) else 0f },
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PlanCard(
    plan: PlanRow,
    isCurrent: Boolean,
    onUpgrade: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = plan.tier, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(text = "$${plan.monthlyPriceCad}/mo", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = onUpgrade,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isCurrent,
                colors = if (isCurrent) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant) else ButtonDefaults.buttonColors()
            ) {
                Text(if (isCurrent) "Current Plan" else "Upgrade Now")
            }
        }
    }
}
