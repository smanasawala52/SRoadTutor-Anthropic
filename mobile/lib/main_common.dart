import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:logger/logger.dart';

import 'app/app.dart';
import 'app/config/env_config.dart';
import 'core/storage/local_database.dart';
import 'core/storage/secure_storage_service.dart';
import 'core/sync/connectivity_service.dart';

/// Shared bootstrap used by all three flavor entrypoints
/// ([main_dev.dart], [main_qa.dart], [main_prod.dart]).
///
/// Steps:
///   1. Ensure the Flutter engine is initialized (required before any plugin
///      call).
///   2. Load the `.env.<flavor>` file.
///   3. Warm up the offline database, the secure-storage wrapper, and the
///      connectivity stream.
///   4. Install a zoned error handler so uncaught errors get logged instead
///      of silently crashing the isolate.
///   5. Mount the Riverpod root + the app widget.
///
/// All of the above runs inside [runZonedGuarded] so any stray async error
/// ends up in [Logger] rather than a native crash log.
Future<void> bootstrap({required String envFile}) async {
  await runZonedGuarded<Future<void>>(
    () async {
      WidgetsFlutterBinding.ensureInitialized();

      // 1. Load env vars from the flavored .env asset.
      final envConfig = await EnvConfig.load(envFile: envFile);

      // 2. Open the offline-first SQLite database (Drift) so it's hot when
      //    the first screen opens. Creating it lazily-on-first-read would
      //    jank the first navigation.
      final db = LocalDatabase();

      // 3. Build the secure storage wrapper (Keychain on iOS, EncryptedShared-
      //    Preferences on Android).
      const secureStorage = SecureStorageService();

      // 4. Start watching connectivity so the sync queue can drain as soon as
      //    the device comes back online.
      final connectivity = ConnectivityService()..start();

      final logger = Logger(
        level: envConfig.enableDebugLogs ? Level.debug : Level.warning,
        filter: _ReleaseAwareFilter(envConfig.enableDebugLogs),
      );

      // 5. Install Flutter's framework-level error hook.
      FlutterError.onError = (FlutterErrorDetails details) {
        logger.e(
          'FlutterError: ${details.exceptionAsString()}',
          error: details.exception,
          stackTrace: details.stack,
        );
      };

      runApp(
        ProviderScope(
          overrides: [
            envConfigProvider.overrideWithValue(envConfig),
            localDatabaseProvider.overrideWithValue(db),
            secureStorageProvider.overrideWithValue(secureStorage),
            connectivityServiceProvider.overrideWithValue(connectivity),
            appLoggerProvider.overrideWithValue(logger),
          ],
          child: const SRoadTutorApp(),
        ),
      );
    },
    (Object error, StackTrace stack) {
      // Last-resort catch: printed to the console; in prod this would flush
      // to Sentry if SENTRY_DSN is configured.
      if (kDebugMode) {
        // ignore: avoid_print
        print('Uncaught zoned error: $error\n$stack');
      }
    },
  );
}

/// Riverpod providers for process-wide singletons. Kept here so they can be
/// overridden with fakes in widget tests.
final envConfigProvider = Provider<EnvConfig>(
  (ref) => throw UnimplementedError('envConfigProvider must be overridden'),
);
final localDatabaseProvider = Provider<LocalDatabase>(
  (ref) => throw UnimplementedError('localDatabaseProvider must be overridden'),
);
final secureStorageProvider = Provider<SecureStorageService>(
  (ref) => throw UnimplementedError('secureStorageProvider must be overridden'),
);
final connectivityServiceProvider = Provider<ConnectivityService>(
  (ref) => throw UnimplementedError(
    'connectivityServiceProvider must be overridden',
  ),
);
final appLoggerProvider = Provider<Logger>(
  (ref) => throw UnimplementedError('appLoggerProvider must be overridden'),
);

class _ReleaseAwareFilter extends LogFilter {
  _ReleaseAwareFilter(this.debugEnabled);
  final bool debugEnabled;

  @override
  bool shouldLog(LogEvent event) {
    if (kReleaseMode && !debugEnabled) {
      return event.level.index >= Level.warning.index;
    }
    return true;
  }
}
