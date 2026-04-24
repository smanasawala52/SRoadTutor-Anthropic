import 'main_common.dart';

/// PROD flavor entrypoint.
///
/// Run with:
///   flutter run --flavor prod -t lib/main_prod.dart
void main() => bootstrap(envFile: '.env.prod');
