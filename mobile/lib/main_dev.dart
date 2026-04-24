import 'main_common.dart';

/// DEV flavor entrypoint.
///
/// Run with:
///   flutter run --flavor dev -t lib/main_dev.dart
void main() => bootstrap(envFile: '.env.dev');
