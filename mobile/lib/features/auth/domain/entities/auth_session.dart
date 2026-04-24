import 'package:equatable/equatable.dart';

import 'auth_tokens.dart';
import 'user.dart';

/// Aggregate of the current auth state — user + tokens.
/// Repositories return this; the UI watches this.
class AuthSession extends Equatable {
  const AuthSession({required this.user, required this.tokens});

  final User user;
  final AuthTokens tokens;

  @override
  List<Object?> get props => [user, tokens];
}
