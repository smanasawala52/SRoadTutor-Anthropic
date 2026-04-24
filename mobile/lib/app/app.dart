import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'router/app_router.dart';
import 'theme/app_theme.dart';

/// Root widget — wires GoRouter + the Material 3 theme.
///
/// The `routerConfigProvider` is a Riverpod provider so redirects can react
/// to authentication state changes (e.g. when the user logs out we want
/// every screen to pop back to `/login`).
class SRoadTutorApp extends ConsumerWidget {
  const SRoadTutorApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final router = ref.watch(routerConfigProvider);

    return MaterialApp.router(
      title: 'SRoadTutor',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.light,
      routerConfig: router,
    );
  }
}
