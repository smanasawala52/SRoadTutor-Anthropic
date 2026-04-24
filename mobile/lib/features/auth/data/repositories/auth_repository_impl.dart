import '../../../../core/errors/exception_mapper.dart';
import '../../domain/entities/auth_session.dart';
import '../../domain/entities/user.dart';
import '../../domain/entities/user_role.dart';
import '../../domain/repositories/auth_repository.dart';
import '../datasources/auth_local_datasource.dart';
import '../datasources/auth_remote_datasource.dart';
import '../models/auth_response_model.dart';

/// Default implementation of [AuthRepository].
///
/// Responsibilities (kept deliberately thin):
///   * Talk to [AuthRemoteDataSource] and [AuthLocalDataSource].
///   * Persist the full session after every successful auth call so the
///     next cold-boot can render an authenticated UI without the network.
///   * Translate raw infrastructure exceptions → domain [Failure] objects.
class AuthRepositoryImpl implements AuthRepository {
  AuthRepositoryImpl({
    required this.remote,
    required this.local,
  });

  final AuthRemoteDataSource remote;
  final AuthLocalDataSource local;

  @override
  Future<User?> getCachedUser() async {
    final tokens = await local.readTokens();
    if (tokens == null) return null;
    return local.readCachedUser();
  }

  @override
  Future<AuthSession> loginWithEmail({
    required String email,
    required String password,
  }) async {
    try {
      final dto = await remote.login(email: email, password: password);
      return _persist(dto);
    } catch (e) {
      throw mapException(e);
    }
  }

  @override
  Future<AuthSession> signup({
    required String email,
    required String password,
    required String fullName,
    required UserRole role,
    String? phoneNumber,
  }) async {
    try {
      final dto = await remote.signup(
        email: email,
        password: password,
        fullName: fullName,
        role: role,
        phoneNumber: phoneNumber,
      );
      return _persist(dto);
    } catch (e) {
      throw mapException(e);
    }
  }

  @override
  Future<AuthSession> loginWithGoogle({
    required String idToken,
    UserRole? role,
  }) async {
    try {
      final dto = await remote.google(idToken: idToken, role: role);
      return _persist(dto);
    } catch (e) {
      throw mapException(e);
    }
  }

  @override
  Future<AuthSession> loginWithFacebook({
    required String accessToken,
    UserRole? role,
  }) async {
    try {
      final dto = await remote.facebook(accessToken: accessToken, role: role);
      return _persist(dto);
    } catch (e) {
      throw mapException(e);
    }
  }

  @override
  Future<AuthSession> refresh() async {
    final tokens = await local.readTokens();
    if (tokens == null) {
      throw Exception('No refresh token available');
    }
    try {
      final dto = await remote.refresh(tokens.refreshToken);
      return _persist(dto);
    } catch (e) {
      throw mapException(e);
    }
  }

  @override
  Future<void> logout() async {
    try {
      final tokens = await local.readTokens();
      if (tokens != null) {
        // Best-effort server revoke; ignore failures — local wipe is what
        // actually terminates the session on this device.
        await remote.logout(tokens.refreshToken).catchError((_) {});
      }
    } finally {
      await local.clearCachedUser();
      await local.clearTokens();
    }
  }

  Future<AuthSession> _persist(AuthResponseModel dto) async {
    final session = dto.toSession();
    await local.saveTokens(session.tokens);
    await local.cacheUser(session.user);
    return session;
  }
}
