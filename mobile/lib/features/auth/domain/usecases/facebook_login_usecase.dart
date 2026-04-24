import '../entities/auth_session.dart';
import '../entities/user_role.dart';
import '../repositories/auth_repository.dart';

class FacebookLoginUseCase {
  const FacebookLoginUseCase(this._repo);
  final AuthRepository _repo;

  Future<AuthSession> call({required String accessToken, UserRole? role}) {
    if (accessToken.isEmpty) {
      throw ArgumentError('Facebook access token is empty');
    }
    return _repo.loginWithFacebook(accessToken: accessToken, role: role);
  }
}
