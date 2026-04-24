import '../entities/auth_session.dart';
import '../repositories/auth_repository.dart';

/// Single-purpose, easily-mockable command object.
///
/// Why use-cases when we already have a repository?
///   * Each use case is one call → one test file → one responsibility.
///   * Use cases can *compose* multiple repos later (e.g. auth + analytics).
///   * The Riverpod layer doesn't have to thread the repo through screens.
class LoginWithEmailUseCase {
  const LoginWithEmailUseCase(this._repo);
  final AuthRepository _repo;

  Future<AuthSession> call({
    required String email,
    required String password,
  }) {
    final trimmedEmail = email.trim().toLowerCase();
    if (trimmedEmail.isEmpty || password.isEmpty) {
      throw ArgumentError('Email and password are required');
    }
    return _repo.loginWithEmail(email: trimmedEmail, password: password);
  }
}
