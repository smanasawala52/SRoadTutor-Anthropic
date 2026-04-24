import '../entities/auth_session.dart';
import '../entities/user_role.dart';
import '../repositories/auth_repository.dart';

class GoogleLoginUseCase {
  const GoogleLoginUseCase(this._repo);
  final AuthRepository _repo;

  Future<AuthSession> call({required String idToken, UserRole? role}) {
    if (idToken.isEmpty) {
      throw ArgumentError('Google ID token is empty');
    }
    return _repo.loginWithGoogle(idToken: idToken, role: role);
  }
}
