package com.sroadtutor.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.currentWindowSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.sroadtutor.data.remote.models.UserRole
import com.sroadtutor.navigation.Route

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(
    currentRoute: Route,
    userRole: UserRole?,
    onNavigate: (Route) -> Unit,
    onLogout: () -> Unit,
    onOpenLanguage: () -> Unit = { onNavigate(Route.Language) },
    content: @Composable (PaddingValues) -> Unit
) {
    val windowSize = currentWindowSize()
    val isExpanded = windowSize.width > 840

    Row(modifier = Modifier.fillMaxSize()) {
        if (isExpanded) {
            NavigationRail(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                header = {
                    Column {
                        // Globe emoji avoids depending on the extended Material
                        // icon set so a missing dependency / IDE cache issue
                        // never blocks the build.
                        TextButton(onClick = onOpenLanguage) {
                            Text("🌐", fontSize = androidx.compose.ui.unit.TextUnit(20f, androidx.compose.ui.unit.TextUnitType.Sp))
                        }
                        IconButton(onClick = onLogout) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Logout")
                        }
                    }
                }
            ) {
                Spacer(Modifier.weight(1f))
                NavigationItem.getVisibleItems(userRole).forEach { item ->
                    NavigationRailItem(
                        selected = currentRoute::class == item.route::class,
                        onClick = { onNavigate(item.route) },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) }
                    )
                }
                Spacer(Modifier.weight(1f))
            }
        }

        Scaffold(
            modifier = Modifier.weight(1f),
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                if (!isExpanded) {
                    TopAppBar(
                        title = { Text("SRoadTutor") },
                        actions = {
                            TextButton(onClick = onOpenLanguage) {
                                Text("🌐", fontSize = androidx.compose.ui.unit.TextUnit(20f, androidx.compose.ui.unit.TextUnitType.Sp))
                            }
                            IconButton(onClick = onLogout) {
                                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Logout")
                            }
                        }
                    )
                }
            },
            bottomBar = {
                if (!isExpanded) {
                    NavigationBar {
                        NavigationItem.getVisibleItems(userRole).forEach { item ->
                            NavigationBarItem(
                                selected = currentRoute::class == item.route::class,
                                onClick = { onNavigate(item.route) },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = { Text(item.label) }
                            )
                        }
                    }
                }
            }
        ) { padding ->
            content(padding)
        }
    }
}

enum class NavigationItem(
    val label: String, 
    val icon: ImageVector, 
    val route: Route,
    val roles: List<UserRole> = UserRole.entries.toList()
) {
    Dashboard("Home", Icons.Default.Dashboard, Route.Dashboard),
    Sessions("Sessions", Icons.Default.DateRange, Route.Sessions),
    Schools("Schools", Icons.Default.School, Route.Schools, listOf(UserRole.OWNER)),
    Instructors("Team", Icons.Default.Person, Route.Instructors, listOf(UserRole.OWNER)),
    Students("Students", Icons.Default.Group, Route.Students, listOf(UserRole.OWNER, UserRole.INSTRUCTOR)),
    Reminders("Alerts", Icons.Default.Notifications, Route.Reminders, listOf(UserRole.OWNER, UserRole.INSTRUCTOR)),
    Marketplace("Market", Icons.Default.Store, Route.Marketplace, listOf(UserRole.OWNER)),
    Matchmaker("Auto", Icons.Default.DirectionsCar, Route.Matchmaker, listOf(UserRole.PARENT)),
    Insurance("Cover", Icons.Default.Security, Route.Insurance, listOf(UserRole.OWNER)),
    Invitations("Invites", Icons.Default.Mail, Route.Invitations, listOf(UserRole.OWNER)),
    Subscription("Billing", Icons.Default.CreditCard, Route.Subscription, listOf(UserRole.OWNER));

    companion object {
        fun getVisibleItems(role: UserRole?): List<NavigationItem> {
            if (role == null) return emptyList()
            return entries.filter { it.roles.contains(role) }
        }
    }
}
