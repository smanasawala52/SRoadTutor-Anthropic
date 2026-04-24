import 'package:flutter_dotenv/flutter_dotenv.dart';

/// Strongly-typed accessor over the `.env.<flavor>` file that was loaded in
/// [main_common.dart#bootstrap].
///
/// Calling `EnvConfig.load(envFile: '.env.dev')` must happen BEFORE the app
/// reads any of the getters below — otherwise every read throws, which is
/// the *correct* behavior (fail loud rather than silent).
///
/// Why a class and not global functions?
///   We can swap `EnvConfig` in tests via Riverpod overrides without monkey-
///   patching `dotenv`.
class EnvConfig {
  EnvConfig._(this._source);

  final Map<String, String> _source;

  /// Loads the given dotenv file from Flutter assets.
  /// Must be called from the flavor entrypoint (main_dev / main_qa / main_prod).
  static Future<EnvConfig> load({required String envFile}) async {
    await dotenv.load(fileName: envFile);
    return EnvConfig._(Map<String, String>.from(dotenv.env));
  }

  /// Alternate constructor used by tests: skip dotenv entirely.
  factory EnvConfig.forTest(Map<String, String> values) => EnvConfig._(values);

  // ─── Backend ────────────────────────────────────────────────────────────

  String get apiBaseUrl => _required('API_BASE_URL');

  // ─── Google OAuth ──────────────────────────────────────────────────────

  String get googleWebClientId => _required('GOOGLE_WEB_CLIENT_ID');
  String get googleIosClientId => _required('GOOGLE_IOS_CLIENT_ID');

  // ─── Facebook OAuth ────────────────────────────────────────────────────

  String get facebookAppId => _required('FACEBOOK_APP_ID');
  String get facebookClientToken => _required('FACEBOOK_CLIENT_TOKEN');

  // ─── Feature flags ─────────────────────────────────────────────────────

  String get envName => _required('ENV_NAME'); // dev | qa | prod
  bool get enableDebugLogs =>
      (_source['ENABLE_DEBUG_LOGS'] ?? 'false').toLowerCase() == 'true';
  String? get sentryDsn {
    final v = _source['SENTRY_DSN'];
    return (v == null || v.isEmpty) ? null : v;
  }

  bool get isProd => envName == 'prod';
  bool get isDev => envName == 'dev';

  // ─── Internals ─────────────────────────────────────────────────────────

  String _required(String key) {
    final v = _source[key];
    if (v == null || v.isEmpty || v.startsWith('REPLACE_ME')) {
      throw StateError(
        'Required env var "$key" is missing or still set to REPLACE_ME. '
        'Check your .env.<flavor> file.',
      );
    }
    return v;
  }
}
