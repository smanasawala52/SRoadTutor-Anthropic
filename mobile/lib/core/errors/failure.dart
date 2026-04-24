import 'package:equatable/equatable.dart';

/// Clean-architecture "failure" — the *domain* version of a thrown exception.
///
/// Datasources throw raw exceptions (`DioException`, `FormatException`,
/// `SocketException`, …). The repository layer catches those and converts
/// them into a [Failure] so the UI layer never has to reason about transport
/// details — only about *what the user sees*.
sealed class Failure extends Equatable {
  const Failure(this.message, [this.code]);

  final String message;
  final String? code;

  @override
  List<Object?> get props => [message, code];
}

/// No internet / DNS / socket failure / request timeout. The action may be
/// safe to queue for later retry.
class NetworkFailure extends Failure {
  const NetworkFailure([String message = 'No internet connection'])
      : super(message, 'NETWORK_ERROR');
}

/// Backend returned a 4xx with a structured error body (our standard
/// `ErrorResponse` JSON envelope).
class ApiFailure extends Failure {
  const ApiFailure({
    required String message,
    required String code,
    this.statusCode,
  }) : super(message, code);

  final int? statusCode;

  @override
  List<Object?> get props => [...super.props, statusCode];
}

/// JWT expired / missing / invalid. UI should route back to login.
class UnauthorizedFailure extends Failure {
  const UnauthorizedFailure([String message = 'You are not signed in'])
      : super(message, 'UNAUTHORIZED');
}

/// Local validation failure (bad input format before we hit the network).
class ValidationFailure extends Failure {
  const ValidationFailure(String message) : super(message, 'VALIDATION_ERROR');
}

/// Something exploded that we didn't anticipate — always paired with a log.
class UnknownFailure extends Failure {
  const UnknownFailure([String message = 'Something went wrong'])
      : super(message, 'UNKNOWN');
}
