import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:logger/logger.dart';
import 'package:drift/native.dart';

import 'package:sroadtutor/app/app.dart';
import 'package:sroadtutor/app/config/env_config.dart';
import 'package:sroadtutor/core/storage/local_database.dart';
import 'package:sroadtutor/core/storage/secure_storage_service.dart';
import 'package:sroadtutor/core/sync/connectivity_service.dart';
import 'package:sroadtutor/features/auth/domain/repositories/auth_repository.dart';
import 'package:sroadtutor/features/auth/presentation/providers/auth_providers.dart';
import 'package:sroadtutor/main_common.dart';

import '../test/_support/fake_auth_repository.dart';

/// End-to-end integration test for the auth flow.
///
/// Runs in a real Flutter engine (emulator or device). Unlike widget tests
/// it exercises:
///   * GoRouter redirects (splash → login → home)
///   * Riverpod wiring
///   * Drift schema create (in-memory)
///
/// Network is still faked out via [FakeAuthRepository] so the test is
/// deterministic.
///
/// Run with:
///   flutter test integration_test/auth_flow_test.dart --flavor dev
void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('happy path: login → home → logout → login',
      (tester) async {
    final repo = FakeAuthRepository();
    final env = EnvConfig.forTest({
      'API_BASE_URL': 'https://test.local',
      'GOOGLE_WEB_CLIENT_ID': 'gw',
      'GOOGLE_IOS_CLIENT_ID': 'gi',
      'FACEBOOK_APP_ID': 'fb',
      'FACEBOOK_CLIENT_TOKEN': 'fbct',
      'ENV_NAME': 'dev',
      'ENABLE_DEBUG_LOGS': 'true',
    });

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          envConfigProvider.overrideWithValue(env),
          localDatabaseProvider
              .overrideWithValue(LocalDatabase.forTesting(NativeDatabase.memory())),
          secureStorageProvider
              .overrideWithValue(InMemorySecureStorageService()),
          connectivityServiceProvider
              .overrideWithValue(ConnectivityService()),
          appLoggerProvider.overrideWithValue(Logger(level: Level.off)),
          authRepositoryProvider.overrideWithValue(repo),
        ],
        child: const SRoadTutorApp(),
      ),
    );

    // Splash → login transition.
    await tester.pumpAndSettle(const Duration(seconds: 1));
    expect(find.byKey(const Key('login.email')), findsOneWidget);

    // Fill creds, tap submit.
    await tester.enterText(find.byKey(const Key('login.email')), 'a@b.com');
    await tester.enterText(find.byKey(const Key('login.password')), 'Password1');
    await tester.tap(find.byKey(const Key('login.submit')));
    await tester.pumpAndSettle(const Duration(seconds: 1));

    // Landed on home.
    expect(find.text('Home'), findsOneWidget);
    expect(find.textContaining('Hi Test User'), findsOneWidget);

    // Log out → back to login.
    await tester.tap(find.byKey(const Key('home.logout')));
    await tester.pumpAndSettle(const Duration(seconds: 1));
    expect(find.byKey(const Key('login.email')), findsOneWidget);
  });

  testWidgets('sign up → auto-login lands on home', (tester) async {
    final repo = FakeAuthRepository();
    final env = EnvConfig.forTest({
      'API_BASE_URL': 'https://test.local',
      'GOOGLE_WEB_CLIENT_ID': 'gw',
      'GOOGLE_IOS_CLIENT_ID': 'gi',
      'FACEBOOK_APP_ID': 'fb',
      'FACEBOOK_CLIENT_TOKEN': 'fbct',
      'ENV_NAME': 'dev',
      'ENABLE_DEBUG_LOGS': 'true',
    });

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          envConfigProvider.overrideWithValue(env),
          localDatabaseProvider
              .overrideWithValue(LocalDatabase.forTesting(NativeDatabase.memory())),
          secureStorageProvider
              .overrideWithValue(InMemorySecureStorageService()),
          connectivityServiceProvider
              .overrideWithValue(ConnectivityService()),
          appLoggerProvider.overrideWithValue(Logger(level: Level.off)),
          authRepositoryProvider.overrideWithValue(repo),
        ],
        child: const SRoadTutorApp(),
      ),
    );

    await tester.pumpAndSettle(const Duration(seconds: 1));
    await tester.tap(find.byKey(const Key('login.gotoSignup')));
    await tester.pumpAndSettle();

    await tester.enterText(find.byKey(const Key('signup.name')), 'Alice');
    await tester.enterText(
      find.byKey(const Key('signup.email')),
      'alice@example.com',
    );
    await tester.enterText(
      find.byKey(const Key('signup.password')),
      'Password1',
    );
    await tester.tap(find.byKey(const Key('signup.submit')));
    await tester.pumpAndSettle(const Duration(seconds: 1));

    expect(find.text('Home'), findsOneWidget);
    expect(repo.calls, contains('signup:alice@example.com:STUDENT'));
  });
}
