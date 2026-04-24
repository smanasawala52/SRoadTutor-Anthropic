import 'package:equatable/equatable.dart';

/// Pair of JWTs returned by the backend on any successful auth call.
class AuthTokens extends Equatable {
  const AuthTokens({required this.accessToken, required this.refreshToken});

  final String accessToken;
  final String refreshToken;

  @override
  List<Object?> get props => [accessToken, refreshToken];
}
