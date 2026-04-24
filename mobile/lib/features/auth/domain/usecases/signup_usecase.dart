import '../entities/auth_session.dart';
import '../entities/user_role.dart';
import '../repositories/auth_repository.dart';

class SignupUseCase {
  const SignupUseCase(this._repo);
  final AuthRepository _repo;

  /// Client-side password rule — mirror of the backend regex so the user
  /// gets instant feedback instead of a 400 round-trip.
  static final _passwordRule =
      RegExp(r'^(?=.*[A-Za-z])(?=.*\d).{8,}$');

  static bool isStrongPassword(String pw) => _passwordRule.hasMatch(pw);

  Future<AuthSession> call({
    required String email,
    required String password,
    required String fullName,
    required UserRole role,
    String? phoneNumber,
  }) {
    final normalizedEmail = email.trim().toLowerCase();
    final normalizedName = fullName.trim();

    if (normalizedEmail.isEmpty) {
      throw ArgumentError('Email is required');
    }
    if (normalizedName.isEmpty) {
      throw ArgumentError('Full name is required');
    }
    if (!isStrongPassword(password)) {
      throw ArgumentError(
        'Password must be at least 8 characters and contain a letter and a number',
      );
    }
    return _repo.signup(
      email: normalizedEmail,
      password: password,
      fullName: normalizedName,
      role: role,
      phoneNumber: phoneNumber?.trim(),
    );
  }
}
