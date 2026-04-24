import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../features/auth/presentation/providers/auth_providers.dart';
import '../../features/auth/presentation/screens/home_screen.dart';
import '../../features/auth/presentation/screens/login_screen.dart';
import '../../features/auth/presentation/screens/role_picker_screen.dart';
import '../../features/auth/presentation/screens/signup_screen.dart';
import '../../features/auth/presentation/screens/splash_screen.dart';

/// All routes the app can navigate to.
///
/// Using a strongly-typed enum-ish class means feature code calls
/// `context.goNamed(AppRoute.login.name)` instead of magic strings.
abstract class AppRoute {
  static const String splash = 'splash';
  static const String login = 'login';
  static const String signup = 'signup';
  static const String rolePicker = 'rolePicker';
  static const String home = 'home';
}

/// Riverpod provider for the active [GoRouter]. It watches the
/// [authStateProvider] so we can redirect unauthenticated users away from
/// protected routes automatically.
final routerConfigProvider = Provider<GoRouter>((ref) {
  final authState = ref.watch(authStateProvider);

  return GoRouter(
    initialLocation: '/splash',
    debugLogDiagnostics: false,
    redirect: (context, state) {
      final loc = state.matchedLocation;
      final isLoggingIn = loc == '/login' || loc == '/signup';
      final isSplash = loc == '/splash';

      return authState.when(
        data: (status) {
          switch (status) {
            case AuthStatus.authenticated:
              if (isLoggingIn || isSplash) return '/home';
              return null;
            case AuthStatus.unauthenticated:
              if (isLoggingIn) return null;
              if (isSplash) return '/login';
              return '/login';
            case AuthStatus.needsRoleSelection:
              if (loc == '/role-picker') return null;
              return '/role-picker';
          }
        },
        // While we're resolving persisted tokens, sit on the splash.
        loading: () => isSplash ? null : '/splash',
        // On unknown error, bail to login — safer default.
        error: (_, __) => '/login',
      );
    },
    routes: [
      GoRoute(
        path: '/splash',
        name: AppRoute.splash,
        builder: (context, state) => const SplashScreen(),
      ),
      GoRoute(
        path: '/login',
        name: AppRoute.login,
        builder: (context, state) => const LoginScreen(),
      ),
      GoRoute(
        path: '/signup',
        name: AppRoute.signup,
        builder: (context, state) => const SignupScreen(),
      ),
      GoRoute(
        path: '/role-picker',
        name: AppRoute.rolePicker,
        builder: (context, state) => const RolePickerScreen(),
      ),
      GoRoute(
        path: '/home',
        name: AppRoute.home,
        builder: (context, state) => const HomeScreen(),
      ),
    ],
    errorBuilder: (context, state) => Scaffold(
      appBar: AppBar(title: const Text('Not found')),
      body: Center(
        child: Text('No route defined for ${state.matchedLocation}'),
      ),
    ),
  );
});
