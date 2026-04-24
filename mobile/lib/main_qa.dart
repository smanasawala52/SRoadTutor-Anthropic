import 'main_common.dart';

/// QA flavor entrypoint.
///
/// Run with:
///   flutter run --flavor qa -t lib/main_qa.dart
void main() => bootstrap(envFile: '.env.qa');
